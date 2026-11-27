# Lộ trình

Tài liệu này nói DX-OS định đi tiếp về đâu.
Chi tiết từng việc, lý do và giới hạn đã biết nằm ở [`docs/dynamic-core/BACKLOG.md`](./docs/dynamic-core/BACKLOG.md); việc đã xong ghi ở [`CHANGELOG.md`](./CHANGELOG.md).
Lộ trình không gắn ngày; thứ tự trong mỗi nhóm là thứ tự định làm.

## Hướng đi

DX-OS là lõi không giao diện: khai báo nghiệp vụ trong một tệp mô hình, lõi dựng cơ sở dữ liệu, tài khoản, API, quy trình và lịch sử.
Mọi việc dưới đây giữ hướng đó: thêm nghiệp vụ là thêm tệp, không thêm mã vào lõi.

## Tiếp theo

- **Thử với đơn vị thật:** đưa DX-OS cho một đơn vị có quy trình duyệt để kiểm các giả thuyết về nhu cầu, vì nhu cầu hiện là giả thuyết.
- **Nâng thành phần lên dòng mới:** gateway APISIX (3.11) và Spring Boot (4.1) đang ở dòng cũ; Dependabot chỉ mở bản vá của hai thành phần này, nên việc nâng làm tay, kèm bộ kiểm của [`docs/TESTING.md`](./docs/TESTING.md).
- **Tài liệu tiếng Anh:** `README_EN.md` đã có; các tài liệu còn lại chỉ có tiếng Việt, bắt đầu từ Bắt đầu nhanh, Viết tệp mô hình và Kiến trúc.

## Sau đó

- **Quy trình:** thêm họ mẫu (Kiểm tra định kỳ, Đánh giá và chọn lọc) khi có đủ ba miền chạy được bằng tham số.

## Đã cân nhắc và không làm

Tìm trong nội dung tệp, hỏi đáp có trích nguồn và tác tử nghe sự kiện, ủy quyền có thời hạn, quyền theo từng trường và vài việc khác đã được cân nhắc rồi giữ nguyên; lý do ở mục "Giới hạn chấp nhận" của [`BACKLOG.md`](./docs/dynamic-core/BACKLOG.md), và chỉ mở lại khi có yêu cầu thật.

## Đóng góp vào lộ trình

Việc mới đi qua issue trên GitHub: mô tả vấn đề, đề xuất và phạm vi ảnh hưởng, theo mẫu issue của kho.
Cách đóng góp ở [`CONTRIBUTING.md`](./CONTRIBUTING.md).
