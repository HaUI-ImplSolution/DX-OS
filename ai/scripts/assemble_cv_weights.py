# SPDX-License-Identifier: AGPL-3.0-only
"""Join the bundled checkpoint locally; never download or print its checksum."""

import argparse
import hashlib
import json
import os
import re
import tempfile
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[1]
PACKAGE = AI_ROOT / "weights/dx-farm-efficientnetv2m"
DEFAULT_OUTPUT = AI_ROOT / "models/efficientnetv2m_rice_disease.pth"


def verify_parts(source=PACKAGE, destination=None, *, expected_sha256=None):
    source = Path(source)
    manifest = json.loads((source / "parts.json").read_text())
    if [part["name"] for part in manifest["parts"]] != [
        f"model.pth.part-{index:03d}" for index in range(3)
    ]:
        raise ValueError("CV weight parts must be ordered 000..002")
    digest, total = hashlib.sha256(), 0
    for part in manifest["parts"]:
        size = 0
        with (source / part["name"]).open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
                size += len(chunk)
                if destination is not None:
                    destination.write(chunk)
        if size != part["bytes"]:
            raise ValueError("CV weight part size mismatch")
        total += size
    if total != manifest["bytes"]:
        raise ValueError("CV checkpoint size mismatch")
    checksum = digest.hexdigest()
    if expected_sha256 is not None:
        if not re.fullmatch(r"[0-9a-fA-F]{64}", expected_sha256):
            raise ValueError("Invalid CV_WEIGHTS_SHA256")
        if checksum != expected_sha256.lower():
            raise ValueError("CV checkpoint checksum mismatch")
    return checksum


def assemble(source=PACKAGE, output=DEFAULT_OUTPUT):
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".cv-", dir=output.parent)
    try:
        with os.fdopen(descriptor, "wb") as destination:
            verify_parts(
                source, destination, expected_sha256=os.getenv("CV_WEIGHTS_SHA256")
            )
        os.chmod(temporary, 0o644)
        os.replace(temporary, output)
    finally:
        Path(temporary).unlink(missing_ok=True)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=PACKAGE)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    if args.verify_only:
        verify_parts(args.source, expected_sha256=os.getenv("CV_WEIGHTS_SHA256"))
    else:
        assemble(args.source, args.output)
    print("Bundled CV weights verified.")


if __name__ == "__main__":
    main()
