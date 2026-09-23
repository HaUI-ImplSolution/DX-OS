# Plan thực hiện: Phase C2

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WPC2-01 → WPC2-02 → WPC2-03 → WPC2-04.
WPC2-05, WPC2-06, WPC2-07 làm song song được sau WPC2-01.

## WPC2-01: `Workspace` và `WorkspaceMember`

- Mục tham chiếu: RD3.
- Mục tiêu: một khái niệm đơn vị dùng cho nông hộ (Farm) và lớp, bộ môn (Lab).
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Đơn vị | `Household` (mã `H0042`, chủ hộ, một tài khoản) | `Workspace` (`code`, `type`, `name`, liên hệ, thuộc tính) |
| Gắn tài khoản | `keycloakUserId` trên household | `WorkspaceMember` nhiều-nhiều, có vai trong workspace |
| API | `/api/v1/households` | Core: `/api/v1/workspaces`; Farm: `/api/v1/households` giữ nguyên làm bí danh, JSON không đổi |
| Mã đơn vị | Sinh `H` + số trong core | `type` quyết định tiền tố; Farm đăng ký `HOUSEHOLD` (`H`) |

**Hướng làm**

- Farm dùng `type = HOUSEHOLD` và một thành viên chủ hộ; phạm vi dữ liệu của FARMER vẫn theo workspace.
- Cột `parcels.household_id` giữ tên, khóa ngoại trỏ tới `workspaces`; view BI và truy vấn Farm không đổi.
- Core/App: tiền tố mã và `type` do app đăng ký qua `AppModule`; core không biết "H".

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`, `app-farm`.
- File chính: `Household*` thành `Workspace*`, `WorkspaceDirectory`, `ParcelService`, `ParcelRepository`, `CurrentUserService`/`UserContext`.
- Endpoint, bảng DB, biến môi trường: `/api/v1/workspaces` (core), `/api/v1/households` (Farm, bí danh); bảng `households` thành `workspaces`, thêm `workspace_members`.
- Role và client bị ảnh hưởng: ADMIN; web, seed demo và E2E của Farm không đổi.
- Tương thích ngược và dữ liệu cũ: hợp đồng `/households` giữ nguyên; DB dựng lại.

**Kết quả đầu ra**

- Không còn `Household` trong `dx-core-api` và `dx-core`; Farm tạo, liên kết và tra workspace như trước.

**Phụ thuộc, lưu ý**: Q2, Q3 ở `00_OVERVIEW.md`.

## WPC2-02: Workflow cấu hình được

- Mục tham chiếu: RD10.
- Mục tiêu: loại case khai báo trạng thái và bước chuyển; ticket Lab có luồng riêng.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Trạng thái case | Enum `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` | Chuỗi do `CaseTypeDefinition` khai báo, kèm cờ kết thúc |
| Bước chuyển | `allowedNext()` cố định | Định nghĩa khai báo bước chuyển và vai được thực hiện |
| Loại case hiện có | Hằng số trong `ApprovalCaseTypes` | Farm đăng ký định nghĩa `approval` mặc định giữ đúng luật cũ |
| Hành động | `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `RESUBMITTED`, `ASSIGNED` | Giữ; app thêm hành động riêng bằng chuỗi |

**Hướng làm**

- Core cung cấp định nghĩa `approval` (PENDING → APPROVED, REJECTED, CANCELLED; REJECTED → PENDING) để Farm dùng lại đúng hành vi.
- Cột `status` giữ dạng chuỗi; kiểm tra bước chuyển theo định nghĩa của loại case.
- Core/App: core không liệt kê loại case; app đăng ký.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`, `app-farm`.
- File chính: `WorkflowStatus`, `WorkflowService`, `WorkflowOperations`, `ApprovalCaseTypes`.
- Endpoint, bảng DB, biến môi trường: giá trị `status` trong JSON giữ nguyên với Farm.
- Role và client bị ảnh hưởng: web, mobile đọc `status`.
- Tương thích ngược và dữ liệu cũ: giá trị cũ vẫn hợp lệ trong định nghĩa `approval`.

**Kết quả đầu ra**

- Farm chạy đúng luật cũ; một định nghĩa thử có 5 trạng thái vẫn chạy được qua core.

**Phụ thuộc, lưu ý**: Q1; R1 ở `04_TRADEOFFS_RISKS.md`.

## WPC2-03: Hộp duyệt về core

- Mục tham chiếu: đề thi HaUI mục 4.1 (workflow engine).
- Mục tiêu: danh sách và chi tiết yêu cầu chờ xử lý dùng chung cho mọi app.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Danh sách yêu cầu | `ApprovalRequestService` ở Farm, gọi 5 repository | Khung lọc, phân trang, gán người phụ trách ở core |
| Thông tin entity trong danh sách | Truy vấn thẳng repository Farm | `CaseSummaryProvider` của app trả tiêu đề, người gửi, tóm tắt |
| Giới hạn yêu cầu chờ | `PendingRequestLimiter` dùng `FARMER_REQUESTS` | Core đọc danh sách loại case tính giới hạn từ cấu hình app |

**Hướng làm**

- Endpoint `/api/v1/approval-requests` giữ nguyên đường dẫn và JSON.
- Core/App: core không biết thửa hay vụ mùa.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`, `app-farm`.
- File chính: `ApprovalRequestService`, `ApprovalRequestController`, `PendingRequestLimiter`, DTO.
- Endpoint, bảng DB, biến môi trường: `MAX_PENDING_REQUESTS` thuộc core.
- Role và client bị ảnh hưởng: ADMIN, ENGINEER, FARMER (web, mobile).
- Tương thích ngược và dữ liệu cũ: JSON không đổi.

**Kết quả đầu ra**

- Hộp duyệt chạy từ core với Farm cài `CaseSummaryProvider`.

**Phụ thuộc, lưu ý**: cần WPC2-02.

## WPC2-04: Thông báo và kênh chat

- Mục tham chiếu: RD1.
- Mục tiêu: thông báo trong app và kênh ngoài dùng chung, loại thông báo do app đăng ký.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Loại thông báo | Enum `NotificationType` của Farm | Mã chuỗi do app đăng ký (nhãn, biểu tượng) |
| Bảng `notifications` | Migration Farm | Migration core |
| Người nhận | `notifyHousehold(Household, ...)` | `NotificationPublisher.notify(userId, ...)`; app tra người nhận qua `WorkspaceDirectory` |
| Kênh chat | `MattermostChannel` ADMIN/ENGINEER, lời nhắn dựng ở Farm | `NotificationChannel` khai báo khóa kênh theo chuỗi; client webhook ở core, lời nhắn ở app |

**Hướng làm**

- Cột `type` thành chuỗi; giá trị cũ của Farm giữ nguyên.
- Farm đăng ký toàn bộ loại cũ.
- Core/App: core không liệt kê loại thông báo hay tên kênh.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`, `app-farm`.
- File chính: `NotificationService`, `Notification`, `NotificationController`, `MattermostWebhookClient`, `MattermostMessageBuilder`, `MattermostChannel`.
- Endpoint, bảng DB, biến môi trường: `/api/v1/notifications` không đổi; webhook theo khóa kênh.
- Role và client bị ảnh hưởng: mobile, web.
- Tương thích ngược và dữ liệu cũ: giá trị `type` giữ nguyên.

**Kết quả đầu ra**

- Farm gửi đủ thông báo như trước; Lab có thể đăng ký loại và kênh riêng không đụng core.

**Phụ thuộc, lưu ý**: cần WPC2-01.

## WPC2-05: Bảo mật, vai và manifest app

- Mục tham chiếu: đề thi HaUI mục 4.1 (Identity/Authorization).
- Mục tiêu: vai, endpoint công khai, hạn chế client do app khai báo.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Vai của app | Hằng số rải ở `CurrentUserService`, `SecurityConfig` | `RoleProvider` khai báo; core tạo vai trong Keycloak lúc khởi động |
| Endpoint công khai | Cấu hình bổ sung trong Farm (C1) | `EndpointPolicyContributor` |
| FARMER chỉ dùng mobile | Cấu hình Farm, `FarmerClientRestrictionFilter` | `ClientRestrictionPolicy` tổng quát |
| Kiểm tra vai | `isFarmer()`, `isAdmin()` | `UserContext.hasRole(...)`, `isStaff()` theo cấu hình app |
| Thông tin app | Không có | `AppModule` và `GET /api/v1/app` trả id, tên, vai, menu |

**Hướng làm**

- `KeycloakAdminClient` có sẵn dùng để tạo vai idempotent lúc khởi động.
- Core/App: core không chứa tên vai hay đường dẫn của app.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`, `dx-host`, `app-farm`.
- File chính: `SecurityConfig`, `FarmerClientRestrictionFilter`, `CurrentUserService`, `KeycloakAdminClient`.
- Endpoint, bảng DB, biến môi trường: `GET /api/v1/app` (mới); `FARMER_CLIENT_IDS` chuyển vào cấu hình Farm.
- Role và client bị ảnh hưởng: mọi vai.
- Tương thích ngược và dữ liệu cũ: ma trận quyền Farm không đổi.

**Kết quả đầu ra**

- Ma trận quyền Farm giống trước; `GET /api/v1/app` trả đúng manifest.

**Phụ thuộc, lưu ý**: R3 ở `04_TRADEOFFS_RISKS.md`.

## WPC2-06: Cổng AI và tri thức theo namespace

- Mục tham chiếu: đề thi HaUI mục 4.2 (Intelligence).
- Mục tiêu: RAG dùng chung, nguồn tri thức theo app.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Client AI | `AiDiagnosisClient` chứa cả phần chung và phần chẩn đoán | `AiGateway` ở core (khóa dịch vụ, circuit breaker, health, retrieve); client chẩn đoán ở Farm |
| Hỏi đáp RAG | `diseaseCode` của Farm trong request | `namespace` và bộ lọc chung; `diseaseCode` là tham số của Farm |
| Danh mục nguồn | `knowledge-sources.json` ở backend | Do `KnowledgeNamespace` của từng app cung cấp |

**Hướng làm**

- AI service nhận `namespace`; dữ liệu RAG hiện có thuộc `farm`.
- Core/App: core không biết bệnh hay thuốc.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`, `app-farm`; `ai/`.
- File chính: `AiDiagnosisClient`, `KnowledgeService`, `KnowledgeController`, `ai/app/api/router.py`.
- Endpoint, bảng DB, biến môi trường: `POST /api/v1/disease/knowledge/retrieve` thêm `namespace` (mặc định `farm`).
- Role và client bị ảnh hưởng: ENGINEER, ADMIN.
- Tương thích ngược và dữ liệu cũ: mặc định `farm` giữ hành vi cũ.

**Kết quả đầu ra**

- Hỏi đáp tri thức của Farm không đổi; core gọi RAG theo namespace.

**Phụ thuộc, lưu ý**: cần WPC2-01; phần AI nhỏ, không đụng CV.

## WPC2-07: Đăng ký BI, xử lý tệp, health

- Mục tham chiếu: đề thi HaUI mục 4.2 (D - Data).
- Mục tiêu: không còn danh sách view và dịch vụ viết tay trong core.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Quyền đọc của Superset | `READABLE_RELATIONS` viết tay | Seeder đọc từ các `BiDatasetProvider` |
| Làm sạch ảnh | `FileStorageService` ở Farm | `FileProcessor` do app cài; core gọi trước khi lưu |
| Trạng thái hệ thống | `SystemStatusService` biết AI, storage | Core gom từ `HealthContributor`; Farm thêm AI, thời tiết |

**Hướng làm**

- Seeder cấp quyền theo danh sách; view và bảng không được đăng ký vẫn đóng.
- Core/App: core không chứa tên view `vw_*`.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`, `app-farm`.
- File chính: `SupersetReadOnlyRoleSeeder`, `FileStorageService`, `SystemStatusService`.
- Endpoint, bảng DB, biến môi trường: `/api/v1/system/status` không đổi.
- Role và client bị ảnh hưởng: Superset, ADMIN.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Quyền Superset của Farm giống trước; thêm view mới chỉ cần đăng ký ở app.

**Phụ thuộc, lưu ý**: cần WPC2-01.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `/api/v1/workspaces` (core) | Mới; `/api/v1/households` của Farm giữ nguyên | Không |
| Endpoint | `GET /api/v1/app` | Mới: manifest | Không |
| Endpoint | `POST /api/v1/disease/knowledge/retrieve` (AI) | Thêm `namespace`, mặc định `farm` | Không |
| Interface | `dx-core-api` | Toàn bộ interface ở `SPI_CATALOG.md` | Không (Farm là app duy nhất) |
| Bảng DB | `households` → `workspaces`; `workspace_members`; `notifications` về core; `parcels.household_id` giữ tên, khóa ngoại tới `workspaces` | Đổi tên bảng, thêm bảng | Có với DB đã tồn tại |
| Biến môi trường | `FARMER_CLIENT_IDS` | Thuộc cấu hình Farm | Không |

## Dữ liệu, migration và rollback

- Migration: cập nhật migration core và Farm (re-baseline như C1).
- Dữ liệu cũ: dựng lại bằng `deploy/reset.sh`.
- Rollback: quay lại nhánh trước phase.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Farm đăng ký định nghĩa `approval` mặc định ở core | Giữ nguyên luật duyệt hiện có |
| D2 | Chỉ tạo interface khi Farm và Lab khác nhau | Tránh trừu tượng thừa |
| D3 | App đăng ký tên, tiền tố, loại thông báo; core không liệt kê | Core không biết app |
