# Danh mục interface của core

Interface khai báo một lần ở `dx-core-api`; mỗi app cài theo cách riêng.
Nguyên tắc: chỉ có interface khi Farm và Lab thật sự khác nhau.
Phần giống nhau nằm trực tiếp trong `dx-core` (RBAC, base entity, error envelope, idempotency, giới hạn tốc độ, phân trang, audit và xóa mềm).

## Interface app cài

| Interface | Mục đích | Farm cài | Lab cài | Phase |
|---|---|---|---|---|
| `AppModule` | Khai báo app: id, tên, route, manifest cho web | `farm` | `lab` | C1, C2 |
| `RoleProvider` | Vai và quyền của app; core tạo vai trong Keycloak lúc khởi động | FARMER, ENGINEER, ADMIN | STUDENT, LECTURER, TECHNICIAN, ADMIN | C2, C3 |
| `CaseTypeDefinition` | Loại case, trạng thái, bước chuyển và vai được phép | duyệt thửa, vụ, chẩn đoán, xác nhận phun | `TICKET` (nhiều trạng thái), `DEVICE_LOAN`, `ROOM_BOOKING` | C2 |
| `WorkflowDecisionHandler` | Áp dụng quyết định lên entity của app | 3 handler hiện có | handler ticket và mượn thiết bị | C1, C2 |
| `CaseSummaryProvider` | Làm giàu hộp duyệt bằng thông tin entity | thửa, vụ, chẩn đoán, phun | ticket, phiếu mượn, đặt phòng | C2 |
| `NotificationChannel` | Gửi lời nhắn ra kênh ngoài | Mattermost (kênh ADMIN, ENGINEER) | Mattermost (kênh kỹ thuật viên, quản trị) | C2 |
| `BiDatasetProvider` | Đăng ký view và bảng cho vai chỉ đọc của Superset | `vw_*` nông nghiệp | `vw_lab_*` ticket, SLA, phòng, thiết bị | C2 |
| `FileProcessor` | Xử lý tệp trước khi lưu | xóa EXIF ảnh lá | giới hạn loại và dung lượng tệp đính kèm | C2 |
| `KnowledgeNamespace` | Tập tài liệu RAG và danh mục nguồn | tài liệu bệnh lúa | quy chế, hướng dẫn CNTT | C2, C4 |
| `EndpointPolicyContributor` | Endpoint công khai và quy tắc truy cập riêng | `diseases`, `pesticides`, `treatment-rules` | không có (danh mục cần đăng nhập) | C2 |
| `ClientRestrictionPolicy` | Vai nào chỉ được dùng client nào | FARMER chỉ dùng mobile | không có | C2 |
| `HealthContributor` | Thêm dịch vụ vào trạng thái hệ thống | AI chẩn đoán, thời tiết | không có | C2 |

## Facade app gọi (core cài)

| Facade | Thay cho | Dùng bởi | Phase |
|---|---|---|---|
| `WorkflowOperations` | `WorkflowService` | mở case, ghi bước, gán người phụ trách, đếm case chờ, tìm case theo `CaseQuery` | C1 |
| `WorkspaceDirectory` | `HouseholdService`, `HouseholdRepository` | tra workspace theo id, mã hoặc tài khoản, tra nhiều id một lần | C1 |
| `ObjectStorage` | `StorageService` | lưu, tạo link có hạn, kiểm tra sẵn sàng | C1 |
| `UserContext` | `CurrentUserService` | id, tên, vai và workspace của người đang đăng nhập | C1 |
| `NotificationPublisher` | `NotificationService.notify*` | gửi thông báo trong app, có khóa chống trùng | C2 |
| `KnowledgeGateway` | `KnowledgeService` | hỏi đáp RAG theo namespace | C2 |
| `AiGateway` | phần chung của `AiDiagnosisClient` | gọi AI service với khóa dịch vụ, circuit breaker, health | C2 |

## Kiểu dữ liệu công khai

- `CaseView`: id, caseType, entityType, entityId, status, createdBy, assignedTo, thời điểm; thay cho entity `WorkflowCase`.
- `WorkspaceView`: id, code, fullName, keycloakUserId ở C1; thêm type, owner, thuộc tính ở C2; thay cho entity `Household`.
- Entity của core (`WorkflowCase`, `Workspace`, `Notification`) không nằm trong `dx-core-api`.
