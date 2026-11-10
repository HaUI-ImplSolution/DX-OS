# API

Bảng dưới liệt kê các đường dẫn chính dưới `/api/v1`; đặc tả đầy đủ và thử gọi trực tiếp ở Swagger UI (`/swagger-ui.html`) và OpenAPI (`/v3/api-docs`) của backend đang chạy.
Mọi phản hồi bọc trong `ApiResponse`, và lỗi có `errorCode` ổn định.
Script `deploy/dev/check_docs.py` kiểm mọi đường dẫn nêu ở đây có trong OpenAPI.

| Đường dẫn | Việc |
|---|---|
| `POST /api/v1/models/plan`, `/apply` | Xem trước và áp dụng tệp mô hình (ADMIN) |
| `POST /api/v1/models/{model}/rollback/plan`, `/rollback` | Xem trước và quay về phiên bản trước (ADMIN) |
| `POST /api/v1/models/{model}/resume`, `/abandon` | Hoàn tất hoặc hủy bỏ lần áp dụng đã dừng giữa chừng (ADMIN) |
| `GET /api/v1/models`, `/models/{model}/runs` | Mô hình đã áp dụng và lịch sử từng bước (ADMIN) |
| `GET /api/v1/models/schema`, `/families`, `/templates`, `/{model}/versions` | Đặc tả tệp mô hình, họ mẫu kèm trạng thái và hành động, thư viện mẫu và các phiên bản đã áp dụng, cho bảng điều khiển (ADMIN) |
| `POST /api/v1/models/diagram` | Sơ đồ quy trình của một tệp mô hình: trạng thái, hành động (kèm vai riêng và trường bắt buộc), vai xử lý, hạn, hành động tự đóng và hành động `after`; tệp sai thì `valid: false` kèm dòng và cột (ADMIN) |
| `GET /api/v1/meta/models` | Metadata mà người gọi được xem; mô hình đang bị giữ hiện với `held` và không có thực thể |
| `/api/v1/data/{model}/{entity}` | Danh sách, tạo, xem, sửa, xóa; lọc `filter.<trường>[.<phép>]`, `sort`, `order`, `page`, `size`; sửa nhận `version` đã đọc, hồ sơ đã đổi thì 409 `ERR_CONCURRENT_MODIFICATION`; 403 `ERR_ACTION_NOT_ALLOWED` và 409 `ERR_INVALID_STATE` có `data.reason` |
| `GET /api/v1/data/{model}/{entity}/stats` | Đếm trên các dòng người gọi được xem, nhận cùng bộ lọc `filter.*` như danh sách: `total`; với thực thể có quy trình thêm `byStatus` (số hồ sơ mỗi trạng thái) và `overdue` (hồ sơ có hạn xử lý trước `computedAt`), `avgDecisionSeconds` (giây trung bình từ lúc tạo đến trạng thái cuối), `avgOpenAgeSeconds` và `oldestOpenAgeSeconds` (tuổi trung bình và lớn nhất của hồ sơ chưa vào trạng thái cuối, tính đến `computedAt`), ba số sau là `null` khi không có hồ sơ nào để tính; với thực thể khai hạn xử lý thêm `closedOnTime` và `closedLate`, số hồ sơ đã đóng (không tính hồ sơ rút lại) chưa từng quá hạn và đã từng quá hạn, đếm từ các dòng lịch sử `overdue`; một hồ sơ từng quá hạn rồi xong đúng hạn ở hạn mới vẫn tính là đã từng quá hạn; với thực thể có trường `unit` thêm `byUnit`; truy vấn bị cắt sau 10 giây |
| `POST /api/v1/data/{model}/{entity}/{id}/actions/{action}` | Hành động của quy trình; metadata liệt kê các vai xử lý (`handlers`), hành động kèm `roles`, `require` và `when` (`{field, op, value}`, `op` là `==`, `!=`, `<`, `<=`, `>` hoặc `>=`, `value` là số với trường `number`; `null` khi không có điều kiện), trạng thái đầu và người được làm; metadata của thực thể có `handlerActions`, tên các hành động mà vai của người gọi được thực hiện, để client chỉ hiện nút hợp lệ |
| `POST .../{id}/assign`, `GET .../{id}/handlers` | Giao việc và danh sách người giao được |
| `GET .../{id}/events` | Dòng lịch sử của bản ghi; `detail` của dòng `updated` là JSON `{"trường": ["cũ", "mới"]}` với giá trị dạng chuỗi hoặc `null`, cắt ở 200 ký tự |
| `.../{id}/files` | Tải lên, liệt kê, lấy link tải có hạn (`/{fileId}/link`) và gỡ tệp đính kèm |
| `GET /api/v1/inbox` | Hồ sơ đang chờ người gọi, trên mọi mô hình; `page`, `size` |
| `GET /api/v1/inbox/stream` | Kênh sự kiện `text/event-stream` cho người đã đăng nhập: sau khi một dòng lịch sử hồ sơ ghi xong ở bất kỳ mô hình nào, kênh gửi khung `event: changed` với dữ liệu `{}`, không kèm nội dung và giống nhau với mọi người, để client đọc lại hộp việc; dòng chú thích `: ping` mỗi 20 giây; server đóng kênh sau 10 phút nên client nối lại với token mới; 503 khi server đã giữ hết số kênh phục vụ được; kênh nằm trong bộ giới hạn 300 lượt mở mỗi phút mỗi người |
| `GET /api/v1/events` | Dòng lịch sử theo đúng dạng của webhook, từ sau một `eventId` (`after`, `size`, `model`) (ADMIN) |
| `GET /api/v1/events/admin` | Việc áp dụng, quay về, tiếp tục, hủy bỏ mô hình và thay đổi tài khoản theo đúng dạng thông điệp `admin.event`, từ sau một `eventId` (`after`, `size`, `model`); `eventId` đếm riêng, không cùng dãy với `/events`; dòng được ghi dù có đích webhook hay không (ADMIN) |
| `GET /api/v1/outbox`, `POST /api/v1/outbox/{id}/retry` | Thông điệp gửi đi theo trạng thái (lỗi, chờ gửi, đã gửi) và thử lại thông điệp chưa gửi (ADMIN) |
| `GET /api/v1/people` | Tìm người cho trường `user`, chỉ trả id và tên hiển thị: `q` tìm tối đa 20 tài khoản theo tên, tên đăng nhập hoặc email (ít hơn 2 ký tự thì trả danh sách rỗng), `ids` là tối đa 50 id cách nhau bởi dấu phẩy và trả những tài khoản có thật; người đã đăng nhập nào cũng gọi được |
| `GET /api/v1/users`, `/users/options`, `/users/{id}` | Tìm và xem người dùng, vai theo từng mô hình và đơn vị; vai và đơn vị có thể gán (ADMIN) |
| `PUT`, `DELETE /api/v1/users/{id}/roles/{model}/{role}`, `/units/{unit}` | Gán và gỡ vai của một mô hình, và đơn vị (ADMIN) |
| `POST /api/v1/users`, `PUT /{id}/enabled`, `POST /{id}/password` | Tạo người dùng với mật khẩu tạm, khóa và mở, đặt lại mật khẩu (ADMIN) |
| `GET /api/v1/audit-logs` | Nhật ký lần truy cập bị từ chối, lỗi, thay đổi về tài khoản và việc áp dụng mô hình; lọc theo `actor`, `action`, `targetType`, `targetId`, `outcome`, `from`, `to` (ADMIN) |
