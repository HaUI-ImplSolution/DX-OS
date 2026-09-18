# Phase C2: Tổng quát hóa core và các interface

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | `dx-core-api`, `dx-core`, `app-farm` |
| Ước lượng | 5-6 ngày |
| Người phụ trách | {chưa phân công} |
| Bắt đầu, dự kiến xong | {chưa chốt} |
| Phụ thuộc | C1 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Đề thi HaUI mục 4.1 | Identity/RBAC, API trung gian, dữ liệu, workflow dùng chung | Nghiêm trọng | Toàn bộ |
| Đề thi HaUI mục 13 | "Thay đổi ứng dụng nghiệp vụ, Open-Core có phải thay đổi theo không?" | Nghiêm trọng | Toàn bộ |
| RD3, RD10 ở `refactor/README.md` | `Workspace`, workflow cấu hình được | Nghiêm trọng | Toàn bộ |

## 2. Lý do

- Vấn đề đang gặp: sau C1 ranh giới đã có nhưng core còn mang dấu vết nông nghiệp.
  `Household` là nông hộ; `NotificationType` liệt kê sự kiện của Farm; `WorkflowStatus` là enum 4 trạng thái kiểu duyệt; `SecurityConfig` biết endpoint công khai của Farm; hộp duyệt nằm ở Farm.
- Vì sao làm ở phase này: app Lab (C4) cần các interface này để cài mà không sửa core.

## 3. Tổng quan sửa đổi

Thứ tự: đổi `Household` thành `Workspace` + `WorkspaceMember` → workflow cấu hình được → đưa hộp duyệt về core → tổng quát hóa thông báo và kênh chat → interface bảo mật, vai, manifest app → cổng AI/tri thức theo namespace → đăng ký BI, xử lý tệp, health.
Mỗi bước giữ Farm chạy đúng như trước, chỉ đổi cách Farm cài interface.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Có | Toàn bộ interface ở `SPI_CATALOG.md` |
| `dx-core` (cài đặt dùng chung) | Có | Workspace, workflow, hộp duyệt, notification, AI gateway |
| `app-farm` | Có | Cài interface, bỏ phần dùng chung đã về core |
| `app-lab` | Không | Chưa tạo |
| `dx-host` (chạy, chọn app) | Có | Endpoint manifest `GET /api/v1/app` |
| Frontend (web) | Không | Farm giữ nguyên `/households`; trang `Workspaces` dùng chung thuộc C5 |
| Mobile | Không | |
| AI | Có | Tham số `namespace` cho truy vấn RAG |
| Data / Seed / Migration | Có | Bảng `workspaces`, `workspace_members`, `notifications` về core |
| Infra / Deploy / CI | Không | Seed và E2E của Farm giữ nguyên |
| Docs / PoF | Có | `SPI_CATALOG.md`, hướng dẫn tạo app |

## 4. Phạm vi

- Trong phạm vi: các interface ở `SPI_CATALOG.md`, đổi tên và tổng quát hóa phần dùng chung.
- Ngoài phạm vi: đổi tên realm (C3); app Lab (C4); giao diện Lab và vỏ chọn app (C5).
- Giả định: RD3 và RD10 đã chốt ở C0; baseline và OpenAPI của C1 đã có.
- Ranh giới Core/App: mọi interface ở `dx-core-api`; Farm cài; core không import Farm.

## 5. Ưu điểm và hạn chế

- Ưu điểm: Lab cài interface mà không sửa core; hộp duyệt, thông báo, workspace dùng lại nguyên.
- Hạn chế: Farm giữ thêm một lớp bí danh `/api/v1/households` để không đổi hợp đồng; workflow cấu hình được phức tạp hơn enum.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: Farm chạy đúng như trước (tạo thửa, duyệt, báo cáo, chẩn đoán, thông báo) sau khi core đã trung tính.
- Sản phẩm bàn giao: `dx-core-api` đủ interface ở `SPI_CATALOG.md`; Farm cài hết các interface cần; migration core có `workspaces`, `workspace_members`, `notifications`.
- Tiêu chí đo được:
  - Tìm `Household`, `Parcel`, `Farm`, `Mattermost` ADMIN/ENGINEER trong `dx-core-api` và `dx-core` không ra kết quả.
  - `dx-core` không còn danh sách endpoint, vai hay tên bảng của Farm.
  - Core khởi động với một app giả không có interface nào ngoài `AppModule` vẫn lên được (kiểm tay).
  - OpenAPI của Farm (kể cả `/api/v1/households`) không đổi so với baseline; core chỉ thêm `/api/v1/workspaces` và `/api/v1/app`.
- Bằng chứng: kết quả tìm kiếm, diff OpenAPI, log test và E2E do người dùng chạy.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: ranh giới module của C1.
- Phase sau cần từ phase này: C4 cài interface; C5 dùng manifest; C3 cần `RoleProvider`.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: Open-Core headless có vai trò thực (10 điểm); khả năng mở rộng (10 điểm).
- Dùng làm bằng chứng cho: câu hỏi định hướng 3, 4, 10 ở mục 13 của đề.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Trạng thái workflow (RD10) | A: giữ enum 4 trạng thái. B: `CaseTypeDefinition` khai báo trạng thái và bước chuyển | Đầu phase | Chốt B (09/10/2026) |
| Q2 | Đường dẫn API workspace | A: đổi `/households` thành `/workspaces`, cập nhật web và seed. B: core có `/workspaces`, Farm giữ `/households` làm bí danh | Đầu phase | Chốt B (09/10/2026): Farm không đổi hợp đồng |
| Q3 | `WorkspaceMember` có vai riêng không | A: chỉ liên kết người dùng. B: kèm vai trong workspace (chủ, thành viên) | Đầu phase | Chốt B (09/10/2026, theo đề xuất) |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger (springdoc) cho `/workspaces`, `/app`.
- [ ] `SPI_CATALOG.md` khớp chữ ký thực tế; hướng dẫn tạo app mới (nháp cho C7).
- [ ] `LICENSES.md`, `.env.example` nếu có đổi.
