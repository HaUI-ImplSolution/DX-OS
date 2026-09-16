# Checklist Triển Khai: Tách AI Thành Dịch Vụ Suy Luận Thuần Túy

> **Dự án**: DX-Farm
> **Ngày tạo**: 15/09/2026
> **Mục đích**: Phân rã kế hoạch decoupling thành các mục nhỏ, có thể giao việc và theo dõi độc lập, để nhiều người/nhiều phiên làm việc cùng một hướng nhất quán.

## Tài liệu tham chiếu (đọc trước khi làm bất kỳ mục nào)

- [`ai_backend_decoupling_plan.md`](./ai_backend_decoupling_plan.md) — nguồn sự thật duy nhất cho kiến trúc mục tiêu, contract API (mục 5) và lý do đằng sau từng quyết định.
  File checklist này chỉ là bản phân rã công việc từ đó; khi hai file lệch nhau, `ai_backend_decoupling_plan.md` luôn thắng.
- [`AI_EVENT_SCHEMA_README.md`](./AI_EVENT_SCHEMA_README.md) — contract API hiện tại của AI (trước khi tách), cần biết để hiểu cái gì đang bị thay thế.
- [`../../README.md`](../../README.md) — tổng quan kiến trúc dự án, roadmap H-P-D-I, trạng thái hiện tại.
- [`../../ai/README.md`](../../ai/README.md) — cấu trúc module AI, danh sách endpoint, biến môi trường.
- [`../../ai/app/graph/README.md`](../../ai/app/graph/README.md) — phạm vi và ranh giới của LangGraph so với `WorkflowRunner`/PostgreSQL.
- [`../../backend/.claude/WORKFLOW.md`](../../backend/.claude/WORKFLOW.md) — quy ước bắt buộc khi sửa `backend/`: thứ tự Spec → Swagger → Code (mục 9), style Markdown một câu một dòng (mục 11), không tự commit/push (mục 7).

## Cách dùng checklist này

- Mỗi mục có mã (`P1-1`, `P2-3`...) để tham chiếu trong commit message hoặc PR, ví dụ `docs(ai): P1-3 xóa router workflow`.
- Đánh dấu `[x]` chỉ khi mục đã xong **và** đã cập nhật tài liệu liên quan theo mục "Sau khi hoàn thành một mục" bên dưới — không đánh dấu xong nếu còn nợ doc.
- Thứ tự Phase 1 → Phase 2 → Phase 3 là thứ tự khuyến nghị, không phải ràng buộc cứng; một số mục ở Phase 3 (tách DB vật lý) có thể làm sớm hơn nếu hạ tầng sẵn sàng, miễn giữ đúng phụ thuộc ghi ở từng mục.

---

## Phase 1 — Tái cấu trúc phía AI (Python/FastAPI)

Owner đề xuất: người phụ trách `ai/`.
Tham chiếu: mục 6 "Phase 1" của `ai_backend_decoupling_plan.md`.

- [ ] **P1-1** — Xóa 10 ORM model nghiệp vụ dư thừa trong `ai/app/db/models/`: `disease_report.py`, `diagnosis.py`, `treatment_application.py`, `parcel.py`, `crop_season.py`, `disease.py`, `pesticide.py`, `treatment_rule.py`, `disease_management_policy.py`, `workflow_case.py`.
- [ ] **P1-2** — Xóa repository/service CRUD nghiệp vụ tương ứng và toàn bộ lệnh `db.add()`/`db.commit()` ghi vào các bảng nghiệp vụ trong AI runtime.
- [ ] **P1-3** — Xóa router `/disease/workflow/*` (`run`, `{case_id}` GET, `approve`, `reject`, `applied`) cùng `WorkflowRunner`/`WorkflowTransitions` (`ai/app/application/workflow_runner.py`, `workflow_transitions.py`).
  Phụ thuộc: rà soát mọi client (frontend, app, script nội bộ) đang gọi các endpoint này và chuyển sang gọi Backend trước khi xóa.
- [ ] **P1-4** — Xóa endpoint `POST /disease/reports/{report_id}/diagnose` (`diagnose_report` trong `ai/app/api/router.py`, ghi thẳng DB) — thay bằng inference contract mới ở P1-6.
- [ ] **P1-5** — Thêm Backend REST client read-only trong `ai/` để lấy field context, treatment rule, policy, PHI và lịch sử phun sau khi CV có `disease_code`.
  Xem contract client cần gọi ở mục 4.3 của kế hoạch (service token, chỉ GET).
- [ ] **P1-6** — Đổi endpoint AI thành inference contract mới `POST /api/v1/diagnose`: nhận ảnh/request context, kết hợp CV + RAG + structured facts và trả recommendation về Backend theo schema mục 5.2 của kế hoạch.
- [ ] **P1-7** — Giữ nguyên `rag/retriever.py` và `rag/ingestion_service.py`; đảm bảo runtime chỉ đọc AI Vector DB, còn ingest là job quản trị có quyền ghi riêng (không lẫn với runtime inference).
- [ ] **P1-8** — Áp dụng timeout + retry giới hạn (`httpx`) khi AI gọi Backend internal API, theo mục 4.4 của kế hoạch (timeout của AI ngắn hơn timeout Backend khi gọi AI).
- [ ] **P1-9** — Đảm bảo nhánh "lấy sự thật nghiệp vụ" thất bại thì AI trả lỗi ngay, không trả `recommendation` chỉ dựa trên RAG evidence (mục 4.4).

## Phase 2 — Tái cấu trúc phía Backend (Java/Spring Boot)

Owner đề xuất: người phụ trách `backend/`.
Tham chiếu: mục 6 "Phase 2" của `ai_backend_decoupling_plan.md`.
**Áp dụng WORKFLOW.md mục 9 cho mọi mục dưới đây** — cập nhật swagger (springdoc annotation) trong cùng lần thay đổi khi endpoint mới hoặc response shape đổi, không ship code rồi để swagger sau.

- [x] **P2-0 (chặn P2-\*)** — Chốt schema liều lượng: dùng `dose_amount` đơn để khớp schema Backend hiện tại (`TreatmentRule.doseAmount`), không thêm `doseMin`/`doseMax` (xem mục 5 của kế hoạch, phần ghi chú dưới response mẫu).
- [x] **P2-1** — Thêm `AiDiagnosisClient` (dùng `RestClient` + `JdkClientHttpRequestFactory`), có timeout/circuit breaker (Resilience4j) theo mục 4.4.
  Chưa nối vào flow nào — `DiseaseReportService` sẽ gọi ở P2-4.
- [x] **P2-2** — Cập nhật `DiseaseReportService`: tự khởi tạo và lưu `DiseaseReport` vào Backend DB (không còn chờ AI ghi hộ).
- [x] **P2-3** — Cung cấp internal API read-only để AI lấy structured facts bằng `disease_code` và context của báo cáo; bảo vệ bằng service token/network nội bộ theo mục 4.3.
  Chỉ gồm endpoint GET; không expose POST/PUT/DELETE cho service role của AI.
- [x] **P2-4** — Gọi `AiDiagnosisClient.diagnose(...)` gửi payload sang AI Service theo contract `POST /api/v1/diagnose`.
  Chưa nối vào flow tạo báo cáo/HTTP endpoint nào — `requestAiDiagnosis()` sẽ được P2-5 gọi khi xử lý và lưu response.
- [x] **P2-5** — Nhận CV, evidence và recommendation từ AI; đối chiếu lại `version` của rule (mục 4.5) trước khi lưu; nếu rule đã đổi trong lúc AI xử lý thì từ chối và lấy lại rule mới.
  `createFromAi()` chưa nối vào `DiagnosisController` — endpoint nào expose nó là quyết định của P2-6.
- [x] **P2-6** — Xác nhận `DiagnosisController` (`create`/`approve`/`reject`/`apply`) là nơi duy nhất quản lý vòng đời phê duyệt sau khi AI không còn state machine riêng.
  Rà soát: chỉ `DiagnosisService` mutate `ApprovalStatus`, và chỉ `DiagnosisController` gọi các phương thức đó.
  Thêm `POST .../diagnosis/ai` gọi `createFromAi()` (P2-5), giữ `POST .../diagnosis` cho nhập tay (testing/demo) - cả hai cùng đổ vào một `Diagnosis`/vòng đời duyệt duy nhất qua `approve`/`reject`/`apply`.

## Phase 3 — Khởi tạo Database & Deployment

Owner đề xuất: người phụ trách hạ tầng/docker-compose.
Tham chiếu: mục 6 "Phase 3" của `ai_backend_decoupling_plan.md`.

- [x] **P3-1** — Khởi tạo DB riêng `dx_farm_ai_vector_db` chỉ chứa bảng `documents` và `document_chunks` (kèm pgvector extension).
- [x] **P3-2** — Cập nhật `deploy/docker-compose.yml` để tách biệt biến môi trường kết nối DB của Backend và AI, theo cùng pattern DB/user riêng mà `keycloak`/`superset_ro` đang dùng trên cùng Postgres instance.
- [x] **P3-3** — Dùng user chỉ-đọc cho AI runtime trên Vector DB, và user/job riêng cho ingest.
  Không cấp bất kỳ credential nào của AI trỏ vào Backend DB, kể cả read-only (mục "Mục tiêu cốt lõi" của kế hoạch).
- [ ] **P3-4** — Quyết định số phận dữ liệu cũ trong AI DB (`disease_reports`, `diagnoses`, `treatment_applications`, `workflow_cases`): bỏ nếu là dữ liệu demo/test, migrate/backfill sang Backend DB trước khi cắt kết nối nếu là dữ liệu thật.

---

## Sau khi hoàn thành một mục — bắt buộc cập nhật tài liệu liên quan

Không coi một mục là "xong" chỉ vì code chạy được.
Trước khi đánh dấu `[x]` ở trên, cập nhật đúng các tài liệu bị ảnh hưởng:

| Loại thay đổi | Tài liệu phải cập nhật |
|---|---|
| Xóa/đổi endpoint AI (`P1-3`, `P1-4`, `P1-6`) | [`AI_EVENT_SCHEMA_README.md`](./AI_EVENT_SCHEMA_README.md), [`../../ai/README.md`](../../ai/README.md) (bảng API + ghi chú trạng thái kiến trúc ở đầu file) |
| Xóa `WorkflowRunner`/`WorkflowTransitions`, đổi phạm vi LangGraph (`P1-3`, `P1-6`) | [`../../ai/app/graph/README.md`](../../ai/app/graph/README.md) |
| Đổi model/DB layer phía AI (`P1-1`, `P1-2`, `P1-7`) | [`../../ai/README.md`](../../ai/README.md) phần "Chuẩn bị database, seed và RAG" |
| Endpoint/response mới hoặc đổi ý nghĩa field ở Backend (`P2-1` .. `P2-6`) | Swagger (springdoc annotation, theo WORKFLOW.md mục 9) **trong cùng lần thay đổi**, không tách riêng |
| Đổi contract `dose_min/max` vs `dose_amount` (`P2-0`) | `ai_backend_decoupling_plan.md` mục 5.2 (cập nhật JSON mẫu khớp quyết định cuối) |
| Tách DB, đổi biến môi trường compose (`P3-1`..`P3-4`) | [`../../ai/README.md`](../../ai/README.md) phần "Biến môi trường" và "Triển khai Docker", [`../../README.md`](../../README.md) phần "Kiến trúc dữ liệu" |
| Bất kỳ mục nào đổi trạng thái tổng thể của kế hoạch | [`../../README.md`](../../README.md) phần "Trạng thái hiện tại" — cập nhật ngày và mô tả |

Sau khi một Phase hoàn tất toàn bộ, gỡ ghi chú "Trạng thái kiến trúc" tạm thời đã thêm vào các README nói trên (đánh dấu là kiến trúc hiện tại thay vì kiến trúc đang chuyển đổi), và cập nhật `CHANGELOG.md` theo đúng convention hiện có (chỉ nêu kết quả, không kể lại diễn biến — theo WORKFLOW.md mục 10).
