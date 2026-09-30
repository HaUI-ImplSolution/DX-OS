# Mobile (`mobile`, Expo)

Thư mục riêng ngang hàng `backend/`, `frontend/`, `ai/`, `bi/`, `chat/` — ứng dụng React Native dành cho nông dân, chụp ảnh sâu bệnh ngay tại ruộng và gửi báo cáo về Backend.
Kế hoạch, đặc tả backend và thiết kế UI nằm ở [`development/docs/mobile/`](../development/docs/mobile/README.md).

## Vì sao chọn Expo (React Native)

Một codebase TypeScript chạy trên Android và iOS, có sẵn module camera, nén ảnh, lưu trữ an toàn và đăng nhập OAuth2.
Toàn bộ dependency đều là MIT hoặc Apache-2.0 (OSI-approved) — xem [`LICENSES.md`](../LICENSES.md).
Ngoài Expo Go, có thể build APK bằng EAS mà không cần cài Android Studio.

## Phạm vi

- Chỉ role `FARMER`; ENGINEER và ADMIN tiếp tục dùng web (`frontend/`) vì việc duyệt cần xem ảnh, kết quả CV, khuyến nghị và quy tắc PHI cùng lúc.
- Luồng hiện có: đăng nhập → chọn thửa → chọn vụ mùa → xem và gửi báo cáo → theo dõi kết quả kỹ sư duyệt.
- Chẩn đoán AI do kỹ sư kích hoạt thủ công trên web; nông dân thấy trạng thái "Chờ chẩn đoán" sau khi gửi.

## Cách hoạt động

- **Điều hướng**: `expo-router` theo cấu trúc file trong `app/`, dạng stack, không có tab bar.
  Route guard bằng `Stack.Protected` ở `app/_layout.tsx`: chưa đăng nhập chỉ thấy màn Đăng nhập.
- **Xác thực**: Authorization Code + PKCE với client public `dxfarm-mobile` của Keycloak, qua `expo-auth-session` và trình duyệt hệ thống.
  Endpoint dựng trực tiếp từ `EXPO_PUBLIC_KEYCLOAK_URL`, không dùng discovery.
  Access token và refresh token lưu ở `expo-secure-store`; token tự refresh trước khi hết hạn.
  Refresh bị từ chối hoặc Backend trả `401` thì phiên bị xoá và app về màn Đăng nhập.
- **Phân quyền**: role đọc từ `realm_access.roles` của access token; tài khoản không có role `FARMER` bị đăng xuất kèm thông báo "Ứng dụng này dành cho nông dân".
  Đăng xuất gọi endpoint logout của Keycloak để kết thúc luôn phiên SSO.
- **API**: `src/api/client.ts` bọc `fetch` với header `Authorization`, timeout 30 giây và `ApiError`; mọi response đi qua khuôn `ApiResponse<T>` của Backend.
  Lỗi mạng trả `ApiError` với `status` `0`.
- **Chụp và gửi ảnh**: `expo-image-picker` mở camera hệ thống; `expo-image-manipulator` thu nhỏ cạnh dài tối đa 1280px và nén JPEG 0.7 để nằm xa giới hạn multipart 10MB.
  Ảnh gửi dạng multipart, part `image`, qua `XMLHttpRequest` để có thanh tiến trình.
- **Hiển thị trạng thái**: trạng thái báo cáo suy ra từ `diagnosis`.

  | `diagnosis` | Chip | Nội dung nông dân thấy |
  |---|---|---|
  | `null` | Chờ chẩn đoán | Ảnh và thời điểm gửi |
  | `PENDING` | Chờ kỹ sư duyệt | Chỉ trạng thái |
  | `APPROVED` | Đã duyệt | Bệnh, khuyến nghị, thuốc, liều, số ngày cách ly |
  | `REJECTED` | Cần chụp lại | Lý do từ chối và nút "Chụp lại" |

- **Banner cách ly**: hiển thị khi `IsolationStatus.isolated` là `true`, kèm số ngày còn lại và ngày hết cách ly.
- **Ảnh báo cáo**: tải từ `${EXPO_PUBLIC_API_BASE_URL}/uploads/{imageRef}`.
- **HTTP cleartext**: `app.config.ts` cho phép HTTP thường (Android `usesCleartextTraffic`, iOS ATS) ở mọi profile EAS trừ `production`, để dev và preview gọi được Backend qua IP LAN.

## Cấu trúc thư mục

| Đường dẫn | Nội dung |
|---|---|
| `app/` | Màn hình theo `expo-router`: `index` (Thửa), `parcels/[code]` (Vụ mùa), `seasons/[id]` (Báo cáo của vụ), `capture` (Chụp và gửi), `reports/[id]` (Chi tiết báo cáo), `login`, `profile` |
| `src/api/` | `client.ts` (`request`, `upload`, `ApiError`) và `endpoints.ts` |
| `src/auth/` | Đăng nhập PKCE (`AuthProvider`), lưu token (`session`), refresh (`tokens`), đọc claim (`claims`) |
| `src/components/` | `Button`, `Card`, `StatusChip`, `IsolationBanner`, `Timeline`, `ProgressBar`, `EmptyState`, `ErrorState`, `Skeleton`, `Screen` |
| `src/hooks/` | `useAsync`, `useParcelIsolation` |
| `src/theme/` | Màu, cỡ chữ, khoảng cách, kích thước vùng chạm |
| `src/types.ts` | Kiểu dữ liệu của phía nông dân |
| `app.json`, `app.config.ts` | Cấu hình Expo: scheme `dxfarm`, quyền camera, cleartext |

## Đăng nhập

- Tài khoản demo tạo sẵn trong realm Keycloak (xem [`sso/README.md`](../sso/README.md)):

  | Username | Mật khẩu | Vai trò | Kết quả trên app |
  |---|---|---|---|
  | `farmer1` | `farmer123` | FARMER | Vào được app |
  | `engineer1` | `engineer123` | ENGINEER | Bị đăng xuất kèm thông báo |
  | `admin1` | `admin123` | ADMIN | Bị đăng xuất kèm thông báo |

- Redirect URI đã đăng ký cho client `dxfarm-mobile`: `dxfarm://*` (app đã build) và `exp://*` (Expo Go, chỉ dùng khi dev).

## Phạm vi dữ liệu của nông dân

- Backend chỉ trả cho `FARMER` những thửa có `farmerName` khớp tên trong token (claim `name`), không phân biệt hoa thường; `farmer1` có tên `Nguyễn Văn Nông`.
  Thửa, vụ mùa hoặc báo cáo của người khác trả `404`.
- Nội dung chẩn đoán (bệnh, khuyến nghị, thuốc, liều) chỉ có khi diagnosis đã `APPROVED`.
  Đặc tả: [`development/docs/mobile/BACKEND_CHANGES.md`](../development/docs/mobile/BACKEND_CHANGES.md).

## Biến môi trường

Khai báo trong `mobile/.env.local` (không commit); mẫu ở [`.env.example`](./.env.example).

| Biến | Ý nghĩa | Ví dụ |
|---|---|---|
| `EXPO_PUBLIC_API_BASE_URL` | Địa chỉ Backend | `http://192.168.1.10:8081` |
| `EXPO_PUBLIC_KEYCLOAK_URL` | Realm URL của Keycloak, phải trùng `KEYCLOAK_PUBLIC_URL` trong `deploy/.env` | `http://192.168.1.10:8180/realms/dxfarm` |
| `EXPO_PUBLIC_KEYCLOAK_CLIENT_ID` | Client OIDC | `dxfarm-mobile` |

Biến `EXPO_PUBLIC_*` được nhúng vào bundle lúc build nên chỉ chứa giá trị công khai.
Thiếu biến nào thì app báo lỗi ngay lúc khởi động.

## Chạy trên điện thoại thật

Điện thoại và máy dev phải cùng mạng WiFi.

### 1. Lấy IP LAN của máy dev

```bash
hostname -I | awk '{print $1}'
```

Các bước dưới dùng `192.168.1.10` làm ví dụ.

### 2. Cấu hình Backend và Keycloak

Trong `deploy/.env` đặt:

```
KEYCLOAK_PUBLIC_URL=http://192.168.1.10:8180
```

Khởi động lại stack:

```bash
cd deploy
docker compose up -d --build
```

- Điện thoại phải truy cập được cổng `8081` (Backend) và `8180` (Keycloak) của máy dev; mở cổng trên firewall nếu cần.
- Keycloak chỉ import realm khi realm chưa tồn tại.
  Với volume Postgres cũ, thêm client `dxfarm-mobile` qua Admin Console hoặc xoá `deploy/data/postgres` để import lại (mất dữ liệu demo).
- Khi `KEYCLOAK_PUBLIC_URL` trỏ sang IP, web cũng phải truy cập Keycloak bằng địa chỉ này.
  Để trống biến này khi chỉ dùng web ở `localhost`.
- Thửa cần có `farmerName` là `Nguyễn Văn Nông` để `farmer1` nhìn thấy; tạo thửa qua web hoặc `POST /api/v1/parcels`.

### 3. Cài đặt và chạy app

```bash
cd mobile
cp .env.example .env.local
# sửa .env.local: thay 192.168.1.10 bằng IP LAN của máy dev
npm install
npx expo start -c
```

- Cờ `-c` xoá cache Metro để nạp lại biến `EXPO_PUBLIC_*`; dùng lại mỗi khi đổi `.env.local`.
- Quét mã QR bằng Expo Go trên điện thoại.
  Expo Go phải hỗ trợ SDK 57; nếu không, dùng development build.

## Kiểm tra mã nguồn

```bash
npx tsc --noEmit
npx expo export --platform android
```

## Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| "Không đăng nhập được, thử lại" hoặc Keycloak báo redirect URI không hợp lệ | Realm chưa có client `dxfarm-mobile` (volume cũ); xem bước 2 |
| Trang đăng nhập Keycloak không tải hoặc trỏ về `localhost` | `KEYCLOAK_PUBLIC_URL` chưa đặt hoặc chưa khởi động lại Keycloak |
| "Không kết nối được. Kiểm tra mạng rồi thử lại." | Điện thoại không tới được Backend: sai IP trong `.env.local`, khác mạng WiFi hoặc firewall chặn cổng `8081` |
| Danh sách thửa trống | `farmerName` của thửa không khớp tên trong token |
| Ảnh báo cáo không hiển thị | Đường dẫn `/uploads/...` không truy cập được từ điện thoại; kiểm tra `EXPO_PUBLIC_API_BASE_URL` |
| Đổi `.env.local` nhưng app vẫn dùng giá trị cũ | Chạy lại `npx expo start -c` |

## Còn thiếu

- Hàng đợi gửi khi mất mạng (offline queue) và header `Idempotency-Key` phía Backend.
- Đóng gói APK bằng EAS (`eas.json`, profile `preview`).
- Push notification khi kỹ sư duyệt hoặc từ chối báo cáo.
- Icon và splash vẫn là ảnh mặc định của Expo.
