# Plan thực hiện: Phase 6

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP6-05 (gọi AI ngoài transaction) làm trước vì nó chạm vào đường gọi AI dùng chung cho các WP khác.
WP6-01 (precheck), WP6-03 (trạng thái cần chụp lại) độc lập, làm song song được sau WP6-05.
WP6-04 (nhập triệu chứng qua RAG) độc lập về code.
Thứ tự: WP6-05 → (WP6-01 ∥ WP6-03 ∥ WP6-04).
Phần đánh giá AI (dataset, công cụ, báo cáo chỉ số) không thuộc phase này.

## WP6-05: Gọi AI ngoài transaction, cho phép chẩn đoán lại

- Mục tham chiếu: mục 11 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: không giữ kết nối DB trong lúc chờ AI; kỹ sư chẩn đoán lại được trên cùng báo cáo khi cần.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Gọi AI để chẩn đoán | Gọi trong `@Transactional`, giữ connection DB suốt 120s timeout | Transaction đọc ngắn → gọi AI không transaction → transaction ghi ngắn |
| Kỹ sư không đồng ý chẩn đoán cũ, muốn chạy lại | Không có đường | Tạo `Diagnosis` mới trên cùng báo cáo; bản cũ giữ lại, bản có `id` lớn nhất là hiện hành |
| Chẩn đoán mới khi bản cũ còn chờ duyệt | Không áp dụng | Workflow `PENDING` của bản cũ đóng thành `CANCELLED`, mở workflow mới |
| Kết quả AI về trễ khi đã có chẩn đoán mới hơn | Không áp dụng | 409, không lưu kết quả cũ |

**Hướng làm**

- `createFromAi` và `requestAiDiagnosis` chạy `NOT_SUPPORTED`; đọc và ghi dùng `TransactionTemplate` riêng.
- Đổi `Diagnosis.diseaseReport` sang `@ManyToOne`; lấy chẩn đoán hiện hành bằng `ORDER BY id DESC`, không thêm cột.
- Khóa dòng báo cáo (`PESSIMISTIC_WRITE`) khi ghi và khi quyết định để tuần tự hóa; đường chỉ đọc (`getApplication`, `history`, `suggest`) không khóa.
- Danh sách báo cáo của vụ tải chẩn đoán hiện hành bằng một truy vấn theo nhóm; khóa PHI vẫn tính trên mọi lần phun của vụ.
- `GET .../diagnosis/history` trả mọi chẩn đoán, mới nhất trước (ENGINEER/ADMIN).

**Phạm vi ảnh hưởng**

- Module và file chính: `service/DiagnosisService.java`, `model/Diagnosis.java`, `repository/DiagnosisRepository.java`, `service/DiseaseReportService.java`.
- Endpoint, bảng DB, biến môi trường: `POST .../diagnosis/ai` chạy lại được; `GET .../diagnosis/history` mới; bảng `diagnoses` bỏ unique theo `disease_report_id`.
- Role và client bị ảnh hưởng: engineer (chẩn đoán lại), web (lịch sử); farmer không đổi.
- Tương thích ngược và dữ liệu cũ: báo cáo cũ có đúng một chẩn đoán, không đổi.

**Kết quả đầu ra**

- Gọi AI không còn giữ transaction DB trong lúc chờ.
- Chẩn đoán lại trên cùng báo cáo thành công, giữ lịch sử; khóa PHI vẫn đúng.

**Phụ thuộc, lưu ý**: làm trước vì đường gọi AI dùng chung cho WP6-01/03/04; rủi ro R1 ở `04_TRADEOFFS_RISKS.md`.

## WP6-01: Precheck ảnh trước khi gửi báo cáo

- Mục tham chiếu: mục 5 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: farmer biết ảnh có đạt chuẩn trước khi gửi báo cáo; server vẫn kiểm lại khi nộp.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Chụp ảnh trên mobile | Gửi thẳng vào báo cáo | Kiểm kích thước và dung lượng trên máy, gọi precheck ngay sau khi chọn ảnh; không đạt thì khóa nút gửi và hiện lý do |
| Kiểm ảnh trước khi nộp | Không có | `POST /api/v1/disease-reports/precheck` (FARMER, không lưu DB hay file) |
| Nộp ảnh mờ hoặc không phải lúa, bỏ qua precheck | Nhận, kỹ sư phát hiện sau | Backend kiểm lại ngoài transaction, trả 422, không lưu ảnh |
| Báo cáo đã lưu | Không có thông tin chất lượng | Lưu `quality_*` đo lúc nộp |
| Mất mạng lúc chụp | Không áp dụng | Ảnh vào hàng đợi, server kiểm khi gửi; 422 không tự thử lại, lỗi dịch vụ thử lại |

**Hướng làm**

- AI: `POST /api/v1/disease/precheck` (`authorize_backend`) và `predict` dùng chung `assess_image` (chất lượng trước, crop classifier sau); precheck không chạy detector bệnh.
- Điều kiện: JPEG/PNG, tối đa 40 megapixel, mỗi chiều từ 160px, độ sáng 20–245, độ nét từ 20; crop classifier cho qua khi top1 là Rice, P(Rice) ≥ 0.80 và chênh với lớp khác cao nhất ≥ 0.20.
- Độ nét: ảnh xám tối đa 640px, bỏ 10% viền, làm mượt Gaussian 5×5, trung vị phương sai Laplacian của 16 vùng, trừ nhiễu ước lượng bằng MAD.
- Backend: `FileStorageService.sanitize` giải mã và mã hóa lại (bỏ EXIF/GPS) trước khi chuyển sang AI; `AiDiagnosisClient.precheck`.

**Phạm vi ảnh hưởng**

- Module và file chính: `ai/app/api/router.py`, `ai/app/services/plant_disease_service.py`, `ai/app/cv/policy.py`, `ai/app/cv/crop_classifier.py`, `controller/DiseaseReportController.java`, `service/DiseaseReportService.java`, `service/AiDiagnosisClient.java`, `mobile/app/capture.tsx`.
- Endpoint, bảng DB, biến môi trường: hai endpoint precheck mới; biến `CROP_CLASSIFIER_PATH` (Compose: `AI_CROP_CLASSIFIER_PATH`); `disease_reports` thêm `quality_passed`, `quality_sharpness`, `quality_brightness`, `quality_width`, `quality_height`.
- Role và client bị ảnh hưởng: farmer (mobile).
- Tương thích ngược và dữ liệu cũ: báo cáo cũ có quality `NULL`; điểm độ nét cũ không so sánh trực tiếp với điểm mới.

**Kết quả đầu ra**

- Ảnh mờ, quá tối, quá sáng, quá nhỏ hoặc không được nhận là lúa bị precheck từ chối trước khi tạo báo cáo.
- Server từ chối được ảnh kém hoặc không phải lúa khi nộp thật dù client bỏ qua precheck.

**Phụ thuộc, lưu ý**: cần WP6-05; ngưỡng chưa hiệu chuẩn bằng ảnh ngoài ruộng (R3); crop classifier còn nhận nhầm cây gần giống lúa (R2, TO7).

## WP6-03: Trạng thái "cần chụp lại" tách khỏi từ chối chẩn đoán

- Mục tham chiếu: mục 5 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: phân biệt "ảnh không dùng được, chụp lại" với "kỹ sư không đồng ý khuyến nghị".
- Ước lượng: S-M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Kỹ sư cần ảnh khác | Từ chối chẩn đoán, farmer hiểu lẫn | `DiseaseReport.status = NEEDS_RETAKE` kèm lý do, `Diagnosis.approvalStatus` không đổi |
| Chẩn đoán đã `APPROVED` hoặc đã yêu cầu chụp lại | Không áp dụng | 409 |
| Chẩn đoán đang chờ duyệt | Không áp dụng | Workflow đóng `CANCELLED` |
| Farmer chụp lại | Báo cáo mới không liên kết | Báo cáo mới có `retakeOf`; xem được chuỗi cũ/mới hai chiều |
| Farmer biết yêu cầu | Phải tự mở app | Nhận thông báo `RETAKE_REQUESTED` trong hộp thông báo |
| Chẩn đoán, duyệt, ghi nhận phun trên ảnh đã yêu cầu chụp lại | Không áp dụng | Bị chặn |

**Hướng làm**

- `DiseaseReport`: `status` (`PENDING`, `PROCESSED`, `NEEDS_RETAKE`), `retake_reason`, `retake_of_id`, `version`.
- `PATCH /api/v1/disease-reports/{id}/request-retake` (ENGINEER/ADMIN, lý do bắt buộc, tối đa 1000 ký tự).
- `POST .../disease-reports?retakeOf={id}`: báo cáo gốc phải `NEEDS_RETAKE`, cùng vụ, chưa có ảnh thay thế; có thể nối thành chuỗi.
- Vụ phải đã duyệt và chưa thu hoạch mới nhận báo cáo.

**Phạm vi ảnh hưởng**

- Module và file chính: `model/DiseaseReport.java`, `service/DiseaseReportService.java`, `controller/DiseaseReportController.java`, `model/NotificationType.java`, mobile `reports/[id].tsx`, web `DiagnosisPanel.tsx`.
- Endpoint, bảng DB, biến môi trường: `PATCH .../request-retake` mới; query `retakeOf`; `disease_reports` thêm 3 cột.
- Role và client bị ảnh hưởng: engineer (yêu cầu), farmer (nhận và gửi lại).
- Tương thích ngược và dữ liệu cũ: báo cáo cũ có chẩn đoán set `PROCESSED`, còn lại `PENDING`.

**Kết quả đầu ra**

- Yêu cầu chụp lại không còn hiển thị như từ chối chẩn đoán ở farmer.
- Chuỗi báo cáo cũ → chụp lại xem được liên tục.

**Phụ thuộc, lưu ý**: dùng `NotificationService` của P5.

## WP6-04: Nhập triệu chứng thay nhập mã bệnh trực tiếp

- Mục tham chiếu: mục 5, 11 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: kỹ sư nhập triệu chứng, hệ thống dùng RAG xếp hạng bệnh trong danh mục, kỹ sư chốt từ danh sách đó; LLM không tự nghĩ ra bệnh mới.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Kỹ sư nhập chẩn đoán tay | Gõ thẳng `diseaseCode` | Nhập triệu chứng, nhận bệnh xếp hạng kèm evidence và nguồn, chọn một mã; vẫn nhập thẳng mã được |
| Thiếu treatment rule | 404 | Lưu chẩn đoán "chờ khuyến nghị", không sinh thuốc, liều hay PHI, không cho ghi nhận phun |
| Dữ liệu cho huấn luyện | Không có | `symptomText` lưu trên chẩn đoán; khi kỹ sư duyệt, ghi `training_labels` |

**Hướng làm**

- AI: `POST /api/v1/disease/symptoms/suggest` tái dùng `RAGRetriever`, chỉ trả mã thuộc danh mục 6 mã, điểm là độ tương đồng tài liệu, không phải confidence.
- Backend: `POST .../diagnosis/suggest` (không lưu), chỉ trả mã đang active; `DiagnosisCreateRequest.symptomText` tùy chọn.
- Nhãn chỉ ghi khi duyệt: triệu chứng, mã đã chốt, người duyệt, thời điểm và chẩn đoán trước đó bị thay; chẩn đoán bị từ chối hoặc bị thay không có nhãn.
- Web: ô triệu chứng, nút gợi ý, danh sách kèm evidence, chọn một mã.

**Phạm vi ảnh hưởng**

- Module và file chính: `ai/app/services/symptom_service.py`, `dto/DiagnosisCreateRequest.java`, `service/DiagnosisService.java`, `model/TrainingLabel.java`, web `DiagnosisPanel.tsx`.
- Endpoint, bảng DB, biến môi trường: hai endpoint suggest mới; `diagnoses.symptom_text`; bảng `training_labels`.
- Role và client bị ảnh hưởng: engineer (web).
- Tương thích ngược và dữ liệu cũ: đường nhập tay cũ vẫn chạy; không backfill nhãn cho dữ liệu cũ.

**Kết quả đầu ra**

- Kỹ sư nhập triệu chứng nhận danh sách bệnh xếp hạng từ danh mục.
- Đường nhập tay không còn 404 trên database sạch.

**Phụ thuộc, lưu ý**: không phụ thuộc WP khác về code.

## Thay đổi hợp đồng (API, schema, cấu hình)

Đường dẫn có prefix `/api/v1`.

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST /disease-reports/precheck` | Mới (FARMER, multipart `image`): `passed` (chất lượng và nhận diện lúa), `reasons`, kích thước, độ nét, độ sáng | Không |
| Endpoint | `POST /crop-seasons/{id}/disease-reports` | Thêm query `retakeOf`; ảnh không đạt chất lượng trả 422 | Không |
| Endpoint | `PATCH /disease-reports/{id}/request-retake` | Mới (ENGINEER/ADMIN): `reason` | Không |
| Endpoint | `POST /disease-reports/{id}/diagnosis/suggest` | Mới (ENGINEER/ADMIN): `symptomText` | Không |
| Endpoint | `POST /disease-reports/{id}/diagnosis` | Thêm `symptomText` tùy chọn; thiếu treatment rule vẫn lưu | Không |
| Endpoint | `POST /disease-reports/{id}/diagnosis/ai` | Gọi lại được trên báo cáo đã có chẩn đoán; 409 nếu kết quả trễ | Có (đổi ràng buộc) |
| Endpoint | `GET /disease-reports/{id}/diagnosis/history` | Mới (ENGINEER/ADMIN) | Không |
| Endpoint (AI) | `POST /api/v1/disease/precheck`, `POST /api/v1/disease/symptoms/suggest` | Mới, cần `X-Service-Api-Key` | Không |
| Response | `DiseaseReportResponse` | Thêm `status`, `retakeReason`, `retakeOf`, `retakeIds`, `quality` | Không |
| Bảng DB | `diagnoses` | Bỏ unique `disease_report_id`, thêm index `(disease_report_id, id DESC)`, thêm `symptom_text` | Có |
| Bảng DB | `disease_reports` | Thêm `status`, `retake_reason`, `retake_of_id`, `quality_*`, `version` | Không (thêm cột) |
| Bảng DB | `training_labels` | Bảng mới, `diagnosis_id` unique | Không |
| Thông báo | `RETAKE_REQUESTED` | Loại thông báo mới | Không |

## Dữ liệu, migration và rollback

- Migration: `V8__report_quality_and_diagnosis_history.sql`, chạy sau `V7` của Phase 5.
- Dữ liệu cũ: báo cáo cũ set `status = PROCESSED` nếu đã có chẩn đoán, còn lại `PENDING`; không backfill quality hay nhãn.
- Rollback: thêm lại unique 1-1 đòi xử lý các báo cáo có nhiều chẩn đoán trước; các cột mới đều nullable hoặc có default.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D6 (kế thừa phase 0) | Test tập trung logic và an toàn, không coverage đầy đủ | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| D7 (kế thừa phase 0) | Giữ 5 bệnh lá, thêm đường triệu chứng qua RAG | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | Giữ `0.70` | Phần đánh giá AI bỏ khỏi phase nên không có số liệu để đổi |
| P6-D0 | Bỏ báo cáo đánh giá AI khỏi phase | Quyết định phạm vi ngày 06/10/2026, xem `PHASE_PLAN.md` |
| P6-D1 | Chẩn đoán hiện hành là bản có `id` lớn nhất | Không cần cột mới |
| P6-D2 | Server kiểm lại chất lượng ảnh khi nộp, trả 503 nếu AI không chạy | Không nhận ảnh chưa kiểm; hàng đợi offline thử lại lỗi dịch vụ |
| P6-D3 | Nhãn kỹ sư chỉ ghi khi duyệt | Chẩn đoán bị từ chối hoặc bị thay không làm nhiễu dữ liệu huấn luyện |
| P6-D4 | Không cho yêu cầu chụp lại khi chẩn đoán đã `APPROVED` | Phác đồ đã dựa trên chẩn đoán đó |
