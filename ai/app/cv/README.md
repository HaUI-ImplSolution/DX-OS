# Computer Vision — Nhận diện bệnh lá lúa

`app/cv/` nạp checkpoint YOLOv8n cục bộ và phát hiện năm bệnh lá lúa. Mapping class chỉ được đọc từ `data/structured/class_contract.json`; healthy là fallback khi không có box đạt ngưỡng.

```text
Ảnh upload → Pillow decode/RGB → YOLOv8n (conf 0.25, IoU 0.45)
→ chọn detection có confidence cao nhất → class contract → response 5 trường
```

Response giữ nguyên `disease_code`, `disease_name`, `confidence`, `model_name`, `model_version`. Bounding box và danh sách detection chỉ được xử lý nội bộ.

`loader.py` yêu cầu checkpoint tồn tại tại `CV_WEIGHTS_PATH`, kiểm tra SHA-256 theo `model_profile.py`, task `detect`, số lớp và đúng thứ tự class. Số lớp được suy ra trực tiếp từ class contract thay vì cấu hình lặp lại. Service không tự tải checkpoint. Khi chạy Compose, `best.pt` được bind-mount read-only.

Các thuộc tính cố định của artifact nằm trong `model_profile.py`; ngưỡng inference và kiểm tra chất lượng ảnh nằm trong `policy.py`, không phải biến môi trường. Detection confidence lọc box YOLO; workflow confidence gate yêu cầu chụp lại và không chạy Backend facts, RAG hoặc LLM nếu kết quả chưa đủ tin cậy. Minimum image dimension và sharpness chặn ảnh quá nhỏ hoặc thiếu độ nét trước inference.

Checkpoint chỉ có năm lớp bệnh, không có lớp healthy/non-leaf riêng. Vì vậy trường hợp không có detection được xem là chưa đủ tin cậy thay vì tự khẳng định lá khỏe; đây là lựa chọn fail-safe để ảnh ngoài miền không đi tiếp vào khuyến nghị.

CV không quyết định thuốc, liều lượng, PHI, truy vấn RAG hoặc ghi diagnosis. Chất lượng tích hợp kỹ thuật không thay thế đánh giá độ chính xác của model trên tập kiểm thử phù hợp.
