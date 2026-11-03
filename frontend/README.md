# Web (React, Vite)

Giao diện web của DX-OS.
Web đăng nhập OIDC qua Keycloak và dựng mọi màn hình từ metadata của lõi bằng react-admin: hộp việc, danh sách, biểu mẫu, chi tiết, hành động của quy trình, tệp đính kèm, chế độ xem lịch, bảng điều khiển của người triển khai (`/console`), giao việc và dòng lịch sử.

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `src/App.tsx` | Đăng nhập OIDC, xử lý callback, đăng xuất một lần |
| `src/shell/` | `auth` (OIDC), `api` (gọi backend, bóc `ApiResponse`), thông báo lỗi tiếng Việt theo `errorCode` |
| `src/admin/` | Ứng dụng react-admin: đọc metadata, `dataProvider`, `authProvider`, màn hình dựng theo thực thể, hộp việc |

## Chạy

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # tsc -b rồi vite build, ra dist/
npx tsc --noEmit   # kiểm kiểu
```

Dev cần backend và Keycloak chạy qua gateway (`docker compose up -d` trong `deploy/`); client `dxos-web` đã cho phép redirect `http://localhost:5173/*`.

## Cấu hình

Biến `VITE_*` nhúng vào bundle lúc build; mẫu ở [`.env.example`](./.env.example) (sao chép thành `.env` khi chạy `npm run dev`):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:9080` | Địa chỉ API; rỗng thì cùng origin |
| `VITE_KEYCLOAK_URL` | `<origin>/realms/dxos` | Realm URL của Keycloak |
| `VITE_KEYCLOAK_CLIENT_ID` | `dxos-web` | Client OIDC (Authorization Code + PKCE) |

Trong Compose, ba biến này để rỗng hoặc `dxos-web`: gateway phục vụ web cùng origin với API và Keycloak (`deploy/docker-compose.yml`, service `frontend`).

## Đóng gói

`Dockerfile` build hai giai đoạn: `node:20-alpine` chạy `npm run build`, rồi `nginx:1.27-alpine` phục vụ `dist/`.
`nginx.conf` trả `index.html` cho mọi đường dẫn (SPA).
Service không publish cổng, gateway APISIX phục vụ web.
