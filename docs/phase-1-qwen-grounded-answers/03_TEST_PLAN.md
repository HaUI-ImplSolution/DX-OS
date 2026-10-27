# Kế hoạch kiểm thử: Phase 1

## 1. Chiến lược

Giai đoạn hiện tại chỉ lập kế hoạch; chưa chạy test, model hoặc Docker.
Mặc định triển khai chỉ compile, typecheck và đọc diff theo quy ước Backend.
Test, khởi động app và gọi endpoint/model thật chỉ chạy khi người dùng yêu cầu rõ; ghi người chạy, ngày và bằng chứng.
Không dựng lại toàn stack, global prune hoặc xóa cache/volume sản phẩm để kiểm thử.

Dùng test nhỏ cho hợp đồng/dự phòng, integration cho gateway/migration/quyền và kiểm tay cho khả năng đọc.
Fixture mock không chứng minh Qwen đã được gọi thành công.
Môi trường chạy thật dùng đúng dependency, model và cấu hình hiện có; không đổi Core hoặc timeout hạ tầng để làm kết quả đẹp.
Kiểm cả DXOS_APP=farm/lab; hỏi đáp trên web Farm/Lab và chi tiết chẩn đoán trên web/mobile Farm, gồm client/payload cũ.
Chatbot mobile chỉ kiểm hồi quy request không opt-in, không triển khai hoặc kiểm nghiệm tính năng sinh mới trên màn đó.

## 2. Test case

| ID | WP | Loại | Mô tả | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T1-01 | WP1-01/06 | Ranh giới | So diff nhánh cha, dependency/import, các thư mục bảo vệ và compile test-source host dùng DTO Farm | Core, host/monitor, deploy/CI, model/retriever/embedding/corpus không đổi; chữ ký/constructor cũ vẫn compile | Chưa phân công | Chưa chạy |
| T1-02 | WP1-02 | Model thật | Câu có đủ evidence, dùng đúng stack khóa hiện tại | Có lời gọi Qwen thành công, nội dung diễn giải hợp lệ, mode generated | Chưa phân công | Chưa chạy |
| T1-03 | WP1-02 | Âm tính | JSON sai, quá dài, lặp, thêm field/nguồn/URL lạ hoặc thiếu nội dung | Không hiển thị đầu ra lỗi; fallback đúng mode, giữ evidence | Chưa phân công | Chưa chạy |
| T1-04 | WP1-02 | Thiếu/lỗi dịch vụ | Không evidence, Ollama tắt, timeout, trả rỗng, hai lượt đồng thời | Không evidence thì không gọi Qwen; lỗi sinh vẫn giữ kết quả nghiệp vụ/evidence; không retry vô hạn | Chưa phân công | Chưa chạy |
| T1-05 | WP1-02 | Nội dung/ranh giới | Câu hỏi/evidence chứa chỉ dẫn đổi vai, nguồn bịa, yêu cầu thuốc/liều/PHI | Không thực hiện tool/ghi DB; trường điều trị giữ nguyên; kiểm nội dung sinh và fallback khi vi phạm | Chưa phân công | Chưa chạy |
| T1-06 | WP1-03 | Happy path | Hỏi trên web Farm/Lab có tài liệu, includeAnswer=true | Qwen trả lời đúng namespace; passages vẫn nguyên nghĩa trích dẫn và nguồn | Chưa phân công | Chưa chạy |
| T1-07 | WP1-03/04 | Tương thích | Body/request cũ, thiếu flag; client mới/Backend cũ; Backend mới/AI cũ; AI mới/Backend cũ | Không opt-in thì không gọi model/không thêm trường phần sinh; thiếu endpoint sinh thì fallback; payload cũ không lỗi | Chưa phân công | Chưa chạy |
| T1-08 | WP1-03 | Phân quyền | Không token, token sai, service key thiếu/sai, client tự gửi namespace/model/URL | Giữ 401/403 hiện có; endpoint AI nội bộ được bảo vệ; app cố định namespace | Chưa phân công | Chưa chạy |
| T1-09 | WP1-04 | Chẩn đoán | Chạy có/không includeExplanation; nguồn sai; đọc lại và duyệt; quan sát giao dịch khi chờ model | Giữ CV/rule/evidence; không lưu nguồn ngoài evidence; đọc/duyệt không gọi lại Qwen; không giữ transaction/connection đọc AI hoặc nghiệp vụ trong lúc chờ | Chưa phân công | Chưa chạy |
| T1-10 | WP1-04 | Đồng thời | AI rerun, diagnosis mới hơn hoặc rule đổi khi đang chờ Qwen | Giữ 409 và kiểm version; không lưu giải thích vào sai diagnosis | Chưa phân công | Chưa chạy |
| T1-11 | WP1-04 | Phân quyền dữ liệu | ENGINEER/ADMIN và FARMER đọc PENDING/REJECTED/APPROVED, cả history/report | Không lộ giải thích chưa duyệt; Farm giữ phạm vi hộ của người dùng | Chưa phân công | Chưa chạy |
| T1-12 | WP1-04 | Migration | DB Farm đã có record, schema mới và các response cũ | Cột nullable, record cũ null, không backfill/đổi nội dung cũ; schema Core/Lab giữ nguyên | Chưa phân công | Chưa chạy |
| T1-13 | WP1-05 | Web | Hỏi đáp Farm/Lab, chẩn đoán, nguồn, fallback, note/steps trống | Nhãn cố định, tối đa 3 ý, không JSON/HTML/Markdown thô, nguồn mở được | Chưa phân công | Chưa chạy |
| T1-14 | WP1-05 | Mobile | Chi tiết báo cáo trên màn hẹp 360px, chữ lớn và máy thật khi được phép; hồi quy hỏi đáp cũ | Giải thích chẩn đoán không tràn/cắt ý, che đúng quyền; chatbot vẫn chỉ dùng passages và không gọi Qwen | Chưa phân công | Chưa chạy |
| T1-15 | WP1-06 | Chất lượng sinh | 36 câu pilot và 6 chủ đề chẩn đoán với evidence đúng luồng | Chấm riêng trọng tâm, bám nguồn và dễ đọc; không thay nhãn retrieval | Chưa phân công | Chưa chạy |
| T1-16 | WP1-06 | Hiệu năng | Cold/warm, 1 và 2 yêu cầu đồng thời, model tắt; ghi cấu hình | Thời gian hữu hạn trong timeout hiện có; ghi generated/fallback, latency, CPU/RAM; không làm nghẽn nghiệp vụ | Chưa phân công | Chưa chạy |
| T1-17 | WP1-06 | Rollback | Client cũ, app cũ với DB đã thêm cột, tắt opt-in | Đọc được dữ liệu cũ; không xóa cột/history Flyway; request cũ dùng luồng trích dẫn | Chưa phân công | Chưa chạy |
| T1-18 | WP1-02/03/04/05 | Phân biệt vai trò | Cùng một bệnh được hỏi kiến thức trên web và được CV nhận diện trong report; gọi xen kẽ/đồng thời với Lab | Chatbot trả đúng ý hỏi có nguồn, không gán bệnh cho người dùng; chẩn đoán giải thích đúng CV, không giả quan sát ảnh; không trộn ngữ cảnh; UI có nhãn riêng | Chưa phân công | Chưa chạy |
| T1-19 | WP1-03/04 | Thiếu căn cứ | Câu hỏi chỉ mô tả triệu chứng, tên bệnh mơ hồ, bộ lọc mâu thuẫn, so sánh nhiều bệnh nhưng thiếu nguồn; report thiếu bối cảnh mức độ bệnh | Nêu rõ phần có/thiếu căn cứ, hỏi làm rõ khi cần; không gọi CV từ chatbot, không tạo diagnosis hoặc tự bổ sung mức độ/diện tích/thuốc/liều | Chưa phân công | Chưa chạy |

## 3. Hồi quy không được hỏng

- [ ] Đăng nhập, quyền endpoint, workspace và service key như bản cha.
- [ ] KnowledgeGateway và endpoint retrieval trả cùng dữ liệu khi corpus/query/cấu hình không đổi.
- [ ] Thuốc, liều, PHI, rule version, duyệt và khóa thu hoạch không chịu quyết định từ văn bản Qwen.
- [ ] Chẩn đoán lưu lịch sử, chặn rerun đồng thời và không giữ transaction trong khi gọi AI.
- [ ] Nông dân không xem nội dung chưa được duyệt; báo cáo/ảnh vẫn theo quyền và link có hạn.
- [ ] Ticket, phân loại bằng luật, SLA, mượn thiết bị và đặt phòng Lab giữ hành vi.
- [ ] Chuyển app và khởi động theo README giữ nguyên; không thêm dịch vụ hoặc cổng public.
- [ ] Câu trả lời sinh không bị ghi vào quoted passages hoặc dùng similarity làm điểm đúng.
- [ ] Màn hỏi đáp mobile giữ request, type và giao diện cũ; không thêm nút, opt-in hoặc nội dung Qwen.
- [ ] Chatbot vẫn truy xuất kiến thức bệnh và giữ trích dẫn gốc; profile hỏi đáp và chẩn đoán không dùng lẫn câu kết luận, bối cảnh hoặc nguồn.

## 4. Tiêu chí đạt

Các case bắt buộc phải đạt và có bằng chứng; chưa chạy không được tích.
T1-02 phải chứng minh đầu ra từ model thật, không dùng fallback hoặc mock để coi là generated.
Schema, nguồn hợp lệ, mode, tương thích và bảo vệ quyền phải đạt 100% trong bộ case kiểm.

Với T1-15, mỗi lượt chấm ba tiêu chí 0/1/2: đúng trọng tâm, bám evidence, dễ đọc theo khuôn.
Người chấm là chủ dự án hoặc người được giao kiểm nội dung; công cụ/AI hỗ trợ tổng hợp, không tự xác nhận thay người duyệt.
Mức 0 là sai/không đáp ứng, 1 là đáp ứng một phần, 2 là đáp ứng đầy đủ; lưu nhận xét và người chấm.
Từng lượt còn phải đúng vai trò profile: chatbot không tự xác nhận bệnh, chẩn đoán không giả quan sát tổn thương; không thiếu điều kiện quan trọng hoặc thêm kết luận ngoài evidence/bối cảnh.
Mục tiêu nghiệm thu đề xuất: ít nhất 90% lượt có đủ evidence đạt mức 2 ở cả ba tiêu chí và có câu trả lời generated hợp lệ; báo riêng các nhóm Farm/Lab/chẩn đoán, không che bằng điểm gộp.
Không có ca bịa nguồn, thông số điều trị hoặc diễn giải sai quy định trọng yếu được chấp nhận; fallback phải được đếm riêng.
Nếu chưa đạt, điều chỉnh prompt/giới hạn trong phạm vi và chạy lại bộ cố định; không sửa nhãn, đổi model hoặc tuyên bố đánh giá độc lập.

T1-16 ghi lại cold/warm, số lượt, cấu hình máy, tỉ lệ generated/fallback và thời gian toàn request.
Ngân sách gọi model đề xuất 20 giây; nếu phần lớn lượt chỉ fallback hoặc không nằm trong timeout triển khai hiện có thì dừng nghiệm thu, không tăng timeout Core/deploy để né lỗi.
Mọi lỗi ngoài phạm vi được ghi nhận riêng; không sửa lan sang Core hoặc luồng nghiệp vụ khác.

## 5. Lệnh và bằng chứng

- Kiểm tra tĩnh khi triển khai: `cd backend && ./mvnw compile`, typecheck web/mobile và `git diff --check`; nếu sửa test Java thì `test-compile`.
- Khi có yêu cầu chạy test: pytest AI, contract chẩn đoán và test Backend liên quan; chỉ mở rộng hồi quy khi có thay đổi/failure cần kiểm.
- Khi có yêu cầu chạy app/E2E: dùng luồng Farm/Lab hiện có, không sửa deploy/demo hoặc CI để phục vụ riêng tính năng này.
- Bằng chứng lưu ngoài repository, tách dữ liệu kiểm và model response khỏi cấu hình/credential; không đưa vào report hoặc commit artifact.
- Ghi người chạy, thời gian, môi trường và kết quả vào bảng sau khi thực hiện; không ghi trước kết quả.
