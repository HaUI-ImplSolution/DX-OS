#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""End-to-end run of the running app against a fresh stack, through the gateway.

    python3 deploy/demo/e2e.py [--app farm|lab] [--base-url URL] [--require-rule] [--require-knowledge]

Run seed_demo.py first: the scenarios use its sample accounts. Without --app the app is read from
the running backend. Farm: --require-rule fails the run when no treatment rule exists. Lab:
--require-knowledge fails the run when the knowledge base answers without a quoted source.
Exit code 0 when every step passes.
"""
import argparse
import sys

import dxos_client as dx
import e2e_farm
import e2e_lab
import seed_demo

SCENARIOS = {"farm": e2e_farm, "lab": e2e_lab}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--app", choices=sorted(SCENARIOS), help="app to test (default: the running one)")
    parser.add_argument("--base-url", default=dx.DEFAULT_BASE_URL)
    parser.add_argument("--require-rule", action="store_true", help="Farm: fail when no treatment rule exists")
    parser.add_argument("--require-knowledge", action="store_true",
                        help="Lab: fail when the knowledge answer has no quoted source")
    args = parser.parse_args()

    app = seed_demo.resolve_app(args.base_url, args.app)
    print(f"E2E of the {app} app")
    steps = dx.Steps()
    SCENARIOS[app].run(args.base_url, steps, require_rule=args.require_rule,
                       require_knowledge=args.require_knowledge)
    sys.exit(steps.summary())


if __name__ == "__main__":
    main()
