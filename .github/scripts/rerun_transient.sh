#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Runs the failed jobs of a finished workflow run again when every one of them failed on a network or quota error.
#
#   GH_TOKEN=... GITHUB_REPOSITORY=owner/name .github/scripts/rerun_transient.sh <run-id>
#
# The errors are listed in transient_errors.txt, the same list retry_transient.sh uses.
# One job that failed for another reason, a test or a compile error for one, leaves the run as it is,
# because the next push starts a new run and a rerun would only fail the same way.
# The log of a job that ended seconds ago can be missing or cut short, so it is read up to three times.
# The rerun waits one to two minutes, so a quota error has time to pass before the jobs ask again.
set -uo pipefail

run="${1:?usage: rerun_transient.sh <run-id>}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"
transient="$(paste -sd'|' "$(dirname "${BASH_SOURCE[0]}")/transient_errors.txt")"

read_wait="${RERUN_READ_WAIT:-10}"
rerun_wait="${RERUN_RERUN_WAIT:-60}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

failed="$(gh api "repos/$repo/actions/runs/$run/jobs?filter=latest&per_page=100" \
  --jq '.jobs[] | select(.conclusion == "failure") | "\(.id)\t\(.name)"')" \
  || { echo "::error::The jobs of run $run could not be read"; exit 1; }

if [[ -z "$failed" ]]; then
  echo "Run $run has no failed job."
  exit 0
fi

# Prints transient, other (a log was read and has no such error) or unreadable (no log could be read).
classify() {
  local id="$1" readable=false attempt
  for attempt in 1 2 3; do
    if gh api "repos/$repo/actions/jobs/$id/logs" > "$log" 2>/dev/null && [[ -s "$log" ]]; then
      readable=true
      if grep -Eiq "$transient" "$log"; then
        echo transient
        return
      fi
    fi
    [[ "$attempt" -lt 3 ]] && sleep "$read_wait"
  done
  [[ "$readable" == true ]] && echo other || echo unreadable
}

all_transient=true
while IFS=$'\t' read -r id name; do
  [[ -n "$id" ]] || continue
  case "$(classify "$id")" in
    transient) echo "- \`$name\` failed on a network or quota error" | tee -a "$summary" ;;
    other)
      echo "- \`$name\` failed for another reason" | tee -a "$summary"
      all_transient=false
      ;;
    *)
      echo "- \`$name\` failed and its log could not be read" | tee -a "$summary"
      all_transient=false
      ;;
  esac
done <<< "$failed"

if [[ "$all_transient" == true ]]; then
  echo "Running the failed jobs of run $run again in about ${rerun_wait} seconds." | tee -a "$summary"
  [[ "$rerun_wait" -gt 0 ]] && sleep $((rerun_wait + RANDOM % 30))
  gh run rerun "$run" --repo "$repo" --failed
else
  echo "Leaving run $run as it is." | tee -a "$summary"
fi
