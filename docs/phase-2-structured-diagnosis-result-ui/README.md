# Phase 2: Hiển thị tài liệu tham khảo (RAG) trong kết quả chẩn đoán

Trạng thái: **Draft, chờ duyệt**.
Nhánh: `feat/structured-siagnosis-result-ui`.

AI không ghép CV, treatment rule và RAG thành một đoạn `summary` nữa.
Backend lưu evidence RAG vào một cột JSONB của diagnosis và trả qua `knowledgeEvidence`.
Web thêm card "Thông tin tham khảo từ tài liệu" dưới treatment card.

| File | Nội dung |
|---|---|
| [00_OVERVIEW.md](./00_OVERVIEW.md) | Lý do, phạm vi, quyết định đã chốt, Definition of Done |
| [01_PLAN.md](./01_PLAN.md) | Bốn work package AI, Backend, web, kiểm chứng; mẫu payload; migration |
| [02_CHECKLIST.md](./02_CHECKLIST.md) | Việc chi tiết để tick |
| [03_TEST_PLAN.md](./03_TEST_PLAN.md) | 8 case kiểm thử thủ công và hồi quy |
| [04_TRADEOFFS_RISKS.md](./04_TRADEOFFS_RISKS.md) | Trade-off, rủi ro, rollback |
