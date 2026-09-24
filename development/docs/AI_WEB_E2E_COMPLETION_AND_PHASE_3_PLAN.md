# Kế hoạch triển khai — Diagnosis, Policy, IoT và AI workflow

> Đây là tài liệu hướng dẫn thực hiện, không chỉ là checklist. Chỉ đánh dấu hoàn thành khi đạt toàn bộ tiêu chí nghiệm thu. Bất cứ thay đổi lớn nào khác hướng bên dưới phải có ADR nêu lý do, rủi ro và migration.

## Nguyên tắc kiến trúc bắt buộc

- Diagnosis là **job bất đồng bộ có trạng thái**, không phải HTTP request chạy dài.
- Backend chọn `TreatmentRule` theo thuật toán xác định; `DiseaseManagementPolicy` seed là guardrail để cho phép/chặn direct treatment và giới hạn phạm vi hóa chất. AI chỉ nhận diện, truy xuất evidence và diễn giải trong rule đã qua guardrail.
- Mọi transition nghiệp vụ phải có `request_id`, audit trail và idempotency khi retry.
- Mattermost và IoT đi qua event/outbox, không gọi trong database transaction hay request người dùng.
- Client validation chỉ cải thiện UX; backend luôn là lớp xác thực cuối.
- Reference/master data (`Disease`, `Pesticide`, `TreatmentRule`, `DiseaseManagementPolicy`) chỉ đi theo luồng `data/` → generator → `data.sql` → Spring seed. Operational data (job, audit, outbox, telemetry, IoT event) chỉ được tạo tại runtime, không seed qua `data.sql`.
- `DiseaseManagementPolicy` dùng theo phạm vi schema và eligibility của `DB_SEED_UNIFICATION_PLAN.md`; không có lifecycle hay API quản trị runtime trong giai đoạn này.
- Không mở rộng GraphRAG/multi-agent hoặc IoT auto-confirm trước khi policy, audit và telemetry contract ổn định.

## Phân kỳ và dependency

| Phân kỳ | Kết quả đầu ra | Điều kiện bắt đầu |
| --- | --- | --- |
| P0 | Async diagnosis, audit, CV gate | Ngay lập tức |
| P1 | Seed-backed policy guardrail và deterministic `TreatmentRule` selector | P0 có schema audit và DB seed generator sẵn sàng |
| P2 | Recovery/progress frontend, Mattermost | API job/status P0 ổn định |
| P3 | Secret management, benchmark, observability | P0–P2 chạy được trên staging |
| P4 | Carbon/AWD, IoT simulator | Telemetry contract được duyệt |
| P5 | IoT tự xác nhận phun | P4 có dữ liệu simulator đáng tin |
| P6 | GraphRAG/multi-agent | P0–P5 đã version hóa audit, policy, telemetry |

## Checklist theo dõi

> Chỉ cập nhật trạng thái tại khu này. Các phần bên dưới là mô tả triển khai tương ứng với mã công việc, không chứa ô checklist.

### P0 — Nền tảng diagnosis

- [ ] P0-01 — Async job cho `/diagnosis/ai`
- [ ] P0-02 — Audit, evidence và replay
- [ ] P0-03 — CV triage và confidence gate
- [ ] P0-04 — Client-side image validation

### P1 — Guardrail và rule selection

- [ ] P1-01 — Seed-backed `DiseaseManagementPolicy` guardrail
- [ ] P1-02 — Luồng `review_required` theo guardrail và treatment rule
- [ ] P1-03 — Deterministic `TreatmentRule` selector có guardrail

### P2 — Frontend và Mattermost

- [ ] P2-01 — Recovery diagnosis khi mạng bị rớt
- [ ] P2-02 — Progress UI dựa trên trạng thái worker
- [ ] P2-03 — Mattermost notification qua transactional outbox

### P3 — Production readiness

- [ ] P3-01 — Production secret management
- [ ] P3-02 — Benchmark concurrency, cold start và sizing

### P4 — Carbon/AWD và telemetry

- [ ] P4-01 — Carbon/AWD Estimator có traceability
- [ ] P4-02 — IoT simulator telemetry mực nước

### P5 — IoT xác nhận phun

- [ ] P5-01 — Evidence contract và workflow auto-confirm hybrid

### P6 — AI workflow mở rộng

- [ ] P6-01 — GraphRAG có provenance và evaluation
- [ ] P6-02 — Multi-agent có guardrail, cost cap và human gate

### Release gates

- [ ] RG-01 — Staging gate
- [ ] RG-02 — Security gate
- [ ] RG-03 — Performance gate
- [ ] RG-04 — IoT gate
- [ ] RG-05 — AI expansion gate

---

## P0 — Nền tảng diagnosis đáng tin cậy

### P0-01. Chuyển `/diagnosis/ai` sang async job

  - **Vùng làm việc:** Backend API, queue/worker, database, frontend diagnosis, monitoring.
  - **Hướng thực hiện:**
    1. Tách upload ảnh khỏi tạo diagnosis, hoặc chỉ tạo job khi object upload đã được xác thực; endpoint diagnosis nhận `image_id`/URI hợp lệ.
    2. `POST /diagnosis/ai` tạo `DiagnosisJob`, trả `202 Accepted` gồm `job_id`, `request_id`, `status_url`.
    3. Worker lấy job bằng lock/lease và chạy: `validating_image` → `cv_triage` → `retrieving_evidence` → `selecting_treatment_rule` → `generating_response` → trạng thái kết thúc.
    4. `GET /diagnosis/jobs/{job_id}` chỉ đọc DB. Frontend theo dõi SSE/WebSocket; polling là fallback.
    5. Dùng idempotency key do client gửi hoặc hash user + ảnh + mục đích diagnosis; request lặp trả job đã có.
  - **Contract trạng thái:** `queued`, `validating_image`, `cv_triage`, `retrieving_evidence`, `selecting_treatment_rule`, `generating_response`, `completed`, `failed`, `cancelled`, `needs_better_photo`, `review_required`.
  - **Không làm:** Không giữ HTTP connection chờ model; không tạo job mới chỉ vì client retry do mất mạng.
  - **Ảnh hưởng:** Cần queue/worker và frontend chuyển từ request-response sang state machine theo job.
  - **Nghiệm thu:** Reload/mất mạng không làm mất job; retry không tạo trùng; worker restart không để job treo vô hạn.

### P0-02. Lưu audit và replay

  - **Vùng làm việc:** Database entity/schema theo quy ước DB hiện hành, diagnosis worker, object storage, API admin/audit, phân quyền dữ liệu.
  - **Hướng thực hiện:**
    1. Tạo `DiagnosisExecution` gắn với `DiagnosisJob`; mỗi attempt có `attempt_no`, `request_id`, `generation_mode`, status và timestamps.
    2. Lưu timings cấu trúc: `queued_ms`, `image_validation_ms`, `cv_ms`, `retrieval_ms`, `policy_selection_ms`, `generation_ms`, `total_ms`.
    3. Lưu version bất biến của CV model, LLM, prompt/template, embedding index; đồng thời lưu `disease_management_policy_id`, `source`, `version`, `treatment_rule_id` và revision/hash của bộ seed `data.sql` đã áp dụng.
    4. Lưu `rag_evidence`: nguồn, context, score, index version, URI, checksum. Nội dung lớn/nhạy cảm ở object storage; DB chỉ giữ metadata và tham chiếu.
    5. Replay phải tạo execution mới tham chiếu execution gốc, không ghi đè recommendation đã phát hành hay duyệt.
  - **Không làm:** Không ghi secret/access token hoặc ảnh gốc vào application log; không dùng replay để sửa lịch sử.
  - **Ảnh hưởng:** Tăng storage và cần retention/access policy; đổi lại truy vết và tái lập kết quả được.
  - **Nghiệm thu:** Bất kỳ diagnosis nào cũng tra được input fingerprint, evidence, versions, timings và replay thành execution mới.

### P0-03. Chặn ảnh chất lượng thấp trước RAG/AI

  - **Vùng làm việc:** CV service/model, backend image validation, frontend diagnosis UI, dataset/analytics.
  - **Hướng thực hiện:**
    1. Tách triage khỏi disease classifier: trả `is_leaf`, `is_disease_relevant`, `quality_score` và confidence tương ứng.
    2. Đặt ngưỡng trong config có version, không hard-code; đo false accept/false reject trên tập validation gắn nhãn trước rollout.
    3. Ảnh không phải lá, quá mờ hoặc confidence thấp kết thúc bằng `needs_better_photo`, có lý do machine-readable và hướng dẫn chụp lại.
    4. Chỉ ảnh qua triage mới được tạo embedding, gọi RAG hoặc LLM.
    5. Theo dõi tỷ lệ reject theo thiết bị, vùng trồng và model version để hiệu chỉnh có kiểm soát.
  - **Không làm:** Không dùng duy nhất disease-classifier confidence để xác định ảnh hợp lệ; không hiểu ảnh reject là diagnosis âm tính.
  - **Ảnh hưởng:** Giảm inference tốn kém và khuyến nghị sai, nhưng có thể tăng yêu cầu chụp lại ảnh.
  - **Nghiệm thu:** Ảnh ngoài ngữ cảnh không gọi RAG/LLM; dashboard có reject rate/lý do; ngưỡng có regression test.

### P0-04. Validate file tại client trước upload

  - **Vùng làm việc:** Frontend upload component, shared validation rules, backend upload endpoint.
  - **Hướng thực hiện:**
    1. Client kiểm tra kích thước `<= 10MB`, định dạng cho phép và khả năng decode ảnh trước upload.
    2. Hiển thị lỗi tại form và không gọi API diagnosis khi upload lỗi.
    3. Backend kiểm tra lại byte size, magic bytes, MIME, pixel dimensions và giới hạn chống decompression bomb.
    4. Quản lý giới hạn trong config/contract chung để client và backend không lệch.
  - **Không làm:** Không tin MIME từ browser; không bỏ validation backend vì đã có validation client.
  - **Ảnh hưởng:** Giảm upload thất bại sau khi chờ, nhưng cần test đa trình duyệt.
  - **Nghiệm thu:** File >10MB bị chặn ngay tại client; request thủ công vượt giới hạn vẫn bị backend từ chối an toàn.

---

## P1 — Disease Management Guardrail và chọn `TreatmentRule` có giải thích

### P1-01. Seed-backed `DiseaseManagementPolicy` guardrail

  - **Vùng làm việc:** `data/` master data, generator dev-time, `data.sql`, entity/repository `disease_management_policies`, tài liệu quy ước seed.
  - **Hướng thực hiện:**
    1. Dùng entity/bảng `disease_management_policies` đúng schema của DB seed plan: `disease_id`, `management_status`, `direct_treatment_rule_allowed`, `chemical_rule_scope`, `recommended_actions`, `source`, `version`.
    2. Seed các record chỉ từ `disease_management_policies_master.json` qua generator → `data.sql`; chỉ record đủ eligibility mới được sinh SQL.
    3. Bảng này là guardrail đọc/tra cứu: quyết định liệu direct treatment rule có được phép và phạm vi hóa chất nào được chấp nhận; nó không tự chứa hay tự sinh dosage/recommendation hóa chất.
    4. Generator phải deterministic, dữ liệu seed chạy idempotent lúc start và mọi sửa đổi master data đi qua review PR theo `DB_SEED_UNIFICATION_PLAN.md`.
    5. Thay đổi schema chỉ được thực hiện khi dữ liệu nguồn đã được duyệt yêu cầu; không suy diễn hay thêm trường từ candidate chưa duyệt.
  - **Không làm:** Không tạo API quản trị/lifecycle `draft → approved → active → retired` ở runtime; không để service hay LLM tự biến guardrail thành khuyến nghị hóa chất.
  - **Ảnh hưởng:** 13 policy hiện có trở thành safety gate thống nhất; quyền thay đổi nằm ở luồng data review/generator, không ở admin UI.
  - **Nghiệm thu:** Entity đúng schema, generator seed đủ record eligible, start DB rỗng seed đủ và start lần hai không trùng; không còn seed tay riêng hoặc nguồn master data thứ hai.

### P1-02. Đưa mọi trường hợp thiếu/không hợp lệ về `review_required`

  - **Vùng làm việc:** Rule-selection service, diagnosis state machine, frontend result screen, review queue, Mattermost event payload.
  - **Hướng thực hiện:**
    1. Giữ `review_required` là trạng thái terminal an toàn. Không dùng `no_policy_available` làm trạng thái nghiệp vụ riêng; nếu cần, nó chỉ là reason code trong audit/API.
    2. Ghi `review_reason` có cấu trúc: `missing_disease_guardrail`, `direct_treatment_not_allowed`, `no_eligible_treatment_rule`, `chemical_scope_violation`, `ambiguous_rule_selection` hoặc `missing_required_context`.
    3. Khi không có guardrail, guardrail chặn direct treatment, không có treatment rule eligible, hoặc không thể chọn xác định, worker kết thúc `review_required` và không gọi generation tạo khuyến nghị điều trị.
    4. Frontend hiển thị lý do an toàn và đưa report vào hàng đợi duyệt; Mattermost chỉ nhận event cần duyệt, có `review_reason` nhưng không có dữ liệu nhạy cảm.
  - **Không làm:** Không để LLM bù rule/hoạt chất; không tự đổi case thiếu data thành recommendation chỉ để hoàn tất request.
  - **Ảnh hưởng:** Tăng case cần chuyên gia duyệt ban đầu nhưng phù hợp với nguyên tắc chỉ dùng dữ liệu seed đã duyệt.
  - **Nghiệm thu:** Mọi tình huống trên đều kết thúc `review_required`, có reason code/audit và không có recommendation điều trị tự động.

### P1-03. Thay “rule đầu tiên” bằng deterministic `TreatmentRule` selector có guardrail

  - **Vùng làm việc:** `TreatmentRule` repository/service, guardrail repository, approved master data, generator, audit, unit/integration test.
  - **Hướng thực hiện:**
    1. Chuẩn hóa `AgronomicContext`: disease, crop nếu dữ liệu rule hỗ trợ, severity, growth stage, location, weather, water regime, lịch sử xử lý và các trường còn thiếu.
    2. Lấy candidate `TreatmentRule` đã seed/eligible theo disease và context có trong dữ liệu được duyệt; việc lấy candidate chưa phải là quyết định recommendation.
    3. Tải `DiseaseManagementPolicy` guardrail tương ứng, kiểm tra `direct_treatment_rule_allowed` và `chemical_rule_scope` trước khi chọn recommendation cuối cùng.
    4. Chỉ xếp hạng các rule còn hợp lệ theo `priority`, độ đặc hiệu context và tie-breaker cố định. `priority`/context dùng để chọn phải tồn tại trong master data đã duyệt và được generator đưa vào schema; thiếu chúng thì trả `review_required`, không tự đặt giá trị.
    5. Audit lưu candidate rule IDs, rule bị loại, guardrail đã áp dụng, score/tie-breaker và rule cuối cùng. Prompt chỉ nhận rule cuối cùng cùng guardrail; output qua validator trước response.
  - **Không làm:** Không phụ thuộc thứ tự query database; không chọn `DiseaseManagementPolicy` như rule điều trị; không bắt AI chọn rule từ danh sách tự do.
  - **Ảnh hưởng:** Priority và context là concern của `TreatmentRule`, không phải bảng guardrail; có thể phải bổ sung dữ liệu/schema theo đúng quy trình eligibility của DB seed plan.
  - **Nghiệm thu:** Cùng context và cùng revision master data luôn chọn cùng `TreatmentRule`; case thiếu priority/context hoặc có conflict đi `review_required`; audit giải thích được lựa chọn.

---

## P2 — Trải nghiệm frontend và Mattermost

### P2-01. Tự đồng bộ diagnosis khi request rớt

  - **Vùng làm việc:** Frontend state management, browser storage, diagnosis job API, authorization.
  - **Hướng thực hiện:**
    1. Sau khi nhận `job_id`, client lưu `job_id`, `request_id`, image reference và timestamp trong local/session storage phù hợp.
    2. Khi vào trang diagnosis, client lấy job chưa kết thúc từ API và reconcile với local state; server là nguồn dữ liệu ưu tiên.
    3. SSE/WebSocket mất kết nối phải reconnect có backoff, rồi luôn gọi status API để reconcile.
    4. Status API kiểm tra ownership/tenant, không chỉ dựa vào `job_id`.
  - **Không làm:** Không coi browser storage là dữ liệu chính; không tạo diagnosis mới khi reconnect.
  - **Ảnh hưởng:** Frontend chuyển hẳn sang state machine theo job.
  - **Nghiệm thu:** Tắt mạng giữa xử lý, reload sau khi mạng lại và thấy kết quả mà không tạo job mới.

### P2-02. Hiển thị tiến trình có dữ liệu thật

  - **Vùng làm việc:** Worker event publisher, job status store, SSE/WebSocket/polling, frontend progress component.
  - **Hướng thực hiện:**
    1. Worker ghi stage transition kèm timestamp và progress metadata sau mỗi bước thực tế.
    2. Status API trả stages hoàn tất, stage hiện tại, lỗi/retryability; chỉ trả ETA khi có dữ liệu đủ tin cậy.
    3. UI dùng nhãn cố định: tải ảnh, kiểm tra ảnh, nhận diện, truy xuất evidence, áp guardrail/chọn rule, tạo khuyến nghị, hoàn tất.
    4. Bước lâu hiển thị thời gian đã chờ và cho phép rời trang an toàn; không có % tiến độ giả.
  - **Không làm:** Không cập nhật progress bằng timer thuần client; không cho cancel nếu worker chưa hỗ trợ cancel an toàn.
  - **Ảnh hưởng:** Tăng event/state records nhưng cải thiện trải nghiệm chờ dài.
  - **Nghiệm thu:** UI phản ánh mọi trạng thái transient/terminal; polling fallback hoạt động khi realtime channel bị chặn.

### P2-03. Tích hợp Mattermost qua transactional outbox

  - **Vùng làm việc:** Domain events/outbox, notification worker, Mattermost webhook config, admin/review UI.
  - **Hướng thực hiện:**
    1. Khi tạo/cập nhật `DiseaseReport`, ghi `OutboxEvent` trong cùng transaction với event type, aggregate ID, dedupe key, payload tối thiểu.
    2. Notification worker đọc outbox, gọi webhook, lưu attempt/status và exponential backoff khi lỗi.
    3. Dedupe key theo report + transition để event xử lý lại không gửi nhiều message.
    4. Template gồm severity, farm/parcel được phép hiển thị, trạng thái, `review_reason` nếu cần duyệt, link review và metadata `request_id/report_id`.
    5. Webhook lấy từ secret manager ở P3; staging dùng webhook/channel riêng.
  - **Không làm:** Không gọi Mattermost trong HTTP request/database transaction; không gửi ảnh gốc, token hay evidence nhạy cảm vào channel.
  - **Ảnh hưởng:** Có worker/outbox retention mới; đổi lại event không mất khi Mattermost lỗi.
  - **Nghiệm thu:** Một report mới/cần duyệt chỉ gửi một notification; webhook lỗi được retry và delivery audit xem được.

---

## P3 — Production readiness: secret, benchmark, observability

### P3-01. Đưa secret vào secret management production

  - **Vùng làm việc:** Infrastructure, CI/CD, deployment manifests, backend configuration, AI/Mattermost/IoT integration.
  - **Hướng thực hiện:**
    1. Chọn Vault hoặc cloud secret manager phù hợp hạ tầng, xác định owner và rotation policy.
    2. Workload đọc secret bằng service identity, không dùng credential tĩnh trong deployment.
    3. Tách dev/staging/prod theo namespace/project riêng; CI chỉ có quyền tối thiểu cần thiết.
    4. Quét source, `.env`, compose, CI history, image build; xoay vòng mọi key từng dùng local/demo.
    5. Chỉ giữ tên biến/dữ liệu giả trong `.env.example`; local dev dùng secret riêng không có quyền production.
  - **Không làm:** Không commit secret đã mã hóa nếu chưa có KMS, rotation và owner rõ ràng; không dùng chung key/webhook staging và production.
  - **Ảnh hưởng:** Phải cập nhật CI/CD/runbook; rotation cần diễn tập staging trước.
  - **Nghiệm thu:** Secret scan không có key thật; workload chạy identity quyền tối thiểu; rotation drill staging thành công.

### P3-02. Benchmark concurrency, cold start và sizing

  - **Vùng làm việc:** Load-test suite, staging deployment, monitoring/tracing, infrastructure-as-code.
  - **Hướng thực hiện:**
    1. Chốt workload: upload đồng thời, tỷ lệ reject, AI latency trung bình/tối đa, retry rate và notification burst.
    2. Test tách lớp: upload/API, queue backlog, CV worker, RAG/LLM worker, database, Mattermost outbox; không chỉ bắn request đồng bộ.
    3. Đo cold start, queue waiting, p50/p95/p99 từng stage, success rate, CPU/RAM/GPU, DB connections, chi phí/job.
    4. Fault test: worker chết giữa job, queue restart, model timeout/rate limit, object storage chậm.
    5. Chốt autoscaling/min replicas, warm strategy, queue retention/DLQ, concurrency cap và SLO alert từ kết quả.
  - **Không làm:** Không sizing production từ laptop/local; không đo end-to-end mà bỏ queue waiting time.
  - **Ảnh hưởng:** Có thể cần tách CV và LLM worker pool hoặc giảm concurrency limit.
  - **Nghiệm thu:** Có báo cáo benchmark version hóa, baseline staging/prod, dashboard và alert gắn SLO.

---

## P4 — Carbon/AWD Estimator và IoT simulator

### P4-01. Hoàn chỉnh Carbon/AWD Estimator

  - **Vùng làm việc:** Agronomy domain model, estimator service, telemetry storage, reporting/dashboard.
  - **Hướng thực hiện:**
    1. Chốt đơn vị, phạm vi và phương pháp luận được chuyên gia phê duyệt; đóng gói bằng `estimator_version`, không để công thức rải trong UI.
    2. Input gồm parcel/crop season, time window, water-level telemetry, data quality và giả định thay thế khi thiếu dữ liệu.
    3. Estimator lưu input aggregation, version, assumptions, output, completeness/confidence score.
    4. Dashboard phân biệt telemetry thô, dữ liệu đã làm sạch và estimate; cảnh báo khi dữ liệu không đủ tin cậy.
  - **Không làm:** Không trình bày estimate như số đo thực; không ghi đè kết quả lịch sử khi công thức thay đổi.
  - **Ảnh hưởng:** Cần chuyên gia carbon/nông học phê duyệt giả định; data retention tăng.
  - **Nghiệm thu:** Một báo cáo truy ngược được telemetry đầu vào, version công thức và confidence/completeness.

### P4-02. Xây IoT simulator cho telemetry mực nước

  - **Vùng làm việc:** Simulator service/CLI, message broker hoặc ingest API, telemetry database, monitoring UI, test environment.
  - **Hướng thực hiện:**
    1. Chốt envelope: `event_id`, `device_id`, `parcel_id`, `occurred_at`, water level, unit, quality flag, sequence number, schema version.
    2. Simulator phát cùng transport/schema dự kiến của thiết bị thật, hỗ trợ seed/scenario file để tái lập.
    3. Có scenario normal AWD cycle, sensor stuck/drift, out-of-order, duplicate, mất kết nối, dữ liệu trễ và vượt ngưỡng.
    4. Ingestion kiểm tra schema, device authorization, idempotency theo `event_id`, ordering policy và lưu raw event bất biến.
  - **Không làm:** Không để simulator ghi thẳng database; không dùng data simulator kích hoạt automation production.
  - **Ảnh hưởng:** Môi trường test gần thiết bị thật, phát hiện sớm lỗi time-series/data quality.
  - **Nghiệm thu:** Scenario tái lập được; duplicate/out-of-order không làm hỏng aggregate/AWD estimate.

---

## P5 — Tự động xác nhận đã phun từ IoT

### P5-01. Xác định event phun đáng tin và workflow hybrid

  - **Vùng làm việc:** IoT contract, device identity/authentication, event ingestion, disease report state machine, audit/notification UI.
  - **Hướng thực hiện:**
    1. Chốt evidence tối thiểu: device identity, parcel/GPS, thời gian, controller command, flow/pressure, lượng phun, duration, telemetry quality và signature/xác thực thiết bị.
    2. Lưu `SprayEvent` raw bất biến; evaluator kiểm tra khớp `TreatmentRule`, guardrail đã audit, parcel, thời gian và liều lượng.
    3. Đủ điều kiện tạo `spraying_confirmed_by_iot`; thiếu/conflict tạo `verification_required` kèm lý do cho con người xử lý.
    4. Idempotency key lấy từ device event; auto-confirm/manual override phải ghi actor, source, evidence.
    5. Chạy shadow mode: chỉ đề xuất trạng thái, so sánh xác nhận thủ công trước khi bật auto-confirm.
  - **Không làm:** Không suy ra “đã phun” từ lịch hẹn/lệnh gửi đi; không auto-confirm từ simulator hoặc device chưa đăng ký.
  - **Ảnh hưởng:** Giảm thao tác nhưng tăng yêu cầu bảo mật thiết bị và kiểm soát telemetry sai lệch.
  - **Nghiệm thu:** Event đủ evidence auto-confirm đúng một lần; dữ liệu thiếu không auto-confirm; shadow accuracy đạt ngưỡng nghiệp vụ phê duyệt.

---

## P6 — GraphRAG, multi-agent và workflow phức tạp

### P6-01. Xây GraphRAG sau khi dữ liệu lõi ổn định

  - **Vùng làm việc:** Knowledge graph store, RAG ingestion/indexing, policy/evidence schema, access control, evaluation suite.
  - **Hướng thực hiện:**
    1. Mô hình hóa crop, disease, symptom, guardrail/version, treatment rule, product, parcel, telemetry, diagnosis/report và nguồn evidence.
    2. Graph/vector store là projection cho retrieval, không phải source of truth của master data. Master data chỉ đi theo `data/` → generator → `data.sql`; các nguồn tri thức bổ sung cần ADR và registry riêng trước ingestion.
    3. Ingestion version hóa source, effective date, access rights; mọi node/chunk có provenance. Retrieval trả evidence có provenance; P1 `TreatmentRule` selector cùng guardrail vẫn là nơi quyết định.
    4. Đánh giá bằng bộ câu hỏi có expected evidence, precision/recall và citation check trước rollout.
  - **Không làm:** Không để graph data chưa kiểm chứng quyết định hóa chất; không rò evidence cross-tenant vào prompt.
  - **Ảnh hưởng:** Tăng indexing/governance nhưng cải thiện explainability/truy vấn quan hệ.
  - **Nghiệm thu:** Evidence có nguồn/version; evaluation đạt ngưỡng; không có cross-tenant leakage.

### P6-02. Triển khai multi-agent với vai trò và guardrail cố định

  - **Vùng làm việc:** Orchestrator/workflow engine, AI services, policy validator, audit/tracing, evaluation/observability.
  - **Hướng thực hiện:**
    1. Bốn vai trò không chồng lấn: retrieval, agronomy validation, compliance/safety, report synthesis.
    2. Orchestrator quản lý input/output schema mỗi node, timeout, retry limit, token/cost budget và fallback đơn giản.
    3. Chỉ evidence có provenance truyền giữa agent; validator `TreatmentRule` + guardrail là gate bắt buộc trước recommendation.
    4. Lưu per-agent trace theo `request_id`: model/version, prompt, tool call, output, timing, fallback.
    5. Kết quả an toàn không chắc chắn phải về `review_required`, không chạy agent loop vô hạn để cố tạo câu trả lời.
  - **Không làm:** Không để agent tự cấp quyền, phát hành policy hoặc gọi IoT actuator; không rollout khi chưa có evaluation, cost limit, kill switch.
  - **Ảnh hưởng:** Tăng latency/cost/diện tích vận hành; chỉ đáng làm khi pipeline hiện tại đã đo được.
  - **Nghiệm thu:** Workflow replay được, có timeout/retry/fallback/cost cap/human review; lỗi một agent không làm mất audit hoặc vượt policy.

## Release gates cuối cùng

### RG-01. Staging gate

P0–P2 hoàn tất; integration test job retry, status recovery, guardrail + `TreatmentRule` selection và Mattermost outbox xanh. DB rỗng seed đủ master data approved, start lần hai không trùng, guardrail chặn direct treatment đúng và mọi case thiếu/ambiguous rule đi `review_required`.

### RG-02. Security gate

Secret scan, service identity quyền tối thiểu, rotation drill và audit access được duyệt.

### RG-03. Performance gate

Có benchmark capacity, SLO/alert, queue DLQ và runbook job lỗi.

### RG-04. IoT gate

Telemetry contract, simulator fault cases, device identity và shadow-mode auto-confirm đạt tiêu chí nghiệp vụ.

### RG-05. AI expansion gate

Chỉ bật GraphRAG/multi-agent khi evaluation chứng minh chất lượng/giải thích tăng trong giới hạn latency và chi phí đã chốt.
