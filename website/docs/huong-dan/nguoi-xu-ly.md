# Hướng dẫn cho người xử lý hồ sơ

Trang này dành cho cán bộ, người duyệt, kỹ thuật viên và người quản lý: những ai giữ vai xử lý trong một nghiệp vụ.

## Hộp việc

**Hộp việc** liệt kê những hồ sơ đang chờ bạn xử lý, việc quá hạn lên đầu.
Số **Việc đang chờ** tự cập nhật trong vài giây khi có hồ sơ mới, không cần tải lại trang.
Bạn chỉ thấy hồ sơ trong phạm vi vai của mình: mọi hồ sơ, hồ sơ của đơn vị bạn, hồ sơ được giao cho bạn, hoặc chỉ đọc.

## Xử lý một hồ sơ

Mở hồ sơ từ hộp việc.
Các nút hành động hiện theo trạng thái hiện tại và vai của bạn.
Mỗi nghiệp vụ chọn một họ quy trình để bắt đầu:

| Họ | Hành động của người xử lý |
|---|---|
| Phê duyệt | **Duyệt**, **Từ chối**, **Yêu cầu bổ sung** |
| Công việc cần xử lý | **Nhận xử lý**, **Xử lý xong**, **Từ chối** |
| Đặt chỗ | **Xác nhận**, **Từ chối**, **Bắt đầu sử dụng**, **Hoàn tất**, **Vắng mặt** |

Từ chối và yêu cầu bổ sung bắt buộc có lý do; người nộp đọc lý do đó ở lịch sử.
Nghiệp vụ có thể thêm hoặc đổi hành động, và có hành động chỉ hiện khi hồ sơ đạt điều kiện, ví dụ số tiền vượt mức thì phải chuyển lên cấp duyệt cao hơn.

## In giấy tờ

Nếu nghiệp vụ có mẫu in, trang chi tiết hồ sơ hiện nút **In** ở những trạng thái mẫu cho phép; ví dụ giấy xác nhận chỉ in được khi hồ sơ đã duyệt.
Bấm nút để tải tệp PDF của hồ sơ.

## Nhận và giao việc

Hồ sơ chưa giao thì cả vai xử lý đều làm được.
**Nhận việc** hoặc **Giao việc** (chọn người ở ô **Giao cho**) khiến chỉ người được giao xử lý được; **Bỏ giao** trả hồ sơ về cho cả vai.

## Hạn xử lý

Nghiệp vụ có hạn thì hồ sơ quá hạn hiện nhãn **Quá hạn** và lên đầu hộp việc.
Lịch sử ghi lúc hồ sơ sắp tới hạn và lúc quá hạn.
Nghiệp vụ có khai leo thang thì lịch sử ghi thêm mỗi lần hồ sơ quá hạn được báo lên vai khác; việc này không đổi người giữ hồ sơ.

## Xử lý nhiều hồ sơ cùng lúc

Chọn nhiều hồ sơ trong danh sách rồi bấm hành động; mỗi lần tối đa 50 hồ sơ.
Mỗi hồ sơ vẫn đi qua đủ luật của nó: hồ sơ không làm được thì được báo kèm lý do, các hồ sơ còn lại vẫn chạy.
Không có nút hoàn tác; hồ sơ xử lý nhầm thì xử lý tiếp theo đường quy trình, ví dụ mở lại hoặc trả lại nếu quy trình có.

## Các cách xem khác

- **Theo trạng thái:** danh sách hiện như một bảng, mỗi cột là một trạng thái.
- **Thống kê:** số hồ sơ theo trạng thái và đơn vị, thời gian xử lý trung bình, và tỉ lệ đóng đúng hạn khi nghiệp vụ có hạn.
- **Xuất CSV:** xuất danh sách ra tệp mà bảng tính đọc được, đúng phần bạn được quyền thấy.
- **Lịch:** với nghiệp vụ đặt chỗ, các lượt đặt theo ngày và theo tài nguyên.

## Quy tắc không lách được

- Người tạo hồ sơ không tự xử lý hồ sơ của mình.
- Hồ sơ đã được quyết định thì không sửa được.
- Hồ sơ đã giao cho người khác thì chỉ người đó xử lý được.
- Trạng thái chỉ đổi qua các nút quy trình; gọi thẳng API để đổi trạng thái bị chặn.
- Mỗi lần xử lý để lại một dòng trong lịch sử, và lịch sử nối thành chuỗi mà ai sửa, xóa hay chèn dòng thì bị chỉ ra.
