# Giải thích kế hoạch triển khai và thuật ngữ

Tài liệu này giải thích bằng tiếng Việt đơn giản nội dung của [kế hoạch triển khai](KE_HOACH_TRIEN_KHAI.md). Đây là tài liệu tham khảo, không dùng để theo dõi trạng thái công việc.

## Bức tranh chung

Hệ thống nhận ảnh lá cây, nhận diện bệnh, tìm thông tin liên quan, đối chiếu các quy tắc đã được duyệt, rồi mới đưa khuyến nghị. Kế hoạch nhằm làm luồng này an toàn hơn, không mất dữ liệu khi lỗi mạng và có thể kiểm tra lại từng quyết định.

## P0 — Làm luồng chẩn đoán ổn định

### P0-01 — Async job

Thay vì người dùng phải giữ trang chờ AI xử lý 1–2 phút, hệ thống tạo một “phiếu công việc” chạy nền. Người dùng có thể rời trang; khi xử lý xong vẫn xem được kết quả.

### P0-02 — Audit và replay

Lưu lại AI đã nhận dữ liệu gì, dùng model nào, tìm tài liệu nào, áp rule nào và mất bao lâu. “Replay” là chạy lại một ca chẩn đoán cũ để kiểm tra hoặc điều tra vì sao kết quả như vậy.

### P0-03 — CV triage và confidence gate

Kiểm tra ảnh đầu vào có thật sự là lá cây/bệnh cây và có đủ rõ không. Ảnh mờ, không phải lá hoặc AI không chắc sẽ được yêu cầu chụp lại, thay vì tốn chi phí chạy toàn bộ AI.

### P0-04 — Client-side image validation

Kiểm tra ảnh ngay trên điện thoại/trình duyệt trước khi upload. Ví dụ, file vượt 10MB hay sai định dạng sẽ được báo ngay.

## P1 — Quy tắc điều trị và chọn rule đúng

Đây là phần quan trọng nhất về an toàn.

### DiseaseManagementPolicy

Trong kế hoạch hiện tại, đây là **guardrail** — hàng rào an toàn. Nó không tự kê thuốc; nó chỉ nói “bệnh này có được phép áp dụng rule điều trị trực tiếp không?” và “được phép trong phạm vi hóa chất nào?”.

### TreatmentRule

Đây là rule điều trị cụ thể, ví dụ bệnh nào có thể dùng biện pháp/hoạt chất nào, trong điều kiện nào.

### P1-01 — Seed-backed guardrail

Đưa các guardrail đã được duyệt từ thư mục dữ liệu chuẩn vào database. “Seed” nghĩa là nạp sẵn dữ liệu chuẩn khi khởi tạo hệ thống.

### P1-02 — review_required

Nếu thiếu guardrail, không có rule phù hợp, rule bị chặn, hoặc hệ thống không thể chọn rõ ràng, hệ thống không tự khuyến nghị mà chuyển sang “cần người duyệt”.

### P1-03 — Deterministic selector

Backend tự chọn `TreatmentRule` theo tiêu chí rõ ràng như độ ưu tiên và mức phù hợp với bối cảnh; không còn lấy rule đầu tiên trong danh sách hay để AI tự chọn tùy ý.

## P2 — Trải nghiệm người dùng và thông báo

### P2-01 — Recovery khi rớt mạng

Nếu mạng rớt lúc AI đang xử lý, người dùng vào lại trang vẫn thấy tiến trình/kết quả cũ, không cần chẩn đoán lại.

### P2-02 — Progress UI

Thay spinner quay mãi, giao diện nói rõ hệ thống đang kiểm tra ảnh, nhận diện bệnh, tìm tài liệu, kiểm tra rule hay tạo kết quả.

### P2-03 — Mattermost outbox

Tự gửi thông báo Mattermost khi có report mới hoặc ca cần duyệt. “Outbox” là bảng hàng đợi gửi thông báo; Mattermost bị lỗi thì hệ thống lưu lại và thử gửi lại, không mất thông báo.

## P3 — Sẵn sàng chạy production

### P3-01 — Secret management

Không để API key, mật khẩu hoặc webhook trong code/file local. Chúng được cất ở hệ thống quản lý bí mật chuyên dụng.

### P3-02 — Benchmark

Đo hệ thống chịu được bao nhiêu người dùng cùng lúc, mất bao lâu khi mới khởi động, và cần bao nhiêu RAM/CPU/GPU.

## P4 — Dữ liệu nước và carbon

### P4-01 — Carbon/AWD Estimator

Tính toán chu kỳ ngập–khô của ruộng, lượng nước và ước tính ảnh hưởng carbon/phát thải. Kết quả phải truy được dữ liệu đầu vào và phiên bản công thức.

### P4-02 — IoT simulator

Chương trình giả lập cảm biến mực nước để phát triển và kiểm tra hệ thống khi chưa có thiết bị thật hoặc khi muốn thử tình huống lỗi.

## P5 — Tự xác nhận đã phun

### P5-01 — IoT auto-confirm

Dùng dữ liệu IoT như thiết bị nào phun, ở ruộng nào, lúc nào, áp suất/lưu lượng bao nhiêu để xác nhận đã phun.

Hệ thống không tự tin chỉ vì có lịch phun hoặc đã gửi lệnh xuống thiết bị. Nếu bằng chứng IoT thiếu hay mâu thuẫn, vẫn yêu cầu người kiểm tra. Ban đầu sẽ chạy “shadow mode”: hệ thống chỉ đề xuất kết quả để so với xác nhận thủ công, chưa tự động đổi trạng thái thật.

## P6 — AI nâng cao

### P6-01 — GraphRAG

Cách tổ chức dữ liệu thành mạng quan hệ, ví dụ cây trồng ↔ bệnh ↔ thuốc ↔ ruộng ↔ cảm biến. Mục đích là tìm thông tin liên quan tốt hơn và giải thích nguồn thông tin.

### P6-02 — Multi-agent

Nhiều vai trò AI phối hợp, ví dụ một AI tìm tài liệu, một AI kiểm tra nông học, một AI kiểm tra an toàn, một AI viết báo cáo. Chúng vẫn phải qua guardrail và rule backend trước khi trả khuyến nghị.

## Thuật ngữ chính

| Thuật ngữ | Nghĩa dễ hiểu |
| --- | --- |
| Backend | Phần máy chủ xử lý logic và dữ liệu. |
| Frontend | Giao diện người dùng trên web/app. |
| `request_id` | Mã định danh của một lần yêu cầu, giúp lần theo lỗi. |
| Idempotency | Gửi lại cùng một yêu cầu nhiều lần vẫn chỉ tạo một kết quả, không bị nhân đôi. |
| Audit trail | Lịch sử để biết ai/hệ thống đã làm gì và dựa vào dữ liệu nào. |
| RAG evidence | Tài liệu hoặc đoạn thông tin AI đã tìm được để làm căn cứ trả lời. |
| Confidence | Mức độ AI tự tin về kết quả. |
| Seed data | Dữ liệu chuẩn được nạp ban đầu vào database. |
| Generator | Chương trình chuyển dữ liệu JSON chuẩn thành `data.sql`. |
| Staging | Môi trường thử gần giống production trước khi đưa ra dùng thật. |
| SLO | Mục tiêu chất lượng dịch vụ, ví dụ 95% job phải hoàn tất trong một thời gian nhất định. |
| Guardrail | Hàng rào an toàn: điều kiện chặn hoặc giới hạn việc tự động đưa khuyến nghị. |
| Outbox | Hàng đợi nội bộ lưu thông báo cần gửi ra hệ thống ngoài để có thể retry khi lỗi. |
| Shadow mode | Hệ thống chỉ tính/đề xuất kết quả để đối chiếu, chưa tự động tác động dữ liệu thật. |
