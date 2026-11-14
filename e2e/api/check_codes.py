# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Checks of the readable record codes: how they are given, kept apart, and found again.

They apply models of their own, so they need nothing from the scripts before them:
    python3 e2e/api/check_codes.py
"""
import re
import sys
import threading
import time
from datetime import datetime, timedelta, timezone

import check_api as rt
import kc

YAML = rt.YAML
YEAR = datetime.now(timezone(timedelta(hours=7))).year

MODEL = """format: 1
name: {name}
roles:
  - {{name: STUDENT}}
  - {{name: OFFICER, staff: true}}
entities:
  request:
    fields:
      title: {{type: text, required: true}}
{code}    access: {{STUDENT: own, OFFICER: all}}
    process: {{family: approval, handler: OFFICER}}
  note:
    fields:
      body: {{type: text, required: true}}
      no: {{type: code, format: "GC-{{seq:3}}"}}
    access: {{STUDENT: own, OFFICER: all}}
"""


def code_line(fmt):
    return f'      no: {{type: code, format: "{fmt}"}}\n'


def model(name, fmt="HS-{year}-{seq:5}"):
    return MODEL.format(name=name, code=code_line(fmt) if fmt else "")


def create(token, name, entity, **fields):
    return kc.api(token, "POST", f"/data/{name}/{entity}", fields)


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")
    stamp = int(time.time())
    name, other, live, tiny = (f"{prefix}{stamp}" for prefix in ("codes", "codesb", "codesc", "codesd"))

    status, body = kc.api(admin, "POST", "/models/apply", model(name), YAML)
    rt.check("CD-01 a model with code fields applies", status == 200 and body["data"]["status"] == "applied", f"{status} {str(body)[:300]}")
    kc.ensure_user("cd_stu", [f"{name}/STUDENT"])
    stu = kc.token("cd_stu")

    status, first = create(stu, name, "request", title="Một")
    status2, second = create(stu, name, "request", title="Hai")
    rt.check("CD-02 the first record gets the first code of the year", status == 200 and first["data"]["no"] == f"HS-{YEAR}-00001", f"{status} {first}")
    rt.check("CD-03 and the next one the next number", status2 == 200 and second["data"]["no"] == f"HS-{YEAR}-00002", f"{status2} {second}")
    status, body = create(stu, name, "note", body="Ghi chú")
    rt.check("CD-04 another entity counts on its own, without the year", status == 200 and body["data"]["no"] == "GC-001", f"{status} {body}")

    status, _ = kc.api(admin, "POST", "/models/apply", model(other), YAML)
    kc.ensure_user("cd_stu2", [f"{other}/STUDENT"])
    status, body = create(kc.token("cd_stu2"), other, "request", title="Khác")
    rt.check("CD-05 another model counts on its own", status == 200 and body["data"]["no"] == f"HS-{YEAR}-00001", f"{status} {body}")

    results = []

    def run(index):
        results.append(create(stu, name, "request", title=f"Song song {index}"))

    threads = [threading.Thread(target=run, args=(i,)) for i in range(20)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    codes = sorted(body["data"]["no"] for status, body in results if status == 200)
    expected = [f"HS-{YEAR}-{n:05d}" for n in range(3, 23)]
    rt.check("CD-06 twenty records created at once get twenty different consecutive codes", codes == expected, f"{len(codes)} {codes[:3]}")

    record = first["data"]["id"]
    status, body = kc.api(stu, "PATCH", f"/data/{name}/request/{record}", {"title": "Một, đã sửa"})
    rt.check("CD-07 changing a record leaves its code", status == 200 and body["data"]["no"] == f"HS-{YEAR}-00001", f"{status} {body}")
    status, body = create(stu, name, "request", title="Tự đặt", no="HS-2026-99999")
    rt.check("CD-08 a code cannot be given on create", status == 400 and "no" in (body.get("data") or {}), f"{status} {body}")
    status, body = kc.api(stu, "PATCH", f"/data/{name}/request/{record}", {"no": "HS-2026-99999"})
    rt.check("CD-09 nor changed", status == 400 and "no" in (body.get("data") or {}), f"{status} {body}")

    status, body = kc.api(stu, "GET", f"/data/{name}/request?filter.no=HS-{YEAR}-00002")
    rt.check("CD-10 a record is found by its exact code", status == 200 and [r["id"] for r in body["data"]["items"]] == [second["data"]["id"]], f"{status} {str(body)[:200]}")
    status, body = kc.api(stu, "GET", f"/data/{name}/request?filter.no.like=HS-{YEAR}&size=100")
    rt.check("CD-11 and by part of it", status == 200 and body["data"]["totalElements"] == 22, f"{status} {str(body)[:200]}")

    status, body = kc.api(stu, "GET", "/meta/models")
    entity = next((e for m in body["data"] if m["name"] == name for e in m["entities"] if e["name"] == "request"), {})
    field = next((f for f in entity.get("fields", []) if f["name"] == "no"), {})
    rt.check("CD-12 the metadata says the field is a code and its format", field.get("type") == "code" and field.get("format") == "HS-{year}-{seq:5}", str(field))
    status, body = kc.api(admin, "GET", f"/meta/models/{name}/openapi")
    text = str(body)
    rt.check("CD-13 the OpenAPI document of the model marks it read only", status == 200 and "x-dxos-code-format" in text and "'readOnly': True" in text, str(status))

    # A model that already has records: the code field added later gives each of them a code.
    status, _ = kc.api(admin, "POST", "/models/apply", model(live, None), YAML)
    kc.ensure_user("cd_stu3", [f"{live}/STUDENT"])
    stu3 = kc.token("cd_stu3")
    ids = [create(stu3, live, "request", title=f"Cũ {i}")[1]["data"]["id"] for i in range(4)]
    code, _ = rt.psql(f"update m_{live}.request set created_at = '2025-06-01 10:00:00+07' where id in ({ids[0]}, {ids[1]})")
    status, plan = kc.api(admin, "POST", "/models/plan", model(live), YAML)
    kinds = [(c["kind"], c["level"]) for c in plan["data"]["changes"]] if status == 200 else []
    rt.check("CD-14 adding a code field to records is a migration in the plan", ("ADD_FIELD", "MIGRATION") in kinds and plan["data"]["level"] == "MIGRATION", f"{status} {kinds}")
    status, body = kc.api(admin, "POST", f"/models/apply?base={plan['data']['fromVersion']}&accept=MIGRATION", model(live), YAML)
    rt.check("CD-15 the model applies", status == 200 and body["data"]["status"] == "applied", f"{status} {str(body)[:300]}")
    status, body = kc.api(stu3, "GET", f"/data/{live}/request?sort=id&size=10")
    found = [r["no"] for r in body["data"]["items"]] if status == 200 else []
    rt.check("CD-16 the old records got codes by the year they were made in, oldest first",
             found == ["HS-2025-00001", "HS-2025-00002", f"HS-{YEAR}-00001", f"HS-{YEAR}-00002"], str(found))
    status, body = create(stu3, live, "request", title="Mới")
    rt.check("CD-17 and the next record carries on from them", status == 200 and body["data"]["no"] == f"HS-{YEAR}-00003", f"{status} {body}")

    # The format changes for new records only.
    wider = model(live, "HS-{year}-{seq:6}")
    status, plan = kc.api(admin, "POST", "/models/plan", wider, YAML)
    kinds = [(c["kind"], c["level"]) for c in plan["data"]["changes"]] if status == 200 else []
    rt.check("CD-18 a new format is a safe change in the plan", kinds == [("CHANGE_FORMAT", "SAFE")], f"{status} {kinds}")
    status, _ = kc.api(admin, "POST", f"/models/apply?base={plan['data']['fromVersion']}", wider, YAML)
    status, body = create(stu3, live, "request", title="Rộng hơn")
    rt.check("CD-19 the next code is wider and carries on counting; older codes stay", status == 200 and body["data"]["no"] == f"HS-{YEAR}-000004", f"{status} {body}")
    status, body = kc.api(stu3, "GET", f"/data/{live}/request?sort=id&size=10")
    rt.check("CD-20 the codes of older records did not change", [r["no"] for r in body["data"]["items"]][:2] == ["HS-2025-00001", "HS-2025-00002"], str(body)[:200])

    # The numbers run out: the record is refused and the number is not used up.
    status, _ = kc.api(admin, "POST", "/models/apply", model(tiny, "T-{seq:1}"), YAML)
    kc.ensure_user("cd_stu4", [f"{tiny}/STUDENT"])
    stu4 = kc.token("cd_stu4")
    made = [create(stu4, tiny, "request", title=f"Số {i}")[0] for i in range(9)]
    status, body = create(stu4, tiny, "request", title="Hết số")
    status_again, _ = create(stu4, tiny, "request", title="Vẫn hết")
    rt.check("CD-21 a full counter refuses the record with an error code that says what to do",
             made == [200] * 9 and status == 422 and (body.get("data") or {}).get("errorCode") == "ERR_CODE_EXHAUSTED" and status_again == 422, f"{made} {status} {body}")
    status, body = kc.api(kc.token("cd_stu4"), "GET", f"/data/{tiny}/request?size=50")
    rt.check("CD-22 and nothing was saved for it", status == 200 and body["data"]["totalElements"] == 9, str(body)[:200])

    for fixture, needle in [("code-without-sequence", "line 10"), ("code-with-default", "line 10")]:
        status, body = kc.api(admin, "POST", "/models/plan", rt.read(f"e2e/fixtures/invalid-models/{fixture}.yaml"), YAML)
        rt.check(f"CD-23 {fixture}", status == 400 and needle in body["data"][0], f"{status} {body.get('data')}")

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
