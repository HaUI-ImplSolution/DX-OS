# Kế hoạch kiểm thử: Phase 4

Kiểm tra phase đã đạt tiêu chuẩn và chạy đúng kỳ vọng chưa.
Quy ước dự án: mặc định chỉ compile, đọc diff.
Chạy test, khởi động app hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ, ghi rõ ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: compile toàn bộ module, thủ công trên stack local và VPS (kịch bản tấn công giả lập), bảo mật (quyền đọc của role, vai ADMIN, chèn mã), E2E có sẵn (hồi quy).
- Môi trường: clone sạch + `docker compose up -d --build` + seed demo; thêm một lượt trên VPS qua Cloudflare Tunnel để có IP thật.
- Chạy lại với `DXOS_APP=farm` và `DXOS_APP=lab` (T4-03); phần còn lại chạy một lần với Farm.
- Dữ liệu và tài khoản test: tài khoản demo ở `sso/README.md`; `ADMIN` (`admin1`), `ENGINEER` (`engineer1`), một `FARMER`; một IP thứ hai (máy khác trong LAN hoặc header `CF-Connecting-IP` giả lập từ máy chạy stack).
- Công cụ kịch bản: `curl` hoặc `hey` cho xả request, `docker compose stop minio backend` để tạo lỗi.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T4-01 | WP4-01 | Happy path | Dừng MinIO, gọi API tải ảnh báo cáo bằng token hợp lệ | Response `5xx`; `GET /api/v1/audit-logs?action=SERVER_ERROR` có một bản ghi `FAILED` với `request_id` bằng header `X-Request-Id`, `metadata` chỉ có tên lớp ngoại lệ | Khuong | |
| T4-02 | WP4-01 | Âm tính | ADMIN tạo workspace trùng mã, xóa mềm thửa đã xóa (Farm), tạo danh mục trùng (Lab) | Mỗi thao tác một bản ghi `FAILED` của đúng hành động; không bản ghi `SUCCESS` | Khuong | |
| T4-03 | WP4-02 | Ranh giới | Khởi động clone sạch với `DXOS_APP=farm` rồi `lab` (`./switch-app.sh lab`) | `monitor-init` xong, `monitor` healthy, migration `V1` của monitor chạy; core không đổi; `/monitor/` đi qua gateway | Khuong | |
| T4-04 | WP4-02 | Phân quyền | `psql` bằng `monitor_app`: đọc `audit_log`, `monitor.*`; đọc `workspaces`. `psql` bằng `keycloak_ro`: đọc `vw_auth_events`; đọc `credential`, `user_entity`, `event_entity` | `audit_log` và `monitor.*` đọc được, `workspaces` bị từ chối; `vw_auth_events` đọc được, ba bảng còn lại `permission denied` | Khuong | |
| T4-05 | WP4-03 | Happy path | Mở `/monitor/` chưa đăng nhập, rồi đăng nhập `admin1` | Chuyển sang Keycloak, đăng nhập xong vào trang; đã đăng nhập web app thì vào thẳng | Khuong | |
| T4-06 | WP4-03 | Phân quyền | `engineer1`, `farmer1` mở `/monitor/` và các API `/monitor/api/*` | 403 tiếng Việt, phiên bị hủy; API không trả dữ liệu | Khuong | |
| T4-07 | WP4-04 | Happy path | Từ một IP xả 700 request tới `/api/*` trong một phút | Có dòng `RATE_LIMITED` ở `monitor.gateway_event` với IP thật | Khuong | |
| T4-08 | WP4-04 | Âm tính | Gọi `GET /api/v1/system/status` nhiều lần; để health check chạy | Không có dòng nào cho `2xx` và cho `127.0.0.1` | Khuong | |
| T4-09 | WP4-04 | Bền dữ liệu | `docker compose up -d --force-recreate apisix` sau T4-07 | Dữ liệu `gateway_event` còn nguyên | Khuong | |
| T4-10 | WP4-04 | Phân quyền | Gọi `POST /monitor/ingest/gateway` (a) không khóa, (b) khóa sai, (c) từ ngoài qua gateway công khai | (a) và (b) bị từ chối; (c) `404`; khóa đúng từ trong mạng Docker thì `2xx` | Khuong | |
| T4-11 | WP4-04 | Lỗi dịch vụ ngoài | Dừng `monitor` 30 giây, gửi request qua gateway, bật lại | Người dùng không thấy khác thường, gateway không treo; sau khi `monitor` lên lại vẫn nhận sự kiện mới | Khuong | |
| T4-12 | WP4-05 | Happy path | Dừng MinIO 1 phút, bật lại | Hai dòng `service_health_event` của `storage` (`DOWN` rồi `UP`), không có dòng lặp khi trạng thái giữ nguyên; một lần dò chậm không sinh `DOWN` | Khuong | |
| T4-13 | WP4-05 | Độc lập | Dừng `backend` 1 phút | `/monitor/` vẫn mở được, `backend` hiện `DOWN` kèm thời gian ngừng; bật lại hiện `UP` | Khuong | |
| T4-14 | WP4-06 | Happy path | Chạy kịch bản: gọi API sai quyền 15 lần, xả 700 request, đăng nhập sai 6 lần; gọi `/monitor/api/summary?window=1h` | Bốn con số khớp kịch bản (từ chối ≥ 15, `429` > 0, đăng nhập sai ≥ 6) | Khuong | |
| T4-15 | WP4-06 | Happy path | Gọi `/api/timeline`, `/api/suspects`, `/api/admin-actions` sau T4-14 | Cột theo giờ có đỉnh đúng giờ; IP thử đứng đầu; thao tác quản trị gần đây có cả `FAILED` | Khuong | |
| T4-16 | WP4-06 | Truy vết | Lấy `X-Request-Id` của một request bị `403`, gọi `/api/trace?requestId=...` | Có dòng của gateway và dòng `ACCESS_DENIED` của backend, cùng IP, đúng thứ tự thời gian | Khuong | |
| T4-17 | WP4-06 | Âm tính | `/api/trace?requestId=' or 1=1 --`, `window=1y`, `ip=abc`, `/api/summary` khi Keycloak dừng | `400` cho tham số sai định dạng; không rò dữ liệu; khi Keycloak dừng chỉ ô đăng nhập sai báo thiếu dữ liệu | Khuong | |
| T4-18 | WP4-07 | Demo đầu cuối | ADMIN mở portal → "Giám sát", chạy kịch bản tấn công và dừng backend 1 phút | Mỗi khối có dữ liệu khớp kịch bản trong tối đa 30 giây; bấm IP đáng ngờ mở được truy vết | Khuong | |
| T4-19 | WP4-07 | Bảo mật | Gửi request tới đường dẫn chứa `<script>alert(1)</script>` rồi mở trang giám sát | Chuỗi hiện nguyên văn, không có script chạy; CSP không báo vi phạm của trang | Khuong | |
| T4-20 | WP4-07 | Hồi quy portal | Mở portal | Có thẻ "Giám sát" và dòng trạng thái; các link và dòng trạng thái cũ vẫn đúng | Khuong | |
| T4-21 | WP4-08 | Happy path | Gây 5 lỗi `5xx` trong 5 phút, xả request cho `429`, rồi dừng MinIO | Kênh "Ban quản trị" nhận một tin cho mỗi loại; bật lại nhận tin phục hồi; không lặp quá một tin mỗi 60 phút cho cùng điều kiện | Khuong | |
| T4-22 | WP4-08 | Dọn dữ liệu | Đặt `monitor.retention-days` nhỏ trong môi trường thử, chờ job chạy | Bản ghi cũ bị xóa; `audit_log` không đổi | Khuong | |
| T4-23 | WP4-04 | Hiệu năng | Xả 5.000 request lỗi từ một IP | Mỗi IP không quá 100 bản ghi mỗi phút (phần dư vào `suppressed`); thời gian phản hồi API và trang giám sát không tăng đáng kể | Khuong | |

_Các case chạy runtime do người dùng thực hiện; Claude chỉ chạy `./mvnw compile` khi được phép._

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động; Superset vẫn từ chối tài khoản `FARMER`.
- [ ] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [ ] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409).
- [ ] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Thông báo trong app và Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ.
- [ ] Gửi lại yêu cầu với `Idempotency-Key` cũ không tạo bản ghi trùng.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với từng giá trị `DXOS_APP`.
- [ ] `audit_log` vẫn chỉ thêm (`UPDATE` và `DELETE` bị trigger từ chối); `ACCESS_DENIED` và `AUTH_FAILED` vẫn ghi như trước.
- [ ] Giới hạn tốc độ của gateway (120 đăng nhập, 600 API mỗi phút) vẫn hoạt động; `http-logger` không làm tăng số request bị đếm.
- [ ] E2E Farm 10/10 và Lab 16/16 vẫn đạt.
- [ ] Cảnh báo `ACCESS_DENIED` theo IP của `AuditAlertJob` vẫn gửi đúng một tin.
- [ ] Cloudflare Access cho `bi.`, `files.` và `admin/*` của Keycloak vẫn hoạt động.

**Theo app**

- Farm:
  - [ ] Khóa thu hoạch theo PHI vẫn chặn đúng (HTTP 422), lỗi này không sinh `SERVER_ERROR`.
  - [ ] Farmer chỉ thấy thửa và báo cáo của mình, chẩn đoán chưa duyệt bị ẩn.
  - [ ] Chẩn đoán AI mất khoảng 50 giây không sinh sự kiện `SLOW`.
- Lab:
  - [ ] Tạo ticket, đặt phòng, mượn thiết bị vẫn chạy; lỗi `409` hoặc `422` nghiệp vụ không sinh `SERVER_ERROR`.

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case T4-01 đến T4-23 đạt, không còn lỗi nghiêm trọng mở.
- Lỗi còn lại đã có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: ảnh màn hình giám sát (đã che IP và email thật), kết quả `psql` các case phân quyền, tin Mattermost; lưu ở `docs/images/` hoặc ghi vào `docs/TESTING.md` mục kết quả.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
