#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Seeds the demo data of the running app: sample accounts and sample records.

    python3 deploy/demo/seed_demo.py [--app farm|lab] [--base-url URL] [--images DIR]

Run it once against a fresh stack (deploy/reset.sh). Without --app the app is read from the
running backend (GET /api/v1/system/status). The sample accounts are created through the Keycloak
Admin API with the administrator of deploy/.env; everything else goes through the same endpoints
the apps use, so the workflow and the business rules are exercised for real.
Running it again creates nothing twice. --images (real leaf photos) is used by the Farm seed.
"""
import argparse
import os
import sys

import dxos_client as dx
import seed_farm
import seed_lab

SEEDS = {"farm": seed_farm, "lab": seed_lab}


def resolve_app(base_url, requested):
    """The app to seed: --app, or the one the backend runs; both must agree when both are known."""
    running = dx.running_app(base_url)
    if requested and running and requested != running:
        sys.exit(f"The backend runs '{running}', not '{requested}'. "
                 f"Switch with deploy/switch-app.sh {requested}.")
    app = requested or running or os.environ.get("DXOS_APP") or dx.read_env().get("DXOS_APP") or "farm"
    if app not in SEEDS:
        sys.exit(f"Unknown app '{app}', expected one of {sorted(SEEDS)}.")
    return app


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--app", choices=sorted(SEEDS), help="app to seed (default: the running one)")
    parser.add_argument("--base-url", default=dx.DEFAULT_BASE_URL)
    parser.add_argument("--images", help="directory of real leaf photos (.jpg), Farm only")
    args = parser.parse_args()

    app = resolve_app(args.base_url, args.app)
    print(f"Seeding the {app} demo data")
    SEEDS[app].seed(args.base_url, images=args.images)


if __name__ == "__main__":
    main()
