# Checklist: Phase 3

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP3-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, AI, DATA, INFRA, TEST.
Mỗi dòng là một việc không quá nửa ngày và có thể kiểm chứng.
Dòng BE và DATA ghi module trong ngoặc vuông: `[core-api]`, `[core]`, `[farm]`, `[lab]`, `[host]`.

## WP3-01: Bảng `audit_log` và `AuditRecorder`

Kết quả đầu ra: ghi được một sự kiện audit; sửa hoặc xóa dòng bị database từ chối.

**Backend**

- [ ] WP3-01-BE-01 [core-api] `AuditRecorder`, `AuditEvent`, `AuditOutcome`.
- [ ] WP3-01-BE-02 [core] `AuditLogEntry`, `AuditLogRepository`, `AuditService` (giao dịch riêng, lỗi ghi chỉ log).

**Data, seed, migration**

- [ ] WP3-01-DATA-01 [core] `V4__audit_log.sql`: bảng, index, trigger append-only.

**Test**

- [ ] WP3-01-TEST-01 Case T3-01, T3-06.

## WP3-02: Ngữ cảnh request (`request_id`, IP, client)

Kết quả đầu ra: log backend và bản ghi audit có `request_id` và IP thật.

**Backend**

- [ ] WP3-02-BE-01 [core] Xác nhận header APISIX chuyển cho backend; filter ghi `requestId` và IP vào MDC.
- [ ] WP3-02-BE-02 [core] `application.yml`: mẫu log có `requestId`.

**Test**

- [ ] WP3-02-TEST-01 Case T3-02.

## WP3-03: Ghi 401 và 403

Kết quả đầu ra: 403 và 401 có token tạo bản ghi, có giới hạn chống làm đầy bảng.

**Backend**

- [ ] WP3-03-BE-01 [core] `RestAccessDeniedHandler` và `ClientRestrictionFilter` ghi `ACCESS_DENIED`.
- [ ] WP3-03-BE-02 [core] `RestAuthenticationEntryPoint` ghi `AUTH_FAILED` khi có bearer token.
- [ ] WP3-03-BE-03 [core] Bộ giới hạn theo nguồn trong bộ nhớ, đếm phần bị bỏ trong `metadata`.

**Test**

- [ ] WP3-03-TEST-01 Case T3-03, T3-04.

## WP3-04: Ghi thao tác quản trị

Kết quả đầu ra: mỗi thao tác trong danh sách ở Q4 tạo một bản ghi.

**Spec và tài liệu**

- [ ] WP3-04-DOC-01 Chốt danh sách thao tác (Q4) và ghi vào `CONTRIBUTING.md`.

**Backend**

- [ ] WP3-04-BE-01 [core] `WorkspaceService`: tạo, liên kết, thêm thành viên.
- [ ] WP3-04-BE-02 [core] `KeycloakAdminClient`: gán vai.
- [ ] WP3-04-BE-03 [farm] Xóa mềm thửa và vụ, thu hoạch sớm.
- [ ] WP3-04-BE-04 [lab] Thao tác quản trị đã chốt ở Q4.

**Test**

- [ ] WP3-04-TEST-01 Case T3-05.

## WP3-05: API đọc audit cho ADMIN

Kết quả đầu ra: ADMIN lọc được audit; vai khác nhận 403.

**Spec và tài liệu**

- [ ] WP3-05-DOC-01 Swagger: `@Tag` và `@Operation` bằng tiếng Anh trước khi code.

**Backend**

- [ ] WP3-05-BE-01 [core] DTO record `AuditLogResponse`, truy vấn có phân trang và lọc.
- [ ] WP3-05-BE-02 [core] `AuditLogController` với `@PreAuthorize("hasRole('ADMIN')")`.

**Test**

- [ ] WP3-05-TEST-01 Case T3-07.

## WP3-06: Cảnh báo Mattermost

Kết quả đầu ra: vượt ngưỡng tạo một tin; Mattermost tắt không hỏng nghiệp vụ.

**Backend**

- [ ] WP3-06-BE-01 [core] Job định kỳ dùng `JobLock`, đếm theo nguồn, chống lặp.
- [ ] WP3-06-BE-02 [core] Gửi qua `ChatWebhook`, ngưỡng cấu hình có mặc định.

**Infra, deploy, CI**

- [ ] WP3-06-INFRA-01 `deploy/.env.example`: biến ngưỡng nếu đưa ra env.

**Test**

- [ ] WP3-06-TEST-01 Case T3-08.

## Đóng phase

- [ ] `./mvnw compile` đạt cho toàn bộ module (backend) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn milestone.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch với từng giá trị `DXOS_APP`.
- [ ] `dx-core-api` và `dx-core` không import gói của app nào; app chỉ import `dx-core-api`.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu và bản ghi audit test không còn dùng.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase: xóa thư mục phase khi xong, việc còn lại sang `docs/BACKLOG.md`.
