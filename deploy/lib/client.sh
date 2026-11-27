#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# The commands of dxos for outside applications: client, client browser, client test and connect.
# Sourced by dxos, which provides DEPLOY_DIR, die, env_value, compose, set_env, from_caller, api, pretty, json_field, json_text,
# public_url, dxos_validate_web_url and set_project.

# Runs one browser-client operation inside Keycloak without publishing its Admin API.
browser_client_keycloak() {
  compose exec -T "$@" keycloak bash -s <<'EOF'
set -euo pipefail
config=/tmp/dxos-browser-client.config
definition=/tmp/dxos-browser-client.json
trap 'rm -f "$config" "$definition"' EXIT
kc() { /opt/keycloak/bin/kcadm.sh "$@" --config "$config"; }
kc config credentials --server http://localhost:8080 --realm master \
  --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1 \
  || { echo "dxos: cannot sign in to Keycloak as the bootstrap administrator" >&2; exit 3; }

case "$ACTION" in
  add)
    if [[ -n "$(kc get clients -r dxos -q clientId="$CLIENT_ID" --fields id --format csv --noquotes)" ]]; then
      echo "dxos: the browser client $CLIENT_ID already exists" >&2
      exit 4
    fi
    json_array() {
      local prefix="$1" count="$2" index variable first=true
      printf '['
      for ((index = 0; index < count; index++)); do
        variable="${prefix}_$index"
        $first || printf ','
        printf '"%s"' "${!variable}"
        first=false
      done
      printf ']'
    }
    redirects="$(json_array REDIRECT "$REDIRECT_COUNT")"
    origins="$(json_array ORIGIN "$ORIGIN_COUNT")"
    logout=""
    for ((index = 0; index < REDIRECT_COUNT; index++)); do
      variable="REDIRECT_$index"
      logout+="${logout:+##}${!variable}"
    done
    cat >"$definition" <<JSON
{
  "clientId": "$CLIENT_ID",
  "name": "DX-OS browser $CLIENT_NAME",
  "protocol": "openid-connect",
  "publicClient": true,
  "bearerOnly": false,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "consentRequired": false,
  "fullScopeAllowed": true,
  "redirectUris": $redirects,
  "webOrigins": $origins,
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "$logout"
  },
  "protocolMappers": [
    {
      "name": "dxos-api-audience",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-audience-mapper",
      "consentRequired": false,
      "config": {
        "included.custom.audience": "dxos-api",
        "id.token.claim": "false",
        "access.token.claim": "true"
      }
    },
    {
      "name": "groups",
      "protocol": "openid-connect",
      "protocolMapper": "oidc-group-membership-mapper",
      "consentRequired": false,
      "config": {
        "full.path": "false",
        "id.token.claim": "false",
        "access.token.claim": "true",
        "claim.name": "groups",
        "userinfo.token.claim": "false"
      }
    }
  ],
  "defaultClientScopes": ["basic", "web-origins", "acr", "profile", "roles", "email"]
}
JSON
    kc create clients -r dxos -f "$definition" >/dev/null
    ;;
  list)
    rows="$(kc get clients -r dxos --fields clientId,enabled --format csv --noquotes \
      | awk -F, '$1 ~ /^dxos-browser-/ {print}' | sort)"
    if [[ -z "$rows" ]]; then
      echo "No browser client is registered."
    else
      printf '%-42s %s\n' CLIENT_ID ENABLED
      while IFS=, read -r client enabled; do printf '%-42s %s\n' "$client" "$enabled"; done <<<"$rows"
    fi
    ;;
  remove)
    id="$(kc get clients -r dxos -q clientId="$CLIENT_ID" --fields id --format csv --noquotes)"
    [[ -n "$id" ]] || { echo "dxos: no browser client called $CLIENT_ID" >&2; exit 4; }
    kc delete "clients/$id" -r dxos >/dev/null
    ;;
  *) echo "dxos: unknown browser-client action: $ACTION" >&2; exit 2 ;;
esac
EOF
}

# Fills CORS_ORIGINS with the normalized origins of CORS_ALLOWED_ORIGINS and stops on an invalid one.
read_cors_env() {
  local configured existing
  CORS_ORIGINS=()
  configured="$(env_value CORS_ALLOWED_ORIGINS)"
  [[ -n "$configured" ]] || return 0
  IFS=',' read -ra configured_origins <<<"$configured"
  for existing in "${configured_origins[@]}"; do
    dxos_validate_web_url "$existing" origin \
      || die "CORS_ALLOWED_ORIGINS is invalid: $DXOS_WEB_URL_ERROR"
    CORS_ORIGINS+=("$DXOS_WEB_URL_ORIGIN")
  done
}

# Adds exact origins without removing entries that may belong to another browser client.
merge_cors_origins() {
  local candidate existing joined changed=false origins=()
  read_cors_env
  origins=("${CORS_ORIGINS[@]:-}")
  [[ -n "${origins[0]:-}" ]] || origins=()
  for candidate in "$@"; do
    for existing in "${origins[@]:-}"; do [[ "$existing" == "$candidate" ]] && continue 2; done
    origins+=("$candidate")
    changed=true
  done
  $changed || return 0
  joined="$(IFS=,; echo "${origins[*]}")"
  set_env CORS_ALLOWED_ORIGINS "$joined"
  if [[ -n "$(compose ps -q --status running backend)" ]]; then
    echo "==> Applying the CORS allowlist"
    recreate_backend >/dev/null
    wait_ready
  fi
}

cmd_browser_client() {
  local action="${1:-}" name client_id redirect origin index duplicate
  local redirects=() origins=() exec_env=()
  case "$action" in
    add)
      shift
      name="${1:-}"
      [[ "$name" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] \
        || die "usage: dxos client browser add <name> --redirect-uri URL [...] (name: lower case letters, digits, - and _)"
      client_id="dxos-browser-$name"
      shift
      while (( $# )); do
        case "$1" in
          --redirect-uri) redirect="${2:?--redirect-uri needs an absolute URL}"; shift 2 ;;
          --redirect-uri=*) redirect="${1#--redirect-uri=}"; shift ;;
          *) die "unknown option: $1" ;;
        esac
        dxos_validate_web_url "$redirect" redirect \
          || die "invalid redirect URI: $DXOS_WEB_URL_ERROR"
        duplicate=false
        for existing in "${redirects[@]:-}"; do [[ "$existing" == "$redirect" ]] && duplicate=true; done
        $duplicate || redirects+=("$redirect")
        origin="$DXOS_WEB_URL_ORIGIN"
        duplicate=false
        for existing in "${origins[@]:-}"; do [[ "$existing" == "$origin" ]] && duplicate=true; done
        $duplicate || origins+=("$origin")
      done
      (( ${#redirects[@]} )) || die "at least one --redirect-uri is required"
      [[ -n "$(compose ps -q --status running keycloak)" ]] || die "Keycloak is not running; run dxos start"
      exec_env=(-e ACTION=add -e "CLIENT_ID=$client_id" -e "CLIENT_NAME=$name"
        -e "REDIRECT_COUNT=${#redirects[@]}" -e "ORIGIN_COUNT=${#origins[@]}")
      for index in "${!redirects[@]}"; do exec_env+=(-e "REDIRECT_$index=${redirects[$index]}"); done
      for index in "${!origins[@]}"; do exec_env+=(-e "ORIGIN_$index=${origins[$index]}"); done
      read_cors_env
      browser_client_keycloak "${exec_env[@]}"
      merge_cors_origins "${origins[@]}"
      cat <<EOF
Registered browser client $client_id:
  authority  $(public_url)/realms/dxos
  client id  $client_id
  flow       Authorization Code with PKCE S256
  redirects  ${redirects[*]}
  CORS       ${origins[*]}
EOF
      ;;
    list)
      (( $# == 1 )) || die "usage: dxos client browser list"
      [[ -n "$(compose ps -q --status running keycloak)" ]] || die "Keycloak is not running; run dxos start"
      browser_client_keycloak -e ACTION=list ;;
    remove)
      (( $# == 2 )) || die "usage: dxos client browser remove <name>"
      name="$2"
      [[ "$name" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not a browser client name: $name"
      [[ -n "$(compose ps -q --status running keycloak)" ]] || die "Keycloak is not running; run dxos start"
      client_id="dxos-browser-$name"
      browser_client_keycloak -e ACTION=remove -e "CLIENT_ID=$client_id"
      echo "Removed $client_id. CORS origins stay in deploy/.env because another client may share them."
      ;;
    *) die "usage: dxos client browser add <name> --redirect-uri URL [...] | list | remove <name>" ;;
  esac
}

# Registers, lists, renews and removes the outside applications of the API; the work is the
# API's, this only calls it.
cmd_client() {
  local action="${1:-}" name model="" role="" body work env_prefix="" project=""
  case "$action" in
    browser)
      shift
      cmd_browser_client "$@" ;;
    list)
      (( $# == 1 )) || die "usage: dxos client list"
      api GET /clients
      pretty "$API_BODY"
      [[ "$API_STATUS" == 200 ]] || return 1 ;;
    add)
      shift
      name="${1:-}"
      [[ "$name" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "usage: dxos client add <name> --model M --role R [--project P] [--env PREFIX] (name: lower case letters, digits, - and _)"
      shift
      while (( $# )); do
        case "$1" in
          --model) model="${2:?--model needs a model name}"; shift 2 ;;
          --model=*) model="${1#--model=}"; shift ;;
          --project) project="${2:?--project needs a project name}"; shift 2 ;;
          --project=*) project="${1#--project=}"; shift ;;
          --role) role="${2:?--role needs a role name}"; shift 2 ;;
          --role=*) role="${1#--role=}"; shift ;;
          --env) env_prefix="${2:?--env needs a prefix such as DXOS_REQUESTER}"; shift 2 ;;
          --env=*) env_prefix="${1#--env=}"; shift ;;
          *) die "unknown option: $1" ;;
        esac
      done
      check_env_prefix "$env_prefix"
      [[ "$model" =~ ^[a-z][a-z0-9_]*$ ]] || die "--model needs the name of an applied model"
      [[ "$role" =~ ^[A-Z][A-Z0-9_]*$ ]] || die "--role needs a role of that model"
      [[ -z "$project" || "$project" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not a project name: $project"
      work="$(mktemp)"
      printf '{"name":"%s","model":"%s","role":"%s"%s}' "$name" "$model" "$role" "${project:+,\"project\":\"$project\"}" >"$work"
      api POST /clients "$work" application/json
      rm -f "$work"
      if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
      print_client_secret "$(json_field clientId "$API_BODY")" "$(json_field secret "$API_BODY")" \
        "role $role of the model $model" "$env_prefix" ;;
    rotate)
      shift
      name="${1:-}"
      [[ "$name" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "usage: dxos client rotate <name> [--env PREFIX]"
      shift
      while (( $# )); do
        case "$1" in
          --env) env_prefix="${2:?--env needs a prefix such as DXOS_REQUESTER}"; shift 2 ;;
          --env=*) env_prefix="${1#--env=}"; shift ;;
          *) die "unknown option: $1" ;;
        esac
      done
      check_env_prefix "$env_prefix"
      api POST "/clients/$name/secret"
      if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
      print_client_secret "$(json_field clientId "$API_BODY")" "$(json_field secret "$API_BODY")" \
        "the old secret no longer works" "$env_prefix" ;;
    test) shift; cmd_client_test "$@" ;;
    role)
      shift
      name="${1:-}"
      [[ "$name" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "usage: dxos client role <name> --role R"
      shift
      while (( $# )); do
        case "$1" in
          --role) role="${2:?--role needs a role name}"; shift 2 ;;
          --role=*) role="${1#--role=}"; shift ;;
          *) die "unknown option: $1" ;;
        esac
      done
      [[ "$role" =~ ^[A-Z][A-Z0-9_]*$ ]] || die "--role needs a role of the model of the application"
      work="$(mktemp)"
      printf '{"role":"%s"}' "$role" >"$work"
      api PUT "/clients/$name" "$work" application/json
      rm -f "$work"
      if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
      echo "dxos-app-$name now holds the role $role; its secret is unchanged and a token it got before lasts until it expires." ;;
    project)
      (( $# == 3 )) || die "usage: dxos client project <name> <project|->"
      [[ "$2" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not an application name: $2"
      set_project "/clients/$2/project" "$3"
      project_text "$3" "dxos-app-$2" ;;
    remove)
      (( $# == 2 )) || die "usage: dxos client remove <name>"
      [[ "$2" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not an application name: $2"
      api DELETE "/clients/$2"
      if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
      echo "Removed dxos-app-$2." ;;
    *) die "usage: dxos client add <name> --model M --role R [--project P] [--env PREFIX] | list | role <name> --role R | rotate <name> [--env PREFIX] | remove <name> | project <name> <project|-> | test <name> | browser add|list|remove" ;;
  esac
}

# An --env prefix is a name an application can use for environment variables.
check_env_prefix() {
  [[ -z "$1" || "$1" =~ ^[A-Z][A-Z0-9_]*$ ]] || die "--env needs a prefix of capital letters, digits and _, such as DXOS_REQUESTER"
}

# The secret of an application, shown once and never kept. With a prefix only the two variable lines
# go to stdout, so they can be appended to the .env file of the application.
print_client_secret() {
  [[ -n "$2" ]] || die "the API answered without a secret"
  if [[ -n "${4:-}" ]]; then
    printf '%s_CLIENT_ID=%s\n%s_CLIENT_SECRET=%s\n' "$4" "$1" "$4" "$2"
    echo "The secret is shown only now: append these lines to the .env file of your application." >&2
    return 0
  fi
  cat <<EOF

Outside application $1 ($3):
  client id  $1
  secret     $2

The secret is shown only now. Get a token with the client credentials grant:
  POST $(public_url)/realms/dxos/protocol/openid-connect/token
  grant_type=client_credentials, client_id=$1, client_secret=<the secret>
EOF
}

# Signs in as an outside application and calls the API of its model, then says which step failed.
# The secret never appears on a command line: it comes from a file, DXOS_CLIENT_SECRET or a prompt.
cmd_client_test() {
  local name="${1:-}" url secret_file="" secret="" work token token_status api_status row model role
  [[ "$name" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "usage: dxos client test <name> [--url URL] [--secret-file FILE]"
  shift
  url="$(public_url)"
  while (( $# )); do
    case "$1" in
      --url) url="${2:?--url needs the address of the gateway}"; shift 2 ;;
      --url=*) url="${1#--url=}"; shift ;;
      --secret-file) secret_file="$(from_caller "${2:?--secret-file needs a file}")"; shift 2 ;;
      --secret-file=*) secret_file="$(from_caller "${1#--secret-file=}")"; shift ;;
      *) die "unknown option: $1" ;;
    esac
  done
  url="${url%/}"
  command -v curl >/dev/null 2>&1 || die "curl is required"
  if [[ -n "$secret_file" ]]; then
    [[ -r "$secret_file" ]] || die "cannot read $secret_file"
    secret="$(<"$secret_file")"
  elif [[ -n "${DXOS_CLIENT_SECRET:-}" ]]; then
    secret="$DXOS_CLIENT_SECRET"
  else
    read -r -s -p "Secret of dxos-app-$name: " secret
    echo
  fi
  [[ -n "$secret" ]] || die "no secret given"

  api GET /clients
  [[ "$API_STATUS" == 200 ]] || { pretty "$API_BODY"; return 1; }
  row="$(tr '{' '\n' <<<"$API_BODY" | grep "\"name\":\"$name\"" | head -n1 || true)"
  [[ -n "$row" ]] || die "no application called $name; dxos client list shows the registered ones"
  model="$(json_field model "$row")"
  role="$(json_field role "$row")"

  work="$(mktemp -d)"
  chmod 700 "$work"
  # shellcheck disable=SC2064 # expand now: the directory is local
  trap "rm -rf '$work'" RETURN
  printf '%s' "$secret" >"$work/secret"

  echo "1. Reaching $url"
  if ! curl -sS -o /dev/null --max-time 10 "$url/realms/dxos/.well-known/openid-configuration" 2>"$work/err"; then
    echo "   FAIL: $(cat "$work/err")"
    echo "   Check the address; from a container use host.docker.internal with extra_hosts host-gateway, or the host name or IP of this machine, not localhost."
    return 1
  fi
  echo "   ok"

  echo "2. Signing in as dxos-app-$name"
  token_status="$(curl -sS -o "$work/token" -w '%{http_code}' --max-time 10 --data-urlencode grant_type=client_credentials \
    --data-urlencode "client_id=dxos-app-$name" --data-urlencode "client_secret@$work/secret" \
    "$url/realms/dxos/protocol/openid-connect/token")" || die "Keycloak did not answer at $url"
  if [[ "$token_status" != 200 ]]; then
    echo "   FAIL: HTTP $token_status $(cat "$work/token")"
    echo "   The client id is dxos-app-$name; a lost or wrong secret is replaced with: dxos client rotate $name"
    return 1
  fi
  token="$(sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p' "$work/token")"
  printf 'Authorization: Bearer %s\n' "$token" >"$work/header"
  echo "   ok"

  echo "3. Calling the API of the model $model with the role $role"
  api_status="$(curl -sS -o "$work/body" -w '%{http_code}' --max-time 20 -H "@$work/header" \
    "$url/api/v1/meta/models/$model/openapi")" || die "the API did not answer at $url"
  if [[ "$api_status" != 200 ]]; then
    echo "   FAIL: HTTP $api_status"
    echo "   The model $model may not be applied (dxos plan <file>, then dxos apply <file>), or the role $role has no access to it."
    return 1
  fi
  echo "   ok"
  echo "dxos-app-$name works: role $role of the model $model."
}

# Warns when other accounts of this machine can read a file of secrets.
# A Windows drive mounted in WSL without metadata ignores chmod and shows mode 777.
warn_env_mode() {
  local mode
  mode="$(stat -c %a "$1" 2>/dev/null)" || return 0
  [[ "${mode: -2}" == 00 ]] && return 0
  echo "dxos: $1 has mode $mode, so other accounts of this machine may read its secrets; on a Windows drive mounted in WSL (/mnt/c) chmod has no effect, so keep the application in the WSL file system (for example under ~) or mount the drive with the metadata option" >&2
}

# Connects an application: registers one client for each role it uses and writes DXOS_BASE_URL and the
# variables of the clients to its .env file. The secret the API shows once goes straight to the file.
cmd_connect() {
  local usage="usage: dxos connect <model> --role R [--role R ...] [--app NAME] [--project P] [--prefix PREFIX] [--env-file FILE] [--base-url URL] [--rotate]"
  local model="${1:-}" app="" prefix="DXOS" env_file="" base_url="" rotate=false roles=() project=""
  local role name var_prefix clients exists work port client_id secret test_name="" test_secret="" wrote=0
  [[ "$model" =~ ^[a-z][a-z0-9_]*$ ]] || die "$usage"
  shift
  while (( $# )); do
    case "$1" in
      --role) roles+=("${2:?--role needs a role name}"); shift 2 ;;
      --role=*) roles+=("${1#--role=}"); shift ;;
      --app) app="${2:?--app needs a name}"; shift 2 ;;
      --app=*) app="${1#--app=}"; shift ;;
      --project) project="${2:?--project needs a project name}"; shift 2 ;;
      --project=*) project="${1#--project=}"; shift ;;
      --prefix) prefix="${2:?--prefix needs a prefix such as DXOS_RETAIL}"; shift 2 ;;
      --prefix=*) prefix="${1#--prefix=}"; shift ;;
      --env-file) env_file="$(from_caller "${2:?--env-file needs a file}")"; shift 2 ;;
      --env-file=*) env_file="$(from_caller "${1#--env-file=}")"; shift ;;
      --base-url) base_url="${2:?--base-url needs the address of the gateway}"; shift 2 ;;
      --base-url=*) base_url="${1#--base-url=}"; shift ;;
      --rotate) rotate=true; shift ;;
      *) die "unknown option: $1 ($usage)" ;;
    esac
  done
  (( ${#roles[@]} )) || die "at least one --role is required ($usage)"
  check_env_prefix "$prefix"
  [[ -z "$project" || "$project" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "not a project name: $project"
  app="${app:-$model}"
  [[ -n "$env_file" ]] || env_file="$(from_caller .env)"
  if [[ -z "$base_url" ]]; then
    port="$(env_value APISIX_HOST_PORT)"
    port="${port##*:}"
    base_url="http://host.docker.internal:${port:-9080}"
  fi
  for role in "${roles[@]}"; do
    [[ "$role" =~ ^[A-Z][A-Z0-9_]*$ ]] || die "not a role name: $role"
    name="${app}-${role,,}"
    [[ "$name" =~ ^[a-z][a-z0-9_-]{0,39}$ ]] || die "the client name $name is too long or has a character that is not allowed; give a shorter --app"
  done

  api GET /clients
  [[ "$API_STATUS" == 200 ]] || { pretty "$API_BODY"; return 1; }
  clients="$API_BODY"

  [[ -e "$env_file" ]] || (umask 077; : >"$env_file")
  [[ ! -s "$env_file" || -z "$(tail -c1 "$env_file")" ]] || echo >>"$env_file"
  if ! grep -qE '^DXOS_BASE_URL=' "$env_file"; then
    printf 'DXOS_BASE_URL=%s\n' "$base_url" >>"$env_file" || die "cannot write $env_file"
  fi

  for role in "${roles[@]}"; do
    name="${app}-${role,,}"
    var_prefix="${prefix}_${role}"
    if grep -qE "^${var_prefix}_CLIENT_SECRET=." "$env_file"; then
      echo "dxos-app-$name: $env_file already holds its secret, left as it is"
      continue
    fi
    exists="$(tr '{' '\n' <<<"$clients" | grep "\"name\":\"$name\"" | head -n1 || true)"
    if [[ -n "$exists" ]]; then
      $rotate || die "dxos-app-$name is registered but $env_file has no secret for it; run again with --rotate to give it a new secret (the old one stops working)"
      api POST "/clients/$name/secret"
    else
      work="$(mktemp)"
      printf '{"name":"%s","model":"%s","role":"%s"%s}' "$name" "$model" "$role" "${project:+,\"project\":\"$project\"}" >"$work"
      api POST /clients "$work" application/json
      rm -f "$work"
    fi
    [[ "$API_STATUS" == 200 ]] || { pretty "$API_BODY"; return 1; }
    client_id="$(json_field clientId "$API_BODY")"
    secret="$(json_field secret "$API_BODY")"
    [[ -n "$client_id" && -n "$secret" ]] || die "the API answered without a secret"
    printf '%s_CLIENT_ID=%s\n%s_CLIENT_SECRET=%s\n' "$var_prefix" "$client_id" "$var_prefix" "$secret" >>"$env_file" \
      || die "cannot write $env_file; run dxos connect again with --rotate to give dxos-app-$name a new secret"
    echo "dxos-app-$name: role $role of $model, written as ${var_prefix}_CLIENT_ID and ${var_prefix}_CLIENT_SECRET"
    wrote=$((wrote + 1))
    if [[ -z "$test_name" ]]; then test_name="$name"; test_secret="$secret"; fi
  done

  if [[ -n "$test_name" ]]; then
    echo "Checking dxos-app-$test_name"
    DXOS_CLIENT_SECRET="$test_secret" cmd_client_test "$test_name" || echo "dxos: the check failed; the variables are written, see the step above" >&2
  fi
  warn_env_mode "$env_file"
  if (( wrote == 0 )); then echo "Nothing new to write."; return 0; fi
  cat <<EOF

Written to $env_file ($wrote new client(s)); keep this file out of git.
Give the service of your application these two lines in its docker-compose.yml:
    env_file: $(basename "$env_file")
    extra_hosts: ["host.docker.internal:host-gateway"]
The address in DXOS_BASE_URL is $(grep -E '^DXOS_BASE_URL=' "$env_file" | tail -n1 | cut -d= -f2-); use the address of this machine instead when the application runs elsewhere.
EOF
}
