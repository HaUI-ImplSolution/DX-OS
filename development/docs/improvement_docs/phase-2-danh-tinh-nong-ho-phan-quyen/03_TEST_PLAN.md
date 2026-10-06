# Kế hoạch kiểm thử: Phase 2

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống (phạm vi dữ liệu, phân quyền, toàn vẹn danh tính), không coverage đầy đủ happy path.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP2-01/02/04; thủ công (Keycloak, web) cho WP2-03/05.
- Môi trường: clone sạch + seed demo đã chạy migration Household.
- Dữ liệu và tài khoản test: 2 household cùng `fullName` nhưng `sub` khác nhau, mỗi hộ có 1 thửa riêng; farmer1, engineer1, admin1.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T2-01 | WP2-01 | Poka-yoke | 2 hộ cùng tên, mỗi hộ gọi `GET /api/v1/parcels` | Mỗi hộ chỉ thấy thửa của mình | | |
| T2-02 | WP2-01 | Hồi quy | Đổi claim `name` trong token (không đổi `sub`), farmer gọi lại `GET /api/v1/parcels` | Vẫn thấy đúng thửa cũ | | |
| T2-03 | WP2-02 | Phân quyền | ENGINEER gọi `POST /api/v1/parcels` | 403 | | |
| T2-04 | WP2-02 | Phân quyền | ENGINEER gọi `POST .../crop-seasons` | 403 | | |
| T2-05 | WP2-02 | Phân quyền | ADMIN gọi các endpoint ghi ở trên | 200 | | |
| T2-06 | WP2-03 | Phân quyền (client) | Farmer đăng nhập web, thử gọi API nghiệp vụ | FE chặn trước khi gọi; nếu gọi trực tiếp API thì 401/403 | | |
| T2-07 | WP2-03 | Âm tính | Gọi API với token thiếu `aud` hợp lệ | 401 | | |
| T2-08 | WP2-03 | Hồi quy | Mobile đăng nhập và gọi API bình thường sau khi bật `aud` | Không đổi hành vi, vẫn 200 | | |
| T2-09 | WP2-04 | Poka-yoke | ADMIN xóa mềm 1 thửa, `GET` thường và `GET ?includeDeleted=true` | Thửa ẩn ở danh sách thường, hiện ở danh sách có `includeDeleted` | | |
| T2-10 | WP2-04 | Phân quyền | FARMER/ENGINEER gọi `DELETE` thửa | 403 | | |
| T2-11 | WP2-05 | Happy path | ADMIN tạo hộ mới qua `POST /api/v1/households` | `Household` + user Keycloak tồn tại, đăng nhập mobile bằng mật khẩu tạm thành công | | |
| T2-12 | WP2-05 | Âm tính | Giả lập Keycloak Admin API lỗi khi tạo hộ | `Household` vừa tạo bị rollback (không còn trong DB) | | |

## 3. Hồi quy (không được hỏng)

- [ ] Khóa PHI (P1) vẫn chặn đúng sau khi thêm `Household`, `@Version`.
- [ ] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động — riêng farmer bị chặn có chủ đích ở web/Superset.
- [ ] Thông báo Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ.
- [ ] Dựng từ clone sạch (gồm Flyway migration mới) bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.
- [ ] Danh sách thửa/vụ/báo cáo hiện có (tạo trước phase 2) vẫn đọc được bình thường sau migration.

## 4. Tiêu chí đạt của phase (exit criteria)

- T2-01 đến T2-12 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test, log migration Flyway chạy trên clone sạch.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
