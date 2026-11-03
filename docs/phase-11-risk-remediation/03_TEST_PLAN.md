# Test plan: Phase 11

Các kiểm tra dưới đây là kế hoạch, chưa được chạy khi tài liệu này được tạo.

| Mã | Nhóm | Tình huống | Kết quả mong đợi |
|---|---|---|---|
| T11-01 | Security | Hai model khai báo cùng role | Apply bị từ chối trước khi dùng lại role Keycloak |
| T11-02 | Security | Hai model khai báo cùng unit | Apply bị từ chối trước khi dùng lại group Keycloak |
| T11-03 | Security | Dữ liệu hiện có đã collision | Audit chỉ rõ model bị ảnh hưởng và không âm thầm mở quyền mới |
| T11-04 | Apply | Lỗi sau schema step | Model bị maintenance; không tiếp tục nhận request ghi |
| T11-05 | Apply | Lỗi sau Keycloak step rồi retry | Retry dùng apply step hiện có, không tạo side effect trùng |
| T11-06 | Apply | Backend dừng trước registry reload | Khởi động lại giữ model ở maintenance để reconcile |
| T11-07 | Apply | Registry reload lỗi sau khi lưu version | Apply không ghi `done`; chạy lại không trả `unchanged` sai |
| T11-08 | Process | Đổi handler khi còn hồ sơ mở | Plan bị nâng mức hoặc từ chối, không đổi rule đang phục vụ |
| T11-09 | Process | Đổi deadline/completion khi còn hồ sơ mở | Plan bị nâng mức hoặc từ chối, Flowable variables không lệch API |
| T11-10 | Process | Đổi process family | Vẫn bị từ chối như hợp đồng hiện tại |
| T11-11 | Flowable | Kiểm tra capability migration độc lập | Có kết luận validate/mapping; không thay đổi apply production |
| T11-12 | Drop | Drop entity có process instance | Apply bị từ chối và báo dependency |
| T11-13 | Drop | Drop entity có attachment | Apply bị từ chối và báo dependency |
| T11-14 | Drop | Drop entity không có dependency | Có thể tiếp tục theo flow `LOSS` hiện tại |
| T11-15 | Regression | API CRUD và action hiện tại | Hồ sơ mới vẫn tạo, xử lý và ghi trạng thái đúng |
| T11-16 | Regression | `report_ro` | Không thay đổi hành vi hoặc phạm vi BI trong phase này |
