# Kế hoạch kiểm thử: Phase 5

Kiểm tra phase đã đạt tiêu chuẩn và chạy đúng kỳ vọng chưa.
Quy ước dự án: mặc định chỉ compile, đọc diff, không thêm tệp test tự động nếu người dùng chưa yêu cầu.
Các case dưới đây được chạy bằng script ở `deploy/dev` và bằng kiểm giao diện thủ công.
Chạy stack hoặc gọi endpoint chỉ khi người dùng cho phép, ghi rõ ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: thủ công qua API và giao diện, bảo mật, hiệu năng.
- Môi trường: clone sạch, compose chính, dữ liệu rỗng rồi nạp mô hình mẫu.
- Dữ liệu và tài khoản test: hai người dùng vai `REQUESTER`, một người dùng vai `APPROVER`, một người dùng không có vai nào; tập 10.000 hồ sơ cho đo hiệu năng.
- Mô hình thứ hai rất nhỏ để thử nạp thêm.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T5-01 | WP5-02 | Happy path | Nạp mô hình mẫu vào hệ thống rỗng | Có schema, bảng, ràng buộc, vai Keycloak, một phiên bản, lịch sử các bước | Claude, 02/11/2026 | Đạt |
| T5-02 | WP5-01 | Âm tính | Nạp tệp tham chiếu tới thực thể chưa khai báo | Lỗi nêu dòng và lý do; hệ thống không đổi | Claude, 02/11/2026 | Đạt |
| T5-03 | WP5-01 | Âm tính | Nạp tệp có tên trường chứa `;--` | Bị bộ kiểm tra từ chối | Claude, 02/11/2026 | Đạt |
| T5-04 | WP5-02 | Idempotency | Nạp lại đúng tệp đã áp dụng | Không làm gì, không thêm phiên bản | Claude, 02/11/2026 | Đạt |
| T5-05 | WP5-02 | Lỗi dịch vụ ngoài | Tắt Keycloak giữa lúc nạp, rồi bật lại và nạp lại | Lịch sử ghi bước đã xong và bước lỗi; nạp lại làm tiếp, không tạo trùng | Claude, 02/11/2026 | Đạt |
| T5-06 | WP5-03 | Phân quyền | `REQUESTER` tạo hồ sơ, đọc danh sách; `APPROVER` đọc danh sách; người không có vai; chưa đăng nhập | `REQUESTER` chỉ thấy hồ sơ của mình; `APPROVER` thấy tất cả; không có vai: 403; chưa đăng nhập: 401 | Claude, 02/11/2026 | Đạt |
| T5-07 | WP5-03 | Phân quyền | Token có hai vai | Quyền là hợp của hai vai | Claude, 02/11/2026 | Đạt |
| T5-08 | WP5-03 | Phân quyền | `REQUESTER` đọc thực thể con qua quyền `own` một bước tham chiếu | Chỉ thấy dòng thuộc hồ sơ của mình | Claude, 02/11/2026 | Đạt |
| T5-09 | WP5-03 | Poka-yoke | Gọi `PATCH` sửa cột trạng thái bằng từng vai | 403 | Claude, 02/11/2026 | Đạt |
| T5-10 | WP5-06 | Bảo mật | Dùng vai kết nối báo cáo ghi vào bảng nghiệp vụ | Bị cơ sở dữ liệu từ chối | Claude, 02/11/2026 | Đạt |
| T5-11 | WP5-04 | Giao dịch | Bật chỗ gây lỗi sau khi đổi trạng thái rồi quyết định | Bản ghi và quy trình quay về như cũ, trạng thái hai bên khớp | Claude, 02/11/2026 | Đạt |
| T5-12 | WP5-04 | Đồng thời | Hai lần `decision` liên tiếp cho cùng hồ sơ | Lần hai nhận 409 | Claude, 02/11/2026 | Đạt |
| T5-13 | WP5-04 | Phân quyền | Vai không phải vai duyệt gọi `decision` | 403 | Claude, 02/11/2026 | Đạt |
| T5-14 | WP5-03 | Bảo mật | Truyền `;DROP TABLE` và tên cột lạ vào tham số lọc và sắp xếp | 400, dữ liệu không đổi | Claude, 02/11/2026 | Đạt |
| T5-15 | WP5-03 | Hiệu năng | Danh sách 10.000 dòng có bộ lọc và sắp xếp, 50 lần gọi | Phân vị 95 dưới 500 ms; ghi số đo | Claude, 02/11/2026 | Đạt: p95 12 ms, trung vị 9 ms |
| T5-16 | WP5-05 | Giao diện | Đăng nhập, mở từng màn hình, tạo hồ sơ với sáu kiểu trường | Form và danh sách đúng theo metadata, lỗi hiện tiếng Việt theo trường | Claude, 02/11/2026 | Đạt |
| T5-17 | WP5-05 | Ranh giới | Nạp mô hình thứ hai rồi tải lại giao diện | Có thêm màn hình mới, không build lại | Claude, 02/11/2026 | Đạt |
| T5-18 | WP5-00 | Ranh giới | Tìm từ khóa `farm`, `lab`, `DXOS_APP` trong mã, cấu hình, CI, ngoài `ai/`, `data/`, `bi/`, `chat/`; `dx-model` không phụ thuộc module nào, `dx-core` không phụ thuộc `dx-engine` | Không còn từ khóa, đồ thị phụ thuộc đúng | Claude, 02/11/2026 | Đạt |
| T5-19 | WP5-04 | Truy vết | Quyết định một hồ sơ; gửi `decided_by` trong yêu cầu sửa | Hồ sơ và tác vụ lưu người quyết định, hồ sơ lưu thời điểm; yêu cầu sửa bị 400 | Claude, 02/11/2026 | Đạt |
| T5-20 | WP5-02 | Lỗi dịch vụ ngoài | Áp dụng dở vì Keycloak dừng, rồi áp dụng tệp khác | 409, không lưu phiên bản; đúng tệp cũ hoàn tất được | Claude, 02/11/2026 | Đạt |
| T5-21 | WP5-02 | Đồng thời | Bốn lần áp dụng song song cùng một mô hình mới | Một lần áp dụng, lịch sử có một lượt chạy | Claude, 02/11/2026 | Đạt |

## 3. Hồi quy (không được hỏng)

- [x] `./mvnw compile` đạt cho toàn bộ module, `dx-monitor` vẫn hoạt động.
- [x] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy.
- [x] Đăng nhập SSO qua Keycloak ở shell vẫn hoạt động.
- [x] Realm hiện có không bị vai do lõi tạo làm hỏng.
- [x] CI đạt, kể cả kiểm tra giấy phép và SBOM.

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case T5-01 đến T5-21 đạt, không còn lỗi nghiêm trọng mở.
- Lỗi còn lại đã có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả chạy `deploy/dev/check_api.py` (51/51) và `check_recovery.py` (16/16) trên stack sạch ngày 02/11/2026, ghi ở mục 8 của [`docs/TESTING.md`](../TESTING.md).
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
