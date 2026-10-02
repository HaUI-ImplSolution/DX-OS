# Kế hoạch thông báo Mattermost qua Incoming Webhook

> **Trạng thái:** Đã triển khai trong Backend và deploy; còn bước cấp webhook tay và kiểm tra thủ công khi chạy stack.
>
> **Cập nhật:** 02/10/2026.
>
> **Phạm vi:** Backend post thông báo vào một channel Mattermost qua Incoming Webhook khi có báo cáo sâu bệnh mới hoặc chẩn đoán chờ duyệt.

## 1. Giả định đã chốt

- Backend là nơi duy nhất phát sinh thông báo; AI Service và frontend không gọi Mattermost.
- Thông báo là kênh phụ trợ, best-effort.
  Dữ liệu và trạng thái duyệt vẫn nằm trong Backend DB; mất một thông báo không làm sai nghiệp vụ.
- Lỗi khi post webhook không được làm hỏng hoặc chậm request tạo báo cáo/chẩn đoán.
- Webhook URL chứa khóa truy cập, được coi là secret: chỉ đọc từ biến môi trường, không có trong source, log hay response.
- Mattermost giữ nguyên tài khoản riêng, không nối SSO; webhook được tạo tay một lần trong UI Mattermost.
- Channel nhận thông báo là `Kỹ sư nông nghiệp` (`ky-su-nong-nghiep`) trong team `dx-farm`, do `chat/bootstrap.sh` tạo sẵn.
- Frontend chưa có routing nên thông báo không kèm deep link; kỹ sư tìm báo cáo theo mã thửa và mã báo cáo trong thông báo.

## 2. Hiện trạng

**Backend**

- `DiseaseReportService.create()` là `@Transactional`.
  Nhánh idempotent (`Idempotency-Key` đã dùng) trả báo cáo cũ ngay, không lưu báo cáo mới; nhánh tạo mới kết thúc bằng `saveAndFlush`.
- `DiagnosisService.create()` (nhập tay) và `createFromAi()` (gọi AI) đều là `@Transactional` và lưu `Diagnosis` với `approvalStatus = PENDING` bằng `save` ở dòng cuối.
  - `createFromAi()` gọi `AiDiagnosisClient.diagnose()` bên trong transaction, trước `save`; AI lỗi, ảnh bị triage hoặc rule đổi version đều ném exception trước `save`.
  - `treatmentRule = null` chỉ xảy ra ở `createFromAi()` khi AI trả đường `review_required`; `create()` luôn có rule.
- `DiseaseReport.reportedAt` và `Diagnosis.diagnosedAt` là `Instant`; `confidence` là `BigDecimal` trong khoảng 0–1.
- Ngoài `MattermostDatabaseSeeder`, Backend gọi Mattermost qua `MattermostWebhookClient`; cấu hình nằm ở khóa `mattermost.webhook` cạnh `mattermost.db` trong `application.yaml`.
- `AiDiagnosisClient` là mẫu client HTTP ra ngoài: `RestClient` + `JdkClientHttpRequestFactory` với timeout cấu hình được.
- `@Async` và Spring event chỉ dùng cho thông báo Mattermost; không thêm dependency vào `pom.xml`.
- `spring.jackson.time-zone: Asia/Ho_Chi_Minh` chỉ áp dụng khi serialize JSON, không áp dụng cho chuỗi tự dựng trong thông báo.

**Mattermost và deploy**

- Service `mattermost` chạy trong `deploy/docker-compose.yml`, Backend gọi được qua hostname nội bộ `mattermost:8065`.
  `MM_SERVICESETTINGS_SITEURL` là `http://localhost:${MATTERMOST_HOST_PORT}`, nên URL webhook sao chép từ UI mang host `localhost`.
- `mattermost-bootstrap` đã tạo sẵn System Admin, team `dx-farm` và 3 channel; `Kỹ sư nông nghiệp` là channel public.
- `docker-compose.yml` truyền `MATTERMOST_WEBHOOK_URL` cho `backend` và bật `MM_SERVICESETTINGS_ENABLEINCOMINGWEBHOOKS` cho `mattermost`; `.env.example` có biến này để trống.

## 3. Quyết định kiến trúc

```
DiseaseReportService.create()  ──┐
DiagnosisService.create()      ──┼─► publishEvent(record thuần dữ liệu)
DiagnosisService.createFromAi()──┘
                                     │  sau khi transaction commit
                                     ▼
              MattermostNotificationListener (@Async, executor riêng)
                                     ▼
              MattermostWebhookClient ──POST JSON──► http://mattermost:8065/hooks/<key>
```

**Phát event thay vì gọi trực tiếp từ service nghiệp vụ**

- Service nghiệp vụ chỉ `publishEvent`; listener dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`.
  Thông báo chỉ đi khi dữ liệu đã commit, transaction rollback thì không có thông báo "ma".
- Cả ba điểm publish đều nằm trong transaction đang hoạt động nên không cần `fallbackExecution`.
- Event là record chứa giá trị thuần (chuỗi, số), dựng từ entity vừa lưu ngay trong transaction.
  Listener chạy ở thread khác, không đụng tới entity lazy.

**Vị trí publish**

| Điểm publish                          | Publish ở đâu                                                                     | Không publish khi                                     |
| ------------------------------------- | --------------------------------------------------------------------------------- | ----------------------------------------------------- |
| `DiseaseReportService.create()`       | Ngay sau `saveAndFlush` của báo cáo mới, ngoài khối `try/catch`                   | Nhánh idempotent trả báo cáo cũ; `saveAndFlush` lỗi   |
| `DiagnosisService.create()`           | Ngay sau `save`                                                                   | Validate hoặc `save` lỗi                              |
| `DiagnosisService.createFromAi()`     | Ngay sau `save`, tức sau lệnh gọi AI và kiểm tra version rule                     | AI lỗi, bị triage, rule đổi version, bệnh không hợp lệ |

- Nhánh idempotent bắt buộc không publish: app mobile gửi lại cùng `Idempotency-Key` khi mất mạng, nếu publish sẽ có thông báo trùng cho một báo cáo.

**Xử lý bất đồng bộ, không retry**

- Listener chạy `@Async("mattermostNotifyExecutor")` trên executor riêng (2 thread, queue 100).
  Request người dùng không phải chờ Mattermost.
- Khi queue đầy, executor bỏ task và ghi một dòng WARN bằng `RejectedExecutionHandler` riêng; không để `TaskRejectedException` nổi lên luồng gọi.
- Mọi exception trong listener/client được bắt và log mức WARN; không ném ngược lên luồng nghiệp vụ.
- Không retry; thông báo là best-effort nên chấp nhận mất khi Mattermost đang tắt.

**Tắt an toàn khi chưa cấu hình**

- `mattermost.webhook.url` rỗng thì listener bỏ qua ngay, không tạo HTTP call (fail-closed).
- Log một dòng lúc khởi động cho biết webhook bật hay tắt, không in URL.

**Nội dung thông báo**

| Sự kiện             | Trigger                                         | Nội dung                                                                    |
| ------------------- | ----------------------------------------------- | --------------------------------------------------------------------------- |
| Báo cáo mới         | `DiseaseReportService.create()`                 | Mã báo cáo, mã thửa, giống lúa, người báo cáo, thời điểm báo cáo            |
| Chẩn đoán chờ duyệt | `DiagnosisService.create()` và `createFromAi()` | Mã báo cáo, mã thửa, bệnh (mã + tên), độ tin cậy, có/không có `TreatmentRule` |

- Định dạng: một `attachment` Mattermost (`title`, `color`, `fields`) kèm `fallback` là text thuần.
- Độ tin cậy hiển thị dạng phần trăm (ví dụ `87%`).
- Chẩn đoán không có `TreatmentRule` ghi rõ "Không có phác đồ — cần kỹ sư xem xét".
- Không đưa vào thông báo: `image_url` (hostname nội bộ `backend:8081`), nội dung khuyến nghị đầy đủ, secret.
- Thời điểm format tường minh theo `ZoneId` `Asia/Ho_Chi_Minh` (ví dụ `dd/MM/yyyy HH:mm`).

**Làm sạch dữ liệu người dùng**

- `reportedBy`, mã thửa, giống lúa và tên bệnh là chuỗi do người dùng/Keycloak/dữ liệu danh mục cung cấp, hiển thị trong `fields` có render markdown.
- Một hàm làm sạch dùng chung cho cả hai thông báo:
  - cắt tối đa 100 ký tự;
  - thay xuống dòng bằng khoảng trắng;
  - vô hiệu mention (`@channel`, `@all`, `@here`, `@user`) bằng cách chèn ký tự zero-width sau `@`;
  - escape ký tự markdown (`` ` `` `*` `_` `[` `]` `<` `>` `|` `#` `~` `\`) và chèn zero-width vào `://` để không chèn được link hay định dạng.

**Cấu hình**

```yaml
mattermost:
  webhook:
    url: ${MATTERMOST_WEBHOOK_URL:}
    connect-timeout-ms: ${MATTERMOST_WEBHOOK_CONNECT_TIMEOUT_MS:2000}
    read-timeout-ms: ${MATTERMOST_WEBHOOK_READ_TIMEOUT_MS:3000}
```

- Khóa `mattermost.webhook` đặt cạnh `mattermost.db` hiện có.
- `MATTERMOST_WEBHOOK_URL` có dạng `http://mattermost:8065/hooks/<key>`; host phải là hostname nội bộ, không dùng `localhost`.
- `deploy/docker-compose.yml` truyền `MATTERMOST_WEBHOOK_URL` (không bắt buộc, mặc định rỗng) cho `backend` và đặt `MM_SERVICESETTINGS_ENABLEINCOMINGWEBHOOKS: "true"` cho `mattermost`.
- `deploy/.env.example` thêm `MATTERMOST_WEBHOOK_URL=` để trống kèm chú thích cách lấy giá trị.

**Cấp webhook (thao tác tay một lần)**

- Team `dx-farm` và channel `Kỹ sư nông nghiệp` đã có sẵn từ `mattermost-bootstrap`.
- Đăng nhập Mattermost bằng System Admin, vào Integrations → Incoming Webhooks → Add Incoming Webhook, chọn channel `Kỹ sư nông nghiệp`.
- Sao chép URL, đổi host `localhost:<cổng>` thành `mattermost:8065`, điền vào `deploy/.env`.
- Chạy lại service `backend` để nhận cấu hình mới.

**API và Swagger**

- Không thêm hay đổi endpoint, request/response nên không cần cập nhật springdoc.

## 4. Các công việc cần thực hiện

**Backend** (`backend/DX-Farm`)

- [x] Package `event/` với hai record `DiseaseReportCreatedEvent` và `DiagnosisPendingEvent`, chỉ chứa giá trị thuần.
- [x] `DiseaseReportService.create()` publish `DiseaseReportCreatedEvent` sau `saveAndFlush` của báo cáo mới (không publish ở nhánh idempotent); `DiagnosisService.create()` và `createFromAi()` publish `DiagnosisPendingEvent` sau `save`.
- [x] `config/AsyncConfig` bật `@EnableAsync` và khai báo executor `mattermostNotifyExecutor` (2 thread, queue 100) với `RejectedExecutionHandler` bỏ task và log WARN.
- [x] `MattermostWebhookClient` (`RestClient` + `JdkClientHttpRequestFactory`, timeout từ cấu hình) POST JSON attachment; URL rỗng thì không gửi.
- [x] `MattermostNotificationListener` xử lý hai event bằng `@Async("mattermostNotifyExecutor")` + `@TransactionalEventListener(AFTER_COMMIT)`, bắt mọi exception và log WARN không kèm URL.
- [x] Hàm làm sạch chuỗi người dùng (cắt độ dài, bỏ xuống dòng, vô hiệu mention, escape markdown) và hàm format thời điểm theo `Asia/Ho_Chi_Minh`, dùng chung cho cả hai thông báo.
- [x] `application.yaml` thêm khóa `mattermost.webhook.*` như mục 3.

**Deploy và tài liệu**

- [x] `deploy/docker-compose.yml` và `deploy/.env.example` cập nhật theo mục 3.
- [x] `chat/README.md`: thay mục "Còn thiếu" bằng mô tả thông báo tự động và các bước cấp webhook.
- [x] `README.md` gốc (mục Giai đoạn 2 [H]) và `CHANGELOG.md` ghi trạng thái hiện tại của tính năng, theo quy ước không kể diễn biến trước/sau.
- [x] Comment và `@Operation` (nếu có) bằng tiếng Anh, ngắn, không nhắc nhãn milestone.

**Cần xác minh khi chạy thật**

- [ ] `MM_SERVICESETTINGS_ENABLEINCOMINGWEBHOOKS=true` có hiệu lực dù `config.json` nằm trong bind mount `deploy/data/mattermost/config/` (biến môi trường ghi đè file; xác nhận mục Incoming Webhooks hiện trong UI).
- [ ] Mention và markdown trong `reportedBy`, mã thửa, giống lúa thật sự bị vô hiệu trên Mattermost (thử một báo cáo có `@channel` và `[x](http://...)` trong tên thửa).

## 5. Các phần chưa đạt được

- Thông báo khi chẩn đoán được duyệt, bị từ chối hoặc đã ghi nhận xử lý.
- Deep link từ thông báo sang màn hình báo cáo (chờ frontend có routing).
- Tự động tạo webhook lúc dựng compose.
  `mattermost-bootstrap` đã có admin token và gọi được REST API tạo webhook, nhưng khóa sinh ra phải đến được `backend` đã khởi động từ trước (qua file dùng chung hoặc khởi động lại `backend`), nên hiện giữ thao tác tay.
- Retry, hàng đợi bền vững hoặc outbox cho thông báo bị lỡ.
- Định tuyến thông báo theo channel khác nhau cho từng loại sự kiện.

## 6. Luồng vận hành và hạn chế đã biết

**Luồng vận hành**

```
1. Nông dân  POST /api/v1/crop-seasons/{id}/disease-reports (ảnh)
      └► DiseaseReportService.create() lưu DB, publish event
            └► commit xong → listener chạy nền → POST webhook
                  └► Mattermost: "Báo cáo sâu bệnh mới"

2. Kỹ sư  POST /api/v1/disease-reports/{id}/diagnosis/ai (hoặc nhập tay)
      └► gọi AI, lưu Diagnosis PENDING, publish event
            └► commit xong → listener chạy nền → POST webhook
                  └► Mattermost: "Chẩn đoán chờ duyệt"

3. Kỹ sư đọc thông báo, mở web app, tìm theo mã thửa/mã báo cáo
   rồi approve/reject/apply; Mattermost không nhận kết quả này.
```

- Chẩn đoán không tự chạy khi có báo cáo; bước 2 do kỹ sư kích hoạt, nên thông báo "chẩn đoán chờ duyệt" chỉ có sau khi có người gọi.
- Ở `createFromAi()`, thông báo đi sau commit nên đến sau khi AI xử lý xong, tối đa theo `ai.service.read-timeout-ms`.
- Gửi lại báo cáo với `Idempotency-Key` đã dùng không sinh thông báo thứ hai.

**Độ tin cậy của thông báo**

- Không retry, không outbox, hàng đợi nằm trong bộ nhớ.
  Thông báo mất khi Mattermost tắt, khi hàng đợi đầy, hoặc khi Backend dừng giữa lúc commit và lúc gửi.
- Thất bại chỉ để lại một dòng WARN, không có nơi xem thông báo nào đã gửi hoặc bị lỡ.
- Webhook hỏng không làm Backend báo lỗi.
  Xóa `deploy/data/mattermost/` hoặc thu hồi webhook làm khóa cũ mất hiệu lực và thông báo ngừng đến.

**Trải nghiệm kỹ sư**

- Luồng một chiều: không duyệt hoặc từ chối được trong Mattermost.
  Muốn vậy cần slash command hoặc nút tương tác để Mattermost gọi ngược Backend, kèm cơ chế xác thực riêng.
- Không có deep link vì frontend chưa có routing.
- Thông báo cũ không được cập nhật sau khi duyệt hoặc từ chối, nên channel lẫn tin đã xử lý và chưa xử lý.
  Cập nhật tin cũ cần lưu `post_id` và dùng bot token cùng REST API của Mattermost thay cho webhook.
- Không gom nhóm: nhiều báo cáo cùng lúc tạo nhiều tin liên tiếp.

**Phân quyền và vận hành**

- Một channel cho mọi sự kiện, không định tuyến theo thửa hoặc kỹ sư phụ trách.
- Quyền xem thông báo do thành viên channel Mattermost quyết định, không đồng bộ với role FARMER/ENGINEER/ADMIN của Backend.
- `Kỹ sư nông nghiệp` là channel public: mọi tài khoản trong team `dx-farm` tự tham gia và đọc được tên người báo cáo, mã thửa.
  Muốn giới hạn phải chuyển channel sang private và thêm thành viên thủ công, hoặc tạo channel private riêng cho webhook.
- Cấp webhook là thao tác tay; môi trường mới chưa cấu hình thì tính năng ở trạng thái tắt.

## 7. Bất biến

- Không thay đổi quyền FARMER/ENGINEER/ADMIN, state machine duyệt, kiểm tra khoảng liều hay khóa PHI.
- Luồng tạo báo cáo/chẩn đoán thành công hay thất bại không phụ thuộc vào Mattermost.
- Mỗi báo cáo mới và mỗi chẩn đoán được lưu sinh đúng một event; request lặp theo `Idempotency-Key` không sinh event.
- Không log, không trả về response và không commit Webhook URL thật.
- Không thêm dependency mới vào `pom.xml`.
- Mattermost vẫn dùng tài khoản riêng, không nối SSO.

## 8. Definition of done

- Toàn bộ hạng mục ở mục 4 hoàn tất.
- `./mvnw compile` (chạy trong `backend/DX-Farm`) pass và diff đã được đọc lại.
- Khi `MATTERMOST_WEBHOOK_URL` rỗng, Backend khởi động và tạo báo cáo/chẩn đoán bình thường, không gọi Mattermost.
- Khi URL hợp lệ, tạo báo cáo mới và chẩn đoán chờ duyệt (cả nhập tay lẫn qua AI) đều sinh đúng một thông báo vào channel `Kỹ sư nông nghiệp`; gửi lại báo cáo cùng `Idempotency-Key` không sinh thêm (kiểm tra thủ công khi người dùng chạy stack).
- Khi Mattermost tắt hoặc URL sai, request tạo báo cáo/chẩn đoán vẫn trả thành công và log có đúng một dòng WARN không chứa URL.
