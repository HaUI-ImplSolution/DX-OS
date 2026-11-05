# Hướng dẫn kiểm thử

Tài liệu này nói cách kiểm các phần đã chạy của lõi và ghi kết quả gần nhất.
Mọi lệnh chạy từ gốc repo, trừ khi ghi khác; lệnh dựng stack ở [`README.md`](../README.md).

## 1. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| Docker và Docker Compose v2 | Backend `verify` dùng Testcontainers nên cần Docker chạy |
| JDK 21 | Dùng Maven wrapper trong repo |
| Node 20 trở lên | Cho `frontend` |
| Python 3 | Cho các script kiểm ở `deploy/dev` |

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
deploy/make_env.sh                          # sinh deploy/.env
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
python3 deploy/dev/check_booking.py         # sau check_process.py, trên cùng stack
python3 deploy/dev/check_demo.py            # sau các script trên hoặc trên stack rỗng
python3 deploy/dev/check_guards.py          # sau các script trên, trên cùng stack
python3 deploy/dev/check_users.py           # sau check_guards.py, trên cùng stack
python3 deploy/dev/check_events.py          # sau check_users.py, trên cùng stack; khởi động lại backend vài lần
python3 deploy/dev/check_bootstrap.py       # sau check_events.py, trên cùng stack; khởi động lại backend vài lần
```

Tiêu chí 3 của `check_demo.py` (chặn tệp nhiễm mã độc) chỉ chạy khi bật quét: `dxos av on` trên bản cài, hoặc đặt `COMPOSE_PROFILES=av` và `STORAGE_SCAN_HOST=clamav` trong `deploy/.env` rồi `docker compose up -d`.

Script tạo client Keycloak `dxos-check` (cho phép đăng nhập bằng mật khẩu, chỉ dùng cho máy dev) và các người dùng thử `admin1`, `req1`, `req2`, `keeper1`, `multi1`, `none1`, `editor1`.
Vai của mô hình được gán ở client `dxos-m-<mô hình>`, vai `ADMIN` ở realm.
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
| T5-05 | Áp dụng lần đầu khi Keycloak dừng; áp dụng dừng ở một bước, rồi áp dụng lại | 502 trước bước đầu, không có bảng hay lượt chạy nào; dừng ở bước nào thì lịch sử ghi bước lỗi đó (`ENSURE_CLIENT`), các bước trước còn; áp dụng lại hoàn tất, vai tạo một lần |
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
| T12-01 đến T12-04 | Hai mô hình dùng cùng tên vai; vai `ADMIN`; người chỉ giữ vai của một mô hình gọi mô hình kia; hai mô hình áp dụng song song | Cả hai áp dụng được; `ADMIN` bị 400 kèm dòng; người giữ vai của mô hình này bị 403 ở mô hình kia; hai lần áp dụng đều 200 |
| T13-06 đến T13-14 | Tab Người dùng ở mức API: danh sách có tìm kiếm và phân trang, gán và gỡ vai và đơn vị, vai hoặc đơn vị không có (404), người không phải ADMIN (403), tạo người dùng, khóa và mở, đặt lại mật khẩu, tự khóa mình (409), bản ghi audit và sự kiện quản trị của Keycloak | Token đổi theo vai; mật khẩu tạm phải đổi trước khi đăng nhập; mỗi thay đổi có bản ghi `USER_*` với người thực hiện, thao tác không xảy ra thì không có; không mật khẩu nào trong log, audit hay sự kiện Keycloak |
| T12-05 đến T12-11 | Xóa thực thể còn hồ sơ đang chạy quy trình; tên thực thể gần giống nhau; hồ sơ đã rút kèm tệp đính kèm | Từ chối khi còn quy trình đang chạy; tên gần giống không chặn nhầm; sau khi rút thì xóa được với `accept=LOSS`, tệp và bảng bị dọn, thực thể khác và quy trình đang chạy nguyên vẹn |
| T12-12 đến T12-22 | Áp dụng thay đổi dở khi Keycloak dừng, lần áp dụng đầu của một mô hình dừng ở `ENSURE_CLIENT`; khởi động lại backend; rồi Tiếp tục và Hủy bỏ | Mô hình bị giữ (409), không hiện cho người dùng, `GET /models` báo `held`, vẫn giữ sau khi khởi động lại; `resume` hoàn tất không cần tệp; `abandon` đưa cột và bảng về phiên bản đang chạy và giữ dòng dữ liệu, hoặc xóa schema của mô hình chưa có phiên bản; chỉ lần chạy dở mới giữ mô hình |
| T14-01 | Tạo hồ sơ liên tục trong lúc áp dụng một thay đổi bảng; đổi luật quy trình khi còn hồ sơ mở và hồ sơ mới đến | Không có 500; thay đổi bảng áp dụng được, yêu cầu đến lúc mô hình bị giữ nhận 409; đổi luật bị từ chối 409 (kiểm tay) |
| T14-02 | Lần áp dụng đầu dừng ở `ENSURE_ROLE`, rồi Hủy bỏ | Client `dxos-m-<mô hình>` và schema mất; áp dụng lại tệp đó tạo client mới (kiểm tay) |
| T14-03 | `backup.sh`, rồi `verify` khi đủ tệp và khi kho thiếu một tệp; `DXOS_FILE_DELETE_DELAY` ngắn hơn lượt chạy | `Verify OK`; `Verify FAILED: files missing for attachments: <khóa>` và mã 1; dòng `WARNING` (kiểm tay, job `backup-drill`) |
| T14-05 | `dxos plan`, `dxos apply` với tệp giống, đổi `SAFE`, đổi `LOSS` có và không có `--accept` | `unchanged`; áp dụng được; `LOSS` thiếu hoặc thấp hơn `--accept` bị từ chối với mã 1; lịch sử ghi `service-account-dxos-cli` (kiểm tay) |
| T14-06, T14-09 | Bên nhận webhook luôn lỗi; gỡ tệp khi kho ngừng | Thông điệp dừng sau số lần đặt, Thử lại cho thêm một lần; tệp rời kho sau khoảng trễ, kho ngừng thì thử lại tới khi mất (`check_events.py`, EV-13 đến EV-17) |
| T14-07 | Bật quét, dừng `clamav`, tải tệp | 503 `ERR_SCAN_UNAVAILABLE`, không tệp nào được lưu (`check_demo.py`) |
| T14-08 | Xóa container `backend` rồi `dxos reconcile`; `dxos stop` rồi `dxos reconcile`; `docker inspect` chính sách restart | Container chạy lại và healthy; stack dừng có chủ ý không bị bật lại; mọi service `unless-stopped` (kiểm tay) |
| T14-10 | Sửa, xóa, chèn một dòng `record_event` bằng SQL; cắt chuỗi sau một điểm neo | `verify` chỉ ra dòng đầu tiên bị đổi (`its content was changed`, `the line before it was changed or removed`, `not linked`); điểm neo mất dòng nằm ở `mismatchedAnchors`; trả lại dòng thì chuỗi nguyên vẹn (kiểm tay) |
| T14-11 | Thống kê với vai `unit` và `own`, có và không có bộ lọc | `total` và tổng `byStatus` bằng `totalElements` của danh sách cùng bộ lọc (kiểm tay) |
| T14-12 | `simulator.py setup`, `run`, `replay` | Hồ sơ sự cố xuất hiện; gửi lại cùng quan sát in `duplicate` và không tạo thêm hồ sơ (kiểm tay) |
| T14-13 | `dxos archive <mô hình>` rồi `dxos archive check`; sửa một byte trong tệp Parquet | Số dòng và sha256 khớp manifest; tệp bị sửa thì `check` thoát với mã 1 (kiểm tay) |
| B4-01 | Áp dụng lại tệp không đổi khi bảng thiếu cột và ràng buộc, thừa cột, đổi kiểu cột, và khi client, vai, nhóm đơn vị bị xóa | `repair` đúng loại thay đổi; áp dụng xong thì kế hoạch về `unchanged`; cột thừa bị từ chối; đổi kiểu cột cần `accept=MIGRATION` (kiểm tay) |
| B4-02 | Menu người dùng, mục "Tài khoản của tôi" | Mở Account Console của Keycloak ở tab mới, đã đăng nhập (kiểm tay) |

T5-11 và T6-14 cần dịch vụ chạy với `ENGINE_FAULT_AFTER_DECISION=true`, T5-05 và T12-13 với `ENGINE_FAULT_APPLY_STEP=ENSURE_CLIENT` (bước áp dụng có tên đó lỗi); các script tự bật rồi tắt bằng cách tạo lại service `backend`.

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
11. Dừng Keycloak (`docker compose stop keycloak`), áp dụng một thay đổi cho một mô hình, rồi bật lại Keycloak: thẻ mô hình hiện nhãn "Đang dừng dở" kèm nút Tiếp tục áp dụng và Hủy bỏ; Tiếp tục thì nhãn mất và phiên bản tăng; Hủy bỏ hỏi xác nhận rồi đưa mô hình về phiên bản đang chạy.
    Mô hình mới áp dụng khi Keycloak dừng thì báo lỗi và không tạo gì; để lần áp dụng đầu dừng dở, chạy backend với `ENGINE_FAULT_APPLY_STEP=ENSURE_CLIENT`: thẻ hiện nhãn "Chưa có phiên bản", không có nút Sửa mô hình, và Hủy bỏ làm thẻ biến mất.
12. Khai báo vai `ADMIN` ở tab Soạn và áp dụng: báo lỗi tiếng Việt kèm dòng và cột, không có nút Áp dụng; khai báo vai trùng tên với mô hình khác thì áp dụng được.
13. Khi một thực thể còn hồ sơ đang xử lý, đổi hạn xử lý hoặc người xử lý của nó rồi bấm Xem kế hoạch: báo "Không đổi được ... khi còn N hồ sơ đang xử lý" và không có nút Áp dụng; rút hết hồ sơ rồi xem lại thì có nút Áp dụng.

## 6. Hồi quy

- Đăng nhập SSO qua Keycloak ở web hoạt động (mục 5).
- `docker compose up -d --build` từ clone sạch lên đủ tám service healthy: `postgres`, `keycloak`, `seaweedfs`, `backend`, `frontend`, `portal`, `monitor`, `apisix`.
- Màn hình giám sát `/monitor/` mở được với tài khoản ADMIN.
- Không còn từ khóa của ứng dụng cũ ngoài `ai/`:

```bash
grep -rIlw -i -E 'farm|lab|farmer|DXOS_APP|dx-host|app-farm|app-lab|dx-core-api' \
  --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git --exclude-dir=docs \
  --exclude-dir=ai --exclude-dir=dist .
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

Ngày 03/11/2026 trên cây làm việc của nhánh `Khuong/main` (gồm cả tệp chưa commit), stack rỗng dựng bằng `docker compose up -d --build`.
Các dòng ghi "Phase 10" là kết quả của lần kiểm trước, cùng ngày, không chạy lại.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify` | Đạt | dx-core 20 test, dx-engine 8, dx-monitor 3; dx-model không có test |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| `check_licenses.py`, `check_routes.py` | Đạt | 0 vấn đề, gồm header, giấy phép của SBOM và danh mục `.github/license-catalog.json` (image, mô hình, tệp trọng số, tài liệu, nén); 12 route, 11 công khai có chủ đích |
| `pytest` của `ai/` | Đạt | Phase 10: 21 test, chạy trong image dựng từ `ai/Dockerfile` |
| `ai/` chạy thật | Đạt | Phase 10: pgvector tạm: `init_db`, `ingest` 3 tài liệu kèm nguồn và giấy phép, tìm kiếm trả đúng tài liệu; câu ngoài kho và mã không có trong kho không có bằng chứng; thiếu khóa dịch vụ 401 |
| Bộ cài và `dxos` | Đạt | Gói dựng từ cây làm việc, image local: gói sai SHA-256 bị từ chối; cài vào `~/.dxos` và `~/.local/bin/dxos`; cài lần hai bị từ chối; `dxos start` từ trạng thái rỗng tới khi mọi dịch vụ sẵn sàng, tạo `admin` với mật khẩu tạm, lần hai không tạo lại; `status`, `logs`, `admin`, `doctor`, `version` đúng; nâng cấp bằng gói mới giữ `.env`, thêm biến mới, xóa tệp bản mới không còn; `uninstall --purge` không để lại container, volume, thư mục hay liên kết |
| `check_events.py` | Đạt | 12/12 trên stack thử riêng: thứ tự, chữ ký, nội dung không lộ, khớp với outbox, tệp đính kèm, thử lại khi bên nhận lỗi, quá hạn, quyết định bị hủy, không đặt địa chỉ, dọn outbox; địa chỉ không trả lời tốn một lần chờ mỗi vòng giao và không có trong log |
| `check_bootstrap.py` | Đạt | 8/8: `applied`, `unchanged`, `skipped` (đã áp dụng trước đó, mức LOSS), `failed` (tệp hỏng, không có tệp), không đặt tệp |
| Lần chạy đầu bằng Chrome | Đạt | `admin` đăng nhập bằng mật khẩu tạm, bị buộc đổi mật khẩu, vào bảng điều khiển, áp dụng mẫu đầu tiên của thư viện mẫu |
| `check_demo.py` trên bản cài | Đạt | 26/26 với `DXOS_ENV_FILE=~/.dxos/deploy/.env DXOS_PROJECT=dxos` |
| Job `install` của CI | Đạt | Chạy lại ở máy: cài từ gói, gói bị sửa bị từ chối, mọi tệp Compose mount đều có |
| `docker compose config` | Đạt | Tám service, cộng `clamav` ở profile `av` |
| Stack từ trạng thái sạch | Đạt | Tám service healthy |
| `check_api.py` | Đạt | 51/51; danh sách 10.000 dòng có lọc và sắp xếp, phân vị 95 là 10 ms, trung vị 8 ms |
| `check_recovery.py` | Đạt | 17/17 |
| `check_case.py` | Đạt | 71/71 |
| `check_change.py` | Đạt | 59/59 |
| `check_process.py` | Đạt | 56/56: hai họ mẫu, lý do, rút lại, hạn và quá hạn, hành động hoàn tất qua outbox kèm thử lại, tệp đính kèm |
| `check_booking.py` | Đạt | 44/44: họ `reservation`, danh mục của bảng điều khiển, mọi mẫu áp dụng được |
| `check_demo.py` | Đạt | 26/26: tám tiêu chí của kịch bản trình diễn, không bật `av` |
| `check_demo.py` với `av` | Đạt | Tiêu chí 3: tệp EICAR bị từ chối với 422 `ERR_FILE_INFECTED` và không được lưu |
| Bảy script trên stack rỗng sau khi thêm `V5` và chỉ mục `handle_by` | Đạt | 324/324, trên bản cài bằng `dxos` với image build từ cây làm việc; có 11 chỉ mục `ix_*_handle_by` |
| Bảng điều khiển và hồ sơ bằng Chrome | Đạt | Trình soạn báo vi phạm schema và gợi ý khóa của tệp mô hình, không lỗi trên trang; danh sách mô hình hiện tên người áp dụng; rút lại hỏi xác nhận, lý do hiện trong lịch sử |
| Giao diện Phase 8 | Đạt | Phase 10: Chrome, tài khoản thử: tạo yêu cầu cấp quyền, hạn xử lý, tải tệp, rút lại, lịch sử đủ ba dòng |
| Giao diện hồ sơ | Đạt | Phase 10: 20/20 bằng Chrome không giao diện: sinh viên nộp, cán bộ sai đơn vị không thấy, cán bộ đúng đơn vị nhận việc và trả lại, sinh viên sửa và nộp lại, cán bộ duyệt, kèm hộp việc và dòng lịch sử; 5/5 sau khi đổi tên và thêm trường, chỉ tải lại trang, không build lại |
| Giao diện Phase 10 | Đạt | Phase 10: Chrome, stack dựng từ bản sao sạch: đăng nhập, bảng điều khiển liệt kê mô hình, thư viện mẫu hiện sáu mẫu |
| Portal, giám sát, bảng điều khiển | Đạt | 8/8 bằng Chrome không giao diện: portal có bốn thẻ, không còn BI và chat, năm dịch vụ đều hoạt động, không lỗi console; màn hình giám sát không còn ô BI và chat; ADMIN mở được bảng điều khiển |
| Từ khóa ứng dụng cũ | Đạt | Không còn ngoài `ai/` và các tài liệu mô tả bản cũ |
| Tệp bản cũ và thành phần chưa mở | Đạt | Không còn `data/`, trọng số, PDF, `ai/evaluation`; Redis, Langfuse, Ollama, Qwen, mMARCO chỉ còn trong tài liệu mô tả việc đã gỡ |
| CI trên GitHub | Đạt | Commit `d0790226` trên `Khuong/main`: CI Infra, Frontend, Dependencies; commit `2b337e40` có thêm CI Backend và CI AI |
| Chưa chạy | | Khởi động sau khi cập nhật từ bản `v1.1.1` (không có đường nâng cấp dữ liệu) |

### Kiểm ngày 04/11/2026 trên nhánh `fix/live-model-guards`

Cây làm việc gồm cả tệp chưa commit; stack dựng riêng (project Compose `dxos-e2e`), cơ sở dữ liệu rỗng.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify` | Đạt | dx-core 20 test, dx-engine 8, dx-monitor 3; dx-model không có test |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| Tám script ở `deploy/dev` theo thứ tự trên | Đạt | 387/387: 51, 17, 71, 65, 56, 44, 26 và `check_guards.py` 57 |
| Bảng điều khiển bằng Chrome | Đạt | Lỗi vai `ADMIN` và vai trùng bằng tiếng Việt, mô hình dừng dở hiện nhãn và hai nút, Tiếp tục và Hủy bỏ đều chạy, kể cả mô hình chưa có phiên bản |
| Tài nguyên toàn stack | Đạt | Đo ngày 04/11/2026 khi chạy tám script: tối đa khoảng 1,7 GiB RAM cho tám service, số từng service ở `deploy/OPERATIONS.md` |
| Hai bản sao `backend` sau gateway | Đạt | Cả hai nhận lưu lượng. Sau một lần áp dụng, 22 trong 40 yêu cầu dữ liệu nhận 404 và danh sách mô hình nhảy qua lại; mô hình bị giữ vẫn được bản sao kia phục vụ (26 trong 40 yêu cầu trả 200 thay vì 409); khởi động lại cả hai thì 40/40 đúng |
| Đổi luật quy trình khi còn hồ sơ mở, bằng Chrome | Đạt | Còn hai hồ sơ: báo không đổi được hạn xử lý, không có nút Áp dụng; rút cả hai thì có nút Áp dụng |
| Bảng từ bản cũ hơn (mô phỏng bằng cách xóa chỉ mục, rồi cột `handle_by`) | Đạt | Thiếu chỉ mục: mọi API chạy bình thường. Thiếu cột: danh sách, đọc, tạo và hộp việc trả 500, log có `BadSqlGrammarException`; áp dụng lại cùng tệp trả `unchanged` và không sửa; một tệp có nội dung khác thêm lại cột và chỉ mục, rồi mọi API trả 200 |

### Kiểm ngày 04/11/2026 trên nhánh `Khuong/phase-13-role-isolation`

Cây làm việc gồm cả tệp chưa commit; stack dựng riêng (project Compose `dxos-e2e`), cơ sở dữ liệu rỗng.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify` | Đạt | dx-core 20 test, dx-engine 8, dx-monitor 3; dx-model không có test |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| Chín script ở `deploy/dev` theo thứ tự trên | Đạt | 428/428: 51, 17, 71, 65, 56, 44, 26, 59 và `check_users.py` 39 |
| `audit_log` sau chuỗi trên | Đạt | 41 bản ghi (2 từ chối, 30 lỗi, 9 thành công), không lỗi ghi |
| Tab Người dùng bằng Chrome | Đạt | Danh sách, tìm kiếm, gán vai, đặt lại mật khẩu, tạo người dùng |
| Stack dev dựng lại từ trạng thái rỗng bằng `reset.sh` | Đạt | Tám service healthy, tài khoản dịch vụ có đủ quyền client |

### Kiểm ngày 04/11/2026 bằng Chrome trên stack thử riêng

Ba mục đầu chạy trên stack chính; ba mục sau chạy trên một stack thử riêng (project Compose `dxscratch`, cổng 9180, cơ sở dữ liệu rỗng), đã xóa sau khi kiểm.
Cả hai dùng image `dxos-*:local` dựng sẵn trên máy.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| Tab Người dùng: khóa và mở tài khoản | Đạt | Danh sách đổi giữa "Đã khóa" và "Hoạt động", nút đổi theo |
| Tab Người dùng: gán và gỡ vai, gán và gỡ đơn vị | Đạt | Đóng rồi mở lại hộp thoại vẫn còn, gỡ thì hết |
| Mật khẩu tạm ở trang đăng nhập Keycloak | Đạt | Người mới tạo bị buộc đổi mật khẩu, đổi xong vào được Hộp việc, không có menu quản trị |
| `STAFF_OTP_FLOW=CONDITIONAL` | Đạt | `ADMIN` bị buộc thiết lập Mobile Authenticator; người không có vai `ADMIN` đăng nhập bình thường |
| Thẻ "Đang dừng dở" sau khi vai chuyển sang client role | Đạt | Tắt Keycloak rồi áp dụng thì báo lỗi; bật lại thì thẻ hiện nhãn, lời giải thích, Tiếp tục áp dụng, Hủy bỏ, Lịch sử; Tiếp tục áp dụng đưa mô hình lên phiên bản 1 |
| Realm thiếu quyền client của tài khoản dịch vụ | Đạt | Keycloak từ chối tạo client (403), log backend ghi rõ; người dùng thấy thông báo "dịch vụ tài khoản chưa dùng được", đã ghi ở giới hạn đã biết |

### Kiểm ngày 04/11/2026: lịch sử thay đổi trong hộp thoại người dùng

Backend dựng từ cây làm việc gồm cả tệp chưa commit; giao diện là bản build chạy bằng `vite preview` trỏ vào stack chính.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw compile formatter:validate` | Đạt | Năm module build thành công, không tệp nào lỗi định dạng |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| Chuỗi script ở `deploy/dev` trên cơ sở dữ liệu rỗng | Đạt | 285/285: `check_api.py` 51, `check_case.py` 71, `check_change.py` 65, `check_guards.py` 59, `check_users.py` 39; chưa có kiểm tra riêng cho tham số lọc theo đối tượng |
| `GET /api/v1/audit-logs` lọc theo `targetType` và `targetId`, gọi tay | Đạt | Chỉ trả các bản ghi của đối tượng đó; migration `V2` chạy trên cơ sở dữ liệu có sẵn dữ liệu |
| Hộp thoại người dùng bằng Chrome | Đạt | Tạo, gán và gỡ vai, thêm và bỏ đơn vị, khóa, mở, đặt lại mật khẩu: mỗi thao tác một dòng đúng người, đúng nội dung, mới nhất ở trên; đơn vị hiện nhãn; không có mật khẩu nào trong lịch sử |
| Phân trang lịch sử bằng Chrome | Đạt | 21 bản ghi hiện 20 dòng và nút Xem thêm; bấm thì ra bản ghi cũ nhất và nút biến mất |
| Mã hành động chưa có nhãn | Đạt | Bản ghi chèn tay với mã lạ hiện nguyên mã; bản ghi không có tên người thực hiện hiện "Không rõ" |
| Hộp thoại người dùng trong image `frontend` của Compose | Đạt | Dựng image từ cây làm việc, chạy trong stack thử riêng: lịch sử, tên đơn vị và mã lạ hiện như bản build thử |

### Kiểm ngày 04/11/2026: tệp mô hình lúc khởi động

Image backend và frontend dựng từ cây làm việc, chạy trong stack thử riêng (cơ sở dữ liệu rỗng); đã xóa sau khi kiểm.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| Tệp lần đầu, khởi động lại cùng tệp | Đạt | `applied` phiên bản 1 với người áp dụng `bootstrap`; khởi động lại ra `unchanged` |
| Tệp đổi an toàn, tệp đã áp dụng trước đó, tệp mất dữ liệu | Đạt | Thêm trường tùy chọn ra `applied` phiên bản 2; quay lại tệp đầu ra `skipped`, giữ phiên bản đang chạy; tệp bỏ một trường ra `skipped` mức LOSS |
| Tệp YAML hỏng, tệp không có | Đạt | `failed` kèm dòng và cột, backend vẫn chạy |
| Chờ Keycloak | Đạt | Keycloak tắt lúc backend khởi động: backend vẫn healthy, ghi "waiting" một lần, Keycloak bật lại thì `applied`; Keycloak tắt quá thời gian chờ (đặt 90 giây khi thử) ra `failed` |
| Mô hình có lần áp dụng dở | Đạt | Áp dụng khi Keycloak tắt: lần chạy `failed` ở `ENSURE_CLIENT`, mô hình bị giữ; khởi động lại với tệp mới ra `skipped` kèm lý do; Tiếp tục áp dụng xong phiên bản 2; khởi động lại ra `applied` phiên bản 3 |
| Cài bằng `install.sh` với image local, rồi `dxos start <tệp>` | Đạt | Image gắn thẻ riêng, cổng riêng: `applied` phiên bản 1; chạy lại cùng tệp ra `unchanged`; tệp bỏ một trường ra `skipped` mức LOSS; `dxos uninstall --purge` không để lại container, volume hay thư mục |
| Cài bằng image thật từ GHCR | Chưa chạy | Image `v2.0.0` chưa phát hành; xem phần 2 của `docs/dynamic-core/BACKLOG.md` |

### Kiểm ngày 04/11/2026: sửa các lỗi của đợt rà soát

Cây làm việc gồm cả tệp chưa commit.
Chuỗi script chạy trên một stack thử riêng (project Compose `dxscratch`, cổng 9180, cơ sở dữ liệu rỗng), đã xóa sau khi kiểm, rồi chạy lại trên stack chính dựng lại bằng `reset.sh`.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify formatter:validate` | Đạt | Năm module build thành công, không tệp nào lỗi định dạng |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| Chín script ở `deploy/dev` theo thứ tự trên | Đạt | 428/428 trên cả hai stack: 51, 17, 71, 65, 56, 44, 26, 59, 39 |
| Các lỗi của đợt rà soát, gọi API bằng kịch bản tạm ngoài repo | Đạt | 19/19: thiếu `enabled` bị 400 và tài khoản vẫn mở; người điều phối không có vai xử lý tự nhận hồ sơ bị 400, giao cho người xử lý thì được; sức chứa hai đã có lịch 09–10 và 10–11 nhận lịch 09–11 và từ chối lịch 09:30–10:30; `in` không có giá trị bị 400; trang 2147483647 của hộp việc, người dùng và hàng đợi sự kiện trả danh sách rỗng |
| Bảng điều khiển ở màn rộng 360px bằng Chrome | Đạt | Năm tab vừa màn hình, bảng người dùng cuộn ngang trong khung; ở màn rộng 1280px vẫn hai cột |
| Stack chính sau `reset.sh` | Đạt | 22 schema `m_*` khớp 22 client `dxos-m-*` của Keycloak |

### Kiểm ngày 04/11/2026: sửa các mục 2, 3, 7 và 9 của báo cáo kỹ thuật

Cây làm việc gồm cả tệp chưa commit.
Chuỗi script chạy trên một stack thử riêng (project Compose `dxscratch`, cổng 9180, cơ sở dữ liệu rỗng) với image backend dựng từ cây làm việc, đã xóa sau khi kiểm.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw compile formatter:validate` | Đạt | Năm module build thành công, không tệp nào lỗi định dạng |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| Mười một script ở `deploy/dev` theo thứ tự trên | Đạt | 452/452: 51, 19, 71, 65, 56, 44, 26, 59, 41, `check_events.py` 12 và `check_bootstrap.py` 8 |
| Vai khai báo lại, gọi API bằng kịch bản tạm ngoài repo | Đạt | 14/14: bỏ vai thì người giữ bị 403; khai báo lại thì kế hoạch ở mức `MIGRATION` kèm câu hỏi đúng số người giữ; áp dụng không xác nhận bị từ chối và người giữ vẫn 403; `accept=MIGRATION` thì 200; quay về phiên bản trước cũng hỏi; vai mới chưa ai giữ vẫn `SAFE` |
| Vai khai báo lại ở bảng điều khiển bằng Chrome | Đạt | Quay về phiên bản trước hiện nhãn "Cần xác nhận", câu hỏi tiếng Việt và ô xác nhận trả lại quyền; nút khóa tới khi tích, tích rồi thì quay về thành công |
| `backup.sh` rồi `verify` trên stack chính | Đạt | 22 mô hình, 10.055 dòng trong các bảng `m_*`; câu truy vấn tìm mô hình thiếu bảng chỉ ra đúng mô hình thử đã bị xóa bảng |
| `dxos av on`, `dxos av off` | Đạt | Bật thì ClamAV chạy tới khi healthy và tiêu chí 3 của `check_demo.py` chặn tệp EICAR với 422, tệp không được lưu; tắt thì gỡ ClamAV; `dxos status` và `doctor.sh` báo đúng trạng thái quét |
| Hành động hoàn tất bị bỏ qua | Đạt | Duyệt hồ sơ mà người dùng hoặc nhóm không có: log backend có dòng `WARN` nêu lý do |

### Kiểm ngày 04/11 và 05/11/2026: sửa các lỗi của báo cáo kỹ thuật

Cây làm việc gồm cả tệp chưa commit.
Chuỗi script chạy trên một stack thử riêng (project Compose `dxscratch`, cổng 9180) dựng rỗng, với image backend dựng từ cây làm việc; stack đã gỡ cùng volume sau khi kiểm.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify formatter:validate` | Đạt | Mã thoát 0, 31 test chạy, không lỗi, không bỏ qua |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| Mười một script ở `deploy/dev` theo thứ tự trên | Đạt | 459/459: 51, 19, 71, 65, 56, 45, 26, 59, 41, `check_events.py` 18 và `check_bootstrap.py` 8 |
| `check_demo.py` với quét virus bật (`COMPOSE_PROFILES=av`) | Đạt | 30/30: tệp nhiễm bị từ chối 422, dừng `clamav` thì tải tệp nhận 503 `ERR_SCAN_UNAVAILABLE` và không tệp nào được lưu |
| Thống kê, kịch bản tạm ngoài repo | Đạt | `total` bằng `totalElements` của danh sách và bằng tổng `byStatus` cho vai `unit` và hai người dùng `own`, cả khi lọc theo `status` |
| Chuỗi hash của lịch sử, kịch bản tạm ngoài repo | Đạt | Dòng nguyên vẹn thì `intact`; sửa, xóa và chèn một dòng bằng SQL chỉ ra đúng dòng; trả lại dòng thì nguyên vẹn; cắt chuỗi sau điểm neo thì `mismatchedAnchors` có điểm neo; người không phải ADMIN nhận 403; `dxos history verify` thoát với mã 1 khi chuỗi hỏng |
| `dxos plan` và `dxos apply` | Đạt | Tệp giống: `unchanged`; thêm trường: `SAFE`, áp dụng không cần `--accept`; xóa trường: `LOSS`, bị từ chối với mã 1 khi thiếu `--accept` hoặc có `--accept SAFE`, áp dụng với `--accept LOSS`; lịch sử ghi `service-account-dxos-cli`; đường dẫn tương đối tính theo thư mục gõ lệnh |
| `dxos archive` và `dxos archive check` | Đạt | 3 hồ sơ đã quyết định và 8 dòng lịch sử, số dòng và sha256 khớp; sửa một byte thì `check` báo lệch và thoát với mã 1 |
| `backup.sh` rồi `verify` với một tệp đính kèm thật | Đạt | Đủ tệp: `Verify OK`; xóa đối tượng khỏi kho rồi sao lưu lại: `Verify FAILED: files missing for attachments` kèm khóa tệp; `DXOS_FILE_DELETE_DELAY=1s` làm `backup.sh` in `WARNING` |
| Lần áp dụng đầu dừng ở `ENSURE_ROLE` rồi Hủy bỏ | Đạt | Client `dxos-m-<mô hình>` và schema mất; áp dụng lại tệp đó tạo client mới |
| Hai luồng tạo hồ sơ trong lúc áp dụng thêm một cột | Đạt | Áp dụng 200; 24 yêu cầu tạo trả 200 và 2 trả 409, không có 5xx; năm lần đổi luật quy trình khi còn hồ sơ mở đều bị 409 |
| Simulator IT/OT | Đạt | `setup`, `run` tạo sự cố; `replay` in `duplicate` và thoát với mã 0 |
| Xóa container `backend`, `dxos reconcile` | Đạt | Container chạy lại và healthy sau 18 giây; mọi container có `restart: unless-stopped` |
| `dxos stop` rồi `dxos reconcile` | Đạt | Không container nào được bật lại; xóa `deploy/.stopped` rồi `dxos reconcile` đưa cả tám service về healthy |
| `dxos reconcile install` và `uninstall` (systemd user timer) | Chưa kiểm | Phiên này bị chặn cài timer; session còn lại đã chạy install, uninstall và thấy timer xuất hiện, biến mất |
| Khởi động lại Docker daemon | Đạt | `sudo systemctl restart docker` do người dùng chạy; mọi service tự lên lại |
| Job `backup-drill` trên GitHub Actions | Đạt | Commit `d0aa2d14`: có tệp thì `verify` đạt, thêm dòng tệp không có trong kho thì `verify` thất bại |
| Job `e2e` trên GitHub Actions | Đạt | Commit `d0aa2d14`: stack rỗng dựng từ image của commit, chuỗi script chạy hết; cả job 14 phút 35 giây, riêng chuỗi script 11 phút 55 giây |
| Hai nhóm của job `e2e` chạy song song, mỗi nhóm một stack rỗng riêng | Đạt | Chạy cục bộ trên cây làm việc gồm tệp chưa commit: nhóm đầu chín script 51, 19, 71, 65, 56, 45, 26, 59, 41 trong 277 giây; nhóm sau `check_events.py` 18 và `check_bootstrap.py` 8 trong 297 giây; cả hai stack đã gỡ cùng volume |
| Hai nhóm của job `e2e` trên GitHub Actions | Đạt | Commit `ad4a3da6`: `e2e (model and process)` 434 giây, riêng script 278 giây; `e2e (events and bootstrap)` 389 giây, riêng script 268 giây; chạy song song nên cả job khoảng 7 phút 15 giây |

### Kiểm ngày 05/11/2026: áp dụng lại tệp không đổi và link tài khoản

Cây làm việc gồm cả tệp chưa commit.
Chuỗi script chạy trên hai stack thử riêng (project Compose `dxscratch`, cổng 9180) dựng rỗng, chia hai nhóm như CI; stack đã gỡ cùng volume sau khi kiểm.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify formatter:validate` | Đạt | Cả backend: dx-core 20 test, dx-engine 8, dx-monitor 3 |
| `npx tsc --noEmit` | Đạt | Không lỗi |
| Mười một script ở `deploy/dev`, hai nhóm như CI | Đạt | 459/459: nhóm một 51, 19, 71, 65, 56, 45, 26, 59, 41; nhóm hai `check_events.py` 18 và `check_bootstrap.py` 8 |
| Áp dụng lại tệp không đổi, kịch bản tạm ngoài repo | Đạt | 35/35: mô hình mới không báo lệch giả; thiếu cột và ràng buộc thì `repair` mức `SAFE`, áp dụng xong danh sách API trả 200 và kế hoạch về `unchanged`; cột thừa thì `rejected`, áp dụng bị từ chối, cột còn nguyên, mô hình không bị giữ; đổi kiểu cột thì `repair` mức `MIGRATION`, thiếu `accept` bị từ chối; xóa vai, client, nhóm đơn vị thì kế hoạch nêu đúng và áp dụng khôi phục; lịch sử có lượt chạy, vẫn phiên bản 1 |
| Kế hoạch lệch ở bảng điều khiển bằng Chrome | Đạt | Tệp không đổi khi bảng thiếu cột: thông báo, dòng "Sửa cấu trúc bảng" kèm SQL, mức An toàn, nút "Sửa phần lệch"; bấm thì áp dụng xong |
| "Tài khoản của tôi" bằng Chrome | Đạt | Mở Account Management của Keycloak ở tab mới, đã đăng nhập, có mục Account security |

### Kiểm ngày 05/11/2026: webhook theo mô hình, thứ tự giao, câu cho chat, sự kiện quản trị và nhắc hạn

Cây làm việc gồm cả tệp chưa commit.
Mọi lượt chạy trên một stack thử riêng (project Compose `dxscratch`, cổng 9180) dựng rỗng; stack đã gỡ cùng volume sau khi kiểm.
Bên nhận webhook là một máy chủ HTTP nhỏ trên máy này, không phải Mattermost hay Slack thật.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify formatter:validate` | Đạt | Cả backend, gồm `ArchitectureTest` |
| `check_events.py` | Đạt | 18/18 với đích mặc định `DXOS_WEBHOOK_URL`; sau khi tạo lại backend, gateway có thể trả 502 một lúc, nên cần `docker compose restart apisix` rồi chạy lại |
| `check_bootstrap.py` | Đạt | 8/8 |
| Đích theo mô hình | Đạt | Mô hình `webhook: ops` nhận sự kiện ở đích `ops` kèm chữ ký; đích chưa cấu hình bị từ chối khi xem kế hoạch và áp dụng (409); tên đích sai dạng báo lỗi theo dòng và cột; đổi đích giữa hai phiên bản thì sự kiện sau đó đi đích mới; đích bị gỡ thì thông điệp nằm lại hàng đợi kèm lỗi cấu hình và được giao sau khi có lại đích và bấm Thử lại |
| Thứ tự theo hồ sơ | Đạt | Dòng đầu của một hồ sơ dừng (`DXOS_OUTBOX_MAX_ATTEMPTS=1`); dòng sau của hồ sơ đó không được giao, dòng của hồ sơ khác được giao; sau Thử lại cả hai tới đúng thứ tự `eventId` |
| Đích dạng `chat` | Đạt | Nội dung chỉ có khóa `text`; hồ sơ mới của thực thể có quy trình đọc là "Việc mới"; nội dung hồ sơ không có trong câu |
| `due_soon` | Đạt | Hạn 3 phút, `DXOS_DUE_SOON_BEFORE=2m`: dòng tới sau khoảng 64 giây và chỉ một lần |
| Sự kiện quản trị | Đạt | `MODEL_APPLIED` vào đích của mô hình, ký và có header `X-Dxos-Event-Id`; thay đổi vai qua API vào đích mặc định |
| `GET /api/v1/events` | Đạt | Phân trang theo `eventId` và lọc theo mô hình; có dòng `due_soon` |

Chín script còn lại của `deploy/dev` không chạy lại ở lượt này.
Các mục từ "Đích theo mô hình" tới "`GET /api/v1/events`" chạy bằng kịch bản tạm ngoài repo.

### Kiểm ngày 05/11/2026: sửa ba lỗi của đợt team rà soát

Cây làm việc gồm cả tệp chưa commit.
Chạy trên một stack thử riêng (project Compose `dxscratch`, cổng 9180) dựng rỗng; stack đã gỡ cùng volume sau khi kiểm.

| Mục | Kết quả | Ghi chú |
|---|---|---|
| `./mvnw clean verify formatter:validate`, `npx tsc --noEmit` | Đạt | Cả backend và web |
| Tài khoản đã khóa trong danh sách người xử lý | Đạt | Hai người giữ vai xử lý: danh sách có cả hai; khóa một người thì danh sách chỉ còn người kia, giao việc cho người đã khóa bị 400, giao cho người còn hoạt động được 200 (kịch bản tạm ngoài repo) |
| Bên nhận giữ phần trả lời | Đạt | Bên nhận trả header ngay rồi giữ body 15 giây: lần gửi lỗi `TimeoutException` sau khoảng 5 giây; trước khi sửa lần gửi trả sau 15 giây (kịch bản tạm ngoài repo) |
| Sửa hồ sơ trên web bằng Chrome | Đạt | Đăng nhập người xử lý, mở hồ sơ của thực thể có hạn, đổi tiêu đề và lưu: lưu được, lịch sử ghi "Sửa thông tin" |
