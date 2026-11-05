# DX-OS: lõi dynamic cho hệ thống hồ sơ có quy trình (Bài dự thi Phần Mềm Nguồn Mở 2026)

**Tiếng Việt** · [English](./README_EN.md)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![AI License](https://img.shields.io/badge/ai%2F-AGPL--3.0-orange.svg)](./ai/LICENSE)

**Đội:** HaUI-ImplSolution · **Trường:** Trường Công nghệ Thông tin và Truyền thông, Đại học Công nghiệp Hà Nội

**Tài liệu:** theo dõi tại [trang docs](https://haui-implsolution.github.io/DX-OS-docs/); hướng thiết kế ở [`docs/dynamic-core/README.md`](./docs/dynamic-core/README.md).

Dự thi **OLP Phần mềm nguồn mở 2026** (khối thi DX-OS, VFOSSA).
Lõi nhận một tệp mô hình khai báo thực thể, trường, vai, quyền và quy trình, rồi dựng bảng dữ liệu, vai đăng nhập, API có phân quyền từng dòng, quy trình phê duyệt và giao diện.
Đổi nghiệp vụ là đổi tệp mô hình, không đổi mã.
Bản phát hành hiện tại là `2.0.0`.
Phiên bản trước của dự án (hai ứng dụng nghiệp vụ viết tay) nằm ở tag `v1.1.1`, và không có đường nâng cấp dữ liệu từ `1.1.x` lên `2.0.0`.

## Trạng thái

Đã chạy được vòng hẹp từ tệp mô hình đến người dùng cuối:

- Đọc và kiểm tra tệp mô hình, báo lỗi theo dòng và cột.
- Áp dụng mô hình vào hệ thống rỗng: schema, bảng, ràng buộc, vai Keycloak, phiên bản và lịch sử từng bước; áp dụng lại cùng tệp thì không làm gì, trừ khi bảng hoặc tài khoản Keycloak đã lệch khỏi tệp thì sửa phần lệch; bị gián đoạn thì áp dụng lại để làm tiếp.
- API chung cho mọi thực thể, quyền theo vai và theo dòng (`all`, `own`, `read`, `unit`, `assigned`), quy tắc bắt buộc, duy nhất và lựa chọn.
- Ba họ mẫu quy trình trên Flowable nhúng: phê duyệt, công việc cần xử lý (có thể tự đóng khi người nộp không xác nhận), đặt chỗ theo khung giờ có kiểm sức chứa; trạng thái hồ sơ chỉ đổi qua quy trình, cùng giao dịch với việc ghi hồ sơ.
- Quy tắc chung của mọi quy trình: từ chối và trả lại cần lý do, người nộp sửa rồi nộp lại hoặc rút hồ sơ, người tạo không tự quyết hồ sơ của mình, hồ sơ quá hạn lên đầu hộp việc.
- Việc làm sau khi duyệt, như thêm người dùng vào nhóm, chạy qua hàng đợi sự kiện và được thử lại khi lỗi.
- Mọi thay đổi của hồ sơ được báo ra ngoài tới địa chỉ web mà mô hình chọn, có chữ ký để bên nhận kiểm tra và được gửi lại khi bên nhận lỗi; các thay đổi của một hồ sơ tới đúng thứ tự, và bên nhận lỡ tin thì lấy lại phần đã lỡ theo số thứ tự.
- Tin báo ra ngoài có thể là một câu đọc được cho Mattermost và Slack: việc mới, sắp đến hạn, quá hạn; việc áp dụng mô hình và thay đổi tài khoản cũng được báo.
- Tệp đính kèm hồ sơ, quét mã độc khi bật.
- Mỗi mô hình có vai riêng: hai mô hình dùng được cùng một tên vai mà người giữ vai của mô hình này không vào được mô hình kia.
- Đơn vị khai báo trong mô hình và đồng bộ thành nhóm Keycloak; giao việc, hộp việc và dòng lịch sử của từng hồ sơ.
- Đổi mô hình khi đã có dữ liệu: xem trước theo mức rủi ro, đổi tên giữ nguyên dữ liệu, báo dòng vi phạm, xác nhận riêng khi mất dữ liệu, quay về phiên bản trước, tiếp tục hoặc hủy bỏ một lần áp dụng dừng giữa chừng.
- Giao diện react-admin dựng từ metadata, bằng tiếng Việt và có chế độ sáng tối, lịch đặt chỗ kéo thả được, không có mã riêng cho thực thể nào.
- Bảng điều khiển cho người triển khai: thư viện mẫu, trình soạn mô hình, xem kế hoạch, áp dụng, lịch sử và quay về, sơ đồ quy trình.
- Một lệnh nhận tệp mô hình trên máy sạch: hệ thống khởi động, tệp được áp dụng và có thể đăng nhập ngay.
- Quản trị người dùng ngay trong bảng điều khiển: xem ai giữ vai nào ở từng ứng dụng, gán và gỡ vai và đơn vị, tạo người dùng với mật khẩu tạm, khóa và mở, đặt lại mật khẩu; mỗi thay đổi được ghi lại kèm người thực hiện và xem lại được trong hộp thoại của người đó.
- Áp dụng mô hình chờ các thao tác đang chạy của mô hình đó rồi mới đổi bảng, và kiểm lại hồ sơ đang mở sau khi đã giữ mô hình, nên không hồ sơ nào lọt qua giữa chừng; hủy bỏ lần áp dụng đầu dọn luôn phần phân quyền mà lần đó đã tạo.
- Lịch sử hồ sơ được nối thành một chuỗi có dấu kiểm và có điểm neo định kỳ; một lệnh chỉ ra dòng nào đã bị sửa, xóa hoặc chèn.
- Thống kê cho mỗi thực thể có quy trình: tổng số, theo trạng thái, quá hạn và theo đơn vị, cùng quyền xem với danh sách; mỗi số mở ra danh sách tương ứng.
- Đổi mô hình được cả từ dòng lệnh để dùng trong tự động hóa, và có lệnh xuất hồ sơ đã xong ra tệp phân tích kèm bản kê để kiểm lại.
- Sao lưu kiểm được cả tệp đính kèm; tệp đã gỡ chỉ rời kho sau một khoảng chờ để bản sao lưu luôn đủ; các dịch vụ tự chạy lại và có lệnh đưa hệ thống về đúng khai báo.
- Ví dụ kết nối thiết bị: một chương trình giả lập tạo hồ sơ sự cố khi chỉ số vượt ngưỡng và không tạo trùng khi gửi lại.
- Đăng nhập một lần qua Keycloak; có thể bắt quản trị viên nhập thêm mã một lần từ ứng dụng xác thực.

Chưa có: thông báo chat, hỏi đáp bằng AI.
Dịch vụ tìm kiếm tri thức có nguồn ở [`ai/`](./ai/README.md) chạy riêng, chưa nằm trong stack.
Các phần này nằm trong [hướng thiết kế](./docs/dynamic-core/README.md).

## Chạy thử

### Cài đặt

Cần máy Linux có Docker với Compose v2, không cần mã nguồn.

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

Bộ cài tải bản phát hành mới nhất, kiểm SHA-256 rồi cài vào `~/.dxos`; lệnh `dxos` nằm ở `~/.local/bin`.
`dxos start` tải image ở lần đầu, chờ mọi dịch vụ sẵn sàng, tạo tài khoản `admin` với mật khẩu tạm in ra màn hình, rồi in các địa chỉ.
Đăng nhập `http://localhost:9080/console` bằng `admin`, đổi mật khẩu, chọn một mẫu ở tab Thư viện mẫu, xem kế hoạch rồi áp dụng.
Đã có tệp mô hình thì `dxos start <tệp>` đưa tệp vào luôn: backend áp dụng tệp khi khởi động nếu tệp là mới, và lệnh in kết quả.
Các lệnh khác (`stop`, `status`, `logs`, `admin`, `update`, `backup`, `plan`, `apply`, `history`, `reconcile`, `archive`, `uninstall`) có ở `dxos help` và [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md#cài-đặt-và-lệnh-dxos).

### Từ mã nguồn

```bash
deploy/make_env.sh                       # sinh deploy/.env với mật khẩu ngẫu nhiên
cd deploy && docker compose up -d --build
```

Gateway ở `http://localhost:9080`: web, API, Swagger (`/swagger-ui.html`), Keycloak (`/admin`) và portal (`/portal/`).
Tài khoản Keycloak admin lấy từ `KEYCLOAK_ADMIN_USERNAME` và `KEYCLOAK_ADMIN_PASSWORD` trong `deploy/.env`.

Nạp mô hình mẫu bằng một người dùng có vai `ADMIN` (cần Python 3; script dưới tạo người dùng thử `admin1`, chỉ dùng cho máy dev):

```bash
python3 - <<'EOF'
import sys; sys.path.insert(0, "deploy/dev")
import kc
kc.ensure_client(); kc.ensure_user("admin1", ["ADMIN"])
print(kc.api(kc.token("admin1"), "POST", "/models/apply", open("backend/dx-engine/src/main/resources/templates/equipment.yaml").read(), "application/yaml"))
EOF
```

Sau đó tạo người dùng ở tab Người dùng của `http://localhost:9080/console`, gán vai `BORROWER` hoặc `KEEPER` của mô hình `equipment`, đăng nhập ở `http://localhost:9080/` và dùng giao diện.
Hướng dẫn kiểm thử đầy đủ ở [`docs/TESTING.md`](./docs/TESTING.md).

## Tệp mô hình

Ví dụ rút gọn của [`backend/dx-engine/src/main/resources/templates/equipment.yaml`](./backend/dx-engine/src/main/resources/templates/equipment.yaml):

```yaml
format: 0
name: equipment
roles:
  - {name: BORROWER}
  - {name: KEEPER, staff: true}
entities:
  device:
    fields:
      code: {type: text, required: true, unique: true}
      name: {type: text, required: true}
    access: {BORROWER: read, KEEPER: all}
  loan:
    fields:
      device: {type: reference, to: device, required: true}
      reason: {type: text, required: true}
      from_at: {type: datetime, required: true}
    access: {BORROWER: own, KEEPER: all}
    process: {family: approval, handler: KEEPER}
```

- Kiểu trường: `text`, `number`, `datetime`, `choice`, `reference`, `user`, `unit`.
- Đơn vị: khai báo ở `units`, mỗi đơn vị là một nhóm Keycloak; trường kiểu `unit` cho biết hồ sơ thuộc đơn vị nào. Ví dụ đầy đủ ở [`backend/dx-engine/src/main/resources/templates/onestop.yaml`](./backend/dx-engine/src/main/resources/templates/onestop.yaml).
- Quyền theo vai: `all` (mọi dòng, đọc và ghi), `own` (dòng của mình), `read` (mọi dòng, chỉ đọc), `unit` (dòng của đơn vị mình), `assigned` (dòng được giao cho mình); `owner_via` cho phép thực thể con theo chủ sở hữu của thực thể cha, qua nhiều cấp.
- Quy trình: `family` chọn họ mẫu (`approval` hoặc `work_item`), `handler` là vai xử lý, `deadline` là hạn xử lý tùy chọn (ví dụ `72h`); họ `work_item` còn có `auto_close` tùy chọn (ví dụ `7d`): hồ sơ đã xử lý xong mà người nộp không xác nhận trong thời gian đó thì hệ thống đóng.
  Từ chối và trả lại bắt buộc có lý do; người nộp rút lại được hồ sơ chưa ở trạng thái cuối.
  Hồ sơ có quy trình không xóa được; hồ sơ đã được quyết định thì không sửa được; hồ sơ đã giao thì chỉ người được giao xử lý được.
  Người tạo hồ sơ không tự quyết định được, trừ khi khai báo `allow_self_decision: true`.
- Đặt chỗ: `family: reservation` kèm `booking: {resource, starts, ends, capacity}`; lượt làm số lượt chạy cùng lúc vượt sức chứa bị từ chối, và ứng dụng có chế độ xem lịch.
  Ví dụ ở [`room_booking.yaml`](./backend/dx-engine/src/main/resources/templates/room_booking.yaml).
- Hành động hoàn tất: `after` gán danh sách thao tác cho một trạng thái cuối, ví dụ `after: {approved: [{do: add_to_group, user: created_by, group_from: nhom}]}`; thao tác chạy sau khi giao dịch xong và được thử lại nếu lỗi.
  Thao tác có sẵn là `add_to_group` và `remove_from_group`; nhóm Keycloak phải có sẵn, thiếu thì thông điệp xem được ở tab Hàng đợi sự kiện; trường người dùng hoặc nhóm để trống thì thao tác bị bỏ qua và hồ sơ có thêm dòng lịch sử `completion_skipped` kèm lý do.
  Ví dụ ở [`backend/dx-engine/src/main/resources/templates/itaccess.yaml`](./backend/dx-engine/src/main/resources/templates/itaccess.yaml).
- Tệp đính kèm: trường `type: file`, tải lên bằng `POST /api/v1/data/{model}/{entity}/{id}/files`; quyền theo quyền của bản ghi.
  Loại tệp nhận được đặt bằng `engine.files.allowed-extensions`, và tệp luôn tải về dạng đính kèm.
  Mỗi hồ sơ giữ tối đa 20 tệp, đặt bằng `engine.files.max-per-record`.
- Webhook: `webhook: <tên đích>` ở đầu tệp chọn nơi nhận lịch sử hồ sơ của mô hình; địa chỉ và bí mật của đích nằm trong `deploy/webhooks/targets.yml`, ngoài git, mẫu ở `deploy/webhooks/targets.example.yml`.
  Mô hình không khai báo thì dùng `DXOS_WEBHOOK_URL`; tên đích chưa có trong tệp thì mô hình không áp dụng được.
  Mỗi đích có `format: json` (mặc định) hoặc `format: chat`.
- Đổi mô hình: khai báo `renamed_from` khi đổi tên trường; `default` điền giá trị cho trường bắt buộc mới trên bảng đã có dòng.
- Đặc tả cho trình soạn thảo: [`dx-model/.../model-v0.schema.json`](./backend/dx-model/src/main/resources/schema/model-v0.schema.json); tệp sai mẫu ở [`models/invalid/`](./models/invalid).

## API

| Đường dẫn | Việc |
|---|---|
| `POST /api/v1/models/plan`, `/apply` | Xem trước và áp dụng tệp mô hình (ADMIN) |
| `POST /api/v1/models/{model}/rollback/plan`, `/rollback` | Xem trước và quay về phiên bản trước (ADMIN) |
| `POST /api/v1/models/{model}/resume`, `/abandon` | Hoàn tất hoặc hủy bỏ lần áp dụng đã dừng giữa chừng (ADMIN) |
| `GET /api/v1/models`, `/models/{model}/runs` | Mô hình đã áp dụng và lịch sử từng bước (ADMIN) |
| `GET /api/v1/models/schema`, `/families`, `/templates`, `/{model}/versions` | Đặc tả tệp mô hình, họ mẫu, thư viện mẫu và các phiên bản đã áp dụng, cho bảng điều khiển (ADMIN) |
| `GET /api/v1/meta/models` | Metadata mà người gọi được xem; mô hình đang bị giữ hiện với `held` và không có thực thể |
| `/api/v1/data/{model}/{entity}` | Danh sách, tạo, xem, sửa, xóa; lọc `filter.<trường>[.<phép>]`, `sort`, `order`, `page`, `size`; sửa nhận `version` đã đọc, hồ sơ đã đổi thì 409 `ERR_CONCURRENT_MODIFICATION`; 403 `ERR_ACTION_NOT_ALLOWED` có `data.reason` |
| `GET /api/v1/data/{model}/{entity}/stats` | Đếm trên các dòng người gọi được xem, nhận cùng bộ lọc `filter.*` như danh sách: `total`; với thực thể có quy trình thêm `byStatus` (số hồ sơ mỗi trạng thái) và `overdue` (hồ sơ có hạn xử lý trước `computedAt`); với thực thể có trường `unit` thêm `byUnit`; truy vấn bị cắt sau 10 giây |
| `POST /api/v1/data/{model}/{entity}/{id}/actions/{action}` | Hành động của quy trình; metadata liệt kê hành động, trạng thái đầu và người được làm |
| `POST .../{id}/assign`, `GET .../{id}/handlers` | Giao việc và danh sách người giao được |
| `GET .../{id}/events` | Dòng lịch sử của bản ghi |
| `.../{id}/files` | Tải lên, liệt kê, lấy link tải có hạn (`/{fileId}/link`) và gỡ tệp đính kèm |
| `GET /api/v1/inbox` | Hồ sơ đang chờ người gọi, trên mọi mô hình; `page`, `size` |
| `GET /api/v1/events` | Dòng lịch sử theo đúng dạng của webhook, từ sau một `eventId` (`after`, `size`, `model`) (ADMIN) |
| `GET /api/v1/outbox`, `POST /api/v1/outbox/{id}/retry` | Thông điệp gửi đi theo trạng thái (lỗi, chờ gửi, đã gửi) và thử lại thông điệp chưa gửi (ADMIN) |
| `GET /api/v1/users`, `/users/options`, `/users/{id}` | Tìm và xem người dùng, vai theo từng mô hình và đơn vị; vai và đơn vị có thể gán (ADMIN) |
| `PUT`, `DELETE /api/v1/users/{id}/roles/{model}/{role}`, `/units/{unit}` | Gán và gỡ vai của một mô hình, và đơn vị (ADMIN) |
| `POST /api/v1/users`, `PUT /{id}/enabled`, `POST /{id}/password` | Tạo người dùng với mật khẩu tạm, khóa và mở, đặt lại mật khẩu (ADMIN) |
| `GET /api/v1/audit-logs` | Nhật ký lần truy cập bị từ chối, lỗi, thay đổi về tài khoản và việc áp dụng mô hình; lọc theo `actor`, `action`, `targetType`, `targetId`, `outcome`, `from`, `to` (ADMIN) |

Mọi phản hồi bọc trong `ApiResponse`; lỗi có `errorCode` ổn định.

## Cấu trúc repository

| Thư mục | Nội dung |
|---|---|
| `backend/dx-model` | Định dạng tệp mô hình: đọc và kiểm tra |
| `backend/dx-core` | Thư viện dùng chung: bảo mật, audit, lưu trữ, trạng thái, client Keycloak |
| `backend/dx-engine` | Dịch vụ lõi: áp dụng mô hình, API chung, Flowable |
| `backend/dx-monitor` | Màn hình giám sát và truy vết |
| `frontend` | Giao diện react-admin dựng từ metadata |
| `deploy` | Compose, gateway APISIX, bộ cài và lệnh `dxos`, script vận hành, script kiểm tra ở `deploy/dev` |
| `sso`, `portal`, `seaweedfs` | Keycloak realm, trang portal, lưu trữ tệp |
| `models` | Mô hình mẫu và tệp sai mẫu |
| `ai` | Dịch vụ tìm kiếm tri thức có nguồn (AGPL-3.0), chạy riêng ngoài stack |

## Kiểm thử

```bash
cd backend && ./mvnw verify            # test cần Docker (Testcontainers)
cd frontend && npx tsc --noEmit
python3 deploy/dev/check_api.py        # trên stack mới dựng, cơ sở dữ liệu rỗng
python3 deploy/dev/check_recovery.py
python3 deploy/dev/check_case.py
python3 deploy/dev/check_change.py
python3 deploy/dev/check_process.py
python3 deploy/dev/check_booking.py
python3 deploy/dev/check_demo.py
python3 deploy/dev/check_guards.py
```

## Đóng góp

- **Báo lỗi:** [tạo bug report](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=bug_report.yml).
- **Đề xuất tính năng:** [tạo feature request](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=feature_request.yml).
- **Gửi mã:** đọc [`CONTRIBUTING.md`](./CONTRIBUTING.md) và tuân theo [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md).

## Liên hệ

| Vai trò | Tên | Liên hệ |
|---|---|---|
| Người phát triển | Phạm Minh Khương | pkhuong535@gmail.com |
| Người phát triển | Nguyễn Đình Duy Khánh | dinhkhanh19042006@gmail.com |
| Người phát triển | Trần Quang Tùng | tung51652@gmail.com |

## Giấy phép

Apache License 2.0, xem [`LICENSE`](./LICENSE); thư mục `ai/` theo AGPL-3.0, xem [`ai/LICENSE`](./ai/LICENSE) và [`COPYRIGHT.txt`](./COPYRIGHT.txt).

Tham khảo: [chủ đề cuộc thi](https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html) · [giáo trình DX-OS](https://opendigitransform.gitbook.io/dx-os)
