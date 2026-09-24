# Dữ liệu seed / DB (`data`)

Thư mục riêng ở gốc repository, ngang hàng `ai/`, `backend/`, `deploy/` — gom mọi dữ liệu liên quan seed/DB vào một chỗ để dễ theo dõi và quản lý, thay vì rải rác trong từng service.
Nội dung là dữ liệu nguồn do AI bàn giao để Backend tham khảo và tạo seed, tách làm hai nhóm độc lập:

- `structured/`: định danh bệnh, thuốc, treatment rule candidate và các chính sách an toàn phục vụ lookup deterministic.
- `rag/`: tri thức giải thích và registry nguồn dành cho AI Vector DB.
  **Không seed vào database nghiệp vụ của Backend.**

## Cách hoạt động

- **Backend** đọc `structured/*.json` để tạo seed cho database nghiệp vụ (`diseases`, `pesticides`, `treatment_rules`).
  Kế hoạch thống nhất luồng này (generator sinh `backend/DX-Farm/src/main/resources/data.sql` từ `structured/*.json`, Spring seed idempotent lúc khởi động) nằm ở [`development/docs/DB_SEED_UNIFICATION_PLAN.md`](../development/docs/DB_SEED_UNIFICATION_PLAN.md).
- **AI** đọc `rag/processed/disease_knowledge.json` và `rag/source_registry.json` qua job ingest (`ai/scripts/ingest_diseases.py`, chạy bằng `python -m scripts.ingest_diseases`) để tạo embedding và ghi vào AI Vector DB (`documents`, `document_chunks`).
  Xem [`ai/README.md`](../ai/README.md) mục "Ingest RAG".
- Trong Docker Compose, `data/` được bind-mount read-only vào container `rag-ingest` tại `/data` (biến `AI_DATA_DIR` trong `deploy/.env`, mặc định `../data`) — xem [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).

## Nguyên tắc bắt buộc

1. `disease_code` phải khớp tuyệt đối với `diseases.code` và nhãn đầu ra CV; không fuzzy match, đổi tên hoặc tự chuẩn hóa.
2. Tên thuốc, liều, đơn vị và PHI là dữ liệu deterministic.
   Không lấy các giá trị này từ RAG và không để LLM sửa.
3. Chỉ seed treatment rule khi đã được duyệt rõ ràng.
   Không bulk-seed toàn bộ file có chữ `candidates`.
4. Seed phải idempotent: chạy lại không sinh bản ghi trùng.
5. `source_ids` phải truy được về `rag/source_registry.json` để audit nguồn.

## Danh mục file

| File | Nội dung | Backend xử lý |
|---|---|---|
| `structured/diseases.json` | 38 định danh lớp CV: `code`, tên tiếng Việt và mô tả | Seed/upsert vào `diseases`; đây là nguồn tiện dụng nhất cho danh mục bệnh |
| `structured/class_contract.json` | Contract ánh xạ `class_id` → `model_label` → `disease_code` | Dùng để kiểm tra tính toàn vẹn, không cần tạo bảng riêng |
| `structured/pesticides_candidates_master.json` | 8 danh tính sản phẩm đã có `eligible_for_pesticide_seed=true` | Có thể seed danh mục thuốc sau khi Backend chốt `code` và `unit` |
| `structured/treatment_rules_candidates_master.json` | 16 rule ứng viên cùng liều, PHI, nguồn và lý do bị chặn | Không bulk-seed; hiện cả 16 record đều có `eligible_for_seed=false` |
| `structured/disease_management_policies_master.json` | 13 guardrail deterministic cho bệnh không có/không được phép suy ra thuốc chữa | Dữ liệu đã đủ làm guardrail, nhưng Backend hiện chưa có bảng/entity tương ứng; chỉ seed sau khi bổ sung schema/contract |
| `structured/pest_management_candidates_master.json` | 1 ứng viên quản lý nhện đỏ | Không seed: vẫn cần chuyên gia và nguồn nhãn chính thức |
| `structured/coverage_master.json` | Tổng hợp độ phủ 38 lớp và đường xử lý an toàn | File báo cáo/kiểm tra, không seed |
| `structured/demo_support_matrix.json` | Trạng thái hỗ trợ của 26 lớp không healthy | File chọn kịch bản demo, không seed |
| `structured/disease_label_mapping_review_queue_master.json` | Các mapping nhãn đang chờ review | Hàng đợi kiểm duyệt, không seed thành rule |
| `structured/regulatory_review_log.json` | Phạm vi, phương pháp và kết quả rà soát pháp lý | Audit, không seed |
| `structured/data_quality_findings.json` | Các phát hiện chất lượng dữ liệu | Audit, không seed |
| `structured/schema_recommendation_v2.json` | Đề xuất schema để biểu diễn khoảng liều và policy | Tài liệu thiết kế, không seed |
| `structured/structured_decision_policy_v1.json` | Thứ tự quyết định treatment/policy/RAG fallback | Contract logic tham khảo, không seed |
| `rag/processed/disease_knowledge.json` | 38 tài liệu tri thức bệnh/IPM dùng để chunk và embedding | AI ingest vào `documents`/`document_chunks`; Backend không seed |
| `rag/source_registry.json` | 41 nguồn và metadata provenance | Dùng audit; AI giữ cùng corpus RAG |

## Thứ tự seed đề xuất

### 1. Diseases

Seed toàn bộ `structured/diseases.json` vào `diseases`:

| JSON | Backend |
|---|---|
| `code` | `diseases.code` |
| `name` | `diseases.name` |
| `description` | `diseases.description` |

Dùng `code` làm natural key để upsert.
File có đủ 38 lớp CV, gồm cả lớp `healthy`.

### 2. Pesticides

Tám record trong `pesticides_candidates_master.json` đã xác minh danh tính sản phẩm và đều có `eligible_for_pesticide_seed=true`.
Tuy nhiên:

- `candidate_id` chỉ là khóa liên kết trong bộ JSON, không phải `pesticides.code` chính thức.
- `formulation` (`SC`, `WP`, `EC`, `SL`) là dạng thuốc, **không phải** đơn vị liều.
- Schema Backend bắt buộc `pesticides.unit`, vì vậy lấy đơn vị từ rule đã duyệt tương ứng (`L/ha`, `kg/ha` hoặc `% solution`), không lấy từ `formulation`.
- Backend cần chốt quy ước mã ổn định, ví dụ `Ortiva 600SC` → `ORTIVA_600SC`.

### 3. Treatment rules

Liên kết dữ liệu theo thứ tự:

```text
treatment_rules_candidates_master.pesticide_candidate_id
    → pesticides_candidates_master.candidate_id
    → pesticides.id sau khi seed

treatment_rules_candidates_master.disease_code
    → diseases.code
    → diseases.id sau khi seed
```

Mapping vào schema Backend hiện tại:

| JSON | Backend |
|---|---|
| `disease_code` | lookup `diseases.id` |
| `pesticide_candidate_id` | lookup thuốc đã seed, lấy `pesticides.id` |
| `dose_min`/`dose_max` | map trực tiếp vào `treatment_rules.dose_min`/`dose_max` |
| `dose_unit` | `dose_unit` |
| `phi_days` | `phi_days`; không seed nếu đang `null` |
| `application_method`, lượng nước và `blocked_reason` | `notes` nếu rule đã được duyệt |
| `source_ids` | `source`, giữ nguyên các mã nguồn để audit |
| phiên bản được nhóm chốt | `version` |

`treatment_rules` có sẵn `dose_min`/`dose_max`.
Rule có khoảng liều map thẳng vào `dose_min`/`dose_max`; rule liều cố định dùng `dose_min = dose_max`.

## Rule đã chốt cho luồng E2E hiện tại

Để kiểm thử luồng AI → Backend → kỹ sư phê duyệt, hiện chỉ seed rule cố định sau:

| Field | Giá trị |
|---|---|
| Candidate | `TR-CAND-004` |
| Disease code | `Tomato___Early_blight` |
| Pesticide code | `ORTIVA_600SC` |
| Pesticide name | `Ortiva 600SC` |
| Dose | `1.50 L/ha` |
| PHI | `7` ngày |
| Application | Phun khi tỷ lệ bệnh khoảng 5–10% |
| Water volume | 400–600 L/ha |
| Sources | `S27,S30,S29,S39` |
| Version | `2026.09.10-mvp-reviewed-v1` |

`S27` và `S29` kiểm tra danh mục/quy định Việt Nam, `S30` là hướng dẫn của nhà sản xuất, còn `S39` hỗ trợ mapping `Alternaria solani` với bệnh đốm vòng trên cà chua.
Không thay đổi thuốc, liều hoặc PHI khi đưa rule này vào seed.

Ví dụ SQL idempotent theo schema Backend hiện tại:

```sql
INSERT INTO pesticides (code, name, unit)
VALUES ('ORTIVA_600SC', 'Ortiva 600SC', 'L/ha')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name, unit = EXCLUDED.unit;

INSERT INTO treatment_rules (
  disease_id, pesticide_id, dose_min, dose_max, dose_unit,
  phi_days, notes, source, version
)
SELECT
  d.id,
  p.id,
  1.50,
  1.50,
  'L/ha',
  7,
  'Phun khi tỷ lệ bệnh khoảng 5-10%. Lượng nước 400-600 L/ha.',
  'S27,S30,S29,S39',
  '2026.09.10-mvp-reviewed-v1'
FROM diseases d
JOIN pesticides p ON p.code = 'ORTIVA_600SC'
WHERE d.code = 'Tomato___Early_blight'
  AND NOT EXISTS (
    SELECT 1
    FROM treatment_rules tr
    WHERE tr.disease_id = d.id
      AND tr.pesticide_id = p.id
  );
```

Các record còn lại trong `treatment_rules_candidates_master.json` vẫn là dữ liệu nghiên cứu.
`blocked_reason`, `verification` và `eligible_for_seed` là hàng rào an toàn, không phải metadata có thể bỏ qua.

## Disease management policies

Mười ba policy đều có `eligible_for_deterministic_guardrail=true`.
Chúng mô tả trường hợp không có thuốc chữa trực tiếp, ưu tiên quản lý vector hoặc chưa xác minh được rule địa phương.
Không chuyển nội dung các policy này thành treatment rule.

Backend hiện chưa có entity/bảng `disease_management_policies`; việc mở entity này đã được lên kế hoạch tại [`development/docs/DB_SEED_UNIFICATION_PLAN.md`](../development/docs/DB_SEED_UNIFICATION_PLAN.md) (field đề xuất: `disease_id`, `management_status`, `direct_treatment_rule_allowed`, `chemical_rule_scope`, `recommended_actions`, `source`, `version`).
Cho tới khi entity được triển khai, giữ 13 policy này làm nguồn tham khảo và để AI đi theo nhánh `review_required` khi không có treatment rule được duyệt.

## RAG không thuộc seed nghiệp vụ Backend

`rag/processed/disease_knowledge.json` chứa kiến thức mô tả triệu chứng, sinh học bệnh, phòng ngừa và IPM.
AI dùng file này để tạo embedding và ghi vào AI Vector DB (`documents`, `document_chunks`).
Nội dung RAG chỉ dùng giải thích và bổ sung ngữ cảnh; không được biến thành tên thuốc, liều hoặc PHI.

## Checklist sau khi seed

- Có đúng 38 `diseases.code` thuộc contract CV và không có mã bị đổi.
- Mọi treatment rule đều tham chiếu disease và pesticide tồn tại.
- Không có hai rule trùng cùng cặp `(disease_id, pesticide_id)` ngoài chủ đích nghiệp vụ.
- Mọi rule có `dose_min`, `dose_max`, `dose_unit`, `phi_days`, `source` và `version` không rỗng.
- API nội bộ `/internal/v1/diagnosis-context` trả đúng rule theo exact `disease_code`.
- Rule không tồn tại hoặc chưa duyệt phải trả nhánh `review_required`; không fallback sang số liệu do RAG/LLM tạo.
