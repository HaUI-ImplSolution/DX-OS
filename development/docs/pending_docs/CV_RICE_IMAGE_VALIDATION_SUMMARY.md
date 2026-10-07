# CV Rice — Tóm tắt triển khai và kiểm chứng

## 1. Đã làm gì

Tích hợp crop classifier YOLO26s-cls vào bước kiểm ảnh trước chẩn đoán; thay checkpoint bệnh YOLOv8n bằng bản `3.1.0` do người dùng cung cấp.
Backend kiểm lại ảnh trước lưu báo cáo, kể cả client bỏ qua precheck. Mobile hiển thị lý do chất lượng ảnh hoặc chưa nhận diện là lúa; offline và retake vẫn theo luồng hiện có.

## 2. Xử lý như thế nào, ở đâu

Luồng: **Ảnh → kiểm kích thước/độ sáng/độ nét → phân loại lúa → nhận diện bệnh → Backend facts/RAG → kỹ sư duyệt.**
Precheck dừng sau phân loại lúa; không chạy detector bệnh.

| Thành phần | Vị trí | Vai trò |
|---|---|---|
| Crop classifier | `ai/app/cv/crop_classifier.py` | ONNX Runtime CPU; RGB, resize cạnh ngắn 224, center crop, /255; kiểm output và điều kiện Rice |
| Kiểm ảnh dùng chung | `ai/app/services/plant_disease_service.py` | `assess_image` dùng cho precheck và predict; diagnose gọi predict trước facts/RAG |
| Nạp model/API | `ai/app/core/lifespan.py`, `ai/app/api/router.py` | Nạp hai model một lần, health phản ánh cả hai; phân biệt ảnh không đạt và lỗi dịch vụ |
| Backend/Mobile | `DiseaseReportService`, `mobile/app/capture.tsx` | Server kiểm trước lưu; app hiển thị lý do và chặn gửi ảnh không đạt |
| Cung cấp artifact | `ai/scripts/download_crop_classifier.py`, `deploy/docker-compose.yml` | Tải revision/checksum cố định trước triển khai; mount ONNX read-only |
| Test hồi quy | `ai/tests/test_crop_classifier.py` | Kiểm policy, chất lượng trước crop, precheck/predict/diagnose, lỗi 422/503 và lifespan |

Cho qua khi top1 là Rice, P(Rice) ≥ **0.80**, chênh với lớp khác cao nhất ≥ **0.20**. Confidence bệnh trong code là **0.70**; local từng được hạ riêng xuống **0.00** để thử theo yêu cầu.
Precheck trả 200 với `passed=false` khi ảnh không đạt; predict/diagnose và nộp báo cáo bị gate chặn trả 422. Classifier lỗi trả 503, không coi là ảnh không phải lúa.

**Tải lại:** Docker không gọi script tải. Khi chủ động chạy script, file đúng SHA-256 được giữ nguyên; thiếu/sai file thì tải vào file tạm, kiểm hash rồi mới thay thế. Tải lỗi không ghi đè file hiện có; file tạm được dọn. E2E CI gọi script trước khi dựng stack; service không tải model lúc khởi động.

## 3. Kết quả đã kiểm chứng — 2026-10-07

- **61 test AI**, Ruff và pip check qua; **79 test Backend**, không bỏ qua test; Mobile typecheck/export Android và Web build qua.
- Chạy script tải hai lần với truy cập mạng bị chặn: file model giữ nguyên inode, thời gian sửa và SHA-256; không tải lại.
- Compose template hợp lệ; thiếu secret bắt buộc bị từ chối. Backend, AI và storage local đều `UP` lúc kiểm tra.
- Qua Backend: ảnh lúa mẫu đạt; ảnh người đủ nét bị từ chối với lý do chưa nhận diện là lúa. Gửi thẳng ảnh người trả 422, số báo cáo không đổi; test Backend xác nhận không gọi lưu ảnh.
- Người dùng thử trên app và phản hồi “có vẻ ổn”; chưa có kết quả riêng từng kịch bản để kết luận toàn bộ ảnh thực địa đạt.
- Nhánh `tung/cv-rice-image-validation` đã push tại `ffe0622`, dựa trên nhánh cha `4fb1169`. CI GitHub chưa được kích hoạt tại lần kiểm tra sau push vì chưa có PR.

## 4. Kiểm lại và giới hạn

| Kịch bản | Kết quả cần kiểm tra |
|---|---|
| Lá lúa rõ, đủ sáng | Qua precheck; gửi được báo cáo |
| Người/đồ vật hoặc ảnh mờ/tối | Bị từ chối với đúng lý do; không tạo báo cáo |
| Ngô/cỏ/lúa mì | Ghi nhận ca lọt; model hiện còn nhận nhầm |
| Offline, chụp lại, chạy AI lại | Kiểm khi đồng bộ; không tạo trùng, giữ liên kết retake và chẩn đoán cũ khi bị từ chối |

Model chưa giải quyết hoàn toàn ảnh ngoài miền: trong 15 ảnh thử trước tích hợp, classifier nhận nhầm 3 ảnh ngoài lúa; 2 ảnh còn qua cả chất lượng (ngô/lúa mì). Đối chiếu ONNX/PyTorch trên cùng tensor cho cùng top1, sai lệch xác suất < `5e-7` ở 5 ảnh. Tăng threshold không sửa các ca Rice gần 100%.
Ảnh tải thử và checkpoint PyTorch đối chiếu đã xóa; ONNX đang dùng được ignore. Chi tiết hợp đồng/model tại [CV README](../../../ai/app/cv/README.md#phân-loại-lúa-trước-chẩn-đoán).
