# PROMPT CHO CODEX — DX-Farm Stage 8 → Stage 10 (MVP End-to-End)

Bạn đang làm việc trong repository **DX-Farm**, tập trung vào thư mục `ai/`.

Mục tiêu của task này là hoàn thiện phần từ **Stage 8 đến Stage 10**, tức là đưa toàn bộ các module đã làm trước đó thành một **MVP chạy end-to-end**:

```text
Stage 8  = LangGraph Spine Flow
Stage 9  = Human-in-the-loop / APPROVED / APPLIED / PHI
Stage 10 = End-to-End Demo Hardening
```

Task này **không được bắt đầu bằng cách viết LangGraph ngay**. Trước tiên phải preflight-check toàn bộ output Stage 1 → Stage 7. Nếu dependency nào chưa đạt checklist thì sửa đúng phần đó trước, chạy test lại, rồi mới tiếp tục.

---

# 0. HARD GUARDRAILS

Kiến trúc DX-Farm:

```text
CV = nhận diện
PostgreSQL = sự thật nghiệp vụ
Structured Knowledge = TreatmentRule + DiseaseManagementPolicy
RAG = tài liệu giải thích có nguồn
LLM = diễn đạt
LangGraph = orchestration
Human = approval
APPLIED = mốc bắt đầu PHI
```

Không được phá các quy tắc:

```text
LLM KHÔNG tự sinh pesticide / active ingredient / dose / PHI.
RAG KHÔNG override Structured Knowledge.
APPROVED != APPLIED.
PHI chỉ bắt đầu khi status = APPLIED.
Runtime KHÔNG dùng hold candidate.
Cross-module identity = disease_code canonical.
```

---

# 1. INSPECT REPO TRƯỚC KHI CODE

Đọc toàn bộ khu vực liên quan:

```text
ai/app/cv/
ai/app/db/models/
ai/app/db/repositories/
ai/app/db/services/        nếu có
ai/app/rag/
ai/app/graph/
ai/app/llm/                nếu có
ai/app/api/                nếu có
ai/data/
ai/scripts/
ai/tests/
ai/migrations/
```

Tìm rõ các thành phần hiện có:

```text
FarmState
LangGraph skeleton
CV client / vision tool
field-context lookup
TreatmentRule lookup
DiseaseManagementPolicy lookup
RAG retriever/service
Qwen/LLM service
Diagnosis persistence
TreatmentApplication service
PHI/harvest lock logic
approval/rejection persistence
```

Trước khi sửa code, in kế hoạch:

```text
PRECHECK PLAN

Existing modules:
- ...

Missing modules:
- ...

Files to modify:
- ...

Files to create:
- ...

Potential blockers:
- ...
```

---

# 2. PREFLIGHT CHECK — STAGE 1 → STAGE 7

## Stage 1 — Disease Knowledge Base

Expected:

```text
38 canonical disease_code
```

Checklist:

```text
[ ] Disease table có canonical codes cần thiết.
[ ] Không duplicate disease_code.
[ ] Healthy classes tồn tại.
[ ] disease_code dùng làm cross-module identity.
```

Không lookup bằng Vietnamese name, class_id hoặc hard-coded UUID.

## Stage 2 — Structured staging

Checklist:

```text
[ ] Candidate/hold data tách khỏi final data.
[ ] Runtime không đọc hold candidate.
```

## Stage 3 — Reviewed final MVP data

Expected:

```text
Pesticides                = 4
TreatmentRules            = 8
DiseaseManagementPolicies = 21
```

Checklist:

```text
[ ] 4 final Pesticides.
[ ] 8 final TreatmentRules.
[ ] 21 final DiseaseManagementPolicies.
[ ] hold data không nằm production seed path.
```

## Stage 4 — DB schema

Expected TreatmentRule:

```text
dose_min
dose_max
dose_unit
phi_days
```

Expected TreatmentApplication:

```text
dose   # actual applied dose
```

Expected model:

```text
DiseaseManagementPolicy
```

Checklist:

```text
[ ] TreatmentRule không còn phụ thuộc field dose cũ.
[ ] dose_min <= dose_max.
[ ] TreatmentApplication vẫn lưu actual dose.
[ ] DiseaseManagementPolicy table tồn tại.
[ ] Alembic head đã chạy.
[ ] Không có migration pending.
```

## Stage 5 — Seed + deterministic lookup

Repository/service tối thiểu:

```python
get_treatment_rules_by_disease_code(...)
get_management_policy_by_disease_code(...)
```

Verify:

```text
[ ] Tomato___Late_blight -> >= 1 TreatmentRule.
[ ] Corn_(maize)___Northern_Leaf_Blight -> TreatmentRule.
[ ] Tomato___Target_Spot -> no final TreatmentRule + safe policy.
[ ] Tomato___Tomato_mosaic_virus -> NO_CHEMICAL_CONTROL.
[ ] seed idempotent.
[ ] actual_dose range validation tồn tại.
```

## Stage 6 — CV ↔ Data integration

Expected CV minimum contract:

```json
{
  "disease_code": "Tomato___Late_blight",
  "confidence": 0.95,
  "model_name": "EfficientNet-B4",
  "model_version": "v1.0"
}
```

Checklist:

```text
[ ] CV health endpoint pass.
[ ] predict endpoint pass.
[ ] 38 CV class codes match Disease.code.
[ ] sample image -> canonical disease_code.
[ ] Diagnosis persistence chạy.
[ ] confidence/model metadata lưu được.
[ ] structured lookup chạy sau CV.
```

Không hard-code model logic trong LangGraph.

## Stage 7 — RAG production-ready

Expected reusable contract gần giống:

```python
retrieve_knowledge(query, disease_code=None, top_k=3)
```

Expected result:

```json
[
  {
    "content": "...",
    "disease_code": "...",
    "source_ids": ["S05"],
    "score": 0.81
  }
]
```

Checklist:

```text
[ ] rich disease_knowledge đã ingest.
[ ] source metadata tồn tại.
[ ] disease_code metadata tồn tại.
[ ] top_k hoạt động.
[ ] sample query relevant.
[ ] re-ingest không duplicate bừa.
[ ] RAG không override structured facts.
```

## PRECHECK GATE

In report:

```text
PRECHECK RESULT

Stage 1: PASS / FAIL
Stage 2: PASS / FAIL
Stage 3: PASS / FAIL
Stage 4: PASS / FAIL
Stage 5: PASS / FAIL
Stage 6: PASS / FAIL
Stage 7: PASS / FAIL

BLOCKERS:
- ...

READY FOR STAGE 8:
YES / NO
```

Nếu `NO`, sửa blocker trước.

---

# STAGE 8 — LANGGRAPH SPINE FLOW

## 3. Mục tiêu

LangGraph chỉ orchestration. Không duplicate business logic trong graph.

Graph phải consume service/tool đã có:

```text
CV
DB
Structured Knowledge
RAG
LLM
Persistence
```

## 4. Target graph

```text
START
  |
  v
Resolve Request
  |
  v
CV Diagnosis
  |
  v
Load Field Context
  |
  v
Treatment / Policy Lookup
  |
  v
Retrieve Knowledge
  |
  v
Generate Recommendation
  |
  v
Engineer Approval
  |
  +---------- REJECT
  |
  +---------- APPROVE
```

APPROVE chưa tạo TreatmentApplication ngay. APPROVE chỉ chuyển workflow sang chờ `APPLIED`.

## 5. FarmState

Nếu đã có FarmState thì giữ tối giản. State không thay DB.

Suggested fields:

```text
request_id
parcel_id
crop_season_id
image_ref
cv_prediction
disease_code
confidence
field_context
treatment_rules
management_policy
rag_chunks
recommendation
approval_status
approval_reason
selected_rule_id
approved_dose
application_status
applied_at
errors
```

Không bắt buộc đúng tên nếu repo có convention khác.

## 6. Node — resolve_request

Input:

```text
request
parcel_id / crop_season_id
image reference
```

Output:

```text
normalized request context
validated identifiers
```

Checklist:

```text
[ ] required IDs validate.
[ ] invalid request fail rõ.
[ ] không xử lý CV ở node này.
```

## 7. Node — cv_diagnosis

Input:

```text
image_ref
```

Call CV service / vision tool.

Output:

```text
disease_code
confidence
model_name
model_version
```

Persist `DiseaseReport/Diagnosis` theo schema hiện tại.

Checklist:

```text
[ ] gọi đúng CV contract.
[ ] không load model trực tiếp trong graph.
[ ] disease_code canonical.
[ ] confidence persisted.
[ ] model metadata persisted.
[ ] CV failure được handle.
```

## 8. Node — load_field_context

Input:

```text
parcel_id
crop_season_id
```

Output tối thiểu:

```text
Parcel
CropSeason
crop
season status
relevant treatment history
harvest lock state nếu có
```

Checklist:

```text
[ ] lookup DB thật.
[ ] missing crop season handled.
[ ] không để LLM tự tạo field context.
```

## 9. Node — treatment_lookup

Input:

```text
disease_code
field/crop context
```

Logic:

```text
TreatmentRule exists?
    |
    +-- yes --> treatment_rules
    |
    +-- no --> DiseaseManagementPolicy
```

Nếu cả hai không có:

```text
safe fallback
RAG explanation only
engineer review required
```

Output contract gợi ý:

```json
{"path":"TREATMENT_RULE","rules":[...]}
```

hoặc:

```json
{"path":"MANAGEMENT_POLICY","policy":{...}}
```

hoặc:

```json
{"path":"REVIEW_REQUIRED"}
```

Checklist:

```text
[ ] exact disease_code lookup.
[ ] TreatmentRule ưu tiên trước policy.
[ ] policy path chạy đúng.
[ ] không fuzzy match.
[ ] không gọi LLM để tìm thuốc.
```

## 10. Node — retrieve_knowledge

Input:

```text
disease_code
user/farmer question
structured path
```

Output:

```text
rag_chunks
source_ids
scores
```

Checklist:

```text
[ ] disease-aware retrieval.
[ ] provenance giữ nguyên.
[ ] top_k bounded.
[ ] structured facts không bị RAG override.
```

## 11. Node — generate_recommendation

Input:

```text
CV diagnosis
field context
TreatmentRule OR Policy
RAG chunks
```

LLM chỉ:

```text
explain
summarize
farmer-friendly wording
mention uncertainty
include sources nếu UI support
```

Nếu TreatmentRule path:

```text
Use ONLY provided product/dose/PHI.
```

Nếu Policy path:

```text
Do NOT propose chemical treatment.
```

Nếu Review-required:

```text
Explain disease context and request expert review.
```

Output structured object, ví dụ:

```json
{
  "summary": "...",
  "diagnosis": "...",
  "recommended_actions": [],
  "structured_rule_refs": [],
  "source_ids": [],
  "requires_engineer_approval": true
}
```

Checklist:

```text
[ ] LLM không thay dose.
[ ] LLM không thay PHI.
[ ] policy-only path không có chemical product.
[ ] provenance còn giữ.
[ ] output parse được.
```

## 12. Node — engineer_approval

Input:

```text
recommendation
structured rule/policy
```

States:

```text
PENDING
APPROVED
REJECTED
```

Nếu có LangGraph interrupt/checkpoint thì dùng theo style repo. Nếu chưa có UI, expose API/service để simulate approval.

Checklist:

```text
[ ] PENDING hợp lệ.
[ ] APPROVED != APPLIED.
[ ] REJECTED cần reason.
[ ] selected TreatmentRule rõ nếu có nhiều rules.
[ ] approved/planned dose không tự chọn.
```

## Stage 8 Output

Stage 8 DONE khi chạy được:

```text
request
-> CV
-> field context
-> structured lookup
-> RAG
-> LLM recommendation
-> PENDING APPROVAL
```

và route APPROVE/REJECT hoạt động.

## Stage 8 Checklist

```text
[ ] Graph compile.
[ ] State schema ổn định.
[ ] resolve_request pass.
[ ] CV node pass.
[ ] Diagnosis persistence pass.
[ ] field context pass.
[ ] TreatmentRule path pass.
[ ] Policy path pass.
[ ] Review-required path pass.
[ ] RAG node pass.
[ ] LLM generation pass.
[ ] no structured hallucination.
[ ] approval checkpoint pass.
[ ] reject route pass.
[ ] error handling pass.
[ ] module tests pass.
```

---

# STAGE 9 — HUMAN-IN-THE-LOOP / APPLIED / PHI

## 13. Mục tiêu

Hoàn thiện business flow sau recommendation.

Hard rule:

```text
APPROVED != APPLIED
```

## 14. REJECT flow

Input:

```text
case/recommendation id
engineer decision
reason
```

Output:

```text
status = REJECTED
reason persisted
case closed
no TreatmentApplication
no PHI lock
```

Checklist:

```text
[ ] reason handled/persisted.
[ ] no TreatmentApplication.
[ ] no harvest lock.
[ ] audit trail có decision.
```

## 15. APPROVE flow

Input:

```text
case/recommendation
selected TreatmentRule nếu có
approved/planned dose
```

Output:

```text
status = APPROVED
selected rule stored
planned/approved dose stored nếu schema support
waiting APPLIED
```

Checklist:

```text
[ ] selected rule thuộc đúng diagnosis/path.
[ ] planned dose nằm trong range.
[ ] APPROVED persisted.
[ ] chưa tạo TreatmentApplication.
[ ] chưa tạo PHI lock.
```

## 16. APPLIED flow

Input:

```text
approved case
actual_dose
applied_at
application confirmation
```

Validation:

```text
rule.dose_min <= actual_dose <= rule.dose_max
```

Nếu out-of-range:

```text
reject request / require re-approval
```

Không clamp hoặc tự sửa dose.

Create `TreatmentApplication` và snapshot:

```text
pesticide
actual dose
dose unit
phi_days
applied_at
rule/version nếu schema support
```

PHI:

```text
harvest_locked_until = applied_at + phi_days
```

Chỉ tại APPLIED.

Checklist:

```text
[ ] chỉ APPROVED case mới APPLIED.
[ ] actual_dose bắt buộc.
[ ] dose range validation.
[ ] applied_at bắt buộc.
[ ] TreatmentApplication tạo thành công.
[ ] snapshot rule facts.
[ ] PHI starts from applied_at.
[ ] harvest_locked_until đúng.
[ ] duplicate APPLIED retry safe.
```

## 17. Policy-only case

Nếu:

```text
direct_treatment_rule_allowed = false
```

thì không tạo chemical TreatmentApplication từ policy.

Checklist:

```text
[ ] không pesticide/dose/PHI invented.
[ ] approval chỉ áp dụng cho management recommendation nếu cần.
```

## 18. PHI lock behavior

Tối thiểu cần service/query tương đương:

```python
is_harvest_locked(crop_season_id)
get_harvest_lock_until(crop_season_id)
```

Nếu nhiều application có PHI overlap, lock hiệu lực nên phản ánh mốc an toàn muộn nhất theo business logic hiện tại.

Checklist:

```text
[ ] lock query gọi được.
[ ] multiple applications không làm lock bị rút ngắn sai.
```

## Stage 9 Output

```text
Recommendation
-> APPROVE / REJECT
-> APPLIED confirmation
-> TreatmentApplication
-> PHI lock
```

## Stage 9 Checklist

```text
[ ] REJECT persisted.
[ ] REJECT reason persisted.
[ ] APPROVED persisted.
[ ] APPROVED != APPLIED.
[ ] APPROVED chưa tạo PHI.
[ ] APPLIED chỉ từ approved case.
[ ] actual_dose validation.
[ ] TreatmentApplication created.
[ ] PHI snapshot.
[ ] harvest lock calculated.
[ ] policy-only path safe.
[ ] duplicate/retry safe.
[ ] audit/history query works.
[ ] tests pass.
```

---

# STAGE 10 — END-TO-END DEMO HARDENING

## 19. Mục tiêu

Tạo MVP demo ổn định, reproducible, debug được. Không cần production-scale.

## 20. Demo fixtures

Chuẩn bị tối thiểu:

```text
1-2 Parcel
1-2 CropSeason
known sample image cho TreatmentRule path
known sample/policy class cho Policy path
engineer demo user nếu auth cần
farmer demo user nếu auth cần
```

## 21. Scenario A — Chemical Treatment path

Recommended known class:

```text
Tomato___Late_blight
```

Expected:

```text
1. farmer uploads image
2. CV returns disease_code
3. Diagnosis saved
4. field context loaded
5. TreatmentRule found
6. RAG evidence retrieved
7. LLM recommendation generated
8. engineer APPROVES one rule
9. no PHI yet
10. APPLIED confirmed with actual_dose
11. TreatmentApplication created
12. PHI lock created
13. history query shows event
```

Checklist:

```text
[ ] all 13 steps pass.
[ ] no manual DB edit during demo.
[ ] real CV service used at least once in final smoke test.
```

## 22. Scenario B — Safe Policy path

Recommended:

```text
Tomato___Tomato_mosaic_virus
```

Expected:

```text
1. disease_code resolved
2. no TreatmentRule
3. DiseaseManagementPolicy found
4. NO_CHEMICAL_CONTROL
5. RAG explanation
6. LLM does not invent pesticide
7. engineer sees safe management recommendation
```

Checklist:

```text
[ ] no chemical product.
[ ] no dose.
[ ] no PHI invented.
[ ] policy status visible.
```

## 23. Scenario C — Reject path

```text
recommendation
-> engineer REJECT
-> reason persisted
-> no TreatmentApplication
-> no PHI
```

Checklist:

```text
[ ] reject reason visible in audit/history.
[ ] rejected case không APPLIED được.
```

## 24. Scenario D — Out-of-range dose

Use known range, ví dụ:

```text
2.0 - 2.5 L/ha
```

Try:

```text
3.0 L/ha
```

Expected:

```text
server-side validation error
no TreatmentApplication
no PHI
```

## 25. Scenario E — Low confidence CV

Threshold phải lấy từ CV validation/config, không đoán trong Stage 10.

Expected:

```text
low confidence
-> require review / confirmation
-> không silently proceed như high-confidence diagnosis
```

Checklist:

```text
[ ] threshold configurable.
[ ] threshold documented.
[ ] low-confidence path tested.
```

## 26. API/service integration

Expose entrypoint rõ để demo, ưu tiên API nếu backend đã có pattern.

Expected operations:

```text
create diagnosis request
get recommendation
approve
reject
confirm applied
query status/history
```

Không tạo duplicate API architecture nếu đã có equivalent.

## 27. Error handling

Handle tối thiểu:

```text
CV unavailable
invalid image
unknown disease_code
missing crop season
no structured rule/policy
RAG unavailable
LLM unavailable
approval invalid
actual dose out of range
duplicate APPLIED
database error
```

API không expose raw secret/path/traceback.

## 28. Observability

Log tối thiểu:

```text
request_id
disease_code
CV confidence
structured path
rule/policy selected
approval status
application status
errors
model_version nếu có
```

Không log secrets.

## 29. Startup / Runbook

Tạo/update `README_MVP_RUNBOOK.md` với thứ tự:

```text
1. PostgreSQL
2. alembic upgrade head
3. seed structured data
4. ingest/check RAG
5. start CV service
6. start AI/API service
7. load demo fixtures
8. run smoke test
```

Ghi exact commands theo repo.

## 30. Smoke test script

Tạo equivalent:

```text
ai/scripts/smoke_test_mvp.py
```

Verify tự động:

```text
DB reachable
CV health
TreatmentRule lookup
Policy lookup
RAG retrieve
LLM generation basic
LangGraph compile
```

Không cần tự approve nếu flow cần human input, nhưng phải verify graph tới approval checkpoint.

## 31. E2E test

Có ít nhất một integration test không phụ thuộc UI:

```text
known disease / known CV contract
-> graph
-> recommendation
-> approve
-> applied
-> PHI
```

Unit/integration test có thể mock CV client nếu model inference quá nặng, nhưng final smoke test phải gọi CV service thật ít nhất một lần.

## 32. Data integrity check

Sau E2E verify DB:

```text
Diagnosis exists
TreatmentApplication chỉ tồn tại cho APPLIED case
Rejected case không có TreatmentApplication
PHI lock consistent
fixtures/seeds không duplicate vô hạn
```

## 33. Security/config check

```text
[ ] no .env committed.
[ ] no API key committed.
[ ] no DB password hard-coded.
[ ] CV base URL configurable.
[ ] DB URL configurable.
[ ] LLM endpoint/key configurable.
[ ] debug mode documented.
```

## 34. Performance check

Không benchmark sâu. Chỉ log/đo:

```text
CV inference time
RAG retrieval time
LLM generation time
total recommendation latency
```

Report bottleneck nếu demo quá chậm.

## 35. Failure recovery

Check:

```text
CV down -> clear error
RAG down -> no invented knowledge
LLM down -> structured facts vẫn retrievable
DB down -> fail safely
duplicate APPLIED -> no duplicate treatment event
```

---

# 36. FINAL CHECKLIST — STAGE 1 → 10

## Stage 1

```text
[ ] canonical disease taxonomy ready
[ ] 38 classes
[ ] no duplicate codes
```

## Stage 2

```text
[ ] staging separated from runtime
[ ] hold data not used in runtime
```

## Stage 3

```text
[ ] 4 final pesticides
[ ] 8 final treatment rules
[ ] 21 final policies
```

## Stage 4

```text
[ ] dose_min/dose_max schema
[ ] DiseaseManagementPolicy model
[ ] migration head clean
```

## Stage 5

```text
[ ] seed idempotent
[ ] deterministic treatment lookup
[ ] deterministic policy lookup
[ ] actual dose validation
```

## Stage 6

```text
[ ] CV contract stable
[ ] class contract matches DB
[ ] Diagnosis persists
[ ] CV -> DB lookup works
```

## Stage 7

```text
[ ] RAG rich knowledge
[ ] provenance
[ ] retrieval service
[ ] no structured hallucination
```

## Stage 8

```text
[ ] LangGraph compile
[ ] treatment path
[ ] policy path
[ ] review-required path
[ ] approval checkpoint
[ ] reject branch
```

## Stage 9

```text
[ ] APPROVED != APPLIED
[ ] TreatmentApplication only after APPLIED
[ ] actual dose validated
[ ] PHI lock
[ ] reject safe
```

## Stage 10

```text
[ ] demo fixtures
[ ] treatment E2E
[ ] policy E2E
[ ] reject E2E
[ ] invalid dose E2E
[ ] low-confidence handling
[ ] smoke test
[ ] runbook
```

---

# 37. FINAL MVP ACCEPTANCE MATRIX

Codex phải chạy/report ít nhất:

| Test | Input | Expected |
|---|---|---|
| DB Rule | `Tomato___Late_blight` | TreatmentRule |
| DB Policy | `Tomato___Tomato_mosaic_virus` | `NO_CHEMICAL_CONTROL` |
| CV | sample image | canonical `disease_code` |
| RAG | disease query | relevant chunks + sources |
| Graph treatment | known treatment class | recommendation |
| Graph policy | virus/policy class | no chemical recommendation |
| Reject | engineer reject | reason persisted, no application |
| Approve | engineer approve | waiting APPLIED, no PHI |
| Applied valid | dose in range | TreatmentApplication + PHI |
| Applied invalid | dose out of range | rejected |
| Seed idempotency | repeat seed | no duplicate |
| Duplicate APPLIED | same case retry | no duplicate application |
| Low confidence | below configured threshold | review-required |
| CV unavailable | service down | safe error |
| LLM unavailable | service down | no invented recommendation |

---

# 38. FINAL MVP OUTPUTS

Repo phải có/update equivalent:

```text
ai/app/graph/
ai/app/graph/state.py
ai/app/graph/graph.py
ai/app/graph/nodes/*
ai/app/graph/tools/*

HITL / treatment application service
PHI lock logic

integration tests
E2E tests
smoke test script
README_MVP_RUNBOOK.md
```

Tên file có thể khác theo repo convention. Không duplicate architecture nếu module tương đương đã có.

---

# 39. README_MVP_RUNBOOK.md PHẢI CÓ

```text
Prerequisites
Environment variables
Database startup
Migration
Seed
RAG ingest/check
CV startup
AI/API startup
Demo fixtures
Smoke test
Demo flow
Known limitations
Troubleshooting
```

---

# 40. FINAL REPORT CODEx PHẢI TRẢ CHO TÙNG

```text
PRECHECK
Stage 1: PASS/FAIL
Stage 2: PASS/FAIL
Stage 3: PASS/FAIL
Stage 4: PASS/FAIL
Stage 5: PASS/FAIL
Stage 6: PASS/FAIL
Stage 7: PASS/FAIL

STAGE 8
- files changed
- graph structure
- nodes implemented
- tests

STAGE 9
- approval flow
- reject flow
- applied flow
- PHI logic
- tests

STAGE 10
- fixtures
- smoke test
- E2E tests
- runbook

FINAL ACCEPTANCE MATRIX
- each test PASS/FAIL

COMMANDS RUN
- ...

KNOWN ISSUES
- ...

MVP READY
YES / NO
```

Không được ghi `MVP READY = YES` nếu core checklist còn fail.

---

# 41. KHÔNG COMMIT/PUSH NẾU CHƯA ĐƯỢC YÊU CẦU

Có thể sửa local và chạy migration/test.

Không tự:

```text
git commit
git push
merge
rebase remote branch
```

trừ khi Tùng yêu cầu trực tiếp.

---

# 42. STOP CONDITIONS

Dừng và report thay vì đoán nếu:

```text
CV disease_code contract không khớp DB
migration có nguy cơ drop dữ liệu ngoài scope
final structured counts sai bất thường
TreatmentRule schema khác hoàn toàn expected
approval persistence model chưa rõ và có nhiều lựa chọn nghiệp vụ
PHI logic hiện tại mâu thuẫn với schema đang có
```

Không invent business rules mới.

---

# 43. DEFINITION OF DONE CUỐI CÙNG

MVP chỉ được coi DONE khi flow sau chạy được:

```text
Farmer image
      |
      v
CV prediction
      |
      v
Diagnosis stored
      |
      v
Field context
      |
      v
TreatmentRule OR ManagementPolicy
      |
      v
RAG evidence
      |
      v
LLM wording
      |
      v
Engineer APPROVE / REJECT
      |
      +--> REJECT
      |      |
      |      v
      |   close case
      |
      +--> APPROVE
             |
             v
         wait APPLIED
             |
             v
      actual dose confirmation
             |
             v
      TreatmentApplication
             |
             v
          PHI lock
```

Và guardrails:

```text
[ ] LLM không bịa thuốc.
[ ] LLM không bịa dose.
[ ] LLM không bịa PHI.
[ ] RAG không override structured facts.
[ ] APPROVED không tạo PHI.
[ ] APPLIED mới tạo PHI.
[ ] actual_dose phải trong range.
[ ] Policy-only disease không bị ép chemical treatment.
[ ] CV identity dùng disease_code canonical.
[ ] runtime không dùng hold candidate.
```

Khi toàn bộ checklist pass, dừng và báo:

```text
DX-FARM MVP READY FOR DEMO
```
