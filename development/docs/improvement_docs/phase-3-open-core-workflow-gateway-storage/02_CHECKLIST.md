# Checklist: Phase 3

Mã dòng: `WP3-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, INFRA, DATA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP3-02: Workflow dùng chung

Kết quả đầu ra: duyệt/từ chối chẩn đoán tạo đúng 1 `WorkflowEvent` mỗi lần, không ghi đè; danh sách chờ trả đúng dữ liệu.

**Spec và tài liệu**

- [x] WP3-02-DOC-01 Swagger cho 3 endpoint mới (`workflow-cases`), `@Operation` bằng tiếng Anh.

**Backend**

- [x] WP3-02-BE-01 Thêm `model/WorkflowCase.java`, `model/WorkflowEvent.java`.
- [x] WP3-02-BE-02 Thêm `repository/WorkflowCaseRepository.java`, `repository/WorkflowEventRepository.java`.
- [x] WP3-02-BE-03 Thêm `service/WorkflowService.java` (`createCase`, `recordTransition`, `assign`).
- [x] WP3-02-BE-04 `DiagnosisService.createFromAi(...)`: gọi `WorkflowService.createCase(...)` sau khi tạo `Diagnosis`.
- [x] WP3-02-BE-05 `DiagnosisService.approve(...)`/`reject(...)`: gọi `WorkflowService.recordTransition(...)`, giữ nguyên logic cập nhật `Diagnosis.approvalStatus` hiện có.
- [x] WP3-02-BE-06 Thêm `controller/WorkflowController.java` (`GET /workflow-cases`, `PATCH .../assign`, `GET .../events`), `@PreAuthorize` cho ENGINEER/ADMIN.

**Data, seed, migration**

- [x] WP3-02-DATA-01 Migration Flyway thêm bảng `workflow_case`, `workflow_event` (gồm cột `metadata JSONB` theo Q1).

**Test**

- [x] WP3-02-TEST-01 Approve 1 chẩn đoán → đúng 1 `WorkflowEvent` mới, `WorkflowCase.status = APPROVED` (T3-01).
- [x] WP3-02-TEST-02 Reject rồi approve lại (nếu luồng cho phép) → 2 event riêng biệt, không ghi đè nhau (T3-02).
- [x] WP3-02-TEST-03 `GET /workflow-cases?caseType=DIAGNOSIS_APPROVAL&status=PENDING` khớp số chẩn đoán `PENDING` thật (T3-03).

## WP3-03: Cửa API — APISIX standalone

Kết quả đầu ra: mọi client gọi qua APISIX; cổng backend/AI/Keycloak không publish trực tiếp ra host.

**Spec và tài liệu**

- [x] WP3-03-DOC-01 README: cập nhật hướng dẫn cổng mới cần gọi (APISIX), bỏ hướng dẫn gọi thẳng backend/Keycloak.

**Infra, deploy, CI**

- [x] WP3-03-INFRA-01 Thêm service `apisix` vào `deploy/docker-compose.yml`, chế độ standalone.
- [x] WP3-03-INFRA-02 Viết `deploy/apisix/apisix.yaml` (kèm `config.yaml`): route `/api/*` → backend, `/realms/*`, `/resources/*`, `/admin/*` → keycloak (giữ path Keycloak hiện có), `/dxfarm-uploads/*` → minio, Swagger → backend, `/*` → web.
- [x] WP3-03-INFRA-03 Gắn plugin `openid-connect` cho route `/api/*`.
- [x] WP3-03-INFRA-04 Gắn plugin `limit-count`/`limit-req` cho route upload ảnh và route trigger AI.
- [x] WP3-03-INFRA-05 Bỏ publish cổng backend (8081), Keycloak (8180) ra host trong `docker-compose.yml`.
- [x] WP3-03-INFRA-06 Thêm `APISIX_HOST_PORT` vào `.env.example` (kèm `GATEWAY_PUBLIC_URL`).

**Frontend (web)**

- [x] WP3-03-FE-01 `frontend/nginx.conf`: bỏ proxy `/api/`, `/uploads/` trả `404`, chỉ phục vụ static asset; web phục vụ qua route `/*` của gateway nên cùng origin với API và Keycloak.

**Mobile**

- [x] WP3-03-MB-01 `mobile/.env.example`: đổi `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_KEYCLOAK_URL` sang APISIX.

**Test**

- [x] WP3-03-TEST-01 Gọi thẳng `IP:8081`/`IP:8180` từ máy ngoài compose network → thất bại (T3-04).
- [x] WP3-03-TEST-02 Web đăng nhập và gọi API nghiệp vụ qua APISIX → hoạt động bình thường (T3-05, hồi quy).
- [x] WP3-03-TEST-03 Mobile đăng nhập và gọi API qua APISIX → hoạt động bình thường (T3-06, hồi quy).
  Đã chạy app trên máy thật qua gateway (xác nhận của người dùng).
- [x] WP3-03-TEST-04 Gọi upload ảnh vượt ngưỡng rate limit → 429 (T3-07).

## WP3-04: Storage service — MinIO và link có hạn

Kết quả đầu ra: `/uploads/{file}` cũ không còn public; ảnh hiển thị qua presigned URL; AI vẫn predict được.

**Spec và tài liệu**

- [x] WP3-04-DOC-01 Cập nhật Swagger: field ảnh trong response đổi từ đường dẫn tĩnh sang presigned URL.

**Backend**

- [x] WP3-04-BE-01 Thêm `service/StorageService.java` (MinIO SDK): `putObject`, `getPresignedUrl`.
- [x] WP3-04-BE-02 `FileStorageService.store(...)`: đổi sang gọi `StorageService.putObject(...)` thay cho `Files.copy`.
- [x] WP3-04-BE-03 `DiagnosisService`: đổi chỗ dựng `imageUrl` từ `publicBaseUrl + "/uploads/" + imageRef` sang `storageService.getPresignedUrl(imageRef, duration)`.
- [x] WP3-04-BE-04 `MattermostMessageBuilder`: đổi nguồn `image_url` sang presigned URL.
- [x] WP3-04-BE-05 `config/WebConfig.java`: bỏ `addResourceHandler("/uploads/**")`.
- [x] WP3-04-BE-06 `security/SecurityConfig.java`: bỏ `/uploads/**` khỏi `PUBLIC_GET_ENDPOINTS`.

**Infra, deploy, CI**

- [x] WP3-04-INFRA-01 Thêm service `minio` vào `deploy/docker-compose.yml`, không publish cổng nào ra host.
  Image build từ source (`minio/Dockerfile`) vì image chính thức không còn phát hành.
- [x] WP3-04-INFRA-02 Thêm `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` vào `.env.example` (không default cho khóa và secret).

**Data, seed, migration**

- [x] WP3-04-DATA-01 Viết script migrate: liệt kê file trong `uploadDir` cũ, `putObject` vào MinIO giữ nguyên key.
- [x] WP3-04-DATA-02 Chạy script trên thư mục ảnh mẫu, xác nhận từng file đã có trong bucket (thư mục `deploy/data/uploads` của máy này trống, không có ảnh thật để chuyển); script không xoá filesystem cũ.

**Test**

- [x] WP3-04-TEST-01 `GET /uploads/{file}` sau khi dọn → 404 (T3-08).
- [x] WP3-04-TEST-02 Web/mobile hiển thị ảnh bằng presigned URL mới → load được (T3-09).
- [x] WP3-04-TEST-03 AI predict ảnh lấy qua presigned URL trong thời hạn cho phép → thành công (T3-10).

## WP3-01: Ranh giới Core và DX-Farm, sơ đồ 3 tầng

Kết quả đầu ra: sơ đồ 3 tầng và sơ đồ luồng request tồn tại dưới dạng file; README nêu ví dụ cụ thể.

**Tài liệu**

- [x] WP3-01-DOC-01 Vẽ sơ đồ 3 tầng (Core — Identity/Workflow/Gateway/Storage, H-P-D-I, App — DX-Farm), lưu trong `development/docs/`.
- [x] WP3-01-DOC-02 Vẽ sơ đồ luồng request (người dùng → APISIX → backend/Keycloak/MinIO → kết quả).
- [x] WP3-01-DOC-03 Cập nhật `README.md` mục kiến trúc, trỏ tới 2 sơ đồ trên, nêu ví dụ package/module cụ thể.

**Backend** (chỉ nếu chọn tái tổ chức package ở `01_PLAN.md`)

- [x] WP3-01-BE-01 Đổi package `Household`, `WorkflowCase`/`WorkflowEvent`, `StorageService` sang nhóm `core.*` (chốt giữ một module Maven theo TO1): `core.identity`, `core.workflow`, `core.storage`.

## Đóng phase

- [x] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, bằng tiếng Anh.
- [x] Swagger khớp code.
- [x] `.env.example`, seed, migration chạy được trên clone sạch.
  Đã chạy `deploy/reset.sh` từ thư mục dữ liệu trống: mọi service healthy, đăng nhập web, Superset và MinIO Console đều hoạt động.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng (xem `03_TEST_PLAN.md`).
- [x] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
