# Checklist Phase 2

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ.

## WP2-01 Scope gate và phản hồi không biết

Kết quả đầu ra: phân loại được ngoài phạm vi, đúng phạm vi và thiếu evidence; không truy xuất khi câu đã ngoài phạm vi.

**Spec và tài liệu**

- [x] WP2-01-DOC-01 Chốt response contract và ba trạng thái scope.
- [x] WP2-01-DOC-02 Chốt message tiếng Việt và reason code.

**AI**

- [~] WP2-01-AI-01 Viết scope classifier rule-based MVP và decision object; embedding calibration còn lại.
- [x] WP2-01-AI-02 Thêm gate trước retriever/LLM.
- [x] WP2-01-AI-03 Tách `IN_SCOPE_NO_EVIDENCE` khỏi `OUT_OF_SCOPE`.
- [x] WP2-01-AI-04 Cho hỏi đáp Farm dùng toàn bộ document types hợp lệ.
- [x] WP2-01-AI-05 Giữ diagnosis truyền `disease_code` và không bị gate hỏi đáp làm thay đổi.
- [x] WP2-01-AI-06 Kiểm tra namespace `farm` và `lab` không bao giờ truy xuất chéo.

**Backend/API**

- [x] WP2-01-BE-01 [farm] Cập nhật schema response additive và OpenAPI.
- [x] WP2-01-BE-02 [lab] Cập nhật schema response additive và OpenAPI nếu Lab dùng endpoint riêng.

**Frontend**

- [x] WP2-01-FE-01 Hiển thị trạng thái ngoài phạm vi và thiếu dữ liệu.
- [x] WP2-01-FE-02 Hiển thị citation theo source và locator.

**Test**

- [x] WP2-01-TEST-01 Viết unit test gate và test không gọi retriever/LLM khi out-of-scope.

## WP2-02 Bộ đánh giá

Kết quả đầu ra: bộ test có nhãn và ngưỡng được chốt trên calibration, đánh giá trên holdout.

- [x] WP2-02-DATA-01 Bổ sung câu hỏi lúa ngoài bệnh, ngoài nông nghiệp, mơ hồ và adversarial.
- [ ] WP2-02-DATA-02 Có người duyệt nhãn độc lập và ghi version.
- [x] WP2-02-DATA-03 Bổ sung nhóm câu hỏi DX-Lab đúng chủ đề, ngoài chủ đề và thiếu tài liệu.
- [x] WP2-02-AI-01 Tính precision/recall/F1 của scope gate; bộ draft hiện đạt precision/recall/F1 = 1.0.
- [~] WP2-02-AI-02 Có report Recall@4/nDCG@4 lexical proxy; metric pgvector và unsupported-answer rate còn cần vector DB.
- [ ] WP2-02-TEST-01 Chạy baseline trước và sau corpus mới.

## WP2-03 Crawl và chuẩn hóa dữ liệu

Kết quả đầu ra: raw snapshot, manifest, processed corpus và validator đều có provenance.

- [x] WP2-03-DATA-01 Chốt allowlist nguồn và chính sách license/terms.
- [x] WP2-03-DATA-02 Enforce Farm chỉ nhận nguồn uy tín có nội dung tiếng Việt.
- [x] WP2-03-DATA-03 Enforce Lab chỉ nhận host `sict.haui.edu.vn` và URL con cùng domain.
- [x] WP2-03-DATA-04 Viết crawler dry-run với rate limit, retry, timeout và size limit.
- [x] WP2-03-DATA-05 Lưu URL, checksum, accessed_at, locator và source metadata.
- [x] WP2-03-DATA-06 Chuẩn hóa các topic lúa ngoài bệnh.
- [x] WP2-03-DATA-07 Viết validator source registry/corpus/chunk/review_status.
- [~] WP2-03-DATA-08 Audit và quarantine source Farm/Lab hiện hữu không đạt policy; Lab còn chờ xác nhận nguồn SICT.
- [x] WP2-03-DOC-01 Viết quy trình chuyên gia duyệt và xử lý nội dung thuốc.

## WP2-04 Ingest và rollback theo namespace

Kết quả đầu ra: ingest atomic, corpus version, smoke retrieval và rollback.

- [x] WP2-04-AI-01 Tái sử dụng `CorpusConfig`/`ingest_corpus`; chỉ thêm cấu hình tối thiểu cho corpus mới.
- [x] WP2-04-AI-02 Mở rộng contract/validator để tài liệu không phải bệnh không bắt buộc `disease_code`.
- [~] WP2-04-AI-03 Ingest version mới bằng credential write riêng; chưa chạy vì chưa có vector DB.
- [x] WP2-04-AI-04 Chạy dry-run và validate artifact trước khi ghi.
- [x] WP2-04-AI-05 Ghi corpus version vào citation/telemetry.
- [~] WP2-04-DATA-01 Chỉ ingest corpus đã duyệt; dữ liệu hiện tại còn `IN_REVIEW`, chưa ingest thật.
- [x] WP2-04-INFRA-01 Thêm job/command crawl và ingest ngoài runtime FastAPI.
- [ ] WP2-04-TEST-01 Kiểm tra rollback về corpus cũ (cần vector DB).
- [ ] WP2-04-TEST-02 Kiểm tra rollback độc lập cho namespace `farm` và `lab` (cần vector DB).

## WP2-05 UI, vận hành và bàn giao

- [x] WP2-05-DOC-01 Cập nhật README, runbook và sơ đồ luồng.
- [~] WP2-05-FE-01 Mobile không dùng status mới; build web cần chạy trong môi trường Node đầy đủ.
- [x] WP2-05-INFRA-01 Cập nhật env example, healthcheck và log không lộ nội dung nhạy cảm.
- [ ] WP2-05-TEST-01 Lưu test report và bằng chứng demo (cần môi trường chạy thật).

## Đóng phase

- [ ] Tất cả test bắt buộc ở `03_TEST_PLAN.md` đạt.
- [x] Scope gate không tạo citation cho câu ngoài phạm vi.
- [x] Runtime FastAPI không import crawler/ingestion và không có credential write.
- [x] Corpus artifact có manifest, version, source registry và review status; ingest thật còn chờ DB.
- [x] Câu hỏi bệnh lúa và luồng chẩn đoán hiện tại không đổi contract.
- [x] Câu hỏi DX-Lab không nhận citation hoặc evidence từ namespace `farm`.
- [x] Câu hỏi Farm không nhận citation hoặc evidence từ namespace `lab`.
- [ ] Có rollback drill thành công.
- [~] Cập nhật README, runbook và checklist; changelog/backlog và báo cáo đánh giá chính thức còn chờ kết quả evaluation.
