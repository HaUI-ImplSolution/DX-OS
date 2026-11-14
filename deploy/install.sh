#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Installs the stack into a directory of its own, so it runs without the source tree.
#
#   ./install.sh [--upgrade] [prefix] [image-tag]
#
# [prefix] defaults to $DXOS_HOME or ~/.dxos; [image-tag] is latest (default) or a release tag such as v2.0.0.
# Copies deploy/, what its compose file mounts (sso/) and the scripts that dxos conformance runs (e2e/api/) into <prefix>, keeping their relative layout,
# writes <prefix>/deploy/.env with random secrets and links the dxos command into ~/.local/bin.
# --upgrade replaces runtime files, keeps .env, data/ and apisix/local.yaml, adds the variables
# a newer .env.example brings and sets the image tag.
# Images are pulled from DXOS_IMAGE_PREFIX (default ghcr.io/haui-implsolution/dxos), nothing is built there;
# DXOS_IMAGE_PREFIX and DXOS_IMAGE_TAG in the environment override the defaults (tests with local images).
set -euo pipefail

UPGRADE=false
ARGS=()
for arg in "$@"; do
  case "$arg" in
    --upgrade) UPGRADE=true ;;
    -h|--help) sed -n '3,13p' "$0"; exit 0 ;;
    -*) echo "Unknown option: $arg" >&2; exit 2 ;;
    *) ARGS+=("$arg") ;;
  esac
done
if (( ${#ARGS[@]} > 2 )); then
  sed -n '3,13p' "$0" >&2
  exit 2
fi

SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "${ARGS[0]:-${DXOS_HOME:-$HOME/.dxos}}"
PREFIX="$(cd "${ARGS[0]:-${DXOS_HOME:-$HOME/.dxos}}" && pwd)"
TAG="${DXOS_IMAGE_TAG:-${ARGS[1]:-latest}}"
IMAGE_PREFIX="${DXOS_IMAGE_PREFIX:-ghcr.io/haui-implsolution/dxos}"
ENV_FILE="$PREFIX/deploy/.env"
MANIFEST="$PREFIX/.dxos-files"
BIN_DIR="${DXOS_BIN_DIR:-$HOME/.local/bin}"

if [[ "$PREFIX" == "$SOURCE" ]]; then
  echo "<prefix> must be a different directory from the source tree." >&2
  exit 1
fi
if [[ -e "$ENV_FILE" && "$UPGRADE" != true ]]; then
  echo "$PREFIX is already installed; upgrade it with: dxos update" >&2
  exit 1
fi
if [[ ! -e "$ENV_FILE" && "$UPGRADE" == true ]]; then
  echo "$PREFIX is not installed yet; run install.sh without --upgrade." >&2
  exit 1
fi

# Runtime files, relative to the source root: everything in deploy/ but local state and env files
# (.env.example stays), the realm file, and the three scripts that dxos conformance runs.
runtime_files() {
  (cd "$SOURCE" && find deploy sso/realm-dxos.json e2e/api/kc.py e2e/api/check_api.py e2e/api/check_conformance.py \
    \( -path deploy/data -o -path deploy/backups -o -name __pycache__ \) -prune -o \
    -type f ! \( -name '.env*' ! -name '.env.example' \) ! -name 'VPS.md' \
    ! -path deploy/apisix/local.yaml -print) | LC_ALL=C sort
}

# Sets KEY=VALUE in the env file, replacing the last assignment or appending one.
set_env() {
  local key="$1" value="$2" tmp
  tmp="$(mktemp "$ENV_FILE.XXXXXX")"
  awk -v key="$key" -v value="$value" '
    { lines[NR] = $0; if (index($0, key "=") == 1) last = NR }
    END {
      for (i = 1; i <= NR; i++) print (i == last ? key "=" value : lines[i])
      if (!last) print key "=" value
    }' "$ENV_FILE" > "$tmp"
  chmod 600 "$tmp"
  mv "$tmp" "$ENV_FILE"
}

NEW_FILES="$(runtime_files)"
if [[ "$UPGRADE" == true && -f "$MANIFEST" ]]; then
  echo "==> Removing the runtime files the new version no longer has"
  comm -23 <(LC_ALL=C sort "$MANIFEST") <(printf '%s\n' "$NEW_FILES") | while IFS= read -r stale; do
    [[ "$stale" == deploy/apisix/local.yaml ]] && continue
    [[ -n "$stale" ]] && rm -f "$PREFIX/$stale"
  done
fi

echo "==> Copying the runtime files to $PREFIX"
printf '%s\n' "$NEW_FILES" | tar -C "$SOURCE" -cf - -T - | tar -C "$PREFIX" -xf -
printf '%s\n' "$NEW_FILES" > "$MANIFEST"

if [[ "$UPGRADE" == true ]]; then
  echo "==> Adding the variables this version brings to $ENV_FILE"
  fresh="$(mktemp)"
  trap 'rm -f "$fresh"' EXIT
  "$PREFIX/deploy/make_env.sh" "$fresh" --force
  while IFS= read -r line; do
    key="${line%%=*}"
    if [[ "$line" =~ ^[A-Za-z0-9_]+= ]] && ! grep -q "^$key=" "$ENV_FILE"; then
      # An install that never chose the virus scan keeps it off: the scan host without the profile av would refuse every upload.
      [[ "$key" == STORAGE_SCAN_HOST ]] && line="STORAGE_SCAN_HOST="
      printf '%s\n' "$line" >> "$ENV_FILE"
      echo "    added $key"
    fi
  done < "$fresh"
else
  echo "==> Writing $ENV_FILE"
  "$PREFIX/deploy/make_env.sh" "$ENV_FILE"
  set_env COMPOSE_PROJECT_NAME dxos
  set_env DXOS_IMAGE_PREFIX "$IMAGE_PREFIX"
fi
set_env DXOS_IMAGE_TAG "$TAG"

mkdir -p "$BIN_DIR"
ln -sfn "$PREFIX/deploy/dxos" "$BIN_DIR/dxos"
echo "==> Linked $BIN_DIR/dxos"

echo
if [[ "$UPGRADE" == true ]]; then
  echo "Runtime files of $PREFIX upgraded to $TAG."
else
  echo "Installed into $PREFIX. The source tree is no longer needed."
  echo
  echo "Start DX-OS with:"
  echo
  echo "  dxos start"
fi
case ":$PATH:" in
  *":$BIN_DIR:"*) ;;
  *) echo; echo "$BIN_DIR is not in PATH; add it (e.g. export PATH=\"$BIN_DIR:\$PATH\" in ~/.bashrc) or run $BIN_DIR/dxos." ;;
esac
