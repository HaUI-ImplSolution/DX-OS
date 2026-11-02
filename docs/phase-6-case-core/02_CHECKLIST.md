# Checklist: Phase 6

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP6-00: Thử công cụ cho Phase 7

- [x] WP6-00-TEST-01 Thử `ProcessMigrationService` với hồ sơ đang chạy.
- [x] WP6-00-TEST-02 Thử pgschema: thêm cột, đổi tên, thêm `NOT NULL` khi dữ liệu vi phạm.
- [x] WP6-00-TEST-03 Thử pgroll với cùng ba tình huống.
- [x] WP6-00-DOC-01 Ghi kết luận vào `04_TRADEOFFS_RISKS.md`.

## WP6-01: Mẫu quy trình

- [x] WP6-01-BE-01 [model] `ProcessTemplate` khai báo trạng thái và hành động.
- [x] WP6-01-BE-02 [engine] BPMN có nhánh trả lại và nộp lại.
- [x] WP6-01-BE-03 [engine] Cột trạng thái và kiểm tra sửa xóa theo mẫu.
- [x] WP6-01-BE-04 [engine] Endpoint hành động thay endpoint quyết định.

## WP6-02: Đơn vị và quyền theo dòng

- [x] WP6-02-BE-01 [model] `units`, kiểu `unit`, phạm vi `unit` và `assigned`, kiểm tra hợp lệ, JSON Schema.
- [x] WP6-02-BE-02 [core] `UserContext.groups()`; thao tác nhóm trong `KeycloakAdminClient`.
- [x] WP6-02-BE-03 [engine] Bước áp dụng tạo nhóm Keycloak.
- [x] WP6-02-BE-04 [engine] Điều kiện dòng hợp mọi phạm vi, cả qua thực thể cha.
- [x] WP6-02-INFRA-01 Mapper `groups` trong realm và script kiểm thử.

## WP6-03: Giao việc và hộp việc

- [x] WP6-03-BE-01 [engine] Cột `assignee`, endpoint giao việc và danh sách người xử lý.
- [x] WP6-03-BE-02 [engine] Chỉ người được giao thực hiện được hành động.
- [x] WP6-03-BE-03 [engine] Endpoint hộp việc.

## WP6-04: Dòng lịch sử

- [x] WP6-04-DATA-01 [engine] Migration `engine.record_event`.
- [x] WP6-04-BE-01 [engine] Ghi sự kiện cùng giao dịch khi tạo, sửa, xóa, giao việc, thực hiện hành động.
- [x] WP6-04-BE-02 [engine] Endpoint đọc dòng lịch sử.

## WP6-05: Giao diện

- [x] WP6-05-FE-01 Metadata mới, trạng thái và bộ lọc từ mẫu.
- [x] WP6-05-FE-02 Nút hành động, hộp thoại ghi chú.
- [x] WP6-05-FE-03 Nhận việc và giao việc.
- [x] WP6-05-FE-04 Dòng lịch sử trên màn chi tiết.
- [x] WP6-05-FE-05 Hộp việc là trang đầu.
- [x] WP6-05-FE-06 Trường đơn vị chọn từ danh sách của mô hình.

## WP6-06: Mô hình mẫu và kiểm chứng

- [x] WP6-06-DATA-01 `models/onestop.yaml` và các tệp sai mới.
- [x] WP6-06-TEST-01 `check_api.py` chuyển sang API hành động; `check_case.py` cho phần mới.
- [x] WP6-06-TEST-02 Chạy trên stack sạch, kiểm giao diện, ghi kết quả.

## Đóng phase

- [~] `./mvnw clean verify`, `npx tsc --noEmit` đạt và đã đọc lại diff; CI chạy khi nhánh được đẩy lên.
- [x] Swagger khớp mã; comment ngắn, bằng tiếng Anh.
- [x] Cập nhật `ARCHITECTURE.md`, `CHANGELOG.md`, `docs/TESTING.md`, `docs/dynamic-core/BACKLOG.md`.
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 4 có bằng chứng.
