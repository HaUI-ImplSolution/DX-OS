# Kế hoạch kiểm thử: Phase 1

Trong lượt lập kế hoạch chỉ đọc tài liệu và kiểm tra diff, không chạy đánh giá hoặc dịch vụ.
Khi được yêu cầu triển khai và chạy đánh giá, ghi người chạy, ngày và kết quả thực tế vào bảng.

## 1. Chiến lược

- Self-check: một chế độ `--self-check` trong script cho đầu vào, ánh xạ chunk, thứ tự run và metric; dùng ví dụ tính tay, không tạo framework/test file mới.
- Integration: model embedding thật, retriever thật và pgvector chỉ đọc; không dùng fake retrieval để công bố baseline.
- Thủ công: người kiểm tra nhãn, người xem Langfuse và người tái lập phép chấm.
- Môi trường: môi trường đánh giá riêng, model có sẵn và database vector đã ingest bằng quy trình hiện có; không dựng toàn bộ Docker.
- Langfuse chạy local bằng Compose chính thức bên ngoài repository; kiểm tra RAM/disk trước khi bật, không chia sẻ database hoặc volume của sản phẩm.
- Dữ liệu: tài liệu công khai trong repository và câu hỏi đánh giá; không dùng tài khoản, ảnh hoặc báo cáo người dùng.
- Mọi nhóm chấm tại đầu ra retriever, trước ngưỡng 0,40 và gộp tài liệu của Backend.
- Nhóm đo: Farm hỏi đáp k=4; sáu query chẩn đoán mặc định k=3 có mã bệnh; Lab hỏi đáp k=4; query/tham số phải khớp caller thật.
- Collector dùng AI dependency hiện có; score/self-check chỉ cần thư viện chấm, không nối DB, nạp model hoặc yêu cầu Langfuse.
- Chỉ đo Recall và nDCG; không tự thêm chỉ số hoặc LLM judge vào phase.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T1-01 | WP1-01 | Dữ liệu | Kiểm mã câu/chunk trùng, mã lạ, nhãn sai và câu không có nhãn liên quan | Báo lỗi trước chấm; không tự sửa hoặc bỏ mẫu | Chưa chạy | Chưa chạy |
| T1-02 | WP1-01 | Kiểm tra nhãn | Người độc lập đọc câu hỏi và corpus trước khi xem thứ hạng | Có nhận xét, trạng thái và phạm vi kiểm tra thật | Chưa chạy | Chưa chạy |
| T1-03 | WP1-02 | Snapshot | Đối chiếu metadata, nội dung, nguồn và thứ tự chunk; thử thiếu/thừa hoặc ánh xạ không duy nhất | Dừng thu kết quả, không tự chọn một bản ghi để chấm | Chưa chạy | Chưa chạy |
| T1-04 | WP1-02 | Chỉ đọc | Chạy thu kết quả với transaction chỉ đọc, so dữ liệu trước/sau | Không có insert/update/delete/ingest | Chưa chạy | Chưa chạy |
| T1-05 | WP1-02 | Ranh giới | Gọi hỏi đáp với `document_type=None`; gọi node chẩn đoán với mã bệnh, không override query | Namespace đúng; Lab không bị lọc nhầm; query chẩn đoán đúng mặc định; Backend/CV không bị gọi | Chưa chạy | Chưa chạy |
| T1-06 | WP1-02 | Ranking | Dùng trường hợp boost làm đổi thứ tự so với cosine | Run giữ đúng thứ tự retriever trả về; cosine lưu riêng | Chưa chạy | Chưa chạy |
| T1-07 | WP1-02 | Mốc đo | Retriever trả các chunk dưới/trên 0,40 | Giữ nguyên đầu ra và thứ tự, không sao chép bộ lọc Backend hoặc loại điểm thấp | Chưa chạy | Chưa chạy |
| T1-08 | WP1-03 | Metric | Qrels có 2 chunk nhãn 1; top-3 chứa cả hai ở vị trí 1 và 3 | Recall@3=1; nDCG@3 xấp xỉ 0,919720789148 với gain tuyến tính | Chưa chạy | Chưa chạy |
| T1-09 | WP1-03 | Âm tính | Câu trả rỗng, câu thiếu bản ghi, node có lỗi hoặc run có chunk trùng | Chỉ câu rỗng hợp lệ có điểm 0; các lỗi còn lại làm lần chạy không hợp lệ | Chưa chạy | Chưa chạy |
| T1-10 | WP1-03 | Tái lập | Chấm cùng qrels/run bằng lệnh thư viện độc lập | Điểm từng câu và nhóm lệch không quá `1e-8` trước làm tròn | Chưa chạy | Chưa chạy |
| T1-11 | WP1-04 | Dashboard | Mở câu đúng/điểm thấp, xem input/output/metadata và score bằng UI Langfuse | Một observation cho lần gọi thật; cấu hình chunking là metadata, không có span/ứng viên giả; score khớp artifact | Chưa chạy | Chưa chạy |
| T1-12 | WP1-04 | Dịch vụ đánh giá | Sai credential, dừng riêng Langfuse, gửi lại cùng lần chạy | Báo publish chưa thành công, không mất dữ liệu và không tạo câu trùng | Chưa chạy | Chưa chạy |
| T1-13 | WP1-04 | Dữ liệu gửi | Rà payload, artifact và diff trước khi chia sẻ | Không có secret, thông tin thật, checksum, `report/` hoặc `report.zip` | Chưa chạy | Chưa chạy |
| T1-14 | WP1-04 | Bên thứ ba | Người khác tính lại score từ qrels/run và xem hướng dẫn chạy collect | Ghi riêng bằng chứng tính lại điểm và chạy lại retrieval; không tự xác nhận thay người khác | Chưa chạy | Chưa chạy |
| T1-15 | WP1-04 | Self-host | Kiểm tra tài nguyên, cổng, volume riêng; restart Langfuse rồi mở lại experiment | Đủ tài nguyên mới chạy, kết quả còn nguyên và không ảnh hưởng DX-OS | Chưa chạy | Chưa chạy |
| T1-16 | WP1-02 | Nguồn vector | DB có nội dung khớp nhưng không có bằng chứng model đã dùng khi ingest | Không công bố baseline tái lập, không tự suy ra tên model từ số chiều vector | Chưa chạy | Chưa chạy |
| T1-17 | WP1-03 | Dependency | Chạy score/self-check khi DB và Langfuse dừng; kiểm tra diff CI/requirements runtime | Vẫn chấm được artifact; không có dependency đánh giá lọt vào CI/runtime | Chưa chạy | Chưa chạy |
| T1-18 | WP1-04 | Tài nguyên | Chạy collect/score, Ollama và Langfuse theo từng cấu hình; ghi CPU/RAM/disk đỉnh, latency/câu, timeout và restart | Đạt sizing pilot hoặc có quyết định tách máy/bước; không swap, hết disk hoặc ảnh hưởng DX-OS | Chưa chạy | Chưa chạy |
| T1-19 | WP1-02/WP1-04 | Nhiều request | Gửi concurrency đã chốt; giữ collector baseline ở `concurrency=1`, không tạo request giả trong artifact chính | Mỗi request độc lập, không mất/nhân đôi kết quả; lỗi/timeout được ghi riêng; không tuyên bố queue/SLA khi chưa có | Chưa chạy | Chưa chạy |

## 3. Hồi quy (không được hỏng)

- [ ] `ai/app/`, API, database nghiệp vụ, CV, embedding, corpus và ranking không bị sửa.
- [ ] Cấu hình và lệnh khởi động sản phẩm Farm/Lab, Docker và CI giữ nguyên.
- [ ] Dependency đánh giá nằm riêng, không thay môi trường runtime.
- [ ] Chạy self-check của công cụ và test ranking hiện có khi được yêu cầu triển khai/chạy đánh giá; không sửa CI hoặc yêu cầu service mới.
- [ ] Lỗi ngoài phạm vi được ghi nhận, không sửa ké để làm xanh đánh giá.

## 4. Tiêu chí đạt của phase (exit criteria)

Các case bắt buộc đạt, phép đo tái lập được và mọi câu trong tập đã chốt đều có kết quả.
Không đặt điểm tối thiểu để che baseline thấp; kết quả dùng để xác định hạn chế hiện tại.
Nếu nhãn chưa được kiểm tra hoặc chưa có người tái lập thì giữ phase mở và ghi kết quả là nội bộ.
Kết quả này không thay thế kiểm định chuyên môn nông nghiệp hoặc đánh giá câu trả lời cuối.
Pilot phủ tài liệu không đại diện mọi câu hỏi thực địa; không nâng điểm bằng cách bỏ nhãn liên quan chỉ vì nhiều chunk overlap hoặc nhiều hơn k.

## 5. Bằng chứng và báo lỗi

Bộ bằng chứng gồm cấu hình, snapshot được phép chia sẻ, thông tin kiểm tra nhãn, qrels, run, điểm từng câu và đường dẫn experiment.
Artifact thực thi đặt ngoài repository; không lưu trong `report/`, không đưa credential hoặc nội dung nhạy cảm lên Git.
Lỗi công cụ đánh giá được xử lý trong domain; lỗi sản phẩm hoặc dữ liệu chuyên môn chỉ ghi nhận để làm việc riêng.
