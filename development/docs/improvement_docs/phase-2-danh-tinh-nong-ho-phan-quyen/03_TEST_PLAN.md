# Kế hoạch kiểm thử: Phase 2

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống (phạm vi dữ liệu, phân quyền, toàn vẹn danh tính), không coverage đầy đủ happy path.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP2-01/02/04; thủ công (Keycloak, web) cho WP2-03/05.
- Môi trường: clone sạch + seed demo đã chạy migration Household.
- Dữ liệu và tài khoản test: 2 household cùng `fullName` nhưng `sub` khác nhau, mỗi hộ có 1 thửa riêng; farmer1, engineer1, admin1.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T2-01 | WP2-01 | Poka-yoke | 2 hộ cùng tên, mỗi hộ gọi `GET /api/v1/parcels` | Mỗi hộ chỉ thấy thửa của mình | | Đạt (E2E + unit) |
| T2-02 | WP2-01 | Hồi quy | Đổi claim `name` trong token (không đổi `sub`), farmer gọi lại `GET /api/v1/parcels` | Vẫn thấy đúng thửa cũ | | Đạt (E2E) |
| T2-03 | WP2-02 | Phân quyền | ENGINEER gọi `POST /api/v1/parcels` | 403 | | Đạt (E2E + MockMvc) |
| T2-04 | WP2-02 | Phân quyền | ENGINEER gọi `POST .../crop-seasons` | 403 | | Đạt (E2E + MockMvc) |
| T2-05 | WP2-02 | Phân quyền | ADMIN gọi các endpoint ghi ở trên | 200 | | Đạt (E2E + MockMvc) |
| T2-06 | WP2-03 | Phân quyền (client) | Farmer đăng nhập web, thử gọi API nghiệp vụ | FE chặn trước khi gọi; nếu gọi trực tiếp API thì 401/403 | | Đạt (E2E: token web 403; MockMvc) |
| T2-07 | WP2-03 | Âm tính | Gọi API với token thiếu `aud` hợp lệ | 401 | | Đạt (E2E: 401; unit validator) |
| T2-08 | WP2-03 | Hồi quy | Mobile đăng nhập và gọi API bình thường sau khi bật `aud` | Không đổi hành vi, vẫn 200 | | Đạt qua luồng client mobile; chưa chạy app trên điện thoại |
| T2-09 | WP2-04 | Poka-yoke | ADMIN xóa mềm 1 thửa, `GET` thường và `GET ?includeDeleted=true` | Thửa ẩn ở danh sách thường, hiện ở danh sách có `includeDeleted` | | Đạt (E2E) |
| T2-10 | WP2-04 | Phân quyền | FARMER/ENGINEER gọi `DELETE` thửa | 403 | | Đạt (E2E + MockMvc) |
| T2-11 | WP2-05 | Happy path | ADMIN tạo hộ mới qua `POST /api/v1/households` | `Household` + user Keycloak tồn tại, đăng nhập mobile bằng mật khẩu tạm thành công | | Đạt (E2E) |
| T2-12 | WP2-05 | Âm tính | Giả lập Keycloak Admin API lỗi khi tạo hộ | `Household` vừa tạo bị rollback (không còn trong DB) | | Đạt (Postgres thật + E2E khi Keycloak dừng) |

## 3. Hồi quy (không được hỏng)

- [x] Khóa PHI (P1) vẫn chặn đúng sau khi thêm `Household`, `@Version`.
- [x] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [x] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động — riêng farmer bị chặn có chủ đích ở web/Superset.
- [x] Thông báo Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ.
- [x] Dựng từ clone sạch (gồm Flyway migration mới) bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.
- [x] Danh sách thửa/vụ/báo cáo hiện có (tạo trước phase 2) vẫn đọc được bình thường sau migration.

## 4. Tiêu chí đạt của phase (exit criteria)

- T2-01 đến T2-12 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test, log migration Flyway chạy trên clone sạch.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).

## 6. Kết quả chạy

- Test backend: 46 test đạt (Mockito, MockMvc ma trận quyền, `ConcurrencyIntegrationTest`, `HouseholdMigrationTest`, `HouseholdServiceIntegrationTest` trên PostgreSQL qua Testcontainers). Hibernate `validate` trong các test này đối chiếu entity với schema do Flyway dựng.
- Migration: DB cũ có `parcels` kèm `farmer_name` được ghép đúng thành hộ (so tên không phân biệt hoa thường), `household_id` thành `NOT NULL`; DB tạo trước Flyway được baseline rồi lên V3.
- Stack thật dựng bằng `deploy/reset.sh` (Postgres, backend, Keycloak, Superset): 46/47 bước của kịch bản gọi API đạt; bước còn lại chỉ lệch định dạng chuỗi trong script (kết quả thực tế đúng).
  Gồm: Flyway V1-V3 trên DB trống, `aud`, chặn token farmer qua client web, onboarding và mật khẩu tạm, Keycloak dừng thì không lưu hộ, gắn tài khoản vào hộ cũ, cùng tên khác hộ, đổi tên không mất thửa, ma trận quyền, xóa mềm, id người dùng trong audit, `superset_ro` không đọc `households`, Superset từ chối farmer.
- Lỗi tìm thấy khi chạy thật và đã sửa: `defer-datasource-initialization` làm Flyway chạy sau Hibernate; realm thiếu scope `basic` nên token không có `sub`; tài khoản không có email bị Keycloak coi là chưa hoàn tất hồ sơ (đặt email thành tùy chọn trong user profile của realm).
- Chưa chạy: đăng nhập bằng app mobile trên điện thoại thật; CI GitHub cho nhánh này.
