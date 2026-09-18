# Plan thực hiện: Phase C0

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WPC0-01 → WPC0-02; WPC0-03 làm song song được.

## WPC0-01: Chốt các quyết định mở

- Mục tham chiếu: quyết định RD8 đến RD10 ở `README.md`.
- Mục tiêu: có kết luận cho Q1 đến Q6 trước khi vào C1.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Tên package, module | Chưa quyết định | Ghi vào `MODULE_MAP.md` và `README.md` |
| Trạng thái workflow | Enum cố định | Ghi lựa chọn A hoặc B vào RD10 |

**Hướng làm**

- Trình bày mỗi câu hỏi với lựa chọn, đề xuất và hệ quả; người dùng chốt.
- Core/App: không có thay đổi code.

**Phạm vi ảnh hưởng**

- Module: không.
- File chính: `refactor/README.md`, `MODULE_MAP.md`, mục 9 của `00_OVERVIEW.md`.
- Endpoint, bảng DB, biến môi trường: không.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Q1, Q2, Q3, Q6 có kết luận; Q4, Q5 có kết luận hoặc hạn chốt rõ.

**Phụ thuộc, lưu ý**: Q3 quyết định độ lớn của C2.

## WPC0-02: Hoàn thiện bản đồ module và danh mục interface

- Mục tham chiếu: đề thi HaUI mục 4.1.
- Mục tiêu: mỗi file hiện có thuộc đúng một module đích; mỗi interface có Farm và Lab cài thế nào.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Phân loại file | Phân loại theo thư mục, 144 file ngoài `core/` | Mỗi file có module đích trong `MODULE_MAP.md` |

**Hướng làm**

- Rà từng file trong `service`, `config`, `security`, `controller`, `dto`, `model`, `exception` còn mơ hồ, ghi vào bảng "Điểm còn mơ hồ".

**Phạm vi ảnh hưởng**

- File chính: `MODULE_MAP.md`, `SPI_CATALOG.md`.
- Module, endpoint, bảng DB, biến môi trường: không.

**Kết quả đầu ra**

- Không còn file nào chưa có module đích; mọi điểm mơ hồ có phase xử lý.

**Phụ thuộc, lưu ý**: cần WPC0-01 cho tên package.

## WPC0-03: Ghi baseline

- Mục tham chiếu: đề thi HaUI mục 12 (demo không chạy bị trừ điểm).
- Mục tiêu: có số liệu trước refactor để đối chiếu sau C1, C2.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| So sánh hành vi | Không có mốc | Mốc: số test, số bước E2E, ảnh chụp OpenAPI |

**Hướng làm**

- Ghi số test backend, AI, số bước E2E hiện đạt (backend 79, AI 64, E2E 10/10 tại `64dc2bc`).
- Xuất `/v3/api-docs` của backend hiện tại để so sánh sau C1 (người dùng chạy).

**Phạm vi ảnh hưởng**

- File chính: mục 5 của `03_TEST_PLAN.md`.
- Module, endpoint, bảng DB, biến môi trường: không.

**Kết quả đầu ra**

- Có đường dẫn lưu OpenAPI baseline và số liệu test ghi trong `03_TEST_PLAN.md`.

**Phụ thuộc, lưu ý**: cần backend chạy được trên máy người dùng.

## Thay đổi hợp đồng (API, schema, cấu hình)

Không có.

## Dữ liệu, migration và rollback

- Migration: không.
- Dữ liệu cũ: không.
- Rollback: xóa nhánh docs.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Phase C0 không đổi code | Giữ nhánh code sạch cho C1 |
