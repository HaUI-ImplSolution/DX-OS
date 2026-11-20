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
set -uo pipefail

run="${1:?usage: rerun_transient.sh <run-id>}"
repo="${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}"
transient="$(paste -sd'|' "$(dirname "${BASH_SOURCE[0]}")/transient_errors.txt")"

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

all_transient=true
while IFS=$'\t' read -r id name; do
  [[ -n "$id" ]] || continue
  if gh api "repos/$repo/actions/jobs/$id/logs" > "$log" 2>/dev/null && grep -Eiq "$transient" "$log"; then
    echo "- \`$name\` failed on a network or quota error" | tee -a "$summary"
  else
    echo "- \`$name\` failed for another reason, or its log could not be read" | tee -a "$summary"
    all_transient=false
  fi
done <<< "$failed"

if [[ "$all_transient" == true ]]; then
  echo "Running the failed jobs of run $run again." | tee -a "$summary"
  gh run rerun "$run" --repo "$repo" --failed
else
  echo "Leaving run $run as it is." | tee -a "$summary"
fi
