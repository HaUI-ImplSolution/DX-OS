# Mở rộng hỏi đáp Farm/Lab và tìm kiếm hybrid

Trạng thái: đã đóng phạm vi triển khai kỹ thuật ngày 28/10/2026 theo yêu cầu chủ dự án.
Các kiểm chứng mở rộng và review UI cuối được theo dõi trong BACKLOG; không coi là đã chạy.
Giữ nguyên thư mục kế hoạch theo yêu cầu chủ dự án.
Mục tiêu là trả lời được câu hỏi tổng quan lẫn câu hỏi cụ thể trong lĩnh vực của từng chatbot.
BM25, cosine và rerank chỉ áp dụng cho chatbot web; chẩn đoán Farm giữ luồng hiện tại.
Giữ đường dẫn thư mục hiện có để tránh đổi các liên kết của nhánh.

| File | Vai trò |
|---|---|
| [00_OVERVIEW.md](00_OVERVIEW.md) | Hiện trạng, domain, ranh giới và điều kiện hoàn thành |
| [01_PLAN.md](01_PLAN.md) | Work package, luồng, hợp đồng và thay đổi dự kiến |
| [02_CHECKLIST.md](02_CHECKLIST.md) | Việc cần làm và bằng chứng để đánh dấu hoàn thành |
| [03_TEST_PLAN.md](03_TEST_PLAN.md) | Hồi quy, đánh giá chất lượng và hiệu năng |
| [04_TRADEOFFS_RISKS.md](04_TRADEOFFS_RISKS.md) | Lựa chọn kỹ thuật, rủi ro và rollback |
| [05_RUNBOOK.md](05_RUNBOOK.md) | Cách vận hành qua một lệnh shell |
| [06_HANDOFF.md](06_HANDOFF.md) | Tiến độ có bằng chứng, lỗi cần kiểm lại và thứ tự tiếp tục |

Các mục chưa có bằng chứng chạy trên luồng mới vẫn để chưa hoàn thành.
Báo cáo lexical proxy và kết quả pilot cũ không thay cho phép đo hybrid thực tế.
