# Plan thực hiện: Phase 2

Thứ tự: spec → Swagger → code → build/đánh giá.
Phần triển khai đang được kiểm thử; tiến độ và bằng chứng ghi ở checklist.
Metadata của 7 tài liệu Farm đã ghi phê duyệt chủ dự án; nội dung corpus không đổi.

## Thứ tự và phụ thuộc

`WP2-01` → `WP2-02` → `WP2-03` → `WP2-04` → `WP2-05` → `WP2-06`.
Dữ liệu có thể được rà trong khi thiết kế contract; không ingest trước khi duyệt.

## Mẫu Core tham chiếu và phân công trách nhiệm

Đã đối chiếu Core ở `origin/Khuong/main` và nhánh hiện tại, cùng ARCHITECTURE, CONTRIBUTING, workflow/skill Backend và yêu cầu ba tầng trong đề thi.
Core gốc đã nhận namespace/nguồn do app khai báo, dùng AiGateway và gộp evidence; giữ cấu trúc đó khi nâng retrieval.

| Mẫu hiện có | Áp dụng vào kế hoạch |
|---|---|
| `dx-core-api/.../api/ai/AiGateway.java` và `api/knowledge/KnowledgeGateway.java` | Dùng lại port hiện có, không thêm gateway Farm/Lab riêng hoặc method BM25/Qwen vào interface |
| `dx-core/.../ai/AiServiceGateway.java` | Dùng lại HTTP/service key/timeout/error translation, không đổi lớp hoặc đưa thuật toán/model vào đây |
| `dx-core/.../knowledge/KnowledgeGatewayService.java` | Chỉ adapter ask sang search envelope; giữ nguồn đăng ký, gộp và retrieve legacy |
| `KnowledgeNamespace`, FarmKnowledgeNamespace, LabKnowledgeNamespace | App khai báo namespace và source catalogue; Core nhận qua constructor/ObjectProvider, không import app |
| `WorkflowDefinitions`, `FileStoreService` | Học cách nhận phần khai báo qua interface; chỉ tham chiếu, không sửa các lớp này |
| FarmAutoConfiguration/LabAutoConfiguration và Maven dependencies | Giữ app được nạp theo lựa chọn hiện có, app chỉ phụ thuộc core-api, không sửa host/module selection |

BM25, cosine, RRF, rerank và phần thực thi Qwen là năng lực chung của AI Service.
Dùng module `ai/app/rag/` hiện có, không tạo một hệ thống core/app Python mới hoặc di chuyển toàn bộ code sang `ai/app/core/`.
Thuật toán chung không chứa nhánh viết riêng cho Farm/Lab; taxonomy, ngưỡng, nguồn và safety QA được khai báo theo namespace/profile và không thay cấu hình diagnosis.
Không nhân đôi thuật toán ở Java, từng app hoặc công cụ evaluation.

**Luồng hai lượt gọi chatbot dự kiến**

```text
App Farm/Lab → KnowledgeGateway.ask → AiGateway → AI search
  AI: scope → BM25 + cosine → RRF → rerank → evidence/status
  Backend: kiểm namespace, ánh xạ nguồn và gộp evidence
App KnowledgeService → AiGateway → AI knowledge/answer → Qwen
  Backend/App: kiểm kết quả theo contract/source → Web
```

Lượt search dùng endpoint mới; lượt Qwen giữ `/api/v1/knowledge/answer` hiện có và opt-in includeAnswer.
Không gọi Qwen khi thiếu evidence hoặc bị scope chặn; khi không yêu cầu includeAnswer vẫn trả evidence theo contract.
Không gộp retrieval và generation thành một request bỏ qua bước Backend, không gọi Ollama trực tiếp từ web hoặc app Backend.
Thuốc, liều, PHI và phê duyệt tiếp tục do Backend quyết định, không chuyển vào bộ máy RAG chung.

**Khóa phạm vi trước code**

Chỉ sửa các domain/file trong bảng ranh giới ở overview mục 4.
Trong Java Core chỉ đổi KnowledgeGatewayService và test của nó; AiServiceGateway/interface Core cùng dịch vụ nghiệp vụ khác là mẫu tham chiếu, không phải phạm vi refactor.
Trước mỗi WP đối chiếu danh sách file dự kiến; kết thúc WP đọc diff để phát hiện sửa ngoài miền.
Nếu cần thay interface, timeout/auth chung, schema, deploy hoặc luồng diagnosis thì phải đề xuất riêng và chờ chủ dự án mở phạm vi trước khi sửa.
Lỗi booking CI hoặc nghiệp vụ khác ghi BACKLOG; không mở rộng đợt chatbot để xử lý.

## WP2-01: Baseline và hợp đồng tách chatbot khỏi diagnosis

- Tham chiếu: overview mục 2, 4, 6; ước lượng M.
- Mục tiêu: sửa đúng đường chạy sản phẩm và khóa các luồng phải giữ nguyên.

**Hành vi thay đổi**

| Đường chạy | Hiện tại | Sau thay đổi |
|---|---|---|
| Chatbot Farm/Lab | App → KnowledgeGateway.ask → từ khóa Core → endpoint retrieve → gộp → AiGateway/Qwen | App → KnowledgeGateway.ask → AiGateway/search → Backend gộp → AiGateway/answer → Qwen |
| Diagnosis Farm | Graph → retrieve bệnh theo mã → facts Backend → giải thích diagnosis riêng | Giữ nguyên đường chạy, thứ tự, profile, k và filter |
| Gợi ý ticket Lab | KnowledgeGateway.retrieve | Giữ retrieve legacy, không qua scope chatbot |

**Hướng làm**

- Giữ `KnowledgeGateway.ask/retrieve` và `KnowledgeAnswer` hiện có, không thêm interface khi chưa cần.
- Thêm `POST /api/v1/knowledge/search` nội bộ AI chỉ cho chatbot, dùng cùng API-key boundary.
- Request có query, namespace do Backend gán, top_k và filter hợp lệ của app.
- Response là envelope scope/reason/namespace/corpus_version/strategy và danh sách chunk; không đổi list response của `/api/v1/disease/knowledge/retrieve`.
- `KnowledgeGatewayService.ask` gọi endpoint mới và hiểu envelope; `retrieve` giữ endpoint, min-score cosine 0,40 và gộp cũ.
- Trong `KnowledgeGatewayService`, bỏ `classify`, `fold`, `containsAny` và record `ScopeDecision` đang áp danh sách từ khóa Farm/Lab trước retrieval.
- `ask()` nhận scope từ envelope AI thay vì tự chặn bằng từ khóa; vẫn kiểm namespace đã đăng ký và gộp evidence như hiện tại.
- Chỉ sửa adapter Knowledge này trong `dx-core`; giữ nguyên chữ ký `KnowledgeGateway`, `retrieve()`, AiGateway, auth, workflow, storage và các Core service khác.
- Tái sử dụng constructor injection, namespace registration và xử lý lỗi hiện có; 401/403 AI là lỗi kết nối/xác thực dịch vụ, không đổi thành trạng thái thiếu tài liệu.
- Logic phân loại chatbot nằm ở AI theo namespace, metadata riêng thuộc app/data; không chuyển toàn bộ nghiệp vụ Core sang AI.
- Không truyền nguyên filter client để ghi đè namespace, chiến lược, ngưỡng hoặc profile.
- Giữ `score`/`similarity` là cosine để client cũ không nhận nhầm điểm RRF/rerank; thêm `bm25_score`, `rrf_score`, `rerank_score`, `rank`, `score_kind` và trạng thái chiến lược vào contract nội bộ.
- Với BM25-only candidate, tính cosine từ embedding cùng snapshot nếu cần điền field cosine; không bịa giá trị hay thay bằng rerank.
- Hybrid lọc evidence bằng ngưỡng đã calibration ở AI; Core không áp lại `knowledge.min-score` legacy lên danh sách này.
- Core giữ thứ tự tài liệu theo chunk xuất hiện đầu, gộp dòng theo chunk_index; không sắp lại hybrid bằng cosine.
- Scope ngoài miền/thiếu evidence đến từ envelope; không suy ra mọi kết quả rỗng đều ngoài miền.

**Phạm vi ảnh hưởng**

`ai/app/api/router.py`, `schemas.py`, module search chatbot mới và adapter `KnowledgeGatewayService`.
App Knowledge DTO/service chỉ sửa khi cần truyền metadata trung tính; không đổi Core workflow hoặc `dx-host`.
Cập nhật OpenAPI tương ứng trước code; client public vẫn dùng endpoint và opt-in `includeAnswer` hiện tại.

**Kết quả đầu ra**

Có contract search, danh sách caller và baseline legacy trên snapshot cũ hợp lệ.
Có bảng đối chiếu trách nhiệm với Core gốc và test hai lượt gọi qua AiGateway; không có thuật toán RAG trong Java Core.
Diagnosis/ticket không gọi search chatbot; client không chọn được namespace tùy ý.

## WP2-02: Corpus tổng quan, nguồn và nhãn có kiểm duyệt

- Tham chiếu: overview mục 2, 6; ước lượng L.
- Mục tiêu: bổ sung kiến thức đủ trả lời thay vì chỉ tăng số file hoặc nhãn topic.

**Hướng làm**

- Farm: lập ma trận câu hỏi–nguồn cho tổng quan/vòng đời, giống/gieo cấy, đất/dinh dưỡng, nước, cỏ dại, IPM, thu hoạch; ưu tiên lấp khoảng trống có câu hỏi thực.
- Lab: tổng quan các dịch vụ/quy định, quy trình sử dụng và hướng dẫn liên quan nhiều tài liệu; không giới hạn chỉ vài mã sự cố.
- Chủ dự án đã duyệt toàn bộ 7 artifact Farm hiện có ngày 28/10/2026; metadata corpus/registry/staging/queue đã APPROVED với reviewer project_owner.
- Không yêu cầu duyệt lại chính các artifact này; bài chỉ có mục lục/link vẫn cần lấy nội dung thực hoặc giữ giới hạn evidence, không suy diễn từ tiêu đề.
- Crawl staging, giữ tổ chức, URL/locator, ngày truy cập, vùng/vụ/đối tượng và điều khoản sử dụng; không xem uy tín là quyền tái phân phối.
- Farm mới ưu tiên cơ quan chuyên ngành/viện/trường/khuyến nông có tài liệu tiếng Việt; corpus bệnh hiện hữu không bị audit rồi thay thế ngoài phạm vi này.
- Lab allowlist từng host chính thức theo nguồn đang dùng và SICT; rà `lic.haui.edu.vn`, `itc.haui.edu.vn`, `tac.haui.edu.vn`, `sict.haui.edu.vn`, không wildcard toàn Internet.
- Manifest cách ly phản ánh review từng nguồn; không tự loại nguồn chính thức chỉ vì khác SICT, không coi `lab_active=0` là đạt coverage.
- Giữ thông tin quyền dùng và giới hạn kỹ thuật của 7 nguồn đã được owner duyệt; không đổi license thành giấy phép mở hoặc tự ghi đã được chuyên gia thẩm định.
- Nguồn bổ sung mới cần review/duyệt riêng; promotion phải ghi đúng quyết định, reviewer và ngày thực tế, không hardcode ngày hoặc giả tên chuyên gia.
- Tái sử dụng ingest atomic theo `(namespace, document_type)`; `farm_knowledge` có disease_code NULL và không thay disease_knowledge.
- Giữ chunker 350 ký tự/overlap 1 dòng và embedding hiện có trong đợt này; đoạn kiến thức tổng quan được biên soạn có heading/đối tượng rõ trước khi chunk.
- Bổ sung registry Farm tổng quát vào danh mục nguồn Backend, kiểm trùng/source_id; Lab/Qwen dùng registry đúng namespace.
- Tái sử dụng `data/scripts/test_knowledge_sources.py` để kiểm cả corpus bệnh lẫn corpus chatbot, không tạo bảng mapping nguồn thứ hai.
- Chuẩn hóa chọn corpus dùng chung cho CLI và evaluation: key `farm_knowledge` không phải namespace; luôn đọc `corpus.namespace`, `corpus.document_type` và code_field.
- Giữ cách gọi `scripts.ingest_diseases --corpus farm/lab` mà deploy đang dùng.
- Với farm, lệnh nạp corpus bệnh cũ cùng phần kiến thức tổng quát đã được duyệt; draft trong staging không làm startup lỗi và không được tự đưa vào DB.
- Nếu người dùng chỉ định ingest một artifact chưa duyệt thì fail trước khi ghi; log rõ corpus được nạp/bỏ qua để không che giấu lỗi.
- Cập nhật dry-run, provenance và rollback theo từng loại tài liệu; không sửa Compose để thêm job hoặc command mới.

**Coverage đã rà ngày 28/10/2026**

| Nhóm câu hỏi | Tài liệu hiện có | Giới hạn |
|---|---|---|
| Farm: nước, dinh dưỡng, phát thải thấp | Canh tác lúa chất lượng cao/phát thải thấp; Gói kỹ thuật canh tác thông minh | Giữ điều kiện ĐBSCL và phạm vi bài nguồn |
| Farm: cỏ dại, cấu tạo cây | Quản lý lúa cỏ; Sinh học lá lúa | Không thay corpus bệnh hoặc suy diễn thuốc |
| Farm: hữu cơ, thu hoạch | Mô hình hữu cơ An Giang hè-thu 2026; Quản lý thu hoạch/sau thu hoạch | Mô hình có vùng/vụ, không coi là quy trình áp dụng mọi nơi |
| Farm: bộ tập huấn khí hậu | Trang mục lục 858 ký tự | Chưa có nội dung các module; cần lấy bài con trước khi trả lời kỹ thuật chi tiết |
| Lab: học tập, thư viện | Máy tính/tài sản, mượn trả, phòng nhóm | Giữ đối tượng và điều kiện xử lý vi phạm |
| Lab: dịch vụ CNTT | Wi-Fi, email, máy chiếu, an toàn thông tin, bảo trì, Faronics | Faronics là công cụ giảng viên quản trị, không liệt kê như dịch vụ sinh viên |
| Lab: thi và LMS | LMS/thi máy trạm, quy định phòng thi, hoãn thi/phúc khảo | Không tự thực hiện thủ tục hoặc phê duyệt |

**Bộ câu hỏi và nhãn**

- Giữ 36 QA và 6 query diagnosis cũ làm tập hồi quy; không coi 6 query diagnosis là ảnh chẩn đoán thật.
- Thêm tối thiểu 10 câu tổng quan và 5 câu diễn đạt mơ hồ/không có từ khóa cụ thể cho mỗi app.
- Giữ bộ 75 câu scope hiện có làm đầu vào draft, rà nhãn và bổ sung câu sai dấu/đ/Đ, typo, trộn miền, adversarial.
- Chia calibration/holdout theo họ câu hỏi để paraphrase không rò sang hai tập.
- Gán nhãn scope, answerable/no-evidence, topic/khía cạnh kỳ vọng và relevance chunk 0/1/2; người duyệt xem câu và corpus trước khi xem ranking.
- Vì corpus nhỏ, rà toàn bộ chunk đủ điều kiện trong từng namespace/filter cho bộ holdout; chưa rà đủ thì ghi draft và tỷ lệ đã chấm.
- Một câu tổng quan có thể có nhiều tài liệu đúng; qrels không ép một tài liệu hay một bệnh duy nhất.

**Phạm vi ảnh hưởng**

`data/rag/staging/`, corpus/registry đã duyệt, crawler/validator/promotion, `ai/app/rag/ingestion_service.py`, `source_registry.py`, CLI ingest và resources Knowledge của hai app.
Không đổi treatment rules, SQL seed, schema hoặc file corpus bệnh.

**Kết quả đầu ra**

Có corpus/nhãn theo version, mapping nguồn đầy đủ và ingest/rollback không làm mất kho của app kia hoặc loại tài liệu khác.
Điều kiện Lab active và câu hỏi tổng quan được kiểm bằng nội dung thật, không chỉ validator hình thức.

## WP2-03: BM25 + cosine → RRF → rerank cho chatbot

- Tham chiếu: overview mục 6, Q1; ước lượng L.
- Mục tiêu: tăng khả năng tìm và xếp hạng evidence của câu tổng quan lẫn thuật ngữ cụ thể.

**Hướng làm**

- Tái sử dụng SentenceTransformer multilingual MiniLM, schema documents/chunks và pgvector cosine; không re-embed corpus bệnh.
- BM25 dùng `rank_bm25.BM25Okapi` nhỏ, pin/lock sau kiểm tra tương thích; không gọi `ts_rank` hoặc lexical token overlap là BM25.
- Tokenize title + content bằng Unicode/casefold nhất quán query/index; xử lý không dấu và đ/Đ có kiểm thử, giữ số/đơn vị và tên sản phẩm.
- Bắt đầu bằng token từ/âm tiết đơn giản; không thêm bộ tách từ tiếng Việt nặng trước khi có số liệu chứng minh cần.
- BM25 dựng từ chunk đã ingest, chỉ đọc, chia namespace và lọc document_type/disease_code trước lấy top-N.
- Cache theo snapshot/phiên bản và tập chunk thực tế; phát hiện thay đổi ở DB trong cùng read snapshot, dựng index mới rồi swap atomic, không trộn index cũ với vector mới.
- Không giữ transaction/connection DB suốt lúc chạy rerank/Qwen; serialize việc khởi tạo cache, giới hạn RAM và invalidation có test sau ingest/rollback.
- Lấy khởi điểm 20 BM25 và 20 cosine, hợp nhất bằng chunk_id; RRF `sum(1/(60 + rank))`, giữ tối đa 20 ứng viên để rerank.
- Đây là tham số ban đầu để calibration, không phải cấu hình đã chứng minh tối ưu.
- CrossEncoder chấm cặp query–title/content, giữ thứ tự và loại trùng; top_k cuối mặc định 4 như chatbot hiện tại.
- Với câu tổng quan, thử giới hạn tối đa 2 chunk/tài liệu khi vẫn đủ ngưỡng để tăng độ phủ; câu cụ thể không bắt buộc đa tài liệu.
- Không thêm topic boost legacy lên RRF một cách mặc định; lưu các biến thể riêng để biết thay đổi nào tạo hiệu quả.
- Reranker sử dụng: `cross-encoder/mmarco-mMiniLMv2-L12-H384-v1`, Apache-2.0, qua CrossEncoder sẵn có; BGE v2 m3 vượt ngân sách CPU/RAM của máy thử.
- Xác minh model với phiên bản thư viện đang khóa, tiếng Việt, license và CPU/RAM trước khi chốt; không nâng toàn bộ LangChain/Transformers chỉ để chạy model này.
- Pin artifact model trong manifest riêng tư và cache bền; không tải lại mỗi request/start, không tải CV trong môi trường đánh giá retrieval.
- Runtime dùng cache Hugging Face `/app/weights/huggingface` hiện có; chuẩn bị model qua job/CLI quản trị đang có, không để request đầu phải tải mạng không giới hạn.
- Giới hạn batch/candidate/length, thread, concurrency và thời gian rerank; tải model một lần theo process, không khởi tạo mỗi request.
- Đóng dấu snapshot/corpus_version theo namespace và loại tài liệu trong envelope/provenance; không lấy version một document làm version của cả kho.
- Model thiếu/lỗi/timeout: ghi strategy thực tế; mặc định không coi hybrid chưa rerank là đã đủ bằng chứng để sinh Qwen.
- Chỉ cho suy giảm về hybrid nếu có ngưỡng riêng đã calibration; nếu chưa có thì trả thiếu evidence/tạm không trả lời, không tự fallback sang nội dung sai.

**Phạm vi ảnh hưởng**

Module search chatbot trong `ai/app/rag`, `core/config.py`, runtime requirements/lock nếu thêm BM25 và chuẩn bị model trong CLI ingest.
Không thay thuật toán/score/default của `RAGRetriever.retrieve` được diagnosis/ticket và endpoint cũ sử dụng.
Không thêm Elasticsearch/OpenSearch, pg extension BM25 hoặc service rerank riêng.

**Kết quả đầu ra**

Có ranking từng tầng và profile không ảnh hưởng legacy; kiểm được index isolation, invalidation, tie-break và suy giảm.
Mọi điểm giữ đúng đơn vị; sigmoid rerank chỉ là điểm thứ tự, không phải xác suất đúng hoặc cosine.

## WP2-04: Gate mềm hơn và Qwen tổng hợp nhiều nguồn

- Tham chiếu: overview mục 4, 6; ước lượng M.
- Mục tiêu: giảm false reject và không làm câu tổng quan bị co về một tài liệu.

**Hành vi sau thay đổi**

| Câu hỏi | Xử lý |
|---|---|
| “Chăm sóc lúa cần chú ý những gì?” | Tìm các khía cạnh có evidence; tổng hợp ngắn, không tự xác nhận bệnh |
| “Có những dịch vụ hỗ trợ nào cho sinh viên?” trong Lab | Tìm các tài liệu dịch vụ liên quan, không yêu cầu tên sự cố |
| “Cần chuẩn bị gì trước khi sử dụng?” | Thử tìm trong namespace; nếu ý định chưa rõ thì nhắc nêu đối tượng, không bịa ngữ cảnh |
| Chủ đề rõ ràng ngoài miền | OUT_OF_SCOPE, không dùng Qwen để trả theo kiến thức nền |
| Đúng miền nhưng corpus chưa hỗ trợ | IN_SCOPE_NO_EVIDENCE, không lấy đoạn gần nhất bất kể nội dung |
| Xin liều thuốc hoặc yêu cầu chatbot thực hiện phê duyệt | Giữ chốt thẩm quyền hiện tại |

**Hướng làm**

- Dùng một quyết định scope trên đường search chatbot; không duy trì blacklist độc lập ở Core và AI.
- Từ khóa là tín hiệu, không phải giấy phép trả lời hoặc điều kiện bắt buộc cho truy xuất.
- Chỉ từ chối sớm khi chủ đề ngoài miền rõ theo toàn bộ ý định; nhắc một thuật ngữ ngoài miền trong câu so sánh đúng lĩnh vực không tự động bị chặn.
- Câu chưa rõ được truy xuất đúng namespace; kết hợp rerank/evidence/topic coverage để quyết định ANSWERABLE hoặc thiếu evidence/yêu cầu làm rõ qua reason_code.
- Ngưỡng riêng từng app và strategy, calibration trên câu tổng quan/cụ thể/no-evidence; không dùng số confidence 0,90 hardcode như xác suất đo được.
- Không đổi các guard Farm về tự chẩn đoán từ lời kể, thuốc/liều/PHI; không bỏ xác thực source, numeric/role và hành động vượt thẩm quyền của Lab.
- Chỉ profile `farm_qa`/`lab_qa` cho phép nhiều tài liệu đã chọn; bỏ ép Lab về title đầu cho câu tổng quan.
- Với câu cụ thể, ưu tiên evidence cùng chủ đề; với câu nhiều điều kiện, mỗi ý phải giữ đúng đối tượng/điều kiện và source_id.
- Giữ schema GeneratedAnswer và bố cục UI, nguồn tách riêng; điều chỉnh prompt QA để summary trả trực tiếp, details chia ý, không trộn nguồn trái điều kiện.
- Giữ giới hạn context QA hiện có làm mốc; chọn đoạn/ý nguyên vẹn theo relevance và độ phủ thay vì thấy tổng >2.000 ký tự là bỏ tất cả.
- Nếu cần tăng context QA thì benchmark riêng theo giới hạn model; không sửa ngân sách/prompt/guard dùng chung cho `farm_diagnosis`.
- Evidence không đủ các khía cạnh thì nói rõ giới hạn, không diễn giải thành quy trình đầy đủ.
- Web Knowledge giữ source cards; bỏ cách diễn giải cosine ×100 là phần trăm chính xác/độ tin cậy hybrid.
- Generation thất bại vẫn giữ notice và sources theo hành vi hiện có; nội dung bị guard từ chối không được hiện như câu trả lời.

**Phạm vi ảnh hưởng**

Search/scope, nhánh QA trong `rag_service.py` và `prompt_builder.py`; app Knowledge service/DTO nếu cần metadata; `frontend/src/shell/pages/Knowledge.tsx` và type tương ứng.
Không sửa mobile, profile diagnosis hoặc màn DiseaseReport.

**Kết quả đầu ra**

Câu đúng miền không có từ khóa cụ thể được thử tìm; Qwen nhận evidence đa tài liệu phù hợp và giữ format hiện có.
Bộ ca no-evidence, trái thẩm quyền và lỗi nguồn vẫn bị chặn.

## WP2-05: Đánh giá ir_measures và Langfuse bằng runner hiện có

- Tham chiếu: overview mục 6; ước lượng L.
- Mục tiêu: một lệnh đo tái lập, độc lập DB sản phẩm, chỉ rõ đóng góp từng thay đổi.

**Hướng làm**

- Giữ `ai/evaluate.sh quality|performance|all|view|down`, cache, lock, resource guard và trap cleanup; không tạo runner thứ hai hoặc gắn vào startup sản phẩm.
- `prepare.py` dùng manifest corpus được duyệt theo namespace/type, không lặp mù mọi CORPORA hoặc lấy dictionary key làm namespace.
- Corpus/chunk ID ổn định theo namespace, document code, chunk_index và snapshot; manifest khóa dữ liệu, nhãn, embedding, reranker, tokenizer, config và code version.
- Cache signature phải đổi khi nội dung, model, tokenizer hay chiến lược đổi; dữ liệu draft không được lén nạp để cho runner chạy qua.
- Chuẩn bị model một lần vào cache evaluation riêng, sau đó chạy offline; secrets/state/artifact ngoài repo.
- Trên cùng corpus mở rộng, chạy legacy, BM25-only, BM25+cosine/RRF và hybrid+rerank bằng code sản phẩm, không viết lại thuật toán trong collector.
- Ghi baseline corpus cũ riêng: so cosine cũ/cosine mới để đo đóng góp data; so các strategy cùng corpus mới để đo ranking.
- Dùng qrels đúng snapshot cho từng corpus; đối chiếu trước/sau khác corpus chỉ trên bộ câu hỏi chung và công bố rõ mẫu số/nhãn thay đổi.
- Quality chính lấy ranking cuối của search chatbot sau gate/ngưỡng trước gộp Backend; lưu ranking raw/intermediate để tách lỗi candidate, rerank và gate.
- Legacy comparison có mốc raw và gate cũ riêng để không quy cải thiện gate vào ranking; thứ hạng hết evidence được ghi là run rỗng, không bỏ câu lỗi khỏi mẫu số.
- Recall@4 và graded nDCG@4 theo từng app, tổng quan/cụ thể; diagnosis riêng @3 chỉ kiểm hồi quy legacy.
- Các câu không có tài liệu liên quan được tính refusal/false accept riêng; không ép Recall/nDCG của chúng thành 0 rồi trộn vào mean answerable.
- Report có candidate recall, tỷ lệ giữ qua gate, coverage khía cạnh, generated/fallback và lỗi nếu có kiểm API Qwen; không dùng ir_measures chấm chất lượng văn bản.
- Chạy diagnosis queries trên corpus bệnh cùng snapshot để kiểm không đổi; không mở rộng hay tuning diagnosis để nâng điểm.
- Export collect, qrels, TREC run, score và so sánh; đối chiếu CLI ir_measures/pytrec_eval trong sai số `1e-8`.
- Publisher dùng Langfuse local đang có, SDK/image phiên bản hiện tại; không nâng Langfuse chỉ vì docs online khác phiên bản.
- Dataset/experiment tách snapshot và strategy, lưu expected labels, output cuối, ranking từng tầng và điểm/độ trễ thật.
- Langfuse replay artifact sau đo; thời gian publish không được ghi như thời gian retrieval, không ghi Qwen answer nếu chỉ chạy retrieval.
- Giữ lịch sử experiment cũ, không xóa để tái dùng tên; reviewer có thể xem câu, nhãn, chunk và điểm từng chiến lược.
- Performance dùng endpoint search chatbot mới thay vì chỉ endpoint cosine cũ; giữ 18 lượt mỗi strategy (2 namespace × 3 VU × 3 lần).
- Mặc định so legacy và hybrid+rerank theo hai đợt tuần tự, tổng 36 lượt; warm-up riêng, cùng CPU/RAM và corpus, UI tắt trong lúc đo.
- Strategy đối chứng do config/server evaluation chọn, không thêm quyền client public ghi đè strategy; kiểm preflight load contract để tránh gọi nhầm endpoint legacy.
- Sửa k6 và validator đang khóa 12/24 câu và bắt đủ 4 dòng: đọc số câu từ manifest, chấp nhận 0–k chunk với scope/reason hợp lệ.
- Tách workload answerable và rejected/no-evidence, giữ cùng tỷ lệ câu giữa strategy; không để nhiều câu từ chối nhanh làm báo cáo latency trông tốt giả tạo.
- Đo p50/p95, RPS, lỗi, cold/warm, thời gian BM25/cosine/rerank và CPU/RAM sampled bằng Docker; không gọi p95 trung bình là p95 gộp.
- Benchmark chỉ đo HTTP AI retrieval, không bao gồm Qwen/Backend/mạng điện thoại; smoke sản phẩm thật nằm WP2-06.
- Evaluation build context/mount chỉ cần code retrieval/corpus/model liên quan; không sao chép CV weights hoặc dựng Backend sản phẩm.

**Phạm vi ảnh hưởng**

`ai/evaluate.sh`, `ai/evaluation/`, `scripts/evaluate_rag.py`, `benchmark_rag.py`, `load_rag.js`, bộ đề/qrels/metadata và publisher đang có.
Không dùng env/volume/database/network deploy; không sửa CI hoặc script khởi động chính.

**Kết quả đầu ra**

Một lệnh mỗi chế độ, có kết quả mới theo chiến lược và Langfuse cho đối chiếu; tự down tài nguyên đánh giá, giữ lịch sử/cache.
Điểm pilot/draft được ghi đúng trạng thái; không chứng nhận độc lập thay người kiểm nhãn.

## WP2-06: Build, chạy thật và bàn giao

- Tham chiếu: overview mục 6; ước lượng M.
- Mục tiêu: chứng minh sửa đúng chatbot và không ảnh hưởng diagnosis/cách khởi động chính.

**Hướng làm**

- Chạy unit/contract/boundary phù hợp; compile Backend và typecheck web/mobile theo phạm vi.
- Build lớp/image bị đổi, dùng cache và giới hạn tài nguyên; kiểm dung lượng trước, không build lại cả Docker stack hoặc global prune.
- Dùng cách gọi README cho Farm rồi Lab, kiểm startup ingest bằng CLI hiện có và model cache; không phát sinh bước bắt buộc người dùng nhập thêm lệnh.
- Chạy câu tổng quan/cụ thể/no-evidence/ngoài miền và các ca Qwen điều kiện–đối tượng; dùng tài khoản test theo vai.
- Chạy lại diagnosis cùng ảnh/mã bệnh/structured facts và ticket suggestions trên snapshot kiểm soát; chỉ so bất biến cần giữ, không hứa văn bản Qwen từng chữ không đổi.
- Chạy `quality`, `performance`, `all`, `view/down`, đường lỗi/interrupt và kiểm sản phẩm đối chứng không bị sửa DB hoặc stop container.
- Cập nhật tài liệu liên quan ngắn gọn, ghi kết quả và giới hạn thật; lỗi ngoài domain chỉ ghi BACKLOG.
- Rà diff, license, dependency, artifact/raw không cần thiết, secret và mã định danh/checksum không cần công khai; không đưa report/, credential hoặc state evaluation vào Git và không sửa .gitignore.
- Chưa commit/push hoặc xóa kế hoạch trước khi chủ dự án test local và chấp nhận kết quả.

**Kết quả đầu ra**

Có bằng chứng build/startup, điểm mới, Langfuse, hồi quy và cleanup; mọi DoD đạt hoặc trade-off được chủ dự án chấp nhận rõ.

## Thay đổi hợp đồng API, schema, cấu hình

| Loại | Đối tượng | Dự kiến | Tương thích |
|---|---|---|---|
| Endpoint AI | `/api/v1/knowledge/search` | Envelope search chatbot, cùng auth nội bộ | Mới; client public không gọi trực tiếp |
| Endpoint AI cũ | `/api/v1/disease/knowledge/retrieve` | Giữ legacy | Diagnosis/ticket không đổi |
| Interface Core | KnowledgeGateway | Giữ ask/retrieve hiện có | Không thêm interface app-specific |
| Public chatbot | Ask/Answer hiện có | Giữ opt-in, schema Qwen và sources; chỉ thêm metadata nếu cần | Client cũ bỏ qua field mới |
| Score | cosine/BM25/RRF/rerank | Tách tên, đơn vị và rank; không dùng chung threshold | Không biến cosine thành xác suất |
| DB | documents/document_chunks | Giữ schema, thêm dữ liệu đúng type/namespace | Không migration |
| Runtime | config retrieval/reranker | Defaults/validation tại Settings, cấu hình tùy chọn ghi AI README | Không sửa deploy hoặc thêm default Compose |
| Evaluation | EVAL_* / manifest | Defaults tại ai/evaluation/.env.example; strategy/model/cache và snapshot | Giữ mode shell và lịch sử cũ |

Tên biến mới chỉ chốt khi code dùng thật; các biến scope cũ khai báo nhưng không được sử dụng phải bỏ hoặc nối đúng logic, không để cấu hình giả.

## Dữ liệu, migration và rollback

Không đổi schema hoặc seed nghiệp vụ.
Ingest thay đúng namespace + document_type, giữ nguyên corpus bệnh và không xóa Lab để thử chính sách nguồn mới.
Rollback code/chatbot strategy và ingest lại artifact đã duyệt trước đó; invalidation cache BM25 theo snapshot mới.
Evaluation rollback/config chỉ ảnh hưởng state riêng; `down` không xóa volume/cache/artifact.

## Quyết định thiết kế và nguồn kỹ thuật

| # | Quyết định | Lý do |
|---|---|---|
| D1 | Endpoint chatbot riêng, endpoint legacy giữ nguyên | Hợp đồng envelope không làm thay list response hay diagnosis |
| D2 | BM25 + cosine/RRF rồi rerank | Ghép hai kiểu truy xuất mà không cộng điểm khác đơn vị |
| D3 | Gate cho câu mơ hồ thử retrieval | Thiếu từ khóa không đồng nghĩa ngoài lĩnh vực |
| D4 | Corpus/nhãn có duyệt, scope không thay safety | Coverage rộng hơn không trao quyền sinh thuốc hoặc sửa nghiệp vụ |
| D5 | Giữ runner shell và Langfuse replay độc lập | Tái lập và xem trực quan, không làm nặng runtime chính |

Tài liệu tham khảo chính thức, kiểm tra ngày 28/10/2026:

- [Sentence Transformers: retrieve và rerank](https://www.sbert.net/examples/sentence_transformer/applications/retrieve_rerank/README.html).
- [RRF và công thức theo thứ hạng](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/reciprocal-rank-fusion); chỉ tham khảo thuật toán, không thêm Elasticsearch.
- [rank_bm25 và giấy phép](https://github.com/dorianbrown/rank_bm25/blob/master/LICENSE).
- [BGE reranker multilingual, Apache-2.0 và CrossEncoder](https://huggingface.co/BAAI/bge-reranker-v2-m3).
- [ir_measures](https://ir-measur.es/en/latest/measures.html), [Langfuse datasets/experiments](https://langfuse.com/docs/evaluation/experiments/datasets); API triển khai phải khớp phiên bản repo đang khóa.
