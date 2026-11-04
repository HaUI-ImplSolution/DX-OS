# Plan thực hiện: Phase 14

Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự, phụ thuộc và người làm

Thứ tự ưu tiên do người dùng chốt; các WP khác vùng chạy song song.

| WP | Việc | Người làm | Phụ thuộc |
|---|---|---|---|
| WP14-01 | Khe hở khi bắt đầu áp dụng | acc1 (`registry/`, `apply/`), acc2 duyệt phần `data/` | |
| WP14-02 | Hủy bỏ lần áp dụng đầu xóa client và vai | acc1 (`apply/`), acc2 (`dx-core`) | |
| WP14-03 | Sao lưu nhất quán giữa cơ sở dữ liệu và tệp | acc2 | WP14-09 |
| WP14-04 | E2E trong CI | acc2 (`.github/` qua khóa `shared-docs`) | |
| WP14-05 | CLI `dxos plan` và `dxos apply` | acc1 (`sso/`, backend), acc2 (`deploy/dxos`) | |
| WP14-06 | Outbox có giới hạn số lần thử | acc2 (`event/`), acc1 (nhãn ở bảng điều khiển) | |
| WP14-07 | Kiểm khi ClamAV không trả lời | acc2 | |
| WP14-08 | Tự phục hồi | acc2 | |
| WP14-09 | Xóa tệp trong kho qua outbox | acc2 (`files/`, `event/`) | WP14-06 |
| WP14-10 | Lịch sử hồ sơ chống sửa | acc2 (`data/`, `event/`, migration) | |
| WP14-11 | Thống kê | acc2 (API ở `data/`), acc1 (giao diện) | |
| WP14-12 | Kết nối IT/OT | acc1 (`models/`, simulator) | WP14-05 (tài khoản dịch vụ) |
| WP14-13 | Dữ liệu lịch sử ra hồ dữ liệu | acc2 (`deploy/`) | |

## WP14-01: Khe hở khi bắt đầu áp dụng

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Yêu cầu dữ liệu đang chạy khi một lần áp dụng bắt đầu | Chạy tiếp song song với bước đổi bảng | Lần áp dụng chờ yêu cầu đó xong rồi mới giữ mô hình |
| Hồ sơ tạo giữa lúc kiểm hồ sơ mở và lúc áp dụng | Không được tính | Lần áp dụng kiểm hồ sơ mở sau khi đã giữ mô hình |

- Hướng làm: `ModelRegistry` có một khóa đọc/ghi cho mỗi mô hình.
  Trong một yêu cầu HTTP, `require()` và `all()` lấy khóa đọc trước khi kiểm mô hình bị giữ, mỗi mô hình một lần cho cả yêu cầu; `ModelUseFilter` trong `registry/` nhả hết khi yêu cầu kết thúc, kể cả khi lỗi, nên `data/`, `files/` và `process/` không phải sửa.
  Luồng nền (bộ quét quá hạn, job của Flowable, outbox) không lấy khóa; lỗi giữa lúc đổi bảng thì lượt sau chạy lại.
  `beginApply` chờ khóa ghi tối đa 30 giây chỉ để đánh dấu mô hình bị giữ; quá hạn thì trả 409 `Model <tên> is busy; try again`; yêu cầu đến sau thấy mô hình bị giữ và nhận 409 như hiện nay.
  Lần áp dụng kiểm hai đợt: tệp không đổi, `base`, lỗi của diff và mức chấp nhận kiểm trước khi giữ; xóa thực thể còn phụ thuộc, hồ sơ mở với luật quy trình và dòng vi phạm luật mới kiểm sau khi giữ.
  Bị từ chối ở đợt sau thì nhả mô hình, trừ khi mô hình đã bị giữ từ trước (tiếp tục lần áp dụng dở).
- Kết quả: kiểm thử đồng thời (tạo hồ sơ liên tục trong lúc áp dụng đổi bảng) không có yêu cầu nào lỗi 500 và không có hồ sơ lọt qua bước kiểm hồ sơ mở.

## WP14-02: Hủy bỏ lần áp dụng đầu xóa client và vai

- Hướng làm: `removeUnfinished` thêm bước `DROP_CLIENT` sau `DROP_SCHEMA`; `KeycloakAdminClient.deleteClient(clientId)` xóa client (kèm mọi client role), client không có thì không làm gì.
  Nhóm đơn vị dùng chung giữa các mô hình nên không xóa.
- Kết quả: sau Hủy bỏ, client `dxos-m-<mô hình>` không còn; áp dụng lại tệp đó tạo client mới, người từng giữ vai không có lại quyền.

## WP14-03: Sao lưu nhất quán giữa cơ sở dữ liệu và tệp

- Hướng làm: tệp chỉ bị xóa khỏi kho sau một khoảng trễ (`DXOS_FILE_DELETE_DELAY`, mặc định 24 giờ, xem WP14-09).
  `backup.sh` giữ thứ tự dump Postgres rồi chụp SeaweedFS, nên mọi dòng tệp đính kèm trong bản dump đều có tệp trong bản chụp nếu lượt sao lưu ngắn hơn khoảng trễ; `backup.sh` cảnh báo khi một lượt chạy lâu hơn khoảng trễ.
  `verify` khôi phục bản chụp vào một Docker volume tạm, khởi động một SeaweedFS tạm trên đó (filer HTTP chỉ mở trong container) và kiểm mọi khóa trong `engine.attachment` của bản dump đều có tệp.
- Kết quả: xóa tệp ngay sau khi dump vẫn cho bản sao lưu khôi phục được đủ tệp; `verify` báo `FAILED` kèm số tệp thiếu khi bản chụp thiếu tệp.

## WP14-04: E2E trong CI

- Hướng làm: job mới trong `.github/workflows/ci-infra.yml` dựng image từ commit, khởi động stack rỗng, chạy mười một script theo thứ tự của `docs/TESTING.md`, tải log các service khi lỗi.
- Kết quả: job chạy trên mỗi push và pull request vào `Khuong/main` và `main`; một script lỗi làm job đỏ.

## WP14-05: CLI `dxos plan` và `dxos apply`

- Hướng làm: realm thêm client bí mật `dxos-cli` có tài khoản dịch vụ giữ vai `ADMIN`; bí mật do `make_env.sh` sinh vào `.env`.
  `dxos plan <tệp>` in kế hoạch; `dxos apply <tệp> [--accept MỨC]` lấy `fromVersion` từ kế hoạch làm `base`, không bao giờ tự chấp nhận mức cao hơn mức người gọi truyền; mã thoát khác 0 khi bị từ chối.
  Lịch sử áp dụng ghi tên tài khoản dịch vụ.
- Kết quả: áp dụng một thay đổi mất dữ liệu không có `--accept LOSS` thì bị từ chối với mã thoát 1; có thì xong.

## WP14-06: Outbox có giới hạn số lần thử

- Hướng làm: thông điệp lỗi quá `DXOS_OUTBOX_MAX_ATTEMPTS` lần (mặc định 20) thì dừng (`next_attempt_at = 'infinity'`); nút Thử lại cho thêm một lần; danh sách outbox có trạng thái `stopped`, mỗi dòng có cờ `stopped`.
  Payload sự kiện `record.event` thêm `schemaVersion: 1`, cùng kiểu tên với các khóa khác.
- Kết quả: bên nhận luôn lỗi thì thông điệp dừng sau 20 lần và hiện "Đã dừng" ở tab Hàng đợi sự kiện.

## WP14-07: Kiểm khi ClamAV không trả lời

- Hướng làm: `check_demo.py`, khi bật quét, dừng `clamav`, tải tệp lên, mong 503 `ERR_SCAN_UNAVAILABLE` và không có tệp nào được lưu, rồi bật lại.
- Kết quả: bài kiểm đạt với `dxos av on`.

## WP14-08: Tự phục hồi

- Hướng làm: mọi service chạy lâu có `restart: unless-stopped`.
  `dxos reconcile` chạy `docker compose up -d --no-build` theo image đã ghim trong `.env`, nên container bị xóa được tạo lại và container lệch cấu hình được tạo lại theo khai báo; mỗi lượt ghi một dòng vào `deploy/reconcile.log`.
  `dxos stop` để lại tệp đánh dấu `deploy/.stopped`, nên `dxos reconcile` không bật lại stack đã dừng có chủ ý; `dxos start` xóa tệp đó.
  `dxos reconcile install` cài một systemd user timer chạy mỗi phút; `dxos reconcile uninstall` gỡ.
- Kết quả: xóa container `backend` thì trong một phút nó chạy lại; khởi động lại Docker thì mọi service tự lên.

## WP14-09: Xóa tệp trong kho qua outbox

- Hướng làm: thay vì xóa ngay sau commit, `AttachmentCleaner` ghi một thông điệp chủ đề `file.delete` vào outbox trong cùng giao dịch, với `next_attempt_at = now() + DXOS_FILE_DELETE_DELAY`; handler xóa đối tượng, đối tượng không có thì coi như xong; lỗi thì thử lại theo outbox.
  Tệp lưu bởi giao dịch rollback vẫn xóa ngay, vì không có dòng nào trỏ tới.
- Kết quả: kho tạm ngừng lúc xóa thì thông điệp được thử lại tới khi xóa xong; không còn đối tượng mồ côi.

## WP14-10: Lịch sử hồ sơ chống sửa

- Hướng làm: migration `V7` thêm cột `prev_hash`, `hash` vào `engine.record_event`.
  `EventLog` ghi mỗi dòng dưới một khóa advisory theo giao dịch, với `hash = sha256(prev_hash || nội dung chuẩn hóa của dòng)`; các dòng cũ được tính bù khi backend khởi động.
  Payload `record.event` mang `hash`, nên bên nhận webhook giữ được neo ngoài cơ sở dữ liệu; backend còn ghi một điểm neo (id, hash, thời điểm) mỗi giờ vào đối tượng `history/anchors.jsonl` của kho đối tượng, nằm ngoài Postgres và đã có trong bản sao lưu.
  `GET /api/v1/history/verify` (ADMIN) tính lại chuỗi, so với điểm neo, trả dòng đầu tiên bị sửa nếu có; `POST /api/v1/history/anchor` ghi điểm neo ngay; `dxos history verify` và `dxos history anchor` gọi hai API này.
- Kết quả: sửa, xóa hay chèn một dòng `record_event` bằng SQL thì `verify` chỉ ra dòng đó; sửa cả chuỗi thì lệch với điểm neo.

## WP14-11: Thống kê

- Hướng làm: `GET /api/v1/data/{model}/{entity}/stats` nhận cùng bộ lọc với danh sách, áp cùng điều kiện quyền, trả `total`, `byStatus`, `overdue` và `byUnit` (khi thực thể có trường đơn vị), kèm `computedAt`.
  Giao diện có trang thống kê cho mỗi thực thể có quy trình; mỗi số mở ra danh sách đã lọc tương ứng.
- Kết quả: người dùng `own` chỉ thấy số của hồ sơ mình được xem; số khớp với số dòng của danh sách cùng bộ lọc.

## WP14-12: Kết nối IT/OT

- Hướng làm: một mẫu `work_item` có trường thiết bị và mã quan sát duy nhất; một simulator nằm ngoài lõi (`examples/itot-simulator`) sinh quan sát thiết bị và gọi API tạo hồ sơ khi vượt ngưỡng, bằng một tài khoản dịch vụ giữ vai của mô hình; `simulator.py setup` tự tạo client `dxos-itot-<mô hình>` và cấp vai đó từ tài khoản quản trị Keycloak của `.env`; cùng một quan sát gửi lại thì nhận 400 (lỗi duy nhất của trường mã quan sát) và không tạo trùng.
- Kết quả: chạy simulator thì hồ sơ sự cố xuất hiện trong hộp việc của người xử lý, lịch sử hồ sơ ghi mã quan sát.

## WP14-13: Dữ liệu lịch sử ra hồ dữ liệu

- Hướng làm: `dxos archive <mô hình> [--before NGÀY]` dùng DuckDB 1.4.1 (MIT) chạy trong container, gắn Postgres ở chế độ chỉ đọc bằng tài khoản chính của `.env` (`report_ro` không đọc được `engine.record_event`), xuất hồ sơ đã tới trạng thái cuối (`decided_at` có giá trị) và lịch sử của chúng ra Parquet trong `deploy/data/archive/<mô hình>/<run>/`, kèm `manifest.json` (phiên bản mô hình, số dòng, sha256 từng tệp); dữ liệu OLTP giữ nguyên.
  `dxos archive check <thư mục>` đọc lại từng tệp và so với manifest.
  DuckDB tải extension `postgres` từ mạng ở lần chạy đầu.
- Kết quả: đọc lại bằng DuckDB ra đúng số dòng của manifest; sha256 khớp.

## Thay đổi hợp đồng

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `GET /api/v1/data/{model}/{entity}/stats` | Mới | Không |
| Endpoint | `GET /api/v1/history/verify`, `POST /api/v1/history/anchor` | Mới, ADMIN | Không |
| Endpoint | `GET /api/v1/outbox` | Thêm trạng thái `stopped` | Không |
| Bảng DB | `engine.record_event` | Cột `prev_hash`, `hash` | Không |
| Sự kiện | `record.event` | Thêm `schemaVersion`, `hash` | Không |
| Biến môi trường | `DXOS_OUTBOX_MAX_ATTEMPTS`, `DXOS_FILE_DELETE_DELAY` (có mặc định), `DXOS_CLI_SECRET` (bắt buộc) | Mới | `.env` cũ phải thêm `DXOS_CLI_SECRET`; `install.sh --upgrade` tự thêm |
| Keycloak | Client `dxos-cli` | Mới | Không; realm đã import thì thêm bằng tay hoặc dựng lại |

## Dữ liệu, migration và rollback

- Migration `V7` của engine thêm hai cột cho phép rỗng; backend tính bù khi khởi động.
- Rollback: xóa hai cột; chuỗi hash mất, lịch sử còn nguyên.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Khóa đọc/ghi trong bộ nhớ, khóa ghi chỉ giữ lúc đánh dấu mô hình bị giữ | Chỉ chạy một bản backend; yêu cầu không phải chờ suốt lần áp dụng |
| D2 | Sao lưu nhất quán bằng khoảng trễ khi xóa tệp, không dừng dịch vụ | Không có thời gian chết; dùng lại outbox |
| D3 | CLI dùng client credentials của `dxos-cli`, bí mật ở `.env` trên máy chủ | Chạy được trong pipeline GitOps; đăng nhập qua trình duyệt để sau |
| D4 | Chuỗi hash toàn cục, neo qua webhook và tệp điểm neo trên máy chủ | Phát hiện sửa bởi người chỉ có quyền trên cơ sở dữ liệu; không cần dịch vụ ngoài |
| D5 | IT/OT và hồ dữ liệu nằm ngoài lõi | Lõi giữ headless và không biết về nghiệp vụ |
