# Giải thích kế hoạch Web E2E và phạm vi ảnh hưởng

Tài liệu này giải thích bằng tiếng Việt đơn giản các hạng mục trong kế hoạch Web E2E. Trạng thái seed được đối chiếu theo [DB Seed worklog](DB_SEED_UNIFICATION_TWO_DAY_WORKLOG.md).

## Bức tranh chung

Luồng diagnosis Web có thể chạy theo hai phạm vi kiểm tra khác nhau. Worklog DB seed chỉ xác minh đường chẩn đoán thủ công: FARMER tạo thửa, vụ mùa và báo cáo; ENGINEER tạo diagnosis; ENGINEER/ADMIN duyệt và ghi nhận liều hợp lệ. Lượt kiểm tra này không gọi CV, RAG hoặc Ollama, nên không phải bằng chứng mới cho chất lượng AI.

Các nhóm bên dưới chỉ nói về phần còn lại quanh luồng này. Chúng không được thay đổi baseline: quyền FARMER/ENGINEER/ADMIN, seed hiện có, RAG ingest, khoảng liều và khóa PHI.

## Baseline sau khi hoàn thành DB seed

- Database sạch start lặp ổn định với **38 disease, 6 pesticide, 1 active treatment rule và 13 disease management policy**.
- Rule active duy nhất là `TR-CAND-004`, được cho phép dưới nhãn `E2E_ASSUMPTION` để kiểm thử local/demo. Đây không phải phê duyệt chuyên gia hoặc dữ liệu production; 15 candidate còn lại vẫn bị chặn.
- Internal API chỉ trả rule active: `Tomato___Early_blight` trả rule E2E cùng version hiện hành; bệnh chỉ có rule inactive trả danh sách rỗng.
- E2E manual xác nhận FARMER bị chặn chẩn đoán (`403`); ENGINEER tạo diagnosis `PENDING`; ADMIN duyệt; liều `1.49` bị chặn (`409`), liều `1.50` được lưu và khóa PHI 7 ngày.
- Worklog không thay đổi AI Vector DB, documents/document_chunks, embedding hoặc job RAG ingest. Vì vậy, số liệu corpus RAG và kết quả CV → RAG → Ollama phải được xác minh bằng kiểm tra AI E2E riêng, không suy ra từ worklog seed.

## Có thể thực hiện ngay

### Validation ảnh trước và sau upload

Trình duyệt báo sớm nếu ảnh quá lớn, sai định dạng hoặc không đọc được. Backend kiểm tra lại nội dung ảnh thật trước khi lưu để không thể lách bằng cách đổi tên file hoặc khai báo sai MIME type.

**Phạm vi ảnh hưởng:** `frontend/src/components/DiseaseReportPanel.tsx`; `backend/DX-Farm/src/main/java/**/service/FileStorageService.java`; test upload. Không đổi API diagnosis, AI contract, schema nghiệp vụ hoặc luồng duyệt.

### Async diagnosis, recovery và progress UI

Diagnosis được chuyển thành job chạy nền. Người dùng không cần giữ trang chờ; khi trở lại sau khi rớt mạng, giao diện lấy lại trạng thái/kết quả và hiển thị bước xử lý thực tế.

**Phạm vi ảnh hưởng:** Backend diagnosis API/service/model/repository; AI client; frontend API và `DiagnosisPanel`; worker/job store; Docker Compose hoặc cấu hình chạy worker. Quyền role, `TreatmentRule`, liều và PHI vẫn phải giữ nguyên.

### Mattermost notification

Backend gửi notification khi có event nghiệp vụ đã chọn, ví dụ report mới hoặc diagnosis cần kỹ sư xem xét. Trong phạm vi hiện tại, đây là thông báo webhook phục vụ demo; không yêu cầu transactional outbox.

**Phạm vi ảnh hưởng:** Backend nơi tạo report/diagnosis; cấu hình webhook trong `deploy/`; channel Mattermost. Không đổi CV, RAG, seed data, rule hoặc workflow duyệt/phun.

## Cần thảo luận trước khi triển khai

### Confidence thấp chuyển `review_required`

AI có confidence thấp không nên được hiểu là một hướng điều trị chắc chắn. Cần thống nhất ngưỡng confidence và cách `review_required` được hiểu/hiển thị trước khi thay behavior hiện có.

**Phạm vi ảnh hưởng:** AI graph/service nơi tính confidence; Backend mapping response diagnosis; UI review; AI contract self-check. Không ảnh hưởng seed, RAG ingest, role hay áp dụng liều.

### Fail closed khi rule chưa xác định

Với seed hiện tại, Internal API chỉ trả rule active: `Tomato___Early_blight` có một rule E2E, còn bệnh không có rule active trả danh sách rỗng. Nếu sau này có nhiều rule active, thứ tự ID không chứng minh rule nào đúng; cần thống nhất khi nào diagnosis phải về review thay vì tự chọn rule.

**Phạm vi ảnh hưởng:** AI xử lý structured facts; Backend kiểm tra rule được chọn; test contract nhiều rule/không rule. Không bổ sung priority hoặc context nông học ở lượt này.

### Audit tối thiểu cho diagnosis

Audit giúp tra lại request, model/version, rule/version, nguồn RAG và thời điểm xử lý của một diagnosis. Cần chốt metadata nào được lưu, thời gian lưu và ai được quyền xem.

**Phạm vi ảnh hưởng:** model/repository/DTO/service diagnosis của Backend; mapping metadata từ AI response; test Backend–AI contract. Không bao gồm replay đầy đủ, queue hoặc object storage mới.

### Reason code cho nhánh review

Reason code là lý do có cấu trúc cho một ca `review_required`, như confidence thấp, nhiều rule hoặc không có rule. Cần thống nhất tập mã và nội dung hiển thị trước khi thêm vào API/UI.

**Phạm vi ảnh hưởng:** diagnosis DTO/model/service ở Backend; AI response mapping; `frontend/src/components/DiagnosisPanel.tsx`. Không thay đổi seed, RAG ingest hay cơ chế approve/apply.

### Production secret management

Secret gồm API key, mật khẩu database và webhook. Cần chốt môi trường triển khai, nơi quản lý secret và quyền truy cập trước khi thay cấu hình chạy hiện có.

**Phạm vi ảnh hưởng:** `deploy/.env`, Docker Compose, cấu hình Backend/AI/Mattermost và CI/CD nếu có. Không đổi chức năng diagnosis, structured facts, rule, liều hoặc PHI.

## Làm sau

### `DiseaseManagementPolicy` trong decision flow runtime

Seed `disease_management_policies` đã tồn tại: database sạch có 13 policy cho 13 disease khác nhau. Repository chưa có caller runtime, nên việc còn lại là đưa policy vào internal facts và decision flow runtime; chỉ làm khi scope contract an toàn được chốt riêng.

**Phạm vi ảnh hưởng khi mở lại:** internal Backend API, AI contract, diagnosis decision flow và regression E2E. Không thay đổi quy trình seed hiện có.

### Deterministic selector theo ngữ cảnh nông học

Đây là chọn rule theo priority hoặc context như severity, mùa vụ và thời tiết. Seed hiện tại chỉ có một active rule E2E và chưa có dữ liệu nông học được duyệt cho những tiêu chí này.

**Phạm vi ảnh hưởng khi mở lại:** master data rule, Backend selection service và structured facts trả cho AI.

### Replay diagnosis đầy đủ và CV triage đầy đủ

Replay yêu cầu versioning rõ model/RAG/LLM để kết quả còn có nghĩa. CV triage (`is_leaf`, `is_disease_relevant`) cần dataset, model và đánh giá riêng; confidence gate không thay thế hai phần này.

**Phạm vi ảnh hưởng khi mở lại:** AI model/dataset/evaluation, audit storage và quy trình vận hành AI.

### Transactional outbox và benchmark production

Outbox chỉ cần khi notification phải không mất khi Mattermost lỗi. Benchmark/sizing chỉ có nghĩa sau khi có staging/production, workload và SLO được duyệt.

**Phạm vi ảnh hưởng khi mở lại:** Backend event persistence/worker; hạ tầng triển khai, monitoring và load-test suite.

### Carbon/AWD Estimator và IoT simulator

Đây là module telemetry, dữ liệu nước và carbon mới; không phải phần của diagnosis Web.

**Phạm vi ảnh hưởng khi mở lại:** domain telemetry, ingest/storage cảm biến, công thức estimator và giao diện báo cáo.

### GraphRAG, multi-agent và IoT auto-confirm

Đây là các mở rộng AI/IoT sau Web E2E. IoT auto-confirm còn tác động trực tiếp đến bằng chứng phun, `TreatmentApplication` và khóa PHI.

**Phạm vi ảnh hưởng khi mở lại:** AI retrieval/orchestration/evaluation, thiết bị và telemetry, workflow điều trị và audit.

## Thuật ngữ chính

| Thuật ngữ | Nghĩa dễ hiểu |
| --- | --- |
| Backend | Phần máy chủ xử lý logic nghiệp vụ và dữ liệu. |
| Frontend | Giao diện người dùng trên web/app. |
| Async job | Công việc chạy nền; người dùng không phải giữ trang chờ. |
| `review_required` | Ca bệnh cần kỹ sư xem xét; hệ thống không tự đưa điều trị. |
| Confidence | Mức AI tự tin về kết quả nhận diện, từ 0 đến 1. |
| Fail closed | Không tự quyết khi thiếu điều kiện an toàn; chuyển sang review. |
| Audit | Metadata giúp tra được input, model, rule và evidence của diagnosis. |
| Reason code | Mã cho biết vì sao ca bệnh được đưa sang review. |
| `TreatmentRule` | Quy tắc điều trị có cấu trúc: thuốc/biện pháp, liều và PHI. |
| `DiseaseManagementPolicy` | Hàng rào cho phép/chặn điều trị trực tiếp; không tự kê thuốc. |
| Outbox | Hàng đợi lưu notification để có thể gửi lại khi hệ thống ngoài lỗi. |
| Secret | API key, password hoặc token cần giữ kín. |
| PHI | Thời gian cách ly trước khi được thu hoạch sau khi xử lý thuốc. |
