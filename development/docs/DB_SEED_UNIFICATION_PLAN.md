# Kế hoạch thống nhất luồng seed dữ liệu và quản lý DB

> **Trạng thái:** Backlog, chưa bắt đầu.
>
> **Cập nhật:** 24/09/2026.
>
> **Phạm vi:** Seed dữ liệu nghiệp vụ (disease/pesticide/treatment rule/disease management policy) từ thư mục `data/` (gốc repository) vào Backend DB một cách idempotent, và thống nhất quy ước quản lý DB.

## 1. Giả định đã chốt

- `data/structured/*.json` (gốc repository, gồm `structured/` và `rag/`) là **nguồn seed duy nhất**.
  Nội dung hiện có trong `data.sql` không còn giá trị tham chiếu, sẽ được thay thế hoàn toàn.
- **Khi data và schema lệch nhau:** ưu tiên dữ liệu **đã được duyệt** (`eligible_for_seed`/`eligible_for_pesticide_seed`/`eligible_for_deterministic_guardrail` = `true`) — sửa schema cho khớp dữ liệu đó.
  Dữ liệu **chưa duyệt** thì không kéo theo sửa schema; nó đứng yên chờ duyệt, không seed và không ép schema thay đổi theo.
- `data/` không phải nguồn đọc trực tiếp lúc runtime của Backend — generator vẫn chạy dev-time, cách Spring seed lúc start không đổi.

## 2. Hiện trạng

- `ddl-auto: update` + `sql.init.mode: always` — mỗi lần start, Hibernate cập nhật schema rồi `data.sql` chạy lại (tự né trùng bằng `ON CONFLICT`/`NOT EXISTS`).
  Không có Flyway/Liquibase.
- Hai cơ chế seed song song, chưa có quy ước:

  | Loại dữ liệu | Cơ chế |
  |---|---|
  | Nghiệp vụ (JPA quản lý: `diseases`, `pesticides`, `treatment_rules`) | `data.sql` |
  | Hạ tầng Postgres (role, database, extension) | `CommandLineRunner` (`KeycloakDatabaseSeeder`, `MattermostDatabaseSeeder`, `SupersetReadOnlyRoleSeeder`, `AiVectorDatabaseSeeder`) |

- `data/` cần entity `disease_management_policy` (13 bản ghi, đều `eligible_for_deterministic_guardrail=true`), nhưng Backend chưa có entity/bảng này.
  `structured_decision_policy_v1.json` đặt việc kiểm tra `direct_treatment_rule_allowed` ở priority 2 (ngay sau treatment rule) — thiếu bảng này là khoảng trống thật trong chuỗi quyết định.

## 3. Quyết định kiến trúc

Giữ nguyên cơ chế "seed idempotent lúc start" đã có (`data.sql` qua Spring).
Chỉ thay đổi **nguồn sinh ra `data.sql`**:

```
data/ (JSON, có cờ eligibility, gốc repository)
   → generator (script dev-time, chạy tay khi có thay đổi)
   → data.sql (commit, review qua PR)
   → Spring seed idempotent lúc start (không đổi)
```

Không đọc JSON trực tiếp lúc runtime của Backend — diff SQL trong PR dễ audit hơn logic ẩn trong code.

## 4. Các thành phần cần triển khai

**Đối soát eligibility**
Rà 16 treatment rule candidate trong `treatment_rules_candidates_master.json`, chỉ giữ lại rule có `eligible_for_seed=true` để seed.
Không tự ý lật cờ mà không có xác nhận từ người duyệt dữ liệu nghiệp vụ.
- [ ] Có danh sách rule nào bị chặn kèm lý do.
- [ ] Quyết định seed/không seed từng rule được ghi lại trong PR.

**Entity `disease_management_policies`**
Field theo `schema_recommendation_v2.json`:
- `disease_id` (FK → `Disease`), `management_status`, `direct_treatment_rule_allowed` (boolean), `chemical_rule_scope`, `recommended_actions` (text), `source`, `version` — cùng phong cách các entity hiện có (`Disease`, `Pesticide`, `TreatmentRule`).
- Seed cả 13 policy qua generator.
- [ ] Entity/bảng tồn tại, đúng field trên.
- [ ] 13 policy được seed qua generator, không seed tay riêng.
- [ ] Bảng chỉ dùng để chặn/tra cứu — không service nào tự sinh khuyến nghị hoá chất từ nó.

**Generator sinh `data.sql`**
Đọc `diseases.json`, `pesticides_candidates_master.json` (lọc `eligible_for_pesticide_seed`), `treatment_rules_candidates_master.json` (lọc theo eligibility đã đối soát), `disease_management_policies_master.json`.
Output deterministic, đúng cấu trúc bảng thật (`dose_min`/`dose_max`).
- [ ] Chạy độc lập, không cần app đang chạy; chạy 2 lần cho ra cùng kết quả.
- [ ] Không giữ lại phần dữ liệu cũ nào trong `data.sql` mà không có nguồn tương ứng trong `data/`.

**Quy ước 2 cơ chế seed**
Ghi rõ: dữ liệu JPA → `data.sql` (qua generator); hạ tầng Postgres → `CommandLineRunner` kiểu `*Seeder`.
Đặt vào README/doc để không phát sinh cơ chế thứ ba.
- [ ] Có đoạn quy ước bằng văn bản, dẫn chiếu được.

**Test & regression**
- [ ] Generator có test tự động (parse/lọc/sinh SQL).
- [ ] Start 2 lần liên tiếp không trùng dữ liệu; start trên DB rỗng seed đủ.
- [ ] E2E ba role (FARMER/ENGINEER/ADMIN) không regression.

## 5. Bất biến

- Không seed rule/policy chưa được duyệt rõ ràng.
- `data/` không phải nguồn seed runtime của Backend — mọi giá trị qua generator + review PR.
- Không thêm Flyway/Liquibase trong lượt này trừ khi có quyết định riêng.
- Không đổi các `*Seeder` hạ tầng hiện có (đã đúng quy ước ở trên).

## 6. Definition of done

`data.sql` được sinh hoàn toàn từ `data/` qua generator; có quy ước rõ ràng cho 2 cơ chế seed; entity `disease_management_policies` tồn tại và seed đủ 13 guardrail; toàn bộ regression pass.
