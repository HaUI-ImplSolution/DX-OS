# Model card: YOLOv8n-RiceLeafBD

## Thông tin phát hành

| Thuộc tính | Giá trị |
|---|---|
| Tên model | `YOLOv8n-RiceLeafBD` |
| Phiên bản model | `3.0.0` |
| Loại tác vụ | Object detection trên ảnh lá lúa |
| Kiến trúc | YOLOv8n, fine-tune từ pretrained `yolov8n.pt` |
| Artifact | `ai/weights/best.pt` |
| SHA-256 | `573db0871f39396f2bbec5e6aa5988d36b79b13e01cb00f03eee29b32a12c60b` |
| Người train | [`@nddKhanh`](https://github.com/nddKhanh) |
| Nhóm phát hành | DX-Farm |
| Nguồn quá trình train | [Kaggle notebook `baodark/dx-farm-8169e1`](https://www.kaggle.com/code/baodark/dx-farm-8169e1/notebook) |
| Thời điểm ghi trong checkpoint | `2026-09-29T16:10:49.967776+00:00` |
| License checkpoint | AGPL-3.0; xem [`MODEL_LICENSE`](MODEL_LICENSE) |

Nhóm DX-Farm đã đồng ý công khai checkpoint. Metadata của checkpoint ghi
`AGPL-3.0 (https://ultralytics.com/license)`. Ultralytics cũng quy định model được train
hoặc fine-tune bằng Ultralytics mặc định thuộc AGPL-3.0 khi không có commercial license
khác. License Apache-2.0 ở gốc repository không thay thế license của checkpoint.

## Mục đích sử dụng

Model hỗ trợ phát hiện năm nhóm bệnh trên ảnh lá lúa để tạo tín hiệu chẩn đoán ban đầu cho
DX-Farm. Kết quả được Backend và người có trách nhiệm kiểm tra trước khi lưu hoặc sử dụng
trong quy trình khuyến nghị.

Không sử dụng model để:

- tự động xác nhận biện pháp điều trị, thuốc hoặc liều lượng;
- thay thế chuyên gia nông nghiệp hoặc khảo sát thực địa;
- suy luận cho cây trồng, bộ phận cây hoặc bệnh ngoài phạm vi huấn luyện;
- khẳng định lá/cây/ruộng hoàn toàn khỏe khi model không phát hiện bounding box.

## Dataset

Model sử dụng
[RiceLeafDiseaseBD Version 3](https://data.mendeley.com/datasets/86s4jzj2m4/3), DOI
[`10.17632/86s4jzj2m4.3`](https://doi.org/10.17632/86s4jzj2m4.3), theo
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Attribution đầy đủ cho các tác
giả dataset được lưu trong [`../LICENSES.md`](../LICENSES.md).

Dataset gốc có sáu lớp. Quá trình train model này sử dụng năm lớp bệnh và không dùng lớp
`Healthy` làm class detection. Ảnh được resize về `640`; augmentation thực tế được ghi ở
phần cấu hình train bên dưới. Hồ sơ hiện chưa lưu được manifest hoặc script tái tạo chính
xác cách chia train/validation/test, vì vậy không tuyên bố khả năng tái tạo dataset split.

## Class contract

| Class ID | Nhãn model | `disease_code` DX-Farm |
|---:|---|---|
| 0 | `Blast` | `Rice___Blast` |
| 1 | `Brown Spot` | `Rice___Brown_spot` |
| 2 | `Leaf Smut` | `Rice___Leaf_smut` |
| 3 | `Rice Tungro` | `Rice___Tungro` |
| 4 | `Sheath Blight` | `Rice___Sheath_blight` |

`Rice___healthy` là fallback runtime khi không có detection đạt confidence threshold, không
phải class thứ sáu của checkpoint và không phải kết luận cây hoặc ruộng hoàn toàn khỏe.
Contract canonical nằm tại [`../data/structured/class_contract.json`](../data/structured/class_contract.json).

## Quá trình train

Các giá trị dưới đây được phục hồi từ metadata của checkpoint tin cậy:

| Thuộc tính | Giá trị |
|---|---|
| Ultralytics lúc train | `8.4.165` |
| Base model | [`yolov8n.pt`](https://github.com/ultralytics/assets/releases/download/v8.3.0/yolov8n.pt), `pretrained=true` |
| Epoch tối đa / patience | `80` / `20` |
| Batch / image size | `16` / `640` |
| Optimizer | AdamW |
| Learning rate | `lr0=0.001`, `lrf=0.01`, cosine schedule |
| Weight decay | `0.0005` |
| Seed | `42`, `deterministic=true` |
| Thiết bị train | CUDA device `0` trên Kaggle; chưa lưu chính xác loại GPU/CUDA |
| AMP | Bật |
| Validation | `split=val`, `iou=0.7`, `max_det=300` |

Augmentation: `hsv_h=0.015`, `hsv_s=0.5`, `hsv_v=0.4`, `degrees=8`,
`translate=0.1`, `scale=0.5`, `flipud=0.1`, `fliplr=0.5`, `mosaic=0.7` và
`close_mosaic=10`. Shear, perspective, mixup, cutmix và copy-paste bằng `0`.

File base `development/.local/yolov8n.pt` còn giữ tại máy người train có SHA-256
`f59b3d833e2ff32e194b5bb8e08d211dc7c5bdf144b90d2c8412c47ccfc83b36`. Metadata
checkpoint chỉ lưu tên `yolov8n.pt`, nên URL release ở trên là nguồn được phục hồi từ asset
chính thức chứ không phải URL được nhúng trong checkpoint.

Những thông tin chưa phục hồi được gồm phiên bản Python/PyTorch/CUDA của môi trường train,
commit mã nguồn train, log đầy đủ, `results.csv` và quy tắc chọn epoch tạo `best.pt`.

## Kết quả đánh giá hiện có

Checkpoint chỉ chứa metric trên **validation split**, không phải test set độc lập:

| Metric validation | Giá trị |
|---|---:|
| Precision | `0.52300` |
| Recall | `0.39748` |
| mAP@0.5 | `0.39957` |
| mAP@0.5:0.95 | `0.17107` |

Chưa có báo cáo test độc lập, metric theo từng class, confusion matrix hoặc đánh giá ngoài
điều kiện thu thập của dataset. Không dùng các số validation trên như bằng chứng về hiệu quả
trong mọi điều kiện thực tế.

## Giới hạn và rủi ro

- Chỉ năm bệnh đã liệt kê được hỗ trợ; model không có class non-leaf hoặc unknown.
- Ánh sáng, nền, giống lúa, thiết bị chụp, giai đoạn sinh trưởng và mức độ bệnh khác dữ liệu
  train có thể làm giảm chất lượng dự đoán.
- Fallback healthy có thể che khuất bệnh ngoài phạm vi hoặc detection có confidence thấp.
- Metric hiện có chưa được xác nhận trên test set độc lập.
- File PyTorch `.pt` có thể thực thi dữ liệu khi giải tuần tự; chỉ load artifact tin cậy sau
  khi xác minh checksum.

## Chạy và xác minh artifact

Service yêu cầu `CV_WEIGHTS_PATH` trỏ đến checkpoint và `CV_WEIGHTS_SHA256` chứa checksum
kỳ vọng. Với Docker Compose, đặt `AI_CV_WEIGHTS_PATH` và `AI_CV_WEIGHTS_SHA256` trong
`deploy/.env`; Compose bind-mount checkpoint ở chế độ read-only.

Xác minh checksum trên PowerShell:

```powershell
(Get-FileHash ai/weights/best.pt -Algorithm SHA256).Hash.ToLowerInvariant()
```

Sau khi khởi động service:

```bash
curl http://localhost:8000/api/v1/disease/health
```

Runtime của repo khóa `ultralytics==8.3.40`, khác với Ultralytics `8.4.165` được ghi trong
checkpoint lúc train. Loader kiểm tra checksum, task `detect`, số lớp và thứ tự nhãn trước
khi cho model phục vụ request.

## Nguồn và license liên quan

- Checkpoint và Ultralytics: AGPL-3.0, xem [`MODEL_LICENSE`](MODEL_LICENSE).
- Mã nguồn DX-Farm tự viết: Apache-2.0, xem [`../LICENSE`](../LICENSE).
- Dataset: CC BY 4.0, xem attribution trong [`../LICENSES.md`](../LICENSES.md).
- Bảng kiểm phát hành và những bằng chứng còn thiếu:
  [`MODEL_RELEASE_CHECKLIST.md`](MODEL_RELEASE_CHECKLIST.md).

Tài liệu này mô tả bằng chứng kỹ thuật hiện có và không thay thế tư vấn pháp lý.
