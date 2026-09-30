# Computer Vision — Nhận diện bệnh lá lúa

`app/cv/` nạp checkpoint YOLOv8n cục bộ và phát hiện năm bệnh lá lúa. Mapping class chỉ được đọc từ `data/structured/class_contract.json`; healthy là fallback khi không có box đạt ngưỡng.

```text
Ảnh upload → Pillow decode/RGB → YOLOv8n (conf 0.25, IoU 0.45)
→ chọn detection có confidence cao nhất → class contract → response 5 trường
```

Response giữ nguyên `disease_code`, `disease_name`, `confidence`, `model_name`, `model_version`. Bounding box và danh sách detection chỉ được xử lý nội bộ.

`loader.py` yêu cầu checkpoint tồn tại tại `CV_WEIGHTS_PATH`, kiểm tra SHA-256 khi cấu hình, task `detect`, số lớp và đúng thứ tự class. Service không tự tải checkpoint. Khi chạy Compose, `best.pt` được bind-mount read-only.

`CV_DETECTION_CONFIDENCE_THRESHOLD` là ngưỡng lọc box YOLO. `CV_CONFIDENCE_THRESHOLD` là confidence gate của workflow; kết quả thấp hơn ngưỡng sẽ yêu cầu chụp lại và không chạy Backend facts, RAG hoặc LLM. `CV_MIN_IMAGE_DIMENSION` và `CV_MIN_SHARPNESS` chặn ảnh quá nhỏ hoặc thiếu độ nét trước inference.

Checkpoint chỉ có năm lớp bệnh, không có lớp healthy/non-leaf riêng. Vì vậy trường hợp không có detection được xem là chưa đủ tin cậy thay vì tự khẳng định lá khỏe; đây là lựa chọn fail-safe để ảnh ngoài miền không đi tiếp vào khuyến nghị.

CV không quyết định thuốc, liều lượng, PHI, truy vấn RAG hoặc ghi diagnosis. Chất lượng tích hợp kỹ thuật không thay thế đánh giá độ chính xác của model trên tập kiểm thử phù hợp.
