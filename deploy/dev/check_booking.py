# SPDX-License-Identifier: Apache-2.0
"""Checks of the reservation family and of what the control panel reads.

Run after check_process.py on the same stack:
    python3 deploy/dev/check_booking.py
"""
import json
import sys
import threading

import check_api as rt
import kc

YAML = rt.YAML
TEMPLATES = "backend/dx-engine/src/main/resources/templates"
ROOMS = rt.read(f"{TEMPLATES}/room_booking.yaml")
BOOKING = "booking: {resource: room, starts: starts_at, ends: ends_at, capacity: 1}"
ROOM = "/data/rooms/room"
BOOK = "/data/rooms/booking"


def at(hour, minute=0, day=10):
    return f"2026-11-{day:02d}T{hour:02d}:{minute:02d}:00+07:00"


def book(token, room, start, end, purpose="Họp nhóm"):
    return kc.api(token, "POST", BOOK, {"room": room, "purpose": purpose, "starts_at": start, "ends_at": end})


def act(token, record, action, note=None):
    return kc.api(token, "POST", f"{BOOK}/{record}/actions/{action}", {"note": note} if note else {})


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")

    # T9-01: the booking block is checked.
    for case, replacement, needle in [
        ("missing", "", "booking"),
        ("resource of the wrong type", BOOKING.replace("resource: room", "resource: purpose"), "reference"),
        ("unknown field", BOOKING.replace("starts: starts_at", "starts: nowhere"), "datetime"),
        ("same field twice", BOOKING.replace("ends: ends_at", "ends: starts_at"), "different"),
        ("capacity zero", BOOKING.replace("capacity: 1", "capacity: 0"), "positive"),
    ]:
        content = ROOMS.replace(f"      {BOOKING}\n", f"      {replacement}\n" if replacement else "")
        status, body = kc.api(admin, "POST", "/models/plan", content, YAML)
        rt.check(f"T9-01 {case}", status == 400 and needle in " ".join(body["data"]), f"{status} {body.get('data')}")
    other = ROOMS.replace("family: reservation", "family: approval")
    status, body = kc.api(admin, "POST", "/models/plan", other, YAML)
    rt.check("T9-01 booking only for reservations", status == 400 and "reservation" in " ".join(body["data"]), f"{status} {body.get('data')}")

    # T9-06: what the control panel reads.
    status, body = kc.api(admin, "GET", "/models/schema")
    rt.check("T9-06 schema", status == 200 and "process" in json.dumps(body["data"]), str(status))
    status, body = kc.api(admin, "GET", "/models/families")
    names = [f["name"] for f in body["data"]] if status == 200 else []
    rt.check("T9-06 families", names == ["approval", "reservation", "work_item"], str(names))
    reservation = next((f for f in body["data"] if f["name"] == "reservation"), {})
    rt.check("T9-06 a family lists its states, actions and parameters",
             len(reservation.get("states", [])) == 7 and any(p["name"] == "booking" for p in reservation.get("params", [])), str(reservation)[:200])
    consistent = all(
        action["to"] in {state["name"] for state in family["states"]}
        and set(action["from"]) <= {state["name"] for state in family["states"]}
        for family in body["data"] for action in family["actions"])
    rt.check("T9-10 every action leads from and to a state of its family", status == 200 and consistent)
    status, body = kc.api(admin, "GET", "/models/templates")
    templates = body["data"] if status == 200 else []
    rt.check("T9-06 templates", status == 200 and len(templates) >= 6 and all(t["content"].startswith("#") or "format" in t["content"] for t in templates), str(status))
    kc.ensure_user("plain9", ["EMPLOYEE"])
    status, _ = kc.api(kc.token("plain9"), "GET", "/models/templates")
    rt.check("T9-06 other roles are refused", status == 403, str(status))

    # T9-07: every template applies.
    for template in templates:
        status, body = kc.api(admin, "POST", "/models/apply", template["content"], YAML)
        rt.check(f"T9-07 template {template['id']} applies", status == 200 and body["data"]["status"] in ("applied", "unchanged"), f"{status} {body}")
    status, body = kc.api(admin, "GET", "/models/rooms/versions")
    rt.check("T9-06 versions", status == 200 and body["data"][0]["version"] == 1, str(body)[:150])
    status, body = kc.api(admin, "GET", "/models/rooms/versions/1")
    rt.check("T9-06 one version with its content", status == 200 and "reservation" in body["data"]["content"], str(status))
    status, _ = kc.api(admin, "GET", "/models/rooms/versions/99")
    rt.check("T9-06 a version that does not exist", status == 404, str(status))

    # Rooms and bookings.
    kc.ensure_user("mgr9", ["MANAGER"])
    kc.ensure_user("guest9", ["GUEST"])
    kc.ensure_user("guest9b", ["GUEST"])
    manager, guest, guest2 = kc.token("mgr9"), kc.token("guest9"), kc.token("guest9b")
    status, made = kc.api(manager, "POST", ROOM, {"code": "P101", "name": "Phòng 101", "seats": 30})
    room = made["data"]["id"]
    status, made = kc.api(manager, "POST", ROOM, {"code": "P102", "name": "Phòng 102", "seats": 20})
    room2 = made["data"]["id"]

    status, first = book(guest, room, at(10), at(11))
    rt.check("T9-02 a free slot is accepted", status == 200 and first["data"]["status"] == "requested", f"{status} {first}")
    first_id = first["data"]["id"]
    status, body = book(guest2, room, at(10, 30), at(11, 30))
    rt.check("T9-03 an overlapping slot is refused", status == 409, f"{status} {body}")
    status, body = book(guest2, room, at(9), at(10, 1))
    rt.check("T9-03 an overlap of a minute is refused", status == 409, f"{status}")
    status, body = book(guest2, room, at(11), at(12))
    rt.check("T9-02 back-to-back slots are fine", status == 200, f"{status} {body}")
    adjacent = body["data"]["id"]
    status, body = book(guest2, room2, at(10), at(11))
    rt.check("T9-02 another room is independent", status == 200, f"{status} {body}")
    status, body = book(guest2, room, at(15), at(14))
    rt.check("T9-03 the end must follow the start", status == 400 and "ends_at" in body["data"], f"{status} {body}")

    # T9-04: a rejected or withdrawn booking frees the slot.
    status, _ = act(guest, first_id, "reject", "x")
    rt.check("T9-04 the owner cannot reject", status == 403, str(status))
    status, body = act(manager, first_id, "reject")
    rt.check("T9-04 a rejection needs a reason", status == 400, f"{status}")
    status, body = act(manager, first_id, "reject", "Phòng sửa chữa")
    rt.check("T9-04 reject", status == 200 and body["data"]["status"] == "rejected", f"{status} {body}")
    status, taken = book(guest2, room, at(10), at(10, 45))
    rt.check("T9-04 the freed slot can be booked", status == 200, f"{status} {taken}")
    status, body = act(guest2, adjacent, "withdraw")
    rt.check("T9-04 withdraw", status == 200 and body["data"]["status"] == "withdrawn", f"{status} {body}")
    status, body = book(guest, room, at(11), at(12))
    rt.check("T9-04 a withdrawn booking frees the slot", status == 200, f"{status} {body}")

    # Changing a booking is checked against the others, not against itself.
    mine = body["data"]["id"]
    status, body = kc.api(guest, "PATCH", f"{BOOK}/{mine}", {"starts_at": at(11, 15), "ends_at": at(11, 45)})
    rt.check("T9-03 moving within its own slot", status == 200, f"{status} {body}")
    status, body = kc.api(guest, "PATCH", f"{BOOK}/{mine}", {"starts_at": at(10, 30), "ends_at": at(11, 45)})
    rt.check("T9-03 moving into another booking is refused", status == 409, f"{status} {body}")
    status, body = kc.api(guest, "PATCH", f"{BOOK}/{mine}", {"purpose": "Đổi mục đích"})
    rt.check("T9-03 changing another field is not checked", status == 200, f"{status}")

    # The confirmed and in-use steps keep the slot taken.
    status, body = act(manager, mine, "confirm")
    rt.check("T9-04 confirm", status == 200 and body["data"]["status"] == "confirmed", f"{status} {body}")
    status, body = book(guest2, room, at(11, 20), at(11, 30))
    rt.check("T9-03 a confirmed booking still holds the slot", status == 409, f"{status}")
    act(manager, mine, "start")
    status, body = act(manager, mine, "complete")
    rt.check("T9-04 start and complete", status == 200 and body["data"]["status"] == "completed", f"{status} {body}")
    status, body = book(guest2, room, at(11, 20), at(11, 30))
    rt.check("T9-04 a completed booking frees the slot", status == 200, f"{status}")

    # T9-04: a capacity above one allows overlaps up to the capacity.
    hall = ROOMS.replace("name: rooms", "name: hall").replace("capacity: 1", "capacity: 2")
    status, body = kc.api(admin, "POST", "/models/apply", hall, YAML)
    rt.check("T9-04 capacity two applies", status == 200, f"{status} {body}")
    status, made = kc.api(manager, "POST", "/data/hall/room", {"code": "H1", "name": "Hội trường", "seats": 200})
    hall_room = made["data"]["id"]
    results = []
    for n in range(3):
        status, body = kc.api(guest, "POST", "/data/hall/booking", {"room": hall_room, "purpose": f"Lớp {n}", "starts_at": at(8, day=12), "ends_at": at(9, day=12)})
        results.append(status)
    rt.check("T9-04 two fit, the third does not", results == [200, 200, 409], str(results))

    # T9-05: many requests for the same slot at once.
    outcomes = []

    def attempt(n):
        status, _ = book(guest, room, at(18), at(19), f"Đồng thời {n}")
        outcomes.append(status)

    threads = [threading.Thread(target=attempt, args=(n,)) for n in range(8)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    rt.check("T9-05 only one of eight concurrent requests gets the slot", sorted(outcomes) == [200] + [409] * 7, str(sorted(outcomes)))

    # The calendar reads a range with the filters of the API.
    status, body = kc.api(guest, "GET", f"{BOOK}?filter.starts_at.lt={at(23).replace('+', '%2B')}&filter.ends_at.gt={at(0).replace('+', '%2B')}&size=200")
    rt.check("T9-11 a day is read with the filters", status == 200 and body["data"]["totalElements"] >= 3, str(status))

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
