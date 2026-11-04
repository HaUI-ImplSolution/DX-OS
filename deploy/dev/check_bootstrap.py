# SPDX-License-Identifier: Apache-2.0
"""Checks of the model file the backend applies when it starts (dxos start <file>).

Run on a stack that is up, after the other checks:
    python3 deploy/dev/check_bootstrap.py
The script puts files in deploy/model/, restarts the backend with each of them, and reads the line
"Model bootstrap: <result> <detail>" that every start writes. The model bootcheck stays applied; the
files and the setting are removed at the end.
"""
import os
import re
import subprocess
import sys
import time
from datetime import datetime, timezone

import check_api as rt
import kc

MODEL_DIR = f"{rt.REPO}/deploy/model"
NAME = "bootcheck.yaml"
FIRST = """format: 0
name: bootcheck
roles:
  - name: BOOT_ROLE
entities:
  item:
    fields:
      title: {type: text, required: true}
    access: {BOOT_ROLE: all}
"""
WITH_NOTE = FIRST.replace("required: true}\n", "required: true}\n      note: {type: text}\n")
WITHOUT_TITLE = FIRST.replace("      title: {type: text, required: true}\n", "      note: {type: text}\n")
LINE = re.compile(r"Model bootstrap: (applied|unchanged|skipped|failed)\s*(.*)")


def write(name, text):
    os.makedirs(MODEL_DIR, exist_ok=True)
    with open(f"{MODEL_DIR}/{name}", "w", encoding="utf-8") as handle:
        handle.write(text)
    os.chmod(f"{MODEL_DIR}/{name}", 0o644)


def restart(file_name):
    # Starts the backend again with the file; returns the result line it logs, or None without a file.
    since = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    env = dict(os.environ, DXOS_MODEL_FILE=file_name or "")
    subprocess.run(rt.COMPOSE + ["up", "-d", "--force-recreate", "backend"], capture_output=True, text=True, cwd=f"{rt.REPO}/deploy", env=env)
    assert rt.wait_api(), "backend did not come back"
    end = time.time() + (120 if file_name else 15)
    while time.time() < end:
        logs = rt.sh(rt.COMPOSE + ["logs", "--no-log-prefix", "--since", since, "backend"]).stdout
        found = [m.groups() for m in map(LINE.search, logs.splitlines()) if m]
        if found:
            return found
        time.sleep(3)
    return []


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")

    write(NAME, FIRST)
    result = restart(NAME)
    status, models = kc.api(admin, "GET", "/models")
    version = next((m["version"] for m in models["data"] if m["model"] == "bootcheck"), None)
    code, by = rt.psql("select applied_by from engine.model_version where model = 'bootcheck' and version = 1")
    rt.check("BS-01 a file never applied is applied at start by bootstrap, with one result line", result[:1] == [("applied", "bootcheck version 1")] and version == 1 and by == "bootstrap", f"{result} {version} {by}")
    result = restart(NAME)
    rt.check("BS-02 the same file at the next start is left alone", [r[0] for r in result] == ["unchanged"], str(result))

    write(NAME, WITH_NOTE)
    result = restart(NAME)
    rt.check("BS-03 a changed file with a safe plan is applied as the next version", result[:1] == [("applied", "bootcheck version 2")], str(result))

    write(NAME, FIRST)
    result = restart(NAME)
    status, models = kc.api(admin, "GET", "/models")
    version = next((m["version"] for m in models["data"] if m["model"] == "bootcheck"), None)
    rt.check("BS-04 an older file that was applied before is not applied again", [r[0] for r in result] == ["skipped"] and version == 2, f"{result} {version}")

    write(NAME, WITHOUT_TITLE)
    result = restart(NAME)
    status, models = kc.api(admin, "GET", "/models")
    version = next((m["version"] for m in models["data"] if m["model"] == "bootcheck"), None)
    rt.check("BS-05 a file whose plan is not safe is not applied and says why", [r[0] for r in result] == ["skipped"] and version == 2 and "level" in result[0][1], f"{result} {version}")

    write(NAME, "model: [broken\n  entities: x:\n")
    result = restart(NAME)
    rt.check("BS-06 a broken file fails with the line and column, and the backend still runs", [r[0] for r in result] == ["failed"] and "line" in result[0][1], str(result))

    result = restart("missing.yaml")
    rt.check("BS-07 a file that is not there fails and the backend still runs", [r[0] for r in result] == ["failed"], str(result))

    result = restart(None)
    rt.check("BS-08 without a file the backend logs no bootstrap line", result == [], str(result))

    os.remove(f"{MODEL_DIR}/{NAME}")
    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
