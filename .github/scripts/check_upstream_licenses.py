#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check that the upstream projects of the component catalog still have the license the catalog declares.

    GITHUB_TOKEN=... python3 .github/scripts/check_upstream_licenses.py

A project that changes its license, as Redis did in 7.4, keeps its image name, so the catalog check alone
would not notice a newer version under the new terms.
This compares the license GitHub reports for each upstream repository with the catalog entry.
A difference fails the check: read the license of the version in use and of the newer ones, then update the entry.
An answer GitHub cannot give (NOASSERTION, a repository without a license file) and a network error are only reported.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KINDS = ("images", "downloads")
# SPDX ids as GitHub reports them, mapped to the ids of the catalog.
SAME = {"GPL-2.0": "GPL-2.0-only", "GPL-3.0": "GPL-3.0-only", "AGPL-3.0": "AGPL-3.0-only", "LGPL-2.1": "LGPL-2.1-only"}


def upstream(name: str, entry: dict) -> str | None:
    """The GitHub project of an entry: the `upstream` key, else the project in `source`."""
    if entry.get("upstream"):
        return entry["upstream"]
    match = re.match(r"https://github\.com/([\w.\-]+/[\w.\-]+)", entry.get("source", ""))
    return match.group(1) if match else None


def reported_license(project: str) -> str | None:
    request = urllib.request.Request(f"https://api.github.com/repos/{project}", headers={"User-Agent": "dxos-license-check"})
    if os.environ.get("GITHUB_TOKEN"):
        request.add_header("Authorization", f"Bearer {os.environ['GITHUB_TOKEN']}")
    with urllib.request.urlopen(request, timeout=30) as response:
        license_ = json.load(response).get("license") or {}
    spdx = license_.get("spdx_id")
    return None if spdx in (None, "NOASSERTION") else SAME.get(spdx, spdx)


def main() -> int:
    catalog = json.loads((ROOT / ".github/component-catalog.json").read_text(encoding="utf-8"))
    failures = notes = checked = 0
    for kind in KINDS:
        for name, entry in catalog.get(kind, {}).items():
            project = upstream(name, entry)
            if project is None:
                print(f"note: {name} has no GitHub project to compare with")
                notes += 1
                continue
            try:
                found = reported_license(project)
            except (urllib.error.URLError, TimeoutError) as error:
                print(f"note: {project} could not be read ({error})")
                notes += 1
                continue
            if found is None:
                print(f"note: GitHub reports no license for {project}; the catalog says {entry['license']}")
                notes += 1
            elif found != entry["license"]:
                print(f"{name}: the catalog says {entry['license']}, {project} now says {found}")
                failures += 1
            else:
                checked += 1
    print(f"{checked} project(s) match the catalog, {notes} note(s), {failures} difference(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
