# SPDX-License-Identifier: Apache-2.0
"""Checks what the gateway shows of tier 1: no page of its own unless the settings allow it.

Run on a stack that is up, in either mode (DXOS_MODE and KEYCLOAK_ADMIN_CONSOLE are read from deploy/.env):
    python3 e2e/api/check_surface.py
It needs no account: the paths are asked without a token, and the backend's own reach into the Keycloak Admin API is
checked with the client credentials of the dxos command.
"""
import json
import sys
import urllib.error
import urllib.request

import check_api as rt
import kc

MODE = kc.ENV.get("DXOS_MODE") or "full"
CONSOLE = (kc.ENV.get("KEYCLOAK_ADMIN_CONSOLE") or "false") == "true"
SWAGGER = ["/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/"]
ADMIN = ["/admin", "/admin/", "/admin/master/console/", "/admin/realms/dxos"]
TIER_TWO = ["/", "/portal/", "/monitor/", "/console"]
TIER_TWO_SERVICES = {"frontend", "portal", "monitor", "monitor-init"}


def get(path, headers=None):
    """Status, content type and the start of the body, without following a redirect."""
    class Stay(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None

    opener = urllib.request.build_opener(Stay)
    request = urllib.request.Request(f"{kc.BASE}{path}", headers=headers or {})
    try:
        with opener.open(request, timeout=30) as response:
            return response.status, response.headers.get("Content-Type", ""), response.read(200).decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.headers.get("Content-Type", ""), error.read(200).decode("utf-8", "replace")


def is_html(kind, body):
    return "html" in kind.lower() or body.lstrip().lower().startswith(("<!doctype", "<html"))


def not_served(paths):
    """The paths that answer something else than 404 or answer a page."""
    wrong = []
    for path in paths:
        status, kind, body = get(path)
        if status != 404 or is_html(kind, body):
            wrong.append(f"{path} -> {status} {kind.split(';')[0]}")
    return wrong


def services():
    out = rt.sh(rt.COMPOSE + ["ps", "-a", "--format", "{{.Service}}"]).stdout
    return {line.strip() for line in out.splitlines() if line.strip()}


def main():
    status, kind, body = get("/v3/api-docs")
    rt.check("SF-01 the OpenAPI document is served as JSON", status == 200 and "json" in kind and '"openapi"' in body, f"{status} {kind}")

    wrong = not_served(SWAGGER)
    rt.check("SF-02 the core serves no Swagger UI", not wrong, "; ".join(wrong))

    if CONSOLE:
        opened = [path for path in ADMIN[:3] if get(path)[0] == 404]
        rt.check("SF-03 the Keycloak Admin Console is open because KEYCLOAK_ADMIN_CONSOLE=true", not opened, "; ".join(opened))
    else:
        wrong = not_served(ADMIN)
        rt.check("SF-03 the Keycloak Admin Console is closed by default", not wrong, "; ".join(wrong))

    status, kind, body = get("/realms/dxos/.well-known/openid-configuration")
    rt.check("SF-04 sign-in is still served by Keycloak", status == 200 and "json" in kind, f"{status} {kind}")

    secret = kc.ENV.get("DXOS_CLI_SECRET", "")
    status, token = kc.form(f"{kc.BASE}/realms/dxos/protocol/openid-connect/token", {
        "grant_type": "client_credentials", "client_id": "dxos-cli", "client_secret": secret})
    status, answer = kc.api(token.get("access_token") if isinstance(token, dict) else None, "GET", "/clients")
    rt.check("SF-05 the backend reaches the Keycloak Admin API with the Admin Console closed or open",
             status == 200 and isinstance(answer, dict) and answer.get("success") is True, f"{status}")

    present = services() & TIER_TWO_SERVICES
    if MODE == "core":
        rt.check("SF-06 core mode runs no container of tier 2", not present, ", ".join(sorted(present)))
        wrong = not_served(TIER_TWO)
        rt.check("SF-07 core mode serves no page of tier 2", not wrong, "; ".join(wrong))
    else:
        rt.check("SF-06 full mode runs the web app, the portal and the monitor", present == TIER_TWO_SERVICES, ", ".join(sorted(present)))
        status, kind, body = get("/")
        portal = get("/portal/")
        rt.check("SF-07 full mode serves the web app and the portal", status == 200 and is_html(kind, body) and portal[0] == 200 and is_html(portal[1], portal[2]), f"{status} {portal[0]}")

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
