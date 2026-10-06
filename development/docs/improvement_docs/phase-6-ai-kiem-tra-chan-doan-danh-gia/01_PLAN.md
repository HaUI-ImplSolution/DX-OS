# Plan thực hiện: Phase 6

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP6-05 (gọi AI ngoài transaction) nên làm trước vì nó chạm vào đường gọi AI dùng chung cho mọi WP khác.
WP6-01 (precheck), WP6-03 (trạng thái cần chụp lại) độc lập, làm song song được sau WP6-05.
WP6-04 (nhập triệu chứng qua RAG) độc lập về code, có thể làm bất kỳ lúc nào.
Thứ tự: WP6-05 → (WP6-01 ∥ WP6-03 ∥ WP6-04).

## WP6-05: Gọi AI ngoài transaction, cho phép chẩn đoán lại

- Mục tham chiếu: mục 11 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: không giữ kết nối DB trong lúc chờ AI; kỹ sư chẩn đoán lại được trên cùng báo cáo khi cần.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `DiagnosisService.createFromAi(...)` hiện gọi AI Service (timeout đọc 120 giây) bên trong phương thức `@Transactional` của class — giữ một connection pool suốt thời gian chờ AI trả lời.
- `createFromAi` ném lỗi nếu `DiseaseReport` đã có `Diagnosis` (ràng buộc 1-1 qua `@JoinColumn(unique = true)` trên `Diagnosis.diseaseReport`) — không có đường tạo chẩn đoán thứ hai trên cùng báo cáo.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Gọi AI để chẩn đoán | Gọi trong `@Transactional`, giữ connection DB suốt 120s timeout | Gọi AI trước (không mở transaction), mở transaction riêng chỉ để lưu kết quả sau khi có response |
| Nhiều kỹ sư bấm chẩn đoán cùng lúc | Dễ cạn connection pool | Pool không bị giữ bởi lời gọi HTTP, chỉ giữ trong lúc ghi DB (ngắn) |
| Kỹ sư không đồng ý chẩn đoán cũ, muốn chạy lại | Không có đường (báo cáo đã có `Diagnosis` thì `createFromAi` ném lỗi) | Cho phép tạo `Diagnosis` mới trên cùng báo cáo (bỏ ràng buộc unique 1-1, đổi quan hệ `DiseaseReport` 1-N `Diagnosis`); chẩn đoán cũ giữ lại, chẩn đoán mới nhất là "hiện hành" |

**Hướng làm**

- Tách lời gọi AI ra khỏi method `@Transactional`: gọi AI trước ở method không transaction, nhận kết quả, sau đó gọi một method `@Transactional` riêng chỉ để lưu `Diagnosis` + tạo `WorkflowCase` (P3).
- Đổi `Diagnosis.diseaseReport` từ `@OneToOne(unique = true)` sang `@ManyToOne` (bỏ `unique = true`); thêm cách xác định "chẩn đoán hiện hành" (ví dụ `isLatest` boolean, hoặc luôn lấy theo `diagnosedAt` mới nhất — ưu tiên cách không cần cột mới nếu đủ dùng).
- Mọi nơi đang query "chẩn đoán của báo cáo" (ví dụ `listByCropSeason` N+1 đã ghi trong CHANGES_OVERVIEW) đổi sang lấy chẩn đoán mới nhất, không giả định 1-1 nữa.

**Phạm vi ảnh hưởng**

- Module và file chính: `service/DiagnosisService.java`, `model/Diagnosis.java`, mọi nơi query theo quan hệ 1-1 cũ (cần rà kỹ khi code, không chỉ sửa 1 chỗ).
- Endpoint, bảng DB, biến môi trường: `POST .../disease-reports/{id}/diagnosis/ai` không còn ném lỗi khi đã có chẩn đoán cũ; bảng `diagnoses` đổi ràng buộc unique.
- Role và client bị ảnh hưởng: engineer (chẩn đoán lại được); không ảnh hưởng farmer.
- Tương thích ngược và dữ liệu cũ: dữ liệu cũ (mỗi báo cáo đúng 1 chẩn đoán) không bị ảnh hưởng, chỉ đổi ràng buộc cho phép nhiều hơn từ đây.

**Kết quả đầu ra**

- Gọi AI không còn giữ transaction DB trong lúc chờ (xác nhận bằng cách đo thời gian giữ connection trong test).
- Chẩn đoán lại trên cùng báo cáo thành công, không ném lỗi "đã có chẩn đoán".

**Phụ thuộc, lưu ý**: làm trước vì đường gọi AI này dùng chung cho WP6-01/03/04.

## WP6-01: Precheck ảnh trước khi gửi báo cáo

- Mục tham chiếu: mục 5 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: farmer biết ảnh có đạt chuẩn hay không trước khi gửi báo cáo chính thức, không phải chờ kỹ sư phát hiện sau.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `PlantDiseaseService.predict(...)` (AI service) đã có check kích thước tối thiểu (`MIN_IMAGE_DIMENSION`) và độ nét (`MIN_SHARPNESS = 10.0`), nhưng chỉ chạy khi backend gọi chẩn đoán thật sau khi báo cáo đã lưu — không có endpoint "thử trước" riêng.
- Nguyên tắc dự án: mobile không được gọi thẳng AI, phải qua backend.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Chụp ảnh trên mobile | Gửi thẳng vào báo cáo, không biết ảnh đạt hay chưa | Check nhẹ ngay trên máy (mờ, tối, quá nhỏ — ước lượng nhanh không cần model) trước khi cho bấm gửi |
| Gửi ảnh để kiểm tra trước khi nộp báo cáo thật | Không có | Backend có endpoint `POST .../disease-reports/precheck` (không lưu DB), gọi AI (tái dùng `PlantDiseaseService.predict`'s check kích thước/độ nét, không chạy nhận diện bệnh đầy đủ nếu không cần), trả "đạt"/"không đạt" + lý do |
| Nộp báo cáo thật dù ảnh không đạt chuẩn | Có thể, kỹ sư phát hiện sau | Server vẫn kiểm lại khi nộp thật (không chỉ tin client), lưu kết quả đánh giá chất lượng vào báo cáo |

**Hướng làm**

- AI service: thêm endpoint `POST /disease/precheck` (hoặc tái dùng logic trong `predict` tách riêng phần check ảnh thành function dùng chung, gọi trước khi chạy model đầy đủ để tiết kiệm tài nguyên) — yêu cầu `authorize_backend` như các endpoint khác sau P1.
- Backend: thêm endpoint `POST /api/v1/disease-reports/precheck` (không lưu DB), nhận ảnh, forward sang AI, trả kết quả cho client.
- Mobile: check nhẹ cục bộ (kích thước file, độ sáng trung bình tính nhanh) trước khi gọi precheck, giảm số request không cần thiết; nếu precheck "không đạt" thì chặn nút gửi, yêu cầu chụp lại.
- `DiseaseReport` (hoặc `Diagnosis`) thêm trường lưu kết quả đánh giá chất lượng ảnh lúc nộp thật (để biết báo cáo nào đã qua precheck, báo cáo nào bỏ qua).

**Phạm vi ảnh hưởng**

- Module và file chính: `ai/app/api/router.py` (endpoint mới hoặc tách logic check), `backend/.../controller/DiseaseReportController.java`, `service/DiseaseReportService.java`, mobile (check cục bộ + gọi precheck).
- Endpoint, bảng DB, biến môi trường: `POST /disease/precheck` (AI, mới), `POST /api/v1/disease-reports/precheck` (backend, mới); `disease_reports` thêm cột lưu kết quả đánh giá chất lượng ảnh.
- Role và client bị ảnh hưởng: farmer (mobile) — thêm bước trước khi gửi, không đổi quyền ai được gửi.
- Tương thích ngược và dữ liệu cũ: báo cáo cũ không có dữ liệu precheck — chấp nhận `NULL`.

**Kết quả đầu ra**

- Ảnh mờ/quá nhỏ bị precheck từ chối trước khi tạo báo cáo chính thức.
- Server vẫn từ chối được ảnh kém khi nộp thật dù client bỏ qua precheck.

**Phụ thuộc, lưu ý**: cần WP6-05 xong (đường gọi AI đã tách khỏi transaction) để không lặp lại vấn đề giữ connection khi thêm lời gọi precheck.

## WP6-03: Trạng thái "cần chụp lại" tách khỏi từ chối chẩn đoán

- Mục tham chiếu: mục 5 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: phân biệt rõ "ảnh không dùng được, chụp lại" với "kỹ sư không đồng ý khuyến nghị", tránh trộn 2 ý nghĩa vào cùng trạng thái `REJECTED`.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- App farmer hiện hiểu "Cần chụp lại" chính là `ApprovalStatus.REJECTED` của `Diagnosis` — không có trạng thái riêng cho "ảnh không dùng được" ở `DiseaseReport`.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Kỹ sư yêu cầu chụp lại vì ảnh không xem được chi tiết | Reject chẩn đoán (`REJECTED`), farmer hiểu lẫn với "không đồng ý khuyến nghị" | `DiseaseReport` có trạng thái riêng `NEEDS_RETAKE` kèm lý do, không đụng tới `Diagnosis.approvalStatus` |
| Farmer chụp lại | Gửi báo cáo mới, không liên kết với báo cáo cũ | Báo cáo mới có `retakeOf` trỏ về báo cáo cũ, xem được cả chuỗi |
| Farmer biết yêu cầu chụp lại | Không có kênh thông báo trước P5 | Nhận `Notification` (từ P5) khi báo cáo chuyển `NEEDS_RETAKE` |

**Hướng làm**

- `DiseaseReport` thêm `status` (enum: `PENDING`/`NEEDS_RETAKE`/`PROCESSED` — không trùng `ApprovalStatus` của `Diagnosis`), `retakeReason` (nullable), `retakeOf` (FK tới `DiseaseReport`, nullable).
- Endpoint `PATCH .../disease-reports/{id}/request-retake` (role ENGINEER/ADMIN) cho kỹ sư yêu cầu chụp lại, kèm lý do bắt buộc.
- Farmer gửi báo cáo mới với `retakeOf = {id báo cáo cũ}` khi chụp lại theo yêu cầu.

**Phạm vi ảnh hưởng**

- Module và file chính: `model/DiseaseReport.java`, `controller/DiseaseReportController.java`, `service/DiseaseReportService.java`.
- Endpoint, bảng DB, biến môi trường: `PATCH .../disease-reports/{id}/request-retake` (mới); `disease_reports` thêm `status`, `retake_reason`, `retake_of_id`.
- Role và client bị ảnh hưởng: engineer (yêu cầu chụp lại), farmer (nhận yêu cầu, gửi báo cáo liên kết).
- Tương thích ngược và dữ liệu cũ: báo cáo cũ không có `status` — set `PROCESSED` qua migration nếu đã có chẩn đoán, `PENDING` nếu chưa.

**Kết quả đầu ra**

- Yêu cầu chụp lại không còn hiển thị như "từ chối chẩn đoán" ở farmer.
- Chuỗi báo cáo cũ → báo cáo chụp lại xem được liên tục.

**Phụ thuộc, lưu ý**: nên có P5 (hộp thông báo) để farmer biết ngay, nhưng không bắt buộc — thiếu P5 vẫn hoạt động được, chỉ farmer phải tự mở app kiểm tra.

## WP6-04: Nhập triệu chứng thay nhập mã bệnh trực tiếp

- Mục tham chiếu: mục 5, 11 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: kỹ sư nhập triệu chứng bằng văn bản, hệ thống dùng RAG xếp hạng bệnh trong danh mục có sẵn, kỹ sư chốt từ danh sách đó — không để LLM tự nghĩ ra bệnh mới, không bắt kỹ sư phải tự biết mã bệnh.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `DiagnosisCreateRequest` hiện bắt kỹ sư tự biết và gõ đúng `diseaseCode`, `confidence`, `modelVersion` — không có hỗ trợ tra cứu nào trong request này.
- `DiagnosisService.create(...)` (đường nhập tay) hiện luôn đòi có treatment rule active cho bệnh đó; seed sạch chưa có rule nào được duyệt nên đường này lỗi 404 trên database mới (đã ghi nhận ở mục 5, 11, 18 `PROJECT_IMPROVEMENTS.md`).
- AI service đã có `RAGRetriever` (`ai/app/rag/retriever.py`, dùng ở endpoint `/disease/knowledge/retrieve`) — tái dùng được cho việc xếp hạng theo triệu chứng, không viết retriever mới.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Kỹ sư nhập chẩn đoán tay | Gõ thẳng `diseaseCode` (phải tự biết đúng mã) | Nhập triệu chứng bằng văn bản tự do, gọi RAG xếp hạng ứng viên trong danh mục 6 mã bệnh hiện có, kỹ sư chọn 1 mã từ danh sách xếp hạng (không tự gõ mã) |
| Đường nhập tay đòi treatment rule active | 404 nếu chưa có rule nào được duyệt cho bệnh đó | Tách rời: cho phép lưu `Diagnosis` ở trạng thái "chờ khuyến nghị" nếu chưa có rule active, không chặn hẳn việc ghi nhận bệnh (gốc rễ dữ liệu rule thiếu là việc của mục 18, không phải lỗi logic ở đây) |
| LLM tham gia | Không áp dụng (chưa có luồng này) | Chỉ dùng để diễn đạt lại phần phòng ngừa từ nguồn đã ingest, không tự sinh `disease_code` mới — kết quả luôn là 1 mã hợp lệ trong danh mục |

**Hướng làm**

- Backend: `DiagnosisCreateRequest` đổi thêm trường tuỳ chọn `symptomText` (String); nếu có `symptomText`, gọi AI `/disease/knowledge/retrieve` (đã có, chỉ thêm tham số lọc theo danh mục 6 mã bệnh) lấy danh sách ứng viên xếp hạng, trả về cho kỹ sư chọn trước khi gửi `diseaseCode` chính thức trong request thứ hai (giữ 2 bước: gợi ý → chốt, không tự động chốt).
- Lưu `symptomText` có cấu trúc (không chỉ chuỗi tự do thô) để dùng làm dữ liệu huấn luyện sau — ít nhất tách được `symptomText` + `diseaseCode` đã chốt + `engineerUserId` + thời điểm.
- `DiagnosisService.create(...)`: bỏ điều kiện bắt buộc treatment rule active, cho phép `treatmentRule = null` + trạng thái riêng báo "chờ khuyến nghị" (đã có hướng xử lý tương tự ở nhánh `review_required` theo `PROJECT_IMPROVEMENTS.md` mục 18).

**Phạm vi ảnh hưởng**

- Module và file chính: `dto/DiagnosisCreateRequest.java`, `service/DiagnosisService.java`, `controller/DiagnosisController.java`, web (màn kỹ sư nhập triệu chứng + xem gợi ý).
- Endpoint, bảng DB, biến môi trường: `POST .../disease-reports/{id}/diagnosis` đổi request (thêm `symptomText` tuỳ chọn); có thể thêm endpoint `POST .../diagnosis/suggest` (gọi RAG, không lưu DB) để tách bước gợi ý khỏi bước chốt.
- Role và client bị ảnh hưởng: engineer (web) — thêm bước nhập triệu chứng, không đổi quyền farmer/admin.
- Tương thích ngược và dữ liệu cũ: đường nhập tay cũ (gõ thẳng `diseaseCode`) vẫn hoạt động được (không bắt buộc `symptomText`), chỉ thêm lựa chọn mới.

**Kết quả đầu ra**

- Kỹ sư nhập triệu chứng → nhận danh sách bệnh xếp hạng từ danh mục, không phải tự nhớ mã.
- Đường nhập tay không còn lỗi 404 trên database sạch chưa có treatment rule.

**Phụ thuộc, lưu ý**: tái dùng `RAGRetriever` đã có ở AI service, không viết retriever mới; không phụ thuộc WP khác về code.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST /disease/precheck` (AI) | Mới | Không |
| Endpoint | `POST /api/v1/disease-reports/precheck` | Mới | Không |
| Endpoint | `PATCH .../disease-reports/{id}/request-retake` | Mới | Không |
| Endpoint | `POST .../disease-reports/{id}/diagnosis` | Thêm `symptomText` tuỳ chọn trong request | Không (tuỳ chọn) |
| Endpoint | `POST .../disease-reports/{id}/diagnosis/ai` | Cho phép gọi lại trên báo cáo đã có chẩn đoán | Có (thay đổi ràng buộc) |
| Bảng DB | `diagnoses` | Bỏ unique 1-1 với `disease_report_id`, đổi sang `@ManyToOne` | Có |
| Bảng DB | `disease_reports` | Thêm `status`, `retake_reason`, `retake_of_id`, cột kết quả precheck | Không (thêm cột) |

## Dữ liệu, migration và rollback

- Migration: đổi ràng buộc unique trên `diagnoses`, thêm cột trên `disease_reports`, qua Flyway (đã có từ P2).
- Dữ liệu cũ: báo cáo cũ set `status = PROCESSED`/`PENDING` theo đã có chẩn đoán hay chưa; không backfill `symptomText`/kết quả precheck cho dữ liệu cũ.
- Rollback: bỏ unique constraint là thay đổi một chiều dễ (không mất dữ liệu); thêm lại unique constraint sau này cần đảm bảo không còn báo cáo nào có nhiều hơn 1 chẩn đoán.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D7 (kế thừa phase 0) | Giữ 5 bệnh lá, thêm đường nhập triệu chứng | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | Giữ ngưỡng confidence `0.70` | Người dùng xác nhận ngày 06/10/2026 |

## Hợp đồng đã triển khai

[05_IMPLEMENTATION.md](./05_IMPLEMENTATION.md) ghi endpoint/schema chính xác.
Chẩn đoán hiện hành chọn theo `id DESC`, không thêm cột `isLatest` và không phụ thuộc timestamp khác nhau.
`training_labels` thuộc Backend; `sourceDiagnosis` tham chiếu kết quả trước để đối chiếu với quyết định kỹ sư.
Precheck AI dùng `/api/v1/disease/precheck`; Backend gửi ảnh đã bỏ EXIF/GPS.
Danh sách báo cáo tải chẩn đoán hiện hành theo nhóm.
Phần dataset và báo cáo chỉ số đánh giá AI được bỏ khỏi phạm vi theo yêu cầu của người dùng ngày 06/10/2026.
