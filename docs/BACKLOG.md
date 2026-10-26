# Việc tồn đọng

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của DX-OS, DX-Farm và DX-Lab.
Làm xong một mục thì xóa dòng đó; không ghi lại diễn biến, chỉ ghi trạng thái hiện tại.

## 1. Trước khi nộp bài

| Việc | Ghi chú |
|---|---|
| Báo cáo kỹ thuật 8-10 trang, slide tối đa 12 | Có dàn ý ở [`SUBMISSION.md`](./SUBMISSION.md) mục 3 |
| Video demo 3-5 phút, rehearsal ít nhất một lần | Kịch bản ở [`SUBMISSION.md`](./SUBMISSION.md) mục 2 |
| Tạo vài issue mẫu trên GitHub | Issues đã bật, hiện chưa có issue nào; lấy từ mục 5 và 6 của tài liệu này |
| Triển khai màn hình giám sát lên VPS | Theo thứ tự: chạy workflow Release (image `backend` và `monitor`), chép `deploy/.env.dev` lên VPS (đã có bốn biến `MONITOR_*` và `KEYCLOAK_READONLY_DB_PASSWORD`), `git pull`, `./update.sh latest`, thêm client `dxos-monitor` vào Keycloak (Admin Console theo `sso/realm-dxos.json`, hoặc `./reset.sh --yes`), rồi kiểm theo `docs/TESTING.md` mục 8.1 |
| Tạo ứng dụng Cloudflare Access thứ tư cho `khuongdev.io.vn`, đường dẫn `monitor/*` | Cùng policy `Admins and engineers`; chỉ làm được trên Cloudflare, xem [`CLOUDFLARE.md`](./CLOUDFLARE.md) |
| Chụp ảnh giao diện hai chế độ (Farm, Lab) và màn hình giám sát lưu vào repository cho hồ sơ | Chưa có; che IP và email thật khi chụp màn hình giám sát |
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
| Màn hình giám sát `/monitor/` trên VPS: đăng nhập `dxos-monitor` qua Cloudflare Tunnel và Access, sự kiện gateway mang IP thật, `http-logger` gửi được từ APISIX trên VPS | Đạt trên stack local (xem `TESTING.md` mục 10); chưa bật trên VPS, cần xong hai việc triển khai ở mục 1 |
| Workflow Release và CI e2e sau khi `backend/Dockerfile` có hai đích và thêm service `monitor` | Local đã build cả hai đích và dựng được; chưa chạy trên GitHub |
| Dựng sạch Lab từ volume rỗng có `monitor` | Local mới dựng sạch Farm; Lab thử khi đổi app trên dữ liệu có sẵn (E2E 16/16); CI e2e dựng sạch cả hai app |
| Màn hình giám sát: `farmer1` mở `/monitor/`, vào thẳng từ phiên đã có của web app, giữ `monitor` dừng 30 giây khi có traffic, `/api/summary` lúc Keycloak dừng, Superset vẫn từ chối FARMER | Đã thử với `engineer1` và các lỗi tham số; các tình huống này chưa thử riêng |
| Thao tác Farm ghi audit từ điện thoại | Qua API đã đạt trên VPS; chưa thử trên thiết bị |
| Hàng đợi 10 ảnh trên điện thoại không bị 429 của gateway | Giới hạn `/api/*` là 600 lượt mỗi phút mỗi IP; đã đạt qua API (601 lượt qua trên VPS), chưa thử trên thiết bị |
| Đăng nhập mobile qua địa chỉ công khai sau khi bật `KC_PROXY_HEADERS` và giới hạn đăng nhập | Log JSON, IP thật, sự kiện Keycloak và audit đã đạt trên VPS; CI đã dựng sạch bằng `--build` |
| Kiểm tay Farm F3 trên điện thoại, kèm ngưỡng GPS và nhãn "Đang cách ly"; icon và splash của mobile (chỉ hiện ở bản build thật, không hiện ở Expo Go) | Kịch bản ở [`TESTING.md`](./TESTING.md); các mục kiểm tay còn lại đã đạt |
| Mobile hiển thị tài liệu tham khảo (chi tiết báo cáo đã duyệt), hỏi đáp dạng danh sách trường và hộp thông báo cuộn tải thêm | Đã qua `tsc` và kiểm hàm tách dòng; chưa mở trên Expo Go hay máy thật |
| Dashboard Superset hiển thị trên giao diện | Bundle và `query_context` đã có test; chưa có test tự động mở dashboard trên Superset thật |

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
