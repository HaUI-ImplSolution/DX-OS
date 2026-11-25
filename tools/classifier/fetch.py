# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0

import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parent
CACHE = Path.home() / ".cache" / "dxos-classifier"
SOURCES_PATH = ROOT / "data" / "sources.json"
DATA_LICENSES = {"CC0-1.0", "CC-BY-4.0", "ODbL-1.0", "Apache-2.0"}


# Compute the SHA-256 digest of a file.
def sha256(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


# Return the file path if its SHA-256 matches the manifest.
def verify(path, expected):
    actual = sha256(path)
    if actual != expected:
        raise ValueError(f"{path}: expected SHA-256 {expected}, got {actual}")
    return path


# Read the source manifest and validate its data licenses.
def sources():
    manifest = json.loads(SOURCES_PATH.read_text(encoding="utf-8"))
    for source in manifest.values():
        if source["license"] not in DATA_LICENSES:
            raise ValueError(f"Unsupported data license: {source['license']}")
    return manifest


# Reuse a verified cached file or download and verify it before caching.
def fetch_file(entry):
    path = CACHE / entry["filename"]
    if path.is_file() and sha256(path) == entry["sha256"]:
        return path

    CACHE.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=CACHE, delete=False) as output:
        temp = Path(output.name)
    try:
        with urlopen(entry["url"], timeout=90) as response, temp.open("wb") as output:
            shutil.copyfileobj(response, output)
        verify(temp, entry["sha256"])
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)
    return path


# Fetch each manifest entry and print its revision, digest, and cached path.
def main():
    for source in sources().values():
        path = fetch_file(source)
        print(f"{source['name']} {source['revision']}  {source['sha256']}  {path}")


if __name__ == "__main__":
    main()
