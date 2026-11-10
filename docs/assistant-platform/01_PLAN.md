# Kế hoạch thực hiện: Trợ lý hồ sơ đa người dùng

Thứ tự: Spec → Swagger/OpenAPI → Code → bằng chứng kiểm thử.
Bản tích hợp đã có trên nhánh PR; các mục chưa nghiệm thu được theo dõi trong checklist và test plan.

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
- Kiểm local, ưu tiên script hiện có; không build lại image cho mỗi sửa Java hoặc test client HTTP, dùng môi trường kiểm thử riêng cho failure injection.
- Không tự stage/commit/push, không đổi danh tính Git hoặc chỉnh `.gitignore`; giữ `report/`, tài liệu cá nhân và dữ liệu cục bộ ngoài phần công việc này.

## WPAS-01: Chốt hợp đồng và kiểm nền

**Mục tiêu:** chốt kiến trúc, hợp đồng và điều kiện nghiệm thu của trợ lý trên Core hiện tại.

- Đối chiếu bốn module, `ArchitectureTest`, `DataService`, `ProcessRules`, `ModelUseFilter`, registry, SSO và bộ cài.
- Khóa bản OpenClaw/Node tương thích, đối chiếu API provider và kiểm khả năng đóng gói, phát hành.
- Kiểm dependency trực tiếp, bắc cầu, license notices, image và khả năng build từ source theo policy nhánh cha; không chỉ kiểm giấy phép MIT của gói OpenClaw ngoài cùng.
- Báo kết quả giấy phép trước khi thêm OpenClaw/Fastify/jose hoặc công cụ build/test vào manifest; danh mục cuối cùng phải khớp manifest, lockfile, SBOM và hồ sơ giấy phép.
- Xác định phần nào kiểm bằng script/test có sẵn và phần nào cần file mới; ngoại lệ WireMock của tech lead chỉ áp dụng client HTTP KeycloakAdminClient/WebhookSender, không cấp quyền tự mở thêm cả bộ test.
- Chốt danh mục thao tác, profile trả lời, schema proposal, ranh giới tài khoản, API mới và ngân sách tài nguyên.
- Lập bảng thành phần tích hợp, chỉ rõ cách lưu dữ liệu, xác thực, điều phối runtime và giao tiếp với Core.

**Phạm vi:** spec, hợp đồng và test thiết kế; chưa sửa kernel hoặc triển khai provider.
**Kết quả:** qua G0, đủ điều kiện mới bắt đầu các WP sau; nếu SDK/giấy phép không đạt thì báo lại lựa chọn thay thế trước khi đổi nền tảng.

Hợp đồng API/danh tính/proposal ở [`assistant/README.md`](../../assistant/README.md).

| Thành phần | Cách tích hợp |
|---|---|
| Core client | Gọi API bằng phiên của chủ tài khoản, giữ `data.reason` và kiểm quyền của Core |
| Xác nhận và callback | Người dùng xác nhận qua đường riêng, token công cụ không có quyền thực thi |
| Thẻ thông báo | Dùng nhãn và trường từ metadata, hiển thị văn bản thuần |
| Thông báo và ledger | Đọc API theo chủ, lưu mốc và kết quả gửi trong SQLite |
| Runtime | Mỗi tài khoản có runtime, vùng dữ liệu, mạng và token riêng |
| HTTP và lưu trữ | Fastify/schema và SQLite/migration; bí mật được cấu hình trên máy triển khai |

Đã kiểm giấy phép upstream và artifact của OpenClaw, Fastify, jose cùng các gói bundled trong lockfile.
`spdx-exceptions` (CC-BY-3.0) và `spdx-license-ids` (CC0-1.0) là dữ liệu định danh giấy phép đi kèm runtime.
Ghi giấy phép gốc trong `DEPENDENCIES.txt`, nguồn và ghi công trong `NOTICE`, ngoại lệ kiểm dữ liệu trong `.github/dependency-exceptions.json`.
Ngoại lệ không áp dụng cho mã phần mềm hoặc gói khác.
Các kiểm chứng runtime, provider và xác nhận vẫn phải đạt trước nghiệm thu.

## WPAS-02: Tài khoản, state và runtime riêng

**Mục tiêu:** mỗi người có trợ lý đúng danh tính và dữ liệu riêng.

- Tạo `assistant/` là dịch vụ mở rộng TypeScript, đóng gói JavaScript trong image phát hành.
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
| OpenAI-compatible | Tạm khóa trong bản hiện tại; chỉ mở lại sau nghiệm thu transport redirect/DNS/private egress |

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

## WPAS-08: Nghiệm thu 2 người và bàn giao

- Chạy ma trận trong `03_TEST_PLAN.md`, cả AI off/on, lỗi API và tài khoản khác quyền.
- Dùng `deploy/dev/check_api.py`, `check_process.py`, `check_case.py`, `check_booking.py`, `check_events.py`, `check_conformance.py` và E2E hiện có theo phần bị ảnh hưởng; chỉ thêm case ngăn sai quyền, sai giao dịch, gọi lặp hoặc mất thông báo cần thiết.
- Compile/formatter/TypeScript và đọc diff là vòng kiểm thường xuyên; test được duyệt chạy theo phạm vi, image/clone sạch chỉ ở mốc nghiệm thu đã thống nhất.
- Dùng mẫu có sẵn của Core, tạo dữ liệu giả qua API trong test; không thêm dataset nghiệp vụ, trọng số hoặc dump DB vào repository.
- Đo tách cold/warm, p50/p95, CPU/RAM/swap/disk, số request Core, token/lượt provider và chi phí nếu provider trả; chỉ dùng số đo của bản tích hợp.
- Chốt trần RAM/CPU, poll/page budget, số tool call/lượt và timeout trong G0 theo máy đích; đo nền Core cùng cấu hình trước khi so sánh AI off/on, không đặt một SLA giả định cho mọi provider.
- Kiểm với hai runtime; thiếu tài nguyên hoặc mất cách ly thì dừng, không gộp chung gateway cho qua test.
- Thử tải ba runtime chuyển sang BACKLOG theo quyết định của chủ dự án.
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
| SQLite trợ lý | Schema có version, mã hóa secret/snapshot, unique owner/bot/proposal và ledger | Migrate transaction riêng; cấu hình bí mật theo từng tài khoản |
| YAML nghiệp vụ | Không đổi | Các mô hình đang có không phải thêm cấu hình AI |

OpenAPI Spring vẫn sinh từ annotation; control API sinh từ schema route, không duy trì bản YAML thủ công thứ hai.
Client Node phải đọc đúng `data.reason`, không sửa response 200/403/409 của Core theo ý LLM.

## Migration và rollback

Không có migration Core, không thay dữ liệu `m_*` hoặc các process instance.
Token, bot và khóa được cấu hình trên môi trường triển khai; không đưa bí mật từ máy lập trình vào gói phát hành.
Người dùng tự liên kết lại trên môi trường mới; migration SQLite, backup và khôi phục master key phải được kiểm trước release.
Tắt trợ lý/cờ giao diện/route tùy chọn thì Core vẫn hoạt động; không rollback các hành động nghiệp vụ đã hoàn tất.
Nếu quay về backend chưa kiểm phiên bản, tắt công cụ ghi của trợ lý đồng thời.
Không xóa lịch sử, volume Core, `report/`, kế hoạch hoặc tài liệu ý tưởng cá nhân trong thao tác rollback.

## Xử lý review PR #199

Thứ tự: WPRV-01 → WPRV-02 → WPRV-03 → WPRV-04 → WPRV-05 → WPRV-06.
Giữ nguyên các kết quả cũ đã có bằng chứng; không dùng chúng thay cho kiểm thử bản sau merge.
Không commit, push hoặc resolve comment trong đợt lập kế hoạch.

### WPRV-01: Chốt nền, quyết định và phạm vi

- Đối chiếu remote PR với `origin/Khuong/main`, kiểm không thiếu commit cha và đọc lại ba vùng merge.
- Giữ `require` và khóa dòng trong `DataService.act`, danh sách `handlers` của `ProcessInfo`, `handlerActions` của metadata.
- Ghi đúng mô hình tích hợp API bên ngoài do người dùng cấu hình; không mô tả provider đó là Open Source AI.
- Giữ xác nhận ngoại lệ `spdx-exceptions` và `spdx-license-ids` đã được người dùng duyệt, không yêu cầu duyệt lại.
- Rà từng dấu tick theo bằng chứng và kết quả từng ca; triển khai adapter không đồng nghĩa nghiệm thu provider hoặc nhiều người đồng thời.

**Phạm vi:** năm tài liệu kế hoạch; đối chiếu Git và diff, không sửa nghiệp vụ của cha.
**Đầu ra:** bảng review có việc sửa/kiểm/chấp nhận và điều kiện đóng; không bỏ sót nhận xét tài liệu.

### WPRV-02: Kiểm rào chắn bảo mật

- Chạy TAS-06 bằng hai runtime thật trong môi trường riêng: API/callback của A nhắm proposal B, sai capability, truy cập volume/network và công cụ ngoài danh mục.
- Tách hai phép kiểm: A dùng credential của A để nhắm B phải bị từ chối; credential bí mật của B phải không thể lấy từ runtime A.
- Không coi một bearer token B đã bị lộ là tự phân biệt được người đang cầm nó; kiểm hạn chế nguồn gọi theo kiến trúc và ghi rõ trust boundary.
- Chạy TAS-12 qua đúng đường probe và inference của OpenClaw đã pin, không chỉ kiểm validator lúc lưu profile.
- Dùng key giả và endpoint thử nghiệm: IPv4/IPv6 private, loopback/link-local, origin không cho phép, redirect sang origin khác hoặc nội bộ, DNS đổi địa chỉ giữa kiểm và kết nối.
- Kiểm không request/key nào đến đích bị chặn; allowlist do ADMIN khai không thay bảo vệ ở transport.
- Ưu tiên bảo vệ có sẵn của runtime đã khóa phiên bản; nếu chưa chứng minh được, vô hiệu hóa profile compatible và báo rõ thay vì tự viết transport hoặc tuyên bố an toàn.
- Nếu phát hiện lỗi, sửa hẹp ở binding chủ, cấu hình runtime hoặc provider path, rồi chạy lại ca đỏ/xanh.

**File chính:** `assistant/src/{auth,providers,runtime,tools}/`, `assistant/runtime/plugin/`, các file test trợ lý hiện có và test plan.
**Đầu ra:** TAS-06/TAS-12 có bằng chứng thật; lỗ hổng cách ly hoặc gửi key sai đích chặn merge, không chuyển thành giới hạn chấp nhận.

### WPRV-03: Backup và chính sách giấy phép

- Giữ mô hình backup restic hiện có; ghi rõ backup có cả master key và SQLite, nên quyền giải mã backup cho phép giải mã secret trợ lý.
- Mô tả quyền truy cập, lưu mật khẩu restic ngoài backup và nguy cơ mất backup credential; không tuyên bố AEAD là lớp cách ly với người quản trị backup.
- Ghi Telegram/nhắc hạn tạm gián đoạn khi trợ lý dừng để snapshot, kiểm resume cả thành công lẫn lỗi và không mất state hoặc khóa.
- Không tự tách khóa khỏi backup vì có thể làm bản sao không khôi phục được; phương án custody khóa riêng cần quyết định vận hành và kế hoạch khác.
- Bỏ `Artistic-2.0` khỏi `ALLOWED` chung; gói đang dùng giấy phép này là `npm@12.2.2` bundled trong runtime, cần đọc lại LICENSE/metadata và khai ngoại lệ theo tên/phiên bản đúng cơ chế checker hiện có.
- Giữ hai ngoại lệ dữ liệu SPDX và attribution đã duyệt; kiểm policy trên cả repository, không chỉ SBOM mới.

**File chính:** `deploy/OPERATIONS.md`, `deploy/backup.sh` nếu test chỉ ra lỗi vòng đời; `.github/{scripts/check_licenses.py,dependency-exceptions.json}`, NOTICE/dependency docs liên quan.
**Đầu ra:** restore được với đúng credential; failure không bỏ trợ lý tắt ngoài ý muốn; không mở policy cho mọi dependency.

### WPRV-04: Metadata và định dạng Core

- Giữ truy vấn phiên bản trực tiếp trong `ModelRegistry` và kiểm phiên bản trước khi ghi; tối ưu truy vấn nằm trong BACKLOG.
- Giữ trường `asynchronous=true`: `RecordStatus` đưa completion vào outbox, frontend dùng trường này để cảnh báo tác vụ ngoài chưa hoàn tất.
- Ghi nghĩa trong spec/Swagger và kiểm với completion thật; giữ cổng xác nhận web cho membership hoặc tác động không biết.
- Định dạng `RecordPreconditions` theo formatter của cha, giữ hành vi parse/check.

**File chính:** `meta/MetaController.java`, `data/RecordPreconditions.java`, `registry/ModelRegistry.java`; giữ cách đọc phiên bản trực tiếp của Core.
**Đầu ra:** metadata đúng nghĩa; hai precondition vẫn đúng trên request cũ/mới; quy trình tự khai báo không hồi quy.

### WPRV-05: UI và gateway tùy chọn

- Cache feature detection trong vòng đời trang/session, bỏ gọi lại theo mỗi focus; reload trang vẫn nhận trạng thái bật/tắt mới.
- Dùng lại API feature hiện có, không thêm endpoint Core chỉ để báo bật trợ lý; khi trợ lý off không tạo chuỗi 404 theo focus.
- Chuyển lệnh dựng APISIX hiện có vào script dùng chung dự kiến `deploy/apisix/render.sh`; giữ bước thay monitor key và ghi dấu kết thúc cấu hình.
- Script chỉ nạp route bổ sung từ thư mục riêng khi được mount; overlay chỉ mount route trợ lý vào thư mục đó, không ghi đè command hoặc sao chép lệnh generator.
- Base Compose gọi cùng script nhưng không mount route tùy chọn; không đưa route trợ lý vào cấu hình hoạt động khi assistant off.
- Route không bật khi assistant off; không mở endpoint điều khiển runtime ra public và không làm mất thay thế monitor key hoặc cấu hình SSO của cha.
- Biến mặc định nằm trong `.env.example`, Compose dùng biến bắt buộc theo convention hiện có.
- Giữ giới hạn ba runtime; đối chiếu mã, UI, README và BACKLOG, không tăng capacity trong đợt này.

**File chính:** `frontend/src/admin/AdminApp.tsx`, `frontend/src/assistant/api.ts`, `deploy/docker-compose*.yml`, cấu hình/script APISIX liên quan trực tiếp.
**Đầu ra:** Core off/on và route đúng; focus không spam request; sửa generator gốc chỉ cần một nơi.

### WPRV-06: Đồng bộ docs và nghiệm thu review

- Gộp nội dung trợ lý vào mục `2.0.0` chưa phát hành theo nhánh cha; giữ toàn bộ log cũ, không tạo thêm hai vùng cùng nghĩa chưa phát hành.
- Đồng bộ `website/docs/dependencies.md`, `DEPENDENCIES.txt`, NOTICE và SBOM nếu dependency đổi; bỏ số `npm audit` cố định khỏi README, dẫn tới kết quả CI.
- Bổ sung trong `docs/API.md` ý nghĩa version/modelVersion tùy chọn cho client ngoài và liên kết API trợ lý tại `assistant/README.md`.
- Rà checklist từng dòng: đủ bằng chứng mới tick, ca một phần giữ mở; cập nhật ghi chú bàn giao cho đúng nhánh đã push và trạng thái sau merge.
- Chạy ma trận hồi quy ở test plan trên nền mới, gồm action tự khai báo; không dùng kết quả trước merge để đóng.
- Ghi giới hạn còn lại vào BACKLOG; không đóng nghiệm thu khi ca bảo mật hoặc hồi quy Core chưa đạt.
- Kiểm diff, secret và file sinh; giữ `report/`, `.gitignore`, khóa, DB, dữ liệu thật và artifact thử ngoài commit.
- Lập bảng phản hồi từng mục review cùng bằng chứng để người dùng đọc trước khi cho phép push/resolve.

**Đầu ra:** mọi nhận xét có kết luận, test và giới hạn có trạng thái thật; không tự đóng toàn phase khi các tiêu chí WPAS gốc còn thiếu.

### Hợp đồng, dữ liệu và rollback của đợt review

Không thêm endpoint nghiệp vụ, sửa YAML, migration Core hoặc bảng trợ lý trong kế hoạch này.
Giữ request cũ không gửi version/modelVersion; thay nghĩa hoặc bỏ trường metadata phải cập nhật spec, `@Operation` và mọi consumer trước code.
Nếu bảo vệ endpoint compatible chưa đạt, profile đó bị chặn có thông báo; provider native vẫn độc lập.
Rollback bằng quay lại thay đổi tương ứng và tắt trợ lý; giữ nguyên lịch sử nghiệp vụ và state/khóa để phục hồi.

## Điều chỉnh theo review bổ sung

Phần này ưu tiên hơn thứ tự WPRV-01–06 ở trên.
Thực hiện theo bảng quyết định tại mục 12 overview; các giới hạn chưa xử lý phải được theo dõi đến khi đáp ứng điều kiện nghiệm thu.

### WPRV-07: Lỗi HTTP, ranh giới mạng và hợp đồng tích hợp

**Ưu tiên bắt buộc:** hoàn thiện bug off/focus của WPRV-05 và phân loại lỗi HTTP trong `assistant/src/server.ts`.
Lỗi parse JSON phải là 400, vượt body limit là 413; lỗi server không dự kiến vẫn 500 và không trả raw error/payload.
Giữ envelope, thêm errorCode/thông báo và dịch frontend nếu phát sinh mã mới; kiểm bằng test hiện có và HTTP qua gateway/monitor.
Không chỉ dùng render config để kết luận off đã sửa: bật → tắt → gọi API nhiều lần, kiểm độ trễ, mã phản hồi và không thêm UPSTREAM_DOWN do thao tác off hợp lệ.
Khi chưa từng bật, SPA có thể trả HTML 200; frontend phải coi là không có trợ lý, không coi đó là lỗi server.

**Mạng, đã duyệt:** control bỏ mạng `default` trong cấu hình sinh ở `assistant/src/setup.ts`.
Overlay khai mạng `assistant-core` giữa control/backend/Keycloak và mạng ingress riêng giữa APISIX/control; runtime giữ từng mạng riêng với control.
Không chỉ gắn control/backend/Keycloak mà bỏ gateway, vì APISIX còn cần kết nối control để phục vụ route trợ lý.
Kiểm TCP cả hostname lẫn IP: control gọi được API/JWKS cần thiết và gateway tới được control, nhưng control/runtime không tới Postgres/SeaweedFS hoặc runtime khác.
Core khi không dùng overlay không có mạng trợ lý hoặc dependency mới.

**Egress:** `allowPrivateNetwork=false` không thay thế firewall cho cả container.
Giữ ba provider native Gemini/OpenAI/Anthropic và endpoint compatible đang tắt.
Bảo vệ kết nối Internet toàn runtime còn cần hoàn thiện, gồm DNS, TLS/redirect, Telegram, provider và hành vi khi lỗi.
Không thêm proxy hoặc dependency mạng trong đợt này nếu chưa duyệt.

**Hợp đồng và docs:** ghi yêu cầu truy cập đúng origin GATEWAY_PUBLIC_URL, không nới CSRF hoặc nhận origin tùy ý.
Mô tả schema bỏ trường ngoài danh sách và danh tính luôn lấy từ JWT; giữ PATCH full-profile hiện có, không tự thêm cập nhật riêng model/key hoặc xóa endpoint.
Bổ sung trust boundary backup gồm master key, provider/SSO secret và token control/tool/confirmation, cùng yêu cầu bảo vệ/thu hồi credential khi có sự cố.
Trường hợp chưa liên kết Core trả 409 thay vì 401; phân biệt với phiên đăng nhập không hợp lệ, kiểm client và thông báo.

**Phiên bản Core:** giữ cách đọc phiên bản trực tiếp trong `ModelRegistry` và kiểm phiên bản trước khi ghi.
Tối ưu truy vấn nằm trong BACKLOG.
TRV-05 kiểm phiên bản sau apply, rollback, restart và yêu cầu dùng snapshot cũ.

**Nghiệm thu:** TRV-11–16 và các hồi quy trên đúng bản tích hợp.
Ghi riêng bằng chứng local, CI và các ca cần key hoặc runtime thật; ca chưa kiểm đủ vẫn để mở.
BACKLOG theo dõi giới hạn kết nối Internet của runtime, quản lý khóa/token backup và tối ưu truy vấn phiên bản.
Chỉ đóng từng mục khi có triển khai và bằng chứng đáp ứng tiêu chí tương ứng.
