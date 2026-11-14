#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Prepares Postgres for the monitoring service; safe to run again.
#   - database dxos: role monitor_app, schema monitor (its own tables), read access to audit_log only;
#   - database keycloak: role keycloak_ro, read access to one view of the sign-in events.
# Needs PGHOST, PGUSER, PGPASSWORD, POSTGRES_DB, MONITOR_DB_PASSWORD, KEYCLOAK_READONLY_DB_PASSWORD.
set -euo pipefail

psql -v ON_ERROR_STOP=1 -d "$POSTGRES_DB" -v monitor_pw="$MONITOR_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE monitor_app LOGIN PASSWORD %L', :'monitor_pw')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'monitor_app') \gexec
SELECT format('ALTER ROLE monitor_app PASSWORD %L', :'monitor_pw') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO monitor_app', current_database()) \gexec
CREATE SCHEMA IF NOT EXISTS monitor AUTHORIZATION monitor_app;
GRANT USAGE ON SCHEMA public TO monitor_app;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM monitor_app;
GRANT SELECT ON public.audit_log TO monitor_app;
SQL
echo "monitor-init: role monitor_app and schema monitor are ready"

# The view is optional: without it the screen only lacks the sign-in figures.
if psql -v ON_ERROR_STOP=1 -d keycloak -v readonly_pw="$KEYCLOAK_READONLY_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE keycloak_ro LOGIN PASSWORD %L', :'readonly_pw')
WHERE NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'keycloak_ro') \gexec
SELECT format('ALTER ROLE keycloak_ro PASSWORD %L', :'readonly_pw') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO keycloak_ro', current_database()) \gexec
CREATE OR REPLACE VIEW public.vw_auth_events AS
SELECT to_timestamp(event_time / 1000.0) AS occurred_at,
       type,
       ip_address,
       client_id,
       error,
       substring(details_json from '"username":"([^"]*)"') AS username
FROM public.event_entity;
GRANT USAGE ON SCHEMA public TO keycloak_ro;
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM keycloak_ro;
GRANT SELECT ON public.vw_auth_events TO keycloak_ro;
SQL
then
  echo "monitor-init: role keycloak_ro and view vw_auth_events are ready"
else
  echo "monitor-init: the sign-in events of Keycloak are not available, continuing without them" >&2
fi
