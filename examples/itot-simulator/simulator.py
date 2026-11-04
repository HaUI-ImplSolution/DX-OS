#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Simulated plant sensors that open incidents in DX-OS through its public API.

The simulator lives outside the core: it only signs in with its own service account and calls
/api/v1/data. Commands:

  setup   creates the Keycloak client dxos-itot-<model>, gives its service account the role of the
          model and keeps the secret in the state file (needs the Keycloak admin of the .env)
  run     reads the devices every few seconds; a reading over the threshold opens one incident per
          observation, and a resolved incident is confirmed when the reading is back to normal or
          reopened when it is not
  replay  sends the last observation again, which the core refuses as a duplicate

Settings by environment: DXOS_BASE_URL (default http://localhost:9080), DXOS_ENV_FILE (default
deploy/.env of this repository, read by setup only), DXOS_ITOT_STATE (default
~/.local/state/dxos-itot-<model>.json).
"""
import argparse
import json
import os
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
BASE = os.environ.get("DXOS_BASE_URL", "http://localhost:9080").rstrip("/")
OPEN = "submitted,in_progress"


def http(method, url, body=None, headers=None):
    headers = dict(headers or {})
    data = None
    if isinstance(body, (dict, list)):
        data = json.dumps(body).encode()
        headers.setdefault("Content-Type", "application/json")
    elif isinstance(body, str):
        data = body.encode()
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload, status = response.read().decode(), response.status
    except urllib.error.HTTPError as error:
        payload, status = error.read().decode(), error.code
    try:
        return status, json.loads(payload) if payload else None
    except ValueError:
        return status, payload


def form(url, fields):
    return http("POST", url, urllib.parse.urlencode(fields),
                {"Content-Type": "application/x-www-form-urlencoded"})


def state_path(model):
    default = os.path.join(os.path.expanduser("~"), ".local", "state", f"dxos-itot-{model}.json")
    return os.environ.get("DXOS_ITOT_STATE", default)


def load_state(model):
    path = state_path(model)
    if not os.path.exists(path):
        sys.exit(f"No state file {path}; run 'setup' first")
    with open(path) as handle:
        return json.load(handle)


def save_state(model, state):
    path = state_path(model)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "w") as handle:
        json.dump(state, handle, indent=2)


# --- setup ------------------------------------------------------------------------------------

def read_env():
    path = os.environ.get("DXOS_ENV_FILE", os.path.join(REPO, "deploy", ".env"))
    with open(path) as handle:
        return dict(line.split("=", 1) for line in handle.read().splitlines()
                    if "=" in line and not line.startswith("#"))


def setup(args):
    env = read_env()
    status, body = form(f"{BASE}/realms/master/protocol/openid-connect/token", {
        "grant_type": "password", "client_id": "admin-cli",
        "username": env["KEYCLOAK_ADMIN_USERNAME"], "password": env["KEYCLOAK_ADMIN_PASSWORD"]})
    if status != 200:
        sys.exit(f"Keycloak admin sign-in failed: {status} {body}")
    admin = {"Authorization": f"Bearer {body['access_token']}"}

    def kc(method, path, payload=None):
        return http(method, f"{BASE}/admin/realms/dxos{path}", payload, admin)

    status, models = kc("GET", f"/clients?clientId=dxos-m-{args.model}")
    if status != 200 or not models:
        sys.exit(f"Model {args.model} is not applied: no client dxos-m-{args.model}")
    model_client = models[0]["id"]
    status, role = kc("GET", f"/clients/{model_client}/roles/{args.role}")
    if status != 200:
        sys.exit(f"Model {args.model} has no role {args.role}")

    client_id = f"dxos-itot-{args.model}"
    status, found = kc("GET", f"/clients?clientId={client_id}")
    if not found:
        status, body = kc("POST", "/clients", {
            "clientId": client_id, "name": f"IT/OT simulator of {args.model}",
            "protocol": "openid-connect", "publicClient": False, "serviceAccountsEnabled": True,
            "standardFlowEnabled": False, "directAccessGrantsEnabled": False,
            "protocolMappers": [{
                "name": "dxos-api-audience", "protocol": "openid-connect",
                "protocolMapper": "oidc-audience-mapper",
                "config": {"included.custom.audience": "dxos-api", "access.token.claim": "true",
                           "id.token.claim": "false"}}]})
        if status != 201:
            sys.exit(f"Could not create client {client_id}: {status} {body}")
        status, found = kc("GET", f"/clients?clientId={client_id}")
    uuid = found[0]["id"]
    status, account = kc("GET", f"/clients/{uuid}/service-account-user")
    status, body = kc("POST", f"/users/{account['id']}/role-mappings/clients/{model_client}", [role])
    if status != 204:
        sys.exit(f"Could not give role {args.role} to {client_id}: {status} {body}")
    status, secret = kc("GET", f"/clients/{uuid}/client-secret")
    save_state(args.model, {"client_id": client_id, "secret": secret["value"],
                            "entity": args.entity, "last_observation": None})
    print(f"Client {client_id} holds role {args.role} of {args.model}; "
          f"state kept in {state_path(args.model)}")


# --- run ----------------------------------------------------------------------------------------

class Api:
    def __init__(self, model, state):
        self.model, self.state = model, state
        self.path = f"/api/v1/data/{model}/{state['entity']}"
        self.token, self.until = None, 0

    def headers(self):
        if time.time() >= self.until:
            status, body = form(f"{BASE}/realms/dxos/protocol/openid-connect/token", {
                "grant_type": "client_credentials", "client_id": self.state["client_id"],
                "client_secret": self.state["secret"]})
            if status != 200:
                sys.exit(f"Sign-in of {self.state['client_id']} failed: {status} {body}")
            self.token, self.until = body["access_token"], time.time() + body["expires_in"] - 30
        return {"Authorization": f"Bearer {self.token}"}

    def call(self, method, path="", body=None):
        return http(method, f"{BASE}{self.path}{path}", body, self.headers())

    def records(self, **filters):
        query = urllib.parse.urlencode({f"filter.{k}": v for k, v in filters.items()} | {"size": 200})
        status, body = self.call("GET", f"?{query}")
        if status != 200:
            raise RuntimeError(f"list failed: {status} {body}")
        return body["data"]["items"]

    def open_incident(self, observation):
        status, body = self.call("POST", "", observation)
        if status in (200, 201):
            return "created", body["data"]["id"]
        errors = body.get("data") if isinstance(body, dict) else None
        if status == 400 and isinstance(errors, dict) and "observation" in errors:
            return "duplicate", None
        raise RuntimeError(f"create failed: {status} {body}")


def observe(api, device, reading, threshold, metric):
    code = f"{device}-{int(time.time() * 1000)}"
    observation = {"device": device, "metric": metric, "reading": round(reading, 1),
                   "threshold": threshold, "observation": code,
                   "detail": f"{metric} {reading:.1f} vượt ngưỡng {threshold}"}
    outcome, record = api.open_incident(observation)
    api.state["last_observation"] = observation
    save_state(api.model, api.state)
    print(f"{device}: {metric} {reading:.1f} > {threshold}, observation {code}: {outcome}"
          + (f" as record #{record}" if record else ""))


def follow_up(api, device, reading, threshold):
    for incident in api.records(device=device, status="resolved"):
        if reading <= threshold:
            api.call("POST", f"/{incident['id']}/actions/confirm", {})
            print(f"{device}: reading {reading:.1f} is normal, record #{incident['id']} confirmed")
        else:
            api.call("POST", f"/{incident['id']}/actions/reopen",
                     {"note": f"Chỉ số vẫn vượt ngưỡng: {reading:.1f}"})
            print(f"{device}: reading {reading:.1f} still high, record #{incident['id']} reopened")


def run(args):
    api = Api(args.model, load_state(args.model))
    devices = {f"{args.prefix}-{n + 1:02d}": args.base for n in range(args.devices)}
    tick = 0
    while args.ticks == 0 or tick < args.ticks:
        tick += 1
        for device, last in devices.items():
            # A slow random walk with the odd spike, as a temperature probe would read.
            reading = last + random.uniform(-3, 3) + (random.uniform(15, 30) if random.random() < args.spike else 0)
            reading = max(args.base - 20, min(reading, args.threshold + 40))
            devices[device] = reading if reading <= args.threshold else (args.base + reading) / 2
            follow_up(api, device, reading, args.threshold)
            if reading > args.threshold and not api.records(device=device, **{"status.in": OPEN}):
                observe(api, device, reading, args.threshold, args.metric)
        time.sleep(args.interval)


def replay(args):
    api = Api(args.model, load_state(args.model))
    observation = api.state.get("last_observation")
    if not observation:
        sys.exit("No observation sent yet; run 'run' first")
    outcome, record = api.open_incident(observation)
    print(f"observation {observation['observation']} sent again: {outcome}"
          + (f" as record #{record}" if record else ""))
    sys.exit(0 if outcome == "duplicate" else 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--model", default="plant")
    commands = parser.add_subparsers(dest="command", required=True)
    one = commands.add_parser("setup", help="create the service account of the simulator")
    one.add_argument("--role", default="SENSOR")
    one.add_argument("--entity", default="incident")
    one.set_defaults(handler=setup)
    one = commands.add_parser("run", help="read the devices and open incidents")
    one.add_argument("--devices", type=int, default=3)
    one.add_argument("--prefix", default="pump")
    one.add_argument("--metric", default="Nhiệt độ (°C)")
    one.add_argument("--base", type=float, default=60.0)
    one.add_argument("--threshold", type=float, default=80.0)
    one.add_argument("--spike", type=float, default=0.15, help="chance of a spike per reading")
    one.add_argument("--interval", type=float, default=5.0, help="seconds between two readings")
    one.add_argument("--ticks", type=int, default=0, help="readings per device, 0 runs until stopped")
    one.set_defaults(handler=run)
    one = commands.add_parser("replay", help="send the last observation again")
    one.set_defaults(handler=replay)
    args = parser.parse_args()
    args.handler(args)


if __name__ == "__main__":
    main()
