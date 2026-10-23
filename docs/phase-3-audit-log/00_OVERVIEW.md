# Phase 3: Audit log trong backend

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | core (dùng chung), app-farm, app-lab |
| Ước lượng | 3 ngày |
| Người phụ trách | Khuong |
| Bắt đầu, dự kiến xong | 29/10/2026, 02/11/2026 |
| Phụ thuộc | Phase 1 (IP thật và `X-Request-Id`) |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Góp ý bảo vệ server | Sau cần audit gì sẽ dễ | Quan trọng | Toàn bộ |
| `docs/phase-1-gateway-logging` | `request_id`, IP thật | Quan trọng | Dùng làm khóa truy vết |

_Rubric: an toàn nghiệp vụ, truy vết và trách nhiệm giải trình._

## 2. Lý do

- Vấn đề đang gặp: backend biết ai tạo hoặc sửa bản ghi (`created_by`, `updated_by`) và `workflow_event` ghi lịch sử duyệt, nhưng không có nơi ghi lần truy cập bị từ chối hay thao tác quản trị (thêm thành viên workspace, đổi vai, xóa mềm).
- Vấn đề đang gặp: không có cách xem "ai bị từ chối ở đâu, từ IP nào" ngoài việc đọc log gateway.
- Vì sao làm ở phase này: cần `request_id` và IP thật từ phase 1 để mỗi bản ghi audit nối được với dòng log gateway.

## 3. Tổng quan sửa đổi

Bảng `audit_log` chỉ thêm, không sửa hay xóa, ghi các sự kiện bảo mật và thao tác quản trị của backend.
Interface `AuditRecorder` ở `dx-core-api` để core và app cùng ghi.
ADMIN xem qua `GET /api/v1/audit-logs`; Superset có thể dựng dashboard từ cùng bảng.
Một job định kỳ cảnh báo lên Mattermost khi có nhiều lần bị từ chối từ cùng một nguồn.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Có | `AuditRecorder`, `AuditEvent`, `AuditOutcome` |
| `dx-core` (cài đặt dùng chung) | Có | `AuditService`, `AuditLogController`, filter ngữ cảnh request, job cảnh báo |
| `app-farm` | Có | Ghi một số thao tác qua `AuditRecorder` |
| `app-lab` | Có | Ghi một số thao tác qua `AuditRecorder` |
| `dx-host` (chạy, chọn app) | Không | |
| Frontend (web) | Không | Màn xem audit nằm ngoài phạm vi, xem mục 4 |
| Mobile | Không | |
| AI | Không | |
| Data / Seed / Migration | Có | Migration core `V4` |
| Infra / Deploy / CI | Không | |
| Docs | Có | Swagger, `README.md`, `CHANGELOG.md`, `deploy/OPERATIONS.md` |

## 4. Phạm vi

- Trong phạm vi: bảng và interface audit, ngữ cảnh request (`request_id`, IP, client), ghi 401 có token và 403, ghi thao tác quản trị, API đọc cho ADMIN, cảnh báo Mattermost.
- Ngoài phạm vi: màn hình xem audit trên web, ghi sự kiện đăng nhập (đã có ở Keycloak từ phase 1), `pgaudit` (image `pgvector/pgvector:pg16` không kèm extension này), xóa audit theo thời hạn.
- Giả định: gateway chuyển `X-Request-Id` và IP thật cho backend (xác nhận ở WP3-02); backend không publish cổng ra ngoài.
- Ranh giới Core/App: bảng, interface và nơi ghi dùng chung nằm ở `dx-core-api` và `dx-core`; app chỉ gọi `AuditRecorder` và định nghĩa tên hành động của mình; core không import gói của app.

## 5. Ưu điểm và hạn chế

- Ưu điểm: bằng chứng truy vết nằm trong cùng database, không thêm thành phần; bảng append-only dùng lại mẫu của `workflow_event`.
- Hạn chế: bảng không có cách xóa theo thời hạn (trigger chặn); dữ liệu tăng theo thời gian, cần theo dõi dung lượng.
- Trade-off:
  - Ghi từ backend thay vì trigger Postgres: biết người dùng, vai và `request_id`, nhưng không bắt được thay đổi SQL trực tiếp vào database.
  - Không dùng Supabase: trùng Keycloak, MinIO, backend và không giải quyết bài toán audit.
  - Giới hạn số bản ghi 401 và 403 mỗi nguồn để kẻ tấn công không làm đầy bảng.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: `farmer1` gọi API chỉ dành cho ADMIN, `admin1` mở `GET /api/v1/audit-logs` thấy một bản ghi `ACCESS_DENIED` có tên người dùng, IP, đường dẫn và `request_id`; `request_id` đó tìm được trong log gateway.
- Sản phẩm bàn giao: migration, mã core, Swagger, tài liệu.
- Tiêu chí đo được:
  - Gọi sai quyền trả 403 và tạo đúng một bản ghi `ACCESS_DENIED`.
  - `UPDATE` hoặc `DELETE` trên `audit_log` bị database từ chối.
  - Tài khoản không phải ADMIN gọi `GET /api/v1/audit-logs` nhận 403.
  - Một nguồn gây hơn ngưỡng 403 trong một phút chỉ tạo tối đa số bản ghi đã cấu hình, phần còn lại được đếm.
  - Vượt ngưỡng cảnh báo thì kênh Mattermost nhận một tin.
- Với mỗi app đang hỗ trợ: khởi động được với `DXOS_APP=farm` và `DXOS_APP=lab`, core không phải sửa để nhận app đó.
- Bằng chứng: kết quả truy vấn API, ảnh chụp tin cảnh báo.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: phase 1 xong.
- Phase sau cần từ phase này: không.

## 8. Ảnh hưởng đến thi

- Điểm rubric liên quan: an toàn nghiệp vụ, trách nhiệm giải trình.
- Dùng làm bằng chứng cho: báo cáo kỹ thuật và câu hỏi hội đồng về truy vết thao tác.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Giữ audit bao lâu | A: giữ vô thời hạn, theo dõi dung lượng. B: phân vùng theo tháng để xóa được | Trước khi code | _(đề xuất A)_ |
| Q2 | Kênh Mattermost nhận cảnh báo | A: kênh quản trị sẵn có. B: tạo kênh mới | Trước khi code | _(chưa chốt)_ |
| Q3 | Dashboard Superset từ `audit_log` | A: làm ở phase này. B: để `docs/BACKLOG.md` | Trước khi code | _(đề xuất B)_ |
| Q4 | Thao tác nào của Farm và Lab được ghi | Danh sách ở `01_PLAN.md` WP3-04 | Trước khi code | _(chưa chốt)_ |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger (springdoc) cho `GET /api/v1/audit-logs`.
- [ ] `ARCHITECTURE.md` (mục 3) và `CONTRIBUTING.md` (cách app ghi audit qua `AuditRecorder`).
- [ ] Sơ đồ ở `ARCHITECTURE.md`, `LICENSES.md`, `.env.example` (biến ngưỡng nếu có).
- [ ] `docs/BACKLOG.md`: xóa việc đã xong, thêm việc phát sinh (màn xem audit, dashboard, xóa theo thời hạn).
