# Plan thực hiện: Phase 1

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP1-01 → WP1-02 → WP1-04.
WP1-03 làm song song với WP1-02, nhưng cần WP1-01 để sự kiện Keycloak có IP thật.

## WP1-01: IP thật của client tại gateway

- Mục tham chiếu: góp ý bảo vệ server, `deploy/docker-compose.cloudflared.yml`.
- Mục tiêu: APISIX dùng `CF-Connecting-IP` làm địa chỉ client khi request đến từ mạng Docker.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Request qua Cloudflare Tunnel | `remote_addr` là IP container `cloudflared` | `remote_addr` là IP của người dùng |
| Request từ LAN tới cổng 9080 | `remote_addr` là IP máy trong LAN | Không đổi |
| Request từ LAN có header `CF-Connecting-IP` giả | Không áp dụng | Header bị bỏ qua vì nguồn không thuộc dải tin cậy |
| Bộ đếm `limit-count` theo `remote_addr` | Mọi người dùng chung một bộ đếm khi đi qua tunnel | Mỗi IP một bộ đếm |

**Hướng làm**

- `deploy/apisix/config.yaml`: `nginx_config.http.real_ip_header: CF-Connecting-IP`, `nginx_config.http.real_ip_from: [127.0.0.1, "unix:", 172.16.0.0/12]`.
- Một cấu hình dùng chung cho LAN và Cloudflare, không cần overlay riêng.
- Core/App: không đụng mã.

**Phạm vi ảnh hưởng**

- Module: Infra.
- File chính: `deploy/apisix/config.yaml`.
- Endpoint, bảng DB, biến môi trường: không.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: các giới hạn tốc độ hiện có đổi từ "dùng chung" sang "theo IP", đúng với ý định ban đầu.

**Kết quả đầu ra**

- `remote_addr` trong access log là IP người dùng khi vào qua tunnel, và là IP LAN khi vào trực tiếp.
- Header `CF-Connecting-IP` giả từ ngoài dải tin cậy không có tác dụng.

**Phụ thuộc, lưu ý**: Q2 ở `00` mục 9; rủi ro R1 ở `04`.

## WP1-02: Access log JSON, `X-Request-Id` và xoay vòng log

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: mỗi request có một dòng log JSON truy vết được, log của mọi service có giới hạn dung lượng.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Đọc log gateway | Dòng văn bản mặc định của nginx | Một dòng JSON mỗi request |
| Truy vết một request qua gateway và backend | Không có khóa chung | `X-Request-Id` do gateway sinh, chuyển tới upstream và trả về client |
| Dung lượng log | Không giới hạn | Mỗi service có `max-size` và `max-file` |

**Hướng làm**

- `config.yaml`: `nginx_config.http.access_log_format` dạng JSON với `access_log_format_escape: json`.
- Trường: `time_iso8601`, `remote_addr`, `request_method`, `uri` (không kèm query, để không lưu `code` và `state` của OIDC), `status`, `request_time`, `upstream_response_time`, `bytes_sent`, `host`, `user_agent`, `request_id`, `upstream_addr`, `cf_ray`.
- `common.yaml`: thêm `global_rules` với plugin `request-id` (header `X-Request-Id`, trả về trong response).
- `docker-compose.yml`: khối `x-logging` với `json-file`, `max-size: 20m`, `max-file: "5"`, riêng `apisix` `100m` và `10`; áp cho mọi service.
- Core/App: không đụng mã.

**Phạm vi ảnh hưởng**

- Module: Infra.
- File chính: `deploy/apisix/config.yaml`, `deploy/apisix/common.yaml`, `deploy/docker-compose.yml`.
- Endpoint, bảng DB, biến môi trường: không.
- Tương thích ngược và dữ liệu cũ: log cũ không còn khi container được tạo lại; không ảnh hưởng nghiệp vụ.

**Kết quả đầu ra**

- `docker compose logs apisix --tail 5` in ra JSON hợp lệ, mỗi dòng có `request_id`.
- `docker compose config` cho thấy `logging` ở mọi service.

**Phụ thuộc, lưu ý**: Q1 ở `00` mục 9; không ghi `Authorization`, cookie hay query string vào log.

## WP1-03: Sự kiện và khóa tạm của Keycloak

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: biết ai đăng nhập, thất bại hay đổi quyền, từ IP nào, và làm chậm việc đoán mật khẩu.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Đăng nhập thành công hoặc thất bại | Không ghi sự kiện | Ghi `LOGIN`, `LOGIN_ERROR`, `LOGOUT`, `UPDATE_PASSWORD`, giữ 90 ngày |
| Thao tác quản trị trong Keycloak | Không ghi | Ghi admin event kèm chi tiết |
| Đoán mật khẩu liên tục | Không giới hạn | Khóa tạm tài khoản sau 5 lần sai, tăng dần thời gian chờ, không khóa vĩnh viễn |
| IP trong sự kiện | IP container APISIX | IP thật của client |

**Hướng làm**

- `sso/realm-dxos.json`: `eventsEnabled`, `eventsExpiration` (7776000 giây), `enabledEventTypes`, `eventsListeners: ["jboss-logging"]`, `adminEventsEnabled`, `adminEventsDetailsEnabled`, `bruteForceProtected`, `failureFactor: 5`, `permanentLockout: false`.
- `docker-compose.cloudflared.yml`: `KC_PROXY_HEADERS: xforwarded` cho Keycloak; chỉ đặt trong overlay, nơi `KC_HOSTNAME` cố định địa chỉ công khai, để chạy LAN không đổi cách Keycloak dựng URL.
- `common.yaml`: route Keycloak ghi đè `X-Forwarded-For` bằng `$remote_addr` (IP thật sau WP1-01) để giá trị đầu chuỗi không bị client giả.
- Keycloak đang chạy: áp cấu hình ở Realm settings → Events và Security defenses, ghi các bước ở `sso/README.md`.

**Phạm vi ảnh hưởng**

- Module: SSO, Infra.
- File chính: `sso/realm-dxos.json`, `deploy/docker-compose.cloudflared.yml`, `deploy/apisix/common.yaml`, `sso/README.md`.
- Endpoint, bảng DB, biến môi trường: bảng `event_entity` và `admin_event_entity` trong database Keycloak; không có biến mới.
- Role và client bị ảnh hưởng: mọi người dùng đăng nhập qua Keycloak; tài khoản demo có thể bị khóa tạm nếu nhập sai nhiều lần.
- Tương thích ngược và dữ liệu cũ: realm chỉ import lần đầu, nên volume cũ phải áp tay.

**Kết quả đầu ra**

- Đăng nhập sai ba lần rồi đúng tạo ba sự kiện `LOGIN_ERROR` và một `LOGIN`, đều có IP thật.
- Sau 5 lần sai, đăng nhập đúng bị từ chối trong thời gian chờ.

**Phụ thuộc, lưu ý**: WP1-01; Q3 ở `00` mục 9; rủi ro R3 ở `04`.

## WP1-04: Tài liệu vận hành và cách xem log

- Mục tham chiếu: `docs/BACKLOG.md`.
- Mục tiêu: người vận hành tìm được một request hoặc một người dùng trong log trong dưới 2 phút.
- Ước lượng: S.

**Hướng làm**

- Tạo `deploy/OPERATIONS.md`: vị trí log, ví dụ `docker compose logs apisix --since 1h | jq 'select(.status>=400)'`, cách đọc sự kiện Keycloak trong Admin Console, thời gian giữ.
- Không thêm công cụ gom log.

**Phạm vi ảnh hưởng**

- File chính: `deploy/OPERATIONS.md`, `CHANGELOG.md`.

**Kết quả đầu ra**

- Các ví dụ lệnh trong tài liệu chạy được trên log thật.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Cấu hình gateway | `real_ip_header`, `real_ip_from`, `access_log_format` | Mới | Không |
| Header | `X-Request-Id` | Gateway sinh và trả về | Không |
| Biến môi trường | `KC_PROXY_HEADERS` (Keycloak, chỉ trong overlay Cloudflare) | `xforwarded` | Không |
| Realm | `eventsEnabled`, `bruteForceProtected` | Bật | Không |
| Compose | `logging` | Thêm cho mọi service | Không |

## Dữ liệu, migration và rollback

- Migration: không có migration backend; Keycloak tự tạo bảng sự kiện.
- Dữ liệu cũ: không đổi.
- Rollback: bỏ các khối cấu hình thêm vào `config.yaml`, `common.yaml`, `docker-compose.yml`; tắt sự kiện trong Admin Console; mất log đã ghi sau khi container được tạo lại.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Không thêm HAProxy | APISIX đã là gateway, HAProxy cũng chỉ thấy IP `cloudflared`; xem `04` |
| D2 | Không dùng Supabase | Trùng Keycloak, MinIO, backend; không giải quyết log và audit |
| D3 | Không ghi query string vào log | Query của OIDC chứa `code` và `state` |
| D4 | Khóa tạm, không khóa vĩnh viễn | Khóa vĩnh viễn cho phép kẻ tấn công khóa tài khoản người khác |
