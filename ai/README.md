# AI Service (Python — FastAPI & LangGraph)

Dịch vụ AI độc lập nằm trong thư mục `ai/` (ngang hàng `backend/`, `frontend/`, `bi/`, `chat/`, `portal/`), đóng vai trò không gian **Intelligence [I]** trong kiến trúc 4 không gian H-P-D-I của hệ thống DX-Farm.

Dịch vụ tuân thủ nguyên tắc kiến trúc bất biến: **Stateless & Advisory** — chỉ cung cấp chẩn đoán thị giác máy tính (CV), tra cứu tri thức định hình, truy vấn RAG giải thích và sinh đề xuất khuyến nghị. Dịch vụ không tự ghi nhận hay thay đổi dữ liệu nghiệp vụ chính thức; mọi khuyến nghị đều qua trạng thái `PENDING` chờ kỹ sư nông nghiệp phê duyệt (Human-in-the-loop).

> **Trạng thái kiến trúc**: Nội dung dưới đây mô tả hành vi hiện tại, nơi AI vẫn ghi trực tiếp `WorkflowCase`/`WorkflowEvent`/`DiseaseReport`/`Diagnosis`/`TreatmentApplication` vào cùng một PostgreSQL với Backend — khác với nguyên tắc Stateless & Advisory ở trên.
> Kế hoạch tách AI thành dịch vụ suy luận phi trạng thái thật sự (không ghi DB nghiệp vụ, chỉ đọc Backend qua REST GET) đã chốt ở [`../development/docs/ai_backend_decoupling_plan.md`](../development/docs/ai_backend_decoupling_plan.md); tài liệu này sẽ cập nhật lại khi Phase 1 của kế hoạch đó hoàn tất.

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
| `POST` | `/reports/{report_id}/diagnose` | Chẩn đoán ảnh cho báo cáo bệnh đã tồn tại và lưu kết quả |
| `POST` | `/knowledge/retrieve` | Truy vấn các đoạn tài liệu RAG trong pgvector theo câu hỏi & mã bệnh |
| `POST` | `/workflow/run` | Chạy toàn bộ LangGraph workflow tạo khuyến nghị dừng ở trạng thái `PENDING` |
| `GET` | `/workflow/{case_id}` | Xem trạng thái workflow và lịch sử audit case |
| `POST` | `/workflow/{case_id}/approve` | Kỹ sư duyệt khuyến nghị (`APPROVED`) |
| `POST` | `/workflow/{case_id}/reject` | Kỹ sư từ chối khuyến nghị (`REJECTED`) |
| `POST` | `/workflow/{case_id}/applied` | Xác nhận đã phun thuốc thực tế (`APPLIED`), tính ngày hết cách ly PHI |

Bảng trên phản ánh contract hiện tại.
Theo Phase 1 của [kế hoạch tách AI/Backend](../development/docs/ai_backend_decoupling_plan.md), `POST /reports/{report_id}/diagnose` và toàn bộ `/workflow/*` sẽ bị xóa, thay bằng contract suy luận phi trạng thái `POST /api/v1/diagnose`: nhận ảnh + field context và trả CV result + RAG evidence + structured facts + recommendation trong một lần gọi, không tự ghi DB nghiệp vụ.

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

pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt
```

### 2. Chuẩn bị database, seed và RAG

AI không dùng Alembic hay migration. PostgreSQL phải được khởi tạo sẵn theo **schema phiên bản hiện tại** của DX-Farm, bao gồm các bảng dùng bởi AI như `diseases`, `pesticides`, `treatment_rules`, `documents`, `document_chunks` và các bảng workflow. Service AI không tự tạo các bảng này.

Các file JSON trong `data/structured/` và `data/rag/` là **dữ liệu nguồn trong repository**, chưa tự xuất hiện trong PostgreSQL. Sau khi schema đã có, nạp seed và tạo embedding RAG:

```bash
python -m scripts.seed_diseases
python -m scripts.seed_pesticides
python -m scripts.seed_treatment_rules
python -m scripts.seed_disease_management_policies
python -m scripts.ingest_diseases
```

`scripts.ingest_diseases` đọc `data/rag/processed/disease_knowledge.json`, tạo embedding và ghi corpus vào PostgreSQL. Chỉ sau bước này `/knowledge/retrieve` và phần RAG của workflow mới có dữ liệu để truy vấn.

`/health` và `/predict` không cần corpus RAG hoặc dữ liệu seed; chúng chỉ cần model CV tải thành công. Tuy vậy `DATABASE_URL` vẫn bắt buộc để ứng dụng khởi tạo cấu hình. Các endpoint RAG/workflow cần cả schema lẫn seed đã nạp.

### 3. Khởi chạy FastAPI Server

```bash
uvicorn app.main:app --reload --port 8000
```
Swagger UI xem và test API tại: `http://localhost:8000/docs`

---

## Triển khai Docker

### Build image

```bash
docker build --tag dxfarm-ai:local --file ai/Dockerfile ai
```

### Chạy container

```bash
docker run --rm --name dxfarm-ai -p 8000:8000 -e DATABASE_URL="postgresql+psycopg://dxfarm:dxfarm@<postgres-host>:5432/dxfarm" -v dxfarm-ai-weights:/app/weights dxfarm-ai:local
```

Thay `<postgres-host>` bằng địa chỉ PostgreSQL mà container truy cập được; nếu service AI nằm cùng Docker Compose với PostgreSQL thì dùng hostname `postgres`.

Kiểm tra service sau khi model đã tải xong:

```bash
curl http://localhost:8000/api/v1/disease/health
```

Lần khởi động đầu tiên cần Internet để tải CV weights từ Hugging Face. Volume `dxfarm-ai-weights` giữ weights cho các lần chạy sau; nếu chạy offline, volume này phải đã có `best_model.pth`.

Để backend gọi container trên cùng Docker network, dùng hostname service `ai` và URL gốc `http://ai:8000`. `deploy/docker-compose.yml` hiện chưa khai báo service `ai`; cần thêm service đó khi tích hợp Compose.

---

## Giới hạn hiện tại & Hướng phát triển tiếp theo

- Các endpoint AI hiện chưa yêu cầu xác thực JWT riêng; xác thực người dùng được kiểm soát ở gateway Spring Boot backend và Keycloak SSO.
- Dockerfile đã sẵn sàng, nhưng Docker Compose và Java backend chưa tự động khởi chạy/gọi AI service; phần tích hợp đó cần cấu hình service `ai` và URL `http://ai:8000`.
- `HF_TOKEN` chưa được cấu hình — HuggingFace Hub sẽ tải model với rate limit ẩn danh (thường đủ dùng, nhưng nếu bị throttle thì set biến `HF_TOKEN` trong `deploy/.env`).
- AI hiện dùng chung một PostgreSQL vật lý với Backend và tự ghi trực tiếp dữ liệu nghiệp vụ (`disease_reports`, `diagnoses`, `treatment_applications`, `workflow_cases`); chưa có Backend REST client trong `ai/`.
  Kế hoạch tách DB & biến AI thành stateless inference service theo [`../development/docs/ai_backend_decoupling_plan.md`](../development/docs/ai_backend_decoupling_plan.md) chưa được triển khai (Phase 1-3 còn mở).
