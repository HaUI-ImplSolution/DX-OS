# AI Service (Python — FastAPI & LangGraph)

`ai/` là dịch vụ suy luận của DX-Farm. Dịch vụ nhận ảnh và ngữ cảnh báo cáo từ Backend, nhận diện bệnh bằng Computer Vision, lấy sự thật nghiệp vụ qua Backend REST API, truy xuất kiến thức giải thích trong AI Vector DB và trả recommendation về Backend.

AI hoạt động theo nguyên tắc **Stateless & Advisory**: không sở hữu hoặc thay đổi dữ liệu nghiệp vụ, không quản lý vòng đời phê duyệt và không tự xác nhận áp dụng thuốc. Backend là nguồn sự thật duy nhất cho dữ liệu nghiệp vụ và các trạng thái `PENDING`, `APPROVED`, `REJECTED`, `APPLIED`.

## Kiến trúc

```text
Backend → POST /api/v1/diagnose
              │
              ├─ CV nhận diện disease_code
              ├─ Backend REST trả structured facts
              ├─ pgvector trả RAG evidence
              └─ LangGraph tổng hợp recommendation
                         │
                         └─ Backend kiểm tra và lưu
```

Các thành phần chính:

- **Computer Vision**: YOLOv8n (checkpoint cục bộ `best.pt`) phát hiện một trong 5 lớp bệnh lá lúa, dùng `Rice___healthy` làm fallback khi không có detection đạt ngưỡng; trả `disease_code`, confidence cùng metadata model.
- **Structured facts**: AI gọi internal API chỉ đọc của Backend để lấy treatment rule, liều lượng, PHI và lịch sử phun. Những dữ liệu này không được lấy từ RAG hoặc tự sinh bởi LLM.
- **RAG**: truy vấn `documents` và `document_chunks` trong PostgreSQL/pgvector bằng embedding 384 chiều, trả evidence kèm nguồn.
- **LangGraph**: điều phối một lượt suy luận stateless; không lưu workflow hoặc trạng thái phê duyệt.
- **Recommendation**: kết hợp kết quả CV, structured facts và RAG evidence. Backend kiểm tra lại rule version trước khi lưu.

Tài liệu chi tiết theo module:

- [`app/cv/README.md`](app/cv/README.md)
- [`app/rag/README.md`](app/rag/README.md)
- [`app/graph/README.md`](app/graph/README.md)

## API

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/v1/disease/health` | Trạng thái model CV và thiết bị chạy (công khai) |
| `GET` | `/api/v1/disease/class-contract` | Contract 5 lớp CV, fallback và `disease_code` canonical (công khai) |
| `POST` | `/api/v1/disease/predict` | Nhận ảnh multipart và trả kết quả CV, yêu cầu header `X-Service-Api-Key` |
| `POST` | `/api/v1/disease/knowledge/retrieve` | Truy vấn RAG theo câu hỏi và `disease_code` tùy chọn, yêu cầu header `X-Service-Api-Key` |
| `POST` | `/api/v1/diagnose` | Contract nội bộ Backend → AI, yêu cầu header `X-Service-Api-Key` |

Mọi endpoint ngoài `/health` và `/class-contract` đều yêu cầu `X-Service-Api-Key` khớp `AI_SERVICE_API_KEY`; thiếu hoặc sai key trả `401`, chưa cấu hình key trả `503`.
Cổng AI không publish ra host, chỉ truy cập được trong mạng compose.

`POST /api/v1/diagnose` tải ảnh từ `image_url`, chạy CV, gọi Backend lấy structured facts, truy vấn RAG và trả recommendation. Nếu structured facts không khả dụng, request thất bại; AI không đưa ra recommendation chỉ dựa trên RAG.

## Dữ liệu và quyền sở hữu

| Dữ liệu | Nơi sở hữu | Quyền của AI runtime |
|---|---|---|
| Parcel, CropSeason, DiseaseReport, Diagnosis, TreatmentApplication | Backend DB `dxfarm` | Không có credential DB; chỉ nhận dữ liệu cần thiết qua REST |
| Disease, Pesticide, TreatmentRule | Backend DB `dxfarm` | Chỉ đọc qua internal REST API |
| DiseaseManagementPolicy | Backend DB `dxfarm` | Chỉ đọc qua internal REST API khi endpoint tương ứng được mở |
| Document, DocumentChunk và embedding | AI Vector DB `dx_farm_ai_vector_db` | Role `ai_vector_ro` chỉ có quyền đọc |
| Corpus ingest | Thư mục `data/` ở gốc repository (mount read-only vào job `rag-ingest`) | Job riêng dùng role `ai_vector_ingest` |

Backend khởi tạo AI Vector DB, extension `vector`, hai bảng RAG và hai role PostgreSQL. Runtime không giữ credential ingest; `RAG_INGEST_DATABASE_URL` chỉ được cấp khi chạy job quản trị.

## Biến môi trường

Cấu hình Docker Compose nằm tại [`deploy/.env.example`](../deploy/.env.example); chạy local thì export các biến dưới đây trong shell.

| Biến | Ý nghĩa |
|---|---|
| `DATABASE_URL` | Kết nối read-only của AI runtime tới `dx_farm_ai_vector_db` |
| `RAG_INGEST_DATABASE_URL` | Kết nối read/write chỉ dành cho job ingest |
| `BACKEND_BASE_URL` | Base URL Backend, mặc định local `http://localhost:8081` |
| `BACKEND_SERVICE_API_KEY` | Key AI gửi khi gọi `/internal/v1/**` của Backend |
| `AI_SERVICE_API_KEY` | Key Backend phải gửi khi gọi `/api/v1/diagnose` |
| `BACKEND_TIMEOUT_SECONDS` | Timeout mỗi lần AI gọi Backend |
| `BACKEND_MAX_ATTEMPTS` | Tổng số lần gọi Backend, bao gồm lần đầu |
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | Cấu hình LLM diễn đạt recommendation |
| `EMBEDDING_MODEL` | Model embedding dùng đồng nhất cho ingest và retrieval |
| `CV_DEVICE` | `auto`, `cpu` hoặc `cuda` |
| `CV_WEIGHTS_PATH` | Đường dẫn checkpoint `best.pt` (mặc định `ai/weights/best.pt`); service không tự tải |
| `CV_WEIGHTS_SHA256` | SHA-256 kỳ vọng của checkpoint; bắt buộc khi nạp model |
| `AI_CV_WEIGHTS_PATH` | Đường dẫn phía host mà Compose bind-mount; mặc định dùng checkpoint chuẩn trong repo |
| `AI_CV_WEIGHTS_SHA256` | Giá trị phía host được Compose truyền vào `CV_WEIGHTS_SHA256` |

Hai service key phải được cấu hình và khớp theo từng chiều. Khi key đầu vào AI để trống, `/api/v1/diagnose` trả `503` theo cơ chế fail-closed.

## Chạy local

```bash
cd ai
python -m venv .venv
source .venv/bin/activate
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

AI không dùng Alembic hoặc migration. Backend phải khởi tạo AI Vector DB trước khi endpoint RAG được gọi.

`/health`, `/class-contract` và `/predict` không cần corpus RAG. `/knowledge/retrieve` và `/diagnose` cần schema vector; để có evidence, corpus phải được ingest trước.

## Ingest RAG

Corpus đã duyệt nằm tại `data/rag/processed/disease_knowledge.json` cùng registry nguồn `data/rag/source_registry.json` (thư mục `data/` ở gốc repository, dùng chung cho mọi dữ liệu seed/DB, không riêng AI).
Sau khi có/cập nhật corpus, chạy:

```bash
export RAG_INGEST_DATABASE_URL=postgresql+psycopg://<ingest-user>:<password>@<host>:5432/dx_farm_ai_vector_db
python -m scripts.ingest_diseases --dry-run
python -m scripts.ingest_diseases
```

Job chỉ ghi `documents` và `document_chunks`. Model embedding khi ingest phải giống model của runtime; đổi model hoặc số chiều vector yêu cầu ingest lại toàn bộ corpus.

## Docker Compose

Từ thư mục gốc repository:

```bash
cp deploy/.env.example deploy/.env
# Mọi biến trong compose là bắt buộc; BACKEND_TO_AI_API_KEY và AI_TO_BACKEND_API_KEY phải khác rỗng.
cd deploy
docker compose up -d --build postgres backend ai
```

Compose sử dụng:

- `ai_vector_ro` qua `DATABASE_URL` cho AI runtime.
- `ai_vector_ingest` qua `RAG_INGEST_DATABASE_URL` cho job `rag-ingest`, tự chạy trước khi AI khởi động.
- Service `ollama` và `ollama-init` chạy cùng stack; `ollama-init` tải model `OLLAMA_MODEL` trước khi AI khởi động.
- `http://ai:8000` cho chiều Backend → AI.
- `http://backend:8081` cho chiều AI → Backend.

Kiểm tra model sau khi service khởi động (cổng AI không publish ra host nên gọi từ bên trong container):

```bash
docker compose exec ai python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8000/api/v1/disease/health').read().decode())"
```

Checkpoint CV chuẩn nằm tại `ai/weights/best.pt`. Compose bind-mount file này ở chế độ read-only; `AI_CV_WEIGHTS_PATH` chỉ cần đổi khi máy dùng đường dẫn khác. Điền checksum được cấp cùng checkpoint vào `AI_CV_WEIGHTS_SHA256`; nếu thiếu hoặc không khớp, AI từ chối khởi động. Volume `ai_weights` chỉ lưu cache embedding. Nếu đường dẫn override không tồn tại, Compose báo lỗi và không tạo thư mục `best.pt` trên host.

## Script kiểm tra

Chạy từ thư mục `ai/`:

| Lệnh | Mục đích |
|---|---|
| `python -m scripts.check_backend_client` | Kiểm tra client gọi Backend bằng mock: retry giới hạn, không retry khi HTTP 401, đọc khoảng liều `dose_min`/`dose_max` |
| `python -m scripts.check_diagnose_contract` | Kiểm tra luồng `/diagnose` chạy theo thứ tự CV, Backend, RAG; response đúng contract và phần diễn đạt không chứa số liệu tự sinh |
| `python -m scripts.check_rag_access_boundary` | Xác nhận việc ghi RAG nằm ngoài runtime FastAPI |
| `pip install -r requirements-dev.txt && python -m pytest tests` | Chạy test (CV, workflow, xác thực service key); CI chạy cùng lệnh |

## Giới hạn vận hành

- CV chỉ nhận diện 5 lớp bệnh lá lúa đã huấn luyện; kết quả là tín hiệu chẩn đoán, không phải quyết định điều trị.
- RAG chỉ tìm được kiến thức đã ingest và không thay thế structured facts từ Backend.
- Ollama có thể fallback khi lỗi hoặc timeout, nhưng thiếu structured facts luôn làm lượt chẩn đoán thất bại.
- `/predict` và `/knowledge/retrieve` là endpoint kỹ thuật; luồng nghiệp vụ chính đi qua Backend và `POST /api/v1/diagnose`.
