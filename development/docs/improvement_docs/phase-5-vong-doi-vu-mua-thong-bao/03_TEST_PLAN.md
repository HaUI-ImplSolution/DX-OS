# Kế hoạch kiểm thử: Phase 5

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống (poka-yoke thu hoạch, khóa PHI qua xác nhận 2 bước), không coverage đầy đủ happy path UI.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP5-01/02/04/05; thủ công trên mobile cho WP5-03.
- Môi trường: clone sạch + seed demo có 1 vụ `ACTIVE` gần tới `expectedHarvestDate`.
- Dữ liệu và tài khoản test: farmer1, engineer1, admin1; 1 chẩn đoán `APPROVED` chưa `apply`.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T5-01 | WP5-03 | Phân quyền dữ liệu | Farmer1 gọi `GET /notifications`, có thông báo của farmer2 trong DB | Chỉ thấy thông báo của chính mình | | Đạt: tài khoản khác không đọc hoặc đánh dấu được thông báo của hộ (`PATCH` trả 404, `GET` chỉ trả của mình) (API thật, 07/10/2026) |
| T5-02 | WP5-03 | Happy path | Đánh dấu đã đọc, gọi lại `GET` | Không còn trong danh sách chưa đọc | | Đạt: đánh dấu đã đọc, `unread-count` giảm; `read-all` về 0 |
| T5-03 | WP5-01 | Poka-yoke | Thu hoạch trước `expectedHarvestDate`, không có `confirmEarly` | 422 | | Đạt: 422 `HARVEST_TOO_EARLY` |
| T5-04 | WP5-01 | Happy path | Thu hoạch hợp lệ kèm `yieldKg`/`harvestedAreaHectares` | Lưu đúng, farmer nhận thông báo xác nhận | | Đạt: lưu `yieldKg`, diện tích, người khai, lý do sớm; khi admin khai thay hộ nhận thông báo `HARVEST_RECORDED` |
| T5-05 | WP5-02 | Poka-yoke | Chạy job nhắc hạn 2 lần trong cùng ngày trên cùng 1 vụ quá hạn | Chỉ tạo đúng 1 thông báo | | Đạt: job (cron mỗi phút khi thử) chạy 2 lần cùng ngày, vẫn đúng 1 thông báo |
| T5-06 | WP5-02 | Đầy đủ | Gia hạn ngày dự kiến 2 lần liên tiếp | 2 bản ghi lịch sử riêng biệt, không ghi đè | | Đạt: 2 dòng lịch sử, ngày cũ và mới nối tiếp nhau |
| T5-07 | WP5-04 | Poka-yoke | Farmer tự khai đã phun | Khóa PHI chưa có hiệu lực; case hiện trong danh sách chờ xác nhận của kỹ sư | | Đạt: bản khai `PENDING_CONFIRMATION`, hiện ở hàng đợi `TREATMENT_CONFIRMATION`, thu hoạch bị chặn 422 `TREATMENT_PENDING_CONFIRMATION` |
| T5-08 | WP5-04 | Happy path | Kỹ sư xác nhận bản khai của farmer | Khóa PHI có hiệu lực từ thời điểm xác nhận | | Đạt: sau xác nhận khóa đến hết ngày phun + PHI chặn thu hoạch (422 `POKA_YOKE_VIETGAP_VIOLATION`); khóa tính từ ngày phun nông dân khai |
| T5-09 | WP5-05 | Poka-yoke | Admin khai thu hoạch thay farmer, không kèm lý do | 400 | | Đạt: 400 `reasonOnBehalf is required` |
| T5-10 | WP5-05 | Đầy đủ | Admin khai thu hoạch thay farmer kèm lý do | Audit ghi rõ actor, hộ được thay, lý do | | Đạt: vụ lưu người khai và lý do, log backend ghi admin, hộ, lý do; nông dân nhận thông báo |

## 3. Hồi quy (không được hỏng)

- [x] Khóa PHI (P1) vẫn chặn đúng cho đường `apply` trực tiếp của kỹ sư (không qua self-report) (`DiagnosisServiceApplyTest`, `CropSeasonServiceTest`).
- [x] Phạm vi dữ liệu theo `Household` (P2) áp dụng đúng cho `Notification` (thông báo gắn `sub` của hộ).
- [x] `workflow_case`/`workflow_event` của luồng chẩn đoán và duyệt thửa/vụ (P3, P4) không bị ảnh hưởng khi thêm `caseType = TREATMENT_CONFIRMATION`.
- [x] Dựng từ clone sạch vẫn lên đủ dịch vụ healthy (`deploy/reset.sh`, V1 đến V7 chạy trên DB mới).

## 4. Tiêu chí đạt của phase (exit criteria)

- T5-01 đến T5-10 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Kết quả chạy

- Backend: `./mvnw test` 58 test đạt (đã cập nhật `CropSeasonServiceTest`, `DiagnosisServiceApplyTest`, `PermissionMatrixTest`, `ConcurrencyIntegrationTest`, `HouseholdMigrationTest` cho chữ ký và migration mới).
- Mobile `npx tsc --noEmit` và web `npx tsc -b` không lỗi.
- T5-01 đến T5-10 và các nhánh phụ (đổi ngày sai, diện tích vượt thửa, từ chối rồi khai lại, quyền của engineer và hộ khác) chạy bằng API thật qua gateway.
- Sau `deploy/reset.sh`, dữ liệu thử dựng bằng script và cả luồng thử lại trên điện thoại thật và web ngày 07/10/2026: thông báo, thu hoạch sớm và thay hộ, nhắc hạn và đổi ngày, khai phun và xác nhận đều đạt.
- Lỗi phát hiện khi thử trên điện thoại: kéo làm mới màn Thửa chưa cập nhật số thông báo chưa đọc; đã sửa, số này tải lại khi kéo làm mới và mỗi lần quay lại màn Thửa.
- Không thêm test tự động mới cho logic P5.

## 6. Bằng chứng và báo lỗi

- Bằng chứng: log test, ảnh chụp hộp thông báo trên mobile.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
