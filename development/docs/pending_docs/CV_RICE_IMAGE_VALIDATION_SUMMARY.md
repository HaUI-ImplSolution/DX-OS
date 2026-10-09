# CV Rice — Tóm tắt triển khai và kiểm chứng

*Cập nhật: 2026-10-08.*

## 1. Đã triển khai

- Classifier YOLO26s-cls kiểm ảnh lúa trước detector bệnh YOLOv8n `3.1.0`.
- Hai artifact được quản lý bằng Git tại `ai/weights/`: `crop_classifier.onnx` và `best.pt`. Docker mount read-only, nạp một lần khi AI Service khởi động; E2E không tải classifier.
- Đã xóa script download, bước download trong workflow và SHA-256/config tương ứng của classifier. Classifier vẫn kiểm file không rỗng, task, mapping, shape, dtype và output; detector bệnh giữ SHA-256 theo nhánh cha.

## 2. Luồng xử lý và vị trí code

**Ảnh → kiểm chất lượng → phân loại lúa → nhận diện bệnh → Backend facts/RAG → kỹ sư duyệt.**
Precheck dừng sau phân loại lúa. Backend kiểm lại trước lưu báo cáo/ảnh; offline được kiểm khi đồng bộ, retake giữ liên kết ảnh cũ/mới và idempotency.

| Thành phần | Vị trí | Vai trò |
|---|---|---|
| Nhận diện lúa | `ai/app/cv/crop_classifier.py` | ONNX Runtime CPU; RGB, resize cạnh ngắn 224, center crop, /255; kiểm điều kiện Rice |
| Kiểm ảnh dùng chung | `ai/app/services/plant_disease_service.py` | `assess_image` dùng cho precheck và predict; diagnose kiểm trước facts/RAG |
| Khởi động và API | `ai/app/core/lifespan.py`, `ai/app/api/router.py` | Nạp hai model; health chỉ sẵn sàng khi cả hai đã nạp |
| Backend/Mobile | `DiseaseReportService`, `mobile/app/capture.tsx` | Kiểm trước lưu, hiển thị lý do và chặn gửi ảnh không đạt |
| Triển khai/E2E | `deploy/docker-compose.yml`, `.github/workflows/e2e.yml` | Dùng artifact từ checkout, không có bước download classifier |
| Test hồi quy | `ai/tests/test_crop_classifier.py` | Kiểm loader, policy, precheck/predict/diagnose và lỗi dịch vụ |

Ngưỡng: Rice ≥ **0.80**, top1 là Rice, chênh lớp khác ≥ **0.20**; confidence bệnh trong code **0.70**. Ngưỡng bệnh từng hạ riêng xuống 0.00 trong local để thử; E2E/container không mạng lần kiểm mới dùng code 0.70.
Precheck trả 200 với `passed=false` khi ảnh không đạt; predict/diagnose hoặc nộp báo cáo bị chặn trả 422. Classifier lỗi trả 503, không báo nhầm ảnh không phải lúa.

## 3. Kết quả kiểm chứng mới nhất

| Kiểm tra | Kết quả |
|---|---|
| AI, Ruff, pip check | **64 test qua**, lint và dependency check qua |
| Backend `verify` | **79 test qua**, không bỏ qua |
| E2E local | **10/10 bước qua**: role, duyệt, idempotency, ảnh trắng bị chặn, chẩn đoán và PHI |
| Container không mạng | Hai lần nạp/giải phóng model và suy luận thành công; nội dung trọng số không đổi; file thiếu/rỗng/hỏng bị từ chối |
| Kiểm ảnh qua Backend | Lúa mẫu đạt; ảnh người đủ nét bị gate cây trồng chặn; gửi trực tiếp trả 422, không thêm báo cáo |

E2E dùng stack riêng từ snapshot các file Git có ONNX thật, images runtime có sẵn và image AI lắp mã mới. Build toàn stack từ đầu/CI GitHub cho thay đổi này chưa được kiểm chứng. Người dùng đã thử app và phản hồi “có vẻ ổn”; chưa có kết quả riêng cho toàn bộ kịch bản thực địa.

## 4. Giới hạn và dọn dẹp

- Model vẫn nhận nhầm ngô/cỏ/lúa mì: lần thử 15 ảnh có 3 ảnh ngoài lúa qua classifier, 2 ảnh còn qua chất lượng. Chuyển cách lưu trọng số không cải thiện độ chính xác; tăng threshold không sửa ca Rice gần 100%.
- Bỏ SHA-256 classifier cho phép trọng số khác cùng contract được nạp. Phiên bản artifact được ghi trong tài liệu model; nguồn tại [CV README](../../../ai/app/cv/README.md#phân-loại-lúa-trước-chẩn-đoán).
- Đã dọn plan tạm, ảnh tải thử, checkpoint PyTorch đối chiếu, stack E2E thử, bản sao build Backend và log thử. Trang classifier riêng `crop-test/`, container/image tester đã xóa; cổng 39083 không còn trang thử.
- Giữ artifact sản phẩm, test hồi quy, tài liệu module và cấu hình/dữ liệu Docker local cần dùng. `backend-image/app.jar` còn cần cho build local; cấu hình/dữ liệu local được ignore. README tổng giữ nguyên.

## 5. Rà soát sau thay đổi

- Fetch mới nhất: nhánh cha có commit tài liệu `482b905` chưa merge vào nhánh này. Mô phỏng merge phần đã commit và kiểm áp dụng patch cha lên working tree hiện tại đều không phát hiện conflict.
- Rà skill AI: dùng loader/contract hiện có, không thêm dependency cho đợt bỏ download/SHA; giữ kiểm input/output và test hồi quy. Giới hạn chấp nhận model khác cùng contract có comment `ponytail:` tại loader.
- Script download, plan tạm, trang thử riêng và bản sao build/log thử đã dọn. Các file còn lại phục vụ sản phẩm, kiểm hồi quy hoặc cấu hình/dữ liệu local đang dùng. README tổng không sửa.

## 6. Cung cấp trọng số cùng repository

Theo quyết định cuối, `best.pt` và `crop_classifier.onnx` được đưa vào Git, bỏ ignore trọng số trong `ai/weights/`; cache tải vẫn được ignore. Clone/pull có đủ hai artifact cho bước tạo cấu hình và dựng stack. E2E/startup nạp file local, không tải model; classifier không kiểm SHA-256. Lỗi CI trước đó ở bước tạo `.env` do thiếu `best.pt` được xử lý bằng việc khôi phục artifact trong checkout.
