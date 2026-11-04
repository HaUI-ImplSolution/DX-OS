# Checklist: Phase 13

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP13-{xx}-{lớp}-{nn}`. Dòng BE ghi module trong ngoặc vuông: `[model]`, `[core]`, `[engine]`.
Mục "Đóng phase" chỉ tick khi đóng phase.

## WP13-00: Thử nghiệm Keycloak

Kết quả đầu ra: biết client role chạy trọn vẹn với realm và backend hiện có.

- [x] WP13-00-TEST-01 Tạo client, vai, gán vai, lấy token, gọi backend, truy vấn người giữ vai.
- [x] WP13-00-TEST-02 So cấu hình `dxos-web` với client dùng để lấy token.
- [x] WP13-00-DOC-01 Ghi kết quả vào `01_PLAN.md`.

## WP13-01: Cách ly vai theo mô hình

Kết quả đầu ra: hai mô hình trùng tên vai áp dụng được và quyền không chung; các script e2e đạt.

**Spec và tài liệu**

- [x] WP13-01-DOC-01 Chốt Q1 đến Q4 ở `00_OVERVIEW.md` mục 9.

**Backend**

- [x] WP13-01-BE-01 [core] `KeycloakAdminClient`: tạo client (bỏ qua nếu đã có), tạo vai dưới client, người giữ vai theo client.
- [x] WP13-01-BE-02 [core] `UserContext` và `CurrentUserService`: đọc vai của người gọi theo `clientId` từ `resource_access`; `KeycloakRoleConverter` giữ vai realm.
- [x] WP13-01-BE-03 [engine] `ApplyService`: bước `ENSURE_CLIENT`, vai tạo dưới client, bỏ chốt chặn trùng vai giữa các mô hình; `ModelParser` giữ cấm vai `ADMIN` theo Q3.
- [x] WP13-01-BE-04 [engine] `DataService` và `MetaController`: quyền theo thực thể, người xử lý, quy tắc hành động và danh sách người xử lý theo vai trong client của mô hình.
- [x] WP13-01-BE-05 [engine] `ModelRegistry`: bỏ dòng log vai dùng chung giữa các mô hình.
- [x] WP13-01-BE-06 [engine] `ApplyService`: bỏ khóa dùng chung giữa các lần áp dụng, giữ khóa theo mô hình.
- [x] WP13-01-BE-07 [engine] Lớp `ModelClient` đặt tên client của mô hình.

**Frontend (web)**

- [x] WP13-01-FE-01 Nhãn tiếng Việt cho bước `ENSURE_CLIENT` ở lịch sử áp dụng; thông báo mới của Keycloak.

**Infra**

- [x] WP13-01-INFRA-01 `deploy/dev/kc.py`: gán vai theo client; đổi cách tạo người dùng thử ở các script `check_*.py`; các script dùng lại tên vai trùng giữa các mô hình.
- [x] WP13-01-INFRA-02 `sso/realm-dxos.json`: thêm `manage-clients`, `view-clients`, `query-clients` cho tài khoản dịch vụ của backend.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WP13-01-TEST-01 Chạy T13-01 đến T13-05 và các script cũ.

## WP13-02: Tab Người dùng, bước 1

Kết quả đầu ra: tab hiện người dùng và vai theo ứng dụng, gán và gỡ vai và đơn vị.

**Spec và tài liệu**

- [x] WP13-02-DOC-01 `@Operation` cho `GET /users`, `GET /users/options`, `GET /users/{id}`, `PUT` và `DELETE` của vai và đơn vị.

**Backend**

- [x] WP13-02-BE-01 [core] `KeycloakAdminClient`: danh sách người dùng có tìm kiếm và phân trang, chi tiết, vai theo client của một người, nhóm của một người, gán và gỡ vai theo client.
- [x] WP13-02-BE-02 [engine] Gói `users`: controller ADMIN, kiểm vai và đơn vị có trong mô hình đang chạy, 404 khi không có.

**Frontend (web)**

- [x] WP13-02-FE-01 `UsersTab.tsx`: danh sách, tìm kiếm, phân trang.
- [x] WP13-02-FE-02 Chi tiết người dùng: vai theo ứng dụng, đơn vị, gán và gỡ bằng một cú bấm.
- [x] WP13-02-FE-03 Thông báo lỗi mới bằng tiếng Việt ở `backendMessages.ts`.

**Test**

- [x] WP13-02-TEST-01 Chạy T13-06 đến T13-09 (`check_users.py`) và thử bằng Chrome.

## WP13-03: Tab Người dùng, bước 2

Kết quả đầu ra: tạo người dùng, khóa và mở, đặt lại mật khẩu trong bảng điều khiển.

- [x] WP13-03-DOC-01 `@Operation` cho `POST /users`, `PUT /users/{id}/enabled`, `POST /users/{id}/password`.
- [x] WP13-03-BE-01 [core] `KeycloakAdminClient`: tạo người dùng không kèm vai, khóa và mở, đặt mật khẩu tạm.
- [x] WP13-03-BE-02 [engine] Endpoint tương ứng; ADMIN không khóa được chính mình.
- [x] WP13-03-FE-01 Form tạo người dùng, nút khóa và mở, đặt lại mật khẩu, hộp hiện mật khẩu tạm một lần.
- [x] WP13-03-TEST-01 Chạy T13-10 đến T13-12 (`check_users.py`).

## WP13-04: Ghi vết thay đổi tài khoản

Kết quả đầu ra: mỗi thay đổi về tài khoản có một bản ghi audit với người quản trị thực hiện, không có mật khẩu.

- [x] WP13-04-DOC-01 Chốt Q5 ở `00_OVERVIEW.md` mục 9.
- [x] WP13-04-DOC-02 Liệt kê tám hành động ở `@Operation` của `GET /api/v1/audit-logs`.
- [x] WP13-04-BE-01 [engine] `UserAdminService`: ghi bản ghi audit sau mỗi thay đổi thành công, kèm tên đăng nhập, mô hình và vai hoặc đơn vị.
- [x] WP13-04-TEST-01 Chạy T13-13 và T13-14 (`check_users.py`), gồm kiểm sự kiện quản trị của Keycloak không chứa mật khẩu.

## WP13-05: Kiểm chứng và tài liệu

- [x] WP13-05-TEST-01 `./mvnw clean verify`, `npx tsc --noEmit`, chín script e2e (428/428), Chrome.
- [x] WP13-05-DOC-01 Cập nhật các tài liệu chung ở `00_OVERVIEW.md` mục 10.
- [x] WP13-05-DOC-02 Ghi điều kiện của realm (`fullScopeAllowed`, quyền client của tài khoản dịch vụ) vào `deploy/OPERATIONS.md` và `sso/README.md`.

## Đóng phase

- [x] `./mvnw clean verify` đạt, `npx tsc --noEmit` đạt.
- [x] Đã đọc lại diff; comment mới ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh; `@Operation` bằng tiếng Anh, không nhắc nhãn phase.
- [x] `.env.example` chỉ đổi một dòng ghi chú, không đổi biến; stack dựng được từ trạng thái rỗng bằng realm mới (stack dev và stack thử).
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] Dọn container và volume thử.
- [x] README, CHANGELOG, ARCHITECTURE, TESTING cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đạt và có bằng chứng.
