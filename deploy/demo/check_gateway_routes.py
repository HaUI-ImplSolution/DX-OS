#!/usr/bin/env python3
"""Fails when a gateway route has no token check and is not on the list of public routes below.

Run from anywhere: python3 deploy/demo/check_gateway_routes.py
A route counts as checked when it uses the bearer-token plugin config or the openid-connect plugin.
"""
import glob
import os
import sys

import yaml

APISIX = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "apisix")

# Route id -> why it may be reached without a token.
PUBLIC = {
    "api-system-status": "service status shown by the web client before sign-in",
    "api-preflight": "CORS preflight carries no token",
    "api-docs": "Swagger UI and OpenAPI document",
    "keycloak-login": "sign-in and token exchange of Keycloak",
    "keycloak": "Keycloak pages, which sign users in themselves",
    "photos": "presigned links; the signature is the credential",
    "superset": "Superset signs users in with Keycloak",
    "storage-admin": "object store admin page with its own login",
    "portal-root": "static portal",
    "portal": "static portal",
    "monitor-ingest": "gateway events delivered with the monitor key",
    "monitor": "monitor pages, which sign users in with Keycloak",
    "web": "static web client",
}


def routes_of(path):
    data = yaml.safe_load(open(path, encoding="utf-8"))
    return (data.get("routes", []) if isinstance(data, dict) else data) or []


def is_checked(route):
    return (route.get("plugin_config_id") == "bearer-token"
            or "openid-connect" in (route.get("plugins") or {}))


def main():
    files = [os.path.join(APISIX, "common.yaml")] + sorted(glob.glob(os.path.join(APISIX, "apps", "*.yaml")))
    routes = [route for path in files for route in routes_of(path)]
    unchecked = {route["id"] for route in routes if not is_checked(route)}
    errors = [f"route {rid} has no token check and is not on the public list"
              for rid in sorted(unchecked - PUBLIC.keys())]
    errors += [f"public list names {rid}, which no longer exists or now has a token check"
               for rid in sorted(PUBLIC.keys() - unchecked)]
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        sys.exit(1)
    print(f"{len(routes)} routes; {len(unchecked)} public on purpose, the others check the token.")


if __name__ == "__main__":
    main()
