#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Downloads a DX-OS release, checks its SHA-256 and installs it; published with each release as dxos-install.sh.
#
#   curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
#   bash get.sh [--version vX.Y.Z] [--prefix DIR] [--file dx-os-X.Y.Z.tar.gz] [--upgrade]
#
# --version picks a release (default: the latest), --prefix the install directory (default $DXOS_HOME or ~/.dxos),
# --file installs from a release archive already on disk (checked against dx-os-X.Y.Z.sha256 beside it when present),
# --upgrade replaces the runtime files of an existing install (dxos update uses it).
# Needs Docker with Compose v2, curl, tar and sha256sum or shasum.
set -euo pipefail

REPO="HaUI-ImplSolution/DX-OS"
VERSION=""
PREFIX="${DXOS_HOME:-$HOME/.dxos}"
FILE=""
UPGRADE=()

usage() { sed -n '3,11p' "$0" 2>/dev/null || true; }
die() { echo "dxos-install: $*" >&2; exit 1; }

while (( $# )); do
  case "$1" in
    --version) VERSION="${2:?--version needs a value}"; shift 2 ;;
    --prefix) PREFIX="${2:?--prefix needs a value}"; shift 2 ;;
    --file) FILE="${2:?--file needs a value}"; shift 2 ;;
    --upgrade) UPGRADE=(--upgrade); shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

command -v docker >/dev/null 2>&1 || die "Docker is required: https://docs.docker.com/engine/install/"
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required (docker compose version)"
command -v tar >/dev/null 2>&1 || die "tar is required"
if command -v sha256sum >/dev/null 2>&1; then
  sha256() { sha256sum "$@"; }
elif command -v shasum >/dev/null 2>&1; then
  sha256() { shasum -a 256 "$@"; }
else
  die "sha256sum or shasum is required"
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

if [[ -n "$FILE" ]]; then
  [[ -f "$FILE" ]] || die "$FILE not found"
  ARCHIVE="$(basename "$FILE")"
  [[ "$ARCHIVE" =~ ^dx-os-([0-9]+\.[0-9]+\.[0-9]+.*)\.tar\.gz$ ]] || die "$ARCHIVE is not named dx-os-X.Y.Z.tar.gz"
  NUMBER="${BASH_REMATCH[1]}"
  cp "$FILE" "$WORK/$ARCHIVE"
  SUMS="$(dirname "$FILE")/dx-os-$NUMBER.sha256"
  if [[ -f "$SUMS" ]]; then
    cp "$SUMS" "$WORK/"
  else
    echo "==> No dx-os-$NUMBER.sha256 beside $FILE; the archive is not checked"
  fi
else
  command -v curl >/dev/null 2>&1 || die "curl is required"
  if [[ -z "$VERSION" ]]; then
    latest="$(curl -fsSLI -o /dev/null -w '%{url_effective}' "https://github.com/$REPO/releases/latest")" \
      || die "cannot reach GitHub to find the latest release"
    VERSION="${latest##*/}"
  fi
  [[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+ ]] || die "no release found (got '$VERSION'); pass --version vX.Y.Z"
  NUMBER="${VERSION#v}"
  ARCHIVE="dx-os-$NUMBER.tar.gz"
  BASE="https://github.com/$REPO/releases/download/$VERSION"
  echo "==> Downloading DX-OS $VERSION"
  curl -fL --progress-bar -o "$WORK/$ARCHIVE" "$BASE/$ARCHIVE" || die "cannot download $BASE/$ARCHIVE"
  curl -fsSL -o "$WORK/dx-os-$NUMBER.sha256" "$BASE/dx-os-$NUMBER.sha256" || die "cannot download the checksums of $VERSION"
fi

if [[ -f "$WORK/dx-os-$NUMBER.sha256" ]]; then
  echo "==> Checking SHA-256"
  line="$(grep -E "[[:space:]]\*?$ARCHIVE\$" "$WORK/dx-os-$NUMBER.sha256" || true)"
  [[ -n "$line" ]] || die "dx-os-$NUMBER.sha256 has no line for $ARCHIVE"
  (cd "$WORK" && printf '%s\n' "$line" | sha256 -c - >/dev/null) || die "$ARCHIVE does not match its SHA-256; not installed"
fi

tar -xzf "$WORK/$ARCHIVE" -C "$WORK"
[[ -f "$WORK/dx-os-$NUMBER/deploy/install.sh" ]] || die "$ARCHIVE has no deploy/install.sh"
bash "$WORK/dx-os-$NUMBER/deploy/install.sh" ${UPGRADE[@]+"${UPGRADE[@]}"} "$PREFIX" "v$NUMBER"
