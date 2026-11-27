# Monitoring and logs

A document for the person who watches the system: the gateway log, sign-in events, rate limits, the audit inside the backend, the metrics for Prometheus and the monitoring screen.
Every request into the stack goes through the APISIX gateway, so the gateway log is the main access record.
Sign-in events live in Keycloak.
Installation and the `dxos` commands are in [Install and operations](../OPERATIONS.md) (Vietnamese).

## The gateway log

Each request is one JSON line on the stdout of the `apisix` container.

| Field | Meaning |
|---|---|
| `time` | The time, ISO 8601 |
| `remote_addr` | The address of the client |
| `host`, `method`, `uri` | The domain, the method, the path (without the query string) |
| `status`, `request_time` | The status code returned, the handling time (seconds) |
| `upstream_addr`, `upstream_time` | The service behind the gateway that handled the request, and its time |
| `bytes_sent` | The number of bytes returned |
| `request_id` | The id of the request, also in the `X-Request-Id` header of the response |
| `cf_ray` | The request id that a proxy in front attaches, empty when the request comes in directly |
| `user_agent` | The browser or application that called |

The backend writes this `request_id` into its own log (inside `[...]` after the log level).

`remote_addr` is the address in the `CF-Connecting-IP` header when a proxy in front sets it, and the IP of the client on the network when the request comes in directly.
The header is trusted only when the request comes from the Docker network (`172.16.0.0/12`) or loopback.
On a machine with a different Docker range, edit `real_ip_from` in `apisix/config.yaml`.

The log of the container also holds lines that are not JSON (startup warnings, the nginx error log), so `jq` reads line by line and skips the bad lines with `fromjson?`.
The health check of the container sends one request from `127.0.0.1` (path `/`, code 400) every 10 seconds.
The examples below leave these lines out.
An example (it needs `jq`; install it with `apt install jq` if the server does not have it):

```bash
cd deploy
docker compose logs apisix --since 1h --no-log-prefix | jq -cR 'fromjson? | select(.status >= 400 and .remote_addr != "127.0.0.1")'
docker compose logs apisix --no-log-prefix | jq -cR --arg id "<id from the X-Request-Id header>" 'fromjson? | select(.request_id == $id)'
```

## Keeping logs

Docker rotates logs by size: 10 files of 100 MB for the gateway, 5 files of 20 MB for the other services.
Logs are lost when a container is recreated.
For long-term tracing use the Keycloak events (90 days) and the business data in the database.

## Keycloak sign-in events

The Admin Console (opened as in [Install and operations](../OPERATIONS.md#keycloak-admin-console), Vietnamese) → realm `dxos` → **Events**:

- **User events**: sign-in, failed sign-in, sign-out, password change or reset, a locked account; with the IP and the client; kept 90 days.
- **Admin events**: administrative operations inside Keycloak, with the content of the change.

After 5 failed sign-ins the account is locked for 1 minute, and each further failure adds 1 minute, up to 15 minutes.
Two failures less than 1 second apart also lock for 1 minute (Keycloak's quick-login check), so a trial sign-in script must wait between attempts.
When one address fails to sign in 10 times in 10 minutes, `monitor` reports it to the admin channel if a webhook is set (the thresholds are `monitor.alert.login-failed-*`).
The failed sign-in events of Keycloak do not record the account name, so there is no per-account alert yet.
To unlock early: **Users** → choose the user → switch off **Temporarily locked**.

The IP in the events follows the same rule as `remote_addr` in the gateway log section.

## Rate limits

The gateway counts by the real IP of the client (see the log section) and, over the threshold, returns `429` with `X-RateLimit-Limit`, `X-RateLimit-Remaining` and `X-RateLimit-Reset`.
The backend also counts by user (the `sub` of the token) and, over the threshold, returns `429` with `Retry-After`.
A request with a wrong token gets `401` and is not counted.

| Where it counts | Path | Threshold per minute |
|---|---|---|
| Gateway, per IP | `/realms/<realm>/login-actions/*` and `/realms/<realm>/protocol/openid-connect/token` (one shared counter) | 120 |
| Gateway, per IP | `/api/*` (requests with a valid token) | 600 |
| Gateway, per IP | `/api/v1/system/status` (public) | See `deploy/apisix/common.yaml` |
| Backend, per user | Every `/api/**` | 300 |

The counter of the backend lives in memory, so it returns to 0 when the backend restarts.
`RATE_LIMIT_ENABLED=false` in `deploy/.env` switches off the backend part, for load tests.
To change the gateway thresholds, edit `count` of `limit-count` in `deploy/apisix/common.yaml` and run `docker compose up -d --force-recreate apisix-config && docker compose restart apisix` so the gateway reloads.
Many users behind one IP (a mobile network) share a counter, so if you see `429` in daily work, raise the threshold.
Each sign-in costs two requests (the form and the token), so a script that signs in more than 60 accounts in one minute from one machine hits the limit; wait a minute or space them out.
This limit only stops a large volume of requests from one address; guessing the password of a single account is handled by the temporary lock of Keycloak.

## The audit inside the backend

The `audit_log` table (append-only; the database refuses edits and deletes) records refused accesses and administrative operations.
The table does not record passwords, tokens or the content of requests.
Each record has the actor (the `sub`, name, roles and client of the token), `ip`, `request_id`, the method and the path.
`request_id` is the `X-Request-Id` value of the gateway, so it joins to the gateway log line (`request_id`) and to the backend log (inside `[...]` after the log level).

| Action | When |
|---|---|
| `ACCESS_DENIED` | A request gets 403 |
| `AUTH_FAILED` | The backend refuses a token that reached it (a token cannot reach the backend if the gateway already refused it) |
| `SERVER_ERROR` | The backend returns `500`, `502` or `503`; `outcome` is `FAILED`, and `metadata` has the status code and the exception class name (no error message) |
| `ADMIN_ACTION_FAILED` | A changing operation (POST, PUT, PATCH, DELETE) by an ADMIN is refused for a business reason (`409`, `422`); `outcome` is `FAILED` |
| `MODEL_*` (`MODEL_APPLIED`, `MODEL_ROLLED_BACK`, `MODEL_RESUMED`, `MODEL_ABANDONED`) | A successful apply, rollback, resume or abandon of a model, including the bootstrap run (actor `Bootstrap`); `target_type` is `MODEL`, `target_id` is the model name, and `metadata` has `version`, `status`, `runId` and `actorName`; an `unchanged` run or a refused one is not recorded |
| `USER_*` (create, lock, unlock, reset password, assign and remove a role, add and remove a unit) | An ADMIN changes an account on the Users tab; `target_id` is the user id and `metadata` has the username and the model, role or unit; no password |

A change to an account made directly in the Keycloak Admin Console does not enter `audit_log`; look it up in the Keycloak admin events (Events, Admin events; kept 90 days), which carry the account of the actor.

The changes to each record are in `engine.record_event`, and the apply history of models is in `engine.apply_run` and `engine.apply_step`.
Sign-ins and password changes are in the Keycloak events.
Each client address has at most 20 records per minute of each kind `ACCESS_DENIED`, `AUTH_FAILED`, `SERVER_ERROR` or `ADMIN_ACTION_FAILED` (`audit.denied.max-per-minute` in `application.yaml`).
The number of skipped records is in `metadata.suppressed` of the first record of the next minute.

An ADMIN looks them up through the API (`GET /api/v1/audit-logs`), filtering by `actor` (user id or username), `action`, `outcome`, `from` and `to`:

```
GET /api/v1/audit-logs?outcome=DENIED&from=2026-10-23T00:00:00Z&size=50
```

Or query directly:

```bash
cd deploy
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "${POSTGRES_DB:-dxos}" -c "select occurred_at, action, outcome, actor_name, ip, path from audit_log order by id desc limit 20"'
```

Records are kept with no time limit and a trigger blocks deletion.
After a long run, watch the size of the table.

## Metrics for Prometheus

The core service exports operational metrics in the Prometheus format at `GET /actuator/prometheus` on port `8081` inside the Docker network, for Prometheus or a similar tool to read.
An outside monitoring tool is optional: the default stack ships no Prometheus, and the [bundled Prometheus](#the-bundled-prometheus-optional) overlay runs one when you switch it on.

Access:

- The API gateway does not forward the path `/actuator` to the outside, so the endpoint can be read only from inside the Docker network of the stack.
- The endpoint requires HTTP Basic with the user `metrics` and the password `METRICS_PASSWORD` in `deploy/.env`; `make_env.sh` generates a random password, and when it is left empty the endpoint is closed to everyone.
- Only this endpoint and `/actuator/health` are open; the other Actuator endpoints are not.

The core service computes the metrics from the database every 30 seconds (change it with `METRICS_REFRESH_MS`, in milliseconds), so a read does not query the data.
The labels are only model names, entity names, statuses and apply results declared by the model files, never records, users or free text, so the number of series follows the number of models and not the number of records.

| Metric | Type | Labels | Meaning |
|---|---|---|---|
| `dxos_records` | gauge | `model`, `entity`, `status` | The number of records of an entity that has a workflow, by status; a status with no records still shows 0 |
| `dxos_records_overdue` | gauge | `model`, `entity` | The number of records that are overdue for the handler |
| `dxos_outbox_pending` | gauge | none | The number of outgoing events not yet delivered |
| `dxos_outbox_retrying` | gauge | none | The number of events not yet delivered that have failed at least once |
| `dxos_outbox_oldest_age_seconds` | gauge | none | The age of the oldest undelivered event, in seconds; 0 when the queue is empty |
| `dxos_model_applies_total` | counter | `result` (`done`, `failed`) | The number of model-file applies by result, counted from the apply history |
| `dxos_models` | gauge | none | The number of running models |
| `dxos_models_left_out` | gauge | none | The number of saved models left out of operation because the saved version does not pass the checks of this release; when it is not 0, see the reason in the list of models and apply the corrected file |

The service also exports the standard metrics of the JVM, the database and HTTP (`jvm_*`, `hikaricp_*`, `http_server_requests_*`); their `uri` label is a path pattern such as `/api/v1/data/{model}/{entity}`, which holds no record ids.

Try a read from the server of the stack:

```bash
cd deploy
PASSWORD=$(grep '^METRICS_PASSWORD=' .env | cut -d= -f2)
docker compose exec backend wget -qO- --header "Authorization: Basic $(printf 'metrics:%s' "$PASSWORD" | base64 -w0)" http://localhost:8081/actuator/prometheus | grep '^dxos_'
```

A Prometheus configuration for a Prometheus that runs in the same Docker network as the stack (the container joins the network `<project-name>_default`, see it with `docker network ls`):

```yaml
scrape_configs:
  - job_name: dxos
    metrics_path: /actuator/prometheus
    scrape_interval: 30s
    basic_auth:
      username: metrics
      password_file: /etc/prometheus/dxos-metrics-password
    static_configs:
      - targets: ["backend:8081"]
```

The file `dxos-metrics-password` holds the password on one line, with no newline at the end.

Reference PromQL queries:

| To learn | Query |
|---|---|
| Records by status | `sum by (model, entity, status) (dxos_records)` |
| Overdue records | `sum by (model, entity) (dxos_records_overdue)` |
| The age of the event queue | `dxos_outbox_oldest_age_seconds` |
| Events not yet delivered | `dxos_outbox_pending` and `dxos_outbox_retrying` |
| Failed applies in 24 hours | `increase(dxos_model_applies_total{result="failed"}[24h])` |

Suggested alert rules:

```yaml
groups:
  - name: dxos
    rules:
      - alert: DxosRecordsOverdue
        expr: sum by (model, entity) (dxos_records_overdue) > 0
        for: 15m
      - alert: DxosEventQueueStuck
        expr: dxos_outbox_oldest_age_seconds > 600
        for: 5m
      - alert: DxosApplyFailed
        expr: increase(dxos_model_applies_total{result="failed"}[1h]) > 0
```

## The bundled Prometheus (optional)

The overlay `deploy/docker-compose.prometheus.yml` runs a Prometheus already wired to the metrics above: it reads `dxos_*` with `METRICS_PASSWORD`.
The default stack does not run it and the core does not change.

### Switch it on

1. Set `METRICS_PASSWORD` in `deploy/.env` (`make_env.sh` generates it for a new install; on an older install set it yourself, for example with `openssl rand -hex 24`) and restart the core service; when it is empty the endpoint is closed, and `dxos start` stops at the machine check with a FAIL line naming the missing variable.
2. Set `COMPOSE_FILE=docker-compose.yml:docker-compose.prometheus.yml` in `deploy/.env`, then run `dxos start`.
3. Open `http://127.0.0.1:9090` and run the queries above.
4. To switch it off, remove the `COMPOSE_FILE` line and run `docker compose up -d --remove-orphans`; to delete the stored metrics too, run `docker volume rm <project>_prometheus_data`.

### Settings

- Prometheus opens on `127.0.0.1:9090`; change it with `PROMETHEUS_BIND` and `PROMETHEUS_HOST_PORT` in `deploy/.env`.
  Prometheus asks for no sign-in and serves plain HTTP, so when you open it to the outside put it behind a VPN or a TLS proxy with a sign-in.
- Prometheus reads `backend:8081` every 30 seconds and keeps the metrics for 15 days in the volume `prometheus_data`; change the retention with `PROMETHEUS_RETENTION` (for example `30d`) in `deploy/.env`.
- The service runs with at most 512 MB of RAM and half a CPU core, a read-only file system and no extra privileges.

### Limits

- One Prometheus, with no replicas; the metrics exist only from the moment the overlay is switched on.
- No dashboard is bundled; for your own dashboard connect your tool to Prometheus or read the endpoint directly.
- There is no Alertmanager; the suggested alert rules above must be added by the operator to their own Prometheus.
- The licenses of the image and the components that come with it are in the note on Prometheus in [`DEPENDENCIES.txt`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/DEPENDENCIES.txt).

## The monitoring screen

The `monitor` service gathers the gateway log, `audit_log`, the Keycloak sign-in events and the state of the services into one screen `/monitor/` for ADMINs, with alerts through a webhook when one is configured.
How to read it, how to trace an incident, the data, the alerts and the configuration are in [`backend/dx-monitor/README.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/dx-monitor/README.md) (Vietnamese).
