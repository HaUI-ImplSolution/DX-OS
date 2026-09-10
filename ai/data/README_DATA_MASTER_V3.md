# DX-Farm Data Master v3

Master v3 hoàn tất **safe workflow coverage** cho toàn bộ 26 class không-healthy.

## Coverage

- Chemical TreatmentRule candidate classes: 12
- Management policy classes: 13
- Pest-management candidate classes: 1
- Classes without a safe structured path: 0

## Quan trọng

`classes_without_safe_structured_path = 0` không có nghĩa toàn bộ class có thuốc đã xác minh.

Nó có nghĩa mỗi class đều có một đường xử lý an toàn:
- structured treatment candidate, hoặc
- deterministic management policy chặn chemical recommendation, hoặc
- pest-management path.

## Next gate

Bước tiếp theo không phải tiếp tục "điền thuốc cho đủ".
Ta cần tạo **Agronomist Review Package** để chọn một số candidate mạnh cho demo,
review mapping CV class ↔ tên bệnh đăng ký Việt Nam, rồi mới bật
`eligible_for_seed = true` và sinh final:
- `pesticides.json`
- `treatment_rules.json`
- `disease_management_policies.json`
