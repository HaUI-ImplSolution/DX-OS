# SPDX-License-Identifier: Apache-2.0
"""Checks of the guards around a model in use: who owns a role, the roles of the platform, removing
a role, dropping an entity that has files or running work, and an apply that stopped halfway.

Run after check_change.py on the same stack:
    python3 e2e/api/check_guards.py
"""
import os
import sys
import threading
import time

import check_api as rt
import kc

YAML = rt.YAML

OWNER = """format: 0
name: ga
roles:
  - name: GA_OWNER
  - name: GA_OTHER
entities:
  thing:
    fields:
      title: {type: text, required: true}
    access: {GA_OWNER: all, GA_OTHER: all}
"""

DROP = """format: 0
name: gdrop
roles:
  - name: GD_USER
  - name: GD_STAFF
    staff: true
entities:
  req:
    fields:
      title: {type: text, required: true}
      evidence: {type: file}
    access: {GD_USER: own, GD_STAFF: all}
    process: {family: approval, handler: GD_STAFF}
  order_item:
    fields:
      title: {type: text, required: true}
    access: {GD_USER: own, GD_STAFF: all}
    process: {family: approval, handler: GD_STAFF}
  orderxitem:
    fields:
      title: {type: text, required: true}
    access: {GD_USER: own, GD_STAFF: all}
    process: {family: approval, handler: GD_STAFF}
  plain:
    fields:
      title: {type: text, required: true}
      evidence: {type: file}
    access: {GD_USER: all, GD_STAFF: all}
"""

STOP = """format: 0
name: gstop
roles:
  - name: GS_ROLE
entities:
  item:
    fields:
      title: {type: text, required: true}
    access: {GS_ROLE: all}
"""

ABAN = """format: 0
name: gaban
roles:
  - name: GB_ROLE
entities:
  item:
    fields:
      title: {type: text, required: true}
    access: {GB_ROLE: all}
"""

FIRST = """format: 0
name: {name}
roles:
  - name: {role}
entities:
  item:
    fields:
      title: {{type: text, required: true}}
    access: {{{role}: all}}
"""

G = "/data/gdrop"


def plan(admin, content):
    status, body = kc.api(admin, "POST", "/models/plan", content, YAML)
    assert status == 200, (status, body)
    return body["data"]


def apply(admin, content, base=None, accept=None):
    query = "&".join(f"{k}={v}" for k, v in (("base", base), ("accept", accept)) if v is not None)
    return kc.api(admin, "POST", f"/models/apply?{query}", content, YAML)


def without(content, *names):
    head, tail = content.split("entities:\n")
    blocks = []
    for line in tail.splitlines(keepends=True):
        if line.startswith("  ") and not line.startswith("   "):
            blocks.append([line])
        else:
            blocks[-1].append(line)
    kept = ["".join(block) for block in blocks if block[0].strip().rstrip(":") not in names]
    return head + "entities:\n" + "".join(kept)


def upload(token, base, record, name, data):
    boundary = "dxosguards"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
            f"Content-Type: application/pdf\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
    return kc.api(token, "POST", f"{base}/{record}/files?field=evidence", body,
                  f"multipart/form-data; boundary={boundary}")


def with_backend(env):
    rt.sh(rt.COMPOSE + ["up", "-d", "backend"], env=dict(os.environ, **env))
    assert rt.wait_api(), "backend did not come back"


def wait_keycloak(seconds=180):
    end = time.time() + seconds
    while time.time() < end:
        status, _ = kc.http("GET", f"{kc.BASE}/realms/dxos/.well-known/openid-configuration")
        if status == 200:
            return True
        time.sleep(3)
    return False


def roles_and_holder(admin):
    # T12-01 to T12-04: the roles of a model are held in the client of that model.
    status, body = apply(admin, OWNER)
    rt.check("T12-01 first model applies", status == 200 and body["data"]["version"] == 1, f"{status} {body}")
    rt.check("T12-01 its roles live in its own Keycloak client",
             sorted(kc.client_role_names("ga")) == ["GA_OTHER", "GA_OWNER"], str(kc.client_role_names("ga")))
    kc.ensure_user("ga_holder", ["ga/GA_OWNER"])
    holder = kc.token("ga_holder")

    taker = OWNER.replace("name: ga\n", "name: gb\n")
    status, body = apply(admin, taker)
    rt.check("T12-02 a second model may use the same role names", status == 200, f"{status} {body}")
    status, made = kc.api(holder, "POST", "/data/ga/thing", {"title": "mine"})
    rt.check("T12-02 the holder works in the model whose client holds the role", status == 200, f"{status} {made}")
    status, _ = kc.api(holder, "POST", "/data/gb/thing", {"title": "not mine"})
    rt.check("T12-02 and has no access to the other model with the same role name", status == 403, str(status))
    status, body = kc.api(holder, "GET", "/meta/models")
    rt.check("T12-02 nor is the other model offered to them", status == 200 and [m["name"] for m in body["data"]] == ["ga"], str(body)[:200])

    reserved = OWNER.replace("name: ga\n", "name: gadmin\n").replace("GA_OWNER", "ADMIN")
    status, body = kc.api(admin, "POST", "/models/plan", reserved, YAML)
    rt.check("T12-03 the role of the platform is reserved, with its line",
             status == 400 and any("ADMIN" in p and "reserved" in p and p.startswith("line ") for p in body["data"]),
             f"{status} {body.get('data')}")
    status, body = apply(admin, reserved)
    rt.check("T12-03 and the apply refuses it too", status == 400, str(status))

    race = OWNER.replace("GA_OWNER", "GA_RACE").replace("GA_OTHER", "GA_RACE2")
    outcomes = []
    threads = [threading.Thread(target=lambda n=n: outcomes.append(
        apply(admin, race.replace("name: ga\n", f"name: {n}\n")))) for n in ("gc", "gd")]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    results = sorted(status for status, _ in outcomes)
    rt.check("T12-04 two models with the same role names applied at once both go through", results == [200, 200], str(results))


def drops(admin):
    # T12-05 to T12-11: dropping an entity.
    status, body = apply(admin, DROP)
    rt.check("T12-05 model with files and processes applies", status == 200, f"{status} {body}")
    kc.ensure_user("gd_u", ["gdrop/GD_USER"])
    user = kc.token("gd_u")

    def make(path, title):
        status, made = kc.api(user, "POST", path, {"title": title})
        assert status == 200, (status, made)
        return made["data"]["id"]

    request = make(f"{G}/req", "r1")
    waiting = make(f"{G}/orderxitem", "oi")
    plain = make(f"{G}/plain", "p1")
    rt.check("T12-05 files are attached", upload(user, f"{G}/req", request, "a.pdf", b"%PDF-1.4 a")[0] == 200
             and upload(user, f"{G}/plain", plain, "p.pdf", b"%PDF-1.4 p")[0] == 200)

    the_plan = plan(admin, without(DROP, "req"))
    rt.check("T12-06 an entity with a running process is not dropped",
             the_plan["action"] == "rejected" and any("Cannot drop req" in r and "1 process instances" in r for r in the_plan["rejections"]),
             str(the_plan["rejections"]))
    status, body = apply(admin, without(DROP, "req"), base=1, accept="LOSS")
    code, tables = rt.psql("select count(*) from information_schema.tables where table_schema='m_gdrop' and table_name='req'")
    rt.check("T12-06 and the apply keeps its table", status == 409 and tables == "1", f"{status} {tables}")

    the_plan = plan(admin, without(DROP, "order_item"))
    rt.check("T12-07 a running process on a similar name does not block it",
             the_plan["action"] == "change" and the_plan["level"] == "LOSS" and not the_plan["rejections"], str(the_plan["rejections"]))

    status, _ = kc.api(user, "POST", f"{G}/req/{request}/actions/withdraw", {})
    rt.check("T12-08 the owner withdraws the record", status == 200, str(status))
    the_plan = plan(admin, without(DROP, "req"))
    drop = [c for c in the_plan["changes"] if c["kind"] == "DROP_ENTITY"]
    rt.check("T12-08 then the entity can be dropped, and the plan says the files go too",
             the_plan["action"] == "change" and drop and "attached files" in drop[0]["detail"], str(the_plan["changes"]))
    status, body = apply(admin, without(DROP, "req"), base=1)
    rt.check("T12-08 and the loss must be accepted", status == 409, f"{status}")
    status, body = apply(admin, without(DROP, "req"), base=1, accept="LOSS")
    rt.check("T12-08 apply", status == 200 and any(s["kind"] == "DROP_FILES" for s in body["data"]["steps"]), f"{status} {body}")
    code, rows = rt.psql("select count(*) from engine.attachment where model='gdrop' and entity='req'")
    code, tables = rt.psql("select count(*) from information_schema.tables where table_schema='m_gdrop' and table_name='req'")
    rt.check("T12-09 its files and its table are gone", rows == "0" and tables == "0", f"{rows} {tables}")
    code, others = rt.psql("select count(*) from engine.attachment where model='gdrop' and entity='plain'")
    rt.check("T12-09 the files of other entities stay", others == "1", others)

    status, body = apply(admin, without(DROP, "req", "order_item", "plain"), base=2, accept="LOSS")
    rt.check("T12-10 more entities go in one apply", status == 200, f"{status} {body}")
    code, rows = rt.psql("select count(*) from engine.attachment where model='gdrop'")
    code, running = rt.psql(f"select count(*) from engine.act_ru_execution where business_key_='gdrop.orderxitem.{waiting}'")
    status, record = kc.api(user, "GET", f"{G}/orderxitem/{waiting}")
    rt.check("T12-10 the work still running is untouched",
             rows == "0" and int(running) >= 1 and status == 200 and record["data"]["status"] == "submitted", f"{rows} {running} {status}")
    logs = rt.sh(rt.COMPOSE + ["logs", "backend", "--no-log-prefix", "--since", "10m"])
    rt.check("T12-11 no object was left in the store", "Could not delete object" not in logs.stdout + logs.stderr)


def stopped_apply(admin):
    # T12-12 to T12-22: an apply that stops halfway holds its model, also across a restart, and is
    # finished or given up from what the run kept.
    status, body = apply(admin, STOP)
    rt.check("T12-12 model applies", status == 200, f"{status} {body}")
    status, body = apply(admin, ABAN)
    rt.check("T12-12 second model applies", status == 200, f"{status} {body}")
    kc.ensure_user("gs_u", ["gstop/GS_ROLE"])
    kc.ensure_user("gb_u", ["gaban/GB_ROLE"])
    kc.ensure_user("gd_u", ["gdrop/GD_USER"])
    worker, keeper, other = kc.token("gs_u"), kc.token("gb_u"), kc.token("gd_u")
    status, made = kc.api(worker, "POST", "/data/gstop/item", {"title": "first"})
    rt.check("T12-12 data works", status == 200, str(status))
    item = made["data"]["id"]
    status, made = kc.api(keeper, "POST", "/data/gaban/item", {"title": "kept"})
    kept = made["data"]["id"]
    changed = STOP.replace("title: {type: text, required: true}", "title: {type: text, required: true}\n      extra: {type: text}")
    moved = ABAN.replace("title: {type: text, required: true}",
                         "name: {type: text, required: true, renamed_from: title}\n      extra: {type: text}")

    first_one = FIRST.format(name="gfirst", role="GF_ONE")
    first_two = FIRST.format(name="gfirst2", role="GF_TWO")
    with_backend({"ENGINE_FAULT_APPLY_STEP": "ENSURE_CLIENT"})
    admin = kc.token("admin1")
    status_one, _ = apply(admin, first_one)
    status_two, _ = apply(admin, first_two)
    rt.check("T12-13 two models whose first apply stops", status_one >= 500 and status_two >= 500, f"{status_one} {status_two}")
    with_backend({"ENGINE_FAULT_APPLY_STEP": ""})
    admin, worker, keeper, other = kc.token("admin1"), kc.token("gs_u"), kc.token("gb_u"), kc.token("gd_u")
    # A new backend fetches the signing keys of Keycloak on its first call, before Keycloak stops.
    kc.api(admin, "GET", "/models")

    rt.sh(rt.COMPOSE + ["stop", "keycloak"])
    status, body = apply(admin, changed, base=1)
    rt.check("T12-13 apply stops while Keycloak is down", status >= 500, f"{status} {body}")
    status, body = apply(admin, moved, base=1, accept="MIGRATION")
    rt.check("T12-13 the second one stops after renaming", status >= 500, f"{status} {body}")
    status, body = kc.api(worker, "GET", f"/data/gstop/item/{item}")
    rt.check("T12-13 the model is held while it is unfinished", status == 409 and "reconciliation" in str(body), f"{status} {body}")
    status, body = kc.api(worker, "GET", "/meta/models")
    offered = [m for m in body["data"] if m["name"] == "gstop"] if status == 200 else []
    rt.check("T12-14 it offers the people nothing to use", status == 200 and not [m for m in offered if m["entities"]], str(body)[:200])
    rt.check("T12-14 it is listed as held so the inbox can explain it", len(offered) == 1 and offered[0].get("held") is True, str(offered)[:200])
    status, body = kc.api(admin, "GET", "/models")
    held = {m["model"]: m["held"] for m in body["data"]} if status == 200 else {}
    rt.check("T12-14 the administrator sees which models are held",
             held.get("gstop") is True and held.get("gaban") is True and held.get("ga") is False, str(held))
    status, body = kc.api(other, "GET", f"{G}/orderxitem/{item}")
    rt.check("T12-15 other models go on", status in (200, 404), str(status))

    rt.sh(rt.COMPOSE + ["up", "-d", "keycloak"])
    rt.check("T12-16 Keycloak is back", wait_keycloak())
    rt.sh(rt.COMPOSE + ["restart", "backend"])
    rt.check("T12-16 backend is back", rt.wait_api())
    admin, worker, keeper = kc.token("admin1"), kc.token("gs_u"), kc.token("gb_u")
    status, body = kc.api(worker, "GET", f"/data/gstop/item/{item}")
    rt.check("T12-16 the model is still held after a restart", status == 409, f"{status} {body}")
    status, body = kc.api(admin, "GET", "/models")
    rt.check("T12-16 and the list still says so", status == 200 and {m["model"]: m["held"] for m in body["data"]}.get("gstop") is True, str(status))

    status, body = kc.api(admin, "POST", "/models/ga/resume")
    rt.check("T12-17 a model with nothing stopped has nothing to finish", status == 409, f"{status} {body}")
    status, body = kc.api(admin, "POST", "/models/gstop/resume")
    rt.check("T12-18 resume finishes it without the file", status == 200 and body["data"]["status"] == "applied" and body["data"]["version"] == 2, f"{status} {body}")
    status, body = kc.api(worker, "GET", f"/data/gstop/item/{item}")
    rt.check("T12-18 and the data is back", status == 200 and body["data"]["title"] == "first", f"{status} {body}")
    code, column = rt.psql("select count(*) from information_schema.columns where table_schema='m_gstop' and table_name='item' and column_name='extra'")
    rt.check("T12-18 with the change in the tables", column == "1", column)
    status, body = kc.api(admin, "GET", "/models/gstop/runs")
    rt.check("T12-18 history shows the stop", [r["status"] for r in body["data"]] == ["done", "failed", "done"], str(body["data"])[:200])
    status, body = kc.api(admin, "POST", "/models/gstop/resume")
    rt.check("T12-18 and it cannot be finished twice", status == 409, f"{status}")

    code, columns = rt.psql("select string_agg(column_name, ',' order by column_name) from information_schema.columns where table_schema='m_gaban' and table_name='item' and column_name in ('title','name','extra')")
    rt.check("T12-19 the stopped rename ran", columns == "extra,name", columns)
    status, body = kc.api(admin, "POST", "/models/gaban/abandon")
    rt.check("T12-20 abandon puts the tables back", status == 200 and body["data"]["version"] == 1, f"{status} {body}")
    code, columns = rt.psql("select string_agg(column_name, ',' order by column_name) from information_schema.columns where table_schema='m_gaban' and table_name='item' and column_name in ('title','name','extra')")
    rt.check("T12-20 the old column is back and the new ones are gone", columns == "title", columns)
    status, body = kc.api(keeper, "GET", f"/data/gaban/item/{kept}")
    rt.check("T12-20 the rows are kept and the model is open again", status == 200 and body["data"]["title"] == "kept", f"{status} {body}")
    status, body = kc.api(admin, "GET", "/models/gaban/versions")
    rt.check("T12-20 no version was added", status == 200 and [v["version"] for v in body["data"]] == [1], str(body)[:200])
    status, body = kc.api(admin, "POST", "/models/gaban/abandon")
    rt.check("T12-20 and it cannot be given up twice", status == 409, f"{status}")

    status, body = kc.api(admin, "GET", "/models")
    listed = {m["model"]: m for m in body["data"]} if status == 200 else {}
    rt.check("T12-21 a model without a version is listed as held",
             all(listed.get(n, {}).get("version") == 0 and listed[n]["held"] is True for n in ("gfirst", "gfirst2")), str(listed.get("gfirst")))
    status, body = kc.api(admin, "POST", "/models/gfirst/resume")
    rt.check("T12-21 resume finishes a first apply", status == 200 and body["data"]["version"] == 1, f"{status} {body}")
    # The fault stops the apply before its client is made, so one is made here as if a later step had stopped it.
    kc.kc("POST", "/clients", {"clientId": "dxos-m-gfirst2", "enabled": True})
    kept_client = kc.client_uuid("gfirst2")
    status, body = kc.api(admin, "POST", "/models/gfirst2/abandon")
    rt.check("T12-21 abandon removes what a first apply created", status == 200, f"{status} {body}")
    rt.check("T12-21 and the Keycloak client of that first apply", kept_client is not None and kc.client_uuid("gfirst2") is None, str(kept_client))
    code, schemas = rt.psql("select string_agg(schema_name, ',' order by schema_name) from information_schema.schemata where schema_name in ('m_gfirst', 'm_gfirst2')")
    status, body = kc.api(admin, "GET", "/models")
    listed = {m["model"]: m for m in body["data"]} if status == 200 else {}
    rt.check("T12-21 the first schema stays, the second is gone and no longer listed",
             schemas == "m_gfirst" and listed.get("gfirst", {}).get("version") == 1 and "gfirst2" not in listed, f"{schemas} {sorted(listed)}")
    status, body = apply(admin, first_two)
    rt.check("T12-21 the abandoned file applies afresh", status == 200 and body["data"]["version"] == 1, f"{status} {body}")
    status, body = kc.api(admin, "POST", "/models/gfirst2/abandon")
    rt.check("T12-21 and a finished model has nothing to give up", status == 409, f"{status}")

    # T12-22: a model is held only by a run that stopped; the rows say which.
    code, _ = rt.psql("insert into engine.apply_run (model, hash, status, actor, content, renames, accept) "
                      "values ('gaban', 'x', 'failed', 'test', 'format: 0', '{}', 'SAFE')")
    rt.sh(rt.COMPOSE + ["restart", "backend"])
    rt.check("T12-22 backend is back again", rt.wait_api())
    keeper = kc.token("gb_u")
    status, body = kc.api(keeper, "GET", f"/data/gaban/item/{kept}")
    rt.check("T12-22 a failed run held it", status == 409, f"{status}")
    rt.psql("update engine.apply_run set status = 'done' where id = (select max(id) from engine.apply_run where model = 'gaban')")
    rt.sh(rt.COMPOSE + ["restart", "backend"])
    rt.check("T12-22 backend is back once more", rt.wait_api())
    keeper = kc.token("gb_u")
    status, body = kc.api(keeper, "GET", f"/data/gaban/item/{kept}")
    rt.check("T12-22 and a finished run lets it go", status == 200, f"{status}")


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")
    roles_and_holder(admin)
    drops(admin)
    stopped_apply(admin)
    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
