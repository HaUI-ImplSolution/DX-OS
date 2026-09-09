# Changelog

Nhật ký thay đổi của DX-Farm, theo từng module. Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/).

## Backend (Spring Boot — `backend/DX-Farm`)

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

- Chưa gọi AI Service Python thật (vision-api CV + LLM) — `Diagnosis` nhận sẵn
  `disease_id/confidence/model_version` qua API như thể AI Service gửi vào, nhưng chưa có HTTP
  client nối sang AI Service vì service đó chưa tồn tại trong repo.
- Chưa có auth/SSO — tên nông dân/kỹ sư hiện là chuỗi tự do, chưa có `User`/`Farmer` entity.
- Chưa có RAG, Carbon/AWD Estimator, IoT simulator, BI dashboard (Superset/Metabase) — thuộc
  Giai đoạn 1–3 theo roadmap trong `README.md`.

## AI Service (Python)

### 2026-09-09

**Đã xong trên `feature/make-data` — nền tảng dữ liệu cho AI workflow (chưa phải AI Service chạy được):**

- Khởi tạo Python data layer dùng SQLAlchemy 2.x và PostgreSQL, gồm `Base`, `engine`,
  `SessionLocal` và kết nối qua `DATABASE_URL`.
- Khai báo 8 model Operational Data / Structured Knowledge phục vụ spine flow: `Parcel`,
  `CropSeason`, `DiseaseReport`, `Diagnosis`, `Disease`, `Pesticide`, `TreatmentRule`
  và `TreatmentApplication`; đã cấu hình UUID, khóa ngoại, index và relationship.
- Bổ sung model nền cho RAG Knowledge: `Document` và `DocumentChunk`; chunk lưu nội dung,
  số trang và embedding `vector(768)` qua pgvector.
- Bổ sung repository cho các thao tác đã có code:
  - tải thửa ruộng và vụ mùa đang hoạt động;
  - tạo báo cáo bệnh, lưu kết quả CV theo `disease_code`, lấy chẩn đoán mới nhất;
  - lookup `Disease → TreatmentRule → Pesticide` bằng SQL deterministic;
  - ghi `TreatmentApplication`, snapshot liều/PHI, tính `harvest_locked_until` từ thời điểm
    `APPLIED`, đọc lịch sử phun và kiểm tra còn cách ly hay không.
- Bổ sung `get_field_context()` để gom Parcel, CropSeason, chẩn đoán mới nhất, lần phun gần
  nhất và trạng thái PHI thành `dict` làm đầu vào cho LangGraph sau này.
- Thiết lập Alembic và tạo initial revision `9700136d7e00` cho 8 bảng spine-flow. Revision đã
  được thử `upgrade → downgrade → upgrade` thành công trên PostgreSQL tạm; database phát
  triển được baseline bằng `alembic stamp head` sau khi đối chiếu schema.
- Đổi PostgreSQL dùng chung trong `deploy/docker-compose.yml` sang
  `pgvector/pgvector:pg16`; cấu hình Compose sau merge với `main` đã qua
  `docker compose config`.
- Mở rộng `.gitignore` cho virtualenv/cache, secrets, model artifacts và build/runtime output;
  migration, seed source và `.env.example` vẫn được phép commit.

**Đã xác minh:**

- Python source compile được; SQLAlchemy đăng ký và configure mapper thành công cho 10
  model/table.
- Initial migration chạy được cả hai chiều cho 8 bảng mà revision đang quản lý.

**Chưa làm / cần xử lý tiếp:**

- `ai/main.py` vẫn rỗng; chưa có FastAPI endpoint, vision-api/MobileNetV3, LangGraph state và
  node, Qwen/Ollama, RAG ingestion/retriever hoặc contract HTTP với Spring Boot backend.
- Ba file seed JSON mới là placeholder rỗng; chưa có dữ liệu Disease/Pesticide/TreatmentRule
  để chạy lookup thật.
- `Document` và `DocumentChunk` được thêm sau initial revision nên chưa có migration kế
  tiếp; `alembic check` hiện phát hiện hai bảng và index tương ứng chưa được quản lý.
- Chưa có test cho model/repository/service; Ruff hiện vẫn báo lỗi chuẩn hóa import và
  re-export.
- Repository Python hiện có cả thao tác ghi Operational Data. Trước khi nối end-to-end cần
  chốt lại ranh giới Single Source of Truth trong tài liệu: backend Spring Boot sở hữu ghi
  nghiệp vụ, AI Service nên gọi backend qua HTTP thay vì tự ghi song song.

## Frontend (`frontend`)

### 2026-09-09

**Đã xong — Demo UI cho Phase 0 spine flow (M1–M6):**

- React 18 + TypeScript + Vite, không dùng router/state library ngoài — một trang duy nhất
  theo đúng thứ tự spine flow: chọn/tạo thửa ruộng → chọn/tạo vụ mùa → gửi báo cáo sâu bệnh
  kèm ảnh → chẩn đoán (nhập tay giả lập AI Service) → kỹ sư duyệt/từ chối → ghi nhận đã phun
  thuốc → khai báo thu hoạch (thấy rõ banner khi bị poka-yoke chặn, HTTP 422).
- Tab phụ "Danh mục tham chiếu" hiển thị read-only Disease/Pesticide/TreatmentRule (Structured
  Knowledge).
- `src/api.ts` bọc toàn bộ `ApiResponse<T>` envelope của backend, ném `ApiError` (status + data)
  để UI phân biệt lỗi validate (400), state machine (409) và poka-yoke (422).
- Đã chạy thử full flow qua trình duyệt thật (parcel → crop season → upload ảnh → diagnosis →
  approve → apply → harvest bị khoá đúng theo PHI) trên backend thật, không chỉ build/typecheck.

**Thay đổi kèm theo ở backend để FE chạy được:**

- Thêm `WebConfig` (CORS cho `/api/**` từ localhost, static resource handler `/uploads/**` trỏ
  vào `app.upload-dir`) vì trước đó chưa có cấu hình CORS và ảnh upload chưa được serve qua HTTP.

**Chưa làm:**

- Chưa có auth/SSO nên tên nông dân/kỹ sư vẫn là input tự do (khớp với hiện trạng backend).
- Chưa có low-code frontend (ToolJet/Appsmith) theo Giai đoạn 1 — đây vẫn là bản tự viết tối
  giản cho demo Phase 0.

## Deploy (`deploy`)

### 2026-09-09

**Đã xong — đóng gói Postgres + backend + frontend chạy một lệnh:**

- `deploy/docker-compose.yml`: build backend từ `backend/DX-Farm/Dockerfile` (đã có sẵn),
  build frontend qua `frontend/Dockerfile` mới (multi-stage: `npm run build` rồi serve bằng
  nginx). Expose ra host: `${POSTGRES_PORT:-5432}` (để còn chạy `mvn spring-boot:run` local
  khi cần hot reload), `${BACKEND_PORT:-8081}`, `${FRONTEND_PORT:-8082}`.
- Frontend production build gọi API cùng-origin (`frontend/.env.production` để trống
  `VITE_API_BASE_URL`) — nginx (`frontend/nginx.conf`) reverse-proxy `/api/` và `/uploads/`
  sang container backend, nên môi trường deploy không cần CORS.
- Dữ liệu Postgres và ảnh upload persist qua bind mount `deploy/data/` (đã có sẵn trong
  `.gitignore` từ trước, không mất khi `docker compose down`).
- Đã build + chạy thử thật bằng `docker compose up -d --build` từ thư mục `deploy`, không chỉ
  viết cấu hình suông.
- Xoá `backend/DX-Farm/docker-compose.yml` (chỉ chạy Postgres riêng cho dev cũ) — giờ
  `deploy/docker-compose.yml` là docker-compose file duy nhất trong repo, dùng chung cho cả
  demo full-stack lẫn Postgres khi dev thủ công.
