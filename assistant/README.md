# Trợ lý hồ sơ

Dịch vụ mở rộng tùy chọn của Dynamic Core, dùng API của đúng người dùng để đọc hồ sơ và đề xuất thao tác.
Đã tích hợp và kiểm thử local bằng Gemini/Telegram; nghiệm thu nhiều trợ lý hoạt động đồng thời còn trong kế hoạch.
Kế hoạch và trạng thái ở [assistant-platform](../docs/assistant-platform/00_OVERVIEW.md).

## Danh tính và cấu hình

Mỗi tài khoản được xác định bằng issuer và subject do JWT đã kiểm chứng cung cấp.
API công khai không nhận ownerId để chọn chủ; role ADMIN không cho phép đọc secret hoặc hội thoại của người khác.
Liên kết Core dùng client riêng với authorization code, PKCE và state một lần; refresh token không lấy từ phiên frontend.
Mỗi chủ có bot Telegram, private chat, gateway, credential, workspace và volume riêng; tối đa ba runtime được provision từ công cụ host.
Model chỉ được dùng công cụ DX-OS trong allowlist, không có shell, browser, filesystem hoặc HTTP tùy ý.

SQLite riêng lưu cấu hình, provider profile, liên kết, proposal, execution và notification ledger.
Secret/snapshot mã hóa AEAD gắn owner/profile; master key ở secret file riêng.
Không kết nối DB nghiệp vụ hoặc Keycloak Admin trực tiếp.
Thông tin runtime của người khác không được lấy làm căn cứ định tuyến; chủ được gắn vào token runtime từ server.

## API công khai

Các endpoint nằm dưới `/assistant-api/v1`, dùng envelope success/message/data/errorCode phù hợp Core và HTTP status thực tế.
Schema route sinh OpenAPI; bảng này không thay bản OpenAPI sinh từ code.
Các thao tác PUT/PATCH có schema chặt, không nhận trường thực thi hoặc quyền tự khai.

| Endpoint | Hành vi |
|---|---|
| `GET /me` | Trạng thái cấu hình của chính chủ; chỉ trả boolean/masked label của secret |
| `GET, POST /me/providers` | Liệt kê hoặc tạo profile; secret chỉ được ghi, không trả lại |
| `PATCH, DELETE /me/providers/{id}` | Đổi hoặc xóa profile của chủ; không tự chọn vendor khác |
| `POST /me/providers/{id}/probe` | Thử capability bằng dữ liệu giả sau thao tác rõ của người dùng, có thể tiêu quota |
| `POST /me/core/link`, `GET /oauth/callback` | Bắt đầu/hoàn tất liên kết SSO; nonce và PKCE dùng một lần |
| `PUT /me/telegram` | Xác minh bot token và bot ID duy nhất; tạo pairing private chat |
| `DELETE /me/telegram` | Dừng runtime rồi gỡ bot/private chat và tùy chọn thông báo của chính chủ, giải phóng binding bot |
| `POST /me/telegram/pairing` | Nhập mã native do bot gửi, xác minh rồi liên kết private chat; không trả token bot |
| `POST /me/runtime/start`, `/me/runtime/stop` | Điều khiển runtime riêng trong pool do operator chuẩn bị, không tạo container hoặc chọn đường dẫn host |
| `GET, PUT /me/notifications` | Trạng thái/tùy chọn polling, nhắc hạn, digest của chính chủ |
| `GET /me/proposals`, `/me/proposals/{id}` | Danh sách/chi tiết có owner check và giới hạn trang |
| `POST /me/proposals/{id}/confirm`, `/cancel` | Xác nhận/hủy qua phiên web đã xác thực, kiểm origin và replay |
| `GET /admin/capacity` | ADMIN xem khả dụng/slot/tài nguyên tổng hợp; không key/chat/transcript |

Không xuất runtime token hoặc khóa giải mã cho frontend.
Không đưa key/token vào URL, thông báo lỗi, trace, metric hoặc OpenAPI example.

## Công cụ và xác nhận

Bridge riêng cho runtime chỉ cung cấp danh mục cố định: metadata, inbox, list/filter, record, events, stats, handlers và tạo proposal.
Nghiệp vụ ghi gồm assign/unassign và action của các họ hiện có; không CRUD, role grant, reset mật khẩu, SQL, apply/rollback hoặc retry outbox.
Công cụ ADMIN chỉ đọc API vận hành và giải thích plan/diagram do Core tạo từ YAML người dùng cung cấp.

Proposal được server tạo từ metadata và bản ghi Core, không nhận summary hay bằng chứng cho phép từ model.
Snapshot gồm owner, model, entity, recordId, operation, action/assignee/note, version, modelVersion, tác động, kênh nhận, hạn và trạng thái.
Đề xuất thiếu version/modelVersion không được chuyển thành lệnh ghi.
Nếu đổi target, người nhận, note hoặc operation thì tạo proposal mới và xác nhận lại.
Tác động membership hoặc chưa xác định được tác động phải chuyển xác nhận trên web.

Luồng trạng thái: pending → executing → succeeded/failed/uncertain; pending cũng có thể thành cancelled/expired.
Việc claim pending phải nguyên tử, có owner check và kiểm hạn; không giữ SQLite transaction trong lúc gọi mạng.
Sau crash, execution đang chạy chuyển uncertain, không tự gửi lại Core.
Phản hồi timeout hoặc lỗi mạng sau khi gửi lệnh không được coi là thất bại chắc chắn.
Chỉ thông báo đã thực hiện khi có kết quả API; completion trong outbox vẫn phải mô tả đang chờ nếu chưa có bằng chứng hoàn tất.

Model không có route/token xác nhận hoặc thực thi.
Xác nhận Telegram qua callback native đã kiểm user/chat/nonce và proposal tương ứng, không suy từ câu “đồng ý” trong hội thoại.
Runtime tool token không đủ để xác nhận; bridge phân biệt vai trò đọc/tạo đề xuất với đường xác nhận tin cậy.
Từ chối hoặc hết hạn xác nhận native sẽ đóng proposal qua đường hủy có capability và kiểm đúng private chat; không để model tự xác nhận hoặc hủy proposal của chủ khác.
Core kiểm quyền và điều kiện phiên bản lần cuối trong transaction dưới khóa mô hình/bản ghi.

## Vận hành và giới hạn

Đề xuất ngân sách ban đầu: timeout đọc Core 10 giây, proposal 120 giây, quét 60 giây/lần với trang 100 dòng và tối đa 20 trang/20 giây.
Quét chưa đủ không được đánh dấu đã đồng bộ hoặc suy ra hồ sơ đã biến mất.
Không retry lệnh ghi; đọc idempotent được thử lại tối đa một lần trong deadline chung.
Model probe/turn có timeout, số tool call và quota riêng; không fallback vendor âm thầm.
Ngân sách RAM/CPU phải đo lại bằng image thật trước nghiệm thu ba người; số PoC không thay số bản tích hợp.

Runtime cần bật khi nhận Telegram/nhắc hạn; tắt runtime không có cơ chế tự thức dậy bằng tin nhắn.
Nhắc hạn và thẻ cố định không gọi LLM, không thay Core scheduler/webhook/outbox.
Off giữ state và volume; purge là thao tác riêng có xác nhận.
Khởi động Core mặc định không cần API key, Node cài trên host hoặc runtime trợ lý.
Lưu/khôi phục không gian hội thoại theo mô hình nằm trong backlog; dữ liệu và quyền của từng mô hình vẫn phải được kiểm ở mỗi lượt.

## Dependency

OpenClaw 2026.11.1 được dùng nguyên bản, khóa trong `runtime/package-lock.json`; bước lifecycle chính thức của package chạy khi build image để runtime không cần ghi vào thư mục cài đặt.
Các bản vá dependency trong lockfile xử lý cảnh báo HIGH đã phát hiện, không hạ phiên bản OpenClaw để né cảnh báo.
Lần kiểm `npm audit` ngày 07/11/2026: control không có cảnh báo; runtime còn 33 cảnh báo Moderate, không có High/Critical.
Đây là kết quả của npm tại thời điểm kiểm, không thay việc quét định kỳ hoặc quét image trong CI.
Hai gói dữ liệu SPDX được duyệt ngoại lệ riêng; xem `DEPENDENCIES.txt`, `NOTICE` và SBOM.
