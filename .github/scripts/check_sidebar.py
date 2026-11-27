#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Check that every document of the docs site is in its sidebar.

    check_sidebar.py

Docusaurus publishes every file of website/docs/ but shows in the sidebar only the ids that website/sidebars.js lists,
so a document that nobody added there is reachable only by its address and opens without a sidebar.
Run it after the site is built, because the product documents of website/docs/tai-lieu/ are copied there by
website/scripts/sync-docs.mjs.

Exit code 0 when every document is listed, 1 otherwise.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "website" / "docs"
SIDEBARS = ROOT / "website" / "sidebars.js"


def main() -> int:
    listed = set(re.findall(r"'([a-z0-9][a-z0-9/-]*)'", SIDEBARS.read_text(encoding="utf-8")))
    ids = sorted(
        path.relative_to(DOCS).with_suffix("").as_posix()
        for path in DOCS.rglob("*")
        if path.is_file() and path.suffix in (".md", ".mdx")
    )
    if not any(i.startswith("tai-lieu/") for i in ids):
        print("check_sidebar: website/docs/tai-lieu is empty; build the site first (npm run build in website/)")
        return 1
    missing = [i for i in ids if i not in listed]
    for i in missing:
        print(f"website/sidebars.js: the document {i} is not listed, so it opens without a sidebar")
    print(f"check_sidebar: {len(ids)} document(s), {len(missing)} missing from the sidebar")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
