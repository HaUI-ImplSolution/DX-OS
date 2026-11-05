# SPDX-License-Identifier: Apache-2.0
"""Recovery checks: a decision that fails after the process step, and an apply interrupted by Keycloak.

Run after check_api.py on the same stack:
    python3 deploy/dev/check_recovery.py
"""
import os
import subprocess
import sys
import threading
import time

import kc
import check_api as rt

YAML = rt.YAML
TEMPORARY_MODEL = """format: 0
name: recovery
roles:
  - name: RECOVERY_ROLE
entities:
  item:
    fields:
      title: {type: text, required: true}
    access: {RECOVERY_ROLE: all}
"""


def up(service, extra_env=None):
    env = dict(os.environ, **(extra_env or {}))
    return subprocess.run(rt.COMPOSE + ["up", "-d", service], capture_output=True, text=True,
                          cwd=f"{rt.REPO}/deploy", env=env)


def main():
    # T5-11
    kc.ensure_user("keeper1", ["equipment/KEEPER"])
    up("backend", {"ENGINE_FAULT_AFTER_DECISION": "true"})
    assert rt.wait_api(), "backend did not come back"
    keeper = kc.token("keeper1")
    kc.ensure_user("req1", ["equipment/BORROWER"])
    status, body = kc.api(kc.token("req1"), "POST", "/data/equipment/loan", {"device": 1, "reason": "fault", "from_at": "2026-11-12T08:00:00+07:00"})
    loan = body["data"]["id"]
    status, body = kc.api(keeper, "POST", f"/data/equipment/loan/{loan}/actions/approve", {})
    rt.check("T5-11 fault gives a server error", status == 500, str(status))
    code, state = rt.psql(f"select status from m_equipment.loan where id={loan}")
    rt.check("T5-11 record unchanged", state == "submitted", state)
    code, tasks = rt.psql(f"select count(*) from engine.act_ru_task t join engine.act_ru_execution e on e.proc_inst_id_=t.proc_inst_id_ and e.parent_id_ is null where e.business_key_='equipment.loan.{loan}'")
    rt.check("T5-11 process unchanged (task still waiting)", tasks == "1", tasks)
    up("backend", {"ENGINE_FAULT_AFTER_DECISION": "false"})
    assert rt.wait_api()
    keeper = kc.token("keeper1")
    status, body = kc.api(keeper, "POST", f"/data/equipment/loan/{loan}/actions/reject", {"note": "Không đủ điều kiện"})
    rt.check("T5-11 decision works again", status == 200 and body["data"]["status"] == "rejected", f"{status}")
    code, tasks = rt.psql(f"select count(*) from engine.act_ru_task t join engine.act_ru_execution e on e.proc_inst_id_=t.proc_inst_id_ and e.parent_id_ is null where e.business_key_='equipment.loan.{loan}'")
    rt.check("T5-11 no task left for the record", tasks == "0", tasks)

    # T5-05
    admin = kc.token("admin1")
    content = TEMPORARY_MODEL
    rt.sh(rt.COMPOSE + ["stop", "keycloak"])
    status, body = kc.api(admin, "POST", "/models/apply", content, YAML)
    rt.check("T5-05 apply fails while Keycloak is down", status >= 500, str(status))
    code, tables = rt.psql("select count(*) from information_schema.tables where table_schema='m_recovery'")
    code, runs = rt.psql("select count(*) from engine.apply_run where model='recovery'")
    rt.check("T5-05 it fails before the first step", tables == "0" and runs == "0", f"{tables} {runs}")
    up("keycloak")
    end = time.time() + 180
    while time.time() < end:
        s, _ = kc.http("GET", f"{kc.BASE}/realms/dxos/.well-known/openid-configuration")
        if s == 200:
            break
        time.sleep(3)
    up("backend", {"ENGINE_FAULT_APPLY_STEP": "ENSURE_CLIENT"})
    assert rt.wait_api(), "backend did not come back"
    admin = kc.token("admin1")
    status, body = kc.api(admin, "POST", "/models/apply", content, YAML)
    rt.check("T5-05 apply fails at a step", status >= 500, str(status))
    code, tables = rt.psql("select count(*) from information_schema.tables where table_schema='m_recovery'")
    rt.check("T5-05 earlier steps were kept", tables == "1", tables)
    code, version = rt.psql("select count(*) from engine.model_version where model='recovery'")
    rt.check("T5-05 version not stored", version == "0", version)
    code, steps = rt.psql("select s.kind||':'||s.status from engine.apply_step s join engine.apply_run r on r.id=s.run_id where r.model='recovery' and s.status='failed'")
    rt.check("T5-05 history names the failed step", steps == "ENSURE_CLIENT:failed", steps)
    up("backend", {"ENGINE_FAULT_APPLY_STEP": ""})
    assert rt.wait_api(), "backend did not come back"
    admin = kc.token("admin1")
    changed = content.replace("title: {type: text, required: true}", "title: {type: text, required: true}\n      extra: {type: text}")
    status, body = kc.api(admin, "POST", "/models/apply", changed, YAML)
    rt.check("T5-20 another file finishes it, as nothing was renamed", status == 200 and body["data"]["status"] == "applied", f"{status} {body}")
    code, column = rt.psql("select count(*) from information_schema.columns where table_schema='m_recovery' and table_name='item' and column_name='extra'")
    rt.check("T5-20 the tables match that file", column == "1", column)
    status, body = kc.api(admin, "POST", "/models/apply", content, YAML)
    rt.check("T5-05 the first file is now a change of the running version", status == 409, f"{status}")
    status, body = kc.api(admin, "POST", "/models/apply?base=1&accept=LOSS", content, YAML)
    rt.check("T5-05 apply again finishes", status == 200 and body["data"]["status"] == "applied", f"{status} {body}")
    status, body = kc.api(admin, "GET", "/models/recovery/runs")
    statuses = [r["status"] for r in body["data"]]
    rt.check("T5-05 history shows the runs", statuses == ["done", "done", "failed"], str(statuses))
    rt.check("T5-05 role created once", kc.client_role_names("recovery").count("RECOVERY_ROLE") == 1)

    # T5-21
    race = TEMPORARY_MODEL.replace("name: recovery", "name: race").replace("RECOVERY_ROLE", "RACE_ROLE")
    outcomes = []
    threads = [threading.Thread(target=lambda: outcomes.append(kc.api(admin, "POST", "/models/apply", race, YAML))) for _ in range(4)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    results = [(s, b["data"]["status"] if s == 200 else None) for s, b in outcomes]
    applied = [r for r in results if r[1] == "applied"]
    allowed = all(r[1] in ("applied", "unchanged") or r[0] == 409 for r in results)
    rt.check("T5-21 parallel applies run once", len(applied) == 1 and allowed, str(results))
    code, runs = rt.psql("select count(*) from engine.apply_run where model='race'")
    rt.check("T5-21 one run recorded", runs == "1", runs)

    failed = [c for c, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
