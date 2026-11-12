# SPDX-License-Identifier: Apache-2.0
"""Checks of the command line of deploy/dxos (plan, apply, archive) and of the IT/OT simulator.

They run the real scripts against the stack that deploy/.env describes and apply a model of their
own, so they need nothing from the scripts before them:
    python3 deploy/dev/check_cli.py
Needs docker (the archive reads the database through a DuckDB container, which downloads its
postgres and httpfs extensions on the first run). Settings: DXOS_ENV_FILE, DXOS_BASE_URL, DXOS_PROJECT.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

import check_api as rt
import kc

DXOS = os.path.join(os.path.dirname(kc.ENV_FILE), "dxos")
SIMULATOR = os.path.join(kc.REPO, "examples", "itot-simulator", "simulator.py")

MODEL = """format: 0
name: {name}
roles:
  - name: SENSOR
  - name: TECH
    staff: true
entities:
  incident:
    label: Incident
    fields:
      device: {{type: text, required: true}}
      metric: {{type: text, required: true}}
      reading: {{type: number, required: true}}
      threshold: {{type: number, required: true}}
      observation: {{type: text, required: true, unique: true}}
{fields}    access: {{SENSOR: own, TECH: all}}
    process: {{family: work_item, handler: TECH}}
"""
DETAIL = "      detail: {type: text}\n"
PLACE = "      place: {type: text}\n"


def run(command, **kw):
    done = subprocess.run(command, capture_output=True, text=True, **kw)
    return done.returncode, done.stdout, done.stderr


def dxos(work, *args):
    return run([DXOS, *args], cwd=work)


def plan(work, file):
    code, out, err = dxos(work, "plan", file)
    try:
        return code, json.loads(out)["data"]
    except (ValueError, KeyError):
        return code, {"raw": out + err}


def versions(admin, name):
    status, body = kc.api(admin, "GET", f"/models/{name}/versions")
    return body["data"] if status == 200 else []


def incidents(token, name):
    status, body = kc.api(token, "GET", f"/data/{name}/incident?size=200")
    return body["data"]["items"] if status == 200 else None


def check(case, ok, detail=""):
    # The output of the commands is shown only for a check that fails.
    rt.check(case, ok, "" if ok else detail)


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")
    name = f"cli{int(time.time())}"
    work = tempfile.mkdtemp(prefix="dxos-cli-")
    try:
        write = lambda file, fields: open(os.path.join(work, file), "w").write(MODEL.format(name=name, fields=fields))
        write("v1.yaml", DETAIL)
        write("v2.yaml", DETAIL + PLACE)
        write("v3.yaml", DETAIL)

        # plan and apply: a new model, the same file again, a safe change, then a change that loses data.
        code, data = plan(work, "v1.yaml")
        check("CLI-01 plan prints the plan of a new model and exits 0", code == 0 and data.get("action") == "create" and data.get("level") == "SAFE", str(data)[:200])
        code, out, err = dxos(work, "apply", "v1.yaml")
        check("CLI-02 apply of a new model applies it", code == 0 and '"status": "applied"' in out and [v["version"] for v in versions(admin, name)] == [1], f"{code} {out[-200:]} {err}")
        code, out, err = dxos(work, "apply", "v1.yaml")
        check("CLI-03 apply of the same file changes nothing and exits 0", code == 0 and "Nothing to apply" in out and len(versions(admin, name)) == 1, f"{code} {out} {err}")
        code, data = plan(work, "v2.yaml")
        check("CLI-04 plan of an added field is SAFE", code == 0 and data.get("level") == "SAFE" and data.get("action") != "unchanged", str(data)[:200])
        code, out, err = dxos(work, "apply", "v2.yaml")
        check("CLI-05 a SAFE change applies without --accept", code == 0 and len(versions(admin, name)) == 2, f"{code} {out[-200:]} {err}")
        code, data = plan(work, "v3.yaml")
        check("CLI-06 plan of a dropped field is LOSS", code == 0 and data.get("level") == "LOSS", str(data)[:200])
        code, out, err = dxos(work, "apply", "v3.yaml")
        check("CLI-07 a LOSS change without --accept exits 1 and applies nothing", code == 1 and len(versions(admin, name)) == 2, f"{code} {out[-200:]} {err}")
        code, out, err = dxos(work, "apply", "v3.yaml", "--accept", "MIGRATION")
        check("CLI-08 --accept below the level of the plan exits 1 and applies nothing", code == 1 and len(versions(admin, name)) == 2, f"{code} {out[-200:]} {err}")
        code, out, err = dxos(work, "apply", "v3.yaml", "--accept", "LOSS")
        check("CLI-09 --accept LOSS applies it", code == 0 and len(versions(admin, name)) == 3, f"{code} {out[-200:]} {err}")
        check("CLI-10 the history names the service account of the command line as the one who applied", all(v.get("applied_by_name") == "service-account-dxos-cli" for v in versions(admin, name)), str(versions(admin, name))[:300])

        # simulator: a reading over the threshold opens one incident; sending it again is refused as a duplicate.
        kc.ensure_user("cli_tech", [f"{name}/TECH"])
        tech = kc.token("cli_tech")
        env = dict(os.environ, DXOS_ENV_FILE=kc.ENV_FILE, DXOS_BASE_URL=kc.BASE, DXOS_ITOT_STATE=os.path.join(work, "itot.json"))
        simulator = lambda *args: run([sys.executable, SIMULATOR, "--model", name, *args], env=env)
        code, out, err = simulator("setup")
        check("CLI-11 simulator setup creates its service account", code == 0, f"{code} {out} {err}")
        code, out, err = simulator("run", "--devices", "1", "--base", "100", "--threshold", "80", "--ticks", "1", "--interval", "0")
        found = incidents(tech, name)
        check("CLI-12 a reading over the threshold opens one incident", code == 0 and "created" in out and found is not None and len(found) == 1, f"{code} {out} {err} {found}")
        code, out, err = simulator("replay")
        found = incidents(tech, name)
        check("CLI-13 replay of the last observation prints duplicate, exits 0 and adds no record", code == 0 and "duplicate" in out and found is not None and len(found) == 1, f"{code} {out} {err} {found}")

        # archive: the finished record goes to Parquet; one changed byte makes the check fail.
        status, body = kc.api(tech, "POST", f"/data/{name}/incident/{found[0]['id'] if found else 0}/actions/reject", {"note": "not needed"})
        check("CLI-14 the incident is decided", status == 200, f"{status} {str(body)[:150]}")
        out_dir = os.path.join(work, "archive")
        code, out, err = dxos(work, "archive", name, "--out", out_dir)
        runs = os.listdir(os.path.join(out_dir, name)) if os.path.isdir(os.path.join(out_dir, name)) else []
        directory = os.path.join(out_dir, name, runs[0]) if runs else ""
        parquet = os.path.join(directory, "incident.parquet")
        check("CLI-15 archive writes the finished records and a manifest", code == 0 and os.path.isfile(parquet) and os.path.isfile(os.path.join(directory, "manifest.json")), f"{code} {out[-300:]} {err[-300:]}")
        code, out, err = dxos(work, "archive", "check", directory)
        check("CLI-16 archive check passes on the archive as written", code == 0 and "Archive matches its manifest" in out, f"{code} {out[-200:]} {err[-200:]}")
        with open(parquet, "r+b") as handle:
            handle.seek(8)
            original = handle.read(1)
            handle.seek(8)
            handle.write(bytes([original[0] ^ 0xFF]))
        code, out, err = dxos(work, "archive", "check", directory)
        check("CLI-17 archive check exits 1 when one byte of a Parquet file changed", code == 1 and "does not match its manifest" in err, f"{code} {out[-200:]} {err[-200:]}")
        with open(parquet, "r+b") as handle:
            handle.seek(8)
            handle.write(original)
        code, out, err = dxos(work, "archive", "check", directory)
        check("CLI-18 and passes again once the byte is back", code == 0, f"{code} {out[-200:]} {err[-200:]}")

        # archive to the object store: written once, read back from the store, and not written twice.
        code, out, err = dxos(work, "archive", name, "--store")
        run_id = next((line.split("--store ", 1)[1].strip() for line in out.splitlines() if "--store " in line), "")
        check("CLI-19 archive --store writes the finished records to the object store", code == 0 and "incident.parquet: 1 rows" in out and run_id.startswith(name + "/"), f"{code} {out[-300:]} {err[-300:]}")
        code, out, err = dxos(work, "archive", "check", "--store", run_id)
        check("CLI-20 archive check --store passes on the run in the store", code == 0 and "Archive matches its manifest" in out and "SHA-256 matches" in out, f"{code} {out[-200:]} {err[-200:]}")
        code, out, err = dxos(work, "archive", "query", run_id, "--file", "incident.parquet", "--param", "status='rejected'", "SELECT count(*) FROM archive WHERE status = $status")
        check("CLI-21 archive query reads one run with a bound parameter", code == 0 and out.strip() == "1", f"{code} {out[-200:]} {err[-200:]}")
        code, out, err = dxos(work, "archive", "query", run_id, "SELECT count(*) FROM archive")
        check("CLI-22 archive query requires an explicit Parquet file", code == 1 and "--file is required" in err, f"{code} {out[-200:]} {err[-200:]}")
        code, out, err = dxos(work, "archive", "query", run_id, "--file", "*.parquet", "SELECT 1")
        check("CLI-23 archive query rejects a Parquet glob", code == 1 and "one Parquet file" in err, f"{code} {out[-200:]} {err[-200:]}")
        code, out, err = dxos(work, "archive", "query", run_id, "--file", "missing.parquet", "SELECT 1")
        check("CLI-24 archive query requires a manifest-listed file", code == 1 and "not listed in the archive manifest" in err, f"{code} {out[-200:]} {err[-200:]}")
        code, out, err = dxos(work, "archive", "query", run_id, "--file", "../secret.parquet", "SELECT 1")
        check("CLI-25 archive query rejects a path outside the selected run", code == 1 and "Parquet file" in err, f"{code} {out[-200:]} {err[-200:]}")
        code, out, err = dxos(work, "archive", "query", run_id, "--file", "incident.parquet", "DELETE FROM archive")
        check("CLI-26 archive query rejects a non-read statement", code == 1 and "SELECT, WITH, or EXPLAIN" in err, f"{code} {out[-200:]} {err[-200:]}")
        code, out, err = dxos(work, "archive", "query", run_id, "--file", "incident.parquet", "SELECT * FROM read_parquet('s3://outside/secret.parquet')")
        check("CLI-27 archive query blocks SQL from reading outside the selected run", code != 0 and "file system operations are disabled by configuration" in err.lower(), f"{code} {out[-200:]} {err[-200:]}")
        code, out, err = dxos(work, "archive", "auto")
        code_list, listing, _ = dxos(work, "archive", "list")
        runs_of_model = [line for line in listing.splitlines() if line.startswith(name + " ")]
        check("CLI-25 archive auto writes nothing for records newer than ARCHIVE_AFTER_DAYS and list shows the one run", code == 0 and f"{name}: nothing" in out and len(runs_of_model) == 1, f"{code} {out[-200:]} {err[-200:]} {runs_of_model}")
    finally:
        shutil.rmtree(work, ignore_errors=True)
        uid = kc.kc("GET", "/users?username=cli_tech&exact=true")[1]
        if uid:
            kc.kc("DELETE", f"/users/{uid[0]['id']}")

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
