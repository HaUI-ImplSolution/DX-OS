# Plan thực hiện: Phase 15

Người dùng đã chốt Q1, Q2, Q3 (hướng A cả ba) ngày 2026-11-10; kết luận ở `00_OVERVIEW.md` mục 9.
Cú pháp, tên khóa và đường dẫn dưới đây vẫn là đề xuất; mỗi cái chốt lại ở bước Spec của work package trước khi code.
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP15-01 → WP15-02: chung nền metadata của mô hình.
WP15-03, WP15-04, WP15-05, WP15-06 cùng đổi định dạng tệp mô hình, nên acc2 đổi `dx-model` một lần cho cả bốn, rồi hai bên làm phần của mình song song.
Bộ sinh OpenAPI của WP15-01 phải phủ cả kiểu và ràng buộc mới, nên acc1 chỉ bắt đầu viết nó sau khi C1 đóng băng ở bước `PRE-02`.
WP15-07 độc lập, chỉ cần frontend; làm được song song với các WP khác.

Q1 chọn thứ tự P1, P2, rồi D1 đến D3, rồi U1, U2; WP15-07 độc lập nên acc1 làm xen được.
Q2 chọn có đổi định dạng với `format: 0` giữ nguyên, nên WP15-03 đến WP15-06 làm đủ.

## Phân việc theo vùng và hợp đồng cần chốt

| Hợp đồng | Bên làm | Bên đọc | Nội dung cần chốt trước khi code song song |
|---|---|---|---|
| C1. Kiểu của `dx-model` | acc2 (`dx-model`) | acc1 (`apply/`, `registry/`, `meta/`, `catalog/`) | Chữ ký cuối của `FieldType`, `Field`, `Entity` và `Ui`; acc2 gửi và đóng băng trước khi acc1 sửa `apply/` hoặc viết bộ sinh OpenAPI |
| C2. API metadata `GET /api/v1/meta/models` | acc1 (`meta/`) | acc1 (`frontend/`) | Các thuộc tính trường và thực thể thêm vào câu trả lời; không đổi thuộc tính đang có |
| C3. Hành vi API dữ liệu với kiểu và quy tắc mới | acc2 (`data/`) | acc1 (`frontend/`, `catalog/`) | Dạng giá trị trong JSON, thông báo lỗi theo trường, toán tử lọc |
| C4. Tài liệu OpenAPI theo mô hình | acc1 (`catalog/`) | acc2 (duyệt) | Đường dẫn, quyền đọc, danh sách thao tác và mã lỗi mà acc2 xác nhận khớp `data/` và `process/` |
| C5. API đăng ký ứng dụng ngoài | acc2 (`dx-core`, gói mới của `dx-engine`, `deploy/dxos`) | acc1 (`frontend/`) | Đường dẫn, thân yêu cầu và trả lời, mã lỗi |

Vùng dùng chung (`docs/`, `README`, `CHANGELOG`, `pom.xml`, mã chung của `dx-engine`) sửa lần lượt qua khóa `shared-docs` và `shared-code`.

## WP15-01: OpenAPI theo từng mô hình (P1)

- Mục tham chiếu: đề xuất mở rộng lõi mục 2; đề thi, yêu cầu lõi phục vụ ứng dụng tầng trên.
- Mục tiêu: mỗi mô hình đang chạy có một tài liệu OpenAPI sinh từ tệp mô hình, đủ để sinh SDK và nhập Postman.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Lập trình viên ngoài đọc tài liệu API của một mô hình | OpenAPI chỉ mô tả API chung `/api/v1/data/{model}/{entity}` với thân tự do | `GET /api/v1/meta/models/{model}/openapi` trả tài liệu OpenAPI của đúng mô hình: từng thực thể, trường có kiểu, bắt buộc, lựa chọn, hành động và lỗi |
| Mô hình đổi phiên bản hoặc quay về | Không có tài liệu theo mô hình | Tài liệu sinh lúc gọi từ mô hình đang chạy, nên luôn khớp |
| Người không có vai nào của mô hình gọi | Không áp dụng | 403 hoặc 404 theo cùng quy tắc với metadata của mô hình |
| Mô hình dùng kiểu và ràng buộc mới | Không áp dụng | Tài liệu mô tả `enum`, mảng, `minimum`, `maximum`, `pattern` và điều kiện bắt buộc |

**Hướng làm**

- Đường dẫn nằm dưới `/api/v1/meta/models/` vì nhóm `/api/v1/models/*` chỉ dành cho `ADMIN`, còn tài liệu này cho mọi người có vai của mô hình đọc, cùng quy tắc với metadata.
- Sinh bằng đối tượng `OpenAPI` của thư viện đang dùng, đọc từ `Model` trong `ModelRegistry`; không viết tay, không lưu.
- Mỗi thực thể có các đường: danh sách (kèm tham số lọc theo từng trường), tạo, đọc, sửa, xóa, hành động (một đường cho mỗi hành động, có `note` khi hành động cần), tệp đính kèm.
- Schema của thực thể lấy kiểu JSON từ kiểu trường; `choice` thành `enum`; `reference` và `user` thành số hoặc chuỗi id; `unit` thành `enum` các đơn vị; `required` theo trường.
- Thêm khóa mở rộng `x-dxos-process` mô tả trạng thái và hành động của quy trình.
- Thông báo lỗi dùng khung `ApiResponse` hiện có và liệt kê `errorCode` thật của lõi.
- Lõi và mô hình: không có tên thực thể nào trong mã; mọi thứ đọc từ `Model`.

**Phạm vi ảnh hưởng**

- Module: `dx-engine`.
- File chính: gói `catalog/` (acc1); acc2 duyệt phần khớp với `data/` và `process/`.
- Endpoint: `GET /api/v1/meta/models/{model}/openapi` (mới); bảng DB và biến môi trường không đổi.
- Vai và client bị ảnh hưởng: mọi người đã đăng nhập có vai của mô hình, và `ADMIN`.
- Tương thích ngược và dữ liệu cũ: không có migration; mô hình đang chạy không phải sửa.

**Kết quả đầu ra**

- Tài liệu của mô hình mẫu nhập được vào một trình sinh SDK và vào Postman không lỗi.
- Đổi một trường của mô hình rồi áp dụng thì tài liệu đổi theo.
- Người không có vai của mô hình không đọc được tài liệu.
- Tài liệu của mô hình thử dùng đủ kiểu và ràng buộc mới mô tả đúng `enum`, mảng, `minimum`, `maximum` và `pattern`.

**Phụ thuộc, lưu ý**: dùng thông tin kiểu của C1 nên cần C1 đóng băng; phần mô tả kiểu mới và ràng buộc mới hoàn tất khi WP15-03 đến WP15-05 xong, và bộ sinh không được có nhánh riêng cho từng kiểu ngoài việc đọc `Field`.

## WP15-02: Đăng ký ứng dụng ngoài (P2)

- Mục tham chiếu: đề xuất mở rộng lõi mục 2; câu hỏi Q3.
- Mục tiêu: một dịch vụ ngoài gọi lõi bằng token máy với vai giới hạn trong một mô hình, không cần tài khoản người.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Nối một dịch vụ ngoài vào một mô hình | Tạo tay client và cấp vai trong Keycloak (như `examples/itot-simulator` làm), hoặc dùng tài khoản người | `dxos client add <tên> --model M --role R` hoặc tab Ứng dụng ngoài tạo client và in secret một lần |
| Secret bị lộ | Sửa tay trong Keycloak | `dxos client rotate <tên>` cấp secret mới, secret cũ hết hiệu lực |
| Gỡ dịch vụ ngoài | Xóa tay | `dxos client remove <tên>` xóa client; hồ sơ đã tạo giữ nguyên |

**Hướng làm**

- API chỉ cho `ADMIN`: `GET /api/v1/clients`, `POST /api/v1/clients` (`name`, `model`, `role`), `POST /api/v1/clients/{name}/secret` (đổi secret), `DELETE /api/v1/clients/{name}`.
- Client Keycloak tên `dxos-app-{name}`, dạng client credentials, có bộ ánh xạ audience `dxos-api`, đánh dấu bằng một thuộc tính để chỉ liệt kê và sửa đúng các client do lõi tạo.
- Tài khoản dịch vụ của client giữ đúng một vai của đúng một mô hình (vai nằm ở client `dxos-m-{model}`); không cấp `ADMIN` hay vai nền tảng nào.
- Secret chỉ có trong câu trả lời tạo và đổi secret, không ghi vào log, audit hay cơ sở dữ liệu.
- Ghi sự kiện quản trị `CLIENT_CREATED`, `CLIENT_SECRET_ROTATED`, `CLIENT_DELETED` với tên, mô hình và vai, không kèm secret.
- Tài khoản dịch vụ `dxos-app-*` không phải người: danh sách người dùng của bảng điều khiển (`users/`) và `PeopleController` (`meta/`) loại chúng hoặc gắn nhãn "ứng dụng", để không ai gán chúng vào trường `user` hay nhầm là người.
- `apply/` (kế hoạch `repair`, phát hiện lệch, hủy bỏ lần áp dụng dở) không coi client `dxos-app-*` là client lệch hay thừa; chỉ client `dxos-m-*` của mô hình mới nằm trong phạm vi của nó.
- Đổi vai của một ứng dụng: gỡ rồi thêm lại, để API nhỏ.
- `deploy/dxos` thêm `dxos client add|list|rotate|remove`, gọi API trên bằng client `dxos-cli`.
- Q3 đã chốt cấp theo vai của một mô hình; cấp theo từng thực thể cần khóa khai báo quyền mới trong tệp mô hình và kiểm ở `data/`, nên nằm ngoài phase này.

**Phạm vi ảnh hưởng**

- Module: `dx-core` (`KeycloakAdminClient`), `dx-engine` (gói mới cho API), frontend, deploy.
- File chính: `core/identity/KeycloakAdminClient.java`, gói mới ở `dx-engine`, `users/` và `meta/PeopleController.java` (acc1), `apply/` (acc1), `deploy/dxos`, `frontend/src/console/`.
- Endpoint: bốn đường ở `/api/v1/clients`; không có bảng DB mới; không có biến môi trường mới.
- Vai và client bị ảnh hưởng: `ADMIN`, client `dxos-cli`, client `dxos-app-*` mới.
- Tương thích ngược: không ảnh hưởng client có sẵn; `examples/itot-simulator` vẫn chạy như cũ, và `setup` của nó có thể đổi sang gọi `dxos client add` sau.

**Kết quả đầu ra**

- Client tạo bằng `dxos client add` lấy token và gọi được `GET /api/v1/data/{model}/{entity}` của mô hình đó.
- Cùng client gọi mô hình khác nhận 403.
- Client không gọi được `/api/v1/clients`, `/api/v1/models/apply` hay API người dùng.
- Secret không xuất hiện trong log của backend, sự kiện quản trị hay lịch sử.
- Tài khoản dịch vụ của ứng dụng không hiện trong danh sách người dùng hay ô chọn người; `repair` và hủy bỏ lần áp dụng không đụng tới client `dxos-app-*`.

**Phụ thuộc, lưu ý**: dùng cách tạo client của `ApplyService.ENSURE_CLIENT` làm mẫu; rủi ro lộ secret ở `04` mục 2.

## WP15-03: Kiểu trường mới (D1)

- Mục tham chiếu: đề xuất mở rộng lõi mục 3; câu hỏi Q2.
- Mục tiêu: khai báo được đúng/sai, chỉ ngày và nhiều lựa chọn.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Khai báo `type: boolean` | Bị từ chối với dòng và cột | Cột `boolean`; API nhận `true` hoặc `false`; giao diện hiện ô chọn |
| Khai báo `type: date` | Phải dùng `datetime` | Cột `date`; API nhận `YYYY-MM-DD`; giao diện hiện ô chọn ngày |
| Cần chọn nhiều giá trị trong một danh sách | Không có | `type: choice` kèm `multiple: true`; cột `text[]`; API nhận mảng chuỗi thuộc `options` |

**Hướng làm**

- `dx-model`: thêm `BOOLEAN` và `DATE` vào `FieldType`; thêm khóa tùy chọn `multiple` cho `choice`; `required` trên `boolean` bị báo lỗi theo dòng và cột vì `false` đã là một giá trị.
- `dx-engine`: `DdlGenerator` và `ModelDiff` ánh xạ cột; `DataService` kiểm và đọc giá trị; bộ lọc hỗ trợ `eq` cho `boolean`, `eq`, `gte`, `lte` cho `date`, `contains` cho nhiều lựa chọn.
- Frontend: ô nhập, cột danh sách, bộ lọc và hiển thị chi tiết cho ba kiểu.
- Ảnh hưởng lan của `multiple` (cột `text[]`):
  - Dòng lịch sử `updated` giữ giá trị cũ và mới dạng chuỗi, nên mảng được ghi thành một chuỗi xác định (các phần tử nối bằng dấu phẩy, đã sắp theo thứ tự khai báo của `options`).
  - Bộ lọc `contains` và tham số lọc của OpenAPI.
  - `multiple` cùng `unique` bị từ chối lúc đọc tệp.
  - Giá trị `default` của trường `multiple` là một mảng, mỗi phần tử thuộc `options`.
  - `dxos archive` ghi cột mảng ra Parquet và `dxos archive check` đọc lại được.
- JSON Schema và `docs/MODEL.md` thêm kiểu và khóa mới.
- Lõi và mô hình: không có tên lĩnh vực nào trong mã; mô hình cũ không đổi.

**Phạm vi ảnh hưởng**

- Module: `dx-model`, `dx-engine`, frontend.
- File chính: `FieldType.java`, `Model.java`, `ModelParser.java`, `apply/DdlGenerator.java`, `apply/ModelDiff.java`, `data/DataService.java`, `frontend/src/admin/*`, `frontend/src/console/ModelForm.tsx`.
- Bảng DB: cột mới theo mô hình đổi.
- Vai và client bị ảnh hưởng: mọi vai có quyền ghi trường.
- Tương thích ngược: mô hình cũ không dùng kiểu mới nên không đổi.

**Kết quả đầu ra**

- Một mô hình dùng cả ba kiểu áp dụng được vào hệ thống rỗng; áp dụng lại không làm gì.
- API từ chối giá trị sai kiểu kèm tên trường.
- Đổi kiểu của trường đang có dữ liệu bị kế hoạch từ chối hoặc xếp mức rủi ro đúng theo quy tắc hiện có.
- Hồ sơ có trường nhiều lựa chọn: lịch sử ghi giá trị cũ và mới đọc được, `dxos archive` rồi `archive check` qua, `multiple` cùng `unique` bị báo lỗi theo dòng và cột.

**Phụ thuộc, lưu ý**: C1 và C3; Q2.

## WP15-04: Ràng buộc giá trị `min`, `max`, `pattern` (D2)

- Mục tham chiếu: đề xuất mở rộng lõi mục 3; câu hỏi Q2.
- Mục tiêu: quy tắc dữ liệu nằm trong tệp, kiểm ở API và ở cơ sở dữ liệu.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| `min` hoặc `max` trên trường `number` hoặc `date` | Không có | API từ chối giá trị ngoài khoảng; cột có ràng buộc `CHECK` |
| `pattern` trên trường `text` | Không có | API từ chối chuỗi không khớp; cột có ràng buộc `CHECK` |
| Đổi ràng buộc khi bảng đã có dòng vi phạm | Không áp dụng | Kế hoạch bị từ chối, nêu số dòng vi phạm, giống cách đã làm cho các thay đổi khác |

**Hướng làm**

- `dx-model`: khóa tùy chọn `min`, `max` (số hoặc ngày, đúng kiểu của trường) và `pattern` (chuỗi), kiểm lúc đọc tệp với dòng và cột; `min` lớn hơn `max` là lỗi.
- `pattern` chỉ nhận tập con của biểu thức chính quy mà Java và PostgreSQL hiểu giống nhau (lớp ký tự, lượng từ, nhóm, lựa chọn), và phải khớp toàn chuỗi; cấu trúc ngoài tập con bị từ chối lúc đọc tệp.
- Chưa biết `DdlGenerator` và công cụ đối chiếu lược đồ có giữ `CHECK` được không (lần áp dụng sau có thể coi `CHECK` là thừa và xóa); khảo sát ở bước `PRE-04` trước khi chốt quyết định D6; nếu không giữ được thì `min`, `max`, `pattern` chỉ kiểm ở API.
- `dx-engine`: `DdlGenerator` sinh `CHECK` (nếu khảo sát cho phép); `ModelDiff` xếp việc thêm hay siết ràng buộc vào mức rủi ro và đếm dòng vi phạm trước khi áp dụng; `DataService` kiểm trước khi ghi và trả thông báo theo trường.
- Frontend: hiện giới hạn ở nhãn gợi ý, kiểm sơ bộ trước khi gửi; API vẫn là nơi quyết định.

**Phạm vi ảnh hưởng**

- Module: `dx-model`, `dx-engine`, frontend.
- File chính: `ModelParser.java`, `apply/DdlGenerator.java`, `apply/ModelDiff.java`, `data/DataService.java`.
- Bảng DB: ràng buộc `CHECK` trên cột của bảng mô hình.
- Tương thích ngược: mô hình cũ không có khóa mới nên không đổi.

**Kết quả đầu ra**

- Giá trị ngoài khoảng hoặc không khớp mẫu bị API từ chối kèm tên trường, và `INSERT` thẳng vào bảng cũng bị cơ sở dữ liệu từ chối.
- Thêm ràng buộc vào bảng đang có dòng vi phạm thì kế hoạch bị từ chối và nêu số dòng.

**Phụ thuộc, lưu ý**: WP15-03 (cùng đợt đổi `dx-model`); rủi ro khác biệt biểu thức chính quy ở `04` mục 2.

## WP15-05: Bắt buộc có điều kiện `required_when` (D3)

- Mục tham chiếu: đề xuất mở rộng lõi mục 3; câu hỏi Q2.
- Mục tiêu: một trường chỉ bắt buộc khi một trường khác có giá trị nhất định, khai báo trong tệp.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Trường chỉ bắt buộc với một loại hồ sơ | Phải đặt `required` cho mọi hồ sơ hoặc kiểm bằng hành động `require` | `required_when: "kind == Hoãn thi"` trên trường; thiếu giá trị thì API từ chối kèm tên trường |

**Hướng làm**

- Dùng lại bộ đọc điều kiện của `when` (`readCondition`): một phép so sánh trên một trường; `required_when` và `required: true` không đi cùng nhau; trường được so sánh phải có thật và cùng thực thể.
- `DataService` kiểm khi tạo và khi sửa, sau khi gộp giá trị mới vào bản ghi hiện có.
- Chỉ kiểm ở API, không đặt `CHECK` ở cơ sở dữ liệu, để thông báo lỗi theo trường rõ ràng và để bản ghi cũ không chặn việc áp dụng mô hình.
- Frontend: đánh dấu bắt buộc động theo giá trị đang nhập; metadata trả điều kiện để giao diện tự xét.

**Phạm vi ảnh hưởng**

- Module: `dx-model`, `dx-engine` (`data/`), frontend.
- File chính: `ModelParser.java`, `Model.java`, `data/DataService.java`, `frontend/src/admin/*`.
- Tương thích ngược: mô hình cũ không có khóa mới nên không đổi.

**Kết quả đầu ra**

- Hồ sơ loại A thiếu trường bị từ chối, loại B không có trường vẫn tạo được.
- Sửa loại từ B sang A khi trường còn trống bị từ chối.
- Điều kiện trỏ tới trường không có bị báo lỗi theo dòng và cột khi áp dụng.

**Phụ thuộc, lưu ý**: WP15-03; dùng chung bộ đọc điều kiện nên không thêm cú pháp biểu thức mới.

## WP15-06: Khối `ui:` trong tệp mô hình (U1)

- Mục tham chiếu: đề xuất mở rộng lõi mục 4; câu hỏi Q2.
- Mục tiêu: người triển khai chỉnh giao diện bằng tệp mà không viết mã; lõi chỉ trả metadata.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Cột và thứ tự mặc định của danh sách | Mọi cột theo thứ tự trường | `ui.list.columns` và `ui.list.sort` đặt cột và sắp xếp mặc định |
| Form dài | Mọi trường xếp một dải | `ui.form.groups` chia trường thành nhóm có nhãn |
| Gợi ý cho người nhập | Không có | `ui.hints` đặt chữ gợi ý theo trường |
| Menu | Một mục cho mỗi thực thể, theo thứ tự khai báo | `ui.menu` đặt nhóm và thứ tự; `ui.icon` chọn biểu tượng từ danh sách cố định |

**Hướng làm**

- `dx-model`: khối `ui:` tùy chọn ở mỗi thực thể; kiểm lúc đọc tệp: cột, trường trong nhóm và trường của gợi ý phải có thật, biểu tượng phải thuộc danh sách cố định; không có nội dung thực thi.
- `ui:` không đổi quyền: cột hay nhóm không làm lộ trường mà vai không đọc được.
- Đổi `ui:` được `ModelDiff` xếp mức `SAFE`, không đụng dữ liệu và không bị coi là đổi luật quy trình, nên áp dụng được cả khi thực thể còn hồ sơ đang xử lý.
- Thứ tự ưu tiên: `ui:` đặt mặc định; cột, sắp xếp và bộ lọc mà người dùng đã lưu trong trình duyệt đè lên mặc định đó.
- `meta/` trả `ui` trong metadata; frontend dùng khi có, bỏ qua khi không.
- JSON Schema và `docs/MODEL.md` thêm khối `ui:`; trình soạn của bảng điều khiển gợi ý và báo lỗi theo schema.
- Lõi và mô hình: tên cột, nhóm, nhãn đều nằm trong tệp mô hình; mã lõi không chứa chúng.

**Phạm vi ảnh hưởng**

- Module: `dx-model`, `dx-engine` (`meta/`), frontend.
- File chính: `Model.java`, `ModelParser.java`, `meta/*`, `frontend/src/admin/resources.tsx`, `frontend/src/admin/AdminApp.tsx`.
- Tương thích ngược: không có `ui:` thì giao diện như hiện tại.

**Kết quả đầu ra**

- Mô hình mẫu có `ui:` hiện cột, sắp xếp, nhóm trường, gợi ý và menu theo tệp.
- Sai tên trường trong `ui:` bị báo lỗi theo dòng và cột.
- Mọi mẫu hiện có không có `ui:` hiển thị y như cũ.
- Sửa `ui:` của thực thể còn hồ sơ đang xử lý áp dụng được và kế hoạch xếp mức `SAFE`.
- Người đã chọn cột riêng vẫn thấy cột của mình; người chưa chọn thấy cột của `ui:`.

**Phụ thuộc, lưu ý**: C1 và C2; danh sách biểu tượng chốt ở bước Spec.

## WP15-07: Hành động hàng loạt (U2)

- Mục tham chiếu: đề xuất mở rộng lõi mục 4.
- Mục tiêu: cán bộ chọn nhiều hồ sơ rồi thực hiện cùng một hành động, mỗi hồ sơ vẫn qua đủ luật quy trình.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Duyệt nhiều hồ sơ | Mở từng hồ sơ để bấm | Chọn nhiều dòng trong danh sách, bấm hành động ở thanh trên; kết quả từng hồ sơ hiện ở bảng tóm tắt |
| Một hồ sơ trong lô bị từ chối (sai trạng thái, không đủ điều kiện, cần ghi chú) | Không áp dụng | Hồ sơ đó báo lý do theo `data.reason`; các hồ sơ khác vẫn được xử lý |

**Hướng làm**

- Chỉ ở frontend; gọi `POST /api/v1/data/{model}/{entity}/{id}/actions/{action}` có sẵn cho từng hồ sơ, không thêm endpoint hàng loạt, để quyền, khóa, điều kiện `when` và lịch sử chạy đúng như thao tác đơn.
- Nút hành động của lô chỉ hiện khi các hồ sơ chọn đều đang ở trạng thái mà hành động áp dụng; hành động cần ghi chú thì hỏi một ghi chú chung.
- Giới hạn số hồ sơ mỗi lô và số yêu cầu chạy cùng lúc, để không vượt giới hạn tốc độ của gateway.

**Phạm vi ảnh hưởng**

- Module: frontend.
- File chính: `frontend/src/admin/resources.tsx`, `frontend/src/admin/caseParts.tsx` và một thành phần mới.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Tương thích ngược: không ảnh hưởng thao tác đơn.

**Kết quả đầu ra**

- Chọn 10 hồ sơ đang chờ và bấm Duyệt: cả 10 sang trạng thái đã duyệt, mỗi hồ sơ có một dòng lịch sử.
- Trong lô có 1 hồ sơ đã bị người khác xử lý: hồ sơ đó báo lý do, 9 hồ sơ còn lại được duyệt.
- Vai không có quyền thực hiện hành động không thấy nút.

**Phụ thuộc, lưu ý**: không phụ thuộc WP khác.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `GET /api/v1/meta/models/{model}/openapi` | Mới | Không |
| Endpoint | `/api/v1/clients` (danh sách, tạo, đổi secret, xóa) | Mới | Không |
| Endpoint | `GET /api/v1/meta/models` | Thêm thuộc tính của trường (`multiple`, `min`, `max`, `pattern`, điều kiện bắt buộc) và `ui` của thực thể | Không, chỉ thêm |
| Tệp mô hình | `type: boolean`, `type: date`, `multiple` | Thêm | Không (mọi mô hình cũ giữ nguyên) |
| Tệp mô hình | `min`, `max`, `pattern`, `required_when` | Thêm | Không |
| Tệp mô hình | `ui:` | Thêm | Không |
| Bảng DB | Bảng của mô hình | Cột `boolean`, `date`, `text[]` và ràng buộc `CHECK` theo mô hình | Không với mô hình cũ |
| Biến môi trường | Không | Không | Không |

## Dữ liệu, migration và rollback

- Migration: không có migration lõi mới dự kiến; cột và ràng buộc do DDL của lần áp dụng mô hình sinh ra; ứng dụng ngoài nằm trong Keycloak.
- Dữ liệu cũ: mô hình cũ không dùng khóa mới nên không phải đổi; thêm ràng buộc vào bảng có dòng vi phạm thì kế hoạch bị từ chối.
- Rollback: quay lại phiên bản mô hình trước bằng chức năng quay về hiện có; ứng dụng ngoài gỡ bằng `dxos client remove`; code lùi bằng bản phát hành trước.

## Quyết định thiết kế đề xuất

Các quyết định dưới đây là đề xuất, chốt cùng người dùng và acc1 trước khi code.

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Mọi khóa và kiểu mới đều tùy chọn, `format: 0` giữ nguyên (đã chốt ở Q2) | Mô hình cũ áp dụng được không cần sửa; không có bước nâng định dạng |
| D2 | Nhiều lựa chọn là `choice` kèm `multiple: true`, không thêm tên kiểu | Dùng lại `options` và cách kiểm lựa chọn |
| D3 | OpenAPI theo mô hình sinh lúc gọi, không lưu | Luôn khớp mô hình đang chạy, không có bản cũ để lệch |
| D4 | Ứng dụng ngoài cấp theo vai của một mô hình (đã chốt ở Q3) | Khớp cách Keycloak gán vai và cách `data/` kiểm quyền hiện tại |
| D5 | Secret của ứng dụng ngoài chỉ hiện một lần, đổi secret là cấp cái mới | Lõi không giữ secret |
| D6 | `min`, `max`, `pattern` kiểm ở API và ở `CHECK` nếu khảo sát `PRE-04` cho thấy `CHECK` được giữ, nếu không thì chỉ ở API; `required_when` chỉ ở API | Quy tắc giá trị nên chặn cả ghi thẳng khi làm được; quy tắc có điều kiện cần thông báo theo trường |
| D7 | Hành động hàng loạt không có endpoint riêng | Mỗi hồ sơ phải qua đủ luật như thao tác đơn |
| D8 | `ui:` chỉ chọn trong những gì metadata đã có, không đổi quyền | Không mở đường lộ trường ngoài quyền |
| D9 | `ui:` là mặc định, lựa chọn đã lưu của từng người đè lên | Người dùng giữ được cách xem của mình |
