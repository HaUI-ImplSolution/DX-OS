# AI Web E2E Demo

Tài liệu này mô tả các thay đổi cần thiết để chạy luồng báo bệnh trên web, qua Backend và AI Service, rồi kiểm tra bằng ba role FARMER, ENGINEER và ADMIN.

## Khởi động

Tại thư mục `deploy/`, chạy:

```bash
docker compose up -d --build
```

Compose khởi động PostgreSQL, Backend, frontend, Keycloak, AI Service, Ollama và các dịch vụ demo hiện có. Job `rag-ingest` nạp corpus vào AI Vector DB; `ollama-init` bảo đảm model được tải trước khi AI Service chạy. Lần đầu cần mạng và dung lượng trống để tải container, model CV, embedding model và LLM.

Corpus RAG mặc định lấy từ `ai/.local/data` qua `AI_DATA_DIR`. Đây là dữ liệu local, không được commit lên Git; cần có corpus tại đường dẫn này trước khi ingest.

## Địa chỉ và tài khoản

| Thành phần | Địa chỉ |
|---|---|
| Portal | `http://localhost:8090` |
| Web chính | `http://localhost:8082` |
| Backend Swagger | `http://localhost:8081/swagger-ui.html` |
| AI health | `http://localhost:8000/api/v1/disease/health` |
| Keycloak | `http://localhost:8180` |

| Role | Tài khoản | Mật khẩu |
|---|---|---|
| FARMER | `farmer1` | `farmer123` |
| ENGINEER | `engineer1` | `engineer123` |
| ADMIN | `admin1` | `admin123` |

## Luồng được nối

1. FARMER tạo thửa, vụ mùa và báo cáo bệnh kèm ảnh.
2. ENGINEER chọn **Chạy chẩn đoán AI**. Frontend gọi endpoint Backend `/api/v1/disease-reports/{id}/diagnosis/ai`.
3. Backend gửi URL ảnh và ngữ cảnh thực địa tới AI Service. AI chạy CV, lấy dữ kiện cấu trúc từ Backend, tìm bằng chứng theo ngữ nghĩa trong pgvector rồi tạo khuyến nghị có căn cứ.
4. Backend lưu kết quả AI vào `Diagnosis`; ENGINEER hoặc ADMIN có thể duyệt/từ chối.
5. Chỉ khi có treatment rule nghiệp vụ hợp lệ mới có thể ghi nhận áp dụng thuốc. Thiếu rule thì kết quả vẫn được lưu và phải được kỹ sư xem xét; Backend chặn áp dụng thuốc.

## Thay đổi chính

- Frontend có nút gọi chẩn đoán AI và hiển thị khuyến nghị AI Backend trả về; luồng nhập chẩn đoán thủ công vẫn còn để demo nghiệp vụ.
- Backend giữ summary do AI tạo, hỗ trợ kết quả `review_required` không có rule, cho phép `treatment_rule_id` rỗng và từ chối áp dụng khi rule không tồn tại.
- Seed Backend bổ sung danh mục nhận diện của 38 lớp CV. Đây chỉ là danh tính bệnh, không tự tạo thuốc hoặc treatment rule.
- AI chấp nhận structured context không có rule như trạng thái cần kỹ sư xem xét.
- Docker Compose nạp corpus RAG một lần và chạy Ollama/model cùng stack để lần chạy không phụ thuộc Ollama đang mở sẵn trên host.
- Backend AI client dùng HTTP/1.1 để request JSON tương thích với Uvicorn trong container.

## Kết quả kiểm tra E2E

Đã chạy trên máy local với ảnh thử và dữ liệu demo:

- FARMER tạo báo cáo được; gọi endpoint chẩn đoán bị từ chối `403`.
- ENGINEER chạy thành công CV → Backend facts → semantic RAG → Ollama. Ảnh thử được nhận diện là `Tomato___Early_blight` với confidence `0.981`; RAG trả 3 đoạn evidence từ nguồn `S03` và `S21`.
- Bệnh thử chưa có treatment rule được duyệt: kết quả được lưu ở trạng thái chờ duyệt, ADMIN duyệt được, còn áp dụng thuốc bị chặn `409`.
- Các URL chính của frontend, Backend, AI và Keycloak trả HTTP `200`; Compose hợp lệ và các service cần thiết đều chạy.

Kết quả nhận diện phụ thuộc ảnh đầu vào và không phải cam kết chính xác cho mọi ảnh. RAG chỉ cung cấp kiến thức tham khảo; liều lượng, PHI và quyết định điều trị phải đến từ rule nghiệp vụ được Backend quản lý.
