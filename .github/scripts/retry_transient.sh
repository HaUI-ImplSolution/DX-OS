#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Runs a command and runs it again only when it failed on a network or quota error of a service it downloads from.
#
#   .github/scripts/retry_transient.sh docker compose build
#
# A failure with no such error in its output is a failure of the code and ends at the first run.
# Three runs at most, waiting 30 and then 60 seconds.
set -uo pipefail

transient='HTTP/[0-9.]+ (429|500|502|503|504)|HTTP (429|500|502|503|504)|429 Too Many Requests|toomanyrequests|rate limit exceeded'
transient+='|rpc error: code = (Unavailable|DeadlineExceeded)|error reading from server: EOF|unexpected EOF'
transient+='|connection (reset|refused|timed out)|i/o timeout|TLS handshake timeout|Temporary failure in name resolution'
transient+='|Could not transfer artifact|Read timed out|Premature end of Content-Length|Service Unavailable|Bad Gateway'

log="$(mktemp)"
trap 'rm -f "$log"' EXIT
for attempt in 1 2 3; do
  "$@" 2>&1 | tee "$log"
  code="${PIPESTATUS[0]}"
  [ "$code" -eq 0 ] && exit 0
  if [ "$attempt" -lt 3 ] && grep -Eiq "$transient" "$log"; then
    echo "::warning::'$1 $2' failed on a network or quota error (run $attempt of 3), running it again"
    sleep $((attempt * 30))
    continue
  fi
  exit "$code"
done
