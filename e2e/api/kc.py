# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Helpers for the manual checks: Keycloak test users and tokens, API calls.

Settings by environment: DXOS_ENV_FILE (default deploy/.env), DXOS_BASE_URL (default http://localhost:9080).
"""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
BASE = os.environ.get("DXOS_BASE_URL", "http://localhost:9080")
ENV_FILE = os.environ.get("DXOS_ENV_FILE", os.path.join(REPO, "deploy", ".env"))
ENV = dict(
    line.split("=", 1) for line in open(ENV_FILE).read().splitlines()
    if "=" in line and not line.startswith("#")
)
CLIENT = "dxos-check"


def http(method, url, body=None, headers=None, raw=False):
    data = None
    headers = dict(headers or {})
    if body is not None:
        if isinstance(body, (dict, list)):
            data = json.dumps(body).encode()
            headers.setdefault("Content-Type", "application/json")
        elif isinstance(body, bytes):
            data = body
        else:
            data = body.encode()
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = response.read().decode()
            status, location = response.status, response.headers.get("Location")
    except urllib.error.HTTPError as error:
        payload, status, location = error.read().decode(), error.code, None
    if raw:
        return status, payload, location
    try:
        return status, json.loads(payload) if payload else None
    except ValueError:
        return status, payload


def form(url, fields):
    body = urllib.parse.urlencode(fields).encode()
    return http("POST", url, body, {"Content-Type": "application/x-www-form-urlencoded"})


# The gateway limits logins per minute, so a burst of them waits for the next minute.
def login(url, fields):
    for _ in range(5):
        status, body = form(url, fields)
        if status != 429:
            break
        time.sleep(30)
    assert status == 200, (fields.get("username"), status, body)
    return body["access_token"]


_admin = {"token": None, "until": 0}


def admin_token():
    if time.time() >= _admin["until"]:
        _admin["token"] = login(f"{BASE}/realms/master/protocol/openid-connect/token", {
            "grant_type": "password", "client_id": "admin-cli",
            "username": ENV["KEYCLOAK_ADMIN_USERNAME"], "password": ENV["KEYCLOAK_ADMIN_PASSWORD"]})
        _admin["until"] = time.time() + 45
    return _admin["token"]


def kc(method, path, body=None):
    token = admin_token()
    status, answer = http(method, f"{BASE}/admin/realms/dxos{path}", body, {"Authorization": f"Bearer {token}"})
    # The gateway closes /admin unless KEYCLOAK_ADMIN_CONSOLE=true, and the scripts reach the Keycloak Admin API through it.
    assert not (status == 404 and "Not found" in str(answer)[:120]), (
        "The gateway does not open /admin; set KEYCLOAK_ADMIN_CONSOLE=true in deploy/.env and recreate apisix-config and apisix")
    return status, answer


def ensure_client():
    status, found = kc("GET", f"/clients?clientId={CLIENT}")
    if found:
        return
    status, _ = kc("POST", "/clients", {
        "clientId": CLIENT, "publicClient": True, "directAccessGrantsEnabled": True,
        "standardFlowEnabled": False, "protocol": "openid-connect",
        "protocolMappers": [
            {"name": "dxos-api-audience", "protocol": "openid-connect",
             "protocolMapper": "oidc-audience-mapper",
             "config": {"included.custom.audience": "dxos-api", "access.token.claim": "true",
                       "id.token.claim": "false"}},
            {"name": "groups", "protocol": "openid-connect",
             "protocolMapper": "oidc-group-membership-mapper",
             "config": {"full.path": "false", "claim.name": "groups", "access.token.claim": "true",
                        "id.token.claim": "false", "userinfo.token.claim": "false"}}],
    })
    assert status in (201, 409), status


def client_uuid(model):
    status, found = kc("GET", f"/clients?clientId=dxos-m-{model}")
    return found[0]["id"] if status == 200 and found else None


def client_role_names(model):
    status, roles = kc("GET", f"/clients/{client_uuid(model)}/roles")
    return [r["name"] for r in roles] if status == 200 else []


def ensure_user(name, roles, groups=()):
    status, found = kc("GET", f"/users?username={name}&exact=true")
    if not found:
        status, _ = kc("POST", "/users", {"username": name, "enabled": True, "firstName": name,
                                          "lastName": "Test", "email": f"{name}@example.com",
                                          "emailVerified": True})
        assert status == 201, status
        status, found = kc("GET", f"/users?username={name}&exact=true")
    user_id = found[0]["id"]
    status, _ = kc("PUT", f"/users/{user_id}/reset-password",
                   {"type": "password", "value": "Test1234!", "temporary": False})
    assert status == 204, status
    for role in roles:
        if "/" in role:
            # "model/ROLE" is a role of that model, held in the client of the model.
            model, name = role.split("/", 1)
            uuid = client_uuid(model)
            assert uuid, f"the model {model} is not applied"
            status, role_repr = kc("GET", f"/clients/{uuid}/roles/{name}")
            assert status == 200, (role, status, role_repr)
            status, _ = kc("POST", f"/users/{user_id}/role-mappings/clients/{uuid}", [role_repr])
        else:
            status, role_repr = kc("GET", f"/roles/{role}")
            assert status == 200, (role, status, role_repr)
            status, _ = kc("POST", f"/users/{user_id}/role-mappings/realm", [role_repr])
        assert status == 204, status
    for group in groups:
        status, found = kc("GET", f"/groups?search={group}&exact=true")
        assert status == 200 and found, (group, status, found)
        status, _ = kc("PUT", f"/users/{user_id}/groups/{found[0]['id']}")
        assert status == 204, status
    return user_id


def token(user):
    return login(f"{BASE}/realms/dxos/protocol/openid-connect/token", {
        "grant_type": "password", "client_id": CLIENT, "username": user, "password": "Test1234!"})


def api(user_token, method, path, body=None, content_type=None):
    headers = {"Authorization": f"Bearer {user_token}"} if user_token else {}
    if content_type:
        headers["Content-Type"] = content_type
    return http(method, f"{BASE}/api/v1{path}", body, headers)
