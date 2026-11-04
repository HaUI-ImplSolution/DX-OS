# Kế hoạch kiểm thử: Phase 13

Quy ước dự án: kiểm bằng compile, `verify`, chuỗi e2e trên stack tạm, thử trên giao diện thật khi cần; không viết file test mới nếu không có yêu cầu, trừ việc mở rộng các script e2e đã có.

## 1. Chiến lược

- Mức kiểm thử: tích hợp qua API trên stack rỗng (script ở `deploy/dev`), thủ công bằng Chrome cho tab Người dùng.
- Môi trường: stack tạm dựng riêng (project Compose khác, cổng khác, dữ liệu trong thư mục tạm), không đụng stack dev.
- Dữ liệu và tài khoản test: người dùng thử do script tạo; mỗi mô hình thử có client và vai riêng.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T13-01 | WP13-01 | Happy path | Áp dụng mô hình A và mô hình B cùng khai báo vai `MANAGER` | Cả hai áp dụng được; có client `dxos-m-a` và `dxos-m-b` | Claude, 04/11 | Đạt |
| T13-02 | WP13-01 | Phân quyền | Người chỉ giữ `MANAGER` của A gọi API dữ liệu của B | 403 | Claude, 04/11 | Đạt |
| T13-03 | WP13-01 | Phân quyền | Người giữ `MANAGER` của A gọi API dữ liệu của A | Đúng quyền theo mô hình A | Claude, 04/11 | Đạt |
| T13-04 | WP13-01 | Ranh giới | Mô hình bỏ một vai, mô hình khác khai báo vai cùng tên | Áp dụng được; người giữ vai cũ không vào được mô hình mới | Claude, 04/11 | Đạt theo T12-02: vai nằm trong client của từng mô hình; kịch bản bỏ vai rồi dùng lại chưa chạy riêng |
| T13-05 | WP13-01 | Phục hồi | Áp dụng khi Keycloak dừng giữa bước tạo client, rồi tiếp tục | Hoàn tất, client và vai tạo một lần | Claude, 04/11 | Đạt |
| T13-06 | WP13-02 | Happy path | Danh sách người dùng có tìm kiếm và phân trang | Đúng người, đúng trang | Claude, 04/11 | Đạt |
| T13-07 | WP13-02 | Happy path | Gán và gỡ vai của mô hình cho một người | Token cấp sau đó đổi theo; tab hiện đúng | Claude, 04/11 | Đạt |
| T13-08 | WP13-02 | Âm tính | Gán vai không có trong mô hình, đơn vị không có, người dùng không có | 404 | Claude, 04/11 | Đạt |
| T13-09 | WP13-02 | Phân quyền | Người không phải ADMIN gọi API người dùng; gán `ADMIN` qua đường vai của mô hình | 403; 404 vì mô hình không khai báo được `ADMIN` | Claude, 04/11 | Đạt |
| T13-10 | WP13-03 | Happy path | Tạo người dùng với mật khẩu tạm | Chưa đăng nhập được bằng mật khẩu tạm cho đến khi đổi (Keycloak báo tài khoản chưa thiết lập xong) | Claude, 04/11 | Đạt |
| T13-11 | WP13-03 | Happy path | Khóa rồi mở một người dùng; đặt lại mật khẩu | Khóa thì không đăng nhập được; mở thì đăng nhập được; mật khẩu tạm mới phải đổi trước khi đăng nhập | Claude, 04/11 | Đạt |
| T13-12 | WP13-03 | Âm tính | ADMIN khóa chính mình; mật khẩu tạm có trong log | 409; không có trong log | Claude, 04/11 | Đạt |
| T13-13 | WP13-04 | Ghi vết | Gán và gỡ vai, thêm và bớt đơn vị, tạo, khóa, mở, đặt lại mật khẩu; rồi tra `GET /api/v1/audit-logs` | Mỗi thay đổi có một bản ghi: người thực hiện là ADMIN, đối tượng là người dùng, chi tiết có tên đăng nhập và vai hoặc đơn vị; không có mật khẩu; thao tác không xảy ra thì không có bản ghi | Claude, 04/11 | Đạt (kèm sửa: ghi rõ schema `public` cho `audit_log`) |
| T13-14 | WP13-04 | Bảo mật | Đọc sự kiện quản trị của Keycloak sau các thao tác trên | Có sự kiện của các thay đổi; không có mật khẩu tạm | Claude, 04/11 | Đạt |

Cách chạy: T13-01 đến T13-04 bằng `check_guards.py` (mã T12-01 đến T12-04) cùng các script cũ dùng chung tên vai; T13-05 bằng `check_recovery.py` (T5-05, bước lỗi là `ENSURE_CLIENT`) và `check_guards.py` (T12-12 đến T12-22); T13-06 đến T13-14 bằng `check_users.py`.

## 3. Hồi quy (không được hỏng)

- [x] Chín script ở `deploy/dev` đạt trên stack rỗng, sau khi đổi cách gán vai (428/428).
- [x] Đăng nhập SSO qua Keycloak ở web và vai `ADMIN` vào được bảng điều khiển (Chrome).
- [ ] OTP có điều kiện của `ADMIN` khi bật `STAFF_OTP_FLOW` không đổi (chưa thử).
- [x] Quy trình: người xử lý nhận hồ sơ, giao việc, hộp việc đúng người (`check_case.py`, `check_process.py`).
- [x] Áp dụng dở rồi Tiếp tục hoặc Hủy bỏ vẫn chạy với bước tạo client (`check_guards.py`).
- [ ] Danh sách mô hình, lịch sử áp dụng, thẻ "Đang dừng dở" vẫn hiện đúng.

## 4. Tiêu chí đạt của phase

- Mọi case ở mục 2 đạt, không còn lỗi nghiêm trọng mở.
- Lỗi còn lại đã ghi ở `docs/dynamic-core/BACKLOG.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng

- Ghi audit: trước khi sửa, mọi lần ghi và đọc `audit_log` ở stack thử đều lỗi `relation "audit_log" does not exist` (bảng nằm ở `public`, kết nối không thấy); entity ghi rõ `schema = "public"` thì ghi và đọc được, `check_users.py` đạt 39/39 và chạy lại vẫn sạch.
- Kết quả (04/11/2026): `./mvnw clean verify` và `npx tsc --noEmit` đạt; chín script ở `deploy/dev` đạt 428/428 (51, 17, 71, 65, 56, 44, 26, 59, 39) trên stack rỗng dựng riêng, chạy theo thứ tự của `docs/TESTING.md` rồi `check_users.py`; bảng `audit_log` ghi 41 bản ghi (2 từ chối, 30 lỗi, 9 thành công), không lỗi ghi; tab Người dùng thử bằng Chrome (danh sách, tìm kiếm, gán vai, đặt lại mật khẩu, tạo người dùng).
- Ghi vào `docs/TESTING.md` mục 8 khi đóng phase.
