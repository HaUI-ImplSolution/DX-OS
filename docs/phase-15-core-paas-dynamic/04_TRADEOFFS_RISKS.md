# Trade-off và rủi ro: Phase 15

Giữ file này vì phase đổi định dạng tệp mô hình, là quyết định khó đảo ngược sau khi có mô hình dùng khóa mới.

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Cách đổi định dạng tệp mô hình | A: thêm khóa và kiểu tùy chọn, `format: 0` giữ nguyên. B: nâng `format` lên 1. C: không đổi định dạng | A (đã chốt ở Q2) | Mô hình cũ chạy không cần sửa; không có bước nâng định dạng | Bộ đọc phải nhận cả hai cách viết; bỏ một khóa sau này phá mô hình đã dùng nó | Cao khi đã có mô hình dùng khóa mới |
| TO2 | Tiêu chí "khai báo được mà không cần cú pháp mới" của README lõi | A: coi D1 đến D3 và U1 là ngoại lệ có chủ đích và ghi lại. B: giữ nguyên tiêu chí, bỏ các WP đó | A (Q2 đã chọn đổi định dạng) | Lõi nói được nhiều lĩnh vực hơn bằng khai báo | Tiêu chí tự đặt của đội không còn đúng nguyên văn; phải sửa README lõi (cần xin phép) | Vừa |
| TO3 | Nơi kiểm giá trị | A: `min`, `max`, `pattern` kiểm ở API và `CHECK`; `required_when` chỉ ở API. B: chỉ kiểm ở API | A nếu khảo sát `PRE-04` cho thấy `CHECK` được giữ qua các lần áp dụng, nếu không thì B | Ghi thẳng vào bảng cũng bị chặn | `CHECK` làm việc siết ràng buộc trên bảng có dữ liệu phức tạp hơn; biểu thức chính quy phải nằm trong tập con chung | Vừa |
| TO4 | Quyền của ứng dụng ngoài | A: theo vai của một mô hình. B: theo từng thực thể | A (đã chốt ở Q3) | Khớp Keycloak và cách `data/` kiểm quyền; ít bề mặt mới | Một ứng dụng không giới hạn được ở một thực thể của mô hình | Vừa; B cần khóa khai báo quyền mới trong tệp mô hình |
| TO5 | Tài liệu OpenAPI theo mô hình | A: sinh lúc gọi từ mô hình đang chạy. B: lưu một bản mỗi phiên bản mô hình | A | Không bao giờ lệch mô hình; không thêm bảng | Tốn một lần dựng mỗi lần gọi; không xem lại được tài liệu của phiên bản cũ | Thấp |
| TO6 | Hành động hàng loạt | A: frontend gọi API hành động từng hồ sơ. B: endpoint hàng loạt ở backend | A | Mỗi hồ sơ qua đủ luật, khóa và lịch sử như thao tác đơn; không thêm API | Nhiều yêu cầu hơn; phải giới hạn lô để không chạm giới hạn tốc độ | Thấp |

Kết luận: người dùng chốt ngày 2026-11-10 hướng A cho Q1, Q2 và Q3; TO1 chọn A, TO4 chọn A; TO2 xử lý bằng cách coi D1 đến D3 và U1 là ngoại lệ có chủ đích và sửa README lõi sau khi xong (cần xin phép); TO3 chờ khảo sát `PRE-04`.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Secret của ứng dụng ngoài lộ qua log, audit hay lịch sử | Thấp | Cao | Secret chỉ ở câu trả lời của lệnh tạo và đổi secret; kiểm bằng cách tìm secret trong log và sự kiện (T15-07) | Tìm thấy chuỗi secret ở log | Đổi secret của client; sửa chỗ ghi log |
| R2 | Ứng dụng ngoài được cấp quá quyền (vai `ADMIN` hay vai nền tảng) | Thấp | Cao | API chỉ nhận vai khai báo của mô hình, từ chối vai nền tảng; kiểm ở T15-06 và T15-09 | Client lấy được token có vai nền tảng | Xóa client; vá bộ kiểm vai |
| R3 | Biểu thức chính quy của Java và PostgreSQL hiểu khác nhau, nên API cho qua mà `CHECK` từ chối hoặc ngược lại | Vừa | Vừa | `pattern` chỉ nhận tập con chung và phải khớp toàn chuỗi; cấu trúc ngoài tập con bị từ chối lúc đọc tệp | Giá trị qua API nhưng `INSERT` thẳng bị từ chối | Thu hẹp tập con; bỏ `CHECK` của `pattern` và chỉ kiểm ở API |
| R4 | Thêm ràng buộc vào bảng đã có dòng vi phạm làm áp dụng mô hình hỏng giữa chừng | Vừa | Cao | `ModelDiff` đếm dòng vi phạm và từ chối kế hoạch trước khi đổi (T15-15) | Lần áp dụng dừng giữa chừng ở bước thêm `CHECK` | Dùng "Tiếp tục" hoặc "Hủy bỏ" của lần áp dụng dừng dở; sửa dữ liệu rồi áp dụng lại |
| R5 | OpenAPI theo mô hình làm lộ cấu trúc mô hình cho người không có vai | Thấp | Vừa | Cùng quy tắc quyền với metadata (T15-03) | Người không có vai đọc được tài liệu | Siết quyền; tắt endpoint |
| R6 | Khối `ui:` trỏ cột hay nhóm làm lộ trường ngoài quyền của vai | Thấp | Cao | `ui:` chỉ chọn trong metadata đã lọc theo vai; không thêm trường vào câu trả lời (T15-20) | Trường ngoài quyền hiện trong danh sách | Bỏ phần `ui` ở frontend; sửa bộ lọc ở `meta/` |
| R7 | Hành động hàng loạt chạm giới hạn tốc độ hoặc để lô dở dang khi mất mạng | Vừa | Thấp | Giới hạn kích thước lô và số yêu cầu cùng lúc; bảng tóm tắt từng hồ sơ cho biết hồ sơ nào đã qua | Nhiều hồ sơ báo 429 | Giảm cỡ lô; người dùng chạy lại cho các hồ sơ còn lại |
| R8 | Hai bên (acc1, acc2) làm lệch hợp đồng C1 đến C5 | Vừa | Vừa | Chốt chữ ký ở `PRE-02` trước khi code; mỗi bên đọc diff phần của bên kia khi đụng ranh giới | Biên dịch của một bên hỏng sau khi bên kia đẩy | Dừng, ghi `blockedBy`, chốt lại hợp đồng |
| R9 | Công cụ đối chiếu lược đồ coi `CHECK` là thừa và xóa ở lần áp dụng sau, nên ràng buộc ở cơ sở dữ liệu mất | Vừa | Vừa | Khảo sát `PRE-04` trước khi chốt TO3 | `CHECK` biến mất sau một lần áp dụng lại | Bỏ `CHECK`, chỉ kiểm ở API |

## 3. Nợ kỹ thuật chấp nhận

- P3 (`Idempotency-Key`), P4 (thao tác hoàn tất `call_target`), U3 (bảng Kanban) và U4 (xuất CSV) chưa làm; ghi vào việc để sau của `BACKLOG.md` khi phase xong.
- Đổi vai của một ứng dụng ngoài bằng cách gỡ rồi thêm lại, chưa có thao tác sửa tại chỗ.
- Hành động hàng loạt không có thao tác hoàn tác.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: một mô hình có sẵn đổi hành vi sau khi thêm khóa mới; `check_conformance.py` hỏng ở mô hình có sẵn; một client ứng dụng ngoài nhận được quyền ngoài vai đã khai báo.
- Rollback:
  - Mô hình: quay về phiên bản trước bằng chức năng quay về; bảng giữ cột mới nhưng không dùng nếu quay về trước khi dùng kiểu mới.
  - Ứng dụng ngoài: `dxos client remove` cho từng client; Keycloak giữ nguyên các client khác.
  - Code: lùi về bản phát hành trước; không có migration lõi nào cần đảo.

## 5. Tác động ngoài phase

- Lên các phase sau: mọi khóa mới trở thành một phần định dạng; P3, P4, U3, U4 dựa vào nền này.
- Lên lõi và các mô hình khác: mô hình có sẵn không đổi; trình soạn, JSON Schema và `docs/MODEL.md` phải theo kịp định dạng.
- Lên demo, tài liệu nộp bài, thời hạn: bản trình diễn có thể dùng mô hình có `ui:` và ứng dụng ngoài; hạn gửi bài 17:00 ngày 06/12/2026 không đổi; việc dở khi đến hạn xếp vào `BACKLOG.md`.
