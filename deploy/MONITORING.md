# Giám sát và nhật ký

Tài liệu cho người theo dõi hệ thống: log của gateway, sự kiện đăng nhập, giới hạn tốc độ, audit trong backend và màn hình giám sát.
Mọi request vào stack đi qua gateway APISIX, nên log của gateway là bản ghi truy cập chính.
Sự kiện đăng nhập nằm ở Keycloak.
Cài đặt và các lệnh `dxos` ở [`OPERATIONS.md`](./OPERATIONS.md).

## Log của gateway

Mỗi request là một dòng JSON trên stdout của container `apisix`.

| Trường | Ý nghĩa |
|---|---|
| `time` | Thời điểm, ISO 8601 |
| `remote_addr` | Địa chỉ client |
| `host`, `method`, `uri` | Tên miền, phương thức, đường dẫn (không có query string) |
| `status`, `request_time` | Mã trả về, thời gian xử lý (giây) |
| `upstream_addr`, `upstream_time` | Dịch vụ phía sau xử lý request và thời gian của nó |
| `bytes_sent` | Số byte trả về |
| `request_id` | Mã của request, cũng nằm trong header `X-Request-Id` của response |
| `cf_ray` | Mã request do proxy phía trước gắn vào, trống khi vào trực tiếp |
| `user_agent` | Trình duyệt hoặc ứng dụng gọi |

Backend ghi `request_id` này vào log của nó (trong `[...]` sau mức log).

`remote_addr` là địa chỉ trong header `CF-Connecting-IP` khi một proxy phía trước đặt nó, và là IP của client trong mạng khi vào trực tiếp.
Header chỉ được tin khi request đến từ mạng Docker (`172.16.0.0/12`) hoặc loopback.
Ở máy có dải Docker khác thì sửa `real_ip_from` trong `apisix/config.yaml`.

Log của container còn có dòng không phải JSON (cảnh báo khi khởi động, error log của nginx), nên `jq` đọc từng dòng và bỏ qua dòng lỗi bằng `fromjson?`.
Kiểm tra sức khỏe của container gửi một request từ `127.0.0.1` (đường dẫn `/`, mã 400) mỗi 10 giây.
Các ví dụ dưới loại dòng này.
Ví dụ (cần `jq`, cài bằng `apt install jq` nếu máy chủ chưa có):

```bash
cd deploy
docker compose logs apisix --since 1h --no-log-prefix | jq -cR 'fromjson? | select(.status >= 400 and .remote_addr != "127.0.0.1")'
docker compose logs apisix --no-log-prefix | jq -cR --arg id "<mã từ header X-Request-Id>" 'fromjson? | select(.request_id == $id)'
```

## Giữ log

Docker xoay vòng log theo dung lượng: gateway 10 tệp 100 MB, các dịch vụ khác 5 tệp 20 MB.
Log mất khi container bị tạo lại.
Truy vết lâu dài cần dùng sự kiện của Keycloak (90 ngày) và dữ liệu nghiệp vụ trong database.

## Sự kiện đăng nhập của Keycloak

Admin Console (mở theo [`OPERATIONS.md`](./OPERATIONS.md#keycloak-admin-console)) → realm `dxos` → **Events**:

- **User events**: đăng nhập, đăng nhập sai, đăng xuất, đổi hoặc đặt lại mật khẩu, tài khoản bị khóa; kèm IP và client; giữ 90 ngày.
- **Admin events**: thao tác quản trị trong Keycloak, kèm nội dung thay đổi.

Sau 5 lần đăng nhập sai, tài khoản bị khóa tạm 1 phút, mỗi lần sai tiếp theo tăng thêm 1 phút, tối đa 15 phút.
Hai lần sai cách nhau dưới 1 giây cũng khóa 1 phút (kiểm tra đăng nhập nhanh của Keycloak), nên script đăng nhập thử cần chờ giữa các lần.
Một địa chỉ đăng nhập sai từ 10 lần trong 10 phút thì `monitor` báo vào kênh quản trị khi có webhook (ngưỡng ở `monitor.alert.login-failed-*`).
Sự kiện đăng nhập sai của Keycloak không ghi tên tài khoản, nên chưa có cảnh báo theo tài khoản.
Gỡ khóa sớm: **Users** → chọn người dùng → tắt công tắc **Temporarily locked**.

IP trong sự kiện theo cùng quy tắc với `remote_addr` ở phần log của gateway.

## Giới hạn tốc độ

Gateway đếm theo IP thật của client (xem phần log), vượt ngưỡng thì trả `429` kèm `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
Backend đếm thêm theo người dùng (`sub` của token), vượt ngưỡng thì trả `429` kèm `Retry-After`.
Request có token sai nhận `401` mà không bị đếm.

| Nơi đếm | Đường dẫn | Ngưỡng mỗi phút |
|---|---|---|
| Gateway, mỗi IP | `/realms/<realm>/login-actions/*` và `/realms/<realm>/protocol/openid-connect/token` (chung một bộ đếm) | 120 |
| Gateway, mỗi IP | `/api/*` (request có token hợp lệ) | 600 |
| Gateway, mỗi IP | `/api/v1/system/status` (công khai) | Xem `deploy/apisix/common.yaml` |
| Backend, mỗi người dùng | Mọi `/api/**` | 300 |

Bộ đếm của backend nằm trong bộ nhớ nên về 0 khi backend khởi động lại.
`RATE_LIMIT_ENABLED=false` trong `deploy/.env` tắt phần của backend, dùng khi thử tải.
Đổi ngưỡng của gateway ở `count` của `limit-count` trong `deploy/apisix/common.yaml` rồi chạy `docker compose up -d --force-recreate apisix-config && docker compose restart apisix` để gateway nạp lại.
Nhiều người dùng chung một IP (mạng di động) cùng dùng bộ đếm, nên thấy `429` trong thao tác thường ngày thì tăng ngưỡng.
Mỗi lần đăng nhập tốn hai request (form và token), nên script đăng nhập hơn 60 tài khoản trong một phút từ một máy sẽ chạm giới hạn; chờ một phút hoặc chạy cách nhau.
Giới hạn này chỉ chặn lượng request lớn từ một địa chỉ; đoán mật khẩu từng tài khoản do khóa tạm của Keycloak xử lý.

## Audit trong backend

Bảng `audit_log` (chỉ thêm, database từ chối sửa hoặc xóa) ghi lần truy cập bị từ chối và thao tác quản trị.
Bảng không ghi mật khẩu, token hay nội dung request.
Mỗi bản ghi có người thực hiện (`sub`, tên, vai, client của token), `ip`, `request_id`, method và đường dẫn.
`request_id` là giá trị `X-Request-Id` của gateway, nên nối được với dòng log gateway (`request_id`) và log backend (trong `[...]` sau mức log).

| Hành động | Khi nào |
|---|---|
| `ACCESS_DENIED` | Request nhận 403 |
| `AUTH_FAILED` | Backend từ chối token đã tới nó (token không tới được backend nếu gateway đã từ chối) |
| `SERVER_ERROR` | Backend trả `500`, `502` hoặc `503`; `outcome` là `FAILED`, `metadata` có mã trả về và tên lớp ngoại lệ (không có thông điệp lỗi) |
| `ADMIN_ACTION_FAILED` | ADMIN thực hiện thao tác thay đổi (POST, PUT, PATCH, DELETE) bị từ chối vì lý do nghiệp vụ (`409`, `422`); `outcome` là `FAILED` |
| `MODEL_*` (`MODEL_APPLIED`, `MODEL_ROLLED_BACK`, `MODEL_RESUMED`, `MODEL_ABANDONED`) | Áp dụng, quay về, tiếp tục hoặc hủy bỏ mô hình thành công, kể cả lần bootstrap (người thực hiện `Bootstrap`); `target_type` là `MODEL`, `target_id` là tên mô hình, `metadata` có `version`, `status`, `runId`, `actorName`; lần `unchanged` hay bị từ chối không ghi |
| `USER_*` (tạo, khóa, mở, đặt lại mật khẩu, gán và gỡ vai, thêm và bỏ đơn vị) | ADMIN đổi tài khoản ở tab Người dùng; `target_id` là mã người dùng, `metadata` có tên đăng nhập và mô hình, vai hoặc đơn vị; không có mật khẩu |

Thay đổi tài khoản làm thẳng ở Keycloak Admin Console không vào `audit_log`; tra ở sự kiện quản trị của Keycloak (Events, Admin events; giữ 90 ngày), có tài khoản người thực hiện.

Thay đổi trên từng bản ghi nằm ở `engine.record_event`, lịch sử áp dụng mô hình ở `engine.apply_run` và `engine.apply_step`.
Đăng nhập và đổi mật khẩu nằm ở sự kiện của Keycloak.
Mỗi địa chỉ client có tối đa 20 bản ghi mỗi loại `ACCESS_DENIED`, `AUTH_FAILED`, `SERVER_ERROR` hoặc `ADMIN_ACTION_FAILED` mỗi phút (`audit.denied.max-per-minute` trong `application.yaml`).
Số bản ghi bị bỏ qua nằm trong `metadata.suppressed` của bản ghi đầu phút kế tiếp.

ADMIN tra cứu qua API (`GET /api/v1/audit-logs`), lọc theo `actor` (user id hoặc username), `action`, `outcome`, `from`, `to`:

```
GET /api/v1/audit-logs?outcome=DENIED&from=2026-10-23T00:00:00Z&size=50
```

Hoặc truy vấn trực tiếp:

```bash
cd deploy
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "${POSTGRES_DB:-dxos}" -c "select occurred_at, action, outcome, actor_name, ip, path from audit_log order by id desc limit 20"'
```

Bản ghi được giữ không giới hạn thời gian và trigger chặn xóa.
Chạy lâu thì theo dõi dung lượng bảng.

## Màn hình giám sát

Dịch vụ `monitor` gom log gateway, `audit_log`, sự kiện đăng nhập của Keycloak và trạng thái dịch vụ vào một màn hình `/monitor/` cho ADMIN, kèm cảnh báo qua webhook khi có cấu hình.
Cách đọc, truy vết một sự cố, dữ liệu, cảnh báo và cấu hình ở [`backend/dx-monitor/README.md`](../backend/dx-monitor/README.md).
