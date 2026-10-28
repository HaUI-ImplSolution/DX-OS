# LangGraph: điều phối suy luận

`app/graph/` điều phối một lượt suy luận stateless của `POST /api/v1/diagnose`: kết hợp kết quả CV, field context từ Backend, structured facts từ Backend REST và evidence từ AI Vector DB thành recommendation.
Graph không lưu dữ liệu nghiệp vụ, không quản lý phê duyệt; Backend sở hữu trạng thái `PENDING`, `APPROVED`, `REJECTED`, `APPLIED`.

## Luồng

```text
resolve_request → cv_diagnosis → load_field_context → treatment_lookup
  → retrieve_knowledge → generate_recommendation → END
```

| Bước | Việc |
|---|---|
| `resolve_request` | Kiểm request ID, ảnh, crop season, field context |
| `cv_diagnosis` | `disease_code` canonical, confidence, metadata model |
| `load_field_context` | Dùng context do Backend gửi, không đọc Backend DB |
| `treatment_lookup` | Gọi Backend REST theo `disease_code` |
| `retrieve_knowledge` | Chỉ đọc `documents`, `document_chunks` |
| `generate_recommendation` | Kết hợp facts và evidence, không sửa thuốc, liều, PHI, rule version |

`FarmState` chỉ giữ dữ liệu của một lượt; `WorkflowRuntime` inject CV predictor, structured-facts tool và RAG retriever mà không đưa connection hay service object vào state.
Khi `include_explanation=true`, service tổng hợp lời giải thích sau graph và giải phóng giao dịch đọc trước khi chờ Qwen; quyết định rule không phụ thuộc phần sinh.

## Ranh giới lỗi

- CV hoặc structured facts thất bại thì lượt thất bại; không chạy RAG hay tạo recommendation chỉ từ evidence.
- RAG không khả dụng thì giữ structured recommendation và ghi `RAG_UNAVAILABLE`.
- Mỗi lần gọi Backend giới hạn 3 giây, retry một lần cho lỗi transport hoặc HTTP `502`/`503`/`504`.
- Chưa có contract cho `management_policy` và `NO_CHEMICAL_CONTROL`.
