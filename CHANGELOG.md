# Changelog

Nhật ký thay đổi của DX-Farm, theo từng module. Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/).

## Backend (Spring Boot — `backend/DX-Farm`)

### 2026-09-11

**Đã xong — SSO thật qua Keycloak (Giai đoạn 2 [H]):**

- Xác thực qua OAuth2 Resource Server, verify token bằng `jwk-set-uri` trỏ vào Keycloak nội bộ.
  `KeycloakRoleConverter` map `realm_access.roles` thành `ROLE_FARMER`/`ROLE_ENGINEER`/`ROLE_ADMIN`.
- `CurrentUserService` lấy `reportedBy`/`decidedBy` từ claim token, không tra DB.
- `KeycloakDatabaseSeeder` tạo role/database Postgres riêng cho Keycloak, theo pattern `MattermostDatabaseSeeder`.
- Quản lý tài khoản qua Keycloak Admin Console.
  Chi tiết: `sso/README.md`.
- Đã chạy thật qua `deploy`, xác nhận 401/403 bằng access token thật.

### 2026-09-09

**Đã xong — Auth/SSO cơ bản (Giai đoạn 2 [H]):**

- JWT stateless (HS256, `spring-security` + `jjwt`), không session/cookie. `User` có 3 role
  `FARMER`/`ENGINEER`/`ADMIN`; seed đúng 1 `ADMIN` lúc khởi động từ biến môi trường, không có
  đăng ký công khai — chỉ `ADMIN` tạo user mới.
- `POST /api/v1/auth/login` trả JWT + role. Mọi API nghiệp vụ yêu cầu đăng nhập, trừ danh mục
  tham chiếu (đọc), `/auth/**`, swagger, `/uploads/**`.
- Điểm mấu chốt: bỏ hẳn field tên tự do (`reportedBy`/`approvedBy`/`rejectedBy`) khỏi request
  body — backend tự lấy actor từ `SecurityContextHolder`, không tin client gửi lên. Duyệt/từ
  chối/chẩn đoán giờ yêu cầu role `ENGINEER`/`ADMIN` (`@PreAuthorize`), enforce ở backend chứ
  không chỉ ẩn nút FE — cùng tinh thần poka-yoke 2 tầng như PHI.
- 401/403 trả đúng khuôn `ApiResponse` sẵn có, qua `RestAuthenticationEntryPoint`/
  `RestAccessDeniedHandler`/`GlobalExceptionHandler`.
- `User` implement thẳng `UserDetails` (thay vì bọc qua Spring's `User`) — bớt 1 query DB thừa
  mỗi lần cần biết actor đang thao tác.
- Đã build + test thật qua `deploy` (login, tạo user, full flow theo từng role, xác nhận
  401/403 qua curl và trình duyệt).

**Đã xong — M7 (Giai đoạn 1):**

- `GET /api/v1/crop-seasons/{id}/isolation-status` — trả lời "ruộng này còn cách ly không?"
  bằng cách tái dùng đúng truy vấn mà `harvest()` dùng để chặn poka-yoke (một nguồn sự thật
  duy nhất giữa "hỏi" và "chặn"). Trả `isolated`, `harvestLockedUntil`, `daysRemaining`,
  `pesticideCode` + câu trả lời tiếng Việt diễn giải facts — không qua LLM.

### 2026-09-08

**Đã xong — Phase 0 spine flow (M1–M6), đủ để demo end-to-end:**

- Khởi tạo project Spring Boot (Java 21, PostgreSQL, Docker/docker-compose), khung dùng chung:
  `ApiResponse<T>` (envelope response thống nhất), `GlobalExceptionHandler` (400/404/409/422/500),
  Swagger/OpenAPI tự sinh từ code.
- **Structured Knowledge** (danh mục, đọc qua API, seed sẵn qua `data.sql`): `Disease`,
  `Pesticide`, `TreatmentRule` (liều lượng + số ngày cách ly PHI).
- **Operational Data**: `Parcel` (thửa ruộng), `CropSeason` (vụ mùa), `DiseaseReport` (báo cáo
  sự cố kèm ảnh, upload multipart lưu local disk), `Diagnosis` (chẩn đoán + khuyến nghị + trạng
  thái duyệt), `TreatmentApplication` (bản ghi đã phun thuốc, snapshot liều/PHI).
- Luồng nghiệp vụ đầy đủ: báo bệnh có ảnh → tra `Disease`→`TreatmentRule` deterministic (không
  qua LLM) → tự sinh khuyến nghị tiếng Việt → kỹ sư Approve/Reject (có state machine chặn
  duyệt/áp dụng sai thứ tự) → ghi nhận đã phun thuốc, tự tính ngày hết cách ly → **poka-yoke**:
  gọi thu hoạch khi còn cách ly bị chặn cứng, trả `HTTP 422` kèm mã lỗi
  `POKA_YOKE_VIETGAP_VIOLATION`.

**Chưa làm** (ngoài phạm vi backend hoặc chưa cần ở Phase 0):

- Vision API và RAG prototype đã có trên nhánh `feature/make-data`, nhưng backend chưa có HTTP
  client gọi AI Service; `Diagnosis` vẫn nhận sẵn `disease_id/confidence/model_version`.
- Chưa có Carbon/AWD Estimator, IoT simulator, BI dashboard (Superset/Metabase), SSO nặng
  (Keycloak/Authentik) hay kênh chat nội bộ (Mattermost) — thuộc Giai đoạn 1–3 theo roadmap
  trong `README.md`. Auth/role cơ bản (JWT, FARMER/ENGINEER/ADMIN) đã xong ở trên.

## AI Service (Python)

### 2026-09-11

**Đã xong — Stage 8 LangGraph spine flow:**

- Hoàn thiện `FarmState`, 9 node và tool adapter; graph chạy từ request → CV → field context
  → structured lookup → RAG → recommendation → checkpoint `PENDING`.
- `disease_code` được kiểm tra đúng contract 38 lớp và propagate nguyên vẹn. Diagnosis lưu
  confidence/model metadata; crop season được kiểm tra thuộc đúng parcel.
- Structured facts luôn lấy từ SQL: treatment dùng đúng rule; policy-only không phát sinh
  thuốc; trường hợp chưa có rule/policy đi `review_required`. RAG lỗi vẫn giữ được structured
  recommendation và ghi lỗi `RAG_UNAVAILABLE`.
- Lớp Ollama chỉ được chọn đoạn evidence nguyên văn. Nội dung không grounded hoặc
  lỗi/timeout sẽ về `evidence_fallback`, không được sửa thuốc, hoạt chất, liều hoặc PHI.
- `/api/v1/disease/workflow/run` gọi graph thật bằng `.invoke()`, có request idempotency,
  giới hạn ảnh, log disease/model/path/timing và lỗi API không lộ traceback/path nội bộ.

**Đã xong — Stage 9 durable HITL / APPLIED / PHI:**

- Thêm `WorkflowCase` và append-only `WorkflowEvent`; trạng thái/audit nằm trong PostgreSQL,
  không phụ thuộc state LangGraph trong bộ nhớ.
- Thêm API lấy trạng thái, approve, reject và applied. Approve/reject chỉ do actor truyền rõ;
  low-confidence cần xác nhận rõ ràng; reject bắt buộc lưu reason.
- Giữ bất biến `APPROVED != APPLIED`: approve chỉ chuyển sang `WAITING_APPLIED`, chưa tạo
  `TreatmentApplication` và chưa bắt đầu PHI.
- APPLIED kiểm tra rule đúng diagnosis và actual dose hữu hạn/nằm trong khoảng; sai dữ liệu
  rollback. Bản ghi lưu snapshot tên thuốc, hoạt chất, dose/unit, PHI, rule version/source.
- PHI được tính đúng `applied_at + phi_days`; truy vấn isolation dùng `MAX` lock nên lần phun
  chồng lấn không thể vô tình rút ngắn thời gian cách ly. Retry cùng payload idempotent, payload
  khác trả conflict.
- Alembic thêm `e8f9a1026011` và head `f0a1b2026011`; đã kiểm tra upgrade/check trên DB hiện
  tại và trên một PostgreSQL tạm hoàn toàn mới.

**Đã xong — Stage 10 demo hardening trong phạm vi `ai/`:**

- Thêm fixture demo idempotent, E2E treatment/policy/reject/invalid-dose/low-confidence,
  smoke test khởi động FastAPI với checkpoint CV thật, kiểm tra DB counts, structured lookup,
  pgvector provenance, graph PENDING và safe LLM fallback.
- Thêm timing CV/RAG/LLM/workflow, runbook migration → seed → ingest → API → smoke → test tại
  `ai/README_MVP_RUNBOOK.md` và biến môi trường cho confidence threshold/LLM timeout.
- Đã xác minh local: **59 passed** với checkpoint thật và PostgreSQL; smoke MVP PASS. Ảnh smoke
  hiện được model nhận là `Tomato___Early_blight`; kịch bản Late blight deterministic được
  kiểm tra tại biên contract bằng fake CV, không khai khống ảnh mẫu Late blight thật.
- Timing smoke tham chiếu trên máy local: CV HTTP 216.20 ms; graph 5160.80 ms, trong đó
  Ollama không sẵn sàng chờ timeout khoảng 5 giây rồi fallback an toàn.
- Giới hạn còn lại: AI transition API chưa có auth riêng; backend/frontend chưa nối HTTP với
  workflow AI; checkpoint chưa có `class_to_idx`/accuracy độc lập; chưa benchmark live Ollama.

### 2026-09-10


**Đã xong — Chuẩn hóa đầu ra CV Service khớp CV Contract & Test suite (Task A2, A3, A5):**

- **Task A2 (`disease_code`)**: Sửa `PlantDiseaseService.predict()` để trả `disease_code` = `CLASS_NAMES[predicted_class]` (ví dụ `"Tomato___Early_blight"`) thay vì `None`, khớp 1:1 với `Disease.code` trong PostgreSQL.
- **Task A3 (CV Contract alignment)**: Bổ sung từ điển tên bệnh tiếng Việt `DISEASE_NAMES_VI` và hàm helper `get_disease_name()` trong `app/cv/class_names.py` cho 38/38 lớp bệnh PlantVillage. Đổi tên trường `disease_name_vi` → `disease_name` trong Pydantic schema `DiseaseDetectionResponse` (`app/api/schemas.py`) và output của service để khớp hoàn toàn với CV Contract trong `AI_README_LangGraph_MVP.md`.
- **Task A5 (Test suite ảnh thật & API router)**: Thêm bộ test `tests/cv/test_plant_disease_service.py` kiểm thử luồng chẩn đoán với ảnh thật `tomato_leaf.jpg`, mock model và FastAPI endpoint `/disease/predict` thông qua `TestClient`. Đã chạy 8/8 unit tests thành công 100%.

- **Partial CV handoff implementation**: Updated response schema, health endpoint, device fallback, inference timing, and generated `class_contract.json`. Remaining tasks: implement structured JSON error responses (`CVErrorResponse`), create `example_response.json`, export OpenAPI (`cv_openapi.json`), write `app/cv/README.md`, add smoke test script, and add unit tests for health endpoint, error contracts, and class contract.
**Đã xong — chuẩn hóa cấu trúc module AI theo tài liệu LangGraph MVP:**

- Tổ chức thống nhất code trong `ai/app`: tách rõ `api`, `core`, `cv`, `db`, `rag`,
  `services` và khung `graph`; chuyển CLI seed/ingest sang `ai/scripts`, test sang
  `ai/tests/cv` và `ai/tests/rag`.
- Hoàn thiện cấu hình tập trung bằng `pydantic-settings`, thêm `ai/.env.example`, tách
  FastAPI lifespan khỏi `main.py` và cố định đường dẫn model tại `ai/weights`.
- Giảm coupling package CV: import class mapping không còn tự kéo theo `torchvision` hoặc
  model loader; FastAPI có thể import mà chưa tải weights, model chỉ được load trong lifespan.
- Bổ sung `get_db()` cho FastAPI dependency; bỏ `app/db/init_db.py` dùng `create_all()` để
  Alembic tiếp tục là luồng quản lý schema được hướng dẫn chính thức.
- Cập nhật tài liệu PostgreSQL theo entrypoint mới: `python -m scripts.seed_diseases` và
  `python -m scripts.ingest_diseases`.
- Đã xác minh: 8 test CV đạt, RAG dry-run đọc đủ 38 tài liệu, Alembic head vẫn là
  `4c2a0f9d8b71`; compile/lint phạm vi refactor đạt.

**Đã xong — chuẩn bị dữ liệu RAG Stage 1 và provenance:**

- Đặt đúng tầng ba artifact từ workbook: 38 bản ghi tại
  `data/rag/processed/disease_knowledge.json`, 26 nguồn tại
  `data/rag/source_registry.json` và 26 candidate tại
  `data/structured/treatment_rule_candidates.json`.
- Validate mã disease/source không trùng và mọi provenance reference đều resolve được;
  prompt/retrieval/response duy trì `source_id` và trả metadata citation có URL.
- Chia knowledge ưu tiên ranh giới dòng và tách theo từ khi cần, tối đa 350 ký tự/chunk để phù hợp giới hạn
  embedding hiện tại; dry-run dự kiến 38 document/270 chunk.
- Giữ guardrail structured knowledge: cả 26 candidate đều
  `REQUIRES_VN_LABEL_VALIDATION` và `eligible_for_seed = false`; chuyển
  `pesticides.json`/`treatment_rules.json` từ file 0 byte thành mảng JSON rỗng hợp lệ.
- Bổ sung pytest cho số lượng dataset, uniqueness, source reference, candidate guardrail,
  chunking và citation.

**Đã xong — Structured Knowledge Stage 4/5 theo gói dữ liệu Stage 3 đã review:**

- Đối chiếu workbook độc lập với workbook trong gói Stage 3 (cùng SHA-256), kiểm tra toàn
  bộ checksum của gói và lưu bản review/manifest/HOLD trong `ai/data/stage3` để audit.
- Promote đúng 8 candidate `APPROVE/READY_TO_PROMOTE` thành bộ cuối: 4 pesticide, 8
  treatment rule và 21 disease-management policy; giữ riêng 8 candidate
  `HOLD/NOT_READY`, tuyệt đối không đưa vào seeder.
- Stage 4 thêm migration `c1f7e2d4a9b0`: đổi `TreatmentRule.dose` thành khoảng
  `dose_min`/`dose_max`, thêm check constraint và bảng `disease_management_policies`.
  `TreatmentApplication.dose` vẫn là liều thực tế đã áp dụng.
- Repository ghi nhận treatment bắt buộc nhận `actual_dose` và từ chối giá trị ngoài
  khoảng rule; bổ sung lookup policy theo disease code.
- Stage 5 thêm ba seeder idempotent chỉ đọc file final. Đã chạy hai lần trên PostgreSQL local:
  revision `c1f7e2d4a9b0`, đúng 4 pesticide, 8 rule, 21 policy và không nhân đôi dữ liệu.
- Kiểm tra dữ liệu/guardrail/unit đạt; integration test PostgreSQL xác minh lookup khoảng
  liều, PHI, policy an toàn và tự chạy seeder hai lần để chặn duplicate.
- Registry provenance hợp nhất hiện có 41 nguồn. Không chạy embedding hoặc re-ingest RAG
  trong Stage 4/5 này. Kết quả review là đánh giá kỹ thuật MVP, không phải field-use
  authorization.

**Đã triển khai — Stage 6 CV ↔ Data và Stage 7 RAG:**

- CV handoff gồm contract 38 lớp, adapter common rust giữ nguyên nguồn, checkpoint/config
  validation, predict/health/class-contract API và xử lý ảnh lỗi.
- Endpoint chẩn đoán report lưu Diagnosis + trạng thái atomically, snapshot confidence/model
  metadata và trả SQL rule/safe policy. Không tự áp dụng thuốc; không sửa backend/frontend.
- Migration `d6e7a1202601` thêm diagnoses.model_name và documents.disease_code/index.
- RAG có exact disease filter, top_k, score/provenance, cache model, atomic replace và
  advisory lock. Đã ingest thật hai lần: 38 document / 270 chunk, version stage7-rich-v1.
- RAG answer chỉ trả evidence (grounded_excerpt), loại draft bịa facts và fallback khi
  Ollama không khả dụng. Facts thuốc/liều/PHI/policy được đọc từ SQL riêng.
- Full suite **40 passed** với checkpoint thật qua FastAPI lifespan và PostgreSQL.
  Có test diagnosis, policy, retrieval/citation, idempotency và rollback.
- Thêm CV/RAG handoff README, cập nhật checklist. Chưa kiểm thử venv trắng, accuracy CV,
  live Ollama; checkpoint thiếu class_to_idx, thứ tự được đối chiếu mã training trong repo.
- Tại snapshot Stage 6–7 này, Stage 8 chưa làm và workflow endpoint còn trả 501; trạng thái
  mới nhất đã được cập nhật trong mục Stage 8–10 ngày 2026-09-11 ở trên.

**Chưa làm:**

- Phần skeleton/human interrupt trong ghi chú Stage 6–7 đã được hoàn thiện ở Stage 8–10;
  backend ↔ AI HTTP integration vẫn chưa triển khai.
- 8 treatment-rule candidate trạng thái HOLD và các candidate staging còn lại chưa phải
  dữ liệu cuối, không được seed cho đến khi có quyết định review mới.

### 2026-09-09

**Đã xong trên `feature/make-data` — nền tảng dữ liệu và RAG prototype chạy local:**

- Khởi tạo SQLAlchemy data layer cho 8 entity spine-flow và 2 entity RAG (`Document`,
  `DocumentChunk`), cùng repository SQL và `get_field_context()`.
- Tạo 38 bản ghi Disease từ sheet `Disease_KB`: `class_name → code`,
  `disease_name → name`, `symptoms → description`; giữ nguyên nguồn và không trùng mã.
- Thêm migration `4c2a0f9d8b71`: bật pgvector, tạo `documents`, `document_chunks`,
  khóa ngoại cascade, index theo `document_id` và embedding `vector(384)`.
- Hoàn thiện batch embedding, ingest idempotent, cosine retrieval, prompt tiếng Việt có
  guardrail và gọi Qwen2.5-1.5B qua Ollama; có `--dry-run` để preview không ghi DB.

**Đã xác minh phần data/RAG:**

- Database đã ở revision `4c2a0f9d8b71`, pgvector `0.8.6`; có đúng 38 document, 38 chunk,
  tất cả embedding 384 chiều.
- Chạy ingestion hai lần không nhân đôi dữ liệu.
- Retrieval câu hỏi ghẻ táo trả `Apple___Apple_scab` Top 1 (similarity khoảng `0.736`);
  RAG end-to-end qua Ollama trả lời đúng context, không thêm dữ liệu thuốc.

**Đã xong — Vision API (Computer Vision) cơ bản:**

- Khởi tạo project FastAPI (đã được chuyển từ `ai/src` vào `ai/app`).
- Tích hợp model phân loại bệnh thực vật dựa trên PyTorch (kiến trúc `EfficientNet-B4`, hỗ trợ 38 lớp bệnh lý).
- Cung cấp API endpoint `POST /api/v1/disease/predict` nhận upload ảnh và trả về kết quả chẩn đoán (`class_id`, `class_name`, `confidence`, `model_name`, `model_version`).

**Chưa làm / cần tích hợp tiếp:**

- Vision API và data/RAG đã nằm chung trong `ai/app`, nhưng RAG/Ollama mới chạy bằng smoke
  script và chưa được nối vào endpoint FastAPI/runtime chính.
- Chưa có LangGraph để điều phối workflow và chưa nối HTTP với Spring Boot backend.
- Vision API đã hoàn thành lookup `disease_code` và `disease_name` tiếng Việt (Task A2, A3).
- Smoke script RAG chưa phải pytest test có assertion; chưa có evaluation set, relevance
  threshold, citation metadata/URL có thể audit hoặc vector index cho dữ liệu lớn.
- Dòng trạng thái ngày 2026-09-09 này đã được Stage 3 ngày 2026-09-10 thay thế cho tập con
  được review; các candidate HOLD vẫn không được sử dụng.

## Frontend (`frontend`)

### 2026-09-11

**Đã xong — Đăng nhập qua Keycloak (SSO thật):**

- `src/auth.ts` dùng `oidc-client-ts` (Authorization Code + PKCE), phiên lưu ở `sessionStorage`.
- Chưa có phiên thì tự redirect sang Keycloak ngay lúc mount, không cần bấm nút — vào thẳng từ portal cũng tự vào được app.
  Callback lỗi (huỷ đăng nhập, tài khoản bị khoá...) thì dừng lại báo lỗi kèm nút thử lại, không lặp vô hạn.
  Đọc `fullName`/`role` từ claim token sau khi quay về.
- `src/api.ts` gắn Bearer token từ phiên OIDC; 401 tự xoá phiên, quay lại màn đăng nhập.
- Single logout: bật `monitorSession` để tự phát hiện khi phiên Keycloak kết thúc ở app khác (Superset) hoặc Keycloak Account Console, tự đăng xuất theo. Chi tiết: `sso/README.md`.
- Đã test qua trình duyệt thật với từng tài khoản demo trong `sso/README.md`, bao gồm bấm thẳng từ portal và đăng xuất chéo giữa App chính/Superset.

### 2026-09-09

**Đã xong — Demo UI cho Phase 0 spine flow (M1–M6):**

- React 18 + TypeScript + Vite, một trang duy nhất theo đúng spine flow: chọn/tạo thửa ruộng →
  vụ mùa → báo sâu bệnh kèm ảnh → chẩn đoán (nhập tay giả lập AI Service) → kỹ sư duyệt/từ chối
  → ghi nhận đã phun thuốc → khai báo thu hoạch (banner rõ khi bị poka-yoke chặn, HTTP 422).
- Tab "Danh mục tham chiếu" (read-only Disease/Pesticide/TreatmentRule) và nút hỏi "ruộng còn
  cách ly không? (M7)" ở panel Vụ mùa, gọi `GET /crop-seasons/{id}/isolation-status`.
- `src/api.ts` bọc `ApiResponse<T>` envelope, ném `ApiError` để UI phân biệt lỗi 400/409/422.
- Đã chạy full flow qua trình duyệt thật trên backend thật, không chỉ build/typecheck.

**Thay đổi kèm theo ở backend để FE chạy được:** thêm `WebConfig` (CORS cho `/api/**`, static
handler `/uploads/**`).

**Chưa làm:** low-code frontend (ToolJet/Appsmith) — đây vẫn là bản tự viết tối giản cho demo.

**Đã xong — Đăng nhập + role, thay input tên tự do bằng danh tính đã xác thực:**

- `LoginPanel` mới gate toàn app; header hiện tên/vai trò + nút đăng xuất. `src/auth.ts` lưu
  `{token, username, fullName, role}` ở `localStorage`.
- `src/api.ts` tự gắn Bearer token; gặp 401 thì tự xoá phiên, quay lại màn đăng nhập.
- Bỏ input "Người báo cáo"/"Tên kỹ sư" — backend tự lấy từ user đã đăng nhập. Nút duyệt/từ
  chối/chẩn đoán chỉ hiện với role `ENGINEER`/`ADMIN` (ẩn FE là UX, chặn thật ở backend).
- Đã test qua trình duyệt thật với từng role, xác nhận UI và 401/403 đúng chỗ.

## Deploy (`deploy`)

### 2026-09-11

**Đã xong — service `keycloak` (SSO thật):**

- Thêm service `keycloak` (image chính thức `quay.io/keycloak/keycloak:26.0`), cổng mặc định `8180`.
  Tự import realm `dxfarm` từ `sso/realm-dxfarm.json` lúc khởi động, không cần thao tác tay.
- `.env.example`: `KEYCLOAK_ADMIN_PASSWORD`/`KEYCLOAK_DB_PASSWORD` thay cho secret JWT/admin cũ.
- Đã chạy thật bằng `docker compose up -d --build`.

### 2026-09-09

**Đã xong — đóng gói Postgres + backend + frontend chạy một lệnh:**

- `deploy/docker-compose.yml`: build backend (`backend/DX-Farm/Dockerfile`) + frontend
  (`frontend/Dockerfile` mới, multi-stage `npm run build` → serve bằng nginx). Expose
  `${POSTGRES_PORT:-5432}`, `${BACKEND_PORT:-8081}`, `${FRONTEND_PORT:-8082}`.
- Frontend production gọi API cùng-origin — nginx reverse-proxy `/api/` và `/uploads/` sang
  backend, nên deploy không cần CORS.
- Postgres + ảnh upload persist qua bind mount `deploy/data/` (không mất khi `docker compose
  down`).
- Đã build + chạy thử thật bằng `docker compose up -d --build`.
- Xoá `backend/DX-Farm/docker-compose.yml` cũ — `deploy/docker-compose.yml` giờ là compose
  file duy nhất trong repo (dùng chung cho demo full-stack lẫn Postgres khi dev thủ công).
- Service `backend` nhận thêm secret cho auth (`JWT_SECRET`, `JWT_EXPIRATION`,
  `SUPER_ADMIN_USERNAME/PASSWORD`) từ `deploy/.env` (mẫu ở `.env.example`).

## BI Dashboard (`bi`, Apache Superset)

### 2026-09-11

**Đã xong — SSO thật qua Keycloak:**

- `bi/superset_config.py` bật `AUTH_TYPE = AUTH_OAUTH`, đăng nhập UI bằng redirect sang Keycloak.
  `CustomSecurityManager` map claim `realm_access.roles` (đọc trực tiếp từ access token) sang role Superset: `ADMIN` → `Admin`, `ENGINEER`/`FARMER` → `Gamma`.
- Client confidential `dxfarm-superset` mới trong `sso/realm-dxfarm.json`.
- `bi/bootstrap.sh` tự `pip install authlib` lúc khởi động (không có sẵn trong image gốc, không build lại image).
- Tài khoản admin DB cục bộ vẫn dùng được qua REST API cho `bootstrap_datasources.py`, không phụ thuộc `AUTH_TYPE`.
- Single logout: `LOGOUT_REDIRECT_URL` khiến đăng xuất Superset cũng kết thúc phiên SSO ở Keycloak, không chỉ xoá cookie Superset. Client `dxfarm-superset` bật `frontchannelLogout` để nhận thông báo khi phiên SSO kết thúc từ nơi khác. Chi tiết: `sso/README.md`.
- Đã đăng nhập thật qua trình duyệt bằng `engineer1`/`admin1`, xác nhận role map đúng và đăng xuất chéo với App chính hoạt động đúng cả 2 chiều.

### 2026-09-10

**Đã xong — BI dashboard (Giai đoạn 2 [D]):**

- Thêm service `superset` (image chính thức `apache/superset:3.1.3`, không build lại) vào `deploy/docker-compose.yml`, lên cùng lệnh `docker compose up -d --build` sẵn có, cổng 8088.
  Bind mount script/dashboard từ thư mục `bi/` riêng.
- Backend tự tạo role Postgres chỉ-đọc `superset_ro` lúc khởi động (`SupersetReadOnlyRoleSeeder`, JDBC thuần — `data.sql` không tương thích với khối PL/pgSQL `DO $$ ... $$`).
- `bi/bootstrap.sh` + `bootstrap_datasources.py`: tự `db upgrade` + tạo admin + đăng ký database connection + 5 dataset + import dashboard mẫu qua REST API công khai của chính Superset.
  Không cần thao tác tay sau `docker compose up`. Idempotent, có retry.
- Dashboard mẫu "DX-Farm — Bản đồ & Trạng thái thửa ruộng": deck.gl Scatterplot vẽ thửa ruộng theo toạ độ + Pie chart tỉ lệ vụ mùa ACTIVE/HARVESTED.
  Build bằng browser thật (Claude in Chrome), verify bằng screenshot dữ liệu thật, export lại thành bundle YAML commit vào `bi/dxfarm_dashboard_export.zip` để tự import lại mỗi lần dựng từ đầu.
- `Parcel` có thêm 2 cột toạ độ tuỳ chọn `latitude`/`longitude` (backend + FE `ParcelPanel`) để dashboard vẽ bản đồ thật.
- Đã build + chạy thử thật, kể cả re-import dashboard nhiều lần — idempotent, không tạo trùng chart.
- **Còn thiếu**: chưa cấu hình `MAPBOX_API_KEY` nên bản đồ chưa có nền (basemap) thật, chỉ thấy điểm trên nền trắng — cần token Mapbox miễn phí, xem `bi/README.md`.

## Kênh chat nội bộ (`chat`, Mattermost)

### 2026-09-11

**Quyết định — không nối SSO cho Mattermost:**

- Team Edition (bản free đang dùng) không hỗ trợ OpenID Connect với IdP tuỳ ý — tính năng đó thuộc Mattermost Enterprise (trả phí).
- Có thể lách bằng provider "GitLab" (generic OAuth2 built-in) trỏ sang Keycloak, nhưng cần custom protocol mapper giả dạng shape JSON của GitLab user và dựa vào hành vi nội bộ không được Mattermost chính thức hỗ trợ cho IdP ngoài GitLab — quyết định không làm.
- App chính và BI Dashboard đã đăng nhập qua Keycloak, xem `sso/README.md`.

### 2026-09-10

**Đã xong — Kênh chat nội bộ (Giai đoạn 2 [H]):**

- Thêm service `mattermost` (image chính thức `mattermost/mattermost-team-edition:10.5`, không build lại) vào `deploy/docker-compose.yml`, lên cùng lệnh `docker compose up -d --build` sẵn có, cổng 8065.
- Backend tự tạo role + database Postgres riêng `mattermost_app`/`mattermost` lúc khởi động (`MattermostDatabaseSeeder`, JDBC thuần vì `CREATE DATABASE` không chạy được trong khối PL/pgSQL `DO $$ ... $$`) — tách biệt hoàn toàn khỏi CSDL nghiệp vụ.
- Dữ liệu chat persist qua bind mount `deploy/data/mattermost/`.
- Rà soát license kỹ vì Mattermost không dùng một license duy nhất: server core AGPLv3 (OSI-approved), `server/enterprise/` là Mattermost Source Available License (không OSI-approved, không dùng tới), bản binary Team Edition được Mattermost, Inc. cấp phép lại MIT riêng cho đúng binary đó. Ghi chi tiết ở `LICENSES.md` và `chat/README.md`.
- Tài khoản Mattermost tách biệt hoàn toàn khỏi tài khoản backend (FARMER/ENGINEER/ADMIN) có chủ đích — nền tảng riêng, không dùng SSO chung.

## Portal (`portal`)

### 2026-09-11

**Đã xong — bấm link từ portal vào thẳng app đã đăng nhập SSO:**

- Link BI Dashboard trỏ thẳng `/login/keycloak` thay vì trang chủ Superset — vào thẳng luồng OAuth, không dừng ở màn hình "Sign In with keycloak".
- Kết hợp với frontend tự redirect khi chưa có phiên: bấm app nào từ portal cũng tự chuyển sang Keycloak nếu chưa đăng nhập, xong quay lại đúng app đó; đã đăng nhập app này rồi thì bấm app kia (App chính/BI Dashboard) không cần đăng nhập lại.
- Đã test qua trình duyệt thật: bấm portal → chưa có phiên → Keycloak → quay lại đúng app; bấm app còn lại → vào thẳng không cần đăng nhập lại.

### 2026-09-10

**Đã xong — trang portal tĩnh, bước đệm trước SSO:**

- Trang tĩnh (nginx, không build step) liệt kê link tới 3 ứng dụng hiện có (Ứng dụng chính,
  BI Dashboard, Kênh chat nội bộ) — người dùng không cần tự nhớ URL từng app.
- Thêm service `portal` vào `deploy/docker-compose.yml`, cổng mặc định `8090` (`PORTAL_PORT`).
- Đã build + chạy thử thật qua `docker compose up -d`, xác nhận trang load đúng.
- **Còn thiếu**: mỗi app đích vẫn đăng nhập riêng — portal chỉ là danh sách link tĩnh, sẽ trở
  thành landing page thật khi SSO (Keycloak/Authentik) được triển khai.

## Identity Provider (`sso`, Keycloak)

### 2026-09-11

**Đã xong — SSO thật (Giai đoạn 2 [H]):**

- Realm `dxfarm` (`sso/realm-dxfarm.json`): 3 realm role, client public `dxfarm-frontend` (PKCE), client confidential `dxfarm-superset`, 3 tài khoản demo.
- Backend, frontend và BI Dashboard (Superset) đều xác thực qua Keycloak.
  Chi tiết + trade-off hostname nội bộ vs. trình duyệt: `sso/README.md`.
- Single logout (front-channel + session monitoring): đăng xuất ở app nào cũng kết thúc phiên SSO ở Keycloak, các app khác đang mở tự đăng xuất theo. Giới hạn: không phải backchannel logout chuẩn OIDC (chỉ hoạt động khi tab còn mở). Chi tiết: `sso/README.md`.
- Mattermost là nền tảng chat tách biệt có chủ đích, không nối SSO — xem `chat/README.md`.

## Rà soát license (PoF)

### 2026-09-11

**Đã xong:**

- Thêm Keycloak (Apache-2.0) và `oidc-client-ts` (MIT) vào `LICENSES.md`, cả hai OSI-approved.
- Thêm Authlib (BSD-3-Clause, OSI-approved) vào `LICENSES.md` — pip package cho SSO OAuth của Superset.

### 2026-09-10

**Đã xong:**

- `LICENSES.md` (gốc repo): rà soát license từng thành phần thật sự đang chạy (backend, frontend, deploy).
  Toàn bộ OSI-approved (Apache-2.0/MIT/BSD/PostgreSQL License).
- Chốt dứt khoát bỏ n8n khỏi kiến trúc (Sustainable Use License, không OSI-approved) — chưa từng tích hợp vào code.
- Thêm `LICENSE` (Apache-2.0 toàn văn) ở gốc repo.
- **Còn thiếu**: SPDX header đầu mỗi file mã nguồn tự viết — việc sửa hàng loạt file, để riêng một lượt khác.
