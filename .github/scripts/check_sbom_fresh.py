#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Check that a committed SBOM lists the packages the project resolves today.

    check_sbom_fresh.py sbom <committed.cdx.json> <generated.cdx.json>

`sbom` compares the packages of two CycloneDX files, the committed one and one generated just now
with the commands of sbom/README.md.
A package that is only on one side means the SBOM is stale: generate it again and commit it.
"""
import json
import sys
from pathlib import Path


def sbom_packages(path: str) -> set[str]:
    components = json.loads(Path(path).read_text(encoding="utf-8")).get("components", [])
    return {f"{c['group'] + '/' if c.get('group') else ''}{c['name']}@{c['version']}" for c in components}


def main(argv: list[str]) -> int:
    if len(argv) != 4 or argv[1] != "sbom":
        print(__doc__)
        return 2
    committed, current, label = sbom_packages(argv[2]), sbom_packages(argv[3]), argv[2]
    stale = sorted(committed - current)
    missing = sorted(current - committed)
    for package in missing:
        print(f"{label}: {package} is resolved but not in the SBOM")
    for package in stale:
        print(f"{label}: {package} is in the SBOM but no longer resolved")
    print(f"{label}: {len(committed)} packages in the SBOM, {len(current)} resolved, {len(missing) + len(stale)} difference(s)")
    return 1 if missing or stale else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
