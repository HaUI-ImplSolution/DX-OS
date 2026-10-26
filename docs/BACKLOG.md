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
| Quét lại gitleaks nếu có commit mới trước ngày chấm | Lần gần nhất 23/10/2026: 434 commit, không có phát hiện |
| Chốt ngày hạn nộp thật | Chưa chốt |

## 2. Việc cần sửa trong mã và cấu hình

| Việc | Ghi chú |
|---|---|
| Chặn lượt chẩn đoán AI thứ hai khi báo cáo đang có một lượt chạy | Rời trang hoặc tải lại khi AI đang chạy rồi bấm lại thì lượt sau chạy hết thời gian rồi nhận 409 `Diagnosis changed while AI was processing`; kết quả của lượt đầu vẫn được lưu, nhưng có thể sinh hai chẩn đoán chờ duyệt cho một báo cáo |
| Mobile hiển thị đoạn có nhãn dài hơn 30 ký tự dưới dạng văn bản thô | `mobile/src/passageText.ts` giữ giới hạn 30; web đã dùng 40 (nhãn dài nhất trong dữ liệu là 34 ký tự, `Kiểm soát hóa học không định lượng`) |

## 3. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Thao tác Farm ghi audit từ điện thoại | Qua API đã đạt trên VPS; chưa thử trên thiết bị |
| Hàng đợi 10 ảnh trên điện thoại không bị 429 của gateway | Giới hạn `/api/*` là 600 lượt mỗi phút mỗi IP; đã đạt qua API (601 lượt qua trên VPS), chưa thử trên thiết bị |
| Đăng nhập mobile qua địa chỉ công khai sau khi bật `KC_PROXY_HEADERS` và giới hạn đăng nhập | Log JSON, IP thật, sự kiện Keycloak và audit đã đạt trên VPS; CI đã dựng sạch bằng `--build` |
| Kiểm tay Farm F3 trên điện thoại, kèm ngưỡng GPS và nhãn "Đang cách ly"; icon và splash của mobile (chỉ hiện ở bản build thật, không hiện ở Expo Go) | Kịch bản ở [`TESTING.md`](./TESTING.md); các mục kiểm tay còn lại đã đạt |
| Mobile hiển thị tài liệu tham khảo (chi tiết báo cáo đã duyệt), hỏi đáp dạng danh sách trường và hộp thông báo cuộn tải thêm | Đã qua `tsc` và kiểm hàm tách dòng; chưa mở trên Expo Go hay máy thật |
| Dashboard Superset hiển thị trên giao diện | Bundle và `query_context` đã có test; chưa có test tự động mở dashboard trên Superset thật |
| Bên thứ ba tái lập đánh giá RAG | Công cụ đã chạy thật và CLI khớp; cần người khác tự tính lại từ qrels/run, ghi riêng với việc chạy lại retrieval. Hướng dẫn ở [`ai/README.md`](../ai/README.md#đánh-giá-truy-xuất-rag) |

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
| Kho RAG của Farm chỉ có 6 tài liệu bệnh lúa | Chưa có VietGAP hay quy trình canh tác |
| Rà nhãn RAG tường minh cho toàn bộ chunk | Chủ dự án đã duyệt bộ câu hỏi và nhãn pilot; qrels hiện chỉ có nhãn dương. Cần rà đủ 0/1/2 trước khi chuyển trạng thái `reviewed`, xem [`data/rag/evaluation/README.md`](../data/rag/evaluation/README.md) |
| Truy xuất tri thức dựa trên MiniLM đa ngữ | Câu nêu tên bệnh được xếp đúng; câu chỉ tả triệu chứng phụ thuộc embedding; bộ thử có 15 câu; `knowledge.min-score` 0.40 loại 7/8 câu ngoài chủ đề, điểm đoạn đầu của câu đúng chủ đề từ 0.50 đến 0.83 |
| Luật phân loại của Lab là luật tay | Chưa đo độ chính xác trên dữ liệu thật |
| Kho tri thức Lab chỉ có 12 bài từ 9 văn bản công khai của HaUI | Chưa có bài về máy in, VPN, quy chế phòng máy của khoa, đặt phòng máy; sổ tay LMS là bản năm học 2019-2020 |
| Câu hỏi Lab ngoài chủ đề vẫn có thể hiện đoạn trên ngưỡng 0.40 | "Máy in kẹt giấy" hiện quy định phòng thi ở 0.58 đến 0.64, cao hơn vài câu đúng (máy chiếu 0.52); đổi ngưỡng không tách được, cần embedding hoặc cách xếp hạng khác |
| Snapshot thời tiết chỉ có 5 điểm ĐBSCL, chụp ngày 07/10/2026 | Chụp lại nếu cần dữ liệu dự phòng mới hơn |
| Giới hạn tốc độ ở gateway tính theo IP (60 câu hỏi tri thức, 60 lần tạo ticket, 30 lần upload ảnh, chẩn đoán AI, đặt phòng hoặc đính kèm, 120 lượt đăng nhập, 600 lượt API mỗi phút) là số tạm; chưa thử tải | Superset dùng SQLite cho metadata, AI service là một tiến trình không có hàng đợi |

## 5. Chức năng chưa có

| Việc | Ghi chú |
|---|---|
| Chặn SQLi và XSS chung ở Cloudflare | Cần gói trả phí; gói Free chỉ có managed ruleset chặn khai thác đã biết, Backend dùng truy vấn tham số hóa |
| Ghi `AuditOutcome.FAILED` khi thao tác quản trị lỗi | Hiện chỉ ghi `SUCCESS` và `DENIED`; thao tác lỗi trả mã lỗi mà không có bản ghi audit |
| Màn hình xem audit trên web, dashboard Superset từ `audit_log`, xóa audit theo thời hạn | Hiện chỉ có `GET /api/v1/audit-logs` và truy vấn SQL; bảng chỉ thêm, chưa có cách xóa theo thời hạn |
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
