# Kênh chat nội bộ (Mattermost)

Thư mục riêng ngang hàng `backend/`, `frontend/`, `ai/`, `bi/` — chỉ chứa ghi chú/quyết định, không có mã nguồn tự viết (Mattermost chạy bằng image chính thức, không build lại).
Dựng cho tiêu chí **Giai đoạn 2 [H]** (không gian Human) — một kênh chat nội bộ chạy thật trong docker-compose, không chỉ nằm trên giấy.
Service `mattermost` được khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml), lên tự động cùng lệnh `docker compose up -d --build` ở [`README.md`](../README.md) gốc — không cần thao tác tay nào thêm.

## Vì sao chọn Mattermost, và rà soát license

Mattermost được chọn theo đúng roadmap đã chốt (`README.md` gốc). License của Mattermost chia theo thư mục trong repo nguồn:

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
- Tài khoản Mattermost hiện tách biệt khỏi tài khoản backend (`FARMER`/`ENGINEER`/`ADMIN`) — user đầu tiên tự đăng ký qua UI sẽ tự động thành System Admin của Mattermost.
  Đăng nhập chung một lần (SSO thật qua Keycloak/Authentik) là mục còn thiếu riêng theo roadmap, chưa làm ở lượt này.

## Đăng nhập lần đầu

- URL: `http://localhost:${MATTERMOST_PORT:-8065}`
- Chưa seed sẵn tài khoản — vào URL trên, bấm "Create account" để tạo user đầu tiên (tự động thành System Admin), sau đó tạo thêm team/channel theo nhu cầu (ví dụ channel "Kỹ sư nông nghiệp", "Ban quản trị").

## Xem/quản trị dữ liệu

- **System Console** (UI, dễ đọc nhất): `http://localhost:${MATTERMOST_PORT:-8065}/admin_console` — đăng nhập bằng tài khoản `system_admin` (user đầu tiên tạo lúc "Create account"). Xem/quản lý user, team, channel, plugin, cấu hình... tại đây, không cần đụng SQL.
- **DBeaver / SQL trực tiếp**: cùng Postgres server với CSDL nghiệp vụ (`dxfarm`) — chỉ cần đổi database sang `mattermost` trong connection, hoặc tạo connection mới:
  - Host/Port: giống connection tới `dxfarm` (mặc định `localhost:5432`)
  - Database: `mattermost`
  - User: `mattermost_app`
  - Password: giá trị `MATTERMOST_DB_PASSWORD` trong `deploy/.env` (mặc định `mattermost_demo_only`)

  Bảng đáng chú ý: `users` (tài khoản — có vài bot hệ thống như `feedbackbot`/`playbooks`/`calls`/`github`, không phải người dùng thật), `teams`/`channels`, `channelmembers`/`teammembers`, `posts` (nội dung tin nhắn), `audits` (log hành động).

## Còn thiếu

- SSO thật (Keycloak/Authentik) để đăng nhập chung 1 lần giữa app chính, BI dashboard và Mattermost — mục riêng theo roadmap `README.md`, chưa làm.
- Chưa tích hợp thông báo tự động (ví dụ: có `DiseaseReport` mới thì post vào channel Mattermost) — nằm ngoài phạm vi "chạy thật trong docker-compose" của lượt này.
