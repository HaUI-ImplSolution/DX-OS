#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Gives the client dxos-backend-admin the client scope kc-admin-api, which puts the audience of the Admin API into its token.
#
#   ./keycloak-admin-scope.sh
#
# From Keycloak 26.8 the Admin API refuses a token without that audience, and every model apply then fails with 502.
# A new realm gets the scope from sso/realm-dxos.json; a realm imported by an earlier release has the scope after the
# upgrade but not on the client. update.sh runs this script; running it again changes nothing.
# It signs in with the bootstrap administrator that the Keycloak container already has in its environment,
# so no secret is read or printed here, and it touches only this one client.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

docker compose exec -T keycloak bash -s <<'INSIDE'
set -euo pipefail
kcadm=/opt/keycloak/bin/kcadm.sh
config=/tmp/kcadm-admin-scope.config
trap 'rm -f "$config"' EXIT

signed_in=false
for _ in $(seq 1 60); do
  if "$kcadm" config credentials --config "$config" --server http://localhost:8080 --realm master \
      --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1; then
    signed_in=true
    break
  fi
  sleep 3
done
if [[ "$signed_in" != true ]]; then
  echo "Keycloak did not accept the sign-in of the bootstrap administrator." >&2
  echo "Add the scope by hand: Clients > dxos-backend-admin > Client scopes > Add client scope > kc-admin-api > Default." >&2
  exit 1
fi

scope="$("$kcadm" get client-scopes -r dxos --config "$config" --fields id,name --format csv --noquotes | awk -F, '$2 == "kc-admin-api" {print $1}')"
if [[ -z "$scope" ]]; then
  echo "This Keycloak has no kc-admin-api scope (older than 26.8): nothing to do."
  exit 0
fi
client="$("$kcadm" get clients -r dxos --config "$config" -q clientId=dxos-backend-admin --fields id --format csv --noquotes)"
if [[ -z "$client" ]]; then
  echo "The client dxos-backend-admin does not exist in the realm dxos." >&2
  exit 1
fi
if "$kcadm" get "clients/$client/default-client-scopes" -r dxos --config "$config" --fields name --format csv --noquotes | grep -qx kc-admin-api; then
  echo "dxos-backend-admin already has the scope kc-admin-api."
else
  "$kcadm" update "clients/$client/default-client-scopes/$scope" -r dxos --config "$config" -n
  echo "Added the scope kc-admin-api to dxos-backend-admin."
fi
INSIDE
