# Giới hạn và rủi ro: Phase 6

## 1. Quyết định thiết kế

| Mã | Quyết định | Lợi ích | Giới hạn |
|---|---|---|---|
| TO1 | Giữ model 5 lớp bệnh lá, không huấn luyện lớp background | Giữ phạm vi D7 | Precheck không xác nhận ảnh là lúa; fallback confidence 0 không chứng minh cây khỏe |
| TO2 | Một báo cáo có nhiều chẩn đoán; ID lớn nhất là hiện hành | Cho phép chạy lại, giữ lịch sử | Rollback về 1–1 đòi xử lý dữ liệu đã phát sinh |
| TO3 | Cho lưu chẩn đoán khi thiếu treatment rule | Ghi nhận bệnh trên DB chưa có rule được duyệt | Chờ khuyến nghị; không cho apply và không sinh thuốc/liều/PHI |

## 2. Rủi ro và cách kiểm soát

| Mã | Rủi ro | Kiểm soát |
|---|---|---|
| R1 | Lấy nhầm chẩn đoán cũ hoặc làm mất khóa PHI | Lấy hiện hành theo ID; PHI dùng tất cả lần phun; test rerun/PHI/đồng thời |
| R2 | Hiểu nhầm precheck đạt là ảnh đúng nội dung | UI ghi rõ chỉ kiểm chất lượng; kỹ sư chốt bệnh |
| R3 | Nền sắc, nhiễu hoặc chủ thể ngoài trung tâm làm sai điểm nét | Chuẩn hóa ảnh, trung vị 16 vùng, hiệu chỉnh nhiễu; còn cần kiểm chứng camera thật và tách vùng lá nếu cần |
| R4 | AI không hoạt động khi nộp ảnh | Trả 503, không nhận ảnh chưa kiểm; hàng đợi retry lỗi dịch vụ |

## 3. Vận hành và tương thích

- Confidence trong code là 0.70; sharpness từ 20, brightness 20–245. Đây là các ngưỡng kỹ thuật cần đối chiếu điều kiện thực địa.
- Điểm nét từ thuật toán cũ không so sánh trực tiếp với điểm mới; không tự đổi trạng thái báo cáo đã lưu.
- V7 chạy sau V5/V6 của Phase 4, giữ migration nhánh cha; không backfill nhãn kỹ sư giả.
- Kết quả AI stale trả 409. Ghi chẩn đoán/duyệt và retake được kiểm soát bằng transaction/row lock.
- P5 chưa có hộp thông báo: farmer mở báo cáo để xem yêu cầu ảnh mới. Workflow P3 đã được tích hợp.
- Nhánh cha chưa seed thuốc/rule được duyệt; chẩn đoán thiếu rule vẫn lưu nhưng không được áp dụng thuốc.

## 4. Điều kiện dừng và rollback

Nếu phát hiện sai chẩn đoán hiện hành hoặc sai khóa PHI, dừng merge WP6-05 để sửa và chạy lại hồi quy.
Ưu tiên sửa tiếp; không thêm lại unique 1–1 khi còn nhiều chẩn đoán cho cùng báo cáo.

Nhãn kỹ sư có thể dùng ở P7; Phase 6 chỉ lưu dữ liệu có cấu trúc.
