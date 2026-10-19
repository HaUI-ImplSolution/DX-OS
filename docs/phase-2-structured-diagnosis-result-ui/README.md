# Phase 2: Hiển thị bằng chứng RAG trong kết quả chẩn đoán

Trạng thái: **Draft, chờ đánh giá**.
Nền lập kế hoạch: code hiện tại của nhánh `feat/structured-siagnosis-result-ui`.

Confidence, treatment rule và responsive layout hiện có là baseline.
Phase chỉ bổ sung đường dữ liệu `rag_evidence` từ AI qua Backend tới web, đồng thời ngừng dùng đoạn summary ghép CV, rule và RAG.

| File | Nội dung |
|---|---|
| [00_OVERVIEW.md](./00_OVERVIEW.md) | Mục tiêu, phạm vi và Definition of Done |
| [01_PLAN.md](./01_PLAN.md) | Bốn work package AI → Backend → web → kiểm thử |
| [02_CHECKLIST.md](./02_CHECKLIST.md) | Việc chi tiết cần triển khai từ code hiện tại |
| [03_TEST_PLAN.md](./03_TEST_PLAN.md) | 13 test case và hồi quy trực tiếp |
| [04_TRADEOFFS_RISKS.md](./04_TRADEOFFS_RISKS.md) | Trade-off JSONB, web-only và tương thích dữ liệu cũ |

Ngoài phạm vi: thay ingestion/ranking, re-ingest corpus, section hóa evidence, Knowledge, symptom suggestion, Lab, mobile và chuẩn hóa treatment notes.
