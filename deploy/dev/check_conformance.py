# SPDX-License-Identifier: Apache-2.0
"""Checks that an applied model keeps the barriers of the core, whatever the model declares.

Run on a stack where the models are applied (all of them, or the ones named):
    python3 deploy/dev/check_conformance.py [model ...]
Per model it takes one user who holds a role of that model and checks that:
  - the status of a record of a process cannot be written through the data API (403);
  - a request without a token is refused (401);
  - the data of a few other models is out of reach (403 or 404, never data).
"""
import sys

import check_api as rt
import kc

SAMPLE_ENTITIES = 2
# The gateway limits the requests of one caller, so each user is tried on a few other models, the next ones in turn.
SAMPLE_MODELS = 3


def sample_user(model):
    """A user who holds the first role of the model, or None when the model has no role."""
    roles = kc.client_role_names(model)
    if not roles:
        return None
    name = f"cf_{model}"[:40]
    kc.ensure_user(name, [f"{model}/{roles[0]}"])
    return kc.token(name)


def entities_of(token, model):
    status, body = kc.api(token, "GET", "/meta/models")
    if status != 200:
        return []
    for entry in body["data"]:
        if entry.get("name") == model:
            return entry.get("entities", [])
    return []


def main():
    kc.ensure_client()
    kc.ensure_user("admin1", ["ADMIN"])
    admin = kc.token("admin1")
    status, listed = kc.api(admin, "GET", "/models")
    models = [m["model"] for m in listed["data"] if not m.get("held") and m.get("version", 0) > 0]
    wanted = sys.argv[1:] or models
    rt.check("CF-01 the models to check are applied", bool(wanted) and all(m in models for m in wanted), f"{wanted} of {models}")

    users = {}
    for model in models:
        token = sample_user(model)
        if token is None:
            continue
        users[model] = (token, entities_of(token, model))

    for model in wanted:
        if model not in users or not users[model][1]:
            continue
        token, entities = users[model]
        for entity in entities:
            if entity.get("process") and entity.get("canWrite"):
                states = [s["name"] for s in entity["process"]["states"]]
                status, body = kc.api(token, "POST", f"/data/{model}/{entity['name']}", {"status": states[-1]})
                rt.check(f"CF-02 {model}.{entity['name']} refuses a status written through the data API",
                         status == 403 and (body.get("data") or {}).get("reason") == "STATUS_BY_PROCESS_ONLY", f"{status} {body}")
        first = entities[0]["name"]
        status, _ = kc.api(None, "GET", f"/data/{model}/{first}")
        rt.check(f"CF-03 {model}.{first} refuses a request without a token", status == 401, str(status))
        leaks = []
        names = list(users)
        start = names.index(model)
        neighbours = [names[(start + step) % len(names)] for step in range(1, min(SAMPLE_MODELS, len(names) - 1) + 1)]
        for other in neighbours:
            for entity in users[other][1][:SAMPLE_ENTITIES]:
                status, _ = kc.api(token, "GET", f"/data/{other}/{entity['name']}")
                if status not in (403, 404):
                    leaks.append(f"{other}.{entity['name']}:{status}")
        rt.check(f"CF-04 a user of {model} reaches no data of another model", not leaks, ", ".join(leaks[:5]))

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
