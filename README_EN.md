# DX-OS: DX-Farm and DX-Lab (Open Source Software 2026 Contest Entry)

[Tiếng Việt](./README.md) · **English**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![AI License](https://img.shields.io/badge/ai%2F-AGPL--3.0-orange.svg)](./ai/LICENSE)

**Team:** HaUI-ImplSolution · **School:** School of Information and Communication Technology, Hanoi University of Industry

A shared open-source platform for digital transformation, built for the **OLP Open Source Software 2026** contest (DX-OS track, VFOSSA).
One shared core handles sign-in, data, workflows, notifications and AI; two business applications are built on top of it.

## 💡 Overview

| Application | Problem | Main flow |
|---|---|---|
| **DX-Farm** | Digital transformation for a rice-growing agricultural cooperative in the Mekong Delta | Farmers photograph and report plant disease, AI diagnoses it, engineers approve and advise treatment, spraying is recorded, and harvest is blocked until the waiting period ends |
| **DX-Lab** | An IT operations desk for a university faculty | Students report incidents, technicians resolve them on time, equipment loans and computer-lab bookings are approved, regulations Q&A, admins view statistics |

## ✨ Highlights

- **One core, many applications:** Farm and Lab share one core; adding an application needs no change to the core.
- **Business safety:** key rules are checked in the Backend and the database, not just in the UI.
- **Traceability:** the gateway logs every request, Keycloak records sign-in events, the Backend writes refused requests, 5xx errors and administrative actions to an append-only audit log, and the administrators-only monitoring screen at `/monitor/` brings these together with service probes and chat alerts.
- **Backup and restore:** `deploy/backup.sh` makes encrypted backups of the database and files, has a restore check, and CI rehearses it on every run.
- **Controlled AI:** AI only assists; drug and dose information comes from expert-approved data and the engineer makes the decision.
- **One-command run:** builds the whole system with demo data, with automated tests on a clean environment.
- **Transparent open source:** licenses and the list of software components are public, with automatic scanning for leaked passwords.

## 🗺️ Overall architecture

![Overall architecture diagram](docs/images/tong_quan.gif)

Users, on web or mobile, reach the internal network only through a single Gateway (APISIX).
The Backend handles the business logic, stores photos in MinIO and data in PostgreSQL, asks the AI Service to diagnose and answer questions, and sends notifications to Mattermost; Superset reads the data for reports.
A separate monitoring service watches unusual traffic and the state of each service, and reports incidents to the administrators through Mattermost.
The role of each service is in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## 🧱 Three-tier architecture

![Three-tier architecture diagram](docs/images/so_do_3_tang.png)

| Tier | Components |
|---|---|
| 1. Open-Core (no end-user UI) | Identity (SSO), Workflow, Notification, Storage, AI and Knowledge, Gateway, Security |
| 2. H-P-D-I | Four spaces (Human, Process, Data, Intelligence) built from the Core modules, plus AI, reports and chat |
| 3. Applications | Farm and Lab |

| Space | Farm | Lab |
|---|---|---|
| **H** Human | Household accounts; roles farmer, engineer, admin | Class and department accounts; roles student, lecturer, technician, admin |
| **P** Process | Approval of parcels, seasons, diagnoses and spraying; harvest blocked until the waiting period ends | Support requests with deadlines, approved equipment loans, double-booking-proof room reservations |
| **D** Data | Database, image storage, summary reports, weather | Database, image storage, deadline and room-usage statistics |
| **I** Intelligence | Disease recognition from photos, document Q&A | Explainable classification suggestions, regulations Q&A |

Every application goes through a single Gateway that checks sign-in, and the Backend checks it again.
Full diagrams and request flows are in [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## 🧩 Features

**DX-Farm:** households and parcels with locations, crop seasons, disease reports with image quality checks, AI diagnosis with engineer approval, spraying records, harvest blocked until the waiting period ends, reminders, notification inbox, chat notifications that link straight to the report, per-parcel weather, document Q&A with citations, summary reports; a mobile app for farmers that works offline.

**DX-Lab:** support requests with deadlines and internal comments, type and priority suggestions, equipment loans with repair log, room booking without time conflicts, regulations Q&A quoted from the university's official documents, with each source opening the original page, statistics.

Feature maturity and test coverage are in [`docs/TESTING.md`](./docs/TESTING.md); outstanding work is in [`docs/BACKLOG.md`](./docs/BACKLOG.md).

## 🧰 Technology and licenses

| Component | Technology | License |
|---|---|---|
| Backend | Java 21, Spring Boot, Flyway | Apache-2.0 |
| AI Service | Python, FastAPI, LangGraph, pgvector, Ollama (Qwen2.5-1.5B) | MIT, Apache-2.0 |
| CV | EfficientNetV2-M (Torchvision) and ONNX crop classifier | BSD-3-Clause, Apache-2.0, AGPL-3.0 |
| Web, Mobile | React, Vite, Expo | MIT |
| SSO, Gateway | Keycloak, Apache APISIX | Apache-2.0 |
| Data, BI | PostgreSQL, MinIO, Apache Superset | PostgreSQL License, AGPL-3.0, Apache-2.0 |
| Chat | Mattermost Team Edition (unmodified) | AGPL-3.0 |

Original code is Apache-2.0 ([`LICENSE`](./LICENSE)); source code in `ai/` remains AGPL-3.0 ([`ai/LICENSE`](./ai/LICENSE)).
Full dependency and license table: [`LICENSES.md`](./LICENSES.md); CycloneDX SBOMs: [`sbom/`](./sbom/README.md).

## 🚀 Install and run

### 💻 System requirements

| Item | Minimum | Notes |
|---|---|---|
| Software | Docker and Docker Compose v2, Git | No JDK, Node or Python needed to run (only `pillow` for the demo script) |
| RAM | 8 GB | 16 GB recommended when building and running the LLM together |
| CPU | 4 vCPU | No GPU needed |
| Disk | 40 GB free | AI image about 3 GB, Ollama about 9 GB plus the Qwen2.5 model |
| Ports | `9080`, `9081`, `9082`, `8065` | Change with the `*_HOST_PORT` variables |

### 📝 Steps

```bash
git clone https://github.com/HaUI-ImplSolution/DX-OS.git && cd DX-OS
python3 deploy/demo/make_ci_env.py      # writes deploy/.env, empty variables get random passwords
python3 -m pip install pillow           # for the demo data script
./deploy/start.sh                       # build, run the app in DXOS_APP, load demo data
```

The first build downloads and builds a lot (MinIO is built from Go source, about 2 minutes), and Superset needs a few more minutes to initialise its metadata.
The command waits until the services are ready; loading the Farm demo data waits up to 5 minutes for the AI service.
Switch applications with one command; the web app follows without a rebuild:

```bash
./deploy/start.sh farm                  # run Farm, load Farm demo data
./deploy/start.sh lab                   # switch to Lab, load Lab demo data (about 1 minute)
./deploy/start.sh farm --reset --yes    # wipe deploy/data and rebuild from scratch
docker compose down                     # stop, keep data in deploy/data (run inside deploy/)
```

Re-running `start.sh` does not create duplicate data.
Load demo data separately; see [`deploy/demo/README.md`](./deploy/demo/README.md) for details.

### 🔧 Configuration

All configuration lives in `deploy/.env` (template: [`deploy/.env.example`](./deploy/.env.example)); Compose stops and names any missing variable.

| Variable | Meaning |
|---|---|
| `DXOS_APP` | Application to run: `farm` (default) or `lab` |
| `POSTGRES_*`, `MINIO_*`, `KEYCLOAK_*`, `SUPERSET_*`, `MATTERMOST_*` | Per-service accounts and passwords, empty in the template, never committed |
| `BACKEND_TO_AI_API_KEY`, `AI_TO_BACKEND_API_KEY` | Service keys for each direction between Backend and AI |
| `PUBLIC_HOST` | Address the browser uses to reach the system: `localhost` for development, the IP or domain of the server when deployed; `GATEWAY_PUBLIC_URL`, `SUPERSET_PUBLIC_URL` and the links in chat notifications follow it |
| `MATTERMOST_WEBHOOK_URL_*` | Overrides the webhook of a chat channel; empty uses the webhook created when the stack starts |
| `OLLAMA_MODEL` | LLM model, default `qwen2.5:1.5b` |

Per-application settings: [`backend/app-farm/README.md`](./backend/app-farm/README.md) and [`backend/app-lab/README.md`](./backend/app-lab/README.md).

## 🌐 Try it

| Service | Address | Login |
|---|---|---|
| Web application | http://localhost:9080 | Keycloak SSO, accounts below |
| Portal (service status) | http://localhost:9080/portal/ | None |
| Monitoring and tracing | http://localhost:9080/monitor/ | Keycloak SSO, ADMIN role only (`admin1`) |
| Swagger | http://localhost:9080/swagger-ui.html | Keycloak access token, click Authorize |
| Keycloak Admin | http://localhost:9080/admin | `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` |
| BI (Superset) | http://localhost:9081 | Keycloak SSO; `admin1` for the admin area |
| Image storage admin (MinIO) | http://localhost:9082 | `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` |
| Chat (Mattermost) | http://localhost:8065 | `MATTERMOST_ADMIN_USERNAME` / `MATTERMOST_ADMIN_PASSWORD` |

### 🌐 Public server

A public server runs behind a Cloudflare Tunnel and opens no inbound port.
Enable it with the overlay [`deploy/docker-compose.cloudflared.yml`](./deploy/docker-compose.cloudflared.yml) (instructions at the top of the file).
Cloudflare concepts and settings are in [`docs/CLOUDFLARE.md`](./docs/CLOUDFLARE.md); logs, rate limits, the audit log, backup and restore are in [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md).

### 🔑 Demo accounts

`seed_demo.py` creates these accounts in the `dxos` realm for demo and judging only; change or delete the passwords before using real data.

| Application | Username | Password | Role |
|---|---|---|---|
| Farm | `farmer1` | `farmer123` | FARMER (mobile app only) |
| Farm | `engineer1` | `engineer123` | ENGINEER |
| Farm, Lab | `admin1` | `admin123` | ADMIN |
| Lab | `student1`, `student2` | `student123` | STUDENT |
| Lab | `lecturer1` | `lecturer123` | LECTURER |
| Lab | `tech1`, `tech2` | `tech1234` | TECHNICIAN |

## 🔌 API

- Swagger UI is at `/swagger-ui.html` and the OpenAPI JSON at `/v3/api-docs`; the documentation is generated from springdoc annotations and covers only the running application (`DXOS_APP`).
- Every call goes through the gateway `http://localhost:9080/api/v1/...` with `Authorization: Bearer <Keycloak access token>`; only `GET /api/v1/system/status` and the application's public catalogues need no token.
- Responses are wrapped in `ApiResponse`; errors carry a stable `errorCode`, business errors return `422` and concurrent conflicts return `409`.
- To try it: click **Authorize** in Swagger with an access token, or use `deploy/demo/dxos_client.py` (Authorization Code + PKCE sign-in, like the real apps).

| Group | Main paths |
|---|---|
| Shared Core | `/api/v1/system/status`, `/api/v1/app` (manifest: roles, menu), `/api/v1/workspaces`, `/api/v1/workflow-cases` (`assign`, `approve`, `reject`, `events`), `/api/v1/notifications`, `/api/v1/audit-logs` (ADMIN) |
| Farm | `/api/v1/households`, `/api/v1/parcels`, `/api/v1/crop-seasons`, `/api/v1/disease-reports`, `/api/v1/treatment-rules`, `/api/v1/knowledge/ask`; see [`backend/app-farm/README.md`](./backend/app-farm/README.md#api) (Vietnamese) |
| Lab | `/api/v1/tickets`, `/api/v1/device-loans`, `/api/v1/room-bookings`, `/api/v1/lab-stats/summary`, `/api/v1/knowledge/ask`; see [`backend/app-lab/README.md`](./backend/app-lab/README.md#api) (Vietnamese) |
| AI Service | Only the Backend can call it on the internal network; it has no gateway route; see [`ai/README.md`](./ai/README.md#api) (Vietnamese) |

## 🧪 Testing

- Backend: `./mvnw verify` in `backend` (Docker required for Testcontainers); AI: `pytest` in `ai/`.
- Web and mobile: `npx tsc --noEmit`.
- End-to-end on a clean stack: `python3 deploy/demo/e2e.py [--app farm|lab]`; CI runs the `E2E` workflow for both applications.
- RAG retrieval evaluation and retrieval load test: `./ai/evaluate.sh quality|performance|all`, run separately from the Farm/Lab stack; see [`ai/README.md`](./ai/README.md#đánh-giá-truy-xuất-rag) (Vietnamese).

Full testing guide: [`docs/TESTING.md`](./docs/TESTING.md).

## 🐛 Troubleshooting

| Symptom | Fix |
|---|---|
| Compose reports a missing variable | Fill in the named variable in `deploy/.env` |
| Web or API returns 401 after a rebuild | Sign out and sign in again (the browser kept the token from the previous build) |
| Web shows the old app after switching | Hard-reload and wait until `/api/v1/system/status` reports the right app |
| Q&A returns nothing | Check `docker compose logs rag-ingest`; the knowledge ingest job has not finished or failed |
| Out of disk space while building | `docker system df`, then prune unused images and cache |

## 📁 Repository layout

| Folder | Contents |
|---|---|
| [`backend/`](./backend/README.md) | `dx-core-api`, `dx-core`, `app-farm`, `app-lab`, `dx-host`, `dx-monitor` |
| [`frontend/`](./frontend/README.md) · [`mobile/`](./mobile/README.md) | React web (both applications) · Expo mobile for farmers |
| [`ai/`](./ai/README.md) | AI Service: CV, RAG, LangGraph |
| [`data/`](./data/README.md) | Structured data, RAG corpora, RAG evaluation set, seed generation scripts |
| [`deploy/`](./deploy) | Docker Compose, gateway, `start.sh`, demo data and E2E; access logs in [`OPERATIONS.md`](./deploy/OPERATIONS.md) |
| [`sso/`](./sso/README.md) · [`bi/`](./bi/README.md) · [`chat/`](./chat/README.md) · [`minio/`](./minio/README.md) · [`portal/`](./portal/README.md) | Keycloak, Superset, Mattermost, MinIO, portal |
| [`docs/`](./docs) | Backlog, testing, AI evaluation, submission pack, council Q&A, phase template (`_template/`), reference material |

Top-level documents: [`ARCHITECTURE.md`](./ARCHITECTURE.md) · [`CHANGELOG.md`](./CHANGELOG.md) · [`LICENSES.md`](./LICENSES.md).

## 🤝 Contributing

- **Report a bug:** [open a bug report](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=bug_report.md).
- **Suggest a feature:** [open a feature request](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=feature_request.md).
- **Submit code:** read [`CONTRIBUTING.md`](./CONTRIBUTING.md) and follow [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md).

## 📞 Contact

| Role | Name | Contact |
|---|---|---|
| Developer | Phạm Minh Khương | pkhuong535@gmail.com |
| Developer | Nguyễn Đình Duy Khánh | dinhkhanh19042006@gmail.com |
| Developer | Trần Quang Tùng | tung51652@gmail.com |

## 📜 License

Apache License 2.0, see [`LICENSE`](./LICENSE); the `ai/` directory is AGPL-3.0, see [`ai/LICENSE`](./ai/LICENSE) and [`LICENSES.md`](./LICENSES.md).

References: [contest topic](https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html) · [DX-OS textbook](https://opendigitransform.gitbook.io/dx-os)
