# Trợ lý hồ sơ

Trợ lý cá nhân của DX-OS để đọc hồ sơ, nhận thông báo và đề xuất giao việc hoặc chuyển trạng thái qua Telegram.
Mỗi người dùng có cấu hình API và bot riêng; thao tác ghi cần người dùng xác nhận và được Core kiểm quyền.

## Bật và sử dụng

Khởi động DX-OS theo [`README.md`](../README.md#chạy-thử); trang **Trợ lý cá nhân** có sẵn khi stack đã chạy.
Mỗi người tự cấu hình API và bot; khi chưa kích hoạt, gateway OpenClaw không chạy và không gọi model.

1. Mở mục **Trợ lý cá nhân** trong ứng dụng, hoặc đường dẫn `/assistant`, rồi chọn **Liên kết DX-OS**.
2. Chọn Gemini, OpenAI hoặc Anthropic, nhập đúng tên model có hỗ trợ gọi công cụ và API key của bạn, rồi chọn **Lưu kết nối**.
3. Chọn **Kiểm kết nối** để thử model bằng dữ liệu giả; thao tác này có thể tiêu quota, còn lưu cấu hình chưa gọi model.
4. Nhập token bot Telegram riêng rồi chọn **Lưu bot**.
5. Chọn **Bật bot để lấy mã liên kết**, chờ bot khởi động rồi gửi `/start` trong cuộc trò chuyện riêng với bot.
6. Nhập mã bot gửi về trên giao diện rồi chọn **Xác nhận liên kết**.
7. Chọn **Bật trợ lý**; hỏi về hộp việc hoặc tìm hồ sơ bằng tiêu đề, xem đề xuất trước khi xác nhận thao tác.

Không dùng bot đang có webhook hoặc được một triển khai khác sử dụng; mỗi bot chỉ liên kết với một tài khoản.
Cấu hình lại bot yêu cầu mã ghép nối mới, kể cả khi dùng lại cùng bot.
Cấu hình thông báo, nhắc hạn và giờ tổng kết ở tab **Thông báo**; xem đề xuất và kết quả ở tab **Hoạt động**.
Chọn **Dừng trợ lý** để ngừng gateway của mình và giữ cấu hình, liên kết cùng dữ liệu.
`dxos stop` dừng toàn bộ stack, `dxos start` khởi động lại và phục hồi những tài khoản đã bật trợ lý.
Sau khôi phục, nếu phiên SSO không còn hiệu lực, chọn **Liên kết lại DX-OS** trên giao diện.

## Danh tính và dữ liệu

Mỗi tài khoản được xác định bằng issuer và subject do JWT đã kiểm chứng cung cấp.
API lấy chủ tài khoản từ JWT, không từ `ownerId` do client gửi; vai ADMIN không cấp quyền đọc bí mật hoặc hội thoại của người khác.
Liên kết Core dùng client riêng với authorization code, PKCE và state một lần; refresh token không lấy từ phiên frontend.
Mỗi tài khoản có bot Telegram, cuộc trò chuyện riêng, gateway, thông tin xác thực và vùng dữ liệu riêng trong pool được chuẩn bị tự động cùng stack.
Model chỉ được dùng danh sách công cụ DX-OS đã cho phép, không có shell, trình duyệt, truy cập tệp hoặc HTTP tùy ý.

SQLite riêng lưu cấu hình API, liên kết tài khoản, đề xuất, kết quả thực thi và các lượt gửi thông báo.
Các trường bí mật và snapshot được mã hóa AEAD gắn với chủ và cấu hình tương ứng; master key lưu trong tệp riêng.
Nội dung cần để trả lời được gửi tới nhà cung cấp API mô hình; tin nhắn và thẻ xác nhận đi qua Telegram.
Lịch sử hội thoại OpenClaw nằm trong vùng dữ liệu riêng của runtime; mã hóa AEAD của control không bao phủ toàn bộ vùng này.
Không kết nối DB nghiệp vụ hoặc Keycloak Admin trực tiếp.
Server gắn chủ tài khoản vào token runtime; client không được chọn runtime của người khác.

## API công khai

API quản lý tài khoản và đề xuất nằm dưới `/assistant-api/v1`; phản hồi dùng `success`, `message`, `data`, `errorCode` và mã HTTP tương ứng.
Schema route sinh OpenAPI; bảng này không thay bản OpenAPI sinh từ code.
Các thao tác ghi chỉ dùng trường có trong schema; trường ngoài danh sách bị bỏ qua.
Danh tính luôn lấy từ JWT đã xác minh, không lấy từ trường ownerId do client gửi.
`PATCH` cấu hình API cần đủ `provider`, `model`, `apiKey`, chưa hỗ trợ sửa từng trường; giao diện hiện dùng tạo, chọn và xóa cấu hình.

| Endpoint | Hành vi |
|---|---|
| `GET /features` | Trạng thái bật dịch vụ, trả trường `enabled`, không cần đăng nhập |
| `GET /openapi.json` | Tài liệu OpenAPI JSON, cần đăng nhập |
| `GET /me` | Trạng thái của chính chủ; chỉ báo đã cấu hình hoặc hiển thị nhãn đã che bí mật |
| `GET, POST /me/providers` | Liệt kê hoặc tạo cấu hình API; không trả lại key |
| `PATCH, DELETE /me/providers/{id}` | Đổi hoặc xóa cấu hình API của chủ |
| `POST /me/providers/{id}/select` | Chọn cấu hình API đã lưu; không tự đổi nhà cung cấp |
| `POST /me/providers/{id}/probe` | Thử khả năng gọi công cụ bằng dữ liệu giả khi người dùng yêu cầu, có thể tiêu quota |
| `POST /me/core/link`, `GET /oauth/callback` | Bắt đầu/hoàn tất liên kết SSO; nonce và PKCE dùng một lần |
| `GET /me/telegram` | Trạng thái cấu hình, ghép nối và tên bot của chủ; không trả token |
| `PUT /me/telegram` | Xác minh token và ID bot duy nhất; tạo vùng ghép nối mới cho mỗi lần cấu hình |
| `DELETE /me/telegram` | Dừng runtime, gỡ bot và cuộc trò chuyện đã liên kết cùng tùy chọn thông báo của chủ |
| `POST /me/telegram/pairing` | Xác minh mã ghép nối do bot gửi rồi liên kết cuộc trò chuyện riêng; không trả token bot |
| `GET /me/runtime` | Trạng thái runtime và khả năng cấp slot cho chủ |
| `POST /me/runtime/start`, `/me/runtime/stop` | Bật hoặc dừng gateway riêng của chủ trong runtime đã cấp, không tạo container từ API |
| `GET, PUT /me/notifications` | Trạng thái và tùy chọn thông báo, nhắc hạn, tổng kết của chủ |
| `GET /me/proposals`, `/me/proposals/{id}` | Danh sách hoặc chi tiết đề xuất của chủ, có phân trang |
| `POST /me/proposals/{id}/confirm`, `/cancel` | Xác nhận/hủy qua phiên web đã xác thực, kiểm origin và replay |
| `GET /admin/capacity` | ADMIN xem số runtime còn trống và tài nguyên tổng hợp; không xem key hoặc hội thoại |

Không xuất runtime token hoặc khóa giải mã cho frontend.
Không xuất key/token trong URL của giao diện, thông báo lỗi, trace, metric hoặc ví dụ OpenAPI.

## Công cụ và xác nhận

Bridge riêng cho runtime cung cấp công cụ đọc metadata, hộp việc, danh sách và bộ lọc, chi tiết, lịch sử, thống kê, người nhận hợp lệ và tạo đề xuất.
Thao tác ghi gồm giao việc, trả việc về nhóm và hành động quy trình do mô hình khai báo; không tạo, sửa hoặc xóa hồ sơ, cấp vai, đổi mật khẩu, SQL, áp dụng hoặc rollback mô hình, thử lại outbox.
Công cụ ADMIN chỉ đọc dữ liệu vận hành và giải thích kế hoạch, sơ đồ do Core tạo từ YAML người dùng cung cấp.

Đề xuất do server tạo từ metadata và bản ghi Core, không nhận nội dung tóm tắt hoặc bằng chứng quyền từ model.
Đề xuất lưu chủ tài khoản, snapshot hồ sơ, thời hạn và trạng thái thực thi.
Snapshot chứa mô hình, thực thể, bản ghi, thao tác, người nhận hoặc hành động, ghi chú, phiên bản và tác động để người dùng xác nhận.
Đề xuất thiếu version/modelVersion không được chuyển thành lệnh ghi.
Nếu đổi target, người nhận, note hoặc operation thì tạo proposal mới và xác nhận lại.
Tác động membership hoặc chưa xác định được tác động phải chuyển xác nhận trên web.

Luồng trạng thái: `pending` → `executing` → `succeeded`, `failed` hoặc `uncertain`; hủy đề xuất chuyển thành `cancelled`.
Đề xuất quá hạn không được xác nhận, kể cả khi trạng thái lưu vẫn là `pending`.
Chuyển từ `pending` sang `executing` phải nguyên tử, kiểm đúng chủ và thời hạn; không giữ giao dịch SQLite trong lúc gọi mạng.
Sau khi tiến trình bị dừng bất ngờ, lượt đang thực thi chuyển thành `uncertain`, không tự gửi lại Core.
Timeout hoặc lỗi mạng sau khi gửi lệnh chưa chứng minh thao tác thất bại.
Chỉ thông báo đã thực hiện khi có kết quả API; completion trong outbox vẫn phải mô tả đang chờ nếu chưa có bằng chứng hoàn tất.

Model không có route/token xác nhận hoặc thực thi.
Xác nhận Telegram qua callback native đã kiểm user/chat/nonce và proposal tương ứng, không suy từ câu “đồng ý” trong hội thoại.
Runtime tool token không đủ để xác nhận; bridge phân biệt vai trò đọc/tạo đề xuất với đường xác nhận tin cậy.
Callback hủy xác nhận dùng token riêng và kiểm đúng cuộc trò chuyện; model không được xác nhận hoặc hủy đề xuất của chủ khác.
Core kiểm quyền, điều kiện `when` và phiên bản trong cùng giao dịch dưới khóa mô hình và bản ghi.

## Vận hành và giới hạn

Control và ba supervisor được triển khai trong Compose chung; mặc định cấp hai slot cho người dùng, tối đa ba.
Các giá trị triển khai và giới hạn CPU/RAM nằm ở [`deploy/.env.example`](../deploy/.env.example); bộ cài và make_env tự nạp giá trị mặc định, API key và bot token được nhập riêng trên giao diện.

Giới hạn hiện tại: timeout đọc Core 10 giây, proposal 120 giây, quét 60 giây/lần với trang 100 dòng và tối đa 20 trang/20 giây.
Quét chưa đủ không được đánh dấu đã đồng bộ hoặc suy ra hồ sơ đã biến mất.
Client Core không tự gửi lại yêu cầu đọc hoặc ghi khi lỗi.
OpenClaw được cấu hình thời hạn 60 giây cho kiểm kết nối model, 180 giây cho hội thoại; mỗi runtime xử lý một lượt tại một thời điểm.
Quota do nhà cung cấp API quyết định.
Lỗi không làm hệ thống tự đổi nhà cung cấp.
Đã kiểm hai trợ lý đồng thời; số đo và điều kiện tại [kế hoạch kiểm thử](../docs/assistant-platform/03_TEST_PLAN.md#9-đo-hai-trợ-lý).
Thử tải ba trợ lý và nghiệm thu provider thứ hai hoãn theo [BACKLOG](../docs/dynamic-core/BACKLOG.md).

Runtime cần bật khi nhận Telegram/nhắc hạn; tắt runtime không có cơ chế tự thức dậy bằng tin nhắn.
Nhắc hạn và thẻ cố định không gọi LLM, không thay bộ lập lịch, webhook hoặc outbox của Core.
Hội thoại chưa tách lịch sử theo mô hình; mỗi công cụ vẫn xác định mô hình đích và kiểm quyền lại qua Core.

## Thành phần và nhà cung cấp API

OpenClaw 2026.11.1 được dùng nguyên bản, khóa trong `runtime/package-lock.json`; các bước cài đặt của package chạy khi build image.
Giấy phép và các gói đi kèm được liệt kê ở [`DEPENDENCIES.txt`](../DEPENDENCIES.txt), [`NOTICE`](../NOTICE) và [`sbom/`](../sbom/README.md).
Kết quả quét lỗ hổng được theo dõi tại workflow `Dependencies` của PR và các lượt chạy định kỳ.
Các model API là dịch vụ do người dùng cấu hình; dự án không đóng gói trọng số hoặc coi chúng là mô hình nguồn mở.

Endpoint OpenAI-compatible tạm khóa cả lưu, chọn, probe và khởi động runtime, kể cả profile cũ.
Gemini, OpenAI và Anthropic dùng endpoint cố định; chỉ Gemini đã có bằng chứng hội thoại thật trong đợt thử hiện tại.
Mở lại endpoint tùy chỉnh cần kiểm transport cho redirect, DNS đổi và mạng private, không chỉ kiểm allowlist.

## Kết nối và lỗi

Thao tác ghi cần `Origin` đúng bằng `GATEWAY_PUBLIC_URL`; khi dùng IP, tunnel hoặc tên miền khác phải cấu hình lại địa chỉ công khai và SSO trước.
Chưa liên kết Core trả 409 ERR_CORE_LINK; phiên xác thực không hợp lệ vẫn trả 401.
Đề xuất cũ thiếu phiên bản trả 409 ERR_PROPOSAL_VERSION_REQUIRED; cần tạo lại đề xuất trước khi xác nhận.
JSON sai trả 400, nội dung gửi quá 32 KiB trả 413; lỗi nội bộ trả 500 và không đưa nội dung yêu cầu vào thông báo.

Sao lưu và khôi phục ở [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md#trợ-lý-cá-nhân).
Ma trận kiểm thử và các ca chưa đạt ở [kế hoạch kiểm thử](../docs/assistant-platform/03_TEST_PLAN.md); các giới hạn cần làm tiếp ở [`BACKLOG`](../docs/dynamic-core/BACKLOG.md).
