# Plan thực hiện: Phase 2

Việc chi tiết nằm ở [02_CHECKLIST.md](./02_CHECKLIST.md).
Thứ tự thực hiện: contract AI → persistence/API Backend → web → kiểm thử.

## Thứ tự và phụ thuộc

WP2-01 → WP2-02 → WP2-03 → WP2-04.

## WP2-01: Trả RAG evidence đầy đủ, không tạo summary ghép chuỗi

- Mục tham chiếu: `00_OVERVIEW.md`, mục 2–5.
- Mục tiêu: sử dụng trực tiếp evidence mà retriever đã tạo và ngừng biến CV, rule và RAG thành một đoạn recommendation.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Retriever tìm được chunk | Metadata đầy đủ tồn tại trong retriever nhưng diagnosis response chỉ giữ content/source/similarity | Diagnosis response giữ thêm chunk/document ID, title và version |
| Tạo recommendation | Writer chọn evidence rồi ghép với CV/rule thành `summary` | Không gọi writer cho diagnosis; recommendation chỉ giữ thông tin chọn rule và yêu cầu duyệt |
| RAG lỗi hoặc rỗng | Rule path vẫn chạy, summary không có phần evidence | Giữ nguyên rule path; `rag_evidence=[]` |

**Hướng làm**

- Mở rộng `DiagnoseRagEvidence` bằng `chunk_id`, `document_id`, `document_title` và `document_version`.
- Map trực tiếp các field đã có từ `RAGRetriever`; không đổi chunking, embedding, ranking hoặc corpus.
- Cho `DiagnoseRecommendation.summary` nullable/deprecated trong contract chuyển tiếp.
- Ngừng gọi `recommendation_writer` trong diagnosis path; không xóa class/dependency dùng chung trong phase này.
- Giữ nguyên `selected_rule_id` và `requires_engineer_approval` để Backend tiếp tục xác thực rule.

**Phạm vi ảnh hưởng**

- Module: AI.
- File chính: `ai/app/api/schemas.py`, `ai/app/services/inference_service.py`, node tạo recommendation và test diagnosis.
- Endpoint: `POST /api/v1/disease/diagnose`.
- Data/infra: không migration vector DB, không re-ingest, không đổi biến môi trường.
- Tương thích ngược: thay đổi metadata là additive; `summary` được giữ nullable trong cửa sổ chuyển tiếp.

**Kết quả đầu ra**

- AI trả tối đa các evidence hiện có với metadata đầy đủ, nội dung nguyên retriever và không tạo đoạn summary tổng hợp.

**Phụ thuộc, lưu ý**: không sửa endpoint Knowledge hoặc symptom suggestion.

## WP2-02: Lưu và trả knowledge evidence qua Backend

- Mục tham chiếu: WP2-01.
- Mục tiêu: evidence không bị mất sau request và được trả đúng quyền cùng diagnosis.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Lưu AI diagnosis | Backend chỉ lưu `recommendation.summary` | Backend lưu snapshot `rag_evidence`; `recommendationText` của AI mới có thể null |
| Đọc diagnosis | Response có treatment rule và text | Response thêm `knowledgeEvidence`; text được giữ để tương thích nhưng web không dùng |
| FARMER đọc PENDING/REJECTED | Các field diagnosis bị che theo trạng thái | Evidence bị che cùng các field hiện có |
| AI trả rule không hợp lệ | Backend từ chối rule ID/version | Giữ nguyên hành vi, evidence không ảnh hưởng kết quả xác thực |

**Hướng làm**

- Migration Farm thêm `knowledge_evidence jsonb NOT NULL DEFAULT '[]'` và cho phép `recommendation_text` null.
- Mở rộng `AiDiagnoseResponse.RagEvidence` theo contract AI.
- Lưu snapshot evidence theo thứ tự trả về; áp giới hạn hợp lý cho số phần tử, content và source ID.
- Thêm DTO `KnowledgeEvidenceResponse` vào `DiagnosisResponse`.
- Resolve source ID bằng `FarmKnowledgeNamespace.sources()`; nếu thiếu metadata vẫn giữ source ID.
- Giữ nguyên lookup disease, selected rule, rule version và `TreatmentRuleService.toResponse()`.
- Diagnosis cũ nhận `knowledgeEvidence=[]`; không parse hoặc backfill `recommendationText`.
- Diagnosis thủ công tiếp tục dùng text hiện có; không thay flow manual.
- Cập nhật mapper che dữ liệu cho FARMER khi diagnosis chưa được phép hiển thị.

**Phạm vi ảnh hưởng**

- Module: `app-farm`, migration Farm và integration test trong `dx-host`.
- File chính: `Diagnosis`, `AiDiagnoseResponse`, `DiagnosisResponse`, `DiagnosisService`, `DiseaseReportService` và migration mới.
- Endpoint: các endpoint hiện có trả `DiagnosisResponse`; không thêm endpoint.
- Role: ENGINEER/ADMIN xem diagnosis PENDING; FARMER chỉ nhận nội dung khi trạng thái hiện tại cho phép.
- Tương thích ngược: field response mới là additive; dữ liệu text cũ được giữ nguyên.

**Kết quả đầu ra**

- Evidence còn nguyên sau reload, có source metadata và không tham gia quyết định thuốc/liều/PHI.

**Phụ thuộc, lưu ý**: JSONB chỉ là snapshot của diagnosis, không dùng cho search hoặc BI.

## WP2-03: Hiển thị evidence trên web hiện tại

- Mục tham chiếu: WP2-02.
- Mục tham chiếu format: `05_EXPECTED_OUTPUT_FORMAT.md`.
- Mục tiêu: bổ sung giá trị RAG vào UI mà không thay lại dữ liệu confidence/treatment card đã có, đồng thời chuẩn hóa thứ tự và thuật ngữ để người dùng phân biệt dự đoán, phác đồ và tài liệu tham khảo.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Có treatment rule | Web hiện card thuốc, liều, PHI, source và version | Giữ nguyên card hiện có |
| Có evidence | Web không hiển thị | Hiện khối “Thông tin tham khảo từ tài liệu” dưới treatment card |
| Evidence rỗng/RAG lỗi | Không có dấu hiệu riêng | Không dựng knowledge card; thao tác nghiệp vụ giữ nguyên |
| Có `recommendationText` cũ | Web hiện không render | Tiếp tục không render; không đưa cục text trở lại UI |

**Hướng làm**

- Mở rộng type `Diagnosis` bằng `knowledgeEvidence` và type nguồn tương ứng.
- Trình bày theo thứ tự: kết quả nhận diện → phác đồ điều trị đã xác thực → thông tin tham khảo từ tài liệu.
- Dùng nhãn “Bệnh nghi ngờ” khi PENDING, “Bệnh đã xác nhận” khi APPROVED và “Độ tin cậy của mô hình” cho confidence; Việt hóa trạng thái nghiệp vụ ở nội dung chính.
- Render mỗi evidence thành một article ngắn gồm document title, content, độ liên quan và `SourceList`.
- Gọi similarity là “Độ liên quan của tài liệu”, không dùng làm confidence chẩn đoán hoặc mức nguy hiểm.
- Phân biệt “Nguồn phác đồ” với “Nguồn tài liệu”; source/version của rule ở trong treatment card, nguồn evidence ở từng article.
- Dùng content như text thuần; không render HTML và không suy ra thuốc/liều/PHI từ chuỗi.
- Chỉ hiển thị “Phương pháp áp dụng” khi contract có field cấu trúc đã xác thực; không parse `TreatmentRule.notes`, không lấy từ evidence và ẩn dòng nếu chưa có.
- Giữ responsive layout, confidence/progress, treatment card và điều kiện nút hiện có.
- Không thêm section mapping, legacy text fallback hoặc `TreatmentRule.notes`.

**Phạm vi ảnh hưởng**

- Module: frontend web Farm.
- File chính: `frontend/src/apps/farm/types.ts`, `DiagnosisPanel.tsx`, stylesheet nếu knowledge card cần class riêng.
- Endpoint/data/config: chỉ dùng response WP2-02, không gọi thêm API.
- Role: mọi role đang được phép thấy diagnosis; quyền không đổi.
- Mobile/Lab: không thay đổi.

**Kết quả đầu ra**

- Web cho người dùng thấy ngay bệnh nghi ngờ/đã xác nhận, độ tin cậy và trạng thái; rule và evidence nằm ở hai khối riêng, có nguồn riêng và không hiển thị summary/text tổng hợp.

**Phụ thuộc, lưu ý**: nội dung chunk có thể chứa nhiều dòng; `white-space: pre-line` là đủ trong phase này.

## WP2-04: Kiểm thử và chốt tài liệu

- Mục tham chiếu: `03_TEST_PLAN.md`.
- Mục tiêu: kiểm chứng contract, persistence, phân quyền và UI trong đúng phạm vi phase.
- Ước lượng: M.

**Hướng làm**

- Chạy unit/contract test AI cho evidence có/không có RAG và rule selection.
- Chạy Backend integration test cho migration, persistence, rule validation và masking.
- Build frontend; kiểm tra UI có evidence, evidence rỗng và responsive.
- Chạy hồi quy duyệt/từ chối/ghi nhận phun và PHI liên quan trực tiếp.
- Cập nhật OpenAPI và tài liệu trạng thái cuối sau khi test đạt.

**Phạm vi ảnh hưởng**

- Module: AI, Backend Farm/host test, frontend web và docs.
- Không chạy lại ingest/ranking test, Lab E2E hoặc mobile build vì các phần này không đổi.

**Kết quả đầu ra**

- Các case bắt buộc đạt, diff chỉ nằm trong phạm vi đã chốt và có bằng chứng kiểm thử.

## Thay đổi hợp đồng

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| AI response | `DiagnoseRagEvidence` | Thêm chunk/document metadata | Không, additive |
| AI response | `DiagnoseRecommendation.summary` | Nullable/deprecated | Không nếu Backend mới được triển khai cùng phase |
| Backend response | `DiagnosisResponse` | Thêm `knowledgeEvidence` | Không, additive |
| Backend response | `recommendationText` | Giữ field nhưng AI mới có thể trả null | Không; field đã nullable ở client |
| Bảng DB | `diagnoses` | Thêm JSONB evidence, bỏ `NOT NULL` của text | Không mất dữ liệu cũ |
| Vector DB/corpus | Không đổi | Không migration hoặc re-ingest | Không |

## Dữ liệu, migration và rollback

- Migration: thêm `knowledge_evidence jsonb NOT NULL DEFAULT '[]'`; bỏ ràng buộc `NOT NULL` của `recommendation_text`.
- Dữ liệu cũ: giữ nguyên text, evidence mặc định rỗng, không backfill.
- Dữ liệu mới: AI diagnosis lưu evidence và có thể để text null; manual diagnosis giữ hành vi hiện tại.
- Rollback ứng dụng: web bỏ knowledge card, Backend ngừng ghi field mới, AI có thể trả contract cũ trong cửa sổ tương thích.
- Không drop column trong rollback khẩn cấp để tránh mất evidence đã ghi.

## Quyết định thiết kế đề xuất

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Tái sử dụng `rag_evidence` và retriever hiện có | Đạt mục tiêu mà không đổi ingestion/ranking |
| D2 | Treatment rule là nguồn duy nhất cho dữ liệu điều trị | Backend xác thực và dùng trực tiếp cho nghiệp vụ |
| D3 | Lưu JSONB snapshot theo diagnosis | Payload nhỏ, cần giữ đúng evidence đã dùng tại thời điểm chẩn đoán |
| D4 | Web không hiển thị legacy text | Không đưa “cục text” trở lại giao diện |
| D5 | Mobile và section hóa evidence là follow-up | Giữ phase đúng phạm vi của nhánh hiện tại |
