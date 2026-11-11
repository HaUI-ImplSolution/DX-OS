#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Updates a running stack to the images published by the Release workflow; nothing is built here.
#
#   ./update.sh [tag]
#
# [tag] is latest, a release tag such as v2.0.0, or sha-<7 chars>, and is written to DXOS_IMAGE_TAG in deploy/.env.
# Images are pulled one at a time, only the containers whose image changed are recreated,
# the gateway restarts when a service behind it was replaced, and replaced images are removed.
# The first start on a server is: COMPOSE_PARALLEL_LIMIT=1 docker compose pull && docker compose up -d
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if [[ ! -f .env ]]; then
  echo "deploy/.env not found. Run: ./make_env.sh (random secrets), or cp .env.example .env and fill every blank variable." >&2
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

# The lock dxos reconcile takes, so that it does not recreate containers while this runs.
exec 9>.reconcile.lock
if command -v flock >/dev/null 2>&1 && ! flock -w 300 9; then
  echo "Another update, backup or reconcile has held deploy/.reconcile.lock for 5 minutes; try again when it ends." >&2
  exit 1
fi

# In core mode the web app, the portal and the monitor are not part of the stack, so they are neither pulled nor checked.
if [[ "$(grep -E '^DXOS_MODE=' .env | tail -n1 | cut -d= -f2-)" == core ]]; then
  SERVICES=(backend keycloak)
else
  SERVICES=(backend monitor frontend portal keycloak)
fi
BEHIND_GATEWAY=("${SERVICES[@]}")
declare -A BEFORE
for service in "${BEHIND_GATEWAY[@]}"; do
  BEFORE[$service]="$(docker compose ps -q "$service")"
done

echo "==> Checking the gateway routes"
docker compose run --rm --no-deps apisix-config --check

echo "==> Pulling images"
for service in "${SERVICES[@]}"; do
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

# Keycloak 26.8 refuses Admin API calls from a client that lacks this scope; a realm imported earlier does not have it.
echo "==> Checking the Keycloak admin client"
./keycloak-admin-scope.sh || echo "WARNING: model apply stays refused until this works; run ./keycloak-admin-scope.sh once Keycloak is up." >&2

docker image prune -f >/dev/null
echo "==> Up to date. Check: docker compose ps"
