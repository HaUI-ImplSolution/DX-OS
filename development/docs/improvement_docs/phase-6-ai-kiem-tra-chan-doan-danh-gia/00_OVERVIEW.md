# Phase 6: Kiểm tra ảnh và chẩn đoán

| Mục | Giá trị |
|---|---|
| Trạng thái | Đã triển khai và kiểm thử tự động; chờ xác nhận đầy đủ luồng điện thoại |
| Người phụ trách | tung |
| Mức độ / ưu tiên | Quan trọng / B |
| Ước lượng | 5–6 ngày |
| Tham chiếu | Mục 5, 11 của `PROJECT_IMPROVEMENTS.md`; D6/D7 của Phase 0 |
| Nền tích hợp | `Khuong/main` tại `f52f564`, có P1/P2/P3 |

## Phạm vi

| WP | Kết quả |
|---|---|
| WP6-01 | Kiểm chất lượng trước khi gửi ảnh; server kiểm lại khi nộp |
| WP6-03 | Trạng thái cần chụp lại và chuỗi báo cáo cũ/mới |
| WP6-04 | Gợi ý bệnh từ triệu chứng, kỹ sư chốt và lưu nhãn |
| WP6-05 | Gọi AI ngoài transaction; cho phép chẩn đoán lại và giữ lịch sử |

CV giữ 5 lớp bệnh lá hiện có; RAG xếp hạng trong danh mục 6 mã, gồm mã fallback.
Phase này không huấn luyện thêm lớp sâu hại hoặc lớp nhận diện ảnh ngoài miền.
Precheck chỉ kiểm chất lượng, không xác nhận ảnh là lúa.

## Điều kiện hoàn thành

- T6-01 đến T6-08 và các ca hồi quy về PHI, idempotency, phân quyền đạt.
- Farmer nhận cảnh báo ảnh mờ trước khi gửi; ảnh rõ được nhận.
- Kỹ sư yêu cầu ảnh mới; farmer gửi ảnh thay thế và xem được chuỗi liên kết.
- Hàng đợi offline gửi lại đúng báo cáo, không tạo trùng.
- Kỹ sư nhập triệu chứng, chốt bệnh và chạy lại AI có lịch sử.

Phần triển khai và kiểm thử tự động đã đạt; chưa có xác nhận đầy đủ camera/offline/retake trên điện thoại thật.

## Tài liệu

- [Plan và hợp đồng API](./01_PLAN.md)
- [Checklist](./02_CHECKLIST.md)
- [Test plan và kết quả](./03_TEST_PLAN.md)
- [Giới hạn và rủi ro](./04_TRADEOFFS_RISKS.md)
