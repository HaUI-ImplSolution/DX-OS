# Dữ liệu seed / DB (`data`)

Thư mục riêng ở gốc repository, ngang hàng `ai/`, `backend/`, `deploy/` — gom mọi dữ liệu liên quan seed/DB vào một chỗ để dễ theo dõi và quản lý, thay vì rải rác trong từng service.
Nội dung là dữ liệu nguồn do AI bàn giao để Backend tham khảo và tạo seed, tách làm hai nhóm độc lập:

- `structured/`: định danh bệnh, thuốc, treatment rule candidate và các chính sách an toàn phục vụ lookup deterministic.
- `rag/`: tri thức giải thích và registry nguồn dành cho AI Vector DB.
  **Không seed vào database nghiệp vụ của Backend.**

## Cách hoạt động

- **Backend** đọc `structured/*.json` để tạo seed cho database nghiệp vụ (`diseases`, `pesticides`, `treatment_rules`, `disease_management_policies`).
  Generator `data/scripts/generate_seed_sql.py` sinh `backend/DX-Farm/src/main/resources/data.sql` từ `structured/*.json`; Spring chạy file này idempotent lúc khởi động.
- **AI** đọc `rag/processed/disease_knowledge.json` và `rag/source_registry.json` qua job ingest (`ai/scripts/ingest_diseases.py`, chạy bằng `python -m scripts.ingest_diseases`) để tạo embedding và ghi vào AI Vector DB (`documents`, `document_chunks`).
  Xem [`ai/README.md`](../ai/README.md) mục "Ingest RAG".
- Trong Docker Compose, `data/` được bind-mount read-only vào container `rag-ingest` tại `/data` (biến `AI_DATA_DIR` trong `deploy/.env`, giá trị mẫu `../data`) — xem [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).
- `data/` ở gốc repository là nguồn canonical duy nhất; không duy trì bản sao trong `ai/.local/data` vì dễ khiến ingest dùng dữ liệu cũ và không thể tái lập từ Git.

## Nguyên tắc bắt buộc

1. `disease_code` phải khớp tuyệt đối với `diseases.code` và nhãn đầu ra CV; không fuzzy match, đổi tên hoặc tự chuẩn hóa.
2. Tên thuốc, liều, đơn vị và PHI là dữ liệu deterministic.
   Không lấy các giá trị này từ RAG và không để LLM sửa.
3. Chỉ seed treatment rule khi có quyết định cho phép rõ ràng trong dữ liệu nguồn (`eligible_for_seed=true` kèm `seed_authorization`).
   Không bulk-seed toàn bộ file có chữ `candidates`.
4. Seed phải idempotent: chạy lại không sinh bản ghi trùng.
5. `source_ids` phải truy được về `rag/source_registry.json` để audit nguồn.

## Danh mục file

| File | Nội dung | Backend xử lý |
|---|---|---|
| `structured/diseases.json` | 6 định danh: 5 lớp bệnh lúa của CV và `Rice___healthy` (fallback): `code`, tên tiếng Việt, mô tả | Seed/upsert vào `diseases` |
| `structured/class_contract.json` | Contract `class_id` → `model_label` → `disease_code` của model CV (5 lớp, kèm fallback), phiên bản `riceleafbd-v3-yolov8n` | Dùng để kiểm tra tính toàn vẹn, không cần tạo bảng riêng |
| `structured/pesticides_candidates_master.json` | 1 danh tính sản phẩm (`NEWTEC_300SC`): mã Backend, đơn vị liều, xác minh đăng ký và nhãn | Chỉ seed record có `eligible_for_pesticide_seed=true`; hiện chưa có record nào |
| `structured/treatment_rules_candidates_master.json` | 2 rule ứng viên (đạo ôn, khô vằn) cùng liều, PHI, nguồn và trạng thái cho phép seed | Chỉ seed rule có `eligible_for_seed=true`; hiện chưa có rule nào |
| `structured/disease_management_policies_master.json` | 6 policy: trạng thái quản lý từng bệnh, hành động khuyến nghị và nguồn | Seed vào `disease_management_policies` khi `eligible_for_deterministic_guardrail=true`; hiện chưa có policy nào |
| `rag/processed/disease_knowledge.json` | 6 tài liệu tri thức bệnh/IPM dùng để chunk và embedding | AI ingest vào `documents`/`document_chunks`; Backend không seed |
| `rag/source_registry.json` | 9 nguồn và metadata provenance | Dùng audit; AI giữ cùng corpus RAG |

Các script trong `scripts/`:

| Script | Việc làm |
|---|---|
| `generate_seed_sql.py` | Sinh `data.sql` từ `structured/*.json`; `--check` xác minh `data.sql` không lệch master |
| `test_generate_seed_sql.py` | Test cho generator |
| `validate_rice_workbook_export.py` | Đối chiếu JSON canonical với workbook thu thập dữ liệu cục bộ (mặc định `development/.local/RICE_DATA_COLLECTION_TEMPLATE.xlsx`) |

## Seed hiện hành

`data.sql` làm các việc sau, theo thứ tự:

1. Thêm cột `active` cho `diseases` và `pesticides` nếu chưa có.
2. Upsert 6 bệnh trong `diseases.json` với `active=TRUE`.
3. Đặt `active=FALSE` cho mọi bệnh khác; bản ghi legacy chỉ bị xóa khi không còn diagnosis, treatment rule hoặc policy tham chiếu.
4. Pesticide, treatment rule và policy: chưa có record nào đủ điều kiện seed nên không thêm mới.
   Rule và pesticide cũ được đặt `active=FALSE`, chỉ bị xóa khi không còn lịch sử diagnosis hoặc treatment application tham chiếu; bảng `disease_management_policies` được làm trống.

Bệnh `active=FALSE` và thuốc `active=FALSE` không xuất hiện trong danh mục API và lookup mới nhưng vẫn giữ được lịch sử.

## Quy ước khi mở khóa dữ liệu thuốc

Mỗi khi chuyên gia duyệt một thuốc, rule hoặc policy, cập nhật JSON trong `structured/` rồi chạy `python data/scripts/generate_seed_sql.py`.

### Pesticides

`pesticides_candidates_master.json` là contract nguồn cho bảng `pesticides` của Backend:

| JSON | Backend |
|---|---|
| `code` | `pesticides.code` — bắt buộc, duy nhất, `UPPER_SNAKE_CASE` |
| `name` | `pesticides.name` |
| `unit` | `pesticides.unit` — bắt buộc khi `eligible_for_pesticide_seed=true` |

- `candidate_id` chỉ là khóa liên kết trong bộ JSON, không thay cho `pesticides.code`.
- `formulation` (`SC`, `WP`, `EC`, `SL`) là dạng thuốc, **không phải** đơn vị liều.
- Generator đọc trực tiếp `code` và `unit`; không tự suy ra từ `name`, treatment rule hoặc `formulation`.
- Chỉ record có đủ `code`, `name`, `unit` với nguồn phù hợp mới được `eligible_for_pesticide_seed=true` và được đưa vào `data.sql`.

### Treatment rules

Liên kết dữ liệu theo thứ tự:

```text
treatment_rules_candidates_master.pesticide_candidate_id
    → pesticides_candidates_master.candidate_id
    → pesticides.id sau khi seed

treatment_rules_candidates_master.disease_code
    → diseases.code
    → diseases.id sau khi seed
```

Mapping vào schema Backend:

| JSON | Backend |
|---|---|
| `disease_code` | lookup `diseases.id` |
| `pesticide_candidate_id` | lookup thuốc đã seed, lấy `pesticides.id` |
| `dose_min`/`dose_max` | `treatment_rules.dose_min`/`dose_max` (rule liều cố định dùng `dose_min = dose_max`) |
| `dose_unit` | `dose_unit` |
| `phi_days` | `phi_days`; không seed nếu đang `null` |
| `application_method`, lượng nước và `blocked_reason` | `notes` nếu rule đã được duyệt |
| `source_ids` | `source`, giữ nguyên các mã nguồn để audit |
| `seed_version` | `version` |
| `seed_authorization` | Ghi vào `notes` để phân biệt phê duyệt chuyên gia với giả định kiểm thử |
| rule còn hiệu lực trong tập seed hiện hành | `active=true` |

Hai rule hiện có (`Rice___Blast` và `Rice___Sheath_blight` với `P-RICE-001`) bị chặn vì chưa có phê duyệt chuyên gia cho mapping từ lớp CV sang đối tượng đăng ký của thuốc.
`blocked_reason`, `verification` và `eligible_for_seed` là hàng rào an toàn, không phải metadata có thể bỏ qua.

Khi nguồn seed thu hồi một rule, Backend đặt rule đó thành `active=false` trước khi đối soát.
Rule chưa được dữ liệu nghiệp vụ tham chiếu sẽ bị xóa; rule đã có diagnosis tham chiếu được giữ để bảo toàn lịch sử nhưng không còn xuất hiện trong lookup mới.
Pesticide mất eligibility chỉ bị xóa khi không còn treatment rule hoặc treatment application tham chiếu.

### Disease management policies

Policy mô tả trường hợp không có thuốc chữa trực tiếp, ưu tiên quản lý vector hoặc chưa xác minh được rule địa phương.
Không chuyển nội dung policy thành treatment rule.

Backend lưu policy trong bảng `disease_management_policies` (một dòng mỗi bệnh: `disease_id`, `management_status`, `direct_treatment_rule_allowed`, `chemical_rule_scope`, `recommended_actions`, `source`, `version`).
Bảng chỉ dùng để chặn/tra cứu; không service nào sinh khuyến nghị hoá chất từ nó.
Chỉ policy có `eligible_for_deterministic_guardrail=true` được seed.

## RAG không thuộc seed nghiệp vụ Backend

`rag/processed/disease_knowledge.json` chứa kiến thức mô tả triệu chứng, sinh học bệnh, phòng ngừa và IPM.
AI dùng file này để tạo embedding và ghi vào AI Vector DB (`documents`, `document_chunks`).
Nội dung RAG chỉ dùng giải thích và bổ sung ngữ cảnh; không được biến thành tên thuốc, liều hoặc PHI.

## Checklist sau khi seed

- Có đúng 6 `diseases.code` active, khớp contract CV và không có mã bị đổi.
- Mọi treatment rule active đều tham chiếu disease và pesticide tồn tại.
- Không có hai rule trùng cùng cặp `(disease_id, pesticide_id)` ngoài chủ đích nghiệp vụ.
- Mọi rule có `dose_min`, `dose_max`, `dose_unit`, `phi_days`, `source` và `version` không rỗng.
- API nội bộ `/internal/v1/diagnosis-context` trả đúng rule theo exact `disease_code`.
- Rule không tồn tại hoặc chưa duyệt phải trả nhánh `review_required`; không fallback sang số liệu do RAG/LLM tạo.
