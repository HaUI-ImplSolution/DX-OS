# Ứng dụng mẫu của bên ngoài

Quyết định, tiêu chí và tiến độ của ứng dụng mẫu chứng minh việc một ứng dụng có sẵn thêm quy trình bằng DX-OS.
Việc cụ thể theo issue [#343](https://github.com/HaUI-ImplSolution/DX-OS/issues/343), kế hoạch chi tiết ở [`docs/appliance-store-plan/`](../appliance-store-plan/00_OVERVIEW.md); làm trong một PR.
Lõi đã có `dxos connect` ([#344](https://github.com/HaUI-ImplSolution/DX-OS/issues/344)) và `webUrl` ([#345](https://github.com/HaUI-ImplSolution/DX-OS/issues/345)) cho ứng dụng này dùng.
Việc tồn đọng chung nằm ở [`BACKLOG.md`](./BACKLOG.md).
Xóa tệp này khi #343 xong.

## 1. Mục đích

Ứng dụng mẫu cho thấy một ứng dụng của người ngoài, chạy sẵn và không liên quan đến dự án này, dùng DX-OS để có thêm quy trình mà không tự viết động cơ quy trình.
Cách tích hợp giống cách người ta dùng Keycloak, MinIO, Superset hay Mattermost: cài dịch vụ cạnh ứng dụng, thêm một tệp cấu hình, ứng dụng gọi vào.
Nó đáp ứng câu "PaaS phục vụ các ứng dụng tầng trên" của đề bằng một bằng chứng chạy được.

## 2. Đã chốt

| Mục | Quyết định |
|---|---|
| Bản chất | Ứng dụng nhỏ như của một nhà phát triển ngoài, đặt trong `examples/`, tự chứa với `docker-compose.yml` và README riêng, không dùng mã của dự án |
| Chức năng sẵn có | Chức năng thường ngày không có quy trình; quy trình là nhu cầu mới của người dùng ứng dụng |
| Lĩnh vực | Cửa hàng điện máy nhỏ; được trùng với lĩnh vực của mẫu có sẵn, quan trọng là thấy rõ vai trong quy trình |
| Vai | Nhân viên bán hàng dùng ứng dụng ngoài; khách không có tài khoản nào; quản lý cửa hàng duyệt đổi trả ở hộp việc của DX-OS; kỹ thuật viên xử lý bảo hành ở hộp việc của DX-OS |
| Quy trình dùng nguyên | Đổi trả hàng: mẫu `retail_return` (họ `approval`), vai `CUSTOMER` do client của ứng dụng đảm nhận, vai `STORE_STAFF` là quản lý |
| Quy trình mở rộng | Bảo hành: `extends: support_desk` (họ `work_item`), thêm trường số seri và ngày mua; vai `REQUESTER` do client của ứng dụng đảm nhận, vai `AGENT` là kỹ thuật viên, có hạn xử lý và webhook khi quá hạn |
| Màn hình duyệt | Dùng giao diện web có sẵn của DX-OS (hộp việc), không tự dựng màn hình duyệt |
| Tự dựng giao diện | README nói rõ ứng dụng cũng có thể tự dựng giao diện và gọi API của DX-OS |
| Liên kết sang DX-OS | Ứng dụng có liên kết rõ ràng sang hộp việc và hồ sơ trên giao diện DX-OS; không bắt người dùng nhập địa chỉ; dùng `webUrl` trong hồ sơ của API và trong webhook |
| Người yêu cầu | Không có tài khoản DX-OS; client máy của ứng dụng lập hồ sơ thay họ và ghi họ là ai vào một trường dữ liệu |
| Nối ứng dụng với DX-OS | Người vận hành tự gõ `dxos apply` cho hai mô hình rồi `dxos connect` cho từng mô hình; lệnh ghi biến client vào `.env` của ứng dụng, compose của ứng dụng thêm `env_file` và `extra_hosts`; không cần đổi `PUBLIC_HOST` |
| Người duyệt | Có tài khoản Keycloak riêng; một script tạo hộ các tài khoản nhân viên và gán vai qua API của DX-OS; README nêu rõ nhân viên có hai tài khoản và đăng nhập một lần là hướng thảo luận riêng |
| Dữ liệu tham chiếu | `retail_return` đòi sản phẩm có sẵn trong DX-OS; script cài đặt nạp một lần một ít sản phẩm từ danh mục của ứng dụng, và README nêu đây là một bước tích hợp |
| Webhook | README nêu ba việc: khai đích ở `deploy/webhooks/targets.yml`, ghi `webhook: <tên>` trong mô hình, và đường mạng tới ứng dụng; demo dùng `host.docker.internal`; backend của DX-OS có `extra_hosts` để tới được ứng dụng cùng máy |
| Nối hồ sơ | Ứng dụng lưu `dxos_id` trong bảng của nó và dùng mã hóa đơn làm khóa nối; cập nhật trạng thái khi webhook về |
| Ngăn xếp | Ưu tiên thành phần đã có trong `DEPENDENCIES.txt`: Node.js 24, Fastify, SQLite tích hợp của Node, HTML đơn giản; sau đó mới đến tiêu chí nhanh, gọn, dễ dựng |
| Giao diện | Trực quan, dễ dùng, dễ demo; ít chức năng, nói được giá trị của lõi |
| Công cụ ngoài | Apprise đã loại vì giấy phép; client có thể sinh bằng OpenAPI Generator theo `docs/EXTENSIONS.md` |

## 3. Điều ứng dụng mẫu phải chứng minh

- Người dùng chỉ thêm một tệp mô hình rồi ứng dụng có quy trình: vai, quyền theo dòng, hạn xử lý, lịch sử không sửa được.
- Phần tích hợp nhỏ và đo được: tệp mô hình, biến client trong `.env`, vài hàm trong mã ứng dụng (gửi hồ sơ, nhận webhook); README nêu số dòng mã thêm vào.
- Hai cách dùng mẫu cùng có mặt: dùng nguyên và mở rộng.
- Quy trình đổi được an toàn: kế hoạch có mức rủi ro rồi mới áp dụng, và quay về được.
- Sự kiện quá hạn hoặc leo thang đến được ứng dụng hoặc kênh chat qua webhook.

## 4. Ngoài phạm vi

- Tự dựng màn hình duyệt trong ứng dụng mẫu.
- Ứng dụng nhiều chức năng hay giao diện cầu kỳ.
- Dùng mã, mô hình hay khung giao diện của dự án trong ứng dụng.
- Lõi chứa tên thực thể, vai hay luồng của lĩnh vực cửa hàng điện máy; nghiệp vụ nằm trong tệp mô hình của ví dụ.

## 5. Tiến độ

| Việc | Trạng thái |
|---|---|
| Chốt hướng và tiêu chí | Xong |
| Chốt hướng danh tính: tạo hộ tài khoản nhân viên bằng script | Xong |
| Chốt dữ liệu tham chiếu, webhook và cách nối hồ sơ | Xong |
| Liên kết sang giao diện web: `webUrl` trong API và webhook (#345) | Xong |
| Chốt lĩnh vực | Xong |
| Xác nhận người thao tác trong lịch sử và cách script lấy quyền ADMIN | Xong, qua đọc mã |
| Cập nhật issue #343 theo hướng mới | Xong |
| Giảm số bước tích hợp: lệnh `dxos connect` (#344) | Xong |
| Thêm công thức OpenAPI Generator vào `docs/EXTENSIONS.md` | Xong |
| `extra_hosts` cho backend của DX-OS và tài liệu đường mạng webhook | Xong |
| Script `setup/setup.sh`: tạo tài khoản quản lý, kỹ thuật viên và nạp sản phẩm, chạy thử trên stack thử | Xong |
| Kiểm lịch sử ghi tên client là người thao tác | Chưa làm |
| Tệp mô hình bảo hành: `ai.tasks` của `support_desk` bị thừa kế, nên `models/warranty.yaml` đặt `ai: null` | Xong |
| Phụ thuộc của ứng dụng: dự kiến chỉ Fastify, nếu thêm thì qua kiểm giấy phép OSI và ghi ở `DEPENDENCIES.txt` | Chưa làm |
| README của ví dụ ở `examples/appliance-store/README.md`: mục tiêu, vai, kịch bản demo, tiêu chí, giới hạn, điều không làm | Xong |
| Ứng dụng mẫu: chức năng thường ngày | Chưa làm |
| Tệp mô hình cho hai quy trình (`models/`), kiểm bằng `dxos lint`, `plan`, `apply`, `conformance` | Xong |
| Tích hợp: gửi hồ sơ, nhận webhook, liên kết sang DX-OS | Chưa làm |
| Kiểm chạy theo README trên máy sạch | Chưa làm |
| Phần pitch (tầm nhìn, nỗi đau, thị trường, cạnh tranh) vào `docs/DEMO.md`, nhận định thị trường ghi là giả thuyết vì chưa có khách hàng thật (#231) | Chưa làm |
| Xóa dòng #343 ở `BACKLOG.md` mục 3.1 và tệp này | Chưa làm |
