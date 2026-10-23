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

Ví dụ (cần `jq`):

```bash
cd deploy
docker compose logs apisix --since 1h --no-log-prefix | jq -c 'select(.status >= 400)'
docker compose logs apisix --since 24h --no-log-prefix | jq -r '.remote_addr' | sort | uniq -c | sort -rn | head
docker compose logs apisix --no-log-prefix | jq -c 'select(.request_id == "<mã từ header X-Request-Id>")'
docker compose logs apisix --since 1h --no-log-prefix | jq -c 'select(.status == 429)'
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
Gỡ khóa sớm: **Users** → chọn người dùng → tắt công tắc **Temporarily locked**.

IP trong sự kiện là IP thật chỉ khi máy chủ chạy với overlay Cloudflare (`docker-compose.cloudflared.yml`), nơi Keycloak đọc `X-Forwarded-For` từ gateway.
