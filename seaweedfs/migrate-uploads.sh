#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Copies the photos of the old local upload folder into the storage bucket, keeping every file name
# as the object key, then checks that every file now exists as an object.
#
#   ./migrate-uploads.sh [source-dir]      (default: deploy/data/uploads)
#
# Safe to run more than once. It never deletes anything: remove the old folder yourself once the
# check passes and the photos open in the app.
# Uploads go through curl with S3 signing, which also runs on CPUs without x86-64-v2.
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

EXPECTED="$(find "$SOURCE" -type f | wc -l)"

# Runs a shell loop over the source files with s3 <curl args> signing requests for the bucket.
in_store() {
  docker run --rm --network "$NETWORK" -v "$SOURCE:/source:ro" -w /source \
    -e S3_ACCESS_KEY -e S3_SECRET_KEY -e S3_BUCKET --entrypoint sh curlimages/curl:8.11.1 -c '
      s3() { curl -sS --fail --aws-sigv4 "aws:amz:us-east-1:s3" --user "$S3_ACCESS_KEY:$S3_SECRET_KEY" "$@"; }
      url() { echo "http://seaweedfs:8333/$S3_BUCKET/${1#./}"; }
      '"$1"
}

echo "==> Copying $EXPECTED file(s) from $SOURCE to bucket $S3_BUCKET"
in_store 'find . -type f | while read -r f; do s3 -o /dev/null -T "$f" "$(url "$f")" || echo "Upload failed: ${f#./}" >&2; done'

MISSING="$(in_store 'find . -type f | while read -r f; do s3 -o /dev/null -I "$(url "$f")" 2>/dev/null || echo "${f#./}"; done')"
if [[ -n "$MISSING" ]]; then
  echo "These files are not in the bucket yet; keep the old folder:" >&2
  echo "$MISSING" >&2
  exit 1
fi
echo "All $EXPECTED file(s) are in the bucket. The old folder was left untouched."
