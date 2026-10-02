# Kênh chat nội bộ (Mattermost)

Thư mục riêng ngang hàng `backend/`, `frontend/`, `ai/`, `bi/` — chỉ chứa ghi chú/quyết định, không có mã nguồn tự viết (Mattermost chạy bằng image chính thức, không build lại).
Dựng cho tiêu chí **Giai đoạn 2 [H]** (không gian Human) — một kênh chat nội bộ chạy thật trong docker-compose, không chỉ nằm trên giấy.
Service `mattermost` được khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml), lên tự động cùng lệnh `docker compose up -d --build` ở [`README.md`](../README.md) gốc — không cần thao tác tay nào thêm.

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

## Còn thiếu

- Thông báo tự động từ Backend vào channel `Kỹ sư nông nghiệp` (báo cáo sâu bệnh mới, chẩn đoán chờ duyệt) qua Incoming Webhook: kế hoạch ở [`development/docs/MATTERMOST_WEBHOOK_NOTIFICATION_PLAN.md`](../development/docs/MATTERMOST_WEBHOOK_NOTIFICATION_PLAN.md), chưa triển khai.
