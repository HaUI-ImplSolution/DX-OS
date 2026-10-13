# BI Dashboard (Apache Superset)

Chỉ chứa cấu hình và script của Superset (image `apache/superset:3.1.3` chính thức, Apache-2.0).
Service `superset` khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).

## Cách hoạt động

- Metadata dùng SQLite riêng (`deploy/data/superset/`), không dùng Postgres nghiệp vụ; không có Celery nên truy vấn chạy đồng bộ.
- `bootstrap.sh` (entrypoint): `db upgrade`, tạo admin, `superset init`, cài `authlib` cho SSO, rồi `gunicorn`.
- Backend tạo role Postgres **chỉ đọc** `superset_ro` (`SupersetReadOnlyRoleSeeder`): chỉ `SELECT` trên các view `vw_*` và bảng danh mục `diseases`, `pesticides`, `rice_varieties`; không đọc được `workspaces` hay bảng nghiệp vụ.
- `bootstrap_datasources.py` đọc `DXOS_APP` (giá trị khác thì dừng) và đăng ký qua REST API của Superset, idempotent và có retry:
  - **Farm:** import `farm_dashboard_export.zip` (database, dataset, chart, dashboard; nguồn sự thật duy nhất) và truyền lại mật khẩu Postgres vì export mask mật khẩu. Chưa có bundle thì chỉ tạo kết nối `DX-Farm` và 6 dataset.
  - **Lab:** tạo kết nối `DX-Lab` và 5 dataset `vw_lab_*` (`rooms`, `devices`, `tickets`, `device_loans`, `room_bookings`); dashboard dựng tay.
- `superset_config.py` cấu hình SSO Keycloak (`AUTH_OAUTH`).

## Đăng nhập

- `http://localhost:${APISIX_SUPERSET_HOST_PORT}` (mặc định `9081`): listener riêng của gateway vì Superset 3.1 không chạy được dưới đường dẫn con.
- SSO Keycloak, tài khoản demo ở [`sso/README.md`](../sso/README.md): `ADMIN` → `Admin`, `ENGINEER` → `Gamma`; tài khoản khác (ví dụ `FARMER`) bị từ chối.
- `SUPERSET_ADMIN_USERNAME` / `SUPERSET_ADMIN_PASSWORD` chỉ dùng cho REST API, không đăng nhập qua UI.

## Dashboard "DX-Farm — Bản đồ & Trạng thái thửa ruộng"

7 chart, mỗi chart đọc một view chỉ chứa bản ghi đã duyệt hoặc đã xử lý, không có dữ liệu cá nhân:

| Chart | View |
|---|---|
| Bản đồ thửa ruộng (deck.gl Scatterplot) | `vw_parcels_approved` |
| Vụ mùa theo trạng thái (Pie) | `vw_crop_seasons_approved` |
| Báo cáo bệnh theo tuần (cột chồng) | `vw_diagnoses_approved` |
| Điểm nóng dịch bệnh (deck.gl Heatmap) | `vw_diagnoses_approved` |
| Thửa đang cách ly (bảng) | `vw_treatment_applications_confirmed` |
| Lịch thu hoạch dự kiến (cột chồng) | `vw_crop_seasons_approved` |
| Tỷ lệ AI đúng/sai (Pie) | `vw_ai_diagnosis_outcomes` (`CONFIRMED`, `REJECTED`, `OVERRIDDEN`) |

Còn `vw_disease_reports_processed` (báo cáo đã chẩn đoán, không ảnh và người báo cáo); view `vw_lab_*` của Lab chỉ chứa mã, id và nhóm.
Chart chưa có dữ liệu hiện "No results" (chưa có việc phun thuốc đã xác nhận hoặc chẩn đoán AI).
Nền bản đồ để trắng vì mọi nền miễn phí đều cần thẻ thanh toán hoặc gắn nhãn sai ở Hoàng Sa, Trường Sa; điểm vẫn đúng vị trí tương đối.

## Thêm view hoặc cập nhật dashboard

- `superset_ro` không tự đọc được bảng mới: tạo view lọc đúng trạng thái đã duyệt trong một migration rồi thêm tên vào `READABLE_RELATIONS` của `SupersetReadOnlyRoleSeeder`.
- Chỉnh dashboard trong UI (đăng nhập `admin1`), **⋯ → Export**, đè file tải về lên `bi/farm_dashboard_export.zip` rồi commit (file không chứa mật khẩu thật).
  Kiểm lại bằng `docker compose up -d --force-recreate superset`; chart bị nhân đôi nghĩa là `position_json` thiếu `meta.uuid` khớp UUID thật, phải sửa qua UI.
