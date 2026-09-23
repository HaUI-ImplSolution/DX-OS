# Kế hoạch kiểm thử: Phase C1

Quy ước dự án: mặc định chỉ compile và đọc diff.
Chạy test, dựng stack hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ; ghi ai chạy và khi nào ở cột "Người chạy".
Không thêm file test mới; test hiện có đi theo module chứa code được kiểm.

## 1. Chiến lược

- Mức kiểm thử áp dụng: compile toàn bộ module, tìm kiếm import hai chiều, so sánh OpenAPI và schema DB với baseline; test, E2E do người dùng chạy.
- Môi trường: clone sạch, `deploy/reset.sh`, `DXOS_APP=farm`.
- Dữ liệu và tài khoản test: tài khoản và dữ liệu mẫu của Farm (`farmer1`, `engineer1`, `admin1`).

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC1-01 | WPC1-01 | Happy path | `./mvnw compile` ở `backend/` | Cả bốn module đạt | Claude (09/10/2026) | Đạt |
| TC1-02 | WPC1-02 | Ranh giới | Tìm import gói `dx-core` hoặc `core.` trong `app-farm` | Không có; chỉ import `dx-core-api` | Claude (09/10/2026) | Đạt |
| TC1-03 | WPC1-03 | Ranh giới | Tìm import gói Farm trong `dx-core-api` và `dx-core` | Không có | Claude (09/10/2026) | Đạt |
| TC1-04 | WPC1-04 | Happy path | Khởi động `dx-host` với `DXOS_APP=farm` | Lên đủ, `/actuator/health` UP | Claude (09/10/2026) | Đạt |
| TC1-05 | WPC1-04 | Âm tính | Khởi động với `DXOS_APP=abc` | Dừng khi khởi động, thông báo nêu giá trị hợp lệ | Claude (09/10/2026) | Đạt |
| TC1-06 | WPC1-05 | Dữ liệu | Dựng DB trống, so schema cuối với baseline | Cùng bảng, cột, ràng buộc, view | Claude (09/10/2026) | Đạt |
| TC1-07 | WPC1-06 | Tài liệu | Tìm `backend/DX-Farm` trong repo | Không còn | Claude (09/10/2026) | Đạt |
| TC1-08 | WPC1-03 | Hồi quy API | So `/v3/api-docs` với baseline ở C0 | Không khác ngoài thông tin đóng gói | Claude (09/10/2026) | Đạt |
| TC1-09 | WPC1-03 | Hồi quy | `./mvnw verify` ở `backend/` | 76 test (10 `dx-core`, 20 `app-farm`, 46 `dx-host`); baseline 80 trừ 4 test của `HouseholdMigrationTest`, `WorkflowMigrationTest` bỏ có chủ đích; không bỏ qua | Claude (09/10/2026) | Đạt |
| TC1-10 | WPC1-03 | Hồi quy | E2E hiện có chạy với `DXOS_APP=farm` | 10/10 bước đạt | Claude (09/10/2026) | Đạt |
| TC1-11 | WPC1-05 | Dữ liệu | `python data/scripts/generate_seed_sql.py --check` và unit test của generator | Đạt với đường dẫn `data.sql` mới | Claude (09/10/2026) | Đạt |
| TC1-12 | WPC1-05 | Dữ liệu | Khởi động `DXOS_APP=farm`, rồi xem seed | `data.sql` được nạp một lần; treatment rule đạo ôn và khô vằn có trong DB | Claude (09/10/2026) | Đạt |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động.
- [ ] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [ ] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409).
- [ ] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Thông báo trong app và Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ.
- [ ] Gửi lại yêu cầu với `Idempotency-Key` cũ không tạo bản ghi trùng.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với `DXOS_APP=farm`.

**Theo app**

- Farm:
  - [ ] Khóa thu hoạch theo PHI vẫn chặn đúng (HTTP 422).
  - [ ] Farmer chỉ thấy thửa và báo cáo của mình, chẩn đoán chưa duyệt bị ẩn.
  - [ ] FARMER dùng token web bị 403, dùng token mobile được.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC1-01 đến TC1-12 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng (09/10/2026, stack dựng sạch bằng `deploy/reset.sh --yes`):
  - `./mvnw clean verify`: 76 test đạt.
  - E2E `--require-rule`: 10/10 bước đạt; log backend không có lỗi.
  - `/v3/api-docs`: 50 path, không khác `openapi-baseline.json` ở C0 (đường dẫn, schema, info).
  - Schema: giống schema dựng từ migration cũ, chỉ khác bảng lịch sử Flyway thứ hai và quyền do seeder cấp lúc chạy.
  - Image backend với `DXOS_APP=abc`: dừng với thông báo `DXOS_APP must be one of [farm] but was 'abc'`.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
