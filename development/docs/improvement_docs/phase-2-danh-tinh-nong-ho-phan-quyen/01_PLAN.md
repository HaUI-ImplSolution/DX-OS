# Plan thực hiện: Phase 2

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP2-01 (Household + migration) chặn tất cả các WP còn lại — phải xong trước.
Sau đó WP2-02 (ma trận quyền), WP2-04 (audit + xóa mềm) làm song song được.
WP2-03 (chặn farmer web/Superset) không phụ thuộc WP2-01, làm song song được ngay từ đầu.
WP2-05 (onboarding Keycloak Admin API) phụ thuộc WP2-01 (cần `Household` tồn tại trước khi tạo kèm tài khoản).

Thứ tự: (WP2-01 ∥ WP2-03) → (WP2-02 ∥ WP2-04 ∥ WP2-05).

## WP2-01: Entity Household và migration

- Mục tham chiếu: mục 6 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: có một thực thể Nông hộ thật, Parcel trỏ vào hộ bằng khóa ngoại thay vì so khớp chuỗi tên.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `model/Parcel.java`: không có `householdId`/FK, chỉ có `private String farmerName` (`@Column(nullable = false)`, không unique).
- `service/CurrentUserService.canAccess(Parcel)`: `name.equalsIgnoreCase(parcel.getFarmerName())` — so chuỗi không phân biệt hoa thường, không dùng claim `sub`.
- Dự án hiện không dùng Flyway/Liquibase (chưa thấy thư mục migration trong `backend/DX-Farm/src/main/resources`, cần xác nhận lại khi code — nếu đúng là dùng `ddl-auto` của Hibernate thì đây là lần đầu đưa migration thật vào).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Xác định chủ thửa | So chuỗi `farmerName` (không phân biệt hoa/thường) với claim `name` | So `householdId` của `Parcel` với `Household.keycloakUserId` (claim `sub`) |
| 2 farmer trùng tên | Thấy chung thửa | Mỗi `sub` một `Household` riêng, không còn đụng nhau |
| Đổi tên farmer trong Keycloak | Mất quyền xem thửa cũ (tên không khớp nữa) | Không ảnh hưởng, vì khóa là `sub` không đổi |
| Tạo thửa mới | `farmerName` nhập tự do theo tên hiển thị | `householdId` lấy từ `Household` của người tạo (hoặc chọn hộ nếu admin tạo hộ) |

**Hướng làm**

- Thêm entity `Household` (`id`, `code` — mã hộ dạng `H0001` sinh tuần tự trong transaction, `fullName`, `phone`, `address`, `keycloakUserId` unique — claim `sub`, `createdAt`/`updatedAt`).
- `Parcel` thêm `householdId` (FK, nullable tạm thời trong giai đoạn migrate, sau đó đổi `NOT NULL` khi dữ liệu đã ghép xong); giữ cột `farmerName` lại tạm thời làm dữ liệu tham chiếu khi ghép (không xóa ngay, đánh dấu `@Deprecated` trong comment, dọn hẳn ở phase sau khi chắc chắn không còn chỗ nào đọc).
- `CurrentUserService`: thêm `currentHousehold()` (tra theo `sub`), đổi `canAccess(Parcel)` sang so `householdId`.
- Đưa Flyway vào dự án (`spring-boot-starter-flyway` hoặc `flyway-core` + `flyway-database-postgresql`), viết migration baseline mô tả schema hiện có trước, rồi thêm migration mới cho `Household` + `Parcel.household_id` + script ghép dữ liệu cũ (theo Q1).

**Phạm vi ảnh hưởng**

- Module và file chính: `model/Household.java` (mới), `model/Parcel.java`, `service/CurrentUserService.java`, `service/ParcelService.java`, `repository/HouseholdRepository.java` (mới), thư mục migration Flyway mới.
- Endpoint, bảng DB, biến môi trường: bảng `households` (mới), `parcels` thêm cột `household_id`; endpoint tạo thửa (`POST /api/v1/parcels`) đổi request/response (bỏ nhận `farmerName` tự do, dùng hộ của người gọi).
- Role và client bị ảnh hưởng: farmer (mobile) — không đổi trải nghiệm nếu hộ đã có sẵn; admin cần màn/endpoint tạo hộ trước khi có farmer nào tạo thửa lần đầu (xem WP2-05).
- Tương thích ngược và dữ liệu cũ: bắt buộc migrate dữ liệu — mọi `Parcel` cũ phải ghép được với một `Household` (tạo mới theo `farmerName` nếu chưa có hộ nào khớp, theo Q1).

**Kết quả đầu ra**

- Mọi `Parcel` trong DB sau migration có `household_id` khác null.
- `canAccess` dùng `householdId`, test xác nhận 2 hộ trùng tên không thấy thửa của nhau.

**Phụ thuộc, lưu ý**: chặn WP2-02, WP2-04, WP2-05.
Cần chốt Q1 trước khi viết script ghép dữ liệu.

## WP2-02: Ma trận quyền và `@PreAuthorize`

- Mục tham chiếu: mục 1 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: engineer không còn tạo được thửa/vụ/các thao tác ảnh hưởng nông hộ; mọi quyền theo bảng rõ ràng, không vá từng chỗ.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `security/SecurityConfig.java`: chỉ có `PUBLIC_GET_ENDPOINTS` (danh mục bệnh/thuốc/rule, `/uploads/**`) và `/internal/v1/**` (AI).
  Mọi endpoint `/api/**` còn lại chỉ cần `anyRequest().authenticated()` — không phân biệt role.
- `controller/DiagnosisController.java`: điểm sáng duy nhất hiện có — class-level `@PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")`, dùng làm mẫu để áp cho các controller khác.
- `controller/ParcelController.java`, `CropSeasonController` (cần xác nhận tên file chính xác khi code): không có `@PreAuthorize` nào.

**Ma trận quyền (role x hành động)**

| Hành động | FARMER (mobile) | ENGINEER | ADMIN |
|---|---|---|---|
| Xem thửa/vụ/báo cáo của hộ mình | Có | - | - |
| Xem thửa/vụ/báo cáo của mọi hộ | Không | Có | Có |
| Tạo thửa, tạo vụ (yêu cầu, chờ duyệt — P4) | Có | Không | Có (thay mặt, có cảnh báo — P4/P5) |
| Duyệt/từ chối thửa, vụ (P4) | Không | Không | Có |
| Gửi báo cáo sâu bệnh | Có | Không | Không |
| Nhập tay/chốt chẩn đoán | Không | Có | Có |
| Duyệt/từ chối chẩn đoán | Không | Có | Có |
| Ghi nhận đã phun thuốc (`apply`) | Có (tự khai, P5) | Có (xác nhận lại, P5) | Có |
| Khai báo thu hoạch | Có | Không | Có (thay mặt, có cảnh báo — P5) |
| Tạo hộ + tài khoản (onboarding) | Không | Không | Có |
| Xem Superset / BI | Không | Có | Có |
| Vào web (ngoài mobile) | Không (chặn — WP2-03) | Có | Có |

Cột FARMER cho "ghi nhận đã phun"/"khai báo thu hoạch" phản ánh hướng đích đến ở P5; ở phase 2 chỉ khóa đúng quyền hiện có (ENGINEER thực hiện `apply`, FARMER/ADMIN thực hiện khai báo thu hoạch như code hiện tại), chưa đổi luồng nghiệp vụ.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Engineer gọi `POST /api/v1/parcels` | 200, tạo được thửa | 403 |
| Engineer gọi `POST .../crop-seasons` | 200 | 403 |
| Farmer gọi endpoint duyệt chẩn đoán | Đã bị chặn (DiagnosisController có `@PreAuthorize`) | Giữ nguyên |
| Admin gọi mọi endpoint ghi | 200 | Giữ 200 (admin toàn quyền) |

**Hướng làm**

- Thêm `@PreAuthorize` theo mẫu `DiagnosisController` cho từng method ghi (không phải class-level nếu controller có cả GET lẫn POST với quyền khác nhau — ví dụ `ParcelController`: GET mở cho mọi role đã đăng nhập, POST giới hạn FARMER/ADMIN).
- Với hành động "farmer chỉ thao tác trên hộ/thửa của mình": kết hợp `@PreAuthorize` (chặn role) với check `canAccess(...)` đã có trong service (chặn phạm vi dữ liệu) — hai lớp độc lập, không thay thế nhau.
- Viết test xác nhận đúng ma trận ở trên cho các hành động đã tồn tại (không test hành động thuộc P4/P5 chưa code).

**Phạm vi ảnh hưởng**

- Module và file chính: `controller/ParcelController.java`, `controller/CropSeasonController.java` (xác nhận tên khi code), `controller/DiseaseReportController.java` (nếu có khoảng trống), `service/CurrentUserService.java`.
- Endpoint, bảng DB, biến môi trường: không có bảng mới; nhiều endpoint ghi đổi từ 200 sang 403 cho role không hợp lệ.
- Role và client bị ảnh hưởng: engineer (web) mất quyền tạo thửa/vụ nếu đang dùng (theo đúng đề mục 1).
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng dữ liệu, chỉ ảnh hưởng ai gọi được endpoint nào.

**Kết quả đầu ra**

- Ma trận quyền ở trên có test cho từng dòng áp dụng được ngay (không thuộc P4/P5).

**Phụ thuộc, lưu ý**: cần WP2-01 xong vì một số check dùng `householdId`.

## WP2-03: Chặn FARMER ở web và Superset, thêm `aud`

- Mục tham chiếu: mục 1, 9, 16 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: role FARMER không đăng nhập được vào web hoặc Superset, chỉ dùng mobile.
- Ước lượng: S.

**Hiện trạng đã rà soát**

- `sso/README.md`: backend xác thực bằng `jwk-set-uri`, không kiểm `iss`; đây là đánh đổi có chủ đích vì mobile/web hiện vào Keycloak qua hostname khác nhau (chưa có reverse proxy thống nhất — việc đó thuộc P3).
  Kiểm `aud` là việc độc lập với `iss`, làm được ngay ở phase này.
- `sso/realm-dxfarm.json`: chưa có audience mapper nào (không tìm thấy `"aud"`/`audience` trong file) — client nào cũng xin được token dùng gọi API backend.
- Web (`frontend/`) hiện không có logic chặn role nào ở phía client (theo PROJECT_IMPROVEMENTS mục 16 "Web vẫn cho FARMER đăng nhập và dùng").

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Farmer đăng nhập web | Đăng nhập được, dùng được các chức năng | Đăng nhập xong bị chặn ngay (màn thông báo "vui lòng dùng app di động"), không gọi được API |
| Farmer đăng nhập Superset | Vào được (nếu có tài khoản map) | Keycloak không cấp quyền role FARMER cho client Superset |
| Token không có `aud` đúng cho backend | Backend vẫn chấp nhận (chỉ kiểm chữ ký) | Backend từ chối (401) nếu `aud` không chứa client id của API |

**Hướng làm**

- Keycloak: thêm audience mapper cho client API (gắn `aud` = client id của backend) vào `realm-dxfarm.json`; cấu hình client Superset không map role `FARMER` (hoặc chặn ở Superset OIDC role mapping).
- Backend: thêm `JwtClaimValidator`/`JwtIssuerValidator` tương đương cho `aud` trong cấu hình `JwtDecoder` (giữ nguyên việc không kiểm `iss` theo đánh đổi đã ghi ở `sso/README.md`, chỉ thêm `aud`).
- Web: sau khi nhận token, decode claim role; nếu chỉ có `FARMER` (không có `ENGINEER`/`ADMIN`) thì hiện màn chặn và không gọi API nghiệp vụ nào (vẫn cho logout).

**Phạm vi ảnh hưởng**

- Module và file chính: `sso/realm-dxfarm.json`, cấu hình `JwtDecoder` ở backend (`security/` package), `frontend/src/` (thêm màn/guard chặn role FARMER).
- Endpoint, bảng DB, biến môi trường: không có endpoint mới; mọi API hiện có thêm điều kiện `aud` khi xác thực token.
- Role và client bị ảnh hưởng: FARMER trên web/Superset (chặn); FARMER trên mobile không đổi (mobile vẫn xin đúng `aud` khi gọi API).
- Tương thích ngược và dữ liệu cũ: mobile hiện tại phải xác nhận đã xin token đúng client/`aud` — nếu dùng chung client với web thì cần tách client Keycloak riêng cho mobile trước khi bật kiểm `aud` (kiểm tra kỹ khi code, tránh khóa luôn mobile).

**Kết quả đầu ra**

- Farmer đăng nhập web: không gọi được API nghiệp vụ nào (401/403 nhất quán, không phải lỗi JS vụn vặt).
- Token thiếu `aud` hợp lệ: mọi API (trừ public) trả 401.

**Phụ thuộc, lưu ý**: không phụ thuộc WP2-01, làm song song được; cần rà kỹ client Keycloak hiện có (web/mobile dùng chung hay riêng client id) trước khi bật `aud` để không khóa nhầm mobile.

## WP2-04: Audit theo id người dùng và xóa mềm

- Mục tham chiếu: mục 6 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: `reportedBy`/`decidedBy` lưu được định danh ổn định (không chỉ tên hiển thị); có đường xóa mềm cho bản ghi tạo nhầm.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `model/DiseaseReport.java:46`: `private String reportedBy;` — lưu tên hiển thị.
- `model/Diagnosis.java:77`: `private String decidedBy;` — lưu tên hiển thị.
- Không tìm thấy cột `deletedAt`/cờ xóa mềm nào trong `model/*.java` hiện có.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Đổi tên user trong Keycloak | `reportedBy`/`decidedBy` cũ vẫn là tên cũ, 2 người trùng tên không phân biệt được | Thêm `reportedByUserId`/`decidedByUserId` (claim `sub`), giữ tên hiển thị song song để không phá dữ liệu cũ |
| Nhập nhầm thửa/vụ/báo cáo | Không có đường hủy, phải sửa DB tay | Có cờ `deletedAt`, API xóa mềm cho ADMIN (ParcelController/CropSeasonController/DiseaseReportController) |
| Query danh sách sau khi xóa mềm | - | Mặc định lọc `deletedAt IS NULL`; ADMIN xem được bản đã xóa theo Q2 |

**Hướng làm**

- Thêm cột `reportedByUserId`/`decidedByUserId` (String, lưu `sub`) bên cạnh cột tên hiển thị hiện có, không đổi tên cột cũ (tránh phá API response hiện tại).
- Thêm `deletedAt` (Instant, nullable) cho `Parcel`, `CropSeason`, `DiseaseReport`; thêm `@Where(clause = "deleted_at IS NULL")` hoặc lọc tường minh ở repository (chọn theo convention JPA dự án đang dùng).
- Endpoint `DELETE .../parcels/{id}`, `.../crop-seasons/{id}`, `.../disease-reports/{id}` (mới) — chỉ ADMIN, set `deletedAt`, không xóa vật lý.

**Phạm vi ảnh hưởng**

- Module và file chính: `model/Parcel.java`, `model/CropSeason.java`, `model/DiseaseReport.java`, `model/Diagnosis.java`, các controller tương ứng, migration Flyway mới.
- Endpoint, bảng DB, biến môi trường: 3 endpoint `DELETE` mới; các bảng tương ứng thêm cột `deleted_at`, `reported_by_user_id`, `decided_by_user_id`.
- Role và client bị ảnh hưởng: chỉ ADMIN có quyền xóa mềm.
- Tương thích ngược và dữ liệu cũ: dữ liệu cũ có `reportedByUserId`/`decidedByUserId` là `NULL` — chấp nhận được (chỉ áp dụng cho bản ghi mới từ đây), không cần backfill.

**Kết quả đầu ra**

- Bản ghi mới tạo sau phase có đủ cả tên hiển thị và id người dùng.
- Xóa mềm một thửa: thửa biến mất khỏi danh sách GET thường, còn trong DB.

**Phụ thuộc, lưu ý**: cần WP2-01 xong (dùng chung migration Flyway, tránh 2 PR cùng sửa schema xung đột).

## WP2-05: Onboarding — admin tạo hộ và tài khoản

- Mục tham chiếu: mục 1, 12 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: admin tạo được hộ + tài khoản Keycloak ngay trong hệ thống, không phải vào Keycloak Admin Console thủ công.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- Hiện tại admin tạo user hoàn toàn qua Keycloak Admin Console (ngoài hệ thống), sau đó farmer tự tạo thửa và backend so tên — không có bước "tạo hồ sơ nông hộ" tách biệt.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Admin thêm nông hộ mới | Vào Keycloak Admin Console tạo user tay, không có hồ sơ hộ trong hệ thống | Gọi `POST /api/v1/households` (backend gọi Keycloak Admin REST API bằng service account riêng) tạo cả `Household` lẫn user Keycloak role FARMER trong một bước |

**Hướng làm**

- Thêm service account Keycloak riêng (client credentials, quyền tối thiểu `manage-users` trên realm) cho backend gọi Keycloak Admin REST API — không dùng tài khoản admin cá nhân.
- `POST /api/v1/households`: tạo `Household` trước (sinh `code` tuần tự), sau đó gọi Keycloak tạo user (set password tạm, buộc đổi lần đầu), map `keycloakUserId` = `sub` vừa tạo vào `Household`.
  Thất bại ở bước Keycloak thì rollback `Household` (transaction ngoài không bọc được lời gọi HTTP, cần bù trừ thủ công: xóa `Household` vừa tạo nếu tạo user Keycloak lỗi).

**Phạm vi ảnh hưởng**

- Module và file chính: `service/HouseholdService.java` (mới), `client/KeycloakAdminClient.java` (mới), cấu hình client credentials mới trong `application.yaml`/`.env.example`.
- Endpoint, bảng DB, biến môi trường: `POST /api/v1/households` (mới); biến `KEYCLOAK_ADMIN_CLIENT_ID`, `KEYCLOAK_ADMIN_CLIENT_SECRET` (mới, không có default — theo nguyên tắc WP1-01).
- Role và client bị ảnh hưởng: chỉ ADMIN gọi được; thêm một service-to-service credential mới (bề mặt tấn công tăng nhẹ, cần quyền tối thiểu).
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng tài khoản đã tạo tay trước đó qua Admin Console (vẫn hoạt động bình thường, chỉ chưa có `Household` tương ứng — xử lý ở WP2-01 Q1).

**Kết quả đầu ra**

- Admin tạo được 1 hộ + 1 tài khoản FARMER mới hoàn toàn qua API, đăng nhập mobile được ngay bằng mật khẩu tạm.

**Phụ thuộc, lưu ý**: cần WP2-01 xong (entity `Household` tồn tại).

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST /api/v1/parcels` | Không nhận `farmerName` tự do, dùng hộ của người gọi | Có |
| Endpoint | `POST /api/v1/parcels`, `POST .../crop-seasons` | ENGINEER gọi → 403 | Có |
| Endpoint | `DELETE /api/v1/parcels/{id}`, `/crop-seasons/{id}`, `/disease-reports/{id}` | Mới, chỉ ADMIN | Không (thêm mới) |
| Endpoint | `POST /api/v1/households` | Mới, chỉ ADMIN | Không (thêm mới) |
| Bảng DB | `households` | Bảng mới | Không |
| Bảng DB | `parcels` | Thêm `household_id` (FK), `deleted_at`; `farmer_name` giữ tạm, đánh dấu không dùng cho logic mới | Có (sau khi `household_id NOT NULL`) |
| Bảng DB | `disease_reports` | Thêm `reported_by_user_id`, `deleted_at` | Không |
| Bảng DB | `diagnosis` | Thêm `decided_by_user_id` | Không |
| Biến môi trường | `KEYCLOAK_ADMIN_CLIENT_ID`, `KEYCLOAK_ADMIN_CLIENT_SECRET` | Mới, không default | Không |
| Cấu hình | Keycloak realm | Thêm audience mapper, chặn role FARMER ở client Superset | Có — client cũ phải xin lại token sau khi đổi |

## Dữ liệu, migration và rollback

- Migration: dùng Flyway từ phase này trở đi; migration đầu tiên là baseline mô tả schema hiện có (để không phá dữ liệu ai đang chạy), sau đó các migration tăng dần cho `Household`, cột mới, script ghép dữ liệu.
- Dữ liệu cũ: script ghép `Parcel.farmerName` → `Household` theo quyết định Q1; hộ nào không khớp `sub` nào (ví dụ dữ liệu demo không có tài khoản Keycloak thật tương ứng) thì tạo `Household` "orphan" (không có `keycloakUserId`), ghi log để admin xử lý tay sau.
- Rollback: Flyway hỗ trợ migrate xuống nếu cần, nhưng ưu tiên sửa tiếp thay vì rollback vì đã có người dùng thật (farmer demo) phụ thuộc dữ liệu mới.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D9 (kế thừa phase 0) | Phân quyền theo thao tác, không theo khu vực | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | Tạo hộ mới riêng cho từng `farmerName` lạ, admin gộp tay | Fuzzy-match tự động dễ ghép sai người, rủi ro hơn để trùng |
| Q2 | Xóa mềm: admin xem qua filter `?includeDeleted=true` | Giữ khả năng audit mà không ảnh hưởng danh sách thường |

## Ghi chú triển khai

- Flyway: `V1__baseline.sql` sinh từ schema Hibernate đang chạy (đã gồm cột `version` và index một vụ ACTIVE của phase 1), `V2` thêm `households`, `V3` thêm cột audit và xóa mềm. `spring.jpa.hibernate.ddl-auto` là `validate`; bỏ `defer-datasource-initialization` để Flyway chạy trước Hibernate, `data.sql` chạy giữa hai bước.
- Mã hộ sinh từ sequence `household_code_seq` (có thể nhảy số khi tạo hộ thất bại).
- Bảng `households` không cấp cho role `superset_ro`.
- Cột `parcels.farmer_name` giữ lại làm bản sao họ tên chủ hộ cho BI, không dùng cho phân quyền.
- Mã thửa chỉ unique giữa các thửa chưa xóa (`uq_parcels_code_not_deleted`); index một vụ ACTIVE bỏ qua vụ đã xóa mềm.
- Ma trận quyền áp đúng bảng ở trên: gửi báo cáo chỉ FARMER (ADMIN và ENGINEER nhận 403); web bỏ form gửi báo cáo.
- `aud` do mapper `dxfarm-api-audience` thêm vào hai client web và mobile; claim `sub` do scope `basic` (realm phải gán scope này cho client).
- Chặn farmer ở web bằng claim `azp` (`FarmerClientRestrictionFilter`), thêm vào màn chặn ở web; Superset chặn trong `oauth_user_info`.
- Realm đặt `email` là tùy chọn trong user profile để nông dân không có email vẫn đăng nhập được; tên đầy đủ khi tạo hộ cần ít nhất hai từ.
- Thêm endpoint `GET /api/v1/households` và `PATCH /api/v1/households/{code}/account` (gắn tài khoản có sẵn vào hộ do migration tạo) ngoài phạm vi ban đầu của plan.
- DB dev cũ: backend tự baseline và lên V3, nhưng Keycloak chỉ import realm một lần nên cần `deploy/reset.sh` (hoặc cập nhật client/user profile thủ công) để có `aud`, `sub`, service account và user profile mới.
