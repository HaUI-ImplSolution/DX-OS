# Cửa hàng điện máy: thêm quy trình vào ứng dụng có sẵn

Ví dụ này là một ứng dụng nhỏ của một cửa hàng điện máy, viết như của một nhà phát triển ngoài và không dùng mã nào của DX-OS.
Ứng dụng có sổ bán hàng chạy bình thường.
Khi cửa hàng cần quy trình đổi trả và bảo hành, họ cài DX-OS cạnh ứng dụng, thêm tệp mô hình và gọi API.
DX-OS lo động cơ quy trình, phân quyền và lịch sử.

Trạng thái: đã có hai mô hình và các bước cài đặt ở mục "Cài DX-OS và nối quy trình"; mã ứng dụng chưa có.
Kế hoạch và tiến độ ở [`docs/dynamic-core/UNG_DUNG_MAU.md`](../../docs/dynamic-core/UNG_DUNG_MAU.md); việc theo [#343](https://github.com/HaUI-ImplSolution/DX-OS/issues/343).

## Ai là ai

| Người | Có tài khoản ở | Làm gì |
|---|---|---|
| Khách hàng | Không có | Nói với nhân viên cần đổi trả hoặc bảo hành; chỉ xuất hiện qua mã hóa đơn |
| Nhân viên bán hàng | Ứng dụng của cửa hàng | Lập yêu cầu đổi trả hoặc bảo hành thay khách, xem trạng thái ngay trong ứng dụng |
| Quản lý cửa hàng | DX-OS (vai `STORE_STAFF`) | Duyệt, từ chối hoặc yêu cầu bổ sung phiếu đổi trả ở hộp việc của DX-OS |
| Kỹ thuật viên | DX-OS (vai `AGENT`) | Nhận và xử lý yêu cầu bảo hành trong hạn ở hộp việc của DX-OS |

Ứng dụng giữ hai client máy của DX-OS, một cho mỗi mô hình: vai `CUSTOMER` của đổi trả và vai `REQUESTER` của bảo hành.
Mọi hồ sơ do ứng dụng lập đều mang tên client đó trong lịch sử.

## Hai quy trình

| Quy trình | Cách dùng DX-OS | Họ quy trình |
|---|---|---|
| Đổi trả hàng | Dùng nguyên mẫu `retail_return` | `approval` |
| Bảo hành | `extends: support_desk`, thêm số seri, ngày mua và mã hóa đơn; có hạn xử lý | `work_item` |

Mã hóa đơn nối hồ sơ ở DX-OS với đơn bán của ứng dụng; ứng dụng còn lưu mã hồ sơ `dxos_id` trong bảng của nó và cập nhật trạng thái khi webhook về.
Mẫu `retail_return` đòi sản phẩm có sẵn trong DX-OS, nên script cài đặt nạp một ít sản phẩm từ danh mục của ứng dụng sang DX-OS một lần.

## Điều ví dụ phải chứng minh

- Người ngoài thêm quy trình chỉ bằng một tệp mô hình, vai, quyền theo dòng, hạn xử lý và lịch sử không sửa được.
- Phần tích hợp nhỏ và đo được: tệp mô hình, biến client trong `.env`, vài hàm trong mã ứng dụng (gửi hồ sơ, nhận webhook); README ghi số dòng mã tích hợp thêm vào khi ví dụ xong.
- Hai cách dùng mẫu cùng có mặt: dùng nguyên và mở rộng.
- Người duyệt dùng giao diện có sẵn của DX-OS; ứng dụng chỉ có liên kết sang đó, và ai muốn tự dựng giao diện thì gọi API theo OpenAPI của mô hình, có thể sinh client theo [`docs/EXTENSIONS.md`](../../docs/EXTENSIONS.md).
- Quy trình đổi được an toàn: kế hoạch có mức rủi ro rồi mới áp dụng, và quay về được.
- Sự kiện quá hạn của bảo hành đến được ứng dụng qua webhook.

## Kịch bản demo

1. Cửa hàng chạy ứng dụng, bán hàng bình thường.
2. Người vận hành cài DX-OS cạnh ứng dụng và chạy script cài đặt: áp dụng hai mô hình, nối ứng dụng bằng `dxos connect` (đăng ký hai client và ghi `.env`), tạo tài khoản quản lý và kỹ thuật viên, nạp sản phẩm.
3. Nhân viên mở một đơn bán, bấm Đổi trả; ứng dụng gửi hồ sơ, hiện trạng thái "Chờ duyệt" và liên kết sang DX-OS lấy từ `webUrl` của hồ sơ.
4. Quản lý mở liên kết, đăng nhập, thấy hồ sơ ở hộp việc và duyệt; webhook về, ứng dụng đổi trạng thái thành "Đã duyệt".
5. Nhân viên lập yêu cầu bảo hành; kỹ thuật viên nhận ở hộp việc; hạn demo được đặt ngắn để quá hạn và ứng dụng hiện cảnh báo từ webhook.
6. Người vận hành đổi mô hình bảo hành bằng `dxos plan`, xem mức rủi ro rồi `dxos apply`, và quay về bản trước.
7. `dxos history verify` báo lịch sử nguyên vẹn.

## Giao diện của ứng dụng

Tối đa ba màn hình: danh sách đơn bán, chi tiết đơn (nút Đổi trả và Bảo hành, trạng thái, liên kết sang DX-OS) và danh sách yêu cầu đang xử lý.
Trực quan, ít chức năng, đủ để demo.

## Giới hạn nói thẳng

- Nhân viên duyệt có hai tài khoản: một ở ứng dụng, một ở DX-OS do script tạo hộ.
  Đăng nhập một lần là hướng thảo luận riêng.
- Quyền theo dòng `own` áp cho client, không áp cho từng nhân viên hay khách; ứng dụng tự lọc hồ sơ nào ai được xem.
- Lịch sử ghi tài khoản dịch vụ của client là người thao tác khi ứng dụng gọi; người nộp thật nằm ở mã hóa đơn.
- Sản phẩm được nạp một lần, không đồng bộ liên tục.
- DX-OS phải chạy cùng ứng dụng web (không dùng `dxos start --core`) để có hộp việc.
- Webhook cần ba việc: khai đích trong `deploy/webhooks/targets.yml`, ghi `webhook: <tên>` ở đầu tệp mô hình, và DX-OS với tới được địa chỉ của ứng dụng.
- Script tạo tài khoản và nạp sản phẩm chạy bởi người vận hành DX-OS bằng quyền ADMIN của `dxos-cli`; thông tin đó không bao giờ vào ứng dụng.

## Ngăn xếp và cấu trúc dự kiến

Node.js 24, Fastify và SQLite tích hợp của Node, giao diện HTML đơn giản; phụ thuộc duy nhất là Fastify, đã qua kiểm giấy phép ở [`DEPENDENCIES.txt`](../../DEPENDENCIES.txt).
Ứng dụng chạy bằng `docker-compose.yml` riêng, không phụ thuộc cây mã của DX-OS.

```text
appliance-store/
  docker-compose.yml   ứng dụng và cơ sở dữ liệu của nó
  app/                 mã ứng dụng và giao diện
  models/              tệp mô hình của hai quy trình (retail.yaml, warranty.yaml)
  catalog/             danh mục sản phẩm (products.csv), dùng cho ứng dụng và cho DX-OS
  setup/               script tạo tài khoản quản lý, kỹ thuật viên và nạp sản phẩm
```

## Cài DX-OS và nối quy trình

Làm trên máy chạy ứng dụng (Linux hoặc WSL2, có Docker Compose v2), trong thư mục `examples/appliance-store`.
Bước nào quan trọng thì người vận hành tự gõ; không script nào làm thay.

1. Cài DX-OS theo [`docs/QUICKSTART.md`](../../docs/QUICKSTART.md) đường 2 bước 1 rồi chạy `dxos start` (bản đầy đủ, để có hộp việc).
2. Khai đích webhook `store`, vì cả hai mô hình ghi `webhook: store`.
   Bí mật dùng chung nằm ở `.env` của ứng dụng và ở `targets.yml` của DX-OS.
   Địa chỉ dùng cổng 3000 của ứng dụng; nếu `~/.dxos/deploy/webhooks/targets.yml` đã có mục `targets:` thì thêm `store:` vào mục đó.

   ```bash
   SECRET=$(openssl rand -hex 24)
   (umask 077; echo "DXOS_WEBHOOK_SECRET=$SECRET" >> .env)
   cat >> ~/.dxos/deploy/webhooks/targets.yml <<EOF
   targets:
     store:
       url: http://host.docker.internal:3000/hooks/dxos
       secret: $SECRET
   EOF
   ```

3. Xem kế hoạch rồi áp dụng hai mô hình.
   Chưa khai đích ở bước 2 thì kế hoạch báo đích `store` chưa cấu hình và `dxos apply` từ chối.

   ```bash
   dxos plan models/retail.yaml && dxos apply models/retail.yaml
   dxos plan models/warranty.yaml && dxos apply models/warranty.yaml
   ```

4. Nối ứng dụng: mỗi lệnh đăng ký một client, ghi biến vào `.env` và đăng nhập thử.

   ```bash
   dxos connect retail --role CUSTOMER
   dxos connect warranty --role REQUESTER
   ```

5. Tạo tài khoản và nạp sản phẩm.
   Script in mật khẩu tạm của quản lý và kỹ thuật viên một lần; mỗi người đổi mật khẩu ở lần đăng nhập đầu.
   Chạy lại không tạo trùng.

   ```bash
   setup/setup.sh
   ```

   `setup.sh` đọc `~/.dxos/deploy/.env`; đặt `--deploy-dir` hoặc `DXOS_DEPLOY_DIR` khi DX-OS cài ở nơi khác.
   Nó dùng client ADMIN `dxos-cli` của bản cài đó, và client này không bao giờ vào ứng dụng.
   Để nạp sản phẩm nó tạo client `appliance-setup` rồi xóa ngay khi xong.

Đổi mô hình bảo hành về sau cũng qua `dxos plan` rồi `dxos apply`: thêm trường là mức `SAFE`, bỏ trường là mức `LOSS` và cần `--accept LOSS`.
Hạn xử lý của bảo hành trong `models/warranty.yaml` là 2 phút để demo thấy quá hạn; đặt `8h` hoặc `2d` cho việc thật.
Webhook gửi mọi thay đổi của cả hai mô hình, kể cả sản phẩm, nên ứng dụng chỉ xử lý sự kiện của phiếu đổi trả và yêu cầu bảo hành.

## Không làm

- Tự dựng màn hình duyệt trong ứng dụng.
- Đăng nhập một lần và đồng bộ danh mục sản phẩm liên tục.
- Chức năng ngoài sổ bán hàng, đổi trả và bảo hành.
- Đưa tên thực thể hay luồng của cửa hàng điện máy vào mã lõi.
