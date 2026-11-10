#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Quiesces and snapshots assistant state for backup; deployment uses the main compose file.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
STATE="$PWD/data/assistant"
services=(assistant-control assistant-runtime-1 assistant-runtime-2 assistant-runtime-3)
die() { echo "assistant backup: $*" >&2; exit 1; }
compose() { docker compose --env-file "${ENV_FILE:-.env}" "$@"; }
case "${1:-status}" in
  running) compose ps --services --status running "${services[@]}" ;;
  stop) compose stop -t 30 "${services[@]}" ;;
  resume)
    shift
    (( $# )) || exit 0
    for service in "$@"; do
      case "$service" in assistant-control|assistant-runtime-[123]) ;; *) die "invalid service" ;; esac
    done
    runtimes=(); control=false
    for service in "$@"; do
      if [[ "$service" == assistant-control ]]; then control=true; else runtimes+=("$service"); fi
    done
    if (( ${#runtimes[@]} )); then compose up -d --no-build --no-deps --wait --wait-timeout 90 "${runtimes[@]}"; fi
    if [[ "$control" == true ]]; then compose up -d --no-build --no-deps --wait --wait-timeout 90 assistant-control; fi ;;
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
