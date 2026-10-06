# Kênh chat nội bộ (Mattermost)

Thư mục riêng ngang hàng `backend/`, `frontend/`, `ai/`, `bi/` — chỉ chứa ghi chú/quyết định, không có mã nguồn tự viết (Mattermost chạy bằng image chính thức, không build lại).
Dựng cho tiêu chí **Giai đoạn 2 [H]** (không gian Human) — một kênh chat nội bộ chạy thật trong docker-compose, không chỉ nằm trên giấy.
Service `mattermost` được khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml), lên tự động cùng lệnh `docker compose up -d --build` ở [`README.md`](../README.md) gốc; thông báo tự động từ Backend chỉ cần thêm một bước cấp webhook (xem mục bên dưới).

## Vì sao chọn Mattermost, và rà soát license

Mattermost được chọn theo đúng roadmap đã chốt (`README.md` gốc).
License của Mattermost chia theo thư mục trong repo nguồn:

- Phần server core (toàn bộ trừ `server/enterprise/`) — **AGPLv3**, OSI-approved.
- `server/enterprise/` (tính năng trả phí: SSO nâng cao, compliance, LDAP...) — **Mattermost Source Available License**, **không OSI-approved** (chỉ free trong dev/test, cần license trả phí ở production).
- Riêng bản Linux binary mà chính Mattermost, Inc. tự compile và phân phối dưới tên **Team Edition** được họ cấp phép lại dưới **MIT** — đây là một grant riêng của Mattermost, Inc. cho đúng binary đó, không phải license áp dụng cho toàn bộ mã nguồn trong repo.

Quyết định: dùng thẳng image chính thức `mattermost/mattermost-team-edition`, không build lại từ source, không bật/không đụng tới thư mục `server/enterprise/`.
Ghi vào [`LICENSES.md`](../LICENSES.md) đúng caveat này để minh bạch khi review PoF.

## Cách hoạt động

- `mattermost` dùng CSDL Postgres **riêng** (`mattermost`), không đọc/viết chung schema nghiệp vụ.
  Backend tự tạo role `mattermost_app` + database `mattermost` lúc khởi động (`MattermostDatabaseSeeder`, JDBC thuần vì `CREATE DATABASE` không chạy được trong khối PL/pgSQL `DO $$ ... $$` hay trong transaction).
- Dữ liệu (config, tin nhắn, file đính kèm, log, plugin) persist qua bind mount `deploy/data/mattermost/` — không mất khi `docker compose down`.
- Service phụ `mattermost-init` (image `alpine`, chạy 1 lần rồi tắt) tự `chown` các thư mục bind mount trên về `uid 2000` — Mattermost chạy bằng user không phải root nhưng thư mục bind mount tạo lần đầu thuộc `root`, nếu không chown trước thì container chính báo lỗi `permission denied` lúc khởi động.
- Service `mattermost-bootstrap` (cùng image Mattermost, chạy 1 lần rồi tắt) chạy [`bootstrap.sh`](./bootstrap.sh) qua REST API: tạo tài khoản System Admin, team `dx-farm` và các channel mặc định; chạy lại nhiều lần không tạo trùng.
  Channel mặc định: `Thông báo chung` (public), `Kỹ sư nông nghiệp` (public), `Ban quản trị` (private).
- Tài khoản Mattermost tách biệt khỏi tài khoản backend (`FARMER`/`ENGINEER`/`ADMIN`).
  Đây là nền tảng tách biệt hoàn toàn có chủ đích: App chính và BI Dashboard đăng nhập qua Keycloak (xem [`sso/README.md`](../sso/README.md)), còn Mattermost dùng tài khoản/role riêng của chính nó, không nối SSO chung — Team Edition (bản free) không hỗ trợ OpenID Connect với IdP tuỳ ý (tính năng đó thuộc Mattermost Enterprise, trả phí).

## Đăng nhập lần đầu

- URL: `http://localhost:${MATTERMOST_HOST_PORT}`
- Tài khoản System Admin có sẵn: `MATTERMOST_ADMIN_USERNAME` / `MATTERMOST_ADMIN_PASSWORD` trong `deploy/.env`.
- Team `DX-Farm HTX` và các channel mặc định đã được tạo sẵn.

## Xem/quản trị dữ liệu

- **System Console** (UI, dễ đọc nhất): `http://localhost:${MATTERMOST_HOST_PORT}/admin_console` — đăng nhập bằng tài khoản System Admin ở trên.
  Xem/quản lý user, team, channel, plugin, cấu hình... tại đây, không cần đụng SQL.
- **DBeaver / SQL trực tiếp**: cùng Postgres server với CSDL nghiệp vụ (`dxfarm`) — chỉ cần đổi database sang `mattermost` trong connection, hoặc tạo connection mới:
  - Host/Port: giống connection tới `dxfarm` (`localhost`, cổng `POSTGRES_HOST_PORT`)
  - Database: `mattermost`
  - User: `mattermost_app`
  - Password: giá trị `MATTERMOST_DB_PASSWORD` trong `deploy/.env`

  Bảng đáng chú ý: `users` (tài khoản — có vài bot hệ thống như `feedbackbot`/`playbooks`/`calls`/`github`, không phải người dùng thật), `teams`/`channels`, `channelmembers`/`teammembers`, `posts` (nội dung tin nhắn), `audits` (log hành động).

## Thông báo tự động từ Backend

Backend post vào channel `Kỹ sư nông nghiệp` qua Incoming Webhook khi có:

- **Báo cáo sâu bệnh mới**: mã báo cáo, mã thửa, giống lúa, người báo cáo, thời điểm báo cáo và ảnh báo cáo.
- **Chẩn đoán chờ duyệt** (nhập tay hoặc qua AI): mã báo cáo, mã thửa, bệnh, độ tin cậy, có hay không có phác đồ.
  Chẩn đoán không có phác đồ ghi "Không có phác đồ — cần kỹ sư xem xét".

Cách hoạt động:

- Thông báo đi sau khi transaction lưu dữ liệu commit, chạy nền trên executor riêng; request tạo báo cáo/chẩn đoán không chờ Mattermost.
- Best-effort, không retry: Mattermost tắt, webhook sai hoặc hàng đợi đầy thì thông báo bị bỏ, Backend chỉ ghi một dòng WARN không chứa URL.
- Gửi lại báo cáo với `Idempotency-Key` đã dùng không sinh thông báo thứ hai.
- `MATTERMOST_WEBHOOK_URL` rỗng thì tính năng tắt; Backend ghi một dòng lúc khởi động cho biết bật hay tắt.
- Thông báo báo cáo mới kèm ảnh khi `MATTERMOST_IMAGE_BASE_URL` trỏ tới địa chỉ backend mà trình duyệt người xem truy cập được (địa chỉ gateway, ví dụ `http://localhost:9080`); để trống thì không kèm ảnh.
- Tên người báo cáo, mã thửa, giống lúa và tên bệnh được cắt tối đa 100 ký tự, bỏ xuống dòng và vô hiệu mention, link, markdown.
  Thời điểm hiển thị theo múi giờ `Asia/Ho_Chi_Minh`.
- Thông báo không kèm deep link; kỹ sư tìm báo cáo trong web app theo mã thửa và mã báo cáo. Hướng bổ sung deep link và danh sách chờ xử lý nằm ở mục 9 của tài liệu thiết kế.

### Cấp webhook (một lần cho mỗi môi trường)

1. Đăng nhập Mattermost bằng tài khoản System Admin.
2. Vào Integrations → Incoming Webhooks → Add Incoming Webhook, chọn channel `Kỹ sư nông nghiệp` trong team `DX-Farm HTX`, bật **Lock to this channel** để webhook chỉ đăng được vào channel đó.
3. Sao chép URL, đổi host `localhost:<cổng>` thành `mattermost:8065` (Backend gọi qua mạng nội bộ của compose), ví dụ `http://mattermost:8065/hooks/<key>`.
4. Điền vào `MATTERMOST_WEBHOOK_URL` trong `deploy/.env`, rồi chạy lại service `backend`: `docker compose up -d backend`.

URL chứa khóa truy cập nên chỉ nằm trong `deploy/.env`, không đưa vào Git.
Xóa `deploy/data/mattermost/` hoặc thu hồi webhook làm khóa cũ mất hiệu lực; khi đó cấp lại và cập nhật `deploy/.env`.

### Hạn chế

- Luồng một chiều: duyệt, từ chối và ghi nhận xử lý vẫn làm trong web app, Mattermost không nhận kết quả và không cập nhật thông báo cũ.
- Một channel cho mọi sự kiện, không định tuyến theo thửa hoặc kỹ sư phụ trách.
- `Kỹ sư nông nghiệp` là channel public: mọi tài khoản trong team đọc được tên người báo cáo và mã thửa.
  Muốn giới hạn thì chuyển channel sang private và thêm thành viên thủ công.
- Không có outbox hay lịch sử gửi: thông báo bị lỡ không được gửi lại.
- Cấp webhook là thao tác tay.

Thiết kế chi tiết: [`development/docs/pending_docs/MATTERMOST_WEBHOOK_NOTIFICATION_PLAN.md`](../development/docs/pending_docs/MATTERMOST_WEBHOOK_NOTIFICATION_PLAN.md).
