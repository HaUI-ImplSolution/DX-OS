# Vận hành

Tài liệu cho người cài và chạy stack hằng ngày: cài đặt và lệnh `dxos`, quản trị người dùng, kiểm tra trước khi chạy, tài nguyên, giám sát từ ngoài, sao lưu và khôi phục, quét mã độc.
Log, giới hạn tốc độ, sự kiện đăng nhập, audit và màn hình giám sát ở [`MONITORING.md`](./MONITORING.md).
Webhook sự kiện, API kéo sự kiện, lịch sử chống sửa và hồ dữ liệu ở [`INTEGRATIONS.md`](./INTEGRATIONS.md).

## Quản trị người dùng

Tab Người dùng của bảng điều khiển (`/console`, ADMIN) cho tìm người dùng, xem vai theo từng ứng dụng, gán và gỡ vai và đơn vị của các mô hình đang chạy, tạo người dùng, khóa và mở, đặt lại mật khẩu.
Mật khẩu tạm do hệ thống sinh, hiện một lần và người dùng phải đổi ở lần đăng nhập đầu.
Vai `ADMIN` của nền tảng không cấp được ở tab này; cấp ở Keycloak Admin Console (Users, Role mapping).
Người dùng đăng nhập bằng nhà cung cấp ngoài, chính sách mật khẩu và MFA do Keycloak quản lý.
Backend cần tài khoản dịch vụ `dxos-backend-admin` có quyền client và scope `kc-admin-api`; `update.sh` gán scope này cho Keycloak dựng từ trước (xem [`sso/README.md`](../sso/README.md)).

## Cài đặt và lệnh `dxos`

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

Bộ cài ([`get.sh`](./get.sh), phát hành kèm mỗi bản với tên `dxos-install.sh`) tải gói của bản phát hành, so với SHA-256 trong `dx-os-X.Y.Z.sha256`, rồi chạy [`install.sh`](./install.sh) của gói đó.
Gói sai SHA-256 thì không cài gì.
`install.sh` chép `deploy/` và `sso/realm-dxos.json` vào `~/.dxos` (đổi bằng `--prefix` hoặc biến `DXOS_HOME`), sinh `deploy/.env` bằng [`make_env.sh`](./make_env.sh), đặt `COMPOSE_PROJECT_NAME=dxos` cùng image GHCR của bản đó, và tạo liên kết `~/.local/bin/dxos`.
Chọn bản bằng `--version vX.Y.Z`; cài từ gói có sẵn trên máy bằng `--file dx-os-X.Y.Z.tar.gz`.

| Lệnh | Việc |
|---|---|
| `dxos start [tệp...]` | Chạy `doctor.sh`, tải image còn thiếu, khởi động, chờ mọi dịch vụ sẵn sàng; realm chưa có ai vai `ADMIN` thì tạo tài khoản `admin` với mật khẩu tạm in ra một lần; có tệp mô hình thì đưa các tệp vào và in kết quả của từng tệp (xem dưới); rồi in các địa chỉ |
| `dxos stop` | Dừng các container, giữ dữ liệu |
| `dxos status` | Trạng thái từng dịch vụ, các địa chỉ và trạng thái quét mã độc |
| `dxos logs [dịch vụ]` | Theo dõi log |
| `dxos admin` | Đặt mật khẩu tạm mới cho `admin`, tạo tài khoản nếu chưa có |
| `dxos update [vX.Y.Z]` | Cài bản mới hơn (mặc định bản mới nhất): thay tệp chạy, giữ `.env` và `data/`, thêm biến mới, rồi chạy `update.sh`; script này gán cho `dxos-backend-admin` scope `kc-admin-api` mà Keycloak 26.8 đòi (`keycloak-admin-scope.sh`, chạy lại được), và nên sao lưu trước vì cơ sở dữ liệu của Keycloak không hạ phiên bản được |
| `dxos doctor`, `dxos backup` | Chạy `doctor.sh`, `backup.sh` |
| `dxos av [on\|off]` | Bật hoặc tắt quét mã độc tệp tải lên (xem dưới); không có đối số thì in trạng thái quét |
| `dxos plan <tệp>`, `dxos apply <tệp> [--accept MỨC]` | In kế hoạch của một tệp mô hình, hoặc áp dụng nó (xem dưới) |
| `dxos history verify`, `dxos history anchor` | Kiểm lịch sử hồ sơ chưa bị sửa, hoặc ghi điểm neo ngay (xem [`INTEGRATIONS.md`](./INTEGRATIONS.md#lịch-sử-chống-sửa)) |
| `dxos reconcile [install\|uninstall]` | Đưa stack về đúng khai báo; `install` chạy mỗi phút bằng systemd user timer (xem dưới) |
| `dxos archive <mô hình>`, `dxos archive check <thư mục>` | Xuất hồ sơ đã xong ra Parquet, hoặc kiểm một bản xuất (xem [`INTEGRATIONS.md`](./INTEGRATIONS.md#hồ-dữ-liệu)) |
| `dxos uninstall [--purge]` | Xóa container và lệnh `dxos`; `--purge` xóa cả dữ liệu và thư mục cài, phải gõ xác nhận |

### Tệp mô hình lúc khởi động

`dxos start <tệp>...` chép các tệp vào `deploy/model/` (chỉ giữ các tệp vừa đưa vào), ghi tên chúng theo thứ tự, cách nhau bằng dấu phẩy, vào `DXOS_MODEL_FILE` trong `.env` và khởi động lại backend nếu nó đang chạy.
Tên tệp chỉ gồm chữ, số, dấu chấm, gạch ngang và gạch dưới, và hai tệp không được trùng tên.
Backend chờ Keycloak trả lời (tối đa 300 giây) rồi áp dụng lần lượt từng tệp với người thực hiện `bootstrap`, và chỉ khi tệp chưa từng được áp dụng, kế hoạch ở mức an toàn không có từ chối hay dòng vi phạm, và mô hình không có lần áp dụng dở.
Mỗi tệp ghi đúng một dòng `Model bootstrap: <kết quả> <chi tiết>` ở mỗi lần khởi động, và `dxos start` in từng dòng:

| Kết quả | Ý nghĩa |
|---|---|
| `applied` | Đã áp dụng, kèm tên mô hình và phiên bản |
| `unchanged` | Mô hình đang chạy chính tệp này |
| `skipped` | Không áp dụng, kèm lý do; xử lý ở bảng điều khiển |
| `failed` | Tệp không đọc được hoặc không qua kiểm tra (kèm dòng và cột), Keycloak không trả lời kịp, hoặc việc áp dụng lỗi giữa chừng |

Backend chạy ở mọi kết quả, và một tệp lỗi không chặn các tệp sau nó.
Hai tệp cùng tên mô hình thì tệp đứng sau là `skipped`.
`dxos start` chờ đủ dòng kết quả của mọi tệp, tối đa 300 giây (đổi bằng `DXOS_MODEL_TIMEOUT`), rồi thoát với mã 4; có tệp `failed` thì thoát với mã 3.
Backend chỉ đọc tệp khi khởi động, nên `dxos start` không tham số trên hệ thống đang chạy không đọc lại tệp.
Tệp hỏng vẫn nằm trong `.env` và được báo lại ở mỗi lần backend khởi động cho đến khi sửa hoặc thay.
Cấu hình đặt tên các tệp ở `DXOS_MODEL_FILE`, cách nhau bằng dấu phẩy, và thư mục chứa chúng ở `ENGINE_MODEL_DIR` (`/models` trong container).

### Đổi mô hình bằng dòng lệnh

`dxos plan <tệp>` in kế hoạch của tệp mô hình mà không đổi gì; mã thoát 1 khi kế hoạch bị từ chối.
`dxos apply <tệp> [--accept SAFE|MIGRATION|LOSS]` lấy kế hoạch rồi áp dụng với phiên bản của kế hoạch làm `base`.
Kế hoạch có mức cao hơn `--accept` (mặc định `SAFE`) thì bị từ chối với mã thoát 1 và không đổi gì; lệnh không bao giờ tự nâng mức.
Tệp giống mô hình đang chạy và không có gì lệch thì in "Nothing to apply" và thoát với mã 0.
Tệp giống nhưng bảng hoặc Keycloak đã lệch khỏi tệp thì kế hoạch ghi `repair`; `dxos apply` sửa phần lệch, và cần `--accept MIGRATION` khi kế hoạch ở mức đó.
Việc kiểm lệch chỉ chạy khi xem kế hoạch hoặc áp dụng, không chạy lúc backend khởi động; bảng hoặc cột thừa do sửa tay bị từ chối chứ không bị xóa, vai thừa ở client của mô hình và nhóm đơn vị thừa không bị đụng, còn chỉ mục hay ràng buộc thừa bị xóa khi áp dụng với mức `MIGRATION`.
Đường dẫn tương đối tính theo thư mục gõ lệnh.

Lệnh đăng nhập bằng client `dxos-cli` của realm, dùng `DXOS_CLI_SECRET` trong `.env`; tài khoản dịch vụ của client giữ vai `ADMIN`, và lịch sử áp dụng ghi tên nó (`service-account-dxos-cli`).
Bí mật nằm trong `.env` của máy chủ, nên chạy được trong pipeline GitOps trên chính máy đó.
Realm đã import từ trước chưa có client này: thêm client `dxos-cli` ở Keycloak Admin Console hoặc dựng lại realm.

### Lần áp dụng dừng giữa chừng

Mô hình có lần áp dụng dừng giữa chừng bị đóng với người dùng và hiện nhãn "Đang dừng dở" ở bảng điều khiển.
Chọn Tiếp tục để hoàn tất, hoặc Hủy bỏ để đưa các bảng về phiên bản đang chạy; chạy `deploy/backup.sh` trước khi Hủy bỏ nếu cần giữ dữ liệu.
Nhóm đơn vị mà lần dở đã tạo ở Keycloak không bị xóa, vì các mô hình dùng chung nhóm theo tên; quản trị viên xóa nhóm rỗng ở Keycloak nếu muốn.
Trước khi áp dụng thay đổi mức mất dữ liệu (`accept=LOSS`), chạy `deploy/backup.sh`.

### Tệp bị gỡ khỏi hồ sơ

Gỡ hoặc thay một tệp, xóa hồ sơ hay xóa thực thể thì đối tượng rời kho sau `DXOS_FILE_DELETE_DELAY` (mặc định 24 giờ).
Backend ghi một thông điệp `file.delete` vào outbox trong cùng giao dịch, đến hạn thì xóa đối tượng.
Đối tượng đã mất thì thông điệp coi như xong; kho đang ngừng thì thông điệp được thử lại.
Một lượt sao lưu ngắn hơn khoảng trễ này luôn có đủ tệp cho mọi dòng tệp đính kèm trong bản dump.
Tệp của giao dịch bị hủy (đã vào kho nhưng dòng không được ghi) bị xóa ngay.

### Tự phục hồi

Mọi service chạy lâu có `restart: unless-stopped`, nên Docker khởi động lại chúng khi tiến trình thoát hoặc máy khởi động lại.
`dxos reconcile` chạy `docker compose up -d --no-build` theo các image đã ghim trong `.env`: container bị xóa được tạo lại, container lệch cấu hình được tạo lại theo khai báo, container đang đúng thì để nguyên.
Mỗi lượt có thay đổi ghi một dòng vào `deploy/reconcile.log`; lượt đến khi lượt trước còn chạy thì bỏ qua.
`update.sh` và `backup.sh` giữ cùng khóa `deploy/.reconcile.lock`, nên khi một lần cập nhật hoặc sao lưu đang chạy thì `dxos reconcile` bỏ qua lượt; hai script đợi tối đa 5 phút để lấy khóa rồi báo lỗi.
`dxos stop` để lại tệp `deploy/.stopped`, và `dxos reconcile` không bật lại stack có tệp này; `dxos start` xóa nó.

`dxos reconcile install` cài `dxos-reconcile.timer` (systemd user timer) chạy lệnh trên mỗi phút, `dxos reconcile uninstall` gỡ.
Máy không có systemd thì chạy `dxos reconcile` từ cron mỗi phút.
Timer của user chỉ chạy khi user còn phiên đăng nhập, trừ khi bật `loginctl enable-linger <user>`.

## Kiểm tra trước khi chạy

`./doctor.sh` (trong `deploy/`) chỉ đọc, không đổi gì và không in giá trị nào của `.env`.
Nó kiểm Docker và Compose, RAM, ổ đĩa, `deploy/.env` và các cổng.
`.env` được so với các biến `${NAME:?}` mà Compose bắt buộc phải có: biến thiếu hoặc để trống bị báo `FAIL`.
Biến có trong `.env` mà `.env.example` không liệt kê bị báo `WARN`.
Các cổng đã bị chiếm chỉ được kiểm khi stack chưa chạy.
Quét mã độc được báo là bật hay tắt; `STORAGE_SCAN_HOST=clamav` mà `COMPOSE_PROFILES` không có `av` bị báo `FAIL`, vì ClamAV sẽ không chạy và mọi tệp tải lên bị từ chối.
Thoát với mã 1 khi có `FAIL`, nên chạy trước `./update.sh` và `docker compose up -d` trên máy chủ.

## Tài nguyên và số bản sao

Đo ngày 04/11/2026 trên máy 12 CPU, 31 GB RAM, bằng `docker stats` mỗi 5 giây khi chạy tám script ở `deploy/dev` trên stack mới dựng.
Stack gồm tám service, không bật `clamav`.
Số đo trước khi nâng nginx lên 1.30 và Keycloak lên 26.8.2, chưa đo lại.

| Service | RAM lúc rảnh (MiB) | RAM đỉnh (MiB) | CPU đỉnh (% của một nhân) |
|---|---|---|---|
| `backend` | 493 | 529 | 417 |
| `keycloak` | 424 | 517 | 365 |
| `monitor` | 287 | 296 | 7 |
| `apisix` | 210 | 222 | 6 |
| `postgres` | 86 | 115 | 166 |
| `seaweedfs` | 68 | 71 | 4 |
| `frontend`, `portal` | 10 mỗi service | 11 mỗi service | 2 |

Cả stack dùng khoảng 1,6 GiB RAM lúc rảnh và tối đa khoảng 1,7 GiB trong lúc chạy kiểm thử.
Image chiếm gần 1 GiB đĩa; dữ liệu Postgres sau lần chạy kiểm thử là khoảng 100 MiB.
CPU chỉ vọt lên ngắn hạn ở `backend` và `keycloak`; chưa đo với nhiều người dùng đồng thời hay với ít nhân hơn.

`backend` chạy một bản.
Chạy hai bản sau gateway thì mỗi bản giữ danh sách mô hình riêng trong bộ nhớ: sau một lần áp dụng, bản kia trả 404 cho mô hình mới và tiếp tục phục vụ mô hình đang bị giữ, cho đến khi khởi động lại cả hai.

## Giám sát từ ngoài

`./online-check.sh` (trong `deploy/`) gọi web, portal, trang đăng nhập và `/api/v1/system/status` qua địa chỉ công khai, rồi thoát với mã 1 khi một đường không trả 200 hoặc khi `backend` hoặc `storage` không `UP`.
Địa chỉ lấy từ `GATEWAY_PUBLIC_URL` trong `.env`; biến `PUBLIC_URL` trong shell ghi đè để kiểm địa chỉ khác.
Chạy theo lịch bằng cron, ví dụ mỗi 12 giờ: `0 */12 * * * cd /đường/dẫn/deploy && ./online-check.sh >> online-check.log 2>&1`.
Khi `.env` có `ALERT_WEBHOOK_URL_ADMIN` (hoặc `ALERT_WEBHOOK_URL`), script gửi một tin vào kênh khi site chuyển sang lỗi và một tin khi site trở lại, không gửi lặp trong lúc còn lỗi.
Script chạy trên chính máy chủ nên không phát hiện được việc máy chủ tắt hẳn; muốn phát hiện, thêm một dịch vụ ping ngoài (ví dụ UptimeRobot) trỏ vào `/api/v1/system/status`.

## Sao lưu và khôi phục

`./backup.sh` (trong `deploy/`) chụp một bản sao lưu mã hóa của những gì không dựng lại được từ repo.
Gồm toàn bộ Postgres (dữ liệu nghiệp vụ, tài khoản Keycloak) và tệp trong SeaweedFS.
Script dùng restic chạy trong container nên chỉ cần Docker.
Bản sao lưu khử trùng lặp và mã hóa bằng `BACKUP_PASSWORD`.
Cất mật khẩu này ở nơi khác máy chủ, vì mất nó là không đọc lại được.

```bash
cd deploy
./backup.sh              # chụp, rồi giữ 7 bản theo ngày, 4 theo tuần, 6 theo tháng
./backup.sh verify       # nạp bản dump mới nhất vào một Postgres tạm và kiểm tra
./backup.sh snapshots    # liệt kê các bản
```

`verify` in số mô hình đã áp dụng và tổng số dòng trong các bảng `m_*` của bản dump.
Mô hình có phiên bản trong `engine.model_version` mà không có bảng nào trong schema `m_<mô hình>` làm `verify` báo `FAILED` kèm tên mô hình.
`verify` còn khôi phục bản chụp của kho vào một Docker volume tạm và khởi động một SeaweedFS tạm trên đó (filer HTTP chỉ mở trong container), rồi kiểm mọi khóa trong `engine.attachment` của bản dump đều có tệp.
`verify` không so nội dung từng tệp, không kiểm quy trình đang chạy và không kiểm quyền sau khi khôi phục.
Thiếu tệp thì `verify` báo `FAILED` kèm tên các tệp thiếu và thoát với mã 1.
`backup.sh` dump Postgres rồi mới chụp kho, và tệp bị gỡ chỉ rời kho sau `DXOS_FILE_DELETE_DELAY`, nên bản sao lưu luôn có tệp của mọi dòng trong bản dump, miễn là cả hai bước ngắn hơn khoảng đó.
Lượt chạy lâu hơn khoảng đó thì `backup.sh` in `WARNING`; khi đó tăng `DXOS_FILE_DELETE_DELAY`.

Kho nằm ở `deploy/backups` (đổi bằng `BACKUP_DIR`) hoặc ở một kho restic từ xa (`BACKUP_REPOSITORY`, ví dụ `s3:...`).
Kho cùng máy với dữ liệu không cứu được khi mất máy, nên chép nó ra ngoài (rsync, rclone) hoặc dùng kho từ xa.
Chạy theo lịch bằng cron, ví dụ mỗi đêm: `0 2 * * * cd /đường/dẫn/deploy && ./backup.sh >> backups/backup.log 2>&1`, và chạy `verify` mỗi tuần.
Máy chủ dùng tệp env khác `.env` thì đặt `ENV_FILE=.env.dev ./backup.sh`.
CI chạy một lượt backup và verify trên Postgres thật có một mô hình hai dòng và một tệp đính kèm thật ở job `Back up and restore` của workflow `Platform`, rồi thêm một dòng tệp không có trong kho và mong `verify` thất bại.

**Khôi phục** (máy mới hoặc sau khi mất dữ liệu):

1. Dựng lại stack từ repo với cùng `deploy/.env`, giữ Postgres chạy và dừng các dịch vụ còn lại: `docker compose up -d postgres` rồi `docker compose stop` các dịch vụ khác.
2. Lấy bản sao lưu ra thư mục tạm: `./backup.sh restore /tmp/dxos-restore`.
3. Nạp Postgres: `gunzip -c /tmp/dxos-restore/stage/postgres.sql.gz | docker compose exec -T postgres psql -U "$POSTGRES_USER" postgres`.
   Hai dòng lỗi `role "..." already exists` và `database "..." already exists` là bình thường vì tài khoản quản trị và database chính đã có sẵn.
   Phần còn lại phải nạp không lỗi.
4. Chép `data/seaweedfs` từ `/tmp/dxos-restore/data/` vào `deploy/data/` (các tệp thuộc root nên dùng `sudo`), rồi `docker compose up -d`.

## Quét mã độc tệp tải lên

Khi bật, mọi tệp đính kèm được ClamAV quét trước khi xử lý và lưu.
Tệp nhiễm bị từ chối với HTTP 422 `ERR_FILE_INFECTED` và lần chặn được ghi vào `audit_log` (`FILE_INFECTED`, kèm tên mẫu mã độc).
ClamAV không trả lời thì tệp không được lưu và người dùng nhận HTTP 503 `ERR_SCAN_UNAVAILABLE`, vì tệp chưa quét không được coi là sạch.
Tắt mặc định vì ClamAV cần khoảng 1 GB RAM.
Bật bằng `dxos av on`, tắt bằng `dxos av off`: lệnh sửa `COMPOSE_PROFILES` và `STORAGE_SCAN_HOST` trong `.env`, rồi khởi động hoặc gỡ ClamAV và tạo lại backend nếu stack đang chạy.
Không dùng `dxos` thì bỏ dấu `#` ở `COMPOSE_PROFILES=av` và `STORAGE_SCAN_HOST=clamav` trong `deploy/.env`, rồi `docker compose up -d`.
Chữ ký virus tải về lần đầu mất vài phút và được giữ trong volume `clamav_data`.
Mỗi lần clamd nạp lại chữ ký (hằng ngày và sau khi khởi động) nó không quét được khoảng 10 giây, và người dùng gặp 503 trong lúc đó; chờ rồi tải lại.
Thử bằng tệp EICAR (chuỗi kiểm thử chuẩn của ngành diệt virus): tệp chỉ gồm đúng chuỗi đó, đổi tên `.pdf` rồi đính vào một yêu cầu; `deploy/dev/check_demo.py` tự làm bước này khi `STORAGE_SCAN_HOST` có giá trị.
