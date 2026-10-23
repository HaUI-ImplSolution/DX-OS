# Plan thực hiện: Phase 2

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP2-01 → WP2-02 → WP2-03.
WP2-02 và WP2-03 là cấu hình Cloudflare, làm song song được sau khi WP2-01 xong.

## WP2-01: Giới hạn tốc độ theo IP ở gateway

- Mục tham chiếu: góp ý bảo vệ server; `docs/phase-1-gateway-logging` WP1-01.
- Mục tiêu: một IP không thể dò mật khẩu hay gọi API quá nhanh.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Gọi `/realms/*/protocol/openid-connect/token` và `/realms/*/login-actions/*` liên tục | Không giới hạn | 30 lượt mỗi phút mỗi IP, vượt thì 429 |
| Gọi `/api/*` liên tục | Chỉ một số route có giới hạn | 600 lượt mỗi phút mỗi IP trên toàn bộ `/api/*`; các giới hạn riêng của route vẫn áp dụng |
| Hai người dùng ở hai IP khác nhau | Chung bộ đếm khi đi qua tunnel | Bộ đếm riêng |

**Hướng làm**

- `common.yaml`: route `keycloak-login` (ưu tiên cao hơn route `keycloak`) cho `/realms/:realm/login-actions/*` và `/realms/:realm/protocol/openid-connect/token` với `limit-count` 30 lượt mỗi phút (khóa `remote_addr`, `rejected_code: 429`, `show_limit_quota_header: true`); `/resources/*` và trang đăng nhập không bị giới hạn.
- `common.yaml`: thêm `limit-count` 600 lượt mỗi phút vào route `api` (cạnh `plugin_config_id: bearer-token`); plugin kiểm token chạy trước nên bộ đếm chỉ tính request có token hợp lệ, còn request token sai nhận 401 mà không bị đếm.
- Thông báo 429 ngắn, bằng tiếng Anh, giống các `rejected_msg` hiện có.
- Core/App: không đụng mã.

**Phạm vi ảnh hưởng**

- Module: Infra.
- File chính: `deploy/apisix/common.yaml`.
- Endpoint, bảng DB, biến môi trường: không.
- Role và client bị ảnh hưởng: mọi người dùng web và mobile.
- Tương thích ngược và dữ liệu cũ: mobile gửi hàng đợi ảnh theo lô; 600 lượt mỗi phút phải đủ cho một lô.

**Kết quả đầu ra**

- Vượt ngưỡng nhận 429 kèm header `X-RateLimit-*`; IP khác không bị ảnh hưởng.

**Phụ thuộc, lưu ý**: Q1 ở `00` mục 9; rủi ro R1 ở `04`.

## WP2-02: Cấu hình Cloudflare và danh sách kiểm

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: lọc lưu lượng xấu trước khi tới VPS.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Bot và quét lỗ hổng phổ biến | Tới thẳng gateway | Cloudflare chặn hoặc thử thách trước |
| Dò mật khẩu phân tán | Chỉ giới hạn ở gateway | Thêm một rule rate limiting của Cloudflare cho `/realms/*/login-actions/*` |

**Hướng làm**

- Danh sách kiểm trong `deploy/OPERATIONS.md`, người vận hành tự bật trong Cloudflare: WAF managed rules, Bot Fight Mode, rate limiting rule cho đăng nhập, HTTPS luôn bật, TLS tối thiểu 1.2, HSTS.
- Theo dõi bằng Security Events của Cloudflare.
- Kiểm hồi quy gửi ảnh và chẩn đoán AI sau khi bật WAF (multipart có thể bị chặn nhầm).

**Phạm vi ảnh hưởng**

- Module: ngoài repo (Cloudflare), chỉ có tài liệu trong repo.
- File chính: `deploy/OPERATIONS.md`.

**Kết quả đầu ra**

- Mọi mục trong danh sách kiểm được bật hoặc ghi rõ lý do bỏ qua.

**Phụ thuộc, lưu ý**: Q4 ở `00` mục 9; rủi ro R2 ở `04`.

## WP2-03: Thu hẹp bề mặt quản trị

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: BI, MinIO Console và Keycloak Admin Console chỉ mở cho người đã xác thực qua Cloudflare Access.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Truy cập `https://bi.khuongdev.io.vn`, `https://files.khuongdev.io.vn` | Tới thẳng trang đăng nhập của dịch vụ | Cloudflare Access đòi đăng nhập (mã gửi qua email) trước |
| Truy cập `https://khuongdev.io.vn/admin/` | Tới thẳng Admin Console | Cloudflare Access đòi đăng nhập trước |
| API, web app, portal, Swagger, chat | Mở | Không đổi |

**Hướng làm**

- Tạo ba ứng dụng Access (`bi.`, `files.`, đường dẫn `/admin*` của domain gốc) với chính sách cho email của quản trị viên và kỹ sư.
- Ghi các bước và lưu ý vào `deploy/OPERATIONS.md`.
- Không đổi cấu hình gateway; kiểm đăng nhập SSO của Superset sau khi bật Access.

**Phạm vi ảnh hưởng**

- Module: ngoài repo (Cloudflare Zero Trust), chỉ có tài liệu trong repo.
- File chính: `deploy/OPERATIONS.md`.
- Role và client bị ảnh hưởng: ADMIN và kỹ sư dùng BI hoặc console; nông dân và mobile không bị ảnh hưởng.

**Kết quả đầu ra**

- Người chưa qua Access không tới được trang đăng nhập của ba dịch vụ trên.
- Mobile và web app của nông dân hoạt động như cũ.

**Phụ thuộc, lưu ý**: Q2, Q3 ở `00` mục 9; rủi ro R3 ở `04`.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Cấu hình gateway | `limit-count` trên route Keycloak và `api` | Mới | Không |
| Response | HTTP 429 trên đăng nhập và API khi vượt ngưỡng | Mới | Không |
| Cloudflare | WAF, Bot Fight Mode, rate limiting, Access | Bật | Không |

## Dữ liệu, migration và rollback

- Migration: không có.
- Dữ liệu cũ: không đổi.
- Rollback: bỏ khối `limit-count` thêm vào `common.yaml` rồi tạo lại `apisix-config` và `apisix`; tắt các mục Cloudflare tương ứng.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Chặn tự động theo IP ở gateway không làm | APISIX không có `stick-table`; Cloudflare WAF đã lo phần này |
| D2 | Không khóa theo quốc gia mặc định | Hội đồng chấm có thể truy cập từ nước ngoài |
| D3 | Swagger giữ công khai | Hội đồng cần xem API; xem Q3 |
