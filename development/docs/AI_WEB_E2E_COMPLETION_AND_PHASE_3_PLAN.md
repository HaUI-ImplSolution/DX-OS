# Kế hoạch hoàn thiện AI Web E2E và chuẩn bị Giai đoạn 3

> **Trạng thái:** Backlog nâng cấp, chưa bắt đầu.
>
> **Cập nhật:** 23/09/2026.
>
> **Nguồn trạng thái hiện tại:** [AI Web E2E Demo](AI_WEB_E2E_DEMO_README.md).
>
> **Phạm vi:** Hoàn thiện các pain point còn lại của luồng AI Web E2E, sau đó chuẩn bị ba hạng mục Giai đoạn 3 trong [README gốc](../../README.md). Đây không phải kế hoạch data/database.

## 1. Phạm vi và giả định tạm thời

Checklist này dùng tên **Nâng cấp A–C** để không nhầm với Giai đoạn 0–3 của README tổng.

Trong lượt nâng cấp này:

- Không thiết kế lại database, không thêm migration framework và không backfill dữ liệu cũ.
- Không thay corpus RAG, seed, schema `documents`/`document_chunks` hoặc schema nghiệp vụ hiện tại.
- Nếu một task cần dữ liệu chưa có, task phải dùng fixture/simulator nhỏ, ghi rõ giả định và không trình bày kết quả giả định như dữ liệu thực tế.
- Mọi schema persistence mới như `DiagnosisJob`, `DiagnosisRun`, policy, telemetry hoặc IoT event store được hoãn đến khi hai bên chốt riêng kế hoạch data.
- Database sạch, 38 disease, 4 pesticide, 8 treatment rule và corpus local hiện tại được xem là baseline demo, không phải dữ liệu production hay khuyến cáo chuyên môn.
- Hệ thống vẫn là single-instance demo. Chưa cam kết job sống qua restart, audit dài hạn hoặc vận hành production.

Các giả định trên phải được nhắc lại trong PR/README của task có sử dụng chúng.

## 2. Bất biến không được phá vỡ

- Backend là nguồn sự thật duy nhất cho dữ liệu nghiệp vụ, treatment rule, PHI, phê duyệt và ghi nhận xử lý.
- AI stateless, không có credential vào Backend DB và chỉ gọi internal REST API chỉ đọc.
- RAG chỉ cung cấp evidence để giải thích; thuốc, liều, PHI và policy không được lấy từ RAG hoặc do LLM/agent tự tạo.
- Mọi khuyến nghị hóa chất phải qua ENGINEER hoặc ADMIN; FARMER không được gọi endpoint chẩn đoán/quyết định.
- Low confidence, thiếu rule, nhiều rule chưa có tiêu chí chọn hoặc dữ kiện không đủ phải đi vào review; không được chọn đại rule đầu tiên.
- IoT chỉ xác nhận sự kiện đã xảy ra, không điều khiển bơm, van hoặc thiết bị phun.
- Mọi dependency/image/model mới phải có license OSI-approved và được cập nhật vào `LICENSES.md` trước khi merge.
- Mã nguồn tự viết mới phải giữ Apache-2.0/SPDX theo quy định OLP 2026 của repo.

## 3. Baseline đã hoàn thành — không làm lại

Các mục dưới đây là trạng thái đã có và chỉ cần giữ bằng regression test:

- [x] Docker Compose dựng được Frontend, Backend, AI, PostgreSQL/pgvector, Ollama, Keycloak, Superset, Mattermost và portal.
- [x] FARMER tạo thửa, vụ mùa và báo cáo ảnh; ENGINEER/ADMIN chạy diagnosis, duyệt/từ chối và ghi nhận xử lý.
- [x] Backend gọi `POST /api/v1/diagnose`; AI gọi `GET /internal/v1/diagnosis-context` bằng service API key.
- [x] AI chạy CV → Backend structured facts → semantic RAG → Ollama và trả recommendation stateless.
- [x] Backend xác minh `rule_id`/version, lưu `Diagnosis` ở `PENDING` và chặn apply khi không có rule.
- [x] Liều ngoài khoảng bị Backend chặn; liều hợp lệ tạo `TreatmentApplication` và khóa thu hoạch theo PHI.
- [x] Backend DB và AI Vector DB đã tách quyền; AI runtime chỉ đọc vector DB, job ingest dùng credential riêng.
- [x] RAG ingest hiện tại tạo 38 documents, 270 chunks/embeddings trên database sạch.
- [x] Nhánh có rule và `review_required` không có rule đều đã chạy E2E với ba role.
- [x] Docker healthcheck, Backend test/formatter và hai AI contract self-check đã pass ở baseline.

Không mở task để viết lại các phần trên nếu không có regression hoặc yêu cầu contract mới cụ thể.

## 4. Pain point còn lại

| Pain point hiện tại | Xử lý trong checklist | Trạng thái |
|---|---|---|
| Frontend chưa preflight file, chỉ có `accept="image/*"` | A1 | Làm ngay |
| Low-confidence chỉ là cờ nội bộ, vẫn có thể đi vào treatment path | A2 | Làm ngay |
| AI chọn rule đầu tiên khi Backend trả nhiều rule | A3 | Làm ngay theo fail-safe |
| Request AI đồng bộ có thể giữ HTTP tới 120 giây | A4 giảm tác động; async durable hoãn | Có giả định |
| Rớt mạng có thể khiến UI không biết diagnosis đã được lưu | A4 refetch trạng thái từ Backend | Làm ngay |
| Chưa có correlation/log và benchmark lặp lại được | B1, B2 | Làm ngay |
| Chưa lưu audit/replay bền vững | Hoãn vì cần persistence/schema | Deferred |
| Chưa có DiseaseManagementPolicy đầy đủ | Dùng rule hiện tại; trường hợp mơ hồ trả review | Có giả định |
| M8 chưa chứng minh citation từ bộ tài liệu VietGAP/IRRI đã duyệt | C1 | Chưa làm |
| License AI/model và dependency mới chưa được khóa đầy đủ | B3 | Bắt buộc trước Gate B |
| Carbon/AWD, GraphRAG/multi-agent, IoT confirmation | C2–C4 | Chỉ mở sau Gate B |

## 5. Nâng cấp A — An toàn và UX trên luồng hiện tại

### A1 — Upload preflight phía Frontend

**Công việc**

- Kiểm tra file tồn tại, không rỗng, MIME/extension được phép và tối đa 10 MiB trước khi gọi API.
- Hiển thị preview, tên, dung lượng và cho phép thay/xóa ảnh.
- Giữ validation 10 MiB ở Backend và AI làm lớp quyết định cuối; không tin validation client.

**Hoàn thành khi**

- [ ] File sai loại, rỗng hoặc quá 10 MiB không gọi API và có thông báo tiếng Việt.
- [ ] Lỗi có focus/`aria-live`, không chỉ biểu diễn bằng màu.
- [ ] Bypass Frontend vẫn bị Backend/AI từ chối.
- [ ] Frontend build và test liên quan pass.

### A2 — Low-confidence trở thành safety gate thật

**Giả định:** chưa thêm bảng policy/threshold. Threshold dùng config hiện tại, phải xuất hiện trong log/response để truy vết trong phạm vi một lần chạy.

**Công việc**

- Chốt contract `LOW_CONFIDENCE_REVIEW` giữa AI và Backend.
- Khi confidence dưới threshold, AI không chọn chemical rule; Backend kiểm tra lại và chặn apply.
- UI nói rõ đây không phải kết luận chắc chắn và hướng dẫn chụp lại/gọi kỹ sư.
- Đánh giá tối thiểu ảnh mờ, tối, không phải lá hoặc ngoài phân phối; trường hợp không chắc chắn luôn đi review.

**Hoàn thành khi**

- [ ] Có test ngay dưới, bằng và trên threshold.
- [ ] Low confidence không có `selected_rule_id` và không thể tạo `TreatmentApplication`.
- [ ] ENGINEER vẫn xem được CV result/evidence để đánh giá, nhưng UI không trình bày như kết luận chắc chắn.
- [ ] Threshold và model version xuất hiện trong bằng chứng test.

### A3 — Bỏ chọn rule đầu tiên

**Giả định:** chưa có DiseaseManagementPolicy, priority, effective time hoặc dữ liệu chuyên môn đủ để xếp hạng nhiều rule.

**Cách tạm thời an toàn**

- Không bịa priority và không chọn theo ID.
- Một rule hợp lệ duy nhất có thể đi `treatment`.
- Không có rule hoặc có nhiều rule nhưng thiếu tiêu chí deterministic phải trả `REVIEW_REQUIRED`; Backend chặn apply tự động.

**Hoàn thành khi**

- [ ] Không còn `actions[0]` hoặc logic tương đương để quyết định treatment.
- [ ] Có test cho 0, 1 và nhiều rule.
- [ ] AI không tự tạo thuốc/liều/PHI để giải quyết trường hợp mơ hồ.
- [ ] Backend là final validator cho path và rule.

### A4 — Khôi phục UI khi request chậm hoặc rớt mạng

**Giả định:** vẫn giữ HTTP đồng bộ và timeout hiện tại; không tạo queue, worker hoặc bảng `DiagnosisJob` trong lượt này.

**Công việc**

- Sau timeout/network error, Frontend refetch report để phát hiện diagnosis đã được Backend lưu.
- Chặn double-click và thao tác cạnh tranh trong lúc request đang chạy.
- Hiển thị bước mô tả thật (`đang gửi`, `đang chẩn đoán`, `đang kiểm tra kết quả`), không hiển thị phần trăm giả.
- Ghi rõ giới hạn: Backend/AI restart giữa request vẫn có thể làm mất lượt chạy; async durable là task sau khi chốt data.

**Hoàn thành khi**

- [ ] Request thành công cập nhật diagnosis như hiện tại.
- [ ] Request rớt sau khi Backend đã lưu vẫn tự tìm lại diagnosis qua refetch.
- [ ] Double-click không tạo hai diagnosis.
- [ ] UI có thông báo hành động tiếp theo khi AI unavailable/timeout.
- [ ] E2E ba role hiện tại vẫn pass.

**Gate A:** A1–A4 hoàn tất; low confidence và nhiều rule không thể đi nhầm vào chemical apply; luồng E2E hiện tại không regression.

## 6. Nâng cấp B — Đo được, kiểm tra được và đủ điều kiện OLP

### B1 — Correlation và observability không thêm persistence

**Giả định:** log chỉ phục vụ phiên demo; chưa phải audit store dài hạn.

- Truyền một correlation/request ID xuyên Backend → AI → Backend internal facts.
- Log milestone và timing CV, facts, RAG, LLM, persist; không log secret, ảnh hoặc toàn bộ evidence.
- Tách health/readiness để biết Backend, CV model, Ollama và RAG index đã sẵn sàng.

**Hoàn thành khi**

- [ ] Một request tìm được xuyên hai service bằng cùng correlation ID.
- [ ] Log có timing theo bước và error code đã sanitize.
- [ ] Readiness fail đúng khi model/index/dependency bắt buộc chưa sẵn sàng.
- [ ] Không có secret hoặc dữ liệu nhạy cảm trong log mẫu.

### B2 — Baseline và regression có thể lặp lại

- Viết runbook cấu hình máy, image/model version, tập ảnh demo và cách đo cold/warm.
- Đo concurrency 1, 2, 4; chỉ tăng lên 8 nếu tài nguyên máy cho phép.
- Test AI unavailable, LLM timeout, RAG unavailable, ảnh hỏng, low confidence, 0/1/nhiều rule.
- Giữ E2E FARMER → ENGINEER/ADMIN → approve/reject/apply làm smoke test chuẩn.

**Hoàn thành khi**

- [ ] Report có p50/p95, throughput, error rate, CPU/RAM và cold/warm.
- [ ] Người khác chạy lại được bằng runbook mà không đoán cấu hình.
- [ ] Contract self-check, Backend test, Frontend build và Compose smoke pass.
- [ ] Kết quả benchmark không được diễn giải thành SLO production.

### B3 — License và hồ sơ nguồn mở

- Rà toàn bộ dependency Python/model checkpoint của AI đang thực sự chạy.
- Mọi queue, GraphRAG, telemetry, benchmark hoặc observability dependency dự kiến thêm phải được kiểm tra OSI trước khi chọn.
- Cập nhật `LICENSES.md`, attribution/model source và SPDX của file tự viết mới.
- Không dùng dependency source-available/non-OSI để “thử tạm” trong stack nộp thi.

**Hoàn thành khi**

- [ ] AI dependencies và model có nguồn/license rõ ràng trong `LICENSES.md`.
- [ ] Dependency mới đều OSI-approved và build/cài được từ source theo yêu cầu repo.
- [ ] Không còn mục “AI Service chưa rà” trong `LICENSES.md`.
- [ ] License/secret scan pass trước khi merge.

**Gate B:** Gate A, B1–B3 hoàn tất; baseline lặp lại được, failure path có bằng chứng và toàn bộ stack dự kiến dùng cho bước tiếp theo đạt điều kiện license OLP.

## 7. Nâng cấp C — Giai đoạn 3 của README tổng

Chỉ mở sau Gate B. Các task dưới đây ưu tiên contract, simulator và spike; chưa mở rộng database.

### C1 — Chứng minh citation RAG hiện tại trước GraphRAG

- Chọn bộ câu hỏi representative và rubric groundedness/citation/policy adherence.
- Chứng minh ít nhất một bộ VietGAP/IRRI đã duyệt được ingest và citation truy ngược đúng nguồn.
- Đo chất lượng, latency và fallback của vector RAG hiện tại.

**Hoàn thành khi**

- [ ] M8 trong README tổng có bằng chứng để đánh dấu xong.
- [ ] Citation trỏ đúng source registry/chunk; không chỉ in mã nguồn không kiểm chứng.
- [ ] Thiếu evidence trả fallback/review, không sinh kết luận chắc chắn.

### C2 — Carbon/AWD estimator và IoT simulator

**Giả định data:** dùng fixture versioned cho parcel/crop season/telemetry; không tuyên bố là số liệu thực địa và chưa persist event.

- Chốt methodology, nguồn IRRI/SECTOR/Đề án 1 triệu hecta, input, đơn vị, uncertainty và disclaimer.
- Chốt AWD advisory threshold/hysteresis/safe override; không điều khiển actuator.
- Thiết kế event contract và simulator deterministic cho normal, dry, wet, offline, duplicate, late/out-of-order.
- Estimator trả range/uncertainty và `REVIEW_REQUIRED` khi input hoặc methodology chưa đủ.

**Hoàn thành khi**

- [ ] Cùng fixture/clock cho cùng output.
- [ ] Output truy được methodology version và input fixture.
- [ ] Không trả con số carbon chính xác giả khi dữ liệu thiếu.
- [ ] Tài liệu dùng đúng tên “Mô-đun Ước Lượng Giảm Phát Thải Tham Chiếu Thực Nghiệm” và disclaimer theo README.

### C3 — Đánh giá GraphRAG/multi-agent

**Giả định data:** spike chỉ dùng corpus local đã duyệt; không tạo graph nghiệp vụ authoritative và không ghi Backend DB.

- Chạy baseline C1 trước khi viết GraphRAG.
- Chốt KPI adopt/defer/reject về groundedness, citation, latency và tài nguyên.
- Nếu cần, làm spike ontology tối thiểu disease/crop/symptom/evidence; Backend facts vẫn authoritative.
- Chỉ xem xét multi-agent nếu GraphRAG vượt KPI và workflow thực sự cần phân vai.

**Hoàn thành khi**

- [ ] Spike có provenance/revision và fallback vector RAG.
- [ ] Không graph/agent nào gọi approve/apply hoặc tự sửa policy/rule.
- [ ] Có báo cáo so sánh với baseline; không vượt KPI thì đóng workstream.
- [ ] Dependency dùng trong spike đạt Gate B3 trước khi nhập vào stack chính.

### C4 — Xác nhận đã phun từ IoT

**Trạng thái:** chỉ thiết kế contract/simulator. Auto-confirm thật bị hoãn vì cần lifecycle, event persistence, device registry và quyết định data riêng.

- Chốt lifecycle dự kiến: `APPROVED_FOR_APPLICATION → AWAITING_IOT_CONFIRMATION → CONFIRMED | EXCEPTION | EXPIRED`.
- Chốt signed event, replay protection, parcel/rule/dose/time correlation và manual fallback có reason.
- Dùng simulator kiểm tra event hợp lệ, forged, duplicate, wrong parcel/rule/dose và late/out-of-order.

**Hoàn thành trong phạm vi hiện tại khi**

- [ ] Contract và transition guard được review.
- [ ] Không event nào xác nhận diagnosis chưa APPROVED.
- [ ] Match mơ hồ hoặc validation thiếu luôn vào `EXCEPTION`, không tự chọn bản ghi đầu tiên.
- [ ] Không có API/UI gửi lệnh điều khiển thiết bị.
- [ ] Tài liệu ghi rõ auto-confirm thật chưa hoàn thành cho đến khi kế hoạch data được chốt.

## 8. Các hạng mục hoãn do phụ thuộc data

Không triển khai các mục sau trong checklist này:

- Queue/worker durable, lease, dead-letter và job recovery sau restart.
- Bảng `DiagnosisJob`, `DiagnosisRun`, audit/replay store và backfill.
- DiseaseManagementPolicy/priority/effective-time persistence đầy đủ.
- Telemetry/event store, device registry và IoT auto-confirm thật.
- Migration framework, migration lịch sử hoặc hỗ trợ nhiều database version.
- Đóng gói corpus/seed mới hoặc thay đổi ownership giữa Backend DB và AI Vector DB.

Khi buộc phải chạm một mục trên, dừng task và mở quyết định riêng với Backend/AI lead; không tự mở rộng phạm vi.

## 9. Checklist chung cho mọi task

- [ ] Phạm vi chỉ thuộc đúng task; không làm lại baseline đã hoàn thành.
- [ ] Nếu dùng fixture/giả định, ghi rõ nguồn, giới hạn và đường thay thế sau này.
- [ ] Không thay đổi database/schema/seed/corpus nếu chưa có quyết định riêng.
- [ ] Authorization, validation, failure/fallback và observability được kiểm tra, không chỉ happy path.
- [ ] Có test nhỏ nhất bảo vệ logic mới; contract/E2E test khi đi qua nhiều service.
- [ ] Không commit secret, ảnh/telemetry thật hoặc output nhạy cảm.
- [ ] Dependency mới đã qua OSI/license review và cập nhật `LICENSES.md`.
- [ ] Build/lint/test/Compose smoke liên quan pass.
- [ ] README, OpenAPI, runbook và changelog liên quan được cập nhật ngắn gọn cùng PR.
- [ ] AI/agent không bypass Backend; fallback fail-safe; IoT confirmation không biến thành auto-actuation.

## 10. Thứ tự triển khai

| Thứ tự | Task | Điều kiện |
|---|---|---|
| 1 | A1 | Baseline hiện tại |
| 2 | A2 và A3 | Chốt contract safety, không cần thay data |
| 3 | A4 | A2/A3 ổn định response path |
| 4 | B1 và B2 | Gate A |
| 5 | B3 | Có thể chạy song song, nhưng bắt buộc trước Gate B |
| 6 | C1 | Gate B |
| 7 | Chọn C2 hoặc C3 | C1 xong; Lead chốt ưu tiên |
| 8 | C4 contract/simulator | C2 có event contract; chưa làm auto-confirm thật |

## 11. Definition of done trong phạm vi này

Checklist hoàn tất khi luồng E2E hiện tại vẫn chạy qua ba role; upload và low-confidence được xử lý an toàn; nhiều rule không còn bị chọn theo thứ tự; UI tự kiểm tra lại kết quả sau lỗi mạng; request có thể trace và benchmark lặp lại; license AI/dependency đáp ứng OLP; vector RAG có citation đã chứng minh; các spike Giai đoạn 3 được đánh giá bằng fixture/giả định minh bạch và không tạo cảm giác rằng data persistence hoặc IoT auto-confirm thật đã hoàn thành.
