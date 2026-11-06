# Rulesets

Quy tắc bảo vệ nhánh `main` và tag của repository, lưu thành JSON để nhập vào GitHub.
Tệp ở đây chỉ là bản ghi: GitHub không tự đọc thư mục này.

| Tệp | Áp dụng cho | Quy tắc |
|---|---|---|
| `main.json` | nhánh `main` | cấm xóa, cấm ghi đè lịch sử, chỉ nhận thay đổi qua pull request gộp bằng merge, và pull request phải có đủ các check bắt buộc xanh |
| `tags.json` | tag `v*` | cấm sửa và cấm xóa tag đã tạo |

Nhập: Settings → Rules → Rulesets → New ruleset → Import a ruleset, chọn từng tệp.
Các check bắt buộc là tên job của các workflow `Backend`, `Frontend`, `AI Service`, `Platform`, `Compliance` và `Docs`.
Workflow của các job này chạy trên mọi pull request vào `main`, nên check nào cũng có kết quả để chờ.
Workflow `Dependencies` không bắt buộc, vì kết quả của nó đổi theo cơ sở dữ liệu lỗ hổng chứ không theo mã.
Đổi tên một job thì đổi cả tên ở đây, nếu không pull request sẽ chờ một check không bao giờ xuất hiện.
Người có vai trò Admin được gộp pull request khi một check còn thiếu (`bypass_mode: pull_request`), nhưng vẫn không đẩy thẳng lên `main` được.
