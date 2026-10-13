# Portal (`portal`)

Trang tĩnh liệt kê link tới các ứng dụng trong hệ thống (Ứng dụng chính, BI Dashboard, Kênh chat nội bộ) cùng các trang quản trị (Keycloak, Superset, Mattermost, MinIO Console) và tài liệu API Swagger của Backend, để người dùng không phải tự nhớ/gõ URL từng app.

Cuối trang có mục **Trạng thái dịch vụ**, tự làm mới mỗi 30 giây:
- Tên ứng dụng đang chạy (`app.name`), Backend, dịch vụ AI và lưu trữ ảnh lấy từ `GET /api/v1/system/status` (công khai, qua gateway); tên app hiện ở tiêu đề trang.
- Ứng dụng chính và Keycloak được gọi trực tiếp qua gateway.
- BI Dashboard (9081) và Kênh chat (8065) chỉ kiểm tra có phản hồi hay không.

## Chạy

Nằm trong `deploy/docker-compose.yml`, lên cùng lệnh `docker compose up -d --build` sẵn có của repo.
Không publish cổng riêng: gateway APISIX phục vụ portal ở `http://localhost:9080/portal/` (`APISIX_HOST_PORT` trong `deploy/.env`).

## Đăng nhập

- Portal tự nó không có phiên đăng nhập — chỉ là danh sách link tĩnh.
- Ứng dụng chính và BI Dashboard (Superset) dùng SSO thật qua Keycloak (xem [`sso/README.md`](../sso/README.md)): bấm link khi chưa có phiên sẽ tự chuyển hướng sang Keycloak đăng nhập, xong quay lại đúng app vừa bấm; đã đăng nhập một app rồi thì bấm app còn lại không cần đăng nhập lại (dùng chung phiên Keycloak).
  Link BI Dashboard trỏ thẳng `/login/keycloak` để vào thẳng luồng SSO, không dừng ở màn hình "Sign In with keycloak" của Superset.
- Kênh chat nội bộ (Mattermost) là nền tảng tách biệt có chủ đích, đăng nhập riêng, không dùng SSO chung.

## Giới hạn hiện tại

- Link ứng dụng chính, Keycloak và Swagger là đường dẫn tương đối của gateway; link Superset (cổng `APISIX_SUPERSET_HOST_PORT`), MinIO Console (`APISIX_MINIO_HOST_PORT`) và Mattermost (`MATTERMOST_HOST_PORT`) tự lấy tên máy của trang đang mở nhưng giữ cổng viết sẵn trong `index.html` (9081, 9082 và 8065), nên đổi cổng trong `.env` thì sửa `data-port` tương ứng.
