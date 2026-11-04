# Vận hành: log truy cập, sự kiện đăng nhập và giám sát

Mọi request vào stack đi qua gateway APISIX, nên log của gateway là bản ghi truy cập chính.
Sự kiện đăng nhập nằm ở Keycloak.
Màn hình giám sát `/monitor/` gom các nguồn này, xem [`backend/dx-monitor/README.md`](../backend/dx-monitor/README.md).

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

Backend ghi `request_id` này vào log của nó (trong `[...]` sau mức log).

`remote_addr` là IP thật của người dùng khi vào qua Cloudflare Tunnel (đọc từ `CF-Connecting-IP`), và là IP trong mạng LAN khi vào trực tiếp.
Header chỉ được tin khi request đến từ mạng Docker (`172.16.0.0/12`) hoặc loopback.
Ở máy có dải Docker khác thì sửa `real_ip_from` trong `apisix/config.yaml`.

Log của container còn có dòng không phải JSON (cảnh báo khi khởi động, error log của nginx), nên `jq` đọc từng dòng và bỏ qua dòng lỗi bằng `fromjson?`.
Kiểm tra sức khỏe của container gửi một request từ `127.0.0.1` (đường dẫn `/`, mã 400) mỗi 10 giây.
Các ví dụ dưới loại dòng này.
Ví dụ (cần `jq`, cài bằng `apt install jq` nếu máy chủ chưa có):

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
Một địa chỉ đăng nhập sai từ 10 lần trong 10 phút thì `monitor` báo vào kênh quản trị khi có webhook (ngưỡng ở `monitor.alert.login-failed-*`).
Sự kiện đăng nhập sai của Keycloak không ghi tên tài khoản, nên chưa có cảnh báo theo tài khoản.
Gỡ khóa sớm: **Users** → chọn người dùng → tắt công tắc **Temporarily locked**.

IP trong sự kiện là IP thật chỉ khi máy chủ chạy với overlay Cloudflare (`docker-compose.cloudflared.yml`), nơi Keycloak đọc `X-Forwarded-For` từ gateway.

## Giới hạn tốc độ

Gateway đếm theo IP thật của client (xem phần log), vượt ngưỡng thì trả `429` kèm `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.

| Đường dẫn | Ngưỡng mỗi IP mỗi phút |
|---|---|
| `/realms/<realm>/login-actions/*`, `/realms/<realm>/protocol/openid-connect/token` (chung một bộ đếm) | 120 |
| `/api/*` (request có token hợp lệ) | 600 |
| `/api/v1/system/status` (công khai) | Xem `deploy/apisix/common.yaml` |

Backend đếm thêm theo người dùng (`sub` của token), vượt ngưỡng thì trả `429` kèm `Retry-After`.
Bộ đếm nằm trong bộ nhớ nên về 0 khi backend khởi động lại.
`RATE_LIMIT_ENABLED=false` trong `deploy/.env` tắt phần này, dùng khi thử tải.

| Lệnh gọi (mỗi người mỗi phút) | Ngưỡng |
|---|---|
| Mọi `/api/**` | 300 |

Request có token sai nhận `401` mà không bị đếm.
Phần này do Cloudflare lọc.
Đổi ngưỡng ở `count` của `limit-count` trong `deploy/apisix/common.yaml` rồi chạy `docker compose up -d --force-recreate apisix-config && docker compose restart apisix` để gateway nạp lại.
Nhiều người dùng chung một IP (mạng di động) cùng dùng bộ đếm.
Thấy `429` trong thao tác thường ngày thì tăng ngưỡng.
Mỗi lần đăng nhập tốn hai request (form và token), nên script đăng nhập hơn 60 tài khoản trong một phút từ một máy sẽ chạm giới hạn.
Chờ một phút hoặc chạy cách nhau.
Giới hạn này chỉ chặn lượng request lớn từ một địa chỉ.
Đoán mật khẩu từng tài khoản do khóa tạm của Keycloak xử lý.

## Cloudflare

Cấu hình nằm trên Cloudflare, không nằm trong repo.
Khái niệm và tổng hợp cấu hình đang dùng theo dõi ở [trang docs](https://haui-implsolution.github.io/DX-OS-docs/).
Sau khi bật mỗi mục, kiểm đăng nhập web và một lượt ghi bản ghi qua API.

Domain `khuongdev.io.vn`:

- **Security → Settings → Bot traffic**: bật Bot fight mode.
- **Security → Settings**: Cloudflare managed ruleset luôn bật ở gói Free, không có công tắc.
  Nó chặn các khai thác đã biết, không chặn SQLi hay XSS chung; bộ đầy đủ cần gói trả phí.
  Backend dùng truy vấn tham số hóa, nên gói Free là đủ cho chuỗi tấn công mẫu.
- **Security → Security rules → Rate limiting rules** (gói Free có 1 rule, 10 lượt trong 10 giây mỗi IP, hành động Block): một rule cho `login-actions` của Keycloak (đường dẫn bắt đầu bằng `/realms/` và chứa `/login-actions/`), tính theo IP, chọn chu kỳ và thời gian chặn nhỏ nhất mà gói cho phép.
- **SSL/TLS → Edge Certificates**: bật Always Use HTTPS, đặt Minimum TLS Version 1.2.
- **Rules → Transform Rules → Modify Request Header**: `X-Forwarded-Port` = `443` (đã có, bắt buộc).
- **Security → Events**: xem request bị chặn hoặc thử thách.
  Tệp tải lên bị chặn nhầm thì thêm rule bỏ qua cho đường dẫn đó.

Không chặn theo quốc gia vì hội đồng chấm có thể truy cập từ nước ngoài.
Swagger (`/swagger-ui/`) để công khai.

### Cổng quản trị sau Cloudflare Access

Zero Trust → Access controls → Applications → Create new application → Self-hosted, một policy Allow theo danh sách email của quản trị viên và kỹ sư dùng chung cho cả hai ứng dụng.
Người dùng đăng nhập vào Access bằng phương thức đã bật (tài khoản Cloudflare hoặc mã gửi qua email).
Form mặc định mở ở chế độ Private IP; thêm một public hostname và xóa dòng Private IP trống.
Tạo hai ứng dụng:

| Ứng dụng | Domain | Đường dẫn |
|---|---|---|
| Keycloak Admin Console | `khuongdev.io.vn` | `admin/*` |
| Màn hình giám sát | `khuongdev.io.vn` | `monitor/*` |

Không đặt Access cho API, web app, `/realms/*`, portal và link tệp, vì người dùng cuối dùng các đường này.
Giữ một phiên Admin Console đang mở khi tạo ứng dụng Access cho `admin/*`.
Nếu bị khóa ngoài thì gỡ ứng dụng đó trong Zero Trust.
Sau khi bật, kiểm đăng nhập vào màn hình giám sát bằng SSO với tài khoản ADMIN.

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

Thay đổi trên từng bản ghi nằm ở `engine.record_event`, lịch sử áp dụng mô hình ở `engine.apply_run` và `engine.apply_step`.
Mô hình có lần áp dụng dừng giữa chừng bị đóng với người dùng và hiện nhãn "Đang dừng dở" ở bảng điều khiển.
Chọn Tiếp tục để hoàn tất, hoặc Hủy bỏ để đưa các bảng về phiên bản đang chạy; chạy `deploy/backup.sh` trước khi Hủy bỏ nếu cần giữ dữ liệu.
Trước khi áp dụng thay đổi mức mất dữ liệu (`accept=LOSS`), chạy `deploy/backup.sh`.
Đăng nhập và đổi mật khẩu nằm ở sự kiện của Keycloak.
Mỗi địa chỉ client có tối đa 20 bản ghi mỗi loại `ACCESS_DENIED`, `AUTH_FAILED`, `SERVER_ERROR` hoặc `ADMIN_ACTION_FAILED` mỗi phút (`audit.denied.max-per-minute` trong `application.yaml`).
Số bản ghi bị bỏ qua nằm trong `metadata.suppressed` của bản ghi đầu phút kế tiếp.

ADMIN tra cứu qua API (Swagger: `GET /api/v1/audit-logs`), lọc theo `actor` (user id hoặc username), `action`, `outcome`, `from`, `to`:

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

## Cài đặt và lệnh `dxos`

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

Bộ cài ([`get.sh`](./get.sh), phát hành kèm mỗi bản với tên `dxos-install.sh`) tải gói của bản phát hành, so với SHA-256 trong `dx-os-X.Y.Z.sha256`, rồi chạy [`install.sh`](./install.sh) của gói đó.
Gói sai SHA-256 thì không cài gì.
`install.sh` chép `deploy/` và `sso/realm-dxos.json` vào `~/.dxos` (đổi bằng `--prefix` hoặc biến `DXOS_HOME`), sinh `deploy/.env` bằng [`make_env.sh`](./make_env.sh), đặt `COMPOSE_PROJECT_NAME=dxos` cùng image GHCR của bản đó, và tạo liên kết `~/.local/bin/dxos`.
Chọn bản bằng `--version vX.Y.Z`; cài từ gói có sẵn trên máy bằng `--file dx-os-X.Y.Z.tar.gz`.

| Lệnh | Việc |
|---|---|
| `dxos start` | Chạy `doctor.sh`, tải image còn thiếu, khởi động, chờ mọi dịch vụ sẵn sàng; realm chưa có ai vai `ADMIN` thì tạo tài khoản `admin` với mật khẩu tạm in ra một lần, rồi in các địa chỉ |
| `dxos stop` | Dừng các container, giữ dữ liệu |
| `dxos status` | Trạng thái từng dịch vụ và các địa chỉ |
| `dxos logs [dịch vụ]` | Theo dõi log |
| `dxos admin` | Đặt mật khẩu tạm mới cho `admin`, tạo tài khoản nếu chưa có |
| `dxos update [vX.Y.Z]` | Cài bản mới hơn (mặc định bản mới nhất): thay tệp chạy, giữ `.env` và `data/`, thêm biến mới, rồi chạy `update.sh` |
| `dxos doctor`, `dxos backup` | Chạy `doctor.sh`, `backup.sh` |
| `dxos uninstall [--purge]` | Xóa container và lệnh `dxos`; `--purge` xóa cả dữ liệu và thư mục cài, phải gõ xác nhận |

Mật khẩu tạm của `admin` phải đổi ở lần đăng nhập đầu.
Tài khoản quản trị Keycloak nằm ở `KEYCLOAK_ADMIN_USERNAME` và `KEYCLOAK_ADMIN_PASSWORD` trong `~/.dxos/deploy/.env`.
Image chỉ có bản `linux/amd64`; bộ cài được kiểm trên Linux, chưa kiểm trên macOS và Windows (WSL2).

## Kiểm tra trước khi chạy

`./doctor.sh` (trong `deploy/`) chỉ đọc, không đổi gì và không in giá trị nào của `.env`.
Nó kiểm Docker và Compose, RAM, ổ đĩa, `deploy/.env` và các cổng.
`.env` được so với các biến `${NAME:?}` mà Compose bắt buộc phải có: biến thiếu hoặc để trống bị báo `FAIL`.
Biến có trong `.env` mà `.env.example` không liệt kê bị báo `WARN`.
Các cổng đã bị chiếm chỉ được kiểm khi stack chưa chạy.
Thoát với mã 1 khi có `FAIL`, nên chạy trước `./update.sh` và `docker compose up -d` trên máy chủ.

## Giám sát từ ngoài

`./online-check.sh` (trong `deploy/`) gọi web, portal, trang đăng nhập và `/api/v1/system/status` qua địa chỉ công khai, rồi thoát với mã 1 khi một đường không trả 200 hoặc khi `backend` hoặc `storage` không `UP`.
Địa chỉ lấy từ `GATEWAY_PUBLIC_URL` trong `.env`; biến `PUBLIC_URL` trong shell ghi đè để kiểm địa chỉ khác.
Chạy theo lịch bằng cron, ví dụ mỗi 12 giờ: `0 */12 * * * cd /đường/dẫn/deploy && ./online-check.sh >> online-check.log 2>&1`.
Khi `.env` có `MATTERMOST_WEBHOOK_URL_ADMIN` (hoặc `MATTERMOST_WEBHOOK_URL`), script gửi một tin vào kênh khi site chuyển sang lỗi và một tin khi site trở lại, không gửi lặp trong lúc còn lỗi.
Script chạy trên chính máy chủ nên không phát hiện được việc máy chủ tắt hẳn; muốn phát hiện, thêm một dịch vụ ping ngoài (ví dụ UptimeRobot) trỏ vào `/api/v1/system/status`.
## Sao lưu và khôi phục

`./backup.sh` (trong `deploy/`) chụp một bản sao lưu mã hóa của những gì không dựng lại được từ repo.
Gồm toàn bộ Postgres (dữ liệu nghiệp vụ, tài khoản Keycloak) và tệp trong SeaweedFS.
Script dùng restic chạy trong container nên chỉ cần Docker.
Bản sao lưu khử trùng lặp và mã hóa bằng `BACKUP_PASSWORD`.
Cất mật khẩu này ở nơi khác máy chủ, vì mất nó là không đọc lại được.

```bash
cd deploy
./backup.sh              # chụp, rồi giữ 7 bản theo ngày, 4 theo tuần, 6 theo tháng
./backup.sh verify       # nạp bản dump mới nhất vào một Postgres tạm và kiểm tra
./backup.sh snapshots    # liệt kê các bản
```

Kho nằm ở `deploy/backups` (đổi bằng `BACKUP_DIR`) hoặc ở một kho restic từ xa (`BACKUP_REPOSITORY`, ví dụ `s3:...`).
Kho cùng máy với dữ liệu không cứu được khi mất máy, nên chép nó ra ngoài (rsync, rclone) hoặc dùng kho từ xa.
Chạy theo lịch bằng cron, ví dụ mỗi đêm: `0 2 * * * cd /đường/dẫn/deploy && ./backup.sh >> backups/backup.log 2>&1`, và chạy `verify` mỗi tuần.
Máy chủ dùng tệp env khác `.env` thì đặt `ENV_FILE=.env.dev ./backup.sh`.
CI chạy một lượt backup và verify trên Postgres thật ở job `backup-drill`.

**Khôi phục** (máy mới hoặc sau khi mất dữ liệu):

1. Dựng lại stack từ repo với cùng `deploy/.env`, giữ Postgres chạy và dừng các dịch vụ còn lại: `docker compose up -d postgres` rồi `docker compose stop` các dịch vụ khác.
2. Lấy bản sao lưu ra thư mục tạm: `./backup.sh restore /tmp/dxos-restore`.
3. Nạp Postgres: `gunzip -c /tmp/dxos-restore/stage/postgres.sql.gz | docker compose exec -T postgres psql -U "$POSTGRES_USER" postgres`.
   Hai dòng lỗi `role "..." already exists` và `database "..." already exists` là bình thường vì tài khoản quản trị và database chính đã có sẵn.
   Phần còn lại phải nạp không lỗi.
4. Chép `data/seaweedfs` từ `/tmp/dxos-restore/data/` vào `deploy/data/` (các tệp thuộc root nên dùng `sudo`), rồi `docker compose up -d`.

## Quét mã độc tệp tải lên

Khi bật, mọi tệp đính kèm được ClamAV quét trước khi xử lý và lưu.
Tệp nhiễm bị từ chối với HTTP 422 `ERR_FILE_INFECTED` và lần chặn được ghi vào `audit_log` (`FILE_INFECTED`, kèm tên mẫu mã độc).
ClamAV không trả lời thì tệp không được lưu và người dùng nhận HTTP 503 `ERR_SCAN_UNAVAILABLE`, vì tệp chưa quét không được coi là sạch.
Tắt mặc định vì ClamAV cần khoảng 1 GB RAM.
Bật: bỏ dấu `#` ở `COMPOSE_PROFILES=av` và `STORAGE_SCAN_HOST=clamav` trong `deploy/.env`, rồi `docker compose up -d`.
Chữ ký virus tải về lần đầu mất vài phút và được giữ trong volume `clamav_data`.
Mỗi lần clamd nạp lại chữ ký (hằng ngày và sau khi khởi động) nó không quét được khoảng 10 giây, và người dùng gặp 503 trong lúc đó; chờ rồi tải lại.
Thử bằng tệp EICAR (chuỗi kiểm thử chuẩn của ngành diệt virus): tệp chỉ gồm đúng chuỗi đó, đổi tên `.pdf` rồi đính vào một yêu cầu; `deploy/dev/check_demo.py` tự làm bước này khi `STORAGE_SCAN_HOST` có giá trị.

## Màn hình giám sát

Dịch vụ `monitor` gom log gateway, `audit_log`, sự kiện đăng nhập của Keycloak và trạng thái dịch vụ vào một màn hình `/monitor/` cho ADMIN, kèm cảnh báo qua webhook khi có cấu hình.
Cách đọc, truy vết một sự cố, dữ liệu, cảnh báo và cấu hình ở [`backend/dx-monitor/README.md`](../backend/dx-monitor/README.md).
