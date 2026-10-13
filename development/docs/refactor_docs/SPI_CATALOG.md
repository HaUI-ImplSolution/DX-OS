# Danh mục interface của core

Interface khai báo một lần ở `dx-core-api`; mỗi app cài theo cách riêng.
Nguyên tắc: chỉ có interface khi Farm và Lab thật sự khác nhau.
Phần giống nhau nằm trực tiếp trong `dx-core` (RBAC, base entity, error envelope, idempotency, giới hạn tốc độ, phân trang, audit và xóa mềm).

## Interface app cài

App khai báo mỗi interface bằng một Spring bean; core gom các bean đó lúc khởi động và không cần interface nào có mặt.

| Interface | Mục đích | Farm cài | Lab cài |
|---|---|---|---|
| `AppModule` | id, tên, menu, loại workspace, loại case tính giới hạn yêu cầu chờ | `farm` | `lab` |
| `RoleProvider` | Vai của app (`RoleDefinition`: tên, mô tả, cờ nhân sự); core tạo vai thiếu trong Keycloak lúc khởi động (cần service account có quyền tạo vai; thiếu quyền thì chỉ ghi log) | FARMER, ENGINEER, ADMIN | STUDENT, LECTURER, TECHNICIAN, ADMIN |
| `CaseTypeDefinition` | Loại case: trạng thái (`CaseStatus`), bước chuyển (`CaseTransition`) và vai được thực hiện; bean, không phải interface | 5 loại duyệt, dùng `CaseTypeDefinition.approval(...)` | `TICKET` (nhiều trạng thái), `DEVICE_LOAN`, `ROOM_BOOKING` |
| `WorkflowDecisionHandler` | Áp dụng quyết định lên entity của app | 3 handler | handler ticket và mượn thiết bị |
| `CaseSummaryProvider` | Mô tả entity của một `entityType` cho hộp duyệt, theo lô; liệt kê id entity của một workspace | thửa, vụ, sửa thửa, chẩn đoán, phun | ticket, phiếu mượn, đặt phòng |
| `NotificationTypeDefinition` | Loại thông báo (mã, nhãn, biểu tượng); bean | 13 loại | 12 loại: ticket, phiếu mượn, đặt phòng |
| `NotificationChannel` | Kênh chat app đăng bài (khóa, nhãn); URL webhook đọc từ `mattermost.webhook.url-<khóa>` | `admin`, `engineer` | `technician`, `admin` |
| `BiDatasetProvider` | View và bảng vai chỉ đọc của Superset được `SELECT` | `vw_*` nông nghiệp và danh mục | `vw_lab_*` |
| `FileProcessor` | Kiểm tra và làm sạch tệp theo mục đích trước khi lưu | ảnh lá: chỉ JPEG/PNG, xóa EXIF | tệp đính kèm ticket: JPEG, PNG, PDF tối đa 5 MiB, loại theo nội dung |
| `KnowledgeNamespace` | Tên corpus RAG và danh mục nguồn của corpus | `farm` | `lab` |
| `EndpointPolicyContributor` | Đường dẫn GET công khai | `diseases`, `pesticides`, `treatment-rules` | không có |
| `ClientRestrictionPolicy` | Vai chỉ được dùng một số client | FARMER chỉ dùng mobile | không có |
| `HealthContributor` | Thêm dịch vụ vào `GET /api/v1/system/status` | AI | AI |

## Facade app gọi (core cài)

| Facade | Dùng bởi |
|---|---|
| `WorkflowOperations` | mở case, ghi bước, gán người phụ trách, đếm case chờ, tìm case theo `CaseQuery` |
| `ApprovalInbox` | danh sách và chi tiết yêu cầu chờ duyệt, lọc theo workspace và ngày |
| `PendingRequestLimit` | chặn người dùng gửi thêm khi đã có đủ yêu cầu chờ |
| `WorkspaceDirectory` | tra workspace theo id, mã, tài khoản; liệt kê theo loại; thành viên |
| `WorkspaceAdministration` | tạo workspace kèm tài khoản chủ, gắn tài khoản chủ, thêm thành viên |
| `UserContext` | id, tên, vai (`roles`, `hasRole`, `isStaff`) và workspace của người đang đăng nhập |
| `ObjectStorage` | lưu, tạo link có hạn, kiểm tra sẵn sàng |
| `FileStore` | xử lý và lưu tệp qua `FileProcessor` của mục đích |
| `NotificationPublisher` | ghi thông báo trong app, có khóa chống trùng |
| `ChatWebhook` | đăng một payload lên webhook của kênh chat |
| `AiGateway` | gọi AI service kèm khóa dịch vụ: health, JSON, multipart |
| `KnowledgeGateway` | hỏi đáp RAG theo namespace, lọc theo ngưỡng điểm, gắn nguồn |

## Kiểu dữ liệu công khai

- `CaseView`: id, caseType, entityType, entityId, status (chuỗi), createdBy, assignedTo, thời điểm; thay cho entity `WorkflowCase`.
- `WorkspaceView`: id, code, type, name, phone, address, ownerUserId, attributes, createdAt; thay cho entity `Workspace`.
- `WorkspaceMemberView`: workspaceId, userId, role (`OWNER` hoặc `MEMBER`), createdAt.
- `InboxItem`: case, `CaseSummary` của entity và workspace của entity.
- Entity của core (`WorkflowCase`, `Workspace`, `WorkspaceMember`, `Notification`) không nằm trong `dx-core-api`.

## Quy ước

- Trạng thái và hành động của case là chuỗi; `WorkflowStatus` và `WorkflowAction` chỉ liệt kê tên của luồng duyệt chuẩn.
- Loại thông báo là chuỗi; core từ chối loại chưa được app đăng ký.
- Controller có tên trường riêng của app (`/api/v1/households`, `/api/v1/approval-requests`, `/api/v1/knowledge/ask`) nằm ở app và dựng từ facade; core chỉ có controller với JSON trung tính.

## Dựng một app mới

1. Thêm module Maven chỉ phụ thuộc `dx-core-api` và đăng ký auto-configuration có điều kiện `dxos.app=<id>`.
2. Cài `AppModule` và `RoleProvider`; khai báo `CaseTypeDefinition`, `NotificationTypeDefinition` cho từng loại cần dùng.
3. Với mỗi loại case có hộp duyệt, cài `WorkflowDecisionHandler` và `CaseSummaryProvider`.
4. Thêm migration vào location riêng và bảng lịch sử Flyway riêng; core chạy trước.
5. Thêm `application-<id>.yaml` và id vào danh sách app hợp lệ của `dx-host`.
