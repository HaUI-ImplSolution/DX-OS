#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Creates one record per row of a CSV file through the data API, as a registered machine client.

Run by `dxos import`; the client secret comes from the environment variable DXOS_IMPORT_SECRET.
Every row goes through the permissions, rules and process of the entity like a record created by
hand, and carries an Idempotency-Key made from its content, so sending the same file again creates
nothing twice while the keys are kept (a day).
"""
import argparse
import csv
import hashlib
import io
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation

MAX_ROWS = 10_000
TOKEN_SECONDS = 240
RETRIES_ON_LIMIT = 30
TRUE = {"true", "1", "yes", "y", "co", "có", "x"}
FALSE = {"false", "0", "no", "n", "khong", "không"}


def request(method, url, token=None, body=None, headers=None, form=None):
    """Returns (status, headers, parsed body); waits and retries while the rate limit answers 429."""
    data = None
    sent = dict(headers or {})
    if token:
        sent["Authorization"] = "Bearer " + token
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        sent["Content-Type"] = "application/x-www-form-urlencoded"
    elif body is not None:
        data = json.dumps(body).encode()
        sent["Content-Type"] = "application/json"
    for attempt in range(RETRIES_ON_LIMIT + 1):
        req = urllib.request.Request(url, data=data, method=method, headers=sent)
        try:
            with urllib.request.urlopen(req, timeout=60) as response:
                status, answer, raw = response.status, response.headers, response.read()
        except urllib.error.HTTPError as error:
            status, answer, raw = error.code, error.headers, error.read()
        except urllib.error.URLError as error:
            sys.exit(f"dxos import: cannot reach {url}: {error.reason}")
        if status == 429 and attempt < RETRIES_ON_LIMIT:
            wait = int(answer.get("Retry-After") or answer.get("X-RateLimit-Reset") or 5)
            print(f"  rate limit, waiting {wait}s", file=sys.stderr)
            time.sleep(max(wait, 1))
            continue
        try:
            parsed = json.loads(raw) if raw else None
        except ValueError:
            parsed = raw.decode("utf-8", "replace")
        return status, answer, parsed
    raise AssertionError("unreachable")


class Session:
    def __init__(self, base, client, secret):
        self.base, self.client, self.secret, self.token, self.at = base, client, secret, None, 0.0

    def login(self):
        status, _, body = request(
            "POST",
            f"{self.base}/realms/dxos/protocol/openid-connect/token",
            form={
                "grant_type": "client_credentials",
                "client_id": f"dxos-app-{self.client}",
                "client_secret": self.secret,
            },
        )
        if status != 200 or not isinstance(body, dict) or "access_token" not in body:
            sys.exit(
                f"dxos import: Keycloak refused dxos-app-{self.client} (HTTP {status}); "
                f"dxos client test {self.client} says what is wrong, and dxos client rotate {self.client} "
                "gives a new secret"
            )
        self.token, self.at = body["access_token"], time.time()

    def call(self, method, path, body=None, headers=None):
        if self.token is None or time.time() - self.at > TOKEN_SECONDS:
            self.login()
        result = request(method, f"{self.base}/api/v1{path}", self.token, body, headers)
        if result[0] == 401:
            self.login()
            result = request(method, f"{self.base}/api/v1{path}", self.token, body, headers)
        return result


def read_table(path):
    with open(path, "rb") as handle:
        text = handle.read().decode("utf-8-sig")
    first = text.split("\n", 1)[0]
    delimiter = max(",;\t", key=first.count)
    reader = csv.reader(io.StringIO(text, newline=""), delimiter=delimiter)
    header = next(reader, None)
    if not header:
        sys.exit(f"dxos import: {path} is empty")
    rows = []
    for row in reader:
        if any(cell.strip() for cell in row):
            rows.append((reader.line_num, row))
    return [cell.strip() for cell in header], rows


def number(text):
    plain = text.replace(" ", "").replace(" ", "")
    if "," in plain and "." in plain:
        decimal = "," if plain.rfind(",") > plain.rfind(".") else "."
        plain = plain.replace("." if decimal == "," else ",", "").replace(decimal, ".")
    elif plain.count(".") > 1:
        plain = plain.replace(".", "")
    elif plain.count(",") > 1:
        plain = plain.replace(",", "")
    elif "," in plain:
        plain = plain.replace(",", ".")
    try:
        value = Decimal(plain)
    except InvalidOperation:
        raise ValueError("must be a number")
    if not value.is_finite():
        raise ValueError("must be a number")
    if value == value.to_integral_value():
        return int(value)
    return float(value) if Decimal(repr(float(value))) == value else format(value, "f")


def day(text):
    for pattern in ("%Y-%m-%d", "%d/%m/%Y"):
        try:
            return datetime.strptime(text, pattern).date()
        except ValueError:
            pass
    raise ValueError("must be a date as YYYY-MM-DD or DD/MM/YYYY")


def moment(text, offset):
    stamp = text.strip().replace("Z", "+00:00")
    parsed = None
    for pattern in ("%d/%m/%Y %H:%M:%S", "%d/%m/%Y %H:%M"):
        try:
            parsed = datetime.strptime(stamp, pattern)
        except ValueError:
            pass
    if parsed is None:
        try:
            parsed = datetime.fromisoformat(stamp.replace(" ", "T", 1))
        except ValueError:
            raise ValueError("must be a date and time, as YYYY-MM-DD HH:MM or DD/MM/YYYY HH:MM")
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=offset)
    return parsed.isoformat(timespec="seconds")


def convert(field, text, offset):
    kind = field["type"]
    if field.get("multiple"):
        items = [item.strip() for item in text.split("|") if item.strip()]
        for item in items:
            if item not in field["options"]:
                raise ValueError(f"{item!r} is not one of {field['options']}")
        return items
    if kind in ("text", "user"):
        return text
    if kind in ("choice", "unit"):
        if text not in field["options"]:
            raise ValueError(f"must be one of {field['options']}")
        return text
    if kind == "number":
        return number(text)
    if kind == "boolean":
        word = text.lower()
        if word in TRUE:
            return True
        if word in FALSE:
            return False
        raise ValueError("must be true or false")
    if kind == "date":
        return day(text).isoformat()
    if kind == "datetime":
        return moment(text, offset)
    if kind == "reference":
        if not text.isdigit():
            raise ValueError("must be the id of a record")
        return int(text)
    raise ValueError("cannot be imported")


def columns_of(header, fields):
    by_name = {f["name"].casefold(): f for f in fields}
    by_label = {f["label"].casefold(): f for f in fields}
    mapped, unknown, unsupported = [], [], []
    for title in header:
        field = by_name.get(title.casefold()) or by_label.get(title.casefold())
        if not title:
            mapped.append(None)
        elif field is None:
            unknown.append(title)
            mapped.append(None)
        elif field["type"] in ("file", "code"):
            unsupported.append(title)
            mapped.append(None)
        else:
            mapped.append(field)
    return mapped, unknown, unsupported


def key_of(model, entity, body, seen):
    digest = hashlib.sha1(
        json.dumps([model, entity, body], sort_keys=True, ensure_ascii=False).encode()
    ).hexdigest()[:24]
    seen[digest] = seen.get(digest, 0) + 1
    return f"import-{digest}-{seen[digest]}"


def problem(payload):
    if isinstance(payload, dict):
        data = payload.get("data")
        if isinstance(data, dict) and data:
            return "; ".join(f"{name}: {why}" for name, why in data.items())
        text = payload.get("message") or "rejected"
        code = payload.get("errorCode")
        return f"{text} ({code})" if code else text
    return str(payload)


def main():
    parser = argparse.ArgumentParser(prog="dxos import")
    parser.add_argument("model")
    parser.add_argument("entity")
    parser.add_argument("file")
    parser.add_argument("--client", required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--offset", default="+07:00")
    args = parser.parse_args()
    secret = os.environ.get("DXOS_IMPORT_SECRET", "")
    if not secret:
        sys.exit("dxos import: no client secret given")
    try:
        sign, hours, minutes = args.offset[0], *args.offset[1:].split(":")
        offset = timezone(timedelta(hours=int(hours), minutes=int(minutes)) * (-1 if sign == "-" else 1))
    except (ValueError, IndexError):
        sys.exit("dxos import: --offset is +HH:MM or -HH:MM")

    header, rows = read_table(args.file)
    if len(rows) > MAX_ROWS:
        sys.exit(f"dxos import: {len(rows)} rows; at most {MAX_ROWS} per file, split the file")
    session = Session(args.url.rstrip("/"), args.client, secret)
    status, _, answer = session.call("GET", f"/meta/models/{args.model}/entities/{args.entity}")
    if status != 200:
        sys.exit(f"dxos import: the entity {args.model}/{args.entity} is not reachable by dxos-app-{args.client} (HTTP {status}): {problem(answer)}")
    entity = answer["data"]
    if not entity["canWrite"]:
        sys.exit(f"dxos import: no role of dxos-app-{args.client} may create records of {args.entity}")
    fields = entity["fields"]
    mapped, unknown, unsupported = columns_of(header, fields)
    if unknown or unsupported:
        sys.exit(
            "dxos import: "
            + (f"unknown columns {unknown}; " if unknown else "")
            + (f"columns that cannot be imported (a file or a code) {unsupported}; " if unsupported else "")
            + "use the name or the label of a field as the heading"
        )
    present = {f["name"] for f in mapped if f}
    missing = [f["name"] for f in fields if f["required"] and f["type"] not in ("file", "code") and f["name"] not in present]
    if missing:
        sys.exit(f"dxos import: the file has no column for the required fields {missing}")

    seen, created, replayed, rejected, refused = {}, 0, 0, [], 0
    for line, row in rows:
        body, errors = {}, {}
        for field, cell in zip(mapped, row):
            text = cell.strip()
            if field is None or text == "":
                continue
            try:
                body[field["name"]] = convert(field, text, offset)
            except ValueError as why:
                errors[field["name"]] = str(why)
        for field in fields:
            if field["required"] and field["type"] not in ("file", "code") and field["name"] not in body and field["name"] not in errors:
                errors[field["name"]] = "is required"
        if errors:
            rejected.append((line, "; ".join(f"{n}: {w}" for n, w in errors.items())))
            continue
        if args.dry_run:
            created += 1
            continue
        status, answer_headers, payload = session.call(
            "POST", f"/data/{args.model}/{args.entity}", body,
            {"Idempotency-Key": key_of(args.model, args.entity, body, seen)},
        )
        if status in (200, 201):
            refused = 0
            if answer_headers.get("Idempotent-Replayed") == "true":
                replayed += 1
            else:
                created += 1
        else:
            rejected.append((line, f"HTTP {status}: {problem(payload)}"))
            refused = refused + 1 if status in (401, 403) else 0
            if refused >= 3:
                sys.exit(f"dxos import: stopped at line {line}, the client is refused (HTTP {status}): {problem(payload)}")

    for line, why in rejected[:50]:
        print(f"line {line}: {why}")
    if len(rejected) > 50:
        print(f"... and {len(rejected) - 50} more rejected rows")
    total = len(rows)
    if args.dry_run:
        print(f"Checked {total} rows: {created} look fine, {len(rejected)} rejected; nothing was created.")
    else:
        print(f"{total} rows: {created} created, {replayed} already imported, {len(rejected)} rejected.")
    return 1 if rejected else 0


if __name__ == "__main__":
    sys.exit(main())
