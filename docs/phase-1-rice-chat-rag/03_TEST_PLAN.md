# Kế hoạch kiểm thử: Phase 2

## 1. Chiến lược

Đã đóng phạm vi triển khai kỹ thuật ngày 28/10/2026; bảng dưới giữ đúng kết quả đã chạy và giới hạn còn lại. Ô chưa đủ được giữ chờ kiểm, không lấy kết quả phase cũ thay thế.

- Unit/contract: scope, BM25/tokenizer, RRF, rerank, context QA và adapter envelope.
- Integration: pgvector thực, source parity, ingest/rollback, quyền chỉ đọc và cache invalidation.
- Product smoke: Farm/Lab theo cách khởi động README, đúng vai, Qwen/schema/source và lỗi dịch vụ.
- Evaluation: ir_measures, đối chiếu CLI, Langfuse replay, k6 và CPU/RAM Docker.
- Môi trường đánh giá có Compose project, DB, role, cache, network và state riêng ngoài repo; không gọi sản phẩm để đo.
- Build dùng cache và tài nguyên giới hạn; không dựng thêm full stack hoặc xóa Docker toàn máy.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T2-01 | WP2-01 | Contract | Gọi endpoint retrieve cũ và chatbot search mới bằng API key hợp lệ/sai | Cũ giữ list response, mới có envelope; unauthorized bị chặn | Codex, 28/10/2026 | Đạt unit contract/auth; E2E Farm/Lab |
| T2-02 | WP2-01 | Boundary | So caller ask/retrieve/diagnosis/ticket và thứ tự hai lượt qua AiGateway | Chỉ ask dùng search, Backend gộp rồi mới gọi answer; không gọi Qwen khi thiếu evidence hoặc includeAnswer=false; legacy giữ nguyên | Codex, 28/10/2026 | Đạt 24 test Backend liên quan; diagnosis ảnh thật và ticket E2E |
| T2-03 | WP2-01 | Namespace | Giả namespace/filter/strategy từ client Farm/Lab | Backend chốt namespace; không truy xuất chéo hoặc client tự đổi ngưỡng | Codex, 28/10/2026 | Đạt namespace unit, access boundary và API Lab |
| T2-04 | WP2-02 | Data | Kiểm metadata phê duyệt owner của 7 artifact Farm, bài mục lục và nguồn lỗi extract | Approval đúng reviewer/ngày; không tự công nhận tiêu đề là kiến thức đủ trả lời; draft khác không ingest | Codex, 28/10/2026 | Đạt validator 7 Farm/12 Lab và hai test approval/parity; mục lục không được coi là nội dung chi tiết |
| T2-05 | WP2-02 | Source | Rà registry Lab, host chính thức và URL/redirect ngoài allowlist | Nguồn hợp lệ còn phục vụ, nguồn không duyệt bị chặn; Lab active không bằng 0 khi bàn giao | Codex, 28/10/2026 | Đạt allowlist/parity; 12 Lab/78 chunk active |
| T2-06 | WP2-02 | Ingest | Chạy CLI --corpus farm/lab như deploy, với draft và approved | Farm nạp đúng disease + generic approved, Lab hợp lệ; không cần đổi Compose | Codex, 28/10/2026 | Đạt ingest Farm/Lab, Lab cache offline; không sửa Compose |
| T2-07 | WP2-02 | Atomicity | Ingest artifact lỗi, retry và rollback generic | Không mất disease/Lab, không trùng chunk; lỗi trước ghi hoặc rollback đúng transaction | Codex, 28/10/2026 | Đạt DB riêng: corpus rỗng chỉ xóa generic, lỗi giữa transaction rollback nguyên dữ liệu |
| T2-08 | WP2-02 | Citation | So corpus, registry, Backend resource và Qwen source_ids | Đủ URL/tổ chức/locator/version; không chỉ hiện ID thiếu thông tin | Codex, 28/10/2026 | Đạt source parity, unit source_ids và API/Qwen thật |
| T2-09 | WP2-03 | Token/BM25 | Query có dấu/không dấu/đ/Đ, mã sản phẩm, số và đơn vị | Query/index cùng chuẩn hóa; BM25 thật có test đối chiếu kết quả nhỏ đã tính trước | Codex, 28/10/2026 | Đạt unit tokenizer/BM25 |
| T2-10 | WP2-03 | Fusion | BM25/cosine trả cùng và khác chunk, score khác đơn vị | RRF đúng công thức, dedup/stable rank; không cộng thẳng hai score | Codex, 28/10/2026 | Đạt RRF/dedup unit, tie-break ổn định |
| T2-11 | WP2-03 | Filter | Candidate có topic/type khác và namespace kia | Lọc trước BM25/cosine top-N; không rerank evidence ngoài quyền | Codex, 28/10/2026 | Đạt unit namespace/filter trước candidate và access boundary |
| T2-12 | WP2-03 | Cache | Đồng thời tạo index, ingest/rollback giữa request | Mỗi request dùng một snapshot nhất quán; cache mới đổi đúng, không trộn index/vector | Codex, 28/10/2026 | Đạt unit cache/concurrency/rollback; kiểm transaction trên DB riêng |
| T2-13 | WP2-03 | Model | Cold load, cache offline, rerank batch/query tiếng Việt | Model đúng manifest, không tải lại; có số CPU/RAM/latency và quyết định chọn model | Codex, 28/10/2026 | Đạt spike MiniLM, ingest Lab offline |
| T2-14 | WP2-03 | Failure | Model thiếu, lỗi/timeout hoặc batch quá tải | Strategy/degraded rõ; không giả đã rerank, không sinh Qwen từ evidence chưa đủ ngưỡng | Codex, 28/10/2026 | Đạt unit lỗi/timeout; startup CV thật khi cache reranker rỗng, search báo unavailable |
| T2-15 | WP2-04 | Farm tổng quan | Hỏi chăm sóc lúa, quản lý nước/dinh dưỡng/IPM chung và câu bệnh cụ thể | Tổng hợp theo nguồn đúng chủ đề; không ép disease filter khi không chọn bệnh | Codex, 28/10/2026 | Đạt smoke Qwen tổng quan phát thải thấp và triệu chứng đạo ôn; một cách hỏi JSON lỗi được ẩn, ghi BACKLOG |
| T2-16 | WP2-04 | Lab tổng quan | Hỏi dịch vụ/quy định chung, cần nhiều tài liệu; đối chiếu câu kỹ thuật cụ thể | Dùng nhiều tài liệu khi cần, không giới hạn vào tài liệu đầu | Codex, 28/10/2026 | Đạt web Lab và replay evidence Backend bằng Qwen; danh sách dịch vụ đúng đối tượng, nhiều nguồn |
| T2-17 | WP2-04 | Gate | Hỏi đúng miền không có từ khóa, sai dấu/typo, câu mơ hồ | Được thử tìm hoặc yêu cầu làm rõ; không tự OUT_OF_SCOPE vì thiếu từ khóa | Codex, 28/10/2026 | Đạt pilot gate đúng miền 60/60 |
| T2-18 | WP2-04 | Scope | Chủ đề rõ ngoài miền, trộn miền và adversarial | Từ chối đúng ý định, không citation sai miền; nhắc thuật ngữ ngoài miền không mặc nhiên bị chặn | Codex, 28/10/2026 | Đạt pilot ngoài miền 30/30 và unit so sánh |
| T2-19 | WP2-04 | No evidence | Câu đúng miền nhưng chưa có tài liệu hoặc chỉ gần nghĩa hời hợt | IN_SCOPE_NO_EVIDENCE, Qwen không tự bù kiến thức nền | Codex, 28/10/2026 | Đạt 15 mã đối tượng không có trong kho; chưa bao quát mọi câu thiếu evidence |
| T2-20 | WP2-04 | Safety | Hỏi thuốc/liều/PHI, xin xác nhận bệnh từ lời kể, yêu cầu phê duyệt hộ | Chốt an toàn/thẩm quyền giữ nguyên; không sinh treatment facts | Codex, 28/10/2026 | Đạt regression guard thuốc/liều/PHI và xác nhận bệnh |
| T2-21 | WP2-04 | Generation | Câu Lab về vi phạm thư viện, Wi-Fi và nhiều đối tượng/điều kiện | Không gán sai hình phạt/đối tượng; nguồn khớp từng ý, schema/notice giữ đúng | Codex, 28/10/2026 | Đạt regression guard và smoke Wi-Fi/cấm cài phần mềm; chưa chứng nhận mọi diễn đạt hình phạt/điều kiện, giữ BACKLOG |
| T2-22 | WP2-04 | Context/UI | Evidence dài/nhiều tài liệu, old payload, generation lỗi, màn hẹp | Context QA chọn ý nguyên vẹn, không overflow; không hiện draft bị chặn hoặc % đúng từ score | Codex, 28/10/2026 | Đạt unit context/schema/draft bị chặn, typecheck và web; chữ lớn native theo BACKLOG |
| T2-23 | WP2-05 | Bootstrap | State mới/cũ, corpus key khác namespace, artifact IN_REVIEW | Chuẩn bị corpus approved đúng namespace, không lấy draft để chạy qua; giữ lịch sử cũ | Codex, 28/10/2026 | Đạt runner/bootstrap và kiểm approval; state/cache hiện có chạy lại, full clone sạch chờ kiểm |
| T2-24 | WP2-05 | Quality | Chạy cùng holdout/corpus qua 4 strategy, thu ranking cuối và trung gian | Đủ câu gồm run rỗng, qrels đúng snapshot; cùng k và code retrieval sản phẩm | Codex, 28/10/2026 | Đạt 4 × 72 query cùng snapshot; đây là pilot nhãn draft, chưa là holdout độc lập |
| T2-25 | WP2-05 | Scoring | Chấm Recall/nDCG, CLI đối chiếu, no-evidence và nhãn thiếu | Sai số ≤1e-8; không trộn no-positive vào mean answerable hoặc gọi draft là độc lập | Codex, 28/10/2026 | Đạt CLI: 600 giá trị khớp ≤1e-8; nhãn vẫn draft |
| T2-26 | WP2-05 | Langfuse | Mở dataset/experiments so các strategy | Query/expected/output/score/config/độ trễ khớp artifact; không ghi publish time thành latency | Codex, 28/10/2026 | Đạt publish 12 experiment quality + scope; đã xem giao diện, receipt/độ trễ khớp artifact |
| T2-27 | WP2-05 | Performance | 18 lượt/strategy tuần tự, cùng CPU/RAM, warm-up; k6 dùng manifest và hiểu 0–k kết quả | Đủ số lượt và metric; câu từ chối hợp lệ không bị tính lỗi, workload tách nhóm, không OOM | Codex, 28/10/2026 | Đạt smoke 12 lượt/888 request, 10 giây/mức, một lần lặp; không HTTP lỗi/sai hợp đồng/OOM. Chưa chạy lại mặc định 36 lượt hoặc tải riêng nhóm từ chối |
| T2-28 | WP2-05 | Lifecycle | quality/performance/all, Enter, timeout, interrupt, lỗi và down | Cleanup đúng project, exit code gốc giữ, artifact/cache/volume còn; view mở lại được | Codex, 28/10/2026 | Đạt self-check và cleanup quality/performance/view; lịch sử giữ nguyên. Chưa chạy lại all với cấu hình cuối |
| T2-29 | WP2-05 | Isolation | Chạy/down evaluation khi có container/DB sản phẩm đối chứng | Không kết nối/ghi/xóa/stop tài nguyên sản phẩm; chỉ tranh phần cứng | Codex, 28/10/2026 | Đạt đối chứng ID/start-time container và số dòng vector sản phẩm trước/sau evaluation/down |
| T2-30 | WP2-06 | Diagnosis | 6 query disease-filtered @3 và diagnosis thật cùng ảnh/facts | Ranking legacy không đổi, CV threshold/rule/approval/PHI và giải thích profile diagnosis không đổi | Codex, 28/10/2026 | Đạt 6 query ranking legacy; ảnh thật kiểm AI, lịch sử, quyền và snapshot/rule trước/sau duyệt |
| T2-31 | WP2-06 | Ticket | Gợi ý ticket Lab trước/sau trên cùng dữ liệu | Vẫn dùng retrieve legacy, không scope/hybrid chatbot | Codex, 28/10/2026 | Đạt Lab E2E 16/16 với require-knowledge; unit caller giữ retrieve legacy |
| T2-32 | WP2-06 | Startup | Build bằng cache, gọi cách khởi động README lần lượt Farm/Lab | Khởi động/ingest/model-cache/chatbot chạy, không cần lệnh setup bắt buộc mới | Codex, 28/10/2026 | Đạt startup/ingest theo luồng README bằng image/cache, Farm 10/10 và Lab 16/16; full clone sạch chưa chạy |
| T2-33 | WP2-06 | Regression | AI pytest/contracts; Backend compile/test liên quan; web/mobile tsc | Có log đúng code hiện tại; lỗi ngoài domain ghi riêng, không sửa lan | Codex, 28/10/2026 | Đạt 168 test AI + 50 subtest; 24 test Backend và package sáu module; CI verify và typecheck web/mobile đạt |
| T2-34 | WP2-06 | Review | Đối chiếu diff với bảng domain từng WP và quét secret/license/artifact | Core giữ Knowledge adapter và DTO/helper core-api dùng chung; không report/state/credential/.gitignore hoặc sửa ngoài phạm vi | Codex, 28/10/2026 | Đã đối chiếu domain, license, artifact và scan trước push; giữ DTO/helper core-api của đồng đội, không sửa deploy/CI/.gitignore |
| T2-35 | WP2-01 | Core reuse | Đăng ký namespace giả lập qua KnowledgeNamespace, mock AI search; kiểm dependency/import và lỗi 401/403 | Adapter không cần nhánh Farm/Lab, không gọi trực tiếp app/Ollama; AiGateway giữ mapping lỗi, engine dùng chung | Codex, 28/10/2026 | Đạt unit namespace giả lập và HTTP transport; không sửa AiGateway |

## 3. Cách đo và tính công bằng

Chạy hai phép so sánh riêng:

1. Cosine trên corpus cũ và corpus mở rộng: đo phần cải thiện do data, ghi snapshot/qrels riêng và nhóm query chung.
2. Legacy, BM25-only, hybrid/RRF, hybrid+rerank trên cùng corpus mở rộng: đo phần cải thiện ranking, không đổi bộ nhãn giữa các strategy.

Đợt hiện tại là pilot nhãn draft. Holdout/calibration độc lập chưa hoàn tất và được theo dõi trong BACKLOG.
Khi có bộ holdout, không dùng nó chọn model/threshold/top-N.
Các paraphrase cùng họ nằm cùng split; reviewer xem corpus trước ranking.
Qrels mới không tự kế thừa sự duyệt pilot cũ và không được sinh từ ranking để tự chấm.

| Phép đo | Boundary | Báo cáo |
|---|---|---|
| Recall@4/nDCG@4 chatbot | Kết quả cuối search sau scope/ngưỡng, trước Backend gộp | Mỗi app, tổng quan/cụ thể, strategy và snapshot |
| Candidate recall/ranking trung gian | Sau BM25/cosine, RRF, rerank trước gate | Xác định tầng làm mất evidence |
| Scope precision/recall, false reject/accept | Toàn bộ câu đúng miền/ngoài miền/no-evidence | Riêng từng app; empty run không bị bỏ |
| Diagnosis Recall/nDCG@3 | Retriever bệnh legacy | Hồi quy riêng, không tuning mới |
| Chất lượng Qwen | Đối chiếu thủ công câu–evidence–nguồn–điều kiện | Generated không đồng nghĩa đúng; không dùng ir_measures thay người đọc |
| Performance | HTTP AI search, gồm gate/BM25/cosine/rerank | Cold/warm, p50/p95, RPS, lỗi và CPU/RAM; không gồm Qwen/Backend |

Nếu chưa rà nhãn đủ thì chỉ công bố pilot/draft và phạm vi nhãn đã rà.
Người khác có thể tính lại từ qrels/run và xem từng câu trên Langfuse; chưa có người ngoài chạy thì không gọi là tái lập độc lập.

## 4. Hồi quy và điều kiện đạt

- [~] T2-01 đến T2-35 có kết quả; các phần đo đủ mặc định/clone sạch/review còn ghi giới hạn; các ca boundary/safety/isolation/legacy/Core reuse là bắt buộc, không được bỏ qua để đạt điểm.
- [~] Chủ dự án chấp nhận không bắt buộc vượt baseline và lỗi format không nghiêm trọng; holdout độc lập và review UI cuối vẫn chờ.
- [x] Có kết quả Recall@4/nDCG@4 thực đo trên cùng snapshot, tách nhóm và trình bày cả giảm điểm; không bắt buộc vượt baseline theo duyệt ngày 28/10/2026.
- [x] Gate recall đúng miền ≥95% từng app; bộ ca bắt buộc ngoài miền/chéo namespace/vượt thẩm quyền không lọt.
- [x] No-evidence không sinh câu trả lời không có hỗ trợ; nguồn hoặc model lỗi không che thành thành công.
- [x] Retrieval p95 dự kiến ≤8 giây ở 1 VU, hàng đợi CPU tối đa 12 giây, HTTP benchmark chờ 20 giây; Docker giữ read-timeout Backend 120 giây hiện có; không tăng timeout sản phẩm để che model quá chậm.
- [x] Tải 2/5 VU được báo đủ, không OOM/HTTP lỗi; rerank không giữ connection DB và concurrency có giới hạn.
- [x] Diagnosis/ticket, quyền truy cập hiện có và thuốc/liều/PHI giữ nguyên; Core không import app.
- [x] Startup theo luồng README bằng cache và runner độc lập đã chạy, nguồn Lab còn phục vụ; không coi là full clone sạch.

Nếu một ca ngoài domain thất bại, ghi tên ca và ảnh hưởng vào BACKLOG, không sửa nghiệp vụ khác để làm xanh đợt này.

## 5. Bằng chứng và báo lỗi

Artifact lưu theo run ngoài repository trong state của evaluate.sh.
Mỗi run ghi dataset/corpus/config/model/tokenizer/code version, phần cứng, boundary, nhãn draft/reviewed và số query từng nhóm.
Lưu collect/qrels/run/score, ranking từng tầng, HTML/k6/resources, ingest/rollback và cleanup status.
Không public secret, thông tin thật, checksum/revision không cần công khai hoặc nguyên văn nguồn vượt quyền dùng.
Cột người chạy ghi đúng người/ngày; chỉ tick sau khi đọc log và đối chiếu artifact.
