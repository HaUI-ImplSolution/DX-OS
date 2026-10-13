#!/usr/bin/env bash
# Switches the stack to another app of the shared core.
#
#   ./switch-app.sh <app> [--reset] [--yes]
#
# <app> is a name with a gateway file in apisix/apps/ (farm or lab). The script writes DXOS_APP
# to deploy/.env, then recreates what depends on the app: the gateway routes, the backend, the
# knowledge ingest and the Superset datasets. The web image is not rebuilt: the web app loads the
# screens of the running app from the backend.
# Without --reset the tables of the previous app stay in the database, unused.
# --reset empties deploy/data first and rebuilds everything (reset.sh; --yes skips its prompt).
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

APP=""
RESET=false
ASSUME_YES=()
for arg in "$@"; do
  case "$arg" in
    --reset) RESET=true ;;
    --yes|-y) ASSUME_YES=(--yes) ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    -*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) APP="$arg" ;;
  esac
done

KNOWN=()
for file in apisix/apps/*.yaml; do
  name="$(basename "$file" .yaml)"
  KNOWN+=("$name")
done

if [[ -z "$APP" ]]; then
  echo "Usage: ./switch-app.sh <${KNOWN[*]// /|}> [--reset] [--yes]" >&2
  exit 2
fi
if [[ ! -f "apisix/apps/$APP.yaml" ]]; then
  echo "Unknown app '$APP', expected one of: ${KNOWN[*]}" >&2
  exit 2
fi
if [[ ! -f .env ]]; then
  echo "deploy/.env not found. Run: cp .env.example .env and fill every blank variable." >&2
  exit 1
fi

# .env may end without a newline, so a plain append would join two lines.
if grep -q '^DXOS_APP=' .env; then
  sed -i "s/^DXOS_APP=.*/DXOS_APP=$APP/" .env
else
  [[ -z "$(tail -c1 .env)" ]] || echo >> .env
  echo "DXOS_APP=$APP" >> .env
fi
echo "==> DXOS_APP=$APP written to deploy/.env"

if [[ "$RESET" == true ]]; then
  exec ./reset.sh ${ASSUME_YES[@]+"${ASSUME_YES[@]}"}
fi

echo "==> Recreating the gateway routes, backend, knowledge ingest and Superset"
docker compose up -d --force-recreate apisix-config backend rag-ingest superset

PORT="$(grep '^APISIX_HOST_PORT=' .env | cut -d= -f2)"
URL="http://localhost:${PORT:-9080}/api/v1/system/status"
TIMEOUT_SECONDS="${SWITCH_TIMEOUT_SECONDS:-600}"
echo "==> Waiting for $URL to report '$APP' (timeout ${TIMEOUT_SECONDS}s)"
deadline=$((SECONDS + TIMEOUT_SECONDS))
until curl -fsS "$URL" 2>/dev/null | grep -Eq "\"id\" *: *\"$APP\""; do
  if ((SECONDS >= deadline)); then
    echo "Timed out. Check: docker compose logs backend" >&2
    exit 1
  fi
  sleep 5
done
echo "The stack runs '$APP'. Reload the web page."
