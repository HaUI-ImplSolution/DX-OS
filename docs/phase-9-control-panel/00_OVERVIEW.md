# Phase 9: Bảng điều khiển và họ mẫu thứ ba

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong |
| Mức độ | Nghiêm trọng |
| Ứng dụng và module chính | `dx-model`, `dx-engine`, `frontend`, `deploy/dev` |
| Phụ thuộc | Phase 8 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Phạm vi trong phase này |
|---|---|---|
| [`docs/dynamic-core/README.md`](../dynamic-core/README.md), mục "Bảng điều khiển" | Thư viện mẫu, soạn mô hình có hướng dẫn, màn hình kế hoạch, lịch sử áp dụng, sơ đồ quy trình | Toàn bộ |
| Cùng tài liệu, mục "Quy trình chung" | Họ chiếm dụng tài nguyên theo thời gian, sở hữu kiểm tra xung đột khung giờ | Toàn bộ |
| Cùng tài liệu, mục "Thế nào là đạt" | Tám tiêu chí của kịch bản trình diễn | Kiểm bằng E2E |
| [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md) | Việc của Phase 9 | Toàn bộ |

## 2. Lý do

Người dùng chính của lõi là người triển khai, và họ cần một giao diện biết mình phải khai báo gì.
Họ chiếm dụng tài nguyên theo thời gian hoàn tất ba họ mẫu của bản dự thi và là họ duy nhất cần chế độ xem lịch.
Ứng dụng Một cửa sinh viên cùng E2E tám tiêu chí là kịch bản trình diễn.

## 3. Phạm vi

- Trong phạm vi:
  - Họ `reservation`: khai báo tài nguyên, khung giờ và sức chứa; kiểm tra xung đột khi tạo và khi sửa.
  - Chế độ xem lịch cho thực thể thuộc họ `reservation`.
  - API đọc cho bảng điều khiển: lược đồ mô hình, các họ mẫu, mẫu mô hình, phiên bản đã áp dụng.
  - Mẫu mô hình trong thư viện mẫu: Một cửa sinh viên, hỗ trợ kỹ thuật, đặt phòng, cấp quyền, mượn thiết bị.
  - Bảng điều khiển: thư viện mẫu, trình soạn YAML có gợi ý, form có hướng dẫn, màn hình kế hoạch, lịch sử áp dụng, sơ đồ quy trình chỉ để xem.
  - Một cửa sinh viên có tệp minh chứng và hạn xử lý.
  - E2E theo tám tiêu chí.
- Ngoài phạm vi:
  - Họ mẫu tùy biến và giao diện mở rộng từ mẫu.
  - Họ kiểm tra định kỳ, họ đánh giá và chọn lọc.
  - Tác tử AI, thông báo chat.
  - Đổi họ mẫu của thực thể đã có hồ sơ.
- Ranh giới: bảng điều khiển chỉ gọi API của lõi; tệp mô hình là nguồn sự thật; mã lõi không chứa tên thực thể của một lĩnh vực.

## 4. Kết quả đầu ra mong đợi (Definition of Done)

- Mô hình đặt phòng chạy trên họ `reservation` không sửa mã lõi; hai lượt đặt trùng khung giờ vượt sức chứa thì lượt sau bị từ chối.
- Thực thể đặt phòng có chế độ xem lịch trong ứng dụng.
- Người triển khai tạo một mô hình từ thư viện mẫu, sửa bằng form hoặc trình soạn, xem kế hoạch với mức rủi ro và áp dụng, không dùng dòng lệnh.
- Màn hình kế hoạch hiện khác biệt, mức rủi ro, dòng vi phạm, số hồ sơ đang chạy; thay đổi mất dữ liệu cần xác nhận riêng.
- Lịch sử áp dụng của một mô hình hiện từng bước và có quay về phiên bản trước.
- Sơ đồ quy trình của mỗi thực thể hiện đúng trạng thái và hành động của họ mẫu.
- Tám tiêu chí ở `docs/dynamic-core/README.md`, mục "Thế nào là đạt", có bằng chứng.
