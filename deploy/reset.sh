#!/usr/bin/env bash
# Rebuilds the stack from scratch: stops the containers, wipes deploy/data and starts again.
#
#   ./reset.sh [--yes] [--keep-images] [service ...]
#
# DESTRUCTIVE: deletes every database, upload and chat/BI state under deploy/data.
# The Ollama model volume is kept so the multi-GB model is not downloaded again.
# deploy/data holds root-owned files, so it is emptied from a throwaway container.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

ASSUME_YES=false
BUILD_FLAG=(--build)
SERVICES=()
for arg in "$@"; do
  case "$arg" in
    --yes|-y) ASSUME_YES=true ;;
    --keep-images) BUILD_FLAG=() ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    -*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) SERVICES+=("$arg") ;;
  esac
done

if [[ ! -f .env ]]; then
  echo "deploy/.env not found. Run: cp .env.example .env and fill every blank variable." >&2
  exit 1
fi

DATA_DIR="$(pwd)/data"
TIMEOUT_SECONDS="${RESET_TIMEOUT_SECONDS:-1800}"

if [[ "$ASSUME_YES" != true ]]; then
  echo "This permanently deletes everything under: $DATA_DIR"
  read -r -p "Type 'reset' to continue: " answer
  [[ "$answer" == "reset" ]] || { echo "Aborted."; exit 1; }
fi

echo "==> Stopping containers"
docker compose down --remove-orphans

echo "==> Emptying $DATA_DIR"
mkdir -p "$DATA_DIR"
docker run --rm -v "$DATA_DIR:/data" alpine:3.20 find /data -mindepth 1 -delete

echo "==> Starting the stack"
docker compose up -d ${BUILD_FLAG[@]+"${BUILD_FLAG[@]}"} ${SERVICES[@]+"${SERVICES[@]}"}

# A container is ready when it is healthy, running without a healthcheck, or a one-shot job
# that exited with code 0.
container_state() {
  docker inspect -f \
    '{{.Name}} {{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}-{{end}} {{.State.ExitCode}}' "$1"
}

pending_services() {
  local pending=()
  local id name status health exit_code
  while read -r id; do
    [[ -n "$id" ]] || continue
    read -r name status health exit_code <<<"$(container_state "$id")"
    case "$status:$health:$exit_code" in
      running:healthy:*|running:-:*|exited:*:0) ;;
      *) pending+=("${name#/}($status/$health)") ;;
    esac
  done < <(docker compose ps -a -q)
  echo "${pending[*]:-}"
}

echo "==> Waiting for services (timeout ${TIMEOUT_SECONDS}s)"
deadline=$((SECONDS + TIMEOUT_SECONDS))
while true; do
  pending="$(pending_services)"
  if [[ -z "$pending" ]]; then
    echo "All services are ready."
    docker compose ps
    exit 0
  fi
  if ((SECONDS >= deadline)); then
    echo "Timed out. Not ready: $pending" >&2
    docker compose ps >&2
    exit 1
  fi
  sleep 10
done
