# Phase 1: Nâng cấp model nhận diện bệnh lúa

Trạng thái: **Đã triển khai model; chưa đóng phase**.
Sau đồng bộ Khuong/main: AI 100/100, dữ liệu 5/5, Farm 10/10 và luồng model thật đạt.
Lab 16/16 và restart/predict offline thuộc lượt trước; không dựng thêm Docker theo yêu cầu mới.
Mã Lab/core/CI đã được đối chiếu không đổi.
Cold start bằng lệnh gốc thất bại ở seed vì AI chưa sẵn sàng.
Lỗi script khởi động và seed được ghi nhận, không sửa ngoài phạm vi.
Bộ trọng số được đóng gói cùng các commit nâng cấp; đã kiểm tra checkout nguyên byte.
Giữ checklist đến khi xử lý tiêu chí khởi động và chốt phát hành.
Điều kiện môi trường và ghi nhận ngoài phạm vi ở `03_TEST_PLAN.md`.
Nền: `origin/Khuong/main`, đã fetch và đồng bộ ngày 16/10/2026.
Không merge/cherry-pick code hoặc commit tích hợp của `feature/dx-farm-efficientnetv2m`; chỉ dùng lại checkpoint đã chọn.
Nhánh: `feature/dx-farm-efficientnetv2m`.
Phạm vi duy nhất: thay model bệnh YOLOv8n bằng checkpoint DX-Farm EfficientNetV2-M do người huấn luyện cung cấp.

| File | Nội dung |
|---|---|
| [00_OVERVIEW.md](./00_OVERVIEW.md) | Mục tiêu, ranh giới và tiêu chí bàn giao |
| [01_PLAN.md](./01_PLAN.md) | Work package và danh sách file được sửa |
| [02_CHECKLIST.md](./02_CHECKLIST.md) | Checklist thực hiện và dọn trước release |
| [03_TEST_PLAN.md](./03_TEST_PLAN.md) | Các kiểm tra cần thực hiện khi được yêu cầu |
| [04_TRADEOFFS_RISKS.md](./04_TRADEOFFS_RISKS.md) | Thay đổi từ detector sang classifier, đóng gói và rollback |

Theo [quy ước template](../_template/README.md), khi hoàn thành sẽ cập nhật trạng thái cuối vào tài liệu chính, đưa việc còn lại vào BACKLOG và xóa thư mục phase này.
