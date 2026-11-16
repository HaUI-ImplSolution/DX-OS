# Tùy biến Frappe và mô hình DX-OS

Đối chiếu mã [Frappe v15.126.1][f-version] với mô hình `format: 1` của DX-OS tại `Khuong/main`.
Các kết luận dựa trên đọc mã và tài liệu chính thức, chưa kiểm bằng một bản Frappe đang chạy.

## Phạm vi

Tài liệu này là phần nghiên cứu của issue #216: so cách Frappe tách tùy biến khỏi mô hình gốc với tệp mô hình của DX-OS, nêu điểm áp dụng được, khác biệt kiến trúc và rủi ro nâng cấp.
Đề xuất nhỏ nhất đã được chốt ở [`DANH_GIA.md`](./DANH_GIA.md) mục 7.2 (đề xuất "Lớp tùy biến trên mẫu", trạng thái Chốt làm): tệp mô hình khai `extends: <mẫu>` và chỉ viết phần khác.
Việc cài đặt `extends` còn lại ở dòng "Lớp tùy biến trên mẫu" của [`BACKLOG.md`](./BACKLOG.md), ngoài phạm vi tài liệu này.
Cơ chế thay lớp controller của Frappe và việc nhập tùy biến của Frappe không được đưa vào DX-OS, lý do ở các mục dưới.

## Cơ chế và khác biệt

Frappe giữ định nghĩa DocType gốc và các đối tượng tùy biến riêng trong cơ sở dữ liệu; `Meta.process` thêm Custom Field rồi áp Property Setter khi dựng metadata hiệu lực. [Nguồn][f-process]
DX-OS nhận một YAML hoàn chỉnh, lưu nội dung theo phiên bản và nạp phiên bản mới nhất; chưa có lớp tùy biến độc lập với tệp gốc. [Parser][d-parser], [lưu phiên bản][d-apply], [registry][d-registry]

| Phần | Frappe | DX-OS |
|---|---|---|
| Định nghĩa gốc | DocType JSON trong app được nhập vào cơ sở dữ liệu bởi [sync_all][f-sync] | [ModelParser.parse][d-parser] đọc một YAML chứa thực thể, trường, quyền và quy trình |
| Thêm trường | [CustomField.validate/on_update][f-field] kiểm tên, cập nhật schema và xóa cache | Sửa YAML rồi áp dụng; [ModelDiff.addField][d-diff] phân loại trường thêm và điều kiện với dữ liệu cũ |
| Ghi đè thuộc tính | [Meta.apply_property_setters][f-meta] đặt thuộc tính lên DocType hoặc trường đã tìm thấy | [readDefinition][d-parser] ghép trạng thái/hành động với họ quy trình trong cùng YAML; không phải ghép hai mô hình |
| Lưu và xuất | [export_customizations][f-custom] ghi JSON theo DocType vào thư mục `custom`; [export_fixtures][f-fixtures] xuất các loại bản ghi do hook `fixtures` chọn | [SAVE_VERSION][d-apply] lưu toàn bộ YAML, hash và người áp dụng; [DdlGenerator.desiredState][d-ddl] sinh schema đích từ mô hình |
| Xung đột | [PropertySetter.validate][f-setter] xóa setter cùng khóa khi tạo setter mới | [ApplyService.run][d-apply] kiểm `base` và mức `accept`; không tự hợp nhất hai bản sửa |
| Quyền | [Meta.set_custom_permissions][f-perms] dùng bộ Custom DocPerm khi có, thay quyền gốc trong điều kiện của hàm | Quyền thực thể nằm trong YAML; [ModelController][d-controller] giới hạn API quản lý mô hình cho ADMIN |
| Nạp lại | [CustomizeForm.save_customization][f-form] cập nhật schema khi cần rồi xóa cache | [ModelRegistry.reload][d-registry] đọc lại phiên bản đã lưu và parse YAML |
| Bỏ tùy biến, phục hồi | [reset_customization][f-reset] xóa tùy biến không phải hệ thống theo bộ lọc; không phục hồi dữ liệu cột | [rollback][d-apply] đưa nội dung phiên bản trước qua đường apply và ghi phiên bản mới; [resume/abandon][d-apply] xử lý lần áp dụng dở |

[Hướng dẫn xuất tùy biến][f-guide] cho phép chọn đồng bộ sau cập nhật và cảnh báo việc thay setter/quyền tại site.
Ở mốc mã này, `sync_customizations` nạp `custom/*.json` khi cài app hoặc khi có `sync_on_migrate`; hàm đồng bộ thêm/cập nhật Custom Field, thay setter cùng khóa và thay bộ Custom DocPerm khi đầu vào có quyền. [Nguồn][f-custom]
Fixtures là đường nhập bản ghi khác; [post_schema_updates][f-migrate] chạy fixtures trước customizations, nên không thể xem hai cách xuất là tương đương.
Ghi đè controller bằng [override_doctype_class][f-controller] thay lớp Python, khác với thay metadata; đưa cơ chế đó vào lõi DX-OS không phù hợp nguyên tắc nghiệp vụ nằm trong mô hình.

## Rủi ro nâng cấp

- **Trùng tên trường:** Custom Field kiểm trùng khi tạo; mô hình đã ghép còn được kiểm tính duy nhất của tên trường. [Nguồn][f-field], [validate_fields][f-validation]
  Suy ra, khi app gốc thêm một trường trùng tên tùy biến, phải kiểm lại toàn bộ metadata thay vì coi hai lớp tự tương thích.
- **Trường gốc đổi hoặc mất:** setter tìm theo tên trường; `apply_property_setters` không áp setter nếu không tìm thấy trường đó. [Nguồn][f-meta]
  Suy ra, tùy biến có thể mất hiệu lực mà không được đổi sang tên mới; thay kiểu cũng cần đánh giá lại, còn DX-OS hiện từ chối đổi kiểu trường. [ModelDiff.changeField][d-diff]
- **Nhiều nguồn cùng sửa:** setter mới thay setter cùng khóa; việc đồng bộ app không phải phép hợp nhất ba chiều. [Nguồn][f-setter], [sync_customizations_for_doctype][f-custom]
  Quyền tùy biến có đầu vào không rỗng bị thay cả bộ; Custom Field bị bỏ khỏi JSON không được đường đồng bộ này tự xóa. [Nguồn][f-custom]
- **Bỏ tùy biến:** xóa metadata trường không đồng nghĩa xóa cột; Frappe có thao tác `trim_table` riêng. [Nguồn][f-reset]
  Với DX-OS, bỏ trường là `LOSS`; quay lui vẫn cần xét dữ liệu và các ràng buộc khi áp dụng, không phải khôi phục bản sao lưu. [ModelDiff][d-diff], [ApplyService][d-apply]

## Điều kiện cho `extends`

Chọn mẫu bằng nút Dùng mẫu hiện chỉ đưa nội dung YAML vào trình sửa, không tạo quan hệ theo dõi bản gốc. [Nguồn][d-template]
Mỗi rủi ro nâng cấp của Frappe ở trên thành một điều kiện cần cân nhắc khi cài `extends`; bảng này không thêm đề xuất mới ngoài mục 7.2 của `DANH_GIA.md`.

| Rủi ro của Frappe | Điều kiện cho `extends` | Đã có trong đề xuất đã chốt |
|---|---|---|
| Trùng tên trường giữa lớp gốc và lớp tùy biến | Kế hoạch áp dụng tính trên tệp đã gộp, nên trường trùng hiện ra ở bước xem kế hoạch như mọi thay đổi khác | Có: kế hoạch tính trên tệp đã gộp |
| Trường gốc đổi tên hoặc mất, phần tùy biến mất hiệu lực không báo | Phần tùy biến nhắc tới trường không còn phải được báo ở bước xem kế hoạch, không bỏ qua im lặng | Một phần: `DANH_GIA.md` nêu rủi ro mẫu đổi tên trường, chưa nêu cách báo |
| Nhiều nguồn cùng sửa một khóa | Quy tắc ghi đè từng khóa, `null` để bỏ, dùng lại quy tắc giữa họ mẫu và quy trình khai báo | Có |
| Khó biết một trường đến từ lớp nào | Lệnh in tệp đã gộp | Có |
| Nâng mẫu đổi kết quả đã áp dụng | Ghim phiên bản mẫu để áp dụng lại cho ra cùng kết quả | Có |

Phương án nhỏ nhất vẫn là ghép thành một YAML hoàn chỉnh trước khi vào parser.
Mô hình kết quả đi qua `plan/apply`, gồm quyền ADMIN, `base`, `accept`, kiểm dữ liệu cũ và giới hạn thay quy trình khi còn hồ sơ đang xử lý. [Nguồn][d-controller], [ApplyService.run/requireRows][d-apply]

[f-version]: https://github.com/frappe/frappe/tree/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0
[f-process]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/model/meta.py#L136-L150
[f-meta]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/model/meta.py#L357-L425
[f-sync]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/model/sync.py#L40-L120
[f-field]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/custom/doctype/custom_field/custom_field.py#L169-L226
[f-custom]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/modules/utils.py#L58-L219
[f-fixtures]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/utils/fixtures.py#L12-L87
[f-setter]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/custom/doctype/property_setter/property_setter.py#L34-L62
[f-perms]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/model/meta.py#L553-L566
[f-form]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/custom/doctype/customize_form/customize_form.py#L227-L258
[f-guide]: https://docs.frappe.io/framework/user/en/guides/app-development/exporting-customizations
[f-migrate]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/migrate.py#L117-L154
[f-controller]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/model/document.py#L94-L129
[f-validation]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/core/doctype/doctype/doctype.py#L1214-L1264
[f-reset]: https://github.com/frappe/frappe/blob/2e9ecdc03fa5b5b8d1ba05224a1aff7dad832bd0/frappe/custom/doctype/customize_form/customize_form.py#L651-L705
[d-parser]: ../../backend/dx-model/src/main/java/HaUI/ImplSolution/dxos/model/ModelParser.java
[d-diff]: ../../backend/dx-engine/src/main/java/HaUI/ImplSolution/dxos/engine/apply/ModelDiff.java
[d-registry]: ../../backend/dx-engine/src/main/java/HaUI/ImplSolution/dxos/engine/registry/ModelRegistry.java
[d-ddl]: ../../backend/dx-engine/src/main/java/HaUI/ImplSolution/dxos/engine/apply/DdlGenerator.java
[d-apply]: ../../backend/dx-engine/src/main/java/HaUI/ImplSolution/dxos/engine/apply/ApplyService.java
[d-controller]: ../../backend/dx-engine/src/main/java/HaUI/ImplSolution/dxos/engine/apply/ModelController.java
[d-template]: ../../frontend/src/console/ConsolePage.tsx
