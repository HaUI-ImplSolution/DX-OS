# Checklist: Phase 2

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP2-{xx}-{lớp}-{nn}`.
Baseline cần giữ: confidence, treatment card và responsive CSS hiện có.

## WP2-01: AI trả evidence có tên tài liệu, bỏ summary

Kết quả đầu ra: `rag_evidence[].document_title` có mặt, `recommendation.summary` không còn.

**Spec và tài liệu**

- [ ] WP2-01-DOC-01 Cập nhật `ai/README.md`: `document_title`, bỏ `summary`.

**AI**

- [ ] WP2-01-AI-01 Thêm `document_title` vào `DiagnoseRagEvidence` và map trong `inference_service`.
- [ ] WP2-01-AI-02 Bỏ `summary` khỏi `DiagnoseRecommendation` và khỏi `generate_recommendation`.
- [ ] WP2-01-AI-03 Bỏ lời gọi và khởi tạo `GroundedRecommendationWriter` trong luồng diagnosis.
- [ ] WP2-01-AI-04 Sửa `check_diagnose_contract.py` và `test_rice_workflow.py` không còn kiểm `summary`.

**Test**

- [ ] WP2-01-TEST-01 Case T2-01, T2-02.

## WP2-02: Backend lưu và trả `knowledgeEvidence`

Kết quả đầu ra: evidence còn nguyên sau reload và đúng masking.

**Spec và tài liệu**

- [ ] WP2-02-DOC-01 Swagger: mô tả `knowledgeEvidence`, `recommendationText` nullable (bằng tiếng Anh).

**Backend**

- [ ] WP2-02-BE-01 [farm] `AiDiagnoseResponse.RagEvidence` thêm `documentTitle`, `Recommendation` bỏ `summary`.
- [ ] WP2-02-BE-02 [farm] `Diagnosis.knowledgeEvidence` (JSON) và `recommendationText` nullable.
- [ ] WP2-02-BE-03 [farm] `DiagnosisService`: map evidence, resolve nguồn qua `FarmKnowledgeNamespace`, giới hạn 5 phần tử và 2000 ký tự.
- [ ] WP2-02-BE-04 [farm] Tạo diagnosis AI với `recommendationText` null; giữ nguyên rule validation.
- [ ] WP2-02-BE-05 [farm] `DiagnosisResponse.knowledgeEvidence` và cập nhật `toResponse`.
- [ ] WP2-02-BE-06 [farm] `DiseaseReportService.toDiagnosisView` mask evidence cho FARMER chưa APPROVED.
- [ ] WP2-02-BE-07 [farm] `DiagnosisCaseSummaryProvider` chấp nhận `recommendationText` null.

**Data, migration**

- [ ] WP2-02-DATA-01 [farm] `V12__diagnosis_knowledge_evidence.sql`.

**Test**

- [ ] WP2-02-TEST-01 Case T2-03 đến T2-06.

## WP2-03: Web hiển thị card tham khảo

Kết quả đầu ra: card tham khảo hiện khi có evidence, ẩn khi rỗng.

**Frontend (web)**

- [ ] WP2-03-FE-01 `types.ts`: `Diagnosis.knowledgeEvidence: KnowledgePassage[] | null`.
- [ ] WP2-03-FE-02 `DiagnosisPanel.tsx`: card "Thông tin tham khảo từ tài liệu" với `documentTitle`, `content`, "Độ liên quan của tài liệu", `SourceList`.
- [ ] WP2-03-FE-03 Ẩn card khi evidence rỗng hoặc null.
- [ ] WP2-03-FE-04 Đổi nhãn "Độ tin cậy" thành "Độ tin cậy của mô hình".
- [ ] WP2-03-FE-05 Style cho card nếu cần, kiểm tra màn hình hẹp.

**Test**

- [ ] WP2-03-TEST-01 Case T2-07, T2-08.

## WP2-04: Kiểm chứng và chốt tài liệu

- [ ] WP2-04-TEST-01 `./mvnw compile` đạt cho toàn bộ module.
- [ ] WP2-04-TEST-02 `npm run build` trong `frontend` đạt.
- [ ] WP2-04-TEST-03 `git diff --check` sạch và đã đọc lại toàn bộ diff.
- [ ] WP2-04-DOC-01 Cập nhật `docs/BACKLOG.md`: chia evidence theo mục, field cấu trúc cho phương pháp áp dụng, mobile.
- [ ] WP2-04-DOC-02 Giao user hướng dẫn kiểm thử thủ công từ `03_TEST_PLAN.md`.

## Đóng phase

- [ ] `./mvnw compile` đạt cho toàn bộ module và đã đọc lại diff.
- [ ] Comment mới ngắn, bằng tiếng Anh, chỉ nêu hành vi hiện tại, không nhắc nhãn phase.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh.
- [ ] `dx-core-api` và `dx-core` không đổi; không có secret trong diff.
- [ ] Các tiêu chí ở `00_OVERVIEW.md` mục 7 có bằng chứng.
- [ ] Cập nhật trạng thái ở `README.md` của thư mục phase.
