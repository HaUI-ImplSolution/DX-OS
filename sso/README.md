# SSO (Keycloak)

Đăng nhập một lần dùng chung cho dịch vụ lõi, web và màn hình giám sát.
Keycloak (Apache-2.0) build từ gói phát hành trên nền Ubuntu ([`Dockerfile`](./Dockerfile)), chạy được cả trên CPU ảo không có x86-64-v2; Keycloak tự import realm `dxos` từ [`realm-dxos.json`](./realm-dxos.json) lúc khởi động; service khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).
Keycloak không publish cổng ra host: gateway APISIX chuyển tiếp `/realms`, `/resources`, `/js` tới Keycloak.
Admin Console (`/admin`) mặc định không được mở ra gateway; đặt `KEYCLOAK_ADMIN_CONSOLE=true` trong `deploy/.env` rồi tạo lại gateway để mở.
Dịch vụ lõi vẫn gọi Admin API của Keycloak qua mạng nội bộ, không phụ thuộc công tắc này.
Cơ sở dữ liệu `keycloak` và vai `keycloak_app` do [`deploy/postgres/init`](../deploy/postgres/init) tạo khi thư mục dữ liệu Postgres được khởi tạo lần đầu.

## Phạm vi

| Thành phần | Cách dùng |
|---|---|
| Dịch vụ lõi | OAuth2 Resource Server, xác thực token bằng `jwk-set-uri`; tạo client và vai của mô hình, và quản lý người dùng, qua Admin API bằng client `dxos-backend-admin` |
| Web | Client `dxos-web`, Authorization Code + PKCE, tự chuyển sang Keycloak khi chưa có phiên |
| Giao diện ngoài | Public client `dxos-browser-<tên>` do `dxos client browser add` tạo, Authorization Code + PKCE S256, redirect URI và web origin chính xác |
| Màn hình giám sát | Client `dxos-monitor`, chỉ cho vai `ADMIN` |
| Dòng lệnh `dxos` | Client bí mật `dxos-cli`, client credentials; tài khoản dịch vụ giữ vai `ADMIN`; `dxos plan` và `dxos apply` lấy token bằng `DXOS_CLI_SECRET` trong `deploy/.env`, và lịch sử áp dụng ghi tài khoản dịch vụ này |

## Realm `dxos`

- Realm import chỉ có vai `ADMIN` và các client trên (realm đã import từ trước chưa có `dxos-cli`: thêm ở Admin Console với tài khoản dịch vụ giữ vai `ADMIN`, hoặc dựng lại bằng `deploy/reset.sh`); client `dxos-m-<mô hình>` và vai của một mô hình do dịch vụ lõi tạo khi áp dụng mô hình.
- Tài khoản dịch vụ `dxos-backend-admin` có ở `realm-management` các quyền `manage-users`, `view-users`, `view-realm`, `manage-realm`, `manage-clients`, `view-clients` và `query-clients`.
  Realm đã import từ trước chưa có ba quyền client: cấp tay ở Admin Console (Clients, `dxos-backend-admin`, Service accounts roles, Assign role, lọc theo client `realm-management`), nếu không áp dụng mô hình báo lỗi 403 ở bước `ENSURE_CLIENT`.
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
- Client `dxos-browser-<tên>` có cùng mapper audience, nhóm và scope vai như `dxos-web`, nhưng chỉ nhận các redirect URI đã đưa cho lệnh đăng ký và không có secret, password grant hay service account.
- Áp dụng mô hình tạo client, vai và nhóm bằng tài khoản dịch vụ `dxos-backend-admin`, và không xóa chúng khi mô hình bỏ đi; Hủy bỏ lần áp dụng đầu của một mô hình xóa client `dxos-m-<mô hình>` (nhóm đơn vị được giữ vì các mô hình dùng chung).
- Vai của mô hình nằm ở client riêng `dxos-m-<mô hình>` (không cho ai đăng nhập), nên hai mô hình dùng được cùng tên vai mà người giữ không chung.
- `dxos-web` giữ `fullScopeAllowed` mặc định (bật) và scope mặc định `roles`, nên token mang vai client ở `resource_access`; tắt một trong hai thì người dùng mất quyền vào mọi mô hình.
- Dịch vụ lõi đọc vai `ADMIN` từ `realm_access.roles` và vai của mô hình từ `resource_access.dxos-m-<mô hình>.roles` của token, rồi tính quyền theo mô hình; chủ sở hữu bản ghi là claim `sub`, không phụ thuộc tên hiển thị.

## Máy chủ công khai

Trên máy chủ công khai, giữ `KEYCLOAK_ADMIN_CONSOLE=false`; khi cần mở `/admin/*` (Admin Console) thì đặt nó sau một lớp xác thực của hạ tầng phía trước, không mở thẳng ra Internet.
`/realms/*` mở cho người dùng web, và gateway giới hạn 120 lượt mỗi phút mỗi IP cho form mật khẩu và token endpoint.
Keycloak tạo URL theo địa chỉ của request.
Máy chủ đứng sau proxy đặt `KEYCLOAK_HOSTNAME=${GATEWAY_PUBLIC_URL}` trong `deploy/.env`.
Compose khi đó tự bật `KC_HOSTNAME` và `KC_HOSTNAME_BACKCHANNEL_DYNAMIC`; chỉ đặt `KC_HOSTNAME` một mình thì gateway từ chối mọi token.

## Sự kiện đăng nhập và khóa tạm

- Realm import bật sự kiện người dùng (giữ 90 ngày), sự kiện quản trị kèm chi tiết, và khóa tạm sau 5 lần đăng nhập sai (chờ 1 phút, tăng dần, tối đa 15 phút, không khóa vĩnh viễn).
- Cách xem sự kiện: [`deploy/MONITORING.md`](../deploy/MONITORING.md).
- Realm chỉ import lần đầu, nên Keycloak đã có dữ liệu áp tay trong Admin Console (realm `dxos`):
  1. **Realm settings → Events → User events settings**: bật **Save events**, **Expiration** 90 ngày, chọn các loại `LOGIN`, `LOGIN_ERROR`, `LOGOUT`, `UPDATE_PASSWORD`, `RESET_PASSWORD` và loại bị khóa.
  2. **Realm settings → Events → Admin events settings**: bật **Save events** và **Include representation**.
  3. **Realm settings → Security defenses → Brute force detection**: bật, chế độ **Lockout temporarily**, **Max login failures** 5, **Wait increment** 1 phút, **Max wait** 15 phút.
- IP trong sự kiện là IP thật khi Keycloak đọc `X-Forwarded-For` do gateway đặt.
- Từ Keycloak 26.8 Admin REST API chỉ nhận token có audience của chính nó, nên client `dxos-backend-admin` mang scope `kc-admin-api` ngay trong `realm-dxos.json`; không có scope này thì mọi lần áp dụng mô hình trả 502 `ERR_ACCOUNT_SERVICE_DENIED`.
  Realm đã import từ trước chạy `deploy/keycloak-admin-scope.sh` để gán scope đó (`deploy/update.sh` gọi nó; chạy lại không đổi gì).
  Làm tay thì vào **Clients → dxos-backend-admin → Client scopes → Add client scope → kc-admin-api → Default**.

## Hostname nội bộ và hostname trình duyệt

Trình duyệt gọi Keycloak qua gateway, dịch vụ lõi trong mạng docker gọi `http://keycloak:8080`, nên claim `iss` của token mang hostname trình duyệt.
Vì vậy dịch vụ lõi dùng `jwk-set-uri` (chỉ lấy public key) thay cho `issuer-uri` và không kiểm `iss`.
Triển khai thật cần reverse proxy để hai bên dùng cùng một hostname, khi đó bật lại `issuer-uri`.

## Đăng xuất

Đăng xuất ở web kết thúc phiên Keycloak; web đang mở ở tab khác tự phát hiện qua `monitorSession` của `oidc-client-ts` và đăng xuất.
