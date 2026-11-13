# SPDX-License-Identifier: Apache-2.0
"""The eight criteria of the demonstration, from docs/dynamic-core/README.md ("Thế nào là đạt").

Run on a stack that has the earlier checks behind it, or a clean one:
    python3 deploy/dev/check_demo.py
Criterion 3 needs the virus scan (docker compose --profile av) to see an infected file blocked;
without it that part is not run, unless DXOS_REQUIRE_SCAN is set, which makes a stack without the scan fail.
"""
import os
import sys

import check_api as rt
import kc

YAML = rt.YAML
TEMPLATES = "backend/dx-engine/src/main/resources/templates"
ONESTOP = rt.read(f"{TEMPLATES}/onestop.yaml")
BASE = "/data/onestop/request"
REQUEST = {"reason": "Cần giấy xác nhận sinh viên", "kind": "Giấy xác nhận", "department": "DAO_TAO"}


def act(token, base, record, action, note=None):
    return kc.api(token, "POST", f"{base}/{record}/actions/{action}", {"note": note} if note else {})


def upload(token, base, record, field, name, data):
    boundary = "dxosdemo"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
            f"Content-Type: application/pdf\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
    return kc.api(token, "POST", f"{base}/{record}/files?field={field}", body, f"multipart/form-data; boundary={boundary}")


def apply_model(token, content):
    """Plans first and applies on top of the version that was planned, the way the control panel does."""
    status, body = kc.api(token, "POST", "/models/plan", content, YAML)
    if status != 200:
        return status, body
    plan = body["data"]
    query = f"?base={plan['fromVersion']}" + (f"&accept={plan['level']}" if plan["level"] != "SAFE" else "") if plan["fromVersion"] else ""
    return kc.api(token, "POST", f"/models/apply{query}", content, YAML)


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")

    # 1. One command with a model file gives a system that accepts logins at the API.
    status, body = apply_model(admin, ONESTOP)
    rt.check("C1 one command applies the model file", status == 200 and body["data"]["status"] in ("applied", "unchanged"), f"{status} {body}")
    status, _ = kc.api(None, "GET", "/meta/models")
    rt.check("C1 the API wants a login", status == 401, str(status))
    kc.ensure_user("demo_stu1", ["onestop/STUDENT"])
    kc.ensure_user("demo_stu2", ["onestop/STUDENT"])
    kc.ensure_user("demo_dt", ["onestop/OFFICER"], ["DAO_TAO"])
    kc.ensure_user("demo_ct", ["onestop/OFFICER"], ["CTSV"])
    stu1, stu2, dt, ct = (kc.token(u) for u in ("demo_stu1", "demo_stu2", "demo_dt", "demo_ct"))
    status, body = kc.api(stu1, "GET", "/meta/models")
    rt.check("C1 a student logs in and the metadata builds the screens",
             status == 200 and any(m["name"] == "onestop" for m in body["data"]), f"{status}")

    # 2. Students see their own records; officers see their unit and work from the inbox.
    status, made = kc.api(stu1, "POST", BASE, REQUEST)
    record = made["data"]["id"]
    status, listed = kc.api(stu2, "GET", BASE)
    rt.check("C2 a student does not see another student's record", all(r["id"] != record for r in listed["data"]["items"]), str(status))
    status, _ = kc.api(stu2, "GET", f"{BASE}/{record}")
    rt.check("C2 and cannot open it", status == 404, str(status))
    status, _ = kc.api(ct, "GET", f"{BASE}/{record}")
    rt.check("C2 an officer of another unit does not see it", status == 404, str(status))
    status, _ = kc.api(dt, "GET", f"{BASE}/{record}")
    rt.check("C2 the officer of the unit sees it", status == 200, str(status))
    status, inbox = kc.api(dt, "GET", "/inbox")
    rt.check("C2 and finds it in the inbox", any(i["id"] == record and i["entity"] == "request" for i in inbox["data"]["items"]), str(status))
    status, inbox = kc.api(ct, "GET", "/inbox")
    rt.check("C2 other units' inbox does not", not any(i["id"] == record and i["model"] == "onestop" for i in inbox["data"]["items"]), str(status))

    # 3. A file attaches to the record.
    status, body = upload(stu1, BASE, record, "evidence", "giay-hen.pdf", b"%PDF-1.4 demo")
    rt.check("C3 a file attaches", status == 200 and body["data"]["name"] == "giay-hen.pdf", f"{status} {body}")
    status, body = kc.api(dt, "GET", f"{BASE}/{record}/files")
    rt.check("C3 the officer sees the file", status == 200 and len(body["data"]) == 1, str(status))
    clean = body["data"][0]["id"] if status == 200 and body["data"] else 0
    status, body = kc.api(dt, "GET", f"{BASE}/{record}/files/{clean}/link")
    fetched = kc.http("GET", body["data"]["url"], raw=True) if status == 200 else (0, "", None)
    rt.check("C3 and downloads it", status == 200 and fetched[0] == 200 and fetched[1].startswith("%PDF"), f"{status} {fetched[0]}")
    scan_on = bool(kc.ENV.get("STORAGE_SCAN_HOST"))
    if os.environ.get("DXOS_REQUIRE_SCAN"):
        rt.check("C3 the virus scan is on", scan_on, f"STORAGE_SCAN_HOST={kc.ENV.get('STORAGE_SCAN_HOST', '')!r}")
    if scan_on:
        # The EICAR test string, built at run time so scanners on this machine leave the script alone.
        eicar = ("X5O!P%@AP[4\\PZX54(P^)7CC)7}$" + "EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*").encode()
        status, body = upload(stu1, BASE, record, "evidence", "virus.pdf", eicar)
        rt.check("C3 an infected file is refused", status == 422 and body.get("errorCode") == "ERR_FILE_INFECTED", f"{status} {body}")
        status, body = kc.api(dt, "GET", f"{BASE}/{record}/files")
        rt.check("C3 and is not stored", status == 200 and len(body["data"]) == 1, str(status))
        # A file that cannot be scanned is not taken as clean.
        rt.sh(rt.COMPOSE + ["stop", "clamav"])
        status, body = upload(stu1, BASE, record, "evidence", "chua-quet.pdf", b"%PDF-1.4 unscanned")
        rt.check("C3 a file is refused while the scanner is down", status == 503 and body.get("errorCode") == "ERR_SCAN_UNAVAILABLE", f"{status} {body}")
        status, body = kc.api(dt, "GET", f"{BASE}/{record}/files")
        rt.check("C3 and is not stored either", status == 200 and len(body["data"]) == 1, str(status))
        rt.sh(rt.COMPOSE + ["up", "-d", "--wait", "clamav"])
        status, body = upload(stu1, BASE, record, "evidence", "da-quet.pdf", b"%PDF-1.4 scanned again")
        rt.check("C3 a clean file is stored again once the scanner is back", status == 200, f"{status} {body}")
    else:
        print("NOTE C3 the virus scan is off (STORAGE_SCAN_HOST blank); the infected file check is not run")

    # 4. A direct call that changes the state is refused.
    status, _ = kc.api(stu1, "PATCH", f"{BASE}/{record}", {"status": "approved"})
    rt.check("C4 the status cannot be written", status in (400, 403), str(status))
    status, _ = act(stu1, BASE, record, "approve")
    rt.check("C4 a student cannot take the officer's action", status == 403, str(status))
    status, body = kc.api(dt, "GET", f"{BASE}/{record}")
    rt.check("C4 the record is unchanged", body["data"]["status"] == "submitted", str(body["data"]["status"]))

    # 8. The officer asks for more, the student fixes it, the record goes on.
    status, body = act(dt, BASE, record, "return", "Cần bản scan giấy hẹn")
    rt.check("C8 the officer asks for more", status == 200 and body["data"]["status"] == "returned", f"{status} {body}")
    status, _ = kc.api(stu1, "PATCH", f"{BASE}/{record}", {"reason": "Cần giấy xác nhận, đã bổ sung giấy hẹn"})
    rt.check("C8 the student fixes it", status == 200, str(status))
    status, body = act(stu1, BASE, record, "resubmit")
    rt.check("C8 and submits again", status == 200 and body["data"]["status"] == "submitted", f"{status} {body}")
    status, body = act(dt, BASE, record, "approve")
    rt.check("C8 the record goes on to the decision", status == 200 and body["data"]["status"] == "approved", f"{status} {body}")

    # 5 and 7. Adding and renaming a field with data; a rule the data breaks.
    # The copy uses the same role names; they are held per model.
    demo = ONESTOP.replace("name: onestop\n", "name: onestop_demo\n")
    apply_model(admin, demo)
    kc.ensure_user("demo_stud", ["onestop_demo/STUDENT"])
    demo_stu = kc.token("demo_stud")
    path = "/data/onestop_demo/request"
    for n in range(3):
        kc.api(demo_stu, "POST", path, {**REQUEST, "reason": "Trùng lặp" if n < 2 else "Khác"})
    # The print template names the field too, so it follows the new name.
    changed = demo.replace("      reason: {type: text, label: Lý do, required: true}",
                           "      content: {type: text, label: Lý do, required: true, renamed_from: reason}\n      phone: {type: text, label: Điện thoại}")
    changed = changed.replace("fields: [kind, reason,", "fields: [kind, content,")
    status, body = kc.api(admin, "POST", "/models/plan", changed, YAML)
    the_plan = body["data"]
    rt.check("C5 adding a field and renaming one gives a plan with its risk",
             status == 200 and the_plan["level"] == "MIGRATION" and the_plan["action"] == "change" and the_plan["statements"], str(the_plan)[:300])
    status, body = kc.api(admin, "POST", f"/models/apply?base={the_plan['fromVersion']}&accept=MIGRATION", changed, YAML)
    rt.check("C5 apply without rebuilding", status == 200 and body["data"]["status"] == "applied", f"{status} {body}")
    running = body["data"]["version"] if status == 200 else the_plan["fromVersion"]
    status, body = kc.api(demo_stu, "GET", f"{path}?size=50")
    rows = body["data"]["items"]
    rt.check("C5 the data is intact under the new name", len(rows) >= 3 and all("content" in r and "reason" not in r for r in rows) and
             sum(1 for r in rows if r["content"] == "Trùng lặp") == 2, str(rows)[:200])
    unique = changed.replace("content: {type: text, label: Lý do, required: true, renamed_from: reason}",
                             "content: {type: text, label: Lý do, required: true, unique: true}")
    status, body = kc.api(admin, "POST", "/models/plan", unique, YAML)
    violations = body["data"]["violations"]
    rt.check("C7 a rule the data breaks is refused with the rows",
             body["data"]["action"] == "rejected" and violations and violations[0]["rows"] == 2 and violations[0]["sample"], str(body["data"])[:300])
    status, _ = kc.api(admin, "POST", f"/models/apply?base={running}&accept=MIGRATION", unique, YAML)
    rt.check("C7 and it does not apply", status in (400, 409, 422), str(status))

    # 6. A model of another family runs without changing the core.
    support = rt.read(f"{TEMPLATES}/support_desk.yaml")
    status, body = apply_model(admin, support)
    rt.check("C6 a work item model applies", status == 200, f"{status} {body}")
    kc.ensure_user("demo_req", ["support/REQUESTER"])
    kc.ensure_user("demo_agent", ["support/AGENT"])
    req, agent = kc.token("demo_req"), kc.token("demo_agent")
    status, made = kc.api(req, "POST", "/data/support/ticket", {"title": "Máy chiếu hỏng", "priority": "Cao"})
    ticket = made["data"]["id"]
    for name, who in (("start", agent), ("resolve", agent), ("confirm", req)):
        status, body = act(who, "/data/support/ticket", ticket, name)
    rt.check("C6 and runs its own process", status == 200 and body["data"]["status"] == "closed", f"{status} {body}")
    rooms = rt.read(f"{TEMPLATES}/room_booking.yaml")
    status, body = apply_model(admin, rooms)
    rt.check("C6 a reservation model applies too", status == 200, f"{status} {body}")

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
