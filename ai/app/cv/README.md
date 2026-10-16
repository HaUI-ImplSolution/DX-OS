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
    → EfficientNetV2-M (ảnh 480×480 bicubic, chuẩn hóa ImageNet) → softmax → chọn lớp cao nhất
    → class ID → disease_code qua class contract
```

```json
{ "disease_code": "Rice___Blast", "disease_name": "Bệnh đạo ôn trên lúa",
  "confidence": 0.932, "model_name": "DX-Farm-EfficientNetV2-M", "model_version": "4.0.0" }
```

`disease_code` khớp chính xác `Disease.code` trong PostgreSQL, ánh xạ duy nhất từ `data/structured/class_contract.json`; không có fuzzy mapping.

## Thành phần

| Thành phần | Vai trò |
|---|---|
| `loader.py` | Nạp checkpoint cục bộ, kiểm checksum, metadata và class contract, chọn CPU/CUDA |
| `contract.py` | Đọc và tra cứu ánh xạ class sang `disease_code` |
| `model_profile.py` | Metadata cố định của checkpoint đã xác thực |
| `policy.py` | Ngưỡng inference, workflow và chất lượng ảnh |
| `triage.py` | Lỗi khi ảnh hoặc kết quả chưa đủ điều kiện đi tiếp |
| `crop_classifier.py` | Crop classifier ONNX Runtime CPU |
| `app/services/plant_disease_service.py` | Điều phối kiểm ảnh, suy luận, định dạng kết quả |

## Vận hành

- Checkpoint DX-Farm EfficientNetV2-M `4.0.0` ở `ai/weights/dx-farm-efficientnetv2m/`, ba phần quản lý bằng Git; ghép trong Docker build, không tự tải.
- Model nạp một lần lúc khởi động; phải khớp `CV_WEIGHTS_SHA256`, kiến trúc, sáu lớp và recipe trong checkpoint.
- `CV_DEVICE=auto` ưu tiên CUDA khi có GPU.
- Confidence dưới `WORKFLOW_CONFIDENCE_THRESHOLD` (`0.70`) thì yêu cầu chụp lại, không truy vấn Backend, RAG hay LLM; điểm không làm tròn trước gate.
- Model có 6 lớp: Healthy, đạo ôn, đốm nâu, than lá, tungro, khô vằn.
  `Rice___healthy` là lớp được huấn luyện; metadata `fallback` giữ để tương thích consumer cũ, không dùng khi suy luận lỗi.

## Kiểm tra chất lượng ảnh

`POST /api/v1/disease/precheck` trả `passed`, `reasons`, `width`, `height`, `sharpness`, `brightness`.
Precheck và predict dùng chung `assess_image` (chất lượng trước, crop classifier sau); precheck không chạy detector.
Backend bỏ EXIF/GPS, kiểm lại khi nộp và chỉ lưu ảnh đạt chuẩn.

| Điều kiện | Ngưỡng |
|---|---|
| Định dạng, độ phân giải | JPEG/PNG, tối đa 40 megapixel |
| Kích thước | Mỗi chiều từ 160px |
| Độ sáng trung bình | 20–245 |
| Độ nét | `sharpness >= 20`, hoặc ảnh nền đơn sắc có cạnh sắc (đoạn dưới) |

Độ nét tính trên ảnh xám tối đa 640px: bỏ 10% viền, làm mượt Gaussian 5×5, lấy trung vị phương sai Laplacian của 16 vùng, trừ nhiễu ước lượng bằng MAD.
Ảnh nền đơn sắc (từ 6 trong 16 vùng gần như không có cạnh) được xét thêm theo tỉ lệ năng lượng cạnh mất đi khi làm mờ lại.
Phép đo dùng Gaussian 1 px trên ảnh 640px và chỉ tính cạnh trên 14.
Ảnh đạt khi trung vị tỉ lệ mất của các vùng có ít nhất 300 điểm cạnh từ 0.22.
Nhiễu hạt và sọc làm vùng nào cũng có cạnh nên không được coi là nền đơn sắc.
Chưa tách vùng lá.
Ngưỡng mới thử trên ảnh công khai nền ruộng, nền trắng và ảnh làm mờ nhân tạo.

## Phân loại lúa

- Artifact [YOLO26s-cls](https://huggingface.co/RaiyanKhaan/KrishokTech-Models/tree/c339988a6eceb5e22943ee27e4731a1a1dc9160a/01_crop_classifier) (10 lớp, `Rice=8`) ở `ai/weights/crop_classifier.onnx`, quản lý bằng Git; ONNX Runtime 1.30.0.
- `CROP_CLASSIFIER_PATH` mặc định `ai/weights/crop_classifier.onnx`; Compose dùng `AI_CROP_CLASSIFIER_PATH`.
  Khi nạp kiểm file, task, mapping, shape, dtype; không đối chiếu SHA-256 nên model khác cùng contract vẫn nạp được.
- Tiền xử lý: RGB, sửa hướng EXIF, resize cạnh ngắn 224 (bilinear), center crop 224, float32 /255.
- Cho qua khi top1 là Rice, P(Rice) ≥ 0.80 và chênh lớp khác cao nhất ≥ 0.20; độc lập với ngưỡng bệnh 0.70.
- `passed` là kết quả chất lượng **và** classifier; ảnh mới lưu `quality_passed` tổng hợp, ảnh cũ không backfill.
- Precheck không đạt: 200 `passed=false`; predict/diagnose: 422, không gọi model bệnh, Backend facts, RAG, LLM. Ảnh hỏng: 400 (diagnose 422). Classifier lỗi: 503.
- Health `model_loaded` chỉ true khi cả hai model sẵn sàng.

**Giới hạn:** model nhận ngô và mía thành Rice với điểm rất cao (6/15 và 15/15 ảnh thử) và chặn 8/50 ảnh lúa nền ruộng.
Tăng ngưỡng không sửa được ca gần 100%; số liệu ở `docs/AI_EVALUATION.md`.
Việc chưa làm ở [`docs/BACKLOG.md`](../../../docs/BACKLOG.md).
