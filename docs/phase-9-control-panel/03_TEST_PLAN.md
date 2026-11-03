# Kế hoạch kiểm thử: Phase 9

Các case chạy bằng script ở `deploy/dev` trên stack sạch, có dữ liệu thật, và kiểm giao diện.

## 1. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T9-01 | WP9-01 | Âm tính | Khối `booking:` sai: thiếu trường, sai kiểu, sức chứa không dương | Từ chối, báo đúng dòng | Claude, 03/11/2026 | Đạt |
| T9-02 | WP9-01 | Happy path | Đặt phòng không trùng giờ | Chấp nhận, trạng thái chờ xác nhận | Claude, 03/11/2026 | Đạt |
| T9-03 | WP9-01 | Âm tính | Đặt trùng khung giờ khi đã đủ sức chứa; kết thúc trước bắt đầu | 409; 400 | Claude, 03/11/2026 | Đạt |
| T9-04 | WP9-01 | Dữ liệu | Lượt bị từ chối hoặc rút lại; sức chứa lớn hơn một | Giờ được đặt lại; đủ sức chứa mới chặn | Claude, 03/11/2026 | Đạt |
| T9-05 | WP9-01 | Đồng thời | Nhiều yêu cầu đặt cùng giờ cùng lúc | Không vượt sức chứa | Claude, 03/11/2026 | Đạt |
| T9-06 | WP9-02 | Happy path | Gọi `schema`, `families`, `templates`, `versions` bằng ADMIN và vai khác | Nội dung đúng; vai khác 403 | Claude, 03/11/2026 | Đạt |
| T9-07 | WP9-03 | Happy path | Áp dụng từng mẫu trong thư viện vào hệ thống rỗng | Mọi mẫu áp dụng được | Claude, 03/11/2026 | Đạt |
| T9-08 | WP9-04 | Giao diện | Người triển khai chọn mẫu, sửa bằng form và bằng trình soạn | YAML hai chế độ khớp nhau | Claude, 03/11/2026 | Đạt: sửa trực tiếp bằng trình soạn; YAML sai bị chặn ở cả hai chế độ; form đọc và ghi lại đúng tệp, giữ chú thích đầu tệp |
| T9-09 | WP9-05 | Giao diện | Xem kế hoạch của thay đổi an toàn, cần chuyển dữ liệu, mất dữ liệu | Hiện đúng mức; mất dữ liệu cần xác nhận riêng | Claude, 03/11/2026 | Đạt: thay đổi mất dữ liệu hiện đúng mức, hồ sơ bị ảnh hưởng và cần xác nhận riêng; kế hoạch quay về hiện đủ; mức cần chuyển dữ liệu kiểm bằng API (`check_demo.py`) |
| T9-10 | WP9-05 | Giao diện | Lịch sử áp dụng, quay về phiên bản trước, sơ đồ quy trình | Đúng từng bước; sơ đồ khớp họ mẫu | Claude, 03/11/2026 | Đạt: lịch sử có phiên bản và từng bước; sơ đồ đúng họ mẫu |
| T9-11 | WP9-06 | Giao diện | Lịch đặt phòng | Lượt đặt hiện đúng giờ và trạng thái | Claude, 03/11/2026 | Đạt: lịch tuần hiện lượt đặt đúng giờ, màu theo trạng thái |
| T9-12 | WP9-07 | E2E | Tám tiêu chí của kịch bản trình diễn | Cả tám đạt | Claude, 03/11/2026 | Đạt |

## 2. Hồi quy

`check_api.py`, `check_recovery.py`, `check_case.py`, `check_change.py`, `check_process.py` chạy lại sau khi chuyển mẫu mô hình.

## 3. Bằng chứng

Kết quả ghi vào cột "Kết quả" và `docs/TESTING.md`.
