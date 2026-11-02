# SSO (Keycloak)

Đăng nhập một lần dùng chung cho dịch vụ lõi, web và màn hình giám sát.
Keycloak (Apache-2.0) build từ gói phát hành trên nền Ubuntu ([`Dockerfile`](./Dockerfile)), chạy được cả trên CPU ảo không có x86-64-v2; Keycloak tự import realm `dxos` từ [`realm-dxos.json`](./realm-dxos.json) lúc khởi động; service khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).
Keycloak không publish cổng ra host: gateway APISIX chuyển tiếp `/realms`, `/resources`, `/admin`, `/js` tới Keycloak.
Cơ sở dữ liệu `keycloak` và vai `keycloak_app` do [`deploy/postgres/init`](../deploy/postgres/init) tạo khi thư mục dữ liệu Postgres được khởi tạo lần đầu.

## Phạm vi

| Thành phần | Cách dùng |
|---|---|
| Dịch vụ lõi | OAuth2 Resource Server, xác thực token bằng `jwk-set-uri`; tạo vai của mô hình qua Admin API bằng client `dxos-backend-admin` |
| Web | Client `dxos-web`, Authorization Code + PKCE, tự chuyển sang Keycloak khi chưa có phiên |
| Màn hình giám sát | Client `dxos-monitor`, chỉ cho vai `ADMIN` |

## Realm `dxos`

- Realm import chỉ có vai `ADMIN` và các client trên; vai của một mô hình do dịch vụ lõi tạo khi áp dụng mô hình.
- Realm chỉ import lần đầu; Keycloak đã có dữ liệu thì thêm client hoặc vai qua Admin Console (cấu hình như `realm-dxos.json`, secret của `dxos-monitor` là `MONITOR_OIDC_CLIENT_SECRET`) hoặc dựng lại bằng `deploy/reset.sh`.
- Realm import lấy `GATEWAY_PUBLIC_URL` làm redirect URI của `dxos-web` (cùng `localhost` cho dev), nên đổi `PUBLIC_HOST` trước lần khởi động đầu tiên của Keycloak.

## Xác thực hai bước và mật khẩu

- Realm đặt chính sách mật khẩu `length(8) and notUsername`.
- Luồng đăng nhập `dxos browser` hỏi mã TOTP với người có vai `ADMIN`; lần đầu họ được hướng dẫn quét mã QR vào ứng dụng xác thực.
- Biến `STAFF_OTP_FLOW` trong `deploy/.env` bật (`CONDITIONAL`) hoặc tắt (`DISABLED`, mặc định) bước này.
  Mặc định tắt để demo và CI đăng nhập được chỉ bằng mật khẩu.
  Máy chủ thật đặt `STAFF_OTP_FLOW=CONDITIONAL` trước lần khởi động đầu, vì Keycloak chỉ import realm một lần.
- Realm đã chạy mà chưa có luồng này: dựng lại bằng `deploy/reset.sh` (xóa dữ liệu), hoặc tạo tay trong Admin Console.
  Cách tạo tay: Authentication, nhân đôi luồng `browser` thành `dxos browser`, rồi trong luồng con `forms` thêm một luồng con Conditional cho vai `ADMIN` gồm hai bước "Condition - user role" (Required, chọn vai) và "OTP Form" (Required), cuối cùng chọn Bind flow, Browser flow.
- Mất điện thoại: quản trị xóa credential OTP của người đó trong Admin Console (Users, Credentials), lần đăng nhập sau họ đăng ký lại.

## Token và phân quyền

- Mật khẩu chỉ nằm ở Keycloak, lưu dạng băm Argon2id (mặc định của Keycloak 26).
- `dxos-web` có mapper `dxos-api-audience` thêm `dxos-api` vào `aud`; dịch vụ lõi (`security.jwt.audience`) trả `401` cho token thiếu audience.
- `dxos-web` có mapper `groups` đưa tên nhóm vào token; quyền `unit` của mô hình dựa trên claim này, nên đổi nhóm chỉ có hiệu lực khi người dùng lấy token mới.
- Áp dụng mô hình tạo vai và nhóm bằng tài khoản dịch vụ `dxos-backend-admin`, và không xóa chúng khi mô hình bỏ đi.
- Dịch vụ lõi đọc vai từ `realm_access.roles` của token và tính quyền theo mô hình; chủ sở hữu bản ghi là claim `sub`, không phụ thuộc tên hiển thị.

## Máy chủ công khai

Trên máy chủ công khai, `/admin/*` (Admin Console) nằm sau Cloudflare Access.
`/realms/*` mở cho người dùng web, và gateway giới hạn 120 lượt mỗi phút mỗi IP cho form mật khẩu và token endpoint.
Overlay `docker-compose.cloudflared.yml` đặt `KC_HOSTNAME` bằng `GATEWAY_PUBLIC_URL`; mặc định Keycloak không đặt `KC_HOSTNAME` nên URL theo địa chỉ request.

## Sự kiện đăng nhập và khóa tạm

- Realm import bật sự kiện người dùng (giữ 90 ngày), sự kiện quản trị kèm chi tiết, và khóa tạm sau 5 lần đăng nhập sai (chờ 1 phút, tăng dần, tối đa 15 phút, không khóa vĩnh viễn).
- Cách xem sự kiện: [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md).
- Realm chỉ import lần đầu, nên Keycloak đã có dữ liệu áp tay trong Admin Console (realm `dxos`):
  1. **Realm settings → Events → User events settings**: bật **Save events**, **Expiration** 90 ngày, chọn các loại `LOGIN`, `LOGIN_ERROR`, `LOGOUT`, `UPDATE_PASSWORD`, `RESET_PASSWORD` và loại bị khóa.
  2. **Realm settings → Events → Admin events settings**: bật **Save events** và **Include representation**.
  3. **Realm settings → Security defenses → Brute force detection**: bật, chế độ **Lockout temporarily**, **Max login failures** 5, **Wait increment** 1 phút, **Max wait** 15 phút.
- IP trong sự kiện là IP thật khi chạy overlay Cloudflare, nơi Keycloak đọc `X-Forwarded-For` do gateway đặt.

## Hostname nội bộ và hostname trình duyệt

Trình duyệt gọi Keycloak qua gateway, dịch vụ lõi trong mạng docker gọi `http://keycloak:8080`, nên claim `iss` của token mang hostname trình duyệt.
Vì vậy dịch vụ lõi dùng `jwk-set-uri` (chỉ lấy public key) thay cho `issuer-uri` và không kiểm `iss`.
Triển khai thật cần reverse proxy để hai bên dùng cùng một hostname, khi đó bật lại `issuer-uri`.

## Đăng xuất

Đăng xuất ở web kết thúc phiên Keycloak; web đang mở ở tab khác tự phát hiện qua `monitorSession` của `oidc-client-ts` và đăng xuất.
