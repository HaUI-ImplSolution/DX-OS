# Phase 2: Giới hạn truy cập và thu hẹp bề mặt quản trị

| Mục | Giá trị |
|---|---|
| Trạng thái | Đang làm (chờ kiểm trên VPS) |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | dùng chung (Infra) |
| Ước lượng | 1 đến 2 ngày |
| Người phụ trách | Khuong |
| Bắt đầu, dự kiến xong | 26/10/2026, 28/10/2026 |
| Phụ thuộc | Phase 1 (IP thật tại gateway) |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Góp ý bảo vệ server | Cơ chế bảo vệ server | Quan trọng | Toàn bộ |
| `docs/phase-1-gateway-logging` | `remote_addr` là IP thật | Quan trọng | Dùng làm khóa giới hạn |

_Rubric: an toàn và vận hành._

## 2. Lý do

- Vấn đề đang gặp: chỉ một vài route có giới hạn tốc độ; đăng nhập (`/realms/*`) và `/api/*` không bị giới hạn.
- Vấn đề đang gặp: cổng quản trị (Superset, MinIO Console, Keycloak `/admin/`) đang mở ra Internet.
- Vì sao làm ở phase này: giới hạn theo IP chỉ có nghĩa sau khi phase 1 cho IP thật.

## 3. Tổng quan sửa đổi

Gateway giới hạn tốc độ theo IP thật cho đăng nhập và API.
Cloudflare bật WAF, chặn bot và một rule giới hạn đăng nhập.
Các giao diện quản trị đặt sau Cloudflare Access.
Mọi cấu hình Cloudflare được ghi thành danh sách kiểm trong `deploy/OPERATIONS.md`.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Không | |
| `dx-core` (cài đặt dùng chung) | Không | |
| `app-farm` | Không | |
| `app-lab` | Không | |
| `dx-host` (chạy, chọn app) | Không | |
| Frontend (web) | Không | |
| Mobile | Không | Kiểm hồi quy gửi ảnh và đăng nhập |
| AI | Không | |
| Data / Seed / Migration | Không | |
| Infra / Deploy / CI | Có | `apisix/common.yaml`, `OPERATIONS.md`; cấu hình Cloudflare ngoài repo |
| Docs | Có | `deploy/OPERATIONS.md`, `CHANGELOG.md` |

## 4. Phạm vi

- Trong phạm vi: giới hạn tốc độ theo IP ở gateway, danh sách kiểm cấu hình Cloudflare, Cloudflare Access cho cổng quản trị.
- Ngoài phạm vi: `audit_log` (phase 3), giới hạn kích thước body, tự động chặn IP (cần `stick-table` kiểu HAProxy, xem `docs/phase-1-gateway-logging/04_TRADEOFFS_RISKS.md`).
- Giả định: máy chủ công khai chạy qua Cloudflare Tunnel và đã có tài khoản Zero Trust gói Free.
- Ranh giới Core/App: không đụng mã backend.

## 5. Ưu điểm và hạn chế

- Ưu điểm: chặn dò mật khẩu và quét API từ ngoài mà không đụng mã; cổng quản trị không còn lộ.
- Hạn chế: nhiều người dùng chung một IP (mạng di động) có thể chạm giới hạn; Cloudflare Access thêm một bước đăng nhập cho kỹ sư và quản trị.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: gửi liên tục yêu cầu đăng nhập từ một IP thì nhận 429, trong lúc IP khác vẫn đăng nhập được.
- Sản phẩm bàn giao: cấu hình gateway, `deploy/OPERATIONS.md` có danh sách kiểm Cloudflare.
- Tiêu chí đo được:
  - Vượt ngưỡng ở `/realms/dxos/protocol/openid-connect/token` trả 429 kèm header `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
  - `bi.`, `files.` và `/admin/` yêu cầu đăng nhập Cloudflare Access trước khi tới ứng dụng.
  - `farmer1` đăng nhập, chụp và gửi ảnh báo cáo trên mobile vẫn thành công.
- Với mỗi app đang hỗ trợ: khởi động được với `DXOS_APP=farm` và `DXOS_APP=lab`.
- Bằng chứng: ảnh chụp Cloudflare Security Events, trích đoạn log 429.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: phase 1 xong (`real_ip_header`).
- Phase sau cần từ phase này: phase 3 ghi lại các lần 401, 403, 429.

## 8. Ảnh hưởng đến thi

- Điểm rubric liên quan: an toàn và vận hành.
- Dùng làm bằng chứng cho: báo cáo kỹ thuật (mục bảo mật).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Ngưỡng giới hạn | A: đăng nhập 120 lượt mỗi phút mỗi IP (form và token chung bộ đếm), API 600 lượt mỗi phút mỗi IP. B: chặt hơn | Trước khi code | _(đề xuất A)_ |
| Q2 | Cổng quản trị sau Cloudflare Access | A: có, cho `bi.`, `files.`, `/admin*`. B: chặn theo IP ở gateway. C: để mở | Trước khi code | _(chưa chốt)_ |
| Q3 | Swagger (`/swagger-ui`) | A: công khai cho hội đồng xem. B: sau Access | Trước khi code | _(đề xuất A)_ |
| Q4 | Chặn theo quốc gia | A: không. B: chỉ cho phép Việt Nam | Trước khi code | _(đề xuất A, vì hội đồng có thể truy cập từ nước ngoài)_ |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger (springdoc) nếu đổi API: không áp dụng.
- [ ] `ARCHITECTURE.md` nếu sơ đồ luồng request có thêm lớp Cloudflare Access.
- [ ] `deploy/.env.example` nếu thêm biến: không thêm biến mới.
- [ ] `docs/BACKLOG.md`: ghi việc phát sinh.
