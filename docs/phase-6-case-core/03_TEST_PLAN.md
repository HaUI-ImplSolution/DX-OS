# Kế hoạch kiểm thử: Phase 6

Các case chạy bằng script ở `deploy/dev` và kiểm giao diện, trên stack sạch.
Tài khoản thử gồm hai sinh viên, cán bộ thuộc hai đơn vị, người giám sát chỉ đọc và quản trị viên.

## 1. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T6-01 | WP6-02 | Happy path | Áp dụng `models/onestop.yaml` | Có đơn vị trong Keycloak, vai, bảng, không sửa mã lõi | Claude, 02/11/2026 | Đạt |
| T6-02 | WP6-02 | Âm tính | Tệp dùng `unit` khi không có trường đơn vị; `assigned` khi không có quy trình; đơn vị trùng | Lỗi kèm dòng và lý do | Claude, 02/11/2026 | Đạt |
| T6-03 | WP6-02 | Phân quyền | Cán bộ đúng và sai đơn vị đọc hồ sơ; người giám sát đọc | Đúng đơn vị thấy, sai đơn vị 404; giám sát thấy nhưng không sửa | Claude, 02/11/2026 | Đạt |
| T6-04 | WP6-02 | Phân quyền | Một người có hai đơn vị; hai vai khác phạm vi | Thấy hợp các dòng | Claude, 02/11/2026 | Đạt |
| T6-05 | WP6-02 | Phân quyền | Thực thể con theo đơn vị của cha | Đúng đơn vị thấy; gắn vào cha ngoài phạm vi bị 403 | Claude, 02/11/2026 | Đạt |
| T6-06 | WP6-02 | Poka-yoke | Cán bộ chuyển hồ sơ sang đơn vị khác | 403 | Claude, 02/11/2026 | Đạt |
| T6-07 | WP6-01 | Happy path | Trả lại, sửa, nộp lại, duyệt | Trạng thái đi đúng đường, hồ sơ kết thúc `approved` | Claude, 02/11/2026 | Đạt |
| T6-08 | WP6-01 | Âm tính | Trả lại không có ghi chú; hành động lạ; hành động sai trạng thái | 400; 404; 409 | Claude, 02/11/2026 | Đạt |
| T6-09 | WP6-01 | Phân quyền | Sinh viên duyệt; cán bộ `resubmit`; người khác `resubmit` | 403 | Claude, 02/11/2026 | Đạt |
| T6-10 | WP6-03 | Happy path | Nhận việc, giao cho người khác, bỏ giao | Cột `assignee` đổi, có trong lịch sử | Claude, 02/11/2026 | Đạt |
| T6-11 | WP6-03 | Phân quyền | Người không được giao thực hiện hành động; giao cho người sai đơn vị; giao cho người nộp | 403; 400; 400 | Claude, 02/11/2026 | Đạt |
| T6-12 | WP6-03 | Happy path | Hộp việc của từng người | Cán bộ thấy việc đơn vị mình chưa giao hoặc giao cho mình; sinh viên thấy hồ sơ bị trả lại | Claude, 02/11/2026 | Đạt |
| T6-13 | WP6-04 | Truy vết | Đọc lịch sử sau chuỗi hành động; sau khi xóa bản ghi | Đủ người, thời điểm, trạng thái, ghi chú; bản ghi đã xóa vẫn còn dòng lịch sử trong cơ sở dữ liệu | Claude, 02/11/2026 | Đạt |
| T6-14 | WP6-04 | Giao dịch | Lỗi cố ý sau bước quy trình | Không có dòng lịch sử của hành động đó | Claude, 02/11/2026 | Đạt |
| T6-16 | WP6-02 | Phân quyền | Vai `assigned`, vai điều phối `all`, người nộp | Hồ sơ chưa giao ngoài tầm với; điều phối giao được nhưng không quyết định; người được giao xử lý được | Claude, 02/11/2026 | Đạt |
| T6-15 | WP6-05 | Giao diện | Toàn bộ luồng T6-07, T6-10, T6-12 trên trình duyệt | Đúng theo metadata, không có tên cứng | Claude, 02/11/2026 | Đạt |

## 2. Hồi quy

- [x] `check_api.py` và `check_recovery.py` của Phase 5 đạt.
- [x] `./mvnw clean verify`, `npx tsc --noEmit`.
- [x] Mô hình không có đơn vị vẫn chạy như cũ.

## 3. Tiêu chí đạt

- Tất cả case T6-01 đến T6-16 đạt, không còn lỗi nghiêm trọng mở.
- Có kết luận của WP6-00.

## 4. Bằng chứng

Chạy ngày 02/11/2026 trên stack sạch dựng từ cây làm việc: `check_api.py` 51/51, `check_recovery.py` 16/16, `check_case.py` 70/70, luồng giao diện 20/20.
Luồng giao diện chạy bằng script tạm trên Chrome không giao diện, không nằm trong repo.
