#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Check the license of the version of every image and download that the component catalog names.

    GITHUB_TOKEN=... python3 .github/scripts/check_upstream_licenses.py

A project that changes its license, as Redis did in 7.4, keeps its image name, so a catalog that declares the
license by name would not notice a newer version under the new terms.
For each image and release the repository uses, this reads the version in use (the tag of the image, or the
version a Dockerfile downloads) and asks GitHub for the license of the upstream project at that version.
A pull request that moves a tag is therefore checked at the version it proposes.

The license found must be the one the catalog declares and must be allowed (an OSI approved license).
A license GitHub cannot identify (NOASSERTION), a version it cannot find and a network error all fail:
an unknown license is not an open one.
An entry of the catalog says how it is verified:
  upstream  the GitHub project, as owner/name; it defaults to the project in `source`;
  refs      the git refs of that project that match a version, as templates with {version} (1.4.1) and
            {major} (1); the first that exists is used; they default to v{version} and {version};
  text      for a project whose license file GitHub cannot identify: the file and the sentences it must hold
            at that ref, so a license that changes its wording fails;
  unverified  the reason, for an image with no single upstream project at a version; it is only reported.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_licenses  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
# SPDX ids as GitHub reports them, mapped to the ids of the catalog.
SAME = {"GPL-2.0": "GPL-2.0-only", "GPL-3.0": "GPL-3.0-only", "AGPL-3.0": "AGPL-3.0-only", "LGPL-2.1": "LGPL-2.1-only"}
DEFAULT_REFS = ("v{version}", "{version}")
DOWNLOAD_ARG = re.compile(r"^ARG\s+(\w+)=(\S+)", re.MULTILINE)
DOWNLOAD_URL = re.compile(r"^ADD\s+https://github\.com/([\w.\-]+/[\w.\-]+)/releases/download/\$\{(\w+)\}", re.MULTILINE)


class Unreadable(Exception):
    """GitHub could not be reached or answered something unexpected."""


def github(path: str, raw: bool = False):
    """The answer of the GitHub API, or None when the path does not exist; a failure raises Unreadable.

    The data is public. A token only raises the rate limit, and an organization that restricts tokens (an IP
    allow list, for one) answers 401 or 403 to it, so such an answer is asked again without the token.
    """
    headers = {"User-Agent": "dxos-license-check", "Accept": "application/vnd.github.raw" if raw else "application/vnd.github+json"}
    token = os.environ.get("GITHUB_TOKEN")
    last = path
    for attempt in range(4):
        request = urllib.request.Request(f"https://api.github.com/{path}", headers=dict(headers))
        if token:
            request.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = response.read()
                return body.decode("utf-8", "replace") if raw else json.loads(body)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            detail = error.read().decode("utf-8", "replace")[:160].replace("\n", " ")
            last = f"{path}: HTTP {error.code} {detail}"
            if error.code in (401, 403) and token:
                token = None
                continue
            if error.code in (403, 429, 500, 502, 503):
                time.sleep(2 * (attempt + 1))
                continue
            raise Unreadable(last) from error
        except (urllib.error.URLError, TimeoutError) as error:
            last = f"{path}: {error}"
            time.sleep(2 * (attempt + 1))
    raise Unreadable(last)


def version_of(tag: str) -> str | None:
    """The version a tag names: 3.11.0-debian is 3.11.0, v8.30.1 is 8.30.1, 22-alpine is 22."""
    match = re.match(r"v?(\d+(?:\.\d+)*)", tag)
    return match.group(1) if match else None


def refs_for(entry: dict, version: str) -> list[str]:
    major = version.split(".")[0]
    return [template.format(version=version, major=major) for template in entry.get("refs", DEFAULT_REFS)]


def project_of(entry: dict) -> str | None:
    if entry.get("upstream"):
        return entry["upstream"]
    match = re.match(r"https://github\.com/([\w.\-]+/[\w.\-]+)", entry.get("source", ""))
    return match.group(1) if match else None


def license_at(project: str, ref: str, fetch=github) -> tuple[bool, str | None]:
    """(the ref exists, the SPDX id GitHub gives the license at it or None)."""
    answer = fetch(f"repos/{project}/license?ref={urllib.parse.quote(ref, safe='')}")
    if answer is None:
        return False, None
    spdx = (answer.get("license") or {}).get("spdx_id")
    return True, None if spdx in (None, "NOASSERTION") else SAME.get(spdx, spdx)


def verify(name: str, entry: dict, version: str, fetch=github) -> str | None:
    """The problem with the license of one version, or None when it is the declared one and allowed."""
    declared = entry["license"]
    project = project_of(entry)
    if project is None:
        return f"{name} {version}: the catalog names no GitHub project; add `upstream`, or `unverified` with the reason"
    found_ref = found = None
    for ref in refs_for(entry, version):
        exists, spdx = license_at(project, ref, fetch)
        if exists:
            found_ref, found = ref, spdx
            break
    if found_ref is None:
        return f"{name} {version}: {project} has none of the refs {refs_for(entry, version)}; add `refs` to the catalog entry"
    if found is None:
        text = entry.get("text")
        if text is None:
            return f"{name} {version}: GitHub cannot identify the license of {project} at {found_ref}; an unknown license is not an open one"
        content = fetch(f"repos/{project}/contents/{urllib.parse.quote(text['path'])}?ref={urllib.parse.quote(found_ref, safe='')}", raw=True)
        missing = [sentence for sentence in text["contains"] if content is None or sentence not in content]
        if missing:
            return f"{name} {version}: {text['path']} of {project} at {found_ref} no longer holds the {declared} text ({missing[0]!r} is missing)"
        found = declared
    if found != declared:
        return f"{name} {version}: the catalog says {declared}, {project} at {found_ref} says {found}"
    if found not in check_licenses.ALLOWED_IMAGES:
        return f"{name} {version}: {found} is not an allowed (OSI approved) license"
    return None


def downloads() -> dict[str, str]:
    """GitHub project to the version a Dockerfile downloads from its releases."""
    found = {}
    for path in ROOT.glob("**/Dockerfile"):
        text = path.read_text(encoding="utf-8")
        args = dict(DOWNLOAD_ARG.findall(text))
        for project, variable in DOWNLOAD_URL.findall(text):
            if variable in args:
                found[project.lower()] = args[variable]
    return found


def versions_in_use() -> dict[str, dict[str, set[str]]]:
    """For images and downloads: the catalog name to the versions the repository uses."""
    images: dict[str, set[str]] = {}
    for name, uses in check_licenses.referenced_references().items():
        for _, reference in uses:
            tag = reference.split("@")[0].rsplit(":", 1)[-1] if ":" in reference.split("@")[0].split("/")[-1] else ""
            version = version_of(tag)
            images.setdefault(name, set()).add(version or tag)
    releases: dict[str, set[str]] = {}
    for project, version in downloads().items():
        releases.setdefault(project, set()).add(version_of(version) or version)
    return {"images": images, "downloads": releases}


def main() -> int:
    catalog = json.loads((ROOT / ".github/component-catalog.json").read_text(encoding="utf-8"))
    used = versions_in_use()
    failures: list[str] = []
    notes: list[str] = []
    checked = 0
    for kind in ("images", "downloads"):
        for name, entry in catalog.get(kind, {}).items():
            versions = sorted(used[kind].get(name) or used[kind].get(name.lower()) or [])
            if not versions:
                continue
            if "unverified" in entry:
                notes.append(f"{name}: not verified at a version ({entry['unverified']})")
                continue
            for version in versions:
                if not re.fullmatch(r"\d+(?:\.\d+)*", version):
                    failures.append(f"{name}: the version in use ({version}) is not a number; pin a tag with a version")
                    continue
                try:
                    problem = verify(name, entry, version)
                except Unreadable as error:
                    problem = f"{name} {version}: the license could not be read ({error})"
                if problem:
                    failures.append(problem)
                else:
                    checked += 1
                    print(f"ok: {name} {version} is {entry['license']}")
    for line in notes:
        print(f"note: {line}")
    for line in failures:
        print(line)
    print(f"{checked} version(s) checked, {len(notes)} note(s), {len(failures)} problem(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
