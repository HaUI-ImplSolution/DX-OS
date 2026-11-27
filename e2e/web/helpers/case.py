# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Setup for the browser check of a decision on a record that changed.

seed REASON: makes sure the onestop model runs, a student and an officer exist, and the student has filed one request with that reason; prints its id.
edit ID REASON: the student changes the reason of that request, as a second person would while the officer has the page open.
model: an administrator applies a new version of the onestop model, as would happen while the officer has the page open.
"""
import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "api"))
import kc  # noqa: E402

REQUEST = "/data/onestop/request"
TEMPLATE = os.path.join(kc.REPO, "backend", "dx-engine", "src", "main", "resources", "templates", "onestop.yaml")


def seed(reason):
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")
    status, models = kc.api(admin, "GET", "/models")
    if not any(model.get("model") == "onestop" for model in models["data"]):
        with open(TEMPLATE, encoding="utf-8") as template:
            status, _ = kc.api(admin, "POST", "/models/apply", template.read(), "application/yaml")
        assert status == 200, status
    kc.ensure_user("stu1", ["onestop/STUDENT"])
    kc.ensure_user("off1", ["onestop/OFFICER"], ["DAO_TAO"])
    status, created = kc.api(kc.token("stu1"), "POST", REQUEST, {"reason": reason, "kind": "Phúc khảo", "department": "DAO_TAO"})
    assert status == 200 or status == 201, (status, created)
    print(json.dumps({"id": created["data"]["id"]}))


def edit(record, reason):
    status, answer = kc.api(kc.token("stu1"), "PATCH", f"{REQUEST}/{record}", {"reason": reason})
    assert status == 200, (status, answer)


def model():
    admin = kc.token("admin1")
    status, models = kc.api(admin, "GET", "/models")
    version = next(entry["version"] for entry in models["data"] if entry["model"] == "onestop")
    with open(TEMPLATE, encoding="utf-8") as template:
        content = template.read().replace("Sinh viên nộp hồ sơ", f"Sinh viên nộp hồ sơ (bản {version + 1})")
    status, answer = kc.api(admin, "POST", f"/models/apply?base={version}", content, "application/yaml")
    assert status == 200, (status, answer)


if __name__ == "__main__":
    if sys.argv[1] == "seed":
        seed(sys.argv[2])
    elif sys.argv[1] == "edit":
        edit(sys.argv[2], sys.argv[3])
    else:
        model()
