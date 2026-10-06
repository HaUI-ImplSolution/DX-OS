# Phase 4: Duyệt thửa/vụ, mã chuẩn, GPS, trang quản lý

| Mục | Giá trị |
|---|---|
| Trạng thái | Chờ Q1 (cần kỹ sư nông nghiệp xác nhận mốc tháng loại vụ), các quyết định khác đã chốt |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B (phần duyệt và trang quản lý nên giữ nhóm A) |
| Ước lượng | 6-8 ngày |
| Người phụ trách | {điền tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Phase 3 |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 2 | Tạo thửa ruộng và vụ mùa | Quan trọng | Toàn bộ |
| mục 3 | Quản lý của engineer và admin | Quan trọng | Toàn bộ |
| mục 12 | Trang quản lý cho admin/engineer | Quan trọng | Toàn bộ |
| mục 13 | Thông báo | Quan trọng | Một phần: định tuyến theo loại sự kiện cho yêu cầu tạo thửa/vụ |
| mục 15 | Mobile | Quan trọng | Một phần: màn hình tạo thửa/vụ, GPS |
| mục 16 | Web frontend | Quan trọng | Một phần: router, trang quản lý |

## 2. Lý do

- Vấn đề đang gặp: mobile hiện chỉ đọc dữ liệu và gửi báo cáo, farmer không tạo được thửa/vụ trên app — toàn bộ việc này đang làm trên web bằng form nhập tay, không qua duyệt.
  Mã thửa là ô nhập tay tự do (`ParcelCreateRequest.code` chỉ `@NotBlank`, unique ở DB), mã vụ chưa tồn tại (chỉ có `id` số).
  Web là một trang duy nhất (`App.tsx`), không router, không có trang quản lý cho admin/engineer xem danh sách yêu cầu.
- Vì sao làm ở phase này: đây là tính năng nghiệp vụ lớn nhất của dự án, cần có `Household` (P2) và `workflow_case`/`workflow_event` (P3) làm nền trước khi viết, làm trước sẽ phải sửa lại.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Sinh mã tự động, API duyệt (tái dùng workflow), API danh sách có lọc/phân trang, danh mục giống |
| Frontend (web) | Có | Thêm router, trang quản lý admin/engineer |
| Mobile | Có | Màn hình tạo thửa/vụ, lấy GPS, hàng đợi offline |
| AI | Không | |
| Data / Seed / Migration | Có | Danh mục giống lúa, migration cột mã/GPS |
| Infra / Deploy / CI | Không | |
| Docs / PoF | Có | README, tài liệu quy chuẩn mã thửa/vụ |

## 4. Phạm vi

- Trong phạm vi: WP4-01 đến WP4-06 ở `01_PLAN.md` (mã tự sinh, mobile tạo thửa/vụ, duyệt qua workflow, danh mục giống, trang quản lý, định tuyến Mattermost).
- Ngoài phạm vi (không làm ở phase này): polygon ruộng/PostGIS (P10), push thật (P10), carbon/IoT (P10).
- Giả định: D8 (phase 0, tự biên soạn danh mục giống) và `workflow_case`/`workflow_event` (P3) đã có sẵn để tái dùng.

## 5. Ưu điểm và hạn chế

- Ưu điểm: đóng đúng tính năng nghiệp vụ lớn nhất còn thiếu; có trang quản lý thật thay vì chọn tuần tự thủ công.
- Hạn chế: khối lượng frontend lớn nhất trong toàn bộ dự án (cả web lẫn mobile cùng lúc).
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (mốc tháng loại vụ chưa có xác nhận chuyên môn, GPS bắt buộc có thể vướng vùng sóng yếu).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: farmer tạo thửa trên mobile (có GPS), mã thửa tự sinh đúng quy chuẩn; admin vào trang quản lý thấy yêu cầu chờ duyệt kèm thông tin hộ, duyệt xong thửa xuất hiện ở danh sách; farmer tạo vụ mùa, hệ thống gợi ý ngày thu hoạch theo giống đã chọn.
- Sản phẩm bàn giao: màn hình mobile mới, trang quản lý web mới (router, danh sách, chi tiết), danh mục giống lúa có nguồn, API sinh mã + API danh sách có lọc/phân trang.
- Tiêu chí đo được: mã thửa/vụ sinh ra khớp đúng quy chuẩn ở `01_PLAN.md`; API danh sách trả đúng dữ liệu theo filter; vụ mùa chỉ tạo được trên thửa `APPROVED`.
- Bằng chứng: ảnh chụp màn hình mobile/web, log test T4-xx.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: `Household.code` (P2) để sinh mã thửa; `workflow_case`/`workflow_event` (P3) để duyệt thửa/vụ; gateway APISIX (P3) để mobile gọi API tạo thửa/vụ qua đúng cổng.
- Phase sau cần từ phase này: P5 (vòng đời vụ mùa) cần thửa/vụ đã `APPROVED` để farmer khai báo thu hoạch; P7 (BI) cần mã thửa/vụ chuẩn để hiển thị trên Superset.
- Việc chuyển từ P3: kiểm thông báo Mattermost kèm ảnh bằng báo cáo thật (webhook đã cấu hình, `03_TEST_PLAN.md` của P3 mục hồi quy).
  Làm khi đã có giao diện tạo nông hộ.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: đề phần D (tìm kiếm/lọc/thống kê) — trang quản lý là bằng chứng trực tiếp; đề mục 9 (giao diện tiếng Việt, trải nghiệm người dùng thật).
- Dùng làm bằng chứng cho: kịch bản demo chính (chuỗi người dùng farmer → engineer/admin → kết quả).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Mốc tháng của 3 loại vụ (Đông Xuân/Hè Thu/Thu Đông) ở ĐBSCL dùng để gợi ý tự động | Theo đề xuất tạm trong `01_PLAN.md`, cần kỹ sư nông nghiệp xác nhận trước khi hard-code | {ngày} | _(chưa chốt — cần chuyên gia, không tự quyết được)_ |
| Q2 | Ngưỡng sai số GPS tối đa để chấp nhận vị trí thửa | 30 mét (đề xuất ban đầu trong `PROJECT_IMPROVEMENTS.md`) / khác | 05/10/2026 | 30 mét — giữ đúng đề xuất gốc, đủ chặt cho định vị thửa ruộng bằng GPS điện thoại thường |
| Q3 | Giới hạn số yêu cầu tạo thửa/vụ đang chờ duyệt mỗi farmer | Tối đa 3 yêu cầu chờ cùng lúc / không giới hạn cứng, chỉ cảnh báo admin | 05/10/2026 | Tối đa 3 yêu cầu chờ cùng lúc — đủ cho hộ có nhiều thửa, vẫn chặn được spam |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger cho endpoint mới (tạo thửa/vụ, danh sách, duyệt).
- [ ] Tài liệu quy chuẩn mã thửa/mã vụ (file riêng hoặc mục trong README).
