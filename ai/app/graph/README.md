# LangGraph — Điều phối suy luận AI

## Vai trò

`app/graph/` điều phối một lần suy luận stateless của `POST /api/v1/diagnose`.
Graph kết hợp kết quả CV, field context từ request Backend, structured facts từ Backend REST API và evidence từ AI Vector DB để tạo recommendation.

Graph không lưu dữ liệu nghiệp vụ, không quản lý phê duyệt và không tạo lịch sử áp dụng thuốc.
Backend sở hữu toàn bộ trạng thái `PENDING`, `APPROVED`, `REJECTED` và `APPLIED`.

## Luồng hoạt động

```text
resolve_request
  → cv_diagnosis
  → load_field_context
  → treatment_lookup
  → retrieve_knowledge
  → generate_recommendation
  → END
```

- `resolve_request` kiểm tra request ID, ảnh, crop season và field context.
- `cv_diagnosis` tạo `disease_code` canonical cùng confidence và metadata model.
- `load_field_context` sử dụng context do Backend gửi, không đọc Backend DB.
- `treatment_lookup` gọi Backend REST client bằng `disease_code`.
- `retrieve_knowledge` chỉ đọc `documents` và `document_chunks` trong AI Vector DB.
- `generate_recommendation` kết hợp facts và evidence nhưng không được sửa thuốc, liều, PHI hoặc rule version.

Graph dùng một bảng quyết định fail-safe: confidence dưới ngưỡng đi `low_confidence_review`; confidence đạt ngưỡng và đúng một rule đi `treatment`; 0 hoặc nhiều rule đi `review_required`. Chỉ `treatment` có `selected_rule_id`; thứ tự rule không được dùng để ra quyết định.

## State và runtime

`FarmState` chỉ giữ dữ liệu của một lần suy luận.
`WorkflowRuntime` inject CV predictor, Backend structured-facts tool, RAG retriever và recommendation writer mà không đưa connection hoặc service object vào state.

## Ranh giới lỗi

CV hoặc structured facts thất bại thì lượt suy luận thất bại.
RAG không khả dụng thì graph vẫn có thể giữ structured recommendation và ghi nhận `RAG_UNAVAILABLE`.
AI giới hạn mỗi lần gọi Backend ở 3 giây và retry một lần cho lỗi transport hoặc HTTP `502`/`503`/`504`.
Khi Backend không cung cấp được structured facts, graph dừng ngay; không chạy RAG hoặc tạo recommendation chỉ dựa trên evidence.

P2-3 hiện chưa cung cấp contract cho `management_policy` và `NO_CHEMICAL_CONTROL`.
