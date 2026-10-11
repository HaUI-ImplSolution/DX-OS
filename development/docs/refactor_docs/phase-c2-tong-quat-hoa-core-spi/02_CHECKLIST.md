# Checklist: Phase C2

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC2-{xx}-{lớp}-{nn}`.
Dòng BE và DATA ghi module trong ngoặc vuông.

## WPC2-01: `Workspace` và `WorkspaceMember`

Kết quả đầu ra: không còn `Household` trong `dx-core-api` và `dx-core`.

**Spec và tài liệu**

- [x] WPC2-01-DOC-01 Cập nhật spec và Swagger cho `/api/v1/workspaces` (core) và ghi chú bí danh `/api/v1/households` của Farm.

**Backend**

- [x] WPC2-01-BE-01 [core-api] `WorkspaceView`, `WorkspaceMemberView`, mở rộng `WorkspaceDirectory`.
- [x] WPC2-01-BE-02 [core] Entity `Workspace`, `WorkspaceMember`, repository, service, controller thay `Household*`.
- [x] WPC2-01-BE-03 [core] Mã đơn vị theo `type` và tiền tố do app đăng ký.
- [x] WPC2-01-BE-04 [core] Liên kết tài khoản Keycloak thành thành viên (thay `keycloakUserId`).
- [x] WPC2-01-BE-05 [farm] Đăng ký `type=HOUSEHOLD`, tiền tố `H`; `ParcelService` dùng `WorkspaceDirectory`.
- [x] WPC2-01-BE-06 [farm] Controller bí danh `/api/v1/households` giữ nguyên JSON và quyền cũ, gọi `WorkspaceDirectory`.

**Frontend (web)**

- [-] WPC2-01-FE-01 Web Farm giữ nguyên `/households` (không đổi).
- [-] WPC2-01-FE-02 Nhãn "Nông hộ" giữ nguyên ở web Farm.

**Mobile**

- [-] WPC2-01-MB-01 Mobile không đổi.

**Data, seed, migration**

- [x] WPC2-01-DATA-01 [core] `workspaces`, `workspace_members` thay `households`.
- [x] WPC2-01-DATA-02 [farm] `parcels.household_id` giữ tên, khóa ngoại tới `workspaces`.

**Infra, deploy, CI**

- [-] WPC2-01-INFRA-01 `seed_demo.py`, `e2e.py`, `dxfarm_client.py` giữ nguyên `/households`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC2-01-TEST-01 Tìm `Household` trong `dx-core-api`, `dx-core` (TC2-01).

## WPC2-02: Workflow cấu hình được

Kết quả đầu ra: Farm chạy đúng luật cũ; một định nghĩa thử 5 trạng thái chạy được.

**Spec và tài liệu**

- [x] WPC2-02-DOC-01 Mô tả `CaseTypeDefinition` ở `SPI_CATALOG.md` (trạng thái, bước chuyển, vai, cờ kết thúc).

**Backend**

- [x] WPC2-02-BE-01 [core-api] `CaseTypeDefinition`, `CaseTransition`, `CaseStatus` (chuỗi) (`CaseStatus`, `CaseTransition` và `CaseTypeDefinition` là record; app khai báo bằng bean).
- [x] WPC2-02-BE-02 [core] `WorkflowService` kiểm bước chuyển theo định nghĩa của loại case.
- [x] WPC2-02-BE-03 [core] Định nghĩa `approval` mặc định (PENDING, APPROVED, REJECTED, CANCELLED; REJECTED → PENDING).
- [x] WPC2-02-BE-04 [core] Từ chối loại case chưa đăng ký bằng lỗi rõ.
- [x] WPC2-02-BE-05 [farm] Đăng ký loại case: duyệt thửa, sửa thửa, duyệt vụ, duyệt chẩn đoán, xác nhận phun, dùng định nghĩa `approval`.

**Frontend (web)**

- [x] WPC2-02-FE-01 `types.ts` nhận `status` dạng chuỗi; `labels.ts` vẫn nhãn cho giá trị của Farm.

**Mobile**

- [x] WPC2-02-MB-01 `mobile/src/types.ts`, `reportStatus.ts` nhận trạng thái không biết (hiển thị an toàn).

**Data, seed, migration**

- [x] WPC2-02-DATA-01 [core] Cột `status` dạng chuỗi (đã là `EnumType.STRING`, kiểm tra ràng buộc).

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC2-02-TEST-01 Luồng duyệt Farm không đổi (TC2-05, TC2-06).

## WPC2-03: Hộp duyệt về core

Kết quả đầu ra: hộp duyệt chạy từ core, Farm cài `CaseSummaryProvider`.

**Backend**

- [x] WPC2-03-BE-01 [core-api] `CaseSummaryProvider` (tiêu đề, người gửi, tóm tắt, đường dẫn mở entity).
- [x] WPC2-03-BE-02 [core] Chuyển `ApprovalRequestService`, `ApprovalRequestController`, DTO về core, bỏ repository Farm (service trong core là `ApprovalInboxService`; controller và DTO ở lại Farm vì JSON mang tên trường `householdCode`, `farmerName`).
- [x] WPC2-03-BE-03 [core] `PendingRequestLimiter` đọc loại case tính giới hạn từ cấu hình app (loại case tính giới hạn khai báo ở `AppModule.limitedCaseTypes`).
- [x] WPC2-03-BE-04 [farm] Cài `CaseSummaryProvider` cho thửa, sửa thửa, vụ, chẩn đoán, phun.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC2-03-TEST-01 Danh sách và chi tiết yêu cầu không đổi (TC2-07).

## WPC2-04: Thông báo và kênh chat

Kết quả đầu ra: Farm gửi đủ thông báo; Lab đăng ký loại và kênh riêng không đụng core.

**Backend**

- [x] WPC2-04-BE-01 [core-api] `NotificationPublisher`, `NotificationTypeDefinition`, `NotificationChannel`.
- [x] WPC2-04-BE-02 [core] `Notification`, repository, service, controller chuyển về core; `type` dạng chuỗi.
- [x] WPC2-04-BE-03 [core] `MattermostWebhookClient` nhận khóa kênh dạng chuỗi.
- [x] WPC2-04-BE-04 [farm] Đăng ký toàn bộ loại thông báo cũ và hai kênh ADMIN, ENGINEER; `MattermostMessageBuilder` giữ ở Farm.

**Mobile**

- [x] WPC2-04-MB-01 Màn thông báo hiển thị loại không biết an toàn.

**Data, seed, migration**

- [x] WPC2-04-DATA-01 [core] Bảng `notifications` vào migration core; [farm] xóa khỏi migration Farm.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC2-04-TEST-01 Thông báo duyệt thửa và nhắc thu hoạch vẫn tạo (TC2-08): duyệt thửa, vụ, chẩn đoán, phun thuốc đạt; job nhắc thu hoạch không đổi và chưa kích riêng.

## WPC2-05: Bảo mật, vai và manifest app

Kết quả đầu ra: ma trận quyền Farm không đổi; `GET /api/v1/app` trả manifest.

**Spec và tài liệu**

- [x] WPC2-05-DOC-01 Spec và Swagger cho `GET /api/v1/app`.

**Backend**

- [x] WPC2-05-BE-01 [core-api] `AppModule`, `RoleProvider`, `EndpointPolicyContributor`, `ClientRestrictionPolicy`.
- [x] WPC2-05-BE-02 [core] `SecurityConfig` ghép endpoint công khai và hạn chế client từ interface; bỏ cấu hình riêng của Farm khỏi core.
- [x] WPC2-05-BE-03 [core] `UserContext.hasRole`, `isStaff`; bỏ `isFarmer`, `isAdmin` cứng.
- [x] WPC2-05-BE-04 [core] Tạo vai từ `RoleProvider` lúc khởi động bằng `KeycloakAdminClient`, idempotent.
- [x] WPC2-05-BE-05 [host] `GET /api/v1/app` trả id, tên, vai, menu.
- [x] WPC2-05-BE-06 [farm] Cài `AppModule`, `RoleProvider`, `EndpointPolicyContributor` (diseases, pesticides, treatment-rules), `ClientRestrictionPolicy` (FARMER chỉ dùng mobile).

**Infra, deploy, CI**

- [x] WPC2-05-INFRA-01 `FARMER_CLIENT_IDS` chuyển vào cấu hình Farm trong `application-farm.yaml`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC2-05-TEST-01 Đối chiếu ma trận quyền trước và sau (TC2-09).

## WPC2-06: Cổng AI và tri thức theo namespace

Kết quả đầu ra: hỏi đáp tri thức Farm không đổi; core gọi RAG theo namespace.

**Backend**

- [x] WPC2-06-BE-01 [core-api] `AiGateway`, `KnowledgeGateway`, `KnowledgeNamespace`.
- [x] WPC2-06-BE-02 [core] Tách phần chung của `AiDiagnosisClient`; `KnowledgeService` và controller theo namespace (service trong core là `KnowledgeGatewayService`; controller và DTO ở lại Farm vì request mang `diseaseCode`).
- [x] WPC2-06-BE-03 [farm] Client chẩn đoán (precheck, diagnose, symptoms) dựa trên `AiGateway`; `KnowledgeNamespace` của Farm đọc `knowledge-sources.json`.

**AI**

- [x] WPC2-06-AI-01 `POST /api/v1/disease/knowledge/retrieve` nhận `namespace`, mặc định `farm`.
- [x] WPC2-06-AI-02 Dữ liệu RAG hiện có gắn namespace `farm`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC2-06-TEST-01 Hỏi đáp tri thức trả cùng kết quả (TC2-10).

## WPC2-07: Đăng ký BI, xử lý tệp, health

Kết quả đầu ra: quyền Superset Farm giống trước; thêm view chỉ cần đăng ký ở app.

**Backend**

- [x] WPC2-07-BE-01 [core-api] `BiDatasetProvider`, `FileProcessor`, `HealthContributor`.
- [x] WPC2-07-BE-02 [core] `SupersetReadOnlyRoleSeeder` đọc danh sách từ provider; `SystemStatusService` gom `HealthContributor`.
- [x] WPC2-07-BE-03 [farm] Cài `BiDatasetProvider` (`vw_*`, `diseases`, `pesticides`, `rice_varieties`), `FileProcessor` (xóa EXIF), `HealthContributor` (AI, thời tiết).

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC2-07-TEST-01 Superset đọc đúng view đã đăng ký, không đọc bảng khác (TC2-11).

## Đóng phase

- [x] `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn phase.
- [x] `.env.example`, seed, migration chạy được trên clone sạch với `DXOS_APP=farm`.
- [x] `dx-core-api` và `dx-core` không import gói của app nào; app chỉ import `dx-core-api`.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] Dọn dữ liệu và ảnh test không còn dùng.
- [x] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng (kiểm app giả chuyển sang C4, TC4-31).
- [x] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
