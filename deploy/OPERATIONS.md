# Vận hành: log truy cập và sự kiện đăng nhập

Mọi request vào stack đi qua gateway APISIX, nên log của gateway là bản ghi truy cập chính.
Sự kiện đăng nhập nằm ở Keycloak.

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
| `cf_ray` | Mã request của Cloudflare |
| `user_agent` | Trình duyệt hoặc ứng dụng gọi |

`remote_addr` là IP thật của người dùng khi vào qua Cloudflare Tunnel (đọc từ `CF-Connecting-IP`), và là IP trong mạng LAN khi vào trực tiếp.
Header chỉ được tin khi request đến từ mạng Docker (`172.16.0.0/12`) hoặc loopback; ở máy có dải Docker khác thì sửa `real_ip_from` trong `apisix/config.yaml`.

Log của container còn có dòng không phải JSON (cảnh báo khi khởi động, error log của nginx), nên `jq` đọc từng dòng và bỏ qua dòng lỗi bằng `fromjson?`.
Kiểm tra sức khỏe của container gửi một request từ `127.0.0.1` (đường dẫn `/`, mã 400) mỗi 10 giây; các ví dụ dưới loại dòng này.
Ví dụ (cần `jq`):

```bash
cd deploy
docker compose logs apisix --since 1h --no-log-prefix | jq -cR 'fromjson? | select(.status >= 400 and .remote_addr != "127.0.0.1")'
docker compose logs apisix --since 24h --no-log-prefix | jq -rR 'fromjson? | select(.remote_addr != "127.0.0.1") | .remote_addr' | sort | uniq -c | sort -rn | head
docker compose logs apisix --no-log-prefix | jq -cR --arg id "<mã từ header X-Request-Id>" 'fromjson? | select(.request_id == $id)'
docker compose logs apisix --since 1h --no-log-prefix | jq -cR 'fromjson? | select(.status == 429)'
```

## Giữ log

Docker xoay vòng log theo dung lượng: gateway 10 tệp 100 MB, các dịch vụ khác 5 tệp 20 MB.
Log mất khi container bị tạo lại.
Truy vết lâu dài cần dùng sự kiện của Keycloak (90 ngày) và dữ liệu nghiệp vụ trong database.

## Sự kiện đăng nhập của Keycloak

Admin Console → realm `dxos` → **Events**:

- **User events**: đăng nhập, đăng nhập sai, đăng xuất, đổi hoặc đặt lại mật khẩu, tài khoản bị khóa; kèm IP và client; giữ 90 ngày.
- **Admin events**: thao tác quản trị trong Keycloak, kèm nội dung thay đổi.

Sau 5 lần đăng nhập sai, tài khoản bị khóa tạm 1 phút, mỗi lần sai tiếp theo tăng thêm 1 phút, tối đa 15 phút.
Hai lần sai cách nhau dưới 1 giây cũng khóa 1 phút (kiểm tra đăng nhập nhanh của Keycloak), nên script đăng nhập thử cần chờ giữa các lần.
Gỡ khóa sớm: **Users** → chọn người dùng → tắt công tắc **Temporarily locked**.

IP trong sự kiện là IP thật chỉ khi máy chủ chạy với overlay Cloudflare (`docker-compose.cloudflared.yml`), nơi Keycloak đọc `X-Forwarded-For` từ gateway.

## Giới hạn tốc độ

Gateway đếm theo IP thật của client (xem phần log), vượt ngưỡng thì trả `429` kèm `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.

| Đường dẫn | Ngưỡng mỗi IP mỗi phút |
|---|---|
| `/realms/<realm>/login-actions/*`, `/realms/<realm>/protocol/openid-connect/token` | 30 |
| `/api/*` (request có token hợp lệ) | 600 |
| Route riêng của Farm và Lab (upload ảnh, chẩn đoán AI, tạo ticket, đặt phòng, hỏi đáp, trạng thái hệ thống) | Xem `deploy/apisix/common.yaml` và `deploy/apisix/apps/<app>.yaml` |

Request có token sai nhận `401` mà không bị đếm; phần này do Cloudflare lọc.
Đổi ngưỡng ở `count` của `limit-count` trong file route tương ứng rồi chạy `./switch-app.sh <app>` để gateway nạp lại.
Nhiều người dùng chung một IP (mạng di động) cùng dùng bộ đếm; thấy `429` trong thao tác thường ngày thì tăng ngưỡng.
Script đăng nhập nhiều tài khoản liên tiếp từ một máy (như `deploy/demo/e2e.py` rồi chạy lại ngay) có thể chạm 30 lượt mỗi phút của form đăng nhập; chờ một phút hoặc chạy cách nhau.

## Cloudflare

Cấu hình nằm trên Cloudflare, không nằm trong repo.
Sau khi bật mỗi mục, kiểm đăng nhập web, đăng nhập mobile, gửi ảnh báo cáo và chẩn đoán AI.

Domain `khuongdev.io.vn`:

- **Security → WAF → Managed rules**: bật Cloudflare Managed Ruleset.
- **Security → Bots**: bật Bot Fight Mode.
- **Security → WAF → Rate limiting rules**: một rule cho `login-actions` của Keycloak (đường dẫn bắt đầu bằng `/realms/` và chứa `/login-actions/`), tính theo IP, chọn chu kỳ và thời gian chặn nhỏ nhất mà gói cho phép.
- **SSL/TLS → Edge Certificates**: bật Always Use HTTPS, đặt Minimum TLS Version 1.2.
- **Rules → Transform Rules → Modify Request Header**: `X-Forwarded-Port` = `443` (đã có, bắt buộc).
- **Security → Events**: xem request bị chặn hoặc thử thách; nếu ảnh báo cáo hoặc câu hỏi AI bị chặn nhầm thì thêm rule bỏ qua cho đường dẫn đó.

Không chặn theo quốc gia vì hội đồng chấm có thể truy cập từ nước ngoài.
Swagger (`/swagger-ui/`) để công khai.

### Cổng quản trị sau Cloudflare Access

Zero Trust → Access → Applications → Add an application → Self-hosted, chính sách Allow theo danh sách email của quản trị viên và kỹ sư (mã gửi qua email).
Tạo ba ứng dụng:

| Ứng dụng | Domain | Đường dẫn |
|---|---|---|
| BI (Superset) | `bi.khuongdev.io.vn` | để trống |
| MinIO Console | `files.khuongdev.io.vn` | để trống |
| Keycloak Admin Console | `khuongdev.io.vn` | `admin/*` |

Không đặt Access cho API, web app, `/realms/*`, portal, chat và link ảnh, vì nông dân và mobile dùng các đường này.
Giữ một phiên Admin Console đang mở khi tạo ứng dụng Access cho `admin/*`; nếu bị khóa ngoài thì gỡ ứng dụng đó trong Zero Trust.
Sau khi bật, kiểm đăng nhập Superset bằng SSO với một tài khoản kỹ sư.

## Audit trong backend

Bảng `audit_log` (chỉ thêm, database từ chối sửa hoặc xóa) ghi lần truy cập bị từ chối và thao tác quản trị; không ghi mật khẩu, token hay nội dung request.
Mỗi bản ghi có người thực hiện (`sub`, tên, vai ứng dụng, client của token), `ip`, `request_id`, method và đường dẫn.
`request_id` là giá trị `X-Request-Id` của gateway, nên nối được với dòng log gateway (`request_id`) và log backend (trong `[...]` sau mức log).

| Hành động | Khi nào |
|---|---|
| `ACCESS_DENIED` | Request nhận 403 |
| `AUTH_FAILED` | Backend từ chối token đã tới nó (token không tới được backend nếu gateway đã từ chối) |
| `WORKSPACE_CREATED`, `WORKSPACE_LINKED`, `MEMBER_ADDED` | ADMIN tạo workspace, gắn tài khoản chủ, thêm thành viên |
| `PARCEL_DELETED`, `SEASON_DELETED`, `REPORT_DELETED`, `HARVEST_OVERRIDE` | Farm: xóa mềm, thu hoạch sớm hoặc thu hoạch thay nông dân |
| `TICKET_CATEGORY_CREATED`, `TICKET_CATEGORY_CHANGED`, `SLA_POLICY_CHANGED`, `TICKET_ATTACHMENT_REMOVED` | Lab: danh mục, SLA, xóa tệp đính kèm |

Quyết định duyệt của workflow nằm ở `workflow_event`; đăng nhập và đổi mật khẩu nằm ở sự kiện của Keycloak.
Mỗi địa chỉ client chỉ có tối đa 20 bản ghi `ACCESS_DENIED` hoặc `AUTH_FAILED` mỗi phút; số bản ghi bị bỏ qua được ghi trong `metadata.suppressed` của bản ghi đầu phút kế tiếp (`audit.denied.max-per-minute` trong `application.yaml`).

ADMIN tra cứu qua API (Swagger: `GET /api/v1/audit-logs`), lọc theo `actor` (user id hoặc username), `action`, `outcome`, `from`, `to`:

```
GET /api/v1/audit-logs?outcome=DENIED&from=2026-10-23T00:00:00Z&size=50
```

Hoặc truy vấn trực tiếp:

```bash
cd deploy
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "${POSTGRES_DB:-dxos}" -c "select occurred_at, action, outcome, actor_name, ip, path from audit_log order by id desc limit 20"'
```

Bản ghi được giữ không giới hạn thời gian; bảng nhỏ và trigger chặn xóa, nên cần theo dõi dung lượng nếu chạy lâu.

### Cảnh báo

Mỗi 5 phút, một địa chỉ bị từ chối từ 10 lần trong 10 phút qua được báo vào kênh Mattermost "Ban quản trị", tối đa một tin mỗi địa chỉ trong 60 phút.
Đổi ngưỡng ở `audit.alert.*` trong `backend/dx-host/src/main/resources/application.yaml`.
Không có webhook của kênh thì không gửi.
