# SPDX-License-Identifier: Apache-2.0
"""Checks of the webhook for the history lines of records.

Run after check_users.py on the same stack (it applies the onestop model when it is missing):
    python3 deploy/dev/check_events.py
The script starts a receiver on this machine, restarts the backend with its address, and leaves the
backend without a webhook at the end.
"""
import hashlib
import hmac
import json
import os
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import check_api as rt
import kc

SECRET = "check-events-secret"
KEYS = {"eventId", "model", "entity", "recordId", "action", "fromStatus", "toStatus", "actor", "actorName", "at"}
BASE = "/data/onestop/request"
received = []
mode = {"answer": 200}
lock = threading.Lock()


class Receiver(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        good = "sha256=" + hmac.new(SECRET.encode(), body, hashlib.sha256).hexdigest()
        with lock:
            code = mode["answer"]
            received.append({"code": code, "body": json.loads(body), "signed": self.headers.get("X-Dxos-Signature") == good,
                             "header": self.headers.get("X-Dxos-Event-Id"), "type": self.headers.get("Content-Type")})
        self.send_response(code)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def log_message(self, *args):
        pass


def start_backend(extra_env=None):
    env = dict(os.environ, **(extra_env or {}))
    subprocess.run(rt.COMPOSE + ["up", "-d", "backend"], capture_output=True, text=True, cwd=f"{rt.REPO}/deploy", env=env)
    assert rt.wait_api(), "backend did not come back"


def gateway():
    # The address of this machine as the backend container sees it.
    container = rt.sh(rt.COMPOSE + ["ps", "-q", "backend"]).stdout.strip()
    return rt.sh(["docker", "inspect", "-f", "{{range .NetworkSettings.Networks}}{{.Gateway}}{{end}}", container]).stdout.strip()


def delivered(event_id=None, code=200):
    with lock:
        return [r for r in received if r["code"] == code and (event_id is None or r["body"]["eventId"] == event_id)]


def wait_for(condition, seconds):
    end = time.time() + seconds
    while time.time() < end:
        if condition():
            return True
        time.sleep(2)
    return condition()


def count(sql):
    return int(rt.psql(sql)[1])


def act(token, record, action, body=None):
    return kc.api(token, "POST", f"{BASE}/{record}/actions/{action}", body or {})


def main():
    assert rt.wait_api(), "the backend does not answer"
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")
    status, models = kc.api(admin, "GET", "/models")
    if not any(m.get("model") == "onestop" for m in models["data"]):
        status, _ = kc.api(admin, "POST", "/models/apply", rt.read("backend/dx-engine/src/main/resources/templates/onestop.yaml"), rt.YAML)
        assert status == 200, status
    kc.ensure_user("stu1", ["onestop/STUDENT"])
    officer_id = kc.ensure_user("off1", ["onestop/OFFICER"], ["DAO_TAO"])
    stu, off = kc.token("stu1"), kc.token("off1")

    server = ThreadingHTTPServer(("0.0.0.0", 0), Receiver)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    webhook = {"DXOS_WEBHOOK_URL": f"http://{gateway()}:{server.server_port}/hook", "DXOS_WEBHOOK_SECRET": SECRET}
    first_line = count("select coalesce(max(id), 0) from engine.record_event") + 1
    queued = count("select count(*) from engine.outbox where topic = 'record.event'")
    start_backend(webhook)

    status, created = kc.api(stu, "POST", BASE, {"reason": "secret reason", "kind": "Phúc khảo", "department": "DAO_TAO"})
    record = created["data"]["id"]
    kc.api(stu, "PATCH", f"{BASE}/{record}", {"reason": "second reason"})
    kc.api(off, "POST", f"{BASE}/{record}/assign", {"assignee": officer_id})
    act(off, record, "return", {"note": "secret note"})
    act(stu, record, "resubmit")
    act(off, record, "approve")
    status, note = kc.api(stu, "POST", "/data/onestop/request_note", {"request": record, "note": "secret note two"})
    kc.api(stu, "DELETE", f"/data/onestop/request_note/{note['data']['id']}")
    wait_for(lambda: len(delivered()) >= 8, 30)
    got = sorted(delivered(), key=lambda r: r["body"]["eventId"])

    rt.check("EV-01 every change of the record reaches the webhook once, in the order of eventId",
             [r["body"]["action"] for r in got] == ["created", "updated", "assigned", "return", "resubmit", "approve", "created", "deleted"],
             str([r["body"]["action"] for r in got]))
    rt.check("EV-02 each request is signed with the secret and carries its event id", bool(got) and all(r["signed"] and r["header"] == str(r["body"]["eventId"]) and r["type"] == "application/json" for r in got))
    rt.check("EV-03 the message holds only ids, statuses, the actor and the time", bool(got) and all(set(r["body"]) == KEYS for r in got))
    rt.check("EV-04 no content of the record or of a note is in the message", "secret" not in json.dumps([r["body"] for r in got]))
    rt.check("EV-05 the statuses of a step are before and after", got[5]["body"]["fromStatus"] == "submitted" and got[5]["body"]["toStatus"] == "approved" and got[5]["body"]["actor"] != "system")
    lines = count(f"select count(*) from engine.record_event where id >= {first_line}")
    queued_now = count("select count(*) from engine.outbox where topic = 'record.event'") - queued
    rt.check("EV-06 every line of the history has one message in the outbox", lines == queued_now and lines >= 8, f"{lines} lines, {queued_now} messages")

    boundary = "dxosevents"
    upload = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"evidence-name.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").encode() + b"%PDF-1.4 x" + f"\r\n--{boundary}--\r\n".encode()
    status, open_record = kc.api(stu, "POST", BASE, {"reason": "with a file", "kind": "Phúc khảo", "department": "DAO_TAO"})
    status, _ = kc.api(stu, "POST", f"{BASE}/{open_record['data']['id']}/files?field=evidence", upload, f"multipart/form-data; boundary={boundary}")
    wait_for(lambda: any(r["body"]["action"] == "file_added" for r in delivered()), 30)
    files = [r for r in delivered() if r["body"]["action"] == "file_added"]
    rt.check("EV-07 an attachment is reported without its file name", status == 200 and len(files) == 1 and "evidence-name" not in json.dumps(files[0]["body"]))

    mode["answer"] = 500
    status, late = kc.api(stu, "POST", BASE, {"reason": "while the receiver fails", "kind": "Hoãn thi", "department": "DAO_TAO"})
    event = count(f"select max(id) from engine.record_event where record_id = {late['data']['id']} and entity = 'request'")
    wait_for(lambda: len(delivered(event, 500)) >= 2, 40)
    tried = len(delivered(event, 500))
    mode["answer"] = 200
    ok = wait_for(lambda: len(delivered(event)) >= 1, 120)
    time.sleep(10)
    rt.check("EV-08 a message the receiver refused is tried again and delivered once when it answers", tried >= 2 and ok and len(delivered(event)) == 1, f"{tried} refused, {len(delivered(event))} delivered")

    status, stale = kc.api(stu, "POST", BASE, {"reason": "late", "kind": "Hoãn thi", "department": "DAO_TAO"})
    stale_id = stale["data"]["id"]
    rt.psql(f"update m_onestop.request set handle_by = now() - interval '1 hour' where id = {stale_id}")
    overdue = lambda: [r for r in delivered() if r["body"]["action"] == "overdue" and r["body"]["recordId"] == stale_id]
    rt.check("EV-09 a record past its deadline is reported once with the system as the actor", wait_for(overdue, 150) and len(overdue()) == 1 and overdue()[0]["body"]["actor"] == "system" and overdue()[0]["signed"])

    start_backend({**webhook, "ENGINE_FAULT_AFTER_DECISION": "true"})
    status, fresh = kc.api(stu, "POST", BASE, {"reason": "rollback", "kind": "Hoãn thi", "department": "DAO_TAO"})
    wait_for(lambda: False, 6)
    before_lines = count("select count(*) from engine.record_event")
    before_messages = count("select count(*) from engine.outbox where topic = 'record.event'")
    seen = len(received)
    status, _ = act(off, fresh["data"]["id"], "approve")
    wait_for(lambda: False, 6)
    rt.check("EV-10 a decision that is rolled back leaves no line, no message and no request",
             status == 500 and count("select count(*) from engine.record_event") == before_lines
             and count("select count(*) from engine.outbox where topic = 'record.event'") == before_messages and len(received) == seen)

    rt.psql("insert into engine.outbox (topic, idempotency_key, model, entity, record_id, payload, delivered_at) values "
            "('record.event', 'check-old', 'onestop', 'request', 1, '{}', now() - interval '40 days'), "
            "('record.event', 'check-recent', 'onestop', 'request', 1, '{}', now() - interval '1 day')")
    start_backend({"DXOS_WEBHOOK_URL": "", "DXOS_WEBHOOK_SECRET": ""})
    seen = len(received)
    lines_before = count("select count(*) from engine.record_event")
    messages_before = count("select count(*) from engine.outbox where topic = 'record.event'")
    kc.api(stu, "PATCH", f"{BASE}/{fresh['data']['id']}", {"reason": "no webhook"})
    wait_for(lambda: False, 6)
    rt.check("EV-11 without an address the history is written and nothing is queued or sent",
             count("select count(*) from engine.record_event") == lines_before + 1
             and count("select count(*) from engine.outbox where topic = 'record.event'") == messages_before and len(received) == seen)
    rt.check("EV-12 a message delivered long ago is deleted and a recent one is kept",
             count("select count(*) from engine.outbox where idempotency_key = 'check-old'") == 0
             and count("select count(*) from engine.outbox where idempotency_key = 'check-recent'") == 1)
    rt.psql("delete from engine.outbox where idempotency_key = 'check-recent'")
    server.shutdown()

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
