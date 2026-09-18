# Checklist: Phase C6

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC6-{xx}-{lớp}-{nn}`.

## WPC6-01: Truyền `DXOS_APP` và script chuyển app

Kết quả đầu ra: một lệnh chuyển app; hệ thống báo app đang chạy.

**Backend**

- [ ] WPC6-01-BE-01 [host] Trạng thái hệ thống trả tên app đang chạy.

**Infra, deploy, CI**

- [ ] WPC6-01-INFRA-01 `docker-compose.yml`: truyền `DXOS_APP` cho backend, seed, Superset.
- [ ] WPC6-01-INFRA-02 `deploy/.env.example`: `DXOS_APP=farm` kèm chú thích giá trị hợp lệ.
- [ ] WPC6-01-INFRA-03 `deploy/switch-app.sh`: ghi biến, restart backend và job liên quan, tùy chọn `--reset`.
- [ ] WPC6-01-INFRA-04 `portal/index.html`: hiển thị tên app đang chạy.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC6-01-TEST-01 Chuyển qua lại hai app (TC6-01).

## WPC6-02: Gateway theo app

Kết quả đầu ra: mỗi app có đúng bộ route và giới hạn riêng.

**Infra, deploy, CI**

- [ ] WPC6-02-INFRA-01 Tách `deploy/apisix/` thành phần chung và phần theo app.
- [ ] WPC6-02-INFRA-02 Phần Farm: `disease-reports`, `diagnosis/ai`, `knowledge/ask`.
- [ ] WPC6-02-INFRA-03 Phần Lab: giới hạn tạo ticket, tải tệp đính kèm, đặt phòng, hỏi đáp, gợi ý phân loại.
- [ ] WPC6-02-INFRA-04 Compose mount file đã ghép theo `DXOS_APP`.
- [ ] WPC6-02-INFRA-05 Route ảnh dùng tên bucket trung tính (`/dxos-uploads/*`).

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC6-02-TEST-01 Route của app khác không tồn tại (TC6-02).

## WPC6-03: Seed và E2E theo app

Kết quả đầu ra: seed và E2E chạy được cho cả hai app.

**Data, seed, migration**

- [ ] WPC6-03-DATA-01 [host] `seed_demo.py` tách phần chung; `seed_farm`, `seed_lab`.
- [ ] WPC6-03-DATA-02 [lab] Tài khoản `student1`, `student2`, `lecturer1`, `tech1`, `tech2`, `admin1`, vai và workspace lớp, bộ môn.
- [ ] WPC6-03-DATA-03 [lab] Nạp dữ liệu mẫu của C4 qua API: danh mục, phòng, thiết bị, ticket ở mọi trạng thái, lịch đặt phòng, phiếu mượn, ngày tương đối.
- [ ] WPC6-03-DATA-04 [lab] Seed chạy hai lần không tạo trùng.

**Infra, deploy, CI**

- [ ] WPC6-03-INFRA-01 `e2e.py` tách phần chung và kịch bản từng app.
- [ ] WPC6-03-INFRA-02 Kịch bản Lab tối thiểu 10 bước: gợi ý và báo sự cố, xử lý, xác nhận kèm đánh giá, 409 khi nhận đồng thời, mượn và trả báo hỏng, đặt phòng và chồng giờ bị từ chối, hỏi đáp, thống kê, 403.
- [ ] WPC6-03-INFRA-03 `make_ci_env.py`, `docker-compose.e2e.yml` nhận `DXOS_APP`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC6-03-TEST-01 E2E Farm và Lab (TC6-03, TC6-04).

## WPC6-04: CI hai app

Kết quả đầu ra: CI xanh cho cả hai app.

**Infra, deploy, CI**

- [ ] WPC6-04-INFRA-01 `e2e.yml`: ma trận `DXOS_APP`.
- [ ] WPC6-04-INFRA-02 `ci.yml`: build toàn bộ module, đường dẫn kích hoạt gồm module mới.
- [ ] WPC6-04-INFRA-03 `ci-frontend.yml`: kiểm `tsc` cho vỏ và hai module app.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC6-04-TEST-01 CI xanh cho cả hai app (TC6-05).

## WPC6-05: Dashboard Superset và portal theo app

Kết quả đầu ra: Superset hiện dashboard của app đang chạy.

**Infra, deploy, CI**

- [ ] WPC6-05-INFRA-01 `bi/bootstrap_datasources.py`: Farm nạp zip dashboard như cũ; Lab chỉ khai báo dataset.
- [ ] WPC6-05-INFRA-02 Compose mount zip của Farm khi `DXOS_APP=farm`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC6-05-TEST-01 Dashboard đúng app (TC6-06).

## Đóng phase

- [ ] `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web) và đã đọc lại diff.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch với từng giá trị `DXOS_APP`.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu và ảnh test không còn dùng.
- [ ] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
