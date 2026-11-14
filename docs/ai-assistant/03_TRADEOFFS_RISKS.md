# Đánh đổi và rủi ro

Đọc cùng [overview](./00_OVERVIEW.md), [plan](./01_PLAN.md) và [checklist](./02_CHECKLIST.md).
Các biện pháp dưới đây là yêu cầu thiết kế, chưa phải kết quả kiểm chứng.

## 1. Đánh đổi đã chọn

| Lựa chọn | Giá trị | Đánh đổi và cách mô tả |
|---|---|---|
| API cá nhân thay LLM local | Bộ cài không mang trọng số/GPU; người dùng chọn provider/model và tự quản quota | AI cần mạng và quyền API; model/dịch vụ ngoài không được gọi là đạt OSAID |
| Chỉ ba provider chính thức | Phạm vi adapter và địa chỉ gửi dữ liệu rõ ràng | Chưa phục vụ endpoint tùy chỉnh hoặc model tự host |
| Một dịch vụ dùng chung | Không có tiến trình/container riêng từng tài khoản | Cách ly phải được thực hiện ở mọi truy vấn, cache, job và credential |
| Bộ lọc Core và BM25 | Không cần embedding, reranker hoặc kho vector mới | Tìm gần nghĩa và diễn đạt khác từ khóa có thể kém; đo tiếng Việt rồi mới quyết định nâng cấp |
| Material UI hiện có | Nhất quán với DX-OS, tránh thêm bộ giao diện | Lấy cách bố trí Gemini, không hứa sao chép toàn bộ trải nghiệm hoặc tính năng Gemini |
| Xác nhận ngoài LLM | Người dùng nhìn và duyệt tác động cụ thể | Cần snapshot, trạng thái bền vững và điều kiện phiên bản ở Core |
| Corpus tự viết, hư cấu | Chủ động quyền sử dụng và đáp án kiểm | Chỉ chứng minh cơ chế, không chứng minh chất lượng trên quy chế thật |

Người dùng chọn hướng API cá nhân không đồng nghĩa mọi nhà cung cấp bên ngoài đều là phần mềm nguồn mở.
Cam kết áp dụng cho mã và dependency của phần tích hợp; phụ thuộc dịch vụ ngoài của tính năng AI phải được công bố rõ.
Tắt AI vẫn dùng được DX-OS không có nghĩa phần AI hoạt động được khi không có API.

## 2. Rủi ro chặn nghiệm thu

| Rủi ro | Xử lý bắt buộc | Ca chứng minh |
|---|---|---|
| Lộ key hoặc dữ liệu chéo chủ | Chủ từ JWT, truy vấn ràng buộc chủ, mã hóa key, không log payload | Đoán ID, hai chủ, đọc DB/backup và kiểm log đã loại bí mật |
| Lịch sử giữ dữ liệu sau khi mất quyền | Kiểm quyền mô hình và nguồn trước khi trả hoặc tái gửi ngữ cảnh; chặn tiếp tục khi nguồn cũ không xác minh được | Gỡ quyền mô hình, đổi phạm vi dòng rồi mở và tiếp tục hội thoại cũ |
| Prompt injection từ tài liệu/hồ sơ | Công cụ allowlist, validate đầu vào, dữ liệu không thay quyền; xác nhận do server dựng | Nguồn yêu cầu lộ key, đọc hồ sơ khác hoặc tự xác nhận |
| Model bịa nguồn hoặc số thống kê | Chỉ nhận ID nguồn đã cấp, thống kê qua API, báo thiếu bằng chứng | Không có đáp án, nguồn giả, kết quả bị phân trang |
| Xác nhận snapshot cũ | Core kiểm version/modelVersion cùng giao dịch với thay đổi | Hồ sơ hoặc mô hình đổi giữa lúc xem và xác nhận |
| Ghi lặp sau timeout hoặc bấm kép | Claim nguyên tử, trạng thái `uncertain`, không tự gửi lại | Hai request đồng thời, đứt mạng sau ghi, restart giữa lượt |
| Credential nền mở rộng quyền | Phiên riêng có thể thu hồi, không ADMIN, đọc lại Core theo người đã bật | Thu hồi phiên, mất vai, giả chữ ký và sự kiện sai mô hình |
| Lặp sự kiện và tiêu quota | Khử trùng bền vững, nhận trước xử lý sau, giới hạn retry và concurrency | Webhook trùng, worker crash, sự kiện do chính đề xuất sinh ra |
| Provider trả cấu trúc khác dự kiến | Adapter kiểm schema và khả năng model; không suy từ hỗ trợ text sang hỗ trợ công cụ | Model không hỗ trợ tool, stream lỗi, key sai và quota |
| AI ảnh hưởng Core | Tiến trình riêng, quyền DB riêng, giới hạn kết nối/tài nguyên, không chờ LLM trong giao dịch Core | Provider treo, backlog job tăng và tắt dịch vụ AI |

## 3. Điểm tech lead cần review

### Đổi mục tiêu nguồn mở

Khuyến nghị ghi đúng phạm vi là tích hợp AI tùy chọn với API cá nhân, mã tích hợp và dependency theo chính sách nguồn mở.
Người dùng đã đồng ý hướng này; cần đồng bộ với cam kết cấp dự án trước khi tích hợp.
Không nghiệm thu theo tiêu chí model OSAID cũ rồi dùng bằng chứng API Gemini/OpenAI/Anthropic thay thế.

### Phạm vi tác tử theo sự kiện

Khuyến nghị giữ một trường scalar được cấu hình, đề xuất cho chính người bật có quyền sửa, xác nhận trên web và có nguồn giải thích.
Không mở thành sửa hồ sơ tùy ý hoặc tự quyết định nhiều bước.
Phải chốt nơi cấu hình, thời hạn phiên nền và cách nhận webhook của bản cài trước khi viết phần này.
Nếu quyết định hoãn hoặc loại bỏ, cần ghi rõ thay đổi phạm vi; không mặc nhiên coi toàn bộ #210 hoàn thành.

### Lịch sử và chính sách dữ liệu

Khuyến nghị chỉ đưa ngữ cảnh cần thiết vào model và khóa hội thoại theo mô hình nghiệp vụ.
Xóa hội thoại phải xóa tin nhắn và nguồn; log vận hành chỉ giữ ID, thời gian, mã lỗi và usage cần thiết.
Chốt thời gian lưu tin nhắn, đề xuất và metadata thực thi trước khi triển khai cơ chế dọn dữ liệu.
Xóa ở DX-OS không được mô tả là xóa được dữ liệu nhà cung cấp đã nhận; điều đó phụ thuộc điều khoản của provider.

### Tài nguyên và chất lượng

Khuyến nghị giữ một service nhỏ, dùng PostgreSQL hiện có và BM25 có giới hạn corpus.
Chưa cam kết số RAM hoặc độ trễ khi chưa đo; cả corpus và lịch sử tăng đều có thể làm dịch vụ nặng dù không có LLM local.
Mục tiêu truy xuất và các ca chặn nghiệm thu ở plan cần được review trước khi dùng làm chuẩn đạt.
Chỉ cân nhắc embedding hoặc reranker khi kết quả đo cho thấy thiếu chất lượng và người dùng chấp thuận thay đổi.

## 4. Giới hạn bằng chứng

Kiểm mô phỏng chỉ chứng minh xử lý transport/schema/lỗi theo giả lập, không chứng minh chất lượng câu trả lời hoặc khả năng model thật.
Mỗi provider và model công bố hỗ trợ cần ca API thật; thiếu key/quota phải để mục tương ứng mở.
BM25 đạt trên corpus hư cấu không chứng minh đạt với toàn bộ văn bản tiếng Việt hoặc tài liệu của một tổ chức thật.
Compile, typecheck, CI, kiểm chức năng và nghiệm thu cuối là các mức bằng chứng riêng.
Không công bố tính năng đã hoàn thành chỉ vì có bộ kế hoạch hoặc giao diện chạy với dữ liệu giả.
