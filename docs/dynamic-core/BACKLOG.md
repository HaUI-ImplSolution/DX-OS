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
| Nhiều bản sao của backend | Đã thử hai bản sau gateway: danh sách mô hình và trạng thái giữ chỉ nằm trong bộ nhớ từng bản sao, nên sau một lần áp dụng 22 trong 40 yêu cầu nhận 404 vì bản kia chưa nạp mô hình, và mô hình đang bị giữ vẫn được bản kia phục vụ; khởi động lại cả hai thì đúng; hiện chỉ chạy một bản; hướng làm: bản sao khác định kỳ đối chiếu `apply_run` và `model_version` rồi nạp lại |
| Trường kiểu `user` | Giá trị chưa được đối chiếu với người dùng có thật trong Keycloak |
| Hạn xử lý | Bộ quét quá hạn chỉ ghi dòng lịch sử; chưa có người nghe để nhắc qua chat |
| Đổi họ mẫu của một thực thể | Bị từ chối, kể cả khi mọi hồ sơ đã ở trạng thái cuối; muốn đổi hình dạng quy trình thì thêm thực thể mới |
| Họ `work_item` | Hồ sơ ở trạng thái đã xử lý xong chờ người nộp xác nhận, chưa tự đóng sau một thời gian |
| Bảng điều khiển | Điền form viết lại tệp và bỏ chú thích giữa tệp; trình soạn kéo theo khoảng 15 MB mã tĩnh, chỉ tải khi mở; phiên bản áp dụng trước khi có cột `applied_by_name`, và thẻ của mô hình chưa có phiên bản, hiện mã người dùng rút gọn thay cho tên |
| Lịch đặt chỗ | Chỉ xem và tạo theo khoảng chọn, chưa kéo thả để đổi giờ; tiêu đề lấy từ trường văn bản đầu tiên |
| Xác nhận mất dữ liệu không tự sao lưu | Người vận hành chạy `deploy/backup.sh` trước |
| Đổi thành viên đơn vị | Chỉ có hiệu lực sau khi người đó lấy token mới |
| Đơn vị dùng chung giữa các ứng dụng | Đơn vị là cơ cấu tổ chức nên mọi mô hình dùng cùng nhóm Keycloak theo tên; vai đã tách theo client của từng mô hình; cần đơn vị riêng từng ứng dụng thì bàn lại |
| Ngôn ngữ giao diện | Chỉ có tiếng Việt; thông báo backend thêm sau mà chưa có mẫu ở `backendMessages.ts` hiện nguyên văn tiếng Anh, vài thông báo lỗi còn tên trạng thái hoặc hành động dạng mã, và ô chọn ngày giờ theo ngôn ngữ của trình duyệt |
| Mô hình có lần áp dụng dở | Bị đóng với người dùng ngay cả khi lỗi xảy ra trước mọi thay đổi; người dùng chỉ thấy mô hình biến khỏi menu, không có lời giải thích |
| Hủy bỏ lần áp dụng dở | Đưa bảng về phiên bản đang chạy: cột và bảng đã thêm bị xóa cùng dữ liệu, thứ đã xóa không quay lại; mô hình chưa có phiên bản thì xóa cả schema và bảng của lần áp dụng đầu |
| Đổi luật quy trình khi còn hồ sơ mở | Người xử lý, hạn xử lý, quyền tự quyết, hành động hoàn tất và đặt chỗ chỉ đổi được khi thực thể không còn hồ sơ đang xử lý, vì hồ sơ chưa lưu phiên bản mô hình; phải hoàn tất hoặc rút hết hồ sơ trước; chuyển hồ sơ đang chạy bằng `ProcessMigrationService` chưa làm |
| Lần áp dụng dở ghi trước migration `V6` | Không có nội dung kèm theo nên không dùng được Tiếp tục hay Hủy bỏ; áp dụng lại tệp |
| Client, vai và nhóm còn lại ở Keycloak | Vai bỏ khỏi tệp, và client, vai, nhóm mà một lần áp dụng dở đã tạo, vẫn còn ở Keycloak cùng người giữ; khai báo lại tên vai trong cùng mô hình thì người giữ có lại quyền; tab Người dùng hiện vai còn lại để gỡ khỏi từng người, còn xóa hẳn client, vai hay nhóm thì làm tay ở Keycloak |
| Xóa thực thể | Bảng, hồ sơ, tệp đính kèm và đối tượng trong kho bị xóa cùng; lịch sử Flowable đã kết thúc, audit và outbox của thực thể còn lại; thực thể còn hồ sơ đang chạy quy trình phải hoàn tất hoặc rút hết hồ sơ trước |
| Khe hở khi bắt đầu áp dụng | Yêu cầu vào trước khi mô hình bị giữ vẫn chạy song song với bước đổi bảng, và hồ sơ tạo giữa lúc kiểm và lúc áp dụng không được tính vào số hồ sơ đang mở; Postgres tự chặn truy vấn khi đổi bảng; hướng làm: mỗi yêu cầu lấy khóa chia sẻ theo mô hình, lần áp dụng lấy khóa độc quyền |
| Hệ thống đã áp dụng mô hình bằng bản cũ hơn | Bảng thiếu cột `handle_by` làm API dữ liệu của thực thể có quy trình trả 500 (danh sách, đọc, tạo, hộp việc) và bộ quét quá hạn lỗi mỗi phút; bảng thiếu chỉ mục `handle_by` vẫn chạy bình thường; hồ sơ đang xử lý trước đó không bị tính quá hạn cho đến lần chuyển trạng thái kế; vai của mô hình áp dụng từ trước khi có client riêng là vai realm nên người dùng không còn vào được; áp dụng lại cùng tệp trả `unchanged` và không sửa gì, chỉ một tệp có nội dung khác mới thêm cột, chỉ mục và client (hướng sửa ở phần 7); chưa có bản triển khai nào ở trạng thái này, nên dựng lại bằng `deploy/reset.sh` |
| Audit chỉ ghi thay đổi về tài khoản | Thay đổi vai, đơn vị và tài khoản của ADMIN vào `audit_log`; áp dụng, đổi và quay lui mô hình thành công chưa vào đó (lịch sử ở `apply_run` và `model_version`); nếu việc ghi audit lỗi thì chỉ có log và thay đổi vẫn thành công; thay đổi làm thẳng trong Admin Console chỉ có ở sự kiện quản trị của Keycloak, giữ 90 ngày, ghi tài khoản người thực hiện |
| Xem trước không giữ khóa | Kế hoạch có thể cũ khi người khác áp dụng xen vào; bước áp dụng kiểm lại mọi điều kiện |

## 4. Rủi ro kỹ thuật hiện tại

| Rủi ro | Hướng xử lý |
|---|---|
| Kết nối của backend đứng ở schema khác `public`: `audit_log` từng báo `relation "audit_log" does not exist` ở mọi lần ghi và đọc, nên audit không chạy; hiện entity ghi rõ `schema = "public"` nên audit chạy | Chưa xác định nguyên nhân gốc; hai nghi phạm cùng đổi schema của kết nối: Flyway của engine (`defaultSchema("engine")`) và Flowable (`setDatabaseSchema("engine")`). Kiểm: log `current_schema()` của các kết nối trong pool ngay sau khi khởi động, rồi bỏ từng nghi phạm để xem lỗi mất không. Sửa gốc nếu đúng: Flowable dùng tiền tố bảng `engine.` thay cho schema, Flyway bỏ `defaultSchema`. Đến lúc đó, mọi truy vấn vào bảng ngoài schema `engine` phải ghi rõ schema |

Lõi không có giao dịch chung giữa PostgreSQL, Keycloak, Flowable và SeaweedFS; mỗi bước chạy lại được, và lần áp dụng dở được tiếp tục hoặc hủy bỏ (xem phần 3).

## 5. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Tab Người dùng: khóa và mở tài khoản, gán và gỡ đơn vị trên giao diện | Chỉ kiểm bằng API |
| Mật khẩu tạm bắt đổi ngay ở trang đăng nhập của Keycloak | Chỉ kiểm bằng cấp token trực tiếp, thấy Keycloak báo tài khoản chưa thiết lập xong |
| OTP có điều kiện của vai `ADMIN` khi bật `STAFF_OTP_FLOW=CONDITIONAL` | Vai `ADMIN` vẫn là vai realm nhưng chưa thử lại sau khi vai của mô hình chuyển sang client role |
| Áp dụng mô hình trên realm đã import thiếu quyền client của tài khoản dịch vụ | Keycloak sẽ từ chối (403) ở bước `ENSURE_CLIENT`; chưa thử |
| Thẻ "Đang dừng dở" ở tab Mô hình sau khi vai chuyển sang client role | Chưa mở lại trên giao diện |

## 6. Đã quyết, chờ làm

| Việc | Đã quyết |
|---|---|
| Lịch sử thay đổi trong hộp thoại người dùng | Hộp thoại người dùng hiện các bản ghi audit `USER_*` về người đó: ai đổi gì, lúc nào; dữ liệu đã có ở `audit_log`, tra qua `GET /api/v1/audit-logs` |

## 7. Đề xuất từ rà soát đề thi và hướng thiết kế, chưa quyết

Đối chiếu đề, thể lệ, các nguyên lý của đề và những gì [`README.md`](./README.md) đã hứa với mã hiện tại.

| Thiếu sót | Căn cứ | Đề xuất |
|---|---|---|
| Tiêu chí 1 chưa đạt trọn: chưa có một lệnh nhận tệp mô hình | "Thế nào là đạt": chạy một lệnh với tệp mô hình trên máy sạch | `dxos apply` hoặc `dxos start <tệp>`, làm cùng `plan` và `apply` trong buổi thảo luận; nên xong trước khi phát hành |
| 428 kiểm tra E2E chỉ chạy tay, CI không chạy | Nguyên tắc "kiểm thử từ góc người dùng" của README; 50 điểm kho mã | Job CI dựng stack bằng bộ cài từ gói của commit rồi chạy chín script ở `deploy/dev` |
| Trục sự kiện chỉ phát sự kiện hoàn tất ở trạng thái cuối, chưa có bên ngoài nào nghe được | README: mọi thay đổi hồ sơ và bước quy trình phát ra sự kiện; đề: kiến trúc hướng sự kiện, chống ốc đảo dữ liệu | Phát sự kiện cho mọi bước và mọi thay đổi hồ sơ, thêm webhook; đem vào buổi thảo luận phần mở rộng |
| Ứng dụng Tầng 2 mới phủ P của H-P-D-I | Đề: ứng dụng minh họa nằm trong không gian H-P-D-I | D: trang thống kê dựng từ metadata (theo trạng thái, quá hạn, đơn vị), hoặc Superset với dataset sinh từ mô hình qua vai `report_ro` đang có. H: thông báo việc mới và nhắc hạn qua webhook hoặc email. I: để sau như đã định |
| Hồ sơ không lưu phiên bản mô hình lúc bắt đầu | Luật quy trình chỉ đổi được khi không còn hồ sơ mở, nên chưa cần | Làm cùng việc chuyển hồ sơ đang chạy bằng `ProcessMigrationService` khi cần đổi luật ngay cả lúc còn hồ sơ mở |
| Người dùng cuối không tự đổi mật khẩu, bật OTP hay xem phiên | Dùng tối đa Keycloak | Link "Tài khoản của tôi" tới Account Console của Keycloak trong menu người dùng; gần như không viết mã |
| Áp dụng lại cùng tệp không sửa phần bị lệch | Lõi trả `unchanged` khi mã băm của tệp trùng, nên không thấy bảng thiếu cột hoặc chỉ mục, bảng bị sửa tay, hay client và vai Keycloak chưa có | So schema và Keycloak thật với mô hình cả khi tệp không đổi và sửa phần lệch; áp dụng lại cùng tệp sẽ chạy thêm pgschema nên cần bàn |
| Ngưỡng của `doctor.sh` cao hơn số đo | `doctor.sh` cảnh báo dưới 8 GB RAM và dưới 40 GiB đĩa; stack tám service đo được dùng tối đa khoảng 1,7 GiB RAM, image gần 1 GiB và dữ liệu khoảng 100 MiB sau kiểm thử (`deploy/OPERATIONS.md`) | Hạ ngưỡng sau khi chốt cấu hình VPS; chưa đo với nhiều người dùng đồng thời hay ít nhân hơn |
| Mẫu nghiệp vụ nằm trong image của dịch vụ lõi | Nguyên tắc "lõi không biết về nghiệp vụ" | Chuyển thư viện mẫu ra thư mục riêng của bản cài; ưu tiên thấp |
