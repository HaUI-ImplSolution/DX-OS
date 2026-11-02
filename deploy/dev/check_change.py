# SPDX-License-Identifier: Apache-2.0
"""Checks of changing a model that already holds data.

Run after check_case.py on the same stack:
    python3 deploy/dev/check_change.py
"""
import sys

import check_api as rt
import kc

YAML = rt.YAML
BASE = "/data/shop/order"
V1 = """format: 0
name: shop
roles:
  - name: BUYER
  - name: SELLER
entities:
  category:
    fields:
      title: {type: text, required: true}
    access: {BUYER: read, SELLER: all}
  order:
    fields:
      item: {type: text, required: true}
      code: {type: text, unique: true}
      kind: {type: choice, options: [book, pen, bag]}
      memo: {type: text}
      category: {type: reference, to: category}
    access: {BUYER: own, SELLER: all}
    process: {template: approval, approver: SELLER}
"""


def plan(admin, content):
    status, body = kc.api(admin, "POST", "/models/plan", content, YAML)
    assert status == 200, (status, body)
    return body["data"]


def apply(admin, content, base=None, accept=None):
    query = "&".join(f"{k}={v}" for k, v in (("base", base), ("accept", accept)) if v is not None)
    return kc.api(admin, "POST", f"/models/apply?{query}", content, YAML)


def kinds(the_plan):
    return {(c["kind"], c["field"]) for c in the_plan["changes"]}


def main():
    kc.ensure_client()
    admin = kc.token("admin1")
    status, body = apply(admin, V1)
    rt.check("T7-01 first apply goes through the same path", status == 200 and body["data"]["version"] == 1, f"{status} {body}")
    rt.check("T7-01 report role reads the new tables", rt.psql("select count(*) from m_shop.order", user="report_ro", password=kc.ENV["REPORT_DB_PASSWORD"])[0] == 0)
    again = plan(admin, V1)
    rt.check("T7-01 plan of the same file is empty", again["action"] == "unchanged" and again["statements"] == [], str(again))

    kc.ensure_user("buyer1", ["BUYER"])
    kc.ensure_user("seller1", ["SELLER"])
    buyer, seller = kc.token("buyer1"), kc.token("seller1")
    status, cat = kc.api(seller, "POST", "/data/shop/category", {"title": "Sách"})
    ids = []
    for i in range(5):
        status, body = kc.api(buyer, "POST", BASE, {"item": f"item {i}", "code": f"c{i}", "kind": "book", "memo": f"memo {i}", "category": cat["data"]["id"]})
        ids.append(body["data"]["id"])
    rt.check("T7-01 data exists", len(ids) == 5 and status == 200, str(ids))
    status, body = kc.api(seller, "POST", f"{BASE}/{ids[0]}/actions/approve", {})
    rt.check("T7-01 one record decided", status == 200)
    rt.psql("insert into m_shop.order (created_by, item, code) select 'seed', 'bulk ' || g, 'b' || g from generate_series(1, 1000) g")

    # T7-02: a safe change needs no confirmation and no rebuild.
    v2 = V1.replace("      memo: {type: text}\n", "      memo: {type: text}\n      priority: {type: number}\n")
    the_plan = plan(admin, v2)
    rt.check("T7-02 plan level safe", the_plan["level"] == "SAFE" and ("ADD_FIELD", "priority") in kinds(the_plan) and the_plan["fromVersion"] == 1 and the_plan["version"] == 2, str(the_plan))
    rt.check("T7-02 plan shows the statement", any("priority" in s for s in the_plan["statements"]), str(the_plan["statements"]))
    code, cols = rt.psql("select count(*) from information_schema.columns where table_schema='m_shop' and table_name='order' and column_name='priority'")
    rt.check("T7-02 the plan changes nothing", cols == "0", cols)
    status, _ = apply(admin, v2)
    rt.check("T7-02 apply without base is out of date", status == 409, str(status))
    status, _ = apply(admin, v2, base=9)
    rt.check("T7-02 a stale base is refused", status == 409, str(status))
    status, body = apply(admin, v2, base=1)
    rt.check("T7-02 apply", status == 200 and body["data"]["version"] == 2, f"{status} {body}")
    status, body = kc.api(buyer, "PATCH", f"{BASE}/{ids[1]}", {"priority": 3})
    rt.check("T7-02 API serves the new field at once", status == 200 and body["data"]["priority"] == 3, f"{status} {body}")
    status, meta = kc.api(buyer, "GET", "/meta/models")
    fields = [f["name"] for m in meta["data"] if m["name"] == "shop" for e in m["entities"] if e["name"] == "order" for f in e["fields"]]
    rt.check("T7-02 metadata shows it", "priority" in fields, str(fields))

    # T7-03: rename with unique, reference and rows; the preview matches the real apply.
    v3 = v2.replace("      code: {type: text, unique: true}", "      serial: {type: text, unique: true, renamed_from: code}")
    v3 = v3.replace("      memo: {type: text}", "      note: {type: text, renamed_from: memo}")
    v3 = v3.replace("      category: {type: reference, to: category}", "      group_of: {type: reference, to: category, renamed_from: category}")
    the_plan = plan(admin, v3)
    rt.check("T7-03 rename needs a confirmation", the_plan["level"] == "MIGRATION" and {("RENAME_FIELD", "serial"), ("RENAME_FIELD", "note"), ("RENAME_FIELD", "group_of")} <= kinds(the_plan), str(the_plan["changes"]))
    rt.check("T7-03 only renames in the plan", all(s.startswith("RENAME") for s in the_plan["statements"]), str(the_plan["statements"]))
    status, _ = apply(admin, v3, base=2)
    rt.check("T7-03 refused without accept", status == 409, str(status))
    status, body = apply(admin, v3, base=2, accept="MIGRATION")
    rt.check("T7-03 apply", status == 200 and body["data"]["version"] == 3, f"{status} {body}")
    code, rows = rt.psql("select count(*), count(serial), count(note), count(group_of) from m_shop.order where created_by <> 'seed'")
    rt.check("T7-03 values are kept", rows == "5|5|5|5", rows)
    status, body = kc.api(buyer, "GET", f"{BASE}/{ids[1]}")
    rt.check("T7-03 API uses the new names", status == 200 and body["data"]["note"] == "memo 1" and body["data"]["priority"] == 3, str(body))
    status, _ = kc.api(buyer, "POST", BASE, {"item": "x", "serial": "c1"})
    rt.check("T7-03 unique still holds", status == 400, str(status))
    status, _ = kc.api(buyer, "POST", BASE, {"item": "x", "group_of": 99999})
    rt.check("T7-03 foreign key still holds", status == 400, str(status))
    same = plan(admin, v3)
    rt.check("T7-03 same file again is unchanged", same["action"] == "unchanged", str(same))

    # T7-04: rows that break a new rule stop the change and are listed.
    code, _ = rt.psql(f"update m_shop.order set note = null where id in ({ids[2]}, {ids[3]})")
    rt.psql("delete from m_shop.order where created_by = 'seed'")
    v4 = v3.replace("      note: {type: text, renamed_from: memo}", "      note: {type: text, required: true}")
    the_plan = plan(admin, v4)
    violation = the_plan["violations"][0] if the_plan["violations"] else {}
    rt.check("T7-04 violation listed with its rows", the_plan["action"] == "rejected" and violation.get("rows") == 2 and set(violation.get("sample", [])) == {ids[2], ids[3]} and violation.get("field") == "note", str(the_plan))
    status, _ = apply(admin, v4, base=3, accept="LOSS")
    rt.check("T7-04 apply is refused", status == 409, str(status))
    code, nullable = rt.psql("select is_nullable from information_schema.columns where table_schema='m_shop' and table_name='order' and column_name='note'")
    rt.check("T7-04 nothing changed", nullable == "YES", nullable)
    code, leftover = rt.psql("select count(*) from pg_constraint where conrelid='m_shop.order'::regclass and conname like '%not_null%'")
    rt.check("T7-04 no constraint left behind", leftover == "0", leftover)
    v4u = v3.replace("      item: {type: text, required: true}", "      item: {type: text, required: true, unique: true}")
    rt.psql(f"update m_shop.order set item = 'same' where id in ({ids[0]}, {ids[1]})")
    the_plan = plan(admin, v4u)
    rt.check("T7-04 duplicates listed", any(v["rule"] == "UNIQUE" and v["rows"] == 2 for v in the_plan["violations"]), str(the_plan["violations"]))
    v4o = v3.replace("options: [book, pen, bag]", "options: [pen, bag]")
    the_plan = plan(admin, v4o)
    rt.check("T7-04 removed option in use listed", any(v["rule"] == "OPTIONS" and v["rows"] == 5 for v in the_plan["violations"]), str(the_plan["violations"]))
    rt.psql(f"update m_shop.order set item = 'x' || id where id in ({ids[0]}, {ids[1]})")
    v4n = v3.replace("      item: {type: text, required: true}\n", "      item: {type: text, required: true}\n      source: {type: text, required: true}\n")
    the_plan = plan(admin, v4n)
    rt.check("T7-04 new required field without default", any(v["rule"] == "NEW_REQUIRED" for v in the_plan["violations"]), str(the_plan["violations"]))
    v5 = v3.replace("      item: {type: text, required: true}\n", "      item: {type: text, required: true}\n      source: {type: text, required: true, default: web}\n")
    the_plan = plan(admin, v5)
    rt.check("T7-05 required field with default", the_plan["level"] == "MIGRATION" and not the_plan["violations"] and the_plan["action"] == "change", str(the_plan))
    status, body = apply(admin, v5, base=3, accept="MIGRATION")
    rt.check("T7-05 apply", status == 200 and body["data"]["version"] == 4, f"{status} {body}")
    code, filled = rt.psql("select count(*) from m_shop.order where source = 'web'")
    rt.check("T7-05 existing rows filled", filled == "5", filled)
    status, body = kc.api(buyer, "POST", BASE, {"item": "after"})
    rt.check("T7-05 the default fills a new row", status == 200 and body["data"]["source"] == "web", f"{status} {body}")
    extra = body["data"]["id"]
    kc.api(buyer, "DELETE", f"{BASE}/{extra}")

    # T7-06: refused changes.
    v6 = v5.replace("      priority: {type: number}", "      priority: {type: text}")
    the_plan = plan(admin, v6)
    rt.check("T7-06 type change refused", the_plan["action"] == "rejected" and any("priority" in r for r in the_plan["rejections"]), str(the_plan["rejections"]))
    status, _ = apply(admin, v6, base=4, accept="LOSS")
    rt.check("T7-06 and not applied", status == 409, str(status))
    code, kind = rt.psql("select data_type from information_schema.columns where table_schema='m_shop' and table_name='order' and column_name='priority'")
    rt.check("T7-06 column unchanged", kind == "numeric", kind)

    # T7-07: losing data asks for its own confirmation; a forgotten rename is treated as a loss.
    v7 = v5.replace("      note: {type: text, renamed_from: memo}\n", "      remark: {type: text}\n")
    the_plan = plan(admin, v7)
    rt.check("T7-07 forgotten rename is a loss", the_plan["level"] == "LOSS" and ("DROP_FIELD", "note") in kinds(the_plan), str(the_plan["changes"]))
    rt.check("T7-07 the plan asks about it", any("renamed_from: note" in q for q in the_plan["questions"]), str(the_plan["questions"]))
    status, _ = apply(admin, v7, base=4, accept="MIGRATION")
    rt.check("T7-07 refused without accept=LOSS", status == 409, str(status))
    code, kept = rt.psql("select count(note) from m_shop.order")
    rt.check("T7-07 values untouched", kept == "3", kept)
    v8 = V1.split("entities:")[0] + "entities:" + v5.split("entities:")[1].split("  order:")[0]
    v8 += "  order:" + v5.split("  order:")[1].replace("      priority: {type: number}\n", "")
    the_plan = plan(admin, v8)
    rt.check("T7-07 drop field plan", the_plan["level"] == "LOSS" and any("DROP COLUMN" in s for s in the_plan["statements"]), str(the_plan["statements"]))
    status, body = apply(admin, v8, base=4, accept="LOSS")
    rt.check("T7-07 apply with accept=LOSS", status == 200 and body["data"]["version"] == 5, f"{status} {body}")
    code, cols = rt.psql("select count(*) from information_schema.columns where table_schema='m_shop' and table_name='order' and column_name='priority'")
    rt.check("T7-07 column removed", cols == "0", cols)

    # T7-08: open records are reported; a rollback is a new version.
    v9 = v8.replace("process: {template: approval, approver: SELLER}", "process: {template: approval, approver: SELLER, allow_self_decision: true}")
    the_plan = plan(admin, v9)
    rt.check("T7-08 open records reported", the_plan["openRecords"].get("order") == 4, str(the_plan["openRecords"]))
    untouched = v8.replace("  category:\n    fields:\n      title: {type: text, required: true}", "  category:\n    label: Danh mục\n    fields:\n      title: {type: text, required: true}")
    other_plan = plan(admin, untouched)
    rt.check("T7-08 a change elsewhere reports no open records", other_plan["openRecords"] == {}, str(other_plan["openRecords"]))
    status, body = apply(admin, v9, base=5)
    rt.check("T7-08 apply", status == 200 and body["data"]["version"] == 6, f"{status} {body}")
    status, body = kc.api(admin, "POST", "/models/shop/rollback/plan")
    rt.check("T7-09 rollback plan", status == 200 and body["data"]["level"] == "SAFE" and body["data"]["version"] == 7, str(body))
    status, _ = kc.api(admin, "POST", "/models/shop/rollback?base=5")
    rt.check("T7-09 rollback checks the base", status == 409, str(status))
    status, body = kc.api(admin, "POST", "/models/shop/rollback?base=6")
    rt.check("T7-09 rollback is a new version", status == 200 and body["data"]["version"] == 7, f"{status} {body}")
    status, body = kc.api(admin, "POST", "/models/shop/rollback/plan")
    rt.check("T7-09 rollback of the rollback", status == 200 and body["data"]["version"] == 8, str(body))

    # T7-10: rollback of a rename keeps the values.
    rena = v9.replace("      note: {type: text, renamed_from: memo}", "      comment: {type: text, renamed_from: note}")
    status, body = apply(admin, rena, base=7, accept="MIGRATION")
    rt.check("T7-10 rename applied", status == 200, f"{status} {body}")
    status, body = kc.api(admin, "POST", "/models/shop/rollback/plan")
    rt.check("T7-10 rollback plan renames back", status == 200 and any(c["kind"] == "RENAME_FIELD" for c in body["data"]["changes"]) and all(s.startswith("RENAME") for s in body["data"]["statements"]), str(body["data"]))
    status, body = kc.api(admin, "POST", "/models/shop/rollback?base=8&accept=MIGRATION")
    rt.check("T7-10 rollback", status == 200, f"{status} {body}")
    code, kept = rt.psql("select count(note) from m_shop.order")
    rt.check("T7-10 values back under the old name", kept == "3", kept)

    # T7-11: an apply stopped after renaming is finished only by its own file.
    v11 = v8.replace("      item: {type: text, required: true}", "      title: {type: text, required: true, renamed_from: item}")
    code, version = rt.psql("select max(version) from engine.model_version where model='shop'")
    rt.sh(rt.COMPOSE + ["stop", "keycloak"])
    status, body = apply(admin, v11, base=int(version), accept="LOSS")
    rt.check("T7-11 apply stops while Keycloak is down", status >= 500, f"{status} {body}")
    code, cols = rt.psql("select count(*) from information_schema.columns where table_schema='m_shop' and table_name='order' and column_name='title'")
    rt.check("T7-11 the rename already ran", cols == "1", cols)
    status, body = apply(admin, v8, base=int(version), accept="LOSS")
    rt.check("T7-11 another file is refused", status == 409, f"{status} {body}")
    rt.sh(rt.COMPOSE + ["up", "-d", "keycloak"])
    import time
    end = time.time() + 180
    while time.time() < end:
        s, _ = kc.http("GET", f"{kc.BASE}/realms/dxos/.well-known/openid-configuration")
        if s == 200:
            break
        time.sleep(3)
    admin = kc.token("admin1")
    status, body = apply(admin, v11, base=int(version), accept="LOSS")
    rt.check("T7-11 its own file finishes it", status == 200 and body["data"]["version"] == int(version) + 1, f"{status} {body}")
    status, body = kc.api(buyer if False else kc.token("buyer1"), "GET", f"{BASE}/{ids[1]}")
    rt.check("T7-11 data is reachable", status == 200 and body["data"]["title"] == "x" + str(ids[1]), str(body))

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
