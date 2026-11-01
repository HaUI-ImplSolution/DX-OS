#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Writes an env file from .env.example: blank passwords, secrets and keys get a random value,
other blank variables stay blank. Used for the first setup of a stack (then set PUBLIC_HOST and the like
before the first start) and by CI.

    python3 deploy/make_env.py [OUTPUT] [--force]     (default: deploy/.env)

An existing OUTPUT is kept unless --force is given.
"""
import pathlib
import secrets
import sys

DEPLOY_DIR = pathlib.Path(__file__).resolve().parent
FIXED = {"POSTGRES_USER": "dxos", "S3_ACCESS_KEY": "dxos-s3"}
SECRET_SUFFIXES = ("_PASSWORD", "_SECRET", "_KEY")


def main():
    args = sys.argv[1:]
    force = "--force" in args
    if force:
        args.remove("--force")
    output = pathlib.Path(args[0]) if args else DEPLOY_DIR / ".env"
    if output.exists() and not force:
        sys.exit(f"{output} already exists; pass --force to replace it.")
    lines = []
    for line in (DEPLOY_DIR / ".env.example").read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator and not line.startswith("#") and not value.strip():
            if key in FIXED:
                value = FIXED[key]
            elif key.endswith(SECRET_SUFFIXES):
                value = secrets.token_hex(16)
            line = f"{key}={value}"
        lines.append(line)
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    output.chmod(0o600)


if __name__ == "__main__":
    main()
