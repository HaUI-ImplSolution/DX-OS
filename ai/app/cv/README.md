# Computer Vision — Nhận diện bệnh lá lúa

## Vai trò của thư mục này

`app/cv/` là phần lõi Computer Vision của AI Service. Thư mục này định nghĩa hợp đồng lớp bệnh, thông tin model, cách nạp checkpoint và chính sách xử lý ảnh để nhận diện bệnh từ ảnh lá lúa.

Nó không phải một service độc lập và không tự mở API. API FastAPI, workflow và các service khác gọi thành phần trong thư mục này để lấy kết quả chẩn đoán ban đầu.

Kết quả quan trọng nhất của CV là `disease_code` chuẩn. Mã này là cầu nối từ ảnh lá lúa sang dữ liệu bệnh, quy tắc điều trị và kiến thức RAG ở các phần khác của hệ thống.

## Phạm vi

CV chịu trách nhiệm:

- Nạp checkpoint YOLOv8n cục bộ và suy luận một ảnh lá lúa.
- Phát hiện ảnh thuộc một trong 5 lớp bệnh lá lúa cố định; dùng `Rice___healthy` làm fallback khi không có detection đạt ngưỡng.
- Trả về mã bệnh chuẩn, tên bệnh tiếng Việt, confidence, tên model và phiên bản model.
- Kiểm tra checksum, task của checkpoint, số lớp và thứ tự class trước khi cho model chạy.

CV không chịu trách nhiệm:

- Không quyết định thuốc, liều lượng, thời gian cách ly (PHI) hoặc thao tác áp dụng thuốc.
- Không tự truy vấn RAG, gọi LLM hay sinh khuyến nghị.
- Không tự ghi chẩn đoán vào PostgreSQL; việc lưu diagnosis do service/workflow bên ngoài thực hiện.
- Không khẳng định cây hoàn toàn khỏe hoặc ảnh ngoài miền không có bệnh; fallback `Rice___healthy` chỉ cho biết không có detection nào đạt ngưỡng.

## Cách hoạt động

```text
Ảnh lá lúa
  → API kiểm tra đây là ảnh và giới hạn dung lượng
  → PlantDiseaseService đọc ảnh RGB
  → kiểm tra kích thước tối thiểu, độ sắc nét và độ sáng
  → crop classifier kiểm điều kiện Rice
  → YOLOv8n suy luận với kích thước ảnh 640
  → lọc bounding box theo confidence và IoU
  → chọn detection có confidence cao nhất
  → ánh xạ class ID qua class contract thành disease_code
  → trả kết quả cho API hoặc workflow
```

Ví dụ kết quả:

```json
{
  "disease_code": "Rice___Blast",
  "disease_name": "Bệnh đạo ôn trên lúa",
  "confidence": 0.932,
  "model_name": "YOLOv8n-RiceLeafBD",
  "model_version": "3.1.0"
}
```

`disease_code` phải khớp chính xác với `Disease.code` trong PostgreSQL. Không có fuzzy mapping theo tên bệnh. Ánh xạ duy nhất được đọc từ `data/structured/class_contract.json`.

## Thành phần

| Thành phần | Vai trò |
|---|---|
| `loader.py` | Nạp checkpoint YOLO cục bộ, kiểm tra checksum, task, class contract và chọn CPU/CUDA. |
| `contract.py` | Đọc, kiểm tra và tra cứu ánh xạ từ class của model sang `disease_code` canonical. |
| `model_profile.py` | Chứa metadata và thuộc tính cố định của checkpoint đã được xác thực. |
| `policy.py` | Chứa các ngưỡng inference, workflow và chất lượng ảnh đã được xác thực. |
| `triage.py` | Khai báo lỗi triage khi ảnh hoặc kết quả chưa đủ điều kiện đi tiếp. |
| `app/services/plant_disease_service.py` | Service bên ngoài thư mục `cv/` điều phối kiểm tra ảnh, suy luận và định dạng kết quả trả về. |

## Đặc điểm vận hành

Artifact hiện tại: YOLOv8n RiceLeafBD, phiên bản `3.1.0`, checkpoint huấn luyện 150 epochs ngày 2026-10-07.
SHA-256: `e5ae985cb87723f0b39c3833d8230ac48c13c2e075296b18faffc1302aa6debe`. Checkpoint nằm tại `ai/weights/best.pt`, được quản lý cùng mã nguồn theo nhánh cha.

- Model được nạp một lần khi FastAPI khởi động, lưu tại `app.state.plant_model` và giải phóng khi service dừng.
- Weights phải có sẵn tại `CV_WEIGHTS_PATH`; service không tự tải checkpoint. Khi chạy Compose, `best.pt` được bind-mount read-only.
- `CV_DEVICE=auto` ưu tiên CUDA khi có GPU, nếu không chạy CPU. Có thể đặt rõ `cpu` hoặc `cuda`.
- Checkpoint phải khớp `CV_WEIGHTS_SHA256`, task `detect` và thứ tự class trong class contract thì mới được nạp.
- Confidence nằm trong khoảng `0–1`. Workflow xem confidence thấp hơn `WORKFLOW_CONFIDENCE_THRESHOLD` — hiện là `0.70` — là chưa đủ tin cậy và yêu cầu chụp lại ảnh trước khi truy vấn Backend facts, RAG hoặc LLM.
- `POST /api/v1/disease/predict` yêu cầu header `X-Service-Api-Key` như mọi endpoint AI khác; chỉ `/health` và `/class-contract` công khai.
- API giới hạn tệp upload là ảnh và tối đa 10 MiB. Ảnh quá nhỏ, thiếu độ nét, không đọc được hoặc output model không hợp lệ sẽ bị từ chối.

## Giới hạn dữ liệu

Checkpoint chỉ phát hiện 5 lớp bệnh lá lúa có trong `class_contract.json`: đạo ôn, đốm nâu, than lá, tungro và khô vằn. Model không có class healthy/non-leaf riêng; `Rice___healthy` là fallback khi không có bounding box đạt ngưỡng, không phải khẳng định toàn cây hoặc ruộng hoàn toàn khỏe.

Chất lượng kết quả ngoài điều kiện ảnh lá lúa hoặc ngoài năm lớp bệnh đã huấn luyện không được đảm bảo.

Kết quả CV là tín hiệu chẩn đoán, không phải chỉ định canh tác. Các ràng buộc nghiệp vụ, thuốc, liều lượng, PHI và phê duyệt của kỹ sư luôn được xử lý ngoài module CV.

## Kiểm tra chất lượng ảnh

`POST /api/v1/disease/precheck` nhận multipart ảnh, yêu cầu `X-Service-Api-Key`; trả `passed`, `reasons`, `width`, `height`, `sharpness`, `brightness`.
Precheck và predict dùng chung `assess_image`: chất lượng trước, crop classifier sau; precheck không chạy detector bệnh.
Backend bỏ EXIF/GPS, kiểm lại khi nộp và chỉ lưu ảnh đạt chuẩn.
Ảnh kém chất lượng trả `passed=false`; dữ liệu ảnh không hợp lệ/định dạng không hỗ trợ trả 400.

| Điều kiện | Ngưỡng |
|---|---|
| Định dạng / độ phân giải | JPEG/PNG, tối đa 40 megapixel |
| Kích thước | Mỗi chiều từ 160px |
| Độ sáng trung bình | 20–245 |
| Độ nét | `sharpness >= 20` |

Điểm nét được tính trên ảnh xám thu về tối đa 640px, không phóng lớn; bỏ 10% viền và làm mượt Gaussian nhị thức 5×5.
Sau đó lấy trung vị phương sai Laplacian của 16 vùng, trừ phần nhiễu ước lượng bằng MAD của sai phân chéo 2×2.
Phép tính dùng số thực để tránh clipping 8-bit.

Kiểm chất lượng chưa tách vùng lá; nền sắc hoặc chủ thể ngoài trung tâm có thể gây sai đánh giá.
Ngưỡng 20 cần đối chiếu camera thực địa.
Workflow giữ ngưỡng confidence 0.70 và từ chối fallback confidence 0 trước khi gọi Backend facts/RAG.

## Phân loại lúa trước chẩn đoán

- `crop_classifier.py` dùng ONNX Runtime CPU 1.30.0; giữ Ultralytics 8.3.40 cho detector bệnh.
- [Artifact YOLO26s-cls](https://huggingface.co/RaiyanKhaan/KrishokTech-Models/tree/c339988a6eceb5e22943ee27e4731a1a1dc9160a/01_crop_classifier): 10 lớp, `Rice=8`, `Others=6`; revision và SHA-256 được cố định trong script tải.
- Tải trước khi chạy: `python3 ai/scripts/download_crop_classifier.py` từ gốc repo. ONNX được ignore, mount read-only; service không tải mạng và không bỏ qua gate khi thiếu/sai model.
- `CROP_CLASSIFIER_PATH` mặc định `ai/weights/crop_classifier.onnx`, `CROP_CLASSIFIER_SHA256` mặc định `b9c34acf76a3d8a04820d7f26c8318736478f54c568a655c09a73f55190837a5`. Compose tương ứng dùng `AI_CROP_CLASSIFIER_PATH` và `AI_CROP_CLASSIFIER_SHA256`.
- RGB, sửa hướng EXIF, resize cạnh ngắn 224 bằng bilinear, center crop 224, NCHW float32 /255 theo preprocessing Ultralytics. Output đã Softmax; không chuẩn hóa lần nữa.
- Cho qua khi top1 là Rice, P(Rice) ≥ 0.80 và chênh với lớp khác cao nhất ≥ 0.20. Confidence bệnh vẫn **0.70**, độc lập với classifier.
- `passed` là kết quả chất lượng **và** classifier; các số đo sharpness/brightness chỉ đo chất lượng. Ảnh mới lưu `quality_passed` theo kết quả tổng hợp, không backfill ảnh cũ.
- Precheck: HTTP 200 `passed=false` khi ảnh không đạt; predict/diagnose: 422 và không gọi detector/Backend facts/RAG/LLM. Ảnh hỏng: precheck/predict 400, diagnose 422. Classifier lỗi: 503; không báo ảnh không phải lúa.
- Backend kiểm lại trước ghi báo cáo/MinIO, kể cả bỏ qua precheck. Offline/retake dùng cùng gate khi gửi; replay idempotency trả báo cáo đã tồn tại.
- Hai model nạp một lần; health `model_loaded` chỉ true khi cả hai sẵn sàng. `model_name`/`model_version` vẫn mô tả detector bệnh.

**Giới hạn đã biết:** model nhận nhầm ngô, lúa mì và cỏ thành Rice với điểm rất cao. Tích hợp theo yêu cầu để thử trong sản phẩm; chưa nghiệm thu khả năng chặn mọi ảnh ngoài miền. Tăng threshold không sửa được các ca Rice gần 100%. Các mẫu thử, đối chiếu và kịch bản thủ công ở [test plan](../../../development/docs/pending_docs/CV_RICE_IMAGE_VALIDATION_TEST_PLAN.md).
