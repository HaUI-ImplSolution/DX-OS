# Checklist: Phase C3

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC3-{xx}-{lớp}-{nn}`.

## WPC3-01: Đổi tên realm, client, audience

Kết quả đầu ra: đăng nhập web, mobile, Superset hoạt động; token audience cũ bị từ chối.

**Spec và tài liệu**

- [x] WPC3-01-DOC-01 Ghi bảng tên mới vào `sso/README.md`.

**Backend**

- [x] WPC3-01-BE-01 [core] `application.yaml`: `jwk-set-uri`, `audience`, `keycloak.admin.realm` dùng `dxos`.
- [x] WPC3-01-BE-02 [farm] `application-farm.yaml`: `farmer-client-ids` mặc định `farm-mobile`.

**Infra, deploy, CI**

- [x] WPC3-01-INFRA-01 Đổi `sso/realm-dxfarm.json` thành `sso/realm-dxos.json` và sửa tên realm, client, mapper audience.
- [x] WPC3-01-INFRA-02 `deploy/apisix/apisix.yaml`: `client_id`, `discovery`, `realm`.
- [x] WPC3-01-INFRA-03 `deploy/docker-compose.yml`: mount realm mới, build arg web.
- [x] WPC3-01-INFRA-04 `deploy/.env.example`: client admin, client Superset.
- [x] WPC3-01-INFRA-05 `bi/superset_config.py`: đường dẫn realm.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC3-01-TEST-01 Token audience cũ bị 401 (TC3-03).

## WPC3-02: Tách vai và tài khoản mẫu khỏi realm import

Kết quả đầu ra: dựng sạch rồi seed có đủ vai và ba tài khoản mẫu.

**Backend**

- [x] WPC3-02-BE-01 [core] Xác nhận tạo vai từ `RoleProvider` chạy idempotent khi khởi động.

**Data, seed, migration**

- [x] WPC3-02-DATA-01 [farm] `deploy/demo/seed_demo.py` tạo `farmer1`, `engineer1`, `admin1` qua Admin API và gán vai.
- [x] WPC3-02-DATA-02 Realm import không còn user và vai của app.

**Infra, deploy, CI**

- [x] WPC3-02-INFRA-01 `deploy/demo/make_ci_env.py`, `e2e.py`: đi qua seed thay vì user có sẵn (`seed_demo.py` tạo tài khoản; `make_ci_env.py` đặt `POSTGRES_USER` và khóa MinIO mới).
- [x] WPC3-02-INFRA-02 Cấp cho service account của backend quyền tạo vai realm (`manage-realm` hoặc tương đương) để `RoleBootstrap` tạo được vai của app (HTTP 403 khi thiếu quyền).

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC3-02-TEST-01 Realm import không chứa user (TC3-04).

## WPC3-03: Đổi tên database, bucket, route và script

Kết quả đầu ra: không còn `dxfarm` ở hạ tầng và script dùng chung.

**Infra, deploy, CI**

- [x] WPC3-03-INFRA-01 `deploy/.env.example`: `POSTGRES_DB`, `POSTGRES_USER`, `MINIO_BUCKET`, email quản trị.
- [x] WPC3-03-INFRA-02 `deploy/apisix/apisix.yaml`: route `/dxos-uploads/*`.
- [x] WPC3-03-INFRA-03 `deploy/demo/dxfarm_client.py` thành `dxos_client.py`, cập nhật `e2e.py`, `seed_demo.py`, `make_ci_env.py`.
- [x] WPC3-03-INFRA-04 `bi/dxfarm_dashboard_export.zip` thành `farm_dashboard_export.zip`; sửa `bootstrap_datasources.py`, compose.
- [x] WPC3-03-INFRA-05 `bi/bootstrap.sh`, `chat/bootstrap.sh`: email mặc định.
- [x] WPC3-03-INFRA-06 `.gitleaks.toml`: cập nhật mẫu bỏ qua.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC3-03-TEST-01 Ảnh tải lên hiển thị qua route mới (TC3-05).

## WPC3-04: Cập nhật web, mobile, AI, tài liệu

Kết quả đầu ra: web và mobile đăng nhập được với tên mới.

**Frontend (web)**

- [x] WPC3-04-FE-01 `frontend/src/auth.ts`, `frontend/Dockerfile`: realm, client id, khóa `dxos.returnPath`.

**Mobile**

- [x] WPC3-04-MB-01 `mobile/.env.example`, `eas.json`, `src/auth/oidc.ts`, `session.ts`, `offline/queue.ts`: realm, client `farm-mobile`, khóa lưu trữ.
- [x] WPC3-04-MB-02 Giữ scheme `dxfarm://` và bundle id; ghi chú build lại ở `mobile/README.md`.

**AI**

- [x] WPC3-04-AI-01 Đổi `_dxfarm_device` thành `_device` ở `router.py`, `loader.py`, `plant_disease_service.py`, test liên quan.

**Spec và tài liệu**

- [x] WPC3-04-DOC-01 `README.md`, `CONTRIBUTING.md`, `deploy/demo/README.md`, `portal/index.html`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC3-04-TEST-01 Tìm `dxfarm` ngoài ngoại lệ (TC3-01).

## Đóng phase

- [x] `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] `.env.example`, seed, migration chạy được trên clone sạch với `DXOS_APP=farm`.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
