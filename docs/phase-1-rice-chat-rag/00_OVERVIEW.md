# Phase 2: Mở rộng hỏi đáp Farm/Lab và tìm kiếm hybrid

| Mục | Giá trị |
|---|---|
| Trạng thái | Đã đóng phạm vi triển khai kỹ thuật ngày 28/10/2026; các kiểm chứng mở rộng chưa hoàn tất chuyển BACKLOG |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | Chatbot web Farm/Lab, AI RAG, corpus và công cụ đánh giá độc lập |
| Ước lượng | 8–12 ngày làm việc, chưa tính thời gian duyệt nguồn và nhãn |
| Người phụ trách | Kỹ sư triển khai; chủ dự án/người có chuyên môn duyệt nội dung và nhãn |
| Bắt đầu, dự kiến xong | Chưa chốt |
| Phụ thuộc | Qwen có cấu trúc hiện có; corpus được phép dùng; Docker local và tài nguyên cho reranker |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi |
|---|---|---|---|
| Chủ dự án, 28/10/2026 | Hỏi tổng quan đúng lĩnh vực phải tìm được kiến thức, không cần chỉ rõ bệnh/sự cố | Bắt buộc | Chatbot Farm/Lab |
| Chủ dự án, 28/10/2026 | BM25 + cosine + rerank; nới chặn từ có kiểm soát | Bắt buộc | Chỉ chatbot |
| Chủ dự án, 28/10/2026 | Chẩn đoán giữ trạng thái hiện tại | Bắt buộc | CV, Graph, evidence bệnh và giải thích chẩn đoán |
| [ARCHITECTURE.md](../../ARCHITECTURE.md) và [CONTRIBUTING.md](../../CONTRIBUTING.md) | Core trung tính; interface/implementation/app registration tách nhau; Backend sở hữu facts | Bắt buộc | Backend/AI |
| [Đề thi chính thức](../reference/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf), mục 3, 4.1–4.2 | Core headless dùng chung; RAG/semantic search thuộc năng lực I – Intelligence | Bắt buộc | Dịch vụ RAG chung được app sử dụng qua Core |
| [ai/README.md](../../ai/README.md) | Runner độc lập, Langfuse local, một lệnh shell và tự dọn | Bắt buộc | Đánh giá |
| [BACKLOG.md](../BACKLOG.md) | Corpus Farm ít chủ đề; nhãn RAG chưa rà đầy đủ; hạn chế Qwen Lab | Tham chiếu | Coverage và chất lượng trả lời |
| [docs/_template](../_template/README.md) | Spec → Swagger → code; checklist cần bằng chứng | Bắt buộc | Toàn bộ |

Đã đọc `ai/.codex/AGENTS.md`, skill RAG và verification trong `ai/.codex/skills/`, cùng workflow và skill Spring Boot trong `backend/.claude/`.
Tái sử dụng pgvector, embedding, Qwen, gateway và runner; không đưa thêm framework RAG hoặc dịch vụ search riêng.

## 2. Lý do và hiện trạng khi bắt đầu

Câu tổng quan có thể cần nhiều tài liệu, trong khi corpus và các chốt xử lý hiện thiên về một bệnh hoặc một hướng dẫn cụ thể.
Điểm pilot Farm Recall@4 = 0,75 và nDCG@4 = 0,596363 cho thấy còn khoảng trống trên bộ nhãn đó, chưa xác định riêng lỗi do corpus, gate hay ranking.
Cần đo các tầng riêng để kiểm chứng nguyên nhân thay vì mặc định thêm reranker sẽ tăng điểm.

| Thành phần đã có | Hạn chế cần xử lý |
|---|---|
| Cosine pgvector, embedding multilingual MiniLM 384 chiều; boost tiêu đề 0,15 | Chưa có BM25 hoặc cross-encoder; câu rộng có thể thiếu bằng chứng nhiều khía cạnh |
| Scope gate từ khóa ở AI và `KnowledgeGatewayService` | Hai bản logic khác nhau; thiếu từ khóa bị từ chối dù đúng lĩnh vực; Core đang chứa từ khóa app |
| Chatbot Backend gọi endpoint retrieve rồi Qwen qua AiGateway | Đường chạy này không đi qua `RAGService.answer`; sửa riêng hàm đó không sửa được chatbot sản phẩm |
| Qwen có schema và guard, Lab chỉ giữ tài liệu đầu; context tối đa 2.000 ký tự | Chặn mất bằng chứng cần cho câu tổng quan; không được nới giới hạn dùng chung làm ảnh hưởng diagnosis |
| 7 tài liệu Farm mới và registry đã được chủ dự án duyệt ngày 28/10/2026 | Metadata đã APPROVED; có bài mới lấy được mục lục nên vẫn cần bổ sung nội dung để tăng coverage |
| Lab có manifest cách ly 9 nguồn vì không thuộc SICT | Loader mới từ chối registry đang có; validator báo `lab_active=0`; chưa được coi là corpus phục vụ hợp lệ |
| `--corpus farm` mặc định vẫn là corpus bệnh | Chưa nạp kiến thức tổng quát qua cách khởi động hiện có |
| `ai/evaluate.sh` và Langfuse replay đã có | Bootstrap lặp mọi `CORPORA`; draft mới làm lỗi, collector nhầm key `farm_knowledge` là namespace; cần cập nhật theo manifest |

## 3. Tổng quan sửa đổi

1. Chốt baseline và hợp đồng chatbot riêng, giữ nguyên đường retrieve cho diagnosis và trợ giúp ticket.
2. Rà nguồn, bổ sung kiến thức tổng quan có nội dung thực, duyệt trước khi ingest.
3. Chatbot kết hợp BM25 và cosine bằng RRF, sau đó cross-encoder rerank.
4. Câu mơ hồ được tìm trong namespace trước khi quyết định thiếu bằng chứng; không bắt buộc tên bệnh/sự cố.
5. Qwen tổng hợp nhiều đoạn phù hợp theo bố cục hiện có, giữ nguồn và guard an toàn.
6. Nâng runner hiện có để chấm so sánh, đo tải, xuất Langfuse local rồi tự dừng dịch vụ đánh giá.

| Module hoặc lớp | Có thay đổi | Giới hạn |
|---|---|---|
| `dx-core-api` | DTO/helper câu trả lời dùng chung đã có trên nhánh | Giữ `GeneratedAnswer`/`KnowledgeAnswerGenerator` do đồng đội bổ sung; không đổi interface AiGateway/KnowledgeGateway |
| `dx-core` | Có, adapter Knowledge | Bỏ từ khóa Farm/Lab; `ask` gọi search chatbot, `retrieve` giữ đường cũ; không đổi Core nghiệp vụ |
| `app-farm`, `app-lab` | Có, Knowledge và registry nguồn | Tích hợp evidence, metadata cần thiết; không đổi disease report, ticket workflow hoặc phê duyệt |
| `dx-host` | Không | Không đổi chọn app, seed hoặc migration |
| Frontend web | Có, màn Knowledge | Thông báo, nguồn, diễn giải điểm; không đổi giao diện chẩn đoán |
| Mobile | Không | Không thêm chatbot hoặc nâng SDK |
| AI | Có | Search chatbot, index BM25, reranker, gate và profile QA; giữ profile diagnosis |
| Data / Seed / Migration | Corpus/registry có; seed/migration không | Thêm corpus chatbot; giữ nguyên nội dung, chunk và embedding của corpus bệnh |
| Infra / Deploy / CI | Evaluation riêng có; `deploy/` và CI không | Tái dùng cache/model và entrypoint ingest hiện có; không thêm service sản phẩm |
| Dependency, giấy phép | Có nếu thêm BM25 | Pin tối thiểu, cập nhật lock, LICENSES và SBOM theo quy ước |
| Docs | Có | Chỉ mục liên quan trong AI README, RAG README, TESTING, BACKLOG, CHANGELOG và tài liệu nguồn |

## 4. Phạm vi

Domain là tìm kiếm và tổng hợp kiến thức cho chatbot web trong namespace đang được app chọn.
Farm hỗ trợ kiến thức trồng lúa, canh tác và IPM; không mở sang mọi cây trồng.
Lab hỗ trợ hướng dẫn sử dụng, quy định và dịch vụ CNTT/vận hành được tài liệu chính thức của Lab/HaUI bao phủ.
Câu rộng vẫn cần evidence; không dùng kiến thức nền Qwen để bù corpus trống.

Ngoài phạm vi: CV, ngưỡng 0,7, Graph chẩn đoán, truy xuất/giải thích `farm_diagnosis`, thuốc/liều/PHI, treatment rule, workflow, auth, schema nghiệp vụ, deploy và CI.
Trợ giúp ticket tiếp tục dùng `KnowledgeGateway.retrieve` như hiện tại, không bị kéo vào chatbot hybrid.
Không thêm bộ nhớ hội thoại, GraphRAG, LLM judge, tracing bắt buộc hoặc dịch vụ search mới.

Core cung cấp KnowledgeGateway/AiGateway trung tính và gộp evidence theo cách hiện có.
AiGateway chỉ chuyển request/response tới AI Service, gắn service key và chuyển đổi lỗi; không cài BM25, cosine, RRF, rerank hoặc chạy Qwen trong lớp gateway.
Bộ máy retrieval và generation dùng chung được triển khai một lần trong AI Service; Farm/Lab dùng lại qua namespace/profile.
App giữ namespace/registry và điều kiện nghiệp vụ; AI xử lý retrieval và scope theo namespace đã được server xác định.
Giữ hai lượt gọi: search → Backend gộp evidence → gọi Qwen khi includeAnswer và evidence hợp lệ.
Hợp đồng, bố cục và các chốt theo profile vẫn giữ riêng; cùng bộ máy không có nghĩa dùng chung dữ liệu hoặc bỏ safety.
Client không được tự đổi namespace để đọc kho của app khác.
Chủ dự án đã phê duyệt toàn bộ 7 tài liệu Farm do đồng đội đưa lên nhánh, ghi `approved_by=project_owner`, ngày 28/10/2026.
Quyết định này không phải phê duyệt nhãn đánh giá, chứng nhận chuyên gia độc lập hoặc quyền tái phân phối nguồn.
Nguồn bổ sung sau này cần quyết định duyệt riêng; không làm trống corpus Lab hiện hữu chỉ vì khác host SICT.

### Domain và ranh giới được sửa

| Domain được phép | Phạm vi file/lớp | Giới hạn |
|---|---|---|
| Cầu nối Knowledge của Core | `KnowledgeGatewayService`, DTO/helper câu trả lời dùng chung và test tương ứng | Đổi đường ask/đọc envelope; giữ đăng ký namespace, gộp và đường retrieve legacy |
| Bộ máy RAG chatbot dùng chung | `ai/app/rag/`, schema/route search mới, Settings và test liên quan | BM25/cosine/RRF/rerank/scope/QA; không viết lại engine cho từng app hoặc chuyển thuật toán vào Java Core |
| Tích hợp Knowledge theo app | Farm/Lab KnowledgeService/DTO, annotation KnowledgeController và registry nguồn | Giữ opt-in/profile/nguồn, không sửa workflow nghiệp vụ |
| Nội dung chatbot | Corpus tổng quát, registry, crawler/ingest/validator và bộ nhãn | Giữ corpus bệnh, structured facts và SQL seed; không thu thập dữ liệu ngoài miền |
| Màn chatbot web | Knowledge page, type/card trực tiếp liên quan | Format, scope, nguồn và ý nghĩa score; không đổi màn nghiệp vụ khác |
| Công cụ đánh giá | evaluate.sh, evaluation/, collector/scorer/publisher/k6 | DB và lifecycle riêng; không sửa startup sản phẩm |
| Đóng gói và tài liệu trực tiếp liên quan | Requirement/lock/license/SBOM, các mục tài liệu đã liệt kê | Chỉ dependency/model cần cho RAG; build bằng cache, không tái cấu trúc dự án |

Giữ DTO/helper dùng chung và bước lọc dòng thuốc khỏi evidence đã có trên nhánh.
Không bổ sung thay đổi vào diagnosis; kiểm hồi quy riêng output đã lọc.
Không sửa interface Core hiện có, `AiServiceGateway`, auth/SSO, workflow, audit, notification, storage, BI/monitor hoặc cách chọn app.
Không sửa CV/model/threshold, Graph, retriever legacy, prompt/guard diagnosis ngoài phần đã có, treatment rules, thuốc/liều/PHI, ticket suggestions, mobile, schema/migration/seed nghiệp vụ, `deploy/`, CI hoặc `.gitignore`.
Các file dùng chung như router, schema, config, rag_service và prompt_builder chỉ được sửa nhánh chatbot; phải có test chứng minh nhánh diagnosis giữ nguyên.
Nếu một thay đổi cần vượt bảng này, dừng phần phụ thuộc, ghi rõ file/lý do/ảnh hưởng và chờ chủ dự án mở phạm vi; công việc độc lập trong domain vẫn tiếp tục.
Lỗi có sẵn ngoài domain chỉ ghi BACKLOG, không sửa để làm xanh CI hoặc checklist.

## 5. Ưu điểm và hạn chế

BM25 bổ sung khớp thuật ngữ; cosine tìm diễn đạt gần nghĩa; reranker xếp lại các ứng viên theo câu hỏi.
Câu tổng quan có thể dùng nhiều tài liệu nhưng vẫn bị giới hạn ngân sách context và nguồn được phép dùng.
Rerank không tạo được kiến thức chưa có, không bảo đảm tiếng Việt tốt trước khi đo, và tăng CPU/RAM/độ trễ.
Xem [04_TRADEOFFS_RISKS.md](04_TRADEOFFS_RISKS.md).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Farm trả lời được câu tổng quan canh tác/chăm sóc và câu bệnh cụ thể khi corpus có hỗ trợ.
- Lab trả lời được câu tổng quan dịch vụ, quy định và câu kỹ thuật cụ thể; nội dung thuộc nhiều tài liệu không bị ép vào tài liệu đầu.
- Câu đúng lĩnh vực nhưng thiếu từ khóa được thử tìm kiếm; câu ngoài lĩnh vực rõ ràng, thiếu evidence hoặc cần thẩm quyền được phản hồi đúng trạng thái.
- Chatbot dùng BM25 + cosine → RRF → rerank thật; chiến lược sử dụng và trạng thái suy giảm được ghi rõ.
- Có một bộ máy RAG/generation chung trong AI Service, gọi qua AiGateway hiện có; hai lượt search và Qwen giữ Backend gộp evidence ở giữa.
- Core tuân thủ interface/implementation/app registration hiện có; không cài từ khóa app hoặc thư viện AI vào Java Core, không đổi các dịch vụ Core ngoài Knowledge.
- Mọi nguồn trả cho Qwen và web có registry, quyền dùng và locator; corpus mới chỉ nhận nội dung được duyệt.
- Diagnosis và ticket giữ đường legacy; 6 query diagnosis có ranking/evidence không đổi trên cùng snapshot, threshold và thuốc/liều/PHI không đổi.
- Bộ đánh giá có nhóm câu tổng quan/cụ thể/mơ hồ/no-evidence/ngoài miền riêng cho Farm và Lab; có calibration/holdout và nhãn theo snapshot.
- Báo Recall@4/nDCG@4 thực đo của bốn strategy trên cùng corpus, tách app và nhóm câu hỏi; nêu rõ phần tăng/giảm và giới hạn nhãn. Theo chủ dự án ngày 28/10/2026, không bắt buộc vượt baseline để nghiệm thu.
- Recall của gate trên câu đúng miền ít nhất 95% mỗi app; toàn bộ ca bắt buộc ngoài miền, chéo namespace và yêu cầu vượt thẩm quyền đều xử lý đúng.
- Quality đo tại ranking chatbot cuối sau gate/ngưỡng trước gộp Backend; có ranking trung gian để phân tích và điểm CLI khớp trong sai số `1e-8`.
- Các câu no-evidence không có nhãn dương không nhập chung Recall/nDCG của nhóm answerable; đo từ chối và false reject riêng.
- Một lệnh `./ai/evaluate.sh quality`, `performance` hoặc `all` tự chuẩn bị DB/model/tool, đo và xuất Langfuse/báo cáo; `view`, `down` vẫn hoạt động.
- Evaluation không kết nối DB/volume/network sản phẩm; Enter, timeout, lỗi và tín hiệu đều cleanup đúng project, giữ artifact/cache/lịch sử.
- Đạt ngân sách retrieval dự kiến p95 ≤ 8 giây ở 1 VU, không OOM/HTTP lỗi ở tải đã chốt; ghi cả phần cứng và cold/warm, không coi đây là SLA production.
- Build và kiểm thử thật cho Farm/Lab bằng cách khởi động trong README, dùng cache; không dựng thêm toàn bộ stack hoặc prune máy.

Tiêu chí so sánh điểm đã được chủ dự án điều chỉnh ngày 28/10/2026; các chốt đúng miền, đúng nguồn, thẩm quyền và hồi quy hệ thống vẫn giữ nguyên.

## 7. Phụ thuộc và thứ tự

`WP2-01` chốt contract/baseline → `WP2-02` corpus/nhãn → `WP2-03` hybrid → `WP2-04` gate/Qwen → `WP2-05` runner → `WP2-06` build, đo và bàn giao.
Baseline và đối chứng dùng cùng snapshot, embedding và phần cứng cho mỗi phép so sánh.
Duyệt corpus và nhãn là hai công việc riêng; không dùng sự đồng ý pilot cũ để duyệt dữ liệu mới.

## 8. Ảnh hưởng đến thi

Có demo hỏi tổng quan và hỏi cụ thể, nguồn kiểm chứng được và bảng so sánh từng chiến lược.
Langfuse cho xem query, nhãn, ranking và điểm; ir_measures chấm truy xuất, không chứng nhận câu Qwen đúng hoặc accuracy chẩn đoán.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Quyết định | Hướng trong kế hoạch | Mốc chốt |
|---|---|---|---|
| Q1 | Reranker và ngân sách máy | Dùng mMARCO multilingual MiniLM; BGE đo 20 cặp mất 24 giây và ~2,8 GiB RAM, MiniLM ~0,5 giây và ~926 MiB trong phép thử nhỏ | Đã đo 28/10/2026; chất lượng tiếng Việt tiếp tục kiểm bằng pilot |
| Q2 | Nguồn Lab | Rà allowlist host chính thức theo registry hiện hữu và SICT; không áp dụng SICT-only cho toàn bộ kho | Trước ingest WP2-02 |
| Q3 | Ngưỡng và tiêu chí | Calibration riêng từng app; khóa mục tiêu mục 6 và ngân sách trước holdout | Trước WP2-05 đo chính thức |
| Q4 | Phê duyệt | Chủ dự án đã duyệt 7 tài liệu Farm hiện có; nhãn đánh giá và dữ liệu bổ sung sau này được duyệt riêng | Nội dung hiện có đã chốt 28/10/2026; nhãn trước công bố |

## 10. Tài liệu cần cập nhật khi xong

- [x] `ai/README.md`, `ai/app/rag/README.md`: luồng, cache và một lệnh đánh giá.
- [x] `data/README.md`, hướng dẫn corpus/evaluation: review, nhãn, snapshot và rollback.
- [x] `docs/TESTING.md`: phương pháp và số đo mới nhất; không giữ bảng đo cũ.
- [x] `docs/BACKLOG.md`, `CHANGELOG.md`: chỉ cập nhật mục liên quan, giữ log cũ.
- [x] Swagger: search nội bộ và ý nghĩa score/status, không đổi contract diagnosis.
- [x] `LICENSES.md`, SBOM theo quy ước khi thay dependency/model; không gán license model cho nguồn crawl.
- [-] Không sửa `ARCHITECTURE.md`: ranh giới Core/app và AiGateway giữ nguyên; luồng chatbot ghi ở AI README.
