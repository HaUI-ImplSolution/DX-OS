# Phase 2: Hiển thị bằng chứng RAG trong kết quả chẩn đoán

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft, chờ đánh giá |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | AI, `app-farm`, frontend web |
| Ước lượng | 2–3 ngày làm việc |
| Người phụ trách | Nhóm AI, Backend và Frontend |
| Bắt đầu, dự kiến xong | Sau khi kế hoạch được duyệt; trong 2–3 ngày làm việc |
| Phụ thuộc | Luồng AI diagnosis, treatment rule và `rag_evidence` hiện có |

Kế hoạch được xây dựng trên code hiện tại của nhánh `feat/structured-siagnosis-result-ui`.
Khối confidence, treatment rule và responsive layout đã có trên web là baseline và không được triển khai lại.

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Yêu cầu ngày 19/10/2026 | Không ghép CV, treatment rule và RAG thành một đoạn `recommendationText` | Quan trọng | Toàn bộ |
| AI diagnosis contract hiện tại | AI đã trả `rag_evidence` nhưng Backend chưa lưu | Quan trọng | Bổ sung metadata, lưu và trả evidence |
| UI kết quả trên nhánh hiện tại | Web đã ưu tiên treatment rule và không còn render text tổng hợp | Quan trọng | Giữ UI hiện có, thêm khối evidence riêng |

## 2. Lý do

- AI hiện ghép CV, treatment rule và nội dung RAG thành `recommendation.summary`; Backend lưu chuỗi này vào `recommendationText`.
- AI đồng thời đã trả `rag_evidence` có cấu trúc, nhưng Backend bỏ qua trường này nên evidence và nguồn bị mất sau request.
- Web hiện không render `recommendationText`; vì vậy RAG vẫn chạy nhưng chưa tạo giá trị trên màn hình kết quả chẩn đoán.
- Treatment rule phải tiếp tục là nguồn duy nhất cho thuốc, liều và PHI; evidence RAG chỉ dùng làm thông tin tham khảo có nguồn.

## 3. Tổng quan sửa đổi

AI sẽ ngừng tạo đoạn summary ghép CV, rule và RAG cho diagnosis, đồng thời chuyển metadata tài liệu/chunk đã có trong retriever vào `rag_evidence`.
Backend sẽ tiếp tục xác thực disease và treatment rule như hiện tại, lưu snapshot evidence cùng diagnosis và trả evidence qua DTO riêng.
Web sẽ giữ nguyên confidence/treatment card hiện có và bổ sung khối “Thông tin tham khảo từ tài liệu”.
`recommendationText` được giữ trong database/API để đọc dữ liệu cũ và phục vụ chẩn đoán thủ công, nhưng web không hiển thị lại trường này.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` | Không | Không đổi interface dùng chung |
| `dx-core` | Không | Không đổi cài đặt dùng chung |
| `app-farm` | Có | Lưu và trả evidence, giữ rule validation và masking |
| `app-lab` | Không | Không đổi dữ liệu hoặc chức năng Lab |
| `dx-host` | Có một phần | Bổ sung integration test Farm nếu cần |
| Frontend web | Có | Bổ sung knowledge evidence card vào UI hiện có |
| Mobile | Không | Để follow-up riêng |
| AI | Có | Mở rộng evidence và ngừng tạo summary ghép chuỗi |
| Data / Migration | Có | Thêm snapshot evidence và cho phép text nullable |
| Infra / Deploy | Không | Không đổi cấu hình hoặc job ingest |
| Docs | Có | Cập nhật contract và tài liệu phase |

## 4. Phạm vi

- Trong phạm vi: bổ sung `chunkId`, `documentId`, `documentTitle` và `documentVersion` vào `rag_evidence`; giữ `content`, `sourceIds` và `similarity` hiện có.
- Trong phạm vi: Backend lưu evidence theo đúng thứ tự AI trả và resolve source ID bằng catalogue Farm hiện có.
- Trong phạm vi: `treatmentRule` tiếp tục là nguồn duy nhất cho thuốc, liều, đơn vị, PHI, source và version của phác đồ.
- Trong phạm vi: web hiển thị evidence thành các đoạn ngắn riêng, kèm tên tài liệu, độ liên quan và nguồn.
- Trong phạm vi: evidence rỗng hoặc RAG lỗi không làm hỏng diagnosis có treatment rule.
- Trong phạm vi: giữ masking PENDING/REJECTED đối với FARMER và giữ nguyên dữ liệu diagnosis cũ.
- Ngoài phạm vi: đổi cách chunk, thêm section enum, re-ingest corpus hoặc thay ranking/embedding.
- Ngoài phạm vi: sửa Knowledge page, symptom suggestion, Lab hoặc mobile.
- Ngoài phạm vi: hiển thị lại `recommendationText` cũ trên web, hiển thị `TreatmentRule.notes` hoặc chuẩn hóa water volume/application method.
- Ngoài phạm vi: xóa toàn bộ LLM writer/dependency; phase chỉ ngừng dùng writer để tạo summary cho diagnosis.
- Ngoài phạm vi: mở rộng corpus hoặc đánh giá RAG production-grade.
- Giả định: retriever tiếp tục trả tối đa ba chunk và source ID có thể resolve qua `FarmKnowledgeNamespace`.

## 5. Ranh giới dữ liệu

| Nhóm | Nguồn đúng | Cách dùng |
|---|---|---|
| Nhận diện | `cvResult` | Tên bệnh, confidence và metadata model |
| Điều trị | Backend `treatmentRule` | Thuốc, liều, PHI, nguồn và phiên bản rule |
| Tham khảo | `knowledgeEvidence` từ RAG | Đoạn tài liệu, độ liên quan và nguồn; không tham gia thực thi nghiệp vụ |
| Quyết định | Backend workflow và kỹ sư | Duyệt, từ chối và cho phép ghi nhận phun |

Nếu evidence nhắc tới hóa chất, nội dung đó vẫn chỉ là trích đoạn tham khảo và không được ghi đè treatment rule.

## 6. Ưu điểm và hạn chế

- Ưu điểm: tận dụng contract/retriever hiện có, không tái thiết kế pipeline RAG.
- Ưu điểm: giữ được giá trị RAG và provenance mà không lặp CV, thuốc, liều và PHI trong một đoạn văn.
- Ưu điểm: không ảnh hưởng Knowledge, symptom suggestion, Lab hoặc mobile.
- Hạn chế: evidence vẫn là chunk văn bản ngắn, chưa được phân loại thành triệu chứng/phòng ngừa/lây lan.
- Hạn chế: bản ghi cũ không có evidence để khôi phục và web tiếp tục không hiển thị `recommendationText`.
- Trade-off và rủi ro chính được ghi tại `04_TRADEOFFS_RISKS.md`.

## 7. Kết quả đầu ra mong đợi (Definition of Done)

- AI diagnosis trả evidence có đủ metadata tài liệu/chunk mà không cần một summary ghép CV, rule và RAG.
- Backend lưu và trả lại evidence không đổi sau khi reload diagnosis.
- Backend vẫn reload treatment rule từ database và từ chối rule ID/version không hợp lệ như hiện tại.
- Web hiển thị confidence, treatment card và knowledge evidence card thành ba khu vực riêng.
- Thuốc, liều và PHI không xuất hiện từ evidence hoặc `recommendationText` trên web.
- Evidence rỗng/RAG lỗi chỉ làm ẩn knowledge card; các thao tác duyệt, từ chối và ghi nhận phun vẫn hoạt động.
- Diagnosis cũ và diagnosis thủ công vẫn đọc được; không parse hoặc backfill text cũ.
- AI test, Backend integration test, frontend build và `git diff --check` đạt.
- Bằng chứng: contract test, persistence test và ảnh web cho trường hợp có/không có evidence.

## 8. Phụ thuộc và thứ tự

- WP2-01 chốt contract AI trước WP2-02.
- WP2-02 hoàn tất DTO/persistence trước khi WP2-03 nối UI.
- WP2-04 kiểm tra toàn bộ sau khi ba lớp đã khớp contract.

## 9. Ảnh hưởng đến thi

- Làm rõ RAG cung cấp evidence có nguồn, không tự kê đơn.
- Chứng minh treatment rule và human-in-the-loop vẫn kiểm soát dữ liệu an toàn.
- Cho thấy RAG tạo giá trị trực tiếp trên kết quả chẩn đoán thay vì chỉ chạy trong pipeline.

## 10. Quyết định cần chốt

| # | Quyết định | Lựa chọn | Đề xuất |
|---|---|---|---|
| Q1 | Cách lưu evidence | JSONB snapshot / bảng con | JSONB snapshot vì payload nhỏ và chỉ đọc cùng diagnosis |
| Q2 | Xử lý `recommendationText` | Xóa ngay / deprecate | Deprecate, cho phép null với AI mới và giữ dữ liệu cũ/manual |
| Q3 | Cách trình bày evidence | Danh sách chunk / phân loại section | Danh sách chunk trong phase này; section hóa để follow-up |

## 11. Tài liệu cần cập nhật khi xong

- [ ] OpenAPI/contract của AI và Backend.
- [ ] `ai/README.md` về diagnosis response.
- [ ] `CHANGELOG.md` nếu maintainer yêu cầu ghi thay đổi contract/UI.
- [ ] `docs/BACKLOG.md` nếu quyết định mở follow-up cho mobile hoặc section hóa evidence.
