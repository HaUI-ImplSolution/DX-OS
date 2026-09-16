# LangGraph — Workflow tạo khuyến nghị

## Vai trò của thư mục này

`app/graph/` điều phối luồng tạo khuyến nghị cho một báo cáo bệnh cây. LangGraph ghép kết quả CV, bối cảnh ruộng/vụ mùa, dữ liệu điều trị có cấu trúc và kiến thức RAG thành một khuyến nghị ở trạng thái chờ duyệt.

Graph chỉ tạo recommendation. Nó không là nguồn lưu trạng thái nghiệp vụ dài hạn và không tự quyết định hoặc ghi nhận việc sử dụng thuốc.

`WorkflowRunner` ở `app/application/` gọi graph, còn PostgreSQL cùng `workflow_service` sở hữu các chuyển trạng thái bền vững và audit.

> **Trạng thái**: Router `/disease/workflow/*` cùng `WorkflowRunner`/`WorkflowTransitions` đã được xóa ở mục **P1-3** của Phase 1 Decoupling ([`../../development/docs/ai_backend_decoupling_checklist.md`](../../../development/docs/ai_backend_decoupling_checklist.md)).
> LangGraph hiện phục vụ luồng suy luận phi trạng thái (stateless inference) trong contract `POST /api/v1/diagnose`, không còn lưu `WorkflowCase` vào DB. Vòng đời duyệt/áp dụng do Backend sở hữu.

## Phạm vi

LangGraph chịu trách nhiệm:

- Kiểm tra dữ liệu đầu vào tối thiểu của một workflow request.
- Gọi CV hoặc dùng kết quả CV đã có để nhận `disease_code`.
- Lấy bối cảnh thửa ruộng và vụ mùa.
- Tra cứu quy tắc điều trị/policy từ structured knowledge.
- Tra cứu evidence RAG theo `disease_code` và câu hỏi tùy chọn.
- Tổng hợp recommendation có action, policy, evidence và cờ low-confidence.
- Trả kết quả để `WorkflowRunner` tạo `WorkflowCase` ở trạng thái `PENDING`.

LangGraph không chịu trách nhiệm:

- Không approve, reject hoặc đánh dấu thuốc đã được áp dụng.
- Không tự chọn thuốc, liều lượng hoặc bỏ qua giới hạn PHI.
- Không là nơi lưu durable workflow state hay audit event.
- Không thay thế PostgreSQL trong việc kiểm tra rule, khóa giao dịch và idempotency.
- Không coi RAG/LLM là nguồn dữ liệu điều trị có thẩm quyền.

## Luồng hoạt động

```text
POST /workflow/run
  → WorkflowRunner kiểm tra request_id idempotent
  → giải quyết parcel / crop season và tạo DiseaseReport PENDING
  → khởi tạo FarmState + WorkflowRuntime
  → LangGraph:

    resolve_request
      → cv_diagnosis
      → load_field_context
      → treatment_lookup
      → retrieve_knowledge
      → generate_recommendation
      → END

  → WorkflowRunner tạo WorkflowCase PENDING trong PostgreSQL
  → kỹ sư approve hoặc reject qua workflow transitions bên ngoài graph
  → chỉ workflow đã APPROVED mới có thể được xác nhận APPLIED
```

Graph luôn dừng trước hành động của con người. Việc approve/reject/applied chạy qua `WorkflowTransitions` và `workflow_service`, sử dụng PostgreSQL làm nguồn sự thật.

## Các node

| Node | Vai trò |
|---|---|
| `resolve_request` | Kiểm tra `request_id`, ảnh đầu vào và `parcel_code` hoặc `crop_season_id`. |
| `cv_diagnosis` | Gọi CV, kiểm tra `disease_code`, confidence và metadata model; lưu diagnosis khi có database/report. |
| `load_field_context` | Lấy bối cảnh thửa ruộng và vụ mùa hiện tại. |
| `treatment_lookup` | Lấy treatment rule hoặc management policy theo `disease_code`. |
| `retrieve_knowledge` | Lấy evidence RAG; lỗi RAG không chặn structured recommendation. |
| `generate_recommendation` | Ghép dữ liệu có cấu trúc, evidence và phần giải thích thành recommendation. |

## State và runtime dependency

`FarmState` là state dữ liệu của graph: request, ảnh, kết quả CV, context ruộng, treatment rule, RAG context, recommendation, errors và timings.

`WorkflowRuntime` chứa dependency của một lần chạy như database session, vision tool, RAG tool và recommendation writer. Dependency không nằm trong state để state chỉ chứa dữ liệu workflow, không chứa connection hay object hạ tầng.

Các tool có thể được inject khi test hoặc tích hợp. Nếu không inject, node dùng service/database mặc định của hệ thống.

## Guardrail và an toàn

- `cv_diagnosis` chỉ chấp nhận `disease_code` nằm trong class contract CV, confidence hữu hạn trong khoảng `0–1`, cùng model name/version.
- Confidence thấp hơn `CV_CONFIDENCE_THRESHOLD` được đánh dấu `low_confidence`; approval sau đó cần xác nhận rõ ràng của kỹ sư.
- `treatment_lookup` lấy thuốc, liều lượng, PHI và policy từ structured knowledge, không lấy từ RAG hay LLM.
- RAG không khả dụng thì graph giữ structured recommendation, thêm lỗi `RAG_UNAVAILABLE` và trả context rỗng.
- Recommendation chỉ đưa các treatment rule đã được truy vấn vào `actions`; policy/review path không được sinh thuốc hóa học.
- Phần wording từ Ollama chỉ được dùng nếu là excerpt xuất hiện nguyên văn trong evidence; nếu không, hệ thống dùng evidence fallback.

## Ranh giới với PostgreSQL workflow

Sau khi graph kết thúc, `WorkflowRunner` lưu recommendation thành `WorkflowCase` với `approval_status=PENDING`.

Các chuyển trạng thái tiếp theo không thuộc LangGraph:

```text
PENDING
  → APPROVED hoặc REJECTED
  → APPLIED (chỉ khi APPROVED + treatment path)
```

`workflow_service` kiểm tra rule được chọn thuộc đúng bệnh, liều lượng nằm trong khoảng được duyệt, low-confidence đã được xác nhận và ghi `WorkflowEvent` append-only. Khi APPLIED, service tạo bản ghi treatment application và tính thời điểm khóa thu hoạch theo PHI.

## Giới hạn

- Graph hiện là tuyến tuần tự cố định, không có agent tự chọn tool hay tự thay đổi luồng.
- Recommendation không đồng nghĩa với phê duyệt hoặc hành động đã áp dụng.
- Lỗi database, structured lookup hoặc CV làm workflow thất bại; RAG là dependency mềm và có fallback.
- Chất lượng kết quả CV/RAG ảnh hưởng chất lượng recommendation, nhưng không được phép vượt qua validation và guardrail nghiệp vụ.
