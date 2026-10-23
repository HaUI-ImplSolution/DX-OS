# SPDX-License-Identifier: Apache-2.0
"""Small DX-OS API client shared by the demo seed and the E2E run of every app (standard library
+ Pillow). Nothing here names an app: accounts, data and scenarios live in the per-app modules."""
import base64
import collections
import hashlib
import html
import http.cookiejar
import io
import json
import os
import pathlib
import re
import secrets
import shlex
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import uuid
from urllib.parse import urljoin

DEFAULT_BASE_URL = "http://localhost:9080"
REALM = "dxos"
WEB_CLIENT = ("dxos-web", "http://localhost:9080/callback")
DEPLOY_DIR = pathlib.Path(__file__).resolve().parent.parent
# Cloudflare rejects the default Python-urllib agent with 403.
USER_AGENT = "DX-OS-demo/1.0"


def _opener(*handlers):
    opener = urllib.request.build_opener(*handlers)
    opener.addheaders = [("User-Agent", USER_AGENT)]
    return opener


urllib.request.install_opener(_opener())

# A sample account: realm role, name parts and the OIDC client its sign-in goes through.
Account = collections.namedtuple("Account", "username password client role first_name last_name")


def read_env(path=None):
    """Values of deploy/.env (DEMO_ENV_FILE selects another file)."""
    path = pathlib.Path(path or os.environ.get("DEMO_ENV_FILE", DEPLOY_DIR / ".env"))
    values = {}
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#") and "=" in line:
                key, _, value = line.partition("=")
                values[key.strip()] = value.strip()
    return values


def psql(sql):
    """Runs SQL in the compose postgres; DEMO_COMPOSE and DEMO_ENV_FILE select another project."""
    env = read_env()
    compose = shlex.split(os.environ.get("DEMO_COMPOSE", "docker compose"))
    subprocess.run(
        [*compose, "exec", "-T", "postgres", "psql", "-U", env["POSTGRES_USER"],
         "-d", env.get("POSTGRES_DB", "dxos"), "-v", "ON_ERROR_STOP=1", "-c", sql],
        cwd=DEPLOY_DIR, check=True, capture_output=True)


def ensure_accounts(base_url, accounts):
    """Creates the sample accounts and their realm roles in Keycloak; existing ones are kept."""
    env = read_env()
    keycloak = KeycloakAdmin(base_url, env["KEYCLOAK_ADMIN_USERNAME"], env["KEYCLOAK_ADMIN_PASSWORD"])
    for account in accounts:
        keycloak.ensure_user(account.username, account.password, account.first_name,
                             account.last_name, f"{account.username}@dxos.local", account.role)


def running_app(base_url):
    """Id of the app the backend runs, from the public system status; None when it is not known."""
    status, body = Api(base_url, "").call("GET", "/system/status")
    app = (body or {}).get("data", {}).get("app") if status == 200 else None
    return app["id"] if app else None


class KeycloakAdmin:
    """Keycloak Admin REST API through the gateway, signed in as the master realm administrator."""

    def __init__(self, base_url, username, password):
        self.base_url = base_url
        request = urllib.request.Request(
            f"{base_url}/realms/master/protocol/openid-connect/token",
            urllib.parse.urlencode(
                {"grant_type": "password", "client_id": "admin-cli", "username": username, "password": password}
            ).encode(),
        )
        with urllib.request.urlopen(request) as response:
            self.token = json.load(response)["access_token"]

    def call(self, method, path, body=None):
        """Returns (status, parsed JSON body or None) of a call under /admin/realms/{REALM}."""
        headers = {"Authorization": f"Bearer {self.token}"}
        payload = None
        if body is not None:
            payload = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            f"{self.base_url}/admin/realms/{REALM}{path}", payload, headers, method=method
        )
        try:
            with urllib.request.urlopen(request) as response:
                status, text = response.status, response.read().decode()
        except urllib.error.HTTPError as error:
            status, text = error.code, error.read().decode()
        try:
            return status, json.loads(text)
        except ValueError:
            return status, None

    def ensure_role(self, name):
        """Returns the realm role representation, creating the role when it does not exist."""
        status, role = self.call("GET", f"/roles/{urllib.parse.quote(name)}")
        if status == 404:
            self.call("POST", "/roles", {"name": name})
            status, role = self.call("GET", f"/roles/{urllib.parse.quote(name)}")
        if status != 200:
            raise RuntimeError(f"Realm role {name} is not available: HTTP {status}")
        return role

    def ensure_user(self, username, password, first_name, last_name, email, role_name):
        """Creates the account when missing and makes sure it holds the realm role."""
        role = self.ensure_role(role_name)
        status, _ = self.call(
            "POST",
            "/users",
            {
                "username": username,
                "enabled": True,
                "emailVerified": True,
                "firstName": first_name,
                "lastName": last_name,
                "email": email,
                "credentials": [{"type": "password", "value": password, "temporary": False}],
            },
        )
        if status not in (201, 409):
            raise RuntimeError(f"Account {username} was not created: HTTP {status}")
        status, found = self.call("GET", f"/users?username={urllib.parse.quote(username)}&exact=true")
        if status != 200 or not found:
            raise RuntimeError(f"Account {username} is not available: HTTP {status}")
        status, _ = self.call("POST", f"/users/{found[0]['id']}/role-mappings/realm", [role])
        if status >= 300:
            raise RuntimeError(f"Role {role_name} was not assigned to {username}: HTTP {status}")


class ApiError(Exception):
    def __init__(self, method, path, status, body):
        super().__init__(f"{method} {path} -> {status}: {body}")
        self.status = status
        self.body = body


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def _b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def login(base_url, username, password, client):
    """Authorization Code + PKCE through the Keycloak login form; returns an access token."""
    client_id, redirect_uri = client
    verifier = secrets.token_urlsafe(64)
    challenge = _b64url(hashlib.sha256(verifier.encode()).digest())
    jar = http.cookiejar.CookieJar()
    opener = _opener(urllib.request.HTTPCookieProcessor(jar), _NoRedirect)
    endpoint = f"{base_url}/realms/{REALM}/protocol/openid-connect"
    query = urllib.parse.urlencode(
        {
            "client_id": client_id,
            "redirect_uri": redirect_uri,
            "response_type": "code",
            "scope": "openid",
            "code_challenge": challenge,
            "code_challenge_method": "S256",
            "state": secrets.token_hex(8),
        }
    )
    login_page = opener.open(f"{endpoint}/auth?{query}").read().decode()
    # Keycloak marks its session cookies Secure; the gateway here serves plain HTTP.
    for cookie in jar:
        cookie.secure = False
    form = re.search(r"<form\b[^>]*id=\"kc-form-login\"[^>]*>", login_page)
    action = re.search(r"action=\"([^\"]+)\"", form.group(0)) if form else None
    if not action:
        raise RuntimeError("Keycloak login form not found")
    data = urllib.parse.urlencode({"username": username, "password": password}).encode()
    try:
        opener.open(urljoin(base_url, html.unescape(action.group(1))), data)
    except urllib.error.HTTPError as redirect:
        location = redirect.headers.get("Location", "")
    else:
        raise RuntimeError(f"Login of {username} was not accepted")
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query).get("code")
    if not location.startswith(redirect_uri) or not code:
        raise RuntimeError(f"Login of {username} did not return an authorization code: {location or 'no redirect'}")
    token_request = urllib.request.Request(
        f"{endpoint}/token",
        urllib.parse.urlencode(
            {
                "grant_type": "authorization_code",
                "client_id": client_id,
                "code": code[0],
                "redirect_uri": redirect_uri,
                "code_verifier": verifier,
            }
        ).encode(),
    )
    with urllib.request.urlopen(token_request) as response:
        return json.load(response)["access_token"]


def multipart_parts(parts):
    """Body and Content-Type of a multipart request; parts are (field, filename, content, type)."""
    boundary = uuid.uuid4().hex
    body = b""
    for field, filename, content, content_type in parts:
        disposition = f'form-data; name="{field}"' + (f'; filename="{filename}"' if filename else "")
        body += (f"--{boundary}\r\nContent-Disposition: {disposition}\r\n"
                 f"Content-Type: {content_type}\r\n\r\n").encode() + content + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


def multipart(field, filename, content, content_type):
    return multipart_parts([(field, filename, content, content_type)])


class Api:
    def __init__(self, base_url, token):
        self.base_url = base_url
        self.token = token

    def call(self, method, path, body=None, headers=None, raw=None):
        """Returns (status, parsed JSON body or None)."""
        request_headers = dict(headers or {})
        if self.token:
            request_headers["Authorization"] = f"Bearer {self.token}"
        payload = raw
        if body is not None:
            payload = json.dumps(body).encode()
            request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            f"{self.base_url}/api/v1{path}", payload, request_headers, method=method
        )
        try:
            with urllib.request.urlopen(request) as response:
                status, text = response.status, response.read().decode()
        except urllib.error.HTTPError as error:
            status, text = error.code, error.read().decode()
        try:
            return status, json.loads(text)
        except ValueError:
            return status, None

    def ok(self, method, path, body=None, headers=None, raw=None):
        """Returns the `data` of a successful call, raises ApiError otherwise."""
        status, parsed = self.call(method, path, body, headers, raw)
        if status >= 300 or not parsed or not parsed.get("success"):
            raise ApiError(method, path, status, parsed)
        return parsed["data"]

    def refused(self, method, path, expected_status, body=None, headers=None, raw=None):
        """Returns the error body of a call that must fail with `expected_status`."""
        status, parsed = self.call(method, path, body, headers, raw)
        if status != expected_status or not parsed or parsed.get("success"):
            raise ApiError(method, path, status, parsed)
        return parsed


def sign_in(base_url, account):
    return Api(base_url, login(base_url, account.username, account.password, account.client))


def png_image(draw_with, size=(640, 480), color=(255, 255, 255)):
    """PNG bytes of a synthetic picture; draw_with(ImageDraw) paints on it."""
    from PIL import Image, ImageDraw

    image = Image.new("RGB", size, color)
    draw_with(ImageDraw.Draw(image))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


class Steps:
    """Runs named E2E steps: a failing step is reported and the run continues."""

    def __init__(self):
        self.results = []

    def __call__(self, name):
        def decorate(function):
            try:
                function()
                self.results.append((name, None))
                print(f"PASS  {name}")
            except Exception as error:  # noqa: BLE001 - every failure is reported, the run continues
                self.results.append((name, error))
                print(f"FAIL  {name}: {error}")
            return function

        return decorate

    def fail(self, name, error):
        self.results.append((name, error))
        print(f"FAIL  {name}")

    def summary(self):
        """Prints the totals and returns the process exit code."""
        failed = [name for name, error in self.results if error]
        print(f"\n{len(self.results) - len(failed)}/{len(self.results)} steps passed")
        return 1 if failed else 0


def expect(condition, message):
    if not condition:
        raise AssertionError(message)


def error_code(body):
    return body.get("errorCode") if body else None


def case_for(admin, case_type, entity_id):
    """Finds the pending workflow case that tracks an entity."""
    page = admin.ok("GET", f"/workflow-cases?caseType={case_type}&status=PENDING&size=100")
    for item in page["items"]:
        if item["entityId"] == entity_id:
            return item
    raise LookupError(f"No pending {case_type} case for entity {entity_id}")
