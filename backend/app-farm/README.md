# app-farm: DX-Farm

Ứng dụng quản lý sâu bệnh và thu hoạch của một hợp tác xã trồng lúa nước, chạy trên core DX-OS.
Chỉ phụ thuộc `dx-core-api`; khởi động khi `DXOS_APP=farm` (giá trị mặc định).

## Chức năng

| Khu vực | Nội dung |
|---|---|
| Nông hộ, thửa | Admin tạo nông hộ kèm tài khoản; nông dân đăng ký thửa kèm GPS, admin duyệt; sửa thửa qua yêu cầu có duyệt |
| Vụ mùa | Nông dân đăng ký vụ theo giống lúa, admin duyệt; gợi ý loại vụ theo tháng gieo; thu hoạch, nhắc hạn thu hoạch hằng ngày |
| Báo cáo sâu bệnh | Nông dân gửi ảnh lá (kiểm tra chất lượng và nhận diện lúa, idempotency, chụp lại theo yêu cầu) |
| Chẩn đoán | Kỹ sư chạy chẩn đoán AI hoặc nhập tay, tra rule thuốc có cấu trúc, duyệt; nông dân chỉ thấy kết quả sau khi duyệt; danh sách báo cáo chưa chẩn đoán cho kỹ sư |
| Phun thuốc | Nông dân khai phun, kỹ sư xác nhận, vụ vào cách ly và thu hoạch bị khóa tới hết PHI |
| Tri thức | Hỏi đáp tài liệu bệnh lúa có trích nguồn (RAG namespace `farm`) |
| Thời tiết | Dự báo theo thửa từ Open-Meteo, snapshot dự phòng khi không gọi được; mỗi giờ lưu số đo thật của từng vị trí thửa cho chart Superset |

## Vai

| Vai | Làm được |
|---|---|
| `FARMER` | Thửa, vụ và báo cáo của hộ mình, khai phun thuốc, thu hoạch, hộp thông báo; chỉ dùng app mobile (client `farm-mobile`), token web bị 403 |
| `ENGINEER` | Xem mọi báo cáo, chẩn đoán và duyệt chẩn đoán, xác nhận khai phun, nhập thủ công, hàng chờ chẩn đoán |
| `ADMIN` | Tạo nông hộ và tài khoản, duyệt thửa và vụ, mọi việc của kỹ sư, tạo thửa và vụ thay hộ |

`ENGINEER` và `ADMIN` là vai nhân sự: thấy mọi nông hộ.
Workspace của Farm có loại `HOUSEHOLD` (nông hộ, mã `H0001`...).

## Vòng đời

| Loại case | Trạng thái |
|---|---|
| `PARCEL_APPROVAL`, `PARCEL_EDIT_REQUEST`, `CROP_SEASON_APPROVAL`, `DIAGNOSIS_APPROVAL`, `TREATMENT_CONFIRMATION` | `PENDING` → `APPROVED`, `REJECTED` hoặc `CANCELLED`; `REJECTED` → `PENDING` khi gửi lại |

Trạng thái và lịch sử nằm ở workflow dùng chung (`workflow_case`, `workflow_event`).
Cột trạng thái trên bảng của Farm phản chiếu trạng thái đó.

## Quy tắc (poka-yoke chặn ở backend)

- GPS giả lập (`GPS_MOCKED`) hoặc sai số lớn hơn `app.gps-max-accuracy-meters` bị từ chối.
- Thửa chưa duyệt không tạo được vụ (`PARCEL_NOT_APPROVED`).
- Thu hoạch trước ngày dự kiến cần xác nhận kèm lý do (`HARVEST_TOO_EARLY`).
- Ảnh trắng hoặc không phải lúa không qua kiểm tra chất lượng; ảnh được xóa EXIF trước khi lưu.
- Liều và PHI chỉ lấy từ `TreatmentRule` đã được chuyên gia duyệt; bệnh chưa có rule chỉ ở trạng thái `review_required`.
- Bản khai phun chờ kỹ sư xác nhận quá `app.treatment-confirmation-wait-hours` giờ được nhắc lên kênh kỹ sư mỗi sáng tới khi có quyết định.
- Bản khai phun chờ kỹ sư xác nhận thì chặn thu hoạch (`TREATMENT_PENDING_CONFIRMATION`); sau khi xác nhận, thu hoạch bị chặn tới `harvestLockedUntil` (`POKA_YOKE_VIETGAP_VIOLATION`).
- Mỗi người có số yêu cầu chờ duyệt tối đa theo `app.max-pending-requests` của core, tính cho các loại case do `AppModule.limitedCaseTypes()` khai báo.

## Interface của core đã cài

Cấu hình bằng bean ở `FarmAppModule` (id `farm`, workspace `HOUSEHOLD`), `FarmSecurityPolicies` (vai, danh mục công khai, FARMER chỉ qua mobile), `FarmWorkflowConfig` (5 loại duyệt), `FarmNotificationConfig` (13 loại thông báo, kênh `ADMIN` và `ENGINEER`), `FarmPlatformConfig` (6 view `vw_*` và 3 bảng danh mục), `FarmKnowledgeNamespace` (`farm`) và `DiseaseImageProcessor` (chỉ JPEG/PNG, xóa EXIF).
Những gì ứng dụng khai báo ở [`ARCHITECTURE.md`](../../ARCHITECTURE.md) mục 3.

## Mã thửa, mã vụ và duyệt

**Mã thửa** `<MÃ HTX>-<MÃ HỘ>-T<STT>`, ví dụ `HTX01-H0042-T03`.

- Mã HTX lấy từ `app.htx-code` (mặc định `HTX01`); mã hộ là mã nông hộ (`H0042`).
- STT tăng dần theo từng hộ, hai chữ số trở lên, lấy từ bảng `household_parcel_counter` trong cùng transaction với việc lưu thửa; hai request đồng thời nhận hai số khác nhau, STT không dùng lại sau khi xóa hay bị từ chối.
- Client không gửi mã, backend sinh; `POST /api/v1/parcels` bắt buộc `latitude` và `longitude`.

**Mã vụ** `<MÃ THỬA>-<NĂM><LOẠI VỤ>`, ví dụ `HTX01-H0042-T03-2026DX`; năm là năm của ngày bắt đầu vụ.

| Loại vụ | Hậu tố | Tháng bắt đầu gợi ý |
|---|---|---|
| Đông Xuân (`DONG_XUAN`) | `DX` | 11, 12, 1 |
| Hè Thu (`HE_THU`) | `HT` | 3, 4, 5 |
| Thu Đông (`THU_DONG`) | `TD` | 6, 7, 8 |

- Mốc tháng ở `app.season-start-months` là số tạm, chưa có kỹ sư nông nghiệp xác nhận; tháng ngoài các mốc không có gợi ý, nông dân tự chọn loại vụ.
- Một thửa có tối đa một vụ mỗi loại mỗi năm và một vụ `ACTIVE` tại một thời điểm; vụ bị từ chối không giữ chỗ.

**GPS**: nông dân gửi thêm `gpsAccuracyMeters` lấy từ điện thoại; sai số lớn hơn `app.gps-max-accuracy-meters` hoặc `mockedLocation=true` bị từ chối (`422`: `GPS_ACCURACY_TOO_LOW`, `GPS_ACCURACY_MISSING`, `GPS_MOCKED`).
Quản trị viên nhập hộ không cần sai số.

**Duyệt**

- Thửa và vụ do nông dân tạo ở trạng thái `PENDING` cho tới khi quản trị viên duyệt qua `PATCH /api/v1/workflow-cases/{id}/approve` hoặc `/reject` (kèm lý do); do quản trị viên tạo thì duyệt ngay.
- Chưa duyệt thì chưa tạo được vụ trên thửa (`PARCEL_NOT_APPROVED`), chưa gửi báo cáo hay thu hoạch trên vụ (`CROP_SEASON_NOT_APPROVED`).
- Bị từ chối thì nông dân sửa và gửi lại (`POST /parcels/{id}/resubmit`, `POST /crop-seasons/{id}/resubmit`); lịch sử nằm ở timeline của case.
- Sửa thửa đã duyệt là một yêu cầu riêng (`POST /parcels/{id}/edit-requests`), thửa giữ giá trị cũ tới khi được duyệt.
- Mỗi nông dân có tối đa `app.max-pending-requests` (3) yêu cầu chờ duyệt cùng lúc (`TOO_MANY_PENDING_REQUESTS`).

## Dữ liệu

- Migration `db/migration/farm` (bảng lịch sử `flyway_schema_history_farm`), chạy sau migration của core.
- `farm/data.sql` seed thuốc và treatment rule đã duyệt, sinh từ `data/structured/*.json` bằng `data/scripts/generate_seed_sql.py`; không sửa tay.
- `weather-snapshot.json` là snapshot thời tiết dự phòng; `knowledge-sources.json` là danh mục nguồn tài liệu RAG (cùng thư mục `resources`).

## Cấu hình

| Thuộc tính | Biến môi trường | Mặc định |
|---|---|---|
| `app.htx-code` | `HTX_CODE` | `HTX01` |
| `app.gps-max-accuracy-meters` | `GPS_MAX_ACCURACY_METERS` | `30` |
| `app.harvest-reminder-cron` | `HARVEST_REMINDER_CRON` | `0 0 7 * * *` |
| `app.harvest-reminder-max-days` | `HARVEST_REMINDER_MAX_DAYS` | `14` |
| `app.treatment-confirmation-reminder-cron` | `TREATMENT_CONFIRMATION_REMINDER_CRON` | `0 0 8 * * *` |
| `app.treatment-confirmation-wait-hours` | `TREATMENT_CONFIRMATION_WAIT_HOURS` | `24` |
| `app.season-start-months.*` | `SEASON_MONTHS_DONG_XUAN`, `SEASON_MONTHS_HE_THU`, `SEASON_MONTHS_THU_DONG` | `11,12,1`, `3,4,5`, `6,7,8` |
| `security.farmer-client-ids` | `FARMER_CLIENT_IDS` | `farm-mobile` |
| `weather.base-url` | `WEATHER_BASE_URL` | `https://api.open-meteo.com` |
| `weather.connect-timeout-ms`, `read-timeout-ms` | `WEATHER_CONNECT_TIMEOUT_MS`, `WEATHER_READ_TIMEOUT_MS` | `2000`, `3000` |
| `weather.cache-ttl` | `WEATHER_CACHE_TTL` | `1h` |
| `weather.collect-interval` | `WEATHER_COLLECT_INTERVAL` | `1h` |
| `mattermost.webhook.url-admin`, `url-engineer` | `MATTERMOST_WEBHOOK_URL_ADMIN`, `MATTERMOST_WEBHOOK_URL_ENGINEER` | trống: dùng webhook tự tạo của channel |
| `mattermost.webhook.image-base-url` | `MATTERMOST_IMAGE_BASE_URL` | trống: thông báo không kèm ảnh |
| `mattermost.webhook.app-base-url` | `MATTERMOST_APP_BASE_URL` | trống: thông báo không kèm liên kết mở báo cáo |

Cấu hình nằm ở `dx-host/src/main/resources/application-farm.yaml`.
Mobile đọc `app.gps-max-accuracy-meters` từ `settings.gpsMaxAccuracyMeters` của `GET /api/v1/app`.
Gọi AI chẩn đoán có circuit breaker `aiDiagnosis` (Resilience4j).

## API

Swagger UI ở `/swagger-ui.html`.

| Nhóm | Đường dẫn |
|---|---|
| Nông hộ | `/api/v1/households` (bí danh của `/api/v1/workspaces` với loại `HOUSEHOLD`) |
| Thửa, vụ | `/api/v1/parcels`, `/api/v1/crop-seasons` (`harvest`, `disease-reports`) |
| Báo cáo, chẩn đoán | `/api/v1/disease-reports` (`diagnosis`, `diagnosis/ai`, `request-retake`, `treatment-application`, `awaiting-diagnosis` cho danh sách báo cáo chưa chẩn đoán) |
| Danh mục | `/api/v1/diseases`, `/api/v1/pesticides`, `/api/v1/treatment-rules`, `/api/v1/rice-varieties` |
| Hộp duyệt | `/api/v1/approval-requests`, `/api/v1/workflow-cases` |
| Tri thức, thời tiết | `/api/v1/knowledge/ask`, `/api/v1/parcels/{code}/weather` |

Lỗi nghiệp vụ trả 422 kèm mã (`GPS_MOCKED`, `HARVEST_TOO_EARLY`, `POKA_YOKE_VIETGAP_VIOLATION`...); xử lý đồng thời trả 409.
