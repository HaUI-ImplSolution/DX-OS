# DX-Farm AI – LangGraph MVP

> README nội bộ dành riêng cho thư mục `ai/`  
> Mục tiêu: giúp **Khánh (CV)** và **Tùng (Data / RAG / LLM / LangGraph)** hiểu cùng một luồng, phát triển song song và tích hợp ít xung đột nhất.

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

## 3. Cấu trúc thư mục đề xuất trong `ai/`

```text
ai/
├── app/
│   ├── db/
│   │   ├── models/
│   │   ├── repositories/
│   │   ├── base.py
│   │   └── session.py
│   │
│   ├── rag/
│   │   ├── document_loader.py
│   │   ├── chunker.py
│   │   ├── embedding_service.py
│   │   ├── ingestion_service.py
│   │   ├── retriever.py
│   │   ├── prompt_builder.py
│   │   └── rag_service.py
│   │
│   ├── services/
│   │   └── field_context_service.py
│   │
│   └── graph/
│       ├── __init__.py
│       ├── state.py
│       ├── graph.py
│       │
│       ├── nodes/
│       │   ├── __init__.py
│       │   ├── resolve_request.py
│       │   ├── cv_diagnosis.py
│       │   ├── load_field_context.py
│       │   ├── treatment_lookup.py
│       │   ├── retrieve_knowledge.py
│       │   ├── generate_recommendation.py
│       │   ├── engineer_approval.py
│       │   ├── record_treatment.py
│       │   └── reject_case.py
│       │
│       ├── tools/
│       │   ├── __init__.py
│       │   ├── vision_tool.py
│       │   ├── field_context_tool.py
│       │   ├── treatment_tool.py
│       │   └── rag_tool.py
│       │
│       └── prompts/
│           ├── __init__.py
│           └── recommendation_prompt.py
│
├── data/
│   ├── structured/
│   └── rag/
│
├── migrations/
├── alembic.ini
├── requirements.txt
└── README.md
```

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
