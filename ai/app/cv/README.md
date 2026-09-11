# CV handoff — Stage 6

Input: ảnh JPEG/PNG qua multipart field `image`, tối đa 10 MiB.
Output: `disease_code`, `disease_name`, `confidence`, `model_name`, `model_version`.

- `POST /api/v1/disease/predict`: inference, không ghi DB.
- `GET /api/v1/disease/health`: trả trạng thái model_loaded và device.
- `GET /api/v1/disease/class-contract`: contract 38 lớp.
- `POST /api/v1/disease/reports/{report_id}/diagnose`: inference cho DiseaseReport đã tồn tại, lưu Diagnosis và trạng thái ANALYZED trong một transaction rồi trả structured rules hoặc safe policy.
  Báo cáo đã có diagnosis trả 409.

Contract được lưu ở `ai/data/structured/class_contract.json`.
Thứ tự logits giữ theo `CLASS_NAMES` và `preprocessor.CLASSES`; adapter chỉ đổi nhãn model common rust `Corn_(maize)___Common_rust_` thành mã Disease nguồn `Corn_(maize)__Common_rust`.
Không sửa dữ liệu nguồn, không fuzzy match hoặc dùng tên hiển thị để lookup DB.

Weights: `Khawajaa/plant-disease-detector/best_model.pth`, cache tại `ai/weights` (gitignored).
Loader kiểm tra shape/state_dict, image size 224 và số lớp 38.
Checkpoint hiện không lưu class_to_idx; thứ tự nhãn được đối chiếu với mã training trong repo, chưa phải chứng nhận accuracy trên tập validation độc lập.

Từ thư mục `ai`, dùng venv đã cài requirements:

```bash
python -m alembic upgrade head
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

Nếu tải weights qua Xet lỗi, chạy với `HF_HUB_DISABLE_XET=1`.
Thiết lập `CV_DEVICE=cpu` khi cần ép CPU.
Ảnh hỏng trả 400, thiếu model trả 503, ảnh quá lớn trả 413; confidence nằm trong [0,1].
Không tự áp dụng thuốc hoặc chọn threshold nghiệp vụ khi chưa có kết quả validation model.
