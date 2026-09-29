# Kế hoạch thống nhất luồng seed dữ liệu và quản lý DB

> **Trạng thái:** Đã triển khai và xác minh generator, entity `disease_management_policies`, quy ước seed và hội tụ an toàn. Một treatment rule được nhóm dự án cho phép bằng giả định E2E; 15 rule còn lại vẫn bị chặn.
>
> **Cập nhật:** 28/09/2026.
>
> **Phạm vi:** Seed dữ liệu nghiệp vụ (disease/pesticide/treatment rule/disease management policy) từ thư mục `data/` (gốc repository) vào Backend DB một cách idempotent, và thống nhất quy ước quản lý DB.

## 1. Giả định đã chốt

- `data/structured/*.json` ở gốc repository là **nguồn seed nghiệp vụ duy nhất**.
  `data/rag/*` chỉ phục vụ AI Vector DB và audit nguồn, không được seed vào Backend DB.
  `data.sql` hiện được sinh hoàn toàn từ dữ liệu có eligibility và quyết định cho phép seed trong `data/structured/`.
- **Khi data và schema lệch nhau:** ưu tiên dữ liệu **đã được duyệt** (`eligible_for_seed`/`eligible_for_pesticide_seed`/`eligible_for_deterministic_guardrail` = `true`) — sửa schema cho khớp dữ liệu đó.
  Dữ liệu **chưa duyệt** thì không kéo theo sửa schema; nó đứng yên chờ duyệt, không seed và không ép schema thay đổi theo.
- `data/` không phải nguồn đọc trực tiếp lúc runtime của Backend — generator vẫn chạy dev-time, cách Spring seed lúc start không đổi.

## 2. Hiện trạng

- `ddl-auto: update` + `sql.init.mode: always` — mỗi lần start, Hibernate cập nhật schema rồi `data.sql` chạy lại (tự né trùng bằng `ON CONFLICT` trên khoá duy nhất của DB).
  Không có Flyway/Liquibase.
- Hai cơ chế seed hiện hành:

  | Loại dữ liệu | Cơ chế |
  |---|---|
  | Nghiệp vụ (JPA quản lý: `diseases`, `pesticides`, `treatment_rules`, `disease_management_policies`) | `data.sql` sinh từ `data/structured/` |
  | Hạ tầng Postgres (role, database, extension) | `CommandLineRunner` (`KeycloakDatabaseSeeder`, `MattermostDatabaseSeeder`, `SupersetReadOnlyRoleSeeder`, `AiVectorDatabaseSeeder`) |

- Backend đã có entity/bảng `disease_management_policies` và seed đủ 13 guardrail.
  Repository hiện chưa tham gia decision flow runtime; nối policy vào internal API/AI là thay đổi contract riêng, không thuộc lượt thống nhất seed này.

## 3. Quyết định kiến trúc

Cơ chế hiện hành giữ việc seed idempotent lúc khởi động qua `data.sql` của Spring.
Nguồn sinh `data.sql` được thống nhất như sau:

```
data/ (JSON, có cờ eligibility, gốc repository)
   → generator (script dev-time, chạy tay khi có thay đổi)
   → data.sql (commit, review qua PR)
   → Spring seed idempotent lúc start (không đổi)
```

Không đọc JSON trực tiếp lúc runtime của Backend — diff SQL trong PR dễ audit hơn logic ẩn trong code.

## 4. Các thành phần đã triển khai

**Đối soát eligibility**
Đã rà 16 treatment rule candidate trong `treatment_rules_candidates_master.json`; chỉ rule có `eligible_for_seed=true` và `seed_authorization` hợp lệ được đưa vào seed.
Nhóm dự án đã cho phép `TR-CAND-004` ở trạng thái `E2E_ASSUMPTION` để kiểm thử local/demo; trạng thái này không thay thế phê duyệt chuyên gia và không hợp lệ cho production.
- [x] Có danh sách rule bị chặn kèm lý do: 15 rule không seed; 1 rule được phép riêng cho E2E.
- [x] Quyết định seed/không seed và phạm vi sử dụng được lưu ngay trong dữ liệu nguồn, README và diff để review.

**Entity `disease_management_policies`**
Field theo schema Backend hiện hành:
- `disease_id` (FK → `Disease`), `management_status`, `direct_treatment_rule_allowed` (boolean), `chemical_rule_scope`, `recommended_actions` (text), `source`, `version` — cùng phong cách các entity hiện có (`Disease`, `Pesticide`, `TreatmentRule`).
- Cả 13 policy được seed qua generator.
- [x] Entity/bảng tồn tại, đúng field trên.
- [x] 13 policy được seed qua generator, không seed tay riêng.
- [x] Bảng chỉ dùng để chặn/tra cứu — không service nào tự sinh khuyến nghị hoá chất từ nó.

**Generator sinh `data.sql`**
Đọc `diseases.json`, `pesticides_candidates_master.json` (lọc `eligible_for_pesticide_seed`), `treatment_rules_candidates_master.json` (lọc theo eligibility đã đối soát), `disease_management_policies_master.json`.
Output deterministic, đúng cấu trúc bảng thật (`dose_min`/`dose_max`).
- [x] Chạy độc lập, không cần app đang chạy; chạy 2 lần cho ra cùng kết quả.
- [x] Không giữ lại phần dữ liệu cũ nào trong `data.sql` mà không có nguồn tương ứng trong `data/`.
- [x] Rule mất eligibility được vô hiệu hóa; chỉ xóa khi không có diagnosis tham chiếu. Pesticide stale chỉ xóa khi không còn rule/application tham chiếu.

**Quy ước 2 cơ chế seed**
Ghi rõ: dữ liệu JPA → `data.sql` (qua generator); hạ tầng Postgres → `CommandLineRunner` kiểu `*Seeder`.
Đặt vào README/doc để không phát sinh cơ chế thứ ba.
- [x] Có đoạn quy ước bằng văn bản, dẫn chiếu được.

**Test & regression**
- [x] Start 2 lần liên tiếp không trùng dữ liệu; start trên DB rỗng seed đủ.
- [x] E2E ba role qua API: AI thật tạo diagnosis theo nhánh `review_required`; nhánh treatment dùng chẩn đoán thủ công để kiểm tra duyệt, giới hạn liều và khóa PHI.
  Kiểm tra này xác nhận luồng kỹ thuật, không đánh giá độ chính xác CV/RAG/LLM hoặc nhánh AI tự chọn treatment rule.

## 5. Bất biến

- Không seed rule/policy nếu thiếu quyết định cho phép rõ ràng trong nguồn.
- `E2E_ASSUMPTION` chỉ dành cho local/demo; không được diễn giải thành phê duyệt chuyên gia hoặc dữ liệu production.
- `data/` không phải nguồn seed runtime của Backend — mọi giá trị qua generator + review PR.
- Không thêm Flyway/Liquibase trong lượt này trừ khi có quyết định riêng.
- Không đổi các `*Seeder` hạ tầng hiện có (đã đúng quy ước ở trên).

## 6. Definition of done

`data.sql` được sinh hoàn toàn từ `data/` qua generator; có quy ước rõ ràng cho 2 cơ chế seed; entity `disease_management_policies` tồn tại và seed đủ 13 guardrail; toàn bộ regression trong phạm vi seed pass.
