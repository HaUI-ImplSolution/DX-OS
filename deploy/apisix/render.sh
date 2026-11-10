#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Render the gateway routes into the standalone APISIX configuration.
set -eu
: "${MONITOR_INGEST_KEY:?}"
sed "s|__MONITOR_INGEST_KEY__|$MONITOR_INGEST_KEY|g" /src/common.yaml > /out/apisix.yaml
printf '#END\n' >> /out/apisix.yaml
