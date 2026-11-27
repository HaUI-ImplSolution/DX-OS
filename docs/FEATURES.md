# Tính năng

Danh sách đầy đủ những gì DX-OS làm được, viết bằng lời thường.
Mỗi tính năng đối chiếu với bài kiểm của nó ở [ma trận bằng chứng](https://haui-implsolution.github.io/DX-OS/docs/evidence-matrix); giới hạn đã biết ở [`BACKLOG.md`](./dynamic-core/BACKLOG.md).

## Khai báo mô hình

- **Từ một tệp đến hệ thống chạy được.**
  Hồ sơ, bảng dữ liệu, tài khoản, vai, màn hình danh sách và biểu mẫu đều dựng từ tệp mô hình.
  Tệp viết theo kiểu cũ vẫn chạy được, và có lệnh đưa lên kiểu mới sau khi xem trước những gì sẽ đổi.
- **Mã hồ sơ dễ đọc.**
  Tệp khai báo khuôn mã như tiền tố, năm và số thứ tự.
  Mỗi hồ sơ nhận một mã không trùng, kể cả khi nhiều người nộp cùng lúc.
- **Quy tắc dữ liệu ngay trong tệp.**
  Đúng/sai, ngày, chọn nhiều giá trị, giới hạn giá trị, mẫu chuỗi và điều kiện bắt buộc đều khai báo trong tệp.
  Cơ sở dữ liệu cũng từ chối giá trị sai.
- **Quy trình tùy biến.**
  Chọn một trong ba mẫu có sẵn: phê duyệt, công việc cần xử lý, đặt chỗ theo khung giờ có kiểm sức chứa.
  Thêm bớt bước và giao cho vai riêng, hoặc tự khai báo từ đầu.
  Một bước có thể chỉ mở khi hồ sơ đạt điều kiện; từ chối và trả lại đều cần lý do; không ai tự duyệt hồ sơ của mình.
- **Đổi nghiệp vụ khi đã có dữ liệu.**
  Xem trước tác động, cảnh báo rủi ro, giữ nguyên dữ liệu khi đổi tên, và quay về được phiên bản trước.

## Quyền và dấu vết

- **Phân quyền đến từng dòng.**
  Mỗi vai chỉ thấy và sửa phần việc của mình, của đơn vị mình hoặc được giao cho mình.
- **Dấu vết không sửa được.**
  Lịch sử hồ sơ nối thành một chuỗi có dấu kiểm.
  Ai sửa, xóa hoặc chèn một dòng thì một lệnh chỉ ra đúng dòng đó.
- **Danh mục dữ liệu.**
  Một trang cho biết dữ liệu của mỗi mô hình nằm ở đâu, ai được xem và các loại hồ sơ liên quan nhau thế nào.
  Trang cũng chỉ ra dữ liệu đi vào và đi ra đâu đến từng trường, và dữ liệu hiện có đầy đủ và còn mới đến đâu.
- **Lưu trữ hồ sơ cũ.**
  Hồ sơ đã đóng lâu được ghi sang kho lưu trữ theo lịch mỗi ngày.
  Dữ liệu đang dùng vẫn giữ nguyên, bản lưu kiểm lại được và truy vấn trực tiếp bằng DuckDB.

## Mở rộng và kết nối

- **Kết nối ra ngoài.**
  Mọi thay đổi của hồ sơ được báo tới địa chỉ web hoặc kênh chat (Mattermost, Slack).
  Thông báo có chữ ký để bên nhận kiểm tra, và được gửi lại khi bên nhận lỗi.
- **Mở cho ứng dụng bên ngoài.**
  Mỗi mô hình có tài liệu API riêng, tự đổi theo mô hình.
  Dịch vụ ngoài đăng ký với đúng một vai và mã bí mật riêng.
  Giao diện ở tên miền khác đăng nhập người dùng theo chuẩn OIDC (luồng Authorization Code với PKCE).
- **Bảng điều khiển cho người triển khai.**
  Thư viện mẫu, trình soạn mô hình, xem kế hoạch, áp dụng, lịch sử, quản lý người dùng và thống kê hồ sơ.
  Trình soạn cho viết tệp trực tiếp hoặc dựng bằng form có cây cấu trúc, bảng quyền và sơ đồ quy trình bấm vào để sửa.
  Có hướng dẫn nói rõ viết được và mở rộng được gì, và sơ đồ quy trình vẽ theo tệp đang soạn.
- **Nhập hồ sơ từ bảng tính.**
  Một lệnh đưa các dòng của tệp CSV vào thành hồ sơ.
  Mỗi dòng đi qua đủ quyền, quy tắc và quy trình như hồ sơ nhập tay; dòng sai được báo kèm lý do.
  Gửi lại cùng tệp không tạo hồ sơ trùng.
- **Nhận số đo thiết bị qua MQTT (tùy chọn).**
  Thêm một broker và một cầu nối nhỏ, bản tin của thiết bị thành hồ sơ qua đúng quyền và quy trình của lõi.
  Bản tin gửi hai lần chỉ tạo một hồ sơ, và lõi ngừng thì bản tin được giữ lại.
- **Thu số đo có sẵn (tùy chọn).**
  Bật thêm Prometheus đã nối sẵn với số đo của lõi về hồ sơ, hạn xử lý, hàng đợi sự kiện và lần áp dụng mô hình.
- **Gom theo dự án.**
  Nhiều hệ thống tích hợp dùng chung một bản cài thì mô hình và ứng dụng ngoài được gom theo dự án để lọc và xem một thay đổi chạm tới ai.
  Dự án chỉ là tên gọi để quản trị, không cấp quyền và không tách dữ liệu.
- **Quản trị theo dự án.**
  Mỗi dự án có danh sách thành viên và quản trị viên.
  Quản trị viên của dự án tự gán vai của các mô hình trong dự án cho thành viên của mình mà không cần quyền quản trị cả hệ thống.
  Đây là phân chia việc quản trị, không phải tách dữ liệu giữa các tổ chức.
- **In giấy tờ từ hồ sơ.**
  Tệp mô hình khai báo mẫu giấy xác nhận, phiếu hay biên bản.
  Hồ sơ thành tệp PDF tiếng Việt cho đúng người được xem hồ sơ đó, chỉ in được ở những trạng thái mẫu cho phép.

## Dùng và vận hành

- **Giao diện theo khai báo.**
  Tệp mô hình chọn được biểu tượng, nhóm menu, cột và sắp xếp mặc định, cách chia nhóm biểu mẫu và lời gợi ý.
  Cán bộ chọn nhiều hồ sơ rồi xử lý cùng lúc, mỗi hồ sơ vẫn qua đủ luật.
- **Xem theo trạng thái và xuất danh sách.**
  Danh sách hồ sơ có quy trình xem được như một bảng theo từng trạng thái.
  Danh sách xuất ra tệp CSV mà bảng tính đọc được, đúng phần người xem được quyền thấy.
- **Tìm hồ sơ theo chữ.**
  Gõ vài từ vào ô tìm của danh sách, không cần gõ dấu và không phân biệt hoa thường.
  Chỉ hiện hồ sơ mà người xem được quyền thấy.
- **Hộp việc tự cập nhật.**
  Khi hồ sơ đổi, hộp việc, danh sách và trang chi tiết đang mở tự làm mới, không cần tải lại trang.
- **Dùng thử ngay.**
  Một lệnh tạo tài khoản thử cho từng vai cùng hồ sơ mẫu ở nhiều trạng thái, để người xem đăng nhập là thấy hệ thống có việc đang chạy.
- **Cài và vận hành đơn giản.**
  Một lệnh cài, một lệnh chạy.
  Có sao lưu kiểm được, tự khởi động lại dịch vụ, quét mã độc cho tệp đính kèm và số đo vận hành đọc được bằng công cụ giám sát có sẵn của bạn.
- **Đăng nhập một lần.**
  Qua Keycloak, có thể bắt quản trị viên nhập thêm mã một lần.
- **[Trợ lý nghiệp vụ](../assistant/README.md).**
  Mô hình AI do đội tự huấn luyện, chạy cùng bản cài trên máy chủ của bạn.
  Nó đọc nội dung hồ sơ để gợi ý cách điền kèm câu chữ làm căn cứ và chỉ ra hồ sơ nên xem lại.
  Người dùng xác nhận trước khi lưu.

## Mười mẫu có sẵn

Chọn được ngay ở bảng điều khiển: một cửa sinh viên, hỗ trợ kỹ thuật, sự cố thiết bị, đặt phòng, cấp quyền dịch vụ CNTT, mượn thiết bị, ghi chú, sổ tài sản, đặt lịch khám và đổi trả hàng.
