# Phát triển tại các điểm mở rộng

DX-OS giữ nghiệp vụ phổ biến trong tệp mô hình và để logic đặc thù ở ngoài lõi.
Tài liệu này hướng dẫn nối dịch vụ, bên nhận sự kiện và giao diện riêng vào hệ thống đang chạy mà không đưa tên thực thể, vai hay luồng của một lĩnh vực vào mã lõi.

## Mức hỗ trợ hiện tại

Trong tài liệu này, **ổn định** nghĩa là hợp đồng đã có tài liệu và kiểm tra trong repository.
Nó không có nghĩa một mô hình có thể đổi trường mà mã tích hợp không cần kiểm tra lại.
**Thử nghiệm** nghĩa là cách làm chạy được trong source tree hiện tại nhưng chưa có ranh giới cài đặt hoặc nâng cấp được bảo toàn.

| Nhu cầu | Mức hỗ trợ | Ranh giới nâng cấp |
|---|---|---|
| Dịch vụ riêng gọi API của lõi | Ổn định | Dùng client riêng cho đúng một vai của một mô hình và đọc OpenAPI sinh từ mô hình đang chạy |
| Bên nhận phản ứng sau thay đổi | Ổn định | Webhook có chữ ký, `schemaVersion` và API đọc bù; webhook không chặn được hành động trước khi trạng thái đổi |
| Giao diện ngoài chạy ở `localhost` | Thử nghiệm cục bộ | Client `dxos-web` và CORS hiện chỉ chuẩn bị sẵn cho gateway cùng origin, `localhost` và `127.0.0.1` |
| Giao diện ngoài ở tên miền khác | Chưa có hợp đồng cài đặt | Chưa có lệnh đăng ký OIDC public client hoặc allowlist CORS cho tên miền tùy chọn |
| Trang hoặc thành phần form trong web Tầng 2 | Tùy biến source | Phải sửa và dựng lại image frontend; chưa có registry plugin hay kiểm tra tương thích |
| Route riêng dưới gateway DX-OS | Thử nghiệm | Phải sửa tệp APISIX do bộ cài quản lý; `dxos update` có thể thay tệp đó |
| Quy trình riêng | Ổn định bằng khai báo | Khai báo `states` và `actions` trong tệp mô hình, không viết plugin; xem [`MODEL.md`](./MODEL.md#tự-khai-báo-hoặc-mở-rộng-quy-trình) |

Ưu tiên tệp mô hình và API chung trước khi viết mã.
Một dịch vụ chỉ gọi API DX-OS không cần thêm route vào APISIX của DX-OS.

## Nguyên tắc chung

- Giữ mã mở rộng trong repository, image và vòng đời phát hành riêng.
- Dùng client hẹp nhất: một ứng dụng máy giữ đúng một vai của một mô hình; trình duyệt dùng đăng nhập người dùng với Authorization Code và PKCE.
- Không đưa secret vào image, repository, biến build frontend, URL, log hoặc lệnh có thể nằm trong shell history.
- Gửi `Idempotency-Key` cho thao tác tạo hồ sơ và hành động có thể được thử lại.
- Chấp nhận trường JSON mới, nhưng từ chối `schemaVersion` chưa hỗ trợ và kiểm lại OpenAPI sau mỗi lần đổi mô hình.
- Ghi và truyền `X-Request-Id` để đối chiếu log gateway, dịch vụ mở rộng và backend.
- Tách lỗi của phần mở rộng khỏi lõi bằng timeout, hàng đợi hoặc retry có giới hạn; không giữ một request của người dùng để chờ công việc dài.

## Dịch vụ riêng gọi API

Đây là đường mở rộng phù hợp khi cần tính toán, đồng bộ hoặc giao tiếp với một hệ thống khác nhưng dữ liệu và chuyển trạng thái vẫn do DX-OS quản lý.

### 1. Chọn hợp đồng và quyền

Áp dụng mô hình trước, rồi chọn một vai có đúng quyền mà dịch vụ cần.
Quản trị viên đăng ký ứng dụng ở tab **Ứng dụng ngoài** của `/console` hoặc bằng lệnh:

```bash
dxos client add equipment-sync --model equipment --role KEEPER
```

Kết quả có `clientId` và `secret`; secret chỉ hiện lần này.
Lưu secret vào trình quản lý secret hoặc một tệp chỉ tiến trình dịch vụ đọc được, không chép vào `.env` của source tree.

Mỗi client giữ đúng một vai của một mô hình.
API của mô hình khác, API quản trị và API người dùng trả `403`.
Muốn đổi vai, xóa client rồi đăng ký lại; hồ sơ client đã tạo vẫn còn.

### 2. Lấy token và OpenAPI của mô hình

Ví dụ dưới đây giữ secret trong tệp có quyền `600` và không đặt giá trị secret trên dòng lệnh:

```bash
export DXOS_BASE_URL=https://dxos.example.org
export DXOS_CLIENT_ID=dxos-app-equipment-sync
export DXOS_SECRET_FILE=/run/secrets/dxos-client-secret

TOKEN="$(curl -fsS \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode "client_id=$DXOS_CLIENT_ID" \
  --data-urlencode "client_secret@$DXOS_SECRET_FILE" \
  "$DXOS_BASE_URL/realms/dxos/protocol/openid-connect/token" | jq -r .access_token)"

curl -fsS \
  -H "Authorization: Bearer $TOKEN" \
  "$DXOS_BASE_URL/api/v1/meta/models/equipment/openapi" \
  -o equipment.openapi.json
```

OpenAPI này được sinh từ phiên bản mô hình đang chạy và chỉ chứa các thực thể người gọi được đọc.
Dùng nó để sinh client hoặc làm đầu vào cho kiểm thử hợp đồng; không sao chép một schema cũ vào mã rồi coi đó là nguồn thật.

### 3. Gọi dữ liệu và quy trình

API dữ liệu nằm dưới `/api/v1/data/{model}/{entity}`.
Trường, hành động, mã lỗi và dữ liệu bắt buộc của mỗi hành động nằm trong OpenAPI vừa lấy; tổng quan ở [`API.md`](./API.md).

```bash
curl -fsS \
  -H "Authorization: Bearer $TOKEN" \
  "$DXOS_BASE_URL/api/v1/data/equipment/loan?page=0&size=20"
```

Khi tạo hồ sơ hoặc gọi hành động, sinh một khóa ổn định cho chính yêu cầu nghiệp vụ và gửi ở header `Idempotency-Key`.
Gửi lại cùng khóa và cùng nội dung nhận lại kết quả đầu; dùng cùng khóa cho nội dung khác trả `409`.
Khi sửa hồ sơ, gửi `version` đã đọc và xử lý `ERR_CONCURRENT_MODIFICATION` thay vì ghi đè.

Không ghi trực tiếp PostgreSQL và không đổi cột `status` qua API dữ liệu.
Mọi chuyển trạng thái đi qua `/actions/{action}` để các quy tắc, Flowable, lịch sử và sự kiện cùng được áp dụng.

### 4. Chạy local và kiểm thử

Chạy dịch vụ ngoài Compose của lõi với `DXOS_BASE_URL=http://localhost:9080`.
Kiểm ít nhất các tình huống sau:

- Lấy token, tải OpenAPI và gọi một endpoint được vai cho phép.
- Gọi mô hình khác hoặc endpoint quản trị và nhận `403`.
- Gửi lại một yêu cầu có cùng `Idempotency-Key` mà không tạo tác dụng lần hai.
- Mô phỏng token hết hạn, `429`, timeout và `409` mà không retry vô hạn.
- Đổi tên hoặc bỏ trường trong bản mô hình thử và để kiểm thử hợp đồng chỉ ra chỗ tích hợp phải đổi.

[`examples/itot-simulator`](../examples/itot-simulator/README.md) minh họa dịch vụ ngoài lấy token máy, tạo hồ sơ, thực hiện hành động và chống gửi trùng.
Lệnh `setup` của ví dụ tự gọi Keycloak để ví dụ chạy độc lập; bản triển khai thật dùng `dxos client add` để client xuất hiện trong quản trị và danh mục dữ liệu.

### 5. Đóng gói và nâng cấp

Đóng gói dịch vụ thành image riêng, chạy bằng Compose hoặc hệ điều phối riêng và truyền URL, client id, secret bằng cấu hình lúc chạy.
Không thêm service vào `deploy/docker-compose.yml` của bộ cài nếu muốn `dxos update` không ghi đè phần triển khai riêng.

Trước khi nâng DX-OS hoặc áp dụng phiên bản mô hình mới:

1. Lấy OpenAPI từ môi trường thử và so với hợp đồng mà dịch vụ đang dùng.
2. Chạy lại các kiểm thử quyền, idempotency, lỗi đồng thời và timeout.
3. Triển khai dịch vụ tương thích trước khi áp dụng thay đổi phá vỡ hợp đồng.
4. Xoay secret bằng `dxos client rotate <tên>` và cập nhật secret runtime; token cũ có thể dùng tới khi hết hạn, khoảng 5 phút.
5. Gỡ client không còn dùng bằng `dxos client remove <tên>`.

## Bên nhận webhook

Webhook phù hợp cho thông báo, đồng bộ bất đồng bộ, lập chỉ mục hoặc kích hoạt công việc sau khi DX-OS đã ghi thay đổi.
Webhook hiện không phải điểm kiểm tra đồng bộ và không thể từ chối một hành động trước khi trạng thái đổi.

### 1. Khai báo đích

Đặt cấu hình của bản cài trong `deploy/webhooks/targets.yml`, ngoài git:

```yaml
targets:
  extension_worker:
    url: https://worker.example.org/dxos/events
    secret: <một bí mật ngẫu nhiên>
    format: json
```

Tệp mô hình chọn đích bằng khóa ở đầu tệp:

```yaml
webhook: extension_worker
```

Chi tiết nạp lại cấu hình, retry, thứ tự và payload nằm ở [`deploy/INTEGRATIONS.md`](../deploy/INTEGRATIONS.md#webhook-sự-kiện).

### 2. Xử lý an toàn

Bên nhận thực hiện theo thứ tự:

1. Đọc nguyên văn byte của body và kiểm `X-Dxos-Signature` bằng HMAC-SHA256 với phép so constant-time.
2. Từ chối `schemaVersion` chưa hỗ trợ; với phiên bản đã biết, bỏ qua trường mới mà nó chưa dùng.
3. Ghi bền vững `eventId` trước khi trả `2xx`; nếu nhận lại id đã có thì trả `2xx` mà không làm lại tác dụng.
4. Đưa công việc dài sang hàng đợi riêng và trả lời trong 5 giây.
5. Chỉ gọi lại API dữ liệu khi cần nội dung hồ sơ, bằng client có quyền hẹp phù hợp.

`record.event` và `admin.event` có dãy `eventId` riêng.
Bên nhận xử lý cả hai loại phải khử trùng theo cặp loại thông điệp và `eventId`.

### 3. Chạy local và đọc bù

URL webhook phải truy cập được từ container backend.
Trên Docker Desktop có thể dùng `host.docker.internal`; trên Linux, chạy bên nhận trong một mạng Docker mà backend tới được hoặc dùng một địa chỉ thử nghiệm riêng.

Để kiểm:

- Tạo hoặc sửa một hồ sơ và xác nhận bên nhận lưu đúng một lần.
- Trả mã lỗi để thấy thông điệp ở tab Hàng đợi sự kiện, sau đó khôi phục bên nhận và bấm **Thử lại**.
- Gửi lại cùng payload để chứng minh `eventId` được khử trùng.
- Sửa một byte của body và xác nhận chữ ký bị từ chối.

Bên nhận lỡ tin đọc bù từng trang bằng `GET /api/v1/events?after=<eventId>&size=500` và tiếp tục khi `more` là `true`.
Feed này yêu cầu `ADMIN`; client tạo bằng `dxos client add` không được cấp vai nền tảng này.
Giữ cursor chỉ sau khi toàn bộ trang đã được lưu thành công.

[`examples/history-witness`](../examples/history-witness/README.md) minh họa đọc hết các trang, giữ `eventId` cùng `hash` ở ngoài máy chủ và so lại về sau.

### 4. Đóng gói và nâng cấp

Đóng gói receiver tách khỏi DX-OS, giữ secret trong secret runtime và sao lưu kho khử trùng cùng cursor.
Theo dõi `schemaVersion`, kiểm payload thử trước khi nâng và triển khai receiver chấp nhận cả phiên bản cũ lẫn mới trong khoảng chuyển tiếp khi hợp đồng thay đổi.

## Giao diện ngoài

Giao diện trình duyệt phải đăng nhập người dùng bằng Authorization Code và PKCE.
Không đặt client secret hoặc dùng `client_credentials` trong JavaScript gửi tới trình duyệt.

### Nguyên mẫu local hiện hỗ trợ

Realm hiện có public client `dxos-web` cho gateway, `http://localhost:9080` và `http://localhost:5173`.
Backend cho CORS từ `http://localhost:*` và `http://127.0.0.1:*`.
Vì vậy một nguyên mẫu local có thể chạy ở `http://localhost:5173` với:

| Giá trị OIDC | Cấu hình |
|---|---|
| Authority | `http://localhost:9080/realms/dxos` |
| Client id | `dxos-web` |
| Flow | Authorization Code + PKCE |
| Redirect và post-logout URI | Origin chính xác của ứng dụng |
| Scope | `openid profile` |

Sau đăng nhập, gửi access token ở `Authorization: Bearer <token>` và đọc `/api/v1/meta/models/{model}/openapi` bằng chính người dùng đó.
Tài liệu sinh ra chỉ chứa thực thể người dùng được xem, nên giao diện vẫn phải xử lý `403` khi quyền đổi và không tự suy quyền từ nút đang hiển thị.

Kiểm luồng đăng nhập, callback, refresh hoặc đăng nhập lại khi token hết hạn, single logout, `401`, `403` và việc quay về đúng trang ban đầu.

### Giới hạn triển khai thật

Tên miền khác chưa có đường cấu hình được hỗ trợ: CORS không đọc allowlist từ `.env`, và chưa có lệnh đăng ký public client với redirect URI tùy chọn.
Sửa tay Keycloak và source backend có thể làm một bản cài chạy được nhưng không phải hợp đồng được bộ cài kiểm hoặc bảo toàn.

Phục vụ giao diện dưới cùng gateway cũng cần route riêng; route đó chịu giới hạn nâng cấp ở phần dưới.
Không mô tả một nguyên mẫu local là giao diện ngoài đã sẵn sàng production cho tới khi có cấu hình CORS, public client và redirect URI được kiểm chặt.

## Trang riêng trong web Tầng 2

Trước hết kiểm tra khối `ui:` của tệp mô hình có đáp ứng được menu, cột, biểu mẫu, gợi ý, lịch và thống kê hay không.
Đường khai báo này được giữ khi nâng cấp và không cần dựng lại frontend.

Nếu vẫn cần trang riêng, hiện chỉ có cách tùy biến source:

1. Tạo component dưới `frontend/src/` và nạp chậm nếu trang lớn.
2. Thêm `<Route>` trong `<CustomRoutes>` và mục `Menu.Item` tương ứng ở `frontend/src/admin/AdminApp.tsx`.
3. Dùng `frontend/src/shell/api.ts` để request mang token và giữ kiểm tra quyền ở backend.
4. Chạy `npm ci` rồi `npm run build` trong `frontend/`.
5. Ở môi trường source, dựng và thay riêng image bằng `cd deploy && docker compose build frontend && docker compose up -d --no-deps frontend`.

Đóng gói frontend tùy biến bằng image và tag riêng.
`dxos update` kéo image frontend chính thức, còn nâng bộ cài thay các tệp runtime; do đó đội sở hữu bản tùy biến phải rebase thay đổi, chạy lại build và phát hành lại image sau mỗi phiên bản.

Chưa có registry cho trang hay thành phần form, chưa có sandbox và chưa có kiểm tra tương thích plugin.
Không tải JavaScript tùy ý từ mô hình hoặc từ một URL do người dùng nhập.

## Route riêng trong APISIX

Dịch vụ chỉ gọi DX-OS không cần route này.
Chỉ thêm route khi người dùng hoặc hệ thống khác phải gọi dịch vụ mở rộng qua cùng cổng vào.

Tệp `deploy/apisix/common.yaml` có hai danh sách `upstreams` và `routes`.
Ví dụ sau giả định container `extension-worker` cùng mạng Docker với APISIX; ghép từng phần vào danh sách tương ứng, không tạo khóa YAML thứ hai:

```yaml
# Trong upstreams:
- id: extension-worker
  type: roundrobin
  nodes:
    "extension-worker:8080": 1

# Trong routes:
- id: extension-worker-api
  priority: 10
  uri: /extensions/worker/*
  upstream_id: extension-worker
  plugin_config_id: bearer-token
```

Route id và đường dẫn phải không trùng, và priority phải cao hơn route web bắt mọi đường dẫn.
`bearer-token` chỉ xác thực token; dịch vụ mở rộng vẫn phải kiểm quyền cần thiết hoặc gọi API lõi bằng token của người dùng.
Một route public phải được xem xét riêng và liệt kê lý do trong `deploy/apisix/check_routes.py`; không bỏ xác thực chỉ để phép kiểm đi qua.

Kiểm và nạp lại cấu hình:

```bash
python3 deploy/apisix/check_routes.py
cd deploy
docker compose up --force-recreate apisix-config
docker compose restart apisix
```

Gọi thử không token, token sai và token đúng; đối chiếu `X-Request-Id` trong log.

Đây chưa phải điểm mở rộng bền qua nâng cấp.
`deploy/install.sh --upgrade` thay các tệp runtime được phân phối, gồm `apisix/common.yaml` và `docker-compose.yml`, nên phải giữ patch riêng, xem lại diff của bản mới và áp dụng lại sau `dxos update`.

## Danh sách kiểm trước khi phát hành phần mở rộng

- Mã và image nằm ngoài lõi, có giấy phép và SBOM của chính nó.
- Client chỉ có một vai của một mô hình; secret không nằm trong source, image hay log.
- OpenAPI của mô hình đang chạy được lưu làm đầu vào kiểm thử hợp đồng.
- Retry ghi dùng `Idempotency-Key`, có giới hạn và không biến `403` hay lỗi kiểm tra thành thành công.
- Webhook kiểm chữ ký trên raw body, khử trùng trước `2xx` và đọc hết từng trang trước khi tăng cursor.
- Giao diện dùng Authorization Code + PKCE, không có secret trong trình duyệt và không tự quyết định quyền.
- Route có id, path, priority, xác thực và giới hạn lưu lượng rõ ràng.
- Đã thử nâng trên môi trường tách biệt và có đường quay lại image cùng cấu hình trước đó.
