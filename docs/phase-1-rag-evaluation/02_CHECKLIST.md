# Checklist: Phase 1

`[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ có lý do.
Checklist dành cho triển khai sau khi kế hoạch được duyệt; chưa có mục triển khai nào được xác nhận hoàn tất.

## WP1-01: Bộ đề và nhãn chuẩn

Kết quả đầu ra: bộ đề cố định, nhãn theo chunk và người kiểm tra độc lập được ghi nhận thật.

- [ ] WP1-01-DOC-01 Chốt Q1–Q2, quy mô pilot, mốc đo tại retriever và nhãn 0/1/2.
- [ ] WP1-01-DATA-01 [farm] Soạn 12 câu hỏi đáp, nhãn nháp và dẫn chứng theo chunk.
- [ ] WP1-01-DATA-02 [farm] Lập sáu trường hợp tham khảo chẩn đoán theo query và mã bệnh thật của node.
- [ ] WP1-01-DATA-03 [lab] Soạn 24 câu hỏi đáp, nhãn nháp và dẫn chứng theo chunk.
- [ ] WP1-01-DATA-04 [farm] Đối chiếu khóa nhãn, nguồn corpus và phiên bản bộ đề; không sao chép corpus vào repository.
- [ ] WP1-01-DATA-05 [lab] Đối chiếu khóa nhãn, nguồn corpus và phiên bản bộ đề; rà đủ nhãn tường minh.
- [ ] WP1-01-DOC-02 Giao bộ nhãn để người độc lập kiểm tra; ghi nhận kết quả thật theo từng phần, chia phiên rà không quá nửa ngày.
- [ ] WP1-01-DATA-07 [farm] Xử lý nhãn còn bất đồng và đóng băng phần Farm khi đủ kiểm tra.
- [ ] WP1-01-DATA-08 [lab] Xử lý nhãn còn bất đồng và đóng băng phần Lab khi đủ kiểm tra.

## WP1-02: Thu kết quả truy xuất

Kết quả đầu ra: kết quả của mọi câu từ retriever thật, không ghi database và không thay runtime.

- [ ] WP1-02-AI-01 Tạo bước collect, snapshot chỉ đọc, đối chiếu corpus và bằng chứng ingest đúng model.
- [ ] WP1-02-AI-02 Dùng retriever/node hiện có, đúng query, `document_type`, namespace, k và thứ tự sau boost.
- [ ] WP1-02-AI-03 Xuất nguyên top-k, run và bản ghi câu rỗng/lỗi; không lọc lại hoặc dựng trace trung gian.
- [ ] WP1-02-TEST-01 Kiểm tra nhóm Farm hỏi đáp và Farm chẩn đoán.
- [ ] WP1-02-TEST-02 Kiểm tra nhóm Lab hỏi đáp và ranh giới namespace.

## WP1-03: Tính Recall và nDCG

Kết quả đầu ra: điểm từng câu/nhóm, tính lại được bằng provider được ghi nhận.

- [ ] WP1-03-AI-01 Tách dependency đánh giá, chốt phiên bản và provider.
- [ ] WP1-03-AI-02 Chấm bằng `ir_measures`, kiểm tra đủ câu và nhãn hợp lệ; score không cần model/DB/Langfuse.
- [ ] WP1-03-AI-03 Xuất kết quả thô và tổng hợp theo nhóm, k và trạng thái nhãn.
- [ ] WP1-03-TEST-01 Thêm một `--self-check` trong script cho input, thứ tự và metric; không thêm file test/dependency vào CI.
- [ ] WP1-03-TEST-02 Đối chiếu ví dụ tính tay, kết quả rỗng và lệnh chấm độc lập.
- [ ] WP1-03-DOC-01 Ghi giấy phép dependency đánh giá; xác nhận runtime SBOM có cần đổi hay không theo quy định.

## WP1-04: Langfuse và bàn giao

Kết quả đầu ra: experiment trực quan, score khớp bản xuất và bên thứ ba chạy lại được.

- [ ] WP1-04-DOC-01 Chốt Q3, tài nguyên máy, dữ liệu được gửi và quyền project.
- [ ] WP1-04-INFRA-01 Chuẩn bị Compose Langfuse chính thức ngoài repository, release/cổng/project/volume riêng và cấu hình bí mật cục bộ.
- [ ] WP1-04-INFRA-02 Khi đủ tài nguyên, kiểm tra UI local và kết nối từ môi trường chạy script; không khởi động lại DX-OS.
- [ ] WP1-04-AI-01 Dùng SDK chính thức và giao diện có sẵn trong bước publish riêng, không thêm REST client/UI/callback runtime.
- [ ] WP1-04-AI-02 Gửi dataset, nhãn, observation của lần gọi thật và score; dùng ID của SDK để chống trùng.
- [ ] WP1-04-TEST-01 Kiểm tra giao diện từng câu, score và thông tin cấu hình trên Langfuse.
- [ ] WP1-04-TEST-02 Kiểm tra lỗi mạng/credential và gửi lại từ kết quả đã lưu.
- [ ] WP1-04-DOC-02 Viết hướng dẫn ngắn để người khác cài môi trường đánh giá và tính lại điểm.
- [ ] WP1-04-TEST-03 Ghi nhận lần tái lập của người khác; không tự tick thay.

## Đóng phase

- [ ] Các case bắt buộc ở `03_TEST_PLAN.md` đạt, ghi người chạy, ngày và bằng chứng thật.
- [ ] Chỉ có một script và một self-check; script không chép lại thuật toán RAG hoặc xử lý Backend.
- [ ] Hai chỉ số được báo đúng phạm vi; điểm thấp và mọi câu thất bại vẫn được giữ lại.
- [ ] Dữ liệu đánh giá trên Langfuse khớp bản xuất; nhãn nội bộ không được ghi là độc lập.
- [ ] Đọc toàn bộ diff và xác nhận chỉ có file trong danh sách cho phép ở `01_PLAN.md`.
- [ ] Backend, frontend, mobile, `ai/app/`, corpus nghiệp vụ, deploy và CI không có diff của phase.
- [ ] Cấu hình, secret và dữ liệu Langfuse local nằm ngoài repository; không có image build hoặc volume sản phẩm bị thay đổi.
- [ ] Không đổi endpoint/schema, không cần sửa Swagger; cách khởi động Farm/Lab giữ nguyên.
- [ ] Rà file thử nghiệm, script trùng, kết quả chạy, cache, dependency và tài liệu thừa trước bàn giao.
- [ ] Không có secret, dữ liệu thật, checksum, `report/` hoặc `report.zip` trong diff; không sửa `.gitignore`.
- [ ] README, TESTING, LICENSES và CHANGELOG chỉ cập nhật phần liên quan; lịch sử changelog giữ nguyên.
- [ ] Phân biệt bằng chứng tính lại score từ artifact với bằng chứng chạy lại truy xuất; không khẳng định hai việc là một.
- [ ] Mọi tiêu chí mục 6 của `00_OVERVIEW.md` có bằng chứng; chưa có kiểm tra nhãn hoặc người tái lập thì chưa đóng phase.
- [ ] Khi hoàn tất bàn giao, chuyển việc còn lại sang backlog và xóa thư mục phase theo template.
