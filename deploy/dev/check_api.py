# SPDX-License-Identifier: Apache-2.0
"""API checks of the model-driven service on a stack with an empty database.

Run from the repository root after  in deploy/:
    python3 deploy/dev/check_api.py
Settings: DXOS_ENV_FILE, DXOS_BASE_URL, DXOS_PROJECT (compose project name, optional).
"""
import os
import statistics
import subprocess
import sys
import time

import kc

REPO = kc.REPO
COMPOSE = ["docker", "compose", "--env-file", kc.ENV_FILE] + (["-p", os.environ["DXOS_PROJECT"]] if os.environ.get("DXOS_PROJECT") else [])
YAML = "application/yaml"
results = []


def check(case, ok, detail=""):
    results.append((case, ok))
    print(f"{'PASS' if ok else 'FAIL'} {case} {detail}")


def sh(args, **kw):
    return subprocess.run(args, capture_output=True, text=True, cwd=f"{REPO}/deploy", **kw)


def psql(sql, user="dxos", password=None, db="dxos"):
    command = COMPOSE + ["exec", "-T"]
    if password:
        command += ["-e", f"PGPASSWORD={password}"]
    command += ["postgres", "psql", "-U", user, "-d", db, "-tA", "-c", sql]
    result = sh(command)
    return result.returncode, (result.stdout + result.stderr).strip()


def read(path):
    return open(f"{REPO}/{path}", encoding="utf-8").read()


def wait_api(seconds=120):
    end = time.time() + seconds
    while time.time() < end:
        status, _ = kc.http("GET", f"{kc.BASE}/api/v1/system/status")
        if status == 200:
            return True
        time.sleep(2)
    return False


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")

    # T5-02, T5-03: files with problems are refused with their line, and nothing changes.
    for name, needle in [("unknown-reference", "line 9"), ("bad-name", "line 9"),
                         ("unknown-role", "line 10"), ("unsupported-format", "line 2")]:
        status, body = kc.api(admin, "POST", "/models/plan", read(f"models/invalid/{name}.yaml"), YAML)
        ok = status == 400 and body["errorCode"] == "ERR_MODEL_INVALID" and needle in body["data"][0]
        check(f"T5-02/03 {name}", ok, f"{status} {body.get('data') if isinstance(body, dict) else body}")
    status, body = kc.api(admin, "GET", "/models")
    check("T5-02 nothing changed", status == 200 and body["data"] == [], str(body))

    # T5-01: apply into an empty system.
    equipment = read("models/equipment.yaml")
    status, body = kc.api(admin, "POST", "/models/plan", equipment, YAML)
    check("T5-01 plan lists steps", status == 200 and body["data"]["action"] == "create"
          and len(body["data"]["steps"]) > 8, f"{status} steps={len(body['data']['steps'])}")
    status, body = kc.api(admin, "POST", "/models/apply", equipment, YAML)
    check("T5-01 apply", status == 200 and body["data"]["status"] == "applied", f"{status} {body}")
    code, tables = psql("select table_name from information_schema.tables where table_schema='m_equipment' order by 1")
    check("T5-01 tables", tables.split() == ["device", "loan", "loan_note"], tables)
    code, constraints = psql("select count(*) from pg_constraint where conname like 'fk_%' or conname like 'uq_%' or conname like 'ck_%'")
    check("T5-01 constraints", int(constraints) >= 4, constraints)
    status, role = kc.kc("GET", "/roles/BORROWER")
    check("T5-01 keycloak role", status == 200)
    status, body = kc.api(admin, "GET", "/models/equipment/runs")
    check("T5-01 history", status == 200 and len(body["data"]) == 1 and body["data"][0]["status"] == "done")

    # T5-04: applying the same file again changes nothing.
    status, body = kc.api(admin, "POST", "/models/apply", equipment, YAML)
    check("T5-04 idempotent", status == 200 and body["data"]["status"] == "unchanged", str(body["data"]))
    status, body = kc.api(admin, "GET", "/models/equipment/runs")
    check("T5-04 no new run", len(body["data"]) == 1)
    changed = equipment.replace("Phiếu mượn\n", "Phiếu mượn mới\n", 1)
    status, body = kc.api(admin, "POST", "/models/apply", changed, YAML)
    check("T5-04 changed file refused", status == 409, f"{status}")

    # Users.
    for user, roles in [("req1", ["BORROWER"]), ("req2", ["BORROWER"]), ("keeper1", ["KEEPER"]),
                        ("multi1", ["BORROWER", "KEEPER"]), ("none1", [])]:
        kc.ensure_user(user, roles)
    req1, req2, keeper, multi, none = (kc.token(u) for u in ("req1", "req2", "keeper1", "multi1", "none1"))

    # Devices (keeper writes, borrowers read).
    ids = []
    for index, kind in enumerate(["laptop", "projector", "camera"]):
        status, body = kc.api(keeper, "POST", "/data/equipment/device",
                              {"code": f"D-{index}", "name": f"Device {index}", "kind": kind})
        ids.append(body["data"]["id"] if status == 200 else None)
    check("T5-06 keeper creates devices", all(ids), str(ids))
    status, body = kc.api(req1, "POST", "/data/equipment/device", {"code": "X", "name": "x"})
    check("T5-06 borrower cannot write devices", status == 403, str(status))
    status, body = kc.api(req1, "GET", "/data/equipment/device")
    check("T5-06 borrower reads devices", status == 200 and body["data"]["totalElements"] == 3)
    status, body = kc.api(keeper, "POST", "/data/equipment/device", {"code": "D-0", "name": "dup"})
    check("T5-02 unique rule", status == 400 and "code" in body["data"], f"{status} {body.get('data')}")
    status, body = kc.api(keeper, "POST", "/data/equipment/device", {"code": "D-9", "name": "n", "kind": "tank"})
    check("T5-02 choice rule", status == 400 and "kind" in body["data"])

    # T5-06: ownership and roles.
    loan = {"device": ids[0], "reason": "class", "from_at": "2026-11-10T08:00:00+07:00", "quantity": 2}
    status, b1 = kc.api(req1, "POST", "/data/equipment/loan", loan)
    check("T5-06 create loan", status == 200 and b1["data"]["status"] == "submitted", f"{status} {b1}")
    status, b2 = kc.api(req2, "POST", "/data/equipment/loan", {**loan, "reason": "lab"})
    loan1, loan2 = b1["data"]["id"], b2["data"]["id"]
    status, body = kc.api(req1, "GET", "/data/equipment/loan")
    check("T5-06 own rows only", status == 200 and [r["id"] for r in body["data"]["items"]] == [loan1])
    status, body = kc.api(keeper, "GET", "/data/equipment/loan")
    check("T5-06 keeper sees all", body["data"]["totalElements"] == 2)
    status, body = kc.api(none, "GET", "/data/equipment/loan")
    check("T5-06 no role 403", status == 403, str(status))
    status, body = kc.api(None, "GET", "/data/equipment/loan")
    check("T5-06 anonymous 401", status == 401, str(status))
    status, body = kc.api(req1, "GET", f"/data/equipment/loan/{loan2}")
    check("T5-06 foreign row 404", status == 404, str(status))
    status, body = kc.api(multi, "GET", "/data/equipment/loan")
    check("T5-07 two roles union", status == 200 and body["data"]["totalElements"] == 2)

    # T5-08: ownership through the record an entity belongs to.
    status, n1 = kc.api(req1, "POST", "/data/equipment/loan_note", {"loan": loan1, "note": "mine"})
    status2, n2 = kc.api(keeper, "POST", "/data/equipment/loan_note", {"loan": loan2, "note": "theirs"})
    check("T5-08 create notes", status == 200 and status2 == 200, f"{status} {status2}")
    status, body = kc.api(req1, "GET", "/data/equipment/loan_note")
    check("T5-08 own through parent", status == 200 and [r["note"] for r in body["data"]["items"]] == ["mine"], str(body["data"]))
    status, body = kc.api(req1, "POST", "/data/equipment/loan_note", {"loan": loan2, "note": "intrusion"})
    check("T5-08 cannot attach to a foreign parent", status == 403, str(status))

    # T5-09: the status cannot be written.
    for who, token in (("keeper", keeper), ("req1", req1)):
        status, body = kc.api(token, "PATCH", f"/data/equipment/loan/{loan1}", {"status": "approved"})
        check(f"T5-09 {who} patch status", status == 403, str(status))
    status, body = kc.api(req1, "POST", "/data/equipment/loan", {**loan, "status": "approved"})
    check("T5-09 create with status", status == 403, str(status))

    # T5-13, T5-12: decisions.
    status, body = kc.api(req1, "POST", f"/data/equipment/loan/{loan1}/decision", {"outcome": "approve"})
    check("T5-13 requester cannot decide", status == 403, str(status))
    status, body = kc.api(keeper, "POST", f"/data/equipment/loan/{loan1}/decision", {"outcome": "approve"})
    check("T5-12 approve", status == 200 and body["data"]["status"] == "approved", f"{status} {body}")
    code, keeper_id = psql("select assignee_ from engine.act_hi_taskinst where assignee_ is not null limit 1")
    check("T5-19 decider and time are stored", body["data"]["decided_by"] == keeper_id != ""
          and body["data"]["decided_at"] is not None, f"{body['data'].get('decided_by')} {keeper_id}")
    status, sent = kc.api(keeper, "PATCH", f"/data/equipment/loan/{loan2}", {"decided_by": "someone"})
    check("T5-19 decider cannot be sent", status == 400, str(status))
    status, body = kc.api(keeper, "POST", f"/data/equipment/loan/{loan1}/decision", {"outcome": "approve"})
    check("T5-12 second decision 409", status == 409, str(status))
    code, task_count = psql("select count(*) from engine.act_ru_task")
    check("T5-12 only the other pending task remains", task_count == "1", task_count)

    # T5-10: the report role reads but cannot write.
    code, out = psql("select count(*) from m_equipment.loan", user="report_ro", password=kc.ENV["REPORT_DB_PASSWORD"])
    check("T5-10 report role reads", code == 0 and out == "2", out)
    code, out = psql("delete from m_equipment.loan", user="report_ro", password=kc.ENV["REPORT_DB_PASSWORD"])
    check("T5-10 report role cannot write", code != 0 and "permission denied" in out, out[:100])

    # T5-14: values and names never become SQL.
    for label, path in [("filter value", "/data/equipment/loan?filter.reason=x'%20OR%201=1%20--"),
                        ]:
        status, body = kc.api(keeper, "GET", path)
        check(f"T5-14 {label}", status == 200 and body["data"]["totalElements"] == 0, f"{status}")
    for label, path in [("sort name", "/data/equipment/loan?sort=id;drop%20table%20x"),
                        ("filter name", "/data/equipment/loan?filter.reason;drop=1"),
                        ("operator", "/data/equipment/loan?filter.reason.xx=1")]:
        status, body = kc.api(keeper, "GET", path)
        check(f"T5-14 {label}", status == 400, f"{status}")
    code, out = psql("select count(*) from m_equipment.loan")
    check("T5-14 data intact", out == "2", out)

    # T5-15: 10,000 rows with filters and sorting.
    psql("insert into m_equipment.loan (created_by, device, reason, from_at, quantity) "
         "select 'seed-' || (g % 50), (select id from m_equipment.device limit 1), 'bulk ' || g, now(), g "
         "from generate_series(1, 10000) g")
    times = []
    for i in range(50):
        started = time.perf_counter()
        status, body = kc.api(keeper, "GET", f"/data/equipment/loan?filter.created_by=seed-{i % 50}"
                              "&filter.quantity.gte=100&sort=quantity&order=desc&page=0&size=25")
        times.append((time.perf_counter() - started) * 1000)
        assert status == 200, (status, body)
    times.sort()
    p95 = times[int(len(times) * 0.95) - 1]
    check("T5-15 p95 under 500 ms", p95 < 500, f"p95={p95:.0f} ms median={statistics.median(times):.0f} ms")
    status, body = kc.api(keeper, "GET", "/data/equipment/loan?sort=created_at&order=desc&size=200")
    check("T5-15 big page", status == 200 and len(body["data"]["items"]) == 200)

    # T5-17 (API side): a second model is served without a restart.
    status, body = kc.api(admin, "POST", "/models/apply", read("models/notes.yaml"), YAML)
    check("T5-17 second model applied", status == 200, f"{status} {body}")
    kc.ensure_user("editor1", ["EDITOR"])
    editor = kc.token("editor1")
    status, body = kc.api(editor, "GET", "/meta/models")
    names = [m["name"] for m in body["data"]]
    check("T5-17 metadata lists it", names == ["notes"], str(names))
    status, body = kc.api(editor, "POST", "/data/notes/note", {"title": "hello"})
    check("T5-17 data works", status == 200, f"{status} {body}")
    status, body = kc.api(req1, "GET", "/meta/models")
    check("T5-17 other roles do not see it", [m["name"] for m in body["data"]] == ["equipment"])

    failed = [case for case, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed", "FAILED: " + ", ".join(failed) if failed else "")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
