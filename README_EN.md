# DX-OS: a dynamic core for record-and-workflow systems (Open Source Software 2026 Contest Entry)

[Tiếng Việt](./README.md) · **English**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![AI License](https://img.shields.io/badge/ai%2F-AGPL--3.0-orange.svg)](./ai/LICENSE)

**Team:** HaUI-ImplSolution · **School:** School of Information and Communication Technology, Hanoi University of Industry

**Documentation:** tracked on the [docs site](https://haui-implsolution.github.io/DX-OS-docs/); the design is in [`docs/dynamic-core/README.md`](./docs/dynamic-core/README.md) (Vietnamese).

Entry of the **OLP Open Source Software 2026** contest (DX-OS track, VFOSSA).
The core takes a model file that declares entities, fields, roles, permissions and workflows, and builds the tables, the login roles, an API with row-level permissions, an approval workflow and the screens.
Changing the business means changing the model file, not the code.
The previous version of the project (two hand-written business apps) is at tag `v1.1.1`.

## Status

The narrow loop from model file to end user runs:

- Reading and checking a model file, with errors reported by line and column.
- Applying a model to an empty system: schema, tables, constraints, Keycloak roles, a stored version and the history of each step; applying again does nothing, and an interrupted apply is finished by applying again.
- One API for every entity, permissions by role and by row (`all`, `own`, `read`), required, unique and choice rules.
- A single-level approval workflow on an embedded Flowable; the status of a record changes only through the workflow, in the same transaction as the write of the record.
- A react-admin UI built from metadata, with no code written for any entity.

Not yet: changing a model that already holds data, file attachments, an event axis, chat notifications, AI, Superset reports.
These are in the [design](./docs/dynamic-core/README.md).

## Run it

You need Docker with Compose v2 and Python 3.

```bash
python3 deploy/make_env.py              # writes deploy/.env with random passwords
cd deploy && docker compose up -d --build
```

The gateway is at `http://localhost:9080`: web, API, Swagger (`/swagger-ui.html`), Keycloak (`/admin`) and the portal (`/portal/`).
The Keycloak admin account is `KEYCLOAK_ADMIN_USERNAME` and `KEYCLOAK_ADMIN_PASSWORD` in `deploy/.env`.

Apply the sample model as a user with the `ADMIN` role (the script creates a test user `admin1`, for development machines only):

```bash
python3 - <<'EOF'
import sys; sys.path.insert(0, "deploy/dev")
import kc
kc.ensure_client(); kc.ensure_user("admin1", ["ADMIN"])
print(kc.api(kc.token("admin1"), "POST", "/models/apply", open("models/equipment.yaml").read(), "application/yaml"))
EOF
```

Then create users with the role `BORROWER` or `KEEPER` in Keycloak, sign in at `http://localhost:9080/` and use the screens.
The full testing guide is [`docs/TESTING.md`](./docs/TESTING.md) (Vietnamese).

## The model file

A shortened [`models/equipment.yaml`](./models/equipment.yaml):

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
    process: {template: approval, approver: KEEPER}
```

- Field types: `text`, `number`, `datetime`, `choice`, `reference`, `user`.
- Access by role: `all` (every row, read and write), `own` (the rows of the user), `read` (every row, read only); `owner_via` lets a child entity follow the owner of its parent.
- Workflow: the `approval` template, one role approves or rejects.
  A record that has been decided can no longer be edited or deleted.
  The creator of a record cannot decide it unless `allow_self_decision: true` is declared.
- Schema for editors: [`model-v0.schema.json`](./backend/dx-model/src/main/resources/schema/model-v0.schema.json); sample bad files in [`models/invalid/`](./models/invalid).

## API

| Path | Purpose |
|---|---|
| `POST /api/v1/models/plan`, `/apply` | Check and apply a model file (ADMIN) |
| `GET /api/v1/models`, `/models/{model}/runs` | Applied models and the history of each step (ADMIN) |
| `GET /api/v1/meta/models` | Metadata the caller may see |
| `/api/v1/data/{model}/{entity}` | List, create, read, update, delete; filter with `filter.<field>[.<op>]`, `sort`, `order`, `page`, `size` |
| `POST /api/v1/data/{model}/{entity}/{id}/decision` | `approve` or `reject` by the approver role |

Every reply is wrapped in `ApiResponse`; errors carry a stable `errorCode`.

## Repository layout

| Directory | Content |
|---|---|
| `backend/dx-model` | Model file format: reader and checker |
| `backend/dx-core` | Shared library: security, audit, storage, status, Keycloak client |
| `backend/dx-engine` | The core service: apply a model, the API, Flowable |
| `backend/dx-monitor` | Monitoring and tracing screen |
| `frontend` | react-admin UI built from metadata |
| `deploy` | Compose, the APISIX gateway, operations scripts, check scripts in `deploy/dev` |
| `sso`, `portal`, `seaweedfs` | Keycloak realm, portal page, file storage |
| `models` | Sample models and sample bad files |
| `ai`, `bi`, `chat`, `data` | AI, reports and chat, to be rebuilt around the model in later steps |

## Tests

```bash
cd backend && ./mvnw verify            # tests need Docker (Testcontainers)
cd frontend && npx tsc --noEmit
python3 deploy/dev/check_api.py        # on a fresh stack, empty database
python3 deploy/dev/check_recovery.py
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
