#!/bin/bash
# SPDX-License-Identifier: Apache-2.0
# Creates the role and the database of Keycloak when the data directory is first initialised.
set -euo pipefail

psql -v ON_ERROR_STOP=1 -v keycloak_password="$KEYCLOAK_DB_PASSWORD" \
  --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE keycloak_app LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE keycloak OWNER keycloak_app;
SQL
