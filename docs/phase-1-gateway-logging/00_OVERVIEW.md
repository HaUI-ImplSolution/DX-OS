# Phase 1: Log truy cập và IP thật tại gateway

| Mục | Giá trị |
|---|---|
| Trạng thái | Đang làm |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A (nền tảng của phase 2 và phase 3) |
| Ứng dụng và module chính | dùng chung (Infra, SSO) |
| Ước lượng | 1 đến 2 ngày |
| Người phụ trách | Khuong |
| Bắt đầu, dự kiến xong | 23/10/2026, 25/10/2026 |
| Phụ thuộc | không |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Góp ý bảo vệ server | Ghi lại traffic để audit về sau | Quan trọng | Toàn bộ |
| Quyết định về Cloudflare Tunnel (`deploy/docker-compose.cloudflared.yml`) | Mọi request tới gateway đi qua `cloudflared` | Quan trọng | Phần địa chỉ client |

_Rubric: an toàn nghiệp vụ và vận hành, bằng chứng audit cho báo cáo kỹ thuật._

## 2. Lý do

- Vấn đề đang gặp: gateway chỉ thấy IP của container `cloudflared`, nên log không phân biệt được người dùng và mọi bộ đếm `limit-count` theo `remote_addr` dùng chung một khóa.
- Vấn đề đang gặp: log của các service không có giới hạn dung lượng, mất khi container bị tạo lại, và Keycloak không ghi sự kiện đăng nhập.
- Vì sao làm ở phase này: phase 2 (giới hạn tốc độ theo IP) và phase 3 (audit) đều cần IP thật và `X-Request-Id`.

## 3. Tổng quan sửa đổi

Gateway lấy IP thật từ `CF-Connecting-IP`, ghi access log JSON có `X-Request-Id`, và Docker xoay vòng log của mọi service.
Keycloak ghi sự kiện đăng nhập, thấy IP thật và khóa tạm tài khoản bị đoán mật khẩu.
Một mục vận hành trong `deploy/OPERATIONS.md` hướng dẫn xem log.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Không | |
| `dx-core` (cài đặt dùng chung) | Không | |
| `app-farm` | Không | |
| `app-lab` | Không | |
| `dx-host` (chạy, chọn app) | Không | |
| Frontend (web) | Không | |
| Mobile | Không | |
| AI | Không | |
| Data / Seed / Migration | Không | |
| Infra / Deploy / CI | Có | `apisix/config.yaml`, `apisix/common.yaml`, `docker-compose.yml`, `OPERATIONS.md` |
| SSO | Có | `realm-dxos.json`, `KC_PROXY_HEADERS` trong overlay Cloudflare |
| Docs | Có | `sso/README.md`, `CHANGELOG.md` |

## 4. Phạm vi

- Trong phạm vi: IP thật tại APISIX, access log JSON, `X-Request-Id`, xoay vòng log Docker, sự kiện và khóa tạm của Keycloak, tài liệu xem log.
- Ngoài phạm vi: giới hạn tốc độ và WAF (phase 2), bảng `audit_log` của backend (phase 3), Loki và Grafana (không làm, xem `04_TRADEOFFS_RISKS.md`).
- Giả định: máy chủ công khai chạy sau Cloudflare Tunnel, bản chạy LAN không có `CF-Connecting-IP`.
- Ranh giới Core/App: không đụng mã backend.

## 5. Ưu điểm và hạn chế

- Ưu điểm: một cấu hình dùng chung cho cả chế độ LAN và Cloudflare, không cần overlay riêng; sửa luôn việc các bộ đếm giới hạn dùng chung một khóa.
- Hạn chế: log theo kích thước, không đảm bảo thời hạn giữ; Keycloak đang chạy phải được áp cấu hình realm thủ công.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: truy cập app qua `https://khuongdev.io.vn`, dòng access log của request đó có IP của máy truy cập, không phải IP container.
- Sản phẩm bàn giao: cấu hình gateway, compose, realm, `deploy/OPERATIONS.md`.
- Tiêu chí đo được:
  - Access log là JSON hợp lệ, mỗi dòng có `remote_addr`, `status`, `request_time`, `request_id`, `upstream_addr`.
  - Request gửi trực tiếp vào cổng LAN kèm header `CF-Connecting-IP` giả không làm đổi `remote_addr` trong log.
  - Đăng nhập sai ba lần tạo sự kiện `LOGIN_ERROR` có `ipAddress` là IP thật.
  - Mọi service có `max-size` và `max-file` trong `docker compose config`.
- Với mỗi app đang hỗ trợ: khởi động được với `DXOS_APP=farm` và `DXOS_APP=lab`, không đổi mã core.
- Bằng chứng: trích đoạn log và `docker compose config`.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: không.
- Phase sau cần từ phase này: phase 2 dùng IP thật làm khóa giới hạn; phase 3 lưu `request_id` và IP vào `audit_log`.

## 8. Ảnh hưởng đến thi

- Điểm rubric liên quan: an toàn và vận hành.
- Dùng làm bằng chứng cho: báo cáo kỹ thuật (mục bảo mật) và câu hỏi hội đồng về giám sát.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Thời gian giữ log truy cập | A: theo dung lượng (khoảng 1 đến 2 tuần). B: thêm volume và `logrotate` giữ 90 ngày | Trước khi code | _(đề xuất A: log truy cập chỉ phục vụ vận hành, audit lâu dài nằm ở phase 3)_ |
| Q2 | Dải mạng tin cậy cho `CF-Connecting-IP` | A: `172.16.0.0/12` (dải mặc định của Docker). B: cố định subnet mạng compose | Trước khi code | _(đề xuất A, chuyển B nếu máy chạy có dải Docker khác)_ |
| Q3 | Áp cấu hình realm cho Keycloak đang chạy | A: Admin Console, ghi các bước. B: script `kcadm.sh` | Trước khi code | _(đề xuất A)_ |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger (springdoc) nếu đổi API: không áp dụng.
- [ ] `ARCHITECTURE.md` nếu có sơ đồ luồng request: kiểm tra.
- [ ] `deploy/.env.example` nếu thêm biến: không thêm biến mới.
- [ ] `docs/BACKLOG.md`: ghi việc phát sinh.
