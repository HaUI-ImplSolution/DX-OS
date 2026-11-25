# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""The list of every package the project uses, in docs/DEPENDENCY_INVENTORY.md.

    python3 .github/scripts/check_license_inventory.py generate [--maven]
    python3 .github/scripts/check_license_inventory.py check [--maven]

The rows come from the lockfiles of the npm projects (development packages too), the Maven build
(every scope, the plugins and their libraries), the pinned GitHub Actions, the images of
.github/licenses/component-catalog.json and .github/licenses/license-inventory.json (tools, fonts, models).
`generate` writes them as tables; `check` fails when the file is not what
`generate` would write, or when a license is not one the OSI approves and no entry of
.github/licenses/dependency-exceptions.json gives the reason.
Without --maven the Maven rows of the file are only checked against the policy: reading them
needs a JDK and the local Maven repository, which the backend job has.
"""
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPENDENCIES = ROOT / "DEPENDENCIES.txt"
INVENTORY = ROOT / "docs/DEPENDENCY_INVENTORY.md"
NPM_PROJECTS = ["frontend", "website", "assistant", "e2e/web"]
OWN_GROUP = "HaUI.ImplSolution"
SCOPE_ORDER = ["compile", "runtime", "provided", "test", "bom", "plugin"]

Row = tuple[str, str, str, str]  # name, version, license, scope
TABLE_HEAD = "| Tên | Phiên bản | Giấy phép | Phạm vi | Ngoại lệ |\n|---|---|---|---|---|"


def load(path: str) -> dict:
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


OSI = load(".github/licenses/osi-approved.json")
OSI_IDS = set(OSI["ids"])
ALIASES = OSI["aliases"]
EXCEPTIONS = load(".github/licenses/dependency-exceptions.json")
EXTRA = load(".github/licenses/license-inventory.json")

# What a POM or a package.json says, in the SPDX name it means.
LICENSE_NAMES = [
    (re.compile(r"^(the )?apache( software)?( public)?( license)?\W*(version |v\.? ?)?2(\.0)?$|^apache[- ]2(\.0)?$"), "Apache-2.0"),
    (re.compile(r"^(the )?mit( license)?$|^bouncy castle licen[cs]e$"), "MIT"),
    (re.compile(r"^eclipse public license\W*((v\.? ?|version )?2(\.0)?)$|^epl[- ]2(\.0)?$"), "EPL-2.0"),
    (re.compile(r"^eclipse public license\W*(v\.? ?|version )1(\.0)?$|^epl[- ]1(\.0)?$"), "EPL-1.0"),
    (re.compile(r"^eclipse distribution license\W*(v\.? ?|version )1(\.0)?$|^edl[- ]1(\.0)?$"), "EDL-1.0"),
    (re.compile(r"^mozilla public license,? (version )?2(\.0)?$|^mpl 2\.0$"), "MPL-2.0"),
    (re.compile(r"^(the )?bsd 2-clause( license)?$"), "BSD-2-Clause"),
    (re.compile(r"^(the )?bsd 3-clause( new)?( license)?$|^the bsd license$|^bsd license 2\.0$"), "BSD-3-Clause"),
    (re.compile(r"^gpl2 w/ cpe$|^gnu general public license,? version 2,? with the (gnu )?classpath exception$|^gpl v2 with the classpath exception$"), "GPL-2.0-only WITH Classpath-exception-2.0"),
    (re.compile(r"^the gnu general public license, v2 with universal foss exception, v1\.0$"), "GPL-2.0-only WITH Universal-FOSS-exception-1.0"),
    (re.compile(r"^universal permissive license,? version 1\.0$"), "UPL-1.0"),
    (re.compile(r"^lgpl,? version 2\.1$|^gnu lesser general public license,? version 2\.1$"), "LGPL-2.1-only"),
    (re.compile(r"^gnu lesser general public license$"), "LGPL-3.0-or-later"),
    (re.compile(r"^cddl \+ gplv2 with classpath exception$"), "CDDL-1.1 OR GPL-2.0-only WITH Classpath-exception-2.0"),
    (re.compile(r"^public domain, per creative commons cc0$"), "CC0-1.0"),
]


def spdx_name(text: str) -> str:
    """A license name as the SPDX id it means; an SPDX expression stays as it is, a name nobody maps stays as written."""
    text = text.strip()
    for pattern, spdx in LICENSE_NAMES:
        if pattern.match(text.lower()):
            return spdx
    if re.search(r"\s(OR|AND|WITH)\s", text):
        return text.replace("(", "").replace(")", "")
    return text


def osi_ok(expression: str) -> bool:
    """True when a license expression has an alternative the OSI approves: A OR B needs one side, A AND B needs both."""
    for alternative in re.split(r"\s+OR\s+", expression.replace("(", "").replace(")", "")):
        parts = re.split(r"\s+AND\s+", alternative)
        if all(ALIASES.get(part.strip(), part.strip()) in OSI_IDS for part in parts):
            return True
    return False


def exception_for(name: str, version: str) -> str | None:
    return EXCEPTIONS.get(f"{name}@{version}") or EXCEPTIONS.get(name)


def npm_rows(project: str) -> list[Row]:
    packages = load(f"{project}/package-lock.json").get("packages", {})
    rows = set()
    for path, meta in packages.items():
        if not path or meta.get("link"):
            continue
        name = path.rsplit("node_modules/", 1)[-1]
        license_ = meta.get("license")
        if isinstance(license_, dict):
            license_ = license_.get("type")
        license_ = spdx_name(license_) if isinstance(license_, str) else "(not declared)"
        scope = "dev" if meta.get("dev") or meta.get("devOptional") else "prod"
        rows.add((name, meta.get("version", ""), license_, scope))
    return sorted(rows)


def pom_licenses(repo: Path, group: str, artifact: str, version: str, depth: int = 0) -> list[str]:
    """The license names of a POM, or of its closest parent that gives some."""
    pom = repo / group.replace(".", "/") / artifact / version / f"{artifact}-{version}.pom"
    if not pom.exists() or depth > 8:
        return []
    try:
        root = ET.parse(pom).getroot()
    except ET.ParseError:
        # A POM that is not well-formed XML (an undeclared prefix, for instance) is read as text.
        text = pom.read_text(encoding="utf-8", errors="replace")
        names = [name.strip() for name in re.findall(r"<license>.*?<name>([^<]+)</name>", text, re.DOTALL)]
        if names:
            return names
        parent = re.search(r"<parent>.*?<groupId>([^<]+)</groupId>.*?<artifactId>([^<]+)</artifactId>.*?<version>([^<]+)</version>", text, re.DOTALL)
        return pom_licenses(repo, *[item.strip() for item in parent.groups()], depth + 1) if parent else []
    for element in root.iter():
        element.tag = re.sub(r"\{[^}]*\}", "", element.tag)
    names = [(item.findtext("name") or "").strip() for item in root.findall("licenses/license")]
    names = [name for name in names if name]
    if names:
        return names
    parent = root.find("parent")
    if parent is None:
        return []
    return pom_licenses(repo, *[(parent.findtext(tag) or "").strip() for tag in ("groupId", "artifactId", "version")], depth + 1)


def maven_license(repo: Path, group: str, artifact: str, version: str) -> str:
    names = pom_licenses(repo, group, artifact, version)
    if not names:
        return "(not declared)"
    return " OR ".join(dict.fromkeys(spdx_name(name) for name in names))


def parse_maven(dependency_list: str, plugins: str) -> dict[tuple[str, str, str], set[str]]:
    """group:artifact:version -> scopes, from `dependency:list` and `dependency:resolve-plugins` output."""
    found: dict[tuple[str, str, str], set[str]] = {}
    for line in dependency_list.splitlines():
        words = line.split()
        parts = words[0].split(":") if words else []
        if len(parts) < 5 or not re.match(r"^[\w.\-]+$", parts[0]):
            continue
        group, artifact, version, scope = parts[0], parts[1], parts[-2], parts[-1]
        if scope in SCOPE_ORDER and group != OWN_GROUP:
            found.setdefault((group, artifact, version), set()).add(scope)
    for line in plugins.splitlines():
        match = re.match(r"^\[INFO\]\s{3,}([\w.\-]+):([\w.\-]+):[\w\-]+(?::[\w\-]+)?:([\w.\-]+)\s*$", line)
        if match and match.group(1) != OWN_GROUP:
            found.setdefault((match.group(1), match.group(2), match.group(3)), set()).add("plugin")
    return found


def backend_boms() -> list[tuple[str, str, str]]:
    """The parent and the imported BOMs of the backend POM, which fix versions and are not in a dependency list."""
    root = ET.parse(ROOT / "backend/pom.xml").getroot()
    for element in root.iter():
        element.tag = re.sub(r"\{[^}]*\}", "", element.tag)
    properties_element = root.find("properties")
    properties = {child.tag: (child.text or "").strip() for child in (properties_element if properties_element is not None else [])}

    def resolved(text: str | None) -> str:
        return re.sub(r"\$\{([^}]+)\}", lambda m: properties.get(m.group(1), m.group(0)), (text or "").strip())

    boms = []
    parent = root.find("parent")
    if parent is not None:
        boms.append(tuple(resolved(parent.findtext(tag)) for tag in ("groupId", "artifactId", "version")))
    for dependency in root.findall("dependencyManagement/dependencies/dependency"):
        if resolved(dependency.findtext("scope")) == "import":
            boms.append(tuple(resolved(dependency.findtext(tag)) for tag in ("groupId", "artifactId", "version")))
    return boms


def run_maven() -> tuple[str, str]:
    backend = ROOT / "backend"
    listing = Path(os.environ.get("TMPDIR", "/tmp")) / "license-inventory-deps.txt"
    listing.unlink(missing_ok=True)
    subprocess.check_call(
        ["./mvnw", "-q", "-B", "test-compile", "dependency:list", "-DincludeScope=test", f"-DoutputFile={listing}", "-DappendOutput=true"],
        cwd=backend,
    )
    plugins = subprocess.run(["./mvnw", "-B", "dependency:resolve-plugins"], cwd=backend, capture_output=True, text=True, check=True).stdout
    return listing.read_text(encoding="utf-8"), plugins


def maven_rows() -> list[Row]:
    repo = Path(os.environ.get("MAVEN_REPO", Path.home() / ".m2/repository"))
    dependency_list, plugins = run_maven()
    found = parse_maven(dependency_list, plugins)
    for group, artifact, version in backend_boms():
        found.setdefault((group, artifact, version), set()).add("bom")
    rows = []
    for (group, artifact, version), scopes in found.items():
        scope = ",".join(sorted(scopes, key=SCOPE_ORDER.index))
        rows.append((f"{group}:{artifact}", version, maven_license(repo, group, artifact, version), scope))
    return sorted(rows)


def pgschema_rows() -> list[Row]:
    """The Go modules linked into the pgschema binary of the backend image, as backend/licenses/THIRD-PARTY-LICENSES-pgschema.txt lists them."""
    path = ROOT / "backend/licenses/THIRD-PARTY-LICENSES-pgschema.txt"
    rows = set()
    listing = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("Modules and their licenses"):
            listing = True
        elif listing and not line.strip():
            break
        elif listing:
            match = re.match(r"^  (.+) (\S+): (.+)$", line)
            if match:
                rows.add((match.group(1), match.group(2), spdx_name(match.group(3)), "image"))
    return sorted(rows)


def keycloak_rows() -> list[Row]:
    """The libraries of the Keycloak distribution in the sso image, as sso/licenses/THIRD-PARTY-LICENSES-keycloak.txt lists them."""
    path = ROOT / "sso/licenses/THIRD-PARTY-LICENSES-keycloak.txt"
    rows = set()
    for line in path.read_text(encoding="utf-8").split("Each text below")[0].splitlines():
        match = re.match(r"^  (\S+) (\S+): (.+)$", line)
        if match:
            rows.add((match.group(1), match.group(2), spdx_name(match.group(3)), "image"))
    return sorted(rows)


def action_rows() -> list[Row]:
    rows = set()
    for workflow in sorted((ROOT / ".github/workflows").glob("*.yml")):
        for match in re.finditer(r"uses:\s*([\w./\-]+)@([0-9a-f]{40})\s*#\s*(\S+)", workflow.read_text(encoding="utf-8")):
            name, _sha, tag = match.groups()
            rows.add((name, tag, EXTRA["actions"].get(name, "(not declared)"), "ci"))
    return sorted(rows)


def image_rows() -> list[Row]:
    catalog = load(".github/licenses/component-catalog.json")
    texts = []
    for path in subprocess.check_output(["git", "-C", str(ROOT), "ls-files", "--cached", "--others", "--exclude-standard"]).decode().split("\n"):
        if path and not path.startswith(".github/scripts/") and re.search(r"(docker-compose[\w.\-]*\.yml|Dockerfile|\.yml|\.sh|dxos|\.mjs|\.py)$", path) and (ROOT / path).is_file():
            texts.append((ROOT / path).read_text(encoding="utf-8", errors="replace"))
    text = "\n".join(texts)
    rows = set()
    for name, entry in catalog["images"].items():
        found = re.findall(rf"(?<![\w.\-]){re.escape(name)}:(v?[0-9][\w.\-]*)", text)
        versions = sorted({v for v in found if not re.fullmatch(r"\d{4,5}", v)}) or ["(see the Dockerfile)"]
        for version in versions:
            rows.add((name, version, entry["license"], "image"))
    return sorted(rows)


def extra_rows(kind: str) -> list[Row]:
    return sorted((item["name"], item["version"], item["license"], item["scope"]) for item in EXTRA[kind])


def sections(maven: bool, existing: dict[str, list[Row]]) -> dict[str, list[Row]]:
    found = {f"npm: {project}": npm_rows(project) for project in NPM_PROJECTS}
    found["Maven: backend"] = maven_rows() if maven else existing.get("Maven: backend", [])
    found["GitHub Actions"] = action_rows()
    found["Go: pgschema trong image backend"] = pgschema_rows()
    found["Keycloak trong image sso"] = keycloak_rows()
    found["ClamAV: phần nhúng trong image clamav"] = extra_rows("clamav")
    found["Image"] = image_rows()
    found["Công cụ CI và dựng"] = extra_rows("tools")
    found["Công cụ của máy chạy"] = extra_rows("system")
    found["Thư viện Python"] = extra_rows("python")
    found["Phông và model"] = extra_rows("assets")
    return found


def cell(text: str) -> str:
    return text.replace("|", "/").replace("\n", " ")


INTRO = """# Danh mục đầy đủ từng gói

Tệp này do `.github/scripts/check_license_inventory.py generate` sinh, không sửa tay.
Nó liệt kê mọi gói dự án dùng ở bất kỳ bước nào: chạy thật, dựng, kiểm thử, CI, kể cả gói dev, gói test, plugin Maven và gói kéo theo.
Mỗi dòng có tên, phiên bản chính xác đang khóa, giấy phép SPDX của đúng phiên bản đó và phạm vi (`prod`, `dev`, `compile`, `runtime`, `test`, `plugin`, `bom`, `ci`, `build`, `image`...).
Giấy phép đọc từ lockfile của npm và từ POM của Maven; gói cho chọn nhiều giấy phép ghi bằng `OR` và chỉ cần một phương án được OSI công nhận.
Danh sách OSI nằm ở `.github/licenses/osi-approved.json`, đối chiếu với danh sách SPDX và opensource.org.
Cột Ngoại lệ nêu lý do khi giấy phép chưa nằm trong danh sách OSI, lấy từ `.github/licenses/dependency-exceptions.json`.
Vai trò và trang chủ của thành phần cấp cao ở [`DEPENDENCIES.txt`](../DEPENDENCIES.txt); ngoại lệ gộp theo nhóm ở phần "Giấy phép ngoài danh sách OSI" của tệp đó.
`check` ở mỗi pull request báo đỏ khi thiếu gói, thừa gói hoặc có giấy phép ngoài OSI mà chưa có lý do.
"""


def render(found: dict[str, list[Row]]) -> str:
    lines = [INTRO, "| Nhóm | Số gói |", "|---|---|"]
    lines += [f"| {title} | {len(rows)} |" for title, rows in found.items()]
    lines.append("")
    for title, rows in found.items():
        lines += [f"## {title}", "", TABLE_HEAD]
        for name, version, license_, scope in rows:
            lines.append(f"| `{cell(name)}` | {cell(version)} | {cell(license_)} | {scope} | {cell(exception_for(name, version) or '')} |")
        lines.append("")
    return "\n".join(lines)


def parse(text: str) -> dict[str, list[Row]]:
    found: dict[str, list[Row]] = {}
    current = None
    for line in text.splitlines():
        if line.startswith("## "):
            current = line[3:].strip()
            found[current] = []
        elif current and line.startswith("| `"):
            cells = [part.strip() for part in line.strip()[1:-1].split("|")]
            found[current].append((cells[0].strip("`"), cells[1], cells[2], cells[3]))
    return found


def policy_problems(found: dict[str, list[Row]]) -> list[str]:
    problems = []
    for title, rows in found.items():
        for name, version, license_, _scope in rows:
            if osi_ok(license_):
                continue
            if not exception_for(name, version):
                problems.append(f"{title}: {name} {version} has license {license_}, which the OSI does not approve, and no entry of dependency-exceptions.json says why")
    return problems


def main(argv: list[str]) -> int:
    if not argv or argv[0] not in ("generate", "check"):
        print(__doc__)
        return 2
    maven = "--maven" in argv
    existing = parse(INVENTORY.read_text(encoding="utf-8")) if INVENTORY.exists() else {}
    found = sections(maven, existing)
    if argv[0] == "generate":
        INVENTORY.write_text(render(found), encoding="utf-8")
        print(f"{sum(len(rows) for rows in found.values())} rows written to docs/DEPENDENCY_INVENTORY.md")
        return 0
    problems = policy_problems(found)
    if not INVENTORY.exists():
        problems.append("docs/DEPENDENCY_INVENTORY.md is missing: run python3 .github/scripts/check_license_inventory.py generate")
    else:
        for title, rows in found.items():
            if title == "Maven: backend" and not maven:
                continue
            have = set(existing.get(title, []))
            want = set(rows)
            problems += [f"{title}: {name} {version} ({license_}, {scope}) is used but not in docs/DEPENDENCY_INVENTORY.md as such" for name, version, license_, scope in sorted(want - have)]
            problems += [f"{title}: {name} {version} ({license_}, {scope}) is in docs/DEPENDENCY_INVENTORY.md but not used" for name, version, license_, scope in sorted(have - want)]
        if set(existing) - set(found):
            problems.append(f"docs/DEPENDENCY_INVENTORY.md has sections the script does not know: {sorted(set(existing) - set(found))}")
    if "docs/DEPENDENCY_INVENTORY.md" not in DEPENDENCIES.read_text(encoding="utf-8"):
        problems.append("DEPENDENCIES.txt does not point to docs/DEPENDENCY_INVENTORY.md")
    for problem in problems:
        print(problem)
    print(f"check_license_inventory: {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
