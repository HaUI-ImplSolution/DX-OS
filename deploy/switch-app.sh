#!/usr/bin/env bash
# Starts the stack with an app of the shared core, or switches a running stack to another app.
#
#   ./switch-app.sh <app> [--seed] [--build] [--reset] [--yes]
#
# <app> is a name with a gateway file in apisix/apps/ (farm or lab). The script writes DXOS_APP
# to deploy/.env, recreates what depends on the app (gateway routes, backend, knowledge ingest,
# Superset datasets), starts every other service that is not running, then waits until
# GET /api/v1/system/status names the app.
# --seed   loads the demo data of the app (deploy/demo/seed_demo.py), safe to repeat.
# --build  rebuilds the images; the web image is otherwise reused when the app changes.
# --reset  empties deploy/data first and rebuilds everything (reset.sh; --yes skips its prompt).
# The tables of the previous app stay in the database, unused, unless --reset is given.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

APP=""
SEED=false
RESET=false
BUILD_FLAG=()
ASSUME_YES=()
for arg in "$@"; do
  case "$arg" in
    --seed) SEED=true ;;
    --build) BUILD_FLAG=(--build) ;;
    --reset) RESET=true ;;
    --yes|-y) ASSUME_YES=(--yes) ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    -*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) APP="$arg" ;;
  esac
done

KNOWN=()
for file in apisix/apps/*.yaml; do
  KNOWN+=("$(basename "$file" .yaml)")
done

if [[ -z "$APP" ]]; then
  echo "Usage: ./switch-app.sh <${KNOWN[*]// /|}> [--seed] [--build] [--reset] [--yes]" >&2
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
  ./reset.sh ${ASSUME_YES[@]+"${ASSUME_YES[@]}"}
else
  echo "==> Recreating the gateway routes, backend, knowledge ingest and Superset"
  docker compose up -d ${BUILD_FLAG[@]+"${BUILD_FLAG[@]}"} --force-recreate apisix-config backend rag-ingest superset
  echo "==> Starting the remaining services"
  docker compose up -d ${BUILD_FLAG[@]+"${BUILD_FLAG[@]}"}
  # APISIX workers keep the address of the replaced backend container until restarted.
  docker compose restart apisix
fi

PORT="$(grep '^APISIX_HOST_PORT=' .env | cut -d= -f2)"
URL="http://localhost:${PORT:-9080}/api/v1/system/status"
TIMEOUT_SECONDS="${SWITCH_TIMEOUT_SECONDS:-900}"
echo "==> Waiting for $URL to report '$APP' (timeout ${TIMEOUT_SECONDS}s)"
deadline=$((SECONDS + TIMEOUT_SECONDS))
until curl -fsS "$URL" 2>/dev/null | grep -Eq "\"id\" *: *\"$APP\""; do
  if ((SECONDS >= deadline)); then
    echo "Timed out. Check: docker compose logs backend" >&2
    exit 1
  fi
  sleep 5
done

if [[ "$SEED" == true ]]; then
  # On Windows `python3` may be the Microsoft Store alias, which exists but does not run Python.
  PYTHON=""
  for candidate in python3 python; do
    if "$candidate" -c 'import sys' >/dev/null 2>&1; then
      PYTHON="$candidate"
      break
    fi
  done
  if [[ -z "$PYTHON" ]]; then
    echo "Python 3 not found; install it, then run: python demo/seed_demo.py --app $APP" >&2
    exit 1
  fi
  echo "==> Loading the demo data of '$APP'"
  "$PYTHON" demo/seed_demo.py --app "$APP"
fi

# Services that are not ready yet; the AI service starts last (model pull, knowledge ingest)
# and the app works without it, so it is not waited for.
not_ready() {
  docker compose ps --format '{{.Service}} {{.Status}}' |
    grep -vE '\(healthy\)|Exited \(0\)|Up [^(]*$' | cut -d' ' -f1 | grep -vxE 'ai|rag-ingest|ollama-init' |
    tr '\n' ' ' || true
}
deadline=$((SECONDS + 120))
while [[ -n "$(not_ready)" ]] && ((SECONDS < deadline)); do
  sleep 5
done
PENDING="$(not_ready)"
if [[ -n "$PENDING" ]]; then
  echo "Still starting: ${PENDING}(watch with: docker compose ps)"
fi
if docker compose ps --format '{{.Service}} {{.Status}}' | grep -E '^ai ' | grep -qv '(healthy)'; then
  echo "The AI service is still starting; questions and diagnosis work once it is healthy."
fi
echo "The stack runs '$APP'. Open http://localhost:${PORT:-9080} and reload the page if it was open."
