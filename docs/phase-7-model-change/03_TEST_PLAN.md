# Kế hoạch kiểm thử: Phase 7

Các case chạy bằng script ở `deploy/dev` trên stack sạch, có dữ liệu thật, và kiểm giao diện.

## 1. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T7-01 | WP7-01 | Happy path | Áp dụng lần đầu; lập kế hoạch lại | Cùng một đường qua pgschema; `report_ro` đọc được; kế hoạch rỗng | Claude, 03/11/2026 | Đạt |
| T7-02 | WP7-03 | Happy path | Thêm trường tùy chọn khi có 1.000 dòng | Kế hoạch không đổi gì; thiếu hoặc sai `base` bị 409; API và metadata thấy ngay | Claude, 03/11/2026 | Đạt |
| T7-03 | WP7-03 | Dữ liệu | Đổi tên trường có `UNIQUE`, khóa ngoại, chỉ mục | Kế hoạch chỉ gồm đổi tên; thiếu `accept` bị 409; dữ liệu và ràng buộc còn nguyên | Claude, 03/11/2026 | Đạt |
| T7-04 | WP7-02 | Âm tính | Thêm bắt buộc, duy nhất, bỏ lựa chọn, trường bắt buộc mới khi dữ liệu vi phạm | Từ chối kèm dòng vi phạm; không đổi gì, không sót ràng buộc | Claude, 03/11/2026 | Đạt |
| T7-05 | WP7-02 | Dữ liệu | Trường bắt buộc mới có `default` | Dòng cũ và mới đều có giá trị | Claude, 03/11/2026 | Đạt |
| T7-06 | WP7-02 | Âm tính | Đổi kiểu trường | Từ chối kèm giải thích, cột không đổi | Claude, 03/11/2026 | Đạt |
| T7-07 | WP7-03 | Poka-yoke | Quên `renamed_from`; xóa trường | Mức `LOSS`, kế hoạch hỏi có phải đổi tên không; chỉ chạy với `accept=LOSS` | Claude, 03/11/2026 | Đạt |
| T7-08 | WP7-02 | Happy path | Đổi cấu hình quy trình khi có hồ sơ đang chờ | Kế hoạch báo số hồ sơ đang xử lý | Claude, 03/11/2026 | Đạt |
| T7-09 | WP7-04 | Happy path | Quay về phiên bản trước | Có kế hoạch; kiểm `base`; ghi thành phiên bản mới | Claude, 03/11/2026 | Đạt |
| T7-10 | WP7-04 | Dữ liệu | Quay về khi có đổi tên | Kế hoạch đảo đổi tên; dữ liệu về tên cũ | Claude, 03/11/2026 | Đạt |
| T7-11 | WP7-03 | Lỗi dịch vụ ngoài | Áp dụng dừng sau khi đã đổi tên vì Keycloak dừng | Tệp khác bị 409; đúng tệp đó hoàn tất | Claude, 03/11/2026 | Đạt |
| T7-12 | WP7-05 | Giao diện | Đổi tên và thêm trường rồi tải lại trang | Thấy ngay, dữ liệu cũ còn, tạo được bản ghi mới, không build lại | Claude, 03/11/2026 | Đạt |

## 2. Hồi quy

- [x] `check_api.py`, `check_recovery.py`, `check_case.py` đạt.
- [x] `./mvnw clean verify`, `npx tsc --noEmit`.
- [x] Luồng giao diện của Phase 6 đạt.

## 3. Bằng chứng

Chạy ngày 03/11/2026 trên stack sạch dựng từ cây làm việc: `check_api.py` 51/51, `check_recovery.py` 17/17, `check_case.py` 70/70, `check_change.py` 59/59, luồng giao diện 20/20 và 5/5.
Luồng giao diện chạy bằng script tạm trên Chrome không giao diện, không nằm trong repo.
