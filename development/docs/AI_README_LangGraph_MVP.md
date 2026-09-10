# DX-Farm AI – LangGraph MVP

> README nội bộ dành riêng cho thư mục `ai/`  
> Mục tiêu: giúp **Khánh (CV)** và **Tùng (Data / RAG / LLM / LangGraph)** hiểu cùng một luồng, phát triển song song và tích hợp ít xung đột nhất.
>
> Theo dõi tiến độ và handoff theo từng Stage tại
> [Phụ lục A — Master Stage Roadmap](#master-stage-roadmap).

---

## 1. Mục tiêu của module `ai/`

Module `ai/` chịu trách nhiệm cho phần Intelligence của DX-Farm MVP:

1. Nhận ảnh bệnh từ luồng nghiệp vụ.
2. Gọi mô hình CV để xác định bệnh.
3. Lấy trạng thái hiện tại của thửa ruộng từ PostgreSQL.
4. Lookup quy tắc xử lý có cấu trúc (`Disease -> TreatmentRule -> Pesticide`).
5. Retrieve kiến thức giải thích từ RAG khi cần.
6. Dùng LLM để tạo khuyến nghị dễ hiểu.
7. Chờ kỹ sư nông nghiệp `APPROVE / REJECT`.
8. Nếu được duyệt, chờ xác nhận thực tế `APPLIED`.
9. Khi `APPLIED`, ghi `TreatmentApplication` và tính mốc PHI / `harvest_locked_until`.

**Nguyên tắc chính**

```text
PostgreSQL
= sự thật đã xảy ra ngoài ruộng

Structured Knowledge
= quy tắc kỹ thuật có cấu trúc

RAG
= kiến thức giải thích từ tài liệu

CV
= nhận diện bệnh từ ảnh

LLM
= tổng hợp context và diễn đạt

LangGraph
= điều phối toàn bộ workflow
```

LLM **không tự sinh** liều lượng, PHI hoặc dữ liệu định lượng quan trọng.

---

## 2. Spine Flow của LangGraph MVP

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
Treatment Lookup
  |
  v
Retrieve Knowledge (RAG)
  |
  v
Generate Recommendation
  |
  v
Engineer Approval
  |
  +----------------------+
  |                      |
REJECT                 APPROVE
  |                      |
  v                      v
Reject Case          Create/Return
  |                 Approved Order
  |                      |
  |                      v
  |               Wait for APPLIED
  |                      |
  |                      v
  |               Record Treatment
  |                + PHI Lock
  |                      |
  +-----------> END <----+
```

### Phân biệt bắt buộc

```text
APPROVED != APPLIED
```

- `APPROVED`: kỹ sư đồng ý với đề xuất.
- `APPLIED`: việc phun đã thực sự được xác nhận ngoài ruộng.
- PHI chỉ bắt đầu tính khi `APPLIED`.

---

## 3. Cấu trúc thư mục `ai/` (đã refactor — 2026-09-10)

> Cấu trúc dưới đây phản ánh trạng thái **thực tế** sau refactor.
> Các thư mục đánh dấu `[SKELETON]` đã được tạo với file stub, chờ implement logic.

```text
ai/
│
├── app/                        # Python package chính của AI Service
│   │
│   ├── main.py                 # Entrypoint: tạo FastAPI app, đăng ký router
│   │
│   ├── core/                   # Config & infrastructure tập trung
│   │   ├── config.py           #   Settings tập trung từ .env (DATABASE_URL, OLLAMA_*, CV_*)
│   │   └── lifespan.py         #   FastAPI lifespan: load/unload CV model một lần
│   │
│   ├── api/                    # HTTP layer — CHỈ định nghĩa router và schemas
│   │   ├── router.py           #   Aggregate router, include sub-routers
│   │   └── schemas.py          #   Pydantic request/response schemas (DiseaseDetectionResponse)
│   │
│   ├── cv/                     # Computer Vision domain (EfficientNet-B4)
│   │   ├── classifier.py       #   EfficientNetB4Classifier: model architecture + training helpers
│   │   ├── preprocessor.py     #   Image preprocessing: PlantVillageDataset, prepare_input_image()
│   │   ├── class_names.py      #   Mapping {class_id: class_name} cho 38 lớp bệnh PlantVillage
│   │   └── loader.py           #   download_model(): tải weights từ HuggingFace về ai/weights/
│   │                           #   ⚠ Path fix: __file__.parents[2] = ai/ — luôn cố định bất kể CWD
│   │
│   ├── db/                     # Data layer: ORM models + repository functions
│   │   ├── base.py             #   SQLAlchemy declarative Base
│   │   ├── session.py          #   SessionLocal factory, get_db() FastAPI dependency
│   │   ├── models/             #   ORM models ánh xạ tới PostgreSQL tables:
│   │   │   ├── parcel.py             # Parcel — thửa ruộng
│   │   │   ├── crop_season.py        # CropSeason — vụ mùa
│   │   │   ├── disease.py            # Disease — danh mục 38 lớp bệnh (Structured Knowledge)
│   │   │   ├── disease_report.py     # DiseaseReport — báo cáo sự cố + ảnh upload
│   │   │   ├── diagnosis.py          # Diagnosis — kết quả CV + trạng thái duyệt APPROVE/REJECT
│   │   │   ├── pesticide.py          # Pesticide — danh mục thuốc BVTV (Structured Knowledge)
│   │   │   ├── treatment_rule.py     # TreatmentRule — khoảng liều + PHI (Structured Knowledge)
│   │   │   ├── disease_management_policy.py # Policy an toàn khi không có chemical rule
│   │   │   ├── treatment_application.py  # TreatmentApplication — đã phun + harvest_locked_until
│   │   │   ├── document.py           # Document — tài liệu RAG (VietGAP/IRRI)
│   │   │   └── document_chunk.py     # DocumentChunk — chunk + embedding vector(384)
│   │   └── repositories/       #   Repository functions (SQLAlchemy queries):
│   │       ├── parcel_repository.py       # get_parcel_by_code(), get_current_crop_season()
│   │       ├── diagnosis_repository.py    # get_latest_diagnosis()
│   │       ├── knowledge_repository.py    # lookup Disease, Rule, Pesticide và management policy
│   │       └── treatment_repository.py    # validate liều thực tế, create/get application, PHI
│   │
│   ├── rag/                    # RAG pipeline (pgvector + Ollama)
│   │   ├── document_loader.py  #   Load raw tài liệu từ file/text
│   │   ├── chunker.py          #   Chia tài liệu thành chunks có kích thước phù hợp
│   │   ├── embedding_service.py #  Tạo vector embedding (multilingual MiniLM 384 chiều)
│   │   ├── ingestion_service.py #  Ingest idempotent: embed + lưu vào pgvector (không nhân đôi)
│   │   ├── retriever.py        #   Cosine similarity retrieval từ pgvector
│   │   ├── prompt_builder.py   #   Build prompt từ retrieved contexts
│   │   ├── source_registry.py  #   Validate/resolve provenance và citation theo source_id
│   │   └── rag_service.py      #   RAGService: kết hợp retriever + LLM Ollama (answer())
│   │
│   ├── services/               # Application services — orchestrate giữa db, cv, rag
│   │   ├── plant_disease_service.py  # PlantDiseaseService: preprocess ảnh + CV inference -> dict
│   │   └── field_context_service.py  # get_field_context(): gom Parcel+Season+Diagnosis+Treatment
│   │
│   └── graph/                  # [SKELETON] LangGraph orchestration — spine flow M1-M6
│       ├── state.py            #   FarmState TypedDict: state object duy nhất qua toàn bộ nodes
│       │                       #   ⚠ State là dữ liệu TẠM — không dùng làm database
│       ├── graph.py            #   Build & compile StateGraph, khai báo edges và conditions
│       │
│       ├── nodes/              #   Mỗi node = 1 bước xử lý trong spine flow:
│       │   ├── resolve_request.py        # Validate và chuẩn hóa input vào FarmState
│       │   ├── cv_diagnosis.py           # Gọi vision_tool -> lưu Diagnosis vào DB
│       │   ├── load_field_context.py     # Đọc Parcel/CropSeason/Treatment hiện tại từ DB
│       │   ├── treatment_lookup.py       # Lookup DETERMINISTIC: Disease->TreatmentRule->Pesticide
│       │   │                             # ⚠ Không dùng LLM để đoán liều/thuốc/PHI
│       │   ├── retrieve_knowledge.py     # RAG retrieve: lấy context giải thích từ pgvector
│       │   ├── generate_recommendation.py # LLM sinh khuyến nghị dễ hiểu TỪ facts đã cung cấp
│       │   │                             # ⚠ LLM không tự sinh số liệu
│       │   ├── engineer_approval.py      # Human-in-the-loop: PENDING -> APPROVED | REJECTED
│       │   ├── record_treatment.py       # Persist TreatmentApplication + tính PHI lock
│       │   │                             # Chỉ chạy sau APPROVED + APPLIED
│       │   └── reject_case.py            # Lưu REJECTED + reason + đóng workflow
│       │
│       ├── tools/              #   Thin wrapper giữa nodes và services/repositories:
│       │   ├── vision_tool.py            # node -> tool -> PlantDiseaseService
│       │   ├── field_context_tool.py     # node -> tool -> field_context_service
│       │   ├── treatment_tool.py         # node -> tool -> knowledge_repository
│       │   └── rag_tool.py               # node -> tool -> RAGRetriever
│       │
│       └── prompts/            #   Prompt templates cho LLM:
│           └── recommendation_prompt.py  # Template khuyến nghị (facts-only, no hallucination)
│
├── data/                       # Static data files — không thay đổi khi runtime
│   ├── structured/             #   JSON seed data cho Structured Knowledge:
│   │   ├── diseases.json             # 38 lớp bệnh (code, name, description)
│   │   ├── pesticides.json           # 4 thuốc trong tập Stage 3 đã review
│   │   ├── treatment_rules.json      # 8 rule APPROVE/READY_TO_PROMOTE
│   │   ├── disease_management_policies.json # 21 policy cho lớp không có chemical rule
│   │   └── treatment_rule_candidates.json # 26 candidate staging; không seed trực tiếp
│   ├── rag/
│   │   ├── source_registry.json      # Registry hợp nhất 41 nguồn và metadata citation
│   │   └── processed/
│   │       └── disease_knowledge.json # 38 bản ghi kiến thức sẵn sàng cho embedding
│   └── stage3/                       # Gói review, manifest, report và HOLD để audit
│
├── migrations/                 # Alembic database migrations
│   └── versions/
│       ├── 9700136d7e00_initial_schema.py           # Schema ban đầu (8 entity spine-flow)
│       ├── 4c2a0f9d8b71_add_rag_document_tables.py  # Bật pgvector + Document + DocumentChunk
│       └── c1f7e2d4a9b0_stage3_structured_knowledge.py # Khoảng liều + management policy
│
├── scripts/                    # CLI scripts chạy một lần — KHÔNG phải app module
│   ├── seed_diseases.py        #   Seed 38 bản ghi Disease vào DB từ diseases.json
│   ├── seed_pesticides.py       #   Seed idempotent 4 pesticide Stage 5
│   ├── seed_treatment_rules.py  #   Seed idempotent 8 rule đã approve
│   ├── seed_disease_management_policies.py # Seed idempotent 21 policy
│   └── ingest_diseases.py      #   Ingest disease knowledge vào pgvector (hỗ trợ --dry-run)
│
├── tests/                      # Unit tests (pytest) và RAG smoke checks
│   ├── cv/
│   │   └── test_model_ENB4C.py #   Test CLASS_NAMES mapping (38 classes), model output shape
│   ├── data/
│   │   └── test_stage3_structured_data.py # Validate final/hold/coverage/guardrail
│   ├── db/                      # Unit + integration test repository Stage 4/5
│   └── rag/
│       ├── test_retriever.py   #   Test cosine retrieval từ pgvector
│       ├── test_rag_service.py #   Test RAG end-to-end với Ollama
│       ├── test_knowledge_data.py # Validate dataset, provenance, guardrail và chunking
│       └── test.py             #   Smoke tests cho RAG pipeline
│
├── weights/                    # Model weights — canonical location
│   └── best_model.pth          #   EfficientNet-B4 weights (auto-download từ HuggingFace nếu thiếu)
│                               #   ⚠ Không commit lên git (kích thước lớn)
│
├── .env.example                # Mẫu biến môi trường được phép commit
├── .env                        # Biến môi trường local (không commit)
├── alembic.ini                 # Alembic config — trỏ tới migrations/
└── requirements.txt            # Python dependencies
```

---

### 3.1. Snapshot dữ liệu RAG Stage 1 (lịch sử, 2026-09-10)

Các dòng dưới mô tả trạng thái ở cuối Stage 1. File final và source registry đã được
Stage 3 thay thế/cập nhật như mục 3.2; các candidate Stage 1 vẫn chỉ là staging.

- `disease_knowledge.json`: 38 lớp bệnh/healthy, giữ nguyên nội dung từ workbook
  `DX_Farm_Plant_Disease_Knowledge_Base_v1(1).xlsx`; trường `content` không chứa
  liều lượng, PHI hay tên sản phẩm.
- Registry ở snapshot Stage 1 có 26 nguồn; registry canonical hiện đã hợp nhất thành 41
  nguồn ở Stage 3. Mọi `source_id` được tham chiếu đều phải resolve được.
- `treatment_rule_candidates.json`: 26 lớp bệnh, không gồm 12 lớp healthy; tất cả
  có `eligible_for_seed = false` và chỉ là dữ liệu staging.
- Ở cuối Stage 1, `pesticides.json` và `treatment_rules.json` là mảng rỗng. Stage 3 chỉ
  promote tập con đã có quyết định review; nguyên tắc không seed dữ liệu HOLD vẫn giữ nguyên.
- Pipeline ưu tiên ranh giới dòng và tách theo từ khi cần, tối đa 350 ký tự/chunk. Dry-run dự kiến
  38 document và 270 chunk; cần chạy ingestion thật để thay dữ liệu cũ trong PostgreSQL.
- Hiện còn một khác biệt mã CV/knowledge cần xử lý ở adapter:
  `Corn_(maize)___Common_rust_` (CV) và `Corn_(maize)__Common_rust` (dữ liệu). Không
  sửa chuỗi trong dữ liệu nguồn.

### 3.2. Structured Knowledge Stage 3 review + Stage 4/5 triển khai (2026-09-10)

- Hai bản workbook review độc lập và nằm trong gói có cùng SHA-256; toàn bộ checksum gói
  đã đạt. Bản review, quyết định, manifest, report và dữ liệu HOLD được lưu tại
  `data/stage3/` để truy vết.
- File final dùng để seed có đúng 4 pesticide, 8 treatment rule và 21
  disease-management policy. Tám candidate `HOLD/NOT_READY` chỉ nằm trong khu vực HOLD,
  không được seeder đọc.
- `TreatmentRule` lưu `dose_min`/`dose_max`; khi ghi `TreatmentApplication`, caller phải
  cung cấp `actual_dose` nằm trong khoảng và giá trị này được snapshot ở cột `dose`.
- Policy bao phủ 21 lớp bệnh không có chemical rule trực tiếp; 5 lớp có rule và 21 lớp có
  policy tạo thành 26 lớp non-healthy, không chồng lấn. Lớp healthy không được gán rule.
- Stage 4 hoàn thiện schema, migration, kiểm tra `actual_dose` và policy lookup. Stage 5
  có ba seeder idempotent; PostgreSQL local đã xác minh ở revision `c1f7e2d4a9b0` với
  count 4/8/21 và test tự chạy seed lần hai không tạo bản ghi trùng.
- Stage 4/5 không chạy embedding/re-ingest. Registry 41 nguồn chỉ được cập nhật cho
  provenance. Workbook là review kỹ thuật phục vụ MVP, không phải field-use authorization.

#### Checklist tiến độ Stage 4/5

**Stage 4 — Schema và repository**

- [x] Thay `TreatmentRule.dose` bằng `dose_min`/`dose_max`, giữ nguyên unit, PHI và provenance.
- [x] Có invariant `dose_min <= dose_max`; `TreatmentApplication.dose` vẫn là actual dose.
- [x] Tạo và đăng ký Alembic model/table `DiseaseManagementPolicy`, `disease_id`
  NOT NULL/UNIQUE/indexed.
- [x] Migration chỉ thay `treatment_rules` và tạo `disease_management_policies`;
  `alembic upgrade head` đạt ở revision `c1f7e2d4a9b0`.
- [x] Repository nhận và validate `actual_dose`; có deterministic treatment/policy lookup.

**Stage 5 — Final data và seed**

- [x] Final seed giữ nguyên 4 pesticide, 8 treatment rule và 21 management policy từ gói review.
- [x] Seeder không hard-code UUID; lookup exact `Disease.code`/`Pesticide.name` và fail-fast.
- [x] Dữ liệu HOLD được giữ để audit nhưng không được seeder đọc.
- [x] Mỗi seeder chạy atomic, rollback khi lỗi và idempotent khi chạy lần hai.

**Kiểm thử và sẵn sàng tích hợp**

- [x] Lookup Tomato late blight và Corn northern leaf blight đạt.
- [x] Tomato Target Spot trả safe policy; Tomato mosaic virus trả `NO_CHEMICAL_CONTROL`.
- [x] Actual dose trong range được nhận, ngoài range/NaN bị từ chối.
- [x] Toàn bộ pytest đạt, gồm integration test tự chạy seeder hai lần.
- [x] Repository đã sẵn sàng để node `treatment_lookup` của LangGraph gọi sau này.
- [ ] LangGraph node/workflow chưa triển khai vì nằm ngoài phạm vi Stage 4/5.
- [ ] RAG chưa re-ingest/embedding lại; chỉ cập nhật provenance registry.

---

## 4. Phân công song song

### Khánh – CV

Khánh chịu trách nhiệm độc lập cho:

```text
Ảnh
  ->
Preprocess
  ->
CV Model
  ->
Prediction JSON
```

Phạm vi chính:

- load model;
- preprocessing ảnh;
- inference;
- chuẩn hóa class name;
- confidence;
- model metadata;
- API / function interface để LangGraph gọi;
- test CV độc lập.

LangGraph **không cần biết** model bên dưới được train như thế nào.

Khánh có thể thay:

```text
MobileNetV3 -> YOLO / EfficientNet / model khác
```

miễn là output contract không thay đổi.

### Tùng – Data / RAG / LLM / LangGraph

Tùng chịu trách nhiệm:

```text
PostgreSQL
Structured Knowledge
RAG
LLM
LangGraph orchestration
```

Phạm vi chính:

- ORM models;
- repository;
- field context;
- Disease / TreatmentRule / Pesticide;
- pgvector;
- embedding;
- retriever;
- RAG;
- prompt;
- LangGraph State;
- graph nodes;
- human approval state;
- ghi TreatmentApplication / PHI.

---

## 5. Điểm giao nhau giữa Khánh và Tùng

Hai bên chỉ cần thống nhất **CV Contract**.

### Input tối thiểu

LangGraph / backend gửi cho CV:

```json
{
  "request_id": "req_001",
  "image_ref": "/path/to/image.jpg"
}
```

`parcel_code` và `crop_season_id` do hệ thống Data / LangGraph quản lý, CV không cần truy cập PostgreSQL.

### Output bắt buộc

```json
{
  "disease_code": "Apple___Apple_scab",
  "disease_name": "Bệnh ghẻ táo",
  "confidence": 0.952,
  "model_name": "MobileNetV3-Small",
  "model_version": "v1.0"
}
```

Có thể mở rộng:

```json
{
  "inference_time_ms": 187,
  "top_k": [
    {
      "disease_code": "Apple___Apple_scab",
      "confidence": 0.952
    }
  ]
}
```

### Quy tắc quan trọng

`disease_code` phải khớp với `Disease.code` trong PostgreSQL.

Ví dụ:

```text
CV:
Apple___Apple_scab

PostgreSQL:
Disease.code = Apple___Apple_scab
```

Không để CV trả UUID database.

---

## 6. Boundary giữa CV và LangGraph

```text
                    KHÁNH
                      |
                   CV Model
                      |
                Prediction JSON
                      |
---------------- Integration Contract ----------------
                      |
                vision_tool.py
                      |
                 cv_diagnosis
                      |
                    State
                      |
                   TÙNG
```

### Khánh không cần làm

- SQL;
- RAG;
- TreatmentRule;
- PHI;
- LangGraph;
- LLM recommendation.

### Tùng không cần làm

- training CV;
- augmentation;
- model architecture;
- image preprocessing nội bộ;
- CV evaluation pipeline.

Điều này giúp hai nhánh phát triển song song.

---

## 7. LangGraph State dự kiến

State chỉ là dữ liệu tạm của workflow.

```text
FarmState
├── request_id
├── actor_id
├── actor_role
├── parcel_code
├── crop_season_id
├── image_ref
│
├── cv_result
├── field_context
├── treatment_rule
├── rag_context
│
├── recommendation
├── approval_status
├── application_status
└── response
```

**Không dùng LangGraph State làm database.**

Dữ liệu lâu dài phải persist xuống PostgreSQL.

---

## 8. Vai trò từng LangGraph node

### `resolve_request`

Chuẩn hóa:

```text
request_id
parcel_code
crop_season_id
image_ref
actor
```

### `cv_diagnosis`

```text
image_ref
  ->
vision_tool
  ->
CV result
  ->
save Diagnosis
```

### `load_field_context`

Lấy:

```text
Parcel
CropSeason
latest Diagnosis
latest TreatmentApplication
PHI status
```

### `treatment_lookup`

Lookup deterministic:

```text
Disease
  ->
TreatmentRule
  ->
Pesticide
```

Không dùng LLM để đoán thuốc / liều / PHI.

### `retrieve_knowledge`

Dùng RAG để lấy:

```text
triệu chứng
nguyên nhân
điều kiện phát triển
biện pháp phòng ngừa
kiến thức giải thích
```

### `generate_recommendation`

LLM nhận:

```text
CV result
+
Field Context
+
Treatment Rule
+
RAG Context
```

và sinh nội dung dễ hiểu cho kỹ sư.

### `engineer_approval`

Human-in-the-loop:

```text
PENDING
APPROVED
REJECTED
```

### `record_treatment`

Chỉ chạy sau khi có:

```text
APPROVED
+
APPLIED
```

Sau đó persist:

```text
TreatmentApplication
applied_at
dose snapshot
phi_days snapshot
harvest_locked_until
```

### `reject_case`

Lưu:

```text
REJECTED
reason
reviewed_by
reviewed_at
```

và đóng workflow.

---

## 9. Tool boundary

Node không nên chứa implementation dài.

```text
cv_diagnosis.py
    |
    v
vision_tool.py
    |
    v
CV service
```

```text
load_field_context.py
    |
    v
field_context_tool.py
    |
    v
field_context_service.py
    |
    v
Repository
    |
    v
PostgreSQL
```

```text
retrieve_knowledge.py
    |
    v
rag_tool.py
    |
    v
RAGRetriever
    |
    v
pgvector
```

---

## 10. Hai bên có thể làm song song như thế nào?

### Track A – Khánh

```text
A1. Hoàn thiện inference
A2. Chuẩn hóa disease_code
A3. Viết CV interface/API
A4. Trả JSON đúng contract
A5. Test với ảnh thật
A6. Chốt model_version
```

### Track B – Tùng

```text
B1. Hoàn thiện PostgreSQL
B2. Hoàn thiện RAG
B3. Tạo FarmState
B4. Tạo deterministic nodes
B5. Tạo vision_tool mock
B6. Build LangGraph
B7. Test graph bằng mock CV JSON
```

Hai bên **không cần chờ nhau**.

Trong lúc Khánh hoàn thiện CV, Tùng có thể mock:

```python
{
    "disease_code": "Apple___Apple_scab",
    "disease_name": "Bệnh ghẻ táo",
    "confidence": 0.952,
    "model_name": "MobileNetV3-Small",
    "model_version": "v1.0"
}
```

Khi CV API sẵn sàng chỉ thay implementation của `vision_tool.py`.

---

## 11. Integration milestone

Hai nhánh được coi là tích hợp thành công khi chạy được:

```text
Image
  ->
CV
  ->
disease_code
  ->
Disease lookup
  ->
TreatmentRule
  ->
RAG
  ->
LLM recommendation
```

Sau đó mới tích hợp:

```text
Engineer Approval
->
APPLIED
->
TreatmentApplication
->
PHI lock
```

---

## 12. Definition of Done cho AI MVP

### Must

- [ ] CV nhận ảnh và trả JSON đúng contract.
- [ ] CV result được lưu thành `Diagnosis`.
- [ ] `Disease.code` map đúng với CV class.
- [ ] Lookup `TreatmentRule / Pesticide` deterministic.
- [ ] RAG retrieve được context liên quan.
- [ ] LLM sinh recommendation từ context đã cung cấp.
- [ ] Có trạng thái `APPROVE / REJECT`.
- [ ] Phân biệt `APPROVED` và `APPLIED`.
- [ ] Khi `APPLIED`, lưu `TreatmentApplication`.
- [ ] Tính được `harvest_locked_until`.

### Should

- [ ] Trả source RAG.
- [ ] Log state từng node.
- [ ] Có retry / timeout khi CV service lỗi.
- [ ] Có confidence threshold cho CV.

### Later

- Farmer Q&A subgraph.
- Carbon / AWD subgraph.
- GraphRAG.
- Multi-agent.
- IoT tự xác nhận APPLIED.

---

## 13. Quy tắc để tránh xung đột khi merge

1. Khánh không sửa code DB/RAG/LangGraph nếu không cần.
2. Tùng không sửa implementation nội bộ của CV.
3. Hai bên cùng duy trì contract JSON.
4. Nếu thay `disease_code`, phải báo trước vì DB dùng code đó làm khóa logic.
5. Không commit model weights lớn trực tiếp nếu chưa thống nhất cách quản lý artifact.
6. Không commit `.env`, secret, token.
7. Mọi thay đổi contract phải update README này trước hoặc cùng PR.

---

## 14. Branch đang làm việc

### Khánh

```text
Khanh/main
```

Phụ trách chính:

```text
CV
```

### Tùng

```text
feature/make-data
```

Phụ trách chính:

```text
Data
RAG
LLM
LangGraph
```

---

## 15. Tóm tắt một câu

```text
Khánh cung cấp "ảnh này là bệnh gì".

Tùng cung cấp "ruộng này đang thế nào, theo quy tắc nên xử lý gì,
tài liệu giải thích gì, và LangGraph điều phối quyết định ra sao".
```

Hai phần gặp nhau tại **CV Prediction Contract** và được LangGraph nối thành spine flow của DX-Farm MVP.

---

<!-- BEGIN MASTER_STAGE_ROADMAP -->

<a id="master-stage-roadmap"></a>

## Phụ lục A — DX-Farm MVP Master Stage Roadmap & Team Handoff

> Cập nhật theo trạng thái nhánh `feature/make-data` ngày 2026-09-10. Chỉ đánh dấu `[x]` cho đầu ra đã có và có thể kiểm tra; trạng thái CV của Khánh không được tự suy đoán.

> File dùng chung cho Tùng và Khánh để theo dõi tiến độ, input/output, checklist và điểm handoff giữa các stage.
> Đã tích hợp vào tài liệu AI dùng chung theo yêu cầu của nhóm.

### 0. Team boundary

**Khánh — CV**

```text
Image -> preprocessing -> CV model -> prediction -> stable disease_code
```

Không phụ trách PostgreSQL, TreatmentRule, RAG, LangGraph hay PHI.

**Tùng — Data / RAG / LLM / LangGraph**

```text
Disease data -> Structured Knowledge -> PostgreSQL -> RAG -> LLM -> LangGraph
-> approval flow -> TreatmentApplication -> PHI lock
```

Không chỉnh sâu model CV nếu không cần.

### 1. Kiến trúc MVP cuối

```text
Farmer image
   |
   v
CV Service
   |
   | disease_code + confidence
   v
Diagnosis DB
   |
   v
TreatmentRule / DiseaseManagementPolicy
   |
   +-------------------+
   |                   |
   v                   v
Treatment facts      Safe policy
   |                   |
   +---------+---------+
             |
             v
        RAG evidence
             |
             v
         LLM wording
             |
             v
      Engineer approval
        |           |
      REJECT      APPROVE
                    |
                    v
               wait APPLIED
                    |
                    v
           TreatmentApplication
                    |
                    v
                 PHI lock
```

### 2. Quy tắc handoff chung

Một stage chỉ được coi là handoff-ready khi có đủ:

```text
1. Input contract rõ.
2. Output contract rõ.
3. Test/checklist pass.
4. README hoặc cách chạy rõ.
5. Branch/commit state rõ.
```

Mẫu handoff:

```text
Stage:
Owner:
Branch:
Commit:

Input:
...

Output:
...

How to run:
...

Tests:
...

Known issues:
...
```

### 3. Status board

| Stage | Nội dung | Owner | Status |
|---|---|---|---|
| 1 | Disease knowledge base | Tùng | DONE |
| 2 | Structured-data research/staging | Tùng | DONE |
| 3 | Reviewed final MVP data | Tùng | DONE |
| 4 | PostgreSQL schema & migration | Tùng | DONE |
| 5 | Structured data seed & lookup | Tùng | DONE |
| 6 | CV ↔ Data integration | Tùng + Khánh | WAITING |
| 7 | RAG production-ready | Tùng | IN PROGRESS |
| 8 | LangGraph spine flow | Tùng | TODO |
| 9 | HITL / APPLIED / PHI | Tùng | WAITING |
| 10 | End-to-end demo hardening | Tùng + Khánh | WAITING |

---

## STAGE 1 — Disease Knowledge Base

### Mục tiêu
Tạo knowledge base chuẩn cho 38 class CV.

### Owner
`Tùng`

### Input

```text
PlantVillage 38-class taxonomy
authoritative disease sources
Vietnam source registry
MVP requirements
```

### Output

```text
Disease knowledge workbook
diseases.json
disease_knowledge.json
source_registry
38 canonical disease_code
```

`disease_code` là identity xuyên suốt hệ thống.

### Checklist

```text
[x] Có đủ 38 class.
[x] Không duplicate disease_code.
[x] Có healthy classes.
[x] Có Vietnamese display names.
[x] Có triệu chứng, pathogen, prevention/IPM.
[x] Có source provenance.
[x] RAG knowledge không hard-code dose/PHI chưa review.
```

### Handoff
Stage 2 nhận taxonomy + knowledge base + source registry.

---

## STAGE 2 — Structured Knowledge Research / Staging

### Mục tiêu
Tìm và staging Pesticide, TreatmentRule candidate, ManagementPolicy và pest candidate.

### Owner
`Tùng`

### Input

```text
Stage 1 disease codes
Vietnam pesticide registration sources
manufacturer product pages
disease-pathogen mapping sources
```

### Output

```text
pesticides_candidates_master.json
treatment_rules_candidates_master.json
disease_management_policies_master.json
pest_management_candidates_master.json
source_registry_master.json
coverage_master.json
```

### Contract
Candidate không đồng nghĩa approved.

Phải phân biệt rõ:

```text
VERIFIED
NEEDS_REVIEW
CONFLICT
LOW_CONFIDENCE
MISSING_PHI
BROAD_TARGET
```

### Checklist

```text
[x] Không bịa dose.
[x] Không bịa PHI.
[x] Broad target không tự map thành exact disease.
[x] Có source authority/provenance.
[x] Có safe policy cho disease chưa có chemical rule sạch.
[x] Không ép mọi class phải có thuốc.
```

### Handoff
Stage 3 nhận toàn bộ candidate + provenance + review queue.

---

## STAGE 3 — Reviewed Final MVP Data

### Mục tiêu
Promote candidate đủ mạnh thành final seed data.

Nguyên tắc:

```text
false negative > false positive
```

Nếu chưa chắc -> HOLD.

### Owner
`Tùng`

### Input

```text
Stage 2 staging data
source review
Vietnam registration evidence
```

### Output final

```text
final_data/
├── pesticides.json
├── treatment_rules.json
└── disease_management_policies.json
```

Reference:

```text
source_registry.json
review report
review workbook
```

Không seed:

```text
hold/
```

### Expected counts

```text
Pesticides                4
TreatmentRules            8
DiseaseManagementPolicies 21
```

### Checklist

```text
[x] Strong/exact mapping mới promote.
[x] Conflict PHI -> HOLD.
[x] Broad target -> HOLD.
[x] Missing PHI -> HOLD.
[x] Crop mismatch -> HOLD.
[x] Final và hold tách riêng.
[x] Final data có source/version.
```

### Handoff
Stage 4/5 chỉ consume `final_data/`.

---

## STAGE 4 — PostgreSQL Schema & Migration

### Mục tiêu
Làm schema tương thích với data thật.

### Owner
`Tùng`

### Input

```text
Stage 3 final JSON contract
existing SQLAlchemy models
existing Alembic migrations
existing repositories
```

### Required changes

`TreatmentRule`:

```text
dose_min
dose_max
dose_unit
phi_days
source
version
notes
```

Invariant:

```text
dose_min <= dose_max
```

`TreatmentApplication` vẫn giữ:

```text
dose
```

vì đây là actual applied dose.

Tạo model:

```text
DiseaseManagementPolicy
```

fields:

```text
id
disease_id
management_status
direct_treatment_rule_allowed
chemical_rule_scope
source
version
notes
```

### Output

```text
updated SQLAlchemy models
Alembic migration
updated treatment_repository
updated knowledge_repository
```

Repository contract tối thiểu:

```python
get_treatment_rules_by_disease_code(...)
get_management_policy_by_disease_code(...)
```

### Checklist

```text
[x] TreatmentRule có dose_min/dose_max.
[x] Không còn code đọc TreatmentRule.dose cũ.
[x] TreatmentApplication.dose vẫn tồn tại.
[x] DiseaseManagementPolicy model tồn tại.
[x] Alembic nhận model mới.
[x] Migration không đụng unrelated tables.
[x] alembic upgrade head pass.
[x] actual_dose validation tồn tại.
[x] policy lookup tồn tại.
```

### Output contract cho Stage 5
DB schema đã sẵn sàng nhận 3 final JSON.

---

## STAGE 5 — Seed Structured Knowledge

### Mục tiêu
Đưa structured data final vào PostgreSQL.

### Owner
`Tùng`

### Input

```text
Stage 4 schema
pesticides.json
treatment_rules.json
disease_management_policies.json
```

### Output

PostgreSQL chứa:

```text
Disease
Pesticide
TreatmentRule
DiseaseManagementPolicy
```

Expected:

```text
4 Pesticides
8 TreatmentRules
21 DiseaseManagementPolicies
```

### Seeder contract

```text
idempotent
exact lookup
transaction-safe
fail-fast
```

Không fuzzy-map disease_code, không hard-code UUID, không tạo placeholder.

### Tests

```text
Tomato___Late_blight
-> TreatmentRule exists

Corn_(maize)___Northern_Leaf_Blight
-> Amistar Top rule exists

Tomato___Target_Spot
-> no final TreatmentRule
-> safe Policy exists

Tomato___Tomato_mosaic_virus
-> NO_CHEMICAL_CONTROL
```

### Checklist

```text
[x] 4 pesticides seed.
[x] 8 rules seed.
[x] 21 policies seed.
[x] seed lần 2 không duplicate.
[x] Disease exact lookup pass.
[x] Pesticide join pass.
[x] Policy lookup pass.
[x] dose range valid.
[x] actual_dose ngoài range bị reject.
[x] tests pass.
```

### Handoff
Stage 6/8 nhận structured lookup service/repository.

---

## STAGE 6 — CV ↔ Data Integration

### Mục tiêu
Nối output CV của Khánh với Disease/Diagnosis/Structured Knowledge của Tùng.

### Owners
`Khánh: CV handoff`
`Tùng: integration`

### Input từ CV

```json
{
  "disease_code": "Tomato___Late_blight",
  "confidence": 0.95,
  "model_name": "EfficientNet-B4",
  "model_version": "v1.0"
}
```

Khánh cần cung cấp:

```text
POST /api/v1/disease/predict
GET /api/v1/disease/health
class_contract.json
CV README
CV tests
```

### Output Stage 6

```text
image
-> CV
-> disease_code
-> Disease
-> Diagnosis saved
-> TreatmentRule / Policy lookup
```

Chưa cần LangGraph.

### Checklist Khánh

```text
[ ] Model load clean environment.
[ ] 38 classes đúng thứ tự.
[ ] disease_code canonical/unique.
[ ] predict endpoint pass.
[ ] health endpoint pass.
[ ] invalid image không crash.
[ ] confidence trong [0,1].
[ ] model_name/version có.
[ ] class_contract.json có.
[ ] CV tests pass.
```

### Checklist Tùng

```text
[ ] 38 CV codes match 38 DB codes.
[ ] Không map bằng class_id/display name.
[ ] Diagnosis persisted.
[ ] confidence/model metadata persisted.
[ ] TreatmentRule lookup chạy sau prediction.
[ ] Policy fallback chạy khi không có rule.
```

### Definition of Done

```text
sample image
-> prediction
-> DB lookup
-> structured result
```

---

## STAGE 7 — RAG Production-Ready

### Mục tiêu
Biến RAG thành reusable service ổn định.

### Owner
`Tùng`

### Input

```text
disease_knowledge.json
source_registry
embedding model
PostgreSQL + pgvector
Document / DocumentChunk
```

### Output contract

```python
retrieve_knowledge(
    query,
    disease_code=None,
    top_k=3
)
```

Response:

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

RAG được dùng cho triệu chứng, pathogen, điều kiện thuận lợi, transmission, prevention, IPM và giải thích.

RAG không được override Pesticide/dose/PHI/Policy.

### Checklist

```text
[ ] Rich disease_knowledge ingest.
[ ] Có disease_code metadata trong Document/Chunk đã ingest.
[x] Có source provenance.
[x] top_k hoạt động.
[ ] disease filter hoạt động nếu dùng.
[x] duplicate chunks được kiểm soát.
[x] re-ingest có cleanup strategy (replace bộ document cùng type).
[x] sample query cơ bản relevant.
[x] source metadata trả được.
[x] Prompt/LLM guardrail không cho bịa structured facts.
```

### Handoff
Stage 8 chỉ gọi `rag_tool.retrieve(...)`.

---

## STAGE 8 — LangGraph Spine Flow

### Mục tiêu
Nối các service hoàn chỉnh thành workflow chính.

### Owner
`Tùng`

### Input

```text
Stage 6 CV integration
Stage 5 structured lookup
Stage 7 RAG service
LLM generation service
DB persistence services
```

### Graph output

```text
START
-> Resolve Request
-> CV Diagnosis
-> Load Field Context
-> Treatment/Policy Lookup
-> Retrieve Knowledge
-> Generate Recommendation
-> Engineer Approval
-> APPROVE / REJECT
```

Suggested nodes:

```text
resolve_request
cv_diagnosis
load_field_context
treatment_lookup
retrieve_knowledge
generate_recommendation
engineer_approval
record_treatment
reject_case
```

### Checklist

```text
[ ] FarmState tối giản.
[ ] Graph compile được.
[ ] CV node gọi đúng contract.
[ ] disease_code propagate đúng.
[ ] DB context load được.
[ ] TreatmentRule path chạy.
[ ] Policy path chạy.
[ ] RAG node chạy.
[ ] LLM chỉ wording.
[ ] Approval route chạy.
[ ] Reject route chạy.
[ ] Node errors được handle.
```

### Definition of Done

```text
request
-> recommendation waiting for approval
```

qua LangGraph.

---

## STAGE 9 — HITL / APPROVED / APPLIED / PHI

### Mục tiêu
Hoàn thiện nghiệp vụ sau recommendation.

### Owner
`Tùng`

### Input

```text
LangGraph recommendation
TreatmentRule
CropSeason
engineer action
farmer/field application confirmation
```

### Critical rule

```text
APPROVED != APPLIED
```

Approve chưa bắt đầu PHI.

### Flow

```text
Recommendation
  |
  v
Engineer review
  |
  +-> REJECT -> save reason -> close
  |
  +-> APPROVE
        |
        v
     wait APPLIED
        |
        v
 actual treatment confirmation
        |
        v
 TreatmentApplication
        |
        v
 PHI lock
```

PHI:

```text
harvest_locked_until = applied_at + phi_days
```

### Output

```text
TreatmentApplication
actual_dose
applied_at
phi_days snapshot
harvest_locked_until
audit trail
```

### Checklist

```text
[ ] APPROVED không tạo PHI trong workflow AI.
[ ] REJECT lưu reason trong workflow AI.
[x] Repository APPLIED yêu cầu actual_dose.
[x] actual_dose nằm trong rule range.
[x] APPLIED tạo TreatmentApplication.
[x] Rule values snapshot.
[x] PHI bắt đầu từ applied_at.
[x] harvest lock được tính từ applied_at + phi_days.
[x] treatment history query được.
```

---

## STAGE 10 — End-to-End Demo Hardening

### Mục tiêu
Biến tất cả module thành MVP demo ổn định.

### Owners
`Tùng + Khánh`

### Input

```text
CV
DB
Structured Knowledge
RAG
LLM
LangGraph
HITL
PHI
```

### Demo fixtures

```text
1-2 Parcel
1-2 CropSeason
known sample images
known treatment path
known policy-only path
```

### Scenario A — Treatment path

```text
Tomato + Late blight image
-> CV
-> TreatmentRule
-> RAG
-> recommendation
-> APPROVE
-> APPLIED
-> PHI lock
```

### Scenario B — Safe policy path

```text
Tomato mosaic virus
-> CV
-> no TreatmentRule
-> NO_CHEMICAL_CONTROL
-> RAG explanation
-> no fabricated chemical treatment
```

### Scenario C — Uncertain CV

```text
low confidence
-> review/confirmation path
```

Threshold lấy từ validation của model CV, không đoán.

### Output

MVP chạy:

```text
Image
-> Recommendation
-> Approval
-> Application
-> PHI
```

### Checklist

```text
[x] Fresh PostgreSQL/Alembic startup documented.
[x] PostgreSQL starts.
[x] migrations pass.
[x] seed pass.
[ ] CV starts trong luồng tích hợp.
[ ] RAG production-ready.
[ ] LangGraph compiles.
[ ] fixtures load.
[ ] treatment scenario end-to-end pass.
[ ] safe-policy scenario end-to-end pass.
[ ] APPROVE pass qua LangGraph.
[ ] REJECT pass qua LangGraph.
[ ] APPLIED pass end-to-end.
[ ] PHI lock pass end-to-end.
[ ] logs/errors understandable.
[x] no secrets committed; local env/weights/cache được ignore.
[ ] demo runbook end-to-end exists.
```

---

## Final MVP Definition of Done

```text
Farmer image
-> CV prediction
-> Diagnosis stored
-> Field context
-> TreatmentRule / Policy
-> RAG evidence
-> LLM recommendation wording
-> Engineer approval/rejection
-> APPLIED confirmation
-> TreatmentApplication
-> PHI lock
```

Và hệ thống KHÔNG:

```text
- để LLM bịa thuốc
- để LLM bịa dose
- để LLM bịa PHI
- coi APPROVED = APPLIED
- fuzzy map disease_code
- ép disease chưa có verified rule phải có thuốc
```

---

## Dependency map

```text
Stage 1
  |
Stage 2
  |
Stage 3
  |
  +-----------> Stage 7
  |
Stage 4
  |
Stage 5
  |
  +----------------+
                   |
Khánh CV ----------+--> Stage 6
                         |
                       Stage 8
                         |
                       Stage 9
                         |
                       Stage 10
                         |
                        MVP
```

Stage 7 có thể làm song song với Stage 4/5/6.

---

## Parallel work plan

### Hiện tại

Tùng:

```text
Stage 4 DONE
-> Stage 5 DONE
-> Stage 7 IN PROGRESS
```

Stage 6 chờ CV trả `disease_code` canonical ổn định; chưa bắt đầu Stage 8.

Khánh:

```text
finish CV
-> stable API
-> class_contract
-> tests
-> handoff
```

### Khi Stage 4/5 + CV xong

Tùng:

```text
Stage 6 integration
```

Khánh chỉ support nếu CV contract mismatch.

### Sau Stage 6

```text
Stage 7 nếu chưa xong
-> Stage 8
-> Stage 9
-> Stage 10
```

---

## Cross-team contracts không được tự ý đổi

### Disease identity

```text
disease_code
```

### CV response

```json
{
  "disease_code": "...",
  "confidence": 0.0,
  "model_name": "...",
  "model_version": "..."
}
```

### Structured treatment

```text
disease_code
pesticide
dose_min
dose_max
dose_unit
phi_days
```

### Policy

```text
disease_code
management_status
direct_treatment_rule_allowed
chemical_rule_scope
```

### RAG

```text
content
disease_code
source_ids
score
```

Phải báo người còn lại nếu thay đổi bất kỳ public contract nào ở trên.

---

## Daily status template

```text
TODAY

Stage:
...

Done:
- ...

Output ready:
- ...

Tests:
- ...

Blocked:
- ...

Public contract changed?
YES / NO

Need other person:
- ...
```

## Stage completion template

```text
STAGE N DONE

Owner:
...

Input consumed:
...

Output produced:
...

Files:
...

Commands:
...

Tests:
...

Known limitations:
...

Next stage can start:
YES / NO

Next stage expected input:
...
```

## MVP scope control

Không mở rộng trước khi Stage 10 pass:

```text
GraphRAG
multi-agent
full carbon accounting
advanced AWD automation
fully autonomous chemical recommendation
large-scale production deployment
```

## Một câu để nhớ kiến trúc

```text
CV nhận diện.
PostgreSQL giữ sự thật.
Structured Knowledge giữ quy tắc.
RAG cung cấp tài liệu.
LLM diễn đạt.
LangGraph điều phối.
Con người phê duyệt.
APPLIED mới bắt đầu PHI.
```

<!-- END MASTER_STAGE_ROADMAP -->
