# DX-Farm

MVP dự thi **OLP Phần mềm nguồn mở 2026** (khối thi DX-OS, VFOSSA). Bài toán được chọn: **Agri-Carbon DX-OS** — hệ điều hành chuyển đổi số nội bộ cho một Hợp tác xã Nông nghiệp Số chuyên canh lúa nước (ĐBSCL), triển khai theo kiến trúc 4 không gian **H-P-D-I** (Human, Process, Data, Intelligence).

> Tài liệu này là bản tổng quan để làm ngữ cảnh cho các phiên làm việc sau — không đi sâu chi tiết kỹ thuật. Chi tiết triển khai từng module sẽ được đào sâu dần trong các lần làm việc tiếp theo.

> Tiến trình implement từng module (đã xong gì, chưa làm gì) được ghi lại ở [`CHANGELOG.md`](./CHANGELOG.md).

> Cách khởi động PostgreSQL, kết nối database và sử dụng Alembic được hướng dẫn tại [`development/docs/README_POSTGRESQL.md`](./development/docs/README_POSTGRESQL.md).

## Nguồn tài liệu

- `development/docs/DX - Farm.docx` — thiết kế gốc "MVP Agri-Carbon DX-OS" (8 trang): phân vai tác nhân, 4 không gian HPDI, spine flow 6 bước, các lưu ý PoF/anti-hallucination/poka-yoke.
- `development/docs/DX-Farm_MVP_DeXuat_Duyet_Lead.docx` — bản đề xuất thống nhất để Lead duyệt: thu hẹp phạm vi demo, LangGraph, schema dữ liệu, khung repo.
- `development/docs/AI_README_LangGraph_MVP.md` — cấu trúc chuẩn của module AI, ranh giới CV/Data/RAG và khung LangGraph MVP.
- `development/docs/AI_EVENT_SCHEMA_README.md` — contract event/API để Backend và Frontend nối
  với AI Service.
- Chủ đề chính thức: https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html
- Giáo trình DX-OS: https://opendigitransform.gitbook.io/dx-os

## Kiến trúc dịch vụ (tóm tắt)

Hai service tách biệt, giao tiếp qua HTTP:

- **Backend (Java/Spring Boot)** — sở hữu Single Source of Truth: API nghiệp vụ, PostgreSQL (Operational Data + Structured Knowledge), state machine duyệt (Approve/Reject), enforce poka-yoke (khoá PHI/thu hoạch), auth. Đây là nơi mọi ghi nhận cuối cùng xảy ra.
- **AI Service (Python)** — vision-api (CV chẩn đoán bệnh), LangGraph điều phối lookup + sinh
  khuyến nghị, LLM nhỏ (Qwen2.5-1.5B qua Ollama) và RAG. Workflow AI lưu trạng thái/audit
  PENDING → APPROVED/REJECTED → APPLIED trong PostgreSQL để chạy demo độc lập; mọi quyết định
  thuốc vẫn cần tác nhân kỹ sư và xác nhận APPLIED riêng.
- Backend gọi AI Service qua contract API (xem mục "Việc cần chốt cùng Lead"); AI Service không truy cập trực tiếp PostgreSQL nghiệp vụ trừ khi cần đọc RAG Knowledge (pgvector) riêng.

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
- [x] M2 — Vision API trả `disease_id/confidence/model_version`, lưu lại được
- [x] M3 — Lookup Disease → TreatmentRule → Pesticide deterministic (SQL/JSON, không qua LLM)
- [x] M4 — LLM chỉ diễn đạt lại facts đã tra được, không tự sinh số liệu
- [x] M5 — Kỹ sư Approve/Reject trong flow; AI API lưu actor/audit nhưng không tự duyệt.
- [x] M6 — APPLIED là bước xác nhận riêng, tạo snapshot `TreatmentApplication`, tính
  `harvest_locked_until` từ thời điểm phun và không cho lần ghi sau rút ngắn khoá PHI.

Ngăn xếp tối thiểu cho giai đoạn này: PostgreSQL, backend Spring Boot (API nghiệp vụ, duyệt, poka-yoke), AI Service Python riêng (vision-api EfficientNet-B4 + LangGraph điều phối + LLM nhỏ Qwen2.5-1.5B qua Ollama) gọi qua HTTP. Chưa cần SSO, chưa cần BI dashboard, chưa cần carbon/IoT.

**Còn thiếu để Phase 0 chạy thật end-to-end:**

- [x] Khởi tạo nền tảng dữ liệu Python: SQLAlchemy, Alembic, PostgreSQL và pgvector.
- [x] Hoàn thiện AI Service Python thống nhất: Vision API, structured lookup, RAG, LangGraph,
  durable HITL và PHI chạy trong luồng tích hợp local.
- [ ] Nối HTTP client backend ↔ AI Service theo contract API.
- [x] Khớp dữ liệu CV chẩn đoán: đã chuẩn hóa Vision API trả `disease_code` và `disease_name` tiếng Việt khớp 1:1 với `Disease.code` trong DB.
- [x] Tích hợp lớp wording vào spine flow; nếu Ollama lỗi/timeout thì trả evidence nguyên văn,
  không thay đổi thuốc, hoạt chất, liều hoặc PHI từ SQL.

#### Phần AI/data/RAG đã có trên `feature/make-data`

- Data layer cho 8 entity spine-flow và 2 entity RAG (`Document`, `DocumentChunk` với
  `vector(384)`, đồng bộ model embedding multilingual MiniLM).
- Dataset có cấu trúc gồm 38 lớp bệnh/healthy được map nguyên văn từ sheet `Disease_KB`:
  `class_name → code`, `disease_name → name`, `symptoms → description`; không trùng mã.
- Alembic head `f0a1b2026011` gồm metadata CV/RAG, workflow case/event bền vững, snapshot
  TreatmentApplication và unique sequence cho audit event.
- Stage 7 đã embedding/ingest rich knowledge: 38 document / 270 chunk, registry 41 nguồn.
  Đã chạy hai lần và kiểm thử rollback, không nhân đôi corpus.
- Gói dữ liệu Stage 3 đã qua review cung cấp bộ seed cuối gồm 4 thuốc, 8 treatment rule và 21
  disease-management policy. Chỉ 8 candidate có quyết định `APPROVE/READY_TO_PROMOTE`
  được đưa vào bộ cuối; 8 candidate `HOLD/NOT_READY` được giữ riêng để audit và không seed.
- Stage 4 đã hoàn thiện schema/repository; ba seeder Stage 5 chạy idempotent. Database local
  đã được migrate và xác minh đúng
  revision/count `f0a1b2026011` / `4` / `8` / `21`. Workbook review chỉ là nguồn đánh giá
  kỹ thuật MVP, không phải quyền cho phép sử dụng ngoài thực địa.
- Stage 6 có contract 38 lớp canonical, predict/health API, endpoint chẩn đoán report:
  lưu Diagnosis/confidence/model metadata, trả SQL rule hoặc safe policy, yêu cầu kỹ sư duyệt.
  Checkpoint thiếu class_to_idx nên thứ tự được đối chiếu với mã training, chưa xác minh độc lập.
- Stage 7 có retrieval API, exact disease filter, top_k, provenance và atomic re-ingest.
  RAG answer ở chế độ grounded_excerpt, không phát hành draft bịa structured facts.
- Stage 8–10 đã có graph đến checkpoint PENDING, API approve/reject/applied, audit trail,
  idempotency, dose guardrail, snapshot rule/PHI và khoá thu hoạch lấy giá trị lớn nhất.
- Kiểm thử local **59 passed**, gồm checkpoint CV thật, FastAPI lifespan, PostgreSQL/pgvector,
  E2E treatment/policy/reject/low-confidence và fresh-database migration. Smoke MVP với model
  thật cũng đã PASS; Ollama không chạy vẫn về `evidence_fallback` an toàn.
  Xem [checklist Stage 6–10](development/docs/AI_README_LangGraph_MVP.md#master-stage-roadmap),
  [CV handoff](ai/app/cv/README.md) và [RAG handoff](ai/app/rag/README.md).
- Chưa nối HTTP client backend ↔ AI, chưa có auth riêng cho transition API AI, chưa thử venv
  trắng, chưa đánh giá accuracy CV độc lập hoặc benchmark live Ollama. M8 vẫn cần nghiệm thu
  đúng bộ tài liệu VietGAP/IRRI và luồng hỏi đáp yêu cầu bên dưới.

### Giai đoạn 1 — Hoàn thiện trải nghiệm & truy vấn (Should)

- [x] M7 — Trả lời được câu hỏi kiểu "ruộng này còn cách ly không?" bằng SQL context
- [ ] M8 — RAG trả lời có trích nguồn từ ít nhất một bộ tài liệu VietGAP/IRRI đã ingest (pgvector)
- [ ] Frontend low-code thật cho nông hộ (ToolJet/Appsmith) thay vì gọi API tay

### Giai đoạn 2 — Lấp đầy đủ 4 không gian H-P-D-I

Đây là phần **cần bổ sung so với bản đề xuất thống nhất hiện tại**, vì đề bài OLP yêu cầu tường minh đủ 4 nhóm công cụ:

- [ ] **[H]** Một SSO nhẹ (Keycloak/Authentik) + một kênh chat nội bộ (Mattermost) chạy thật trong docker-compose, không chỉ nằm trên giấy
  - [x] Bước đệm đã xong: đăng nhập JWT + phân quyền FARMER/ENGINEER/ADMIN tự viết trong Spring
    Boot (thay các field tên tự do như `reportedBy`/`approvedBy`) — đây **chưa phải SSO thật**,
    chỉ là auth cục bộ riêng cho app này, mỗi user/mật khẩu do chính backend tự quản lý
  - [ ] SSO thật: dựng thêm một Identity Provider riêng (Keycloak/Authentik) chạy trong
    docker-compose, để sau này có thêm app khác (Mattermost, BI dashboard) thì đăng nhập 1 lần
    dùng chung được, thay vì mỗi app một tài khoản như hiện tại
  - [ ] Kênh chat nội bộ (Mattermost) — chưa bắt đầu
- [ ] **[D]** Một dashboard BI tối thiểu (Superset/Metabase) hiển thị bản đồ/trạng thái thửa ruộng cho Ban quản trị — không dừng ở truy vấn SQL thuần
- [ ] Rà lại license mọi thành phần open-core trước khi khoá stack (xem mục PoF bên dưới)

### Giai đoạn 3 — Chiều sâu (Later, sau khi Giai đoạn 0-2 chạy ổn)

- [ ] Carbon/AWD Estimator hoàn chỉnh + IoT simulator (telemetry mực nước)
- [ ] GraphRAG / multi-agent / workflow phức tạp
- [ ] Tự động xác nhận đã phun từ IoT thay vì xác nhận thủ công

## Kiến trúc dữ liệu (tóm tắt)

Một PostgreSQL vật lý, ba nguồn logic:

- **Operational Data**: Parcel, CropSeason, DiseaseReport, Diagnosis, TreatmentApplication — "chuyện gì đã xảy ra"
- **Structured Knowledge**: Disease, Pesticide, TreatmentRule, DiseaseManagementPolicy — "quy tắc là gì" (lookup bằng khoá/SQL, không qua LLM)
- **RAG Knowledge**: tài liệu VietGAP/IRRI/nội bộ đã duyệt, qua pgvector — "tài liệu giải thích gì"

## Điểm cần lưu ý cho tiêu chí PoF (50/100 điểm, mang tính loại trừ)

- Giấy phép mọi thành phần phải **OSI-approved**; đã loại Llama (không đạt chuẩn OSD), chọn Qwen2.5-1.5B-Instruct (Apache-2.0).
- ⚠️ **n8n Community Edition dùng Sustainable Use License — không phải OSI-approved.** Nếu còn dùng n8n trong stack, đây là rủi ro trừ điểm PoF nghiêm trọng cần xử lý trước khi khoá kiến trúc (ưu tiên: giữ LangGraph làm orchestrator chính, cân nhắc bỏ hẳn n8n hoặc thay bằng công cụ OSI-approved khác).
- Mã nguồn tự viết: SPDX header `Apache-2.0` ở đầu mỗi file, kèm `LICENSE` toàn văn ở gốc repo.
- Source control phải public, có web viewer.
- Phải build/cài đặt được từ source; khuyến khích Docker.
- Không sửa mã thư viện đính kèm; có README, Changelog, bug tracker.
- Gói phát hành dùng `.tar.gz`, không dùng `.zip`/`.rar`.
- Công thức carbon: định danh là "Mô-đun Ước Lượng Giảm Phát Thải Tham Chiếu Thực Nghiệm", trích nguồn IRRI/SECTOR + Đề án 1 triệu hecta, tuyên bố rõ không thay thế kiểm toán thực địa (phòng thủ học thuật).

## Việc cần chốt cùng Lead trước khi chia task

- [x] Phạm vi demo: chỉ spine flow (Giai đoạn 0) hay bao gồm cả [H]/[D] tối thiểu (Giai đoạn 2)?
- [ ] Quyết định dứt khoát về n8n (giữ/bỏ) vì lý do license
- [x] Schema operational + structured knowledge (entity, field bắt buộc)
- [x] Contract `vision-api` (request/response JSON)
- [ ] Contract giữa backend Spring Boot và AI Service Python (REST, base URL, timeout/retry, ai-service base URL cấu hình ở đâu)
- [x] Thời điểm PHI bắt đầu: khi `TreatmentApplication = APPLIED` (không phải khi Approve)
- [x] Cấu trúc monorepo và quyền sở hữu từng thư mục/module
- [x] Chính sách repo Private trong lúc phát triển, thời điểm public trước khi nộp bài

## Trạng thái hiện tại (cập nhật 2026-09-11)

Backend Spring Boot đã có JWT/phân quyền và M7; Frontend demo đã có màn đăng nhập. Trong phạm
vi `ai/`, Stage 8–10 đã hoàn thiện Vision API, structured lookup, RAG endpoint, LangGraph,
durable HITL/APPLIED/PHI và smoke/test local. Phần còn thiếu để ghép toàn hệ thống là HTTP
client backend ↔ AI, auth cho transition AI hoặc ủy quyền từ backend, và UI gọi flow mới.
Chi tiết theo từng module: [`CHANGELOG.md`](./CHANGELOG.md).

## Chạy demo Phase 0

Đóng gói cả Postgres + backend + frontend vào một lệnh duy nhất, dùng thư mục [`deploy`](./deploy)
— đây là docker-compose file duy nhất trong repo, chạy một chỗ cho tiện:

```bash
cd deploy
cp .env.example .env      # chỉnh lại nếu cần, đặc biệt JWT_SECRET và SUPER_ADMIN_PASSWORD
docker compose up -d --build
```

- Frontend: http://localhost:8082 (nginx serve build tĩnh + reverse-proxy `/api` và `/uploads`
  sang backend, nên không cần CORS, không cần biết host/port backend) — đăng nhập bằng
  `SUPER_ADMIN_USERNAME`/`SUPER_ADMIN_PASSWORD` đã đặt trong `.env` (mặc định seed đúng 1 lần
  lúc khởi động), sau đó dùng tài khoản admin đó gọi `POST /api/v1/users` (qua Swagger hoặc
  curl) để tạo thêm tài khoản `ENGINEER`/`FARMER`.
- Backend (Swagger/debug trực tiếp): http://localhost:8081/swagger-ui.html — bấm "Authorize"
  và dán JWT lấy từ `POST /api/v1/auth/login` để gọi thử các API cần đăng nhập.
- PostgreSQL dùng image `pgvector/pgvector:pg16`; AI Service chưa được đóng gói vào Compose.
- Dữ liệu Postgres + ảnh upload được lưu ở `deploy/data/` (bind mount, không mất khi
  `docker compose down`; dùng `down -v` hoặc xoá thư mục nếu muốn làm sạch từ đầu)
- Dừng: `docker compose down` (chạy trong thư mục `deploy`)

Khi cần sửa code + hot reload thay vì rebuild image: chạy `docker compose up -d postgres` trong
`deploy` để có sẵn Postgres, rồi `./mvnw spring-boot:run` (backend) và `npm run dev` (frontend,
sau khi `cp .env.example .env`) như bình thường — không cần thêm docker-compose nào khác.
