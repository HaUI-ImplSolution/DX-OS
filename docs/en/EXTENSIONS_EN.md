# Developing at the extension points

DX-OS keeps common business logic in the model file and leaves specific logic outside the core.
This document shows how to connect your own service, event receiver and interface to a running system without putting the entity names, roles or flows of a particular field into the core code.

## Current level of support

In this document, **stable** means the contract is documented and checked in the repository.
It does not mean a model may change its fields without the integration code being checked again.
**Experimental** means the approach works in the current source tree but has no installation or upgrade boundary that is preserved.

| Need | Level of support | Upgrade boundary |
|---|---|---|
| A separate service that calls the API of the core | Stable | Use a separate client for exactly one role of one model and read the OpenAPI generated from the running model |
| A receiver that reacts after a change | Stable | A signed webhook, `schemaVersion` and a catch-up API; to block an action before the status changes, use the `check` key of the action |
| An outside interface running at its own origin | Stable | Register a public client with `dxos client browser add`; redirects and CORS use an exact list, with no wildcard |
| A page or a form component in the Tier 2 web app | Source customization | You must edit and rebuild the frontend image; there is no plugin registry or compatibility check yet |
| A custom route under the DX-OS gateway | Stable | The `deploy/apisix/local.yaml` of the install is checked and merged with the core configuration; it is kept unchanged on upgrade |
| A custom workflow | Stable by declaration | Declare `states` and `actions` in the model file, not a plugin; see [Writing a model file](./MODEL_EN.md#declaring-or-extending-a-workflow-yourself) |

Prefer the model file and the common API before writing code.
A service that only calls the DX-OS API does not need a route added to the APISIX of DX-OS.

## General principles

- Keep the extension code in its own repository, image and release life cycle.
- Use the narrowest client: a machine application holds exactly one role of one model; a browser uses a user sign-in with Authorization Code and PKCE.
- Do not put secrets in an image, a repository, a frontend build variable, a URL, a log or a command that can end up in the shell history.
- Send an `Idempotency-Key` for operations that create a record and for actions that may be retried.
- Accept new JSON fields, but refuse an unsupported `schemaVersion` and check the OpenAPI again after every model change.
- Record and pass on `X-Request-Id` to match the gateway log, the extension service and the backend.
- Separate the failures of the extension from the core with a timeout, a queue or a bounded retry; do not hold a user's request open to wait for long work.

## A separate service that calls the API

This is the right extension path when you need computation, synchronization or communication with another system but the data and the status changes are still managed by DX-OS.

### 1. Choose the contract and the permission

Apply the model first, then choose a role that has exactly the permission the service needs.
An administrator registers the application on the **Outside applications** tab of `/console` or with the command:

```bash
dxos client add equipment-sync --model equipment --role KEEPER
```

The result has a `clientId` and a `secret`; the secret is shown only this time.
Store the secret in a secret manager or in a file that only the service process can read; do not copy it into the `.env` of the source tree.

Each client holds exactly one role of one model.
The API of another model, the administration API and the user API return `403`.
Switch to another role of the same model with `dxos client role <name> --role <role>` or the **Change role** button on the **Outside applications** tab; the client id and the secret stay the same, and the tokens already issued keep the old role until they expire.
To switch to another model, delete the client and register it again; the records the client already created remain.
An install shared by several integrated systems groups the clients and models of each system into a project, with `--project` when registering or on the **Project** tab; see [the Projects section of `OPERATIONS.md`](../OPERATIONS.md#dự-án) (Vietnamese).

### 2. Get the token and the OpenAPI of the model

The values the service needs and where to get them:

| Value | Where to get it |
|---|---|
| The address of the entry point (`DXOS_BASE_URL`) | `GATEWAY_PUBLIC_URL` in `deploy/.env`, `http://localhost:9080` by default |
| The address to get a token | `<entry point address>/realms/dxos/protocol/openid-connect/token` |
| `clientId` | Printed by `dxos client add`, or the **Outside applications** tab; it has the form `dxos-app-<name>` |
| secret | Shown once at registration or at `dxos client rotate <name>` |
| The model and entity names | The model file, or `GET /api/v1/meta/models` |

The example below keeps the secret in a file with mode `600` and puts no secret value on the command line:

```bash
export DXOS_BASE_URL=https://dxos.example.org
export DXOS_CLIENT_ID=dxos-app-equipment-sync
export DXOS_SECRET_FILE=/run/secrets/dxos-client-secret

TOKEN="$(curl -fsS \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode "client_id=$DXOS_CLIENT_ID" \
  --data-urlencode "client_secret@$DXOS_SECRET_FILE" \
  "$DXOS_BASE_URL/realms/dxos/protocol/openid-connect/token" | jq -r .access_token)"

curl -fsS \
  -H "Authorization: Bearer $TOKEN" \
  "$DXOS_BASE_URL/api/v1/meta/models/equipment/openapi" \
  -o equipment.openapi.json
```

The PowerShell version of the two commands above, to run in Windows Terminal; the secret is read from a file and not put on the command line:

```powershell
$base = "https://dxos.example.org"
$secret = (Get-Content -Raw $env:DXOS_SECRET_FILE).Trim()

$token = (Invoke-RestMethod -Method Post `
  -Uri "$base/realms/dxos/protocol/openid-connect/token" `
  -Body @{ grant_type = "client_credentials"; client_id = "dxos-app-equipment-sync"; client_secret = $secret }).access_token

Invoke-RestMethod -Headers @{ Authorization = "Bearer $token" } `
  -Uri "$base/api/v1/meta/models/equipment/openapi" -OutFile equipment.openapi.json
```

This OpenAPI is generated from the running version of the model and holds only the entities the caller may read.
Use it to generate a client or as the input of contract tests; do not copy an old schema into the code and treat it as the source of truth.

A client generated with [OpenAPI Generator](https://openapi-generator.tech) (Apache-2.0), run in Docker on the `equipment.openapi.json` file you just fetched:

```bash
docker run --rm -u "$(id -u):$(id -g)" -v "$PWD:/local" \
  openapitools/openapi-generator-cli:v7.27.0 generate \
  -i /local/equipment.openapi.json -g typescript-fetch -o /local/client
```

`-g` chooses the language, for example `typescript-fetch`, `python` or `java`; `docker run ... openapi-generator-cli:v7.27.0 list` prints the full list.
Get a token as above and put it in the `Authorization` header of the generated client.
Generate the client again after every model change, and check the code that calls it.
This tool is run by the outside application itself; DX-OS does not package it, and its license is in the note on OpenAPI Generator in `DEPENDENCIES.txt`.

### 3. Call the data and the workflow

The data API lives under `/api/v1/data/{model}/{entity}`.
The fields, the actions, the error codes and the data each action requires are in the OpenAPI you just fetched; an overview is in [API](./API_EN.md).

```bash
curl -fsS \
  -H "Authorization: Bearer $TOKEN" \
  "$DXOS_BASE_URL/api/v1/data/equipment/loan?page=0&size=20"
```

```powershell
Invoke-RestMethod -Headers @{ Authorization = "Bearer $token" } `
  -Uri "$base/api/v1/data/equipment/loan?page=0&size=20"
```

When you create a record or call an action, generate a stable key for the business request itself and send it in the `Idempotency-Key` header.
Sending the same key with the same content again returns the first result; using the same key for other content returns `409`.
When you edit a record, send the `version` you read and handle `ERR_CONCURRENT_MODIFICATION` instead of overwriting.
If you already have a CSV file to load, use `dxos import` ([Install and operations](../OPERATIONS.md#nhập-hồ-sơ-từ-tệp-csv), Vietnamese) instead of writing a script.

Do not write to PostgreSQL directly and do not change the `status` column through the data API.
Every status change goes through `/actions/{action}` so that the rules, Flowable, the history and the events are all applied together.

How to handle the errors of a call:

| Code | From where | What to do |
|---|---|---|
| `401` | The entry point, when the token is missing or invalid; the body returned is HTML, not JSON | Get a new token and call again once |
| `403` | The backend, `ERR_ACTION_NOT_ALLOWED` with `data.reason` (for example `OWNER_ONLY`, `ASSIGNED_TO_OTHER`, `CHECK_DENIED`) | Do not retry; change the role or fix the request |
| `409` | The backend, `data.reason` is `WRONG_STATE`, `RECORD_CLOSED`, `IDEMPOTENCY_KEY_REUSED`, `CONDITION_NOT_MET` or `MODEL_VERSION_CHANGED`; editing a record returns `ERR_CONCURRENT_MODIFICATION` | Read the record again and decide; `IDEMPOTENCY_KEY_REUSED` means the key was used for other content; `MODEL_VERSION_CHANGED` means the model changed after the metadata was read |
| `429` | The entry point or the backend | Wait and call again; only the backend (`ERR_TOO_MANY_REQUESTS`) sends `Retry-After`, and the entry point sends `X-RateLimit-Reset`, the seconds left in the window |
| `503` and timeouts | The backend, or the network between the two sides | Retry a limited number of times with growing spacing, keeping the same `Idempotency-Key` |

### 4. Run locally and test

Run the service outside the Compose of the core with `DXOS_BASE_URL=http://localhost:9080`.
Test at least these cases:

- Get a token, download the OpenAPI and call an endpoint that the role allows.
- Call another model or an administration endpoint and receive `403`.
- Send a request again with the same `Idempotency-Key` without a second effect.
- Simulate an expired token, `429`, a timeout and `409` without retrying forever.
- Rename or remove a field in a trial version of the model and let the contract tests show where the integration must change.

[`examples/itot-simulator`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/examples/itot-simulator/README.md) shows an outside service that gets a machine token, creates a record, performs an action and avoids duplicate sends; it needs only Python 3.9 or later, so it runs on Windows too.
With `--transport mqtt` it publishes readings to the broker of the [MQTT bridge](./INTEGRATIONS_EN.md#the-mqtt-bridge-optional), like a real device.
The `setup` command of the example calls Keycloak itself so that the example runs on its own; a real deployment uses `dxos client add` so that the client appears in the administration and the data catalog.

### 5. Package and upgrade

Package the service into its own image, run it with Compose or your own orchestrator and pass the URL, the client id and the secret through runtime configuration.
Do not add the service to the `deploy/docker-compose.yml` of the installer if you want `dxos update` not to overwrite your own deployment.

Before you upgrade DX-OS or apply a new version of the model:

1. Get the OpenAPI from a test environment and compare it with the contract the service uses.
2. Run again the tests for permissions, idempotency, concurrent errors and timeouts.
3. Deploy the compatible service before you apply a change that breaks the contract.
4. Rotate the secret with `dxos client rotate <name>` and update the runtime secret; the old token can be used until it expires, about 5 minutes.
5. Remove a client that is no longer used with `dxos client remove <name>`.

## A webhook receiver

A webhook suits notifications, asynchronous synchronization, indexing or triggering work after DX-OS has written a change.
A webhook cannot refuse an action; if you want a separate service to block an action before the status changes, use `check` on the action, see [Checking before a status change](./INTEGRATIONS_EN.md#checking-before-a-status-change).

### 1. Declare the target

Put the configuration of the install in `deploy/webhooks/targets.yml`, outside git:

```yaml
targets:
  extension_worker:
    url: https://worker.example.org/dxos/events
    secret: <a random secret>
    format: json
```

The model file chooses the target with a key at the top of the file:

```yaml
webhook: extension_worker
```

The details of reloading the configuration, retry, order and payload are in [Integrating with outside systems](./INTEGRATIONS_EN.md#event-webhooks).

### 2. Handle it safely

The receiver does this in order:

1. Read the exact bytes of the body and verify `X-Dxos-Signature` with HMAC-SHA256 using a constant-time comparison.
2. Refuse an unsupported `schemaVersion`; for a known version, ignore a new field that it does not use yet.
3. Record the `eventId` durably before returning `2xx`; if an id that already exists comes in again, return `2xx` without repeating the effect.
4. Hand long work to a separate queue and answer within 5 seconds.
5. Call the data API again only when you need the content of the record, with a narrow-permission client that fits.

`record.event` and `admin.event` have their own `eventId` sequences.
A receiver that handles both kinds must de-duplicate by the pair of message kind and `eventId`.

### 3. Run locally and catch up

The webhook URL must be reachable from the backend container.
On Docker Desktop you can use `host.docker.internal`; on Linux, run the receiver in a Docker network that the backend can reach or use a separate test address.

To test:

- Create or edit a record and confirm that the receiver stores it exactly once.
- Return an error code to see the message on the Event queue tab, then restore the receiver and click **Retry**.
- Send the same payload again to show that `eventId` is de-duplicated.
- Change one byte of the body and confirm that the signature is refused.

A receiver that missed messages catches up page by page with `GET /api/v1/events?after=<eventId>&size=500` and continues while `more` is `true`.
This feed requires `ADMIN`; a client made with `dxos client add` is not granted this platform role.
The changes of the administration are caught up with `GET /api/v1/events/admin` with the same conventions `after`, `size`, `next` and `more`.
Keep the cursor only after the whole page has been stored successfully.

[`examples/history-witness`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/examples/history-witness/README.md) shows reading all the pages, keeping the `eventId` with the `hash` outside the server and comparing them again later.

### 4. Package and upgrade

Package the receiver separately from DX-OS, keep the secret in a runtime secret, and back up the de-duplication store together with the cursor.
Watch `schemaVersion`, check a sample payload before you upgrade, and deploy a receiver that accepts both the old and the new version during the transition when the contract changes.

## Links to the DX-OS web interface

An outside application sends an approver to the web interface of DX-OS with a link.
The inbox is the home page of the public address (`GATEWAY_PUBLIC_URL`), for example `http://localhost:9080/`.
A record is at `<public address>/<model>-<entity>/<id>/show`, for example `http://localhost:9080/retail-return_request/12/show`.
The person who opens the link signs in with a DX-OS account and sees the record only when their role may read it.

The data API returns this address ready-made in the `webUrl` field of the record, and the webhook messages and the `GET /api/v1/events` feed carry the same field, so the application does not build the address itself.
`webUrl` is computed by the system and is not a field of the model, and it is absent when you run `dxos start --core` because that mode has no web application.

## An outside interface

A browser interface must sign users in with Authorization Code and PKCE.
Do not put a client secret or use `client_credentials` in JavaScript sent to a browser.

### Register the client and the origin

An example where the interface is served at `https://partner.example` and receives the callback at `/auth/callback`:

```bash
dxos client browser add partner-ui \
  --redirect-uri https://partner.example/auth/callback
```

The command creates the public client `dxos-browser-partner-ui`, turns on Authorization Code with PKCE S256, adds the audience mapper `dxos-api` and the group claim, and then adds `https://partner.example` to `CORS_ALLOWED_ORIGINS` in `deploy/.env`.
If the backend is running, the command recreates only the backend and waits for the service to be ready.
Give several `--redirect-uri` when the application has its own callback and silent renew; each URI is kept exactly, and is never widened with a wildcard.

The application uses:

| OIDC value | Setting |
|---|---|
| Authority | `http://localhost:9080/realms/dxos` |
| Client id | `dxos-browser-partner-ui` |
| Flow | Authorization Code + PKCE |
| Redirect and post-logout URI | One exact URI that was registered |
| Scope | `openid profile` |

Replace the authority with the real gateway address when you deploy.
Do not put a secret in the interface; a public client has no secret.

A redirect outside the development machine must use HTTPS; HTTP accepts only `localhost`, `127.0.0.1` or `[::1]`.
A wildcard, user info, a fragment, or a wrong host or port is refused before Keycloak is changed.
`CORS_ALLOWED_ORIGINS` holds only origins separated by commas, with no path, query or trailing `/`; the backend checks it again at startup and `dxos doctor` reports a wrong value ahead of time.

After sign-in, send the access token in `Authorization: Bearer <token>` and read `/api/v1/meta/models/{model}/openapi` as that same user.
The generated document holds only the entities the user may see, so the interface must still handle `403` when permissions change and must not infer permissions from the buttons it shows.

Test the flow of sign-in, the callback, refresh or sign-in again when the token expires, single logout, `401`, `403` and going back to the original page.
List clients with `dxos client browser list` and remove one with `dxos client browser remove partner-ui`.
The remove command keeps the origin in `CORS_ALLOWED_ORIGINS` because another client may share it; the administrator deletes an origin that is no longer used and then recreates the backend.

Serving the interface under the same gateway still needs a route of its own, with the limits of the [Custom routes in APISIX](#custom-routes-in-apisix) section.

## A custom page in the Tier 2 web app

First check whether the `ui:` block of the model file can meet your needs for menus, columns, forms, hints, calendars and statistics.
This declarative path is kept on upgrade and needs no frontend rebuild.

If you still need a page of your own, for now the only way is source customization:

1. Create a component under `frontend/src/` and load it lazily if the page is large.
2. Add a `<Route>` in `<CustomRoutes>` and the matching `Menu.Item` in `frontend/src/admin/AdminApp.tsx`.
3. Use `frontend/src/shell/api.ts` so that requests carry the token, and keep the permission checks in the backend.
4. Run `npm ci` and then `npm run build` in `frontend/`.
5. In a source environment, build and replace the image on its own with `cd deploy && docker compose build frontend && docker compose up -d --no-deps frontend`.

Package a customized frontend as an image with its own tag.
`dxos update` pulls the official frontend image, while upgrading the installer replaces the runtime files; so the team that owns the customization must rebase the change, run the build again and release the image again after every version.

There is no registry for pages or form components, no sandbox and no plugin compatibility check yet.
Do not load arbitrary JavaScript from a model or from a URL that a user typed.

## Custom routes in APISIX

A service that only calls DX-OS does not need this route.
Add a route only when users or other systems must call the extension service through the same entry point.

Create `deploy/apisix/local.yaml` in the install from [`local.example.yaml`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/deploy/apisix/local.example.yaml); this file holds only the additions, and `common.yaml` is managed by the core.
The following example assumes the container `extension-worker` is in the same Docker network as APISIX:

```yaml
upstreams:
  - id: extension-worker
    type: roundrobin
    nodes:
      "extension-worker:8080": 1
routes:
  - id: extension-worker-api
    uri: /extensions/worker/*
    upstream_id: extension-worker
    plugin_config_id: bearer-token
```

The local file accepts `upstreams`, `routes` and the optional `public_routes`, in one YAML document of at most 1 MiB, with no duplicate keys, anchors or aliases.
A route must have its own id, valid references and a priority greater than the `-10` of the web fallback (`0` by default).
The path of a local route must not overlap a core route or another local route, except the web fallback `/*`; a path with `%`, a `.` or `..` segment, a double slash or a control character is refused.
Host, methods and priority are not allowed to override a core path, even when the matching component is switched off in `core` mode.

APISIX forwards the path `/extensions/worker/...` unchanged to the service.
`bearer-token` authenticates the token; the extension service must still check business permissions.
A public route is declared explicitly as `public_routes: {<id>: "reason it is public"}` in the local file; the id must belong to a local route that does not check a token.
This metadata serves only the configuration check and is not loaded as an APISIX resource.

Check and reload from the `deploy` folder of the install:

```bash
docker compose run --rm --no-deps apisix-config --check
docker compose run --rm --no-deps apisix-config
```

The checker uses the same pinned APISIX image as the gateway, so no Python needs to be installed on the deployment machine.
In the source tree you can run `python3 e2e/api/check_routes.py --local <file>` (it needs Python, PyYAML and Docker; add `--mode core` or `--admin-console` to check the matching configuration).
A valid configuration is merged in the order core then local and replaces the output file atomically, and the running APISIX reads it again by itself.
An error in the schema, a plugin, a reference or a conflict keeps the previous valid output file and names only the file, the resource and the kind of error.
After you replace the upstream container, run `docker compose restart apisix` to refresh the address.

`dxos update` keeps the local file unchanged and checks it against the new `common.yaml` before it replaces the containers.
A conflict with the new core version stops the update so the deployer can fix the local file; the step that upgrades the installer may already have replaced the runtime files, so this is not a full undo.
Remove the local file and reload to remove the custom routes.
Keep a copy of the local file with the deployment configuration, because the backups of Postgres and SeaweedFS do not hold this file.
