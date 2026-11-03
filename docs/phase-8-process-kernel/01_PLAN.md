# Plan thực hiện: Phase 8

Thứ tự làm: Spec → Swagger → Code.
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WP8-01 → WP8-02 → WP8-03 → WP8-04 → WP8-05 → WP8-06; WP8-08 độc lập với quy trình; WP8-07 hoãn.

## WP8-01: Họ mẫu có tham số

- Mục tiêu: mô hình chọn họ mẫu và tham số; không còn mẫu viết cứng.
- Hướng làm:
  - `ProcessFamily` mô tả họ mẫu: trạng thái và hành động; tham số (`handler`, `allow_self_decision`, `deadline`) nằm ở khối `process:` của thực thể.
  - `ModelParser` đọc `family`, `handler`, `allow_self_decision`, `deadline` và báo lỗi đúng dòng.
  - Mỗi hành động có thể xuất phát từ nhiều trạng thái.
- Họ mẫu của phase:
  - `approval`: nộp, duyệt, từ chối, trả lại để bổ sung, nộp lại, rút lại.
  - `work_item`: tiếp nhận, nhận xử lý, xử lý xong, xác nhận hoặc mở lại, từ chối, rút lại.

## WP8-02: Quy tắc bất biến

- Mục tiêu: một lớp duy nhất giữ các quy tắc chung, mọi họ mẫu cùng chịu.
- Hướng làm:
  - `ProcessRules` gom kiểm tra vai, quyền tự quyết, người nhận việc và lý do khỏi `DataService`.
  - Từ chối và trả lại bắt buộc có lý do; rút lại cho phép ghi lý do.
  - Rút lại là hành động của người nộp khi bản ghi chưa ở trạng thái cuối; bản ghi chuyển sang `withdrawn` và được giữ lại.
  - Xóa bản ghi có quy trình bị từ chối, kèm hướng dẫn rút lại.
  - Trạng thái chỉ được ghi qua `RecordStatus`.

## WP8-03: Quy trình trong kế hoạch áp dụng

- Mục tiêu: kế hoạch cho thấy hết ảnh hưởng của quy trình trước khi áp dụng.
- Hướng làm:
  - `ModelDiff` so sánh họ mẫu, người xử lý, hạn và quyền tự quyết.
  - Đổi người xử lý hoặc hạn là thay đổi an toàn; đổi họ mẫu đi theo WP8-07.
  - Kế hoạch báo số bản ghi đang chạy của thực thể bị đụng tới.
  - Kiểm lúc áp dụng: người xử lý là vai có thật và có quyền xem bản ghi.

## WP8-04: Họ công việc cần xử lý có hạn

- Mục tiêu: họ mẫu thứ hai kiểm chứng lớp quy trình chung.
- Hướng làm:
  - Bản ghi lưu `handle_by` khi vào bước chờ người xử lý, tính từ tham số `deadline`.
  - Hồ sơ quá hạn được tính khi đọc (`overdue`), hộp việc đưa lên đầu; không cần bộ đếm giờ riêng.
  - Việc nhắc quá hạn phát sự kiện ở WP8-05.

## WP8-05: Trục sự kiện và hành động hoàn tất

- Mục tiêu: phản ứng sau khi duyệt mà không làm hỏng giao dịch.
- Hướng làm:
  - Bảng outbox ghi sự kiện trong giao dịch của thay đổi, bộ giao đọc và thử lại.
  - Hành động hoàn tất chỉ gọi các thao tác đã đăng ký, có khóa chống chạy lặp.
  - Sự kiện quá hạn do một tác vụ định kỳ quét `handle_by` phát ra.

## WP8-06: Mô hình cấp phát dịch vụ IT

- Mục tiêu: chứng minh tính trung lập trên chuỗi duyệt với hành động hoàn tất.
- Hướng làm: một tệp mô hình ngắn; duyệt xong thì thao tác đã đăng ký thêm người dùng vào nhóm quyền Keycloak.

## WP8-07: Đổi họ mẫu khi có hồ sơ đang chạy

- Không làm trong phase này: đổi họ mẫu của một thực thể bị từ chối kèm giải thích.
- Việc đổi cần bảng ánh xạ trạng thái và chuyển instance Flowable giữa hai quy trình khác khóa; xem `docs/dynamic-core/BACKLOG.md`.

## WP8-08: Tệp đính kèm

- Mục tiêu: dịch vụ dữ liệu phi cấu trúc nối vào mô hình.
- Hướng làm: kiểu trường `file`, tải lên qua `dx-core/storage`, quét ClamAV khi bật, tệp gắn với bản ghi và quyền theo dòng.

## WP8-09: Kiểm chứng

- Mục tiêu: bằng chứng cho từng tiêu chí ở `00_OVERVIEW.md` mục 4.
- Hướng làm: script ở `deploy/dev`, chạy trên stack sạch, kiểm giao diện.

## Thay đổi hợp đồng

| Thay đổi | Trước | Sau |
|---|---|---|
| Khối `process:` của tệp mô hình | `template`, `approver`, `allow_self_decision` | `family`, `handler`, `allow_self_decision`, `deadline` |
| Metadata của thực thể | `process.template`, `process.approver`, `action.from` là chuỗi | `process.family`, `process.handler`, `process.deadline`, `action.from` là danh sách |
| Hành động của quy trình | `approve`, `reject`, `return`, `resubmit` | thêm `withdraw`; `reject` và `return` bắt buộc có lý do |
| Xóa bản ghi có quy trình | Cho phép khi chưa ở trạng thái cuối | Từ chối, dùng `withdraw` |
| Bản ghi có quy trình | `status`, `assignee`, `decided_by`, `decided_at` | thêm `handle_by` khi mô hình khai báo hạn |

## Quyết định thiết kế

- Quy tắc bất biến nằm trong Java vì tệp BPMN chỉ lo thứ tự các bước.
- Hạn lưu ở `handle_by` và quá hạn tính khi đọc, nên xác định được ngay cả khi lõi vừa khởi động lại.
