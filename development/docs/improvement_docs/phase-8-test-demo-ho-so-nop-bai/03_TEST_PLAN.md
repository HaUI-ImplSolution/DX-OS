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
| T8-01 | WP8-02 | Đầy đủ | Chạy script seed demo trên database sạch | Có đủ hộ/thửa/vụ/báo cáo theo đúng mô tả ở `01_PLAN.md` | | Đạt (stack cô lập, chạy lại không tạo trùng, 07/10/2026) |
| T8-02 | WP8-01 | E2E | Chạy job E2E trong CI | Chuỗi tạo hộ → thửa → vụ → báo cáo → chẩn đoán → duyệt → phun → thu hoạch bị chặn chạy đúng, không lỗi | | Đạt (10/10 bước trên stack cô lập với rule CI; CI GitHub do người dùng xác nhận đạt) |
| T8-03 | WP8-03 | Bảo mật | Chạy công cụ quét secret trên toàn bộ lịch sử commit trước khi chuyển public | Không còn secret thật nào, false positive đã được allowlist có lý do | | Đạt (gitleaks, 253 commit, 07/10/2026) |
| T8-04 | WP8-05 | Happy path | Dừng 1 service (ví dụ AI), mở portal | Portal hiện đúng trạng thái "down" cho service đó | | Đạt (dừng `ai`, dòng "AI chẩn đoán" chuyển "ngừng" sau chu kỳ làm mới 30 giây, các dịch vụ khác giữ "hoạt động"; 07/10/2026) |
| T8-05 | WP8-05 | Đầy đủ | Gọi API gây lỗi khóa PHI từ FE | FE hiện thông báo tiếng Việt, không lộ message tiếng Anh thẳng từ backend | | Đạt (do người dùng xác nhận; phía API đã kiểm `errorCode` ở E2E) |

## 3. Hồi quy (không được hỏng)

- [x] Toàn bộ test case đã viết ở P1-P7 vẫn pass sau các thay đổi của P8: `./mvnw verify` 78/78, `pytest` AI 48/48, test của `data/scripts` đạt (07/10/2026).
- [x] Dựng sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy, lặp lại được 2 lần liên tiếp (stack cô lập gồm postgres, minio, keycloak, backend, ai, frontend, portal, apisix, dữ liệu xóa giữa hai lần; chưa chạy `reset.sh` và chưa dựng từ tag).
- [x] Đăng nhập SSO qua Keycloak: web (`admin1`) và Superset (vào thẳng bằng phiên dùng chung) đạt trên stack đang chạy; mobile do người dùng xác nhận đạt.

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
