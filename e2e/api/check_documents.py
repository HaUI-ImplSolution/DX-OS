# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Checks of the print templates of a model: the PDF of a record, who may get it and when.

They apply a model of their own, so they need nothing from the scripts before them:
    python3 e2e/api/check_documents.py
"""
import json
import re
import sys
import time
import urllib.error
import urllib.request

import check_api as rt
import kc

YAML = rt.YAML

MODEL = """format: 1
name: NAME
roles:
  - {name: STUDENT}
  - {name: OFFICER, staff: true}
entities:
  request:
    label: Hồ sơ
    fields:
      full_name: {type: text, required: true, label: Họ và tên}
      note: {type: text, label: Ghi chú}
      amount: {type: number, label: Số tiền}
      born: {type: date, label: Ngày sinh}
      agreed: {type: boolean, label: Đồng ý}
    access: {STUDENT: own, OFFICER: all}
    process: {family: approval, handler: OFFICER}
    documents:
      confirmation:
        label: Giấy xác nhận
        file: giay-xac-nhan-{{id}}-{{full_name}}
        when: approved
        title: GIẤY XÁC NHẬN
        body:
          - heading: Thông tin chung
          - text: "Xác nhận {{full_name}}, sinh ngày {{born}}, đã nộp hồ sơ số {{id}}."
          - fields: [full_name, note, amount, born, agreed, status, created_at]
          - signatures: [Người nộp, Cán bộ phụ trách, Thủ trưởng đơn vị]
      summary:
        label: Tóm tắt
        file: tom-tat-{{id}}
        body:
          - text: "{{full_name}} / {{note}} / {{amount}}"
LONG
          - signatures: [Người lập]
"""


def model(name):
    paragraph = ("Nội dung dài để kiểm việc xuống trang của tài liệu in, gồm chữ tiếng Việt có dấu như "
                 "Đặng Thị Ánh, Nguyễn Văn Ơn, Phạm Quỳnh Như và Lê Ngọc Hưng. ") * 12
    blocks = "".join(f"          - text: {json.dumps(paragraph, ensure_ascii=False)}\n" for _ in range(8))
    return MODEL.replace("NAME", name).replace("LONG\n", blocks)


def fetch(token, path):
    """GET that keeps the body as bytes, which kc.api cannot do for a PDF."""
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    request = urllib.request.Request(f"{kc.BASE}/api/v1{path}", headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, dict(response.headers), response.read()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read()


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")
    name = f"docs{int(time.time())}"
    base = f"/data/{name}/request"

    status, body = kc.api(admin, "POST", "/models/apply", model(name), YAML)
    rt.check("PD-01 a model with print templates applies", status == 200 and body["data"]["status"] == "applied", f"{status} {str(body)[:300]}")
    kc.ensure_user("pd_stu1", [f"{name}/STUDENT"])
    kc.ensure_user("pd_stu2", [f"{name}/STUDENT"])
    kc.ensure_user("pd_off", [f"{name}/OFFICER"])
    stu1, stu2, off = (kc.token(u) for u in ("pd_stu1", "pd_stu2", "pd_off"))

    status, made = kc.api(stu1, "POST", base, {"full_name": "Nguyễn Văn Ánh", "born": "2004-03-09", "amount": 1500000.5, "agreed": True})
    record = made["data"]["id"]

    status, body = kc.api(stu1, "GET", f"{base}/{record}/documents")
    listed = {d["name"]: d for d in body["data"]} if status == 200 else {}
    rt.check("PD-02 the owner lists the templates of the record", set(listed) == {"confirmation", "summary"}, f"{status} {str(body)[:200]}")
    rt.check("PD-03 and sees which can be printed in this state",
             listed.get("summary", {}).get("available") is True and listed.get("confirmation", {}).get("available") is False
             and listed.get("summary", {}).get("file") == f"tom-tat-{record}.pdf", str(listed))

    status, headers, pdf = fetch(stu1, f"{base}/{record}/documents/summary")
    disposition = headers.get("Content-Disposition", "")
    rt.check("PD-04 the owner gets the PDF", status == 200 and pdf.startswith(b"%PDF-") and headers.get("Content-Type") == "application/pdf"
             and f'filename="tom-tat-{record}.pdf"' in disposition and headers.get("Cache-Control", "").startswith("no-store"), f"{status} {headers}")
    rt.check("PD-05 the Vietnamese font is embedded", b"NotoSans" in pdf, f"{len(pdf)} bytes")
    rt.check("PD-06 an empty value does not break the page", status == 200 and re.search(rb"/Type\s*/Page(?!s)", pdf) is not None)

    status, headers, body = fetch(stu1, f"{base}/{record}/documents/confirmation")
    rt.check("PD-07 a template outside its states is refused", status == 409 and b"DOCUMENT_NOT_AVAILABLE" in body, f"{status} {body[:200]}")
    status, _, _ = fetch(stu1, f"{base}/{record}/documents/nothing")
    rt.check("PD-08 an unknown template is 404", status == 404, str(status))
    status, _, _ = fetch(stu2, f"{base}/{record}/documents/summary")
    rt.check("PD-09 another student cannot print the record", status == 404, str(status))
    status, _, _ = fetch(stu2, f"{base}/{record}/documents")
    rt.check("PD-10 nor list its templates", status == 404, str(status))
    status, _, _ = fetch(None, f"{base}/{record}/documents/summary")
    rt.check("PD-11 a request without a token is 401", status == 401, str(status))

    status, _ = kc.api(off, "POST", f"{base}/{record}/actions/approve", {})
    rt.check("PD-12 the officer approves", status == 200, str(status))
    status, headers, pdf = fetch(stu1, f"{base}/{record}/documents/confirmation")
    rt.check("PD-13 the confirmation prints once approved, under an ASCII name",
             status == 200 and pdf.startswith(b"%PDF-") and f'filename="giay-xac-nhan-{record}-Nguyen_Van_Anh.pdf"' in headers.get("Content-Disposition", ""), f"{status} {headers.get('Content-Disposition')}")

    status, headers, pdf = fetch(off, f"{base}/{record}/documents/summary")
    pages = len(re.findall(rb"/Type\s*/Page(?!s)", pdf))
    rt.check("PD-14 a long template runs over several pages", status == 200 and pages >= 2, f"{status} {pages} pages")

    for fixture, needle in [("document-unknown-field", "line 14"), ("document-state-without-process", "line 13")]:
        status, body = kc.api(admin, "POST", "/models/plan", rt.read(f"e2e/fixtures/invalid-models/{fixture}.yaml"), YAML)
        rt.check(f"PD-19 {fixture}", status == 400 and needle in body["data"][0], f"{status} {body.get('data')}")

    code, count = rt.psql("select count(*) from audit_log where action = 'DOCUMENT_PRINTED'")
    rt.check("PD-15 every print is in the audit log", code == 0 and int(count) >= 3, count)

    status, body = kc.api(admin, "GET", f"/meta/models/{name}/openapi")
    paths = (body.get("paths") or {}) if isinstance(body, dict) else {}
    rt.check("PD-16 the OpenAPI document of the model describes the templates",
             f"{base}/{{id}}/documents/{{name}}" in "".join(paths) or any(p.endswith("/documents/{name}") for p in paths), str(list(paths))[:300])
    status, body = kc.api(stu1, "GET", "/meta/models")
    entity = next((e for m in body["data"] if m["name"] == name for e in m["entities"]), {})
    rt.check("PD-17 the metadata lists the templates for the screens",
             [d["name"] for d in entity.get("documents", [])] == ["confirmation", "summary"] and entity["documents"][0]["when"] == ["approved"], str(entity.get("documents")))

    changed = model(name).replace("title: GIẤY XÁC NHẬN", "title: GIẤY XÁC NHẬN SINH VIÊN")
    status, body = kc.api(admin, "POST", "/models/plan", changed, YAML)
    kinds = [c["kind"] for c in body["data"]["changes"]] if status == 200 else []
    rt.check("PD-18 a changed template is a safe change in the plan", kinds == ["CHANGE_DOCUMENTS"] and body["data"]["level"] == "SAFE", f"{status} {kinds}")

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
