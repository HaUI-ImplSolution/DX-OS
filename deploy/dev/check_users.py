# SPDX-License-Identifier: Apache-2.0
"""Checks of the user administration: who holds which role of which model, units, and accounts.

Run after check_guards.py on the same stack (it needs the models applied by the scripts before):
    python3 deploy/dev/check_users.py
"""
import json
import subprocess
import sys

import check_api as rt
import kc


def user_id(admin, username):
    status, body = kc.api(admin, "GET", f"/users?search={username}")
    found = [u for u in body["data"]["items"] if u["username"] == username]
    return found[0]["id"] if found else None


def audited(admin, action, target):
    # The audit records of one action about one account, newest first.
    status, body = kc.api(admin, "GET", f"/audit-logs?action={action}&size=200")
    return [e for e in body["data"]["items"] if e["targetId"] == target] if status == 200 else []


def grant_status(username, password="Test1234!"):
    # The status of a sign-in with a password, to see whether an account can sign in.
    status, body = kc.form(f"{kc.BASE}/realms/dxos/protocol/openid-connect/token", {
        "grant_type": "password", "client_id": kc.CLIENT, "username": username, "password": password})
    return status, str(body)


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")

    # T13-06: the list, a search and the paging.
    kc.ensure_user("usr_roles", [])
    kc.ensure_user("usr_unit", [])
    status, body = kc.api(admin, "GET", "/users?size=5")
    page = body["data"]
    rt.check("T13-06 a page of accounts with the total", status == 200 and len(page["items"]) == 5 and page["totalElements"] > 5 and page["totalPages"] > 1, str(page)[:200])
    status, body = kc.api(admin, "GET", "/users?search=usr_roles")
    rt.check("T13-06 a search finds the account", status == 200 and [u["username"] for u in body["data"]["items"]] == ["usr_roles"], str(body)[:200])
    status, body = kc.api(admin, "GET", "/users/options")
    options = body["data"]
    models = {m["model"]: [r["name"] for r in m["roles"]] for m in options["models"]}
    rt.check("T13-06 the options list the roles and units of the running models",
             sorted(models.get("equipment", [])) == ["BORROWER", "KEEPER"] and {"DAO_TAO", "CTSV"} <= {u["name"] for u in options["units"]}, str(models)[:200])

    # T13-07: giving and taking a role changes the token that comes after.
    uid = user_id(admin, "usr_roles")
    status, _ = kc.api(admin, "PUT", f"/users/{uid}/roles/equipment/BORROWER")
    rt.check("T13-07 a role of a model is given", status == 200, str(status))
    status, body = kc.api(admin, "GET", f"/users/{uid}")
    rt.check("T13-07 the detail shows it under the model", status == 200 and body["data"]["roles"].get("equipment") == ["BORROWER"], str(body)[:200])
    status, _ = kc.api(kc.token("usr_roles"), "GET", "/data/equipment/loan")
    rt.check("T13-07 the next token reaches the data of the model", status == 200, str(status))
    status, _ = kc.api(admin, "PUT", f"/users/{uid}/roles/equipment/BORROWER")
    rt.check("T13-07 giving it again changes nothing", status == 200, str(status))
    status, _ = kc.api(admin, "DELETE", f"/users/{uid}/roles/equipment/BORROWER")
    status2, body = kc.api(admin, "GET", f"/users/{uid}")
    rt.check("T13-07 the role is taken", status == 200 and status2 == 200 and not body["data"]["roles"].get("equipment"), str(body)[:200])
    status, _ = kc.api(kc.token("usr_roles"), "GET", "/data/equipment/loan")
    rt.check("T13-07 and the next token no longer reaches the data", status == 403, str(status))

    uid_unit = user_id(admin, "usr_unit")
    status, _ = kc.api(admin, "PUT", f"/users/{uid_unit}/units/DAO_TAO")
    status2, body = kc.api(admin, "GET", f"/users/{uid_unit}")
    rt.check("T13-07 a unit is given", status == 200 and status2 == 200 and body["data"]["units"] == ["DAO_TAO"], str(body)[:200])
    status, _ = kc.api(admin, "DELETE", f"/users/{uid_unit}/units/DAO_TAO")
    status2, body = kc.api(admin, "GET", f"/users/{uid_unit}")
    rt.check("T13-07 and taken", status == 200 and body["data"]["units"] == [], str(body)[:200])

    # T13-08: what does not exist is refused.
    missing = "00000000-0000-0000-0000-000000000000"
    cases = [
        ("a role the model does not declare", "PUT", f"/users/{uid}/roles/equipment/NOPE"),
        ("a model that is not applied", "PUT", f"/users/{uid}/roles/nomodel/BORROWER"),
        ("an account that does not exist", "PUT", f"/users/{missing}/roles/equipment/BORROWER"),
        ("a unit no model declares", "PUT", f"/users/{uid}/units/NOWHERE"),
        ("the detail of an account that does not exist", "GET", f"/users/{missing}"),
    ]
    for label, method, path in cases:
        status, body = kc.api(admin, method, path)
        rt.check(f"T13-08 {label}", status == 404, f"{status} {body}")

    # T13-09: only an administrator, and the roles of the platform are out of reach.
    status, _ = kc.api(kc.token("usr_roles"), "GET", "/users")
    rt.check("T13-09 an account without the ADMIN role cannot list accounts", status == 403, str(status))
    status, _ = kc.api(admin, "PUT", f"/users/{uid}/roles/equipment/ADMIN")
    rt.check("T13-09 the role ADMIN is not a role of any model", status == 404, str(status))
    status, _ = kc.api(admin, "PUT", f"/users/{uid}/roles/master/ADMIN")
    rt.check("T13-09 nor can it be reached through another name", status == 404, str(status))

    # T13-10: an account with a temporary password.
    leftover = user_id(admin, "usr_new")
    if leftover:
        kc.kc("DELETE", f"/users/{leftover}")
    status, body = kc.api(admin, "POST", "/users", {"username": "usr_new", "fullName": "Nguyen Van An", "email": "usr_new@example.com"})
    created = body["data"] if status == 200 else {}
    password = created.get("temporaryPassword", "")
    rt.check("T13-10 an account is created with a password it must change",
             status == 200 and len(password) >= 12 and created.get("username") == "usr_new", f"{status} {body if status != 200 else ''}")
    signin, text = grant_status("usr_new", password)
    rt.check("T13-10 the temporary password cannot sign in before it is changed", signin == 400 and "not fully set up" in text, f"{signin} {text[:120]}")
    status, _ = kc.api(admin, "POST", "/users", {"username": "usr_new", "fullName": "Other"})
    rt.check("T13-10 a taken username is refused", status == 409, str(status))
    status, body = kc.api(admin, "POST", "/users", {"username": "", "fullName": ""})
    rt.check("T13-10 a missing username and name are refused with the fields", status == 400 and set(body["data"]) == {"username", "fullName"}, f"{status} {body}")
    status, body = kc.api(admin, "GET", "/users?search=usr_new")
    rt.check("T13-10 the new account is found with its name", [u["name"] for u in body["data"]["items"]] == ["Nguyen Van An"], str(body)[:200])

    # T13-11: lock, unlock, new password.
    uid_new = user_id(admin, "usr_new")
    kc.ensure_user("usr_lock", [])
    uid_lock = user_id(admin, "usr_lock")
    status, _ = kc.api(admin, "PUT", f"/users/{uid_lock}/enabled", {"enabled": False})
    rt.check("T13-11 an account is locked and cannot sign in", status == 200 and grant_status("usr_lock")[0] == 400, str(status))
    status, _ = kc.api(admin, "PUT", f"/users/{uid_lock}/enabled", {"enabled": True})
    rt.check("T13-11 and unlocked", status == 200 and grant_status("usr_lock")[0] == 200, str(status))
    status, body = kc.api(admin, "POST", f"/users/{uid_lock}/password")
    fresh = body["data"]["temporaryPassword"] if status == 200 else ""
    signin, text = grant_status("usr_lock", fresh)
    rt.check("T13-11 a new temporary password must be changed before it signs in",
             status == 200 and len(fresh) >= 12 and signin == 400 and "not fully set up" in text, f"{status} {signin} {text[:100]}")
    rt.check("T13-11 and the old password no longer works", grant_status("usr_lock")[0] == 401)

    # T13-12: an administrator keeps their own account, and the passwords stay out of the logs.
    status, _ = kc.api(admin, "PUT", f"/users/{user_id(admin, 'admin1')}/enabled", {"enabled": False})
    rt.check("T13-12 an administrator cannot lock their own account", status == 409, str(status))
    logs = subprocess.run(rt.COMPOSE + ["logs", "backend", "--no-log-prefix", "--since", "30m"], capture_output=True, text=True, cwd=f"{rt.REPO}/deploy")
    rt.check("T13-12 no temporary password is in the backend log", password not in logs.stdout + logs.stderr and fresh not in logs.stdout + logs.stderr)
    status, _ = kc.api(admin, "DELETE", f"/users/{missing}/roles/equipment/BORROWER")
    rt.check("T13-12 taking a role from an account that does not exist is refused", status == 404, str(status))
    # T13-13: every change is in the audit log with the administrator who made it.
    def noted(action, target, **meta):
        entries = audited(admin, action, target)
        return any(e["actorName"] == "admin1" and e["targetType"] == "USER" and e["outcome"] == "SUCCESS"
                   and all(e["metadata"].get(k) == v for k, v in meta.items()) for e in entries)
    rt.check("T13-13 a role given and taken is recorded with who did it and which role",
             noted("USER_ROLE_GRANTED", uid, username="usr_roles", model="equipment", role="BORROWER")
             and noted("USER_ROLE_REVOKED", uid, username="usr_roles", model="equipment", role="BORROWER"))
    rt.check("T13-13 a unit added and taken is recorded",
             noted("USER_UNIT_ADDED", uid_unit, username="usr_unit", unit="DAO_TAO") and noted("USER_UNIT_REMOVED", uid_unit, unit="DAO_TAO"))
    rt.check("T13-13 an account created is recorded", noted("USER_CREATED", uid_new, username="usr_new"))
    rt.check("T13-13 a lock, an unlock and a new password are recorded",
             noted("USER_LOCKED", uid_lock, username="usr_lock") and noted("USER_UNLOCKED", uid_lock) and noted("USER_PASSWORD_RESET", uid_lock))
    status, body = kc.api(admin, "GET", f"/audit-logs?targetType=user&targetId={uid_lock}&size=200")
    rows = body["data"]["items"] if status == 200 else []
    rt.check("T13-13 the audit log lists the records about one account whoever made them",
             status == 200 and {e["action"] for e in rows} >= {"USER_LOCKED", "USER_UNLOCKED", "USER_PASSWORD_RESET"}
             and all(e["targetType"] == "USER" and e["targetId"] == uid_lock for e in rows), f"{status} {len(rows)}")
    status, body = kc.api(admin, "GET", f"/audit-logs?targetType=USER&targetId={missing}&size=200")
    rt.check("T13-13 an account with no record gives an empty list", status == 200 and body["data"]["items"] == [])
    status, body = kc.api(admin, "GET", "/audit-logs?actor=admin1&size=200")
    dump = str(body["data"]) if status == 200 else ""
    rt.check("T13-13 no password is in the audit log", status == 200 and password not in dump and fresh not in dump)
    status, body = kc.api(admin, "GET", f"/audit-logs?action=USER_ROLE_GRANTED&size=200")
    refused = [e for e in body["data"]["items"] if e["targetId"] == missing]
    rt.check("T13-13 a change that did not happen leaves no record", status == 200 and not refused)

    # T13-14: the events of Keycloak for the same changes do not hold a password either.
    status, events = kc.kc("GET", "/admin-events?max=500")
    text = json.dumps(events) if status == 200 else ""
    rt.check("T13-14 Keycloak records the changes of the accounts", status == 200 and any(uid_lock in (e.get("resourcePath") or "") for e in events), f"{status} {len(events) if status == 200 else ''}")
    rt.check("T13-14 and its events do not hold a temporary password", password not in text and fresh not in text)
    kc.kc("DELETE", f"/users/{uid_new}")

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
