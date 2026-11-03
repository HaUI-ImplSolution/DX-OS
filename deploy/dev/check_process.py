# SPDX-License-Identifier: Apache-2.0
"""Checks of the common process: families, the rules every process obeys, deadlines.

Run after check_case.py on the same stack:
    python3 deploy/dev/check_process.py
"""
import sys
import time

import check_api as rt
import kc

YAML = rt.YAML
DESK = """format: 0
name: desk8
roles:
  - name: ASKER
  - name: AGENT
    staff: true
entities:
  ticket:
    label: Yêu cầu hỗ trợ
    fields:
      title: {type: text, required: true}
    access: {ASKER: own, AGENT: all}
    process: {family: work_item, handler: AGENT, deadline: 1h}
  leave:
    label: Đơn nghỉ
    fields:
      reason: {type: text, required: true}
    access: {ASKER: own, AGENT: all}
    process: {family: approval, handler: AGENT}
"""
BAD = DESK.split("entities:")[0] + """entities:
  ticket:
    fields:
      title: {type: text, required: true}
    access: {ASKER: own, AGENT: all}
    process: %s
"""
TICKETS = "/data/desk8/ticket"
LEAVES = "/data/desk8/leave"


def act(token, base, record, action, note=None):
    return kc.api(token, "POST", f"{base}/{record}/actions/{action}", {"note": note} if note else {})


def wait_for(check, seconds=40):
    deadline = time.time() + seconds
    while time.time() < deadline:
        if check():
            return True
        time.sleep(2)
    return False


def create(token, base, body):
    status, made = kc.api(token, "POST", base, body)
    assert status == 200, (status, made)
    return made["data"]["id"]


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")

    # T8-02: the process block is checked and errors point at its line.
    for case, process, needle in [
        ("unknown family", "{family: nope, handler: AGENT}", "line 12"),
        ("unknown key", "{family: work_item, handler: AGENT, speed: fast}", "line 12"),
        ("bad deadline", "{family: work_item, handler: AGENT, deadline: soon}", "line 12"),
        ("zero deadline", "{family: work_item, handler: AGENT, deadline: 0h}", "line 12"),
        ("handler not a role", "{family: work_item, handler: NOBODY}", "line 12"),
    ]:
        status, body = kc.api(admin, "POST", "/models/plan", BAD % process, YAML)
        rt.check(f"T8-02 {case}", status == 400 and needle in body["data"][0], f"{status} {body.get('data')}")

    # T8-01: two families run in one model without changing the code of the core.
    status, body = kc.api(admin, "POST", "/models/apply", DESK, YAML)
    rt.check("T8-01 apply", status == 200 and body["data"]["status"] == "applied", f"{status} {body}")
    asker_id = kc.ensure_user("asker8", ["ASKER"])
    kc.ensure_user("agent8", ["AGENT"])
    kc.ensure_user("agent9", ["AGENT"])
    asker, agent, agent2 = kc.token("asker8"), kc.token("agent8"), kc.token("agent9")
    status, body = kc.api(agent, "GET", "/meta/models")
    entities = {e["name"]: e for model in body["data"] if model["name"] == "desk8" for e in model["entities"]}
    rt.check("T8-01 metadata names the family and deadline",
             entities["ticket"]["process"]["family"] == "work_item"
             and entities["ticket"]["process"]["deadlineSeconds"] == 3600
             and entities["leave"]["process"]["family"] == "approval"
             and entities["leave"]["process"]["deadlineSeconds"] is None, str(entities))

    # The work item family from take to close.
    ticket = create(asker, TICKETS, {"title": "Máy in hỏng"})
    status, body = kc.api(asker, "GET", f"{TICKETS}/{ticket}")
    rt.check("T8-08 the deadline starts with the record",
             body["data"]["handle_by"] is not None and body["data"]["overdue"] is False, str(body["data"]))
    status, _ = act(asker, TICKETS, ticket, "start")
    rt.check("T8-06 the owner cannot take the work", status == 403, str(status))
    status, body = act(agent, TICKETS, ticket, "start")
    rt.check("T8-01 take", status == 200 and body["data"]["status"] == "in_progress", f"{status} {body}")
    status, body = act(agent, TICKETS, ticket, "resolve")
    rt.check("T8-01 resolve", status == 200 and body["data"]["status"] == "resolved", f"{status} {body}")
    rt.check("T8-08 the deadline stops when nobody has to act", body["data"]["handle_by"] is None, str(body["data"]))
    status, _ = act(agent, TICKETS, ticket, "confirm")
    rt.check("T8-06 the handler cannot confirm", status == 403, str(status))
    status, body = act(asker, TICKETS, ticket, "reopen")
    rt.check("T8-03 reopen needs a reason", status == 400 and "note" in body["data"], f"{status} {body}")
    status, body = act(asker, TICKETS, ticket, "reopen", "Vẫn chưa in được")
    rt.check("T8-01 reopen", status == 200 and body["data"]["status"] == "in_progress" and body["data"]["handle_by"] is not None, f"{status} {body}")
    act(agent, TICKETS, ticket, "resolve")
    status, body = act(asker, TICKETS, ticket, "confirm")
    rt.check("T8-01 confirm closes", status == 200 and body["data"]["status"] == "closed" and body["data"]["decided_by"] == asker_id, f"{status} {body}")
    status, _ = act(asker, TICKETS, ticket, "withdraw")
    rt.check("T8-04 a closed record cannot be withdrawn", status == 409, str(status))

    # T8-03, T8-04, T8-05: reasons, withdrawing, keeping the record.
    second = create(asker, TICKETS, {"title": "Hỏng chuột"})
    status, body = act(agent, TICKETS, second, "reject")
    rt.check("T8-03 reject needs a reason", status == 400 and "note" in body["data"], f"{status} {body}")
    status, body = act(agent, TICKETS, second, "reject", "Ngoài phạm vi hỗ trợ")
    rt.check("T8-03 reject with a reason", status == 200 and body["data"]["status"] == "rejected", f"{status} {body}")
    third = create(asker, TICKETS, {"title": "Cần cài phần mềm"})
    act(agent, TICKETS, third, "start")
    status, _ = act(agent, TICKETS, third, "withdraw")
    rt.check("T8-04 only the owner withdraws", status == 403, str(status))
    status, body = act(asker, TICKETS, third, "withdraw", "Đã tự xử lý")
    rt.check("T8-04 withdraw", status == 200 and body["data"]["status"] == "withdrawn", f"{status} {body}")
    code, kept = rt.psql(f"select count(*) from engine.record_event where record_id={third} and entity='ticket' and action='withdraw'")
    rt.check("T8-04 withdrawing leaves one history line", kept == "1", kept)
    status, _ = kc.api(asker, "DELETE", f"{TICKETS}/{third}")
    rt.check("T8-05 a record with a process cannot be deleted", status == 409, str(status))
    status, _ = act(agent, TICKETS, third, "start")
    rt.check("T8-04 a withdrawn record is final", status == 409, str(status))

    # The approval family and the rules the creator and the handler role are held to.
    leave = create(asker, LEAVES, {"reason": "Việc gia đình"})
    status, _ = act(asker, LEAVES, leave, "approve")
    rt.check("T8-06 the owner cannot approve", status == 403, str(status))
    status, body = act(agent, LEAVES, leave, "return", "Ghi rõ ngày nghỉ")
    rt.check("T8-01 return", status == 200 and body["data"]["status"] == "returned", f"{status} {body}")
    status, body = act(asker, LEAVES, leave, "withdraw")
    rt.check("T8-04 withdraw from returned", status == 200 and body["data"]["status"] == "withdrawn", f"{status} {body}")

    # T8-08: overdue records come first in the inbox, then those waiting longest.
    older = create(asker, TICKETS, {"title": "Cũ hơn"})
    newer = create(asker, TICKETS, {"title": "Mới hơn"})
    rt.psql(f"update m_desk8.ticket set handle_by = now() - interval '1 minute' where id = {newer}")
    status, body = kc.api(agent, "GET", f"{TICKETS}/{newer}")
    rt.check("T8-08 a past deadline marks the record overdue", body["data"]["overdue"] is True, str(body["data"]))
    status, body = kc.api(agent, "GET", f"{TICKETS}/{older}")
    rt.check("T8-08 a future deadline does not", body["data"]["overdue"] is False, str(body["data"]))
    status, body = kc.api(agent2, "GET", "/inbox")
    order = [item["id"] for item in body["data"]["items"] if item["model"] == "desk8" and item["entity"] == "ticket"]
    rt.check("T8-08 inbox puts overdue first", order[:2] == [newer, older] and body["data"]["items"][0]["overdue"] is True, str(order))

    # T8-07: changing the handler and the deadline is safe and reports the records in flight.
    changed = DESK.replace("deadline: 1h", "deadline: 2d")
    status, body = kc.api(admin, "POST", "/models/plan", changed, YAML)
    kinds = {c["kind"] for c in body["data"]["changes"]}
    rt.check("T8-07 deadline change is safe and reports open records",
             status == 200 and body["data"]["level"] == "SAFE" and "CHANGE_DEADLINE" in kinds
             and body["data"]["openRecords"].get("ticket", 0) >= 2, str(body["data"]))
    swapped = DESK.replace("family: work_item", "family: approval")
    status, body = kc.api(admin, "POST", "/models/plan", swapped, YAML)
    rt.check("T8-07 a change of family is refused for now",
             status == 200 and body["data"]["action"] == "rejected" and body["data"]["rejections"], str(body["data"]))

    # T8-09, T8-10: a final state starts the actions the model asks for, once the decision committed.
    status, body = kc.api(admin, "POST", "/models/apply", rt.read("backend/dx-engine/src/main/resources/templates/itaccess.yaml"), YAML)
    rt.check("T8-10 apply the access model", status == 200 and body["data"]["status"] == "applied", f"{status} {body}")
    status, found = kc.kc("GET", "/groups?search=GIT_USERS&exact=true")
    if not found:
        kc.kc("POST", "/groups", {"name": "GIT_USERS"})
        status, found = kc.kc("GET", "/groups?search=GIT_USERS&exact=true")
    git_group = found[0]["id"]
    employee_id = kc.ensure_user("emp8", ["EMPLOYEE"])
    kc.ensure_user("itadmin8", ["IT_ADMIN"])
    employee, itadmin = kc.token("emp8"), kc.token("itadmin8")
    requests = "/data/itaccess/access_request"
    first = create(employee, requests, {"access_group": "GIT_USERS", "reason": "Làm dự án mới"})

    def member(group_id):
        status, people = kc.kc("GET", f"/groups/{group_id}/members")
        return employee_id in [p["id"] for p in people]

    rt.check("T8-10 nothing happens before the decision", not member(git_group))
    rt.check("T8-10 nothing happens before the decision", not member(git_group))

    # T8-11: files attached to a record follow the access to the record.
    def upload(token, record, field, name, data):
        boundary = "dxosboundary"
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
                f"Content-Type: application/pdf\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
        return kc.api(token, "POST", f"{requests}/{record}/files?field={field}", body,
                      f"multipart/form-data; boundary={boundary}")

    status, body = upload(employee, first, "evidence", "giay-to.pdf", b"%PDF-1.4 test")
    rt.check("T8-11 the owner attaches a file", status == 200 and body["data"]["name"] == "giay-to.pdf", f"{status} {body}")
    file_id = body["data"]["id"] if status == 200 else 0
    status, body = upload(employee, first, "reason", "x.pdf", b"x")
    rt.check("T8-11 only file fields take files", status == 400, f"{status} {body}")
    status, body = upload(employee, first, "evidence", "rong.pdf", b"")
    rt.check("T8-11 an empty file is refused", status == 400, f"{status}")
    status, body = kc.api(employee, "GET", f"{requests}/{first}")
    rt.check("T8-11 the file field shows the latest name", body["data"]["evidence"] == "giay-to.pdf", str(body["data"]))
    status, body = kc.api(itadmin, "GET", f"{requests}/{first}/files")
    rt.check("T8-11 the handler sees the files", status == 200 and [f["name"] for f in body["data"]] == ["giay-to.pdf"], f"{status} {body}")
    status, body = kc.api(itadmin, "GET", f"{requests}/{first}/files/{file_id}/link")
    fetched = kc.http("GET", body["data"]["url"], raw=True) if status == 200 else (0, "", None)
    rt.check("T8-11 the link downloads the file", status == 200 and fetched[0] == 200 and fetched[1].startswith("%PDF"), f"{status} {fetched[0]}")
    other = kc.ensure_user("emp8b", ["EMPLOYEE"])
    status, _ = kc.api(kc.token("emp8b"), "GET", f"{requests}/{first}/files")
    rt.check("T8-11 another employee sees nothing", status == 404, str(status))
    status, _ = kc.api(itadmin, "DELETE", f"{requests}/{first}/files/{file_id}")
    rt.check("T8-11 only who added a file removes it", status == 403, str(status))
    status, _ = kc.api(employee, "DELETE", f"{requests}/{first}/files/{file_id}")
    rt.check("T8-11 the owner removes it", status == 200, str(status))
    status, body = kc.api(employee, "GET", f"{requests}/{first}")
    rt.check("T8-11 and the field empties", body["data"]["evidence"] is None, str(body["data"]))
    upload(employee, first, "evidence", "giay-to-2.pdf", b"%PDF-1.4 second")
    status, body = act(employee, requests, first, "approve")
    rt.check("T8-10 the requester cannot approve", status == 403, str(status))
    status, body = act(itadmin, requests, first, "approve")
    rt.check("T8-10 approve", status == 200 and body["data"]["status"] == "approved", f"{status} {body}")
    status, _ = upload(employee, first, "evidence", "muon.pdf", b"%PDF-1.4 late")
    rt.check("T8-11 a decided record takes no new file", status == 409, str(status))
    delivered = wait_for(lambda: member(git_group))
    rt.check("T8-10 Keycloak puts the requester in the group", delivered)
    code, rows = rt.psql(f"select count(*) from engine.outbox where record_id={first} and entity='access_request' and delivered_at is not null")
    rt.check("T8-09 one message, delivered", rows == "1", rows)

    # A group that does not exist yet fails, waits and succeeds once the group is created.
    second = create(employee, requests, {"access_group": "VPN_USERS", "reason": "Làm việc từ xa"})
    act(itadmin, requests, second, "approve")
    failed_once = wait_for(lambda: rt.psql(f"select attempts from engine.outbox where record_id={second}")[1] not in ("", "0"))
    code, error = rt.psql(f"select last_error from engine.outbox where record_id={second}")
    rt.check("T8-09 a failed message is kept with its error", failed_once and "VPN_USERS" in error, error)
    kc.kc("POST", "/groups", {"name": "VPN_USERS"})
    status, vpn = kc.kc("GET", "/groups?search=VPN_USERS&exact=true")
    rt.check("T8-09 the message is retried until it succeeds", wait_for(lambda: member(vpn[0]["id"]), 60))
    code, rows = rt.psql(f"select count(*) from engine.outbox where record_id={second}")
    rt.check("T8-09 still one message", rows == "1", rows)

    # A rejected request starts nothing.
    third = create(employee, requests, {"access_group": "WIKI_EDITORS", "reason": "Thử"})
    act(itadmin, requests, third, "reject", "Chưa cần")
    code, rows = rt.psql(f"select count(*) from engine.outbox where record_id={third}")
    rt.check("T8-09 a rejection sends nothing", rows == "0", rows)

    # T8-08: the sweep writes one overdue line to the history.
    rt.psql(f"update m_desk8.ticket set handle_by = now() - interval '2 minutes' where id = {newer}")
    reported = wait_for(lambda: rt.psql(f"select count(*) from engine.record_event where record_id={newer} and entity='ticket' and action='overdue'")[1] == "1", 120)
    rt.check("T8-08 the sweep records the overdue record once", reported)
    code, rows = rt.psql(f"select count(*) from engine.record_event where record_id={newer} and entity='ticket' and action='overdue'")
    rt.check("T8-08 and not again", rows == "1", rows)

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
