# AI Service (Python — FastAPI & LangGraph)

Dịch vụ AI độc lập nằm trong thư mục `ai/` (ngang hàng `backend/`, `frontend/`, `bi/`, `chat/`, `portal/`), đóng vai trò không gian **Intelligence [I]** trong kiến trúc 4 không gian H-P-D-I của hệ thống DX-Farm.

Dịch vụ tuân thủ nguyên tắc kiến trúc bất biến: **Stateless & Advisory** — chỉ cung cấp chẩn đoán thị giác máy tính (CV), tra cứu tri thức định hình, truy vấn RAG giải thích và sinh đề xuất khuyến nghị. Dịch vụ không tự ghi nhận hay thay đổi dữ liệu nghiệp vụ chính thức; mọi khuyến nghị đều qua trạng thái `PENDING` chờ kỹ sư nông nghiệp phê duyệt (Human-in-the-loop).

---

## Kiến trúc & Các thành phần chính

1. **Vision API (Computer Vision)**:
   - Sử dụng mô hình `EfficientNet-B4` được huấn luyện trên 38 lớp bệnh cây trồng (PlantVillage).
   - Trả về mã bệnh chuẩn `disease_code` (như `Corn_(maize)__Common_rust` hay `Tomato___Early_blight`), độ tin cậy `confidence` và tên bệnh tiếng Việt `disease_name` khớp 1:1 với cơ sở dữ liệu `Disease.code` ở backend.

2. **Tra cứu quy tắc định hình (Structured Knowledge)**:
   - Truy vấn SQL xác định thông tin quy trình điều trị (`TreatmentRule`: danh mục thuốc được phép, hoạt chất, khoảng liều lượng min-max, đơn vị liều, số ngày cách ly PHI) hoặc chính sách quản lý không hóa chất (`DiseaseManagementPolicy`).
   - Tuyệt đối tuân thủ nguyên tắc **Deterministic-first**: số liệu thuốc và PHI lấy trực tiếp từ database, không cho phép LLM tự bịa số liệu.

3. **Truy vấn tài liệu RAG (Unstructured Knowledge)**:
   - Tìm kiếm vector trên pgvector sử dụng mô hình embedding `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`.
   - Trả về các đoạn tài liệu giải thích từ các tài liệu VietGAP/IRRI đã ingest kèm trích nguồn (`source_ids`, `similarity`).

4. **Điều phối Workflow (LangGraph)**:
   - Luồng xương sống 9 node: `resolve_request` $\rightarrow$ `cv_diagnosis` $\rightarrow$ `load_field_context` $\rightarrow$ `treatment_lookup` $\rightarrow$ `retrieve_knowledge` $\rightarrow$ `generate_recommendation` $\rightarrow$ `engineer_approval`.
   - Lưu trữ trạng thái duyệt bền vững trong bảng `WorkflowCase` và `WorkflowEvent` của PostgreSQL.

---

## Danh sách API chính (`/api/v1/disease`)

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/health` | Kiểm tra trạng thái mô hình CV (đã load hay chưa, device GPU/CPU) |
| `GET` | `/class-contract` | Lấy danh sách contract 38 lớp bệnh ánh xạ CV $\leftrightarrow$ DB |
| `POST` | `/predict` | Nhận diện bệnh từ ảnh lá cây qua CV |
| `POST` | `/diagnose-and-retrieve` | **API nối luồng**: Nhận ảnh $\rightarrow$ CV nhận diện bệnh $\rightarrow$ Tự động tra cứu tri thức điều trị (`/{disease_code}/treatment`) + RAG giải thích (`top_k=1`) |
| `POST` | `/diagnose-and-retrieve/pdf` | **API xuất báo cáo PDF**: Nhận ảnh $\rightarrow$ Chẩn đoán & Tra cứu $\rightarrow$ Xuất file báo cáo PDF có cấu trúc rõ ràng tường minh (`application/pdf`) |

| `GET` | `/{disease_code}/treatment` | Tra cứu quy trình điều trị & chính sách theo mã bệnh định hình |
| `POST` | `/knowledge/retrieve` | Truy vấn các đoạn tài liệu RAG trong pgvector theo câu hỏi & mã bệnh |
| `POST` | `/workflow/run` | Chạy toàn bộ LangGraph workflow tạo khuyến nghị dừng ở trạng thái `PENDING` |
| `GET` | `/workflow/{case_id}` | Xem trạng thái workflow và lịch sử audit case |
| `POST` | `/workflow/{case_id}/approve` | Kỹ sư duyệt khuyến nghị (`APPROVED`) |
| `POST` | `/workflow/{case_id}/reject` | Kỹ sư từ chối khuyến nghị (`REJECTED`) |
| `POST` | `/workflow/{case_id}/applied` | Xác nhận đã phun thuốc thực tế (`APPLIED`), tính ngày hết cách ly PHI |

---

## Biến môi trường (`.env`)

Dịch vụ AI sử dụng cấu hình từ file `.env` (mẫu tại `.env.example`). Các biến môi trường chính gồm:

| Biến môi trường | Giá trị mặc định | Giải thích |
|---|---|---|
| `DATABASE_URL` | `postgresql+psycopg://dxfarm:dxfarm@localhost:5432/dxfarm` | Kết nối PostgreSQL (Operational & Knowledge DB) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Endpoint của dịch vụ Ollama local |
| `OLLAMA_MODEL` | `qwen2.5:1.5b` | Mô hình LLM nhỏ dùng để diễn đạt khuyến nghị tiếng Việt |
| `EMBEDDING_MODEL` | `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` | Mô hình embedding cho RAG |
| `CV_MODEL_NAME` | `EfficientNet-B4` | Tên mô hình nhận diện bệnh |
| `CV_CONFIDENCE_THRESHOLD` | `0.70` | Ngưỡng độ tin cậy cảnh báo low confidence |
| `LLM_TIMEOUT_SECONDS` | `5` | Thời gian chờ tối đa khi gọi LLM (tự fallback nếu quá giờ) |

---

## Hướng dẫn cài đặt & Chạy cục bộ (Local Development)

### 1. Khởi tạo môi trường ảo Python & Cài thư viện

```bash
cd ai
python -m venv .venv
# Trên Windows:
.venv\Scripts\\activate
# Trên Linux/macOS:
source .venv/bin/activate

# Cài heavy deps trước (torch, torchvision, pymupdf)
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements-heavy.txt
# Sau đó cài app deps
pip install -r requirements.txt
```

### 2. Thiết lập cơ sở dữ liệu (Migration & Seed)

```bash
# Chạy migration Alembic
alembic upgrade head

# Seed danh mục bệnh và nạp dữ liệu tri thức RAG
python -m scripts.seed_diseases
python -m scripts.ingest_diseases
```

### 3. Khởi chạy FastAPI Server

```bash
uvicorn src.main:app --reload --port 8000
```
Swagger UI xem và test API tại: `http://localhost:8000/docs`

### 4. Chạy Test Suite

```bash
pytest
```

---

## Triển khai Docker

### Khởi chạy toàn bộ hệ thống

```bash
cd deploy
docker compose up -d
```

### Build & restart chỉ AI service

```bash
docker compose up -d --build ai
```

### ⚠️ Lưu ý về thời gian build

Build image lần **đầu tiên** sẽ mất **15–25 phút** do phải tải các package nặng:

| Package | Kích thước | Ghi chú |
|---|---|---|
| `torch` (CPU) | ~196 MB | Bắt buộc cho CV model inference |
| `torchvision` | ~1.7 MB | Transform ảnh đầu vào |
| `pymupdf` | ~25.8 MB | Xuất báo cáo PDF |
| App deps còn lại | ~50 MB | FastAPI, LangGraph, SQLAlchemy... |

Từ **lần thứ 2 trở đi**, nếu chỉ sửa code Python hoặc `requirements.txt`:
- Layer `torch` + `torchvision` + `pymupdf` → **cache hit**, không tải lại
- Chỉ install lại app deps nhẹ → build **< 5 phút**

### Cấu trúc requirements (2 file tách biệt)

```
requirements-heavy.txt  ← torch, torchvision, pymupdf  (cache vĩnh viễn)
requirements.txt        ← app deps nhẹ                 (rebuild khi cần)
```

> **Nguyên tắc**: Chỉ chỉnh `requirements-heavy.txt` khi **thực sự cần** đổi version torch/pymupdf. Mọi dep mới thêm vào `requirements.txt`.

### Dọn dẹp cache Docker (khi ổ cứng đầy)

```bash
# Xóa image dangling
docker image prune -f
# Xóa toàn bộ build cache (~16GB tiết kiệm được)
docker builder prune -f
# Xóa container đã dừng
docker container prune -f
```

> ⚠️ Sau `docker builder prune -f`, lần build tiếp theo sẽ lại mất 15–25 phút để tải lại torch. Chỉ chạy khi thực sự cần giải phóng không gian.

---

## Giới hạn hiện tại & Hướng phát triển tiếp theo

- Các endpoint AI hiện chưa yêu cầu xác thực JWT riêng; xác thực người dùng được kiểm soát ở gateway Spring Boot backend và Keycloak SSO.
- Khi triển khai Docker Compose toàn hệ thống, cấu hình biến môi trường của AI Service được hợp nhất chung tại [`deploy/.env`](../deploy/.env).
- `HF_TOKEN` chưa được cấu hình — HuggingFace Hub sẽ tải model với rate limit ẩn danh (thường đủ dùng, nhưng nếu bị throttle thì set biến `HF_TOKEN` trong `deploy/.env`).

