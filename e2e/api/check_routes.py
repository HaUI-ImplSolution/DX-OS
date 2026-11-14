#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Checks gateway routes with the same renderer and APISIX image used at runtime."""
import argparse
import subprocess
from pathlib import Path

import yaml

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--local", type=Path, help="installation routes to check instead of apisix/local.yaml")
    parser.add_argument("--mode", choices=("full", "core"), default="full")
    parser.add_argument("--admin-console", action="store_true", help="check with the Keycloak Admin Console enabled")
    args = parser.parse_args()
    source = Path(__file__).resolve().parents[2] / "deploy" / "apisix"
    compose = yaml.safe_load((source.parent / "docker-compose.yml").read_text())
    command = ["docker", "run", "--rm", "--network", "none", "-v", f"{source}:/src:ro",
               "-e", f"DXOS_MODE={args.mode}",
               "-e", f"KEYCLOAK_ADMIN_CONSOLE={str(args.admin_console).lower()}"]
    if args.local:
        if not args.local.is_file():
            parser.error("--local must name an existing file")
        command += ["-v", f"{args.local.resolve()}:/local.yaml:ro"]
    command += ["--entrypoint", "/usr/local/openresty/bin/resty", compose["services"]["apisix"]["image"],
                "--errlog-level", "error", "-I", "/usr/local/apisix", "-I", "/usr/local/apisix/deps/share/lua/5.1",
                "-I", "/usr/local/apisix/deps/lib/lua/5.1", "/src/render.lua", "--check"]
    if args.local:
        command += ["--local", "/local.yaml"]
    try:
        return subprocess.run(command, check=False).returncode
    except OSError:
        parser.exit(1, "Docker is required to check the gateway routes.\n")


if __name__ == "__main__":
    raise SystemExit(main())
