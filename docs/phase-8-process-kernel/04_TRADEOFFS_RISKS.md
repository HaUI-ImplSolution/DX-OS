# Trade-off và rủi ro: Phase 8

## 1. Trade-off

| # | Quyết định | Được | Mất |
|---|---|---|---|
| TO1 | Quy tắc bất biến nằm trong Java, BPMN chỉ lo thứ tự | Không lệch giữa các họ mẫu | Mỗi quy tắc mới phải sửa Java |
| TO2 | Hạn tính bằng `handle_by` khi đọc, không dùng bộ đếm giờ của Flowable | Xác định được, không cần chạy bộ thực thi nền | Nhắc quá hạn phải do tác vụ quét định kỳ |
| TO3 | Họ mẫu viết tay, nhận tham số | Dễ đọc, đúng chuẩn Flowable | Mỗi hình dạng mới là một họ mẫu mới |

## 2. Rủi ro

| # | Rủi ro | Giảm thiểu | Dự phòng |
|---|---|---|---|
| R1 | Trừu tượng hóa sai vì mới có hai họ mẫu | Dựng lớp quy trình cùng họ thứ hai | Điều chỉnh khi họ thứ ba vào |
| R2 | Đổi định dạng khối `process:` làm hỏng mô hình cũ | Định dạng còn `format: 0`, đổi cả `models/` và script cùng lúc | Giữ tên cũ như bí danh |
| R3 | Rút lại và xóa đổi hành vi giao diện | Cập nhật giao diện cùng phase, kiểm bằng tay | Giữ xóa cho thực thể không có quy trình |
| R4 | Hành động hoàn tất chạy lặp hoặc mất | Khóa chống chạy lặp, thử lại từ bảng outbox | Chạy lại thủ công từ bảng |

## 3. Điều kiện dừng

Họ công việc cần xử lý đòi lớp quy trình chung phải có ngoại lệ theo họ mẫu thì dừng và xem lại cách tách trước khi làm tiếp.

## 4. Nợ kỹ thuật chấp nhận

- Họ chiếm dụng tài nguyên theo thời gian và chế độ xem lịch để phase sau.
- Đổi họ mẫu của thực thể đã có hồ sơ chưa hỗ trợ; xem backlog.
- Gỡ tệp chỉ xóa dòng liên kết; đối tượng trong kho lưu trữ còn lại.
- Thông điệp outbox lỗi chỉ xem được trong bảng `engine.outbox`.
- Bản ghi chỉ lưu phiên bản mô hình từ WP8-07.
- Cột hệ thống mới của bảng quy trình (`handle_by`) và trạng thái mới chỉ vào bảng khi mô hình được áp dụng với nội dung khác; hệ thống đã chạy bản cũ cần đặt lại hoặc đổi mô hình một lần.
