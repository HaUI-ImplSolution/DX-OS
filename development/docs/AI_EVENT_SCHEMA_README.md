# DX-Farm AI API Contract

AI Service là dịch vụ suy luận stateless.
Backend sở hữu báo cáo bệnh, diagnosis, trạng thái phê duyệt và lịch sử áp dụng thuốc.

## Endpoint suy luận

`POST /api/v1/diagnose` nhận JSON theo DTO `AiDiagnoseRequest` của Backend.
Request bắt buộc có `request_id`, `crop_season_id`, `image_url` và `field_context`.
Backend xác thực bằng header `X-Service-Api-Key`.

AI thực hiện lần lượt CV, lấy structured facts từ Backend, truy vấn RAG và chạy LangGraph để tạo recommendation.
Response khớp DTO `AiDiagnoseResponse`, gồm `request_id`, `cv_result`, `rag_evidence`, `structured_context` và `recommendation`.

Thuốc, `dose_amount`, `phi_days` và `version` phải giữ nguyên từ structured facts do Backend cung cấp.
Backend kiểm tra lại rule version và lưu recommendation ở trạng thái `PENDING`.

Contract JSON đầy đủ được định nghĩa tại mục 5 của [`ai_backend_decoupling_plan.md`](./ai_backend_decoupling_plan.md).

## Endpoint hỗ trợ

| Method | Endpoint | Vai trò |
|---|---|---|
| `GET` | `/api/v1/disease/health` | Trạng thái model CV |
| `GET` | `/api/v1/disease/class-contract` | Danh sách mã bệnh canonical |
| `POST` | `/api/v1/disease/predict` | Suy luận CV độc lập bằng multipart image |
| `POST` | `/api/v1/disease/knowledge/retrieve` | Truy vấn RAG độc lập |

## Giới hạn hiện tại

P2-3 chưa trả `management_policy` cho nhánh `NO_CHEMICAL_CONTROL`.
Docker Compose chưa truyền đầy đủ URL và API key giữa AI với Backend.
