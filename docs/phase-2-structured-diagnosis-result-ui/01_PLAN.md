# Plan thực hiện: Phase 2

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP2-01 và WP2-02 chốt chung contract `rag_evidence`, làm song song và triển khai cùng lúc.
WP2-03 làm sau WP2-02.

## WP2-01: AI trả evidence có tên tài liệu, bỏ summary ghép chuỗi

- Mục tham chiếu: `00_OVERVIEW.md` mục 2–4, Q4, Q5.
- Mục tiêu: `rag_evidence` đủ để hiển thị, `recommendation` không còn đoạn ghép CV, rule và RAG.
- Ước lượng: S.

**Hành vi sau thay đổi**

| Tình huống | Hành vi |
|---|---|
| Retriever tìm được chunk | Mỗi phần tử `rag_evidence` có `content`, `document_title`, `source_ids`, `similarity` |
| Tạo recommendation | Chỉ có `selected_rule_id` và `requires_engineer_approval`; không gọi LLM writer |
| RAG lỗi hoặc rỗng | `rag_evidence = []`, rule path vẫn hoàn tất |
| Low confidence | Giữ luồng dừng sớm hiện có |

**Hướng làm**

- Thêm `document_title` vào `DiagnoseRagEvidence`, lấy từ field `document_title` retriever đã trả.
- Bỏ `summary` khỏi `DiagnoseRecommendation`.
- `generate_recommendation` chỉ dựng `selected_rule_id`, `actions`, `path` và cờ duyệt.
- `inference_service` không khởi tạo và không truyền `GroundedRecommendationWriter` cho diagnosis.
- Không đổi chunking, embedding, ranking, corpus.

**Phạm vi ảnh hưởng**

- Module: AI.
- File chính: `ai/app/api/schemas.py`, `ai/app/services/inference_service.py`, `ai/app/graph/nodes/generate_recommendation.py`, `ai/scripts/check_diagnose_contract.py`, `ai/tests/test_rice_workflow.py`.
- Endpoint: `POST /api/v1/disease/diagnose`.
- Tương thích ngược: Backend dùng Jackson mặc định bỏ qua field lạ, nên thiếu hoặc thừa `summary` không làm hỏng parse.

**Kết quả đầu ra**

- Response diagnosis có `document_title` ở từng evidence và không có `recommendation.summary`.

## WP2-02: Backend lưu và trả `knowledgeEvidence`

- Mục tham chiếu: WP2-01, Q1–Q3.
- Mục tiêu: evidence còn nguyên sau reload, đúng quyền, không ảnh hưởng validation rule.
- Ước lượng: M.

**Hành vi sau thay đổi**

| Tình huống | Hành vi |
|---|---|
| Lưu AI diagnosis | Lưu evidence vào cột JSONB, `recommendationText = null` |
| Resolve nguồn | `source_ids` được resolve qua `FarmKnowledgeNamespace.sources()` lúc lưu; ID không có trong catalogue giữ lại với `title = sourceId` |
| Đọc diagnosis | `DiagnosisResponse.knowledgeEvidence` là danh sách `KnowledgeAnswerResponse.Passage` |
| FARMER đọc PENDING hoặc REJECTED | `knowledgeEvidence = null`, giống các field còn lại bị che |
| Diagnosis cũ hoặc thủ công | `knowledgeEvidence = []`, `recommendationText` giữ nguyên |
| AI trả rule không hợp lệ | Backend từ chối như cũ, evidence không tham gia |

**Hướng làm**

- Migration `V12__diagnosis_knowledge_evidence.sql`:
  `ADD COLUMN knowledge_evidence jsonb NOT NULL DEFAULT '[]'` và `ALTER COLUMN recommendation_text DROP NOT NULL`.
- `Diagnosis.knowledgeEvidence`: `@JdbcTypeCode(SqlTypes.JSON)` kiểu `List<KnowledgeAnswerResponse.Passage>`, `@Builder.Default` là danh sách rỗng, bỏ `nullable = false` của `recommendationText`.
- `AiDiagnoseResponse.RagEvidence` thêm `documentTitle`; `Recommendation` bỏ `summary`.
- `DiagnosisService` tạo diagnosis AI với `recommendationText` null và evidence đã map.
  Giữ tối đa 5 phần tử và cắt `content` ở 2000 ký tự, không từ chối diagnosis vì evidence lớn.
- `DiagnosisResponse` thêm `knowledgeEvidence`; cập nhật `toResponse` và `toDiagnosisView` (mask).
- Giữ nguyên lookup disease, selected rule, rule version và `TreatmentRuleService.toResponse()`.
- Không thêm endpoint.

**Phạm vi ảnh hưởng**

- Module: `app-farm`.
- File chính: `Diagnosis`, `AiDiagnoseResponse`, `DiagnosisResponse`, `DiagnosisService`, `DiseaseReportService.toDiagnosisView`, migration V12.
- `DiagnosisCaseSummaryProvider` đọc `recommendationText`, cần chấp nhận null.
- Bảng DB: `diagnoses`.
- Role: ENGINEER, ADMIN xem được diagnosis PENDING; FARMER theo quyền hiện có.
- Tương thích ngược: field response mới là additive, dữ liệu cũ không đổi.

**Kết quả đầu ra**

- Reload diagnosis trả đúng evidence và thứ tự; masking đúng theo vai.

## WP2-03: Web hiển thị card tham khảo

- Mục tham chiếu: WP2-02, Q6.
- Mục tiêu: người dùng phân biệt được dự đoán của mô hình, phác đồ đã xác thực và tài liệu tham khảo.
- Ước lượng: S.

**Hành vi sau thay đổi**

| Tình huống | Hành vi |
|---|---|
| Có evidence | Card "Thông tin tham khảo từ tài liệu" dưới treatment card |
| Evidence rỗng hoặc null | Không dựng card |
| `recommendationText` | Không render |

**Hướng làm**

- `types.ts`: `Diagnosis.knowledgeEvidence: KnowledgePassage[] | null`, dùng type có sẵn ở `shell/types.ts`.
- `DiagnosisPanel.tsx`: card mới theo mẫu `treatment-card`.
  Mỗi evidence là một `article` gồm `documentTitle`, `content` (text thuần, `white-space: pre-line`), "Độ liên quan của tài liệu: N%" và `SourceList`.
- Dòng ghi chú trong card: "Chỉ để tham khảo, không thay thế phác đồ đã xác thực."
- Đổi nhãn "Độ tin cậy" thành "Độ tin cậy của mô hình" và "Nguồn treatment rule" thành "Nguồn phác đồ".
- Giữ confidence/progress, treatment card, responsive và điều kiện các nút.

**Phạm vi ảnh hưởng**

- Module: frontend web Farm.
- File chính: `frontend/src/apps/farm/types.ts`, `components/DiagnosisPanel.tsx`, stylesheet nếu cần class riêng.
- Không gọi API mới; mobile và Lab không đổi.

**Kết quả đầu ra**

- Ba khối riêng: độ tin cậy, phác đồ, tài liệu tham khảo; thuốc, liều, PHI chỉ ở treatment card.

## WP2-04: Kiểm chứng và chốt tài liệu

- Mục tham chiếu: `03_TEST_PLAN.md`.
- Ước lượng: S.
- `./mvnw compile`, build frontend, `git diff --check`, đọc lại diff.
- Cập nhật Swagger, `ai/README.md`, `docs/BACKLOG.md`.
- Giao user hướng dẫn kiểm thử thủ công (`03_TEST_PLAN.md`).

## Hình dạng payload `DiagnosisResponse`

Chỉ các key mới được đánh dấu; các key còn lại giữ nguyên.

```json
{
  "id": 1201,
  "confidence": 0.92,
  "recommendationText": null,
  "approvalStatus": "PENDING",
  "treatmentRule": { "...": "không đổi" },
  "knowledgeEvidence": [
    {
      "content": "Đạo ôn thường tạo vết bệnh hình thoi, tâm xám và rìa nâu trên lá.",
      "documentTitle": "Bệnh đạo ôn trên lúa",
      "diseaseCode": "Rice___Blast",
      "score": 0.86,
      "sources": [
        {
          "sourceId": "SRC_IRRI_BLAST",
          "title": "Rice Blast",
          "organization": "International Rice Research Institute",
          "url": "https://example.org/rice-blast",
          "license": null,
          "reviewStatus": "OFFICIAL"
        }
      ]
    }
  ]
}
```

Giá trị trong mẫu chỉ minh họa hình dạng payload.
`score` là độ liên quan của tài liệu, khác `confidence` của mô hình, không đổi tên và không trộn hai giá trị.

## Thay đổi hợp đồng

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| AI response | `rag_evidence[]` | Thêm `document_title` | Không |
| AI response | `recommendation.summary` | Bỏ | Không, Backend không còn đọc |
| Backend response | `DiagnosisResponse.knowledgeEvidence` | Thêm | Không |
| Backend response | `recommendationText` | Null với diagnosis AI mới | Không, client đã coi là nullable |
| Bảng DB | `diagnoses` | Thêm `knowledge_evidence jsonb`, bỏ `NOT NULL` của `recommendation_text` | Không mất dữ liệu |

## Dữ liệu, migration và rollback

- Migration: `db/migration/farm/V12__diagnosis_knowledge_evidence.sql`, chạy khi khởi động Backend.
- Dữ liệu cũ: giữ nguyên text, evidence mặc định `[]`, không backfill.
- Rollback ứng dụng: web ẩn card, Backend ngừng ghi cột mới.
- Không drop cột khi rollback để giữ evidence đã lưu.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Tái dùng `Passage`, `KnowledgePassage`, `SourceList` | Không thêm DTO, type, component |
| D2 | Resolve nguồn lúc lưu | `toResponse` giữ static, evidence là snapshot tại thời điểm chẩn đoán |
| D3 | Treatment rule là nguồn duy nhất cho thuốc, liều, PHI | Backend xác thực, kỹ sư duyệt |
| D4 | Web không hiển thị `recommendationText` | Không đưa đoạn ghép chuỗi trở lại giao diện |
