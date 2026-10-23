# Plan thực hiện: Phase 3

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP3-01 → WP3-02 → WP3-03 → WP3-04 → WP3-05 → WP3-06.
WP3-05 (API đọc) và WP3-06 (cảnh báo) chỉ cần WP3-01 và WP3-03, làm song song được sau đó.

## WP3-01: Bảng `audit_log` và `AuditRecorder`

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: một nơi ghi sự kiện audit, chỉ thêm, dùng chung cho core và app.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Ghi một sự kiện audit | Không có nơi ghi | `AuditRecorder.record(event)` thêm một dòng vào `audit_log` |
| Sửa hoặc xóa dòng audit | Không áp dụng | Trigger của database từ chối |
| Ghi audit lỗi | Không áp dụng | Lỗi được log, không làm hỏng request của người dùng |

**Hướng làm**

- `dx-core-api`: interface `AuditRecorder`, record `AuditEvent` (action, target type, target id, outcome, metadata; factory `AuditEvent.success`), enum `AuditOutcome` (`SUCCESS`, `DENIED`, `FAILED`).
- `dx-core`: `AuditService` cài `AuditRecorder`, entity `AuditLogEntry` (Lombok như các entity khác), repository.
- Migration core `V4__audit_log.sql`: bảng `audit_log` (`id`, `occurred_at`, `actor_user_id`, `actor_name`, `actor_roles`, `client_id`, `action`, `target_type`, `target_id`, `outcome`, `ip`, `request_id`, `metadata jsonb`), index theo `occurred_at`, `actor_user_id`, `action`; trigger chặn `UPDATE` và `DELETE` theo mẫu của `workflow_event`.
- Ghi bằng giao dịch riêng (`REQUIRES_NEW`) để sự kiện `DENIED` vẫn lưu khi giao dịch chính bị hoàn tác; lỗi ghi chỉ được log.
- Core/App: interface ở `dx-core-api` vì app (Farm, Lab) cần ghi mà chỉ được phụ thuộc `dx-core-api`.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`.
- File chính: `AuditRecorder`, `AuditEvent`, `AuditOutcome`, `AuditService`, `AuditLogEntry`, `AuditLogRepository`, `V4__audit_log.sql`.
- Endpoint, bảng DB, biến môi trường: bảng `audit_log`.
- Role và client bị ảnh hưởng: không có.
- Tương thích ngược và dữ liệu cũ: bảng mới, không đụng dữ liệu cũ; app còn lại không phải sửa.

**Kết quả đầu ra**

- `./mvnw compile` đạt; migration tạo bảng và trigger trên database sạch.

**Phụ thuộc, lưu ý**: Q1 ở `00` mục 9.

## WP3-02: Ngữ cảnh request (`request_id`, IP, client)

- Mục tham chiếu: `docs/phase-1-gateway-logging` WP1-02.
- Mục tiêu: mọi bản ghi audit và dòng log của backend có `request_id` và IP thật.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Log của backend | Không có `request_id` | Mẫu log có `request_id` từ MDC |
| Bản ghi audit | Không có | `ip` và `request_id` lấy từ ngữ cảnh request |

**Hướng làm**

- `dx-core`: `RequestTraceFilter` chạy sớm đọc `X-Request-Id` và `X-Real-IP` (APISIX đặt `X-Real-IP` bằng `$remote_addr` sau khi lấy IP thật), đặt cùng method và đường dẫn vào MDC; không có header thì dùng địa chỉ kết nối và một mã mới.
- `application.yml`: thêm `%X{requestId}` vào mẫu log.
- Core/App: toàn bộ ở `dx-core`.

**Phạm vi ảnh hưởng**

- Module: `dx-core`.
- File chính: filter ngữ cảnh request, `application.yml`.
- Endpoint, bảng DB, biến môi trường: không.

**Kết quả đầu ra**

- Một request qua gateway có cùng `request_id` ở log gateway, log backend và bản ghi audit.

**Phụ thuộc, lưu ý**: phase 1; chỉ tin header IP khi backend chỉ nhận request qua gateway.

## WP3-03: Ghi 401 và 403

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: biết ai bị từ chối ở đâu, từ IP nào.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Có token nhưng không đủ quyền (403) | Chỉ trả lỗi | Ghi `ACCESS_DENIED` kèm người dùng, vai, đường dẫn, method |
| Token sai hoặc hết hạn (401) | Chỉ trả lỗi | Ghi `AUTH_FAILED` kèm IP và đường dẫn |
| Không có token (401) | Chỉ trả lỗi | Không ghi (nhiễu) |
| Một nguồn gây nhiều 403 | Không áp dụng | Ghi tối đa N bản ghi mỗi phút mỗi nguồn, đếm phần còn lại trong `metadata` |

**Hướng làm**

- `RestAccessDeniedHandler` (cũng phục vụ `ClientRestrictionFilter`), `GlobalExceptionHandler` (403 do `@PreAuthorize`) và `RestAuthenticationEntryPoint` gọi `AuditService.recordDenial` trước khi trả lỗi.
- `AuditDenialLimiter` giới hạn trong bộ nhớ theo khóa (IP và hành động), mặc định 20 bản ghi mỗi phút.
- Core/App: toàn bộ ở `dx-core`.

**Phạm vi ảnh hưởng**

- Module: `dx-core`.
- File chính: ba lớp bảo mật nêu trên, bộ giới hạn.
- Role và client bị ảnh hưởng: mọi vai và client; response không đổi.

**Kết quả đầu ra**

- 403 của `farmer1` vào API quản trị tạo đúng một bản ghi; 100 lần liên tiếp không tạo quá ngưỡng đã cấu hình.

**Phụ thuộc, lưu ý**: WP3-01, WP3-02; giới hạn số bản ghi mỗi nguồn để kẻ tấn công không làm đầy bảng.

## WP3-04: Ghi thao tác quản trị

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: các thao tác thay đổi quyền hoặc xóa dữ liệu đều có dấu vết.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| ADMIN tạo workspace, liên kết, thêm thành viên | Không có dấu vết riêng | Ghi `WORKSPACE_CREATED`, `WORKSPACE_LINKED`, `MEMBER_ADDED` |
| Đổi vai ở Keycloak qua `KeycloakAdminClient` | Không có dấu vết riêng | Ghi `ROLE_ASSIGNED` |
| Xóa mềm thửa, vụ (Farm) | `deleted_at` và người sửa | Thêm `PARCEL_DELETED`, `SEASON_DELETED` |
| Thu hoạch sớm kèm lý do (Farm) | Có trong dữ liệu vụ | Thêm `HARVEST_OVERRIDE` |
| Quyết định duyệt trong workflow | Đã có ở `workflow_event` | Không ghi lại để tránh trùng |

**Hướng làm**

- Danh sách đã chốt (Q4), ghi ở controller sau khi service trả về để bản ghi chỉ có khi thao tác đã hoàn tất: core `WORKSPACE_CREATED`, `WORKSPACE_LINKED`, `MEMBER_ADDED`; Farm `PARCEL_DELETED`, `SEASON_DELETED`, `REPORT_DELETED`, `HARVEST_OVERRIDE` (thu hoạch sớm hoặc thu hoạch thay); Lab `TICKET_CATEGORY_CREATED`, `TICKET_CATEGORY_CHANGED`, `SLA_POLICY_CHANGED`, `TICKET_ATTACHMENT_REMOVED`.
- Việc gán vai ở Keycloak nằm trong `WORKSPACE_CREATED` (cờ `withAccount`), không có hành động riêng.
- Core/App: tên hành động của app do app định nghĩa, không đưa vào enum của core.

**Phạm vi ảnh hưởng**

- Module: `dx-core`, `app-farm`, `app-lab`.
- File chính: `WorkspaceService`, `KeycloakAdminClient`, service tương ứng của Farm và Lab.
- Tương thích ngược và dữ liệu cũ: không đổi response.

**Kết quả đầu ra**

- Mỗi thao tác trong danh sách tạo đúng một bản ghi `SUCCESS` (hoặc `FAILED` khi lỗi).

**Phụ thuộc, lưu ý**: WP3-01; Q4.

## WP3-05: API đọc audit cho ADMIN

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: ADMIN tra cứu audit không cần vào database.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| ADMIN xem audit | Không có | `GET /api/v1/audit-logs` có phân trang và lọc theo người dùng, hành động, khoảng thời gian, kết quả |
| Vai khác gọi | Không áp dụng | 403 |

**Hướng làm**

- Spec và Swagger trước: `@Tag`, `@Operation` bằng tiếng Anh; response bọc `ApiResponse<T>`.
- `AuditLogController` ở `dx-core` với `@PreAuthorize("hasRole('ADMIN')")`; DTO là record; không trả entity.
- Truy vấn bằng repository có phân trang, sắp xếp mới nhất trước.
- Core/App: toàn bộ ở `dx-core`.

**Phạm vi ảnh hưởng**

- Module: `dx-core`.
- Endpoint: `GET /api/v1/audit-logs`.
- Role và client bị ảnh hưởng: ADMIN.

**Kết quả đầu ra**

- ADMIN lọc được theo `actor`, `action`, `from`, `to`, `outcome`; vai khác nhận 403.

**Phụ thuộc, lưu ý**: WP3-01, WP3-03.

## WP3-06: Cảnh báo Mattermost

- Mục tham chiếu: góp ý bảo vệ server.
- Mục tiêu: quản trị viên biết ngay khi có dấu hiệu dò quyền.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Một IP hoặc người dùng bị từ chối nhiều lần trong 10 phút | Không ai biết | Tin cảnh báo lên kênh Mattermost, tối đa một tin mỗi nguồn trong khoảng chống lặp |

**Hướng làm**

- `AuditAlertJob` trong `dx-core` dùng `JobLock` để chỉ một instance chạy; đếm bản ghi `DENIED` theo IP trong cửa sổ 10 phút, ngưỡng 10, nhắc lại sau 60 phút.
- Gửi qua `ChatWebhook` vào kênh `admin` ("Ban quản trị", có ở cả hai ứng dụng); ngưỡng và chu kỳ là thuộc tính `audit.*` có mặc định trong `application.yaml`, không đưa ra biến môi trường.
- Core/App: toàn bộ ở `dx-core`.

**Phạm vi ảnh hưởng**

- Module: `dx-core`.
- File chính: job cảnh báo, cấu hình.
- Endpoint, bảng DB, biến môi trường: có thể thêm biến ngưỡng.

**Kết quả đầu ra**

- Vượt ngưỡng tạo đúng một tin; tắt Mattermost không làm hỏng nghiệp vụ.

**Phụ thuộc, lưu ý**: WP3-03; Q2.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `GET /api/v1/audit-logs` | Mới, chỉ ADMIN | Không |
| Interface | `AuditRecorder` ở `dx-core-api` | Thêm | Không (app hiện có không phải sửa) |
| Bảng DB | `audit_log` | Mới, append-only | Không |
| Thuộc tính | `audit.denied.max-per-minute`, `audit.alert.*` | Mới, có mặc định trong `application.yaml` | Không |

## Dữ liệu, migration và rollback

- Migration: `db/migration/core/V4__audit_log.sql`, chạy khi backend khởi động.
- Dữ liệu cũ: không đụng.
- Rollback: gỡ mã ghi audit; bảng `audit_log` có thể để lại (trigger chặn xóa dòng, cần `DROP TABLE` thủ công nếu muốn bỏ).

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Ghi từ backend, không dùng `pgaudit` | Image `pgvector/pgvector:pg16` không có extension; backend biết người dùng và `request_id` |
| D2 | Ghi bằng giao dịch riêng | Sự kiện `DENIED` phải lưu dù giao dịch chính hoàn tác |
| D3 | Không ghi quyết định workflow vào `audit_log` | Đã có ở `workflow_event` |
| D4 | Không ghi 401 không có token | Phần lớn là nhiễu quét |
