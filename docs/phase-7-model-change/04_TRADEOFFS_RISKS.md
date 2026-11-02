# Trade-off và rủi ro: Phase 7

## 1. Kết quả bốn điều kiện của pgschema

Thử ngày 02/11/2026 với pgschema 1.13.4 trên PostgreSQL 16, 1.000 dòng, bảng cùng dạng với bảng lõi sinh ra.

| # | Điều kiện | Kết quả |
|---|---|---|
| 1 | Chạy được với bảng có khóa ngoại, `UNIQUE`, `CHECK`, chỉ mục, quyền `report_ro` | Đạt: tạo từ schema rỗng, lập kế hoạch lại thì rỗng |
| 2 | Áp dụng lại sau lỗi giữa chừng về đúng đích, kể cả phần sót | Đạt: áp dụng lại dọn ràng buộc dở; về đích cũ cũng dọn được |
| 3 | Kế hoạch xem trước khớp áp dụng thật khi có đổi tên | Đạt: giữ tên cũ khi xem trước thì hai kế hoạch giống hệt |
| 4 | Đổi tên trường có `UNIQUE`, khóa ngoại, chỉ mục thì kế hoạch rỗng, dữ liệu còn đủ | Đạt, với điều kiện đổi cả tên ràng buộc và chỉ mục; `UNIQUE` vẫn có hiệu lực |

Điều thấy thêm:

- Kế hoạch JSON lập trước khi lược đồ đổi thì bị pgschema từ chối.
- Bỏ quyền khỏi trạng thái đích thì pgschema sinh `REVOKE`, nên đích phải khai báo đủ quyền.
- Xóa thực thể sinh `DROP TABLE ... CASCADE`.
- Áp dụng lỗi ở nhóm sau thì các nhóm trước đã chạy, ví dụ cột mới vẫn còn.

Quyết định: giữ pgschema.

## 2. Rủi ro

| # | Rủi ro | Giảm thiểu | Dự phòng |
|---|---|---|---|
| R1 | Quên khai báo tên cũ làm mất dữ liệu | Xóa trường là mức mất dữ liệu, cần xác nhận riêng; kế hoạch hỏi khi nghi đổi tên | Sao lưu trước khi xác nhận |
| R2 | pgschema sinh thao tác xóa mà mô hình không có | Đối chiếu kế hoạch JSON với thay đổi đã phân loại, lệch thì dừng | Tự sinh `ALTER TABLE` |
| R3 | Không có giao dịch chung giữa các nhóm câu lệnh | Kiểm vi phạm trước khi chạy; mỗi bước chạy lại được; lịch sử ghi chỗ dừng | Áp dụng lại |
| R4 | Thêm một tệp thực thi và tiến trình con | Ghim digest, giới hạn thời gian, ghi lại lỗi của công cụ | Tự sinh `ALTER TABLE` |
| R5 | API lỗi trong khoảnh khắc giữa lúc đổi cột và lúc nạp mô hình mới | Khoảng này rất ngắn; đổi không gián đoạn nằm ngoài phạm vi | Áp dụng ngoài giờ cao điểm |

## 3. Điều kiện dừng

Lược đồ do lõi sinh ra làm pgschema báo khác biệt không hết, hoặc kế hoạch sinh thao tác xóa không đoán trước được, thì chuyển sang tự sinh `ALTER TABLE`.

## 4. Nợ kỹ thuật chấp nhận

- API có thể lỗi trong khoảnh khắc giữa lúc đổi cột và lúc nạp mô hình mới; đổi không gián đoạn nằm ngoài phạm vi.
- Xem trước tạo schema rỗng và vai `report_ro` nếu chưa có, vì pgschema cần chúng để so sánh; chưa có bảng hay bản ghi nào bị đổi.
- Xác nhận mất dữ liệu không tự sao lưu; người vận hành chạy `deploy/backup.sh` trước.
- Áp dụng đã đổi tên mà tệp đó không thể hoàn tất thì phải sửa nguyên nhân rồi chạy lại, chưa có đường hủy.
- Vai và nhóm Keycloak không bị xóa khi mô hình bỏ chúng.
- Chuyển hồ sơ đang chạy sang mẫu quy trình khác chờ Phase 8.
