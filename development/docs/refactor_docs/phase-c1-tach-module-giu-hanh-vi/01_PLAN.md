# Plan thực hiện: Phase C1

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WPC1-01 → WPC1-02 → WPC1-03 → WPC1-04 → WPC1-06.
WPC1-05 (migration) làm song song với WPC1-03 sau khi WPC1-01 xong.

## WPC1-01: Dựng parent Maven và khung module

- Mục tham chiếu: RD1, RD8.
- Mục tiêu: có `backend/pom.xml` và bốn module build được, chưa chuyển code.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Build backend | Một module `backend/DX-Farm`, jar `DX-Farm-0.0.1-SNAPSHOT.jar` | Parent `dx-os-parent`, module `dx-core-api`, `dx-core`, `app-farm`, `dx-host`; jar chạy được ở `dx-host` |
| Phụ thuộc | Một pom | `app-farm` phụ thuộc `dx-core-api`; `dx-core` phụ thuộc `dx-core-api`; `dx-host` phụ thuộc cả ba |

**Hướng làm**

- Parent giữ Spring Boot 4.1.1, Java 21, cấu hình formatter hiện có.
- Dùng `git mv` để giữ lịch sử file.
- Core/App: `app-farm` khai báo phụ thuộc duy nhất vào `dx-core-api` (cộng thư viện Spring/JPA cần thiết).

**Phạm vi ảnh hưởng**

- Module: tất cả.
- File chính: `backend/pom.xml` (mới), pom của bốn module, `backend/DX-Farm/Dockerfile`, `deploy/docker-compose.yml` (`context`), `.github/workflows/ci.yml` (`working-directory`).
- Endpoint, bảng DB, biến môi trường: không.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: đường dẫn `backend/DX-Farm` biến mất; mọi tham chiếu phải đổi.

**Kết quả đầu ra**

- `./mvnw compile` ở `backend/` chạy được trên khung module rỗng; Dockerfile build được `dx-host`.

**Phụ thuộc, lưu ý**: R1 ở `04_TRADEOFFS_RISKS.md`.

## WPC1-02: Tạo `dx-core-api`

- Mục tham chiếu: RD1, `SPI_CATALOG.md`.
- Mục tiêu: có đủ facade và kiểu dữ liệu công khai để `app-farm` không cần import `dx-core`.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Farm mở và quyết định case | Gọi `WorkflowService` và đọc entity `WorkflowCase` | Gọi `WorkflowOperations`, nhận `CaseView` |
| Farm tra nông hộ | Dùng `HouseholdRepository`, entity `Household` | Gọi `WorkspaceDirectory`, nhận `WorkspaceView` |
| Farm lưu ảnh | Dùng `StorageService` (lộ client MinIO) | Gọi `ObjectStorage` (lưu, đọc, link có hạn, kiểm tra sẵn sàng) |
| Farm biết người đăng nhập | `CurrentUserService` | `UserContext` |
| Farm gửi thông báo | `NotificationService.notifyHousehold*` | `NotificationPublisher` |
| Handler duyệt | `onDecision(WorkflowCase, ...)` | `onDecision(CaseView, ...)` |

**Hướng làm**

- Chỉ đưa vào `dx-core-api` những gì Farm đang dùng thật: `Household` 10 chỗ, `WorkflowStatus` 9, `WorkflowService` 6, `WorkflowCase` 6, `StorageService` 6, `WorkflowActor` 4, `WorkflowDecisionHandler` 3.
- `ApiResponse`, `PageResponse`, `WorkflowStatus`, `WorkflowAction`, các exception dùng chung chuyển sang `dx-core-api`.
- Interface mở rộng khác (`RoleProvider`, `BiDatasetProvider`...) chưa tạo ở phase này; thêm ở C2 khi có người cài.
- Core/App: entity của core không lộ ra.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`.
- File chính: facade và view mới; DTO/exception chuyển từ `dto/`, `exception/`.
- Endpoint, bảng DB, biến môi trường: không.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: JSON trả về của endpoint không đổi.

**Kết quả đầu ra**

- Mọi tham chiếu của Farm tới core đi qua kiểu trong `dx-core-api`.

**Phụ thuộc, lưu ý**: cần WPC1-01.

## WPC1-03: Chuyển code vào module

- Mục tham chiếu: `MODULE_MAP.md`.
- Mục tiêu: `dx-core` chứa `core/*` và phần dùng chung; `app-farm` chứa phần nghiệp vụ.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| `Parcel` thuộc nông hộ | Quan hệ JPA tới entity `Household` | Cột `household_id` dạng số; tra cứu qua `WorkspaceDirectory` |
| `ParcelRepository` lọc theo nông hộ | Truy vấn qua entity `Household` | Truy vấn theo id |
| `ApprovalRequestDetailResponse` | Import kiểu của core | Dùng kiểu của `dx-core-api` |
| Seeder, security, exception handler | Cùng module với Farm | Nằm trong `dx-core` |

**Hướng làm**

- Chuyển theo thứ tự: DTO/exception chung → `core/*` → bảo mật và nền tảng → nghiệp vụ Farm.
- Endpoint công khai và hạn chế client FARMER nằm trong cấu hình bảo mật bổ sung của `app-farm`.
  `SecurityConfig` của `dx-core` không chứa tên endpoint hay vai của Farm.
- `ApprovalRequestService` chuyển cả khối sang `app-farm` ở C1 (đang gọi 5 repository của Farm); C2 tách khung về core.
- Core/App: `dx-core` không import gói Farm; kiểm bằng tìm kiếm import.

**Phạm vi ảnh hưởng**

- Module: `dx-core`, `app-farm`.
- File chính: toàn bộ `backend/DX-Farm/src/main` và `src/test`.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: test đi theo module chứa code được kiểm.

**Kết quả đầu ra**

- `./mvnw compile` đạt cho từng module; không còn import vi phạm ranh giới.

**Phụ thuộc, lưu ý**: R2, R3 ở `04_TRADEOFFS_RISKS.md`.

## WPC1-04: `dx-host` và chọn app bằng `DXOS_APP`

- Mục tham chiếu: RD2.
- Mục tiêu: một fat jar chạy được; app nạp theo biến môi trường.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Khởi động | Một ứng dụng Spring Boot cố định | `dx-host` nạp core và app theo `dxos.app` |
| `DXOS_APP` không đặt | Không áp dụng | Mặc định `farm` |
| `DXOS_APP` sai | Không áp dụng | Dừng khi khởi động, nêu các giá trị hợp lệ |

**Hướng làm**

- Mỗi app có auto-configuration riêng, bật bằng `@ConditionalOnProperty(dxos.app=...)`, giới hạn quét entity, repository và Flyway location của app.
- `dx-core` có auto-configuration riêng cho entity và Flyway của core.
- Core/App: core không biết tên app.

**Phạm vi ảnh hưởng**

- Module: `dx-host`, `app-farm`, `dx-core`.
- File chính: class `main`, các file `AutoConfiguration.imports`, `application.yaml`.
- Endpoint, bảng DB, biến môi trường: thêm `DXOS_APP` (mặc định `farm`).
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- `DXOS_APP=farm` khởi động như trước; giá trị lạ làm dừng sớm.

**Phụ thuộc, lưu ý**: cần WPC1-03; R4 ở `04_TRADEOFFS_RISKS.md`.

## WPC1-05: Tách migration thành hai location

- Mục tham chiếu: RD9.
- Mục tiêu: core có migration riêng, không đọc bảng của app.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Vị trí migration | Một thư mục V1 đến V9 | `db/migration/core` và `db/migration/farm`, mỗi nơi một bảng lịch sử |
| `households`, `workflow_*` | V2, V4 kèm backfill đọc `parcels`, `diagnoses` | Bảng nằm ở core, không backfill |
| `parcels.household_id` | Thêm trong V2 | Thêm trong migration Farm (Farm tham chiếu core) |
| V3 | Trộn nhiều bảng Farm | Chuyển hẳn sang Farm |

**Hướng làm**

- Re-baseline: đánh số lại từng location, bỏ bước backfill dữ liệu cũ (DB luôn dựng sạch).
- Thứ tự chạy: core trước, Farm sau.
- Bảng `notifications` vẫn ở migration Farm ở phase này; C2 chuyển.
- Core/App: không có `REFERENCES` từ bảng core sang bảng app.

**Phạm vi ảnh hưởng**

- Module: `dx-core`, `app-farm`.
- File chính: `V1` đến `V9` hiện tại, cấu hình Flyway.
- Endpoint, bảng DB, biến môi trường: tên bảng không đổi; thêm một bảng lịch sử Flyway.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: DB đã có không áp dụng được; phải `deploy/reset.sh`.

**Kết quả đầu ra**

- Dựng DB trống: hai location chạy theo thứ tự, schema cuối giống trước refactor.

**Phụ thuộc, lưu ý**: R5 ở `04_TRADEOFFS_RISKS.md`.

## WPC1-06: Cập nhật build, triển khai và tài liệu

- Mục tham chiếu: đề thi HaUI mục 5 (build từ source).
- Mục tiêu: clone sạch build và chạy được theo README.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Lệnh build | `./mvnw verify` trong `backend/DX-Farm` | `./mvnw verify` trong `backend` |
| Docker build | `context: ../backend/DX-Farm` | `context: ../backend`, Dockerfile build `dx-host` |
| CI | `working-directory: ./backend/DX-Farm` | `./backend` |

**Hướng làm**

- Sửa `README.md`, `CONTRIBUTING.md`, `ci.yml`, `docker-compose.yml`.
- Thêm `DXOS_APP` vào `deploy/.env.example` với mặc định `farm`.

**Phạm vi ảnh hưởng**

- Module: `dx-host`.
- File chính: tài liệu, workflow, compose.
- Biến môi trường: `DXOS_APP`.

**Kết quả đầu ra**

- Không còn tham chiếu `backend/DX-Farm` trong repo.

**Phụ thuộc, lưu ý**: cần WPC1-04.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `/api/v1/...` | Không đổi | Không |
| Interface | `dx-core-api` | Facade và view mới | Không (chưa có app khác) |
| Bảng DB | Tất cả | Tên không đổi; thêm bảng lịch sử Flyway của core | Có với DB đã tồn tại (phải dựng lại) |
| Biến môi trường | `DXOS_APP` | Mới, mặc định `farm` | Không |

## Dữ liệu, migration và rollback

- Migration: hai location Flyway, core chạy trước.
- Dữ liệu cũ: không migrate; dựng lại bằng `deploy/reset.sh`.
- Rollback: quay lại commit trước phase; DB dựng lại.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Tách cơ học trước, đổi tên và tổng quát hóa ở C2 | Giữ hành vi để so với baseline |
| D2 | App giữ `household_id` dạng số, không giữ quan hệ JPA tới entity core | Hai module không chia sẻ entity |
| D3 | Bỏ backfill dữ liệu cũ khi re-baseline | DB luôn dựng sạch; core không được đọc bảng app |
