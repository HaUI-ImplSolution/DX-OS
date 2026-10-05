# Checklist: Phase 5

Mã dòng: `WP5-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, DATA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP5-03: Hộp thông báo trong app

Kết quả đầu ra: farmer xem được thông báo mới trong app; đánh dấu đã đọc đồng bộ qua backend.

**Backend**

- [ ] WP5-03-BE-01 Thêm `model/Notification.java`, `repository/NotificationRepository.java`.
- [ ] WP5-03-BE-02 Thêm `service/NotificationService.java` (`create`, `listForUser`, `markRead`, `markAllRead`).
- [ ] WP5-03-BE-03 Thêm `controller/NotificationController.java` (`GET`, `PATCH .../read`, `PATCH .../read-all`).

**Mobile**

- [ ] WP5-03-MB-01 Màn hình hộp thông báo, poll khi mở app và khi quay lại foreground.

**Data, seed, migration**

- [ ] WP5-03-DATA-01 Migration thêm bảng `notifications`.

**Test**

- [ ] WP5-03-TEST-01 Tạo thông báo cho farmer, farmer `GET` thấy đúng, không thấy thông báo của hộ khác (T5-01).
- [ ] WP5-03-TEST-02 Đánh dấu đã đọc, `GET` lại không còn trong danh sách chưa đọc (T5-02).

## WP5-01: Khai báo thu hoạch — xác nhận sớm và dữ liệu thực tế

Kết quả đầu ra: thu hoạch sớm bị chặn nếu thiếu xác nhận; dữ liệu sản lượng/diện tích được lưu đầy đủ.

**Backend**

- [ ] WP5-01-BE-01 Thêm `dto/HarvestRequest.java`.
- [ ] WP5-01-BE-02 `CropSeason`: thêm `yieldKg`, `harvestedAreaHectares`, `harvestedByUserId`, `earlyReason`.
- [ ] WP5-01-BE-03 `CropSeasonService.harvest(...)`: nhận `HarvestRequest`, kiểm `confirmEarly`/`earlyReason` nếu `actualHarvestDate` trước `expectedHarvestDate`.
- [ ] WP5-01-BE-04 Gọi `NotificationService.create(...)` sau khi thu hoạch thành công.

**Data, seed, migration**

- [ ] WP5-01-DATA-01 Migration thêm 4 cột trên `crop_seasons`.

**Test**

- [ ] WP5-01-TEST-01 Thu hoạch sớm không kèm `confirmEarly` → 422 (T5-03).
- [ ] WP5-01-TEST-02 Thu hoạch hợp lệ kèm đủ dữ liệu → lưu đúng, farmer nhận thông báo (T5-04).

## WP5-02: Job nhắc hạn và gia hạn ngày dự kiến

Kết quả đầu ra: vụ quá hạn dự kiến mà chưa thu hoạch → farmer nhận đúng 1 thông báo/ngày; gia hạn có lịch sử.

**Backend**

- [ ] WP5-02-BE-01 Thêm `service/HarvestReminderScheduler.java` (`@Scheduled`, tần suất theo Q2).
- [ ] WP5-02-BE-02 Endpoint `PATCH .../crop-seasons/{id}/reschedule-harvest`.
- [ ] WP5-02-BE-03 Ghi lịch sử gia hạn (bảng mới hoặc tái dùng `workflow_event`).

**Test**

- [ ] WP5-02-TEST-01 Vụ quá hạn, chạy job → đúng 1 thông báo tạo ra, chạy job lại cùng ngày → không tạo thêm (T5-05).
- [ ] WP5-02-TEST-02 Gia hạn 2 lần liên tiếp → 2 bản ghi lịch sử riêng biệt (T5-06).

## WP5-04: Farmer tự khai "đã phun", kỹ sư xác nhận lại

Kết quả đầu ra: farmer tự khai không làm khóa PHI có hiệu lực ngay; chỉ có hiệu lực sau khi kỹ sư xác nhận.

**Backend**

- [ ] WP5-04-BE-01 `TreatmentApplication`: thêm `reportedByUserId`, `confirmedByUserId`, `confirmationStatus`.
- [ ] WP5-04-BE-02 Endpoint `POST .../treatment-applications/self-report` (role FARMER).
- [ ] WP5-04-BE-03 Endpoint `PATCH .../treatment-applications/{id}/confirm` (role ENGINEER/ADMIN, giữ `@PreAuthorize` như `apply` hiện tại).
- [ ] WP5-04-BE-04 `harvestLockedUntil` chỉ có hiệu lực chặn khi `confirmationStatus = CONFIRMED`.
- [ ] WP5-04-BE-05 Tạo `WorkflowCase(caseType = TREATMENT_CONFIRMATION)` khi farmer tự khai, để kỹ sư thấy trong danh sách chờ.

**Data, seed, migration**

- [ ] WP5-04-DATA-01 Migration thêm 3 cột trên `treatment_applications`; dữ liệu cũ set `confirmation_status = CONFIRMED`.

**Test**

- [ ] WP5-04-TEST-01 Farmer tự khai đã phun → khóa PHI chưa có hiệu lực, hiện trong danh sách chờ xác nhận (T5-07).
- [ ] WP5-04-TEST-02 Kỹ sư xác nhận → khóa PHI có hiệu lực từ thời điểm xác nhận (T5-08).

## WP5-05: Admin thao tác thay farmer — ghi log rõ ràng

Kết quả đầu ra: admin thao tác thay farmer có audit rõ ràng (ai, thay cho ai, lý do).

**Backend**

- [ ] WP5-05-BE-01 `HarvestRequest`: thêm `onBehalfOfHouseholdId`, `reasonOnBehalf`.
- [ ] WP5-05-BE-02 Ghi `metadata` của `WorkflowEvent` đủ 3 thông tin khi admin thao tác thay.

**Test**

- [ ] WP5-05-TEST-01 Admin khai thu hoạch thay farmer, không kèm lý do → 400 (T5-09).
- [ ] WP5-05-TEST-02 Admin khai thu hoạch thay farmer kèm đủ lý do → audit thể hiện đúng "thao tác thay mặt" (T5-10).

## Đóng phase

- [ ] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (mobile) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, bằng tiếng Anh.
- [ ] Swagger khớp code.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
