#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Starts the whole stack with an app and loads its demo data.
#
#   ./start.sh [app] [--build] [--reset] [--yes]
#
# Without [app] the value of DXOS_APP in deploy/.env is used (farm when it is not set).
# Switch to the other app later with the same command, e.g. ./start.sh lab.
# Options are those of switch-app.sh, which does the work.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

APP=""
if [[ $# -gt 0 && "$1" != -* ]]; then
  APP="$1"
  shift
fi
if [[ -z "$APP" ]]; then
  APP="$(grep -s '^DXOS_APP=' .env | cut -d= -f2 || true)"
  APP="${APP:-farm}"
fi

exec ./switch-app.sh "$APP" --seed "$@"
