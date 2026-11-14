# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Checks of the common process: families, the rules every process obeys, deadlines.

Run after check_case.py on the same stack:
    python3 e2e/api/check_process.py
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
    asker_id = kc.ensure_user("asker8", ["desk8/ASKER"])
    kc.ensure_user("agent8", ["desk8/AGENT"])
    kc.ensure_user("agent9", ["desk8/AGENT"])
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
    status, body = kc.api(asker, "PATCH", f"{LEAVES}/{leave}", {"reason": "Sửa sau khi rút"})
    rt.check("T8-04 a withdrawn record cannot be changed, and says it is closed", status == 409 and (body.get("data") or {}).get("reason") == "RECORD_CLOSED", f"{status} {body}")
    status, body = kc.api(asker, "DELETE", f"{LEAVES}/{leave}")
    rt.check("T8-04 a record with a process is kept, and says so", status == 409 and (body.get("data") or {}).get("reason") == "KEPT_BY_PROCESS", f"{status} {body}")

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

    # T8-09: the counts of an entity agree with its list and stay inside what the caller may read.
    def counts_match(token):
        status, stats = kc.api(token, "GET", f"{TICKETS}/stats")
        status_list, listed = kc.api(token, "GET", f"{TICKETS}?size=200")
        data, rows = stats["data"], listed["data"]["items"]
        return (status == 200 and status_list == 200 and data["total"] == listed["data"]["totalElements"]
                and sum(item["count"] for item in data["byStatus"]) == data["total"]
                and data["overdue"] == sum(1 for row in rows if row["overdue"]), data)
    ok, data = counts_match(agent)
    rt.check("T8-09 stats agree with the list: total, per status and overdue", ok and data["overdue"] >= 1, str(data))
    ok, data = counts_match(asker)
    rt.check("T8-09 the owner's stats count only their own tickets", ok and data["total"] >= 2, str(data))
    rt.psql(f"update m_desk8.ticket set created_at = now() - interval '2 hours' where id = {older}")
    rt.psql(f"update m_desk8.ticket set decided_at = created_at + interval '600 seconds' where id = {newer}")
    status, stats = kc.api(agent, "GET", f"{TICKETS}/stats")
    data = stats["data"]
    code, expected = rt.psql("select round(avg(extract(epoch from decided_at - created_at))) from m_desk8.ticket where decided_at is not null")
    rt.check("T8-09 stats give the mean time to the final state", abs(data["avgDecisionSeconds"] - int(expected)) <= 1 and data["avgDecisionSeconds"] >= 1, f"{data} {expected}")
    rt.check("T8-09 and the age of the open records", data["oldestOpenAgeSeconds"] >= 7200 and 0 < data["avgOpenAgeSeconds"] <= data["oldestOpenAgeSeconds"], str(data))
    rt.psql(f"update m_desk8.ticket set decided_at = NULL where id = {newer}")
    status, stats = kc.api(agent, "GET", f"{TICKETS}/stats?filter.title=no-such-title")
    data = stats.get("data") or {}
    rt.check("T8-09 a figure with no record behind it is null",
             status == 200 and data.get("total") == 0 and all(data.get(k) is None for k in ("avgDecisionSeconds", "avgOpenAgeSeconds", "oldestOpenAgeSeconds")), str(data))

    # QL: the quality of an entity covers the rows the caller may read and nothing else.
    status, quality = kc.api(agent, "GET", f"{TICKETS}/quality")
    status_stats, stats = kc.api(agent, "GET", f"{TICKETS}/stats")
    data = quality.get("data") or {}
    metrics = data.get("metrics") or {}
    title = next((f for f in (metrics.get("completeness") or {}).get("fields", []) if f["field"] == "title"), {})
    rt.check("QL-01 the sample is the rows the list returns, with the moment of the measure",
             status == 200 and data.get("sampleSize") == stats["data"]["total"] and data.get("measuredAt")
             and set(metrics) == {"completeness", "requiredWhen", "constraints", "freshness"}, f"{status} {data}")
    rt.check("QL-02 a required field is filled in every record", title.get("filled") == data.get("sampleSize") and title.get("rate") == 1.0, str(title))
    status, quality = kc.api(asker, "GET", f"{TICKETS}/quality")
    status_stats, stats = kc.api(asker, "GET", f"{TICKETS}/stats")
    rt.check("QL-03 the owner's sample is the tickets the owner can list",
             status == 200 and quality["data"]["sampleSize"] == stats["data"]["total"] and stats["data"]["total"] > 0,
             f"{quality.get('data')} {stats.get('data')}")
    status, quality = kc.api(agent, "GET", f"{TICKETS}/quality?filter.title=no-such-title")
    empty = quality.get("data") or {}
    title = next((f for f in empty["metrics"]["completeness"]["fields"] if f["field"] == "title"), {}) if empty else {}
    rt.check("QL-04 with no record the measures are still defined: rate and dates are null, counts are 0",
             status == 200 and empty.get("sampleSize") == 0 and title.get("rate") is None and title.get("filled") == 0
             and empty["metrics"]["freshness"]["oldest"] is None and empty["metrics"]["freshness"]["stale"] == 0, str(empty))
    rt.psql(f"update m_desk8.ticket set updated_at = now() - interval '100 days' where id = {older}")
    status, quality = kc.api(agent, "GET", f"{TICKETS}/quality")
    status_wide, wide = kc.api(agent, "GET", f"{TICKETS}/quality?staleDays=200")
    fresh, fresh_wide = quality["data"]["metrics"]["freshness"], wide["data"]["metrics"]["freshness"]
    rt.check("QL-05 a record not changed within staleDays is counted, and a longer period leaves it out",
             fresh["staleDays"] == 90 and fresh["stale"] >= 1 and fresh_wide["staleDays"] == 200 and fresh_wide["stale"] < fresh["stale"],
             f"{fresh} {fresh_wide}")
    rt.check("QL-06 measuring again gives the same figures",
             kc.api(agent, "GET", f"{TICKETS}/quality?staleDays=200")[1]["data"]["metrics"] == wide["data"]["metrics"], "")
    rt.psql("update m_desk8.ticket set updated_at = now() - interval '300 days' where decided_at is not null")
    status, quality = kc.api(agent, "GET", f"{TICKETS}/quality")
    aged = quality["data"]["metrics"]["freshness"]
    rt.check("QL-09 a record that reached its final state is not counted as stale",
             aged["stale"] == fresh["stale"] and aged["oldest"] < fresh["oldest"], f"{fresh} {aged}")
    status, body = kc.api(agent, "GET", f"{TICKETS}/quality?staleDays=0")
    rt.check("QL-07 staleDays outside 1 to 3650 is refused", status == 400, f"{status} {body}")
    kc.ensure_user("outsider8", [])
    status, body = kc.api(kc.token("outsider8"), "GET", f"{TICKETS}/quality")
    rt.check("QL-08 a caller whose roles do not reach the entity gets nothing", status == 403 and (body.get("data") or {}).get("reason") == "NO_ROLE_FOR_ENTITY", f"{status} {body}")

    status, quality = kc.api(admin, "GET", f"{TICKETS}/quality")
    code, stored = rt.psql("select count(*) from m_desk8.ticket")
    rt.check("QL-10 an administrator is measured over every row, and gets counts only",
             status == 200 and str(quality["data"]["sampleSize"]) == stored and "items" not in quality["data"], f"{status} {quality.get('data')} {stored}")

    # T8-10: an update that names the version it read does not overwrite a newer one.
    mine = create(asker, TICKETS, {"title": "Version"})
    status, body = kc.api(asker, "GET", f"{TICKETS}/{mine}")
    read_at = body["data"]["version"]
    status, body = kc.api(asker, "PATCH", f"{TICKETS}/{mine}", {"title": "First", "version": read_at})
    rt.check("T8-10 an update at the current version applies and moves the version",
             status == 200 and body["data"]["version"] == read_at + 1, f"{status} {body.get('data')}")
    status, body = kc.api(asker, "PATCH", f"{TICKETS}/{mine}", {"title": "Second", "version": read_at})
    rt.check("T8-10 an update at an older version is refused", status == 409 and body.get("errorCode") == "ERR_CONCURRENT_MODIFICATION", f"{status} {body}")
    status, body = kc.api(asker, "GET", f"{TICKETS}/{mine}")
    rt.check("T8-10 and the record keeps the newer content", body["data"]["title"] == "First", str(body["data"]["title"]))
    status, body = kc.api(asker, "PATCH", f"{TICKETS}/{mine}", {"title": "Third", "version": "x"})
    rt.check("T8-10 a version that is not a number is refused", status == 400, f"{status} {body}")
    status, body = kc.api(asker, "PATCH", f"{TICKETS}/{mine}", {"title": "Fourth"})
    rt.check("T8-10 an update without a version applies to the current record", status == 200 and body["data"]["title"] == "Fourth", f"{status} {body}")
    status, body = kc.api(asker, "GET", f"{TICKETS}/{mine}")
    read_at = body["data"]["version"]
    status, _ = act(agent, TICKETS, mine, "start")
    status, body = kc.api(asker, "PATCH", f"{TICKETS}/{mine}", {"title": "Late", "version": read_at})
    rt.check("T8-10 a step of the process in between also makes the version stale", status == 409, f"{status} {body}")

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
    employee_id = kc.ensure_user("emp8", ["itaccess/EMPLOYEE"])
    kc.ensure_user("itadmin8", ["itaccess/IT_ADMIN"])
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
    other = kc.ensure_user("emp8b", ["itaccess/EMPLOYEE"])
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
    code, rows = rt.psql(f"select count(*) from engine.outbox where topic='completion' and record_id={first} and entity='access_request' and delivered_at is not null")
    rt.check("T8-09 one message, delivered", rows == "1", rows)

    # A group that does not exist yet fails, waits and succeeds once the group is created.
    second = create(employee, requests, {"access_group": "VPN_USERS", "reason": "Làm việc từ xa"})
    act(itadmin, requests, second, "approve")
    failed_once = wait_for(lambda: rt.psql(f"select attempts from engine.outbox where topic='completion' and record_id={second}")[1] not in ("", "0"))
    code, error = rt.psql(f"select last_error from engine.outbox where topic='completion' and record_id={second}")
    rt.check("T8-09 a failed message is kept with its error", failed_once and "VPN_USERS" in error, error)
    kc.kc("POST", "/groups", {"name": "VPN_USERS"})
    status, vpn = kc.kc("GET", "/groups?search=VPN_USERS&exact=true")
    rt.check("T8-09 the message is retried until it succeeds", wait_for(lambda: member(vpn[0]["id"]), 60))
    code, rows = rt.psql(f"select count(*) from engine.outbox where topic='completion' and record_id={second}")
    rt.check("T8-09 still one message", rows == "1", rows)

    # A rejected request starts nothing.
    third = create(employee, requests, {"access_group": "WIKI_EDITORS", "reason": "Thử"})
    act(itadmin, requests, third, "reject", "Chưa cần")
    code, rows = rt.psql(f"select count(*) from engine.outbox where topic='completion' and record_id={third}")
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
