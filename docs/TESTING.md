# Hướng dẫn kiểm thử

Tài liệu này nói cách kiểm lõi, từng loại kiểm chạy ở đâu và những gì còn phải kiểm tay.
Kết quả của từng lần chạy nằm ở tab Actions của repository, tài liệu này không chép lại.
Mọi lệnh chạy từ gốc repo, trừ khi ghi khác; lệnh dựng stack ở [`README.md`](../README.md).
Mức hoàn thiện của từng tính năng và bài kiểm của nó ở [`website/docs/evidence-matrix.md`](../website/docs/evidence-matrix.md).

## 1. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| Docker và Docker Compose v2 | Backend `verify` dùng Testcontainers nên cần Docker chạy |
| JDK 21 | Dùng Maven wrapper trong repo; đặt `JAVA_HOME` về JDK 21 nếu máy mặc định bản khác |
| Node 22 trở lên | Cho `frontend`, `website` và `e2e`; riêng trợ lý cần Node 24.16 |
| Python 3 | Cho các script kiểm ở `e2e/api` |

## 2. Các loại kiểm

| Loại | Lệnh | Chạy ở CI |
|---|---|---|
| Định dạng, biên dịch, test của backend | `cd backend && ./mvnw verify` | Workflow `Backend` |
| Trợ lý tùy chọn, không cần key | `cd assistant && npm ci && npm test` (Node 24.16) | Workflow `Assistant` |
| Kiểu của web | `cd frontend && npx tsc --noEmit` | Workflow `Frontend` |
| Phân tích tĩnh của web | `cd frontend && npm run lint` | Workflow `Frontend` |
| Test đơn vị của web (Vitest, 58 test: `body()` của nhà cung cấp dữ liệu, điều kiện, xuất CSV, định dạng số và thời gian, điều hướng của bộ dựng mô hình, nơi dùng và đổi tên của trường và vai trò, hàng của quy trình) | `cd frontend && npm test` | Workflow `Frontend` |
| Giấy phép và header | `python3 .github/scripts/check_licenses.py`; `python3 .github/scripts/check_license_inventory.py check` (thêm `--maven` cho gói Maven, cần JDK 21); `pipx run --spec reuse==6.3.0 reuse lint` | `Check licenses` của `Compliance`; Maven ở `Scan the backend SBOM` của `Dependencies` |
| Gói trong image và văn bản giấy phép của image | `python3 .github/scripts/check_image_packages.py check` (thêm `--full` để đọc lại image, cần Docker); `python3 .github/scripts/check_third_party_notices.py check` (cần JDK 21) | `Check licenses` của `Compliance`; `Check the packages inside the images` theo lịch; `Scan the backend SBOM` của `Dependencies` |
| Giấy phép của phiên bản đang dùng | `GITHUB_TOKEN=... python3 .github/scripts/check_upstream_licenses.py`; `python3 -m unittest discover -s .github/scripts/tests` | `Check upstream licenses` và `Check licenses` của `Compliance` |
| SBOM khớp tệp khóa | `python3 .github/scripts/check_sbom_fresh.py` | `Check SBOMs against lockfiles` của `Compliance` |
| Route của gateway | `python3 e2e/api/check_routes.py` | `Validate the gateway routes` của `Platform` |
| Compose hợp lệ, dừng khi thiếu bí mật | `docker compose config` trong `deploy/` | `Validate the Compose file` của `Platform` |
| Gói phát hành và bộ cài | `.github/scripts/check_archive.sh <gói> <phiên bản>` | `Install from the release archive` của `Platform` |
| Sao lưu và khôi phục | `deploy/backup.sh` rồi `verify` | `Back up and restore` của `Platform` |
| API trên stack thật | Các script `e2e/api/check_*.py`, mục 4 | `API end-to-end` của `Platform`, ba nhóm, một nhóm chạy ở chế độ lõi và giữ quét mã độc |
| Bề mặt của gateway | `python3 e2e/api/check_surface.py` | `Gateway surface` của `Platform`, hai chế độ |
| Trình duyệt | Xem [`e2e/README.md`](../e2e/README.md) | `Browser end-to-end` của `Platform` |
| Trích dẫn giữa các tài liệu | `python3 .github/scripts/check_doc_refs.py` | `Build the docs site` của `Docs` |
| Trang tài liệu | `cd website && npm ci && npm run build` | `Build the docs site` của `Docs` |
| Tải đồng thời và cấu hình nhỏ nhất | `python3 e2e/api/load_test.py` trên stack tạm; các bước và kết quả ở [`SIZING.md`](SIZING.md) | Không |
| Kiểm tay | Mục 6 | Không |

Sau khi đổi tài nguyên nên chạy `./mvnw clean verify`: tài nguyên cũ còn trong `target/` làm Flyway thấy hai migration cùng phiên bản.
`./mvnw spotless:apply` sửa định dạng khi `spotless:check` báo lỗi.
Test của client Keycloak, bộ gửi webhook và bộ dò dịch vụ dùng WireMock, không cần Docker.
Test chậm gắn `@Tag("slow")`, bỏ qua bằng `-DexcludedGroups=slow`.

### Test của backend

`./mvnw verify` chạy 388 test (ngày 24/11/2026), khoảng một phút ở máy dev.
Test không cần Docker:

| Nhóm | Kiểm gì |
|---|---|
| Bộ đọc mô hình (`dx-model`) | `ModelParser` theo từng khối: vai, quyền, tham chiếu, quy trình, hạn, báo quá hạn, hành động hoàn tất, đặt chỗ, trợ lý, mẫu in, giao diện, thuộc tính của trường; mỗi lỗi nêu đúng dòng; `ConditionText` (thứ tự `and`, `or`, `not`, ngoặc, trích dẫn); `CodeFormat`; `ModelExtends`; `ModelLint` |
| Kế hoạch đổi mô hình (`ModelDiffTest`) | Từng loại thay đổi và mức rủi ro của nó: thêm, bỏ, đổi tên và đổi kiểu trường, ràng buộc chặt hơn hay lỏng hơn, quyền rộng ra hay hẹp lại, đổi quy trình, các câu hỏi, từ chối và dòng kiểm tra dữ liệu cũ |
| Hạn theo lịch (`BusinessCalendarTest`, `DeadlinesTest`) | Hạn rơi vào nghỉ trưa, cuối tuần, ngày lễ, ngoài giờ, qua nửa đêm; hạn tính theo giờ và theo ngày làm |
| Sinh mã, SQL và BPMN | Mã hồ sơ từ bộ đếm và năm của ngày làm việc, hết số; tên đi vào SQL; bảng và ràng buộc sinh từ mô hình; BPMN của từng họ quy trình |
| Mẫu đóng gói sẵn (`BundledTemplatesTest`) | Mỗi mẫu đọc được, sinh đủ bảng, so với chính nó không đổi gì |
| Phần còn lại | Tìm không dấu và ký tự đại diện, chặn mẫu chuỗi chạy vô hạn, câu chữ gửi vào kênh chat, PDF của mẫu in, tài liệu OpenAPI, giới hạn tốc độ theo người dùng, cột của màn giám sát |

Test cần Docker (PostgreSQL thật bằng Testcontainers, áp dụng migration của `engine`):

| Test | Kiểm gì |
|---|---|
| `HistoryChainIntegrationTest` | Chuỗi băm của lịch sử: dòng bị sửa, bị xóa hay chen vào đều bị chỉ đúng chỗ; ghi chú không nằm trong chuỗi; không phụ thuộc múi giờ của phiên |
| `DataServiceIntegrationTest` | Tạo, đọc, sửa, xóa trên bảng sinh từ mô hình: mã, mặc định, ràng buộc, quyền `own`, `all`, `read`, xung đột phiên bản, lọc, sắp xếp, phân trang, tìm không dấu, lịch sử |
| `ModelRegistryIntegrationTest` | Kho mô hình đọc phiên bản mới nhất, loại mô hình hỏng kèm lý do, giữ mô hình đang áp dụng |

Phần dựa vào chương trình ngoài (áp dụng mô hình qua `pgschema`, quy trình Flowable, Keycloak) do các script `e2e/api/check_*.py` ở mục 4 kiểm.

Độ phủ chưa được đo trong CI và không có lệnh đo nào trong repository.
Một lần đo bằng JaCoCo 0.8.13 chạy ngoài repository ngày 24/11/2026 cho: `dx-model` phủ 75% số dòng, `dx-engine` 38%, `dx-core` 23%, `dx-monitor` 9%, toàn backend 42%; các lớp thuần như `CodeFormat`, `ConditionText`, `BusinessCalendar`, `DdlGenerator`, `ModelDiff` phủ từ 96% đến 100%.

## 3. Dựng stack

```bash
deploy/make_env.sh                          # sinh deploy/.env
cd deploy && docker compose up -d --build
docker compose ps                           # mọi service healthy
```

Dựng lại từ đầu: `./reset.sh` trong `deploy/` (xóa `deploy/data`).
Image `dxos-*:local` chỉ mới theo lần `docker compose build` gần nhất, nên build lại sau khi đổi mã.
Cổng 9080 hoặc 5432 bị chiếm thì đổi `APISIX_HOST_PORT`, `POSTGRES_HOST_PORT` trong `deploy/.env`.

Kiểm trên một stack riêng thì không đụng stack chính:

- Dùng tệp env riêng và project Compose riêng, đổi cổng gateway và Postgres trong tệp env đó.
- Đặt đủ `DXOS_ENV_FILE`, `DXOS_BASE_URL` và `DXOS_PROJECT` (tên project Compose) thì lệnh `docker compose` của script chỉ chạm project đó.
- Xóa project, volume và thư mục dữ liệu của nó sau khi kiểm.

Các script gọi Keycloak Admin API qua gateway, mà gateway đóng `/admin` mặc định.
Đặt `KEYCLOAK_ADMIN_CONSOLE=true` trong env của stack kiểm rồi `docker compose up -d apisix-config` và `docker compose restart apisix`.
Chế độ lõi thì đặt `DXOS_MODE=core` và `COMPOSE_PROFILES=av` trong env đó, hoặc dùng `dxos start --core`.

## 4. Kiểm API trên stack

Chạy trên stack mới dựng, cơ sở dữ liệu rỗng, vì script áp dụng mô hình mẫu từ trạng thái rỗng.
Mỗi script phụ thuộc các script đứng trước trong cùng nhóm, nên chạy đúng thứ tự:

```bash
for c in api recovery case change process booking demo guards users docs conformance cli browser; do
  python3 e2e/api/check_$c.py
done
```

`check_events.py` và `check_bootstrap.py` khởi động lại backend nhiều lần, nên chạy ở một stack rỗng khác, theo thứ tự này:

```bash
python3 e2e/api/check_events.py
python3 e2e/api/check_bootstrap.py
```

| Script | Kiểm gì |
|---|---|
| `check_api.py` | Áp dụng mô hình mẫu vào hệ thống rỗng, lỗi tệp theo dòng và cột, quyền theo vai và theo dòng, chặn ghi `status`, chống SQL qua tên trường, 10.000 dòng có lọc và sắp xếp |
| `check_recovery.py` | Lỗi sau bước quy trình thì hồ sơ và quy trình cùng quay về; áp dụng bị Keycloak làm gián đoạn rồi áp dụng lại |
| `check_case.py` | Trạng thái và hành động, đơn vị, giao việc, hộp việc, lịch sử có giá trị cũ và mới, mã lý do của lỗi 403 và 409 |
| `check_change.py` | Đổi mô hình khi đã có dữ liệu: mức rủi ro, đổi tên giữ dữ liệu, dòng vi phạm, số hồ sơ chờ thiếu trường mà một `require` mới đòi, quay về; tệp mở rộng từ mẫu bằng `extends` |
| `check_process.py` | Các họ mẫu, hạn và quá hạn, hành động hoàn tất qua outbox, tệp đính kèm, thống kê, chống ghi đè |
| `check_codes.py` | Mã hồ sơ: mã đầu năm, đếm riêng theo thực thể, tạo đồng thời, từ chối ghi và sửa mã, tra theo mã, chuyển năm, đổi khuôn, hết số |
| `check_booking.py` | Họ `reservation`, danh mục của bảng điều khiển, mọi mẫu áp dụng được |
| `check_documents.py` | Mẫu in: PDF, tên tệp, trạng thái cho in, quyền đọc hồ sơ, audit, OpenAPI và metadata |
| `check_demo.py` | Tám tiêu chí của kịch bản trình diễn |
| `check_guards.py` | Người giữ vai, vai của nền tảng, xóa thực thể còn hồ sơ đang chạy, lần áp dụng dừng giữa chừng (tiếp tục và hủy bỏ) |
| `check_users.py` | Quản trị người dùng: gán và gỡ vai và đơn vị, tạo, khóa, đặt lại mật khẩu, audit; quản trị viên dự án chỉ thấy và gán vai trong dự án của mình, 403 ngoài phạm vi, người ở hai dự án, mô hình chưa có dự án |
| `check_docs.py` | Mọi endpoint các tài liệu nêu có trong OpenAPI của backend đang chạy, đúng phương thức |
| `check_conformance.py` | Rào chắn của lõi giữ với mọi mô hình đã áp dụng (hoặc các mô hình nêu tên): không ghi được `status`, không token thì 401, không đọc được dữ liệu mô hình khác |
| `check_cli.py` | Lệnh `dxos`: `lint`, `conformance`, `plan`, `apply --accept`, `model upgrade`, `archive` (kể cả `--store`, `check`, `query`, `list`, `auto`), `project` và `--project` của `client add`, lịch sử ghi tài khoản của lệnh, bộ mô phỏng IT/OT gửi lại không thêm hồ sơ; cần Docker và tự áp dụng một mô hình riêng |
| `check_browser.py` | Giao diện trình duyệt ngoài: `dxos client browser add`, `list`, `remove`, CORS, đăng nhập PKCE gọi được API, redirect không an toàn bị từ chối |
| `check_events.py` | Webhook: thứ tự, chữ ký, thử lại, nội dung không lộ, nhắc hạn; chuỗi hash nhiều phiên bản, trường riêng tư ngoài hash, điểm neo và bên nhận so hash |
| `check_bootstrap.py` | Tệp mô hình lúc khởi động: `applied`, `unchanged`, `skipped`, `failed` |
| `check_surface.py` | Không cần tài khoản, chạy ở cả hai chế độ: `/v3/api-docs` trả JSON, `/swagger-ui*` và `/admin*` trả 404 (`/admin` mở khi `KEYCLOAK_ADMIN_CONSOLE=true`), đăng nhập Keycloak phục vụ, chế độ `core` không phục vụ trang nào của Tầng 2 |

Các script tạo client Keycloak `dxos-check` (cho phép đăng nhập bằng mật khẩu, chỉ dùng cho máy dev) và các người dùng thử như `admin1`, `req1`, `keeper1`.
Vai của mô hình được gán ở client `dxos-m-<mô hình>`, vai `ADMIN` ở realm.
Mỗi kiểm in một dòng `PASS` hoặc `FAIL` kèm nhãn, cuối script in `N/M passed`, và script thoát với mã 1 khi có kiểm hỏng.

Điều kiện đặc biệt:

- Tiêu chí 3 của `check_demo.py` (tệp sạch, tệp EICAR bị chặn, tệp bị từ chối khi ClamAV dừng) chỉ chạy khi bật quét; khi tắt quét (`dxos av off`) script in `NOTE` rồi bỏ qua phần này.
- Đặt `DXOS_REQUIRE_SCAN=1` thì stack không quét làm script báo `FAIL`; nhóm chế độ lõi của `API end-to-end` đặt biến này.
- Với quét bật, đợi `clamav` khỏe (`docker compose up -d --wait clamav`) trước khi chạy script, vì tệp tải lên bị từ chối khi ClamAV chưa nạp xong chữ ký.
- Một số kiểm cần backend chạy với biến gây lỗi có chủ ý (`ENGINE_FAULT_AFTER_DECISION=true`, `ENGINE_FAULT_APPLY_STEP=ENSURE_CLIENT`); các script tự bật rồi tắt bằng cách tạo lại service `backend`.
- Keycloak 26.8 trả 400 thay vì 401 cho mật khẩu sai; các kiểm về mật khẩu nhận cả hai.

## 5. Trình duyệt

Bài Playwright của bảng điều khiển nằm ở `e2e/web/`; cách chạy, biến cấu hình và nơi có ảnh chụp ở [`e2e/README.md`](../e2e/README.md).
Bài `phone.spec.ts` chạy ở cỡ điện thoại 390 px và kiểm bảng điều khiển cùng hộp việc không rộng hơn màn hình.
CI chạy bài này ở job `Browser end-to-end` của workflow `Platform`.

## 6. Kiểm tay trước khi phát hành

Những mục dưới chưa có kiểm tự động.
Mỗi mục nêu điều phải thấy; lệnh và biến cấu hình chi tiết ở tài liệu của tính năng đó.

### Giao diện web

Đăng nhập bằng `req1` (`BORROWER`), `keeper1` (`KEEPER`) và `admin1` (`ADMIN`) của script ở mục 4.

1. **Vai và menu:** `BORROWER` thấy menu Thiết bị, Phiếu mượn, Ghi chú phiếu mượn, chỉ phiếu của mình, Thiết bị chỉ đọc; `KEEPER` thấy mọi phiếu và bấm Duyệt thì `Chờ duyệt` thành `Đã duyệt`.
2. **Mã hồ sơ** (mẫu `onestop`, trường "Số hồ sơ"): form không có ô nhập mã; chi tiết nêu mã kèm nút sao chép; danh sách có cột mã đầu tiên và tìm được theo một phần mã; thẻ điện thoại hiện mã.
3. **Chữ và bundle:** bundle web không chứa tên thực thể hay vai nào của mô hình; mọi chữ trên giao diện là tiếng Việt, kể cả lỗi tệp mô hình theo dòng và cột và nhãn loại thay đổi trong kế hoạch.
4. **Chế độ tối và điện thoại 390 px:** chế độ tối hiện đủ hộp việc, danh sách, chi tiết và bảng điều khiển; ở điện thoại mỗi hồ sơ là một thẻ, không trang nào tràn ngang, tên trang không bị công tắc ngôn ngữ che.
5. **Hộp việc thời gian thực:** số "Việc đang chờ" của cán bộ tăng trong khoảng hai giây khi sinh viên nộp hồ sơ, ở mọi tab, không tải lại trang; chi tiết đang mở đổi trạng thái khi người khác duyệt và form đang gõ dở không bị làm mới.
   Khởi động lại `backend` thì tab đang mở nhận lại thay đổi sau vài giây.
   Hộp việc trống của người nộp có một nút "Tạo <tên thực thể>" cho mỗi thực thể được ghi, bấm vào mở đúng biểu mẫu tạo; tài khoản chỉ đọc không thấy nút nào.
   `GET /api/v1/inbox/stream` không token thì 401, có token thì 200 `text/event-stream` kèm `: ping` mỗi 20 giây và đóng sau 600 giây.
6. **Hàng đợi sự kiện:** ba danh sách Lỗi, Chờ gửi, Đã gửi; thông điệp lỗi hiện lỗi gần nhất và nút Thử lại ngay.
7. **Tệp đính kèm:** tải `.html` lên bị từ chối (400); tải `.pdf` rồi mở link thì trình duyệt lưu thành tệp; gỡ tệp rồi mở link cũ thì 404.
8. **Lịch đặt chỗ:** kéo một lượt để đổi giờ, kéo cạnh dưới để đổi giờ kết thúc; lượt trùng giờ quay về chỗ cũ kèm lỗi.
9. **Lịch sử và ghi đè:** chi tiết hồ sơ hiện `Nhãn: cũ → mới` của từng trường; sửa hồ sơ đã bị người khác đổi thì báo "Bản ghi vừa được người khác thay đổi".
10. **Tài khoản của tôi:** mở Account Console của Keycloak ở tab mới, đã đăng nhập.
11. **Lần chạy đầu trên máy sạch:** `admin` đăng nhập bằng mật khẩu tạm, bị buộc đổi mật khẩu, vào bảng điều khiển, áp dụng mẫu đầu tiên.
    Stack chạy dở: chỉ chạy `docker compose up -d postgres` rồi `dxos start` thì `doctor` ghi cổng PostgreSQL do chính stack giữ, không `FAIL`, và lệnh dựng nốt các dịch vụ còn lại.
    Một chương trình ngoài giữ cổng (ví dụ `nc -l 127.0.0.1 <cổng>` khi stack chưa chạy) thì `dxos start` dừng ở `FAIL` nêu chương trình ngoài giữ cổng và khóa trong `deploy/.env` cần đổi.
12. **Hành động theo vai:** người chỉ giữ vai `AGENT` không thấy nút của hành động dành cho `LEAD`; hồ sơ đã giao thì chỉ người được giao thấy nút, "Bỏ giao" trả lại cho vai khác.
    Hành động có `require` khi trường còn trống báo "Bắt buộc nhập trước khi thực hiện" kèm tên trường và giữ nguyên trạng thái.
    Hồ sơ có quy trình không có nút Xóa; nút hành động không đặt nhãn hiện mã hành động.
13. **Hành động có `when`:** với `lead_approve` (`amount <= 5000000`), `lead_forward` (`amount > 5000000`) và `lead_reject` không điều kiện: hồ sơ 1.000.000 sáng nút duyệt, 9.000.000 sáng nút chuyển, không có số tiền thì cả hai nút có `when` mờ và nút từ chối vẫn sáng.
    Nút mờ có chú thích "Chỉ thực hiện khi ..."; gọi API của hành động mờ trả 409 `ERR_INVALID_STATE` với `data.reason` là `CONDITION_NOT_MET`; đúng 5.000.000 thì `<=` đạt.
14. **Hành động hàng loạt:** chọn nhiều hồ sơ chờ xử lý, thanh trên bảng hiện các hành động hợp lệ; hộp xác nhận nêu số hồ sơ và bảng kết quả báo từng hồ sơ; hồ sơ đã bị người khác xử lý báo lý do, các hồ sơ còn lại vẫn xong.
    Hành động cần ghi chú bắt buộc nhập ghi chú; chọn hơn 50 hồ sơ thì nút mờ; vai không có quyền xử lý không có ô chọn.
15. **Bảng theo trạng thái:** mỗi trạng thái là một cột có tổng số và tối đa 10 thẻ; thẻ mở đúng chi tiết, "Xem cả N hồ sơ" mở danh sách đã lọc; tải lại vẫn ở chế độ bảng; bộ lọc áp cho mọi cột; ở 360 px các cột cuộn bên trong, trang không tràn ngang.
16. **Xuất CSV:** tệp `<thực thể>-<ngày>.csv` có BOM, một dòng tiêu đề và đúng số dòng của danh sách đã lọc; ô bắt đầu bằng `=` hoặc `@` được thêm dấu nháy; vai chỉ thấy hồ sơ của mình chỉ nhận các dòng đó.
    Khi cổng API trả 429 giữa lúc xuất (ví dụ hạ `limit-count` của route `api` xuống vài lượt mỗi phút), nút hiện "Hệ thống đang giới hạn tốc độ, đang chờ để xuất tiếp", chờ tối đa khoảng một phút rồi hỏi lại và xuất đủ; còn bị từ chối thì báo lỗi.
    Khi chỉ việc tra tên người bị từ chối, tệp vẫn được tạo, cột người ghi mã và thông báo cuối nêu "Một số tên người không tra được nên được ghi bằng mã."
17. **Khối `ui:`:** menu hiện nhóm và biểu tượng khai báo; danh sách mở đúng cột và thứ tự khai báo, nút Cột vẫn hiện đủ cột; biểu mẫu chia nhóm theo `ui.form` với nhóm Khác ở cuối và hiện lời gợi ý; đổi `ui:` khi còn hồ sơ mở vẫn áp dụng được.
18. **Kiểu và quy tắc trên biểu mẫu** (mẫu Sổ tài sản): `boolean`, `date` và nhiều lựa chọn có ô nhập riêng; mã sai mẫu báo "Không đúng định dạng"; giá trị ngoài khoảng bị từ chối; "Bắt buộc nhập khi" hiện đúng với `required_when`.
    Trường nhiều lựa chọn có `default` là danh sách thì hồ sơ tạo không gửi trường đó nhận danh sách ấy; ở chế độ Điền form, ô mặc định chọn từ `options` và YAML ghi danh sách.
19. **Thống kê:** thực thể khai hạn có ô "Đúng hạn" với phần trăm và `n/N`; thực thể không khai hạn không có ô này.
    Báo cáo theo trường: "Nhóm theo" lựa chọn, đơn vị, trạng thái hoặc ngày theo tháng, "Tính" đếm hoặc tổng, trung bình, nhỏ nhất, lớn nhất của trường số; có dòng Tất cả, nhóm rỗng hiện "Chưa có giá trị" và "—".
    Thời gian trung bình ở từng trạng thái: hồ sơ trả lại rồi nộp lại tính mỗi lượt riêng, trạng thái cuối không có dòng, dòng đầu là trạng thái có trung bình lớn nhất và mang nhãn "Chờ lâu nhất"; vai `unit` chỉ thấy số trên hồ sơ của đơn vị mình.
    Vai `own` chỉ nhận số của hồ sơ mình; `report?groupBy=<trường số>` hoặc `metrics=sum(<trường chữ>)` trả 400, vai không có quyền đọc trả 403.
    Hồ sơ đóng sau hạn có dòng `overdue` trước dòng quyết định và tính là muộn (kể cả khi được trả lại rồi duyệt), hồ sơ rút lại sau hạn thì không; `closedOnTime` cộng `closedLate` bằng số hồ sơ đã quyết định và `GET /api/v1/history/verify` báo chuỗi nguyên vẹn.
    Tên hành động dành riêng (như `overdue`, `created`, `escalated`, xem [`MODEL.md`](MODEL.md)) bị từ chối ở bước kiểm kèm dòng và cột.
20. **Mẫu in** (`onestop`, Kết quả xử lý hồ sơ chỉ cho hồ sơ Đã duyệt): hồ sơ Chờ xử lý không có nút In; sau khi `OFFICER` duyệt, nút "In: Kết quả xử lý hồ sơ" hiện và trình duyệt lưu `ket-qua-xu-ly-ho-so-<mã>.pdf` có tiêu đề, các trường đã khai báo và chỗ ký.
    Người nộp thấy nút; người không đọc được hồ sơ thì không in được; ở điện thoại nút hiện dạng biểu tượng.

### Bảng điều khiển

21. **Soạn và áp dụng:** sơ đồ quy trình vẽ lại sau 5 giây không gõ theo `deadline`, `auto_close` và `after`; tệp sai báo lỗi kèm dòng và cột và giữ sơ đồ đúng gần nhất; sơ đồ mở to thành hộp thoại.
    Vai `ADMIN` khai báo trong tệp báo lỗi tiếng Việt kèm dòng và cột và không có nút Áp dụng; vai trùng tên với mô hình khác thì áp dụng được.
22. **Chế độ Điền form:** cây bên trái gồm Thông tin chung, Vai trò, Đơn vị và từng thực thể với Thông tin chung, Trường dữ liệu, Quyền truy cập, Quy trình (Tổng quan, Trạng thái, Hành động, Thời hạn, Tự động hóa), Giao diện, Mẫu in và Trợ lý nghiệp vụ; đường dẫn phía trên nêu chỗ đang sửa.
    Trang Giao diện sửa biểu tượng, nhóm và thứ tự menu, cột và cách sắp xếp của danh sách, nhóm trường trên form và gợi ý của trường; ô chọn trường của một nhóm chỉ còn trường chưa nằm trong nhóm khác, và nhóm chưa có trường thì báo đỏ.
    Trang Mẫu in sửa tên mẫu, nhãn, tên tệp, tiêu đề, trạng thái được in và các khối nội dung, đổi thứ tự được; dòng ký nhận tối đa bốn tiêu đề, và thêm mẫu thứ 11 bị chặn.
    Một khối để trống làm Core từ chối tệp, và lỗi hiện ở đúng trang Giao diện hoặc Mẫu in.
    Chuyển sang Soạn YAML rồi quay lại thì form vẫn ở chỗ cũ; vai trò, trường và thực thể chọn từ danh sách đã khai báo.
    Trường dữ liệu là bảng, có ô tìm khi hơn 5 trường; bấm một dòng mở phần sửa của trường đó, chỉ với các thuộc tính hợp kiểu.
    Mục Vai trò có ma trận vai trò × thực thể; ô báo đỏ khi vai trò xử lý quy trình mà không xem được hồ sơ.
    Xóa trường, vai trò hay thực thể đang được dùng thì hộp xác nhận liệt kê các chỗ dùng.
    Đổi tên một vai trò thì quyền, vai xử lý, `role` của hành động và `escalate.to` đổi theo; đổi tên một trường thì mọi chỗ hộp xác nhận liệt kê đều đổi theo, nên Xem kế hoạch không báo lỗi tham chiếu.
    Ở chế độ form, bảng "Viết được gì trong tệp" nằm dưới khung chính và chỉ nêu phần đang sửa (mỗi trang một nhóm khóa; các bước của quy trình chỉ nêu khóa của bước đó); sơ đồ quy trình nằm ở Quy trình → Tổng quan và mở to được. Ở chế độ YAML bảng đầy đủ vẫn ở cột phải cùng thẻ sơ đồ.
    Mọi biểu tượng thùng rác trong form màu đỏ.
    Ở Quy trình → Tổng quan, bấm một trạng thái trên sơ đồ mở Trạng thái với dòng đó được chọn, bấm một mũi tên mở hành động đó, bấm mũi tên tự đóng mở Thời hạn.
    Thêm vai trò xử lý chưa có quyền xem hồ sơ thì cây và trang hiện cảnh báo màu cam; cho quyền thì hết.
    Tệp có lỗi kèm dòng và cột thì cây đánh dấu đỏ đúng mục, và bấm lỗi trong danh sách đầu form thì đi tới mục đó.
    Sửa một chữ thì cạnh tên tệp hiện "Có thay đổi chưa áp dụng"; áp dụng xong mở lại thì hiện "Chưa sửa gì".
    Thêm hành động, đổi mã trạng thái, bỏ hoặc khôi phục một hành động của mẫu, chọn "Tự khai báo, không dùng mẫu": sơ đồ vẽ lại và YAML giữ nguyên dòng chú thích.
23. **Hướng dẫn trong trình soạn:** nút "Phạm vi có thể làm" ở mỗi phần của form và bảng "Viết được gì trong tệp" (ở chế độ form chỉ nêu phần đang sửa) nêu hệ thống chấp nhận gì, mở rộng được gì, tự tạo được gì, tên trạng thái và hành động của từng mẫu quy trình; rê chuột lên một khóa trong YAML hiện giải thích tiếng Việt; trình soạn không gạch chân `when`.
24. **Đổi quy trình khi còn hồ sơ đang mở:** thêm hoặc bỏ trạng thái, đổi `from`, `to`, `by`, `when`, `initial` hay hạn xử lý thì "Xem kế hoạch" báo không đổi được kèm câu tiếng Việt nêu số hồ sơ và không có nút Áp dụng; rút hết hồ sơ rồi xem lại thì có nút Áp dụng.
    Đổi nhãn, `role` hoặc `handler` thì áp dụng được: vai cũ không thực hiện được nữa, vai mới thực hiện được hồ sơ đang mở.
    Bỏ `terminal` của trạng thái cuối còn hồ sơ đã đóng thì bị từ chối kèm số dòng vi phạm ở `status`.
    Thêm `require` vào một hành động (thêm cả trường mới cũng được) thì kế hoạch vẫn `SAFE`, `CHANGE_ACTIONS` nêu hành động và trường, và một dải cảnh báo nêu số hồ sơ đang ở trạng thái nguồn còn thiếu trường; hồ sơ đã đủ trường hoặc đã đóng không được đếm, bỏ `require` thì không có dải cảnh báo.
25. **Áp dụng dừng dở:** dừng Keycloak (`docker compose stop keycloak`), áp dụng một thay đổi rồi bật lại thì thẻ mô hình hiện "Đang dừng dở" kèm Tiếp tục áp dụng và Hủy bỏ; Tiếp tục thì phiên bản tăng, Hủy bỏ hỏi xác nhận rồi về phiên bản đang chạy.
    Với lần áp dụng đầu dừng dở (`ENGINE_FAULT_APPLY_STEP=ENSURE_CLIENT`) thẻ hiện "Chưa có phiên bản" và Hủy bỏ làm thẻ biến mất.
    Mô hình đã lưu vi phạm luật kiểm: thêm vào `engine.model_version` một phiên bản có `auto_close` gọi hành động `needs_note: true` rồi khởi động lại backend; thẻ của mô hình ở tab **Mô hình đang chạy** có dải cảnh báo, mô hình vẫn chạy, và áp dụng tệp đã sửa (kèm `base`) xóa cảnh báo.
    Một phiên bản không đọc được (thiếu `roles`) làm thẻ có chip "Không chạy", yêu cầu dữ liệu của mô hình trả 409 và `dxos_models_left_out` bằng 1; tệp mới vi phạm vẫn bị từ chối ở kế hoạch.
26. **Người dùng:** tìm, gán và gỡ vai và đơn vị, tạo (mật khẩu tạm phải đổi ở lần đăng nhập đầu), khóa, mở, đặt lại mật khẩu; hộp thoại hiện lịch sử thay đổi và không có mật khẩu nào.
    Quản trị theo dự án: ở tab **Dự án** tạo dự án A và B, đưa một mô hình vào mỗi dự án, bấm **Thành viên** ở A rồi thêm tài khoản `x` làm thành viên và `y` làm quản trị viên.
    Đăng nhập bằng `y`: Bảng điều khiển chỉ có tab **Người dùng** và **Thành viên dự án**; danh sách chỉ có thành viên của A, hộp thoại của `x` chỉ có vai của mô hình A, không có đơn vị, đặt lại mật khẩu và khóa; `y` gán được vai của mô hình A cho `x`.
    `y` không thêm `x` vào B và không nâng ai lên quản trị viên (403); với tài khoản `ADMIN` thì cả hai làm được.
    Đổi dự án của một mô hình ở tab **Mô hình đang chạy** hiện hộp xác nhận; xóa dự án khi còn mô hình bị từ chối, còn dự án chỉ có thành viên thì xóa được.
27. **Ứng dụng ngoài:** đăng ký một ứng dụng với một vai của một mô hình, mã bí mật hiện một lần kèm nút sao chép; token lấy bằng mã đó đọc được mô hình đã gán, gọi bảng điều khiển hay quản trị người dùng thì 403.
    Đổi mã thì mã cũ bị từ chối; "Đổi vai" giữ mã cũ lấy được token, token mới mang vai mới và token cũ giữ vai cũ đến khi hết hạn; xóa thì không lấy được token và hồ sơ đã tạo vẫn còn.
    Tab **Dự án**: tạo hai dự án, đưa một mô hình vào dự án A và đăng ký một ứng dụng vào dự án B gọi mô hình đó thì dòng ứng dụng có nhãn "Dùng chéo dự án"; bộ lọc dự án ở tab **Mô hình đang chạy** và **Ứng dụng ngoài** chỉ hiện đúng nhóm, "Chưa phân loại" vẫn hiện những mô hình và ứng dụng chưa gán; xóa dự án còn mô hình hay ứng dụng thì bị từ chối.
28. **OpenAPI của mô hình:** nhập được vào công cụ API hoặc trình sinh thư viện không lỗi; đổi một trường rồi áp dụng và quay về thì tài liệu đổi theo; người không có vai của mô hình nhận 404.
29. **Danh mục dữ liệu** (ADMIN, mô hình có quy trình và tham chiếu): trang liệt kê vai và ứng dụng ngoài đi vào, nơi dữ liệu đi ra, liên kết giữa thực thể; mỗi thực thể có số bản ghi, quyền theo vai và danh sách trường.
    Đăng ký thêm một ứng dụng ngoài rồi tải lại thì nó hiện ở "Dữ liệu đi vào từ".
    Mục Nguồn gốc: mỗi trường có ba cột Đi vào từ, Được dùng bởi, Đi ra tới, và hiện đúng `default`, `reference`, `required_when`, `require`, `when`.
    Mục chất lượng: số đo kèm thời điểm; độ điền, thiếu theo điều kiện và vi phạm ràng buộc khác 0 được tô nổi bật; độ mới theo ô "Không cập nhật quá (ngày)" (30, 90, 180, 365; mặc định 90).
    Bỏ một dòng khỏi điều kiện bằng SQL (`update <bảng> set <trường> = null`) thì "thiếu theo điều kiện" tăng 1; đặt `updated_at` lùi 120 ngày thì số bản ghi chưa đổi tăng 1 với `staleDays=90` và về lại với `staleDays=200`.
    `data-catalog` và `data-lineage` bằng vai không phải ADMIN trả 403; `quality` bằng ADMIN có `sampleSize` bằng số dòng của bảng, bằng vai chỉ thấy dòng của mình thì bằng số dòng của họ, `staleDays=0` thì 400.
### API và tích hợp

30. **Khóa chống chạy lặp:** `POST /api/v1/data/{model}/{entity}` hai lần với cùng `Idempotency-Key` và nội dung thì cùng `id`, lần hai có `Idempotent-Replayed: true` và không thêm hồ sơ; cùng khóa với nội dung hoặc hành động khác thì 409 `IDEMPOTENCY_KEY_REUSED`; khóa sai khuôn thì 400; yêu cầu lỗi không giữ khóa.
31. **Webhook `chat` với Mattermost thật** (dựng riêng): tạo Incoming Webhook, đặt địa chỉ vào đích `format: chat` của `deploy/webhooks/targets.yml`, khai `webhook` trong mô hình có hạn `1m`, đặt `DXOS_DUE_SOON_BEFORE=20s`.
    Tạo một hồ sơ: kênh nhận lần lượt "Việc mới", "Sắp đến hạn xử lý", "Quá hạn xử lý" với đúng mã hồ sơ, mỗi loại một lần, outbox ghi đã giao.
    Tạm dừng Mattermost hơn 5 giây rồi tạo hồ sơ: API vẫn thành công, outbox ghi lỗi, tiếp tục thì outbox tự gửi lại (có thể trùng một tin).
    Địa chỉ hook sai (404) thì outbox ghi lỗi mà hồ sơ vẫn lưu; trả địa chỉ đúng thì thông báo tự được giao.
32. **Số đo Prometheus** (stack có `onestop` và hồ sơ của `dxos demo`): `/actuator/prometheus` trong container `backend` theo [`docs/MONITORING.md`](MONITORING.md): không mật khẩu hoặc sai thì 401, đúng `METRICS_PASSWORD` thì 200 có dòng `dxos_*`; qua cổng `9080` không trả số đo; `METRICS_PASSWORD` trống thì mọi lần đọc trả 401.
    `dxos_records{status=...}` cộng lại bằng số hồ sơ ở danh sách, `dxos_records_overdue` bằng số quá hạn ở Thống kê, nộp thêm một hồ sơ thì tăng một sau tối đa `METRICS_REFRESH_MS`.
    Đích webhook sai làm `dxos_outbox_pending` và `dxos_outbox_oldest_age_seconds` tăng, sửa thì về 0; áp dụng tệp mô hình sai làm `dxos_model_applies_total{result="failed"}` tăng một; không có nhãn nào ngoài `model`, `entity`, `status`, `result`.
33. **Thống kê theo vai:** với vai `unit` và `own`, có và không có bộ lọc, `total` và tổng `byStatus` bằng số dòng của danh sách cùng bộ lọc.
34. **Áp dụng khi đang ghi:** áp dụng một mô hình đổi bảng trong lúc có yêu cầu ghi hồ sơ của mô hình đó thì không có 500 và không hồ sơ nào ghi sai cấu trúc.
35. **Sửa chữa khi áp dụng lại:** áp dụng lại tệp không đổi khi bảng thiếu hoặc thừa cột, đổi kiểu cột, hoặc client, vai, nhóm đơn vị bị xóa thì kế hoạch ghi `repair` đúng loại, áp dụng xong về `unchanged`.

### Hướng dẫn và mẫu mô hình

Kiểm bằng đúng các lệnh và thao tác trong tài liệu.

36. **[`docs/REPORTING.md`](REPORTING.md)** (stack có `onestop` và `equipment`): kết nối bằng `report_ro` thì thấy bảng của `m_onestop` và `m_equipment`; truy vấn nối hai mô hình trả một dòng mỗi người; `UPDATE` bị từ chối với `permission denied for table request` và đọc `engine.outbox` bị từ chối; `ALTER ROLE` đổi mật khẩu thì mật khẩu cũ bị từ chối và áp dụng lại mô hình không đặt lại mật khẩu.
37. **[`docs/EXTENSIONS.md`](EXTENSIONS.md):**
    - `dxos client add`, lấy token từ tệp secret, tải OpenAPI đúng như lệnh trong tài liệu; mô hình khác và API quản trị trả 403; hai lần tạo cùng `Idempotency-Key` và nội dung trả cùng `id`, khác nội dung trả 409.
    - `deploy/apisix/local.yaml` với upstream và route mẫu: `docker compose run --rm --no-deps apisix-config --check` đạt với mẫu, và từ chối route thiếu token check, trùng id hoặc đường dẫn lõi, plugin hay schema sai, tham chiếu thiếu.
    - Nạp bằng cùng lệnh bỏ `--check`: gateway nhận cấu hình mới khi sửa hoặc bỏ local, cấu hình lỗi giữ nguyên tệp đầu ra; sau khi nạp, không token hoặc token sai trả 401 và token đúng tới được dịch vụ với nguyên đường dẫn.
    - Nâng bản cài từ archive với tệp local có sẵn: nội dung giữ nguyên, không có trong `.dxos-files`, route riêng vẫn được ghép với cấu hình lõi mới.
38. **Mẫu Đặt lịch khám và Đổi trả hàng** (tài khoản thử, dữ liệu giả): ở Thư viện mẫu bấm Dùng mẫu này rồi áp dụng từng mẫu.
    - `clinic`: người đặt chỉ thấy lịch của mình; trùng giờ cùng phòng bị từ chối lúc đặt, phòng khác thì nhận; người tiếp nhận đi qua xác nhận, tiếp nhận khám và hoàn tất.
    - `retail`: số lượng dưới 1 và thiếu lý do bị từ chối; nhân viên trả phiếu về kèm ghi chú, khách nộp lại rồi nhân viên duyệt; khách không tự duyệt và không đọc được phiếu của người khác.
    - `python3 e2e/api/check_conformance.py clinic retail` đạt.

### Lệnh `dxos` ngoài stack kiểm

39. **`dxos history verify`** với chuỗi gồm hash phiên bản 1 và 2: sửa phần bất biến, xóa hoặc chèn một dòng `record_event` bằng SQL thì chỉ ra dòng đầu tiên bị đổi; đổi tên người thực hiện, ghi chú hoặc chi tiết của dòng phiên bản 2 không làm đổi hash hay điểm neo, còn đổi mã người thực hiện thì chuỗi vỡ ở dòng đó; trả phần bất biến về giá trị cũ thì chuỗi nguyên vẹn.
40. **`dxos reconcile`:** xóa container `backend` thì chạy lại; sau `dxos stop` thì không bật lại; mọi service có chính sách restart `unless-stopped`.
41. **`dxos demo`** (stack có `dxos-cli`): tạo bốn tài khoản `judge_*` và 40 hồ sơ; `judge_student` thấy hồ sơ của mình, mỗi cán bộ chỉ thấy đơn vị mình, `judge_supervisor` thấy mọi hồ sơ và không có nút Tạo mới; chạy lại không nộp thêm hồ sơ và in mật khẩu mới; `dxos demo remove` gỡ bốn tài khoản.
42. **`dxos client add`, `list`, `role`, `rotate`, `remove`:** secret chỉ in ở `add` và `rotate`; `role` đổi vai mà secret cũ vẫn lấy được token và token mới mang vai mới; `rotate` làm secret cũ bị từ chối; `remove` làm client không lấy được token.
    `dxos project add`, `list`, `remove`, `dxos model project`, `dxos client project` và `--project` của `client add` gán và gỡ dự án; dự án còn mô hình hoặc ứng dụng, hoặc không có, thì lệnh trả lỗi; `dxos project member add`, `list`, `remove` thêm, liệt kê và bớt thành viên theo tên đăng nhập, tên không có thì lệnh trả lỗi.
43. **`dxos client browser add`:** tạo public client chỉ dùng Authorization Code với PKCE S256, có mapper audience và nhóm, lưu origin chính xác vào `CORS_ALLOWED_ORIGINS`; preflight từ origin đó được nhận, đăng nhập PKCE gọi được API; HTTP ngoài loopback và wildcard bị từ chối trước khi tạo client.
44. **Lưu trữ theo lịch:** `dxos archive schedule install` rồi `systemctl --user start dxos-archive.service`: `deploy/archive.log` ghi lượt chạy, `dxos archive list` thấy lần chạy mới khi có hồ sơ đã đóng quá `ARCHIVE_AFTER_DAYS`, chạy lần nữa không ghi lại các hồ sơ đó.
    `dxos archive check --store` khớp manifest; khóa S3 sai thì `list` và `auto` thoát mã 1 và không in khóa; `schedule uninstall` gỡ hai tệp đơn vị.
45. **`dxos gitops`, nguồn mô hình** (stack có `dxos-cli`, kho git cục bộ có một tệp mô hình mới):
    - `report`: `sync` ghi `drift`, `status` thoát mã 2 và chưa có schema `m_<mô hình>`; `approve` bị từ chối.
    - `approve`: `sync` ghi `pending`, `approve` với commit khác bị từ chối, đúng commit thì `applied` và `status` thoát mã 0.
    - `auto`: trường mới tùy chọn được áp dụng, xóa trường nằm chờ ở `pending`, `git revert` rồi `sync` thì về `in-sync`.
    - Tệp hỏng không chặn tệp khác: `failed` cho tệp hỏng, `sync` thoát mã 1, các tệp còn lại vẫn được xét.
46. **`dxos gitops`, nguồn hạ tầng** (bản cài thử đã chạy `install.sh`, `COMPOSE_PROJECT_NAME` riêng, kho DX-OS cục bộ):
    - Cùng nội dung thì `in-sync`; commit sửa, thêm và bỏ một tệp thì `drift` với đúng ba con số `changed`, `added`, `removed`.
    - `approve`: `pending`, commit sai bị từ chối, đúng commit thì `applied`, tệp trong bản cài khớp kho và `apisix/local.yaml` giữ nguyên.
    - `GITOPS_INFRA_MODE=auto` thiếu `GITOPS_REQUIRE_SIGNED=true` bị từ chối; commit không ký bị từ chối (mã 1, không đổi gì), commit ký đúng khóa được áp dụng.
    - `GITOPS_INFRA_REF` là tag `vX.Y.Z` thì `DXOS_IMAGE_TAG` đổi theo sau khi áp dụng; là nhánh thì giữ nguyên.
    - `update.sh` lỗi thì lượt áp dụng báo `failed` và thoát mã 1, các lượt sau chạy lại cho đến khi thành công rồi mới `in-sync`; `approve infra` với `--accept` khác `SAFE` bị từ chối.
47. **`dxos gitops`, chung hai nguồn:**
    - Nhánh không tồn tại thì `sync` thoát mã 1, `status` nêu nguyên nhân và giữ các mục của lượt tốt trước; `GITOPS_*` sai bị `sync` và `doctor` từ chối với cùng thông báo.
    - Địa chỉ SSH với host lạ, khóa sai hoặc khóa có quyền rộng bị từ chối, mỗi lỗi ghi nguyên nhân riêng.
    - API tắt thì nguồn mô hình ghi `error` và thoát mã 1.
    - `dxos gitops install` tạo timer và `uninstall` gỡ; `history.log` ghi mỗi lần commit hoặc kết quả đổi.
48. **Kế hoạch khi đổi quyền truy cập:** áp dụng một mẫu (ví dụ `equipment`), sửa `access` của một thực thể rồi chạy `dxos plan <tệp>` (bản chữ ghi "Widen access", "Narrow access"...; `dxos plan --json <tệp>` ghi các mã dưới đây) hoặc bấm Xem kế hoạch ở tab Soạn và áp dụng:
    - `own` sang `all` ra `WIDEN_ACCESS` với `BORROWER: own → all`; `all` sang `read` ra `NARROW_ACCESS`.
    - Thêm một vai vào `access` ra `GRANT_ACCESS` (`AUDITOR: none → read`); bỏ một vai ra `REVOKE_ACCESS` (`BORROWER: own → none`).
    - `own` sang `assigned`, hoặc `read` sang `own`, ra `CHANGE_ACCESS`.
    - `GRANT_ACCESS` và `WIDEN_ACCESS` ở mức `MIGRATION` kèm câu hỏi `role BORROWER gets wider access to ...`: `dxos apply` không có `--accept MIGRATION` thoát mã 1 và không đổi gì, tab Kế hoạch đòi tick "mở rộng quyền của vai", và `dxos gitops` ở chế độ `auto` để kế hoạch đó chờ duyệt.
    - `NARROW_ACCESS`, `REVOKE_ACCESS` và `CHANGE_ACCESS` ở mức `SAFE`, `dxos apply` không cần `--accept`; chỉ đổi nhãn thì không có dòng quyền nào, và tệp giống mô hình đang chạy thì `dxos plan --check` thoát mã 0.
    - Tab Kế hoạch ghi nhãn đúng ở tiếng Việt và tiếng Anh, dòng `vai: cũ → mới` hiện dưới nhãn.
49. **Lịch làm việc:** áp dụng một mô hình có `calendars` (ngày làm thứ Hai đến thứ Sáu, giờ `['08:00-12:00', '13:00-17:00']`) và hai thực thể có quy trình, một có `calendar: office` và `deadline: 4h`, một có `deadline: 1d` cùng lịch, một không có lịch:
    - Hồ sơ tạo 15:00 thứ Hai ở thực thể `4h` có `handle_by` 10:00 thứ Ba; thực thể `1d` có `handle_by` 15:00 thứ Ba; hồ sơ tạo chiều thứ Sáu hoặc ngày nghỉ tính từ lần mở cửa kế tiếp; thực thể không có lịch có `handle_by` đúng bằng lúc tạo cộng hạn.
    - Hành động đưa hồ sơ vào trạng thái chờ xử lý (như `start`) đặt lại `handle_by` theo lịch.
    - `dxos lint` và Xem kế hoạch báo lỗi theo dòng và cột cho lịch không khai, giờ chồng nhau, giờ kết thúc trước giờ bắt đầu, `days` rỗng, ngày nghỉ sai dạng hoặc trùng, và `calendar` không có `deadline`.
    - Đổi giờ của lịch đang dùng, hoặc bỏ `calendar` khỏi quy trình, ra `CHANGE_CALENDAR` ở mức `SAFE` và bị từ chối khi thực thể còn hồ sơ đang xử lý; một lịch không quy trình nào dùng mà đổi vẫn ra một dòng `CHANGE_CALENDAR`.
    - Một tệp `extends` khai lại `office` chỉ với `hours` giữ `days` của mẫu và thêm lịch mới theo tên.
    - Form quy trình (bước Thời hạn) ở tab Soạn và áp dụng có ô Lịch làm việc với các lịch đã khai, báo đỏ khi tên lịch chưa khai, và đủ ở tiếng Việt và tiếng Anh.
50. **Điều kiện nối nhiều phép:** áp dụng một mô hình có thực thể `loan` với `amount` (number), `kind` (choice `book`, `pen`, `Phần mềm`) và `note` (text), một hành động có `when: "amount > 100 and (kind == book or kind == pen)"` và `note` có `required_when: "amount > 100 and not kind == \"Phần mềm\""`:
    - Hồ sơ `amount` 500, `kind` book: hành động làm được, và `note` để trống bị API từ chối với `is required when amount > 100 and not kind == "Phần mềm"`.
    - Hồ sơ `amount` 500, `kind` Phần mềm: hành động bị 409 `CONDITION_NOT_MET`, và `note` để trống được chấp nhận.
    - Hồ sơ `amount` 50, `kind` book: hành động bị 409.
    - Điều kiện cũ một phép (`kind == Phần mềm`, không nháy) vẫn đọc được; `when: "amount > 1 and"` và ngoặc không đóng bị `dxos lint` từ chối kèm dòng, cột và chỗ sai; chín phép so sánh hoặc ngoặc lồng bốn cấp bị từ chối.
    - Áp dụng `required_when` nối nhiều phép vào thực thể đã có hồ sơ vi phạm thì kế hoạch bị từ chối và nêu số dòng vi phạm, đếm đúng theo điều kiện nối.
    - `GET /api/v1/meta/models/<tên>/entities/<thực thể>` và `x-dxos-required-when` của OpenAPI trả điều kiện nối dạng `{op: "and", conditions: [...]}`, điều kiện một phép vẫn `{field, op, value}`.
    - Ở web: nút hành động của hồ sơ không đạt điều kiện bị mờ kèm gợi ý "Chỉ thực hiện khi ... và (... hoặc ...)", ô `note` báo đỏ đúng lúc, và tab Soạn hiện điều kiện nối ở bảng hành động.
51. **`dxos connect`** (stack có `dxos-cli`, đã áp dụng `examples/order_approval/dxos.yaml`, `PUBLIC_HOST` còn `localhost`):
    - Trong thư mục `examples/order_approval` không có `.env`: `dxos connect order_approval --role REQUESTER --role APPROVER` ghi `.env` quyền `600` có `DXOS_BASE_URL=http://host.docker.internal:<cổng>` và bốn biến client, không in secret, và `dxos client test` đạt.
    - `docker compose run --rm app` in `filed order 1, status submitted` và `approved order 1, status approved`.
    - Chạy lại không đổi tệp (`Nothing new to write`).
    - Xóa dòng `DXOS_APPROVER_CLIENT_SECRET`: chạy lại bị từ chối và nói dùng `--rotate`; với `--rotate` thì có secret mới và `dxos client test` đạt.
    - Vai không có trong mô hình bị API từ chối và không ghi secret nào; `--app` quá dài bị từ chối; `--prefix DXOS_RETAIL --app shop` ghi `DXOS_RETAIL_<VAI>_CLIENT_*` và tệp thiếu dòng cuối vẫn được nối đúng dòng.
52. **Webhook tới ứng dụng cùng máy** (stack đầy đủ, một máy chủ nhận tạm nghe trên `0.0.0.0:<cổng>`, mô hình có `webhook: <tên>`):
    - Trong `deploy/webhooks/targets.yml` khai `<tên>` với `url: http://host.docker.internal:<cổng>/hook` và `secret`.
    - Một thay đổi hồ sơ làm máy chủ nhận được `POST` JSON kèm chữ ký.
    - `docker compose exec backend grep host.docker.internal /etc/hosts` trả một dòng địa chỉ.
53. **Ví dụ `appliance-store`** (stack đầy đủ, làm theo `examples/appliance-store/README.md` trên một thư mục chỉ có ứng dụng):
    - Chưa nối DX-OS: ứng dụng chạy, chi tiết đơn không có biểu mẫu Đổi trả và Bảo hành, `/claims` trả 404.
    - Sau khi áp dụng hai mô hình, đăng ký hai tài khoản máy, ghi `.env` và thêm `env_file`, `extra_hosts`: biểu mẫu hiện ra, phiếu đổi trả và bảo hành gửi được, quản lý duyệt thì mở lại trang ứng dụng thấy "Đã duyệt", phiếu bảo hành quá hạn 2 phút hiện "Quá hạn".
    - Sản phẩm chưa có ở DX-OS: ứng dụng báo "nhờ quản lý thêm vào mục Sản phẩm" và không tạo phiếu.
    - `./reset.sh --keep-dxos` xóa container, ảnh, dữ liệu và `.env` của ứng dụng, đưa `docker-compose.yml` về như cũ và gỡ hai tài khoản máy; `./reset.sh` còn gỡ DX-OS cùng dữ liệu.
    - Ba phép thử của mục "Vì sao không tự viết vòng duyệt": 403 khi tài khoản máy gọi `approve`, `dxos history verify` báo dòng bị sửa rồi nguyên vẹn sau khi khôi phục, `LOSS` cần `--accept LOSS` và đổi hạn bị từ chối khi còn phiếu mở.

### Trợ lý (tùy chọn)

48. Theo [`assistant/README.md`](../assistant/README.md), liên kết SSO, cấu hình inference/nguồn của bản cài và khai báo nhiệm vụ `ai.tasks`.
    Mở hồ sơ, tạo đề xuất, xem nguồn, sửa hoặc hủy, rồi xác nhận và đối chiếu phiên bản/lịch sử Core.
    Kiểm hai chủ không đọc/ghi chéo; giá trị trùng không tạo lịch sử sửa và model tắt không cản nghiệp vụ.
    Kiểm container trợ lý không ra được Internet và chỉ `assistant-egress` tới được endpoint inference; "Hủy liên kết" làm trợ lý không đọc được hồ sơ nữa và xóa phiên của Keycloak; `dist/rotate.js` giữ nguyên liên kết sau khi thay master key.
    Kiểm nâng cấp giữ liên kết SSO và lịch sử, không thực thi lại đề xuất cũ; sau khôi phục từ sao lưu, người dùng liên kết lại tài khoản.

## 7. Hồi quy

- Đăng nhập SSO qua Keycloak ở web hoạt động.
- `docker compose up -d --build` từ clone sạch lên đủ tám service healthy: `postgres`, `keycloak`, `seaweedfs`, `backend`, `frontend`, `portal`, `monitor`, `apisix`.
- Màn hình giám sát `/monitor/` mở được với tài khoản ADMIN.
- Không còn từ khóa của ứng dụng cũ:

```bash
grep -rIlw -i -E 'farm|lab|farmer|DXOS_APP|dx-host|app-farm|app-lab|dx-core-api' \
  --exclude-dir=node_modules --exclude-dir=target --exclude-dir=.git --exclude-dir=docs \
  --exclude-dir=dist .
```

## 8. Sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `release version 21 not supported` | Đặt `JAVA_HOME` về JDK 21 |
| Flyway báo "more than one migration with version 1" khi test | Chạy `./mvnw clean verify` |
| Keycloak không có vai `keycloak_app` | Thư mục dữ liệu Postgres đã có từ trước; dựng lại bằng `./reset.sh` để `deploy/postgres/init` chạy |
| `check_api.py` lỗi ở bước đầu | Cơ sở dữ liệu đã có mô hình; dựng lại stack rỗng |
| Gateway trả 429 giữa chừng khi chạy nhiều script | Giới hạn tốc độ của gateway; chờ một phút hoặc chạy cách nhau |
| Áp dụng mô hình trả 502 `ERR_ACCOUNT_SERVICE_DENIED` | Tài khoản dịch vụ `dxos-backend-admin` thiếu quyền hoặc scope `kc-admin-api`; xem [`sso/README.md`](../sso/README.md) |
| Kết quả không khớp mã vừa sửa | Image `dxos-backend:local` cũ hơn mã; chạy `docker compose build` rồi tạo lại service |
| Cổng 9080 hoặc 5432 bị chương trình ngoài stack chiếm (`dxos doctor` báo `FAIL`) | Đổi `APISIX_HOST_PORT`, `POSTGRES_HOST_PORT` trong `deploy/.env` hoặc dừng chương trình đó; cổng do container của stack giữ thì không phải lỗi |
