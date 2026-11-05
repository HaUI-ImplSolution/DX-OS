# Trade-off và rủi ro: Phase 14

| # | Chọn | Bỏ | Rủi ro và cách giảm |
|---|---|---|---|
| TO1 | Khóa đọc/ghi trong bộ nhớ theo mô hình | Khóa trong Postgres | Không đúng khi chạy nhiều bản backend; BACKLOG đã ghi chỉ chạy một bản |
| TO2 | Trễ xóa tệp để sao lưu nhất quán | Dừng ghi trong lúc sao lưu | Lượt sao lưu dài hơn khoảng trễ thì vẫn hở; `backup.sh` cảnh báo; tệp đã xóa còn chiếm chỗ trong khoảng trễ |
| TO3 | Client credentials cho CLI | Device flow qua trình duyệt | Bí mật quyền ADMIN nằm trong `.env`; `.env` vốn đã giữ mật khẩu quản trị và chỉ đọc được bởi chủ máy |
| TO4 | Chuỗi hash toàn cục, ghi dưới khóa advisory | Chuỗi theo từng hồ sơ | Ghi lịch sử thành tuần tự; đủ với tải của một tổ chức; chuỗi theo hồ sơ không phát hiện được xóa cả hồ sơ |
| TO5 | Neo trên máy chủ và qua webhook | Neo ở dịch vụ ngoài (transparency log) | Người quản trị máy chủ vẫn sửa được cả chuỗi và điểm neo; webhook là neo nằm ngoài nếu bên nhận lưu `hash` |
