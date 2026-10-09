# Kế hoạch kiểm thử: Phase 6

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống, không coverage đầy đủ happy path.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit, PostgreSQL 16 Testcontainers) cho WP6-05/03/04; pytest (AI) cho WP6-01 và gợi ý triệu chứng; thủ công trên mobile cho phần client.
- Môi trường: clone sạch + seed demo.
- Dữ liệu và tài khoản test: ảnh mờ và ảnh lá lúa rõ; `farmer1` (mobile), `engineer1` (web).

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T6-01 | WP6-05 | Happy path | Chạy AI lần hai trên báo cáo đã có chẩn đoán | Tạo chẩn đoán mới, giữ bản cũ, bản mới là hiện hành | | Đạt: `rerunPreservesHistoryAndUsesLatestDiagnosis` |
| T6-02 | WP6-05 | Hiệu năng/logic | Gọi AI với pool DB một connection | Không giữ transaction hay connection trong lúc chờ AI | | Đạt: `aiWaitHasNoTransactionOrBorrowedDatabaseConnection` |
| T6-03 | WP6-01 | Poka-yoke | Precheck ảnh mờ, ảnh có sọc màn hình, nhiễu, chữ sắc | `passed=false`, không tạo báo cáo | | Đạt: `test_image_quality_and_symptoms.py` |
| T6-04 | WP6-01 | Poka-yoke | Bỏ qua precheck, nộp ảnh mờ | 422, không lưu DB hay file | | Đạt: `serverRejectsBlurEvenWhenClientSkipsPrecheck` |
| T6-05 | WP6-03 | Poka-yoke | Kỹ sư yêu cầu ảnh mới | `NEEDS_RETAKE`, giữ quyết định chẩn đoán | | Đạt: `retakeChangesReportAndPreservesDiagnosisDecision` |
| T6-06 | WP6-03 | Đầy đủ | Farmer gửi ảnh với `retakeOf` | Chuỗi cũ/mới đúng vụ, retry không tạo trùng | | Đạt: `linkedRetakeAndIdempotentReplayStayInSameChain` |
| T6-07 | WP6-04 | Poka-yoke | Nhập triệu chứng bất kỳ | Chỉ trả mã thuộc danh mục, có evidence và nguồn | | Đạt: `test_symptom_ranking_filters_unknown_codes_and_keeps_best_evidence` |
| T6-08 | WP6-04 | Hồi quy | Nhập tay trên database sạch chưa có treatment rule | Chờ khuyến nghị, không trả 404 | | Đạt: `manualDiagnosisWithoutRuleStoresStructuredEngineerLabel` |
| T6-09 | WP6-03 | Poka-yoke | Yêu cầu chụp lại khi chẩn đoán đang chờ duyệt, rồi khi đã `APPROVED` | Hộ nhận `RETAKE_REQUESTED`, workflow chờ thành `CANCELLED`; chẩn đoán `APPROVED` trả 409 | | Đạt: thử tay trên giao diện web và điện thoại |
| T6-10 | WP6-04 | Logic | Ghi chẩn đoán kèm triệu chứng, kiểm nhãn trước và sau khi duyệt | Chưa có nhãn trước khi duyệt; sau khi duyệt có nhãn kèm triệu chứng và người duyệt | | Đạt: `manualDiagnosisWithoutRuleStoresStructuredEngineerLabel` |
| T6-11 | WP6-01 | Poka-yoke | Bỏ qua precheck, nộp ảnh không được nhận là lúa | 422, không lưu DB hay file | | Đạt: `serverRejectsNonRiceEvenWhenClientSkipsPrecheck` |
| T6-12 | WP6-01 | Hồi quy | Crop classifier: nạp model, ngưỡng Rice 0.80 và chênh 0.20, precheck/predict/diagnose, lỗi dịch vụ | Ảnh không đạt: precheck 200 `passed=false`, predict/diagnose 422; classifier lỗi 503 | | Đạt: `ai/tests/test_crop_classifier.py` |

## 3. Hồi quy (không được hỏng)

- [x] Khóa PHI (P1) vẫn chặn đúng sau khi đổi quan hệ `Diagnosis`-`DiseaseReport` thành 1-N (`olderTreatmentStillLocksHarvestAfterDiagnosisRerun`).
- [x] Danh sách báo cáo lấy chẩn đoán hiện hành bằng truy vấn theo nhóm.
- [x] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [x] Luồng farmer tự khai phun và kỹ sư xác nhận (P5) chạy trên chẩn đoán hiện hành (`DiagnosisServiceApplyTest`).
- [x] Nộp báo cáo vào vụ chưa duyệt vẫn bị từ chối trước khi gọi precheck.
- [x] Dựng từ clone sạch bằng `deploy/reset.sh` vẫn lên đủ dịch vụ healthy sau khi hợp nhánh.
- [x] Điện thoại thật, camera: ảnh mờ bị chặn trước khi gửi; ảnh lá lúa rõ được nhận và xem được trên web.
- [x] Điện thoại thật, offline: ảnh chờ được gửi lại khi có mạng, không tạo trùng; ảnh mờ bị từ chối ở mục "Lỗi".
- [x] Điện thoại thật, chụp lại: farmer nhận thông báo, gửi ảnh thay thế và xem được chuỗi báo cáo.

## 4. Tiêu chí đạt của phase (exit criteria)

- T6-01 đến T6-12 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test và kết quả thử trên điện thoại.
- Kết quả chạy: backend `./mvnw clean test` 75 test đạt sau khi hợp nhánh `Khuong/main` tại `6e03bc6`; AI `pytest tests` 48 test đạt; web và mobile `npx tsc --noEmit` đạt.
- Thử tay trên giao diện web và điện thoại thật đạt: camera, offline, gợi ý triệu chứng, chẩn đoán lại, nhãn kỹ sư, yêu cầu chụp lại, chẩn đoán bị từ chối; dựng lại stack sạch bằng `deploy/reset.sh` đạt.
- Sau khi thêm crop classifier: AI 64 test, Backend `verify` 79 test, E2E local 10/10 bước đạt; hai model nạp và suy luận được trong container không mạng. Build toàn stack từ đầu trên CI GitHub chưa kiểm chứng. Chi tiết ở `pending_docs/CV_RICE_IMAGE_VALIDATION_SUMMARY.md`.
- Unit và integration test mock ở ranh giới HTTP AI: chứng minh logic, không đo độ chính xác model.
- Ảnh mờ có chữ/viền sắc, sọc màn hình hoặc nhiễu bị chặn, ảnh lá lúa rõ được nhận (điểm nét 30.113 so với ngưỡng 20; ảnh mờ 9.613 bị từ chối); bỏ qua precheck và nộp ảnh mờ trực tiếp trả 422.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
