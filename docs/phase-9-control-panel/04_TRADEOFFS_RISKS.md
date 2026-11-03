# Trade-off và rủi ro: Phase 9

## 1. Trade-off

| # | Quyết định | Được | Mất |
|---|---|---|---|
| TO1 | Bảng điều khiển trong ứng dụng web, nạp chậm | Một lần đăng nhập, không thêm dịch vụ | Mã bảng điều khiển nằm chung kho với ứng dụng |
| TO2 | Mẫu mô hình nằm trong dịch vụ lõi | Build backend không cần thư mục ngoài `backend/` | Mẫu phải đi qua bản phát hành của lõi |
| TO3 | Form dựng từ JSON Schema | Thêm khóa vào schema là form có thêm trường | Giao diện form chung hơn một wizard viết tay |

## 2. Rủi ro

| # | Rủi ro | Giảm thiểu | Dự phòng |
|---|---|---|---|
| R1 | Hai yêu cầu đặt cùng giờ cùng lọt qua kiểm tra | Khóa dòng tài nguyên trong giao dịch | Ràng buộc loại trừ ở PostgreSQL |
| R2 | Thư viện của bảng điều khiển làm tăng kích thước và danh mục giấy phép | Nạp chậm; kiểm giấy phép trước khi thêm | Bỏ thư viện đó |
| R3 | Form và trình soạn sửa lệch nhau | Cả hai đọc và ghi cùng một tệp YAML | Chỉ giữ trình soạn |
| R4 | Chuyển mẫu làm hỏng script cũ | Cập nhật script cùng lúc, chạy lại hồi quy | Giữ đường dẫn cũ |

## 3. Điều kiện dừng

Họ `reservation` đòi lớp quy trình chung phải có ngoại lệ theo họ mẫu thì dừng và xem lại cách tách trước khi làm tiếp.

## 4. Nợ kỹ thuật chấp nhận

- Họ kiểm tra định kỳ và họ đánh giá và chọn lọc chưa làm.
- Họ mẫu tùy biến chưa làm.
- Trình soạn Monaco kéo theo khoảng 15 MB mã tĩnh trong image web; chỉ tải khi mở bảng điều khiển.
- Điền form viết lại tệp YAML và bỏ chú thích giữa tệp; chú thích đầu tệp được giữ.
- Cột "Áp dụng bởi" hiện mã người dùng Keycloak.
- Quét mã độc ClamAV của tệp đính kèm chưa chạy E2E.
