# SPDX-License-Identifier: Apache-2.0
"""Checks of the case features: states and actions, units, assignment, inbox and history.

Run after check_recovery.py on the same stack:
    python3 deploy/dev/check_case.py
"""
import os
import subprocess
import sys

import check_api as rt
import kc

YAML = rt.YAML
BASE = "/data/onestop/request"
ASSIGNED_MODEL = """format: 0
name: desk
roles:
  - name: ASKER
  - name: WORKER
  - name: COORD
entities:
  ticket:
    fields:
      title: {type: text, required: true}
    access: {ASKER: own, WORKER: assigned, COORD: all}
    process: {family: approval, handler: WORKER}
"""
REQUEST = {"reason": "Cần giấy xác nhận", "kind": "Giấy xác nhận", "department": "DAO_TAO"}


def up(service, extra_env=None):
    env = dict(os.environ, **(extra_env or {}))
    return subprocess.run(rt.COMPOSE + ["up", "-d", service], capture_output=True, text=True,
                          cwd=f"{rt.REPO}/deploy", env=env)


def act(token, record, action, note=None):
    return kc.api(token, "POST", f"{BASE}/{record}/actions/{action}", {"note": note} if note else {})


def main():
    kc.ensure_client()
    admin = kc.token("admin1")

    # T6-02: rules of the model file.
    for name, needle in [("unit-without-field", "line 10"), ("assigned-without-process", "line 10"),
                         ("duplicate-unit", "line 8")]:
        status, body = kc.api(admin, "POST", "/models/plan", rt.read(f"models/invalid/{name}.yaml"), YAML)
        rt.check(f"T6-02 {name}", status == 400 and needle in body["data"][0], f"{status} {body.get('data')}")

    # T6-01: a model with units is applied without changing the code of the core.
    status, body = kc.api(admin, "POST", "/models/apply", rt.read("backend/dx-engine/src/main/resources/templates/onestop.yaml"), YAML)
    rt.check("T6-01 apply", status == 200 and body["data"]["status"] == "applied", f"{status} {body}")
    status, groups = kc.kc("GET", "/groups?search=DAO_TAO&exact=true")
    rt.check("T6-01 unit is a Keycloak group", status == 200 and groups[0]["name"] == "DAO_TAO", str(groups))

    kc.ensure_user("stu1", ["STUDENT"])
    kc.ensure_user("stu2", ["STUDENT"])
    officer_id = kc.ensure_user("off_dt1", ["OFFICER"], ["DAO_TAO"])
    colleague_id = kc.ensure_user("off_dt2", ["OFFICER"], ["DAO_TAO"])
    other_id = kc.ensure_user("off_ct", ["OFFICER"], ["CTSV"])
    kc.ensure_user("off_two", ["OFFICER"], ["DAO_TAO", "CTSV"])
    kc.ensure_user("both", ["STUDENT", "OFFICER"], ["CTSV"])
    kc.ensure_user("sup", ["SUPERVISOR"])
    stu1, stu2 = kc.token("stu1"), kc.token("stu2")
    officer, colleague, other = kc.token("off_dt1"), kc.token("off_dt2"), kc.token("off_ct")
    two, both, sup = kc.token("off_two"), kc.token("both"), kc.token("sup")

    status, body = kc.api(officer, "GET", "/meta/models")
    meta = body["data"][0]["entities"][0]
    rt.check("T6-01 metadata has states and actions",
             [s["name"] for s in meta["process"]["states"]] == ["submitted", "returned", "approved", "rejected", "withdrawn"]
             and meta["canHandle"] and meta["unitField"] == "department", str(meta["process"]))

    # T6-03, T6-04: rows by unit.
    status, created = kc.api(stu1, "POST", BASE, REQUEST)
    rt.check("T6-03 student creates", status == 200 and created["data"]["status"] == "submitted", f"{status} {created}")
    record = created["data"]["id"]
    status, _ = kc.api(officer, "GET", f"{BASE}/{record}")
    rt.check("T6-03 officer of the unit reads", status == 200, str(status))
    status, _ = kc.api(other, "GET", f"{BASE}/{record}")
    rt.check("T6-03 officer of another unit gets 404", status == 404, str(status))
    status, _ = kc.api(stu2, "GET", f"{BASE}/{record}")
    rt.check("T6-03 another student gets 404", status == 404, str(status))
    status, _ = kc.api(sup, "GET", f"{BASE}/{record}")
    rt.check("T6-03 supervisor reads", status == 200, str(status))
    status, _ = kc.api(sup, "PATCH", f"{BASE}/{record}", {"reason": "x"})
    rt.check("T6-03 supervisor cannot write", status == 403, str(status))
    status, ctsv = kc.api(stu2, "POST", BASE, {**REQUEST, "department": "CTSV"})
    status, listed = kc.api(two, "GET", BASE)
    rt.check("T6-04 two units see both", listed["data"]["totalElements"] == 2, str(listed["data"]["totalElements"]))
    status, mine = kc.api(both, "POST", BASE, {**REQUEST, "department": "DAO_TAO"})
    status, listed = kc.api(both, "GET", BASE)
    ids = {r["id"] for r in listed["data"]["items"]}
    rt.check("T6-04 own and unit rows join", ids == {ctsv["data"]["id"], mine["data"]["id"]}, str(ids))

    # T6-05: a record that belongs to another one follows its unit.
    status, note = kc.api(officer, "POST", "/data/onestop/request_note", {"request": record, "note": "đã nhận"})
    rt.check("T6-05 officer adds a note in the unit", status == 200, f"{status} {note}")
    status, _ = kc.api(other, "POST", "/data/onestop/request_note", {"request": record, "note": "x"})
    rt.check("T6-05 note on a record out of reach", status == 403, str(status))
    status, listed = kc.api(other, "GET", "/data/onestop/request_note")
    rt.check("T6-05 other unit sees no note", listed["data"]["totalElements"] == 0, str(listed["data"]))

    # T6-06: an officer cannot move a record out of their units.
    status, _ = kc.api(officer, "PATCH", f"{BASE}/{record}", {"department": "CTSV"})
    rt.check("T6-06 move to another unit", status == 403, str(status))

    # T6-09: who may act.
    status, _ = act(stu1, record, "approve")
    rt.check("T6-09 student cannot approve", status == 403, str(status))
    status, _ = act(sup, record, "approve")
    rt.check("T6-09 supervisor cannot approve", status == 403, str(status))

    # T6-08: wrong requests.
    status, body = act(officer, record, "return")
    rt.check("T6-08 return without a note", status == 400 and "note" in body["data"], f"{status} {body}")
    status, _ = act(officer, record, "dance")
    rt.check("T6-08 unknown action", status == 404, str(status))
    status, _ = act(stu1, record, "resubmit")
    rt.check("T6-08 action from another state", status == 409, str(status))

    # T6-10, T6-11: assignment.
    status, people = kc.api(officer, "GET", f"{BASE}/{record}/handlers")
    names = {p["id"] for p in people["data"]}
    rt.check("T6-10 handlers are the officers of the unit",
             officer_id in names and colleague_id in names and other_id not in names, str(people["data"]))
    status, body = kc.api(officer, "POST", f"{BASE}/{record}/assign", {"assignee": officer_id})
    rt.check("T6-10 claim", status == 200 and body["data"]["assignee"] == officer_id, f"{status} {body}")
    status, _ = act(colleague, record, "approve")
    rt.check("T6-11 colleague cannot act on an assigned record", status == 403, str(status))
    status, _ = kc.api(officer, "POST", f"{BASE}/{record}/assign", {"assignee": other_id})
    rt.check("T6-11 assign to another unit", status == 400, str(status))
    status, _ = kc.api(stu1, "POST", f"{BASE}/{record}/assign", {"assignee": officer_id})
    rt.check("T6-11 student cannot assign", status == 403, str(status))
    status, body = kc.api(officer, "POST", f"{BASE}/{record}/assign", {"assignee": colleague_id})
    rt.check("T6-10 give to a colleague", status == 200 and body["data"]["assignee"] == colleague_id, str(status))
    status, body = kc.api(colleague, "POST", f"{BASE}/{record}/assign", {"assignee": None})
    rt.check("T6-10 release", status == 200 and body["data"]["assignee"] is None, str(status))
    status, body = kc.api(both, "POST", f"{BASE}/{mine['data']['id']}/assign", {"assignee": other_id})
    rt.check("T6-11 assign to a user out of the unit", status == 400, str(status))
    status, own_unit = kc.api(both, "POST", BASE, {**REQUEST, "department": "CTSV"})
    both_id = kc.ensure_user("both", [])
    status, people = kc.api(both, "GET", f"{BASE}/{own_unit['data']['id']}/handlers")
    rt.check("T6-11 the creator is no handler of their record", both_id not in {p["id"] for p in people["data"]}, str(people["data"]))
    status, _ = kc.api(both, "POST", f"{BASE}/{own_unit['data']['id']}/assign", {"assignee": both_id})
    rt.check("T6-11 the creator cannot take their record", status == 400, str(status))
    status, _ = act(both, own_unit["data"]["id"], "approve")
    rt.check("T6-11 the creator cannot approve their record", status == 403, str(status))

    # T6-12: the inbox.
    def inbox(token):
        return {(i["entity"], i["id"]) for i in kc.api(token, "GET", "/inbox")[1]["data"]["items"]}

    rt.check("T6-12 officer sees the unit's work", ("request", record) in inbox(officer))
    rt.check("T6-12 other unit does not", ("request", record) not in inbox(other))
    rt.check("T6-12 student has nothing to do", ("request", record) not in inbox(stu1))
    kc.api(colleague, "POST", f"{BASE}/{record}/assign", {"assignee": colleague_id})
    rt.check("T6-12 assigned work leaves other inboxes", ("request", record) not in inbox(officer))
    rt.check("T6-12 and stays with its holder", ("request", record) in inbox(colleague))

    # T6-07: return, change, submit again, approve.
    status, body = act(colleague, record, "return", "Thiếu minh chứng")
    rt.check("T6-07 return", status == 200 and body["data"]["status"] == "returned", f"{status} {body}")
    rt.check("T6-12 returned record reaches the student", ("request", record) in inbox(stu1))
    rt.check("T6-12 and leaves the officer", ("request", record) not in inbox(colleague))
    status, _ = act(colleague, record, "approve")
    rt.check("T6-08 approve a returned record", status == 409, str(status))
    status, _ = act(stu2, record, "resubmit")
    rt.check("T6-09 another student cannot resubmit", status == 404, str(status))
    status, _ = act(colleague, record, "resubmit")
    rt.check("T6-09 officer cannot resubmit", status == 403, str(status))
    status, body = kc.api(stu1, "PATCH", f"{BASE}/{record}", {"reason": "Đã bổ sung"})
    rt.check("T6-07 student changes a returned record", status == 200, f"{status} {body}")
    status, body = act(stu1, record, "resubmit")
    rt.check("T6-07 resubmit", status == 200 and body["data"]["status"] == "submitted", f"{status} {body}")
    status, body = act(colleague, record, "approve")
    rt.check("T6-07 approve", status == 200 and body["data"]["status"] == "approved"
             and body["data"]["decided_by"] == colleague_id, f"{status} {body}")
    status, _ = kc.api(stu1, "PATCH", f"{BASE}/{record}", {"reason": "muộn"})
    rt.check("T6-07 a closed record is frozen", status == 409, str(status))
    code, tasks = rt.psql(f"select count(*) from engine.act_ru_execution where business_key_='onestop.request.{record}'")
    rt.check("T6-07 process ended", tasks == "0", tasks)

    # T6-13: the history.
    status, body = kc.api(stu1, "GET", f"{BASE}/{record}/events")
    actions = [e["action"] for e in body["data"]]
    assigned = [e["detail"] for e in body["data"] if e["action"] == "assigned"]
    rt.check("T6-13 assignments name the holder", assigned == ["off_dt1 Test", "off_dt2 Test", None, "off_dt2 Test"], str(assigned))
    rt.check("T6-13 history in order", actions == ["created", "assigned", "assigned", "assigned", "assigned",
                                                   "return", "updated", "resubmit", "approve"], str(actions))
    returned = next(e for e in body["data"] if e["action"] == "return")
    rt.check("T6-13 action details", returned["note"] == "Thiếu minh chứng" and returned["from_status"] == "submitted"
             and returned["to_status"] == "returned" and returned["actor"] == colleague_id, str(returned))
    updated = next(e for e in body["data"] if e["action"] == "updated")
    rt.check("T6-13 no field value kept", updated["detail"] == "reason", str(updated))
    status, _ = kc.api(other, "GET", f"{BASE}/{record}/events")
    rt.check("T6-13 history follows read access", status == 404, str(status))
    status, same = kc.api(stu2, "POST", BASE, REQUEST)
    same_id = same["data"]["id"]
    status, partly = kc.api(stu2, "PATCH", f"{BASE}/{same_id}", {**REQUEST, "reason": "Khác"})
    status, _ = kc.api(stu2, "PATCH", f"{BASE}/{same_id}", {**REQUEST, "reason": "Khác"})
    status, body = kc.api(stu2, "GET", f"{BASE}/{same_id}/events")
    details = [e["detail"] for e in body["data"] if e["action"] == "updated"]
    rt.check("T6-13 only changed fields are recorded", details == ["reason"], str(details))
    status, doomed = kc.api(stu2, "POST", BASE, REQUEST)
    status, _ = kc.api(stu2, "DELETE", f"{BASE}/{doomed['data']['id']}")
    rt.check("T8-05 a record with a process cannot be deleted", status == 409, str(status))
    status, body = act(stu2, doomed["data"]["id"], "withdraw", "Nộp nhầm")
    code, kept = rt.psql(f"select count(*) from engine.record_event where record_id={doomed['data']['id']} and entity='request' and action='withdraw'")
    rt.check("T8-04 withdrawing keeps the record and its history", status == 200 and body["data"]["status"] == "withdrawn" and kept == "1", f"{status} {kept}")

    # T6-16: a role that reaches only the records assigned to it.
    status, body = kc.api(admin, "POST", "/models/apply", ASSIGNED_MODEL, YAML)
    rt.check("T6-16 model applied", status == 200, f"{status} {body}")
    worker_id = kc.ensure_user("worker1", ["WORKER"])
    kc.ensure_user("coord1", ["COORD"])
    kc.ensure_user("asker1", ["ASKER"])
    worker, asker = kc.token("worker1"), kc.token("asker1")
    coord = kc.token("coord1")
    tickets = "/data/desk/ticket"
    status, ticket = kc.api(asker, "POST", tickets, {"title": "Máy in hỏng"})
    ticket_id = ticket["data"]["id"]
    status, _ = kc.api(worker, "GET", f"{tickets}/{ticket_id}")
    rt.check("T6-16 unassigned record out of reach", status == 404, str(status))
    status, _ = kc.api(worker, "POST", tickets, {"title": "x"})
    rt.check("T6-16 assigned scope cannot create", status == 403, str(status))
    status, _ = kc.api(asker, "POST", f"{tickets}/{ticket_id}/assign", {"assignee": worker_id})
    rt.check("T6-16 the asker cannot assign", status == 403, str(status))
    status, body = kc.api(coord, "POST", f"{tickets}/{ticket_id}/assign", {"assignee": worker_id})
    rt.check("T6-16 coordinator assigns", status == 200 and body["data"]["assignee"] == worker_id, f"{status} {body}")
    status, _ = kc.api(coord, "POST", f"{tickets}/{ticket_id}/actions/approve", {})
    rt.check("T6-16 coordinator does not decide", status == 403, str(status))
    status, body = kc.api(worker, "POST", f"{tickets}/{ticket_id}/assign", {"assignee": None})
    rt.check("T6-16 holder gives the record back", status == 200 and body["data"]["assignee"] is None, f"{status} {body}")
    status, _ = kc.api(worker, "GET", f"{tickets}/{ticket_id}")
    rt.check("T6-16 and it leaves their reach", status == 404, str(status))
    kc.api(coord, "POST", f"{tickets}/{ticket_id}/assign", {"assignee": worker_id})
    status, listed = kc.api(worker, "GET", tickets)
    rt.check("T6-16 assigned record in reach", [r["id"] for r in listed["data"]["items"]] == [ticket_id], str(listed["data"]))
    status, body = kc.api(worker, "POST", f"{tickets}/{ticket_id}/actions/approve", {})
    rt.check("T6-16 holder acts", status == 200 and body["data"]["status"] == "approved", f"{status} {body}")

    # T6-14: a failure after the process step leaves no trace.
    status, fresh = kc.api(stu1, "POST", BASE, REQUEST)
    fresh_id = fresh["data"]["id"]
    up("backend", {"ENGINE_FAULT_AFTER_DECISION": "true"})
    assert rt.wait_api(), "backend did not come back"
    status, _ = act(kc.token("off_dt1"), fresh_id, "return", "lỗi cố ý")
    rt.check("T6-14 fault gives a server error", status == 500, str(status))
    code, state = rt.psql(f"select status from m_onestop.request where id={fresh_id}")
    rt.check("T6-14 record unchanged", state == "submitted", state)
    code, events = rt.psql(f"select count(*) from engine.record_event where record_id={fresh_id} and entity='request' and action='return'")
    rt.check("T6-14 no event of the failed action", events == "0", events)
    up("backend", {"ENGINE_FAULT_AFTER_DECISION": "false"})
    assert rt.wait_api(), "backend did not come back"

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
