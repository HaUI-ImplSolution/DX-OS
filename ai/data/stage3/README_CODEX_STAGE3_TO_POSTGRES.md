# DX-Farm Stage 3 Reviewed MVP — Hướng dẫn cho Codex

## 0. Mục tiêu của package

Package này là đầu ra của Stage 3 sau khi review kỹ thuật dựa trên:

- Danh mục thuốc BVTV Việt Nam theo Thông tư 75/2025/TT-BNNMT.
- Thông tư 28/2026/TT-BNNMT sửa đổi/bổ sung Thông tư 75.
- Trang hướng dẫn sản phẩm chính thức của nhà sản xuất.
- Nguồn bệnh học dùng để đối chiếu `CV disease_code` với tên bệnh tiếng Việt.

**Đây là dữ liệu cho MVP/demo phần mềm.**
Không coi package này là chữ ký chuyên môn hoặc hướng dẫn phun thuốc ngoài thực địa.

---

# 1. Các file Codex ĐƯỢC phép seed

Chỉ dùng:

```text
final_data/
├── pesticides.json
├── treatment_rules.json
└── disease_management_policies.json
```

`source_registry.json` dùng cho provenance/audit, không bắt buộc tạo table PostgreSQL ở MVP.

Số record hiện tại:

```text
pesticides                 = 4
treatment_rules            = 8
disease_management_policies = 21
```

## TUYỆT ĐỐI KHÔNG seed

```text
hold/
├── treatment_rules_hold.json
└── pesticides_not_used_in_final_rules.json
```

Các file `hold/` chỉ để lưu candidate chưa đủ evidence.

---

# 2. Trạng thái code hiện tại mà package này giả định

Trong bản `ai/` được review:

```text
app/db/models/pesticide.py
app/db/models/treatment_rule.py
app/db/models/treatment_application.py
app/db/repositories/knowledge_repository.py
app/db/repositories/treatment_repository.py
```

### `Pesticide`

Schema hiện tại dùng được:

```python
name
active_ingredient
concentration
notes
```

Không cần redesign để seed Stage 3.

### `TreatmentRule`

Schema hiện tại:

```python
dose: Float
```

**KHÔNG còn phù hợp với data thật.**

Nhãn sản phẩm có các khoảng như:

```text
2.0 - 2.5 L/ha
0.25 - 0.5 L/ha
0.3 - 0.4 L/ha
```

Codex phải migrate thành:

```python
dose_min: Mapped[float]
dose_max: Mapped[float]
dose_unit: Mapped[str]
phi_days: Mapped[int]
```

Giữ:

```python
disease_id
pesticide_id
source
version
notes
```

Không được lấy trung bình khoảng liều để nhét lại vào `dose`.

---

# 3. Model mới bắt buộc: DiseaseManagementPolicy

Tạo:

```text
app/db/models/disease_management_policy.py
```

Schema MVP gợi ý:

```python
class DiseaseManagementPolicy(Base):
    __tablename__ = "disease_management_policies"

    id: UUID primary key

    disease_id: UUID
        FK -> diseases.id
        nullable=False
        unique=True
        index=True

    management_status: String(100)
        nullable=False

    direct_treatment_rule_allowed: Boolean
        nullable=False
        default=False

    chemical_rule_scope: String(255)
        nullable=False

    source: String(255)
        nullable=True

    version: String(50)
        nullable=False
        default="1.0"

    notes: Text
        nullable=True
```

Relationship tới `Disease` là đủ cho MVP.

Đừng tạo table `source_registry` lúc này nếu không thật sự cần.

---

# 4. TreatmentApplication KHÔNG đổi sang dose_min/dose_max

`TreatmentApplication` là sự kiện thực tế.

Nó phải tiếp tục lưu:

```python
dose: float
```

vì đây là **liều thực tế đã áp dụng**.

Phân biệt:

```text
TreatmentRule
= khoảng liều cho phép/được review

TreatmentApplication
= liều thực tế được kỹ sư chọn + nông dân xác nhận APPLIED
```

---

# 5. Sửa treatment_repository.py

Hiện tại code có logic kiểu:

```python
dose=rule.dose
```

Không còn hợp lệ.

Đổi:

```python
def create_treatment_from_rule(
    db,
    crop_season_id,
    rule,
    actual_dose,
    applied_at,
):
```

Trước khi ghi:

```python
if not (rule.dose_min <= actual_dose <= rule.dose_max):
    raise ValueError(...)
```

Sau đó snapshot:

```python
dose=actual_dose
dose_unit=rule.dose_unit
phi_days=rule.phi_days
```

**Không tự chọn `dose_min`, `dose_max`, midpoint hoặc random dose.**

Actual dose phải là input đã được kỹ sư duyệt.

---

# 6. Alembic migration

Sau khi sửa model:

```bash
cd ai
alembic revision --autogenerate -m "update treatment rules and add disease management policies"
```

Kiểm tra file migration trước khi chạy.

Migration phải:

```text
treatment_rules
- remove/replace dose
+ dose_min
+ dose_max

+ create disease_management_policies
```

Sau đó:

```bash
alembic upgrade head
```

Nếu database dev hiện chưa có TreatmentRule data thật thì migration có thể đơn giản hơn.
Không tự drop toàn bộ DB nếu không cần.

---

# 7. Seeder cần tạo

Tạo:

```text
ai/scripts/
├── seed_pesticides.py
├── seed_treatment_rules.py
└── seed_disease_management_policies.py
```

Seeder phải **idempotent**.

## 7.1 Pesticide

Đọc:

```text
data/structured/pesticides.json
```

Logical match gợi ý:

```text
name
+ active_ingredient
+ concentration
```

Nếu đã tồn tại -> update/skip có kiểm soát.

## 7.2 TreatmentRule

Đọc:

```text
data/structured/treatment_rules.json
```

JSON dùng:

```text
disease_code
pesticide_name
```

Seeder lookup:

```text
Disease.code -> Disease.id
Pesticide.name -> Pesticide.id
```

Rồi insert:

```text
dose_min
dose_max
dose_unit
phi_days
source
version
notes
```

Nếu `Disease.code` không tồn tại -> FAIL FAST.

Nếu `Pesticide.name` không tồn tại -> FAIL FAST.

Không tạo Disease/Pesticide giả để né lỗi.

## 7.3 DiseaseManagementPolicy

Đọc:

```text
data/structured/disease_management_policies.json
```

Lookup `Disease.code`.

Một disease chỉ nên có 1 policy active trong MVP.

---

# 8. Copy data vào repo

Package này đang có:

```text
final_data/pesticides.json
final_data/treatment_rules.json
final_data/disease_management_policies.json
```

Codex copy thành:

```text
ai/data/structured/pesticides.json
ai/data/structured/treatment_rules.json
ai/data/structured/disease_management_policies.json
```

Có thể lưu:

```text
final_data/source_registry.json
```

thành:

```text
ai/data/rag/source_registry.json
```

nhưng cần merge/deduplicate bằng `source_id`, không ghi đè mù nếu repo đã có source mới hơn.

---

# 9. Repository API cần bổ sung

Trong:

```text
app/db/repositories/knowledge_repository.py
```

giữ:

```python
get_treatment_rules_by_disease_code(...)
```

và thêm:

```python
get_management_policy_by_disease_code(...)
```

Logic structured lookup cấp service/tool:

```text
Disease code
    |
    +--> có TreatmentRule final?
    |       |
    |       yes -> return rules
    |
    +--> không có rule
            |
            +--> có DiseaseManagementPolicy?
                    |
                    yes -> return policy
                    |
                    no -> fail-safe: RAG only + engineer review
```

LLM không được quyết định rằng "không có rule" đồng nghĩa với "tự đề xuất thuốc".

---

# 10. Guardrail bắt buộc cho LangGraph sau này

Luồng phải theo:

```text
CV disease_code
      |
      v
Disease lookup
      |
      v
Structured lookup
      |
      +--> TreatmentRule tồn tại
      |        |
      |        v
      |    đưa facts/rule cho LLM diễn đạt
      |
      +--> Policy direct_treatment_rule_allowed = false
               |
               v
          CHẶN chemical recommendation
               |
               v
          RAG explanation / engineer review
```

RAG không được override Structured Knowledge.

---

# 11. Các rule đã promote trong package

Package promote **8 rule**, cho **5 CV disease class**.

Đây là deliberate MVP scope, không phải cố phủ đủ 38 class.

Các class được promote:

```text
Corn_(maize)___Northern_Leaf_Blight
Potato___Early_blight
Potato___Late_blight
Tomato___Early_blight
Tomato___Late_blight
```

Các candidate còn lại nằm `hold/`.

---

# 12. Test sau khi seed

Codex phải thêm test tối thiểu:

### Test 1 — exact treatment

Input:

```text
Tomato___Late_blight
```

Kỳ vọng:

```text
>= 1 TreatmentRule
Pesticide được join thành công
dose_min <= dose_max
phi_days > 0
```

### Test 2 — another exact treatment

Input:

```text
Corn_(maize)___Northern_Leaf_Blight
```

Kỳ vọng:

```text
Amistar Top 325SC
0.25 <= dose <= 0.5 L/ha
PHI = 10
```

Không hard-code kết quả vào repository; test đọc DB.

### Test 3 — blocked class

Input ví dụ:

```text
Tomato___Target_Spot
```

Kỳ vọng:

```text
TreatmentRule final = none
DiseaseManagementPolicy exists
direct_treatment_rule_allowed = false
```

### Test 4 — virus

```text
Tomato___Tomato_mosaic_virus
```

Kỳ vọng:

```text
NO_CHEMICAL_CONTROL
```

### Test 5 — actual applied dose

Với rule:

```text
dose_min = 2.0
dose_max = 2.5
```

- `actual_dose=2.2` -> accepted
- `actual_dose=3.0` -> rejected

---

# 13. Lệnh chạy mong muốn

Từ `ai/`:

```bash
python -m scripts.seed_pesticides
python -m scripts.seed_treatment_rules
python -m scripts.seed_disease_management_policies
```

Sau đó chạy pytest.

Nếu project thay đổi module path sau refactor thì giữ nguyên ý nghĩa, không bắt buộc đúng tên lệnh trên.

---

# 14. Những việc Codex KHÔNG được làm

Không:

```text
- seed file trong hold/
- tự thay đổi disease_code
- tự map broad target "đốm lá" sang một disease cụ thể
- tự điền missing PHI
- tự chọn midpoint của dose range
- cho LLM tính hoặc bịa dose/PHI
- sửa TreatmentApplication thành dose_min/dose_max
- xóa disease data hiện tại để seed lại từ đầu
- hard-code UUID trong JSON
```

---

# 15. Definition of Done

Stage này hoàn tất khi:

```text
[ ] Alembic migration chạy thành công
[ ] 4 pesticides được seed
[ ] 8 TreatmentRules được seed
[ ] 21 DiseaseManagementPolicies được seed
[ ] Seeder chạy lần 2 không duplicate
[ ] Exact TreatmentRule lookup pass
[ ] Safe policy lookup pass
[ ] actual_dose validation pass
[ ] pytest pass
```

Sau đó Structured Data side đủ để LangGraph nối vào.
