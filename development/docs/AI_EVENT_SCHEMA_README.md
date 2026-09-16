# DX-Farm AI Event Schema

Contract local của AI service cho Backend/Frontend; base URL là `http://127.0.0.1:8000/api/v1` và `disease_code` phải khớp chính xác `Disease.code`.

> **Trạng thái**: Tài liệu này mô tả contract **hiện tại**, nơi AI tự lưu `WorkflowCase`/`WorkflowEvent` và ghi trực tiếp vào PostgreSQL dùng chung với Backend.
> Theo [`ai_backend_decoupling_plan.md`](./ai_backend_decoupling_plan.md), toàn bộ `/disease/workflow/*` cùng `POST /disease/reports/{report_id}/diagnose` sẽ bị xóa ở Phase 1 và thay bằng contract suy luận phi trạng thái `POST /api/v1/diagnose` (Backend gọi AI, AI trả JSON một lần, không tự ghi DB nghiệp vụ).
> Nội dung dưới đây vẫn đúng cho tới khi Phase 1 hoàn tất; xem contract mới ở mục 5 của kế hoạch đó.

## Quy tắc bất biến

- Structured PostgreSQL quyết định thuốc, hoạt chất, liều và PHI; RAG chỉ bổ sung evidence/source.
- `APPROVED` không phải `APPLIED`; chỉ `APPLIED` tạo `TreatmentApplication` và bắt đầu PHI.
- AI MVP chưa xác thực actor; Backend production phải xác thực/ủy quyền trước khi proxy transition.

## Luồng bền vững

```text
POST workflow/run -> RECOMMENDATION_PENDING (PENDING/NOT_READY)
APPROVED -> WAITING_APPLIED (treatment) | NOT_APPLICABLE (policy/review)
REJECTED -> CANCELLED
APPLIED -> TreatmentApplication + PHI lock
```

| `structured_path` | Ý nghĩa |
|---|---|
| `treatment` | Có rule hợp lệ; engineer chọn rule và dose. |
| `policy` | Chỉ policy; không có chemical action. |
| `review_required` | Chưa đủ rule/policy; cần review. |

## Endpoints

| Method | Endpoint | Input chính | Output chính |
|---|---|---|---|
| POST | `/disease/workflow/run` | multipart image + context | recommendation, `case_id`, PENDING |
| GET | `/disease/workflow/{case_id}` | case UUID | durable case + audit events |
| POST | `/disease/workflow/{case_id}/approve` | actor, rule/dose khi treatment | APPROVED |
| POST | `/disease/workflow/{case_id}/reject` | actor, reason | REJECTED/CANCELLED |
| POST | `/disease/workflow/{case_id}/applied` | actor, actual dose, time | APPLIED + PHI lock |
| POST | `/disease/predict` | multipart image | CV-only diagnosis |
| POST | `/disease/knowledge/retrieve` | query, disease code, top_k | RAG evidence |

## `POST /disease/workflow/run`

`Content-Type: multipart/form-data`.

| Field | Required | Rule |
|---|---:|---|
| `image` | yes | image, maximum 10 MiB |
| `request_id` | no | stable client retry key; duplicate returns the same case |
| `parcel_code` | conditional | required when `crop_season_id` is absent |
| `crop_season_id` | conditional | UUID; must belong to supplied parcel when both exist |
| `rag_question` | no | optional retrieval question |
| `requested_by` | no | MVP audit actor |

Response has `approval_status: "PENDING"`; `application_status` can be `null` at this immediate checkpoint.
Use `GET /workflow/{case_id}` as the durable source, where a new case is `NOT_READY`.

```json
{
  "case_id": "uuid",
  "request_id": "field-report-001",
  "approval_status": "PENDING",
  "cv_result": {"disease_code": "Tomato___Late_blight", "confidence": 0.91, "model_name": "EfficientNet-B4", "model_version": "v1.0"},
  "recommendation": {"path": "treatment", "actions": [], "rag_sources": [], "requires_engineer_approval": true},
  "errors": []
}
```

## Durable case and events

`GET /disease/workflow/{case_id}` returns `approval_status`, `application_status`, recommendation, selected rule/dose, optional application and ordered `events`.

| Event | Required details |
|---|---|
| `RECOMMENDATION_PENDING` | disease code, structured path, low confidence |
| `APPROVED` | selected rule, approved dose, application status |
| `REJECTED` | reason |
| `APPLIED` | application id, actual dose, PHI and lock timestamp |

Render `events` by ascending `sequence`; do not infer a transition from UI state.

## Transition payloads

```json
// approve: policy/review omits selected_rule_id and approved_dose
{"actor_id":"engineer-001","selected_rule_id":"uuid","approved_dose":1.5,"confirmed_low_confidence":false}
// reject
{"actor_id":"engineer-001","reason":"Cần ảnh rõ hơn"}
// applied
{"actor_id":"farmer-001","actual_dose":1.5,"applied_at":"2026-09-11T08:30:00Z"}
```

Treatment approve requires a listed rule and dose inside its range; low-confidence treatment requires explicit confirmation.
Applied retries with the same dose/time are idempotent; a different payload returns `409`.

## Errors

| HTTP | Meaning |
|---:|---|
| 400 | invalid image, UUID or workflow context |
| 404 | parcel, season or case missing |
| 409 | invalid state transition or non-idempotent applied retry |
| 413 | image exceeds 10 MiB |
| 422 | invalid dose, missing transition field or low-confidence confirmation |
| 500 | internal workflow failure |
| 503 | CV model or database unavailable |
