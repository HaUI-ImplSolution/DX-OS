# Việc tồn đọng của lõi dynamic

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của lõi dynamic.
Hướng đi nằm ở [`README.md`](./README.md); kế hoạch từng phase nằm ở `docs/phase-N-*`.
Làm xong một mục thì xóa dòng đó, không ghi lại diễn biến.

## 1. Thứ tự đến hạn nộp

Hạn gửi bài là 17:00 ngày 06/12/2026.
Mốc dưới đây là dự kiến.

| Phase | Nội dung | Dự kiến xong |
|---|---|---|
| Sau Phase 10 | Tag `v2.0.0` và GitHub Release, triển khai công khai, trang docs, gửi bài | 06/12 |

## 2. Phải xử lý trước khi phát hành

| Việc | Ghi chú |
|---|---|
| Tag `v2.0.0` và GitHub Release | Tạo sau khi nhánh phase 10 vào `main`; ngày của mục `[2.0.0]` trong `CHANGELOG.md` sửa theo ngày tag; chạy workflow Release từ tag để có image `v2.0.0` mà `dxos-install.sh` kéo về |
| Trang docs `DX-OS-docs` | Vẫn mô tả bản `v1.1.1`; cập nhật cùng lúc triển khai |
| Triển khai công khai | VPS giữ `v1.1.1`; `deploy/VPS.md` còn mô tả bản đó, và bản mới không có đường nâng cấp dữ liệu nên phải dựng lại; đặt `STAFF_OTP_FLOW=CONDITIONAL` trước lần khởi động đầu để bắt ADMIN nhập mã một lần |
| Gửi bài | Thư tới BTC trước 17:00 ngày 06/12/2026 |

## 3. Giới hạn đã biết

| Việc | Ghi chú |
|---|---|
| CLI `dxos` chưa có `plan` và `apply` | Áp dụng mô hình qua bảng điều khiển hoặc REST |
| Dịch vụ `ai/` | Chưa nằm trong compose; chỉ tìm kiếm tri thức có nguồn, chưa có hỏi đáp sinh câu trả lời vì chưa có mô hình ngôn ngữ đạt tiêu chuẩn mở; dữ liệu huấn luyện của mô hình embedding chưa kiểm theo định nghĩa của OSI |
| Chưa cắt bớt service bằng compose profile | Mô hình không dùng thành phần nào vẫn phải chạy thành phần đó |
| Hành động hoàn tất | Chỉ có `add_to_group` và `remove_from_group`; nhóm Keycloak phải có sẵn, thiếu thì thông điệp được thử lại mãi và xem được ở tab Hàng đợi sự kiện; trường người dùng hoặc nhóm để trống thì thao tác bị bỏ qua, không để lại dấu vết |
| Tệp đính kèm | Xóa đối tượng trong kho lỗi thì chỉ ghi log, không thử lại nên có thể còn đối tượng mồ côi; quét ClamAV chỉ kiểm bằng test đơn vị, chưa chạy E2E với profile `av` |
| Nhiều bản sao của backend | Danh sách mô hình đang chạy chỉ nạp lại ở bản sao vừa áp dụng; chạy hai bản sao thì bản kia giữ mô hình cũ đến khi khởi động lại |
| Bộ quét quá hạn | Quét cả bảng của mỗi thực thể có hạn mỗi phút; cột `handle_by` chưa có chỉ mục |
| Trường kiểu `user` | Giá trị chưa được đối chiếu với người dùng có thật trong Keycloak |
| Hạn xử lý | Bộ quét quá hạn chỉ ghi dòng lịch sử; chưa có người nghe để nhắc qua chat |
| Đổi họ mẫu của một thực thể | Bị từ chối, kể cả khi mọi hồ sơ đã ở trạng thái cuối; muốn đổi hình dạng quy trình thì thêm thực thể mới; `docs/dynamic-core/README.md` còn mô tả việc chuyển hồ sơ đang chạy bằng `ProcessMigrationService`, chưa làm |
| Họ `work_item` | Hồ sơ ở trạng thái đã xử lý xong chờ người nộp xác nhận, chưa tự đóng sau một thời gian |
| Bảng điều khiển | Điền form viết lại tệp và bỏ chú thích giữa tệp; cột "Áp dụng bởi" hiện mã người dùng; trình soạn kéo theo khoảng 15 MB mã tĩnh, chỉ tải khi mở; trình duyệt báo lỗi `Missing requestHandler` từ worker của monaco-yaml khi mở trình soạn, cần kiểm việc báo lỗi và gợi ý theo schema |
| Lịch đặt chỗ | Chỉ xem và tạo theo khoảng chọn, chưa kéo thả để đổi giờ; tiêu đề lấy từ trường văn bản đầu tiên |
| Rút lại trên giao diện | Nút rút lại không mở ô nhập lý do, dù API cho phép ghi lý do |
| Áp dụng đã đổi tên chưa có đường hủy | Phải sửa nguyên nhân rồi chạy lại đúng tệp đó |
| Xác nhận mất dữ liệu không tự sao lưu | Người vận hành chạy `deploy/backup.sh` trước |
| Đổi thành viên đơn vị | Chỉ có hiệu lực sau khi người đó lấy token mới |
| Ngôn ngữ giao diện | Chỉ có tiếng Việt; thông báo mới của backend chưa có mẫu ở `backendMessages.ts` hiện nguyên văn tiếng Anh, vài thông báo lỗi còn tên trạng thái hoặc hành động dạng mã, và ô chọn ngày giờ theo ngôn ngữ của trình duyệt |

## 4. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Khởi động bản mới trên dữ liệu của `v1.1.1` | Không có đường nâng cấp dữ liệu |
| Hệ thống đã áp dụng mô hình trước Phase 8 | Cột `handle_by` chỉ vào bảng khi mô hình được áp dụng với nội dung khác; đặt lại stack hoặc đổi mô hình một lần |
| Mức tài nguyên của toàn bộ stack | Cần đo trước khi chọn cấu hình VPS |
