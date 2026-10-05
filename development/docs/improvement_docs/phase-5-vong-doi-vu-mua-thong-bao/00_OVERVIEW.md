# Phase 5: Vòng đời vụ mùa và thông báo cho farmer

| Mục | Giá trị |
|---|---|
| Trạng thái | Chờ Q1 (cần kỹ sư xác nhận thực tế canh tác), Q2 đã chốt |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ước lượng | 4-5 ngày |
| Người phụ trách | {điền tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Phase 4 |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 4 | Luồng xử lý ở block vụ mùa | Quan trọng | Toàn bộ |
| mục 10 | Lỗ hổng nghiệp vụ / poka-yoke | Nghiêm trọng | Một phần: farmer tự khai "đã phun" |
| mục 13 | Thông báo | Quan trọng | Một phần: hộp thông báo trong app (D5) |
| mục 15 | Mobile | Quan trọng | Một phần: màn hình khai báo thu hoạch, hộp thông báo |

## 2. Lý do

- Vấn đề đang gặp: `CropSeasonService.harvest(Long id)` không nhận tham số nào ngoài `id`, chỉ set `status = HARVESTED` và `harvestedAt = Instant.now()` — không cảnh báo thu hoạch sớm, không lưu sản lượng/diện tích thu hoạch, không ghi ai thực hiện.
  Không có job định kỳ nào nhắc farmer khi đến ngày dự kiến mà chưa thu hoạch.
  Người xác nhận "đã phun thuốc" hiện là kỹ sư (`DiagnosisController` giới hạn `ENGINEER`/`ADMIN`), nhưng thực tế farmer là người phun — kỹ sư không đứng ở ruộng, dữ liệu phun thuốc phụ thuộc vào việc kỹ sư có hỏi lại farmer hay không.
- Vì sao làm ở phase này: đây là tính năng nghiệp vụ nối tiếp P4 (vòng đời vụ mùa sau khi đã tạo/duyệt), và là nơi dữ liệu sản lượng/diện tích thu hoạch bắt đầu được thu thập — làm muộn thì mất luôn cơ hội có dữ liệu thật cho carbon/năng suất sau này (mục 24, để sau).

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Scheduler, hộp thông báo, đổi luồng `harvest`/`apply` |
| Frontend (web) | Có | Cảnh báo khi admin thao tác thay farmer |
| Mobile | Có | Màn hình thu hoạch, hộp thông báo, farmer tự khai đã phun |
| AI | Không | |
| Data / Seed / Migration | Có | Migration cột mới trên `CropSeason`, `TreatmentApplication`; bảng `Notification` |
| Infra / Deploy / CI | Không | |
| Docs / PoF | Có | README, CHANGELOG |

## 4. Phạm vi

- Trong phạm vi: WP5-01 đến WP5-05 ở `01_PLAN.md` (khai báo thu hoạch có dữ liệu thật, job nhắc hạn, hộp thông báo, farmer tự khai đã phun, admin thao tác thay farmer có log rõ).
- Ngoài phạm vi (không làm ở phase này): push thật qua FCM/ntfy (P10, D5 đã chốt pull trước); nhiều lần apply/phun lặp lại trên cùng chẩn đoán (chưa chốt, xem câu hỏi mở).
- Giả định: D5 (phase 0, hộp thông báo GET + PATCH đánh dấu đã đọc) đã chốt; `Clock`/`ZoneId` thống nhất (P1) đã có sẵn để scheduler dùng đúng múi giờ.

## 5. Ưu điểm và hạn chế

- Ưu điểm: bắt đầu có dữ liệu sản lượng/diện tích thu hoạch thật; farmer nhận được phản hồi ở mọi mốc quan trọng thay vì phải tự mở app kiểm tra.
- Hạn chế: đổi quy trình phun thuốc (farmer tự khai thay kỹ sư) ảnh hưởng trách nhiệm, cần thống nhất với kỹ sư trước khi code.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (farmer tự khai đã phun có thể khai sai/khai khống, scheduler chạy sai múi giờ).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: đến ngày dự kiến thu hoạch mà chưa khai báo, farmer nhận thông báo trong hộp thông báo và chốt lại ngày mới; farmer bấm thu hoạch sớm phải xác nhận kèm lý do; farmer tự khai đã phun, kỹ sư xác nhận lại trước khi khóa PHI có hiệu lực chính thức; admin thao tác thay farmer có log "thao tác thay mặt" rõ ràng.
- Sản phẩm bàn giao: scheduler nhắc hạn, bảng `Notification`, API GET/PATCH hộp thông báo, luồng `apply` 2 bước (farmer khai → engineer xác nhận), luồng `harvest` có `confirmEarly`/sản lượng/diện tích.
- Tiêu chí đo được: bấm thu hoạch sớm không kèm `confirmEarly` bị từ chối; job nhắc hạn chạy đúng giờ theo `Asia/Ho_Chi_Minh`; farmer xem được thông báo mới trong hộp thông báo sau khi admin duyệt thửa/vụ (nối từ P4).
- Bằng chứng: log test T5-xx, ảnh chụp hộp thông báo trên mobile.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: `Clock` bean (P1) cho scheduler; thửa/vụ đã `APPROVED` (P4) để có dữ liệu thật mà khai báo thu hoạch.
- Phase sau cần từ phase này: P7 (BI) hiển thị sản lượng/diện tích thu hoạch thật lên Superset; mục 24 (carbon/AWD, để sau) cần đúng dữ liệu sản lượng đã thu thập ở đây.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: đề mục 4.1 (quy trình có trạng thái, lịch sử, theo dõi yêu cầu chờ — job nhắc hạn là một biểu hiện của "theo dõi yêu cầu chờ xử lý").
- Dùng làm bằng chứng cho: độ tin cậy của "khóa PHI" khi trả lời câu hỏi hội đồng về AI/nghiệp vụ (mục 11, 20 `PROJECT_IMPROVEMENTS.md`).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Một chẩn đoán có được áp dụng thuốc (`apply`) nhiều lần (phun lặp lại cách nhau vài ngày) không | Cho phép nhiều lần, khóa PHI theo lần phun mới nhất (đã là hành vi ngầm định hiện tại qua `findTopBy...OrderByHarvestLockedUntilDesc`) / Giữ đúng 1 lần/chẩn đoán như hiện tại | {ngày} | _(chưa chốt — cần kỹ sư xác nhận thực tế canh tác)_ |
| Q2 | Job nhắc hạn chạy với tần suất nào | 1 lần/ngày (buổi sáng) / Nhiều lần/ngày | 05/10/2026 | 1 lần/ngày buổi sáng — đủ cho nhu cầu nhắc hạn ngày, tránh làm phiền farmer |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger cho endpoint `apply`/`harvest` đổi shape, endpoint `notifications` mới.
