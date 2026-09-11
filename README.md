# DX-Farm

MVP dự thi **OLP Phần mềm nguồn mở 2026** (khối thi DX-OS, VFOSSA). Bài toán được chọn: **Agri-Carbon DX-OS** — hệ điều hành chuyển đổi số nội bộ cho một Hợp tác xã Nông nghiệp Số chuyên canh lúa nước (ĐBSCL), triển khai theo kiến trúc 4 không gian **H-P-D-I** (Human, Process, Data, Intelligence).

> Tài liệu này là bản tổng quan để làm ngữ cảnh cho các phiên làm việc sau — không đi sâu chi tiết kỹ thuật. Chi tiết triển khai từng module sẽ được đào sâu dần trong các lần làm việc tiếp theo.

> Tiến trình implement từng module (đã xong gì, chưa làm gì) được ghi lại ở [`CHANGELOG.md`](./CHANGELOG.md).

## Nguồn tài liệu

- `docs/DX - Farm.docx` — thiết kế gốc "MVP Agri-Carbon DX-OS" (8 trang): phân vai tác nhân, 4 không gian HPDI, spine flow 6 bước, các lưu ý PoF/anti-hallucination/poka-yoke.
- `docs/DX-Farm_MVP_DeXuat_Duyet_Lead.docx` — bản đề xuất thống nhất để Lead duyệt: thu hẹp phạm vi demo, LangGraph, schema dữ liệu, khung repo.
- Chủ đề chính thức: https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html
- Giáo trình DX-OS: https://opendigitransform.gitbook.io/dx-os

## Kiến trúc dịch vụ (tóm tắt)

Hai service tách biệt, giao tiếp qua HTTP:

- **Backend (Java/Spring Boot)** — sở hữu Single Source of Truth: API nghiệp vụ, PostgreSQL (Operational Data + Structured Knowledge), state machine duyệt (Approve/Reject), enforce poka-yoke (khoá PHI/thu hoạch), auth. Đây là nơi mọi ghi nhận cuối cùng xảy ra.
- **AI Service (Python)** — stateless theo hướng advisory: vision-api (CV chẩn đoán bệnh), LangGraph điều phối bước lookup + sinh khuyến nghị, LLM nhỏ (Qwen2.5-1.5B qua Ollama), RAG. Chỉ trả đề xuất/kết quả phân tích; không tự ghi dữ liệu nghiệp vụ, không tự quyết định thay kỹ sư.
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
- [x] M5 — Kỹ sư Approve/Reject trong flow (trạng thái duyệt lưu & kiểm soát ở Spring Boot backend; AI Service chỉ đề xuất, không tự quyết)
- [x] M6 — Khi xác nhận đã phun (APPLIED): backend ghi `TreatmentApplication`, tính `harvest_locked_until`, poka-yoke 2 tầng (Spring Boot API trả 422 + UI disable) chặn thu hoạch sớm

Ngăn xếp tối thiểu cho giai đoạn này: PostgreSQL, backend Spring Boot (API nghiệp vụ, duyệt, poka-yoke), AI Service Python riêng (vision-api MobileNetV3 + LangGraph điều phối + LLM nhỏ Qwen2.5-1.5B qua Ollama) gọi qua HTTP. Chưa cần SSO, chưa cần BI dashboard, chưa cần carbon/IoT.

**Còn thiếu để Phase 0 chạy thật end-to-end:**

- [ ] Hoàn thiện AI Service Python: Đã có vision-api (FastAPI + EfficientNet-B4), còn thiếu LangGraph + LLM Qwen2.5-1.5B qua Ollama.
- [ ] Nối HTTP client backend ↔ AI Service theo contract API.
- [ ] Khớp dữ liệu CV chẩn đoán: model hiện có trả về `class_name` nhưng chưa map `disease_code` với cơ sở dữ liệu của backend.
- [ ] LLM sinh khuyến nghị thật (hiện backend dùng khuyến nghị tiếng Việt dạng template).

### Giai đoạn 1 — Hoàn thiện trải nghiệm & truy vấn (Should)

- [x] M7 — Trả lời được câu hỏi kiểu "ruộng này còn cách ly không?" bằng SQL context
- [ ] M8 — RAG trả lời có trích nguồn từ ít nhất một bộ tài liệu VietGAP/IRRI đã ingest (pgvector)
- [ ] Frontend low-code thật cho nông hộ (ToolJet/Appsmith) thay vì gọi API tay

### Giai đoạn 2 — Lấp đầy đủ 4 không gian H-P-D-I

Đây là phần **cần bổ sung so với bản đề xuất thống nhất hiện tại**, vì đề bài OLP yêu cầu tường minh đủ 4 nhóm công cụ:

- [x] **[H]** Một SSO nhẹ (Keycloak) + một kênh chat nội bộ (Mattermost) chạy thật trong docker-compose, không chỉ nằm trên giấy
  - [x] SSO thật: Identity Provider Keycloak chạy trong docker-compose.
    Backend xác thực bằng OAuth2 Resource Server, frontend đăng nhập bằng redirect sang Keycloak (PKCE).
    Chi tiết: [`sso/README.md`](./sso/README.md).
    Superset/Mattermost vẫn đăng nhập riêng, để sau.
  - [x] Bước đệm trước SSO: trang portal tĩnh liệt kê link tới các app (App chính, BI, Chat) —
    chưa đăng nhập chung được, nhưng người dùng không cần tự nhớ URL từng app. Chi tiết:
    [`portal/README.md`](./portal/README.md).
  - [x] Kênh chat nội bộ (Mattermost) — chạy thật trong docker-compose (image Team Edition chính thức). Chi tiết: [`chat/README.md`](./chat/README.md).
  - [ ] Tích hợp thông báo tự động vào channel Mattermost (ví dụ: có `DiseaseReport` mới hoặc chờ duyệt thì backend tự post vào channel)
- [x] **[D]** Dashboard BI (Apache Superset) hiển thị bản đồ/trạng thái thửa ruộng cho Ban quản trị, tự bootstrap qua docker-compose.
  Chi tiết: [`bi/README.md`](./bi/README.md).
- [x] Rà lại license mọi thành phần open-core trước khi khoá stack (xem mục PoF bên dưới) — xem [`LICENSES.md`](./LICENSES.md).

### Giai đoạn 3 — Chiều sâu (Later, sau khi Giai đoạn 0-2 chạy ổn)

- [ ] Carbon/AWD Estimator hoàn chỉnh + IoT simulator (telemetry mực nước)
- [ ] GraphRAG / multi-agent / workflow phức tạp
- [ ] Tự động xác nhận đã phun từ IoT thay vì xác nhận thủ công

## Kiến trúc dữ liệu (tóm tắt)

Một PostgreSQL vật lý, ba nguồn logic:

- **Operational Data**: Parcel, CropSeason, DiseaseReport, Diagnosis, TreatmentApplication — "chuyện gì đã xảy ra"
- **Structured Knowledge**: Disease, Pesticide, TreatmentRule — "quy tắc là gì" (lookup bằng khoá/SQL, không qua LLM)
- **RAG Knowledge**: tài liệu VietGAP/IRRI/nội bộ đã duyệt, qua pgvector — "tài liệu giải thích gì"

## Điểm cần lưu ý cho tiêu chí PoF (50/100 điểm, mang tính loại trừ)

- Giấy phép mọi thành phần phải **OSI-approved**; đã loại Llama (không đạt chuẩn OSD), chọn Qwen2.5-1.5B-Instruct (Apache-2.0).
- ✅ Không dùng n8n (Sustainable Use License, không OSI-approved) — LangGraph (Apache-2.0) là orchestrator.
  Rà soát license đầy đủ từng thành phần: [`LICENSES.md`](./LICENSES.md).
- Mã nguồn tự viết: SPDX header `Apache-2.0` ở đầu mỗi file, kèm `LICENSE` toàn văn ở gốc repo.
- Source control phải public, có web viewer.
- Phải build/cài đặt được từ source; khuyến khích Docker.
- Không sửa mã thư viện đính kèm; có README, Changelog, bug tracker.
- Gói phát hành dùng `.tar.gz`, không dùng `.zip`/`.rar`.
- Công thức carbon: định danh là "Mô-đun Ước Lượng Giảm Phát Thải Tham Chiếu Thực Nghiệm", trích nguồn IRRI/SECTOR + Đề án 1 triệu hecta, tuyên bố rõ không thay thế kiểm toán thực địa (phòng thủ học thuật).

## Việc cần chốt cùng Lead trước khi chia task

- [x] Phạm vi demo: chỉ spine flow (Giai đoạn 0) hay bao gồm cả [H]/[D] tối thiểu (Giai đoạn 2)?
- [x] Quyết định dứt khoát về n8n (giữ/bỏ) vì lý do license — đã bỏ, xem `LICENSES.md`
- [ ] Schema operational + structured knowledge (entity, field bắt buộc)
- [ ] Contract `vision-api` (request/response JSON)
- [ ] Contract giữa backend Spring Boot và AI Service Python (REST, base URL, timeout/retry, ai-service base URL cấu hình ở đâu)
- [ ] Thời điểm PHI bắt đầu: khi `TreatmentApplication = APPLIED` (không phải khi Approve)
- [x] Cấu trúc monorepo và quyền sở hữu từng thư mục/module
- [x] Chính sách repo Private trong lúc phát triển, thời điểm public trước khi nộp bài

## Trạng thái hiện tại (cập nhật 2026-09-11)

Backend Spring Boot đã xong, xác thực qua OAuth2 Resource Server (SSO thật qua Keycloak), phân quyền FARMER/ENGINEER/ADMIN theo realm role.
Frontend demo (React/Vite) cho Phase 0 spine flow đã xong, đăng nhập bằng redirect sang Keycloak.
AI Service (Python) đã dựng xong phần Vision API cơ bản, đang thiếu phần điều phối LangGraph và LLM.
BI Dashboard (Superset) và kênh chat nội bộ (Mattermost) đã chạy thật trong docker-compose, kèm trang portal tĩnh liệt kê link tới các app — Superset/Mattermost chưa nối SSO, thuộc đợt sau.
Đã rà soát license toàn bộ thành phần đang chạy ([`LICENSES.md`](./LICENSES.md)).
Checklist việc còn thiếu: xem mục "Giai đoạn 0"/"Giai đoạn 2" ở trên.
Chi tiết đầy đủ theo từng module: [`CHANGELOG.md`](./CHANGELOG.md).

## Chạy demo Phase 0

### 1. Khởi động (một lệnh duy nhất, dựng toàn bộ Postgres + Keycloak + backend + frontend + BI + chat + portal)

```bash
cd deploy
cp .env.example .env      # chỉnh lại nếu cần, đặc biệt KEYCLOAK_ADMIN_PASSWORD, SUPERSET_*
docker compose up -d --build
```

### 2. Vào test

| Service | Vào đâu | Đăng nhập |
|---|---|---|
| **Portal** (danh sách link các app) | http://localhost:8090 | Không cần đăng nhập — chỉ là trang link |
| **App chính** (frontend) | http://localhost:8082 | Bấm "Đăng nhập" → redirect sang Keycloak, dùng tài khoản demo (xem [`sso/README.md`](./sso/README.md)) |
| **Keycloak Admin Console** | http://localhost:8180/admin | `admin`/`KEYCLOAK_ADMIN_PASSWORD` trong `.env` |
| **Backend** (Swagger, gọi API tay) | http://localhost:8081/swagger-ui.html | Lấy access token từ Keycloak (xem `sso/README.md`), bấm "Authorize" rồi dán vào |
| **BI Dashboard** (Superset) | http://localhost:8088 | `SUPERSET_ADMIN_USERNAME`/`SUPERSET_ADMIN_PASSWORD` trong `.env` |
| **Chat nội bộ** (Mattermost) | http://localhost:8065 | Chưa có tài khoản — bấm "Create account" để tạo (tự động thành Admin) |

### 3. Dừng lại

```bash
docker compose down
```

<details>
<summary>Ghi chú thêm (chỉ cần khi cần, không bắt buộc đọc để test)</summary>

- Quản lý tài khoản (tạo/xoá/đổi role FARMER/ENGINEER/ADMIN): qua Keycloak Admin Console.
  Xem [`sso/README.md`](./sso/README.md).
- Dữ liệu Postgres + ảnh upload lưu ở `deploy/data/` (bind mount, không mất khi `docker compose down`; dùng `down -v` hoặc xoá thư mục nếu muốn làm sạch từ đầu).
- Chi tiết riêng từng service: [`sso/README.md`](./sso/README.md) (Keycloak), [`bi/README.md`](./bi/README.md) (Superset), [`chat/README.md`](./chat/README.md) (Mattermost), [`portal/README.md`](./portal/README.md) (trang portal).
- Sửa code + hot reload thay vì rebuild image: chạy `docker compose up -d postgres` trong `deploy` để có sẵn Postgres, rồi `./mvnw spring-boot:run` (backend) và `npm run dev` (frontend, sau khi `cp .env.example .env`) như bình thường.

</details>
