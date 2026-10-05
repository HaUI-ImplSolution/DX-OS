# Trade-off và rủi ro: Phase 5

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Farmer tự khai đã phun (WP5-04) | Farmer khai là có hiệu lực ngay / Farmer khai, kỹ sư xác nhận mới có hiệu lực | Kỹ sư xác nhận mới có hiệu lực | Giữ nguyên tắc dữ liệu quyết định khóa PHI phải qua người có trách nhiệm xác nhận; đúng thực tế hơn (farmer là người phun, kỹ sư xác nhận lại) | Thêm một bước chờ, khóa PHI không có hiệu lực ngay lúc farmer khai | Thấp — đổi lại thành có hiệu lực ngay chỉ là bỏ điều kiện `confirmationStatus`, không cần migration |
| TO2 | Lịch sử gia hạn ngày thu hoạch | Bảng riêng / Tái dùng `workflow_event` | Tái dùng `workflow_event` (nếu `caseType` phù hợp), có bảng riêng nếu không | Không tạo thêm bảng lịch sử thứ hai, nhất quán với pattern P3 | `workflow_event` phải đủ tổng quát để biểu diễn "gia hạn ngày" (không phải approve/reject thông thường) | Vừa — nếu không đủ tổng quát, phải tách bảng riêng sau, tốn một migration |

Kết luận: TO1-TO2 áp dụng ngay.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Farmer tự khai đã phun sai sự thật (khai khống để mở khóa thu hoạch sớm hơn) | Thấp-Vừa | Cao (ảnh hưởng trực tiếp độ tin cậy của khóa PHI — điểm nhấn nghiệp vụ của dự án) | Khóa PHI chỉ có hiệu lực sau khi kỹ sư xác nhận (TO1); kỹ sư nên yêu cầu ảnh/giờ kèm theo bản khai khi nghi ngờ | Nhiều bản khai của cùng farmer bị kỹ sư từ chối xác nhận | Thêm bước kỹ sư từ chối xác nhận (không chỉ confirm/không làm gì), farmer phải khai lại |
| R2 | Job nhắc hạn (WP5-02) chạy sai múi giờ nếu `Clock` bean (P1) không được dùng nhất quán ở scheduler mới | Thấp (đã có `Clock` từ P1, chỉ cần dùng đúng) | Vừa | Review code đảm bảo `HarvestReminderScheduler` dùng `Clock` đã inject, không gọi `LocalDate.now()` trực tiếp | Thông báo nhắc hạn xuất hiện lệch ngày so với giờ Việt Nam | Sửa lại chỗ gọi sai, không cần migration |
| R3 | Đổi luồng `apply` thành 2 bước (farmer khai → kỹ sư xác nhận) làm chậm quy trình thực tế nếu kỹ sư không xác nhận kịp, kéo dài thời gian trước khi khóa PHI có hiệu lực đúng | Vừa | Vừa | Thông báo cho kỹ sư ngay khi có bản khai mới (qua `workflow_case`/`Notification`); SLA xử lý có thể bổ sung ở P12 (trang quản lý) nếu cần | Nhiều bản khai `PENDING_CONFIRMATION` tồn đọng quá lâu | Cho phép admin xác nhận thay nếu kỹ sư không xử lý kịp (dùng lại cơ chế "thao tác thay mặt" WP5-05) |

## 3. Nợ kỹ thuật chấp nhận

- Q1 (một chẩn đoán có được `apply` nhiều lần không) chưa chốt — thiết kế WP5-04 giả định mỗi `TreatmentApplication` độc lập, đủ tổng quát cho cả hai hướng trả lời Q1, không cần code lại khi có xác nhận.
- Chưa có SLA tự động nhắc kỹ sư khi bản khai `PENDING_CONFIRMATION` tồn đọng quá lâu — để P12 (trang quản lý, nếu làm) hoặc theo dõi thủ công.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: kỹ sư phản hồi rằng đổi luồng `apply` 2 bước không khả thi trong thực tế demo (ví dụ kỹ sư luôn đứng cạnh farmer lúc phun) — khi đó giữ nguyên luồng cũ (kỹ sư tự `apply`), chỉ thêm optional cho farmer tự khai như một kênh bổ sung, không bắt buộc.
- Rollback: các cột mới đều nullable/có default, rollback bằng migration ngược không mất dữ liệu nghiệp vụ; `confirmationStatus = CONFIRMED` mặc định cho dữ liệu cũ đảm bảo không phá hành vi đã có khi rollback.

## 5. Tác động ngoài phase

- Lên các phase sau: P7 (BI) cần `yieldKg`/`harvestedAreaHectares` thật từ phase này để làm chart năng suất; mục 24 (carbon/AWD, để sau) phụ thuộc trực tiếp vào dữ liệu sản lượng đã thu thập ở đây.
- Lên demo, tài liệu nộp bài, thời hạn: luồng farmer tự khai → kỹ sư xác nhận là một phần của câu trả lời cho câu hỏi hội đồng về độ tin cậy dữ liệu AI/nghiệp vụ (mục 11, 20 `PROJECT_IMPROVEMENTS.md`).
