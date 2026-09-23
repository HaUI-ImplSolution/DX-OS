# Kế hoạch hoàn thiện AI Web E2E và Giai đoạn 3

> **Trạng thái:** Backlog triển khai, chưa bắt đầu.
>
> **Cập nhật:** 23/09/2026.
>
> **Phạm vi:** Hoàn thiện các giới hạn được liệt kê trong [AI Web E2E Demo](AI_WEB_E2E_DEMO_README.md), sau đó thực hiện ba hạng mục Giai đoạn 3 trong [README gốc](../../README.md). Tài liệu này thay thế kế hoạch cũ và không còn là kế hoạch về data/database.

## 1. Mục tiêu và nguyên tắc

Mục tiêu là chuyển luồng demo đồng bộ thành luồng có thể vận hành, kiểm tra và mở rộng an toàn.

~~~mermaid
flowchart LR
    F[Farmer gửi ảnh] --> B[Backend tạo Diagnosis Job]
    B --> Q[Queue và worker]
    Q --> A[AI: CV, facts, RAG, diễn đạt]
    A --> R[Backend kiểm tra policy và rule]
    R --> H[Diagnosis chờ kỹ sư duyệt]
    H --> T[Ghi nhận xử lý]
    T -. Giai đoạn 3 .-> I[Xác nhận IoT]
~~~

- Backend là nơi duy nhất quyết định trạng thái nghiệp vụ, policy điều trị, duyệt/từ chối và ghi nhận xử lý. AI chỉ suy luận và diễn đạt dựa trên input được cấp.
- LLM hoặc agent không được tự đặt thuốc, liều, PHI, tự duyệt diagnosis, hoặc điều khiển thiết bị. Xác nhận IoT là xác nhận một sự kiện đã xảy ra, không phải lệnh tự động phun.
- Mỗi kết quả có tác động nghiệp vụ phải truy được request, phiên bản model/policy/rule, evidence và thời lượng từng bước.
- Giai đoạn 3 chỉ bắt đầu sau khi các gate Giai đoạn 0–2 đạt ở staging. Không dùng GraphRAG hoặc multi-agent để che vấn đề chưa giải quyết của pipeline cơ bản.
- Treatment rule hiện tại vẫn chỉ là demo/MVP. Không bỏ nhãn demo hoặc dùng như khuyến cáo thực tế trước khi có phê duyệt chuyên môn độc lập.

Ngoài phạm vi: tái cấu trúc kho dữ liệu/database, thay thuật toán CV, tự động điều khiển bơm/van/phun và phát hành production. Schema/API bổ sung chỉ phục vụ job, audit, policy, IoT và vận hành luồng này.

## 2. Cơ sở lập kế hoạch

| Khoảng trống | Dấu hiệu đã xác nhận | Hướng xử lý | Giai đoạn |
|---|---|---|---|
| Request AI giữ HTTP đến 120 giây | Endpoint diagnosis AI gọi service đồng bộ và trả 201 | Job nền, polling, idempotency | 1 |
| Rớt mạng làm UX không rõ kết quả | UI chỉ chờ promise; reload mới có thể thấy diagnosis | Job truy vấn được theo report; UI tự khôi phục | 1 |
| Chỉ có spinner tĩnh | DiagnosisPanel không biết bước AI hiện tại | Milestone có ý nghĩa, polling/backoff, lỗi có thể hành động | 1 |
| Không audit/replay được | AI có request ID, evidence; graph có timings; Diagnosis chỉ lưu summary/model | Lưu immutable run, provenance, timing, policy snapshot | 2 |
| Chưa có DiseaseManagementPolicy; rule đầu tiên được chọn | Rule được sắp theo ID và AI lấy phần tử đầu | Backend resolver chọn policy/rule xác định theo ngữ cảnh | 2 |
| Threshold CV chưa chặn nghiệp vụ | Có cấu hình threshold và cờ low confidence, nhưng pipeline vẫn tiếp tục treatment path | Low-confidence/image-quality path chặn đề xuất hóa chất | 2 |
| Upload chỉ bị chặn sau khi gửi | Frontend chỉ nhận image wildcard; Backend/AI giới hạn 10 MiB | Browser preflight, server vẫn quyết định cuối | 0 |
| Chưa benchmark, secret chỉ local | Được ghi rõ trong AI Web E2E Demo | Baseline, recovery test, secret theo môi trường | 0–2 |
| Hạng mục chiều sâu | README mục Giai đoạn 3: Carbon/AWD, GraphRAG/multi-agent, xác nhận phun từ IoT | Ba workstream sau gate | 3 |

## 3. Cách dùng backlog này

Mỗi mục T là một task có thể giao cho một phiên làm việc. Không đánh dấu xong chỉ vì mã đã viết: chỉ đánh dấu khi checklist hoàn tất, test qua và PR được review.

| Ký hiệu | Ý nghĩa |
|---|---|
| [ ] | Chưa bắt đầu |
| [~] | Đang làm; thêm link PR/issue và người phụ trách sau ký hiệu |
| [x] | Đã xong; thêm link bằng chứng kiểm tra |
| Gate | Điểm dừng bắt buộc trước khi làm nhóm task phía sau |

Khi bắt đầu một task, tạo issue theo mẫu: mục tiêu, phạm vi file/module, API/schema ảnh hưởng, người phụ trách, rủi ro, checklist bên dưới và bằng chứng bàn giao. Một task không được mở rộng sang task kế tiếp nếu chưa qua Gate của nó.

## 4. Backlog Giai đoạn 0 — Chuẩn bị để triển khai

**Mục tiêu giai đoạn:** chốt contract và có baseline đo được. Giai đoạn này chưa thay đổi flow diagnosis thành async.

### T0.1 — Chốt thiết kế diagnosis job

**Mục đích:** biến yêu cầu async thành contract cụ thể trước khi viết migration hoặc worker.

**Công việc cần làm**

- Chọn queue/worker có durable job, retry, lease và khả năng quan sát; không dùng background thread chỉ sống trong web process.
- Chốt state machine: QUEUED, RUNNING, SUCCEEDED, FAILED_RETRYABLE, FAILED_FINAL và CANCELLED.
- Chốt semantics cho Idempotency-Key, cùng key trả cùng job; mỗi report chỉ có một job active.
- Chốt retry matrix, lease timeout, số attempt, backoff, người có quyền retry/cancel và cách rollout/rollback.
- Viết OpenAPI cho tạo job, đọc job, progress, error và status terminal.

**Đầu ra bàn giao:** ADR được duyệt và file OpenAPI/contract version đầu tiên.

**Checklist hoàn tất**

- [ ] ADR trả lời công nghệ queue/worker, ownership vận hành, lease và recovery sau restart.
- [ ] Contract nêu rõ request/response HTTP status, Idempotency-Key, error code và authorization.
- [ ] Có sơ đồ state transition; mỗi transition có actor và điều kiện hợp lệ.
- [ ] Retry matrix phân biệt lỗi tạm thời với ảnh, contract, policy hoặc input không hợp lệ.
- [ ] Có kế hoạch feature flag, migration compatible ngược và rollback ở staging.
- [ ] Lead hoặc reviewer kỹ thuật phê duyệt ADR/contract.

### T0.2 — Cải thiện upload và thông điệp low confidence

**Mục đích:** chặn lỗi người dùng có thể tự sửa trước upload và chuẩn bị UI cho path an toàn.

**Công việc cần làm**

- Thêm preflight trong DiseaseReportPanel: file tồn tại, loại file whitelist, dung lượng tối đa 10 MiB.
- Hiển thị preview, tên/dung lượng và thao tác xoá file đã chọn.
- Thống nhất constants/config giới hạn upload với Backend và AI; server vẫn validate ảnh thật.
- Thiết kế trạng thái LOW_CONFIDENCE_REVIEW: giải thích rõ không phải kết luận, không có khuyến nghị hóa chất, cách chụp lại hoặc gọi kỹ sư.

**Đầu ra bàn giao:** PR frontend, test component và ảnh/recording các trạng thái upload/error.

**Checklist hoàn tất**

- [ ] File quá 10 MiB không gọi API và có thông báo tiếng Việt.
- [ ] MIME/extension sai, file rỗng và preview không render đều có lỗi dễ hiểu.
- [ ] Người dùng có thể thay/xoá ảnh trước khi submit.
- [ ] UI không chỉ dùng màu để báo lỗi; focus và aria-live hoạt động.
- [ ] Backend/AI test vẫn chặn ảnh giả hoặc vượt giới hạn dù client bị bypass.
- [ ] Frontend build và test liên quan pass.

### T0.3 — Baseline performance và khả năng quan sát

**Mục đích:** có số liệu trước khi đổi kiến trúc, tránh dùng cảm nhận để đánh giá async/GraphRAG sau này.

**Công việc cần làm**

- Viết runbook benchmark: cấu hình máy, CPU/RAM/GPU, version image/model, tập ảnh demo và cách đo cold/warm.
- Thêm request ID/correlation ID xuyên Backend và AI; không log secret, ảnh hoặc evidence đầy đủ.
- Đo end-to-end và từng bước CV, facts, RAG, LLM tại concurrency 1, 2, 4, 8.
- Tạo health/readiness riêng cho Backend, AI, Ollama và RAG để biết model/index đã dùng được hay chưa.

**Đầu ra bàn giao:** benchmark report, dashboard/log mẫu và smoke-test report.

**Checklist hoàn tất**

- [ ] Runbook có thể để người khác lặp lại mà không đoán cấu hình.
- [ ] Report có p50/p95/p99, throughput, error rate, CPU/RAM/VRAM và cold/warm.
- [ ] Một request có thể trace được qua Backend và AI bằng correlation ID.
- [ ] Smoke test có các lỗi: AI unavailable, LLM timeout, RAG unavailable, ảnh hỏng và low confidence.
- [ ] Không có secret hoặc nội dung ảnh/evidence nhạy cảm trong log mẫu.

**Gate 0:** T0.1, T0.2 và T0.3 hoàn tất. Contract được review, baseline có thể lặp lại và luồng E2E cũ vẫn pass.

## 5. Backlog Giai đoạn 1 — Diagnosis chạy nền và UX có thể khôi phục

**Mục tiêu giai đoạn:** click chạy AI trả ngay, reload/rớt mạng không mất kết quả, và không có diagnosis trùng.

### T1.1 — Backend job API và persistence

**Mục đích:** tạo job bền vững và API status trước khi nối worker.

**Công việc cần làm**

- Thêm entity/migration DiagnosisJob và attempt/lease tối thiểu.
- Thay endpoint tạo diagnosis AI sang tạo job và trả 202 Accepted.
- Thêm endpoint đọc job theo report, gồm progress, lỗi đã sanitize và diagnosis ID khi thành công.
- Enforce unique job active/report và Idempotency-Key bằng database constraint + transaction.

**Đầu ra bàn giao:** migration, DTO/controller/service, OpenAPI cập nhật và integration tests.

**Checklist hoàn tất**

- [ ] ENGINEER/ADMIN tạo job được; FARMER vẫn bị chặn.
- [ ] Cùng Idempotency-Key trả lại cùng job, không tạo record mới.
- [ ] Double-click/hai tab chỉ tạo một active job.
- [ ] Nếu diagnosis đã tồn tại, API trả trạng thái/kết quả hiện có, không tạo diagnosis thứ hai.
- [ ] Response không lộ stack trace, API key hay URL nội bộ.
- [ ] Migration chạy được trên database sạch và môi trường nâng cấp.

### T1.2 — Worker, retry và recovery

**Mục đích:** chuyển orchestration AI ra khỏi request thread và bảo đảm job không bị kẹt/mất.

**Công việc cần làm**

- Tách phần gọi AI hiện có thành handler worker, vẫn giữ validation contract/rule trước persist.
- Implement publish, consume, atomic lease, lease renewal, concurrency limit và reconciliation job quá hạn lease.
- Implement retry backoff + jitter theo retry matrix; ghi attempt và lý do retry.
- Cập nhật milestone sau các bước: fetch ảnh, CV, facts, RAG, generation, persist.

**Đầu ra bàn giao:** worker chạy được ở staging, retry/recovery tests và runbook xử lý failed job.

**Checklist hoàn tất**

- [ ] Worker không tạo diagnosis trùng khi nhận message trễ hoặc chạy lại sau crash.
- [ ] Worker restart giữa inference và persist không mất job, không apply treatment.
- [ ] Lỗi input/contract/policy thành FAILED_FINAL; lỗi hạ tầng retry đúng giới hạn.
- [ ] Job hết lease được nhận lại hoặc chuyển failure có lý do rõ ràng.
- [ ] RAG fallback được gắn cờ trong status/audit, không bị xem là evidence đầy đủ.
- [ ] Có metric queue depth, queue age, active worker, retry và dead-letter/final failure.

### T1.3 — Polling, resume và progress frontend

**Mục đích:** người dùng hiểu hệ thống đang làm gì và luôn tìm lại được kết quả từ server.

**Công việc cần làm**

- Đổi API client nhận DiagnosisJob thay vì chờ Diagnosis.
- Khi mở report, đọc diagnosis/job mới nhất từ server.
- Poll có backoff, dừng khi unmount/tab ẩn, resume khi quay lại.
- Hiển thị timeline milestone, elapsed time, trạng thái chờ queue và action theo lỗi.

**Đầu ra bàn giao:** PR frontend và E2E recording của submit, reload, success, failure, retry.

**Checklist hoàn tất**

- [ ] Reload trong lúc RUNNING tiếp tục hiển thị đúng job.
- [ ] SUCCEEDED tự tải diagnosis, không yêu cầu chạy lại.
- [ ] FAILED hiển thị action đúng: retry, đổi ảnh hoặc liên hệ kỹ sư/vận hành.
- [ ] Không có polling leak khi đổi report hoặc unmount.
- [ ] Nút AI/manual không tạo thao tác cạnh tranh khi job active.
- [ ] Progress không hiển thị phần trăm giả và có aria-live.

### T1.4 — Kiểm thử luồng async và rollout staging

**Mục đích:** xác minh flow mới chịu được các race/failure thực tế trước khi bật mặc định.

**Checklist hoàn tất**

- [ ] Unit tests cho state transition, idempotency, retry classification, lease expiry và authorization.
- [ ] Integration tests cho double-click, hai tab, retry mạng, diagnosis đã tồn tại, worker crash.
- [ ] Contract tests Backend ↔ AI cho request ID, malformed response, timeout và RAG fallback.
- [ ] E2E test: upload → queue → reload → diagnosis → approve/reject/apply với FARMER/ENGINEER/ADMIN.
- [ ] Feature flag được bật ở staging; có report duplicate rate, final failure rate, p95 completion latency.
- [ ] Có rollback note nếu worker/queue fail khi rollout.

**Gate 1:** T1.1 đến T1.4 hoàn tất. Không có diagnosis trùng trong test race; reload không mất job; worker restart/retry đạt E2E; latency và failure được đo.

## 6. Backlog Giai đoạn 2 — Decision an toàn và vận hành được

**Mục tiêu giai đoạn:** mỗi diagnosis giải thích/tái kiểm tra được; confidence thấp không đi vào chemical flow; hệ thống có SLO, recovery và secret handling.

### T2.1 — Audit trail và replay read-only

**Mục đích:** lưu dấu vết đầy đủ cho mỗi attempt mà không biến replay thành thao tác nghiệp vụ mới.

**Công việc cần làm**

- Thêm DiagnosisRun immutable, timing và evidence/provenance.
- Nâng contract AI để trả evidence ID/revision, score, generation mode, timing theo bước.
- Lưu snapshot input, model, threshold, policy/rule, outcome và error code.
- Cung cấp API/màn hình audit chỉ cho ENGINEER/ADMIN và replay read-only trong môi trường cô lập.

**Đầu ra bàn giao:** migration, API audit, một audit trail mẫu hoàn chỉnh và test quyền truy cập.

**Checklist hoàn tất**

- [ ] Từ diagnosis có thể truy về job, attempt, request ID, image checksum, model/version, policy/rule snapshot và timing.
- [ ] Evidence có source/chunk/revision/score; nội dung nhạy cảm được redact theo policy.
- [ ] Rule/model/prompt mới không làm mất khả năng xem run cũ.
- [ ] Replay không tạo Diagnosis hoặc TreatmentApplication mới.
- [ ] Audit API bị chặn với FARMER và phân trang được.
- [ ] Test migration/backfill và authorization pass.

### T2.2 — DiseaseManagementPolicy và deterministic rule resolver

**Mục đích:** loại bỏ việc AI chọn rule đầu tiên và đưa quyết định deterministic về Backend.

**Công việc cần làm**

- Chốt owner/chuyên gia phê duyệt các policy path, priority, effective time, crop/growth stage và safety constraints.
- Thêm PolicyResolution và resolver ở Backend.
- Resolver chọn một rule hợp lệ hoặc trả NON_CHEMICAL_MANAGEMENT, REVIEW_REQUIRED, LOW_CONFIDENCE_REVIEW hoặc BLOCKED_BY_SAFETY_POLICY.
- AI chỉ diễn đạt policy snapshot Backend đã chọn; Backend validate lại trước persist.

**Đầu ra bàn giao:** API nội bộ versioned, decision table, tests và UI hiển thị policy path.

**Checklist hoàn tất**

- [ ] Không còn logic chọn rule theo thứ tự ID ở AI hoặc Backend.
- [ ] Resolver xét eligibility, status/effective time, crop/growth stage, recent treatment, priority và tie-breaker.
- [ ] Hai rule ngang hạng cần quyết định nông học trả REVIEW_REQUIRED.
- [ ] Mỗi path có machine reason code, human reason và hành vi UI/apply rõ ràng.
- [ ] Contract cũ có migration/feature flag hoặc compatibility plan.
- [ ] Decision-table tests cover priority, expired rule, missing context, recent treatment và safety block.

### T2.3 — Low confidence và image-quality gate

**Mục đích:** ảnh có confidence thấp hoặc chất lượng không đủ không thể dẫn tới treatment recommendation.

**Công việc cần làm**

- Chọn threshold từ benchmark/evaluation, có owner và version trong audit.
- Đưa low confidence vào contract chính thức và chuyển path sang LOW_CONFIDENCE_REVIEW.
- Đánh giá quality checks cho ảnh không phải lá, mờ, tối hoặc out-of-distribution.
- Báo cáo accuracy/calibration và giới hạn giữa PlantVillage với ảnh ruộng thật.

**Checklist hoàn tất**

- [ ] Confidence ngay dưới/bằng/trên threshold có test xác định.
- [ ] LOW_CONFIDENCE_REVIEW không có selected chemical rule và Backend chặn apply.
- [ ] Quality check không chắc chắn chỉ cảnh báo/review, không âm thầm loại bỏ ảnh.
- [ ] UI hướng dẫn thao tác tiếp theo, không thể hiện kết quả như kết luận chắc chắn.
- [ ] Báo cáo evaluation và limitation được review trước pilot.

### T2.4 — SLO, recovery, secret và release gate

**Mục đích:** đưa luồng từ chạy được sang vận hành có kiểm soát.

**Công việc cần làm**

- Xuất metrics job, queue, retry, model readiness, RAG fallback, API error và resource usage; dựng dashboard/alert/runbook.
- Load test warm/cold/concurrency; chốt SLO dựa trên số đo, không dùng timeout hiện tại làm SLO.
- Recovery drill queue/DB/AI/Ollama/RAG restart, retry exhausted và rolling deploy worker.
- Tách secret/config theo local, staging, production; secret scan và least privilege.

**Checklist hoàn tất**

- [ ] Dashboard và alert có owner, ngưỡng, runbook cho queue stalled, failure burst, model/index not ready, latency regression.
- [ ] Benchmark report so sánh baseline và async flow, có throughput, completion latency, CPU/RAM/VRAM.
- [ ] SLO demo/staging được lead chấp thuận.
- [ ] Recovery drill không mất audit hoặc tạo diagnosis trùng.
- [ ] API key/password thật không nằm trong source, image hoặc log; secret scan CI pass.
- [ ] Threat model, rollback plan, model limitation và runbook được review.

**Gate 2:** T2.1 đến T2.4 hoàn tất. Một diagnosis mẫu có audit trọn vẹn; policy không chọn rule theo ID; low confidence không thể dẫn tới chemical apply; SLO/recovery/secret/runbook đã được duyệt.

## 7. Backlog Giai đoạn 3 — Chỉ mở sau Gate 2

### T3A.1 — Carbon/AWD contract và IoT simulator

**Mục đích:** tạo môi trường mô phỏng tái lập trước khi nhận telemetry thật.

**Công việc cần làm**

- Chốt persona, crop/parcel, timezone, đơn vị, telemetry frequency, offline behavior và ranh giới advisory so với automatic control.
- Chốt methodology estimator: scope, assumptions, input, CO2e output, uncertainty/data-quality, version và disclaimer.
- Chốt AWD policy do chuyên gia phê duyệt: threshold, hysteresis, safe override, growth stage và điều kiện REVIEW_REQUIRED.
- Thiết kế telemetry event: event ID, device identity, parcel/crop season, observed time UTC, metric/unit/value, quality, source, signature và schema version.
- Viết simulator các scenario normal, dry, wet, sensor offline, duplicate, out-of-order với clock điều khiển được.

**Checklist hoàn tất**

- [ ] Có contract event và validation rule được review.
- [ ] Simulator lặp lại cùng output với cùng scenario/clock.
- [ ] Scenario có cả data tốt, data thiếu, duplicate, late/out-of-order và sensor offline.
- [ ] Documentation nói rõ advisory không điều khiển actuator.
- [ ] Methodology/threshold chưa duyệt luôn dẫn tới REVIEW_REQUIRED.

### T3A.2 — Telemetry ingestion, AWD evaluator và carbon estimator

**Mục đích:** biến event mô phỏng thành advisory/estimation truy được nguồn.

**Công việc cần làm**

- Implement ingestion có signature/auth, idempotency, schema/unit/range validation, dedup, out-of-order rule, dead-letter và monitoring.
- Implement AWD evaluator tách khỏi transport; lưu telemetry window, policy/methodology version, reason, expiry.
- Implement carbon estimator lưu input snapshot, output range/uncertainty và data-quality state.
- Thêm UI timeline telemetry, freshness/quality, advisory/lý do và acknowledgement.

**Checklist hoàn tất**

- [ ] Telemetry stale/unknown trả REVIEW_REQUIRED, không trả hướng dẫn tưới chắc chắn.
- [ ] Duplicate và out-of-order không thay đổi sai trạng thái/advisory.
- [ ] Output truy được telemetry window, policy và methodology version.
- [ ] Không trả số carbon chính xác giả khi input thiếu.
- [ ] E2E simulator → ingestion → evaluator → dashboard pass.

### T3A.3 — Pilot telemetry thật

**Checklist hoàn tất**

- [ ] Chỉ bật cho device/parcel allowlist bằng feature flag.
- [ ] Có kill switch và runbook khi device gửi lỗi hoặc telemetry drift.
- [ ] Đối soát event mô phỏng/thực địa trước khi mở rộng.
- [ ] Không có đường API/UI điều khiển bơm, van hoặc thiết bị phun.

### T3B.1 — Đánh giá GraphRAG trước khi đầu tư

**Mục đích:** chứng minh nhu cầu trước khi thêm graph hoặc agent.

**Công việc cần làm**

- Viết bộ câu hỏi representative và rubric: groundedness, citation correctness, policy adherence, latency, cost.
- Chạy baseline bằng vector RAG hiện tại.
- Chốt KPI adopt/defer/reject và người quyết định.

**Checklist hoàn tất**

- [ ] Baseline có điểm chất lượng, latency/resource/cost và evidence review.
- [ ] Rubric kiểm tra citation, policy safety, hallucination và fallback.
- [ ] KPI có ngưỡng thành công và điều kiện dừng rõ ràng.
- [ ] Không có code GraphRAG/multi-agent production trước khi review baseline.

### T3B.2 — GraphRAG spike và quyết định

**Công việc cần làm**

- Xác định ontology tối thiểu: disease, crop, symptom, policy, evidence; Backend facts vẫn authoritative và graph chỉ read-only.
- Làm spike nhỏ có provenance, revision, access control và fallback vector RAG.
- So sánh với baseline, công bố adopt/defer/reject.

**Checklist hoàn tất**

- [ ] Mỗi kết quả spike có provenance/revision và fallback vector RAG.
- [ ] Không có ghi trực tiếp nghiệp vụ từ graph layer.
- [ ] Báo cáo so sánh quality, latency, resource, cost được review.
- [ ] Nếu không vượt KPI, đóng workstream thay vì mở rộng thêm công nghệ.

### T3B.3 — Workflow phức tạp hoặc multi-agent, chỉ khi T3B.2 adopt

**Công việc cần làm**

- Thiết kế typed input/output và state machine trước khi tách agent.
- Mỗi node có timeout, retry budget, idempotency, trace, safe fallback.
- Mỗi agent có role, tool allowlist, read-only scope, output schema và policy check sau handoff.
- Chạy shadow mode trước canary.

**Checklist hoàn tất**

- [ ] Không agent nào gọi approve/apply, đổi policy/rule hoặc ghi trực tiếp nghiệp vụ.
- [ ] Backend validator vẫn là final gate cho mọi output agent.
- [ ] CI có regression cho factual/policy/citation/adversarial tool test và latency/cost budget.
- [ ] Shadow report có mismatch, hallucination, fallback rate, cost trước khi canary.

### T3C.1 — Policy và lifecycle xác nhận đã phun từ IoT

**Mục đích:** chốt ý nghĩa nghiệp vụ/pháp lý trước khi tự động xác nhận bất kỳ treatment application nào.

**Công việc cần làm**

- Chốt bằng chứng đủ CONFIRMED, các điều kiện EXCEPTION, actor chịu trách nhiệm, retention và dispute handling.
- Chốt lifecycle: APPROVED_FOR_APPLICATION → AWAITING_IOT_CONFIRMATION → CONFIRMED hoặc EXCEPTION/EXPIRED.
- Chốt manual fallback có reason/audit.

**Checklist hoàn tất**

- [ ] Policy owner/chuyên gia phê duyệt điều kiện auto-confirm.
- [ ] Không có event IoT nào xác nhận diagnosis chưa APPROVED.
- [ ] Manual fallback không overwrite dấu vết IoT hoặc decision cũ.
- [ ] UI/backend có transition guard cho mọi trạng thái.

### T3C.2 — Xác thực event và correlation engine

**Công việc cần làm**

- Thiết kế signed event: event ID, device, timestamp, location/parcel, crop season, diagnosis/rule correlation, pesticide, dose/unit, coverage, calibration, firmware/schema version.
- Implement device registry, credential rotation, signature verification, replay protection, idempotency, clock-skew/geofence và device-to-parcel authorization.
- Implement correlation engine chỉ match diagnosis APPROVED, rule hợp lệ, parcel/crop season/window đúng.

**Checklist hoàn tất**

- [ ] Forged signature, duplicate, wrong parcel/rule/dose, late/out-of-order bị từ chối hoặc EXCEPTION.
- [ ] Match mơ hồ không tự chọn bản ghi đầu tiên.
- [ ] Dose/unit/coverage/calibration/firmware mismatch không auto-confirm.
- [ ] Confirm lưu event reference immutable, validation result, rule snapshot, resolver version và actor IOT.
- [ ] Không tạo application khi validation không đầy đủ.

### T3C.3 — UI exception và pilot IoT

**Checklist hoàn tất**

- [ ] Kỹ sư thấy trạng thái chờ IoT, evidence tóm tắt, exception queue và manual confirm/reject có lý do.
- [ ] E2E simulator cover event hợp lệ, lỗi xác thực, duplicate, mismatch, mất mạng và manual fallback.
- [ ] Pilot chỉ chạy theo device/parcel allowlist, feature flag và kill switch.
- [ ] Có đối soát event–thực địa trước khi tăng phạm vi.
- [ ] Auto-confirm không đồng nghĩa auto-actuation; không có API/UI gửi lệnh phun.

## 8. Checklist chung cho mọi task

- [ ] Có issue/task, owner, phạm vi, acceptance criteria và link tài liệu liên quan.
- [ ] Contract/API/schema thay đổi có version, migration/rollback, compatibility note.
- [ ] Đã xét authorization, idempotency, retry/failure, observability; không chỉ happy path.
- [ ] Có unit test; integration/contract test khi đi qua Backend–AI/IoT; E2E khi thay đổi luồng người dùng.
- [ ] Không commit secret, ảnh/telemetry thật hoặc audit output nhạy cảm; log/error đã redact.
- [ ] Build/lint/test liên quan pass; compose config và smoke pass nếu thay đổi deploy.
- [ ] README, OpenAPI, runbook, dashboard/alert liên quan được cập nhật cùng PR.
- [ ] Reviewer xác nhận AI/agent không bypass Backend policy, auto-confirm không thành auto-actuation, fallback an toàn và audit đầy đủ.

## 9. Thứ tự các phiên làm việc

| Phiên | Chỉ làm các task | Điều kiện bắt đầu | Bàn giao tối thiểu |
|---|---|---|---|
| S1 | T0.1 | Không có | ADR + contract review |
| S2 | T0.2 | T0.1 chốt path/status | PR frontend + test/build |
| S3 | T0.3 | Không có dependency code | Benchmark/runbook + smoke report |
| S4 | T1.1 | Gate 0 | Migration + API + integration tests |
| S5 | T1.2 | T1.1 | Worker + recovery/retry report |
| S6 | T1.3 và T1.4 | T1.1, T1.2 | E2E + staging rollout report |
| S7 | T2.1 | Gate 1 | Audit trail + authorization tests |
| S8 | T2.2 và T2.3 | T2.1 contract ready | Policy tests + safety UI |
| S9 | T2.4 | T2.2, T2.3 | SLO + drill + hardening evidence |
| S10 | Gate 2 review, chọn T3A/T3B/T3C | T2.1 đến T2.4 | Gate checklist + owner/priority Giai đoạn 3 |

## 10. Definition of done toàn chương trình

Chương trình hoàn tất trong phạm vi này khi ENGINEER có thể gửi AI job, đóng/reload trình duyệt và quay lại thấy tiến trình/kết quả không trùng lặp; diagnosis có audit từ input đến evidence/policy/model/timing; low-confidence hoặc thiếu policy không thể dẫn tới chemical apply; có benchmark, SLO, recovery drill, secret handling và runbook; mỗi hạng mục Giai đoạn 3 cũng đạt checklist hoàn tất của riêng nó.
