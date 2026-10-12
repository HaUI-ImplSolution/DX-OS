# Kế hoạch kiểm thử: Phase C6

Quy ước dự án: mặc định chỉ compile và đọc diff.
Chạy stack, seed, E2E hoặc CI chỉ khi người dùng yêu cầu rõ; ghi ai chạy và khi nào ở cột "Người chạy".
Không thêm file test mới ngoài kịch bản E2E của app đã nêu ở WPC6-03.

## 1. Chiến lược

- Mức kiểm thử áp dụng: E2E cho từng app, kiểm cấu hình gateway, thủ công trên web và Superset, CI.
- Môi trường: clone sạch, `deploy/reset.sh`, lần lượt `farm` và `lab`.
- Dữ liệu và tài khoản test: Farm (`farmer1`, `engineer1`, `admin1`), Lab (`student1`, `student2`, `lecturer1`, `tech1`, `tech2`, `admin1`).

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC6-01 | WPC6-01 | Chuyển app | `switch-app.sh lab` rồi `switch-app.sh farm` | Mỗi lần backend lên đúng app; trạng thái hệ thống đúng tên | | |
| TC6-02 | WPC6-02 | Ranh giới | Gọi route của Farm khi chạy Lab và ngược lại | Không tồn tại (404) | | |
| TC6-03 | WPC6-03 | E2E | E2E `farm` | Tất cả bước đạt, bằng baseline | | |
| TC6-04 | WPC6-03 | E2E | E2E `lab` (ít nhất 10 bước phủ ticket, mượn thiết bị, đặt phòng, hỏi đáp, thống kê) | Tất cả bước đạt | | |
| TC6-05 | WPC6-04 | CI | Mở PR thử | Hai job E2E xanh | | |
| TC6-06 | WPC6-05 | Happy path | Mở Superset khi chạy từng app | Farm: dashboard như trước; Lab: đọc được view Lab | | |
| TC6-07 | WPC6-01 | Chuyển app | Đổi app không `--reset` | Không lỗi migration; bảng app cũ còn nhưng không dùng | | |
| TC6-08 | WPC6-01 | Chuyển app | Đổi `DXOS_APP` mà không build lại image web | Web hiện đúng app | | |
| TC6-09 | WPC6-03 | Dữ liệu | Seed hai lần liên tiếp | Không tạo trùng | | |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] Đăng nhập SSO qua Keycloak (web, Superset) vẫn hoạt động.
- [ ] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [ ] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409).
- [ ] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Giới hạn tốc độ ở gateway vẫn áp dụng cho thao tác tốn tài nguyên.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với từng giá trị `DXOS_APP`.

**Theo app**

- Farm:
  - [ ] Khóa thu hoạch theo PHI vẫn chặn đúng (HTTP 422).
  - [ ] FARMER dùng token web bị 403, dùng token mobile được.
- Lab:
  - [ ] STUDENT không thấy ticket của người khác.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC6-01 đến TC6-09 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log CI, ảnh chụp hai chế độ, kết quả script lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
