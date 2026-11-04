# Plan thực hiện: Phase 13

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP13-00 (đã xong) → WP13-01 → WP13-02 → WP13-03 → WP13-04 → WP13-05.
WP13-02 cần WP13-01 vì tab biết vai thuộc ứng dụng nào từ client của mô hình.
WP13-03 làm sau khi WP13-02 chạy được.

## WP13-00: Thử nghiệm Keycloak (đã xong)

- Mục tiêu: biết client role có chạy trọn vẹn với realm và backend hiện có không, trước khi chọn giữa client role và tên vai kèm mô hình.
- Cách làm: chạy trên stack tạm, bằng Admin API, không sửa mã trong repo.

| Kiểm | Kết quả |
|---|---|
| Tạo client bearer-only cho một mô hình | 201; tạo lại cùng `clientId` trả 409, nên bước tạo chạy lại được |
| Tạo vai dưới client, gán cho người dùng | 201 và 204 |
| Token của client `dxos-check` (cấu hình y hệt `dxos-web`: `fullScopeAllowed`, cùng scope mặc định và mapper) | Có `resource_access.dxos-m-shop.roles = [BUYER]`; `aud` tự thêm `dxos-m-shop` |
| Backend nhận token | 200 ở `/meta/models` và `/inbox` |
| Người giữ vai | `GET /clients/{id}/roles/{role}/users` trả đúng; `GET /users/{id}/role-mappings` trả cả vai realm và vai theo client trong một lần gọi |
| Gỡ vai | Token cấp sau đó không còn vai |
| Kích thước token | Khoảng 2,7 KB với 30 client role cộng 30 vai realm có tiền tố; backend vẫn nhận |
| Tên vai có dấu chấm (phương án dự phòng) | Tạo, đọc, gán, token và truy vấn người giữ vai đều chạy |

- Chưa kiểm: đọc token trong trình duyệt (công cụ chặn); đăng nhập thật bằng `dxos-web` chỉ được suy ra từ cấu hình giống nhau.
- Kết luận: chọn client role.

## WP13-01: Cách ly vai theo mô hình

- Mục tham chiếu: phần 6 của `BACKLOG.md`, dòng "Vai tách theo ứng dụng, đơn vị dùng chung".
- Mục tiêu: vai của mô hình thuộc client riêng của mô hình; hai mô hình dùng được cùng tên vai mà không chung quyền.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Hai mô hình cùng khai báo vai `MANAGER` | Mô hình thứ hai bị từ chối | Cả hai áp dụng được; người giữ `MANAGER` của mô hình A không có quyền ở mô hình B |
| Mô hình bỏ một vai | Vai và người giữ còn ở Keycloak, mô hình khác khai báo lại thì thừa hưởng | Vai còn trong client của mô hình, không mô hình nào khác thừa hưởng được |
| Engine so quyền | Theo tên vai trong `realm_access` | Theo vai của người gọi trong client của mô hình đó |
| Danh sách người xử lý | Người giữ vai realm cùng tên | Người giữ client role của mô hình |

**Hướng làm**

- `dx-core`: `CurrentUserService` thêm hàm đọc vai của người gọi theo `clientId` từ `resource_access`; `KeycloakRoleConverter` giữ `realm_access` cho vai realm như `ADMIN`.
- `dx-core`: `KeycloakAdminClient` thêm tạo client (bỏ qua nếu đã có), tạo vai dưới client, người giữ vai của client, vai của một người dùng theo client.
- `dx-engine`: lớp `ModelClient` đặt tên client của mô hình; `ApplyService` thêm bước `ENSURE_CLIENT` trước các bước `ENSURE_ROLE`, vai tạo dưới client; bỏ chốt chặn trùng vai giữa các mô hình, và bỏ khóa dùng chung giữa các lần áp dụng (chỉ còn khóa theo từng mô hình).
- `sso/realm-dxos.json`: tài khoản dịch vụ của backend thêm `manage-clients`, `view-clients`, `query-clients`, vì tạo client và vai lúc áp dụng cần các quyền này.
- `dx-engine`: `DataService` (quyền theo thực thể, người xử lý, quy tắc hành động, danh sách người xử lý) dùng vai của người gọi trong client của mô hình.
- `deploy/dev`: `kc.py` có hàm gán vai theo client; các script đổi cách tạo người dùng thử.
- Frontend: nhãn tiếng Việt cho bước mới ở lịch sử áp dụng.

**Phạm vi ảnh hưởng**

- File chính: `KeycloakRoleConverter`, `CurrentUserService`, `KeycloakAdminClient`, `ApplyService`, `DataService`, `deploy/dev/kc.py` và các script `check_*.py`.
- Bảng DB: không đổi.
- Role và client: client mới `dxos-m-<tên mô hình>` cho mỗi mô hình; `ADMIN` và luồng OTP vẫn là vai realm.
- Tương thích ngược: bản 2.0.0 chưa triển khai; stack dev dựng lại bằng `reset.sh`.

**Kết quả đầu ra**

- Áp dụng hai mô hình trùng tên vai thành công và quyền không chung.
- Tám script ở `deploy/dev` đạt sau khi đổi cách gán vai.

**Phụ thuộc, lưu ý**: Q1 đến Q4 ở `00_OVERVIEW.md` mục 9; rủi ro R1 đến R3 ở `04_TRADEOFFS_RISKS.md`.

## WP13-02: Tab Người dùng, bước 1

- Mục tham chiếu: phần 6 của `BACKLOG.md`, dòng "Tab Người dùng trong bảng điều khiển".
- Mục tiêu: quản trị viên xem người dùng, thấy ai giữ vai nào ở từng ứng dụng, gán và gỡ vai và đơn vị.

**Hướng làm**

- API ADMIN mỏng ở `dx-engine` (gói `users`), gọi Admin REST API của Keycloak; việc Keycloak đã làm tốt để lại cho Keycloak và mở bằng liên kết.
- Chỉ đổi vai của mô hình và đơn vị; vai realm (`ADMIN`) không có đường nào qua API.
- Vai phải do phiên bản đang chạy của mô hình khai báo, đơn vị phải do một mô hình đang chạy khai báo; không thì 404.
- Giao diện: tab "Người dùng" ở `src/console`, danh sách có tìm kiếm và phân trang, chi tiết người dùng chia vai theo ứng dụng.

**Hợp đồng dự kiến**

| Loại | Đối tượng | Mô tả |
|---|---|---|
| Endpoint | `GET /api/v1/users?search&page&size` | Danh sách: `id`, tên đăng nhập, họ tên, email, trạng thái |
| Endpoint | `GET /api/v1/users/options` | Các vai và đơn vị mà mô hình đang chạy khai báo; chỉ những thứ này mới gán được |
| Endpoint | `GET /api/v1/users/{id}` | Chi tiết: vai theo mô hình và đơn vị |
| Endpoint | `PUT` và `DELETE /api/v1/users/{id}/roles/{model}/{role}` | Gán và gỡ vai của mô hình |
| Endpoint | `PUT` và `DELETE /api/v1/users/{id}/units/{unit}` | Gán và gỡ đơn vị |

**Kết quả đầu ra**

- Tab hiện đúng vai theo ứng dụng của người dùng thử; gán và gỡ vai làm token cấp sau đó đổi theo.

## WP13-03: Tab Người dùng, bước 2

- Mục tiêu: vòng đời tài khoản không cần rời bảng điều khiển.

**Hợp đồng dự kiến**

| Loại | Đối tượng | Mô tả |
|---|---|---|
| Endpoint | `POST /api/v1/users` | Tạo người dùng; mật khẩu tạm sinh ngẫu nhiên khi không gửi, trả về một lần |
| Endpoint | `PUT /api/v1/users/{id}/enabled` | Khóa và mở |
| Endpoint | `POST /api/v1/users/{id}/password` | Đặt mật khẩu tạm mới |

- ADMIN không khóa được chính mình.
- Mật khẩu tạm bắt đổi ở lần đăng nhập đầu và không xuất hiện trong log hay audit.

## WP13-04: Ghi vết thay đổi tài khoản

- Mục tiêu: biết ai đã đổi gì về tài khoản, bằng bản ghi không sửa được.
- Hướng làm: `UserAdminService` ghi một bản ghi audit sau mỗi thay đổi thành công; người thực hiện, địa chỉ IP, mã yêu cầu và đường dẫn do `AuditService` lấy từ token và yêu cầu.
- Tám hành động: `USER_CREATED`, `USER_LOCKED`, `USER_UNLOCKED`, `USER_PASSWORD_RESET`, `USER_ROLE_GRANTED`, `USER_ROLE_REVOKED`, `USER_UNIT_ADDED`, `USER_UNIT_REMOVED`.
- Đối tượng là mã người dùng; chi tiết có tên đăng nhập và mô hình và vai, hoặc đơn vị. Không bao giờ ghi mật khẩu.
- Tra cứu bằng `GET /api/v1/audit-logs` có sẵn.
- Sự kiện quản trị của Keycloak đã bật, giữ nguyên; kiểm rằng chúng không chứa mật khẩu tạm.

## WP13-05: Kiểm chứng và tài liệu

- Chạy verify, chuỗi e2e và Chrome theo [`03_TEST_PLAN.md`](./03_TEST_PLAN.md).
- Cập nhật tài liệu sau khi người dùng OK kết quả.

## Thay đổi hợp đồng

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Keycloak | Client `dxos-m-<mô hình>` | Mới, bearer-only, chứa vai của mô hình | Có: vai của mô hình không còn là vai realm |
| Token | `resource_access.<client>.roles` | Engine đọc vai ở đây | Có với người gọi tự đọc vai realm của mô hình |
| Endpoint | `/api/v1/users...` | Mới, chỉ ADMIN | Không |
| Bước áp dụng | `ENSURE_CLIENT` | Mới, trước `ENSURE_ROLE` | Không |
| Keycloak realm | Tài khoản dịch vụ `dxos-backend-admin` | Thêm `manage-clients`, `view-clients`, `query-clients` | Có với realm đã import: phải cấp tay, vì realm chỉ import một lần |

## Dữ liệu, migration và rollback

- Migration: không có.
- Dữ liệu cũ: stack dev dựng lại bằng `reset.sh`; vai realm do bản trước tạo còn nằm vô hại.
- Realm đã import: cấp `manage-clients`, `view-clients`, `query-clients` của `realm-management` cho tài khoản `service-account-dxos-backend-admin` ở Admin Console; thiếu thì áp dụng mô hình báo Keycloak từ chối (403) ở bước `ENSURE_CLIENT`.
- Rollback: hoàn tác commit; client và vai đã tạo ở Keycloak còn lại, không ảnh hưởng.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Cách ly bằng client role, một client cho mỗi mô hình | Đúng khái niệm Keycloak, thử nghiệm WP13-00 sạch |
| D2 | Tab Người dùng làm hai bước | Xem và gán vai trước, vòng đời tài khoản sau |
| D3 | API chỉ đổi vai của mô hình và đơn vị | `ADMIN` không cấp được qua tab |
| D4 | Đơn vị vẫn là nhóm Keycloak dùng chung | Đơn vị là cơ cấu tổ chức, đã quyết ở BACKLOG |
| D5 | Ghi vết bằng sự kiện audit ở backend, giữ sự kiện quản trị đã bật của Keycloak | Backend biết người quản trị thật, Keycloak chỉ thấy tài khoản dịch vụ; bảng audit chỉ thêm |
| D6 | Bỏ khóa áp dụng dùng chung giữa các mô hình | Khóa đó chỉ để hai mô hình không cùng qua kiểm tra vai trùng; còn khóa theo từng mô hình |
| D7 | Thêm quyền client cho tài khoản dịch vụ của backend | Tạo client và vai lúc áp dụng cần quyền này |
| D8 | Mật khẩu tạm do hệ thống sinh, hiện một lần | Người quản trị không phải nghĩ mật khẩu; không ghi ở log và audit |
