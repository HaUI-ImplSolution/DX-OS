#!/bin/sh
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Follow atomic replacements of the generated configuration directory.
set -eu
ln -sfn /usr/local/apisix/generated/apisix.yaml /usr/local/apisix/conf/apisix.yaml
exec /docker-entrypoint.sh "$@"
