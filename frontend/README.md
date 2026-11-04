# Web (React, Vite)

Giao diện web của DX-OS.
Web đăng nhập OIDC qua Keycloak và dựng mọi màn hình từ metadata của lõi bằng react-admin: hộp việc, danh sách, biểu mẫu, chi tiết, hành động của quy trình, tệp đính kèm, chế độ xem lịch, bảng điều khiển của người triển khai (`/console`), giao việc và dòng lịch sử.

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `src/App.tsx` | Đăng nhập OIDC, xử lý callback, đăng xuất một lần |
| `src/shell/` | `auth` (OIDC), `api` (gọi backend, bóc `ApiResponse`), `errorMessages` và `backendMessages` (thông báo của backend bằng tiếng Việt), `locale`, `i18n` và `viMessages` (chữ của react-admin), `theme` (sáng và tối), `format`, `PageHeader` |
| `src/admin/` | Ứng dụng react-admin: đọc metadata, `dataProvider`, `authProvider`, màn hình dựng theo thực thể (danh sách, chi tiết, biểu mẫu), hộp việc, lịch |
| `src/console/` | Bảng điều khiển của người triển khai: mô hình đang chạy, thư viện mẫu, trình soạn và kế hoạch, hàng đợi sự kiện, sơ đồ quy trình, nhãn tiếng Việt của kế hoạch (`labels`) |
| `src/assistant/` | Trợ lý hồ sơ dùng metadata chung, nguồn đối chiếu và trạng thái xử lý; chỉ hiện lối vào khi Backend bật tính năng |

## Ngôn ngữ

Giao diện bằng tiếng Việt.
Backend trả thông báo tiếng Anh kèm `errorCode` ổn định; `shell/backendMessages.ts` dịch chúng theo từng mẫu câu, nên thông báo mới của backend cần thêm một mẫu ở đó, nếu không sẽ hiện nguyên văn.
Chữ có sẵn của react-admin nằm ở `shell/viMessages.ts`.
Ngôn ngữ hiện tại giữ ở `shell/locale.ts`, nên thêm một ngôn ngữ là thêm bảng thông báo, bảng mẫu câu của backend và một nút gọi `setLocale`.

Danh sách có ô **Tìm hồ sơ theo nội dung**, giữ các bộ lọc hiện hành.
Chi tiết và kết quả tìm có nút **Tóm tắt diễn biến**, **Tìm trường hợp tương tự**; tự lấy hồ sơ đã chọn, không yêu cầu nhập ID.
Trang `/assistant` là lối tìm kiếm bổ sung theo một loại hồ sơ được phép đọc.
Trạng thái Backend đứng trước phần tổng hợp AI; nguồn thu gọn và mở được từ trích dẫn về bản gốc.
Tùy chọn diễn giải điều kiện và khoảng lịch sử nằm trong phần nâng cao.
Phần sinh không thực hiện hành động nghiệp vụ hoặc thay thế trạng thái hồ sơ.

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
