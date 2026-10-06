# Checklist: Phase 6

Mã dòng: `WP6-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, AI, DATA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP6-05: Gọi AI ngoài transaction, cho phép chẩn đoán lại

Kết quả đầu ra: gọi AI không giữ transaction DB; chẩn đoán lại được trên cùng báo cáo.

**Backend**

- [x] WP6-05-BE-01 Tách `DiagnosisService.createFromAi(...)`: gọi AI ở method không `@Transactional`, lưu kết quả ở method `@Transactional` riêng.
- [x] WP6-05-BE-02 `Diagnosis.diseaseReport`: đổi `@OneToOne(unique = true)` → `@ManyToOne`.
- [x] WP6-05-BE-03 Rà và sửa mọi nơi query "chẩn đoán của báo cáo" theo quan hệ 1-1 cũ (ví dụ `listByCropSeason`), đổi sang lấy chẩn đoán mới nhất.

**Data, seed, migration**

- [x] WP6-05-DATA-01 Migration đổi ràng buộc unique trên `diagnoses`.

**Test**

- [x] WP6-05-TEST-01 Gọi `createFromAi` lần 2 trên báo cáo đã có chẩn đoán → thành công, không ném lỗi (T6-01).
- [x] WP6-05-TEST-02 Đo thời gian giữ connection DB trong lúc gọi AI → không còn giữ suốt timeout (T6-02).

## WP6-01: Precheck ảnh trước khi gửi báo cáo

Kết quả đầu ra: ảnh không đạt chuẩn bị chặn trước khi tạo báo cáo chính thức; server vẫn kiểm lại khi nộp thật.

**AI**

- [x] WP6-01-AI-01 Thêm endpoint `POST /disease/precheck` (hoặc tách logic check từ `predict`), yêu cầu `authorize_backend`.

**Backend**

- [x] WP6-01-BE-01 Thêm endpoint `POST /api/v1/disease-reports/precheck` (không lưu DB), forward sang AI.
- [x] WP6-01-BE-02 `DiseaseReport`: thêm cột lưu kết quả đánh giá chất lượng ảnh lúc nộp thật.

**Mobile**

- [x] WP6-01-MB-01 Check nhẹ cục bộ (kích thước file, độ sáng) trước khi gọi precheck.
- [x] WP6-01-MB-02 Gọi precheck, chặn nút gửi nếu "không đạt", hiện lý do.

**Test**

- [x] WP6-01-TEST-01 Gửi ảnh mờ qua precheck → "không đạt", không tạo báo cáo (T6-03).
- [x] WP6-01-TEST-02 Bỏ qua precheck (giả lập client cố tình), nộp báo cáo thật với ảnh mờ → server vẫn từ chối (T6-04).
- [x] Hồi quy ảnh mờ có sọc màn hình, nhiễu hạt, chữ/viền sắc; kiểm ảnh rõ sau nén JPEG và ở hai độ phân giải; HTTP thật xác nhận ảnh mờ trả 422, không tạo báo cáo.

## WP6-03: Trạng thái "cần chụp lại" tách khỏi từ chối chẩn đoán

Kết quả đầu ra: yêu cầu chụp lại không lẫn với từ chối chẩn đoán; chuỗi báo cáo cũ/mới xem được liên tục.

**Backend**

- [x] WP6-03-BE-01 `DiseaseReport`: thêm `status`, `retakeReason`, `retakeOf`.
- [x] WP6-03-BE-02 Endpoint `PATCH .../disease-reports/{id}/request-retake` (role ENGINEER/ADMIN, bắt buộc lý do).
- [x] WP6-03-BE-03 Farmer gửi báo cáo mới kèm `retakeOf` khi chụp lại theo yêu cầu.

**Data, seed, migration**

- [x] WP6-03-DATA-01 Migration thêm 3 cột trên `disease_reports`; set `status` cho dữ liệu cũ.

**Test**

- [x] WP6-03-TEST-01 Kỹ sư yêu cầu chụp lại → `DiseaseReport.status = NEEDS_RETAKE`, `Diagnosis.approvalStatus` không đổi (T6-05).
- [x] WP6-03-TEST-02 Farmer gửi báo cáo mới kèm `retakeOf` → xem được chuỗi liên tục (T6-06).

## WP6-04: Nhập triệu chứng thay nhập mã bệnh trực tiếp

Kết quả đầu ra: kỹ sư nhập triệu chứng nhận gợi ý xếp hạng; đường nhập tay không còn lỗi 404 khi thiếu treatment rule.

**Backend**

- [x] WP6-04-BE-01 `DiagnosisCreateRequest`: thêm `symptomText` tuỳ chọn.
- [x] WP6-04-BE-02 Endpoint `POST .../diagnosis/suggest` (gọi RAG, không lưu DB) hoặc tích hợp vào luồng hiện có.
- [x] WP6-04-BE-03 `DiagnosisService.create(...)`: bỏ điều kiện bắt buộc treatment rule active, cho phép trạng thái "chờ khuyến nghị".
- [x] WP6-04-BE-04 Lưu `symptomText` có cấu trúc kèm `diseaseCode` đã chốt, `engineerUserId`, thời điểm.

**Frontend (web)**

- [x] WP6-04-FE-01 Màn kỹ sư nhập triệu chứng, xem danh sách gợi ý xếp hạng, chọn 1 mã.

**Test**

- [x] WP6-04-TEST-01 Nhập triệu chứng → nhận danh sách bệnh trong danh mục 6 mã, không có mã lạ (T6-07).
- [x] WP6-04-TEST-02 Nhập tay trên database sạch chưa có treatment rule → không còn 404 (T6-08).

## Tích hợp nhánh cha

- [x] Cập nhật nền `Khuong/main` tại `9ec9320` và giải quyết xung đột.
- [x] Giữ lưu ảnh MinIO, `imageUrl` cho client và URL nội bộ cho AI.
- [x] Tích hợp workflow duyệt từng chẩn đoán; chẩn đoán lại đóng hồ sơ chờ duyệt cũ.
- [x] Dùng migration V7 sau V5/V6 của Phase 4; nâng database V6 lên V7 giữ nguyên lịch sử duyệt.
- [x] Chạy lại kiểm thử Backend/AI, build web, TypeScript/export Android và kiểm tra cấu hình Compose.
- [x] Giữ router Phase 4, hàng đợi thửa/vụ và điều kiện vụ đã duyệt; ghép lại callback chẩn đoán/retake.
- [ ] Dựng lại toàn bộ stack sạch và xác nhận seed/demo sau merge Phase 4.

## Đóng phase

- [x] `./mvnw compile` đạt (backend), `pytest` đạt (AI, nếu chạy được theo quy ước dự án) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, bằng tiếng Anh.
- [x] Swagger khớp code.
- [x] `.env.example`, seed, migration chạy được trên clone sạch.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] CHANGELOG, tài liệu Phase 6 và README module cập nhật; không thêm nội dung vào README tổng.
- [x] Các tiêu chí chức năng WP6-01/03/04/05 đã đạt qua kiểm thử tự động.
- [ ] Demo camera/precheck/offline/chuỗi chụp lại trên điện thoại thật.
- [x] Cập nhật trạng thái phase ở `improvement_docs/README.md`.

## Bằng chứng và phần chờ

Xem [hợp đồng API](./01_PLAN.md#hợp-đồng-api) và [kết quả kiểm thử](./03_TEST_PLAN.md#6-kết-quả-kiểm-chứng-mới-nhất).
Triển khai và kiểm thử tự động đã đạt; xác nhận camera/offline/retake theo mục 7 của test plan để đóng phần điện thoại.
