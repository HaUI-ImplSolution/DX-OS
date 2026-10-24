# Trade-off và rủi ro: Phase 4

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Nơi hiển thị giám sát | A: dashboard Superset. B: màn hình riêng trong dịch vụ Spring Boot mới. C: thêm vào backend và web app | B | Còn chạy khi backend sập; kiểm soát hiển thị và phân quyền; không phụ thuộc bundle dựng tay | Phải tự viết giao diện và một container mới | Vừa: dữ liệu đã nằm trong Postgres nên thêm Superset hoặc Grafana sau vẫn đọc được |
| TO2 | Vị trí mã | A: module `backend/dx-monitor`. B: thư mục cấp cao riêng | A | Dùng lại parent pom, `./mvnw`, CI, quy ước; vẫn tách jar và container | Chung vòng đời build với backend | Thấp |
| TO3 | Công cụ giám sát | A: Spring Boot + Postgres hiện có. B: Prometheus + Grafana. C: Loki + Grafana | A | Không thêm thành phần ngoài hệ sinh thái, SSO dùng lại, tận dụng `audit_log` và `request_id` | Không có metric tài nguyên và phân vị độ trễ | Vừa |
| TO4 | Phạm vi sự kiện gateway | A: chỉ sự kiện bất thường. B: mọi request | A | Bảng nhỏ, đủ để truy vết tấn công và lỗi | Không có biểu đồ tổng lưu lượng | Thấp: nới bộ lọc ở monitor |
| TO5 | Cách gateway đưa sự kiện vào database | A: `http-logger` gọi monitor. B: sidecar đọc stdout | A (chờ spike) | Không thêm container, khóa riêng, monitor lọc nên đổi ngưỡng không cần sửa gateway | Mất sự kiện nếu monitor ngừng lâu hơn hàng đợi của gateway | Vừa |
| TO6 | Sự kiện Keycloak | A: role `keycloak_ro` + view. B: chép qua Admin API | A | Không chép dữ liệu, không thêm tài khoản dịch vụ trong Keycloak | Phụ thuộc cấu trúc `event_entity` của Keycloak | Thấp |
| TO7 | Thời hạn giữ | A: 30 ngày cho bảng monitor, `audit_log` giữ mãi. B: giữ mãi tất cả | A | Bảng gateway không phình; audit vẫn là chứng cứ lâu dài | Sự kiện gateway cũ hơn 30 ngày không còn | Thấp: đổi `retention-days` |
| TO8 | Xác thực màn hình | A: `oauth2Login` của chính monitor, vai ADMIN. B: bắt buộc thêm Cloudflare Access | A và thêm B (Q7-A) | A đủ và dùng chung phiên SSO | A phải cấu hình đúng URL nội bộ và công khai của Keycloak | Thấp |

Kết luận: chọn các phương án đã đánh dấu; TO5 chốt sau spike WP4-04-INFRA-01, Khuong chốt Q1 đến Q7 (Q6, Q7 theo đề xuất).

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | `http-logger` của APISIX 3.11 standalone không mang được khóa từ môi trường hoặc không gửi `log_format` như cần | Vừa | Vừa | Spike nửa ngày ở WP4-04 trước khi làm phần còn lại | Spike không gửi được batch tới monitor | Phương án B của Q3 (sidecar đọc stdout), hoặc `sed` thay khóa trong `apisix-config` |
| R2 | `monitor-init` chạy sai thứ tự so với Flyway của backend hoặc Keycloak (bảng chưa có), làm `monitor` không khởi động | Vừa | Vừa | `depends_on` `backend` và `keycloak` healthy; script idempotent; `monitor` tự thử lại kết nối | `monitor-init` thoát lỗi `relation does not exist` | Chạy lại `docker compose up -d monitor-init monitor` |
| R3 | `oauth2Login` sau Cloudflare Tunnel sai URL chuyển hướng hoặc issuer (Keycloak không cố định hostname) | Vừa | Vừa | Dùng `forward-headers-strategy`, tách địa chỉ trình duyệt và nội bộ như Superset, không kiểm issuer như backend | Đăng nhập lặp vòng hoặc `redirect_uri` không khớp | Sửa `redirect-uri` theo `GATEWAY_PUBLIC_URL`; Cloudflare Access (Q7-A) là lớp bảo vệ thứ hai trong lúc sửa |
| R4 | Cuộc quét lớn làm đầy `gateway_event` hoặc ghi dồn làm chậm | Vừa | Vừa | Gateway giới hạn 600 request mỗi phút mỗi IP; monitor giới hạn 100 bản ghi mỗi IP mỗi phút; batch; xóa sau 30 ngày | Kích thước bảng tăng nhanh, thời gian ghi tăng | Hạ ngưỡng ghi, tắt `http-logger` bằng cấu hình, giữ log Docker |
| R5 | Dò trạng thái báo nhầm `DOWN` (máy chậm, Ollama nạp mô hình) hoặc đích không thuộc app đang chạy | Vừa | Thấp | `DOWN` sau 2 lần thất bại liên tiếp; `required: false` cho đích tùy chọn; thời gian chờ theo đích | Tin cảnh báo `DOWN` rồi `UP` trong vòng vài chục giây | Tăng thời gian chờ hoặc số lần thất bại |
| R6 | Dữ liệu tấn công (đường dẫn, tên đăng nhập) chèn mã vào trang giám sát | Thấp | Cao | `textContent`, không `innerHTML`; CSP `default-src 'self'`; thư viện biểu đồ nhúng sẵn; chỉ ADMIN xem | Chuỗi `<script>` chạy trong test T4-19 | Gỡ khối hiển thị đường dẫn, rút gọn ký tự được phép |
| R7 | Lộ IP hoặc đường dẫn người dùng qua màn hình | Thấp | Cao | Chỉ ADMIN, tùy chọn sau Cloudflare Access; không trả `actor_user_id`, `metadata` thô; `user_agent` rút gọn; giữ 30 ngày | Tài khoản không phải ADMIN vào được | Gỡ route `/monitor/` ở gateway |
| R8 | Nâng cấp Keycloak đổi cấu trúc `event_entity`, làm `vw_auth_events` hỏng | Thấp | Thấp | `monitor-init` tạo lại view mỗi lần `up`; lỗi chỉ ảnh hưởng ô đăng nhập sai | Ô đăng nhập sai báo thiếu dữ liệu | Chỉnh view, hoặc dùng Admin Console |
| R9 | Bắt `5xx` ở `GlobalExceptionHandler` sinh vòng lặp hoặc làm chậm xử lý lỗi | Thấp | Vừa | Ghi trong giao dịch riêng, lỗi ghi chỉ được log, giới hạn theo IP và phút | Log đầy `Audit write failed` | Tắt ghi `SERVER_ERROR` bằng cấu hình |
| R10 | `monitor` sập cùng lúc backend nên mất cả hai nguồn | Thấp | Thấp | Hai container độc lập; log Docker và `audit_log` vẫn còn; cảnh báo `DOWN` gửi từ monitor, nên monitor sập thì không tự báo | Không có tin `DOWN` dù hệ thống lỗi | Chấp nhận (mục 3); kiểm tay `docker compose ps` |

## 3. Nợ kỹ thuật chấp nhận

- Không có metric tài nguyên (CPU, RAM, đĩa) và phân vị độ trễ: chưa cần để truy vết; thêm Prometheus ở phase sau nếu có nhu cầu (TO3).
- Không có truy vết phân tán Backend → AI: `request_id` chưa được chuyển cho AI service; ghi vào `docs/BACKLOG.md` nếu muốn làm.
- `audit_log` chưa xóa theo thời hạn: bảng chỉ thêm, theo dõi dung lượng ở `deploy/OPERATIONS.md`.
- Monitor tự giám sát chính nó chưa có: không có cảnh báo khi `monitor` ngừng (R10); cần một kiểm tra ngoài (Cloudflare Health Check hoặc uptime monitor miễn phí) nếu muốn.
- Cảnh báo `ACCESS_DENIED` theo IP vẫn nằm ở backend (`AuditAlertJob`), các cảnh báo mới nằm ở `monitor`: hai nơi gửi cảnh báo, ghi rõ trong `deploy/OPERATIONS.md`.
- Lớp gửi Mattermost của `monitor` lặp lại một phần `ChatWebhook` của core vì `dx-monitor` không được phụ thuộc core.
- Sự kiện đăng nhập Keycloak không có cảnh báo Mattermost: khóa tạm của Keycloak và giới hạn tốc độ đã chặn; màn hình để theo dõi.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: spike WP4-04-INFRA-01 cho thấy `http-logger` không dùng được và phương án B tốn hơn 2 ngày; hoặc `oauth2Login` sau Cloudflare Tunnel không chạy ổn sau 1 ngày xử lý; hoặc ghi `gateway_event` làm tăng độ trễ API rõ rệt.
- Rollback: bỏ khối `http-logger` khỏi cấu hình APISIX và chạy `./switch-app.sh <app>`; gỡ service `monitor`, `monitor-init`, route `/monitor/*`, thẻ ở portal; `DROP SCHEMA monitor CASCADE` và `DROP ROLE monitor_app`, `keycloak_ro`; gỡ client `dxos-monitor`.
  Dữ liệu bị ảnh hưởng: chỉ dữ liệu giám sát; `audit_log` và dữ liệu nghiệp vụ không đổi.

## 5. Tác động ngoài phase

- Lên các phase sau: dữ liệu giám sát nằm sẵn trong Postgres, nên thêm Prometheus hay Superset sau này vẫn khai thác được.
- Lên core và các app khác (Farm, Lab): chỉ thêm lời gọi `AuditEvent.failed`; monitor dùng chung cả hai app và không biết app nào đang chạy.
- Lên demo, tài liệu nộp bài, thời hạn: thêm một đoạn demo "bị tấn công thì truy vết thế nào"; cần cập nhật `docs/SUBMISSION.md`, `docs/COUNCIL_QA.md`.
  Phase lớn hơn bản đầu (9 đến 12 ngày); nếu sát hạn nộp bài, rút còn WP4-01, WP4-02, WP4-03, WP4-05, WP4-06, WP4-07 (không có `gateway_event` và cảnh báo mới: chỉ đọc `audit_log`, Keycloak và trạng thái dịch vụ), chuyển WP4-04 và WP4-08 vào `docs/BACKLOG.md`.
