# Kế hoạch kiểm thử: Phase C0

Phase chỉ có tài liệu nên kiểm tra bằng đọc lại.

## 1. Chiến lược

- Mức kiểm thử áp dụng: rà soát tài liệu, đối chiếu với code hiện tại.
- Môi trường: không cần dựng stack.
- Dữ liệu và tài khoản test: không.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC0-01 | WPC0-02 | Rà soát | Lấy ngẫu nhiên 10 file ở `service`, `config`, `security`, tra trong `MODULE_MAP.md` | Mỗi file có đúng một module đích | Khuong | Đạt |
| TC0-02 | WPC0-02 | Rà soát | Mỗi interface ở `SPI_CATALOG.md` có cách cài của Farm hoặc Lab | Không có interface chỉ một bên cài mà không ghi lý do | Khuong | Đạt |
| TC0-03 | WPC0-01 | Rà soát | Mỗi quyết định ở `README.md` có trạng thái | Không có quyết định bỏ trống | Khuong | Đạt |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [x] Không có file code nào đổi trong diff của phase.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC0-01 đến TC0-03 đạt.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Baseline (nhánh `Khuong/main` tại `304c810`, ngày 09/10/2026):
  - Backend `./mvnw verify`: 80/80 test đạt.
  - E2E `deploy/demo/e2e.py`: 10/10 bước đạt trên stack dựng sạch.
  - Seed Farm: `generate_seed_sql.py --check` thoát mã 0 (không in gì khi nhất quán); `unittest data.scripts.test_generate_seed_sql` 4/4 đạt.
  - AI `pytest`: 64 test đạt.
- OpenAPI baseline: [`openapi-baseline.json`](./openapi-baseline.json) (50 path, gồm `/api/v1/households`; chưa có `/api/v1/workspaces` và `/api/v1/app`).
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả".
