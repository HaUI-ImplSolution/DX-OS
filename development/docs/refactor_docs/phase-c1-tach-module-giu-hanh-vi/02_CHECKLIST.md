# Checklist: Phase C1

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC1-{xx}-{lớp}-{nn}`.
Dòng BE và DATA ghi module trong ngoặc vuông.

## WPC1-01: Dựng parent Maven và khung module

Kết quả đầu ra: `./mvnw compile` ở `backend/` chạy được trên khung module.

**Spec và tài liệu**

- [x] WPC1-01-DOC-01 Ghi tên module, package, artifact đã chốt (Q1 của C0) vào `MODULE_MAP.md`.

**Backend**

- [x] WPC1-01-BE-01 [host] Tạo `backend/pom.xml` (parent) và bốn pom module.
- [x] WPC1-01-BE-02 [host] `git mv` Maven wrapper, formatter config và `.mvn` lên `backend/`.
- [x] WPC1-01-BE-03 [core-api] Khung `dx-core-api` với package gốc.

**Infra, deploy, CI**

- [x] WPC1-01-INFRA-01 Dockerfile build `dx-host` từ `backend/`.
- [x] WPC1-01-INFRA-02 `deploy/docker-compose.yml`: đổi `context` backend.
- [x] WPC1-01-INFRA-03 `.github/workflows/ci.yml`: đổi `working-directory`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC1-01-TEST-01 `./mvnw compile` ở `backend/` đạt (TC1-01).

## WPC1-02: Tạo `dx-core-api`

Kết quả đầu ra: mọi tham chiếu của Farm tới core đi qua kiểu trong `dx-core-api`.

**Spec và tài liệu**

- [x] WPC1-02-DOC-01 Cập nhật `SPI_CATALOG.md` phần facade theo chữ ký thực tế.

**Backend**

- [x] WPC1-02-BE-01 [core-api] `WorkflowOperations`, `CaseView`, `WorkflowStatus`, `WorkflowAction`, `WorkflowActor`, `WorkflowDecisionHandler(CaseView, ...)`.
- [x] WPC1-02-BE-02 [core-api] `WorkspaceDirectory`, `WorkspaceView` (tra theo id, mã, tài khoản, nhiều id một lần).
- [x] WPC1-02-BE-03 [core-api] `ObjectStorage` (lưu, link có hạn, kiểm tra sẵn sàng), `UserContext`; `NotificationPublisher` thêm ở C2.
- [x] WPC1-02-BE-04 [core-api] `ApiResponse`, `PageResponse`, `CaseQuery`, `WorkflowCaseResponse`, `WorkflowEventResponse`; DTO notification, approval, knowledge, status ở `app-farm` cho tới C2.
- [x] WPC1-02-BE-05 [core-api] Exception dùng chung chuyển từ `exception/`.
- [x] WPC1-02-BE-06 [core] Cài các facade trên bằng service hiện có.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC1-02-TEST-01 Tìm kiếm import: `app-farm` chỉ import `dx-core-api` (TC1-02).

## WPC1-03: Chuyển code vào module

Kết quả đầu ra: `./mvnw compile` đạt cho từng module, không import vi phạm ranh giới.

**Backend**

- [x] WPC1-03-BE-01 [core] Chuyển `core/identity`, `core/workflow`, `core/storage` vào `dx-core`.
- [x] WPC1-03-BE-02 [core] Chuyển `GlobalExceptionHandler`, `AsyncConfig`, `SchedulingConfig`, `TimeConfig`, `OpenApiConfig`, các seeder hạ tầng, `SystemStatusService` và controller.
- [x] WPC1-03-BE-03 [core] Chuyển bảo mật: `SecurityConfig`, `KeycloakRoleConverter`, entry point, access denied handler, `ServiceApiKeyAuthenticationFilter`.
- [x] WPC1-03-BE-04 [farm] Chuyển model, repository, service, controller, DTO nghiệp vụ, ba handler duyệt.
- [x] WPC1-03-BE-05 [farm] `Parcel`, `ParcelRepository`: bỏ quan hệ JPA tới `Household`, dùng id và `WorkspaceDirectory`.
- [x] WPC1-03-BE-06 [farm] `ApprovalRequestService` và `ApprovalRequestController` chuyển nguyên khối sang Farm, cùng nhóm Notification, `AiDiagnosisClient`, `KnowledgeService`, `SystemStatusService`, `PendingRequestLimiter`.
- [x] WPC1-03-BE-07 [farm] Cấu hình bảo mật bổ sung cho endpoint công khai và hạn chế client FARMER.
- [x] WPC1-03-BE-08 [farm] Thay mọi import `core.*` bằng kiểu của `dx-core-api`.
- [x] WPC1-03-BE-09 [core] Chuyển test về module chứa code được kiểm.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC1-03-TEST-01 Tìm kiếm import hai chiều (TC1-02, TC1-03).

## WPC1-04: `dx-host` và chọn app bằng `DXOS_APP`

Kết quả đầu ra: `DXOS_APP=farm` khởi động như trước; giá trị lạ làm dừng sớm.

**Backend**

- [x] WPC1-04-BE-01 [host] Class `main`, `application.yaml` chung.
- [x] WPC1-04-BE-02 [core] Auto-configuration cho entity, repository, Flyway của core.
- [x] WPC1-04-BE-03 [farm] Auto-configuration có điều kiện `dxos.app=farm`, giới hạn quét entity, repository, Flyway.
- [x] WPC1-04-BE-04 [host] Dừng khi khởi động nếu `dxos.app` không khớp app nào, nêu giá trị hợp lệ.

**Infra, deploy, CI**

- [x] WPC1-04-INFRA-01 `deploy/.env.example` thêm `DXOS_APP=farm`; compose truyền biến vào backend.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC1-04-TEST-01 Khởi động với `DXOS_APP` đúng và sai (TC1-04, TC1-05).

## WPC1-05: Tách migration thành hai location

Kết quả đầu ra: DB trống dựng xong, schema cuối giống trước refactor.

**Data, seed, migration**

- [x] WPC1-05-DATA-01 [core] `db/migration/core`: `households`, `workflow_case`, `workflow_event` (không backfill).
- [x] WPC1-05-DATA-02 [farm] `db/migration/farm`: bảng Farm, thêm `parcels.household_id` tham chiếu `households`, V3 chuyển hẳn sang đây.
- [x] WPC1-05-DATA-03 [host] Hai bean Flyway (core, Farm) với bảng lịch sử riêng; core chạy trước Farm; bỏ `baseline-on-migrate`.
- [x] WPC1-05-DATA-04 [farm] `data.sql` thành tài nguyên riêng của Farm, nạp qua `application-farm.yaml`; kiểm thứ tự chạy sau Flyway.
- [x] WPC1-05-DATA-05 [core] Bảng `notifications` còn ở migration Farm; ghi chú chuyển ở C2.
- [x] WPC1-05-DATA-06 [farm] V10 (`treatment_rules.notes` kiểu `text`) vào migration Farm.

**Infra, deploy, CI**

- [x] WPC1-05-INFRA-01 `data/scripts/generate_seed_sql.py` ghi ra đường dẫn mới; `test_generate_seed_sql.py`, `ci-ai.yml` (`paths` và bước `--check`), `data/README.md`, `README.md` mục seed khớp.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC1-05-TEST-01 So sánh schema cuối với baseline (TC1-06).
- [x] WPC1-05-TEST-02 Xử lý `HouseholdMigrationTest`, `WorkflowMigrationTest` (kiểm backfill đã bỏ) theo quyết định của người dùng; cập nhật `MigratedDatabase`, `SeedDataIntegrationTest`, `ConcurrencyIntegrationTest` (TC1-11, TC1-12).

## WPC1-06: Cập nhật build, triển khai và tài liệu

Kết quả đầu ra: không còn tham chiếu `backend/DX-Farm`.

**Spec và tài liệu**

- [x] WPC1-06-DOC-01 `README.md`, `CONTRIBUTING.md`: lệnh build mới.
- [x] WPC1-06-DOC-02 `ARCHITECTURE_OPEN_CORE.md` mục 2: ranh giới ở mức biên dịch.
- [x] WPC1-06-DOC-03 `CHANGELOG.md`: trạng thái cuối của backend.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC1-06-TEST-01 Tìm chuỗi `backend/DX-Farm` trong repo (TC1-07).

## Đóng phase

- [x] `./mvnw compile` đạt cho toàn bộ module (backend) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn phase.
- [x] `.env.example`, seed, migration chạy được trên clone sạch với `DXOS_APP=farm`.
- [x] `dx-core-api` và `dx-core` không import gói của app nào; app chỉ import `dx-core-api`.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
