#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Clears the demo so it can be run again from the start.
#
#   ./reset.sh [--yes] [--keep-dxos]
#
# The store: stops it, deletes its container, image, volume and .env, and takes the two lines that
# connect it to DX-OS (env_file and extra_hosts) out of docker-compose.yml.
# DX-OS: removes it with every model, account, record and file (dxos uninstall --purge), so the demo
# starts again at the installation. With --keep-dxos DX-OS stays and only the two accounts of the
# store are removed; its models and people stay too.
# DESTRUCTIVE: asks for confirmation unless --yes is given.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

ASSUME_YES=false
KEEP_DXOS=false
for arg in "$@"; do
  case "$arg" in
    --yes | -y) ASSUME_YES=true ;;
    --keep-dxos) KEEP_DXOS=true ;;
    -h | --help) sed -n '5,13p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "reset: unknown option $arg" >&2; exit 2 ;;
  esac
done

if [[ "$ASSUME_YES" != true ]]; then
  echo "This deletes the data, the image and the .env of the store in $(pwd)."
  if [[ "$KEEP_DXOS" == true ]]; then
    echo "DX-OS stays; the accounts retail-customer and warranty-requester are removed."
  else
    echo "It also removes DX-OS with every model, account, record and file."
  fi
  read -r -p "Type 'reset' to continue: " answer
  [[ "$answer" == reset ]] || { echo "Aborted."; exit 1; }
fi

echo "==> The store"
docker compose down --volumes --remove-orphans --rmi local
rm -f .env
sed -i -e '/^    env_file: \.env$/d' -e '/^    extra_hosts: \["host.docker.internal:host-gateway"\]$/d' docker-compose.yml

echo "==> DX-OS"
if ! command -v dxos >/dev/null 2>&1; then
  echo "The dxos command is not found; nothing to remove there."
elif [[ "$KEEP_DXOS" == true ]]; then
  for name in retail-customer warranty-requester; do
    dxos client remove "$name" >/dev/null 2>&1 && echo "Removed the account $name." || echo "The account $name was not there."
  done
else
  dxos uninstall --purge --yes
fi

echo "Done. Start again at step 1 of README.md."
