# DX-Farm AI MVP — Stage 8–10 Runbook

Runbook này chỉ vận hành AI service local. Backend/frontend không bị thay đổi.
Luồng an toàn: CV → SQL rule/policy → RAG evidence → recommendation → human
APPROVE/REJECT → xác nhận APPLIED → PHI.

## Prerequisites

- Docker Engine + Docker Compose v2.
- Python 3.11/3.12 và virtualenv đã cài ai/requirements.txt.
- Checkpoint CV tại ai/weights/best_model.pth hoặc quyền tải Hugging Face.
- Ollama và qwen2.5:1.5b là tùy chọn; khi down, hệ thống trả evidence/structured
  facts an toàn. PostgreSQL và pgvector là bắt buộc.

## Environment variables

Sao chép ai/.env.example thành ai/.env và sửa cho máy local. Không commit .env.

- DATABASE_URL: bắt buộc, cấu hình được.
- OLLAMA_BASE_URL, OLLAMA_MODEL, LLM_TIMEOUT_SECONDS: LLM local.
- CV_CONFIDENCE_THRESHOLD: mặc định 0.70; dưới ngưỡng cần kỹ sư xác nhận rõ.
- CV_DEVICE: auto, cpu hoặc device hợp lệ.
- LOG_LEVEL: mức log. HTTP response không trả traceback/secret.

Ollama local không dùng API key. Nếu đổi provider cần key, đưa secret qua
environment/secret manager; không hard-code hoặc commit.

## Database startup và migration

Từ repository root, chỉ bật PostgreSQL:

~~~bash
docker compose -f deploy/docker-compose.yml up -d postgres
cd ai
../venv/bin/python -m alembic upgrade head
~~~

Head Stage 9 là f0a1b2026011. Migration thêm workflow audit và snapshot treatment;
không drop bảng hoặc dữ liệu Stage 1–7.

## Seed structured data

~~~bash
../venv/bin/python -m scripts.seed_diseases
../venv/bin/python -m scripts.seed_pesticides
../venv/bin/python -m scripts.seed_treatment_rules
../venv/bin/python -m scripts.seed_disease_management_policies
~~~

Seeder idempotent. Runtime không đọc data/stage3/hold hoặc candidate.

## RAG ingest/check

Lệnh sau tạo embedding thật và replace atomically riêng disease knowledge:

~~~bash
HF_HUB_OFFLINE=1 ../venv/bin/python -m scripts.ingest_diseases
~~~

Bỏ HF_HUB_OFFLINE ở lần đầu nếu model chưa trong cache. Expected: 38 document /
270 chunk, source registry 41 nguồn.

## CV và AI/API startup

~~~bash
HF_HUB_OFFLINE=1 ../venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000
~~~

Nếu checkpoint chưa có, bỏ offline để tải; nếu Hugging Face Xet lỗi, dùng
HF_HUB_DISABLE_XET=1. API docs: http://127.0.0.1:8000/docs.

## Demo fixtures

~~~bash
../venv/bin/python -m scripts.seed_demo_fixtures
~~~

Tạo idempotent DEMO-PARCEL-001 và một crop season active. AI service chưa có auth
model nên không tự tạo user; API nhận actor_id để audit demo.

## Smoke test

Từ ai/:

~~~bash
HF_HUB_OFFLINE=1 ../venv/bin/python -m scripts.smoke_test_mvp \
  --image tests/data/images/tomato_leaf.jpg
~~~

Script kiểm tra DB/count, rule, safe policy, RAG/provenance, FastAPI lifespan +
model thật, prediction canonical, LangGraph đến PENDING và LLM evidence fallback.
Script không tự approve hoặc ghi treatment. Output có `timings_ms` và
`graph_timings_ms`; đây là số đo smoke trên máy đang chạy, không phải benchmark chuẩn.

## Demo flow

1. POST /api/v1/disease/workflow/run dạng multipart với image, parcel_code,
   request_id và requested_by.
2. Đọc case_id, recommendation, rule/policy; trạng thái luôn PENDING.
3. Chemical path: POST .../workflow/{case_id}/approve với actor_id,
   selected_rule_id, approved_dose. Chưa có TreatmentApplication/PHI.
4. Khi phun thật: POST .../workflow/{case_id}/applied với actor_id,
   actual_dose, applied_at. Lúc này mới tạo application và PHI.
5. Hoặc POST .../workflow/{case_id}/reject với reason.
6. GET .../workflow/{case_id} trả trạng thái và audit history.

Policy/review path không nhận chemical rule/dose và không thể confirm chemical
APPLIED. Request/APPLIED retry an toàn; server không clamp liều ngoài range.

## Tests

~~~bash
HF_HUB_OFFLINE=1 OMP_NUM_THREADS=2 \
RUN_STAGE3_DB_TESTS=1 RUN_STAGE67_DB_TESTS=1 RUN_STAGE910_DB_TESTS=1 \
../venv/bin/python -m pytest -q tests --tb=short
~~~

Thêm RUN_REAL_CV_TESTS=1 để test checkpoint thật. DB tests dùng outer transaction
và rollback fixtures, không để lại case/application test.

## Known limitations

- Checkpoint CV không chứa class_to_idx; thứ tự lớp đối chiếu training code,
  chưa phải chứng nhận accuracy/calibration độc lập.
- LLM wording bảo thủ: chỉ evidence excerpt; Ollama down vẫn giữ structured facts.
- AI API chưa có authentication/authorization; actor_id chỉ phục vụ audit MVP.
- Chưa nối HTTP client Spring backend hoặc UI approval; demo gọi API trực tiếp.
- Ảnh workflow chỉ lưu URI audit workflow://...; chưa có object storage.

## Troubleshooting

- Connection refused :5432: bật postgres và kiểm tra DATABASE_URL.
- Alembic current chưa phải f0a1b2026011: chạy alembic upgrade head.
- CV 503: kiểm tra checkpoint/cache và health.
- RAG rỗng: chạy ingest, kiểm tra 38/270 và embedding 384 chiều.
- Ollama timeout: kiểm tra OLLAMA_BASE_URL/model; structured facts vẫn dùng được.
- 409: case đã transition hoặc transition không hợp lệ.
- 422 dose: actual dose phải nằm trong dose_min..dose_max; không tự sửa.
