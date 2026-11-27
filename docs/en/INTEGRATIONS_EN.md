# Integrating with outside systems

A document for the person who connects an outside system to DX-OS: event webhooks, the API for pulling events, tamper-evident history and the data lake.
Installation and the `dxos` commands are in [Install and operations](../OPERATIONS.md) (Vietnamese); logs and audit are in [Monitoring and logs](./MONITORING_EN.md).
Connecting a reporting tool straight to the business data with a read-only role is in [Connecting a reporting tool](./REPORTING_EN.md).

## Event webhooks

Each model sends the history rows of its records to one target.
The model file chooses the target with `webhook: <target name>` at the top of the file, with a name of the form `[a-z][a-z0-9_]*`.
The address and the secret of each target are in `deploy/webhooks/targets.yml`; this file is kept out of git because an address may hold a key; the sample is in `deploy/webhooks/targets.example.yml`:

```yaml
targets:
  ops:
    url: https://chat.example.org/hooks/abc
    secret: change-me
    format: chat
```

An application running on the same machine as DX-OS receives the webhook at `http://host.docker.internal:<port>/<path>`: the backend has `extra_hosts: host.docker.internal:host-gateway`, so you need not set the IP address of the machine.
An application that does not run in Docker must listen on an address that the Docker network gateway can reach, not only on `127.0.0.1`.

`format` is `json` (the message as it is, the default) or `chat` (see "Messages for Mattermost and Slack" below); a target that has no format gets `DXOS_WEBHOOK_FORMAT`.

A model that does not write `webhook` uses `DXOS_WEBHOOK_URL` in `.env`; when that is empty and no target is written, the model sends nothing.
Only the rows created after the model has a target are sent.
A model that names a target that is not in the file is refused when you preview the plan and apply returns 409.
The `targets.yml` file is reloaded when it changes, with no need to recreate the backend; a target with a wrong name or address is skipped and written to the log.
A model that no longer has any target (no `webhook` and an empty `DXOS_WEBHOOK_URL`) has its pending messages dropped, and they are not sent when a target comes back; what was missed is pulled with the event-pull API below.
A target that is removed from the file after the model has already run still lets the messages of that model enter the queue, shows a configuration error on the Event queue tab, and delivers them when the target is back and you click Retry.
Each row (create, edit, delete, a workflow action, assignment, adding or removing a file, overdue) is written to the outbox in the transaction of the change, so a change that is cancelled sends nothing.
The backend sends a JSON `POST` with these fields:

| Field | Meaning |
|---|---|
| `eventId` | The number of the history row, increasing; also in the header `X-Dxos-Event-Id` |
| `model`, `entity`, `recordId` | Which record |
| `webUrl` | The page of the record in the web interface, see [Links to the web interface](./EXTENSIONS_EN.md#links-to-the-dx-os-web-interface); absent when you run `dxos start --core` |
| `action` | The action, for example `created`, `updated`, `deleted`, `assigned`, `due_soon`, `overdue`, `file_added`, `completion_skipped`, `auto_closed`, or the name of a workflow action |
| `fromStatus`, `toStatus` | The status before and after |
| `actor`, `actorName` | The user id and the name of the person who acted, sent outside the system; `system` for an overdue row |
| `at` | The time, in UTC |
| `schemaVersion` | The version of the message format, currently 1 |
| `hash` | The hash of the row in the history chain (see "Tamper-evident history"); a receiver that keeps it has an anchor outside the database |

The message has no record content, notes or file names; a receiver that needs them calls the API with its own permissions.
When `DXOS_WEBHOOK_SECRET` is set, each request carries `X-Dxos-Signature: sha256=<hex>`, the HMAC-SHA256 of the exact content; the receiver computes it again and compares.
A receiver that returns 2xx within 5 seconds is done; any other result makes the message be sent again, with the wait doubling each time, up to 5 minutes.
A message that fails more than `DXOS_OUTBOX_MAX_ATTEMPTS` times (20 by default) stops and is no longer sent by itself; the Retry button gives it one more try.
The rows of one record are delivered in order: a later row waits while an older row of the same record has not been delivered, even when the older row has stopped and is waiting for Retry.
Rows of different records do not wait for each other, and a row may arrive twice, so the receiver de-duplicates by `eventId`.
Messages not yet sent, stopped ones, the latest error and the retry button are on the Event queue tab; a sent message is deleted after 30 days.
If you change `DXOS_WEBHOOK_URL`, `DXOS_WEBHOOK_SECRET` or `DXOS_WEBHOOK_FORMAT`, recreate the backend with `docker compose up -d backend`.

A receiver that missed messages (it was down, or a message stopped) gets them back with `GET /api/v1/events?after=<the eventId you kept>&size=<at most 500>&model=<optional>` with an ADMIN token.
The rows are read straight from the history, in the same form as the webhook message, from old to new; `next` is the `eventId` to ask for next and `more` says whether rows remain.
This command works even when no target has been set.
The changes of the administration are fetched again with `GET /api/v1/events/admin?after=<the eventId you kept>&size=<at most 500>&model=<optional>`, with the same `next` and `more` conventions; each element has exactly the form of the `admin.event` message.

A record whose deadline is longer than `DXOS_DUE_SOON_BEFORE` (`24h` by default, `0` to switch off) gets one `due_soon` row when that much time is left before the deadline, exactly once for each deadline.
A deadline no longer than this interval has no `due_soon` row.
This interval is compared with the declared `deadline`, even when the workflow has a working calendar, and counts in ordinary hours from the deadline of the record.
A workflow that declares `escalate` gives an overdue record further `escalated` rows, one for each level that falls due or each repeated reminder, with `level` (the level, from 1) and `round` (the reminder, from 0) in the message.
An `escalated` row does not change the status of the record, and the other fields of the message have the same form as for any other history row.

### Messages for Mattermost and Slack

A target with `format: chat` receives `{"text": "<one sentence>"}` instead of the message as it is; Mattermost and Slack both accept this form at their incoming webhook address.
The sentence has the model name, the entity, the record number and the status, with no record content.
A record of an entity with a workflow that was just created or submitted again reads as "Việc mới" (new work), a `due_soon` row reads "Sắp đến hạn xử lý" (about to reach its deadline), an `overdue` row reads "Quá hạn xử lý" (overdue), an `escalated` row reads "Leo thang mức" (escalation level) with the receiving roles, an assignment row reads "Được giao" (assigned); other rows give the action and the status before and after.
The sentences are in Vietnamese.

To connect Mattermost, create an Incoming Webhook for the receiving channel, put its URL in the target `ops` of the example above and declare `webhook: ops` in the model.
Keep the webhook URL out of Git and make sure the backend can reach it; `localhost` inside a container is the container itself.

The webhook flow was checked on 13 November 2026 with Mattermost Team Edition 11.7.15, using the unmodified Linux amd64 binary/client under `MIT-COMPILED-LICENSE`, with no plugin.
Mattermost is a separately built service and is not part of the default install of DX-OS.

- With a handling deadline of `1m` and `DXOS_DUE_SOON_BEFORE=20s`, the rows `created`, `due_soon` and `overdue` reached the right channel and the outbox recorded them as delivered.
- A name in Vietnamese displays correctly; a payload checked separately through `WebhookSender` keeps line breaks, quotes, `&`, `< >` and emoji correctly in the Mattermost interface.
- When Mattermost does not answer within 5 seconds, or a wrong hook returns HTTP 404, the record is still saved, the outbox records the error and sends again successfully after the target is restored, before the limit on attempts.

Mattermost does not de-duplicate by `X-Dxos-Event-Id`, so a notification can repeat when a timeout is followed by a resend.
Slack has not been tested in practice.

### Checking before a status change

An action that declares `check: <target name>` makes the service at that target be asked before the record changes status.
The target is declared in `deploy/webhooks/targets.yml` like the target of `webhook`, with two optional keys more:

```yaml
targets:
  audit_service:
    url: https://audit.example.org/dxos/check
    secret: change-me
    on_error: deny
    bypass: false
```

- `on_error` is `deny` (the default) or `allow`, for when the service does not answer within 3 seconds, returns an error or answers in the wrong form.
- `bypass: true` lets every action through without asking, for use while the service is under maintenance; editing the file takes effect, with no need to recreate the backend.
- A target name that is not in the file is refused when you preview the plan and apply returns 409, like the target of `webhook`.

The service is asked after the caller has passed every rule of the workflow, inside the same transaction that holds the lock of the record, so two actions on one record do not ask on top of each other.
The backend sends a JSON `POST` with `model`, `entity`, `record_id`, `action`, `from`, `to`, `actor`, `note` and `record` (the fields of the record).
`X-Dxos-Event-Id` is the same on every ask about the same action on the same version of the record, so the service can recognize a repeat; `X-Dxos-Signature` is present when the target has a `secret`, computed as for event webhooks.
The service returns `2xx` with `{"allow": true}` or `{"allow": false, "code": "...", "message": "..."}`; `code` and `message` belong to the service, at most 300 characters.

| Result | The record | What the caller receives | The history row |
|---|---|---|---|
| `allow: true` | Changes status | The result of the action | The row of the action |
| `allow: false` | Stays as it is | 403 `ERR_ACTION_NOT_ALLOWED`, `data.reason` is `CHECK_DENIED`, `data.code` is the code of the service when there is one, `message` is the sentence of the service | `check_denied` |
| No answer or a wrong answer, `on_error: deny` | Stays as it is, can be retried | 503 `ERR_CHECK_UNAVAILABLE`, `data.reason` is `CHECK_UNAVAILABLE` | `check_unavailable` |
| No answer or a wrong answer, `on_error: allow` | Changes status | The result of the action | `check_skipped`, then the row of the action |
| `bypass: true` | Changes status | The result of the action | `check_skipped`, then the row of the action |

The automatic closing action of `auto_close` is carried out by the system, so it does not ask the service.

### Model applies and account changes

These changes send a message of the kind `admin.event` with `eventId`, `kind`, `model`, `actor`, `actorName`, `at`, `facts` and `schemaVersion`; `eventId` is the number of the row in the change log of the administration, increasing, and also in the header `X-Dxos-Event-Id`.
This number counts on its own, not in the same sequence as the `eventId` of the record history rows, so the receiver de-duplicates by the pair of message kind and `eventId`.
`kind` is `MODEL_APPLIED`, `MODEL_ROLLED_BACK`, `MODEL_RESUMED`, `MODEL_ABANDONED`, or the name of an audit action such as `USER_ROLE_GRANTED`.
A message of one model goes to the target of that model; account changes and a model that has been removed go to the default target.
Each change is written as a row whether or not there is a target; only when there is a target does that row enter the sending queue.

## Tamper-evident history

Each `engine.record_event` row has `hash = sha256(prev_hash || normalized content of the row)`; the rows are chained into one single chain, written under an advisory lock.
Hash version 1 still verifies the rows already released without recomputing the hash or invalidating the old anchors.
New events use version 2: the hash is computed from the action, the object, the status, the time and the id of the person who acted (`actor`); `actor_name`, `note` and `detail` still serve display but are not in the hash.
Every hour the backend writes an anchor (id, hash, time) into the object `history/anchors.jsonl` of the object store, outside Postgres and included in the backup of the store; `dxos history anchor` writes an anchor at once.
The event webhook carries the `hash` of each row, so the receiver has an extra anchor outside the server.
`dxos history verify` (or `GET /api/v1/history/verify`, ADMIN) recomputes the whole chain and compares it with the anchors.
The result has `intact`, `head`, `firstBreak` (the first row that was edited, inserted or that sits after a deleted row) and `mismatchedAnchors` (anchors whose row or hash no longer exists); the command exits with code 1 when `intact` is `false`.
The chain detects an edit of the immutable part, a deletion or an insertion of a row made with SQL, and a cutting off of the tail of the history after an anchor.
The name of the actor and the free content of a version 2 event are outside the integrity commitment: `dxos history verify` does not detect an edit of them.
The id of the actor is still in the hash.
Someone who rewrites the whole chain and also the anchor object on the server is not detected, unless compared with the hash that a webhook receiver has kept.
[`examples/history-witness/witness.py`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/examples/history-witness/witness.py) does that comparison: `save` writes the pairs of `eventId` and `hash` to a file somewhere out of reach of the person who edits the server, and `compare` reports which row has changed its hash or is gone.

## The data lake

`dxos archive <model> [--before YYYY-MM-DD] [--out FOLDER | --store]` writes the records that reached a final status (`decided_at` has a value, older than `--before` if given) and their history to Parquet, each run into a folder `<model>/<run>/` with a `manifest.json`.
The command runs DuckDB in a container and reads Postgres with the `POSTGRES_USER` account of `.env` in read-only mode, so the data in the database stays as it is.

### Write to a folder

By default it writes to `deploy/archive`, or to `--out FOLDER`.
`manifest.json` holds the version of the model, the row count and the sha256 of each file.
`dxos archive check <folder>` reads each file again and compares the row count and the sha256 with the manifest; a mismatch exits with code 1.

### Write to the object store

`--store` writes to the bucket `S3_ARCHIVE_BUCKET` (`dxos-archive` by default) of the SeaweedFS running in the stack, under `<model>/<run>/`.
The bucket is separate, does not go through the gateway and is created at the first write.
DuckDB writes straight to `s3://` with the keys `S3_ACCESS_KEY` and `S3_SECRET_KEY` of `.env`; the keys enter the container through environment variables, so they are not on the command line, in a log or in an error message.
The store is part of the SeaweedFS data, so `dxos backup` backs it up together with the uploaded files.

The `manifest.json` in the store also has `since` and `upTo`, the two ends of the `decided_at` interval that the run took.
The row counts and sha256 values in it are computed from the very bytes that are in the store.
`dxos archive list` lists the runs in the store, and `dxos archive check --store <model>/<run>` reads each file again from the store and compares it with the manifest; a file that differs or cannot be read makes the command exit with code 1.
If the store cannot be read (a wrong key, SeaweedFS is not running), the command reports an error and exits with code 1, and does not treat it as an empty store.

`dxos archive query <model>/<run> --file <file>.parquet` builds a temporary table `archive` from exactly one Parquet file that is in the manifest of that run and then accepts one `SELECT`, `WITH` or `EXPLAIN` statement.
The command checks the row count and the SHA-256 of the file against the manifest before it runs the SQL; with no `--file`, with a wildcard, or with a file that is not in the manifest, the command exits with code 1.
The command supports bound parameters with `--param`, switches off access to outside paths before it runs the SQL, and needs no database permission to be granted to a reporting tool.
The command runs on the install machine with the object store key in `.env`, so only someone who may operate the deployment folder can use it.
Examples and the parameter syntax are in [Install and operations](../OPERATIONS.md#lưu-trữ-hồ-sơ-đã-đóng) (Vietnamese).

### On a schedule

`dxos archive auto` writes, for each model that has a workflow, the records closed for more than `ARCHIVE_AFTER_DAYS` days (365 by default) that the store does not hold yet.
A run takes the interval from the `upTo` of the previous run to the deadline of today, so a record is in exactly one run; with no new records nothing is written.
`dxos archive schedule install` installs `dxos-archive.timer` (a systemd user timer) that runs that command at 03:30 every day and makes up for a missed turn when the machine was off, each time writing to `deploy/archive.log`; `dxos archive schedule uninstall` removes it.
A machine without systemd runs `dxos archive auto` from cron every day.
A user timer runs only while the user still has a login session, unless you switch on `loginctl enable-linger <user>`.

### Network

The first time, DuckDB downloads the extensions `postgres` and `httpfs` from the network and keeps them in `deploy/duckdb`; later runs need no network.

## The MQTT bridge (optional)

Field devices usually speak MQTT and do not call HTTP.
The overlay `deploy/docker-compose.mqtt.yml` adds an MQTT broker and a small bridge: the bridge receives messages by topic and then creates records through the common data API with a machine client, like any outside application.
The core does not change and keeps no device state; the default stack does not change either.

### Switch it on

1. Copy `deploy/mqtt.env.example` to `deploy/mqtt.env` (kept out of Git) and fill in `MQTT_PASSWORD`, generated with `openssl rand -hex 24`.
2. Apply a model that has an entity to receive readings, for example the "Equipment incident" template (`plant`) of the template library, and then register the client of the bridge: `dxos client add itot-mqtt --model plant --role SENSOR --env MQTT_BRIDGE`.
   Paste the two lines `MQTT_BRIDGE_CLIENT_ID` and `MQTT_BRIDGE_CLIENT_SECRET` that the command prints into `deploy/mqtt.env`.
3. Set `COMPOSE_FILE=docker-compose.yml:docker-compose.mqtt.yml` in `deploy/.env`, then run `dxos start`.
4. To switch it off, remove the `COMPOSE_FILE` line and run `docker compose up -d --remove-orphans`; to delete the data of the broker too, run `docker volume rm <project>_mqtt_data`.

The broker does not run when `MQTT_PASSWORD` is empty and writes the reason to the log.
When the client is not registered or the model is not applied, the bridge writes the reason to the log and tries again, so registering or applying later is enough.

### The broker

- Mosquitto 2.0.23; every connection needs `MQTT_USER` and `MQTT_PASSWORD`, and there is no anonymous connection.
- Port 1883 is open only on `127.0.0.1`; set `MQTT_BIND=0.0.0.0` (and `MQTT_HOST_PORT` if needed) in `deploy/.env` to open it to devices.
  The broker speaks plain MQTT, with no TLS, so when you open it to an untrusted network put it behind a VPN or behind a TLS proxy.
- The session of the bridge is kept and messages travel with QoS 1: when the bridge is off the broker holds the messages (up to 10,000, each up to 64 KB) and delivers them when it runs again.
- The data of the broker lives in the volume `mqtt_data`; this is a queue, not a store, and `dxos backup` does not back it up.

### Routes

The bridge reads the list of routes from the variable `MQTT_BRIDGE_ROUTES` in `deploy/mqtt.env` (one JSON line, kept when the install is updated), or, if it is not set, from `deploy/mqtt/bridge.json`, the incident route of the `plant` template.
Each route connects a topic with an entity:

| Key | Meaning |
|---|---|
| `topic` | The MQTT topic filter, which may use `+` and `#` |
| `model`, `entity` | The entity that receives the records; the role of the client must be able to write to it |
| `record` | The fields of the record; a value is a constant or a string with a template |
| `when` | Optional: `{"field": "reading", "op": ">", "value": 80}`; only a message whose payload field satisfies the condition becomes a record (`op` is `>`, `>=`, `<`, `<=`, `==` or `!=`) |
| `skip_if_open` | Optional: `{"field": "device", "value": "{topic.1}"}`; skip the message when the entity (which has a workflow) still has an unclosed record with that field equal to that value |
| `key` | Optional: the observation code, usually `{payload.observation}`; when it is not set, the hash of the whole message is used |

Inside a string, `{topic.N}` is the Nth level of the topic (from 0) and `{payload.name}` is a field of the JSON payload; a string that consists of one template alone keeps the type of the value, so a number stays a number.
For example, the message `{"metric": "Nhiệt độ (°C)", "reading": 91.5, "observation": "pump-01-1"}` sent to `plant/pump-01/reading` creates an incident for `pump-01` when `reading` is over 80 and `pump-01` has no open incident.

### Reliability

- The bridge acknowledges a message to the broker only after it has handled it, so a message is not lost when the bridge or the core stops for a while.
- A temporary error (the core is down, 5xx, 429, the network, the token) is retried, with spacing up to 30 seconds, until it passes; the errors 401 and 403 are also retried because they are configuration errors and not errors of the message.
- A message that the core refuses with 400 or 409, a message that is not JSON, one that lacks a field the route needs or one that matches no route is written to the log and skipped, and does not stop the bridge.
- Each record is sent with an `Idempotency-Key` generated from `model`, `entity` and `key`, so a message delivered twice creates only one record while the key is still kept for 24 hours; after that, only a field declared `unique` (like `observation` of the `plant` template) can block a duplicate row.
- The bridge calls the `apisix` port inside the Docker network, with the public address of `GATEWAY_PUBLIC_URL` in the `Host` header, so the token issued by Keycloak has the same issuer as the token of every other application.
- The log is at `docker compose logs mqtt-bridge`.

### Try it

`python3 examples/itot-simulator/simulator.py run --transport mqtt` publishes the readings of a simulated device; how to run the whole path is in [`examples/itot-simulator/README.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/examples/itot-simulator/README.md).
Send one message by hand:

```
docker compose exec mosquitto sh -c 'mosquitto_pub -h 127.0.0.1 -u "$MQTT_USER" -P "$MQTT_PASSWORD" -q 1 -t plant/pump-01/reading -m "{\"metric\":\"Nhiệt độ\",\"reading\":91.5,\"observation\":\"pump-01-1\"}"'
```

### Limits

- MQTT 3.1.1, QoS 0 and 1, no TLS; one broker and one bridge, with no replicas.
- The bridge only creates records; confirming or reopening an incident is the job of the handler or of the monitoring system through the API.
- The `when` condition compares one field of the payload with one value; more complex formulas belong in the device or in the monitoring system.
- The core keeps no real-time state of a device, so the bridge creates no digital twin.
- The licenses of the components of the two images (Mosquitto, Python) and of what comes with them are in the note on the MQTT bridge in [`DEPENDENCIES.txt`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/DEPENDENCIES.txt).
