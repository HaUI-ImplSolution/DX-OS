# Plan thực hiện: Phase 7

Thứ tự làm: Spec → Swagger → Code.
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WP7-00 → WP7-01 → WP7-02 → WP7-03 → WP7-04 → WP7-05.

## WP7-00: Kiểm bốn điều kiện của pgschema

- Mục tiêu: quyết định giữ hay bỏ pgschema.
- Kết quả: đạt cả bốn, xem [`04_TRADEOFFS_RISKS.md`](./04_TRADEOFFS_RISKS.md).

## WP7-01: Trạng thái đích qua pgschema

- Mục tiêu: một đường duy nhất cho lần đầu và các lần đổi sau.
- Ước lượng: M.
- Hướng làm:
  - `DdlGenerator` sinh trạng thái đích của một mô hình, gồm bảng, ràng buộc, chỉ mục, quyền `report_ro`.
  - `SchemaTool` gọi pgschema: lập kế hoạch JSON, áp dụng đúng kế hoạch đó.
  - Image backend kèm tệp thực thi pgschema, ghim theo digest.

## WP7-02: So sánh mô hình và phân loại

- Mục tiêu: biết đổi gì và rủi ro mức nào trước khi đụng vào hệ thống.
- Ước lượng: L.

| Thay đổi | Mức | Xử lý |
|---|---|---|
| Thêm thực thể, trường tùy chọn, vai, đơn vị, lựa chọn; bỏ bắt buộc, bỏ duy nhất; đổi nhãn, quyền, vai duyệt | An toàn | Áp dụng |
| Đổi tên trường bằng `renamed_from` | Cần chuyển dữ liệu | Xác nhận kế hoạch |
| Thêm trường bắt buộc, thêm bắt buộc, thêm duy nhất, bỏ lựa chọn | Cần chuyển dữ liệu | Có dòng vi phạm thì dừng và liệt kê |
| Xóa trường, xóa thực thể | Mất dữ liệu | Xác nhận riêng |
| Đổi kiểu trường, đổi đích tham chiếu, đổi mẫu quy trình | Từ chối | Giải thích |
| Thêm hoặc bỏ quy trình khi thực thể đã có dòng | Từ chối | Giải thích |

- Một trường bị xóa cùng một trường cùng kiểu được thêm thì kế hoạch hỏi có phải đổi tên không.
- Trường mới bắt buộc trên bảng đã có dòng cần `default`.

## WP7-03: Xem trước và áp dụng

- Mục tiêu: kế hoạch khớp với áp dụng thật; dữ liệu không mất nếu thiếu xác nhận.
- Ước lượng: L.

| Tình huống | Hành vi sau thay đổi |
|---|---|
| `POST /models/plan` | Trả phiên bản gốc và đích, thay đổi theo mức, dòng vi phạm, số hồ sơ đang xử lý, câu lệnh sẽ chạy |
| `POST /models/apply?base=N&accept=safe\|migration\|loss` | Chạy nếu mức rủi ro không vượt `accept` |
| Phiên bản đang chạy khác `base` | 409, kế hoạch hết hiệu lực |
| Kế hoạch pgschema có thao tác xóa ngoài danh sách đã phân loại | Dừng, không chạy |
| Áp dụng dở có đổi tên, rồi đưa tệp khác | 409, chỉ đúng tệp cũ hoàn tất được |
| Áp dụng dở chưa đổi tên, rồi đưa tệp khác | Áp dụng được, pgschema đưa về đích mới |

- Bước đổi tên kiểm trước khi chạy nên chạy lại được; đổi cả ràng buộc và chỉ mục mang tên trường.
- Xem trước dùng tên cũ cho các cột đổi tên, rồi tự thêm dòng đổi tên vào kế hoạch.

## WP7-04: Quay lui

- Mục tiêu: về phiên bản trước khi thay đổi quay lui được.
- Ước lượng: S.
- `POST /models/{model}/rollback` lấy nội dung phiên bản trước, đảo các lần đổi tên, đi qua đúng đường xem trước và áp dụng, ghi thành phiên bản mới.
- Quay lui mà làm mất dữ liệu cũng cần xác nhận riêng.

## WP7-05: Kiểm chứng

- Ước lượng: M.
- `deploy/dev/check_change.py` chạy trên hệ thống có dữ liệu thật.
- Cập nhật `check_api.py`, `check_recovery.py` theo hành vi mới.

## Thay đổi hợp đồng

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Tệp mô hình | `renamed_from`, `default` | Mới | Không |
| Endpoint | `POST /models/plan` | Phản hồi có thay đổi, vi phạm, hồ sơ đang xử lý | Có, chưa phát hành |
| Endpoint | `POST /models/apply` | Thêm `base`, `accept`; đổi mô hình đã áp dụng được | Không |
| Endpoint | `POST /models/{model}/rollback` | Mới | Không |
| Image | `backend` | Kèm pgschema | Không |

## Quyết định thiết kế

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | pgschema so sánh lược đồ và sinh SQL; lõi đổi tên, liệt kê vi phạm, phân loại rủi ro | Không viết lại thứ đã có; công cụ không làm được ba việc này |
| D2 | Mô hình là nguồn phân loại, kế hoạch pgschema là nguồn câu lệnh, và hai bên được đối chiếu | Lệch nhau thì dừng thay vì mất dữ liệu |
| D3 | Áp dụng đúng tệp kế hoạch JSON đã kiểm | pgschema từ chối kế hoạch cũ khi lược đồ đã đổi |
| D4 | Tệp mô hình khai báo `renamed_from` | So sánh hai trạng thái không phân biệt được đổi tên với xóa rồi thêm |
| D5 | Giữ vai và nhóm Keycloak khi mô hình bỏ chúng | Xóa có thể ảnh hưởng người dùng và mô hình khác |
| D6 | Đổi mẫu quy trình để Phase 8 | Hiện chỉ có một mẫu; `ProcessMigrationService` dùng khi có mẫu thứ hai |
