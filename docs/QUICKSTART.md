# Bắt đầu nhanh: đưa quy trình duyệt vào ứng dụng của bạn

Làm theo trang này, bạn có một quy trình duyệt chạy được: người nộp tạo hồ sơ, người duyệt duyệt hoặc từ chối.
DX-OS lo phần còn lại: lưu dữ liệu, phân quyền, quy trình và lịch sử.

Chọn một trong hai đường và đi hết đường đó, từ cài đặt đến dùng được; mỗi đường tự đủ, không cần đọc đường kia.

- [**Đường 1, không có code**](#đường-1-không-có-code): người dùng làm việc trên giao diện web có sẵn của DX-OS.
- [**Đường 2, có code**](#đường-2-có-code): ứng dụng của bạn, chạy trong Docker, gọi API của DX-OS.

Ở mọi bước, bạn thao tác bằng **Console** (giao diện web `/console`) hoặc **CLI** (lệnh `dxos`), tùy bạn thích cách nào.
Ví dụ là quy trình duyệt đơn đặt hàng với hai vai: `REQUESTER` (người nộp) và `APPROVER` (người duyệt).

---

## Đường 1. Không có code

### Bước 1. Cài và chạy DX-OS

Cần máy Linux (hoặc Windows có WSL2) đã cài Docker và Docker Compose v2.

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

`dxos start` in ra mật khẩu tạm của tài khoản `admin`.
Quên mật khẩu thì chạy `dxos admin` để lấy mật khẩu tạm mới.

Mở `http://localhost:9080/console`, đăng nhập bằng `admin` và đổi mật khẩu.
`admin` chỉ để quản trị DX-OS; ứng dụng của bạn không dùng tài khoản này.

### Bước 2. Tạo và áp dụng mô hình

Mô hình là một tệp YAML mô tả hồ sơ có những trường nào, ai được làm gì và quy trình duyệt ra sao.
Tạo một thư mục làm việc bất kỳ (ví dụ `mkdir ~/dxos-model && cd ~/dxos-model`) và lưu đoạn sau thành `dxos.yaml` ở đó.
Tệp nằm ngoài thư mục cài `~/.dxos`, nên cập nhật DX-OS không đụng tới nó; nên giữ nó trong git của bạn.
Dùng Console thì không cần lưu tệp, chỉ cần dán nội dung.
Không muốn gõ lại thì tải tệp mẫu về: `curl -fsSLO https://raw.githubusercontent.com/HaUI-ImplSolution/DX-OS/main/examples/order_approval/dxos.yaml`.

```yaml
format: 1
name: order_approval
roles:
  - {name: REQUESTER, description: Người nộp đơn}
  - {name: APPROVER, description: Người duyệt đơn, staff: true}
entities:
  order_request:
    label: Đơn đặt hàng
    fields:
      title: {type: text, label: Tiêu đề, required: true}
      amount: {type: number, label: Số tiền, required: true}
    access: {REQUESTER: own, APPROVER: all}
    process: {family: approval, handler: APPROVER}
```

- `roles`: các vai trong quy trình.
- `access`: `own` là chỉ thấy hồ sơ của mình, `all` là thấy mọi hồ sơ.
- `process`: dùng quy trình duyệt có sẵn. Hồ sơ mới ở trạng thái `submitted`; `APPROVER` chọn `approve` (duyệt), `reject` (từ chối) hoặc `return` (trả lại để bổ sung). Từ chối và trả lại phải có lý do.

Cách viết đầy đủ ở [`MODEL.md`](./MODEL.md).
Không muốn viết từ đầu thì chọn một mẫu có sẵn rồi sửa.

Áp dụng mô hình:

- **Console:** tab **Soạn và áp dụng**, dán nội dung tệp, bấm **Xem kế hoạch**, rồi **Áp dụng**. Tab **Thư viện mẫu** có các mẫu dựng sẵn.
- **CLI:** chạy trong thư mục chứa `dxos.yaml` (hoặc ghi đường dẫn đầy đủ của tệp).

  ```bash
  dxos lint dxos.yaml    # kiểm tệp
  dxos plan dxos.yaml    # xem sẽ đổi gì, chưa đổi gì cả
  dxos apply dxos.yaml   # áp dụng
  ```

Chỉ cần áp dụng một lần, bằng Console hoặc CLI, không cần cả hai.
Kết quả: tab **Mô hình đang chạy** có thẻ **Order approval**, phiên bản 1.

Sau này sửa tệp thì chạy lại `dxos plan` rồi `dxos apply`.
Nếu kế hoạch báo thay đổi mức `MIGRATION` hoặc `LOSS` thì thêm `--accept MIGRATION` hoặc `--accept LOSS` vào `dxos apply`.

### Bước 3. Tạo người dùng và gán vai

Ở tab **Người dùng** của Console, bấm **Tạo người dùng** cho mỗi người thật, điền tên đăng nhập và họ tên.
Hệ thống sinh mật khẩu tạm và chỉ hiện một lần: chép gửi cho người đó, họ phải đổi mật khẩu ở lần đăng nhập đầu.
Rồi tìm người đó trong danh sách, bấm vào tên và bấm vai cần gán ở mục **Order approval**: `REQUESTER` cho người nộp, `APPROVER` cho người duyệt.
Vai có hiệu lực ngay khi người đó đăng nhập lại.

### Bước 4. Dùng

Người nộp mở `http://localhost:9080/`, đăng nhập bằng tài khoản được cấp, vào menu **Đơn đặt hàng**, bấm **Tạo mới**, điền tiêu đề và số tiền rồi bấm **Lưu**.
Hồ sơ ở trạng thái **Chờ xử lý**.
Người duyệt đăng nhập, thấy hồ sơ trong **Hộp việc**, mở nó và bấm **Duyệt**, **Từ chối** hoặc **Yêu cầu bổ sung**.
Hồ sơ đổi trạng thái và lịch sử ghi lại ai làm gì, lúc nào.

Chỉ muốn xem thử, chưa cần mô hình của mình: `dxos demo` áp dụng mô hình Một cửa sinh viên và tạo sẵn tài khoản thử cùng hồ sơ mẫu.
Muốn nhận thông báo khi hồ sơ đổi (vào chat hoặc hệ thống khác) thì xem [webhook](./INTEGRATIONS.md#webhook-sự-kiện).

---

## Đường 2. Có code

Bạn đã có ứng dụng chạy bằng image riêng trong Docker Compose của bạn.
Bạn cài DX-OS cạnh nó, rồi chỉ thêm vài biến vào ứng dụng để nó gọi DX-OS.
Một ví dụ chạy được nằm ở [`examples/order_approval`](../examples/order_approval/README.md).

### Bước 1. Cài DX-OS cạnh ứng dụng

Trên máy chủ chạy ứng dụng của bạn (Linux hoặc WSL2, có Docker Compose v2):

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
```

Cài xong thì chạy luôn `dxos start`, không cần sửa tệp cấu hình nào.
DX-OS mở ở `http://localhost:9080` trên máy này, và container của ứng dụng gọi nó qua `host.docker.internal` (bước 4).
Chỉ khi người dùng hoặc ứng dụng ở máy khác cần vào DX-OS thì đặt `PUBLIC_HOST` trong `~/.dxos/deploy/.env` thành IP hoặc tên máy chủ trước lần `dxos start` đầu tiên; danh sách biến ở [`OPERATIONS.md`](./OPERATIONS.md#biến-cấu-hình-env).

```bash
dxos start
```

`dxos start` in ra mật khẩu tạm của tài khoản `admin` (quên thì chạy `dxos admin`), các địa chỉ và việc nên làm tiếp.

### Bước 2. Áp dụng mô hình

Tạo một thư mục (ví dụ `mkdir ~/dxos-model && cd ~/dxos-model`) và lưu mô hình thành `dxos.yaml` ở đó.
Có thể tải tệp mẫu về thay vì gõ lại:

```bash
curl -fsSLO https://raw.githubusercontent.com/HaUI-ImplSolution/DX-OS/main/examples/order_approval/dxos.yaml
```

Nội dung của tệp:

```yaml
format: 1
name: order_approval
roles:
  - {name: REQUESTER, description: Người nộp đơn}
  - {name: APPROVER, description: Người duyệt đơn, staff: true}
entities:
  order_request:
    label: Đơn đặt hàng
    fields:
      title: {type: text, label: Tiêu đề, required: true}
      amount: {type: number, label: Số tiền, required: true}
    access: {REQUESTER: own, APPROVER: all}
    process: {family: approval, handler: APPROVER}
```

Mô hình khai ba thứ: các vai (`roles`), ai thấy hồ sơ nào (`access`, `own` là hồ sơ của mình, `all` là mọi hồ sơ) và quy trình duyệt có sẵn (`process`).
Hồ sơ mới ở trạng thái `submitted`; `APPROVER` chọn `approve`, `reject` hoặc `return`.
Cách viết đầy đủ ở [`MODEL.md`](./MODEL.md).

Chạy trong thư mục đó:

```bash
dxos plan dxos.yaml    # xem sẽ đổi gì, chưa đổi gì cả
dxos apply dxos.yaml
```

Muốn dùng giao diện: mở `http://localhost:9080/console` (từ máy khác thì dùng địa chỉ của `PUBLIC_HOST`), đăng nhập `admin`, tab **Soạn và áp dụng**, dán nội dung tệp rồi **Áp dụng**.

### Bước 3. Nối ứng dụng với DX-OS

Mỗi client là một tài khoản máy giữ đúng một vai.
Chạy trong thư mục của dự án ứng dụng, nơi có `docker-compose.yml`:

```bash
dxos connect order_approval --role REQUESTER --role APPROVER
```

Lệnh đăng ký một client cho mỗi vai (`dxos-app-order_approval-requester` và `dxos-app-order_approval-approver`) và ghi vào `.env` ở thư mục hiện tại: `DXOS_BASE_URL`, rồi `DXOS_<VAI>_CLIENT_ID` và `DXOS_<VAI>_CLIENT_SECRET` cho từng vai.
Tệp `.env` mới tạo có quyền `600`, tệp có sẵn được nối thêm và không bị ghi đè.
Cuối cùng lệnh đăng nhập thử bằng client đầu tiên, gọi API của mô hình, rồi in hai dòng cần thêm vào `docker-compose.yml` ở bước 4.
`order_approval` là `name` của tệp mô hình đã áp dụng và `--role` là tên một vai khai trong tệp đó; đổi `name` hay tên vai trong mô hình thì đổi theo trong lệnh.
`DXOS_BASE_URL` là `http://host.docker.internal:9080`, địa chỉ mà container trên cùng máy với DX-OS dùng được; `--base-url` đổi nó khi ứng dụng chạy ở máy khác, và dòng `DXOS_BASE_URL` đã có trong `.env` thì được giữ nguyên.

`secret` chỉ hiện một lần và hệ thống không giữ lại, nên lệnh ghi thẳng vào `.env` mà không in ra màn hình.
Chạy lại lệnh thì client đã có secret trong `.env` được giữ nguyên.
Client đã đăng ký mà `.env` không còn secret của nó thì lệnh dừng và nói thêm `--rotate` để cấp secret mới (secret cũ ngừng dùng được).
`--app` đổi tên ứng dụng dùng để đặt tên client, `--prefix` đổi tiền tố biến (ví dụ `--prefix DXOS_RETAIL` cho mô hình thứ hai) và `--env-file` ghi vào tệp khác.
Tệp `.env` này chứa secret, đừng đưa nó vào git.
Trong WSL2, dự án nằm trên ổ Windows có đường dẫn dạng `/mnt/c/...`, còn thư mục home của WSL là một nơi khác; chạy `pwd` và `ls .env` để chắc đang ở đúng thư mục trước khi chạy lệnh.

Cũng có thể đăng ký từng client bằng `dxos client add <tên> --model M --role R --env <TIỀN_TỐ>`, hoặc ở tab **Ứng dụng ngoài** của Console: bấm **Đăng ký ứng dụng**, nhập tên, chọn mô hình và vai, bấm **Đăng ký**.
Cách đó hiện `clientId` (dạng `dxos-app-<tên>`) và secret một lần duy nhất; chép cả hai vào `.env` bằng tay.

### Bước 4. Thêm hai dòng vào service của ứng dụng

Trong `docker-compose.yml` của bạn, thêm vào service ứng dụng:

```yaml
services:
  my-app:
    image: my-app:latest          # image của bạn, giữ nguyên
    env_file: .env
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

`env_file` đưa các biến của `.env` vào container, và `extra_hosts` cho container trên Linux gọi được `host.docker.internal` (Docker Desktop trên Windows và macOS có sẵn tên này).
Các biến trong `.env`, tất cả đều bắt buộc:

| Biến | Giá trị |
|---|---|
| `DXOS_BASE_URL` | Địa chỉ DX-OS mà container truy cập được: `http://host.docker.internal:9080` |
| `DXOS_<VAI>_CLIENT_ID` | `clientId` của client, một biến cho mỗi vai ứng dụng dùng |
| `DXOS_<VAI>_CLIENT_SECRET` | `secret` của client đó |

Tên biến do ứng dụng của bạn tự đặt; DX-OS không đọc chúng.
Ứng dụng chỉ dùng một vai thì chỉ cần ba biến.

Kiểm tra hai việc, mỗi việc một lệnh:

```bash
# container tới được DX-OS: phải in 200
docker run --rm --add-host host.docker.internal:host-gateway curlimages/curl -s -o /dev/null -w "%{http_code}\n" http://host.docker.internal:9080/realms/dxos/.well-known/openid-configuration

# client đăng nhập và gọi được API: phải in "works"
dxos client test order_approval-requester
```

`dxos connect` đã kiểm client đầu tiên; `dxos client test` hỏi secret của client rồi đi qua ba bước (tới được địa chỉ, đăng nhập, gọi API của mô hình) và báo bước nào hỏng.
Lỗi nào cũng có cách sửa ở [Gặp lỗi thì xem gì](#gặp-lỗi-thì-xem-gì).

### Bước 5. Gọi DX-OS từ mã của ứng dụng

Ứng dụng cần làm hai việc: lấy token, rồi gọi API kèm token.

**Lấy token** bằng client của vai cần dùng.
Token sống khoảng 5 phút; giữ lại để dùng nhiều lần và lấy token mới khi gặp lỗi `401`.

```bash
curl -fsS \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode "client_id=$DXOS_REQUESTER_CLIENT_ID" \
  --data-urlencode "client_secret=$DXOS_REQUESTER_CLIENT_SECRET" \
  "$DXOS_BASE_URL/realms/dxos/protocol/openid-connect/token"
```

Lấy `access_token` trong JSON trả về.

**Tạo hồ sơ** bằng token của `REQUESTER`:

```bash
curl -fsS -X POST "$DXOS_BASE_URL/api/v1/data/order_approval/order_request" \
  -H "Authorization: Bearer $REQUESTER_TOKEN" \
  -H "Idempotency-Key: order-0001" \
  -H "Content-Type: application/json" \
  -d '{"title": "Mua 10 màn hình", "amount": 50000000}'
```

Kết quả có `data.id` là mã hồ sơ, trạng thái `submitted`.
`Idempotency-Key` là khóa bạn đặt cho mỗi yêu cầu nghiệp vụ; gửi lại cùng khóa thì không tạo hồ sơ thứ hai.

**Duyệt** bằng token của `APPROVER`:

```bash
curl -fsS -X POST "$DXOS_BASE_URL/api/v1/data/order_approval/order_request/<id>/actions/approve" \
  -H "Authorization: Bearer $APPROVER_TOKEN" \
  -H "Content-Type: application/json" -d '{}'
```

Từ chối hoặc trả lại thì gọi `reject` hoặc `return` kèm lý do: `-d '{"note": "Thiếu báo giá"}'`.
Trạng thái chỉ đổi qua `actions`, không sửa trực tiếp trường `status`.

**Đọc hồ sơ:** `GET $DXOS_BASE_URL/api/v1/data/order_approval/order_request/<id>`, và `.../<id>/events` cho lịch sử.

Cùng các bước bằng Python:

```python
import os, requests

BASE = os.environ["DXOS_BASE_URL"]
URL = f"{BASE}/api/v1/data/order_approval/order_request"

def auth(role):  # role: "REQUESTER" hoặc "APPROVER"
    r = requests.post(f"{BASE}/realms/dxos/protocol/openid-connect/token", data={
        "grant_type": "client_credentials",
        "client_id": os.environ[f"DXOS_{role}_CLIENT_ID"],
        "client_secret": os.environ[f"DXOS_{role}_CLIENT_SECRET"],
    })
    r.raise_for_status()
    return {"Authorization": f"Bearer {r.json()['access_token']}"}

created = requests.post(URL, headers={**auth("REQUESTER"), "Idempotency-Key": "order-0001"},
                        json={"title": "Mua 10 màn hình", "amount": 50000000})
created.raise_for_status()
order_id = created.json()["data"]["id"]

requests.post(f"{URL}/{order_id}/actions/approve", headers=auth("APPROVER"), json={}).raise_for_status()
```

Bản chạy được, chỉ dùng Python chuẩn và đóng gói sẵn trong Docker Compose, ở [`examples/order_approval`](../examples/order_approval/README.md).
Danh sách trường và hành động của mô hình nằm trong OpenAPI riêng của nó: `GET /api/v1/meta/models/order_approval/openapi`.
Dùng tài liệu này để sinh client thay vì viết tay.

### Bước 6. Biết khi hồ sơ đổi (không bắt buộc)

- **Hỏi lại:** gọi `GET` hồ sơ hoặc danh sách, ví dụ `?filter.status=approved`. Cách đơn giản nhất.
- **Webhook:** DX-OS gọi vào địa chỉ của ứng dụng mỗi khi hồ sơ đổi, kèm chữ ký để kiểm. Cách khai ở [`INTEGRATIONS.md`](./INTEGRATIONS.md#webhook-sự-kiện); ứng dụng cùng máy dùng địa chỉ `http://host.docker.internal:<cổng>/...` trong `deploy/webhooks/targets.yml`.

---

## Gặp lỗi thì xem gì

| Dấu hiệu | Nguyên nhân thường gặp | Cách sửa |
|---|---|---|
| Container không tới được DX-OS (`curl` in `000`, "connection refused") | Service thiếu `extra_hosts`, hoặc `DXOS_BASE_URL` dùng `localhost`, hoặc ứng dụng chạy ở máy khác | Thêm `extra_hosts` như bước 4 và dùng `DXOS_BASE_URL=http://host.docker.internal:9080`; ứng dụng ở máy khác thì đặt `PUBLIC_HOST` thành IP hoặc tên máy chủ, chạy `dxos start`, rồi sửa `DXOS_BASE_URL` trong `.env` thành `http://<PUBLIC_HOST>:9080` |
| Đăng nhập báo `401` hoặc "Invalid client credentials" | Sai `clientId` (phải là `dxos-app-<tên>`, không phải `<tên>`) hoặc sai, mất secret | Kiểm bằng `dxos client test <tên>`; mất secret thì chạy lại `dxos connect` với `--rotate`, hoặc `dxos client rotate <tên> --env <TIỀN_TỐ>` |
| Gọi API báo `400` | Thiếu trường bắt buộc, hoặc `reject` và `return` không kèm `note` | Đọc nội dung lỗi: nó nêu trường hoặc lý do còn thiếu; gửi lại cho đủ |
| Gọi API báo `401` | Token hết hạn (sống khoảng 5 phút) | Lấy token mới rồi gọi lại |
| Gọi API báo `403` | Client của vai này không được làm việc đó (ví dụ `REQUESTER` gọi `approve`), hoặc gọi mô hình khác | Dùng client của đúng vai; không thử lại cùng lời gọi |
| Gọi API báo `409` | Hồ sơ không ở trạng thái cho hành động này, hoặc `Idempotency-Key` đã dùng cho nội dung khác | Đọc lại hồ sơ rồi quyết định; dùng khóa mới cho yêu cầu mới |
| `dxos apply` bị từ chối | Kế hoạch có thay đổi nặng hơn mức an toàn, hoặc tệp sai | Đọc kết quả `dxos plan`; chấp nhận thì thêm `--accept MIGRATION` hoặc `--accept LOSS`; tệp sai thì `dxos lint` chỉ đúng dòng và cột |
| `/console` trả 404 | DX-OS đang chạy chế độ chỉ lõi | `dxos start --full` |
| Quên mật khẩu `admin` | | `dxos admin` in mật khẩu tạm mới |
| Không rõ dịch vụ nào hỏng | | `dxos status`, rồi `dxos logs <dịch vụ>` |

Vẫn không rõ nguyên nhân thì xem [xử lý sự cố](./OPERATIONS.md#ứng-phó-sự-cố).

## Các lệnh nên biết thêm

| Lệnh | Việc |
|---|---|
| `dxos status` | Xem dịch vụ nào đang chạy và các địa chỉ |
| `dxos logs [dịch vụ]` | Đọc log khi có lỗi |
| `dxos doctor` | Kiểm máy và cấu hình trước khi chạy |
| `dxos client list` | Xem các client đã đăng ký, kèm mô hình và vai |
| `dxos client test <tên>` | Kiểm một client đăng nhập và gọi được API |
| `dxos client rotate <tên>` | Cấp secret mới khi mất hoặc lộ; secret cũ hết hiệu lực ngay |
| `dxos client remove <tên>` | Xóa client không còn dùng |
| `dxos backup` | Sao lưu mã hóa dữ liệu và tệp |
| `dxos update` | Cập nhật lên bản mới, giữ cấu hình và dữ liệu |
| `dxos stop` | Dừng hệ thống, giữ dữ liệu |

Chỉ cần API, không cần giao diện web và Console: `dxos start --core`.
Khi đó dùng CLI cho mọi thao tác.

Toàn bộ lệnh ở [`OPERATIONS.md`](./OPERATIONS.md#cài-đặt-và-lệnh-dxos).

## Đọc tiếp

- [`MODEL.md`](./MODEL.md): viết mô hình của riêng bạn.
- [`EXTENSIONS.md`](./EXTENSIONS.md#dịch-vụ-riêng-gọi-api): kiểm thử, đóng gói và nâng cấp ứng dụng gọi API; giao diện web ở tên miền khác.
- [`API.md`](./API.md): các đường dẫn của API.
- [`INTEGRATIONS.md`](./INTEGRATIONS.md): webhook, kéo sự kiện, lịch sử chống sửa.
- [`OPERATIONS.md`](./OPERATIONS.md): biến cấu hình `.env`, sao lưu, khôi phục, xử lý sự cố.
