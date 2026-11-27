# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""The packages inside the third-party images the stack runs, in docs/IMAGE_PACKAGES.md.

    python3 .github/scripts/check_image_packages.py generate [--all]
    python3 .github/scripts/check_image_packages.py check [--full]

`generate` pulls each image at the tag and digest the repository pins, lets Trivy list its packages with
the licenses their distribution records, and writes one table per image. It needs Docker.
It reads only the images that have no table yet; --all reads every image again.
`check` fails when an image the stack runs has no table; with --full it also reads the images again and
fails when a table differs, which only a change of an image or of Trivy causes.
A license that the OSI does not list is shown in the last column and never hidden.
"""
import json
import re
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request
from pathlib import Path

import check_license_inventory as inventory

ROOT = inventory.ROOT
DOC = ROOT / "docs/IMAGE_PACKAGES.md"
TRIVY = "aquasec/trivy:0.58.1"
# The images that run in the stack, in a script of the product or in a build of its images; the CI tools are in DEPENDENCY_INVENTORY.md.
IMAGES = [
    "alpine", "apache/apisix", "chrislusf/seaweedfs", "clamav/clamav", "duckdb/duckdb", "eclipse-mosquitto", "eclipse-temurin",
    "nginx", "node", "pgplex/pgschema", "postgres", "prom/prometheus", "python", "restic/restic",
]
INTRO = """# Gói bên trong các image bên thứ ba

Tệp này do `.github/scripts/check_image_packages.py generate` sinh, không sửa tay.
Mỗi bảng liệt kê gói của một image mà stack chạy, ở đúng tag và digest repository ghim, kèm giấy phép mà bản phân phối của gói ghi lại.
Danh sách do Trivy đọc từ cơ sở dữ liệu gói của image; một gói có nhiều tệp thì cột giấy phép gộp giấy phép của các tệp đó.
Cột Ngoài OSI nêu các giấy phép chưa nằm trong `.github/licenses/osi-approved.json`; chúng không bị ẩn.
Image bên thứ ba chạy trong stack được kéo nguyên bản; image dự án phát hành chứa lớp nền của `eclipse-temurin`, `nginx` và `node` cùng hệ tệp của `clamav/clamav`, nên các phần đó được phân phối lại.
Các gói này giữ giấy phép do nhà phân phối hệ điều hành của image ghi.
Mô-đun Go biên dịch vào tệp chạy và gói npm không ghi giấy phép trong image; giấy phép của chúng lấy từ deps.dev theo đúng phiên bản, và `(not recorded)` nghĩa là deps.dev không ghi giấy phép nào.
Image `clamav/clamav` là nền của image `clamav` của dự án, chỉ xóa libclamunrar, nên bảng gói của nó vẫn đúng.
Giấy phép của từng thành phần cấp cao và của từng gói thư viện của dự án ở [`DEPENDENCY_INVENTORY.md`](./DEPENDENCY_INVENTORY.md).
"""


def image_refs() -> dict[str, str]:
    """name:tag -> the reference with its digest when the repository pins one."""
    texts = []
    for path in subprocess.check_output(["git", "-C", str(ROOT), "ls-files", "--cached", "--others", "--exclude-standard"]).decode().split("\n"):
        if path and not path.startswith((".github/scripts/", "docs/")) and re.search(r"(docker-compose[\w.\-]*\.yml|Dockerfile|\.yml|\.sh|dxos)$", path) and (ROOT / path).is_file():
            texts.append((ROOT / path).read_text(encoding="utf-8", errors="replace"))
    text = "\n".join(texts)
    refs: dict[str, str] = {}
    for name in IMAGES:
        for match in re.finditer(rf"(?<![\w.\-])({re.escape(name)}:(v?[0-9][\w.\-]*)(@sha256:[0-9a-f]{{64}})?)", text):
            full, tag, digest = match.group(1), match.group(2), match.group(3)
            if re.fullmatch(r"\d{4,5}", tag):
                continue
            key = f"{name}:{tag}"
            if digest or key not in refs:
                refs[key] = full
    return dict(sorted(refs.items()))


_dependency_licenses: dict[tuple[str, str, str], str] = {}


def dependency_license(system: str, name: str, version: str) -> str:
    """The licenses deps.dev records for a Go module or an npm package at one version; empty when it records none."""
    key = (system, name, version)
    if key not in _dependency_licenses:
        url = f"https://api.deps.dev/v3/systems/{system}/packages/{urllib.parse.quote(name, safe='')}/versions/{urllib.parse.quote(version, safe='')}"
        found = ""
        for attempt in range(3):
            try:
                request = urllib.request.Request(url, headers={"User-Agent": "dxos-license-inventory"})
                with urllib.request.urlopen(request, timeout=60) as response:
                    found = ", ".join(json.loads(response.read()).get("licenses", []))
                break
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    break
                time.sleep(2 * (attempt + 1))
            except OSError:
                time.sleep(2 * (attempt + 1))
        _dependency_licenses[key] = found
    return _dependency_licenses[key]


def scan(ref: str) -> list[tuple[str, str, str]]:
    subprocess.run(["docker", "pull", "-q", ref], check=True, capture_output=True)
    with tempfile.TemporaryDirectory() as out:
        subprocess.run(
            ["docker", "run", "--rm", "-v", "/var/run/docker.sock:/var/run/docker.sock", "-v", f"{out}:/out", TRIVY,
             "image", "--scanners", "license", "--format", "cyclonedx", "--output", "/out/sbom.json", ref],
            check=True, capture_output=True,
        )
        data = json.loads((Path(out) / "sbom.json").read_text(encoding="utf-8"))
    rows = set()
    for component in data.get("components", []):
        if component.get("type") != "library":
            continue
        names = []
        for entry in component.get("licenses", []):
            item = entry.get("license") or {}
            names.append(item.get("id") or item.get("name") or entry.get("expression") or "")
        text = ", ".join(sorted({n for n in names if n}))
        purl = re.match(r"pkg:(golang|npm)/(.+?)@([^?#]+)", component.get("purl", ""))
        if not text and purl:
            system = "go" if purl.group(1) == "golang" else "npm"
            name, version = component["name"], component.get("version", "")
            text = dependency_license(system, name, version)
        rows.add((component["name"], component.get("version", ""), text or "(not recorded)"))
    return sorted(rows)


def outside_osi(licenses: str) -> str:
    tokens = [token.strip() for token in licenses.split(",") if token.strip()]
    return ", ".join(token for token in tokens if token != "(not recorded)" and not inventory.osi_ok(token))


def render(tables: dict[str, list[tuple[str, str, str]]]) -> str:
    lines = [INTRO, "| Image | Gói | Gói có giấy phép ngoài OSI |", "|---|---|---|"]
    for ref, rows in tables.items():
        lines.append(f"| `{ref}` | {len(rows)} | {sum(1 for row in rows if outside_osi(row[2]))} |")
    lines.append("")
    for ref, rows in tables.items():
        lines += [f"## {ref}", "", "| Gói | Phiên bản | Giấy phép | Ngoài OSI |", "|---|---|---|---|"]
        for name, version, licenses in rows:
            lines.append(f"| `{inventory.cell(name)}` | {inventory.cell(version)} | {inventory.cell(licenses)} | {inventory.cell(outside_osi(licenses))} |")
        lines.append("")
    return "\n".join(lines)


def parse(text: str) -> dict[str, list[tuple[str, str, str]]]:
    tables: dict[str, list[tuple[str, str, str]]] = {}
    current = None
    for line in text.splitlines():
        if line.startswith("## "):
            current = line[3:].strip()
            tables[current] = []
        elif current and line.startswith("| `"):
            cells = [part.strip() for part in line.strip()[1:-1].split("|")]
            tables[current].append((cells[0].strip("`"), cells[1], cells[2]))
    return tables


def main(argv: list[str]) -> int:
    if not argv or argv[0] not in ("generate", "check"):
        print(__doc__)
        return 2
    refs = image_refs()
    if argv[0] == "generate":
        kept = parse(DOC.read_text(encoding="utf-8")) if DOC.exists() and "--all" not in argv else {}
        tables = {key: kept[key] if key in kept else scan(ref) for key, ref in refs.items()}
        DOC.write_text(render(tables), encoding="utf-8")
        print(f"{sum(len(rows) for rows in tables.values())} packages of {len(tables)} images written to docs/IMAGE_PACKAGES.md")
        return 0
    if not DOC.exists():
        print("docs/IMAGE_PACKAGES.md is missing: run python3 .github/scripts/check_image_packages.py generate")
        return 1
    have = parse(DOC.read_text(encoding="utf-8"))
    problems = [f"{key} has no table in docs/IMAGE_PACKAGES.md" for key in refs if key not in have]
    problems += [f"docs/IMAGE_PACKAGES.md has a table for {key}, which no file of the repository uses" for key in have if key not in refs]
    if "--full" in argv and not problems:
        for key, ref in refs.items():
            if scan(ref) != have[key]:
                problems.append(f"{key}: the packages of the image are not what docs/IMAGE_PACKAGES.md lists")
    for problem in problems:
        print(problem)
    print(f"check_image_packages: {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
