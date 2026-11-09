# Việc tồn đọng của lõi dynamic

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của lõi dynamic.
Hướng đi nằm ở [`README.md`](./README.md).
Làm xong một mục thì xóa dòng đó, không ghi lại diễn biến.

## 1. Thứ tự đến hạn nộp

Hạn gửi bài là 17:00 ngày 06/12/2026.
Mốc dưới đây là dự kiến.

| Mốc | Nội dung | Dự kiến xong |
|---|---|---|
| Phát hành 2.0.0 | Tag `v2.0.0` và GitHub Release, triển khai công khai, trang docs, gửi bài | 06/12 |

## 2. Phải xử lý trước khi phát hành

| Việc | Ghi chú |
|---|---|
| Tag `v2.0.0` và GitHub Release | Tạo từ `main`; ngày của mục `[2.0.0]` trong `CHANGELOG.md` sửa theo ngày tag; đẩy tag thì workflow `Release Package` tự tạo Release từ mục đó (không tạo Release bằng tay trước, vì workflow không ghi đè nội dung đã có); rồi chạy tay workflow `Release Images` trên tag để có image `v2.0.0` mà `dxos-install.sh` kéo về |
| Kiểm cài bằng image thật | `dxos-install.sh` và `dxos start <tệp>` mới kiểm bằng image local qua `install.sh`; sau khi workflow Release có image `v2.0.0`, chạy lại trên máy sạch |
| Triển khai công khai | VPS giữ `v1.1.1`; bản mới không có đường nâng cấp dữ liệu nên phải dựng lại; đặt `STAFF_OTP_FLOW=CONDITIONAL` trước lần khởi động đầu để bắt ADMIN nhập mã một lần, đặt `KEYCLOAK_HOSTNAME=${GATEWAY_PUBLIC_URL}` trong `.env` thay cho `KC_HOSTNAME` ở file override (đặt `KC_HOSTNAME` một mình thì gateway từ chối token), và bật quét mã độc (`dxos av on`) |
| Ruleset của `main` | Nhập `.github/rulesets/main.json` và `tags.json` ở Settings → Rules sau khi các check đã chạy một lần trên pull request, vì tên check phải đã xuất hiện |
| Gửi bài | Thư tới BTC trước 17:00 ngày 06/12/2026 |

## 3. Giới hạn đã biết

| Việc | Ghi chú |
|---|---|
| Phần AI | Chưa có mã: hỏi đáp sinh câu trả lời và tác tử đề xuất có người duyệt cần mô hình ngôn ngữ và embedding đạt định nghĩa Open Source AI của OSI (trọng số và dữ liệu huấn luyện đều mở), chưa chọn; khi làm sẽ là dịch vụ mới nối vào lõi |
| Ứng dụng Tầng 2 | Chưa phủ I của H-P-D-I; để sau như đã định |

## 4. Giới hạn chấp nhận

Đã cân nhắc và quyết định giữ nguyên; chỉ mở lại khi có yêu cầu thật.

| Việc | Ghi chú |
|---|---|
| Trường kiểu `user` | Giá trị là id người dùng Keycloak, giao diện web cho nhập tay và backend không đối chiếu với người dùng có thật |
| Đổi luật quy trình khi còn hồ sơ mở | Hạn xử lý, quyền tự quyết, hành động hoàn tất, đặt chỗ, trạng thái, hành động và điều kiện `when` chỉ đổi được khi thực thể không còn hồ sơ đang xử lý; phải hoàn tất hoặc rút hết hồ sơ trước, vì hồ sơ chưa lưu phiên bản mô hình và hồ sơ đang chạy giữ bản BPMN cũ (Flowable không chuyển chúng sang bản mới) |
| Đổi vai xử lý khi còn hồ sơ mở | Có hiệu lực ngay vì quyền xét theo mô hình; hồ sơ đã giao cho người không còn giữ vai mới thì chỉ người điều phối giao lại được, và nhóm ứng viên ghi trong task Flowable đang chạy vẫn là vai cũ (không nơi nào đọc nó) |
| Ngôn ngữ giao diện | Chỉ có tiếng Việt |
| Cắt bớt service bằng compose profile | Mô hình không dùng thành phần nào vẫn phải chạy thành phần đó |
| Nhiều bản sao của backend | Chạy một bản; hai bản sau gateway thì danh sách mô hình và trạng thái giữ lệch nhau giữa các bản sau một lần áp dụng |
| Điều kiện `when` | Một phép so sánh trên một trường, không có và/hoặc hay biểu thức; chỉ cho hành động của người xử lý, không cho hành động của người nộp, `withdraw` hay `auto_close`; người nộp sửa được trường đó khi hồ sơ còn mở nên có thể đổi đường đi trước khi người duyệt bấm, dòng lịch sử `updated` giữ giá trị cũ và mới |
| Đổi họ mẫu của một thực thể | Đổi khóa `family` sang họ khác bị từ chối, vì trạng thái của hồ sơ cũ không ánh xạ sang họ mới; muốn đổi hình dạng quy trình thì sửa `states` và `actions` của quy trình hiện có, hoặc thêm thực thể mới |
| Đổi vai hoặc đơn vị của người đang đăng nhập | Vai và đơn vị đọc từ token đã ký nên thay đổi có hiệu lực sau tối đa khoảng 5 phút (token sống 300 giây), và người bị gỡ giữ quyền đến lúc đó |
| Nạp mô hình lúc khởi động | Chỉ một tệp mỗi lần |
| Chuỗi hash của lịch sử | Phát hiện chứ không ngăn sửa; điểm neo nằm trong kho đối tượng của cùng máy chủ nên người sửa được cả cơ sở dữ liệu lẫn kho thì chỉ bị lộ khi so với `hash` mà bên nhận webhook đã giữ |
| Giá trị trong lịch sử | Dòng lịch sử `updated` giữ giá trị cũ và mới của từng trường; hồ sơ không có quy trình bị xóa thì các dòng này vẫn còn, vì chuỗi hash không cho sửa dòng đã ghi, và chỉ người đọc được hồ sơ xem được chúng khi hồ sơ còn |
| Nhập dữ liệu hàng loạt | Không có nhập CSV và gói mô hình kèm dữ liệu danh mục; dữ liệu vào qua API chung để đi qua quyền, ràng buộc và quy trình |
| Tìm kiếm | Không tìm hồ sơ và nội dung tệp; chỉ mục và đoạn trích có thể lộ dữ liệu ngoài quyền, và tìm tiếng Việt cần đo riêng |
| Đăng ký thông báo | Không đăng ký theo loại sự kiện và người nhận; thông báo chat qua webhook cùng các dòng `due_soon` và `overdue` là mức cung cấp |
| Webhook sự kiện | Dạng `chat` chưa thử với Mattermost và Slack thật; thông điệp của việc áp dụng mô hình và của tài khoản không có trong `GET /api/v1/events`, hai loại này xem ở nhật ký audit |
| Kiểm tương thích khi đổi mô hình | Chưa có nơi đăng ký mã mở rộng nên chưa có hợp đồng để đối chiếu |
| Người thứ hai duyệt thay đổi mô hình | Không bắt buộc; là chính sách tùy bản triển khai |
| Ủy quyền có phạm vi và thời hạn | Không có; chạm nhiều đường kiểm quyền |
| Quyền theo từng trường | Không có; phải phủ mọi đường dữ liệu đi ra: danh sách, bộ lọc, số đếm, lịch sử và webhook |
| Góc nhìn công việc đã lưu | Không lưu bộ lọc, cột và sắp xếp |
| Mã lý do ở giao diện | Giao diện dịch lỗi theo bảng thông điệp, không theo `data.reason`; mã này dành cho client ngoài |
| Kiểm tài liệu và OpenAPI chiều ngược | `check_docs.py` kiểm endpoint tài liệu nêu có trong OpenAPI; không kiểm chiều ngược vì `docs/API.md` chỉ liệt kê các đường dẫn chính và viết tắt đường dẫn, nên sẽ báo nhầm |
| Danh sách người xử lý | Tối đa 500 người cho mỗi vai hoặc đơn vị; người ngoài danh sách không giao việc được |
| Kiểm thử tự động chưa phủ một số ý | Chưa phủ `dxos plan`, `dxos apply`, `dxos archive`, simulator IT/OT, khe hở khi áp dụng và `verify` thiếu tệp; các ý này kiểm tay theo mục 6 của `docs/TESTING.md` |
