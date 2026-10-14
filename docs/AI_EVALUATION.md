# Đánh giá AI chẩn đoán bệnh lúa

Kết quả chạy pipeline thị giác máy (CV) của AI Service trên ảnh công khai độc lập với dữ liệu huấn luyện.
Đây là phép thử nhỏ để biết mô hình đang ở đâu, chưa phải đánh giá thống kê đầy đủ.

## 1. Phạm vi

| Hạng mục | Giá trị |
|---|---|
| Mô hình | YOLOv8n-RiceLeafBD 3.1.0: Blast, Brown Spot, Leaf Smut, Rice Tungro, Sheath Blight |
| Bộ lọc ảnh | Kích thước, độ nét (có nhánh cho ảnh nền đơn sắc), độ sáng, bộ phân loại cây trồng (ngưỡng lúa 0.80) |
| Ngưỡng | Phát hiện 0.25; workflow coi là chắc chắn từ 0.70 |
| Mã chạy | `PlantDiseaseService` và `assess_image` của service thật, qua `python -m scripts.evaluate_cv <thư mục>` |

## 2. Bộ ảnh

- 100 ảnh từ Dhan-Shomadhan (Mendeley Data, doi `10.17632/znsxdctwtt.1`, CC BY 4.0): 5 nhóm, mỗi nhóm 10 ảnh nền ruộng (Field) và 10 ảnh nền trắng (White), chọn cách đều theo chỉ số.
- 4 nhóm thuộc lớp đã huấn luyện (Blast, Brown Spot, Rice Tungro, Sheath Blight); Leaf Scald không thuộc lớp nào, dùng để xem cách xử lý bệnh lạ; Leaf Smut không có ảnh.
- Ảnh gốc 1952 x 4160 px; nhãn do tác giả bộ dữ liệu gán, chưa qua chuyên gia của dự án.
- Ảnh không phải lúa để thử bộ phân loại: 15 ảnh ngô (`Project-AgML/corn_leaf_disease_classification`) và 15 ảnh mía (`Project-AgML/sugarcane_leaf_disease_classification`).

## 3. Kết quả

### 3.1. Toàn pipeline

| Nhóm | Số ảnh | Bị chặn ở bộ lọc | Không thấy bệnh | Đúng bệnh | Sai bệnh | Đạt từ 0.70 |
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

Độ nét được kiểm trên 17 ảnh mỗi nền, kèm bản làm mờ nhân tạo (Gaussian 0.2% và 0.4% cạnh dài) và bản làm mờ 0.4% cộng nhiễu hạt 4, 8, 12 mức xám:

| Nền | Ảnh gốc đạt | Làm mờ 0.4% đạt | Làm mờ 0.4% kèm nhiễu 4, 8, 12 đạt |
|---|---|---|---|
| Field | 17/17 | 0/17 | 0/17 mỗi mức |
| White | 16/17 | 0/17 | 0/17 mỗi mức |

Làm mờ 0.2% cho 9/17 ảnh Field và 0/17 ảnh White đạt; kèm nhiễu 12 thì 1/17 ảnh White đạt.

### 3.3. Detector khi bỏ qua bộ lọc

Chạy detector với độ tin cậy từ 0.05 trên 80 ảnh thuộc lớp đã huấn luyện:

| Cách đưa ảnh vào | Đúng lớp | Từ 0.25: đúng / sai | Độ tin cậy cao nhất | Từ 0.70 |
|---|---|---|---|---|
| Ảnh nguyên (như môi trường chạy thật) | 21/80 | 5 / 5 | 0.57 | 0 |
| Cắt 3 ô vuông, lấy kết quả cao nhất | 32/80 | 17 / 19 | | 0 |

Brown Spot không ảnh nào đúng lớp, thường bị đoán thành Blast (7/20) hoặc Rice Tungro (3/20).
Cắt ô làm số ảnh sai tăng theo (độ chính xác khoảng 47%), nên pipeline thật dùng ảnh nguyên.

### 3.4. Bộ phân loại cây trồng với ảnh không phải lúa

| Bộ ảnh | Số ảnh | Bị nhận là lúa |
|---|---|---|
| Ngô | 15 | 6 |
| Mía | 15 | 15 |

## 4. Nhận xét

1. 4 trên 80 ảnh thuộc lớp đã huấn luyện được chẩn đoán đúng, đều dưới 0.70; mô hình chưa tổng quát hóa sang bộ ảnh này.
2. Không có kết luận sai với độ tin cậy từ 0.70; ảnh không có kết luận đều đi vào luồng cần kỹ sư xem.
3. Bộ lọc độ nét nhận ảnh nền trắng đồng nhất; ảnh làm mờ và ảnh mờ có nhiễu hạt bị chặn.
4. Bộ phân loại cây trồng chặn 8/50 ảnh lúa nền ruộng, nặng nhất ở Rice Tungro (lá vàng cam), và nhận mía, ngô là lúa vì chỉ có 10 lớp, không có lớp ngô, mía, cỏ.
5. Ảnh Leaf Scald không bị đoán thành một bệnh trong năm lớp một cách chắc chắn.

## 5. Giới hạn của phép thử

- 100 ảnh từ một bộ dữ liệu, mỗi ô 10 ảnh nên khoảng tin cậy còn rộng; chưa biết bộ ảnh có nằm trong dữ liệu huấn luyện của RiceLeafBD hay không.
- Ảnh mờ là mờ nhân tạo, chưa có ảnh mờ do rung tay hay lệch tiêu cự thật.
- Các ngưỡng độ nét, lúa và 0.70 chưa đo trên ảnh ngoài ruộng của hợp tác xã.
- Ảnh không phải lúa chỉ gồm ngô và mía, chưa có cỏ dại và lúa mì.

## 6. Hướng cải thiện

- Chụp từ 100 ảnh ruộng thật của hợp tác xã, kỹ sư gán nhãn, rồi chạy lại phép thử này làm mốc.
- Huấn luyện thêm detector bằng ảnh nền ruộng và ảnh dọc từ điện thoại; Brown Spot cần dữ liệu bổ sung.
- Thay hoặc huấn luyện thêm bộ phân loại cây trồng với lớp ngô, mía, cỏ dại, lúa mì.
- Ghi confusion matrix và chỉ số theo lớp khi có đủ mẫu.
