# Trade-off và rủi ro: Phase 1

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Lớp biên và log truy cập | A: giữ APISIX. B: thêm HAProxy phía trước | A | Không thêm container, không thêm giấy phép GPL vào SBOM | `stick-table` của HAProxy để tự chặn IP | Thấp |
| TO2 | Nơi lưu log truy cập | A: `json-file` của Docker với xoay vòng. B: volume và `logrotate`. C: Loki và Grafana | A | Không thêm thành phần, chạy được ở LAN và VPS | Không đảm bảo thời hạn giữ, truy vấn bằng `jq` | Thấp |
| TO3 | Cách lấy IP thật | A: `real_ip_header` trong `config.yaml` dùng chung. B: chỉ đặt trong overlay Cloudflare | A | Một cấu hình, an toàn ở LAN vì nguồn ngoài dải tin cậy bị bỏ qua | Phải giữ `real_ip_from` đúng dải Docker | Thấp |
| TO4 | Backend dữ liệu cho audit | A: giữ Postgres hiện có. B: Supabase | A | Không thêm hệ thống đăng nhập và lưu trữ thứ hai | Không có Studio và API tự sinh | Cao |

Kết luận (đề xuất, chờ người dùng chốt): chấp nhận TO1 tới TO4; HAProxy chỉ xét lại nếu bỏ Cloudflare Tunnel và mở thẳng cổng 80 và 443.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Dải Docker của máy chủ nằm ngoài `172.16.0.0/12`, `real_ip_from` không khớp | Thấp | Vừa | Kiểm subnet bằng `docker network inspect`, ghi vào `OPERATIONS.md` | `remote_addr` vẫn là IP container | Bổ sung dải đúng hoặc cố định subnet compose |
| R2 | Request từ chính máy chủ (bridge gateway) nằm trong dải tin cậy nên giả được `CF-Connecting-IP` | Thấp | Thấp | Chỉ người có shell trên máy chủ mới làm được | Log có IP lạ từ `127.0.0.1` | Thu hẹp `real_ip_from` về subnet của mạng compose |
| R3 | Khóa tạm làm kẹt tài khoản demo khi chấm | Vừa | Vừa | Thời gian chờ ngắn (60 giây, tăng dần, tối đa 15 phút), ghi chú trong tài liệu demo | Đăng nhập báo tài khoản bị tạm khóa | Gỡ khóa trong Admin Console hoặc tắt brute force |
| R4 | `KC_PROXY_HEADERS` sai làm Keycloak dựng sai địa chỉ chuyển hướng | Thấp | Cao | Chỉ bật trong overlay, nơi `KC_HOSTNAME` cố định địa chỉ; kiểm đăng nhập web và mobile sau khi đổi | Chuyển hướng về `localhost` hoặc `http` | Bỏ biến, chấp nhận IP của APISIX trong sự kiện |
| R5 | Log đầy đĩa | Thấp | Vừa | `max-size` và `max-file` ở mọi service | `df -h` tăng | Giảm giới hạn, dọn log |

## 3. Nợ kỹ thuật chấp nhận

- Chưa có công cụ truy vấn log tập trung (Loki, Grafana); đưa vào `docs/BACKLOG.md` nếu cần.
- Chưa gắn `sub` của JWT vào access log; truy vết người dùng đi qua `request_id` và `audit_log` ở phase 3.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: đăng nhập web hoặc mobile hỏng sau khi đổi `KC_PROXY_HEADERS`, hoặc gateway không khởi động vì cấu hình.
- Rollback: bỏ các khối đã thêm ở `config.yaml`, `common.yaml`, `docker-compose.yml`, tạo lại container `apisix` và `keycloak`; sự kiện đã ghi trong database Keycloak giữ nguyên.

## 5. Tác động ngoài phase

- Lên các phase sau: phase 2 và phase 3 dựa vào `remote_addr` và `X-Request-Id`.
- Lên core và các app khác (Farm, Lab): không đụng mã.
- Lên demo, tài liệu nộp bài, thời hạn: thêm một mục "giám sát và audit" cho báo cáo kỹ thuật.
