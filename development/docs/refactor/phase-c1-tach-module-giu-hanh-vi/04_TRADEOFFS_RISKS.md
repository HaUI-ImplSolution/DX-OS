# Trade-off và rủi ro: Phase C1

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Thứ tự tách và tổng quát hóa | A: tách cơ học ở C1 rồi tổng quát hóa ở C2. B: làm cùng lúc | A | Diff dễ đọc; so được với baseline; test hiện có là lưới an toàn | Hai đợt chạm cùng file (đổi tên `Household` ở C2) | Thấp |
| TO2 | Schema DB (RD9) | A: giữ `public`, tách Flyway theo location và bảng lịch sử. B: schema `core`, `farm`, `lab` | A | Không phải đổi view `vw_*`, quyền Superset, cấu hình JPA | Bảng của các app cùng namespace; chỉ ổn khi mỗi lần chạy một app | Vừa: chuyển schema về sau cần migration và sửa view |
| TO3 | Re-baseline migration | A: bỏ backfill, đánh số lại. B: giữ V1 đến V9 và để core phụ thuộc bảng Farm | A | Core không đọc bảng của app; số phiên bản sạch | DB đã có không áp dụng được, phải dựng lại | Thấp (DB luôn dựng sạch bằng `deploy/reset.sh`) |
| TO4 | Quan hệ `Parcel` và nông hộ | A: app giữ id số, tra qua `WorkspaceDirectory`. B: chia sẻ entity qua `dx-core` | A | Hai module không chia sẻ entity | Mất quan hệ JPA; truy vấn lọc theo nông hộ viết lại theo id | Vừa |
| TO5 | `ApprovalRequestService` ở C1 | A: chuyển nguyên khối sang Farm rồi tách ở C2. B: tách ngay | A | Giữ hành vi, hoàn thành ranh giới nhanh | Core tạm thời chưa có hộp duyệt chung | Thấp |

Kết luận: TO1 đến TO5 áp dụng ngay; TO2 đã chốt ngày 09/10/2026 (RD9).

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Di chuyển hàng trăm file làm lỗi compile dây chuyền, khó định vị | Vừa | Vừa | Chuyển theo nhóm nhỏ, compile sau mỗi nhóm; `git mv` để theo dõi | Số lỗi compile tăng sau mỗi nhóm | Quay lại nhóm trước, chia nhỏ hơn |
| R2 | Facade thiếu hàm, buộc Farm import `dx-core` | Vừa | Vừa | Lập danh sách hàm Farm đang gọi trước khi viết facade (số lượt dùng ở `01_PLAN.md`) | Import `core.*` còn sót khi tìm kiếm | Bổ sung hàm vào facade, không mở `dx-core` |
| R3 | Bộ test hiện có đổi hành vi khi đổi cấu trúc (Spring bean, quét package, Testcontainers) | Vừa | Cao | Chuyển test cùng module; chạy `verify` ở cuối mỗi WP | Test fail không liên quan đến logic | Dừng, sửa cấu hình quét và context test |
| R4 | Auto-configuration có điều kiện làm entity hoặc repository không được quét | Vừa | Cao | Giới hạn quét theo package app; kiểm tra khởi động | `No qualifying bean` hoặc bảng không tồn tại khi chạy | Khai báo `@EntityScan`, `@EnableJpaRepositories` rõ cho từng app |
| R5 | Thứ tự Flyway sai, migration Farm chạy trước core | Thấp | Cao | Core chạy trước bằng cấu hình rõ | Lỗi `relation households does not exist` | Thêm ràng buộc thứ tự khởi tạo |
| R6 | Cấu hình bảo mật tách đôi gây hở hoặc chặn nhầm endpoint | Thấp | Cao | Giữ nguyên quy tắc hiện có; đối chiếu ma trận quyền trước và sau | Test phân quyền fail; endpoint công khai bị 401 | Đưa cấu hình về một chỗ trong C1, tách ở C2 |

## 3. Nợ kỹ thuật chấp nhận

- `Household`, `NotificationType`, `WorkflowStatus` giữ nguyên tên và kiểu; C2 xử lý.
- `SecurityConfig` còn biết endpoint công khai của Farm qua cấu hình bổ sung; C2 tách bằng interface.
- Bảng `notifications` còn ở migration Farm; C2 chuyển về core.
- Hộp duyệt (`ApprovalRequestService`) tạm nằm ở Farm.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: OpenAPI lệch baseline ngoài thông tin đóng gói, hoặc test hiện có giảm số lượng.
- Rollback: quay lại nhánh trước phase; DB dựng lại bằng `deploy/reset.sh`.

## 5. Tác động ngoài phase

- Lên các phase sau: C2 đổi tên và tổng quát hóa trên ranh giới này; C3 và C4 cần `dx-host`.
- Lên demo, tài liệu nộp bài, thời hạn: đường dẫn build đổi, README và CONTRIBUTING phải khớp trước khi demo.
