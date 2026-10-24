# Checklist: Phase 4

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP4-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, AI, DATA, INFRA, TEST.
Mỗi dòng là một việc không quá nửa ngày và có thể kiểm chứng.
Dòng BE và DATA ghi module trong ngoặc vuông: `[core-api]`, `[core]`, `[farm]`, `[lab]`, `[monitor]`.
Lớp FE ở phase này là giao diện của `dx-monitor` (Thymeleaf, JavaScript) và portal.

## Trước khi code

- [x] Chốt Q1 đến Q5 ở `00_OVERVIEW.md` mục 9 (Q6 và Q7 theo đề xuất).
- [ ] Merge `main` vào `Khuong/main`, rồi cắt nhánh mới cho phase này.

## WP4-01: Ghi lỗi hệ thống và thao tác thất bại vào `audit_log`

Kết quả đầu ra: một lỗi `5xx` cố ý sinh một bản ghi `SERVER_ERROR` có `request_id`; `./mvnw compile` đạt.

**Spec và tài liệu**

- [ ] WP4-01-DOC-01 Cập nhật `@Operation` của `GET /api/v1/audit-logs` (giá trị `SERVER_ERROR`, `FAILED`) trước khi code.

**Backend**

- [ ] WP4-01-BE-01 [core-api] `AuditEvent.failed(action, targetType, targetId, metadata)`.
- [ ] WP4-01-BE-02 [core] `GlobalExceptionHandler` ghi `SERVER_ERROR` ở nhánh `Exception` chung (chỉ tên lớp ngoại lệ trong `metadata`), qua bộ giới hạn theo IP và phút.
- [ ] WP4-01-BE-03 [core] `WorkspaceController`: ghi `FAILED` khi tạo workspace, gắn chủ, thêm thành viên bị lỗi nghiệp vụ.
- [ ] WP4-01-BE-04 [farm] `ParcelController`, `CropSeasonController`, `DiseaseReportController`: ghi `FAILED` cho xóa mềm và thu hoạch sớm bị lỗi.
- [ ] WP4-01-BE-05 [lab] `CatalogController`, `TicketController`: ghi `FAILED` cho danh mục, SLA, xóa tệp đính kèm.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WP4-01-TEST-01 Chạy T4-01, T4-02.

## WP4-02: Module `dx-monitor` và hạ tầng chạy

Kết quả đầu ra: `./mvnw compile` đạt cho toàn bộ module; `monitor` healthy trên clone sạch với `farm` và `lab`; `/monitor/` đi qua gateway.

**Spec và tài liệu**

- [ ] WP4-02-DOC-01 Ghi các biến môi trường mới và sơ đồ luồng vào `deploy/.env.example` và `ARCHITECTURE.md` trước khi code.

**Backend**

- [ ] WP4-02-BE-01 [monitor] Thêm module `dx-monitor` vào `backend/pom.xml`; `pom.xml` của module (web, thymeleaf, security, oauth2-client, jdbc, flyway, postgresql, actuator, springdoc), không phụ thuộc `dx-core-api` và `dx-core`.
- [ ] WP4-02-BE-02 [monitor] Lớp khởi động, `application.yaml` (cổng `8090`, context path `/monitor`, hai datasource, `forward-headers-strategy`), record `ApiResponse` riêng.
- [ ] WP4-02-BE-03 [monitor] Cấu hình giờ nghiệp vụ `Asia/Ho_Chi_Minh` và chế độ lưu thời gian UTC như backend.

**Data, seed, migration**

- [ ] WP4-02-DATA-01 [monitor] `db/migration/monitor/V1__monitor_schema.sql`: `gateway_event`, `service_health_event`, chỉ mục theo thời gian, `ip`, `request_id`; Flyway `schemas=monitor`.

**Infra, deploy, CI**

- [ ] WP4-02-INFRA-01 `deploy/monitor-init.sql` và service `monitor-init` (image `pgvector/pgvector:pg16`, chạy sau `backend` và `keycloak` healthy): role `monitor_app`, schema `monitor`, `SELECT` trên `audit_log`, role `keycloak_ro`, view `vw_auth_events`; idempotent.
- [ ] WP4-02-INFRA-02 `backend/Dockerfile.monitor` build `-pl dx-monitor -am`; `docker-compose.yml` service `monitor` (log xoay vòng, không publish cổng, healthcheck `/monitor/actuator/health`), `apisix` không chờ `monitor`.
- [ ] WP4-02-INFRA-03 `apisix/common.yaml`: upstream `monitor:8090`, route `/monitor/*` với `limit-count` theo IP, route chặn tiền tố `/monitor/ingest/`.
- [ ] WP4-02-INFRA-04 `.env.example`: `MONITOR_DB_PASSWORD`, `KEYCLOAK_READONLY_DB_PASSWORD`, `MONITOR_OIDC_CLIENT_SECRET`, `MONITOR_INGEST_KEY`.
- [ ] WP4-02-INFRA-05 `.github/workflows/ci.yml` và `release.yml`: build module mới và image `monitor`.

**Test**

- [ ] WP4-02-TEST-01 Chạy T4-03, T4-04.

## WP4-03: Đăng nhập Keycloak và chỉ ADMIN vào được

Kết quả đầu ra: `ADMIN` vào `/monitor/`; `ENGINEER`, `FARMER` nhận 403; không phiên thì chuyển sang Keycloak.

**Backend**

- [ ] WP4-03-BE-01 [monitor] `SecurityConfig`: `oauth2Login` với `authorization-uri` theo `GATEWAY_PUBLIC_URL`, `token-uri` và `jwk-set-uri` nội bộ, không kiểm issuer.
- [ ] WP4-03-BE-02 [monitor] Lấy vai từ `realm_access.roles` của access token; thiếu `ADMIN` thì từ chối và hủy phiên; trang 403 tiếng Việt.
- [ ] WP4-03-BE-03 [monitor] Đăng xuất kết thúc phiên Keycloak; cookie `HttpOnly`, `SameSite=Lax`, `Secure` qua HTTPS.
- [ ] WP4-03-BE-04 [monitor] `/monitor/ingest/**` và `/monitor/actuator/health` được loại khỏi đăng nhập phiên (ingest dùng khóa riêng, health công khai trong mạng Docker).

**Infra, deploy, CI**

- [ ] WP4-03-INFRA-01 `sso/realm-dxos.json`: client confidential `dxos-monitor`, redirect URI, secret từ `MONITOR_OIDC_CLIENT_SECRET`; `sso/README.md` nêu client và cách áp realm lên Keycloak đang chạy.

**Test**

- [ ] WP4-03-TEST-01 Chạy T4-05, T4-06.

## WP4-04: Sự kiện bất thường của gateway vào database

Kết quả đầu ra: `429`, `401`, `404`, `5xx`, `SLOW` của gateway nằm ở `monitor.gateway_event` và còn sau khi tạo lại container `apisix`.

**Spec và tài liệu**

- [ ] WP4-04-DOC-01 Mô tả `POST /monitor/ingest/gateway` (payload, khóa, mã trả về) trong `deploy/OPERATIONS.md` trước khi code.

**Backend**

- [ ] WP4-04-BE-01 [monitor] Entity `GatewayEvent` (Lombok), repository, enum `GatewayEventKind`.
- [ ] WP4-04-BE-02 [monitor] `GatewayIngestController`: nhận mảng, xác thực `MONITOR_INGEST_KEY` (thiếu khóa cấu hình thì từ chối mọi request), giới hạn kích thước mảng.
- [ ] WP4-04-BE-03 [monitor] `GatewayEventFilter`: lọc theo `status` và `request_time`, bỏ `127.0.0.1` và địa chỉ Docker, miễn ngưỡng chậm cho `monitor.gateway.slow-exempt-paths`.
- [ ] WP4-04-BE-04 [monitor] Giới hạn 100 bản ghi mỗi IP mỗi phút, phần dư cộng vào cột `suppressed`.

**Infra, deploy, CI**

- [ ] WP4-04-INFRA-01 Spike (tối đa nửa ngày): `http-logger` ở `global_rules` gửi batch tới `monitor`, có `auth_header`, đọc khóa từ môi trường, `log_format` đủ trường; ghi kết quả vào `04_TRADEOFFS_RISKS.md` R1.
- [ ] WP4-04-INFRA-02 `apisix/common.yaml` (hoặc `config.yaml`): bật `http-logger` với retry và batch.
- [ ] WP4-04-INFRA-03 `docker-compose.yml`: truyền `MONITOR_INGEST_KEY` cho `monitor` và `apisix-config`.

**Test**

- [ ] WP4-04-TEST-01 Chạy T4-07 đến T4-11.

## WP4-05: Dò trạng thái dịch vụ

Kết quả đầu ra: tắt rồi bật MinIO sinh hai dòng `DOWN`, `UP` đúng thời điểm; dừng backend thì `monitor` vẫn ghi nhận.

**Backend**

- [ ] WP4-05-BE-01 [monitor] `monitor.probe.targets` trong `application.yaml` và lớp đọc cấu hình (tên, URL hoặc `jdbc`, thời gian chờ, `required`).
- [ ] WP4-05-BE-02 [monitor] `ServiceProbe` (HTTP và `select 1`), `ServiceHealthSampler` mỗi 30 giây.
- [ ] WP4-05-BE-03 [monitor] Entity `ServiceHealthEvent`; chỉ ghi khi đổi trạng thái, `DOWN` sau 2 lần thất bại liên tiếp, nạp trạng thái cuối khi khởi động.

**Test**

- [ ] WP4-05-TEST-01 Chạy T4-12, T4-13.

## WP4-06: API đọc và truy vết

Kết quả đầu ra: sáu endpoint `GET` trả đúng dữ liệu kịch bản cho ADMIN, vai khác nhận 403.

**Spec và tài liệu**

- [ ] WP4-06-DOC-01 `@Operation` tiếng Anh cho sáu endpoint trước khi code.

**Backend**

- [ ] WP4-06-BE-01 [monitor] `MonitorQueryService` với `JdbcTemplate`: tổng hợp, theo giờ, IP đáng ngờ, đường dẫn `5xx`; cửa sổ chỉ nhận `1h`, `24h`, `7d`.
- [ ] WP4-06-BE-02 [monitor] `AuthEventReader` đọc `vw_auth_events` bằng datasource `keycloak_ro`; lỗi kết nối chỉ làm ô đó báo thiếu dữ liệu.
- [ ] WP4-06-BE-03 [monitor] `SummaryController`: `/api/summary`, `/api/timeline`, `/api/suspects`, `/api/health`, `/api/admin-actions`.
- [ ] WP4-06-BE-04 [monitor] `TraceController`: `/api/trace` theo `requestId` hoặc `ip`, kiểm định dạng, tối đa 200 dòng, sắp theo thời gian.
- [ ] WP4-06-BE-05 [monitor] Không trả `actor_user_id`, `metadata` thô; `user_agent` rút 120 ký tự.

**Test**

- [ ] WP4-06-TEST-01 Chạy T4-14 đến T4-17.

## WP4-07: Giao diện giám sát và link ở portal

Kết quả đầu ra: ADMIN thấy trang có dữ liệu sau kịch bản tấn công; dữ liệu chứa `<script>` không chạy.

**Frontend (web)**

- [ ] WP4-07-FE-01 [monitor] `templates/monitor.html` + `static/monitor.css`: khung trang, chọn cửa sổ thời gian, tiếng Việt, dùng được trên màn hình hẹp.
- [ ] WP4-07-FE-02 [monitor] `static/monitor.js`: gọi các API, làm mới 30 giây, đưa dữ liệu vào trang bằng `textContent`.
- [ ] WP4-07-FE-03 [monitor] Thẻ trạng thái dịch vụ, bốn con số (màu đổi khi vượt ngưỡng), biểu đồ theo giờ.
- [ ] WP4-07-FE-04 [monitor] Bảng IP đáng ngờ (bấm để truy vết), bảng đường dẫn `5xx`, bảng thao tác quản trị, ô truy vết.
- [ ] WP4-07-FE-05 [monitor] Nhúng thư viện biểu đồ MIT vào `static/`; thêm vào `LICENSES.md`.
- [ ] WP4-07-FE-06 [monitor] CSP `default-src 'self'` cho trang giám sát.
- [ ] WP4-07-FE-07 [portal] Thẻ "Giám sát" trỏ `/monitor/` và dòng trạng thái kiểm `/monitor/actuator/health`; cập nhật `portal/README.md`.

**Test**

- [ ] WP4-07-TEST-01 Chạy T4-18 đến T4-20.

## WP4-08: Cảnh báo, dọn dữ liệu và tài liệu vận hành

Kết quả đầu ra: `5xx`, `429` dồn dập và dịch vụ `DOWN` mỗi loại gửi đúng một tin vào kênh "Ban quản trị"; bản ghi cũ hơn 30 ngày bị xóa.

**Backend**

- [ ] WP4-08-BE-01 [monitor] `MattermostSender`: đọc webhook từ thư mục webhook (mount vào `monitor`) hoặc `MATTERMOST_WEBHOOK_URL_ADMIN`; không có thì không gửi.
- [ ] WP4-08-BE-02 [monitor] `AlertJob`: `5xx` dồn dập (5 lần trong 5 phút), `429` theo IP (10 lần trong 10 phút), dịch vụ `DOWN` và "đã phục hồi"; tối đa một tin mỗi điều kiện mỗi 60 phút.
- [ ] WP4-08-BE-03 [monitor] `RetentionJob` hằng ngày xóa `gateway_event` và `service_health_event` quá `monitor.retention-days` (30).

**Infra, deploy, CI**

- [ ] WP4-08-INFRA-01 `docker-compose.yml`: mount thư mục webhook và truyền biến Mattermost cho `monitor`.

**Spec và tài liệu**

- [ ] WP4-08-DOC-01 `deploy/OPERATIONS.md`: mục "Màn hình giám sát" (khối nào trả lời câu hỏi nào, quy trình truy vết từ `request_id`, khoảng trống khi `monitor` ngừng), cập nhật mục Audit và Cảnh báo; `docs/CLOUDFLARE.md` (ứng dụng Access cho `/monitor/*`, Q7-A).
- [ ] WP4-08-DOC-02 `README.md`, `README_EN.md`, `ARCHITECTURE.md`, `backend/README.md`, `sso/README.md`, `LICENSES.md`, `CHANGELOG.md` (chỉ trạng thái cuối).
- [ ] WP4-08-DOC-03 `docs/TESTING.md` thêm mục kiểm giám sát, `docs/COUNCIL_QA.md`, `docs/SUBMISSION.md`, `docs/BACKLOG.md`.

**Test**

- [ ] WP4-08-TEST-01 Chạy T4-21 đến T4-23.

## Đóng phase

- [ ] `./mvnw compile` đạt cho toàn bộ module (backend, gồm `dx-monitor`) và đã đọc lại diff; không chạy test hay app trừ khi người dùng yêu cầu.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn milestone.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch với `DXOS_APP=farm` và `DXOS_APP=lab`.
- [ ] `dx-core-api` và `dx-core` không import gói của app nào; app chỉ import `dx-core-api`; `dx-monitor` không import `dx-core-api` và `dx-core`.
- [ ] Không có secret, mật khẩu hay dữ liệu thật (IP thật, email) trong diff và trong ảnh chụp.
- [ ] Dọn dữ liệu và ảnh test không còn dùng.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Gộp việc còn lại vào `docs/BACKLOG.md` rồi xóa thư mục phase này.
