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

- `handler` là vai xử lý, hoặc một danh sách vai như `[MANAGER, SELLER]`; ai giữ một trong các vai đó thì xử lý được.
- `family` chọn một họ mẫu để bắt đầu: `approval`, `work_item` hoặc `reservation`; bỏ trống thì tự khai báo đủ `states` và `actions`.
- `deadline` là hạn xử lý tùy chọn (ví dụ `72h`).
- Họ mẫu bắt buộc có lý do khi từ chối và trả lại; quy trình tự khai báo đặt `needs_note: true` cho từng hành động cần lý do.
- Người nộp chỉ rút lại được hồ sơ ở các trạng thái khai trong `withdraw`; quy trình không khai `withdraw` thì không cho rút lại.
- Hồ sơ có quy trình không xóa được, hồ sơ đã được quyết định thì không sửa được, và hồ sơ đã giao thì chỉ người được giao xử lý được.
- Người tạo hồ sơ không tự quyết định được, trừ khi khai báo `allow_self_decision: true`.

### Tự khai báo hoặc mở rộng quy trình

- `states` là bản đồ từ tên trạng thái sang `{label, terminal}`; `initial` là trạng thái hồ sơ mới vào, mặc định là trạng thái khai báo đầu tiên.
- `actions` là bản đồ từ tên hành động sang `{label, from, to, by, role, needs_note, require, when}`.
  `from` là một trạng thái hoặc một danh sách, `by` là `handler` (mặc định) hoặc `owner`.
  `role` giới hạn một hành động của người xử lý cho vai riêng thay vì cả `handler`.
  Vai ghi ở `role` không cần có trong `handler`; người giữ vai đó vẫn thực hiện được hành động.
  Nút `label` để trống thì giao diện hiện mã hành động.
  `needs_note: true` bắt buộc có lý do.
  `require` liệt kê trường phải có giá trị trước khi hành động được thực hiện.
  `when` là một phép so sánh một trường của hồ sơ với một giá trị, viết thành chuỗi `trường toán_tử giá trị`, ví dụ `when: "amount > 5000000"`.
  Hành động chỉ thực hiện được khi phép so sánh đúng với hồ sơ ở lúc thực hiện.
  Toán tử là `==`, `!=`, `<`, `<=`, `>` hoặc `>=`.
  Trường là trường `number`, `choice` hoặc `text` của thực thể; `<`, `<=`, `>` và `>=` chỉ dùng cho trường `number`.
  Giá trị của trường `number` là số, giá trị của trường `choice` là một trong `options` của nó.
  Trường chưa có giá trị thì không đạt điều kiện nào.
  `when` chỉ dùng cho hành động của người xử lý, nên không dùng được với `withdraw` và hành động của `auto_close`.
  Viết `when: null` để bỏ điều kiện của một hành động trong họ mẫu.
  Hành động không đạt điều kiện trả 409 `ERR_INVALID_STATE` với `data.reason` là `CONDITION_NOT_MET`.
- Khi có `family`, mục khai báo ghi đè từng khóa của khung; viết `null` để bỏ một trạng thái hoặc hành động của khung.
- `withdraw` và trạng thái `withdrawn` là tên dành riêng: khai `withdraw: {from: [...]}` thì nền tảng tự thêm trạng thái `withdrawn`, và hành động này luôn do người nộp thực hiện.
- Mỗi trạng thái không phải cuối phải có hành động đi ra, và ít nhất một hành động đi ra không có `when`, để hồ sơ luôn có lối đi; mọi trạng thái phải tới được từ `initial`, trạng thái cuối không có hành động đi ra, và phải có ít nhất một trạng thái cuối.
- `auto_close` là thời gian chờ người nộp trước khi hệ thống thay họ làm một hành động.
  Họ `work_item` nhận dạng ngắn `auto_close: 7d` (hành động `confirm`); các trường hợp khác viết `auto_close: {after: 7d, action: <hành động của người nộp>}`.
- Quy trình khác khung gốc chạy trên BPMN riêng, sinh từ các trạng thái và hành động khai báo khi áp dụng mô hình.
- Đổi trạng thái đầu, thêm hoặc bỏ trạng thái, hay đổi `from`, `to`, `by`, `when` của một hành động khi còn hồ sơ chưa ở trạng thái cuối thì kế hoạch bị từ chối.
- Đổi nhãn, `role`, `require`, các trạng thái của `withdraw` hay danh sách `handler` thì an toàn và có hiệu lực ngay, kể cả với hồ sơ đang mở.
- Bỏ một trạng thái còn hồ sơ nằm ở đó, hoặc bỏ `terminal` của trạng thái còn hồ sơ đã đóng, cũng bị từ chối, vì hồ sơ đã đóng không chạy tiếp được.

```yaml
process:
  handler: [MANAGER, SELLER]
  states:
    todo: {label: Mới}
    review: {label: Đang xem}
    done: {label: Xong, terminal: true}
  actions:
    take: {from: todo, to: review, role: SELLER}
    finish: {from: review, to: done, role: MANAGER, require: [amount]}
    withdraw: {from: todo}
```

Hai hành động có `when` loại trừ nhau chia hồ sơ theo giá trị một trường; hành động không có `when` luôn là lối thoát:

```yaml
actions:
  lead_approve: {from: submitted, to: approved, role: LEAD, when: "amount <= 5000000"}
  lead_forward: {from: submitted, to: lead_approved, role: LEAD, when: "amount > 5000000"}
  lead_reject: {from: submitted, to: rejected, role: LEAD, needs_note: true}
  approve: {from: lead_approved, to: approved, role: DIRECTOR}
```

### Đặt chỗ

`family: reservation` (có thể mở rộng như trên) kèm `booking: {resource, starts, ends, capacity}`.
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
