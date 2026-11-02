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
    kc.ensure_user("keeper1", ["KEEPER"])
    up("backend", {"ENGINE_FAULT_AFTER_DECISION": "true"})
    assert rt.wait_api(), "backend did not come back"
    keeper = kc.token("keeper1")
    kc.ensure_user("req1", ["BORROWER"])
    status, body = kc.api(kc.token("req1"), "POST", "/data/equipment/loan", {"device": 1, "reason": "fault", "from_at": "2026-11-12T08:00:00+07:00"})
    loan = body["data"]["id"]
    status, body = kc.api(keeper, "POST", f"/data/equipment/loan/{loan}/decision", {"outcome": "approve"})
    rt.check("T5-11 fault gives a server error", status == 500, str(status))
    code, state = rt.psql(f"select status from m_equipment.loan where id={loan}")
    rt.check("T5-11 record unchanged", state == "submitted", state)
    code, tasks = rt.psql(f"select count(*) from engine.act_ru_task t join engine.act_ru_execution e on e.proc_inst_id_=t.proc_inst_id_ and e.parent_id_ is null where e.business_key_='equipment.loan.{loan}'")
    rt.check("T5-11 process unchanged (task still waiting)", tasks == "1", tasks)
    up("backend", {"ENGINE_FAULT_AFTER_DECISION": "false"})
    assert rt.wait_api()
    keeper = kc.token("keeper1")
    status, body = kc.api(keeper, "POST", f"/data/equipment/loan/{loan}/decision", {"outcome": "reject"})
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
    rt.check("T5-05 earlier steps were kept", tables == "1", tables)
    code, version = rt.psql("select count(*) from engine.model_version where model='recovery'")
    rt.check("T5-05 version not stored", version == "0", version)
    code, steps = rt.psql("select s.kind||':'||s.status from engine.apply_step s join engine.apply_run r on r.id=s.run_id where r.model='recovery' and s.status='failed'")
    rt.check("T5-05 history names the failed step", steps == "ENSURE_ROLE:failed", steps)
    up("keycloak")
    end = time.time() + 180
    while time.time() < end:
        s, _ = kc.http("GET", f"{kc.BASE}/realms/dxos/.well-known/openid-configuration")
        if s == 200:
            break
        time.sleep(3)
    admin = kc.token("admin1")
    changed = content.replace("title: {type: text, required: true}", "title: {type: text, required: true}\n      extra: {type: text}")
    status, body = kc.api(admin, "POST", "/models/apply", changed, YAML)
    rt.check("T5-20 other file refused while an apply is unfinished", status == 409, f"{status}")
    code, version = rt.psql("select count(*) from engine.model_version where model='recovery'")
    rt.check("T5-20 no version stored", version == "0", version)
    status, body = kc.api(admin, "POST", "/models/apply", content, YAML)
    rt.check("T5-05 apply again finishes", status == 200 and body["data"]["status"] == "applied", f"{status}")
    status, body = kc.api(admin, "GET", "/models/recovery/runs")
    statuses = [r["status"] for r in body["data"]]
    rt.check("T5-05 history shows both runs", statuses == ["done", "failed"], str(statuses))
    status, roles = kc.kc("GET", "/roles?search=RECOVERY_ROLE")
    rt.check("T5-05 role created once", len([r for r in roles if r["name"] == "RECOVERY_ROLE"]) == 1)

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
