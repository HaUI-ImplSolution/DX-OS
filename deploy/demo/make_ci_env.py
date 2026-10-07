#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Writes deploy/.env for a throwaway CI stack: blank variables of .env.example get random values."""
import hashlib
import pathlib
import secrets

DEPLOY_DIR = pathlib.Path(__file__).resolve().parent.parent
FIXED = {"POSTGRES_USER": "dxfarm", "MINIO_ACCESS_KEY": "dxfarm-minio"}
OPTIONAL_BLANK = {"MATTERMOST_WEBHOOK_URL", "MATTERMOST_WEBHOOK_URL_ADMIN", "MATTERMOST_WEBHOOK_URL_ENGINEER"}


def main():
    weights = DEPLOY_DIR.parent / "ai" / "weights" / "best.pt"
    lines = []
    for line in (DEPLOY_DIR / ".env.example").read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator and not line.startswith("#") and not value.strip() and key not in OPTIONAL_BLANK:
            if key == "AI_CV_WEIGHTS_SHA256":
                value = hashlib.sha256(weights.read_bytes()).hexdigest()
            else:
                value = FIXED.get(key, secrets.token_hex(16))
            line = f"{key}={value}"
        lines.append(line)
    (DEPLOY_DIR / ".env").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
