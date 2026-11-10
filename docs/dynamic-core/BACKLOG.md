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
| Tạo ADMIN khi cài mới | `deploy/dxos` đếm service account có vai ADMIN nên bỏ qua tạo tài khoản người dùng; bản cài sạch chỉ lên dịch vụ, chưa đăng nhập được. Đã có ở nhánh cha; lọc service account trước khi quyết định đã có ADMIN. Lệnh `dxos admin` là bước khôi phục hiện có |
| Tag `v2.0.0` và GitHub Release | Tạo từ `main`; ngày của mục `[2.0.0]` trong `CHANGELOG.md` sửa theo ngày tag; đẩy tag thì workflow `Release Package` tự tạo Release từ mục đó (không tạo Release bằng tay trước, vì workflow không ghi đè nội dung đã có); rồi chạy tay workflow `Release Images` trên tag để có image `v2.0.0` mà `dxos-install.sh` kéo về |
| Kiểm cài bằng image thật | `dxos-install.sh` và `dxos start <tệp>` mới kiểm bằng image local qua `install.sh`; sau khi workflow Release có image `v2.0.0`, chạy lại trên máy sạch |
| Triển khai công khai | VPS giữ `v1.1.1`; bản mới không có đường nâng cấp dữ liệu nên phải dựng lại; đặt `STAFF_OTP_FLOW=CONDITIONAL` trước lần khởi động đầu để bắt ADMIN nhập mã một lần, đặt `KEYCLOAK_HOSTNAME=${GATEWAY_PUBLIC_URL}` trong `.env` thay cho `KC_HOSTNAME` ở file override (đặt `KC_HOSTNAME` một mình thì gateway từ chối token), và bật quét mã độc (`dxos av on`) |
| Ruleset của `main` | Nhập `.github/rulesets/main.json` và `tags.json` ở Settings → Rules sau khi các check đã chạy một lần trên pull request, vì tên check phải đã xuất hiện |
| Gửi bài | Thư tới BTC trước 17:00 ngày 06/12/2026 |

## 3. Giới hạn đã biết

| Việc | Ghi chú |
|---|---|
| Dọn tài liệu và thành phần AI cũ | Đối chiếu checkout cũ với Core hiện hành trước khi loại bỏ `COPYRIGHT.txt`, mã Python, dependency và SBOM không còn dùng; xử lý riêng, ngoài phạm vi tích hợp trợ lý |
| Truy vấn phiên bản metadata | Giữ cách đọc hiện tại; khoảng 9 ms cho 54 truy vấn trên 23 mô hình/31 thực thể; cache chỉ làm khi có nhu cầu và đã kiểm apply/rollback/đồng thời |
| Phần AI | hội thoại chưa tách theo mô hình; câu sinh đôi lúc có mã kỹ thuật hoặc thời gian chưa đúng múi giờ; chưa chọn model tự triển khai/embedding đáp ứng định nghĩa Open Source AI của OSI |
| Thử tải ba trợ lý đồng thời | Hoãn theo chủ dự án; đợt hiện tại nghiệm thu hai trợ lý. Cần bot và tài nguyên phù hợp trước khi đo cold/warm, độ trễ, RAM/swap và ảnh hưởng Core |
| Nghiệm thu provider API thứ hai | Chờ key OpenAI hoặc Anthropic; kiểm model thật với cùng luồng đọc, đề xuất, xác nhận và lỗi/quota trước khi công bố hỗ trợ đã xác minh |
| Egress trợ lý | Runtime chưa có allowlist toàn mạng; endpoint compatible đang tắt; cần triển khai và kiểm chứng giới hạn kết nối toàn container |
| Khóa/token backup trợ lý | Key và capability nằm cùng kho restic; người giải mã backup có thể đọc secret/giả danh runtime; cần tách quyền giữ khóa/token và có cách thu hồi, thay mới |
| Ứng dụng Tầng 2 | Chưa phủ I của H-P-D-I; để sau như đã định |
| Giới hạn của kiểu và thao tác mới | Đổi vai của một ứng dụng ngoài bằng cách gỡ rồi đăng ký lại; hành động hàng loạt không có hoàn tác và tối đa 50 hồ sơ mỗi lần; trường nhiều lựa chọn không có giá trị mặc định; trường ngày chưa làm nguồn của điều kiện `when` và `required_when` |

## 4. Giới hạn chấp nhận

Đã cân nhắc và quyết định giữ nguyên; chỉ mở lại khi có yêu cầu thật.

| Việc | Ghi chú |
|---|---|
| Tìm người cho trường `user` | `GET /api/v1/people` cho mọi người đã đăng nhập tìm cả người ngoài phạm vi mô hình, khớp theo tên, tên đăng nhập hoặc email, và chỉ trả id với tên hiển thị; thu hẹp theo vai của mô hình thì phải đối chiếu vai của từng người |
| Đổi mô hình khi còn hồ sơ mở | Hồ sơ chưa lưu phiên bản mô hình và hồ sơ đang chạy giữ bản BPMN cũ (Flowable không chuyển chúng sang bản mới); hạn xử lý, quyền tự quyết, hành động hoàn tất, đặt chỗ, trạng thái, hành động và điều kiện `when` vì thế chỉ đổi được khi thực thể không còn hồ sơ đang xử lý, phải hoàn tất hoặc rút hết hồ sơ trước; đổi vai xử lý thì có hiệu lực ngay vì quyền xét theo mô hình, hồ sơ đã giao cho người không còn giữ vai mới chỉ người điều phối giao lại được, và nhóm ứng viên ghi trong task Flowable đang chạy vẫn là vai cũ (không nơi nào đọc nó) |
| Ngôn ngữ giao diện | Chữ do mô hình định nghĩa (tên thực thể, nhãn trường, nhãn trạng thái và hành động tự khai, nội dung tệp mẫu) không được dịch |
| Cắt bớt service bằng compose profile | Mô hình không dùng thành phần nào vẫn phải chạy thành phần đó |
| Nhiều bản sao của backend | Chạy một bản; hai bản sau gateway thì danh sách mô hình và trạng thái giữ lệch nhau giữa các bản sau một lần áp dụng |
| Điều kiện `when` | Một phép so sánh trên một trường, không có và/hoặc hay biểu thức; chỉ cho hành động của người xử lý, không cho hành động của người nộp, `withdraw` hay `auto_close`; người nộp sửa được trường đó khi hồ sơ còn mở nên có thể đổi đường đi trước khi người duyệt bấm, dòng lịch sử `updated` giữ giá trị cũ và mới |
| Đổi họ mẫu của một thực thể | Đổi khóa `family` sang họ khác bị từ chối, vì trạng thái của hồ sơ cũ không ánh xạ sang họ mới; muốn đổi hình dạng quy trình thì sửa `states` và `actions` của quy trình hiện có, hoặc thêm thực thể mới |
| Đổi vai hoặc đơn vị của người đang đăng nhập | Vai và đơn vị đọc từ token đã ký nên thay đổi có hiệu lực sau tối đa khoảng 5 phút (token sống 300 giây), và người bị gỡ giữ quyền đến lúc đó |
| Chuỗi hash của lịch sử | Phát hiện chứ không ngăn sửa; điểm neo nằm trong kho đối tượng của cùng máy chủ nên người sửa được cả cơ sở dữ liệu lẫn kho thì chỉ bị lộ khi so với `hash` mà bên nhận webhook đã giữ |
| Giá trị trong lịch sử | Dòng lịch sử `updated` giữ giá trị cũ và mới của từng trường; hồ sơ không có quy trình bị xóa thì các dòng này vẫn còn, vì chuỗi hash không cho sửa dòng đã ghi, và chỉ người đọc được hồ sơ xem được chúng khi hồ sơ còn |
| Nhập dữ liệu hàng loạt | Không có nhập CSV và gói mô hình kèm dữ liệu danh mục; dữ liệu vào qua API chung để đi qua quyền, ràng buộc và quy trình |
| Tìm kiếm | Không tìm hồ sơ và nội dung tệp; chỉ mục và đoạn trích có thể lộ dữ liệu ngoài quyền, và tìm tiếng Việt cần đo riêng |
| Đăng ký thông báo | Không đăng ký theo loại sự kiện và người nhận; thông báo chat qua webhook cùng các dòng `due_soon` và `overdue` là mức cung cấp |
| Hành động hoàn tất gọi ra ngoài | Chỉ có `add_to_group` và `remove_from_group`; không có thao tác gọi tới một đích của webhook, vì webhook của mô hình đã gửi mọi dòng lịch sử kèm trạng thái cũ, trạng thái mới và hành động, nên bên nhận lọc theo trạng thái được |
| Webhook sự kiện | Dạng `chat` chưa thử với Mattermost và Slack thật |
| Kiểm tương thích khi đổi mô hình | Chưa có nơi đăng ký mã mở rộng nên chưa có hợp đồng để đối chiếu |
| Người thứ hai duyệt thay đổi mô hình | Không bắt buộc; là chính sách tùy bản triển khai |
| Ủy quyền có phạm vi và thời hạn | Không có; chạm nhiều đường kiểm quyền |
| Quyền theo từng trường | Không có; phải phủ mọi đường dữ liệu đi ra: danh sách, bộ lọc, số đếm, lịch sử và webhook |
| Kiểm tài liệu và OpenAPI chiều ngược | `check_docs.py` kiểm endpoint tài liệu nêu có trong OpenAPI; không kiểm chiều ngược vì `docs/API.md` chỉ liệt kê các đường dẫn chính và viết tắt đường dẫn, nên sẽ báo nhầm |
| Kiểm thử tự động chưa phủ một số ý | Chưa phủ khe hở khi áp dụng (áp dụng một mô hình đổi bảng trong lúc có yêu cầu ghi hồ sơ), vì kiểm chạy đua dễ lúc đỏ lúc xanh; ý này kiểm tay theo mục 6 của `docs/TESTING.md`; ứng dụng ngoài, kiểu và ràng buộc mới, `required_when`, `ui:`, tài liệu OpenAPI theo mô hình, giới hạn luồng thông báo, khóa chống chạy lặp, bảng theo trạng thái và xuất CSV cũng chỉ kiểm tay theo mục đó, chưa có script trong CI |
