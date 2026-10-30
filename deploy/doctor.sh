#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Read-only check of this machine and deploy/.env before the stack is started or updated.
#
#   ./doctor.sh
#
# Prints OK, WARN or FAIL per check and exits 1 when any check fails. Values of .env are never printed.
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

FAILS=0
WARNS=0
ok()   { printf '  [ OK ] %s\n' "$*"; }
warn() { printf '  [WARN] %s\n' "$*"; WARNS=$((WARNS + 1)); }
fail() { printf '  [FAIL] %s\n' "$*"; FAILS=$((FAILS + 1)); }

# Value of KEY in .env (last assignment), empty when absent or blank.
env_value() { grep -E "^$1=" .env | tail -n1 | cut -d= -f2-; }
# Port number of a *_HOST_PORT value such as 9080 or 127.0.0.1:9082.
port_of() { printf '%s' "${1##*:}"; }

echo "Docker"
if ! command -v docker >/dev/null 2>&1; then
  fail "docker is not installed"
elif ! docker info >/dev/null 2>&1; then
  fail "the Docker daemon is not reachable by this user"
else
  version="$(docker compose version --short 2>/dev/null | sed 's/^v//')"
  if [[ -z "$version" ]]; then
    fail "Docker Compose v2 is missing (docker compose version)"
  else
    major="${version%%.*}"; rest="${version#*.}"; minor="${rest%%.*}"
    if (( major < 2 )); then
      fail "Docker Compose $version is too old, v2 is required"
    elif (( major == 2 && minor < 24 )); then
      warn "Docker Compose $version: the E2E overlay (demo/docker-compose.e2e.yml) needs 2.24 or newer"
    else
      ok "Docker Compose $version"
    fi
  fi
fi

echo "Machine"
mem_total_mib=$(( $(awk '/^MemTotal:/ {print $2}' /proc/meminfo) / 1024 ))
mem_free_mib=$(( $(awk '/^MemAvailable:/ {print $2}' /proc/meminfo) / 1024 ))
if (( mem_total_mib < 7500 )); then warn "RAM ${mem_total_mib} MiB, 8 GB is the minimum"; else ok "RAM ${mem_total_mib} MiB, ${mem_free_mib} MiB available"; fi
if (( mem_free_mib < 1500 )); then warn "only ${mem_free_mib} MiB of RAM is available"; fi
disk_free_gib=$(( $(df -Pk . | awk 'NR==2 {print $4}') / 1048576 ))
if (( disk_free_gib < 40 )); then warn "${disk_free_gib} GiB of disk is free here, 40 GiB is recommended"; else ok "${disk_free_gib} GiB of disk is free"; fi

echo "deploy/.env"
if [[ ! -f .env ]]; then
  fail ".env not found: run python3 demo/make_ci_env.py .env (random secrets), or cp .env.example .env and fill every blank variable"
else
  compose_files=(docker-compose.yml)
  for f in $(env_value COMPOSE_FILE | tr ':' ' '); do [[ " ${compose_files[*]} " == *" $f "* ]] || compose_files+=("$f"); done
  # Variables a compose file cannot start without: ${NAME:?}
  required="$(cat "${compose_files[@]}" 2>/dev/null | grep -vE '^[[:space:]]*#' | grep -oE '\$\{[A-Z0-9_]+:\?' | sed -E 's/^\$\{//; s/:\?$//' | sort -u)"
  missing=(); blank=()
  for key in $required; do
    if ! grep -qE "^$key=" .env; then missing+=("$key")
    elif [[ -z "$(env_value "$key")" ]]; then blank+=("$key"); fi
  done
  if (( ${#missing[@]} )); then fail "missing in .env: ${missing[*]}"; fi
  if (( ${#blank[@]} )); then fail "blank in .env: ${blank[*]}"; fi
  if (( ${#missing[@]} + ${#blank[@]} == 0 )); then ok "every required variable is set ($(wc -w <<<"$required" | tr -d ' ') checked)"; fi

  known="$( { grep -oE '^[A-Z][A-Z0-9_]*=' .env.example; grep -oE '^# [A-Z][A-Z0-9_]*=' .env.example | sed 's/^# //'; } | tr -d '=' | sort -u)"
  unknown=()
  while IFS= read -r key; do
    grep -qxF "$key" <<<"$known" || unknown+=("$key")
  done < <(grep -oE '^[A-Z][A-Z0-9_]*=' .env | tr -d '=' | sort -u)
  if (( ${#unknown[@]} )); then
    warn "in .env but not in .env.example (renamed or unused?): ${unknown[*]}"
    if printf '%s\n' "${unknown[@]}" | grep -q 'MINIO'; then
      warn "MINIO_* variables are now S3_*, APISIX_MINIO_HOST_PORT is APISIX_STORAGE_ADMIN_HOST_PORT and PORTAL_MINIO_URL is PORTAL_STORAGE_URL"
    fi
  else
    ok "no variable outside .env.example"
  fi

  app="$(env_value DXOS_APP)"
  if [[ -n "$app" && "$app" != farm && "$app" != lab ]]; then fail "DXOS_APP must be farm or lab"; fi
  if [[ "$(env_value COMPOSE_FILE)" == *cloudflared* && -z "$(env_value CLOUDFLARE_TUNNEL_TOKEN)" ]]; then
    fail "COMPOSE_FILE includes the cloudflared overlay but CLOUDFLARE_TUNNEL_TOKEN is blank"
  fi

  if docker compose config --quiet >/dev/null 2>&1; then ok "docker compose config is valid"; else fail "docker compose config fails: run it in deploy/ to see why"; fi

  echo "Ports"
  if [[ -n "$(docker compose ps -q apisix 2>/dev/null)" ]]; then
    ok "the stack is running, port checks skipped"
  else
    for key in APISIX_HOST_PORT APISIX_SUPERSET_HOST_PORT APISIX_STORAGE_ADMIN_HOST_PORT MATTERMOST_HOST_PORT POSTGRES_HOST_PORT; do
      value="$(env_value "$key")"
      [[ -n "$value" ]] || continue
      port="$(port_of "$value")"
      if (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; then fail "$key: port $port is already in use"; else ok "$key: port $port is free"; fi
    done
  fi
fi

echo
if (( FAILS )); then echo "$FAILS check(s) failed, $WARNS warning(s)."; exit 1; fi
echo "No failures, $WARNS warning(s)."
