# SPDX-License-Identifier: Apache-2.0
"""Checks the supported registration path for an external browser application."""

import os
import subprocess
import sys
import urllib.error
import urllib.request

import check_api as rt
import kc

DXOS = os.path.join(os.path.dirname(kc.ENV_FILE), "dxos")
NAME = "browser-check"
CLIENT_ID = f"dxos-browser-{NAME}"
REDIRECT = "https://browser.example.test/auth/callback"
ORIGIN = "https://browser.example.test"


def dxos(*args):
    return subprocess.run([DXOS, *args], cwd=kc.REPO, capture_output=True, text=True)


def preflight(origin):
    request = urllib.request.Request(
        f"{kc.BASE}/api/v1/system/status",
        method="OPTIONS",
        headers={"Origin": origin, "Access-Control-Request-Method": "GET"},
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.headers.get("Access-Control-Allow-Origin")
    except urllib.error.HTTPError as error:
        return error.code, error.headers.get("Access-Control-Allow-Origin")


def main():
    dxos("client", "browser", "remove", NAME)
    try:
        added = dxos("client", "browser", "add", NAME, "--redirect-uri", REDIRECT)
        rt.check("BROWSER-01 the CLI registers a browser client", added.returncode == 0,
                 (added.stdout + added.stderr)[-300:])

        status, found = kc.kc("GET", f"/clients?clientId={CLIENT_ID}")
        representation = {}
        if status == 200 and found:
            status, representation = kc.kc("GET", f"/clients/{found[0]['id']}")
        mapper_names = {mapper.get("name") for mapper in representation.get("protocolMappers", [])}
        configured = (
            status == 200
            and representation.get("publicClient") is True
            and representation.get("standardFlowEnabled") is True
            and representation.get("implicitFlowEnabled") is False
            and representation.get("directAccessGrantsEnabled") is False
            and representation.get("serviceAccountsEnabled") is False
            and representation.get("redirectUris") == [REDIRECT]
            and representation.get("webOrigins") == [ORIGIN]
            and representation.get("attributes", {}).get("pkce.code.challenge.method") == "S256"
            and {"dxos-api-audience", "groups"}.issubset(mapper_names)
        )
        rt.check("BROWSER-02 the client is public, PKCE-only and carries API claims", configured,
                 str(representation)[:400])

        env_text = open(kc.ENV_FILE, encoding="utf-8").read()
        status, allowed = preflight(ORIGIN)
        rt.check("BROWSER-03 its exact origin is persisted and accepted by CORS",
                 f"CORS_ALLOWED_ORIGINS=" in env_text and ORIGIN in env_text
                 and status in (200, 204) and allowed == ORIGIN,
                 f"status={status} allow={allowed}")

        listed = dxos("client", "browser", "list")
        rt.check("BROWSER-04 list shows the client without a secret",
                 listed.returncode == 0 and CLIENT_ID in listed.stdout
                 and "secret" not in listed.stdout.lower(), listed.stdout[-300:])

        unsafe_http = dxos("client", "browser", "add", "unsafe-http",
                           "--redirect-uri", "http://browser.example.test/callback")
        unsafe_wildcard = dxos("client", "browser", "add", "unsafe-wildcard",
                               "--redirect-uri", "https://*.example.test/callback")
        rt.check("BROWSER-05 unsafe redirects are refused before Keycloak is changed",
                 unsafe_http.returncode != 0 and unsafe_wildcard.returncode != 0,
                 unsafe_http.stderr[-150:] + unsafe_wildcard.stderr[-150:])
    finally:
        removed = dxos("client", "browser", "remove", NAME)
        rt.check("BROWSER-06 remove deletes the public client", removed.returncode == 0,
                 (removed.stdout + removed.stderr)[-300:])

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
