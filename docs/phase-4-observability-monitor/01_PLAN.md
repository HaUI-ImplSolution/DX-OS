# Plan thực hiện: Phase 4

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Kiến trúc đích

```
Người dùng ──► Cloudflare ──► APISIX ──┬─► backend  (API, ghi audit_log)
                                       ├─► ...
                                       └─► monitor  (/monitor/*, ADMIN, SSO Keycloak)
APISIX ──http-logger (bất thường)──► monitor ──► schema monitor (gateway_event, service_health_event)
monitor ──đọc──► public.audit_log (role monitor_app)      monitor ──đọc──► keycloak.vw_auth_events (role keycloak_ro)
monitor ──dò──► backend, keycloak, minio, ai, postgres, superset, mattermost, frontend, portal
monitor ──cảnh báo──► Mattermost "Ban quản trị"
```

`monitor` là module `backend/dx-monitor`, Spring Boot 4 / Java 21 như `dx-host`, chạy thành container riêng, cổng nội bộ `8090`, context path `/monitor`.
Nó không import `dx-core-api` hay `dx-core`, nên backend sập hay đổi cấu trúc không kéo nó theo.
Giao diện là trang Thymeleaf kèm thư viện biểu đồ nhúng sẵn (không tải từ CDN), không có bước build Node.

## Thứ tự và phụ thuộc

WP4-01 (backend) độc lập với phần còn lại.
WP4-02 → WP4-03 là nền của mọi WP monitor.
WP4-04, WP4-05, WP4-06 làm song song được sau WP4-03 (WP4-06 cần bảng của WP4-04 và WP4-05 để chạy thật, nhưng viết được song song).
WP4-07 cần WP4-06.
WP4-08 cần WP4-04, WP4-05.

```
WP4-01
WP4-02 → WP4-03 ┬→ WP4-04 ┬→ WP4-08
                ├→ WP4-05 ┘
                └→ WP4-06 → WP4-07
```

## WP4-01: Ghi lỗi hệ thống và thao tác thất bại vào `audit_log`

- Mục tham chiếu: `docs/BACKLOG.md` mục 5 ("Ghi `AuditOutcome.FAILED`").
- Mục tiêu: `audit_log` trả lời được "lỗi gì đã xảy ra", không chỉ "ai bị từ chối".
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Backend trả `5xx` (lỗi chưa lường trước) | Chỉ có log backend | Thêm bản ghi `SERVER_ERROR`, `outcome = FAILED`, kèm `request_id`, đường dẫn, tên lớp ngoại lệ trong `metadata` (không ghi thông điệp lỗi có thể chứa dữ liệu) |
| Thao tác quản trị lỗi nghiệp vụ (trùng khóa, vi phạm ràng buộc) | Trả mã lỗi, không có bản ghi | Thêm bản ghi của hành động đó với `outcome = FAILED` |
| Một IP gây lỗi dồn dập | Không áp dụng | Dùng lại bộ giới hạn theo IP và phút của `AuditDenialLimiter`; phần bị bỏ ghi vào `metadata.suppressed` |

**Hướng làm**

- `dx-core-api`: thêm factory `AuditEvent.failed(action, targetType, targetId, metadata)`; `AuditOutcome.FAILED` đã có.
- `dx-core`: `GlobalExceptionHandler` gọi `AuditRecorder` ở nhánh `Exception` chung (5xx), không đụng nhánh lỗi nghiệp vụ `4xx`.
- Thao tác quản trị đã ghi `SUCCESS` ghi thêm `FAILED` khi ném `BusinessException`; chỉ bọc ở controller đã gọi `AuditRecorder`, không thêm try/catch mới ở service.
- Core/App: phần chung ở `dx-core`; app chỉ gọi `AuditEvent.failed` ở những chỗ đã gọi `AuditEvent.success`.

**Phạm vi ảnh hưởng**

- Module: `dx-core-api`, `dx-core`, `app-farm`, `app-lab`.
- File chính: `AuditEvent`, `GlobalExceptionHandler`, `WorkspaceController`, `ParcelController`, `CropSeasonController`, `DiseaseReportController`, `CatalogController`, `TicketController`.
- Endpoint, bảng DB, biến môi trường: không đổi bảng; `GET /api/v1/audit-logs` lọc được `action=SERVER_ERROR`.
- Role và client bị ảnh hưởng: không có.
- Tương thích ngược và dữ liệu cũ: không cần migration; cột `outcome` đã nhận `FAILED`.

**Kết quả đầu ra**

- `./mvnw compile` đạt; một lỗi `5xx` cố ý (tắt MinIO rồi tải ảnh) sinh đúng một bản ghi `SERVER_ERROR` có `request_id` khớp header `X-Request-Id`.

**Phụ thuộc, lưu ý**: ghi audit lỗi không được ném lỗi mới trong lúc xử lý lỗi (giữ cam kết "ghi lỗi chỉ được log").

## WP4-02: Module `dx-monitor` và hạ tầng chạy

- Mục tham chiếu: Q1, Q6.
- Mục tiêu: dịch vụ `monitor` khởi động, có database riêng trong Postgres hiện có, đi qua gateway, build trong CI.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| `docker compose up -d` | Không có dịch vụ giám sát | Thêm `monitor-init` (một lần) rồi `monitor` |
| Truy cập `/monitor/` qua gateway | 404 | Tới dịch vụ `monitor` (đăng nhập ở WP4-03), có giới hạn tốc độ theo IP |
| Dữ liệu của monitor | Không áp dụng | Schema `monitor` trong database `dxos`, role `monitor_app` |

**Hướng làm**

- Module `backend/dx-monitor`: thêm vào `backend/pom.xml`; package `HaUI.ImplSolution.dxos.monitor`; phụ thuộc web, thymeleaf, security, oauth2-client, jdbc, flyway, postgresql, actuator, springdoc.
  Không phụ thuộc `dx-core-api` hay `dx-core`.
  `ApiResponse` của monitor là record riêng nhỏ, cùng dạng để UI và tài liệu quen thuộc.
- Dockerfile cho monitor (`backend/Dockerfile.monitor`) build `-pl dx-monitor -am`, cùng cách `backend/Dockerfile`; `COPY` thêm `pom.xml` của module mới.
- `monitor-init` (image `pgvector/pgvector:pg16` đã có sẵn, dùng `psql`): chạy sau `backend` healthy (để `audit_log` đã tồn tại) và `keycloak` healthy (để bảng của Keycloak đã tồn tại); idempotent:
  - tạo role `monitor_app` (mật khẩu `MONITOR_DB_PASSWORD`), schema `monitor` thuộc role đó, `GRANT SELECT ON public.audit_log`;
  - tạo role `keycloak_ro` (mật khẩu `KEYCLOAK_READONLY_DB_PASSWORD`) và view `vw_auth_events` trong database `keycloak` (`event_time` đổi sang timestamp, `type`, `ip_address`, `client_id`, `error`, tên đăng nhập từ `details_json`; không `user_id`), chỉ `GRANT SELECT` trên view đó.
- Flyway riêng của monitor: `db/migration/monitor`, `spring.flyway.schemas=monitor`; bảng lịch sử Flyway nằm trong schema `monitor`.
- Gateway: route `/monitor/*` tới upstream `monitor:8090` trong `apisix/common.yaml`, `limit-count` theo IP; route không phụ thuộc `DXOS_APP`.
- `docker-compose.yml`: service `monitor` (log xoay vòng như các service khác, không publish cổng), `depends_on` `monitor-init`; `apisix` không chờ `monitor` (monitor lỗi chỉ làm `/monitor/` trả 502, không chặn gateway khởi động); healthcheck bằng `/monitor/actuator/health`.
- CI: thêm bước build `dx-monitor` vào `ci.yml` (job backend đã chạy `./mvnw`), bước build image `monitor` vào `release.yml` theo cách các image khác.
- Core/App: không đụng `dx-core`; monitor không biết `DXOS_APP`.

**Phạm vi ảnh hưởng**

- Module: `dx-monitor` (mới), Infra, CI.
- File chính: `backend/pom.xml`, `backend/dx-monitor/**`, `backend/Dockerfile.monitor`, `deploy/docker-compose.yml`, `deploy/monitor-init.sql` hoặc script tương đương, `deploy/apisix/common.yaml`, `.github/workflows/ci.yml`, `release.yml`.
- Endpoint, bảng DB, biến môi trường: `/monitor/*`, schema `monitor`, `MONITOR_DB_PASSWORD`, `KEYCLOAK_READONLY_DB_PASSWORD`.
- Role và client bị ảnh hưởng: không có.
- Tương thích ngược và dữ liệu cũ: bổ sung thuần; môi trường có sẵn phải thêm biến vào `deploy/.env`; thiếu biến thì Compose dừng và nêu tên biến.

**Kết quả đầu ra**

- `./mvnw compile` đạt cho toàn bộ module; `docker compose up -d` trên clone sạch với `farm` và `lab` đưa `monitor` lên healthy; `psql` bằng `monitor_app` đọc được `audit_log` và bị từ chối với `workspaces`.

**Phụ thuộc, lưu ý**: Q6; rủi ro R2 ở `04`.

## WP4-03: Đăng nhập Keycloak và chỉ ADMIN vào được

- Mục tham chiếu: `sso/README.md`.
- Mục tiêu: `/monitor/` đòi đăng nhập SSO và chỉ cho vai `ADMIN`.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Chưa đăng nhập mở `/monitor/` | Không áp dụng | Chuyển sang Keycloak; đã đăng nhập app khác thì vào thẳng |
| `ENGINEER`, `FARMER` đăng nhập | Không áp dụng | Trang 403 tiếng Việt, phiên bị hủy |
| Gateway gọi nạp sự kiện | Không áp dụng | Đường nạp (WP4-04) dùng khóa riêng, không dùng phiên |

**Hướng làm**

- Client confidential `dxos-monitor` trong `sso/realm-dxos.json` (cùng mẫu client `dxos-superset`), redirect URI `.../monitor/login/oauth2/code/keycloak`; secret qua `MONITOR_OIDC_CLIENT_SECRET`.
- Spring Security `oauth2Login`: `authorization-uri` theo địa chỉ trình duyệt (`GATEWAY_PUBLIC_URL`), `token-uri` và `jwk-set-uri` theo địa chỉ nội bộ `keycloak:8080`, kiểm chữ ký nhưng không kiểm issuer (Keycloak không cố định hostname, giống backend, xem `sso/README.md`).
- Vai lấy từ `realm_access.roles` của access token; thiếu `ADMIN` thì từ chối.
- Đăng xuất kết thúc phiên Keycloak (giống Superset) để các app khác cùng đăng xuất.
- Phiên là cookie `HttpOnly`, `SameSite=Lax`, `Secure` khi qua HTTPS; `server.forward-headers-strategy=framework` để dựng đúng redirect sau Cloudflare.
- Core/App: không đụng core; vai `ADMIN` đã có trong realm.

**Phạm vi ảnh hưởng**

- Module: `dx-monitor`, SSO.
- File chính: `SecurityConfig` của monitor, `application.yaml` của monitor, `sso/realm-dxos.json`, `sso/README.md`.
- Endpoint, bảng DB, biến môi trường: `MONITOR_OIDC_CLIENT_SECRET`.
- Role và client bị ảnh hưởng: client mới `dxos-monitor`; mọi vai khác bị từ chối.
- Tương thích ngược và dữ liệu cũ: Keycloak đang chạy phải được áp realm thủ công (như phase 1), hoặc dựng lại từ clone sạch.

**Kết quả đầu ra**

- `ADMIN` vào được; `ENGINEER`, `FARMER` nhận 403; không có phiên thì bị chuyển sang Keycloak.

**Phụ thuộc, lưu ý**: R3 ở `04`.

## WP4-04: Sự kiện bất thường của gateway vào database

- Mục tham chiếu: `deploy/OPERATIONS.md` mục "Log của gateway" (mất khi container bị tạo lại).
- Mục tiêu: `429`, `401`, `404` dồn dập, `5xx`, request chậm từ gateway còn lại sau khi cập nhật và truy vấn được.
- Ước lượng: L (có một bước spike).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Gateway trả `429`, `401`, `404`, `5xx`, hoặc request chậm | Một dòng JSON ở stdout, mất khi tạo lại container | Thêm một dòng ở `monitor.gateway_event` với `kind`, IP, đường dẫn, mã, thời gian, `request_id` |
| Request thường (`2xx`, `3xx`, health check từ `127.0.0.1`) | Một dòng JSON ở stdout | Không lưu vào database; vẫn ở log Docker |
| `monitor` ngừng | Không áp dụng | Gateway giữ hàng đợi ngắn trong bộ nhớ và gửi lại; hết lượt thử thì bỏ, không chặn request của người dùng |
| `gateway_event` quá 30 ngày | Không áp dụng | Job hằng ngày xóa (WP4-08) |

**Hướng làm**

- Spike (WP4-04-INFRA-01, tối đa nửa ngày): trên APISIX 3.11 standalone, xác nhận `http-logger` ở `global_rules` (a) gửi được batch tới `http://monitor:8090/monitor/ingest/gateway` trên mạng Docker, (b) mang được khóa qua `auth_header`, (c) đọc được khóa từ môi trường khi `apisix.yaml` được ghép bởi `apisix-config`, (d) `log_format` mang đủ `request_id`, `remote_addr`, `status`, `request_time`, `uri` (không query string), `host`.
  Nếu (c) không được, thay bằng bước `sed` trong `apisix-config`.
  Nếu `http-logger` không đạt, chuyển sang phương án B của Q3 và báo lại trước khi làm tiếp.
- Monitor: `POST /monitor/ingest/gateway` nhận mảng sự kiện, xác thực bằng khóa riêng `MONITOR_INGEST_KEY` trong header (thiếu khóa cấu hình thì từ chối mọi request, fail closed); đường này chỉ có trên mạng Docker vì gateway chỉ routing `/monitor/` tới trang giao diện, nên route công khai chặn tiền tố `/monitor/ingest/` (trả 404).
- Lọc ở monitor (đổi ngưỡng không cần sửa gateway): lưu khi `status` là `401`, `404`, `429`, `>= 500`, hoặc `request_time >= monitor.gateway.slow-seconds` (mặc định 10, trừ các đường dẫn trong `monitor.gateway.slow-exempt-paths`, mặc định là route chẩn đoán AI); bỏ `127.0.0.1` và địa chỉ Docker.
- `kind`: `RATE_LIMITED`, `AUTH_REJECTED`, `NOT_FOUND`, `SERVER_ERROR`, `UPSTREAM_DOWN` (`502`, `503`, `504`), `SLOW`.
- Giới hạn khối lượng: tối đa `monitor.gateway.max-per-ip-minute` (mặc định 100) bản ghi mỗi IP mỗi phút, phần dư cộng vào cột `suppressed` của bản ghi kế tiếp, để một cuộc quét lớn không làm đầy bảng.
- Core/App: toàn bộ ở `dx-monitor`; danh sách đường dẫn miễn ngưỡng chậm là cấu hình, không phải interface.

**Phạm vi ảnh hưởng**

- Module: `dx-monitor`, Infra.
- File chính: `GatewayEvent`, `GatewayEventRepository`, `GatewayIngestController`, `GatewayEventFilter`, `V1__monitor_schema.sql`, `apisix/common.yaml` (hoặc `config.yaml`), `docker-compose.yml`.
- Endpoint, bảng DB, biến môi trường: `POST /monitor/ingest/gateway` (chỉ trong mạng Docker), bảng `monitor.gateway_event`, `MONITOR_INGEST_KEY`, `monitor.gateway.*`.
- Role và client bị ảnh hưởng: chỉ APISIX; người dùng web và mobile không đổi.
- Tương thích ngược và dữ liệu cũ: bảng mới.

**Kết quả đầu ra**

- Xả 700 request từ một IP: các request `429` xuất hiện ở `gateway_event` với IP thật; tạo lại container `apisix` thì dữ liệu còn.
- Dừng `monitor` 30 giây: người dùng không thấy khác thường.

**Phụ thuộc, lưu ý**: Q3, Q4; rủi ro R1, R4 ở `04`.

## WP4-05: Dò trạng thái dịch vụ

- Mục tham chiếu: `SystemStatusService` (chỉ có trạng thái tức thời, nằm trong backend).
- Mục tiêu: biết dịch vụ nào ngừng, từ lúc nào, bao lâu, kể cả khi chính backend ngừng.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Một dịch vụ đổi `UP`/`DOWN` | Chỉ thấy khi gọi `GET /api/v1/system/status` đúng lúc, và backend sập thì không có gì | `monitor` dò mỗi 30 giây, ghi một dòng `monitor.service_health_event` mỗi lần đổi trạng thái |
| Dịch vụ giữ nguyên trạng thái | Không áp dụng | Không ghi (bảng chỉ có điểm chuyển) |

**Hướng làm**

- Danh sách đích dò ở `monitor.probe.targets` (tên, URL hoặc `jdbc`, thời gian chờ): `backend`, `keycloak`, `storage` (MinIO), `ai`, `database` (một truy vấn `select 1`), `bi` (Superset), `chat` (Mattermost), `web`, `portal`, `apisix`.
  Đích nào không thuộc app đang chạy thì đặt `required: false` trong cấu hình; chưa từng `UP` thì không ghi `DOWN`.
- Trạng thái cuối nạp từ database khi khởi động để khỏi ghi lặp; `DOWN` chỉ ghi sau 2 lần dò liên tiếp thất bại để tránh báo nhầm do một lần chậm.
- Core/App: dùng URL healthcheck mà `docker-compose.yml` đã dùng, không đụng `HealthContributor`.

**Phạm vi ảnh hưởng**

- Module: `dx-monitor`.
- File chính: `ServiceProbe`, `ServiceHealthEvent`, `ServiceHealthSampler`, `V1__monitor_schema.sql`, `application.yaml`.
- Endpoint, bảng DB, biến môi trường: bảng `monitor.service_health_event`; `monitor.probe.*`.
- Role và client bị ảnh hưởng: không có.
- Tương thích ngược và dữ liệu cũ: bảng mới.

**Kết quả đầu ra**

- Tắt MinIO một phút rồi bật lại: hai dòng `storage` (`DOWN`, `UP`) có thời điểm đúng; dừng backend thì màn hình giám sát vẫn mở được.

**Phụ thuộc, lưu ý**: R5 ở `04`.

## WP4-06: API đọc và truy vết

- Mục tham chiếu: `docs/BACKLOG.md` mục 5 ("Màn hình xem audit trên web").
- Mục tiêu: các truy vấn tổng hợp mà màn hình cần, chỉ đọc, chỉ ADMIN.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Xem số liệu theo khoảng thời gian | `jq` trên log, `psql` | API trả tổng hợp theo cửa sổ `1h`, `24h`, `7d` |
| Tìm theo `request_id` hoặc IP | Ghép tay giữa log gateway, `audit_log`, log backend | Một API trả dòng thời gian hợp nhất từ `gateway_event`, `audit_log` và `vw_auth_events` |

**Hướng làm**

- Endpoint (đều `GET`, ADMIN, `@Operation` tiếng Anh):
  - `/monitor/api/summary?window=24h`: bốn con số (từ chối, `5xx`, `429`, đăng nhập sai) và số dịch vụ đang `DOWN`.
  - `/monitor/api/timeline?window=24h`: sự kiện bất thường theo giờ, chia theo loại; đăng nhập sai theo giờ.
  - `/monitor/api/suspects?window=24h`: IP đứng đầu theo số từ chối, `429`, `401`, `404`, đăng nhập sai; đường dẫn `5xx` nhiều nhất.
  - `/monitor/api/health`: trạng thái hiện tại của từng dịch vụ và các lần đổi trạng thái gần đây kèm thời gian ngừng.
  - `/monitor/api/admin-actions?limit=50`: thao tác quản trị gần đây (kể cả `FAILED`) từ `audit_log`.
  - `/monitor/api/trace?requestId=...` hoặc `?ip=...`: dòng thời gian hợp nhất, tối đa 200 dòng.
- Truy vấn bằng `JdbcTemplate` và SQL có tham số; cửa sổ thời gian chỉ nhận giá trị trong danh sách, `requestId` và `ip` được kiểm định dạng; không ghép chuỗi SQL.
- Không trả `actor_user_id`, `metadata` thô, `user_agent` đầy đủ (rút 120 ký tự).
- Ô Keycloak tách khỏi các ô khác: Keycloak lỗi hoặc `keycloak_ro` thiếu thì chỉ ô đó báo "không có dữ liệu".
- Core/App: toàn bộ ở `dx-monitor`; không import bảng của app.

**Phạm vi ảnh hưởng**

- Module: `dx-monitor`.
- File chính: `SummaryController`, `TraceController`, `MonitorQueryService`, `AuthEventReader`.
- Endpoint, bảng DB, biến môi trường: sáu endpoint nêu trên.
- Role và client bị ảnh hưởng: ADMIN.
- Tương thích ngược và dữ liệu cũ: chỉ đọc.

**Kết quả đầu ra**

- `./mvnw compile` đạt; gọi từng endpoint bằng phiên ADMIN trả đúng dữ liệu kịch bản; vai khác nhận 403.

**Phụ thuộc, lưu ý**: WP4-04, WP4-05 để có dữ liệu thật.

## WP4-07: Giao diện giám sát và link ở portal

- Mục tham chiếu: Q1.
- Mục tiêu: một trang cho ADMIN thấy tình hình tấn công, lỗi và truy ra nguyên nhân.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| ADMIN muốn xem tình hình bảo mật | `jq` trên log, `psql`, Admin Console | Mở portal → "Giám sát" → `/monitor/` |
| Portal | Link tới ứng dụng, BI, chat, quản trị, Swagger | Thêm link "Giám sát" và dòng trạng thái của `monitor` |

**Hướng làm**

Một trang `/monitor/`, tự làm mới mỗi 30 giây, bố cục từ trên xuống: tình hình → xu hướng → thủ phạm → truy vết. Chọn cửa sổ `1h`, `24h`, `7d` ở góc trên.

| Khối | Nội dung | Nguồn API |
|---|---|---|
| Trạng thái dịch vụ | Thẻ từng dịch vụ `UP`/`DOWN`, thời điểm đổi gần nhất; danh sách các lần ngừng gần đây kèm thời gian ngừng | `/api/health` |
| Bốn con số | Truy cập bị từ chối, lỗi `5xx`, `429`, đăng nhập sai; màu đổi khi vượt ngưỡng cảnh báo | `/api/summary` |
| Xu hướng theo giờ | Cột chồng sự kiện bất thường theo loại; đường đăng nhập sai | `/api/timeline` |
| Thủ phạm | Bảng IP đáng ngờ (bấm vào IP để truy vết), bảng đường dẫn `5xx` nhiều nhất | `/api/suspects` |
| Thao tác quản trị | Bảng gần đây, `FAILED` tô màu | `/api/admin-actions` |
| Truy vết | Ô nhập `request_id` hoặc IP, kết quả là dòng thời gian hợp nhất (gateway, backend, Keycloak) | `/api/trace` |

- Thymeleaf cho khung trang và JavaScript thuần gọi các API trên; thư viện biểu đồ MIT nhúng thẳng trong `static/` (ghi vào `LICENSES.md`), không CDN để chạy được khi mất mạng.
- Mọi chuỗi hiển thị là tiếng Việt; IP, đường dẫn và tên người dùng đưa vào trang bằng `textContent` (không `innerHTML`) để không bị chèn script từ chính dữ liệu tấn công.
- CSP chặt cho trang (`default-src 'self'`) vì dữ liệu hiển thị do kẻ tấn công kiểm soát một phần.
- Portal: thêm thẻ "Giám sát" trỏ `/monitor/` và dòng trạng thái kiểm `/monitor/actuator/health`; cập nhật `portal/README.md`.
- Core/App: không đụng core và app.

**Phạm vi ảnh hưởng**

- Module: `dx-monitor`, portal.
- File chính: `templates/monitor.html`, `static/monitor.js`, `static/monitor.css`, thư viện biểu đồ, `portal/index.html`, `portal/README.md`.
- Endpoint, bảng DB, biến môi trường: không có.
- Role và client bị ảnh hưởng: ADMIN thấy; vai khác thấy link ở portal nhưng bị từ chối khi vào.
- Tương thích ngược và dữ liệu cũ: portal chỉ thêm một thẻ.

**Kết quả đầu ra**

- ADMIN thấy trang có dữ liệu sau kịch bản tấn công giả lập; dữ liệu chứa `<script>` trong đường dẫn không chạy; vai khác bị từ chối.

**Phụ thuộc, lưu ý**: WP4-06.

## WP4-08: Cảnh báo, dọn dữ liệu và tài liệu vận hành

- Mục tham chiếu: `AuditAlertJob`, `deploy/OPERATIONS.md`.
- Mục tiêu: sự cố nặng tới tay quản trị ngay, bảng giám sát không phình, quy trình truy vết có tài liệu.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Một IP bị từ chối từ 10 lần trong 10 phút | `AuditAlertJob` của backend báo kênh "Ban quản trị" | Giữ nguyên, không đổi |
| Một IP bị `429` từ 10 lần trong 10 phút | Không báo | `monitor` báo một tin, tối đa một tin mỗi IP mỗi 60 phút |
| Từ 5 lỗi `5xx` trong 5 phút | Không báo | `monitor` báo một tin, tối đa một tin mỗi 60 phút |
| Dịch vụ đổi sang `DOWN` | Không báo | `monitor` báo ngay một tin; khi `UP` lại báo "đã phục hồi" |
| Bản ghi giám sát quá 30 ngày | Không áp dụng | Job hằng ngày xóa `gateway_event` và `service_health_event` cũ |

**Hướng làm**

- Webhook: dùng cùng nguồn như backend (thư mục webhook do `mattermost-bootstrap` tạo, mount vào `monitor`; biến `MATTERMOST_WEBHOOK_URL_ADMIN` nếu đặt); không có webhook thì không gửi.
  Không import `ChatWebhook` của core; một lớp gửi nhỏ riêng trong monitor.
- Ngưỡng ở `monitor.alert.*` và thời hạn giữ ở `monitor.retention-days: 30` trong `application.yaml` (Q4, Q5).
- Tài liệu: `deploy/OPERATIONS.md` có mục "Màn hình giám sát" (khối nào trả lời câu hỏi nào, quy trình truy vết từ `request_id`, khoảng trống khi `monitor` ngừng), `README.md`, `README_EN.md`, `ARCHITECTURE.md`, `backend/README.md`, `portal/README.md`, `LICENSES.md`, `CHANGELOG.md`, `docs/TESTING.md`, `docs/BACKLOG.md`.
- Core/App: toàn bộ ở `dx-monitor`.

**Phạm vi ảnh hưởng**

- Module: `dx-monitor`, Docs.
- File chính: `AlertJob`, `MattermostSender`, `RetentionJob`, `application.yaml`, các tài liệu nêu trên.
- Endpoint, bảng DB, biến môi trường: `monitor.alert.*`, `monitor.retention-days`.
- Role và client bị ảnh hưởng: kênh Mattermost "Ban quản trị".
- Tương thích ngược và dữ liệu cũ: không có webhook thì không gửi (như hiện nay).

**Kết quả đầu ra**

- Kịch bản `5xx`, `429` và dừng dịch vụ mỗi loại gửi đúng một tin; tin lặp không quá một tin mỗi 60 phút; bản ghi cũ bị xóa.

**Phụ thuộc, lưu ý**: Q5.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `GET /api/v1/audit-logs` | Lọc được `action=SERVER_ERROR` và `outcome=FAILED` | Không |
| Endpoint | `/monitor/**` | Mới, ADMIN qua SSO; có giới hạn tốc độ ở gateway | Không |
| Endpoint | `POST /monitor/ingest/gateway` | Mới, chỉ trong mạng Docker, khóa riêng, bị chặn ở route công khai | Không |
| Interface | `AuditEvent` ở `dx-core-api` | Thêm factory `failed(...)` | Không (Farm, Lab chỉ thêm lời gọi) |
| Bảng DB | `monitor.gateway_event`, `monitor.service_health_event` | Mới, xóa sau 30 ngày | Không |
| Role DB | `monitor_app` | Mới, chủ schema `monitor` và `SELECT` trên `audit_log` | Không |
| Role DB (database `keycloak`) | `keycloak_ro` | Mới, chỉ `SELECT` trên view `vw_auth_events` | Không |
| Client SSO | `dxos-monitor` | Mới trong `realm-dxos.json` | Không |
| Biến môi trường | `MONITOR_DB_PASSWORD`, `KEYCLOAK_READONLY_DB_PASSWORD`, `MONITOR_OIDC_CLIENT_SECRET`, `MONITOR_INGEST_KEY` | Mới, bắt buộc | Có với môi trường có sẵn: thêm vào `deploy/.env` (`.env.example` cập nhật) |
| Cấu hình | `monitor.*` (`gateway`, `probe`, `alert`, `retention-days`) | Mới, có mặc định | Không |

## Dữ liệu, migration và rollback

- Migration: Flyway của monitor `db/migration/monitor/V1__monitor_schema.sql` (hai bảng, chỉ mục theo thời gian, `ip`, `request_id`), chạy khi `monitor` khởi động; role, schema và view Keycloak do `monitor-init` tạo.
  Core không có migration mới.
- Dữ liệu cũ: `audit_log` giữ nguyên; bản ghi `FAILED` và `SERVER_ERROR` chỉ có từ lúc triển khai.
- Rollback: bỏ khối `http-logger` khỏi cấu hình APISIX, gỡ service `monitor` và `monitor-init`, `DROP SCHEMA monitor CASCADE`, `DROP ROLE monitor_app`, `keycloak_ro`; gỡ thẻ ở portal.
  Mất: dữ liệu giám sát đã thu.
  `audit_log` và dữ liệu nghiệp vụ không bị đụng; phần ghi `SERVER_ERROR` ở backend vô hại nếu để lại.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Màn hình giám sát là dịch vụ Spring Boot riêng (`dx-monitor`), không nằm trong backend | Còn chạy khi backend sập, và là nơi duy nhất nhận được `UPSTREAM_DOWN` lẫn cảnh báo lúc đó |
| D2 | Module trong `backend/` thay vì thư mục cấp cao riêng | Dùng lại parent pom, `./mvnw`, quy ước và CI backend; vẫn là jar và container riêng, không phụ thuộc core |
| D3 | Giao diện Thymeleaf + JavaScript thuần, biểu đồ nhúng sẵn | Không thêm bước build Node và không CDN; chạy được trong mạng LAN không Internet |
| D4 | Dùng Postgres, Keycloak, Mattermost hiện có; không thêm Prometheus, Grafana, Loki, Superset | Không thêm thành phần cần vận hành trên máy chủ đã chạy Ollama; nhất quán với phase 1 |
| D5 | Chỉ lưu sự kiện bất thường của gateway | Truy vết cần điểm bất thường; lưu hết mỗi request làm bảng lớn mà không thêm giá trị |
| D6 | `audit_log` giữ nguyên chỉ thêm; bảng của monitor xóa sau 30 ngày | Audit là chứng cứ; sự kiện gateway là dữ liệu vận hành lượng lớn |
| D7 | Chỉ ADMIN xem; monitor chỉ đọc, không có thao tác ghi | IP và đường dẫn là dữ liệu vận hành nhạy cảm; màn hình bị chiếm cũng không làm hại hệ thống |
| D8 | `AuditAlertJob` của backend giữ nguyên, monitor lo các cảnh báo mới | Không đụng phần đã chạy ổn; backend sập thì cảnh báo mới vẫn gửi |
