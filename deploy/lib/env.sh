#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Reads an env file. The caller sets ENV_VALUE_FILE to the file before calling env_value or public_url.

# The value of the last assignment of a key as Compose reads it: without a trailing carriage return, the
# surrounding quotes or a comment after the value. Empty when the key or the file is missing.
env_value() {
  local value
  [[ -f "$ENV_VALUE_FILE" ]] || return 0
  value="$(grep -E "^$1=" "$ENV_VALUE_FILE" | tail -n1 | cut -d= -f2- || true)"
  value="${value%$'\r'}"
  value="${value#"${value%%[![:space:]]*}"}"
  case "$value" in
    '"'*'"'*) value="${value#?}"; value="${value%%'"'*}" ;;
    "'"*"'"*) value="${value#?}"; value="${value%%"'"*}" ;;
    *)
      value="${value%%' #'*}"
      value="${value%"${value##*[![:space:]]}"}" ;;
  esac
  printf '%s\n' "$value"
}

# The address browsers use: GATEWAY_PUBLIC_URL with PUBLIC_HOST and APISIX_HOST_PORT filled in. It is
# built from those two when it is blank or refers to any other variable.
public_url() {
  local url host port name
  url="$(env_value GATEWAY_PUBLIC_URL)"
  host="$(env_value PUBLIC_HOST)"
  port="$(env_value APISIX_HOST_PORT)"
  host="${host:-localhost}"
  port="${port##*:}"
  port="${port:-9080}"
  name='${PUBLIC_HOST}'
  url="${url//"$name"/$host}"
  name='${APISIX_HOST_PORT}'
  url="${url//"$name"/$port}"
  [[ -n "$url" && "$url" != *'${'* ]] || url="http://$host:$port"
  echo "${url%/}"
}
