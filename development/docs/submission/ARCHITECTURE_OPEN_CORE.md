# Kiến trúc Open-Core 3 tầng

Tài liệu này mô tả ranh giới giữa phần lõi dùng chung (Core) và hai ứng dụng chạy trên nó: DX-Farm (nông nghiệp) và DX-Lab (vận hành CNTT của khoa).
Sơ đồ viết bằng Mermaid, GitHub hiển thị trực tiếp.
Danh sách interface ở [`SPI_CATALOG.md`](../refactor_docs/SPI_CATALOG.md); danh sách file theo module ở [`MODULE_MAP.md`](../refactor_docs/MODULE_MAP.md).

## 1. Sơ đồ 3 tầng

```mermaid
flowchart TB
  subgraph T3["Tầng 3: ứng dụng nghiệp vụ (mỗi lần chạy một app, chọn bằng DXOS_APP)"]
    direction LR
    FARM["<b>DX-Farm</b><br/>Thửa, vụ mùa, báo cáo sâu bệnh,<br/>chẩn đoán, khai phun thuốc, khóa thu hoạch<br/>web kỹ sư và admin · mobile nông dân"]
    LAB["<b>DX-Lab</b><br/>Yêu cầu hỗ trợ có SLA, thiết bị và mượn trả,<br/>đặt phòng máy, tri thức và hỏi đáp, thống kê<br/>web sinh viên, giảng viên, kỹ thuật viên, quản trị"]
  end

  subgraph T2["Tầng 2: bốn không gian H-P-D-I"]
    direction LR
    H["<b>H</b> Human<br/>Keycloak, Workspace,<br/>vai và phạm vi dữ liệu"]
    P["<b>P</b> Process<br/>workflow_case,<br/>workflow_event, hộp duyệt"]
    D["<b>D</b> Data<br/>PostgreSQL, MinIO,<br/>view BI, Superset"]
    I["<b>I</b> Intelligence<br/>CV, RAG, LangGraph,<br/>luật phân loại"]
  end

  subgraph T1["Tầng 1: Open-Core dùng chung (headless)"]
    direction LR
    ID["<b>core.identity</b><br/>Workspace, WorkspaceMember,<br/>KeycloakAdminClient"]
    WF["<b>core.workflow</b><br/>loại case cấu hình được,<br/>lịch sử chỉ thêm"]
    ST["<b>core.storage</b><br/>S3, link có hạn,<br/>FileProcessor"]
    NT["<b>core.notification</b><br/>thông báo, kênh chat"]
    AIG["<b>core.ai, core.knowledge</b><br/>cổng AI, RAG theo namespace"]
    GW["<b>Gateway</b><br/>APISIX: JWT, giới hạn tốc độ"]
  end

  T3 --> T2 --> T1
```

| Tầng | Thành phần | Nơi nằm trong repo |
|---|---|---|
| 1. Core | Identity, SSO | `backend/dx-core/.../core/identity`, `sso/` (Keycloak) |
| 1. Core | Workflow | `backend/dx-core/.../core/workflow`, bảng `workflow_case`, `workflow_event` |
| 1. Core | Storage | `backend/dx-core/.../core/storage`, `minio/` |
| 1. Core | Notification | `backend/dx-core/.../core/notification`, bảng `notifications` |
| 1. Core | Cổng AI, tri thức | `backend/dx-core/.../core/ai`, `.../core/knowledge` |
| 1. Core | Bảo mật, trạng thái hệ thống | `backend/dx-core/.../core/security`, `.../core/status` |
| 1. Core | API Gateway | `deploy/apisix/common.yaml` |
| 2. H-P-D-I | Human, Process, Data, Intelligence | các thành phần Core ở trên cộng `ai/`, `bi/`, `chat/` |
| 3. Ứng dụng | DX-Farm | `backend/app-farm`, `frontend/src/apps/farm`, `mobile/`, `data/` |
| 3. Ứng dụng | DX-Lab | `backend/app-lab`, `frontend/src/apps/lab`, `data/rag/lab/` |

## 2. Module và ranh giới biên dịch

```mermaid
flowchart BT
  api["<b>dx-core-api</b><br/>interface, DTO, lỗi"]
  core["<b>dx-core</b><br/>cài đặt dùng chung"]
  farm["<b>app-farm</b>"]
  lab["<b>app-lab</b>"]
  host["<b>dx-host</b><br/>chạy được, chọn app bằng DXOS_APP"]

  core --> api
  farm --> api
  lab --> api
  host --> core
  host --> farm
  host --> lab
```

- Backend gồm năm module Maven.
  `dx-core-api` giữ interface, DTO và lỗi; `dx-core` cài đặt các dịch vụ dùng chung; `app-farm` và `app-lab` là hai ứng dụng; `dx-host` ghép chúng và là fat jar duy nhất được đóng gói.
- Mỗi ứng dụng chỉ có `dx-core-api` trên classpath.
  Compiler từ chối mọi import từ ứng dụng vào ruột `dx-core`, và `dx-core` không import ứng dụng nào.
- Core không biết ứng dụng nào đang chạy:
  - `WorkflowService` nhận `caseType`, `entityType`, `entityId` dạng chuỗi và số; trạng thái, bước chuyển và vai do app khai báo bằng `CaseTypeDefinition`.
  - `StorageService` nhận khóa object và byte; việc làm sạch tệp do app cài `FileProcessor`.
  - Vai, endpoint công khai, loại workspace, loại thông báo, kênh chat, view BI, nguồn tri thức và chỉ báo sức khỏe do app khai báo qua interface; core chỉ gom các bean đó lúc khởi động.
- Entity của core không lộ ra ứng dụng: app nhận `CaseView` và `WorkspaceView`, và lưu id dạng số thay cho quan hệ JPA xuyên module.
- Ứng dụng gọi core qua facade của `dx-core-api`: `WorkflowOperations`, `ApprovalInbox`, `WorkspaceDirectory`, `NotificationPublisher`, `ObjectStorage`, `FileStore`, `UserContext`, `KnowledgeGateway`, `AiGateway`.
- Đổi ứng dụng chỉ thêm module ứng dụng mới; `dx-core-api`, `dx-core` và `deploy/apisix/common.yaml` giữ nguyên.
  Cách thêm ở [`CONTRIBUTING.md`](../../../CONTRIBUTING.md) mục "Thêm một ứng dụng mới".

### Cách ứng dụng cắm vào

| Việc | Cơ chế | Farm | Lab |
|---|---|---|---|
| Khai báo app | `AppModule` (id, tên, menu, loại workspace) | `FarmAppModule` | `LabAppModule` |
| Vai | `RoleProvider`, core tạo vai thiếu trong Keycloak lúc khởi động | FARMER, ENGINEER, ADMIN | STUDENT, LECTURER, TECHNICIAN, ADMIN |
| Quy trình | `CaseTypeDefinition`, `WorkflowDecisionHandler` | 5 loại: duyệt thửa, sửa thửa, duyệt vụ mùa, duyệt chẩn đoán, xác nhận khai phun | `TICKET`, `DEVICE_LOAN`, `ROOM_BOOKING` |
| Hộp duyệt | `CaseSummaryProvider` | 5 provider | 3 provider |
| Thông báo và chat | `NotificationTypeDefinition`, `NotificationChannel` | kênh `ADMIN`, `ENGINEER` | kênh `ADMIN`, `TECHNICIAN` |
| Dữ liệu BI | `BiDatasetProvider` (view `vw_*`) | 6 view đã duyệt | 5 view `vw_lab_*` |
| Tri thức | `KnowledgeNamespace` | `farm` | `lab` |
| Schema | Flyway location riêng, bảng lịch sử riêng | `db/migration/farm` | `db/migration/lab` |

## 3. Chọn ứng dụng lúc chạy

- `DXOS_APP` (`farm` hoặc `lab`) chọn app; giá trị khác làm backend dừng trước khi tạo bean (`AppSelectionCheck`).
- Mỗi app nạp bằng một cấu hình tự động có điều kiện `dxos.app=<app>`: chỉ app đang chạy có entity, bảng, controller và lịch chạy.
- Lớp ngoài backend đổi theo cùng biến:
  - Gateway: `apisix-config` ghép `common.yaml` với `apps/<app>.yaml`.
  - Superset: kết nối và dataset của app.
  - Tri thức: job `rag-ingest` nạp corpus của app vào namespace riêng.
  - Web: một image, đọc `GET /api/v1/app` rồi nạp module giao diện của app.
- `deploy/start.sh <app>` đổi app bằng một lệnh; bảng của app trước nằm lại trong database, không dùng tới.

## 4. Sơ đồ luồng request: Farm

```mermaid
sequenceDiagram
  autonumber
  actor U as Người dùng (web hoặc mobile)
  participant G as APISIX :9080
  participant K as Keycloak
  participant B as Backend (DXOS_APP=farm)
  participant DB as PostgreSQL
  participant S as MinIO
  participant AI as AI Service

  U->>G: GET /realms/dxos/... (đăng nhập OIDC)
  G->>K: chuyển tiếp
  K-->>U: access token (aud dxos-api)

  U->>G: POST /api/v1/crop-seasons/{id}/disease-reports (Bearer token, ảnh)
  G->>G: kiểm JWT, giới hạn tốc độ
  G->>B: chuyển tiếp
  B->>B: kiểm JWT lần hai, quyền theo vai và nông hộ
  B->>S: lưu ảnh đã xóa EXIF (nội bộ)
  B->>DB: ghi báo cáo
  B-->>U: báo cáo kèm imageUrl (link có hạn)

  U->>G: GET /dxos-uploads/{key}?X-Amz-... (link có hạn)
  G->>S: chuyển tiếp nguyên đường dẫn
  S-->>U: ảnh

  Note over B,AI: Kỹ sư bấm chẩn đoán
  B->>AI: image_url (link nội bộ có hạn)
  AI->>S: tải ảnh
  AI-->>B: kết quả
  B->>DB: ghi Diagnosis, mở workflow_case, ghi workflow_event
```

## 5. Sơ đồ luồng request: Lab

```mermaid
sequenceDiagram
  autonumber
  actor SV as Sinh viên
  actor KTV as Kỹ thuật viên
  participant G as APISIX :9080
  participant B as Backend (DXOS_APP=lab)
  participant DB as PostgreSQL
  participant S as MinIO
  participant C as Mattermost

  SV->>G: POST /api/v1/tickets/suggest (mô tả sự cố)
  G->>B: chuyển tiếp (giới hạn tốc độ)
  B-->>SV: loại, ưu tiên, lý do và bài tri thức liên quan

  SV->>G: POST /api/v1/tickets (ticket + ảnh)
  G->>B: chuyển tiếp (kiểm JWT, giới hạn tốc độ)
  B->>S: lưu tệp đính kèm
  B->>DB: ghi ticket, hạn phản hồi và hạn xử lý theo SLA
  B->>DB: mở workflow_case TICKET, ghi workflow_event
  B-->>C: thông báo kênh kỹ thuật viên (webhook)

  par hai kỹ thuật viên nhận cùng ticket
    KTV->>G: POST /api/v1/tickets/{id}/assign
    G->>B: chuyển tiếp
    B->>DB: cập nhật có khóa phiên bản
  end
  B-->>KTV: một bên 200, bên còn lại 409

  Note over B,DB: LabScheduler kiểm hạn SLA định kỳ, cảnh báo ở 80% và đánh dấu quá hạn
  KTV->>G: resolve (ghi chú và nguyên nhân gốc)
  SV->>G: confirm (đánh giá 1-5)
  B->>DB: ticket CLOSED, workflow_event ghi từng bước
```

## 6. Nguyên tắc vận hành

- Mọi client chỉ biết một địa chỉ: gateway.
  Cổng của backend, Keycloak, AI, MinIO và portal không publish ra host.
  Gateway có ba listener: `9080` cho web, API, Keycloak, ảnh và portal (`/portal/`), `9081` riêng cho Superset (bản 3.1 không chạy được dưới đường dẫn con), `9082` riêng cho console MinIO.
- Gateway chỉ có route và giới hạn tốc độ của app đang chạy.
- Backend kiểm lại JWT độc lập với gateway.
  Gateway sai cấu hình thì backend vẫn tự bảo vệ.
- AI Service không có route ở gateway, chỉ backend gọi được qua mạng nội bộ của compose.
- `GET /api/v1/system/status` (công khai) trả app đang chạy và trạng thái từng dịch vụ; portal đọc từ đây.

## 7. Đối chiếu với yêu cầu của đề

| Yêu cầu (đề mục 3 và 4.1) | Chứng minh |
|---|---|
| Tầng 1 headless, không có giao diện người dùng cuối | `dx-core` chỉ có API; giao diện nằm ở `frontend/` và `mobile/` (tầng 2 và 3) |
| Identity/SSO | Keycloak, realm `dxos`, `core.identity`, vai do app khai báo |
| API Gateway | APISIX: JWT, giới hạn tốc độ, một cửa cho mọi client |
| Dữ liệu cấu trúc và phi cấu trúc | PostgreSQL (nghiệp vụ, pgvector), MinIO (ảnh, tệp đính kèm) |
| Workflow | `core.workflow`: loại case cấu hình được, lịch sử chỉ thêm, hộp duyệt |
| Ít nhất một ứng dụng ở tầng 3 | Hai ứng dụng: DX-Farm và DX-Lab, cùng một Core không đổi |
