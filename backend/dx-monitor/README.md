# dx-monitor: màn hình giám sát và truy vết

Dịch vụ Spring Boot riêng của DX-OS, chạy trong container `monitor`.
Không phụ thuộc `dx-core-api` hay `dx-core` và không biết app nào đang chạy, nên dùng chung cho Farm và Lab.
Chạy riêng nên vẫn hoạt động khi backend dừng.

Mở từ thẻ "Giám sát và truy vết" của portal hoặc tại `/monitor/`, đăng nhập SSO Keycloak; chỉ vai `ADMIN` vào được.
Màn hình chỉ đọc, nền sáng, tự làm mới mỗi 30 giây.
Nội dung viết cho người quản lý không chuyên IT: biểu đồ là chính và không hiện mã trạng thái.

## Chạy và build

- Cổng nội bộ `8090`, đường dẫn gốc `/monitor`; gateway chuyển `/monitor/*` tới đây.
- Image build từ [`../Dockerfile`](../Dockerfile) với đích `monitor`: `docker build --target monitor backend`.
- Service `monitor-init` và `monitor` khai báo trong [`../../deploy/docker-compose.yml`](../../deploy/docker-compose.yml).
- Cấu hình ở [`src/main/resources/application.yaml`](./src/main/resources/application.yaml); biến môi trường ở mục Cấu hình bên dưới.
- Log truy cập của gateway, sự kiện đăng nhập của Keycloak và giới hạn tốc độ nằm ở [`../../deploy/OPERATIONS.md`](../../deploy/OPERATIONS.md).

## Các khối trên màn hình

| Khối | Trả lời câu hỏi | Nguồn |
|---|---|---|
| Dải trạng thái | Hệ thống đang bình thường, cần chú ý hay có dịch vụ ngừng | Các nguồn dưới đây |
| Năm con số | Số dịch vụ đang chạy, truy cập bị chặn, lỗi hệ thống, truy cập quá nhiều, đăng nhập sai | `audit_log`, `monitor.gateway_event`, sự kiện đăng nhập của Keycloak, dò dịch vụ |
| Sự việc bất thường theo thời gian, tỷ lệ các loại | Đỉnh bất thường bắt đầu lúc nào, loại nào nhiều nhất | Như trên |
| Đăng nhập sai theo thời gian | Có ai đang đoán mật khẩu không | Sự kiện đăng nhập của Keycloak |
| Nguồn truy cập gây nhiều sự việc nhất | Địa chỉ nào đáng ngờ; bấm để tra cứu chi tiết | Như trên |
| Tình trạng các dịch vụ | Mỗi dịch vụ đang chạy hay đã ngừng, chạy được bao nhiêu phần trăm thời gian, lúc nào ngừng | `monitor.service_health_event` |
| Chức năng gặp lỗi nhiều nhất | Người dùng gặp lỗi ở chức năng nào | `monitor.gateway_event` |
| Thao tác quản trị gần đây | Ai đã đổi gì, thao tác nào không thành công | `audit_log` |
| Tra cứu chi tiết (thu gọn, cho kỹ thuật viên) | Một yêu cầu hoặc một địa chỉ đã đi qua những đâu, kèm mã trạng thái | Gateway, backend, Keycloak |

## Cách đọc

- Khoảng xem chọn ở góc trên: 1 giờ, 24 giờ (mặc định) hoặc 7 ngày qua.
- Biểu đồ theo thời gian chia cột theo khoảng xem: 1 phút (đến 1 giờ), 5 phút (đến 6 giờ), 1 giờ (đến 48 giờ), 6 giờ (7 ngày).
- Rê chuột vào cột để xem sự việc trong cột bắt đầu và kết thúc lúc nào (ví dụ `14:10 - 14:50 (40 phút)`) cùng số lượng từng loại.
- Vạch xám dọc đánh dấu các mốc giờ trên trục thời gian.
- Kéo chuột ngang trên một biểu đồ để phóng to; khoảng chọn bám theo mép cột, nên kéo từ vạch 10:00 thì phóng từ đúng 10:00.
  Cả hai biểu đồ theo thời gian cùng phóng và chia cột mịn hơn.
  Bấm "Xem lại toàn bộ" hoặc đổi khoảng xem để trở về.
  Cột mới mọc lên khi biểu đồ đổi; hiệu ứng tắt khi hệ điều hành bật giảm chuyển động.
- Mỗi ô dịch vụ có thanh theo thời gian: xanh là Tốt, đỏ là Ngừng, vàng là Trống dữ liệu; rê chuột vào một khối để xem khoảng thời gian chính xác.
- Ô dịch vụ liệt kê các lần ngừng (đỏ) và khoảng trống dữ liệu từ 5 phút (vàng), ví dụ `Ngừng 14:49 - 14:50 (1 phút)`; lần đang ngừng ghi `14:49 - nay`.
  Dịch vụ không có hai loại này ghi "Hoạt động tốt".
- Tra cứu chi tiết: để trống thì xem 200 bản ghi mới nhất, nhập mã yêu cầu hoặc IP để lọc; mỗi trang 20 dòng, số trang ở góc dưới bên phải.

## Truy vết một sự cố

1. Người dùng báo lỗi: lấy `X-Request-Id` trong phản hồi (hoặc `request_id` ở log gateway), dán vào ô mã yêu cầu của mục Tra cứu chi tiết.
2. Kết quả có dòng của gateway (mã trả về, thời điểm) và dòng của backend (`ACCESS_DENIED`, `SERVER_ERROR`...) cùng `request_id`.
3. Nghi ngờ một địa chỉ: bấm tên nguồn trong biểu đồ để xem mọi sự kiện của địa chỉ đó, kể cả đăng nhập sai.
4. Cần chi tiết hơn: tìm cùng `request_id` trong log backend (`docker compose logs backend`).

## Dữ liệu

- Gateway gửi mọi request cho `monitor` theo đợt; `monitor` chỉ giữ `429`, `401`, `404`, `5xx` và request chậm từ 10 giây (trừ chẩn đoán AI và hỏi đáp).
  Bỏ qua `401` của chính `/monitor/` khi phiên hết hạn.
- Mỗi địa chỉ giữ tối đa 100 bản ghi mỗi phút; phần dư cộng vào cột `suppressed` của bản ghi gần nhất cùng địa chỉ và loại, nên số tổng vẫn đủ.
- Bảng `gateway_event` và `service_health_event` nằm ở schema `monitor` của database chính, xóa sau `monitor.retention-days` (30 ngày) lúc 03:30 mỗi đêm.
- `audit_log` không bị xóa.
- Sự kiện đăng nhập đọc từ database `keycloak` qua role `keycloak_ro`, role này chỉ thấy view `vw_auth_events`; thời hạn giữ theo cấu hình của realm (90 ngày).
- Service `monitor-init` tạo role, schema và view mỗi lần `docker compose up`.
  Thiếu bảng của Keycloak thì chỉ ô đăng nhập sai báo thiếu dữ liệu.
- Khi `monitor` dừng, gateway giữ đợt chưa gửi trong bộ nhớ và thử lại vài lần rồi bỏ.
  Khoảng đó không có sự kiện gateway; `audit_log` và log Docker vẫn còn.

## Cảnh báo

`monitor` gửi vào kênh Mattermost "Ban quản trị", cùng webhook với cảnh báo của backend:

| Điều kiện | Ngưỡng mặc định |
|---|---|
| Dịch vụ ngừng, rồi phục hồi | Sau 2 lần dò thất bại liên tiếp |
| Lỗi `5xx` tại cổng | 5 lần trong 5 phút |
| Một địa chỉ bị giới hạn tốc độ | 10 lần trong 10 phút |

- Một điều kiện được báo tối đa một tin mỗi 60 phút; `monitor` khởi động lại thì quên các điều kiện đã báo.
- Ngưỡng ở `monitor.alert.*` và đích dò ở `monitor.probe.targets` trong `backend/dx-monitor/src/main/resources/application.yaml`.
- Cảnh báo `ACCESS_DENIED` theo địa chỉ do backend gửi.
- `monitor` ngừng thì không có cảnh báo nào của nó; kiểm bằng `docker compose ps`.

## Cấu hình

| Biến (`deploy/.env`) | Ý nghĩa |
|---|---|
| `MONITOR_DB_PASSWORD` | Mật khẩu role `monitor_app` |
| `KEYCLOAK_READONLY_DB_PASSWORD` | Mật khẩu role `keycloak_ro` |
| `MONITOR_OIDC_CLIENT_SECRET` | Secret của client `dxos-monitor` trong Keycloak |
| `MONITOR_INGEST_KEY` | Khóa gateway dùng khi gửi sự kiện; chữ và số, không có `\|` hay `&` |

- Gateway điền khóa vào cấu hình khi `apisix-config` chạy; đổi khóa thì chạy `./switch-app.sh <app>`.
- Keycloak đang chạy chưa có client `dxos-monitor`: thêm client theo `sso/realm-dxos.json` trong Admin Console, hoặc dựng lại Keycloak từ đầu.
- Đường nhận `/monitor/ingest/` chỉ dùng trong mạng Docker; gateway công khai trả `404` cho nó.
