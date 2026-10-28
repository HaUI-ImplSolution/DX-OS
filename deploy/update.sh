#!/usr/bin/env bash
# Updates a running stack to the images published by the Release workflow; nothing is built here.
#
#   ./update.sh [tag]
#
# [tag] is latest or sha-<7 chars> and is written to DXOS_IMAGE_TAG in deploy/.env.
# Images are pulled one at a time, only the containers whose image changed are recreated,
# the gateway restarts when a service behind it was replaced, and replaced images are removed.
# The first start on a server is: COMPOSE_PARALLEL_LIMIT=1 docker compose pull && ./start.sh
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ ! -f .env ]]; then
  echo "deploy/.env not found. Run: python3 demo/make_ci_env.py .env (random secrets), or cp .env.example .env and fill every blank variable." >&2
  exit 1
fi
if ! grep -q '^DXOS_IMAGE_PREFIX=' .env; then
  echo "DXOS_IMAGE_PREFIX is not set in deploy/.env; see .env.example." >&2
  exit 1
fi

if [[ $# -gt 0 ]]; then
  # .env may end without a newline, so a plain append would join two lines.
  if grep -q '^DXOS_IMAGE_TAG=' .env; then
    sed -i "s/^DXOS_IMAGE_TAG=.*/DXOS_IMAGE_TAG=$1/" .env
  else
    [[ -z "$(tail -c1 .env)" ]] || echo >> .env
    echo "DXOS_IMAGE_TAG=$1" >> .env
  fi
  echo "==> DXOS_IMAGE_TAG=$1 written to deploy/.env"
fi

BEHIND_GATEWAY=(backend monitor frontend portal keycloak)
declare -A BEFORE
for service in "${BEHIND_GATEWAY[@]}"; do
  BEFORE[$service]="$(docker compose ps -q "$service")"
done

echo "==> Pulling images"
for service in backend monitor frontend portal ai minio keycloak; do
  docker compose pull "$service"
done

echo "==> Recreating the services whose image changed"
docker compose up -d --no-build

REPLACED=false
for service in "${BEHIND_GATEWAY[@]}"; do
  [[ "$(docker compose ps -q "$service")" == "${BEFORE[$service]}" ]] || REPLACED=true
done
if [[ "$REPLACED" == true ]]; then
  # APISIX workers keep the address of a replaced container until restarted.
  docker compose restart apisix
fi

docker image prune -f >/dev/null
echo "==> Up to date. Check: docker compose ps"
