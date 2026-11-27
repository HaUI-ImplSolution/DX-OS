#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# The model commands of dxos: plan, apply, lint, conformance and model.
# Sourced by dxos, which provides DEPLOY_DIR, die, env_value, compose, from_caller, api, pretty, json_field and set_project.

# Prints the plan in an API answer as text, with the file name for the apply hint. Fails, printing nothing, when
# jq is missing or the answer is no plan.
plan_text() {
  command -v jq >/dev/null 2>&1 || return 1
  jq -er --arg file "$2" '
    def kinds: {
      ADD_ROLE: "Add role", ADD_UNIT: "Add unit", ADD_ENTITY: "Add entity", DROP_ENTITY: "Drop entity",
      ADD_FIELD: "Add field", DROP_FIELD: "Drop field", RENAME_FIELD: "Rename field",
      ADD_REQUIRED: "Make input required", DROP_REQUIRED: "Make input optional",
      ADD_UNIQUE: "Require unique values", DROP_UNIQUE: "Allow repeated values",
      CHANGE_OPTIONS: "Change the choices", CHANGE_LABEL: "Change the label", CHANGE_DEFAULT: "Change the default value",
      CHANGE_FORMAT: "Change the code format", CHANGE_PATTERN: "Change the text pattern", DROP_PATTERN: "Remove the text pattern",
      CHANGE_REQUIRED_WHEN: "Change the condition for required input", DROP_REQUIRED_WHEN: "Remove the condition for required input",
      CHANGE_UI: "Change how the entity is displayed", CHANGE_DOCUMENTS: "Change the printable documents",
      CHANGE_AI_TASKS: "Change the assistant tasks",
      GRANT_ACCESS: "Grant access to a role", REVOKE_ACCESS: "Revoke the access of a role",
      WIDEN_ACCESS: "Widen access", NARROW_ACCESS: "Narrow access", CHANGE_ACCESS: "Change access",
      CHANGE_OWNER_VIA: "Change the owner taken from the parent record",
      ADD_PROCESS: "Add process", DROP_PROCESS: "Drop process", CHANGE_HANDLER: "Change the handler role",
      CHANGE_FLOW: "Change states or actions", CHANGE_ACTIONS: "Change the label, role or required fields of an action",
      CHANGE_SELF_DECISION: "Change who may decide on own records", CHANGE_COMPLETION: "Change the completion action",
      CHANGE_BOOKING: "Change the booking block", CHANGE_DEADLINE: "Change the deadline", CHANGE_CALENDAR: "Change the working calendar",
      CHANGE_ESCALATION: "Change the escalation levels", CHANGE_AUTO_CLOSE: "Change the time before a record closes itself",
      REPAIR_SCHEMA: "Repair the table structure", REPAIR_CLIENT: "Recreate the client in Keycloak",
      REPAIR_ROLE: "Recreate the role in Keycloak", REPAIR_UNIT: "Recreate the unit group in Keycloak"
    };
    def rules: {
      REQUIRED: "required", NEW_REQUIRED: "new field that is required", REQUIRED_WHEN: "required under a condition",
      UNIQUE: "must be unique", OPTIONS: "accepts only the new choices", ELEMENTS: "number of elements",
      MIN: "minimum value", MAX: "maximum value", PATTERN: "text pattern"
    };
    def levels: {
      SAFE: "can be applied now, no data moves",
      MIGRATION: "existing data is moved to the new structure",
      LOSS: "some data will be deleted, back it up with deploy/backup.sh first"
    };
    def target: [.entity, .field] | map(select(. != null)) | join(".");
    def change:
      "  " + ((.level + "         ")[0:9]) + " " + (kinds[.kind] // .kind)
      + (if target != "" then ": " + target + (if (.detail // "") != "" then " (" + .detail + ")" else "" end)
         elif (.detail // "") != "" then ": " + .detail else "" end);
    if (.data | type) == "array" then (.message // "The model file has problems:"), (.data[] | "  " + tostring)
    else .data | select(.changes != null) | . as $p
    | ($p.version | tostring) as $v
    | (if $p.action == "create" then "Model \($p.model): new, created as version \($v)"
       elif $p.action == "change" then "Model \($p.model): version \($p.fromVersion) -> \($v)"
       elif $p.action == "repair" then "Model \($p.model): the file is unchanged, but tables or Keycloak entries drifted from it; applying repairs them and keeps version \($p.fromVersion)"
       elif $p.action == "rejected" then "Model \($p.model): the plan is refused, nothing can be applied"
       else "Model \($p.model): the running model already equals this file, nothing to apply" end),
      (if $p.action == "unchanged" then empty else "Level: \($p.level), \(levels[$p.level] // "")" end),
      (if ($p.changes | length) > 0 then "", "Changes (\($p.changes | length)):", ($p.changes[] | change) else empty end),
      (if ($p.openRecords | length) > 0 then "", "Records in progress: " + ($p.openRecords | to_entries | map("\(.key) \(.value)") | join(", ")) else empty end),
      (if ($p.impacts | length) > 0 then "", "Actions that will refuse waiting records until they are filled in:",
         ($p.impacts[] | "  \(.entity).\(.action) needs \(.fields | join(", ")): \(.rows) records in \(.states | join(", ")) lack it") else empty end),
      (if ($p.violations | length) > 0 then "", "Existing data that breaks a new rule:",
         ($p.violations[] | "  " + target + ": " + (rules[.rule] // .rule) + ", \(.rows) rows" + (if (.sample | length) > 0 then ", ids " + (.sample | map(tostring) | join(", ")) else "" end)) else empty end),
      (if ($p.rejections | length) > 0 then "", "Refused:", ($p.rejections[] | "  " + .) else empty end),
      (if ($p.questions | length) > 0 then "", "To confirm:", ($p.questions[] | "  " + .) else empty end),
      (if ($p.statements | length) > 0 then "", "SQL statements: \($p.statements | length) (dxos plan --json lists them)" else empty end),
      (if $p.action != "rejected" and $p.action != "unchanged" and $p.level != "SAFE"
         then "", "To apply: dxos apply \($file) --accept \($p.level)" else empty end)
    end
  ' <<<"$1" 2>/dev/null
}

# Prints an apply answer as text: the model, its new version and the steps that ran. Fails, printing nothing,
# when jq is missing or the answer holds no applied version.
applied_text() {
  command -v jq >/dev/null 2>&1 || return 1
  jq -er '
    def kinds: {
      RENAME_FIELD: "Rename field", CHANGE_SCHEMA: "Change the tables", FILL_CODES: "Fill in record codes",
      DROP_FILES: "Delete the attached files", ENSURE_CLIENT: "Make sure the client exists",
      ENSURE_ROLE: "Make sure the role exists", ENSURE_UNIT: "Make sure the unit exists",
      DEPLOY_PROCESS: "Deploy the process", SAVE_VERSION: "Save the model version",
      UNDEPLOY_PROCESS: "Remove the process", DROP_SCHEMA: "Drop the schema", DROP_CLIENT: "Delete the client"
    };
    .data | select(.version != null) | (.steps // []) as $steps
    | "Applied model \(.model): version \(.version)",
      "Steps (\($steps | length)):",
      ($steps[] | "  " + (kinds[.kind] // .kind)
        + (if (.target // "") == "" then "" else ": " + .target end))
  ' <<<"$1" 2>/dev/null
}

# Prints the message of a refused apply, and its lines when the answer lists them. Fails when jq is missing.
apply_error_text() {
  command -v jq >/dev/null 2>&1 || return 1
  jq -er '
    select((.message | type) == "string" and .message != "")
    | "dxos: " + (.message | gsub("[\r\n]+"; " ")),
      (if (.data | type) == "array" then .data[] | "  " + tostring else empty end)
  ' <<<"$1" 2>/dev/null
}

# Prints a plan answer as text, or as JSON when asked, when jq is missing or when the answer is no plan.
print_plan() {
  local text
  if [[ "${3:-}" != json ]] && text="$(plan_text "$1" "$2")"; then
    printf '%s\n' "$text"
  else
    pretty "$1"
  fi
}

model_file_arg() {
  [[ -n "${1:-}" ]] || die "usage: dxos $2 <model-file>"
  [[ -f "$1" && -r "$1" ]] || die "model file not found or not readable: $1"
}

# Prints the plan; exit code 1 when the plan is refused or the API says no. With --check the exit code is
# 0 when the running model equals the file and 2 when the plan has changes.
cmd_plan() {
  local check=false form=text file=""
  while (( $# )); do
    case "$1" in
      --check) check=true; shift ;;
      --json) form=json; shift ;;
      -*) die "unknown option: $1" ;;
      *) [[ -z "$file" ]] || die "usage: dxos plan [--check] [--json] <model-file>"; file="$1"; shift ;;
    esac
  done
  [[ -n "$file" ]] || die "usage: dxos plan [--check] [--json] <model-file>"
  file="$(from_caller "$file")"
  model_file_arg "$file" plan
  api POST /models/plan "$file"
  print_plan "$API_BODY" "$file" "$form"
  [[ "$API_STATUS" == 200 && "$(json_field action "$API_BODY")" != rejected ]] || return 1
  if $check && [[ "$(json_field action "$API_BODY")" != unchanged ]]; then return 2; fi
  return 0
}

# Plans, then applies with the version of that plan as base. The level is never raised on its own:
# a plan above SAFE is refused unless --accept names its level.
cmd_apply() {
  local file="" accept="" form=text query text
  while (( $# )); do
    case "$1" in
      --accept) accept="${2:?--accept needs SAFE, MIGRATION or LOSS}"; shift 2 ;;
      --accept=*) accept="${1#--accept=}"; shift ;;
      --json) form=json; shift ;;
      -*) die "unknown option: $1" ;;
      *) [[ -z "$file" ]] || die "usage: dxos apply <model-file> [--accept SAFE|MIGRATION|LOSS] [--json]"; file="$1"; shift ;;
    esac
  done
  [[ -z "$file" ]] || file="$(from_caller "$file")"
  model_file_arg "$file" apply
  [[ -z "$accept" || "$accept" =~ ^(SAFE|MIGRATION|LOSS)$ ]] || die "--accept takes SAFE, MIGRATION or LOSS"
  api POST /models/plan "$file"
  if [[ "$API_STATUS" != 200 ]]; then print_plan "$API_BODY" "$file" "$form"; return 1; fi
  case "$(json_field action "$API_BODY")" in
    unchanged) echo "Nothing to apply: the model runs this file already."; return 0 ;;
    rejected) print_plan "$API_BODY" "$file" "$form"; echo "dxos: the plan is refused; nothing was applied" >&2; return 1 ;;
  esac
  query="base=$(json_field fromVersion "$API_BODY")"
  [[ -n "$accept" ]] && query="$query&accept=$accept"
  api POST "/models/apply?$query" "$file"
  if [[ "$API_STATUS" != 200 ]]; then
    if [[ "$form" != json ]] && text="$(apply_error_text "$API_BODY")"; then
      printf '%s\n' "$text" >&2
    else
      pretty "$API_BODY"
    fi
    return 1
  fi
  if [[ "$form" != json ]] && text="$(applied_text "$API_BODY")"; then
    printf '%s\n' "$text"
  else
    pretty "$API_BODY"
  fi
}

# Checks model files with the reader of the backend; nothing runs on the stack and no file is changed. With
# DXOS_LINT_JAR (the dxos-lint.jar of a release) it runs on the local Java, otherwise in the backend image.
cmd_lint() {
  local jar="${DXOS_LINT_JAR:-}" image="" arg file code=0 status
  (( $# )) || die "usage: dxos lint <model-file>..."
  if [[ -n "$jar" ]]; then
    [[ -f "$jar" ]] || die "DXOS_LINT_JAR is not a file: $jar"
    command -v java > /dev/null || die "Java 21 or newer is needed to run $jar"
    java -jar "$jar" "$@"
    return
  fi
  [[ -f "$DEPLOY_DIR/.env" ]] || die "no deploy/.env to name the backend image: set DXOS_LINT_JAR to the dxos-lint.jar of a release"
  command -v docker > /dev/null || die "Docker or DXOS_LINT_JAR (the dxos-lint.jar of a release, with Java 21) is needed"
  image="$(env_value DXOS_IMAGE_PREFIX)"
  image="${image:-dxos}-backend:$(env_value DXOS_IMAGE_TAG)"
  [[ "$image" == *: ]] && image="${image}local"
  for arg in "$@"; do
    file="$(from_caller "$arg")"
    [[ -f "$file" && -r "$file" ]] || { echo "dxos: model file not found or not readable: $arg" >&2; code=2; continue; }
    status=0
    docker run --rm -i --network none --read-only --tmpfs /tmp --cap-drop ALL --entrypoint java "$image" \
      -jar /opt/dxos/dxos-lint.jar --name "$arg" - < "$file" || status=$?
    (( status <= code )) || code=$status
  done
  return "$code"
}

# Runs the barrier checks of the core (e2e/api/check_conformance.py) for a model that is applied on this stack.
# They sign in as test users of the model, so the command creates the client dxos-check and those users in Keycloak.
cmd_conformance() {
  local model="" allow=false host port
  while (( $# )); do
    case "$1" in
      --allow-test-users) allow=true; shift ;;
      -*) die "unknown option: $1" ;;
      *) [[ -z "$model" ]] || die "usage: dxos conformance <model> [--allow-test-users]"; model="$1"; shift ;;
    esac
  done
  [[ -n "$model" ]] || die "usage: dxos conformance <model> [--allow-test-users]"
  command -v python3 > /dev/null || die "python3 is required"
  host="$(env_value PUBLIC_HOST)"
  case "${host:-localhost}" in
    localhost|127.0.0.1|::1) ;;
    *) $allow || die "this stack is reached at $host: conformance creates the client dxos-check and test users in its Keycloak; run it with --allow-test-users to do so" ;;
  esac
  [[ "$(env_value KEYCLOAK_ADMIN_CONSOLE)" == true ]] \
    || die "conformance reaches the Keycloak Admin API through the gateway: set KEYCLOAK_ADMIN_CONSOLE=true in deploy/.env, then run docker compose up -d apisix-config and docker compose restart apisix"
  port="$(env_value APISIX_HOST_PORT)"
  DXOS_ENV_FILE="$DEPLOY_DIR/.env" DXOS_BASE_URL="http://localhost:${port##*:}" \
    python3 "$DEPLOY_DIR/../e2e/api/check_conformance.py" "$model"
}

# Shows the complete format upgrade before an optional atomic replacement of the model file.
cmd_model_upgrade() (
  local write=false file="" target dir work upgraded diff_status=0
  while (( $# )); do
    case "$1" in
      --write) write=true; shift ;;
      -*) die "unknown option: $1" ;;
      *) [[ -z "$file" ]] || die "usage: dxos model upgrade [--write] <model-file>"; file="$1"; shift ;;
    esac
  done
  [[ -n "$file" ]] || die "usage: dxos model upgrade [--write] <model-file>"
  file="$(from_caller "$file")"
  model_file_arg "$file" "model upgrade"
  api POST /models/upgrade "$file"
  if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi

  # A symlinked model file is upgraded where it points; the link stays.
  target="$(readlink -f -- "$file")"
  dir="$(dirname "$target")"
  work="$(mktemp -d "$dir/.dxos-model-upgrade.XXXXXX")"
  trap 'rm -rf "$work"' EXIT
  upgraded="$work/upgraded.yaml"
  if command -v jq >/dev/null 2>&1; then
    jq -ej '.data.content' <<<"$API_BODY" >"$upgraded"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c 'import json, sys; sys.stdout.write(json.load(sys.stdin)["data"]["content"])' \
      <<<"$API_BODY" >"$upgraded"
  else
    die "jq or python3 is required to read the upgraded model"
  fi

  if cmp -s -- "$file" "$upgraded"; then
    echo "The model already uses format $(json_field toFormat "$API_BODY")."
    return 0
  fi
  diff -u --label "$file" --label "$file (upgraded)" -- "$file" "$upgraded" || diff_status=$?
  (( diff_status == 1 )) || die "could not compare the upgraded model"
  if [[ "$write" != true ]]; then
    echo "Preview only; run with --write to replace the file."
    return 0
  fi
  chmod --reference="$target" "$upgraded"
  chown --reference="$target" "$upgraded" 2>/dev/null \
    || echo "Warning: the owner and group of $file could not be kept." >&2
  sync -- "$upgraded" 2>/dev/null || true
  mv -f -- "$upgraded" "$target"
  sync -- "$dir" 2>/dev/null || true
  echo "Upgraded $file atomically."
)

# Prints the file that results from the extends key; a file without extends is printed as it is.
cmd_model_merge() {
  local file
  (( $# == 1 )) || die "usage: dxos model merge <model-file>"
  file="$(from_caller "$1")"
  model_file_arg "$file" "model merge"
  api POST /models/merge "$file"
  if [[ "$API_STATUS" != 200 ]]; then pretty "$API_BODY"; return 1; fi
  [[ "$(json_field template "$API_BODY")" != null ]] || echo "The file does not extend a template." >&2
  if command -v jq >/dev/null 2>&1; then
    jq -ej '.data.content' <<<"$API_BODY"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c 'import json, sys; sys.stdout.write(json.load(sys.stdin)["data"]["content"])' <<<"$API_BODY"
  else
    die "jq or python3 is required to read the merged model"
  fi
}

cmd_model() {
  case "${1:-}" in
    upgrade) shift; cmd_model_upgrade "$@" ;;
    merge) shift; cmd_model_merge "$@" ;;
    project)
      (( $# == 3 )) || die "usage: dxos model project <model> <project|->"
      [[ "$2" =~ ^[a-z][a-z0-9_]*$ ]] || die "not a model name: $2"
      set_project "/models/$2/project" "$3"
      project_text "$3" "the model $2" ;;
    *) die "usage: dxos model upgrade [--write] <model-file> | dxos model merge <model-file> | dxos model project <model> <project|->" ;;
  esac
}
