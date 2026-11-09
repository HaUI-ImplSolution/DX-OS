# Trade-off và rủi ro: Trợ lý hồ sơ đa người dùng

## 1. Trade-off

| Mã | Quyết định | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|
| TO1 | Dùng lại nền agent hay tự viết | OpenClaw cho provider, channel, approval, scheduler | Tận dụng nền mở đã có; tập trung Core bridge | Dependency lớn, phải kiểm API và license bắc cầu | Vừa |
| TO2 | Một gateway nhiều session hay runtime riêng | Gateway/container theo chủ, tối đa ba ban đầu | Giảm chia sẻ credential, bộ nhớ và kênh | RAM/disk và provision tăng; container không thay thế mọi ranh giới bảo mật host | Cao |
| TO3 | Thêm module Java hay dịch vụ mở rộng | TypeScript/Fastify + SQLite riêng | Không đưa LLM vào kernel; dùng runtime OpenClaw | Thêm một dịch vụ và vòng đời release | Vừa |
| TO4 | Tự ghi hay xác nhận | Tất cả ghi có xác nhận; nhạy cảm trên web | Người dùng biết tác động; Core giữ quyết định | Thêm một lần tương tác; chưa tự động hóa toàn trình | Thấp |
| TO5 | Poll hay thêm event feed Core | Poll phân trang theo chủ, có ngân sách | Không sửa outbox/webhook/quyền feed | Có độ trễ, tăng request; chỉ phù hợp quy mô ban đầu | Vừa |
| TO6 | Đọc lại rồi ghi hay precondition Core | Thêm version tùy chọn vào API action/assign | Chặn race trong transaction, giữ client cũ | Có thay đổi hẹp trong engine và test hồi quy | Vừa |
| TO7 | Provider tự host hay API ngoài | Nhiều API do từng người cấu hình | Không tải LLM/GPU, đổi nhà cung cấp | Dữ liệu ra ngoài, quota, Internet và chi phí; không phải Open Source AI | Thấp |
| TO8 | Bot chung hay bot riêng | Bot riêng mỗi chủ ở đợt này | Dễ cách ly và sở hữu token | Người dùng phải tạo bot/pairing, onboarding dài hơn | Vừa |

Các lựa chọn kỹ thuật là đề xuất để người dùng review; số người và nguyên tắc ghi có xác nhận đã được chốt.
Không dùng tên session hoặc tuyên bố của provider làm bằng chứng cách ly/an toàn.
Chính sách dependency mở áp dụng cả runtime/build/test; API LLM bên ngoài là lựa chọn tùy chọn đã được người dùng yêu cầu, không phải thành phần mô hình mở của bản phát hành Core.
Chỉ dẫn `COPYRIGHT.txt` trong delta được ánh xạ sang `DEPENDENCIES.txt`, `NOTICE` và SBOM theo nhánh cha hiện tại; không tạo lại file đã bỏ.

## 2. Rủi ro

| Mã | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Rò credential/transcript giữa chủ | Vừa | Cao | Runtime/volume/token riêng; owner server-side, test chéo | Runtime nhận bridge token khác chủ hoặc log chứa payload | Dừng đa người dùng, không dùng chung gateway để lách |
| R2 | Ghi sai vì stale state/model hoặc quyền đã đổi | Vừa | Cao | Version tại Core, quyền/actor cuối giao dịch, proposal TTL | Kết quả khác snapshot xác nhận | Hủy proposal, đọc lại; không retry bỏ điều kiện |
| R3 | Prompt injection trong hồ sơ | Cao | Cao | Allowlist tool, xác nhận ngoài model, không shell/HTTP/SQL tự do | Model gọi tool ngoài phạm vi hoặc tự nhận đã duyệt | Từ chối ở bridge, giữ luồng đọc xác định |
| R4 | OpenClaw nặng hoặc phụ thuộc không đạt policy | Vừa | Cao | G0 đọc LICENSE upstream/artifact/bundled cả build/test trước manifest, đo 2–3 runtime | OOM/swap mạnh hoặc license không được phép | Chặn mở rộng; báo phương án khác trước thay nền |
| R5 | API gọi lặp sau timeout/crash | Vừa | Cao | Ledger claim nguyên tử; trạng thái uncertain, không retry mù | Không có receipt nhưng Core có event | Đối chiếu bằng đọc/lịch sử; người dùng quyết định tiếp |
| R6 | Dữ liệu ra ngoài provider hoặc SSRF | Vừa | Cao | Đồng ý, tối thiểu payload, exact origin, kiểm DNS/redirect và egress | Key đi sai host hoặc payload thừa | Tắt profile; không tự chuyển vendor |
| R7 | Telegram lặp/mất tin, token bị thay | Vừa | Vừa | Binding xác minh, ledger, backoff, nonce và re-read quyền | 401/429, delivery uncertain | Hiện tình trạng trên web, hướng dẫn liên kết lại |
| R8 | ADMIN bị hiểu thành quyền nghiệp vụ toàn hệ thống | Vừa | Cao | Tách tool quản trị và business JWT; kiểm C không có vai | Đọc hồ sơ bằng tài khoản quản trị nền tảng | Tắt tool vi phạm, không cấp service account vượt quyền |
| R9 | Membership chưa hoàn tất nhưng báo đã cấp quyền | Vừa | Cao | Summary effects, xác nhận web, phân biệt quyết định/outbox | Completion pending/failed nhưng thông báo thành công toàn bộ | Handoff quản trị theo UI hiện có |
| R10 | Poll bỏ trang, bão tin hoặc quá tải Core | Vừa | Vừa | Paging, baseline, budget, ledger, đo tải | Request tăng mạnh hoặc bỏ việc nằm sau trang đầu | Giảm nhịp/thông báo, giữ Core ưu tiên |
| R11 | Mất master key hoặc backup không nhất quán | Vừa | Cao | Backup DB/key/runtime riêng, quyền file, restore test | DB có nhưng secret không đọc được | Liên kết lại; không ảnh hưởng DB nghiệp vụ |
| R12 | SDK/model đổi cách gọi hoặc quota | Cao | Vừa | Khóa bản, capability probe và ma trận model thật | Danh sách model có nhưng tool call lỗi | Báo model chưa hỗ trợ; chọn profile khác do người dùng |
| R13 | Phạm vi phình hoặc mở lại ý tưởng đã loại | Vừa | Vừa | Allowlist file/API, đối chiếu quyết định đã loại, chốt WP, review diff | Xuất hiện RAG/MCP, module cũ, CSV/fulltext/Core subscription | Dừng phần ngoài domain, không ghép vào trợ lý |
| R14 | Runtime tắt làm mất nghe Telegram | Vừa | Vừa | Runtime bật khi dùng bot/thông báo; không hứa đánh thức tự động | Người dùng nhắn lúc slot đã off | UI báo off, người dùng bật lại; không thêm bot poller thứ hai |

## 3. Giới hạn chấp nhận ở bản đầu

- Một host, một control process, SQLite riêng và tối đa ba runtime; không tuyên bố HA hoặc scale đa máy.
- Một bot riêng mỗi người, Telegram private chat; không bot chung, group chat hoặc kênh khác.
- Gợi ý điều phối theo dữ liệu đang được phép thấy, không tối ưu năng lực toàn tổ chức.
- Không fulltext/RAG, đọc tệp, sinh YAML, ghi hàng loạt hoặc tự thực hiện quyết định theo lịch.
- Không exactly-once xuyên Core/Telegram; kết quả không rõ được giữ và đối chiếu thay vì gửi lại mù.
- Quyền theo trường chưa có ở Core; chỉ gửi dữ liệu cần cho tác vụ và có thông báo/đồng ý, không quảng cáo bộ lọc AI là field-level ACL.
- Không nhập tự động secret/state PoC; cấu hình lại và nghiệm thu trên bản tích hợp.

### Backlog: Không gian hội thoại theo mô hình

Hoãn giao diện chuyển không gian, lưu/khôi phục hội thoại riêng từng mô hình và thống kê chi phí theo mô hình đến sau khi hoàn thiện trợ lý.
Đợt hiện tại vẫn bắt buộc xác định mô hình đích của mỗi công cụ/proposal, kiểm quyền bằng API và không dùng dữ liệu mô hình khác làm căn cứ trả lời.
Hội thoại hiện thuộc tài khoản, chưa tách lịch sử theo mô hình; người dùng cần chỉ rõ mô hình khi đổi ngữ cảnh.
Tool và proposal luôn mang mô hình đích, lấy lại dữ liệu/quyền từ Core; không coi lịch sử hội thoại là bằng chứng quyền hoặc trạng thái.
Không tạo database hoặc runtime mới cho từng mô hình trong đợt này.

## 4. Điều kiện dừng và rollback

Dừng triển khai nếu G0 không đạt, không chứng minh được cách ly ba chủ, không giữ được xác nhận ngoài model, hoặc precondition không được kiểm nguyên tử ở Core.
Dừng công cụ ghi khi API Core chưa hỗ trợ phiên bản hoặc metadata tác động chưa đủ.
Lỗi rò dữ liệu, ghi sai người và mất Core không được hạ mức để đóng checklist.

Rollback bằng tắt trợ lý, thu route/client tùy chọn và giữ dữ liệu theo chính sách backup.
Không xóa hồ sơ, process/event hoặc đảo ngược quyết định người dùng đã xác nhận.
Giữ SQLite/key/runtime để khôi phục có kiểm soát; purge là thao tác riêng có xác nhận, không gộp vào off/down.

## 5. Tác động ngoài đợt này

Phần Core chỉ có điều kiện ghi và metadata trung tính; quy trình, YAML và bốn module không đổi hướng kiến trúc.
Deploy/CI có mở rộng vì đây là gói tùy chọn mới, không thể tuyên bố tích hợp production mà không đụng hạ tầng.
Mở rộng nhiều người hơn, bot chung, provider tự host, kênh mới và event subscription cần kế hoạch riêng dựa trên số đo đợt này.

## 6. Rủi ro cần chặn khi xử lý review PR #199

| Rủi ro | Biện pháp và điều kiện |
|---|---|
| Tối ưu cache mở rộng thay đổi Core | Đã hoãn và hoàn lại diff cache; giữ kiểm phiên bản hiện có, tối ưu riêng sau khi có nhu cầu |
| Làm mất require/handlers/handlerActions vừa được cha bổ sung | Đối chiếu ba vùng merge và kiểm quy trình tự khai báo trong TRV-06/09 |
| Allowlist endpoint nhưng redirect/DNS vẫn gửi key sai đích | Kiểm transport probe/inference thật; chưa bảo đảm thì chặn compatible, không chỉ sửa thông báo |
| Token/runtime chéo chủ | Kiểm credential isolation và capability thật; lỗi rò dữ liệu hoặc ghi sai chủ chặn merge |
| Người có quyền giải mã backup cũng có master key | Ghi rõ trust boundary; hạn chế quyền backup, giữ mật khẩu restic ngoài bản sao; không tuyên bố bảo vệ khỏi quản trị backup |
| Dừng trợ lý để backup làm gián đoạn Telegram/nhắc hạn | Ghi thời gian gián đoạn và kiểm resume khi lỗi; không hứa xử lý realtime trong thời gian dừng |
| Thay cách dựng gateway làm ảnh hưởng Core khi AI tắt | Một generator có route tùy chọn, kiểm off/on và route SSO/monitor; rollback thay đổi cấu hình nếu lỗi |
| Đánh dấu toàn phase hoàn tất chỉ vì sửa xong review | Checklist gốc và TAS còn thiếu giữ mở; bảng phản hồi review không thay nghiệm thu |

Phương án hiện chọn cho backup là mô tả đúng quyền của người giữ credential restic và giữ khả năng phục hồi hiện tại.
Tách custody master key khỏi backup chưa nằm trong đợt này; nếu cần phải chốt quy trình mất khóa/khôi phục trước khi triển khai.

## 7. Giới hạn và quyết định sau review bổ sung

- Egress tự do: giới hạn tool không thay firewall; hoãn proxy/allowlist đến trước dùng thật và giữ compatible khóa theo phạm vi bản thử đã chốt.
- Control cùng mạng DB: đã duyệt sửa hẹp trong overlay thay vì chỉ đổi câu README; cần thêm mạng ingress cho APISIX khi bỏ default.
- Backup có capability: người giải mã backup còn có thể giả danh runtime; bản thử giữ trust boundary hiện tại; custody/rotation ghi BACKLOG trước dùng thật.
- Origin/PATCH/schema: giữ hợp đồng hiện tại và mô tả đúng, không tự mở nhiều origin hoặc cập nhật profile từng trường.
- Cache phiên bản: lợi ích đo được thấp, đã duyệt hoãn và hoàn lại riêng diff tối ưu; diff cache local đã hoàn lại; kiểm phiên bản của PR gốc giữ nguyên.

Các giới hạn egress/backup được chấp nhận riêng cho bản thử có kiểm soát.
Giữ Gemini, OpenAI và Anthropic qua endpoint cố định; chưa tuyên bố đã nghiệm thu cả ba bằng key thật.
Hai bug HTTP và route off vẫn là việc cần giải quyết trước merge.
