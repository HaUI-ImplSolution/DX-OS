# DX-OS: lõi dynamic cho hệ thống hồ sơ có quy trình (Bài dự thi Phần Mềm Nguồn Mở 2026)

**Tiếng Việt** · [English](./README_EN.md)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![AI License](https://img.shields.io/badge/ai%2F-AGPL--3.0-orange.svg)](./ai/LICENSE)

**Đội:** HaUI-ImplSolution · **Trường:** Trường Công nghệ Thông tin và Truyền thông, Đại học Công nghiệp Hà Nội

**Tài liệu:** theo dõi tại [trang docs](https://haui-implsolution.github.io/DX-OS-docs/); hướng thiết kế ở [`docs/dynamic-core/README.md`](./docs/dynamic-core/README.md).

Dự thi **OLP Phần mềm nguồn mở 2026** (khối thi DX-OS, VFOSSA).
Lõi nhận một tệp mô hình khai báo thực thể, trường, vai, quyền và quy trình, rồi dựng bảng dữ liệu, vai đăng nhập, API có phân quyền từng dòng, quy trình phê duyệt và giao diện.
Đổi nghiệp vụ là đổi tệp mô hình, không đổi mã.
Phiên bản trước của dự án (hai ứng dụng nghiệp vụ viết tay) nằm ở tag `v1.1.1`.

## Trạng thái

Đã chạy được vòng hẹp từ tệp mô hình đến người dùng cuối:

- Đọc và kiểm tra tệp mô hình, báo lỗi theo dòng và cột.
- Áp dụng mô hình vào hệ thống rỗng: schema, bảng, ràng buộc, vai Keycloak, phiên bản và lịch sử từng bước; áp dụng lại thì không làm gì, bị gián đoạn thì áp dụng lại để làm tiếp.
- API chung cho mọi thực thể, quyền theo vai và theo dòng (`all`, `own`, `read`, `unit`, `assigned`), quy tắc bắt buộc, duy nhất và lựa chọn.
- Quy trình phê duyệt một cấp trên Flowable nhúng, có trả lại để bổ sung; trạng thái hồ sơ chỉ đổi qua quy trình, cùng giao dịch với việc ghi hồ sơ.
- Đơn vị khai báo trong mô hình và đồng bộ thành nhóm Keycloak; giao việc, hộp việc và dòng lịch sử của từng hồ sơ.
- Đổi mô hình khi đã có dữ liệu: xem trước theo mức rủi ro, đổi tên giữ nguyên dữ liệu, báo dòng vi phạm, xác nhận riêng khi mất dữ liệu, quay về phiên bản trước.
- Giao diện react-admin dựng từ metadata, không có mã riêng cho thực thể nào.

Chưa có: thông báo chat, AI, báo cáo Superset.
Các phần này nằm trong [hướng thiết kế](./docs/dynamic-core/README.md).

## Chạy thử

Cần Docker với Compose v2 và Python 3.

```bash
python3 deploy/make_env.py              # sinh deploy/.env với mật khẩu ngẫu nhiên
cd deploy && docker compose up -d --build
```

Gateway ở `http://localhost:9080`: web, API, Swagger (`/swagger-ui.html`), Keycloak (`/admin`) và portal (`/portal/`).
Tài khoản Keycloak admin lấy từ `KEYCLOAK_ADMIN_USERNAME` và `KEYCLOAK_ADMIN_PASSWORD` trong `deploy/.env`.

Nạp mô hình mẫu bằng một người dùng có vai `ADMIN` (script dưới tạo người dùng thử `admin1`, chỉ dùng cho máy dev):

```bash
python3 - <<'EOF'
import sys; sys.path.insert(0, "deploy/dev")
import kc
kc.ensure_client(); kc.ensure_user("admin1", ["ADMIN"])
print(kc.api(kc.token("admin1"), "POST", "/models/apply", open("backend/dx-engine/src/main/resources/templates/equipment.yaml").read(), "application/yaml"))
EOF
```

Sau đó tạo người dùng với vai `BORROWER` hoặc `KEEPER` trong Keycloak, đăng nhập ở `http://localhost:9080/` và dùng giao diện.
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
- Quy trình: `family` chọn họ mẫu (`approval` hoặc `work_item`), `handler` là vai xử lý, `deadline` là hạn xử lý tùy chọn (ví dụ `72h`).
  Từ chối và trả lại bắt buộc có lý do; người nộp rút lại được hồ sơ chưa ở trạng thái cuối.
  Hồ sơ có quy trình không xóa được; hồ sơ đã được quyết định thì không sửa được; hồ sơ đã giao thì chỉ người được giao xử lý được.
  Người tạo hồ sơ không tự quyết định được, trừ khi khai báo `allow_self_decision: true`.
- Đặt chỗ: `family: reservation` kèm `booking: {resource, starts, ends, capacity}`; hai lượt trùng khung giờ vượt sức chứa thì lượt sau bị từ chối, và ứng dụng có chế độ xem lịch.
  Ví dụ ở [`room_booking.yaml`](./backend/dx-engine/src/main/resources/templates/room_booking.yaml).
- Hành động hoàn tất: `after` gán danh sách thao tác cho một trạng thái cuối, ví dụ `after: {approved: [{do: add_to_group, user: created_by, group_from: nhom}]}`; thao tác chạy sau khi giao dịch xong và được thử lại nếu lỗi.
  Thao tác có sẵn là `add_to_group` và `remove_from_group`.
  Ví dụ ở [`backend/dx-engine/src/main/resources/templates/itaccess.yaml`](./backend/dx-engine/src/main/resources/templates/itaccess.yaml).
- Tệp đính kèm: trường `type: file`, tải lên bằng `POST /api/v1/data/{model}/{entity}/{id}/files`; quyền theo quyền của bản ghi.
  Loại tệp nhận được đặt bằng `engine.files.allowed-extensions`, và tệp luôn tải về dạng đính kèm.
  Mỗi hồ sơ giữ tối đa 20 tệp, đặt bằng `engine.files.max-per-record`.
- Đổi mô hình: khai báo `renamed_from` khi đổi tên trường; `default` điền giá trị cho trường bắt buộc mới trên bảng đã có dòng.
- Đặc tả cho trình soạn thảo: [`dx-model/.../model-v0.schema.json`](./backend/dx-model/src/main/resources/schema/model-v0.schema.json); tệp sai mẫu ở [`models/invalid/`](./models/invalid).

## API

| Đường dẫn | Việc |
|---|---|
| `POST /api/v1/models/plan`, `/apply` | Xem trước và áp dụng tệp mô hình (ADMIN) |
| `POST /api/v1/models/{model}/rollback/plan`, `/rollback` | Xem trước và quay về phiên bản trước (ADMIN) |
| `GET /api/v1/models`, `/models/{model}/runs` | Mô hình đã áp dụng và lịch sử từng bước (ADMIN) |
| `GET /api/v1/meta/models` | Metadata mà người gọi được xem |
| `/api/v1/data/{model}/{entity}` | Danh sách, tạo, xem, sửa, xóa; lọc `filter.<trường>[.<phép>]`, `sort`, `order`, `page`, `size` |
| `POST /api/v1/data/{model}/{entity}/{id}/actions/{action}` | Hành động của quy trình; metadata liệt kê hành động, trạng thái đầu và người được làm |
| `POST .../{id}/assign`, `GET .../{id}/handlers` | Giao việc và danh sách người giao được |
| `GET .../{id}/events` | Dòng lịch sử của bản ghi |
| `GET /api/v1/inbox` | Hồ sơ đang chờ người gọi, trên mọi mô hình; `page`, `size` |
| `GET /api/v1/outbox`, `POST /api/v1/outbox/{id}/retry` | Thông điệp gửi đi theo trạng thái (lỗi, chờ gửi, đã gửi) và thử lại thông điệp chưa gửi (ADMIN) |

Mọi phản hồi bọc trong `ApiResponse`; lỗi có `errorCode` ổn định.

## Cấu trúc repository

| Thư mục | Nội dung |
|---|---|
| `backend/dx-model` | Định dạng tệp mô hình: đọc và kiểm tra |
| `backend/dx-core` | Thư viện dùng chung: bảo mật, audit, lưu trữ, trạng thái, client Keycloak |
| `backend/dx-engine` | Dịch vụ lõi: áp dụng mô hình, API chung, Flowable |
| `backend/dx-monitor` | Màn hình giám sát và truy vết |
| `frontend` | Giao diện react-admin dựng từ metadata |
| `deploy` | Compose, gateway APISIX, script vận hành, script kiểm tra ở `deploy/dev` |
| `sso`, `portal`, `seaweedfs` | Keycloak realm, trang portal, lưu trữ tệp |
| `models` | Mô hình mẫu và tệp sai mẫu |
| `ai`, `bi`, `chat`, `data` | Phần AI, báo cáo và chat, sẽ dựng lại theo mô hình ở các bước sau |

## Kiểm thử

```bash
cd backend && ./mvnw verify            # test cần Docker (Testcontainers)
cd frontend && npx tsc --noEmit
python3 deploy/dev/check_api.py        # trên stack mới dựng, cơ sở dữ liệu rỗng
python3 deploy/dev/check_recovery.py
python3 deploy/dev/check_case.py
python3 deploy/dev/check_change.py
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
