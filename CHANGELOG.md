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

- Khởi tạo project FastAPI (`ai/src`).
- Tích hợp model phân loại bệnh thực vật dựa trên PyTorch (kiến trúc `EfficientNet-B4`, hỗ trợ 38 lớp bệnh lý).
- Cung cấp API endpoint `POST /api/v1/disease/predict` nhận upload ảnh và trả về kết quả chẩn đoán (`class_id`, `class_name`, `confidence`, `model_name`, `model_version`).

**Chưa làm / cần tích hợp tiếp:**

- Vision API (`ai/src`) và data/RAG (`ai/app`) chưa được hợp nhất thành một package/runtime;
  RAG/Ollama mới chạy bằng smoke script, chưa có endpoint FastAPI.
- Chưa có LangGraph để điều phối workflow và chưa nối HTTP với Spring Boot backend.
- Vision API chưa lookup `disease_code`/`disease_name_vi`; hai trường hiện trả `null`.
- Smoke script RAG chưa phải pytest test có assertion; chưa có evaluation set, relevance
  threshold, citation metadata/URL có thể audit hoặc vector index cho dữ liệu lớn.
- `pesticides.json` và `treatment_rules.json` vẫn là placeholder rỗng, chưa có dữ liệu đã
  đối chiếu danh mục/nhãn Việt Nam.

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
