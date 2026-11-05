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
| Ma trận bằng chứng | Chưa có bảng yêu cầu → chức năng → bài kiểm → CI hoặc demo → giới hạn; chưa tách bằng chứng từ dữ liệu giả lập, từ stack kiểm và từ triển khai công khai; kết quả kiểm tay ở `docs/TESTING.md` còn ghi trên cây làm việc gồm tệp chưa commit |
| Bộ hỏi đáp kỹ thuật | Chưa có; gắn với mã và demo của bản nộp: ranh giới lõi, đóng góp riêng, đổi mô hình an toàn, giới hạn phục hồi |
| Tag `v2.0.0` và GitHub Release | Tạo sau khi nhánh phase 10 vào `main`; ngày của mục `[2.0.0]` trong `CHANGELOG.md` sửa theo ngày tag; chạy workflow Release từ tag để có image `v2.0.0` mà `dxos-install.sh` kéo về |
| Kiểm cài bằng image thật | `dxos-install.sh` và `dxos start <tệp>` mới kiểm bằng image local qua `install.sh`; sau khi workflow Release có image `v2.0.0`, chạy lại trên máy sạch |
| Trang docs `DX-OS-docs` | Vẫn mô tả bản `v1.1.1`; cập nhật cùng lúc triển khai |
| Triển khai công khai | VPS giữ `v1.1.1`; `deploy/VPS.md` còn mô tả bản đó, và bản mới không có đường nâng cấp dữ liệu nên phải dựng lại; đặt `STAFF_OTP_FLOW=CONDITIONAL` trước lần khởi động đầu để bắt ADMIN nhập mã một lần, và bật quét mã độc (`dxos av on`) |
| Gửi bài | Thư tới BTC trước 17:00 ngày 06/12/2026 |

## 3. Giới hạn đã biết

| Việc | Ghi chú |
|---|---|
| Nạp mô hình lúc khởi động | Chỉ một tệp mỗi lần |
| Dịch vụ `ai/` | Chưa nằm trong compose; chỉ tìm kiếm tri thức có nguồn, chưa có hỏi đáp sinh câu trả lời vì chưa có mô hình ngôn ngữ đạt tiêu chuẩn mở; dữ liệu huấn luyện của mô hình embedding chưa kiểm theo định nghĩa của OSI |
| Chưa cắt bớt service bằng compose profile | Mô hình không dùng thành phần nào vẫn phải chạy thành phần đó |
| Hành động hoàn tất | Chỉ có `add_to_group` và `remove_from_group`; nhóm Keycloak phải có sẵn, thiếu thì thông điệp xem được ở tab Hàng đợi sự kiện; trường người dùng hoặc nhóm để trống thì thao tác bị bỏ qua, chỉ để lại một dòng `WARN` trong log backend |
| Nhiều bản sao của backend | Đã thử hai bản sau gateway: danh sách mô hình và trạng thái giữ chỉ nằm trong bộ nhớ từng bản sao, nên sau một lần áp dụng 22 trong 40 yêu cầu nhận 404 vì bản kia chưa nạp mô hình, và mô hình đang bị giữ vẫn được bản kia phục vụ; khởi động lại cả hai thì đúng; hiện chỉ chạy một bản; hướng làm: bản sao khác định kỳ đối chiếu `apply_run` và `model_version` rồi nạp lại |
| Trường kiểu `user` | Giá trị chưa được đối chiếu với người dùng có thật trong Keycloak |
| Hạn xử lý | Bộ quét quá hạn chỉ ghi dòng lịch sử; chưa có người nghe để nhắc qua chat |
| Đổi họ mẫu của một thực thể | Bị từ chối, kể cả khi mọi hồ sơ đã ở trạng thái cuối; muốn đổi hình dạng quy trình thì thêm thực thể mới |
| Họ `work_item` | Hồ sơ ở trạng thái đã xử lý xong chờ người nộp xác nhận, chưa tự đóng sau một thời gian |
| Bảng điều khiển | Điền form viết lại tệp và bỏ chú thích giữa tệp; trình soạn kéo theo khoảng 15 MB mã tĩnh, chỉ tải khi mở; phiên bản áp dụng trước khi có cột `applied_by_name`, và thẻ của mô hình chưa có phiên bản, hiện mã người dùng rút gọn thay cho tên |
| Lịch đặt chỗ | Chỉ xem và tạo theo khoảng chọn, chưa kéo thả để đổi giờ; tiêu đề lấy từ trường văn bản đầu tiên |
| Xác nhận mất dữ liệu không tự sao lưu | Người vận hành chạy `deploy/backup.sh` trước |
| Đổi thành viên đơn vị | Chỉ có hiệu lực sau khi người đó lấy token mới |
| Đơn vị dùng chung giữa các ứng dụng | Đơn vị là cơ cấu tổ chức nên mọi mô hình dùng cùng nhóm Keycloak theo tên; vai đã tách theo client của từng mô hình; khai báo lại một đơn vị đã bỏ thì người còn trong nhóm có lại quyền theo đơn vị mà kế hoạch không báo, vì thành viên đơn vị là dữ kiện của tổ chức; cần đơn vị riêng từng ứng dụng thì bàn lại |
| Ngôn ngữ giao diện | Chỉ có tiếng Việt; thông báo backend thêm sau mà chưa có mẫu ở `backendMessages.ts` hiện nguyên văn tiếng Anh, vài thông báo lỗi còn tên trạng thái hoặc hành động dạng mã, và ô chọn ngày giờ theo ngôn ngữ của trình duyệt |
| Mô hình có lần áp dụng dở | Bị đóng với người dùng ngay cả khi lỗi xảy ra trước mọi thay đổi; người dùng chỉ thấy mô hình biến khỏi menu, không có lời giải thích |
| Tài khoản dịch vụ thiếu quyền ở Keycloak | Áp dụng mô hình bị Keycloak từ chối (403) ở bước `ENSURE_CLIENT`; log backend ghi rõ lý do, nhưng người dùng thấy câu "Dịch vụ tài khoản chưa dùng được, vui lòng thử lại sau" như khi Keycloak tắt hẳn, nên dễ tưởng thử lại sẽ hết lỗi |
| Hủy bỏ lần áp dụng dở | Đưa bảng về phiên bản đang chạy: cột và bảng đã thêm bị xóa cùng dữ liệu, thứ đã xóa không quay lại; mô hình chưa có phiên bản thì xóa cả schema, bảng và client `dxos-m-<mô hình>` của lần áp dụng đầu; nhóm đơn vị mà lần đó tạo vẫn phải dọn tay ở Keycloak vì các mô hình dùng chung nhóm |
| Đổi luật quy trình khi còn hồ sơ mở | Người xử lý, hạn xử lý, quyền tự quyết, hành động hoàn tất và đặt chỗ chỉ đổi được khi thực thể không còn hồ sơ đang xử lý, vì hồ sơ chưa lưu phiên bản mô hình; phải hoàn tất hoặc rút hết hồ sơ trước; chuyển hồ sơ đang chạy bằng `ProcessMigrationService` chưa làm |
| Lần áp dụng dở ghi trước migration `V6` | Không có nội dung kèm theo nên không dùng được Tiếp tục hay Hủy bỏ; áp dụng lại tệp |
| Vai bỏ khỏi tệp mô hình | Vẫn còn ở client của mô hình cùng người giữ nhưng không cho quyền gì, vì quyền chỉ theo vai mà mô hình đang khai báo; khai báo lại thì kế hoạch hỏi xác nhận; xóa hẳn vai hay client làm tay ở Keycloak |
| Xóa thực thể | Bảng, hồ sơ, tệp đính kèm và đối tượng trong kho bị xóa cùng; lịch sử Flowable đã kết thúc, audit và outbox của thực thể còn lại; thực thể còn hồ sơ đang chạy quy trình phải hoàn tất hoặc rút hết hồ sơ trước |
| Hệ thống đã áp dụng mô hình bằng bản cũ hơn | Bảng thiếu cột `handle_by` làm API dữ liệu của thực thể có quy trình trả 500 (danh sách, đọc, tạo, hộp việc) và bộ quét quá hạn lỗi mỗi phút; bảng thiếu chỉ mục `handle_by` vẫn chạy bình thường; hồ sơ đang xử lý trước đó không bị tính quá hạn cho đến lần chuyển trạng thái kế; vai của mô hình áp dụng từ trước khi có client riêng là vai realm nên người dùng không còn vào được; áp dụng lại cùng tệp báo `repair` và thêm cột, chỉ mục và client còn thiếu, nhưng người dùng vẫn phải được gán lại vai trong client mới; chưa có bản triển khai nào ở trạng thái này |
| Audit chỉ ghi thay đổi về tài khoản | Thay đổi vai, đơn vị và tài khoản của ADMIN vào `audit_log`; áp dụng, đổi và quay lui mô hình thành công chưa vào đó (lịch sử ở `apply_run` và `model_version`); nếu việc ghi audit lỗi thì chỉ có log và thay đổi vẫn thành công; thay đổi làm thẳng trong Admin Console chỉ có ở sự kiện quản trị của Keycloak, giữ 90 ngày, ghi tài khoản người thực hiện |
| Kiểm lệch của tệp không đổi | Chỉ chạy khi xem kế hoạch hoặc áp dụng, không chạy lúc backend khởi động; mỗi lần xem kế hoạch của tệp không đổi chạy thêm pgschema; bảng hoặc cột thừa do sửa tay bị từ chối chứ không bị xóa; vai thừa ở client của mô hình và nhóm đơn vị thừa không bị đụng; chỉ mục hay ràng buộc thừa bị xóa khi áp dụng với mức `MIGRATION` |
| Xem trước không giữ khóa | Kế hoạch có thể cũ khi người khác áp dụng xen vào; bước áp dụng kiểm lại mọi điều kiện |
| Quét mã độc | Tắt mặc định ở bản cài vì ClamAV cần khoảng 1 GB RAM; tắt thì tệp tải lên không được quét; quét nằm ở dịch vụ lưu tệp, sau gateway |
| Kiểm bản sao lưu | `verify` đếm bảng và dòng và kiểm mọi khóa của `engine.attachment` có tệp, nhưng không so nội dung từng tệp, quy trình đang chạy hay quyền sau khi khôi phục |
| Sao lưu và khoảng trễ xóa tệp | Bản sao lưu có đủ tệp của mọi dòng trong bản dump chỉ khi dump và chụp kho ngắn hơn `DXOS_FILE_DELETE_DELAY`; lượt dài hơn chỉ được cảnh báo; tệp đã gỡ vẫn nằm trong kho đến hết khoảng trễ |
| Tự phục hồi | `dxos reconcile` chỉ chạy một bản backend, không có khóa chung với `dxos update` và `backup.sh`, và timer cần phiên đăng nhập của user trừ khi bật `loginctl enable-linger` |
| Chuỗi hash của lịch sử | Phát hiện chứ không ngăn sửa; điểm neo nằm trong kho đối tượng của cùng máy chủ nên người sửa được cả cơ sở dữ liệu lẫn kho thì chỉ bị lộ khi so với `hash` mà bên nhận webhook đã giữ |
| Hồ dữ liệu | `dxos archive` chỉ xuất hồ sơ ở trạng thái cuối và lịch sử của chúng, dữ liệu trong cơ sở dữ liệu giữ nguyên; DuckDB tải extension `postgres` từ mạng ở lần chạy đầu |
| Thống kê | Có tổng số, theo trạng thái, quá hạn và theo đơn vị; chưa có thời gian chờ và chưa có tài liệu công thức, bộ lọc cho từng chỉ số; tài khoản chỉ đọc `report_ro` được `SELECT` cả bảng nên không tự áp dụng quyền `own`, `unit`, `assigned` của người xem |
| Bài kiểm của đợt sửa báo cáo kỹ thuật | Khe hở khi áp dụng, xóa client khi Hủy bỏ, thống kê, chuỗi hash, `dxos plan`, `dxos apply`, `dxos archive`, `verify` thiếu tệp và simulator IT/OT chỉ kiểm tay, chưa có script ở `deploy/dev` nên job `e2e` không chạy chúng |
| Webhook sự kiện | Giao ít nhất một lần, nên bên nhận bỏ lặp theo `X-Dxos-Event-Id`; payload chưa có `request_id`; dạng `chat` chưa thử với Mattermost và Slack thật; thông điệp của việc áp dụng mô hình và của tài khoản không có trong `GET /api/v1/events` |
| Ứng dụng Tầng 2 | Chưa phủ I của H-P-D-I; để sau như đã định |
| Hộp việc theo đơn vị | Người tạo hồ sơ rồi rời đơn vị vẫn thấy tiêu đề, mã và trạng thái hồ sơ của mình ở hộp việc, dù mở chi tiết bị 404 |
| Danh sách người xử lý | Tối đa 500 người cho mỗi vai hoặc đơn vị; người ngoài danh sách không giao việc được |
| Không có giao dịch chung giữa các hệ thống | Giữa PostgreSQL, Keycloak, Flowable và SeaweedFS; mỗi bước chạy lại được, và lần áp dụng dở được tiếp tục hoặc hủy bỏ |
