# Connecting a reporting tool

DX-OS creates the PostgreSQL role `report_ro` so that the operator's reporting tool can read the live business data.
The role has `USAGE` on the schema `m_<model>` and `SELECT` on each table that the model file declares, but no write permission.
Each new table created by a model apply also receives this read permission.

A direct connection to the database does not apply the `own`, `unit` or `assigned` scopes of the API.
A report that needs row-level permissions should come from `GET /api/v1/data/{model}/{entity}/report`, which groups by a field and counts, sums or averages the rows the caller may see.
`report_ro` can read every row of every business table it has been granted, so give the credentials only to a reporting tool that the operator trusts.
The role is not granted read access to the internal `engine` schema, to the Keycloak database or to the data of the monitoring screen.

The guide below uses [Apache Superset](https://superset.apache.org/) as the example because its source code is under the [Apache License 2.0](https://github.com/apache/superset/blob/master/LICENSE.txt).
Superset is an optional component that the operator installs separately; DX-OS does not install it, update it or put it in the stack.

## Prepare the connection details

`deploy/make_env.sh` generates a random `REPORT_DB_PASSWORD` and sets mode `600` on `deploy/.env`.
If you create `.env` by hand, set this variable before applying the first model.
The core service creates `report_ro` at the first apply and grants read access when each model is applied.

| Field | Value |
|---|---|
| Host | The private PostgreSQL endpoint that the reporting tool can see; on the DX-OS machine the default is `127.0.0.1` |
| Port | The port part of `POSTGRES_HOST_PORT`; `5432` by default |
| Database | The value of `POSTGRES_DB`; `dxos` by default |
| Username | `report_ro` |
| Password | The value of `REPORT_DB_PASSWORD` in `deploy/.env` |
| Business schema | `m_<model name>`, for example `m_onestop` and `m_equipment` |

Do not use `POSTGRES_USER` for the reporting tool.
Do not copy `.env`, a URI with a password, a screenshot of the configuration or a result with real data into an issue, a log or the repository.

## Keep the connection on a private network

The default `POSTGRES_HOST_PORT=127.0.0.1:5432` publishes PostgreSQL only on the loopback of the DX-OS machine.
Keep this restriction when the reporting tool runs on the same machine.
If the tool runs in a container, `127.0.0.1` is the container itself; point the tool at a private endpoint of the host or a private Docker network instead of publishing the port to the Internet.

When the reporting tool runs on another machine, use a VPN or an SSH tunnel.
The example below runs on the reporting machine and opens the tunnel port only on that machine's loopback:

```bash
ssh -N -L 127.0.0.1:15432:127.0.0.1:5432 <user>@<dxos-host>
```

The tool then connects to `127.0.0.1:15432`.
Do not change `POSTGRES_HOST_PORT` to `0.0.0.0:5432` or just `5432` to serve remote reports.
If Superset runs in a container, put the tunnel or the private endpoint in the same closed network as the container and do not bind it to a public interface.

## Connect Apache Superset

Superset needs the PostgreSQL driver in its own environment.
Install the driver by the project's official [database connection guide](https://superset.apache.org/docs/databases/) and [PostgreSQL reference](https://superset.apache.org/user-docs/databases/supported/postgresql/).
That driver belongs to the Superset deployment, not to the dependencies of DX-OS.

1. In Superset, open **Settings → Data: Database Connections** and add a PostgreSQL database.
2. Enter the host, port, database, username and password from the table above, or use this URI with URL-encoded values:

   ```text
   postgresql+psycopg2://report_ro:<password>@<private-host>:<port>/<database>
   ```

3. Run **Test Connection**, then save the connection.
4. Create datasets only from the `m_<model>` schemas you need to report on and keep the DML options off.

Do not use a real URI in a shell command because the password can land in the history or in the process list.
Store the password with the secret management of the Superset deployment and limit who may administer connections in Superset.

## Check the read scope

Run the query below with the `report_ro` connection to list the business tables that were granted `SELECT`:

```sql
SELECT table_schema, table_name
FROM information_schema.role_table_grants
WHERE grantee = current_user
  AND privilege_type = 'SELECT'
  AND table_schema LIKE 'm\_%' ESCAPE '\'
ORDER BY table_schema, table_name;
```

The next example totals the student one-stop records and the equipment loan slips by the same Keycloak account id.
`FULL OUTER JOIN` keeps people who appear in only one model, so the result does not depend on whether the two models already share the same set of users:

```sql
WITH one_stop AS (
  SELECT created_by, COUNT(*) AS request_count
  FROM m_onestop.request
  GROUP BY created_by
), equipment AS (
  SELECT created_by, COUNT(*) AS loan_count
  FROM m_equipment.loan
  GROUP BY created_by
)
SELECT
  COALESCE(one_stop.created_by, equipment.created_by) AS user_id,
  COALESCE(one_stop.request_count, 0) AS one_stop_requests,
  COALESCE(equipment.loan_count, 0) AS equipment_loans
FROM one_stop
FULL OUTER JOIN equipment USING (created_by)
ORDER BY user_id;
```

`created_by` is the Keycloak account id, not the display name.
This query only joins the data by a shared identifier and creates no foreign key between the two schemas.

Check the write permission with a statement that cannot change any row even if the permission were set up wrongly:

```sql
UPDATE m_onestop.request
SET reason = reason
WHERE false;
```

PostgreSQL must answer `permission denied for table request`.
If the reporting tool blocks DML before sending it to the database, run the statement with `psql` using the `report_ro` account itself.

## Rotate the password

Changing `REPORT_DB_PASSWORD` in `.env` does not change the password of a role that already exists.
Rotate the password in PostgreSQL, in the reporting tool deployment and in `.env` in one operation:

1. Create a new random password in your password manager.
2. Open an administrative `psql` without putting the new password on the command line:

   ```bash
   cd deploy
   docker compose exec postgres sh -lc 'psql --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'
   ```

3. At the `psql` prompt, run `\password report_ro`, enter the new password twice and do not close the administrative session yet.
4. Update `REPORT_DB_PASSWORD` in `deploy/.env`, keeping the file mode `600`, then update the connection secret in Superset.
5. If you must cut off at once the connections that still hold the old password, run this in the administrative session:

   ```sql
   SELECT pg_terminate_backend(pid)
   FROM pg_stat_activity
   WHERE usename = 'report_ro'
     AND pid <> pg_backend_pid();
   ```

6. Run **Test Connection** again, the read query and the refused write test above, then leave `psql` with `\q`.

Ending the sessions breaks the dashboards that are running.
You do not need to restart PostgreSQL; keep `.env` in sync so that a new install or the next restore uses the right credentials.

## Checklist

- The tool signs in as `report_ro`, not as `POSTGRES_USER`.
- PostgreSQL is reachable only over loopback, a VPN, a tunnel or a private network.
- Only trusted operators administer the connection because `report_ro` reads every business row.
- A query across two models runs and the write test answers `permission denied`.
- The password does not appear in the repository, in logs, in a shared URI or in a screenshot.
- After each rotation, PostgreSQL, `.env` and the reporting tool use the same value.
