# Lộ trình

Tài liệu này nói DX-OS định đi tiếp về đâu.
Chi tiết từng việc, lý do và giới hạn đã biết nằm ở [`docs/dynamic-core/BACKLOG.md`](./docs/dynamic-core/BACKLOG.md); việc đã xong ghi ở [`CHANGELOG.md`](./CHANGELOG.md).
Lộ trình không gắn ngày; thứ tự trong mỗi nhóm là thứ tự định làm.

## Hướng đi

DX-OS là lõi không giao diện: khai báo nghiệp vụ trong một tệp mô hình, lõi dựng cơ sở dữ liệu, tài khoản, API, quy trình và lịch sử.
Mọi việc dưới đây giữ hướng đó: thêm nghiệp vụ là thêm tệp, không thêm mã vào lõi.

## Tiếp theo

- **Chứng minh lõi phục vụ ứng dụng bên ngoài:** một ứng dụng mẫu có giao diện riêng chỉ gọi lõi qua API; một ví dụ cho thấy tệp mô hình sống trong git, chạy kế hoạch trên pull request và áp dụng khi gộp.
- **Dữ liệu:** truy vấn trực tiếp trên kho lưu trữ dài hạn.

## Sau đó

- **Trí tuệ nhân tạo:** hỏi đáp có trích nguồn và tác tử đề xuất có người duyệt, chỉ dùng mô hình mở hoàn toàn theo định nghĩa của OSI.
- **Quy trình:** thêm họ mẫu khi có đủ ba miền chạy được bằng tham số.
- **Tệp mô hình:** nâng tệp cũ khi định dạng đổi.

## Đã cân nhắc và không làm

Nhập CSV, tìm kiếm hồ sơ, ủy quyền có thời hạn, quyền theo từng trường và vài việc khác đã được cân nhắc rồi giữ nguyên; lý do ở mục "Giới hạn chấp nhận" của [`BACKLOG.md`](./docs/dynamic-core/BACKLOG.md), và chỉ mở lại khi có yêu cầu thật.

## Đóng góp vào lộ trình

Việc mới đi qua issue trên GitHub: mô tả vấn đề, đề xuất và phạm vi ảnh hưởng, theo mẫu issue của kho.
Cách đóng góp ở [`CONTRIBUTING.md`](./CONTRIBUTING.md).
