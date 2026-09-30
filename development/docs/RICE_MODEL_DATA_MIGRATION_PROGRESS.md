# Tiến độ chuyển đổi dữ liệu và mô hình bệnh lá lúa

Nhật ký triển khai `RICE_MODEL_DATA_MIGRATION_PLAN.md`. Chỉ ghi nhận thay đổi đã kiểm tra; nội dung hoàn tất sẽ được chắt lọc vào `CHANGELOG.md`.

## Data / AI / Backend

### 2026-09-30

**Đã xong — Giai đoạn 0: chốt baseline và phạm vi ảnh hưởng**

- Xác nhận Git sạch trước khi audit; chưa thay data, model hoặc code vận hành.
- Chốt `RICE_DATA_COLLECTION_TEMPLATE.xlsx` là nguồn thay thế bộ PlantVillage hiện tại, không merge hai bộ dữ liệu.
- Audit workbook: 1 dataset; 6 lớp/điều kiện; 6 tài liệu RAG; 9 nguồn; 81 bằng chứng; 1 thuốc ứng viên; 2 treatment rule ứng viên; 6 policy.
- Giữ nguyên governance từ workbook: dataset và 6 lớp CV đang `BLOCKED`; các hàng bệnh, RAG, nguồn, bằng chứng, thuốc, rule và policy đang `IN_REVIEW`. Không có thuốc, rule hoặc policy đủ điều kiện seed.
- Chốt mapping runtime theo checkpoint: `0 Blast`, `1 Brown Spot`, `2 Leaf Smut`, `3 Rice Tungro`, `4 Sheath Blight`; healthy là fallback khi không có detection đạt ngưỡng.
- Xác minh tĩnh `best.pt`: task `detect`, nguồn kiến trúc `yolov8n.pt`, đủ 5 lớp đúng thứ tự; SHA-256 `573DB0871F39396F2BBEC5E6AA5988D36B79B13E01CB00F03EEE29B32A12C60B`.
- Ghi nhận SHA-256 workbook: `C27C415E40C5CB127FF4206794CA02C4AB055E81BC31D37E4977913230A631F3`.
- Truy vết luồng hiện tại: workbook/JSON → generator → `data.sql` → Backend DB; RAG JSON → ingest → AI Vector DB; upload → AI CV → exact `disease_code` → Backend context/RAG → recommendation → Diagnosis.

**Baseline đã kiểm tra**

- Data generator: 3/3 test đạt; `data.sql --check` đạt.
- RAG: dry-run xác thực 38/38 tài liệu hiện tại.
- AI: 3/3 script kiểm tra Backend client, diagnose contract và ranh giới ghi RAG đạt; chưa có source test được pytest thu thập.
- Backend: Maven build đạt, 1/1 test đạt.
- Frontend: production build đạt; project chưa có test script.
- Compose: cấu hình hợp lệ; chưa chạy E2E container vì Docker daemon không hoạt động.

**Phạm vi thay đổi đã chốt cho các giai đoạn sau**

- Data: thay nội dung `data/structured/{diseases,class_contract,pesticides_candidates_master,treatment_rules_candidates_master,disease_management_policies_master}.json`, `data/rag/processed/disease_knowledge.json` và `data/rag/source_registry.json` từ workbook.
- Provenance: dùng sheet `06_BANG_CHUNG` để kiểm tra nội dung và `source_ids`; không thêm schema/file runtime mới khi chưa có consumer.
- Legacy data: loại `data/structured/pest_management_candidates_master.json` vì không có dữ liệu thay thế trong workbook và không có runtime consumer.
- Seed: cập nhật generator/test và sinh lại `backend/DX-Farm/src/main/resources/data.sql`; bảo toàn bản ghi cũ đang được lịch sử tham chiếu và loại chúng khỏi lookup mới.
- CV: thay loader/inference EfficientNet bằng YOLOv8n; cập nhật contract mapping, cấu hình, dependency, checkpoint mount read-only và test. Giữ nguyên response API.
- RAG/E2E: cập nhật corpus, version ingest và fixture/check script sang mã bệnh lúa; giữ nguyên graph, DTO và database schema.
- Tài liệu: cập nhật README, env mẫu, Compose và license theo model/data mới.
- Không cần đổi frontend, Backend DTO, API endpoint hoặc workflow topology nếu các contract hiện tại tiếp tục đạt.

**Ghi chú baseline**

- Môi trường AI hiện chưa cài `ultralytics`; kiểm tra load/inference thật của checkpoint thuộc Giai đoạn 3.
- Warning thiếu secret local của Keycloak/Superset khi kiểm tra Compose không liên quan migration.
