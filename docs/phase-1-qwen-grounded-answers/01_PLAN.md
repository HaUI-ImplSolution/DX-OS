# Plan thực hiện: Phase 1

Thứ tự: Spec → Swagger → Code.
Checklist ở [`02_CHECKLIST.md`](./02_CHECKLIST.md), kiểm thử ở [`03_TEST_PLAN.md`](./03_TEST_PLAN.md).

## Thứ tự và phụ thuộc

`WP1-01 → WP1-02 → WP1-03/WP1-04 → WP1-05 → WP1-06`.
WP1-03 và WP1-04 có thể triển khai độc lập sau khi schema dùng chung phía AI được chốt.
Đây là phân chia công việc, không yêu cầu mở thêm worktree hoặc agent.

## WP1-01: Chốt hợp đồng, phạm vi và tương thích

- Tham chiếu: `00_OVERVIEW.md` mục 4, 6 và 9; ARCHITECTURE mục 2–5.
- Mục tiêu: xác định đầu ra model, nơi điều phối và dữ liệu lưu trước khi sửa.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Hỏi đáp | Chỉ có question/passages | Có thêm answer khi client yêu cầu, giữ passages |
| Chẩn đoán Farm | CV, rule và evidence | Thêm generatedExplanation, giữ dữ liệu nghiệp vụ |
| Client cũ | Request không có tùy chọn sinh | Giữ luồng hiện tại |

**Hướng làm**

- Giữ nguyên DTO Core và dữ liệu quoted; app thêm DTO phần sinh trong module của mình.
- Chốt schema, giới hạn hiển thị, source IDs, mode và trường metadata do server cấp.
- Dùng query parameter optional để không thay body cũ; client mới vẫn hiểu response không có phần sinh.
- Khi không opt-in, giữ payload cũ và không serialize trường phần sinh mới; kiểm cả triển khai lệch phiên bản Backend/AI/client.
- Giữ constructor/signature cũ của DTO và entry point Farm bằng overload nhỏ nếu thêm tham số; test host đang tạo AiDiagnoseResponse trực tiếp phải compile được mà không sửa dx-host.
- Viết Swagger trên controller app trước triển khai service.

**Phạm vi ảnh hưởng**

- `app-farm/controller/KnowledgeController.java`, `DiagnosisController.java`, DTO tương ứng; `app-lab/controller/KnowledgeController.java`, `KnowledgeDtos.java`.
- Không sửa Core, route gateway, vai hoặc dependency Maven.

**Kết quả đầu ra**: hợp đồng ở cuối file được duyệt; diff ranh giới bằng không.

## WP1-02: Tổng hợp Qwen với khuôn trả lời cố định

- Tham chiếu: yêu cầu câu trả lời thực sự được sinh; skill RAG, dependency và Ponytail của AI.
- Mục tiêu: một bộ tổng hợp stateless dùng lại cho ba luồng, không thêm framework.
- Ước lượng: M.

**Hướng làm**

- Tận dụng ChatOllama, cấu hình model, prompt builder và Pydantic sẵn có trong `ai/app/rag/`.
- Đầu vào là câu hỏi/chủ đề, namespace do Backend xác định và evidence được cung cấp; không cho client chỉ định model, URL hoặc namespace tùy ý.
- Profile do điểm gọi cố định: farm_qa, lab_qa hoặc farm_diagnosis; mỗi profile có prompt/validation riêng, không để Qwen hoặc browser tự chọn chế độ chẩn đoán.
- Đầu ra nội dung cố định: summary, steps, note, source IDs; tên mục và bố cục thuộc UI.
- Giới hạn đề xuất: summary 350 ký tự, tối đa 3 steps × 180 ký tự, note nullable tối đa 220 ký tự, một lần gọi model mỗi yêu cầu.
- Giới hạn câu hỏi/evidence theo đầu vào và k hiện có, giới hạn token sinh; không gửi toàn corpus, ảnh, credential hoặc dữ liệu nhận dạng không cần thiết vào prompt.
- Chỉ khởi tạo/gọi phần LLM khi opt-in và có căn cứ; không thêm probe Ollama vào startup/health hoặc làm request cũ phụ thuộc model.
- Kiểm JSON/schema, giới hạn, nội dung trống/lặp, nguồn thuộc đầu vào; không cắt cụt câu để ép đạt giới hạn.
- Thay gate yêu cầu đầu ra xuất hiện nguyên văn bằng kiểm tra phù hợp với nội dung được diễn giải; giữ raw evidence riêng.
- Prompt coi tài liệu là dữ liệu, không làm theo chỉ dẫn trong câu hỏi/evidence; không gọi tool hoặc thực hiện hành động nghiệp vụ.
- Farm chỉ sinh giải thích và hướng xử lý phi hóa chất có căn cứ; loại phần thuốc/liều/PHI khỏi nội dung sinh, giữ thẻ điều trị do Backend dựng.
- Kiểm phát sinh thuốc/liều/PHI và mâu thuẫn rõ ràng; vi phạm thì fallback, không tuyên bố kiểm schema là bảo đảm đúng ngữ nghĩa.
- `generated` do server gán sau khi chấp nhận đầu ra; lỗi model/schema/nguồn trả `extractive`; không có evidence thì `insufficient_context` và không gọi model.
- Chỉ dự phòng với lỗi sinh nội dung đã xác định; không bắt chung lỗi phân quyền, dữ liệu hoặc database để giả thành câu trả lời thành công.
- Ngân sách gọi Qwen đề xuất tối đa 20 giây, không tự retry; xác minh nằm trong timeout hiện có của Backend/gateway trên cấu hình triển khai.
- Thử khả năng JSON/giới hạn của Qwen với đúng Ollama và dependency đang khóa; nếu không đạt thì dừng ở gate, không tự nâng version, đổi model hoặc sửa Core.

**Phạm vi ảnh hưởng**

- `ai/app/rag/rag_service.py`, `prompt_builder.py`, schema phần sinh trong RAG, `ai/app/api/router.py`, `schemas.py`, test AI liên quan.
- Thêm endpoint nội bộ `POST /api/v1/knowledge/answer`, bảo vệ bằng service key hiện có, nhận evidence chứ không ingest.
- Giữ nguyên `/knowledge/retrieve`, retriever, embedding, chunker, corpus và công cụ đánh giá.

**Kết quả đầu ra**: kiểm tra hợp đồng đạt và có bằng chứng gọi Qwen thật; fallback đúng mode và nội dung.

### Hai mục đích trả lời của Farm

| Nội dung | Chatbot Farm trên web | Giải thích chẩn đoán Farm |
|---|---|---|
| Đầu vào | Câu hỏi, bộ lọc bệnh nếu người dùng chọn, tài liệu truy xuất | Kết quả CV của đúng report, bối cảnh được cung cấp và tài liệu đúng bệnh |
| Mục tiêu | Giải thích kiến thức bệnh theo ý hỏi: nhận biết, nguyên nhân, phân biệt, phòng ngừa | Giải thích ý nghĩa nhận định CV và việc cần theo dõi trong lượt chẩn đoán |
| Cách diễn đạt | Kiến thức chung hoặc có điều kiện, không khẳng định người hỏi mắc bệnh | Gắn rõ với kết quả mô hình, không nâng thành kết luận chuyên gia đã duyệt |
| Nội dung bổ sung | Tối đa 3 ý liên quan; hỏi khái niệm không bị ép thành các bước xử lý | Tối đa 3 việc theo dõi/xử lý phi hóa chất có căn cứ và phù hợp bối cảnh |
| Bằng chứng | Giữ nguyên passages của câu hỏi và nguồn; thiếu tài liệu cho phần nào thì nói rõ | Giữ rag_evidence, kết quả CV và snapshot giải thích theo diagnosis |
| Thông số điều trị | Không suy thuốc/liều/PHI từ tài liệu hoặc câu hỏi | Thẻ thuốc/liều/PHI dựng trực tiếp từ rule Backend, tách phần Qwen |

Profile lab_qa trả lời quy chế/hướng dẫn từ namespace lab và không dùng ngữ cảnh bệnh hoặc quyền thực hiện nghiệp vụ.
Mỗi lần gọi giữ ngữ cảnh riêng; không dùng chẩn đoán gần nhất, history chat hoặc trạng thái global để lấp chỗ thiếu của câu hỏi.
Trích dẫn giúp đối chiếu căn cứ, không tự chứng minh câu diễn giải đúng; phải rà từng ý với evidence trong đánh giá nội dung.

## WP1-03: Hỏi đáp web Farm và Lab qua interface Core hiện có

- Tham chiếu: ARCHITECTURE mục 3; KnowledgePassage chỉ chứa trích dẫn.
- Mục tiêu: hai app trả answer riêng mà Core không đổi.
- Ước lượng: M.

**Hướng làm**

1. Giữ lời gọi `KnowledgeGateway.ask` để lấy passages đã lọc/gộp theo namespace của app.
2. Khi `includeAnswer=true` và có passages, app gọi endpoint tổng hợp qua `AiGateway.postJson`.
3. App trả `question`, `passages` nguyên ý nghĩa và `answer` optional; không đưa văn bản sinh vào `passages.content`.
4. Không có passages thì trả insufficient_context; model lỗi thì giữ passages và mode extractive.

Farm tiếp tục dùng toàn corpus hoặc bộ lọc diseaseCode hiện có để trả lời về các bệnh đã có tài liệu; không đổi thuật toán retrieval để thêm phần sinh.
Nếu câu hỏi mơ hồ, bộ lọc bệnh không khớp ý hỏi hoặc tài liệu chưa bao phủ các bệnh được so sánh, trả lời rõ phần có căn cứ và yêu cầu làm rõ phần thiếu.
Chatbot không gọi CV, không tạo diagnosis hoặc tự gán bệnh cho ruộng từ mô tả văn bản.
Trong phạm vi này chatbot không thêm API đọc rule thuốc: câu hỏi cần thuốc/liều/PHI được hướng tới phác đồ Backend đã duyệt; không lấy con số từ RAG để điền vào câu trả lời.

Backend mới gặp AI cũ chưa có endpoint tổng hợp phải dự phòng trên evidence đã lấy, không làm mất kết quả hỏi đáp.

**Phạm vi ảnh hưởng**

- KnowledgeService, KnowledgeController và DTO thuộc `app-farm`/`app-lab`; test service/controller của từng app.
- Không đổi KnowledgeGateway, KnowledgeAnswer, KnowledgePassage hoặc AiServiceGateway của Core.
- Không lưu lịch sử hỏi đáp, không migration Lab, không giữ transaction/database connection khi chờ Qwen.
- Chỉ client web yêu cầu includeAnswer; chatbot mobile không đổi request, response sử dụng hoặc giao diện.
- Không đổi phân loại ticket bằng luật, SLA hoặc các API nghiệp vụ khác.

**Kết quả đầu ra**: cả hai app có câu trả lời được sinh khi yêu cầu; trích dẫn và ranh giới namespace giữ nguyên.

## WP1-04: Giải thích chẩn đoán Farm và lưu cùng phiên bản

- Tham chiếu: lịch sử chẩn đoán, quyền xem và rule version hiện có của Farm.
- Mục tiêu: nội dung người dùng đọc sau duyệt đúng với bản kỹ sư đã xem.
- Ước lượng: M.

**Hướng làm**

- `POST .../diagnosis/ai?includeExplanation=true` là opt-in, mặc định false; chuyển thành `include_explanation` trong request nội bộ AI.
- Trong `inference_service.py`, gọi bộ tổng hợp sau graph hiện có khi đã có CV/facts/evidence; giữ graph stateless, không thêm agent hoặc thay trạng thái duyệt.
- Trả `generated_explanation` riêng, không sửa recommendation rule ID, structured_context hoặc rag_evidence.
- Profile farm_diagnosis dùng đúng CV/bối cảnh/evidence của lượt đó; không dùng prompt hỏi đáp chung hoặc suy luận lại tên bệnh.
- Qwen không nhận ảnh và model CV không xuất mô tả tổn thương: không viết rằng đã quan sát vết bệnh, diện tích hoặc mức độ nặng nếu đầu vào không có dữ kiện đó.
- Đặc điểm bệnh từ tài liệu phải được diễn đạt là kiến thức tham khảo, không đổi thành dấu hiệu chắc chắn có trên ảnh; thiếu bối cảnh thì không tự bổ sung.
- Thêm cột nullable `diagnoses.generated_explanation` JSONB bằng migration Farm mới, dự kiến `V14__diagnosis_generated_explanation.sql`; kiểm lại số migration trước khi viết.
- Lưu bản đã kiểm tra gồm version, mode, nội dung, model/prompt version và nguồn vào đúng diagnosis; không backfill hoặc sinh lại khi đọc/duyệt.
- Backend kiểm source IDs phần sinh thuộc evidence của chính lượt chẩn đoán trước khi lưu.
- Bản ghi cũ không có phần sinh; `recommendationText` và evidence giữ ý nghĩa cũ.
- Mọi DTO trả cho FARMER phải che generatedExplanation khi PENDING/REJECTED như các trường chẩn đoán đang bị che; chỉ trả phần được phép sau APPROVED.
- Giữ gọi AI ngoài transaction, khóa chạy AI theo report, kiểm diagnosis mới hơn, version rule, lịch sử và luồng duyệt hiện có.
- Phía AI materialize evidence rồi giải phóng giao dịch đọc trước khi chờ Qwen; không sửa dependency/session lifecycle dùng chung hoặc quyền database.

**Phạm vi ảnh hưởng**

- AI inference/schema; app-farm AI DTO/client nếu cần, DiagnosisService, Diagnosis/DTO, đường map báo cáo và migration Farm.
- Không sửa workflow Core, migration cũ hoặc label huấn luyện.

**Kết quả đầu ra**: phần sinh bền vững theo từng diagnosis; duyệt/đọc lại không đổi nội dung; không lộ dữ liệu chờ duyệt.

## WP1-05: Hỏi đáp web và giải thích chẩn đoán trên web/mobile

- Mục tiêu: đọc câu trả lời trực tiếp trước, xem nguồn khi cần; giữ bố cục chẩn đoán hiện tại.
- Ước lượng: M.

**Hướng làm**

- UI hỏi đáp dùng Trả lời, Thông tin liên quan/Hướng dẫn và Lưu ý; phần giải thích chẩn đoán dùng Giải thích kết quả nhận diện, Việc cần theo dõi và Giới hạn cần lưu ý.
- Nhãn theo profile do code chọn, không do Qwen sinh; mục rỗng ẩn, mỗi phần ý bổ sung tối đa 3 ý, không ép câu hỏi kiến thức thành hướng điều trị.
- Note chỉ hiện khi có giới hạn/lưu ý cụ thể; không tự thêm đoạn pháp lý hoặc lời dẫn chung vào mọi câu trả lời.
- Không dùng danh sách section tùy ý do Qwen đặt tên, không render HTML/Markdown từ model và không hiện JSON thô.
- Tách khối Giải thích của AI khỏi thẻ thuốc đã xác thực và phần trích dẫn tài liệu.
- Nguồn ở phần có thể mở rộng, liên kết từ danh mục sẵn có; similarity chỉ gắn với tài liệu.
- Chế độ dự phòng có nhãn rõ; không ghi câu trả lời AI nếu model chưa sinh được nội dung hợp lệ.
- Web hỏi đáp và web chẩn đoán gửi query opt-in; khi server cũ không trả phần sinh thì giữ cách hiển thị hiện tại.
- Chatbot mobile giữ nguyên; mobile chỉ đọc phần giải thích chẩn đoán đã được lưu và được phép hiển thị.

**Phạm vi ảnh hưởng**

- `frontend/src/shell/pages/Knowledge.tsx`, helper/types liên quan, component trình bày phần sinh nhỏ và style cần thiết.
- Component phần sinh dùng chung được export qua `frontend/src/shell/index.ts` theo ranh giới import hiện có, không thêm điểm truy cập vào ruột shell.
- Chỉ sửa trang app Farm/Lab nếu nhãn giới thiệu cần phản ánh đầu ra mới; không đổi router/auth/manifest.
- `frontend/src/apps/farm/components/DiagnosisPanel.tsx`, types/api Farm.
- `mobile/app/reports/[id].tsx`, component giải thích và phần type Diagnosis liên quan; không sửa mobile/app/knowledge.tsx, askKnowledge hoặc type KnowledgeAnswer của mobile.

**Kết quả đầu ra**: hỏi đáp web Farm/Lab và giải thích chẩn đoán web/mobile rõ ràng, hỗ trợ payload cũ; chatbot mobile giữ hành vi.

## WP1-06: Hồi quy, tài liệu và bàn giao

- Mục tiêu: chứng minh phạm vi, chất lượng trình bày và khả năng quay lại bản phát hành.
- Ước lượng: M.

**Hướng làm**

- Mặc định compile/đọc diff theo quy ước; kế hoạch chạy test và model thật phải được yêu cầu rõ, không dùng kết quả cũ để tích đạt.
- Kiểm tra ranh giới ngay sau mỗi WP; kiểm API/data cũ, cold/warm và quyền xem trước khi nghiệm thu.
- Dùng lại 36 câu hỏi pilot và 6 chủ đề chẩn đoán để đánh giá nội dung sinh riêng; không đổi câu hỏi/qrels hoặc suy điểm retrieval thành điểm trả lời.
- Kiểm thêm cùng một bệnh qua chatbot và chẩn đoán để xác nhận khác vai trò, không giả quan sát ảnh hoặc trộn bối cảnh.
- Ghi riêng tỉ lệ generated/fallback, thời gian, CPU/RAM và đánh giá của người đọc.
- Không dựng lại toàn stack hoặc xóa cache/volume sản phẩm để kiểm tra; nếu được phép chạy, dùng môi trường/ảnh mẫu và tài nguyên sẵn có.
- Cập nhật tài liệu tối thiểu tại vị trí đang mô tả đầu ra cũ; giữ nguyên lịch sử CHANGELOG.
- Rà diff, dependency, secret, checksum literal, file test thừa và artifact; không track report hoặc sửa .gitignore.

**Kết quả đầu ra**: mọi DoD có bằng chứng, lỗi ngoài phạm vi chỉ ghi nhận, chưa commit/push nếu chưa có yêu cầu.

## Thay đổi hợp đồng

| Loại | Đối tượng | Thay đổi | Tương thích |
|---|---|---|---|
| API app | `POST /api/v1/knowledge/ask` | Query `includeAnswer=false`; web gửi true, thêm answer optional | Giữ body, question, passages và status cũ; mobile không opt-in |
| API app Farm | `POST /api/v1/disease-reports/{reportId}/diagnosis/ai` | Query `includeExplanation=false`; thêm generatedExplanation optional | Request không có body vẫn hợp lệ |
| API app Farm | Các response chứa DiagnosisResponse, kể cả history/report | Thêm generatedExplanation optional theo quyền | Bản ghi cũ null, không đổi trường cũ |
| API AI nội bộ | `POST /api/v1/knowledge/answer` | Câu hỏi, namespace cố định và evidence → answer | Endpoint mới, có service key |
| API AI nội bộ | `POST /api/v1/diagnose` | include_explanation optional; generated_explanation optional | Giữ các trường hiện có |
| Core API | AiGateway, KnowledgeGateway và DTO | Không đổi | Giữ nguyên |
| Schema Farm | diagnoses.generated_explanation | JSONB nullable, một migration mới | Không backfill, app cũ bỏ qua cột |
| Cấu hình | OLLAMA_BASE_URL, OLLAMA_MODEL | Tái sử dụng | Không thêm Compose/env/CI |

Phần sinh có `version=1`, `mode`, `summary`, `steps`, `note`, `sourceIds`, `modelName`, `promptVersion`.
Trường công khai dùng camelCase theo app; schema AI dùng snake_case và ánh xạ rõ trong DTO.
Nội dung do model sinh chỉ gồm summary/steps/note/source IDs; mode và metadata do server cấp.
mode generated yêu cầu summary có nội dung, nguồn không rỗng, steps đúng giới hạn và metadata model/prompt hợp lệ.
Không nhận source IDs ngoài tập được cấp hoặc để model tự quyết định mode.
Nguồn hợp lệ là tập con của evidence đầu vào; không nhận URL, điểm similarity hoặc thông số điều trị do model tạo.
Mode extractive dùng nội dung lấy nguyên từ evidence trong khuôn dự phòng; insufficient_context không tạo hướng dẫn hành động thiếu căn cứ.
Fallback không ghép toàn bộ evidence thành một đoạn dài trong answer.
Chỉ chọn dòng trọn ý, vừa giới hạn và phù hợp phạm vi; nếu không có thì dùng thông báo cố định, giữ tài liệu gốc ở passages/knowledgeEvidence để người dùng mở xem.
Mọi mode cùng áp dụng quy định thuốc/liều/PHI của Farm.

## Dữ liệu, migration và rollback

- Chỉ thêm migration Farm; không sửa bảng Core/Lab, seed, rule hoặc corpus.
- Nội dung đã lưu không được tính lại khi xem lịch sử, duyệt hoặc đọc báo cáo.
- Rollback bằng image/code cũ; giữ cột nullable và dữ liệu đã sinh, không down migration hay xóa dữ liệu.
- Client cũ tiếp tục gọi không opt-in; client mới chịu được thiếu trường answer/generatedExplanation.
- Migration đã áp dụng được giữ trong lịch sử Flyway của nhánh triển khai; lần sửa tiếp theo dùng migration mới, không xóa/sửa file đã chạy.

## Quyết định thiết kế

| # | Quyết định | Lý do |
|---|---|---|
| D1 | App điều phối qua gateway sẵn có; Core không sửa | Bảo vệ ranh giới sản phẩm đã phát hành |
| D2 | Nội dung sinh tách evidence và dữ liệu điều trị | Giữ ý nghĩa nguồn, score và thông số nghiệp vụ |
| D3 | Schema nội dung cố định, UI quyết định bố cục | Giảm dài dòng, lặp và định dạng khó đọc |
| D4 | Opt-in ở client mới, có fallback | Hạn chế thay đổi hành vi của client đã phát hành |
| D5 | Lưu phần giải thích theo diagnosis bằng cột riêng | Giữ đúng nội dung đã xem/duyệt và không đổi nghĩa trường cũ |
| D6 | Giữ dependency, model, deploy và CI | Tránh mở rộng phạm vi nâng cấp |
| D7 | Profile hỏi đáp/chẩn đoán riêng, chung schema trình bày | Đúng vai trò, có căn cứ và không trộn ngữ cảnh |

## Vị trí file dự kiến

Các đường dẫn Java dưới đây tính từ package `src/main/java/HaUI/ImplSolution/dxos/` của module tương ứng.
Chỉ tạo value object/component mới nếu chưa có phần dùng lại phù hợp; không thêm module hoặc tầng gateway mới.

| Phần | File hoặc vị trí chính | Nội dung |
|---|---|---|
| AI | `ai/app/rag/rag_service.py`, `prompt_builder.py`; schema phần sinh nhỏ trong RAG | Gọi Qwen, ba profile, kiểm nội dung/nguồn và fallback |
| API AI | `ai/app/api/router.py`, `schemas.py` | Endpoint tổng hợp có service key và trường optional của diagnose |
| Chẩn đoán AI | `ai/app/services/inference_service.py` | Nối tổng hợp sau graph và giải phóng giao dịch đọc trước chờ model |
| Farm hỏi đáp | `app-farm`: `farm/controller/KnowledgeController.java`, `farm/service/KnowledgeService.java`, `farm/dto/KnowledgeAnswerResponse.java` | Opt-in web, gọi gateway sẵn có và trả answer riêng |
| Lab hỏi đáp | `app-lab`: `lab/controller/KnowledgeController.java`, `lab/service/KnowledgeService.java`, `lab/dto/KnowledgeDtos.java` | Tương tự với profile/namespace Lab |
| Farm chẩn đoán | `app-farm`: `farm/controller/DiagnosisController.java`, `farm/service/DiagnosisService.java`, `farm/service/DiseaseReportService.java`, `farm/model/Diagnosis.java`, các DTO AiDiagnoseRequest/AiDiagnoseResponse/DiagnosisResponse trong farm/dto | Opt-in, kiểm/lưu snapshot và che phần chưa duyệt trong mọi response |
| Schema Farm | `backend/app-farm/src/main/resources/db/migration/farm/` | Một migration nullable mới, dự kiến V14; không sửa file cũ |
| Web dùng chung | `frontend/src/shell/pages/Knowledge.tsx`, `api.ts`, `types.ts`, `index.ts`, component/style phần sinh | Câu trả lời riêng và nguồn mở rộng; không đổi auth/router |
| Web Farm | `frontend/src/apps/farm/components/DiagnosisPanel.tsx`, `api.ts`, `types.ts` | Hiển thị giải thích riêng với CV/rule/evidence |
| Mobile Farm | `mobile/app/reports/[id].tsx`, phần type Diagnosis và component giải thích | Chỉ đọc phần giải thích được duyệt, không sửa chatbot |
| Kiểm chứng/tài liệu | Test tương ứng trong AI/Farm/Lab/UI; README đúng mục, TESTING, CHANGELOG, BACKLOG | Kiểm hồi quy, nội dung và bàn giao trong domain |

## Kết quả rà soát kế hoạch

| Nguồn đối chiếu | Điểm đã đưa vào kế hoạch |
|---|---|
| Template và kiến trúc Open-Core | Sáu WP, DoD, 19 case dự kiến, Core không đổi; app gọi qua interface hiện có |
| Ponytail, RAG và dependency | Một bộ tổng hợp, ba profile; giữ retriever/model/dependency, không thêm agent hoặc công cụ đánh giá mới |
| LangGraph | Giữ graph và trạng thái nghiệp vụ; tổng hợp phía AI sau graph, không đưa lời gọi sinh vào quyết định rule |
| Spring Boot/JPA và code Farm | Controller → service → DTO, Swagger trước code, transaction ngắn, migration nullable và redaction; giữ chữ ký cũ để host compile |
| UI và payload đang phát hành | Khuôn/nhãn do code chọn, opt-in web, đọc được payload cũ, không nâng cấp chatbot mobile |
| Verification | Schema không thay đánh giá ngữ nghĩa; mode/fallback phải trung thực; mọi kiểm thử/model thật vẫn chưa chạy |
