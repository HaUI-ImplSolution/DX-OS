# Plan thực hiện: Phase 3

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP3-02 (workflow), WP3-03 (gateway), WP3-04 (storage) độc lập về code, làm song song được.
WP3-01 (ranh giới Core/App + sơ đồ) nên làm cuối cùng vì sơ đồ cần phản ánh đúng những gì 3 WP kia đã dựng, không vẽ trước rồi code lệch sơ đồ.
Thứ tự đề xuất: (WP3-02 ∥ WP3-03 ∥ WP3-04) → WP3-01.

## WP3-01: Ranh giới Core và DX-Farm, sơ đồ 3 tầng

- Mục tham chiếu: mục 7 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: chỉ ra được bằng code (không chỉ bằng lời) phần nào là Core dùng chung, phần nào là ứng dụng DX-Farm.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `backend/DX-Farm` là một module Maven duy nhất, package-by-layer (`controller/service/repository/model/dto`), không có module con nào tách riêng Core.
  `Household`, `workflow_case`/`workflow_event` (WP3-02), storage service (WP3-04) đều có thể định danh rõ là Core; `Parcel`, `CropSeason`, `DiseaseReport`, `Diagnosis`, `Disease`, `Pesticide`, `TreatmentRule` là nghiệp vụ nông nghiệp (App).

**Hướng làm**

- Không tách module Maven riêng ở phase này (rủi ro refactor lớn, không cần thiết để chứng minh ranh giới).
  Thay vào đó: nhóm package theo `core.identity`, `core.workflow`, `core.storage` và `app.agri` (hoặc giữ package hiện tại nhưng viết rõ trong README/sơ đồ package nào thuộc Core, package nào thuộc App).
  Chọn phương án theo mức độ rủi ro chấp nhận được khi bắt đầu code (ghi vào `04_TRADEOFFS_RISKS.md`).
- Vẽ sơ đồ 3 tầng (Tầng 1 Core — Identity/Workflow/Gateway/Storage, H-P-D-I, Tầng 3 — DX-Farm) và sơ đồ luồng request (người dùng → gateway → Core/App → kết quả), lưu dưới `development/docs/` dạng ảnh hoặc mã nguồn diagram (mermaid/draw.io), không chỉ mô tả bằng văn bản.
- README cập nhật mục kiến trúc, trỏ tới sơ đồ, nêu rõ ví dụ cụ thể: "đổi ứng dụng (ví dụ P9) không cần sửa `core.identity`/`core.workflow`/`core.storage`, chỉ thêm `app.*` mới".

**Phạm vi ảnh hưởng**

- Module và file chính: cấu trúc package backend (nếu chọn tái tổ chức), `README.md`, file sơ đồ mới trong `development/docs/`.
- Endpoint, bảng DB, biến môi trường: không có thay đổi hành vi, đây là việc tổ chức code và tài liệu.
- Role và client bị ảnh hưởng: không ảnh hưởng runtime.
- Tương thích ngược và dữ liệu cũ: nếu tái tổ chức package, cần đổi import ở toàn bộ file liên quan — rủi ro thấp nếu chỉ đổi package declaration, IDE refactor tool xử lý được.

**Kết quả đầu ra**

- Sơ đồ 3 tầng và sơ đồ luồng request tồn tại dưới dạng file, không chỉ trong đầu người làm.
- README nêu được ví dụ cụ thể "đổi ứng dụng, Core không đổi" bằng tên package/module thật, không nói chung.

**Phụ thuộc, lưu ý**: làm sau WP3-02/03/04 để sơ đồ phản ánh đúng kết quả thật.

## WP3-02: Workflow dùng chung (`workflow_case`/`workflow_event`)

- Mục tham chiếu: mục 8 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: có bảng lịch sử không bị ghi đè, có `assignedTo`, có API danh sách chờ, áp dụng được ngay cho luồng duyệt chẩn đoán hiện có.
- Ước lượng: L.

**Hiện trạng đã rà soát**

- `model/Diagnosis.java`: `approvalStatus` (enum), `decidedBy` (String, tên hiển thị), `decidedAt`, `rejectionReason` — tất cả là field phẳng trên entity, mỗi lần duyệt/từ chối ghi đè trực tiếp, không có bản ghi lịch sử nào trước đó.
  Không có cột `assignedTo` ở bất kỳ entity nào.
  Không có endpoint liệt kê "chẩn đoán đang chờ duyệt" dùng chung — mỗi nơi tự lọc theo `approvalStatus = PENDING` trong truy vấn riêng.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Duyệt/từ chối chẩn đoán | Ghi đè `approvalStatus`/`decidedBy`/`decidedAt` trực tiếp trên `Diagnosis`, không có lịch sử | Tạo một `WorkflowEvent` mới (không sửa event cũ), cập nhật `WorkflowCase.status`; `Diagnosis.approvalStatus` vẫn được cập nhật song song để không phá logic nghiệp vụ hiện có (khóa PHI, điều kiện thu hoạch) |
| Xem ai đang phụ trách một chẩn đoán | Không có | `WorkflowCase.assignedTo` (nullable, id kỹ sư), có thể tự nhận việc (`PATCH .../assign`) |
| Xem danh sách chẩn đoán đang chờ duyệt | Mỗi nơi tự viết query riêng | `GET /api/v1/workflow-cases?caseType=DIAGNOSIS_APPROVAL&status=PENDING` dùng chung cho mọi loại quy trình sau này |
| Xem lịch sử xử lý một chẩn đoán | Không có | `GET /api/v1/workflow-cases/{id}/events` trả timeline đầy đủ |

**Hướng làm**

- Entity `WorkflowCase`: `id`, `caseType` (String/enum mở, ví dụ `DIAGNOSIS_APPROVAL`), `entityType` + `entityId` (tham chiếu polymorphic tới `Diagnosis`/sau này `Parcel`/`CropSeason`), `status` (PENDING/APPROVED/REJECTED/CANCELLED — generic, không trùng tên với `ApprovalStatus` của `Diagnosis`), `createdBy`, `createdAt`, `assignedTo` (nullable), `updatedAt`, `version` (optimistic lock, theo pattern đã có từ P1).
- Entity `WorkflowEvent`: `id`, `workflowCaseId` (FK), `fromStatus`, `toStatus`, `actorUserId`, `actorDisplayName`, `reason` (nullable), `occurredAt`, `metadata` (JSONB, tuỳ chọn — theo Q1).
  Bảng này chỉ `INSERT`, không `UPDATE`/`DELETE` qua API nghiệp vụ.
- `DiagnosisService.approve(...)`/`reject(...)`: sau khi cập nhật `Diagnosis` như hiện tại, gọi `WorkflowService.recordTransition(...)` để tạo `WorkflowCase` (nếu chưa có, khi `createFromAi`) hoặc thêm `WorkflowEvent` (khi approve/reject).
  Không thay thế logic nghiệp vụ hiện có bằng workflow engine — chỉ thêm lớp ghi nhận song song, giảm rủi ro hồi quy (theo trade-off đã ghi ở phase 0 P3).
- `WorkflowController`: `GET /api/v1/workflow-cases` (phân trang, lọc `caseType`/`status`/`assignedTo`), `PATCH /api/v1/workflow-cases/{id}/assign`, `GET /api/v1/workflow-cases/{id}/events`.

**Phạm vi ảnh hưởng**

- Module và file chính: `model/WorkflowCase.java`, `model/WorkflowEvent.java` (mới), `repository/WorkflowCaseRepository.java`, `repository/WorkflowEventRepository.java` (mới), `service/WorkflowService.java` (mới), `controller/WorkflowController.java` (mới), `service/DiagnosisService.java` (thêm lời gọi, không đổi logic cũ).
- Endpoint, bảng DB, biến môi trường: 3 endpoint mới (`GET /workflow-cases`, `PATCH /workflow-cases/{id}/assign`, `GET /workflow-cases/{id}/events`); 2 bảng mới (`workflow_case`, `workflow_event`).
- Role và client bị ảnh hưởng: ENGINEER/ADMIN dùng danh sách chờ mới; không ảnh hưởng FARMER.
- Tương thích ngược và dữ liệu cũ: `Diagnosis` đã tồn tại trước phase này không có `WorkflowCase` tương ứng — chấp nhận "lịch sử trống" cho dữ liệu cũ, chỉ áp dụng đầy đủ cho chẩn đoán tạo mới từ đây.

**Kết quả đầu ra**

- Duyệt/từ chối một chẩn đoán mới tạo ra đúng 1 `WorkflowEvent`, không ghi đè event cũ.
- `GET /api/v1/workflow-cases?caseType=DIAGNOSIS_APPROVAL&status=PENDING` trả đúng danh sách khớp với số chẩn đoán `PENDING` thật.

**Phụ thuộc, lưu ý**: cần chốt Q1 (JSONB `metadata`) trước khi viết migration; P4 sẽ tái dùng nguyên bảng này cho `caseType = PARCEL_APPROVAL`/`CROP_SEASON_APPROVAL`, không tạo bảng mới.

## WP3-03: Cửa API — APISIX standalone

- Mục tham chiếu: mục 7, 9 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: một cửa chung kiểm JWT, rate limit, routing; không client nào gọi thẳng backend/AI/Keycloak qua cổng riêng nữa.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `frontend/nginx.conf` chỉ proxy `/api/` và `/uploads/` tới backend; không có rate limit, không kiểm JWT ở lớp này (JWT được backend tự kiểm).
- `mobile/.env.example`: `EXPO_PUBLIC_API_BASE_URL=http://192.168.1.10:8081`, `EXPO_PUBLIC_KEYCLOAK_URL=http://192.168.1.10:8180/realms/dxfarm` — mobile gọi thẳng cổng backend và Keycloak qua IP LAN, không qua lớp trung gian nào.
- `deploy/docker-compose.yml`: backend (8081), AI (8000, đã siết auth ở P1 nhưng vẫn publish cổng), Keycloak (8180), Postgres (5432), Superset (8088), Mattermost (8065) đều publish ra host.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Web gọi API | Qua nginx frontend → backend trực tiếp | Qua APISIX → backend (nginx frontend vẫn phục vụ static asset, không còn tự proxy `/api`) |
| Mobile gọi API/Keycloak | Thẳng IP:8081 và IP:8180 | Thẳng một hostname/IP:cổng APISIX duy nhất, APISIX route tới backend hoặc Keycloak theo path |
| Gọi vượt rate limit (upload ảnh, trigger AI) | Không giới hạn | APISIX trả 429 sau ngưỡng cấu hình |
| Gọi thẳng cổng AI (8000) từ máy ngoài compose network | Không còn khả năng từ P1 (cổng không publish) | Giữ nguyên, APISIX không route tới AI (AI chỉ gọi nội bộ từ backend) |
| Gọi thẳng cổng backend (8081)/Keycloak (8180) bỏ qua APISIX | Khả thi nếu còn publish cổng | Không publish cổng backend/Keycloak ra host nữa, chỉ APISIX publish |

**Hướng làm**

- Thêm service `apisix` vào `deploy/docker-compose.yml`, chế độ standalone (`apisix.yaml` tĩnh, không etcd), publish đúng 1 cổng ra host.
- Route: `/api/*` → backend:8081, `/auth/*` → keycloak:8080 (đổi tên path cho rõ, hoặc giữ `/realms/*` nếu không muốn đổi cấu hình Keycloak hiện có), `/uploads/*` hoặc storage path mới → theo kết quả WP3-04.
- Plugin `openid-connect` cho route `/api/*`: xác thực JWT bằng Keycloak realm (lớp phòng vệ thứ hai, không thay thế Spring Security của backend — hai lớp độc lập theo đúng nguyên tắc "chặn 2 tầng" của dự án).
- Plugin `limit-count`/`limit-req` cho route upload ảnh và các route trigger AI (qua backend), ngưỡng cụ thể cần đo theo tải thật, mặc định tạm một con số hợp lý (ví dụ 30 request/phút/IP) rồi điều chỉnh sau khi quan sát.
- Backend: không còn publish cổng 8081 ra host trong `docker-compose.yml`; Keycloak: không còn publish cổng 8180 ra host, chỉ APISIX publish.
- Web (`frontend/nginx.conf`): bỏ block `location /api/` và `/uploads/` (APISIX đảm nhiệm), frontend nginx chỉ còn phục vụ static asset.
- Mobile (`mobile/.env.example`): đổi `EXPO_PUBLIC_API_BASE_URL`/`EXPO_PUBLIC_KEYCLOAK_URL` sang hostname/cổng của APISIX.

**Phạm vi ảnh hưởng**

- Module và file chính: `deploy/docker-compose.yml`, `deploy/.env.example`, `deploy/apisix.yaml` (mới), `frontend/nginx.conf`, `mobile/.env.example`.
- Endpoint, bảng DB, biến môi trường: không có endpoint backend mới; biến `AI_HOST_PORT`, `BACKEND_HOST_PORT`, `KEYCLOAK_HOST_PORT` không còn publish ra host (hoặc đổi ý nghĩa thành chỉ debug nội bộ); thêm `APISIX_HOST_PORT`.
- Role và client bị ảnh hưởng: mọi client (web, mobile) đổi điểm gọi; không đổi hành vi nghiệp vụ.
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng dữ liệu; ảnh hưởng cấu hình deploy — ai đang có `.env` cũ với `BACKEND_HOST_PORT` phải đọc lại README để biết cổng mới cần gọi.

**Kết quả đầu ra**

- Gọi thẳng `IP:8081` hoặc `IP:8180` từ máy ngoài compose network thất bại (cổng không publish).
- Web và mobile hoạt động bình thường khi đổi sang gọi qua APISIX (test T3-xx).
- Gọi upload ảnh vượt ngưỡng rate limit nhận 429.

**Phụ thuộc, lưu ý**: không phụ thuộc WP3-02/WP3-04 về code, nhưng nên hoàn thiện route `/uploads`/storage cùng lúc với kết quả WP3-04 để không phải đổi route 2 lần.

## WP3-04: Storage service — MinIO và link có hạn

- Mục tham chiếu: mục 7, 9 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: ảnh thửa ruộng không còn phục vụ công khai không cần token; có metadata (ai upload, loại, nguồn) đi kèm.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `config/WebConfig.java`: `registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + location)` — phục vụ file tĩnh trực tiếp từ filesystem container, không qua logic nghiệp vụ nào.
- `security/SecurityConfig.java`: `/uploads/**` nằm trong `PUBLIC_GET_ENDPOINTS`, không cần token.
- `service/FileStorageService.java` (đã qua P1: validate ảnh thật + xoá EXIF): `store(...)` ghi file vào `uploadDir` local, trả về tên file (`imageRef`).
- `service/DiagnosisService.java:145`: `String imageUrl = publicBaseUrl + "/uploads/" + report.getImageRef();` — backend tự dựng URL công khai để gửi cho AI service và dùng trong thông báo Mattermost.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Lưu ảnh upload | Ghi file vào filesystem local container | Upload object vào bucket MinIO, kèm metadata (`uploadedBy`, `contentType`, `source`) |
| Xem ảnh từ web/mobile | Gọi thẳng `/uploads/{file}`, không cần token, không hạn | Backend trả presigned URL có hạn (ví dụ 15 phút) qua response DTO, client dùng URL đó để tải ảnh |
| AI service lấy ảnh để predict | Backend dựng URL `/uploads/...` công khai, AI fetch qua HTTP | Backend dựng presigned URL (hoặc gọi MinIO nội bộ qua compose network bằng access key riêng cho service), AI fetch bằng URL đó trong thời hạn đủ cho timeout 120s |
| Ảnh cũ đã lưu local trước phase này | - | Migrate sang MinIO theo Q2 (migrate ngay, một script một lần) |

**Hướng làm**

- Thêm service `minio` vào `deploy/docker-compose.yml`, không publish cổng console ra host (chỉ API port nội bộ, hoặc publish tạm cho admin thao tác qua VPN/SSH tunnel khi cần).
- `FileStorageService` đổi implementation: dùng MinIO Java SDK, `putObject` thay cho `Files.copy`; trả về object key (giữ format tương tự `imageRef` hiện tại để các chỗ gọi không đổi nhiều) thay vì đường dẫn filesystem.
- Thêm `StorageService.getPresignedUrl(objectKey, duration)` dùng chung cho mọi nơi cần hiển thị/truyền ảnh (response DTO cho web/mobile, `DiagnosisService` khi gọi AI, `MattermostMessageBuilder` khi dựng `image_url`).
- Script migrate: liệt kê toàn bộ file trong `uploadDir` cũ, `putObject` vào MinIO với key giữ nguyên tên file, chạy một lần trên môi trường đang có dữ liệu, xác nhận số lượng object khớp số file gốc trước khi xoá filesystem cũ.
- `SecurityConfig`: bỏ `/uploads/**` khỏi `PUBLIC_GET_ENDPOINTS` (không còn endpoint này ở backend nữa sau khi chuyển hẳn sang presigned URL của MinIO).

**Phạm vi ảnh hưởng**

- Module và file chính: `service/FileStorageService.java`, `service/StorageService.java` (mới hoặc đổi tên/mở rộng `FileStorageService`), `config/WebConfig.java` (bỏ resource handler `/uploads/**`), `security/SecurityConfig.java`, `service/DiagnosisService.java`, `service/MattermostMessageBuilder.java`, script migrate một lần (mới).
- Endpoint, bảng DB, biến môi trường: response DTO của báo cáo/chẩn đoán đổi field ảnh từ đường dẫn tĩnh sang presigned URL (có hạn, đổi mỗi lần gọi); biến `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` (mới, không default, theo nguyên tắc WP1-01).
- Role và client bị ảnh hưởng: web/mobile đổi cách hiển thị ảnh (dùng URL trả về trong response thay vì tự ghép `/uploads/` + tên file, nếu FE/mobile đang tự ghép thì phải sửa).
- Tương thích ngược và dữ liệu cũ: ảnh cũ migrate theo Q2; mọi link `/uploads/...` cũ đã chia sẻ (ví dụ trong thông báo Mattermost đã gửi trước đây) sẽ không còn truy cập được sau khi tắt resource handler — chấp nhận được vì đó là thông báo cũ, không phải dữ liệu nghiệp vụ.

**Kết quả đầu ra**

- Gọi `GET /uploads/{file}` (route cũ) sau khi dọn trả 404, không còn public.
- Ảnh hiển thị trên web/mobile vẫn load được bằng presigned URL mới.
- AI service vẫn predict được ảnh lấy qua presigned URL trong thời hạn cho phép.

**Phụ thuộc, lưu ý**: nên làm cùng lúc với phần route `/uploads` ở WP3-03 để APISIX trỏ đúng đích ngay từ đầu, không phải đổi route 2 lần.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `GET /api/v1/workflow-cases`, `PATCH .../assign`, `GET .../events` | Mới | Không (thêm mới) |
| Endpoint | `GET /uploads/{file}` | Bỏ hẳn, thay bằng presigned URL trong response DTO | Có |
| Response DTO | Báo cáo/chẩn đoán, field ảnh | Đổi từ đường dẫn tĩnh sang presigned URL có hạn | Có |
| Bảng DB | `workflow_case`, `workflow_event` | Bảng mới | Không |
| Biến môi trường | `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` | Mới, không default | Không |
| Biến môi trường | `BACKEND_HOST_PORT`, `KEYCLOAK_HOST_PORT` | Không còn publish ra host | Có — người dựng phải gọi qua APISIX |
| Biến môi trường | `APISIX_HOST_PORT` | Mới | Không |
| Cấu hình | `frontend/nginx.conf` | Bỏ `location /api/`, `/uploads/` | Có |
| Cấu hình | `mobile/.env.example` | Đổi `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_KEYCLOAK_URL` sang APISIX | Có |

## Dữ liệu, migration và rollback

- Migration: thêm bảng `workflow_case`, `workflow_event` qua Flyway (đã có từ P2).
- Dữ liệu cũ: ảnh local migrate sang MinIO theo script một lần (Q2); `Diagnosis` cũ không có `WorkflowCase` tương ứng, chấp nhận lịch sử trống cho dữ liệu trước phase này.
- Rollback: nếu APISIX gây lỗi nghiêm trọng lúc demo, có thể tạm publish lại cổng backend/Keycloak trực tiếp (đổi `.env`, không cần sửa code) trong khi sửa APISIX; MinIO rollback khó hơn (ảnh đã xoá khỏi filesystem) nên chỉ xoá filesystem cũ sau khi xác nhận migrate đủ số lượng.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D2 (kế thừa phase 0) | Tự làm `workflow_case`/`workflow_event` | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| D3 (kế thừa phase 0) | APISIX standalone | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| D4 (kế thừa phase 0) | MinIO | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | `workflow_event.metadata` dạng JSONB tuỳ chọn | P4/P9 thêm loại quy trình mới không cần migration riêng |
| Q2 | Migrate ảnh cũ ngay bằng một script | Dữ liệu quy mô demo, không cần chạy song song 2 nguồn |
