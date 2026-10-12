# Phase C4: App Lab (backend)

| Mục | Giá trị |
|---|---|
| Trạng thái | Hoàn tất |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | `app-lab` |
| Ước lượng | 6-7 ngày |
| Người phụ trách | {chưa phân công} |
| Bắt đầu, dự kiến xong | {chưa chốt} |
| Phụ thuộc | C2 (interface), C3 (realm, vai) |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Đề thi HaUI mục 4.3 | Ứng dụng minh họa giải quyết bài toán rõ của khoa, phòng, trung tâm: quản lý yêu cầu CNTT, thiết bị, phòng học, công việc | Nghiêm trọng | Backend |
| Đề thi HaUI mục 4.2 | H, P, D, I đều có minh chứng; I gồm RAG, tìm kiếm, phân loại, phát hiện bất thường, suy luận bằng luật | Nghiêm trọng | Toàn bộ |
| Đề thi HaUI mục 8, 15 | Nguồn dữ liệu có gốc; sản phẩm giải quyết đúng bài toán và có dữ liệu mẫu, kịch bản demo đầu-cuối | Quan trọng | Dữ liệu mẫu và tri thức |
| RD5, RD11 ở `refactor_docs/README.md` | Lab web trước; ứng dụng dùng được thật, có thứ tự ưu tiên | Nghiêm trọng | Toàn bộ |

## 2. Lý do

- Vấn đề đang gặp: chỉ có Farm nên chưa chứng minh core dùng chung; Farm cũng không phục vụ bài toán đơn vị đào tạo mà đề HaUI yêu cầu.
  Một ứng dụng quá nhỏ (vài bảng CRUD) không đạt tiêu chí "giá trị sử dụng" và "khả năng mở rộng" của vòng trường.
- Vì sao làm ở phase này: sau C2 đã có đủ interface; Lab là phép thử cho thiết kế core và là phần được chấm điểm sản phẩm.

## 3. Tổng quan sửa đổi

Tạo `app-lab` chỉ phụ thuộc `dx-core-api`: **DX-Lab, trạm vận hành CNTT của khoa**, gồm bốn khu vực chức năng và báo cáo:

1. Yêu cầu hỗ trợ (ticket): trao đổi hai chiều, đính kèm, phân công, SLA theo ưu tiên, người dùng xác nhận và đánh giá.
2. Tài sản và thiết bị: danh mục phòng và thiết bị, nhật ký bảo trì, mượn trả có hạn, nhắc quá hạn.
3. Đặt phòng máy và phòng thực hành: đặt theo khung giờ, chống xung đột, kiểm sức chứa và tình trạng phòng, duyệt, lịch tuần.
4. Tri thức và hỏi đáp: quy chế, hướng dẫn; gợi ý bài liên quan trước khi gửi ticket; hỏi đáp RAG.
5. Thống kê: SLA, thời gian xử lý trung bình, tải của kỹ thuật viên, mức sử dụng phòng, thiết bị theo trạng thái, mức hài lòng.

Lab cài các interface của core: vai, loại case, thông báo, kênh chat, tóm tắt hộp duyệt, view BI, tri thức, xử lý tệp.
Trạng thái nằm ở workflow dùng chung; lịch nhắc và cảnh báo SLA do Lab chạy bằng lịch của mình.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Không (ngoại lệ: bổ sung nếu thiếu, không phá Farm) | |
| `dx-core` (cài đặt dùng chung) | Không | |
| `app-farm` | Không | |
| `app-lab` | Có | Mới |
| `dx-host` (chạy, chọn app) | Có | Nạp `app-lab` khi `DXOS_APP=lab`; `application-lab.yaml` |
| Frontend (web) | Không | C5 |
| Mobile | Không | Để sau (RD5) |
| AI | Có | Nạp tri thức namespace `lab` |
| Data / Seed / Migration | Có | `db/migration/lab`, danh mục, dữ liệu mẫu, extension chống xung đột đặt phòng |
| Infra / Deploy / CI | Không | C6 |
| Docs / PoF | Có | `app-lab/README.md`, nguồn dữ liệu |

## 4. Phạm vi

Phạm vi đã cắt gọn (RD11): chỉ làm các WP dưới đây; các mục nâng cao chuyển sang backlog.

| WP | Nội dung |
|---|---|
| WPC4-01 | Khung `app-lab` |
| WPC4-02 | Danh mục nền: phòng, thiết bị, loại sự cố, chính sách SLA |
| WPC4-03 | Ticket: tạo, trao đổi, đính kèm, phân công, vòng đời, SLA, đánh giá |
| WPC4-04 | Thiết bị: nhật ký bảo trì, mượn trả |
| WPC4-05 | Đặt phòng máy |
| WPC4-06 | Thông báo, nhắc việc, cảnh báo SLA theo lịch |
| WPC4-07 | Tri thức: phân loại bằng luật, gợi ý bài liên quan, hỏi đáp RAG |
| WPC4-08 | Thống kê, view BI, dữ liệu mẫu, tài liệu |

- Trong phạm vi: domain và API Lab, workflow, vai, thông báo, lịch nhắc, BI view, tri thức, dữ liệu mẫu.
- Ngoài phạm vi (backlog): giao diện (C5), chuyển app và dựng stack (C6), mobile, tích hợp email, đồng bộ thời khóa biểu thật, ticket tương tự (`pg_trgm`), cảnh báo thiết bị hỏng lặp lại, leo thang và tự đóng ticket, dashboard Superset của Lab, biến ticket đã giải quyết thành bài tri thức.
- Giả định: C2 hoàn tất; AI service nhận `namespace`; realm có `RoleProvider` (C3).
- Ranh giới Core/App: `app-lab` chỉ import `dx-core-api`; nếu thiếu interface, bổ sung ở `dx-core-api` và ghi vào `SPI_CATALOG.md`, Farm không phải sửa.

## 5. Ưu điểm và hạn chế

- Ưu điểm: ba loại case có vòng đời khác nhau trên cùng workflow (bằng chứng mạnh cho workflow engine); bài toán thật của khoa; tái dùng phần lớn từ core.
- Hạn chế: phạm vi lớn, dữ liệu và tri thức phải tự soạn để có nguồn gốc rõ; chưa có mobile; không tích hợp thời khóa biểu thật.
- Trade-off lớn: ghi ở đây.
  Chống xung đột đặt phòng bằng ràng buộc loại trừ của PostgreSQL (`EXCLUDE USING gist`, extension `btree_gist`) thay cho kiểm tra ở mã: an toàn khi hai người đặt cùng lúc, đổi lại phụ thuộc extension (là extension tin cậy, DB owner tạo được).
  Các mục nâng cao (ticket tương tự, cảnh báo bất thường, leo thang, tự đóng, dashboard Superset của Lab) đã chuyển sang backlog để giữ phạm vi vừa phải.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được:
  - Sinh viên mô tả sự cố, thấy bài liên quan và gợi ý loại; gửi ticket kèm ảnh; trao đổi với kỹ thuật viên; xác nhận và đánh giá khi xong.
  - Kỹ thuật viên nhận ticket, xử lý, ghi nhật ký bảo trì cho thiết bị; ticket sắp quá hạn được nhắc.
  - Giảng viên đặt phòng máy cho học phần, hệ thống chặn trùng giờ; kỹ thuật viên duyệt; sinh viên mượn và trả thiết bị.
  - Quản trị xem thống kê SLA, tải kỹ thuật viên, mức sử dụng phòng.
- Sản phẩm bàn giao: module `app-lab`, migration, dữ liệu mẫu, tài liệu nguồn dữ liệu.
- Tiêu chí đo được:
  - `DXOS_APP=lab` khởi động được; `DXOS_APP=farm` không nạp entity hay bảng của Lab.
  - `app-lab` không import gói nào ngoài `dx-core-api`; `dx-core` và `app-farm` không đổi nếu `dx-core-api` đã đủ.
  - Ba loại case (`TICKET`, `DEVICE_LOAN`, `ROOM_BOOKING`) đi đủ trạng thái, lịch sử ghi đủ ở `workflow_event`.
  - Hai người đặt cùng phòng cùng giờ: một bên bị từ chối; hai người xử lý cùng ticket: một bên 409.
  - Mỗi chức năng "I" (luật phân loại, gợi ý bài tri thức, hỏi đáp RAG) có ví dụ chạy và giải thích kết quả.
- Bằng chứng: kết quả compile, log khởi động hai chế độ, kịch bản gọi API do người dùng chạy.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: interface và manifest của C2; tên và vai trung tính của C3.
- Phase sau cần từ phase này: C5 dựng giao diện Lab; C6 seed và demo.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: tính thân thiện và giá trị sử dụng (10 điểm); mức độ hoàn thiện (10 điểm); khả năng mở rộng và chất lượng kỹ thuật (10 điểm); Open-Core headless (10 điểm).
- Dùng làm bằng chứng cho: câu hỏi định hướng 3, 4, 5, 6, 7 ở mục 13 của đề.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Phạm vi domain (RD11) | Mở rộng: ticket, tài sản, đặt phòng, tri thức, thống kê | Đầu phase | Chốt (09/10/2026) |
| Q2 | Tên hiển thị app | "DX-Lab HaUI: Trạm vận hành CNTT khoa" (id `lab`) | Đầu phase | Chốt theo đề xuất (09/10/2026) |
| Q3 | Trạng thái ticket | NEW → ASSIGNED → IN_PROGRESS → WAITING_USER ↔ IN_PROGRESS → RESOLVED → CLOSED; REJECTED, CANCELLED; RESOLVED → IN_PROGRESS khi mở lại | Đầu phase | Chốt theo đề xuất (09/10/2026) |
| Q4 | Loại workspace của Lab | `CLASS` (lớp), `DEPARTMENT` (bộ môn) | Đầu phase | Chốt theo đề xuất (09/10/2026) |
| Q5 | Nguồn tri thức mẫu | Tài liệu tự soạn theo mẫu quy chế phòng máy, cấp phép mở (CC BY 4.0) | Đầu phase | Chốt theo đề xuất (09/10/2026) |
| Q6 | Chính sách SLA mặc định | Phản hồi và xử lý theo ưu tiên: KHẨN 1h/4h, CAO 4h/1 ngày, THƯỜNG 1 ngày/3 ngày, THẤP 2 ngày/7 ngày; tính theo giờ lịch, cấu hình được | Đầu phase | Chốt theo đề xuất (09/10/2026) |
| Q7 | Quy tắc đặt phòng | Khung giờ cách nhau 30 phút, tối đa 4 giờ mỗi lần, đặt trước tối đa 30 ngày, STUDENT không tự đặt phòng | Đầu phase | Chốt theo đề xuất (09/10/2026) |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] Swagger (springdoc) cho API Lab.
- [x] `app-lab/README.md` (domain, vai, interface đã cài, quy tắc SLA và đặt phòng).
- [x] `LICENSES.md` (nguồn dữ liệu mẫu và tri thức), `.env.example` nếu có đổi.
