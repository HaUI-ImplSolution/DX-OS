# Trade-off và rủi ro: Phase 6

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất |
|---|---|---|---|---|---|
| TO1 | Thứ tự làm | A: khái niệm hồ sơ trước; B: đổi mô hình trước | A, thử công cụ song song | Phase 7 biết đủ khái niệm cần chuyển đổi | Phần đặt cược chính hoàn thiện muộn hơn một tuần |
| TO2 | Nguồn của đơn vị | A: mô hình cùng nhóm Keycloak; B: bảng riêng; C: đặt tên theo vai | A | Dùng đúng năng lực của Keycloak, token mang sẵn đơn vị | Đổi nhóm phải đăng nhập lại mới có hiệu lực |
| TO3 | Nguồn của hộp việc | A: truy vấn bảng hồ sơ; B: truy vấn tác vụ Flowable | A | Áp dụng cùng quyền theo dòng của API chung | Phụ thuộc trạng thái trong bảng khớp với quy trình, đã bảo đảm bằng một giao dịch |

## 2. Rủi ro

| # | Rủi ro | Giảm thiểu | Dự phòng |
|---|---|---|---|
| R1 | Quyền theo dòng phức tạp hơn gây lộ dữ liệu | Dựng điều kiện ở một chỗ, case âm tính T6-03 đến T6-06 | Khóa phạm vi mới đến khi sửa xong |
| R2 | Phạm vi nở làm chậm Phase 7 | Không làm tự động giao việc, nhiều cấp duyệt | Cắt bớt giao diện giao việc, giữ API |
| R3 | Công cụ migration không hợp | WP6-00 thử ngay đầu phase | Tự sinh `ALTER TABLE` từ so sánh mô hình |

## 3. Kết luận thử công cụ

Thử ngày 02/11/2026 trên PostgreSQL 16 với 1.000 dòng, không đưa vào mã sản phẩm.

| Công cụ | Tình huống | Kết quả |
|---|---|---|
| pgschema | Thêm cột | Sinh đúng `ADD COLUMN` |
| pgschema | Đổi tên cột | Sinh `DROP COLUMN` rồi `ADD COLUMN`, **mất dữ liệu** |
| pgschema | Thêm `NOT NULL` khi có dòng vi phạm | Kế hoạch không báo dòng vi phạm; chưa chạy thử áp dụng |
| pgroll 0.16.3 | Đổi tên cột | Giữ nguyên dữ liệu, tạo schema phiên bản `m_01_rename` trong lúc chuyển |
| pgroll 0.16.3 | Thêm `NOT NULL` khi có dòng vi phạm | Tự quay lui, lỗi chung, không liệt kê dòng vi phạm |
| `ProcessMigrationService` | Hồ sơ đang chạy, mẫu mới vẫn có bước hiện tại | Kiểm tra trước không lỗi; chuyển xong đi tiếp được theo nhánh mới |
| `ProcessMigrationService` | Mẫu mới bỏ bước hiện tại | Kiểm tra trước báo đúng bước chưa được ánh xạ; khai báo ánh xạ thì chuyển được |
| Flowable | Không chuyển | Hồ sơ cũ ở phiên bản cũ, hồ sơ mới dùng phiên bản mới |

Thử thêm cách đổi tên trước rồi mới đưa trạng thái đích cho pgschema 1.13.4:

| Tình huống | Kết quả |
|---|---|
| Đích giống hiện tại | Kế hoạch rỗng |
| Đổi tên bằng `RENAME COLUMN`, rồi lập kế hoạch | Kế hoạch rỗng, dữ liệu còn đủ |
| Thêm cột, thêm trạng thái vào ràng buộc | Áp dụng được, dữ liệu còn đủ |
| Xóa cột | Kế hoạch ghi rõ thao tác, dạng JSON có loại thao tác và đường dẫn |
| Thêm `NOT NULL` khi có dòng vi phạm | Áp dụng thất bại, để lại ràng buộc chưa xác thực |

Hướng cho Phase 7, chốt sau khi đạt bốn điều kiện dưới đây:

- **Lược đồ:** pgschema so sánh và sinh SQL; lõi đổi tên theo tên cũ khai báo trong tệp mô hình trước khi chạy nó, và tự liệt kê dòng vi phạm trước khi áp dụng.
- **Chặn mất dữ liệu:** lõi đọc JSON của kế hoạch, thấy thao tác xóa thì dừng, chỉ chạy khi có xác nhận riêng và sao lưu; một trường bị xóa cùng một trường cùng kiểu được thêm thì kế hoạch hỏi có phải đổi tên không.
- **Quy trình:** dùng `ProcessMigrationService`; bước xem trước gọi kiểm tra của Flowable để báo hồ sơ đang xử lý bị ảnh hưởng.
- **pgroll:** không dùng trong bản dự thi, vì thế mạnh của nó là đổi không gián đoạn, thuộc ngoài phạm vi.

Rủi ro còn lại: quên khai báo tên cũ thì so sánh hai trạng thái không nhận ra đổi tên, nên việc bảo vệ dữ liệu nằm ở lớp lõi tự viết, không nằm ở công cụ.

Điều kiện kiểm ngay đầu Phase 7:

1. Chạy được với DDL do lõi sinh ra, gồm khóa ngoại, `UNIQUE` và quyền `report_ro`.
2. Áp dụng lại sau khi lỗi giữa chừng thì về đúng trạng thái đích, kể cả phần còn sót.
3. Kế hoạch xem trước khớp với những gì áp dụng thật, khi có đổi tên.
4. Đổi tên trường có `UNIQUE` và chỉ mục thì kế hoạch rỗng sau bước đổi tên, dữ liệu còn đủ.

Không đạt thì tự sinh `ALTER TABLE` trong một giao dịch từ so sánh hai phiên bản mô hình.

## 4. Nợ kỹ thuật chấp nhận

- Trạng thái của mẫu quy trình là ràng buộc `CHECK` sinh lúc tạo bảng; đổi mẫu khi đã có bảng cần bước chuyển ở Phase 7.
- Người xử lý và người quyết định lưu bằng mã người dùng; màn hình hồ sơ hiện tên qua dòng lịch sử.
- Đổi thành viên đơn vị chỉ có hiệu lực sau khi người đó lấy token mới.
- Hộp việc tối đa 100 hồ sơ cho mỗi thực thể, chưa phân trang.
- Nhật ký audit của `dx-core` vẫn chỉ ghi lỗi và truy cập bị từ chối; thay đổi trên bản ghi nằm ở dòng lịch sử.
