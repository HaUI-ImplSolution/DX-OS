#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compares the hashes of the record history that a receiver kept with what the system holds now.

The receiver keeps one line per event it was sent: the eventId and the hash of the webhook message.
When the history is rewritten and the chain recomputed, the system still reads as intact to its own
check, but the hashes the receiver kept no longer match. Commands:

  save <file>     appends the (eventId, hash) of every event not yet in the file, read from the feed
  compare <file>  reads the feed over the range of the file and lists the events whose hash changed
                  and the events that are gone; the exit code is 1 when there is any

The file has one "eventId hash" per line, which a receiver can also write from its own webhook log.
Settings by environment: DXOS_BASE_URL (default http://localhost:9080) and either DXOS_WITNESS_TOKEN
(an access token of an ADMIN) or DXOS_WITNESS_CLIENT_ID and DXOS_WITNESS_CLIENT_SECRET (a client
credentials client of the realm whose service account holds ADMIN; DXOS_REALM_URL defaults to
<base>/realms/dxos).
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("DXOS_BASE_URL", "http://localhost:9080").rstrip("/")
PAGE = 500


def http(method, url, body=None, headers=None):
    data = body.encode() if isinstance(body, str) else None
    request = urllib.request.Request(url, data=data, method=method, headers=dict(headers or {}))
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            payload, status = response.read().decode(), response.status
    except urllib.error.HTTPError as error:
        payload, status = error.read().decode(), error.code
    try:
        return status, json.loads(payload) if payload else None
    except ValueError:
        return status, payload


def access_token():
    token = os.environ.get("DXOS_WITNESS_TOKEN")
    if token:
        return token
    client = os.environ.get("DXOS_WITNESS_CLIENT_ID")
    secret = os.environ.get("DXOS_WITNESS_CLIENT_SECRET")
    if not (client and secret):
        sys.exit("Set DXOS_WITNESS_TOKEN, or DXOS_WITNESS_CLIENT_ID and DXOS_WITNESS_CLIENT_SECRET.")
    realm = os.environ.get("DXOS_REALM_URL", f"{BASE}/realms/dxos").rstrip("/")
    status, body = http("POST", f"{realm}/protocol/openid-connect/token", urllib.parse.urlencode(
        {"grant_type": "client_credentials", "client_id": client, "client_secret": secret}),
        {"Content-Type": "application/x-www-form-urlencoded"})
    if status != 200:
        sys.exit(f"The token request answered {status}.")
    return body["access_token"]


def feed(token, after=0, upto=None):
    """The events after `after`, as {eventId: hash}, up to eventId `upto` when given."""
    found = {}
    while True:
        status, body = http("GET", f"{BASE}/api/v1/events?after={after}&size={PAGE}",
                            headers={"Authorization": f"Bearer {token}"})
        if status != 200:
            sys.exit(f"The events feed answered {status}.")
        data = body["data"]
        for item in data["items"]:
            found[int(item["eventId"])] = item["hash"]
        if not data["more"] or (upto is not None and int(data["next"]) >= upto):
            return found
        after = int(data["next"])


def read_saved(path):
    saved = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                parts = line.split()
                if len(parts) == 2:
                    saved[int(parts[0])] = parts[1]
    return saved


def compare(saved, current):
    """(matched, changed, gone): the kept hashes against the system's, each of the last two sorted."""
    matched, changed, gone = 0, [], []
    for event_id, kept in sorted(saved.items()):
        now = current.get(event_id)
        if now is None:
            gone.append(event_id)
        elif now == kept:
            matched += 1
        else:
            changed.append(event_id)
    return matched, changed, gone


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("command", choices=["save", "compare"])
    parser.add_argument("file")
    args = parser.parse_args()
    token = access_token()
    saved = read_saved(args.file)
    if args.command == "save":
        current = feed(token, after=max(saved) if saved else 0)
        new = {i: h for i, h in current.items() if i not in saved}
        with open(args.file, "a", encoding="utf-8") as handle:
            for event_id, hash_ in sorted(new.items()):
                handle.write(f"{event_id} {hash_}\n")
        print(f"{len(new)} events added, {len(saved) + len(new)} kept.")
        return
    if not saved:
        sys.exit("The file holds no events; run save first.")
    current = feed(token, after=min(saved) - 1, upto=max(saved))
    matched, changed, gone = compare(saved, current)
    print(f"{matched} match, {len(changed)} changed, {len(gone)} gone.")
    if changed:
        print(f"First changed: {changed[0]}.")
    if gone:
        print(f"First gone: {gone[0]}.")
    sys.exit(1 if changed or gone else 0)


if __name__ == "__main__":
    main()
