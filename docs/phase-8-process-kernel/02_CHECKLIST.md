# Checklist: Phase 8

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP8-01: Họ mẫu có tham số

- [x] WP8-01-DOC-01 Mô tả khối `process:` mới trong JSON Schema, `README.md`, `README_EN.md`, `ARCHITECTURE.md`.
- [x] WP8-01-CODE-01 `ProcessFamily`, `ProcessDefinition`, hành động xuất phát từ nhiều trạng thái.
- [x] WP8-01-CODE-02 `ModelParser` đọc `family`, `handler`, `allow_self_decision`, `deadline`; báo lỗi đúng dòng.
- [x] WP8-01-CODE-03 Họ `approval` và họ `work_item` cùng BPMN.
- [x] WP8-01-CODE-04 Đổi `models/*.yaml`, script `deploy/dev` và metadata cho khớp.

## WP8-02: Quy tắc bất biến

- [x] WP8-02-CODE-01 `ProcessRules` gom kiểm tra vai, tự quyết, người nhận việc, lý do.
- [x] WP8-02-CODE-02 Từ chối, trả lại, rút lại bắt buộc có lý do.
- [x] WP8-02-CODE-03 Hành động `withdraw` giữ lại bản ghi; xóa bản ghi có quy trình bị từ chối.
- [x] WP8-02-CODE-04 Giao diện nhận `from` là danh sách và hiện nút rút lại.

## WP8-03: Quy trình trong kế hoạch áp dụng

- [x] WP8-03-CODE-01 `ModelDiff` so sánh họ mẫu, người xử lý, hạn, quyền tự quyết.
- [x] WP8-03-CODE-02 Kiểm lúc áp dụng: người xử lý là vai có thật và có quyền xem bản ghi (bộ đọc mô hình).
- [x] WP8-03-CODE-03 Kế hoạch báo số bản ghi đang chạy bị đụng tới.

## WP8-04: Họ công việc cần xử lý có hạn

- [x] WP8-04-CODE-01 Cột `handle_by`, tính khi vào bước chờ người xử lý.
- [x] WP8-04-CODE-02 `overdue` trong dữ liệu trả về; hộp việc đưa hồ sơ quá hạn lên đầu.
- [x] WP8-04-CODE-03 Giao diện đánh dấu hồ sơ quá hạn.

## WP8-05: Trục sự kiện và hành động hoàn tất

- [x] WP8-05-CODE-01 Bảng outbox và bộ giao có thử lại.
- [x] WP8-05-CODE-02 Thao tác đăng ký sẵn cho hành động hoàn tất, có khóa chống chạy lặp.
- [x] WP8-05-CODE-03 Sự kiện quá hạn.

## WP8-06: Mô hình cấp phát dịch vụ IT

- [x] WP8-06-CODE-01 Tệp mô hình trong `models/`.
- [x] WP8-06-TEST-01 Duyệt xong thì Keycloak cấp quyền thật.

## WP8-07: Đổi họ mẫu khi có hồ sơ đang chạy

- [-] WP8-07-CODE-01 Bản ghi lưu phiên bản mô hình lúc bắt đầu: hoãn, xem backlog.
- [-] WP8-07-CODE-02 Bảng ánh xạ trạng thái trong kế hoạch: hoãn, xem backlog.

## WP8-08: Tệp đính kèm

- [x] WP8-08-CODE-01 Kiểu trường `file` trong định dạng mô hình và DDL.
- [x] WP8-08-CODE-02 Tải lên, tải xuống, quét ClamAV, quyền theo dòng.
- [x] WP8-08-CODE-03 Trường tệp trong form và chi tiết.

## WP8-09: Kiểm chứng

- [x] WP8-09-TEST-01 `check_process.py` mới; các script cũ đã cập nhật.
- [x] WP8-09-TEST-02 Chạy trên stack sạch, kiểm giao diện.

## Đóng phase

- [x] `./mvnw clean verify`, `npx tsc --noEmit`, `check_licenses.py`, `check_routes.py` đạt và đã đọc lại diff.
- [ ] CI trên GitHub: chưa chạy vì chưa push.
- [x] Swagger khớp mã (`@Operation` trên `FileController` và `InboxController`); comment ngắn, bằng tiếng Anh.
- [x] Đã cập nhật `ARCHITECTURE.md`, `CHANGELOG.md`, `README.md`, `README_EN.md`, `backend/README.md`, `frontend/README.md`, `docs/TESTING.md`, `docs/dynamic-core/README.md`, `docs/dynamic-core/BACKLOG.md`.
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 4 có bằng chứng (`03_TEST_PLAN.md`).
