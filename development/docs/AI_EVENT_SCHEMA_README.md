# DX-Farm AI Event Schema

Tài liệu tích hợp dành cho Backend và Frontend khi gọi AI Service. Contract này mô tả đúng API
đang có trong thư mục ai/; không phải cam kết cho các endpoint Spring Boot.

## 1. Quy tắc tích hợp

- Base URL local: http://127.0.0.1:8000/api/v1.
- disease_code là mã canonical, phải khớp exact với Disease.code; không fuzzy-map.
- PostgreSQL/structured knowledge là nguồn sự thật cho thuốc, hoạt chất, liều, PHI và policy.
- RAG chỉ bổ sung evidence/source; không override structured facts.
- LLM chỉ diễn đạt evidence. Ollama lỗi/timeout thì generation_mode là evidence_fallback.
- APPROVED không có nghĩa là đã phun. Chỉ APPLIED mới tạo TreatmentApplication và bắt đầu PHI.
- AI MVP chưa có authentication/authorization riêng; Backend production phải xác thực actor trước
  khi proxy approve/reject/applied.

## 2. Workflow event map

~~~text
WORKFLOW_REQUEST
  -> RECOMMENDATION_PENDING
       -> APPROVED -> WAITING_APPLIED -> APPLIED
       -> APPROVED -> NOT_APPLICABLE
       -> REJECTED  -> CANCELLED
~~~

### Trạng thái

| Field | Giá trị | Ý nghĩa |
|---|---|---|
| approval_status | PENDING | Đang chờ kỹ sư xử lý |
| approval_status | APPROVED | Kỹ sư đã duyệt recommendation |
| approval_status | REJECTED | Kỹ sư từ chối, phải có reason |
| application_status | NOT_READY | Case mới, chưa approve |
| application_status | WAITING_APPLIED | Chemical recommendation đã approve |
| application_status | NOT_APPLICABLE | Policy/review path, không tạo chemical application |
| application_status | APPLIED | Đã tạo TreatmentApplication, PHI có hiệu lực |
| application_status | CANCELLED | Case bị reject |

| structured_path | Ý nghĩa |
|---|---|
| treatment | Có TreatmentRule hợp lệ; có thể chọn rule/dose |
| policy | Có policy nhưng không được đề xuất hóa chất |
| review_required | Chưa có rule/policy đủ điều kiện |

## 3. Tạo recommendation

Endpoint: POST /api/v1/disease/workflow/run

Content-Type: multipart/form-data

| Field | Bắt buộc | Kiểu | Ghi chú |
|---|---:|---|---|
| image | Có | image file | Tối đa 10 MiB |
| request_id | Không | string | Nên truyền giá trị ổn định để retry idempotent |
| parcel_code | Có nếu không có crop_season_id | string | Mã thửa/lô |
| crop_season_id | Không | UUID | AI kiểm tra season thuộc parcel |
| rag_question | Không | string | Câu hỏi bổ sung cho RAG |
| requested_by | Không | string <=100 | Actor dùng cho audit MVP |

~~~bash
curl -X POST http://127.0.0.1:8000/api/v1/disease/workflow/run \
  -F 'image=@tests/data/images/tomato_leaf.jpg;type=image/jpeg' \
  -F 'request_id=field-report-2026-0001' \
  -F 'parcel_code=DEMO-PARCEL-001' \
  -F 'rag_question=Triệu chứng và lưu ý cách ly là gì?' \
  -F 'requested_by=farmer-001'
~~~

Response HTTP 200 ở checkpoint:

~~~json
{
  "case_id": "8a5e7f6e-0e6f-4f2d-9f4f-25d7e6d4a901",
  "request_id": "field-report-2026-0001",
  "approval_status": "PENDING",
  "application_status": "NOT_READY",
  "cv_result": {
    "disease_code": "Tomato___Early_blight",
    "disease_name": "Bệnh đốm vòng sớm trên cà chua",
    "confidence": 0.91,
    "model_name": "efficientnet_b4",
    "model_version": "plant-disease-v1"
  },
  "field_context": {
    "parcel": {"id": "...", "code": "DEMO-PARCEL-001"},
    "current_season": {"id": "...", "status": "ACTIVE"},
    "latest_diagnosis": null,
    "latest_treatment": null,
    "harvest": {"is_blocked": false, "locked_until": null}
  },
  "treatment_rule": {
    "path": "treatment",
    "disease_code": "Tomato___Early_blight",
    "rules": [
      {
        "id": "rule-uuid",
        "pesticide": "Tên từ structured DB",
        "active_ingredient": "Hoạt chất từ structured DB",
        "dose_min": 1.0,
        "dose_max": 2.0,
        "dose_unit": "...",
        "phi_days": 7,
        "source": "...",
        "version": "...",
        "notes": "..."
      }
    ],
    "policy": null
  },
  "rag_context": [
    {"content": "Evidence nguyên văn", "source_ids": ["source-001"], "score": 0.82}
  ],
  "recommendation": {
    "path": "treatment",
    "disease_code": "Tomato___Early_blight",
    "confidence": 0.91,
    "summary": "...",
    "explanation": "...",
    "generation_mode": "evidence_fallback",
    "actions": [],
    "policy": null,
    "rag_sources": ["source-001"],
    "requires_engineer_approval": true,
    "low_confidence": false
  },
  "errors": [],
  "timings_ms": {
    "cv_inference": 120.0,
    "rag_retrieval": 13.0,
    "llm_generation": 5000.0,
    "workflow_total": 5200.0
  }
}
~~~

Gửi lại cùng request_id sẽ trả cùng case_id; Frontend không tạo case mới cho cùng request_id.

## 4. Đọc trạng thái và audit event

Endpoint: GET /api/v1/disease/workflow/{case_id}

Response durable state từ PostgreSQL:

~~~json
{
  "case_id": "...",
  "request_id": "field-report-2026-0001",
  "crop_season_id": "...",
  "disease_report_id": "...",
  "disease_code": "Tomato___Early_blight",
  "structured_path": "treatment",
  "recommendation": {},
  "rag_source_ids": ["source-001"],
  "low_confidence": false,
  "approval_status": "PENDING",
  "application_status": "NOT_READY",
  "approval_reason": null,
  "selected_rule_id": null,
  "approved_dose": null,
  "application": null,
  "events": [
    {
      "sequence": 1,
      "event_type": "RECOMMENDATION_PENDING",
      "actor_id": "farmer-001",
      "details": {
        "disease_code": "Tomato___Early_blight",
        "structured_path": "treatment",
        "low_confidence": false
      },
      "created_at": "2026-09-11T08:00:00+00:00"
    }
  ],
  "created_at": "2026-09-11T08:00:00+00:00",
  "updated_at": "2026-09-11T08:00:00+00:00"
}
~~~

Event hiện có:

| Event | Khi tạo | details |
|---|---|---|
| RECOMMENDATION_PENDING | Graph hoàn tất recommendation | disease_code, structured_path, low_confidence |
| APPROVED | Kỹ sư approve | selected_rule_id, approved_dose, application_status |
| REJECTED | Kỹ sư reject | reason |
| APPLIED | Xác nhận đã phun | application_id, actual_dose, PHI, lock timestamp |

sequence bắt đầu từ 1 và tăng theo từng case. Render timeline theo sequence.

## 5. Transition events

### Approve

Endpoint: POST /api/v1/disease/workflow/{case_id}/approve

~~~json
{
  "actor_id": "engineer-001",
  "selected_rule_id": "rule-uuid",
  "approved_dose": 1.5,
  "confirmed_low_confidence": false
}
~~~

Với treatment, selected_rule_id và approved_dose bắt buộc; dose phải nằm trong
[dose_min, dose_max]. Response là APPROVED + WAITING_APPLIED, chưa có application và PHI.

Với policy/review_required, không gửi chemical rule/dose; response là APPROVED +
NOT_APPLICABLE.

### Reject

Endpoint: POST /api/v1/disease/workflow/{case_id}/reject

~~~json
{
  "actor_id": "engineer-001",
  "reason": "Cần chụp lại ảnh rõ hơn"
}
~~~

Response là REJECTED + CANCELLED; reason được lưu trong case và event.

### Applied

Endpoint: POST /api/v1/disease/workflow/{case_id}/applied

~~~json
{
  "actor_id": "farmer-001",
  "actual_dose": 1.5,
  "applied_at": "2026-09-11T08:30:00Z"
}
~~~

Chỉ case APPROVED + WAITING_APPLIED + treatment mới được APPLIED. AI kiểm tra lại rule và
actual_dose rồi tạo application snapshot:

~~~json
{
  "application_status": "APPLIED",
  "application": {
    "id": "application-uuid",
    "pesticide_id": "...",
    "pesticide_name": "...",
    "active_ingredient": "...",
    "actual_dose": 1.5,
    "dose_unit": "...",
    "phi_days": 7,
    "rule_version": "...",
    "rule_source": "...",
    "applied_at": "2026-09-11T08:30:00+00:00",
    "harvest_locked_until": "2026-09-18T08:30:00+00:00",
    "status": "APPLIED"
  }
}
~~~

Retry cùng actual_dose và applied_at trả application cũ. Payload khác trả HTTP 409; AI không
clamp dose.

## 6. Endpoint phụ trợ

| Method | Endpoint | Dùng cho |
|---|---|---|
| GET | /api/v1/disease/health | Kiểm tra model đã load |
| GET | /api/v1/disease/class-contract | Lấy 38 canonical disease classes |
| POST | /api/v1/disease/predict | CV-only; multipart image |
| POST | /api/v1/disease/reports/{report_id}/diagnose | Lưu diagnosis cho report có sẵn |
| POST | /api/v1/disease/knowledge/retrieve | RAG retrieval có filter disease/top_k |

RAG request:

~~~json
{
  "query": "Triệu chứng bệnh sương mai cà chua",
  "disease_code": "Tomato___Late_blight",
  "top_k": 3
}
~~~

top_k nằm trong 1–20. Retrieval chỉ là evidence có provenance, không phải quyết định thuốc.

## 7. HTTP errors

| HTTP | Trường hợp | Cách xử lý |
|---:|---|---|
| 400 | Ảnh/UUID/context/request sai | Hiển thị lỗi input, không retry mù |
| 404 | Parcel, crop season hoặc case không tồn tại | Kiểm tra ID/context |
| 409 | Transition sai trạng thái hoặc retry APPLIED khác payload | Refresh case, hiển thị conflict |
| 413 | Ảnh lớn hơn 10 MiB | Yêu cầu ảnh nhỏ hơn |
| 422 | Dose ngoài range, thiếu reason/field approve | Hiển thị validation |
| 500 | Lỗi workflow nội bộ | Dùng message chung; xem log bằng request id |
| 503 | CV model/DB không sẵn sàng | Retry có backoff; không tự approve/apply |

Không hiển thị traceback, filesystem path, SQL hoặc secret cho người dùng cuối.

## 8. Hướng dẫn nối Backend và Frontend

Backend nên:

1. Tạo request_id ổn định cho một lần báo bệnh và truyền lại khi retry.
2. Proxy multipart tới workflow/run, lưu case_id cùng nghiệp vụ backend.
3. Chỉ role kỹ sư gọi approve/reject; chỉ actor được phép xác nhận applied.
4. Không chuyển APPROVED thành APPLIED nếu chưa có xác nhận thực tế.
5. Đồng bộ trạng thái từ GET workflow/{case_id} sau mỗi transition.

Frontend nên:

1. Hiển thị recommendation.path, low_confidence, rag_sources và events.
2. Với treatment, chỉ chọn selected_rule_id từ treatment_rule.rules và validate dose range.
3. Với policy/review, ẩn nút chemical approve/applied.
4. Chỉ hiển thị cách ly từ application.harvest_locked_until sau APPLIED.
5. Dùng events[].sequence làm audit timeline.

Runbook: ai/README_MVP_RUNBOOK.md.

