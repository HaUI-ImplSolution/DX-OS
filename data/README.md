# Dữ liệu seed / DB (`data`)

Thư mục riêng ở gốc repository, ngang hàng `ai/`, `backend/`, `deploy/` — gom mọi dữ liệu liên quan seed/DB vào một chỗ để dễ theo dõi và quản lý, thay vì rải rác trong từng service.
Nội dung là dữ liệu nguồn do AI bàn giao để Backend tham khảo và tạo seed, tách làm hai nhóm độc lập:

- `structured/`: định danh bệnh, thuốc, treatment rule candidate và các chính sách an toàn phục vụ lookup deterministic.
- `rag/`: tri thức giải thích và registry nguồn dành cho AI Vector DB.
  **Không seed vào database nghiệp vụ của Backend.**

## Cách hoạt động

- **Backend** đọc `structured/*.json` để tạo seed cho database nghiệp vụ (`diseases`, `pesticides`, `treatment_rules`).
  Generator `data/scripts/generate_seed_sql.py` sinh `backend/DX-Farm/src/main/resources/data.sql` từ `structured/*.json`; Spring chạy file này idempotent lúc khởi động.
  Quy ước và kế hoạch: [`development/docs/DB_SEED_UNIFICATION_PLAN.md`](../development/docs/DB_SEED_UNIFICATION_PLAN.md).
- **AI** đọc `rag/processed/disease_knowledge.json` và `rag/source_registry.json` qua job ingest (`ai/scripts/ingest_diseases.py`, chạy bằng `python -m scripts.ingest_diseases`) để tạo embedding và ghi vào AI Vector DB (`documents`, `document_chunks`).
  Xem [`ai/README.md`](../ai/README.md) mục "Ingest RAG".
- Trong Docker Compose, `data/` được bind-mount read-only vào container `rag-ingest` tại `/data` (biến `AI_DATA_DIR` trong `deploy/.env`, giá trị mẫu `../data`) — xem [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).
- `data/` ở gốc repository là nguồn canonical duy nhất; không duy trì bản sao trong `ai/.local/data` vì dễ khiến ingest dùng dữ liệu cũ và không thể tái lập từ Git.

## Nguyên tắc bắt buộc

1. `disease_code` phải khớp tuyệt đối với `diseases.code` và nhãn đầu ra CV; không fuzzy match, đổi tên hoặc tự chuẩn hóa.
2. Tên thuốc, liều, đơn vị và PHI là dữ liệu deterministic.
   Không lấy các giá trị này từ RAG và không để LLM sửa.
3. Chỉ seed treatment rule khi có quyết định cho phép rõ ràng trong dữ liệu nguồn.
   `E2E_ASSUMPTION` chỉ cho phép chạy local/demo, không phải phê duyệt chuyên gia và không được dùng cho production.
   Không bulk-seed toàn bộ file có chữ `candidates`.
4. Seed phải idempotent: chạy lại không sinh bản ghi trùng.
5. `source_ids` phải truy được về `rag/source_registry.json` để audit nguồn.

## Danh mục file

| File | Nội dung | Backend xử lý |
|---|---|---|
| `structured/diseases.json` | 38 định danh lớp CV: `code`, tên tiếng Việt và mô tả | Seed/upsert vào `diseases`; đây là nguồn tiện dụng nhất cho danh mục bệnh |
| `structured/class_contract.json` | Contract ánh xạ `class_id` → `model_label` → `disease_code` | Dùng để kiểm tra tính toàn vẹn, không cần tạo bảng riêng |
| `structured/pesticides_candidates_master.json` | 8 danh tính sản phẩm, gồm mã Backend và đơn vị liều | Seed 6 record có `eligible_for_pesticide_seed=true`; 2 record chờ xác minh đơn vị |
| `structured/treatment_rules_candidates_master.json` | 16 rule ứng viên cùng liều, PHI, nguồn và trạng thái cho phép seed | Seed 1 rule mang `E2E_ASSUMPTION`; 15 rule còn lại vẫn bị chặn |
| `structured/disease_management_policies_master.json` | 13 guardrail deterministic cho bệnh không có/không được phép suy ra thuốc chữa | Seed vào `disease_management_policies` (`recommended_actions` nối bằng xuống dòng, `source` là `source_ids` nối bằng dấu phẩy) |
| `structured/pest_management_candidates_master.json` | 1 ứng viên quản lý nhện đỏ | Không seed: vẫn cần chuyên gia và nguồn nhãn chính thức |
| `rag/processed/disease_knowledge.json` | 38 tài liệu tri thức bệnh/IPM dùng để chunk và embedding | AI ingest vào `documents`/`document_chunks`; Backend không seed |
| `rag/source_registry.json` | 41 nguồn và metadata provenance | Dùng audit; AI giữ cùng corpus RAG |

Các báo cáo coverage/demo, review queue, review log và đề xuất schema/decision cũ đã được loại bỏ sau khi nội dung cần thiết được hợp nhất vào master JSON, code và tài liệu hiện hành.
Không tạo lại các artifact dẫn xuất này nếu chưa có consumer hoặc quy trình review cụ thể.

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
- `P-CAND-006` chờ nhãn primary xác nhận đơn vị; `P-CAND-008` chỉ có nguồn secondary cho đơn vị. Cả hai không được seed.
- Sinh lại toàn bộ seed: `python data/scripts/generate_seed_sql.py`; dùng `--check` để xác minh `data.sql` không lệch master.

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
| `seed_version` | `version` |
| `seed_authorization` | Ghi vào `notes` để phân biệt giả định E2E với phê duyệt chuyên gia |
| rule còn hiệu lực trong tập seed hiện hành | `active=true` |

`treatment_rules` có sẵn `dose_min`/`dose_max`.
Rule có khoảng liều map thẳng vào `dose_min`/`dose_max`; rule liều cố định dùng `dose_min = dose_max`.

## Giả định treatment rule cho E2E hiện tại

Nhóm dự án cho phép seed **duy nhất** rule dưới đây để kiểm thử luồng AI → Backend → kỹ sư phê duyệt.
Đây là giả định E2E (`E2E_ASSUMPTION`), không phải kết luận đã được chuyên gia nông nghiệp thẩm định và không phải dữ liệu đủ điều kiện dùng trong production.

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
| Sources | `S27,S30,S29` |
| Authorization | `E2E_ASSUMPTION` — nhóm dự án, ngày 26/09/2026 |
| Version | `2026.09.26-e2e-assumed-v1` |

`S27` và `S29` phục vụ kiểm tra danh mục/quy định Việt Nam; `S30` là hướng dẫn của nhà sản xuất.
Generator đưa trạng thái và ghi chú cho phép E2E vào `notes`, vì vậy người đọc dữ liệu Backend không thể nhầm rule này với rule đã được chuyên gia duyệt.

Mười lăm record còn lại trong `treatment_rules_candidates_master.json` vẫn là dữ liệu nghiên cứu và không được seed.
`blocked_reason`, `verification` và `eligible_for_seed` là hàng rào an toàn, không phải metadata có thể bỏ qua.

Khi nguồn seed thu hồi một rule, Backend đặt rule đó thành `active=false` trước khi đối soát.
Rule chưa được dữ liệu nghiệp vụ tham chiếu sẽ bị xóa; rule đã có diagnosis tham chiếu được giữ để bảo toàn lịch sử nhưng không còn xuất hiện trong lookup mới.
Pesticide mất eligibility chỉ bị xóa khi không còn treatment rule hoặc treatment application tham chiếu.

## Disease management policies

Mười ba policy đều có `eligible_for_deterministic_guardrail=true`.
Chúng mô tả trường hợp không có thuốc chữa trực tiếp, ưu tiên quản lý vector hoặc chưa xác minh được rule địa phương.
Không chuyển nội dung các policy này thành treatment rule.

Backend lưu 13 policy này trong bảng `disease_management_policies` (một dòng mỗi bệnh: `disease_id`, `management_status`, `direct_treatment_rule_allowed`, `chemical_rule_scope`, `recommended_actions`, `source`, `version`).
Bảng chỉ dùng để chặn/tra cứu; không service nào sinh khuyến nghị hoá chất từ nó.

## RAG không thuộc seed nghiệp vụ Backend

`rag/processed/disease_knowledge.json` chứa kiến thức mô tả triệu chứng, sinh học bệnh, phòng ngừa và IPM.
AI dùng file này để tạo embedding và ghi vào AI Vector DB (`documents`, `document_chunks`).
Nội dung RAG chỉ dùng giải thích và bổ sung ngữ cảnh; không được biến thành tên thuốc, liều hoặc PHI.

## Checklist sau khi seed

- Có đúng 38 `diseases.code` thuộc contract CV và không có mã bị đổi.
- Mọi treatment rule active đều tham chiếu disease và pesticide tồn tại.
- Không có hai rule trùng cùng cặp `(disease_id, pesticide_id)` ngoài chủ đích nghiệp vụ.
- Mọi rule có `dose_min`, `dose_max`, `dose_unit`, `phi_days`, `source` và `version` không rỗng.
- API nội bộ `/internal/v1/diagnosis-context` trả đúng rule theo exact `disease_code`.
- Rule không tồn tại hoặc chưa duyệt phải trả nhánh `review_required`; không fallback sang số liệu do RAG/LLM tạo.
