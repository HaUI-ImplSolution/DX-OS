# Việc tồn đọng

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của DX-OS, DX-Farm và DX-Lab.
Làm xong một mục thì xóa dòng đó; không ghi lại diễn biến, chỉ ghi trạng thái hiện tại.

## 1. Trước khi nộp bài

| Việc | Ghi chú |
|---|---|
| Báo cáo kỹ thuật 8-10 trang, slide tối đa 12 | Có dàn ý ở [`SUBMISSION.md`](./SUBMISSION.md) mục 3 |
| Video demo 3-5 phút, rehearsal ít nhất một lần | Kịch bản ở [`SUBMISSION.md`](./SUBMISSION.md) mục 2 |
| Tạo vài issue mẫu trên GitHub | Issues đã bật, hiện chưa có issue nào; lấy từ mục 5 và 6 của tài liệu này |
| Vẽ thêm Monitor vào `docs/images/tong_quan.gif` | README, README_EN và `ARCHITECTURE.md` đã có Monitor; ảnh thêm ô Monitor và ba đường Gateway → Monitor, Monitor → Mattermost, Monitor → PostgreSQL |
| Chụp ảnh giao diện hai chế độ (Farm, Lab) và màn hình giám sát lưu vào repository cho hồ sơ | Chưa có; che IP và email thật khi chụp màn hình giám sát |
| Quét lại gitleaks nếu có commit mới trước ngày chấm | Lần gần nhất 23/10/2026: 434 commit, không có phát hiện |
| Chốt ngày hạn nộp thật | Chưa chốt |

## 2. Việc cần sửa trong mã và cấu hình

| Việc | Phạm vi |
|---|---|
| Giữ đúng điều kiện và đối tượng trong trả lời Lab | Có ca gán mức cấm thư viện 30 ngày vào sai hành vi; cần đối chiếu từng kết luận với điều kiện trong nguồn |
| Làm rõ các nhánh hướng dẫn Wi-Fi | Còn câu trộn đăng nhập tài khoản với truy cập miễn phí; cần diễn đạt riêng theo đối tượng sử dụng |
| Tăng độ bao phủ trả lời Qwen | Có cách hỏi mô tả bệnh khiến JSON chưa hoàn chỉnh trong giới hạn 192 token; bản nháp bị ẩn, nguồn vẫn giữ. Cần cải thiện tỷ lệ trả lời hợp lệ, không bỏ chốt thuốc/liều/PHI và nguồn |
| Ổn định test biên 30 ngày đặt phòng Lab | `LabBookingIntegrationTest` đặt lúc 08:00 ngày thứ 30 nhưng service giới hạn 30 × 24 giờ; test phụ thuộc giờ chạy, cần cố định `Clock` |

## 3. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Bên thứ ba tái lập đánh giá RAG | Công cụ đã chạy thật và CLI khớp; cần người khác tự tính lại từ qrels/run, ghi riêng với việc chạy lại retrieval. Hướng dẫn ở [`ai/README.md`](../ai/README.md#đánh-giá-truy-xuất-rag) |
| Chấm nội dung Qwen trên 36 câu hỏi và 6 chủ đề chẩn đoán | Chưa có bảng chấm trọng tâm, bám nguồn và dễ đọc do người duyệt xác nhận; tỷ lệ `generated` không phải độ đúng nội dung |
| Chữ lớn trên mobile native | Đã typecheck, kiểm render và nghiệm thu chức năng; chưa đo riêng bố cục khi tăng cỡ chữ trên máy thật |
| Khởi động bản Qwen từ clone sạch | Đã kiểm bằng image/cache và các cặp phiên bản; chưa chạy lại full build/seed toàn stack từ clone sạch |

## 4. Giới hạn của AI và dữ liệu

| Việc | Ghi chú |
|---|---|
| Model bệnh chưa được đánh giá độc lập ngoài ruộng | Accuracy 89,43% trên 1.466 ảnh theo người huấn luyện, xem [`AI_EVALUATION.md`](./AI_EVALUATION.md); cần ít nhất 100 ảnh ruộng thật có nhãn kỹ sư để đo toàn pipeline và ngưỡng 0.70 |
| Crop classifier nhận 6/15 ảnh ngô và 15/15 ảnh mía là lúa, chặn 8/50 ảnh lúa nền ruộng | Classifier 10 lớp không có lớp ngô, mía, cỏ; cần thay classifier hoặc thêm dữ liệu ngoài miền; kỹ sư vẫn chốt bệnh |
| Crop classifier không đối chiếu SHA-256 | Model khác cùng contract vẫn nạp được; chỉ checkpoint bệnh có SHA-256 |
| Ngưỡng độ nét mới thử trên ảnh công khai và ảnh mờ nhân tạo | Chưa có ảnh mờ thật ngoài ruộng; chưa tách vùng lá |
| Chẩn đoán AI chạy trên CPU mất khoảng 50 giây mỗi ảnh | Đo trên máy chủ 8 nhân; chưa có GPU hay mô hình nhỏ hơn để rút ngắn; lần gọi đầu sau khi khởi động lại AI nạp mô hình và có thể vượt 60 giây chờ của gateway, nhận 504 (kết quả vẫn được tạo) |
| Nộp báo cáo cần AI service chạy | Chưa có đường nhận ảnh chưa kiểm khi AI dừng; hàng đợi offline thử lại |
| Chưa có mô hình nhận diện sâu hại | Classifier nhận Healthy và 5 bệnh lá |
| Rà điều khoản nguồn Farm mở rộng | Owner đã duyệt dùng cho RAG; các cờ CHECK_LICENSE_TERMS vẫn giữ, cần xác nhận quyền lưu/phân phối nội dung từng nguồn trước khi công bố corpus |
| Hoàn thiện coverage tri thức canh tác Farm | Đã có 7 tài liệu tổng quát; trang tập huấn còn chủ yếu mục lục, cần nội dung tài liệu con và nguồn cho các chủ đề còn thiếu |
| Rà nhãn RAG tường minh cho toàn bộ chunk | Chủ dự án đã duyệt bộ câu hỏi và nhãn pilot; qrels hiện chỉ có nhãn dương. Cần rà đủ 0/1/2 trước khi chuyển trạng thái `reviewed`, xem [`data/rag/evaluation/README.md`](../data/rag/evaluation/README.md) |
| Đánh giá chatbot hybrid mở rộng | Bộ 72 query có thêm 30 câu nhãn nháp; cần rà đủ nhãn, tách calibration/holdout và hiệu chỉnh ngưỡng từng app bằng người đánh giá độc lập. Recall nhóm tổng quan Farm còn hạn chế; so sánh trên cùng snapshot, không suy diễn điểm retrieval thành độ đúng Qwen. |
| Đo đầy đủ workload đánh giá hybrid | Lần tải mới nhất là smoke 12 lượt/888 request; cần chạy lại mặc định 36 lượt và mode all với cấu hình cuối, thêm workload tải riêng nhóm từ chối |
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
| Cảnh báo cho sự kiện đăng nhập sai của Keycloak | Màn hình giám sát chỉ hiển thị; khóa tạm của Keycloak và giới hạn tốc độ của gateway đã chặn |
| Metric tài nguyên (CPU, RAM, đĩa) và phân vị độ trễ | Màn hình giám sát chỉ có sự kiện bất thường và trạng thái dịch vụ; thêm Prometheus nếu cần |
| Gộp lớp gửi Mattermost của `monitor` với `ChatWebhook` của core | `dx-monitor` không được phụ thuộc core nên hai nơi cùng đọc webhook; cảnh báo `ACCESS_DENIED` theo địa chỉ vẫn do `AuditAlertJob` của backend gửi |
| Phân trang tra cứu chi tiết ở phía máy chủ | Hiện lấy tối đa 200 bản ghi mới nhất rồi chia trang trong trình duyệt |
| Tên chức năng ở biểu đồ lỗi lấy từ danh sách cố định trong `monitor.js` | Đường dẫn ngoài danh sách hiện là "Chức năng khác"; thêm app mới thì bổ sung danh sách |
| Cảnh báo `monitor` không nhớ điều kiện đã báo sau khi khởi động lại | Khởi động lại trong lúc điều kiện còn thì tin được gửi lại; bộ nhớ chống lặp nằm trong tiến trình |
| Cảnh báo khi chính `monitor` ngừng | Cần một kiểm tra từ ngoài (Cloudflare Health Check hoặc dịch vụ uptime); hiện kiểm bằng `docker compose ps` |
| Truyền `request_id` từ Backend sang AI service | Truy vết dừng ở Backend; log của AI chưa mang `request_id` |
| Backend tự gọi AI sau khi upload ảnh | Chẩn đoán AI do kỹ sư bấm; đang giữ thủ công có chủ đích |
| Push thật (FCM, UnifiedPush) | Hộp thông báo chỉ kéo về khi mở app |
| Hộp thông báo cho engineer và admin | Họ dùng hàng chờ và Mattermost |
| Phun lặp lại trên cùng một chẩn đoán | Mỗi chẩn đoán một lần phun; phun lại phải qua báo cáo và chẩn đoán mới |
| Thu hoạch thay hộ không có trong timeline workflow | Thu hoạch không có case; thông tin nằm trên vụ và log backend |
| Phạm vi phụ trách theo khu vực cho engineer | Phân quyền hiện theo thao tác, không theo khu vực |
| Retry, hàng đợi bền vững hoặc outbox cho thông báo chat bị lỡ | Hiện gửi một lần, lỗi thì bỏ |
| Trường "Phương pháp áp dụng" và lượng nước của phác đồ | `TreatmentRule.notes` trộn nhiều loại thông tin; cần field riêng được chuyên gia xác thực |

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
