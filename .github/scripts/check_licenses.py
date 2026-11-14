# SPDX-License-Identifier: Apache-2.0
"""Check the licensing of the repository.

    python3 .github/scripts/check_licenses.py
    python3 .github/scripts/check_licenses.py sbom FILE...   # only the licenses of the SBOMs given

1. Every tracked source file starts with an SPDX license line that names Apache-2.0, unless REUSE.toml
   states the license of that file itself (an override annotation: third-party files and released migrations).
   `reuse lint` checks the rest of the tree.
2. Every package in sbom/*.cdx.json has a license that is allowed below or
   an entry in .github/dependency-exceptions.json that gives the reason.
3. No vendor/, third_party/ or node_modules/ directory is committed.
4. Every container image the repository builds from, copies from or runs (in a Dockerfile, a compose file, a
   `docker run` command of a workflow or a script, or a script variable such as DUCKDB="duckdb/duckdb:1.4.1"), every release downloaded with ADD
   and every tracked weight, archive or document file is declared
   in .github/component-catalog.json with an allowed license, and DEPENDENCIES.txt names each entry.
5. Every license that REUSE.toml gives to a file of the repository, and every text in LICENSES/, is an allowed one
   (or OFL-1.1, the license of the Noto Sans files), so no file of the project is under a license outside the OSI list.

Several license entries on one package are alternatives, as in an SPDX OR expression.
The exit status is 1 when anything fails.
"""
import json
import re
import subprocess
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

SOURCE_EXTENSIONS = {
    "java", "ts", "tsx", "js", "jsx", "mjs", "cjs", "sh", "py", "yml", "yaml",
    "css", "html", "sql", "xml", "conf", "toml", "example",
}
SOURCE_NAMES = {"Dockerfile"}
HEADER_LINES = 6
# REUSE-IgnoreStart
SPDX_LINE = re.compile(r"SPDX-License-Identifier: *([\w.\-+]+)")
# REUSE-IgnoreEnd

# Licenses approved by the OSI. Data under CC-BY-4.0 or CC0-1.0 is not on that list,
# so the few packages that carry it are named, with the reason, in .github/dependency-exceptions.json.
ALLOWED = {
    "Apache-2.0", "MIT", "MIT-0", "BSD-2-Clause", "BSD-3-Clause", "ISC", "0BSD", "Unlicense",
    "BlueOak-1.0.0", "Python-2.0", "CNRI-Python", "Zlib",
    # Weak copyleft, used as unmodified libraries.
    "MPL-2.0", "EPL-2.0", "LGPL-2.1-only", "LGPL-2.1-or-later",
    # GPL-2.0-only with the Classpath exception, the SPDX id that the Java SBOMs use for it.
    "GPL-2.0-with-classpath-exception",
}
# Images run as separate processes, unmodified and reached over the network.
ALLOWED_IMAGES = ALLOWED | {"PostgreSQL", "GPL-2.0-only"}
ARTIFACT_EXTENSIONS = {"onnx", "pt", "pth", "ckpt", "safetensors", "gguf", "pdf", "zip", "parquet", "csv", "xlsx"}
FROM_LINE = re.compile(r"^FROM\s+(?:--platform=\S+\s+)?(\S+)", re.MULTILINE)
IMAGE_LINE = re.compile(r"^\s*image:\s*[\"']?([^\s\"'#]+)", re.MULTILINE)
COPY_FROM_LINE = re.compile(r"^COPY\s+--from=(\S+)", re.MULTILINE)
DOWNLOAD_LINE = re.compile(r"^ADD\s+https://github\.com/([\w.\-]+/[\w.\-]+)/", re.MULTILINE)
SCRIPT_IMAGE = re.compile(r"\b((?:aquasec/trivy|restic/restic)):[\w.\-]+")
# An image reference with a tag, as in nginx/nginx:1.30 or ghcr.io/org/name:v1@sha256:<digest>.
IMAGE_REFERENCE = r"(?:[\w.\-]+(?::\d+)?/)*[\w.\-]+/[\w.\-]+:[\w.\-]+(?:@sha256:[0-9a-f]{64})?"
IMAGE_TOKEN = re.compile(rf"^{IMAGE_REFERENCE}$")
# A script variable that holds an image, as in DUCKDB="duckdb/duckdb:1.4.1@sha256:...".
VARIABLE_IMAGE = re.compile(rf'^\s*[A-Z][A-Z0-9_]*="({IMAGE_REFERENCE})"', re.MULTILINE)


def docker_run_images(text: str) -> list[str]:
    """Images named in a `docker run` command: its line, the next line (a YAML folded block continues there) and every line after a trailing backslash."""
    found: list[str] = []
    lines = text.split("\n")
    for index, line in enumerate(lines):
        if "docker run" not in line:
            continue
        command = line.split("docker run", 1)[1]
        last = index
        while last + 1 < len(lines) and (last == index or lines[last].rstrip().endswith("\\")):
            last += 1
            command += " " + lines[last]
        for token in command.replace("\\", " ").split():
            token = token.strip("\"'")
            if IMAGE_TOKEN.match(token):
                found.append(token)
    return found


def tracked_files() -> list[str]:
    return subprocess.check_output(["git", "-C", str(ROOT), "ls-files"]).decode().split("\n")


def glob_to_regex(pattern: str) -> re.Pattern:
    """A REUSE.toml path: * stays inside a directory, ** crosses directories."""
    out, index = "", 0
    while index < len(pattern):
        if pattern.startswith("**", index):
            out, index = out + ".*", index + 2
        elif pattern[index] == "*":
            out, index = out + "[^/]*", index + 1
        else:
            out, index = out + re.escape(pattern[index]), index + 1
    return re.compile(out + r"\Z")


def declared_in_reuse(files: list[str]) -> set[str]:
    """The files whose license REUSE.toml gives itself with an override annotation, so that they need no line of their own."""
    annotations = tomllib.loads((ROOT / "REUSE.toml").read_text(encoding="utf-8")).get("annotations", [])
    patterns = []
    for annotation in annotations:
        if annotation.get("precedence") == "override":
            path = annotation["path"]
            patterns += [path] if isinstance(path, str) else path
    expressions = [glob_to_regex(pattern) for pattern in patterns]
    return {rel for rel in files if any(expression.match(rel) for expression in expressions)}


def check_headers() -> list[str]:
    problems = []
    files = tracked_files()
    declared = declared_in_reuse(files)
    for rel in files:
        name = rel.rsplit("/", 1)[-1]
        if rel in declared or not (name in SOURCE_NAMES or rel.rsplit(".", 1)[-1] in SOURCE_EXTENSIONS) or not (ROOT / rel).is_file():
            continue
        wanted = "Apache-2.0"
        head = (ROOT / rel).read_text(encoding="utf-8", errors="replace").split("\n")[:HEADER_LINES]
        found = next((m.group(1) for m in map(SPDX_LINE.search, head) if m), None)
        if found != wanted:
            problems.append(f"{rel}: license header is {found or 'missing'}, expected {wanted}")
    return problems


# The licenses a file of the repository may carry: the allowed ones and the license of the Noto Sans files.
OWN_LICENSES = ALLOWED | {"OFL-1.1"}


def check_own_licenses() -> list[str]:
    problems = []
    annotations = tomllib.loads((ROOT / "REUSE.toml").read_text(encoding="utf-8")).get("annotations", [])
    for annotation in annotations:
        expression = annotation.get("SPDX-License-Identifier", "")
        if not expression_allowed(expression, OWN_LICENSES):
            problems.append(f"REUSE.toml: {annotation.get('path')} is under {expression}, which is not an allowed license")
    for text in sorted((ROOT / "LICENSES").glob("*.txt")):
        if text.stem not in OWN_LICENSES:
            problems.append(f"LICENSES/{text.name}: {text.stem} is not an allowed license")
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


def check_sboms(files: list[Path] | None = None) -> list[str]:
    """The licenses of the packages of the SBOMs given, or of the committed ones in sbom/."""
    exceptions = json.loads((ROOT / ".github/dependency-exceptions.json").read_text(encoding="utf-8"))
    problems = []
    for sbom in sorted(files if files is not None else (ROOT / "sbom").glob("*.cdx.json")):
        allowed = ALLOWED
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


def referenced_references() -> dict[str, list[tuple[str, str]]]:
    """Image name to the (file, reference) pairs that use it. Images built by this repository are skipped."""
    found: dict[str, list[tuple[str, str]]] = {}
    for rel in tracked_files():
        path = ROOT / rel
        if not path.is_file():
            continue
        name = path.name
        if name == "Dockerfile":
            stages = set(re.findall(r"^FROM\s+\S+\s+AS\s+(\S+)", path.read_text(encoding="utf-8"), re.MULTILINE | re.IGNORECASE))
            text = path.read_text(encoding="utf-8")
            references = [r for r in FROM_LINE.findall(text) + COPY_FROM_LINE.findall(text) if r not in stages]
        elif rel.startswith(("deploy/docker-compose", ".github/workflows/")) and name.endswith((".yml", ".yaml")):
            text = path.read_text(encoding="utf-8")
            references = [r for r in IMAGE_LINE.findall(text) if "${" not in r] + docker_run_images(text)
        elif rel.startswith("deploy/") and (name.endswith(".sh") or name == "dxos"):
            text = path.read_text(encoding="utf-8")
            references = ([m.group(0) for m in SCRIPT_IMAGE.finditer(text)] + VARIABLE_IMAGE.findall(text)
                          + docker_run_images(text))
        else:
            continue
        for reference in references:
            found.setdefault(image_name(reference), []).append((rel, reference))
    return found


def referenced_images() -> dict[str, str]:
    """Image name to the first file that uses it."""
    return {name: uses[0][0] for name, uses in referenced_references().items()}


def referenced_downloads() -> dict[str, str]:
    """GitHub project to the Dockerfile that downloads a release of it with ADD."""
    found: dict[str, str] = {}
    for rel in tracked_files():
        path = ROOT / rel
        if path.name == "Dockerfile" and path.is_file():
            for repo in DOWNLOAD_LINE.findall(path.read_text(encoding="utf-8")):
                found.setdefault(repo.lower(), rel)
    return found


def check_catalog() -> list[str]:
    catalog = json.loads((ROOT / ".github/component-catalog.json").read_text(encoding="utf-8"))
    copyright_text = (ROOT / "DEPENDENCIES.txt").read_text(encoding="utf-8")
    problems = []
    for kind, allowed in (("images", ALLOWED_IMAGES), ("downloads", ALLOWED)):
        for name, entry in catalog.get(kind, {}).items():
            if entry.get("license") not in allowed:
                problems.append(f"component-catalog {kind} {name}: {entry.get('license')} is not allowed")
            if not entry.get("use") or not entry.get("source"):
                problems.append(f"component-catalog {kind} {name}: use and source are required")
            tail = name.split("/")[-1].lower()
            if tail not in copyright_text.lower() and tail.replace("-", " ") not in copyright_text.lower():
                problems.append(f"DEPENDENCIES.txt does not name {name}")
    for name, rel in referenced_images().items():
        if name not in catalog.get("images", {}):
            problems.append(f"{rel}: image {name} is not in component-catalog.json")
    for name, rel in referenced_downloads().items():
        if name not in {key.lower() for key in catalog.get("downloads", {})}:
            problems.append(f"{rel}: download from {name} is not in component-catalog.json")
    declared = {entry["path"]: entry for entry in catalog.get("files", [])}
    for entry in declared.values():
        if entry.get("license") not in ALLOWED or not entry.get("note"):
            problems.append(f"component-catalog file {entry['path']}: license and note are required")
        if not (ROOT / entry["path"]).is_file():
            problems.append(f"component-catalog file {entry['path']} does not exist")
    for rel in tracked_files():
        if rel.rsplit(".", 1)[-1].lower() in ARTIFACT_EXTENSIONS and (ROOT / rel).is_file() and rel not in declared:
            problems.append(f"{rel}: weights, archives and documents must be declared in component-catalog.json")
    return problems


def check_bundled() -> list[str]:
    """Third-party code is declared as a dependency, never committed into the repository."""
    return [f"{rel}: bundled third-party code is not allowed" for rel in tracked_files()
            if re.search(r"(^|/)(vendor|third_party|node_modules)/", rel)]


def main() -> int:
    # `sbom FILE...` checks the licenses of SBOMs generated from the pull request, which can differ from
    # the committed ones until they are generated again.
    if sys.argv[1:2] == ["sbom"]:
        problems = check_sboms([Path(name) for name in sys.argv[2:]])
        for problem in problems:
            print(problem)
        print(f"{len(problems)} problem(s)")
        return 1 if problems else 0
    problems = check_headers() + check_own_licenses() + check_sboms() + check_catalog() + check_bundled()
    for problem in problems:
        print(problem)
    print(f"{len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
