# Việc tồn đọng

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của DX-OS, DX-Farm và DX-Lab.
Làm xong một mục thì xóa dòng đó; không ghi lại diễn biến, chỉ ghi trạng thái hiện tại.

## 1. Trước khi nộp bài

| Việc | Ghi chú |
|---|---|
| Báo cáo kỹ thuật 8-10 trang, slide tối đa 12 | Có dàn ý ở [`SUBMISSION.md`](./SUBMISSION.md) mục 3 |
| Video demo 3-5 phút, rehearsal ít nhất một lần | Kịch bản ở [`SUBMISSION.md`](./SUBMISSION.md) mục 2 |
| Tạo vài issue mẫu trên GitHub | Issues đã bật, hiện chưa có issue nào; lấy từ mục 5 và 6 của tài liệu này |
| Chụp ảnh giao diện hai chế độ (Farm, Lab) và màn hình giám sát lưu vào repository cho hồ sơ | Chưa có; che IP và email thật khi chụp màn hình giám sát |
| Chốt ngày hạn nộp thật | Chưa chốt |

## 2. Việc cần sửa trong mã và cấu hình

| Việc | Phạm vi |
|---|---|
| Hỏi đáp Farm có thể nêu ý không có trong nguồn | Câu hỏi về một bệnh đã nêu tên vẫn có thể nhận nguyên nhân hoặc cách phòng không có trong nguồn |
| Ảnh cũ trên VPS chưa nằm trong bucket SeaweedFS | Bucket `dxos-uploads` được tạo mới khi backend khởi động; ảnh cũ còn ở `deploy/data/minio` (khoảng 364 KB) và chưa chạy `seaweedfs/migrate-uploads.sh`; ảnh của báo cáo cũ (ví dụ báo cáo #1) trả 404 và không hiện trên màn hình duyệt yêu cầu |
| Link ảnh báo cáo ký với `http://khuongdev.io.vn` | Backend dựng link theo scheme `http` dù trang chạy qua HTTPS; trình duyệt tự nâng lên HTTPS nên chữ ký vẫn khớp, còn request `http` thật nhận 503; client không tự nâng (app mobile) chưa kiểm |
| Cloudflare cache `/portal/config.js` 4 giờ | Origin trả đúng khóa `storage` nhưng edge gửi `cache-control: max-age=14400`, trình duyệt giữ bản cũ nên link "Quản trị lưu ảnh" trên portal hiện `https://khuongdev.io.vn:9082/` thay vì `files.`; cần Cache Rule bỏ qua cache cho `/portal/config.js` hoặc xóa cache sau mỗi lần `update.sh`; chú thích thẻ portal vẫn ghi `localhost:9081` và `localhost:8065`, kiểm lại sau khi cache hết hạn |
| Hỏi đáp tri thức Farm chậm ở lần gọi đầu sau cập nhật | Câu "Làm sao nhận biết lúa bị khô vằn?" mất hơn 8 giây (chưa đo chính xác) khi `AI_ANSWER_GENERATION=false`; chưa biết do AI vừa khởi động lại hay do tải |
| Log SeaweedFS khi khởi động có cảnh báo | `IAM gRPC service on filer is UNAUTHENTICATED`, `FILER LOCK: Error ... no lock server found`, `raft.Server: Not current leader`; chưa rõ cảnh báo nào chỉ là nhiễu lúc khởi động, cần xác nhận cổng filer không ra ngoài mạng compose |
| `doctor.sh` cảnh báo dung lượng đĩa trên VPS | Trống 39 GiB, khuyến nghị 40 GiB; theo dõi dung lượng khi dữ liệu và ảnh Docker tăng |

### Ngữ cảnh cho các việc về lưu ảnh và portal trên VPS

Phần này đủ để làm tiếp mà không cần lịch sử trò chuyện.
Các giả thuyết đánh dấu "chưa kiểm" chưa được xác nhận bằng log hay mã.

**Trạng thái VPS khi ghi**

- VPS chạy ảnh `latest` build từ `main` (merge #147); mọi container healthy, `deploy/doctor.sh` báo 0 lỗi và 1 cảnh báo đĩa.
- `deploy/.env` của VPS dùng tên biến mới (`S3_*`, `PORTAL_STORAGE_URL`, `APISIX_STORAGE_ADMIN_HOST_PORT`); bản trước khi đổi nằm ở `deploy/.env.bak-before-seaweedfs` cũng trên VPS.
- VPS giữ biến riêng `AI_ANSWER_GENERATION=false` và `AI_CHAT_CANDIDATE_COUNT=10`, nên không chép đè `.env` từ máy khác; đổi tên biến tại chỗ.
- `COMPOSE_FILE=docker-compose.yml:docker-compose.cloudflared.yml` nằm trong `.env`, nên chạy `docker compose` trong `deploy/` là đủ.
- Cập nhật: chạy workflow `Release` (workflow_dispatch) cho `main`, rồi trên VPS `git pull --ff-only` và `cd deploy && ./update.sh latest`.
- Cấu hình Cloudflare (tunnel, Access, Transform Rule) ở [`CLOUDFLARE.md`](./CLOUDFLARE.md); Cloudflare chặn User-Agent mặc định của `urllib` bằng 403.
- Đã kiểm bằng Chrome với tài khoản quản trị Keycloak; chưa có phiên tài khoản nông dân.

**1. Link ảnh báo cáo ký bằng `http://`**

- Biểu hiện: `src` của ảnh trên màn "Yêu cầu" (chi tiết báo cáo) bắt đầu bằng `http://khuongdev.io.vn/dxos-uploads/<khóa>.png?X-Amz-...`.
  Trình duyệt tự nâng lên HTTPS và nhận 404; request `http` thật nhận 503.
- Mã: `StorageService.requestBaseUrl()` trong `backend/dx-core/src/main/java/HaUI/ImplSolution/dxos/core/storage/StorageService.java` lấy host từ header `Host` và chỉ dùng `https` khi `X-Forwarded-Proto` bằng `https`, còn lại là `http`.
- Chưa kiểm: backend nhận `X-Forwarded-Proto: http` vì APISIX hoặc `cloudflared` ghi lại header Cloudflare đã gửi (đã có Transform Rule `X-Forwarded-Port` làm tiền lệ, xem `CLOUDFLARE.md`).
- Hướng dự kiến: xem header backend thật sự nhận (log tạm hoặc `curl -H` từ trong mạng compose); nếu bị ghi đè thì thêm Transform Rule `X-Forwarded-Proto: https` hoặc cho APISIX giữ header; hoặc ưu tiên `storage.public-url` khi nó là HTTPS.
- Xong khi: `img.src` là `https://`, ảnh mới upload hiện ở web, app mobile tải được, network không còn request `http://`.

**2. Cloudflare cache `/portal/config.js`**

- Biểu hiện: `window.PORTAL_URLS` trên `https://khuongdev.io.vn/portal/` có khóa `minio` (bản cũ); thẻ "Quản trị lưu ảnh" ra `https://khuongdev.io.vn:9082/`.
- Kiểm: `curl -sI -A "Mozilla/5.0" https://khuongdev.io.vn/portal/config.js` trả `cache-control: max-age=14400`.
  Origin (`docker compose exec portal wget -qO- http://localhost/config.js` trên VPS) trả khóa `storage`.
- Mã: `portal/nginx.conf` (location `= /config.js`, có `Cache-Control: no-cache`) và `portal/index.html` (đọc `window.PORTAL_URLS` quanh dòng 479).
- Hướng dự kiến: Cache Rule cho đường dẫn `/portal/config.js` (cân nhắc cả `/portal/index.html`) đặt Bypass cache hoặc tôn trọng header của origin; ghi rule vào `CLOUDFLARE.md`.
- Xong khi: header không còn `max-age=14400` và link ra `https://files.khuongdev.io.vn`; kiểm lại chú thích thẻ portal đang ghi `localhost:9081` và `localhost:8065`.

**3. Ảnh cũ ngoài bucket SeaweedFS**

- Biểu hiện: ảnh báo cáo #1 (khóa `498540d9-544c-4e09-81a9-8679ee7e5046.png`) trả 404; các báo cáo cũ khác (#2, #5, #6) chưa mở, mọi ảnh upload trước lần cập nhật đều không có trong bucket mới.
- Dữ liệu cũ: `deploy/data/minio/dxos-uploads/` trên VPS có 6 ảnh PNG ở định dạng nội bộ của MinIO (mỗi khóa là một thư mục chứa `xl.meta`), nên không thể chép thẳng vào bucket.
- Image MinIO cũ còn trên VPS: `ghcr.io/haui-implsolution/dxos-minio:latest` (và `sha-1bc2276`).
- Cách chuyển: chạy tạm image đó trên `data/minio` với khóa MinIO trong `.env.bak-before-seaweedfs`, tải ảnh ra một thư mục bằng `aws s3 sync`, rồi chạy `seaweedfs/migrate-uploads.sh <thư-mục>` (xem `seaweedfs/README.md`, mục "Chuyển ảnh có sẵn").
- Chọn khác: bỏ 6 ảnh demo, khi đó báo cáo cũ cần ảnh mới hoặc xóa.
- Dọn sau khi xong: `deploy/data/minio` và image `dxos-minio` trên VPS.

**4. Cảnh báo trong log SeaweedFS lúc khởi động**

- Xem: `docker compose logs seaweedfs` ở lần khởi động đầu; gồm `IAM gRPC service on filer is UNAUTHENTICATED`, `FILER LOCK: Error ... no lock server found` và `raft.Server: Not current leader`.
- Mã: service `seaweedfs` trong `deploy/docker-compose.yml` (`weed server -s3 -filer.disableHttp`, không publish cổng ra host).
- Chưa kiểm: hai dòng sau là nhiễu của lần bầu leader đầu; cảnh báo IAM chỉ có hại nếu cổng filer gRPC (18888) mở ra ngoài mạng compose, hiện không có `ports:`.
- Xong khi: khởi động lại `seaweedfs` mà log không còn cảnh báo lặp, và xác nhận bằng `docker compose port seaweedfs 18888` rằng không có cổng nào được publish.

**5. Hỏi đáp tri thức chậm ở lần gọi đầu**

- Biểu hiện: `POST /api/v1/knowledge/ask?includeAnswer=true` với câu "Làm sao nhận biết lúa bị khô vằn?" mất hơn 8 giây (khoảng 8 đến 18 giây), khi AI vừa khởi động lại sau `update.sh`.
- Đo lại: gọi cùng câu ba lần liên tiếp và ghi thời gian từng lần; mục 4 nêu mức khoảng 5 giây khi `AI_ANSWER_GENERATION=false`.
- Chậm kéo dài thì xem `AI_CHAT_CANDIDATE_COUNT` và hàng đợi rerank CPU (`ai/README.md`); gateway chờ tối đa 60 giây.

**6. Việc cần tài khoản nông dân**

- Đăng nhập app Farm hoặc app mobile bằng tài khoản `FARMER`, upload ảnh báo cáo, rồi mở ảnh vừa tải (kết hợp việc 1).
- Đăng nhập `https://files.khuongdev.io.vn` (SeaweedFS Admin, sau Cloudflare Access) bằng `S3_ACCESS_KEY` và `S3_SECRET_KEY` trong `deploy/.env` của VPS; không ghi giá trị vào repo.
- Chạy `deploy/backup.sh` rồi `verify`, kiểm thư mục `data/seaweedfs` có trong bản sao lưu.
- Thử giới hạn gọi API theo người dùng bằng một tài khoản thật qua Cloudflare, xem mục 3.

## 3. Chưa kiểm thử

| Việc | Ghi chú |
|---|---|
| Bên thứ ba tái lập đánh giá RAG | Cần người đánh giá độc lập kiểm tra nhãn và tính lại điểm từ qrels/run; cách chạy ở [`ai/README.md`](../ai/README.md#đánh-giá-truy-xuất-rag) |
| Chấm nội dung Qwen trên 36 câu hỏi và 6 chủ đề chẩn đoán | Chưa có bảng chấm trọng tâm, bám nguồn và dễ đọc do người duyệt xác nhận; tỷ lệ `generated` không phải độ đúng nội dung |
| Chữ lớn trên mobile native | Đã typecheck, kiểm render và nghiệm thu chức năng; chưa đo riêng bố cục khi tăng cỡ chữ trên máy thật |
| Khởi động bản Qwen từ clone sạch | Đã kiểm bằng image/cache và các cặp phiên bản; chưa chạy lại full build/seed toàn stack từ clone sạch |
| `backup.sh` với kho sao lưu từ xa | Đã chạy sao lưu và `verify` trên VPS với dữ liệu thật vào kho cục bộ; chưa thử `BACKUP_REPOSITORY` từ xa |
| Cầu dao AI với tải thật | Đã thử khi AI dừng; chưa thử khi AI chậm vì tải, lúc đó lỗi đọc quá hạn cũng tính là lỗi dịch vụ |
| Lưu ảnh SeaweedFS qua Cloudflare trên VPS | Đã kiểm trên Chrome với tài khoản quản trị: app Farm (4 tab), hỏi đáp, màn hình giám sát (10/10 dịch vụ), BI qua SSO và trang đăng nhập `files.khuongdev.io.vn` đều chạy; chưa thử upload ảnh mới bằng tài khoản nông dân và mở link ảnh vừa tải, đăng nhập SeaweedFS Admin, `backup.sh` với `data/seaweedfs` |
| Giới hạn gọi API theo người dùng trên VPS | Chưa thử với tài khoản thật qua Cloudflare, nơi `X-Forwarded-For` và địa chỉ client do Cloudflare đặt |

## 4. Giới hạn của AI và dữ liệu

| Việc | Ghi chú |
|---|---|
| Model bệnh chưa được đánh giá độc lập ngoài ruộng | Accuracy 89,43% trên 1.466 ảnh theo người huấn luyện, xem [`AI_EVALUATION.md`](./AI_EVALUATION.md); cần ít nhất 100 ảnh ruộng thật có nhãn kỹ sư để đo toàn pipeline và ngưỡng 0.70 |
| Crop classifier nhận 6/15 ảnh ngô và 15/15 ảnh mía là lúa, chặn 8/50 ảnh lúa nền ruộng | Classifier 10 lớp không có lớp ngô, mía, cỏ; cần thay classifier hoặc thêm dữ liệu ngoài miền; kỹ sư vẫn chốt bệnh |
| Ngưỡng độ nét mới thử trên ảnh công khai và ảnh mờ nhân tạo | Chưa có ảnh mờ thật ngoài ruộng; chưa tách vùng lá |
| Chẩn đoán AI chạy trên CPU mất khoảng 50 giây mỗi ảnh | Đo trên máy chủ 8 nhân; chưa có GPU hay mô hình nhỏ hơn để rút ngắn; lần gọi đầu sau khi khởi động lại AI nạp mô hình và có thể vượt 60 giây chờ của gateway, nhận 504 (kết quả vẫn được tạo) |
| Qwen tắt trên VPS | CPU ảo chỉ có SSE2/SSE3 nên Qwen đọc prompt khoảng 2 token/giây; VPS đặt `AI_ANSWER_GENERATION=false` và `AI_CHAT_CANDIDATE_COUNT=10`, chatbot trả tài liệu nguyên văn trong khoảng 5 giây. Bật lại Qwen cần CPU có AVX2 |
| Nộp báo cáo cần AI service chạy | Chưa có đường nhận ảnh chưa kiểm khi AI dừng; hàng đợi offline thử lại |
| Chưa có mô hình nhận diện sâu hại | Classifier nhận Healthy và 5 bệnh lá |
| Hoàn thiện coverage tri thức canh tác Farm | Đã có 7 tài liệu tổng quát; trang tập huấn còn chủ yếu mục lục, cần nội dung tài liệu con và nguồn cho các chủ đề còn thiếu |
| Rà nhãn RAG tường minh cho toàn bộ chunk | Chủ dự án đã duyệt bộ câu hỏi và nhãn pilot; qrels hiện chỉ có nhãn dương. Cần rà đủ 0/1/2 trước khi chuyển trạng thái `reviewed`, xem [`data/rag/evaluation/README.md`](../data/rag/evaluation/README.md) |
| Đánh giá chatbot hybrid mở rộng | Rà nhãn 30 câu bổ sung, tách bộ hiệu chỉnh và bộ kiểm tra độc lập. Câu hỏi gián tiếp về mượn tài liệu còn bị lọc ở ngưỡng cosine 0,40; độ bao phủ câu tổng quan Farm cần cải thiện |
| Đánh giá truy xuất trên corpus lớn | Chưa đo gần giới hạn 20.000 đoạn mỗi chỉ mục BM25; số liệu hiện có trên 244 đoạn, với 1/2/5 người dùng đồng thời |
| Đo đầy đủ tải chatbot hybrid | Mới đo rút gọn 12 lượt, 1.369 request; cần chạy đủ 36 lượt theo cấu hình mặc định và đo riêng nhóm câu hỏi bị từ chối |
| Truy xuất legacy dựa trên MiniLM đa ngữ | Diagnosis/ticket vẫn dùng cosine và ngưỡng legacy; diễn đạt gián tiếp còn phụ thuộc embedding, không có reranker như chatbot |
| Luật phân loại của Lab là luật tay | Chưa đo độ chính xác trên dữ liệu thật |
| Kho tri thức Lab chỉ có 12 bài từ 9 văn bản công khai của HaUI | Chưa có bài về máy in, VPN, quy chế phòng máy của khoa, đặt phòng máy; sổ tay LMS là bản năm học 2019-2020 |
| Gợi ý tri thức ticket Lab chưa có hybrid/gate chatbot | Ticket vẫn dùng retrieve legacy; đoạn gần nghĩa chưa chắc đúng sự cố. Chưa đánh giá riêng độ phù hợp trên dữ liệu ticket thật |
| Snapshot thời tiết chỉ có 5 điểm ĐBSCL, chụp ngày 07/10/2026 | Chụp lại nếu cần dữ liệu dự phòng mới hơn |
| Giới hạn tốc độ ở gateway tính theo IP và ở backend theo người dùng (60 câu hỏi tri thức, 60 lần tạo ticket, 30 lần upload ảnh, chẩn đoán AI, đặt phòng hoặc đính kèm, 120 lượt đăng nhập, 600 lượt API mỗi phút) là số tạm; chưa thử tải | Superset dùng SQLite cho metadata, AI service là một tiến trình; chatbot có hàng đợi rerank CPU giới hạn |

## 5. Chức năng chưa có

| Việc | Ghi chú |
|---|---|
| Chặn SQLi và XSS chung ở Cloudflare | Cần gói trả phí; gói Free chỉ có managed ruleset chặn khai thác đã biết, Backend dùng truy vấn tham số hóa |
| Xóa audit theo thời hạn | Bảng `audit_log` chỉ thêm, chưa có cách xóa theo thời hạn; theo dõi dung lượng bảng |
| Chạy `backup.sh` theo lịch và chép kho ra ngoài máy chủ | Có script, `verify` và job CI; lịch cron, kho từ xa và diễn tập khôi phục định kỳ do người vận hành đặt |
| Cảnh báo đăng nhập sai theo tài khoản | Đã có cảnh báo theo địa chỉ; sự kiện `LOGIN_ERROR` của Keycloak không ghi tên tài khoản (chỉ `user_id`), nên cần thêm `user_id` vào view `vw_auth_events` |
| Metric tài nguyên (CPU, RAM, đĩa) và phân vị độ trễ | Màn hình giám sát chỉ có sự kiện bất thường và trạng thái dịch vụ; thêm Prometheus nếu cần |
| Gộp lớp gửi Mattermost của `monitor` với `ChatWebhook` của core | `dx-monitor` không được phụ thuộc core nên hai nơi cùng đọc webhook; cảnh báo `ACCESS_DENIED` theo địa chỉ vẫn do `AuditAlertJob` của backend gửi |
| Phân trang tra cứu chi tiết ở phía máy chủ | Hiện lấy tối đa 200 bản ghi mới nhất rồi chia trang trong trình duyệt |
| Tên chức năng ở biểu đồ lỗi lấy từ danh sách cố định trong `monitor.js` | Đường dẫn ngoài danh sách hiện là "Chức năng khác"; thêm app mới thì bổ sung danh sách |
| Cảnh báo `monitor` không nhớ điều kiện đã báo sau khi khởi động lại | Khởi động lại trong lúc điều kiện còn thì tin được gửi lại; bộ nhớ chống lặp nằm trong tiến trình |
| Cảnh báo khi chính `monitor` ngừng | Cần một kiểm tra từ ngoài (Cloudflare Health Check hoặc dịch vụ uptime); hiện kiểm bằng `docker compose ps` |
| Backend tự gọi AI sau khi upload ảnh | Chẩn đoán AI do kỹ sư bấm; đang giữ thủ công có chủ đích |
| Push thật (FCM, UnifiedPush) | Hộp thông báo chỉ kéo về khi mở app |
| ClamAV bật mặc định | Đang tắt vì cần khoảng 1 GB RAM; chưa đo trên máy chủ thật |
| Hộp thông báo cho engineer và admin | Họ dùng hàng chờ và Mattermost |
| Phun lặp lại trên cùng một chẩn đoán | Mỗi chẩn đoán một lần phun; phun lại phải qua báo cáo và chẩn đoán mới |
| Thu hoạch thay hộ không có trong timeline workflow | Thu hoạch không có case; thông tin nằm trên vụ và log backend |
| Phạm vi phụ trách theo khu vực cho engineer | Phân quyền hiện theo thao tác, không theo khu vực |
| Thông báo chat chỉ có gửi lại khi lỗi tạm thời | `chat_outbox` lưu tin sau lần gửi đầu thất bại; tin chưa kịp vào bảng khi backend tắt giữa lúc commit và lúc gửi vẫn mất, chưa có cảnh báo khi tồn đọng |
| Trường "Phương pháp áp dụng" và lượng nước của phác đồ | `TreatmentRule.notes` trộn nhiều loại thông tin; cần field riêng được chuyên gia xác thực |
| Email cho thông báo trong hộp thư | Chưa có. Cần `spring-boot-starter-mail`, tắt `management.health.mail` (biến `SPRING_MAIL_HOST` rỗng vẫn tạo bộ gửi và làm `/actuator/health` báo lỗi), hàng đợi gửi lại chung với tin chat, địa chỉ lấy từ Keycloak lúc gửi, bảng lựa chọn của từng tài khoản và màn hình bật tắt |
| Che thông tin cá nhân trong log | Nội dung log chưa đi qua `PiiMask` |
| Mật khẩu demo cũ của `tech1`, `tech2` | Stack có sẵn hai tài khoản này giữ mật khẩu `tech123`; chỉ stack dựng mới nhận `tech1234` |
| Cải tiến core và vận hành chưa làm: metric và Alertmanager, trang trạng thái công khai và cảnh báo sang chat cho giám sát từ bên ngoài, WAF Coraza, nâng cấp có đường lùi, compose profiles, kiểm giấy phép tự động, sổ kiểm toán chuỗi băm | Chưa làm |

## 6. Phụ thuộc người ngoài

| Việc | Ảnh hưởng |
|---|---|
| Chuyên gia duyệt `disease_management_policies` (hiện 0/6 policy đủ điều kiện, bảng để trống) | Chẩn đoán chưa trả `management_policy` cho nhánh `NO_CHEMICAL_CONTROL`; có policy được duyệt thì mới thêm trường này vào contract AI và Backend |
| Bổ sung văn bản chuyên môn cho rule đốm nâu | Help 400SC đã mở theo yêu cầu chủ dự án ngày 2026-10-20 với trạng thái `EXPERT_APPROVED`; chưa kèm văn bản chuyên môn riêng. Seed có ba rule; than lá, Tungro và lá khỏe không có rule thuốc |
| Kỹ sư nông nghiệp xác nhận mốc tháng loại vụ và danh mục giống (đang `IN_REVIEW`) | Gợi ý loại vụ và ngày thu hoạch dựa trên số tạm; đổi `app.season-start-months` khi có xác nhận, không cần migration |
| Khoa cung cấp văn bản về máy in, VPN, quy chế và đặt phòng máy | Có văn bản thì thêm bài vào `data/rag/lab/` theo nguyên tắc ở `data/README.md` |
| Ảnh lá lúa thật cho `seed_demo.py --images` | Demo chẩn đoán AI có ý nghĩa; chart "Tỷ lệ AI đúng/sai" có dữ liệu; 5 ảnh RiceLeafDiseaseBD (CC BY 4.0) đã chạy thử ngoài repository, chưa kèm theo |

## 7. Backlog của DX-Lab

- Mobile cho Lab.
- Ticket tương tự bằng `pg_trgm`.
- Leo thang và tự đóng ticket.
- Dashboard Superset riêng của Lab.
- Tích hợp email và thời khóa biểu thật.
- Biến ticket đã giải quyết thành bài tri thức.

## 8. Để sau (dài hạn)

| Việc | Điều kiện đưa vào làm |
|---|---|
| Carbon/AWD, IoT mô phỏng mực nước, tự xác nhận phun từ IoT | Có dữ liệu thu hoạch thật qua ít nhất một vụ hoàn chỉnh |
| Polygon ruộng với PostGIS | Cần tính diện tích chính xác từ bản đồ thay vì nhập tay; GPS điểm đơn đang đủ cho định vị |
| GraphRAG hoặc multi-agent | Corpus tri thức lớn đáng kể, RAG đơn giản không còn đủ |
| Domain và HTTPS cho APK phát hành | Phát hành ngoài phạm vi cuộc thi; địa chỉ backend và Keycloak hiện nhúng trong APK |
| Scale thật (100 đến 100.000 người dùng) | Vận hành thật; hướng mở rộng ở [`COUNCIL_QA.md`](./COUNCIL_QA.md) câu 9 |

## 9. Đã chốt không làm

- Mattermost tương tác hai chiều (duyệt hoặc từ chối ngay trong chat).
- SSO cho Mattermost: Team Edition không hỗ trợ OpenID Connect với IdP tùy ý.
- Frontend low-code (ToolJet, Appsmith) thay cho giao diện tự viết.
