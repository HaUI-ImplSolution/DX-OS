# Kế hoạch kiểm thử: Phase 8

Phần lớn phase này là kiểm tra/tổng hợp, không viết nhiều test mới ngoài E2E.
Theo D6 (phase 0): test vẫn chỉ tập trung case ảnh hưởng logic/an toàn hệ thống; E2E là bổ sung bắt buộc riêng theo mục 18.

## 1. Chiến lược

- Mức kiểm thử áp dụng: E2E tự động trong CI (WP8-01); thủ công cho WP8-02/03/04/05/06 (dữ liệu, hồ sơ, portal, kịch bản demo).
- Môi trường: clone sạch từ tag đã public (nếu Q1 đã chốt và thực hiện).
- Dữ liệu và tài khoản test: toàn bộ tài khoản demo đã có từ các phase trước (farmer1, engineer1, admin1).

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T8-01 | WP8-02 | Đầy đủ | Chạy script seed demo trên database sạch | Có đủ hộ/thửa/vụ/báo cáo theo đúng mô tả ở `01_PLAN.md` | | |
| T8-02 | WP8-01 | E2E | Chạy job E2E trong CI | Chuỗi tạo hộ → thửa → vụ → báo cáo → chẩn đoán → duyệt → phun → thu hoạch bị chặn chạy đúng, không lỗi | | |
| T8-03 | WP8-03 | Bảo mật | Chạy công cụ quét secret trên toàn bộ lịch sử commit trước khi chuyển public | Không còn secret thật nào, false positive đã được allowlist có lý do | | Đạt (gitleaks, 253 commit, 07/10/2026) |
| T8-04 | WP8-05 | Happy path | Dừng 1 service (ví dụ AI), mở portal | Portal hiện đúng trạng thái "down" cho service đó | | |
| T8-05 | WP8-05 | Đầy đủ | Gọi API gây lỗi khóa PHI từ FE | FE hiện thông báo tiếng Việt, không lộ message tiếng Anh thẳng từ backend | | |

## 3. Hồi quy (không được hỏng)

- [ ] Toàn bộ test case đã viết ở P1-P7 vẫn pass sau các thay đổi của P8 (SPDX header, errorCode, README).
- [ ] Dựng từ clone sạch (từ tag nếu đã có) bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy, lặp lại được 2 lần liên tiếp bằng script reset (P1).
- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động.

## 4. Tiêu chí đạt của phase (exit criteria)

- T8-01 đến T8-05 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Checklist PoF ở `02_CHECKLIST.md` đã tick đủ (WP8-03, WP8-04).
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log CI (job E2E), kết quả quét secret, video rehearsal kịch bản demo, checklist PoF đã tick.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).

## 6. Hướng dẫn chạy tay

Chạy trên stack mới dựng: `cd deploy && ./reset.sh --yes`, rồi từ gốc repo:

**T8-01 (dữ liệu demo)**

1. `python3 -m pip install pillow && python3 deploy/demo/seed_demo.py`.
2. Đăng nhập web bằng `admin1`: trang Nông hộ có hộ gắn `farmer1` và hai hộ `demo.hogd2`, `demo.hogd3`; hàng chờ duyệt có một thửa An Giang.
3. Đăng nhập `engineer1`: có một chẩn đoán chờ duyệt.
4. Chạy lại lệnh bước 1: in "Demo data already present", không tạo thêm.

**T8-02 (E2E)**

1. `python3 deploy/demo/e2e.py`: mọi bước `PASS`, thoát mã 0 (không có rule thì bước cuối kiểm nhánh `review_required`).
2. Trên GitHub: chạy workflow `E2E` (`workflow_dispatch`) và đọc log.

**T8-04 (portal)**

1. Mở `http://localhost:9080/portal/`, cuối trang các dịch vụ hiện "hoạt động".
2. `docker compose stop ai`, đợi tối đa 30 giây: dòng "AI chẩn đoán" chuyển "ngừng".
3. `docker compose start ai`.

**T8-05 (lỗi tiếng Việt)**

1. Trên app mobile bằng `farmer1`, khai thu hoạch một vụ trước ngày dự kiến mà không xác nhận; hoặc gọi `POST /api/v1/crop-seasons/{id}/harvest` bằng token `farmer1`.
2. App hiển thị thông báo tiếng Việt (`HARVEST_TOO_EARLY`); response JSON có `errorCode`.
3. Gọi `POST /api/v1/parcels` bằng `engineer1`: `403` với `errorCode` `ERR_FORBIDDEN`.
