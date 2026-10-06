# Checklist: Phase 2

Mã dòng: `WP2-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, DATA, INFRA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP2-01: Entity Household và migration

Kết quả đầu ra: mọi `Parcel` có `household_id`; `canAccess` dùng `householdId` thay `farmerName`.

**Spec và tài liệu**

- [x] WP2-01-DOC-01 Cập nhật Swagger request/response của `POST /api/v1/parcels` (bỏ `farmerName` tự do).

**Backend**

- [x] WP2-01-BE-01 Thêm `model/Household.java` (`id`, `code`, `fullName`, `phone`, `address`, `keycloakUserId` unique, `createdAt`/`updatedAt`).
- [x] WP2-01-BE-02 Thêm `repository/HouseholdRepository.java`.
- [x] WP2-01-BE-03 `model/Parcel.java`: thêm `householdId` (FK), giữ `farmerName` tạm thời.
- [x] WP2-01-BE-04 Sinh `Household.code` tuần tự trong transaction (ví dụ `H%04d`), có unique constraint DB chống trùng khi đồng thời.
- [x] WP2-01-BE-05 `CurrentUserService`: thêm `currentHousehold()` (tra theo `sub`), đổi `canAccess(Parcel)` sang so `householdId`.
- [x] WP2-01-BE-06 `ParcelService.create(...)`: lấy `householdId` từ hộ của người gọi, bỏ nhận `farmerName` tự do từ request.

**Data, seed, migration**

- [x] WP2-01-DATA-01 Đưa Flyway vào dự án, viết migration baseline mô tả schema hiện có.
- [x] WP2-01-DATA-02 Migration thêm bảng `households`, cột `parcels.household_id` (nullable tạm).
- [x] WP2-01-DATA-03 Script ghép dữ liệu: mỗi `farmerName` phân biệt tạo 1 `Household` mới (theo Q1), gán `household_id` cho `Parcel` tương ứng.
- [x] WP2-01-DATA-04 Migration đổi `household_id` thành `NOT NULL` sau khi script ghép chạy xong và xác nhận không còn `NULL`.

**Test**

- [x] WP2-01-TEST-01 2 hộ cùng `fullName` không thấy thửa của nhau (T2-01).
- [x] WP2-01-TEST-02 Đổi `name` claim trong token (giả lập) không làm mất quyền xem thửa cũ (T2-02).

## WP2-02: Ma trận quyền và `@PreAuthorize`

Kết quả đầu ra: ma trận quyền ở `01_PLAN.md` có test cho từng dòng áp dụng được ngay.

**Spec và tài liệu**

- [x] WP2-02-DOC-01 Chốt ma trận quyền ở `01_PLAN.md` với người phụ trách trước khi code (đã có bản đề xuất, chỉ cần xác nhận).

**Backend**

- [x] WP2-02-BE-01 `ParcelController`: `@PreAuthorize` cho method tạo (FARMER, ADMIN), giữ GET mở cho mọi role đã đăng nhập.
- [x] WP2-02-BE-02 `CropSeasonController` (xác nhận tên file khi code): tương tự WP2-02-BE-01.
- [x] WP2-02-BE-03 Rà toàn bộ controller còn lại theo ma trận quyền, thêm `@PreAuthorize` cho method còn thiếu.

**Test**

- [x] WP2-02-TEST-01 ENGINEER gọi `POST /api/v1/parcels` → 403 (T2-03).
- [x] WP2-02-TEST-02 ENGINEER gọi `POST .../crop-seasons` → 403 (T2-04).
- [x] WP2-02-TEST-03 ADMIN gọi các endpoint ghi vẫn 200 (T2-05).

## WP2-03: Chặn FARMER ở web và Superset, thêm `aud`

Kết quả đầu ra: farmer đăng nhập web/Superset không gọi được API nghiệp vụ; token thiếu `aud` hợp lệ bị 401.

**Spec và tài liệu**

- [x] WP2-03-DOC-01 Xác nhận web và mobile hiện dùng chung client Keycloak hay client riêng (ghi vào đây trước khi bật `aud`, tránh khóa nhầm mobile).

**Backend**

- [x] WP2-03-BE-01 Thêm validator `aud` vào cấu hình `JwtDecoder`.

**Frontend (web)**

- [x] WP2-03-FE-01 Sau đăng nhập, decode role; chỉ có `FARMER` thì hiện màn chặn, không gọi API nghiệp vụ.

**Infra, deploy, CI**

- [x] WP2-03-INFRA-01 `sso/realm-dxfarm.json`: thêm audience mapper cho client API.
- [x] WP2-03-INFRA-02 Cấu hình client Superset không map role FARMER.

**Test**

- [x] WP2-03-TEST-01 Farmer đăng nhập web, gọi API nghiệp vụ bất kỳ → bị chặn ở FE, và 401/403 nếu vẫn gọi trực tiếp (T2-06).
- [x] WP2-03-TEST-02 Token thiếu `aud` hợp lệ gọi API → 401 (T2-07).
- [ ] WP2-03-TEST-03 Mobile vẫn đăng nhập và gọi API bình thường sau khi bật `aud` (T2-08, hồi quy).

## WP2-04: Audit theo id người dùng và xóa mềm

Kết quả đầu ra: bản ghi mới có id người tạo/quyết định; có đường xóa mềm cho ADMIN.

**Backend**

- [x] WP2-04-BE-01 Thêm `reportedByUserId` vào `DiseaseReport`, set từ claim `sub` lúc tạo.
- [x] WP2-04-BE-02 Thêm `decidedByUserId` vào `Diagnosis`, set từ claim `sub` lúc approve/reject.
- [x] WP2-04-BE-03 Thêm `deletedAt` cho `Parcel`, `CropSeason`, `DiseaseReport`; lọc `deletedAt IS NULL` ở query mặc định.
- [x] WP2-04-BE-04 Thêm `DELETE /api/v1/parcels/{id}`, `/crop-seasons/{id}`, `/disease-reports/{id}` (chỉ ADMIN), set `deletedAt` thay vì xóa vật lý.
- [x] WP2-04-BE-05 Thêm query param `includeDeleted` cho ADMIN ở các endpoint GET danh sách tương ứng (theo Q2).

**Data, seed, migration**

- [x] WP2-04-DATA-01 Migration thêm cột `deleted_at`, `reported_by_user_id`, `decided_by_user_id`.

**Test**

- [x] WP2-04-TEST-01 Xóa mềm một thửa → biến mất khỏi GET thường, còn thấy với `includeDeleted=true` (T2-09).
- [x] WP2-04-TEST-02 FARMER/ENGINEER gọi `DELETE` → 403 (T2-10).

## WP2-05: Onboarding — admin tạo hộ và tài khoản

Kết quả đầu ra: admin tạo được 1 hộ + 1 tài khoản FARMER qua API, đăng nhập mobile ngay được.

**Backend**

- [x] WP2-05-BE-01 Thêm `client/KeycloakAdminClient.java` dùng client credentials riêng, quyền tối thiểu.
- [x] WP2-05-BE-02 `POST /api/v1/households`: tạo `Household`, gọi Keycloak tạo user, map `keycloakUserId`; rollback `Household` nếu bước Keycloak lỗi.

**Infra, deploy, CI**

- [x] WP2-05-INFRA-01 Thêm client service account trong `sso/realm-dxfarm.json`; thêm biến `KEYCLOAK_ADMIN_CLIENT_ID`/`SECRET` vào `.env.example` (không default, theo nguyên tắc WP1-01).

**Test**

- [x] WP2-05-TEST-01 Admin tạo hộ mới → user Keycloak tồn tại, đăng nhập mobile bằng mật khẩu tạm thành công (T2-11).
- [x] WP2-05-TEST-02 Giả lập lỗi gọi Keycloak → `Household` vừa tạo bị rollback, không để lại hộ "mồ côi" (T2-12).

## Đóng phase

- [x] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (web) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, bằng tiếng Anh.
- [x] Swagger khớp code.
- [x] `.env.example`, seed, migration chạy được trên clone sạch.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng (riêng đăng nhập app mobile trên điện thoại thật chưa chạy).
- [x] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
