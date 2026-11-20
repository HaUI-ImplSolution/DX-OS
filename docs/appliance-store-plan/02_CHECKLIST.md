# Checklist: ứng dụng mẫu cửa hàng điện máy

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP-{xx}-{lớp}-{nn}`.
Lớp gồm DOC, BE, FE, DATA, INFRA, TEST; đợt làm này không đụng module `dx-*` nên dòng BE là mã của ứng dụng ví dụ.

## WP-01: Mô hình, script cài đặt và compose

Kết quả đầu ra: `dxos lint`, `dxos plan`, `check_conformance.py` đạt cho hai mô hình; quản lý và kỹ thuật viên đăng nhập được.

**Spec và tài liệu**

- [x] WP-01-DOC-01 Chốt Q3: chạy `dxos plan` với `warranty.yaml` xem `ai.tasks` có bị thừa kế không.

**Data, migration**

- [x] WP-01-DATA-01 `models/retail_return.yaml` (nguyên mẫu hoặc `extends` rỗng).
- [x] WP-01-DATA-02 `models/warranty.yaml` (`extends: support_desk`, số seri, ngày mua, mã hóa đơn, hạn ngắn, `webhook:`).
- [x] WP-01-DATA-03 `setup/`: tạo tài khoản quản lý và kỹ thuật viên, gán vai qua API.
- [x] WP-01-DATA-04 `setup/`: nạp sản phẩm từ danh mục của ứng dụng sang DX-OS.

**Infra, deploy, CI**

- [ ] WP-01-INFRA-01 `docker-compose.yml` của ứng dụng: `env_file`, `extra_hosts`, cổng.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WP-01-TEST-01 Chạy T-01 đến T-04.

## WP-02: Ứng dụng và giao diện

Kết quả đầu ra: ứng dụng chạy riêng, đăng nhập và xem đơn được khi chưa có DX-OS.

**Backend**

- [ ] WP-02-BE-01 Khung Fastify, SQLite, bảng `orders`, `products`, `staff`, `claims`, dữ liệu mẫu.
- [ ] WP-02-BE-02 Đăng nhập nhân viên và phiên.
- [ ] WP-02-BE-03 Các route danh sách đơn, chi tiết đơn, danh sách yêu cầu.

**Frontend (web)**

- [ ] WP-02-FE-01 Ba màn hình HTML đơn giản, thông báo lỗi tiếng Việt.

**Infra, deploy, CI**

- [ ] WP-02-INFRA-01 `Dockerfile` và `docker-compose.yml` cho ứng dụng; kiểm `check_licenses.py`.

**Test**

- [ ] WP-02-TEST-01 Chạy T-05.

## WP-03: Tích hợp

Kết quả đầu ra: đổi trả đi hết vòng; webhook sai chữ ký bị từ chối; bảo hành quá hạn hiện cảnh báo.

**Spec và tài liệu**

- [x] WP-03-DOC-01 Tài liệu đường mạng webhook về ứng dụng cùng máy: `docs/INTEGRATIONS.md`, `docs/OPERATIONS.md`, `docs/QUICKSTART.md`, CHANGELOG.
- [x] WP-03-DOC-02 Thêm mục kiểm tay vào `docs/TESTING.md`: webhook tới ứng dụng ở `host.docker.internal`.

**Backend**

- [ ] WP-03-BE-01 `app/dxos.js`: lấy token bằng client credentials.
- [ ] WP-03-BE-02 `app/dxos.js`: gửi hồ sơ đổi trả và bảo hành, lưu `dxos_id`.
- [ ] WP-03-BE-03 `app/dxos.js`: nhận webhook, kiểm chữ ký, bỏ qua `eventId` đã xử lý, đổi trạng thái.
- [ ] WP-03-BE-04 Hiện liên kết `webUrl` và cảnh báo quá hạn ở màn hình.

**Infra, deploy, CI**

- [x] WP-03-INFRA-01 `extra_hosts` cho backend ở `deploy/docker-compose.yml`, kiểm `docker compose config`.
- [ ] WP-03-INFRA-02 Hướng dẫn trong README: `targets.yml`, `webhook: <tên>`, đường mạng.

**Test**

- [ ] WP-03-TEST-01 Chạy T-06 đến T-11.

## WP-04: Kiểm, tài liệu và đóng việc

Kết quả đầu ra: mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đạt, issue đóng được.

**Spec và tài liệu**

- [ ] WP-04-DOC-01 Chạy theo README trên stack thử cổng 9180, sửa README theo kết quả.
- [ ] WP-04-DOC-02 Đếm và ghi số dòng của `app/dxos.js` vào README; bỏ dòng "định hướng".
- [ ] WP-04-DOC-03 Mục pitch ở `docs/DEMO.md`, nhận định thị trường ghi là giả thuyết (#231).
- [ ] WP-04-DOC-04 CHANGELOG một dòng; xóa dòng #343 ở `BACKLOG.md`; xóa `UNG_DUNG_MAU.md`.

**Test**

- [ ] WP-04-TEST-01 Chạy T-12 và T-13, ghi kết quả.

## Đóng đợt làm

- [ ] Compile và đọc lại diff; ví dụ không dùng mã của `backend/` hay `frontend/`.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] `deploy/.env.example` đúng (không thêm biến); từng tệp mô hình áp dụng được vào hệ thống rỗng.
- [ ] Mã lõi không chứa tên thực thể, vai hay luồng của cửa hàng điện máy.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu, container, volume và ảnh của stack thử.
- [ ] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Xóa thư mục này này, việc còn lại gộp vào `BACKLOG.md`.
