# Đánh đổi và rủi ro

Đọc cùng [overview](./00_OVERVIEW.md), [plan](./01_PLAN.md) và [checklist](./02_CHECKLIST.md).
Các biện pháp dưới đây là yêu cầu thiết kế, chưa phải kết quả kiểm chứng.

## 1. Đánh đổi đã chọn

| Lựa chọn | Giá trị | Đánh đổi và cách mô tả |
|---|---|---|
| Self-host do người triển khai quản lý | Nội dung được suy luận trong hạ tầng DX-OS, chủ động model và runtime | Phải cấp tài nguyên, giữ trọng số, vận hành hàng đợi và kiểm hồ sơ model theo OSAID |
| Apertus 8B Q4_K_M qua llama.cpp | Giảm dung lượng trọng số, có thể chạy CPU và giữ suy luận trong bản cài | Trọng số tham khảo 5,06 GB chưa gồm RAM ngữ cảnh/runtime; có thể suy giảm chất lượng và chưa có số đo tốc độ |
| Q5_K_M đối chứng khi cần | Đo xem giảm lượng tử hóa có cải thiện câu trả lời/công cụ trên cùng bộ ca không | Trọng số 5,81 GB; thêm bộ nhớ và không bảo đảm sửa được giới hạn model gốc |
| Một dịch vụ dùng chung | Không có tiến trình/container riêng từng tài khoản | Cách ly phải được thực hiện ở mọi truy vấn, cache, job và credential |
| Bộ lọc Core và BM25 | Không cần embedding, reranker hoặc kho vector mới | Tìm gần nghĩa và diễn đạt khác từ khóa có thể kém; đo tiếng Việt rồi mới quyết định nâng cấp |
| Tái sử dụng Haystack, cân nhắc FastEmbed/FlashRank khi cần | Giảm mã pipeline/thuật toán tự viết | Haystack đề xuất stack Python; phải so dependency với TypeScript tối thiểu và kiểm model riêng, không cài tất cả |
| Material UI hiện có | Nhất quán với DX-OS, tránh thêm bộ giao diện | Lấy cách bố trí Gemini, không hứa sao chép toàn bộ trải nghiệm hoặc tính năng Gemini |
| Xác nhận ngoài LLM | Người dùng nhìn và duyệt tác động cụ thể | Cần snapshot, trạng thái bền vững và điều kiện phiên bản ở Core |
| Corpus tự viết, hư cấu | Chủ động quyền sử dụng và đáp án kiểm | Chỉ chứng minh cơ chế, không chứng minh chất lượng trên quy chế thật |

Cam kết nguồn mở áp dụng cho mã tích hợp, runtime, model và các thành phần truy xuất thực sự dùng.
Self-host chưa chứng minh độ mở; cần đối chiếu trọng số, mã, thông tin dữ liệu huấn luyện/hậu huấn luyện và artifact chuyển đổi.
Tắt AI vẫn dùng được DX-OS không có nghĩa phần AI hoạt động được khi không có backend suy luận khả dụng.
Chi tiết và nguồn của các dung lượng GGUF tham khảo ở [plan, mục khảo sát Apertus](./01_PLAN.md#khảo-sát-apertus-8b-lượng-tử-hóa).

## 2. Rủi ro chặn nghiệm thu

| Rủi ro | Xử lý bắt buộc | Ca chứng minh |
|---|---|---|
| Lộ credential hoặc dữ liệu chéo chủ | Chủ từ JWT, truy vấn ràng buộc chủ, mã hóa credential nền, không log payload | Đoán ID, hai chủ, đọc DB/backup và kiểm log đã loại bí mật |
| Lịch sử giữ dữ liệu sau khi mất quyền | Kiểm quyền mô hình và nguồn trước khi trả hoặc tái gửi ngữ cảnh; chặn tiếp tục khi nguồn cũ không xác minh được | Gỡ quyền mô hình, đổi phạm vi dòng rồi mở và tiếp tục hội thoại cũ |
| Prompt injection từ tài liệu/hồ sơ | Công cụ allowlist, validate đầu vào, dữ liệu không thay quyền; xác nhận do server dựng | Nguồn yêu cầu lộ credential, đọc hồ sơ khác hoặc tự xác nhận |
| Model bịa nguồn hoặc số thống kê | Chỉ nhận ID nguồn đã cấp, thống kê qua API, báo thiếu bằng chứng | Không có đáp án, nguồn giả, kết quả bị phân trang |
| Xác nhận snapshot cũ | Core kiểm version/modelVersion cùng giao dịch với thay đổi | Hồ sơ hoặc mô hình đổi giữa lúc xem và xác nhận |
| Ghi lặp sau timeout hoặc bấm kép | Claim nguyên tử, trạng thái `uncertain`, không tự gửi lại | Hai request đồng thời, đứt mạng sau ghi, restart giữa lượt |
| Credential nền mở rộng quyền | Phiên riêng có thể thu hồi, không ADMIN, đọc lại Core theo người đã bật | Thu hồi phiên, mất vai, giả chữ ký và sự kiện sai mô hình |
| Lặp sự kiện và quá tải suy luận | Khử trùng bền vững, nhận trước xử lý sau, giới hạn retry và concurrency | Webhook trùng, worker crash, sự kiện do chính đề xuất sinh ra |
| Runtime trả cấu trúc khác dự kiến | Adapter kiểm schema, chat template và khả năng model; không suy từ hỗ trợ text sang hỗ trợ công cụ | Model chọn sai tool, stream lỗi, hủy lượt và timeout |
| Q4 trả sai tiếng Việt hoặc tham số công cụ | Đo nguồn/công cụ riêng, kiểm template/runtime và đối chứng Q5 khi cần | Cùng câu hỏi, ngữ cảnh và công cụ; JSON hợp lệ nhưng sai thao tác vẫn bị tính lỗi |
| Local dùng hết RAM/CPU, hàng đợi dài | Giới hạn ngữ cảnh và concurrency, đo RAM đỉnh/độ trễ cùng tải Core | Một lượt rồi hai chủ xếp hàng, quá ngân sách và timeout |
| Bản lượng tử hóa không đủ hồ sơ nguồn mở | Kiểm model gốc, artifact chuyển đổi và dữ liệu hiệu chỉnh nếu có trước khi phân phối | Đối chiếu license, provenance và các phần huấn luyện/hậu huấn luyện |
| Endpoint local làm lộ quyền hoặc rò dữ liệu chéo chủ | Cấu hình ở hạ tầng, xác thực dịch vụ, không gửi token Core, tách prompt/cache theo chủ | URL giả, redirect, gọi trực tiếp cổng suy luận và hai chủ đồng thời |
| AI ảnh hưởng Core | Tiến trình riêng, quyền DB riêng, giới hạn kết nối/tài nguyên, không chờ LLM trong giao dịch Core | Runtime treo, backlog job tăng và tắt dịch vụ AI |

## 3. Điểm tech lead cần review

### Hồ sơ nguồn mở của pipeline self-host

Kế hoạch triển khai bằng Apertus 8B Instruct lượng tử hóa qua llama.cpp, với mục tiêu đáp ứng OSAID.
Cần kiểm đúng bản Instruct và hậu huấn luyện, runtime, artifact GGUF cùng dữ liệu hiệu chỉnh nếu dùng.
Nghiệm thu dựa trên hồ sơ nguồn mở và kết quả chạy thật, không chỉ nhãn giấy phép hoặc việc xử lý nội bộ.
Chốt tuyên bố nguồn mở sau khi có hồ sơ của đúng model, runtime và các model truy xuất nếu thực sự dùng.

### Phạm vi tác tử theo sự kiện

Khuyến nghị giữ một trường scalar được cấu hình, đề xuất cho chính người bật có quyền sửa, xác nhận trên web và có nguồn giải thích.
Không mở thành sửa hồ sơ tùy ý hoặc tự quyết định nhiều bước.
Phải chốt nơi cấu hình, thời hạn phiên nền và cách nhận webhook của bản cài trước khi viết phần này.
Nếu quyết định hoãn hoặc loại bỏ, cần ghi rõ thay đổi phạm vi; không mặc nhiên coi toàn bộ #210 hoàn thành.

### Lịch sử và chính sách dữ liệu

Khuyến nghị chỉ đưa ngữ cảnh cần thiết vào model và khóa hội thoại theo mô hình nghiệp vụ.
Xóa hội thoại phải xóa tin nhắn và nguồn; log vận hành chỉ giữ ID, thời gian, mã lỗi và usage cần thiết.
Chốt thời gian lưu tin nhắn, đề xuất và metadata thực thi trước khi triển khai cơ chế dọn dữ liệu.
Xóa hội thoại phải tính cả dữ liệu/cache trong runtime và bản backup; không tuyên bố xóa khỏi backup cũ khi chưa có cơ chế tương ứng.

### Tài nguyên và chất lượng

Khuyến nghị một dịch vụ điều phối, PostgreSQL hiện có, BM25 có giới hạn corpus và một tiến trình suy luận dùng chung.
Chưa cam kết số RAM hoặc độ trễ khi chưa đo; trọng số, KV cache, corpus và lịch sử đều có thể làm hệ thống nặng.
Khảo sát Q4 trước với ngữ cảnh 4.096 token và một lượt xử lý; Q5 chỉ đối chứng khi cần, không tải thêm mọi bản lượng tử hóa.
Giảm số bit không bảo đảm nhanh hơn trên mọi phần cứng, và giảm bộ nhớ không bảo đảm giữ chất lượng tiếng Việt/công cụ.
Mục tiêu truy xuất và các ca chặn nghiệm thu ở plan cần được review trước khi dùng làm chuẩn đạt.
Chỉ cân nhắc embedding hoặc reranker khi kết quả đo cho thấy thiếu chất lượng và người dùng chấp thuận thay đổi.
Haystack là hướng ưu tiên khảo sát để tái sử dụng pipeline; FastEmbed/FlashRank là lựa chọn thay thế cho phần tương ứng, không phải lớp bắt buộc cộng thêm.

## 4. Giới hạn bằng chứng

Kiểm mô phỏng chỉ chứng minh xử lý transport/schema/lỗi theo giả lập, không chứng minh chất lượng câu trả lời hoặc khả năng model thật.
Model và runtime công bố hỗ trợ cần ca suy luận thật; thiếu trọng số, runtime hoặc máy đích phù hợp phải để mục tương ứng mở.
Local cần chạy đúng GGUF/runtime trên máy đích; kích thước file hoặc benchmark model khác không chứng minh RAM và tốc độ của Apertus.
Chênh lệch Q4/Q5 chưa chứng minh mức mất chất lượng so với BF16 nếu chưa có đối chứng BF16; không suy ra phần trăm chất lượng được giữ lại.
BM25 đạt trên corpus hư cấu không chứng minh đạt với toàn bộ văn bản tiếng Việt hoặc tài liệu của một tổ chức thật.
Compile, typecheck, CI, kiểm chức năng và nghiệm thu cuối là các mức bằng chứng riêng.
Không công bố tính năng đã hoàn thành chỉ vì có bộ kế hoạch hoặc giao diện chạy với dữ liệu giả.
