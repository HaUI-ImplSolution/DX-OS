#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Render common routes and optional fragments into the standalone APISIX configuration.
set -eu
: "${MONITOR_INGEST_KEY:?}"
awk '
  /^routes:$/ {
    print
    for (i = 2; i < ARGC; i++)
      while ((getline line < ARGV[i]) > 0) print line
    ARGC = 2
    next
  }
  { print }
' /src/common.yaml /optional/*.yaml |
  sed "s|__MONITOR_INGEST_KEY__|$MONITOR_INGEST_KEY|g" > /out/apisix.yaml
printf '#END\n' >> /out/apisix.yaml
