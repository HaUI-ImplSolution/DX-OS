# Portal

Trang tĩnh liệt kê link tới ứng dụng chính, BI Dashboard, kênh chat, các trang quản trị (Keycloak, Superset, Mattermost, MinIO Console) và Swagger.
Cuối trang có mục **Trạng thái dịch vụ**, tự làm mới mỗi 30 giây:

- Tên ứng dụng đang chạy (hiện ở tiêu đề), Backend, AI và lưu trữ ảnh lấy từ `GET /api/v1/system/status` (công khai, qua gateway).
- Ứng dụng chính và Keycloak gọi qua gateway; BI (`9081`) và chat (`8065`) chỉ kiểm có phản hồi hay không.

## Chạy

Nằm trong `deploy/docker-compose.yml`, không publish cổng riêng: gateway phục vụ ở `http://localhost:9080/portal/`.

## Đăng nhập

Portal không có phiên, chỉ là danh sách link.
Ứng dụng chính và Superset dùng SSO Keycloak ([`sso/README.md`](../sso/README.md)): chưa có phiên thì tự chuyển sang Keycloak, đã đăng nhập một app thì mở app kia không cần đăng nhập lại.
Link BI trỏ thẳng `/login/keycloak`; Mattermost đăng nhập riêng.

## Giới hạn

Link ứng dụng chính, Keycloak và Swagger là đường dẫn tương đối của gateway; link Superset, MinIO Console và Mattermost lấy tên máy của trang đang mở nhưng giữ cổng viết sẵn trong `index.html` (`data-port`: 9081, 9082, 8065), nên đổi cổng trong `.env` thì sửa `data-port` tương ứng.
