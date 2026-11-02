# Hướng dẫn kiểm thử

Tài liệu này nói cách kiểm các phần đã chạy của lõi và ghi kết quả gần nhất.
Mọi lệnh chạy từ gốc repo, trừ khi ghi khác; lệnh dựng stack ở [`README.md`](../README.md).

## 1. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| Docker và Docker Compose v2 | Backend `verify` dùng Testcontainers nên cần Docker chạy |
| JDK 21 | Dùng Maven wrapper trong repo |
| Node 20 trở lên | Cho `frontend` |
| Python 3 | Cho `deploy/make_env.py` và các script kiểm ở `deploy/dev` |

## 2. Kiểm tĩnh

```bash
cd backend && ./mvnw verify          # formatter, compile, test của dx-model, dx-core, dx-engine, dx-monitor
cd frontend && npx tsc --noEmit
python3 .github/scripts/check_licenses.py
python3 deploy/apisix/check_routes.py
```

`./mvnw verify` sau khi đổi tài nguyên nên chạy `clean verify`: tài nguyên cũ còn trong `target/` làm Flyway thấy hai migration cùng phiên bản.

## 3. Dựng stack

```bash
python3 deploy/make_env.py                  # sinh deploy/.env
cd deploy && docker compose up -d --build
docker compose ps                           # mọi service healthy
```

Muốn dựng lại từ đầu: `./reset.sh` trong `deploy/` (xóa `deploy/data`).

## 4. Kiểm API tự động

Chạy trên stack mới dựng, cơ sở dữ liệu rỗng, vì script áp dụng mô hình mẫu từ trạng thái rỗng:

```bash
python3 deploy/dev/check_api.py
python3 deploy/dev/check_recovery.py        # sau check_api.py, trên cùng stack
```

Script tạo client Keycloak `dxos-check` (cho phép đăng nhập bằng mật khẩu, chỉ dùng cho máy dev) và các người dùng thử `admin1`, `req1`, `req2`, `keeper1`, `multi1`, `none1`, `editor1`.
Biến `DXOS_ENV_FILE`, `DXOS_BASE_URL`, `DXOS_PROJECT` đổi tệp env, địa chỉ gateway và tên project Compose.

| Mã | Nội dung | Kết quả mong đợi |
|---|---|---|
| T5-01 | Áp dụng `models/equipment.yaml` vào hệ thống rỗng | Có schema, ba bảng, ràng buộc, vai Keycloak, một phiên bản, lịch sử các bước |
| T5-02, T5-03 | Bốn tệp sai ở `models/invalid/`; quy tắc duy nhất và lựa chọn ở dữ liệu | 400 kèm dòng và cột, hoặc lỗi theo trường; hệ thống không đổi |
| T5-04 | Áp dụng lại cùng tệp; áp dụng tệp khác cho mô hình đã có | Không làm gì; 409 |
| T5-06, T5-07 | Quyền theo vai và theo dòng, người không có vai, chưa đăng nhập, hai vai | `own` chỉ thấy dòng của mình; 403; 401; hợp quyền |
| T5-08 | Thực thể con theo chủ sở hữu của cha | Chỉ thấy bản ghi con của cha mình; gắn vào cha người khác bị 403 |
| T5-09 | Sửa hoặc gửi cột `status` bằng từng vai | 403 |
| T5-10 | Vai `report_ro` đọc và ghi trực tiếp vào cơ sở dữ liệu | Đọc được, ghi bị từ chối |
| T5-12, T5-13 | Quyết định hai lần; vai không phải vai duyệt quyết định | 409; 403 |
| T5-14 | Giá trị, tên trường, tên sắp xếp và toán tử có SQL | Giá trị vô hại; tên và toán tử lạ bị 400; dữ liệu nguyên vẹn |
| T5-15 | 10.000 dòng, 50 lượt lọc và sắp xếp | Phân vị 95 dưới 500 ms |
| T5-17 | Áp dụng mô hình `notes.yaml` khi dịch vụ đang chạy | Metadata và dữ liệu phục vụ ngay, mỗi vai chỉ thấy mô hình của mình |
| T5-05 | Áp dụng khi Keycloak dừng, rồi áp dụng lại | 502 và lịch sử ghi bước lỗi `ENSURE_ROLE`; áp dụng lại hoàn tất, vai tạo một lần |
| T5-11 | Lỗi cố ý sau bước quy trình của một quyết định | 500; hồ sơ vẫn `submitted` và tác vụ vẫn chờ; sau đó quyết định bình thường |

T5-11 cần dịch vụ chạy với `ENGINE_FAULT_AFTER_DECISION=true`; `check_recovery.py` tự bật rồi tắt bằng cách tạo lại service `backend`.

## 5. Kiểm giao diện thủ công

1. Mở `http://localhost:9080/`, đăng nhập bằng người dùng vai `BORROWER` (ví dụ `req1` của script trên).
2. Menu có Thiết bị, Phiếu mượn, Ghi chú phiếu mượn; danh sách Thiết bị chỉ đọc (không có nút tạo).
3. Danh sách Phiếu mượn chỉ có phiếu của mình; biểu mẫu tạo có đủ năm trường theo kiểu của mô hình (danh sách chọn thiết bị, hai ô ngày giờ, số lượng).
4. Đăng xuất, đăng nhập bằng người dùng vai `KEEPER`: thấy mọi phiếu; mở một phiếu `Chờ duyệt` thì có nút Duyệt và Từ chối; bấm Duyệt thì trạng thái thành `Đã duyệt`.
5. Bundle web không chứa tên thực thể hay vai nào của mô hình.

## 6. Hồi quy

- Đăng nhập SSO qua Keycloak ở web hoạt động (mục 5).
- `docker compose up -d --build` từ clone sạch lên đủ tám service healthy: `postgres`, `keycloak`, `seaweedfs`, `backend`, `frontend`, `portal`, `monitor`, `apisix`.
- Màn hình giám sát `/monitor/` mở được với tài khoản ADMIN.
- Không còn từ khóa của ứng dụng cũ ngoài `ai/`, `data/`, `bi/`, `chat/`:

```bash
grep -rIlw -i -E 'farm|lab|farmer|DXOS_APP|dx-host|app-farm|app-lab|dx-core-api' \
  --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git --exclude-dir=docs \
  --exclude-dir=ai --exclude-dir=data --exclude-dir=bi --exclude-dir=chat --exclude-dir=dist .
```

## 7. Sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `release version 21 not supported` | Đặt `JAVA_HOME` về JDK 21 |
| Flyway báo "more than one migration with version 1" khi test | Chạy `./mvnw clean verify` |
| Keycloak không có vai `keycloak_app` | Thư mục dữ liệu Postgres đã có từ trước; dựng lại bằng `./reset.sh` để `deploy/postgres/init` chạy |
| `check_api.py` lỗi ở bước đầu | Cơ sở dữ liệu đã có mô hình; dựng lại stack rỗng |
| Cổng 9080 hoặc 5432 bị chiếm | Đổi `APISIX_HOST_PORT`, `POSTGRES_HOST_PORT` trong `deploy/.env` |

## 8. Kết quả kiểm gần nhất

Ngày 02/11/2026 trên clone sạch, stack dựng bằng `docker compose up -d --build`.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify` | Đạt | dx-core 20 test, dx-engine 8, dx-monitor 3; dx-model không có test |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| `check_licenses.py`, `check_routes.py` | Đạt | 0 vấn đề; 12 route, 11 công khai có chủ đích |
| `docker compose config` | Đạt | Tám service, cộng `clamav` ở profile `av` |
| Stack từ trạng thái sạch | Đạt | Tám service healthy |
| `check_api.py` | Đạt | 49/49; danh sách 10.000 dòng có lọc và sắp xếp, phân vị 95 là 11 ms, trung vị 8 ms |
| `check_recovery.py` | Đạt | 12/12 |
| Giao diện | Đạt | Người mượn thấy phiếu của mình, người duyệt thấy tất cả và duyệt được trên màn chi tiết |
| Từ khóa ứng dụng cũ | Đạt | Không còn ngoài `ai/`, `data/`, `bi/`, `chat/` và các tài liệu mô tả bản cũ |
| Chưa chạy | | Khởi động sau khi cập nhật từ bản `v1.1.1` (không có đường nâng cấp dữ liệu); CI trên GitHub chưa chạy cho nhánh này |
