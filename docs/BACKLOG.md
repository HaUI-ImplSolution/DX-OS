# Việc tồn đọng

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của DX-OS, DX-Farm và DX-Lab.
Làm xong một mục thì xóa dòng đó; không ghi lại diễn biến, chỉ ghi trạng thái hiện tại.

## 1. Trước khi nộp bài

| Việc | Ghi chú |
|---|---|
| Báo cáo kỹ thuật 8-10 trang, slide tối đa 12 | Có dàn ý ở [`SUBMISSION.md`](./SUBMISSION.md) mục 3 |
| Video demo 3-5 phút, rehearsal ít nhất một lần | Kịch bản ở [`SUBMISSION.md`](./SUBMISSION.md) mục 2 |
| Tạo vài issue mẫu trên GitHub | Issues đã bật, hiện chưa có issue nào; lấy từ mục 5 và 6 của tài liệu này |
| Chụp ảnh giao diện hai chế độ (Farm, Lab) và màn hình giám sát lưu vào repository cho hồ sơ | Chưa có; che IP và email thật khi chụp màn hình giám sát |
| Chốt ngày hạn nộp thật | Chưa chốt |

## 2. Việc cần sửa trong mã và cấu hình

| Việc | Phạm vi |
|---|---|
| Hỏi đáp Farm có thể nêu ý không có trong nguồn | Câu hỏi về một bệnh đã nêu tên vẫn có thể nhận nguyên nhân hoặc cách phòng không có trong nguồn |

## 3. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Bên thứ ba tái lập đánh giá RAG | Cần người đánh giá độc lập kiểm tra nhãn và tính lại điểm từ qrels/run; cách chạy ở [`ai/README.md`](../ai/README.md#đánh-giá-truy-xuất-rag) |
| Chấm nội dung Qwen trên 36 câu hỏi và 6 chủ đề chẩn đoán | Chưa có bảng chấm trọng tâm, bám nguồn và dễ đọc do người duyệt xác nhận; tỷ lệ `generated` không phải độ đúng nội dung |
| Chữ lớn trên mobile native | Đã typecheck, kiểm render và nghiệm thu chức năng; chưa đo riêng bố cục khi tăng cỡ chữ trên máy thật |
| Khởi động bản Qwen từ clone sạch | Đã kiểm bằng image/cache và các cặp phiên bản; chưa chạy lại full build/seed toàn stack từ clone sạch |
| `backup.sh` với kho sao lưu từ xa | Đã chạy sao lưu và `verify` trên VPS với dữ liệu thật vào kho cục bộ; chưa thử `BACKUP_REPOSITORY` từ xa |
| Cầu dao AI với tải thật | Đã thử khi AI dừng; chưa thử khi AI chậm vì tải, lúc đó lỗi đọc quá hạn cũng tính là lỗi dịch vụ |

## 4. Giới hạn của AI và dữ liệu

| Việc | Ghi chú |
|---|---|
| Model bệnh chưa được đánh giá độc lập ngoài ruộng | Accuracy 89,43% trên 1.466 ảnh theo người huấn luyện, xem [`AI_EVALUATION.md`](./AI_EVALUATION.md); cần ít nhất 100 ảnh ruộng thật có nhãn kỹ sư để đo toàn pipeline và ngưỡng 0.70 |
| Crop classifier nhận 6/15 ảnh ngô và 15/15 ảnh mía là lúa, chặn 8/50 ảnh lúa nền ruộng | Classifier 10 lớp không có lớp ngô, mía, cỏ; cần thay classifier hoặc thêm dữ liệu ngoài miền; kỹ sư vẫn chốt bệnh |
| Ngưỡng độ nét mới thử trên ảnh công khai và ảnh mờ nhân tạo | Chưa có ảnh mờ thật ngoài ruộng; chưa tách vùng lá |
| Chẩn đoán AI chạy trên CPU mất khoảng 50 giây mỗi ảnh | Đo trên máy chủ 8 nhân; chưa có GPU hay mô hình nhỏ hơn để rút ngắn; lần gọi đầu sau khi khởi động lại AI nạp mô hình và có thể vượt 60 giây chờ của gateway, nhận 504 (kết quả vẫn được tạo) |
| Qwen tắt trên VPS | CPU ảo chỉ có SSE2/SSE3 nên Qwen đọc prompt khoảng 2 token/giây; VPS đặt `AI_ANSWER_GENERATION=false` và `AI_CHAT_CANDIDATE_COUNT=10`, chatbot trả tài liệu nguyên văn trong khoảng 5 giây. Bật lại Qwen cần CPU có AVX2 |
| Nộp báo cáo cần AI service chạy | Chưa có đường nhận ảnh chưa kiểm khi AI dừng; hàng đợi offline thử lại |
| Chưa có mô hình nhận diện sâu hại | Classifier nhận Healthy và 5 bệnh lá |
| Hoàn thiện coverage tri thức canh tác Farm | Đã có 7 tài liệu tổng quát; trang tập huấn còn chủ yếu mục lục, cần nội dung tài liệu con và nguồn cho các chủ đề còn thiếu |
| Rà nhãn RAG tường minh cho toàn bộ chunk | Chủ dự án đã duyệt bộ câu hỏi và nhãn pilot; qrels hiện chỉ có nhãn dương. Cần rà đủ 0/1/2 trước khi chuyển trạng thái `reviewed`, xem [`data/rag/evaluation/README.md`](../data/rag/evaluation/README.md) |
| Đánh giá chatbot hybrid mở rộng | Rà nhãn 30 câu bổ sung, tách bộ hiệu chỉnh và bộ kiểm tra độc lập. Câu hỏi gián tiếp về mượn tài liệu còn bị lọc ở ngưỡng cosine 0,40; độ bao phủ câu tổng quan Farm cần cải thiện |
| Đánh giá truy xuất trên corpus lớn | Chưa đo gần giới hạn 20.000 đoạn mỗi chỉ mục BM25; số liệu hiện có trên 244 đoạn, với 1/2/5 người dùng đồng thời |
| Đo đầy đủ tải chatbot hybrid | Mới đo rút gọn 12 lượt, 1.369 request; cần chạy đủ 36 lượt theo cấu hình mặc định và đo riêng nhóm câu hỏi bị từ chối |
| Truy xuất legacy dựa trên MiniLM đa ngữ | Diagnosis/ticket vẫn dùng cosine và ngưỡng legacy; diễn đạt gián tiếp còn phụ thuộc embedding, không có reranker như chatbot |
| Luật phân loại của Lab là luật tay | Chưa đo độ chính xác trên dữ liệu thật |
| Kho tri thức Lab chỉ có 12 bài từ 9 văn bản công khai của HaUI | Chưa có bài về máy in, VPN, quy chế phòng máy của khoa, đặt phòng máy; sổ tay LMS là bản năm học 2019-2020 |
| Gợi ý tri thức ticket Lab chưa có hybrid/gate chatbot | Ticket vẫn dùng retrieve legacy; đoạn gần nghĩa chưa chắc đúng sự cố. Chưa đánh giá riêng độ phù hợp trên dữ liệu ticket thật |
| Snapshot thời tiết chỉ có 5 điểm ĐBSCL, chụp ngày 07/10/2026 | Chụp lại nếu cần dữ liệu dự phòng mới hơn |
| Giới hạn tốc độ ở gateway tính theo IP (60 câu hỏi tri thức, 60 lần tạo ticket, 30 lần upload ảnh, chẩn đoán AI, đặt phòng hoặc đính kèm, 120 lượt đăng nhập, 600 lượt API mỗi phút) là số tạm; chưa thử tải | Superset dùng SQLite cho metadata, AI service là một tiến trình; chatbot có hàng đợi rerank CPU giới hạn |

## 5. Chức năng chưa có

| Việc | Ghi chú |
|---|---|
| Chặn SQLi và XSS chung ở Cloudflare | Cần gói trả phí; gói Free chỉ có managed ruleset chặn khai thác đã biết, Backend dùng truy vấn tham số hóa |
| Xóa audit theo thời hạn | Bảng `audit_log` chỉ thêm, chưa có cách xóa theo thời hạn; theo dõi dung lượng bảng |
| Chạy `backup.sh` theo lịch và chép kho ra ngoài máy chủ | Có script, `verify` và job CI; lịch cron, kho từ xa và diễn tập khôi phục định kỳ do người vận hành đặt |
| Cảnh báo đăng nhập sai theo tài khoản | Đã có cảnh báo theo địa chỉ; sự kiện `LOGIN_ERROR` của Keycloak không ghi tên tài khoản (chỉ `user_id`), nên cần thêm `user_id` vào view `vw_auth_events` |
| Metric tài nguyên (CPU, RAM, đĩa) và phân vị độ trễ | Màn hình giám sát chỉ có sự kiện bất thường và trạng thái dịch vụ; thêm Prometheus nếu cần |
| Gộp lớp gửi Mattermost của `monitor` với `ChatWebhook` của core | `dx-monitor` không được phụ thuộc core nên hai nơi cùng đọc webhook; cảnh báo `ACCESS_DENIED` theo địa chỉ vẫn do `AuditAlertJob` của backend gửi |
| Phân trang tra cứu chi tiết ở phía máy chủ | Hiện lấy tối đa 200 bản ghi mới nhất rồi chia trang trong trình duyệt |
| Tên chức năng ở biểu đồ lỗi lấy từ danh sách cố định trong `monitor.js` | Đường dẫn ngoài danh sách hiện là "Chức năng khác"; thêm app mới thì bổ sung danh sách |
| Cảnh báo `monitor` không nhớ điều kiện đã báo sau khi khởi động lại | Khởi động lại trong lúc điều kiện còn thì tin được gửi lại; bộ nhớ chống lặp nằm trong tiến trình |
| Cảnh báo khi chính `monitor` ngừng | Cần một kiểm tra từ ngoài (Cloudflare Health Check hoặc dịch vụ uptime); hiện kiểm bằng `docker compose ps` |
| Backend tự gọi AI sau khi upload ảnh | Chẩn đoán AI do kỹ sư bấm; đang giữ thủ công có chủ đích |
| Push thật (FCM, UnifiedPush) | Hộp thông báo chỉ kéo về khi mở app |
| ClamAV bật mặc định | Đang tắt vì cần khoảng 1 GB RAM; chưa đo trên máy chủ thật |
| Nâng `io.minio:minio` lên 8.6.0 | Sửa CVE-2025-59952 nhưng kéo theo OkHttp 5, cần đổi client lưu trữ và thử với MinIO thật; đang ghi nhận ở `.trivyignore` tới 31/12/2026 |
| Hộp thông báo cho engineer và admin | Họ dùng hàng chờ và Mattermost |
| Phun lặp lại trên cùng một chẩn đoán | Mỗi chẩn đoán một lần phun; phun lại phải qua báo cáo và chẩn đoán mới |
| Thu hoạch thay hộ không có trong timeline workflow | Thu hoạch không có case; thông tin nằm trên vụ và log backend |
| Phạm vi phụ trách theo khu vực cho engineer | Phân quyền hiện theo thao tác, không theo khu vực |
| Thông báo chat chỉ có gửi lại khi lỗi tạm thời | `chat_outbox` lưu tin sau lần gửi đầu thất bại; tin chưa kịp vào bảng khi backend tắt giữa lúc commit và lúc gửi vẫn mất, chưa có cảnh báo khi tồn đọng |
| Trường "Phương pháp áp dụng" và lượng nước của phác đồ | `TreatmentRule.notes` trộn nhiều loại thông tin; cần field riêng được chuyên gia xác thực |
| Email cho thông báo trong hộp thư | Chưa có. Cần `spring-boot-starter-mail`, tắt `management.health.mail` (biến `SPRING_MAIL_HOST` rỗng vẫn tạo bộ gửi và làm `/actuator/health` báo lỗi), hàng đợi gửi lại chung với tin chat, địa chỉ lấy từ Keycloak lúc gửi, bảng lựa chọn của từng tài khoản và màn hình bật tắt |
| Che thông tin cá nhân trong log | Nội dung log chưa đi qua `PiiMask` |
| Mật khẩu demo cũ của `tech1`, `tech2` | Stack có sẵn hai tài khoản này giữ mật khẩu `tech123`; chỉ stack dựng mới nhận `tech1234` |
| Cải tiến core và vận hành chưa làm: metric, Alertmanager, giám sát từ bên ngoài, WAF Coraza, nâng cấp có đường lùi, compose profiles, kiểm giấy phép tự động, Dependabot, sổ kiểm toán chuỗi băm | Chưa làm; xem mục P1 của `docs/DX-Lab-core-ops-improvements.md` |

## 6. Phụ thuộc người ngoài

| Việc | Ảnh hưởng |
|---|---|
| Chuyên gia duyệt `disease_management_policies` (hiện 0/6 policy đủ điều kiện, bảng để trống) | Chẩn đoán chưa trả `management_policy` cho nhánh `NO_CHEMICAL_CONTROL`; có policy được duyệt thì mới thêm trường này vào contract AI và Backend |
| Bổ sung văn bản chuyên môn cho rule đốm nâu | Help 400SC đã mở theo yêu cầu chủ dự án ngày 2026-10-20 với trạng thái `EXPERT_APPROVED`; chưa kèm văn bản chuyên môn riêng. Seed có ba rule; than lá, Tungro và lá khỏe không có rule thuốc |
| Kỹ sư nông nghiệp xác nhận mốc tháng loại vụ và danh mục giống (đang `IN_REVIEW`) | Gợi ý loại vụ và ngày thu hoạch dựa trên số tạm; đổi `app.season-start-months` khi có xác nhận, không cần migration |
| Khoa cung cấp văn bản về máy in, VPN, quy chế và đặt phòng máy | Có văn bản thì thêm bài vào `data/rag/lab/` theo nguyên tắc ở `data/README.md` |
| Ảnh lá lúa thật cho `seed_demo.py --images` | Demo chẩn đoán AI có ý nghĩa; chart "Tỷ lệ AI đúng/sai" có dữ liệu; 5 ảnh RiceLeafDiseaseBD (CC BY 4.0) đã chạy thử ngoài repository, chưa kèm theo |

## 7. Backlog của DX-Lab

- Mobile cho Lab.
- Ticket tương tự bằng `pg_trgm`.
- Leo thang và tự đóng ticket.
- Dashboard Superset riêng của Lab.
- Tích hợp email và thời khóa biểu thật.
- Biến ticket đã giải quyết thành bài tri thức.

## 8. Để sau (dài hạn)

| Việc | Điều kiện đưa vào làm |
|---|---|
| Carbon/AWD, IoT mô phỏng mực nước, tự xác nhận phun từ IoT | Có dữ liệu thu hoạch thật qua ít nhất một vụ hoàn chỉnh |
| Polygon ruộng với PostGIS | Cần tính diện tích chính xác từ bản đồ thay vì nhập tay; GPS điểm đơn đang đủ cho định vị |
| GraphRAG hoặc multi-agent | Corpus tri thức lớn đáng kể, RAG đơn giản không còn đủ |
| Domain và HTTPS cho APK phát hành | Phát hành ngoài phạm vi cuộc thi; địa chỉ backend và Keycloak hiện nhúng trong APK |
| Scale thật (100 đến 100.000 người dùng) | Vận hành thật; hướng mở rộng ở [`COUNCIL_QA.md`](./COUNCIL_QA.md) câu 9 |

## 9. Đã chốt không làm

- Mattermost tương tác hai chiều (duyệt hoặc từ chối ngay trong chat).
- SSO cho Mattermost: Team Edition không hỗ trợ OpenID Connect với IdP tùy ý.
- Frontend low-code (ToolJet, Appsmith) thay cho giao diện tự viết.
