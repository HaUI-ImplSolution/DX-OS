#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Writes an env file from .env.example: blank passwords, secrets and keys get a random value,
the model checksums are computed from ai/weights, other blank variables stay blank. Used for the first setup of a stack (then set PUBLIC_HOST and the like before the first start) and by CI.

    python3 deploy/demo/make_ci_env.py [OUTPUT] [--app farm|lab] [--force]     (default: deploy/.env, farm)

An existing OUTPUT is kept unless --force is given.
"""
import hashlib
import pathlib
import secrets
import sys

DEPLOY_DIR = pathlib.Path(__file__).resolve().parent.parent
FIXED = {"POSTGRES_USER": "dxos", "MINIO_ACCESS_KEY": "dxos-minio"}
SECRET_SUFFIXES = ("_PASSWORD", "_SECRET", "_KEY")


def main():
    sys.path.insert(0, str(DEPLOY_DIR.parent))
    from ai.scripts.assemble_cv_weights import verify_parts

    args = sys.argv[1:]
    force = "--force" in args
    if force:
        args.remove("--force")
    app = "farm"
    if "--app" in args:
        position = args.index("--app")
        app = args[position + 1]
        del args[position:position + 2]
    output = pathlib.Path(args[0]) if args else DEPLOY_DIR / ".env"
    if output.exists() and not force:
        sys.exit(f"{output} already exists; pass --force to replace it.")
    checksum = verify_parts()
    crop_checksum = hashlib.sha256((DEPLOY_DIR.parent / "ai/weights/crop_classifier.onnx").read_bytes()).hexdigest()
    lines = []
    for line in (DEPLOY_DIR / ".env.example").read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator and not line.startswith("#") and not value.strip():
            if key == "AI_CV_WEIGHTS_SHA256":
                value = checksum
            elif key == "AI_CROP_CLASSIFIER_SHA256":
                value = crop_checksum
            elif key in FIXED:
                value = FIXED[key]
            elif key.endswith(SECRET_SUFFIXES):
                value = secrets.token_hex(16)
            line = f"{key}={value}"
        if key == "DXOS_APP":
            line = f"DXOS_APP={app}"
        lines.append(line)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    output.chmod(0o600)


if __name__ == "__main__":
    main()
