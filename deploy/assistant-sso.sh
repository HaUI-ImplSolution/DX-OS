#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Installs only the assistant's public PKCE client using the operator's Keycloak access.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
file=data/assistant/private/oidc-client.json
[[ -f "$file" ]] || { echo "Run dxos assistant prepare first." >&2; exit 1; }
docker compose cp "$file" keycloak:/tmp/dxos-assistant-client.json
docker compose exec -T keycloak bash -s <<'INSIDE'
set -euo pipefail
config="$(mktemp /tmp/kcadm-assistant.XXXXXX)"
trap 'rm -f "$config" /tmp/dxos-assistant-client.json' EXIT
kc() { /opt/keycloak/bin/kcadm.sh "$@" --config "$config"; }
if ! kc config credentials --server http://localhost:8080 --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1; then
  echo "Keycloak did not accept the operator sign-in; the assistant client was not changed." >&2; exit 1
fi
client="$(kc get clients -r dxos -q clientId=dxos-assistant --fields id --format csv --noquotes)"
if [[ -n "$client" ]]; then
  attributes="$(kc get "clients/$client" -r dxos)"
  if ! grep -Eq '"dxos.assistant.managed"[[:space:]]*:[[:space:]]*"true"' <<< "$attributes"; then
    echo "An unmanaged dxos-assistant client already exists; review it before proceeding." >&2; exit 1
  fi
  kc update "clients/$client" -r dxos -f /tmp/dxos-assistant-client.json >/dev/null
else
  kc create clients -r dxos -f /tmp/dxos-assistant-client.json >/dev/null
fi
echo "Assistant PKCE client is ready; business users, roles and model clients were not changed."
INSIDE
