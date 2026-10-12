# Bản đồ module

Danh sách file và thành phần theo module đích.
Tên package và artifact đã chốt (RD8); xem `phase-c0-chot-thiet-ke-nen/00_OVERVIEW.md` mục 9.

## Bố cục

```
backend/
  pom.xml                 parent (dx-os-parent)
  dx-core-api/            HaUI.ImplSolution.dxos.core.api
  dx-core/                HaUI.ImplSolution.dxos.core
  app-farm/               HaUI.ImplSolution.dxos.farm
  app-lab/                HaUI.ImplSolution.dxos.lab
  dx-host/                HaUI.ImplSolution.dxos.host   (fat jar, Dockerfile)
```

`backend/DX-Farm` hiện tại được thay bằng bố cục trên.

## dx-core-api

| Nhóm | Nội dung |
|---|---|
| Interface app cài | `AppModule`, `RoleProvider`, `CaseTypeDefinition`, `WorkflowDecisionHandler`, `CaseSummaryProvider`, `NotificationChannel`, `BiDatasetProvider`, `FileProcessor`, `KnowledgeNamespace`, `EndpointPolicyContributor`, `ClientRestrictionPolicy`, `HealthContributor` |
| Facade app gọi | `WorkflowOperations`, `WorkspaceDirectory`, `NotificationPublisher`, `ObjectStorage`, `UserContext`, `KnowledgeGateway`, `AiGateway` |
| View và DTO | `CaseView`, `WorkspaceView`, `ApiResponse`, `PageResponse`, DTO của workspace, workflow, notification, approval, knowledge, status |
| Enum | `WorkflowAction`, `WorkflowStatus` (hoặc trạng thái cấu hình được, xem RD10) |
| Lỗi | `ResourceNotFoundException`, `InvalidStateException`, `PokaYokeViolationException`, `AccountNotLinkedException`, `AiServiceException` |

Chi tiết từng interface ở [`SPI_CATALOG.md`](./SPI_CATALOG.md).

## dx-core

| Nhóm | Nguồn hiện tại | Ghi chú |
|---|---|---|
| Identity | `core/identity/*` (Household, HouseholdService, HouseholdController, KeycloakAdminClient), `KeycloakAdminException`, `CurrentUserService` | `Household` thành `Workspace` + `WorkspaceMember`; `isFarmer/isAdmin` thành kiểm tra theo vai |
| Workflow | `core/workflow/*` (gồm `WorkflowActor`, `WorkflowController`), `ApprovalRequestService`, `ApprovalRequestController`, `PendingRequestLimiter` | Làm giàu hộp duyệt qua `CaseSummaryProvider`; giới hạn yêu cầu chờ lấy case type từ cấu hình app |
| Storage | `core/storage/*` (gồm `StorageBucketInitializer`) | Giữ nguyên |
| Notification | `NotificationService`, `Notification`, `NotificationRepository`, `NotificationController`, `NotificationResponse` | `NotificationType` từ enum thành mã chuỗi do app đăng ký; nằm ở `app-farm` cho tới C2 |
| Kênh chat | `MattermostWebhookClient` | Khóa kênh là chuỗi do app khai báo; lời nhắn do app viết; nằm ở `app-farm` cho tới C2 |
| AI gateway | Phần chung của `AiDiagnosisClient` (khóa dịch vụ, health, retrieve), `KnowledgeService`, `KnowledgeController` | `namespace` cho RAG; nằm ở `app-farm` cho tới C2 |
| Bảo mật | `SecurityConfig`, `KeycloakRoleConverter`, `RestAccessDeniedHandler`, `RestAuthenticationEntryPoint`, `ServiceApiKeyAuthenticationFilter`, bộ lọc hạn chế client (từ `FarmerClientRestrictionFilter`) | Endpoint công khai và hạn chế client lấy từ interface |
| Nền tảng | `GlobalExceptionHandler`, `AsyncConfig`, `SchedulingConfig`, `TimeConfig`, `OpenApiConfig`, `SystemStatusService`, `SystemStatusController` | Trạng thái hệ thống gom từ `HealthContributor`; `SystemStatusService` và controller nằm ở `app-farm` cho tới C2 |
| Khởi tạo hạ tầng | `KeycloakDatabaseSeeder`, `MattermostDatabaseSeeder`, `AiVectorDatabaseSeeder`, `SupersetReadOnlyRoleSeeder` | Seeder Superset đọc view từ `BiDatasetProvider` |
| Migration | `households`, `workflow_case`, `workflow_event`, `notifications` | `db/migration/core`, bảng lịch sử riêng |

## app-farm

| Nhóm | Nội dung |
|---|---|
| Sự kiện | Năm event nghiệp vụ trong `event/` (`ParcelCreatedEvent`, `CropSeasonCreatedEvent`, `DiseaseReportCreatedEvent`, `DiagnosisPendingEvent`, `TreatmentReportedEvent`); `MattermostChannel` (ADMIN, ENGINEER) |
| Model và repository | `Parcel`, `ParcelEditRequest`, `CropSeason`, `HarvestReschedule`, `DiseaseReport`, `Diagnosis`, `Disease`, `Pesticide`, `TreatmentRule`, `TreatmentApplication`, `DiseaseManagementPolicy`, `RiceVariety`, `TrainingLabel` và enum đi kèm (`ApprovalStatus`, `CropSeasonStatus`, `SeasonType`, `DiseaseReportStatus`, `TreatmentConfirmationStatus`) |
| Service | `ParcelService`, `CropSeasonService`, `DiseaseReportService`, `DiagnosisService`, `DiseaseService`, `PesticideService`, `RiceVarietyService`, `TreatmentRuleService`, `WeatherService`, `HarvestReminderService`, `HarvestReminderScheduler`, `MattermostNotificationListener`, `GpsFixValidator`, `ParcelCodeGenerator`, `SeasonCalendar`, `InternalAiContextService`, ba handler duyệt, `ApprovalCaseTypes`, `MattermostMessageBuilder` |
| AI riêng của Farm | Phần precheck, diagnose, symptoms của `AiDiagnosisClient`; `AiTriageException`; `FileStorageService` (xóa EXIF, thành `FileProcessor`) |
| Controller | `ParcelController`, `CropSeasonController`, `DiseaseReportController`, `DiagnosisController`, `DiseaseController`, `PesticideController`, `RiceVarietyController`, `TreatmentApplicationController`, `TreatmentRuleController`, `InternalAiContextController` |
| DTO | Khoảng 28 DTO nghiệp vụ (parcel, crop season, diagnosis, report, harvest, treatment, weather...) |
| Cấu hình | Hạn chế client FARMER chỉ dùng mobile, `RoleProvider` (FARMER, ENGINEER, ADMIN), `BiDatasetProvider` (`vw_*`), `app.*`, `weather.*` |
| Dữ liệu | Migration của Farm (V1 đến V10, trừ phần của core), `data.sql` (sinh bởi `data/scripts/generate_seed_sql.py` từ `data/structured/*.json`, gồm thuốc và treatment rule đã duyệt), `knowledge-sources.json`, `weather-snapshot.json` |
| Ngoài backend | `mobile/`, CV trong `ai/`, dashboard Superset của Farm, `data/` |

## app-lab

| Nhóm | Nội dung |
|---|---|
| Domain | Ticket (bình luận, tệp đính kèm, SLA, đánh giá), loại sự cố, chính sách SLA; phòng; thiết bị, nhật ký bảo trì, phiếu mượn trả; đặt phòng máy |
| Workflow | `CaseTypeDefinition` cho `TICKET`, `DEVICE_LOAN`, `ROOM_BOOKING` |
| Vai | STUDENT, LECTURER, TECHNICIAN, ADMIN (qua `RoleProvider`); workspace `CLASS`, `DEPARTMENT` |
| Lịch chạy | Cảnh báo SLA, nhắc mượn trả |
| Thông minh | Phân loại và ưu tiên bằng luật, gợi ý bài tri thức, hỏi đáp RAG namespace `lab` |
| Dữ liệu | Migration riêng (extension `btree_gist`), `BiDatasetProvider` (`vw_lab_*`), dữ liệu mẫu giả lập, tri thức tự soạn CC BY 4.0 |
| Giao diện | Web trong `frontend/`: ticket, thiết bị, đặt phòng, tổng quan và thống kê; mobile để sau |

## dx-host

- Class `main`, `application.yaml` chung, `application-farm.yaml`, `application-lab.yaml`.
- Property `dxos.app` (biến `DXOS_APP`); mỗi app chỉ nạp khi bật `@ConditionalOnProperty`.
- Endpoint `GET /api/v1/app` trả manifest của app đang chạy.
- Fat jar, Dockerfile, script `deploy/switch-app.sh`.

## Điểm còn mơ hồ

| # | Điểm | Cách xử lý dự kiến | Phase |
|---|---|---|---|
| 1 | `ApprovalRequestService` gọi 5 repository của Farm | Tách khung lọc vào core, làm giàu qua `CaseSummaryProvider` | C2 |
| 2 | `AiDiagnosisClient` chứa cả phần chung và phần Farm | Tách đôi: `AiGateway` ở core, client chẩn đoán ở Farm | C2 |
| 3 | `Parcel` và `ParcelRepository` tham chiếu entity `Household` | App giữ id, tra cứu qua `WorkspaceDirectory`; bỏ quan hệ JPA xuyên module | C1 |
| 4 | Migration V2 và V4 có bước backfill đọc `parcels` và `diagnoses` | Bỏ backfill khi re-baseline, core không đọc bảng của app | C1 |
| 5 | Migration V3 trộn audit và soft delete của nhiều bảng Farm | Chuyển hẳn vào migration Farm | C1 |
| 6 | Bảng `notifications` nằm trong migration V7 của Farm | Chuyển vào migration core cùng đợt đổi `NotificationType` | C2 |
| 7 | `WorkflowStatus` là enum cố định 4 trạng thái | RD10: trạng thái do `CaseTypeDefinition` khai báo | C2 |
| 8 | Realm `dxfarm` có sẵn vai và user mẫu của Farm | Realm trung tính, vai của app được tạo lúc khởi động | C3 |
| 9 | `data.sql` ở gốc classpath, chạy qua `spring.sql.init` | Tài nguyên riêng của Farm, nạp theo `application-farm.yaml`; generator và CI đổi đường dẫn | C1 |
| 10 | Spring Boot chỉ tự cấu hình một Flyway | Hai bean Flyway (core, Farm), bảng lịch sử riêng, core chạy trước | C1 |
| 11 | `HouseholdMigrationTest`, `WorkflowMigrationTest` kiểm backfill của V2, V4 | Bỏ hoặc viết lại khi bỏ backfill | C1 |
