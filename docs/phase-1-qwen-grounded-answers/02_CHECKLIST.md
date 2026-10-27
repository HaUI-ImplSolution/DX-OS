# Checklist: Phase 1

`[ ]` chưa làm, `[~]` đang làm, `[x]` đã có bằng chứng, `[-]` không áp dụng kèm lý do.
Hiện chỉ hoàn thành khảo sát và lập kế hoạch; không tích các mục triển khai hoặc chạy thử.

## WP1-01: Hợp đồng và ranh giới

- [x] WP1-01-DOC-01 Đọc template, kiến trúc, quy ước Backend và các skill AI/Spring Boot liên quan.
- [x] WP1-01-DOC-02 Cập nhật Khuong/main bằng fast-forward và tạo feat/qwen-grounded-answers từ nhánh cha mới nhất.
- [x] WP1-01-DOC-03 Viết đủ overview, plan, checklist, test plan và rủi ro; tách phần sinh khỏi trích dẫn.
- [ ] WP1-01-DOC-04 Chủ dự án duyệt kế hoạch, gồm cột lưu giải thích Farm và opt-in tương thích.
- [ ] WP1-01-BE-01 [farm] Swagger hỏi đáp/chẩn đoán và DTO mô tả trường mới, mode và quyền xem.
- [ ] WP1-01-BE-02 [lab] Swagger hỏi đáp và DTO mô tả trường mới, mode và dự phòng.
- [ ] WP1-01-BE-03 [farm] Giữ constructor/signature đang được host dùng; không sửa host để chạy theo DTO mới.
- [ ] WP1-01-TEST-01 Kiểm T1-01: ranh giới Core và các file ngoài domain không đổi.

## WP1-02: Bộ tổng hợp Qwen

- [ ] WP1-02-AI-01 Kiểm tra capability JSON/schema trên đúng Ollama, Qwen và dependency hiện có; không tự nâng version.
- [ ] WP1-02-AI-02 Định nghĩa schema, giới hạn nội dung, nguồn hợp lệ và metadata do server cấp.
- [ ] WP1-02-AI-03 Tái sử dụng ChatOllama/prompt, nhận evidence được cấp và tạo trả lời có cấu trúc.
- [ ] WP1-02-AI-06 Profile farm_qa/lab_qa/farm_diagnosis riêng; server cố định vai trò, không dùng chung ngữ cảnh giữa lượt.
- [ ] WP1-02-AI-04 Kiểm đầu ra, nguồn, lặp, nội dung cấm của Farm và mode dự phòng; không dùng gate nguyên văn cho câu sinh.
- [ ] WP1-02-AI-05 Endpoint tổng hợp nội bộ có service key, timeout hữu hạn và không retry model.
- [ ] WP1-02-AI-07 Giới hạn input/token; chỉ gọi LLM khi opt-in và có căn cứ, không thêm phụ thuộc startup/health.
- [ ] WP1-02-TEST-01 Chuẩn bị case JSON sai, nguồn bịa, thiếu evidence, prompt injection, lỗi/timeout và cấm phát sinh thuốc/liều/PHI.
- [ ] WP1-02-TEST-02 Khi được yêu cầu, chạy T1-02 đến T1-05 và ghi bằng chứng gọi model thật riêng với mock.

## WP1-03: Hỏi đáp web hai app

- [ ] WP1-03-BE-01 [farm] Giữ KnowledgeGateway lấy evidence, thêm AiGateway tổng hợp khi opt-in.
- [ ] WP1-03-BE-02 [lab] Tương tự cho namespace lab, không đổi nghiệp vụ ticket/SLA/thiết bị/phòng.
- [ ] WP1-03-BE-03 [farm] DTO answer riêng, giữ question/passages và body request.
- [ ] WP1-03-AI-01 Hỏi kiến thức bệnh vẫn lấy corpus/bộ lọc hiện có; không biến câu hỏi thành chẩn đoán hoặc tự điền thuốc/liều/PHI.
- [ ] WP1-03-BE-04 [lab] DTO answer riêng, giữ question/passages và body request.
- [ ] WP1-03-TEST-01 Case request cũ/mới, không evidence, lỗi Qwen, namespace và quyền endpoint.
- [ ] WP1-03-TEST-02 Kiểm T1-06 đến T1-08 khi được yêu cầu; diff Core bằng không.

## WP1-04: Giải thích chẩn đoán Farm

- [ ] WP1-04-AI-01 Nối bộ tổng hợp sau graph khi include_explanation=true, giữ facts/rule/evidence.
- [ ] WP1-04-AI-03 Giải thích đúng CV/bối cảnh được cấp; không giả quan sát tổn thương hoặc thêm mức độ nặng/diện tích.
- [ ] WP1-04-BE-01 [farm] Thêm opt-in, ánh xạ generated_explanation sang phần lưu/response.
- [ ] WP1-04-DATA-01 [farm] Thêm migration nullable mới, kiểm số thứ tự; không sửa migration cũ hoặc backfill.
- [ ] WP1-04-BE-02 [farm] Lưu nội dung/version/mode/nguồn theo diagnosis; đọc và duyệt không gọi lại model.
- [ ] WP1-04-BE-04 [farm] Kiểm nguồn phần sinh thuộc evidence của đúng diagnosis trước khi lưu.
- [ ] WP1-04-BE-03 [farm] Kiểm mọi mapper trả FARMER để che nội dung mới khi chưa được phép.
- [ ] WP1-04-TEST-01 Case history, AI rerun 409, rule version, PENDING/REJECTED/APPROVED và dữ liệu cũ.
- [ ] WP1-04-TEST-02 Kiểm T1-09 đến T1-12 khi được yêu cầu; không giữ transaction lúc chờ AI.
- [ ] WP1-04-AI-02 Giải phóng giao dịch đọc sau khi materialize evidence, trước khi gọi Qwen; không đổi lifecycle session chung.

## WP1-05: Giao diện

- [ ] WP1-05-FE-01 Trang hỏi đáp dùng chung đọc answer optional, gửi opt-in và có khuôn cố định.
- [ ] WP1-05-FE-02 Farm hiển thị giải thích riêng với thuốc/evidence; Lab giữ nhãn nghiệp vụ phù hợp.
- [ ] WP1-05-FE-03 Nguồn mở rộng được; không dùng similarity làm điểm đúng câu trả lời; xử lý fallback/thiếu context.
- [ ] WP1-05-FE-04 Khuôn hỏi đáp và giải thích chẩn đoán có nhãn/nội dung phù hợp riêng; không ép đủ mục hoặc thêm lời dẫn/lưu ý chung.
- [ ] WP1-05-MB-01 Chi tiết báo cáo đã duyệt hiện phần giải thích; không hiện nội dung bị che; chatbot mobile không sửa.
- [ ] WP1-05-TEST-01 T1-13 và T1-14: payload cũ, đoạn dài, chữ lớn, nội dung chứa Markdown/HTML và thiết bị thật khi được phép.

## WP1-06: Kiểm chứng và bàn giao

- [ ] WP1-06-TEST-01 Compile backend, typecheck web/mobile, kiểm tra Python và đọc diff theo phạm vi.
- [ ] WP1-06-TEST-02 Sau yêu cầu chạy test rõ ràng, chạy test AI/Backend liên quan và kiểm tra contract chẩn đoán.
- [ ] WP1-06-TEST-03 Sau yêu cầu chạy thật rõ ràng, kiểm cả Farm/Lab và model thật; không dựng lại toàn stack hoặc xóa cache sản phẩm.
- [ ] WP1-06-TEST-04 Rà nội dung sinh trên 36 câu pilot và 6 chủ đề chẩn đoán; ghi người chấm và từng tiêu chí T1-15.
- [ ] WP1-06-TEST-05 Đo generated/fallback, độ trễ, CPU/RAM, cold/warm và hai yêu cầu đồng thời theo T1-16.
- [ ] WP1-06-TEST-06 Kiểm rollback, migration/record cũ và opt-in theo T1-17.
- [ ] WP1-06-TEST-07 Kiểm cặp hỏi đáp/chẩn đoán cùng bệnh, câu thiếu bối cảnh và tách profile theo T1-18, T1-19.
- [ ] WP1-06-DOC-01 Cập nhật đúng mục README/TESTING/BACKLOG liên quan; CHANGELOG chỉ thêm trạng thái cuối.
- [ ] WP1-06-DOC-02 Rà LICENSES/SBOM theo dependency thực tế; không sửa khi dependency giữ nguyên.

## Đóng phase

- [ ] Mọi tiêu chí ở 00_OVERVIEW mục 6 có bằng chứng; compile không được ghi thay cho test/model thật.
- [ ] Diff Core/API/host/monitor, deploy/CI, CV/retriever/embedding/corpus bằng không so với nhánh cha đã đồng bộ.
- [ ] App chỉ dùng core-api; frontend shell chỉ đổi phần hỏi đáp, không đổi cơ chế chọn app/auth/router.
- [ ] Mobile không đổi màn hỏi đáp, askKnowledge hoặc type KnowledgeAnswer; phần thay đổi chỉ phục vụ chi tiết chẩn đoán.
- [ ] Hai app khởi động theo README hiện có khi được phép kiểm; không đòi cấu hình/dịch vụ mới.
- [ ] API cũ, bản ghi cũ, request không opt-in, quyền xem và rule/liều/PHI giữ hành vi.
- [ ] Đánh giá riêng nội dung Qwen và retrieval; không dùng pilot cũ thay bằng chứng mới.
- [ ] Comment mới ngắn, tiếng Anh; Swagger đúng code, không ghi tên phase.
- [ ] Rà toàn bộ file phát sinh: wrapper/prompt cũ, helper/test trùng, log, ảnh, cache, trọng số tải thử và tài liệu thừa.
- [ ] Không xóa test có giá trị hoặc migration đã áp dụng; chỉ dọn file thử không dùng.
- [ ] Không có secret, token, dữ liệu thật, checksum literal hoặc artifact trong diff; report không tracked và .gitignore không đổi.
- [ ] Cập nhật nhánh cha mới nhất, xử lý conflict trong domain; lỗi ngoài domain chỉ ghi nhận.
- [ ] Khi đủ DoD, chuyển việc còn lại sang BACKLOG và xóa thư mục phase theo template; không xóa log CHANGELOG cũ.
- [ ] Không commit/push khi chưa có yêu cầu riêng của chủ dự án.
