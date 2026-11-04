# Trade-off và rủi ro: Phase 13

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Cách ly vai | A: giữ tên vai toàn cục kèm bảng ghi vai thuộc mô hình nào; B: tên vai realm kèm tên mô hình; C: client role, một client cho mỗi mô hình | C | Đúng khái niệm Keycloak, quản lý vai theo ứng dụng ngay trong Admin Console, có thể giao quyền quản vai của một ứng dụng | Mỗi mô hình thêm một client; phải đọc `resource_access` | Cao sau khi triển khai, vì phải chuyển vai và thành viên; thấp lúc này vì bản 2.0.0 chưa triển khai |
| TO2 | Tab Người dùng | Chỉ xem và liên kết sang Keycloak, hoặc đầy đủ | Đầy đủ, hai bước | Quản trị trọn vẹn trong bảng điều khiển | Thêm API và giao diện | Thấp |

Phương án B đã thử và chạy được; giữ làm dự phòng nếu cấu hình scope của realm đổi.
Kết luận: theo thỏa thuận với người dùng, thử nghiệm quyết định giữa C và B; thử nghiệm sạch nên chọn C (xem `01_PLAN.md`, WP13-00).

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Vai không vào token khi realm hoặc client web được cấu hình khác (ví dụ tắt `fullScopeAllowed`) | Thấp | Cao: người dùng mất quyền | Chuỗi e2e kiểm token có `resource_access` đúng (đã đạt); ghi điều kiện này vào `deploy/OPERATIONS.md` khi đóng phase | e2e đỏ ở bước gọi API dữ liệu | Quay lại phương án B (đã thử chạy) |
| R2 | Áp dụng dừng giữa bước tạo client hoặc vai | Vừa | Thấp | Bước tạo chạy lại được (tạo lại client trả 409 và được bỏ qua); Tiếp tục và Hủy bỏ đã có | Lần chạy `failed` ở `ENSURE_CLIENT` | Tiếp tục áp dụng |
| R3 | Người dùng đổi vai nhưng token cũ còn hiệu lực | Cao | Thấp | Đã là giới hạn biết trước: vai có hiệu lực sau khi lấy token mới; ghi trên tab | Người dùng báo chưa thấy quyền mới | Đăng nhập lại |
| R4 | Mật khẩu tạm lộ ở log, audit hay phản hồi | Thấp | Cao | Không ghi mật khẩu ở log và audit; chỉ trả về một lần cho ADMIN khi tạo | Kiểm bằng grep log sau T13-10 | Đặt lại mật khẩu |
| R5 | Danh sách người dùng chậm khi có nhiều người | Thấp | Thấp | Tìm kiếm và phân trang theo Keycloak | Tab chậm | Giới hạn kích thước trang |
| R6 | Tài khoản dịch vụ của backend có quyền quản lý client, rộng hơn quyền quản lý người dùng | Thấp | Vừa: lộ bí mật của tài khoản này thì đổi được cấu hình client | Bí mật chỉ nằm ở `.env` và môi trường của backend; chỉ cấp ba quyền cần thiết | Bất thường ở log Keycloak | Đổi bí mật của `dxos-backend-admin` |
| R7 | Realm đã import thiếu quyền client | Vừa | Vừa: không áp dụng được mô hình mới | Ghi cách cấp quyền ở `deploy/OPERATIONS.md` và `sso/README.md` khi đóng phase | Áp dụng báo Keycloak từ chối ở bước `ENSURE_CLIENT` | Cấp quyền tay ở Admin Console hoặc dựng lại realm |

## 3. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: vai không vào token ở stack dựng từ trạng thái rỗng, hoặc e2e không đạt được sau khi đổi cách gán vai.
- Rollback: hoàn tác commit; client và vai đã tạo ở Keycloak còn lại, người dùng gán vai realm như trước.

## 4. Tác động ngoài phase

- Lên các phase sau: tab Người dùng là nền cho việc quản trị người dùng của ứng dụng minh họa.
- Lên demo và thời hạn nộp bài: không làm đổi việc bắt buộc ở phần 2 của `BACKLOG.md`.
