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
| TC6-01 | WPC6-01 | Chuyển app | `switch-app.sh lab` rồi `switch-app.sh farm` | Mỗi lần backend lên đúng app; trạng thái hệ thống đúng tên | Claude, 13/10/2026 | Đạt: `switch-app.sh lab` rồi `switch-app.sh farm`; mỗi lần script đợi `system/status` báo đúng `app.id`; `deploy/.env` đổi theo; gateway nhận file mới không cần khởi động lại |
| TC6-02 | WPC6-02 | Ranh giới | Gọi route của Farm khi chạy Lab và ngược lại | Không tồn tại (404) | Claude, 13/10/2026 | Đạt: Lab chạy thì `/parcels` 404 `ERR_NOT_FOUND`, Farm chạy thì `/tickets`, `/rooms`, `/lab-stats/summary` 404; file gateway của Farm không có route Lab và ngược lại; Lab giới hạn `POST /tickets` ở 30 lần mỗi phút (429) |
| TC6-03 | WPC6-03 | E2E | E2E `farm` | Tất cả bước đạt, bằng baseline | Claude, 13/10/2026 | Đạt: 10/10 bước với `--require-rule`, trước và sau khi chuyển từ Lab về Farm |
| TC6-04 | WPC6-03 | E2E | E2E `lab` (ít nhất 10 bước phủ ticket, mượn thiết bị, đặt phòng, hỏi đáp, thống kê) | Tất cả bước đạt | Claude, 13/10/2026 | Đạt: 16/16 bước với `--require-knowledge`, chạy lại lần hai cũng đạt |
| TC6-05 | WPC6-04 | CI | Mở PR thử | Hai job E2E xanh | Người dùng, PR của nhánh, 13/10/2026 | Đạt: các job của workflow `E2E` (`farm` và `lab`) và các workflow còn lại của PR đều xanh, theo xác nhận của người dùng |
| TC6-06 | WPC6-05 | Happy path | Mở Superset khi chạy từng app | Farm: hiện dashboard Farm; Lab: đọc được view Lab | Claude, 13/10/2026 | Đạt: Farm hiện dashboard "DX-Farm — Bản đồ & Trạng thái thửa ruộng" có dữ liệu (bản đồ, vụ mùa, báo cáo bệnh); Lab hiện kết nối `DX-Lab` và 5 dataset `vw_lab_*`; role `superset_ro` đọc được `vw_lab_tickets` (13 dòng) và bị từ chối `tickets` |
| TC6-07 | WPC6-01 | Chuyển app | Đổi app không `--reset` | Không lỗi migration; bảng app cũ còn nhưng không dùng | Claude, 13/10/2026 | Đạt: farm → lab → farm trên cùng DB, không lỗi migration; bảng Lab còn nguyên khi chạy Farm. `switch-app.sh lab --reset --yes` dựng sạch: Lab không có bảng của Farm (`parcels` không tồn tại), seed và E2E 16/16 |
| TC6-08 | WPC6-01 | Chuyển app | Đổi `DXOS_APP` mà không build lại image web | Web hiện đúng app | Claude, 13/10/2026 | Đạt: container `frontend` không bị tạo lại khi chuyển app; trình duyệt hiện "DX-Farm" (menu Farm) ở chế độ Farm và "DX-Lab HaUI" (tổng quan có dữ liệu seed) ở chế độ Lab |
| TC6-09 | WPC6-03 | Dữ liệu | Seed hai lần liên tiếp | Không tạo trùng | Claude, 13/10/2026 | Đạt: `seed_demo.py` chạy hai lần cho mỗi app; Lab giữ 12 yêu cầu, 5 phiếu, 5 lịch, 5 phòng, 7 thiết bị; Farm báo đã có dữ liệu |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [x] Đăng nhập SSO qua Keycloak hoạt động cho cả hai app: web (`dxos-web`) và Superset (`admin1`) đăng nhập được trên trình duyệt ở chế độ Farm và Lab.
- [x] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [x] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409).
- [x] Link ảnh có hạn hoạt động (200, `X-Amz-Expires=900`); sửa chữ ký hoặc bỏ query thì bị từ chối (403).
- [x] Giới hạn tốc độ ở gateway vẫn áp dụng cho thao tác tốn tài nguyên (đã kiểm 429 ở route upload của Farm và route tạo ticket của Lab).
- [x] Dựng sạch lên đủ dịch vụ healthy với `farm` (`reset.sh`) và với `lab` (`switch-app.sh lab --reset --yes`).

**Theo app**

- Farm:
  - [x] Khóa thu hoạch theo PHI vẫn chặn đúng (HTTP 422).
  - [x] FARMER dùng token web bị 403 (`/parcels`, `/app`), dùng token mobile được (200).
- Lab:
  - [x] STUDENT không thấy ticket của người khác.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC6-01 đến TC6-09 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log CI của PR (workflow `E2E` hai job `farm` và `lab`, các workflow còn lại); `mvnw clean verify` (67 test) và `npx tsc --noEmit` đạt; stack dựng bằng `reset.sh` rồi chuyển `farm` → `lab` → `farm` bằng `switch-app.sh`, seed và E2E của từng app qua gateway `localhost:9080` (13/10/2026).
- Hành vi đã ghi nhận:
  - Đường dẫn không có controller (route của app khác) trả 404 `ERR_NOT_FOUND`.
  - Giới hạn tốc độ tính theo địa chỉ, 30 lần mỗi phút cho tạo ticket và upload; E2E chạy ngay sau khi dùng hết hạn mức thì các bước tạo ticket nhận 429 và chạy lại được sau một phút.
  - `DXOS_APP` không hợp lệ làm backend dừng với thông báo `DXOS_APP must be one of [farm, lab] but was 'abc'`; `apisix-config` dừng với thông báo nêu thiếu file gateway.
  - Sau khi dựng sạch, trình duyệt giữ token cũ nên web báo "Không tải được ứng dụng, lỗi HTTP 401" tới khi bấm Đăng xuất rồi đăng nhập lại; Superset cần bấm "Sign in with Keycloak" lại.
- Vấn đề còn mở: `POST /api/v1/tickets` và `POST /api/v1/crop-seasons/{id}/disease-reports` không có `Content-Type` multipart trả 500 (`HttpMediaTypeNotSupportedException` rơi vào xử lý lỗi chung), không ảnh hưởng client của web.
