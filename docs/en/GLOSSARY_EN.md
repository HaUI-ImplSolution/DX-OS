# Glossary

The words used in the documentation and the interface of DX-OS.
Each word is explained in one or two lines, with the document to read for more.
Where a term appears in the Vietnamese documents or interface, the Vietnamese word is given in parentheses.

## 1. Product and architecture

| Term | Meaning | See also |
|---|---|---|
| DX-OS | An open-source dynamic core for record-and-workflow systems: declare the business in one model file and the core builds the rest | [`README_EN.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/README_EN.md) |
| Core, Tier 1 (lõi) | The part that runs behind the scenes, with no interface and only an API: identity, API gateway, data, workflow, history, events | [Architecture](./ARCHITECTURE_EN.md#1-three-tiers) |
| Applications, Tier 2 (ứng dụng) | The web interface, the control panel, the portal, the monitoring screen and the `dxos` command, built from the data of the core and calling it through the API gateway | [Architecture](./ARCHITECTURE_EN.md#1-three-tiers) |
| Models, Tier 3 (mô hình) | The files that describe a business; adding a business means adding a file, with no code | [Writing a model file](./MODEL_EN.md) |
| H-P-D-I | The four spaces of the contest brief: H a shared digital environment, P guard rails and automatic flows, D one source of truth, I artificial intelligence | [`README_EN.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/README_EN.md#against-the-brief) |
| Headless | Having no interface: the core only serves an API to the applications above it | [Architecture](./ARCHITECTURE_EN.md) |
| API gateway, APISIX | The only gate open to the outside: checks tokens at the edge, limits rates and routes to the web app, the API, Keycloak and monitoring | [Architecture](./ARCHITECTURE_EN.md#3-identity-and-tokens) |
| `dx-engine` | The runnable core service: the control part, the common API and the embedded Flowable | [Architecture](./ARCHITECTURE_EN.md#2-backend-modules) |
| `dx-model` | The module that reads and checks model files and reports errors by line and column; it does not depend on Spring | [Architecture](./ARCHITECTURE_EN.md#2-backend-modules) |
| `dx-core` | The shared library of the backend: security, audit, storage, file scanning, the Keycloak client | [Architecture](./ARCHITECTURE_EN.md#2-backend-modules) |
| `dx-monitor` | The monitoring service that runs on its own and does not depend on the other modules | [Monitoring and logs](./MONITORING_EN.md) |
| Control panel (bảng điều khiển) | The deployer's page `/console` (ADMIN): template library, model editor, plan, apply, users, outside applications, data catalog, projects | [`TESTING.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/TESTING.md) (Vietnamese) |
| Portal | The page `/portal/` that lists the addresses and the state of the services | [`README_EN.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/README_EN.md) |
| Core-only mode | `dxos start --core` runs only Tier 1: no web app, portal, monitoring or assistant | [Install and operations](../OPERATIONS.md#chế-độ-chỉ-lõi) (Vietnamese) |
| Extension point (điểm mở rộng) | Where users add their own service, webhook receiver, custom interface or custom route outside the core | [Extension points](./EXTENSIONS_EN.md) |

## 2. The model file

| Term | Meaning | See also |
|---|---|---|
| Model file (tệp mô hình) | A YAML file that declares the entities, fields, roles, permissions and workflow of a business | [Writing a model file](./MODEL_EN.md) |
| Format (`format`) | The version number of the syntax of the model file; the core upgrades an old file in memory, and `dxos model upgrade` writes a new file | [Writing a model file](./MODEL_EN.md#upgrading-the-format) |
| Entity (thực thể) | A kind of record of the business, such as a request or a device; each entity becomes a data table | [Writing a model file](./MODEL_EN.md#fields-and-units) |
| Field (trường) | A column of the entity, with a type (`text`, `number`, `date`, `choice`, `reference`, `file`...) and constraints | [Writing a model file](./MODEL_EN.md#fields-and-units) |
| Role (vai) | The group of permissions of a user in a model; the roles of a model live in the client `dxos-m-<model>` in Keycloak | [Architecture](./ARCHITECTURE_EN.md#3-identity-and-tokens) |
| `ADMIN` | A role of the platform that belongs to no model: it uses the control panel and manages users | [Install and operations](../OPERATIONS.md) (Vietnamese) |
| Unit (đơn vị) | The organization structure, a Keycloak group shared by every model; it decides only the scope of rows, not what a person may do | [Writing a model file](./MODEL_EN.md#fields-and-units) |
| Row-level permission (quyền theo dòng) | How far a role may touch records: `all` every row, `read` every row read-only, `own` the rows you created, `unit` the rows of your unit, `assigned` the rows assigned to you | [Writing a model file](./MODEL_EN.md#permissions-by-role) |
| Ready template (mẫu có sẵn) | A sample model that can be picked at once in the control panel, kept in `backend/dx-engine/src/main/resources/templates/` | [Writing a model file](./MODEL_EN.md) |
| Record code (mã hồ sơ) | A field with `type: code` gives each record a short unique code, such as `HS-2026-00007`, for reading, printing and lookup | [Writing a model file](./MODEL_EN.md#readable-record-codes) |
| `required_when` | The key that makes a field required only when another field has a certain value | [Writing a model file](./MODEL_EN.md#conditional-required) |
| `ui` block | The part of the model file that chooses the icon, the menu group, the columns, the form groups and the hints | [Writing a model file](./MODEL_EN.md#the-ui-block) |
| Print documents (tài liệu in) | The `documents` key declares the template of a certificate, slip or minutes; each record becomes a PDF file | [Writing a model file](./MODEL_EN.md#print-documents) |

## 3. Workflow

| Term | Meaning | See also |
|---|---|---|
| Workflow family (họ mẫu) | A ready-made workflow, called a workflow template in the control panel: `approval` (a chain of approvals), `work_item` (a piece of work to handle), `reservation` (occupying a resource over a time slot) | [Writing a model file](./MODEL_EN.md#workflow) |
| Status (trạng thái) | Where a record stands in the workflow; only the workflow can change the status column | [Writing a model file](./MODEL_EN.md#declaring-or-extending-a-workflow-yourself) |
| Action (hành động) | A step that moves the status, taken by a role, which may need a reason, required fields or a `when` condition | [Writing a model file](./MODEL_EN.md#declaring-or-extending-a-workflow-yourself) |
| Final status (trạng thái cuối) | A status that closes the record: afterwards the record is frozen and keeps who decided and when | [Writing a model file](./MODEL_EN.md#declaring-or-extending-a-workflow-yourself) |
| Inbox (hộp việc) | A person's list of waiting work; it refreshes by itself when a record changes | [`TESTING.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/TESTING.md#giao-diện-web) (Vietnamese) |
| Deadline (hạn xử lý) | The time a record has to be handled (`deadline`); when it passes, it is written to the history and reminded | [Writing a model file](./MODEL_EN.md#workflow) |
| Working calendar (lịch làm việc) | Working days, working hours and days off declared in `calendars`; a workflow picks a calendar with `calendar` so that the deadline counts only working hours | [Writing a model file](./MODEL_EN.md#working-calendars) |
| Escalation (leo thang) | The levels at which another role is told when a record stays overdue for long; it only notifies and changes neither the status nor who holds the record | [Writing a model file](./MODEL_EN.md#workflow) |
| Completion action (hành động hoàn tất) | An operation that runs after a record reaches a final status, such as adding a person to a Keycloak group; it runs after the transaction and is retried on error | [Writing a model file](./MODEL_EN.md#completion-actions) |
| `check` | The key of an action that lets a separate service decide before the status changes; a refusal returns 403 | [Writing a model file](./MODEL_EN.md#declaring-or-extending-a-workflow-yourself) |
| Withdraw (rút lại) | The submitter takes the record back in the statuses listed in `withdraw` | [Writing a model file](./MODEL_EN.md#workflow) |
| Invariant rules (quy tắc bất biến) | The rules inside the core that a model cannot switch off: only the workflow changes the status, the creator cannot approve their own record, every step leaves a history | [`dynamic-core/README.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/README.md#quy-trình-chung) (Vietnamese) |
| Flowable | The workflow engine embedded in `dx-engine`; the core generates the BPMN from the model file | [Architecture](./ARCHITECTURE_EN.md#6-workflow-and-status) |

## 4. Changing a model while the system runs

| Term | Meaning | See also |
|---|---|---|
| Plan (`plan`, kế hoạch) | The preview of a change: what will change (for a workflow action, which action and which property), the risk level, the rows that violate and the number of waiting records that a new `require` would block (`impacts`); nothing is changed yet | [Architecture](./ARCHITECTURE_EN.md#4-applying-a-model) |
| Apply (`apply`, áp dụng) | Bring the system to exactly the model file, step by step, recording each step | [Architecture](./ARCHITECTURE_EN.md#4-applying-a-model) |
| `SAFE`, `MIGRATION`, `LOSS` | The three risk levels of a plan: can be applied at once; needs the data to be migrated (a violating row stops it); loses data and so needs a separate confirmation | [Architecture](./ARCHITECTURE_EN.md#4-applying-a-model) |
| Interrupted apply | A failing step stops the apply; the model stays held until an administrator chooses to continue (`resume`) or abandon (`abandon`) | [Architecture](./ARCHITECTURE_EN.md#holding-a-model-and-an-interrupted-apply) |
| Repair (`repair`) | Applying an unchanged file again to rebuild what was lost in a table or in Keycloak, without raising the version | [Architecture](./ARCHITECTURE_EN.md#4-applying-a-model) |
| Rollback (`rollback`) | Apply the content of the previous version again, recorded as a new version | [Architecture](./ARCHITECTURE_EN.md#4-applying-a-model) |
| `renamed_from` | Declares the old name of a field so it can be renamed while keeping the data | [Writing a model file](./MODEL_EN.md#changing-a-model-that-already-has-data) |
| `pgschema` | An open-source tool that compares the tables with the model file and changes the tables to match; the core handles renames and blocks data loss | [Architecture](./ARCHITECTURE_EN.md#4-applying-a-model) |
| GitOps | `dxos gitops` pulls the infrastructure and the model files from git on a schedule, then reports drift, waits for approval or applies on its own | [Install and operations](../OPERATIONS.md) (Vietnamese) |
| `dxos lint` | Checks a model file with the very reader of the backend, with no running system needed | [Writing a model file](./MODEL_EN.md#checking-a-file-before-applying) |

## 5. Data and events

| Term | Meaning | See also |
|---|---|---|
| Tamper-evident history (lịch sử chống sửa) | The history rows of a record are chained by hash; if the immutable part of a row is edited or a row is deleted or inserted, `dxos history verify` points at the first row that changed; the name, note and detail of a new row sit outside the chain | [Integrating with outside systems](./INTEGRATIONS_EN.md) |
| Anchor (điểm neo) | Every hour the core writes a mark of the hash chain into the object store, outside PostgreSQL, to compare against when checking | [Architecture](./ARCHITECTURE_EN.md#10-integrity-and-recovery) |
| Outbox | A table in PostgreSQL that holds the events waiting to be sent, so they can be sent again when the receiver fails | [Integrating with outside systems](./INTEGRATIONS_EN.md) |
| Webhook | A notification of each change of a record to a web address or a chat channel, signed so that the receiver can verify it | [Extension points](./EXTENSIONS_EN.md#a-webhook-receiver) |
| `Idempotency-Key` | A header for a request that creates a record or runs an action: sending the same key with the same content again does the work only once | [Extension points](./EXTENSIONS_EN.md#3-call-the-data-and-the-workflow) |
| Data lake (hồ dữ liệu) | A Parquet store in SeaweedFS that holds closed records and their history, with a manifest and hashes; read with DuckDB | [Architecture](./ARCHITECTURE_EN.md#10-integrity-and-recovery) |
| Data catalog (danh mục dữ liệu) | A page that tells where the data of each model lives, who may see it, where it comes from and goes to, and how good it is | [Architecture](./ARCHITECTURE_EN.md#11-data) |
| Lineage (phả hệ) | For each field: where the value comes from, which rules read it and where it goes | [Architecture](./ARCHITECTURE_EN.md#11-data) |
| `report_ro` | A read-only PostgreSQL role for reporting tools; it sees every row and applies no row-level permission | [Connecting a reporting tool](./REPORTING_EN.md) |
| Per-model OpenAPI | API documentation generated at the moment of the call from the running model, so it always matches the model file | [API](./API_EN.md) |

## 6. Sign-in, operations and licenses

| Term | Meaning | See also |
|---|---|---|
| Keycloak, realm `dxos` | The single sign-on service; it keeps accounts, passwords and roles | [Architecture](./ARCHITECTURE_EN.md#3-identity-and-tokens) |
| Project (dự án) | A name that groups the models and outside applications of one integrated system, to filter and see the reach of a change; it grants no permission and isolates no data | [Install and operations](../OPERATIONS.md#dự-án) (Vietnamese) |
| Project member and administrator | A person within the scope of a project, and a person who may manage, outside that scope, the members and the roles of the models in the project; no `ADMIN` role is needed and no data permission is granted | [Install and operations](../OPERATIONS.md#dự-án) (Vietnamese) |
| Client | An application registered with Keycloak: `dxos-web` for the web, `dxos-cli` for the command, `dxos-m-<model>` holding the roles of a model, `dxos-app-<name>` for an outside application | [Architecture](./ARCHITECTURE_EN.md#3-identity-and-tokens) |
| Machine token | The token an outside application gets with its own secret; its service account holds exactly one role of one model | [Extension points](./EXTENSIONS_EN.md#a-separate-service-that-calls-the-api) |
| OIDC, PKCE | How an interface on another domain signs users in without keeping a secret in the browser | [Extension points](./EXTENSIONS_EN.md#an-outside-interface) |
| Self-healing (`reconcile`) | `dxos reconcile` brings the stack back to what is declared, recreating containers that were deleted or drifted; it runs every minute from a systemd timer | [Install and operations](../OPERATIONS.md) (Vietnamese) |
| ClamAV | The service that scans uploaded files for malware; a file that cannot be scanned is refused | [Install and operations](../OPERATIONS.md#quét-mã-độc-tệp-tải-lên) (Vietnamese) |
| SPDX, REUSE | The two lines `SPDX-FileCopyrightText` and `SPDX-License-Identifier` at the top of each code file, and `REUSE.toml` for files that cannot hold them; `reuse lint` checks the whole tree | [`CONTRIBUTING.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/CONTRIBUTING.md) (Vietnamese) |
| SBOM | The bill of materials (CycloneDX) of the backend, the web app and the docs site, in `sbom/` | [`sbom/README.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/sbom/README.md) (Vietnamese) |
| OSI | The organization that approves open-source licenses; every component of DX-OS uses a license approved by the OSI | [`DEPENDENCIES.txt`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/DEPENDENCIES.txt) (Vietnamese) |
