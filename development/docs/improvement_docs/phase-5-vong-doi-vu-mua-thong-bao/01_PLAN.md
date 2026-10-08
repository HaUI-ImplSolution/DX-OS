# Plan thực hiện: Phase 5

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP5-03 (hộp thông báo) chặn WP5-02 (job nhắc hạn cần kênh để bắn thông báo) và một phần WP5-01/04/05 (các nơi cần báo cho farmer).
WP5-01 (khai báo thu hoạch), WP5-04 (farmer tự khai đã phun), WP5-05 (admin thao tác thay) độc lập về code với nhau.
Thứ tự: WP5-03 → (WP5-01 ∥ WP5-02 ∥ WP5-04 ∥ WP5-05).

## WP5-03: Hộp thông báo trong app

- Mục tham chiếu: mục 13, 15 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: farmer có một nơi xem thông báo trong app, không cần dịch vụ push ngoài (theo D5, phase 0).
- Ước lượng: M.

**Hiện trạng đã rà soát**

- Mobile hiện không có thư viện thông báo nào, không có bảng `Notification` ở backend.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Admin duyệt thửa/vụ, kết quả chẩn đoán, nhắc hạn thu hoạch | Farmer không biết trừ khi tự mở app kiểm tra từng màn | Có bản ghi `Notification` cho farmer, hiện trong hộp thông báo khi mở app |
| Xem thông báo | Không áp dụng | `GET /api/v1/notifications` (phân trang, farmer chỉ thấy của mình) |
| Đánh dấu đã đọc | Không áp dụng | `PATCH /api/v1/notifications/{id}/read`, `PATCH .../read-all` |

**Hướng làm**

- `model/Notification.java`: `id`, `recipientUserId` (claim `sub`), `type` (enum: `PARCEL_APPROVED`, `PARCEL_REJECTED`, `CROP_SEASON_APPROVED`, `HARVEST_REMINDER`, `RETAKE_REQUESTED`, ...), `title`, `body`, `relatedEntityType`/`relatedEntityId` (để deep-link), `readAt` (nullable), `createdAt`.
- `service/NotificationService.java`: `create(recipientUserId, type, ...)` dùng chung cho mọi nơi cần báo farmer (gọi từ WP5-01/02/04 và P4's duyệt thửa/vụ).
- Mobile: màn hình hộp thông báo, poll khi mở app và khi quay lại foreground (theo đúng D5, không dùng push).

**Phạm vi ảnh hưởng**

- Module và file chính: `model/Notification.java`, `repository/NotificationRepository.java`, `service/NotificationService.java`, `controller/NotificationController.java` (mới); mobile — màn hình mới + logic poll.
- Endpoint, bảng DB, biến môi trường: `GET /api/v1/notifications`, `PATCH .../read`, `PATCH .../read-all` (mới); bảng `notifications` (mới).
- Role và client bị ảnh hưởng: farmer (mobile); engineer/admin có thể dùng chung cơ chế này sau nếu cần (ngoài phạm vi phase).
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng, tính năng hoàn toàn mới.

**Kết quả đầu ra**

- Farmer mở app sau khi admin duyệt thửa → thấy thông báo mới trong hộp thông báo.
- Đánh dấu đã đọc, mở lại app trên thiết bị khác → vẫn thấy đã đọc (đồng bộ qua backend, không phải local-only).

**Phụ thuộc, lưu ý**: chặn WP5-01/02/04/05 (các WP này gọi `NotificationService.create(...)` khi cần báo farmer).

## WP5-01: Khai báo thu hoạch — xác nhận sớm và dữ liệu thực tế

- Mục tham chiếu: mục 4, 10 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: thu hoạch sớm phải xác nhận có chủ đích; có dữ liệu sản lượng/diện tích thu hoạch thật, không chỉ mỗi mốc thời gian.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `service/CropSeasonService.harvest(Long id)`: không nhận tham số nào ngoài `id`; chỉ set `status = HARVESTED`, `harvestedAt = Instant.now()`.
  Không có kiểm tra ngày hiện tại so với `expectedHarvestDate`; không có trường sản lượng/diện tích thu hoạch; không ghi ai thực hiện hành động.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Thu hoạch trước `expectedHarvestDate` mà không có cờ xác nhận | Chấp nhận ngay | 422, yêu cầu `confirmEarly=true` kèm `earlyReason` |
| Khai báo thu hoạch | Chỉ lưu `harvestedAt = now` | Nhận thêm `actualHarvestDate`, `yieldKg` (hoặc tạ, thống nhất đơn vị), `harvestedAreaHectares`; ghi `harvestedByUserId` |
| Còn khuyến nghị (chẩn đoán) đã `APPROVED` chưa `apply`, hoặc báo cáo còn `PENDING` | Không chặn, không cảnh báo | Cảnh báo (không chặn cứng — theo mục 10, cần chốt mức độ) khi thu hoạch mà còn việc chưa xử lý |

**Hướng làm**

- `DiagnosisApplyRequest`... (không áp dụng ở đây, xem WP5-04); cho `harvest`: đổi `CropSeasonService.harvest(Long id)` thành nhận `HarvestRequest(actualHarvestDate, yieldKg, harvestedAreaHectares, confirmEarly, earlyReason)`.
- Check `actualHarvestDate.isBefore(season.getExpectedHarvestDate())` (dùng `Clock` từ P1) → bắt buộc `confirmEarly=true` + `earlyReason` không rỗng, ghi vào audit (không chỉ popup FE, theo đúng nguyên tắc chặn 2 tầng).
- Sau khi thu hoạch thành công, gọi `NotificationService.create(...)` báo xác nhận cho farmer (và admin nếu là admin thao tác thay, xem WP5-05).

**Phạm vi ảnh hưởng**

- Module và file chính: `dto/HarvestRequest.java` (mới), `service/CropSeasonService.java`, `model/CropSeason.java` (thêm `yieldKg`, `harvestedAreaHectares`, `harvestedByUserId`, `earlyReason`), `controller/CropSeasonController.java`.
- Endpoint, bảng DB, biến môi trường: `POST .../crop-seasons/{id}/harvest` đổi request body; `crop_seasons` thêm 7 cột.
- Role và client bị ảnh hưởng: farmer (mobile) khai báo thu hoạch chính mình; admin (web) khai thay (xem WP5-05).
- Tương thích ngược và dữ liệu cũ: vụ mùa đã `HARVESTED` trước phase này không có dữ liệu sản lượng — chấp nhận `NULL`, không backfill.

**Kết quả đầu ra**

- Thu hoạch sớm không kèm `confirmEarly` → 422.
- Khai báo thu hoạch hợp lệ lưu đủ sản lượng/diện tích/ngày thực tế.

**Phụ thuộc, lưu ý**: cần WP5-03 (hộp thông báo) để báo kết quả cho farmer.

## WP5-02: Job nhắc hạn và gia hạn ngày dự kiến

- Mục tham chiếu: mục 4 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: đến ngày dự kiến thu hoạch mà chưa khai báo, hệ thống tự nhắc farmer và cho chốt lại ngày mới, có lịch sử gia hạn.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- Không có `@Scheduled` nào trong `backend/DX-Farm` (xác nhận lại khi code); không có cơ chế gia hạn ngày dự kiến nào hiện có.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Đến `expectedHarvestDate` mà vụ còn `ACTIVE` | Im lặng | Job định kỳ (theo Q2) tạo `Notification(type = HARVEST_REMINDER)` cho farmer |
| Farmer chốt lại ngày dự kiến mới | Không áp dụng | `PATCH .../crop-seasons/{id}/reschedule-harvest` (body: `newExpectedHarvestDate`, `reason`), ghi vào bảng lịch sử gia hạn (lần 1, lần 2...) |

**Hướng làm**

- `@Scheduled(cron = ...)` (tần suất theo Q2) quét `CropSeason` có `status = ACTIVE` và `expectedHarvestDate <= LocalDate.now(clock)`, chưa có `Notification(type = HARVEST_REMINDER)` nào tạo trong ngày hôm đó (tránh bắn lặp), tạo thông báo qua `NotificationService`.
- Entity `HarvestReschedule` (bảng `harvest_reschedules`) ghi mỗi lần gia hạn: ngày cũ, ngày mới, lý do, người đổi, thời điểm.
  Workflow chỉ có vòng duyệt, không biểu diễn được việc đổi ngày.

**Phạm vi ảnh hưởng**

- Module và file chính: `service/HarvestReminderScheduler.java` (mới), `controller/CropSeasonController.java` (endpoint reschedule mới), `model/CropSeason.java` hoặc tái dùng `workflow_event`.
- Endpoint, bảng DB, biến môi trường: `PATCH .../crop-seasons/{id}/reschedule-harvest`, `GET .../harvest-reschedules` (mới); bảng `harvest_reschedules` (mới); `HARVEST_REMINDER_CRON`.
- Role và client bị ảnh hưởng: farmer (mobile).
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng, job chỉ xét vụ còn `ACTIVE` từ thời điểm chạy trở đi.

**Kết quả đầu ra**

- Vụ mùa quá hạn dự kiến mà chưa thu hoạch → farmer nhận đúng 1 thông báo nhắc/ngày (không spam).
- Gia hạn 2 lần liên tiếp → có đủ 2 bản ghi lịch sử, không ghi đè nhau.

**Phụ thuộc, lưu ý**: cần WP5-03 (hộp thông báo) đã có trước; cần `Clock` bean từ P1 để chạy đúng múi giờ.

## WP5-04: Farmer tự khai "đã phun", kỹ sư xác nhận lại

- Mục tham chiếu: mục 10 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: người thực sự phun thuốc (farmer) tự khai, kỹ sư xác nhận lại thay vì kỹ sư tự nhập thay.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `controller/DiagnosisController.java`: class-level `@PreAuthorize("hasAnyRole('ENGINEER','ADMIN')")` — farmer hiện không gọi được endpoint `apply` nào.
  UI mobile hiện ghi "Ghi nhận việc phun thuốc do kỹ sư thực hiện" (theo `PROJECT_IMPROVEMENTS.md` mục 10).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Ghi nhận đã phun thuốc | Kỹ sư tự nhập `appliedAt` | Farmer tự khai (`POST .../treatment-application/self-report`), tạo bản ghi `PENDING_CONFIRMATION` chặn thu hoạch; kỹ sư xác nhận (`POST .../confirm`) thì khóa PHI chính thức có hiệu lực |
| Khóa PHI có hiệu lực | Ngay khi kỹ sư `apply` | Chỉ sau khi kỹ sư xác nhận bản khai của farmer (giữ nguyên nguyên tắc: dữ liệu quyết định khóa PHI phải qua người có trách nhiệm xác nhận, không chỉ farmer tự khai là đủ) |

**Hướng làm**

- `TreatmentApplication` thêm `reportedByUserId` (farmer), `confirmedByUserId` (kỹ sư, nullable cho tới khi xác nhận), `confirmationStatus` (enum: `PENDING_CONFIRMATION`/`CONFIRMED`/`REJECTED`).
  `harvestLockedUntil` tính từ ngày phun nông dân khai; chỉ bản `CONFIRMED` giữ khóa PHI, bản `PENDING_CONFIRMATION` chặn thu hoạch tạm, bản `REJECTED` không chặn.
  `apply` của kỹ sư là đường "kỹ sư tự xác nhận luôn": bản ghi tạo ra là `CONFIRMED`.
- Endpoint `POST /disease-reports/{reportId}/treatment-application/self-report` cho farmer (role `FARMER`), `POST .../confirm` và `POST .../reject` cho kỹ sư và admin.
- Bản khai mới xuất hiện trong danh sách chờ của kỹ sư (`workflow_case`, `caseType = TREATMENT_CONFIRMATION`, tái dùng từ P3) và gửi tin vào kênh Mattermost của kỹ sư.

**Phạm vi ảnh hưởng**

- Module và file chính: `model/TreatmentApplication.java`, `service/DiagnosisService.java`, `controller/TreatmentApplicationController.java`.
- Endpoint, bảng DB, biến môi trường: `/disease-reports/{reportId}/treatment-application` (`GET`, `POST self-report`, `POST confirm`, `POST reject`); `treatment_applications` thêm `reported_by_user_id`, `confirmed_by_user_id`, `confirmed_at`, `confirmation_status`, `rejection_reason`.
- Role và client bị ảnh hưởng: farmer (mobile, tự khai mới), engineer (xác nhận thay vì tự nhập từ đầu).
- Tương thích ngược và dữ liệu cũ: bản ghi `TreatmentApplication` cũ (kỹ sư tự `apply`) coi như `confirmationStatus = CONFIRMED` ngay từ đầu qua migration, không yêu cầu xác nhận lại.

**Kết quả đầu ra**

- Farmer tự khai đã phun → chưa khóa PHI ngay, xuất hiện trong danh sách chờ xác nhận của kỹ sư.
- Kỹ sư xác nhận → khóa PHI có hiệu lực đúng từ thời điểm xác nhận, không phải thời điểm farmer khai.

**Phụ thuộc, lưu ý**: Q1 (một chẩn đoán có được `apply` nhiều lần không) ảnh hưởng trực tiếp thiết kế này — nếu cho phép nhiều lần, `confirmationStatus` phải theo từng `TreatmentApplication` riêng chứ không theo `Diagnosis`.

## WP5-05: Admin thao tác thay farmer — ghi log rõ ràng

- Mục tham chiếu: mục 4 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: khi admin bấm hộ farmer (khai thu hoạch, v.v.), phải ghi rõ "thao tác thay mặt" trong audit, không chỉ hiện popup cảnh báo.
- Ước lượng: S.

**Hiện trạng đã rà soát**

- Hiện tại không có khái niệm "admin thao tác thay farmer" ở tầng audit — mọi hành động chỉ ghi người gọi API thật (nếu có, sau P2 là `reportedByUserId`/`decidedByUserId`), không phân biệt "làm cho chính mình" hay "làm thay người khác".

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Admin khai thu hoạch thay farmer | Không phân biệt, chỉ ghi admin là người gọi API (nếu có audit) | Request bắt buộc `reasonOnBehalf`; vụ lưu người khai và lý do, backend ghi log, hộ nhận thông báo |

**Hướng làm**

- `HarvestRequest` (WP5-01) thêm `reasonOnBehalf`, bắt buộc khi người gọi không phải FARMER (ADMIN).
  Hộ được thay lấy từ vụ mùa nên không cần `onBehalfOfHouseholdId`.
- Vụ mùa lưu người khai (`harvested_by_user_id`, `harvested_by_name`) và `harvest_on_behalf_reason`; backend ghi log và gửi thông báo cho hộ.
  Thu hoạch không có workflow case nên không ghi vào `workflow_event.metadata`.

**Phạm vi ảnh hưởng**

- Module và file chính: `dto/HarvestRequest.java`, `service/CropSeasonService.java`, `model/CropSeason.java`.
- Endpoint, bảng DB, biến môi trường: không có bảng mới, thêm cột trên `crop_seasons`.
- Role và client bị ảnh hưởng: admin khai thay hộ qua `reasonOnBehalf`.
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng, field mới đều tuỳ chọn.

**Kết quả đầu ra**

- Admin khai thu hoạch thay farmer → log/audit thể hiện rõ ràng, không lẫn với farmer tự khai.

**Phụ thuộc, lưu ý**: cần WP5-01 (endpoint `harvest` mới).

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST /crop-seasons/{id}/harvest` | Body mới: `yieldKg` (bắt buộc), `actualHarvestDate`, `harvestedAreaHectares`, `confirmEarly`, `earlyReason`, `reasonOnBehalf` | Có |
| Endpoint | `GET /crop-seasons/{id}/harvest-readiness` | Mới: ngày còn lại, cách ly, cảnh báo không chặn | Không |
| Endpoint | `PATCH /crop-seasons/{id}/reschedule-harvest`, `GET /crop-seasons/{id}/harvest-reschedules` | Mới | Không |
| Endpoint | `/disease-reports/{reportId}/treatment-application`: `GET`, `POST self-report`, `POST confirm`, `POST reject` | Mới | Không |
| Endpoint | `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all` | Mới | Không |
| Response | `CropSeasonResponse` | Thêm `actualHarvestDate`, `yieldKg`, `harvestedAreaHectares`, `harvestedByName`, `earlyReason`, `harvestOnBehalfReason` | Không |
| Response | `TreatmentApplicationResponse` | Thêm `confirmationStatus`, `reportedByFarmer`, `rejectionReason` | Không |
| Bảng DB | `crop_seasons` | Thêm `actual_harvest_date`, `yield_kg`, `harvested_area_hectares`, `harvested_by_user_id`, `harvested_by_name`, `early_reason`, `harvest_on_behalf_reason` | Không (thêm cột) |
| Bảng DB | `treatment_applications` | Thêm `confirmation_status`, `reported_by_user_id`, `confirmed_by_user_id`, `confirmed_at`, `rejection_reason`; bản ghi cũ là `CONFIRMED` | Không (thêm cột) |
| Bảng DB | `notifications`, `harvest_reschedules` | Bảng mới (`V7__season_lifecycle_notifications.sql`) | Không |
| Case workflow | `TREATMENT_CONFIRMATION` (entity `TREATMENT_APPLICATION`) | Mới, quyết định qua endpoint riêng của kỹ sư | Không |
| Cấu hình | `HARVEST_REMINDER_CRON` (mặc định `0 0 7 * * *`, múi giờ `Asia/Ho_Chi_Minh`) | Mới, chưa có trong `deploy/.env.example` | Không |

## Dữ liệu, migration và rollback

- Migration: thêm cột cho `crop_seasons`/`treatment_applications`, bảng `notifications`, qua Flyway (đã có từ P2).
- Dữ liệu cũ: `treatment_applications` cũ set `confirmation_status = CONFIRMED` qua migration (không yêu cầu xác nhận lại hồi tố).
- Rollback: mọi cột mới đều nullable/có default, rollback bằng migration ngược không mất dữ liệu nghiệp vụ.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D5 (kế thừa phase 0) | Hộp thông báo GET + PATCH đánh dấu đã đọc | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | Giữ đúng 1 lần phun/chẩn đoán | Giữ ràng buộc unique, ít thay đổi nhất; bản khai bị từ chối được khai lại trên cùng bản ghi |
| P5-D1 | Bản khai chờ xác nhận vẫn chặn thu hoạch (422 `TREATMENT_PENDING_CONFIRMATION`) | Nông dân khai thật không thiệt hơn khai gian; khóa PHI tính từ ngày phun đã khai |
| P5-D2 | Việc tồn đọng khi thu hoạch chỉ cảnh báo, không chặn | Backend trả `GET .../harvest-readiness` để app hiện trước khi gửi |
| P5-D3 | Lịch sử đổi ngày ở bảng `harvest_reschedules`; thu hoạch hộ ghi ở cột của vụ | Workflow chỉ có vòng duyệt, không biểu diễn việc đổi ngày hay thu hoạch |
| P5-D4 | Endpoint thu hoạch giữ `POST`; đổi ngày là `PATCH`; khai phun nằm ở `/disease-reports/{id}/treatment-application` | Khớp endpoint hiện có và báo cáo là khóa tự nhiên của lần phun |
| Q2 | Job nhắc hạn chạy 1 lần/ngày buổi sáng | Đủ cho nhu cầu nhắc hạn ngày, tránh làm phiền farmer |
