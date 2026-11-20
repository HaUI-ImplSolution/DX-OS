# Kế hoạch kiểm thử: ứng dụng mẫu cửa hàng điện máy

Quy ước dự án: mặc định chỉ compile, đọc diff.
Chạy test, khởi động app hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ; đợt làm này người dùng đã cho phép dựng stack thử cổng 9180, ghi ở cột "Người chạy".
Không thêm tệp test mới.

## 1. Chiến lược

- Mức kiểm thử áp dụng: thủ công theo README trên stack thử, kèm các script kiểm có sẵn (`check_conformance.py`, `check_licenses.py`).
- Môi trường: stack thử `dxscratch` cổng 9180, DX-OS bản đầy đủ, ứng dụng bằng compose riêng.
- Chạy lại với từng tệp mô hình mà đợt làm đụng tới: `retail` và `warranty`.
- Dữ liệu và tài khoản test: client `CUSTOMER` và `REQUESTER` do `dxos connect` ghi; tài khoản quản lý (`STORE_STAFF`) và kỹ thuật viên (`AGENT`) do script tạo; một nhân viên bán hàng của ứng dụng; vài sản phẩm và đơn bán mẫu.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T-01 | WP-01 | Happy path | `dxos lint` rồi `dxos plan` rồi `dxos apply` cho hai tệp mô hình | Áp dụng được vào hệ thống rỗng | | |
| T-02 | WP-01 | Ranh giới | Áp dụng lại cùng hai tệp; chạy `check_conformance.py retail` và `warranty` | Không làm gì; đạt | | |
| T-03 | WP-01 | Âm tính | `dxos connect` với vai không có trong mô hình | API từ chối, lệnh dừng | | |
| T-04 | WP-01 | Happy path | Chạy script tạo tài khoản; quản lý và kỹ thuật viên đăng nhập hộp việc | Đăng nhập được, hộp việc rỗng; sản phẩm đã nạp | | |
| T-05 | WP-02 | Happy path | Chạy ứng dụng riêng khi chưa có DX-OS; đăng nhập, xem đơn, yêu cầu | Ba màn hình hiện đúng | | |
| T-06 | WP-03 | Happy path | Bấm Đổi trả cho một đơn; quản lý mở `webUrl` và duyệt | Hồ sơ có `dxos_id`; trạng thái ứng dụng thành "Đã duyệt" | | |
| T-07 | WP-03 | Âm tính | Gửi webhook giả sai chữ ký tới ứng dụng | Từ chối, trạng thái không đổi | | |
| T-08 | WP-03 | Đồng thời | Gửi lại cùng webhook (trùng `eventId`) | Trạng thái không đổi hai lần | | |
| T-09 | WP-03 | Phân quyền | Client đổi trả gọi API bảo hành và ngược lại | 403 | | |
| T-10 | WP-03 | Happy path | Lập bảo hành, kỹ thuật viên nhận, hạn ngắn trôi qua | Webhook quá hạn về, ứng dụng hiện cảnh báo | | |
| T-11 | WP-03 | Lỗi dịch vụ ngoài | Tắt ứng dụng, đổi trạng thái hồ sơ, bật lại | Webhook thử lại và đến được; DX-OS không lỗi | | |
| T-12 | WP-04 | Ranh giới | Đổi mô hình bảo hành: `dxos plan` xem mức rủi ro, `apply`, quay về bản trước; `dxos history verify` | Có mức rủi ro; quay về được; lịch sử nguyên vẹn | | |
| T-13 | WP-04 | Happy path | Làm đúng từng bước README trên máy sạch | Duyệt xong một hồ sơ của mỗi quy trình, không bước nào ngoài README | | |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi mô hình)**

- [ ] Đăng nhập SSO qua Keycloak (web, API) vẫn hoạt động.
- [ ] Người dùng chỉ thấy hồ sơ mà quyền của vai cho phép.
- [ ] Trạng thái hồ sơ chỉ đổi qua quy trình, ghi đủ lịch sử.
- [ ] Áp dụng lại cùng tệp mô hình không làm gì.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` trong `deploy/` lên đủ dịch vụ healthy, kể cả khi có `extra_hosts` của backend.

**Theo mô hình**

- [ ] `python3 e2e/api/check_conformance.py retail` và `warranty` đạt.
- [ ] `python3 .github/scripts/check_licenses.py` không báo lỗi.

## 4. Tiêu chí đạt của đợt làm (exit criteria)

- Tất cả case T-01 đến T-13 đạt, không còn lỗi nghiêm trọng mở.
- Lỗi còn lại đã có issue.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log chạy theo README và ảnh chụp các màn hình chính, đính vào mô tả PR.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
