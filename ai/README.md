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
            └─ LangGraph chọn rule và trả evidence → Backend kiểm tra rule version và lưu
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
- Mỗi phần tử `rag_evidence` có `content`, `document_title`, `chunk_index` (vị trí đoạn trong tài liệu), `source_ids`, `similarity`; `recommendation` chỉ có `selected_rule_id` và `requires_engineer_approval`, AI không tạo đoạn văn tổng hợp.
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
| `OLLAMA_BASE_URL`, `OLLAMA_MODEL` | LLM diễn đạt câu trả lời hỏi đáp tri thức |
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

### Đánh giá truy xuất RAG

Công cụ nằm ngoài runtime; không đổi model, corpus, ranking hoặc dữ liệu sản phẩm.
Bộ đề có 36 câu hỏi đáp và 6 query chẩn đoán, được chủ dự án duyệt cùng nhãn pilot; việc rà đủ nhãn và tái lập độc lập ở [`BACKLOG`](../docs/BACKLOG.md).
Chỉ số đo tại đầu ra retriever, trước ngưỡng và gộp tài liệu của Backend.

`collect` cần môi trường AI hiện có, model embedding và pgvector đúng corpus; tài khoản kết nối chỉ có quyền đọc.
Bằng chứng ingest lưu ngoài repository trong `ingest.json`, gồm `embedding_model`, `source_model`, `model_artifact`, `corpora`, `completed_at` và `runtime_versions` của torch, sentence-transformers, transformers, SQLAlchemy, pgvector.
Thiếu bằng chứng đúng model hoặc snapshot không khớp thì dừng; không tự ingest đè database sản phẩm.

```bash
cd ai
# Trong môi trường đã cài dependency AI, cấu hình DATABASE_URL chỉ đọc và model đã có.
python -m scripts.evaluate_rag collect --ingest-record /path/outside-repo/ingest.json --output /path/outside-repo/run-01
```

`score`, `self-check` và `publish` chỉ cần môi trường đánh giá riêng; không nạp model hoặc gọi lại retriever.
Mọi artifact phải nằm ngoài repository; mỗi lần collect dùng thư mục mới.

```bash
python3 -m venv /tmp/dx-rag-eval
/tmp/dx-rag-eval/bin/pip install -r requirements-eval.txt
/tmp/dx-rag-eval/bin/python scripts/evaluate_rag.py self-check
/tmp/dx-rag-eval/bin/python scripts/evaluate_rag.py score --collect /path/outside-repo/run-01/collect.json --output /path/outside-repo/run-01
/tmp/dx-rag-eval/bin/python scripts/evaluate_rag.py publish --collect /path/outside-repo/run-01/collect.json --score-file /path/outside-repo/run-01/score.json --dry-run
```

Langfuse self-host dùng [Compose chính thức](https://langfuse.com/self-hosting/deployment/docker-compose) bên ngoài repository, project và volume riêng; phiên bản đã kiểm tra là server 4.70.1 với SDK 5.0.4.
Không thêm Langfuse vào `deploy/` của DX-OS hoặc bật toàn stack sản phẩm để chạy đánh giá.
Giữ PostgreSQL của Langfuse trong mạng Docker nội bộ, tránh chiếm cổng `5432` của sản phẩm.
Sau khi host sẵn sàng, thiết lập `LANGFUSE_BASE_URL`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` trong phiên riêng và chạy lại lệnh publish bỏ `--dry-run`.
SDK tạo ba experiment theo nhóm, replay kết quả đã lưu và gắn score; thời gian trace publish không phải latency retriever, `elapsed_ms` gốc nằm trong output.
Trong Experiments, bật Columns → Expected Output và Output để xem nhãn/chunk bên cạnh điểm; mở chi tiết để xem metadata và cấu hình.
Gửi lại lần đã publish thành công được nhận diện bằng `publish.json` bên ngoài Git; không ghi đè artifact đã bàn giao.
Quy trình tự chấm lại bằng CLI và giới hạn kết quả ở [`docs/TESTING.md`](../docs/TESTING.md).

### Thử tải HTTP retrieval

`scripts/benchmark_rag.py` và `scripts/load_rag.js` đo endpoint retrieval thật bằng k6; không thay ứng dụng sản phẩm hoặc thêm dependency vào CI/image.
Các lệnh bên dưới chạy từ thư mục `ai/` trong môi trường tương ứng.
Farm/Lab hỏi đáp hiện lấy đoạn qua AI rồi Backend lọc/gộp, không gọi Ollama. Phép thử dưới đây chỉ gồm HTTP → embedding → pgvector → ranking → JSON; không đo Backend/gateway, CV hay toàn luồng điện thoại.

Chuẩn bị container AI đánh giá riêng từ image có sẵn, database đánh giá dùng role mặc định chỉ đọc và model/provenance như bước collect. Bind cổng container vào `127.0.0.1:8088`, mount `ai/`, `data/`, model và thư mục artifact bên ngoài repository; không build lại DX-OS. Giới hạn đã thử: AI 2 CPU/1536 MiB, pgvector 1 CPU/256 MiB; dùng `OMP_NUM_THREADS=2`, `MKL_NUM_THREADS=2`, một worker.
Trong container, dùng môi trường AI và khóa `AI_SERVICE_API_KEY` riêng để chạy:

```bash
python scripts/benchmark_rag.py serve --host 0.0.0.0 --port 8000 \
  --ingest-record /artifacts/ingest.json --output /artifacts/load-server.json
```

Lệnh này kiểm corpus, provenance và quyền đọc, làm nóng embedding, ghi server record rồi mở ứng dụng AI hiện có với CV lifespan tắt. Không dùng container này phục vụ chẩn đoán; record và credential ở ngoài repository. `DATABASE_URL` trỏ DB đánh giá, `EMBEDDING_MODEL` phải khớp record ingest; không đổi env của sản phẩm.

Trên host, dùng môi trường đánh giá riêng và binary [k6 chính thức](https://grafana.com/docs/k6/latest/set-up/install-k6/) (đã thử 2.3.0); đặt cùng `AI_SERVICE_API_KEY` trong phiên riêng, không ghi khóa vào script hoặc lệnh có giá trị thật:

```bash
export RAG_LOAD_URL=http://127.0.0.1:8088/api/v1/disease/knowledge/retrieve
/path/eval-venv/bin/python scripts/benchmark_rag.py self-check
/path/eval-venv/bin/python scripts/benchmark_rag.py run \
  --k6 /path/k6 --output /path/outside-repo/load-run-01 \
  --server-record /path/outside-repo/artifacts/load-server.json \
  --container dx-rag-load-ai --container dx-rag-vector --seconds 30 --repeats 3
```

Mỗi nhóm chạy riêng 1/2/5 VU, mỗi mức ba lượt; làm nóng câu hỏi ngoài cửa sổ đo, mỗi VU đợi phản hồi rồi gửi tiếp, không think time. Không đồng nghĩa 1/2/5 request mỗi giây. Mỗi lượt nghỉ 3 giây; giữ cả kết quả lỗi, không ghi đè thư mục cũ.
Kết quả gồm `benchmark.json`, từng lượt có `summary.json`, `metrics.json.gz`, `resources.json`, `k6.log` và `dashboard.html` do k6 xuất. CPU/RAM là mẫu Docker, không phải số tự đo của Langfuse; cách đọc và giới hạn ở [`docs/TESTING.md`](../docs/TESTING.md#thử-tải-http-retrieval).

Sau khi đo, dừng riêng container AI thử tải, bật lại Langfuse nếu đã tạm dừng vì thiếu RAM. Với các biến Langfuse trong phiên đánh giá, chạy:

```bash
/path/eval-venv/bin/python scripts/benchmark_rag.py publish --output /path/outside-repo/load-run-01
```

Trong Experiments, chọn run có tên thư mục đầu ra (pilot hiện tại: `load-2026-10-26-final`); mỗi dòng là một lượt thử tải. Bật Output để xem cấu hình/tài nguyên và các cột score latency/RPS/lỗi/CPU/RAM. Đây là replay tóm tắt k6 đã lưu; thời gian trace publish không phải latency HTTP, hai score chất lượng Recall/nDCG được giữ riêng. Đọc `latency_p95_ms` từng dòng: `SUMMARY / AVG` là trung bình các p95 của các lượt, không phải p95 chung của toàn bộ request.

## Kiểm tra

Chạy từ `ai/`:

| Lệnh | Mục đích |
|---|---|
| `pip install -r requirements-dev.txt && python -m pytest tests` | Test CV, workflow, xác thực service key (CI chạy cùng lệnh) |
| `python -m scripts.check_backend_client` | Client Backend: retry giới hạn, không retry khi 401, đọc khoảng liều |
| `python -m scripts.check_diagnose_contract` | Luồng `diagnose` đúng thứ tự CV, Backend, RAG; `recommendation` chỉ có rule được chọn và cờ duyệt, evidence có tên tài liệu |
| `python -m scripts.check_rag_access_boundary` | Việc ghi RAG nằm ngoài runtime FastAPI |

## Giới hạn và giấy phép

- CV nhận diện Healthy và 5 bệnh lá lúa; accuracy 89,43% trên 1.466 ảnh theo báo cáo người huấn luyện, chưa phải accuracy toàn pipeline; kỹ sư chốt bệnh tại Backend.
- RAG chỉ tìm được kiến thức đã ingest; Ollama lỗi hoặc timeout thì trả evidence nguyên văn.
- `ai/` phân phối theo AGPL-3.0 ([`LICENSE`](./LICENSE)); Backend gọi AI qua HTTP nên không là tác phẩm phái sinh.
  Danh sách thành phần ở [`LICENSES.md`](../LICENSES.md).
