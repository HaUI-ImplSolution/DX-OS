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
| Dịch vụ `ai/` | Hoãn đến phần AI: chưa nằm trong compose và chưa nối vào lõi, vì không có mô hình, backend hay gateway nào gọi tới; chỉ tìm kiếm tri thức có nguồn, chưa có hỏi đáp sinh câu trả lời và tác tử đề xuất có người duyệt vì chưa có mô hình ngôn ngữ đạt tiêu chuẩn mở; dữ liệu huấn luyện của mô hình embedding chưa kiểm theo định nghĩa của OSI |
| Webhook sự kiện | Dạng `chat` chưa thử với Mattermost và Slack thật; thông điệp của việc áp dụng mô hình và của tài khoản không có trong `GET /api/v1/events` |
| Ứng dụng Tầng 2 | Chưa phủ I của H-P-D-I; để sau như đã định |

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
| Danh sách người xử lý | Tối đa 500 người cho mỗi vai hoặc đơn vị; người ngoài danh sách không giao việc được |
| Kiểm thử tự động chưa phủ một số ý | Chưa phủ `dxos plan`, `dxos apply`, `dxos archive`, simulator IT/OT, khe hở khi áp dụng và `verify` thiếu tệp; các ý này kiểm tay theo mục "Kiểm thử chưa tự động hóa" ở `docs/TESTING.md` |
