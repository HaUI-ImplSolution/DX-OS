# Kế hoạch thông báo Mattermost qua Incoming Webhook

> **Trạng thái:** Kế hoạch, chưa triển khai.
>
> **Cập nhật:** 29/09/2026.
>
> **Phạm vi:** Backend post thông báo vào một channel Mattermost qua Incoming Webhook khi có báo cáo sâu bệnh mới hoặc chẩn đoán chờ duyệt.

## 1. Giả định đã chốt

- Backend là nơi duy nhất phát sinh thông báo; AI Service và frontend không gọi Mattermost.
- Thông báo là kênh phụ trợ, best-effort.
  Dữ liệu và trạng thái duyệt vẫn nằm trong Backend DB; mất một thông báo không làm sai nghiệp vụ.
- Lỗi khi post webhook không được làm hỏng hoặc chậm request tạo báo cáo/chẩn đoán.
- Webhook URL chứa khóa truy cập, được coi là secret: chỉ đọc từ biến môi trường, không có trong source, log hay response.
- Mattermost giữ nguyên tài khoản riêng, không nối SSO; webhook được tạo tay một lần trong UI Mattermost.
- Frontend chưa có routing nên thông báo không kèm deep link; kỹ sư tìm báo cáo theo mã thửa và mã báo cáo trong thông báo.

## 2. Hiện trạng

- `DiseaseReportService.create()` lưu `DiseaseReport` trong một transaction.
- `DiagnosisService.create()` (nhập tay) và `DiagnosisService.createFromAi()` (gọi AI) đều lưu `Diagnosis` với `approvalStatus = PENDING`.
  `treatmentRule = null` khi AI chỉ yêu cầu kỹ sư xem xét.
- Service `mattermost` chạy trong `deploy/docker-compose.yml`, Backend gọi được qua hostname nội bộ `mattermost:8065`.
- Backend chưa có code gọi Mattermost; `application.yaml` mới chỉ có `mattermost.db.password`.
- Backend đã có mẫu client HTTP ra ngoài: `AiDiagnosisClient` dùng `RestClient` + `JdkClientHttpRequestFactory` với timeout cấu hình được.
- `spring-boot-starter-aspectj` và `resilience4j-spring-boot3` đã có trong `pom.xml`; chưa dùng `@Async` hay Spring event.

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
- Event là record chứa giá trị thuần (chuỗi, số), dựng trong transaction.
  Listener chạy ở thread khác, không đụng tới entity lazy.

**Xử lý bất đồng bộ, không retry**

- Listener chạy `@Async` trên executor riêng (`mattermostNotifyExecutor`, 1–2 thread, queue 100, tràn thì bỏ).
  Request người dùng không phải chờ Mattermost.
- Mọi exception trong listener/client được bắt và log mức WARN; không ném ngược lên luồng nghiệp vụ.
- Không retry; thông báo là best-effort nên chấp nhận mất khi Mattermost đang tắt.

**Tắt an toàn khi chưa cấu hình**

- `mattermost.webhook.url` rỗng thì listener bỏ qua ngay, không tạo HTTP call.
  Cùng kiểu fail-closed với `ai.service.inbound-api-key`.
- Log một dòng lúc khởi động cho biết webhook bật hay tắt, không in URL.

**Nội dung thông báo**

| Sự kiện                | Trigger                                              | Nội dung                                                                                    |
| ------------------------ | ---------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Báo cáo mới           | `DiseaseReportService.create()`                    | Mã báo cáo, mã thửa, giống lúa, người báo cáo, thời điểm báo cáo             |
| Chẩn đoán chờ duyệt | `DiagnosisService.create()` và `createFromAi()` | Mã báo cáo, mã thửa, bệnh (mã + tên), độ tin cậy, có/không có`TreatmentRule` |

- Định dạng: một `attachment` Mattermost (`title`, `color`, `fields`) kèm `fallback` là text thuần.
- Chẩn đoán không có `TreatmentRule` ghi rõ "Không có phác đồ — cần kỹ sư xem xét".
- Không đưa vào thông báo: `image_url` (hostname nội bộ `backend:8081`), nội dung khuyến nghị đầy đủ, secret.
- Thời điểm hiển thị theo múi giờ `Asia/Ho_Chi_Minh`, cùng cấu hình Jackson hiện có.

**Làm sạch dữ liệu người dùng**

- `reportedBy`, mã thửa và giống lúa là chuỗi do người dùng/Keycloak cung cấp.
  Trước khi đưa vào thông báo: cắt tối đa 100 ký tự và vô hiệu hóa mention (`@channel`, `@all`, `@here`, `@user`) để không spam channel.

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
- `deploy/docker-compose.yml` truyền `MATTERMOST_WEBHOOK_URL` cho `backend` và bật `MM_SERVICESETTINGS_ENABLEINCOMINGWEBHOOKS=true` cho `mattermost`.
- `deploy/.env.example` thêm `MATTERMOST_WEBHOOK_URL=` để trống kèm chú thích cách lấy giá trị.

**Cấp webhook (thao tác tay một lần)**

- Đăng nhập Mattermost bằng System Admin, tạo team và channel nhận thông báo (ví dụ "Kỹ sư nông nghiệp").
- Vào Integrations → Incoming Webhooks → Add Incoming Webhook, chọn channel đích.
- Sao chép URL, đổi host `localhost:8065` thành `mattermost:8065`, điền vào `deploy/.env`.
- Chạy lại service `backend` để nhận cấu hình mới.

**API và Swagger**

- Không thêm hay đổi endpoint, request/response nên không cần cập nhật springdoc.

## 4. Các công việc cần thực hiện

**Backend**

- [ ] Package `event/` với hai record `DiseaseReportCreatedEvent` và `DiagnosisPendingEvent`, chỉ chứa giá trị thuần.
- [ ] `DiseaseReportService.create()` publish `DiseaseReportCreatedEvent`; `DiagnosisService.create()` và `createFromAi()` publish `DiagnosisPendingEvent` sau khi `save`.
- [ ] `config/AsyncConfig` bật `@EnableAsync` và khai báo executor `mattermostNotifyExecutor` có giới hạn thread và queue.
- [ ] `MattermostWebhookClient` (`RestClient` + `JdkClientHttpRequestFactory`, timeout từ cấu hình) POST JSON attachment; URL rỗng thì không gửi.
- [ ] `MattermostNotificationListener` xử lý hai event bằng `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`, bắt mọi exception và log WARN không kèm URL.
- [ ] Hàm làm sạch chuỗi người dùng (cắt độ dài, vô hiệu mention) dùng chung cho cả hai thông báo.
- [ ] `application.yaml` thêm khóa `mattermost.webhook.*` như mục 3.

**Deploy và tài liệu**

- [ ] `deploy/docker-compose.yml` và `deploy/.env.example` cập nhật theo mục 3.
- [ ] `chat/README.md`: thay mục "Còn thiếu" bằng mô tả thông báo tự động và các bước cấp webhook.
- [ ] `README.md` gốc và `CHANGELOG.md` ghi trạng thái hiện tại của tính năng, theo quy ước không kể diễn biến trước/sau.
- [ ] Comment và `@Operation` (nếu có) bằng tiếng Anh, ngắn, không nhắc nhãn milestone.

## 5. Các phần chưa đạt được

- Thông báo khi chẩn đoán được duyệt, bị từ chối hoặc đã ghi nhận xử lý.
- Deep link từ thông báo sang màn hình báo cáo (chờ frontend có routing).
- Tự động tạo webhook lúc dựng compose (ví dụ qua `mmctl` hoặc REST API của Mattermost).
- Retry, hàng đợi bền vững hoặc outbox cho thông báo bị lỡ.
- Định tuyến thông báo theo channel khác nhau cho từng loại sự kiện.

## 6. Luồng vận hành và hạn chế đã biết

**Luồng vận hành**

```
1. Nông dân  POST /crop-seasons/{id}/disease-reports (ảnh)
      └► DiseaseReportService.create() lưu DB, publish event
            └► commit xong → listener chạy nền → POST webhook
                  └► Mattermost: "Báo cáo sâu bệnh mới"

2. Kỹ sư  POST /disease-reports/{id}/diagnosis/ai (hoặc nhập tay)
      └► gọi AI, lưu Diagnosis PENDING, publish event
            └► commit xong → listener chạy nền → POST webhook
                  └► Mattermost: "Chẩn đoán chờ duyệt"

3. Kỹ sư đọc thông báo, mở web app, tìm theo mã thửa/mã báo cáo
   rồi approve/reject/apply; Mattermost không nhận kết quả này.
```

- Chẩn đoán không tự chạy khi có báo cáo; bước 2 do kỹ sư kích hoạt, nên thông báo "chẩn đoán chờ duyệt" chỉ có sau khi có người gọi.
- Ở `createFromAi()`, thông báo đi sau commit nên đến sau khi AI xử lý xong, tối đa theo `ai.service.read-timeout-ms`.

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
  Thông báo có tên người báo cáo và mã thửa nên cần kiểm soát thành viên channel.
- Cấp webhook là thao tác tay; môi trường mới chưa cấu hình thì tính năng ở trạng thái tắt.

## 7. Bất biến

- Không thay đổi quyền FARMER/ENGINEER/ADMIN, state machine duyệt, kiểm tra khoảng liều hay khóa PHI.
- Luồng tạo báo cáo/chẩn đoán thành công hay thất bại không phụ thuộc vào Mattermost.
- Không log, không trả về response và không commit Webhook URL thật.
- Không thêm dependency mới vào `pom.xml`.
- Mattermost vẫn dùng tài khoản riêng, không nối SSO.

## 8. Definition of done

- Toàn bộ hạng mục ở mục 4 hoàn tất.
- `./mvnw compile` pass và diff đã được đọc lại.
- Khi `MATTERMOST_WEBHOOK_URL` rỗng, Backend khởi động và tạo báo cáo/chẩn đoán bình thường, không gọi Mattermost.
- Khi URL hợp lệ, tạo báo cáo mới và chẩn đoán chờ duyệt đều sinh đúng một thông báo vào channel đã chọn (kiểm tra thủ công khi người dùng chạy stack).
- Khi Mattermost tắt hoặc URL sai, request tạo báo cáo/chẩn đoán vẫn trả thành công và log có đúng một dòng WARN không chứa URL.
