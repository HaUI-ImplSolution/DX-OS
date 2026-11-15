# Portal

Trang tĩnh liệt kê link tới ứng dụng chính, bảng điều khiển (`/console`), màn hình giám sát (`/monitor/`) và đặc tả OpenAPI (`/v3/api-docs`).
Trang có tiếng Việt và tiếng Anh, chọn bằng nút VI và EN ở góc trên; lựa chọn nằm ở khóa `dxos.locale` của `localStorage`, cùng khóa với ứng dụng chính vì cả hai phục vụ từ một địa chỉ.
Cuối trang có mục **Trạng thái dịch vụ**, tự làm mới mỗi 30 giây:

- Tên ứng dụng đang chạy (hiện ở tiêu đề), Backend và lưu trữ tệp lấy từ `GET /api/v1/system/status` (công khai, qua gateway).
- Ứng dụng chính và Keycloak gọi qua gateway; màn hình giám sát (`/monitor/actuator/health`) chỉ kiểm có phản hồi hay không.

## Chạy

Nằm trong `deploy/docker-compose.yml`, không publish cổng riêng: gateway phục vụ ở `/portal/`.

## Đăng nhập

Portal không có phiên, chỉ là danh sách link.
Ứng dụng chính và màn hình giám sát dùng SSO Keycloak ([`sso/README.md`](../sso/README.md)): chưa có phiên thì tự chuyển sang Keycloak, đã đăng nhập một app thì mở app kia không cần đăng nhập lại.

## Địa chỉ dịch vụ

Mọi link là đường dẫn tương đối của gateway.
Trên máy chủ công khai, nếu hạ tầng phía trước đặt lớp xác thực trước màn hình giám sát thì link mở trang đăng nhập đó trước.
Phép kiểm trạng thái màn hình giám sát vẫn nhận phản hồi sau khi chuyển hướng, nên chỉ cho biết lớp xác thực đó có trả lời.
