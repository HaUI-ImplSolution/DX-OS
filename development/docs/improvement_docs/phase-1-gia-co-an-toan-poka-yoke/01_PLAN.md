# Plan thực hiện: Phase 1

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP1-01 (secret) và WP1-04 (CI/script/log) độc lập, làm song song được ngay.
WP1-02 (khóa PHI) và WP1-03 (AI auth + upload) độc lập với nhau, có thể song song, nhưng WP1-02 nên làm trước WP1-04's bước "CI chạy test thật" vì CI cần có test để chạy.
Thứ tự đề xuất: WP1-01 → WP1-02 ∥ WP1-03 → WP1-04 (CI bật test ở bước cuối khi đã có test từ WP1-02/WP1-03).

## WP1-01: Loại secret hard-code và mật khẩu mặc định

- Mục tham chiếu: mục 9 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: không còn secret/mật khẩu thật nào nằm trong file được commit; thiếu biến môi trường bắt buộc thì dừng khởi động thay vì chạy với giá trị yếu.
- Ước lượng: S.

**Hiện trạng đã rà soát**

- `sso/realm-dxfarm.json`: mật khẩu demo `farmer123`/`engineer123`/`admin123` (plaintext trong JSON) và `"secret": "dxfarm_superset_demo_secret"` của Superset client.
- `deploy/.env.example`: có giá trị mặc định cho `AI_VECTOR_DB_READONLY_PASSWORD=AiVectorRo123`, `AI_VECTOR_DB_INGEST_PASSWORD=AiVectorIngest123`, `KEYCLOAK_DB_PASSWORD=Keycloak123`, `SUPERSET_READONLY_DB_PASSWORD=Superset123`, `SUPERSET_OIDC_CLIENT_SECRET=dxfarm_superset_demo_secret`, `MATTERMOST_DB_PASSWORD=Mattermost123`.
  Các biến còn lại (`POSTGRES_PASSWORD`, `KEYCLOAK_ADMIN_PASSWORD`, `SUPERSET_SECRET_KEY`, `SUPERSET_ADMIN_PASSWORD`, `MATTERMOST_ADMIN_PASSWORD`) đã để trống, đi cùng `:?` trong `deploy/docker-compose.yml` (compose tự dừng nếu thiếu) — mẫu đúng cần nhân rộng cho các biến còn lại.
- `backend/DX-Farm/src/main/resources/application.yaml`: có giá trị mặc định kiểu `keycloak_demo_only` ở một số chỗ (cần rà lại từng dòng khi code).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Thiếu biến secret khi `docker compose up` | Container chạy với giá trị mặc định yếu hoặc không chạy tùy biến | Toàn bộ biến có nghĩa "mật khẩu/secret" dùng `${VAR:?lỗi rõ tên biến}`, compose dừng và báo tên biến thiếu |
| `application.yaml` thiếu config secret | Dùng default `keycloak_demo_only` | Không còn default, bắt buộc truyền qua biến môi trường, Spring fail-fast nếu thiếu |
| Đọc file realm Keycloak | Mật khẩu demo dạng plaintext trong file JSON track bởi git | Vẫn giữ 3 tài khoản demo (farmer123/engineer123/admin123) nhưng ghi rõ "chỉ dùng demo" trong README, review lại có nên generate tại thời điểm `--import-realm` thay vì hard-code (xem câu hỏi mở) |

**Hướng làm**

- Soát toàn bộ `deploy/.env.example` và `application.yaml`, chuyển các giá trị có sẵn (`AiVectorRo123`...) thành chuỗi rỗng, đồng bộ với `:?` đã có trong `docker-compose.yml`.
- Thêm `gitleaks` (hoặc công cụ đã chốt ở câu hỏi Q3) chạy local (`gitleaks detect --source .`) và trong CI (WP1-04), rà cả lịch sử commit một lần trước khi repo chuyển public (P8).
- README ghi rõ bảng tài khoản demo là "chỉ phục vụ chấm thi/demo, đổi mật khẩu trước khi dùng thật".

**Phạm vi ảnh hưởng**

- Module và file chính: `deploy/.env.example`, `deploy/docker-compose.yml`, `backend/DX-Farm/src/main/resources/application.yaml`, `sso/realm-dxfarm.json`, `sso/README.md`.
- Endpoint, bảng DB, biến môi trường: không có endpoint mới; toàn bộ biến `*_PASSWORD`, `*_SECRET` trong `.env.example` đổi default.
- Role và client bị ảnh hưởng: không ảnh hưởng runtime của farmer/engineer/admin, chỉ ảnh hưởng người dựng hệ thống (phải tự điền `.env` đầy đủ).
- Tương thích ngược và dữ liệu cũ: ai đang có `.env` cũ với giá trị mặc định vẫn chạy được (giá trị cũ không bị thu hồi), chỉ `.env.example` mới (clone mới) đổi.

**Kết quả đầu ra**

- `gitleaks` (hoặc tool đã chọn) chạy trên working tree và trên lịch sử commit, không báo lỗi secret thật (cho qua bằng allowlist nếu là false positive, ghi lý do).
- Thiếu bất kỳ biến `*_PASSWORD`/`*_SECRET` nào mà `docker compose up` vẫn chạy coi như WP chưa xong.

**Phụ thuộc, lưu ý**: không phụ thuộc WP khác; nên làm sớm nhất vì ảnh hưởng tới README/CHANGELOG của WP1-04.

## WP1-02: Khóa PHI và ràng buộc nghiệp vụ poka-yoke

- Mục tham chiếu: mục 10 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: khóa PHI (thời gian cấm thu hoạch sau khi phun thuốc) không thể bị vô hiệu hoặc kéo dài sai bằng cách khai ngày phun tùy ý; vụ mùa có ràng buộc đúng vòng đời.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `DiagnosisService.java:228`: `LocalDate appliedAt = request.appliedAt() != null ? request.appliedAt() : LocalDate.now();` — nhận `appliedAt` từ `DiagnosisApplyRequest` không có validation nào (`dto/DiagnosisApplyRequest.java` không có `@PastOrPresent`/`@Past`), có thể lùi vô hạn hoặc ở tương lai.
- `CropSeasonService.java:79,111,115`: dùng `LocalDate.now()` trực tiếp (không `Clock`/`ZoneId`), trong khi `MattermostMessageBuilder.java:29` lại dùng `ZoneId.of("Asia/Ho_Chi_Minh")` — hai nơi tính ngày khác múi giờ nhau.
  `backend/DX-Farm/Dockerfile` không set `ENV TZ`, container chạy giờ hệ thống mặc định (thường UTC).
- Không tìm thấy kiểm tra trạng thái `CropSeason` khi tạo `DiseaseReport` mới (service tạo báo cáo không chặn vụ đã `HARVESTED`).
- Không tìm thấy ràng buộc DB hoặc service chặn 2 `CropSeason` cùng `ACTIVE` trên 1 `Parcel`, cũng không có check `expectedHarvestDate >= startDate`.
- `grep "@Version"` trên toàn bộ `model/*.java`: không có kết quả — chưa có khóa lạc quan ở `Diagnosis` hay `CropSeason`, trong khi cả hai đều có trường trạng thái (`ApprovalStatus`, `CropSeasonStatus`) bị nhiều thao tác ghi đè (approve/reject/apply, harvest).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| `POST .../diagnosis/{id}/apply` với `appliedAt` ở tương lai hoặc lùi quá N ngày | Chấp nhận, `harvestLockedUntil` sai lệch | 422 `PokaYokeViolationException` kiểu mới, không lưu |
| Tính `harvestLockedUntil`, hạn cách ly | `LocalDate.now()` theo múi giờ container (có thể UTC) | Dùng `Clock` bean cố định `Asia/Ho_Chi_Minh`, inject thay vì gọi `LocalDate.now()` trực tiếp |
| Tạo `DiseaseReport` trên `CropSeason` đã `HARVESTED` | Chấp nhận | 422, thông báo "vụ mùa đã thu hoạch" |
| Tạo `CropSeason` thứ hai `ACTIVE` trên cùng `Parcel` đang có vụ `ACTIVE` | Chấp nhận | 409, danh sách unique `(parcel_id)` lọc theo status `ACTIVE` (partial index) hoặc check ở service trong transaction |
| `expectedHarvestDate < startDate` lúc tạo vụ | Chấp nhận | 400 |
| Hai người cùng bấm `approve`/`reject`/`apply`/thu hoạch đồng thời | Request sau ghi đè request trước âm thầm | `@Version` trên `Diagnosis`, `CropSeason`; request sau nhận `409` (`OptimisticLockException` map sang response rõ ràng) |

**Hướng làm**

- Thêm bean `Clock` (`@Bean Clock clock() { return Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")); }`), thay mọi `LocalDate.now()` trong `CropSeasonService`, `DiagnosisService` bằng `LocalDate.now(clock)`; inject `Clock` qua constructor để test control được thời gian.
- Thêm validator cho `appliedAt` trong `DiagnosisService.apply(...)`: so với `LocalDate.now(clock)`, từ chối nếu `isAfter(today)` hoặc `isBefore(today.minusDays(N))` (N theo Q1 ở `00_OVERVIEW.md`).
- `DiseaseReportService.create(...)`: thêm check `cropSeason.getStatus() != CropSeasonStatus.HARVESTED` trước khi lưu.
- `CropSeasonService.create(...)`: thêm check còn vụ `ACTIVE` khác trên cùng thửa (trong transaction, kèm unique constraint DB làm lớp chặn thứ hai chống race) và `expectedHarvestDate.isBefore(startDate)`.
- Thêm cột `version` (`@Version private Long version;`) vào `Diagnosis` và `CropSeason`; `GlobalExceptionHandler` bắt `ObjectOptimisticLockingFailureException` trả `409` với thông báo "dữ liệu đã bị người khác cập nhật, tải lại".
- Dockerfile backend thêm `ENV TZ=Asia/Ho_Chi_Minh` làm lớp phòng vệ thứ hai (đồng bộ với `Clock` bean).

**Phạm vi ảnh hưởng**

- Module và file chính: `service/CropSeasonService.java`, `service/DiagnosisService.java`, `service/DiseaseReportService.java`, `model/CropSeason.java`, `model/Diagnosis.java`, `exception/PokaYokeViolationException.java` (mở rộng hoặc thêm loại mới), `exception/GlobalExceptionHandler.java`, `backend/DX-Farm/Dockerfile`, config `@Bean Clock`.
- Endpoint, bảng DB, biến môi trường: `POST .../diagnosis/{id}/apply`, `POST .../crop-seasons`, `POST .../disease-reports` đổi mã lỗi trong một số trường hợp; bảng `crop_season` thêm unique constraint (partial, `WHERE status = 'ACTIVE'`) và cột `version`; bảng `diagnosis` thêm cột `version`.
- Role và client bị ảnh hưởng: farmer (tạo vụ/báo cáo), engineer (apply, approve/reject) — mobile và web đều cần xử lý mã lỗi mới ở FE (tối thiểu hiển thị message có sẵn từ backend, không cần UI đẹp ở phase này).
- Tương thích ngược và dữ liệu cũ: seed/dữ liệu demo đang có nhiều vụ `ACTIVE` trên cùng thửa hoặc `appliedAt` ngoài khoảng hợp lệ phải dọn trước khi bật ràng buộc DB (xem "Dữ liệu, migration và rollback").

**Kết quả đầu ra**

- 6 case ở bảng "Hành vi thay đổi" đều có test (theo khung D6 ở phase 0) và pass.
- Seed demo dựng từ clone sạch không vi phạm ràng buộc mới.

**Phụ thuộc, lưu ý**: cần chốt Q1 (N ngày) và Q2 (xử lý dữ liệu cũ) ở `00_OVERVIEW.md` trước khi viết migration thật.

## WP1-03: Xác thực AI service và kiểm tra upload ảnh thật

- Mục tham chiếu: mục 9 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: không endpoint AI nào gọi được mà thiếu service API key; ảnh upload được xác minh nội dung thật, không giữ EXIF có thể lộ GPS.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `ai/app/api/router.py`: `authorize_backend` (kiểm `X-Service-Api-Key` bằng `hmac.compare_digest`) chỉ gắn vào `/inference/diagnose` qua `dependencies=[Depends(authorize_backend)]`.
  `/disease/predict` (dòng 88) và `/disease/knowledge/retrieve` (dòng 97) không có dependency này, gọi trực tiếp được không cần key.
- `deploy/docker-compose.yml`: AI service publish `"${AI_HOST_PORT:?}:8000"` ra host — client bất kỳ trong mạng LAN có thể gọi thẳng, không qua backend.
- `backend/DX-Farm/.../service/FileStorageService.java`: `store(MultipartFile file)` chỉ kiểm `file.getContentType()` do client gửi có bắt đầu bằng `"image/"`, lấy đuôi file từ `file.getOriginalFilename()` client gửi; không đọc magic bytes, không decode lại ảnh, không xóa EXIF.
  `application.yaml` giới hạn `max-file-size: 10MB` ở tầng multipart (Spring), không có giới hạn chiều rộng/cao ảnh.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Gọi `POST /disease/predict` không có `X-Service-Api-Key` | 200, chạy model | 401 |
| Gọi `POST /disease/knowledge/retrieve` không có `X-Service-Api-Key` | 200, trả kết quả RAG | 401 |
| Cổng AI (8000) gọi trực tiếp từ máy khác trong LAN | Truy cập được | Không publish ra host nữa (chỉ mạng nội bộ compose); `AI_HOST_PORT` đổi thành tùy chọn chỉ dùng khi debug local |
| Upload file đổi đuôi `.jpg` nhưng nội dung không phải ảnh | Lưu thành công, có thể làm hỏng bước AI predict sau | Từ chối ở backend (đọc lại bytes bằng thư viện ảnh, decode lỗi thì 400) |
| Upload ảnh thật có EXIF chứa GPS | Lưu nguyên EXIF, link `/uploads/...` công khai lộ vị trí | Chuẩn hóa lại ảnh (re-encode) trước khi lưu, xóa toàn bộ EXIF |

**Hướng làm**

- AI: thêm `dependencies=[Depends(authorize_backend)]` vào `router` (prefix `/disease`) thay vì chỉ gắn riêng từng route, hoặc thêm vào từng route còn thiếu nếu muốn giữ `/health`, `/class-contract` công khai (health-check không cần auth).
- Compose: bỏ block `ports` của service AI (hoặc đổi sang `expose` nội bộ), cập nhật `.env.example`/README nếu còn cần debug (ví dụ port-forward tạm thời qua `docker compose exec`).
- Backend: `FileStorageService.store(...)` đọc lại bytes bằng `ImageIO`/thư viện tương đương để xác minh định dạng thật và lấy lại kích thước, re-encode (loại bỏ metadata EXIF) trước khi ghi file; từ chối nếu decode lỗi hoặc không phải JPEG/PNG.
- Giữ nguyên giới hạn `max-file-size: 10MB` ở tầng Spring; không cần thêm giới hạn chiều rộng/cao trừ khi phát sinh vấn đề thực tế khi test.

**Phạm vi ảnh hưởng**

- Module và file chính: `ai/app/api/router.py`, `deploy/docker-compose.yml`, `deploy/.env.example`, `backend/.../service/FileStorageService.java`.
- Endpoint, bảng DB, biến môi trường: `/disease/predict`, `/disease/knowledge/retrieve` đổi mã lỗi khi thiếu key; `AI_HOST_PORT` đổi ý nghĩa (tùy chọn debug, không còn bắt buộc public).
- Role và client bị ảnh hưởng: backend (service gọi AI đã có key sẵn, không đổi), bất kỳ client ngoài nào từng gọi thẳng AI (dự kiến không có, vì nguyên tắc dự án là client chỉ gọi backend).
- Tương thích ngược và dữ liệu cũ: ảnh đã upload trước đó vẫn giữ EXIF cũ (không hồi tố); chỉ ảnh mới từ thời điểm deploy trở đi được xử lý.

**Kết quả đầu ra**

- Gọi 2 endpoint AI còn thiếu auth mà không có key → 401 (test tự động).
- Upload ảnh thật có GPS trong EXIF → file lưu trên server không còn EXIF (kiểm bằng đọc lại file sau upload trong test).

**Phụ thuộc, lưu ý**: không phụ thuộc WP khác trong phase 1; P3 (gateway APISIX) sẽ bọc thêm một lớp auth ở gateway, không thay thế lớp `authorize_backend` này.

## WP1-04: Script reset, mở rộng CI, log theo profile

- Mục tham chiếu: mục 17 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: dựng lại toàn bộ hệ thống từ clone sạch bằng một lệnh, lặp lại được nhiều lần cho demo; CI chạy test thật thay vì chỉ build; log không để DEBUG mặc định khi chạy thật.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `.github/workflows/ci.yml`: chỉ trigger khi `backend/**` đổi, job `build` chạy `mvn ... verify -DskipTests` — không test nào chạy trong CI hiện tại; không có job cho frontend/mobile/AI, không có `docker compose config`/build image/quét secret.
- `application.yaml`: `logging.level.HaUI.ImplSolution.DX_Farm: DEBUG` là giá trị cố định, không theo Spring profile.
- Chưa tìm thấy script reset trong `deploy/` (cần Glob xác nhận khi code); `deploy/data/*` một số thư mục thuộc uid container (Postgres, Mattermost) nên không xóa trực tiếp bằng user thường trên host.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Push/PR vào `backend/**` | Build + `verify -DskipTests` | Build + chạy test thật (`mvn verify`, bỏ `-DskipTests`) |
| Push/PR đổi `frontend/`, `mobile/`, `ai/` | Không có CI job nào chạy | Thêm job tối thiểu: frontend `tsc --noEmit`, mobile `tsc --noEmit`/`expo export`, AI `pytest` |
| Muốn dựng lại từ đầu (demo nhiều lần) | Xóa tay `deploy/data/*`, vướng quyền uid container | Một script (`deploy/reset.sh` hoặc tương đương) chạy qua container để xóa đúng quyền, kèm hướng dẫn trong README |
| Chạy production/demo thật | Log DEBUG cho toàn bộ package app | DEBUG chỉ ở profile `dev`/`local`; `prod`/mặc định dùng INFO |

**Hướng làm**

- CI: bỏ `-DskipTests` ở job backend hiện có; thêm job riêng (hoặc step) cho frontend (`npm ci && npx tsc --noEmit`), mobile (`npm ci && npx tsc --noEmit`), AI (`pip install -r requirements*.txt && pytest`), mỗi job lọc theo `paths` tương ứng như backend đang làm; thêm step `docker compose -f deploy/docker-compose.yml config` để bắt lỗi cú pháp compose sớm; thêm step quét secret (dùng tool đã chọn ở WP1-01 Q3).
- Script reset: dừng toàn bộ container, xóa nội dung `deploy/data/*` bằng một container tạm (`docker run --rm -v .../deploy/data:/data alpine rm -rf /data/*`) để không bị chặn quyền, sau đó `docker compose up -d --build`.
- `application.yaml`: tách `logging.level` theo `application-dev.yaml`/mặc định, hoặc dùng biến môi trường `LOG_LEVEL_APP` với default `INFO`.

**Phạm vi ảnh hưởng**

- Module và file chính: `.github/workflows/ci.yml`, `application.yaml` (+ file profile mới nếu cần), `deploy/` (script reset mới).
- Endpoint, bảng DB, biến môi trường: không có; có thể thêm biến `SPRING_PROFILES_ACTIVE` nếu chưa dùng.
- Role và client bị ảnh hưởng: không ảnh hưởng runtime người dùng cuối, chỉ ảnh hưởng người dựng/CI.
- Tương thích ngược và dữ liệu cũ: script reset là thao tác phá hủy có chủ đích (xóa `deploy/data`), phải ghi cảnh báo rõ trong README, không tự động chạy trong CI.

**Kết quả đầu ra**

- CI job backend xanh với test thật chạy (không `-DskipTests`); có job build/check cho frontend, mobile, AI.
- Từ máy sạch: `git clone` → script reset/dựng → `docker compose ps` toàn bộ `healthy` trong một lần chạy, lặp lại được lần hai không lỗi.

**Phụ thuộc, lưu ý**: cần WP1-02/WP1-03 đã có test để CI "bật test thật" không đỏ ngay từ đầu; làm sau cùng trong phase 1.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST .../diagnosis/{id}/apply` | `appliedAt` ngoài khoảng hợp lệ → 422 thay vì 200 | Có (hành vi cũ chấp nhận mọi ngày) |
| Endpoint | `POST .../disease-reports` | Vụ `HARVESTED` → 422 thay vì 200 | Có |
| Endpoint | `POST .../crop-seasons` | 2 vụ `ACTIVE`/thửa hoặc `expectedHarvestDate < startDate` → 409/400 | Có |
| Endpoint | `PATCH .../diagnosis/{id}/approve`, `/reject`, `/apply`; `PATCH .../crop-seasons/{id}` (thu hoạch) | Ghi đè đồng thời → 409 (optimistic lock) thay vì ghi đè âm thầm | Có, nhưng chỉ lộ ra khi có 2 request đồng thời |
| Endpoint AI | `POST /disease/predict`, `POST /disease/knowledge/retrieve` | Bắt buộc header `X-Service-Api-Key` | Có (client nội bộ phải thêm header, không có client ngoài theo nguyên tắc dự án) |
| Bảng DB | `diagnosis`, `crop_season` | Thêm cột `version BIGINT` (optimistic lock) | Không (thêm cột có default) |
| Bảng DB | `crop_season` | Thêm unique constraint một phần `(parcel_id) WHERE status = 'ACTIVE'` | Có — cần dọn dữ liệu vi phạm trước (xem bên dưới) |
| Biến môi trường | `deploy/.env.example` (`AI_VECTOR_DB_READONLY_PASSWORD`, `KEYCLOAK_DB_PASSWORD`, `SUPERSET_READONLY_DB_PASSWORD`, `SUPERSET_OIDC_CLIENT_SECRET`, `MATTERMOST_DB_PASSWORD`, ...) | Bỏ giá trị mặc định, dùng `:?` | Có — người dựng phải tự điền |
| Biến môi trường | `AI_HOST_PORT` | Không còn bắt buộc publish ra host | Không (vẫn hoạt động nếu giữ, chỉ không khuyến khích) |
| Cấu hình | `backend/DX-Farm/Dockerfile` | Thêm `ENV TZ=Asia/Ho_Chi_Minh` | Không |

## Dữ liệu, migration và rollback

- Migration: thêm cột `version` (default `0`) cho `diagnosis`, `crop_season`; thêm unique constraint một phần cho `crop_season`.
  Chạy theo cơ chế migration hiện có của dự án (rà lại khi code — nếu chưa dùng Flyway thì ghi nhận là nợ kỹ thuật, không mở rộng sang Flyway ở phase này vì đó là việc của P2).
- Dữ liệu cũ: trước khi bật unique constraint, chạy script rà `crop_season` nhóm theo `parcel_id` có nhiều hơn 1 `ACTIVE`, theo quyết định Q2 (xóa seed cũ và seed lại, hoặc chuyển vụ thừa sang trạng thái khác thủ công).
- Rollback: nếu constraint mới chặn nhầm dữ liệu hợp lệ, có thể tắt tạm bằng migration ngược (xóa constraint), nhưng ưu tiên sửa dữ liệu thay vì rollback vì đây là lỗ hổng đã xác nhận cần đóng.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D6 (kế thừa phase 0) | Test chỉ cho case ảnh hưởng logic/an toàn hệ thống | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | `appliedAt` lùi tối đa 30 ngày | Đủ dư cho sai số thực tế, vẫn chặn input vô lý |
| Q2 | Dọn dữ liệu cũ bằng xóa + seed lại | Dữ liệu demo, không phải dữ liệu thật, không cần script chuyển trạng thái phức tạp |
| Q3 | Dùng gitleaks | MIT license (OSI), chạy local + CI không cần dịch vụ ngoài |

## Ghi chú triển khai

- Không có công cụ migration: cột `version` do Hibernate `ddl-auto: update` thêm (`NOT NULL DEFAULT 0`), partial unique index nằm trong `backend/DX-Farm/src/main/resources/schema.sql`.
- Cổng AI bỏ hẳn khỏi compose (không còn `AI_HOST_PORT`).
- `apply` tăng version của `CropSeason` để thu hoạch chạy song song không lọt qua khóa PHI vừa tạo.
- Upload ảnh chỉ nhận JPEG/PNG, giới hạn 40 megapixel, áp hướng EXIF vào điểm ảnh trước khi bỏ metadata.
- Secret của client Superset đi vào realm qua `${SUPERSET_OIDC_CLIENT_SECRET}` lúc Keycloak import.
- Thêm healthcheck cho frontend, portal, keycloak để `reset.sh` và `docker compose ps` phản ánh đúng trạng thái.
- CI tách thành 5 workflow theo thư mục (`ci.yml`, `ci-frontend.yml`, `ci-mobile.yml`, `ci-ai.yml`, `ci-infra.yml`), chạy cho cả `main` và `Khuong/main`.
