# Tệp mô hình

Một tệp YAML khai báo thực thể, trường, vai, quyền và quy trình của một nghiệp vụ.
Lõi dựng bảng dữ liệu, vai Keycloak, API và giao diện từ tệp này, nên đổi nghiệp vụ là đổi tệp, không đổi mã.
Đặc tả hiện hành cho trình soạn thảo ở [`model-v1.schema.json`](../backend/dx-model/src/main/resources/schema/model-v1.schema.json), và tệp sai mẫu dùng cho kiểm tự động ở [`models/invalid/`](../models/invalid).

## Ví dụ

Rút gọn từ [`equipment.yaml`](../backend/dx-engine/src/main/resources/templates/equipment.yaml):

```yaml
format: 1
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

## Nâng định dạng

Format hiện hành là `1`.
Lõi vẫn đọc format `0` bằng cách chạy migration `0 → 1` trong bộ nhớ trước khi kiểm tệp; format mới hơn bản lõi bị từ chối.

`dxos model upgrade <tệp>` gọi cùng pipeline migration và in unified diff mà không ghi tệp.
Sau khi đọc diff, chạy `dxos model upgrade --write <tệp>` để thay tệp bằng kết quả hoàn chỉnh qua một lần rename nguyên tử.
Chạy lại trên format hiện hành không đổi nội dung.
Hệ thống nhận ra tệp đổi theo nội dung chữ, nên lần áp dụng đầu của tệp đã nâng tạo thêm một phiên bản không đổi bảng hay quyền nào, và `dxos plan --check` trả 2 cho đến lúc đó.

File tạm nằm cùng thư mục với tệp gốc (với liên kết mềm là thư mục của tệp đích), giữ quyền, chủ sở hữu và nhóm của tệp.
Nếu migration, API, trích xuất kết quả hoặc ghi file tạm lỗi thì tệp gốc giữ nguyên; nếu tiến trình dừng trước rename thì xóa file `.dxos-model-upgrade.*` còn sót và chạy lại.
Sau rename, dùng lịch sử Git hoặc bản sao lưu để quay lại nếu không muốn giữ kết quả.

## Kiểm tệp trước khi áp dụng

`dxos lint <tệp>...` đọc tệp bằng đúng bộ đọc của backend và không cần hệ thống đang chạy.
Mỗi lỗi in một dòng `tệp:dòng:cột: nội dung`, hoặc `tệp: nội dung` khi lỗi không có một vị trí.
Mã thoát là 0 khi mọi tệp hợp lệ, 1 khi có lỗi và 2 khi không đọc được tệp.
Trên bản cài, lệnh chạy trong image backend đã cài, không mở mạng và không đổi tệp nào.
Trên máy không có DX-OS, tải `dxos-lint.jar` ở trang Release và chạy `java -jar dxos-lint.jar <tệp>...` với Java 21; đặt `DXOS_LINT_JAR` thì `dxos lint` dùng tệp này thay cho image.
Ví dụ workflow CI cho kho mô hình ở [`examples/model-repo`](../examples/model-repo).

Lint chỉ kiểm tệp, nên `dxos plan` còn so tệp với mô hình đang chạy, đích webhook và dữ liệu hiện có.
Kết quả lint hợp lệ không có nghĩa là `apply` thành công; kết quả lint có lỗi thì `plan` cũng từ chối đúng những lỗi đó.

`dxos conformance <mô hình>` chạy trên hệ thống đang chạy và kiểm các rào chắn của lõi cho mô hình đã áp dụng; cách chạy và điều kiện ở [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md#kiểm-tệp-mô-hình-và-rào-chắn-của-lõi).

## Trường và đơn vị

- Kiểu trường: `text`, `number`, `datetime`, `date`, `boolean`, `choice`, `reference`, `user`, `unit`.
- Trường `date` giữ một ngày dạng `YYYY-MM-DD`, còn `datetime` giữ một thời điểm kèm múi giờ.
- Trường `boolean` là đúng hoặc sai (`true`, `false`); không đặt `required` hay `unique`, muốn có giá trị ban đầu thì đặt `default`.
- Trường `choice` kèm `multiple: true` giữ nhiều lựa chọn trong `options`.
  API nhận và trả một mảng, mỗi lựa chọn một lần và theo thứ tự của `options`, và lọc bằng `filter.<tên>.contains=<lựa chọn>`.
  `default` là một danh sách các lựa chọn của `options`, không trùng nhau và không rỗng, ví dụ `default: [Mới, Đã sửa]`; bản ghi tạo mà không gửi trường này nhận danh sách đó.
  Trường này không đi cùng `unique`.
- Trường `user` giữ id của một tài khoản; giao diện cho chọn theo tên, và API từ chối id không có thật.
- Đơn vị khai báo ở `units`, mỗi đơn vị là một nhóm Keycloak; trường kiểu `unit` cho biết hồ sơ thuộc đơn vị nào.
  Ví dụ đầy đủ ở [`onestop.yaml`](../backend/dx-engine/src/main/resources/templates/onestop.yaml).

## Ràng buộc giá trị

- `min` và `max` giới hạn một trường `number` hoặc `date`, cả hai đầu đều nhận giá trị: `qty: {type: number, min: 1, max: 100}`.
- `pattern` là biểu thức chính quy của một trường `text`, và phải khớp toàn bộ giá trị: `code: {type: text, pattern: "[A-Z]{3}-[0-9]+"}`.
  Biểu thức chỉ gồm ký tự, dấu chấm, lớp ký tự `[...]`, nhóm `(...)`, `|` và các lượng từ `*`, `+`, `?`, `{n}`, `{n,m}` với số lặp tối đa 255; không có `^`, `$`, `\d`, `(?` hay lượng từ lười.
  Một nhóm có lượng từ bên trong không được lặp bằng `*`, `+` hay `{n,m}`, như `([a-z]+)*`, vì kiểu viết đó có thể làm phép khớp chạy rất lâu.
  Giá trị được kiểm bằng `pattern` dài tối đa 1000 ký tự, và phép khớp chỉ được đọc một số ký tự nhất định; quá số đó thì giá trị bị từ chối với `is too complex to check against the pattern`.
- Giá trị vi phạm bị API từ chối kèm tên trường, và bảng có ràng buộc `CHECK` nên ghi thẳng vào cơ sở dữ liệu cũng bị chặn.
- Siết hoặc thêm ràng buộc trên bảng đã có dòng vi phạm thì kế hoạch bị từ chối và nêu số dòng vi phạm; nới hoặc bỏ ràng buộc thì kế hoạch ở mức `SAFE`.

## Bắt buộc có điều kiện

`required_when` làm một trường chỉ bắt buộc khi một trường khác của thực thể có giá trị nhất định:

```yaml
note: {type: text, required_when: "kind == Phần mềm"}
```

- Điều kiện là một phép so sánh, viết như `when` của hành động: `==` và `!=` cho mọi kiểu, `<`, `<=`, `>`, `>=` chỉ cho số và ngày.
- Trường được so sánh là `number`, `date`, `text`, `choice` một lựa chọn hoặc `boolean` (`foreign == true`) của cùng thực thể.
- Giá trị của trường `date` viết như `YYYY-MM-DD`, ví dụ `due >= 2026-01-31`; ngày không có thật bị từ chối khi đọc mô hình.
- API kiểm khi tạo và khi sửa, trên hồ sơ sau khi gộp giá trị mới; trường còn trống thì báo `is required when kind == Phần mềm`.
- Không đi cùng `required`, không đặt trên trường `boolean` hay `file`, và không so sánh trường với chính nó.
- Chỉ kiểm ở API, cơ sở dữ liệu không có ràng buộc này.

## Giao diện `ui`

Khối `ui` của một thực thể chọn cách nó hiện trên màn hình; không có thì giao diện dựng theo thứ tự các trường:

```yaml
ui:
  icon: inventory
  menu: {group: Kho, order: 1}
  list: {columns: [code, kind, price], sort: -created_at}
  form:
    groups:
      - {label: Nhận dạng, fields: [code, kind]}
      - {label: Chi tiết, fields: [price, bought_on]}
  hints: {code: "Ba chữ in hoa, dấu gạch ngang, bốn chữ số"}
```

- `icon` là một trong `description`, `folder`, `inbox`, `person`, `group`, `school`, `assignment`, `event`, `room`, `computer`, `build`, `inventory`, `payments`, `support`, `shield`, `settings`, `list`, `calendar`, `chat`, `local_shipping`.
- `menu.group` gom các thực thể thành một nhóm trong menu và `menu.order` là thứ tự.
- `list.columns` là các cột của danh sách: trường của thực thể, hoặc `created_at`, `updated_at`, và `status`, `assignee` khi thực thể có quy trình.
  `list.sort` là tên cột, thêm dấu `-` ở đầu để sắp giảm dần.
- `form.groups` chia form thành nhóm có nhãn; trường không nằm trong nhóm nào xếp vào nhóm cuối.
- `hints` đặt dòng gợi ý dưới ô nhập của một trường.
- `ui` chỉ chọn trong những gì giao diện đã có và không đổi quyền; sai tên trường, cột hay biểu tượng thì tệp bị từ chối kèm dòng và cột.
- Cột mà người dùng chọn bằng nút Cột được giữ trong trình duyệt và đè lên cột mặc định của `ui`.
- Đổi `ui` là thay đổi `SAFE`, không đụng dữ liệu, và áp dụng được cả khi thực thể còn hồ sơ đang xử lý.

Ví dụ đầy đủ ở [`asset_register.yaml`](../backend/dx-engine/src/main/resources/templates/asset_register.yaml).

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
- `escalate` là danh sách tối đa 5 mức leo thang tính từ lúc hồ sơ quá `deadline`, nên quy trình phải khai `deadline`.
  Mỗi mức khai `after` (thời gian sau hạn) và `to` (một vai hoặc danh sách vai, mỗi vai phải đọc được hồ sơ), cùng `repeat: {every: 12h, times: 3}` nếu muốn nhắc lại; bỏ `repeat` thì mỗi mức gửi một lần.
  Ví dụ: `escalate: [{after: 24h, to: MANAGER}, {after: 72h, to: [DIRECTOR], repeat: {every: 12h, times: 3}}]`.
  Leo thang chỉ ghi dòng lịch sử `escalated` và gửi thông báo qua webhook của mô hình, không đổi trạng thái, người giữ hồ sơ hay quyền của ai.
  Dịch vụ tắt rồi chạy lại thì chỉ gửi thông báo mới nhất đã đến hạn, và hồ sơ vào hạn mới thì tính lại từ đầu.
- Họ mẫu bắt buộc có lý do khi từ chối và trả lại; quy trình tự khai báo đặt `needs_note: true` cho từng hành động cần lý do.
- Người nộp chỉ rút lại được hồ sơ ở các trạng thái khai trong `withdraw`; quy trình không khai `withdraw` thì không cho rút lại.
- Hồ sơ có quy trình không xóa được, hồ sơ đã được quyết định thì không sửa được, và hồ sơ đã giao thì chỉ người được giao xử lý được.
- Người tạo hồ sơ không tự quyết định được, trừ khi khai báo `allow_self_decision: true`.

### Tự khai báo hoặc mở rộng quy trình

- `states` là bản đồ từ tên trạng thái sang `{label, terminal}`; `initial` là trạng thái hồ sơ mới vào, mặc định là trạng thái khai báo đầu tiên.
- `actions` là bản đồ từ tên hành động sang `{label, from, to, by, role, needs_note, require, when, check}`.
  `from` là một trạng thái hoặc một danh sách, `by` là `handler` (mặc định) hoặc `owner`.
  `role` giới hạn một hành động của người xử lý cho vai riêng thay vì cả `handler`.
  Vai ghi ở `role` không cần có trong `handler`; người giữ vai đó vẫn thực hiện được hành động.
  Nút `label` để trống thì giao diện hiện mã hành động.
  `needs_note: true` bắt buộc có lý do.
  `require` liệt kê trường phải có giá trị trước khi hành động được thực hiện.
  `when` là một phép so sánh một trường của hồ sơ với một giá trị, viết thành chuỗi `trường toán_tử giá trị`, ví dụ `when: "amount > 5000000"`.
  Hành động chỉ thực hiện được khi phép so sánh đúng với hồ sơ ở lúc thực hiện.
  Toán tử là `==`, `!=`, `<`, `<=`, `>` hoặc `>=`.
  Trường là trường `number`, `date`, `choice`, `text` hoặc `boolean` của thực thể; `<`, `<=`, `>` và `>=` chỉ dùng cho trường `number` và `date`.
  Giá trị của trường `number` là số, của trường `date` là ngày `YYYY-MM-DD`, của trường `choice` là một trong `options` của nó.
  Trường chưa có giá trị thì không đạt điều kiện nào.
  `when` chỉ dùng cho hành động của người xử lý, nên không dùng được với `withdraw` và hành động của `auto_close`.
  Viết `when: null` để bỏ điều kiện của một hành động trong họ mẫu.
  Hành động không đạt điều kiện trả 409 `ERR_INVALID_STATE` với `data.reason` là `CONDITION_NOT_MET`.
  `check: <tên đích>` cho một dịch vụ riêng quyết định hành động trước khi hồ sơ chuyển trạng thái.
  Tên đích khai trong `deploy/webhooks/targets.yml` như đích của `webhook`; tên chưa có trong tệp thì mô hình bị từ chối.
  Dịch vụ được hỏi sau khi mọi quy tắc của quy trình đã đạt và trước khi trạng thái đổi; hành động bị từ chối trả 403 `ERR_ACTION_NOT_ALLOWED` với `data.reason` là `CHECK_DENIED` và `data.code` là mã dịch vụ trả về.
  Dịch vụ không trả lời hoặc trả lời sai thì cách xử lý do `on_error` của đích quyết định, cách khai và giao thức ở [`deploy/INTEGRATIONS.md`](../deploy/INTEGRATIONS.md#kiểm-tra-trước-khi-chuyển-trạng-thái).
  Hành động tự đóng của `auto_close` không hỏi dịch vụ.
- Khi có `family`, mục khai báo ghi đè từng khóa của khung; viết `null` để bỏ một trạng thái hoặc hành động của khung.
- `withdraw` và trạng thái `withdrawn` là tên dành riêng: khai `withdraw: {from: [...]}` thì nền tảng tự thêm trạng thái `withdrawn`, và hành động này luôn do người nộp thực hiện.
- Tên hành động `created`, `updated`, `deleted`, `assigned`, `file_added`, `file_removed`, `completion_skipped`, `auto_closed`, `overdue`, `due_soon`, `escalated`, `check_denied`, `check_unavailable` và `check_skipped` là tên dành riêng, vì lịch sử hồ sơ dùng chúng cho các dòng do nền tảng ghi.
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
states:
  lead_approved: {label: Chờ giám đốc}
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
Tin của dòng `escalated` có thêm `level` (mức, từ 1) và `round` (lần nhắc, từ 0), và tin chat nêu vai nhận.

## Đổi mô hình khi đã có dữ liệu

- `renamed_from` khai báo tên cũ khi đổi tên trường, để giữ nguyên dữ liệu.
- `default` điền giá trị cho trường bắt buộc mới trên bảng đã có dòng; trường nhiều lựa chọn nhận một danh sách.
- Đổi kiểu của một trường đang có (kể cả từ một lựa chọn sang nhiều lựa chọn) bị từ chối; thêm một trường mới thay vì đổi kiểu.
- Đổi tên một trường có `min`, `max` hay `pattern` giữ nguyên dữ liệu và đổi tên cả các ràng buộc của nó.
- Xem trước, áp dụng, quay về và các lệnh liên quan ở [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md#cài-đặt-và-lệnh-dxos) và [`API.md`](./API.md).
