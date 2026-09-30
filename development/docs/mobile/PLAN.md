# Kế hoạch triển khai mobile

> **Cập nhật:** 30/09/2026.

Bối cảnh, phạm vi và quyết định đã chốt xem [`README.md`](./README.md).
Đặc tả backend xem [`BACKEND_CHANGES.md`](./BACKEND_CHANGES.md).

## Quy ước làm việc

- Thay đổi backend đi theo thứ tự document plan → duyệt plan → swagger → code.
- Verify chỉ ở mức compile/typecheck: backend `./mvnw compile`; mobile `npx tsc --noEmit` và `npx expo export`.
  Không chạy `spring-boot:run`, không chạy test, không viết test file.
- Không tự commit hoặc push; cuối mỗi giai đoạn dừng để review diff và đề xuất commit message tiếng Anh.
- Comment và `@Operation` bằng tiếng Anh, ngắn, chỉ nêu hành vi hiện tại.
- Tài liệu `.md` viết mỗi câu một dòng và chỉ ghi trạng thái cuối.
- Bám pattern sẵn có: DTO là `record`, response bọc `ApiResponse<T>`, không trả entity JPA.
- Không tạo CI config.

## Giai đoạn 0 — Thiết kế UI (có cổng duyệt)

- [X] Danh sách màn hình và luồng: [`design/screens.md`](./design/screens.md).
- [X] Design tokens kế thừa web: [`design/tokens.md`](./design/tokens.md).
- [X] Prototype HTML bấm được: [`design/prototype.html`](./design/prototype.html).
- [X] User duyệt prototype và chốt danh sách màn hình.
- [ ] (Tuỳ chọn) publish prototype thành Artifact private hoặc chuyển sang Figma nếu user muốn.

**Cổng duyệt:** đã qua ngày 30/09/2026.

## Giai đoạn 1 — Backend: phạm vi dữ liệu và ẩn khuyến nghị chưa duyệt

Chi tiết: `BACKEND_CHANGES.md` mục 1, 2 và 4.

- [X] Cập nhật Swagger (`@Operation`, schema `DiagnosisResponse`).
- [X] Helper xác định FARMER và so khớp tên, đặt cạnh `CurrentUserService`.
- [X] `ParcelRepository.findByFarmerNameIgnoreCase` và lọc trong `ParcelService`.
- [X] Kiểm tra sở hữu trong `CropSeasonService` và `DiseaseReportService`.
- [X] `DiseaseReportService.toResponse` rút gọn diagnosis cho FARMER.
- [X] `./mvnw compile` và đọc lại diff.

## Giai đoạn 2 — SSO: client Keycloak cho mobile

Chi tiết: `BACKEND_CHANGES.md` mục 5.

- [X] Thêm client `dxfarm-mobile` vào `sso/realm-dxfarm.json`.
- [X] Cập nhật `sso/README.md` (phạm vi, cách dev với IP LAN).
- [X] Cho phép ghi đè `KC_HOSTNAME` qua `KEYCLOAK_PUBLIC_URL` (`deploy/docker-compose.yml`, `deploy/.env.example`).
- [X] Kiểm tra JSON hợp lệ bằng `python3 -m json.tool`.

## Giai đoạn 3 — Khung app `mobile/`

- [X] Khởi tạo Expo SDK mới nhất, TypeScript, `expo-router` tại `mobile/` (ngang hàng `frontend/`).
- [X] Phụ thuộc: `expo-auth-session`, `expo-secure-store`, `expo-image-picker`, `expo-image-manipulator`, `expo-web-browser`.
- [X] `app.json`: scheme `dxfarm`, quyền camera bằng chuỗi tiếng Việt, cho phép HTTP cleartext ngoài profile `production` qua `app.config.ts` và `expo-build-properties`.
- [X] `mobile/.env.example`: `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_KEYCLOAK_URL`, `EXPO_PUBLIC_KEYCLOAK_CLIENT_ID=dxfarm-mobile`.
- [X] Cấu trúc: `app/`, `src/api/`, `src/auth/`, `src/components/`, `src/theme/`, `src/types.ts`.
- [X] Client API port từ `frontend/src/api.ts` (`request`, `ApiError`, `json`); chỉ copy type farmer cần từ `frontend/src/types.ts`.
- [X] `npx tsc --noEmit` và `npx expo export`.

## Giai đoạn 4 — Xác thực

- [X] Authorization Code + PKCE bằng `expo-auth-session`.
- [X] Lưu access và refresh token ở `expo-secure-store`, tự refresh khi hết hạn.
- [X] `401` xoá phiên và về màn Đăng nhập.
- [X] Giải mã `realm_access.roles` như `frontend/src/auth.ts`; role khác FARMER bị đăng xuất kèm thông báo.
- [X] Route guard ở layout gốc.

## Giai đoạn 5 — Màn hình đọc dữ liệu

- [X] Thửa của tôi, Vụ mùa của thửa, Báo cáo của vụ, kèm pull-to-refresh và trạng thái rỗng/lỗi/tải.
- [X] Banner cách ly từ `IsolationStatus`.
- [X] Ảnh hiển thị qua `${API_BASE}/uploads/{imageRef}`.
- [X] Component dùng chung: `StatusChip`, `Screen`, `EmptyState`.

## Giai đoạn 6 — Chụp, gửi và chi tiết báo cáo

- [X] Chụp bằng `launchCameraAsync`, xử lý quyền camera bị từ chối.
- [X] Nén bằng `expo-image-manipulator`: cạnh dài tối đa 1280px, JPEG 0.7, dưới giới hạn multipart 10MB.
- [X] Gửi `FormData` part `image`, hiển thị tiến trình và lỗi.
- [X] Chi tiết báo cáo với timeline theo `diagnosis` (`null`, `PENDING`, `APPROVED`, `REJECTED`).
- [X] `REJECTED` hiện lý do và nút "Chụp lại".

Mốc hoàn thành luồng online đầu-cuối; có thể chốt demo tại đây.

## Giai đoạn 7 — Offline queue (có thể cắt)

Backend (`BACKEND_CHANGES.md` mục 3):

- [X] Header `Idempotency-Key` và `@Parameter` trong Swagger.
- [X] Cột `DiseaseReport.clientRequestId`, logic trả báo cáo cũ khi trùng key.
- [X] `./mvnw compile`.

Mobile:

- [X] Khi offline hoặc lỗi mạng: copy ảnh vào `documentDirectory`, ghi hàng đợi `{id, cropSeasonId, uri, createdAt, attempts}` vào AsyncStorage.
- [X] Tự flush khi `NetInfo` báo có mạng và khi app trở lại foreground, retry có backoff, `id` làm `Idempotency-Key`.
- [X] Lỗi `4xx` đánh dấu thất bại, không retry vô hạn.
- [X] Badge "Chờ gửi (N)" và danh sách trong Hồ sơ, cho phép xoá mục hỏng.
- [X] Phụ thuộc thêm: `@react-native-async-storage/async-storage`, `@react-native-community/netinfo`, `expo-file-system`; thêm `react-dom` cùng phiên bản `react` để `npm` không kéo bản không tương thích.

## Giai đoạn 8 — Đóng gói và tài liệu

- [X] `mobile/eas.json` với profile `preview` xuất APK; Expo Go cho dev. Việc build APK do user thực hiện theo hướng dẫn trong `mobile/README.md`.
- [X] `mobile/README.md`: mục đích, chạy dev với IP LAN, biến môi trường, tài khoản demo, build APK.
- [X] Cập nhật `README.md` gốc (kiến trúc, tiến độ, backlog) và `CHANGELOG.md` (mục Mobile).

## Bổ sung sau review

- [X] `POST /crop-seasons/{id}/harvest` trả `404` khi thửa không thuộc FARMER.
- [X] `POST /parcels` bởi FARMER lưu thửa với tên của user.
- [X] Web: form đăng ký thửa khoá tên chủ hộ cho FARMER; `DiagnosisPanel` chịu được diagnosis rút gọn.
- [X] Tổng quan thay đổi: [`CHANGES_OVERVIEW.md`](./CHANGES_OVERVIEW.md).

## Checklist kiểm thử tay

Chạy trên điện thoại thật, do user thực hiện.

- [ ] Đăng nhập `farmer1` thấy đúng thửa của mình, không thấy thửa người khác.
- [ ] Chụp và gửi thấy chip "Chờ chẩn đoán".
- [ ] Kỹ sư chẩn đoán trên web thì farmer thấy "Chờ kỹ sư duyệt" và không thấy thuốc, liều.
- [ ] Kỹ sư duyệt thì farmer thấy khuyến nghị đầy đủ; kỹ sư ghi nhận đã phun thì banner khoá thu hoạch xuất hiện.
- [ ] Kỹ sư từ chối thì farmer thấy lý do và nút "Chụp lại".
- [ ] Tắt mạng, chụp và gửi thì vào hàng đợi; bật mạng thì tự gửi đúng một lần.
- [ ] Đăng nhập `engineer1` bị chặn với thông báo.
- [ ] Đăng nhập web bằng `farmer1` và tạo thửa: thửa mang tên `Nguyễn Văn Nông` bất kể giá trị nhập.
- [ ] Farmer gọi `harvest` hoặc xem báo cáo của vụ thuộc thửa người khác nhận `404`.

## Rủi ro

- Sở hữu thửa dựa trên chuỗi tên: `farmerName` phải khớp claim `name` (`Nguyễn Văn Nông` với `farmer1`); lệch chính tả thì farmer không thấy thửa.
- Realm Keycloak không tự import lại trên volume cũ.
- Điện thoại phải truy cập được backend (`8081`) và Keycloak (`8180`) qua IP LAN cùng mạng.
- Lý do từ chối do kỹ sư nhập sẽ hiển thị cho farmer; cần nhắc kỹ sư không ghi thông tin nhạy cảm.
