# AI Service (Python, FastAPI, LangGraph)

Dịch vụ suy luận của DX-OS: nhận diện bệnh lá lúa bằng CV cho Farm, hỏi đáp RAG theo namespace (`farm`, `lab`) cho cả hai ứng dụng.
AI là **stateless và advisory**: không sở hữu dữ liệu nghiệp vụ, không quản lý phê duyệt, không tự xác nhận áp dụng thuốc.
Backend là nguồn sự thật duy nhất; thuốc, liều, PHI luôn lấy từ structured facts của Backend, không từ RAG hay LLM.

## Luồng chẩn đoán

```text
Backend → POST /api/v1/diagnose
            ├─ CV nhận diện disease_code
            ├─ Backend REST trả structured facts (treatment rule, liều, PHI)
            ├─ pgvector trả RAG evidence
            └─ LangGraph tổng hợp recommendation → Backend kiểm tra rule version và lưu
```

| Thành phần | Vai trò | Tài liệu |
|---|---|---|
| Computer Vision | EfficientNetV2-M phân loại Healthy và 5 bệnh lá lúa, crop classifier kiểm ảnh là lúa | [`app/cv`](app/cv/README.md) |
| RAG | Tra cứu tài liệu trong PostgreSQL/pgvector, trả evidence kèm nguồn | [`app/rag`](app/rag/README.md) |
| LangGraph | Điều phối một lượt suy luận, không lưu trạng thái | [`app/graph`](app/graph/README.md) |

## API

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/v1/disease/health` | Readiness của model bệnh và crop classifier (công khai) |
| `GET` | `/api/v1/disease/class-contract` | Contract 6 lớp CV và `disease_code` canonical (công khai) |
| `POST` | `/api/v1/disease/precheck` | Ảnh multipart: kiểm chất lượng và phân loại lúa, không chạy model bệnh |
| `POST` | `/api/v1/disease/predict` | Ảnh multipart: kết quả CV |
| `POST` | `/api/v1/disease/symptoms/suggest` | `symptom_text`, `top_k` 1–6: mã bệnh có score, evidence và source IDs |
| `POST` | `/api/v1/disease/knowledge/retrieve` | Truy vấn RAG theo câu hỏi, `namespace` (mặc định `farm`), `disease_code` và `document_type` tùy chọn |
| `POST` | `/api/v1/diagnose` | Contract nội bộ Backend → AI |

- Mọi endpoint trừ `health` và `class-contract` yêu cầu header `X-Service-Api-Key` khớp `AI_SERVICE_API_KEY`: thiếu hoặc sai key trả `401`, chưa cấu hình key trả `503` (fail-closed).
- Cổng AI không publish ra host, chỉ truy cập được trong mạng compose.
- `diagnose` tải ảnh từ `image_url`, chạy CV, lấy structured facts, truy vấn RAG và trả recommendation; thiếu structured facts thì request thất bại, AI không đưa recommendation chỉ dựa trên RAG.
- Request có `request_id`, `crop_season_id`, `image_url`, `field_context`; response có `request_id`, `cv_result`, `rag_evidence`, `structured_context`, `recommendation`.
- Thuốc, `dose_min`/`dose_max`, `dose_unit`, `phi_days`, `version` giữ nguyên từ structured facts.
- Contract được khóa bởi DTO `AiDiagnoseRequest`, `AiDiagnoseResponse` của `app-farm` và schema `app/api/schemas.py`; `scripts/check_diagnose_contract.py` kiểm tương thích hai phía.

## Dữ liệu và quyền

- Dữ liệu nghiệp vụ (báo cáo, chẩn đoán, thuốc, rule) ở database `dxos` của Backend; AI không có credential, chỉ gọi REST nội bộ chỉ đọc.
- `documents`, `document_chunks` và embedding ở database `dxos_ai_vector_db`: runtime dùng role `ai_vector_ro` (chỉ đọc), job `rag-ingest` dùng role `ai_vector_ingest`.
- Backend khởi tạo database vector, extension `vector`, hai bảng RAG và hai role; runtime không giữ credential ingest.

## Biến môi trường

Compose lấy từ [`deploy/.env.example`](../deploy/.env.example); chạy local thì export trong shell.

| Biến | Ý nghĩa |
|---|---|
| `DATABASE_URL` | Kết nối chỉ đọc tới `dxos_ai_vector_db` |
| `RAG_INGEST_DATABASE_URL` | Kết nối ghi, chỉ cấp cho job ingest |
| `BACKEND_BASE_URL` | Base URL Backend (local `http://localhost:8081`) |
| `BACKEND_SERVICE_API_KEY` | Key AI gửi khi gọi `/internal/v1/**` của Backend |
| `AI_SERVICE_API_KEY` | Key Backend phải gửi khi gọi AI |
| `BACKEND_TIMEOUT_SECONDS`, `BACKEND_MAX_ATTEMPTS` | Timeout và tổng số lần gọi Backend |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | LLM diễn đạt recommendation |
| `EMBEDDING_MODEL` | Embedding dùng chung cho ingest và retrieval |
| `CV_DEVICE` | `auto`, `cpu` hoặc `cuda` |
| `CV_WEIGHTS_PATH`, `CV_WEIGHTS_SHA256` | Checkpoint `ai/models/efficientnetv2m_rice_disease.pth` khi chạy local; trong image là `/opt/dxos/models/efficientnetv2m_rice_disease.pth`; SHA-256 bắt buộc khi nạp |
| `CROP_CLASSIFIER_PATH` | Artifact crop classifier (mặc định `ai/weights/crop_classifier.onnx`) |
| `AI_CV_WEIGHTS_PATH`, `AI_CV_WEIGHTS_SHA256`, `AI_CROP_CLASSIFIER_PATH` | Đường dẫn model bệnh trong image, checksum riêng và đường dẫn crop classifier phía host |

## Chạy

```bash
# Local
cd ai && python -m venv .venv && source .venv/bin/activate
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt
python -m scripts.assemble_cv_weights
export CV_WEIGHTS_SHA256=$(cat weights/dx-farm-efficientnetv2m/model.pth.part-* | sha256sum | cut -d' ' -f1)
uvicorn app.main:app --reload --port 8000

# Compose, từ thư mục deploy (BACKEND_TO_AI_API_KEY và AI_TO_BACKEND_API_KEY phải khác rỗng)
docker compose up -d --build postgres backend ai
```

- Checkout có ba phần model bệnh và `crop_classifier.onnx` trong `ai/weights/`; Docker build ghép model bệnh vào image, crop classifier vẫn mount read-only. Thiếu hoặc sai SHA-256 thì AI từ chối khởi động; restart không tải trọng số bệnh.
  Giá trị `AI_CV_WEIGHTS_SHA256` trong `deploy/.env` là SHA-256 của ba phần nối đúng thứ tự; từ `ai/`, tính bằng `cat weights/dx-farm-efficientnetv2m/model.pth.part-* | sha256sum`, chỉ lưu giá trị vào env riêng.
- AI không dùng migration; Backend phải khởi tạo database vector trước khi gọi RAG.
- Stack Compose có `ollama` và `ollama-init` (tải `OLLAMA_MODEL` trước khi AI khởi động) và job `rag-ingest` chạy trước AI.
- Kiểm model: `docker compose exec ai python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8000/api/v1/disease/health').read().decode())"`.

### Ingest RAG

Corpus đã duyệt ở `data/rag/processed/disease_knowledge.json` (Farm) và `data/rag/lab/processed/lab_knowledge.json` (Lab), cùng registry nguồn:

```bash
export RAG_INGEST_DATABASE_URL=postgresql+psycopg://<ingest-user>:<password>@<host>:5432/dxos_ai_vector_db
python -m scripts.ingest_diseases --dry-run            # preview, không ghi
python -m scripts.ingest_diseases                      # corpus farm
python -m scripts.ingest_diseases --corpus lab         # corpus lab
```

Mỗi lần ingest thay thế tài liệu của đúng namespace của nó.
Đổi embedding model hoặc số chiều vector thì phải ingest lại toàn bộ corpus.

## Kiểm tra

Chạy từ `ai/`:

| Lệnh | Mục đích |
|---|---|
| `pip install -r requirements-dev.txt && python -m pytest tests` | Test CV, workflow, xác thực service key (CI chạy cùng lệnh) |
| `python -m scripts.check_backend_client` | Client Backend: retry giới hạn, không retry khi 401, đọc khoảng liều |
| `python -m scripts.check_diagnose_contract` | Luồng `diagnose` đúng thứ tự CV, Backend, RAG; diễn đạt không chứa số liệu tự sinh |
| `python -m scripts.check_rag_access_boundary` | Việc ghi RAG nằm ngoài runtime FastAPI |

## Giới hạn và giấy phép

- CV nhận diện Healthy và 5 bệnh lá lúa; accuracy 89,43% trên 1.466 ảnh theo báo cáo người huấn luyện, chưa phải accuracy toàn pipeline; kỹ sư chốt bệnh tại Backend.
- RAG chỉ tìm được kiến thức đã ingest; Ollama lỗi hoặc timeout thì trả evidence nguyên văn.
- `ai/` phân phối theo AGPL-3.0 ([`LICENSE`](./LICENSE)); Backend gọi AI qua HTTP nên không là tác phẩm phái sinh.
  Danh sách thành phần ở [`LICENSES.md`](../LICENSES.md).
