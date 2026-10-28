# Kênh chat nội bộ (Mattermost)

Nhận thông báo một chiều từ Backend và cảnh báo từ `dx-monitor` qua Incoming Webhook.
Chạy bằng image chính thức `mattermost/mattermost-team-edition`, không build lại và không dùng `server/enterprise/` (AGPL-3.0, giấy phép các thành phần ở [`LICENSES.md`](../LICENSES.md)).
Service khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).

## Cách hoạt động

- Database Postgres riêng `mattermost`, Backend tạo role và database lúc khởi động (`MattermostDatabaseSeeder`); dữ liệu ở `deploy/data/mattermost/`.
- [`bootstrap.sh`](./bootstrap.sh) chạy một lần qua REST API (idempotent): tạo System Admin, team `DX-Farm HTX` và channel `Thông báo chung`, `Kỹ sư nông nghiệp` (Farm), `Kỹ thuật viên` (Lab), `Ban quản trị` (private).
- Đăng nhập tách khỏi Keycloak có chủ đích vì Team Edition không hỗ trợ OIDC với IdP tùy ý.
- Truy cập `http://${PUBLIC_HOST}:${MATTERMOST_HOST_PORT}` (mặc định `8065`) bằng `MATTERMOST_ADMIN_USERNAME` / `MATTERMOST_ADMIN_PASSWORD` trong `deploy/.env`.

## Thông báo từ Backend

| App | Channel (biến webhook) | Sự kiện |
|---|---|---|
| Farm | `Ban quản trị` (`MATTERMOST_WEBHOOK_URL_ADMIN`) | Yêu cầu thửa mới, yêu cầu vụ mùa mới (kể cả gửi lại) |
| Farm | `Kỹ sư nông nghiệp` (`MATTERMOST_WEBHOOK_URL_ENGINEER`) | Báo cáo sâu bệnh mới (kèm ảnh khi có `MATTERMOST_IMAGE_BASE_URL`), chẩn đoán chờ duyệt, khai phun thuốc chờ xác nhận, kết quả duyệt hoặc từ chối chẩn đoán, kết quả ghi nhận, xác nhận hoặc từ chối việc phun thuốc |
| Lab | `Kỹ thuật viên` (`MATTERMOST_WEBHOOK_URL_TECHNICIAN`) | Yêu cầu hỗ trợ mới, yêu cầu quá hạn xử lý |
| Lab | `Ban quản trị` (`MATTERMOST_WEBHOOK_URL_ADMIN`) | Yêu cầu quá hạn xử lý |
| Farm, Lab | `Ban quản trị` (`MATTERMOST_WEBHOOK_URL_ADMIN`) | Backend báo khi một địa chỉ bị từ chối nhiều lần; `dx-monitor` báo khi dịch vụ ngừng hoặc phục hồi, lỗi `5xx` và địa chỉ bị giới hạn tốc độ ([`dx-monitor/README.md`](../backend/dx-monitor/README.md#cảnh-báo)) |

- Gửi nền sau khi transaction commit, best-effort không retry: lỗi thì bỏ thông báo và ghi một dòng WARN không chứa URL.
- Webhook của từng channel do `mattermost-bootstrap` tạo khi dựng stack và ghi vào `deploy/data/mattermost/webhooks/<khóa>.url` (`engineer`, `admin`, `technician`, `default`).
- Backend đọc file này mỗi lần gửi, nên sau `reset.sh` không phải sửa `deploy/.env` hay khởi động lại Backend.
- Biến `MATTERMOST_WEBHOOK_URL_*` đặt trong `deploy/.env` thắng file; channel không có cả hai dùng `MATTERMOST_WEBHOOK_URL` rồi `default.url`.
- Gửi lại báo cáo với `Idempotency-Key` đã dùng không sinh thông báo thứ hai.
- Có `MATTERMOST_APP_BASE_URL` thì tiêu đề thông báo của báo cáo là liên kết `<địa chỉ>/?report=<id>`, mở thẳng báo cáo trên web (cần đăng nhập vai kỹ sư hoặc quản trị); rỗng thì không kèm liên kết.
- Tên, mã thửa, giống lúa, tên bệnh cắt tối đa 100 ký tự, vô hiệu mention, link, markdown; giờ theo `Asia/Ho_Chi_Minh`.

## Webhook

`mattermost-bootstrap` tạo mỗi channel một incoming webhook tên `DX-OS <khóa>`, khóa channel, và chạy lại không tạo trùng.
URL trỏ tới `mattermost:8065` (Backend gọi qua mạng compose) và chỉ nằm trong `deploy/data/mattermost/webhooks/`, thư mục bị xóa cùng `deploy/data`.
Dùng Mattermost bên ngoài stack thì đặt `MATTERMOST_WEBHOOK_URL_*` trong `deploy/.env`.

## Hạn chế

- Một chiều: duyệt và từ chối làm trong web; thông báo không được cập nhật sau khi gửi.
- Định tuyến theo loại sự kiện, không theo thửa hay người phụ trách.
- Số điện thoại, email và dãy số từ 9 chữ số trở lên trong tin được thay bằng `[đã ẩn]` trước khi gửi, vì lịch sử chat giữ tin mãi.
- Tin gửi lỗi tạm thời (mạng, HTTP 5xx, 429) được lưu ở bảng `chat_outbox` và gửi lại tối đa 8 lần; tin chưa kịp vào bảng khi backend tắt giữa lúc commit và lúc gửi thì mất.
- `Kỹ sư nông nghiệp` là channel public: mọi thành viên team đọc được tên người báo cáo và mã thửa; muốn giới hạn thì chuyển sang private.

Hướng mở rộng ở [`docs/BACKLOG.md`](../docs/BACKLOG.md).
