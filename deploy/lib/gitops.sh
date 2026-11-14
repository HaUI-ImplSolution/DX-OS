#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# GitOps pull for dxos. Two sources, each with its own repository, ref and mode:
#   models  the model files of a repository, compared with the running models;
#   infra   the deployment of this install (deploy/, the gateway routes, the image tag), compared with
#           a DX-OS repository at a ref, and upgraded to it.
# Sourced by dxos, which provides DEPLOY_DIR, PREFIX, STOPPED_MARK, env_value, die, api, json_field,
# cmd_apply, compose and wait_ready.

GITOPS_DIR="$DEPLOY_DIR/gitops"

gitops_rank() {
  case "$1" in SAFE) echo 1 ;; MIGRATION) echo 2 ;; LOSS) echo 3 ;; *) echo 4 ;; esac
}

# Whether the source (models or infra) has a repository in deploy/.env.
gitops_configured() {
  case "$1" in
    models) [[ -n "$(env_value GITOPS_REPO)" ]] ;;
    infra) [[ -n "$(env_value GITOPS_INFRA_REPO)" ]] ;;
  esac
}

# Reads and checks the GITOPS_* settings of one source into the G_* variables.
gitops_conf() {
  G_KIND="$1"
  G_DIR="$GITOPS_DIR/$1"
  local prefix=GITOPS
  [[ "$1" == infra ]] && prefix=GITOPS_INFRA
  G_REPO="$(env_value "${prefix}_REPO")"
  [[ -n "$G_REPO" ]] || die "${prefix}_REPO is not set in deploy/.env"
  G_REF="$(env_value "${prefix}_REF")"; G_REF="${G_REF:-main}"
  G_MODE="$(env_value "${prefix}_MODE")"; G_MODE="${G_MODE:-report}"
  G_PATH="$(env_value GITOPS_PATH)"; G_PATH="${G_PATH:-models}"
  G_SIGNED="$(env_value GITOPS_REQUIRE_SIGNED)"; G_SIGNED="${G_SIGNED:-false}"
  G_KEY="$(env_value GITOPS_SSH_KEY)"
  G_KNOWN="$(env_value GITOPS_KNOWN_HOSTS)"
  G_SIGNERS="$(env_value GITOPS_ALLOWED_SIGNERS)"

  [[ "$G_REPO" =~ ^(https://|ssh://|file://|/|[A-Za-z0-9._-]+@[A-Za-z0-9.-]+:)[^[:space:]]+$ ]] \
    || die "${prefix}_REPO must be an https://, ssh://, file:// or user@host:path address, or an absolute path"
  [[ "$G_REF" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*$ ]] || die "${prefix}_REF is not a branch or tag name: $G_REF"
  [[ "$G_PATH" == . || ( "$G_PATH" =~ ^[A-Za-z0-9._/-]+$ && "$G_PATH" != /* && "$G_PATH" != *..* ) ]] \
    || die "GITOPS_PATH must be a relative directory of the repository without ..: $G_PATH"
  [[ "$G_MODE" =~ ^(report|approve|auto)$ ]] || die "${prefix}_MODE takes report, approve or auto"
  [[ "$G_SIGNED" =~ ^(true|false)$ ]] || die "GITOPS_REQUIRE_SIGNED takes true or false"
  local file
  for file in "$G_KEY" "$G_KNOWN" "$G_SIGNERS"; do
    [[ -z "$file" || ( "$file" =~ ^/[A-Za-z0-9._/-]+$ && -r "$file" ) ]] \
      || die "not a readable absolute path without spaces: $file"
  done
  [[ "$G_SIGNED" == false || -n "$G_SIGNERS" ]] || die "GITOPS_REQUIRE_SIGNED=true needs GITOPS_ALLOWED_SIGNERS"
  # An unattended upgrade of the deployment must come from a commit someone signed.
  [[ "$G_KIND" != infra || "$G_MODE" != auto || "$G_SIGNED" == true ]] \
    || die "GITOPS_INFRA_MODE=auto needs GITOPS_REQUIRE_SIGNED=true"
}

# git inside the clone, with the deploy key and the pinned host keys, never asking on the terminal.
gitops_git() {
  local ssh="ssh -o BatchMode=yes -o StrictHostKeyChecking=yes"
  [[ -z "$G_KEY" ]] || ssh+=" -i $G_KEY -o IdentitiesOnly=yes"
  [[ -z "$G_KNOWN" ]] || ssh+=" -o UserKnownHostsFile=$G_KNOWN"
  GIT_SSH_COMMAND="$ssh" GIT_TERMINAL_PROMPT=0 git -c protocol.ext.allow=never -C "$G_DIR/repo" "$@"
}

# Writes the status of the run. After the lines "time=", "commit=", "mode=", "signature=" and "error="
# comes one line per item: "<name> <state> <level> <detail>", states in-sync, drift, pending, rejected,
# applied, failed.
gitops_write_status() {
  local commit="$1" signature="$2" error="$3" lines="$4" previous="$G_DIR/status"
  local tmp="$G_DIR/status.tmp" file state level
  # A run that failed before it compared anything keeps the items and the commit of the last good run.
  if [[ -n "$error" && -z "$lines" && -f "$previous" ]]; then
    lines="$(grep -vE '^(time|commit|mode|signature|error)=' "$previous" || true)"
    [[ -z "$lines" ]] || lines+=$'\n'
    commit="$(sed -n 's/^commit=//p' "$previous")"
  fi
  {
    echo "time=$(date -Iseconds)"
    echo "commit=$commit"
    echo "mode=$G_MODE"
    echo "signature=$signature"
    echo "error=$error"
    printf '%s' "$lines"
  } >"$tmp"
  # The history gets a line when the state of an item or the commit changes.
  while read -r file state level _; do
    [[ -n "$file" ]] || continue
    if ! grep -q "^$file $state " "$previous" 2>/dev/null || ! grep -qx "commit=$commit" "$previous" 2>/dev/null; then
      echo "$(date -Iseconds) ${commit:0:12} $file $state ${level:-}" >>"$G_DIR/history.log"
    fi
  done <<<"$lines"
  if [[ -n "$error" ]] && ! grep -qx "error=$error" "$previous" 2>/dev/null; then
    echo "$(date -Iseconds) ${commit:0:12} - error $error" >>"$G_DIR/history.log"
  fi
  mv "$tmp" "$previous"
}

# The line of a git or ssh error file that tells the cause, not the closing advice. Specific causes come
# first because "Could not read from remote repository" ends every ssh failure; the key path is not shown.
gitops_error_line() {
  local line
  line="$(grep -iE 'permission denied|verification failed|host key is known|too open|unable to access|couldn.t find|no such' "$1" 2>/dev/null | head -n1)"
  [[ -n "$line" ]] || line="$(grep -iE 'fatal|could not' "$1" 2>/dev/null | grep -viE 'please make sure|repository exists' | tail -n1)"
  [[ -n "$line" ]] || line="$(grep -v '^$' "$1" 2>/dev/null | tail -n1)"
  line="$(tr -d '\r' <<<"$line" | cut -c1-160)"
  [[ -z "${G_KEY:-}" ]] || line="${line//$G_KEY/GITOPS_SSH_KEY}"
  echo "$line"
}

# Fetches the ref into the clone, checks the signature when required and checks the commit out.
# Sets G_COMMIT and G_SIGNATURE; on failure writes the status and returns 1.
gitops_fetch() {
  local err="$G_DIR/.git.err"
  G_COMMIT=""
  G_SIGNATURE="not-required"
  if [[ ! -d "$G_DIR/repo/.git" ]]; then
    git -C "$G_DIR/repo" init -q
    git -C "$G_DIR/repo" remote add origin "$G_REPO"
  else
    git -C "$G_DIR/repo" remote set-url origin "$G_REPO"
  fi
  if ! gitops_git fetch -q --depth 1 origin "$G_REF" 2>"$err"; then
    gitops_write_status "" unknown "fetch failed: $(gitops_error_line "$err")" ""
    echo "dxos: cannot fetch $G_REF: $(gitops_error_line "$err")" >&2
    return 1
  fi
  G_COMMIT="$(gitops_git rev-parse "FETCH_HEAD^{commit}")"
  if [[ "$G_SIGNED" == true ]]; then
    if gitops_git -c gpg.format=ssh -c "gpg.ssh.allowedSignersFile=$G_SIGNERS" verify-commit "$G_COMMIT" >/dev/null 2>&1; then
      G_SIGNATURE=verified
    else
      gitops_write_status "$G_COMMIT" failed "commit ${G_COMMIT:0:12} is not signed by an allowed signer" ""
      echo "dxos: commit ${G_COMMIT:0:12} is not signed by an allowed signer; nothing was applied" >&2
      return 1
    fi
  fi
  gitops_git checkout -q --force --detach "$G_COMMIT"
}

# Whether the approval file names this commit and a level at least as high as the plan's.
gitops_approved() {
  local approved
  approved="$(cat "$G_DIR/approved" 2>/dev/null || true)"
  [[ "${approved%% *}" == "$1" ]] && (( $(gitops_rank "${approved##* }") >= $(gitops_rank "$2") ))
}

# --- models ---

# Plans one model file and applies it when the mode and the approval allow; prints the status line.
gitops_one() {
  local path="$1" commit="$2" name accept="" action level state out
  name="$(basename "$path")"
  api POST /models/plan "$path"
  if [[ "$API_STATUS" != 200 ]]; then
    echo "$name failed - HTTP $API_STATUS"
    return 0
  fi
  action="$(json_field action "$API_BODY")"
  level="$(json_field level "$API_BODY")"
  case "$action" in
    unchanged) echo "$name in-sync -"; return 0 ;;
    rejected) echo "$name rejected ${level:--}"; return 0 ;;
  esac
  case "$G_MODE" in
    auto) [[ "$level" == SAFE ]] && accept=SAFE ;;
    approve) ! gitops_approved "$commit" "$level" || accept="$level" ;;
  esac
  if [[ -z "$accept" ]]; then
    [[ "$G_MODE" == report ]] && state=drift || state=pending
    echo "$name $state $level"
    return 0
  fi
  if out="$(cmd_apply "$path" --accept "$accept" 2>&1)"; then
    echo "$name applied $level"
  else
    printf '%s\n' "$out" >"$G_DIR/last-apply-error.log"
    echo "$name failed $level"
  fi
}

gitops_models() {
  local lines="" error="" dir path
  dir="$G_DIR/repo/$G_PATH"
  if ! ( api GET /models && [[ "$API_STATUS" == 200 ]] ) >/dev/null 2>"$G_DIR/.api.err"; then
    gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "the API did not answer: $(gitops_error_line "$G_DIR/.api.err")" ""
    echo "dxos: the API did not answer; no model was compared" >&2
    return 1
  fi
  [[ -d "$dir" ]] || { gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "directory $G_PATH is not in the repository" ""; return 1; }
  while IFS= read -r path; do
    lines+="$(gitops_one "$path" "$G_COMMIT")"$'\n'
  done < <(find "$dir" -maxdepth 1 -type f \( -name '*.yaml' -o -name '*.yml' \) | LC_ALL=C sort)
  [[ -n "$lines" ]] || error="no model file in $G_PATH"
  gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "$error" "$lines"
  # The approval ends when no file waits for it any more.
  grep -qE '^[^ =]+ (drift|pending|failed) ' <<<"$lines" || rm -f "$G_DIR/approved"
  ! grep -qE '^[^ =]+ failed ' <<<"$lines" || return 1
}

# --- infra ---

# The files an install keeps as runtime files; the same selection as runtime_files in install.sh.
gitops_runtime_files() {
  (cd "$1" && find deploy sso/realm-dxos.json e2e/api/kc.py e2e/api/check_api.py e2e/api/check_conformance.py \
    \( -path deploy/data -o -path deploy/backups -o -path deploy/gitops -o -name __pycache__ \) -prune -o \
    -type f ! \( -name '.env*' ! -name '.env.example' \) ! -name 'VPS.md' \
    ! -path deploy/apisix/local.yaml -print) | LC_ALL=C sort
}

# Compares the deployment of the clone with this install: prints "<changed> <added> <removed>".
gitops_infra_diff() {
  local root="$G_DIR/repo" changed=0 added=0 removed=0 file listed
  listed="$(gitops_runtime_files "$root")"
  while IFS= read -r file; do
    [[ -n "$file" ]] || continue
    if [[ ! -f "$PREFIX/$file" ]]; then added=$((added + 1))
    elif ! cmp -s "$root/$file" "$PREFIX/$file"; then changed=$((changed + 1))
    fi
  done <<<"$listed"
  if [[ -f "$PREFIX/.dxos-files" ]]; then
    removed="$(comm -23 <(LC_ALL=C sort "$PREFIX/.dxos-files") <(printf '%s\n' "$listed") | grep -c . || true)"
  fi
  echo "$changed $added $removed"
}

# Runs update.sh for the new files and tag. The marker stays until it succeeds, so a failed pull or start is
# tried again at the next run even though the files already match the repository.
gitops_infra_update() {
  local tag="$1" out
  if [[ -z "$(compose ps -q backend 2>/dev/null)" ]]; then
    rm -f "$G_DIR/update-pending"
    return 0
  fi
  echo "$tag" >"$G_DIR/update-pending"
  if ! out="$("$DEPLOY_DIR/update.sh" "$tag" 2>&1)"; then
    printf '%s\n' "$out" >"$G_DIR/last-apply-error.log"
    return 1
  fi
  rm -f "$G_DIR/update-pending"
  wait_ready >/dev/null 2>&1 || true
}

gitops_infra() {
  local root="$G_DIR/repo" changed added removed tag current state detail out
  [[ -f "$root/deploy/install.sh" && -f "$root/deploy/docker-compose.yml" ]] || {
    gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "the repository has no deploy/install.sh and deploy/docker-compose.yml" ""
    return 1
  }
  if [[ -f "$G_DIR/update-pending" ]]; then
    if ! gitops_infra_update "$(cat "$G_DIR/update-pending")"; then
      gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "" "infra failed - update.sh did not finish; it runs again at the next run"$'\n'
      return 1
    fi
    rm -f "$G_DIR/approved"
  fi
  read -r changed added removed <<<"$(gitops_infra_diff)"
  current="$(env_value DXOS_IMAGE_TAG)"
  tag="$current"
  [[ "$G_REF" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] && tag="$G_REF"
  detail="changed=$changed,added=$added,removed=$removed,tag=$tag"
  if (( changed + added + removed == 0 )) && [[ "$tag" == "$current" ]]; then
    gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "" "infra in-sync - $detail"$'\n'
    return 0
  fi
  case "$G_MODE" in
    report) state=drift ;;
    approve) gitops_approved "$G_COMMIT" SAFE && state=apply || state=pending ;;
    auto) state=apply ;;
  esac
  if [[ "$state" != apply ]]; then
    gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "" "infra $state - $detail"$'\n'
    return 0
  fi
  if ! out="$(env -u DXOS_IMAGE_TAG bash "$root/deploy/install.sh" --upgrade "$PREFIX" "$tag" 2>&1)"; then
    printf '%s\n' "$out" >"$G_DIR/last-apply-error.log"
    gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "" "infra failed - $detail"$'\n'
    return 1
  fi
  # The stack picks the new files up now when it runs; a stopped stack uses them when it starts.
  if ! gitops_infra_update "$tag"; then
    gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "" "infra failed - $detail"$'\n'
    return 1
  fi
  # An approval covers one upgrade; a later difference needs a new approval.
  rm -f "$G_DIR/approved"
  gitops_write_status "$G_COMMIT" "$G_SIGNATURE" "" "infra applied - $detail"$'\n'
}

# --- commands ---

# Runs one source once; returns 1 when the run failed.
gitops_sync_kind() {
  gitops_conf "$1"
  mkdir -p "$G_DIR/repo"
  chmod 700 "$GITOPS_DIR"
  exec 8>"$G_DIR/.lock"
  if command -v flock >/dev/null 2>&1 && ! flock -n 8; then
    echo "dxos: another $1 sync is running; the next run continues from the same repository" >&2
    return 0
  fi
  gitops_fetch || return 1
  if [[ "$1" == infra ]]; then gitops_infra; else gitops_models; fi
}

gitops_sync() {
  local kinds=() kind failed=0
  [[ ! -e "$STOPPED_MARK" ]] || return 0
  if [[ -n "${1:-}" ]]; then
    [[ "$1" =~ ^(models|infra)$ ]] || die "usage: dxos gitops sync [models|infra]"
    kinds=("$1")
  else
    for kind in models infra; do ! gitops_configured "$kind" || kinds+=("$kind"); done
    (( ${#kinds[@]} )) || die "set GITOPS_REPO or GITOPS_INFRA_REPO in deploy/.env"
  fi
  mkdir -p "$GITOPS_DIR"
  for kind in "${kinds[@]}"; do
    gitops_sync_kind "$kind" || failed=1
  done
  return "$failed"
}

# Prints the last run of each source; exit 2 when an item is not in sync or waits for approval, 1 when a run failed.
gitops_status() {
  local kinds=(models infra) kind file error result=0 shown=false
  [[ -z "${1:-}" ]] || kinds=("$1")
  for kind in "${kinds[@]}"; do
    file="$GITOPS_DIR/$kind/status"
    [[ -f "$file" ]] || continue
    shown=true
    echo "[$kind]"
    cat "$file"
    error="$(sed -n 's/^error=//p' "$file")"
    if [[ -n "$error" ]] || grep -qE '^[^ =]+ failed ' "$file"; then result=1
    elif (( result == 0 )) && grep -qE '^[^ =]+ (drift|pending|rejected) ' "$file"; then result=2
    fi
  done
  $shown || { echo "No GitOps run yet; run dxos gitops sync."; return 1; }
  return "$result"
}

# Approves the commit that waits and runs the source at once. For models the level is the highest plan to accept.
gitops_approve() {
  local kind=models want level=SAFE commit
  if [[ "${1:-}" == infra ]]; then kind=infra; shift; fi
  want="${1:-}"
  [[ -n "$want" ]] || die "usage: dxos gitops approve [infra] <commit> [--accept SAFE|MIGRATION|LOSS]"
  shift
  if [[ "${1:-}" == --accept ]]; then level="${2:-}"; shift 2; fi
  [[ "$level" =~ ^(SAFE|MIGRATION|LOSS)$ ]] || die "--accept takes SAFE, MIGRATION or LOSS"
  (( $# == 0 )) || die "usage: dxos gitops approve [infra] <commit> [--accept SAFE|MIGRATION|LOSS]"
  [[ "$kind" == models || "$level" == SAFE ]] || die "--accept applies to models only; an infra upgrade has no level"
  gitops_conf "$kind"
  [[ "$G_MODE" == approve ]] || die "the mode of $kind is $G_MODE; set it to approve to use dxos gitops approve"
  commit="$(sed -n 's/^commit=//p' "$G_DIR/status" 2>/dev/null)"
  [[ -n "$commit" && "$commit" == "$want"* && ${#want} -ge 7 ]] \
    || die "the commit waiting for approval is ${commit:0:12}; run dxos gitops sync to fetch the latest"
  echo "$commit $level" >"$G_DIR/approved"
  gitops_sync "$kind"
}

gitops_timer() {
  local units="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
  case "$1" in
    install)
      command -v systemctl >/dev/null 2>&1 || die "systemd is required; or run dxos gitops sync from cron"
      mkdir -p "$units"
      printf '%s\n' "[Unit]" "Description=Pull the model files and the deployment from git and compare them with this install" "" \
        "[Service]" "Type=oneshot" "ExecStart=$DEPLOY_DIR/dxos gitops sync" >"$units/dxos-gitops.service"
      printf '%s\n' "[Unit]" "Description=Pull from git, every 5 minutes" "" "[Timer]" \
        "OnBootSec=2min" "OnUnitActiveSec=5min" "" "[Install]" "WantedBy=timers.target" >"$units/dxos-gitops.timer"
      systemctl --user daemon-reload
      systemctl --user enable --now dxos-gitops.timer
      echo "dxos gitops sync runs every 5 minutes; the commits and results are in $GITOPS_DIR/<models|infra>/history.log."
      echo "To keep it running after you log out: loginctl enable-linger $(id -un)" ;;
    uninstall)
      systemctl --user disable --now dxos-gitops.timer 2>/dev/null || true
      rm -f "$units/dxos-gitops.service" "$units/dxos-gitops.timer"
      systemctl --user daemon-reload 2>/dev/null || true
      echo "dxos gitops no longer runs on its own." ;;
  esac
}

cmd_gitops() {
  local action="${1:-sync}"
  [[ $# -eq 0 ]] || shift
  case "$action" in
    sync) (( $# <= 1 )) || die "usage: dxos gitops sync [models|infra]"; gitops_sync "$@" ;;
    status) (( $# <= 1 )) || die "usage: dxos gitops status [models|infra]"; gitops_status "$@" ;;
    approve) gitops_approve "$@" ;;
    install|uninstall) (( $# == 0 )) || die "usage: dxos gitops $action"; gitops_timer "$action" ;;
    *) die "usage: dxos gitops sync [models|infra] | status [models|infra] | approve [infra] <commit> [--accept LEVEL] | install | uninstall" ;;
  esac
}
