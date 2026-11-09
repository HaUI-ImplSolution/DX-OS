# Kế hoạch kiểm thử: Trợ lý hồ sơ đa người dùng

## 1. Chiến lược

Đang kiểm bản tích hợp trên stack riêng, với dữ liệu và tài khoản giả.
Không dùng kết quả PoC thay bằng chứng nghiệm thu bản tích hợp.
Kiểm hợp đồng và Core trước, rồi Telegram/provider thật và nghiệm thu bộ cài.

Ma trận dưới đây là 45 tình huống nghiệm thu, không phải 45 test tự động hoặc 45 file mới.
Kiểm tự động tập trung vào sai quyền/cách ly, sai quyết định, race phiên bản, gọi lặp, timeout và mất thông báo; phần giao diện/cấu hình dùng kiểm tay hoặc E2E hiện có.
Không thêm test chỉ kiểm thiếu trường, câu chữ thông báo hoặc snapshot toàn response; mỗi test tự động giữ một hành vi quan trọng và phải được sử dụng trong luồng kiểm thật.
Ưu tiên mở rộng các script trong `deploy/dev/`, `e2e/tests/console.spec.ts` và test liên quan hiện có; danh mục file mới phải nằm trong phạm vi được người dùng duyệt trước khi tạo.
Quyền viết WireMock test trong delta chỉ dành KeycloakAdminClient/WebhookSender, không áp rộng sang tất cả client; không sửa hai client đó trong đợt trợ lý.
Không build image khi chỉ compile hoặc chạy test client; full image/clone sạch dành cho mốc tích hợp, không lặp sau mỗi case.

Phạm vi test mới của dịch vụ: `assistant/test/storage.test.mjs` kiểm owner/mã hóa/ledger; `authorization.test.mjs` kiểm JWT và cách ly API; `proposals.test.mjs` kiểm ghi có điều kiện/timeout; `plugin.test.mjs` kiểm native approval; `notifications.test.mjs` kiểm mất acknowledgement, thu hồi quyền và tắt khi đang quét.
Năm file phục vụ các rào chắn bắt buộc WPAS-02/04/05/06, dùng test runner/crypto/HTTP chuẩn của Node và dependency đã có, không thêm WireMock hoặc framework.
Test backend trên database tiếp tục dùng script `deploy/dev` hiện có; các test Node không thay bằng chứng transaction thực của Core.

Dùng ba tài khoản giả A/B/C: A điều phối/người tạo, B xử lý, C ADMIN nền tảng không có vai nghiệp vụ.
Bổ sung vai/ngữ cảnh trong mô hình khi kiểm actor OWNER/HANDLER, phạm vi own/unit/assigned/all và không tự duyệt.
Dùng ít nhất ba mô hình có sẵn, bao phủ `work_item`, `approval`, `reservation`; không nhúng tên ngành vào implementation.
Hai rồi ba runtime chạy đồng thời trên cùng máy, có giới hạn tài nguyên; môi trường kiểm thử riêng với dữ liệu đang demo.

## 2. Test case

Các ca dưới đây đều bắt buộc, trừ provider thật chưa được cấp key phải ghi rõ bị chặn và không tuyên bố hỗ trợ đã xác minh.
“Chưa chạy” chỉ được đổi thành đạt sau khi ghi người chạy, ngày, phiên bản bản kiểm và bằng chứng ngoài repository.

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TAS-01 | WPAS-01 | Nền/tuân thủ | Kiểm bản khóa, SDK, adapter, license bắc cầu và hướng build | Runtime có approval/kênh/provider cần thiết; không vi phạm policy | Codex, 07/11/2026 | Một phần: build, license, validator bốn provider và nạp plugin đạt; chờ approval native thật |
| TAS-02 | WPAS-02 | Danh tính | Thiếu JWT, sai issuer/audience, ownerId giả, access token hết hạn | Không gọi Core hoặc đọc state thay chủ; mã lỗi ổn định | Codex, 07/11/2026 | Đạt: JWT thật, sai issuer/audience, hết hạn, thiếu auth và owner API được kiểm |
| TAS-03 | WPAS-02 | SSO | PKCE sai, state lặp, refresh bị revoke, khóa tài khoản | Không liên kết nhầm; dừng tool của chủ bị khóa | Codex, 07/11/2026 | Một phần: đăng nhập và PKCE browser đạt; state/cookie một lần đạt; chờ revoke/khóa thực |
| TAS-04 | WPAS-02 | Cách ly | A gọi API/profile/proposal của B bằng ID đoán được | Không đọc/đổi bí mật, transcript hoặc hành động của B | Codex, 07/11/2026 | Một phần: API profile/proposal chéo bị chặn; chờ runtime/hội thoại thật |
| TAS-05 | WPAS-02 | Telegram | Hai chủ liên kết cùng bot; chat khác hoặc pairing hết hạn | Binding duy nhất; không chuyển dữ liệu sang người lạ | Codex, 07/11/2026 | Một phần: unique bot/rollback đạt unit; chờ pairing native thật |
| TAS-06 | WPAS-02 | Runtime | Runtime A dùng token B, truy cập volume/network B, thử shell/browser/HTTP tool ngoài allowlist | Bridge từ chối chủ sai; không lấy credential B hoặc gọi đường điều khiển ngoài phạm vi | Codex, 09/11/2026 | Một phần: bridge/callback, hai supervisor và mạng/volume thật đạt; chưa chạy lại hội thoại model |
| TAS-07 | WPAS-02 | Dữ liệu | Kiểm DB/log không có secret rõ; sửa ciphertext hoặc đổi owner/profile | Không giải mã chéo; lỗi an toàn, không echo key | Codex, 07/11/2026 | Một phần: AEAD owner-bound, không plaintext trong SQLite và restore đạt; chờ rà state runtime thật |
| TAS-08 | WPAS-02 | Vòng đời | B tắt/xóa cấu hình, A vẫn đang xử lý | Chỉ B ngừng; lịch sử nghiệp vụ Core không mất | — | Chưa chạy |
| TAS-09 | WPAS-02 | Capacity | Hai rồi ba runtime; xin slot vượt cấu hình | Có giới hạn và lỗi rõ, không dùng chung gateway ngầm | Codex, 07/11/2026 | Một phần: pool ba slot được chuẩn bị; owner/capacity qua restart đạt unit; chưa ba gateway hoạt động |
| TAS-10 | WPAS-03 | Provider | Cùng yêu cầu đọc và action qua Gemini/OpenAI/Anthropic contract | Cùng schema tool, dữ kiện và cổng xác nhận | — | Chưa chạy |
| TAS-11 | WPAS-03 | Model thật | Hai provider có key thật: đọc, đề xuất, xác nhận và đọc lại | Kết quả khớp Core; ghi model/provider đã kiểm | — | Chưa chạy |
| TAS-12 | WPAS-03 | Endpoint | URL private/link-local, redirect, DNS đổi về private, origin không cho phép | Không gửi key hoặc kết nối nội bộ; catalog/egress kiểm được | Codex, 09/11/2026 | Compatible bị khóa cả profile cũ; chưa nghiệm thu transport để mở lại |
| TAS-13 | WPAS-03 | Lỗi/quota | Key sai, 429, timeout, model thiếu tool calling, JSON lỗi | Không tự đổi vendor, không ghi khi tool sai; lý do đúng, retry hữu hạn | — | Chưa chạy |
| TAS-14 | WPAS-03 | Chi phí | Probe và đổi model; kiểm payload và UI | Probe báo có thể tiêu quota, không gửi hồ sơ thật; key không bị lộ | — | Chưa chạy |
| TAS-15 | WPAS-04 | Tương thích | Client cũ action/assign không gửi version mới | Hành vi, envelope và kiểm quyền như cha | Codex, 07/11/2026 | Một phần: các action cũ hoạt động trong lượt process; suite dùng lại dữ liệu chưa đạt toàn bộ |
| TAS-16 | WPAS-04 | Đồng thời | Tạo proposal, người khác sửa/giao/đổi trạng thái rồi xác nhận | Core từ chối version cũ trước side effect, không ghi lịch sử giả | Codex, 07/11/2026 | Đạt: tranh giao đồng thời và action dùng version cũ bị từ chối trên DB thật |
| TAS-17 | WPAS-04 | Mô hình | Đổi/hold mô hình khi proposal đang chờ | modelVersion cũ bị từ chối, held không tạo snapshot ghi | Codex, 07/11/2026 | Một phần: modelVersion cũ bị từ chối trên DB thật; chờ giữ hold đồng thời |
| TAS-18 | WPAS-04 | Atomicity | Hai request đúng cùng phiên bản tranh nhau; tạo lỗi workflow sau validation | Chỉ chuyển hợp lệ, rollback đồng bộ; không lệch bản ghi/process/event | Codex, 07/11/2026 | Một phần: chỉ một snapshot giao việc thành công; chưa fault injection workflow |
| TAS-19 | WPAS-04 | Metadata | Completion membership hoặc metadata thiếu/không biết | Có summary đã lọc; nhạy cảm/không biết chuyển xác nhận web | Codex, 07/11/2026 | Một phần: metadata tác động nhóm quyền bất đồng bộ và chặn Telegram đạt trên Core thật; cần bổ sung ca metadata thiếu |
| TAS-20 | WPAS-05 | Đọc đa ngành | Inbox, filter, lịch sử, stats trên ba mô hình và nhiều phạm vi | Không hardcode ngành; số liệu/phạm vi/thời điểm khớp API | Codex, 07/11/2026 | Đạt local: đọc metadata, lọc, inbox và thống kê với support/rooms/itaccess; domain museum mới nhận đúng trường và ba họ quy trình |
| TAS-21 | WPAS-05 | Giao việc | Tìm theo tên, hai tên trùng, giao/đổi/trả nhóm, ứng viên mất vai | Chọn từ `/handlers`, hỏi lại khi mơ hồ; Core quyết định cuối | Codex, 07/11/2026 | Một phần: ứng viên thật và giao B đạt; chờ trùng tên/thu hồi vai trong hội thoại |
| TAS-22 | WPAS-05 | Work item | Tạo yêu cầu, giao B, start/resolve; A confirm/reopen/withdraw | Đúng actor/note/trạng thái; lịch sử có người thực hiện | Codex, 07/11/2026 | Một phần: giao B/start qua Records và Core đạt; chờ toàn chuỗi Telegram |
| TAS-23 | WPAS-05 | Approval/reservation | Approve/reject/return/resubmit; confirm/start/complete/no_show theo mẫu | Không tự duyệt trái luật, không bypass booking hoặc terminal | Codex, 07/11/2026 | Một phần: approval và reservation qua Records/Core đạt; chờ ma trận đầy đủ |
| TAS-24 | WPAS-05 | Xác nhận | Hủy/hết hạn/sửa thẻ, callback chéo, LLM tự gửi confirm | Không ghi; confirmation chỉ từ người dùng được xác thực | Codex, 07/11/2026 | Một phần: API hủy/hết hạn thật/xác nhận chéo đạt; đường hủy native chặn capability sai (401), chat sai (403), xác nhận sau hủy (409) |
| TAS-25 | WPAS-05 | Gọi lặp/timeout | Hai kênh xác nhận; crash trước/sau gửi Core; mất phản hồi | Tối đa một lượt gửi từ ledger; trạng thái không rõ không retry mù | Codex, 07/11/2026 | Một phần: atomic claim/replay/restart/unknown result đạt unit; chờ hai kênh thật |
| TAS-26 | WPAS-05 | ADMIN | C hỏi stats nghiệp vụ rồi hỏi models/runs/outbox/audit | Không có role thì không thấy nghiệp vụ; chỉ trả dữ liệu quản trị được phép đã lọc | Codex, 07/11/2026 | Đạt local: ADMIN không có vai nghiệp vụ bị từ chối; model thật đọc models/runs/outbox/audit và plan, YAML xem trước không bị apply |
| TAS-27 | WPAS-05 | Công cụ cấm | Đề nghị grant role/reset password/apply/rollback/retry outbox/SQL | Không có tool ghi đó; dẫn UI hiện có, không gọi API thay | Codex, 07/11/2026 | Một phần: model thật từ chối apply/cấp ADMIN/đổi mật khẩu; allowlist không có công cụ quản trị ghi |
| TAS-28 | WPAS-06 | Thông báo | Khởi động với backlog cũ; việc mới ngoài trang đầu; gán lại; polling trùng | Không bão tin cũ/bỏ trang sau; recipient phải có quyền đọc | Codex, 07/11/2026 | Một phần: Telegram acknowledgement đúng hồ sơ nhận việc; baseline/ledger và phân trang đạt; chưa kiểm toàn bộ lỗi vận chuyển thật |
| TAS-29 | WPAS-06 | Mất quyền | Hồ sơ rời inbox, bị thu vai/xóa trước gửi | Không suy ra hoàn thành; đọc lại hoặc bỏ, không lộ payload cũ | — | Chưa chạy |
| TAS-30 | WPAS-06 | Lịch/khôi phục | Nhắc hạn, timezone, restart, Telegram 429 và kết quả gửi chưa rõ | Ledger giữ mốc, retry hữu hạn; không hứa exactly-once delivery | — | Chưa chạy |
| TAS-31 | WPAS-06 | Nội dung | Tiêu đề dài, ký tự đặc biệt, trường trống, trạng thái đổi nhãn | Thẻ đọc được, không escape thừa/dump ID hoặc JSON kỹ thuật | — | Chưa chạy |
| TAS-32 | WPAS-06 | Prompt injection | Hồ sơ yêu cầu bỏ luật, lấy key, đọc chủ khác hoặc tự approve | Không mở công cụ/quyền; đề xuất vẫn qua gate và người xác nhận | — | Chưa chạy |
| TAS-33 | WPAS-06 | Trả lời | Không có dữ liệu, thống kê chỉ một đơn vị, Core/provider lỗi | Nêu giới hạn và lý do đúng; không bịa số/“đã xong”/từ chối do tưởng tượng | Codex, 07/11/2026 | Một phần: số liệu khớp API, lời báo ghi được đối chiếu Core; còn lộ mã kỹ thuật và một ca ghi sai nhãn múi giờ trong câu sinh |
| TAS-34 | WPAS-07 | Onboarding | Người mới cấu hình từ UI, không nhập ID kỹ thuật | Có hướng dẫn, secret write-only; pairing và trạng thái đủ rõ | Codex, 07/11/2026 | Một phần: UI đăng nhập, liên kết DX-OS và hướng dẫn đạt; chờ người dùng cấu hình/pairing |
| TAS-35 | WPAS-07 | AI off | Clone sạch theo README không key/assistant; các luồng Core và SSO | Cách khởi động/hoạt động như cha, không phụ thuộc AI | — | Chưa chạy |
| TAS-36 | WPAS-07 | AI on/lỗi | Opt-in, restart; dừng control/runtime/provider/Telegram | Core vẫn phục vụ; lỗi trợ lý riêng, không rebuild frontend khi đổi config | — | Chưa chạy |
| TAS-37 | WPAS-07 | Build/release | Build image và license/SBOM/catalog/check CI bằng workflow cha | Dependency đầy đủ, version khóa; không bỏ check để đạt | Codex, 07/11/2026 | Một phần: image build, bộ cài mới/nâng cấp, license, actionlint, SBOM runtime đạt; chưa chạy release CI |
| TAS-38 | WPAS-07 | Backup/rollback | Backup/restore SQLite + key + runtime; tắt AI, quay backend cũ | Khôi phục đúng chủ; backend cũ tắt tool ghi; không rollback nghiệp vụ đã làm | Codex, 07/11/2026 | Một phần: backup/restore state/key và giải mã SSO đạt; chờ rollback backend cũ và runtime đã có hội thoại |
| TAS-39 | WPAS-08 | Hiệu năng | Hai rồi ba người đọc/giao/xử lý, cold/warm và provider lỗi | Báo p50/p95/tài nguyên/request/quota; Core không lỗi hoặc OOM vì AI | — | Chưa chạy |
| TAS-40 | WPAS-02 | Bảo mật web | CSRF, replay SSO, brute-force pairing, CORS và callback giả | Không liên kết/confirm hộ; rate limit, origin và nonce được kiểm | Codex, 07/11/2026 | Một phần: origin, JWT, replay state và gateway giấu route nội bộ đạt; chờ brute-force pairing |
| TAS-41 | WPAS-05 | Side effect | Approve có completion membership đang pending/failed | Chỉ nói quyết định đã ghi; completion chưa xong không báo đã cấp quyền | — | Chưa chạy |
| TAS-42 | WPAS-04 | Hồi quy | Formatter/build/ArchitectureTest và test Core liên quan | Bốn module và client hiện tại không bị phá, không phụ thuộc ngược | Codex, 07/11/2026 | Đạt: Maven verify, ArchitectureTest và frontend build đạt |
| TAS-43 | WPAS-05 | Explain plan | ADMIN cung cấp YAML, Core trả plan; model gợi ý apply | Chỉ giải thích plan đã lọc; không apply hoặc sửa YAML âm thầm | Codex, 07/11/2026 | Một phần: Core plan đã lọc SQL/handoff đạt; chờ phản hồi model thật |
| TAS-44 | WPAS-06 | Không dùng LLM | Tắt model, giữ runtime/kênh; phát sinh việc mới và nhắc hạn | Thẻ cố định vẫn gửi, không tốn token heartbeat; webhook cũ không đổi | — | Chưa chạy |
| TAS-45 | WPAS-08 | Dọn và nghiệm thu | Review diff, secret scan, dữ liệu giả; test tay web/Telegram | Không file thừa/secret/report/ignore; người dùng nghiệm thu trước commit/push | Codex, 07/11/2026 | Một phần: quét 82 file không phát hiện secret; chưa nghiệm thu Telegram và review cuối |

## 3. Hồi quy và điều kiện đạt

- Core off/on trợ lý giữ đăng nhập, scope, action, assign, file đính kèm, booking, webhook/outbox và apply/recovery mô hình hiện có.
- Không đổi YAML hoặc schema nghiệp vụ; request cũ không gửi precondition mới vẫn tương thích.
- Mọi ca cách ly, quyền, xác nhận, stale snapshot và tác động membership phải đạt; không nới các cổng này để demo.
- Đo và chốt ngân sách 2–3 runtime ở WPAS-01, ghi cấu hình máy trước thử tải; không tuyên bố một ngưỡng production khi chưa có số đo.
- Provider xác minh thật phải được liệt kê riêng với profile mới kiểm contract; không đánh dấu vendor chưa có key là đạt.
- Không xóa hoặc viết lại mục backlog như đã xong khi mới xử lý một phần; mục đã hoãn hoặc giới hạn chấp nhận phải giữ rõ trạng thái.
- Giới hạn nội dung/hiệu năng còn lại chỉ được chấp nhận khi có bằng chứng, mức độ và người dùng đồng ý; không đóng thay cho lỗi rò dữ liệu hoặc ghi sai người.

## 4. Bằng chứng

Lưu log đã che bí mật, ảnh thẻ, bản đo và báo cáo ngoài repository theo từng run; không mặc định dùng `report/` làm thư mục phát hành.
Tài liệu chính chỉ cập nhật kết quả tổng hợp cuối và giới hạn còn tồn tại.
Không công bố key/token, payload thật, runtime mapping, fingerprint hoặc mã từ môi trường để chứng minh kết quả.

## 5. Kiểm chứng xử lý review PR #199

Đây là các ca kiểm bổ sung cho review; kết quả và giới hạn được ghi riêng từng dòng.
Mở rộng test/script hiện có, không thêm framework hoặc file test không cần thiết.
Dùng key/token giả cho failure injection; transport thực cần runtime đã pin và môi trường tách biệt, không cần tiêu quota model cho ca SSRF.
Không bật toàn bộ stack trong lúc chỉ sửa tài liệu; khi triển khai, dùng image/cache hiện có và chỉ dựng phần cần cho từng ca.

| ID | WP | Cách kiểm | Điều kiện đạt | Trạng thái |
|---|---|---|---|---|
| TRV-01 | WPRV-02 | TAS-06: hai runtime, capability/token đúng-sai, callback chéo, volume/network và tool ngoài allowlist | Không lấy credential B từ A, không dùng danh tính A đọc/ghi B; ghi rõ giới hạn bearer token | Đạt bridge/capability và 2 supervisor thật; chưa gọi lại model |
| TRV-02 | WPRV-02 | TAS-12: lưu/probe/inference với HTTPS private IPv4/IPv6, link-local, redirect và DNS thay đổi; endpoint ghi nhận bằng key giả | Không đích bị cấm nhận request/key; compatible bị chặn nếu transport không bảo đảm | Đạt chế độ khóa compatible ở lưu/probe/start; chưa mở transport |
| TRV-03 | WPRV-03 | Backup/restore ở nơi tạm, cố tình làm snapshot lỗi sau khi dừng runtime | State phục hồi bằng đúng key; cleanup resume đúng trạng thái ban đầu; Core hoạt động | Đạt script success/failure với dependency mô phỏng; restic thật restore/decrypt synthetic đạt |
| TRV-04 | WPRV-03 | Checker toàn repo; kiểm gói Artistic/SPDX và thử dependency khác cùng license không có ngoại lệ trong dữ liệu tạm | Chỉ đúng ngoại lệ được chấp nhận; không mở ALLOWED; attribution đủ | Đạt checker toàn repo, Artistic không còn trong ALLOWED; ngoại lệ npm đúng phiên bản |
| TRV-05 | WPRV-04 | Giữ cách đọc phiên bản của PR; apply/resume/abandon/rollback/restart, held và version cũ | Hoàn lại diff cache; phiên bản và stale request vẫn đúng, tối ưu SQL đã hoãn | Đạt hoàn lại cache; custom require/role/stale và apply/rollback đúng phiên bản |
| TRV-06 | WPRV-04 | Effects thật/không biết, xác nhận membership; request cũ, require thiếu/đủ và action tự khai báo | Metadata đúng nghĩa; không hạ cổng web; giữ handlers/handlerActions, quyền và transaction cha | Đạt 74/74 process và custom require/role/handlers/handlerActions trên Core thật |
| TRV-07 | WPRV-05 | Browser off/on, nhiều lần focus và reload; đếm request feature | Không gọi lặp theo focus; reload phản ánh cấu hình mới; menu/SSO không hỏng | Đạt browser với Chromium có sẵn: 1 request/trang, focus không gọi thêm; reload nhận feature |
| TRV-08 | WPRV-05 | Render config Compose/APISIX off/on, route/SSO/monitor; thiếu biến bắt buộc; capacity ba slot | Không route trợ lý khi off, không mất route gốc, không fallback biến trong Compose hoặc lộ control port | Đạt Compose/render và on/off thật; không tái tạo Core khi on lặp |
| TRV-09 | WPRV-06 | Formatter/verify, Node test, TypeScript/build và script process/conformance nhánh cha; smoke đọc → đề xuất → xác nhận → đọc lại | Core cũ và quy trình tự khai báo đều đúng; trợ lý tắt/lỗi không chặn Core; không bỏ qua lỗi mới | Đạt 11 script Core trên DB sạch/JAR cố định, verify, Node 24 28/28, frontend/docs build |
| TRV-10 | WPRV-06 | Đọc diff docs/API/dependency/CHANGELOG/checklist, docs build/license/workflow liên quan và secret scan | Review được ánh xạ đủ, tick có bằng chứng; không secret/report/.gitignore/artifact, không xóa log cũ | Đạt license/diff/link/SPDX và Gitleaks trên file đổi; không report/.gitignore |

Ca provider thứ hai và hai–ba người đồng thời chưa có bằng chứng vẫn giữ mở tại TAS-11/TAS-39 và BACKLOG.
Kết quả TRV không thay thế các ca WPAS gốc còn thiếu hoặc quyền nghiệm thu cuối của người dùng.

## 6. Ca kiểm theo review bổ sung

Các ca dưới đây kiểm phạm vi đã chốt sau review bổ sung.

| ID | Liên quan | Cách kiểm và điều kiện đạt | Trạng thái |
|---|---|---|---|
| TRV-11 | Bug off/focus | Bật rồi off, gọi features/API lặp và focus browser: không chờ upstream đã tắt, không sinh UPSTREAM_DOWN sai; reload nhận trạng thái mới | Đạt gateway/monitor thật, 5 lượt sau off không 5xx hoặc lỗi monitor mới; browser focus đạt |
| TRV-12 | Bug HTTP | Token/Origin hợp lệ, JSON lỗi và body 40 KB: 400/413; lỗi server thật vẫn 500, không trả nội dung key; monitor không coi lỗi client là SERVER_ERROR | Đạt 400/413 qua gateway; lỗi 500 giữ nội dung chung trong Node test |
| TRV-13 | Control network | Core/JWKS/gateway hoạt động; control không TCP/DNS tới DB/store, runtime không tới mạng Core hoặc runtime khác; Core off overlay giữ hành vi | Đạt TCP/DNS thực: control tới API/SSO, không tới DB/store; runtime peer bị chặn |
| TRV-14 | Origin/schema/PATCH | Origin chính cho ghi, alias bị chặn rõ; trường owner lạ không thay chủ; PATCH full-profile có hành vi được docs mô tả; chưa liên kết 409 và auth sai 401 | Đạt Node test Origin/owner/PATCH và 409 chưa link; JWT sai vẫn 401 |
| TRV-15 | Backup capability | Bản restore synthetic có key/SQLite/slot/env được nhận diện là dữ liệu bí mật; không in token, giữ quyền file; kết luận custody đúng với phương án người dùng chốt | Đã ghi trust boundary key/capability vào docs/BACKLOG; restic restore synthetic đạt |
| TRV-16 | Phạm vi và bằng chứng | Kiểm lại theo quyết định hoãn cache/egress allowlist; mọi recreate vẫn mount JAR mới; script bị dừng giữ chưa đạt; không lấy test reviewer/bản cũ thay test diff mới | Đạt bản sao JAR/frontend cố định; các lượt sai fixture hoặc bị ngắt không tính đạt |

Lượt local mới nhất đã kiểm 28/28 test Node, backend verify, TypeScript/build và docs build.
Hai supervisor thật đã kiểm capability điều khiển chéo, tách mạng/volume và chặn start/probe compatible, chưa thay kiểm ba gateway có model hoạt động đồng thời.
Các kết quả này không đồng nghĩa đã xử lý hết các nhận xét mới.

## 7. Bằng chứng kết thúc xử lý review

Chạy trên một stack riêng, DB rỗng, dùng image/cache hiện có cùng bản sao cố định của JAR và frontend vừa build.
Không chạy Maven đóng gói lên JAR đang được container sử dụng.
Các lượt fixture bị ngắt hoặc dùng lại dữ liệu cũ không được tính vào kết quả cuối.

| Kiểm tra | Kết quả |
|---|---|
| Backend | `./mvnw verify` đạt, gồm formatter, kiến trúc và integration test database |
| Trợ lý | 28/28 test đạt trong Node 24 của image runtime đã pin |
| Hồi quy Core | api, recovery, case, change, process, booking, demo, guards, users, docs, conformance đều đạt; process 74/74 |
| Quy trình tự khai báo | Require/role, handlers/handlerActions, stale/current action, apply và rollback đúng phiên bản |
| Mạng | Control gọi API/SSO được, không TCP/DNS tới DB/store; runtime không tới peer; không Docker socket |
| Lifecycle và HTTP | On lặp không khởi động lại Core; off không còn route/502/504, monitor không thêm lỗi trợ lý; JSON sai 400, body lớn 413 |
| Lỗi khi tắt trợ lý | Sáu ca mô phỏng Docker đạt: render, tìm gateway, đọc project/network, probe thất bại và tắt thành công; lỗi trả mã khác 0 kèm hướng dẫn thử lại, giữ marker và không dừng dịch vụ |
| Browser | SSO/Core thật, 1 feature probe/trang, focus không gọi thêm; reload và menu/provider được kiểm với response trợ lý mô phỏng |
| Backup | Script success/failure với dependency mô phỏng resume đúng; restic thật restore key/capability/SQLite synthetic và giải mã đúng |
| Tuân thủ | License, docs build, liên kết, cú pháp script, diff và Gitleaks đạt |

Artifact và fixture nằm ngoài repository, không có key thật hoặc Telegram thật trong lượt review này.
Không tuyên bố full build/seed toàn bộ image từ clone sạch, nghiệm thu model thật cả ba provider hoặc tải hai–ba trợ lý đồng thời.

## 8. Đối chiếu checklist và review

| Bằng chứng | Phạm vi xác nhận |
|---|---|
| DOC-01 | Đối chiếu `00_OVERVIEW.md`, `01_PLAN.md`, `assistant/README.md`, quy ước CONTRIBUTING và quyết định API/SPDX/egress/backup/cache đã chốt với chủ dự án; kiểm liên kết và ranh giới file thay đổi |

Các mục WPAS về provider, toàn chuỗi công việc, thông báo và giao diện còn mở khi ca TAS liên quan mới đạt một phần hoặc chưa chạy.
Các mục WPRV chỉ xác nhận phạm vi xử lý review với bằng chứng TRV được dẫn ngay ở từng dòng; không đóng các ca TAS còn thiếu.
Đối với TAS-09, chỉ xác nhận giới hạn ba slot trong cấu hình/mã/tài liệu; chưa xác nhận ba gateway có model hoạt động đồng thời.

Bộ YAML kiểm đa ngành bao phủ ba họ quy trình, không cần thêm mã riêng cho lĩnh vực.
Hai ca hội thoại trên mô hình mới còn chưa đạt vì provider unavailable/timeout; kiểm Core 403 không thay kết quả hội thoại.
Giữ các ca này trong nghiệm thu model, không dùng các kết quả HTTP/CI thay thế.
