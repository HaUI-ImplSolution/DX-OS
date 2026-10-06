# Checklist: Phase 1

Mã dòng: `WP1-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, AI, DATA, INFRA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP1-01: Loại secret hard-code và mật khẩu mặc định

Kết quả đầu ra: quét secret sạch trên working tree và lịch sử commit; thiếu biến `*_PASSWORD`/`*_SECRET` thì compose dừng với thông báo rõ.

**Spec và tài liệu**

- [x] WP1-01-DOC-01 Ghi vào README bảng tài khoản demo (farmer123/engineer123/admin123) kèm cảnh báo "chỉ dùng demo/chấm thi".

**Backend**

- [x] WP1-01-BE-01 Rà `application.yaml`, bỏ default kiểu `keycloak_demo_only`, bắt buộc truyền qua biến môi trường.

**Infra, deploy, CI**

- [x] WP1-01-INFRA-01 Sửa `deploy/.env.example`: `AI_VECTOR_DB_READONLY_PASSWORD`, `AI_VECTOR_DB_INGEST_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `SUPERSET_READONLY_DB_PASSWORD`, `SUPERSET_OIDC_CLIENT_SECRET`, `MATTERMOST_DB_PASSWORD` → để trống.
- [x] WP1-01-INFRA-02 Đối chiếu từng biến `.env.example` với `docker-compose.yml`, đảm bảo biến secret nào cũng có `:?` (báo tên biến khi thiếu).
- [x] WP1-01-INFRA-03 Cài và chạy thử `gitleaks` (hoặc tool đã chốt Q3) local, rà cả `git log` — xử lý/allowlist từng phát hiện.

**Test**

- [x] WP1-01-TEST-01 Thử `docker compose up` thiếu một biến `*_PASSWORD` bất kỳ, xác nhận compose dừng và báo đúng tên biến (T1-01).

## WP1-02: Khóa PHI và ràng buộc nghiệp vụ poka-yoke

Kết quả đầu ra: 6 tình huống ở `01_PLAN.md` WP1-02 đều có hành vi đúng và có test.

**Spec và tài liệu**

- [x] WP1-02-DOC-01 Swagger: khai báo rõ mã lỗi 422/409/400 mới cho 3 endpoint liên quan, `@Operation` bằng tiếng Anh.

**Backend**

- [x] WP1-02-BE-01 Thêm `@Bean Clock clock()` cố định `Asia/Ho_Chi_Minh`; inject vào `CropSeasonService`, `DiagnosisService` qua constructor.
- [x] WP1-02-BE-02 Thay toàn bộ `LocalDate.now()` trong 2 service trên bằng `LocalDate.now(clock)`.
- [x] WP1-02-BE-03 Validator `appliedAt` trong `DiagnosisService.apply(...)`: từ chối tương lai hoặc lùi quá N ngày (N theo Q1), ném exception 422 có thông báo rõ.
- [x] WP1-02-BE-04 `DiseaseReportService.create(...)`: chặn tạo báo cáo khi `CropSeason.status == HARVESTED`.
- [x] WP1-02-BE-05 `CropSeasonService.create(...)`: chặn tạo vụ `ACTIVE` thứ hai trên cùng thửa (check trong transaction) và chặn `expectedHarvestDate < startDate`.
- [x] WP1-02-BE-06 Thêm `@Version` vào `Diagnosis`, `CropSeason`; `GlobalExceptionHandler` map `ObjectOptimisticLockingFailureException` → 409.
- [x] WP1-02-BE-07 `backend/DX-Farm/Dockerfile`: thêm `ENV TZ=Asia/Ho_Chi_Minh`.

**Data, seed, migration**

- [x] WP1-02-DATA-01 Rà seed/dữ liệu demo hiện có, xử lý vụ mùa vi phạm "1 ACTIVE/thửa" theo Q2 trước khi bật constraint DB.
- [x] WP1-02-DATA-02 Migration thêm cột `version` (default 0) cho `diagnosis`, `crop_season`; thêm unique constraint một phần cho `crop_season`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WP1-02-TEST-01 Case `appliedAt` tương lai/lùi quá hạn → 422 (T1-02, T1-03).
- [x] WP1-02-TEST-02 Case tạo báo cáo trên vụ `HARVESTED` → 422 (T1-04).
- [x] WP1-02-TEST-03 Case tạo 2 vụ `ACTIVE`/thửa, `expectedHarvestDate < startDate` → 409/400 (T1-05, T1-06).
- [x] WP1-02-TEST-04 Case 2 request `approve` đồng thời trên cùng `Diagnosis` → 1 thành công, 1 nhận 409 (T1-07).

## WP1-03: Xác thực AI service và kiểm tra upload ảnh thật

Kết quả đầu ra: mọi endpoint AI đòi `X-Service-Api-Key`; ảnh giả hoặc có EXIF bị chặn/làm sạch.

**Spec và tài liệu**

- [x] WP1-03-DOC-01 Cập nhật `ai/app/cv/README.md` mục xác thực, ghi rõ `/predict` và `/knowledge/retrieve` nay cũng cần key.

**Backend**

- [x] WP1-03-BE-01 `FileStorageService.store(...)`: đọc lại bytes bằng thư viện ảnh (decode thật), từ chối nếu không phải ảnh hợp lệ.
- [x] WP1-03-BE-02 Re-encode ảnh trước khi ghi file để loại bỏ EXIF (không chỉ đổi tên/đuôi).

**AI**

- [x] WP1-03-AI-01 Gắn `Depends(authorize_backend)` cho `/disease/predict` và `/disease/knowledge/retrieve` (giữ `/health`, `/class-contract` công khai).

**Infra, deploy, CI**

- [x] WP1-03-INFRA-01 Bỏ publish cổng AI ra host trong `deploy/docker-compose.yml` (hoặc đổi thành tùy chọn debug có ghi chú).

**Test**

- [x] WP1-03-TEST-01 Gọi `/disease/predict`, `/disease/knowledge/retrieve` không có key → 401 (T1-08, T1-09).
- [x] WP1-03-TEST-02 Upload file đổi đuôi giả ảnh → 400 (T1-10).
- [x] WP1-03-TEST-03 Upload ảnh thật có EXIF GPS → đọc lại file đã lưu, xác nhận không còn EXIF (T1-11).

## WP1-04: Script reset, mở rộng CI, log theo profile

Kết quả đầu ra: CI chạy test thật cho backend + check cho frontend/mobile/AI; dựng lại từ clone sạch bằng một script.

**Spec và tài liệu**

- [x] WP1-04-DOC-01 README: thêm hướng dẫn chạy script reset và ý nghĩa (phá hủy dữ liệu local).

**Infra, deploy, CI**

- [x] WP1-04-INFRA-01 `.github/workflows/ci.yml`: bỏ `-DskipTests` ở job backend.
- [x] WP1-04-INFRA-02 Thêm job CI cho frontend (`tsc --noEmit`), mobile (`tsc --noEmit`/`expo export`), AI (`pytest`), lọc theo `paths` tương ứng.
- [x] WP1-04-INFRA-03 Thêm step `docker compose -f deploy/docker-compose.yml config` và step quét secret vào CI.
- [x] WP1-04-INFRA-04 Viết script reset (`deploy/reset.sh` hoặc tương đương): dừng container, xóa `deploy/data/*` qua container tạm (tránh lỗi quyền uid), `docker compose up -d --build`, chờ mọi service sẵn sàng.
- [x] WP1-04-INFRA-05 Tách `logging.level` theo profile hoặc biến môi trường, mặc định INFO khi không phải `dev`.

**Test**

- [x] WP1-04-TEST-01 Chạy script reset 2 lần liên tiếp trên máy sạch, xác nhận `docker compose ps` toàn bộ `healthy` cả hai lần (T1-12).
- [ ] WP1-04-TEST-02 Xác nhận CI job mới (frontend/mobile/AI) chạy và xanh trên PR thử (T1-13).

## Đóng phase

- [x] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn milestone.
- [x] `.env.example`, seed, migration chạy được trên clone sạch.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] Dọn dữ liệu và ảnh test không còn dùng.
- [x] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
