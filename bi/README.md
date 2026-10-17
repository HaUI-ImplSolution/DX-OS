# BI Dashboard (Apache Superset)

Chỉ chứa cấu hình và script của Superset (image `apache/superset:3.1.3` chính thức, Apache-2.0).
Service `superset` khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).

## Cách hoạt động

- Metadata dùng SQLite riêng (`deploy/data/superset/`), không dùng Postgres nghiệp vụ; không có Celery nên truy vấn chạy đồng bộ.
- `bootstrap.sh` (entrypoint): `db upgrade`, tạo admin, `superset init`, cài `authlib==1.3.2` cho SSO khi chưa có (giữ `cryptography` trong khoảng Superset 3.1.3 yêu cầu), rồi `gunicorn`.
- Healthcheck của service có `start_period` 15 phút vì migration metadata lần đầu mất vài phút; các dịch vụ phụ thuộc chờ đến khi Superset healthy.
- Backend tạo role Postgres **chỉ đọc** `superset_ro` (`SupersetReadOnlyRoleSeeder`): chỉ `SELECT` trên các view `vw_*` và bảng danh mục `diseases`, `pesticides`, `rice_varieties`; không đọc được `workspaces` hay bảng nghiệp vụ.
- `bootstrap_datasources.py` đọc `DXOS_APP` (giá trị khác thì dừng) và đăng ký qua REST API của Superset, idempotent và có retry:
  - **Farm:** import `farm_dashboard_export.zip` (database, dataset, chart, dashboard; nguồn sự thật duy nhất) và truyền lại mật khẩu Postgres vì export mask mật khẩu. Chưa có bundle thì chỉ tạo kết nối `DX-Farm` và 7 dataset.
  - **Lab:** tạo kết nối `DX-Lab` và 5 dataset `vw_lab_*` (`rooms`, `devices`, `tickets`, `device_loans`, `room_bookings`); dashboard dựng tay.
- Superset bỏ `query_context` khi import, nên `bootstrap_datasources.py` sinh lại cho từng chart của dashboard bằng `chart_query_context.py` rồi lưu qua `PUT /api/v1/chart/{id}`.
  Hỗ trợ pie, cột và đường theo thời gian, bảng thô, deck.gl scatter và heatmap; chart loại khác giữ nguyên.
- Cùng bước đó ghi `mapbox_style` của chart bản đồ theo bundle.
- `superset_config.py` cấu hình SSO Keycloak (`AUTH_OAUTH`).

## Đăng nhập

- `http://localhost:${APISIX_SUPERSET_HOST_PORT}` (mặc định `9081`): listener riêng của gateway vì Superset 3.1 không chạy được dưới đường dẫn con.
- SSO Keycloak, tài khoản demo ở [`sso/README.md`](../sso/README.md): `ADMIN` → `Admin`, `ENGINEER` → `Gamma`; tài khoản khác (ví dụ `FARMER`) bị từ chối.
- `SUPERSET_ADMIN_USERNAME` / `SUPERSET_ADMIN_PASSWORD` chỉ dùng cho REST API, không đăng nhập qua UI.

## Dashboard "DX-Farm — Bản đồ & Trạng thái thửa ruộng"

11 chart, mỗi chart đọc một view chỉ chứa bản ghi đã duyệt hoặc đã xử lý, không có dữ liệu cá nhân:

| Chart | View |
|---|---|
| Bản đồ thửa ruộng (deck.gl Scatterplot) | `vw_parcels_approved` |
| Vụ mùa theo trạng thái (Pie) | `vw_crop_seasons_approved` |
| Báo cáo bệnh theo tuần (cột chồng) | `vw_diagnoses_approved` |
| Điểm nóng dịch bệnh (deck.gl Heatmap) | `vw_diagnoses_approved` |
| Thửa đang cách ly (bảng) | `vw_treatment_applications_confirmed` |
| Lịch thu hoạch dự kiến (cột chồng) | `vw_crop_seasons_approved` |
| Tỷ lệ AI đúng/sai (Pie) | `vw_ai_diagnosis_outcomes` (`CONFIRMED`, `REJECTED`, `OVERRIDDEN`) |
| Sản lượng thu hoạch theo tháng (cột chồng) | `vw_crop_seasons_approved` (vụ `HARVESTED`) |
| Diện tích thu hoạch theo tháng (cột chồng) | `vw_crop_seasons_approved` (vụ `HARVESTED`) |
| Nhiệt độ theo ngày (đường) | `vw_weather_daily` (trung bình nhiệt độ cao nhất và thấp nhất của các vị trí thửa) |
| Lượng mưa theo ngày (cột) | `vw_weather_daily` (trung bình lượng mưa trong ngày của các vị trí thửa) |

Còn `vw_disease_reports_processed` (báo cáo đã chẩn đoán, không ảnh và người báo cáo); view `vw_lab_*` của Lab chỉ chứa mã, id và nhóm.
Chart chưa có dữ liệu hiện "No results" (chưa có việc phun thuốc đã xác nhận hoặc chẩn đoán AI).
Hai chart thời tiết đọc bảng `weather_observations`, mỗi giờ Backend ghi một dòng cho mỗi vị trí thửa đã duyệt từ dữ liệu Open-Meteo thật (không ghi snapshot dự phòng).
Chart có điểm đầu tiên sau lần chạy đầu của job, khoảng một phút sau khi Backend khởi động.
Nền bản đồ là `basemap_light.json`: vector tile OpenFreeMap (dữ liệu OpenStreetMap) vẽ đất, nước, sông, đường và nhãn tỉnh, thành phố, thị xã.
Nhãn chỉ vẽ trong vùng kinh độ 102°E đến 110°E; không có nhãn đảo, nhãn biển và đường biên giới.
Style được mount vào `/static/assets/basemap_light.json`, hai chart bản đồ trỏ tới đó.
Tham số `?v=` là hash nội dung file, do `bootstrap_datasources.py` tính nên trình duyệt tự tải lại khi file đổi.
Góc bản đồ hiện attribution OpenFreeMap, OpenMapTiles và OpenStreetMap.
Tile tải từ `tiles.openfreemap.org`, địa chỉ này nằm trong `connect-src` của CSP ở `superset_config.py`.
Ô Map Style trong Explore chỉ nhận URL `mapbox://styles/...`; đổi nền bằng cách sửa bundle.

## Kiểm thử

```bash
cd bi && python -m unittest test_chart_query_context
```

Cần PyYAML.
Bài kiểm tra đọc `farm_dashboard_export.zip`: `query_context` sinh cho mọi chart, chart trỏ đúng dataset, mật khẩu database bị che, mọi dataset là view mà `superset_ro` đọc được, mỗi chart có đúng một ô trong dashboard và gắn bằng UUID, mỗi hàng không quá 12 cột.
CI chạy ở job `dashboard` của `ci-infra.yml`.

## Thêm view hoặc cập nhật dashboard

- `superset_ro` không tự đọc được bảng mới: tạo view lọc đúng trạng thái đã duyệt trong một migration rồi thêm tên vào `BiDatasetProvider` của app (`FarmPlatformConfig`, `LabPlatformConfig`).
- Chỉnh dashboard trong UI (đăng nhập `admin1`), **⋯ → Export**, đè file tải về lên `bi/farm_dashboard_export.zip` rồi commit (file không chứa mật khẩu thật).
  Kiểm lại bằng `docker compose up -d --force-recreate superset`; chart bị nhân đôi nghĩa là `position_json` thiếu `meta.uuid` khớp UUID thật, phải sửa qua UI.
