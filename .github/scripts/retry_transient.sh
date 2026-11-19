#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Runs a command and runs it again only when it failed on a network or quota error of a service it downloads from.
#
#   .github/scripts/retry_transient.sh docker compose build
#
# A 404 counts only when wget got it, which is the Maven wrapper download that Maven Central sometimes refuses wrongly.
# A failure with no such error in its output is a failure of the code and ends at the first run.
# Three runs at most, waiting 30 and then 60 seconds.
set -uo pipefail

# One extended regular expression per line, shared with rerun_transient.sh.
transient="$(paste -sd'|' "$(dirname "${BASH_SOURCE[0]}")/transient_errors.txt")"

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
