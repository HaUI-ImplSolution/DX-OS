# DX-OS: a dynamic core for record-and-workflow systems (Open Source Software 2026 Contest Entry)

[Tiếng Việt](./README.md) · **English**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![AI License](https://img.shields.io/badge/ai%2F-AGPL--3.0-orange.svg)](./ai/LICENSE)

**Team:** HaUI-ImplSolution · **School:** School of Information and Communication Technology, Hanoi University of Industry

**Documentation:** tracked on the [docs site](https://haui-implsolution.github.io/DX-OS-docs/); the design is in [`docs/dynamic-core/README.md`](./docs/dynamic-core/README.md) (Vietnamese).

Entry of the **OLP Open Source Software 2026** contest (DX-OS track, VFOSSA).
The core takes a model file that declares entities, fields, roles, permissions and workflows, and builds the tables, the login roles, an API with row-level permissions, an approval workflow and the screens.
Changing the business means changing the model file, not the code.
The current release is `2.0.0`.
The previous version of the project (two hand-written business apps) is at tag `v1.1.1`, and there is no data upgrade path from `1.1.x` to `2.0.0`.

## Status

The narrow loop from model file to end user runs:

- Reading and checking a model file, with errors reported by line and column.
- Applying a model to an empty system: schema, tables, constraints, Keycloak roles, a stored version and the history of each step; applying the same file again does nothing unless the tables or Keycloak accounts have drifted from it, in which case the drift is put right, and an interrupted apply is finished by applying again.
- One API for every entity, permissions by role and by row (`all`, `own`, `read`, `unit`, `assigned`), required, unique and choice rules.
- Three workflow families on an embedded Flowable: approval, work items (which can close by themselves when the owner does not confirm), and time-slot reservations with a capacity check; the status of a record changes only through the workflow, in the same transaction as the write of the record.
- Rules shared by every workflow: rejecting and returning need a reason, the submitter fixes and resubmits or withdraws a record, nobody decides their own record, and overdue records go to the top of the inbox.
- Follow-up work after an approval, such as adding a user to a group, runs through an event queue and is retried on failure.
- Every change to a record is reported to the web address the model chooses, signed so the receiver can check it and sent again when the receiver fails; the changes of one record arrive in order, and a receiver that missed messages gets them again by sequence number.
- Outgoing messages can be a readable sentence for Mattermost and Slack: a new task, due soon, overdue; applying a model and changing an account are reported too.
- File attachments on records, with a virus scan when enabled.
- Each model has its own roles: two models can use the same role name, and a holder of the role of one model cannot reach the other model.
- Units declared in the model and kept as Keycloak groups; assignment, an inbox and the history of every record.
- Changing a model that holds data: a preview by level of risk, renames that keep the values, a list of the rows breaking a new rule, a separate confirmation for losing data, a return to the previous version, and finishing or giving up an apply that stopped halfway.
- A react-admin UI built from metadata, in Vietnamese with a light and a dark mode, and a booking calendar where bookings can be dragged, with no code written for any entity.
- A control panel for the person deploying the system: a template library, a model editor, a preview of the plan, apply, history and rollback, and a diagram of the workflow.
- One command takes a model file on a clean machine: the system starts, the file is applied and you can sign in at once.
- User administration in the control panel: see who holds which role in each application, give and take roles and units, create a user with a temporary password, lock and unlock, reset a password; every change is recorded with who made it and can be read back in the person's dialog.
- Applying a model waits for the running operations of that model before the tables change, and checks the open records again once the model is held, so no record slips through in between; cancelling a first apply also removes the access settings it created.
- The history of records is chained with checks and anchored at regular times; one command names the line that was changed, deleted or inserted.
- Statistics for every entity with a process: total, by status, overdue and by unit, with the same viewing rights as the list; each number opens the matching list.
- Models can also be changed from the command line for automation, and finished records can be exported to analysis files with a manifest to check them again.
- Backups also check attachments; a removed file leaves the store only after a waiting time so the backup is always complete; services restart on their own and one command brings the system back to what is declared.
- A device-connection example: a simulator opens an incident record when a reading passes its limit and does not create a duplicate when the reading is sent again.
- Single sign-on through Keycloak; administrators can be required to enter a one-time code from an authenticator app as well.

Not yet available: chat notifications, AI answers.
The search service with cited sources in [`ai/`](./ai/README.md) runs on its own and is not in the stack.
These are in the [design](./docs/dynamic-core/README.md).

## Run it

### Install

You need a Linux machine with Docker and Compose v2, and no source code.

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

The installer downloads the latest release, checks its SHA-256 and installs it into `~/.dxos`; the `dxos` command goes to `~/.local/bin`.
`dxos start` downloads the images the first time, waits until every service is ready, creates the account `admin` with a temporary password printed on screen, and prints the addresses.
Sign in at `http://localhost:9080/console` as `admin`, change the password, pick a template in the template library, look at the plan and apply it.
With a model file already at hand, `dxos start <file>` takes it in at once: the backend applies the file on start when it is new, and the command prints the result.
The other commands (`stop`, `status`, `logs`, `admin`, `update`, `backup`, `plan`, `apply`, `history`, `reconcile`, `archive`, `uninstall`) are in `dxos help` and [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md#cài-đặt-và-lệnh-dxos) (Vietnamese).

### From the source

```bash
deploy/make_env.sh                       # writes deploy/.env with random passwords
cd deploy && docker compose up -d --build
```

The gateway is at `http://localhost:9080`: web, API, Swagger (`/swagger-ui.html`), Keycloak (`/admin`) and the portal (`/portal/`).
The Keycloak admin account is `KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD` in `deploy/.env`.

Apply the sample model as a user with the `ADMIN` role (needs Python 3; the script creates a test user `admin1`, for development machines only):

```bash
python3 - <<'EOF'
import sys; sys.path.insert(0, "deploy/dev")
import kc
kc.ensure_client(); kc.ensure_user("admin1", ["ADMIN"])
print(kc.api(kc.token("admin1"), "POST", "/models/apply", open("backend/dx-engine/src/main/resources/templates/equipment.yaml").read(), "application/yaml"))
EOF
```

Then create users in the Users tab of `http://localhost:9080/console`, give them the role `BORROWER` or `KEEPER` of the model `equipment`, sign in at `http://localhost:9080/` and use the screens.
The full testing guide is [`docs/TESTING.md`](./docs/TESTING.md) (Vietnamese).

## The model file

A shortened [`backend/dx-engine/src/main/resources/templates/equipment.yaml`](./backend/dx-engine/src/main/resources/templates/equipment.yaml):

```yaml
format: 0
name: equipment
roles:
  - {name: BORROWER}
  - {name: KEEPER, staff: true}
entities:
  device:
    fields:
      code: {type: text, required: true, unique: true}
      name: {type: text, required: true}
    access: {BORROWER: read, KEEPER: all}
  loan:
    fields:
      device: {type: reference, to: device, required: true}
      reason: {type: text, required: true}
      from_at: {type: datetime, required: true}
    access: {BORROWER: own, KEEPER: all}
    process: {family: approval, handler: KEEPER}
```

- Field types: `text`, `number`, `datetime`, `choice`, `reference`, `user`, `unit`.
- Units: declared in `units`, each one a Keycloak group; a `unit` field says which unit a record belongs to. A full example is [`backend/dx-engine/src/main/resources/templates/onestop.yaml`](./backend/dx-engine/src/main/resources/templates/onestop.yaml).
- Access by role: `all` (every row, read and write), `own` (the rows of the user), `read` (every row, read only), `unit` (the rows of the units of the user), `assigned` (the rows assigned to the user); `owner_via` lets a child entity follow the owner of its parent, through any number of levels.
- Workflow: `family` picks the process family (`approval` or `work_item`), `handler` is the role that handles records, `deadline` is an optional handling time (for example `72h`); the `work_item` family also takes an optional `auto_close` (for example `7d`): a resolved record that its owner does not confirm within that time is closed by the system.
  Rejecting and returning need a reason; the owner can withdraw a record that is not in a final state.
  A record with a process cannot be deleted; a decided record can no longer be edited; an assigned record can only be handled by its assignee.
  The creator of a record cannot decide it unless `allow_self_decision: true` is declared.
- Reservations: `family: reservation` with `booking: {resource, starts, ends, capacity}`; a booking that would put more bookings at the same moment than the capacity allows is refused, and the app has a calendar view.
  See [`room_booking.yaml`](./backend/dx-engine/src/main/resources/templates/room_booking.yaml).
- Completion actions: `after` gives a list of operations for a final state, for example `after: {approved: [{do: add_to_group, user: created_by, group_from: group_field}]}`; they run after the transaction commits and are retried on failure.
  The available operations are `add_to_group` and `remove_from_group`; the Keycloak group must already exist, otherwise the message shows in the Event queue tab; when the user or group field is empty the operation is skipped and the record gets a `completion_skipped` history line with the reason.
  See [`backend/dx-engine/src/main/resources/templates/itaccess.yaml`](./backend/dx-engine/src/main/resources/templates/itaccess.yaml).
- Attachments: a `type: file` field, uploaded with `POST /api/v1/data/{model}/{entity}/{id}/files`; access follows the access to the record.
  The accepted file types are set by `engine.files.allowed-extensions`, and a file is always downloaded as an attachment.
  A record holds at most 20 files, set by `engine.files.max-per-record`.
- Webhook: `webhook: <target name>` at the top of the file chooses where the history of the records of the model goes; the address and secret of a target live in `deploy/webhooks/targets.yml`, outside git, with a sample in `deploy/webhooks/targets.example.yml`.
  A model that declares none uses `DXOS_WEBHOOK_URL`; a target name missing from the file means the model cannot be applied.
  Each target has `format: json` (the default) or `format: chat`.
- Changing a model: declare `renamed_from` when renaming a field; `default` fills a new required field on a table that has rows.
- Schema for editors: [`model-v0.schema.json`](./backend/dx-model/src/main/resources/schema/model-v0.schema.json); sample bad files in [`models/invalid/`](./models/invalid).

## API

| Path | Purpose |
|---|---|
| `POST /api/v1/models/plan`, `/apply` | Preview and apply a model file (ADMIN) |
| `POST /api/v1/models/{model}/rollback/plan`, `/rollback` | Preview and return to the previous version (ADMIN) |
| `POST /api/v1/models/{model}/resume`, `/abandon` | Finish or give up an apply that stopped halfway (ADMIN) |
| `GET /api/v1/models`, `/models/{model}/runs` | Applied models and the history of each step (ADMIN) |
| `GET /api/v1/models/schema`, `/families`, `/templates`, `/{model}/versions` | The model file schema, workflow families, the template library and the applied versions, for the control panel (ADMIN) |
| `GET /api/v1/meta/models` | Metadata the caller may see; a model that is held shows with `held` and no entities |
| `/api/v1/data/{model}/{entity}` | List, create, read, update, delete; filter with `filter.<field>[.<op>]`, `sort`, `order`, `page`, `size`; an update takes the `version` it read, and a record that changed answers 409 `ERR_CONCURRENT_MODIFICATION`; a 403 `ERR_ACTION_NOT_ALLOWED` carries `data.reason` |
| `GET /api/v1/data/{model}/{entity}/stats` | Counts over the rows the caller may see, with the same `filter.*` parameters as the list: `total`; for an entity with a process also `byStatus` (records per status) and `overdue` (records whose deadline is before `computedAt`); for an entity with a `unit` field also `byUnit`; the query is cut after 10 seconds |
| `POST /api/v1/data/{model}/{entity}/{id}/actions/{action}` | An action of the workflow; the metadata lists the actions, their starting state and who may take them |
| `POST .../{id}/assign`, `GET .../{id}/handlers` | Assign a record and list who can take it |
| `GET .../{id}/events` | The history of a record |
| `.../{id}/files` | Upload, list, get a time-limited download link (`/{fileId}/link`) and remove file attachments |
| `GET /api/v1/inbox` | The records waiting for the caller, on every model; `page`, `size` |
| `GET /api/v1/events` | The history lines in the layout of the webhook, after an `eventId` (`after`, `size`, `model`) (ADMIN) |
| `GET /api/v1/outbox`, `POST /api/v1/outbox/{id}/retry` | Outgoing messages by status (failed, pending, delivered) and a retry of an undelivered one (ADMIN) |
| `GET /api/v1/users`, `/users/options`, `/users/{id}` | Find and read users, their roles per model and their units; the roles and units that can be given (ADMIN) |
| `PUT`, `DELETE /api/v1/users/{id}/roles/{model}/{role}`, `/units/{unit}` | Give and take a role of one model, and a unit (ADMIN) |
| `POST /api/v1/users`, `PUT /{id}/enabled`, `POST /{id}/password` | Create a user with a temporary password, lock and unlock, reset a password (ADMIN) |
| `GET /api/v1/audit-logs` | The log of refused requests, failures, changes to accounts and applies of models; filter by `actor`, `action`, `targetType`, `targetId`, `outcome`, `from`, `to` (ADMIN) |

Every reply is wrapped in `ApiResponse`; errors carry a stable `errorCode`.

## Repository layout

| Directory | Content |
|---|---|
| `backend/dx-model` | Model file format: reader and checker |
| `backend/dx-core` | Shared library: security, audit, storage, status, Keycloak client |
| `backend/dx-engine` | The core service: apply a model, the API, Flowable |
| `backend/dx-monitor` | Monitoring and tracing screen |
| `frontend` | react-admin UI built from metadata |
| `deploy` | Compose, the APISIX gateway, the installer and the `dxos` command, operations scripts, check scripts in `deploy/dev` |
| `sso`, `portal`, `seaweedfs` | Keycloak realm, portal page, file storage |
| `models` | Sample models and sample bad files |
| `ai` | A search service with cited sources (AGPL-3.0), run on its own outside the stack |

## Tests

```bash
cd backend && ./mvnw verify            # tests need Docker (Testcontainers)
cd frontend && npx tsc --noEmit
python3 deploy/dev/check_api.py        # on a fresh stack, empty database
python3 deploy/dev/check_recovery.py
python3 deploy/dev/check_case.py
python3 deploy/dev/check_change.py
python3 deploy/dev/check_process.py
python3 deploy/dev/check_booking.py
python3 deploy/dev/check_demo.py
python3 deploy/dev/check_guards.py
```

## Contributing

- **Bug reports:** [open a bug report](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=bug_report.yml).
- **Feature requests:** [open a feature request](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=feature_request.yml).
- **Code:** read [`CONTRIBUTING.md`](./CONTRIBUTING.md) and follow [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md).

## Contact

| Role | Name | Contact |
|---|---|---|
| Developer | Phạm Minh Khương | pkhuong535@gmail.com |
| Developer | Nguyễn Đình Duy Khánh | dinhkhanh19042006@gmail.com |
| Developer | Trần Quang Tùng | tung51652@gmail.com |

## License

Apache License 2.0, see [`LICENSE`](./LICENSE); the `ai/` directory is AGPL-3.0, see [`ai/LICENSE`](./ai/LICENSE) and [`COPYRIGHT.txt`](./COPYRIGHT.txt).

References: [contest topic](https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html) · [DX-OS curriculum](https://opendigitransform.gitbook.io/dx-os)
