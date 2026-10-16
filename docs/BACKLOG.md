# Việc tồn đọng

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của DX-OS, DX-Farm và DX-Lab.
Làm xong một mục thì xóa dòng đó; không ghi lại diễn biến, chỉ ghi trạng thái hiện tại.

## 1. Trước khi nộp bài

| Việc | Ghi chú |
|---|---|
| Báo cáo kỹ thuật 8-10 trang, slide tối đa 12 | Có dàn ý ở [`SUBMISSION.md`](./SUBMISSION.md) mục 3 |
| Video demo 3-5 phút, rehearsal ít nhất một lần | Kịch bản ở [`SUBMISSION.md`](./SUBMISSION.md) mục 2 |
| Tạo vài issue mẫu trên GitHub | Issues đã bật, hiện chưa có issue nào; lấy từ mục 5 và 6 của tài liệu này |
| Chụp ảnh giao diện hai chế độ (Farm, Lab) lưu vào repository cho hồ sơ | Chưa có |
| Quét lại gitleaks nếu có commit mới trước ngày chấm | Lần gần nhất 15/10/2026: 358 commit, không có phát hiện |
| Chốt ngày hạn nộp thật | Chưa chốt |

## 2. Việc cần sửa trong mã và cấu hình

Không còn việc.

## 3. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Kiểm tay Farm F3 trên điện thoại, kèm ngưỡng GPS và nhãn "Đang cách ly"; icon và splash của mobile (chỉ hiện ở bản build thật, không hiện ở Expo Go) | Kịch bản ở [`TESTING.md`](./TESTING.md); các mục kiểm tay còn lại đã đạt |
| Dashboard Superset hiển thị trên giao diện | Bundle và `query_context` đã có test; chưa có test tự động mở dashboard trên Superset thật |

## 4. Giới hạn của AI và dữ liệu

| Việc | Ghi chú |
|---|---|
| Đánh giá AI mới dùng ảnh công khai | Chẩn đoán đúng 4/80 ảnh (detector riêng 21/80), độ tin cậy cao nhất 0.57, xem [`AI_EVALUATION.md`](./AI_EVALUATION.md); cần ít nhất 100 ảnh ruộng thật có nhãn kỹ sư để đo lại ngưỡng 0.70 và huấn luyện thêm |
| Crop classifier nhận 6/15 ảnh ngô và 15/15 ảnh mía là lúa, chặn 8/50 ảnh lúa nền ruộng | Classifier 10 lớp không có lớp ngô, mía, cỏ; cần thay classifier hoặc thêm dữ liệu ngoài miền; kỹ sư vẫn chốt bệnh |
| Classifier không đối chiếu SHA-256 | Model khác cùng contract vẫn nạp được; chỉ `best.pt` có SHA-256 |
| Ngưỡng độ nét mới thử trên ảnh công khai và ảnh mờ nhân tạo | Chưa có ảnh mờ thật ngoài ruộng; chưa tách vùng lá |
| Nộp báo cáo cần AI service chạy | Chưa có đường nhận ảnh chưa kiểm khi AI dừng; hàng đợi offline thử lại |
| Chưa có mô hình nhận diện sâu hại | Detector chỉ có 5 bệnh lá |
| Kho RAG của Farm chỉ có 6 tài liệu bệnh lúa | Chưa có VietGAP hay quy trình canh tác |
| Truy xuất tri thức dựa trên MiniLM đa ngữ | Câu nêu tên bệnh được xếp đúng; câu chỉ tả triệu chứng phụ thuộc embedding; bộ thử có 15 câu |
| Giới hạn 60 câu hỏi mỗi phút mỗi IP là số tạm, chưa thử tải | `knowledge.min-score` 0.40 loại 7/8 câu ngoài chủ đề; điểm đoạn đầu của câu đúng chủ đề từ 0.50 đến 0.83 |
| Luật phân loại của Lab là luật tay | Chưa đo độ chính xác trên dữ liệu thật |
| Snapshot thời tiết chỉ có 5 điểm ĐBSCL, chụp ngày 07/10/2026 | Chụp lại nếu cần dữ liệu dự phòng mới hơn |
| Giới hạn tốc độ ở gateway tính theo IP là số tạm; chưa thử tải | Superset dùng SQLite cho metadata, AI service là một tiến trình không có hàng đợi |

## 5. Chức năng chưa có

| Việc | Ghi chú |
|---|---|
| Backend tự gọi AI sau khi upload ảnh | Chẩn đoán AI do kỹ sư bấm; đang giữ thủ công có chủ đích |
| Push thật (FCM, UnifiedPush) | Hộp thông báo chỉ kéo về khi mở app |
| Hộp thông báo cho engineer và admin | Họ dùng hàng chờ và Mattermost; mobile chỉ tải 30 thông báo mới nhất, chưa cuộn tải thêm |
| Phun lặp lại trên cùng một chẩn đoán | Mỗi chẩn đoán một lần phun; phun lại phải qua báo cáo và chẩn đoán mới |
| SLA nhắc kỹ sư khi bản khai phun chờ xác nhận lâu | Admin xác nhận thay được |
| Thu hoạch thay hộ không có trong timeline workflow | Thu hoạch không có case; thông tin nằm trên vụ và log backend |
| Nhắc hạn thu hoạch không giới hạn số lần | Nhắc mỗi ngày tới khi thu hoạch hoặc đổi ngày |
| Phạm vi phụ trách theo khu vực cho engineer | Phân quyền hiện theo thao tác, không theo khu vực |
| Retry, hàng đợi bền vững hoặc outbox cho thông báo chat bị lỡ | Hiện gửi một lần, lỗi thì bỏ |

## 6. Phụ thuộc người ngoài

| Việc | Ảnh hưởng |
|---|---|
| Chuyên gia duyệt `disease_management_policies` (hiện 0/6 policy đủ điều kiện, bảng để trống) | Chẩn đoán chưa trả `management_policy` cho nhánh `NO_CHEMICAL_CONTROL`; có policy được duyệt thì mới thêm trường này vào contract AI và Backend |
| Chuyên gia duyệt treatment rule cho bệnh còn lại (đốm nâu, Help 400SC đang `IN_REVIEW`) | Đạo ôn và khô vằn đã có rule được duyệt; bệnh khác chỉ ở `review_required`, không có liều và PHI. Có quyết định duyệt thì cập nhật `data/structured/` và chạy lại `generate_seed_sql.py` |
| Kỹ sư nông nghiệp xác nhận mốc tháng loại vụ và danh mục giống (đang `IN_REVIEW`) | Gợi ý loại vụ và ngày thu hoạch dựa trên số tạm; đổi `app.season-start-months` khi có xác nhận, không cần migration |
| Người phụ trách xác nhận nhãn nền bản đồ Superset | Nhãn tỉnh, thành phố, thị xã lấy từ OpenStreetMap; nhãn đảo, nhãn biển và biên giới đã bỏ; cần người có thẩm quyền xác nhận trước khi công bố |
| Ảnh lá lúa thật cho `seed_demo.py --images` | Demo chẩn đoán AI có ý nghĩa; chart "Tỷ lệ AI đúng/sai" có dữ liệu |

## 7. Backlog của DX-Lab

- Mobile cho Lab.
- Ticket tương tự bằng `pg_trgm`.
- Cảnh báo thiết bị hỏng lặp lại.
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
