# Rulesets

Quy tắc bảo vệ nhánh `main` và tag của repository, lưu thành JSON để nhập vào GitHub.
Tệp ở đây chỉ là bản ghi: GitHub không tự đọc thư mục này.

| Tệp | Áp dụng cho | Quy tắc |
|---|---|---|
| `main.json` | nhánh `main` | cấm xóa, cấm ghi đè lịch sử, chỉ nhận thay đổi qua pull request gộp bằng merge |
| `tags.json` | tag `v*` | cấm sửa và cấm xóa tag đã tạo |

Nhập: Settings → Rules → Rulesets → New ruleset → Import a ruleset, chọn từng tệp.
Quy tắc yêu cầu workflow phải xanh (required status checks) chưa có ở đây, vì các workflow `ci-*.yml` chỉ chạy khi đổi đúng thư mục nên một pull request chỉ đổi tài liệu sẽ không bao giờ có check để chờ.
