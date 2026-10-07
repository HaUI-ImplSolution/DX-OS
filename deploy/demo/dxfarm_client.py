# SPDX-License-Identifier: Apache-2.0
"""Small DX-Farm API client shared by the demo seed and the E2E run (standard library + Pillow)."""
import base64
import hashlib
import html
import http.cookiejar
import io
import json
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request
import uuid
from urllib.parse import urljoin

DEFAULT_BASE_URL = "http://localhost:9080"
REALM = "dxfarm"
WEB_CLIENT = ("dxfarm-frontend", "http://localhost:9080/callback")
MOBILE_CLIENT = ("dxfarm-mobile", "dxfarm://callback")

ACCOUNTS = {
    "admin": ("admin1", "admin123", WEB_CLIENT),
    "engineer": ("engineer1", "engineer123", WEB_CLIENT),
    "farmer": ("farmer1", "farmer123", MOBILE_CLIENT),
}


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
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), _NoRedirect)
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


def multipart(field, filename, content, content_type):
    boundary = uuid.uuid4().hex
    head = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; "
        f"filename=\"{filename}\"\r\nContent-Type: {content_type}\r\n\r\n"
    ).encode()
    body = head + content + f"\r\n--{boundary}--\r\n".encode()
    return body, f"multipart/form-data; boundary={boundary}"


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

    def upload_report(self, season_id, image, key=None, filename="leaf"):
        png = image.startswith(b"\x89PNG")
        content, content_type = multipart(
            "image", f"{filename}.{'png' if png else 'jpg'}", image, "image/png" if png else "image/jpeg"
        )
        headers = {"Content-Type": content_type, "Idempotency-Key": key or str(uuid.uuid4())}
        return self.ok("POST", f"/crop-seasons/{season_id}/disease-reports", headers=headers, raw=content)


def sign_in(base_url, role):
    username, password, client = ACCOUNTS[role]
    return Api(base_url, login(base_url, username, password, client))


def leaf_photo(variant=0):
    """Synthetic leaf-like PNG that passes the image-quality check (no model weights needed)."""
    from PIL import Image, ImageDraw

    image = Image.new("RGB", (640, 480), (95 + variant % 5 * 3, 125, 65))
    draw = ImageDraw.Draw(image)
    for x in range(-40 - variant * 7, 680, 80):
        draw.polygon([(x, 480), (x + 24, 0), (x + 66, 480)], fill=(35, 105, 38))
        draw.line([(x + 25, 0), (x + 32, 480)], fill=(145, 180, 80), width=3)
        for y in range(20, 480, 14):
            draw.line([(x + 31, y), (x + 54, y + 10)], fill=(90, 150, 55), width=2)
            draw.ellipse((x + 30, y + 3, x + 39, y + 11), fill=(165, 105, 55))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def case_for(admin, case_type, entity_id):
    """Finds the pending workflow case that tracks an entity."""
    page = admin.ok("GET", f"/workflow-cases?caseType={case_type}&status=PENDING&size=100")
    for item in page["items"]:
        if item["entityId"] == entity_id:
            return item
    raise LookupError(f"No pending {case_type} case for entity {entity_id}")
