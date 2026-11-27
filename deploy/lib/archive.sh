#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# The archive commands of dxos: finished records and their history as Parquet files.
# Sourced by dxos, which provides DEPLOY_DIR, die, env_value, compose and from_caller.

# --- History of finished records, out to open files ---

DUCKDB="duckdb/duckdb:1.4.1@sha256:b9e06a757942c2fa195a0df86155db1b0887ce45f33d3bef9ff50890c913531e"

# Runs DuckDB in a container as the calling user: duckdb_run <dir> [docker options] -- [duckdb arguments].
# A non-empty <dir> is mounted at /archive; the extensions it downloads stay in deploy/duckdb/, so only the
# first run needs the network.
duckdb_run() {
  local dir="$1" options=()
  shift
  while [[ "$1" != "--" ]]; do options+=("$1"); shift; done
  shift
  mkdir -p "$DEPLOY_DIR/duckdb"
  [[ -z "$dir" ]] || options+=(-v "$dir:/archive")
  docker run --rm -i --user "$(id -u):$(id -g)" -e HOME=/tmp -v "$DEPLOY_DIR/duckdb:/ext" \
    ${options[@]+"${options[@]}"} "$DUCKDB" /duckdb "$@"
}

# Row count of a Parquet file of the archive.
parquet_rows() { duckdb_run "$1" -- -csv -noheader -c "SELECT count(*) FROM '/archive/$2'" </dev/null; }

# The entities of a model that have a process (the ones with decided_at), one per line.
archive_entities() {
  compose exec -T postgres psql -U "$(env_value POSTGRES_USER)" -d "$(env_value POSTGRES_DB)" -tAc \
    "SELECT table_name FROM information_schema.columns WHERE table_schema = 'm_$1'
     AND column_name = 'decided_at' ORDER BY table_name"
}

# The manifest fields the listing reads, typed so that DuckDB keeps the timestamps as the text it was given.
MANIFEST_COLUMNS="{model: 'VARCHAR', run: 'VARCHAR', since: 'VARCHAR', upTo: 'VARCHAR', files: 'JSON[]'}"

# Where the object store archive lives: the bucket, and the DuckDB statements that open it.
archive_bucket() { local bucket; bucket="$(env_value S3_ARCHIVE_BUCKET)"; printf '%s\n' "${bucket:-dxos-archive}"; }
store_prelude() {
  local endpoint ssl=false
  endpoint="$(env_value S3_ENDPOINT)"; endpoint="${endpoint:-http://seaweedfs:8333}"
  [[ "$endpoint" != https://* ]] || ssl=true
  endpoint="${endpoint#*://}"; endpoint="${endpoint%%/*}"
  # The keys reach the container through the environment; the statement only names the variables.
  printf "SET extension_directory='/ext';\nINSTALL httpfs; LOAD httpfs;\n"
  printf "CREATE SECRET lake (TYPE s3, KEY_ID getenv('AWS_ACCESS_KEY_ID'), SECRET getenv('AWS_SECRET_ACCESS_KEY'), "
  printf "ENDPOINT '%s', URL_STYLE 'path', USE_SSL %s, REGION 'us-east-1');\n" "$endpoint" "$ssl"
}
# Runs DuckDB with the object store open, reading statements from stdin: duckdb_store [docker options].
duckdb_store() {
  local network
  network="$(docker inspect -f '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' \
    "$(compose ps -q seaweedfs)" | awk '{print $1}')"
  [[ -n "$network" ]] || die "SeaweedFS is not running; run dxos start"
  AWS_ACCESS_KEY_ID="$(env_value S3_ACCESS_KEY)" AWS_SECRET_ACCESS_KEY="$(env_value S3_SECRET_KEY)" \
    duckdb_run "" --network "$network" -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY "$@" -- -csv -noheader
}

# Runs one SELECT over the object store and prints its rows. A store with no archive yet prints nothing;
# any other failure (keys, address) stops the command, so a failed read is never taken for an empty store.
store_query() {
  local out
  out="$( { store_prelude; echo "$1"; } | duckdb_store 2>&1)" || {
    grep -q "No files found\|404 Not Found" <<<"$out" && return 0
    die "cannot read the object store: $(grep -m1 -i 'error' <<<"$out" | cut -c1-200)"
  }
  printf '%s\n' "$out"
}

# Writes the records of a model that reached an end state (decided_at is set), and their history
# lines, to Parquet files with a manifest of row counts and SHA-256. The database is read only.
# With store=1 the files go to the object store and the window is [since, before), so a record is
# written once by the runs that follow each other; otherwise they go to a directory.
archive_model() {
  local model="$1" before="${2:-}" out="${3:-$DEPLOY_DIR/archive}" store="${4:-0}" since="${5:-}" skip_empty="${6:-0}"
  local schema run dir network entities entity cond tcond sql version upto="" target="" waiting=0
  [[ "$model" =~ ^[a-z][a-z0-9_]*$ ]] || die "not a model name: $model"
  [[ -z "$before" || "$before" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || die "--before takes a date YYYY-MM-DD"
  [[ -n "$(compose ps -q --status running postgres)" ]] || die "Postgres is not running; run dxos start"
  schema="m_$model"
  psql_value() { compose exec -T postgres psql -U "$(env_value POSTGRES_USER)" -d "$(env_value POSTGRES_DB)" -tAc "$1"; }
  version="$(psql_value "SELECT coalesce(max(version), 0) FROM engine.model_version WHERE model = '$model'")"
  [[ "$version" != 0 ]] || die "model $model has no applied version"
  entities="$(archive_entities "$model")"
  [[ -n "$entities" ]] || die "model $model has no entity with a process"
  run="$(date -u +%Y%m%dT%H%M%SZ)"
  if (( store )); then
    upto="${before:+${before}T00:00:00Z}"; upto="${upto:-$(date -u +%Y-%m-%dT%H:%M:%SZ)}"
    cond="decided_at IS NOT NULL AND decided_at < TIMESTAMPTZ '$upto'"
    [[ -z "$since" ]] || cond="$cond AND decided_at >= TIMESTAMPTZ '$since'"
    dir=""
    target="s3://$(archive_bucket)/$model/$run"
  else
    cond="decided_at IS NOT NULL"
    [[ -z "$before" ]] || cond="$cond AND decided_at < TIMESTAMPTZ '$before'"
    dir="$(realpath -m "$out")/$model/$run"
    mkdir -p "$dir"
    target="/archive"
  fi
  tcond="t.${cond//AND decided_at/AND t.decided_at}"
  if (( skip_empty )); then
    for entity in $entities; do
      (( waiting += $(psql_value "SELECT count(*) FROM $schema.$entity WHERE $cond") )) || true
    done
    (( waiting > 0 )) || { echo "==> $model: nothing to archive"; return 0; }
  fi
  network="$(docker inspect -f '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' \
    "$(compose ps -q postgres)" | awk '{print $1}')"
  sql="SET extension_directory='/ext'; INSTALL postgres; LOAD postgres;
ATTACH 'host=postgres dbname=$(env_value POSTGRES_DB) user=$(env_value POSTGRES_USER)' AS pg (TYPE postgres, READ_ONLY);"
  (( ! store )) || sql+=$'\n'"$(store_prelude)"
  local listed=""
  for entity in $entities; do
    sql+="
COPY (SELECT * FROM pg.$schema.$entity WHERE $cond ORDER BY id) TO '$target/$entity.parquet' (FORMAT parquet);
COPY (SELECT e.* FROM pg.engine.record_event e JOIN pg.$schema.$entity t ON t.id = e.record_id
  WHERE e.model = '$model' AND e.entity = '$entity' AND $tcond ORDER BY e.id)
  TO '$target/${entity}_history.parquet' (FORMAT parquet);"
    for name in "$entity" "${entity}_history"; do
      listed+="${listed:+, }{'name': '$name.parquet', 'rows': (SELECT count(*) FROM read_parquet('$target/$name.parquet')),
        'sha256': (SELECT sha256(content) FROM read_blob('$target/$name.parquet'))}"
    done
  done
  if (( store )); then
    sql+="
COPY (SELECT '$model' AS model, $version AS modelVersion, '$run' AS run, '$since' AS since, '$before' AS before,
  '$upto' AS upTo, 'records with decided_at set, and their history lines' AS condition, [$listed] AS files)
  TO '$target/manifest.json' (FORMAT json, ARRAY false);
SELECT 'FILE|' || f.name || '|' || f.rows FROM (SELECT unnest(files) AS f FROM read_json('$target/manifest.json'));"
    echo "==> Writing the finished records of $model (version $version, decided before $upto${since:+, since $since}) to $target"
    local listing
    listing="$(PGPASSWORD="$(env_value POSTGRES_PASSWORD)" AWS_ACCESS_KEY_ID="$(env_value S3_ACCESS_KEY)" \
      AWS_SECRET_ACCESS_KEY="$(env_value S3_SECRET_KEY)" duckdb_run "" --network "$network" \
      -e PGPASSWORD -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -- -csv -noheader <<<"$sql")" \
      || die "DuckDB could not write the archive to the object store (it downloads its extensions on the first run)"
    sed -n 's/^FILE|\(.*\)|\(.*\)$/    \1: \2 rows/p' <<<"$listing"
    echo "Archive written; check it with: dxos archive check --store $model/$run"
    return 0
  fi
  echo "==> Writing the finished records of $model (version $version) to $dir"
  # The password reaches the container through the environment, never the command line.
  PGPASSWORD="$(env_value POSTGRES_PASSWORD)" duckdb_run "$dir" --network "$network" -e PGPASSWORD -- \
    <<<"$sql" >/dev/null \
    || die "DuckDB could not write the archive (it downloads its postgres extension on the first run)"
  local files="" name rows sum
  for name in $(cd "$dir" && ls ./*.parquet | sed 's|^\./||'); do
    rows="$(parquet_rows "$dir" "$name")"
    sum="$(sha256sum "$dir/$name" | cut -d' ' -f1)"
    files+="${files:+,}
    {\"name\": \"$name\", \"rows\": $rows, \"sha256\": \"$sum\"}"
    echo "    $name: $rows rows"
  done
  local before_json=null
  [[ -z "$before" ]] || before_json="\"$before\""
  cat >"$dir/manifest.json" <<EOF
{
  "model": "$model",
  "modelVersion": $version,
  "run": "$run",
  "before": $before_json,
  "condition": "records with decided_at set, and their history lines",
  "files": [$files
  ]
}
EOF
  echo "Archive written; check it with: dxos archive check $dir"
}

# Reads every file of an archive back and compares its row count and SHA-256 with the manifest.
archive_check() {
  local dir="${1:?usage: dxos archive check <dir | --store model/run>}" bad=0 name rows sum want_rows want_sum
  if [[ "$dir" == --store ]]; then archive_check_store "${2:?usage: dxos archive check --store model/run}"; return; fi
  dir="$(cd "$(from_caller "$dir")" && pwd)" || die "no such directory: $1"
  [[ -f "$dir/manifest.json" ]] || die "no manifest.json in $dir"
  while read -r name want_rows want_sum; do
    rows="$(parquet_rows "$dir" "$name")"
    sum="$(sha256sum "$dir/$name" | cut -d' ' -f1)"
    if [[ "$rows" == "$want_rows" && "$sum" == "$want_sum" ]]; then
      echo "    $name: $rows rows, SHA-256 matches"
    else
      echo "    $name: $rows rows (manifest $want_rows), SHA-256 ${sum:0:12}... (manifest ${want_sum:0:12}...)" >&2
      bad=1
    fi
  done < <(sed -n 's/.*"name": "\([^"]*\)", "rows": \([0-9]*\), "sha256": "\([0-9a-f]*\)".*/\1 \2 \3/p' "$dir/manifest.json")
  (( bad == 0 )) || die "the archive does not match its manifest"
  echo "Archive matches its manifest."
}

# The same check for a run in the object store: model/run as printed by dxos archive list.
archive_check_store() {
  local ref="$1" model run target bad=0 name want_rows want_sum rows sum manifest got count=0 sql
  model="${ref%%/*}"; run="${ref#*/}"
  [[ "$model" =~ ^[a-z][a-z0-9_]*$ && "$run" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || die "not a run: $ref (expected model/run)"
  target="s3://$(archive_bucket)/$model/$run"
  manifest="$( { store_prelude
    echo "SELECT 'FILE|' || f.name || '|' || f.rows || '|' || f.sha256 FROM (SELECT unnest(files) AS f FROM read_json('$target/manifest.json'));"
    } | duckdb_store)" || die "cannot read $target/manifest.json from the object store"
  manifest="$(grep '^FILE|' <<<"$manifest" || true)"
  [[ -n "$manifest" ]] || die "the manifest of $ref lists no files"
  # First the SHA-256 of every file, then the rows of the files whose bytes are the ones written.
  sql="$(store_prelude)"
  while IFS='|' read -r _ name want_rows want_sum; do
    [[ "$name" =~ ^[A-Za-z0-9_]+\.parquet$ ]] || die "the manifest names a file that is not a Parquet file: $name"
    sql+="
SELECT 'SUM|$name|' || sha256(content) FROM read_blob('$target/$name');"
  done <<<"$manifest"
  got="$(duckdb_store <<<"$sql" 2>/dev/null)" || die "cannot read the files of $ref from the object store"
  sql="$(store_prelude)"
  while IFS='|' read -r _ name want_rows want_sum; do
    sum="$(sed -n "s/^SUM|$name|//p" <<<"$got")"
    [[ "$sum" == "$want_sum" ]] && sql+="
SELECT 'ROWS|$name|' || count(*) FROM read_parquet('$target/$name');"
  done <<<"$manifest"
  got+=$'\n'"$(duckdb_store <<<"$sql" 2>/dev/null || true)"
  while IFS='|' read -r _ name want_rows want_sum; do
    sum="$(sed -n "s/^SUM|$name|//p" <<<"$got")"
    rows="$(sed -n "s/^ROWS|$name|//p" <<<"$got")"
    if [[ "$sum" == "$want_sum" && "$rows" == "$want_rows" ]]; then
      echo "    $name: $rows rows, SHA-256 matches"
    else
      echo "    $name: ${rows:-unreadable} rows (manifest $want_rows), SHA-256 ${sum:0:12}... (manifest ${want_sum:0:12}...)" >&2
      bad=1
    fi
  done <<<"$manifest"
  (( bad == 0 )) || die "the archive does not match its manifest"
  echo "Archive matches its manifest."
}

# Runs one read-only, parameterized query over one manifest-listed Parquet file of one object-store run.
archive_query() {
  local ref="${1:-}" file="" sql="" model run name value target query_input execute_args=""
  local manifest_line manifest want_rows want_sum actual actual_rows actual_sum
  local memory_limit threads temp_limit
  local param_value_pattern="^(NULL|TRUE|FALSE|-?[0-9]+([.][0-9]+)?|'([^']|'')*')$"
  local params=()
  shift || true
  model="${ref%%/*}"; run="${ref#*/}"
  [[ "$model" =~ ^[a-z][a-z0-9_]*$ && "$run" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] \
    || die "not an archive run: $ref (expected model/YYYYMMDDTHHMMSSZ)"
  while (( $# )); do
    case "$1" in
      --file) file="${2:?--file needs a Parquet file}"; shift 2 ;;
      --file=*) file="${1#--file=}"; shift ;;
      --param) params+=("${2:?--param needs name=value}"); shift 2 ;;
      --param=*) params+=("${1#--param=}"); shift ;;
      --) shift; sql="${1:-}"; shift; (( $# == 0 )) || die "query must be one SQL statement" ;;
      -*) die "unknown option: $1" ;;
      *) [[ -z "$sql" ]] || die "query must be one SQL statement"; sql="$1"; shift ;;
    esac
  done
  [[ -n "$file" ]] || die "--file is required; choose one Parquet file in the selected archive run"
  [[ "$file" =~ ^[A-Za-z0-9_]+\.parquet$ ]] \
    || die "--file must name one Parquet file in the selected archive run"
  [[ -n "$sql" ]] || die "usage: dxos archive query model/run --file FILE [--param name=value ...] \"SELECT ... FROM archive\""
  sql="${sql%;}"
  [[ "$sql" =~ ^[[:space:]]*(SELECT|WITH|EXPLAIN)([[:space:]]|$) ]] \
    || die "archive query accepts SELECT, WITH, or EXPLAIN only"
  [[ "$sql" != *';'* ]] || die "archive query accepts one SQL statement only"
  for value in "${params[@]}"; do
    name="${value%%=*}"
    [[ "$value" == *=* && "$name" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] \
      || die "--param must use name=value with a valid parameter name"
    [[ "$value" != *$'\r'* && "$value" != *$'\n'* && "$value" != *';'* ]] \
      || die "--param values cannot contain newlines or semicolons"
    value="${value#*=}"
    [[ "$value" =~ $param_value_pattern ]] \
      || die "--param values must be NULL, TRUE, FALSE, a number, or a single-quoted string"
    [[ -z "$execute_args" ]] || execute_args+=", "
    execute_args+="$name := $value"
  done
  target="s3://$(archive_bucket)/$model/$run/$file"
  manifest="$( { store_prelude
    echo "SELECT 'FILE|' || f.rows || '|' || f.sha256 FROM (SELECT unnest(files) AS f FROM read_json('s3://$(archive_bucket)/$model/$run/manifest.json')) WHERE f.name = '$file';"
    } | duckdb_store 2>&1)" \
    || die "cannot read the manifest for $model/$run"
  manifest_line="$(grep '^FILE|' <<<"$manifest" || true)"
  [[ "$(grep -c '^FILE|' <<<"$manifest_line")" == 1 ]] \
    || die "the Parquet file $file is not listed in the archive manifest"
  IFS='|' read -r _ want_rows want_sum <<<"$manifest_line"
  memory_limit="$(env_value ARCHIVE_QUERY_MEMORY_LIMIT)"; memory_limit="${memory_limit:-2GB}"
  threads="$(env_value ARCHIVE_QUERY_THREADS)"; threads="${threads:-2}"
  temp_limit="$(env_value ARCHIVE_QUERY_TEMP_LIMIT)"; temp_limit="${temp_limit:-4GB}"
  [[ "$memory_limit" =~ ^[1-9][0-9]*(MB|GB|TB)$ ]] || die "ARCHIVE_QUERY_MEMORY_LIMIT must be a positive MB, GB, or TB value"
  [[ "$threads" =~ ^[1-9][0-9]*$ ]] || die "ARCHIVE_QUERY_THREADS must be a positive integer"
  [[ "$temp_limit" =~ ^[1-9][0-9]*(MB|GB|TB)$ ]] || die "ARCHIVE_QUERY_TEMP_LIMIT must be a positive MB, GB, or TB value"

  # The checksum runs under the same limits as the query.
  actual="$( { store_prelude
    echo "SET memory_limit = '$memory_limit'; SET threads = $threads;"
    echo "SELECT 'SUM|' || sha256(content) FROM read_blob('$target');"
    echo "SELECT 'ROWS|' || count(*) FROM read_parquet('$target');"
    } | duckdb_store 2>/dev/null)" \
    || die "cannot verify $target against its archive manifest"
  actual_sum="$(sed -n 's/^SUM|//p' <<<"$actual")"
  actual_rows="$(sed -n 's/^ROWS|//p' <<<"$actual")"
  [[ "$actual_sum" == "$want_sum" && "$actual_rows" == "$want_rows" ]] \
    || die "the Parquet file $file does not match its archive manifest"

  query_input="$(store_prelude)"
  query_input+=$'\n'"SET allowed_paths = ['$target'];"$'\n'
  query_input+="SET memory_limit = '$memory_limit';"$'\n'
  query_input+="SET threads = $threads;"$'\n'
  query_input+="SET max_temp_directory_size = '$temp_limit';"$'\n'
  query_input+="SET enable_external_access=false;"$'\n'
  query_input+="SET lock_configuration=true;"$'\n'
  query_input+="CREATE OR REPLACE TEMP VIEW archive AS SELECT * FROM read_parquet('$target');"$'\n'
  if (( ${#params[@]} )); then
    query_input+="PREPARE archive_query AS $sql;"$'\n'
    query_input+="EXECUTE archive_query($execute_args);"$'\n'
  else
    query_input+="$sql"$'\n'
  fi
  # CREATE SECRET in the prelude prints a first line "true"; drop it and keep the exit code of DuckDB.
  duckdb_store <<<"$query_input" | sed '1{/^true$/d;}'
  return "${PIPESTATUS[0]}"
}

# The runs in the object store, oldest first.
archive_list() {
  local out rows model run since upto files
  out="$(store_query "SELECT 'RUN|' || model || '|' || run || '|' || coalesce(nullif(since, ''), '-') || '|' || coalesce(upTo, '-') || '|' || len(files)
      FROM read_json('s3://$(archive_bucket)/*/*/manifest.json', columns = $MANIFEST_COLUMNS) ORDER BY model, run;")"
  rows="$(grep '^RUN|' <<<"$out" || true)"
  [[ -n "$rows" ]] || { echo "No archive in the object store yet."; return 0; }
  printf '%-16s %-17s %-21s %-21s %s\n' MODEL RUN FROM BEFORE FILES
  while IFS='|' read -r _ model run since upto files; do
    printf '%-16s %-17s %-21s %-21s %s\n' "$model" "$run" "$since" "$upto" "$files"
  done <<<"$rows"
}

# Archives, for every model with a process, the records decided before today minus ARCHIVE_AFTER_DAYS
# that no earlier run of the store holds. Records stay in the database.
archive_auto() {
  local days model upto last failed=0 models
  days="$(env_value ARCHIVE_AFTER_DAYS)"; days="${days:-365}"
  [[ "$days" =~ ^[0-9]+$ ]] || die "ARCHIVE_AFTER_DAYS in .env must be a number of days"
  [[ -n "$(compose ps -q --status running postgres)" ]] || die "Postgres is not running; run dxos start"
  upto="$(date -u -d "$days days ago" +%Y-%m-%d)"
  echo "==> $(date -u +%Y-%m-%dT%H:%M:%SZ) archiving records decided before $upto (ARCHIVE_AFTER_DAYS=$days)"
  last="$(store_query "SELECT 'LAST|' || model || '|' || max(upTo)
    FROM read_json('s3://$(archive_bucket)/*/*/manifest.json', columns = $MANIFEST_COLUMNS) GROUP BY model;")"
  models="$(compose exec -T postgres psql -U "$(env_value POSTGRES_USER)" -d "$(env_value POSTGRES_DB)" -tAc \
    "SELECT DISTINCT model FROM engine.model_version ORDER BY model")"
  for model in $models; do
    [[ -n "$(archive_entities "$model")" ]] || continue
    local since
    since="$(sed -n "s/^LAST|$model|\(.*\)$/\1/p" <<<"$last")"
    if [[ -n "$since" && ! "$since" < "${upto}T00:00:00Z" ]]; then echo "==> $model: nothing new before $upto"; continue; fi
    ( archive_model "$model" "$upto" "" 1 "$since" 1 ) || failed=1
  done
  (( failed == 0 )) || die "some models could not be archived"
}

cmd_archive_schedule() {
  local units="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
  case "${1:-}" in
    install)
      command -v systemctl >/dev/null 2>&1 || die "systemd is required; or run dxos archive auto from cron once a day"
      mkdir -p "$units"
      printf '%s\n' "[Unit]" "Description=Archive the finished records of DX-OS to the object store" "" "[Service]" "Type=oneshot" \
        "ExecStart=$DEPLOY_DIR/dxos archive auto" "StandardOutput=append:$DEPLOY_DIR/archive.log" \
        "StandardError=append:$DEPLOY_DIR/archive.log" >"$units/dxos-archive.service"
      printf '%s\n' "[Unit]" "Description=Archive the finished records of DX-OS, every day" "" "[Timer]" \
        "OnCalendar=*-*-* 03:30:00" "Persistent=true" "" "[Install]" "WantedBy=timers.target" >"$units/dxos-archive.timer"
      systemctl --user daemon-reload
      systemctl --user enable --now dxos-archive.timer
      echo "dxos archive auto runs every day at 03:30; records decided more than $(env_value ARCHIVE_AFTER_DAYS | sed 's/^$/365/') days ago go to the object store, and the run is logged to $DEPLOY_DIR/archive.log."
      echo "To keep it running after you log out: loginctl enable-linger $(id -un)" ;;
    uninstall)
      systemctl --user disable --now dxos-archive.timer 2>/dev/null || true
      rm -f "$units/dxos-archive.service" "$units/dxos-archive.timer"
      systemctl --user daemon-reload 2>/dev/null || true
      echo "dxos archive no longer runs on its own." ;;
    *) die "usage: dxos archive schedule install|uninstall" ;;
  esac
}

cmd_archive() {
  case "${1:-}" in
    check) shift; archive_check "$@" ;;
    query) shift; archive_query "$@" ;;
    list) archive_list ;;
    auto) archive_auto ;;
    schedule) shift; cmd_archive_schedule "$@" ;;
    ""|-*) die "usage: dxos archive <model> [--before YYYY-MM-DD] [--out DIR | --store] | query | check | list | auto | schedule (query takes model/run --file FILE)" ;;
    *)
      local model="$1" before="" out="" store=0
      shift
      while (( $# )); do
        case "$1" in
          --before) before="${2:?--before needs a date}"; shift 2 ;;
          --before=*) before="${1#--before=}"; shift ;;
          --out) out="${2:?--out needs a directory}"; shift 2 ;;
          --out=*) out="${1#--out=}"; shift ;;
          --store) store=1; shift ;;
          *) die "unknown option: $1" ;;
        esac
      done
      [[ -z "$out" || "$store" == 0 ]] || die "--out and --store exclude each other"
      [[ -z "$out" ]] || out="$(from_caller "$out")"
      archive_model "$model" "$before" "$out" "$store" ;;
  esac
}
