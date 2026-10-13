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
  Không có Celery worker nên kết nối `DX-Farm` chạy truy vấn đồng bộ (`allow_run_async: false`), SQL Lab dùng được ngay.
- Backend tự tạo role Postgres **chỉ-đọc** `superset_ro` lúc khởi động (`SupersetReadOnlyRoleSeeder`, xem backend).
  Superset không bao giờ có quyền ghi vào CSDL nghiệp vụ và không đọc được bảng nghiệp vụ: role chỉ có `SELECT` trên các view `vw_*` (bản ghi đã duyệt hoặc đã xử lý, không có dữ liệu cá nhân) và ba bảng danh mục `diseases`, `pesticides`, `rice_varieties`.
- `bi/bootstrap_datasources.py` chạy sau khi server lên và đọc `DXOS_APP` (`farm` hoặc `lab`) để biết app nào cần BI; giá trị khác làm bước này dừng.
  Nó tự đăng ký mọi thứ qua REST API công khai của chính Superset (không đụng ORM nội bộ, ổn định qua version):
  - Nếu **đã có** `farm_dashboard_export.zip` (trường hợp bình thường, file này đã commit sẵn trong repo): import trọn bộ database connection + dataset + chart + dashboard từ bundle này — đây là **nguồn sự thật duy nhất**.
    Mật khẩu Postgres bị Superset mask thành `XXXXXXXXXX` lúc export nên bootstrap phải truyền lại mật khẩu thật qua tham số `passwords` của API import, khớp theo đường dẫn file trong zip đã bỏ thư mục gốc (`databases/DX-Farm.yaml`).
  - Nếu **chưa có** export nào (lần đầu dựng dashboard mới, trước khi có ai build+export) thì fallback: tự tạo database connection `DX-Farm` (role `superset_ro`) + 6 dataset trỏ vào các view `vw_*`.
    Chart/dashboard thì để build tay theo hướng dẫn bên dưới.
  - Với `DXOS_APP=lab` không có bundle: script tạo kết nối `DX-Lab` (cùng role `superset_ro`) và 5 dataset `vw_lab_rooms`, `vw_lab_devices`, `vw_lab_tickets`, `vw_lab_device_loans`, `vw_lab_room_bookings`; dashboard dựng tay từ các dataset này, số liệu có sẵn ở trang thống kê của web Lab.
  - Toàn bộ script idempotent (`overwrite=true`, tìm-theo-tên trước khi tạo mới) và có retry vì một vài request đầu tiên ngay sau khi Superset vừa khởi động đôi khi thất bại thoáng qua.
- `bi/superset_config.py` (bind mount vào `/app/pythonpath/`, Superset tự nạp) cấu hình SSO thật qua Keycloak.
  `authlib` không có sẵn trong image gốc nên `bootstrap.sh` tự `pip install` lúc container khởi động, không build lại image.

## Đăng nhập

- URL: `http://localhost:${APISIX_SUPERSET_HOST_PORT}` (mặc định `http://localhost:9081`), cổng thứ hai của gateway APISIX; Superset không publish cổng riêng.
  Superset 3.1 không chạy được dưới đường dẫn con nên cần listener riêng.
- Đăng nhập qua UI bằng SSO thật (Keycloak) — bấm "Sign In with keycloak", dùng tài khoản demo trong [`sso/README.md`](../sso/README.md).
  Role realm map sang role Superset: `ADMIN` → `Admin`, `ENGINEER` → `Gamma`; tài khoản không có một trong hai role này (ví dụ `FARMER`) bị từ chối đăng nhập.
  Khi chạy Lab, `admin1` (role `ADMIN`) xem được dataset `vw_lab_*`.
  Role Postgres `superset_ro` không đọc được bảng `workspaces` (thông tin liên hệ nông hộ) hay bất kỳ bảng nghiệp vụ nào ngoài các view.
- Tài khoản `SUPERSET_ADMIN_USERNAME`/`SUPERSET_ADMIN_PASSWORD` (seed lúc khởi động) vẫn tồn tại song song, chỉ dùng cho REST API/automation (`bi/bootstrap_datasources.py` tự đăng nhập bằng tài khoản này) — không đăng nhập được qua UI nữa vì đã bật `AUTH_TYPE = AUTH_OAUTH`.

## Xem/quản trị dữ liệu

- **Dữ liệu nghiệp vụ Superset đọc**: các view `vw_*` trong Postgres `dxos` — cùng CSDL với backend, không tách riêng (định nghĩa ở `V9__bi_views.sql`).
  Superset chỉ đọc qua role `superset_ro` (chỉ có quyền `SELECT` trên danh sách view và bảng danh mục, đã seed sẵn).
  Xem qua DBeaver bằng connection tới `dxos` hiện có, hoặc tạo connection mới:
  - Host/Port: giống connection tới `dxos` (`localhost`, cổng `POSTGRES_HOST_PORT`)
  - Database: `dxos`
  - User: `superset_ro`
  - Password: giá trị `SUPERSET_READONLY_DB_PASSWORD` trong `deploy/.env`

- **Metadata riêng của Superset** (dashboard, chart, user/role của Superset, log truy vấn...): **không** nằm trong Postgres — Superset dùng SQLite riêng, file `deploy/data/superset/superset.db`.

## Dữ liệu cho BI

| View | Nội dung |
|---|---|
| `vw_parcels_approved` | Thửa đã duyệt, chưa xóa; không có tên chủ hộ |
| `vw_crop_seasons_approved` | Vụ đã duyệt của thửa đã duyệt |
| `vw_disease_reports_processed` | Báo cáo đã có chẩn đoán; không có ảnh và người báo cáo |
| `vw_diagnoses_approved` | Chẩn đoán kỹ sư đã duyệt, kèm bệnh và vị trí thửa |
| `vw_treatment_applications_confirmed` | Việc phun thuốc kỹ sư đã xác nhận, kèm ngày hết cách ly |
| `vw_ai_diagnosis_outcomes` | Kết quả từng chẩn đoán do AI tạo: `CONFIRMED`, `REJECTED` hoặc `OVERRIDDEN` |

Quy ước khi thêm bảng mới:

- `superset_ro` không tự đọc được bảng mới.
  Muốn đưa dữ liệu lên BI, tạo view lọc đúng trạng thái đã duyệt trong một migration rồi thêm tên view vào `READABLE_RELATIONS` ở `SupersetReadOnlyRoleSeeder`.
- Bảng danh mục không nhạy cảm cũng thêm vào danh sách đó.
- `vw_ai_diagnosis_outcomes` coi chẩn đoán có `symptom_text` rỗng và `model_version` khác `manual-input-v1` là kết quả của AI.

## Dashboard mẫu hiện có

**"DX-Farm — Bản đồ & Trạng thái thửa ruộng"** — 7 chart, đều đọc qua view:

- **Bản đồ thửa ruộng** (deck.gl Scatterplot, `vw_parcels_approved`) — vẽ điểm theo `latitude`/`longitude`.
- **Vụ mùa theo trạng thái** (Pie, `vw_crop_seasons_approved`) — tỉ lệ `ACTIVE`/`HARVESTED`.
- **Báo cáo bệnh theo tuần** (cột chồng, `vw_diagnoses_approved`) — số chẩn đoán đã duyệt theo tuần, tách theo bệnh.
- **Điểm nóng dịch bệnh** (deck.gl Heatmap, `vw_diagnoses_approved`) — mật độ chẩn đoán theo vị trí thửa.
- **Thửa đang cách ly** (bảng, `vw_treatment_applications_confirmed`) — thửa có `harvest_locked_until` chưa qua.
- **Lịch thu hoạch dự kiến** (cột chồng, `vw_crop_seasons_approved`) — vụ `ACTIVE` theo tháng dự kiến thu hoạch và giống lúa.
- **Tỷ lệ AI đúng/sai** (Pie, `vw_ai_diagnosis_outcomes`) — chẩn đoán AI được kỹ sư duyệt, từ chối hoặc thay bằng bệnh khác.

Chart không có dữ liệu hiện "No results" cho tới khi có bản ghi phù hợp (ví dụ chưa có việc phun thuốc đã xác nhận).

### Nền bản đồ

Hai chart bản đồ dùng nền trắng, không cấu hình basemap — điểm vẫn đúng vị trí tương đối.
Đã cân nhắc và bỏ qua các phương án:

- **Mapbox**: cần API key + thẻ thanh toán; nhãn vùng biển ghi "South China Sea".
- **OpenStreetMap** (mọi renderer công khai: OSM Standard, CyclOSM, Humanitarian, OpenTopoMap): không cần key, nhưng dữ liệu OSM tại khu vực Hoàng Sa/Trường Sa gắn nhãn hành chính Trung Quốc ("西沙区"/"三沙市"/"南沙区") — vấn đề ở tầng dữ liệu OSM, đổi style không giải quyết được.
- **Google Maps**: nhãn đúng nhất ("quần đảo Hoàng Sa", "Biển Đông" theo tiếng Việt) nhưng Embed API tĩnh không gắn được marker động, còn JS API (gắn được marker) cần thẻ thanh toán + có ngưỡng free/tháng.
- Tự host vector tile riêng (lọc bỏ nhãn tranh chấp) giải quyết được nhưng là một dự án kỹ thuật riêng, không tương xứng quy mô demo.

Thửa ruộng thật nằm ở ĐBSCL, cách xa Hoàng Sa/Trường Sa — vấn đề nhãn chỉ lộ ra nếu người xem chủ động kéo bản đồ ra xa vùng dữ liệu.

Câu trả lời ngắn cho hội đồng: nền bản đồ miễn phí nào cũng hoặc cần thẻ thanh toán, hoặc gắn nhãn tranh chấp chủ quyền sai; nhóm chọn nền trắng để không phụ thuộc bên thứ ba và không hiện nhãn sai, đổi sang tile tự host khi có ngân sách.

### Nguồn dữ liệu mở khác

Thời tiết lấy từ Open-Meteo (CC BY 4.0) qua `GET /api/v1/parcels/{code}/weather`; không có chart thời tiết trong dashboard.

## Build/sửa dashboard rồi cập nhật bundle

Dashboard nên chỉnh trực tiếp qua UI Superset (kéo-thả, thêm chart) như bình thường — không cần sửa YAML tay.
Sau khi chỉnh xong, export lại và commit đè lên bundle cũ để lần `docker compose up` sau tự có ngay, không cần ai làm lại:

1. Vào UI bằng tài khoản `admin1`, chỉnh dashboard "DX-Farm — Bản đồ & Trạng thái thửa ruộng" cho tới khi ưng ý.
2. **⋯ → Export** trên trang dashboard (hoặc gọi `GET /api/v1/dashboard/export/?q=!(<id>)` bằng token admin).
3. Đè file tải về vào đúng `bi/farm_dashboard_export.zip`.
4. Trong thư mục `deploy/`: `docker compose up -d --force-recreate superset` để test lại từ đầu, xác nhận dashboard vẫn hiện đúng.
   Nếu thấy chart nhân đôi nghĩa là `position_json` thiếu `meta.uuid` khớp đúng UUID thật của chart, phải sửa lại qua UI thay vì build tay.
5. Commit file zip mới.

Lưu ý: file `.zip` này chứa cấu trúc YAML (readable), không chứa mật khẩu thật (Superset tự mask lúc export) — an toàn để commit vào git.
