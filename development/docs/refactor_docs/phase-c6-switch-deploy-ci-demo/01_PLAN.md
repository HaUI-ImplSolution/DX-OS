# Plan thực hiện: Phase C6

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WPC6-01 → WPC6-02 → WPC6-03 → WPC6-04.
WPC6-05 làm song song với WPC6-03.

## WPC6-01: Truyền `DXOS_APP` và script chuyển app

- Mục tham chiếu: RD2.
- Mục tiêu: một biến đổi toàn hệ thống.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Chọn app | Cố định Farm | `DXOS_APP` trong `deploy/.env` truyền vào backend, seed, Superset |
| Đổi app | Không có | `./deploy/switch-app.sh {farm|lab} [--reset]` |
| Kiểm tra app đang chạy | Không có | Trạng thái hệ thống và portal hiển thị tên app |

**Hướng làm**

- Script ghi `DXOS_APP`, restart backend và job liên quan; `--reset` gọi `deploy/reset.sh`.
- Core/App: script không chứa logic app; chỉ đổi biến.

**Phạm vi ảnh hưởng**

- Module: `dx-host`, hạ tầng.
- File chính: `deploy/docker-compose.yml`, `deploy/.env.example`, `deploy/switch-app.sh`, `portal/index.html`.
- Endpoint, bảng DB, biến môi trường: `DXOS_APP`.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: mặc định `farm`.

**Kết quả đầu ra**

- Một lệnh chuyển app; hệ thống báo app đang chạy.

**Phụ thuộc, lưu ý**: Q2.

## WPC6-02: Gateway theo app

- Mục tham chiếu: đề thi HaUI mục 4.1 (API Gateway).
- Mục tiêu: gateway chỉ có route và giới hạn của app đang chạy.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Route của Farm | Trong `apisix.yaml` chung (`disease-reports`, `diagnosis/ai`, `knowledge/ask`) | `disease-reports` và `diagnosis/ai` trong `apps/farm.yaml`; `knowledge/ask` ở phần chung vì cả hai app có route này |
| Route của Lab | Không có | Giới hạn tốc độ cho tạo ticket, tải tệp đính kèm, đặt phòng, hỏi đáp, gợi ý phân loại |
| Route chung | Lẫn trong một file | `common.yaml`: Keycloak, portal, ảnh, trạng thái hệ thống, hỏi đáp tri thức, `/api/*` |

**Hướng làm**

- Service `apisix-config` ghép `common.yaml` với `apps/<DXOS_APP>.yaml` thành `deploy/data/apisix/apisix.yaml`, gateway mount file đó; app không có file thì compose dừng.
- Core/App: phần chung không nhắc `crop`, `disease`, `ticket`.

**Phạm vi ảnh hưởng**

- Module: hạ tầng.
- File chính: `deploy/apisix/*`, compose.
- Endpoint, bảng DB, biến môi trường: giới hạn tốc độ theo app.
- Role và client bị ảnh hưởng: mọi client.
- Tương thích ngược và dữ liệu cũ: Farm giữ giới hạn 30 lần mỗi phút cho upload ảnh và chẩn đoán AI.

**Kết quả đầu ra**

- Mỗi app có đúng bộ route và giới hạn riêng.

**Phụ thuộc, lưu ý**: Q1.

## WPC6-03: Seed và E2E theo app

- Mục tham chiếu: đề thi HaUI mục 7, 8.
- Mục tiêu: chạy một lệnh có dữ liệu mẫu và kiểm đầu-cuối cho từng app.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Seed | `seed_demo.py` tạo hộ, thửa, vụ, báo cáo | Phần chung (client, tài khoản) và `seed_farm`, `seed_lab`; chọn theo `DXOS_APP` |
| E2E | `e2e.py` 10 bước của Farm | Phần chung và kịch bản theo app |
| Tài khoản mẫu | `farmer1`, `engineer1`, `admin1` | Thêm `student1`, `student2`, `lecturer1`, `tech1`, `tech2`, `admin1` cho Lab |
| Dữ liệu mẫu Lab | Không có | Nạp bằng API: lớp và bộ môn, phòng, thiết bị, ticket ở mọi trạng thái, lịch đặt phòng, phiếu mượn; ngày đặt phòng tính theo ngày chạy, thời điểm tạo ticket là lúc nạp (`workflow_event` chỉ thêm, không sửa) |

**Hướng làm**

- Kịch bản Lab (tối thiểu 10 bước): sinh viên nhận gợi ý rồi báo sự cố kèm ảnh; kỹ thuật viên nhận, trao đổi, xử lý; sinh viên xác nhận và đánh giá; hai kỹ thuật viên nhận cùng ticket (409); mượn thiết bị được duyệt rồi trả báo hỏng; đặt phòng được duyệt và đặt chồng giờ bị từ chối; hỏi đáp quy chế có nguồn; thống kê khớp dữ liệu; vai không đủ quyền bị 403.
- Seed nạp qua API nên đi qua poka-yoke; chạy hai lần không tạo trùng.
- Core/App: client và tiện ích chung không nhắc app.

**Phạm vi ảnh hưởng**

- Module: `deploy/demo`.
- File chính: `seed_demo.py`, `seed_farm.py`, `seed_lab.py`, `e2e.py`, `e2e_farm.py`, `e2e_lab.py`, `dxos_client.py`, `make_ci_env.py`, `docker-compose.e2e.yml`.
- Endpoint, bảng DB, biến môi trường: tham số `--app` hoặc đọc `DXOS_APP`.
- Role và client bị ảnh hưởng: tài khoản mẫu.
- Tương thích ngược và dữ liệu cũ: seed Farm giữ nội dung.

**Kết quả đầu ra**

- Seed và E2E chạy được cho cả hai app.

**Phụ thuộc, lưu ý**: cần C4, C5.

## WPC6-04: CI hai app

- Mục tham chiếu: đề thi HaUI mục 12.
- Mục tiêu: PR nào cũng kiểm được hai app.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Workflow E2E | Một lần, Farm | Ma trận `DXOS_APP: [farm, lab]` |
| Backend CI | Một module | Build toàn bộ module |
| Đường dẫn kích hoạt | Có `backend/**` | Gồm cả module mới |

**Hướng làm**

- Hai job E2E độc lập; cùng cache build.
- Core/App: workflow chung, tham số theo app.

**Phạm vi ảnh hưởng**

- Module: `.github/workflows`.
- File chính: `e2e.yml`, `ci.yml`.
- Endpoint, bảng DB, biến môi trường: `DXOS_APP` ở workflow.

**Kết quả đầu ra**

- CI xanh cho cả hai app.

**Phụ thuộc, lưu ý**: cần WPC6-03.

## WPC6-05: Dashboard Superset và portal theo app

- Mục tham chiếu: đề thi HaUI mục 4.2 (D - Data), 9.
- Mục tiêu: Superset và portal hiển thị đúng app đang chạy.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Dashboard | `farm_dashboard_export.zip` (sau C3) | Farm giữ dashboard hiện có; Lab chỉ cấp quyền đọc view Lab, dashboard Superset của Lab để backlog (trang thống kê trong web thay thế) |
| Portal | Mô tả hệ thống Farm | Mô tả theo manifest hoặc nội dung chung |

**Hướng làm**

- `bi/bootstrap_datasources.py` đọc `DXOS_APP`: Farm nạp bundle dashboard; Lab chỉ khai báo dataset.
- Core/App: tên file dashboard do app đặt.

**Phạm vi ảnh hưởng**

- Module: `bi/`, `portal/`.
- File chính: `bootstrap_datasources.py`, compose, `portal/index.html`.
- Endpoint, bảng DB, biến môi trường: `DXOS_APP`.

**Kết quả đầu ra**

- Farm có dashboard của Farm; Lab đọc được view Lab.

**Phụ thuộc, lưu ý**: cần C4 WPC4-05.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Biến môi trường | `DXOS_APP` | Truyền tới backend, seed, Superset | Không |
| Cấu hình | `deploy/apisix/*` | Phần chung và phần theo app | Không |
| Script | `deploy/switch-app.sh`, `seed_demo.py`, `e2e.py` | Mới hoặc có tham số app | Không |
| Bảng DB | Không | Không | Không |

## Dữ liệu, migration và rollback

- Migration: không.
- Dữ liệu cũ: đổi app giữ bảng của app cũ; `--reset` dọn.
- Rollback: quay lại nhánh trước phase.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Mỗi lần khởi động một app (RD2) | Đơn giản, dễ chứng minh |
| D2 | Phần cấu hình theo app nằm ở thư mục riêng của app | Phần chung không biết app |
