#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
# Sourced by install.sh and dxos.

# The files an install keeps as runtime files, relative to the source root given: everything in deploy/ but
# local state and env files (.env*, *.env; .env.example stays), the realm file, and the three scripts that
# dxos conformance runs.
runtime_files() {
  (cd "$1" && find deploy sso/realm-dxos.json e2e/api/kc.py e2e/api/check_api.py e2e/api/check_conformance.py \
    \( -path deploy/data -o -path deploy/backups -o -path deploy/gitops -o -name __pycache__ \) -prune -o \
    -type f ! \( -name '.env*' ! -name '.env.example' \) ! -name '*.env' ! -name 'VPS.md' \
    ! -path deploy/apisix/local.yaml -print) | LC_ALL=C sort
}
