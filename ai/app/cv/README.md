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
  → kiểm tra kích thước tối thiểu và độ sắc nét
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
  "model_version": "3.0.0"
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

- Model được nạp một lần khi FastAPI khởi động, lưu tại `app.state.plant_model` và giải phóng khi service dừng.
- Weights phải có sẵn tại `CV_WEIGHTS_PATH`; service không tự tải checkpoint. Khi chạy Compose, `best.pt` được bind-mount read-only.
- `CV_DEVICE=auto` ưu tiên CUDA khi có GPU, nếu không chạy CPU. Có thể đặt rõ `cpu` hoặc `cuda`.
- Checkpoint phải có SHA-256, task `detect` và thứ tự class khớp model profile/class contract thì mới được nạp.
- Confidence nằm trong khoảng `0–1`. Workflow xem confidence thấp hơn `WORKFLOW_CONFIDENCE_THRESHOLD` — hiện là `0.70` — là chưa đủ tin cậy và yêu cầu chụp lại ảnh trước khi truy vấn Backend facts, RAG hoặc LLM.
- API giới hạn tệp upload là ảnh và tối đa 10 MiB. Ảnh quá nhỏ, thiếu độ nét, không đọc được hoặc output model không hợp lệ sẽ bị từ chối.

## Giới hạn dữ liệu

Checkpoint chỉ phát hiện 5 lớp bệnh lá lúa có trong `class_contract.json`: đạo ôn, đốm nâu, than lá, tungro và khô vằn. Model không có class healthy/non-leaf riêng; `Rice___healthy` là fallback khi không có bounding box đạt ngưỡng, không phải khẳng định toàn cây hoặc ruộng hoàn toàn khỏe.

Chất lượng kết quả ngoài điều kiện ảnh lá lúa hoặc ngoài năm lớp bệnh đã huấn luyện không được đảm bảo.

Kết quả CV là tín hiệu chẩn đoán, không phải chỉ định canh tác. Các ràng buộc nghiệp vụ, thuốc, liều lượng, PHI và phê duyệt của kỹ sư luôn được xử lý ngoài module CV.
