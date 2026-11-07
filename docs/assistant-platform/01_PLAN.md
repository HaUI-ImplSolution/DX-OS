# Kế hoạch thực hiện: Trợ lý hồ sơ đa người dùng

Thứ tự: Spec → Swagger/OpenAPI → Code → bằng chứng kiểm thử.
Đây là kế hoạch mới trên nhánh cha hiện tại, không phải ghi nhận tính năng đã triển khai.

## Quy tắc triển khai

- Áp dụng yêu cầu đã chốt của người dùng trước, tiếp theo rule tech lead và convention thực tế của nhánh cha; không chép cấu trúc sáu module hoặc lệnh môi trường máy khác từ memory cũ.
- Đối chiếu `CONTRIBUTING.md`, controller/service/metadata hiện tại và check CI trước mỗi phần; không định dạng lại hoặc refactor file không thuộc phạm vi.
- Java dùng constructor injection, JDBC và transaction hiện có, DTO record khi cần; không đưa JPA, module Maven hoặc tầng framework mới vào Core.
- Giữ `ApiResponse`, HTTP status và `data.reason`; thông báo backend mới bằng tiếng Anh kèm mã lỗi, thêm mẫu dịch vào `frontend/src/shell/backendMessages.ts` khi cần.
- UI dùng shell/auth/react-admin/MUI và cách ánh xạ lỗi hiện có; không dựng lại layout hay hệ thống định danh.
- Comment ngắn bằng tiếng Anh, chỉ nói hành vi hiện tại, không nhãn WP/phase; mã nguồn có header SPDX Apache-2.0, Markdown mỗi câu một dòng.
- Chỉ tạo helper, directory, interface và dependency thực sự dùng; ưu tiên API OpenClaw hoặc thư viện chuẩn, không fork/vá/vendor mã bên thứ ba vào repository.
- Trước khi sửa manifest, báo dependency đề xuất và kết quả đọc LICENSE của đúng bản upstream cùng artifact đã phát hành, kể cả build/test và gói bundled; không suy từ giấy phép của gói ngoài cùng.
- SBOM phát hành chỉ chứa runtime theo `sbom/README.md`; dependency build/test vẫn phải kiểm và ghi giấy phép theo policy, không bỏ qua vì không xuất hiện trong SBOM runtime.
- Mỗi mục backlog được chọn phải có quyết định cho toàn bộ phần còn mở; làm xong mới bỏ mục, phần chấp nhận hoặc hoãn giữ đúng trạng thái, không xóa để đánh dấu hoàn thành giả.
- Không tự thêm file test ngoài phạm vi được duyệt; các ca ở test plan là tiêu chí kiểm, không phải yêu cầu tạo từng file hoặc một framework test mới.
- Kiểm local, ưu tiên script hiện có; không build lại image cho mỗi sửa Java hoặc test client HTTP, không dùng stack/dữ liệu đang demo cho failure injection.
- Không tự stage/commit/push, không đổi danh tính Git hoặc chỉnh `.gitignore`; giữ `report/`, tài liệu cá nhân và diff PoC ngoài phần công việc này.

## WPAS-01: Chốt hợp đồng và kiểm nền

**Mục tiêu:** có đường tích hợp phù hợp bản Core mới và không mang hạn chế của PoC sang sản phẩm.

- Đối chiếu bốn module, `ArchitectureTest`, `DataService`, `ProcessRules`, `ModelUseFilter`, registry, SSO và bộ cài.
- Khóa bản OpenClaw/Node/provider API tương thích; dùng bản PoC làm đối chiếu SDK, không mặc định bản đó đã phù hợp mọi provider hoặc chính sách release.
- Kiểm dependency trực tiếp, bắc cầu, license notices, image và khả năng build từ source theo policy nhánh cha; không chỉ kiểm giấy phép MIT của gói OpenClaw ngoài cùng.
- Báo kết quả giấy phép trước khi thêm OpenClaw/Fastify/jose hoặc công cụ build/test vào manifest; các tên trong kế hoạch là lựa chọn đề xuất, chưa phải dependency đã được duyệt.
- Xác định phần nào kiểm bằng script/test có sẵn và phần nào cần file mới; ngoại lệ WireMock của tech lead chỉ áp dụng client HTTP KeycloakAdminClient/WebhookSender, không cấp quyền tự mở thêm cả bộ test.
- Chốt danh mục thao tác, profile trả lời, schema proposal, ranh giới tài khoản, API mới và ngân sách tài nguyên.
- Lập bảng tái sử dụng PoC: giữ logic đã chứng minh, thay cấu hình một chủ, state tệp toàn cục, server HTTP tự viết và đường dẫn local bằng thành phần nhiều người dùng.

**Phạm vi:** spec, hợp đồng và test thiết kế; chưa sửa kernel hoặc triển khai provider.
**Kết quả:** qua G0, đủ điều kiện mới bắt đầu các WP sau; nếu SDK/giấy phép không đạt thì báo lại lựa chọn thay thế trước khi đổi nền tảng.

Hợp đồng API/danh tính/proposal ở [`assistant/README.md`](../../assistant/README.md).

| Thành phần PoC | Quyết định tích hợp |
|---|---|
| Core client và `data.reason` | Giữ cách gọi API theo chủ; mở thêm handlers/stats/assign, không sao chép token một tài khoản |
| Approval hook và callback native | Giữ cơ chế con người xác nhận; tách capability thực thi khỏi model tool token |
| Renderer thẻ có label | Tái sử dụng hành vi; trường lấy từ metadata, không escape Markdown cho đường plain text |
| Notification baseline/ledger | Giữ nguyên tắc chống bão tin; chuyển state sang SQLite có owner, không dùng state toàn cục |
| Runtime generator | Thay bằng runtime riêng được provision, không dùng port/path cố định một người |
| HTTP server/state file PoC | Thay bằng Fastify/schema và SQLite/migration; không copy server tự viết hoặc secret demo |

Đã kiểm giấy phép upstream và artifact của OpenClaw, Fastify, jose cùng các gói bundled trong lockfile.
Ngày 07/11/2026, chủ dự án duyệt ngoại lệ dữ liệu cho `spdx-exceptions` (CC-BY-3.0) và `spdx-license-ids` (CC0-1.0); ghi riêng trong dependency exceptions và giữ attribution trong NOTICE.
Ngoại lệ không áp dụng cho mã phần mềm hoặc gói khác.
Các kiểm chứng runtime, provider và xác nhận vẫn phải đạt trước nghiệm thu.

## WPAS-02: Tài khoản, state và runtime riêng

**Mục tiêu:** mỗi người có trợ lý đúng danh tính và dữ liệu riêng.

- Tạo `assistant/` là dịch vụ mở rộng TypeScript, build thành JavaScript cho production.
- Dùng Fastify cho route/schema/giới hạn body và OpenAPI, `jose` cho JWT; không tự viết lại HTTP framework, JWT hoặc provider SDK.
- Cấu trúc theo chức năng `accounts`, `providers`, `runtime`, `proposals`, `notifications`, `tools`; route → service → store/client, tách tác vụ có side effect khỏi render.
- SQLite riêng qua API chuẩn của Node, một control process và giao dịch ngắn; không thêm ORM, Redis hoặc DB server cho quy mô này.
- Bảng dự kiến: tài khoản/liên kết, provider profile, proposal/execution, lịch/notification delivery; khóa/index luôn có chủ.
- Mã hóa secret và snapshot nhạy cảm bằng AEAD, gắn owner/profile vào AAD; khóa chủ ở secret file riêng, không ở DB hoặc Git.
- JWT kiểm issuer/audience và chủ từ server; không nhận `ownerId`, vai hoặc chat ID tự khai làm thẩm quyền.
- Client Keycloak riêng dùng code + PKCE, state một lần và refresh token riêng; không sao chép token của phiên web hoặc dùng ADMIN/service account cho nghiệp vụ.
- Xác minh `botId` bằng Telegram, unique binding giữa các tài khoản; pairing private chat một lần và hủy callback cũ khi thay liên kết.
- Provision tối đa ba runtime cách ly bằng công cụ host do người vận hành chạy; control service chỉ gọi API start/stop hạn hẹp của runtime đã được cấp.
- Không mount Docker socket vào control service hoặc gateway, không cho UI/LLM tạo container, chạy shell hoặc chọn đường dẫn host.
- Mỗi runtime chỉ có volume, secret, runtime token và network cần thiết cho chủ; không publish cổng OpenClaw ra Internet.
- Tắt toàn bộ công cụ OpenClaw ngoài danh mục DX-OS đã duyệt, gồm shell, filesystem tùy ý, browser và HTTP tổng quát; chỉ tách container là chưa đủ.
- Dữ liệu và ngân sách của người A không đổi khi B tắt/xóa/đổi key; khóa tài khoản hoặc refresh thất bại thì ngừng công cụ của đúng chủ.

**File chính:** `assistant/src/{accounts,runtime,storage,auth}/`, `assistant/migrations/`, hợp đồng runtime và test.
**Data:** SQLite/volume riêng, không Flyway hoặc quyền DB nghiệp vụ.
**Kết quả:** A/B/C không truy cập được secret, transcript, callback, proposal hoặc công cụ mang danh nhau.

## WPAS-03: Nhiều nhà cung cấp API

**Mục tiêu:** đổi nhà cung cấp mà giữ cùng công cụ, xác nhận và giao diện.

| Profile | Hướng tích hợp |
|---|---|
| Gemini | Adapter Google của OpenClaw; ghim transport/model tương thích, không để catalog tự đổi tham số suy luận |
| OpenAI | Adapter native; chọn Responses/Chat theo model đã kiểm |
| Anthropic | Adapter native Messages và tool calling |
| OpenAI-compatible | Adapter cấu hình của OpenClaw; ví dụ DeepSeek/OpenRouter, chỉ endpoint được ADMIN cho phép và đã kiểm |

- Profile gồm provider, model, secret reference và capability đã xác minh; một profile mặc định mỗi chủ, có thể lưu nhiều profile.
- Không coi mọi model hoặc endpoint tương thích là đã hỗ trợ tool calling/structured output.
- Kiểm kết nối dùng dữ liệu tổng hợp vô hại; nút kiểm gọi model thật phải báo có thể tiêu quota, không gửi hồ sơ thật để thử key.
- Không tự fallback sang nhà cung cấp khác vì thay đổi nơi nhận dữ liệu và chi phí; người dùng chọn rõ trước lượt mới.
- Endpoint mặc định lấy từ catalog; endpoint tùy chỉnh phải được ADMIN cho phép, HTTPS, không loopback/link-local/metadata service hoặc redirect sang origin khác.
- Chỉ gửi key tới đúng provider; giới hạn timeout, lượt gọi, output và số lần thử theo tài khoản; 429 tôn trọng thời gian chờ, không retry vô hạn.
- Key/probe lỗi được dịch theo nguyên nhân thật, không echo URL/body chứa bí mật; không áp một quy tắc tiền tố key cho tất cả provider.
- UI tách trạng thái Telegram/runtime và model, để lỗi/quota LLM không biến thành thông báo sai rằng Core hoặc kênh thông báo đã hỏng.

**File chính:** `assistant/src/providers/`, plugin/config generator, UI provider profile.
**Kết quả:** cùng bộ đề và tool contract chạy qua ít nhất hai nhà cung cấp thật; profile còn thiếu key hoặc chưa kiểm thật phải ghi chưa xác minh, không tích đạt chung.

## WPAS-04: Điều kiện thực thi trung tính của Core

**Mục tiêu:** xác nhận đúng snapshot, không áp thao tác lên hồ sơ hoặc mô hình đã đổi.

| File/lớp trong `dx-engine` | Thay đổi dự kiến |
|---|---|
| `data/DataController.java` | Nhận `version`, `modelVersion` tùy chọn ở action và assign; cập nhật `@Operation` |
| `data/DataService.java` | Overload/điều kiện ghi chung; kiểm version sau khóa dòng trong cùng transaction, trước workflow/assign |
| `data/RecordPreconditions.java` mới hoặc helper tương đương | Parse/kiểm miền giá trị dùng chung, không chứa tên AI/provider/industry |
| `registry/ModelRegistry.java` | Đọc/so phiên bản dưới khóa mô hình mà request đang giữ |
| `meta/MetaController.java`, `process/ProcessInfo.java` | Version và summary tác động của trạng thái đích: thêm/bớt thành viên nhóm, bất đồng bộ; không lộ secret, URL hoặc config quản trị |
| Test liên quan | Request cũ, stale snapshot, đổi mô hình, đồng thời, quyền và atomicity |

- Không thêm luật phân quyền ở AI thay cho Core; giữ `ProcessRules`, booking guard, giao dịch và event log.
- `assign` vẫn lấy ứng viên từ `/handlers`; đổi người hoặc đơn vị trong lúc chờ phải đọc lại và Core từ chối điều kiện cũ.
- Action và assign của client cũ không gửi điều kiện giữ hành vi; client trợ lý phải gửi đủ hai phiên bản, thiếu thì không ghi.
- Metadata chỉ mô tả khả năng/tác động, không khẳng định một action chắc chắn được phép cho mọi dòng.
- Không đổi `dx-model`, YAML, BPMN, enum actor, `dx-core` hoặc `dx-monitor`.
- Không sửa KeycloakAdminClient/WebhookSender hoặc test của chúng chỉ để có thêm test cho trợ lý; đây không phải phần cần thay đổi của WP này.
- Không thêm bảng idempotency Core: ledger trợ lý chặn gọi lặp; timeout sau khi gửi là chưa xác định, không hứa exactly-once qua hai dịch vụ.

**API:** `GET /api/v1/meta/models`, `GET /meta/models/{model}/entities/{entity}`, action và assign hiện có.
**Kết quả:** giao dịch từ chối snapshot cũ ngay tại Core, không phải chỉ đọc lại ở Node rồi ghi không điều kiện.

## WPAS-05: Công cụ nghiệp vụ và quản trị

**Mục tiêu:** mở chức năng từ API hiện có, không cho model gọi HTTP tự do.

| Công cụ | Nguồn sự thật | Cách thực hiện |
|---|---|---|
| Hộp việc, danh sách/lọc, chi tiết, lịch sử | Metadata và API data/inbox | Đọc theo quyền của chủ, phân trang và giới hạn kết quả |
| Thống kê/báo cáo ngắn | `/data/.../stats`, có phạm vi và `computedAt` | Số liệu tính bởi Core; LLM chỉ diễn giải |
| Chọn người giao | `GET .../{id}/handlers` | Hiện tên/username và lựa chọn từ server; tên trùng phải hỏi lại |
| Giao, đổi người, trả việc về nhóm | `POST .../{id}/assign` | Snapshot chứa người nhận/nhãn và hai version; xác nhận trước ghi |
| Hành động quy trình | `process.actions`: from/to/by/needsNote | Catalog theo họ/action được triển khai; không suy từ chữ “approve/start” riêng lẻ |
| Kiểm vận hành cho ADMIN | Models/runs/versions, outbox và audit API | Chỉ lấy phần cần giải thích, không gửi raw payload/secret/history hash lên model |
| Giải thích thay đổi mô hình | Kế hoạch do Core tạo từ YAML người dùng cung cấp | Nêu mức rủi ro và kiểm tra thiếu; không có tool apply/rollback |

- Mở vòng công việc của `work_item`: start, resolve, confirm, reopen, withdraw và reject đúng actor/note.
- Mở action của `approval` và `reservation` đã có trong metadata, không thêm cấp duyệt hoặc bypass giới hạn đặt chỗ.
- Action không có tác động nhạy cảm dùng xác nhận native Telegram/web; có tác động membership hoặc thiếu thông tin tác động thì chuyển xác nhận sang web, trình bày rõ trước khi làm.
- Sau action thành công nhưng completion còn trong outbox, chỉ báo trạng thái hồ sơ đã đổi và tác vụ sau quyết định đang chờ; không báo quyền ngoài đã được cấp.
- Không mở tool CRUD dữ liệu, cấp/gỡ vai/đơn vị, quản trị mật khẩu, thay mô hình, retry outbox, archive hoặc SQL trong đợt này.
- `ADMIN` không được đi vòng role mô hình; đọc quản trị và đọc nghiệp vụ là hai loại công cụ.
- Với YAML do ADMIN cung cấp, dùng API plan/diagram và màn Console hiện có để giải thích/đối chiếu; không tạo parser, layout sơ đồ hoặc trình biên tập riêng cho trợ lý.
- API diagram là công cụ ADMIN đọc tệp đang viết, không thay metadata được lọc theo quyền của người xử lý hồ sơ.
- Nhận yêu cầu bằng tên/tiêu đề, trả danh sách chọn có nhãn khi mơ hồ; UUID chỉ ở state server, không bắt người dùng tìm rồi nhập.
- Proposal chứa chủ, kênh/chat, target, action, note, assignee, version, deadline và trạng thái; LLM không được đặt cờ đã duyệt.
- Bridge dành cho model chỉ được đọc và tạo proposal, không có quyền gọi endpoint xác nhận/thực thi.
- Xác nhận đi qua phiên web đã xác thực hoặc callback native đã kiểm người gửi, chat, nonce và nội dung proposal; token runtime không thay thế bằng chứng xác nhận của con người.
- Claim proposal nguyên tử trong SQLite; callback lặp, hai kênh xác nhận cùng lúc hoặc một thẻ bị sửa chỉ có tối đa một lượt gửi Core.
- Đọc lại ứng viên, metadata và quyền khi xác nhận; Core vẫn kiểm lần cuối; kết quả chưa rõ phải hướng dẫn đối chiếu, không đoán thành công.

**File chính:** `assistant/src/tools/`, `proposals/`, Core client và plugin DX-OS.
**Kết quả:** giao việc, xử lý và xác nhận đúng người trên các mô hình khác nhau, lịch sử Core đối chiếu được.

## WPAS-06: Tự động hóa và câu trả lời

**Mục tiêu:** giúp làm việc chủ động mà không trao quyền tự ghi cho model.

- Dùng scheduler/lifecycle, transport và approval của OpenClaw; không viết agent loop, cron server hay Telegram poller thứ hai.
- Runtime cần hoạt động khi nghe Telegram hoặc gửi thông báo; tắt runtime thì UI phải báo kênh đang off, không hứa tự đánh thức khi có tin nhắn.
- Mỗi tài khoản bật riêng: việc mới/được giao/cần bổ sung, nhắc gần hạn/quá hạn và bản tổng kết theo lịch.
- Cấu hình đó nằm trong state trợ lý; không mở lại tính năng per-recipient subscription đã bị loại khỏi hướng phát triển Core.
- Mốc ban đầu không gửi bão tin cũ; ledger có owner + loại thông báo + hồ sơ + mốc hạn, pagination, trạng thái pending/sent/failed/uncertain và retry hữu hạn.
- Dùng deadline/overdue/trạng thái từ Core; không xây lại `AutoCloseSweep` hoặc tự xác nhận hồ sơ theo lịch.
- Khi hồ sơ biến khỏi inbox, không đoán đã hoàn thành; đọc lại theo quyền hoặc bỏ thông báo nếu không còn được thấy.
- Giữ polling có ngân sách cho 2–3 tài khoản; không thay webhook/outbox/đăng ký thông báo Core hoặc chiếm target webhook đang dùng.
- Gợi ý giao việc chỉ dùng danh sách hợp lệ và thống kê có phạm vi; nếu không có đủ dữ liệu thì yêu cầu người dùng chọn, không suy đoán năng lực cá nhân.
- Thẻ thông báo, chi tiết và xác nhận dùng renderer xác định, lấy label/state từ metadata, ẩn trường kỹ thuật và giá trị trống, hiển thị thời gian có múi giờ.
- Câu trả lời LLM theo khung: kết luận ngắn → dữ kiện có nguồn/phạm vi/thời điểm → việc có thể làm tiếp; lời giải thích không thay phán quyết của Core.
- Không tự gán bệnh, ngành hoặc vai vào prompt chung; không đọc tệp đính kèm/RAG trong đợt này.
- Nội dung hồ sơ là dữ liệu, không phải chỉ thị; câu trả lời không hợp schema/nguồn hoặc không có evidence thì không biến thành tool call ghi.
- Mỗi đề xuất nêu rõ hành động, người nhận, trạng thái, nội dung người dùng phải cung cấp và tác động; chỉ dùng từ “đã” sau bằng chứng API.
- Tắt LLM heartbeat định kỳ; tổng kết cố định không cần LLM, diễn giải bằng LLM là lựa chọn bật riêng và có ngân sách.

**Kết quả:** người dùng nhận việc mới và có thể làm tiếp bằng nút, số liệu không bịa, lỗi provider không làm hỏng thông báo hoặc Core.

## WPAS-07: UI, bộ cài, license và CI

**Mục tiêu:** tích hợp đúng cách chạy và cách phát hành nhánh cha.

- `frontend/src/assistant/` dùng react-admin/MUI/auth, menu và error mapping hiện có; không dựng lại shell.
- Có hướng dẫn tạo profile API/bot, đồng ý gửi dữ liệu ra provider, SSO/pairing, kiểm kết nối, chọn model, bật/tắt, quota, proposal và xử lý lỗi.
- Trang quản trị trợ lý chỉ quản lý khả dụng/runtime/capacity; không đọc key hoặc hội thoại của chủ khác.
- Dùng cấu hình runtime để bật giao diện, không yêu cầu rebuild frontend mỗi khi đổi provider hoặc kích hoạt trợ lý; nếu thêm cơ chế runtime config thì chỉ ở frontend/deploy, không nhét cờ AI vào metadata nghiệp vụ.
- Thêm profile/compose trợ lý riêng và lệnh `dxos assistant on|off|status|prepare` theo pattern hiện có; provision từ file private do người vận hành quản lý.
- Không đổi lệnh khởi động mặc định: không bật AI thì `dxos start`/compose hiện tại vẫn chạy như cha, không cần key hoặc runtime AI.
- Sau opt-in một lần, `dxos start/stop/status` nhận biết thành phần trợ lý; lỗi AI báo riêng, không làm mất khả dụng Core.
- Runtime image được build/publish một lần và dùng cache, không npm install hoặc tải model mỗi lần khởi động.
- Secret/mapping người dùng ở volume private; không mount Docker socket vào service, không để app web điều khiển container.
- Giá trị cấu hình có mặc định đặt tại `.env.example`; Compose không thêm fallback ngầm, giá trị bắt buộc thiếu phải có lỗi rõ.
- Backup/restore khi bật trợ lý phải giữ DB, key giải mã và state runtime theo hướng dẫn an toàn; off/down giữ dữ liệu, purge phải tách và có xác nhận.
- Mở rộng check/build/release hiện có cho package/image mới, không thay/bỏ các check của cha; SPDX, notices, dependency catalog, SBOM runtime và quét lỗ hổng đều phải đủ.

**File dự kiến:** `assistant/{package.json,Dockerfile,README.md}`, `frontend/src/admin/AdminApp.tsx`, cấu hình frontend/Vite cần thiết; `deploy/{dxos,.env.example,assistant.sh,docker-compose.assistant.yml}`, cấu hình APISIX và setup SSO bổ sung; `sso/realm-dxos.json` nếu cần client mẫu; `deploy/{install,update,backup}.sh` chỉ chỗ phải nhận gói tùy chọn; CI/compliance/release, `DEPENDENCIES.txt`, `NOTICE`, `sbom/`, catalog.
Đường dẫn mới là đề xuất; khi triển khai chỉ giữ file thật sự cần, không tạo placeholder cả danh sách.

**Kết quả:** clone sạch chạy Core bằng lệnh cũ; bật thêm trợ lý theo hướng dẫn, không cần môi trường npm/Python trên máy người dùng cuối.

## WPAS-08: Nghiệm thu 2–3 người và bàn giao

- Chạy ma trận trong `03_TEST_PLAN.md`, cả AI off/on, lỗi API và tài khoản khác quyền.
- Dùng `deploy/dev/check_api.py`, `check_process.py`, `check_case.py`, `check_booking.py`, `check_events.py`, `check_conformance.py` và E2E hiện có theo phần bị ảnh hưởng; chỉ thêm case ngăn sai quyền, sai giao dịch, gọi lặp hoặc mất thông báo cần thiết.
- Compile/formatter/TypeScript và đọc diff là vòng kiểm thường xuyên; test được duyệt chạy theo phạm vi, image/clone sạch chỉ ở mốc nghiệm thu đã thống nhất.
- Dùng mẫu có sẵn của Core, tạo dữ liệu giả qua API trong test; không thêm dataset nghiệp vụ, trọng số hoặc dump DB vào repository.
- Đo tách cold/warm, p50/p95, CPU/RAM/swap/disk, số request Core, token/lượt provider và chi phí nếu provider trả; không dùng số PoC thay số bản mới.
- Chốt trần RAM/CPU, poll/page budget, số tool call/lượt và timeout trong G0 theo máy đích; đo nền Core cùng cấu hình trước khi so sánh AI off/on, không đặt một SLA giả định cho mọi provider.
- Kiểm với hai rồi ba runtime; thiếu tài nguyên hoặc mất cách ly thì dừng, không gộp chung gateway cho qua test.
- Chạy provider contract và luồng thật với key được cấp qua UI; không dùng mock để đánh dấu vendor thật đạt.
- Người dùng nghiệm thu Telegram/web, gồm giao việc giữa hai người và ADMIN không có role nghiệp vụ.
- Dọn file sinh thừa, đọc diff toàn bộ, kiểm secret/notice/SBOM và các file bị tác động ngoài domain; giữ kế hoạch để review, không tự commit/push.

## Hợp đồng và dữ liệu

| Đối tượng | Thay đổi | Tương thích |
|---|---|---|
| Core action/assign | Hai điều kiện phiên bản tùy chọn | Request cũ vẫn dùng được; assistant không ghi nếu thiếu |
| Core metadata | Version và mô tả tác động hoàn tất đã rút gọn | Chỉ thêm trường; model held không cung cấp snapshot để ghi |
| `/assistant-api/v1/me/...` | Profile, liên kết, lifecycle, notification, proposal/receipt của chính chủ | API mới của dịch vụ mở rộng, không đặt trong Spring Core |
| `/assistant-api/v1/admin/...` | Khả dụng/capacity của trợ lý | JWT ADMIN; không đọc secret/transcript người khác |
| API nội bộ runtime | Token riêng bound owner; start/stop và tool bridge cố định | Không publish cho browser, không nhận URL/header/method HTTP tùy ý |
| SQLite trợ lý | Schema có version, mã hóa secret/snapshot, unique owner/bot/proposal và ledger | Migrate transaction riêng; không nhập state PoC tự động |
| YAML nghiệp vụ | Không đổi | Các mô hình đang có không phải thêm cấu hình AI |

OpenAPI Spring vẫn sinh từ annotation; control API sinh từ schema route, không duy trì bản YAML thủ công thứ hai.
Client Node phải đọc đúng `data.reason`, không sửa response 200/403/409 của Core theo ý LLM.

## Migration và rollback

Không có migration Core, không thay dữ liệu `m_*` hoặc các process instance.
State PoC chỉ tham khảo, không tự nhập token/bot/khóa từ máy lập trình vào bản tích hợp.
Người dùng tự liên kết lại trên môi trường mới; migration SQLite, backup và khôi phục master key phải được kiểm trước release.
Tắt trợ lý/cờ giao diện/route tùy chọn thì Core vẫn hoạt động; không rollback các hành động nghiệp vụ đã hoàn tất.
Nếu quay về backend chưa kiểm phiên bản, tắt công cụ ghi của trợ lý đồng thời.
Không xóa lịch sử, volume Core, `report/`, kế hoạch PoC hoặc tài liệu ý tưởng cá nhân trong thao tác rollback.
