# Kế hoạch kiểm thử: Phase 8

Các case chạy bằng script ở `deploy/dev` trên stack sạch, có dữ liệu thật, và kiểm giao diện.

## 1. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T8-01 | WP8-01 | Happy path | Áp dụng mô hình dùng `family: approval` rồi `family: work_item` | Cả hai chạy, không sửa mã lõi | Claude, 03/11/2026 | Đạt |
| T8-02 | WP8-01 | Âm tính | Họ không tồn tại, tham số lạ, `deadline` sai định dạng | Từ chối, báo đúng dòng và tham số | Claude, 03/11/2026 | Đạt |
| T8-03 | WP8-02 | Âm tính | Từ chối, trả lại, mở lại không có lý do | 422, không đổi trạng thái | Claude, 03/11/2026 | Đạt |
| T8-04 | WP8-02 | Happy path | Người nộp rút lại hồ sơ đang chờ | Trạng thái `withdrawn`, bản ghi còn, lịch sử có lý do | Claude, 03/11/2026 | Đạt |
| T8-05 | WP8-02 | Âm tính | Xóa bản ghi có quy trình | Bị từ chối, bản ghi còn | Claude, 03/11/2026 | Đạt |
| T8-06 | WP8-02 | Âm tính | Người tạo tự duyệt; người ngoài đơn vị duyệt | Bị từ chối như trước | Claude, 03/11/2026 | Đạt |
| T8-07 | WP8-03 | Happy path | Đổi hạn và người xử lý khi có hồ sơ | Kế hoạch an toàn, báo số hồ sơ đang chạy | Claude, 03/11/2026 | Đạt |
| T8-08 | WP8-04 | Happy path | Tạo hồ sơ `work_item` có hạn ngắn, chờ qua hạn | `overdue` bật, hộp việc đưa lên đầu | Claude, 03/11/2026 | Đạt |
| T8-09 | WP8-05 | Dữ liệu | Duyệt xong rồi gây lỗi sau giao dịch | Hành động hoàn tất chạy đúng một lần | Claude, 03/11/2026 | Đạt: một thông điệp, thử lại tới khi nhóm có thật; lỗi sau quyết định nằm ở T6-14 |
| T8-10 | WP8-06 | Happy path | Gửi và duyệt yêu cầu cấp quyền | Người dùng vào nhóm Keycloak | Claude, 03/11/2026 | Đạt |
| T8-11 | WP8-08 | Âm tính | Tải tệp nhiễm mã độc | Bị chặn, hồ sơ không gắn tệp | Claude, 03/11/2026 | Đạt phần gắn, tải, gỡ, quyền theo bản ghi; tệp nhiễm mã độc do `FileStoreServiceTest` kiểm (từ chối, ghi audit, không lưu), chưa chạy E2E với profile `av` |

## 2. Hồi quy

`check_api.py`, `check_case.py`, `check_change.py`, `check_recovery.py` chạy lại sau khi cập nhật theo định dạng mới.

## 3. Bằng chứng

Kết quả ghi vào cột "Kết quả" và `docs/TESTING.md`.
