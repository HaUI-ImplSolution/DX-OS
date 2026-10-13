# Computer Vision: nhận diện bệnh lá lúa

`app/cv/` là lõi CV của AI Service: hợp đồng lớp bệnh, nạp checkpoint và chính sách xử lý ảnh.
Không có API riêng; FastAPI và workflow gọi vào đây để lấy kết quả chẩn đoán ban đầu.
Kết quả quan trọng nhất là `disease_code` chuẩn, nối ảnh với dữ liệu bệnh, rule điều trị và RAG.

CV **không** quyết định thuốc, liều, PHI; không truy vấn RAG hay gọi LLM; không ghi chẩn đoán vào database.

## Luồng

```text
Ảnh → kiểm tra là ảnh, ≤ 10 MiB
    → kích thước tối thiểu, độ nét, độ sáng
    → crop classifier kiểm điều kiện Rice
    → YOLOv8n (ảnh 640) → lọc theo confidence và IoU → chọn detection cao nhất
    → class ID → disease_code qua class contract
```

```json
{ "disease_code": "Rice___Blast", "disease_name": "Bệnh đạo ôn trên lúa",
  "confidence": 0.932, "model_name": "YOLOv8n-RiceLeafBD", "model_version": "3.1.0" }
```

`disease_code` khớp chính xác `Disease.code` trong PostgreSQL, ánh xạ duy nhất từ `data/structured/class_contract.json`; không có fuzzy mapping.

## Thành phần

| Thành phần | Vai trò |
|---|---|
| `loader.py` | Nạp checkpoint cục bộ, kiểm checksum, task, class contract, chọn CPU/CUDA |
| `contract.py` | Đọc và tra cứu ánh xạ class sang `disease_code` |
| `model_profile.py` | Metadata cố định của checkpoint đã xác thực |
| `policy.py` | Ngưỡng inference, workflow và chất lượng ảnh |
| `triage.py` | Lỗi khi ảnh hoặc kết quả chưa đủ điều kiện đi tiếp |
| `crop_classifier.py` | Crop classifier ONNX Runtime CPU |
| `app/services/plant_disease_service.py` | Điều phối kiểm ảnh, suy luận, định dạng kết quả |

## Vận hành

- Checkpoint YOLOv8n RiceLeafBD `3.1.0` (150 epochs, 2026-10-07) ở `ai/weights/best.pt`, quản lý bằng Git; không tự tải.
- Model nạp một lần lúc khởi động; phải khớp `CV_WEIGHTS_SHA256`, task `detect` và thứ tự class trong contract.
- `CV_DEVICE=auto` ưu tiên CUDA khi có GPU.
- Confidence bệnh dưới `WORKFLOW_CONFIDENCE_THRESHOLD` (`0.70`) thì yêu cầu chụp lại, không truy vấn Backend, RAG hay LLM; fallback confidence `0` bị từ chối.
- Model chỉ có 5 lớp: đạo ôn, đốm nâu, than lá, tungro, khô vằn.
  `Rice___healthy` là fallback khi không có detection đạt ngưỡng, không khẳng định cây khỏe.

## Kiểm tra chất lượng ảnh

`POST /api/v1/disease/precheck` trả `passed`, `reasons`, `width`, `height`, `sharpness`, `brightness`.
Precheck và predict dùng chung `assess_image` (chất lượng trước, crop classifier sau); precheck không chạy detector.
Backend bỏ EXIF/GPS, kiểm lại khi nộp và chỉ lưu ảnh đạt chuẩn.

| Điều kiện | Ngưỡng |
|---|---|
| Định dạng, độ phân giải | JPEG/PNG, tối đa 40 megapixel |
| Kích thước | Mỗi chiều từ 160px |
| Độ sáng trung bình | 20–245 |
| Độ nét | `sharpness >= 20` |

Độ nét tính trên ảnh xám tối đa 640px: bỏ 10% viền, làm mượt Gaussian 5×5, lấy trung vị phương sai Laplacian của 16 vùng, trừ nhiễu ước lượng bằng MAD.
Chưa tách vùng lá và ngưỡng chưa hiệu chuẩn bằng ảnh ngoài ruộng.

## Phân loại lúa

- Artifact [YOLO26s-cls](https://huggingface.co/RaiyanKhaan/KrishokTech-Models/tree/c339988a6eceb5e22943ee27e4731a1a1dc9160a/01_crop_classifier) (10 lớp, `Rice=8`) ở `ai/weights/crop_classifier.onnx`, quản lý bằng Git; ONNX Runtime 1.30.0, detector giữ Ultralytics 8.3.40.
- `CROP_CLASSIFIER_PATH` mặc định `ai/weights/crop_classifier.onnx`; Compose dùng `AI_CROP_CLASSIFIER_PATH`.
  Khi nạp kiểm file, task, mapping, shape, dtype; không đối chiếu SHA-256 nên model khác cùng contract vẫn nạp được.
- Tiền xử lý: RGB, sửa hướng EXIF, resize cạnh ngắn 224 (bilinear), center crop 224, float32 /255.
- Cho qua khi top1 là Rice, P(Rice) ≥ 0.80 và chênh lớp khác cao nhất ≥ 0.20; độc lập với ngưỡng bệnh 0.70.
- `passed` là kết quả chất lượng **và** classifier; ảnh mới lưu `quality_passed` tổng hợp, ảnh cũ không backfill.
- Precheck không đạt: 200 `passed=false`; predict/diagnose: 422, không gọi detector, Backend facts, RAG, LLM. Ảnh hỏng: 400 (diagnose 422). Classifier lỗi: 503.
- Health `model_loaded` chỉ true khi cả hai model sẵn sàng.

**Giới hạn:** model nhận nhầm ngô, lúa mì và cỏ thành Rice với điểm rất cao; tăng ngưỡng không sửa được ca gần 100%.
Lần thử 15 ảnh có 3 ảnh ngoài lúa qua classifier, 2 ảnh trong đó còn qua kiểm chất lượng.
Việc chưa làm ở [`docs/BACKLOG.md`](../../../docs/BACKLOG.md).
