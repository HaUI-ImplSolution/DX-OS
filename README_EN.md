# DX-OS

[Tiếng Việt](./README.md) · **English**

> **An open-source dynamic core for record-and-workflow systems.**
> Declare a business in one model file; DX-OS builds the database, the accounts and roles, a permissioned API, the approval workflow and the screens.
> Changing the business means changing the file, not the code.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![Platform](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/ci-platform.yml/badge.svg)](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/ci-platform.yml)
[![Compliance](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/ci-compliance.yml/badge.svg)](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/ci-compliance.yml)
[![Docs](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/docs.yml/badge.svg)](https://haui-implsolution.github.io/DX-OS/)

Entry of the **OLP Open Source Software 2026** contest, DX-OS track (VFOSSA), by team **HaUI-ImplSolution**, School of Information and Communication Technology, Hanoi University of Industry.

**Quick links:** [Overview](#overview) · [Features](#features) · [How it works](#how-it-works) · [Try it](#try-it) · [Documentation](#documentation) · [Structure](#repository-structure) · [Contributing](#contributing) · [License](#license)

## Overview

Every school, department and small business has the same kind of process: one person sends a request, another reviews it, approves or returns it, and every step must leave a trace.
Each new business need usually means building login, permissions, file storage and the approval flow from scratch.

DX-OS builds that shared part once, as a headless core that is not tied to any business.
The person deploying it describes their business in a model file: what a record holds, who may see and edit it, who approves it, and how long handling may take.
The core reads the file and builds a working system.

The whole system is assembled from open-source software under OSI-approved licenses, installs on your own machine, and the data stays there.
The current version is `2.0.0`, and the changes of each version are in [`CHANGELOG.md`](./CHANGELOG.md).

## Features

- **From one file to a running system.** Records, tables, accounts, roles, list screens and forms are all built from the model file.
- **Permissions down to the row.** Each role sees and edits only its own work, the work of its unit, or the work assigned to it.
- **Data rules in the file.** True or false, dates, several values at once, limits on values, text patterns and conditional requirements are all declared in the file, and the database refuses a wrong value as well.
- **Customizable workflows.** Start from one of three ready made shapes (approval, work items, time-slot reservations with a capacity check) and add or remove steps, give each step to its own role, or declare a workflow from scratch; a step can open only when the record meets a condition, for example an amount above a limit goes to a higher approval level; rejecting and returning need a reason, and nobody approves their own record.
- **Changing the business once data exists.** The system previews the impact, warns about risk, keeps the values when something is renamed, and can return to the previous version.
- **A trace that cannot be edited.** The history of records is one chain with checks; if anyone edits, deletes or inserts a line, one command names that line.
- **Connections to the outside.** Every change to a record is reported to a web address or a chat channel (Mattermost, Slack), signed so the receiver can check it and sent again when the receiver fails.
- **Open to outside applications.** Each model has its own API document that follows the model, and an outside application is registered with exactly one role of one model and calls with its own secret.
- **A control panel for the person deploying.** Template library, model editor (write the file or fill a form, with guidance on what can be written and extended) with a process diagram drawn from the file being written, plan preview, apply, history, user management and record statistics.
- **An interface set by declaration.** The model file can choose icons, menu groups, the default columns and sort, how forms are grouped and the hints; an officer selects several records and handles them at once, each still going through every rule.
- **Board by state and list export.** The list of records with a process can be seen as a board with one column per state, and exported to a CSV file a spreadsheet can read, with exactly the rows the viewer may see.
- **A data catalog.** One page shows where the data of each model lives, who may see it, how the kinds of records relate, where the data comes in from and where it goes out.
- **Archive of old records.** Records closed long ago are written to an archive store on a daily schedule, the data in use stays as it is, and the copy can be checked again.
- **An inbox that updates itself.** When a record changes, the inbox, the lists and the detail page that are open refresh without reloading the page.
- **Try it at once.** One command creates a trial account for each role and sample records in several states, so a visitor who signs in finds a system with work in it.
- **Simple to install and run.** One command installs and one starts; backups can be checked, services restart on their own, and attachments are virus-scanned when enabled.
- **Single sign-on.** Through Keycloak, and administrators can be required to enter a one-time code as well.

Each feature and the test that proves it are matched in the [evidence matrix](https://haui-implsolution.github.io/DX-OS/docs/evidence-matrix) (Vietnamese).

## How it works

1. **Declare.** Write or pick a model file: the entities, fields, roles, permissions and workflows of the business.
2. **Apply.** Preview the plan, then apply it; the core builds the database, the roles in Keycloak, the API and the workflows, and records each step.
3. **Use.** People sign in and work in a web interface built from the same file; every change leaves history and is reported outside.

The system has three tiers:

| Tier | Role |
|---|---|
| Core | A headless service: reads models, applies them, the common API, workflows, history, events |
| Apps | A web interface and a control panel built from the data of the core, plus a links page (portal) and a monitoring screen |
| Models | The files that describe a business; adding a business is adding a file |

The diagrams and the path of one request through the system are in [`ARCHITECTURE.md`](./ARCHITECTURE.md) (Vietnamese); how to write a model file is in [`docs/MODEL.md`](./docs/MODEL.md) (Vietnamese).

Ten templates are ready to pick in the control panel: student one-stop service, technical support, equipment incident, room booking, IT service access, equipment loan, notes, an asset register, clinic appointments and retail returns.

## Try it

### Try it online

Version `2.0.0` runs publicly at https://khuongdev.io.vn/ with the One-stop student model and 40 sample requests.
Sign in with one of the four trial accounts below, all with the password `15c9777835f0dfcf4e8d`.

| Account | Role |
|---|---|
| `judge_student` | Student, files requests |
| `judge_officer` | Officer of the unit DAO_TAO |
| `judge_officer2` | Officer of the unit CTSV |
| `judge_supervisor` | Supervisor, reads every request |

The control panel `/console` and the monitoring page are for administrators, so these accounts cannot open them.
Send feedback or report a problem by [opening an issue](https://github.com/HaUI-ImplSolution/DX-OS/issues/new/choose).

### Install

You need a Linux machine with Docker and Compose v2; no source code.

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

The installer downloads the latest release, checks its SHA-256 and installs it into `~/.dxos`; the `dxos` command goes to `~/.local/bin`.
`dxos start` downloads the images the first time, waits until every service is ready, creates the account `admin` with a temporary password printed on screen, and prints the addresses.
Sign in at `http://localhost:9080/console` as `admin`, change the password, pick a template on the Template library tab, preview the plan and apply it.
If you already have a model file, `dxos start <file>` takes it in at once; list every file after `dxos start` when there are several.
If you only need the API platform for outside applications, run `dxos start --core`: no web application, portal or monitoring, see [Core-only mode](./deploy/OPERATIONS.md#chế-độ-chỉ-lõi) (Vietnamese).
The other commands and the operation guide are in [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md) (Vietnamese).

### From source

You also need JDK 21 and Node 22 to build and test outside Docker.

```bash
deploy/make_env.sh                       # writes deploy/.env with random passwords
cd deploy && docker compose up -d --build
./dxos admin                             # creates the admin account with a temporary password
```

Users come in through the gateway at `http://localhost:9080`:

| Address | Content |
|---|---|
| `/` | Web interface |
| `/console` | Control panel for the person deploying |
| `/portal/` | A page listing the addresses and the state of the services |
| `/monitor/` | Monitoring and tracing (ADMIN) |
| `/v3/api-docs` | OpenAPI specification of the backend |

The Keycloak administration console (`/admin`) is not exposed by default; set `KEYCLOAK_ADMIN_CONSOLE=true` in `deploy/.env` and recreate the gateway to open it (account in `deploy/.env`).

The full testing guide is in [`docs/TESTING.md`](./docs/TESTING.md) (Vietnamese).

## Documentation

Docs site: <https://haui-implsolution.github.io/DX-OS/>.
The documents are written in Vietnamese; the API reference is the OpenAPI specification of the running backend.

| You need | See |
|---|---|
| Writing a model file | [`docs/MODEL.md`](./docs/MODEL.md) |
| Calling the API | [`docs/API.md`](./docs/API.md) and the OpenAPI specification (`/v3/api-docs`) of the running backend |
| Building extension services, webhooks, and custom UIs | [`docs/EXTENSIONS.md`](./docs/EXTENSIONS.md) (Vietnamese) |
| Architecture | [`ARCHITECTURE.md`](./ARCHITECTURE.md) |
| Install, the `dxos` commands, backup | [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md) |
| Monitoring, logs, access audit | [`deploy/MONITORING.md`](./deploy/MONITORING.md) |
| Webhooks, tamper-evident history, long-term archive | [`deploy/INTEGRATIONS.md`](./deploy/INTEGRATIONS.md) |
| Connecting a reporting tool | [`deploy/REPORTING.md`](./deploy/REPORTING.md) (Vietnamese) |
| Testing | [`docs/TESTING.md`](./docs/TESTING.md), [`e2e/README.md`](./e2e/README.md) |
| The 15-minute demonstration script | [`docs/DEMO.md`](./docs/DEMO.md) |
| Design and remaining work | [`docs/dynamic-core/README.md`](./docs/dynamic-core/README.md), [`BACKLOG.md`](./docs/dynamic-core/BACKLOG.md) |
| Changes by version | [`CHANGELOG.md`](./CHANGELOG.md) |
| Third-party components and licenses | [`DEPENDENCIES.txt`](./DEPENDENCIES.txt), [`NOTICE`](./NOTICE), [`sbom/`](./sbom/README.md) |

## Repository structure

| Folder | Content |
|---|---|
| `backend/dx-model` | The model file format: reading and checking |
| `backend/dx-core` | Shared library: security, audit, storage, state, Keycloak client |
| `backend/dx-engine` | The core service: applying models, the common API, Flowable |
| `backend/dx-monitor` | The monitoring and tracing screen |
| `frontend` | A react-admin interface built from metadata, in Vietnamese and English |
| `deploy` | Compose, the APISIX gateway, the installer and the `dxos` command, operation scripts, check scripts in `deploy/dev` |
| `sso`, `portal`, `seaweedfs` | The Keycloak realm, the portal page, file storage |
| `models` | Invalid model files for automatic checks; usable sample models are in `backend/dx-engine/src/main/resources/templates/` |
| `examples` | Sample programs: a receiver that keeps history hashes ([`history-witness`](./examples/history-witness/README.md)), an IT/OT system simulator ([`itot-simulator`](./examples/itot-simulator/README.md)) |
| `e2e` | Browser tests of the web interface (Playwright) |
| `website` | The docs site (Docusaurus), published on GitHub Pages |
| `docs` | The testing guide, the model file and the API; the design is in `dynamic-core/` |
| `sbom` | CycloneDX component lists of the backend, the web interface and the docs site |
| `LICENSES` | The full text of the licenses of the repository, as the REUSE specification asks |
| `.github` | CI workflows, issue and pull request templates, branch rules, check scripts |

## Contributing

- **Report a bug:** [open a bug report](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=bug_report.yml).
- **Suggest a feature:** [open a feature request](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=feature_request.yml).
- **Send code:** read [`CONTRIBUTING.md`](./CONTRIBUTING.md) and follow [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md).
- **Security vulnerability:** do not post it in public, follow [`SECURITY.md`](./SECURITY.md).

## Team

| Name | Contact |
|---|---|
| Phạm Minh Khương | pkhuong535@gmail.com |
| Nguyễn Đình Duy Khánh | dinhkhanh19042006@gmail.com |
| Trần Quang Tùng | tung51652@gmail.com |

## License

Apache License 2.0, see [`LICENSE`](./LICENSE).
The license of each third-party component is in [`DEPENDENCIES.txt`](./DEPENDENCIES.txt).

References: [contest topic](https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html) · [DX-OS textbook](https://opendigitransform.gitbook.io/dx-os)
