# Kênh chat nội bộ (Mattermost)

Nhận thông báo một chiều từ Backend qua Incoming Webhook.
Chạy bằng image chính thức `mattermost/mattermost-team-edition`, không build lại và không dùng `server/enterprise/` (AGPL-3.0, giấy phép các thành phần ở [`LICENSES.md`](../LICENSES.md)).
Service khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).

## Cách hoạt động

- Database Postgres riêng `mattermost`, Backend tạo role và database lúc khởi động (`MattermostDatabaseSeeder`); dữ liệu ở `deploy/data/mattermost/`.
- [`bootstrap.sh`](./bootstrap.sh) chạy một lần qua REST API (idempotent): tạo System Admin, team `DX-Farm HTX` và channel `Thông báo chung`, `Kỹ sư nông nghiệp` (Farm), `Kỹ thuật viên` (Lab), `Ban quản trị` (private).
- Đăng nhập tách khỏi Keycloak có chủ đích vì Team Edition không hỗ trợ OIDC với IdP tùy ý.
- Truy cập `http://localhost:${MATTERMOST_HOST_PORT}` (mặc định `8065`) bằng `MATTERMOST_ADMIN_USERNAME` / `MATTERMOST_ADMIN_PASSWORD` trong `deploy/.env`.

## Thông báo từ Backend

| App | Channel (biến webhook) | Sự kiện |
|---|---|---|
| Farm | `Ban quản trị` (`MATTERMOST_WEBHOOK_URL_ADMIN`) | Yêu cầu thửa mới, yêu cầu vụ mùa mới (kể cả gửi lại) |
| Farm | `Kỹ sư nông nghiệp` (`MATTERMOST_WEBHOOK_URL_ENGINEER`) | Báo cáo sâu bệnh mới (kèm ảnh khi có `MATTERMOST_IMAGE_BASE_URL`), chẩn đoán chờ duyệt |
| Lab | `Kỹ thuật viên` (`MATTERMOST_WEBHOOK_URL_TECHNICIAN`) | Yêu cầu hỗ trợ mới, yêu cầu quá hạn xử lý |
| Lab | `Ban quản trị` (`MATTERMOST_WEBHOOK_URL_ADMIN`) | Yêu cầu quá hạn xử lý |

- Gửi nền sau khi transaction commit, best-effort không retry: lỗi thì bỏ thông báo và ghi một dòng WARN không chứa URL.
- Kênh không có webhook riêng dùng `MATTERMOST_WEBHOOK_URL`; cả hai rỗng thì kênh đó tắt.
- Gửi lại báo cáo với `Idempotency-Key` đã dùng không sinh thông báo thứ hai.
- Tên, mã thửa, giống lúa, tên bệnh cắt tối đa 100 ký tự, vô hiệu mention, link, markdown; giờ theo `Asia/Ho_Chi_Minh`.

## Cấp webhook (một lần cho mỗi môi trường)

1. System Admin vào Integrations → Incoming Webhooks → Add, chọn channel, bật **Lock to this channel**; một webhook cho mỗi channel.
2. Đổi host của URL thành `mattermost:8065`, ví dụ `http://mattermost:8065/hooks/<key>` (Backend gọi qua mạng compose).
3. Điền `MATTERMOST_WEBHOOK_URL_*` trong `deploy/.env` (bỏ dấu `#`), rồi `docker compose up -d backend`.

URL chứa khóa truy cập nên chỉ nằm trong `deploy/.env`; xóa `deploy/data/mattermost/` làm webhook cũ mất hiệu lực, khi đó cấp lại.

## Hạn chế

- Một chiều: duyệt và từ chối vẫn làm trong web; thông báo không kèm deep link và không được cập nhật.
- Định tuyến theo loại sự kiện, không theo thửa hay người phụ trách; không có outbox nên thông báo lỡ không gửi lại.
- `Kỹ sư nông nghiệp` là channel public: mọi thành viên team đọc được tên người báo cáo và mã thửa; muốn giới hạn thì chuyển sang private.

Hướng mở rộng ở [`docs/BACKLOG.md`](../docs/BACKLOG.md).
