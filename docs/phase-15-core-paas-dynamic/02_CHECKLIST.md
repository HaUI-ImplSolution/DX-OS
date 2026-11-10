# Checklist: Phase 15

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Người dùng đã chốt ba câu hỏi ở `00_OVERVIEW.md` mục 9 ngày 2026-11-10; các việc còn lại chỉ tick sau khi người dùng OK kết quả.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP15-{xx}-{lớp}-{nn}`.
Lớp gồm DOC, BE, FE, DATA, INFRA, TEST.
Dòng BE và DATA ghi module trong ngoặc vuông; cuối dòng ghi bên làm: `(acc1)` hoặc `(acc2)`.

## Chốt trước khi code

- [x] PRE-01 Người dùng trả lời Q1, Q2, Q3 ở `00_OVERVIEW.md` mục 9 và kết luận được ghi vào bảng.
- [ ] PRE-02 acc1 và acc2 chốt các hợp đồng C1 đến C5 ở `01_PLAN.md`, ghi vào `tasks` của hai bên.
- [ ] PRE-03 Chốt cú pháp cuối của các khóa mới (`multiple`, `min`, `max`, `pattern`, `required_when`, `ui:`) và danh sách biểu tượng.
- [ ] PRE-04 Khảo sát trên stack tạm: thêm `CHECK` vào một bảng mô hình rồi áp dụng lại; ghi `DdlGenerator` và công cụ đối chiếu lược đồ có giữ `CHECK` không, rồi chốt TO3 và D6 (nếu không giữ thì `min`, `max`, `pattern` chỉ kiểm ở API).

## WP15-01: OpenAPI theo từng mô hình (P1)

Kết quả đầu ra: tài liệu OpenAPI của mô hình nhập được vào trình sinh SDK và Postman, đổi theo mô hình, chỉ người có vai của mô hình đọc được, và mô tả đúng cả kiểu và ràng buộc mới.

**Spec và tài liệu**

- [ ] WP15-01-DOC-01 Cập nhật `docs/API.md` và Swagger cho `GET /api/v1/meta/models/{model}/openapi` trước khi code.

**Backend**

- [ ] WP15-01-BE-01 [dx-engine] Bộ sinh `OpenAPI` từ `Model`: đường danh sách, tạo, đọc, sửa, xóa, hành động, tệp đính kèm (acc1).
- [ ] WP15-01-BE-02 [dx-engine] Schema theo kiểu trường, `required`, `enum` cho `choice` và `unit`, khóa `x-dxos-process` (acc1).
- [ ] WP15-01-BE-05 [dx-engine] Bắt đầu sau khi C1 đóng băng; schema mô tả `boolean`, `date`, mảng của `multiple`, `minimum`, `maximum`, `pattern` và điều kiện bắt buộc, chỉ đọc từ `Field` (acc1).
- [ ] WP15-01-BE-03 [dx-engine] Quyền đọc: người có vai của mô hình và `ADMIN`; còn lại 403 hoặc 404 như metadata (acc1).
- [ ] WP15-01-BE-04 [dx-engine] acc2 đối chiếu danh sách thao tác, tham số lọc và `errorCode` với `data/` và `process/` (acc2).

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WP15-01-TEST-01 Chạy các case T15-01 đến T15-04 và T15-24.

## WP15-02: Đăng ký ứng dụng ngoài (P2)

Kết quả đầu ra: client tạo bằng `dxos client add` gọi được API của đúng một mô hình bằng token máy, không gọi được gì khác.

**Spec và tài liệu**

- [ ] WP15-02-DOC-01 Cập nhật `docs/API.md`, Swagger và `deploy/OPERATIONS.md` (lệnh `dxos client`) trước khi code.

**Backend**

- [ ] WP15-02-BE-01 [dx-core] `KeycloakAdminClient`: tạo client credentials có audience `dxos-api` và thuộc tính đánh dấu, cấp một vai của một mô hình cho tài khoản dịch vụ, đổi secret, xóa, liệt kê (acc2).
- [ ] WP15-02-BE-02 [dx-engine] Gói mới cho `/api/v1/clients`: chỉ `ADMIN`, kiểm tên và mô hình và vai, không cấp `ADMIN` hay vai nền tảng (acc2).
- [ ] WP15-02-BE-03 [dx-engine] Secret chỉ có trong câu trả lời tạo và đổi secret; không vào log, audit, lịch sử (acc2).
- [ ] WP15-02-BE-04 [dx-engine] Sự kiện quản trị `CLIENT_CREATED`, `CLIENT_SECRET_ROTATED`, `CLIENT_DELETED` qua `AdminEventPublisher`, không kèm secret (acc2).
- [ ] WP15-02-BE-05 [dx-engine] `users/` và `PeopleController` loại hoặc gắn nhãn tài khoản dịch vụ `dxos-app-*` (acc1).
- [ ] WP15-02-BE-06 [dx-engine] `apply/`: `repair`, phát hiện lệch và hủy bỏ lần áp dụng không coi client `dxos-app-*` là lệch hay thừa (acc1).

**Frontend (web)**

- [ ] WP15-02-FE-01 Tab "Ứng dụng ngoài" ở bảng điều khiển: danh sách, thêm, đổi secret, xóa; secret hiện một lần kèm nút sao chép (acc1).

**Infra, deploy, CI**

- [ ] WP15-02-INFRA-01 `deploy/dxos`: `dxos client add|list|rotate|remove` qua client `dxos-cli`, cập nhật dải help (acc2).

**Test**

- [ ] WP15-02-TEST-01 Chạy các case T15-05 đến T15-09 và T15-26.

## WP15-03: Kiểu trường mới (D1)

Kết quả đầu ra: mô hình dùng `boolean`, `date` và `choice` nhiều lựa chọn áp dụng được, ghi và đọc đúng, giao diện nhập được.

**Spec và tài liệu**

- [ ] WP15-03-DOC-01 Cập nhật `docs/MODEL.md` và JSON Schema trước khi code.

**Backend**

- [ ] WP15-03-BE-01 [dx-model] `BOOLEAN`, `DATE` trong `FieldType`; khóa `multiple`; lỗi theo dòng và cột (`required` trên `boolean`, `multiple` trên kiểu không phải `choice`) (acc2).
- [ ] WP15-03-BE-02 [dx-engine] `DdlGenerator`, `ModelDiff`: cột `boolean`, `date`, `text[]`; đổi kiểu của cột có dữ liệu xếp mức rủi ro đúng (acc1).
- [ ] WP15-03-BE-03 [dx-engine] `DataService`: đọc, kiểm và lọc theo kiểu mới, thông báo lỗi theo trường; `default` của trường `multiple` là mảng; `multiple` cùng `unique` bị từ chối (acc2).
- [ ] WP15-03-BE-04 [dx-engine] `meta/` trả `multiple` và kiểu mới (acc1).
- [ ] WP15-03-BE-05 [dx-engine] Dòng lịch sử `updated` ghi mảng thành một chuỗi xác định (acc2).

**Frontend (web)**

- [ ] WP15-03-FE-01 Ô nhập, cột danh sách, bộ lọc và chi tiết cho `boolean`, `date`, nhiều lựa chọn; hộp chọn kiểu ở `ModelForm.tsx` (acc1).

**Data, migration**

- [ ] WP15-03-DATA-01 [dx-engine] Một mẫu hoặc tệp thử dùng cả ba kiểu để kiểm; mẫu có sẵn không đổi (acc1).

**Infra, deploy, CI**

- [ ] WP15-03-INFRA-01 `dxos archive` ghi cột mảng ra Parquet và `archive check` đọc lại được (acc2).

**Test**

- [ ] WP15-03-TEST-01 Chạy các case T15-10 đến T15-12 và T15-25.

## WP15-04: Ràng buộc giá trị (D2)

Kết quả đầu ra: `min`, `max`, `pattern` bị chặn ở API và ở cơ sở dữ liệu; thêm ràng buộc vào bảng có dòng vi phạm thì kế hoạch bị từ chối.

**Spec và tài liệu**

- [ ] WP15-04-DOC-01 Cập nhật `docs/MODEL.md`, JSON Schema và ghi tập con biểu thức chính quy cho phép.

**Backend**

- [ ] WP15-04-BE-01 [dx-model] Khóa `min`, `max`, `pattern`; kiểm `min` không lớn hơn `max`, đúng kiểu trường, `pattern` trong tập con cho phép; lỗi theo dòng và cột (acc2).
- [ ] WP15-04-BE-02 [dx-engine] `DdlGenerator` sinh `CHECK` nếu `PRE-04` cho phép; `ModelDiff` đếm dòng vi phạm và từ chối kế hoạch (acc1).
- [ ] WP15-04-BE-03 [dx-engine] `DataService` kiểm giá trị trước khi ghi, thông báo theo trường (acc2).

**Frontend (web)**

- [ ] WP15-04-FE-01 Hiện giới hạn ở nhãn gợi ý và kiểm sơ bộ trước khi gửi (acc1).

**Test**

- [ ] WP15-04-TEST-01 Chạy các case T15-13 đến T15-15.

## WP15-05: Bắt buộc có điều kiện (D3)

Kết quả đầu ra: trường có `required_when` bị bắt buộc đúng lúc ở API; giao diện đánh dấu theo giá trị đang nhập.

**Spec và tài liệu**

- [ ] WP15-05-DOC-01 Cập nhật `docs/MODEL.md` và JSON Schema.

**Backend**

- [ ] WP15-05-BE-01 [dx-model] Khóa `required_when` dùng lại bộ đọc điều kiện; không đi cùng `required`; trường so sánh phải có thật (acc2).
- [ ] WP15-05-BE-02 [dx-engine] `DataService` kiểm khi tạo và sửa sau khi gộp giá trị mới vào bản ghi hiện có (acc2).
- [ ] WP15-05-BE-03 [dx-engine] `meta/` trả điều kiện (acc1).

**Frontend (web)**

- [ ] WP15-05-FE-01 Đánh dấu bắt buộc động trong form theo điều kiện (acc1).

**Test**

- [ ] WP15-05-TEST-01 Chạy các case T15-16 đến T15-17.

## WP15-06: Khối `ui:` (U1)

Kết quả đầu ra: mô hình có `ui:` hiện cột, sắp xếp, nhóm trường, gợi ý và menu theo tệp; không có `ui:` thì giao diện như cũ.

**Spec và tài liệu**

- [ ] WP15-06-DOC-01 Cập nhật `docs/MODEL.md`, JSON Schema và `docs/API.md` (thuộc tính `ui` của metadata).

**Backend**

- [ ] WP15-06-BE-01 [dx-model] Khối `ui:` ở thực thể, kiểm tên trường và biểu tượng, lỗi theo dòng và cột (acc2).
- [ ] WP15-06-BE-02 [dx-engine] `registry/` giữ `ui`; `meta/` trả `ui` trong metadata (acc1).
- [ ] WP15-06-BE-03 [dx-engine] `ModelDiff` xếp đổi `ui:` mức `SAFE`, không tính là đổi luật quy trình nên không bị chặn khi còn hồ sơ mở (acc1).

**Frontend (web)**

- [ ] WP15-06-FE-01 Danh sách dùng cột và sắp xếp mặc định của `ui.list`; cột đã lưu của từng người đè lên (acc1).
- [ ] WP15-06-FE-02 Form chia nhóm theo `ui.form` và hiện gợi ý theo `ui.hints` (acc1).
- [ ] WP15-06-FE-03 Menu và biểu tượng theo `ui.menu`, `ui.icon` (acc1).
- [ ] WP15-06-FE-04 Trình soạn của bảng điều khiển gợi ý khối `ui:` theo schema (acc1).

**Data, migration**

- [ ] WP15-06-DATA-01 [dx-engine] Thêm `ui:` vào một mẫu có sẵn làm ví dụ, không làm đổi hành vi của mẫu khác (acc1).

**Test**

- [ ] WP15-06-TEST-01 Chạy các case T15-18 đến T15-20 và T15-27.

## WP15-07: Hành động hàng loạt (U2)

Kết quả đầu ra: chọn nhiều hồ sơ rồi bấm một hành động; từng hồ sơ qua đủ luật, hồ sơ bị từ chối báo lý do.

**Frontend (web)**

- [ ] WP15-07-FE-01 Thanh hành động của lô trong danh sách có quy trình, chỉ hiện hành động áp dụng cho mọi hồ sơ đã chọn (acc1).
- [ ] WP15-07-FE-02 Gọi API hành động có sẵn cho từng hồ sơ với giới hạn kích thước lô và số yêu cầu cùng lúc (acc1).
- [ ] WP15-07-FE-03 Hỏi một ghi chú chung khi hành động cần ghi chú; bảng tóm tắt kết quả từng hồ sơ với lý do theo `data.reason` (acc1).
- [ ] WP15-07-FE-04 Thông báo tiếng Việt và tiếng Anh cho các chuỗi mới (acc1).

**Test**

- [ ] WP15-07-TEST-01 Chạy các case T15-21 đến T15-23.

## Đóng phase

- [ ] `./mvnw formatter:validate` và `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn milestone.
- [ ] `deploy/.env.example` và migration chạy được trên clone sạch; từng tệp mô hình mà phase đụng tới áp dụng được vào hệ thống rỗng.
- [ ] `dx-model` không phụ thuộc module nào, `dx-core` không phụ thuộc `dx-engine`, mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu và ảnh test không còn dùng.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Ghi trạng thái cuối ở `00_OVERVIEW.md` rồi gộp việc còn lại vào `docs/dynamic-core/BACKLOG.md` và xóa thư mục phase (docs chưa có chỉ mục phase).
