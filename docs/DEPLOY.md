# Triển khai lên máy chủ

Tài liệu này đưa DX-OS lên một máy chủ Linux để người dùng vào bằng `https://tên-miền`.
Caddy làm reverse proxy trên máy chủ, ngoài Docker, và tự lấy, gia hạn chứng chỉ Let's Encrypt.

```text
trình duyệt → Caddy (cổng 80, 443) → 127.0.0.1:9080 (gateway APISIX) → các dịch vụ
```

Đăng nhập giao diện web từ máy khác cần HTTPS, vì trình duyệt chỉ cho tạo mã PKCE trên HTTPS hoặc `localhost`.
Cấu hình Caddy nằm ngoài repository; DX-OS không kèm và không cần nó khi chạy ở `localhost`.
Lệnh `dxos`, danh sách biến `.env`, sao lưu và xử lý sự cố ở [`OPERATIONS.md`](./OPERATIONS.md).

## Chuẩn bị máy chủ

- Linux có Docker và Compose v2 ([cách cài](https://docs.docker.com/engine/install/)), `curl`, `tar` và `python3`.
  Tài khoản chạy `dxos` gọi được `docker` không cần `sudo` (thuộc nhóm `docker`).
- RAM từ 8 GB, đĩa trống nên từ 40 GB; số đo ở [Tài nguyên và số bản sao](./OPERATIONS.md#tài-nguyên-và-số-bản-sao).
- IP công khai cố định (trên AWS là Elastic IP) và một bản ghi DNS loại A của tên miền trỏ tới IP đó.
  DNS đặt ở Cloudflare thì để chế độ DNS only, để Let's Encrypt nối thẳng tới Caddy.
- Tường lửa (Security Group của AWS, hoặc `ufw`) mở TCP 80 và 443 từ mọi nơi, và SSH từ địa chỉ của người quản trị.
  Không mở cổng 9080; PostgreSQL mặc định chỉ nghe trên `127.0.0.1`.

## Máy chủ có tên miền và HTTPS

Làm lần lượt sáu bước trên máy chủ, thay `ten-mien.example` bằng tên miền của bạn.
Địa chỉ phải đúng trước lần `dxos start` đầu tiên, vì realm Keycloak chỉ import một lần.

### 1. Cài DX-OS

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
```

Bộ cài chép tệp vào `~/.dxos` và chưa khởi động gì.
Nó báo `~/.local/bin` chưa có trong `PATH` thì thêm dòng `export PATH="$HOME/.local/bin:$PATH"` vào `~/.bashrc` rồi mở lại phiên.

### 2. Đổi biến trong `.env`

```bash
nano ~/.dxos/deploy/.env
```

Tệp đã có sẵn mọi mật khẩu và khóa ngẫu nhiên, nên chỉ sửa sáu biến dưới đây.
Mỗi biến đã có một dòng trong tệp: sửa ngay dòng đó, và bỏ dấu `#` trước `KEYCLOAK_HOSTNAME`.

```bash
PUBLIC_HOST=ten-mien.example
GATEWAY_PUBLIC_URL=https://ten-mien.example
KEYCLOAK_HOSTNAME=${GATEWAY_PUBLIC_URL}
APISIX_HOST_PORT=127.0.0.1:9080
CORS_ALLOWED_ORIGINS=https://ten-mien.example
STAFF_OTP_FLOW=CONDITIONAL
```

| Biến | Tác dụng |
|---|---|
| `PUBLIC_HOST` | Tên máy chủ mà trình duyệt dùng |
| `GATEWAY_PUBLIC_URL` | Địa chỉ công khai đầy đủ, dùng cho đăng nhập, link tệp và link hồ sơ |
| `KEYCLOAK_HOSTNAME` | Keycloak công bố địa chỉ này cho trình duyệt và đọc các header `X-Forwarded-*` của proxy |
| `APISIX_HOST_PORT` | Chỉ tiến trình trên chính máy chủ gọi được gateway |
| `CORS_ALLOWED_ORIGINS` | Cho giao diện web ghi dữ liệu; thiếu thì bị trả `403 Invalid CORS request`, vì backend chỉ thấy HTTP từ proxy |
| `STAFF_OTP_FLOW` | Vai `ADMIN` phải nhập mã một lần khi đăng nhập |

Kiểm tệp, rồi sửa hết các dòng `FAIL` trước khi đi tiếp:

```bash
dxos doctor
```

### 3. Cài Caddy

Trên Ubuntu hoặc Debian (bản phân phối khác xem [hướng dẫn của Caddy](https://caddyserver.com/docs/install)):

```bash
sudo apt update && sudo apt install -y caddy
sudo nano /etc/caddy/Caddyfile
```

Thay toàn bộ nội dung `Caddyfile` bằng:

```text
ten-mien.example {
    encode zstd gzip
    reverse_proxy 127.0.0.1:9080 {
        header_up X-Forwarded-Port 443
        header_up CF-Connecting-IP {remote_host}
    }
}
```

`X-Forwarded-Port 443` giữ cho các địa chỉ chuyển hướng của Keycloak không mang cổng 9080.
`CF-Connecting-IP` là header gateway đọc địa chỉ client; Caddy ghi nó bằng địa chỉ thật, nên giới hạn tốc độ và nhật ký tính theo từng client và client không tự khai được địa chỉ.
Proxy khác Caddy phải ghi đè cả `X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-Port` và `CF-Connecting-IP`, không chuyển tiếp giá trị client gửi.
Gateway tin các header này, nên chỉ proxy được gọi tới nó (`APISIX_HOST_PORT=127.0.0.1:9080`).
Nạp cấu hình khi DNS đã trỏ đúng, vì Let's Encrypt kiểm tên miền ngay lúc đó:

```bash
sudo systemctl enable caddy
sudo systemctl restart caddy
```

### 4. Khởi động

```bash
dxos start
```

Lần đầu lệnh tải image, chờ mọi dịch vụ sẵn sàng, rồi in mật khẩu tạm của tài khoản `admin` một lần.
Đưa tệp mô hình vào ngay lúc khởi động bằng `dxos start <tệp>`, xem [Tệp mô hình lúc khởi động](./OPERATIONS.md#tệp-mô-hình-lúc-khởi-động).
Máy trình diễn cần dữ liệu thử thì chạy `dxos demo`.

### 5. Kiểm tra

```bash
dxos status
~/.dxos/deploy/online-check.sh
curl -fsS https://ten-mien.example/realms/dxos/.well-known/openid-configuration | grep -c '"http://'
```

`online-check.sh` in `OK` ở mọi dòng.
Lệnh cuối in `0`, tức Keycloak không công bố địa chỉ `http://` nào.
Sau đó mở `https://ten-mien.example/console`, đăng nhập `admin` và đổi mật khẩu.

### 6. Tự phục hồi, sao lưu và giám sát

Cho stack tự phục hồi, kể cả khi không có phiên đăng nhập nào:

```bash
dxos reconcile install
sudo loginctl enable-linger "$USER"
```

Chụp bản sao lưu đầu tiên và kiểm nó:

```bash
cd ~/.dxos/deploy
./backup.sh
./backup.sh verify
```

Cất `BACKUP_PASSWORD` của `.env` ở nơi khác máy chủ.
Rồi chạy `crontab -e` và thêm hai dòng, để sao lưu mỗi đêm và kiểm trang mỗi 12 giờ:

```text
0 2 * * * cd $HOME/.dxos/deploy && ./backup.sh >> backup.log 2>&1
0 */12 * * * cd $HOME/.dxos/deploy && ./online-check.sh >> online-check.log 2>&1
```

Chi tiết ở [Tự phục hồi](./OPERATIONS.md#tự-phục-hồi), [Sao lưu và khôi phục](./OPERATIONS.md#sao-lưu-và-khôi-phục) và [Giám sát từ ngoài](./OPERATIONS.md#giám-sát-từ-ngoài).

## Đổi cấu hình sau khi đã chạy

Sửa `~/.dxos/deploy/.env` rồi chạy `dxos start` để các dịch vụ nhận giá trị mới.

Đổi tên miền sau lần khởi động đầu tiên cần thêm bốn bước, vì realm đã import giữ địa chỉ cũ:

1. Sửa các biến ở bước 2 và tên miền trong `Caddyfile`, rồi `sudo systemctl reload caddy` và `dxos start`.
2. Mở [Keycloak Admin Console](./OPERATIONS.md#keycloak-admin-console), chọn realm `dxos`.
3. Ở hai client `dxos-web` và `dxos-monitor`, đổi tên miền trong Valid redirect URIs, Valid post logout redirect URIs và Web origins.
4. Đóng lại Admin Console.

## Cập nhật bản mới

```bash
dxos backup
dxos update            # bản mới nhất; chọn bản bằng dxos update vX.Y.Z
```

`.env`, `data/` và `apisix/local.yaml` được giữ nguyên.
Cơ sở dữ liệu của Keycloak không hạ phiên bản được, nên luôn sao lưu trước.

## Lỗi khi triển khai

| Triệu chứng | Cách xử lý |
|---|---|
| Trình duyệt báo lỗi chứng chỉ | Kiểm bản ghi DNS, cổng 80 và 443, chế độ DNS only; đọc `sudo journalctl -u caddy -n 50` rồi `sudo systemctl restart caddy` |
| Trang trả `502` | Stack chưa sẵn sàng hoặc `APISIX_HOST_PORT` khác `127.0.0.1:9080`; chạy `dxos status` |
| Lưu hồ sơ trả `403 Invalid CORS request` | `CORS_ALLOWED_ORIGINS` đúng `https://tên-miền`, không có dấu `/` cuối; rồi `dxos start` |
| Trang báo `Đăng nhập thất bại` sau khi nhập mật khẩu | Chạy lệnh cuối ở bước 5; khác `0` thì kiểm `KEYCLOAK_HOSTNAME` rồi `dxos start` |
| Keycloak báo `Invalid parameter: redirect_uri` | Địa chỉ được đặt sau lần khởi động đầu tiên; xem [Đổi cấu hình sau khi đã chạy](#đổi-cấu-hình-sau-khi-đã-chạy) |
