# Plan thực hiện: ứng dụng mẫu cửa hàng điện máy

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code; đợt làm này không đổi API nên bước Swagger bỏ.

## Thứ tự và phụ thuộc

WP-01 → WP-02 → WP-03 → WP-04.
WP-02 làm song song được với WP-01 khi mô hình đã chốt tên vai và trường.
WP-03 cần cả hai; việc Q1 (`extra_hosts` của backend) làm trước WP-03.

## WP-01: Mô hình, script cài đặt và compose

- Mục tham chiếu: issue #343, `docs/dynamic-core/UNG_DUNG_MAU.md` mục 2.
- Mục tiêu: người vận hành áp dụng được hai quy trình và nối ứng dụng bằng các lệnh có sẵn.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Cửa hàng cần quy trình đổi trả | Ứng dụng không có | `retail_return` dùng nguyên: `CUSTOMER` là client của ứng dụng, `STORE_STAFF` là quản lý |
| Cửa hàng cần quy trình bảo hành | Ứng dụng không có | `warranty.yaml` `extends: support_desk`, thêm số seri, ngày mua, mã hóa đơn, hạn ngắn, `webhook:` |
| Nối ứng dụng | Chưa có | `dxos apply` hai mô hình, `dxos connect` cho từng mô hình, do người vận hành tự gõ theo README |

**Hướng làm**

- `models/retail_return.yaml` là bản sao nguyên của mẫu, hoặc `extends: retail_return` rỗng; chọn bản `extends` nếu `dxos plan` cho kết quả giống nhau.
- `models/warranty.yaml`: thêm trường, đặt `deadline` ngắn cho demo; nếu `ai.tasks` bị thừa kế (Q3) thì tắt trong mô hình này.
- `setup/` chỉ làm phần không có lệnh sẵn: tạo tài khoản quản lý và kỹ thuật viên ở Keycloak rồi gán vai qua API, nạp sản phẩm; không chạy `apply` và `connect` thay người vận hành.
- `docker-compose.yml` của ứng dụng: `env_file: .env`, `extra_hosts`, cổng ứng dụng.
- Lõi và mô hình: nghiệp vụ cửa hàng điện máy chỉ nằm trong hai tệp mô hình.

**Phạm vi ảnh hưởng**

- Module: `examples/appliance-store/` (`models/`, `setup/`, `docker-compose.yml`).
- File chính: `models/*.yaml`, `setup/*`, `docker-compose.yml`.
- Endpoint, bảng DB, biến môi trường: dùng `POST /clients` qua `dxos connect`, API người dùng và vai, `POST /data/retail/product`; biến `DXOS_*` trong `.env` của ứng dụng.
- Vai và client bị ảnh hưởng: `CUSTOMER` của `retail`, `REQUESTER` của `warranty` (client); `STORE_STAFF`, `AGENT` (người).
- Tương thích ngược và dữ liệu cũ: không, ví dụ chạy trên hệ thống rỗng.

**Kết quả đầu ra**

- `dxos lint` và `dxos plan` đạt cho cả hai tệp, áp dụng lại không làm gì, `check_conformance.py` đạt cho `retail` và `warranty`.
- Sau các lệnh của README, quản lý và kỹ thuật viên đăng nhập được và thấy hộp việc rỗng.

**Phụ thuộc, lưu ý**: Q3 ở `00` mục 9; mục "Giới hạn nói thẳng" của README về hai tài khoản.

## WP-02: Ứng dụng và giao diện

- Mục tham chiếu: issue #343.
- Mục tiêu: sổ bán hàng chạy bình thường, chưa có quy trình.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Nhân viên bán hàng | Không có ứng dụng | Đăng nhập ứng dụng, xem danh sách đơn bán và chi tiết đơn |
| Yêu cầu đang xử lý | Không có | Màn hình thứ ba liệt kê yêu cầu đổi trả và bảo hành kèm trạng thái |

**Hướng làm**

- Node.js 24, Fastify, `node:sqlite`, HTML đơn giản; phụ thuộc duy nhất là Fastify.
- Ba màn hình: danh sách đơn bán, chi tiết đơn (nút Đổi trả, Bảo hành, trạng thái, liên kết sang DX-OS), danh sách yêu cầu.
- Đăng nhập nhân viên bằng tài khoản của chính ứng dụng (bảng người dùng và phiên đơn giản), không dùng Keycloak của DX-OS.
- Dữ liệu mẫu: vài sản phẩm và đơn bán nạp lúc khởi động.

**Phạm vi ảnh hưởng**

- Module: `examples/appliance-store/app/`.
- File chính: `app/server.js`, `app/db.js`, `app/views/`.
- Endpoint, bảng DB, biến môi trường: bảng `orders`, `products`, `staff`, `claims` (có cột `dxos_id`, `status`).
- Vai và client bị ảnh hưởng: nhân viên bán hàng (người dùng của ứng dụng).
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Ứng dụng chạy bằng `docker compose up` riêng, đăng nhập và xem đơn được khi chưa có DX-OS.
- `check_licenses.py` không báo lỗi với dependency của ứng dụng.

**Phụ thuộc, lưu ý**: song song được với WP-01.

## WP-03: Tích hợp

- Mục tham chiếu: issue #343, #344, #345.
- Mục tiêu: hồ sơ đi từ ứng dụng sang DX-OS và trạng thái quay về.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Bấm Đổi trả hoặc Bảo hành | Chỉ ghi trong ứng dụng | Ứng dụng lập hồ sơ ở DX-OS bằng client của mô hình, lưu `dxos_id`, hiện liên kết `webUrl` |
| Quản lý duyệt | Không có | Webhook về, ứng dụng kiểm chữ ký và đổi trạng thái thành "Đã duyệt" hoặc "Bị từ chối" |
| Bảo hành quá hạn | Không có | Webhook quá hạn về, ứng dụng hiện cảnh báo |

**Hướng làm**

- Một tệp `app/dxos.js` chứa toàn bộ tích hợp: lấy token bằng client credentials, `POST` hồ sơ, nhận webhook; đếm dòng của tệp này cho README.
- Nối hồ sơ: `dxos_id` trong bảng của ứng dụng, mã hóa đơn làm khóa nối.
- Webhook: README nêu ba việc (đích ở `deploy/webhooks/targets.yml`, `webhook: <tên>` ở mô hình, đường mạng tới ứng dụng); đích dùng `http://host.docker.internal:<cổng>/hooks/dxos`.
- Người yêu cầu ghi vào trường dữ liệu (tên khách), vì client lập hồ sơ thay họ.
- Lõi và mô hình: không đổi mã lõi.

**Phạm vi ảnh hưởng**

- Module: `examples/appliance-store/app/`; deploy (`extra_hosts` của backend).
- File chính: `app/dxos.js`, `app/server.js`, `deploy/docker-compose.yml`, `docs/INTEGRATIONS.md`, `docs/OPERATIONS.md`, `docs/QUICKSTART.md`, `docs/TESTING.md`.
- Endpoint, bảng DB, biến môi trường: `/hooks/dxos` của ứng dụng; `DXOS_BASE_URL`, `DXOS_<VAI>_CLIENT_ID`, `DXOS_<VAI>_CLIENT_SECRET`, `DXOS_WEBHOOK_SECRET`.
- Vai và client bị ảnh hưởng: `CUSTOMER` của `retail`, `REQUESTER` của `warranty`.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Hồ sơ đổi trả đi hết vòng: lập, duyệt ở hộp việc qua `webUrl`, trạng thái về ứng dụng.
- Webhook sai chữ ký bị ứng dụng từ chối, webhook trùng `eventId` không đổi trạng thái hai lần.
- Bảo hành quá hạn hiện cảnh báo ở ứng dụng.

**Phụ thuộc, lưu ý**: rủi ro webhook không tới được ứng dụng là điểm kiểm đầu tiên.

## WP-04: Kiểm, tài liệu và đóng việc

- Mục tham chiếu: issue #343.
- Mục tiêu: README đúng với những gì chạy được, và issue đóng được.

**Hướng làm**

- Chạy theo README trên stack thử cổng 9180, đủ kịch bản demo, sửa README theo kết quả.
- Ghi số dòng tích hợp thật; thêm mục pitch ở `docs/DEMO.md`.
- Xóa dòng #343 ở BACKLOG, xóa `UNG_DUNG_MAU.md` và thư mục này này; CHANGELOG một dòng.

**Phạm vi ảnh hưởng**

- Module: docs, `examples/appliance-store/README.md`.
- File chính: `README.md` của ví dụ, `docs/DEMO.md`, `CHANGELOG.md`, `docs/dynamic-core/BACKLOG.md`.

**Kết quả đầu ra**

- Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đạt và có bằng chứng; mô tả PR và comment done theo WORKFLOW §14, §15.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | API của DX-OS | Không đổi | Không |
| Tệp mô hình | `dx-model` | Không đổi; thêm hai tệp mô hình trong ví dụ | Không |
| Bảng DB | Của DX-OS | Không đổi; ứng dụng có SQLite riêng | Không |
| Biến môi trường | `DXOS_*` trong `.env` của ứng dụng | Mới, do `dxos connect` ghi | Không |
| Compose | `extra_hosts` của backend DX-OS | Thêm một dòng | Không |

## Dữ liệu, migration và rollback

- Migration: không; ứng dụng tạo bảng SQLite khi khởi động.
- Dữ liệu cũ: không có.
- Rollback: xóa thư mục ví dụ và gỡ hai mô hình bằng `dxos` theo QUICKSTART; không ảnh hưởng dữ liệu khác.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Cửa hàng điện máy; đổi trả dùng nguyên, bảo hành mở rộng | Hai họ quy trình và hai cách dùng mẫu trong một ví dụ |
| D2 | Script tạo hộ tài khoản người duyệt | Vai nhìn thấy rõ khi demo; ghi giới hạn hai tài khoản |
| D3 | Người duyệt dùng hộp việc của DX-OS | Không tự dựng màn hình duyệt |
| D4 | `apply` và `connect` do người vận hành tự gõ | Giữ các bước quan trọng, đo được số bước |
| D5 | Fastify và `node:sqlite` | Đã có trong `DEPENDENCIES.txt`, ít phụ thuộc |
