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
| Interface app cài | `AppModule`, `RoleProvider`, `CaseTypeDefinition`, `WorkflowDecisionHandler`, `CaseSummaryProvider`, `NotificationTypeDefinition`, `NotificationChannel`, `BiDatasetProvider`, `FileProcessor`, `KnowledgeNamespace`, `EndpointPolicyContributor`, `ClientRestrictionPolicy`, `HealthContributor` |
| Facade app gọi | `WorkflowOperations`, `ApprovalInbox`, `PendingRequestLimit`, `WorkspaceDirectory`, `WorkspaceAdministration`, `NotificationPublisher`, `ChatWebhook`, `ObjectStorage`, `FileStore`, `UserContext`, `KnowledgeGateway`, `AiGateway` |
| View và DTO | `CaseView`, `WorkspaceView`, `ApiResponse`, `PageResponse`, DTO của workspace, workflow, notification, approval, knowledge, status |
| Hằng số | `WorkflowAction`, `WorkflowStatus` (tên của luồng duyệt chuẩn; trạng thái thật là chuỗi theo `CaseTypeDefinition`) |
| Lỗi | `ResourceNotFoundException`, `InvalidStateException`, `PokaYokeViolationException`, `AccountNotLinkedException`, `AiServiceException` |

Chi tiết từng interface ở [`SPI_CATALOG.md`](./SPI_CATALOG.md).

## dx-core

| Nhóm | Thành phần | Ghi chú |
|---|---|---|
| Identity | `Workspace`, `WorkspaceMember`, `WorkspaceService`, `WorkspaceDirectoryService`, `WorkspaceController` (`/api/v1/workspaces`), `WorkspaceTypes`, `WorkspaceCodeGenerator`, `KeycloakAdminClient`, `CurrentUserService` | Tiền tố mã và loại workspace do `AppModule` khai báo; vai theo `RoleProvider` |
| Workflow | `WorkflowService`, `WorkflowDefinitions`, `WorkflowController`, `ApprovalInboxService`, `PendingRequestLimiter` | Trạng thái và bước chuyển theo `CaseTypeDefinition`; hộp duyệt làm giàu qua `CaseSummaryProvider` |
| Storage | `StorageService`, `FileStoreService`, `StorageBucketInitializer` | `FileStoreService` chạy `FileProcessor` của mục đích trước khi lưu |
| Notification | `Notification`, `NotificationInboxService`, `NotificationController`, `NotificationTypes`, `MattermostWebhookClient` | Loại thông báo là mã chuỗi do app đăng ký; khóa kênh chat là chuỗi |
| AI gateway | `AiServiceGateway`, `KnowledgeGatewayService` | `namespace` cho RAG; nguồn tài liệu theo `KnowledgeNamespace` |
| Bảo mật | `SecurityConfig`, `AppRoles`, `ClientRestrictionFilter`, `RoleBootstrap`, `KeycloakRoleConverter`, `RestAccessDeniedHandler`, `RestAuthenticationEntryPoint`, `ServiceApiKeyAuthenticationFilter` | Endpoint công khai và hạn chế client lấy từ interface |
| Nền tảng | `GlobalExceptionHandler`, `AsyncConfig`, `SchedulingConfig`, `TimeConfig`, `OpenApiConfig`, `SystemStatusService`, `SystemStatusController` | Trạng thái hệ thống gom từ `HealthContributor` |
| Khởi tạo hạ tầng | `KeycloakDatabaseSeeder`, `MattermostDatabaseSeeder`, `AiVectorDatabaseSeeder`, `SupersetReadOnlyRoleSeeder` | Seeder Superset đọc view từ `BiDatasetProvider` |
| Migration | `workspaces`, `workspace_members`, `workspace_code_counter`, `workflow_case`, `workflow_event`, `notifications` | `db/migration/core`, bảng lịch sử riêng |

## app-farm

| Nhóm | Nội dung |
|---|---|
| Sự kiện | Năm event nghiệp vụ trong `event/` (`ParcelCreatedEvent`, `CropSeasonCreatedEvent`, `DiseaseReportCreatedEvent`, `DiagnosisPendingEvent`, `TreatmentReportedEvent`); `MattermostChannel` (ADMIN, ENGINEER) |
| Model và repository | `Parcel`, `ParcelEditRequest`, `CropSeason`, `HarvestReschedule`, `DiseaseReport`, `Diagnosis`, `Disease`, `Pesticide`, `TreatmentRule`, `TreatmentApplication`, `DiseaseManagementPolicy`, `RiceVariety`, `TrainingLabel` và enum đi kèm (`ApprovalStatus`, `CropSeasonStatus`, `SeasonType`, `DiseaseReportStatus`, `TreatmentConfirmationStatus`) |
| Service | `ParcelService`, `CropSeasonService`, `DiseaseReportService`, `DiagnosisService`, `DiseaseService`, `PesticideService`, `RiceVarietyService`, `TreatmentRuleService`, `WeatherService`, `HarvestReminderService`, `HarvestReminderScheduler`, `MattermostNotificationListener`, `GpsFixValidator`, `ParcelCodeGenerator`, `SeasonCalendar`, `InternalAiContextService`, `NotificationService`, ba handler duyệt, năm `*CaseSummaryProvider`, `ApprovalCaseTypes`, `MattermostMessageBuilder` |
| AI riêng của Farm | Phần precheck, diagnose, symptoms của `AiDiagnosisClient`; `AiTriageException`; `DiseaseImageProcessor` (`FileProcessor`: chỉ JPEG/PNG, xóa EXIF) và `FileStorageService`; `FarmKnowledgeNamespace` |
| Controller | `ParcelController`, `CropSeasonController`, `DiseaseReportController`, `DiagnosisController`, `DiseaseController`, `PesticideController`, `RiceVarietyController`, `TreatmentApplicationController`, `TreatmentRuleController`, `InternalAiContextController`, `HouseholdController` (`/api/v1/households`), `ApprovalRequestController`, `KnowledgeController` |
| DTO | Khoảng 28 DTO nghiệp vụ (parcel, crop season, diagnosis, report, harvest, treatment, weather...) |
| Cấu hình | `FarmAppModule`, `FarmSecurityPolicies` (`RoleProvider`, `EndpointPolicyContributor`, `ClientRestrictionPolicy`), `FarmWorkflowConfig` (`CaseTypeDefinition`), `FarmNotificationConfig`, `FarmPlatformConfig` (`BiDatasetProvider`, `HealthContributor`), `app.*`, `weather.*` |
| Dữ liệu | Migration của Farm (V1 đến V10, trừ phần của core), `data.sql` (sinh bởi `data/scripts/generate_seed_sql.py` từ `data/structured/*.json`, gồm thuốc và treatment rule đã duyệt), `knowledge-sources.json`, `weather-snapshot.json` |
| Ngoài backend | `mobile/`, CV trong `ai/`, dashboard Superset của Farm, `data/` |

## app-lab

| Nhóm | Nội dung |
|---|---|
| Domain | `Ticket`, `TicketComment`, `TicketAttachment`, `TicketCategory`, `SlaPolicy`; `Room`, `Device`, `DeviceMaintenance`, `DeviceLoan`, `RoomBooking` |
| Workflow | `CaseTypeDefinition` cho `TICKET`, `DEVICE_LOAN`, `ROOM_BOOKING` (`LabWorkflowConfig`); ba `*DecisionHandler`, ba `*SummaryProvider` |
| Vai | STUDENT, LECTURER, TECHNICIAN, ADMIN (`LabSecurityPolicies`); workspace `CLASS`, `DEPARTMENT` (`LabAppModule`) |
| Service | `TicketService`, `TicketSlaService`, `TicketClassifier`, `TicketSuggestionService`, `DeviceService`, `DeviceMaintenanceService`, `DeviceLoanService`, `RoomService`, `RoomBookingService`, `LoanReminderService`, `LabStatsService`, `KnowledgeService`, `LabNotifier`, `LabChatListener` |
| Lịch chạy | `LabScheduler`: kiểm tra SLA, nhắc mượn trả |
| Controller | `TicketController`, `DeviceController`, `RoomController`, `CatalogController`, `DeviceLoanController`, `RoomBookingController`, `LabStatsController`, `KnowledgeController`, `ApprovalRequestController` |
| Cấu hình | `LabAutoConfiguration`, `LabNotificationConfig`, `LabPlatformConfig` (`BiDatasetProvider`, `HealthContributor`), `lab.*` |
| Dữ liệu | Migration `db/migration/lab` (V1 schema và extension `btree_gist`, V2 view `vw_lab_*`, V3 loại sự cố và SLA mặc định), `lab/classifier-rules.json`, `lab/knowledge-sources.json` |
| Tri thức | `data/rag/lab/` (10 bài tự soạn CC BY 4.0, registry nguồn), nạp bằng `ingest_diseases --corpus lab` |
| Giao diện | Web trong `frontend/src/apps/lab/`; mobile để sau |

## frontend

| Thư mục | Nội dung |
|---|---|
| `src/shell/` | Vỏ dùng chung: `auth`, `api` (client, hộp duyệt, thông báo, workspace, tri thức), khung trang và menu theo manifest, `Requests`, `CaseDetail`, `Knowledge`, `Workspaces`, `Notifications`, thành phần dùng chung; `index.ts` là cửa duy nhất của app vào vỏ |
| `src/apps/farm/` | Module Farm: trang, panel, `api`, `types`, nhãn và thông báo lỗi riêng |
| `src/apps/lab/` | Module Lab: tổng quan, yêu cầu hỗ trợ, hàng đợi, mượn trả, đặt phòng, tài sản, thống kê, danh mục, hỏi đáp |
| `src/appRegistry.ts` | Ánh xạ `id` của app sang hàm nạp module (`import()`), nằm ngoài `shell/` |
| `src/App.tsx` | Đăng nhập, đọc manifest, nạp module, dựng vỏ |

Thêm giao diện cho app mới:

1. Tạo `src/apps/<id>/index.tsx` xuất mặc định một `AppUiModule` (lời giới thiệu, nhãn vai, `routes`, nhãn trạng thái và thông báo lỗi riêng nếu có).
2. Mỗi `route` có `path` trùng đường dẫn của mục menu trong manifest; trang chi tiết đặt `menuPath` về mục menu của nó để dùng chung quyền.
3. Thêm một dòng vào `src/appRegistry.ts`.
4. Chỉ import từ `src/shell` (qua `index.ts`) và từ chính thư mục app.

## dx-host

- Class `main`, `application.yaml` chung, `application-farm.yaml`, `application-lab.yaml`.
- Property `dxos.app` (biến `DXOS_APP`); mỗi app chỉ nạp khi bật `@ConditionalOnProperty`.
- Endpoint `GET /api/v1/app` trả manifest của app đang chạy.
- Fat jar, Dockerfile, script `deploy/switch-app.sh`.

## Điểm còn mơ hồ

| # | Điểm | Cách xử lý dự kiến | Phase |
|---|---|---|---|
| 1 | `ApprovalRequestService` gọi 5 repository của Farm | Khung lọc nằm ở `ApprovalInboxService` của core, làm giàu qua `CaseSummaryProvider`; controller và DTO ở Farm | C2 |
| 2 | `AiDiagnosisClient` chứa cả phần chung và phần Farm | `AiGateway` ở core, client chẩn đoán ở Farm | C2 |
| 3 | `Parcel` và `ParcelRepository` tham chiếu entity `Household` | App giữ id, tra cứu qua `WorkspaceDirectory`; bỏ quan hệ JPA xuyên module | C1 |
| 4 | Migration V2 và V4 có bước backfill đọc `parcels` và `diagnoses` | Bỏ backfill khi re-baseline, core không đọc bảng của app | C1 |
| 5 | Migration V3 trộn audit và soft delete của nhiều bảng Farm | Chuyển hẳn vào migration Farm | C1 |
| 6 | Bảng `notifications` nằm trong migration V7 của Farm | Nằm ở migration core, `type` là chuỗi | C2 |
| 7 | `WorkflowStatus` là enum cố định 4 trạng thái | Trạng thái do `CaseTypeDefinition` khai báo (RD10) | C2 |
| 8 | Realm `dxfarm` có sẵn vai và user mẫu của Farm | Realm trung tính, vai của app được tạo lúc khởi động | C3 |
| 9 | `data.sql` ở gốc classpath, chạy qua `spring.sql.init` | Tài nguyên riêng của Farm, nạp theo `application-farm.yaml`; generator và CI đổi đường dẫn | C1 |
| 10 | Spring Boot chỉ tự cấu hình một Flyway | Hai bean Flyway (core, Farm), bảng lịch sử riêng, core chạy trước | C1 |
| 11 | `HouseholdMigrationTest`, `WorkflowMigrationTest` kiểm backfill của V2, V4 | Bỏ hoặc viết lại khi bỏ backfill | C1 |
