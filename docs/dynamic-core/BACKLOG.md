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
| Tệp đính kèm | Xóa đối tượng trong kho lỗi thì chỉ ghi log, không thử lại nên có thể còn đối tượng mồ côi |
| Nhiều bản sao của backend | Trạng thái giữ mô hình và danh sách mô hình đang chạy chỉ nằm trong bộ nhớ của từng bản sao; chạy hai bản sao thì bản kia giữ mô hình cũ đến khi khởi động lại; hướng làm: bản sao khác định kỳ đối chiếu `apply_run` và `model_version` rồi nạp lại |
| Trường kiểu `user` | Giá trị chưa được đối chiếu với người dùng có thật trong Keycloak |
| Hạn xử lý | Bộ quét quá hạn chỉ ghi dòng lịch sử; chưa có người nghe để nhắc qua chat |
| Đổi họ mẫu của một thực thể | Bị từ chối, kể cả khi mọi hồ sơ đã ở trạng thái cuối; muốn đổi hình dạng quy trình thì thêm thực thể mới |
| Họ `work_item` | Hồ sơ ở trạng thái đã xử lý xong chờ người nộp xác nhận, chưa tự đóng sau một thời gian |
| Bảng điều khiển | Điền form viết lại tệp và bỏ chú thích giữa tệp; trình soạn kéo theo khoảng 15 MB mã tĩnh, chỉ tải khi mở; phiên bản áp dụng trước khi có cột `applied_by_name`, và thẻ của mô hình chưa có phiên bản, hiện mã người dùng rút gọn thay cho tên |
| Lịch đặt chỗ | Chỉ xem và tạo theo khoảng chọn, chưa kéo thả để đổi giờ; tiêu đề lấy từ trường văn bản đầu tiên |
| Xác nhận mất dữ liệu không tự sao lưu | Người vận hành chạy `deploy/backup.sh` trước |
| Đổi thành viên đơn vị | Chỉ có hiệu lực sau khi người đó lấy token mới |
| Ngôn ngữ giao diện | Chỉ có tiếng Việt; thông báo backend thêm sau mà chưa có mẫu ở `backendMessages.ts` hiện nguyên văn tiếng Anh, vài thông báo lỗi còn tên trạng thái hoặc hành động dạng mã, và ô chọn ngày giờ theo ngôn ngữ của trình duyệt |
| Mô hình có lần áp dụng dở | Bị đóng với người dùng ngay cả khi lỗi xảy ra trước mọi thay đổi; người dùng chỉ thấy mô hình biến khỏi menu, không có lời giải thích |
| Hủy bỏ lần áp dụng dở | Đưa bảng về phiên bản đang chạy: cột và bảng đã thêm bị xóa cùng dữ liệu, thứ đã xóa không quay lại; mô hình chưa có phiên bản thì xóa cả schema và bảng của lần áp dụng đầu; vai và nhóm đã tạo ở Keycloak vẫn còn |
| Đổi luật quy trình khi còn hồ sơ mở | Người xử lý, hạn xử lý, quyền tự quyết, hành động hoàn tất và đặt chỗ chỉ đổi được khi thực thể không còn hồ sơ đang xử lý, vì hồ sơ chưa lưu phiên bản mô hình; phải hoàn tất hoặc rút hết hồ sơ trước; chuyển hồ sơ đang chạy bằng `ProcessMigrationService` chưa làm |
| Lần áp dụng dở ghi trước migration `V6` | Không có nội dung kèm theo nên không dùng được Tiếp tục hay Hủy bỏ; áp dụng lại tệp |
| Vai còn lại ở Keycloak khi mô hình bỏ vai | Lõi chưa ghi vai thuộc mô hình nào, nên vai và người giữ nó vẫn còn trong Keycloak sau khi mô hình bỏ vai hoặc hủy một lần áp dụng dở; mô hình khác khai báo lại tên đó thì có sẵn những người giữ; dọn tay ở Keycloak; hướng làm: vai thuộc riêng từng mô hình (client role, phần 6) hoặc bảng ghi vai thuộc mô hình nào để từ chối vai đã bỏ |
| Hai mô hình đã lưu dùng chung vai | Mô hình lưu từ trước chốt chặn vẫn chạy và chỉ có dòng log lỗi lúc khởi động, nên người giữ vai chung vẫn vào được dữ liệu của cả hai cho đến khi đổi tên vai; mô hình đó không áp dụng thêm được cho đến lúc ấy; bản 2.0.0 chưa triển khai nên chưa có dữ liệu như vậy |
| Xóa thực thể | Bảng, hồ sơ, tệp đính kèm và đối tượng trong kho bị xóa cùng; lịch sử Flowable đã kết thúc, audit và outbox của thực thể còn lại; thực thể còn hồ sơ đang chạy quy trình phải hoàn tất hoặc rút hết hồ sơ trước |
| Khe hở khi bắt đầu áp dụng | Yêu cầu vào trước khi mô hình bị giữ vẫn chạy song song với bước đổi bảng, và hồ sơ tạo giữa lúc kiểm và lúc áp dụng không được tính vào số hồ sơ đang mở; Postgres tự chặn truy vấn khi đổi bảng; hướng làm: mỗi yêu cầu lấy khóa chia sẻ theo mô hình, lần áp dụng lấy khóa độc quyền |
| Xem trước không giữ khóa | Kế hoạch có thể cũ khi người khác áp dụng xen vào; bước áp dụng kiểm lại mọi điều kiện |

## 4. Rủi ro kỹ thuật hiện tại

Không còn rủi ro nào chưa được xử lý hoặc ghi ở phần khác.
Lõi không có giao dịch chung giữa PostgreSQL, Keycloak, Flowable và SeaweedFS; mỗi bước chạy lại được, và lần áp dụng dở được tiếp tục hoặc hủy bỏ (xem phần 3).

## 5. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Khởi động bản mới trên dữ liệu của `v1.1.1` | Không có đường nâng cấp dữ liệu |
| Hệ thống đã áp dụng mô hình trước bản này | Cột `handle_by` và chỉ mục của nó chỉ vào bảng khi mô hình được áp dụng với nội dung khác; đặt lại stack hoặc đổi mô hình một lần |
| Mức tài nguyên của toàn bộ stack | Cần đo trước khi chọn cấu hình VPS |
| Hai bản sao backend chạy cùng lúc | Chưa thử |
| Dòng log lỗi khi hai mô hình đã lưu dùng chung vai | Không dựng được tình huống qua API vì bộ kiểm chặn từ đầu |
| Đổi luật quy trình khi còn hồ sơ mở trên giao diện | Chỉ có kiểm tự động của API |

## 6. Đã quyết, chờ làm

| Việc | Đã quyết |
|---|---|
| Tài khoản người dùng | Không khai báo trong tệp mô hình: tệp mô hình là cấu trúc và nằm trong git, người dùng là dữ liệu có email và mật khẩu; tài khoản nằm ở Keycloak |
| Vai tách theo ứng dụng, đơn vị dùng chung | Mọi mô hình tạo vai và nhóm cùng tên trong một realm; bộ kiểm từ chối vai trùng với mô hình khác và vai nền tảng `ADMIN` đã có. Đơn vị là cơ cấu tổ chức nên dùng chung giữa các ứng dụng; vai phải thuộc riêng từng ứng dụng và quản lý riêng. Cách tách hẳn đem vào buổi thảo luận; ứng viên là client role của Keycloak, mỗi mô hình một client |
| Tab Người dùng trong bảng điều khiển | Danh sách người dùng với vai theo từng ứng dụng và đơn vị; tạo người dùng với mật khẩu tạm; gán, gỡ vai và đơn vị; khóa, mở; đặt lại mật khẩu; xem theo ứng dụng ai giữ vai nào. Viết ít mã nhất, dùng tối đa Keycloak: lõi chỉ mở API ADMIN mỏng trên Admin REST API, phần Keycloak đã làm tốt (chính sách mật khẩu, MFA, phiên, đăng nhập bằng nhà cung cấp ngoài) để lại cho Keycloak và mở bằng link |

## 7. Đề xuất từ rà soát đề thi và hướng thiết kế, chưa quyết

Đối chiếu đề, thể lệ, các nguyên lý của đề và những gì [`README.md`](./README.md) đã hứa với mã hiện tại.

| Thiếu sót | Căn cứ | Đề xuất |
|---|---|---|
| Tiêu chí 1 chưa đạt trọn: chưa có một lệnh nhận tệp mô hình | "Thế nào là đạt": chạy một lệnh với tệp mô hình trên máy sạch | `dxos apply` hoặc `dxos start <tệp>`, làm cùng `plan` và `apply` trong buổi thảo luận; nên xong trước khi phát hành |
| 387 kiểm tra E2E chỉ chạy tay, CI không chạy | Nguyên tắc "kiểm thử từ góc người dùng" của README; 50 điểm kho mã | Job CI dựng stack bằng bộ cài từ gói của commit rồi chạy tám script ở `deploy/dev` |
| Trục sự kiện chỉ phát sự kiện hoàn tất ở trạng thái cuối, chưa có bên ngoài nào nghe được | README: mọi thay đổi hồ sơ và bước quy trình phát ra sự kiện; đề: kiến trúc hướng sự kiện, chống ốc đảo dữ liệu | Phát sự kiện cho mọi bước và mọi thay đổi hồ sơ, thêm webhook; đem vào buổi thảo luận phần mở rộng |
| Ứng dụng Tầng 2 mới phủ P của H-P-D-I | Đề: ứng dụng minh họa nằm trong không gian H-P-D-I | D: trang thống kê dựng từ metadata (theo trạng thái, quá hạn, đơn vị), hoặc Superset với dataset sinh từ mô hình qua vai `report_ro` đang có. H: thông báo việc mới và nhắc hạn qua webhook hoặc email. I: để sau như đã định |
| Hồ sơ không lưu phiên bản mô hình lúc bắt đầu | Luật quy trình chỉ đổi được khi không còn hồ sơ mở, nên chưa cần | Làm cùng việc chuyển hồ sơ đang chạy bằng `ProcessMigrationService` khi cần đổi luật ngay cả lúc còn hồ sơ mở |
| Người dùng cuối không tự đổi mật khẩu, bật OTP hay xem phiên | Dùng tối đa Keycloak | Link "Tài khoản của tôi" tới Account Console của Keycloak trong menu người dùng; gần như không viết mã |
| Chưa đo tài nguyên của toàn stack | Đối tượng SME, bộ cài càng nhẹ càng tốt; cũng là mục ở phần 5 | Đo bằng `docker stats` khi chạy tám script, ghi số vào README và `doctor.sh` |
| Mẫu nghiệp vụ nằm trong image của dịch vụ lõi | Nguyên tắc "lõi không biết về nghiệp vụ" | Chuyển thư viện mẫu ra thư mục riêng của bản cài; ưu tiên thấp |
