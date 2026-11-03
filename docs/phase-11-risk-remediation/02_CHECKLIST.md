# Checklist: Phase 11

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP11-00: Hợp đồng guard và apply

- [ ] WP11-00-DOC-01 Chốt maintenance/reconcile signal bằng bảng apply hiện có.
- [ ] WP11-00-DOC-02 Chốt chặn process rule khi còn hồ sơ mở.
- [ ] WP11-00-DOC-03 Chốt chặn `DROP_ENTITY` khi còn dependency.

## WP11-01: Collision role và unit

- [ ] WP11-01-CODE-01 So sánh role/unit của model mới với toàn bộ model đã áp dụng.
- [ ] WP11-01-CODE-02 Từ chối collision trước khi gọi Keycloak.
- [ ] WP11-01-CODE-03 Audit collision của dữ liệu hiện có.

## WP11-02: Maintenance gate và phục hồi apply

- [ ] WP11-02-CODE-01 Đặt model vào maintenance trong lúc apply.
- [ ] WP11-02-CODE-02 Reload registry trước khi ghi apply `done`.
- [ ] WP11-02-CODE-03 Khi khởi động lại với apply dở, giữ model ở maintenance.
- [ ] WP11-02-CODE-04 Chỉ thêm resume endpoint nếu retry hiện tại không đủ.

## WP11-03: Chặn process rule khi còn hồ sơ mở

- [ ] WP11-03-CODE-01 Nâng handler, deadline, completion, booking và process rule thành migration/rejected khi `openRecords` khác rỗng.
- [ ] WP11-03-CODE-02 Giữ Flowable definition và variables hiện tại trong lúc hồ sơ còn mở.
- [ ] WP11-03-TEST-01 Xác minh capability `ProcessMigrationService` độc lập, chưa tích hợp vào apply.

## WP11-04: Dependency guard cho DROP_ENTITY

- [ ] WP11-04-CODE-01 Đếm process instance và attachment rows trước `DROP_ENTITY`.
- [ ] WP11-04-CODE-02 Từ chối drop nếu còn dependency.
- [ ] WP11-04-DOC-01 Ghi rõ history/audit/outbox chưa bị dọn trong phase này.

## WP11-05: Kiểm chứng và tài liệu

- [ ] WP11-05-TEST-01 Chạy test plan cho collision guard.
- [ ] WP11-05-TEST-02 Chạy test plan cho apply failure, retry và reconcile.
- [ ] WP11-05-TEST-03 Chạy test plan cho process rule change khi còn hồ sơ mở.
- [ ] WP11-05-TEST-04 Chạy test plan cho DROP_ENTITY dependency guard.
- [ ] WP11-05-DOC-01 Cập nhật Swagger, ARCHITECTURE và BACKLOG sau review.

## Đóng phase

- [ ] Mọi mục DoD trong `00_OVERVIEW.md` có bằng chứng.
- [ ] Phần BI/report không bị thay đổi trong phase này.
- [ ] Không commit hoặc push trước khi người dùng review.
