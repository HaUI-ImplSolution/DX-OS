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

IP trong sự kiện là IP của client khi Keycloak đọc `X-Forwarded-For` do gateway đặt từ `CF-Connecting-IP`, tức là khi có proxy phía trước.

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
Đổi ngưỡng ở `count` của `limit-count` trong `deploy/apisix/common.yaml` rồi chạy `docker compose up -d --force-recreate apisix-config && docker compose restart apisix` để gateway nạp lại.
Nhiều người dùng chung một IP (mạng di động) cùng dùng bộ đếm.
Thấy `429` trong thao tác thường ngày thì tăng ngưỡng.
Mỗi lần đăng nhập tốn hai request (form và token), nên script đăng nhập hơn 60 tài khoản trong một phút từ một máy sẽ chạm giới hạn.
Chờ một phút hoặc chạy cách nhau.
Giới hạn này chỉ chặn lượng request lớn từ một địa chỉ.
Đoán mật khẩu từng tài khoản do khóa tạm của Keycloak xử lý.

## Quản trị người dùng

Tab Người dùng của bảng điều khiển (`/console`, ADMIN) cho tìm người dùng, xem vai theo từng ứng dụng, gán và gỡ vai và đơn vị của các mô hình đang chạy, tạo người dùng, khóa và mở, đặt lại mật khẩu.
Mật khẩu tạm do hệ thống sinh, hiện một lần và người dùng phải đổi ở lần đăng nhập đầu.
Vai `ADMIN` của nền tảng không cấp được ở tab này; cấp ở Keycloak Admin Console (Users, Role mapping).
Người dùng đăng nhập bằng nhà cung cấp ngoài, chính sách mật khẩu và MFA do Keycloak quản lý.
Backend cần tài khoản dịch vụ `dxos-backend-admin` có quyền client; Keycloak dựng từ trước phải cấp tay (xem [`sso/README.md`](../sso/README.md)).

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
| `MODEL_APPLIED`, `MODEL_ROLLED_BACK`, `MODEL_RESUMED`, `MODEL_ABANDONED` | Áp dụng, quay về, tiếp tục hoặc hủy bỏ mô hình thành công, kể cả lần bootstrap (người thực hiện tên `Bootstrap`); `outcome` là `SUCCESS`, `target_type` là `MODEL`, `target_id` là tên mô hình, `metadata` có `version`, `status`, `runId`, `actorName`; lần `unchanged` hay bị từ chối không ghi |
| `USER_CREATED`, `USER_LOCKED`, `USER_UNLOCKED`, `USER_PASSWORD_RESET`, `USER_ROLE_GRANTED`, `USER_ROLE_REVOKED`, `USER_UNIT_ADDED`, `USER_UNIT_REMOVED` | ADMIN đổi tài khoản ở tab Người dùng; `outcome` là `SUCCESS`, `target_id` là mã người dùng, `metadata` có tên đăng nhập và mô hình, vai hoặc đơn vị; không có mật khẩu |

Thay đổi tài khoản làm thẳng ở Keycloak Admin Console không vào `audit_log`; tra ở sự kiện quản trị của Keycloak (Events, Admin events; giữ 90 ngày), có tài khoản người thực hiện.

Thay đổi trên từng bản ghi nằm ở `engine.record_event`, lịch sử áp dụng mô hình ở `engine.apply_run` và `engine.apply_step`.
Mô hình có lần áp dụng dừng giữa chừng bị đóng với người dùng và hiện nhãn "Đang dừng dở" ở bảng điều khiển.
Chọn Tiếp tục để hoàn tất, hoặc Hủy bỏ để đưa các bảng về phiên bản đang chạy; chạy `deploy/backup.sh` trước khi Hủy bỏ nếu cần giữ dữ liệu.
Nhóm đơn vị mà lần dở đã tạo ở Keycloak không bị xóa, vì các mô hình dùng chung nhóm theo tên; quản trị viên xóa nhóm rỗng ở Keycloak nếu muốn.
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
| `dxos start [tệp]` | Chạy `doctor.sh`, tải image còn thiếu, khởi động, chờ mọi dịch vụ sẵn sàng; realm chưa có ai vai `ADMIN` thì tạo tài khoản `admin` với mật khẩu tạm in ra một lần; có tệp mô hình thì đưa tệp vào và in kết quả (xem dưới); rồi in các địa chỉ |
| `dxos stop` | Dừng các container, giữ dữ liệu |
| `dxos status` | Trạng thái từng dịch vụ, các địa chỉ và trạng thái quét mã độc |
| `dxos logs [dịch vụ]` | Theo dõi log |
| `dxos admin` | Đặt mật khẩu tạm mới cho `admin`, tạo tài khoản nếu chưa có |
| `dxos update [vX.Y.Z]` | Cài bản mới hơn (mặc định bản mới nhất): thay tệp chạy, giữ `.env` và `data/`, thêm biến mới, rồi chạy `update.sh` |
| `dxos doctor`, `dxos backup` | Chạy `doctor.sh`, `backup.sh` |
| `dxos av [on\|off]` | Bật hoặc tắt quét mã độc tệp tải lên (xem dưới); không có đối số thì in trạng thái quét |
| `dxos plan <tệp>`, `dxos apply <tệp> [--accept MỨC]` | In kế hoạch của một tệp mô hình, hoặc áp dụng nó (xem dưới) |
| `dxos history verify`, `dxos history anchor` | Kiểm lịch sử hồ sơ chưa bị sửa, hoặc ghi điểm neo ngay (xem dưới) |
| `dxos reconcile [install\|uninstall]` | Đưa stack về đúng khai báo; `install` chạy mỗi phút bằng systemd user timer (xem dưới) |
| `dxos archive <mô hình>`, `dxos archive check <thư mục>` | Xuất hồ sơ đã xong ra Parquet, hoặc kiểm một bản xuất (xem dưới) |
| `dxos uninstall [--purge]` | Xóa container và lệnh `dxos`; `--purge` xóa cả dữ liệu và thư mục cài, phải gõ xác nhận |

### Tệp mô hình lúc khởi động

`dxos start <tệp>` chép tệp vào `deploy/model/` (chỉ giữ một tệp), ghi tên tệp vào `DXOS_MODEL_FILE` trong `.env` và khởi động lại backend nếu nó đang chạy.
Tên tệp chỉ gồm chữ, số, dấu chấm, gạch ngang và gạch dưới.
Backend chờ Keycloak trả lời (tối đa 300 giây) rồi áp dụng tệp với người thực hiện `bootstrap`, và chỉ khi tệp chưa từng được áp dụng, kế hoạch ở mức an toàn không có từ chối hay dòng vi phạm, và mô hình không có lần áp dụng dở.
Mỗi lần khởi động ghi một dòng `Model bootstrap: <kết quả> <chi tiết>`, và `dxos start` in dòng đó:

| Kết quả | Ý nghĩa |
|---|---|
| `applied` | Đã áp dụng, kèm tên mô hình và phiên bản |
| `unchanged` | Mô hình đang chạy chính tệp này |
| `skipped` | Không áp dụng, kèm lý do; xử lý ở bảng điều khiển |
| `failed` | Tệp không đọc được hoặc không qua kiểm tra (kèm dòng và cột), Keycloak không trả lời kịp, hoặc việc áp dụng lỗi giữa chừng; `dxos start` thoát với mã 3 |

Backend chạy ở mọi kết quả.
`dxos start` chờ dòng kết quả tối đa 300 giây (đổi bằng `DXOS_MODEL_TIMEOUT`) rồi thoát với mã 4.
Backend chỉ đọc tệp khi khởi động, nên `dxos start` không tham số trên hệ thống đang chạy không đọc lại tệp.
Tệp hỏng vẫn nằm trong `.env` và được báo lại ở mỗi lần backend khởi động cho đến khi sửa hoặc thay.

### Webhook sự kiện

Mỗi mô hình gửi các dòng lịch sử của hồ sơ tới một đích.
Tệp mô hình chọn đích bằng `webhook: <tên đích>` ở đầu tệp, tên theo dạng `[a-z][a-z0-9_]*`.
Địa chỉ và bí mật của từng đích nằm trong `deploy/webhooks/targets.yml`, tệp này ngoài git vì địa chỉ có thể chứa khóa; mẫu ở `deploy/webhooks/targets.example.yml`:

```yaml
targets:
  ops:
    url: https://chat.example.org/hooks/abc
    secret: change-me
    format: chat
```

`format` là `json` (thông điệp nguyên văn, mặc định) hoặc `chat` (xem "Câu cho Mattermost và Slack" dưới đây); đích mặc định đặt dạng bằng `DXOS_WEBHOOK_FORMAT`.

Mô hình không ghi `webhook` thì dùng `DXOS_WEBHOOK_URL` trong `.env`; để trống và không ghi đích thì mô hình không gửi gì.
Chỉ dòng sinh ra sau khi mô hình có đích mới được gửi.
Mô hình ghi tên đích chưa có trong tệp thì xem kế hoạch báo bị từ chối và áp dụng trả 409.
Tệp `targets.yml` được nạp lại khi đổi, không cần tạo lại backend; một đích sai tên hoặc địa chỉ bị bỏ qua và ghi vào log.
Mô hình không còn đích nào (không ghi `webhook` và `DXOS_WEBHOOK_URL` để trống) thì thông điệp đang chờ của nó bị bỏ và không được gửi khi bật lại; phần đã lỡ lấy bằng API kéo sự kiện ở dưới.
Đích bị gỡ khỏi tệp sau khi mô hình đã chạy thì thông điệp của mô hình đó vẫn vào hàng đợi, báo lỗi cấu hình ở tab Hàng đợi sự kiện, và được giao khi đích có lại và bấm Thử lại.
Mỗi dòng (tạo, sửa, xóa, hành động của quy trình, giao việc, thêm hoặc bỏ tệp, quá hạn) được ghi vào outbox trong giao dịch của thay đổi, nên thay đổi bị hủy thì không có gì được gửi.
Backend gửi `POST` JSON với các trường:

| Trường | Ý nghĩa |
|---|---|
| `eventId` | Số của dòng lịch sử, tăng dần; cũng nằm ở header `X-Dxos-Event-Id` |
| `model`, `entity`, `recordId` | Hồ sơ nào |
| `action` | Hành động, ví dụ `created`, `updated`, `deleted`, `assigned`, `due_soon`, `overdue`, `file_added`, `completion_skipped`, `auto_closed`, hoặc tên hành động của quy trình |
| `fromStatus`, `toStatus` | Trạng thái trước và sau |
| `actor`, `actorName` | Mã người dùng và tên người làm, gửi ra ngoài hệ thống; `system` với dòng quá hạn |
| `at` | Thời điểm, giờ UTC |
| `schemaVersion` | Phiên bản của dạng thông điệp, hiện là 1 |
| `hash` | Hash của dòng trong chuỗi lịch sử (xem "Lịch sử chống sửa"); bên nhận giữ lại thì có điểm neo ngoài cơ sở dữ liệu |

Thông điệp không có nội dung hồ sơ, ghi chú hay tên tệp; bên nhận cần thì gọi API với quyền của mình.
Có `DXOS_WEBHOOK_SECRET` thì mỗi yêu cầu kèm `X-Dxos-Signature: sha256=<hex>`, là HMAC-SHA256 của nguyên văn nội dung; bên nhận tính lại rồi so.
Bên nhận trả 2xx trong 5 giây là xong; kết quả khác thì thông điệp được gửi lại với thời gian chờ gấp đôi mỗi lần, tối đa 5 phút.
Thông điệp lỗi quá `DXOS_OUTBOX_MAX_ATTEMPTS` lần (mặc định 20) thì dừng, không tự gửi nữa; nút Thử lại cho nó thêm một lần.
Các dòng của một hồ sơ được giao theo thứ tự: dòng sau chờ khi dòng cũ hơn của cùng hồ sơ chưa giao, kể cả khi dòng cũ đã dừng và chờ Thử lại.
Dòng của các hồ sơ khác nhau không chờ nhau, và một dòng có thể đến hai lần, nên bên nhận khử trùng theo `eventId`.
Thông điệp chưa gửi, đã dừng, lỗi gần nhất và nút thử lại nằm ở tab Hàng đợi sự kiện; thông điệp đã gửi bị xóa sau 30 ngày.
Đổi `DXOS_WEBHOOK_URL`, `DXOS_WEBHOOK_SECRET` hay `DXOS_WEBHOOK_FORMAT` thì tạo lại backend bằng `docker compose up -d backend`.

Bên nhận lỡ thông điệp (ngừng chạy, hoặc thông điệp đã dừng) lấy lại bằng `GET /api/v1/events?after=<eventId đã giữ>&size=<tối đa 500>&model=<tùy chọn>` với token ADMIN.
Các dòng đọc thẳng từ lịch sử, cùng dạng với thông điệp webhook, từ cũ tới mới; `next` là `eventId` để hỏi tiếp và `more` cho biết còn dòng.
Lệnh này dùng được cả khi chưa đặt đích nào.

Hồ sơ có hạn xử lý dài hơn `DXOS_DUE_SOON_BEFORE` (mặc định `24h`, `0` để tắt) nhận một dòng `due_soon` khi còn chừng đó thời gian trước hạn, đúng một lần cho mỗi hạn.
Hạn không dài hơn khoảng này thì không có dòng `due_soon`.

#### Câu cho Mattermost và Slack

Đích có `format: chat` nhận `{"text": "<một câu>"}` thay cho thông điệp nguyên văn; Mattermost và Slack đều nhận dạng này ở địa chỉ webhook đến của họ.
Câu có tên mô hình, thực thể, số hồ sơ và trạng thái, không có nội dung hồ sơ.
Hồ sơ của thực thể có quy trình vừa được tạo hoặc nộp lại đọc là "Việc mới", dòng `due_soon` là "Sắp đến hạn xử lý", dòng `overdue` là "Quá hạn xử lý", dòng giao việc là "Được giao"; các dòng khác ghi hành động và trạng thái trước, sau.

#### Việc áp dụng mô hình và thay đổi tài khoản

Các thay đổi này gửi thông điệp chủ đề `admin.event` với `id`, `kind`, `model`, `actor`, `actorName`, `at`, `facts` và `schemaVersion`; `id` cũng nằm ở header `X-Dxos-Event-Id`.
`kind` là `MODEL_APPLIED`, `MODEL_ROLLED_BACK`, `MODEL_RESUMED`, `MODEL_ABANDONED`, hoặc tên hành động của audit như `USER_ROLE_GRANTED`.
Thông điệp của một mô hình vào đích của mô hình đó; thay đổi tài khoản và mô hình đã gỡ vào đích mặc định.
Không có đích thì không ghi gì.
Chúng không nằm trong API kéo sự kiện, vì không phải dòng lịch sử của hồ sơ.

### Đổi mô hình bằng dòng lệnh

`dxos plan <tệp>` in kế hoạch của tệp mô hình mà không đổi gì; mã thoát 1 khi kế hoạch bị từ chối.
`dxos apply <tệp> [--accept SAFE|MIGRATION|LOSS]` lấy kế hoạch rồi áp dụng với phiên bản của kế hoạch làm `base`.
Kế hoạch có mức cao hơn `--accept` (mặc định `SAFE`) thì bị từ chối với mã thoát 1 và không đổi gì; lệnh không bao giờ tự nâng mức.
Tệp giống mô hình đang chạy và không có gì lệch thì in "Nothing to apply" và thoát với mã 0.
Tệp giống nhưng bảng hoặc Keycloak đã lệch khỏi tệp thì kế hoạch ghi `repair`; `dxos apply` sửa phần lệch, và cần `--accept MIGRATION` khi kế hoạch ở mức đó.
Việc kiểm lệch chỉ chạy khi xem kế hoạch hoặc áp dụng, không chạy lúc backend khởi động; bảng hoặc cột thừa do sửa tay bị từ chối chứ không bị xóa, vai thừa ở client của mô hình và nhóm đơn vị thừa không bị đụng, còn chỉ mục hay ràng buộc thừa bị xóa khi áp dụng với mức `MIGRATION`.
Đường dẫn tương đối tính theo thư mục gõ lệnh.

Lệnh đăng nhập bằng client `dxos-cli` của realm, dùng `DXOS_CLI_SECRET` trong `.env`; tài khoản dịch vụ của client giữ vai `ADMIN`, và lịch sử áp dụng ghi tên nó (`service-account-dxos-cli`).
Bí mật nằm trong `.env` của máy chủ, nên chạy được trong pipeline GitOps trên chính máy đó.
Realm đã import từ trước chưa có client này: thêm client `dxos-cli` ở Keycloak Admin Console hoặc dựng lại realm.

### Tệp bị gỡ khỏi hồ sơ

Gỡ hoặc thay một tệp, xóa hồ sơ hay xóa thực thể thì đối tượng rời kho sau `DXOS_FILE_DELETE_DELAY` (mặc định 24 giờ).
Backend ghi một thông điệp `file.delete` vào outbox trong cùng giao dịch, đến hạn thì xóa đối tượng.
Đối tượng đã mất thì thông điệp coi như xong; kho đang ngừng thì thông điệp được thử lại.
Một lượt sao lưu ngắn hơn khoảng trễ này luôn có đủ tệp cho mọi dòng tệp đính kèm trong bản dump.
Tệp của giao dịch bị hủy (đã vào kho nhưng dòng không được ghi) bị xóa ngay.

### Tự phục hồi

Mọi service chạy lâu có `restart: unless-stopped`, nên Docker khởi động lại chúng khi tiến trình thoát hoặc máy khởi động lại.
`dxos reconcile` chạy `docker compose up -d --no-build` theo các image đã ghim trong `.env`: container bị xóa được tạo lại, container lệch cấu hình được tạo lại theo khai báo, container đang đúng thì để nguyên.
Mỗi lượt có thay đổi ghi một dòng vào `deploy/reconcile.log`; lượt đến khi lượt trước còn chạy thì bỏ qua.
`update.sh` và `backup.sh` giữ cùng khóa `deploy/.reconcile.lock`, nên khi một lần cập nhật hoặc sao lưu đang chạy thì `dxos reconcile` bỏ qua lượt; hai script đợi tối đa 5 phút để lấy khóa rồi báo lỗi.
`dxos stop` để lại tệp `deploy/.stopped`, và `dxos reconcile` không bật lại stack có tệp này; `dxos start` xóa nó.

`dxos reconcile install` cài `dxos-reconcile.timer` (systemd user timer) chạy lệnh trên mỗi phút, `dxos reconcile uninstall` gỡ.
Máy không có systemd thì chạy `dxos reconcile` từ cron mỗi phút.
Timer của user chỉ chạy khi user còn phiên đăng nhập, trừ khi bật `loginctl enable-linger <user>`.

### Lịch sử chống sửa

Mỗi dòng `engine.record_event` có `hash = sha256(prev_hash || nội dung chuẩn hóa của dòng)`; các dòng được nối thành một chuỗi duy nhất, ghi dưới một khóa advisory.
Mỗi giờ backend ghi một điểm neo (id, hash, thời điểm) vào đối tượng `history/anchors.jsonl` của kho đối tượng, nằm ngoài Postgres và có trong bản sao lưu của kho; `dxos history anchor` ghi một điểm neo ngay.
Webhook sự kiện mang `hash` của từng dòng, nên bên nhận có thêm một điểm neo ngoài máy chủ.

`dxos history verify` (hay `GET /api/v1/history/verify`, ADMIN) tính lại cả chuỗi và so với các điểm neo.
Kết quả có `intact`, `head`, `firstBreak` (dòng đầu tiên bị sửa, bị chèn hoặc đứng sau một dòng bị xóa) và `mismatchedAnchors` (điểm neo mà dòng hay hash tương ứng không còn); lệnh thoát với mã 1 khi `intact` là `false`.
Chuỗi chống được việc sửa, xóa hay chèn một dòng bằng SQL, và việc cắt bớt đuôi lịch sử sau một điểm neo.
Người sửa lại cả chuỗi và cả đối tượng điểm neo trên máy chủ thì không phát hiện được, trừ khi so với hash mà bên nhận webhook đã giữ.
[`examples/history-witness/witness.py`](../examples/history-witness/witness.py) làm việc so đó: `save` ghi cặp `eventId` và `hash` vào một tệp ở nơi người sửa máy chủ không với tới, `compare` báo dòng nào đã đổi hash hoặc không còn.

### Hồ dữ liệu

`dxos archive <mô hình> [--before YYYY-MM-DD] [--out THƯ_MỤC]` ghi các hồ sơ đã tới trạng thái cuối (`decided_at` có giá trị, cũ hơn `--before` nếu có) và lịch sử của chúng ra Parquet ở `<THƯ_MỤC>/<mô hình>/<lần chạy>/` (mặc định `deploy/archive`).
Lệnh chạy DuckDB trong một container, đọc Postgres bằng tài khoản `POSTGRES_USER` của `.env` ở chế độ chỉ đọc, và ghi `manifest.json` gồm phiên bản mô hình, số dòng và sha256 từng tệp.
Dữ liệu trong cơ sở dữ liệu giữ nguyên.
`dxos archive check <thư mục>` đọc lại từng tệp và so số dòng và sha256 với manifest; lệch thì thoát với mã 1.
Lần chạy đầu DuckDB tải extension `postgres` từ mạng.

## Kiểm tra trước khi chạy

`./doctor.sh` (trong `deploy/`) chỉ đọc, không đổi gì và không in giá trị nào của `.env`.
Nó kiểm Docker và Compose, RAM, ổ đĩa, `deploy/.env` và các cổng.
`.env` được so với các biến `${NAME:?}` mà Compose bắt buộc phải có: biến thiếu hoặc để trống bị báo `FAIL`.
Biến có trong `.env` mà `.env.example` không liệt kê bị báo `WARN`.
Các cổng đã bị chiếm chỉ được kiểm khi stack chưa chạy.
Quét mã độc được báo là bật hay tắt; `STORAGE_SCAN_HOST=clamav` mà `COMPOSE_PROFILES` không có `av` bị báo `FAIL`, vì ClamAV sẽ không chạy và mọi tệp tải lên bị từ chối.
Thoát với mã 1 khi có `FAIL`, nên chạy trước `./update.sh` và `docker compose up -d` trên máy chủ.

## Tài nguyên và số bản sao

Đo ngày 04/11/2026 trên máy 12 CPU, 31 GB RAM, bằng `docker stats` mỗi 5 giây khi chạy tám script ở `deploy/dev` trên stack mới dựng.
Stack gồm tám service, không bật `clamav`.

| Service | RAM lúc rảnh (MiB) | RAM đỉnh (MiB) | CPU đỉnh (% của một nhân) |
|---|---|---|---|
| `backend` | 493 | 529 | 417 |
| `keycloak` | 424 | 517 | 365 |
| `monitor` | 287 | 296 | 7 |
| `apisix` | 210 | 222 | 6 |
| `postgres` | 86 | 115 | 166 |
| `seaweedfs` | 68 | 71 | 4 |
| `frontend`, `portal` | 10 mỗi service | 11 mỗi service | 2 |

Cả stack dùng khoảng 1,6 GiB RAM lúc rảnh và tối đa khoảng 1,7 GiB trong lúc chạy kiểm thử.
Image chiếm gần 1 GiB đĩa; dữ liệu Postgres sau lần chạy kiểm thử là khoảng 100 MiB.
CPU chỉ vọt lên ngắn hạn ở `backend` và `keycloak`; chưa đo với nhiều người dùng đồng thời hay với ít nhân hơn.

`backend` chạy một bản.
Chạy hai bản sau gateway thì mỗi bản giữ danh sách mô hình riêng trong bộ nhớ: sau một lần áp dụng, bản kia trả 404 cho mô hình mới và tiếp tục phục vụ mô hình đang bị giữ, cho đến khi khởi động lại cả hai.

## Giám sát từ ngoài

`./online-check.sh` (trong `deploy/`) gọi web, portal, trang đăng nhập và `/api/v1/system/status` qua địa chỉ công khai, rồi thoát với mã 1 khi một đường không trả 200 hoặc khi `backend` hoặc `storage` không `UP`.
Địa chỉ lấy từ `GATEWAY_PUBLIC_URL` trong `.env`; biến `PUBLIC_URL` trong shell ghi đè để kiểm địa chỉ khác.
Chạy theo lịch bằng cron, ví dụ mỗi 12 giờ: `0 */12 * * * cd /đường/dẫn/deploy && ./online-check.sh >> online-check.log 2>&1`.
Khi `.env` có `ALERT_WEBHOOK_URL_ADMIN` (hoặc `ALERT_WEBHOOK_URL`), script gửi một tin vào kênh khi site chuyển sang lỗi và một tin khi site trở lại, không gửi lặp trong lúc còn lỗi.
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

`verify` in số mô hình đã áp dụng và tổng số dòng trong các bảng `m_*` của bản dump.
Mô hình có phiên bản trong `engine.model_version` mà không có bảng nào trong schema `m_<mô hình>` làm `verify` báo `FAILED` kèm tên mô hình.
`verify` còn khôi phục bản chụp của kho vào một Docker volume tạm và khởi động một SeaweedFS tạm trên đó (filer HTTP chỉ mở trong container), rồi kiểm mọi khóa trong `engine.attachment` của bản dump đều có tệp.
`verify` không so nội dung từng tệp, không kiểm quy trình đang chạy và không kiểm quyền sau khi khôi phục.
Thiếu tệp thì `verify` báo `FAILED` kèm tên các tệp thiếu và thoát với mã 1.
`backup.sh` dump Postgres rồi mới chụp kho, và tệp bị gỡ chỉ rời kho sau `DXOS_FILE_DELETE_DELAY`, nên bản sao lưu luôn có tệp của mọi dòng trong bản dump, miễn là cả hai bước ngắn hơn khoảng đó.
Lượt chạy lâu hơn khoảng đó thì `backup.sh` in `WARNING`; khi đó tăng `DXOS_FILE_DELETE_DELAY`.

Kho nằm ở `deploy/backups` (đổi bằng `BACKUP_DIR`) hoặc ở một kho restic từ xa (`BACKUP_REPOSITORY`, ví dụ `s3:...`).
Kho cùng máy với dữ liệu không cứu được khi mất máy, nên chép nó ra ngoài (rsync, rclone) hoặc dùng kho từ xa.
Chạy theo lịch bằng cron, ví dụ mỗi đêm: `0 2 * * * cd /đường/dẫn/deploy && ./backup.sh >> backups/backup.log 2>&1`, và chạy `verify` mỗi tuần.
Máy chủ dùng tệp env khác `.env` thì đặt `ENV_FILE=.env.dev ./backup.sh`.
CI chạy một lượt backup và verify trên Postgres thật có một mô hình hai dòng và một tệp đính kèm thật ở job `Back up and restore` của workflow `Platform`, rồi thêm một dòng tệp không có trong kho và mong `verify` thất bại.

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
Bật bằng `dxos av on`, tắt bằng `dxos av off`: lệnh sửa `COMPOSE_PROFILES` và `STORAGE_SCAN_HOST` trong `.env`, rồi khởi động hoặc gỡ ClamAV và tạo lại backend nếu stack đang chạy.
Không dùng `dxos` thì bỏ dấu `#` ở `COMPOSE_PROFILES=av` và `STORAGE_SCAN_HOST=clamav` trong `deploy/.env`, rồi `docker compose up -d`.
Chữ ký virus tải về lần đầu mất vài phút và được giữ trong volume `clamav_data`.
Mỗi lần clamd nạp lại chữ ký (hằng ngày và sau khi khởi động) nó không quét được khoảng 10 giây, và người dùng gặp 503 trong lúc đó; chờ rồi tải lại.
Thử bằng tệp EICAR (chuỗi kiểm thử chuẩn của ngành diệt virus): tệp chỉ gồm đúng chuỗi đó, đổi tên `.pdf` rồi đính vào một yêu cầu; `deploy/dev/check_demo.py` tự làm bước này khi `STORAGE_SCAN_HOST` có giá trị.

## Màn hình giám sát

Dịch vụ `monitor` gom log gateway, `audit_log`, sự kiện đăng nhập của Keycloak và trạng thái dịch vụ vào một màn hình `/monitor/` cho ADMIN, kèm cảnh báo qua webhook khi có cấu hình.
Cách đọc, truy vết một sự cố, dữ liệu, cảnh báo và cấu hình ở [`backend/dx-monitor/README.md`](../backend/dx-monitor/README.md).
