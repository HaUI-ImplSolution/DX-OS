# Cloudflare cho DX-OS

Tổng hợp các khái niệm và cấu hình Cloudflare đang dùng cho máy chủ công khai `khuongdev.io.vn`.
Cấu hình nằm trên tài khoản Cloudflare, không nằm trong repo; các bước bật và kiểm ở [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md).
Máy chủ nằm sau NAT và không mở cổng nào ra Internet ngoài SSH.

## 1. Cách request đi vào hệ thống

```
Người dùng
  → Cloudflare (DNS, chứng chỉ HTTPS, WAF, giới hạn, Access)
  → Cloudflare Tunnel
  → container cloudflared trên máy chủ
  → APISIX hoặc Mattermost trong mạng Docker
```

Mọi request công khai đi qua Cloudflare trước khi chạm vào máy chủ.
Máy chủ tự kết nối ra Cloudflare qua tunnel, nên không cần IP công khai hay mở cổng vào.

## 2. Khái niệm

| Khái niệm | Là gì | Dùng để làm gì ở đây |
|---|---|---|
| Proxy (đám mây cam) | Cloudflare đứng giữa người dùng và máy chủ, domain trỏ về Cloudflare | Giấu máy chủ gốc, áp dụng HTTPS, chặn bot và giới hạn tốc độ |
| Tunnel (`cloudflared`) | Kết nối đi ra từ máy chủ tới Cloudflare, Cloudflare gửi request ngược xuống qua kết nối đó | Công khai dịch vụ mà không mở cổng |
| Published application route | Ánh xạ một hostname công khai sang một dịch vụ nội bộ của tunnel | Mỗi tên miền con trỏ tới một dịch vụ riêng |
| Transform Rule | Quy tắc sửa header của request ở edge | Đặt `X-Forwarded-Port` bằng `443` |
| Bot fight mode | Thử thách các request nghi là bot | Giảm quét và dò tự động |
| Managed ruleset | Bộ rule WAF do Cloudflare duy trì | Chặn các khai thác đã biết |
| Rate limiting rule | Giới hạn số request trong một khoảng thời gian theo một đặc điểm (IP) | Chặn gửi form đăng nhập dồn dập |
| Zero Trust, Access | Cổng đăng nhập đặt trước một ứng dụng, chỉ người trong policy mới qua | Bảo vệ các trang quản trị |
| Policy (Access) | Quy tắc Allow hoặc Block theo email, nhóm hoặc điều kiện khác | Chỉ quản trị viên và kỹ sư vào được trang quản trị |

## 3. Cấu hình đang dùng

### Domain và HTTPS

- Domain `khuongdev.io.vn` do Cloudflare quản lý DNS, gói Free.
- Chứng chỉ HTTPS do Cloudflare cấp cho domain gốc và các tên miền con cấp một.
- **Always Use HTTPS** bật: `http://` luôn chuyển 301 sang `https://`.
- **Minimum TLS Version 1.2**: TLS 1.0 và 1.1 bị từ chối.

### Tunnel `dxos-vps` và bốn route

| Hostname | Dịch vụ trong mạng Docker | Nội dung |
|---|---|---|
| `khuongdev.io.vn` | `http://apisix:9080` | Web app, API, Keycloak, portal, Swagger, link ảnh |
| `bi.khuongdev.io.vn` | `http://apisix:9081` | BI (Superset) |
| `files.khuongdev.io.vn` | `http://apisix:9082` | Quản trị lưu ảnh |
| `chat.khuongdev.io.vn` | `http://mattermost:8065` | Chat (Mattermost) |

Token của tunnel nằm trong `deploy/.env` trên máy chủ (`CLOUDFLARE_TUNNEL_TOKEN`), không nằm trong repo.
Cloudflare chỉ nhận người dùng qua cổng 443, nên mỗi dịch vụ có một tên miền con riêng.

### Request Header Transform Rule

- Rule **Forward public port 443** đặt header `X-Forwarded-Port` bằng `443` cho mọi request.
- Thiếu rule, gateway gắn cổng của listener vào header này.
- Khi đó Superset dựng địa chỉ chuyển hướng có thêm `:9081` và backend từ chối request ghi bằng `403 Invalid CORS request`.
- Rule này bắt buộc.

### Bot fight mode

- Bật ở Security → Settings → Bot traffic.
- Tải ảnh báo cáo và chẩn đoán AI chạy bình thường khi bật.

### Managed ruleset (gói Free)

- Luôn bật, không có công tắc.
- Chặn các khai thác đã biết (ví dụ cảnh báo php2shell trên trang Security rules).
- Không chặn SQLi hay XSS chung.
- Bộ đầy đủ cần gói trả phí.
- Backend dùng truy vấn tham số hóa, nên SQLi không phải rủi ro của ứng dụng.

### Rate limiting rule

- Một rule (gói Free cho một rule), áp dụng cho đường dẫn bắt đầu bằng `/realms/` và chứa `/login-actions/`, tức là form mật khẩu của Keycloak.
- Giới hạn 10 lượt trong 10 giây mỗi IP, vượt thì Block 10 giây.
- Response bị chặn có mã lỗi Cloudflare `1015` và header `retry-after: 10`.

### Cloudflare Access

Bốn ứng dụng Self-hosted dùng chung policy `Admins and engineers` (Allow theo email):

| Ứng dụng | Hostname | Đường dẫn |
|---|---|---|
| BI Superset | `bi.khuongdev.io.vn` | toàn bộ |
| Quản trị lưu ảnh | `files.khuongdev.io.vn` | toàn bộ |
| Keycloak Admin Console | `khuongdev.io.vn` | `admin/*` |
| Màn hình giám sát | `khuongdev.io.vn` | `monitor/*` |

- Request chưa có phiên Access nhận 302 sang trang đăng nhập Access (`*.cloudflareaccess.com`).
- Người dùng đăng nhập bằng phương thức đã bật: tài khoản Cloudflare hoặc mã gửi qua email.
- Màn hình giám sát còn đòi đăng nhập SSO Keycloak và vai ADMIN sau lớp Access.
- Gateway gửi sự kiện cho `monitor` trong mạng Docker, không qua Cloudflare, nên Access không ảnh hưởng.
- Không đặt Access cho web app, API, `/realms/*`, portal, chat và link ảnh, vì nông dân, sinh viên và mobile dùng các đường này.
- Thêm người vào: Zero Trust → Access controls → Policies → `Admins and engineers` → thêm email.
- Bị khóa ngoài thì gỡ ứng dụng `Keycloak Admin Console` ở trang Applications.

### Không dùng

- Chặn theo quốc gia: hội đồng chấm có thể truy cập từ nước ngoài.
- Swagger (`/swagger-ui/`) để công khai cho hội đồng xem API.
- WAF managed rules đầy đủ, HSTS: chưa bật.

## 4. Các lớp bảo vệ và việc mỗi lớp làm

| Lớp | Chặn hoặc ghi gì |
|---|---|
| Cloudflare edge | HTTPS bắt buộc, TLS tối thiểu 1.2, bot, khai thác đã biết, form đăng nhập dồn dập |
| Cloudflare Access | Người lạ vào trang quản trị (Superset, quản trị lưu ảnh, Keycloak Admin, màn hình giám sát) |
| APISIX | 120 lượt mỗi phút mỗi IP cho form đăng nhập và token, 600 lượt cho `/api/*`, kiểm token, log JSON kèm `X-Request-Id` |
| Keycloak | Khóa tạm tài khoản sau 5 lần sai mật khẩu, sự kiện đăng nhập kèm IP thật |
| Backend | Phân quyền theo vai, `audit_log` cho request bị từ chối và thao tác quản trị |

- Cloudflare thêm header `CF-Connecting-IP` với IP thật của người dùng.
- APISIX tin header này khi request đến từ mạng Docker.
- Header `CF-Connecting-IP` giả gửi từ ngoài bị Cloudflare từ chối ở edge (mã lỗi `1000`).
- IP thật có trong log gateway, sự kiện Keycloak và `audit_log`.

## 5. Mã lỗi và hiện tượng thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `error code: 1000` | Request có header `CF-Connecting-IP` giả | Không gửi header này; Cloudflare tự đặt |
| `error code: 1015`, `retry-after: 10` | Vượt rate limiting rule của form đăng nhập | Chờ 10 giây |
| `429` kèm `X-RateLimit-Limit` | Vượt giới hạn của APISIX | Chờ một phút hoặc tăng ngưỡng ở `deploy/apisix/common.yaml` |
| Chuyển sang trang `cloudflareaccess.com` | Chưa có phiên Access | Đăng nhập Access bằng email trong policy |
| `504` ở chẩn đoán AI | Gateway chờ AI tối đa 60 giây; lần gọi đầu sau khi khởi động lại phải nạp mô hình | Gọi lại; các lần sau khoảng 53 giây |
| Superset chuyển hướng sai cổng | Thiếu Transform Rule `X-Forwarded-Port` | Tạo lại rule ở mục 3 |
| Cloudflare 502 hoặc 1033 | `cloudflared` không chạy hoặc không tới được `apisix` | `docker compose ps` và `docker compose logs cloudflared` trên máy chủ |

## 6. Kiểm nhanh từ bên ngoài

```bash
curl -sI http://khuongdev.io.vn/portal/ | head -3          # 301 sang https
curl -sI https://bi.khuongdev.io.vn/ | grep -i location     # trang đăng nhập Access
curl -sI https://khuongdev.io.vn/admin/ | grep -i location  # trang đăng nhập Access
curl -s https://khuongdev.io.vn/cdn-cgi/trace | grep ip=    # IP mà Cloudflare thấy
```

Gửi nhiều `POST` liên tiếp vào `/realms/dxos/login-actions/authenticate` sẽ thấy `1015`.
Gửi hơn 120 `POST` trong một phút vào `/realms/dxos/protocol/openid-connect/token` sẽ thấy `429` của APISIX.
