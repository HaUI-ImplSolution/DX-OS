# Phase 4: Màn hình giám sát và truy vết

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A (hoàn tất chuỗi gateway, bảo vệ, audit của phase 1 đến 3) |
| Ứng dụng và module chính | dùng chung (module mới `dx-monitor`, core, Infra, portal) |
| Ước lượng | 9 đến 12 ngày |
| Người phụ trách | Khuong |
| Bắt đầu, dự kiến xong | _(chưa chốt)_ |
| Phụ thuộc | Phase 1 đến 3 đã xong (log gateway, giới hạn tốc độ, `audit_log`) |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| `docs/BACKLOG.md` mục 5 | Màn hình xem audit trên web | Quan trọng | Toàn bộ, nằm trong màn hình giám sát |
| `docs/BACKLOG.md` mục 5 | Ghi `AuditOutcome.FAILED` khi thao tác lỗi | Quan trọng | Toàn bộ |
| `docs/BACKLOG.md` mục 5 | Dashboard Superset từ `audit_log` | Theo dõi | Thay bằng màn hình giám sát riêng (Q1), không làm dashboard Superset |
| `docs/BACKLOG.md` mục 5 | Xóa audit theo thời hạn | Theo dõi | Không làm, `audit_log` giữ nguyên chỉ thêm |

_Rubric: an toàn nghiệp vụ và vận hành, bằng chứng truy vết cho báo cáo kỹ thuật và video demo._

## 2. Lý do

- Vấn đề đang gặp: dữ liệu truy vết nằm rải rác ở bốn nơi (log gateway, `audit_log`, sự kiện Keycloak, log backend) và chỉ đọc được bằng `jq`, `psql` hoặc Admin Console.
  Muốn biết "ai đang bị chặn, lỗi bắt đầu lúc nào, request đó đi qua những đâu" phải ghép tay.
- Vấn đề đang gặp: log gateway mất khi container bị tạo lại, nên `429`, `401` và `5xx` ở gateway (dấu hiệu quét, đoán mật khẩu, backend sập) không còn sau khi cập nhật.
- Vấn đề đang gặp: `audit_log` chưa có lỗi hệ thống (`5xx`) và thao tác quản trị thất bại (`FAILED`).
- Vấn đề đang gặp: công cụ giám sát nằm trong backend thì mất đúng lúc backend sập.
- Vì sao làm ở phase này: nền (IP thật, `request_id`, `audit_log`) đã có, màn hình chỉ khai thác lại; hoãn thì dữ liệu `429`/`5xx` tiếp tục mất sau mỗi lần cập nhật.

## 3. Tổng quan sửa đổi

Một dịch vụ Spring Boot mới `monitor` (module `backend/dx-monitor`, container riêng, không phụ thuộc `dx-core`) lo toàn bộ phần giám sát:
nhận sự kiện bất thường từ gateway, tự dò trạng thái các dịch vụ, đọc `audit_log` và sự kiện đăng nhập của Keycloak, phục vụ màn hình giám sát cho ADMIN và gửi cảnh báo Mattermost.
Vì tách khỏi backend nên màn hình và cảnh báo vẫn chạy khi backend sập.
Màn hình có ở portal dưới dạng một link, mở `/monitor/` qua gateway, đăng nhập bằng SSO Keycloak, chỉ vai ADMIN vào được.
Backend chỉ thêm việc ghi lỗi `5xx` và thao tác thất bại vào `audit_log`.

Thứ tự: ghi lỗi vào audit (WP4-01) → nền dịch vụ monitor (WP4-02, WP4-03) → nguồn dữ liệu (WP4-04, WP4-05) → đọc và hiển thị (WP4-06, WP4-07) → cảnh báo và tài liệu (WP4-08).

### Tín hiệu giữ lại (chỉ những thông số giúp truy vết)

| Nhóm | Thông số | Nguồn | Trả lời câu hỏi |
|---|---|---|---|
| Tấn công | Truy cập bị từ chối (`ACCESS_DENIED`, `AUTH_FAILED`) theo IP và giờ | `audit_log` (đã có) | Ai đang dò quyền, từ đâu |
| Tấn công | Request bị giới hạn tốc độ (`429`), token bị gateway từ chối (`401`), dò đường dẫn (`404` dồn dập) theo IP | `monitor.gateway_event` (mới) | Ai đang quét hoặc xả request |
| Tấn công | Đăng nhập sai (`LOGIN_ERROR`) theo IP, tài khoản, giờ | Keycloak `event_entity` | Ai đang đoán mật khẩu |
| Lỗi hệ thống | Lỗi `5xx` theo đường dẫn và giờ | `audit_log` (mới) và `gateway_event` | Lỗi nào, từ lúc nào |
| Lỗi hệ thống | `502`/`503`/`504` của gateway | `gateway_event` | Dịch vụ sau gateway có sập không |
| Lỗi hệ thống | Dịch vụ đổi trạng thái `UP`/`DOWN` | `monitor.service_health_event` (mới, tự dò) | Dịch vụ nào ngừng, ngừng bao lâu |
| Lỗi hệ thống | Request chậm bất thường (từ 10 giây, trừ route chẩn đoán AI) | `gateway_event` | Đường dẫn nào chậm |
| Truy vết | Thao tác quản trị gần đây, kể cả thất bại | `audit_log` | Ai đã đổi gì |
| Truy vết | Mọi sự kiện của một `request_id` hoặc một IP | `audit_log`, `gateway_event`, Keycloak | Một request hay một địa chỉ đã làm gì |

Không đưa lên màn hình: tổng lượt truy cập, băng thông, phân bố trình duyệt, CPU/RAM (không giúp truy vết; Docker và Cloudflare Analytics đã có).

## 4. Phạm vi

- Trong phạm vi: ghi `5xx` và `FAILED` vào `audit_log`; module `dx-monitor` (đăng nhập ADMIN, nạp sự kiện gateway, dò dịch vụ, API đọc, giao diện, cảnh báo, dọn dữ liệu cũ); role đọc sự kiện Keycloak; link ở portal; tài liệu vận hành.
- Ngoài phạm vi: Prometheus, Grafana, Loki, dashboard Superset (xem `04_TRADEOFFS_RISKS.md`), xóa audit theo thời hạn, đo CPU/RAM/disk, truy vết phân tán giữa Backend và AI, thao tác ghi từ màn hình (chặn IP, đóng tài khoản).
- Giả định: máy chủ công khai chạy sau Cloudflare Tunnel; `request_id` có ở gateway và backend (phase 1, 3); Keycloak là nơi cấp vai ADMIN.
- Ranh giới Core/App: `dx-monitor` chỉ là dịch vụ vận hành, không import `dx-core-api` hay `dx-core` và không biết app nào đang chạy; chỉ đọc `audit_log` qua role chỉ đọc.
  `dx-core-api` thêm một factory `AuditEvent.failed`; Farm và Lab chỉ thêm lời gọi.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Có | `AuditEvent.failed(...)` |
| `dx-core` (cài đặt dùng chung) | Có | `GlobalExceptionHandler` và `WorkspaceController` ghi `SERVER_ERROR`, `FAILED` |
| `app-farm` | Có | Thao tác quản trị lỗi ghi `FAILED` |
| `app-lab` | Có | Như Farm |
| `dx-host` (chạy, chọn app) | Không | |
| `dx-monitor` (module mới) | Có | Dịch vụ giám sát, jar và container riêng |
| Frontend (web) | Không | |
| Mobile | Không | |
| AI | Không | |
| Data / Seed / Migration | Có | Flyway riêng của monitor (schema `monitor`) |
| Infra / Deploy / CI | Có | Service `monitor`, one-shot `monitor-init`, `http-logger` của APISIX, route `/monitor/*`, job CI |
| SSO | Có | Client `dxos-monitor` trong `realm-dxos.json` |
| Portal | Có | Link "Giám sát" và dòng trạng thái |
| Docs | Có | `deploy/OPERATIONS.md`, `README.md`, `ARCHITECTURE.md`, `CHANGELOG.md`, `docs/BACKLOG.md`, `docs/TESTING.md`, `LICENSES.md` |

## 5. Ưu điểm và hạn chế

- Ưu điểm: giám sát độc lập với backend nên vẫn thấy và báo được khi backend sập; dữ liệu bền sau khi container bị tạo lại; một `request_id` nối được gateway, audit và log backend; không thêm công cụ ngoài Spring Boot, Postgres, Mattermost đã có.
- Hạn chế: thêm một container cần vận hành; phải tự viết giao diện thay vì dùng Superset; chỉ lưu sự kiện bất thường nên không có biểu đồ tổng lưu lượng.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: ADMIN mở portal, bấm "Giám sát", đăng nhập SSO, thấy trạng thái dịch vụ và các con số 24 giờ.
  Kịch bản tấn công giả lập (gọi API sai quyền, xả request quá giới hạn, đăng nhập sai nhiều lần, dừng backend một phút) hiện lên đúng ô trong tối đa 30 giây.
  Dán `request_id` của một request bị chặn vào ô truy vết thấy đủ sự kiện của gateway và backend.
  Kịch bản dừng backend: màn hình giám sát vẫn mở được, dịch vụ `backend` hiện `DOWN`, kênh Mattermost nhận tin.
- Sản phẩm bàn giao: module `dx-monitor`, thay đổi ghi audit ở core và app, cấu hình APISIX, Keycloak, compose, link portal, tài liệu vận hành.
- Tiêu chí đo được:
  - Request `429` và `5xx` thấy ở `monitor.gateway_event` sau khi container `apisix` bị tạo lại.
  - Lỗi `5xx` do backend sinh ra có bản ghi `SERVER_ERROR` với `outcome = FAILED` và `request_id`.
  - Tài khoản `ENGINEER` và `FARMER` mở `/monitor/` bị từ chối; `ADMIN` vào được.
  - `monitor_app` và `keycloak_ro` không đọc được bảng nghiệp vụ, `credential` hay `user_entity`.
  - Backend ngừng không làm `dx-monitor` ngừng; `dx-monitor` ngừng không làm hỏng request của người dùng.
- Với mỗi app đang hỗ trợ: khởi động được sau khi đổi `DXOS_APP` (Farm, Lab), màn hình giám sát có ở cả hai, core không phải sửa để nhận app.
- Bằng chứng: ảnh màn hình (đã che IP và email thật), kết quả `docs/TESTING.md` mục mới, tin Mattermost, log kịch bản demo.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: IP thật và `request_id` (phase 1), giới hạn tốc độ trả `429` (phase 2), `audit_log` và `AuditRecorder` (phase 3).
- Phase sau cần từ phase này: không có phase kế tiếp; việc còn lại chuyển vào `docs/BACKLOG.md`.

## 8. Ảnh hưởng đến thi

- Điểm rubric liên quan: an toàn nghiệp vụ, khả năng vận hành và truy vết, tính trực quan của giải pháp.
- Dùng làm bằng chứng cho: báo cáo kỹ thuật (mục an toàn và vận hành), slide kiến trúc, video demo đoạn "bị tấn công thì truy vết thế nào".

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Giám sát hiển thị ở đâu | A: Superset. B: màn hình mới, link ở portal | Trước khi code | B, một dịch vụ Spring Boot riêng (Khuong chốt) |
| Q2 | Cách đưa sự kiện Keycloak vào màn hình | A: role `keycloak_ro` chỉ đọc một view. B: gọi Admin REST API rồi chép | Trước WP4-06 | A (Khuong chốt) |
| Q3 | Gateway ghi sự kiện bằng cách nào | A: `http-logger` của APISIX gọi dịch vụ. B: sidecar đọc stdout | Trước WP4-04 | A, chờ spike WP4-04-INFRA-01 (Khuong chốt) |
| Q4 | Thời hạn giữ `gateway_event` và `service_health_event` | A: 30 ngày. B: 90 ngày | Trước WP4-04 | 30 ngày (Khuong chốt) |
| Q5 | Ngưỡng cảnh báo mới | `5xx`: 5 lần trong 5 phút; dịch vụ `DOWN`: ngay; `429`: 10 lần trong 10 phút của từng IP | Trước WP4-08 | Theo đề xuất (Khuong chốt) |
| Q6 | Vị trí mã của dịch vụ giám sát | A: module `dx-monitor` trong `backend/` (đề xuất: dùng chung parent pom, `./mvnw`, CI backend; container riêng). B: thư mục cấp cao riêng như `ai/`, `portal/` | Trước WP4-02 | A (Khuong chốt theo đề xuất) |
| Q7 | Đặt `/monitor/*` sau Cloudflare Access như `bi.` và `files.` | A: có, thêm lớp bảo vệ. B: không, chỉ SSO + vai ADMIN | Trước khi bật trên VPS | A (Khuong chốt theo đề xuất) |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `README_EN.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger (springdoc): `GET /api/v1/audit-logs` có thêm giá trị `SERVER_ERROR`, `outcome=FAILED`; API của `dx-monitor` có `@Operation` tiếng Anh.
- [ ] `ARCHITECTURE.md` (sơ đồ luồng log, audit và dịch vụ `monitor`), `LICENSES.md` (thư viện biểu đồ nhúng sẵn, giấy phép MIT), `backend/README.md` (module mới), `portal/README.md`, `sso/README.md` (client `dxos-monitor`).
- [ ] `deploy/.env.example` (khóa nạp sự kiện gateway, mật khẩu `monitor_app` và `keycloak_ro`, secret client `dxos-monitor`).
- [ ] `deploy/OPERATIONS.md`, `docs/CLOUDFLARE.md` (ứng dụng Access thứ tư cho `/monitor/*`), `docs/TESTING.md`, `docs/COUNCIL_QA.md`, `docs/SUBMISSION.md`.
- [ ] `docs/BACKLOG.md`: xóa các dòng "Ghi `AuditOutcome.FAILED`", "Màn hình xem audit trên web" và "dashboard Superset từ `audit_log`", thêm việc phát sinh.
