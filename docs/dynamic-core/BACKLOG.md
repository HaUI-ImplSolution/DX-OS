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
| Kết quả kiểm trong `docs/TESTING.md` | Còn ghi trên cây làm việc gồm tệp chưa commit; ghi lại trên bản gắn tag |
| Tag `v2.0.0` và GitHub Release | Tạo sau khi nhánh phase 10 vào `main`; ngày của mục `[2.0.0]` trong `CHANGELOG.md` sửa theo ngày tag; chạy workflow Release từ tag để có image `v2.0.0` mà `dxos-install.sh` kéo về |
| Kiểm cài bằng image thật | `dxos-install.sh` và `dxos start <tệp>` mới kiểm bằng image local qua `install.sh`; sau khi workflow Release có image `v2.0.0`, chạy lại trên máy sạch |
| Trang docs `DX-OS-docs` | Vẫn mô tả bản `v1.1.1`; cập nhật cùng lúc triển khai |
| Triển khai công khai | VPS giữ `v1.1.1`; `deploy/VPS.md` còn mô tả bản đó, và bản mới không có đường nâng cấp dữ liệu nên phải dựng lại; đặt `STAFF_OTP_FLOW=CONDITIONAL` trước lần khởi động đầu để bắt ADMIN nhập mã một lần, và bật quét mã độc (`dxos av on`) |
| Gửi bài | Thư tới BTC trước 17:00 ngày 06/12/2026 |

## 3. Giới hạn đã biết

| Việc | Ghi chú |
|---|---|
| Phần AI | Chưa có mã: hỏi đáp sinh câu trả lời và tác tử đề xuất có người duyệt cần mô hình ngôn ngữ và embedding đạt định nghĩa Open Source AI của OSI (trọng số và dữ liệu huấn luyện đều mở), chưa chọn; khi làm sẽ là dịch vụ mới nối vào lõi |
| Webhook sự kiện | Dạng `chat` chưa thử với Mattermost và Slack thật; thông điệp của việc áp dụng mô hình và của tài khoản không có trong `GET /api/v1/events` |
| Ứng dụng Tầng 2 | Chưa phủ I của H-P-D-I; để sau như đã định |
| Duyệt nhiều cấp tuần tự | Họ phê duyệt chỉ có một cấp xử lý; nhiều cấp cần trạng thái và hành động sinh theo số cấp khai báo trong mô hình, nên đổi `dx-model`, kernel quy trình, `data` và giao diện, và đụng các mô hình đang chạy; chưa làm vì rủi ro cao |

## 4. Giới hạn chấp nhận

Đã cân nhắc và quyết định giữ nguyên; chỉ mở lại khi có yêu cầu thật.

| Việc | Ghi chú |
|---|---|
| Trường kiểu `user` | Giá trị là id người dùng Keycloak, giao diện web cho nhập tay và backend không đối chiếu với người dùng có thật |
| Đổi luật quy trình khi còn hồ sơ mở | Người xử lý, hạn xử lý, quyền tự quyết, hành động hoàn tất và đặt chỗ chỉ đổi được khi thực thể không còn hồ sơ đang xử lý; phải hoàn tất hoặc rút hết hồ sơ trước, vì hồ sơ chưa lưu phiên bản mô hình |
| Ngôn ngữ giao diện | Chỉ có tiếng Việt |
| Cắt bớt service bằng compose profile | Mô hình không dùng thành phần nào vẫn phải chạy thành phần đó |
| Nhiều bản sao của backend | Chạy một bản; hai bản sau gateway thì danh sách mô hình và trạng thái giữ lệch nhau giữa các bản sau một lần áp dụng |
| Đổi họ mẫu của một thực thể | Bị từ chối, kể cả khi mọi hồ sơ đã ở trạng thái cuối, vì trạng thái của hồ sơ cũ không ánh xạ sang họ mới; muốn đổi hình dạng quy trình thì thêm thực thể mới |
| Đổi vai hoặc đơn vị của người đang đăng nhập | Vai và đơn vị đọc từ token đã ký nên thay đổi có hiệu lực sau tối đa khoảng 5 phút (token sống 300 giây), và người bị gỡ giữ quyền đến lúc đó |
| Nạp mô hình lúc khởi động | Chỉ một tệp mỗi lần |
| Chuỗi hash của lịch sử | Phát hiện chứ không ngăn sửa; điểm neo nằm trong kho đối tượng của cùng máy chủ nên người sửa được cả cơ sở dữ liệu lẫn kho thì chỉ bị lộ khi so với `hash` mà bên nhận webhook đã giữ |
| Giá trị trong lịch sử | Dòng lịch sử `updated` giữ giá trị cũ và mới của từng trường; hồ sơ không có quy trình bị xóa thì các dòng này vẫn còn, vì chuỗi hash không cho sửa dòng đã ghi, và chỉ người đọc được hồ sơ xem được chúng khi hồ sơ còn |
| Nhập dữ liệu hàng loạt | Không có nhập CSV và gói mô hình kèm dữ liệu danh mục; dữ liệu vào qua API chung để đi qua quyền, ràng buộc và quy trình |
| Tìm kiếm | Không tìm hồ sơ và nội dung tệp; chỉ mục và đoạn trích có thể lộ dữ liệu ngoài quyền, và tìm tiếng Việt cần đo riêng |
| Đăng ký thông báo | Không đăng ký theo loại sự kiện và người nhận; thông báo chat qua webhook cùng các dòng `due_soon` và `overdue` là mức cung cấp |
| Kiểm tương thích khi đổi mô hình | Chưa có nơi đăng ký mã mở rộng nên chưa có hợp đồng để đối chiếu |
| Người thứ hai duyệt thay đổi mô hình | Không bắt buộc; là chính sách tùy bản triển khai |
| Ủy quyền có phạm vi và thời hạn | Không có; chạm nhiều đường kiểm quyền |
| Quyền theo từng trường | Không có; phải phủ mọi đường dữ liệu đi ra: danh sách, bộ lọc, số đếm, lịch sử và webhook |
| Chỉ số thống kê nâng cao | Không có tỷ lệ hoàn tất trong hạn vì `handle_by` bị xóa khi hồ sơ vào trạng thái cuối; không có dataset Superset cho chỉ số vì vai `report_ro` không nhận quyền `own`, `unit`, `assigned` |
| Góc nhìn công việc đã lưu | Không lưu bộ lọc, cột và sắp xếp |
| Mã lý do ở giao diện | Giao diện dịch lỗi theo bảng thông điệp, không theo `data.reason`; mã này dành cho client ngoài |
| Kiểm tài liệu và OpenAPI chiều ngược | `check_docs.py` kiểm endpoint tài liệu nêu có trong OpenAPI; không kiểm chiều ngược vì README viết tắt đường dẫn và sẽ báo nhầm |
| Danh sách người xử lý | Tối đa 500 người cho mỗi vai hoặc đơn vị; người ngoài danh sách không giao việc được |
| Kiểm thử tự động chưa phủ một số ý | Chưa phủ `dxos plan`, `dxos apply`, `dxos archive`, simulator IT/OT, khe hở khi áp dụng và `verify` thiếu tệp; các ý này kiểm tay theo mục "Kiểm thử chưa tự động hóa" ở `docs/TESTING.md` |
