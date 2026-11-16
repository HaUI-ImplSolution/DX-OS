#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Quiesces and snapshots assistant state for backup; deployment uses the main compose file.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
STATE="$PWD/data/assistant"
services=(assistant-control)
die() { echo "assistant backup: $*" >&2; exit 1; }
compose() { docker compose --env-file "${ENV_FILE:-.env}" "$@"; }
retire_legacy() {
  local project service
  project=$(compose config --format json | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')
  for service in assistant-runtime-1 assistant-runtime-2 assistant-runtime-3; do
    local ids=()
    mapfile -t ids < <(docker ps -aq --filter "label=com.docker.compose.project=$project" --filter "label=com.docker.compose.service=$service")
    if (( ${#ids[@]} )); then docker stop -t 30 "${ids[@]}" >/dev/null; fi
  done
}
case "${1:-status}" in
  retire) retire_legacy ;;
  running) compose ps --services --status running "${services[@]}" ;;
  stop) retire_legacy; compose stop -t 30 "${services[@]}" ;;
  resume)
    shift
    (( $# )) || exit 0
    [[ $# == 1 && "$1" == assistant-control ]] || die "invalid service"
    compose up -d --no-build --no-deps --wait --wait-timeout 90 assistant-control ;;
  status) compose ps "${services[@]}" ;;
  snapshot)
    [[ $# == 2 && "$2" == /* ]] || die "snapshot needs a private absolute destination"
    [[ -z "$(compose ps -q --status running "${services[@]}")" ]] || die "stop the assistant before copying its state"
    mkdir -p "$2"; chmod 700 "$2"
    cp -a "$STATE/." "$2/"
    find "$2" -type d -exec chmod 700 {} +
    find "$2" -type f -exec chmod 600 {} + ;;
  *) die "use the application to configure assistants; this helper only manages backup state" ;;
esac
