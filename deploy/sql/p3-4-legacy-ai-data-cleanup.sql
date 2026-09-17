-- P3-4: dọn dữ liệu cũ do AI ghi trực tiếp vào DB nghiệp vụ dùng chung (dxfarm), trước khi tách
-- DB vật lý (P3-1..P3-3). Quyết định: toàn bộ là dữ liệu demo/test (chưa có farmer thật dùng hệ
-- thống) nên bỏ, không migrate/backfill. Chạy một lần, thủ công:
--   psql "postgresql://<user>:<password>@<host>:5432/dxfarm" -f deploy/sql/p3-4-legacy-ai-data-cleanup.sql

BEGIN;

-- workflow_cases/workflow_events chỉ AI dùng (WorkflowRunner/WorkflowTransitions); Backend không
-- có entity tương ứng và AI đã ngừng kết nối vào DB này từ khi DATABASE_URL bị cắt sang
-- dx_farm_ai_vector_db (P3-2/P3-3) - hai bảng này mồ côi hoàn toàn, xoá luôn cả bảng.
DROP TABLE IF EXISTS workflow_events CASCADE;
DROP TABLE IF EXISTS workflow_cases CASCADE;

-- disease_reports/diagnoses/treatment_applications vẫn là bảng Backend đang dùng (Phase 2) - chỉ
-- xoá dữ liệu demo cũ (một phần do AI ghi trực tiếp trước Phase 2), giữ nguyên bảng/cấu trúc.
TRUNCATE TABLE treatment_applications, diagnoses, disease_reports RESTART IDENTITY CASCADE;

COMMIT;
