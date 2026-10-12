# Plan thực hiện: Phase C3

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WPC3-01 → WPC3-02 → WPC3-03; WPC3-04 làm song song với WPC3-03.

## WPC3-01: Đổi tên realm, client, audience

- Mục tham chiếu: RD4.
- Mục tiêu: tên nền tảng trung tính ở Keycloak và mọi nơi đọc nó.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Realm | `dxfarm` | `dxos` |
| Audience API | `dxfarm-api` | `dxos-api` |
| Client web | `dxfarm-frontend` | `dxos-web` |
| Client mobile Farm | `dxfarm-mobile` | `farm-mobile` |
| Client Superset | `dxfarm-superset` | `dxos-superset` |
| Client admin backend | `dxfarm-backend-admin` | `dxos-backend-admin` |

**Hướng làm**

- Đổi `sso/realm-dxfarm.json` thành `sso/realm-dxos.json`, cập nhật mapper audience.
- Sửa mọi tham chiếu theo danh sách ở `02_CHECKLIST.md`.
- Core/App: tên `farm-mobile` khai báo trong cấu hình Farm (`ClientRestrictionPolicy`).

**Phạm vi ảnh hưởng**

- Module: `dx-core` (mặc định cấu hình), `app-farm`, hạ tầng.
- File chính: `sso/`, `deploy/apisix/apisix.yaml`, `deploy/docker-compose.yml`, `deploy/.env.example`, `application.yaml`, `bi/superset_config.py`, `frontend/`, `mobile/`.
- Endpoint, bảng DB, biến môi trường: `KEYCLOAK_ADMIN_CLIENT_ID`, `SUPERSET_OIDC_CLIENT_ID`, `JWT_AUDIENCE`, `FARMER_CLIENT_IDS`, các build arg của web.
- Role và client bị ảnh hưởng: mọi client.
- Tương thích ngược và dữ liệu cũ: token cũ vô hiệu; phải đăng nhập lại.

**Kết quả đầu ra**

- Đăng nhập web, mobile, Superset hoạt động với tên mới; token audience cũ bị từ chối.

**Phụ thuộc, lưu ý**: R1 ở `04_TRADEOFFS_RISKS.md`.

## WPC3-02: Tách vai và tài khoản mẫu khỏi realm import

- Mục tham chiếu: RD1, đề thi HaUI mục 4.1.
- Mục tiêu: realm nền tảng không chứa dữ liệu của app.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Vai FARMER, ENGINEER, ADMIN | Có sẵn trong realm import | Core tạo từ `RoleProvider` lúc khởi động (idempotent) |
| Tài khoản `farmer1`, `engineer1`, `admin1` | Có sẵn trong realm import | Script seed của Farm tạo qua Admin API |
| `ADMIN` quản trị chung | Chung với app | Giữ một vai quản trị nền tảng trong realm |

**Hướng làm**

- Realm import giữ: client, scope, mapper, cấu hình token, vai quản trị nền tảng.
- Core/App: core không chứa tên vai của app; app khai báo.

**Phạm vi ảnh hưởng**

- Module: `dx-core`, `app-farm`; `deploy/demo/seed_demo.py`.
- File chính: `sso/realm-dxos.json`, `KeycloakAdminClient`, seed demo.
- Endpoint, bảng DB, biến môi trường: không.
- Role và client bị ảnh hưởng: mọi tài khoản mẫu.
- Tương thích ngược và dữ liệu cũ: tài khoản mẫu chỉ có sau khi chạy seed.

**Kết quả đầu ra**

- Dựng sạch rồi chạy seed: có đủ vai và ba tài khoản mẫu của Farm.

**Phụ thuộc, lưu ý**: cần `RoleProvider` của C2; Q2.

## WPC3-03: Đổi tên database, bucket, route và script

- Mục tham chiếu: RD4.
- Mục tiêu: không còn `dxfarm` ở hạ tầng và script dùng chung.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Database và tài khoản Postgres | `dxfarm` | `dxos` |
| Bucket MinIO, route ảnh | `dxfarm-uploads`, `/dxfarm-uploads/*` | `dxos-uploads`, `/dxos-uploads/*` |
| Script demo | `dxfarm_client.py` | `dxos_client.py` |
| Dashboard Superset | `dxfarm_dashboard_export.zip` | `farm_dashboard_export.zip` (của app Farm) |
| Email quản trị mặc định | `admin@dxfarm.local` | `admin@dxos.local` |

**Hướng làm**

- Sửa `.env.example`, compose, APISIX, `bi/`, `chat/`, `deploy/demo/`, `.gitleaks.toml`.
- Core/App: dashboard là tài sản của app, đặt tên theo app.

**Phạm vi ảnh hưởng**

- Module: không (hạ tầng).
- File chính: danh sách ở `02_CHECKLIST.md`.
- Endpoint, bảng DB, biến môi trường: `POSTGRES_DB`, `MINIO_BUCKET`, route `/dxos-uploads/*`.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: link ảnh đã lưu và dữ liệu DB cũ mất khi dựng lại.

**Kết quả đầu ra**

- Dựng sạch bằng `deploy/reset.sh`, ảnh tải và hiển thị qua route mới.

**Phụ thuộc, lưu ý**: Q3.

## WPC3-04: Cập nhật web, mobile, AI, tài liệu

- Mục tham chiếu: RD4.
- Mục tiêu: client và tài liệu khớp tên mới.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Web | `/realms/dxfarm`, `dxfarm-frontend`, khóa `dxfarm.returnPath` | `/realms/dxos`, `dxos-web`, khóa `dxos.returnPath` |
| Mobile | `/realms/dxfarm`, `dxfarm-mobile`, khóa `dxfarm.accessToken` | `/realms/dxos`, `farm-mobile`; scheme `dxfarm://` giữ nguyên |
| AI | `_dxfarm_device` | `_device` |

**Hướng làm**

- Mobile cần build lại và cài lại do đổi client; ghi chú trong `mobile/README.md`.
- Core/App: mobile Farm là thương hiệu của app; giữ scheme và bundle.

**Phạm vi ảnh hưởng**

- Module: web, mobile, `ai/`.
- File chính: `frontend/src/auth.ts`, `frontend/Dockerfile`, `mobile/.env.example`, `eas.json`, `src/auth/*`, `ai/app/*`.
- Endpoint, bảng DB, biến môi trường: `VITE_KEYCLOAK_*`, `EXPO_PUBLIC_KEYCLOAK_*`.
- Role và client bị ảnh hưởng: web, mobile.
- Tương thích ngược và dữ liệu cũ: phiên lưu trong thiết bị bị vô hiệu.

**Kết quả đầu ra**

- Web và mobile đăng nhập được với tên mới.

**Phụ thuộc, lưu ý**: cần WPC3-01.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Cấu hình | Realm, client, audience Keycloak | Đổi tên | Có (token, phiên cũ) |
| Biến môi trường | `JWT_AUDIENCE`, `FARMER_CLIENT_IDS`, `KEYCLOAK_ADMIN_CLIENT_ID`, `SUPERSET_OIDC_CLIENT_ID`, `POSTGRES_DB`, `MINIO_BUCKET` | Giá trị mặc định mới | Có với `.env` cũ |
| Route | `/dxfarm-uploads/*` → `/dxos-uploads/*` | Đổi | Có với link ảnh cũ |
| Bảng DB | Không | Không | Không |

## Dữ liệu, migration và rollback

- Migration: không có migration DB; realm import mới, dữ liệu dựng lại.
- Dữ liệu cũ: mất khi dựng lại; không có dữ liệu thật.
- Rollback: quay lại nhánh trước phase và dựng lại.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Tên nền tảng `dxos`, tên app đặt riêng (`farm-mobile`) | Nền tảng trung tính, app giữ nhận diện |
| D2 | Tài khoản mẫu do seed của từng app tạo | Realm không chứa dữ liệu app |
