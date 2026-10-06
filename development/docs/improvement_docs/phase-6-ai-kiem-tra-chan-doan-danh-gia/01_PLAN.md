# Plan Phase 6

Thứ tự: Spec → Swagger → Code. WP6-05 làm trước; WP6-01/03/04 dùng các ranh giới transaction đã tách.
Việc triển khai được theo dõi trong [checklist](./02_CHECKLIST.md), kiểm chứng trong [test plan](./03_TEST_PLAN.md).

## WP6-05: Chẩn đoán lại và transaction

- Mục tiêu: gọi AI không giữ transaction/kết nối DB; cùng báo cáo có nhiều chẩn đoán.
- Luồng: transaction đọc ngắn → HTTP AI ngoài transaction → transaction ghi ngắn.
- Chẩn đoán hiện hành là bản có `id` lớn nhất; lịch sử trả theo `id DESC`.
- Khóa dòng báo cáo tuần tự hóa ghi và quyết định; kết quả AI bị stale trả 409.
- Chẩn đoán mới đóng workflow PENDING cũ thành CANCELLED, mở workflow mới và giữ lịch sử cũ.
- Danh sách báo cáo tải chẩn đoán hiện hành theo nhóm; PHI vẫn lấy từ mọi lần phun của vụ.
- Kiểm chứng: T6-01/02, stale result, đồng thời và PHI.

## WP6-01: Precheck ảnh

- Mục tiêu: chặn ảnh kém trước khi tạo báo cáo, kể cả client bỏ qua precheck.
- Mobile kiểm kích thước, dung lượng và độ sáng; Backend bỏ EXIF/GPS rồi chuyển ảnh sang AI kiểm độ nét.
- Precheck không lưu DB/file và không chạy YOLO. Khi nộp thật, Backend kiểm lại ngoài transaction; ảnh không đạt trả 422.
- Ảnh JPEG/PNG: mỗi chiều từ 160px, dung lượng tối đa 10 MiB, tối đa 40 megapixel, độ sáng 20–245.
- Độ nét: ảnh xám tối đa 640px, bỏ 10% viền, làm mượt Gaussian nhị thức 5×5; trung vị phương sai Laplacian của 16 vùng trừ nhiễu MAD. `sharpness < 20` bị từ chối.
- Precheck và predict dùng chung `inspect_image`; chi tiết ở [CV README](../../../../ai/app/cv/README.md).
- Offline: ảnh chờ trong máy, server kiểm khi hàng đợi gửi. Lỗi chất lượng 422 không tự retry; lỗi dịch vụ có thể retry.
- Kiểm chứng: T6-03/04, ảnh mờ có sọc màn hình/nhiễu/chữ sắc và ảnh rõ sau nén JPEG.

## WP6-03: Yêu cầu chụp lại

- `DiseaseReport.status`: `PENDING`, `PROCESSED`, `NEEDS_RETAKE`; tách khỏi `Diagnosis.approvalStatus`.
- ENGINEER/ADMIN yêu cầu ảnh mới với lý do bắt buộc. Quyết định chẩn đoán cũ không đổi.
- FARMER gửi ảnh thay thế với query `retakeOf`; cùng hộ/cùng vụ, báo cáo gốc phải NEEDS_RETAKE.
- Mỗi báo cáo gốc có tối đa một ảnh thay thế chưa xóa; có thể nối tiếp thành chuỗi.
- `retakeOf` và Idempotency-Key được giữ trong hàng đợi offline. Response có liên kết hai chiều để xem ảnh cũ/mới.
- Chỉ nhận báo cáo trong vụ đã duyệt và chưa thu hoạch; kiểm trước precheck và kiểm lại khi lưu.
- NEEDS_RETAKE chặn chẩn đoán/duyệt/apply trên ảnh cũ. Farmer xem yêu cầu trong báo cáo; thông báo riêng thuộc P5.
- Kiểm chứng: T6-05/06, phân quyền, idempotency và retake đồng thời.

## WP6-04: Triệu chứng và nhãn kỹ sư

- Tái dùng `RAGRetriever`: nhập triệu chứng → ứng viên có evidence/nguồn → kỹ sư chọn và chốt mã bệnh.
- Chỉ trả mã thuộc danh mục và đang active; điểm tương đồng tài liệu không thay confidence của chẩn đoán.
- Luồng nhập trực tiếp `diseaseCode` vẫn được hỗ trợ; `symptomText` là trường tùy chọn khi lưu.
- Thiếu treatment rule được duyệt vẫn lưu chẩn đoán chờ khuyến nghị; không tự sinh thuốc/liều/PHI và không cho apply.
- Backend lưu `training_labels`: triệu chứng, mã đã chốt, người chốt, thời điểm và chẩn đoán nguồn để đối chiếu.
- Lịch sử trên web tải lại khi quyết định duyệt thay đổi trên cùng ID.
- Kiểm chứng: T6-07/08 và cập nhật lịch sử.

## Hợp đồng API

Các đường dẫn dưới đây có prefix `/api/v1`. FARMER gọi qua client OIDC mobile; ENGINEER/ADMIN dùng web.

| API | Quyền | Request / kết quả chính |
|---|---|---|
| `POST /disease-reports/precheck` | FARMER | Multipart `image`; trả `passed`, `reasons`, kích thước, độ nét và độ sáng |
| `POST /crop-seasons/{id}/disease-reports` | FARMER | Multipart `image`; query `retakeOf` tùy chọn; header Idempotency-Key dạng UUID |
| `PATCH /disease-reports/{id}/request-retake` | ENGINEER/ADMIN | JSON `reason`, bắt buộc, tối đa 1000 ký tự |
| `POST /disease-reports/{id}/diagnosis/suggest` | ENGINEER/ADMIN | JSON `symptomText`, bắt buộc, tối đa 4000 ký tự |
| `POST /disease-reports/{id}/diagnosis` | ENGINEER/ADMIN | `diseaseCode`, `confidence`, `modelVersion`; `symptomText` tùy chọn |
| `POST /disease-reports/{id}/diagnosis/ai` | ENGINEER/ADMIN | Chạy mới/chạy lại; trả 409 nếu kết quả stale |
| `GET /disease-reports/{id}/diagnosis/history` | ENGINEER/ADMIN | Các chẩn đoán theo ID giảm dần |
| `POST /disease/precheck` (AI) | Backend service key | Multipart `image`, chỉ kiểm chất lượng |
| `POST /disease/symptoms/suggest` (AI) | Backend service key | `symptom_text`, `top_k` 1–6; trả mã bệnh, score, evidence, source IDs |

Báo cáo trả `imageUrl`, `status`, `retakeReason`, `retakeOf`, `retakeIds`, `quality` và chẩn đoán hiện hành.
Farmer chỉ xem nội dung chẩn đoán khi APPROVED; lịch sử đầy đủ chỉ dành cho ENGINEER/ADMIN.
Ảnh lưu MinIO bằng URL có thời hạn; AI dùng URL nội bộ hiệu lực 10 phút.

## Schema và tương thích

Flyway V7 chạy sau V5/V6 của Phase 4; không sửa migration của nhánh cha.

- `diagnoses`: bỏ unique trên `disease_report_id`, thêm index `(disease_report_id, id DESC)`.
- `disease_reports`: thêm trạng thái, retake, quality và optimistic version.
- `training_labels`: mỗi chẩn đoán có tối đa một nhãn kỹ sư; AI không ghi dữ liệu này.
- Báo cáo cũ có chẩn đoán được backfill PROCESSED, còn lại PENDING; quality cũ để NULL.
- Không backfill nhãn giả, không xóa chẩn đoán hoặc lần phun cũ. Thêm lại unique 1–1 đòi xử lý các bản ghi trùng báo cáo trước.

## Quyết định

- D6: kiểm thử tập trung vào logic/an toàn, không yêu cầu coverage đầy đủ.
- D7: giữ 5 lớp bệnh lá, bổ sung đường triệu chứng qua RAG.
- Confidence trong code: `0.70`; ngưỡng kiểm độ nét: `20`. Các giới hạn xem [rủi ro](./04_TRADEOFFS_RISKS.md).
