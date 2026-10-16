# Phase 1: Nâng cấp model nhận diện bệnh lúa

Trạng thái: **Đã duyệt kế hoạch, chưa triển khai**.
Nền: `origin/Khuong/main`, đã đồng bộ ngày 16/10/2026.
Nhánh: `feature/dx-farm-efficientnetv2m`.
Phạm vi duy nhất: thay model bệnh YOLOv8n bằng checkpoint DX-Farm EfficientNetV2-M do người huấn luyện cung cấp.
Giữ API, ngưỡng 0,70, quality/crop gate, nghiệp vụ và workflow CI hiện có.

| File | Nội dung |
|---|---|
| [00_OVERVIEW.md](./00_OVERVIEW.md) | Mục tiêu, ranh giới và tiêu chí bàn giao |
| [01_PLAN.md](./01_PLAN.md) | Work package và danh sách file được sửa |
| [02_CHECKLIST.md](./02_CHECKLIST.md) | Checklist thực hiện và dọn trước release |
| [03_TEST_PLAN.md](./03_TEST_PLAN.md) | Kiểm tra cần thực hiện và kết quả để trống |
| [04_TRADEOFFS_RISKS.md](./04_TRADEOFFS_RISKS.md) | Trade-off, đóng gói và rollback |

Theo [template](../_template/README.md), chỉ đóng phase khi có bằng chứng; đưa việc còn lại vào BACKLOG và dọn thư mục phase trước release.
