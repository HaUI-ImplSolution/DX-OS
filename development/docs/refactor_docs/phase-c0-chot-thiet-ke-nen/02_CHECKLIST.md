# Checklist: Phase C0

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC0-{xx}-{lớp}-{nn}`.

## WPC0-01: Chốt các quyết định mở

Kết quả đầu ra: Q1, Q2, Q3, Q6 có kết luận; Q4, Q5 có kết luận hoặc hạn chốt.

**Spec và tài liệu**

- [x] WPC0-01-DOC-01 Chốt Q1 (tên package, artifact) và ghi vào `MODULE_MAP.md`.
- [x] WPC0-01-DOC-02 Chốt Q2 (schema DB) và ghi vào RD9.
- [x] WPC0-01-DOC-03 Chốt Q3 (trạng thái workflow) và ghi vào RD10.
- [x] WPC0-01-DOC-04 Chốt Q6 (đường dẫn API).
- [x] WPC0-01-DOC-05 Chốt Q4, Q5 hoặc ghi hạn chốt.
- [x] WPC0-01-DOC-06 Cập nhật bảng quyết định ở `README.md`.

## WPC0-02: Hoàn thiện bản đồ module và danh mục interface

Kết quả đầu ra: mọi file có module đích; mọi điểm mơ hồ có phase xử lý.

**Spec và tài liệu**

- [x] WPC0-02-DOC-01 Viết `MODULE_MAP.md`.
- [x] WPC0-02-DOC-02 Viết `SPI_CATALOG.md`.
- [x] WPC0-02-DOC-03 Rà các file `service`, `config`, `security` còn mơ hồ ở bảng "Điểm còn mơ hồ" và cập nhật.
- [x] WPC0-02-DOC-04 Đối chiếu `SPI_CATALOG.md` với quyết định RD10.

## WPC0-03: Ghi baseline

Kết quả đầu ra: số liệu test và OpenAPI baseline được lưu.

**Spec và tài liệu**

- [x] WPC0-03-DOC-01 Ghi số test và số bước E2E baseline vào `03_TEST_PLAN.md` mục 5.
- [x] WPC0-03-DOC-02 Người dùng xuất `/v3/api-docs` và lưu ở đường dẫn ghi trong mục 5.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC0-03-TEST-01 Đọc lại tài liệu theo TC0-01.

## Đóng phase

- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] `README.md` của `refactor_docs/` có trạng thái quyết định mới nhất.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
