# Phase 2: Hiển thị tài liệu tham khảo (RAG) trong kết quả chẩn đoán

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft, chờ duyệt |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | `app-farm`, AI, frontend web |
| Ước lượng | 1–1,5 ngày |
| Người phụ trách | Nhóm AI, Backend và Frontend |
| Bắt đầu, dự kiến xong | Sau khi duyệt plan, trong 1–1,5 ngày làm việc |
| Phụ thuộc | Không |

Confidence, treatment card và responsive layout trên nhánh `feat/structured-siagnosis-result-ui` là baseline, không làm lại.

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Yêu cầu ngày 19/10/2026 | Không ghép CV, treatment rule và RAG thành một đoạn `recommendationText` | Quan trọng | Toàn bộ |
| Luồng `POST /api/v1/disease/diagnose` | AI đã trả `rag_evidence` nhưng Backend không lưu | Quan trọng | Toàn bộ |

## 2. Lý do

- AI ghép CV, treatment rule và đoạn RAG thành `recommendation.summary`.
  Backend lưu chuỗi đó vào `recommendationText`, còn web không render nó.
- AI đã trả `rag_evidence` có cấu trúc (nội dung, nguồn, độ tương đồng).
  Backend bỏ qua trường này nên RAG chạy nhưng không hiện ra ở đâu.
- CV nằm ở `confidence`, rule nằm ở `treatmentRule`.
  Chỉ phần RAG chưa có chỗ lưu và hiển thị riêng.

## 3. Tổng quan sửa đổi

RAG retrieval vẫn chạy như cũ.
AI không tạo `summary` ghép chuỗi nữa và trả thêm tên tài liệu cho từng đoạn evidence.
Backend lưu evidence thành một cột JSONB của diagnosis và trả về qua trường `knowledgeEvidence`.
Web thêm một card "Thông tin tham khảo từ tài liệu" dưới treatment card.

Evidence tái sử dụng `KnowledgeAnswerResponse.Passage` ở Backend, `KnowledgePassage` và `SourceList` ở web, nên không thêm DTO, type hay component nguồn mới.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` | Không | |
| `dx-core` | Không | |
| `app-farm` | Có | Lưu và trả `knowledgeEvidence`, ngừng lưu `summary` của AI |
| `app-lab` | Không | |
| `dx-host` | Không | |
| Frontend (web) | Có | Một card mới và nhãn "Độ tin cậy của mô hình" |
| Mobile | Không | |
| AI | Có | Thêm `document_title`, bỏ `summary` và lời gọi LLM writer |
| Data / Migration | Có | Một migration Farm |
| Infra / Deploy / CI | Không | |
| Docs | Có | Swagger, `ai/README.md` |

## 4. Phạm vi

- Trong phạm vi: thêm `document_title` vào `rag_evidence` của AI.
- Trong phạm vi: bỏ `summary` ghép chuỗi và lời gọi `GroundedRecommendationWriter` trong luồng diagnosis.
- Trong phạm vi: lưu evidence theo thứ tự AI trả, nguồn đã resolve qua `FarmKnowledgeNamespace`.
- Trong phạm vi: card tham khảo trên web, đổi nhãn "Độ tin cậy" thành "Độ tin cậy của mô hình".
- Ngoài phạm vi: ingestion, chunking, ranking, corpus, Knowledge page, symptom suggestion, Lab, mobile.
- Ngoài phạm vi: chia evidence theo mục (triệu chứng, phòng ngừa, lây lan), cần phân loại corpus lúc ingest, ghi vào `docs/BACKLOG.md`.
- Ngoài phạm vi: hiển thị `TreatmentRule.notes` và "Phương pháp áp dụng", cần field cấu trúc riêng.
- Ngoài phạm vi: xóa class `GroundedRecommendationWriter`.
- Giả định: retriever trả tối đa ba chunk, mỗi chunk có `document_title` và `source_ids`.
- Ranh giới Core/App: toàn bộ thay đổi nằm trong `app-farm`, AI và frontend Farm.

## 5. Ranh giới dữ liệu

| Nhóm | Nguồn đúng | Cách dùng |
|---|---|---|
| Nhận diện | `confidence` của diagnosis | Tên bệnh, độ tin cậy của mô hình |
| Điều trị | `treatmentRule` do Backend xác thực | Thuốc, liều, PHI, nguồn và phiên bản phác đồ |
| Tham khảo | `knowledgeEvidence` từ RAG | Đoạn tài liệu và nguồn, không tham gia nghiệp vụ |
| Quyết định | Workflow và kỹ sư | Duyệt, từ chối, ghi nhận phun |

Nếu evidence nhắc tới hóa chất, đó vẫn chỉ là trích đoạn tham khảo và không ghi đè treatment rule.

## 6. Ưu điểm và hạn chế

- Ưu điểm: không đổi pipeline RAG, không thêm DTO, type hay component nguồn.
- Ưu điểm: bỏ một lời gọi LLM mỗi lần chẩn đoán, `summary` không còn lặp thuốc, liều, PHI.
- Hạn chế: evidence là đoạn văn bản thô, chưa chia theo mục.
- Hạn chế: diagnosis cũ không có evidence và không được backfill.
- Trade-off và rủi ro: xem `04_TRADEOFFS_RISKS.md`.

## 7. Kết quả đầu ra mong đợi (Definition of Done)

- Demo: ENGINEER mở một diagnosis mới thấy ba khối riêng là độ tin cậy, phác đồ và tài liệu tham khảo.
- Diagnosis AI mới có `recommendationText = null` và `knowledgeEvidence` đúng thứ tự AI trả sau khi reload.
- FARMER chỉ thấy `knowledgeEvidence` khi diagnosis APPROVED.
- Rule ID hoặc version sai vẫn bị Backend từ chối như cũ.
- Evidence rỗng hoặc RAG lỗi: card tham khảo ẩn, duyệt, từ chối và ghi nhận phun vẫn hoạt động.
- Thuốc, liều, PHI chỉ xuất hiện trong treatment card.
- `./mvnw compile` đạt, frontend build đạt, `git diff --check` sạch, Swagger khớp code.

## 8. Phụ thuộc và thứ tự

- WP2-01 (AI) và WP2-02 (Backend) cùng chốt contract `rag_evidence`, triển khai cùng lúc.
- WP2-03 (web) làm sau WP2-02.

## 9. Ảnh hưởng đến thi

- Cho thấy RAG tạo giá trị trực tiếp trên kết quả chẩn đoán, có nguồn trích dẫn.
- Cho thấy treatment rule và kỹ sư duyệt vẫn là bên quyết định thuốc, liều và PHI.

## 10. Quyết định đã chốt

| # | Quyết định | Kết luận |
|---|---|---|
| Q1 | Cách lưu evidence | Một cột JSONB trong `diagnoses`, vì số chunk thay đổi (0–3) và chỉ đọc cùng diagnosis |
| Q2 | Mỗi nguồn một cột `recommendation_*` | Không: CV và rule đã có chỗ lưu riêng, chỉ RAG cần cột mới |
| Q3 | `recommendationText` | Giữ cột, cho phép null; AI mới để null, diagnosis thủ công và dữ liệu cũ giữ nguyên |
| Q4 | Writer LLM | Không gọi trong diagnosis; writer chỉ trả lại đoạn nguyên văn của evidence nên không thêm giá trị |
| Q5 | Metadata evidence | Chỉ thêm `document_title`; `chunk_id`, `document_id`, `version` chưa có chỗ dùng trên UI |
| Q6 | Chia evidence theo mục | Hoãn, cần đổi ingestion |

## 11. Tài liệu cần cập nhật khi xong

- [ ] Swagger: `DiagnosisResponse.knowledgeEvidence` và `recommendationText` nullable.
- [ ] `ai/README.md`: `rag_evidence.document_title`, bỏ `recommendation.summary`.
- [ ] `docs/BACKLOG.md`: chia evidence theo mục, field cấu trúc cho phương pháp áp dụng, mobile.
- [ ] `CHANGELOG.md` chỉ khi maintainer yêu cầu.
