#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Creates what the two processes need in DX-OS besides the models and the client credentials:
# the accounts of the manager (STORE_STAFF of retail) and the technician (AGENT of warranty), and
# the products of the catalog, because the return request of the template refers to a product.
#
#   setup/setup.sh [--deploy-dir DIR] [--manager NAME] [--technician NAME]
#
# Run it by the person who runs DX-OS, after `dxos apply` of both models. It signs in with the ADMIN
# client dxos-cli of that installation, which never reaches the application. The temporary passwords
# are printed once; a person changes theirs at the first sign-in. Running it again creates nothing twice.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="${DXOS_DEPLOY_DIR:-$HOME/.dxos/deploy}"
MANAGER="manager"
TECHNICIAN="technician"

die() { echo "setup: $*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-dir) DEPLOY_DIR="${2:?--deploy-dir needs a directory}"; shift 2 ;;
    --manager) MANAGER="${2:?--manager needs a name}"; shift 2 ;;
    --technician) TECHNICIAN="${2:?--technician needs a name}"; shift 2 ;;
    -h | --help) sed -n 4,12p "${BASH_SOURCE[0]}"; exit 0 ;;
    *) die "unknown argument $1" ;;
  esac
done
for name in "$MANAGER" "$TECHNICIAN"; do
  [[ "$name" =~ ^[a-z][a-z0-9_.-]{1,39}$ ]] || die "the username $name must be 2 to 40 characters: a-z, 0-9, _ . -"
done
command -v curl >/dev/null 2>&1 || die "curl is required"
ENV_FILE="$DEPLOY_DIR/.env"
[[ -f "$ENV_FILE" ]] || die "$ENV_FILE is missing; set --deploy-dir or DXOS_DEPLOY_DIR to the DX-OS installation"
CATALOG="$HERE/../catalog/products.tsv"
[[ -f "$CATALOG" ]] || die "$CATALOG is missing"

env_value() { grep -E "^$1=" "$ENV_FILE" | tail -n1 | cut -d= -f2- || true; }

# The address of DX-OS as the dxos command uses it.
url="$(env_value GATEWAY_PUBLIC_URL)"
# shellcheck disable=SC2016 # a literal "${" marks a value Compose interpolates
if [[ -z "$url" || "$url" == *'${'* ]]; then
  host="$(env_value PUBLIC_HOST)"
  port="$(env_value APISIX_HOST_PORT)"
  url="http://${host:-localhost}:${port##*:}"
fi
BASE="${url%/}"

WORK="$(mktemp -d)"
chmod 700 "$WORK"
trap 'rm -rf "$WORK"' EXIT

secret="$(env_value DXOS_CLI_SECRET)"
[[ -n "$secret" ]] || die "DXOS_CLI_SECRET is empty in $ENV_FILE"
printf '%s' "$secret" >"$WORK/secret"
curl -sS -o "$WORK/token" --data-urlencode grant_type=client_credentials --data-urlencode client_id=dxos-cli \
  --data-urlencode "client_secret@$WORK/secret" "$BASE/realms/dxos/protocol/openid-connect/token" \
  || die "Keycloak did not answer at $BASE"
token="$(sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' "$WORK/token")"
[[ -n "$token" ]] || die "Keycloak refused the client dxos-cli: $(cat "$WORK/token")"
printf 'Authorization: Bearer %s\n' "$token" >"$WORK/admin.header"
HEADER="$WORK/admin.header"

# api METHOD PATH [JSON]: the status goes to STATUS and the body to BODY; HEADER names the token file.
api() {
  local args=()
  [[ -n "${3:-}" ]] && args=(-H "Content-Type: application/json" --data-binary "$3")
  STATUS="$(curl -sS -o "$WORK/body" -w '%{http_code}' -X "$1" -H "@$HEADER" ${args[@]+"${args[@]}"} "$BASE$2")" \
    || die "DX-OS did not answer at $BASE"
  BODY="$(cat "$WORK/body")"
}
field() { sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\).*/\1/p" <<<"$2" | head -n1; }
json_escape() { sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' <<<"$1"; }

# ensure_account USERNAME "Full name" MODEL ROLE
ensure_account() {
  local username="$1" full="$2" model="$3" role="$4" id password=""
  api POST /api/v1/users "{\"username\":\"$username\",\"fullName\":\"$full\",\"email\":\"$username@example.org\"}"
  case "$STATUS" in
    200 | 201)
      id="$(field id "$BODY")"
      password="$(field temporaryPassword "$BODY")" ;;
    409)
      api GET "/api/v1/users?search=$username&size=100"
      id="$(grep -o "\"id\":\"[^\"]*\",\"username\":\"$username\"" <<<"$BODY" | head -n1 | sed 's/^"id":"\([^"]*\)".*/\1/')"
      [[ -n "$id" ]] || die "the account $username exists but is not found; its username or email is taken by another account" ;;
    *) die "creating $username: HTTP $STATUS $BODY" ;;
  esac
  [[ -n "$id" ]] || die "no id for $username: $BODY"
  api PUT "/api/v1/users/$id/roles/$model/$role"
  [[ "$STATUS" == 200 || "$STATUS" == 204 ]] \
    || die "giving $role of $model to $username: HTTP $STATUS $BODY (is the model $model applied?)"
  if [[ -n "$password" ]]; then
    echo "account $username: $role of $model, temporary password $password"
  else
    echo "account $username: $role of $model (existed, password unchanged)"
  fi
}

ensure_account "$MANAGER" "Quản lý cửa hàng" retail STORE_STAFF
ensure_account "$TECHNICIAN" "Kỹ thuật viên" warranty AGENT

# The data API gives no access to an ADMIN token, so the products are loaded by a client of the role
# STORE_STAFF that lives only for this loop and is removed on exit.
SETUP_CLIENT="appliance-setup"
SETUP_CLIENT_MADE=false
cleanup() {
  if $SETUP_CLIENT_MADE; then
    HEADER="$WORK/admin.header"
    api DELETE "/api/v1/clients/$SETUP_CLIENT" >/dev/null 2>&1 || true
    [[ "$STATUS" == 200 || "$STATUS" == 204 ]] \
      || echo "setup: remove the client $SETUP_CLIENT with: dxos client remove $SETUP_CLIENT" >&2
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT

api GET /api/v1/clients
[[ "$STATUS" == 200 ]] || die "listing the clients: HTTP $STATUS $BODY"
if grep -q "\"name\":\"$SETUP_CLIENT\"" <<<"$BODY"; then
  die "the client $SETUP_CLIENT is left from an earlier run; remove it with: dxos client remove $SETUP_CLIENT"
fi
api POST /api/v1/clients "{\"name\":\"$SETUP_CLIENT\",\"model\":\"retail\",\"role\":\"STORE_STAFF\"}"
[[ "$STATUS" == 200 || "$STATUS" == 201 ]] || die "creating the client $SETUP_CLIENT: HTTP $STATUS $BODY"
SETUP_CLIENT_MADE=true
client_id="$(field clientId "$BODY")"
printf '%s' "$(field secret "$BODY")" >"$WORK/secret"
curl -sS -o "$WORK/token" --data-urlencode grant_type=client_credentials --data-urlencode "client_id=$client_id" \
  --data-urlencode "client_secret@$WORK/secret" "$BASE/realms/dxos/protocol/openid-connect/token" \
  || die "Keycloak did not answer at $BASE"
token="$(sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' "$WORK/token")"
[[ -n "$token" ]] || die "Keycloak refused the client $client_id: $(cat "$WORK/token")"
printf 'Authorization: Bearer %s\n' "$token" >"$WORK/client.header"
HEADER="$WORK/client.header"

# The code of a product is unique, so a product loaded before is found by its code and left alone.
made=0
kept=0
while IFS=$'\t' read -r code name; do
  [[ -n "$code" ]] || continue
  api GET "/api/v1/data/retail/product?filter.code=$code"
  [[ "$STATUS" == 200 ]] || die "looking for product $code: HTTP $STATUS $BODY"
  if grep -q "\"code\":\"$code\"" <<<"$BODY"; then
    kept=$((kept + 1))
    continue
  fi
  api POST /api/v1/data/retail/product "{\"code\":\"$(json_escape "$code")\",\"name\":\"$(json_escape "$name")\"}"
  [[ "$STATUS" == 200 || "$STATUS" == 201 ]] || die "loading product $code: HTTP $STATUS $BODY"
  made=$((made + 1))
done <"$CATALOG"
echo "products: $made loaded, $kept already there"
