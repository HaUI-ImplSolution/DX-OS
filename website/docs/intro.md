---
slug: /intro
---

# Giới thiệu

Trang này là nơi lưu các tài liệu của [DX-OS](https://github.com/HaUI-ImplSolution/DX-OS): lõi dynamic cho hệ thống hồ sơ có quy trình.
Lõi nhận một tệp mô hình khai báo thực thể, trường, vai, quyền và quy trình, rồi dựng bảng dữ liệu, vai đăng nhập, API có phân quyền từng dòng, quy trình phê duyệt và giao diện.

## Tài liệu nằm ở đâu

| Nội dung | Nơi xem |
|---|---|
| Cách dùng theo vai: nộp, xử lý, triển khai | [Hướng dẫn sử dụng](./huong-dan/nguoi-nop.md) ở thanh bên của trang này |
| Giới thiệu, cài đặt, mục lục tài liệu | [README](https://github.com/HaUI-ImplSolution/DX-OS#readme) trong repository |
| Kiến trúc 3 tầng và luồng gọi dịch vụ | [Kiến trúc](./tai-lieu/kien-truc.md) |
| Module backend, build, cấu hình | [backend/README.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/backend/README.md) |
| Cài đặt, lệnh `dxos`, sao lưu, quét mã độc | [Cài đặt và vận hành](./tai-lieu/van-hanh.md) |
| Log, giới hạn tốc độ, sự kiện đăng nhập, audit, giám sát | [Giám sát và nhật ký](./tai-lieu/giam-sat.md) |
| Webhook sự kiện, lịch sử chống sửa, hồ dữ liệu | [Tích hợp với hệ thống ngoài](./tai-lieu/tich-hop.md) |
| Kết nối công cụ báo cáo bằng `report_ro` | [Kết nối công cụ báo cáo](./tai-lieu/bao-cao.md) |
| Phát triển dịch vụ, webhook và giao diện mở rộng | [Điểm mở rộng](./tai-lieu/mo-rong.md) |
| Tham chiếu API | OpenAPI `/v3/api-docs` của backend đang chạy; bảng đường dẫn trong [API](./tai-lieu/api.md) |
| Thay đổi theo phiên bản | [CHANGELOG.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/CHANGELOG.md) và [Releases](https://github.com/HaUI-ImplSolution/DX-OS/releases) |
| Viết tệp mô hình | [Viết tệp mô hình](./tai-lieu/viet-mo-hinh.md) |
| Hướng dẫn kiểm thử | [docs/TESTING.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/TESTING.md) |
| Đóng góp, bảo mật, quy tắc ứng xử | [CONTRIBUTING.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/CONTRIBUTING.md), [SECURITY.md](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/SECURITY.md) |
| Đánh giá, hồ sơ cuộc thi | Các mục ở thanh bên của trang này |

## Các mục trên trang này

- **Hình ảnh và video:** ảnh các màn hình của sản phẩm và hai đoạn video ngắn.
- **Tài liệu sản phẩm:** viết tệp mô hình, API, cài đặt và vận hành, giám sát, tích hợp, báo cáo, điểm mở rộng, kiến trúc, tương thích giữa các bản và kịch bản trình diễn; các trang này được chép từ `docs/` và `deploy/` mỗi lần dựng trang, nên không có bản thứ hai để sửa.
- **Hướng dẫn sử dụng:** ba trang theo vai: người nộp hồ sơ, người xử lý hồ sơ và người triển khai.
- **Đánh giá:** ma trận bằng chứng cho từng tính năng và bảng rà soát PoF theo đề thi.
- **Cuộc thi:** câu hỏi hội đồng, thành phần được chọn và không chọn.
- **Mẫu tài liệu theo phase:** mẫu dùng khi mở một phase làm việc mới.
