#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Validation shared by dxos and doctor.sh for browser redirect URIs and CORS origins.

DXOS_WEB_URL_ERROR=""
DXOS_WEB_URL_ORIGIN=""

# Validates an exact absolute web URL. KIND is "origin" or "redirect"; the normalized origin is
# returned in DXOS_WEB_URL_ORIGIN and a short reason is returned in DXOS_WEB_URL_ERROR.
dxos_validate_web_url() {
  local value="$1" kind="$2" scheme rest authority suffix host port lower_host label
  DXOS_WEB_URL_ERROR=""
  DXOS_WEB_URL_ORIGIN=""

  if [[ -z "$value" || "$value" =~ [[:space:]] ]]; then
    DXOS_WEB_URL_ERROR="the value is empty or contains whitespace"
    return 1
  fi
  if [[ "$value" == *'*'* || "$value" == *'#'* || "$value" == *'\\'* \
      || "$value" == *'"'* || "$value" == *"'"* || "$value" == *'<'* \
      || "$value" == *'>'* || "$value" == *'{'* || "$value" == *'}'* \
      || "$value" == *'|'* || "$value" == *'^'* || "$value" == *'`'* ]]; then
    DXOS_WEB_URL_ERROR="the URL contains a wildcard, fragment or unsafe character"
    return 1
  fi
  case "$value" in
    https://*) scheme=https ;;
    http://*) scheme=http ;;
    *) DXOS_WEB_URL_ERROR="the scheme must be http or https"; return 1 ;;
  esac

  rest="${value#*://}"
  authority="${rest%%/*}"
  authority="${authority%%\?*}"
  suffix="${rest#"$authority"}"
  if [[ -z "$authority" || "$authority" == *@* ]]; then
    DXOS_WEB_URL_ERROR="a host without user information is required"
    return 1
  fi

  port=""
  if [[ "$authority" == \[* ]]; then
    if [[ ! "$authority" =~ ^(\[::1\])(:([0-9]+))?$ ]]; then
      DXOS_WEB_URL_ERROR="only the loopback IPv6 address [::1] is supported"
      return 1
    fi
    host="${BASH_REMATCH[1]}"
    port="${BASH_REMATCH[3]}"
  else
    if [[ ! "$authority" =~ ^([A-Za-z0-9.-]+)(:([0-9]+))?$ ]]; then
      DXOS_WEB_URL_ERROR="the host or port is invalid"
      return 1
    fi
    host="${BASH_REMATCH[1]}"
    port="${BASH_REMATCH[3]}"
    if [[ "$host" == .* || "$host" == *. || "$host" == *..* ]]; then
      DXOS_WEB_URL_ERROR="the host is invalid"
      return 1
    fi
    IFS='.' read -ra labels <<<"$host"
    for label in "${labels[@]}"; do
      if [[ ! "$label" =~ ^([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9-]{0,61}[A-Za-z0-9])$ ]]; then
        DXOS_WEB_URL_ERROR="the host is invalid"
        return 1
      fi
    done
  fi

  if [[ -n "$port" ]] && (( 10#$port < 1 || 10#$port > 65535 )); then
    DXOS_WEB_URL_ERROR="the port must be between 1 and 65535"
    return 1
  fi
  if [[ "$kind" == origin && -n "$suffix" ]]; then
    DXOS_WEB_URL_ERROR="an origin must not contain a path or query"
    return 1
  fi
  if [[ "$kind" != origin && "$kind" != redirect ]]; then
    DXOS_WEB_URL_ERROR="the URL kind is invalid"
    return 1
  fi

  lower_host="${host,,}"
  if [[ "$scheme" == http && "$lower_host" != localhost && "$lower_host" != 127.0.0.1 \
      && "$lower_host" != '[::1]' ]]; then
    DXOS_WEB_URL_ERROR="http is allowed only for localhost or a loopback address"
    return 1
  fi
  DXOS_WEB_URL_ORIGIN="$scheme://${lower_host}${port:+:$port}"
}
