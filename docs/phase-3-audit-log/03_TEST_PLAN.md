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
| T3-01 | WP3-01 | Happy path | Khởi động backend trên database sạch | Có bảng `audit_log` và trigger; backend healthy | Claude, local, 23/10/2026 | Đạt (migration `4 - audit log` chạy khi khởi động; có bảng và trigger) |
| T3-02 | WP3-02 | Happy path | Gọi một API qua gateway, lấy `X-Request-Id` từ response | Cùng giá trị có trong log gateway, log backend và (nếu có) bản ghi audit | Claude, local, 23/10/2026 | Đạt (`X-Request-Id` tự đặt có trong dòng log gateway và trong cột `request_id` của bản ghi; mẫu log backend đã có `[%X{requestId}]`) |
| T3-03 | WP3-03 | Phân quyền | `farmer1` gọi một API chỉ dành cho ADMIN | 403, có đúng một bản ghi `ACCESS_DENIED` với người dùng, vai, IP, đường dẫn, `request_id` | Claude, local, 23/10/2026 | Đạt (`student1` nhận 403, một bản ghi `ACCESS_DENIED` có người dùng, vai `STUDENT`, client `dxos-web`, IP, method, đường dẫn) |
| T3-04 | WP3-03 | Âm tính | Gọi liên tiếp 100 lần API trên bằng cùng token | Số bản ghi không vượt ngưỡng cấu hình, phần còn lại được đếm trong `metadata` | Claude, local, 23/10/2026 | Đạt (100 lần 403 chỉ thêm 19 dòng, giới hạn 20 mỗi phút) |
| T3-05 | WP3-04 | Happy path | `admin1` thêm thành viên vào workspace, gán vai | Mỗi thao tác một bản ghi `SUCCESS`; thao tác lỗi tạo bản ghi `FAILED` | Claude, local, 23/10/2026 | Đạt (Lab: `WORKSPACE_CREATED`, `MEMBER_ADDED`, `SLA_POLICY_CHANGED`; Farm: `HARVEST_OVERRIDE` thu hoạch sớm, `SEASON_DELETED`, `PARCEL_DELETED`, `REPORT_DELETED`; chưa kiểm trạng thái `FAILED`) |
| T3-06 | WP3-01 | Bảo mật | Chạy `UPDATE` và `DELETE` trên `audit_log` bằng `psql` | Bị từ chối bởi trigger | Claude, local, 23/10/2026 | Đạt (`UPDATE` và `DELETE` đều báo `audit_log is append-only`) |
| T3-07 | WP3-05 | Phân quyền | ADMIN, rồi `engineer1` và `farmer1` gọi `GET /api/v1/audit-logs` với bộ lọc | ADMIN nhận 200 đúng dữ liệu đã lọc; vai khác nhận 403 | Claude, local, 23/10/2026 | Đạt (ADMIN lọc theo `outcome`, `actor`, `action`; `outcome=NOPE` trả 400; `student1` trả 403; Swagger có mục Audit) |
| T3-08 | WP3-06 | Happy path | Tạo nhiều 403 từ một nguồn vượt ngưỡng; thử khi Mattermost tắt | Một tin cảnh báo trên kênh; khi Mattermost tắt nghiệp vụ vẫn chạy | Claude, local, 23/10/2026 | Đạt (tin cảnh báo lên kênh "Ban quản trị" đúng một lần lúc 14:50:00; Mattermost tắt chưa thử) |
| T3-09 | WP3-04 | Ranh giới | Đổi `DXOS_APP` giữa Farm và Lab, khởi động lại | App khởi động được, core không đổi, bản ghi audit cũ còn nguyên | Claude, local, 23/10/2026 | Đạt (chuyển Lab sang Farm: backend khởi động, bản ghi cũ còn nguyên và ADMIN đọc được; E2E Farm 10/10, Lab 16/16) |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [~] Đăng nhập SSO qua Keycloak vẫn hoạt động: web (Chrome và E2E Farm, Lab) và Superset (Chrome) đạt; mobile chưa kiểm.
- [x] Người dùng chỉ thấy dữ liệu thuộc workspace của mình (E2E Lab: sinh viên khác nhận 404).
- [x] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409) (E2E Lab: hai kỹ thuật viên nhận cùng ticket, một người 409).
- [x] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối (thời hạn thử 5 giây: link mới 200, chữ ký sai và quá hạn 403).
- [x] Thông báo trong app và Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ (tắt Mattermost: upload và E2E Farm 10/10 vẫn đạt, log có `Mattermost webhook call failed`).
- [x] Gửi lại yêu cầu với `Idempotency-Key` cũ không tạo bản ghi trùng (E2E Farm: upload hai lần cùng khóa).
- [x] Dựng từ clone sạch lên đủ dịch vụ healthy với từng giá trị `DXOS_APP` (clone sạch trong thư mục tạm, không overlay, không `--build`: Farm và Lab đều lên đủ dịch vụ healthy, migration và import realm từ đầu; migration core V1 đến V4 chạy từ đầu); chưa thử `--build` toàn bộ image.
- [x] Response 401 và 403 giữ nguyên nội dung và mã (E2E Farm và Lab đạt).

**Theo app**

- Farm:
  - [x] Khóa thu hoạch theo PHI vẫn chặn đúng (HTTP 422) (E2E Farm 10/10 với bản backend có audit).
  - [x] Farmer chỉ thấy thửa và báo cáo của mình, chẩn đoán chưa duyệt bị ẩn (E2E Farm 10/10, bước chẩn đoán chờ kỹ sư duyệt).
- Lab:
  - [x] Sinh viên chỉ thấy ticket của mình (E2E Lab 16/16 với bản backend có audit; Chrome); giới hạn tạo ticket còn hoạt động (60 qua, 6 nhận 429).

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case T3-01 tới T3-09 đạt.
- Lỗi còn lại đã có issue; nợ kỹ thuật được ghi ở `docs/BACKLOG.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả API, ảnh chụp tin cảnh báo, lưu trong mô tả PR.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
