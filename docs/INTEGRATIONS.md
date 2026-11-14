# Tích hợp với hệ thống ngoài

Tài liệu cho người nối hệ thống ngoài với DX-OS: webhook sự kiện, API kéo sự kiện, lịch sử chống sửa và hồ dữ liệu.
Cài đặt và các lệnh `dxos` ở [`OPERATIONS.md`](./OPERATIONS.md); log và audit ở [`MONITORING.md`](./MONITORING.md).
Kết nối công cụ báo cáo trực tiếp tới dữ liệu nghiệp vụ bằng vai chỉ đọc ở [`REPORTING.md`](./REPORTING.md).

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
Quy trình khai `escalate` thì hồ sơ quá hạn nhận thêm các dòng `escalated`, mỗi dòng cho một mức đến hạn hoặc một lần nhắc lại, kèm `level` (mức, từ 1) và `round` (lần nhắc, từ 0) trong thông điệp.
Dòng `escalated` không đổi trạng thái hồ sơ, và các trường còn lại của thông điệp có dạng như mọi dòng lịch sử khác.

### Câu cho Mattermost và Slack

Đích có `format: chat` nhận `{"text": "<một câu>"}` thay cho thông điệp nguyên văn; Mattermost và Slack đều nhận dạng này ở địa chỉ webhook đến của họ.
Câu có tên mô hình, thực thể, số hồ sơ và trạng thái, không có nội dung hồ sơ.
Hồ sơ của thực thể có quy trình vừa được tạo hoặc nộp lại đọc là "Việc mới", dòng `due_soon` là "Sắp đến hạn xử lý", dòng `overdue` là "Quá hạn xử lý", dòng `escalated` là "Leo thang mức" kèm các vai nhận, dòng giao việc là "Được giao"; các dòng khác ghi hành động và trạng thái trước, sau.

Để nối Mattermost, tạo Incoming Webhook cho kênh nhận, đặt URL vào đích `ops` ở ví dụ trên và khai `webhook: ops` trong mô hình.
Giữ URL webhook ngoài Git và bảo đảm backend truy cập được; `localhost` trong container là chính container đó.

Luồng webhook đã kiểm ngày 13/11/2026 với Mattermost Team Edition 11.7.15, dùng binary/client Linux amd64 nguyên bản theo `MIT-COMPILED-LICENSE`, không kèm plugin.
Mattermost là dịch vụ dựng riêng, không thuộc bản cài mặc định của DX-OS.

- Với hạn xử lý `1m` và `DXOS_DUE_SOON_BEFORE=20s`, các dòng `created`, `due_soon`, `overdue` tới đúng kênh và outbox ghi nhận đã giao.
- Tên người làm bằng tiếng Việt hiển thị đúng; payload kiểm riêng qua `WebhookSender` giữ đúng xuống dòng, dấu nháy, `&`, `< >` và emoji trên giao diện Mattermost.
- Khi Mattermost không phản hồi trong 5 giây hoặc hook sai trả HTTP 404, hồ sơ vẫn lưu, outbox ghi lỗi và tự gửi lại thành công sau khi khôi phục đích trước giới hạn số lần thử.

Mattermost không khử trùng theo `X-Dxos-Event-Id`, nên thông báo có thể trùng khi hết thời gian chờ rồi gửi lại.
Slack chưa được kiểm thực tế.

### Kiểm tra trước khi chuyển trạng thái

Hành động khai `check: <tên đích>` thì dịch vụ ở đích đó được hỏi trước khi hồ sơ chuyển trạng thái.
Đích khai trong `deploy/webhooks/targets.yml` như đích của `webhook`, thêm hai khóa tùy chọn:

```yaml
targets:
  audit_service:
    url: https://audit.example.org/dxos/check
    secret: change-me
    on_error: deny
    bypass: false
```

- `on_error` là `deny` (mặc định) hoặc `allow`, cho lúc dịch vụ không trả lời trong 3 giây, trả lỗi hoặc trả lời không đúng dạng.
- `bypass: true` cho mọi hành động đi qua mà không hỏi, dùng khi bảo trì dịch vụ; sửa tệp là có hiệu lực, không cần tạo lại backend.
- Tên đích chưa có trong tệp thì xem kế hoạch báo bị từ chối và áp dụng trả 409, như đích của `webhook`.

Dịch vụ được hỏi sau khi người gọi đã đạt mọi quy tắc của quy trình, trong cùng giao dịch giữ khóa hồ sơ, nên hai hành động trên một hồ sơ không hỏi chồng nhau.
Backend gửi `POST` JSON với `model`, `entity`, `record_id`, `action`, `from`, `to`, `actor`, `note` và `record` (các trường của hồ sơ).
`X-Dxos-Event-Id` giống nhau ở mọi lần hỏi cùng một hành động trên cùng một phiên bản hồ sơ, để dịch vụ nhận ra lần lặp; `X-Dxos-Signature` có khi đích có `secret`, tính như ở webhook sự kiện.
Dịch vụ trả `2xx` với `{"allow": true}` hoặc `{"allow": false, "code": "...", "message": "..."}`; `code` và `message` là của dịch vụ, tối đa 300 ký tự.

| Kết quả | Hồ sơ | Người gọi nhận | Dòng lịch sử |
|---|---|---|---|
| `allow: true` | Chuyển trạng thái | Kết quả của hành động | Dòng của hành động |
| `allow: false` | Giữ nguyên | 403 `ERR_ACTION_NOT_ALLOWED`, `data.reason` là `CHECK_DENIED`, `data.code` là mã của dịch vụ khi có, `message` là câu của dịch vụ | `check_denied` |
| Không trả lời hoặc trả lời sai, `on_error: deny` | Giữ nguyên, thử lại được | 503 `ERR_CHECK_UNAVAILABLE`, `data.reason` là `CHECK_UNAVAILABLE` | `check_unavailable` |
| Không trả lời hoặc trả lời sai, `on_error: allow` | Chuyển trạng thái | Kết quả của hành động | `check_skipped`, rồi dòng của hành động |
| `bypass: true` | Chuyển trạng thái | Kết quả của hành động | `check_skipped`, rồi dòng của hành động |

Hành động tự đóng của `auto_close` do hệ thống thực hiện nên không hỏi dịch vụ.

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

`dxos archive query <mô hình>/<lần chạy> --file <tệp>.parquet` dựng bảng tạm `archive` từ đúng một tệp Parquet có trong manifest của lần chạy đó rồi nhận một câu `SELECT`, `WITH` hoặc `EXPLAIN`.
Lệnh kiểm số dòng và SHA-256 của tệp với manifest trước khi chạy câu SQL; không có `--file`, có ký tự đại diện hoặc tệp không có trong manifest thì lệnh thoát với mã 1.
Lệnh hỗ trợ tham số ràng buộc bằng `--param`, tắt truy cập đường dẫn ngoài trước khi chạy câu SQL và không cần cấp quyền cơ sở dữ liệu cho công cụ báo cáo.
Lệnh chạy trên máy cài đặt bằng khóa kho đối tượng trong `.env`, nên chỉ người có quyền vận hành thư mục triển khai mới dùng được.
Ví dụ và cú pháp tham số nằm trong [`OPERATIONS.md`](./OPERATIONS.md#lưu-trữ-hồ-sơ-đã-đóng).

### Theo lịch

`dxos archive auto` ghi, với mỗi mô hình có quy trình, các hồ sơ đã đóng quá `ARCHIVE_AFTER_DAYS` ngày (mặc định 365) mà kho chưa giữ.
Lần chạy lấy khoảng từ `upTo` của lần chạy trước tới hạn của hôm nay, nên một hồ sơ nằm trong đúng một lần chạy; không có hồ sơ mới thì không ghi gì.
`dxos archive schedule install` cài `dxos-archive.timer` (systemd user timer) chạy lệnh đó lúc 03:30 mỗi ngày và bù lượt bị lỡ khi máy tắt, mỗi lần ghi vào `deploy/archive.log`; `dxos archive schedule uninstall` gỡ.
Máy không có systemd thì chạy `dxos archive auto` từ cron mỗi ngày.
Timer của user chỉ chạy khi user còn phiên đăng nhập, trừ khi bật `loginctl enable-linger <user>`.

### Mạng

Lần chạy đầu DuckDB tải extension `postgres` và `httpfs` từ mạng và lưu ở `deploy/duckdb`; các lần sau chạy không cần mạng.
