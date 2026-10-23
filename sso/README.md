# SSO (Keycloak)

Đăng nhập một lần dùng chung cho Backend, web, mobile và Superset.
Keycloak (Apache-2.0) build từ gói phát hành trên nền Ubuntu ([`Dockerfile`](./Dockerfile)), chạy được cả trên CPU ảo không có x86-64-v2; Keycloak tự import realm `dxos` từ [`realm-dxos.json`](./realm-dxos.json) lúc khởi động; service khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).
Keycloak không publish cổng ra host: gateway APISIX chuyển tiếp `/realms`, `/resources`, `/admin`, `/js` tới Keycloak.

## Phạm vi

| Thành phần | Cách dùng |
|---|---|
| Backend | OAuth2 Resource Server, xác thực token bằng `jwk-set-uri` |
| Web | Authorization Code + PKCE, tự chuyển sang Keycloak khi chưa có phiên |
| Mobile (chỉ FARMER) | Client public `farm-mobile`, PKCE, không direct access grants ([`mobile/README.md`](../mobile/README.md)) |
| Superset | `AUTH_OAUTH`, role realm map sang role Superset ([`bi/README.md`](../bi/README.md)) |
| Mattermost | Không dùng SSO: Team Edition không hỗ trợ OIDC với IdP tùy ý ([`chat/README.md`](../chat/README.md)) |

## Realm `dxos`

- Database Postgres riêng `keycloak`; Backend tạo role `keycloak_app` và database lúc khởi động (`KeycloakDatabaseSeeder`).
- Realm role `ADMIN`; client public `dxos-web`, `farm-mobile` (Standard Flow + PKCE, không secret); client confidential `dxos-superset` (secret `SUPERSET_OIDC_CLIENT_SECRET`) và `dxos-backend-admin` (service account của Backend).
- Realm import không chứa vai hay tài khoản của app: vai (`FARMER`, `ENGINEER`, `STUDENT`... theo `RoleProvider`) do Backend tạo lúc khởi động, tài khoản demo do `deploy/demo/seed_demo.py` tạo.
- Keycloak chỉ import realm khi realm chưa tồn tại; đổi `realm-dxos.json` hoặc secret sau đó thì sửa trong Admin Console hoặc `deploy/reset.sh`.
- `dxos-backend-admin` chỉ giữ `manage-users`, `view-users`, `view-realm`, `manage-realm`; Backend dùng nó để tạo tài khoản workspace và vai của app qua Admin REST API.

## Token và phân quyền

- `dxos-web` và `farm-mobile` có mapper `dxos-api-audience` thêm `dxos-api` vào `aud`; Backend (`security.jwt.audience`) trả `401` cho token thiếu audience.
- Token chỉ có role `FARMER` phải có `azp` thuộc `security.farmer-client-ids` (mặc định `farm-mobile`), nếu không Backend trả `403`; web hiện màn "dùng ứng dụng di động", Superset từ chối.
- Phạm vi dữ liệu của FARMER theo nông hộ gắn với claim `sub`, không phụ thuộc tên hiển thị.
  Tài khoản chưa gắn hộ thì admin tạo hộ kèm tài khoản hoặc gắn tài khoản có sẵn ở trang "Nông hộ" của web.

## Tài khoản demo

Chỉ để demo; đổi mật khẩu trước khi triển khai ngoài môi trường dev.

| Username | Mật khẩu | Role |
|---|---|---|
| `farmer1` | `farmer123` | FARMER |
| `engineer1` | `engineer123` | ENGINEER |
| `admin1` | `admin123` | ADMIN |
| `student1`, `student2` | `student123` | STUDENT |
| `lecturer1` | `lecturer123` | LECTURER |
| `tech1`, `tech2` | `tech123` | TECHNICIAN |

Farm dùng ba tài khoản đầu, Lab dùng `admin1` và các nhóm còn lại; `seed_demo.py` tạo tài khoản của app đang chạy.
Quản lý user qua Admin Console `http://localhost:${APISIX_HOST_PORT}/admin` (`KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD`).

## Client mobile

- Redirect URI: `dxfarm://*` (app đã build) và `exp://*` (Expo Go, chỉ dev).
- Điện thoại truy cập Keycloak qua IP LAN của máy dev, hoặc qua `GATEWAY_PUBLIC_URL` khi máy chủ công khai.
- Realm import lấy `GATEWAY_PUBLIC_URL` và `SUPERSET_PUBLIC_URL` làm redirect URI của `dxos-web` và `dxos-superset` (cùng `localhost` cho dev), nên đổi `PUBLIC_HOST` trước lần khởi động đầu tiên của Keycloak.
- Mặc định Keycloak không đặt `KC_HOSTNAME` nên URL theo địa chỉ request, web (`localhost:9080`) và điện thoại (`<IP-LAN>:9080`) chạy song song không cần đổi `.env`.
- Overlay `docker-compose.cloudflared.yml` đặt `KC_HOSTNAME` bằng `GATEWAY_PUBLIC_URL`, nên web và điện thoại đều vào bằng địa chỉ HTTPS công khai.
- Volume Postgres cũ chưa có client `farm-mobile`: thêm qua Admin Console (cấu hình như `realm-dxos.json`) hoặc xóa `deploy/data/postgres` để import lại.

## Hostname nội bộ và hostname trình duyệt

Trình duyệt gọi Keycloak qua gateway (`http://localhost:9080`), Backend và Superset trong mạng docker gọi `http://keycloak:8080`, nên claim `iss` của token mang hostname trình duyệt.
Vì vậy Backend dùng `jwk-set-uri` (chỉ lấy public key) thay cho `issuer-uri` và không kiểm `iss`; Superset chỉ định từng endpoint (`authorize_url` hostname trình duyệt, `access_token_url`/`api_base_url`/`jwks_uri` hostname nội bộ) và lấy thông tin user từ access token thay vì `/userinfo`.
Triển khai thật cần reverse proxy để hai bên dùng cùng một hostname, khi đó bật lại `issuer-uri`.

## Đăng xuất một lần

- Web → Superset: đăng xuất web kết thúc phiên Keycloak, Keycloak gọi front-channel logout của `dxos-superset` (`http://localhost:8088/logout/`).
- Superset → Web: `LOGOUT_REDIRECT_URL` trong `bi/superset_config.py` kết thúc phiên Keycloak; web đang mở tự phát hiện qua `monitorSession` của `oidc-client-ts` và đăng xuất.
- Đăng xuất ở Keycloak Account Console kích hoạt cả hai.

Đây là cơ chế phía trình duyệt, không phải backchannel logout chuẩn OIDC: không hoạt động khi không có tab nào mở, vì session của Superset là cookie ký sẵn không thu hồi được từ server.
