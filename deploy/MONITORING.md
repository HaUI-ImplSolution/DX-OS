# Giám sát và nhật ký

Tài liệu cho người theo dõi hệ thống: log của gateway, sự kiện đăng nhập, giới hạn tốc độ, audit trong backend, số đo cho Prometheus và màn hình giám sát.
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

## Số đo cho Prometheus

Dịch vụ lõi xuất số đo vận hành theo định dạng Prometheus ở `GET /actuator/prometheus` của cổng `8081` trong mạng Docker, để Prometheus, Grafana Agent hoặc công cụ tương tự đọc.
Công cụ giám sát bên ngoài là tùy chọn: stack không kèm Prometheus hay Grafana.

Truy cập:

- Cổng API không chuyển đường dẫn `/actuator` ra ngoài, nên endpoint chỉ đọc được từ trong mạng Docker của stack.
- Endpoint đòi HTTP Basic với người dùng `metrics` và mật khẩu `METRICS_PASSWORD` trong `deploy/.env`; `make_env.sh` sinh mật khẩu ngẫu nhiên, và để trống thì endpoint đóng với mọi người.
- Chỉ endpoint này và `/actuator/health` được mở; các endpoint khác của Actuator không mở.

Số đo do dịch vụ lõi tính từ cơ sở dữ liệu mỗi 30 giây (đổi bằng `METRICS_REFRESH_MS`, tính bằng mili giây), nên một lần đọc không truy vấn dữ liệu.
Nhãn chỉ là tên mô hình, tên thực thể, trạng thái và kết quả áp dụng do các tệp mô hình khai báo, không bao giờ là hồ sơ, người dùng hay chữ tự do, nên số chuỗi theo số mô hình chứ không theo số hồ sơ.

| Số đo | Kiểu | Nhãn | Ý nghĩa |
|---|---|---|---|
| `dxos_records` | gauge | `model`, `entity`, `status` | Số hồ sơ của thực thể có quy trình theo trạng thái; trạng thái chưa có hồ sơ vẫn hiện số 0 |
| `dxos_records_overdue` | gauge | `model`, `entity` | Số hồ sơ mà người xử lý đã quá hạn |
| `dxos_outbox_pending` | gauge | không | Số sự kiện gửi ra ngoài chưa chuyển được |
| `dxos_outbox_retrying` | gauge | không | Số sự kiện chưa chuyển được và đã thử lỗi ít nhất một lần |
| `dxos_outbox_oldest_age_seconds` | gauge | không | Tuổi của sự kiện chưa chuyển cũ nhất, bằng giây; 0 khi hàng đợi trống |
| `dxos_model_applies_total` | counter | `result` (`done`, `failed`) | Số lần áp dụng tệp mô hình theo kết quả, tính từ lịch sử áp dụng |
| `dxos_models` | gauge | không | Số mô hình đang chạy |

Dịch vụ cũng xuất các số đo chuẩn của JVM, cơ sở dữ liệu và HTTP (`jvm_*`, `hikaricp_*`, `http_server_requests_*`); nhãn `uri` của chúng là mẫu đường dẫn như `/api/v1/data/{model}/{entity}`, không chứa mã hồ sơ.

Thử đọc từ máy chủ của stack:

```bash
cd deploy
PASSWORD=$(grep '^METRICS_PASSWORD=' .env | cut -d= -f2)
docker compose exec backend wget -qO- --header "Authorization: Basic $(printf 'metrics:%s' "$PASSWORD" | base64 -w0)" http://localhost:8081/actuator/prometheus | grep '^dxos_'
```

Cấu hình Prometheus chạy trong cùng mạng Docker của stack (container tham gia mạng `<tên-dự-án>_default`, xem bằng `docker network ls`):

```yaml
scrape_configs:
  - job_name: dxos
    metrics_path: /actuator/prometheus
    scrape_interval: 30s
    basic_auth:
      username: metrics
      password_file: /etc/prometheus/dxos-metrics-password
    static_configs:
      - targets: ["backend:8081"]
```

Tệp `dxos-metrics-password` chứa mật khẩu trên một dòng, không có ký tự xuống dòng ở cuối.

Truy vấn Grafana tham khảo, mỗi dòng là một panel:

| Panel | Truy vấn |
|---|---|
| Hồ sơ theo trạng thái | `sum by (model, entity, status) (dxos_records)` |
| Hồ sơ quá hạn | `sum by (model, entity) (dxos_records_overdue)` |
| Tuổi hàng đợi sự kiện | `dxos_outbox_oldest_age_seconds` |
| Sự kiện chưa chuyển được | `dxos_outbox_pending` và `dxos_outbox_retrying` |
| Lần áp dụng thất bại trong 24 giờ | `increase(dxos_model_applies_total{result="failed"}[24h])` |

Quy tắc cảnh báo gợi ý:

```yaml
groups:
  - name: dxos
    rules:
      - alert: DxosRecordsOverdue
        expr: sum by (model, entity) (dxos_records_overdue) > 0
        for: 15m
      - alert: DxosEventQueueStuck
        expr: dxos_outbox_oldest_age_seconds > 600
        for: 5m
      - alert: DxosApplyFailed
        expr: increase(dxos_model_applies_total{result="failed"}[1h]) > 0
```

## Màn hình giám sát

Dịch vụ `monitor` gom log gateway, `audit_log`, sự kiện đăng nhập của Keycloak và trạng thái dịch vụ vào một màn hình `/monitor/` cho ADMIN, kèm cảnh báo qua webhook khi có cấu hình.
Cách đọc, truy vết một sự cố, dữ liệu, cảnh báo và cấu hình ở [`backend/dx-monitor/README.md`](../backend/dx-monitor/README.md).
