# Trade-off và rủi ro: Phase 5

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Ai phục vụ API dữ liệu | A: API chung bằng Java đọc mô hình; B: công cụ có sẵn sinh API từ cơ sở dữ liệu | A | Quyền, quy tắc, trạng thái và sự kiện ở một chỗ; cùng giao dịch với Flowable; không phụ thuộc cách đọc vai từ token của công cụ ngoài | Đội tự viết và bảo trì bộ dựng truy vấn; chèn SQL và hiệu năng là trách nhiệm của đội | Vừa |
| TO2 | Farm và Lab trong lúc xây lõi | A: gỡ ngay đầu phase; B: giữ chạy song song | A | Một nhánh mã, cấu trúc đổi tự do, không gánh tương thích | Nhánh làm việc không có bản chạy được cho đến khi xong WP5-00 và WP5-06 | Vừa, quay về tag `v1.1.1` |
| TO3 | Phần còn lại của `dx-core` | A: rút gọn thành thư viện dùng lại; B: viết lại từng phần | A | Giữ bảo mật, audit, lưu trữ, client Keycloak đã chạy tốt | Phải rà từng gói để chắc không còn khái niệm ứng dụng | Thấp |
| TO4 | Phạm vi quyền `own` | A: trường người dùng cộng một bước tham chiếu; B: quan hệ nhiều bước tổng quát | A | Làm kịp, đủ cho mô hình mẫu | Quan hệ sâu hơn chưa dùng được | Thấp |

Kết luận: chấp nhận TO1 đến TO4 như đã chọn.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Flowable 8 không chạy nhúng được trên Spring Boot 4.1 | Vừa | Cao | WP5-04-BE-01 kiểm ngay đầu WP | Lỗi khởi động hoặc thiếu auto-configuration | Dùng bản Flowable khác hoặc cấu hình thủ công engine không qua starter; chưa có phương án nào thì dừng và họp lại hướng workflow |
| R2 | Chèn SQL hoặc quyền theo dòng sai trong API chung | Vừa | Cao | Tên chỉ lấy từ mô hình đã kiểm, toán tử danh sách trắng, giá trị qua tham số; case T5-03, T5-06 đến T5-09, T5-14 | Case âm tính nào không bị chặn | Khóa ghi của API chung đến khi sửa xong |
| R3 | Trạng thái bản ghi và quy trình lệch nhau | Vừa | Cao | Một giao dịch, chỗ gây lỗi có điều khiển (T5-11) | Hồ sơ có `submitted` nhưng không có tác vụ chờ | Tác vụ đối chiếu định kỳ, trạng thái lấy theo quy trình |
| R4 | react-admin không hợp metadata đổi lúc chạy | Vừa | Vừa | Thử sớm, dựng bằng vòng lặp trên metadata | Phải viết riêng màn hình cho một thực thể | Chuyển sang Refine |
| R5 | API dựng truy vấn lúc chạy chậm | Thấp | Vừa | Đo ở T5-15 từ sớm | Phân vị 95 vượt 500 ms | Thêm chỉ mục từ mô hình, giới hạn bộ lọc |
| R6 | Tài khoản dịch vụ không đủ quyền tạo vai ở realm hiện có | Thấp | Vừa | Thử ngay ở WP5-02 | Keycloak trả 403 | Cấp quyền `manage-realm` cho tài khoản dịch vụ, ghi vào `sso/README.md` |
| R7 | Triển khai nhầm nhánh làm hỏng bản công khai đang chạy `v1.1.1` | Thấp | Cao | Bản công khai chỉ triển khai từ tag; nhánh phase không nối vào quy trình triển khai | Quy trình triển khai kéo mã từ nhánh phase | Triển khai lại từ tag `v1.1.1` |
| R8 | Gỡ nhiều làm vỡ build, CI hoặc deploy theo cách khó lần ra | Vừa | Vừa | Gỡ theo từng gói, compile sau mỗi bước, đọc lại diff | `./mvnw compile` hoặc CI đỏ sau một bước gỡ | Hoàn tác bước đó và gỡ lại nhỏ hơn |

Kết quả của các rủi ro lớn khi đóng phase:

- R1: Flowable 8.0.0 chạy nhúng trên Spring Boot 4.1 bằng `flowable-spring` cấu hình tay, bảng ở schema `engine`; không cần phương án dự phòng.
- R2: các case T5-03, T5-06 đến T5-09, T5-14 đạt; một lỗi thật được tìm ra và sửa là gắn bản ghi con vào bản ghi cha của người khác.
- R3: T5-11 đạt, hồ sơ và quy trình quay về cùng nhau khi có lỗi sau bước quy trình.
- R4: react-admin dựng được từ metadata; không cần chuyển sang Refine.
- R5: p95 11 ms cho 10.000 dòng.

## 3. Nợ kỹ thuật chấp nhận

- Chưa có so sánh phiên bản, đổi tên, báo dữ liệu vi phạm: Phase 6.
- Chưa có tệp đính kèm, mẫu xử lý có hạn, trục sự kiện: Phase 7.
- Chưa có CLI `dxos`, chưa cắt bớt service bằng compose profile.
- Quyền `own` mới tới một bước tham chiếu.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: R1 chưa có phương án sau ngày thứ 3 của WP5-04, hoặc case T5-11 không đạt sau hai lần sửa.
- Rollback: xóa schema `m_*` và `engine`, xóa vai Keycloak do lõi tạo, chạy `deploy/reset.sh`; quay về bản cũ bằng tag `v1.1.1`.

## 5. Tác động ngoài phase

- Lên các phase sau: hợp đồng metadata và định dạng mô hình phiên bản 0 trở thành nền; đổi sau sẽ kéo theo giao diện.
- Lên Farm và Lab: gỡ khỏi nhánh; bản cũ ở tag `v1.1.1`, dữ liệu cũ không chuyển sang.
- Lên demo, tài liệu nộp bài, thời hạn: nếu R1 xảy ra, phần quy trình của demo chịu ảnh hưởng đầu tiên.
