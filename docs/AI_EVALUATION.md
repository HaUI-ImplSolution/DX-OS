# Đánh giá AI chẩn đoán bệnh lúa

Kết quả chạy thử pipeline thị giác máy (CV) của AI Service trên bộ ảnh công khai độc lập với dữ liệu huấn luyện.
Đây là phép thử nhỏ để biết mô hình đang ở đâu, chưa phải đánh giá thống kê đầy đủ.

## 1. Phạm vi

| Hạng mục | Giá trị |
|---|---|
| Mô hình | YOLOv8n-RiceLeafBD 3.1.0, 5 lớp: Blast, Brown Spot, Leaf Smut, Rice Tungro, Sheath Blight |
| Bộ lọc ảnh | Kích thước, độ nét, độ sáng, rồi bộ phân loại cây trồng (ngưỡng lúa 0.80) |
| Ngưỡng | Phát hiện 0.25; workflow chỉ coi là chắc chắn từ 0.70 |
| Mã chạy | Đúng `PlantDiseaseService` và `assess_image` của service thật |

## 2. Bộ ảnh

- Nguồn: Dhan-Shomadhan, Hossain và cộng sự (2021), Mendeley Data, doi `10.17632/znsxdctwtt.1`, CC BY 4.0, bản HuggingFace `Project-AgML/Dhan-Shomadhan_rice_leaf_disease_classification`.
- 30 ảnh: 5 nhóm, mỗi nhóm 3 ảnh nền ruộng (Field) và 3 ảnh nền trắng (White), chọn cách đều theo chỉ số, không chọn tay.
- 4 nhóm thuộc lớp đã huấn luyện: Blast, Brown Spot, Rice Tungro, Sheath Blight.
- Nhóm Leaf Scald (bệnh cháy lá) không thuộc lớp nào của mô hình, dùng để xem hệ thống xử lý bệnh lạ.
- Lớp Leaf Smut không có ảnh trong bộ này nên chưa được đánh giá.
- Ảnh gốc 1952 x 4160 px; nhãn do tác giả bộ dữ liệu gán, chưa qua chuyên gia nông nghiệp của dự án.

## 3. Kết quả

Chạy bằng `python -m scripts.evaluate_cv <thư mục ảnh>` trong `ai/`.

| Nhóm | Số ảnh | Bị chặn ở bộ lọc | Ra "không thấy bệnh" | Đúng bệnh | Sai bệnh | Đạt từ 0.70 |
|---|---|---|---|---|---|---|
| Blast | 6 | 3 | 3 | 0 | 0 | 0 |
| Brown Spot | 6 | 4 | 2 | 0 | 0 | 0 |
| Rice Tungro | 6 | 4 | 2 | 0 | 0 | 0 |
| Sheath Blight | 6 | 3 | 3 | 0 | 0 | 0 |
| Leaf Scald (lớp lạ) | 6 | 3 | 2 | không áp dụng | 1 | 0 |

Theo nền ảnh:

| Nền | Số ảnh | Bị chặn ở bộ lọc | Vào được mô hình | Mô hình phát hiện từ 0.25 |
|---|---|---|---|---|
| Field | 15 | 2 (bộ phân loại cây trồng) | 13 | 1 (sai: Leaf Scald thành Sheath Blight, 0.26) |
| White | 15 | 15 (độ nét) | 0 | không có |

Khi bỏ qua bộ lọc và hạ ngưỡng phát hiện xuống 0.05 để xem mô hình thô:

- Đúng lớp ở 7 trên 24 ảnh thuộc lớp đã huấn luyện (5 Blast, 1 Rice Tungro, 1 Sheath Blight).
- Độ tin cậy cao nhất 0.43, không ảnh nào đạt 0.70.
- Ảnh Brown Spot nền trắng bị đoán thành Blast.

## 4. Nhận xét

1. Với ngưỡng đang dùng, không ảnh nào của bốn lớp đã huấn luyện được chẩn đoán đúng.
   Mô hình chưa tổng quát hóa sang bộ ảnh này.
2. Hệ thống không đưa ra kết luận sai với độ tin cậy cao (0 trường hợp sai từ 0.70).
   Ảnh không có kết luận đều đi vào luồng cần kỹ sư xem, phù hợp thiết kế kỹ sư là người chốt bệnh.
3. Bộ lọc độ nét chặn toàn bộ 15 ảnh nền trắng (độ nét 0.1 đến 11.8, ngưỡng 20) dù ảnh rõ.
   Chỉ số độ nét dựa vào chi tiết nên ảnh lá trên nền trắng đồng nhất bị coi là mờ.
   Ảnh nền ruộng đều qua bộ lọc này.
4. Bộ phân loại cây trồng chặn nhầm 2 trên 15 ảnh nền ruộng (xác suất lúa 0.60 và 0.00).
5. Ảnh Leaf Scald không bị đoán thành một bệnh trong năm lớp một cách chắc chắn, đúng với việc luật 0.70 chặn bệnh lạ.

## 5. Giới hạn của phép thử

- Chỉ 30 ảnh từ một bộ dữ liệu; mỗi ô nền và nhóm có 3 ảnh nên chưa tính được khoảng tin cậy.
- Ảnh cao 1:2.1 bị co về 640 px, vùng bệnh có thể quá nhỏ; chưa thử cắt ảnh trước khi chạy.
- Chưa biết bộ ảnh này có nằm trong dữ liệu huấn luyện của RiceLeafBD hay không; kết quả thấp cho thấy khả năng trùng là nhỏ.
- Chưa hiệu chỉnh lại ngưỡng độ nét, ngưỡng lúa và ngưỡng 0.70.

## 6. Hướng cải thiện

- Chụp 100 ảnh trở lên từ ruộng thật của hợp tác xã, kỹ sư gán nhãn, rồi chạy lại phép thử này làm mốc.
- Huấn luyện thêm hoặc tinh chỉnh mô hình bằng ảnh nền ruộng và ảnh dọc từ điện thoại.
- Đo lại ngưỡng độ nét trên ảnh nền sáng đồng nhất để không chặn nhầm.
- Tách ảnh theo lớp, ghi confusion matrix và chỉ số theo lớp khi có đủ mẫu.
