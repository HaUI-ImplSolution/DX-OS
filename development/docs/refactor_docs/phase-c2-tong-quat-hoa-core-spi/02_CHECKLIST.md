# Checklist: Phase C2

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC2-{xx}-{lớp}-{nn}`.
Dòng BE và DATA ghi module trong ngoặc vuông.

## WPC2-01: `Workspace` và `WorkspaceMember`

Kết quả đầu ra: không còn `Household` trong `dx-core-api` và `dx-core`.

**Spec và tài liệu**

- [ ] WPC2-01-DOC-01 Cập nhật spec và Swagger cho `/api/v1/workspaces` (core) và ghi chú bí danh `/api/v1/households` của Farm.

**Backend**

- [ ] WPC2-01-BE-01 [core-api] `WorkspaceView`, `WorkspaceMemberView`, mở rộng `WorkspaceDirectory`.
- [ ] WPC2-01-BE-02 [core] Entity `Workspace`, `WorkspaceMember`, repository, service, controller thay `Household*`.
- [ ] WPC2-01-BE-03 [core] Mã đơn vị theo `type` và tiền tố do app đăng ký.
- [ ] WPC2-01-BE-04 [core] Liên kết tài khoản Keycloak thành thành viên (thay `keycloakUserId`).
- [ ] WPC2-01-BE-05 [farm] Đăng ký `type=HOUSEHOLD`, tiền tố `H`; `ParcelService` dùng `WorkspaceDirectory`.
- [ ] WPC2-01-BE-06 [farm] Controller bí danh `/api/v1/households` giữ nguyên JSON và quyền cũ, gọi `WorkspaceDirectory`.

**Frontend (web)**

- [-] WPC2-01-FE-01 Web Farm giữ nguyên `/households` (không đổi).
- [-] WPC2-01-FE-02 Nhãn "Nông hộ" giữ nguyên ở web Farm.

**Mobile**

- [-] WPC2-01-MB-01 Mobile không đổi.

**Data, seed, migration**

- [ ] WPC2-01-DATA-01 [core] `workspaces`, `workspace_members` thay `households`.
- [ ] WPC2-01-DATA-02 [farm] `parcels.household_id` giữ tên, khóa ngoại tới `workspaces`.

**Infra, deploy, CI**

- [-] WPC2-01-INFRA-01 `seed_demo.py`, `e2e.py`, `dxfarm_client.py` giữ nguyên `/households`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC2-01-TEST-01 Tìm `Household` trong `dx-core-api`, `dx-core` (TC2-01).

## WPC2-02: Workflow cấu hình được

Kết quả đầu ra: Farm chạy đúng luật cũ; một định nghĩa thử 5 trạng thái chạy được.

**Spec và tài liệu**

- [ ] WPC2-02-DOC-01 Mô tả `CaseTypeDefinition` ở `SPI_CATALOG.md` (trạng thái, bước chuyển, vai, cờ kết thúc).

**Backend**

- [ ] WPC2-02-BE-01 [core-api] `CaseTypeDefinition`, `CaseTransition`, `CaseStatus` (chuỗi).
- [ ] WPC2-02-BE-02 [core] `WorkflowService` kiểm bước chuyển theo định nghĩa của loại case.
- [ ] WPC2-02-BE-03 [core] Định nghĩa `approval` mặc định (PENDING, APPROVED, REJECTED, CANCELLED; REJECTED → PENDING).
- [ ] WPC2-02-BE-04 [core] Từ chối loại case chưa đăng ký bằng lỗi rõ.
- [ ] WPC2-02-BE-05 [farm] Đăng ký loại case: duyệt thửa, sửa thửa, duyệt vụ, duyệt chẩn đoán, xác nhận phun, dùng định nghĩa `approval`.

**Frontend (web)**

- [ ] WPC2-02-FE-01 `types.ts` nhận `status` dạng chuỗi; `labels.ts` vẫn nhãn cho giá trị của Farm.

**Mobile**

- [ ] WPC2-02-MB-01 `mobile/src/types.ts`, `reportStatus.ts` nhận trạng thái không biết (hiển thị an toàn).

**Data, seed, migration**

- [ ] WPC2-02-DATA-01 [core] Cột `status` dạng chuỗi (đã là `EnumType.STRING`, kiểm tra ràng buộc).

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC2-02-TEST-01 Luồng duyệt Farm không đổi (TC2-05, TC2-06).

## WPC2-03: Hộp duyệt về core

Kết quả đầu ra: hộp duyệt chạy từ core, Farm cài `CaseSummaryProvider`.

**Backend**

- [ ] WPC2-03-BE-01 [core-api] `CaseSummaryProvider` (tiêu đề, người gửi, tóm tắt, đường dẫn mở entity).
- [ ] WPC2-03-BE-02 [core] Chuyển `ApprovalRequestService`, `ApprovalRequestController`, DTO về core, bỏ repository Farm.
- [ ] WPC2-03-BE-03 [core] `PendingRequestLimiter` đọc loại case tính giới hạn từ cấu hình app.
- [ ] WPC2-03-BE-04 [farm] Cài `CaseSummaryProvider` cho thửa, sửa thửa, vụ, chẩn đoán, phun.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC2-03-TEST-01 Danh sách và chi tiết yêu cầu không đổi (TC2-07).

## WPC2-04: Thông báo và kênh chat

Kết quả đầu ra: Farm gửi đủ thông báo; Lab đăng ký loại và kênh riêng không đụng core.

**Backend**

- [ ] WPC2-04-BE-01 [core-api] `NotificationPublisher`, `NotificationTypeDefinition`, `NotificationChannel`.
- [ ] WPC2-04-BE-02 [core] `Notification`, repository, service, controller chuyển về core; `type` dạng chuỗi.
- [ ] WPC2-04-BE-03 [core] `MattermostWebhookClient` nhận khóa kênh dạng chuỗi.
- [ ] WPC2-04-BE-04 [farm] Đăng ký toàn bộ loại thông báo cũ và hai kênh ADMIN, ENGINEER; `MattermostMessageBuilder` giữ ở Farm.

**Mobile**

- [ ] WPC2-04-MB-01 Màn thông báo hiển thị loại không biết an toàn.

**Data, seed, migration**

- [ ] WPC2-04-DATA-01 [core] Bảng `notifications` vào migration core; [farm] xóa khỏi migration Farm.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC2-04-TEST-01 Thông báo duyệt thửa và nhắc thu hoạch vẫn tạo (TC2-08).

## WPC2-05: Bảo mật, vai và manifest app

Kết quả đầu ra: ma trận quyền Farm không đổi; `GET /api/v1/app` trả manifest.

**Spec và tài liệu**

- [ ] WPC2-05-DOC-01 Spec và Swagger cho `GET /api/v1/app`.

**Backend**

- [ ] WPC2-05-BE-01 [core-api] `AppModule`, `RoleProvider`, `EndpointPolicyContributor`, `ClientRestrictionPolicy`.
- [ ] WPC2-05-BE-02 [core] `SecurityConfig` ghép endpoint công khai và hạn chế client từ interface; bỏ cấu hình riêng của Farm khỏi core.
- [ ] WPC2-05-BE-03 [core] `UserContext.hasRole`, `isStaff`; bỏ `isFarmer`, `isAdmin` cứng.
- [ ] WPC2-05-BE-04 [core] Tạo vai từ `RoleProvider` lúc khởi động bằng `KeycloakAdminClient`, idempotent.
- [ ] WPC2-05-BE-05 [host] `GET /api/v1/app` trả id, tên, vai, menu.
- [ ] WPC2-05-BE-06 [farm] Cài `AppModule`, `RoleProvider`, `EndpointPolicyContributor` (diseases, pesticides, treatment-rules), `ClientRestrictionPolicy` (FARMER chỉ dùng mobile).

**Infra, deploy, CI**

- [ ] WPC2-05-INFRA-01 `FARMER_CLIENT_IDS` chuyển vào cấu hình Farm trong `application-farm.yaml`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC2-05-TEST-01 Đối chiếu ma trận quyền trước và sau (TC2-09).

## WPC2-06: Cổng AI và tri thức theo namespace

Kết quả đầu ra: hỏi đáp tri thức Farm không đổi; core gọi RAG theo namespace.

**Backend**

- [ ] WPC2-06-BE-01 [core-api] `AiGateway`, `KnowledgeGateway`, `KnowledgeNamespace`.
- [ ] WPC2-06-BE-02 [core] Tách phần chung của `AiDiagnosisClient`; `KnowledgeService` và controller theo namespace.
- [ ] WPC2-06-BE-03 [farm] Client chẩn đoán (precheck, diagnose, symptoms) dựa trên `AiGateway`; `KnowledgeNamespace` của Farm đọc `knowledge-sources.json`.

**AI**

- [ ] WPC2-06-AI-01 `POST /api/v1/disease/knowledge/retrieve` nhận `namespace`, mặc định `farm`.
- [ ] WPC2-06-AI-02 Dữ liệu RAG hiện có gắn namespace `farm`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC2-06-TEST-01 Hỏi đáp tri thức trả cùng kết quả (TC2-10).

## WPC2-07: Đăng ký BI, xử lý tệp, health

Kết quả đầu ra: quyền Superset Farm giống trước; thêm view chỉ cần đăng ký ở app.

**Backend**

- [ ] WPC2-07-BE-01 [core-api] `BiDatasetProvider`, `FileProcessor`, `HealthContributor`.
- [ ] WPC2-07-BE-02 [core] `SupersetReadOnlyRoleSeeder` đọc danh sách từ provider; `SystemStatusService` gom `HealthContributor`.
- [ ] WPC2-07-BE-03 [farm] Cài `BiDatasetProvider` (`vw_*`, `diseases`, `pesticides`, `rice_varieties`), `FileProcessor` (xóa EXIF), `HealthContributor` (AI, thời tiết).

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC2-07-TEST-01 Superset đọc đúng view đã đăng ký, không đọc bảng khác (TC2-11).

## Đóng phase

- [ ] `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn phase.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch với `DXOS_APP=farm`.
- [ ] `dx-core-api` và `dx-core` không import gói của app nào; app chỉ import `dx-core-api`.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu và ảnh test không còn dùng.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
