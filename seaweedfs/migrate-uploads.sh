#!/usr/bin/env bash
# Copies the photos of the old local upload folder into the storage bucket, keeping every file name
# as the object key, then checks that every file now exists as an object.
#
#   ./migrate-uploads.sh [source-dir]      (default: deploy/data/uploads)
#
# Safe to run more than once. It never deletes anything: remove the old folder yourself once the
# check passes and the photos open in the app.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE="${1:-$HERE/../deploy/data/uploads}"
[[ -d "$SOURCE" ]] || { echo "Source directory not found: $SOURCE" >&2; exit 1; }
SOURCE="$(cd "$SOURCE" && pwd)"

cd "$HERE/../deploy"
[[ -f .env ]] || { echo "deploy/.env not found." >&2; exit 1; }

set -a
# shellcheck disable=SC1091
source .env
set +a

STORE_CONTAINER="$(docker compose ps -q seaweedfs)"
[[ -n "$STORE_CONTAINER" ]] || { echo "The seaweedfs service is not running. Run: docker compose up -d seaweedfs" >&2; exit 1; }
NETWORK="$(docker inspect -f '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{end}}' "$STORE_CONTAINER")"

SOURCE_DIR="$SOURCE"
EXPECTED="$(find "$SOURCE_DIR" -type f | wc -l)"

aws() {
  docker run --rm --network "$NETWORK" -v "$SOURCE_DIR:/source:ro" \
    -e AWS_ACCESS_KEY_ID="$S3_ACCESS_KEY" -e AWS_SECRET_ACCESS_KEY="$S3_SECRET_KEY" \
    -e AWS_DEFAULT_REGION=us-east-1 amazon/aws-cli --endpoint-url http://seaweedfs:8333 "$@"
}

echo "==> Copying $EXPECTED file(s) from $SOURCE_DIR to bucket $S3_BUCKET"
aws s3 sync /source "s3://$S3_BUCKET" --only-show-errors

MISSING="$(comm -23 \
  <(cd "$SOURCE_DIR" && find . -type f | sed 's|^\./||' | sort) \
  <(aws s3 ls "s3://$S3_BUCKET" --recursive | awk '{ $1=$2=$3=""; sub(/^ +/, ""); print }' | sort))"
if [[ -n "$MISSING" ]]; then
  echo "These files are not in the bucket yet; keep the old folder:" >&2
  echo "$MISSING" >&2
  exit 1
fi
echo "All $EXPECTED file(s) are in the bucket. The old folder was left untouched."
