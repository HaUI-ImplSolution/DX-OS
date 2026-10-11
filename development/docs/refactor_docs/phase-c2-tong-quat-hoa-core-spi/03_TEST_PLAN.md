# Kế hoạch kiểm thử: Phase C2

Quy ước dự án: mặc định chỉ compile và đọc diff.
Chạy test, dựng stack hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ; ghi ai chạy và khi nào ở cột "Người chạy".
Không thêm file test mới; test hiện có được cập nhật theo tên và kiểu mới.

## 1. Chiến lược

- Mức kiểm thử áp dụng: compile, tìm kiếm từ khóa trong core, so OpenAPI, test và E2E hiện có do người dùng chạy, thủ công trên web và mobile.
- Môi trường: clone sạch, `deploy/reset.sh`, `DXOS_APP=farm`.
- Dữ liệu và tài khoản test: `farmer1`, `engineer1`, `admin1` và dữ liệu seed demo.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC2-01 | WPC2-01 | Ranh giới | Tìm `Household` trong `dx-core-api`, `dx-core` | Không có | Claude (11/10/2026) | Đạt |
| TC2-02 | WPC2-01 | Happy path | ADMIN tạo nông hộ qua `/households` (như trước) | Mã `H...`; thành viên được ghi; JSON như trước | Claude (11/10/2026) | Đạt |
| TC2-03 | WPC2-01 | Phân quyền | FARMER gọi `/workspaces` và `/households` | 403 | Claude (11/10/2026) | Đạt |
| TC2-04 | WPC2-01 | Hồi quy | FARMER chỉ thấy thửa của workspace mình | Đúng như trước | Claude (11/10/2026) | Đạt (hộ thứ hai thấy 0 thửa, thửa của hộ khác trả 404, `/households` trả 403) |
| TC2-05 | WPC2-02 | Hồi quy | Duyệt, từ chối, gửi lại thửa và vụ | Trạng thái và lịch sử như trước | Claude (11/10/2026) | Đạt (E2E duyệt thửa, vụ, chẩn đoán; `WorkflowServiceIntegrationTest` kiểm từ chối và gửi lại) |
| TC2-06 | WPC2-02 | Đồng thời | Hai người quyết định cùng một case | Một bên 409 | Claude (11/10/2026) | Đạt (`ConcurrencyIntegrationTest`) |
| TC2-07 | WPC2-03 | Hồi quy | Danh sách và chi tiết yêu cầu, lọc, gán người phụ trách | JSON như trước | Claude (11/10/2026) | Đạt (danh sách, lọc theo `householdCode`, chi tiết kèm timeline; JSON cùng khóa như cũ) |
| TC2-08 | WPC2-04 | Hồi quy | Duyệt thửa, nhắc thu hoạch | Thông báo được tạo, không trùng | Claude (11/10/2026) | Đạt cho duyệt thửa, vụ, chẩn đoán, phun thuốc; nhắc thu hoạch chưa chạy |
| TC2-09 | WPC2-05 | Phân quyền | Đối chiếu ma trận quyền Farm trước và sau | Giống nhau | Claude (11/10/2026) | Đạt (`PermissionMatrixTest` 16/16) |
| TC2-10 | WPC2-06 | Hồi quy | Hỏi đáp tri thức bằng một câu mẫu | Cùng đoạn trích và nguồn | Claude (11/10/2026) | Đạt (2 đoạn trích, nguồn `SRC_MND_RICE_V3`, `SRC_IRRI_BLAST`) |
| TC2-11 | WPC2-07 | Phân quyền | Vai Superset đọc view đã đăng ký và một bảng chưa đăng ký | Đọc được view; bảng khác bị từ chối | Claude (11/10/2026) | Đạt (đọc `vw_*` và danh mục; `workspaces`, `parcels`, `notifications`, `workflow_case` bị từ chối) |
| TC2-12 | WPC2-05 | Ranh giới | Tìm tên vai, endpoint, tên view của Farm trong `dx-core` | Không có | Claude (11/10/2026) | Đạt |
| TC2-13 | WPC2-05 | Happy path | `GET /api/v1/app` với Farm | Manifest gồm id `farm`, vai, menu | Claude (11/10/2026) | Đạt |
| TC2-14 | WPC2-02 | Ranh giới | Khởi động với app giả chỉ cài `AppModule` | Core lên được (thủ công); kiểm ở C4 khi có `app-lab` | | |
| TC2-15 | Tất cả | Hồi quy | `./mvnw verify`, E2E hiện có | Số test không ít hơn baseline, E2E 10/10 | Claude (11/10/2026) | Đạt (`./mvnw clean verify` 80/80, E2E `--require-rule` 10/10) |

## 3. Hồi quy (không được hỏng)

Bằng chứng (11/10/2026): `./mvnw clean verify` 80/80, E2E `--require-rule` 10/10, kiểm tay trên Chrome và qua API với token thật (Claude).

**Dùng chung (mọi app)**

- [x] Đăng nhập SSO qua Keycloak vẫn hoạt động: web bằng `admin1` trên Chrome, Superset bằng nút Keycloak trên Chrome, client mobile `dxfarm-mobile` bằng `farmer1` qua luồng Authorization Code + PKCE (E2E). Ứng dụng di động Expo chưa chạy.
- [x] Người dùng chỉ thấy dữ liệu thuộc workspace của mình: hộ thứ hai thấy 0 thửa, thửa của hộ khác trả 404, `/households` trả 403.
- [x] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409): lịch sử `SUBMITTED`, `ASSIGNED`, `APPROVED` ghi nhận khi duyệt trên web; `ConcurrencyIntegrationTest` và `PermissionMatrixTest.reportLockConflictReturns409` đạt.
- [x] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối: link ký trả 200, thiếu chữ ký hoặc chữ ký sai trả 403. Hết hạn do chữ ký của MinIO đảm nhận, chưa chờ hết 15 phút để thử.
- [~] Thông báo trong app và Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ: thông báo trong app đạt (`PARCEL_APPROVED`, `TREATMENT_CONFIRMED`, ...); webhook trong `deploy/.env` trả HTTP 400 vì Mattermost vừa dựng lại nên khóa cũ không còn, nghiệp vụ vẫn chạy bình thường. Chưa kiểm việc đăng bài thành công, cần tạo lại webhook theo `chat/README.md`.
- [x] Gửi lại yêu cầu với `Idempotency-Key` cũ không tạo bản ghi trùng: bước "upload is idempotent" của E2E và `ReportDiagnosisIntegrationTest.linkedRetakeAndIdempotentReplayStayInSameChain`.
- [x] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với `DXOS_APP=farm`: dựng bằng `reset.sh --yes`, 11 service healthy.

**Theo app**

- Farm:
  - [x] Khóa thu hoạch theo PHI vẫn chặn đúng (HTTP 422): bước cuối của E2E kiểm `POKA_YOKE_VIETGAP_VIOLATION` và `harvestLockedUntil`.
  - [x] Farmer chỉ thấy thửa và báo cáo của mình, chẩn đoán chưa duyệt bị ẩn: bước "diagnosis waits for the engineer" của E2E kiểm `diseaseCode` là `null` trước khi duyệt.
  - [x] FARMER dùng token web bị 403, dùng token mobile được: token client `dxfarm-frontend` trả 403, token `dxfarm-mobile` trả 200.
  - [x] Hộp thư thông báo mobile hiển thị đủ loại thông báo cũ: người dùng chạy ứng dụng, thấy thông báo và đánh dấu đã đọc hết (11/10/2026).

## 4. Tiêu chí đạt của phase (exit criteria)

- TC2-01 đến TC2-15 đạt; TC2-14 chuyển sang C4.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng (11/10/2026, stack dựng sạch bằng `reset.sh --yes`, `DXOS_APP=farm`):
  - `./mvnw clean verify`: 80 test đạt (13 `dx-core`, 21 `app-farm`, 46 `dx-host`).
  - E2E `--require-rule`: 10/10 bước đạt.
  - `/v3/api-docs`: 54 path, thêm `/api/v1/app`, `/api/v1/workspaces`, `/api/v1/workspaces/{code}/account`, `/api/v1/workspaces/{code}/members`; không path nào của Farm bị xóa hay đổi schema. Khác biệt còn lại: hậu tố `operationId` của các thao tác trùng tên, mô tả `workflow-cases` ghi "Staff only", tham số `status` của `workflow-cases` là chuỗi.
  - `superset_ro` đọc được `vw_parcels_approved`, `diseases`, `pesticides`, `rice_varieties`; bị từ chối ở `workspaces`, `workspace_members`, `notifications`, `workflow_case`, `parcels`.
  - Service account của backend đọc được vai nhưng không tạo được vai (HTTP 403): vai thiếu chỉ được ghi log, không dừng backend.
  - Web (Chrome, vai ADMIN): menu, trang Yêu cầu (lọc trạng thái, chi tiết, lịch sử), Nông hộ, Hàng đợi, Tài liệu (hỏi đáp có nguồn) hiển thị đúng và không có lỗi console; nhận việc rồi duyệt một thửa trên giao diện thì thông báo `PARCEL_APPROVED` tới nông dân và lịch sử ghi `SUBMITTED`, `ASSIGNED`, `APPROVED`.
  - Mobile (người dùng chạy 11/10/2026): đăng nhập `farmer1`, thông báo hiển thị và đánh dấu đã đọc, các màn còn lại hoạt động.
  - Không kiểm trong phase: TC2-14 (chuyển sang C4, TC4-31), kích riêng job nhắc thu hoạch.

- Bằng chứng: kết quả tìm kiếm, diff OpenAPI, log test và E2E, ảnh chụp web và mobile lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
