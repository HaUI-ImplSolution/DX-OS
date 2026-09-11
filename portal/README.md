# Portal (`portal`)

Trang tĩnh liệt kê link tới các ứng dụng trong hệ thống (Ứng dụng chính, BI Dashboard, Kênh chat
nội bộ), để người dùng không phải tự nhớ/gõ URL từng app.

## Chạy

Nằm trong `deploy/docker-compose.yml`, lên cùng lệnh `docker compose up -d --build` sẵn có của repo.
Mặc định expose cổng `8090` (`PORTAL_PORT` trong `deploy/.env`).

## Đăng nhập

- Portal tự nó không có phiên đăng nhập — chỉ là danh sách link tĩnh.
- Ứng dụng chính và BI Dashboard (Superset) dùng SSO thật qua Keycloak (xem [`sso/README.md`](../sso/README.md)): bấm link khi chưa có phiên sẽ tự chuyển hướng sang Keycloak đăng nhập, xong quay lại đúng app vừa bấm; đã đăng nhập một app rồi thì bấm app còn lại không cần đăng nhập lại (dùng chung phiên Keycloak).
  Link BI Dashboard trỏ thẳng `/login/keycloak` để vào thẳng luồng SSO, không dừng ở màn hình "Sign In with keycloak" của Superset.
- Kênh chat nội bộ (Mattermost) vẫn đăng nhập riêng, chưa nối SSO.

## Giới hạn hiện tại

- Link trỏ theo cổng mặc định trong `deploy/.env.example` (`FRONTEND_PORT`, `SUPERSET_PORT`,
  `MATTERMOST_PORT`). Nếu đổi cổng trong `.env`, sửa lại URL tương ứng trong `index.html`.
