# Hướng dẫn cho người triển khai

Trang này dành cho người cài DX-OS lên máy chủ, chọn hoặc viết tệp mô hình nghiệp vụ, cấp tài khoản và vận hành.
Từng lệnh và tùy chọn có ở [`docs/OPERATIONS.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/OPERATIONS.md); trang này chỉ vạch đường đi.

## Cài và chạy

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

- `dxos start` khởi động các dịch vụ và chờ chúng sẵn sàng; kèm một hay nhiều tệp mô hình thì áp dụng luôn khi khởi động.
- `dxos status` cho trạng thái từng dịch vụ và các địa chỉ; `dxos doctor` kiểm máy trước khi chạy.
- `dxos admin` đặt mật khẩu tạm cho tài khoản `admin`; bắt buộc đổi ở lần đăng nhập đầu.
- `dxos demo` tạo bốn tài khoản thử và hồ sơ mẫu để xem hệ thống có việc đang chạy.

Địa chỉ chính: `/` là giao diện của người dùng, `/console` là bảng điều khiển của bạn, `/portal/` liệt kê các địa chỉ và trạng thái dịch vụ.

## Chọn nghiệp vụ

Vào `/console` bằng tài khoản `ADMIN`.

- **Thư viện mẫu:** chọn một trong các mẫu có sẵn (một cửa sinh viên, hỗ trợ kỹ thuật, đặt phòng, mượn thiết bị và các mẫu khác) rồi áp dụng.
- **Soạn và áp dụng:** viết tệp mô hình hoặc điền form, có hướng dẫn nói rõ viết được những gì, và sơ đồ quy trình vẽ theo tệp đang soạn.

Thêm một nghiệp vụ mới là thêm một tệp mô hình; cách viết ở [`docs/MODEL.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/MODEL.md).

## Đổi nghiệp vụ khi đã có dữ liệu

1. Mở phiên bản đang chạy ở tab **Soạn và áp dụng** và sửa.
2. Bấm **Xem kế hoạch**: kế hoạch nêu mức an toàn, thay đổi nào chuyển được dữ liệu, thay đổi nào bị từ chối và vì sao.
   Lỗi trong tệp được chỉ đúng dòng và cột.
3. Bấm **Áp dụng** khi kế hoạch đúng ý; hệ thống đang chạy không bị dừng.
4. Muốn quay lại, ở tab **Mô hình đang chạy** bấm **Lịch sử** của mô hình rồi **Quay về phiên bản trước**; việc quay về cũng có kế hoạch để xem trước.

Kế hoạch bị từ chối không đổi gì; hệ thống vẫn chạy phiên bản cũ.
Từ dòng lệnh dùng `dxos plan <tệp>` và `dxos apply <tệp>`.
Muốn tệp mô hình nằm trong git và tự được đối chiếu với hệ thống đang chạy thì dùng `dxos gitops`.

## Người dùng và vai

Tab **Người dùng** của `/console` cho tìm người dùng, tạo người dùng, gán và gỡ vai và đơn vị, khóa và mở tài khoản, đặt lại mật khẩu.
Mật khẩu tạm hiện một lần và người dùng phải đổi ở lần đăng nhập đầu.
Vai `ADMIN` của nền tảng không cấp ở tab này mà cấp ở Keycloak Admin Console.

## Nối hệ thống khác

- **Webhook:** mọi thay đổi của hồ sơ được báo tới địa chỉ web hoặc kênh chat, có chữ ký để bên nhận kiểm tra; chi tiết ở [`docs/INTEGRATIONS.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/INTEGRATIONS.md).
- **Ứng dụng ngoài:** tab **Ứng dụng ngoài** hoặc `dxos client add` đăng ký một ứng dụng gọi API với đúng một vai của một mô hình.
- **Hàng đợi sự kiện:** tab cùng tên cho xem việc chờ gửi và thử lại khi lỗi.
- **Công cụ báo cáo:** nối bằng vai chỉ đọc `report_ro`; xem [`docs/REPORTING.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/REPORTING.md).

## Dữ liệu và an toàn

- **Danh mục dữ liệu:** tab cùng tên cho biết dữ liệu của mỗi mô hình nằm ở đâu, ai được xem, đi vào từ đâu và đi ra đâu, đến từng trường.
- **Sao lưu:** `dxos backup` chụp bản mã hóa của cơ sở dữ liệu và tệp; cất mật khẩu sao lưu ở nơi khác máy chủ.
- **Lịch sử chống sửa:** `dxos history verify` kiểm chuỗi lịch sử của hồ sơ chưa bị sửa.
- **Lưu trữ dài hạn:** `dxos archive` ghi hồ sơ đã đóng ra tệp để lưu và kiểm lại.
- **Quét mã độc tệp tải lên:** bật sẵn; `dxos av off` để tắt, `dxos av` để xem trạng thái.
- **Kiểm tệp mô hình:** `dxos lint <tệp>` báo lỗi theo dòng và cột mà không cần hệ thống đang chạy; `dxos conformance <mô hình>` kiểm các rào chắn của lõi trên stack đang chạy.
- **Cập nhật:** `dxos update` cài bản mới, giữ cấu hình và dữ liệu.
- **Kéo từ git:** `dxos gitops` kéo hạ tầng từ một kho DX-OS và các tệp mô hình từ một kho của bạn, rồi báo lệch, chờ bạn duyệt hoặc tự áp dụng; cách cấu hình ở mục Kéo từ git của `docs/OPERATIONS.md`.
