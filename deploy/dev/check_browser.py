# SPDX-License-Identifier: Apache-2.0
"""Checks the supported registration path for an external browser application."""

import base64
import hashlib
import html.parser
import http.cookiejar
import ipaddress
import os
import secrets
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

import check_api as rt
import kc

DXOS = os.path.join(os.path.dirname(kc.ENV_FILE), "dxos")
NAME = "browser-check"
CLIENT_ID = f"dxos-browser-{NAME}"
REDIRECT = "https://browser.example.test/auth/callback"
ORIGIN = "https://browser.example.test"
USER = os.environ.get("E2E_USER", "ui_admin")
PASSWORD = os.environ.get("E2E_PASSWORD", "Test1234!")


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


class LoginForm(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.action = ""
        self.hidden = {}

    def handle_starttag(self, tag, attrs):
        attributes = dict(attrs)
        if tag == "form" and attributes.get("id") == "kc-form-login":
            self.action = attributes.get("action", "")
        if tag == "input" and attributes.get("type") == "hidden" and attributes.get("name"):
            self.hidden[attributes["name"]] = attributes.get("value", "")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, msg, headers, newurl):
        return None


class LoopbackCookiePolicy(http.cookiejar.DefaultCookiePolicy):
    def return_ok_secure(self, cookie, request):
        host = urllib.parse.urlsplit(request.get_full_url()).hostname
        try:
            loopback = host == "localhost" or ipaddress.ip_address(host).is_loopback
        except ValueError:
            loopback = False
        if cookie.secure and request.type == "http" and loopback:
            return True
        return super().return_ok_secure(cookie, request)


def authorization_code():
    verifier = secrets.token_urlsafe(64)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    query = urllib.parse.urlencode({
        "client_id": CLIENT_ID,
        "redirect_uri": REDIRECT,
        "response_type": "code",
        "scope": "openid profile",
        "code_challenge": challenge,
        "code_challenge_method": "S256",
    })
    cookies = http.cookiejar.CookieJar(LoopbackCookiePolicy())
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(cookies), NoRedirect())
    auth_url = f"{kc.BASE}/realms/dxos/protocol/openid-connect/auth?{query}"
    response = opener.open(auth_url)
    form = LoginForm()
    form.feed(response.read().decode())
    fields = dict(form.hidden, username=USER, password=PASSWORD)
    request = urllib.request.Request(
        urllib.parse.urljoin(auth_url, form.action),
        data=urllib.parse.urlencode(fields).encode(),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST")
    try:
        opener.open(request)
    except urllib.error.HTTPError as error:
        if error.code not in (301, 302, 303, 307, 308):
            raise
        location = error.headers.get("Location", "")
    else:
        raise AssertionError("Keycloak login did not redirect to the browser callback")
    callback = urllib.parse.urlparse(location)
    values = urllib.parse.parse_qs(callback.query)
    assert "code" in values and "error" not in values, values
    status, token = kc.form(f"{kc.BASE}/realms/dxos/protocol/openid-connect/token", {
        "grant_type": "authorization_code", "client_id": CLIENT_ID,
        "redirect_uri": REDIRECT, "code": values["code"][0],
        "code_verifier": verifier})
    assert status == 200 and token.get("access_token"), (status, token)
    return token["access_token"]


def main():
    kc.ensure_user(USER, ["ADMIN"])
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

        token = authorization_code()
        status, body = kc.http("GET", f"{kc.BASE}/api/v1/models", headers={
            "Authorization": f"Bearer {token}", "Origin": ORIGIN})
        rt.check("BROWSER-06 PKCE login exchanges a code and calls the API",
                 status == 200 and body.get("success") is True, str(body)[:300])
    finally:
        removed = dxos("client", "browser", "remove", NAME)
        rt.check("BROWSER-07 remove deletes the public client", removed.returncode == 0,
                 (removed.stdout + removed.stderr)[-300:])

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
