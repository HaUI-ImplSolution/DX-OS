# Changelog

Nhật ký thay đổi của DX-Farm, theo từng module.
Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/).

## Backend (Spring Boot — `backend/DX-Farm`)

### 2026-10-02

**Đã xong — Thông báo Mattermost qua Incoming Webhook:**

- `DiseaseReportService.create()` publish `DiseaseReportCreatedEvent` sau khi lưu báo cáo mới; `DiagnosisService.create()` và `createFromAi()` publish `DiagnosisPendingEvent` sau khi lưu chẩn đoán `PENDING`.
  Báo cáo trả về từ nhánh idempotent (`Idempotency-Key` đã dùng) không publish.
- `MattermostNotificationListener` nhận event bằng `@TransactionalEventListener(AFTER_COMMIT)` và `@Async("mattermostNotifyExecutor")`; executor 2 thread, queue 100, tràn thì bỏ và ghi WARN.
- `MattermostWebhookClient` (`RestClient` + timeout cấu hình được) POST một `attachment` vào webhook; lỗi chỉ ghi một dòng WARN không chứa URL, không ném lên luồng nghiệp vụ.
- `MattermostMessageBuilder` dựng nội dung tiếng Việt; chuỗi người dùng cắt 100 ký tự, vô hiệu mention, link và markdown; thời điểm theo `Asia/Ho_Chi_Minh`.
- Cấu hình `mattermost.webhook.url|connect-timeout-ms|read-timeout-ms` (`MATTERMOST_WEBHOOK_URL` rỗng thì tắt).
- Không đổi endpoint, request/response hay Swagger.

### 2026-09-30

**Đã xong — Idempotency cho gửi báo cáo:**

- `POST /api/v1/crop-seasons/{id}/disease-reports` nhận header tuỳ chọn `Idempotency-Key` (UUID); key đã dùng thì trả báo cáo cũ mà không lưu ảnh lần hai.
- `DiseaseReport.clientRequestId` (`UUID`, unique, nullable); key thuộc vụ mùa khác hoặc user khác trả `409`, key sai định dạng trả `400`.
- Swagger khai báo header bằng `@Parameter`.

**Đã xong — Phạm vi dữ liệu của FARMER (phục vụ bản mobile):**

- User có role `FARMER` (không kèm `ENGINEER`/`ADMIN`) chỉ thấy thửa có `farmerName` khớp claim `name` của token, không phân biệt hoa thường.
  `CurrentUserService` có `isFarmer()` và `canAccess(Parcel)`; `ParcelRepository.findByFarmerNameIgnoreCase` phục vụ danh sách thửa.
- Endpoint thửa, vụ mùa (gồm `harvest`) và báo cáo bệnh trả `404` khi thửa không thuộc FARMER.
- `POST /api/v1/parcels` bởi FARMER lưu thửa với tên của chính họ, bỏ qua `farmerName` trong request.
- Với FARMER, `diagnosis` lồng trong báo cáo chỉ đầy đủ khi `APPROVED`; `PENDING` chỉ có `id`, `diseaseReportId`, `approvalStatus`; `REJECTED` thêm `decidedAt`, `rejectionReason`.
  ENGINEER và ADMIN nhận dữ liệu đầy đủ như trước.
- Swagger: `@Operation` của `ParcelController`, `CropSeasonController`, `DiseaseReportController` và schema `DiagnosisResponse` mô tả hành vi theo role.
- Đặc tả: `development/docs/mobile/BACKEND_CHANGES.md`.

**Đã xong — Chuyển seed sang bệnh lá lúa:**

- Seed 6 bệnh lá lúa active; pesticide, treatment rule và policy đang `IN_REVIEW` không được seed.
- Taxonomy, pesticide và rule cũ không còn tham gia lookup; bản ghi đang được lịch sử diagnosis/treatment tham chiếu được giữ lại ở trạng thái inactive.
- Migration đạt trên database mới và database legacy mô phỏng; Backend khởi động healthy với đúng 6 bệnh active.

### 2026-09-26

- Hoàn thiện seed idempotent từ dữ liệu nguồn: 1 treatment rule giả định cho E2E, 15 candidate còn lại bị chặn; rule stale được vô hiệu hóa và bảo toàn an toàn khi đã có lịch sử tham chiếu.
- Tối giản `data/`: loại 7 artifact báo cáo/đề xuất đã được master JSON, code và tài liệu hiện hành thay thế.
- Chuẩn hóa `data/` ở gốc repository làm nguồn RAG duy nhất, loại bản sao local khỏi luồng ingest E2E.
- Dọn hai tài liệu kế hoạch/checklist tách AI-Backend trung gian sau khi trạng thái cuối đã được phản ánh trong code và tài liệu hiện hành.

### 2026-09-16

**Đã xong — Phase 2 tách AI thành dịch vụ suy luận (P2-0..P2-6):**

- `AiDiagnosisClient` (`RestClient` + `JdkClientHttpRequestFactory`) gọi AI theo contract `POST /api/v1/diagnose`, timeout riêng + circuit breaker Resilience4j.
- Internal API read-only `GET /internal/v1/diagnosis-context` cho AI tra treatment rule + lịch sử phun theo `disease_code`, bảo vệ bằng service API key riêng (`ServiceApiKeyAuthenticationFilter`), chỉ cho GET.
- `DiagnosisController` có endpoint mới `POST .../diagnosis/ai` gọi `DiagnosisService.createFromAi()`: nhận CV/recommendation từ AI, đối chiếu `version` của treatment rule trước khi lưu, từ chối (409) nếu rule đã đổi trong lúc AI xử lý.
- Endpoint nhập tay `POST .../diagnosis` vẫn giữ cho testing/demo — cả hai cùng đổ vào một `Diagnosis`/vòng đời duyệt duy nhất qua `approve`/`reject`/`apply`.
- Xác nhận `DiseaseReportService` đã tự lưu `DiseaseReport` vào Backend DB từ Phase 0, không phụ thuộc AI ghi hộ.

**Đã xong — Phase 3 tách DB vật lý cho AI (P3-1..P3-4):**

- `AiVectorDatabaseSeeder` tự tạo database `dx_farm_ai_vector_db` (chỉ `documents`/`document_chunks`, extension `pgvector`) lúc khởi động.
- Hai role Postgres riêng cho AI: `ai_vector_ro` chỉ đọc khi runtime và `ai_vector_ingest` đọc/ghi khi ingest; AI không nhận credential truy cập Backend DB.
- `deploy/docker-compose.yml` chuyển AI runtime sang DB/role riêng và cấp `RAG_INGEST_DATABASE_URL` cho job ingest.
- Dữ liệu cũ do AI ghi trực tiếp trước khi tách DB (`disease_reports`, `diagnoses`, `treatment_applications`, `workflow_cases`/`workflow_events`) xác định là dữ liệu demo/test và không được giữ lại.

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

- JWT stateless (HS256, `spring-security` + `jjwt`), không session/cookie.
  `User` có 3 role `FARMER`/`ENGINEER`/`ADMIN`; seed đúng 1 `ADMIN` lúc khởi động từ biến môi trường, không có đăng ký công khai — chỉ `ADMIN` tạo user mới.
- `POST /api/v1/auth/login` trả JWT + role.
  Mọi API nghiệp vụ yêu cầu đăng nhập, trừ danh mục tham chiếu (đọc), `/auth/**`, swagger, `/uploads/**`.
- Điểm mấu chốt: bỏ hẳn field tên tự do (`reportedBy`/`approvedBy`/`rejectedBy`) khỏi request body — backend tự lấy actor từ `SecurityContextHolder`, không tin client gửi lên.
  Duyệt/từ chối/chẩn đoán giờ yêu cầu role `ENGINEER`/`ADMIN` (`@PreAuthorize`), enforce ở backend chứ không chỉ ẩn nút FE — cùng tinh thần poka-yoke 2 tầng như PHI.
- 401/403 trả đúng khuôn `ApiResponse` sẵn có, qua `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`/`GlobalExceptionHandler`.
- `User` implement thẳng `UserDetails` (thay vì bọc qua Spring's `User`) — bớt 1 query DB thừa mỗi lần cần biết actor đang thao tác.
- Đã build + test thật qua `deploy` (login, tạo user, full flow theo từng role, xác nhận 401/403 qua curl và trình duyệt).

**Đã xong — M7 (Giai đoạn 1):**

- `GET /api/v1/crop-seasons/{id}/isolation-status` — trả lời "ruộng này còn cách ly không?" bằng cách tái dùng đúng truy vấn mà `harvest()` dùng để chặn poka-yoke (một nguồn sự thật duy nhất giữa "hỏi" và "chặn").
  Trả `isolated`, `harvestLockedUntil`, `daysRemaining`, `pesticideCode` + câu trả lời tiếng Việt diễn giải facts — không qua LLM.

### 2026-09-08

**Đã xong — Phase 0 spine flow (M1–M6), đủ để demo end-to-end:**

- Khởi tạo project Spring Boot (Java 21, PostgreSQL, Docker/docker-compose), khung dùng chung: `ApiResponse<T>` (envelope response thống nhất), `GlobalExceptionHandler` (400/404/409/422/500), Swagger/OpenAPI tự sinh từ code.
- **Structured Knowledge** (danh mục, đọc qua API, seed sẵn qua `data.sql`): `Disease`, `Pesticide`, `TreatmentRule` (liều lượng + số ngày cách ly PHI).
- **Operational Data**: `Parcel` (thửa ruộng), `CropSeason` (vụ mùa), `DiseaseReport` (báo cáo sự cố kèm ảnh, upload multipart lưu local disk), `Diagnosis` (chẩn đoán + khuyến nghị + trạng thái duyệt), `TreatmentApplication` (bản ghi đã phun thuốc, snapshot liều/PHI).
- Luồng nghiệp vụ đầy đủ: báo bệnh có ảnh → tra `Disease`→`TreatmentRule` deterministic (không qua LLM) → tự sinh khuyến nghị tiếng Việt → kỹ sư Approve/Reject (có state machine chặn duyệt/áp dụng sai thứ tự) → ghi nhận đã phun thuốc, tự tính ngày hết cách ly → **poka-yoke**: gọi thu hoạch khi còn cách ly bị chặn cứng, trả `HTTP 422` kèm mã lỗi `POKA_YOKE_VIETGAP_VIOLATION`.

**Chưa làm** (ngoài phạm vi backend hoặc chưa cần ở Phase 0):

- Vision API và RAG prototype đã có trên nhánh `feature/make-data`, nhưng backend chưa có HTTP client gọi AI Service; `Diagnosis` vẫn nhận sẵn `disease_id/confidence/model_version`.
- Chưa có Carbon/AWD Estimator, IoT simulator, BI dashboard (Superset/Metabase), SSO nặng (Keycloak/Authentik) hay kênh chat nội bộ (Mattermost) — thuộc Giai đoạn 1–3 theo roadmap trong `README.md`.
  Auth/role cơ bản (JWT, FARMER/ENGINEER/ADMIN) đã xong ở trên.

## Dữ liệu (`data`)

### 2026-09-30

**Đã xong — Thay bộ dữ liệu PlantVillage bằng dữ liệu bệnh lá lúa:**

- Chuẩn hóa 6 điều kiện lúa, gồm 5 lớp detection và một mã healthy; loại toàn bộ mã PlantVillage khỏi canonical JSON và seed SQL.
- Corpus RAG còn 6 tài liệu, truy vết qua 9 nguồn; dữ liệu thuốc, treatment rule và policy giữ nguyên trạng thái `IN_REVIEW` từ workbook.
- Generator, validator đối chiếu workbook, seed check và RAG dry-run đều đạt; `data.sql` được sinh deterministic từ dữ liệu chuẩn.

## Mobile (`mobile`, Expo)

### 2026-10-01

- Metro chạy ở cổng `8083` (script `npm start`) vì `8081` dành cho Backend.
- `mobile/README.md` ghi cách chạy khi web và mobile dùng Keycloak cùng lúc: điện thoại vào qua IP LAN ở `.env.local`, không cần đổi `deploy/.env`.

### 2026-09-30

**Đã xong — Khung app cho FARMER:**

- Expo SDK 57, TypeScript, `expo-router`, thư mục `mobile/` ngang hàng `frontend/`.
- `app.json`: scheme `dxfarm`, chuỗi quyền camera tiếng Việt; `app.config.ts` cho phép HTTP cleartext ngoài profile `production`.
- `mobile/.env.example`: `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_KEYCLOAK_URL`, `EXPO_PUBLIC_KEYCLOAK_CLIENT_ID`.
- `src/`: client API (`request`, `ApiError`, `json`, timeout 30 giây, lỗi mạng trả `status` 0), lưu token bằng `expo-secure-store`, type dùng cho farmer, theme theo design tokens, component `Screen`.

**Đã xong — Xác thực:**

- Đăng nhập Authorization Code + PKCE với client `dxfarm-mobile` qua `expo-auth-session`; endpoint dựng từ `EXPO_PUBLIC_KEYCLOAK_URL`, không dùng discovery.
- Access token và refresh token lưu ở `expo-secure-store`; token tự refresh trước khi hết hạn, refresh bị từ chối hoặc `401` thì xoá phiên và về màn Đăng nhập.
- Role đọc từ `realm_access.roles` của access token; tài khoản không có role FARMER bị đăng xuất kèm thông báo "Ứng dụng này dành cho nông dân".
- Đăng xuất kết thúc luôn phiên Keycloak (POST endpoint logout kèm refresh token).
- Route guard bằng `Stack.Protected` ở layout gốc; màn Đăng nhập và Hồ sơ.

**Đã xong — Màn hình đọc dữ liệu:**

- Thửa của tôi (badge "Đang cách ly" theo vụ đang hoạt động), Vụ mùa của thửa (vụ đang hoạt động ở trên, vụ đã thu hoạch mờ hơn), Báo cáo của vụ (lưới 2 cột kèm chip trạng thái).
- Banner cách ly từ `IsolationStatus`; ảnh hiển thị qua `/uploads/{imageRef}`.
- Kéo để làm mới, khung xương khi tải, trạng thái rỗng và lỗi kèm nút "Thử lại".
- Component dùng chung: `StatusChip`, `Card`, `EmptyState`, `ErrorState`, `IsolationBanner`; hook `useAsync`.

**Đã xong — Chụp, gửi và chi tiết báo cáo:**

- Chụp bằng camera hệ thống (`launchCameraAsync`), xin quyền camera và hướng dẫn mở Cài đặt khi bị từ chối.
- Nén bằng `expo-image-manipulator`: cạnh dài tối đa 1280px, JPEG 0.7.
- Gửi multipart part `image` qua `XMLHttpRequest` để có thanh tiến trình; lỗi hiển thị kèm nút "Gửi lại"; không thoát được màn hình khi đang gửi.
- Chi tiết báo cáo: timeline Đã gửi → Đã chẩn đoán → Kỹ sư duyệt theo `diagnosis` (`null`, `PENDING`, `APPROVED`, `REJECTED`); `APPROVED` hiện bệnh, khuyến nghị, thuốc, liều, số ngày cách ly; `REJECTED` hiện lý do và nút "Chụp lại".
- Danh sách báo cáo tự làm mới khi quay lại từ màn chụp; nút tròn nổi "Chụp" ở góc phải dưới.

**Đã xong — Hàng đợi gửi khi mất mạng:**

- Mất mạng hoặc lỗi kết nối lúc gửi thì ảnh được copy vào `documentDirectory` và ghi hàng đợi vào AsyncStorage; mỗi ảnh có UUID làm `Idempotency-Key`.
- Tự gửi khi `NetInfo` báo có mạng và khi app trở lại foreground; thử lại với khoảng chờ tăng dần, tối đa 8 lần; lỗi `4xx` (trừ `408`, `409`, `429`) đánh dấu "Lỗi".
- Dải "Chờ gửi (N)" ở màn Báo cáo của vụ; danh sách chờ gửi và nút xoá ở màn Hồ sơ; hàng đợi tách theo tài khoản.

**Đã xong — Đóng gói:**

- `mobile/eas.json` với profile `preview` xuất APK; hướng dẫn build bằng EAS trong `mobile/README.md`.

**Đã xong — Hướng dẫn chạy:**

- `mobile/README.md`: chạy thử trên điện thoại thật với IP LAN, biến môi trường, tài khoản demo, kiểm tra mã nguồn.

## AI Service (Python)

### 2026-09-30

**Đã xong — Chuyển CV và RAG sang bệnh lá lúa:**

- Thay EfficientNet bằng YOLOv8n với checkpoint cục bộ `best.pt`; kiểm tra checksum, task, số lớp và thứ tự class khi khởi động.
- Mapping class đọc từ `class_contract.json`; checkpoint được mount read-only và không tự tải model.
- RAG ingest 6 tài liệu thành 31 chunks; cả 6 mã lúa đi đúng Backend context/RAG và về `review_required` khi chưa có rule được duyệt.
- Giữ nguyên response CV năm trường và contract chẩn đoán; container CPU, kiểm thử tích hợp và E2E đều đạt.
- Bổ sung shared library cho OpenCV/Ultralytics; AI image build và healthcheck thành công.

### 2026-09-18

- Hoàn tất tích hợp AI stateless với Backend REST và AI Vector DB riêng sau ba phase decoupling.
- Runtime chỉ đọc RAG; job ingest sử dụng credential ghi riêng qua `RAG_INGEST_DATABASE_URL`.

### 2026-09-17

- Thêm timeout và retry giới hạn khi AI gọi internal API của Backend.
- Buộc AI trả lỗi khi Backend không cung cấp structured facts.

### 2026-09-16

- Xóa thành công 10 ORM model nghiệp vụ dư thừa khỏi AI Service.
- Xóa thành công repository/service CRUD và thao tác ghi dữ liệu nghiệp vụ khỏi AI runtime.
- Xóa thành công router workflow cũ, `WorkflowRunner`, `WorkflowTransitions` và schema liên quan khỏi AI Service.
- Xóa thành công endpoint legacy `POST /disease/reports/{report_id}/diagnose` khỏi AI Service.
- Thêm Backend REST client read-only để AI lấy field context và structured facts.
- Hoàn thành `POST /api/v1/diagnose`, kết hợp CV, RAG và structured facts từ Backend để trả recommendation đúng contract.
- Tách quyền truy vấn RAG của runtime khỏi quyền ghi corpus của job ingestion.

### 2026-09-14

**Đã xong — đóng gói Docker và chuẩn hóa cấu hình AI Service:**

- `ai/Dockerfile` đóng gói FastAPI trên cổng 8000 với healthcheck, non-root user, volume weights và 2-layer pip cache cho PyTorch.
- AI Service dùng schema PostgreSQL thống nhất; dữ liệu seed và embedding được ingest sẵn trước khi RAG/workflow truy vấn.

### 2026-09-11

**Đã xong — Luồng điều phối AI Spine Flow & Quản lý vòng đời khuyến nghị:**

- LangGraph điều phối spine flow: request chẩn đoán CV → field context → structured lookup → RAG → recommendation → checkpoint `PENDING`.
  `workflow_runner.py` và `workflow_transitions.py` nằm ở tầng application, tách biệt khỏi graph để orchestration không lẫn với HITL transition.
- Structured facts luôn lấy từ SQL: treatment dùng đúng rule; policy-only không tự sinh thuốc; RAG hoặc LLM lỗi/timeout tự động về `evidence_fallback`, giữ nguyên thuốc, hoạt chất, liều và PHI từ SQL.
- Durable HITL trong PostgreSQL: lưu `WorkflowCase` và `WorkflowEvent` append-only; giữ bất biến `APPROVED != APPLIED` (approve chỉ chuyển sang `WAITING_APPLIED`, chỉ `APPLIED` mới tạo `TreatmentApplication` và tính PHI).
- Tính toán PHI an toàn: `harvest_locked_until = applied_at + phi_days`, dùng khóa `MAX` để các lần phun sau không thể rút ngắn thời gian cách ly của lần phun trước.

### 2026-09-10

**Đã xong — Chuẩn hóa Vision API, Dữ liệu quy tắc & RAG Search:**

- Vision API chuẩn hóa trả `disease_code` và `disease_name` tiếng Việt khớp 1:1 với `Disease.code` trong PostgreSQL cho 38 lớp bệnh lý.
- Tái cấu trúc module `ai/app`: tách biệt `api`, `cv`, `db`, `rag`, `services` và `graph`; cấu hình tập trung qua `pydantic-settings` và load weights mô hình trong FastAPI lifespan.
- Định dạng quy tắc điều trị: đổi `TreatmentRule.dose` sang khoảng `dose_min`/`dose_max`, thêm bảng `DiseaseManagementPolicy` và validate liều thực tế `actual_dose` khi áp dụng.
- RAG search với pgvector: lọc chính xác theo `disease_code`, truy vấn Top-K chunk kèm metadata nguồn (`provenance`/citation URL) và fallback an toàn khi LLM không phản hồi.

### 2026-09-09

**Đã xong — Nền tảng dữ liệu RAG & Vision API cơ bản:**

- Khởi tạo SQLAlchemy data layer cho 8 entity spine-flow và 2 entity RAG (`Document`, `DocumentChunk`), bật extension pgvector với vector embedding 384 chiều.
- RAG prototype local: ingest 38 tài liệu tri thức bệnh cây trồng, tìm kiếm cosine similarity và tích hợp Qwen2.5-1.5B qua Ollama để giải thích ngữ cảnh.
- Vision API cơ bản: FastAPI endpoint `POST /api/v1/disease/predict` nhận ảnh và phân loại bệnh dựa trên mô hình PyTorch EfficientNet-B4.

**Chưa làm:**

- Chưa nối HTTP client giữa Spring Boot backend và AI Service (API workflow AI chạy local độc lập).
- 8 candidate quy tắc điều trị trạng thái HOLD được giữ riêng để audit, không seed vào CSDL chính thức.

## Frontend (`frontend`)

### 2026-10-01

- Đăng xuất giữ `id_token_hint`: `signOut` không xoá user trước `signoutRedirect`, nên Keycloak kết thúc phiên SSO ngay thay vì hiện trang xác nhận, và Superset đăng xuất theo.

### 2026-09-30

**Đã xong — Phù hợp phạm vi dữ liệu của FARMER:**

- `ParcelPanel`: khi đăng nhập bằng FARMER, ô "Tên chủ hộ" hiển thị tên người dùng và bị khoá; thửa luôn được đăng ký dưới tên đó.
- `DiagnosisPanel`, `Diagnosis` trong `types.ts`: các trường chẩn đoán có thể `null`; FARMER thấy trạng thái, và lý do từ chối khi `REJECTED`, còn nội dung chẩn đoán chỉ hiện khi `APPROVED`.

### 2026-09-11

**Đã xong — Đăng nhập qua Keycloak (SSO thật):**

- `src/auth.ts` dùng `oidc-client-ts` (Authorization Code + PKCE), phiên lưu ở `sessionStorage`.
- Chưa có phiên thì tự redirect sang Keycloak ngay lúc mount, không cần bấm nút — vào thẳng từ portal cũng tự vào được app.
  Callback lỗi (huỷ đăng nhập, tài khoản bị khoá...) thì dừng lại báo lỗi kèm nút thử lại, không lặp vô hạn.
  Đọc `fullName`/`role` từ claim token sau khi quay về.
- `src/api.ts` gắn Bearer token từ phiên OIDC; 401 tự xoá phiên, quay lại màn đăng nhập.
- Single logout: bật `monitorSession` để tự phát hiện khi phiên Keycloak kết thúc ở app khác (Superset) hoặc Keycloak Account Console, tự đăng xuất theo.
  Chi tiết: `sso/README.md`.
- Đã test qua trình duyệt thật với từng tài khoản demo trong `sso/README.md`, bao gồm bấm thẳng từ portal và đăng xuất chéo giữa App chính/Superset.

### 2026-09-09

**Đã xong — Demo UI cho Phase 0 spine flow (M1–M6):**

- React 18 + TypeScript + Vite, một trang duy nhất theo đúng spine flow: chọn/tạo thửa ruộng → vụ mùa → báo sâu bệnh kèm ảnh → chẩn đoán (nhập tay giả lập AI Service) → kỹ sư duyệt/từ chối → ghi nhận đã phun thuốc → khai báo thu hoạch (banner rõ khi bị poka-yoke chặn, HTTP 422).
- Tab "Danh mục tham chiếu" (read-only Disease/Pesticide/TreatmentRule) và nút hỏi "ruộng còn cách ly không? (M7)" ở panel Vụ mùa, gọi `GET /crop-seasons/{id}/isolation-status`.
- `src/api.ts` bọc `ApiResponse<T>` envelope, ném `ApiError` để UI phân biệt lỗi 400/409/422.
- Đã chạy full flow qua trình duyệt thật trên backend thật, không chỉ build/typecheck.

**Thay đổi kèm theo ở backend để FE chạy được:** thêm `WebConfig` (CORS cho `/api/**`, static handler `/uploads/**`).

**Chưa làm:** low-code frontend (ToolJet/Appsmith) — đây vẫn là bản tự viết tối giản cho demo.

**Đã xong — Đăng nhập + role, thay input tên tự do bằng danh tính đã xác thực:**

- `LoginPanel` mới gate toàn app; header hiện tên/vai trò + nút đăng xuất.
  `src/auth.ts` lưu `{token, username, fullName, role}` ở `localStorage`.
- `src/api.ts` tự gắn Bearer token; gặp 401 thì tự xoá phiên, quay lại màn đăng nhập.
- Bỏ input "Người báo cáo"/"Tên kỹ sư" — backend tự lấy từ user đã đăng nhập.
  Nút duyệt/từ chối/chẩn đoán chỉ hiện với role `ENGINEER`/`ADMIN` (ẩn FE là UX, chặn thật ở backend).
- Đã test qua trình duyệt thật với từng role, xác nhận UI và 401/403 đúng chỗ.

## Deploy (`deploy`)

### 2026-10-02

- `backend` nhận biến tuỳ chọn `MATTERMOST_WEBHOOK_URL` (mặc định rỗng); `mattermost` bật `MM_SERVICESETTINGS_ENABLEINCOMINGWEBHOOKS`.
- `.env.example` có `MATTERMOST_WEBHOOK_URL` để trống kèm chú thích cách lấy giá trị.

### 2026-10-01

- Keycloak không đặt `KC_HOSTNAME`, dùng hostname của request: web vào `localhost:8180`, điện thoại vào qua IP LAN, hai bên chạy cùng lúc.
- Superset đọc tên, email và role từ access token thay vì gọi `/userinfo`, vì `/userinfo` từ chối token có issuer khác hostname của request.
- `VITE_KEYCLOAK_URL` của frontend và `KEYCLOAK_BROWSER_BASE` của Superset lấy từ `KEYCLOAK_PUBLIC_URL` (mẫu `http://localhost:8180`).

### 2026-09-30

- `docker-compose.yml` không chứa giá trị mặc định: mọi biến `${...}` bắt buộc khai báo trong `deploy/.env`, giá trị mẫu nằm ở `.env.example`; thiếu biến nào Compose dừng và báo tên biến đó. `KEYCLOAK_PUBLIC_URL` có giá trị mẫu `http://localhost:8180`.
- Đồng bộ tài liệu với cấu hình hiện tại: hướng dẫn `.env` bắt buộc, tài khoản Mattermost có sẵn, job `rag-ingest`/Ollama tự chạy, khoảng liều `dose_min`/`dose_max`, số liệu seed (38 bệnh, 6 thuốc, 1 rule active, 13 policy).
- Service `superset-init` chown `data/superset` về uid 1000 trước khi Superset khởi động, nên dựng mới không cần thao tác tay.
- Service `mattermost-bootstrap` tạo System Admin, team và channel mặc định (xem mục `chat`); biến `MATTERMOST_ADMIN_USERNAME`, `MATTERMOST_ADMIN_EMAIL`, `MATTERMOST_ADMIN_PASSWORD` trong `.env.example`.

### 2026-09-29

- `deploy/.env.example` là nơi duy nhất chứa mẫu cấu hình; các file `.env*` của `ai`, `backend/DX-Farm`, `frontend` đã bỏ.
- Biến môi trường đặt tên theo `<SERVICE>_<THIẾT_LẬP>`, nhóm theo service trong `.env.example`; `docker-compose.yml` ánh xạ sang tên nội bộ của từng service. Khóa giữa backend và AI đặt theo chiều: `BACKEND_TO_AI_API_KEY`, `AI_TO_BACKEND_API_KEY`.
- Cổng expose ra host đặt tên `<SERVICE>_HOST_PORT`; timeout backend gọi AI là `BACKEND_TO_AI_CONNECT_TIMEOUT_MS`/`BACKEND_TO_AI_READ_TIMEOUT_MS`; mật khẩu role Postgres phụ nêu rõ đích: `SUPERSET_READONLY_DB_PASSWORD`, `AI_VECTOR_DB_READONLY_PASSWORD`, `AI_VECTOR_DB_INGEST_PASSWORD`.
- `BACKEND_BASE_URL` là URL backend dùng chung cho AI và cho `APP_PUBLIC_BASE_URL` của backend.
- `frontend/Dockerfile` nhận `VITE_API_BASE_URL`, `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_CLIENT_ID` qua build args, cấp từ `deploy/docker-compose.yml` (URL Keycloak theo `KEYCLOAK_HOST_PORT`).

### 2026-09-11

**Đã xong — service `keycloak` (SSO thật):**

- Thêm service `keycloak` (image chính thức `quay.io/keycloak/keycloak:26.0`), cổng mặc định `8180`.
  Tự import realm `dxfarm` từ `sso/realm-dxfarm.json` lúc khởi động, không cần thao tác tay.
- `.env.example`: `KEYCLOAK_ADMIN_PASSWORD`/`KEYCLOAK_DB_PASSWORD` thay cho secret JWT/admin cũ.
- Đã chạy thật bằng `docker compose up -d --build`.

### 2026-09-09

**Đã xong — đóng gói Postgres + backend + frontend chạy một lệnh:**

- `deploy/docker-compose.yml`: build backend (`backend/DX-Farm/Dockerfile`) + frontend (`frontend/Dockerfile` mới, multi-stage `npm run build` → serve bằng nginx).
  Expose `${POSTGRES_PORT:-5432}`, `${BACKEND_PORT:-8081}`, `${FRONTEND_PORT:-8082}`.
- Frontend production gọi API cùng-origin — nginx reverse-proxy `/api/` và `/uploads/` sang backend, nên deploy không cần CORS.
- Postgres + ảnh upload persist qua bind mount `deploy/data/` (không mất khi `docker compose down`).
- Đã build + chạy thử thật bằng `docker compose up -d --build`.
- Xoá `backend/DX-Farm/docker-compose.yml` cũ — `deploy/docker-compose.yml` giờ là compose file duy nhất trong repo (dùng chung cho demo full-stack lẫn Postgres khi dev thủ công).
- Service `backend` nhận thêm secret cho auth (`JWT_SECRET`, `JWT_EXPIRATION`, `SUPER_ADMIN_USERNAME/PASSWORD`) từ `deploy/.env` (mẫu ở `.env.example`).

## BI Dashboard (`bi`, Apache Superset)

### 2026-09-30

- Import dashboard mẫu chạy được trên metadata DB trống: khóa `passwords` của API import dùng đường dẫn đã bỏ thư mục gốc của zip.

### 2026-09-11

**Đã xong — SSO thật qua Keycloak:**

- `bi/superset_config.py` bật `AUTH_TYPE = AUTH_OAUTH`, đăng nhập UI bằng redirect sang Keycloak.
  `CustomSecurityManager` map claim `realm_access.roles` (đọc trực tiếp từ access token) sang role Superset: `ADMIN` → `Admin`, `ENGINEER`/`FARMER` → `Gamma`.
- Client confidential `dxfarm-superset` mới trong `sso/realm-dxfarm.json`.
- `bi/bootstrap.sh` tự `pip install authlib` lúc khởi động (không có sẵn trong image gốc, không build lại image).
- Tài khoản admin DB cục bộ vẫn dùng được qua REST API cho `bootstrap_datasources.py`, không phụ thuộc `AUTH_TYPE`.
- Single logout: `LOGOUT_REDIRECT_URL` khiến đăng xuất Superset cũng kết thúc phiên SSO ở Keycloak, không chỉ xoá cookie Superset.
  Client `dxfarm-superset` bật `frontchannelLogout` để nhận thông báo khi phiên SSO kết thúc từ nơi khác.
  Chi tiết: `sso/README.md`.
- Đã đăng nhập thật qua trình duyệt bằng `engineer1`/`admin1`, xác nhận role map đúng và đăng xuất chéo với App chính hoạt động đúng cả 2 chiều.

### 2026-09-10

**Đã xong — BI dashboard (Giai đoạn 2 [D]):**

- Thêm service `superset` (image chính thức `apache/superset:3.1.3`, không build lại) vào `deploy/docker-compose.yml`, lên cùng lệnh `docker compose up -d --build` sẵn có, cổng 8088.
  Bind mount script/dashboard từ thư mục `bi/` riêng.
- Backend tự tạo role Postgres chỉ-đọc `superset_ro` lúc khởi động (`SupersetReadOnlyRoleSeeder`, JDBC thuần — `data.sql` không tương thích với khối PL/pgSQL `DO $$ ... $$`).
- `bi/bootstrap.sh` + `bootstrap_datasources.py`: tự `db upgrade` + tạo admin + đăng ký database connection + 5 dataset + import dashboard mẫu qua REST API công khai của chính Superset.
  Không cần thao tác tay sau `docker compose up`.
  Idempotent, có retry.
- Dashboard mẫu "DX-Farm — Bản đồ & Trạng thái thửa ruộng": deck.gl Scatterplot vẽ thửa ruộng theo toạ độ + Pie chart tỉ lệ vụ mùa ACTIVE/HARVESTED.
  Build bằng browser thật (Claude in Chrome), verify bằng screenshot dữ liệu thật, export lại thành bundle YAML commit vào `bi/dxfarm_dashboard_export.zip` để tự import lại mỗi lần dựng từ đầu.
- `Parcel` có thêm 2 cột toạ độ tuỳ chọn `latitude`/`longitude` (backend + FE `ParcelPanel`) để dashboard vẽ bản đồ thật.
- Đã build + chạy thử thật, kể cả re-import dashboard nhiều lần — idempotent, không tạo trùng chart.
- **Còn thiếu**: chưa cấu hình `MAPBOX_API_KEY` nên bản đồ chưa có nền (basemap) thật, chỉ thấy điểm trên nền trắng — cần token Mapbox miễn phí, xem `bi/README.md`.

## Kênh chat nội bộ (`chat`, Mattermost)

### 2026-10-02

**Đã xong — Thông báo tự động từ Backend:**

- Backend post "Báo cáo sâu bệnh mới" và "Chẩn đoán chờ duyệt" vào channel `Kỹ sư nông nghiệp` qua Incoming Webhook; webhook cấp tay một lần theo `chat/README.md`.
- Thông báo best-effort, không retry, tắt khi `MATTERMOST_WEBHOOK_URL` rỗng.
- Một chiều: duyệt/từ chối vẫn làm trong web app; không deep link; channel public nên mọi thành viên team đọc được thông báo.
- Mattermost vẫn dùng tài khoản riêng, không nối SSO.

### 2026-09-30

- `chat/bootstrap.sh` (service `mattermost-bootstrap`) tạo sẵn System Admin, team `dx-farm` và 3 channel: `Thông báo chung`, `Kỹ sư nông nghiệp` (public), `Ban quản trị` (private); idempotent.

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
- Rà soát license kỹ vì Mattermost không dùng một license duy nhất: server core AGPLv3 (OSI-approved), `server/enterprise/` là Mattermost Source Available License (không OSI-approved, không dùng tới), bản binary Team Edition được Mattermost, Inc. cấp phép lại MIT riêng cho đúng binary đó.
  Ghi chi tiết ở `LICENSES.md` và `chat/README.md`.
- Tài khoản Mattermost tách biệt hoàn toàn khỏi tài khoản backend (FARMER/ENGINEER/ADMIN) có chủ đích — nền tảng riêng, không dùng SSO chung.

## Portal (`portal`)

### 2026-10-01

- Thêm mục "Quản trị & tài liệu": Keycloak Admin, Superset Admin (`/users/list/`), Mattermost Admin (System Console) và Swagger của Backend.

### 2026-09-11

**Đã xong — bấm link từ portal vào thẳng app đã đăng nhập SSO:**

- Link BI Dashboard trỏ thẳng `/login/keycloak` thay vì trang chủ Superset — vào thẳng luồng OAuth, không dừng ở màn hình "Sign In with keycloak".
- Kết hợp với frontend tự redirect khi chưa có phiên: bấm app nào từ portal cũng tự chuyển sang Keycloak nếu chưa đăng nhập, xong quay lại đúng app đó; đã đăng nhập app này rồi thì bấm app kia (App chính/BI Dashboard) không cần đăng nhập lại.
- Đã test qua trình duyệt thật: bấm portal → chưa có phiên → Keycloak → quay lại đúng app; bấm app còn lại → vào thẳng không cần đăng nhập lại.

### 2026-09-10

**Đã xong — trang portal tĩnh, bước đệm trước SSO:**

- Trang tĩnh (nginx, không build step) liệt kê link tới 3 ứng dụng hiện có (Ứng dụng chính, BI Dashboard, Kênh chat nội bộ) — người dùng không cần tự nhớ URL từng app.
- Thêm service `portal` vào `deploy/docker-compose.yml`, cổng mặc định `8090` (`PORTAL_PORT`).
- Đã build + chạy thử thật qua `docker compose up -d`, xác nhận trang load đúng.
- **Còn thiếu**: mỗi app đích vẫn đăng nhập riêng — portal chỉ là danh sách link tĩnh, sẽ trở thành landing page thật khi SSO (Keycloak/Authentik) được triển khai.

## Identity Provider (`sso`, Keycloak)

### 2026-09-30

**Đã xong — Client cho bản mobile:**

- Client public `dxfarm-mobile` trong `realm-dxfarm.json`: Standard Flow + PKCE, không direct access grants, redirect URI `dxfarm://*` và `exp://*`.
- Keycloak trong `deploy/docker-compose.yml` dùng hostname của request để điện thoại truy cập qua IP LAN.
- `sso/README.md`: phạm vi, cách dev với IP LAN, cách thêm client vào volume cũ.

### 2026-09-11

**Đã xong — SSO thật (Giai đoạn 2 [H]):**

- Realm `dxfarm` (`sso/realm-dxfarm.json`): 3 realm role, client public `dxfarm-frontend` (PKCE), client confidential `dxfarm-superset`, 3 tài khoản demo.
- Backend, frontend và BI Dashboard (Superset) đều xác thực qua Keycloak.
  Chi tiết + trade-off hostname nội bộ vs. trình duyệt: `sso/README.md`.
- Single logout (front-channel + session monitoring): đăng xuất ở app nào cũng kết thúc phiên SSO ở Keycloak, các app khác đang mở tự đăng xuất theo.
  Giới hạn: không phải backchannel logout chuẩn OIDC (chỉ hoạt động khi tab còn mở).
  Chi tiết: `sso/README.md`.
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

## Tài liệu (root, `development/docs`, `ai/`)

### 2026-10-02

**Đã xong:**

- `development/docs/MATTERMOST_WEBHOOK_NOTIFICATION_PLAN.md`: thiết kế, danh sách công việc và hạn chế của thông báo Mattermost.
- `chat/README.md` (mục thông báo tự động và cấp webhook), `README.md` gốc (Giai đoạn 2 [H], trạng thái, bảng truy cập).

### 2026-09-30

**Đã xong:**

- `development/docs/mobile/`: kế hoạch, đặc tả backend, thiết kế UI và `CHANGES_OVERVIEW.md` tổng hợp hành vi cũ và mới của nhánh mobile.
- `mobile/README.md`, `LICENSES.md` (mục Mobile), `sso/README.md` (client `dxfarm-mobile`), `README.md` gốc (client mobile, trạng thái).

### 2026-09-24

**Đã xong:**

- Thống nhất luồng seed: `data/scripts/generate_seed_sql.py` sinh `data.sql` từ `data/structured/*.json`; chỉ seed record có cờ eligibility `true`.
  Quy ước hai cơ chế seed ghi ở `README.md`.
- Thêm entity `DiseaseManagementPolicy` (bảng `disease_management_policies`), seed 13 guardrail qua generator.
  `treatment_rules` có ràng buộc duy nhất `(disease_id, pesticide_id)` nên seed chạy đồng thời vẫn không trùng.
  `treatment_rules` chưa có dòng nào vì cả 16 candidate đều `eligible_for_seed=false`.
- `ai/app/rag/source_registry.py`, `ai/app/rag/ingestion_service.py` đọc corpus từ `data/rag/...` ở gốc repository.
- `deploy/docker-compose.yml`, `deploy/.env.example` bind-mount `data/` vào `/data` cho container `rag-ingest`.
- `data/README.md`: mapping `dose_min`/`dose_max` khớp đúng `TreatmentRule` hiện tại.
- `README.md`, `ai/README.md`: đánh dấu `DiseaseManagementPolicy` đang ở giai đoạn kế hoạch, chưa có entity/bảng thật.

### 2026-09-15

**Đã xong:**

- Từng bổ sung kế hoạch và checklist triển khai tách AI/Backend theo phase; hai tài liệu trung gian này được dọn sau khi hoàn tất và hợp nhất trạng thái cuối vào tài liệu hiện hành.
- Cập nhật `README.md`, `ai/README.md`, `ai/app/graph/README.md`, `AI_EVENT_SCHEMA_README.md` để mô tả trạng thái kiến trúc tại thời điểm triển khai.
- Dọn `development/docs/`: chỉ còn kế hoạch/checklist/contract hiện hành; tài liệu thiết kế gốc chuyển vào `legacy_docs/`.
