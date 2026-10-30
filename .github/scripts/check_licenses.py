# SPDX-License-Identifier: Apache-2.0
"""Check the licensing of the repository.

    python3 .github/scripts/check_licenses.py

1. Every tracked source file starts with an SPDX-License-Identifier line:
   AGPL-3.0-only under ai/, Apache-2.0 everywhere else.
2. Every package in sbom/*.cdx.json has a license that is allowed below or
   an entry in .github/license-exceptions.json that gives the reason.

Several license entries on one package are alternatives, as in an SPDX OR expression.
The exit status is 1 when anything fails.
"""
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

SOURCE_EXTENSIONS = {"java", "ts", "tsx", "js", "jsx", "mjs", "cjs", "sh", "py", "yml", "yaml"}
HEADER_LINES = 6
SPDX_LINE = re.compile(r"SPDX-License-Identifier: *([\w.\-+]+)")

ALLOWED = {
    "Apache-2.0", "MIT", "MIT-0", "MIT-CMU", "BSD-2-Clause", "BSD-3-Clause", "ISC", "0BSD", "Unlicense",
    "CC0-1.0", "CC-BY-4.0", "BlueOak-1.0.0", "Python-2.0", "PSF-2.0", "CNRI-Python", "Zlib",
    # Weak copyleft, used as unmodified libraries.
    "MPL-2.0", "EPL-2.0", "LGPL-2.1-only", "LGPL-2.1-or-later", "GPL-2.0-with-classpath-exception",
}
# The AI service is AGPL-3.0 itself, so a library under the LGPL v3 can sit beside it.
ALLOWED_BY_SBOM = {"ai.cdx.json": {"LGPL-3.0-only", "LGPL-3.0-or-later"}}


def tracked_files() -> list[str]:
    return subprocess.check_output(["git", "-C", str(ROOT), "ls-files"]).decode().split("\n")


def check_headers() -> list[str]:
    problems = []
    for rel in tracked_files():
        if rel.rsplit(".", 1)[-1] not in SOURCE_EXTENSIONS or not (ROOT / rel).is_file():
            continue
        wanted = "AGPL-3.0-only" if rel.startswith("ai/") else "Apache-2.0"
        head = (ROOT / rel).read_text(encoding="utf-8", errors="replace").split("\n")[:HEADER_LINES]
        found = next((m.group(1) for m in map(SPDX_LINE.search, head) if m), None)
        if found != wanted:
            problems.append(f"{rel}: license header is {found or 'missing'}, expected {wanted}")
    return problems


def expression_allowed(expression: str, allowed: set[str]) -> bool:
    """AND needs every side allowed, OR needs one; WITH is matched as the plain license."""
    tokens = re.findall(r"\(|\)|[^\s()]+", expression)
    position = 0

    def parse_or() -> bool:
        nonlocal position
        result = parse_and()
        while position < len(tokens) and tokens[position] == "OR":
            position += 1
            result = parse_and() or result
        return result

    def parse_and() -> bool:
        nonlocal position
        result = parse_atom()
        while position < len(tokens) and tokens[position] == "AND":
            position += 1
            result = parse_atom() and result
        return result

    def parse_atom() -> bool:
        nonlocal position
        if tokens[position] == "(":
            position += 1
            result = parse_or()
            position += 1
            return result
        license_id = tokens[position]
        position += 1
        if position < len(tokens) and tokens[position] == "WITH":
            position += 2
        return license_id in allowed

    return parse_or()


def check_sboms() -> list[str]:
    exceptions = json.loads((ROOT / ".github/license-exceptions.json").read_text(encoding="utf-8"))
    problems = []
    for sbom in sorted((ROOT / "sbom").glob("*.cdx.json")):
        allowed = ALLOWED | ALLOWED_BY_SBOM.get(sbom.name, set())
        for component in json.loads(sbom.read_text(encoding="utf-8")).get("components", []):
            label = f"{component.get('group', '')}:{component['name']}".strip(":")
            if label in exceptions or f"{label}@{component.get('version')}" in exceptions:
                continue
            alternatives = []
            for entry in component.get("licenses", []):
                alternatives.append(entry.get("expression") or entry["license"].get("id") or entry["license"].get("name", ""))
            if not alternatives:
                problems.append(f"{sbom.name}: {label} has no license")
            elif not any(expression_allowed(a, allowed) for a in alternatives):
                problems.append(f"{sbom.name}: {label} is {' / '.join(alternatives)}, not in the allowed list")
    return problems


def main() -> int:
    problems = check_headers() + check_sboms()
    for problem in problems:
        print(problem)
    print(f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
