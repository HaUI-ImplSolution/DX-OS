# Checklist: Phase 3

Mã dòng: `WP3-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, INFRA, DATA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP3-02: Workflow dùng chung

Kết quả đầu ra: duyệt/từ chối chẩn đoán tạo đúng 1 `WorkflowEvent` mỗi lần, không ghi đè; danh sách chờ trả đúng dữ liệu.

**Spec và tài liệu**

- [ ] WP3-02-DOC-01 Swagger cho 3 endpoint mới (`workflow-cases`), `@Operation` bằng tiếng Anh.

**Backend**

- [ ] WP3-02-BE-01 Thêm `model/WorkflowCase.java`, `model/WorkflowEvent.java`.
- [ ] WP3-02-BE-02 Thêm `repository/WorkflowCaseRepository.java`, `repository/WorkflowEventRepository.java`.
- [ ] WP3-02-BE-03 Thêm `service/WorkflowService.java` (`createCase`, `recordTransition`, `assign`).
- [ ] WP3-02-BE-04 `DiagnosisService.createFromAi(...)`: gọi `WorkflowService.createCase(...)` sau khi tạo `Diagnosis`.
- [ ] WP3-02-BE-05 `DiagnosisService.approve(...)`/`reject(...)`: gọi `WorkflowService.recordTransition(...)`, giữ nguyên logic cập nhật `Diagnosis.approvalStatus` hiện có.
- [ ] WP3-02-BE-06 Thêm `controller/WorkflowController.java` (`GET /workflow-cases`, `PATCH .../assign`, `GET .../events`), `@PreAuthorize` cho ENGINEER/ADMIN.

**Data, seed, migration**

- [ ] WP3-02-DATA-01 Migration Flyway thêm bảng `workflow_case`, `workflow_event` (gồm cột `metadata JSONB` theo Q1).

**Test**

- [ ] WP3-02-TEST-01 Approve 1 chẩn đoán → đúng 1 `WorkflowEvent` mới, `WorkflowCase.status = APPROVED` (T3-01).
- [ ] WP3-02-TEST-02 Reject rồi approve lại (nếu luồng cho phép) → 2 event riêng biệt, không ghi đè nhau (T3-02).
- [ ] WP3-02-TEST-03 `GET /workflow-cases?caseType=DIAGNOSIS_APPROVAL&status=PENDING` khớp số chẩn đoán `PENDING` thật (T3-03).

## WP3-03: Cửa API — APISIX standalone

Kết quả đầu ra: mọi client gọi qua APISIX; cổng backend/AI/Keycloak không publish trực tiếp ra host.

**Spec và tài liệu**

- [ ] WP3-03-DOC-01 README: cập nhật hướng dẫn cổng mới cần gọi (APISIX), bỏ hướng dẫn gọi thẳng backend/Keycloak.

**Infra, deploy, CI**

- [ ] WP3-03-INFRA-01 Thêm service `apisix` vào `deploy/docker-compose.yml`, chế độ standalone.
- [ ] WP3-03-INFRA-02 Viết `deploy/apisix.yaml`: route `/api/*` → backend, `/auth/*` (hoặc giữ path Keycloak hiện có) → keycloak, route ảnh/storage → theo WP3-04.
- [ ] WP3-03-INFRA-03 Gắn plugin `openid-connect` cho route `/api/*`.
- [ ] WP3-03-INFRA-04 Gắn plugin `limit-count`/`limit-req` cho route upload ảnh và route trigger AI.
- [ ] WP3-03-INFRA-05 Bỏ publish cổng backend (8081), Keycloak (8180) ra host trong `docker-compose.yml`.
- [ ] WP3-03-INFRA-06 Thêm `APISIX_HOST_PORT` vào `.env.example`.

**Frontend (web)**

- [ ] WP3-03-FE-01 `frontend/nginx.conf`: bỏ `location /api/`, `/uploads/`, chỉ phục vụ static asset.

**Mobile**

- [ ] WP3-03-MB-01 `mobile/.env.example`: đổi `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_KEYCLOAK_URL` sang APISIX.

**Test**

- [ ] WP3-03-TEST-01 Gọi thẳng `IP:8081`/`IP:8180` từ máy ngoài compose network → thất bại (T3-04).
- [ ] WP3-03-TEST-02 Web đăng nhập và gọi API nghiệp vụ qua APISIX → hoạt động bình thường (T3-05, hồi quy).
- [ ] WP3-03-TEST-03 Mobile đăng nhập và gọi API qua APISIX → hoạt động bình thường (T3-06, hồi quy).
- [ ] WP3-03-TEST-04 Gọi upload ảnh vượt ngưỡng rate limit → 429 (T3-07).

## WP3-04: Storage service — MinIO và link có hạn

Kết quả đầu ra: `/uploads/{file}` cũ không còn public; ảnh hiển thị qua presigned URL; AI vẫn predict được.

**Spec và tài liệu**

- [ ] WP3-04-DOC-01 Cập nhật Swagger: field ảnh trong response đổi từ đường dẫn tĩnh sang presigned URL.

**Backend**

- [ ] WP3-04-BE-01 Thêm `service/StorageService.java` (MinIO SDK): `putObject`, `getPresignedUrl`.
- [ ] WP3-04-BE-02 `FileStorageService.store(...)`: đổi sang gọi `StorageService.putObject(...)` thay cho `Files.copy`.
- [ ] WP3-04-BE-03 `DiagnosisService`: đổi chỗ dựng `imageUrl` từ `publicBaseUrl + "/uploads/" + imageRef` sang `storageService.getPresignedUrl(imageRef, duration)`.
- [ ] WP3-04-BE-04 `MattermostMessageBuilder`: đổi nguồn `image_url` sang presigned URL.
- [ ] WP3-04-BE-05 `config/WebConfig.java`: bỏ `addResourceHandler("/uploads/**")`.
- [ ] WP3-04-BE-06 `security/SecurityConfig.java`: bỏ `/uploads/**` khỏi `PUBLIC_GET_ENDPOINTS`.

**Infra, deploy, CI**

- [ ] WP3-04-INFRA-01 Thêm service `minio` vào `deploy/docker-compose.yml`, không publish console ra host.
- [ ] WP3-04-INFRA-02 Thêm `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` vào `.env.example` (không default).

**Data, seed, migration**

- [ ] WP3-04-DATA-01 Viết script migrate: liệt kê file trong `uploadDir` cũ, `putObject` vào MinIO giữ nguyên key.
- [ ] WP3-04-DATA-02 Chạy script trên seed demo, xác nhận số object khớp số file gốc trước khi xoá filesystem cũ.

**Test**

- [ ] WP3-04-TEST-01 `GET /uploads/{file}` sau khi dọn → 404 (T3-08).
- [ ] WP3-04-TEST-02 Web/mobile hiển thị ảnh bằng presigned URL mới → load được (T3-09).
- [ ] WP3-04-TEST-03 AI predict ảnh lấy qua presigned URL trong thời hạn cho phép → thành công (T3-10).

## WP3-01: Ranh giới Core và DX-Farm, sơ đồ 3 tầng

Kết quả đầu ra: sơ đồ 3 tầng và sơ đồ luồng request tồn tại dưới dạng file; README nêu ví dụ cụ thể.

**Tài liệu**

- [ ] WP3-01-DOC-01 Vẽ sơ đồ 3 tầng (Core — Identity/Workflow/Gateway/Storage, H-P-D-I, App — DX-Farm), lưu trong `development/docs/`.
- [ ] WP3-01-DOC-02 Vẽ sơ đồ luồng request (người dùng → APISIX → backend/Keycloak/MinIO → kết quả).
- [ ] WP3-01-DOC-03 Cập nhật `README.md` mục kiến trúc, trỏ tới 2 sơ đồ trên, nêu ví dụ package/module cụ thể.

**Backend** (chỉ nếu chọn tái tổ chức package ở `01_PLAN.md`)

- [ ] WP3-01-BE-01 Đổi package `Household`, `WorkflowCase`/`WorkflowEvent`, `StorageService` sang nhóm `core.*` (nếu đã chốt tái tổ chức ở `04_TRADEOFFS_RISKS.md`).

## Đóng phase

- [ ] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, bằng tiếng Anh.
- [ ] Swagger khớp code.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
