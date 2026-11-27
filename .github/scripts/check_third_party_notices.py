# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""The license and notice texts that the Java images ship, in backend/licenses/.

    python3 .github/scripts/check_third_party_notices.py generate
    python3 .github/scripts/check_third_party_notices.py check

For the backend and the monitor, the libraries of the runnable jar (compile and runtime scope) are read from the
local Maven repository and the license and notice files each jar carries are copied, once per distinct text with the
list of packages that share it. A package whose jar carries none is listed with the license its POM declares.
The same folder holds the notices of the pgschema binary that the backend image copies from the upstream image: its Go
modules are read from the build information of the binary and their license files from the Go module proxy.
The same folder of the sso image holds the notices of the Keycloak distribution that its Dockerfile unpacks (351 jars, each with the
license its POM declares), and the folder of the clamav image the license of ClamAV and of the third-party parts built into it.
The Dockerfile copies the files to /licenses. `check` fails when a Java file is not what `generate` writes (it needs a JDK)
or when the pgschema file is for another image than the one the Dockerfile copies from; `generate` needs Docker and network access.
"""
import hashlib
import io
import os
import re
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

import check_image_packages as images
import check_license_inventory as inventory

ROOT = inventory.ROOT
MODULES = {
    "dx-engine": ("the dxos-backend image", ROOT / "backend/licenses/THIRD-PARTY-LICENSES-backend.txt"),
    "dx-monitor": ("the dxos-monitor image", ROOT / "backend/licenses/THIRD-PARTY-LICENSES-monitor.txt"),
    "dx-model": ("the dxos-lint.jar release file", ROOT / "backend/licenses/THIRD-PARTY-LICENSES-lint.txt"),
}
# The libraries a jar carries when it is not all of the runtime closure (the shade plugin of dx-model takes only SnakeYAML).
ONLY = {"dx-model": {"org.yaml:snakeyaml"}}
# Fonts that a jar embeds besides its libraries.
FONTS = {"dx-engine": "Noto Sans Regular and Bold, Copyright 2022 The Noto Project Authors (https://github.com/notofonts/latin-greek-cyrillic), SIL Open Font License 1.1:"}
LEGAL = re.compile(r"^(META-INF/)?(licen[cs]es?|notice|copying)([\-_.][^/]*)?$|^META-INF/licen[cs]es?/.+", re.IGNORECASE)
MAX_BYTES = 200_000
PGSCHEMA = ROOT / "backend/licenses/THIRD-PARTY-LICENSES-pgschema.txt"
KEYCLOAK = ROOT / "sso/licenses/THIRD-PARTY-LICENSES-keycloak.txt"
CLAMAV = ROOT / "clamav/licenses/THIRD-PARTY-LICENSES-clamav.txt"
PGSCHEMA_IMAGE = re.compile(r"pgplex/pgschema:([\w.\-]+)@(sha256:[0-9a-f]{64})")


def closure(module: str) -> list[tuple[str, str, str]]:
    listing = Path(os.environ.get("TMPDIR", "/tmp")) / f"third-party-{module}.txt"
    listing.unlink(missing_ok=True)
    subprocess.check_call(
        ["./mvnw", "-q", "-B", "test-compile", "dependency:list", "-pl", module, "-am", "-DincludeScope=runtime", f"-DoutputFile={listing}", "-DappendOutput=true"],
        cwd=ROOT / "backend",
    )
    found = inventory.parse_maven(listing.read_text(encoding="utf-8"), "")
    return sorted(key for key in found if module not in ONLY or f"{key[0]}:{key[1]}" in ONLY[module])


def jar_path(repo: Path, group: str, artifact: str, version: str) -> Path | None:
    folder = repo / group.replace(".", "/") / artifact / version
    plain = folder / f"{artifact}-{version}.jar"
    if plain.exists():
        return plain
    return next(iter(sorted(folder.glob("*.jar"))), None)


def legal_files(jar: Path) -> list[tuple[str, str]]:
    files = []
    with zipfile.ZipFile(jar) as archive:
        for info in sorted(archive.infolist(), key=lambda item: item.filename):
            if info.is_dir() or info.file_size > MAX_BYTES or not LEGAL.match(info.filename):
                continue
            text = archive.read(info).decode("utf-8", errors="replace").replace("\r\n", "\n").strip()
            if text:
                files.append((info.filename, text))
    return files


def grouped(entries: list[tuple[str, list[tuple[str, str]]]]) -> tuple[list[str], list[str]]:
    """The text blocks of the files that packages carry, one per distinct text, and the packages that carry none."""
    groups: dict[tuple[str, str], list[str]] = {}
    texts: dict[tuple[str, str], str] = {}
    without = []
    for name, files in entries:
        if not files:
            without.append(name)
        for filename, text in files:
            kind = "NOTICE" if "notice" in filename.lower() else "LICENSE"
            key = (kind, hashlib.sha256(text.encode("utf-8")).hexdigest())
            groups.setdefault(key, []).append(name)
            texts[key] = text
    rule = "=" * 80
    lines = []
    for (kind, digest), names in sorted(groups.items(), key=lambda item: (item[0][0], item[1][0])):
        lines += [rule, f"{kind} text of {len(set(names))} packages:", *[f"  {name}" for name in sorted(set(names))], "", texts[(kind, digest)], ""]
    return lines, without


def render(label: str, packages: list[tuple[str, str, str]], repo: Path, fonts: str | None = None) -> str:
    entries = []
    for group, artifact, version in packages:
        jar = jar_path(repo, group, artifact, version)
        entries.append((f"{group}:{artifact} {version}", legal_files(jar) if jar else []))
    lines = [
        f"Third-party notices of {label}",
        "",
        f"{label[0].upper() + label[1:]} holds {len(packages)} {'library' if len(packages) == 1 else 'libraries'} in one jar. Each text below is a license or notice file that the jar of a library carries,",
        "given once with the libraries that share it. The license of DX-OS itself is in the LICENSE and NOTICE files next to this one.",
        "",
    ]
    body, without = grouped(entries)
    lines += body
    rule = "=" * 80
    if without:
        declared = [(name, inventory.maven_license(repo, *re.match(r"(.+):(.+) (.+)", name).groups())) for name in without]
        lines += [rule, "Libraries whose jar carries no license file, with the license their POM declares:", *[f"  {name}: {license_}" for name, license_ in sorted(declared)], ""]
        if any(license_ == "Apache-2.0" for _name, license_ in declared):
            lines += [rule, "Apache License 2.0, the text for the libraries above that declare it:", "", (ROOT / "LICENSES/Apache-2.0.txt").read_text(encoding="utf-8").strip(), ""]
    if fonts:
        lines += [rule, "Fonts embedded in the jar:", f"  {fonts}", "", (ROOT / "LICENSES/OFL-1.1.txt").read_text(encoding="utf-8").strip(), ""]
    return "\n".join(lines)


def fetch(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "dxos-license-notices"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def pgschema_reference() -> tuple[str, str]:
    match = PGSCHEMA_IMAGE.search((ROOT / "backend/Dockerfile").read_text(encoding="utf-8"))
    return match.group(1), match.group(2)


def module_files(path: str, version: str) -> list[tuple[str, str]]:
    """The license files at the root of a Go module, from the module proxy."""
    escaped = re.sub(r"[A-Z]", lambda m: "!" + m.group(0).lower(), path)
    archive = zipfile.ZipFile(io.BytesIO(fetch(f"https://proxy.golang.org/{escaped}/@v/{version}.zip")))
    root = f"{path}@{version}/"
    files = []
    for name in sorted(archive.namelist()):
        base = name[len(root):] if name.startswith(root) else ""
        if base and "/" not in base and re.match(r"^(licen[cs]e|copying|notice)([\-_.].*)?$", base, re.IGNORECASE):
            files.append((base, archive.read(name).decode("utf-8", errors="replace").replace("\r\n", "\n").strip()))
    return files


def render_pgschema() -> str:
    version, digest = pgschema_reference()
    image = f"pgplex/pgschema:{version}@{digest}"
    subprocess.run(["docker", "pull", "-q", image], check=True, capture_output=True)
    container = subprocess.check_output(["docker", "create", image]).decode().strip()
    with tempfile.TemporaryDirectory() as folder:
        try:
            subprocess.run(["docker", "cp", f"{container}:/usr/local/bin/pgschema", f"{folder}/pgschema"], check=True, capture_output=True)
        finally:
            subprocess.run(["docker", "rm", container], capture_output=True)
        binary = (Path(folder) / "pgschema").read_bytes()
    modules = sorted(set(re.findall(rb"\ndep\t([^\t\n]+)\t([^\t\n]+)\t", binary)))
    go = re.search(rb"(?<![\w.])go(1\.\d+\.\d+)(?![\w.])", binary).group(1).decode()
    entries = [(f"pgschema {version}", [("LICENSE", fetch(f"https://raw.githubusercontent.com/pgplex/pgschema/v{version}/LICENSE").decode("utf-8").strip())])]
    entries.append((f"Go standard library {go}", [("LICENSE", fetch(f"https://raw.githubusercontent.com/golang/go/go{go}/LICENSE").decode("utf-8").strip())]))
    entries += [(f"{path.decode()} {mod_version.decode()}", module_files(path.decode(), mod_version.decode())) for path, mod_version in modules]
    lines = [
        "Third-party notices of the pgschema binary in the dxos-backend image",
        "",
        f"Image: {image}",
        f"The binary is statically linked from Go {go} and {len(modules)} Go modules, named in its build information.",
        "Each text below is a license or notice file of the project or of a module, given once with the packages that share it.",
        "",
        "Modules and their licenses, as deps.dev records them:",
        f"  pgschema {version}: Apache-2.0",
        f"  Go standard library {go}: BSD-3-Clause",
        *[f"  {path.decode()} {mod_version.decode()}: {images.dependency_license('go', path.decode(), mod_version.decode()) or '(not recorded)'}" for path, mod_version in modules],
        "",
    ]
    body, without = grouped(entries)
    lines += body
    if without:
        lines += ["=" * 80, "Modules whose source carries no license file:", *[f"  {name}" for name in sorted(without)], ""]
    return "\n".join(lines)

def central(path: str) -> bytes:
    return fetch(f"https://repo1.maven.org/maven2/{path}")


def parent_of(pom: Path) -> tuple[str, str, str] | None:
    """The parent a POM names, read from the project element and not from a plugin or a dependency below it."""
    try:
        root = ET.parse(pom).getroot()
    except ET.ParseError:
        head = re.split(r"<(?:dependencies|build|dependencyManagement)>", pom.read_text(encoding="utf-8", errors="replace"))[0]
        found = re.search(r"<parent>.*?<groupId>([^<]+)</groupId>.*?<artifactId>([^<]+)</artifactId>.*?<version>([^<]+)</version>", head, re.DOTALL)
        return tuple(item.strip() for item in found.groups()) if found else None
    for element in root.iter():
        element.tag = re.sub(r"\{[^}]*\}", "", element.tag)
    parent = root.find("parent")
    if parent is None:
        return None
    return tuple((parent.findtext(tag) or "").strip() for tag in ("groupId", "artifactId", "version"))


def central_pom_licenses(repo: Path, group: str, artifact: str, version: str) -> str:
    """The licenses of a library from the POM that Maven Central keeps for it, following its parents."""
    wanted = (group, artifact, version)
    for _ in range(8):
        g, a, v = wanted
        pom = repo / g.replace(".", "/") / a / v / f"{a}-{v}.pom"
        if not pom.exists():
            try:
                body = central(f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.pom")
            except OSError:
                break
            pom.parent.mkdir(parents=True, exist_ok=True)
            pom.write_bytes(body)
        if inventory.pom_licenses(repo, g, a, v):
            break
        parent = parent_of(pom)
        if parent is None:
            break
        wanted = parent
    return inventory.maven_license(repo, group, artifact, version)


def guess_coordinates(repo: Path, stem: str) -> tuple[str, str, str]:
    """group, artifact and version of a jar named group.artifact-version.jar, from the split at which Maven Central has the library."""
    for start in [match.start() for match in re.finditer(r"-\d", stem)]:
        name, version = stem[:start], stem[start + 1:]
        parts = name.split(".")
        for cut in range(1, len(parts)):
            group, artifact = ".".join(parts[:cut]), ".".join(parts[cut:])
            pom = repo / group.replace(".", "/") / artifact / version / f"{artifact}-{version}.pom"
            if pom.exists():
                return group, artifact, version
            try:
                body = central(f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom")
            except OSError:
                continue
            pom.parent.mkdir(parents=True, exist_ok=True)
            pom.write_bytes(body)
            return group, artifact, version
    split = re.search(r"-\d", stem)
    return ("", stem[: split.start()], stem[split.start() + 1 :]) if split else ("", stem, "")


def keycloak_reference() -> tuple[str, str]:
    text = (ROOT / "sso/Dockerfile").read_text(encoding="utf-8")
    return re.search(r"ARG KEYCLOAK_VERSION=(\S+)", text).group(1), re.search(r"ARG KEYCLOAK_SHA1=(\S+)", text).group(1)


def render_keycloak() -> str:
    version, sha1 = keycloak_reference()
    data = fetch(f"https://github.com/keycloak/keycloak/releases/download/{version}/keycloak-{version}.tar.gz")
    if hashlib.sha1(data).hexdigest() != sha1:
        raise SystemExit("The Keycloak archive does not match the SHA-1 of sso/Dockerfile")
    entries, licenses = [], []
    with tempfile.TemporaryDirectory() as folder, tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as archive:
        repo = Path(folder)
        for member in sorted(archive.getmembers(), key=lambda item: item.name):
            if not member.isfile() or not member.name.endswith(".jar") or "/lib/" not in member.name and "/providers/" not in member.name:
                continue
            with zipfile.ZipFile(io.BytesIO(archive.extractfile(member).read())) as jar:
                properties = next((name for name in jar.namelist() if name.startswith("META-INF/maven/") and name.endswith("/pom.properties")), None)
                coordinates = dict(line.split("=", 1) for line in jar.read(properties).decode().splitlines() if "=" in line) if properties else {}
                stem = re.match(r"(?P<name>.+?)-(?P<version>\d[\w.\-]*)\.jar$", member.name.rsplit("/", 1)[-1])
                if not stem:
                    continue
                group, artifact = coordinates.get("groupId"), coordinates.get("artifactId")
                versions = list(dict.fromkeys(v for v in (coordinates.get("version"), stem.group("version")) if v))
                if not group:
                    group, artifact, guessed = guess_coordinates(repo, member.name.rsplit("/", 1)[-1].removesuffix(".jar"))
                    versions = [guessed] if guessed else versions
                if group == "org.keycloak":
                    continue
                jar_version = versions[0]
                files = []
                for name in sorted(jar.namelist()):
                    info = jar.getinfo(name)
                    if not info.is_dir() and info.file_size <= MAX_BYTES and LEGAL.match(name):
                        text = jar.read(name).decode("utf-8", errors="replace").replace("\r\n", "\n").strip()
                        if text:
                            files.append((name, text))
                entries.append((f"{group}:{artifact} {jar_version}", files))
                declared = next((found for found in (central_pom_licenses(repo, group, artifact, candidate) for candidate in versions) if found != "(not declared)"), "(not declared)")
                licenses.append((f"{group}:{artifact}", jar_version, declared))
    # A jar that carries the same library twice under two names, one of them without a group, is listed once.
    named = {(name.split(":", 1)[1], version) for name, version, _ in licenses if not name.startswith(":")}
    licenses = sorted({item for item in licenses if not (item[0].startswith(":") and (item[0][1:], item[1]) in named)})
    entries = [item for item in entries if not (item[0].startswith(":") and tuple(item[0][1:].rsplit(" ", 1)) in named)]
    lines = [
        "Third-party notices of the Keycloak distribution in the dxos-sso image",
        "",
        f"Keycloak {version} (Apache-2.0, the license in /opt/keycloak/LICENSE.txt) is unpacked from its release archive, checked against SHA-1 {sha1}, without changes.",
        f"The libraries it carries ({len(licenses)}) and their licenses, as the POM of each declares them on Maven Central:",
        *[f"  {name} {lib_version}: {license_}" for name, lib_version, license_ in licenses],
        "",
        "Each text below is a license or notice file that the jar of a library carries, given once with the libraries that share it.",
        "",
    ]
    body, without = grouped(entries)
    lines += body
    if without:
        lines += ["=" * 80, "Libraries whose jar carries no license file:", *[f"  {name}" for name in sorted(without)], "", "The Apache License 2.0, for those that declare it, is in /licenses/LICENSE of this image.", ""]
    return "\n".join(lines)


def clamav_version() -> str:
    return re.search(r"clamav/clamav:([\d.]+)@", (ROOT / "clamav/Dockerfile").read_text(encoding="utf-8")).group(1)


def render_clamav() -> str:
    version = clamav_version()
    tag = f"clamav-{version}"
    raw = f"https://raw.githubusercontent.com/Cisco-Talos/clamav/{tag}"
    names = [item["name"] for item in __import__("json").loads(fetch(f"https://api.github.com/repos/Cisco-Talos/clamav/contents/COPYING?ref={tag}"))]
    entries = [(f"ClamAV {version}", [("COPYING.txt", fetch(f"{raw}/COPYING.txt").decode("utf-8").strip())])]
    for name in sorted(names):
        if name == "COPYING.unrar":
            continue
        entries.append((f"third-party part of ClamAV: {name.removeprefix('COPYING.')}", [(name, fetch(f"{raw}/COPYING/{name}").decode("utf-8", errors="replace").replace("\r\n", "\n").strip())]))
    lines = [
        "License of ClamAV and of the third-party parts built into the dxos-clamav image",
        "",
        f"ClamAV {version} is licensed for public use under the GNU General Public License, version 2, copyright Cisco Systems, Inc.",
        f"Its source is at https://github.com/Cisco-Talos/clamav/tree/{tag}; the image is built from the official image clamav/clamav, and the Dockerfile that builds it is clamav/Dockerfile of DX-OS.",
        "The README of ClamAV lists the third-party parts below; their license texts follow, as the project keeps them in its COPYING directory.",
        "libclamunrar, which is under the UnRAR license, is removed from this image; ClamAV runs without RAR support.",
        "The virus signature databases in /var/lib/clamav carry the GNU General Public License, version 2, as a COPYING file inside each .cvd, and are unchanged.",
        "ClamAV is a trademark of Cisco Systems, Inc.; this image is not made or endorsed by Cisco.",
        "",
    ]
    body, _ = grouped(entries)
    return "\n".join(lines + body)


def main(argv: list[str]) -> int:
    if not argv or argv[0] not in ("generate", "check"):
        print(__doc__)
        return 2
    repo = Path(os.environ.get("MAVEN_REPO", Path.home() / ".m2/repository"))
    problems = []
    for module, (label, target) in MODULES.items():
        text = render(label, closure(module), repo, FONTS.get(module))
        if argv[0] == "generate":
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text, encoding="utf-8")
            print(f"{target.relative_to(ROOT)}: {text.count(chr(10))} lines")
        elif not target.exists() or target.read_text(encoding="utf-8") != text:
            problems.append(f"{target.relative_to(ROOT)} is not what the runtime libraries of {module} give: run python3 .github/scripts/check_third_party_notices.py generate")
    version, digest = pgschema_reference()
    if argv[0] == "generate":
        PGSCHEMA.write_text(render_pgschema(), encoding="utf-8")
        print(f"{PGSCHEMA.relative_to(ROOT)}: {PGSCHEMA.read_text(encoding='utf-8').count(chr(10))} lines")
    elif not PGSCHEMA.exists() or f"pgplex/pgschema:{version}@{digest}" not in PGSCHEMA.read_text(encoding="utf-8"):
        problems.append(f"{PGSCHEMA.relative_to(ROOT)} is not for pgplex/pgschema:{version}@{digest}, the image of backend/Dockerfile: run python3 .github/scripts/check_third_party_notices.py generate")
    kc_version, kc_sha1 = keycloak_reference()
    if argv[0] == "generate":
        KEYCLOAK.parent.mkdir(parents=True, exist_ok=True)
        KEYCLOAK.write_text(render_keycloak(), encoding="utf-8")
        CLAMAV.parent.mkdir(parents=True, exist_ok=True)
        CLAMAV.write_text(render_clamav(), encoding="utf-8")
        print(f"{KEYCLOAK.relative_to(ROOT)}: {KEYCLOAK.read_text(encoding='utf-8').count(chr(10))} lines")
        print(f"{CLAMAV.relative_to(ROOT)}: {CLAMAV.read_text(encoding='utf-8').count(chr(10))} lines")
    else:
        if not KEYCLOAK.exists() or f"Keycloak {kc_version}" not in KEYCLOAK.read_text(encoding="utf-8") or kc_sha1 not in KEYCLOAK.read_text(encoding="utf-8"):
            problems.append(f"{KEYCLOAK.relative_to(ROOT)} is not for Keycloak {kc_version} with SHA-1 {kc_sha1}, the archive of sso/Dockerfile: run python3 .github/scripts/check_third_party_notices.py generate")
        if not CLAMAV.exists() or f"ClamAV {clamav_version()} is licensed" not in CLAMAV.read_text(encoding="utf-8"):
            problems.append(f"{CLAMAV.relative_to(ROOT)} is not for ClamAV {clamav_version()}, the version of clamav/Dockerfile: run python3 .github/scripts/check_third_party_notices.py generate")
    for problem in problems:
        print(problem)
    if argv[0] == "check":
        print(f"check_third_party_notices: {len(problems)} problem(s)")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
