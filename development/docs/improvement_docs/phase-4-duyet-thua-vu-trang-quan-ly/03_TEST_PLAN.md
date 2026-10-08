# Kế hoạch kiểm thử: Phase 4

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống (sinh mã, ràng buộc duyệt, poka-yoke), không coverage đầy đủ happy path cho UI.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP4-01/03/04; thủ công trên thiết bị thật cho WP4-02 (GPS, offline); thủ công trên web cho WP4-05/06.
- Môi trường: clone sạch + seed demo đã có danh mục giống.
- Dữ liệu và tài khoản test: 1 hộ chưa có thửa nào; admin1, engineer1.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T4-01 | WP4-01 | Đồng thời | 2 request tạo thửa đồng thời cùng hộ | Mã sinh ra không trùng, cả 2 thửa tồn tại với STT khác nhau | | Đạt: 2 request đồng thời trả `T02` và `T03` (API thật, 06/10/2026) |
| T4-02 | WP4-01 | Poka-yoke | Tạo vụ trùng `(thửa, năm, loại vụ)` | 409 | | Đạt: vụ thứ hai trên thửa trả 409 |
| T4-03 | WP4-01 | Âm tính | Tạo thửa thiếu `latitude`/`longitude` | 400 | | Đạt: 400 thiếu `latitude`, `longitude` |
| T4-04 | WP4-02 | Offline | Tạo thửa khi tắt mạng, mở mạng lại | Request tự gửi đúng 1 lần, không trùng khi bấm gửi lại | | Đạt (iPhone, chế độ máy bay: thửa và vụ tự gửi đúng một lần, không trùng) |
| T4-05 | WP4-02 | Poka-yoke | Lấy GPS giả lập sai số vượt 30m | FE cảnh báo; backend vẫn từ chối nếu client cố gửi qua | | Đạt (iPhone: sai số lớn khóa nút gửi; backend `422 GPS_ACCURACY_TOO_LOW`) |
| T4-06 | WP4-03 | Poka-yoke | Tạo vụ trên thửa chưa duyệt | 403/422 | | Đạt: 422 `PARCEL_NOT_APPROVED` |
| T4-07 | WP4-03 | Happy path | Admin duyệt thửa, farmer tạo vụ ngay sau | Tạo vụ thành công | | Đạt |
| T4-08 | WP4-03 | Poka-yoke | Farmer tạo yêu cầu chờ duyệt thứ 4 (Q3 = 3) | Bị chặn | | Đạt: yêu cầu thứ 4 trả 422 `TOO_MANY_PENDING_REQUESTS` |
| T4-09 | WP4-04 | Âm tính | Tạo vụ với `riceVarietyCode` không tồn tại | 400 | | Đạt: 400 |
| T4-10 | WP4-04 | Poka-yoke | `expectedHarvestDate` lệch quá khoảng, không có `reasonForOverride` | 400 | | Đạt: 400 kèm yêu cầu `reasonForOverride` |
| T4-11 | WP4-05 | Happy path | Admin vào `/admin/requests` | Danh sách đúng, có thông tin hộ | | Đạt trên web (admin1): danh sách, thông tin hộ, duyệt, timeline |
| T4-12 | WP4-05 | Happy path | Engineer vào `/engineer/queue` | Danh sách đúng việc được giao | | Đạt: API `assignedTo=me` và `unassigned`; web hiện thẻ chẩn đoán kèm ảnh |
| T4-13 | WP4-06 | Happy path | Tạo yêu cầu thửa mới | Thông báo vào webhook admin, không lẫn channel báo cáo | | Đạt (thông báo thửa và vụ vào `ban-quan-tri`, báo cáo vào `ky-su-nong-nghiep`) |

## 3. Hồi quy (không được hỏng)

- [x] Khóa PHI (P1) vẫn chặn đúng. Bộ test hiện có (58 case) đạt.
- [x] Phạm vi dữ liệu theo `Household` (P2) không đổi.
- [x] `WorkflowCase`/`WorkflowEvent` của luồng chẩn đoán (P3) không bị ảnh hưởng khi thêm `caseType` mới. Duyệt chẩn đoán qua endpoint chung trả 409, vẫn qua endpoint riêng.
- [x] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [x] Dựng từ clone sạch bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T4-01 đến T4-13 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Kết quả chạy

Ngày 06/10/2026, 39 bước trên web (admin1, engineer1) và iPhone (tài khoản nông hộ tạo trên web), mọi bước đạt:

- T4-03, T4-05: thiếu GPS khóa nút gửi, sai số lớn cảnh báo và khóa.
- T4-06, T4-07, T4-09, T4-10, T4-02: vụ mùa chỉ tạo được trên thửa đã duyệt, chọn giống từ danh mục, ngày thu hoạch lệch phải có lý do, vụ thứ hai bị chặn.
- T4-08: yêu cầu chờ duyệt thứ 4 bị chặn.
- T4-11, T4-12: trang `/requests` và `/queue`, kỹ sư không duyệt thửa và vụ.
- Từ chối, sửa và gửi lại; yêu cầu chỉnh sửa thửa đã duyệt; thao tác xóa của admin trên giao diện.
- Hồi quy: báo cáo offline, thu hoạch, bộ 58 test tự động.
- Lỗi Mattermost ở lần chạy đầu do URL webhook dùng `localhost` thay vì `mattermost:8065`; sau khi sửa, T4-13 đạt.

## 6. Bằng chứng và báo lỗi

- Bằng chứng: log test, ảnh chụp màn hình mobile (GPS, offline) và web (trang quản lý).
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
