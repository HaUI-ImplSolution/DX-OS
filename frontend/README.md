# Web (React, Vite)

Giao diện web của DX-OS: một image phục vụ cả hai ứng dụng.
Sau khi đăng nhập, web đọc `GET /api/v1/app` rồi nạp module giao diện của ứng dụng đang chạy (`DXOS_APP`), nên đổi ứng dụng không cần build lại.

- **Farm** dành cho kỹ sư và admin: hàng chờ (báo cáo chưa chẩn đoán, chẩn đoán chờ duyệt, khai phun thuốc), chi tiết yêu cầu, nông hộ, thời tiết theo thửa, tài liệu. Địa chỉ `/?report=<id>` mở thẳng một báo cáo. Nông dân chỉ dùng app mobile, web hiện màn "dùng ứng dụng di động".
- **Lab** dành cho sinh viên, giảng viên, kỹ thuật viên và quản trị: yêu cầu hỗ trợ, mượn thiết bị, đặt phòng, tài sản, thống kê, hỏi đáp.

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `src/App.tsx` | Đăng nhập OIDC, đọc manifest, nạp module app, dựng vỏ |
| `src/appRegistry.ts` | Ánh xạ `id` của app sang hàm nạp module (`import()`) |
| `src/shell/` | Vỏ dùng chung: `auth`, `api`, khung trang và menu theo manifest, hộp duyệt (`Requests`, `CaseDetail`), `Knowledge`, `Workspaces`, `Notifications`, thành phần chung; `index.ts` là cửa duy nhất của app vào vỏ |
| `src/apps/farm/` | Module Farm: trang, panel, `api`, `types`, nhãn và thông báo lỗi riêng |
| `src/apps/lab/` | Module Lab: trang, `api`, `types`, nhãn riêng |

Menu và quyền xem trang lấy từ manifest của Backend (vai nào thấy mục nào); vai của người dùng là vai mạnh nhất trong các vai app khai báo.

## Thêm giao diện cho ứng dụng mới

1. Tạo `src/apps/<id>/index.tsx` xuất mặc định một `AppUiModule` (`tagline`, `roleLabels`, `routes`, và tùy chọn `labels`, `errorMessages`, `blockedRoles`; xem `src/shell/appModule.ts`).
2. Mỗi `route` có `path` trùng đường dẫn của mục menu trong `AppModule` của backend; trang chi tiết đặt `menuPath` về mục menu của nó để dùng chung quyền.
3. Thêm một dòng vào `src/appRegistry.ts`.
4. Chỉ import từ `src/shell` (qua `index.ts`) và từ thư mục của app.

Các bước phía backend ở [`CONTRIBUTING.md`](../CONTRIBUTING.md).

## Chạy

```bash
npm install
npm run dev        # http://localhost:5173
npm run build      # tsc -b rồi vite build, ra dist/
npx tsc --noEmit   # kiểm kiểu
```

Dev cần Backend và Keycloak chạy qua gateway (`./deploy/start.sh`); client `dxos-web` đã cho phép redirect `http://localhost:5173/*`.

## Cấu hình

Biến `VITE_*` nhúng vào bundle lúc build; mẫu ở [`.env.example`](./.env.example) (sao chép thành `.env` khi chạy `npm run dev`):

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:9080` | Địa chỉ API; rỗng thì cùng origin |
| `VITE_KEYCLOAK_URL` | `<origin>/realms/dxos` | Realm URL của Keycloak |
| `VITE_KEYCLOAK_CLIENT_ID` | `dxos-web` | Client OIDC (Authorization Code + PKCE) |
| `VITE_SUPERSET_URL` | `<host>:9081/login/keycloak` | Link tới BI trong trang thống kê của Lab |

Trong Compose, ba biến đầu để rỗng và `VITE_KEYCLOAK_CLIENT_ID=dxos-web`: gateway phục vụ web cùng origin với API và Keycloak (`deploy/docker-compose.yml`, service `frontend`).

## Đóng gói

`Dockerfile` build hai giai đoạn: `node:20-alpine` chạy `npm run build`, rồi `nginx:1.27-alpine` phục vụ `dist/`.
`nginx.conf` trả `index.html` cho mọi đường dẫn (SPA) và `404` cho `/uploads/`; ảnh nằm ở MinIO qua link có hạn của Backend.
Service không publish cổng, gateway APISIX phục vụ web ở `http://localhost:9080`.
