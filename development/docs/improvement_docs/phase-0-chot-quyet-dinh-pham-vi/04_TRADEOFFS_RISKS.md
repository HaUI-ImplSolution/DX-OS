# Trade-off và rủi ro: Phase 0

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | D1 phạm vi đề tài | A: giữ nguyên, B: thêm app thứ hai (P9), C: đổi hẳn đề tài | **A** | Không tốn thêm 3-5 ngày việc (P9), tập trung làm sâu DX-Farm | Mất bằng chứng trực tiếp "đổi ứng dụng, Core có đổi không" (câu hỏi hội đồng số 1) — bù lại bằng sơ đồ + tách module rõ ràng ở P3 | Vừa — nếu sau này cần P9 gấp, vẫn thêm được vì P3 không phụ thuộc việc có P9 hay không |
| TO2 | D2 workflow engine | Flowable / tự làm | **Tự làm** | Nhẹ, đúng 5 trường đề yêu cầu, kiểm soát được trong 14 ngày | Không có UI/BPMN chuẩn, nếu sau này cần quy trình phức tạp hơn phải tự mở rộng | Cao — đổi từ tự làm sang Flowable giữa chừng coi như viết lại P3 |
| TO3 | D4 lưu ảnh | MinIO / storage service tự bọc | **MinIO** | Đúng nghĩa "object storage" theo đề mục 4.1 | Thêm dịch vụ, tốn RAM, phải migrate ảnh cũ trong `/uploads` local khi làm P3 | Vừa — nếu hết thời gian giữa chừng, interface (link có hạn) vẫn giữ nguyên nên có thể rút về filesystem tạm mà không đổi API |
| TO4 | D6 bật test phần lõi | Bật lại toàn bộ / bật lại chỉ case crash-logic / giữ quy ước không viết test | **Bật lại, chỉ case crash/logic** (khóa PHI, idempotency, phạm vi dữ liệu theo role, chuyển trạng thái workflow) | Đáp ứng đề mục 7, 9 với chi phí thấp hơn coverage đầy đủ | Không có test cho happy path và UI, lỗi nghiệp vụ không gây crash có thể lọt | Thấp — mở rộng thêm case sau này không phải viết lại, chỉ thêm file test mới |

Kết luận: đã chốt với người phụ trách phase (xem `01_PLAN.md` mục "Quyết định thiết kế đã chốt"), ngày 05/10/2026.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | _(đã xử lý)_ Giảng viên/BTC xác nhận D1 ngày 05/10/2026, không còn rủi ro chặn P9 | - | - | - | - | - |
| R2 | _(đã xử lý)_ Chốt không ràng buộc mốc 14 ngày (05/10/2026); nếu sau này có hạn nộp thật xuất hiện bất ngờ, phải quay lại áp dụng lộ trình rút gọn ở `PHASE_PLAN.md` mục 3 | Thấp | Vừa (chỉ ảnh hưởng tốc độ, không ảnh hưởng nội dung đã làm) | Theo dõi thông báo chính thức từ BTC trong lúc làm các phase sau | BTC công bố deadline cụ thể | Áp dụng ngay lộ trình rút gọn đã có sẵn ở `PHASE_PLAN.md` mục 3, ưu tiên nhóm A |
| R3 | Chọn tự làm workflow (D2) nhưng sau đó phát sinh quy trình phức tạp hơn 5 trường cơ bản | Thấp | Vừa | Thiết kế bảng `workflow_case`/`workflow_event` đủ tổng quát ngay từ P3 (không hard-code riêng cho chẩn đoán), dù không còn P9 vẫn nên tổng quát để dùng lại cho P4/P5 | P4/P5 cần thêm loại trạng thái mà bảng hiện tại không biểu diễn được | Mở rộng bảng (thêm cột, không đổi schema gốc) thay vì chuyển hẳn sang Flowable giữa chừng |
| R4 | Nhóm không chờ phase 0 chốt, bắt đầu code P1-P3 song song theo giả định khác quyết định đã chốt | Thấp | Vừa (nay thấp hơn vì D1-D10 đã chốt xong trong cùng ngày) | Phổ biến bảng quyết định đã chốt ở `01_PLAN.md` cho cả nhóm trước khi bắt đầu P1-P3 | Code P3 dùng Traefik/Flowable thay vì APISIX/tự làm đã chốt | P1 (mục 9, 10, 17 — bảo mật, poka-yoke) không phụ thuộc D1-D10 nên luôn an toàn để bắt đầu song song |

## 3. Nợ kỹ thuật chấp nhận

- Không áp dụng cho phase này — phase 0 không tạo nợ kỹ thuật, chỉ tạo nợ quyết định (các dòng "chưa chốt" nếu còn sau khi đóng phase).

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: D1 trả lời là C (đổi hẳn đề tài) — khi đó toàn bộ `PHASE_PLAN.md` và `PROJECT_IMPROVEMENTS.md` cần viết lại, không chỉ phase 0.
- Rollback: phase 0 chỉ là tài liệu, rollback đơn giản là sửa lại cột "Kết luận" ở `01_PLAN.md` và lan truyền thay đổi sang phase đang bị ảnh hưởng (xem cột "Phụ thuộc, lưu ý" của từng DP).

## 5. Tác động ngoài phase

- Lên các phase sau: D2-D4 là nội dung chính của P3; D1 quyết định P9 tồn tại hay không; D6 quyết định khối lượng mọi phase sau (mỗi WP có thêm việc test).
- Lên demo, tài liệu nộp bài, thời hạn: ngày hạn chốt ở đây là input duy nhất để P8 lên lịch rehearsal; D1 là câu trả lời trực tiếp cho câu hỏi hội đồng số 1 (mục 20 `PROJECT_IMPROVEMENTS.md`).
