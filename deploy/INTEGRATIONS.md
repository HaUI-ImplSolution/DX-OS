# Tích hợp với hệ thống ngoài

Tài liệu cho người nối hệ thống ngoài với DX-OS: webhook sự kiện, API kéo sự kiện, lịch sử chống sửa và hồ dữ liệu.
Cài đặt và các lệnh `dxos` ở [`OPERATIONS.md`](./OPERATIONS.md); log và audit ở [`MONITORING.md`](./MONITORING.md).

## Webhook sự kiện

Mỗi mô hình gửi các dòng lịch sử của hồ sơ tới một đích.
Tệp mô hình chọn đích bằng `webhook: <tên đích>` ở đầu tệp, tên theo dạng `[a-z][a-z0-9_]*`.
Địa chỉ và bí mật của từng đích nằm trong `deploy/webhooks/targets.yml`, tệp này ngoài git vì địa chỉ có thể chứa khóa; mẫu ở `deploy/webhooks/targets.example.yml`:

```yaml
targets:
  ops:
    url: https://chat.example.org/hooks/abc
    secret: change-me
    format: chat
```

`format` là `json` (thông điệp nguyên văn, mặc định) hoặc `chat` (xem "Câu cho Mattermost và Slack" dưới đây); đích mặc định đặt dạng bằng `DXOS_WEBHOOK_FORMAT`.

Mô hình không ghi `webhook` thì dùng `DXOS_WEBHOOK_URL` trong `.env`; để trống và không ghi đích thì mô hình không gửi gì.
Chỉ dòng sinh ra sau khi mô hình có đích mới được gửi.
Mô hình ghi tên đích chưa có trong tệp thì xem kế hoạch báo bị từ chối và áp dụng trả 409.
Tệp `targets.yml` được nạp lại khi đổi, không cần tạo lại backend; một đích sai tên hoặc địa chỉ bị bỏ qua và ghi vào log.
Mô hình không còn đích nào (không ghi `webhook` và `DXOS_WEBHOOK_URL` để trống) thì thông điệp đang chờ của nó bị bỏ và không được gửi khi bật lại; phần đã lỡ lấy bằng API kéo sự kiện ở dưới.
Đích bị gỡ khỏi tệp sau khi mô hình đã chạy thì thông điệp của mô hình đó vẫn vào hàng đợi, báo lỗi cấu hình ở tab Hàng đợi sự kiện, và được giao khi đích có lại và bấm Thử lại.
Mỗi dòng (tạo, sửa, xóa, hành động của quy trình, giao việc, thêm hoặc bỏ tệp, quá hạn) được ghi vào outbox trong giao dịch của thay đổi, nên thay đổi bị hủy thì không có gì được gửi.
Backend gửi `POST` JSON với các trường:

| Trường | Ý nghĩa |
|---|---|
| `eventId` | Số của dòng lịch sử, tăng dần; cũng nằm ở header `X-Dxos-Event-Id` |
| `model`, `entity`, `recordId` | Hồ sơ nào |
| `action` | Hành động, ví dụ `created`, `updated`, `deleted`, `assigned`, `due_soon`, `overdue`, `file_added`, `completion_skipped`, `auto_closed`, hoặc tên hành động của quy trình |
| `fromStatus`, `toStatus` | Trạng thái trước và sau |
| `actor`, `actorName` | Mã người dùng và tên người làm, gửi ra ngoài hệ thống; `system` với dòng quá hạn |
| `at` | Thời điểm, giờ UTC |
| `schemaVersion` | Phiên bản của dạng thông điệp, hiện là 1 |
| `hash` | Hash của dòng trong chuỗi lịch sử (xem "Lịch sử chống sửa"); bên nhận giữ lại thì có điểm neo ngoài cơ sở dữ liệu |

Thông điệp không có nội dung hồ sơ, ghi chú hay tên tệp; bên nhận cần thì gọi API với quyền của mình.
Có `DXOS_WEBHOOK_SECRET` thì mỗi yêu cầu kèm `X-Dxos-Signature: sha256=<hex>`, là HMAC-SHA256 của nguyên văn nội dung; bên nhận tính lại rồi so.
Bên nhận trả 2xx trong 5 giây là xong; kết quả khác thì thông điệp được gửi lại với thời gian chờ gấp đôi mỗi lần, tối đa 5 phút.
Thông điệp lỗi quá `DXOS_OUTBOX_MAX_ATTEMPTS` lần (mặc định 20) thì dừng, không tự gửi nữa; nút Thử lại cho nó thêm một lần.
Các dòng của một hồ sơ được giao theo thứ tự: dòng sau chờ khi dòng cũ hơn của cùng hồ sơ chưa giao, kể cả khi dòng cũ đã dừng và chờ Thử lại.
Dòng của các hồ sơ khác nhau không chờ nhau, và một dòng có thể đến hai lần, nên bên nhận khử trùng theo `eventId`.
Thông điệp chưa gửi, đã dừng, lỗi gần nhất và nút thử lại nằm ở tab Hàng đợi sự kiện; thông điệp đã gửi bị xóa sau 30 ngày.
Đổi `DXOS_WEBHOOK_URL`, `DXOS_WEBHOOK_SECRET` hay `DXOS_WEBHOOK_FORMAT` thì tạo lại backend bằng `docker compose up -d backend`.

Bên nhận lỡ thông điệp (ngừng chạy, hoặc thông điệp đã dừng) lấy lại bằng `GET /api/v1/events?after=<eventId đã giữ>&size=<tối đa 500>&model=<tùy chọn>` với token ADMIN.
Các dòng đọc thẳng từ lịch sử, cùng dạng với thông điệp webhook, từ cũ tới mới; `next` là `eventId` để hỏi tiếp và `more` cho biết còn dòng.
Lệnh này dùng được cả khi chưa đặt đích nào.
Thay đổi của quản trị lấy lại bằng `GET /api/v1/events/admin?after=<eventId đã giữ>&size=<tối đa 500>&model=<tùy chọn>`, cùng quy ước `next` và `more`; mỗi phần tử có đúng dạng thông điệp `admin.event`.

Hồ sơ có hạn xử lý dài hơn `DXOS_DUE_SOON_BEFORE` (mặc định `24h`, `0` để tắt) nhận một dòng `due_soon` khi còn chừng đó thời gian trước hạn, đúng một lần cho mỗi hạn.
Hạn không dài hơn khoảng này thì không có dòng `due_soon`.

### Câu cho Mattermost và Slack

Đích có `format: chat` nhận `{"text": "<một câu>"}` thay cho thông điệp nguyên văn; Mattermost và Slack đều nhận dạng này ở địa chỉ webhook đến của họ.
Câu có tên mô hình, thực thể, số hồ sơ và trạng thái, không có nội dung hồ sơ.
Hồ sơ của thực thể có quy trình vừa được tạo hoặc nộp lại đọc là "Việc mới", dòng `due_soon` là "Sắp đến hạn xử lý", dòng `overdue` là "Quá hạn xử lý", dòng giao việc là "Được giao"; các dòng khác ghi hành động và trạng thái trước, sau.

### Việc áp dụng mô hình và thay đổi tài khoản

Các thay đổi này gửi thông điệp chủ đề `admin.event` với `eventId`, `kind`, `model`, `actor`, `actorName`, `at`, `facts` và `schemaVersion`; `eventId` là số của dòng trong nhật ký thay đổi của quản trị, tăng dần, và cũng nằm ở header `X-Dxos-Event-Id`.
Số này đếm riêng, không cùng dãy với `eventId` của dòng lịch sử hồ sơ, nên bên nhận khử trùng theo cặp loại thông điệp và `eventId`.
`kind` là `MODEL_APPLIED`, `MODEL_ROLLED_BACK`, `MODEL_RESUMED`, `MODEL_ABANDONED`, hoặc tên hành động của audit như `USER_ROLE_GRANTED`.
Thông điệp của một mô hình vào đích của mô hình đó; thay đổi tài khoản và mô hình đã gỡ vào đích mặc định.
Mỗi thay đổi được ghi thành một dòng dù có đích hay không; có đích thì dòng đó mới vào hàng đợi gửi.

## Lịch sử chống sửa

Mỗi dòng `engine.record_event` có `hash = sha256(prev_hash || nội dung chuẩn hóa của dòng)`; các dòng được nối thành một chuỗi duy nhất, ghi dưới một khóa advisory.
Mỗi giờ backend ghi một điểm neo (id, hash, thời điểm) vào đối tượng `history/anchors.jsonl` của kho đối tượng, nằm ngoài Postgres và có trong bản sao lưu của kho; `dxos history anchor` ghi một điểm neo ngay.
Webhook sự kiện mang `hash` của từng dòng, nên bên nhận có thêm một điểm neo ngoài máy chủ.

`dxos history verify` (hay `GET /api/v1/history/verify`, ADMIN) tính lại cả chuỗi và so với các điểm neo.
Kết quả có `intact`, `head`, `firstBreak` (dòng đầu tiên bị sửa, bị chèn hoặc đứng sau một dòng bị xóa) và `mismatchedAnchors` (điểm neo mà dòng hay hash tương ứng không còn); lệnh thoát với mã 1 khi `intact` là `false`.
Chuỗi chống được việc sửa, xóa hay chèn một dòng bằng SQL, và việc cắt bớt đuôi lịch sử sau một điểm neo.
Người sửa lại cả chuỗi và cả đối tượng điểm neo trên máy chủ thì không phát hiện được, trừ khi so với hash mà bên nhận webhook đã giữ.
[`examples/history-witness/witness.py`](../examples/history-witness/witness.py) làm việc so đó: `save` ghi cặp `eventId` và `hash` vào một tệp ở nơi người sửa máy chủ không với tới, `compare` báo dòng nào đã đổi hash hoặc không còn.

## Hồ dữ liệu

`dxos archive <mô hình> [--before YYYY-MM-DD] [--out THƯ_MỤC | --store]` ghi các hồ sơ đã tới trạng thái cuối (`decided_at` có giá trị, cũ hơn `--before` nếu có) và lịch sử của chúng ra Parquet, mỗi lần chạy vào một thư mục `<mô hình>/<lần chạy>/` kèm `manifest.json`.
Lệnh chạy DuckDB trong một container và đọc Postgres bằng tài khoản `POSTGRES_USER` của `.env` ở chế độ chỉ đọc, nên dữ liệu trong cơ sở dữ liệu giữ nguyên.

### Ghi ra thư mục

Mặc định ghi vào `deploy/archive`, hoặc vào `--out THƯ_MỤC`.
`manifest.json` gồm phiên bản mô hình, số dòng và sha256 từng tệp.
`dxos archive check <thư mục>` đọc lại từng tệp và so số dòng và sha256 với manifest; lệch thì thoát với mã 1.

### Ghi vào kho đối tượng

`--store` ghi vào bucket `S3_ARCHIVE_BUCKET` (mặc định `dxos-archive`) của SeaweedFS đang chạy trong stack, ở đường `<mô hình>/<lần chạy>/`.
Bucket riêng, không đi qua gateway và tự tạo ở lần ghi đầu.
DuckDB ghi thẳng tới `s3://` bằng khóa `S3_ACCESS_KEY` và `S3_SECRET_KEY` của `.env`; khóa đi vào container qua biến môi trường nên không nằm trên dòng lệnh, trong log hay trong thông báo lỗi.
Kho là một phần dữ liệu của SeaweedFS nên `dxos backup` sao lưu cùng các tệp tải lên.

`manifest.json` trong kho gồm thêm `since` và `upTo`, hai đầu của khoảng `decided_at` mà lần chạy lấy.
Số dòng và sha256 trong đó tính từ chính các byte đã nằm trong kho.
`dxos archive list` liệt kê các lần chạy trong kho, `dxos archive check --store <mô hình>/<lần chạy>` đọc lại từng tệp từ kho và so với manifest; một tệp lệch hoặc không đọc được thì lệnh thoát với mã 1.
Không đọc được kho (khóa sai, SeaweedFS không chạy) thì lệnh báo lỗi và thoát với mã 1, không coi là kho trống.

### Theo lịch

`dxos archive auto` ghi, với mỗi mô hình có quy trình, các hồ sơ đã đóng quá `ARCHIVE_AFTER_DAYS` ngày (mặc định 365) mà kho chưa giữ.
Lần chạy lấy khoảng từ `upTo` của lần chạy trước tới hạn của hôm nay, nên một hồ sơ nằm trong đúng một lần chạy; không có hồ sơ mới thì không ghi gì.
`dxos archive schedule install` cài `dxos-archive.timer` (systemd user timer) chạy lệnh đó lúc 03:30 mỗi ngày và bù lượt bị lỡ khi máy tắt, mỗi lần ghi vào `deploy/archive.log`; `dxos archive schedule uninstall` gỡ.
Máy không có systemd thì chạy `dxos archive auto` từ cron mỗi ngày.
Timer của user chỉ chạy khi user còn phiên đăng nhập, trừ khi bật `loginctl enable-linger <user>`.

### Mạng

Lần chạy đầu DuckDB tải extension `postgres` và `httpfs` từ mạng và lưu ở `deploy/data/duckdb`; các lần sau chạy không cần mạng.
