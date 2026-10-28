# Checklist: Phase 2

Ký hiệu: `[ ]` chưa có bằng chứng đạt, `[~]` có tiền đề nhưng chưa đủ yêu cầu mới, `[x]` đã kiểm chứng, `[-]` không làm kèm lý do.
Các mục triển khai mới không được tick từ kết quả pilot hoặc checklist cũ.
Mỗi dòng là một việc nhỏ có đầu ra kiểm tra được; có thể tách tiếp khi triển khai.

## WP2-01: Baseline và contract

Kết quả: chatbot có đường search riêng; diagnosis/ticket giữ legacy.

- [x] WP2-01-DOC-01 Đọc template, kiến trúc, workflow/skill RAG và Spring; đối chiếu các caller hiện tại để sửa kế hoạch.
- [x] WP2-01-DOC-04 Đối chiếu Core gốc, interface/implementation/app registration và mẫu WorkflowDefinitions/FileStoreService; ghi vai trò gateway, engine và domain được sửa.
- [x] WP2-01-TEST-02 Kiểm hai lượt search/answer qua AiGateway, có Backend gộp giữa; không gọi Qwen khi thiếu evidence hoặc includeAnswer=false.
- [x] WP2-01-TEST-03 Kiểm adapter với namespace giả lập được đăng ký qua KnowledgeNamespace, không cần nhánh Core Farm/Lab.
- [x] WP2-01-DOC-02 Chốt request/envelope search, ý nghĩa score/rank và trạng thái suy giảm trước code.
- [x] WP2-01-DOC-03 Khóa protocol pilot và ngân sách 2 CPU/1536 MiB; holdout độc lập chuyển BACKLOG, không gọi kết quả pilot là kiểm định độc lập.
- [x] WP2-01-AI-01 Khai báo schema và Swagger endpoint chatbot; giữ nguyên schema retrieve/diagnose.
- [x] WP2-01-BE-01 [core] Cho ask gọi search envelope; bỏ keyword classifier app trong Core.
- [x] WP2-01-BE-02 [core] Giữ retrieve legacy, min-score và caller ticket; kiểm gộp không đảo ranking hybrid.
- [x] WP2-01-TEST-01 Lưu baseline legacy và inventory caller, xác nhận namespace do server gán.

## WP2-02: Corpus tổng quan và bộ nhãn

Kết quả: dữ liệu đủ nội dung, có quyền dùng/review, ingest bằng cách gọi hiện có.

- [x] WP2-02-DATA-01 [farm] Rà coverage của 7 artifact đã được owner duyệt, phân biệt mục lục và nội dung thực; lập ma trận chủ đề còn thiếu.
- [~] WP2-02-DATA-02 [farm] Hoàn thiện nguồn/nội dung cho các câu tổng quan ưu tiên, giữ vùng/vụ và locator.
- [x] WP2-02-DATA-03 [lab] Lập ma trận câu tổng quan dịch vụ/quy định/hướng dẫn với các nguồn hiện hữu.
- [x] WP2-02-DATA-04 [lab] Rà allowlist host chính thức và sửa quarantine/loader để không loại cả kho vì chính sách SICT-only.
- [x] WP2-02-DATA-13 [farm] Ghi nhận chủ dự án duyệt đủ 7 tài liệu/nguồn ngày 28/10/2026 trong corpus, registry và staging/queue.
- [~] WP2-02-DATA-05 [farm] Promotion đã ghi reviewer/ngày theo đầu vào; cờ điều khoản nguồn vẫn giữ để rà riêng, approval owner không cấp lại quyền sử dụng.
- [x] WP2-02-DATA-06 [lab] Review nguồn mới và hiệu lực nội dung, giữ nguồn hiện hữu hợp lệ.
- [x] WP2-02-AI-01 Chọn corpus theo namespace/type và review; giữ CORPORA key khác namespace khi cần.
- [x] WP2-02-AI-02 Mở rộng `--corpus farm` để nạp phần tổng quát đã duyệt, giữ lệnh deploy đang gọi và disease corpus.
- [x] WP2-02-AI-03 Dry-run, lỗi artifact chưa duyệt và rollback theo loại tài liệu không ghi chéo.
- [x] WP2-02-BE-01 [farm] Đồng bộ registry mới vào resource Knowledge hiện có và kiểm trùng source_id.
- [x] WP2-02-BE-02 [lab] Đồng bộ registry/nguồn được phục vụ; không đổi ticket workflow.
- [x] WP2-02-DATA-07 [farm] Thêm ít nhất 10 câu tổng quan và 5 câu không có từ khóa cụ thể, giữ QA/diagnosis cũ.
- [x] WP2-02-DATA-08 [lab] Thêm ít nhất 10 câu tổng quan và 5 câu không có từ khóa cụ thể, giữ QA cũ.
- [-] WP2-02-DATA-09 [farm] Chia calibration/holdout theo họ câu; ghi source/chunk labels theo snapshot. Hoãn calibration/holdout độc lập sang BACKLOG; hiện đo pilot nhãn draft.
- [-] WP2-02-DATA-10 [lab] Chia calibration/holdout theo họ câu; ghi source/chunk labels theo snapshot. Hoãn calibration/holdout độc lập sang BACKLOG; hiện đo pilot nhãn draft.
- [-] WP2-02-DATA-11 [farm] Rà qrels 0/1/2 trên chunk đủ điều kiện trước khi xem ranking, ghi reviewer và phạm vi. Chuyển BACKLOG, không đổi draft thành reviewed.
- [-] WP2-02-DATA-12 [lab] Rà qrels 0/1/2 và các ca no-evidence/ngoài miền, ghi reviewer và phạm vi. Chuyển BACKLOG, không đổi draft thành reviewed.
- [x] WP2-02-TEST-01 Kiểm source parity, corpus bệnh không đổi, ingest atomic và Lab active thực tế.

## WP2-03: Hybrid retrieval và rerank

Kết quả: BM25 + cosine/RRF + rerank thật, giới hạn tài nguyên, không đổi legacy.

- [x] WP2-03-AI-01 Kiểm khả năng chạy CrossEncoder model ứng viên với lock hiện tại và dữ liệu tiếng Việt.
- [x] WP2-03-AI-02 Đo cold/warm model, RAM/CPU, batch và độ trễ trước khi chốt model/ngân sách.
- [x] WP2-03-AI-03 Pin BM25 tối thiểu, tokenizer nhất quán có dấu/không dấu/đ/Đ và regression số/đơn vị.
- [x] WP2-03-AI-04 Dựng BM25 đọc từ cùng snapshot vector, namespace/filter trước lấy candidate.
- [x] WP2-03-AI-05 Cache/invalidation theo tập chunk/version, swap atomic và khóa khởi tạo đồng thời.
- [x] WP2-03-AI-06 Candidate cosine dùng embedding/filter hiện có; giữ legacy topic boost ngoài nhánh mới.
- [x] WP2-03-AI-07 RRF, dedup, stable tie-break và lưu ranking/component scores.
- [x] WP2-03-AI-08 Rerank ứng viên, ngân sách context và chọn top-4 có độ phủ cho câu tổng quan.
- [x] WP2-03-AI-09 Chuẩn bị/cached model qua đường quản trị hiện có; không tải trong request hoặc mỗi start.
- [x] WP2-03-AI-10 Timeout/concurrency/failure rõ strategy; không giả kết quả rerank khi model lỗi.
- [x] WP2-03-DOC-01 Cập nhật LICENSES, lock và SBOM theo quy ước; tách giấy phép model/package/nguồn dữ liệu.
- [x] WP2-03-TEST-01 Kiểm ranking legacy không đổi, cache sau ingest/rollback và không giữ DB connection lúc rerank.

## WP2-04: Gate và tổng hợp Qwen QA

Kết quả: câu tổng quan được truy xuất; evidence đa nguồn không làm mất guard an toàn.

- [x] WP2-04-AI-01 Gate trung tâm cho câu mơ hồ thử retrieval, không bắt buộc tên bệnh/sự cố.
- [-] WP2-04-AI-02 Calibration độc lập từng namespace/strategy chuyển BACKLOG; ngưỡng hiện là pilot, không phải xác suất đúng.
- [x] WP2-04-AI-03 Cho Lab QA dùng nhiều tài liệu phù hợp thay vì chỉ title đầu, giữ điều kiện/đối tượng.
- [x] WP2-04-AI-04 Đóng gói context QA nguyên ý theo relevance/coverage; không đụng ngân sách diagnosis.
- [x] WP2-04-AI-05 Prompt QA giữ schema, trả trực tiếp và chỉ dẫn nguồn có trong evidence.
- [x] WP2-04-TEST-01 Kiểm lại chốt thuốc/liều/PHI, tự xác nhận bệnh, hành động phê duyệt, numeric và nguồn.
- [x] WP2-04-FE-01 Knowledge hiển thị scope/thiếu dữ liệu/nguồn đúng; không diễn giải score thành % chính xác.
- [x] WP2-04-TEST-02 Chạy ca tổng quan/mơ hồ và ca Lab điều kiện–hình phạt/Wi-Fi đã biết; ghi lỗi còn lại.

## WP2-05: Runner, ir_measures và Langfuse

Kết quả: một lệnh mỗi mode, đo luồng mới trên DB riêng và giữ tự cleanup.

- [x] WP2-05-AI-01 Bootstrap/collector dùng corpus.namespace và manifest approved; không nạp draft hoặc giả namespace.
- [x] WP2-05-AI-02 Model cache/signature có reranker, tokenizer, config và snapshot; giữ lịch sử cũ.
- [x] WP2-05-AI-03 Quality xuất legacy/BM25/hybrid/hybrid+rerank cùng corpus và ranking trung gian/cuối.
- [x] WP2-05-AI-04 Tách baseline data cũ/mới, diagnosis @3, raw vs sau gate và nhóm no-evidence.
- [x] WP2-05-AI-05 Export qrels/run/collect/score, đối chiếu CLI ir_measures trong sai số `1e-8`.
- [x] WP2-05-AI-06 Langfuse dataset/experiment theo snapshot+strategy; replay điểm/độ trễ thực, không ghi publish thành inference.
- [x] WP2-05-AI-07 Benchmark endpoint chatbot thật, 18 lượt/strategy, mặc định hai strategy chạy tuần tự.
- [~] WP2-05-AI-09 K6/validator đọc manifest, hiểu envelope/0–k kết quả; đã kiểm từ chối bằng scope/API, workload tải riêng nhóm từ chối chuyển BACKLOG.
- [x] WP2-05-AI-08 Lấy CPU/RAM, cold/warm và latency từng tầng, báo đúng boundary AI HTTP retrieval.
- [x] WP2-05-INFRA-01 Giữ quality/performance/all/view/down, config riêng, giới hạn tài nguyên và lock.
- [x] WP2-05-INFRA-02 Cập nhật evaluation image/cache cần thiết, không copy CV hoặc build stack chính.
- [x] WP2-05-TEST-01 Kiểm cleanup lỗi/Enter/timeout/tín hiệu và hồi phục down, artifact còn nguyên.
- [~] WP2-05-TEST-02 Kiểm clone/state mới và state cũ, model đã cache chạy offline, Lab có corpus hợp lệ.
- [x] WP2-05-TEST-03 Đối chứng container/DB sản phẩm không đổi khi chạy hoặc down evaluation.

## WP2-06: Build, chạy thật và bàn giao

Kết quả: đủ bằng chứng DoD, owner test local trước commit/push.

- [x] WP2-06-TEST-01 Compile/test-compile Backend; chạy test gateway/Knowledge liên quan theo yêu cầu đã được duyệt.
- [x] WP2-06-TEST-02 AI pytest, contract diagnosis, write boundary và regression scope/hybrid đạt.
- [x] WP2-06-TEST-03 Typecheck web/mobile; mobile không đổi code/SDK.
- [x] WP2-06-INFRA-01 Build image bị đổi bằng cache, kiểm dung lượng; không full rebuild hoặc global prune.
- [x] WP2-06-TEST-04 Khởi động Farm theo luồng README bằng cache, kiểm ingest/chatbot, ảnh thật và duyệt; E2E 10/10. Không thay cho full clone sạch.
- [x] WP2-06-TEST-05 Khởi động Lab theo luồng README bằng cache, kiểm corpus/chatbot và ticket legacy; E2E 16/16.
- [~] WP2-06-TEST-06 Quality và performance mới nhất đạt, đã xem Langfuse/HTML; chưa chạy lại toàn bộ all với cấu hình cuối. Artifact riêng được giữ.
- [ ] WP2-06-TEST-07 Người dùng kiểm câu tổng quan/cụ thể/không trả lời được, format và nguồn trên web.
- [x] WP2-06-DOC-01 Cập nhật AI README, RAG README, TESTING và tài liệu corpus/evaluation đúng kết quả.
- [x] WP2-06-DOC-02 Bổ sung CHANGELOG, cập nhật BACKLOG liên quan; giữ log cũ và lỗi ngoài domain.

## Đóng phase

- [ ] Mọi test bắt buộc trong `03_TEST_PLAN.md` đạt, người chạy/ngày/bằng chứng có thật.
- [ ] Đạt mục 6 overview hoặc chủ dự án chấp nhận rõ trade-off còn lại; không đổi ngưỡng sau holdout.
- [x] Core không chứa từ khóa Farm/Lab hoặc import app; app chỉ phụ thuộc core-api.
- [x] Giữ KnowledgeGatewayService cùng DTO/helper câu trả lời dùng chung do đồng đội bổ sung trong core-api; AiGateway/AiServiceGateway/interface/host và các Core service khác giữ nguyên.
- [x] Một engine RAG/generation chung trong AI Service; giữ hai lượt gọi và gộp evidence Backend, không nhân bản thuật toán theo app.
- [x] Đã đối chiếu bảng domain trước/sau từng WP; thay đổi cần vượt phạm vi phải được chủ dự án đồng ý riêng.
- [x] Giữ CV/threshold, Graph/retriever legacy, treatment facts, ticket workflow, deploy và CI. Giữ bước lọc evidence chẩn đoán đã có trên nhánh; kiểm hồi quy riêng, không ghi là output nguyên văn.
- [x] Cách khởi động chính giữ nguyên; đánh giá chỉ chạy khi gọi evaluate.sh.
- [x] Swagger, score/status, dependency/lock và LICENSES/SBOM nhất quán.
- [x] Dọn file test tạm, corpus/raw dư, code chết và cấu hình không dùng; giữ fixture thật cần để tái lập.
- [x] Không stage report/, state, credential, token, mã định danh/checksum không cần công khai hoặc sửa .gitignore.
- [ ] Chủ dự án đã test local, đọc diff và cho phép commit/push riêng.
- [x] Chuyển các giới hạn còn lại sang BACKLOG; giữ thư mục kế hoạch theo yêu cầu chủ dự án để review, không xóa tự động.

## Bằng chứng ngày 28/10/2026

- AI: 168 test và 50 subtest; diagnose contract, access boundary, evaluator/benchmark self-check và runner self-check đạt. Backend: 24 test liên quan, package đủ sáu module; CI có typecheck và E2E Farm/Lab đạt.
- Data: validator 7 Farm/12 Lab và hai test approval/parity đạt. Ingest Lab offline 12 tài liệu/78 chunk; DB riêng kiểm corpus rỗng xóa đúng generic, giữ bệnh/Lab, lỗi giữa transaction rollback nguyên dữ liệu; role đọc bị từ chối ghi.
- Quality: bốn strategy × 72 query, cùng 25 tài liệu/244 chunk; scope 90/90, gồm 15 ca thiếu evidence; CLI ir_measures khớp 600 giá trị ≤1e-8. Nhãn mới vẫn draft.
- Performance: 12 lượt/888 request, 10 giây/mức, một lần lặp, không lỗi HTTP/hợp đồng/OOM. CPU/RAM, p95 và phạm vi đo ở TESTING; mặc định runner vẫn 30 giây/mức, ba lần lặp.
- Langfuse: publish quality, scope và performance; đã xem experiment/HTML bằng trình duyệt. Quality/performance tự down, view mở lại lịch sử; so ID/start-time container và số dòng vector sản phẩm trước/sau cleanup không đổi.
- Sản phẩm: Farm 10/10 và Lab 16/16 E2E local; Qwen trả lời Farm tổng quan/triệu chứng và Lab tổng quan theo đúng đối tượng. Giữ source_id thật; thuốc/liều/PHI, ngoài miền và thẩm quyền vẫn bị chặn. Ca JSON chưa hoàn chỉnh bị ẩn draft, theo dõi BACKLOG.
- Ảnh thật: tạo báo cáo riêng, chạy AI, kiểm lịch sử và quyền trước/sau duyệt; giải thích và treatment rule giữ nguyên sau duyệt. Không thay CV/ngưỡng hoặc workflow.
- Log/artifact tại `$XDG_DATA_HOME/dx-os-chat-rag` (mặc định `~/.local/share/dx-os-chat-rag`), ngoài repository. Không dùng kết quả pilot để xác nhận nhãn độc lập.

## Trạng thái bàn giao

Phần triển khai và kiểm thử kỹ thuật đã sẵn sàng review; chưa tự đóng nghiệm thu người dùng hoặc đánh giá độc lập.
Giữ nguyên kế hoạch theo yêu cầu chủ dự án. Các mục chờ review và giới hạn đo còn được ghi rõ ở checklist, [06_HANDOFF.md](06_HANDOFF.md) và BACKLOG.
