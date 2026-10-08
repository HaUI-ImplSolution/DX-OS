# Mobile (`mobile`, Expo)

Thư mục riêng ngang hàng `backend/`, `frontend/`, `ai/`, `bi/`, `chat/` — ứng dụng React Native dành cho nông dân, chụp ảnh sâu bệnh ngay tại ruộng và gửi báo cáo về Backend.
Kế hoạch, đặc tả backend và thiết kế UI nằm ở [`development/docs/pending_docs/mobile/`](../development/docs/pending_docs/mobile/README.md).

## Vì sao chọn Expo (React Native)

Một codebase TypeScript chạy trên Android và iOS, có sẵn module camera, nén ảnh, lưu trữ an toàn và đăng nhập OAuth2.
Toàn bộ dependency đều là MIT hoặc Apache-2.0 (OSI-approved) — xem [`LICENSES.md`](../LICENSES.md).
Ngoài Expo Go, có thể build APK bằng EAS mà không cần cài Android Studio.

## Phạm vi

- Chỉ role `FARMER`; ENGINEER và ADMIN tiếp tục dùng web (`frontend/`) vì việc duyệt cần xem ảnh, kết quả CV, khuyến nghị và quy tắc PHI cùng lúc.
- Luồng hiện có: đăng nhập → chọn thửa → chọn vụ mùa → xem và gửi báo cáo → theo dõi kết quả kỹ sư duyệt → khai phun thuốc → khai báo thu hoạch; mất mạng thì ảnh chờ trong máy và tự gửi khi có mạng.
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
  App kiểm mỗi chiều từ 160px và dung lượng tối đa 10 MiB; Backend kiểm độ sáng, độ nét và phân loại lúa ngay sau khi chụp ảnh và khi nộp thật.
  Ảnh không đạt phải chụp lại; precheck dùng classifier cây trồng, chưa kết luận bệnh và có thể nhận nhầm cây gần giống lúa.
- **Hàng đợi offline**: khi không có mạng hoặc gặp lỗi kết nối lúc gửi, ảnh được copy vào thư mục dữ liệu của app và ghi vào hàng đợi (`@react-native-async-storage/async-storage`).
  Mỗi ảnh có một UUID dùng làm header `Idempotency-Key`, nên gửi lại nhiều lần vẫn chỉ tạo một báo cáo.
  Ảnh thay thế giữ `retakeOf` trong hàng đợi; Backend kiểm chất lượng và phân loại lúa khi có mạng, lỗi 422 không tạo báo cáo.
  Hàng đợi tự gửi khi `NetInfo` báo có mạng và khi app trở lại foreground; lỗi tạm thời được thử lại với khoảng chờ tăng dần (5 giây đến tối đa 5 phút, tối đa 8 lần).
  Lỗi `4xx` khác `408`, `409`, `429` đánh dấu "Lỗi" và không thử lại.
  Hàng đợi tách theo tài khoản; danh sách "Chờ gửi" và nút xoá nằm ở màn Hồ sơ, số lượng hiện ở đầu màn Báo cáo của vụ.
- **Tạo thửa**: màn `parcels/new` lấy vị trí bằng `expo-location` (cần đứng tại ruộng), hiện sai số; sai số quá 30 m hoặc vị trí giả lập thì nút gửi bị khóa, Backend vẫn kiểm lại.
  Thửa gửi đi ở trạng thái "Chờ duyệt"; bị từ chối thì thẻ thửa hiện lý do và nút "Sửa và gửi lại"; thửa đã duyệt có nút "Đề nghị chỉnh sửa thửa".
- **Tạo vụ mùa**: màn `seasons/new` (chỉ với thửa đã duyệt) chọn giống từ danh mục, ngày bắt đầu và loại vụ; ngày thu hoạch gợi ý do Backend tính, chọn ngày ngoài khoảng gợi ý phải nêu lý do.
  Cần mạng để tải danh mục giống.
- **Hàng đợi thửa và vụ**: khi mất mạng lúc gửi, thửa và vụ mới vào cùng hàng đợi với ảnh, mỗi mục một `Idempotency-Key`; lỗi `422` (ví dụ sai số GPS) đánh dấu "Lỗi" và không thử lại.
- **Hộp thông báo**: màn `notifications` đọc `GET /api/v1/notifications` (30 thông báo mới nhất).
  `NotificationsProvider` hỏi số chưa đọc khi mở app, mỗi lần app trở lại foreground, mỗi lần quay lại màn Thửa và khi kéo làm mới, không dùng push.
  Trạng thái đã đọc lưu ở Backend nên mọi thiết bị thấy như nhau.
- **Thu hoạch**: màn `seasons/harvest` đọc `harvest-readiness` rồi gửi ngày, sản lượng và diện tích.
  Ngày thu hoạch trước ngày dự kiến thì phải bật xác nhận và nhập lý do.
  Vụ còn cách ly thì nút gửi bị khóa.
  Màn `seasons/reschedule` đổi ngày dự kiến kèm lý do và liệt kê các lần đổi.
- **Khai phun thuốc**: ở chi tiết báo cáo đã duyệt, nông dân nhập liều và ngày phun.
  Bản khai chờ kỹ sư xác nhận, trong lúc chờ vụ chưa thu hoạch được.
  Bị từ chối thì màn hiện lý do và cho khai lại.
- **Hiển thị trạng thái**: `NEEDS_RETAKE` của báo cáo được ưu tiên, các quyết định còn lại lấy từ `diagnosis`.
  Màn chi tiết có liên kết xem chuỗi ảnh thay thế.

  | `diagnosis` | Chip | Nội dung nông dân thấy |
  |---|---|---|
  | `null` | Chờ chẩn đoán | Ảnh và thời điểm gửi |
  | `PENDING` | Chờ kỹ sư duyệt | Chỉ trạng thái |
  | `APPROVED` | Đã duyệt | Bệnh, khuyến nghị, thuốc, liều, số ngày cách ly |
  | `REJECTED` | Khuyến nghị bị từ chối | Lý do từ chối |
  | Báo cáo `NEEDS_RETAKE` | Cần chụp lại | Lý do và nút chụp ảnh thay thế |

- **Banner cách ly**: hiển thị khi `IsolationStatus.isolated` là `true`, kèm số ngày còn lại và ngày hết cách ly.
- **Ảnh báo cáo**: tải từ `imageUrl` do Backend trả về; đây là URL MinIO có thời hạn qua gateway.
- **HTTP cleartext**: `app.config.ts` cho phép HTTP thường (Android `usesCleartextTraffic`, iOS ATS) ở mọi profile EAS trừ `production`, để dev và preview gọi được Backend qua IP LAN.

## Cấu trúc thư mục

| Đường dẫn | Nội dung |
|---|---|
| `app/` | Màn hình theo `expo-router`: `index` (Thửa), `parcels/[code]` (Vụ mùa), `parcels/new` (Tạo hoặc sửa thửa), `seasons/new` (Tạo hoặc sửa vụ), `seasons/[id]` (Báo cáo của vụ), `seasons/harvest` (Khai báo thu hoạch), `seasons/reschedule` (Đổi ngày thu hoạch), `notifications` (Hộp thông báo), `capture` (Chụp và gửi), `reports/[id]` (Chi tiết báo cáo), `login`, `profile` |
| `src/api/` | `client.ts` (`request`, `upload`, `ApiError`) và `endpoints.ts` |
| `src/auth/` | Đăng nhập PKCE (`AuthProvider`), lưu token (`session`), refresh (`tokens`), đọc claim (`claims`) |
| `src/components/` | `Button`, `Card`, `Field`, `OptionList`, `ParcelCard`, `StatusChip`, `IsolationBanner`, `Timeline`, `ProgressBar`, `EmptyState`, `ErrorState`, `Skeleton`, `Screen` |
| `src/offline/` | Hàng đợi gửi khi mất mạng: lưu trữ (`queue`) và tự gửi lại (`QueueProvider`) |
| `src/hooks/` | `useAsync`, `useParcelIsolation` |
| `src/theme/` | Màu, cỡ chữ, khoảng cách, kích thước vùng chạm |
| `src/*.ts` | `config` (biến môi trường), `types` (kiểu dữ liệu phía nông dân), `format` (ngày giờ), `image` (nén ảnh), `reportStatus` (chip trạng thái) |
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

- Backend chỉ trả cho `FARMER` những thửa thuộc nông hộ đã gắn với tài khoản (claim `sub` của token khớp `Household.keycloakUserId`), không phụ thuộc tên hiển thị.
  Tài khoản chưa gắn nông hộ thấy danh sách rỗng và không tạo được thửa; admin tạo hộ cùng tài khoản bằng `POST /api/v1/households` hoặc gắn tài khoản có sẵn bằng `PATCH /api/v1/households/{code}/account`.
  Thửa, vụ mùa, báo cáo hoặc thao tác thu hoạch trên dữ liệu của hộ khác trả `404`.
  Thửa do FARMER tạo luôn thuộc hộ của chính họ.
- Token của `FARMER` chỉ được chấp nhận khi cấp cho client `dxfarm-mobile` (claim `azp`); cùng token đó cấp qua client web bị backend từ chối `403`.
  Mọi access token phải có `aud` chứa `dxfarm-api`; realm tự thêm vào token của client web và mobile, nên token cấp trước khi có cấu hình này (không có `aud`) bị `401` và phải đăng nhập lại.
- Nội dung chẩn đoán (bệnh, khuyến nghị, thuốc, liều) chỉ có khi diagnosis đã `APPROVED`.
  Đặc tả: [`development/docs/pending_docs/mobile/BACKEND_CHANGES.md`](../development/docs/pending_docs/mobile/BACKEND_CHANGES.md).

## Biến môi trường

Khai báo trong `mobile/.env` (không commit); mẫu ở [`.env.example`](./.env.example).

| Biến | Ý nghĩa | Ví dụ |
|---|---|---|
| `EXPO_PUBLIC_API_BASE_URL` | Địa chỉ gateway (API và ảnh) | `http://192.168.1.10:9080` |
| `EXPO_PUBLIC_KEYCLOAK_URL` | Realm URL của Keycloak, cũng qua gateway | `http://192.168.1.10:9080/realms/dxfarm` |
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

### 2. Khởi động Backend và Keycloak

```bash
cd deploy
docker compose up -d --build
```

- Điện thoại phải truy cập được cổng `9080` (gateway) của máy dev; mở cổng trên firewall nếu cần.
- Keycloak chỉ import realm khi realm chưa tồn tại.
  Với volume Postgres cũ, thêm client `dxfarm-mobile` qua Admin Console hoặc xoá `deploy/data/postgres` để import lại (mất dữ liệu demo).
- Thửa cần có `farmerName` là `Nguyễn Văn Nông` để `farmer1` nhìn thấy; đăng nhập web bằng `farmer1` để tạo thửa (tên được gán tự động), hoặc dùng `admin1`/`engineer1` tạo thửa với đúng tên đó.

### 3. Cài đặt và chạy app

```bash
cd mobile
cp .env.example .env
# sửa .env: thay 192.168.1.10 bằng IP LAN của máy dev
npm install
npm start -- -c
```

- Cờ `-c` xoá cache Metro để nạp lại biến `EXPO_PUBLIC_*`; dùng lại mỗi khi đổi `.env`.
- Metro chạy ở cổng `8083` (script `npm start`); điện thoại cần truy cập được cổng này của máy dev.
- Quét mã QR bằng Expo Go trên điện thoại.
  Expo Go phải hỗ trợ SDK 57; nếu không, dùng development build.

## Đóng gói APK

APK được build trên đám mây của EAS (Expo Application Services), không cần cài Android Studio hay Android SDK trên máy dev.
Cấu hình nằm ở [`eas.json`](./eas.json), profile `preview` xuất file `.apk` để cài trực tiếp lên điện thoại Android.

### Chuẩn bị

- Tài khoản Expo miễn phí tại https://expo.dev.
- Backend và Keycloak đã chạy với địa chỉ điện thoại truy cập được (xem mục "Chạy trên điện thoại thật", bước 2).
- Client `dxfarm-mobile` có redirect URI `dxfarm://*` — có sẵn trong realm import.

### Các bước

1. Mở `eas.json` và thay `192.168.1.10` trong ba biến `EXPO_PUBLIC_*` của profile `preview` bằng IP LAN của máy chạy Backend và Keycloak.
   Giá trị này được nhúng vào APK lúc build; file `.env` không được đưa lên EAS.
2. Đăng nhập và gắn dự án với tài khoản Expo (chỉ lần đầu):

   ```bash
   cd mobile
   npx eas-cli login
   npx eas-cli init
   ```

   Lệnh `init` ghi `projectId` của dự án vào `app.json`; commit thay đổi này nếu muốn dùng lại.
3. Build:

   ```bash
   npx eas-cli build --platform android --profile preview
   ```

   Lần đầu EAS hỏi có tạo Android keystore không; chọn để EAS tự quản lý.
4. Khi build xong (thường vài phút đến vài chục phút do hàng đợi), EAS in ra link và mã QR của file `.apk`.
   Mở link trên điện thoại để tải về, cho phép cài ứng dụng từ nguồn không xác định khi hệ thống hỏi, rồi cài đặt.
5. Mở app "DX-Farm Nông dân" và đăng nhập bằng `farmer1`.

### Lưu ý

- IP nhúng trong APK cố định: đổi IP máy dev (DHCP cấp lại) hoặc chuyển sang mạng khác thì phải sửa `eas.json` và build lại.
- Profile `preview` cho phép HTTP thường để gọi Backend qua IP LAN; bản chạy thật cần HTTPS và một profile `production` riêng.
- Muốn tăng số phiên bản, sửa `version` trong `app.json`, và thêm `android.versionCode` khi cần cài đè.
- Build trên máy dev (không dùng đám mây) cần JDK và Android SDK, chạy `npx eas-cli build --platform android --profile preview --local`.
- Nếu IP máy dev đổi, sửa `EXPO_PUBLIC_KEYCLOAK_URL` trong `eas.json` cho khớp rồi build lại.

## Kiểm tra mã nguồn

```bash
npx tsc --noEmit
npx expo export --platform android
```

## Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp |
|---|---|
| "Không đăng nhập được, thử lại" hoặc Keycloak báo redirect URI không hợp lệ | Realm chưa có client `dxfarm-mobile` (volume cũ); xem bước 2 |
| Trang đăng nhập Keycloak không tải, hoặc đăng nhập xong bị chuyển về `localhost` và Safari không mở được | `EXPO_PUBLIC_KEYCLOAK_URL` trong `.env` đang là `localhost` hoặc sai IP; đặt IP LAN của máy dev rồi chạy `npm start -- -c` |
| "Không kết nối được. Kiểm tra mạng rồi thử lại." | Điện thoại không tới được Backend: sai IP trong `.env`, khác mạng WiFi hoặc firewall chặn cổng `9080` |
| Danh sách thửa trống | `farmerName` của thửa không khớp tên trong token |
| Ảnh báo cáo không hiển thị | Gateway trong `imageUrl` không truy cập được từ điện thoại hoặc link đã hết hạn; kiểm tra IP LAN và tải lại báo cáo |
| Ảnh nằm mãi ở "Chờ gửi" | Chưa có mạng tới Backend hoặc đang chờ khoảng thử lại; đưa app về foreground để gửi ngay |
| Ảnh ở trạng thái "Lỗi" | Backend từ chối ảnh (ví dụ sai định dạng, quá lớn hoặc vụ mùa không còn thuộc tài khoản); đọc thông báo dưới ảnh rồi xoá và chụp lại |
| Đổi `.env` nhưng app vẫn dùng giá trị cũ | Chạy lại `npm start -- -c` |

## Còn thiếu

- Push notification khi kỹ sư duyệt hoặc từ chối báo cáo.
- Icon và splash vẫn là ảnh mặc định của Expo.
