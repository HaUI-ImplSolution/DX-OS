# Trade-off và rủi ro: Phase C2

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Trạng thái workflow (RD10) | A: giữ enum 4 trạng thái, Lab tự giữ trạng thái ticket trong entity. B: `CaseTypeDefinition` khai báo trạng thái và bước chuyển | B (đã chốt) | Ticket Lab (mới, phân công, đang xử lý, đã xử lý, đóng) dùng workflow thật; lịch sử và hộp duyệt dùng chung; bằng chứng mạnh cho "workflow engine" | Core phức tạp hơn; kiểm tra bước chuyển động; web và mobile phải chịu trạng thái không biết | Cao: sau khi Lab dùng, quay về enum cần viết lại Lab |
| TO2 | Quan hệ workspace và người dùng | A: một tài khoản mỗi workspace. B: `WorkspaceMember` nhiều-nhiều có vai | B | Lớp, bộ môn có nhiều thành viên; Farm dùng một thành viên | Thêm bảng, thêm truy vấn | Vừa |
| TO3 | Đường dẫn workspace | A: đổi `/households` thành `/workspaces`. B: core có `/workspaces`, Farm giữ `/households` làm bí danh | B | Farm không đổi hợp đồng; web, seed, E2E giữ nguyên | Thêm một controller bí danh ở Farm | Thấp |
| TO4 | Loại thông báo | A: enum ở core. B: mã chuỗi do app đăng ký | B | Lab thêm loại không sửa core | Mất kiểm tra loại lúc biên dịch | Vừa |
| TO5 | Interface hóa | A: interface cho mọi dịch vụ. B: chỉ cho điểm Farm và Lab khác nhau | B | Ít trừu tượng thừa | Có thể thiếu interface khi app thứ ba cần | Thấp |
| TO6 | Nơi đặt controller có tên trường riêng của app (`households`, `approval-requests`, `knowledge/ask`) | A: controller trung tính ở core, Farm đổi JSON. B: core giữ khung xử lý và facade, controller ở app | B | Farm giữ nguyên hợp đồng; core không có từ vựng nông nghiệp | Mỗi app tự viết lớp HTTP mỏng cho các path này | Thấp |

Kết luận: TO1 đã chốt ngày 09/10/2026 (RD10); các mục còn lại áp dụng ngay.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Workflow cấu hình được làm hỏng luật duyệt của Farm | Vừa | Cao | Định nghĩa `approval` mặc định tái tạo đúng luật cũ; chạy E2E sau WPC2-02 | Duyệt hoặc gửi lại thất bại, 409 bất thường | Giữ enum ở core, bọc định nghĩa phía trên |
| R2 | Bí danh `/households` lệch JSON cũ (tên trường, mã lỗi) | Vừa | Vừa | Ánh xạ từ `WorkspaceView` sang JSON cũ; so OpenAPI với baseline | OpenAPI Farm khác baseline | Sửa ánh xạ ở Farm |
| R3 | Bảo mật tổng quát hóa làm hở hoặc chặn nhầm endpoint | Thấp | Cao | Đối chiếu ma trận quyền trước và sau (TC2-09); mặc định từ chối khi interface không khai báo | Endpoint công khai bị 401, hoặc endpoint nội bộ truy cập được | Quay về cấu hình Farm trong core tạm thời |
| R4 | Hộp duyệt chậm do gọi `CaseSummaryProvider` từng dòng | Thấp | Vừa | Interface nhận danh sách id và trả theo lô | Danh sách yêu cầu chậm khi nhiều dòng | Cache tóm tắt trong core |
| R5 | Interface thiếu hoặc thừa, Lab (C4) phát hiện muộn | Vừa | Vừa | Soạn trước danh sách nhu cầu của Lab ở `SPI_CATALOG.md` | C4 phải sửa `dx-core-api` | Cho phép sửa `dx-core-api` ở C4 nếu không phá Farm |

## 3. Nợ kỹ thuật chấp nhận

- Interface cho thời tiết, lịch nhắc, CV vẫn chỉ của Farm; Lab không dùng.
- Không viết test mới cho việc "cắm app" (RD6).
- Web chưa có vỏ chọn app (C5); đổi tên trang ở phase này theo từng trang.
- Service account của backend chưa có quyền tạo vai Keycloak; `RoleBootstrap` chỉ ghi log khi bị từ chối. Cấp quyền ở C3.
- Bảng `documents` của AI vector DB vẫn có cột `disease_code` (schema của AI, không phải core).
- OpenAPI lệch baseline ở hậu tố `operationId`, mô tả `workflow-cases` và kiểu tham số `status` của `workflow-cases`.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: E2E hiện có không đạt sau WPC2-02 hoặc WPC2-05.
- Rollback: quay lại nhánh trước phase; DB dựng lại.

## 5. Tác động ngoài phase

- Lên các phase sau: C4 cài interface; C5 dùng `GET /api/v1/app`; C3 dùng `RoleProvider`.
- Lên demo, tài liệu nộp bài, thời hạn: web, seed và E2E đổi tên endpoint; demo script và ảnh minh họa phải khớp.
