# Computer Vision — Nhận diện bệnh cây

## Vai trò của thư mục này

`app/cv/` là phần lõi Computer Vision của AI Service. Thư mục này định nghĩa mô hình, danh sách lớp bệnh, cách nạp checkpoint và tiền xử lý ảnh để nhận diện bệnh từ ảnh lá cây.

Nó không phải một service độc lập và không tự mở API. API FastAPI, workflow và các service khác gọi thành phần trong thư mục này để lấy kết quả chẩn đoán ban đầu.

Kết quả quan trọng nhất của CV là `disease_code` chuẩn. Mã này là cầu nối từ ảnh cây trồng sang dữ liệu bệnh, quy tắc điều trị và kiến thức RAG ở các phần khác của hệ thống.

## Phạm vi

CV chịu trách nhiệm:

- Nạp checkpoint EfficientNet-B4 và suy luận một ảnh lá cây.
- Phân loại ảnh vào một trong 38 lớp bệnh/cây trồng cố định.
- Trả về mã bệnh chuẩn, tên bệnh tiếng Việt, confidence, tên model và phiên bản model.
- Kiểm tra checkpoint khớp số lớp, kích thước ảnh và thứ tự class trước khi cho model chạy.

CV không chịu trách nhiệm:

- Không quyết định thuốc, liều lượng, thời gian cách ly (PHI) hoặc thao tác áp dụng thuốc.
- Không tự truy vấn RAG, gọi LLM hay sinh khuyến nghị.
- Không tự ghi chẩn đoán vào PostgreSQL; việc lưu diagnosis do service/workflow bên ngoài thực hiện.
- Không kết luận rằng cây hoàn toàn khỏe hoặc không có bệnh ngoài 38 lớp đã huấn luyện.

## Cách hoạt động

```text
Ảnh lá cây
  → API kiểm tra đây là ảnh và giới hạn dung lượng
  → PlantDiseaseService đọc ảnh RGB
  → fit ảnh về 224 × 224, giữ tỷ lệ trước khi crop
  → chuyển tensor và chuẩn hóa theo ImageNet
  → EfficientNet-B4 suy luận ở chế độ evaluation
  → softmax chọn lớp có xác suất cao nhất
  → chuẩn hóa model label thành disease_code
  → trả kết quả cho API hoặc workflow
```

Ví dụ kết quả:

```json
{
  "disease_code": "Tomato___Early_blight",
  "disease_name": "Bệnh cháy lá sớm trên cà chua",
  "confidence": 0.932,
  "model_name": "EfficientNet-B4",
  "model_version": "v1.0"
}
```

`disease_code` phải khớp chính xác với `Disease.code` trong PostgreSQL. Không có fuzzy mapping theo tên bệnh. Một ngoại lệ mapping được giữ riêng để sửa khác biệt nhãn `Corn_(maize)___Common_rust_` của model và mã canonical trong dữ liệu.

## Thành phần

| Thành phần | Vai trò |
|---|---|
| `classifier.py` | Khai báo EfficientNet-B4 và classification head cho 38 lớp. |
| `loader.py` | Tải checkpoint từ Hugging Face nếu weights chưa có, kiểm tra tính tương thích và chọn CPU/CUDA. |
| `preprocessor.py` | Chuẩn bị ảnh đầu vào; đồng thời chứa utility dataset/transform phục vụ phần dữ liệu và huấn luyện. |
| `class_names.py` | Danh sách 38 class cố định và tên bệnh tiếng Việt. |
| `contract.py` | Chuyển nhãn model thành `disease_code` canonical và công bố class contract. |
| `app/services/plant_disease_service.py` | Service bên ngoài thư mục `cv/` điều phối tiền xử lý, suy luận và định dạng kết quả trả về. |

## Đặc điểm vận hành

- Model được nạp một lần khi FastAPI khởi động, lưu tại `app.state.plant_model` và giải phóng khi service dừng.
- Weights mặc định lấy từ Hugging Face khi chưa tồn tại ở `ai/weights/`; các lần chạy sau tái sử dụng cache này.
- `CV_DEVICE=auto` ưu tiên CUDA khi có GPU, nếu không chạy CPU. Có thể đặt rõ `cpu` hoặc `cuda`.
- Model chạy `eval()` và `torch.no_grad()` khi suy luận để không cập nhật trọng số.
- Confidence nằm trong khoảng `0–1`. Workflow xem confidence thấp hơn `CV_CONFIDENCE_THRESHOLD` — mặc định `0.70` — là low-confidence và yêu cầu xác nhận rõ ràng trước khi áp dụng hành động liên quan.
- API giới hạn tệp upload là ảnh và tối đa 10 MiB. Ảnh không đọc được hoặc output model không hợp lệ sẽ bị từ chối.

## Giới hạn dữ liệu

Model chỉ nhận diện 38 lớp PlantVillage có trong `class_names.py`. Chất lượng kết quả ngoài điều kiện ảnh lá, cây trồng hoặc bệnh thuộc tập lớp này không được đảm bảo.

Kết quả CV là tín hiệu chẩn đoán, không phải chỉ định canh tác. Các ràng buộc nghiệp vụ, thuốc, liều lượng, PHI và phê duyệt của kỹ sư luôn được xử lý ngoài module CV.
