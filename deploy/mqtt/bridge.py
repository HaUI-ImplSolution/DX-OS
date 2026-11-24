#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Turns MQTT messages into records through the DX-OS data API, as a registered machine client.

The bridge lives outside the core: it subscribes to the topics of its config file and calls
/api/v1/data like any application. Each route of the config names a topic filter, the model and
entity to create a record in, an optional condition on the payload, and how the record is built:

  {"routes": [{
     "topic": "plant/+/reading", "model": "plant", "entity": "incident",
     "when": {"field": "reading", "op": ">", "value": 80},
     "skip_if_open": {"field": "device", "value": "{topic.1}"},
     "key": "{payload.observation}",
     "record": {"device": "{topic.1}", "reading": "{payload.reading}", "detail": "{payload.metric} is high"}}]}

In a string, {topic.N} is level N of the topic and {payload.name} is a field of the JSON payload;
a string that is only one such placeholder keeps the type of the value. The key names the
observation, so a message delivered twice creates one record. A message the core rejects (400, 409)
or that does not fit the route is logged and dropped; any other failure is retried until it passes,
so no message is lost while the core is down.

The routes come from MQTT_BRIDGE_ROUTES (the JSON text) when it is set, else from the file bridge.json next to this one.

Settings by environment: MQTT_HOST, MQTT_PORT, MQTT_USER, MQTT_PASSWORD, MQTT_BRIDGE_ROUTES,
DXOS_API_URL (gateway reached from this network), DXOS_PUBLIC_URL (the address Keycloak knows),
MQTT_BRIDGE_CLIENT_ID and MQTT_BRIDGE_CLIENT_SECRET (from dxos client add --env MQTT_BRIDGE).
"""
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from minimqtt import Client, MqttError  # noqa: E402

ALIVE = "/tmp/alive"
DROP = {400, 409}
OPERATORS = {
    ">": lambda a, b: a > b, ">=": lambda a, b: a >= b, "<": lambda a, b: a < b,
    "<=": lambda a, b: a <= b, "==": lambda a, b: a == b, "!=": lambda a, b: a != b,
}
WHOLE = re.compile(r"^\{(topic|payload)\.([^{}]+)\}$")
PART = re.compile(r"\{(topic|payload)\.([^{}]+)\}")


def log(level, message):
    print(f"{time.strftime('%Y-%m-%dT%H:%M:%S%z')} {level} {message}", flush=True)


def touch():
    try:
        with open(ALIVE, "w") as handle:
            handle.write(str(time.time()))
    except OSError:
        pass


class Retry(Exception):
    pass


# --- templates --------------------------------------------------------------------------------

def lookup(kind, name, topic, payload):
    if kind == "topic":
        return topic[int(name)]
    if name not in payload or payload[name] is None:
        raise KeyError(f"payload.{name}")
    return payload[name]


def render(value, topic, payload):
    if isinstance(value, dict):
        return {k: render(v, topic, payload) for k, v in value.items()}
    if not isinstance(value, str):
        return value
    whole = WHOLE.match(value)
    if whole:
        return lookup(whole.group(1), whole.group(2), topic, payload)
    return PART.sub(lambda m: str(lookup(m.group(1), m.group(2), topic, payload)), value)


def matches(pattern, topic):
    want, have = pattern.split("/"), topic.split("/")
    for index, level in enumerate(want):
        if level == "#":
            return True
        if index >= len(have) or (level != "+" and level != have[index]):
            return False
    return len(want) == len(have)


def passes(when, payload):
    if not when:
        return True
    value = payload.get(when["field"])
    numeric = isinstance(value, (int, float)) and not isinstance(value, bool)
    if when["op"] not in ("==", "!=") and not numeric:
        raise ValueError(f"payload.{when['field']} is not a number")
    return OPERATORS[when["op"]](value, when["value"])


# --- API --------------------------------------------------------------------------------------

class Api:
    def __init__(self):
        self.base = os.environ.get("DXOS_API_URL", "http://apisix:9080").rstrip("/")
        public = os.environ.get("DXOS_PUBLIC_URL", "")
        self.host = urllib.parse.urlparse(public).netloc if public else None
        self.client = os.environ["MQTT_BRIDGE_CLIENT_ID"]
        self.secret = os.environ["MQTT_BRIDGE_CLIENT_SECRET"]
        self.token, self.until = None, 0.0

    def request(self, method, path, body=None, headers=None, form=None):
        sent = dict(headers or {})
        if self.host:
            sent["Host"] = self.host
        data = None
        if form is not None:
            data = urllib.parse.urlencode(form).encode()
            sent["Content-Type"] = "application/x-www-form-urlencoded"
        elif body is not None:
            data = json.dumps(body).encode()
            sent["Content-Type"] = "application/json"
        req = urllib.request.Request(self.base + path, data=data, method=method, headers=sent)
        try:
            with urllib.request.urlopen(req, timeout=30) as response:
                status, raw = response.status, response.read()
        except urllib.error.HTTPError as error:
            status, raw = error.code, error.read()
        except (urllib.error.URLError, OSError) as error:
            raise Retry(f"the API is not reachable: {error}")
        try:
            return status, json.loads(raw) if raw else None
        except ValueError:
            return status, raw.decode("utf-8", "replace")

    def login(self):
        status, body = self.request("POST", "/realms/dxos/protocol/openid-connect/token", form={
            "grant_type": "client_credentials", "client_id": self.client, "client_secret": self.secret})
        if status != 200 or not isinstance(body, dict) or "access_token" not in body:
            raise Retry(f"Keycloak refused {self.client} (HTTP {status}); check MQTT_BRIDGE_CLIENT_SECRET")
        self.token, self.until = body["access_token"], time.time() + body.get("expires_in", 300) - 30

    def call(self, method, path, body=None, headers=None):
        for attempt in (0, 1):
            if self.token is None or time.time() >= self.until:
                self.login()
            status, answer = self.request(method, "/api/v1" + path, body, {**(headers or {}), "Authorization": f"Bearer {self.token}"})
            if status == 401 and attempt == 0:
                self.token = None
                continue
            return status, answer


def settle(action):
    """Runs action until it does not raise Retry, waiting longer after each failure (up to 30 s)."""
    wait = 1
    while True:
        try:
            return action()
        except Retry as why:
            log("WARN", f"{why}; trying again in {wait}s")
            touch()
            time.sleep(wait)
            wait = min(wait * 2, 30)


# --- routes -----------------------------------------------------------------------------------

def load_routes(path):
    text = os.environ.get("MQTT_BRIDGE_ROUTES")
    try:
        if text:
            routes = json.loads(text)["routes"]
        else:
            with open(path, encoding="utf-8") as handle:
                routes = json.load(handle)["routes"]
    except (OSError, ValueError, KeyError) as why:
        sys.exit(f"bridge: cannot read the routes of {'MQTT_BRIDGE_ROUTES' if text else path}: {why}")
    for route in routes:
        for name in ("topic", "model", "entity", "record"):
            if name not in route:
                sys.exit(f"bridge: a route has no {name}: {route}")
        when = route.get("when")
        if when and (when.get("op") not in OPERATORS or "field" not in when or "value" not in when):
            sys.exit(f"bridge: the condition of {route['topic']} needs field, value and an op of {sorted(OPERATORS)}")
        skip = route.get("skip_if_open")
        if skip and ("field" not in skip or "value" not in skip):
            sys.exit(f"bridge: skip_if_open of {route['topic']} needs field and value")
    return routes


def prepare(api, route):
    """Reads the entity once: it must be writable, and an entity with a process says which states are open."""
    def action():
        status, body = api.call("GET", f"/meta/models/{route['model']}/entities/{route['entity']}")
        if status != 200:
            raise Retry(f"{route['model']}/{route['entity']} is not reachable (HTTP {status}); is the model applied?")
        entity = body["data"]
        if not entity["canWrite"]:
            raise Retry(f"the role of {api.client} may not create {route['entity']} records")
        return entity
    entity = settle(action)
    if route.get("skip_if_open"):
        process = entity.get("process")
        if not process:
            sys.exit(f"bridge: skip_if_open of {route['topic']} needs an entity with a process")
        route["_open"] = ",".join(s["name"] for s in process["states"] if not s["terminal"])
    return route


def handle(api, routes, topic, raw):
    levels = topic.split("/")
    route = next((r for r in routes if matches(r["topic"], topic)), None)
    if route is None:
        return log("INFO", f"{topic}: no route, dropped")
    try:
        payload = json.loads(raw)
        if not isinstance(payload, dict):
            raise ValueError("the payload is not a JSON object")
        if not passes(route.get("when"), payload):
            return log("INFO", f"{topic}: below the condition, ignored")
        record = render(route["record"], levels, payload)
        key = render(route["key"], levels, payload) if "key" in route else hashlib.sha1(raw).hexdigest()
        wanted = render(route["skip_if_open"], levels, payload) if route.get("skip_if_open") else None
    except (ValueError, KeyError, IndexError) as why:
        return log("WARN", f"{topic}: does not fit the route ({why}), dropped")
    path = f"/data/{route['model']}/{route['entity']}"
    if wanted:
        query = urllib.parse.urlencode({f"filter.{wanted['field']}": wanted["value"], "filter.status.in": route["_open"], "size": 1})
        def look():
            status, body = api.call("GET", f"{path}?{query}")
            if status != 200:
                raise Retry(f"listing {path} failed (HTTP {status})")
            return body["data"]["totalElements"]
        if settle(look):
            return log("INFO", f"{topic}: a record of {wanted['value']} is still open, skipped")
    identity = hashlib.sha1(f"{route['model']}/{route['entity']}/{key}".encode()).hexdigest()[:32]

    def create():
        status, body = api.call("POST", path, record, {"Idempotency-Key": f"mqtt-{identity}"})
        if status in (200, 201):
            return body["data"]["id"]
        if status in DROP:
            return (status, body)
        raise Retry(f"creating in {path} failed (HTTP {status}): {body if isinstance(body, str) else json.dumps(body)[:200]}")
    result = settle(create)
    if isinstance(result, tuple):
        status, body = result
        return log("WARN", f"{topic}: rejected by the core (HTTP {status}): {json.dumps(body, ensure_ascii=False)[:300]}")
    log("INFO", f"{topic}: record #{result} in {route['model']}/{route['entity']}")


def main():
    routes = load_routes(os.environ.get("MQTT_BRIDGE_CONFIG", "/app/bridge.json"))
    api = Api()
    routes = [prepare(api, route) for route in routes]
    topics = sorted({route["topic"] for route in routes})
    log("INFO", f"bridge ready: {len(routes)} route(s), topics {topics}")
    wait = 1
    while True:
        client = Client(os.environ.get("MQTT_HOST", "mosquitto"), int(os.environ.get("MQTT_PORT", "1883")),
                        "dxos-mqtt-bridge", os.environ.get("MQTT_USER"), os.environ.get("MQTT_PASSWORD"), keepalive=120)
        try:
            client.connect()
            client.subscribe(topics)
            log("INFO", "connected to the broker")
            wait = 1
            for item in client.messages():
                touch()
                if item is None:
                    continue
                topic, raw, identifier = item
                handle(api, routes, topic, raw)
                if identifier is not None:
                    client.ack(identifier)
        except (MqttError, OSError) as why:
            log("WARN", f"broker connection lost: {why}; connecting again in {wait}s")
            client.close()
            touch()
            time.sleep(wait)
            wait = min(wait * 2, 30)


if __name__ == "__main__":
    main()
