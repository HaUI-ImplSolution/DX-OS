# Checklist: Phase 6

Mã dòng: `WP6-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, AI, DATA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP6-05: Gọi AI ngoài transaction, cho phép chẩn đoán lại

Kết quả đầu ra: gọi AI không giữ transaction DB; chẩn đoán lại được trên cùng báo cáo.

**Backend**

- [x] WP6-05-BE-01 Tách `DiagnosisService.createFromAi(...)`: gọi AI không transaction, lưu kết quả ở transaction ghi riêng.
- [x] WP6-05-BE-02 `Diagnosis.diseaseReport`: đổi `@OneToOne(unique = true)` → `@ManyToOne`.
- [x] WP6-05-BE-03 Rà và sửa mọi nơi query "chẩn đoán của báo cáo" theo quan hệ 1-1 cũ, đổi sang lấy chẩn đoán mới nhất (danh sách báo cáo tải theo nhóm).
- [x] WP6-05-BE-04 Khóa dòng báo cáo khi ghi và quyết định; kết quả AI trễ trả 409; chẩn đoán mới đóng workflow chờ cũ.
- [x] WP6-05-BE-05 Endpoint `GET .../diagnosis/history` (ENGINEER/ADMIN).

**Data, seed, migration**

- [x] WP6-05-DATA-01 Migration đổi ràng buộc unique trên `diagnoses`.

**Frontend (web)**

- [x] WP6-05-FE-01 Nút chạy lại chẩn đoán AI và lịch sử chẩn đoán trên màn kỹ sư.

**Test**

- [x] WP6-05-TEST-01 Gọi `createFromAi` lần 2 trên báo cáo đã có chẩn đoán → thành công, không ném lỗi (T6-01).
- [x] WP6-05-TEST-02 Gọi AI với pool một connection → không giữ transaction hay connection trong lúc chờ (T6-02).

## WP6-01: Precheck ảnh trước khi gửi báo cáo

Kết quả đầu ra: ảnh không đạt chuẩn bị chặn trước khi tạo báo cáo chính thức; server vẫn kiểm lại khi nộp thật.

**AI**

- [x] WP6-01-AI-01 Endpoint `POST /api/v1/disease/precheck` (`authorize_backend`), dùng chung `assess_image` với `predict`.
- [x] WP6-01-AI-02 Đo độ nét ổn định với sọc màn hình, nhiễu hạt, chữ và viền sắc; thêm kiểm độ sáng.
- [x] WP6-01-AI-03 Crop classifier ONNX (`ai/app/cv/crop_classifier.py`) kiểm ảnh là lúa sau bước chất lượng; nạp một lần khi khởi động, health chỉ sẵn sàng khi cả hai model đã nạp; classifier lỗi trả 503.

**Backend**

- [x] WP6-01-BE-01 Endpoint `POST /api/v1/disease-reports/precheck` (FARMER, không lưu DB), bỏ EXIF/GPS rồi forward sang AI.
- [x] WP6-01-BE-02 `DiseaseReport`: cột lưu kết quả chất lượng ảnh lúc nộp.
- [x] WP6-01-BE-03 Nộp báo cáo kiểm lại chất lượng và nhận diện lúa ngoài transaction; không đạt trả 422, không lưu ảnh.

**Mobile**

- [x] WP6-01-MB-01 Kiểm kích thước và dung lượng trên máy trước khi gọi precheck.
- [x] WP6-01-MB-02 Gọi precheck sau khi chọn ảnh, chặn nút gửi nếu không đạt, hiện lý do chất lượng hoặc chưa nhận là lúa.
- [x] WP6-01-MB-03 Mất mạng thì ảnh vào hàng đợi, server kiểm khi gửi.

**Test**

- [x] WP6-01-TEST-01 Gửi ảnh mờ qua precheck → không đạt, không tạo báo cáo (T6-03).
- [x] WP6-01-TEST-02 Bỏ qua precheck, nộp báo cáo thật với ảnh mờ → server vẫn từ chối (T6-04).
- [x] WP6-01-TEST-03 Bỏ qua precheck, nộp báo cáo thật với ảnh không được nhận là lúa → server vẫn từ chối, không lưu (T6-11).
- [x] WP6-01-TEST-04 Loader, policy, precheck/predict/diagnose và lỗi dịch vụ của crop classifier (T6-12).

## WP6-03: Trạng thái "cần chụp lại" tách khỏi từ chối chẩn đoán

Kết quả đầu ra: yêu cầu chụp lại không lẫn với từ chối chẩn đoán; chuỗi báo cáo cũ/mới xem được liên tục.

**Backend**

- [x] WP6-03-BE-01 `DiseaseReport`: thêm `status`, `retakeReason`, `retakeOf`.
- [x] WP6-03-BE-02 Endpoint `PATCH .../disease-reports/{id}/request-retake` (ENGINEER/ADMIN, bắt buộc lý do).
- [x] WP6-03-BE-03 Farmer gửi báo cáo mới kèm `retakeOf`; báo cáo gốc phải `NEEDS_RETAKE`, cùng vụ, chưa có ảnh thay thế.
- [x] WP6-03-BE-04 Yêu cầu chụp lại gửi `RETAKE_REQUESTED` cho hộ, đóng workflow chờ của chẩn đoán hiện hành, từ chối khi chẩn đoán đã `APPROVED` hoặc đã yêu cầu.
- [x] WP6-03-BE-05 `NEEDS_RETAKE` chặn chẩn đoán, duyệt và ghi nhận phun trên ảnh cũ.

**Frontend (web)**

- [x] WP6-03-FE-01 Kỹ sư nhập lý do và yêu cầu ảnh mới; badge "Cần chụp lại".

**Mobile**

- [x] WP6-03-MB-01 Chip và thẻ "Cần chụp lại" kèm lý do; nút chụp lại gắn `retakeOf`; liên kết xem ảnh trước và ảnh chụp lại.
- [x] WP6-03-MB-02 Giữ `retakeOf` trong hàng đợi offline.

**Data, seed, migration**

- [x] WP6-03-DATA-01 Migration thêm cột trên `disease_reports`; set `status` cho dữ liệu cũ.

**Test**

- [x] WP6-03-TEST-01 Kỹ sư yêu cầu chụp lại → `NEEDS_RETAKE`, `Diagnosis.approvalStatus` không đổi (T6-05).
- [x] WP6-03-TEST-02 Farmer gửi báo cáo mới kèm `retakeOf` → xem được chuỗi liên tục, retry không tạo trùng (T6-06).
- [x] WP6-03-TEST-03 Yêu cầu chụp lại → hộ nhận `RETAKE_REQUESTED`, workflow chờ thành `CANCELLED`; chẩn đoán `APPROVED` → 409 (T6-09).

## WP6-04: Nhập triệu chứng thay nhập mã bệnh trực tiếp

Kết quả đầu ra: kỹ sư nhập triệu chứng nhận gợi ý xếp hạng; đường nhập tay không còn lỗi 404 khi thiếu treatment rule.

**AI**

- [x] WP6-04-AI-01 Endpoint `POST /api/v1/disease/symptoms/suggest` tái dùng `RAGRetriever`, chỉ trả mã trong danh mục.

**Backend**

- [x] WP6-04-BE-01 `DiagnosisCreateRequest`: thêm `symptomText` tùy chọn.
- [x] WP6-04-BE-02 Endpoint `POST .../diagnosis/suggest` (không lưu DB, chỉ mã đang active).
- [x] WP6-04-BE-03 `DiagnosisService.create(...)`: bỏ điều kiện bắt buộc treatment rule active, cho phép "chờ khuyến nghị".
- [x] WP6-04-BE-04 Lưu `symptomText` trên chẩn đoán; ghi `training_labels` khi duyệt (mã đã chốt, người duyệt, thời điểm, chẩn đoán bị thay).

**Frontend (web)**

- [x] WP6-04-FE-01 Kỹ sư nhập triệu chứng, xem danh sách gợi ý kèm evidence và nguồn, chọn một mã.

**Data, seed, migration**

- [x] WP6-04-DATA-01 Migration thêm `diagnoses.symptom_text` và bảng `training_labels`.

**Test**

- [x] WP6-04-TEST-01 Nhập triệu chứng → danh sách chỉ có mã trong danh mục, không có mã lạ (T6-07).
- [x] WP6-04-TEST-02 Nhập tay trên database sạch chưa có treatment rule → không còn 404 (T6-08).
- [x] WP6-04-TEST-03 Nhãn chỉ có sau khi duyệt chẩn đoán (T6-10).

## Đóng phase

- [x] Hợp `Khuong/main` tại `6e03bc6` (có Phase 5) và giải quyết xung đột.
- [x] Giữ lưu ảnh MinIO, `imageUrl` cho client và URL nội bộ cho AI.
- [x] Giữ workflow duyệt từng chẩn đoán; chẩn đoán lại đóng hồ sơ chờ duyệt cũ.
- [x] Dùng migration `V8` sau `V7` của Phase 5; `HouseholdMigrationTest` chạy V1 đến V8 trên PostgreSQL mới.
- [x] Ghép luồng chẩn đoán lại với luồng farmer tự khai phun; đường chỉ đọc không khóa dòng.
- [x] Giữ router Phase 4, hàng đợi thửa/vụ, hộp thông báo Phase 5.
- [x] Dựng lại toàn bộ stack từ clone sạch và xác nhận seed/demo sau khi hợp nhánh.
- [x] `./mvnw clean test` đạt (backend), `npx tsc --noEmit` đạt (mobile, web); đã đọc lại diff.
- [x] `pytest` đạt (AI) sau khi hợp nhánh.
- [x] Comment mới trong diff ngắn, bằng tiếng Anh.
- [x] Swagger khớp code.
- [x] `.env.example`, seed, migration chạy được trên clone sạch.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] CHANGELOG, `ai/README.md`, `ai/app/cv/README.md`, `mobile/README.md` cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] Demo offline, báo cáo gửi lại không trùng, ảnh mờ vào mục "Lỗi" và chuỗi chụp lại trên điện thoại thật.
- [x] Demo camera và precheck trên điện thoại thật.
- [x] Cập nhật trạng thái phase ở `improvement_docs/README.md` thành Xong.
