# SSO thật (Keycloak)

Thư mục riêng ngang hàng `backend/`, `frontend/`, `ai/`, `bi/`, `chat/` — chỉ chứa realm import và ghi chú, không có mã nguồn tự viết (Keycloak chạy bằng image chính thức).
Dựng cho tiêu chí **Giai đoạn 2 [H]** (không gian Human) — đăng nhập một lần dùng chung.

Service `keycloak` khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml), tự import realm `dxfarm` từ [`realm-dxfarm.json`](./realm-dxfarm.json) lúc khởi động.
Không cần thao tác tay nào thêm sau `docker compose up -d --build`.
Keycloak không publish cổng ra host: gateway APISIX (`APISIX_HOST_PORT`, mặc định `9080`) chuyển tiếp `/realms`, `/resources`, `/admin` và `/js` tới Keycloak.

## Vì sao chọn Keycloak

Chuẩn công nghiệp cho OIDC/SAML, license Apache-2.0 (OSI-approved), có tài liệu tích hợp chuẩn cho cả Spring Boot, Superset và Mattermost.

## Phạm vi

- Backend Spring Boot: OAuth2 Resource Server, xác thực token do Keycloak cấp.
- Frontend: tự redirect sang Keycloak (Authorization Code + PKCE) ngay khi chưa có phiên — không cần bấm nút đăng nhập trung gian, vào thẳng từ portal cũng được.
- Mobile (Expo, chỉ FARMER): client public `dxfarm-mobile`, Authorization Code + PKCE, không dùng direct access grants.
  Chi tiết: [`development/docs/pending_docs/mobile/README.md`](../development/docs/pending_docs/mobile/README.md).
- BI Dashboard (Superset): đăng nhập bằng redirect sang Keycloak (`AUTH_OAUTH`), role realm map sang role Superset.
  Chi tiết: [`bi/README.md`](../bi/README.md).
- **Không làm**: Mattermost (Team Edition không hỗ trợ OpenID Connect với IdP tuỳ ý — cần Enterprise license).
  Chi tiết: [`chat/README.md`](../chat/README.md).

## Cách hoạt động

- `keycloak` dùng CSDL Postgres riêng (`keycloak`), cùng pattern với Mattermost/Superset.
  Backend tự tạo role `keycloak_app` + database `keycloak` lúc khởi động (`KeycloakDatabaseSeeder`).
- Realm `dxfarm` có 3 realm role `FARMER`/`ENGINEER`/`ADMIN`, client public `dxfarm-frontend` và `dxfarm-mobile` (Standard Flow + PKCE, không dùng client secret), và client confidential `dxfarm-superset` (Superset trao đổi token server-to-server nên cần secret).
  Secret của client này lấy từ `SUPERSET_OIDC_CLIENT_SECRET` trong `deploy/.env`: Keycloak thay `${SUPERSET_OIDC_CLIENT_SECRET}` trong `realm-dxfarm.json` lúc import, nên file realm không chứa secret thật.
  Keycloak chỉ import khi realm chưa tồn tại; đổi giá trị này sau khi đã import thì cập nhật secret client trong Admin Console hoặc dựng lại bằng `deploy/reset.sh`.
- 3 tài khoản demo tạo sẵn trong realm import — xem bảng dưới.
  Chỉ dùng để demo, đổi mật khẩu/secret thật trước khi triển khai ngoài môi trường dev.
- Backend giới hạn dữ liệu của FARMER theo nông hộ: claim `sub` của token phải khớp `Household.keycloakUserId`.
  Tên hiển thị (claim `name`) đổi được mà không ảnh hưởng quyền truy cập.
  Các tài khoản demo chưa gắn nông hộ: admin tạo hộ kèm tài khoản mới bằng `POST /api/v1/households`, hoặc gắn tài khoản có sẵn vào hộ bằng `PATCH /api/v1/households/{code}/account` với `sub` của tài khoản.

## Audience và giới hạn client

- Client `dxfarm-frontend` và `dxfarm-mobile` có protocol mapper `dxfarm-api-audience` thêm `dxfarm-api` vào `aud` của access token.
  Backend (`security.jwt.audience`, mặc định `dxfarm-api`) từ chối `401` mọi token không có audience đó; vẫn không kiểm `iss` như đã ghi ở mục hostname bên dưới.
- Nông dân chỉ dùng app mobile: token chỉ mang role `FARMER` phải có `azp` thuộc `security.farmer-client-ids` (mặc định `dxfarm-mobile`), nếu không backend trả `403`.
  Web hiển thị màn "dùng ứng dụng di động" cho tài khoản này và không gọi API nghiệp vụ.
  Superset từ chối đăng nhập tài khoản chỉ có role `FARMER`.
- Client confidential `dxfarm-backend-admin` (service account, secret `KEYCLOAK_ADMIN_CLIENT_SECRET`) chỉ giữ role `manage-users`, `view-users`, `view-realm` của `realm-management`; backend dùng nó để tạo tài khoản nông hộ qua Admin REST API.

| Username | Mật khẩu | Role |
|---|---|---|
| `farmer1` | `farmer123` | FARMER |
| `engineer1` | `engineer123` | ENGINEER |
| `admin1` | `admin123` | ADMIN |

Quản lý user (tạo/xoá/đổi role) qua Keycloak Admin Console: `http://localhost:${KEYCLOAK_HOST_PORT}/admin`, đăng nhập bằng `KEYCLOAK_ADMIN_PASSWORD`.

## Client mobile `dxfarm-mobile`

- Redirect URI: `dxfarm://*` (app đã build) và `exp://*` (Expo Go, chỉ dùng khi dev).
- Backend xác thực bằng `jwk-set-uri` và không kiểm tra `iss`, nên token cấp qua IP LAN vẫn hợp lệ.
- Điện thoại phải truy cập được Keycloak và backend qua IP LAN của máy dev cùng mạng.
  Keycloak không đặt `KC_HOSTNAME` nên URL trong form đăng nhập và redirect theo địa chỉ request: web vào `localhost:9080`, điện thoại vào `<IP-LAN>:9080`, hai bên chạy song song mà không cần đổi `deploy/.env`.
- Keycloak chỉ import realm khi realm chưa tồn tại.
  Với volume Postgres cũ, thêm client `dxfarm-mobile` qua Admin Console (cấu hình như trong `realm-dxfarm.json`) hoặc xoá `deploy/data/postgres` để import lại.

## Lưu ý kỹ thuật: hostname nội bộ vs. hostname trình duyệt

Trình duyệt gọi Keycloak qua `http://localhost:${APISIX_HOST_PORT}` (qua gateway), backend/Superset trong mạng docker gọi qua `http://keycloak:8080` — hai hostname khác nhau cho cùng một Keycloak.
Backend xác thực token bằng `jwk-set-uri` (chỉ lấy public key verify chữ ký) thay vì `issuer-uri` (sẽ lệch claim `iss` vì token mang hostname trình duyệt).
Superset (`bi/superset_config.py`) tự chỉ định từng endpoint (`authorize_url` dùng hostname trình duyệt, `access_token_url`/`api_base_url`/`jwks_uri` dùng hostname nội bộ) thay vì dùng `server_metadata_url` tự khám phá — cùng lý do.
Tên, email và role của user lấy thẳng từ access token nhận qua kênh server-to-server, không gọi `/userinfo` (Keycloak từ chối token có issuer khác hostname của request).

Đây là đánh đổi chấp nhận được cho docker-compose chạy trên một máy.
Triển khai thật cần reverse proxy để trình duyệt và backend cùng gọi Keycloak qua một hostname, khi đó bật lại `issuer-uri`.

## Đăng xuất một lần (Single Logout)

Đăng xuất ở app này thì các app khác đang mở cũng tự đăng xuất theo, không cần vào từng app đăng xuất tay:

- **App chính → Superset**: đăng xuất frontend gọi Keycloak kết thúc phiên SSO; Keycloak trả về front-channel logout (`frontchannelLogout` trên client `dxfarm-superset`, trỏ `http://localhost:8088/logout/`) — trình duyệt tự tải ngầm URL này, xoá luôn cookie Superset.
- **Superset → App chính**: `LOGOUT_REDIRECT_URL` trong `bi/superset_config.py` khiến đăng xuất Superset cũng gọi Keycloak kết thúc phiên SSO (không chỉ xoá cookie Superset).
  Frontend đang mở tab thì tự phát hiện qua session monitoring (`monitorSession: true` trong `oidc-client-ts`, poll trạng thái phiên Keycloak vài giây/lần) và tự đăng xuất.
- **Đăng xuất ở Keycloak Account Console**: kích hoạt cả hai cơ chế trên vì phiên SSO kết thúc trực tiếp.

**Giới hạn**: đây là cơ chế phía trình duyệt (front-channel + polling định kỳ), không phải backchannel logout chuẩn OIDC (server-to-server, hoạt động kể cả khi không có tab nào đang mở).
Superset (Flask-AppBuilder) dùng cookie session ký sẵn, không có nơi lưu để server tự thu hồi — backchannel logout thật cần thêm server-side session store + endpoint xác thực logout token, không làm ở mức này.
