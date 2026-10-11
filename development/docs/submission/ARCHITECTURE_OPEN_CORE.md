# Kiến trúc Open-Core 3 tầng

Tài liệu này mô tả ranh giới giữa phần lõi dùng chung (Core) và ứng dụng DX-Farm, kèm sơ đồ 3 tầng và sơ đồ luồng request.
Sơ đồ viết bằng Mermaid, GitHub hiển thị trực tiếp.

## 1. Sơ đồ 3 tầng

```mermaid
flowchart TB
  subgraph T3["Tầng 3: ứng dụng DX-Farm (Agri-Carbon)"]
    direction LR
    APP["Parcel, CropSeason, DiseaseReport, Diagnosis,<br/>Disease, Pesticide, TreatmentRule<br/>AI Service (CV, RAG, LangGraph) · web · mobile · Superset · Mattermost"]
  end

  subgraph T2["Tầng 2: bốn không gian H-P-D-I"]
    direction LR
    H["<b>H</b> Human<br/>Keycloak, Workspace,<br/>ma trận quyền"]
    P["<b>P</b> Process<br/>workflow_case,<br/>workflow_event"]
    D["<b>D</b> Data<br/>PostgreSQL, MinIO,<br/>Superset"]
    I["<b>I</b> Intelligence<br/>AI Service"]
  end

  subgraph T1["Tầng 1: Core dùng chung"]
    direction LR
    ID["<b>core.identity</b><br/>Workspace, WorkspaceMember,<br/>KeycloakAdminClient"]
    WF["<b>core.workflow</b><br/>WorkflowCase, WorkflowEvent,<br/>WorkflowService, ApprovalInbox"]
    ST["<b>core.storage</b><br/>StorageService (S3, link có hạn)"]
    NT["<b>core.notification</b><br/>Notification, kênh chat"]
    GW["<b>Gateway</b><br/>APISIX standalone"]
  end

  T3 --> T2 --> T1
```

| Tầng | Thành phần | Nơi nằm trong repo |
|---|---|---|
| 1. Core | Identity | `backend/dx-core/.../core/identity`, `sso/` (Keycloak) |
| 1. Core | Workflow | `backend/dx-core/.../core/workflow`, bảng `workflow_case`, `workflow_event` |
| 1. Core | Storage | `backend/dx-core/.../core/storage`, `minio/` |
| 1. Core | Notification | `backend/dx-core/.../core/notification`, bảng `notifications` |
| 1. Core | AI gateway, tri thức | `backend/dx-core/.../core/ai`, `.../core/knowledge` |
| 1. Core | Gateway | `deploy/apisix/` |
| 2. H-P-D-I | Human, Process, Data, Intelligence | các thành phần Core ở trên cộng `ai/`, `bi/`, `chat/` |
| 3. Ứng dụng | DX-Farm | `backend/app-farm`, `frontend/`, `mobile/`, `ai/`, `data/` |

## 2. Ranh giới Core và ứng dụng

- Backend gồm bốn module Maven: `dx-core-api` (interface, DTO, lỗi), `dx-core` (cài đặt dùng chung), `app-farm` (nghiệp vụ nông nghiệp) và `dx-host` (chạy được, chọn app bằng `DXOS_APP`).
- `dx-core` không import gì từ nghiệp vụ nông nghiệp; nó chỉ phụ thuộc `dx-core-api`.
  `WorkflowService` nhận `caseType`, `entityType`, `entityId` dạng chuỗi và số, không biết `Diagnosis` là gì; trạng thái và bước chuyển của mỗi loại case do app khai báo bằng `CaseTypeDefinition`.
  `StorageService` nhận khóa object và byte, không biết đó là ảnh báo cáo.
  Vai, endpoint công khai, loại workspace, loại thông báo, kênh chat, view BI và nguồn tri thức do app khai báo qua interface; core chỉ gom các bean đó lúc khởi động.
- `app-farm` chỉ có `dx-core-api` trên classpath và gọi Core qua các facade của module này:
  - `DiagnosisService` mở case `DIAGNOSIS_APPROVAL` và ghi bước duyệt qua `WorkflowOperations`.
  - `DiagnosisService` mở case `TREATMENT_CONFIRMATION` khi nông dân khai phun thuốc và ghi bước xác nhận của kỹ sư.
  - `FileStorageService` lưu ảnh qua `FileStore`; `DiseaseImageProcessor` làm sạch ảnh trước khi lưu.
  - `ParcelService` tra nông hộ qua `WorkspaceDirectory` và giữ `household_id` dạng số.
  - Hộp duyệt chạy ở core (`ApprovalInbox`); Farm mô tả từng loại entity qua `CaseSummaryProvider`.
  - Thông báo ghi qua `NotificationPublisher`, hỏi đáp tri thức qua `KnowledgeGateway`, gọi AI qua `AiGateway`.
- Đổi ứng dụng (ví dụ làm ứng dụng thứ hai) chỉ thêm module ứng dụng mới.
  `dx-core-api`, `dx-core` và cấu hình `deploy/apisix/` giữ nguyên.
  Ứng dụng mới khai báo `caseType` riêng, ví dụ `PARCEL_APPROVAL`, và dùng lại bảng `workflow_case`, `workflow_event` và API `/api/v1/workflow-cases`.
  `GET /api/v1/app` trả id, tên, vai và menu của app đang chạy cho client.
- Ranh giới là ràng buộc lúc biên dịch: compiler từ chối mọi import từ `app-farm` vào ruột `dx-core`.

## 3. Sơ đồ luồng request

```mermaid
sequenceDiagram
  autonumber
  actor U as Người dùng (web hoặc mobile)
  participant G as APISIX :9080
  participant K as Keycloak
  participant B as Backend
  participant DB as PostgreSQL
  participant S as MinIO
  participant AI as AI Service

  U->>G: GET /realms/dxfarm/... (đăng nhập OIDC)
  G->>K: chuyển tiếp
  K-->>U: access token (aud dxfarm-api)

  U->>G: POST /api/v1/crop-seasons/{id}/disease-reports (Bearer token, ảnh)
  G->>G: kiểm JWT, giới hạn tốc độ
  G->>B: chuyển tiếp
  B->>B: kiểm JWT lần hai, quyền theo vai trò
  B->>S: lưu ảnh đã xóa EXIF (nội bộ)
  B->>DB: ghi báo cáo
  B-->>U: báo cáo kèm imageUrl (link có hạn)

  U->>G: GET /dxfarm-uploads/{key}?X-Amz-... (link có hạn)
  G->>S: chuyển tiếp nguyên đường dẫn
  S-->>U: ảnh

  Note over B,AI: Kỹ sư bấm chẩn đoán
  B->>AI: image_url (link nội bộ có hạn)
  AI->>S: tải ảnh
  AI-->>B: kết quả
  B->>DB: ghi Diagnosis, mở workflow_case, ghi workflow_event
```

- Mọi client chỉ biết một địa chỉ: gateway.
- Cổng của backend, Keycloak, AI, MinIO, Superset và portal không publish ra host.
  Gateway có hai listener: `9080` cho web, API, Keycloak, ảnh và portal (`/portal/`), `9081` riêng cho Superset (bản 3.1 không chạy được dưới đường dẫn con), `9082` riêng cho console MinIO.
- Backend kiểm lại JWT độc lập với gateway.
  Gateway sai cấu hình thì backend vẫn tự bảo vệ.
- AI Service không có route ở gateway, chỉ backend gọi được qua mạng nội bộ của compose.
