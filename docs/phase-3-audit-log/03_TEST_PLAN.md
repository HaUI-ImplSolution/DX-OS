# Kế hoạch kiểm thử: Phase 3

Kiểm tra phase đã đạt tiêu chuẩn và chạy đúng kỳ vọng chưa.
Quy ước dự án: mặc định chỉ compile, đọc diff.
Chạy test, khởi động app hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ, ghi rõ ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: thủ công trên stack thật, bảo mật, phân quyền.
- Môi trường: clone sạch + compose + seed demo.
- Chạy lại với `DXOS_APP=farm` và `DXOS_APP=lab`.
- Dữ liệu và tài khoản test: `farmer1`, `engineer1`, `admin1` (Farm); tài khoản sinh viên, kỹ thuật viên và quản trị của Lab.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T3-01 | WP3-01 | Happy path | Khởi động backend trên database sạch | Có bảng `audit_log` và trigger; backend healthy | | |
| T3-02 | WP3-02 | Happy path | Gọi một API qua gateway, lấy `X-Request-Id` từ response | Cùng giá trị có trong log gateway, log backend và (nếu có) bản ghi audit | | |
| T3-03 | WP3-03 | Phân quyền | `farmer1` gọi một API chỉ dành cho ADMIN | 403, có đúng một bản ghi `ACCESS_DENIED` với người dùng, vai, IP, đường dẫn, `request_id` | | |
| T3-04 | WP3-03 | Âm tính | Gọi liên tiếp 100 lần API trên bằng cùng token | Số bản ghi không vượt ngưỡng cấu hình, phần còn lại được đếm trong `metadata` | | |
| T3-05 | WP3-04 | Happy path | `admin1` thêm thành viên vào workspace, gán vai | Mỗi thao tác một bản ghi `SUCCESS`; thao tác lỗi tạo bản ghi `FAILED` | | |
| T3-06 | WP3-01 | Bảo mật | Chạy `UPDATE` và `DELETE` trên `audit_log` bằng `psql` | Bị từ chối bởi trigger | | |
| T3-07 | WP3-05 | Phân quyền | ADMIN, rồi `engineer1` và `farmer1` gọi `GET /api/v1/audit-logs` với bộ lọc | ADMIN nhận 200 đúng dữ liệu đã lọc; vai khác nhận 403 | | |
| T3-08 | WP3-06 | Happy path | Tạo nhiều 403 từ một nguồn vượt ngưỡng; thử khi Mattermost tắt | Một tin cảnh báo trên kênh; khi Mattermost tắt nghiệp vụ vẫn chạy | | |
| T3-09 | WP3-04 | Ranh giới | Đổi `DXOS_APP` giữa Farm và Lab, khởi động lại | App khởi động được, core không đổi, bản ghi audit cũ còn nguyên | | |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động.
- [ ] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [ ] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409).
- [ ] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Thông báo trong app và Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ.
- [ ] Gửi lại yêu cầu với `Idempotency-Key` cũ không tạo bản ghi trùng.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với từng giá trị `DXOS_APP`.
- [ ] Response 401 và 403 giữ nguyên nội dung và mã.

**Theo app**

- Farm:
  - [ ] Khóa thu hoạch theo PHI vẫn chặn đúng (HTTP 422).
  - [ ] Farmer chỉ thấy thửa và báo cáo của mình, chẩn đoán chưa duyệt bị ẩn.
- Lab:
  - [ ] Sinh viên chỉ thấy ticket của mình; giới hạn tạo ticket vẫn hoạt động.

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case T3-01 tới T3-09 đạt.
- Lỗi còn lại đã có issue; nợ kỹ thuật được ghi ở `docs/BACKLOG.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả API, ảnh chụp tin cảnh báo, lưu trong mô tả PR.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
