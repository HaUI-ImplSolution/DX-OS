# SSO thật (Keycloak)

Thư mục riêng ngang hàng `backend/`, `frontend/`, `ai/`, `bi/`, `chat/` — chỉ chứa realm import và ghi chú, không có mã nguồn tự viết (Keycloak chạy bằng image chính thức).
Dựng cho tiêu chí **Giai đoạn 2 [H]** (không gian Human) — đăng nhập một lần dùng chung.

Service `keycloak` khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml), tự import realm `dxfarm` từ [`realm-dxfarm.json`](./realm-dxfarm.json) lúc khởi động.
Không cần thao tác tay nào thêm sau `docker compose up -d --build`.
Cổng host mặc định `8180` (`KEYCLOAK_PORT`), không dùng `8080` mặc định của Keycloak vì đó là port hay bị chiếm nhất trên máy dev.

## Vì sao chọn Keycloak

Chuẩn công nghiệp cho OIDC/SAML, license Apache-2.0 (OSI-approved), có tài liệu tích hợp chuẩn cho cả Spring Boot, Superset và Mattermost.

## Phạm vi đợt này

- Backend Spring Boot: OAuth2 Resource Server, xác thực token do Keycloak cấp.
- Frontend: đăng nhập bằng redirect sang Keycloak (Authorization Code + PKCE).
- **Chưa làm**: Superset và Mattermost vẫn đăng nhập riêng, thuộc đợt sau theo roadmap (`README.md` gốc).

## Cách hoạt động

- `keycloak` dùng CSDL Postgres riêng (`keycloak`), cùng pattern với Mattermost/Superset.
  Backend tự tạo role `keycloak_app` + database `keycloak` lúc khởi động (`KeycloakDatabaseSeeder`).
- Realm `dxfarm` có 3 realm role `FARMER`/`ENGINEER`/`ADMIN` và 1 client public `dxfarm-frontend` (Standard Flow + PKCE, không dùng client secret).
- 3 tài khoản demo tạo sẵn trong realm import — xem bảng dưới.
  Chỉ dùng để demo, đổi mật khẩu thật trước khi triển khai ngoài môi trường dev.

| Username | Mật khẩu | Role |
|---|---|---|
| `farmer1` | `farmer123` | FARMER |
| `engineer1` | `engineer123` | ENGINEER |
| `admin1` | `admin123` | ADMIN |

Quản lý user (tạo/xoá/đổi role) qua Keycloak Admin Console: `http://localhost:${KEYCLOAK_PORT:-8180}/admin`, đăng nhập bằng `KEYCLOAK_ADMIN_PASSWORD`.

## Lưu ý kỹ thuật: hostname nội bộ vs. hostname trình duyệt

Trình duyệt gọi Keycloak qua `http://localhost:${KEYCLOAK_PORT:-8180}`, backend trong mạng docker gọi qua `http://keycloak:8080` — hai hostname khác nhau cho cùng một Keycloak.
Vì vậy backend xác thực token bằng `jwk-set-uri` (chỉ lấy public key verify chữ ký) thay vì `issuer-uri` (sẽ lệch claim `iss` vì token mang hostname trình duyệt).

Đây là đánh đổi chấp nhận được cho docker-compose chạy trên một máy.
Triển khai thật cần reverse proxy để trình duyệt và backend cùng gọi Keycloak qua một hostname, khi đó bật lại `issuer-uri`.
