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
| **Lỗ hổng: tên vai dành riêng của nền tảng** | Bộ kiểm không cấm mô hình khai báo vai `ADMIN` (vai quản trị nền tảng: áp dụng, đổi và quay lui mọi mô hình) hay vai mặc định của Keycloak (`offline_access`, `uma_authorization`, `default-roles-dxos`); gán vai đó cho người dùng của một ứng dụng là trao cho họ quyền quản trị cả nền tảng. Sửa cùng bộ kiểm vai trùng tên ở phần 5 |
| Dịch vụ `ai/` | Chưa nằm trong compose; chỉ tìm kiếm tri thức có nguồn, chưa có hỏi đáp sinh câu trả lời vì chưa có mô hình ngôn ngữ đạt tiêu chuẩn mở; dữ liệu huấn luyện của mô hình embedding chưa kiểm theo định nghĩa của OSI |
| Chưa cắt bớt service bằng compose profile | Mô hình không dùng thành phần nào vẫn phải chạy thành phần đó |
| Hành động hoàn tất | Chỉ có `add_to_group` và `remove_from_group`; nhóm Keycloak phải có sẵn, thiếu thì thông điệp được thử lại mãi và xem được ở tab Hàng đợi sự kiện; trường người dùng hoặc nhóm để trống thì thao tác bị bỏ qua, không để lại dấu vết |
| Tệp đính kèm | Xóa đối tượng trong kho lỗi thì chỉ ghi log, không thử lại nên có thể còn đối tượng mồ côi |
| Nhiều bản sao của backend | Danh sách mô hình đang chạy chỉ nạp lại ở bản sao vừa áp dụng; chạy hai bản sao thì bản kia giữ mô hình cũ đến khi khởi động lại |
| Trường kiểu `user` | Giá trị chưa được đối chiếu với người dùng có thật trong Keycloak |
| Hạn xử lý | Bộ quét quá hạn chỉ ghi dòng lịch sử; chưa có người nghe để nhắc qua chat |
| Đổi họ mẫu của một thực thể | Bị từ chối, kể cả khi mọi hồ sơ đã ở trạng thái cuối; muốn đổi hình dạng quy trình thì thêm thực thể mới; `docs/dynamic-core/README.md` còn mô tả việc chuyển hồ sơ đang chạy bằng `ProcessMigrationService`, chưa làm |
| Họ `work_item` | Hồ sơ ở trạng thái đã xử lý xong chờ người nộp xác nhận, chưa tự đóng sau một thời gian |
| Bảng điều khiển | Điền form viết lại tệp và bỏ chú thích giữa tệp; trình soạn kéo theo khoảng 15 MB mã tĩnh, chỉ tải khi mở; phiên bản áp dụng trước khi có cột `applied_by_name` hiện mã người dùng thay cho tên |
| Lịch đặt chỗ | Chỉ xem và tạo theo khoảng chọn, chưa kéo thả để đổi giờ; tiêu đề lấy từ trường văn bản đầu tiên |
| Áp dụng đã đổi tên chưa có đường hủy | Phải sửa nguyên nhân rồi chạy lại đúng tệp đó |
| Xác nhận mất dữ liệu không tự sao lưu | Người vận hành chạy `deploy/backup.sh` trước |
| Đổi thành viên đơn vị | Chỉ có hiệu lực sau khi người đó lấy token mới |
| Ngôn ngữ giao diện | Chỉ có tiếng Việt; thông báo backend thêm sau mà chưa có mẫu ở `backendMessages.ts` hiện nguyên văn tiếng Anh, vài thông báo lỗi còn tên trạng thái hoặc hành động dạng mã, và ô chọn ngày giờ theo ngôn ngữ của trình duyệt |

## 4. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Khởi động bản mới trên dữ liệu của `v1.1.1` | Không có đường nâng cấp dữ liệu |
| Hệ thống đã áp dụng mô hình trước bản này | Cột `handle_by` và chỉ mục của nó chỉ vào bảng khi mô hình được áp dụng với nội dung khác; đặt lại stack hoặc đổi mô hình một lần |
| Mức tài nguyên của toàn bộ stack | Cần đo trước khi chọn cấu hình VPS |

## 5. Đã quyết, chờ làm

| Việc | Đã quyết |
|---|---|
| Tài khoản người dùng | Không khai báo trong tệp mô hình: tệp mô hình là cấu trúc và nằm trong git, người dùng là dữ liệu có email và mật khẩu; tài khoản nằm ở Keycloak |
| Vai tách theo ứng dụng, đơn vị dùng chung | Hiện mọi mô hình tạo vai và nhóm cùng tên trong một realm, nên hai ứng dụng khai báo cùng tên vai sẽ dùng chung quyền. Đơn vị là cơ cấu tổ chức nên dùng chung giữa các ứng dụng; vai phải thuộc riêng từng ứng dụng và quản lý riêng. Làm trước: bộ kiểm từ chối vai trùng tên với vai của mô hình khác đang chạy. Cách tách hẳn đem vào buổi thảo luận; ứng viên là client role của Keycloak, mỗi mô hình một client |
| Tab Người dùng trong bảng điều khiển | Danh sách người dùng với vai theo từng ứng dụng và đơn vị; tạo người dùng với mật khẩu tạm; gán, gỡ vai và đơn vị; khóa, mở; đặt lại mật khẩu; xem theo ứng dụng ai giữ vai nào. Viết ít mã nhất, dùng tối đa Keycloak: lõi chỉ mở API ADMIN mỏng trên Admin REST API, phần Keycloak đã làm tốt (chính sách mật khẩu, MFA, phiên, đăng nhập bằng nhà cung cấp ngoài) để lại cho Keycloak và mở bằng link |

## 6. Đề xuất từ rà soát đề thi và hướng thiết kế, chưa quyết

Đối chiếu đề, thể lệ, các nguyên lý của đề và những gì [`README.md`](./README.md) đã hứa với mã hiện tại.

| Thiếu sót | Căn cứ | Đề xuất |
|---|---|---|
| Tiêu chí 1 chưa đạt trọn: chưa có một lệnh nhận tệp mô hình | "Thế nào là đạt": chạy một lệnh với tệp mô hình trên máy sạch | `dxos apply` hoặc `dxos start <tệp>`, làm cùng `plan` và `apply` trong buổi thảo luận; nên xong trước khi phát hành |
| 324 kiểm tra E2E chỉ chạy tay, CI không chạy | Nguyên tắc "kiểm thử từ góc người dùng" của README; 50 điểm kho mã | Job CI dựng stack bằng bộ cài từ gói của commit rồi chạy bảy script ở `deploy/dev` |
| Trục sự kiện chỉ phát sự kiện hoàn tất ở trạng thái cuối, chưa có bên ngoài nào nghe được | README: mọi thay đổi hồ sơ và bước quy trình phát ra sự kiện; đề: kiến trúc hướng sự kiện, chống ốc đảo dữ liệu | Phát sự kiện cho mọi bước và mọi thay đổi hồ sơ, thêm webhook; đem vào buổi thảo luận phần mở rộng |
| Ứng dụng Tầng 2 mới phủ P của H-P-D-I | Đề: ứng dụng minh họa nằm trong không gian H-P-D-I | D: trang thống kê dựng từ metadata (theo trạng thái, quá hạn, đơn vị), hoặc Superset với dataset sinh từ mô hình qua vai `report_ro` đang có. H: thông báo việc mới và nhắc hạn qua webhook hoặc email. I: để sau như đã định |
| Áp dụng dở không tự thử lại hay quay lui | README "tự phục hồi"; nguyên lý Zero-Trust gắn GitOps, tự phục hồi | Tự chạy lại lần áp dụng dở khi dịch vụ khởi động, hoặc nút tiếp tục ở bảng điều khiển; không làm thì sửa README cho đúng |
| Hồ sơ không lưu phiên bản mô hình lúc bắt đầu | README: quy trình đọc tham số theo phiên bản mô hình của hồ sơ | Làm cùng việc chuyển hồ sơ đang chạy bằng `ProcessMigrationService`, hoặc sửa README cho đúng hiện trạng |
| Người dùng cuối không tự đổi mật khẩu, bật OTP hay xem phiên | Dùng tối đa Keycloak | Link "Tài khoản của tôi" tới Account Console của Keycloak trong menu người dùng; gần như không viết mã |
| Chưa đo tài nguyên của toàn stack | Đối tượng SME, bộ cài càng nhẹ càng tốt; cũng là mục ở phần 4 | Đo bằng `docker stats` khi chạy bảy script, ghi số vào README và `doctor.sh` |
| Mẫu nghiệp vụ nằm trong image của dịch vụ lõi | Nguyên tắc "lõi không biết về nghiệp vụ" | Chuyển thư viện mẫu ra thư mục riêng của bản cài; ưu tiên thấp |
