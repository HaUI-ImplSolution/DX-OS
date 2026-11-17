#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Starts the optional assistant and quiesces its state for container-based backup.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"
services=(assistant-control)
die() { echo "assistant: $*" >&2; exit 1; }
compose() { docker compose --env-file "${ENV_FILE:-.env}" "$@"; }
enabled() { compose config --services | grep -Fxq assistant-control; }
case "${1:-status}" in
  enabled) enabled ;;
  start)
    if ! enabled; then
      compose --profile assistant stop -t 30 assistant-control assistant-sso assistant-init
      exit 0
    fi
    if ! compose up -d --no-build --pull missing assistant-sso || ! compose wait assistant-sso; then
      compose stop -t 30 assistant-control >&2 || true
      die "SSO bootstrap failed; Core remains available"
    fi
    compose up -d --no-build --no-deps --wait --wait-timeout 90 assistant-control ;;
  platform-services) compose config --services | sed '/^assistant-/d' ;;
  running) compose ps --services --status running "${services[@]}" | sed '/^$/d' ;;
  stop) compose stop -t 30 "${services[@]}" ;;
  resume)
    shift
    (( $# )) || exit 0
    [[ $# == 1 && "$1" == assistant-control ]] || die "invalid service"
    compose up -d --no-build --no-deps --wait --wait-timeout 90 assistant-control ;;
  status) compose ps "${services[@]}" ;;
  *) die "use enabled, platform-services, start, running, stop, resume or status" ;;
esac
