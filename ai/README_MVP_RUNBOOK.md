# DX-Farm AI MVP Runbook

AI runtime nằm ở `ai/app`: FastAPI nhận ảnh, EfficientNet-B4 trả disease code chuẩn, LangGraph tạo khuyến nghị có evidence, còn PostgreSQL lưu case và audit event.

## Chạy local

```bash
cd /path/to/DX---Farm
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d postgres
cd ai
python3 -m venv venv
source venv/bin/activate
python -m pip install -r requirements.txt
cp .env.example .env
python -m alembic upgrade head
uvicorn app.main:app --reload
```

Kiểm tra: `GET http://127.0.0.1:8000/api/v1/disease/health`.

## Dữ liệu và kiểm thử

```bash
cd ai
python -m scripts.seed_diseases
python -m scripts.seed_pesticides
python -m scripts.seed_treatment_rules
python -m scripts.seed_disease_management_policies
HF_HUB_OFFLINE=1 OMP_NUM_THREADS=2 RUN_REAL_CV_TESTS=1 RUN_STAGE3_DB_TESTS=1 RUN_STAGE67_DB_TESTS=1 RUN_STAGE910_DB_TESTS=1 python -m pytest -q tests
```

Chỉ chạy `python -m scripts.ingest_diseases` khi chủ động tạo embedding; `--dry-run` không ghi database.

## Giới hạn hiện tại

- AI chưa được Backend/Frontend gọi qua HTTP production và chưa có auth riêng.
- `workflow/run` chỉ tạo recommendation/case PENDING; approve, reject, applied là endpoint tách riêng.
- Thuốc, liều và PHI chỉ lấy từ structured database; RAG/LLM không được thay thế các facts này.

Contract tích hợp: `development/docs/AI_EVENT_SCHEMA_README.md`; kiến trúc: `development/docs/AI_README_LangGraph_MVP.md`; database: `development/docs/README_POSTGRESQL.md`.
