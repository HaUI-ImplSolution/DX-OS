# Checklist: Phase 2

Ký hiệu: `[ ]` chưa có bằng chứng đạt, `[~]` có tiền đề nhưng chưa đủ yêu cầu mới, `[x]` đã kiểm chứng, `[-]` không làm kèm lý do.
Các mục triển khai mới không được tick từ kết quả pilot hoặc checklist cũ.
Mỗi dòng là một việc nhỏ có đầu ra kiểm tra được; có thể tách tiếp khi triển khai.

## WP2-01: Baseline và contract

Kết quả: chatbot có đường search riêng; diagnosis/ticket giữ legacy.

- [x] WP2-01-DOC-01 Đọc template, kiến trúc, workflow/skill RAG và Spring; đối chiếu các caller hiện tại để sửa kế hoạch.
- [x] WP2-01-DOC-04 Đối chiếu Core gốc, interface/implementation/app registration và mẫu WorkflowDefinitions/FileStoreService; ghi vai trò gateway, engine và domain được sửa.
- [ ] WP2-01-TEST-02 Kiểm hai lượt search/answer qua AiGateway, có Backend gộp giữa; không gọi Qwen khi thiếu evidence hoặc includeAnswer=false.
- [x] WP2-01-TEST-03 Kiểm adapter với namespace giả lập được đăng ký qua KnowledgeNamespace, không cần nhánh Core Farm/Lab.
- [x] WP2-01-DOC-02 Chốt request/envelope search, ý nghĩa score/rank và trạng thái suy giảm trước code.
- [ ] WP2-01-DOC-03 Khóa mục tiêu holdout, ngân sách máy và protocol baseline.
- [x] WP2-01-AI-01 Khai báo schema và Swagger endpoint chatbot; giữ nguyên schema retrieve/diagnose.
- [x] WP2-01-BE-01 [core] Cho ask gọi search envelope; bỏ keyword classifier app trong Core.
- [x] WP2-01-BE-02 [core] Giữ retrieve legacy, min-score và caller ticket; kiểm gộp không đảo ranking hybrid.
- [ ] WP2-01-TEST-01 Lưu baseline legacy và inventory caller, xác nhận namespace do server gán.

## WP2-02: Corpus tổng quan và bộ nhãn

Kết quả: dữ liệu đủ nội dung, có quyền dùng/review, ingest bằng cách gọi hiện có.

- [x] WP2-02-DATA-01 [farm] Rà coverage của 7 artifact đã được owner duyệt, phân biệt mục lục và nội dung thực; lập ma trận chủ đề còn thiếu.
- [ ] WP2-02-DATA-02 [farm] Hoàn thiện nguồn/nội dung cho các câu tổng quan ưu tiên, giữ vùng/vụ và locator.
- [x] WP2-02-DATA-03 [lab] Lập ma trận câu tổng quan dịch vụ/quy định/hướng dẫn với các nguồn hiện hữu.
- [x] WP2-02-DATA-04 [lab] Rà allowlist host chính thức và sửa quarantine/loader để không loại cả kho vì chính sách SICT-only.
- [x] WP2-02-DATA-13 [farm] Ghi nhận chủ dự án duyệt đủ 7 tài liệu/nguồn ngày 28/10/2026 trong corpus, registry và staging/queue.
- [ ] WP2-02-DATA-05 [farm] Kiểm điều khoản dùng riêng và sửa promotion ghi đúng reviewer/ngày cho lần sau; không hardcode ngày duyệt.
- [ ] WP2-02-DATA-06 [lab] Review nguồn mới và hiệu lực nội dung, giữ nguồn hiện hữu hợp lệ.
- [x] WP2-02-AI-01 Chọn corpus theo namespace/type và review; giữ CORPORA key khác namespace khi cần.
- [x] WP2-02-AI-02 Mở rộng `--corpus farm` để nạp phần tổng quát đã duyệt, giữ lệnh deploy đang gọi và disease corpus.
- [x] WP2-02-AI-03 Dry-run, lỗi artifact chưa duyệt và rollback theo loại tài liệu không ghi chéo.
- [x] WP2-02-BE-01 [farm] Đồng bộ registry mới vào resource Knowledge hiện có và kiểm trùng source_id.
- [ ] WP2-02-BE-02 [lab] Đồng bộ registry/nguồn được phục vụ; không đổi ticket workflow.
- [x] WP2-02-DATA-07 [farm] Thêm ít nhất 10 câu tổng quan và 5 câu không có từ khóa cụ thể, giữ QA/diagnosis cũ.
- [x] WP2-02-DATA-08 [lab] Thêm ít nhất 10 câu tổng quan và 5 câu không có từ khóa cụ thể, giữ QA cũ.
- [ ] WP2-02-DATA-09 [farm] Chia calibration/holdout theo họ câu; ghi source/chunk labels theo snapshot.
- [ ] WP2-02-DATA-10 [lab] Chia calibration/holdout theo họ câu; ghi source/chunk labels theo snapshot.
- [ ] WP2-02-DATA-11 [farm] Rà qrels 0/1/2 trên chunk đủ điều kiện trước khi xem ranking, ghi reviewer và phạm vi.
- [ ] WP2-02-DATA-12 [lab] Rà qrels 0/1/2 và các ca no-evidence/ngoài miền, ghi reviewer và phạm vi.
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
- [~] WP2-04-AI-02 Calibration riêng từng namespace/strategy, không dùng confidence hardcode như xác suất.
- [x] WP2-04-AI-03 Cho Lab QA dùng nhiều tài liệu phù hợp thay vì chỉ title đầu, giữ điều kiện/đối tượng.
- [x] WP2-04-AI-04 Đóng gói context QA nguyên ý theo relevance/coverage; không đụng ngân sách diagnosis.
- [x] WP2-04-AI-05 Prompt QA giữ schema, trả trực tiếp và chỉ dẫn nguồn có trong evidence.
- [x] WP2-04-TEST-01 Kiểm lại chốt thuốc/liều/PHI, tự xác nhận bệnh, hành động phê duyệt, numeric và nguồn.
- [x] WP2-04-FE-01 Knowledge hiển thị scope/thiếu dữ liệu/nguồn đúng; không diễn giải score thành % chính xác.
- [ ] WP2-04-TEST-02 Chạy ca tổng quan/mơ hồ và ca Lab điều kiện–hình phạt/Wi-Fi đã biết; ghi lỗi còn lại.

## WP2-05: Runner, ir_measures và Langfuse

Kết quả: một lệnh mỗi mode, đo luồng mới trên DB riêng và giữ tự cleanup.

- [x] WP2-05-AI-01 Bootstrap/collector dùng corpus.namespace và manifest approved; không nạp draft hoặc giả namespace.
- [ ] WP2-05-AI-02 Model cache/signature có reranker, tokenizer, config và snapshot; giữ lịch sử cũ.
- [x] WP2-05-AI-03 Quality xuất legacy/BM25/hybrid/hybrid+rerank cùng corpus và ranking trung gian/cuối.
- [x] WP2-05-AI-04 Tách baseline data cũ/mới, diagnosis @3, raw vs sau gate và nhóm no-evidence.
- [x] WP2-05-AI-05 Export qrels/run/collect/score, đối chiếu CLI ir_measures trong sai số `1e-8`.
- [x] WP2-05-AI-06 Langfuse dataset/experiment theo snapshot+strategy; replay điểm/độ trễ thực, không ghi publish thành inference.
- [x] WP2-05-AI-07 Benchmark endpoint chatbot thật, 18 lượt/strategy, mặc định hai strategy chạy tuần tự.
- [ ] WP2-05-AI-09 K6/validator đọc bộ đề từ manifest, hiểu envelope/0–k kết quả; tách workload có evidence và từ chối.
- [x] WP2-05-AI-08 Lấy CPU/RAM, cold/warm và latency từng tầng, báo đúng boundary AI HTTP retrieval.
- [x] WP2-05-INFRA-01 Giữ quality/performance/all/view/down, config riêng, giới hạn tài nguyên và lock.
- [x] WP2-05-INFRA-02 Cập nhật evaluation image/cache cần thiết, không copy CV hoặc build stack chính.
- [x] WP2-05-TEST-01 Kiểm cleanup lỗi/Enter/timeout/tín hiệu và hồi phục down, artifact còn nguyên.
- [~] WP2-05-TEST-02 Kiểm clone/state mới và state cũ, model đã cache chạy offline, Lab có corpus hợp lệ.
- [ ] WP2-05-TEST-03 Đối chứng container/DB sản phẩm không đổi khi chạy hoặc down evaluation.

## WP2-06: Build, chạy thật và bàn giao

Kết quả: đủ bằng chứng DoD, owner test local trước commit/push.

- [x] WP2-06-TEST-01 Compile/test-compile Backend; chạy test gateway/Knowledge liên quan theo yêu cầu đã được duyệt.
- [x] WP2-06-TEST-02 AI pytest, contract diagnosis, write boundary và regression scope/hybrid đạt.
- [x] WP2-06-TEST-03 Typecheck web/mobile; mobile không đổi code/SDK.
- [x] WP2-06-INFRA-01 Build image bị đổi bằng cache, kiểm dung lượng; không full rebuild hoặc global prune.
- [ ] WP2-06-TEST-04 Khởi động Farm bằng lệnh README, kiểm ingest/cache/chatbot và hồi quy diagnosis.
- [ ] WP2-06-TEST-05 Khởi động Lab bằng lệnh README, kiểm corpus/chatbot và ticket legacy.
- [ ] WP2-06-TEST-06 Chạy quality/performance/all; lưu report và kiểm Langfuse bằng trình duyệt.
- [ ] WP2-06-TEST-07 Người dùng kiểm câu tổng quan/cụ thể/không trả lời được, format và nguồn trên web.
- [ ] WP2-06-DOC-01 Cập nhật AI README, RAG README, TESTING và tài liệu corpus/evaluation đúng kết quả.
- [ ] WP2-06-DOC-02 Bổ sung CHANGELOG, cập nhật BACKLOG liên quan; giữ log cũ và lỗi ngoài domain.

## Đóng phase

- [ ] Mọi test bắt buộc trong `03_TEST_PLAN.md` đạt, người chạy/ngày/bằng chứng có thật.
- [ ] Đạt mục 6 overview hoặc chủ dự án chấp nhận rõ trade-off còn lại; không đổi ngưỡng sau holdout.
- [x] Core không chứa từ khóa Farm/Lab hoặc import app; app chỉ phụ thuộc core-api.
- [x] Giữ KnowledgeGatewayService cùng DTO/helper câu trả lời dùng chung do đồng đội bổ sung trong core-api; AiGateway/AiServiceGateway/interface/host và các Core service khác giữ nguyên.
- [x] Một engine RAG/generation chung trong AI Service; giữ hai lượt gọi và gộp evidence Backend, không nhân bản thuật toán theo app.
- [ ] Đã đối chiếu bảng domain trước/sau từng WP; thay đổi cần vượt phạm vi phải được chủ dự án đồng ý riêng.
- [x] Giữ CV/threshold, Graph/retriever legacy, treatment facts, ticket workflow, deploy và CI. Giữ bước lọc evidence chẩn đoán đã có trên nhánh; kiểm hồi quy riêng, không ghi là output nguyên văn.
- [ ] Cách khởi động chính giữ nguyên; đánh giá chỉ chạy khi gọi evaluate.sh.
- [x] Swagger, score/status, dependency/lock và LICENSES/SBOM nhất quán.
- [ ] Dọn file test tạm, corpus/raw dư, code chết và cấu hình không dùng; giữ fixture thật cần để tái lập.
- [ ] Không stage report/, state, credential, token, mã định danh/checksum không cần công khai hoặc sửa .gitignore.
- [ ] Chủ dự án đã test local, đọc diff và cho phép commit/push riêng.
- [ ] Sau khi được chấp nhận, chuyển tồn đọng sang BACKLOG và xử lý thư mục kế hoạch theo template.

## Bằng chứng ngày 28/10/2026

- AI: 162 test và 50 subtest đạt; diagnose contract và RAG access boundary đạt. Backend: 24 test gateway/Knowledge/codec và package 6 module đạt. Typecheck web/mobile và build frontend đạt; không đổi mobile.
- Corpus: validator 7 tài liệu Farm/12 Lab, source parity đạt; ingest Lab 12 tài liệu/78 chunk chạy lại với `HF_HUB_OFFLINE=1`. Trên DB đánh giá riêng, lỗi sau xóa trong transaction rollback nguyên dữ liệu; role đọc bị từ chối ghi.
- Retrieval: bốn strategy cùng 72 query; 6 query diagnosis có ranking/evidence không đổi. Đợt tải đủ 36 lượt, 8.974 request, không lỗi HTTP/hợp đồng; CPU/RAM và HTML lưu ngoài repo. Bước publish `all` gặp chênh lệch cộng số thực, đã sửa thứ tự tính; lượt quality mới publish đủ bốn strategy và scope thành công, exit 0 và tự down. Đã đăng nhập bằng trình duyệt, thấy experiment hybrid_rerank và bảng HTML so sánh; đã publish lại cả 36 lượt tải. `view` thoát bằng Enter tự down, giữ lịch sử. CLI ir_measures khớp 600 giá trị trong sai số 1e-8. Gate pilot đạt 90/90 ca, gồm 15 ca thiếu bằng chứng. Smoke web Lab có câu trả lời thật, không lỗi JavaScript; đã phát hiện chức năng quản trị bị nêu như dịch vụ sinh viên, bổ sung policy loại nguồn quản trị giảng viên khi câu hỏi dành cho sinh viên, lượt quality sau cùng đã publish đủ bốn strategy/scope và tự down, exit 0. Generation sau thay đổi này vẫn chưa kiểm lại.
- BGE v2 m3 vượt ngân sách thử (20 cặp khoảng 24 giây); MiniLM đa ngôn ngữ khoảng 0,5 giây cho 20 cặp trong spike. Không coi tốc độ spike là độ trễ end-to-end.
- Chủ dự án ngày 28/10 chấp nhận không bắt buộc điểm mới vượt baseline. Vẫn báo điểm từng nhóm, dữ liệu nhãn nháp và giảm điểm; không tự đổi nhãn theo ranking.
- Artifact/log nằm ngoài checkout tại `$XDG_DATA_HOME/dx-os-chat-rag` (máy hiện tại dùng `~/.local/share/dx-os-chat-rag`). Các mục nhãn độc lập, bổ sung nội dung mục lục và người dùng kiểm local vẫn chưa đủ bằng chứng.

## Điểm dừng bàn giao

Xem [06_HANDOFF.md](06_HANDOFF.md) để tiếp tục. Người dùng yêu cầu chốt phần đang chạy trước khi tắt máy; chưa đóng phase; sau đó đã được chủ dự án cho phép commit/push bản bàn giao. Đã có kết quả retrieval/scope sau policy đối tượng Lab, nhưng generation/web sau sửa cuối và smoke Farm/diagnosis/ticket còn chờ. Không tick các ca đó bằng kết quả của phiên bản trước sửa.

## Xác minh trước push bản bàn giao

Fetch origin: nhánh local khớp remote trước commit và đã chứa `origin/Khuong/main` hiện tại. Lượt quality cuối publish đủ bốn strategy/scope, exit 0, tự cleanup. Sau cleanup, runner self-check đạt; không còn container evaluation hoặc container thử nghiệm riêng. Gitleaks v8.24.3 quét các file thay đổi bằng cấu hình repository: không phát hiện secret. Diff không thêm giá trị hash cố định; report/ và .gitignore không đổi.

## Tiếp tục trên phiên bản đồng đội đã bổ sung

- [x] Khôi phục đúng quyết định chủ dự án duyệt 7 tài liệu Farm; đồng bộ corpus, staging, registry, queue và danh mục nguồn Backend. Validator đạt 7 Farm/12 Lab; hai test source parity/approval đạt.
- [x] Xác minh startup AI khi thiếu cache reranker: container không có mạng/cache dùng hai model CV thật, health đạt; unit kiểm trạng thái suy giảm. Không thay deploy/CI.
- [x] Kiểm corpus rỗng vẫn được chọn để ingest, thứ hạng liên tục sau lọc và evaluator chỉ chấp nhận đúng phép lọc đã khai báo; chưa sửa nhãn để tăng điểm.
- [x] Chạy lại bốn strategy × 72 query trên 25 tài liệu/244 chunk; scope 90/90, CLI khớp 600 giá trị ≤1e-8; publish đủ 12 experiment và scope lên Langfuse, tự down, giữ lịch sử cũ.
- [ ] Smoke Qwen Farm/Lab, diagnosis và ticket bằng code hiện tại; chỉ tick khi có bằng chứng mới.

Xác minh phần sửa tiếp: 167 test AI và 50 subtest đạt; contract diagnosis, access boundary và self-check evaluator đạt. Backend giữ code dùng chung của đồng đội: 24 test liên quan và package sáu module đạt với JDK 21. Tài liệu nguồn có hai test parity/approval đạt. Các log nằm ngoài repository.

CI của bản sửa startup/evidence đã xanh toàn bộ, gồm E2E Farm/Lab từ checkout sạch trên GitHub Actions. E2E local chạy bằng image/cache hiện có đạt Farm 10/10 và Lab 16/16; không chạy thêm full stack hoặc sửa deploy/CI. QA đã kiểm phần tổng quan Lab theo nhóm dịch vụ/đối tượng và wire JSON không tự mâu thuẫn. Thời gian chờ QA 30 giây trong read-timeout Docker 120 giây có sẵn; không đổi Core/deploy. Diagnosis giữ profile/ngân sách riêng.

- [x] QA: 168 test và 50 subtest; không đổi schema API. Farm tổng quan và Lab tổng quan có trả lời thực, giữ source_id đầy đủ. Câu thiếu căn cứ trả rỗng; thuốc/liều/PHI, xác nhận bệnh cá nhân và quyền phê duyệt vẫn được kiểm riêng.
- [x] Ảnh thật: tạo report riêng, chạy AI, kiểm ẩn trước duyệt, lưu lịch sử và giữ nguyên explanation/treatment rule sau duyệt. Luồng đạt, AI khoảng 11,2 giây trên máy thử.
- [x] Ghi ca JSON chưa hoàn chỉnh vào BACKLOG; giữ draft bị chặn. Người dùng chấp nhận lỗi diễn đạt/format không nghiêm trọng là tồn đọng, không yêu cầu mọi chỉ số vượt baseline.

- [x] DB pgvector tách biệt: nạp corpus Farm tổng quát rỗng xóa đúng loại tài liệu, giữ nguyên bệnh/Lab; gây lỗi embedding sau xóa thì rollback nguyên dữ liệu. Container/volume thử nghiệm đã dọn.
