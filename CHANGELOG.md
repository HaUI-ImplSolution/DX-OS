# Changelog

Nhật ký thay đổi của DX-Farm, theo từng module.
Định dạng dựa trên [Keep a Changelog](https://keepachangelog.com/).

## Backend (Spring Boot — `backend`)

### 2026-10-12

**Đã xong — App Lab (`app-lab`):**

- Module `app-lab` chỉ phụ thuộc `dx-core-api`; `DXOS_APP=lab` nạp module, migration `db/migration/lab` (bảng lịch sử `flyway_schema_history_lab`) và `application-lab.yaml`.
- Yêu cầu hỗ trợ: tạo kèm tệp (JPEG, PNG, PDF), trao đổi công khai và nội bộ, tự nhận hoặc được gán, `resolve` bắt buộc ghi chú và nguyên nhân gốc, xác nhận kèm đánh giá 1-5, mở lại tối đa 3 lần.
  Hạn phản hồi và xử lý tính từ chính sách SLA theo ưu tiên; mã ticket `TK-<năm>-<số>`.
- Thiết bị: nhật ký bảo trì, phiếu mượn có hạn 7 ngày (sinh viên do giảng viên duyệt, giảng viên do kỹ thuật viên duyệt), tối đa 2 phiếu mở mỗi người, trả hỏng mở nhật ký sửa chữa.
- Đặt phòng: bước 30 phút, tối đa 4 giờ, trước tối đa 30 ngày, kiểm sức chứa; ràng buộc `EXCLUDE USING gist` ở PostgreSQL chặn hai lịch hiệu lực chồng giờ cùng phòng.
- Ba loại case `TICKET`, `DEVICE_LOAN`, `ROOM_BOOKING` trên workflow dùng chung; lịch sử ở `workflow_event`, hai người xử lý cùng ticket thì một bên nhận 409.
- Lịch cảnh báo SLA (80% thời hạn xử lý, quá hạn) và nhắc mượn trả hằng ngày; mỗi thông báo gửi một lần nhờ khóa chống trùng.
- `POST /api/v1/tickets/suggest` gợi ý loại và ưu tiên bằng luật kèm lý do và tối đa 3 đoạn tri thức; `POST /api/v1/knowledge/ask` hỏi đáp namespace `lab`.
- `GET /api/v1/lab-stats/summary` và 5 view `vw_lab_*` cho Superset; `GET /api/v1/approval-requests` liệt kê ticket, phiếu mượn và đặt phòng cho kỹ thuật viên.
- Chi tiết ở [`backend/app-lab/README.md`](./backend/app-lab/README.md).

**Đã xong — Tên trung tính cho realm và client:**

- Realm `dxos`, audience `dxos-api`; client Farm mobile cho FARMER là `farm-mobile` (`security.farmer-client-ids`).
- Service account `dxos-backend-admin` giữ `manage-realm` để `RoleBootstrap` tạo vai của app lúc khởi động.

### 2026-10-11

**Đã xong — Core trung tính cho nhiều ứng dụng:**

- Đơn vị dữ liệu là `Workspace` (`code`, `type`, `name`, liên hệ, thuộc tính) với `WorkspaceMember` nhiều-nhiều (vai `OWNER` hoặc `MEMBER`).
  Tiền tố mã theo loại do app đăng ký; Farm đăng ký `HOUSEHOLD` với tiền tố `H`.
  Core có `/api/v1/workspaces`; Farm giữ `/api/v1/households` với JSON như cũ.
- Workflow cấu hình được: mỗi loại case khai báo trạng thái, bước chuyển và vai bằng `CaseTypeDefinition`; trạng thái là chuỗi.
  Farm đăng ký năm loại theo luồng duyệt chuẩn (`PENDING` → `APPROVED`, `REJECTED`, `CANCELLED`; `REJECTED` → `PENDING`).
  Loại case chưa đăng ký bị từ chối.
- Hộp duyệt chạy ở core (`ApprovalInbox`); app mô tả entity qua `CaseSummaryProvider`.
  Giới hạn số yêu cầu chờ (`MAX_PENDING_REQUESTS`) thuộc core, loại case tính giới hạn do app khai báo.
- Thông báo trong app ở core: `notifications` nằm ở migration core, `type` là mã chuỗi do app đăng ký.
  Khóa kênh chat là chuỗi; webhook của kênh đọc từ `mattermost.webhook.url-<khóa>` và lùi về `mattermost.webhook.url`.
- Bảo mật theo interface: `RoleProvider` (core tạo vai thiếu trong Keycloak lúc khởi động, thử lại nền khi Keycloak chưa lên; thiếu quyền tạo vai thì chỉ ghi log), `EndpointPolicyContributor`, `ClientRestrictionPolicy`.
  `UserContext` có `roles`, `hasRole`, `isStaff`.
- `GET /api/v1/app` trả id, tên, vai, menu và loại workspace của app đang chạy.
- Cổng AI (`AiGateway`) và hỏi đáp tri thức (`KnowledgeGateway`) ở core; AI service nhận `namespace` (mặc định `farm`) ở `POST /api/v1/disease/knowledge/retrieve`.
- `SupersetReadOnlyRoleSeeder` cấp quyền theo `BiDatasetProvider`; `GET /api/v1/system/status` gom `HealthContributor`; `FileStore` chạy `FileProcessor` của mục đích trước khi lưu tệp.
- Lỗi poka-yoke mang dữ liệu đi kèm dạng `data` (Farm đưa `harvestLockedUntil` vào đó).
- Web và mobile nhận trạng thái case và loại thông báo ngoài danh sách đã biết, hiển thị nguyên giá trị.

### 2026-10-09

**Đã xong — Backend đa module:**

- Backend là bốn module Maven: `dx-core-api` (interface, DTO, lỗi), `dx-core` (identity, workflow, storage, bảo mật, nền tảng), `app-farm` (nghiệp vụ nông nghiệp) và `dx-host` (fat jar).
  Package gốc là `HaUI.ImplSolution.dxos`.
- `app-farm` chỉ phụ thuộc `dx-core-api` và gọi core qua `WorkflowOperations`, `WorkspaceDirectory`, `ObjectStorage`, `UserContext`.
  `Parcel` giữ `household_id` dạng số, không còn quan hệ JPA tới nông hộ.
- `DXOS_APP` (mặc định `farm`) chọn app chạy cùng core; giá trị không hợp lệ làm backend dừng khi khởi động.
- Flyway có hai location: `db/migration/core` (`households`, `workflow_case`, `workflow_event`) và `db/migration/farm`, mỗi nơi một bảng lịch sử, core chạy trước.
  Database dựng lại bằng `deploy/reset.sh`.
- `data.sql` của Farm nằm ở `app-farm/src/main/resources/farm/data.sql` và chỉ nạp khi `DXOS_APP=farm`.

**Đã xong — Seed treatment rule đã duyệt:**

- Seed idempotent `NEWTEC_300SC` cùng hai rule `EXPERT_APPROVED` cho đạo ôn (`Rice___Blast`) và khô vằn (`Rice___Sheath_blight`); rule Help 400SC cho đốm nâu ở trạng thái `IN_REVIEW`, không seed.
- `V10__treatment_rule_notes_text.sql` mở rộng `treatment_rules.notes` sang `text`; integration test kiểm tra migration, số rule active và chạy seed lặp không sinh trùng.
- CI xác minh `data.sql` luôn khớp dữ liệu nguồn trong `data/`; file SQL vẫn được commit nhưng không sửa tay.

### 2026-10-07

**Đã xong — Kiểm thử, dữ liệu demo, hồ sơ nộp bài:**

- `ApiResponse` lỗi có thêm trường `errorCode` ổn định (ví dụ `ERR_FORBIDDEN`, `ERR_NOT_FOUND`, `ERR_VALIDATION`, `ERR_INTERNAL`); lỗi poka-yoke giữ mã riêng (`HARVEST_TOO_EARLY`, `POKA_YOKE_VIETGAP_VIOLATION`, ...).
- `GET /api/v1/system/status` công khai, trả `UP`/`DOWN` của backend, AI Service và lưu trữ ảnh cho portal.
- Mọi file Java có SPDX header `Apache-2.0`.

**Đã xong — Dữ liệu, BI và hỏi đáp tài liệu:**

- `V9__bi_views.sql` thêm sáu view chỉ đọc cho BI: `vw_parcels_approved`, `vw_crop_seasons_approved`, `vw_disease_reports_processed`, `vw_diagnoses_approved`, `vw_treatment_applications_confirmed`, `vw_ai_diagnosis_outcomes`.
  View chỉ chứa bản ghi đã duyệt hoặc đã xử lý, không có tên chủ hộ, người báo cáo hay đường dẫn ảnh.
- `SupersetReadOnlyRoleSeeder` cấp cho `superset_ro` quyền đọc theo danh sách: sáu view trên và ba bảng danh mục (`diseases`, `pesticides`, `rice_varieties`).
  Mọi bảng khác, kể cả bảng thêm về sau, đóng cho tới khi được thêm vào danh sách.
- `GET /api/v1/parcels/{code}/weather` trả thời tiết hiện tại và dự báo 5 ngày tại vị trí thửa từ Open-Meteo (không cần key, cache 1 giờ).
  Open-Meteo không gọi được thì trả điểm tham chiếu gần nhất trong `weather-snapshot.json` kèm `isSnapshot=true` và ghi chú.
  Thửa chưa có GPS trả `422 PARCEL_LOCATION_MISSING`; nông dân chỉ xem được thời tiết thửa của hộ mình.
- `POST /api/v1/knowledge/ask` trả các đoạn tài liệu phù hợp nhất từ RAG, mỗi đoạn kèm nguồn (tên, tổ chức, link, giấy phép, trạng thái duyệt).
  Đoạn được trích nguyên văn, không sinh thêm; câu hỏi không liên quan trả danh sách rỗng (ngưỡng `knowledge.min-score` trong `application.yaml`, mặc định 0.40).
  Bảng ánh xạ nguồn nằm ở `knowledge-sources.json`, lấy từ `data/rag/source_registry.json`.
- Gateway giới hạn 60 câu hỏi mỗi phút cho mỗi địa chỉ ở `/api/v1/knowledge/ask`.
- `WEATHER_BASE_URL` (mặc định `https://api.open-meteo.com`) đổi được địa chỉ Open-Meteo qua `deploy/.env`.
- Kết nối `DX-Farm` trong Superset chạy truy vấn đồng bộ vì stack không có Celery worker, SQL Lab dùng được.

**Đã xong — Vòng đời vụ mùa và thông báo cho nông dân:**

- `V7__season_lifecycle_notifications.sql` thêm bảng `notifications` và `harvest_reschedules`.
  `crop_seasons` có thêm cột thu hoạch, `treatment_applications` có thêm cột xác nhận (bản ghi có sẵn là `CONFIRMED`).
- Hộp thông báo trong app: `GET /api/v1/notifications` (phân trang, `unreadOnly`), `GET /api/v1/notifications/unread-count`, `PATCH /api/v1/notifications/{id}/read`, `PATCH /api/v1/notifications/read-all`.
  Mỗi tài khoản chỉ thấy thông báo của mình, trạng thái đã đọc lưu ở server.
  Thông báo được ghi cùng giao dịch khi duyệt hoặc từ chối thửa, vụ, yêu cầu sửa thửa, chẩn đoán và việc phun thuốc.
  Hộ cũng nhận thông báo khi admin khai thu hoạch thay và khi đến hạn thu hoạch.
- `POST /api/v1/crop-seasons/{id}/harvest` nhận `yieldKg` (bắt buộc), `actualHarvestDate` và `harvestedAreaHectares`, và lưu người khai.
  `actualHarvestDate` mặc định hôm nay, không ở tương lai, tối đa 30 ngày trước.
  `harvestedAreaHectares` mặc định cả thửa, không vượt diện tích thửa (`400`).
  Trước ngày dự kiến trả `422 HARVEST_TOO_EARLY` trừ khi có `confirmEarly=true` kèm `earlyReason`.
  Admin khai thay hộ phải có `reasonOnBehalf` (`400` nếu thiếu); lý do và người khai nằm trên vụ.
- `GET /api/v1/crop-seasons/{id}/harvest-readiness` trả số ngày còn lại tới ngày dự kiến, trạng thái cách ly và các cảnh báo không chặn.
  Cảnh báo gồm báo cáo chưa có kết quả duyệt và khuyến nghị đã duyệt chưa ghi nhận phun.
- `PATCH /api/v1/crop-seasons/{id}/reschedule-harvest` (FARMER, ADMIN) đổi ngày dự kiến từ hôm nay đến 90 ngày tới, kèm lý do.
  `GET /api/v1/crop-seasons/{id}/harvest-reschedules` trả đủ các lần đổi theo thứ tự.
- Job `HarvestReminderScheduler` chạy 07:00 giờ `Asia/Ho_Chi_Minh` (`app.harvest-reminder-cron` trong `application.yaml`).
  Vụ `ACTIVE` đã duyệt tới hoặc quá ngày dự kiến nhận một thông báo mỗi ngày (`dedup_key` duy nhất).
- Nông dân khai phun thuốc: `POST /api/v1/disease-reports/{reportId}/treatment-application/self-report` (FARMER) tạo bản ghi `PENDING_CONFIRMATION` và case `TREATMENT_CONFIRMATION` trong hàng đợi kỹ sư.
  `POST .../confirm` và `POST .../reject` (ENGINEER, ADMIN, từ chối cần lý do) quyết định bản khai; `GET .../treatment-application` trả bản ghi của báo cáo.
  Bản khai chờ xác nhận chặn thu hoạch (`422 TREATMENT_PENDING_CONFIRMATION`).
  Khóa PHI tính từ ngày phun nông dân khai và có hiệu lực khi kỹ sư xác nhận.
  Bị từ chối thì nông dân khai lại trên cùng bản ghi.
  `POST .../diagnosis/apply` của kỹ sư ghi nhận ngay ở trạng thái `CONFIRMED`; mỗi chẩn đoán có tối đa một lần phun.
- Mattermost kênh kỹ sư nhận tin khi có bản khai phun thuốc mới.

**Chưa làm:**

- Push thật (FCM, UnifiedPush): hộp thông báo chỉ kéo về khi mở app (P10).
- Hộp thông báo cho engineer và admin: họ thấy việc chờ ở hàng đợi và Mattermost.
- Phun lặp lại nhiều lần trên cùng một chẩn đoán.
- SLA nhắc kỹ sư khi bản khai phun chờ xác nhận quá lâu.
- Lịch sử thu hoạch thay hộ trong timeline workflow (hiện nằm trên vụ và log backend).

**Đã xong — Kiểm tra ảnh, yêu cầu chụp lại, chẩn đoán lại và gợi ý theo triệu chứng:**

- `V8__report_quality_and_diagnosis_history.sql`: `diagnoses` không còn unique theo `disease_report_id` và có `symptom_text`; `disease_reports` có trạng thái, lý do và liên kết chụp lại, kết quả chất lượng ảnh, `version`; bảng `training_labels`.
- `POST /api/v1/disease-reports/precheck` (FARMER) kiểm kích thước, độ sáng và độ nét của ảnh, không lưu gì; không xác nhận ảnh là lúa.
  Nộp báo cáo kiểm lại chất lượng ngoài transaction: ảnh không đạt trả `422`, AI không chạy trả `503`, ảnh lưu bỏ EXIF/GPS và báo cáo ghi kết quả chất lượng.
- `PATCH /api/v1/disease-reports/{id}/request-retake` (ENGINEER, ADMIN, lý do bắt buộc) đặt báo cáo `NEEDS_RETAKE` mà không đổi quyết định chẩn đoán.
  Hộ nhận thông báo `RETAKE_REQUESTED`, chẩn đoán đang chờ duyệt đóng thành `CANCELLED`; chẩn đoán đã `APPROVED` hoặc báo cáo đã yêu cầu rồi trả `409`.
  `POST .../disease-reports?retakeOf={id}` gửi ảnh thay thế: báo cáo gốc phải `NEEDS_RETAKE`, cùng vụ, chưa có ảnh thay thế; báo cáo trả `retakeOf` và `retakeIds`.
- Một báo cáo có nhiều chẩn đoán, bản mới nhất là hiện hành.
  `POST .../diagnosis/ai` chạy lại được, gọi AI ngoài transaction và trả `409` nếu có chẩn đoán mới hơn trong lúc chờ; `GET .../diagnosis/history` trả mọi bản.
  Chẩn đoán mới đóng workflow chờ của bản cũ; khóa PHI tính trên mọi lần phun của vụ.
- `POST .../diagnosis/suggest` (ENGINEER, ADMIN) xếp hạng bệnh trong danh mục từ triệu chứng, kèm evidence và nguồn, không lưu gì.
  `POST .../diagnosis` nhận `symptomText` tùy chọn; thiếu treatment rule được duyệt vẫn lưu chẩn đoán chờ khuyến nghị, không áp dụng được thuốc.
  Khi kỹ sư duyệt, `training_labels` ghi triệu chứng, mã bệnh, người duyệt và chẩn đoán bị thay.

**Chưa làm:**

- Hiệu chuẩn ngưỡng ảnh bằng ảnh ngoài ruộng.
- Báo cáo đánh giá AI (test set độc lập, chỉ số theo lớp): bỏ khỏi phase.

### 2026-10-06

**Đã xong — Duyệt thửa/vụ, mã chuẩn, GPS, danh mục giống:**

- `V5__parcel_season_approval.sql`, `V6__rice_variety_seed.sql`: trạng thái duyệt, mã, GPS và khóa `client_request_id` cho `parcels` và `crop_seasons`; bảng `rice_varieties`, `household_parcel_counter`, `parcel_edit_requests`.
  Thửa và vụ có sẵn coi như đã duyệt; thửa chưa có tọa độ mang cờ `location_missing`.
- Mã thửa `HTX01-H0042-T03` và mã vụ `HTX01-H0042-T03-2026DX` do backend sinh, quy tắc ở `development/docs/legacy_docs/PARCEL_SEASON_CODES.md`; `POST /api/v1/parcels` không nhận `code`, bắt buộc `latitude` và `longitude`.
- Nông dân phải gửi `gpsAccuracyMeters` không quá `app.gps-max-accuracy-meters` (30 m) và không `mockedLocation` (`422`).
- Thửa và vụ của nông dân chờ quản trị viên duyệt; của quản trị viên được duyệt ngay.
  Core `workflow` có `WorkflowDecisionHandler` theo `caseType`; `PATCH /api/v1/workflow-cases/{id}/approve` và `/reject` (ADMIN, từ chối cần lý do) áp quyết định lên thửa, vụ hoặc yêu cầu chỉnh sửa.
- `POST /api/v1/parcels/{id}/resubmit`, `POST /api/v1/crop-seasons/{id}/resubmit` (gửi lại sau khi bị từ chối), `POST /api/v1/parcels/{id}/edit-requests` (đổi diện tích hoặc vị trí thửa đã duyệt).
- Tạo vụ trên thửa chưa duyệt, gửi báo cáo hoặc thu hoạch trên vụ chưa duyệt trả `422`; tối đa `app.max-pending-requests` (3) yêu cầu chờ mỗi nông dân.
- `Idempotency-Key` cho `POST /api/v1/parcels` và `POST /api/v1/parcels/{code}/crop-seasons`.
- `GET /api/v1/rice-varieties` và `GET /api/v1/crop-seasons/suggestion`; `riceVarietyCode` thay `riceVariety`, ngày thu hoạch gợi ý là trung bình số ngày sinh trưởng, lệch khỏi khoảng phải kèm `reasonForOverride`.
  Danh mục giống ghi nguồn và `reviewStatus`; mọi giống còn `IN_REVIEW`.
- Loại vụ gợi ý theo tháng bắt đầu, cấu hình ở `app.season-start-months`.
- `GET /api/v1/approval-requests` (lọc loại, trạng thái, mã hộ, người nhận, khoảng ngày; phân trang) và `GET /api/v1/approval-requests/{caseId}` (kèm timeline) cho ENGINEER và ADMIN.
- `PATCH /api/v1/households/{code}/account` nhận `username` thay cho `keycloakUserId` (đúng một trong hai).
- Webhook Mattermost theo kênh: `MATTERMOST_WEBHOOK_URL_ADMIN` cho yêu cầu thửa và vụ, `MATTERMOST_WEBHOOK_URL_ENGINEER` cho báo cáo và chẩn đoán; kênh nào để trống dùng `MATTERMOST_WEBHOOK_URL`.

**Đã xong — Open-Core: workflow dùng chung, lưu ảnh có link hạn, package Core:**

- Package `core.identity` (`Household`, `HouseholdService`, `KeycloakAdminClient`), `core.workflow`, `core.storage` là phần Core; ứng dụng DX-Farm giữ các package còn lại.
  Sơ đồ ở `development/docs/submission/ARCHITECTURE_OPEN_CORE.md`.
- `V4__workflow.sql`: bảng `workflow_case` (trạng thái, `assigned_to`, optimistic lock) và `workflow_event` (chỉ thêm; trigger chặn `UPDATE` và `DELETE`, cột `metadata` JSONB).
  Chẩn đoán đã có được dựng case và lịch sử từ cột quyết định hiện có.
- Mỗi chẩn đoán mới mở case `DIAGNOSIS_APPROVAL`; duyệt hoặc từ chối ghi đúng một `workflow_event` cùng người thực hiện và lý do.
- `GET /api/v1/workflow-cases` (phân trang, lọc `caseType`, `status`, `assignedTo` hoặc `me`), `PATCH /api/v1/workflow-cases/{id}/assign` (nhận việc; case của người khác chỉ ADMIN nhận lại được), `GET /api/v1/workflow-cases/{id}/events`; chỉ ENGINEER và ADMIN.
- Ảnh báo cáo lưu trong bucket MinIO riêng tư với metadata `uploaded-by`, `source`; `StorageService` ký link có hạn (`storage.presign-ttl` trong `application.yaml`, mặc định 15 phút) theo đúng host mà client dùng để gọi API.
- `DiseaseReportResponse.imageRef` thành `imageUrl` (link có hạn).
  AI nhận link nội bộ hiệu lực 10 phút, thông báo Mattermost nhận link công khai hiệu lực 7 ngày.
- Bỏ `GET /uploads/**`, `app.upload-dir` và `app.public-base-url`; thêm `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `STORAGE_PUBLIC_URL` (không có giá trị mặc định).
- Test: `WorkflowServiceIntegrationTest` (PostgreSQL thật: lịch sử không ghi đè, chuyển trạng thái, danh sách chờ, nhận việc, chặn sửa/xóa event), `StorageServiceTest`, ma trận quyền cho `/workflow-cases`.

**Đã xong — Nông hộ, phân quyền, migration:**

- Schema do Flyway quản lý (`db/migration`): `V1` là baseline của schema cũ (DB tạo trước Flyway được đánh dấu đã ở V1 qua `baseline-on-migrate`), `V2` thêm `households` và `parcels.household_id`, `V3` thêm cột audit và xóa mềm. Hibernate chỉ `validate`.
- `Household` (mã `H0001`, `keycloakUserId` = claim `sub`); mỗi `farmerName` phân biệt của thửa cũ thành một hộ riêng (không phân biệt hoa thường), hộ chưa có tài khoản do admin gắn tay.
- Quyền của FARMER dựa trên hộ gắn với `sub`, không còn so tên; đổi tên trong Keycloak không mất thửa, hai nông dân trùng tên không thấy dữ liệu của nhau. Tài khoản chưa gắn hộ thấy danh sách rỗng và không tạo được thửa (`403`).
- `POST /api/v1/parcels` không nhận `farmerName`: FARMER luôn tạo cho hộ của mình, ADMIN truyền `householdCode`. Mã thửa trùng trả `409`.
- Ma trận quyền bằng `@PreAuthorize`: tạo thửa/vụ và thu hoạch chỉ FARMER và ADMIN, gửi báo cáo chỉ FARMER, xóa mềm và quản lý hộ chỉ ADMIN, danh sách hộ cho ENGINEER và ADMIN.
- Access token phải có `aud` = `dxfarm-api`; token chỉ mang role FARMER chỉ nhận khi `azp` là `dxfarm-mobile`, ngược lại `403`.
- `reportedByUserId`, `decidedByUserId` lưu `sub` bên cạnh tên hiển thị; idempotency của báo cáo so theo id người dùng.
- Xóa mềm `DELETE /api/v1/parcels/{id}`, `/crop-seasons/{id}`, `/disease-reports/{id}` (ADMIN); danh sách nhận `includeDeleted=true` (chỉ ADMIN). Thửa còn vụ mùa chưa xóa trả `409`; mã thửa và vụ ACTIVE dùng lại được sau khi xóa.
- `POST /api/v1/households` (ADMIN) tạo hộ và tài khoản Keycloak FARMER bằng service account `dxfarm-backend-admin`, trả mật khẩu tạm một lần; Keycloak lỗi thì không lưu hộ. `GET /api/v1/households` và `PATCH /api/v1/households/{code}/account` (gắn tài khoản có sẵn).
- Role `superset_ro` không đọc được `households`.


**Đã xong — Khóa PHI và ràng buộc vụ mùa:**

- `POST .../diagnosis/apply` nhận `appliedAt` trong khoảng từ hôm nay lùi tối đa 30 ngày; ngoài khoảng trả `422` với `errorCode` `INVALID_APPLIED_AT`.
- Ngày nghiệp vụ (hạn cách ly, trạng thái cách ly, thu hoạch, thời điểm duyệt) tính theo `Clock` cố định `Asia/Ho_Chi_Minh` (`TimeConfig`); image backend đặt `TZ=Asia/Ho_Chi_Minh`.
- Tạo báo cáo trên vụ đã `HARVESTED` trả `422` `CROP_SEASON_HARVESTED` trước khi lưu ảnh.
- Mỗi thửa chỉ có một vụ `ACTIVE`: service trả `409`, partial unique index `uq_crop_seasons_one_active_per_parcel` (`V1__baseline.sql`, `V5__parcel_season_approval.sql`) chặn race. `expectedHarvestDate` trước `startDate` trả `400`.
- `Diagnosis` và `CropSeason` có cột `version` (optimistic lock); ghi đè đồng thời trả `409`. `apply` tăng version của vụ mùa để không lọt qua một lần thu hoạch đang chạy song song.
- Upload ảnh: chỉ nhận JPEG/PNG theo nội dung thật (không theo `Content-Type` hay đuôi file), giới hạn 40 megapixel, giải mã rồi mã hóa lại nên file lưu không còn EXIF/GPS; hướng chụp EXIF được áp vào điểm ảnh.
- Mọi mật khẩu/secret trong `application.yaml` bắt buộc khai báo qua biến môi trường, không còn giá trị mặc định; log package ứng dụng mặc định `INFO` (`LOG_LEVEL_APP`).
- Test: Mockito cho khóa PHI, vụ mùa, báo cáo, lưu ảnh; `ConcurrencyIntegrationTest` chạy PostgreSQL qua Testcontainers cho `@Version` và partial unique index.


### 2026-10-02

**Đã xong — Xử lý khi AI Service không khả dụng:**

- Lỗi kết nối AI và circuit breaker mở được ánh xạ thành `HTTP 503` với thông báo ổn định cho người dùng, thay vì rơi vào handler `500 Internal server error`; frontend hiển thị thông báo này và tự cuộn tới banner lỗi.

**Đã xong — Jackson 3:**

- `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` và `AiDiagnosisClient` dùng `tools.jackson.databind.ObjectMapper` do Spring cung cấp thay vì tự khởi tạo.

**Đã xong — Thông báo Mattermost qua Incoming Webhook:**

- `DiseaseReportService.create()` publish `DiseaseReportCreatedEvent` sau khi lưu báo cáo mới; `DiagnosisService.create()` và `createFromAi()` publish `DiagnosisPendingEvent` sau khi lưu chẩn đoán `PENDING`.
  Báo cáo trả về từ nhánh idempotent (`Idempotency-Key` đã dùng) không publish.
- `MattermostNotificationListener` nhận event bằng `@TransactionalEventListener(AFTER_COMMIT)` và `@Async("mattermostNotifyExecutor")`; executor 2 thread, queue 100, tràn thì bỏ và ghi WARN.
- `MattermostWebhookClient` (`RestClient` + timeout cấu hình được) POST một `attachment` vào webhook; lỗi chỉ ghi một dòng WARN không chứa URL, không ném lên luồng nghiệp vụ.
- `MattermostMessageBuilder` dựng nội dung tiếng Việt; chuỗi người dùng cắt 100 ký tự, vô hiệu mention, link và markdown; thời điểm theo `Asia/Ho_Chi_Minh`.
- Cấu hình `mattermost.webhook.url|image-base-url|connect-timeout-ms|read-timeout-ms` (`MATTERMOST_WEBHOOK_URL` rỗng thì tắt).
- Thông báo báo cáo mới kèm ảnh qua `image_url` khi `MATTERMOST_IMAGE_BASE_URL` được đặt (backend `compose` truyền biến này).
- Không đổi endpoint, request/response hay Swagger.

### 2026-09-30

**Đã xong — Idempotency cho gửi báo cáo:**

- `POST /api/v1/crop-seasons/{id}/disease-reports` nhận header tuỳ chọn `Idempotency-Key` (UUID); key đã dùng thì trả báo cáo cũ mà không lưu ảnh lần hai.
- `DiseaseReport.clientRequestId` (`UUID`, unique, nullable); key thuộc vụ mùa khác hoặc user khác trả `409`, key sai định dạng trả `400`.
- Swagger khai báo header bằng `@Parameter`.

**Đã xong — Phạm vi dữ liệu của FARMER (phục vụ bản mobile):**

- User có role `FARMER` (không kèm `ENGINEER`/`ADMIN`) chỉ thấy thửa có `farmerName` khớp claim `name` của token, không phân biệt hoa thường.
  `CurrentUserService` có `isFarmer()` và `canAccess(Parcel)`; `ParcelRepository.findByFarmerNameIgnoreCase` phục vụ danh sách thửa.
- Endpoint thửa, vụ mùa (gồm `harvest`) và báo cáo bệnh trả `404` khi thửa không thuộc FARMER.
- `POST /api/v1/parcels` bởi FARMER lưu thửa với tên của chính họ, bỏ qua `farmerName` trong request.
- Với FARMER, `diagnosis` lồng trong báo cáo chỉ đầy đủ khi `APPROVED`; `PENDING` chỉ có `id`, `diseaseReportId`, `approvalStatus`; `REJECTED` thêm `decidedAt`, `rejectionReason`.
  ENGINEER và ADMIN nhận dữ liệu đầy đủ như trước.
- Swagger: `@Operation` của `ParcelController`, `CropSeasonController`, `DiseaseReportController` và schema `DiagnosisResponse` mô tả hành vi theo role.
- Đặc tả: `development/docs/pending_docs/mobile/BACKEND_CHANGES.md`.

**Đã xong — Chuyển seed sang bệnh lá lúa:**

- Seed 6 bệnh lá lúa active; pesticide, treatment rule và policy đang `IN_REVIEW` không được seed.
- Taxonomy, pesticide và rule cũ không còn tham gia lookup; bản ghi đang được lịch sử diagnosis/treatment tham chiếu được giữ lại ở trạng thái inactive.
- Migration đạt trên database mới và database legacy mô phỏng; Backend khởi động healthy với đúng 6 bệnh active.

### 2026-09-26

- Hoàn thiện seed idempotent từ dữ liệu nguồn: 1 treatment rule giả định cho E2E, 15 candidate còn lại bị chặn; rule stale được vô hiệu hóa và bảo toàn an toàn khi đã có lịch sử tham chiếu.
- Tối giản `data/`: loại 7 artifact báo cáo/đề xuất đã được master JSON, code và tài liệu hiện hành thay thế.
- Chuẩn hóa `data/` ở gốc repository làm nguồn RAG duy nhất, loại bản sao local khỏi luồng ingest E2E.
- Dọn hai tài liệu kế hoạch/checklist tách AI-Backend trung gian sau khi trạng thái cuối đã được phản ánh trong code và tài liệu hiện hành.

### 2026-09-16

**Đã xong — Phase 2 tách AI thành dịch vụ suy luận (P2-0..P2-6):**

- `AiDiagnosisClient` (`RestClient` + `JdkClientHttpRequestFactory`) gọi AI theo contract `POST /api/v1/diagnose`, timeout riêng + circuit breaker Resilience4j.
- Internal API read-only `GET /internal/v1/diagnosis-context` cho AI tra treatment rule + lịch sử phun theo `disease_code`, bảo vệ bằng service API key riêng (`ServiceApiKeyAuthenticationFilter`), chỉ cho GET.
- `DiagnosisController` có endpoint mới `POST .../diagnosis/ai` gọi `DiagnosisService.createFromAi()`: nhận CV/recommendation từ AI, đối chiếu `version` của treatment rule trước khi lưu, từ chối (409) nếu rule đã đổi trong lúc AI xử lý.
- Endpoint nhập tay `POST .../diagnosis` vẫn giữ cho testing/demo — cả hai cùng đổ vào một `Diagnosis`/vòng đời duyệt duy nhất qua `approve`/`reject`/`apply`.
- Xác nhận `DiseaseReportService` đã tự lưu `DiseaseReport` vào Backend DB từ Phase 0, không phụ thuộc AI ghi hộ.

**Đã xong — Phase 3 tách DB vật lý cho AI (P3-1..P3-4):**

- `AiVectorDatabaseSeeder` tự tạo database `dx_farm_ai_vector_db` (chỉ `documents`/`document_chunks`, extension `pgvector`) lúc khởi động.
- Hai role Postgres riêng cho AI: `ai_vector_ro` chỉ đọc khi runtime và `ai_vector_ingest` đọc/ghi khi ingest; AI không nhận credential truy cập Backend DB.
- `deploy/docker-compose.yml` chuyển AI runtime sang DB/role riêng và cấp `RAG_INGEST_DATABASE_URL` cho job ingest.
- Dữ liệu cũ do AI ghi trực tiếp trước khi tách DB (`disease_reports`, `diagnoses`, `treatment_applications`, `workflow_cases`/`workflow_events`) xác định là dữ liệu demo/test và không được giữ lại.

### 2026-09-11

**Đã xong — SSO thật qua Keycloak (Giai đoạn 2 [H]):**

- Xác thực qua OAuth2 Resource Server, verify token bằng `jwk-set-uri` trỏ vào Keycloak nội bộ.
  `KeycloakRoleConverter` map `realm_access.roles` thành `ROLE_FARMER`/`ROLE_ENGINEER`/`ROLE_ADMIN`.
- `CurrentUserService` lấy `reportedBy`/`decidedBy` từ claim token, không tra DB.
- `KeycloakDatabaseSeeder` tạo role/database Postgres riêng cho Keycloak, theo pattern `MattermostDatabaseSeeder`.
- Quản lý tài khoản qua Keycloak Admin Console.
  Chi tiết: `sso/README.md`.
- Đã chạy thật qua `deploy`, xác nhận 401/403 bằng access token thật.

### 2026-09-09

**Đã xong — Auth/SSO cơ bản (Giai đoạn 2 [H]):**

- JWT stateless (HS256, `spring-security` + `jjwt`), không session/cookie.
  `User` có 3 role `FARMER`/`ENGINEER`/`ADMIN`; seed đúng 1 `ADMIN` lúc khởi động từ biến môi trường, không có đăng ký công khai — chỉ `ADMIN` tạo user mới.
- `POST /api/v1/auth/login` trả JWT + role.
  Mọi API nghiệp vụ yêu cầu đăng nhập, trừ danh mục tham chiếu (đọc), `/auth/**`, swagger, `/uploads/**`.
- Điểm mấu chốt: bỏ hẳn field tên tự do (`reportedBy`/`approvedBy`/`rejectedBy`) khỏi request body — backend tự lấy actor từ `SecurityContextHolder`, không tin client gửi lên.
  Duyệt/từ chối/chẩn đoán giờ yêu cầu role `ENGINEER`/`ADMIN` (`@PreAuthorize`), enforce ở backend chứ không chỉ ẩn nút FE — cùng tinh thần poka-yoke 2 tầng như PHI.
- 401/403 trả đúng khuôn `ApiResponse` sẵn có, qua `RestAuthenticationEntryPoint`/`RestAccessDeniedHandler`/`GlobalExceptionHandler`.
- `User` implement thẳng `UserDetails` (thay vì bọc qua Spring's `User`) — bớt 1 query DB thừa mỗi lần cần biết actor đang thao tác.
- Đã build + test thật qua `deploy` (login, tạo user, full flow theo từng role, xác nhận 401/403 qua curl và trình duyệt).

**Đã xong — M7 (Giai đoạn 1):**

- `GET /api/v1/crop-seasons/{id}/isolation-status` — trả lời "ruộng này còn cách ly không?" bằng cách tái dùng đúng truy vấn mà `harvest()` dùng để chặn poka-yoke (một nguồn sự thật duy nhất giữa "hỏi" và "chặn").
  Trả `isolated`, `harvestLockedUntil`, `daysRemaining`, `pesticideCode` + câu trả lời tiếng Việt diễn giải facts — không qua LLM.

### 2026-09-08

**Đã xong — Phase 0 spine flow (M1–M6), đủ để demo end-to-end:**

- Khởi tạo project Spring Boot (Java 21, PostgreSQL, Docker/docker-compose), khung dùng chung: `ApiResponse<T>` (envelope response thống nhất), `GlobalExceptionHandler` (400/404/409/422/500), Swagger/OpenAPI tự sinh từ code.
- **Structured Knowledge** (danh mục, đọc qua API, seed sẵn qua `data.sql`): `Disease`, `Pesticide`, `TreatmentRule` (liều lượng + số ngày cách ly PHI).
- **Operational Data**: `Parcel` (thửa ruộng), `CropSeason` (vụ mùa), `DiseaseReport` (báo cáo sự cố kèm ảnh, upload multipart lưu local disk), `Diagnosis` (chẩn đoán + khuyến nghị + trạng thái duyệt), `TreatmentApplication` (bản ghi đã phun thuốc, snapshot liều/PHI).
- Luồng nghiệp vụ đầy đủ: báo bệnh có ảnh → tra `Disease`→`TreatmentRule` deterministic (không qua LLM) → tự sinh khuyến nghị tiếng Việt → kỹ sư Approve/Reject (có state machine chặn duyệt/áp dụng sai thứ tự) → ghi nhận đã phun thuốc, tự tính ngày hết cách ly → **poka-yoke**: gọi thu hoạch khi còn cách ly bị chặn cứng, trả `HTTP 422` kèm mã lỗi `POKA_YOKE_VIETGAP_VIOLATION`.

## Dữ liệu (`data`)

### 2026-10-12

- `rag/lab/` có corpus của DX-Lab: 10 tài liệu tự soạn (CC BY 4.0) ở `processed/lab_knowledge.json` và registry nguồn `source_registry.json`.

### 2026-10-09

- `pesticides_candidates_master.json` có 2 sản phẩm (`NEWTEC_300SC` được seed, `HELP_400SC` chờ duyệt); `treatment_rules_candidates_master.json` có 3 rule (2 `EXPERT_APPROVED` được seed, rule đốm nâu bị chặn).
- `rag/source_registry.json` có 10 nguồn, thêm `SRC_ADC_HELP_400SC`.
- `data/README.md` mô tả quy trình sinh lại `data.sql`: commit dữ liệu nguồn và file đã sinh cùng lúc, CI chạy `--check` và unit test của generator.

### 2026-09-30

**Đã xong — Thay bộ dữ liệu PlantVillage bằng dữ liệu bệnh lá lúa:**

- Chuẩn hóa 6 điều kiện lúa, gồm 5 lớp detection và một mã healthy; loại toàn bộ mã PlantVillage khỏi canonical JSON và seed SQL.
- Corpus RAG còn 6 tài liệu, truy vết qua 9 nguồn; dữ liệu thuốc, treatment rule và policy giữ nguyên trạng thái `IN_REVIEW` từ workbook.
- Generator, validator đối chiếu workbook, seed check và RAG dry-run đều đạt; `data.sql` được sinh deterministic từ dữ liệu chuẩn.

## Mobile (`mobile`, Expo)

### 2026-10-12

**Đã xong — Client `farm-mobile`:**

- Realm `dxos`, client `farm-mobile`; scheme `dxfarm://` và bundle `vn.dxfarm.mobile` là nhận diện của app.
- Khóa lưu phiên và hàng đợi tải lên có tiền tố `farm.`.

### 2026-10-11

**Đã xong — Loại thông báo mở rộng:**

- `AppNotification.type` và `relatedEntityType` nhận giá trị ngoài danh sách đã biết; thông báo không điều hướng được vẫn hiển thị tiêu đề và nội dung.

### 2026-10-07

- Thông báo lỗi từ Backend hiển thị bằng tiếng Việt theo `errorCode` (`src/api/errorMessages.ts`).
- Màn `notifications` là hộp thông báo của nông dân, chạm một thông báo để mở thửa, vụ hoặc báo cáo liên quan.
  Nút "Thông báo (n)" ở đầu màn Thửa.
  Số chưa đọc được tải khi mở app, mỗi lần app trở lại foreground, mỗi lần quay lại màn Thửa và khi kéo làm mới.
- Màn `seasons/harvest` khai báo thu hoạch (ngày, sản lượng kg, diện tích ha), hiện banner cách ly và các việc chưa xử lý.
  Thu hoạch trước ngày dự kiến phải bật xác nhận và nhập lý do.
- Màn `seasons/reschedule` đổi ngày dự kiến thu hoạch kèm lý do và liệt kê các lần đổi.
- Màn Chi tiết báo cáo cho nông dân khai "Tôi đã phun thuốc" (liều, ngày phun) khi chẩn đoán đã duyệt.
  Màn hiện trạng thái chờ hoặc đã được kỹ sư xác nhận, và lý do khi bị từ chối để khai lại.

- Màn `capture` kiểm ảnh trước khi gửi: kích thước và dung lượng trên máy, độ sáng và độ nét qua Backend; ảnh không đạt khóa nút gửi và hiện lý do.
  Mất mạng thì ảnh vào hàng đợi, Backend kiểm khi gửi.
- Báo cáo `NEEDS_RETAKE` hiện chip "Cần chụp lại" và lý do; nút "Chụp lại" gửi ảnh thay thế liên kết báo cáo cũ (`retakeOf` giữ trong hàng đợi offline), màn chi tiết có liên kết xem ảnh trước và ảnh chụp lại.
  Chẩn đoán bị từ chối hiện "Khuyến nghị bị từ chối", không còn nút chụp lại.

**Chưa làm:**

- Chưa xác nhận camera, hàng đợi offline và chuỗi chụp lại trên điện thoại thật.
- Hộp thông báo chỉ tải trang đầu (30 thông báo mới nhất), chưa có cuộn tải thêm.

### 2026-10-06

- Nông dân tạo thửa ngay trên điện thoại (`parcels/new`): nhập diện tích, bấm "Tôi đang ở ruộng, lấy vị trí" (`expo-location`), thấy sai số GPS; sai số quá 30 m hoặc vị trí giả lập thì không gửi được.
  Thửa chờ quản trị viên duyệt; bị từ chối thì sửa và gửi lại, thửa đã duyệt thì đề nghị chỉnh sửa.
- Tạo vụ mùa (`seasons/new`): chọn giống từ danh mục, ngày bắt đầu, loại vụ; ngày thu hoạch gợi ý lấy từ backend, chọn ngày khác khoảng gợi ý phải nêu lý do.
- Hàng đợi offline nhận thêm thửa và vụ mùa mới, mỗi mục một `Idempotency-Key`; danh sách chờ gửi ở màn Hồ sơ.
- Thẻ thửa và vụ hiện trạng thái duyệt và lý do từ chối.
- `EXPO_PUBLIC_API_BASE_URL` và `EXPO_PUBLIC_KEYCLOAK_URL` cùng trỏ tới gateway (`<IP-LAN>:9080`); ảnh báo cáo hiển thị từ `imageUrl` trong response.

### 2026-10-01

- Metro chạy ở cổng `8083` (script `npm start`) vì `8081` dành cho Backend.
- `mobile/README.md` ghi cách chạy khi web và mobile dùng Keycloak cùng lúc: điện thoại vào qua IP LAN ở `.env.local`, không cần đổi `deploy/.env`.

### 2026-09-30

**Đã xong — Khung app cho FARMER:**

- Expo SDK 57, TypeScript, `expo-router`, thư mục `mobile/` ngang hàng `frontend/`.
- `app.json`: scheme `dxfarm`, chuỗi quyền camera tiếng Việt; `app.config.ts` cho phép HTTP cleartext ngoài profile `production`.
- `mobile/.env.example`: `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_KEYCLOAK_URL`, `EXPO_PUBLIC_KEYCLOAK_CLIENT_ID`.
- `src/`: client API (`request`, `ApiError`, `json`, timeout 30 giây, lỗi mạng trả `status` 0), lưu token bằng `expo-secure-store`, type dùng cho farmer, theme theo design tokens, component `Screen`.

**Đã xong — Xác thực:**

- Đăng nhập Authorization Code + PKCE với client `dxfarm-mobile` qua `expo-auth-session`; endpoint dựng từ `EXPO_PUBLIC_KEYCLOAK_URL`, không dùng discovery.
- Access token và refresh token lưu ở `expo-secure-store`; token tự refresh trước khi hết hạn, refresh bị từ chối hoặc `401` thì xoá phiên và về màn Đăng nhập.
- Role đọc từ `realm_access.roles` của access token; tài khoản không có role FARMER bị đăng xuất kèm thông báo "Ứng dụng này dành cho nông dân".
- Đăng xuất kết thúc luôn phiên Keycloak (POST endpoint logout kèm refresh token).
- Route guard bằng `Stack.Protected` ở layout gốc; màn Đăng nhập và Hồ sơ.

**Đã xong — Màn hình đọc dữ liệu:**

- Thửa của tôi (badge "Đang cách ly" theo vụ đang hoạt động), Vụ mùa của thửa (vụ đang hoạt động ở trên, vụ đã thu hoạch mờ hơn), Báo cáo của vụ (lưới 2 cột kèm chip trạng thái).
- Banner cách ly từ `IsolationStatus`; ảnh hiển thị qua `/uploads/{imageRef}`.
- Kéo để làm mới, khung xương khi tải, trạng thái rỗng và lỗi kèm nút "Thử lại".
- Component dùng chung: `StatusChip`, `Card`, `EmptyState`, `ErrorState`, `IsolationBanner`; hook `useAsync`.

**Đã xong — Chụp, gửi và chi tiết báo cáo:**

- Chụp bằng camera hệ thống (`launchCameraAsync`), xin quyền camera và hướng dẫn mở Cài đặt khi bị từ chối.
- Nén bằng `expo-image-manipulator`: cạnh dài tối đa 1280px, JPEG 0.7.
- Gửi multipart part `image` qua `XMLHttpRequest` để có thanh tiến trình; lỗi hiển thị kèm nút "Gửi lại"; không thoát được màn hình khi đang gửi.
- Chi tiết báo cáo: timeline Đã gửi → Đã chẩn đoán → Kỹ sư duyệt theo `diagnosis` (`null`, `PENDING`, `APPROVED`, `REJECTED`); `APPROVED` hiện bệnh, khuyến nghị, thuốc, liều, số ngày cách ly; `REJECTED` hiện lý do và nút "Chụp lại".
- Danh sách báo cáo tự làm mới khi quay lại từ màn chụp; nút tròn nổi "Chụp" ở góc phải dưới.

**Đã xong — Hàng đợi gửi khi mất mạng:**

- Mất mạng hoặc lỗi kết nối lúc gửi thì ảnh được copy vào `documentDirectory` và ghi hàng đợi vào AsyncStorage; mỗi ảnh có UUID làm `Idempotency-Key`.
- Tự gửi khi `NetInfo` báo có mạng và khi app trở lại foreground; thử lại với khoảng chờ tăng dần, tối đa 8 lần; lỗi `4xx` (trừ `408`, `409`, `429`) đánh dấu "Lỗi".
- Dải "Chờ gửi (N)" ở màn Báo cáo của vụ; danh sách chờ gửi và nút xoá ở màn Hồ sơ; hàng đợi tách theo tài khoản.

**Đã xong — Đóng gói:**

- `mobile/eas.json` với profile `preview` xuất APK; hướng dẫn build bằng EAS trong `mobile/README.md`.

**Đã xong — Hướng dẫn chạy:**

- `mobile/README.md`: chạy thử trên điện thoại thật với IP LAN, biến môi trường, tài khoản demo, kiểm tra mã nguồn.

## AI Service (Python)

### 2026-10-12

**Đã xong — Corpus theo namespace:**

- Job ingest nhận `--corpus farm|lab` và thay thế tài liệu của đúng namespace đó.
- `POST /api/v1/disease/knowledge/retrieve` nhận `document_type` tùy chọn; không có thì tìm trong toàn namespace.

**Đã xong — Thuộc tính thiết bị của mô hình:**

- Thiết bị chạy mô hình CV lưu ở thuộc tính `_device`.

### 2026-10-11

**Đã xong — Namespace cho truy vấn RAG:**

- `POST /api/v1/disease/knowledge/retrieve` nhận `namespace` (mặc định `farm`); retriever chỉ đọc tài liệu cùng namespace.
- Bảng `documents` có cột `namespace` (mặc định `farm`); ingest của bệnh lúa ghi `farm`.

### 2026-10-08

- Docker/E2E nạp classifier trực tiếp từ file local; bỏ script và bước tải classifier trong E2E. Đưa `best.pt` và `crop_classifier.onnx` vào Git để checkout sạch có đủ trọng số, chỉ ignore cache tải.
- Bỏ SHA-256 và cấu hình checksum của crop classifier; giữ kiểm file/task/mapping/shape/dtype/output, ngưỡng và SHA-256 của detector bệnh.
- Kiểm chứng local: AI 64 test, Backend 79 test và E2E 10/10 bước qua; hai model nạp/suy luận được trong container không có mạng.

### 2026-10-07

- Thêm crop classifier ONNX vào precheck và chẩn đoán; Backend kiểm lại trước lưu, Mobile hiển thị lý do riêng cho chất lượng/chưa nhận là lúa. Rice threshold 0.80, margin 0.20; lỗi model trả 503. Model còn nhận nhầm ngô/cỏ/lúa mì, đang thử trong sản phẩm.
- Cập nhật checkpoint YOLOv8n RiceLeafBD lên artifact `3.1.0` (150 epochs); giữ class contract và confidence threshold 0.70.
- ONNX tải riêng theo revision/checksum cố định; Compose mount read-only, E2E tải trước khi dựng stack. README module, OpenAPI và license đã cập nhật.

- `requirements.lock` khóa phiên bản chính xác mọi gói Python; Dockerfile cài `requirements.txt` với file này làm constraints.
- Mọi file Python có SPDX header `Apache-2.0`.
- `POST /api/v1/disease/precheck` kiểm chất lượng ảnh (JPEG/PNG, tối đa 40 megapixel, mỗi chiều từ 160px, độ sáng 20–245, độ nét từ 20) và trả `passed`, `reasons`, kích thước, độ nét, độ sáng; chạy thêm crop classifier, không chạy detector bệnh.
  `predict` dùng chung bước kiểm này.
  Độ nét đo trên ảnh xám tối đa 640px, bỏ viền, trung vị theo 16 vùng và trừ nhiễu, nên sọc màn hình, nhiễu hạt, chữ và viền sắc không che được ảnh mờ.
- `POST /api/v1/disease/symptoms/suggest` xếp hạng các mã bệnh trong danh mục từ triệu chứng bằng RAG, kèm evidence và nguồn; điểm là độ tương đồng tài liệu, không phải confidence.

**Chưa làm:**

- Ngưỡng ảnh chưa hiệu chuẩn bằng ảnh ngoài ruộng; crop classifier còn nhận nhầm ngô/cỏ/lúa mì, chưa nghiệm thu khả năng chặn toàn bộ ảnh ngoài miền.

### 2026-10-06

- Mọi endpoint ngoài `/disease/health` và `/disease/class-contract` (gồm `/disease/predict`, `/disease/knowledge/retrieve`, `/diagnose`) yêu cầu header `X-Service-Api-Key`; thiếu hoặc sai key trả `401`, chưa cấu hình key trả `503`.
- Thêm `requirements-dev.txt` (pytest) và `tests/test_service_auth.py`.


### 2026-10-02

- Đưa checkpoint YOLOv8n đã xác thực vào `ai/weights/best.pt` để clone mới có thể chạy Compose mà không phải chép model thủ công; vẫn kiểm tra SHA-256 trước khi nạp.
- `AI_CV_WEIGHTS_PATH` cho phép override đường dẫn theo máy. Bind mount không tự tạo thư mục `best.pt` khi source bị thiếu, nên Compose dừng với lỗi cấu hình rõ ràng.
- Công khai hồ sơ `YOLOv8n-RiceLeafBD` 3.0.0: thêm toàn văn AGPL-3.0, nguồn train,
  attribution RiceLeafDiseaseBD Version 3 và checksum; rà license dependency AI trực tiếp
  và ghi nhận Ultralytics/PyMuPDF theo AGPL-3.0.

### 2026-09-30

**Đã xong — Chuyển CV và RAG sang bệnh lá lúa:**

- Thay EfficientNet bằng YOLOv8n với checkpoint cục bộ `best.pt`; kiểm tra checksum, task, số lớp và thứ tự class khi khởi động.
- Mapping class đọc từ `class_contract.json`; checkpoint được mount read-only và không tự tải model.
- RAG ingest 6 tài liệu thành 31 chunks; cả 6 mã lúa đi đúng Backend context/RAG và về `review_required` khi chưa có rule được duyệt.
- Giữ nguyên response CV năm trường và contract chẩn đoán; container CPU, kiểm thử tích hợp và E2E đều đạt.
- Bổ sung shared library cho OpenCV/Ultralytics; AI image build và healthcheck thành công.

### 2026-09-18

- Hoàn tất tích hợp AI stateless với Backend REST và AI Vector DB riêng sau ba phase decoupling.
- Runtime chỉ đọc RAG; job ingest sử dụng credential ghi riêng qua `RAG_INGEST_DATABASE_URL`.

### 2026-09-17

- Thêm timeout và retry giới hạn khi AI gọi internal API của Backend.
- Buộc AI trả lỗi khi Backend không cung cấp structured facts.

### 2026-09-16

- Xóa thành công 10 ORM model nghiệp vụ dư thừa khỏi AI Service.
- Xóa thành công repository/service CRUD và thao tác ghi dữ liệu nghiệp vụ khỏi AI runtime.
- Xóa thành công router workflow cũ, `WorkflowRunner`, `WorkflowTransitions` và schema liên quan khỏi AI Service.
- Xóa thành công endpoint legacy `POST /disease/reports/{report_id}/diagnose` khỏi AI Service.
- Thêm Backend REST client read-only để AI lấy field context và structured facts.
- Hoàn thành `POST /api/v1/diagnose`, kết hợp CV, RAG và structured facts từ Backend để trả recommendation đúng contract.
- Tách quyền truy vấn RAG của runtime khỏi quyền ghi corpus của job ingestion.

### 2026-09-14

**Đã xong — đóng gói Docker và chuẩn hóa cấu hình AI Service:**

- `ai/Dockerfile` đóng gói FastAPI trên cổng 8000 với healthcheck, non-root user, volume weights và 2-layer pip cache cho PyTorch.
- AI Service dùng schema PostgreSQL thống nhất; dữ liệu seed và embedding được ingest sẵn trước khi RAG/workflow truy vấn.

### 2026-09-11

**Đã xong — Luồng điều phối AI Spine Flow & Quản lý vòng đời khuyến nghị:**

- LangGraph điều phối spine flow: request chẩn đoán CV → field context → structured lookup → RAG → recommendation → checkpoint `PENDING`.
  `workflow_runner.py` và `workflow_transitions.py` nằm ở tầng application, tách biệt khỏi graph để orchestration không lẫn với HITL transition.
- Structured facts luôn lấy từ SQL: treatment dùng đúng rule; policy-only không tự sinh thuốc; RAG hoặc LLM lỗi/timeout tự động về `evidence_fallback`, giữ nguyên thuốc, hoạt chất, liều và PHI từ SQL.
- Durable HITL trong PostgreSQL: lưu `WorkflowCase` và `WorkflowEvent` append-only; giữ bất biến `APPROVED != APPLIED` (approve chỉ chuyển sang `WAITING_APPLIED`, chỉ `APPLIED` mới tạo `TreatmentApplication` và tính PHI).
- Tính toán PHI an toàn: `harvest_locked_until = applied_at + phi_days`, dùng khóa `MAX` để các lần phun sau không thể rút ngắn thời gian cách ly của lần phun trước.

### 2026-09-10

**Đã xong — Chuẩn hóa Vision API, Dữ liệu quy tắc & RAG Search:**

- Vision API chuẩn hóa trả `disease_code` và `disease_name` tiếng Việt khớp 1:1 với `Disease.code` trong PostgreSQL cho 38 lớp bệnh lý.
- Tái cấu trúc module `ai/app`: tách biệt `api`, `cv`, `db`, `rag`, `services` và `graph`; cấu hình tập trung qua `pydantic-settings` và load weights mô hình trong FastAPI lifespan.
- Định dạng quy tắc điều trị: đổi `TreatmentRule.dose` sang khoảng `dose_min`/`dose_max`, thêm bảng `DiseaseManagementPolicy` và validate liều thực tế `actual_dose` khi áp dụng.
- RAG search với pgvector: lọc chính xác theo `disease_code`, truy vấn Top-K chunk kèm metadata nguồn (`provenance`/citation URL) và fallback an toàn khi LLM không phản hồi.

### 2026-09-09

**Đã xong — Nền tảng dữ liệu RAG & Vision API cơ bản:**

- Khởi tạo SQLAlchemy data layer cho 8 entity spine-flow và 2 entity RAG (`Document`, `DocumentChunk`), bật extension pgvector với vector embedding 384 chiều.
- RAG prototype local: ingest 38 tài liệu tri thức bệnh cây trồng, tìm kiếm cosine similarity và tích hợp Qwen2.5-1.5B qua Ollama để giải thích ngữ cảnh.
- Vision API cơ bản: FastAPI endpoint `POST /api/v1/disease/predict` nhận ảnh và phân loại bệnh dựa trên mô hình PyTorch EfficientNet-B4.

## Frontend (`frontend`)

### 2026-10-12

**Đã xong — Client `dxos-web`:**

- Web đăng nhập realm `dxos` bằng client `dxos-web`; khóa lưu đường dẫn quay lại là `dxos.returnPath`.

### 2026-10-11

**Đã xong — Trạng thái case dạng chuỗi:**

- `CaseStatus` là chuỗi; `labels.ts` có `statusLabel` và `statusTone`, trạng thái chưa biết hiển thị nguyên giá trị với tông trung tính.

### 2026-10-07

- Thông báo lỗi từ Backend hiển thị bằng tiếng Việt theo `errorCode` (`src/errorMessages.ts`).
- Trang `/knowledge` ("Tài liệu") cho kỹ sư và admin: nhập câu hỏi, tùy chọn giới hạn theo bệnh, nhận các đoạn tài liệu kèm danh sách nguồn (link, giấy phép, trạng thái duyệt).

- Panel Chẩn đoán cho kỹ sư nhập triệu chứng, xem gợi ý bệnh kèm evidence và nguồn rồi chọn mã, chạy lại chẩn đoán AI và xem lịch sử chẩn đoán.
  Kỹ sư yêu cầu ảnh mới kèm lý do; báo cáo hiện "Cần chụp lại" và khóa việc chẩn đoán, duyệt, ghi nhận phun cho tới khi có ảnh mới.
- Hàng đợi kỹ sư có thêm tab "Khai phun thuốc".
  Kỹ sư nhận việc, bấm "Xác nhận đã phun" hoặc từ chối kèm lý do.
  Danh sách yêu cầu có thêm loại "Khai phun thuốc".
- Panel Vụ mùa cho admin khai thu hoạch thay nông hộ bằng form có sản lượng, diện tích và lý do thao tác thay mặt.
  Thu hoạch trước ngày dự kiến cần xác nhận và lý do.
  Form hiện cảnh báo cách ly và việc chưa xử lý trước khi gửi.

### 2026-10-06

- Router (`react-router-dom`): `/` luồng demo, `/requests` danh sách yêu cầu (lọc loại, trạng thái, mã hộ, ngày; phân trang; xem và duyệt ngay trong dòng), `/requests/:caseId` chi tiết kèm ảnh và timeline, `/queue` hàng đợi chẩn đoán (việc của tôi, chưa có người nhận).
  Menu theo role: ENGINEER và ADMIN thấy "Yêu cầu" và "Hàng đợi chẩn đoán".
  Đường dẫn `/admin/*` thuộc Keycloak ở gateway nên trang quản lý không dùng tiền tố đó.
- Đăng nhập xong quay lại đúng trang đã mở.
- Trang `/households` (ADMIN): thêm nông hộ cùng tài khoản đăng nhập (hiện mật khẩu tạm một lần), danh sách hộ, gắn tài khoản có sẵn theo tên đăng nhập.
- Luồng demo của ADMIN có nút xóa thửa, vụ mùa và báo cáo (có hỏi xác nhận); mọi thao tác của admin đều làm được trên giao diện.
- Form đăng ký thửa của admin bỏ ô mã thửa và bắt buộc vĩ độ, kinh độ; form vụ mùa chọn giống từ danh mục, loại vụ và ngày thu hoạch gợi ý lấy từ backend.
- Web phục vụ qua gateway cùng origin với API và Keycloak: nginx chỉ còn file tĩnh (bỏ proxy `/api` và `/uploads`), `VITE_API_BASE_URL` và `VITE_KEYCLOAK_URL` để trống thì dùng origin hiện tại.
- Ảnh báo cáo lấy từ `imageUrl` (link có hạn) thay vì ghép `/uploads/<tên file>`.
- Tài khoản chỉ có role `FARMER` thấy màn "Vui lòng dùng ứng dụng di động" và không gọi API nghiệp vụ.
- Admin chọn nông hộ khi đăng ký thửa; tạo vụ mùa và khai báo thu hoạch chỉ hiện với ADMIN; bỏ form gửi báo cáo (chỉ nông dân gửi từ mobile).


### 2026-10-01

- Đăng xuất giữ `id_token_hint`: `signOut` không xoá user trước `signoutRedirect`, nên Keycloak kết thúc phiên SSO ngay thay vì hiện trang xác nhận, và Superset đăng xuất theo.

### 2026-09-30

**Đã xong — Phù hợp phạm vi dữ liệu của FARMER:**

- `ParcelPanel`: khi đăng nhập bằng FARMER, ô "Tên chủ hộ" hiển thị tên người dùng và bị khoá; thửa luôn được đăng ký dưới tên đó.
- `DiagnosisPanel`, `Diagnosis` trong `types.ts`: các trường chẩn đoán có thể `null`; FARMER thấy trạng thái, và lý do từ chối khi `REJECTED`, còn nội dung chẩn đoán chỉ hiện khi `APPROVED`.

### 2026-09-11

**Đã xong — Đăng nhập qua Keycloak (SSO thật):**

- `src/auth.ts` dùng `oidc-client-ts` (Authorization Code + PKCE), phiên lưu ở `sessionStorage`.
- Chưa có phiên thì tự redirect sang Keycloak ngay lúc mount, không cần bấm nút — vào thẳng từ portal cũng tự vào được app.
  Callback lỗi (huỷ đăng nhập, tài khoản bị khoá...) thì dừng lại báo lỗi kèm nút thử lại, không lặp vô hạn.
  Đọc `fullName`/`role` từ claim token sau khi quay về.
- `src/api.ts` gắn Bearer token từ phiên OIDC; 401 tự xoá phiên, quay lại màn đăng nhập.
- Single logout: bật `monitorSession` để tự phát hiện khi phiên Keycloak kết thúc ở app khác (Superset) hoặc Keycloak Account Console, tự đăng xuất theo.
  Chi tiết: `sso/README.md`.
- Đã test qua trình duyệt thật với từng tài khoản demo trong `sso/README.md`, bao gồm bấm thẳng từ portal và đăng xuất chéo giữa App chính/Superset.

### 2026-09-09

**Đã xong — Demo UI cho Phase 0 spine flow (M1–M6):**

- React 18 + TypeScript + Vite, một trang duy nhất theo đúng spine flow: chọn/tạo thửa ruộng → vụ mùa → báo sâu bệnh kèm ảnh → chẩn đoán (nhập tay giả lập AI Service) → kỹ sư duyệt/từ chối → ghi nhận đã phun thuốc → khai báo thu hoạch (banner rõ khi bị poka-yoke chặn, HTTP 422).
- Tab "Danh mục tham chiếu" (read-only Disease/Pesticide/TreatmentRule) và nút hỏi "ruộng còn cách ly không? (M7)" ở panel Vụ mùa, gọi `GET /crop-seasons/{id}/isolation-status`.
- `src/api.ts` bọc `ApiResponse<T>` envelope, ném `ApiError` để UI phân biệt lỗi 400/409/422.
- Đã chạy full flow qua trình duyệt thật trên backend thật, không chỉ build/typecheck.

**Thay đổi kèm theo ở backend để FE chạy được:** thêm `WebConfig` (CORS cho `/api/**`, static handler `/uploads/**`).

**Chưa làm:** low-code frontend (ToolJet/Appsmith) — đây vẫn là bản tự viết tối giản cho demo.

**Đã xong — Đăng nhập + role, thay input tên tự do bằng danh tính đã xác thực:**

- `LoginPanel` mới gate toàn app; header hiện tên/vai trò + nút đăng xuất.
  `src/auth.ts` lưu `{token, username, fullName, role}` ở `localStorage`.
- `src/api.ts` tự gắn Bearer token; gặp 401 thì tự xoá phiên, quay lại màn đăng nhập.
- Bỏ input "Người báo cáo"/"Tên kỹ sư" — backend tự lấy từ user đã đăng nhập.
  Nút duyệt/từ chối/chẩn đoán chỉ hiện với role `ENGINEER`/`ADMIN` (ẩn FE là UX, chặn thật ở backend).
- Đã test qua trình duyệt thật với từng role, xác nhận UI và 401/403 đúng chỗ.

## Deploy (`deploy`)

### 2026-10-12

- `MATTERMOST_WEBHOOK_URL_TECHNICIAN` là webhook của kênh kỹ thuật viên khi chạy `DXOS_APP=lab`.

**Đã xong — Tên nền tảng `dxos` ở hạ tầng:**

- Database và tài khoản Postgres `dxos`, bucket `dxos-uploads` với route ảnh `/dxos-uploads/*`, email quản trị mặc định `admin@dxos.local`.
- `seed_demo.py` tạo vai và tài khoản `farmer1`, `engineer1`, `admin1` qua Keycloak Admin API bằng tài khoản quản trị trong `deploy/.env`; `e2e.py` chạy sau seed.
- `dxos_client.py` là client dùng chung của seed và E2E, gồm Keycloak Admin API.

### 2026-10-11

**Đã xong — Dữ liệu demo theo workspace:**

- `deploy/demo/seed_demo.py` thêm nông hộ mẫu vào bảng `workspaces` (loại `HOUSEHOLD`) và lấy mã từ `workspace_code_counter`.
- `application.yaml` giữ cấu hình dùng chung của core (`ai.service.*`, `knowledge.min-score`, `app.max-pending-requests`, `mattermost.webhook.url`); `application-farm.yaml` chỉ giữ phần của Farm.

### 2026-10-07

- `demo/seed_demo.py` tạo dữ liệu demo qua API: nông hộ (gắn `farmer1`), thửa có tọa độ ĐBSCL, vụ đang canh tác và đã thu hoạch, báo cáo kèm ảnh, chẩn đoán đã duyệt và đang chờ, yêu cầu chụp lại, một thửa chờ duyệt.
  Chạy lại không tạo trùng.
- `demo/e2e.py` chạy kịch bản đầu-cuối qua gateway: phạm vi dữ liệu, poka-yoke GPS và ngày thu hoạch, duyệt qua workflow, upload idempotent, kiểm tra chất lượng ảnh, duyệt chẩn đoán, khai phun thuốc và khóa thu hoạch.
- Workflow `E2E` (`.github/workflows/e2e.yml`) dựng stack rút gọn bằng `demo/docker-compose.e2e.yml`, nạp dữ liệu demo, thêm một rule thử nghiệm riêng cho CI (`demo/ci_fixture_rule.sql`) rồi chạy `e2e.py`.
- Gateway thêm route công khai `GET /api/v1/system/status`, giới hạn 120 lần mỗi phút.

### 2026-10-06

- Thêm biến `HTX_CODE`, `MATTERMOST_WEBHOOK_URL_ADMIN`, `MATTERMOST_WEBHOOK_URL_ENGINEER` vào `.env.example` và `docker-compose.yml`.
- Thêm `apisix` (standalone, `apisix/config.yaml`, `apisix/apisix.yaml`): route `/api`, `/realms`, `/admin`, link ảnh có hạn, Swagger và web; kiểm JWT bằng `openid-connect`, giới hạn 30 lần mỗi phút cho upload ảnh và gọi chẩn đoán AI (`429`).
  Chỉ gateway publish cổng: `APISIX_HOST_PORT` (web, API, Keycloak, ảnh, Swagger, portal ở `/portal/`) và `APISIX_SUPERSET_HOST_PORT` (listener thứ hai, chỉ chuyển tới Superset).
  Superset và portal không còn publish cổng riêng.
  Listener thứ ba `APISIX_MINIO_HOST_PORT` (9082) chuyển tới console MinIO (`--console-address :9001`, hỗ trợ websocket).
- Thêm `minio` build từ source (`minio/Dockerfile` ở thư mục `minio/` riêng, tag `RELEASE.2025-10-15T17-29-55Z`) vì image chính thức không còn phát hành; dữ liệu ở `deploy/data/minio`.
- Backend, Keycloak và frontend không còn publish cổng; bỏ `BACKEND_HOST_PORT`, `KEYCLOAK_HOST_PORT`, `FRONTEND_HOST_PORT`, `UPLOAD_DIR` và `APP_PUBLIC_BASE_URL`.
  Thêm `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `GATEWAY_PUBLIC_URL`.
- `.env.example` sắp lại theo bốn mục: địa chỉ và cổng, tài khoản đăng nhập (mỗi dịch vụ một khối: Keycloak, Superset, Mattermost, MinIO, PostgreSQL), khóa và secret nội bộ, cấu hình từng dịch vụ; có chú thích cách điền và nơi đăng nhập.
  `.env` dùng cùng bố cục, không còn biến khai báo trùng.
- `minio/migrate-uploads.sh` chuyển ảnh cũ vào bucket và kiểm từng file đã có trong bucket, không xoá thư mục cũ.
- `KEYCLOAK_ADMIN_CLIENT_ID` và `KEYCLOAK_ADMIN_CLIENT_SECRET` (secret bắt buộc, không mặc định) cho backend và Keycloak.


- Không còn mật khẩu/secret mặc định trong `.env.example`; mọi biến secret dùng `:?` nên Compose dừng và báo tên biến thiếu.
- `keycloak` nhận `SUPERSET_OIDC_CLIENT_SECRET` để thay vào realm lúc import.
- Cổng AI không publish ra host; bỏ `AI_HOST_PORT`.
- `reset.sh`: dừng stack, xoá `deploy/data` qua container tạm (tránh lỗi quyền root), dựng lại và chờ mọi service sẵn sàng.
- CI: `ci.yml` (backend `mvn verify` gồm test), `ci-frontend.yml`, `ci-mobile.yml` (`tsc --noEmit`), `ci-ai.yml` (`pytest`), `ci-infra.yml` (gitleaks trên lịch sử commit, kiểm tra `docker compose config` và việc Compose dừng khi thiếu secret). Cấu hình gitleaks ở `.gitleaks.toml`.


### 2026-10-02

- `backend` nhận biến tuỳ chọn `MATTERMOST_WEBHOOK_URL` (mặc định rỗng); `mattermost` bật `MM_SERVICESETTINGS_ENABLEINCOMINGWEBHOOKS`.
- `.env.example` có `MATTERMOST_WEBHOOK_URL` để trống kèm chú thích cách lấy giá trị.

### 2026-10-01

- Keycloak không đặt `KC_HOSTNAME`, dùng hostname của request: web vào `localhost:8180`, điện thoại vào qua IP LAN, hai bên chạy cùng lúc.
- Superset đọc tên, email và role từ access token thay vì gọi `/userinfo`, vì `/userinfo` từ chối token có issuer khác hostname của request.
- `VITE_KEYCLOAK_URL` của frontend và `KEYCLOAK_BROWSER_BASE` của Superset lấy từ `KEYCLOAK_PUBLIC_URL` (mẫu `http://localhost:8180`).

### 2026-09-30

- `docker-compose.yml` không chứa giá trị mặc định: mọi biến `${...}` bắt buộc khai báo trong `deploy/.env`, giá trị mẫu nằm ở `.env.example`; thiếu biến nào Compose dừng và báo tên biến đó. `KEYCLOAK_PUBLIC_URL` có giá trị mẫu `http://localhost:8180`.
- Đồng bộ tài liệu với cấu hình hiện tại: hướng dẫn `.env` bắt buộc, tài khoản Mattermost có sẵn, job `rag-ingest`/Ollama tự chạy, khoảng liều `dose_min`/`dose_max`, số liệu seed (38 bệnh, 6 thuốc, 1 rule active, 13 policy).
- Service `superset-init` chown `data/superset` về uid 1000 trước khi Superset khởi động, nên dựng mới không cần thao tác tay.
- Service `mattermost-bootstrap` tạo System Admin, team và channel mặc định (xem mục `chat`); biến `MATTERMOST_ADMIN_USERNAME`, `MATTERMOST_ADMIN_EMAIL`, `MATTERMOST_ADMIN_PASSWORD` trong `.env.example`.

### 2026-09-29

- `deploy/.env.example` là nơi duy nhất chứa mẫu cấu hình; các file `.env*` của `ai`, `backend/DX-Farm`, `frontend` đã bỏ.
- Biến môi trường đặt tên theo `<SERVICE>_<THIẾT_LẬP>`, nhóm theo service trong `.env.example`; `docker-compose.yml` ánh xạ sang tên nội bộ của từng service. Khóa giữa backend và AI đặt theo chiều: `BACKEND_TO_AI_API_KEY`, `AI_TO_BACKEND_API_KEY`.
- Cổng expose ra host đặt tên `<SERVICE>_HOST_PORT`; timeout backend gọi AI là `BACKEND_TO_AI_CONNECT_TIMEOUT_MS`/`BACKEND_TO_AI_READ_TIMEOUT_MS`; mật khẩu role Postgres phụ nêu rõ đích: `SUPERSET_READONLY_DB_PASSWORD`, `AI_VECTOR_DB_READONLY_PASSWORD`, `AI_VECTOR_DB_INGEST_PASSWORD`.
- `BACKEND_BASE_URL` là URL backend dùng chung cho AI và cho `APP_PUBLIC_BASE_URL` của backend.
- `frontend/Dockerfile` nhận `VITE_API_BASE_URL`, `VITE_KEYCLOAK_URL`, `VITE_KEYCLOAK_CLIENT_ID` qua build args, cấp từ `deploy/docker-compose.yml` (URL Keycloak theo `KEYCLOAK_HOST_PORT`).

### 2026-09-11

**Đã xong — service `keycloak` (SSO thật):**

- Thêm service `keycloak` (image chính thức `quay.io/keycloak/keycloak:26.0`), cổng mặc định `8180`.
  Tự import realm `dxfarm` từ `sso/realm-dxfarm.json` lúc khởi động, không cần thao tác tay.
- `.env.example`: `KEYCLOAK_ADMIN_PASSWORD`/`KEYCLOAK_DB_PASSWORD` thay cho secret JWT/admin cũ.
- Đã chạy thật bằng `docker compose up -d --build`.

### 2026-09-09

**Đã xong — đóng gói Postgres + backend + frontend chạy một lệnh:**

- `deploy/docker-compose.yml`: build backend (`backend/DX-Farm/Dockerfile`) + frontend (`frontend/Dockerfile` mới, multi-stage `npm run build` → serve bằng nginx).
  Expose `${POSTGRES_PORT:-5432}`, `${BACKEND_PORT:-8081}`, `${FRONTEND_PORT:-8082}`.
- Frontend production gọi API cùng-origin — nginx reverse-proxy `/api/` và `/uploads/` sang backend, nên deploy không cần CORS.
- Postgres + ảnh upload persist qua bind mount `deploy/data/` (không mất khi `docker compose down`).
- Đã build + chạy thử thật bằng `docker compose up -d --build`.
- Xoá `backend/DX-Farm/docker-compose.yml` cũ — `deploy/docker-compose.yml` giờ là compose file duy nhất trong repo (dùng chung cho demo full-stack lẫn Postgres khi dev thủ công).
- Service `backend` nhận thêm secret cho auth (`JWT_SECRET`, `JWT_EXPIRATION`, `SUPER_ADMIN_USERNAME/PASSWORD`) từ `deploy/.env` (mẫu ở `.env.example`).

## BI Dashboard (`bi`, Apache Superset)

### 2026-10-12

**Đã xong — Bundle dashboard của Farm:**

- Bundle là `bi/farm_dashboard_export.zip`, trỏ tới database `dxos`; Superset đăng nhập bằng client `dxos-superset`.

### 2026-10-06

- Tài khoản bị Superset từ chối (farmer) bị kết thúc luôn phiên Keycloak và quay về trang đăng nhập kèm cảnh báo, nên bấm "Sign In with keycloak" lần sau hiện form đăng nhập Keycloak thay vì tự đăng nhập lại và bị từ chối tiếp (`KeycloakAuthOAuthView`).
- Phiên đăng nhập cũ trỏ tới user không còn trong metadata DB (sau khi dựng lại dữ liệu) được coi là chưa đăng nhập, thay vì lỗi 500 (`KeycloakSecurityManager.load_user`).
- Superset chạy sau gateway ở `http://localhost:9081` (`APISIX_SUPERSET_HOST_PORT`), `ENABLE_PROXY_FIX` bật để link và redirect OAuth theo địa chỉ gateway; không còn publish cổng 8088.
- Tài khoản chỉ có role `FARMER` bị từ chối đăng nhập Superset; role `FARMER` không còn map sang role Superset.


### 2026-09-30

- Import dashboard mẫu chạy được trên metadata DB trống: khóa `passwords` của API import dùng đường dẫn đã bỏ thư mục gốc của zip.

### 2026-09-11

**Đã xong — SSO thật qua Keycloak:**

- `bi/superset_config.py` bật `AUTH_TYPE = AUTH_OAUTH`, đăng nhập UI bằng redirect sang Keycloak.
  `CustomSecurityManager` map claim `realm_access.roles` (đọc trực tiếp từ access token) sang role Superset: `ADMIN` → `Admin`, `ENGINEER`/`FARMER` → `Gamma`.
- Client confidential `dxfarm-superset` mới trong `sso/realm-dxfarm.json`.
- `bi/bootstrap.sh` tự `pip install authlib` lúc khởi động (không có sẵn trong image gốc, không build lại image).
- Tài khoản admin DB cục bộ vẫn dùng được qua REST API cho `bootstrap_datasources.py`, không phụ thuộc `AUTH_TYPE`.
- Single logout: `LOGOUT_REDIRECT_URL` khiến đăng xuất Superset cũng kết thúc phiên SSO ở Keycloak, không chỉ xoá cookie Superset.
  Client `dxfarm-superset` bật `frontchannelLogout` để nhận thông báo khi phiên SSO kết thúc từ nơi khác.
  Chi tiết: `sso/README.md`.
- Đã đăng nhập thật qua trình duyệt bằng `engineer1`/`admin1`, xác nhận role map đúng và đăng xuất chéo với App chính hoạt động đúng cả 2 chiều.

### 2026-09-10

**Đã xong — BI dashboard (Giai đoạn 2 [D]):**

- Thêm service `superset` (image chính thức `apache/superset:3.1.3`, không build lại) vào `deploy/docker-compose.yml`, lên cùng lệnh `docker compose up -d --build` sẵn có, cổng 8088.
  Bind mount script/dashboard từ thư mục `bi/` riêng.
- Backend tự tạo role Postgres chỉ-đọc `superset_ro` lúc khởi động (`SupersetReadOnlyRoleSeeder`, JDBC thuần — `data.sql` không tương thích với khối PL/pgSQL `DO $$ ... $$`).
- `bi/bootstrap.sh` + `bootstrap_datasources.py`: tự `db upgrade` + tạo admin + đăng ký database connection + 5 dataset + import dashboard mẫu qua REST API công khai của chính Superset.
  Không cần thao tác tay sau `docker compose up`.
  Idempotent, có retry.
- Dashboard mẫu "DX-Farm — Bản đồ & Trạng thái thửa ruộng": deck.gl Scatterplot vẽ thửa ruộng theo toạ độ + Pie chart tỉ lệ vụ mùa ACTIVE/HARVESTED.
  Build bằng browser thật (Claude in Chrome), verify bằng screenshot dữ liệu thật, export lại thành bundle YAML commit vào `bi/dxfarm_dashboard_export.zip` để tự import lại mỗi lần dựng từ đầu.
- `Parcel` có thêm 2 cột toạ độ tuỳ chọn `latitude`/`longitude` (backend + FE `ParcelPanel`) để dashboard vẽ bản đồ thật.
- Đã build + chạy thử thật, kể cả re-import dashboard nhiều lần — idempotent, không tạo trùng chart.
- **Còn thiếu**: chưa cấu hình `MAPBOX_API_KEY` nên bản đồ chưa có nền (basemap) thật, chỉ thấy điểm trên nền trắng — cần token Mapbox miễn phí, xem `bi/README.md`.

## Kênh chat nội bộ (`chat`, Mattermost)

### 2026-10-06

- Yêu cầu thửa và vụ mới đi vào kênh `Ban quản trị` qua `MATTERMOST_WEBHOOK_URL_ADMIN`; báo cáo mới và chẩn đoán chờ duyệt đi vào kênh `Kỹ sư nông nghiệp` qua `MATTERMOST_WEBHOOK_URL_ENGINEER`.
  Kênh không có webhook riêng dùng `MATTERMOST_WEBHOOK_URL`.

### 2026-10-02

**Đã xong — Thông báo tự động từ Backend:**

- Backend post "Báo cáo sâu bệnh mới" và "Chẩn đoán chờ duyệt" vào channel `Kỹ sư nông nghiệp` qua Incoming Webhook; webhook cấp tay một lần theo `chat/README.md`.
- Thông báo best-effort, không retry, tắt khi `MATTERMOST_WEBHOOK_URL` rỗng.
- Một chiều: duyệt/từ chối vẫn làm trong web app; không deep link; channel public nên mọi thành viên team đọc được thông báo.
- Mattermost vẫn dùng tài khoản riêng, không nối SSO.

### 2026-09-30

- `chat/bootstrap.sh` (service `mattermost-bootstrap`) tạo sẵn System Admin, team `dx-farm` và 3 channel: `Thông báo chung`, `Kỹ sư nông nghiệp` (public), `Ban quản trị` (private); idempotent.

### 2026-09-11

**Quyết định — không nối SSO cho Mattermost:**

- Team Edition (bản free đang dùng) không hỗ trợ OpenID Connect với IdP tuỳ ý — tính năng đó thuộc Mattermost Enterprise (trả phí).
- Có thể lách bằng provider "GitLab" (generic OAuth2 built-in) trỏ sang Keycloak, nhưng cần custom protocol mapper giả dạng shape JSON của GitLab user và dựa vào hành vi nội bộ không được Mattermost chính thức hỗ trợ cho IdP ngoài GitLab — quyết định không làm.
- App chính và BI Dashboard đã đăng nhập qua Keycloak, xem `sso/README.md`.

### 2026-09-10

**Đã xong — Kênh chat nội bộ (Giai đoạn 2 [H]):**

- Thêm service `mattermost` (image chính thức `mattermost/mattermost-team-edition:10.5`, không build lại) vào `deploy/docker-compose.yml`, lên cùng lệnh `docker compose up -d --build` sẵn có, cổng 8065.
- Backend tự tạo role + database Postgres riêng `mattermost_app`/`mattermost` lúc khởi động (`MattermostDatabaseSeeder`, JDBC thuần vì `CREATE DATABASE` không chạy được trong khối PL/pgSQL `DO $$ ... $$`) — tách biệt hoàn toàn khỏi CSDL nghiệp vụ.
- Dữ liệu chat persist qua bind mount `deploy/data/mattermost/`.
- Rà soát license kỹ vì Mattermost không dùng một license duy nhất: server core AGPLv3 (OSI-approved), `server/enterprise/` là Mattermost Source Available License (không OSI-approved, không dùng tới), bản binary Team Edition được Mattermost, Inc. cấp phép lại MIT riêng cho đúng binary đó.
  Ghi chi tiết ở `LICENSES.md` và `chat/README.md`.
- Tài khoản Mattermost tách biệt hoàn toàn khỏi tài khoản backend (FARMER/ENGINEER/ADMIN) có chủ đích — nền tảng riêng, không dùng SSO chung.

## Portal (`portal`)

### 2026-10-07

- Mục "Trạng thái dịch vụ" ở cuối trang hiện hoạt động hoặc ngừng của ứng dụng chính, backend, AI, lưu trữ ảnh, Keycloak, BI và chat, làm mới mỗi 30 giây.

### 2026-10-06

- Portal phục vụ ở `/portal/` của gateway; link app chính, Keycloak Admin Console và Swagger là đường dẫn tương đối, thêm card MinIO Console; link Superset, MinIO Console và chat tự theo tên máy đang mở trang.

### 2026-10-01

- Thêm mục "Quản trị & tài liệu": Keycloak Admin, Superset Admin (`/users/list/`), Mattermost Admin (System Console) và Swagger của Backend.

### 2026-09-11

**Đã xong — bấm link từ portal vào thẳng app đã đăng nhập SSO:**

- Link BI Dashboard trỏ thẳng `/login/keycloak` thay vì trang chủ Superset — vào thẳng luồng OAuth, không dừng ở màn hình "Sign In with keycloak".
- Kết hợp với frontend tự redirect khi chưa có phiên: bấm app nào từ portal cũng tự chuyển sang Keycloak nếu chưa đăng nhập, xong quay lại đúng app đó; đã đăng nhập app này rồi thì bấm app kia (App chính/BI Dashboard) không cần đăng nhập lại.
- Đã test qua trình duyệt thật: bấm portal → chưa có phiên → Keycloak → quay lại đúng app; bấm app còn lại → vào thẳng không cần đăng nhập lại.

### 2026-09-10

**Đã xong — trang portal tĩnh, bước đệm trước SSO:**

- Trang tĩnh (nginx, không build step) liệt kê link tới 3 ứng dụng hiện có (Ứng dụng chính, BI Dashboard, Kênh chat nội bộ) — người dùng không cần tự nhớ URL từng app.
- Thêm service `portal` vào `deploy/docker-compose.yml`, cổng mặc định `8090` (`PORTAL_PORT`).
- Đã build + chạy thử thật qua `docker compose up -d`, xác nhận trang load đúng.
- **Còn thiếu**: mỗi app đích vẫn đăng nhập riêng — portal chỉ là danh sách link tĩnh, sẽ trở thành landing page thật khi SSO (Keycloak/Authentik) được triển khai.

## Identity Provider (`sso`, Keycloak)

### 2026-10-12

**Đã xong — Realm nền tảng `dxos`:**

- `sso/realm-dxos.json` chứa client `dxos-web`, `farm-mobile`, `dxos-superset`, `dxos-backend-admin`, mapper audience `dxos-api` và vai `ADMIN` của nền tảng.
- Vai của app do backend tạo lúc khởi động; tài khoản mẫu do seed của app tạo.

### 2026-10-06

- Client `dxfarm-frontend` nhận redirect URI và web origin `http://localhost:9080` (origin của gateway); client `dxfarm-superset` nhận `http://localhost:9081` (listener Superset của gateway).
- Keycloak không publish cổng; trình duyệt và điện thoại đăng nhập qua gateway, backend vẫn xác thực bằng `jwk-set-uri` nội bộ.
- Client `dxfarm-frontend` và `dxfarm-mobile` có mapper thêm `dxfarm-api` vào `aud` của access token.
- Client `dxfarm-backend-admin` (service account, `manage-users`, `view-users`, `view-realm`) cho backend tạo tài khoản nông hộ; secret từ `KEYCLOAK_ADMIN_CLIENT_SECRET`.


- `secret` của client `dxfarm-superset` trong `realm-dxfarm.json` là `${SUPERSET_OIDC_CLIENT_SECRET}`, lấy từ `deploy/.env` lúc import. Mật khẩu 3 tài khoản demo vẫn nằm trong realm và được ghi rõ là chỉ dùng demo.


### 2026-09-30

**Đã xong — Client cho bản mobile:**

- Client public `dxfarm-mobile` trong `realm-dxfarm.json`: Standard Flow + PKCE, không direct access grants, redirect URI `dxfarm://*` và `exp://*`.
- Keycloak trong `deploy/docker-compose.yml` dùng hostname của request để điện thoại truy cập qua IP LAN.
- `sso/README.md`: phạm vi, cách dev với IP LAN, cách thêm client vào volume cũ.

### 2026-09-11

**Đã xong — SSO thật (Giai đoạn 2 [H]):**

- Realm `dxfarm` (`sso/realm-dxfarm.json`): 3 realm role, client public `dxfarm-frontend` (PKCE), client confidential `dxfarm-superset`, 3 tài khoản demo.
- Backend, frontend và BI Dashboard (Superset) đều xác thực qua Keycloak.
  Chi tiết + trade-off hostname nội bộ vs. trình duyệt: `sso/README.md`.
- Single logout (front-channel + session monitoring): đăng xuất ở app nào cũng kết thúc phiên SSO ở Keycloak, các app khác đang mở tự đăng xuất theo.
  Giới hạn: không phải backchannel logout chuẩn OIDC (chỉ hoạt động khi tab còn mở).
  Chi tiết: `sso/README.md`.
- Mattermost là nền tảng chat tách biệt có chủ đích, không nối SSO — xem `chat/README.md`.

## Rà soát license (PoF)

### 2026-10-07

- `LICENSES.md` khớp dependency hiện có (Resilience4j, AspectJ, Hibernate Validator, react-router-dom, expo-location, pgvector, base image).
- SBOM CycloneDX của backend, AI, frontend, mobile ở `sbom/`.
- `CONTRIBUTING.md`, mẫu issue và pull request ở `.github/`.

### 2026-09-11

**Đã xong:**

- Thêm Keycloak (Apache-2.0) và `oidc-client-ts` (MIT) vào `LICENSES.md`, cả hai OSI-approved.
- Thêm Authlib (BSD-3-Clause, OSI-approved) vào `LICENSES.md` — pip package cho SSO OAuth của Superset.

### 2026-09-10

**Đã xong:**

- `LICENSES.md` (gốc repo): rà soát license từng thành phần thật sự đang chạy (backend, frontend, deploy).
  Toàn bộ OSI-approved (Apache-2.0/MIT/BSD/PostgreSQL License).
- Chốt dứt khoát bỏ n8n khỏi kiến trúc (Sustainable Use License, không OSI-approved) — chưa từng tích hợp vào code.
- Thêm `LICENSE` (Apache-2.0 toàn văn) ở gốc repo.
- **Còn thiếu**: SPDX header đầu mỗi file mã nguồn tự viết — việc sửa hàng loạt file, để riêng một lượt khác.

## Tài liệu (root, `development/docs`, `ai/`)

### 2026-10-09

- `development/docs/_template/`: mẫu phase dùng chung, không gắn với một core cụ thể.
- `development/docs/legacy_docs/PARCEL_SEASON_CODES.md`, `development/docs/submission/ARCHITECTURE_OPEN_CORE.md`: vị trí hiện hành của quy chuẩn mã thửa/vụ và sơ đồ Open-Core.
- `development/docs/improvement_docs/REMAINING_WORK.md`: tổng hợp việc còn lại sau P0 đến P8.
- `development/docs/pending_docs/CV_RICE_IMAGE_VALIDATION_SUMMARY.md`: tóm tắt kiểm ảnh lúa bằng crop classifier.
- Trạng thái treatment rule trong `README.md`, `data/README.md`, `deploy/demo/README.md`, `development/docs/submission/` và `improvement_docs/` khớp seed hiện hành.

### 2026-10-07

- `README.md` có bảng mức hoàn thiện từng tính năng (trong code, kiểm thử, dữ liệu demo), hướng dẫn dữ liệu demo, kiểm thử và E2E.
- `development/docs/submission/`: kịch bản demo, bộ trả lời hội đồng và dàn ý báo cáo kỹ thuật.
- `development/docs/legacy_docs/PARCEL_SEASON_CODES.md`: mã thửa, mã vụ, GPS và quy tắc duyệt.
- `deploy/demo/README.md`: script seed demo, E2E và workflow `E2E`.
- Phase 5 đến 8 của `development/docs/improvement_docs` có kế hoạch, checklist và kết quả kiểm thử.

### 2026-10-06

- `development/docs/submission/ARCHITECTURE_OPEN_CORE.md`: sơ đồ 3 tầng Open-Core và sơ đồ luồng request (Mermaid), ranh giới Core và ứng dụng.
- `README.md` gốc có bảng đăng nhập vào từng service (tài khoản ứng dụng và tài khoản quản trị riêng); `minio/README.md` mô tả thư mục MinIO và console; `README.md` mô tả gateway, workflow dùng chung, lưu ảnh có link hạn; `LICENSES.md` thêm APISIX, MinIO và MinIO Java SDK.
- `README.md` có bảng tài khoản demo, hướng dẫn `deploy/reset.sh` và cách chạy test backend; `LICENSES.md` ghi thêm Testcontainers, JUnit/Mockito/AssertJ, gitleaks và pytest (chỉ dùng cho test/CI).
- Phase 1 của `development/docs/improvement_docs` ở trạng thái Xong, kèm kết quả kiểm thử.

### 2026-10-05

**Đã xong:**

- `development/docs/improvement_docs/`: `PROJECT_IMPROVEMENTS.md` tổng hợp vấn đề cải thiện và đối chiếu đề thi HaUI 2026, template phase (`_template/`) và kế hoạch chia phase.
- `development/docs/pending_docs/`: tài liệu mobile và kế hoạch thông báo Mattermost.
- `development/docs/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`: đề thi tuyển chọn nội bộ.

### 2026-10-02

**Đã xong:**

- `development/docs/pending_docs/MATTERMOST_WEBHOOK_NOTIFICATION_PLAN.md`: thiết kế, danh sách công việc và hạn chế của thông báo Mattermost.
- `chat/README.md` (mục thông báo tự động và cấp webhook), `README.md` gốc (Giai đoạn 2 [H], trạng thái, bảng truy cập).

### 2026-09-30

**Đã xong:**

- `development/docs/pending_docs/mobile/`: kế hoạch, đặc tả backend, thiết kế UI và `CHANGES_OVERVIEW.md` tổng hợp hành vi cũ và mới của nhánh mobile.
- `mobile/README.md`, `LICENSES.md` (mục Mobile), `sso/README.md` (client `dxfarm-mobile`), `README.md` gốc (client mobile, trạng thái).

### 2026-09-24

**Đã xong:**

- Thống nhất luồng seed: `data/scripts/generate_seed_sql.py` sinh `data.sql` từ `data/structured/*.json`; chỉ seed record có cờ eligibility `true`.
  Quy ước hai cơ chế seed ghi ở `README.md`.
- Thêm entity `DiseaseManagementPolicy` (bảng `disease_management_policies`), seed 13 guardrail qua generator.
  `treatment_rules` có ràng buộc duy nhất `(disease_id, pesticide_id)` nên seed chạy đồng thời vẫn không trùng.
  `treatment_rules` chưa có dòng nào vì cả 16 candidate đều `eligible_for_seed=false`.
- `ai/app/rag/source_registry.py`, `ai/app/rag/ingestion_service.py` đọc corpus từ `data/rag/...` ở gốc repository.
- `deploy/docker-compose.yml`, `deploy/.env.example` bind-mount `data/` vào `/data` cho container `rag-ingest`.
- `data/README.md`: mapping `dose_min`/`dose_max` khớp đúng `TreatmentRule` hiện tại.
- `README.md`, `ai/README.md`: đánh dấu `DiseaseManagementPolicy` đang ở giai đoạn kế hoạch, chưa có entity/bảng thật.

### 2026-09-15

**Đã xong:**

- Từng bổ sung kế hoạch và checklist triển khai tách AI/Backend theo phase; hai tài liệu trung gian này được dọn sau khi hoàn tất và hợp nhất trạng thái cuối vào tài liệu hiện hành.
- Cập nhật `README.md`, `ai/README.md`, `ai/app/graph/README.md`, `AI_EVENT_SCHEMA_README.md` để mô tả trạng thái kiến trúc tại thời điểm triển khai.
- Dọn `development/docs/`: chỉ còn kế hoạch/checklist/contract hiện hành; tài liệu thiết kế gốc chuyển vào `legacy_docs/`.
