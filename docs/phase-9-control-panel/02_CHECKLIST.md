# Checklist: Phase 9

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP9-01: Họ chiếm dụng tài nguyên theo thời gian

- [x] WP9-01-DOC-01 Mô tả khối `booking:` trong JSON Schema và README.
- [x] WP9-01-CODE-01 Họ `reservation` và BPMN.
- [x] WP9-01-CODE-02 `ModelParser` đọc và kiểm `booking`.
- [x] WP9-01-CODE-03 Kiểm tra xung đột khi tạo và khi sửa, khóa dòng tài nguyên.

## WP9-02: API cho bảng điều khiển

- [x] WP9-02-CODE-01 `schema`, `families`, `templates`, `versions`.

## WP9-03: Mẫu mô hình

- [x] WP9-03-CODE-01 Chuyển mẫu vào `templates/` kèm `index.json`; cập nhật script và tài liệu.
- [x] WP9-03-CODE-02 Mẫu hỗ trợ kỹ thuật và đặt phòng.

## WP9-04: Bảng điều khiển, khung và soạn mô hình

- [x] WP9-04-CODE-01 Tuyến `/console`, vai ADMIN, nạp chậm.
- [x] WP9-04-CODE-02 Thư viện mẫu.
- [x] WP9-04-CODE-03 Trình soạn Monaco theo schema.
- [x] WP9-04-CODE-04 Form có hướng dẫn.

## WP9-05: Bảng điều khiển, kế hoạch và lịch sử

- [x] WP9-05-CODE-01 Màn hình kế hoạch và áp dụng.
- [x] WP9-05-CODE-02 Lịch sử áp dụng và quay về.
- [x] WP9-05-CODE-03 Sơ đồ quy trình.

## WP9-06: Chế độ xem lịch

- [x] WP9-06-CODE-01 Lịch cho thực thể thuộc họ `reservation`.

## WP9-07: Một cửa sinh viên và E2E

- [x] WP9-07-CODE-01 Mẫu Một cửa sinh viên có tệp minh chứng và hạn.
- [x] WP9-07-TEST-01 `check_demo.py` theo tám tiêu chí.

## WP9-08: Kiểm chứng

- [x] WP9-08-TEST-01 Chạy bộ kiểm trên stack sạch.
- [x] WP9-08-TEST-02 Kiểm giao diện bằng Chrome.

## Đóng phase

- [x] `./mvnw clean verify`, `npx tsc --noEmit`, `check_licenses.py`, `check_routes.py` đạt và đã đọc lại diff.
- [ ] CI trên GitHub: chưa chạy vì chưa push.
- [x] Swagger khớp mã; comment ngắn, bằng tiếng Anh.
- [x] Đã cập nhật `ARCHITECTURE.md`, `CHANGELOG.md`, `README.md`, `README_EN.md`, `backend/README.md`, `frontend/README.md`, `docs/TESTING.md`, `docs/dynamic-core/README.md`, `docs/dynamic-core/BACKLOG.md`, `COPYRIGHT.txt` và `sbom/frontend.cdx.json`.
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 4 có bằng chứng.
