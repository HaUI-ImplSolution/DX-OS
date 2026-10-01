# Tổng quan thay đổi của nhánh mobile

> **Cập nhật:** 30/09/2026.
> **Nhánh:** `feature/mobile-version`, so với `Khuong/main`.

Tài liệu này tóm tắt toàn bộ thay đổi của nhánh: backend, SSO, deploy, web và ứng dụng mobile mới.
Mỗi thay đổi hành vi được ghi theo dạng hành vi cũ và hành vi mới để reviewer đối chiếu nhanh.
Đặc tả chi tiết từng phần nằm ở [`BACKEND_CHANGES.md`](./BACKEND_CHANGES.md), kế hoạch ở [`PLAN.md`](./PLAN.md), hướng dẫn chạy ở [`mobile/README.md`](../../../mobile/README.md).

## 1. Tóm tắt

- Thêm ứng dụng mobile Expo (`mobile/`) cho role FARMER: đăng nhập, xem thửa/vụ mùa/báo cáo, chụp và gửi ảnh sâu bệnh, theo dõi kết quả duyệt, gửi lại tự động khi mất mạng.
- Backend giới hạn dữ liệu của FARMER theo thửa của họ và chỉ lộ nội dung chẩn đoán khi kỹ sư đã duyệt.
- Backend thêm `Idempotency-Key` cho việc gửi báo cáo để gửi lại không tạo báo cáo trùng.
- Keycloak có client `dxfarm-mobile`; compose cho phép đặt địa chỉ công khai của Keycloak để điện thoại truy cập qua IP LAN.
- Web (`frontend/`) điều chỉnh để phù hợp với hành vi mới của FARMER.
- ENGINEER và ADMIN không đổi hành vi ở bất kỳ endpoint nào.

## 2. Backend: hành vi cũ và mới

"FARMER" là user có `ROLE_FARMER` mà không có `ROLE_ENGINEER` hoặc `ROLE_ADMIN`.
Tên của user lấy từ claim `name` của token (dự phòng `preferred_username`) và so khớp không phân biệt hoa thường với `farmerName` của thửa.

### 2.1. Phạm vi dữ liệu

| Endpoint | Hành vi cũ (FARMER) | Hành vi mới (FARMER) |
|---|---|---|
| `GET /api/v1/parcels` | Trả mọi thửa | Chỉ trả thửa có `farmerName` khớp tên user |
| `GET /api/v1/parcels/{code}` | Trả thửa bất kỳ | `404` nếu thửa thuộc người khác |
| `POST /api/v1/parcels` | Lưu đúng `farmerName` trong request | Lưu với tên của user, bỏ qua `farmerName` trong request |
| `GET /api/v1/parcels/{code}/crop-seasons` | Trả vụ của thửa bất kỳ; thửa không tồn tại trả danh sách rỗng | `404` nếu thửa thuộc người khác hoặc không tồn tại |
| `POST /api/v1/parcels/{code}/crop-seasons` | Tạo vụ trên thửa bất kỳ | `404` nếu thửa thuộc người khác |
| `GET /api/v1/crop-seasons/{id}` | Trả vụ bất kỳ | `404` nếu thửa của vụ thuộc người khác |
| `GET /api/v1/crop-seasons/{id}/isolation-status` | Trả trạng thái cách ly của vụ bất kỳ | `404` nếu thửa của vụ thuộc người khác |
| `POST /api/v1/crop-seasons/{id}/harvest` | Ghi nhận thu hoạch vụ bất kỳ (chỉ bị khoá PHI chặn) | `404` nếu thửa của vụ thuộc người khác; khoá PHI vẫn áp dụng |
| `GET /api/v1/crop-seasons/{id}/disease-reports` | Trả báo cáo của vụ bất kỳ | `404` nếu thửa của vụ thuộc người khác |
| `POST /api/v1/crop-seasons/{id}/disease-reports` | Tạo báo cáo cho vụ bất kỳ | `404` nếu thửa của vụ thuộc người khác |
| `GET /api/v1/disease-reports/{id}` | Trả báo cáo bất kỳ | `404` nếu thửa của vụ chứa báo cáo thuộc người khác |

- Dùng `404` thay vì `403` để không lộ sự tồn tại của dữ liệu người khác.
- ENGINEER và ADMIN vẫn thấy và thao tác trên mọi thửa như trước.
- User có cả `FARMER` và `ENGINEER`/`ADMIN` được coi là nhân viên, không bị giới hạn.
- Thửa cũ có `farmerName` không khớp tên trong token sẽ không còn hiện với FARMER tương ứng.

### 2.2. Nội dung chẩn đoán trong báo cáo

Áp dụng cho `diagnosis` lồng trong response của `GET /disease-reports/{id}` và `GET /crop-seasons/{id}/disease-reports`.

| Trạng thái diagnosis | Hành vi cũ (FARMER) | Hành vi mới (FARMER) |
|---|---|---|
| Chưa có diagnosis | `diagnosis` là `null` | Không đổi |
| `PENDING` | Đầy đủ: bệnh, độ tin cậy, khuyến nghị, quy tắc thuốc và liều | Chỉ `id`, `diseaseReportId`, `approvalStatus`; các trường còn lại `null` |
| `REJECTED` | Đầy đủ | `id`, `diseaseReportId`, `approvalStatus`, `decidedAt`, `rejectionReason`; các trường còn lại `null` |
| `APPROVED` | Đầy đủ | Không đổi (đầy đủ) |

- ENGINEER và ADMIN luôn nhận `DiagnosisResponse` đầy đủ.
- Lý do từ chối do kỹ sư nhập sẽ hiển thị cho FARMER.

### 2.3. Idempotency khi gửi báo cáo

| Tình huống | Hành vi cũ | Hành vi mới |
|---|---|---|
| Không có header `Idempotency-Key` | Mỗi request tạo một báo cáo | Không đổi |
| Có `Idempotency-Key`, key chưa dùng | Không có khái niệm key | Tạo báo cáo và lưu key vào `DiseaseReport.clientRequestId` |
| Có `Idempotency-Key`, key đã dùng bởi cùng user và cùng vụ | Không có khái niệm key | Trả báo cáo đã lưu, không lưu ảnh lần hai |
| Key đã dùng bởi vụ mùa khác hoặc user khác | Không có khái niệm key | `409` |
| Hai request cùng key chạy đồng thời | Không có khái niệm key | Request thua ràng buộc unique trả `409`; gửi lại sẽ nhận báo cáo đã lưu |
| Key không phải UUID | Không có khái niệm key | `400` |

- Cột `client_request_id` (unique, nullable) do Hibernate tự thêm khi backend khởi động lại (`ddl-auto: update`).

### 2.4. Swagger

- `@Operation` của `ParcelController`, `CropSeasonController`, `DiseaseReportController` mô tả hành vi theo role.
- Schema `DiagnosisResponse` ghi rõ trường rút gọn của FARMER.
- Header `Idempotency-Key` khai báo bằng `@Parameter`.
- Tag `DiseaseReport` đổi mô tả từ nhãn milestone sang "Disease sighting reports".

## 3. SSO và deploy

| Hạng mục | Hành vi cũ | Hành vi mới |
|---|---|---|
| Client Keycloak cho mobile | Chỉ có `dxfarm-frontend` và `dxfarm-superset` | Thêm client public `dxfarm-mobile`: Standard Flow + PKCE, không direct access grants, redirect URI `dxfarm://*` và `exp://*` |
| `KC_HOSTNAME` trong `deploy/docker-compose.yml` | Cố định `http://localhost:${KEYCLOAK_HOST_PORT}` | Lấy từ `KEYCLOAK_PUBLIC_URL` (mẫu `http://localhost:8180`) |
| `deploy/.env.example` | Không có `KEYCLOAK_PUBLIC_URL` | Có `KEYCLOAK_PUBLIC_URL=http://localhost:8180` |

- Đặt `KEYCLOAK_PUBLIC_URL=http://localhost:8180` thì mọi thứ chạy như trước.
- Đặt `KEYCLOAK_PUBLIC_URL` sang IP LAN để điện thoại đăng nhập được; khi đó web và BI cũng phải truy cập Keycloak bằng địa chỉ này.
- Keycloak chỉ import realm khi realm chưa tồn tại; volume Postgres cũ cần thêm client `dxfarm-mobile` bằng tay hoặc xoá volume để import lại.

## 4. Web (`frontend/`)

| Thành phần | Hành vi cũ | Hành vi mới |
|---|---|---|
| Form đăng ký thửa (`ParcelPanel`) khi đăng nhập bằng FARMER | Nhập tự do tên chủ hộ | Ô tên chủ hộ hiển thị tên user và bị khoá |
| Form đăng ký thửa khi đăng nhập bằng ENGINEER/ADMIN | Nhập tự do tên chủ hộ | Không đổi |
| Hiển thị diagnosis (`DiagnosisPanel`) | Giả định mọi trường đều có giá trị | Bỏ qua trường `null`; FARMER thấy trạng thái, lý do từ chối khi `REJECTED`, nội dung chẩn đoán khi `APPROVED` |
| Kiểu `Diagnosis` (`types.ts`) | Các trường chẩn đoán không `null` | Các trường chẩn đoán có thể `null` |

## 5. Ứng dụng mobile (mới)

Ứng dụng Expo SDK 57 với `expo-router`, thư mục `mobile/`, chỉ dành cho role FARMER.

### 5.1. Màn hình

| Màn hình | Chức năng |
|---|---|
| Đăng nhập | Mở trình duyệt hệ thống tới Keycloak; tài khoản không phải FARMER bị đăng xuất kèm thông báo |
| Thửa của tôi | Danh sách thửa của user, badge "Đang cách ly" |
| Vụ mùa của thửa | Vụ đang hoạt động ở trên, vụ đã thu hoạch mờ hơn, banner cách ly |
| Báo cáo của vụ | Lưới ảnh kèm chip trạng thái, dải "Chờ gửi", nút chụp nổi |
| Chụp và gửi | Camera hệ thống, nén ảnh, thanh tiến trình, gửi lại khi lỗi |
| Chi tiết báo cáo | Timeline, khuyến nghị đã duyệt, lý do từ chối, nút "Chụp lại" |
| Hồ sơ | Thông tin user, danh sách chờ gửi, đăng xuất |

### 5.2. Ánh xạ trạng thái báo cáo

| `diagnosis` | Chip | Nội dung nông dân thấy |
|---|---|---|
| `null` | Chờ chẩn đoán | Ảnh và thời điểm gửi |
| `PENDING` | Chờ kỹ sư duyệt | Chỉ trạng thái |
| `APPROVED` | Đã duyệt | Bệnh, khuyến nghị, thuốc, liều, số ngày cách ly |
| `REJECTED` | Cần chụp lại | Lý do từ chối và nút "Chụp lại" |

### 5.3. Cơ chế chính

- **Xác thực**: Authorization Code + PKCE; token lưu ở `expo-secure-store`; tự refresh trước khi hết hạn; refresh bị từ chối hoặc `401` thì về màn Đăng nhập; đăng xuất kết thúc luôn phiên Keycloak.
- **Gửi ảnh**: thu nhỏ cạnh dài tối đa 1280px, JPEG 0.7, multipart part `image` qua `XMLHttpRequest`.
- **Hàng đợi offline**: mất mạng hoặc lỗi kết nối thì ảnh được lưu trong máy và tự gửi khi có mạng hoặc khi app trở lại foreground, thử lại với khoảng chờ tăng dần tối đa 8 lần; mỗi ảnh có UUID làm `Idempotency-Key` nên không tạo báo cáo trùng; hàng đợi tách theo tài khoản.
- **Cấu hình**: `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_KEYCLOAK_URL`, `EXPO_PUBLIC_KEYCLOAK_CLIENT_ID`; HTTP cleartext được phép ở mọi profile EAS trừ `production`.
- **Đóng gói**: `mobile/eas.json` có profile `preview` xuất APK; việc build do người dùng thực hiện theo `mobile/README.md`.

## 6. Ảnh hưởng khi nâng cấp

- Backend tự thêm cột `client_request_id` khi khởi động lại; không cần migration tay.
- Thửa tạo trước đây có tên chủ hộ khác tên trong token của FARMER không hiện với FARMER đó; sửa `farmerName` của thửa cho khớp nếu cần.
- Volume Postgres cũ cần thêm client `dxfarm-mobile` (xem mục 3).
- Client gọi trực tiếp API bằng vai trò FARMER cần chịu được các trường `null` trong `diagnosis` và mã `404` cho dữ liệu của người khác.
- Không có thay đổi hành vi cho ENGINEER và ADMIN, không đổi contract của các endpoint ghi ngoài `POST /parcels` (FARMER) và header `Idempotency-Key` tuỳ chọn.

## 7. Kiểm tra đã thực hiện và còn lại

| Hạng mục | Trạng thái |
|---|---|
| `./mvnw compile` (backend) | Đạt |
| `npx tsc --noEmit`, `npx expo export --platform android` (mobile) | Đạt |
| `npx tsc -b` (web) | Đạt |
| `npx expo-doctor` | 21/21 kiểm tra đạt |
| Chạy backend, chạy test, chạy app trên thiết bị | Chưa thực hiện |
| Checklist kiểm thử tay trong [`PLAN.md`](./PLAN.md) | Chờ người dùng thực hiện trên điện thoại thật |
| Build APK | Chờ người dùng thực hiện theo `mobile/README.md` |

## 8. Tồn đọng

- Icon và splash của app vẫn là ảnh mặc định của Expo.
- Mỗi thẻ thửa gọi thêm hai request để hiện badge cách ly; nhiều thửa cần endpoint gộp.
- Request thua khi hai lần gửi cùng key chạy đồng thời để lại một file ảnh mồ côi trong `uploads/`.
- Các mục trên và các mục backlog khác đều nằm trong [`README.md`](./README.md).
- `DiseaseReportService.listByCropSeason` truy vấn diagnosis riêng cho từng báo cáo; hành vi này có từ trước nhánh mobile.

## 9. Bản đồ commit

| Commit | Nội dung |
|---|---|
| `4d1d90e` | Kế hoạch, đặc tả backend và thiết kế UI |
| `76d95fd` | Phạm vi dữ liệu FARMER và ẩn chẩn đoán chưa duyệt (backend) |
| `daf9c11` | Client Keycloak `dxfarm-mobile` và `KEYCLOAK_PUBLIC_URL` |
| `bb25bf6` | Khung app Expo, API client, theme |
| `c2219a7` | Đăng nhập PKCE, refresh token, route guard |
| `4739ed9` | Màn Thửa, Vụ mùa, Báo cáo |
| `854fce2` | Chụp, gửi ảnh, chi tiết báo cáo |
| `51398d9` | `mobile/README.md` và mục license |
| `3c62b25` | Idempotency ở backend và hàng đợi offline |
| `9a965e1` | `eas.json` và hướng dẫn đóng gói APK |
| `0c21af7` | Sửa các điểm phát hiện khi review |
| `e0f9266` | Siết `harvest` và `POST /parcels` cho FARMER |
