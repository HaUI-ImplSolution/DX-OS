# Trade-off và rủi ro: Phase 5

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Farmer tự khai đã phun (WP5-04) | Farmer khai là có hiệu lực ngay / Farmer khai, kỹ sư xác nhận mới có hiệu lực | Kỹ sư xác nhận mới có hiệu lực | Giữ nguyên tắc dữ liệu quyết định khóa PHI phải qua người có trách nhiệm xác nhận; đúng thực tế hơn (farmer là người phun, kỹ sư xác nhận lại) | Thêm một bước chờ, khóa PHI không có hiệu lực ngay lúc farmer khai | Thấp — đổi lại thành có hiệu lực ngay chỉ là bỏ điều kiện `confirmationStatus`, không cần migration |
| TO2 | Lịch sử gia hạn ngày thu hoạch | Bảng riêng / Tái dùng `workflow_event` | Bảng riêng `harvest_reschedules` | Ghi được ngày cũ, ngày mới và lý do từng lần, không phụ thuộc vòng duyệt | Thêm một bảng ngoài workflow | Thấp |
| TO3 | Bản khai phun chờ xác nhận có chặn thu hoạch không | Không chặn tới khi xác nhận / Chặn từ lúc khai | Chặn từ lúc khai (`422 TREATMENT_PENDING_CONFIRMATION`) | Khai thật không thiệt hơn khai gian, không có khoảng hở thu hoạch trước khi kỹ sư xử lý | Nông dân phải chờ kỹ sư kể cả khi khai đúng | Thấp: bỏ một điều kiện kiểm tra |
| TO4 | Việc tồn đọng khi thu hoạch (báo cáo chưa duyệt, khuyến nghị chưa phun) | Cảnh báo / Xác nhận có chủ đích / Chặn cứng | Cảnh báo, không chặn (`harvest-readiness`) | Không làm kẹt vụ khi kỹ sư xử lý chậm | Nông dân có thể bỏ qua cảnh báo | Thấp |
| TO5 | Số lần phun trên một chẩn đoán (Q1) | Một lần / Nhiều lần | Một lần, bản khai bị từ chối được khai lại trên cùng bản ghi | Giữ ràng buộc unique, ít đổi schema | Phun lại phải qua báo cáo và chẩn đoán mới | Vừa: bỏ unique và đổi cách tính khóa theo lần mới nhất |

Kết luận: TO1-TO5 áp dụng ngay.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Farmer tự khai đã phun sai sự thật (khai khống để mở khóa thu hoạch sớm hơn) | Thấp-Vừa | Cao (ảnh hưởng trực tiếp độ tin cậy của khóa PHI — điểm nhấn nghiệp vụ của dự án) | Khóa PHI chỉ có hiệu lực sau khi kỹ sư xác nhận (TO1); kỹ sư nên yêu cầu ảnh/giờ kèm theo bản khai khi nghi ngờ | Nhiều bản khai của cùng farmer bị kỹ sư từ chối xác nhận | Thêm bước kỹ sư từ chối xác nhận (không chỉ confirm/không làm gì), farmer phải khai lại |
| R2 | Job nhắc hạn (WP5-02) chạy sai múi giờ nếu `Clock` bean (P1) không được dùng nhất quán ở scheduler mới | Thấp (đã có `Clock` từ P1, chỉ cần dùng đúng) | Vừa | Review code đảm bảo `HarvestReminderScheduler` dùng `Clock` đã inject, không gọi `LocalDate.now()` trực tiếp | Thông báo nhắc hạn xuất hiện lệch ngày so với giờ Việt Nam | Sửa lại chỗ gọi sai, không cần migration |
| R3 | Đổi luồng `apply` thành 2 bước (farmer khai → kỹ sư xác nhận) làm chậm quy trình thực tế nếu kỹ sư không xác nhận kịp, kéo dài thời gian trước khi khóa PHI có hiệu lực đúng | Vừa | Vừa | Thông báo cho kỹ sư ngay khi có bản khai mới (qua `workflow_case`/`Notification`); SLA xử lý có thể bổ sung ở P12 (trang quản lý) nếu cần | Nhiều bản khai `PENDING_CONFIRMATION` tồn đọng quá lâu | Cho phép admin xác nhận thay nếu kỹ sư không xử lý kịp (dùng lại cơ chế "thao tác thay mặt" WP5-05) |

R1 và R3 giữ mức theo dõi; R2 đã xử lý: scheduler dùng `Clock` và cron theo `Asia/Ho_Chi_Minh`, đã thử chạy mỗi phút và không tạo thông báo lặp trong ngày.

## 3. Nợ kỹ thuật chấp nhận

- Mỗi chẩn đoán chỉ có một lần phun (Q1); phun lặp lại phải tạo báo cáo và chẩn đoán mới.
- Chưa có SLA tự động nhắc kỹ sư khi bản khai `PENDING_CONFIRMATION` tồn đọng; admin xác nhận thay được.
- Hộp thông báo chỉ dành cho nông dân; engineer và admin dùng hàng đợi và Mattermost.
- Mobile chỉ tải 30 thông báo mới nhất, chưa cuộn tải thêm; thông báo chưa có hạn lưu hay xóa.
- Job nhắc hạn nhắc mỗi ngày cho tới khi vụ được thu hoạch hoặc đổi ngày; chưa có giới hạn số lần nhắc.
- Job chạy trên một bản backend; chạy nhiều bản thì `dedup_key` chặn trùng nhưng lần ghi tranh chấp có thể làm một lượt chạy bị bỏ.
- Thu hoạch thay hộ ghi trên vụ và log backend, không có trong timeline workflow vì thu hoạch không có case.
- Cảnh báo việc tồn đọng khi thu hoạch chỉ mang tính tham khảo, không chặn.
- `HARVEST_REMINDER_CRON` có mặc định trong `application.yaml` nhưng chưa nằm trong `deploy/.env.example`.
- Không có test tự động cho logic mới; kiểm bằng chạy thật (xem `03_TEST_PLAN.md`).
- Dữ liệu thử `P5-TEST` (thuốc và phác đồ) không phải khuyến cáo thật; demo khóa PHI thật cần phác đồ do chuyên gia duyệt (P8, Q2).

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: kỹ sư phản hồi rằng đổi luồng `apply` 2 bước không khả thi trong thực tế demo (ví dụ kỹ sư luôn đứng cạnh farmer lúc phun) — khi đó giữ nguyên luồng cũ (kỹ sư tự `apply`), chỉ thêm optional cho farmer tự khai như một kênh bổ sung, không bắt buộc.
- Rollback: các cột mới đều nullable/có default, rollback bằng migration ngược không mất dữ liệu nghiệp vụ; `confirmationStatus = CONFIRMED` mặc định cho dữ liệu cũ đảm bảo không phá hành vi đã có khi rollback.

## 5. Tác động ngoài phase

- Lên các phase sau: P7 (BI) cần `yieldKg`/`harvestedAreaHectares` thật từ phase này để làm chart năng suất; mục 24 (carbon/AWD, để sau) phụ thuộc trực tiếp vào dữ liệu sản lượng đã thu thập ở đây.
- Lên demo, tài liệu nộp bài, thời hạn: luồng farmer tự khai → kỹ sư xác nhận là một phần của câu trả lời cho câu hỏi hội đồng về độ tin cậy dữ liệu AI/nghiệp vụ (mục 11, 20 `PROJECT_IMPROVEMENTS.md`).
