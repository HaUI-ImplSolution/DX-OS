# Portal (`portal`)

Trang tĩnh liệt kê link tới các ứng dụng trong hệ thống (Ứng dụng chính, BI Dashboard, Kênh chat
nội bộ), để người dùng không phải tự nhớ/gõ URL từng app.

## Chạy

Nằm trong `deploy/docker-compose.yml`, lên cùng lệnh `docker compose up -d --build` sẵn có của repo.
Mặc định expose cổng `8090` (`PORTAL_PORT` trong `deploy/.env`).

## Giới hạn hiện tại

- Chỉ là danh sách link tĩnh, không tự động đăng nhập chung.
  Ứng dụng chính dùng SSO thật (Keycloak, xem [`sso/README.md`](../sso/README.md)); BI Dashboard/Kênh chat nội bộ vẫn đăng nhập riêng.
- Link trỏ theo cổng mặc định trong `deploy/.env.example` (`FRONTEND_PORT`, `SUPERSET_PORT`,
  `MATTERMOST_PORT`). Nếu đổi cổng trong `.env`, sửa lại URL tương ứng trong `index.html`.
