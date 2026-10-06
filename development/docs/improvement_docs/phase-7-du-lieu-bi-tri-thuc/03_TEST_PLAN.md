# Kế hoạch kiểm thử: Phase 7

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống (quyền đọc dữ liệu), còn lại là kiểm tra thủ công trên BI/UI.

## 1. Chiến lược

- Mức kiểm thử áp dụng: thủ công qua `psql`/Superset UI cho WP7-01/02; integration (JUnit) cho WP7-04/05; thủ công trên web cho WP7-05.
- Môi trường: clone sạch + seed demo có bản ghi cả `APPROVED` và chưa duyệt.
- Dữ liệu và tài khoản test: ít nhất 1 thửa `APPROVED` và 1 thửa `PENDING`; admin1.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T7-01 | WP7-01 | Bảo mật dữ liệu | Kết nối `psql -U superset_ro`, `SELECT * FROM parcels` | Bị từ chối quyền | | |
| T7-02 | WP7-01 | Bảo mật dữ liệu | `SELECT * FROM vw_parcels_approved` bằng `superset_ro` | Thành công, chỉ thấy thửa `APPROVED` | | |
| T7-03 | WP7-02 | Hồi quy | Dựng lại Superset từ clone sạch (`--force-recreate superset`) | Dashboard hiện đủ 7 chart, không chart nào nhân đôi | | |
| T7-04 | WP7-04 | Happy path | Gọi `GET /parcels/{code}/weather` cho thửa có GPS | Trả dữ liệu thời tiết thật | | |
| T7-05 | WP7-04 | Dự phòng | Giả lập Open-Meteo timeout/lỗi | Trả snapshot kèm `isSnapshot = true`, không 500 | | |
| T7-06 | WP7-05 | Happy path | Nhập câu hỏi về VietGAP ở trang `/knowledge` | Trả lời kèm ít nhất 1 nguồn trích dẫn | | |

## 3. Hồi quy (không được hỏng)

- [ ] Backend (ứng dụng chính, không phải `superset_ro`) vẫn đọc/ghi đầy đủ bảng gốc như trước.
- [ ] Dashboard cũ (bản đồ, pie vụ mùa) vẫn hiển thị đúng sau khi đổi dataset sang view.
- [ ] Đăng nhập SSO Superset qua Keycloak vẫn hoạt động.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T7-01 đến T7-06 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: ảnh chụp dashboard, log `psql` xác nhận quyền, ảnh chụp màn hỏi đáp có trích nguồn.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
