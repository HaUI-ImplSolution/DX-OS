#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Encrypted backup of everything that cannot be rebuilt from the repository: every Postgres
# database (business data, Keycloak accounts), the object store files and attachments. Uses restic (deduplicated, encrypted),
# run from a container, so only Docker is needed.
#
#   ./backup.sh                 snapshot now, then drop snapshots beyond 7 daily, 4 weekly, 6 monthly
#   ./backup.sh verify          restore the latest snapshot into a scratch Postgres and SeaweedFS and check
#                               that every model has its tables and every attachment its file
#   ./backup.sh snapshots       list the snapshots
#   ./backup.sh restore <dir>   restore the latest snapshot into <dir> (see deploy/OPERATIONS.md)
#
# Settings in deploy/.env (ENV_FILE=other.env picks another file):
#   BACKUP_PASSWORD     encrypts the repository; lose it and the backups are unreadable
#   BACKUP_DIR          local repository, default ./backups
#   BACKUP_REPOSITORY   a restic repository elsewhere (s3:..., sftp:...) instead of BACKUP_DIR; export
#                       AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY in the shell for an S3 one
# Run it from cron or a systemd timer; keep a copy of the repository outside this machine.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

ENV_FILE="${ENV_FILE:-.env}"
RESTIC_IMAGE="restic/restic:0.17.3"
SEAWEEDFS_IMAGE="$(sed -n 's/^ *image: \(chrislusf\/seaweedfs:.*\)$/\1/p' docker-compose.yml | head -1)"
POSTGRES_IMAGE="pgvector/pgvector:pg16@sha256:7b822b0aac60967beb1ea5e576b8602c94c300a157d187f385ae3e0da199b90a"

[[ -f "$ENV_FILE" ]] || { echo "$ENV_FILE not found. Run: ./make_env.sh (random secrets), or cp .env.example .env and fill every blank variable." >&2; exit 1; }
env_value() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- || true; }

POSTGRES_USER="$(env_value POSTGRES_USER)"
POSTGRES_DB="$(env_value POSTGRES_DB)"
BACKUP_PASSWORD="$(env_value BACKUP_PASSWORD)"
BACKUP_DIR="$(env_value BACKUP_DIR)"
BACKUP_REPOSITORY="$(env_value BACKUP_REPOSITORY)"
S3_ACCESS_KEY="$(env_value S3_ACCESS_KEY)"
S3_SECRET_KEY="$(env_value S3_SECRET_KEY)"
S3_BUCKET="$(env_value S3_BUCKET)"
FILE_DELETE_DELAY="$(env_value DXOS_FILE_DELETE_DELAY)"
[[ -n "$BACKUP_PASSWORD" ]] || { echo "BACKUP_PASSWORD is empty in $ENV_FILE." >&2; exit 1; }

# Passed to restic by name, so the password never appears in a command line.
export RESTIC_PASSWORD="$BACKUP_PASSWORD"
BACKUP_DIR="$(realpath -m "${BACKUP_DIR:-./backups}")"
STAGE="$BACKUP_DIR/stage"
compose() { docker compose --env-file "$ENV_FILE" "$@"; }

# restic <extra docker args> -- <restic args>
restic() {
  local docker_args=()
  while [[ "$1" != "--" ]]; do docker_args+=("$1"); shift; done
  shift
  if [[ -n "$BACKUP_REPOSITORY" ]]; then
    docker_args+=(-e "RESTIC_REPOSITORY=$BACKUP_REPOSITORY" -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY)
  else
    mkdir -p "$BACKUP_DIR"
    docker_args+=(-e RESTIC_REPOSITORY=/repo -v "$BACKUP_DIR:/repo")
  fi
  docker run --rm -i -e RESTIC_PASSWORD "${docker_args[@]}" "$RESTIC_IMAGE" "$@"
}

# Seconds of a duration such as 90s, 30m, 24h or 2d (the forms of DXOS_FILE_DELETE_DELAY).
seconds() {
  local value="${1:-24h}"
  case "$value" in
    *ms) echo 0 ;;
    *s) echo "${value%s}" ;;
    *m) echo $(( ${value%m} * 60 )) ;;
    *h) echo $(( ${value%h} * 3600 )) ;;
    *d) echo $(( ${value%d} * 86400 )) ;;
    *) echo "$value" ;;
  esac
}

do_backup() {
  local started; started="$(date +%s)"
  # The lock dxos reconcile takes, so that it does not recreate containers while the dump runs.
  exec 9>.reconcile.lock
  if command -v flock >/dev/null 2>&1 && ! flock -w 300 9; then
    echo "Another update, backup or reconcile has held deploy/.reconcile.lock for 5 minutes; try again when it ends." >&2
    exit 1
  fi
  restic -- cat config >/dev/null 2>&1 || restic -- init
  rm -rf "$STAGE"; mkdir -p "$STAGE"; chmod 700 "$STAGE"
  trap 'rm -rf "$STAGE"' EXIT
  echo "==> Dumping Postgres"
  compose exec -T postgres pg_dumpall -U "$POSTGRES_USER" | gzip > "$STAGE/postgres.sql.gz"
  [[ -s "$STAGE/postgres.sql.gz" ]] || { echo "The database dump is empty." >&2; exit 1; }
  local paths=(/stage)
  for dir in seaweedfs; do [[ -d "data/$dir" ]] && paths+=("/data/$dir"); done
  echo "==> Writing the snapshot (${paths[*]})"
  restic -v "$STAGE:/stage:ro" -v "$(pwd)/data:/data:ro" -- backup --tag dxos "${paths[@]}"
  # A removed file stays in the store for DXOS_FILE_DELETE_DELAY, so a file named in the dump is
  # still there when the store is copied, as long as the dump and the copy fit in that time.
  local took=$(( $(date +%s) - started )) delay; delay="$(seconds "$FILE_DELETE_DELAY")"
  if (( took >= delay )); then
    echo "WARNING: the dump and the copy of the store took ${took}s, not less than DXOS_FILE_DELETE_DELAY (${delay}s);" \
      "a file removed in between may be missing from this snapshot. Raise DXOS_FILE_DELETE_DELAY." >&2
  fi
  echo "==> Dropping old snapshots"
  # One retention group for every snapshot: the restic container gets a new hostname on each run
  # and the stored paths change with the services, both of which split the default grouping.
  restic -- forget --tag dxos --group-by tags --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune
  restic -- check
  echo "Backup done."
}

# A backup nobody restored is a hope: load the dump into a throwaway Postgres and look inside.
do_verify() {
  local name="dxos-backup-verify-$$" store="dxos-backup-verify-store-$$"
  trap "docker rm -f $name $store >/dev/null 2>&1 || true; docker volume rm -f $store >/dev/null 2>&1 || true" EXIT
  docker run -d --name "$name" -e POSTGRES_PASSWORD=verify "$POSTGRES_IMAGE" >/dev/null
  until docker exec "$name" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
  sleep 2
  echo "==> Restoring the latest database dump into a scratch Postgres"
  restic -- dump latest /stage/postgres.sql.gz | gunzip \
    | docker exec -i "$name" psql -U postgres -q -v ON_ERROR_STOP=1 >/dev/null
  local databases tables models missing records
  sql() { docker exec "$name" psql -U postgres -d "$POSTGRES_DB" -tAc "$1"; }
  databases="$(docker exec "$name" psql -U postgres -tAc "SELECT string_agg(datname, ' ' ORDER BY datname) FROM pg_database WHERE NOT datistemplate")"
  tables="$(sql "SELECT count(*) FROM information_schema.tables
    WHERE table_schema NOT IN ('pg_catalog', 'information_schema')")"
  echo "Databases restored: $databases"
  echo "Tables in $POSTGRES_DB: $tables"
  [[ "$tables" -gt 0 ]] || { echo "Verify FAILED: the business database is empty." >&2; exit 1; }
  if [[ "$(sql "SELECT to_regclass('engine.model_version') IS NOT NULL")" != t ]]; then
    echo "No model applied yet (engine.model_version is missing)."
    echo "Verify OK."
    return
  fi
  # Every model with an applied version keeps its records in schema m_<model>.
  models="$(sql "SELECT count(DISTINCT model) FROM engine.model_version")"
  missing="$(sql "SELECT coalesce(string_agg(DISTINCT v.model, ' '), '') FROM engine.model_version v
    WHERE NOT EXISTS (SELECT 1 FROM information_schema.tables t WHERE t.table_schema = 'm_' || v.model)")"
  records="$(sql "SELECT coalesce(sum((xpath('/row/c/text()', query_to_xml(format('SELECT count(*) AS c FROM %I.%I',
    table_schema, table_name), false, true, '')))[1]::text::bigint), 0)
    FROM information_schema.tables WHERE table_schema LIKE 'm\_%' AND table_type = 'BASE TABLE'")"
  echo "Applied models: $models, records in their tables: $records"
  [[ -z "$missing" ]] || { echo "Verify FAILED: no tables for the models: $missing" >&2; exit 1; }
  verify_files
  echo "Verify OK."
}

# Every attachment row of the dump must have its object in the copy of the store: the copy is
# restored into a scratch volume and served by a scratch SeaweedFS, and each key is looked up.
verify_files() {
  local keys count absent
  if [[ "$(sql "SELECT to_regclass('engine.attachment') IS NOT NULL")" != t ]]; then
    echo "No attachment table yet."
    return
  fi
  keys="$(sql "SELECT object_key FROM engine.attachment ORDER BY id")"
  count="$(grep -c . <<<"$keys" || true)"
  if [[ "$count" -eq 0 ]]; then
    echo "Attachments: 0"
    return
  fi
  echo "==> Restoring the copy of the store into a scratch SeaweedFS"
  docker volume create "$store" >/dev/null
  # The image keeps the filer metadata in /data/filerldb2, so the copy is served from /data.
  restic -v "$store:/restore" -- restore latest:/data/seaweedfs --target /restore >/dev/null
  docker run -d --name "$store" -e "AWS_ACCESS_KEY_ID=$S3_ACCESS_KEY" -e "AWS_SECRET_ACCESS_KEY=$S3_SECRET_KEY" \
    -e "WEED_JWT_FILER_SIGNING_KEY=$S3_SECRET_KEY" -v "$store:/data" "$SEAWEEDFS_IMAGE" \
    -logtostderr=true -v=-1 server -ip=127.0.0.1 -dir=/data -filer -volume.max=0 >/dev/null
  local tries=0
  until [[ "$(docker exec "$store" curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:8888/")" == 200 ]]; do
    (( ++tries < 60 )) || { echo "Verify FAILED: the scratch SeaweedFS did not start." >&2; exit 1; }
    sleep 2
  done
  absent="$(docker exec -i "$store" sh -c "while read -r key; do
      curl -s -o /dev/null -w '%{http_code}\n' -I \"http://127.0.0.1:8888/buckets/$S3_BUCKET/\$key\" | grep -qx 200 || echo \"\$key\"
    done" <<<"$keys")"
  echo "Attachments: $count, files missing from the copy of the store: $(grep -c . <<<"$absent" || true)"
  [[ -z "$absent" ]] || { echo "Verify FAILED: files missing for attachments: $(head -5 <<<"$absent" | tr '\n' ' ')" >&2; exit 1; }
}

case "${1:-backup}" in
  backup) do_backup ;;
  verify) do_verify ;;
  snapshots) restic -- snapshots ;;
  restore)
    [[ -n "${2:-}" ]] || { echo "Usage: ./backup.sh restore <dir>" >&2; exit 2; }
    mkdir -p "$2"
    restic -v "$(realpath "$2"):/restore" -- restore latest --target /restore ;;
  -h|--help) sed -n '2,18p' "$0" ;;
  *) echo "Unknown command: $1" >&2; exit 2 ;;
esac
