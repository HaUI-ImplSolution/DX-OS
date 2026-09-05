# Changelog

Nhật ký thay đổi của DX-Farm, theo từng module. Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/).

## Backend (Spring Boot — `backend/DX-Farm`)

### 2026-09-09

**Đã xong — Auth/SSO cơ bản (Giai đoạn 2 [H]):**

- Bật `spring-boot-starter-security` + `jjwt` (đã stub sẵn trong `pom.xml`/`application.yaml` từ
  trước, giờ mới dùng thật): JWT stateless (HS256), không session/cookie.
- `User` entity với 3 role `FARMER`/`ENGINEER`/`ADMIN`; seed đúng 1 tài khoản `ADMIN` lúc khởi
  động từ `SUPER_ADMIN_USERNAME`/`SUPER_ADMIN_PASSWORD` nếu bảng `users` còn rỗng — không có
  đăng ký công khai, chỉ `ADMIN` tạo được user mới (`POST /api/v1/users`).
- `POST /api/v1/auth/login` trả JWT + role. Toàn bộ API nghiệp vụ yêu cầu đăng nhập
  (`anyRequest().authenticated()`), trừ danh mục tham chiếu (đọc), `/auth/**`, swagger và
  `/uploads/**` (ảnh phục vụ trực tiếp qua HTTP, `<img>` ở FE không gửi kèm token).
- **Thay field tên tự do bằng danh tính đã xác thực** — điểm mấu chốt của tính năng: bỏ hẳn
  `reportedBy`/`approvedBy`/`rejectedBy` khỏi request body, backend tự lấy từ
  `SecurityContextHolder` (qua `CurrentUserService`) thay vì tin client gửi lên. Duyệt/từ
  chối/ghi nhận chẩn đoán giờ yêu cầu role `ENGINEER`/`ADMIN` (`@PreAuthorize`), enforce ở
  backend chứ không chỉ ẩn nút ở FE — đúng tinh thần poka-yoke 2 tầng đã áp dụng cho PHI.
  `Parcel.farmerName` giữ nguyên dạng text tự do vì đây là thuộc tính mô tả chủ thửa, không
  phải actor gọi API.
- 401 (chưa đăng nhập/token sai) và 403 (đủ đăng nhập nhưng sai role) trả đúng khuôn
  `ApiResponse` hiện có, qua `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler` (tầng
  filter chain) và `GlobalExceptionHandler` (tầng controller, cho lỗi từ `@PreAuthorize`).
- Đã build + chạy thử thật qua `deploy` (login → tạo user kỹ sư/nông dân → full spine flow
  theo từng role, xác nhận 401/403 đúng chỗ qua curl và qua trình duyệt) không chỉ build suông.
- `User` implement thẳng `UserDetails` thay vì bọc qua 
  `org.springframework.security.core.userdetails.User` — `CurrentUserService` giờ lấy actor
  thẳng từ principal trong `SecurityContext`, bớt 1 query DB thừa mỗi lần cần biết ai đang thao
  tác (report/approve/reject).

**Đã xong — M7 (Giai đoạn 1):**

- `GET /api/v1/crop-seasons/{id}/isolation-status` — trả lời "ruộng này còn cách ly không?"
  bằng SQL context deterministic: tái dùng chính truy vấn
  `findTopByCropSeasonIdOrderByHarvestLockedUntilDesc` mà `harvest()` đã dùng để chặn
  poka-yoke, đảm bảo một nguồn sự thật duy nhất giữa "hỏi" và "chặn". Trả về
  `isolated`, `harvestLockedUntil`, `daysRemaining`, `pesticideCode`, kèm câu trả lời tiếng
  Việt diễn đạt lại facts (`message`) — không qua LLM, đúng nguyên tắc deterministic-first.

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
- Chưa có RAG, Carbon/AWD Estimator, IoT simulator, BI dashboard (Superset/Metabase), SSO nặng
  (Keycloak/Authentik) hay kênh chat nội bộ (Mattermost) — thuộc Giai đoạn 1–3 theo roadmap
  trong `README.md`. Auth/role cơ bản (JWT, FARMER/ENGINEER/ADMIN) đã xong ở trên.

## AI Service (Python)

Chưa bắt đầu.

## Frontend (`frontend`)

### 2026-09-09

**Đã xong — Demo UI cho Phase 0 spine flow (M1–M6):**

- React 18 + TypeScript + Vite, không dùng router/state library ngoài — một trang duy nhất
  theo đúng thứ tự spine flow: chọn/tạo thửa ruộng → chọn/tạo vụ mùa → gửi báo cáo sâu bệnh
  kèm ảnh → chẩn đoán (nhập tay giả lập AI Service) → kỹ sư duyệt/từ chối → ghi nhận đã phun
  thuốc → khai báo thu hoạch (thấy rõ banner khi bị poka-yoke chặn, HTTP 422).
- Tab phụ "Danh mục tham chiếu" hiển thị read-only Disease/Pesticide/TreatmentRule (Structured
  Knowledge).
- Nút "Hỏi: ruộng này còn cách ly không? (M7)" ở panel Vụ mùa, gọi
  `GET /crop-seasons/{id}/isolation-status`, hiển thị câu trả lời bằng banner xanh/đỏ theo
  đúng trạng thái cách ly hiện tại.
- `src/api.ts` bọc toàn bộ `ApiResponse<T>` envelope của backend, ném `ApiError` (status + data)
  để UI phân biệt lỗi validate (400), state machine (409) và poka-yoke (422).
- Đã chạy thử full flow qua trình duyệt thật (parcel → crop season → upload ảnh → diagnosis →
  approve → apply → harvest bị khoá đúng theo PHI) trên backend thật, không chỉ build/typecheck.

**Thay đổi kèm theo ở backend để FE chạy được:**

- Thêm `WebConfig` (CORS cho `/api/**` từ localhost, static resource handler `/uploads/**` trỏ
  vào `app.upload-dir`) vì trước đó chưa có cấu hình CORS và ảnh upload chưa được serve qua HTTP.

**Chưa làm:**

- Chưa có low-code frontend (ToolJet/Appsmith) theo Giai đoạn 1 — đây vẫn là bản tự viết tối
  giản cho demo Phase 0.

**Đã xong — Đăng nhập + role, thay input tên tự do bằng danh tính đã xác thực:**

- `LoginPanel` mới, gate toàn bộ app sau màn đăng nhập; header hiện tên + vai trò người đang
  dùng kèm nút đăng xuất. `src/auth.ts` lưu `{token, username, fullName, role}` ở
  `localStorage`.
- `src/api.ts` tự gắn `Authorization: Bearer <token>` vào mọi request; gặp 401 (token hết
  hạn/thu hồi) thì tự xoá phiên và đưa người dùng quay lại màn đăng nhập.
- Bỏ hẳn input "Người báo cáo" (báo bệnh) và "Tên kỹ sư nông nghiệp" (duyệt/từ chối) — backend
  giờ tự lấy từ user đã đăng nhập. Nút duyệt/từ chối/ghi nhận chẩn đoán chỉ hiện với role
  `ENGINEER`/`ADMIN` (ẩn ở FE là UX, chặn thật vẫn ở backend qua `@PreAuthorize`).
- Đã test qua trình duyệt thật: đăng nhập admin mặc định → tạo tài khoản kỹ sư + nông dân qua
  API → đăng nhập lại bằng từng role, xác nhận UI ẩn/hiện đúng và backend trả 401/403 đúng chỗ.

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

**Đã xong (bổ sung) — truyền secret cho auth:**

- Service `backend` nhận thêm `JWT_SECRET`, `JWT_EXPIRATION`, `SUPER_ADMIN_USERNAME`,
  `SUPER_ADMIN_PASSWORD` từ `deploy/.env` (mẫu ở `deploy/.env.example`).
