# Tài liệu

Trang này giúp xem nhanh DX-OS trong 5 phút và biết tìm từng phần ở đâu.
Giới thiệu đầy đủ ở [`README.md`](../README.md).

## Xem nhanh trong 5 phút

Không cần cài gì:

1. **Hệ thống là gì (1 phút):** [`README.md`](../README.md), mục Giới thiệu và sơ đồ ở mục Kiến trúc; từ nào lạ thì tra ở [`GLOSSARY.md`](./GLOSSARY.md).
2. **Dùng thử (2 phút):** <https://khuongdev.io.vn/>.
   Tài khoản thử và mật khẩu ở [`README.md`](../README.md#chạy-thử), mục Chạy thử.
   Bấm gì ở từng bước, theo vai: [người nộp](https://haui-implsolution.github.io/DX-OS/docs/huong-dan/nguoi-nop), [người xử lý](https://haui-implsolution.github.io/DX-OS/docs/huong-dan/nguoi-xu-ly) và [người triển khai](https://haui-implsolution.github.io/DX-OS/docs/huong-dan/nguoi-trien-khai).
3. **Đổi nghiệp vụ bằng tệp (1 phút):** hai đoạn video ở [trang hình ảnh](https://haui-implsolution.github.io/DX-OS/docs/hinh-anh): một hồ sơ đi từ nộp đến duyệt, và một lần sửa tệp mô hình rồi áp dụng mà dữ liệu cũ vẫn còn.
4. **Mức hoàn thiện (30 giây):** [ma trận bằng chứng](https://haui-implsolution.github.io/DX-OS/docs/evidence-matrix) cho từng tính năng: cách kiểm, bước CI, lệnh chạy lại và giới hạn đã biết.
5. **Kiểm tự động (30 giây):** tab [Actions](https://github.com/HaUI-ImplSolution/DX-OS/actions), workflow `Platform` và `Compliance`.

Tự cài thì dùng hai lệnh ở [`README.md`](../README.md#cài-đặt), mục Cài đặt; hướng dẫn kiểm tay ở [`TESTING.md`](./TESTING.md).

## Tài liệu trong thư mục này

| Tệp | Nội dung |
|---|---|
| [`MODEL.md`](./MODEL.md) | Cách viết tệp mô hình: trường, ràng buộc, quyền, quy trình, tài liệu in |
| [`API.md`](./API.md) | Bảng đường dẫn của API chung; đặc tả đầy đủ là OpenAPI của backend đang chạy |
| [`EXTENSIONS.md`](./EXTENSIONS.md) | Viết dịch vụ riêng, bên nhận webhook, giao diện ngoài và route riêng ở ngoài lõi |
| [`TESTING.md`](./TESTING.md) | Các loại kiểm, cách dựng stack, các script kiểm API và danh sách kiểm tay trước khi phát hành |
| [`DEMO.md`](./DEMO.md) | Kịch bản trình diễn 15 phút và cách xử lý khi sự cố |
| [`SIZING.md`](./SIZING.md) | Số người dùng đồng thời và cấu hình nhỏ nhất, kèm cách đo lại |
| [`TARGETS.md`](./TARGETS.md) | Kiểm chứng các chỉ số mục tiêu: mã tự viết, thời gian dựng, chi phí tự cài |
| [`COMPATIBILITY.md`](./COMPATIBILITY.md) | Những gì giữ nguyên giữa các bản: API, số đo, tệp mô hình, webhook, cơ sở dữ liệu |
| [`GLOSSARY.md`](./GLOSSARY.md) | Thuật ngữ dùng trong tài liệu và giao diện |
| [`dynamic-core/`](./dynamic-core/README.md) | Hướng thiết kế của lõi, [đối chiếu với đề thi](./dynamic-core/DANH_GIA.md) và [việc còn lại](./dynamic-core/BACKLOG.md) |
| [`diagrams/`](./diagrams) | Nguồn draw.io của sơ đồ kiến trúc |

Kiến trúc ở [`ARCHITECTURE.md`](../ARCHITECTURE.md), cài đặt và vận hành ở [`docs/OPERATIONS.md`](OPERATIONS.md), và mục lục đầy đủ ở [`README.md`](../README.md#tài-liệu), mục Tài liệu.
Các tài liệu sản phẩm cũng đọc được trên [trang docs](https://haui-implsolution.github.io/DX-OS/), có mục lục và liên kết giữa các trang.

## Giới hạn đã biết

Việc chưa làm và giới hạn chấp nhận ở [`BACKLOG.md`](./dynamic-core/BACKLOG.md).
Giới hạn của từng tính năng ở [ma trận bằng chứng](https://haui-implsolution.github.io/DX-OS/docs/evidence-matrix).
Phần trí tuệ nhân tạo (chữ I của H-P-D-I) chưa làm, xem [`README.md`](../README.md#đối-chiếu-với-đề-thi), mục Đối chiếu với đề thi.
