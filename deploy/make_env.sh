#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Writes an env file from .env.example: blank passwords, secrets and keys get a random value,
# other blank variables stay blank. Used for the first setup of a stack (then set PUBLIC_HOST and the
# like before the first start), by install.sh and by CI.
#
#   deploy/make_env.sh [OUTPUT] [--force]     (default: deploy/.env)
#   deploy/make_env.sh [OUTPUT] --update-assistant  add missing assistant defaults without replacing values
#
# An existing OUTPUT is kept unless --force is given.
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FORCE=false
UPDATE_ASSISTANT=false
OUTPUT=""
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    --update-assistant) UPDATE_ASSISTANT=true ;;
    -h|--help) sed -n '3,9p' "$0"; exit 0 ;;
    -*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) OUTPUT="$arg" ;;
  esac
done
OUTPUT="${OUTPUT:-$DEPLOY_DIR/.env}"

if [[ "$UPDATE_ASSISTANT" == true ]]; then
  [[ -f "$OUTPUT" ]] || { echo "Create $OUTPUT with make_env.sh first." >&2; exit 1; }
  umask 077
  tmp="$(mktemp "${OUTPUT}.XXXXXX")"
  trap 'rm -f "$tmp"' EXIT
  cp "$OUTPUT" "$tmp"
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" == ASSISTANT_*=* ]] || continue
    key="${line%%=*}"
    if ! grep -q "^${key}=" "$tmp"; then
      [[ -z "$(tail -c1 "$tmp")" ]] || echo >> "$tmp"
      printf '%s\n' "$line" >> "$tmp"
    fi
  done < "$DEPLOY_DIR/.env.example"
  chmod 600 "$tmp"
  mv "$tmp" "$OUTPUT"
  trap - EXIT
  exit 0
fi

if [[ -e "$OUTPUT" && "$FORCE" != true ]]; then
  echo "$OUTPUT already exists; pass --force to replace it." >&2
  exit 1
fi

# 32 hex characters (128 bits) from the kernel's random source.
random_hex() { od -An -N16 -tx1 /dev/urandom | tr -d ' \n'; }

umask 077
tmp="$(mktemp "${OUTPUT}.XXXXXX")"
trap 'rm -f "$tmp"' EXIT
while IFS= read -r line || [[ -n "$line" ]]; do
  if [[ "$line" =~ ^([A-Za-z0-9_]+)=[[:space:]]*$ ]]; then
    key="${BASH_REMATCH[1]}"
    case "$key" in
      POSTGRES_USER) line="$key=dxos" ;;
      S3_ACCESS_KEY) line="$key=dxos-s3" ;;
      *_PASSWORD|*_SECRET|*_KEY) line="$key=$(random_hex)" ;;
      *) line="$key=" ;;
    esac
  fi
  printf '%s\n' "$line"
done < "$DEPLOY_DIR/.env.example" > "$tmp"
chmod 600 "$tmp"
mv "$tmp" "$OUTPUT"
trap - EXIT
