# Changelog

Nhật ký thay đổi của DX-OS.
Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/).

Mỗi phiên bản có một mục tóm tắt các điểm cốt lõi; mục Chưa phát hành gom những gì sau phiên bản mới nhất.

## [Unreleased]

## [2.0.0] - chưa phát hành

Lõi dynamic thay cho hai ứng dụng nghiệp vụ viết tay của bản `1.1.1`.
Không có đường nâng cấp dữ liệu từ `1.1.x`; bản cũ nằm ở tag `v1.1.1`.

### Lõi và tệp mô hình

- Một tệp mô hình khai báo thực thể, trường, vai, quyền, đơn vị và quy trình; `dx-model` đọc và kiểm tra, báo lỗi theo dòng và cột.
- API chung `/api/v1/data/{model}/{entity}` với lọc, sắp xếp, phân trang, quy tắc bắt buộc, duy nhất và lựa chọn; metadata ở `/api/v1/meta/models`.
- Sửa hồ sơ nhận `version`: hồ sơ đã đổi thì trả 409 `ERR_CONCURRENT_MODIFICATION` và không ghi đè.
- Lỗi 403 `ERR_ACTION_NOT_ALLOWED` và 409 `ERR_INVALID_STATE` có `data.reason`, mã ổn định cho client; giao diện dịch lỗi theo mã này.
- Hành động của người xử lý nhận điều kiện `when`, một phép so sánh một trường với một giá trị như `amount > 5000000`; hồ sơ chưa đạt thì trả 409 `ERR_INVALID_STATE` với `data.reason` là `CONDITION_NOT_MET`, giá trị trống không đạt, nên một trạng thái rẽ nhánh theo ngưỡng được bằng hai hành động `when` loại trừ nhau; nút của hành động mờ trên giao diện và sơ đồ quy trình ghi điều kiện trên cạnh.
- Thống kê `GET /api/v1/data/{model}/{entity}/stats` cho thực thể có quy trình: tổng số, theo trạng thái, quá hạn, theo đơn vị, thời gian xử lý trung bình và tuổi hồ sơ đang mở, cùng bộ lọc và quyền như danh sách.
- Thực thể khai hạn xử lý có thêm `closedOnTime` và `closedLate` trong thống kê, đếm từ các dòng lịch sử `overdue`; trang thống kê hiện tỷ lệ hồ sơ đã đóng đúng hạn.
- Dòng `overdue` được ghi khi hồ sơ rời một hạn đã quá.
- Tên hành động của quy trình không được trùng tên một dòng lịch sử của hệ thống như `created`, `overdue` hay `due_soon`; tệp đặt tên đó bị từ chối ở bước kiểm.
- Dòng lịch sử `updated` ghi giá trị cũ và mới của từng trường đã đổi.
- Bảy mẫu mô hình ở `backend/dx-engine/src/main/resources/templates/`: một cửa sinh viên, hỗ trợ kỹ thuật, sự cố thiết bị, đặt phòng, cấp quyền, mượn thiết bị, ghi chú.
- Vai `report_ro` chỉ đọc cho công cụ báo cáo của người vận hành.

### Áp dụng và đổi mô hình

- Áp dụng vào hệ thống rỗng dựng schema `m_<mô hình>` (pgschema), bảng, ràng buộc, vai và nhóm Keycloak, phiên bản và lịch sử từng bước; áp dụng lại cùng tệp thì không làm gì, bảng hoặc Keycloak lệch thì kế hoạch ghi `repair` và sửa phần lệch.
- Đổi mô hình khi đã có dữ liệu: xem trước theo ba mức rủi ro, đổi tên bằng `renamed_from`, `default` cho trường bắt buộc mới, báo dòng vi phạm, xác nhận riêng khi mất dữ liệu, kế hoạch hết hiệu lực khi phiên bản đã đổi, quay về phiên bản trước.
- Mô hình bị giữ trong lúc áp dụng; áp dụng dừng giữa chừng thì tiếp tục (`resume`) hoặc hủy bỏ (`abandon`), kể cả sau khi backend khởi động lại.
- Mỗi mô hình có một lần áp dụng tại một thời điểm, các mô hình khác nhau áp dụng song song; yêu cầu dữ liệu, hành động quy trình và thao tác tệp giữ khóa đọc nên không hồ sơ nào lọt giữa kiểm và đổi bảng.
- Chốt chặn: không đổi hạn, quyền tự quyết, hành động hoàn tất, đặt chỗ hay trạng thái và hành động của quy trình (kể cả điều kiện `when`) khi còn hồ sơ đang xử lý; không xóa thực thể còn hồ sơ đang chạy quy trình; vai `ADMIN` của nền tảng không khai báo được.
- Trạng thái còn hồ sơ nằm ở đó không bỏ được, và trạng thái cuối còn hồ sơ đã đóng không đổi thành trạng thái thường; kế hoạch báo số dòng vi phạm.
- Đổi nhãn, vai của hành động, trường bắt buộc, các trạng thái rút lại hay danh sách vai xử lý có hiệu lực ngay với hồ sơ đang mở.
- Khai báo lại một vai còn người giữ thì kế hoạch lên mức `MIGRATION` và báo số người có lại quyền.
- Tệp mô hình lúc khởi động: `dxos start <tệp> [<tệp>...]` đưa một hay nhiều tệp vào, backend áp dụng lần lượt từng tệp nếu là tệp mới và có kế hoạch an toàn; tệp lỗi không chặn tệp sau, và mỗi tệp có một dòng kết quả.
- `dxos plan` và `dxos apply [--accept MỨC]` đổi mô hình từ dòng lệnh, đăng nhập bằng client `dxos-cli`.

### Quyền và định danh

- Quyền theo vai và theo dòng (`all`, `read`, `own`, `unit`, `assigned`); thực thể con đi theo chuỗi `owner_via` nhiều cấp, chuỗi quay vòng bị từ chối.
- Đơn vị khai trong mô hình và đồng bộ thành nhóm Keycloak.
- Mỗi mô hình có một client `dxos-m-<mô hình>` chứa vai của nó, nên hai mô hình dùng cùng tên vai không vào được dữ liệu của nhau.
- Tài khoản dịch vụ `dxos-backend-admin` có quyền `manage-clients`, `view-clients`, `query-clients` và scope `kc-admin-api`, vì Admin API của Keycloak 26.8 chỉ nhận token có audience của nó.
  Realm đã import từ trước chạy `deploy/keycloak-admin-scope.sh` (`update.sh` gọi nó) và cấp các quyền client ở Keycloak Admin Console.
- Trường kiểu `user` giữ id tài khoản Keycloak: backend từ chối id không có thật khi tạo hồ sơ hoặc khi giá trị đổi, giao diện cho chọn người theo tên qua `GET /api/v1/people` và hiện tên ở danh sách, trang chi tiết và lịch sử.
- Tab Người dùng và API `/api/v1/users` (ADMIN): tìm kiếm, vai theo từng ứng dụng, gán và gỡ vai và đơn vị, tạo người dùng với mật khẩu tạm, khóa và mở, đặt lại mật khẩu.
- Thay đổi tài khoản và việc áp dụng, quay về, tiếp tục, hủy bỏ mô hình ghi `audit_log` (`USER_*`, `MODEL_*`); `GET /api/v1/audit-logs` lọc theo đối tượng.
- Keycloak từ chối tài khoản dịch vụ thì API trả 502 `ERR_ACCOUNT_SERVICE_DENIED`, tách khỏi `ERR_ACCOUNT_SERVICE_UNAVAILABLE` khi Keycloak không trả lời.

### Quy trình và hồ sơ

- Ba họ mẫu trên Flowable nhúng, cùng giao dịch với việc ghi hồ sơ: `approval`, `work_item` và `reservation`; trạng thái chỉ đổi qua quy trình.
- Khối `process:` dùng `family`, `handler`, `states`, `actions`, `initial`, `allow_self_decision`, `deadline`, `auto_close` và `after`; khối `booking:` khai báo tài nguyên, khung giờ và sức chứa.
- Quy tắc chung của mọi quy trình: từ chối và trả lại cần lý do, người nộp sửa rồi nộp lại hoặc rút lại, người tạo không tự quyết định, hồ sơ đã giao thì chỉ người được giao xử lý.
- Hồ sơ lưu người quyết định và thời điểm quyết định.
- Giao việc, hộp việc `/api/v1/inbox` có phân trang, và dòng lịch sử của từng bản ghi ghi trong cùng giao dịch; người nhận phải nằm trong danh sách người xử lý, đọc hết các trang của Keycloak nên không giới hạn số người, và ô Giao cho tìm theo tên.
- Hạn xử lý theo bản ghi: hồ sơ quá hạn lên đầu hộp việc và có dòng quá hạn; hồ sơ có hạn dài hơn `DXOS_DUE_SOON_BEFORE` nhận thêm dòng `due_soon`.
- Hành động hoàn tất `add_to_group` và `remove_from_group` chạy qua outbox và được thử lại; trường trống thì bỏ qua và ghi dòng `completion_skipped`.
- Đặt chỗ so sức chứa với số lượt chạy cùng lúc ở mọi thời điểm của khung giờ, có khóa dòng tài nguyên.
- `auto_close` tùy chọn: hồ sơ chờ người nộp quá thời gian đó thì hệ thống làm thay họ một hành động và ghi dòng `auto_closed`; họ `work_item` viết `auto_close: 7d`, quy trình khác viết `auto_close: {after: 7d, action: <hành động>}`.
- Quy trình tự định nghĩa: `states` và `actions` thêm, ghi đè hoặc bỏ (`null`) từng phần của khung, hoặc khai báo toàn bộ khi không có `family`; `initial` chọn trạng thái đầu, `withdraw` là hành động dành riêng của người nộp.
- `handler` nhận một vai hoặc danh sách vai; mỗi hành động có thể giới hạn cho vai riêng bằng `role`, và `require` bắt buộc trường có giá trị trước khi hành động được thực hiện.
- Parser kiểm cả quy trình: hành động trỏ tới trạng thái có thật, mỗi trạng thái không phải cuối có lối ra và tới được từ trạng thái đầu, có trạng thái cuối; lỗi báo dòng và cột, kèm lựa chọn hợp lệ.
- Quy trình khác khung gốc chạy trên BPMN riêng sinh từ các trạng thái và hành động, được deploy khi áp dụng mô hình; quy trình giống khung dùng BPMN của khung.
- Hộp việc và danh sách người nhận việc tính theo vai của từng hành động.
- Metadata của thực thể có `handlerActions`, các hành động mà vai của người gọi được thực hiện.
- Người nộp chỉ rút lại được hồ sơ ở các trạng thái khai trong `withdraw`.

### Sự kiện, webhook và toàn vẹn

- Hàng đợi sự kiện bằng bảng outbox: thông điệp ghi trong giao dịch của thay đổi, giao sau đó, thử lại với thời gian chờ tăng dần và dừng sau `DXOS_OUTBOX_MAX_ATTEMPTS` lần.
  ADMIN xem và thử lại ở `GET /api/v1/outbox`, `POST /api/v1/outbox/{id}/retry` và tab Hàng đợi sự kiện.
- Webhook theo mô hình: mỗi dòng lịch sử gửi dạng JSON có chữ ký tới đích mà tệp mô hình chọn (`webhook: <tên đích>`), định dạng `json` hoặc `chat` cho Mattermost và Slack; các dòng của một hồ sơ tới đúng thứ tự.
- Thông điệp `admin.event` báo việc áp dụng mô hình và thay đổi tài khoản; mỗi thay đổi được ghi thành một dòng có `eventId`, và `GET /api/v1/events/admin` trả lại các dòng đó cho bên nhận bị lỡ, dù có đích webhook hay không.
- `GET /api/v1/events` cho bên nhận lỡ tin kéo lại theo `eventId`.
- Lịch sử hồ sơ nối thành chuỗi hash, mỗi giờ ghi một điểm neo ra kho đối tượng; `dxos history verify` và `GET /api/v1/history/verify` chỉ ra dòng bị sửa, xóa hoặc chèn.
- `examples/history-witness` cho bên nhận giữ `eventId` và `hash` rồi so với hệ thống.
- `dxos archive` xuất hồ sơ đã xong ra Parquet kèm `manifest.json`; `dxos archive check` kiểm lại.
- `GET /api/v1/inbox/stream` báo cho giao diện khi một dòng lịch sử hồ sơ ghi xong, để hộp việc, danh sách và trang chi tiết đang mở tự đọc lại; khung không kèm nội dung hồ sơ, nhịp ping 20 giây, server đóng kênh sau 10 phút, tối đa 2000 kênh cùng lúc; route gateway giữ kiểm token và cho đọc 15 phút.
- `examples/itot-simulator` giả lập thiết bị tạo hồ sơ sự cố qua API; gửi lại cùng một quan sát thì lõi từ chối.

### Tệp đính kèm

- Trường kiểu `file` lưu tệp ở SeaweedFS, quyền theo quyền của bản ghi; loại tệp nhận được đặt bằng `engine.files.allowed-extensions`, mỗi hồ sơ tối đa 20 tệp, link tải luôn ở dạng đính kèm.
- ClamAV quét tệp khi bật bằng `dxos av on`; tệp nhiễm bị từ chối với 422 `ERR_FILE_INFECTED`, ClamAV không trả lời thì 503 `ERR_SCAN_UNAVAILABLE` và tệp không được lưu.
- Tệp gỡ khỏi hồ sơ rời kho sau `DXOS_FILE_DELETE_DELAY` (mặc định 24 giờ) để bản sao lưu luôn đủ tệp; tệp của giao dịch bị hủy xóa ngay.

### Giao diện

- Giao diện react-admin dựng từ metadata, có tiếng Việt và tiếng Anh chọn bằng nút VI và EN, có chế độ sáng và tối: hộp việc, chi tiết hồ sơ dạng thẻ với dòng thời gian, lịch đặt chỗ kéo thả được, trang thống kê.
  Danh sách mỗi thực thể cho chọn và sắp xếp cột bằng nút Cột, nhớ bộ lọc và sắp xếp đã chọn, và lưu bộ lọc thành truy vấn đặt tên ở nút Thêm bộ lọc; tất cả lưu trong trình duyệt.
  Lựa chọn ngôn ngữ dùng chung với portal; đổi ngôn ngữ khi trình soạn của bảng điều khiển còn bản chưa áp dụng thì hỏi xác nhận trước khi tải lại trang.
  Backend trả thông báo tiếng Anh kèm `errorCode`; bản tiếng Việt dịch theo `shell/backendMessages.ts` và `data.reason`, bản tiếng Anh hiện thông báo của backend.
- Bảng điều khiển `/console` cho ADMIN: thư viện mẫu, trình soạn YAML (Monaco) theo JSON Schema, xem kế hoạch, áp dụng, lịch sử áp dụng kèm tên người, quay về, sơ đồ quy trình, mô hình dừng giữa chừng (Tiếp tục, Hủy bỏ) và nút "Sửa phần lệch".
  API đọc cho bảng điều khiển: `/api/v1/models/schema`, `families`, `templates` và `{model}/versions`.
- Sơ đồ quy trình vẽ từ chính tệp đang soạn: vai xử lý của từng hành động, hạn xử lý, hành động của `auto_close`, hành động `after` và đặt chỗ, cho cả quy trình tự khai báo.
  Tệp được kiểm và vẽ lại sau 5 giây không gõ; tệp sai thì báo lỗi theo dòng và cột và giữ sơ đồ đúng gần nhất.
- Điền form gồm các tab Chung, Vai, Đơn vị và Thực thể (Chung, Trường, Quyền, Quy trình), nhãn tiếng Việt; vai, trường và thực thể đã khai báo chọn từ danh sách, đổi tên một vai thì quyền và vai xử lý đổi theo.
  Tab Quy trình sửa nhãn, thêm hoặc bỏ trạng thái và hành động của khung, giao một hành động cho vai riêng, bắt buộc điền trường, đặt hành động tự đóng, hoặc chọn không dùng khung để tự khai báo.
- Mỗi phần của form có mục "Phạm vi có thể làm": hệ thống chấp nhận gì, mở rộng được gì, tự tạo được gì.
  Cạnh trình soạn YAML có bảng "Viết được gì trong tệp" với tên trạng thái và hành động của từng khung, ví dụ mở rộng quy trình và ý nghĩa từng khóa; rê chuột lên một khóa hiện giải thích.
  `POST /api/v1/models/diagram` (ADMIN) chỉ đọc tệp, không so với mô hình đang chạy.
- Chi tiết hồ sơ chỉ hiện nút hành động mà vai của người dùng được làm; hồ sơ có quy trình không có nút xóa.
- Bảng kế hoạch nêu rõ trạng thái hay hành động nào được thêm hoặc bỏ, và lý do một thay đổi bị từ chối bằng tiếng Việt.
- Menu người dùng có "Tài khoản của tôi", mở Account Console của Keycloak.
- Giao diện vừa màn hình rộng 360 px.

### Cài đặt và vận hành

- Cài bằng một lệnh: `dxos-install.sh` phát hành kèm mỗi bản tải gói, kiểm SHA-256 và cài vào `~/.dxos`; `install.sh --upgrade` nâng cấp bản đã cài, giữ `.env` và dữ liệu.
- Lệnh `dxos`: `start`, `stop`, `status`, `logs`, `admin`, `update`, `doctor`, `backup`, `av`, `plan`, `apply`, `history`, `reconcile`, `archive`, `demo`, `uninstall`; `dxos start` tạo tài khoản `admin` đầu tiên với mật khẩu tạm.
- `dxos demo` tạo bốn tài khoản thử theo vai của mô hình Một cửa sinh viên và nộp khoảng 40 hồ sơ mẫu qua API chung; mật khẩu sinh ngẫu nhiên và in một lần; chạy lại không nộp trùng; `dxos demo remove` gỡ các tài khoản thử.
- `deploy/make_env.sh` sinh `.env` với mật khẩu ngẫu nhiên; cài và chạy stack không cần Python.
- Tự phục hồi: mọi service chạy lâu có `restart: unless-stopped`, `dxos reconcile` đưa stack về đúng khai báo và chạy mỗi phút bằng systemd user timer.
- `deploy/backup.sh` sao lưu mã hóa Postgres và kho tệp; `verify` nạp bản dump vào Postgres tạm và kiểm mọi tệp đính kèm có trong kho.
- Màn hình giám sát `/monitor/` gửi cảnh báo qua `ALERT_WEBHOOK_URL_ADMIN` hoặc `ALERT_WEBHOOK_URL`.
- Stack gồm `postgres` (image chính thức `postgres:16-bookworm`), `keycloak`, `seaweedfs`, `backend`, `apisix`, `frontend`, `portal`, `monitor` và `clamav` qua profile `av`.
- Biến tùy chọn `KEYCLOAK_HOSTNAME` trong `deploy/.env.example` (đặt bằng `GATEWAY_PUBLIC_URL` khi đứng sau proxy HTTPS) bật `KC_HOSTNAME` và `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` cho Keycloak; không đặt thì Keycloak chạy như mặc định.

### Kiểm thử, CI và tài liệu

- Mười bốn script `deploy/dev/check_*.py` kiểm API, phục hồi, hồ sơ, đổi mô hình, quy trình, đặt chỗ, kịch bản trình diễn, chốt chặn, người dùng, tài liệu khớp OpenAPI, rào chắn của mọi mô hình, webhook, tệp mô hình lúc khởi động và các lệnh `dxos plan`, `apply`, `archive` cùng bộ mô phỏng IT/OT.
- Test WireMock cho client Keycloak, bộ gửi webhook và bộ dò dịch vụ; test chậm gắn `@Tag("slow")`.
- CI gồm tám workflow: `Backend`, `Frontend`, `Platform`, `Compliance`, `Dependencies`, `Docs`, `Release Images`, `Release Package`; action ghim theo SHA, mọi job có giới hạn thời gian.
  Mỗi pull request chạy hai nhóm kiểm API trên stack rỗng, một bài Playwright cho bảng điều khiển (`e2e/`), kiểm gói phát hành, SBOM khớp tệp khóa, giấy phép, tệp bắt buộc, cú pháp workflow và bí mật.
- `Release Package` chỉ phát hành tag `vX.Y.Z` nằm trên `main`, kiểm gói trước khi đăng và không ghi đè tệp đã có.
- `.github/rulesets/` lưu quy tắc của nhánh `main` và của tag `v*` để nhập vào GitHub.
- Trang tài liệu ở `website/` (Docusaurus) đăng lên GitHub Pages khi đẩy vào `main`.
- Tài liệu vận hành chia ba tệp ở `deploy/`: `OPERATIONS.md`, `MONITORING.md` và `INTEGRATIONS.md`; `docs/TESTING.md` là hướng dẫn kiểm.
- `docs/DEMO.md` ghi kịch bản trình diễn 15 phút: chuẩn bị, tài khoản thử, từng bước kèm kết quả mong đợi và cách xử lý sự cố.

### Gỡ bỏ và giấy phép

- Gỡ `bi/` (Superset), `chat/` (Mattermost), `ai/`, `data/`, trọng số và mã nhận diện bệnh lúa, luồng chẩn đoán, bộ đánh giá RAG, kho văn bản của HaUI, reranker mMARCO và Qwen; bản cũ ở tag `v1.1.1`.
  Portal chỉ còn ứng dụng chính, Keycloak Admin, màn hình giám sát và Swagger; biến `PORTAL_BI_URL` và `PORTAL_CHAT_URL` không còn dùng.
- Mã của dự án chỉ có một giấy phép, Apache-2.0.
- Mỗi bản build của ứng dụng web và của trang tài liệu kèm `third-party-licenses.txt` ở gốc, chép thông báo bản quyền của từng thư viện npm đóng gói trong đó (`frontend/scripts/third-party-licenses.mjs`).
- `.github/component-catalog.json` khai báo image và tệp tải về; `check_licenses.py` chỉ cho giấy phép OSI và chặn mục thiếu.
  `DEPENDENCIES.txt`, `NOTICE` và các tệp SBOM ghi đúng thành phần của bản phát hành; số phiên bản của backend, web và SBOM là `2.0.0`.

## [1.1.1] - 2026-10-30

- Workflow `Release Package` tạo GitHub Release cho mỗi tag `vX.Y.Z`, kèm gói `dx-os-X.Y.Z.tar.gz`, tệp `.sha256` và SBOM; ghi chú lấy từ mục tương ứng của CHANGELOG.
  Image GHCR của workflow `Release` có thêm tag theo phiên bản khi chạy trên tag.
- `deploy/install.sh` cài các file chạy sang một thư mục khác và dùng image dựng sẵn, nên stack chạy được khi không còn thư mục mã nguồn; job `install` của CI kiểm cách cài này.
- Mọi file mã nguồn có header `SPDX-License-Identifier` (`AGPL-3.0-only` trong `ai/`, `Apache-2.0` ở nơi khác); job `license` của CI kiểm header và giấy phép của từng dependency trong `sbom/`.
- `sbom/ai.cdx.json` ghi giấy phép của từng gói Python; `.github/scripts/enrich_sbom_licenses.py` bổ sung từ PyPI sau khi sinh SBOM.
- Image của Apache APISIX, PostgreSQL (pgvector), Superset, Mattermost, Ollama và Alpine ghim digest trong `deploy/docker-compose.yml`.
- Số phiên bản của backend, web và mobile khớp với phiên bản phát hành (`1.1.1`).
- Thêm `SECURITY.md`, biểu mẫu issue dạng form và `.mailmap`; `CONTRIBUTING.md` mô tả đúng luồng nhánh và quy trình phát hành.
- README có mục chạy ngoài thư mục mã nguồn và build từ mã nguồn không qua Docker; `ai/weights/README.md` ghi nguồn và SHA-256 của crop classifier.
- Tài liệu nội bộ nằm ở trang docs https://haui-implsolution.github.io/DX-OS-docs/; repository giữ `docs/TESTING.md` và `docs/images/`.
- Thư mục `.claude/` và `.codex/` không nằm trong repository.
- `COPYRIGHT.txt` ghi phiên bản của Keycloak, APISIX, Superset, Mattermost, nginx, cloudflared và thêm Alpine; image `cloudflared` ghim phiên bản và digest.

## [1.1.0] - 2026-10-30

- Bảng thành phần và giấy phép nằm ở `COPYRIGHT.txt`, trình bày bằng bảng văn bản thuần.
- Web và mobile hiển thị thông báo tiếng Việt cho lỗi `ERR_TOO_MANY_REQUESTS` và cho phản hồi `429` của gateway.
- `deploy/doctor.sh` kiểm Docker, RAM, ổ đĩa, `deploy/.env` và các cổng trước khi chạy hoặc cập nhật.
- Backend chạy trên Spring Boot 4.1.2 (Tomcat 11.0.27, Jackson 2.21.7 và 3.1.7); SBOM của backend không còn lỗi HIGH hoặc CRITICAL đã có bản vá.
- CI kiểm mọi route gateway không có token nằm trong danh sách công khai, quét SBOM đã phân giải của backend (chặn khi có lỗi đã có bản vá) và có luật phân lớp bằng ArchUnit.
- `deploy/online-check.sh` kiểm site công khai từ máy chủ (chạy bằng cron) và báo sang Mattermost khi site lỗi hoặc trở lại.
- Lưu ảnh và tệp đính kèm chạy trên SeaweedFS, kèm trang quản trị `seaweedfs-admin`.
  Bộ chạy đánh giá RAG dùng cùng image cho kho S3 của Langfuse.
  Biến cấu hình là `S3_*`, `APISIX_STORAGE_ADMIN_HOST_PORT` và `PORTAL_STORAGE_URL`.
  gRPC của filer chỉ nhận lời gọi ký bằng `S3_SECRET_KEY`, nên khóa này cần tối thiểu 16 ký tự; log của `seaweedfs` chỉ gồm cảnh báo và lỗi.
  `seaweedfs/migrate-uploads.sh` chuyển ảnh có sẵn vào bucket, chạy được trên CPU không có x86-64-v2.
- Link ảnh ký sau Cloudflare dùng `https`; portal đọc địa chỉ dịch vụ ở `/portal/config`, đường dẫn Cloudflare không cache, và chú thích thẻ hiện địa chỉ thật.
- `backup.sh` xóa snapshot cũ theo chính sách giữ cho mọi snapshot của dự án, kể cả khi danh sách thư mục sao lưu thay đổi.
- AI nạp sẵn model embedding lúc khởi động; hỏi đáp Farm về một bệnh đã nêu tên chỉ giữ nguyên nhân và cách phòng có trong nguồn, và dòng "Tên khoa học" được giữ trong evidence.
- Header `Idempotency-Key` của cả sáu endpoint tạo mới được đọc bằng `IdempotencyKey.parse` trong `dx-core-api`.
- Dependabot mở pull request cập nhật Maven, npm, GitHub Actions và image gốc của Dockerfile; Trivy quét thêm các image của bên thứ ba trong compose mỗi tuần và báo bằng cảnh báo.
- Backend giới hạn số lần gọi theo người dùng (`sub` của token) ở các lệnh tốn tài nguyên, trả `429` kèm `Retry-After`; `RATE_LIMIT_ENABLED=false` tắt khi thử tải.
- Route chẩn đoán AI của gateway chờ tới 95 giây, đủ cho chẩn đoán 50 đến 70 giây trên CPU và dưới giới hạn 100 giây của Cloudflare.

## [1.0.3] - 2026-10-29

- Sao lưu và khôi phục có mã hóa bằng `deploy/backup.sh`, có lệnh kiểm tra khôi phục và job CI chạy thử.
- Độ bền: tin chat lỗi tạm thời được gửi lại, cầu dao khi gọi AI service, mã request đi suốt từ gateway tới AI service.
- Bảo mật: xác thực hai bước và chính sách mật khẩu ở Keycloak, quét mã độc tệp tải lên bằng ClamAV, che thông tin cá nhân trong tin chat và câu hỏi gửi sang AI, container chạy không phải root, console MinIO mặc định chỉ mở trên máy chủ.
- Giám sát: cảnh báo vào kênh Ban quản trị khi một dịch vụ ngừng hoặc phục hồi, khi lỗi `5xx` dồn dập, khi một địa chỉ bị giới hạn tốc độ hoặc đăng nhập sai nhiều lần.
- Câu trả lời Qwen: Lab giữ đúng điều kiện và đối tượng của quy định; Farm trả lời được câu hỏi mô tả bệnh dài và câu "mô tả bệnh"; gỡ đường trả lời RAG legacy.
- Máy chủ có CPU yếu tắt được câu trả lời Qwen và giảm số đoạn đưa vào rerank bằng `deploy/.env`.
- Hỏi đáp Farm tìm bệnh từ triệu chứng trả tài liệu thay cho câu trả lời của Qwen; AI kiểm SHA-256 của crop classifier; mô tả triệu chứng gửi sang AI được che thông tin cá nhân.
- Vận hành và CI: sinh `deploy/.env` với mật khẩu ngẫu nhiên, quét lỗ hổng phụ thuộc bằng Trivy.
- Chatbot Farm/Lab dùng chung hybrid retrieval BM25/cosine, RRF và multilingual rerank; giữ truy xuất chẩn đoán/ticket legacy, mở rộng corpus canh tác và tổng hợp QA đa nguồn.
- Chatbot kiểm độ liên quan cosine trước rerank; truy vấn thiếu căn cứ không sinh câu trả lời.
  Cache BM25 theo metadata tài liệu, lấy top ứng viên cosine tại PostgreSQL và giữ snapshot nhất quán khi ingest đồng thời.
- Corpus Farm đã duyệt không lưu lại giá trị checksum khi promotion; fingerprint của mỗi lượt đánh giá nằm trong state ngoài Git.
- AI vẫn khởi động CV khi chưa có cache reranker; chatbot báo chưa sẵn sàng.
  Ingest corpus tổng quát rỗng thu hồi đúng loại tài liệu; evaluator đối chiếu evidence đã lọc theo policy khai báo.
- Qwen QA dùng khuôn JSON gọn, câu tổng quan Lab chọn dịch vụ đúng đối tượng; giữ giới hạn hàng đợi khi chạy đồng thời và công bố số đo mới nhất.
- Bộ chạy đánh giá so sánh bốn chiến lược trên 72 query, hiển thị ranking/điểm trên Langfuse local và đo tải search chatbot; dữ liệu đánh giá mới giữ trạng thái nháp.

- Chatbot Lab giữ thứ tự tài liệu truy xuất, từ chối yêu cầu thực hiện phê duyệt và không tự nhận thay đổi nghiệp vụ; vẫn hỗ trợ hỏi về quy định.
- Qwen giữ câu trả lời hợp lệ khi diễn đạt khác nguồn hoặc thiếu dấu chấm; xử lý mục phụ lỗi định dạng riêng, tiếp tục chặn mâu thuẫn bệnh và thông số điều trị.
- Qwen tổng hợp hỏi đáp web Farm/Lab và giải thích chẩn đoán Farm bằng khuôn cố định, có nguồn và kiểm nhất quán bệnh; ẩn phần sinh không đạt, chatbot thông báo theo lý do; lưu giải thích riêng, chỉ hiện cho nông dân sau duyệt.
- Màn hình giám sát và truy vết `/monitor/` cho quản trị viên, chạy riêng nên vẫn hoạt động khi backend dừng, giữ dữ liệu 30 ngày.
- Truy vết máy chủ công khai: log gateway kèm `X-Request-Id`, sự kiện đăng nhập của Keycloak và `audit_log` chỉ thêm.
- Giới hạn tốc độ theo IP thật ở gateway.
- Farm: báo cáo đang có lượt chẩn đoán AI chạy dở thì lượt thứ hai trả `409`.
- Bộ câu hỏi và nhãn đánh giá truy xuất RAG cho Farm/Lab, gồm 36 câu hỏi đáp và 6 lượt tham khảo chẩn đoán.
- Công cụ thu kết quả chỉ đọc, chấm Recall/nDCG bằng `ir_measures` và hiển thị câu hỏi, đoạn tài liệu, điểm trên Langfuse local.
- Thử tải endpoint truy xuất bằng k6, đo độ trễ, request/giây, tỷ lệ lỗi và tài nguyên Docker; xuất dữ liệu để kiểm tra lại.
- Bộ chạy đánh giá RAG độc lập bằng một lệnh, tự chuẩn bị Langfuse local, dùng lại cache và tự dọn sau khi xem.
- Mọi file Java và Python tự viết có dòng `SPDX-License-Identifier: Apache-2.0`.
- Tài liệu: README có mục API, CHANGELOG theo phiên bản, mật khẩu Keycloak băm Argon2id.

## [1.0.2] - 2026-10-23

- Model bệnh lá lúa DX-Farm EfficientNetV2-M (Healthy và 5 bệnh) thay detector cũ.
- Workflow `Release` phát hành image lên GHCR; `deploy/update.sh` cập nhật stack từ image đã phát hành.
- Overlay Cloudflare Tunnel cho máy chủ công khai, một biến `PUBLIC_HOST` quyết định các địa chỉ công khai, cấu hình Cloudflare được tổng hợp ở [trang docs](https://haui-implsolution.github.io/DX-OS-docs/); chạy được trên CPU ảo không có x86-64-v2.
- Farm: chẩn đoán kèm tài liệu tham khảo, nhắc khai phun chờ xác nhận, nhắc thu hoạch có giới hạn ngày, bật rule đốm nâu, thời tiết lưu theo giờ và chart thời tiết trên Superset.
- Lab: tri thức từ văn bản chính thức của HaUI kèm link tới trang gốc, cảnh báo thiết bị hỏng lặp lại, màn hình ticket và quy tắc mượn thiết bị, đặt phòng làm lại.
- Lab: kỹ thuật viên không đặt phòng hay mượn thiết bị, không ai duyệt lịch đặt phòng của chính mình; trùng mã phòng, mã tài sản hoặc mã loại sự cố trả `422` kèm câu tiếng Việt.
- Portal lấy địa chỉ BI, MinIO và chat từ cấu hình; web và mobile hiển thị tài liệu tham khảo của chẩn đoán; hộp thông báo trên mobile tải theo trang và có profile `preview` của EAS cho máy thật.

## [1.0.1] - 2026-10-15

- Farm: thời tiết theo thửa, danh sách báo cáo chưa chẩn đoán, thông báo chat kèm liên kết mở thẳng báo cáo, chart sản lượng thu hoạch.
- Mobile: thẻ thời tiết, hỏi đáp, giới hạn GPS lấy từ backend, icon và màn hình khởi động riêng của DX-Farm.
- BI: bản đồ Superset dùng nền vector tile OpenFreeMap có nhãn tiếng Việt; chart của dashboard Farm có `query_context` nên `/api/v1/chart/{id}/data` chạy được cho cả 9 chart.
- Backend ghi người tạo chẩn đoán, khóa việc chạy định kỳ giữa nhiều bản chạy; kỹ sư xem được dashboard BI.
- Webhook chat tạo lúc khởi động và backend đọc từ file; nhận ảnh nền đơn sắc.
- Tài liệu: sơ đồ kiến trúc dạng ảnh, README tiếng Anh, quy tắc ứng xử.

## [1.0.0] - 2026-10-13

Bản phát hành đầu tiên của DX-OS.

- Core dùng chung (`dx-core-api`, `dx-core`, `dx-host`): định danh và workspace, workflow, hộp duyệt, thông báo, lưu trữ, cổng AI và tri thức, bảo mật, trạng thái hệ thống.
- Hai ứng dụng chọn bằng `DXOS_APP`: DX-Farm (`app-farm`) và DX-Lab (`app-lab`).
- AI Service: nhận diện bệnh lá lúa, RAG theo namespace `farm` và `lab`, LangGraph.
- Web React dùng chung; mobile Expo cho nông dân.
- Hạ tầng Docker Compose: Keycloak, APISIX, PostgreSQL, MinIO, Superset, Mattermost, portal; E2E Farm và Lab trong CI.
- Farm: thửa và vụ mùa có trạng thái duyệt; nông dân gửi vị trí GPS có sai số tối đa 30 m; kỹ sư duyệt chẩn đoán từ ảnh và có thể yêu cầu chụp lại; ghi nhận phun thuốc tính khóa thời gian cách ly (PHI); đổi ngày thu hoạch dự kiến kèm lý do.
- Lab: ticket, mượn thiết bị và đặt phòng chạy trên workflow và hộp duyệt dùng chung của core; thống kê qua `GET /api/v1/lab-stats/summary` và 5 view `vw_lab_*` cho Superset.
- Quyền theo vai bằng `@PreAuthorize`; nông dân chỉ thấy thửa của hộ mình; quản trị viên tạo hộ kèm tài khoản Keycloak.
- Mobile đăng nhập Keycloak bằng PKCE, token lưu trong `expo-secure-store`; ảnh nén trước khi gửi; khi mất mạng, báo cáo, thửa và vụ mùa vào hàng đợi offline, mỗi mục một `Idempotency-Key`.
- Đăng nhập một lần giữa web, BI và portal, đăng xuất ở một nơi kết thúc phiên Keycloak; tài khoản chỉ có vai FARMER không vào Superset, Superset đọc dữ liệu bằng role chỉ-đọc `superset_ro`.
- Backend, Keycloak và frontend không publish cổng, mọi truy cập đi qua gateway; `deploy/demo/seed_demo.py` tạo tài khoản và dữ liệu demo.
- Tài liệu và nguồn mở: `LICENSE`, `LICENSES.md`, SBOM CycloneDX, `CONTRIBUTING.md`, mẫu issue và pull request.

[Unreleased]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v2.0.0...main
[2.0.0]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.1.1...v2.0.0
[1.1.1]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.3...v1.1.0
[1.0.3]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/HaUI-ImplSolution/DX-OS/releases/tag/v1.0.0

