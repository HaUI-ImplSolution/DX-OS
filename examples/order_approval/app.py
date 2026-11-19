# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""A minimal application of DX-OS: the requester files an order, the approver approves it.

It needs only the Python standard library and these environment variables:
DXOS_BASE_URL, DXOS_REQUESTER_CLIENT_ID, DXOS_REQUESTER_CLIENT_SECRET,
DXOS_APPROVER_CLIENT_ID and DXOS_APPROVER_CLIENT_SECRET.
"""
import json
import os
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.environ["DXOS_BASE_URL"].rstrip("/")
ORDERS = f"{BASE}/api/v1/data/order_approval/order_request"


def call(method, url, headers=None, body=None, form=False):
    data = None
    if body is not None:
        data = urllib.parse.urlencode(body).encode() if form else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    request.add_header("Content-Type", "application/x-www-form-urlencoded" if form else "application/json")
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise SystemExit(f"{method} {url} failed: HTTP {error.code} {error.read().decode()}")


def token(role):
    """A token of the client of one role; it lives about five minutes."""
    answer = call("POST", f"{BASE}/realms/dxos/protocol/openid-connect/token", form=True, body={
        "grant_type": "client_credentials",
        "client_id": os.environ[f"DXOS_{role}_CLIENT_ID"],
        "client_secret": os.environ[f"DXOS_{role}_CLIENT_SECRET"],
    })
    return {"Authorization": f"Bearer {answer['access_token']}"}


requester, approver = token("REQUESTER"), token("APPROVER")

created = call("POST", ORDERS, {**requester, "Idempotency-Key": str(uuid.uuid4())},
               {"title": "Mua 10 màn hình", "amount": 50000000})["data"]
print(f"filed order {created['id']}, status {created['status']}")

approved = call("POST", f"{ORDERS}/{created['id']}/actions/approve", approver, {})["data"]
print(f"approved order {approved['id']}, status {approved['status']}")
