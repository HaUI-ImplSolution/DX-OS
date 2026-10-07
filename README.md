# DX-Farm

MVP dự thi **OLP Phần mềm nguồn mở 2026** (khối thi DX-OS, VFOSSA).
Bài toán được chọn: **Agri-Carbon DX-OS** — hệ điều hành chuyển đổi số nội bộ cho một Hợp tác xã Nông nghiệp Số chuyên canh lúa nước (ĐBSCL), triển khai theo kiến trúc 4 không gian **H-P-D-I** (Human, Process, Data, Intelligence).

> Tài liệu này là bản tổng quan để làm ngữ cảnh cho các phiên làm việc sau — không đi sâu chi tiết kỹ thuật.
> Chi tiết triển khai từng module sẽ được đào sâu dần trong các lần làm việc tiếp theo.

> Tiến trình implement từng module (đã xong gì, chưa làm gì) được ghi lại ở [`CHANGELOG.md`](./CHANGELOG.md).

> Cách khởi động PostgreSQL bằng Docker Compose xem mục "Chạy demo Phase 0" bên dưới; chuẩn bị schema/seed/RAG riêng cho AI xem [`ai/README.md`](./ai/README.md).

## Nguồn tài liệu

- `development/docs/legacy_docs/DX - Farm.docx` — thiết kế gốc "MVP Agri-Carbon DX-OS" (8 trang): phân vai tác nhân, 4 không gian HPDI, spine flow 6 bước, các lưu ý PoF/anti-hallucination/poka-yoke.
- `development/docs/legacy_docs/DX-Farm_MVP_DeXuat_Duyet_Lead.docx` — bản đề xuất thống nhất để Lead duyệt: thu hẹp phạm vi demo, LangGraph, schema dữ liệu, khung repo.
- `mobile/README.md` — app Expo cho nông dân: kiến trúc, chạy trên điện thoại thật, đóng gói APK; kế hoạch, đặc tả và tổng quan thay đổi (hành vi cũ/mới) ở `development/docs/pending_docs/mobile/`.
- `ai/README.md` — kiến trúc, endpoint, cấu hình và cách vận hành AI Service; các README
  trong `ai/app/` mô tả CV, RAG và LangGraph.
- `development/docs/PARCEL_SEASON_CODES.md` — quy chuẩn mã thửa, mã vụ, GPS và quy tắc duyệt thửa/vụ.
- `development/docs/legacy_docs/AI_EVENT_SCHEMA_README.md` — contract event/API giữa Backend và AI Service.
- `development/docs/improvement_docs/` — danh sách vấn đề cần cải thiện (`PROJECT_IMPROVEMENTS.md`), template và kế hoạch chia phase để implement.
- `development/docs/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf` — đề thi tuyển chọn đội tuyển PMNM HaUI 2026.
- Chủ đề chính thức: https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html
- Giáo trình DX-OS: https://opendigitransform.gitbook.io/dx-os

## Kiến trúc dịch vụ (tóm tắt)

Các client (web, mobile) chỉ gọi một cửa chung là gateway APISIX (cổng `9080`), gateway chuyển tới Backend, Keycloak, MinIO và web.
Sơ đồ 3 tầng Open-Core (Core dùng chung, H-P-D-I, ứng dụng DX-Farm) và sơ đồ luồng request ở [`development/docs/ARCHITECTURE_OPEN_CORE.md`](./development/docs/ARCHITECTURE_OPEN_CORE.md).
Core nằm ở các package `core.identity`, `core.workflow`, `core.storage` của Backend; đổi ứng dụng chỉ thêm package ứng dụng mới, Core không đổi.

Hai service tách biệt, giao tiếp qua HTTP:

- **Backend (Java/Spring Boot)** — sở hữu Single Source of Truth: API nghiệp vụ, PostgreSQL (Operational Data + Structured Knowledge), state machine duyệt (Approve/Reject), enforce poka-yoke (khoá PHI/thu hoạch), auth.
  Đây là nơi mọi ghi nhận cuối cùng xảy ra.
  Role `FARMER` chỉ thấy và thao tác trên thửa có `farmerName` khớp tên trong token (thửa FARMER tạo luôn mang tên họ) và chỉ nhận nội dung chẩn đoán khi đã `APPROVED`.
- **Gateway (APISIX standalone)** — cửa duy nhất publish ra host: kiểm JWT Keycloak lần đầu, giới hạn tốc độ upload ảnh và gọi AI, route `/api`, `/realms`, link ảnh có hạn và web.
  Cổng của Backend, Keycloak, AI và MinIO không publish ra host; Backend vẫn tự kiểm JWT lần hai.
- **Workflow dùng chung** — mỗi chẩn đoán có một `workflow_case` (trạng thái, người phụ trách) và các `workflow_event` chỉ thêm, không sửa, nên xem lại được toàn bộ lịch sử duyệt.
  `GET /api/v1/workflow-cases` trả danh sách việc chờ cho kỹ sư và admin.
- **Hộp thông báo** — Backend ghi thông báo cho nông dân khi duyệt thửa, vụ, chẩn đoán, khai phun thuốc và khi đến hạn thu hoạch.
  Mobile đọc qua `GET /api/v1/notifications`, trạng thái đã đọc lưu ở server.
  Job nhắc hạn chạy 07:00 giờ Việt Nam mỗi ngày (`HARVEST_REMINDER_CRON`).
- **Thu hoạch và khai phun thuốc** — thu hoạch ghi ngày, sản lượng, diện tích và người khai; thu hoạch sớm cần xác nhận kèm lý do.
  Nông dân khai phun thuốc, kỹ sư xác nhận; bản khai chờ xác nhận chặn thu hoạch.
- **Lưu ảnh (MinIO)** — ảnh báo cáo nằm trong bucket riêng tư, client nhận link có hạn (mặc định 15 phút) trong trường `imageUrl`; không còn đường dẫn ảnh công khai.
- **AI Service (Python)** — Vision API, LangGraph, Qwen2.5 qua Ollama và RAG; hoạt động như dịch vụ suy luận stateless.
  AI gọi internal REST API của Backend để lấy structured facts, chỉ đọc AI Vector DB riêng và trả recommendation về Backend.
- **Mobile (Expo/React Native)** — client dành riêng cho FARMER: đăng nhập Keycloak (PKCE), xem thửa/vụ/báo cáo, chụp và gửi ảnh sâu bệnh, gửi lại tự động khi mất mạng.
  Chỉ gọi API của Backend, không truy cập trực tiếp dịch vụ nào khác.
- **Giao tiếp** — hai service gọi nhau qua HTTP với service API key riêng cho từng chiều; AI không có credential truy cập Backend DB.
- **Thông báo** — Backend post một chiều sang Mattermost qua Incoming Webhook (báo cáo mới, chẩn đoán chờ duyệt); đây là kênh phụ trợ best-effort, dữ liệu và trạng thái duyệt vẫn nằm trong Backend.

## Nguyên tắc kiến trúc (bất biến qua mọi giai đoạn)

1. **Deterministic-first**: số liệu kỹ thuật (thuốc, liều lượng, PHI, công thức carbon) luôn lấy từ dữ liệu có cấu trúc / lookup xác định — LLM không được tự sinh con số.
2. **Human-in-the-loop**: mọi khuyến nghị có thuốc phải qua kỹ sư nông nghiệp phê duyệt trước khi thành hành động.
3. **Single Source of Truth**: PostgreSQL là nơi lưu "chuyện gì đã xảy ra"; RAG chỉ dùng để giải thích, không thay thế truy vấn có cấu trúc.
4. **Poka-Yoke defense-in-depth**: ràng buộc chặn ở cả tầng backend (API guard) lẫn frontend (disable UI) — không chỉ ẩn nút bấm.
5. **Tuyến tính H→P→D→I**: không nhảy cóc sang tự động hoá [I] khi [P]/[D] chưa chuẩn hoá.

## Chiến lược triển khai: tối giản trước, mở rộng sau

Ưu tiên dựng cho được **một luồng xương sống (spine flow) chạy thật đầu-cuối**, sau đó mới bồi thêm chiều rộng (đủ 4 không gian) và chiều sâu (carbon, RAG, multi-agent...).

### Giai đoạn 0 — Spine Flow tối thiểu (mục tiêu demo đầu tiên)

Luồng: nông dân báo bệnh bằng ảnh → CV chẩn đoán → tra cứu quy tắc xử lý (deterministic) → LLM diễn đạt khuyến nghị → kỹ sư duyệt → ghi nhận can thiệp + khoá thu hoạch theo PHI.

- [x] M1 — Chọn thửa ruộng/vụ mùa, gửi ảnh bệnh
- [x] M2 — Vision API trả `disease_code/confidence/model_version`, lưu lại được
- [x] M3 — Lookup Disease → TreatmentRule → Pesticide deterministic (SQL/JSON, không qua LLM)
- [x] M4 — LLM chỉ diễn đạt lại facts đã tra được, không tự sinh số liệu
- [x] M5 — Kỹ sư Approve/Reject trong Backend; AI chỉ trả recommendation và không tự quản lý trạng thái duyệt.
- [x] M6 — APPLIED là bước xác nhận riêng, tạo snapshot `TreatmentApplication`, tính `harvest_locked_until` từ thời điểm phun và không cho lần ghi sau rút ngắn khoá PHI.

Ngăn xếp tối thiểu cho giai đoạn này: PostgreSQL, backend Spring Boot (API nghiệp vụ, duyệt, poka-yoke), AI Service Python riêng (vision-api YOLOv8n + LangGraph điều phối + LLM nhỏ Qwen2.5-1.5B qua Ollama) gọi qua HTTP.
Chưa cần SSO, chưa cần BI dashboard, chưa cần carbon/IoT.

**Phase 0 đã nối end-to-end qua contract Backend ↔ AI:**

- [x] Backend gọi `POST /api/v1/diagnose`; AI gọi internal API Backend để lấy structured facts.

#### Tiến độ AI Service

- [x] Vision API chuẩn hóa `disease_code`, confidence và metadata model cho 6 điều kiện lúa: 5 lớp detection và một mã healthy.
- [x] Backend REST client chỉ đọc lấy structured facts; AI không truy cập trực tiếp Backend DB.
- [x] RAG dùng AI Vector DB riêng với runtime read-only và job ingest có credential ghi riêng.
- [x] LangGraph điều phối CV → structured facts → RAG → recommendation theo contract stateless `POST /api/v1/diagnose`.
- [x] Backend và AI giao tiếp qua HTTP với service API key, timeout và retry giới hạn.

### Giai đoạn 1 — Hoàn thiện trải nghiệm & truy vấn (Should)

- [x] M7 — Trả lời được câu hỏi kiểu "ruộng này còn cách ly không?" bằng SQL context
- [ ] M8 — RAG trả lời có trích nguồn từ ít nhất một bộ tài liệu VietGAP/IRRI đã ingest (pgvector)
- [ ] Frontend low-code thật cho nông hộ (ToolJet/Appsmith) thay vì gọi API tay

### Giai đoạn 2 — Lấp đầy đủ 4 không gian H-P-D-I

Đây là phần **cần bổ sung so với bản đề xuất thống nhất hiện tại**, vì đề bài OLP yêu cầu tường minh đủ 4 nhóm công cụ:

- [x] **[H]** Một SSO nhẹ (Keycloak) + một kênh chat nội bộ (Mattermost) chạy thật trong docker-compose, không chỉ nằm trên giấy
  - [x] SSO thật: Identity Provider Keycloak chạy trong docker-compose.
    Backend xác thực bằng OAuth2 Resource Server, frontend và BI Dashboard (Superset) đăng nhập bằng redirect sang Keycloak.
    Chi tiết: [`sso/README.md`](./sso/README.md).
  - [x] Bước đệm trước SSO: trang portal tĩnh liệt kê link tới các app (App chính, BI, Chat) — chưa đăng nhập chung được, nhưng người dùng không cần tự nhớ URL từng app.
    Chi tiết: [`portal/README.md`](./portal/README.md).
  - [x] Kênh chat nội bộ (Mattermost) — chạy thật trong docker-compose (image Team Edition chính thức), nền tảng tách biệt hoàn toàn có chủ đích, không dùng SSO chung với Keycloak.
    Chi tiết: [`chat/README.md`](./chat/README.md).
  - [x] Thông báo tự động vào channel Mattermost: backend post qua Incoming Webhook khi có `DiseaseReport` mới hoặc chẩn đoán chờ duyệt; best-effort, tắt khi chưa cấu hình `MATTERMOST_WEBHOOK_URL`.
    Cách cấp webhook: [`chat/README.md`](./chat/README.md).
- [x] **[D]** Dashboard BI (Apache Superset) hiển thị bản đồ/trạng thái thửa ruộng cho Ban quản trị, tự bootstrap qua docker-compose.
  Chi tiết: [`bi/README.md`](./bi/README.md).
- [x] Rà lại license mọi thành phần open-core trước khi khoá stack (xem mục PoF bên dưới) — xem [`LICENSES.md`](./LICENSES.md).

### Giai đoạn 3 — Chiều sâu (Later, sau khi Giai đoạn 0-2 chạy ổn)

- [ ] Carbon/AWD Estimator hoàn chỉnh + IoT simulator (telemetry mực nước)
- [ ] GraphRAG / multi-agent / workflow phức tạp
- [ ] Tự động xác nhận đã phun từ IoT thay vì xác nhận thủ công

## Kiến trúc dữ liệu (tóm tắt)

Hai database vật lý riêng trên cùng một Postgres instance:

- **Backend DB (`dxfarm`)** — Operational Data (Parcel, CropSeason, DiseaseReport, Diagnosis, TreatmentApplication) và Structured Knowledge (Disease, Pesticide, TreatmentRule; DiseaseManagementPolicy).
- **AI Vector DB (`dx_farm_ai_vector_db`)** — chỉ `documents`/`document_chunks` (RAG Knowledge, qua pgvector).
  Backend tự tạo DB này cùng 2 role Postgres riêng cho AI (`ai_vector_ro` chỉ-đọc cho runtime, `ai_vector_ingest` đọc/ghi cho job ingest) lúc khởi động; không role nào của AI được cấp quyền vào Backend DB (Phase 3, P3-1..P3-3 đã xong).

### Quy ước seed dữ liệu

Chỉ có hai cơ chế seed, không thêm cơ chế thứ ba:

| Loại dữ liệu | Cơ chế |
|---|---|
| Nghiệp vụ do JPA quản lý (`diseases`, `pesticides`, `treatment_rules`, `disease_management_policies`) | `backend/DX-Farm/src/main/resources/data.sql`, sinh từ `data/structured/*.json` bằng `python3 data/scripts/generate_seed_sql.py`; commit file sinh ra và review diff qua PR |
| Hạ tầng Postgres (role, database, extension) | `CommandLineRunner` kiểu `*Seeder` trong `backend/DX-Farm/.../config` |

- `data.sql` không sửa tay; đổi dữ liệu ở `data/` rồi chạy lại generator.
- Chỉ record có cờ eligibility `true` được seed; generator in danh sách treatment rule bị chặn kèm lý do.
- Backend không đọc `data/` lúc runtime; `data.sql` chạy idempotent mỗi lần start (`ON CONFLICT` trên khoá duy nhất của DB).


Dữ liệu demo cũ do AI ghi trực tiếp trước khi tách DB (`disease_reports`, `diagnoses`, `treatment_applications`, `workflow_cases`/`workflow_events`) là dữ liệu demo/test, không được giữ lại.

## Điểm cần lưu ý cho tiêu chí PoF (50/100 điểm, mang tính loại trừ)

- Giấy phép mọi thành phần phải **OSI-approved**; đã loại Llama (không đạt chuẩn OSD), chọn Qwen2.5-1.5B-Instruct (Apache-2.0).
- ✅ Không dùng n8n (Sustainable Use License, không OSI-approved) — LangGraph (Apache-2.0) là orchestrator.
  Bảng rà soát license và các hạng mục còn thiếu: [`LICENSES.md`](./LICENSES.md).
- Checkpoint CV, Ultralytics và PyMuPDF của AI Service được phân phối theo AGPL-3.0;
  dataset RiceLeafDiseaseBD Version 3 theo CC BY 4.0. Xem
  [`LICENSES.md`](./LICENSES.md) và [`ai/MODEL_LICENSE`](./ai/MODEL_LICENSE).
- Mã nguồn tự viết: SPDX header `Apache-2.0` ở đầu mỗi file, kèm `LICENSE` toàn văn ở gốc repo.
- Source control phải public, có web viewer.
- Phải build/cài đặt được từ source; khuyến khích Docker.
- Không sửa mã thư viện đính kèm; có README, Changelog, bug tracker.
- Gói phát hành dùng `.tar.gz`, không dùng `.zip`/`.rar`.
- Công thức carbon: định danh là "Mô-đun Ước Lượng Giảm Phát Thải Tham Chiếu Thực Nghiệm", trích nguồn IRRI/SECTOR + Đề án 1 triệu hecta, tuyên bố rõ không thay thế kiểm toán thực địa (phòng thủ học thuật).

## Việc cần chốt cùng Lead trước khi chia task

- [x] Phạm vi demo: chỉ spine flow (Giai đoạn 0) hay bao gồm cả [H]/[D] tối thiểu (Giai đoạn 2)?
- [x] Quyết định dứt khoát về n8n (giữ/bỏ) vì lý do license — đã bỏ, xem `LICENSES.md`
- [x] Schema operational + structured knowledge (entity, field bắt buộc)
- [x] Contract `vision-api` (request/response JSON)
- [x] Contract giữa Backend Spring Boot và AI Service Python đã chốt: REST nội bộ, service API key hai chiều, timeout/retry giới hạn và khoảng liều `dose_min`/`dose_max` thống nhất.
  Contract hiện hành được mô tả tại [`AI_EVENT_SCHEMA_README.md`](./development/docs/legacy_docs/AI_EVENT_SCHEMA_README.md) và khóa bằng DTO/schema trong code.
- [x] Thời điểm PHI bắt đầu: khi `TreatmentApplication = APPLIED` (không phải khi Approve)
- [x] Cấu trúc monorepo và quyền sở hữu từng thư mục/module
- [x] Chính sách repo Private trong lúc phát triển, thời điểm public trước khi nộp bài

## Trạng thái hiện tại (cập nhật 2026-10-06)

Backend Spring Boot sở hữu dữ liệu nghiệp vụ, structured knowledge và toàn bộ vòng đời phê duyệt; đồng thời cung cấp internal API chỉ đọc cho AI.
AI Service hoạt động stateless: kết hợp CV, structured facts từ Backend và RAG evidence, sau đó trả recommendation để Backend kiểm tra và lưu.
AI Vector DB đã tách khỏi Backend DB; runtime dùng role chỉ đọc, còn job ingest dùng role đọc/ghi riêng.
Ba phase tách AI/Backend đã hoàn thành; trạng thái cuối được phản ánh trực tiếp trong code, README và CHANGELOG.
Frontend, Keycloak SSO, Superset, Mattermost và portal tiếp tục dùng chung stack Docker Compose.
Web, API, Keycloak, ảnh và portal đi qua gateway APISIX ở `http://localhost:9080`, Superset ở listener thứ hai `http://localhost:9081` của cùng gateway; Backend, Keycloak, AI, MinIO, Superset và portal không publish cổng ra host.
Duyệt hoặc từ chối chẩn đoán ghi một sự kiện vào `workflow_event` (không ghi đè), mỗi chẩn đoán có người phụ trách và danh sách việc chờ qua `GET /api/v1/workflow-cases`.
Ảnh báo cáo lưu ở MinIO (build từ source vì image chính thức không còn phát hành) và chỉ xem được qua link có hạn.
Backend post thông báo báo cáo mới và chẩn đoán chờ duyệt vào channel Mattermost `Kỹ sư nông nghiệp` qua Incoming Webhook khi `MATTERMOST_WEBHOOK_URL` được cấu hình.
Keycloak không cố định hostname nên web (`localhost`) và điện thoại (IP LAN) đăng nhập cùng lúc mà không cần đổi `deploy/.env`.
Bản mobile Expo cho FARMER đã có luồng đầu-cuối (đăng nhập, xem thửa/vụ/báo cáo, chụp và gửi, hàng đợi offline); hướng dẫn chạy và đóng gói APK ở [`mobile/README.md`](./mobile/README.md), kế hoạch ở [`development/docs/pending_docs/mobile/PLAN.md`](./development/docs/pending_docs/mobile/PLAN.md).
Khóa PHI chặn ngày phun ngoài khoảng 30 ngày gần nhất, mỗi thửa chỉ có một vụ đang canh tác, mọi endpoint AI (trừ health) cần service key, ảnh upload được giải mã lại và không còn EXIF/GPS, mật khẩu hệ thống không còn giá trị mặc định.
Quyền của nông dân dựa trên nông hộ gắn với tài khoản (claim `sub`), schema do Flyway quản lý, ma trận quyền theo role được áp bằng `@PreAuthorize`, nông dân chỉ dùng app mobile (web và Superset từ chối), admin tạo nông hộ kèm tài khoản qua `POST /api/v1/households`.
CI chạy riêng cho backend (kèm test), frontend, mobile, AI và hạ tầng (quét secret, kiểm tra compose).
Chi tiết thay đổi theo từng module xem tại [`CHANGELOG.md`](./CHANGELOG.md).

## Chạy demo Phase 0

### 1. Khởi động (một lệnh duy nhất, dựng toàn bộ Postgres + Keycloak + backend + AI (kèm Ollama và nạp RAG) + frontend + BI + chat + portal)

```bash
cd deploy
cp .env.example .env      # điền mọi biến đang để trống trong .env (bắt buộc)
docker compose up -d --build
```

### 2. Vào test

| Service | Vào đâu | Đăng nhập |
|---|---|---|
| **Portal** (link tới các app, trang quản trị và tài liệu API, qua gateway) | http://localhost:9080/portal/ | Không cần đăng nhập — chỉ là trang link |
| **App chính** (frontend, qua gateway) | http://localhost:9080 | Bấm "Đăng nhập" → redirect sang Keycloak, dùng tài khoản demo (xem [`sso/README.md`](./sso/README.md)) |
| **Keycloak Admin Console** (qua gateway) | http://localhost:9080/admin | `KEYCLOAK_ADMIN_USERNAME`/`KEYCLOAK_ADMIN_PASSWORD` trong `.env` |
| **Backend** (Swagger, gọi API tay, qua gateway) | http://localhost:9080/swagger-ui.html | Lấy access token từ Keycloak (xem `sso/README.md`), bấm "Authorize" rồi dán vào |
| **BI Dashboard** (Superset, listener thứ hai của gateway) | http://localhost:9081 | Bấm "Sign In with keycloak", dùng tài khoản demo (xem [`sso/README.md`](./sso/README.md)); tài khoản role `ADMIN` vào được trang quản trị `/users/list/` |
| **Quản trị lưu ảnh** (MinIO Console, listener thứ ba của gateway) | http://localhost:9082 | Tài khoản gốc `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` trong `.env`; xem bucket và ảnh báo cáo |
| **Chat nội bộ** (Mattermost) | http://localhost:8065 | Tài khoản System Admin có sẵn: `MATTERMOST_ADMIN_USERNAME`/`MATTERMOST_ADMIN_PASSWORD` trong `.env`; team `DX-Farm HTX` và 3 channel đã được tạo sẵn; muốn nhận thông báo từ Backend thì cấp webhook theo [`chat/README.md`](./chat/README.md) |

### Đăng nhập vào từng service

Mỗi service có hai kiểu tài khoản: tài khoản của ứng dụng (engineer, admin trong Keycloak) và tài khoản quản trị riêng của chính service đó.

| Service | Engineer / admin của ứng dụng | Quản trị riêng của service |
|---|---|---|
| App chính (web) | SSO Keycloak: `engineer1` và `admin1` | Không có, `admin1` là quản trị của ứng dụng |
| Keycloak | Không vào được Admin Console | `KEYCLOAK_ADMIN_USERNAME`/`KEYCLOAK_ADMIN_PASSWORD` (realm `master`) |
| Superset (BI) | SSO Keycloak: `engineer1` vào với role Gamma, `admin1` với role Admin (kể cả trang `/users/list/`) | Tài khoản `SUPERSET_ADMIN_*` chỉ dùng cho script bootstrap qua REST, giao diện chỉ có nút đăng nhập Keycloak |
| MinIO Console | Không dùng SSO | `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` |
| Mattermost | Tài khoản Mattermost riêng, không dùng SSO | `MATTERMOST_ADMIN_USERNAME`/`MATTERMOST_ADMIN_PASSWORD` |
| Swagger (API) | Lấy access token của `engineer1` hoặc `admin1`, bấm Authorize | Không có |
| Portal | Không cần đăng nhập | Không có |

### Tài khoản demo

Realm Keycloak tạo sẵn 3 tài khoản **chỉ phục vụ demo và chấm thi**; đổi mật khẩu (hoặc xoá tài khoản) trước khi dùng cho dữ liệu thật.

| Username | Mật khẩu | Role |
|---|---|---|
| `farmer1` | `farmer123` | FARMER |
| `engineer1` | `engineer123` | ENGINEER |
| `admin1` | `admin123` | ADMIN |

Các tài khoản demo chưa gắn nông hộ: `farmer1` chưa thấy thửa nào cho tới khi admin gắn vào một hộ (`PATCH /api/v1/households/{code}/account`) hoặc tạo hộ mới kèm tài khoản (`POST /api/v1/households`). Web chỉ dành cho kỹ sư và quản trị viên; tài khoản `farmer1` dùng app mobile.

Mật khẩu hệ thống (Postgres, Keycloak admin, Superset, Mattermost, API key nội bộ) không có giá trị mặc định: thiếu biến nào trong `deploy/.env` thì `docker compose` dừng và báo tên biến đó.

### 3. Dừng lại

```bash
docker compose down
```

### 4. Dựng lại từ đầu

```bash
cd deploy
./reset.sh            # hỏi xác nhận, thêm --yes để bỏ qua
```

Script dừng container, **xoá toàn bộ `deploy/data/`** (database, ảnh upload, trạng thái Superset và Mattermost), rồi `docker compose up -d --build` và chờ mọi service sẵn sàng (healthy, hoặc job một lần đã kết thúc thành công).
Model Ollama nằm ở volume Docker nên không bị tải lại.
Đây là thao tác phá hủy dữ liệu local, không chạy trong CI.
Muốn chỉ dựng một phần: `./reset.sh --yes postgres backend keycloak`.

<details>
<summary>Ghi chú thêm (chỉ cần khi cần, không bắt buộc đọc để test)</summary>

- Quản lý tài khoản (tạo/xoá/đổi role FARMER/ENGINEER/ADMIN): qua Keycloak Admin Console.
  Xem [`sso/README.md`](./sso/README.md).
- Dữ liệu Postgres, Mattermost, Superset và ảnh upload (MinIO) lưu ở `deploy/data/` (bind mount, không mất khi `docker compose down`); model Ollama và cache embedding của AI nằm ở volume Docker.
  Dựng lại từ đầu bằng `deploy/reset.sh` (xem mục "Dựng lại từ đầu" bên dưới).
- Chi tiết riêng từng service: [`mobile/README.md`](./mobile/README.md) (app mobile), [`sso/README.md`](./sso/README.md) (Keycloak), [`bi/README.md`](./bi/README.md) (Superset), [`minio/README.md`](./minio/README.md) (lưu ảnh), [`chat/README.md`](./chat/README.md) (Mattermost), [`portal/README.md`](./portal/README.md) (trang portal).
- Test backend: `./mvnw test` trong `backend/DX-Farm`; cần Docker vì có test chạy PostgreSQL qua Testcontainers.
- Backend log mức `INFO`; đặt `LOG_LEVEL_APP=DEBUG` khi cần chi tiết (không nằm trong `deploy/.env.example`).
- Cổng AI (8000), Backend (8081), Keycloak (8080), MinIO (9000), Superset (8088) và portal (80) không publish ra host; chỉ gateway (`APISIX_HOST_PORT` mặc định `9080`, `APISIX_SUPERSET_HOST_PORT` mặc định `9081`) và Mattermost mở ra ngoài, Backend gọi AI qua mạng compose.
- Lần dựng đầu build MinIO từ source Go (khoảng 2 phút) vì image chính thức không còn phát hành.
- Ảnh cũ trong `deploy/data/uploads` chuyển vào MinIO bằng `minio/migrate-uploads.sh` (không xoá thư mục cũ, chỉ so số lượng).
- Sửa code + hot reload thay vì rebuild image: chạy `docker compose up -d postgres` trong `deploy` để có sẵn Postgres, rồi `./mvnw spring-boot:run` (backend) và `VITE_KEYCLOAK_URL=http://localhost:9080/realms/dxfarm npm run dev` (frontend) như bình thường.
  Mọi file `.env` chỉ nằm ở `deploy/`.

</details>
