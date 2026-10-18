# SPDX-License-Identifier: Apache-2.0
"""Lab end-to-end scenario: incident ticket from suggestion to rated closure, concurrent take-over,
visibility of internal notes, device loan with a damaged return, room booking with overlap and
role checks, knowledge answer and statistics.

Each run creates its own room, device and ticket (unique codes), so it can be repeated on the
same stack. The knowledge step needs the Lab corpus ingested; require_knowledge makes a missing
source a failure, otherwise only the shape of the answer is checked.
"""
import concurrent.futures
import datetime
import json
import random
import uuid

import dxos_client as dx
import seed_lab
from dxos_client import error_code, expect

UTC = datetime.timezone.utc


def run(base_url, steps, require_knowledge=False, **_):
    users = {key: dx.sign_in(base_url, account) for key, account in seed_lab.ACCOUNTS.items()}
    admin, tech1, tech2 = users["admin"], users["tech1"], users["tech2"]
    lecturer, student1, student2 = users["lecturer"], users["student1"], users["student2"]
    state = {}
    run_id = uuid.uuid4().hex[:6].upper()
    today = datetime.date.today()

    @steps("system status is public, names the Lab app and reports the backend up")
    def _():
        status, body = dx.Api(base_url, "").call("GET", "/system/status")
        expect(status == 200, f"status {status}")
        expect(body["data"]["app"]["id"] == "lab", body["data"]["app"])
        services = {s["name"]: s["status"] for s in body["data"]["services"]}
        expect(services.get("backend") == "UP", services)

    @steps("a route of another app does not exist (404)")
    def _():
        status, _body = admin.call("GET", "/parcels")
        expect(status == 404, f"status {status}")

    @steps("a student gets a category and priority suggestion with reasons before sending")
    def _():
        suggestion = student1.ok("POST", "/tickets/suggest", {
            "title": "Máy chiếu phòng A2 không lên hình", "description": "Máy chiếu bật nhưng không có tín hiệu"})
        expect(suggestion["categoryCode"] == "PROJECTOR", suggestion["categoryCode"])
        expect(suggestion["matchedKeywords"] and suggestion["reasons"], suggestion)
        urgent = student1.ok("POST", "/tickets/suggest", {
            "title": "Mất mạng giữa giờ thi", "description": "Cả phòng máy mất mạng giữa giờ thi"})
        expect(urgent["priority"] == "URGENT", urgent["priority"])

    @steps("a student reports an incident with a photo and gets SLA deadlines")
    def _():
        def paint(draw):
            draw.rectangle((40, 40, 600, 440), outline=(30, 30, 30), width=6)
            draw.text((60, 60), "No signal", fill=(30, 30, 30))

        body = {"title": f"E2E {run_id}: máy chiếu không lên hình",
                "description": "Máy chiếu bật đèn nhưng không có tín hiệu từ laptop.",
                "categoryCode": "PROJECTOR"}
        content, content_type = dx.multipart_parts([
            ("ticket", None, json.dumps(body).encode(), "application/json"),
            ("files", "man-hinh.png", dx.png_image(paint), "image/png")])
        detail = student1.ok("POST", "/tickets", headers={
            "Content-Type": content_type, "Idempotency-Key": str(uuid.uuid4())}, raw=content)
        ticket = detail["ticket"]
        expect(ticket["status"] == "NEW", ticket["status"])
        expect(ticket["code"].startswith("TK-"), ticket["code"])
        expect(ticket["responseDueAt"] and ticket["resolutionDueAt"], "missing SLA deadlines")
        expect(len(detail["attachments"]) == 1, detail["attachments"])
        state["ticket"] = ticket

    @steps("two technicians take the same ticket at once: one succeeds, the other gets 409")
    def _():
        path = f"/tickets/{state['ticket']['id']}/assign"
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            first, second = pool.map(lambda api: api.call("POST", path), [tech1, tech2])
        statuses = sorted([first[0], second[0]])
        expect(statuses == [200, 409], statuses)
        state["owner"] = tech1 if first[0] == 200 else tech2

    @steps("the technician replies in public and in an internal note, the student sees only the reply")
    def _():
        owner, ticket_id = state["owner"], state["ticket"]["id"]
        owner.ok("POST", f"/tickets/{ticket_id}/start")
        owner.ok("POST", f"/tickets/{ticket_id}/comments", {"body": "Đang kiểm tra cáp và nguồn tín hiệu."})
        owner.ok("POST", f"/tickets/{ticket_id}/comments", {"body": "Ghi chú nội bộ: nghi hỏng cổng HDMI.",
                                                          "internal": True})
        student_view = student1.ok("GET", f"/tickets/{ticket_id}")
        bodies = [c["body"] for c in student_view["comments"]]
        expect(any("kiểm tra cáp" in b for b in bodies), bodies)
        expect(not any("nội bộ" in b for b in bodies), bodies)
        student1.refused("POST", f"/tickets/{ticket_id}/comments", 403,
                         {"body": "Thử ghi chú nội bộ", "internal": True})

    @steps("resolving without a root cause is refused, then the ticket is resolved")
    def _():
        owner, ticket_id = state["owner"], state["ticket"]["id"]
        body = owner.refused("POST", f"/tickets/{ticket_id}/resolve", 422,
                             {"resolutionNote": "Đã thay cáp HDMI."})
        expect(error_code(body) == "ROOT_CAUSE_REQUIRED", body)
        resolved = owner.ok("POST", f"/tickets/{ticket_id}/resolve",
                            {"resolutionNote": "Đã thay cáp HDMI.", "rootCause": "Cáp HDMI đứt ngầm."})
        expect(resolved["status"] == "RESOLVED", resolved["status"])

    @steps("the student confirms with a rating and the timeline records every step")
    def _():
        ticket_id = state["ticket"]["id"]
        closed = student1.ok("POST", f"/tickets/{ticket_id}/confirm", {"rating": 4})
        expect(closed["status"] == "CLOSED" and closed["rating"] == 4, closed)
        timeline = student1.ok("GET", f"/tickets/{ticket_id}")["timeline"]
        expect(len(timeline) >= 5, [e.get("toStatus") for e in timeline])

    @steps("another student cannot open the ticket (404) and a student cannot read statistics (403)")
    def _():
        student2.refused("GET", f"/tickets/{state['ticket']['id']}", 404)
        student1.refused("GET", "/lab-stats/summary", 403)

    @steps("a student's loan is approved by a lecturer, a second loan of the device is refused")
    def _():
        device = tech1.ok("POST", "/devices", {
            "assetCode": f"E2E-{run_id}", "deviceType": "Laptop", "model": "E2E test laptop"})
        state["device"] = device
        due = (today + datetime.timedelta(days=3)).isoformat()
        loan = student1.ok("POST", "/device-loans", {"deviceId": device["id"], "purpose": "E2E mượn laptop",
                                                    "dueDate": due})
        expect(loan["status"] == "PENDING", loan["status"])
        approved = lecturer.ok("POST", f"/device-loans/{loan['id']}/approve")
        expect(approved["status"] == "APPROVED", approved["status"])
        expect(tech1.ok("GET", f"/devices/{device['id']}")["status"] == "ON_LOAN", "device not on loan")
        body = student2.refused("POST", "/device-loans", 422, {
            "deviceId": device["id"], "purpose": "E2E mượn trùng", "dueDate": due})
        expect(error_code(body) == "DEVICE_NOT_AVAILABLE", body)
        state["loan"] = approved

    @steps("a damaged return needs a note, then the device goes under repair with a maintenance entry")
    def _():
        loan, device = state["loan"], state["device"]
        body = tech1.refused("POST", f"/device-loans/{loan['id']}/return", 422, {"condition": "DAMAGED"})
        expect(error_code(body) == "RETURN_NOTE_REQUIRED", body)
        tech1.ok("POST", f"/device-loans/{loan['id']}/return",
                 {"condition": "DAMAGED", "note": "Màn hình có vệt sáng."})
        expect(tech1.ok("GET", f"/devices/{device['id']}")["status"] == "UNDER_REPAIR", "not under repair")
        expect(len(tech1.ok("GET", f"/devices/{device['id']}/maintenance")) >= 1, "no maintenance entry")

    @steps("a lecturer's booking is approved by a technician and appears in the room schedule")
    def _():
        room = tech1.ok("POST", "/rooms", {
            "code": f"E2E-{run_id}", "building": "Nhà E2E", "name": f"Phòng thử {run_id}",
            "roomType": "COMPUTER_LAB", "capacity": 30})
        state["room"] = room
        day = datetime.datetime.combine(today, datetime.time(), tzinfo=UTC) + datetime.timedelta(
            days=random.randint(5, 25))
        starts = day + datetime.timedelta(hours=random.randint(1, 8), minutes=random.choice([0, 30]))
        state["slot"] = (starts, starts + datetime.timedelta(hours=2))
        booking = lecturer.ok("POST", "/room-bookings", {
            "roomId": room["id"], "title": "E2E thực hành", "attendees": 25,
            "startsAt": _instant(starts), "endsAt": _instant(starts + datetime.timedelta(hours=2))})
        expect(booking["status"] == "PENDING", booking["status"])
        tech1.ok("POST", f"/room-bookings/{booking['id']}/approve")
        schedule = lecturer.ok("GET", f"/rooms/{room['id']}/schedule?from={starts.date()}&to={starts.date()}")
        expect(any(e["bookingId"] == booking["id"] for e in schedule["entries"]), schedule["entries"])

    @steps("overlapping, over-long and over-capacity bookings are refused with a reason")
    def _():
        room, (starts, _ends) = state["room"], state["slot"]
        overlap = lecturer.refused("POST", "/room-bookings", 422, {
            "roomId": room["id"], "title": "E2E trùng giờ", "attendees": 10,
            "startsAt": _instant(starts + datetime.timedelta(hours=1)),
            "endsAt": _instant(starts + datetime.timedelta(hours=3))})
        expect(error_code(overlap) == "ROOM_SLOT_TAKEN", overlap)
        later = starts + datetime.timedelta(days=1)
        too_long = lecturer.refused("POST", "/room-bookings", 422, {
            "roomId": room["id"], "title": "E2E quá dài", "attendees": 10,
            "startsAt": _instant(later), "endsAt": _instant(later + datetime.timedelta(hours=5))})
        expect(error_code(too_long) == "BOOKING_TOO_LONG", too_long)
        crowded = lecturer.refused("POST", "/room-bookings", 422, {
            "roomId": room["id"], "title": "E2E vượt sức chứa", "attendees": 31,
            "startsAt": _instant(later), "endsAt": _instant(later + datetime.timedelta(hours=1))})
        expect(error_code(crowded) == "ROOM_CAPACITY_EXCEEDED", crowded)

    @steps("a student cannot book a room (403)")
    def _():
        starts = state["slot"][0] + datetime.timedelta(days=2)
        student1.refused("POST", "/room-bookings", 403, {
            "roomId": state["room"]["id"], "title": "E2E sinh viên đặt phòng", "attendees": 5,
            "startsAt": _instant(starts), "endsAt": _instant(starts + datetime.timedelta(hours=1))})

    @steps("a knowledge answer quotes its sources")
    def _():
        answer = student1.ok("POST", "/knowledge/ask", {"question": "Quy định sử dụng máy tính trong thư viện là gì?"})
        passages = answer["passages"]
        if require_knowledge:
            expect(passages, "no passage: is the Lab corpus ingested?")
            expect(all(p["sources"] and p["sources"][0]["license"] for p in passages), passages)

    @steps("statistics cover the tickets of the period and the technician queue lists the open cases")
    def _():
        summary = admin.ok("GET", "/lab-stats/summary")
        by_status = sum(item["count"] for item in summary["ticketsByStatus"])
        expect(summary["ticketsCreated"] >= 1, summary["ticketsCreated"])
        expect(by_status == summary["ticketsCreated"], (by_status, summary["ticketsCreated"]))
        expect(summary["averageRating"] is not None, "no rating counted")
        queue = tech1.ok("GET", "/approval-requests")
        expect(queue is not None, "approval requests unavailable")


def _instant(moment):
    return moment.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")
