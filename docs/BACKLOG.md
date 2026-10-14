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
| Bổ sung các thành viên còn lại vào mục Liên hệ của `README.md` và `README_EN.md` | Hiện chỉ có người duy trì |
| Quét lại gitleaks nếu có commit mới trước ngày chấm | Lần gần nhất 13/10/2026: 345 commit, không có phát hiện |
| Chốt ngày hạn nộp thật | Chưa chốt |

## 2. Việc cần sửa trong mã và cấu hình

| Việc | Chi tiết |
|---|---|
| Tên database vector còn là `dx_farm_ai_vector_db` | Đổi sang tên trung tính cần sửa `AiVectorDatabaseSeeder` và AI service |
| Lịch nền (SLA, nhắc mượn trả, nhắc thu hoạch) chạy ở mọi bản backend | Thông báo có khóa chống trùng nên không gửi lặp, nhưng chạy nhiều bản cần khóa lịch hoặc một bản giữ lịch |
| Tham số `app.*`, `knowledge.*`, `storage.presign-ttl` chỉ đổi được trong `application.yaml` | Chưa truyền qua `deploy/.env`; `HARVEST_REMINDER_CRON` cũng chưa có trong `.env.example` |
| Bảng danh mục mới phải thêm tay vào `READABLE_RELATIONS` của `SupersetReadOnlyRoleSeeder` | Quyền mặc định của role BI đóng; quên thì Superset không đọc được bảng |
| Mobile viết cứng ngưỡng GPS 30 m | `mobile/src/config.ts`; backend đã cấu hình được qua `app.gps-max-accuracy-meters` |
| `knowledge-sources.json` của Farm sao tay từ `data/rag/source_registry.json` | Chưa có kiểm tra lệch |
| Thẻ thửa trên mobile gọi riêng trạng thái cách ly cho từng thửa | Cần endpoint gộp theo danh sách thửa |
| Export dashboard Superset chưa có `query_context` cho từng chart | Dashboard vẫn hiển thị bình thường |
| `vw_ai_diagnosis_outcomes` nhận biết chẩn đoán AI qua `symptom_text` rỗng và `model_version` khác `manual-input-v1` | Dễ sai nếu đổi tên model; nên có cột nguồn trong `diagnoses` |
| `engineer1` vào Superset thấy danh sách dashboard rỗng | Nghi thiếu quyền dataset của role Gamma; chưa quyết định cho kỹ sư xem dashboard hay chỉ admin |

## 3. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Kiểm tay Farm F3 (mobile), F6b, F8 (Superset); Lab L17, API bước 17 (SLA quá hạn thật, đợi 5 phút) và 22 (role BI); các màn hình web Lab | Kịch bản ở [`TESTING.md`](./TESTING.md); các mục còn lại của F, L và API đã chạy ngày 14/10/2026 |
| Thời tiết: gọi endpoint thật và giả lập Open-Meteo lỗi | Chưa có màn hình nên chưa thử qua giao diện |
| Test tự động cho nhắc hạn thu hoạch và thông báo khi duyệt thửa, vụ, chẩn đoán | Thông báo thu hoạch hộ đã có test; các thông báo còn lại đã kiểm tay trên web và điện thoại |
| Test tự động cho luồng mượn trả thiết bị, quyền theo vai và chuyển trạng thái ticket của Lab | Hiện chỉ có E2E; bộ phân loại, cảnh báo SLA và ràng buộc đặt phòng đã có test |
| Dashboard Superset | Kiểm tay, chưa có test tự động |

## 4. Giới hạn của AI và dữ liệu

| Việc | Ghi chú |
|---|---|
| Đánh giá AI mới dùng ảnh công khai | Báo cáo ở [`AI_EVALUATION.md`](./AI_EVALUATION.md): detector chẩn đoán đúng 4/80 ảnh, độ tin cậy cao nhất của dự đoán đúng là 0.57; cần ít nhất 100 ảnh ruộng thật có nhãn kỹ sư để đo lại ngưỡng 0.70 và huấn luyện thêm |
| Crop classifier nhận nhầm cây giống lúa và chặn nhầm lúa nền ruộng | 6/15 ảnh ngô và 15/15 ảnh mía bị nhận là lúa; 8/50 ảnh lúa nền ruộng bị chặn, 5 ảnh là Rice Tungro; classifier có 10 lớp, không có lớp ngô, mía, cỏ; hướng xử lý là thay classifier hoặc bổ sung dữ liệu ngoài miền; kỹ sư vẫn là người chốt bệnh |
| Classifier không đối chiếu SHA-256 | Model khác cùng contract vẫn nạp được; chỉ `best.pt` có SHA-256 |
| Ngưỡng độ nét (20, ảnh nền đơn sắc 0.22) và độ sáng 20-245 mới thử trên ảnh công khai và ảnh làm mờ nhân tạo | Chưa có ảnh mờ do rung tay hay lệch tiêu cự ngoài ruộng; kiểm chất lượng chưa tách vùng lá |
| Nộp báo cáo cần AI service chạy | Chưa có đường nhận ảnh chưa kiểm khi AI dừng; hàng đợi offline thử lại |
| Chưa có mô hình nhận diện sâu hại | Detector chỉ có 5 bệnh lá |
| Chẩn đoán chưa trả `management_policy` cho nhánh `NO_CHEMICAL_CONTROL` | Bảng `disease_management_policies` đã có dữ liệu seed nhưng contract AI và Backend chưa có trường này |
| Kho RAG của Farm chỉ có 6 tài liệu bệnh lúa | Chưa có VietGAP hay quy trình canh tác |
| Truy xuất tri thức dựa trên MiniLM đa ngữ | Câu hỏi nêu tên bệnh được xếp đúng nhờ khớp tên chủ đề; câu chỉ tả triệu chứng phụ thuộc embedding; bộ thử có 15 câu |
| Giới hạn 60 câu hỏi mỗi phút mỗi IP là số tạm, chưa thử tải | Ngưỡng `knowledge.min-score` 0.40 loại 7/8 câu ngoài chủ đề, câu về thời tiết đạt 0.47; điểm đoạn đầu của 15 câu đúng chủ đề từ 0.50 đến 0.83 |
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
| Thời tiết chưa có màn hình web hay mobile, chưa có chart thời tiết | Chỉ có API |
| Trang `/knowledge` chưa có trên mobile | Chỉ có trên web |
| Chart sản lượng và diện tích thu hoạch trên Superset | Dữ liệu đã thu thập, chưa vẽ |
| Phạm vi phụ trách theo khu vực cho engineer | Phân quyền hiện theo thao tác, không theo khu vực |
| Icon và splash mobile vẫn là ảnh mặc định của Expo | |
| Thông báo Mattermost khi chẩn đoán được duyệt, bị từ chối hoặc đã ghi nhận xử lý | |
| Deep link từ thông báo Mattermost sang báo cáo, danh sách báo cáo chờ xử lý | Hướng làm bên dưới |
| Retry, hàng đợi bền vững hoặc outbox cho thông báo chat bị lỡ; tự tạo webhook lúc dựng compose | Hiện best-effort và cấp webhook bằng tay |

Hướng làm deep link:

- Thông báo "Báo cáo sâu bệnh mới" và "Chẩn đoán chờ duyệt" kèm link `<MATTERMOST_APP_BASE_URL>/?report=<id>`; biến mới là địa chỉ web mà trình duyệt người xem truy cập được, rỗng thì không kèm link.
- Web đọc query `report`, gọi `GET /api/v1/disease-reports/{id}` rồi tự chọn thửa, vụ và báo cáo; quyền vẫn do Backend kiểm (FARMER nhận 404 với thửa của người khác); `DiseaseReportResponse` thêm `parcelCode`.
- Sau đó thêm tab "Chờ xử lý" liệt kê báo cáo chưa có chẩn đoán hoặc chẩn đoán `PENDING`, lọc theo thửa và farmer, cần endpoint list theo đúng phạm vi người xem.
- Không làm nút chạy AI ngay trong Mattermost: cần Mattermost gọi ngược Backend kèm xác thực.

## 6. Phụ thuộc người ngoài

| Việc | Ảnh hưởng |
|---|---|
| Chuyên gia duyệt treatment rule cho bệnh còn lại (đốm nâu, Help 400SC đang `IN_REVIEW`) | Đạo ôn và khô vằn đã có rule được duyệt; bệnh khác chỉ ở `review_required`, không có liều và PHI. Có quyết định duyệt thì cập nhật `data/structured/` và chạy lại `generate_seed_sql.py` |
| Kỹ sư nông nghiệp xác nhận mốc tháng loại vụ và danh mục giống (đang `IN_REVIEW`) | Gợi ý loại vụ và ngày thu hoạch dựa trên số tạm; đổi `app.season-start-months` khi có xác nhận, không cần migration |
| Người phụ trách xác nhận nền bản đồ Superset (đang nền trắng) | Dashboard bản đồ |
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
