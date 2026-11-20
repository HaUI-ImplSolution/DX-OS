# Thêm quy trình đổi trả và bảo hành vào ứng dụng bán hàng có sẵn

Thư mục này là ứng dụng của một cửa hàng điện máy nhỏ, viết như của một nhà phát triển bên ngoài.
Mã của nó không dùng gì của DX-OS và không biết DX-OS là gì.
Tài liệu này đi theo người làm ứng dụng: chạy ứng dụng, nghe nói đến DX-OS, cài nó trên máy sạch và nối hai quy trình vào ứng dụng.
Mọi bước làm được trên giao diện web của DX-OS; chỗ nào có dòng lệnh tương đương thì ghi ở mục "Cách khác bằng dòng lệnh" của bước đó.

## Bạn có gì

Một ứng dụng bán hàng chạy bằng Docker Compose:

- Nhân viên bán hàng đăng nhập, xem danh sách đơn bán và chi tiết từng đơn.
- Khách hàng không có tài khoản nào; họ chỉ xuất hiện qua đơn bán của mình.

```text
appliance-store/
  docker-compose.yml   ứng dụng và cơ sở dữ liệu SQLite của nó
  app/                 mã ứng dụng (Node.js 24, không có gói npm nào)
  catalog/             danh mục sản phẩm mẫu
```

## Bước 1. Chạy ứng dụng của bạn

Cần Docker Compose v2.

```bash
cd examples/appliance-store
docker compose up -d --build
docker compose logs app
```

Lệnh `logs` in mật khẩu của tài khoản `nhanvien` một lần.
Mở `http://localhost:3000`, đăng nhập và xem các đơn bán.
Cổng 3000 bị chiếm thì đặt `APP_HOST_PORT=3010` trong tệp `.env` cạnh `docker-compose.yml` rồi chạy lại.

## Việc cửa hàng cần thêm

Cửa hàng muốn hai việc mà ứng dụng chưa có:

- **Đổi trả hàng:** nhân viên lập phiếu cho khách, quản lý xem và duyệt hoặc từ chối.
- **Bảo hành:** nhân viên lập phiếu, kỹ thuật viên nhận và sửa trong thời hạn; quá hạn thì ứng dụng phải báo.

Cả hai cần vòng duyệt, phân quyền theo người và lịch sử không sửa được, và bạn không muốn tự viết những thứ đó.

Bạn tìm thấy **DX-OS** trên GitHub: một hệ thống nguồn mở chạy cạnh ứng dụng của bạn và lo đúng phần này.
Bạn chọn hoặc mô tả quy trình, DX-OS dựng ra kho dữ liệu, tài khoản và vai, vòng duyệt, lịch sử và một giao diện để người duyệt làm việc.
Ứng dụng của bạn gọi DX-OS qua API, giống cách nó gọi Keycloak hay MinIO: cài, thêm cấu hình, gọi.

## Bước 2. Cài DX-OS (không cần tải mã nguồn)

Trên máy chạy ứng dụng (Linux hoặc WSL2, có Docker Compose v2):

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

Lần đầu mất vài phút.
`dxos start` in địa chỉ các trang và mật khẩu tạm của tài khoản quản trị `admin`.
Mở `http://localhost:9080/console`, đăng nhập `admin` và đặt mật khẩu mới theo yêu cầu.
Từ đây bạn làm việc trên trang này, gọi là Console.
Tài liệu đầy đủ ở [`docs/QUICKSTART.md`](../../docs/QUICKSTART.md), đường 2.

## Bước 3. Áp dụng hai quy trình

DX-OS có sẵn một thư viện mẫu quy trình.
Một quy trình có thể dùng nguyên mẫu, hoặc bắt đầu từ một mẫu rồi chỉ ghi phần khác, bằng khóa `extends`.

### Đổi trả: dùng nguyên mẫu

Mẫu "Đổi trả hàng" đã có sẵn phiếu đổi trả, vai khách hàng (`CUSTOMER`), vai quản lý (`STORE_STAFF`) và vòng duyệt, nên không cần sửa gì.

1. Ở Console, tab **Thư viện mẫu**, bấm **Dùng mẫu này** ở thẻ **Đổi trả hàng**.
2. Ở tab **Soạn và áp dụng**, bấm **Xem kế hoạch**.
   Kế hoạch cho biết sẽ đổi gì và mức rủi ro (`Safe` là an toàn); chưa đổi gì cả.
3. Bấm **Áp dụng**.
   Tab **Mô hình đang chạy** có thêm thẻ **retail**.

### Bảo hành: mở rộng một mẫu

Mẫu "Hỗ trợ kỹ thuật" gần với bảo hành nhưng thiếu số seri, ngày mua và tên khách.
Bạn dùng nó làm gốc và ghi thêm phần khác.
Ở tab **Soạn và áp dụng**, xóa nội dung đang có, dán đoạn sau, bấm **Xem kế hoạch** rồi **Áp dụng**:

```yaml
extends: support_desk
name: warranty
entities:
  ticket:
    label: Yêu cầu bảo hành
    fields:
      title: {type: text, label: Sản phẩm, required: true}
      detail: {type: text, label: Lỗi gặp phải}
      serial: {type: text, label: Số seri, required: true}
      purchased_on: {type: date, label: Ngày mua, required: true}
      receipt: {type: text, label: Mã hóa đơn, required: true}
      customer: {type: text, label: Tên khách hàng, required: true}
      attachment: null
    ai: null
    process: {family: work_item, handler: AGENT, deadline: 2m}
```

`extends: support_desk` nghĩa là bắt đầu từ mẫu đó; các dòng còn lại chỉ nêu chỗ khác.
Tệp đặt tên trường của phiếu bảo hành, bỏ trường đính kèm và phần trợ lý AI của mẫu, và đặt hạn xử lý 2 phút để thấy ngay phiếu quá hạn khi thử; dùng `8h` hoặc `2d` cho việc thật.
Cách viết đầy đủ ở [`docs/MODEL.md`](../../docs/MODEL.md).

Sau bước này DX-OS có hai mô hình với bốn vai:

| Mô hình | Vai | Ai giữ vai |
|---|---|---|
| `retail` | `CUSTOMER` | Ứng dụng của bạn, thay khách hàng lập phiếu |
| `retail` | `STORE_STAFF` | Quản lý cửa hàng, duyệt phiếu |
| `warranty` | `REQUESTER` | Ứng dụng của bạn, thay khách hàng lập phiếu |
| `warranty` | `AGENT` | Kỹ thuật viên, xử lý phiếu |

**Cách khác bằng dòng lệnh:** lưu đoạn YAML trên thành `warranty.yaml`, rồi

```bash
dxos plan warranty.yaml
dxos apply warranty.yaml
```

Mẫu đổi trả cũng có thể áp dụng bằng một tệp hai dòng, `extends: retail_return` và `name: retail`.

## Bước 4. Tạo quản lý và kỹ thuật viên

Quản lý và kỹ thuật viên là người thật, nên họ có tài khoản ở DX-OS.
Ở Console, tab **Người dùng**:

1. Bấm **Tạo người dùng** cho quản lý (ví dụ `manager`) và cho kỹ thuật viên (ví dụ `technician`).
   DX-OS sinh mật khẩu tạm và chỉ hiện một lần; chép gửi cho từng người, họ đổi mật khẩu ở lần đăng nhập đầu.
2. Mở từng người và gán vai: `STORE_STAFF` của `retail` cho quản lý, `AGENT` của `warranty` cho kỹ thuật viên.

Chi tiết ở [`docs/QUICKSTART.md`](../../docs/QUICKSTART.md), đường 1, bước 3.

## Bước 5. Nối ứng dụng với DX-OS

Ứng dụng cần một tài khoản máy cho mỗi mô hình để gọi API của DX-OS; mỗi tài khoản giữ đúng một vai.

1. Ở Console, tab **Ứng dụng ngoài**, đăng ký hai tài khoản:
   - tên `retail-customer`, mô hình `retail`, vai `CUSTOMER`;
   - tên `warranty-requester`, mô hình `warranty`, vai `REQUESTER`.
2. Mỗi lần đăng ký, DX-OS hiện mã tài khoản (dạng `dxos-app-<tên>`) và mã bí mật một lần duy nhất; bấm **Sao chép**.
   Quên thì dùng nút đổi mã ở cùng tab.
3. Ghi vào tệp `.env` cạnh `docker-compose.yml`, giữ quyền riêng tư cho nó:

   ```bash
   touch .env && chmod 600 .env
   ```

   ```text
   DXOS_BASE_URL=http://host.docker.internal:9080
   DXOS_CUSTOMER_CLIENT_ID=dxos-app-retail-customer
   DXOS_CUSTOMER_CLIENT_SECRET=<mã bí mật của retail-customer>
   DXOS_REQUESTER_CLIENT_ID=dxos-app-warranty-requester
   DXOS_REQUESTER_CLIENT_SECRET=<mã bí mật của warranty-requester>
   ```

   Ứng dụng chạy trong Docker nên gọi DX-OS qua `host.docker.internal`, tên của máy chủ nhìn từ trong container.
   `.env` chứa bí mật nên không đưa vào Git; thư mục này đã có `.gitignore` cho nó.

**Cách khác bằng dòng lệnh:** hai lệnh dưới đây đăng ký tài khoản, ghi cả năm dòng trên vào `.env` (quyền `600`) và đăng nhập thử.
Chạy trong thư mục `appliance-store`.

```bash
dxos connect retail --role CUSTOMER
dxos connect warranty --role REQUESTER
```

## Bước 6. Thêm hai dòng vào ứng dụng

Thêm đúng hai dòng vào service `app` trong `docker-compose.yml` và chạy lại:

```yaml
    env_file: .env
    extra_hosts: ["host.docker.internal:host-gateway"]
```

```bash
docker compose up -d
```

`env_file` đưa các biến của `.env` vào ứng dụng; `extra_hosts` cho container biết `host.docker.internal` là máy chủ.
Mở lại `http://localhost:3000`: trang chi tiết đơn đã có biểu mẫu Đổi trả và Bảo hành, và có mục "Yêu cầu đang xử lý".
Ứng dụng tự bật các phần này khi thấy thông tin kết nối trong `.env`.

## Bước 7. Quản lý thêm sản phẩm vào DX-OS

Phiếu đổi trả của mẫu chọn một sản phẩm có sẵn ở DX-OS, nên quản lý nhập các sản phẩm khách có thể đổi trả.
Quản lý mở `http://localhost:9080`, đăng nhập `manager` (đổi mật khẩu tạm ở lần đầu), vào menu **Sản phẩm**, bấm **Tạo mới**, điền mã và tên rồi **Lưu**.
Mã phải trùng mã trong ứng dụng; danh sách ở `catalog/products.tsv`.
Muốn thử thì hai sản phẩm đầu là đủ: `TV-55` (Tivi 55 inch) và `FR-250` (Tủ lạnh 250 lít).
Gửi đổi trả cho một sản phẩm chưa có ở DX-OS thì ứng dụng báo "nhờ quản lý thêm vào mục Sản phẩm".

## Dùng thử

1. Trong ứng dụng, mở đơn của sản phẩm vừa nhập, điền Đổi trả (chọn Trả hàng, nêu lý do) và gửi.
   Phiếu hiện "Chờ duyệt" kèm liên kết "Mở trên DX-OS".
2. Quản lý mở liên kết hoặc **Hộp việc**, mở phiếu và bấm **Duyệt**.
   Mở lại trang trong ứng dụng: phiếu thành "Đã duyệt" mà không ai bấm gì ở ứng dụng.
3. Lập một yêu cầu bảo hành.
   Kỹ thuật viên nhận ở hộp việc và bấm bắt đầu: ứng dụng hiện "Đang xử lý".
   Sau 2 phút phiếu hiện "Quá hạn"; khi kỹ thuật viên bấm hoàn tất, ứng dụng hiện "Đã xử lý".

Mọi bước ở DX-OS đều ghi vào lịch sử không sửa được.

## Vì sao không tự viết vòng duyệt

Một vòng duyệt đơn giản tự viết chỉ vài trăm dòng.
Ba phép thử dưới đây cho thấy phần khó nằm ở chỗ khác, và không cần thêm mã nào vào ứng dụng.
Làm sau phần "Dùng thử", khi đã có phiếu đổi trả số 1 và một phiếu bảo hành.

### 1. Ứng dụng không tự duyệt được phiếu của chính nó

Luật "chỉ quản lý được duyệt" nằm ở DX-OS, không nằm trong mã ứng dụng.
Dù ứng dụng có lỗi hay bị chiếm quyền, tài khoản máy của nó vẫn chỉ làm được việc của vai `CUSTOMER`.
Chạy trong thư mục `appliance-store`, bằng chính tài khoản máy trong `.env` của ứng dụng:

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -d grant_type=client_credentials -d client_id=$DXOS_CUSTOMER_CLIENT_ID \
  -d client_secret=$DXOS_CUSTOMER_CLIENT_SECRET \
  http://localhost:9080/realms/dxos/protocol/openid-connect/token | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
curl -i -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}' \
  http://localhost:9080/api/v1/data/retail/return_request/1/actions/approve
```

Kết quả: `HTTP 403` kèm "Only STORE_STAFF can do this", và phiếu vẫn "Chờ duyệt".
Cùng token đó gọi `/api/v1/data/warranty/ticket` (dữ liệu của mô hình khác) hoặc `/api/v1/users` cũng trả 403.

### 2. Lịch sử bị sửa là lộ ra

Mọi bước của phiếu nối thành một chuỗi hash.
Người có quyền vào cơ sở dữ liệu sửa một dòng lịch sử thì `dxos history verify` chỉ ra dòng bị sửa.
Chạy trong `~/.dxos/deploy` (tên người dùng và cơ sở dữ liệu `dxos` là mặc định):

```bash
cd ~/.dxos/deploy
dxos history verify     # "intact": true
ORIG=$(docker compose exec -T postgres psql -U dxos -d dxos -Atc "select actor from engine.record_event where id = (select min(id) from engine.record_event)")
docker compose exec -T postgres psql -U dxos -d dxos -c "update engine.record_event set actor = 'someone-else' where id = (select min(id) from engine.record_event)"
dxos history verify     # "intact": false, firstBreak nêu dòng và "its content was changed"
docker compose exec -T postgres psql -U dxos -d dxos -c "update engine.record_event set actor = '$ORIG' where id = (select min(id) from engine.record_event)"
dxos history verify     # "intact": true
```

Người sửa được cả cơ sở dữ liệu lẫn kho tệp vẫn có thể tính lại cả chuỗi; cách chặn việc đó bằng bên nhận giữ hash nằm ở [`examples/history-witness`](../history-witness/README.md).

### 3. Đổi quy trình có kế hoạch và mức rủi ro

Sửa quy trình khi hệ thống đang có phiếu là việc dễ làm hỏng dữ liệu.
DX-OS không áp dụng thẳng mà cho xem kế hoạch với mức rủi ro trước; Console ở tab **Soạn và áp dụng** cho kết quả giống nhau.
Lưu mô hình bảo hành thành `warranty.yaml` và thử ba thay đổi:

- **Thêm một trường** (ví dụ dòng `branch: {type: text, label: Chi nhánh}` sau dòng `customer`): `dxos plan warranty.yaml` báo `SAFE`, `dxos apply warranty.yaml` áp dụng.
- **Bỏ trường đó lại:** kế hoạch báo `LOSS` vì dữ liệu của trường sẽ mất. `dxos apply` từ chối với "This change is at level LOSS ... pass accept=LOSS", và chỉ làm khi bạn gõ rõ `dxos apply warranty.yaml --accept LOSS`.
- **Đổi hạn xử lý** (ví dụ `deadline: 2m` thành `8h`) khi còn phiếu bảo hành chưa đóng: bị từ chối với "Cannot change CHANGE_DEADLINE for ticket while 1 records are still open", không áp dụng gì.

Tự viết thì cả ba việc này (luật theo vai, lịch sử chống sửa, đổi quy trình an toàn) đều phải tự làm và tự giữ đúng qua từng lần sửa.
Về công sức, ví dụ so sánh một luồng phiếu cơ bản nằm ở [`examples/effort-benchmark`](../effort-benchmark/README.md): 347 dòng Java viết tay so với một tệp YAML 28 dòng.

## Bạn đã thêm gì vào ứng dụng

| Phần | Cỡ |
|---|---|
| Quy trình đổi trả | Dùng nguyên mẫu, không có dòng nào của bạn |
| Quy trình bảo hành | 15 dòng YAML |
| Compose của ứng dụng | Hai dòng: `env_file` và `extra_hosts` |
| Mã trong ứng dụng | 109 dòng, không tính chú thích và dòng trống |

Mã tích hợp gồm `app/dxos.mjs` (lấy token, gửi phiếu, tra sản phẩm, đọc trạng thái), hàm gửi phiếu (28 dòng, gồm cả kiểm tra biểu mẫu và lưu phiếu) và hàm làm mới trạng thái (7 dòng) trong `app/server.mjs`.
Ứng dụng không có mã vòng duyệt, phân quyền hay lịch sử; những việc đó do DX-OS làm.

## Nếu cần thêm

### DX-OS báo ngay khi phiếu đổi trạng thái

Mặc định ứng dụng hỏi DX-OS trạng thái của các phiếu đang mở mỗi khi bạn mở trang, nên không cần cấu hình gì thêm.
Muốn ứng dụng nhận tin ngay cả khi không ai mở trang (ví dụ để báo quá hạn tức thì), dùng webhook: DX-OS gọi vào ứng dụng mỗi khi phiếu đổi.
Ứng dụng đã có chỗ nhận ở `/hooks/dxos` (khoảng 30 dòng mã), kiểm chữ ký, bỏ qua tin trùng và nhận lại tin bị lỡ khi nó tắt.

Cấu hình webhook nằm ở tệp trên máy chủ, không có trên giao diện:

1. Sinh bí mật dùng chung và ghi vào hai nơi, `.env` của ứng dụng và `~/.dxos/deploy/webhooks/targets.yml` của DX-OS:

   ```bash
   SECRET=$(openssl rand -hex 24)
   echo "DXOS_WEBHOOK_SECRET=$SECRET" >> .env
   cat >> ~/.dxos/deploy/webhooks/targets.yml <<EOF
   targets:
     store:
       url: http://host.docker.internal:3000/hooks/dxos
       secret: $SECRET
   EOF
   ```

   Đổi `APP_HOST_PORT` ở bước 1 thì đổi cả cổng trong `url`.
   Tệp đã có mục `targets:` thì thêm `store:` vào mục đó.
2. Cho cả hai mô hình biết nơi nhận bằng dòng `webhook: store` ở đầu tệp mô hình: ở Console, tab **Mô hình đang chạy**, bấm **Sửa mô hình** của từng mô hình, thêm dòng đó, **Xem kế hoạch** rồi **Áp dụng** (mức `Safe`).
3. Chạy lại ứng dụng để nó đọc bí mật: `docker compose up -d`.

### Việc khác

- **Đổi quy trình về sau:** sửa mô hình rồi xem kế hoạch và áp dụng. Thêm trường là mức `Safe`; bỏ trường là mức `Loss` và cần xác nhận.
- **Hạn bảo hành thật:** đổi `deadline: 2m` thành `8h` hoặc `2d`; đổi hạn bị từ chối khi còn phiếu mở.
- **Ứng dụng chạy ở máy khác DX-OS:** đặt `PUBLIC_HOST` của DX-OS thành địa chỉ máy chủ trước lần `dxos start` đầu tiên và dùng địa chỉ đó ở `DXOS_BASE_URL`.
- **Tự dựng giao diện duyệt trong ứng dụng:** gọi API theo OpenAPI của từng mô hình; có thể sinh client bằng OpenAPI Generator, xem [`docs/EXTENSIONS.md`](../../docs/EXTENSIONS.md).
- **Báo tin vào chat (Mattermost, Slack):** thêm một nơi nhận có `format: chat` trong `targets.yml`, xem [`docs/INTEGRATIONS.md`](../../docs/INTEGRATIONS.md).
- **Giao cho trợ lý AI:** mọi bước trên có lệnh `dxos` tương đương (`dxos plan`, `dxos apply`, `dxos connect`, `dxos client`), nên một trợ lý AI đọc [`docs/OPERATIONS.md`](../../docs/OPERATIONS.md) có thể tích hợp giúp bạn mà không cần Console.

## Giới hạn nói thẳng

- Nhân viên duyệt có hai tài khoản: một ở ứng dụng, một ở DX-OS. Đăng nhập một lần là hướng riêng, chưa làm.
- Quyền xem theo dòng `own` áp cho tài khoản máy của ứng dụng, không áp cho từng nhân viên hay khách; ứng dụng tự lọc ai được xem phiếu nào.
- Lịch sử ghi tài khoản máy của ứng dụng là người thao tác khi ứng dụng gọi; người nộp thật nằm ở mã hóa đơn và tên khách trong phiếu.
- Phiếu đổi trả của mẫu không có trường tên khách, nên ứng dụng ghi tên khách vào đầu lý do.
- Sản phẩm phải có sẵn ở DX-OS trước khi gửi đổi trả; ví dụ này không đồng bộ danh mục.
- Khi chưa bật webhook, trạng thái chỉ mới nhất khi có người mở trang của ứng dụng; phiếu đã xong thì ứng dụng không hỏi lại.
- DX-OS phải chạy bản đầy đủ (không `dxos start --core`) để người duyệt có hộp việc.
- Ứng dụng là ví dụ: dữ liệu mẫu cố định, phiên đăng nhập đơn giản, một tài khoản nhân viên.

## Không làm

- Tự dựng màn hình duyệt trong ứng dụng.
- Đăng nhập một lần và đồng bộ danh mục sản phẩm sang DX-OS.
- Chức năng ngoài sổ bán hàng, đổi trả và bảo hành.
