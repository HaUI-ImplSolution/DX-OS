# Kế hoạch kiểm thử: Phase 4

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống (sinh mã, ràng buộc duyệt, poka-yoke), không coverage đầy đủ happy path cho UI.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP4-01/03/04; thủ công trên thiết bị thật cho WP4-02 (GPS, offline); thủ công trên web cho WP4-05/06.
- Môi trường: clone sạch + seed demo đã có danh mục giống.
- Dữ liệu và tài khoản test: 1 hộ chưa có thửa nào; admin1, engineer1.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T4-01 | WP4-01 | Đồng thời | 2 request tạo thửa đồng thời cùng hộ | Mã sinh ra không trùng, cả 2 thửa tồn tại với STT khác nhau | | |
| T4-02 | WP4-01 | Poka-yoke | Tạo vụ trùng `(thửa, năm, loại vụ)` | 409 | | |
| T4-03 | WP4-01 | Âm tính | Tạo thửa thiếu `latitude`/`longitude` | 400 | | |
| T4-04 | WP4-02 | Offline | Tạo thửa khi tắt mạng, mở mạng lại | Request tự gửi đúng 1 lần, không trùng khi bấm gửi lại | | |
| T4-05 | WP4-02 | Poka-yoke | Lấy GPS giả lập sai số vượt 30m | FE cảnh báo; backend vẫn từ chối nếu client cố gửi qua | | |
| T4-06 | WP4-03 | Poka-yoke | Tạo vụ trên thửa chưa duyệt | 403/422 | | |
| T4-07 | WP4-03 | Happy path | Admin duyệt thửa, farmer tạo vụ ngay sau | Tạo vụ thành công | | |
| T4-08 | WP4-03 | Poka-yoke | Farmer tạo yêu cầu chờ duyệt thứ 4 (Q3 = 3) | Bị chặn | | |
| T4-09 | WP4-04 | Âm tính | Tạo vụ với `riceVarietyCode` không tồn tại | 400 | | |
| T4-10 | WP4-04 | Poka-yoke | `expectedHarvestDate` lệch quá khoảng, không có `reasonForOverride` | 400 | | |
| T4-11 | WP4-05 | Happy path | Admin vào `/admin/requests` | Danh sách đúng, có thông tin hộ | | |
| T4-12 | WP4-05 | Happy path | Engineer vào `/engineer/queue` | Danh sách đúng việc được giao | | |
| T4-13 | WP4-06 | Happy path | Tạo yêu cầu thửa mới | Thông báo vào webhook admin, không lẫn channel báo cáo | | |

## 3. Hồi quy (không được hỏng)

- [ ] Khóa PHI (P1) vẫn chặn đúng.
- [ ] Phạm vi dữ liệu theo `Household` (P2) không đổi.
- [ ] `WorkflowCase`/`WorkflowEvent` của luồng chẩn đoán (P3) không bị ảnh hưởng khi thêm `caseType` mới.
- [ ] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T4-01 đến T4-13 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test, ảnh chụp màn hình mobile (GPS, offline) và web (trang quản lý).
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
