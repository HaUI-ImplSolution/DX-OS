# Changelog

Nhật ký thay đổi của DX-Farm, theo từng module. Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/).

## Backend (Spring Boot — `backend/DX-Farm`)

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

### 2026-09-10

**Đã xong — Chuẩn hóa đầu ra CV Service khớp CV Contract & Test suite (Task A2, A3, A5):**

- **Task A2 (`disease_code`)**: Sửa `PlantDiseaseService.predict()` để trả `disease_code` = `CLASS_NAMES[predicted_class]` (ví dụ `"Tomato___Early_blight"`) thay vì `None`, khớp 1:1 với `Disease.code` trong PostgreSQL.
- **Task A3 (CV Contract alignment)**: Bổ sung từ điển tên bệnh tiếng Việt `DISEASE_NAMES_VI` và hàm helper `get_disease_name()` trong `app/cv/class_names.py` cho 38/38 lớp bệnh PlantVillage. Đổi tên trường `disease_name_vi` → `disease_name` trong Pydantic schema `DiseaseDetectionResponse` (`app/api/schemas.py`) và output của service để khớp hoàn toàn với CV Contract trong `AI_README_LangGraph_MVP.md`.
- **Task A5 (Test suite ảnh thật & API router)**: Thêm bộ test `tests/cv/test_plant_disease_service.py` kiểm thử luồng chẩn đoán với ảnh thật `tomato_leaf.jpg`, mock model và FastAPI endpoint `/disease/predict` thông qua `TestClient`. Đã chạy 8/8 unit tests thành công 100%.

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

**Chưa làm:**

- `ai/app/graph` mới là skeleton theo tài liệu; chưa implement LangGraph state/nodes/tools,
  human interrupt hoặc nối RAG vào FastAPI endpoint.
- Chưa re-ingest bộ Stage 1 vào PostgreSQL; sau khi ingest cần xác minh 38 document,
  270 chunk, retrieval và citation trên database thật.
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
