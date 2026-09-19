# AI Web E2E Demo

Tài liệu này mô tả các thay đổi cần thiết để chạy luồng báo bệnh trên web, qua Backend và AI Service, rồi kiểm tra bằng ba role FARMER, ENGINEER và ADMIN.

## Khởi động

Tại thư mục `deploy/`, chạy:

```bash
docker compose up -d --build
```

Compose khởi động PostgreSQL, Backend, frontend, Keycloak, AI Service, Ollama và các dịch vụ demo hiện có. Job `rag-ingest` nạp corpus vào AI Vector DB; `ollama-init` bảo đảm model được tải trước khi AI Service chạy. Lần đầu cần mạng và dung lượng trống để tải container, model CV, embedding model và LLM.

Corpus RAG mặc định lấy từ `ai/.local/data` qua `AI_DATA_DIR`. Đây là dữ liệu local, không được commit lên Git; cần có corpus tại đường dẫn này trước khi ingest.

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
5. Chỉ khi có treatment rule nghiệp vụ hợp lệ mới có thể ghi nhận áp dụng thuốc. Thiếu rule thì kết quả vẫn được lưu và phải được kỹ sư xem xét; Backend chặn áp dụng thuốc.

## Thay đổi chính

- Frontend có nút gọi chẩn đoán AI và hiển thị khuyến nghị AI Backend trả về; luồng nhập chẩn đoán thủ công vẫn còn để demo nghiệp vụ.
- Backend giữ summary do AI tạo, hỗ trợ kết quả `review_required` không có rule, cho phép `treatment_rule_id` rỗng và từ chối áp dụng khi rule không tồn tại.
- Seed Backend bổ sung danh mục nhận diện của 38 lớp CV. Đây chỉ là danh tính bệnh, không tự tạo thuốc hoặc treatment rule.
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
4. Backend trả danh tính bệnh, treatment rule hiện hành nếu có và lịch sử xử lý gần nhất.
5. AI tìm evidence trong documents/document_chunks của AI Vector DB, sau đó dùng Ollama diễn đạt phần giải thích.
6. AI trả cv_result, rag_evidence, structured_context và recommendation.
7. Backend kiểm tra một trong hai contract:
   - treatment: selected_rule_id phải có trong rules; Backend đọc lại rule và so version trước khi lưu.
   - review_required: selected_rule_id phải rỗng và danh sách rules phải rỗng.
8. Backend lưu summary do AI trả về, nhưng vẫn đặt diagnosis ở PENDING để ENGINEER hoặc ADMIN quyết định.
9. Mỗi báo cáo chỉ có một diagnosis. Muốn chạy lại cần tạo báo cáo mới.
10. TreatmentApplication chỉ được tạo sau khi diagnosis được duyệt và có treatment rule hợp lệ.

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
| [InternalAiContextService.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/service/InternalAiContextService.java) | Bệnh có thật nhưng thiếu rule sẽ trả rules rỗng thay vì 404 | Phân biệt “không biết disease_code” với “đã biết bệnh nhưng cần review” |
| [DiagnosisService.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/service/DiagnosisService.java) | Kiểm tra hai path treatment/review_required, xác minh rule version, lưu summary AI và chặn apply khi rule rỗng | Backend vẫn là nguồn sự thật và lớp quyết định cuối; AI không thể tự đưa rule lạ hoặc rule cũ |
| [AiDiagnosisClient.java](../../backend/DX-Farm/src/main/java/HaUI/ImplSolution/DX_Farm/service/AiDiagnosisClient.java) | Ép HTTP/1.1 | Khắc phục tương thích Java HTTP client với Uvicorn trong container hiện tại |
| [application.yaml](../../backend/DX-Farm/src/main/resources/application.yaml) | Tăng read timeout từ 15 lên 120 giây | Cold start CPU gồm CV, embedding và LLM có thể vượt 15 giây |
| [data.sql](../../backend/DX-Farm/src/main/resources/data.sql) | Seed identity đủ 38 lớp CV theo kiểu idempotent | Backend phải nhận đúng disease_code do model trả về; phần này không tạo thuốc hay treatment rule |

### AI Service

| File | Thay đổi | Lý do |
|---|---|---|
| [ai/app/api/schemas.py](../../ai/app/api/schemas.py) | Cho phép review_required, rules rỗng và selected_rule_id rỗng | Không ép AI chọn hoặc bịa rule khi Backend chưa có dữ liệu deterministic |
| [ai/app/clients/backend_client.py](../../ai/app/clients/backend_client.py) | Chấp nhận response Backend không có rule | Đồng bộ client với contract nội bộ mới |
| [ai/app/services/inference_service.py](../../ai/app/services/inference_service.py) | Chọn path theo structured facts và kiểm tra invariant trước khi trả response | Chặn response mâu thuẫn như treatment không có rule hoặc review_required lại chọn rule |
| [ai/scripts/check_diagnose_contract.py](../../ai/scripts/check_diagnose_contract.py) | Thêm kiểm tra nhánh không có rule | Bảo vệ contract an toàn khỏi regression |
| [ai/Dockerfile](../../ai/Dockerfile) | Đưa scripts vào image | Cho phép container rag-ingest dùng chung image AI để nạp corpus |

### Docker và dữ liệu

| File | Thay đổi | Lý do |
|---|---|---|
| [deploy/docker-compose.yml](../../deploy/docker-compose.yml) | Thêm Ollama, ollama-init, rag-ingest và volumes model/data | Dựng được toàn bộ luồng bằng một lệnh, không phụ thuộc Ollama hoặc bước ingest đang chạy trên host |
| [deploy/docker-compose.yml](../../deploy/docker-compose.yml) | Runtime dùng ai_vector_ro, ingest dùng ai_vector_ingest | AI runtime chỉ đọc, job quản trị mới được thay corpus |
| [deploy/docker-compose.yml](../../deploy/docker-compose.yml) | Mount AI_DATA_DIR read-only và cho phép đổi POSTGRES_DATA_DIR | Giữ corpus ngoài Git/image và cho phép dùng database demo riêng mà không ghi đè dữ liệu cũ |
| [deploy/.env.example](../../deploy/.env.example) | Bổ sung URL nội bộ, model, timeout, data path và service keys mẫu | Có cấu hình tham chiếu để người khác dựng lại stack local |
| [deploy/docker-compose.yml](../../deploy/docker-compose.yml) | Fallback mặc định của `AI_SERVICE_READ_TIMEOUT_MS` cho service `backend` đổi từ 15000 lên 120000 | Compose luôn set env var này nên trước đó nó ghi đè giá trị 120000 trong `application.yaml` khi `.env` chưa khai báo, khiến timeout thật vẫn là 15 giây |

## Phương án khác và trade-off

| Vấn đề | Cách hiện tại | Phương án khác | Được và mất |
|---|---|---|---|
| Bệnh chưa có rule | Lưu diagnosis theo review_required, cho review nhưng chặn apply | Fail cả request; cho LLM sinh rule tạm; hoặc tạo RecommendationDraft riêng | Hiện tại giữ được kết quả CV/RAG và an toàn hơn LLM-generated rule, nhưng treatmentRule phải nullable và UI phải giải thích trạng thái “đã duyệt nhưng chưa được áp dụng”. Entity draft sạch domain hơn nhưng tăng schema/API/UI |
| Thực thi AI | HTTP đồng bộ, timeout 120 giây | Queue + async job + polling/WebSocket | Đồng bộ ít code, dễ demo; đổi lại giữ request lâu, khó scale và dễ timeout. Async tốt hơn cho production nhưng cần retry, idempotency, job state và UI tiến độ |
| LLM | Ollama trong Compose | Ollama host, managed API hoặc template không LLM | Compose tái lập tốt và dữ liệu không rời máy, nhưng tải model nặng và CPU chậm. Host tận dụng GPU nhưng phụ thuộc máy. API dễ scale nhưng tốn phí/cần mạng. Template ổn định nhưng kém linh hoạt |
| Nạp RAG | Job one-shot trước AI | Ingest thủ công, DB snapshot hoặc pipeline quản trị | Tự động và dễ dựng lại, nhưng startup lâu và recreate job có thể re-embed corpus. Pipeline riêng quản trị tốt hơn nhưng cần versioning và quy trình deploy data |
| Phân phối corpus | Bind-mount ai/.local/data read-only | Commit Git, đóng vào image hoặc artifact storage | Local giữ repo nhẹ và tránh đẩy data chưa chốt; đổi lại người nhận nhánh phải có data riêng. Artifact có version/checksum tốt hơn nhưng cần hạ tầng |
| Seed 38 bệnh | data.sql idempotent | Migration, importer CLI/API hoặc seeder có version | Phù hợp giai đoạn phát triển một schema, nhưng audit/rollback kém hơn pipeline production |
| Recommendation text | Lưu summary do AI trả | Backend tự dựng text hoặc lưu toàn response AI vào bảng audit | Giữ được phần giải thích CV+RAG+LLM, nhưng hiện chưa lưu evidence/generation metadata riêng. Backend tự dựng deterministic hơn nhưng mất phần diễn giải RAG |
| HTTP protocol | Ép HTTP/1.1 | Auto-negotiation hoặc reverse proxy HTTP/2 | Sửa tương thích với thay đổi nhỏ, nhưng không tận dụng multiplexing khi tải đồng thời tăng |
| Rule lookup | Backend lấy rule đầu tiên theo disease | Trả mọi rule rồi chọn theo priority, thời gian hiệu lực và bối cảnh | Đủ cho demo một rule/bệnh, nhưng chưa biểu diễn tốt nhiều sản phẩm hay rule có hiệu lực theo thời gian |

Không chọn phương án để LLM tự tạo tên thuốc, liều hoặc PHI. Cách đó phá vỡ deterministic-first và biến RAG thành nguồn sự thật nghiệp vụ không kiểm soát được.

## Phạm vi kết quả và giới hạn

Nhánh hiện đã nối được luồng thật CV → Backend facts → semantic RAG → Ollama → lưu Diagnosis → duyệt/từ chối. Hai nhánh an toàn đều được biểu diễn: có rule thì có thể áp dụng sau khi duyệt; không có rule thì giữ kết quả để chuyên gia review nhưng Backend chặn áp dụng.

Các phần chưa đạt được:

- Chưa dùng async job; request giữ kết nối trong suốt thời gian suy luận.
- Chưa có pipeline seed treatment rule có version; commit chỉ seed identity 38 lớp CV.
- Chưa lưu riêng rag_evidence, request_id, generation_mode và timings để audit/replay.
- Chưa có contract DiseaseManagementPolicy; bệnh không có rule hiện đi vào review_required.
- Backend hiện chỉ lấy tối đa một rule cho mỗi disease dù DTO có dạng danh sách.
- Chưa benchmark concurrency, cold start và yêu cầu RAM/CPU.
- API key và tài khoản mẫu chỉ dùng local, chưa có secret management production.

Ngày 21/09/2026, database Docker local đã được nạp trực tiếp rule Tomato___Early_blight → Ortiva 600SC, liều 1.50 L/ha, PHI 7 ngày, version 2026.09.10-mvp-reviewed-v1; internal API đã trả đúng rule. Dữ liệu này không nằm trong commit. Máy clone mới vẫn đi theo review_required cho tới khi Backend seed rule. Diagnosis cũ có treatment_rule_id rỗng không tự cập nhật; phải tạo báo cáo mới để test nhánh treatment.

## Kết quả kiểm tra E2E

Đã chạy trên máy local với ảnh thử và dữ liệu demo:

- FARMER tạo báo cáo được; gọi endpoint chẩn đoán bị từ chối `403`.
- ENGINEER chạy thành công CV → Backend facts → semantic RAG → Ollama. Ảnh thử được nhận diện là `Tomato___Early_blight` với confidence `0.981`; RAG trả 3 đoạn evidence từ nguồn `S03` và `S21`.
- Trong lần kiểm tra ban đầu, database chưa có treatment rule cho bệnh thử: kết quả được lưu ở trạng thái chờ duyệt, ADMIN duyệt được, còn áp dụng thuốc bị chặn 409.
- Các URL chính của frontend, Backend, AI và Keycloak trả HTTP `200`; Compose hợp lệ và các service cần thiết đều chạy.

Kết quả nhận diện phụ thuộc ảnh đầu vào và không phải cam kết chính xác cho mọi ảnh. RAG chỉ cung cấp kiến thức tham khảo; liều lượng, PHI và quyết định điều trị phải đến từ rule nghiệp vụ được Backend quản lý.
