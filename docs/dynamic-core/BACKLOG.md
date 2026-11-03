# Việc tồn đọng của lõi dynamic

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của lõi dynamic.
Hướng đi nằm ở [`README.md`](./README.md); kế hoạch từng phase nằm ở `docs/phase-N-*`.
Làm xong một mục thì xóa dòng đó, không ghi lại diễn biến.

## 1. Thứ tự đến hạn nộp

Hạn gửi bài là 17:00 ngày 06/12/2026.
Mốc dưới đây là dự kiến.

| Phase | Nội dung | Dự kiến xong |
|---|---|---|
| 9 | Ứng dụng Một cửa sinh viên, bảng điều khiển của người triển khai (màn hình kế hoạch, lịch sử áp dụng), họ mẫu chiếm dụng tài nguyên theo thời gian và chế độ xem lịch, E2E theo tám tiêu chí đạt | 30/11 |
| 10 | Bản phát hành, giấy phép, SBOM, tài liệu, triển khai công khai, gửi bài | 06/12 |

## 2. Phải xử lý trước khi phát hành

| Việc | Ghi chú |
|---|---|
| Các mục "Tạm gác" và "Việc cần làm" của [Cam kết nguồn mở](./README.md#cam-kết-nguồn-mở) | Kho quy chế HaUI, Redis và Langfuse trong đánh giá RAG, mô hình AI, Mattermost |
| `README.md` gốc, `README_EN.md`, trang docs | Trang docs vẫn mô tả bản `v1.1.1` đang chạy trên `main`; cập nhật khi bản mới lên `main` |
| Triển khai công khai | VPS giữ bản cũ đến khi lõi mới đạt |
| Cập nhật `deploy/.env` trên máy dev | Tệp này còn biến của bản cũ; dựng stack dev bằng `deploy/make_env.py` |

## 3. Giới hạn đã biết

| Việc | Ghi chú |
|---|---|
| Chưa có CLI `dxos` | Áp dụng mô hình qua REST |
| Chưa cắt bớt service bằng compose profile | Mô hình không dùng thành phần nào vẫn phải chạy thành phần đó |
| Quyền `own` chỉ tới một bước tham chiếu | Quan hệ nhiều bước chưa dùng được |
| Hành động hoàn tất | Chỉ có thao tác `add_to_group`, chưa có `remove_from_group`; nhóm Keycloak phải có sẵn, thiếu thì thông điệp được thử lại; lỗi chỉ xem được ở cột `last_error` của `engine.outbox`, chưa có API hay màn hình |
| Tệp đính kèm | Gỡ tệp chỉ xóa dòng liên kết, đối tượng trong kho lưu trữ còn lại; chưa giới hạn loại tệp ngoài mức 10 MB của yêu cầu; quét ClamAV chỉ kiểm bằng test đơn vị, chưa chạy E2E với profile `av` |
| Hạn xử lý | Bộ quét quá hạn chỉ ghi dòng lịch sử; chưa có người nghe để nhắc qua chat |
| Đổi họ mẫu của một thực thể | Bị từ chối, kể cả khi mọi hồ sơ đã ở trạng thái cuối; muốn đổi hình dạng quy trình thì thêm thực thể mới; `docs/dynamic-core/README.md` còn mô tả việc chuyển hồ sơ đang chạy bằng `ProcessMigrationService`, chưa làm |
| Họ `work_item` | Hồ sơ ở trạng thái đã xử lý xong chờ người nộp xác nhận, chưa tự đóng sau một thời gian |
| Rút lại trên giao diện | Nút rút lại không mở ô nhập lý do, dù API cho phép ghi lý do |
| Áp dụng đã đổi tên chưa có đường hủy | Phải sửa nguyên nhân rồi chạy lại đúng tệp đó |
| Xác nhận mất dữ liệu không tự sao lưu | Người vận hành chạy `deploy/backup.sh` trước |
| Hộp việc chưa phân trang | Tối đa 100 hồ sơ cho mỗi thực thể |
| Đổi thành viên đơn vị | Chỉ có hiệu lực sau khi người đó lấy token mới |
| Nhãn mặc định của react-admin | Các nút có sẵn của khung chưa dịch sang tiếng Việt |

## 4. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Khởi động bản mới trên dữ liệu của `v1.1.1` | Không có đường nâng cấp dữ liệu |
| Hệ thống đã áp dụng mô hình trước Phase 8 | Cột `handle_by` chỉ vào bảng khi mô hình được áp dụng với nội dung khác; đặt lại stack hoặc đổi mô hình một lần |
| Mức tài nguyên của toàn bộ stack | Cần đo trước khi chọn cấu hình VPS |
