# Tệp mô hình

Một tệp YAML khai báo thực thể, trường, vai, quyền và quy trình của một nghiệp vụ.
Lõi dựng bảng dữ liệu, vai Keycloak, API và giao diện từ tệp này, nên đổi nghiệp vụ là đổi tệp, không đổi mã.
Đặc tả cho trình soạn thảo ở [`model-v0.schema.json`](../backend/dx-model/src/main/resources/schema/model-v0.schema.json), và tệp sai mẫu dùng cho kiểm tự động ở [`models/invalid/`](../models/invalid).

## Ví dụ

Rút gọn từ [`equipment.yaml`](../backend/dx-engine/src/main/resources/templates/equipment.yaml):

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

Các mẫu dùng được nằm ở [`templates/`](../backend/dx-engine/src/main/resources/templates) và hiện trong tab Thư viện mẫu của bảng điều khiển.

## Trường và đơn vị

- Kiểu trường: `text`, `number`, `datetime`, `choice`, `reference`, `user`, `unit`.
- Đơn vị khai báo ở `units`, mỗi đơn vị là một nhóm Keycloak; trường kiểu `unit` cho biết hồ sơ thuộc đơn vị nào.
  Ví dụ đầy đủ ở [`onestop.yaml`](../backend/dx-engine/src/main/resources/templates/onestop.yaml).

## Quyền theo vai

`access` gán cho mỗi vai một mức:

| Mức | Ý nghĩa |
|---|---|
| `all` | Mọi dòng, đọc và ghi |
| `own` | Dòng của mình |
| `read` | Mọi dòng, chỉ đọc |
| `unit` | Dòng của đơn vị mình |
| `assigned` | Dòng được giao cho mình |

`owner_via` cho thực thể con theo chủ sở hữu của thực thể cha, qua nhiều cấp.

## Quy trình

- `family` chọn họ mẫu: `approval`, `work_item` hoặc `reservation`.
- `handler` là vai xử lý, `deadline` là hạn xử lý tùy chọn (ví dụ `72h`).
- Họ `work_item` có thêm `auto_close` tùy chọn (ví dụ `7d`): hồ sơ đã xử lý xong mà người nộp không xác nhận trong thời gian đó thì hệ thống đóng.
- Từ chối và trả lại bắt buộc có lý do, và người nộp rút lại được hồ sơ chưa ở trạng thái cuối.
- Hồ sơ có quy trình không xóa được, hồ sơ đã được quyết định thì không sửa được, và hồ sơ đã giao thì chỉ người được giao xử lý được.
- Người tạo hồ sơ không tự quyết định được, trừ khi khai báo `allow_self_decision: true`.

### Đặt chỗ

`family: reservation` kèm `booking: {resource, starts, ends, capacity}`.
Lượt đặt làm số lượt chạy cùng lúc vượt sức chứa thì bị từ chối, và ứng dụng có chế độ xem lịch.
Ví dụ ở [`room_booking.yaml`](../backend/dx-engine/src/main/resources/templates/room_booking.yaml).

### Hành động hoàn tất

`after` gán danh sách thao tác cho một trạng thái cuối, ví dụ `after: {approved: [{do: add_to_group, user: created_by, group_from: nhom}]}`.
Thao tác chạy sau khi giao dịch xong và được thử lại nếu lỗi.
Thao tác có sẵn là `add_to_group` và `remove_from_group`.
Nhóm Keycloak phải có sẵn; thiếu thì thông điệp xem được ở tab Hàng đợi sự kiện.
Trường người dùng hoặc nhóm để trống thì thao tác bị bỏ qua và hồ sơ có thêm dòng lịch sử `completion_skipped` kèm lý do.
Ví dụ ở [`itaccess.yaml`](../backend/dx-engine/src/main/resources/templates/itaccess.yaml).

## Tệp đính kèm

Trường `type: file`; tải lên bằng `POST /api/v1/data/{model}/{entity}/{id}/files`, quyền theo quyền của bản ghi.
Loại tệp nhận được đặt bằng `engine.files.allowed-extensions`, và tệp luôn tải về dạng đính kèm.
Mỗi hồ sơ giữ tối đa 20 tệp, đặt bằng `engine.files.max-per-record`.

## Webhook

`webhook: <tên đích>` ở đầu tệp chọn nơi nhận lịch sử hồ sơ của mô hình.
Địa chỉ và bí mật của đích nằm trong `deploy/webhooks/targets.yml`, ngoài git; mẫu ở `deploy/webhooks/targets.example.yml`.
Mô hình không khai báo thì dùng `DXOS_WEBHOOK_URL`, và tên đích chưa có trong tệp thì mô hình không áp dụng được.
Mỗi đích có `format: json` (mặc định) hoặc `format: chat`.

## Đổi mô hình khi đã có dữ liệu

- `renamed_from` khai báo tên cũ khi đổi tên trường, để giữ nguyên dữ liệu.
- `default` điền giá trị cho trường bắt buộc mới trên bảng đã có dòng.
- Xem trước, áp dụng, quay về và các lệnh liên quan ở [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md#cài-đặt-và-lệnh-dxos) và [`API.md`](./API.md).
