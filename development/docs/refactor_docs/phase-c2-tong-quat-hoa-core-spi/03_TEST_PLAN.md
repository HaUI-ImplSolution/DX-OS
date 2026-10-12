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
| TC2-01 | WPC2-01 | Ranh giới | Tìm `Household` trong `dx-core-api`, `dx-core` | Không có | | |
| TC2-02 | WPC2-01 | Happy path | ADMIN tạo nông hộ qua `/households` (như trước) | Mã `H...`; thành viên được ghi; JSON như trước | | |
| TC2-03 | WPC2-01 | Phân quyền | FARMER gọi `/workspaces` và `/households` | 403 | | |
| TC2-04 | WPC2-01 | Hồi quy | FARMER chỉ thấy thửa của workspace mình | Đúng như trước | | |
| TC2-05 | WPC2-02 | Hồi quy | Duyệt, từ chối, gửi lại thửa và vụ | Trạng thái và lịch sử như trước | | |
| TC2-06 | WPC2-02 | Đồng thời | Hai người quyết định cùng một case | Một bên 409 | | |
| TC2-07 | WPC2-03 | Hồi quy | Danh sách và chi tiết yêu cầu, lọc, gán người phụ trách | JSON như trước | | |
| TC2-08 | WPC2-04 | Hồi quy | Duyệt thửa, nhắc thu hoạch | Thông báo được tạo, không trùng | | |
| TC2-09 | WPC2-05 | Phân quyền | Đối chiếu ma trận quyền Farm trước và sau | Giống nhau | | |
| TC2-10 | WPC2-06 | Hồi quy | Hỏi đáp tri thức bằng một câu mẫu | Cùng đoạn trích và nguồn | | |
| TC2-11 | WPC2-07 | Phân quyền | Vai Superset đọc view đã đăng ký và một bảng chưa đăng ký | Đọc được view; bảng khác bị từ chối | | |
| TC2-12 | WPC2-05 | Ranh giới | Tìm tên vai, endpoint, tên view của Farm trong `dx-core` | Không có | | |
| TC2-13 | WPC2-05 | Happy path | `GET /api/v1/app` với Farm | Manifest gồm id `farm`, vai, menu | | |
| TC2-14 | WPC2-02 | Ranh giới | Khởi động với app giả chỉ cài `AppModule` | Core lên được (thủ công) | | |
| TC2-15 | Tất cả | Hồi quy | `./mvnw verify`, E2E hiện có | Số test không ít hơn baseline, E2E 10/10 | | |

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
  - [ ] Hộp thư thông báo mobile hiển thị đủ loại thông báo cũ.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC2-01 đến TC2-15 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả tìm kiếm, diff OpenAPI, log test và E2E, ảnh chụp web và mobile lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
