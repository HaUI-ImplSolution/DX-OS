# Checklist: Phase 7

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP7-00: Kiểm bốn điều kiện của pgschema

- [x] WP7-00-TEST-01 Chạy với bảng có khóa ngoại, `UNIQUE`, `CHECK`, chỉ mục, quyền `report_ro`.
- [x] WP7-00-TEST-02 Áp dụng lại sau lỗi giữa chừng, cả khi về đích cũ.
- [x] WP7-00-TEST-03 Kế hoạch xem trước khớp áp dụng thật khi có đổi tên.
- [x] WP7-00-TEST-04 Đổi tên trường có `UNIQUE`, khóa ngoại, chỉ mục thì kế hoạch rỗng, dữ liệu còn đủ.
- [x] WP7-00-DOC-01 Ghi kết luận vào `04_TRADEOFFS_RISKS.md`.

## WP7-01: Trạng thái đích qua pgschema

- [x] WP7-01-BE-01 [engine] Sinh trạng thái đích, có thể giữ tên cũ khi xem trước.
- [x] WP7-01-BE-02 [engine] `SchemaTool` lập kế hoạch JSON và áp dụng kế hoạch.
- [x] WP7-01-INFRA-01 Image backend kèm pgschema; cập nhật `COPYRIGHT.txt`.

## WP7-02: So sánh mô hình và phân loại

- [x] WP7-02-BE-01 [model] `renamed_from`, `default`, kiểm tra hợp lệ, JSON Schema.
- [x] WP7-02-BE-02 [engine] `ModelDiff` phân loại thay đổi, kể cả từ chối.
- [x] WP7-02-BE-03 [engine] Liệt kê dòng vi phạm và hồ sơ đang xử lý.

## WP7-03: Xem trước và áp dụng

- [x] WP7-03-BE-01 [engine] Phản hồi `plan` mới.
- [x] WP7-03-BE-02 [engine] `apply` kiểm `base`, `accept`, đối chiếu thao tác xóa.
- [x] WP7-03-BE-03 [engine] Bước đổi tên chạy lại được, đổi cả ràng buộc và chỉ mục.
- [x] WP7-03-BE-04 [engine] Áp dụng dở: quy tắc tệp khác theo việc đã đổi tên hay chưa.

## WP7-04: Quay lui

- [x] WP7-04-BE-01 [engine] Endpoint quay lui.

## WP7-05: Kiểm chứng

- [x] WP7-05-TEST-01 `check_change.py`; cập nhật hai script cũ.
- [x] WP7-05-TEST-02 Chạy trên stack sạch, kiểm giao diện sau khi đổi mô hình.

## Đóng phase

- [x] `./mvnw clean verify`, `npx tsc --noEmit`, CI đạt và đã đọc lại diff.
- [x] Swagger khớp mã; comment ngắn, bằng tiếng Anh.
- [x] Cập nhật `ARCHITECTURE.md`, `CHANGELOG.md`, `README.md`, `README_EN.md`, `docs/TESTING.md`, `docs/dynamic-core/BACKLOG.md`.
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 4 có bằng chứng.
