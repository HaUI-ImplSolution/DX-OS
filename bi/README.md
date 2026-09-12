# BI Dashboard (Apache Superset)

Thư mục riêng ngang hàng `backend/`, `frontend/`, `ai/` — chỉ chứa cấu hình/script riêng của Superset (không phải mã nguồn ứng dụng tự viết).
Dựng cho tiêu chí **Giai đoạn 2 [D]** — dashboard BI tối thiểu hiển thị bản đồ/trạng thái thửa ruộng cho Ban quản trị, không dừng ở truy vấn SQL thuần.
Service `superset` được khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml) (bind mount các file ở thư mục này vào container), lên tự động cùng lệnh `docker compose up -d --build` ở [`README.md`](../README.md) gốc — không cần thao tác tay nào thêm.

## Vì sao chọn Apache Superset

Apache-2.0 (OSI-approved), khớp yêu cầu PoF — xem rà soát license đầy đủ ở [`LICENSES.md`](../LICENSES.md) tại gốc repo.
Không chọn Metabase (AGPL-3.0, copyleft mạnh hơn dù vẫn OSI-approved) để tránh mọi tranh cãi khi review.

## Cách hoạt động

- Service `superset` dùng image `apache/superset:3.1.3` chính thức, không build lại.
- `bi/bootstrap.sh` (entrypoint) chạy `superset db upgrade` + tạo admin + `superset init` rồi khởi `gunicorn`.
  Dùng metadata DB SQLite riêng (`deploy/data/superset/`, persist qua bind mount) — không dùng chung Postgres nghiệp vụ.
- Backend tự tạo role Postgres **chỉ-đọc** `superset_ro` lúc khởi động (`SupersetReadOnlyRoleSeeder`, xem backend).
  Superset không bao giờ có quyền ghi vào CSDL nghiệp vụ.
- `bi/bootstrap_datasources.py` chạy sau khi server lên, tự đăng ký mọi thứ qua REST API công khai của chính Superset (không đụng ORM nội bộ, ổn định qua version):
  - Nếu **đã có** `dxfarm_dashboard_export.zip` (trường hợp bình thường, file này đã commit sẵn trong repo): import trọn bộ database connection + dataset + chart + dashboard từ bundle này — đây là **nguồn sự thật duy nhất**.
    Mật khẩu Postgres bị Superset mask thành `XXXXXXXXXX` lúc export nên bootstrap phải truyền lại mật khẩu thật qua tham số `passwords` của API import, khớp theo đường dẫn file trong zip.
  - Nếu **chưa có** export nào (lần đầu dựng dashboard mới, trước khi có ai build+export) thì fallback: tự tạo database connection `DX-Farm` (role `superset_ro`) + 5 dataset (`parcels`, `crop_seasons`, `disease_reports`, `diagnoses`, `treatment_applications`) trỏ thẳng vào các bảng nghiệp vụ.
    Chart/dashboard thì để build tay theo hướng dẫn bên dưới.
  - Toàn bộ script idempotent (`overwrite=true`, tìm-theo-tên trước khi tạo mới) và có retry vì một vài request đầu tiên ngay sau khi Superset vừa khởi động đôi khi thất bại thoáng qua.
- `bi/superset_config.py` (bind mount vào `/app/pythonpath/`, Superset tự nạp) cấu hình SSO thật qua Keycloak.
  `authlib` không có sẵn trong image gốc nên `bootstrap.sh` tự `pip install` lúc container khởi động, không build lại image.

## Đăng nhập

- URL: `http://localhost:${SUPERSET_PORT:-8088}`
- Đăng nhập qua UI bằng SSO thật (Keycloak) — bấm "Sign In with keycloak", dùng tài khoản demo
  trong [`sso/README.md`](../sso/README.md). Role realm map sang role Superset:
  `ADMIN` → `Admin`, `ENGINEER`/`FARMER` → `Gamma`.
- Tài khoản `SUPERSET_ADMIN_USERNAME`/`SUPERSET_ADMIN_PASSWORD` (seed lúc khởi động) vẫn tồn tại
  song song, chỉ dùng cho REST API/automation (`bi/bootstrap_datasources.py` tự đăng nhập bằng
  tài khoản này) — không đăng nhập được qua UI nữa vì đã bật `AUTH_TYPE = AUTH_OAUTH`.

## Xem/quản trị dữ liệu

- **Dữ liệu nghiệp vụ Superset đọc** (Parcel, CropSeason...): nằm trong Postgres `dxfarm` — cùng CSDL với backend, không tách riêng. Superset chỉ đọc qua role `superset_ro` (chỉ có quyền `SELECT`, đã seed sẵn). Xem qua DBeaver bằng connection tới `dxfarm` hiện có, hoặc tạo connection mới:
  - Host/Port: giống connection tới `dxfarm` (mặc định `localhost:5432`)
  - Database: `dxfarm`
  - User: `superset_ro`
  - Password: giá trị `SUPERSET_RO_PASSWORD` trong `deploy/.env` (mặc định `superset_ro_demo_only`)

- **Metadata riêng của Superset** (dashboard, chart, user/role của Superset, log truy vấn...): **không** nằm trong Postgres — Superset dùng SQLite riêng, file `deploy/data/superset/superset.db`.

## Dashboard mẫu hiện có

**"DX-Farm — Bản đồ & Trạng thái thửa ruộng"** — 2 chart:

- **Bản đồ thửa ruộng** (deck.gl Scatterplot, dataset `parcels`) — vẽ điểm theo `latitude`/`longitude`.
  `Parcel` có thêm 2 cột toạ độ tuỳ chọn (xem backend `Parcel.java` + `ParcelPanel.tsx`) — chỉ thửa nào nhập toạ độ khi đăng ký mới lên bản đồ.
  ⚠️ Cố tình không cấu hình nền bản đồ (basemap) — chỉ thấy các điểm trên nền trắng, điểm vẫn đúng vị trí tương đối. Đã cân nhắc và bỏ qua các phương án:
  - **Mapbox**: cần API key + thẻ thanh toán; nhãn vùng biển ghi "South China Sea".
  - **OpenStreetMap** (mọi renderer công khai: OSM Standard, CyclOSM, Humanitarian, OpenTopoMap): không cần key, nhưng dữ liệu OSM tại khu vực Hoàng Sa/Trường Sa gắn nhãn hành chính Trung Quốc ("西沙区"/"三沙市"/"南沙区") — vấn đề ở tầng dữ liệu OSM, đổi style không giải quyết được.
  - **Google Maps**: nhãn đúng nhất ("quần đảo Hoàng Sa", "Biển Đông" theo tiếng Việt) nhưng Embed API tĩnh không gắn được marker động, còn JS API (gắn được marker) cần thẻ thanh toán + có ngưỡng free/tháng.
  - Tự host vector tile riêng (lọc bỏ nhãn tranh chấp) giải quyết được nhưng là một dự án kỹ thuật riêng, không tương xứng quy mô demo.
  Thửa ruộng thật nằm ở ĐBSCL, cách xa Hoàng Sa/Trường Sa — vấn đề nhãn chỉ lộ ra nếu người xem chủ động kéo bản đồ ra xa vùng dữ liệu.
- **Vụ mùa theo trạng thái** (Pie, dataset `crop_seasons`) — tỉ lệ `ACTIVE`/`HARVESTED`.

## Build/sửa dashboard rồi cập nhật bundle

Dashboard nên chỉnh trực tiếp qua UI Superset (kéo-thả, thêm chart) như bình thường — không cần sửa YAML tay.
Sau khi chỉnh xong, export lại và commit đè lên bundle cũ để lần `docker compose up` sau tự có ngay, không cần ai làm lại:

1. Vào UI, chỉnh dashboard "DX-Farm — Bản đồ & Trạng thái thửa ruộng" cho tới khi ưng ý.
2. **⋯ → Export** trên trang dashboard (hoặc gọi `GET /api/v1/dashboard/export/?q=!(<id>)` bằng token admin).
3. Đè file tải về vào đúng `bi/dxfarm_dashboard_export.zip`.
4. Trong thư mục `deploy/`: `docker compose up -d --force-recreate superset` để test lại từ đầu, xác nhận dashboard vẫn hiện đúng.
   Nếu thấy chart nhân đôi nghĩa là `position_json` thiếu `meta.uuid` khớp đúng UUID thật của chart, phải sửa lại qua UI thay vì build tay.
5. Commit file zip mới.

Lưu ý: file `.zip` này chứa cấu trúc YAML (readable), không chứa mật khẩu thật (Superset tự mask lúc export) — an toàn để commit vào git.
