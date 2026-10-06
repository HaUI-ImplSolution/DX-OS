# Bàn giao Phase 6

Nhánh: `tung/phas6-ai-kiem-tra-chan-doan-danh-gia`, đã cập nhật nền `Khuong/main` tại `f52f564`.
Trạng thái: WP6-01/03/04/05 hoàn thành triển khai và kiểm thử tự động; còn demo điện thoại thật.

## Hợp đồng triển khai

| API | Quyền | Nội dung |
|---|---|---|
| `POST /api/v1/disease-reports/precheck` | FARMER | Multipart `image`; không ghi DB/file, trả chất lượng ảnh |
| `POST /api/v1/crop-seasons/{id}/disease-reports` | FARMER | Kiểm lại chất lượng; `retakeOf` optional query parameter; giữ Idempotency-Key |
| `PATCH /api/v1/disease-reports/{id}/request-retake` | ENGINEER/ADMIN | JSON `reason`, bắt buộc, tối đa 1000 ký tự |
| `POST /api/v1/disease-reports/{id}/diagnosis/suggest` | ENGINEER/ADMIN | JSON `symptomText`, bắt buộc, tối đa 4000 ký tự; trả ứng viên có nguồn |
| `POST /api/v1/disease-reports/{id}/diagnosis` | ENGINEER/ADMIN | Chốt `diseaseCode`, `confidence`, `modelVersion`; `symptomText` optional |
| `POST /api/v1/disease-reports/{id}/diagnosis/ai` | ENGINEER/ADMIN | Chẩn đoán hoặc chạy lại; giữ lịch sử, kết quả stale trả 409 |
| `GET /api/v1/disease-reports/{id}/diagnosis/history` | ENGINEER/ADMIN | Tất cả chẩn đoán theo id giảm dần |
| `POST /api/v1/disease/precheck` (AI) | Backend API key | Multipart, chỉ kiểm chất lượng, không chạy YOLO |
| `POST /api/v1/disease/symptoms/suggest` (AI) | Backend API key | JSON `symptom_text` và `top_k` 1–6, tái dùng RAGRetriever |

Báo cáo trả URL ảnh có thời hạn qua `imageUrl`, cùng `status`, `retakeReason`, `retakeOf`, `retakeIds`, `quality`. AI dùng URL MinIO nội bộ có thời hạn 10 phút.
Farmer chỉ thấy nội dung chẩn đoán hiện hành sau khi được duyệt; lịch sử đầy đủ chỉ dành cho staff.
Thiếu treatment rule lưu chẩn đoán có `treatmentRule=null`, khuyến nghị chờ kỹ sư và không cho apply.
Nhãn kỹ sư nằm trong `training_labels` ở DB Backend, không ghi vào DB AI.
Chẩn đoán hiện hành là id lớn nhất, ổn định ngay khi nhiều bản ghi có timestamp bằng nhau.
Danh sách báo cáo tải chẩn đoán hiện hành và các retake bằng truy vấn theo nhóm, không thêm truy vấn mỗi báo cáo.

## Transaction và đồng thời

Đường AI: transaction đọc ngắn → HTTP AI ngoài transaction → transaction ghi ngắn.
Precheck lúc nộp ảnh cũng chạy ngoài transaction; giao dịch ghi kiểm lại quyền, idempotency và trạng thái vụ/retake.
Report row lock tuần tự hóa ghi chẩn đoán và quyết định; kết quả AI không ghi đè chẩn đoán mới xuất hiện trong lúc chờ.
Mỗi chẩn đoán có workflow duyệt riêng. Chẩn đoán lại chuyển workflow PENDING cũ sang CANCELLED, giữ nguyên chẩn đoán và event cũ; approve/reject cập nhật workflow của chẩn đoán hiện hành.
`NEEDS_RETAKE` chặn chẩn đoán/duyệt/apply trên ảnh cũ, không đổi quyết định chẩn đoán đã lưu.
Retake phải cùng hộ/cùng vụ, chỉ một ảnh thay thế chưa xóa cho mỗi báo cáo gốc; ảnh mới có thể được yêu cầu chụp lại để nối thành chuỗi.
Khóa PHI lấy từ tất cả lần phun của vụ, không bị bỏ qua khi có chẩn đoán mới.

## Schema và dữ liệu cũ

Migration `V5__phase6_report_quality_and_diagnosis_history.sql` chạy sau V4 workflow của Phase 3.
Bỏ unique trên `diagnoses.disease_report_id`, thêm index `(disease_report_id, id DESC)`.
Thêm status/retake/chất lượng và `version` cho báo cáo; thêm `training_labels`.
Báo cáo cũ có chẩn đoán được đặt `PROCESSED`, còn lại `PENDING`; chất lượng cũ để NULL.
Không backfill nhãn kỹ sư giả và không xóa chẩn đoán hay lần phun cũ.
V1–V4 và schema treatment rule của nhánh cha được giữ nguyên. Nhánh cha hiện không seed thuốc hoặc treatment rule được duyệt; đường chẩn đoán thiếu rule lưu khuyến nghị chờ kỹ sư và không cho áp dụng thuốc.

## Kiểm chứng

Bằng chứng tự động: JUnit + PostgreSQL 16 Testcontainers, pytest, TypeScript web/mobile và build web.
`Phase6IntegrationTest` kiểm T6-01/02/04/05/06/08 và PHI/stale result.
`ai/tests/test_phase6.py` kiểm T6-03/07, auth, kích thước và độ sáng ảnh.
`AiDiagnosisClientPrecheckTest` kiểm forwarding multipart có Content-Type ảnh thật/API key và lỗi xác thực dịch vụ không bị coi là ảnh mờ.
`PermissionMatrixTest` kiểm farmer không gọi được thao tác staff và staff không nộp/precheck ảnh thay farmer.
Các test migration chạy schema mới trên DB trống và DB baseline cũ.

Lệnh chuẩn để chạy lại trên clone sạch:

```sh
cd backend/DX-Farm
./mvnw test
```

```sh
cd ai
python -m pytest tests -q
```

```sh
cd frontend
npm ci
npm run build
```

```sh
cd mobile
npm ci
npx tsc --noEmit
npm run export -- --platform android --output-dir /tmp/phase6-mobile-export
```

Môi trường thực hiện có thư mục backend `target` do root sở hữu và chỉ có JRE 21.
Đã dùng JDK 21 đầy đủ trong `/tmp` và bản sao POM chỉ đổi đường dẫn source/resource/output để chạy Maven; không sửa POM dự án.
Log kiểm tra sau cập nhật nhánh cha: `/tmp/dxfarm-phase6-parent-refresh-backend.log`, `/tmp/dxfarm-phase6-parent-ai.log`, `/tmp/dxfarm-phase6-parent-web.log`, `/tmp/dxfarm-phase6-parent-mobile-export.log` và `/tmp/dxfarm-phase6-parent-refresh-seed.log`.
Kết quả sau cập nhật nhánh cha: Backend 74 test, AI 34 test, seed 4 test đều đạt; web build, mobile TypeScript/export Android và cấu hình Compose đều đạt. Chi tiết ở `03_TEST_PLAN.md`.
Trước khi cập nhật nhánh cha, Compose đã chạy từ snapshot trong `/tmp/dxfarm-phase6-stack`, DB trống, volume mới, secret tổng hợp và cổng riêng.
Chỉ thay URL redirect của realm thử nghiệm cho cổng riêng, giữ cấu hình OIDC/PKCE; không bật password grant.
Model/embedding/LLM cache được sao chép từ artifact có sẵn sang volume mới; nguồn cache không bị ghi.
Các dịch vụ backend/AI/PostgreSQL/Keycloak/web/portal/Ollama/Superset/Mattermost đều healthy, các job init/ingest kết thúc thành công.
RAG thật ingest 6 tài liệu/31 đoạn; HTTP thật trả 6 ứng viên hợp lệ có bằng chứng.
HTTP qua OIDC/PKCE xác nhận ảnh mờ bị chặn cả hai đường, lưu quality, nhập tay không có rule, history/phân quyền và chuỗi retake.
Swagger `/v3/api-docs` trả 200 và chứa đầy đủ endpoint P6 cùng các field đúng shape.
Log local thêm: `/tmp/dxfarm-phase6-compose.log`, `/tmp/dxfarm-phase6-http-smoke.log`.

Ruff cho phần Python thay đổi đạt; toàn bộ test cũ còn 3 lỗi sắp xếp import có sẵn ngoài diff.

## Phần còn chờ

- Demo camera/precheck/offline/chuỗi retake trên điện thoại thật và ảnh chụp minh chứng.
- P5 chưa có trên nhánh cha; yêu cầu chụp lại hiển thị trong báo cáo, chưa có thông báo riêng cho farmer. Workflow P3 đã tích hợp cho luồng duyệt chẩn đoán.

## Quyết định phạm vi 06/10/2026

- Giữ confidence 0.70 theo xác nhận của người dùng.
- Bỏ phần dataset và báo cáo chỉ số đánh giá AI khỏi Phase 6. Đã xóa công cụ đánh giá, dependency riêng, hướng dẫn dữ liệu, báo cáo và 2 test chỉ phục vụ công cụ đó.
- Giữ các chức năng precheck, RAG, retake, lịch sử chẩn đoán và nhãn kỹ sư.

Đường AI thật với ảnh synthetic trả 422 do confidence chưa đạt; không tự ghi kết quả “healthy” hoặc duyệt.
Lượt này chỉ kiểm tra runtime/triage, không phải phép đo accuracy độc lập.

Stack thử nghiệm riêng đã được dừng và gỡ volume sao chép sau kiểm tra; dịch vụ/volume có sẵn của người dùng không bị thay đổi.
