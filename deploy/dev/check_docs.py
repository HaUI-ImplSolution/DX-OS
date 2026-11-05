# SPDX-License-Identifier: Apache-2.0
"""Checks that every endpoint the documents name is in the OpenAPI document of the running backend.

Run on any stack that is up:
    python3 deploy/dev/check_docs.py
"""
import os
import re
import sys

import check_api as rt
import kc

DOCS = ["README.md", "README_EN.md", "ARCHITECTURE.md", "deploy/OPERATIONS.md"]
METHODS = ("GET", "POST", "PUT", "PATCH", "DELETE")
# A method (optional) and an absolute API path inside backticks; a path that starts without /api/v1 is a shorthand.
MENTION = re.compile(r"`(?:(GET|POST|PUT|PATCH|DELETE) )?(/api/v1/[A-Za-z0-9_{}\-./]*)(?:\?[^`]*)?`")


def shape(path):
    return re.sub(r"\{[^}/]+\}", "{}", path).rstrip("/")


def documented():
    found = {}
    for name in DOCS:
        with open(os.path.join(kc.REPO, name), encoding="utf-8") as handle:
            text = handle.read()
        for method, path in MENTION.findall(text):
            found.setdefault((method, shape(path)), set()).add(name)
    return found


def main():
    status, spec = kc.http("GET", f"{kc.BASE}/v3/api-docs")
    rt.check("DOC-01 the OpenAPI document is served", status == 200 and isinstance(spec, dict) and spec.get("paths"), str(status))
    served = {}
    for path, operations in (spec or {}).get("paths", {}).items():
        for method in operations:
            if method.upper() in METHODS:
                served.setdefault(shape(path), set()).add(method.upper())
    mentions = documented()
    rt.check("DOC-02 the documents name endpoints", len(mentions) > 10, str(len(mentions)))

    missing = []
    for (method, path), files in sorted(mentions.items()):
        methods = served.get(path)
        if methods is None or (method and method not in methods):
            missing.append(f"{method or '*'} {path} ({', '.join(sorted(files))})")
    rt.check("DOC-03 every endpoint a document names is served with that method", not missing, "; ".join(missing))

    failed = [case for case, ok in rt.results if not ok]
    print(f"\n{len(rt.results) - len(failed)}/{len(rt.results)} passed", failed)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
