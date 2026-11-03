# SPDX-License-Identifier: Apache-2.0
"""Check the licensing of the repository.

    python3 .github/scripts/check_licenses.py

1. Every tracked source file starts with an SPDX-License-Identifier line:
   AGPL-3.0-only under ai/, Apache-2.0 everywhere else.
2. Every package in sbom/*.cdx.json has a license that is allowed below or
   an entry in .github/license-exceptions.json that gives the reason.
3. Every container image the repository builds from or runs, every model named in the
   configuration of the AI service and every tracked weight, archive or document file is declared
   in .github/license-catalog.json with an allowed license, and COPYRIGHT.txt names each entry.

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
# Images run as separate processes, unmodified and reached over the network.
ALLOWED_IMAGES = ALLOWED | {"PostgreSQL", "GPL-2.0-only", "AGPL-3.0-only"}
ARTIFACT_EXTENSIONS = {"onnx", "pt", "pth", "ckpt", "safetensors", "gguf", "pdf", "zip", "parquet", "csv", "xlsx"}
FROM_LINE = re.compile(r"^FROM\s+(?:--platform=\S+\s+)?(\S+)", re.MULTILINE)
IMAGE_LINE = re.compile(r"^\s*image:\s*[\"']?([^\s\"'#]+)", re.MULTILINE)
SCRIPT_IMAGE = re.compile(r"\b((?:aquasec/trivy|restic/restic)):[\w.\-]+")
MODEL_DEFAULT = re.compile(r"\w*_model\s*:\s*str\s*=\s*\"([^\"]+)\"")


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


def image_name(reference: str) -> str:
    """The image without registry host, tag and digest."""
    name = reference.split("@")[0].rsplit(":", 1)[0] if ":" in reference.split("/")[-1] else reference.split("@")[0]
    parts = name.split("/")
    if len(parts) > 2 or (len(parts) == 2 and ("." in parts[0] or ":" in parts[0])):
        parts = parts[1:]
    return "/".join(parts)


def referenced_images() -> dict[str, str]:
    """Image name to the file that uses it. Images built by this repository are skipped."""
    found: dict[str, str] = {}
    for rel in tracked_files():
        path = ROOT / rel
        if not path.is_file():
            continue
        name = path.name
        if name == "Dockerfile":
            stages = set(re.findall(r"^FROM\s+\S+\s+AS\s+(\S+)", path.read_text(encoding="utf-8"), re.MULTILINE | re.IGNORECASE))
            references = [r for r in FROM_LINE.findall(path.read_text(encoding="utf-8")) if r not in stages]
        elif rel.startswith(("deploy/docker-compose", ".github/workflows/")) and name.endswith((".yml", ".yaml")):
            references = [r for r in IMAGE_LINE.findall(path.read_text(encoding="utf-8")) if "${" not in r]
        elif rel.startswith("deploy/") and name.endswith(".sh"):
            references = [m.group(0) for m in SCRIPT_IMAGE.finditer(path.read_text(encoding="utf-8"))]
        else:
            continue
        for reference in references:
            found.setdefault(image_name(reference), rel)
    return found


def check_catalog() -> list[str]:
    catalog = json.loads((ROOT / ".github/license-catalog.json").read_text(encoding="utf-8"))
    copyright_text = (ROOT / "COPYRIGHT.txt").read_text(encoding="utf-8")
    problems = []
    for kind, allowed in (("images", ALLOWED_IMAGES), ("models", ALLOWED)):
        for name, entry in catalog.get(kind, {}).items():
            if entry.get("license") not in allowed:
                problems.append(f"license-catalog {kind} {name}: {entry.get('license')} is not allowed")
            if not entry.get("use") or not entry.get("source"):
                problems.append(f"license-catalog {kind} {name}: use and source are required")
            tail = name.split("/")[-1].lower()
            if tail not in copyright_text.lower() and tail.replace("-", " ") not in copyright_text.lower():
                problems.append(f"COPYRIGHT.txt does not name {name}")
    for name, rel in referenced_images().items():
        if name not in catalog.get("images", {}):
            problems.append(f"{rel}: image {name} is not in license-catalog.json")
    config = (ROOT / "ai/app/core/config.py")
    if config.is_file():
        for model in MODEL_DEFAULT.findall(config.read_text(encoding="utf-8")):
            if model not in catalog.get("models", {}):
                problems.append(f"ai/app/core/config.py: model {model} is not in license-catalog.json")
    declared = {entry["path"]: entry for entry in catalog.get("files", [])}
    for entry in declared.values():
        if entry.get("license") not in ALLOWED or not entry.get("note"):
            problems.append(f"license-catalog file {entry['path']}: license and note are required")
        if not (ROOT / entry["path"]).is_file():
            problems.append(f"license-catalog file {entry['path']} does not exist")
    for rel in tracked_files():
        if rel.rsplit(".", 1)[-1].lower() in ARTIFACT_EXTENSIONS and (ROOT / rel).is_file() and rel not in declared:
            problems.append(f"{rel}: weights, archives and documents must be declared in license-catalog.json")
    return problems


def main() -> int:
    problems = check_headers() + check_sboms() + check_catalog()
    for problem in problems:
        print(problem)
    print(f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
