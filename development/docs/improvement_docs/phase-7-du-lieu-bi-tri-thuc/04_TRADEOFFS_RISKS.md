# Trade-off và rủi ro: Phase 7

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Nền bản đồ | Chọn một phương án có chi phí/rủi ro nhãn (Mapbox/OSM/Google) / Giữ nền trắng đã chọn | Giữ nền trắng | Không tốn chi phí, không dính rủi ro nhãn tranh chấp Hoàng Sa/Trường Sa đã phân tích kỹ trong `bi/README.md` | Bản đồ kém trực quan hơn khi demo, phải giải thích nếu hội đồng hỏi | Thấp — đổi nền bản đồ là việc cấu hình chart, không ảnh hưởng dữ liệu |
| TO2 | Bảng mới trong các phase sau có nên tự động cấp quyền `superset_ro` không | Giữ `ALTER DEFAULT PRIVILEGES` (tự động cấp cho mọi bảng mới) / Bỏ, cấp quyền tường minh từng bảng | Bỏ, cấp theo danh sách trong seeder | Bảng mới đóng sẵn | Mỗi bảng danh mục mới phải thêm tay vào danh sách | Thấp — thêm tên vào danh sách, không mất dữ liệu |

Kết luận: TO1-TO2 áp dụng ngay, quy ước thêm view và danh sách quyền nằm trong `bi/README.md`.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | REVOKE quyền trên bảng gốc (WP7-01) làm dashboard cũ gãy ngay nếu chưa kịp đổi dataset sang view | Vừa | Cao (mất demo BI tạm thời) | Đổi dataset và export dashboard mới trong cùng một đợt làm với REVOKE, không tách 2 việc ra 2 lần merge khác nhau | Dashboard báo lỗi "permission denied" ngay sau khi chạy migration | Rollback migration (GRANT lại tạm thời) trong lúc sửa dataset, không để demo bị gãy |
| R2 | View mới lọc sai điều kiện "đã duyệt" (ví dụ quên lọc theo `approvalStatus` của cả `Parcel` và `CropSeason` cùng lúc), làm lộ dữ liệu chưa duyệt qua view | Thấp | Vừa | Test T7-02 xác nhận view chỉ trả bản ghi đúng trạng thái; review kỹ điều kiện `WHERE` khi code | Dashboard hiện thửa/vụ còn ở trạng thái chờ duyệt | Sửa lại điều kiện view, không cần migration cấu trúc (chỉ đổi nội dung view) |
| R3 | Open-Meteo đổi API (breaking change) hoặc giới hạn rate limit bất ngờ, ảnh hưởng demo trực tiếp | Thấp | Thấp | Luôn có fallback snapshot (WP7-04); không phụ thuộc Open-Meteo là tính năng bắt buộc cho luồng demo chính | Gọi API trả lỗi khác thường (không phải timeout thông thường) | Chuyển hẳn sang dùng snapshot cho buổi demo, cập nhật code gọi API sau |

## 3. Nợ kỹ thuật chấp nhận

- Nền bản đồ vẫn là nền trắng (TO1) — giới hạn đã biết, không có giải pháp miễn phí hoàn chỉnh trong phạm vi dự án.
- Danh sách quyền `superset_ro` (TO2) cập nhật thủ công; quên thêm view thì chart báo `permission denied` chứ không lộ dữ liệu.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: R1 xảy ra trong lúc test (dashboard gãy ngay sau REVOKE) — rollback migration ngay, không để tình trạng này tồn tại qua một lần merge.
- Rollback: `GRANT` lại quyền cũ trên bảng gốc là một câu lệnh SQL đơn giản, không mất dữ liệu; view có thể xoá và tạo lại bất kỳ lúc nào.

## 5. Tác động ngoài phase

- Lên các phase sau: dữ liệu mới muốn lên BI phải qua view và được thêm vào danh sách quyền theo quy ước ở `bi/README.md`.
- Lên demo, tài liệu nộp bài, thời hạn: dashboard và trang hỏi đáp là bằng chứng trực tiếp cho phần D và I của H-P-D-I trong báo cáo/slide (P8).
