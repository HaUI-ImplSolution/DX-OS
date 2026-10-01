# Đặc tả thay đổi backend cho mobile

> **Cập nhật:** 30/09/2026.

Tài liệu này là spec đi trước Swagger và code theo thứ tự Spec → Swagger → Code.
Chỉ FARMER bị ảnh hưởng; ENGINEER và ADMIN giữ nguyên hành vi.

| Mục | Trạng thái |
|---|---|
| 1. Phạm vi dữ liệu của farmer | Đã triển khai |
| 2. Ẩn nội dung chẩn đoán chưa duyệt | Đã triển khai |
| 3. Idempotency cho gửi báo cáo | Đã triển khai |
| 4. Swagger | Đã triển khai cho mục 1, 2 và 3 |
| 5. SSO | Đã triển khai |

## 1. Phạm vi dữ liệu của farmer

Hành vi khi user hiện tại có role FARMER:

| Endpoint | Hành vi |
|---|---|
| `GET /api/v1/parcels` | Chỉ trả thửa có `farmerName` khớp tên user, không phân biệt hoa thường |
| `POST /api/v1/parcels` | `farmerName` trong request bị bỏ qua, thửa được lưu với tên của user |
| `GET /api/v1/parcels/{code}` | `404` nếu thửa không thuộc user |
| `GET/POST /api/v1/parcels/{code}/crop-seasons` | `404` nếu thửa không thuộc user |
| `GET /api/v1/crop-seasons/{id}` và `/isolation-status` | `404` nếu thửa của vụ không thuộc user |
| `POST /api/v1/crop-seasons/{id}/harvest` | `404` nếu thửa của vụ không thuộc user |
| `GET/POST /api/v1/crop-seasons/{id}/disease-reports` | `404` nếu thửa của vụ không thuộc user |
| `GET /api/v1/disease-reports/{id}` | `404` nếu thửa của vụ chứa báo cáo không thuộc user |

- Người bị giới hạn là user có `ROLE_FARMER` mà không có `ROLE_ENGINEER` hoặc `ROLE_ADMIN` (`CurrentUserService.isFarmer()`).
- Tên user lấy từ `CurrentUserService.currentFullName()` (claim `name`, dự phòng `preferred_username`).
- Trả `404` thay vì `403` để không lộ sự tồn tại của thửa người khác.
- Field `farmerName` của `ParcelCreateRequest` vẫn bắt buộc để hợp đồng API không đổi; ENGINEER và ADMIN nhập tên chủ hộ như trước.
- Form đăng ký thửa trên web khoá ô tên chủ hộ theo tên người dùng khi đăng nhập bằng FARMER.

## 2. Ẩn nội dung chẩn đoán chưa duyệt

Hành vi cho FARMER ở `GET /disease-reports/{id}` và `GET /crop-seasons/{id}/disease-reports`, theo `approvalStatus` của diagnosis:

| `approvalStatus` | Trường được trả | Trường `null` |
|---|---|---|
| `PENDING` | `id`, `diseaseReportId`, `approvalStatus` | Toàn bộ còn lại |
| `REJECTED` | `id`, `diseaseReportId`, `approvalStatus`, `rejectionReason`, `decidedAt` | Toàn bộ còn lại |
| `APPROVED` | Toàn bộ | — |

- `diagnosis` vẫn là `null` khi báo cáo chưa được chẩn đoán.
- `DiagnosisService.toResponse` (dùng cho ENGINEER/ADMIN) không đổi; phần rút gọn nằm ở tầng `DiseaseReportService.toResponse`.
- Web đăng nhập bằng FARMER cũng nhận dữ liệu rút gọn; `DiagnosisPanel` bỏ qua các trường `null`.

## 3. Idempotency cho gửi báo cáo

- `POST /api/v1/crop-seasons/{id}/disease-reports` nhận header tuỳ chọn `Idempotency-Key` (UUID do client sinh).
- `DiseaseReport` thêm cột `clientRequestId` (`UUID`, unique, nullable); `ddl-auto: update` tự thêm cột.
- Nếu key đã tồn tại thì trả về báo cáo cũ với cùng response, không lưu ảnh lần hai.
- Không có header thì hành vi như hiện tại.
- Key trùng nhưng thuộc vụ mùa khác hoặc user khác trả `409`.
- Hai request cùng key chạy đồng thời: request thua ở ràng buộc unique trả `409`; client gửi lại sẽ nhận báo cáo đã lưu.
- `Idempotency-Key` không phải UUID hợp lệ trả `400`.

## 4. Swagger

- Cập nhật `@Operation` ở `ParcelController`, `CropSeasonController`, `DiseaseReportController` để mô tả hành vi theo role.
- Schema `DiagnosisResponse` ghi rõ FARMER nhận trường rút gọn khi chưa `APPROVED`.
- Header `Idempotency-Key` khai báo bằng `@Parameter`.
- `@Operation`/`@Tag` viết tiếng Anh, không nhãn milestone.

## 5. SSO

- Client public `dxfarm-mobile` trong `sso/realm-dxfarm.json`: Standard Flow + PKCE, không direct access grants, redirect URI `dxfarm://*` và `exp://*` (chỉ dev).
- Backend không đổi: xác thực bằng `jwk-set-uri`, không kiểm tra `iss`, nên token cấp qua IP LAN vẫn hợp lệ.
- Keycloak chỉ import realm khi realm chưa tồn tại; volume cũ cần thêm client qua Admin Console hoặc xoá volume để import lại.
- Keycloak không đặt `KC_HOSTNAME`, dùng hostname của request, để điện thoại truy cập qua IP LAN còn web qua `localhost`.

## 6. Ngoài phạm vi

- `/uploads/**` giữ public như thiết kế hiện tại.
- Không thêm endpoint đăng ký device token hay push.
- Không thêm cột owner cho `Parcel`.
