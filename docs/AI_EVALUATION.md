# Đánh giá AI chẩn đoán bệnh lúa

Kết quả chạy thử pipeline thị giác máy (CV) của AI Service trên bộ ảnh công khai độc lập với dữ liệu huấn luyện.
Đây là phép thử nhỏ để biết mô hình đang ở đâu, chưa phải đánh giá thống kê đầy đủ.

## 1. Phạm vi

| Hạng mục | Giá trị |
|---|---|
| Mô hình | YOLOv8n-RiceLeafBD 3.1.0, 5 lớp: Blast, Brown Spot, Leaf Smut, Rice Tungro, Sheath Blight |
| Bộ lọc ảnh | Kích thước, độ nét (có nhánh riêng cho ảnh nền đơn sắc), độ sáng, rồi bộ phân loại cây trồng (ngưỡng lúa 0.80) |
| Ngưỡng | Phát hiện 0.25; workflow chỉ coi là chắc chắn từ 0.70 |
| Mã chạy | Đúng `PlantDiseaseService` và `assess_image` của service thật |

## 2. Bộ ảnh

- Nguồn: Dhan-Shomadhan, Hossain và cộng sự (2021), Mendeley Data, doi `10.17632/znsxdctwtt.1`, CC BY 4.0, bản HuggingFace `Project-AgML/Dhan-Shomadhan_rice_leaf_disease_classification`.
- 100 ảnh: 5 nhóm, mỗi nhóm 10 ảnh nền ruộng (Field) và 10 ảnh nền trắng (White).
  Ảnh được chọn cách đều theo chỉ số trong từng ô, không chọn tay.
  Các nhãn viết sai chính tả của bộ dữ liệu (`Browon Spot`, `Rice Turgro`, `Shath Blight`) được gộp vào nhóm tương ứng.
- 4 nhóm thuộc lớp đã huấn luyện: Blast, Brown Spot, Rice Tungro, Sheath Blight.
- Nhóm Leaf Scald (bệnh cháy lá) không thuộc lớp nào của mô hình, dùng để xem hệ thống xử lý bệnh lạ.
- Lớp Leaf Smut không có ảnh trong bộ này nên chưa được đánh giá.
- Ảnh gốc 1952 x 4160 px.
  Nhãn do tác giả bộ dữ liệu gán, chưa qua chuyên gia nông nghiệp của dự án.
- Ảnh không phải lúa để thử bộ phân loại cây trồng: 15 ảnh ngô (`Project-AgML/corn_leaf_disease_classification`) và 15 ảnh mía (`Project-AgML/sugarcane_leaf_disease_classification`), chọn cách đều theo chỉ số.

## 3. Kết quả

Chạy bằng `python -m scripts.evaluate_cv <thư mục ảnh>` trong `ai/`.

### 3.1. Toàn pipeline (ngưỡng như môi trường chạy thật)

| Nhóm | Số ảnh | Bị chặn ở bộ lọc | Ra "không thấy bệnh" | Đúng bệnh | Sai bệnh | Đạt từ 0.70 |
|---|---|---|---|---|---|---|
| Blast | 20 | 0 | 18 | 2 | 0 | 0 |
| Brown Spot | 20 | 2 | 16 | 0 | 2 | 0 |
| Rice Tungro | 20 | 5 | 15 | 0 | 0 | 0 |
| Sheath Blight | 20 | 1 | 15 | 2 | 2 | 0 |
| Leaf Scald (lớp lạ) | 20 | 2 | 16 | không áp dụng | 2 | 0 |

### 3.2. Bộ lọc ảnh

| Nền | Số ảnh | Qua bộ lọc | Bị chặn ở độ nét | Bị chặn ở bộ phân loại cây trồng |
|---|---|---|---|---|
| Field | 50 | 41 | 1 | 8 (5 Rice Tungro, 2 Brown Spot, 1 Leaf Scald) |
| White | 50 | 49 | 1 | 0 |

Ngưỡng độ nét được kiểm trên 17 ảnh mỗi nền.
Mỗi ảnh có thêm bản làm mờ nhân tạo (Gaussian bán kính 0.2% và 0.4% cạnh dài) và bản làm mờ 0.4% cộng nhiễu hạt độ lệch 4, 8, 12 mức xám ở độ phân giải gốc.

| Nền | Ảnh gốc đạt | Làm mờ 0.4% đạt | Làm mờ 0.4% kèm nhiễu 4 đạt | Kèm nhiễu 8 đạt | Kèm nhiễu 12 đạt |
|---|---|---|---|---|---|
| Field | 17/17 | 0/17 | 0/17 | 0/17 | 0/17 |
| White | 16/17 | 0/17 | 0/17 | 0/17 | 0/17 |

Làm mờ 0.2% cho 9/17 ảnh Field đạt và 0/17 ảnh White đạt.
Làm mờ 0.2% kèm nhiễu 12 cho 1/17 ảnh White đạt.

### 3.3. Detector khi bỏ qua bộ lọc

Detector được chạy riêng với độ tin cậy từ 0.05 trên 80 ảnh thuộc lớp đã huấn luyện.

| Cách đưa ảnh vào | Đúng lớp | Từ 0.25: đúng / sai | Độ tin cậy cao nhất | Từ 0.70 |
|---|---|---|---|---|
| Ảnh nguyên (như môi trường chạy thật) | 21/80 | 5 / 5 | 0.57 | 0 |
| Cắt 3 ô vuông, lấy kết quả cao nhất | 32/80 | 17 / 19 | | 0 |

- Brown Spot không ảnh nào đúng lớp ở cả hai cách.
  Lớp này thường bị đoán thành Blast (7/20) hoặc Rice Tungro (3/20).
- Cắt ô tăng số ảnh đúng nhưng số ảnh sai tăng theo, độ chính xác khoảng 47% ở ngưỡng 0.25.
  Pipeline thật dùng ảnh nguyên.

### 3.4. Bộ phân loại cây trồng với ảnh không phải lúa

| Bộ ảnh | Số ảnh | Bị nhận là lúa |
|---|---|---|
| Ngô | 15 | 6 |
| Mía | 15 | 15 |

## 4. Nhận xét

1. 4 trên 80 ảnh thuộc lớp đã huấn luyện được chẩn đoán đúng, tất cả dưới 0.70.
   Mô hình chưa tổng quát hóa sang bộ ảnh này.
   Độ tin cậy cao nhất của một dự đoán đúng là 0.57.
2. Hệ thống không đưa ra kết luận sai với độ tin cậy cao (0 trường hợp sai từ 0.70).
   Ảnh không có kết luận đều đi vào luồng cần kỹ sư xem, phù hợp thiết kế kỹ sư là người chốt bệnh.
3. Bộ lọc độ nét nhận ảnh nền trắng đồng nhất theo phần năng lượng cạnh mất đi khi làm mờ lại.
   Ảnh làm mờ nhân tạo và ảnh mờ có nhiễu hạt bị chặn.
4. Bộ phân loại cây trồng chặn 8 trên 50 ảnh lúa nền ruộng, nặng nhất ở Rice Tungro (lá vàng cam).
   Bộ này nhận mía và ngô là lúa.
   Đây là phân loại kín 10 lớp, không có lớp ngô, mía, cỏ, nên không tách được các cây giống lúa.
5. Ảnh Leaf Scald không bị đoán thành một bệnh trong năm lớp một cách chắc chắn, đúng với việc luật 0.70 chặn bệnh lạ.

## 5. Giới hạn của phép thử

- 100 ảnh từ một bộ dữ liệu, mỗi ô nền và nhóm có 10 ảnh nên khoảng tin cậy còn rộng.
- Chưa biết bộ ảnh này có nằm trong dữ liệu huấn luyện của RiceLeafBD hay không.
  Kết quả thấp cho thấy khả năng trùng là nhỏ.
- Ảnh mờ là mờ nhân tạo (Gaussian), chưa có ảnh mờ do rung tay hay lệch tiêu cự thật.
- Ngưỡng độ nét, ngưỡng lúa và ngưỡng 0.70 chưa đo trên ảnh ngoài ruộng của hợp tác xã.
- Bộ ảnh không phải lúa chỉ gồm ngô và mía, chưa có cỏ dại và lúa mì.

## 6. Hướng cải thiện

- Chụp 100 ảnh trở lên từ ruộng thật của hợp tác xã, kỹ sư gán nhãn, rồi chạy lại phép thử này làm mốc.
- Huấn luyện thêm hoặc tinh chỉnh detector bằng ảnh nền ruộng và ảnh dọc từ điện thoại.
  Brown Spot cần dữ liệu bổ sung.
- Thay hoặc huấn luyện thêm bộ phân loại cây trồng với lớp ngô, mía, cỏ dại, lúa mì.
- Tách ảnh theo lớp, ghi confusion matrix và chỉ số theo lớp khi có đủ mẫu.
