#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Checks a release archive before it is published.
#
#   .github/scripts/check_archive.sh dx-os-1.2.3.tar.gz 1.2.3
#
# The archive must have the legal and documentation files at its root and no file that looks like a secret.
# Tags before v1.1.0 have no NOTICE or COPYRIGHT.txt and are not packaged again.
set -euo pipefail

archive="${1:?usage: check_archive.sh <archive.tar.gz> <version>}"
version="${2:?usage: check_archive.sh <archive.tar.gz> <version>}"

list="$(mktemp)"
trap 'rm -f "$list"' EXIT
tar -tzf "$archive" > "$list"

for file in LICENSE NOTICE COPYRIGHT.txt README.md CHANGELOG.md; do
  grep -qx "dx-os-${version}/${file}" "$list" \
    || { echo "::error::The archive has no ${file}"; exit 1; }
done

if secret=$(grep -E '(^|/)\.env$|(^|/)id_rsa|\.pem$|\.key$' "$list"); then
  echo "::error::The archive holds files that look like secrets:"
  echo "$secret"
  exit 1
fi
echo "The archive has its legal files and nothing that looks like a secret ($(wc -l < "$list") entries)."
