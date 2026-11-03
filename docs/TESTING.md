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
cd ai && python -m pytest tests -q   # cần pip install -r requirements-dev.txt
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
python3 deploy/dev/check_case.py            # sau check_recovery.py, trên cùng stack
python3 deploy/dev/check_change.py          # sau check_case.py, trên cùng stack
python3 deploy/dev/check_process.py         # sau check_change.py, trên cùng stack
```

Script tạo client Keycloak `dxos-check` (cho phép đăng nhập bằng mật khẩu, chỉ dùng cho máy dev) và các người dùng thử `admin1`, `req1`, `req2`, `keeper1`, `multi1`, `none1`, `editor1`.
Biến `DXOS_ENV_FILE`, `DXOS_BASE_URL`, `DXOS_PROJECT` đổi tệp env, địa chỉ gateway và tên project Compose.

| Mã | Nội dung | Kết quả mong đợi |
|---|---|---|
| T5-01 | Áp dụng `backend/dx-engine/src/main/resources/templates/equipment.yaml` vào hệ thống rỗng | Có schema, ba bảng, ràng buộc, vai Keycloak, một phiên bản, lịch sử các bước |
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
| T5-19 | Quyết định một hồ sơ; gửi `decided_by` trong yêu cầu sửa | Hồ sơ lưu người và thời điểm quyết định; yêu cầu sửa bị 400 |
| T5-20 | Áp dụng dở chưa đổi tên, rồi áp dụng tệp khác | Áp dụng được, bảng khớp với tệp đó |
| T5-21 | Bốn lần áp dụng song song cùng một mô hình mới | Một lần áp dụng, lịch sử có một lượt chạy |
| T6-01, T6-02 | Áp dụng `backend/dx-engine/src/main/resources/templates/onestop.yaml`; ba tệp sai về đơn vị và phạm vi | Có nhóm Keycloak và metadata; 400 kèm dòng |
| T6-03 đến T6-06 | Quyền theo đơn vị, hợp phạm vi, thực thể con, chuyển đơn vị | Đúng đơn vị thấy, sai đơn vị 404; chuyển đơn vị 403 |
| T6-07 đến T6-09 | Trả lại, sửa, nộp lại, duyệt; hành động sai | Đúng đường trạng thái; 400, 403, 404, 409 đúng chỗ |
| T6-10 đến T6-12 | Giao việc, người không được giao, hộp việc | Chỉ người được giao xử lý được; hộp việc đúng từng người |
| T6-13, T6-14 | Dòng lịch sử; lỗi cố ý sau bước quy trình | Đủ người, trạng thái, ghi chú, chỉ trường thực sự đổi; không có sự kiện của hành động lỗi |
| T6-16 | Vai `assigned` và vai điều phối | Hồ sơ chưa giao ngoài tầm với; điều phối giao được, không quyết định được |
| T7-01, T7-02 | Áp dụng lần đầu; thêm trường tùy chọn khi có 1.000 dòng | Cùng một đường; kế hoạch không đổi gì; thiếu hoặc sai `base` bị 409; API thấy ngay |
| T7-03 | Đổi tên trường có `UNIQUE` và khóa ngoại | Kế hoạch chỉ gồm đổi tên; thiếu `accept` bị 409; dữ liệu và ràng buộc còn nguyên |
| T7-04, T7-05 | Quy tắc mới mà dữ liệu vi phạm; trường bắt buộc có `default` | Từ chối kèm dòng vi phạm, không sót ràng buộc; `default` điền dòng cũ và mới |
| T7-06, T7-07 | Đổi kiểu; xóa trường; quên `renamed_from` | Từ chối; mức `LOSS` và câu hỏi có phải đổi tên không; chỉ chạy với `accept=LOSS` |
| T7-08 đến T7-10 | Hồ sơ đang xử lý; quay về phiên bản trước, cả khi có đổi tên | Có số hồ sơ; quay về là phiên bản mới, dữ liệu về tên cũ |
| T7-11 | Áp dụng dừng sau khi đã đổi tên | Tệp khác bị 409; đúng tệp đó hoàn tất |
| T8-01, T8-02 | Mô hình dùng cả hai họ mẫu; khối `process:` sai | Cả hai chạy không sửa mã lõi; 400 kèm dòng |
| T8-03 đến T8-06 | Lý do, rút lại, xóa, người không được phép | 400 khi thiếu lý do; rút lại giữ bản ghi và ghi lịch sử; xóa 409; 403 |
| T8-07 | Đổi hạn và người xử lý khi có hồ sơ; đổi họ mẫu | Mức `SAFE`, báo số hồ sơ đang chạy; đổi họ mẫu bị từ chối |
| T8-08 | Hạn xử lý, quá hạn, hộp việc, bộ quét | Cờ quá hạn đúng, quá hạn lên đầu hộp việc, một dòng lịch sử cho mỗi hạn |
| T8-09, T8-10 | Duyệt yêu cầu cấp quyền; nhóm Keycloak chưa có | Người dùng vào nhóm; thông điệp được thử lại tới khi nhóm có, mỗi khóa một thông điệp; từ chối không gửi gì |
| T9-01 đến T9-05 | Khối `booking:` sai; đặt trùng giờ, giờ liền nhau, đổi giờ; sức chứa hai; tám yêu cầu đồng thời | Từ chối kèm lý do; 409 khi đủ sức chứa; đúng một yêu cầu lọt qua |
| T9-06, T9-07 | Schema, họ mẫu, mẫu, phiên bản cho ADMIN và vai khác; áp dụng từng mẫu | Đúng nội dung; vai khác 403; mọi mẫu áp dụng được |
| T9-12 (C1 đến C8) | Tám tiêu chí của kịch bản trình diễn | Cả tám đạt |
| T8-11 | Tệp đính kèm | Gắn, tải qua gateway, gỡ, quyền theo bản ghi; 409 khi hồ sơ đã quyết định |

T5-11 và T6-14 cần dịch vụ chạy với `ENGINE_FAULT_AFTER_DECISION=true`; các script tự bật rồi tắt bằng cách tạo lại service `backend`.

## 5. Kiểm giao diện thủ công

1. Mở `http://localhost:9080/`, đăng nhập bằng người dùng vai `BORROWER` (ví dụ `req1` của script trên).
2. Menu có Thiết bị, Phiếu mượn, Ghi chú phiếu mượn; danh sách Thiết bị chỉ đọc (không có nút tạo).
3. Danh sách Phiếu mượn chỉ có phiếu của mình; biểu mẫu tạo có đủ năm trường theo kiểu của mô hình (danh sách chọn thiết bị, hai ô ngày giờ, số lượng).
4. Đăng xuất, đăng nhập bằng người dùng vai `KEEPER`: thấy mọi phiếu; mở một phiếu `Chờ duyệt` thì có nút Duyệt và Từ chối; bấm Duyệt thì trạng thái thành `Đã duyệt`.
5. Bundle web không chứa tên thực thể hay vai nào của mô hình.
6. Đăng nhập bằng người dùng vai `ADMIN`, mở `/console`, tab Hàng đợi sự kiện: có ba danh sách Lỗi, Chờ gửi, Đã gửi kèm số lượng; thông điệp lỗi hiện lỗi gần nhất và nút Thử lại ngay, bấm xong thì sau vài giây nó sang Đã gửi.
7. Tải một tệp `.html` lên trường tệp của một hồ sơ: bị từ chối (400); tải một tệp `.pdf` rồi mở link tải: trình duyệt lưu thành tệp; gỡ tệp rồi mở lại link cũ: 404.
8. Mọi chữ trên giao diện là tiếng Việt: nút Tạo mới, Thêm bộ lọc, số dòng mỗi trang, thông báo "Bắt buộc nhập", lỗi tệp mô hình theo dòng và cột ("dòng 2, cột 9: ..."), nhãn các loại thay đổi trong kế hoạch.
9. Bấm biểu tượng đổi giao diện: chế độ tối hiện đủ hộp việc, danh sách, chi tiết hồ sơ và bảng điều khiển.
10. Ở bảng điều khiển, tab Soạn và áp dụng: bấm biểu tượng mở to của sơ đồ quy trình thì hiện hộp thoại lớn, phần nền phía sau mờ đi.

## 6. Hồi quy

- Đăng nhập SSO qua Keycloak ở web hoạt động (mục 5).
- `docker compose up -d --build` từ clone sạch lên đủ tám service healthy: `postgres`, `keycloak`, `seaweedfs`, `backend`, `frontend`, `portal`, `monitor`, `apisix`.
- Màn hình giám sát `/monitor/` mở được với tài khoản ADMIN.
- Không còn từ khóa của ứng dụng cũ ngoài `ai/`, `bi/`, `chat/`:

```bash
grep -rIlw -i -E 'farm|lab|farmer|DXOS_APP|dx-host|app-farm|app-lab|dx-core-api' \
  --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git --exclude-dir=docs \
  --exclude-dir=ai --exclude-dir=bi --exclude-dir=chat --exclude-dir=dist .
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

Ngày 03/11/2026 trên bản sao sạch của cây làm việc (nhánh `Khuong/phase-10-release`, gồm cả tệp chưa commit), stack dựng bằng `docker compose up -d --build`.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify` | Đạt | dx-core 20 test, dx-engine 8, dx-monitor 3; dx-model không có test |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| `check_licenses.py`, `check_routes.py` | Đạt | 0 vấn đề, gồm header, giấy phép của SBOM và danh mục `.github/license-catalog.json` (image, mô hình, tệp trọng số, tài liệu, nén); 12 route, 11 công khai có chủ đích |
| `pytest` của `ai/` | Đạt | 21 test, chạy trong image dựng từ `ai/Dockerfile` |
| `ai/` chạy thật | Đạt | pgvector tạm: `init_db`, `ingest` 3 tài liệu kèm nguồn và giấy phép, tìm kiếm trả đúng tài liệu; câu ngoài kho và mã không có trong kho không có bằng chứng; thiếu khóa dịch vụ 401 |
| `deploy/install.sh` | Đạt | Cài sang thư mục khác, `docker compose config` đạt |
| `docker compose config` | Đạt | Tám service, cộng `clamav` ở profile `av` |
| Stack từ trạng thái sạch | Đạt | Tám service healthy |
| `check_api.py` | Đạt | 51/51; danh sách 10.000 dòng có lọc và sắp xếp, phân vị 95 là 10 ms, trung vị 9 ms |
| `check_recovery.py` | Đạt | 17/17 |
| `check_case.py` | Đạt | 71/71 |
| `check_change.py` | Đạt | 59/59 |
| `check_booking.py` | Đạt | 44/44: họ `reservation`, danh mục của bảng điều khiển, mọi mẫu áp dụng được |
| `check_demo.py` | Đạt | 26/26: tám tiêu chí của kịch bản trình diễn |
| `check_process.py` | Đạt | 56/56: hai họ mẫu, lý do, rút lại, hạn và quá hạn, hành động hoàn tất qua outbox kèm thử lại, tệp đính kèm |
| Giao diện Phase 8 | Đạt | Chrome, tài khoản thử: tạo yêu cầu cấp quyền, hạn xử lý, tải tệp, rút lại, lịch sử đủ ba dòng |
| Giao diện | Đạt | 20/20 bằng Chrome không giao diện: sinh viên nộp, cán bộ sai đơn vị không thấy, cán bộ đúng đơn vị nhận việc và trả lại, sinh viên sửa và nộp lại, cán bộ duyệt, kèm hộp việc và dòng lịch sử; 5/5 sau khi đổi tên và thêm trường, chỉ tải lại trang, không build lại |
| Giao diện Phase 10 | Đạt | Chrome, stack dựng từ bản sao sạch: đăng nhập, bảng điều khiển liệt kê mô hình, thư viện mẫu hiện sáu mẫu |
| Từ khóa ứng dụng cũ | Đạt | Không còn ngoài `ai/`, `bi/`, `chat/` và các tài liệu mô tả bản cũ |
| Tệp bản cũ và thành phần chưa mở | Đạt | Không còn `data/`, trọng số, PDF, `ai/evaluation`; Redis, Langfuse, Ollama, Qwen, mMARCO chỉ còn trong tài liệu mô tả việc đã gỡ |
| CI trên GitHub | Chưa chạy | Nhánh phase 10 chưa push |
| Chưa chạy | | Khởi động sau khi cập nhật từ bản `v1.1.1` (không có đường nâng cấp dữ liệu) |
