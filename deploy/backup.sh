#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Encrypted backup of everything that cannot be rebuilt from the repository: every Postgres
# database (business data, Keycloak accounts, Mattermost, vector store), the object store photos and
# attachments, Mattermost files and the Superset metadata. Uses restic (deduplicated, encrypted),
# run from a container, so only Docker is needed.
#
#   ./backup.sh                 snapshot now, then drop snapshots beyond 7 daily, 4 weekly, 6 monthly
#   ./backup.sh verify          restore the latest database dump into a scratch Postgres and check it
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
POSTGRES_IMAGE="pgvector/pgvector:pg16@sha256:7b822b0aac60967beb1ea5e576b8602c94c300a157d187f385ae3e0da199b90a"

[[ -f "$ENV_FILE" ]] || { echo "$ENV_FILE not found. Run: python3 demo/make_ci_env.py .env (random secrets), or cp .env.example .env and fill every blank variable." >&2; exit 1; }
env_value() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- || true; }

POSTGRES_USER="$(env_value POSTGRES_USER)"
POSTGRES_DB="$(env_value POSTGRES_DB)"
BACKUP_PASSWORD="$(env_value BACKUP_PASSWORD)"
BACKUP_DIR="$(env_value BACKUP_DIR)"
BACKUP_REPOSITORY="$(env_value BACKUP_REPOSITORY)"
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

do_backup() {
  restic -- cat config >/dev/null 2>&1 || restic -- init
  rm -rf "$STAGE"; mkdir -p "$STAGE"; chmod 700 "$STAGE"
  trap 'rm -rf "$STAGE"' EXIT
  echo "==> Dumping Postgres"
  compose exec -T postgres pg_dumpall -U "$POSTGRES_USER" | gzip > "$STAGE/postgres.sql.gz"
  [[ -s "$STAGE/postgres.sql.gz" ]] || { echo "The database dump is empty." >&2; exit 1; }
  local paths=(/stage)
  for dir in seaweedfs mattermost superset; do [[ -d "data/$dir" ]] && paths+=("/data/$dir"); done
  echo "==> Writing the snapshot (${paths[*]})"
  restic -v "$STAGE:/stage:ro" -v "$(pwd)/data:/data:ro" -- backup --tag dxos "${paths[@]}"
  echo "==> Dropping old snapshots"
  # One retention group for every snapshot: the restic container gets a new hostname on each run
  # and the stored paths change with the services, both of which split the default grouping.
  restic -- forget --tag dxos --group-by tags --keep-daily 7 --keep-weekly 4 --keep-monthly 6 --prune
  restic -- check
  echo "Backup done."
}

# A backup nobody restored is a hope: load the dump into a throwaway Postgres and look inside.
do_verify() {
  local name="dxos-backup-verify-$$"
  trap "docker rm -f $name >/dev/null 2>&1 || true" EXIT
  docker run -d --name "$name" -e POSTGRES_PASSWORD=verify "$POSTGRES_IMAGE" >/dev/null
  until docker exec "$name" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done
  sleep 2
  echo "==> Restoring the latest database dump into a scratch Postgres"
  restic -- dump latest /stage/postgres.sql.gz | gunzip \
    | docker exec -i "$name" psql -U postgres -q -v ON_ERROR_STOP=1 >/dev/null
  local databases tables
  databases="$(docker exec "$name" psql -U postgres -tAc "SELECT string_agg(datname, ' ' ORDER BY datname) FROM pg_database WHERE NOT datistemplate")"
  tables="$(docker exec "$name" psql -U postgres -d "$POSTGRES_DB" -tAc "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")"
  echo "Databases restored: $databases"
  echo "Tables in $POSTGRES_DB: $tables"
  [[ "$tables" -gt 0 ]] || { echo "Verify FAILED: the business database is empty." >&2; exit 1; }
  echo "Verify OK."
}

case "${1:-backup}" in
  backup) do_backup ;;
  verify) do_verify ;;
  snapshots) restic -- snapshots ;;
  restore)
    [[ -n "${2:-}" ]] || { echo "Usage: ./backup.sh restore <dir>" >&2; exit 2; }
    mkdir -p "$2"
    restic -v "$(realpath "$2"):/restore" -- restore latest --target /restore ;;
  -h|--help) sed -n '2,17p' "$0" ;;
  *) echo "Unknown command: $1" >&2; exit 2 ;;
esac
