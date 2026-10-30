#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Installs the stack into a directory of its own, so it runs without the source tree.
#
#   ./install.sh <prefix> [image-tag]
#
# Copies deploy/ and what its compose file mounts (data/, sso/, bi/, chat/, ai/weights/) into <prefix>,
# keeping their relative layout, and writes <prefix>/deploy/.env with random secrets.
# Images are pulled from the registry in DXOS_IMAGE_PREFIX (ghcr.io/haui-implsolution/dxos), nothing is built there.
# [image-tag] is latest (default) or a release tag such as v1.1.1.
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 || "$1" == -* ]]; then
  sed -n '3,6p' "$0" >&2
  exit 1
fi

SOURCE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$1"
PREFIX="$(cd "$1" && pwd)"
TAG="${2:-latest}"

if [[ "$PREFIX" == "$SOURCE" ]]; then
  echo "<prefix> must be a different directory from the source tree." >&2
  exit 1
fi
if [[ -e "$PREFIX/deploy/.env" ]]; then
  echo "$PREFIX/deploy/.env exists; this directory is already installed (update it with deploy/update.sh)." >&2
  exit 1
fi

echo "==> Copying the runtime files to $PREFIX"
tar -C "$SOURCE" -cf - \
  --exclude='deploy/data' --exclude='deploy/.env' --exclude='deploy/.env.*' --exclude='deploy/VPS.md' \
  --exclude='__pycache__' \
  deploy \
  data \
  sso/realm-dxos.json \
  bi/basemap_light.json bi/bootstrap.sh bi/bootstrap_datasources.py bi/chart_query_context.py \
  bi/farm_dashboard_export.zip bi/grant_engineer_access.py bi/superset_config.py \
  chat/bootstrap.sh \
  ai/weights/crop_classifier.onnx \
  | tar -C "$PREFIX" -xf -
cp "$SOURCE/deploy/.env.example" "$PREFIX/deploy/.env.example"

echo "==> Writing $PREFIX/deploy/.env"
python3 "$SOURCE/deploy/demo/make_ci_env.py" "$PREFIX/deploy/.env"
{
  echo "DXOS_IMAGE_PREFIX=ghcr.io/haui-implsolution/dxos"
  echo "DXOS_IMAGE_TAG=$TAG"
} >> "$PREFIX/deploy/.env"

cat <<EOF

Installed. The source tree is no longer needed; to run the stack:

  cd $PREFIX/deploy
  docker compose pull
  python3 -m pip install pillow     # for the demo data scripts
  ./start.sh
EOF
