# Đánh giá AI chẩn đoán bệnh lúa

Kết quả model bệnh DX-Farm EfficientNetV2-M do người huấn luyện cung cấp; không chấm lại accuracy trong đợt tích hợp.
Các phép thử bộ lọc ảnh ở mục 3.2 và 3.4 giữ kết quả trước đây vì bộ lọc không thay đổi.

## 1. Phạm vi

| Hạng mục | Giá trị |
|---|---|
| Mô hình | DX-Farm-EfficientNetV2-M 4.0.0: Healthy, Blast, Brown Spot, Leaf Smut, Rice Tungro, Sheath Blight |
| Tiền xử lý model bệnh | RGB/EXIF, resize toàn ảnh 480×480 bicubic có antialias, chuẩn hóa ImageNet, softmax sáu lớp và top-1 |
| Bộ lọc ảnh | Kích thước, độ nét (có nhánh cho ảnh nền đơn sắc), độ sáng, bộ phân loại cây trồng (ngưỡng lúa 0.80) |
| Ngưỡng | Workflow nhận confidence từ 0.70, kỹ sư vẫn duyệt kết luận |
| Mã chạy | `PlantDiseaseService` và `assess_image` của service thật, qua `python -m scripts.evaluate_cv <thư mục>` |

## 2. Bộ ảnh

- Model bệnh: tập test 1.466 ảnh RiceLeafDiseaseBD v3, DOI `10.17632/86s4jzj2m4.3`, CC BY 4.0, theo báo cáo người huấn luyện.
- Bộ lọc ảnh: phép thử trước đây dùng 100 ảnh Dhan-Shomadhan (Mendeley Data, DOI `10.17632/znsxdctwtt.1`, CC BY 4.0), chia đều năm nhóm và hai loại nền Field/White.
- Bộ ảnh kiểm bộ lọc không phải tập chấm model bệnh mới.
- Ảnh không phải lúa để thử bộ phân loại: 15 ảnh ngô (`Project-AgML/corn_leaf_disease_classification`) và 15 ảnh mía (`Project-AgML/sugarcane_leaf_disease_classification`).

## 3. Kết quả

### 3.1. Model bệnh theo báo cáo người huấn luyện

| Lớp | Precision | Recall | F1 | Số ảnh |
|---|---:|---:|---:|---:|
| Healthy | 0.908333 | 0.923729 | 0.915966 | 236 |
| Blast | 0.886486 | 0.824121 | 0.854167 | 199 |
| Brown Spot | 0.849558 | 0.880734 | 0.864865 | 327 |
| Leaf Smut | 0.770642 | 0.777778 | 0.774194 | 108 |
| Rice Tungro | 0.932927 | 0.908012 | 0.920301 | 337 |
| Sheath Blight | 0.947170 | 0.969112 | 0.958015 | 259 |
| Macro avg | 0.882519 | 0.880581 | 0.881251 | 1466 |
| Weighted avg | 0.894629 | 0.894270 | 0.894160 | 1466 |

Accuracy 0.894270 (89,43%); đây là chỉ số classifier, không phải độ chính xác toàn pipeline có quality/crop gate.

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

### 3.3. Ngưỡng workflow

Giữ ngưỡng 0.70 và so trên confidence chưa làm tròn.
Báo cáo được cung cấp chưa đo riêng precision/recall hoặc tỉ lệ nhận ảnh tại ngưỡng này.
Healthy là lớp được huấn luyện, không suy ra từ việc model không phát hiện được bệnh.

### 3.4. Bộ phân loại cây trồng với ảnh không phải lúa

| Bộ ảnh | Số ảnh | Bị nhận là lúa |
|---|---|---|
| Ngô | 15 | 6 |
| Mía | 15 | 15 |

## 4. Nhận xét

1. Sheath Blight có F1 cao nhất (0.958015), Leaf Smut thấp nhất (0.774194) trong báo cáo người huấn luyện.
2. Confidence từ 0.70 vẫn cần kỹ sư duyệt; chưa có đánh giá độc lập về độ tin cậy ngoài tập test.
3. Bộ lọc độ nét nhận ảnh nền trắng đồng nhất; ảnh làm mờ và ảnh mờ có nhiễu hạt bị chặn.
4. Bộ phân loại cây trồng chặn 8/50 ảnh lúa nền ruộng, nặng nhất ở Rice Tungro (lá vàng cam), và nhận mía, ngô là lúa vì chỉ có 10 lớp, không có lớp ngô, mía, cỏ.
5. Classifier sáu lớp chưa có cơ chế nhận biết bệnh ngoài tập nhãn; ảnh lạ có thể bị gán một lớp với confidence cao.

## 5. Giới hạn của phép thử

- Chỉ số model mới là báo cáo của người huấn luyện trên tập test dự án, chưa phải đánh giá độc lập theo cây/ruộng.
- Phép thử bộ lọc dùng 100 ảnh, mỗi ô 10 ảnh nên khoảng tin cậy còn rộng.
- Ảnh mờ là mờ nhân tạo, chưa có ảnh mờ do rung tay hay lệch tiêu cự thật.
- Các ngưỡng độ nét, lúa và 0.70 chưa đo trên ảnh ngoài ruộng của hợp tác xã.
- Ảnh không phải lúa chỉ gồm ngô và mía, chưa có cỏ dại và lúa mì.

## 6. Hướng cải thiện

- Chụp từ 100 ảnh ruộng thật của hợp tác xã, kỹ sư gán nhãn, rồi chạy lại phép thử này làm mốc.
- Bổ sung ảnh thực địa và dữ liệu cho Leaf Smut, kiểm tra các cặp bệnh hay nhầm trước khi huấn luyện thêm classifier.
- Thay hoặc huấn luyện thêm bộ phân loại cây trồng với lớp ngô, mía, cỏ dại, lúa mì.
- Ghi confusion matrix và chỉ số theo lớp khi có đủ mẫu.
