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

**Đã xong — Vision API (Computer Vision) cơ bản:**

- Khởi tạo project FastAPI (`ai/src`).
- Tích hợp model phân loại bệnh thực vật dựa trên PyTorch (kiến trúc `EfficientNet-B4`, hỗ trợ 38 lớp bệnh lý).
- Cung cấp API endpoint `POST /api/v1/disease/predict` nhận upload ảnh và trả về kết quả chẩn đoán (`class_id`, `class_name`, `confidence`, `model_name`, `model_version`).

**Chưa làm:**

- Chưa tích hợp LLM (Qwen2.5-1.5B qua Ollama) để sinh khuyến nghị.
- Chưa có LangGraph để điều phối luồng (workflow).
- Chưa có lookup chuẩn xác mã bệnh (`disease_code`) và tên tiếng Việt (`disease_name_vi`) để đồng bộ với mã `Disease` của CSDL backend (hiện API trả về `null`).
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
