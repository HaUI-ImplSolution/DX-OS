#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Writes an env file for a throwaway stack: blank variables of .env.example get random values.

    python3 deploy/demo/make_ci_env.py [OUTPUT] [--app farm|lab]     (default: deploy/.env, farm)
"""
import pathlib
import secrets
import sys

DEPLOY_DIR = pathlib.Path(__file__).resolve().parent.parent
FIXED = {"POSTGRES_USER": "dxos", "MINIO_ACCESS_KEY": "dxos-minio"}


def main():
    sys.path.insert(0, str(DEPLOY_DIR.parent))
    from ai.scripts.assemble_cv_weights import verify_parts

    args = sys.argv[1:]
    app = "farm"
    if "--app" in args:
        position = args.index("--app")
        app = args[position + 1]
        del args[position:position + 2]
    checksum = verify_parts()
    lines = []
    for line in (DEPLOY_DIR / ".env.example").read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator and not line.startswith("#") and not value.strip():
            if key == "AI_CV_WEIGHTS_SHA256":
                value = checksum
            else:
                value = FIXED.get(key, secrets.token_hex(16))
            line = f"{key}={value}"
        if key == "DXOS_APP":
            line = f"DXOS_APP={app}"
        lines.append(line)
    output = pathlib.Path(args[0]) if args else DEPLOY_DIR / ".env"
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")
    output.chmod(0o600)


if __name__ == "__main__":
    main()
