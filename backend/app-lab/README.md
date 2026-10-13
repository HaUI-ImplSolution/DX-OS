# app-lab: DX-Lab

Ứng dụng vận hành CNTT của khoa chạy trên core DX-OS.
Chỉ phụ thuộc `dx-core-api`; khởi động khi `DXOS_APP=lab`.

## Chức năng

| Khu vực | Nội dung |
|---|---|
| Yêu cầu hỗ trợ (ticket) | Gửi kèm ảnh, trao đổi công khai và nội bộ, tự nhận hoặc được gán, SLA theo ưu tiên, xác nhận và đánh giá, mở lại tối đa 3 lần |
| Thiết bị | Danh mục, nhật ký bảo trì, mượn trả có hạn, nhắc trước hạn và quá hạn |
| Đặt phòng | Đặt theo khung giờ, chống trùng giờ ở tầng cơ sở dữ liệu, duyệt, lịch theo phòng |
| Tri thức | Gợi ý loại và ưu tiên bằng luật, bài liên quan, hỏi đáp quy chế (RAG namespace `lab`) |
| Thống kê | SLA, thời gian phản hồi và xử lý, tải kỹ thuật viên, mức dùng phòng, thiết bị theo trạng thái |

## Vai

| Vai | Làm được |
|---|---|
| `STUDENT` | Gửi và theo dõi ticket của mình, mượn thiết bị, xem lịch phòng |
| `LECTURER` | Như sinh viên, đặt phòng, duyệt phiếu mượn của sinh viên |
| `TECHNICIAN` | Xử lý mọi ticket, quản lý phòng và thiết bị, duyệt đặt phòng và phiếu mượn của giảng viên, nhận lại thiết bị, xem thống kê |
| `ADMIN` | Mọi việc của kỹ thuật viên, gán và từ chối ticket, danh mục loại sự cố và SLA, duyệt mọi phiếu |

`TECHNICIAN` và `ADMIN` là vai nhân sự: thấy mọi ticket và đặt phòng.
Workspace của Lab có loại `CLASS` (lớp) và `DEPARTMENT` (bộ môn).

## Vòng đời

| Loại case | Trạng thái |
|---|---|
| `TICKET` | `NEW` → `ASSIGNED` → `IN_PROGRESS` ↔ `WAITING_USER`; `IN_PROGRESS` → `RESOLVED` → `CLOSED` hoặc về `IN_PROGRESS` khi mở lại; `NEW`, `ASSIGNED` → `REJECTED`; `NEW` → `CANCELLED` |
| `DEVICE_LOAN` | `PENDING` → `APPROVED` → `RETURNED`; `PENDING` → `REJECTED` hoặc `CANCELLED` |
| `ROOM_BOOKING` | `PENDING` → `APPROVED`; `PENDING` → `REJECTED`; `PENDING`, `APPROVED` → `CANCELLED` |

Trạng thái và lịch sử nằm ở workflow dùng chung (`workflow_case`, `workflow_event`).
Cột `status` trên bảng của Lab phản chiếu trạng thái đó, do handler cập nhật cùng transaction với quyết định.

## Quy tắc

**SLA** (giờ lịch, tính từ lúc gửi, chỉnh được ở `/api/v1/sla-policies`):

| Ưu tiên | Phản hồi | Xử lý |
|---|---|---|
| `URGENT` | 1 giờ | 4 giờ |
| `HIGH` | 4 giờ | 1 ngày |
| `NORMAL` | 1 ngày | 3 ngày |
| `LOW` | 2 ngày | 7 ngày |

- Hạn của ticket cố định lúc tạo; đổi chính sách chỉ áp dụng cho ticket tạo sau.
- Phản hồi đầu tiên là lúc kỹ thuật viên bắt đầu hoặc trả lời công khai đầu tiên.
- Lịch kiểm tra (`lab.sla.check-cron`) cảnh báo người xử lý khi đã dùng 80% thời gian và đánh dấu quá hạn khi qua hạn; mỗi thông báo gửi một lần cho mỗi ticket.
- Ticket quá hạn còn được đăng lên kênh chat kỹ thuật viên và quản trị.

**Mượn thiết bị**

- Chỉ mượn thiết bị `AVAILABLE`; thời hạn trả tối đa 7 ngày kể từ hôm nay.
- Mỗi người tối đa 2 phiếu đang chờ duyệt hoặc chưa trả.
- Phiếu của sinh viên do giảng viên duyệt, của giảng viên do kỹ thuật viên duyệt; `ADMIN` duyệt được mọi phiếu; không ai tự duyệt phiếu của mình.
- Duyệt đặt thiết bị ở `ON_LOAN`; trả tốt về `AVAILABLE`, trả hỏng sang `UNDER_REPAIR` kèm nhật ký sửa chữa.

**Đặt phòng**

- Giờ theo bước 30 phút, tối đa 4 giờ mỗi lần, đặt trước tối đa 30 ngày, không đặt vào quá khứ.
- Số người không vượt sức chứa; phòng `MAINTENANCE` không nhận đặt; `STUDENT` không tự đặt.
- Yêu cầu giữ khung giờ từ lúc gửi đến khi bị từ chối hoặc hủy.
  Hai lịch còn hiệu lực không thể chồng giờ cùng phòng: ràng buộc `EXCLUDE USING gist` (extension `btree_gist`) chặn cả hai yêu cầu gửi cùng lúc.
- Chuyển phòng sang bảo trì không hủy lịch; phản hồi liệt kê các lịch bị ảnh hưởng để người quản lý xử lý.

## Interface của core đã cài

| Interface | Lab cài |
|---|---|
| `AppModule` | `LabAppModule`: id `lab`, menu, loại workspace `CLASS` và `DEPARTMENT` |
| `RoleProvider` | `LabSecurityPolicies`: `STUDENT`, `LECTURER`, `TECHNICIAN`, `ADMIN` |
| `CaseTypeDefinition` | `LabWorkflowConfig`: `TICKET`, `DEVICE_LOAN`, `ROOM_BOOKING` |
| `WorkflowDecisionHandler` | `TicketDecisionHandler`, `DeviceLoanDecisionHandler`, `RoomBookingDecisionHandler` |
| `CaseSummaryProvider` | `TicketSummaryProvider`, `DeviceLoanSummaryProvider`, `RoomBookingSummaryProvider` |
| `NotificationTypeDefinition`, `NotificationChannel` | `LabNotificationConfig`: 12 loại thông báo, kênh `technician` và `admin` |
| `BiDatasetProvider` | `LabPlatformConfig`: các view `vw_lab_*` |
| `FileProcessor` | `TicketAttachmentProcessor`: JPEG, PNG, PDF tối đa 5 MiB, loại theo nội dung |
| `KnowledgeNamespace` | `LabKnowledgeNamespace`: namespace `lab` |
| `HealthContributor` | `ai` |

## Dữ liệu

- Migration `db/migration/lab` (bảng lịch sử `flyway_schema_history_lab`), chạy sau migration của core.
- `V3` nạp sẵn 8 loại sự cố và 4 chính sách SLA mặc định.
- View BI `vw_lab_rooms`, `vw_lab_devices`, `vw_lab_tickets`, `vw_lab_device_loans`, `vw_lab_room_bookings` chỉ chứa mã, id và nhóm; không có nội dung ticket, tên người hay tiêu đề lịch.
- Tri thức: `data/rag/lab/`, job `rag-ingest` tự nạp vào namespace `lab` khi dựng stack với `DXOS_APP=lab`.

## Cấu hình

| Thuộc tính | Biến môi trường | Mặc định |
|---|---|---|
| `lab.sla.check-cron` | `LAB_SLA_CHECK_CRON` | `0 */5 * * * *` |
| `lab.sla.warning-ratio` | `LAB_SLA_WARNING_RATIO` | `0.8` |
| `lab.reminder.loan-cron` | `LAB_LOAN_REMINDER_CRON` | `0 0 7 * * *` |
| `lab.reminder.loan-due-soon-days` | `LAB_LOAN_DUE_SOON_DAYS` | `1` |
| `lab.classifier.rules-path` | `LAB_CLASSIFIER_RULES_PATH` | `classpath:lab/classifier-rules.json` |
| `lab.stats.room-open-hours-per-day` | `LAB_ROOM_OPEN_HOURS_PER_DAY` | `10` |
| `mattermost.webhook.url-technician` | `MATTERMOST_WEBHOOK_URL_TECHNICIAN` | trống: dùng URL mặc định |
| `mattermost.webhook.url-admin` | `MATTERMOST_WEBHOOK_URL_ADMIN` | trống: dùng URL mặc định |

Luật phân loại ở `src/main/resources/lab/classifier-rules.json`: từ khóa theo loại và luật nâng ưu tiên, kèm lý do.
Từ khóa quản trị viên thêm vào một loại sự cố (`keywords`) được so khớp cùng tệp luật; so khớp bỏ qua hoa thường, dấu và dấu câu, theo từ nguyên vẹn.

## API

Swagger UI ở `/swagger-ui.html`.

| Nhóm | Đường dẫn |
|---|---|
| Danh mục | `/api/v1/rooms`, `/api/v1/devices`, `/api/v1/ticket-categories`, `/api/v1/sla-policies` |
| Ticket | `/api/v1/tickets` (`suggest`, `technicians`, `{id}/comments`, `attachments`, `assign`, `start`, `wait`, `resolve`, `confirm`, `reopen`, `cancel`, `reject`) |
| Thiết bị | `/api/v1/devices/{id}/maintenance`, `/api/v1/device-loans` (`approve`, `reject`, `return`, `cancel`) |
| Đặt phòng | `/api/v1/room-bookings` (`approve`, `reject`, `cancel`), `/api/v1/rooms/{id}/schedule` |
| Tri thức | `/api/v1/knowledge/ask` |
| Hàng đợi, thống kê | `/api/v1/approval-requests`, `/api/v1/lab-stats/summary` |

Lỗi nghiệp vụ trả 422 kèm mã (`ROOM_SLOT_TAKEN`, `DEVICE_NOT_AVAILABLE`, `TICKET_REOPEN_LIMIT`...); xử lý đồng thời trả 409.

## Giới hạn

- Danh sách kỹ thuật viên (`/api/v1/tickets/technicians`) gồm những người đã từng nhận ticket; chưa có danh bạ người dùng theo vai.
- Người nhận cảnh báo quá hạn là người xử lý (thông báo trong app) và kênh chat; quản trị viên nhận qua kênh chat và bộ lọc `overdue=true`.
- Thời gian chờ người dùng vẫn tính vào SLA.
