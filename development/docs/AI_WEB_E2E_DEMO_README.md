# AI Web E2E Demo

Tài liệu này mô tả các thay đổi cần thiết để chạy luồng báo bệnh trên web, qua Backend và AI Service, rồi kiểm tra bằng ba role FARMER, ENGINEER và ADMIN.

## Khởi động

Tại thư mục `deploy/`, chạy:

```bash
docker compose up -d --build
```

Compose khởi động PostgreSQL, Backend, frontend, Keycloak, AI Service, Ollama và các dịch vụ demo hiện có. Job `rag-ingest` nạp corpus vào AI Vector DB; `ollama-init` bảo đảm model được tải trước khi AI Service chạy. Lần đầu cần mạng và dung lượng trống để tải container, model CV, embedding model và LLM.

Corpus RAG mặc định lấy từ `data/` (gốc repository) qua `AI_DATA_DIR`; đổi biến này nếu muốn dùng bộ corpus khác.
Thiết lập chuẩn là `AI_DATA_DIR=../data`; không dùng bản sao `ai/.local/data` trong luồng E2E của repository.

`data.sql` được sinh từ `data/structured/*.json` bằng `data/scripts/generate_seed_sql.py`.
Trên database sạch, Backend seed 38 bệnh, 6 thuốc, 1 treatment rule active và 13 disease management policy.
Rule duy nhất là `TR-CAND-004` cho `Tomato___Early_blight`, được nhóm dự án cho phép dưới trạng thái `E2E_ASSUMPTION` để chạy local/demo; đây không phải phê duyệt chuyên gia và không dùng cho production.
Mười lăm treatment rule candidate khác vẫn bị chặn, nên các bệnh tương ứng tiếp tục đi nhánh `review_required`.
Corpus RAG và structured treatment facts là hai nguồn độc lập: thiếu corpus thì không có semantic evidence, còn treatment rule vẫn do Backend quản lý và kiểm tra.

Schema hiện chỉ hỗ trợ một phiên bản database phát triển và không có migration.
Nếu volume PostgreSQL cũ còn cột `dose_amount`, cần dùng một `POSTGRES_DATA_DIR` sạch trước khi chạy bản này để Hibernate tạo schema `dose_min`/`dose_max` hiện hành.

## Địa chỉ và tài khoản

| Thành phần | Địa chỉ |
|---|---|
| Portal | `http://localhost:8090` |
| Web chính | `http://localhost:8082` |
| Backend Swagger | `http://localhost:8081/swagger-ui.html` |
| AI health | `http://localhost:8000/api/v1/disease/health` |
| Keycloak | `http://localhost:8180` |

| Role | Tài khoản | Mật khẩu |
|---|---|---|
| FARMER | `farmer1` | `farmer123` |
| ENGINEER | `engineer1` | `engineer123` |
| ADMIN | `admin1` | `admin123` |

## Luồng được nối

1. FARMER tạo thửa, vụ mùa và báo cáo bệnh kèm ảnh.
2. ENGINEER chọn **Chạy chẩn đoán AI**. Frontend gọi endpoint Backend `/api/v1/disease-reports/{id}/diagnosis/ai`.
3. Backend gửi URL ảnh và ngữ cảnh thực địa tới AI Service. AI chạy CV, lấy dữ kiện cấu trúc từ Backend, tìm bằng chứng theo ngữ nghĩa trong pgvector rồi tạo khuyến nghị có căn cứ.
4. Backend lưu kết quả AI vào `Diagnosis`; ENGINEER hoặc ADMIN có thể duyệt/từ chối.
5. Khi ghi nhận đã xử lý, ENGINEER hoặc ADMIN phải nhập liều thực tế nằm trong khoảng của rule đã chọn.
6. Thiếu rule thì kết quả vẫn được lưu và phải được kỹ sư xem xét; Backend chặn áp dụng thuốc.

## Thay đổi chính

- Frontend có nút gọi chẩn đoán AI và hiển thị khuyến nghị AI Backend trả về; luồng nhập chẩn đoán thủ công vẫn còn để demo nghiệp vụ.
- Backend giữ summary do AI tạo, hỗ trợ kết quả `review_required` không có rule, cho phép `treatment_rule_id` rỗng và từ chối áp dụng khi rule không tồn tại.
- Backend seed qua `data.sql` theo kiểu idempotent (`ON CONFLICT` trên khoá duy nhất của DB); file do generator sinh từ `data/structured/*.json`.
- Rule hỗ trợ khoảng liều `dose_min`/`dose_max`; Backend trả toàn bộ rule của một bệnh và kiểm tra liều thực tế trước khi tạo `TreatmentApplication`.
- AI chấp nhận structured context không có rule như trạng thái cần kỹ sư xem xét.
- Docker Compose chạy job nạp corpus trước khi khởi động AI và chạy Ollama/model cùng stack, nên không phụ thuộc Ollama đang mở sẵn trên host.
- Backend AI client dùng HTTP/1.1 để request JSON tương thích với Uvicorn trong container.

## Luồng kỹ thuật chi tiết

    FARMER
      └─ tạo báo cáo + ảnh
           └─ Backend lưu dữ liệu nghiệp vụ

    ENGINEER hoặc ADMIN
      └─ Frontend gọi Backend /diagnosis/ai
           └─ Backend gọi AI /api/v1/diagnose
                ├─ CV nhận diện disease_code và confidence
                ├─ AI gọi Backend lấy structured facts
                ├─ AI semantic search trong pgvector
                └─ Ollama diễn đạt recommendation từ evidence
                     └─ Backend kiểm tra response và lưu Diagnosis PENDING
                          ├─ có rule: duyệt → được phép ghi nhận áp dụng
                          └─ không có rule: review_required → chặn áp dụng

Một request hiện đi qua các bước sau:

1. Backend tạo request_id, gửi URL ảnh, crop_season_id, mã thửa và tối đa năm lần xử lý gần nhất cho AI.
2. AI tải ảnh từ URL nội bộ, giới hạn tối đa 10 MiB và chạy EfficientNet-B4.
3. Sau khi có disease_code, AI gọi GET /internal/v1/diagnosis-context. Backend exact-match disease_code; AI không đọc trực tiếp database nghiệp vụ.
4. Backend trả danh tính bệnh, toàn bộ treatment rule hiện hành nếu có và lịch sử xử lý gần nhất.
5. AI tìm evidence trong documents/document_chunks của AI Vector DB, sau đó dùng Ollama diễn đạt phần giải thích.
6. AI trả cv_result, rag_evidence, structured_context và recommendation.
7. Backend kiểm tra một trong hai contract:
   - treatment: selected_rule_id phải có trong rules; Backend đọc lại rule và so version trước khi lưu.
   - review_required: selected_rule_id phải rỗng và danh sách rules phải rỗng.
8. Backend lưu summary do AI trả về, nhưng vẫn đặt diagnosis ở PENDING để ENGINEER hoặc ADMIN quyết định.
9. Mỗi báo cáo chỉ có một diagnosis. Muốn chạy lại cần tạo báo cáo mới.
10. TreatmentApplication chỉ được tạo sau khi diagnosis được duyệt, có treatment rule hợp lệ và liều thực tế nằm trong khoảng cho phép.

## Thay đổi nằm ở đâu và vì sao

### Frontend

| File | Thay đổi | Lý do |
|---|---|---|
| [frontend/src/api.ts](../../frontend/src/api.ts) | Thêm hàm gọi endpoint chẩn đoán AI | Trước đó Web chỉ nhập kết quả thủ công nên chưa chạy AI thật |
| [frontend/src/components/DiagnosisPanel.tsx](../../frontend/src/components/DiagnosisPanel.tsx) | Thêm nút chạy AI, trạng thái loading/lỗi; giữ form thủ công; ẩn thao tác apply khi không có rule | Cho phép test E2E mà không phá luồng demo cũ và tránh mời người dùng thực hiện hành động Backend chắc chắn từ chối |
| [frontend/src/styles.css](../../frontend/src/styles.css) | Thêm banner cảnh báo thiếu rule | Làm rõ vì sao diagnosis đã review nhưng chưa thể áp dụng thuốc |

### Backend

| File | Thay đổi | Lý do |
|---|---|---|
| [Diagnosis.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/model/Diagnosis.java) | Cho phép treatment_rule_id rỗng | Không làm mất kết quả CV/RAG chỉ vì bệnh chưa có rule được duyệt |
| [TreatmentRule.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/model/TreatmentRule.java) | Lưu khoảng `doseMin`/`doseMax` và trạng thái `active` | Giữ nguyên khoảng liều; rule bị thu hồi không tham gia lookup mới nhưng vẫn có thể được giữ để bảo toàn lịch sử |
| [InternalAiContextService.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/service/InternalAiContextService.java) | Bệnh có thật nhưng thiếu rule sẽ trả rules rỗng thay vì 404 | Phân biệt “không biết disease_code” với “đã biết bệnh nhưng cần review” |
| [DiagnosisService.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/service/DiagnosisService.java) | Kiểm tra hai path treatment/review_required, tải lại đúng rule AI chọn, xác minh version và kiểm tra liều thực tế | Backend vẫn là nguồn sự thật và lớp quyết định cuối; AI không thể tự đưa rule lạ, rule cũ hoặc liều ngoài khoảng |
| [AiDiagnosisClient.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/service/AiDiagnosisClient.java) | Ép HTTP/1.1 | Khắc phục tương thích Java HTTP client với Uvicorn trong container hiện tại |
| [application.yaml](../../backend/DX-Farm/src/main/resources/application.yaml) | Tăng read timeout từ 15 lên 120 giây | Cold start CPU gồm CV, embedding và LLM có thể vượt 15 giây |
| [data.sql](../../backend/DX-Farm/src/main/resources/data.sql) | Sinh từ `data/structured/*.json` theo eligibility đã duyệt | Mọi giá trị seed truy được về `data/` và review qua diff |
| [DiseaseManagementPolicy.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/model/DiseaseManagementPolicy.java) | Entity/bảng `disease_management_policies`, seed 13 guardrail | Lưu chính sách chặn/tra cứu cho bệnh không có thuốc chữa trực tiếp |

### AI Service

| File | Thay đổi | Lý do |
|---|---|---|
| [ai/app/api/schemas.py](../../ai/app/api/schemas.py) | Cho phép review_required, rules rỗng, selected_rule_id rỗng và kiểm tra khoảng liều | Không ép AI chọn hoặc bịa rule khi Backend chưa có dữ liệu deterministic |
| [ai/app/clients/backend_client.py](../../ai/app/clients/backend_client.py) | Chấp nhận response Backend không có rule | Đồng bộ client với contract nội bộ mới |
| [ai/app/services/inference_service.py](../../ai/app/services/inference_service.py) | Chọn path theo structured facts và kiểm tra invariant trước khi trả response | Chặn response mâu thuẫn như treatment không có rule hoặc review_required lại chọn rule |
| [ai/scripts/check_diagnose_contract.py](../../ai/scripts/check_diagnose_contract.py) | Thêm kiểm tra nhánh không có rule | Bảo vệ contract an toàn khỏi regression |
| [ai/Dockerfile](../../ai/Dockerfile) | Đưa scripts vào image | Cho phép container rag-ingest dùng chung image AI để nạp corpus |

### Docker và dữ liệu

| File | Thay đổi | Lý do |
|---|---|---|
| [deploy/docker-compose.yml](../../deploy/docker-compose.yml) | Thêm Ollama, ollama-init, rag-ingest và volumes model/data | Dựng được toàn bộ luồng bằng một lệnh, không phụ thuộc Ollama hoặc bước ingest đang chạy trên host |
| [deploy/docker-compose.yml](../../deploy/docker-compose.yml) | Runtime dùng ai_vector_ro, ingest dùng ai_vector_ingest | AI runtime chỉ đọc, job quản trị mới được thay corpus |
| [deploy/docker-compose.yml](../../deploy/docker-compose.yml) | Mount AI_DATA_DIR read-only và cho phép đổi POSTGRES_DATA_DIR | Nạp corpus từ `data/` vào container `rag-ingest` mà không đóng vào image; vẫn cho phép dùng database demo riêng mà không ghi đè dữ liệu cũ |
| [deploy/.env.example](../../deploy/.env.example) | Bổ sung URL nội bộ, model, timeout, data path và service keys mẫu | Có cấu hình tham chiếu để người khác dựng lại stack local |
| [deploy/docker-compose.yml](../../deploy/docker-compose.yml) | Fallback mặc định của `BACKEND_TO_AI_READ_TIMEOUT_MS` cho service `backend` đổi từ 15000 lên 120000 | Compose luôn set env var này nên trước đó nó ghi đè giá trị 120000 trong `application.yaml` khi `.env` chưa khai báo, khiến timeout thật vẫn là 15 giây |

## Phương án khác và trade-off

| Vấn đề | Cách hiện tại | Phương án khác | Được và mất |
|---|---|---|---|
| Bệnh chưa có rule | Lưu diagnosis theo review_required, cho review nhưng chặn apply | Fail cả request; cho LLM sinh rule tạm; hoặc tạo RecommendationDraft riêng | Hiện tại giữ được kết quả CV/RAG và an toàn hơn LLM-generated rule, nhưng treatmentRule phải nullable và UI phải giải thích trạng thái “đã duyệt nhưng chưa được áp dụng”. Entity draft sạch domain hơn nhưng tăng schema/API/UI |
| Thực thi AI | HTTP đồng bộ, timeout 120 giây | Queue + async job + polling/WebSocket | Đồng bộ ít code, dễ demo; đổi lại giữ request lâu, khó scale và dễ timeout. Async tốt hơn cho production nhưng cần retry, idempotency, job state và UI tiến độ |
| LLM | Ollama trong Compose | Ollama host, managed API hoặc template không LLM | Compose tái lập tốt và dữ liệu không rời máy, nhưng tải model nặng và CPU chậm. Host tận dụng GPU nhưng phụ thuộc máy. API dễ scale nhưng tốn phí/cần mạng. Template ổn định nhưng kém linh hoạt |
| Nạp RAG | Job one-shot trước AI | Ingest thủ công, DB snapshot hoặc pipeline quản trị | Tự động và dễ dựng lại, nhưng startup lâu và recreate job có thể re-embed corpus. Pipeline riêng quản trị tốt hơn nhưng cần versioning và quy trình deploy data |
| Phân phối corpus | Commit vào Git tại `data/` (gốc repository) + bind-mount read-only vào container `rag-ingest` | Không commit (mỗi máy tự có data riêng), đóng vào image, hoặc artifact storage có version/checksum | Commit giữ corpus tái lập được cho mọi người clone nhánh, đổi lại repo nặng hơn theo kích thước corpus. Đóng vào image thì mỗi lần đổi corpus phải rebuild; artifact storage tốt hơn cho corpus lớn nhưng cần thêm hạ tầng |
| Seed structured facts | `data.sql` idempotent, sinh từ `data/structured/*.json` qua generator | Migration, importer CLI/API hoặc seeder có version | Phù hợp giai đoạn phát triển một schema, nhưng audit/rollback kém hơn pipeline production |
| Recommendation text | Lưu summary do AI trả | Backend tự dựng text hoặc lưu toàn response AI vào bảng audit | Giữ được phần giải thích CV+RAG+LLM, nhưng hiện chưa lưu evidence/generation metadata riêng. Backend tự dựng deterministic hơn nhưng mất phần diễn giải RAG |
| HTTP protocol | Ép HTTP/1.1 | Auto-negotiation hoặc reverse proxy HTTP/2 | Sửa tương thích với thay đổi nhỏ, nhưng không tận dụng multiplexing khi tải đồng thời tăng |
| Rule lookup | Backend trả mọi rule theo ID; AI chọn rule đầu tiên một cách deterministic | Chấm điểm theo priority, thời gian hiệu lực, bối cảnh ruộng và lịch sử xử lý | Đủ để test nhiều rule và Backend tải lại đúng ID đã chọn, nhưng thứ tự ID chưa phải chiến lược nông học tối ưu |
| Biểu diễn liều | Rule lưu khoảng min/max và người duyệt nhập liều thực tế | Lưu một liều mặc định hoặc mô hình liều theo cây trồng/giai đoạn | Khoảng liều giữ đúng dữ liệu review và có kiểm soát ở Backend; đổi lại UI phải yêu cầu thêm một giá trị khi apply |

Không chọn phương án để LLM tự tạo tên thuốc, liều hoặc PHI. Cách đó phá vỡ deterministic-first và biến RAG thành nguồn sự thật nghiệp vụ không kiểm soát được.

## Phạm vi kết quả và giới hạn

Nhánh hiện đã nối được luồng thật CV → Backend facts → semantic RAG → Ollama → lưu Diagnosis → duyệt/từ chối. Hai nhánh an toàn đều được biểu diễn: có rule thì có thể áp dụng sau khi duyệt; không có rule thì giữ kết quả để chuyên gia review nhưng Backend chặn áp dụng.

Các phần chưa đạt được:

- Chưa dùng async job; request giữ kết nối trong suốt thời gian suy luận.
- Chưa lưu riêng rag_evidence, request_id, generation_mode và timings để audit/replay.
- DiseaseManagementPolicy đã có bảng và dữ liệu seed nhưng chưa được internal API/AI sử dụng; bệnh không có rule vẫn đi vào review_required.
- AI hiện chọn rule đầu tiên trong danh sách Backend trả về; chưa có priority hay bộ chọn dựa trên ngữ cảnh nông học.
- Chưa benchmark concurrency, cold start và yêu cầu RAM/CPU.
- API key và tài khoản mẫu chỉ dùng local, chưa có secret management production.
- CV không có ngưỡng confidence tối thiểu; mọi ảnh đều đi hết pipeline kể cả confidence rất thấp hoặc ảnh không phải lá cây bị bệnh.
- Frontend không tự phát hiện diagnosis đã được tạo khi request `/diagnosis/ai` bị rớt giữa chừng; phải tự reload trang để thấy trạng thái mới nhất.
- Chưa validate kích thước ảnh ở phía client trước khi upload; vượt giới hạn 10MB chỉ báo lỗi sau khi đã upload xong.
- Chưa có phản hồi tiến trình khi chờ AI xử lý (có thể tới 120 giây); chỉ có spinner tĩnh, không có bước trung gian.

Rule `TR-CAND-004` version `2026.09.26-e2e-assumed-v1` chỉ là giả định có truy vết để kiểm thử E2E, chưa phải phê duyệt của chuyên gia nông nghiệp có thẩm quyền cho sử dụng thực tế.
Mười lăm candidate còn lại không được seed và không được bịa liều hoặc PHI thành structured facts.

## Kết quả kiểm tra E2E

Ngày 26/09/2026 đã dựng Backend, Keycloak và PostgreSQL trên database tạm sạch để kiểm tra regression của seed và luồng nghiệp vụ ba role:

- Seed ổn định ở 38 bệnh, 6 thuốc, 1 treatment rule active và 13 policy; start lặp không tạo bản ghi trùng.
- FARMER tạo thửa, vụ mùa và báo cáo được; gọi endpoint chẩn đoán bị từ chối `403`.
- ENGINEER dùng endpoint chẩn đoán thủ công dành cho testing/demo, nhận đúng `ORTIVA_600SC`, liều `1.50 L/ha`, PHI 7 và version `2026.09.26-e2e-assumed-v1`; diagnosis được lưu ở trạng thái `PENDING`.
- ADMIN duyệt được diagnosis; liều `1.49 L/ha` bị Backend chặn `409`, còn `1.50 L/ha` được lưu và tạo khóa thu hoạch đến 03/10/2026.
- Database có đúng một diagnosis đã duyệt và một treatment application; Backend test chạy bằng JDK 21 qua 1/1 test.

Kiểm tra này chủ đích cô lập seed, phân quyền và state machine nên không gọi CV/RAG/Ollama.
Luồng AI đầy đủ vẫn theo contract mô tả ở trên và cần ảnh kiểm thử CV phù hợp khi chạy lại; không dùng kết quả chẩn đoán thủ công làm bằng chứng rằng chất lượng model hoặc semantic search đã được xác minh trong lượt này.

Kết quả nhận diện phụ thuộc ảnh đầu vào và không phải cam kết chính xác cho mọi ảnh. RAG chỉ cung cấp kiến thức tham khảo; liều lượng, PHI và quyết định điều trị phải đến từ rule nghiệp vụ được Backend quản lý.
