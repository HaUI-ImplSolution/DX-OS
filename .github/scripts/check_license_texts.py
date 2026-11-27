# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""The license files inside the packages that go into the product.

    python3 .github/scripts/check_license_texts.py

The other license checks read what a package declares; this one reads the text it ships. The packages are the npm
production packages of frontend and assistant (package-lock.json) and the Maven compile and runtime libraries of the
backend (docs/DEPENDENCY_INVENTORY.md), each downloaded at its locked version, or read from the local Maven repository.
A package fails when a license file restricts use (terms of use, non-commercial, source-available licenses), or when
none of its license files holds the text of an open license. A package without a license file passes: its declared
license is checked by check_license_inventory.py. An entry name@version of
.github/licenses/dependency-exceptions.json accepts a package with its reason. It needs the network.
"""
import base64
import hashlib
import io
import re
import sys
import tarfile
import time
import urllib.error
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import check_license_inventory as inventory

ROOT = inventory.ROOT
NPM_PROJECTS = ["frontend", "assistant"]
MAVEN_SCOPES = {"compile", "runtime"}
MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
M2 = Path.home() / ".m2/repository"

LICENSE_FILE = re.compile(r"(?i)^(licen[cs]e|copying|notice|unlicense|eula|copyright|patents|third.?party)|licen[cs]e(\.\w+)?$")
NOT_A_TEXT = re.compile(r"(?i)\.(js|ts|mjs|cjs|map|json|class|java|png|py|go)$")
# Wording that restricts use. The GPL and MPL texts mention non-commercial copies, so the patterns are whole phrases.
RESTRICTIONS = re.compile(
    r"these terms of use|terms of use copyright|business source license|server side public license|commons clause|"
    r"elastic license|confluent community license|redis source available|sustainable use license|"
    r"polyform (noncommercial|small business|shield|strict)|functional source license|hippocratic license|"
    r"shall be used for good, not evil|for non-?commercial (use|purposes) only|may not be used for commercial|"
    r"attribution-noncommercial|for evaluation purposes only|oracle (free use terms|binary code license)|unrar license|"
    r"proprietary and confidential|embargo|sanction(s|ed) (list|countr)")
OPEN_LICENSES = re.compile(
    r"apache license|licenses/license-2\.0|permission is hereby granted, free of charge|\bmit\b|"
    r"permission to use, copy, modify|redistribution and use .{0,40}source and binary|\bbsd\b|eclipse (public|distribution) license|"
    r"mozilla public license|gnu (lesser |library |affero )?general public|common development and distribution|"
    r"free and unencumbered software|\bcc0\b|creative commons|public domain|blue ?oak|universal permissive license|"
    r"python software foundation|sil open font license|bouncy castle|unicode.{0,20}license|w3c")


def normalize(text: str) -> str:
    """Lower case, one space between words, without the comment marks that start the lines of some license files."""
    return re.sub(r"\s+", " ", re.sub(r"(?m)^[ \t]*(?:[#*>!;]+|//)[ \t]?", "", text)).lower()


def problem_of(texts: dict[str, str]) -> str | None:
    """Why the license files of one package are not accepted, or None."""
    normalized = {name: normalize(text) for name, text in texts.items()}
    for name, text in sorted(normalized.items()):
        found = RESTRICTIONS.search(text)
        if found:
            return f"{name} restricts use: \"{found.group(0)}\""
    if normalized and not any(OPEN_LICENSES.search(text) for text in normalized.values()):
        return f"no license file holds the text of an open license: {', '.join(sorted(normalized))}"
    return None


def is_license_file(path: str) -> bool:
    name = path.rsplit("/", 1)[-1]
    return bool(LICENSE_FILE.search(name)) and not NOT_A_TEXT.search(name)


def fetch(url: str) -> bytes:
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "dxos-license-texts"})
            with urllib.request.urlopen(request, timeout=120) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code == 404 or attempt == 3:
                raise
        except OSError:
            if attempt == 3:
                raise
        time.sleep(5 * (attempt + 1))
    raise AssertionError("unreachable")


def npm_packages() -> dict[str, tuple[str, str]]:
    """name@version -> (tarball address, integrity) of every production package."""
    found = {}
    for project in NPM_PROJECTS:
        for path, meta in inventory.load(f"{project}/package-lock.json").get("packages", {}).items():
            if path and not meta.get("link") and not meta.get("dev") and not meta.get("devOptional"):
                name = meta.get("name") or path.rsplit("node_modules/", 1)[-1]
                found[f"{name}@{meta['version']}"] = (meta["resolved"], meta["integrity"])
    return found


def maven_libraries(document: str) -> set[str]:
    """group:artifact@version of the compile and runtime rows of the Maven tables."""
    found, maven = set(), False
    for line in document.split("\n"):
        if line.startswith("## "):
            maven = line.startswith("## Maven")
        row = re.match(r"\| `([^`]+)` \| ([^|]+) \| [^|]* \| ([^|]+) \|", line)
        if maven and row and MAVEN_SCOPES & set(row.group(3).strip().split(",")):
            found.add(f"{row.group(1)}@{row.group(2).strip()}")
    return found


def npm_texts(address: str, integrity: str) -> dict[str, str]:
    data = fetch(address)
    algorithm, digest = integrity.split(" ")[0].split("-", 1)
    if base64.b64encode(hashlib.new(algorithm, data).digest()).decode() != digest:
        raise ValueError("the download does not match the integrity of the lockfile")
    texts = {}
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        for member in archive:
            path = member.name.split("/", 1)[-1]
            if member.isfile() and "node_modules/" not in path and is_license_file(path):
                texts[path] = archive.extractfile(member).read().decode(errors="replace")
    return texts


def jar_texts(coordinate: str) -> dict[str, str]:
    name, version = coordinate.split("@")
    group, artifact = name.split(":")[:2]
    path = f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.jar"
    local = M2 / path
    try:
        data = local.read_bytes() if local.exists() else fetch(f"{MAVEN_CENTRAL}/{path}")
    except urllib.error.HTTPError as error:
        # An artifact that is only a POM, such as a starter or a BOM, has no jar and so no text to read.
        if error.code == 404 and b"<packaging>pom</packaging>" in fetch(f"{MAVEN_CENTRAL}/{path[:-4]}.pom"):
            return {}
        raise
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return {entry: archive.read(entry).decode(errors="replace") for entry in archive.namelist() if is_license_file(entry)}


def main() -> int:
    packages = {key: (npm_texts, source) for key, source in npm_packages().items()}
    libraries = maven_libraries(inventory.INVENTORY.read_text(encoding="utf-8"))
    if not packages or not libraries:
        print("no npm package or no Maven library was found; the lockfiles or the inventory have changed their form")
        return 1
    packages.update({key: (jar_texts, (key,)) for key in libraries})

    def check(key: str) -> str | None:
        if key in inventory.EXCEPTIONS:
            return None
        read, source = packages[key]
        try:
            found = problem_of(read(*source))
        except Exception as error:  # noqa: BLE001 - a package that cannot be read is not a package that passed
            found = f"cannot be read: {error}"
        return f"{key}: {found}" if found else None

    with ThreadPoolExecutor(8) as pool:
        problems = [problem for problem in pool.map(check, sorted(packages)) if problem]
    for problem in problems:
        print(problem)
    print(f"check_license_texts: {len(packages)} packages, {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
