# Trợ lý nghiệp vụ động

Dịch vụ độc lập kết nối Dynamic Core qua API, dùng model self-host để đề xuất một giá trị trường từ hồ sơ và nguồn tri thức được cấu hình.
Giao diện hiển thị tên trường cùng giá trị đề xuất, lưu ý riêng theo trường và nguồn tham khảo thu gọn.
Người dùng xem căn cứ, sửa hoặc xác nhận trên giao diện hồ sơ; Core kiểm quyền, quy tắc và phiên bản trước khi ghi.
Inference do người vận hành cấu hình và chạy tại máy chủ của bản cài.

## Cấu hình và sử dụng

1. Thêm `assistant` vào `COMPOSE_PROFILES` trong `deploy/.env`, giữ các profile đang dùng, rồi chạy `dxos start` theo [README](../README.md#chạy-thử).
   Mở **Trợ lý nghiệp vụ** và liên kết tài khoản DX-OS; tắt mặc định thì Core và web không cần dịch vụ này.
2. Người vận hành cấu hình inference và catalog nguồn bằng các biến đầy đủ trong [`deploy/.env.example`](../deploy/.env.example).
3. Người quản trị mô hình khai báo nhiệm vụ trong `ai.tasks` của thực thể bằng YAML hoặc tab trợ lý trong Console, rồi áp dụng mô hình.
4. Người dùng mở hồ sơ đã lưu, chọn nhiệm vụ, tạo đề xuất và đọc nguồn trước khi xác nhận.
5. Nếu sửa giá trị, chọn xem lại để tạo snapshot mới, sau đó xác nhận lưu trong một thao tác riêng.

Hai template `onestop` và `support_desk` minh họa cùng khả năng phân loại trên trường khác nhau.
Catalog phải có các mã nguồn mà nhiệm vụ tham chiếu; thiếu nguồn thì nhiệm vụ báo chưa cấu hình, không tự lấy tài liệu từ Internet.
Dữ liệu demo nằm ngoài Git theo quyết định chủ dự án; bản cài phải tự chuẩn bị nguồn hợp lệ trước khi dùng AI.

`ai.tasks` khai báo nhãn, `input_fields`, `output_field`, `instruction`, `sources` và `max_length`; xem [MODEL](../docs/MODEL.md#nhiệm-vụ-trợ-lý-nghiệp-vụ).
Đầu ra hiện là text hoặc choice đơn trị; trường dùng để cấp membership và các trường hệ thống không được chọn.
Khai báo nhiệm vụ không cấp thêm quyền đọc/ghi hoặc thay cơ chế phân quyền của Core.

## Inference và nguồn tri thức

Model mặc định là **Apertus-8B-Instruct-2509**, self-host; bản local dùng trọng số GGUF lượng tử hóa Q4.
Giấy phép upstream của model là Apache-2.0; mã llama.cpp `b12387` là MIT, các thư viện hệ thống và mã vendor giữ giấy phép riêng theo [`DEPENDENCIES.txt`](../DEPENDENCIES.txt), ghi chú [9]–[12].
Người vận hành cấu hình endpoint phục vụ model này bằng các biến inference trong [`deploy/.env.example`](../deploy/.env.example); việc chọn model không tự tải hoặc đóng gói trọng số.

Người dùng không nhập endpoint, API key hoặc model trên giao diện.
Endpoint do người vận hành cố định trong bản cài; transport chặn redirect, giới hạn input/output, timeout và một lượt suy luận đồng thời.
Timeout inference tối đa 180 giây, cổng vào chờ 240 giây để còn thời gian đọc Core và trả lỗi; giao diện chờ tối đa 270 giây cho một lượt tạo đề xuất.
Model không nhận JWT/refresh token Core, quyền xác nhận, công cụ shell, filesystem hoặc quyền gọi HTTP tùy ý.
Trường đơn vị có thể làm đầu vào chỉ đọc để phát hiện mâu thuẫn theo nguồn; vẫn không được chọn làm đầu ra.
Một lượt chỉ gửi các trường được nhiệm vụ khai báo cùng các nguồn liên quan, không gửi lịch sử hội thoại hoặc toàn bộ metadata hệ thống.

Dùng endpoint chat có structured output nguyên bản, hoặc native completion với framing lấy từ template upstream của đúng model.
`ASSISTANT_PROMPT_FRAMING_FILE` là tệp JSON của người vận hành, gồm `bos`, `systemPrefix`, `systemSuffix`, `userPrefix`, `userSuffix`, `assistantPrefix` và `stop`.
Framing chỉ chứa chuỗi, không chạy mã hoặc tải template từ URL; không tự thay template model khi gặp lỗi.
Đổi model, trọng số hoặc framing phải cập nhật `ASSISTANT_MODEL_REVISION`, xử lý xong lượt đang chạy rồi khởi động lại provider để vô hiệu đề xuất cũ.
Không tải trọng số trong request hoặc lúc Core khởi động; tắt inference không ngăn Core hoặc form nghiệp vụ hoạt động.

Catalog nguồn là một mảng JSON trong tệp ngoài Git do `ASSISTANT_SOURCES_FILE` chỉ đến.
Mỗi mục có `id`, `title`, `revision`, `text` và `license`; mã nguồn bắt đầu bằng chữ thường, tối đa 40 ký tự gồm chữ thường, chữ số và gạch dưới, revision không rỗng và văn bản tối đa 12.000 ký tự.
Catalog nhận dữ liệu CC0-1.0, CC-BY-4.0 hoặc ODbL-1.0 theo cam kết nguồn mở; người vận hành phải giữ provenance và thông báo bản quyền của nguồn.
Nguồn được nạp bất biến khi provider khởi động; đổi nội dung phải có revision mới và khởi động lại provider.
Không đặt URL hoặc secret vào YAML nhiệm vụ; file nguồn, framing và key có thể đặt trong `deploy/data/assistant/private`, tương ứng `/run/secrets` trong container.
Key inference phải là tệp thông thường có mode 0600; nguồn và framing phải được người vận hành bảo vệ khỏi sửa trái phép.

Đầu ra model có cấu trúc gồm `status`, `value`, `sources` và `checks`.
Giá trị hiện tại của các trường đối chiếu được giữ ở server để so sánh, không đưa vào dữ liệu model nhằm giảm sao chép lựa chọn sai.
Các cặp số nguyên an toàn trong đầu vào có thêm kết quả so sánh; số thập phân hoặc ngoài miền số nguyên an toàn không được suy diễn bằng cơ chế này.
`checks` ánh xạ mỗi trường đầu vào choice/unit đơn trị có tập lựa chọn, trừ trường đầu ra, tới giá trị theo nguồn hoặc null khi chưa đủ căn cứ.
Server kiểm tập lựa chọn rồi so với hồ sơ để tạo `snapshot.warnings` dạng `{field, expected, actual}`; đây là lưu ý để người dùng kiểm tra, không phải lệnh ghi.
Giá trị đề xuất và mã nguồn được kiểm ở server trước khi tạo proposal.
Model trả thiếu căn cứ thì không tạo proposal; JSON hỏng, choice ngoài danh mục hoặc nguồn bịa đều bị từ chối.
Căn cứ hiển thị lấy từ văn bản đã cung cấp, không dùng lời tự đánh giá độ tin cậy của model làm bằng chứng.
Nguồn có thật không chứng minh đề xuất đúng: người dùng vẫn phải kiểm nội dung trước khi xác nhận.
Self-host và trọng số Apache-2.0 chưa đủ chứng minh model đạt OSAID; model, dữ liệu và runtime phải được kiểm riêng trước phát hành.

## Danh tính, đề xuất và API

Danh tính lấy từ JWT đã kiểm issuer/audience; owner không nhận từ body, query hoặc model.
Liên kết Core dùng PKCE, state một lần và refresh riêng, không lấy refresh token từ frontend.
SQLite lưu snapshot/receipt và credential mã hóa AEAD theo chủ; master key được giữ riêng.
Proposal giữ target/task, giá trị, căn cứ, thời hạn, phiên bản hồ sơ/mô hình và revision nguồn/model.
Sửa nội dung tạo proposal thay thế và vô hiệu bản cũ nguyên tử; confirm không nhận patch mới từ browser.

| Endpoint dưới `/assistant-api/v1` | Chức năng |
|---|---|
| `GET /features` | Trạng thái bật dịch vụ, không gọi model |
| `GET /openapi.json` | OpenAPI sinh từ schema route, cần đăng nhập |
| `GET /me` | Trạng thái liên kết và cấu hình model, không gọi model |
| `POST /me/core/link`, `GET /oauth/callback` | Liên kết Core bằng SSO/PKCE |
| `POST /me/proposals` | Nhận model/entity/id/task, server tự đọc hồ sơ và tạo đề xuất |
| `GET /me/proposals`, `GET /me/proposals/{id}` | Lịch sử/chi tiết của chủ còn quyền đọc hồ sơ |
| `POST /me/proposals/{id}/revise` | Sửa đúng giá trị của trường đã cấu hình, tạo snapshot mới |
| `POST /me/proposals/{id}/confirm`, `/cancel` | Xác nhận hoặc hủy ngoài LLM, kiểm Origin và danh tính |

Luồng trạng thái là `pending` → `executing` → `succeeded`, `failed` hoặc `uncertain`, với TTL 120 giây tính từ lúc lưu proposal.
Đề xuất được xóa sau 30 ngày kể từ lúc tạo, trừ đề xuất đang thực hiện.
Atomic claim chỉ cho một lần ghi; Core kiểm lại version/modelVersion và quyền trong giao dịch.
Sau timeout hoặc dừng tiến trình khi ghi, kết quả chưa rõ giữ `uncertain`, không tự gửi lại.
Xác nhận giá trị trùng hồ sơ trả 409 `ERR_PROPOSAL_NO_CHANGE`, không gọi PATCH và không tạo lịch sử sửa giả.
Receipt thành công có `changed: true` khi Core trả phiên bản hồ sơ tăng; phiên bản không tăng giữ kết quả `uncertain`.
Receipt chỉ nói đã lưu khi Core trả thành công; người dùng đối chiếu lịch sử hồ sơ nếu kết quả chưa rõ.

## State, sao lưu và giới hạn

Trước nâng cấp, chụp snapshot nhất quán của SQLite và state theo [quy trình backup](../docs/OPERATIONS.md#sao-lưu-và-khôi-phục).
Master key ở `deploy/data/assistant/private/master.key` phải được cất riêng ngoài kho restic; thiếu hoặc sai khóa thì không giải mã được credential đã sao lưu.
Database có sẵn nhưng thiếu hoặc sai master key phải khôi phục đúng key, không tạo khóa thay thế.
Migration giữ lịch sử, liên kết SSO và owner; kết quả ghi chưa rõ không được thực thi lại.
Khôi phục dùng snapshot và image tương thích, không tự xóa dữ liệu hoặc đảo ngược thay đổi đã ghi Core.

Một provider phục vụ nhiều chủ với state riêng và một inference slot dùng chung; không triển khai nhiều replica trên cùng SQLite.
Hỏi đáp mở, embedding/reranker và tác tử tự tạo đề xuất theo sự kiện chưa thuộc bản này.
Tiêu chí compile, kiểm mô phỏng và nghiệm thu Core/model thật được phân biệt trong [test plan](../docs/ai-assistant/03_TEST_PLAN.md).
