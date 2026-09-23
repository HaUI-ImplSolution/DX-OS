# Checklist: Phase C3

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC3-{xx}-{lớp}-{nn}`.

## WPC3-01: Đổi tên realm, client, audience

Kết quả đầu ra: đăng nhập web, mobile, Superset hoạt động; token audience cũ bị từ chối.

**Spec và tài liệu**

- [ ] WPC3-01-DOC-01 Ghi bảng tên mới vào `sso/README.md`.

**Backend**

- [ ] WPC3-01-BE-01 [core] `application.yaml`: `jwk-set-uri`, `audience`, `keycloak.admin.realm` dùng `dxos`.
- [ ] WPC3-01-BE-02 [farm] `application-farm.yaml`: `farmer-client-ids` mặc định `farm-mobile`.

**Infra, deploy, CI**

- [ ] WPC3-01-INFRA-01 `git mv sso/realm-dxfarm.json sso/realm-dxos.json` và sửa tên realm, client, mapper audience.
- [ ] WPC3-01-INFRA-02 `deploy/apisix/apisix.yaml`: `client_id`, `discovery`, `realm`.
- [ ] WPC3-01-INFRA-03 `deploy/docker-compose.yml`: mount realm mới, build arg web.
- [ ] WPC3-01-INFRA-04 `deploy/.env.example`: client admin, client Superset.
- [ ] WPC3-01-INFRA-05 `bi/superset_config.py`: đường dẫn realm.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC3-01-TEST-01 Token audience cũ bị 401 (TC3-03).

## WPC3-02: Tách vai và tài khoản mẫu khỏi realm import

Kết quả đầu ra: dựng sạch rồi seed có đủ vai và ba tài khoản mẫu.

**Backend**

- [ ] WPC3-02-BE-01 [core] Xác nhận tạo vai từ `RoleProvider` chạy idempotent khi khởi động.

**Data, seed, migration**

- [ ] WPC3-02-DATA-01 [farm] `deploy/demo/seed_demo.py` tạo `farmer1`, `engineer1`, `admin1` qua Admin API và gán vai.
- [ ] WPC3-02-DATA-02 Realm import không còn user và vai của app.

**Infra, deploy, CI**

- [ ] WPC3-02-INFRA-01 `deploy/demo/make_ci_env.py`, `e2e.py`: đi qua seed thay vì user có sẵn.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC3-02-TEST-01 Realm import không chứa user (TC3-04).

## WPC3-03: Đổi tên database, bucket, route và script

Kết quả đầu ra: không còn `dxfarm` ở hạ tầng và script dùng chung.

**Infra, deploy, CI**

- [ ] WPC3-03-INFRA-01 `deploy/.env.example`: `POSTGRES_DB`, `POSTGRES_USER`, `MINIO_BUCKET`, email quản trị.
- [ ] WPC3-03-INFRA-02 `deploy/apisix/apisix.yaml`: route `/dxos-uploads/*`.
- [ ] WPC3-03-INFRA-03 `deploy/demo/dxfarm_client.py` thành `dxos_client.py`, cập nhật `e2e.py`, `seed_demo.py`, `make_ci_env.py`.
- [ ] WPC3-03-INFRA-04 `bi/dxfarm_dashboard_export.zip` thành `farm_dashboard_export.zip`; sửa `bootstrap_datasources.py`, compose.
- [ ] WPC3-03-INFRA-05 `bi/bootstrap.sh`, `chat/bootstrap.sh`: email mặc định.
- [ ] WPC3-03-INFRA-06 `.gitleaks.toml`: cập nhật mẫu bỏ qua.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC3-03-TEST-01 Ảnh tải lên hiển thị qua route mới (TC3-05).

## WPC3-04: Cập nhật web, mobile, AI, tài liệu

Kết quả đầu ra: web và mobile đăng nhập được với tên mới.

**Frontend (web)**

- [ ] WPC3-04-FE-01 `frontend/src/auth.ts`, `frontend/Dockerfile`: realm, client id, khóa `dxos.returnPath`.

**Mobile**

- [ ] WPC3-04-MB-01 `mobile/.env.example`, `eas.json`, `src/auth/oidc.ts`, `session.ts`, `offline/queue.ts`: realm, client `farm-mobile`, khóa lưu trữ.
- [ ] WPC3-04-MB-02 Giữ scheme `dxfarm://` và bundle id; ghi chú build lại ở `mobile/README.md`.

**AI**

- [ ] WPC3-04-AI-01 Đổi `_dxfarm_device` thành `_device` ở `router.py`, `loader.py`, `plant_disease_service.py`, test liên quan.

**Spec và tài liệu**

- [ ] WPC3-04-DOC-01 `README.md`, `CONTRIBUTING.md`, `deploy/demo/README.md`, `portal/index.html`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC3-04-TEST-01 Tìm `dxfarm` ngoài ngoại lệ (TC3-01).

## Đóng phase

- [ ] `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch với `DXOS_APP=farm`.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
