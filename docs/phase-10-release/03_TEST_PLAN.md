# Kế hoạch kiểm thử: Phase 10

Các case chạy trên một bản clone sạch của nhánh phase, không dùng thư mục làm việc hiện tại.

## 1. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T10-01 | WP10-01 | Tĩnh | Tìm tệp của bản cũ: `data/`, trọng số, Redis, Langfuse, kho HaUI | Không còn trong cây mã | Claude, 03/11/2026 | Đạt: không còn `data/`, trọng số, PDF, `ai/evaluation` |
| T10-02 | WP10-02 | Đơn vị | `pytest` của `ai/` | Đạt | Claude, 03/11/2026 | Đạt: 21 test trong container |
| T10-03 | WP10-02 | Chạy thật | Nạp một thư mục tài liệu mẫu vào `ai/` rồi tìm kiếm | Trả đoạn kèm nguồn; câu ngoài kho báo không có bằng chứng | Claude, 03/11/2026 | Đạt: nạp 3 tài liệu vào pgvector, câu đúng chủ đề trả đúng tài liệu, "giá vàng hôm nay" và mã `ABC123` không có bằng chứng |
| T10-04 | WP10-03 | Tĩnh | Tìm `1.1.1` ngoài lịch sử CHANGELOG và tham chiếu tag | Không còn | Claude, 03/11/2026 | Đạt: không còn ngoài lịch sử CHANGELOG, tài liệu phase và tham chiếu tag |
| T10-05 | WP10-04 | Tĩnh | `check_licenses.py` | 0 vấn đề, gồm image, mô hình, dữ liệu | Claude, 03/11/2026 | Đạt: 0 vấn đề, gồm danh mục image, mô hình, tệp |
| T10-06 | WP10-04 | Tĩnh | Mọi thành phần trong SBOM có giấy phép | Đạt | Claude, 03/11/2026 | Đạt: SBOM backend 221, web 203, ai 59 thành phần, đều có giấy phép |
| T10-07 | WP10-06 | Tĩnh | Đọc lại workflow phát hành và `install.sh` so với cây mã | Không còn nhắc thành phần đã gỡ | Claude, 03/11/2026 | Đạt: sửa `update.sh` (còn kéo service `ai`) và `ci-ai.yml` (còn đường dẫn `data/`); `release.yml` giữ nguyên |
| T10-08 | WP10-07 | Biên dịch | `./mvnw clean verify`, `npx tsc --noEmit`, `check_routes.py` | Đạt | Claude, 03/11/2026 | Đạt: `clean verify`, `tsc`, `check_routes` trên bản sao sạch |
| T10-09 | WP10-07 | E2E | Dựng stack rỗng từ clone sạch, chạy `check_api` đến `check_demo` | Đạt như bản trước | Claude, 03/11/2026 | Đạt: 51, 17, 71, 59, 56, 44, 26 |
| T10-10 | WP10-07 | Giao diện | Chrome: đăng nhập, bảng điều khiển, một hồ sơ qua quy trình | Đạt | Claude, 03/11/2026 | Đạt: đăng nhập, bảng điều khiển, thư viện mẫu; hồ sơ qua quy trình kiểm bằng `check_demo.py` |

## 2. Hồi quy

Chuỗi `check_api.py`, `check_recovery.py`, `check_case.py`, `check_change.py`, `check_process.py`, `check_booking.py`, `check_demo.py`.

## 3. Bằng chứng

Kết quả ghi vào cột "Kết quả" và `docs/TESTING.md`.
