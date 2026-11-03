# Changelog

Nhật ký thay đổi của DX-OS.
Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/).

Mỗi phiên bản có một mục tóm tắt các điểm cốt lõi; mục Chưa phát hành gom những gì sau phiên bản mới nhất.

## [Unreleased]

- Lõi dynamic: tệp mô hình khai báo thực thể, trường, vai, quyền và quy trình; `dx-model` đọc và kiểm tra, báo lỗi theo dòng và cột.
- `dx-engine` áp dụng mô hình vào hệ thống rỗng (schema, bảng, ràng buộc, vai Keycloak, phiên bản, lịch sử từng bước, vai `report_ro` chỉ đọc).
- API chung `/api/v1/data/{model}/{entity}` với quyền theo vai và theo dòng, lọc, sắp xếp, phân trang, quy tắc bắt buộc, duy nhất và lựa chọn; API metadata `/api/v1/meta/models`.
- Quy trình phê duyệt một cấp trên Flowable nhúng, cùng giao dịch với việc ghi hồ sơ; trạng thái chỉ đổi qua quy trình.
- Hồ sơ lưu người quyết định và thời điểm quyết định.
- Lần áp dụng dở chỉ hoàn tất được bằng đúng tệp đã bắt đầu, và mỗi mô hình chỉ có một lần áp dụng chạy tại một thời điểm.
- Hồ sơ là khái niệm của lõi: mẫu quy trình khai báo trạng thái và hành động, cán bộ trả lại kèm ghi chú để người nộp sửa và nộp lại.
- Đơn vị khai báo trong mô hình và đồng bộ thành nhóm Keycloak; phạm vi quyền `unit` và `assigned`.
- Giao việc, hộp việc `/api/v1/inbox`, dòng lịch sử của từng bản ghi ghi trong cùng giao dịch.
- Đổi mô hình khi đã có dữ liệu: xem trước theo ba mức rủi ro, đổi tên bằng `renamed_from`, `default` cho trường bắt buộc mới, báo dòng vi phạm, xác nhận riêng khi mất dữ liệu, kế hoạch hết hiệu lực khi phiên bản đã đổi, quay về phiên bản trước.
- Lược đồ do pgschema so sánh và thay đổi; image backend kèm tệp thực thi này.
- Giao diện react-admin dựng từ metadata, gồm hộp việc, nút hành động, giao việc và dòng lịch sử.
- Stack chỉ gồm lõi và Tầng 2 (`postgres`, `keycloak`, `seaweedfs`, `backend`, `apisix`, `frontend`, `portal`, `monitor`, `clamav` qua profile); vai và cơ sở dữ liệu của Keycloak tạo bởi `deploy/postgres/init`; realm chỉ còn vai `ADMIN`.
- Các script `deploy/dev/check_api.py`, `check_recovery.py`, `check_case.py` và `check_change.py` kiểm API, khả năng phục hồi, các tính năng của hồ sơ và việc đổi mô hình.
- Họ mẫu quy trình `approval` và `work_item`; khối `process:` dùng `family`, `handler`, `allow_self_decision`, `deadline` và `after`.
- Quy tắc bất biến của quy trình nằm trong engine: từ chối và trả lại cần lý do, người nộp rút lại hồ sơ và bản ghi được giữ lại, người tạo không tự quyết định.
- Hạn xử lý theo bản ghi, hồ sơ quá hạn lên đầu hộp việc và có dòng quá hạn trong lịch sử.
- Trục sự kiện bằng bảng outbox: hành động hoàn tất chạy sau khi giao dịch xong, thử lại được, một thông điệp cho mỗi khóa; mô hình `backend/dx-engine/src/main/resources/templates/itaccess.yaml` thêm người dùng vào nhóm Keycloak sau khi duyệt.
- Kiểu trường `file`: tệp đính kèm bản ghi, lưu ở SeaweedFS, quét ClamAV khi bật, quyền theo quyền của bản ghi.
- Script `deploy/dev/check_process.py` kiểm quy trình chung.
- Họ mẫu `reservation` cho đặt chỗ theo khung giờ: khối `booking:`, kiểm tra xung đột có khóa dòng tài nguyên và sức chứa, chế độ xem lịch trong ứng dụng.
- Bảng điều khiển ở tuyến `/console` cho vai ADMIN: thư viện mẫu, trình soạn YAML Monaco theo JSON Schema, form dựng từ schema, màn hình kế hoạch, lịch sử áp dụng và quay về, sơ đồ quy trình.
- API đọc cho bảng điều khiển: `/api/v1/models/schema`, `families`, `templates` và `{model}/versions`.
- Mẫu mô hình nằm ở `backend/dx-engine/src/main/resources/templates/` kèm `index.json`: Một cửa sinh viên, hỗ trợ kỹ thuật, đặt phòng, cấp quyền, mượn thiết bị, ghi chú.
- Scripts `check_booking.py` và `check_demo.py` kiểm họ `reservation`, API của bảng điều khiển và tám tiêu chí của kịch bản trình diễn.
- Hàng đợi sự kiện: `GET /api/v1/outbox` và `POST /api/v1/outbox/{id}/retry` cho ADMIN xem thông điệp theo trạng thái cùng lỗi gần nhất và thử lại thông điệp chưa gửi, kèm tab Hàng đợi sự kiện ở bảng điều khiển.
- Thao tác hoàn tất `remove_from_group`.
- Quyền `own`, `unit` và `assigned` của thực thể con đi theo chuỗi `owner_via` nhiều cấp; tệp mô hình có chuỗi `owner_via` quay vòng bị từ chối.
- Tệp đính kèm: loại tệp nhận được đặt bằng `engine.files.allowed-extensions`, link tải luôn ở dạng đính kèm, và đối tượng trong kho bị xóa khi tệp hoặc hồ sơ bị gỡ hoặc khi giao dịch tải lên hỏng; mỗi hồ sơ giữ tối đa 20 tệp.
- Hộp việc `/api/v1/inbox` phân trang bằng `page` và `size`, trả tổng số; giao diện hộp việc có chuyển trang.
- Trường `required` kiểu text từ chối chuỗi rỗng và chuỗi chỉ có dấu cách.
- Mô hình đã lưu không còn qua được bộ kiểm bị bỏ khỏi danh sách đang chạy và ghi lỗi vào log; các mô hình khác vẫn chạy và dịch vụ vẫn khởi động.
- Hai ứng dụng nghiệp vụ và ứng dụng di động của phiên bản trước nằm ở tag `v1.1.1`.

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

[Unreleased]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.1.0...main
[1.1.0]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.3...v1.1.0
[1.0.3]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/HaUI-ImplSolution/DX-OS/releases/tag/v1.0.0

