# SPDX-License-Identifier: Apache-2.0
"""Download the pinned crop artifact before starting Docker; never at service startup."""

import hashlib
import os
from pathlib import Path
from tempfile import NamedTemporaryFile
from urllib.request import urlopen

REVISION = "c339988a6eceb5e22943ee27e4731a1a1dc9160a"
SHA256 = "b9c34acf76a3d8a04820d7f26c8318736478f54c568a655c09a73f55190837a5"
URL = f"https://huggingface.co/RaiyanKhaan/KrishokTech-Models/resolve/{REVISION}/01_crop_classifier/crop_classifier.onnx"


def main():
    destination = (
        Path(__file__).resolve().parents[1] / "weights" / "crop_classifier.onnx"
    )
    destination.parent.mkdir(parents=True, exist_ok=True)
    if (
        destination.is_file()
        and hashlib.sha256(destination.read_bytes()).hexdigest() == SHA256
    ):
        print("Crop classifier already verified")
        return
    temporary = None
    try:
        with NamedTemporaryFile(dir=destination.parent, delete=False) as output:
            temporary = Path(output.name)
            digest = hashlib.sha256()
            with urlopen(URL, timeout=60) as response:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
                    digest.update(chunk)
            if digest.hexdigest() != SHA256:
                raise ValueError("Downloaded crop classifier SHA-256 mismatch")
        os.chmod(temporary, 0o644)
        temporary.replace(destination)
        print("Crop classifier downloaded and verified")
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
