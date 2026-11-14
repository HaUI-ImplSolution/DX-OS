# Ma trận bằng chứng

Tài liệu này trả lời một câu hỏi: tính năng nào của lõi đã xong thật, căn cứ vào đâu.
Mỗi yêu cầu của đề và mỗi tính năng của lõi được xếp một mức, kèm cách kiểm, bước CI, lệnh chạy lại và giới hạn đã biết.
Cách chạy từng bài kiểm ở [`docs/TESTING.md`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/TESTING.md); tài liệu này không chép lại kết quả của từng lượt CI, xem tab Actions của repository.
Việc chưa làm và giới hạn chấp nhận ở [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md).

## Các mức

| Mức | Điều kiện |
|---|---|
| **Hoàn thiện** | Đủ cả bốn: (1) có kiểm tự động khẳng định đúng sai; (2) kiểm đó chạy trong CI trên pull request; (3) chạy lại được bằng một lệnh; (4) không còn giới hạn đã ghi nào chặn luồng chính |
| **Dùng được** | Chạy đầu-cuối, nhưng thiếu một trong (1) đến (3): kiểm chỉ chạy tay hoặc chỉ chạy khi bật thêm cấu hình, hoặc chưa kiểm trên bản phát hành thật |
| **Minh họa** | Có mã và có kiểm, nhưng chủ đích chỉ để chứng minh năng lực, bằng dữ liệu hoặc thiết bị giả lập |
| **Một phần** | Mới có một phần phạm vi đã định |
| **Chưa làm** | Chưa có mã trong repository |

## Bốn loại bằng chứng

| Loại | Chạy ở đâu | Có trong CI |
|---|---|---|
| Test đơn vị và tích hợp của backend | `cd backend && ./mvnw verify`; một số test dùng Testcontainers nên cần Docker | Có, job `Build and test the backend` của workflow `Backend` trên mọi pull request |
| Script kiểm trên stack thật | `python3 deploy/dev/check_<tên>.py` trên stack rỗng dựng bằng `docker compose up -d --build` | Có, job `API end-to-end` của workflow `Platform` trên mọi pull request |
| Kiểm bằng trình duyệt | Một kịch bản Playwright của bảng điều khiển ở `e2e/`; các màn hình còn lại kiểm tay theo `docs/TESTING.md`, mục 6 | Kịch bản Playwright có, job `Browser end-to-end` của workflow `Platform`; kiểm tay không |
| Bản triển khai công khai | VPS của đội | Không; hiện giữ bản `v1.1.1` nên chưa có bằng chứng loại này cho lõi mới |

Dữ liệu của hai loại đầu do chính các script tạo ra trên stack rỗng; chưa có tổ chức nào dùng lõi với dữ liệu thật.
Job `API end-to-end` của workflow `Platform` chia hai nhóm, mỗi nhóm một stack rỗng: `model and process` và `events and bootstrap`; script của từng nhóm khai ở `.github/workflows/ci-platform.yml`.
Mỗi script in `N/M passed` và thoát với mã 1 khi có kiểm hỏng; số kiểm của từng script xem ở kết quả của lượt CI.

## 1. Bốn dịch vụ bắt buộc của đề

| Yêu cầu | Tính năng của lõi | Kiểm | Lệnh | Mức | Giới hạn |
|---|---|---|---|---|---|
| Định danh và SSO | Keycloak; vai và đơn vị sinh từ mô hình; mỗi mô hình có client riêng nên hai mô hình dùng cùng tên vai không vào được nhau | `check_users.py` 41, `check_guards.py` 61, `check_conformance.py` 62 | `python3 deploy/dev/check_conformance.py` | Hoàn thiện | Đổi vai hoặc đơn vị có hiệu lực sau tối đa khoảng 5 phút vì đọc từ token đã ký |
| Cổng API | APISIX kiểm token, giới hạn tốc độ, định tuyến; route không kiểm token nằm trong danh sách công khai | `deploy/apisix/check_routes.py`; mọi script đi qua cổng 9080; `check_conformance.py` kiểm 401 khi thiếu token | `python3 deploy/apisix/check_routes.py` | Hoàn thiện | Giới hạn tốc độ chưa có kiểm riêng; lúc chạy `check_conformance.py` gặp 429 cho thấy giới hạn đang hoạt động |
| Dữ liệu có cấu trúc | Schema, ràng buộc và chỉ mục sinh từ mô hình trên PostgreSQL; bắt buộc, duy nhất, lựa chọn kiểm cả ở API lẫn cơ sở dữ liệu | `check_api.py` 51, `check_change.py` 65 | `python3 deploy/dev/check_api.py` | Hoàn thiện | Danh sách 10.000 dòng có lọc và sắp xếp đo ở phân vị 95 là 11 ms; chưa thử tải lớn hơn |
| Dữ liệu phi cấu trúc | Tệp đính kèm lưu ở SeaweedFS, siêu dữ liệu ở PostgreSQL, quét ClamAV, quyền theo quyền của hồ sơ | `check_process.py` (tải lên, tải về, gỡ), `check_demo.py` tiêu chí 3 (tệp EICAR bị chặn), job `Back up and restore` của workflow `Platform` (sao lưu và khôi phục kèm tệp) | `python3 deploy/dev/check_demo.py` | Dùng được | Quét mã độc bật mặc định; kiểm tệp EICAR chỉ chạy khi quét bật (`dxos av off` thì bỏ qua), và nhóm chế độ lõi của job `API end-to-end` trong workflow `Platform` đặt `DXOS_REQUIRE_SCAN=1` nên phần này nằm trong CI |
| Động cơ luồng công việc | Flowable nhúng, ba họ mẫu `approval`, `work_item`, `reservation`; trạng thái chỉ đổi qua quy trình, cùng giao dịch với việc ghi hồ sơ | `check_process.py` 69, `check_case.py` 76, `check_booking.py` 47, `check_recovery.py` 19 | `python3 deploy/dev/check_process.py` | Hoàn thiện | Họ phê duyệt có sẵn chỉ có một cấp; nhiều cấp tuần tự và điều kiện `when` khai báo được bằng `states` và `actions` của quy trình tự khai báo (kiểm tay trên stack tạm, chưa có kiểm tự động); không có trình thiết kế BPMN |
| Lõi không có giao diện | `dx-engine` chỉ phục vụ API; giao diện và portal là Tầng 2 | Mọi script chỉ gọi API; `ArchitectureTest` kiểm phụ thuộc giữa các lớp của `dx-engine`; `docs/TESTING.md` mục 7 | | Dùng được | Chưa có test khẳng định rằng dịch vụ lõi không trả trang nào |

## 2. Năng lực của lõi

| Tính năng | Kiểm | Lệnh | Mức | Giới hạn |
|---|---|---|---|---|
| Đọc và kiểm tệp mô hình, báo lỗi theo dòng và cột | `check_api.py` và `check_case.py` với bảy tệp sai mẫu ở `models/invalid/`; `check_cli.py` CLI-34 đến CLI-36 so `dxos lint` với `plan` | `python3 deploy/dev/check_api.py` | Hoàn thiện | |
| Mã hồ sơ đọc được theo khuôn khai báo, duy nhất khi ghi đồng thời | `check_codes.py` | `python3 deploy/dev/check_codes.py` | Hoàn thiện | Mỗi thực thể một trường mã; hết số thì từ chối hồ sơ mới |
| Mẫu in khai báo: PDF tiếng Việt theo quyền đọc hồ sơ | `check_documents.py` | `python3 deploy/dev/check_documents.py` | Hoàn thiện | Mẫu chỉ dùng trường của hồ sơ, chưa kéo dữ liệu qua quan hệ |
| Xem trước và áp dụng mô hình, áp dụng lại không đổi gì, sửa phần lệch của bảng và Keycloak | `check_api.py`, `check_recovery.py` 19 | `python3 deploy/dev/check_recovery.py` | Hoàn thiện | Việc kiểm lệch chỉ chạy khi xem kế hoạch hoặc áp dụng, không chạy lúc khởi động |
| Đổi mô hình khi đã có dữ liệu: mức rủi ro, đổi tên giữ dữ liệu, dòng vi phạm, xác nhận khi mất dữ liệu, quay về | `check_change.py` 65, `check_guards.py` 61 | `python3 deploy/dev/check_change.py` | Hoàn thiện | Đổi người xử lý, hạn xử lý, quyền tự quyết hoặc đặt chỗ chỉ được khi thực thể không còn hồ sơ đang xử lý |
| Tiếp tục hoặc hủy bỏ lần áp dụng dừng giữa chừng | `check_recovery.py`, `check_guards.py`; thẻ "Đang dừng dở" kiểm bằng Chrome | `python3 deploy/dev/check_guards.py` | Hoàn thiện | |
| API chung với quyền theo vai và theo dòng (`all`, `read`, `own`, `unit`, `assigned`, `owner_via`) | `check_api.py`, `check_case.py` | `python3 deploy/dev/check_case.py` | Hoàn thiện | Không có quyền theo từng trường |
| Chặn lách quy trình: không ghi được `status` qua API dữ liệu | `check_demo.py` tiêu chí 4, `check_conformance.py` | `python3 deploy/dev/check_conformance.py` | Hoàn thiện | |
| Quy tắc chung của quy trình: lý do khi từ chối hoặc trả lại, rút lại, người tạo không tự quyết, hạn và quá hạn | `check_process.py`, `check_case.py` | `python3 deploy/dev/check_process.py` | Hoàn thiện | |
| Đặt chỗ theo khung giờ có sức chứa | `check_booking.py` 47 | `python3 deploy/dev/check_booking.py` | Hoàn thiện | Kéo thả trên lịch kiểm tay bằng Chrome |
| Tự đóng hồ sơ đã xử lý của họ `work_item` | `check_process.py` | `python3 deploy/dev/check_process.py` | Hoàn thiện | |
| Hành động hoàn tất qua outbox, thử lại khi lỗi | `check_process.py`; `AdvisoryJobLockIntegrationTest` | `python3 deploy/dev/check_process.py` | Hoàn thiện | Chỉ có `add_to_group` và `remove_from_group` |
| Chống ghi đè khi sửa đồng thời (`version`, 409) | `check_process.py` | `python3 deploy/dev/check_process.py` | Hoàn thiện | |
| Mã lý do cho lỗi 403 và 409 | `check_case.py`, `check_process.py`, `check_booking.py` | `python3 deploy/dev/check_case.py` | Dùng được | `STILL_REFERENCED` và một số mã của 403 chưa có kiểm riêng |
| Lịch sử hồ sơ kèm giá trị cũ và mới của từng trường | `check_case.py` | `python3 deploy/dev/check_case.py` | Hoàn thiện | |
| Chuỗi hash của lịch sử, điểm neo, lệnh kiểm | `check_events.py` 24 | `python3 deploy/dev/check_events.py` | Hoàn thiện | Phát hiện sửa chứ không ngăn sửa; người sửa được cả cơ sở dữ liệu lẫn kho thì chỉ bị lộ khi so với hash bên nhận giữ |
| Webhook theo mô hình: chữ ký, thứ tự theo hồ sơ, thử lại, lấy lại phần đã lỡ | `check_events.py`, `WebhookSenderTest` | `python3 deploy/dev/check_events.py` | Hoàn thiện | Dạng `chat` đã thử với Mattermost Team Edition 11.7.15 ngày 13/11/2026, kiểm tay theo `docs/TESTING.md` mục 6, «Webhook `chat` với Mattermost thật»; chưa thử với Slack thật |
| Bên nhận so hash lịch sử (`witness.py`) | `check_events.py`; chạy dòng lệnh kiểm tay | `python3 examples/history-witness/witness.py compare` | Hoàn thiện | |
| Bộ kiểm chung cho mô hình mới | `check_conformance.py` 62; `check_cli.py` CLI-37 và CLI-38 chạy nó qua `dxos conformance` | `python3 deploy/dev/check_conformance.py` hoặc `dxos conformance <mô hình>` | Hoàn thiện | |
| Quản trị người dùng: gán vai và đơn vị, tạo, khóa, đặt lại mật khẩu, nhật ký | `check_users.py` 41, `KeycloakAdminClientTest`; tab Người dùng kiểm bằng Chrome | `python3 deploy/dev/check_users.py` | Hoàn thiện | Thay đổi làm thẳng ở Keycloak Admin Console không qua đây |
| Thống kê theo thực thể, kể cả thời gian xử lý | `check_process.py` | `python3 deploy/dev/check_process.py` | Hoàn thiện | Chưa có tỷ lệ hoàn tất trong hạn |
| Báo cáo theo trường (`GET /api/v1/data/{model}/{entity}/report`, trang Thống kê) | `ReportQueryTest`, `ReportServiceTest` (Postgres thật); kiểm tay theo `docs/TESTING.md` mục 6, «Thống kê»; `check_docs.py` kiểm đường dẫn có trong OpenAPI | Mở trang Thống kê của một thực thể có quy trình, mục Báo cáo theo trường | Dùng được | Một trường nhóm mỗi lần, đếm hoặc tính trên trường số; không có kiểm tự động trên stack; trang chỉ có cho thực thể có quy trình, thực thể khác dùng API |
| Tài liệu khớp với API thật | `check_docs.py` 3 | `python3 deploy/dev/check_docs.py` | Hoàn thiện | |
| Chịu tải đồng thời và cấu hình nhỏ nhất | `deploy/dev/load_test.py` trên stack tạm giới hạn 12, 8, 4 và 2 nhân; kết quả ở `docs/SIZING.md` | `python3 deploy/dev/load_test.py --levels 1,10,25,50,75,100,200 --reset` | Dùng được | Chạy tay, không trong CI; số là cận dưới vì bộ tạo tải cùng máy và giới hạn tốc độ tắt khi đo; chưa đo 1 nhân, backend dưới 768 MB RAM và bảng trên 10.000 hồ sơ |
| Lõi không trả Swagger UI, Admin Console mặc định đóng và mở được bằng `KEYCLOAK_ADMIN_CONSOLE=true` | `check_surface.py` 7; job `Gateway surface` của workflow `Platform`, hai chế độ | `python3 deploy/dev/check_surface.py` | Dùng được | Chưa có kết quả của job này trên pull request |
| Chạy riêng Tầng 1 (`dxos start --core`) | Nhóm `core mode` của job `API end-to-end` trong workflow `Platform` | `dxos start --core` | Dùng được | Chưa có kết quả của job này trên pull request; chế độ lõi không có bảng điều khiển, nên không có kiểm giao diện |
| Nạp mô hình lúc khởi động (`dxos start <tệp>`) | `check_bootstrap.py` 9; job `Install from the release archive` của workflow `Platform` | `python3 deploy/dev/check_bootstrap.py` | Dùng được | Chưa kiểm bằng image phát hành thật của bản `2.0.0` |
| Cài một lệnh, nâng cấp, gói sai SHA-256 bị từ chối | Job `Install from the release archive` của workflow `Platform` | `curl -fsSL .../dxos-install.sh \| bash` | Dùng được | Chưa chạy trên máy sạch với image phát hành thật |
| Sao lưu và khôi phục kèm tệp đính kèm | Job `Back up and restore` của workflow `Platform` | `dxos backup` | Hoàn thiện | Một bản backend, một máy |
| Số đo vận hành cho Prometheus (`/actuator/prometheus`) | `OperationalMetricsTest` (PostgreSQL thật): số khớp dữ liệu, tên số đo và nhãn là một tập cố định; kiểm tay theo `docs/TESTING.md` mục 6, «Số đo Prometheus» | `cd backend && ./mvnw verify` | Dùng được | Chưa thử với Prometheus hay Grafana thật; lần áp dụng thất bại và hàng đợi tồn chỉ kiểm bằng dữ liệu giả; một bản backend |
| Màn hình giám sát và cảnh báo | `ServiceProbeTest` 4, `AlertJobTest` | `cd backend && ./mvnw verify` | Dùng được | Không có kiểm đầu-cuối của giao diện giám sát |
| Bảng điều khiển và giao diện react-admin | `npx tsc --noEmit` trong workflow `Frontend`; `e2e/tests/console.spec.ts` (quản trị viên đăng nhập, áp dụng một mẫu, thấy mô hình chạy) trong job `Browser end-to-end` của workflow `Platform`; còn lại kiểm tay bằng Chrome | `cd e2e && npx playwright test` trên stack đang chạy | Dùng được | Tự động hóa mới có luồng bảng điều khiển; hộp việc, lịch đặt chỗ và trang hồ sơ kiểm tay |
| Ví dụ kết nối thiết bị (`itot-simulator`) | Kiểm tay: gửi lại cùng quan sát in `duplicate`, không tạo thêm hồ sơ | `python3 examples/itot-simulator/simulator.py run` | Minh họa | Thiết bị và dữ liệu giả lập |
| Hộp việc thời gian thực (`GET /api/v1/inbox/stream`) | Kiểm tay theo `docs/TESTING.md` mục 6, «Hộp việc thời gian thực»; `mvnw verify` kiểm biên dịch và test của dịch vụ | Mở hộp việc ở hai cửa sổ rồi nộp một hồ sơ | Dùng được | Không có kiểm tự động của kênh; chạy một bản backend nên kênh chỉ báo thay đổi do chính bản đó ghi |
| Tài khoản và hồ sơ mẫu để dùng thử (`dxos demo`) | Kiểm tay theo `docs/TESTING.md` mục 6, «`dxos demo`» | `dxos demo` | Dùng được | Không có kiểm tự động; hồ sơ quá hạn chỉ có sau ba ngày kể từ lần nộp |
| Kiểu `boolean`, `date`, nhiều lựa chọn, giới hạn `min` `max` `pattern`, bắt buộc có điều kiện | Kiểm tay theo `docs/TESTING.md` mục 6, «Kiểu và quy tắc trên biểu mẫu»; `check_conformance.py` và `check_booking.py` áp mọi mẫu, kể cả Sổ tài sản | Áp dụng mẫu Sổ tài sản rồi ghi giá trị đúng và sai qua API | Dùng được | Không có kiểm tự động riêng cho các kiểu mới; `CHECK` của cơ sở dữ liệu kiểm tay bằng chèn thẳng vào bảng |
| Tài liệu OpenAPI theo mô hình | Kiểm tay theo `docs/TESTING.md` mục 6, «OpenAPI của mô hình»; `check_docs.py` kiểm đường dẫn của tài liệu có trong OpenAPI chung | `GET /api/v1/meta/models/{model}/openapi` | Dùng được | Chưa thử trong một trình sinh thư viện cụ thể; đã nhập qua bộ kiểm cú pháp OpenAPI |
| Ứng dụng ngoài (`dxos client`, tab Ứng dụng ngoài) | Kiểm tay theo `docs/TESTING.md` mục 6, «Ứng dụng ngoài» và «`dxos client add`, `list`, `role`, `rotate`, `remove`» | `dxos client add` | Dùng được | Không có kiểm tự động; mỗi ứng dụng giữ đúng một vai của một mô hình, đổi vai bằng `dxos client role` hoặc nút Đổi vai mà giữ nguyên mã bí mật |
| Giao diện ở tên miền khác (`dxos client browser`, `CORS_ALLOWED_ORIGINS`) | `CorsOriginsTest`; `check_browser.py` BROWSER-01 đến BROWSER-07 (đăng ký client, cấu hình PKCE, preflight CORS, redirect không an toàn bị từ chối, đăng nhập PKCE rồi gọi API, xóa client) ở nhóm `model and process` và `core mode` của job `API end-to-end` | `python3 deploy/dev/check_browser.py` | Dùng được | Script Python thay trình duyệt, chưa thử một giao diện ngoài thật; CORS mặc định chỉ nhận cổng 5173 của `localhost` và `127.0.0.1` |
| Route riêng của bản cài (`deploy/apisix/local.yaml`) | Job `Validate the gateway routes` chạy `check_routes.py` với cấu hình lõi và mẫu `local.example.yaml`, và từ chối sáu biến thể sai (id trùng, đường dẫn giao với route lõi, upstream hay plugin thiếu, method sai, thiếu kiểm token); job `Install from the release archive` kiểm `local.yaml` giữ nguyên khi nâng cấp | `python3 deploy/apisix/check_routes.py --local deploy/apisix/local.example.yaml` | Dùng được | Chưa kiểm một dịch vụ riêng thật đứng sau route; local là một tài liệu YAML tối đa 1 MiB và chỉ nhận tập matcher nêu ở `docs/EXTENSIONS.md` |
| GitOps kéo hạ tầng và mô hình (`dxos gitops`, `dxos plan --check`) | Kiểm tay theo `docs/TESTING.md` mục 6, «`dxos gitops`, nguồn mô hình», «`dxos gitops`, nguồn hạ tầng» và «`dxos gitops`, chung hai nguồn»: ba chế độ, chữ ký commit, đổi tag image, nâng cấp hạ tầng trên bản cài thử, thử lại khi `update.sh` lỗi | `dxos gitops sync`, `dxos gitops status`, `dxos plan --check <tệp>` | Dùng được | Chưa có script trong CI; nâng cấp hạ tầng chưa chạy tự động qua `update.sh` trong CI; mô hình chỉ đọc một thư mục, không đệ quy |
| Leo thang khi quá hạn (`escalate`) | `EscalationSweepTest` (Postgres thật): thứ tự các lần báo, chạy bù sau gián đoạn chỉ ghi lần mới nhất, không gửi lại lần đã gửi, kết quả không đổi theo bốn múi giờ | `cd backend && ./mvnw verify` | Dùng được | Chưa có script trong CI chạy cả đường từ hồ sơ quá hạn đến webhook; chỉ báo qua webhook của mô hình, nhận theo vai |
| Danh mục dữ liệu (`GET /api/v1/models/{model}/data-catalog`, tab Danh mục dữ liệu) | Kiểm tay theo `docs/TESTING.md` mục 6, «Danh mục dữ liệu»; `check_docs.py` kiểm đường dẫn có trong OpenAPI | Mở tab Danh mục dữ liệu bằng ADMIN, chọn một mô hình có quy trình | Dùng được | Không có kiểm tự động; chỉ ADMIN xem được |
| Phả hệ từng trường (`GET /api/v1/models/{model}/data-lineage`, tab Danh mục dữ liệu) | `LineageServiceTest`; kiểm tay theo `docs/TESTING.md` mục 6, «Danh mục dữ liệu»; `check_docs.py` kiểm đường dẫn có trong OpenAPI | Mở một thực thể trong tab Danh mục dữ liệu bằng ADMIN, mục Nguồn gốc của từng trường | Dùng được | Dựng từ tệp mô hình nên chỉ nêu cái mô hình khai; không có bước biến đổi vì lõi giữ nguyên giá trị; chỉ ADMIN xem được |
| Số đo chất lượng dữ liệu (`GET /api/v1/data/{model}/{entity}/quality`, tab Danh mục dữ liệu) | `QualityMetricsTest` (Postgres thật), `check_process.py` QL-01 đến QL-10; kiểm tay theo `docs/TESTING.md` mục 6, «Danh mục dữ liệu»; `check_docs.py` kiểm đường dẫn có trong OpenAPI | Mở một thực thể trong tab Danh mục dữ liệu bằng ADMIN | Dùng được | Đếm trực tiếp trên bảng và bị cắt sau 10 giây; chỉ có bốn số đo, độ hợp lệ của khóa ngoài và của trường `user` chưa đo |
| Lưu trữ và truy vấn hồ sơ đã đóng trong kho đối tượng | `check_cli.py` CLI-19 đến CLI-21 và CLI-26 đến CLI-33; kiểm tay theo `docs/TESTING.md` mục 6, «Lưu trữ theo lịch» | `dxos archive <mô hình> --store`, `dxos archive query <mô hình>/<lần chạy>`, `dxos archive schedule install` | Dùng được | Lần chạy đầu cần mạng để DuckDB tải extension; truy vấn giới hạn trong một lần chạy; timer là systemd user timer, máy không có systemd thì dùng cron |
| Khối `ui:` và hành động hàng loạt | Kiểm tay theo `docs/TESTING.md` mục 6, «Khối `ui:`» và «Hành động hàng loạt»; `npx tsc --noEmit` | Mở danh sách và biểu mẫu của Sổ tài sản; chọn nhiều hồ sơ rồi Duyệt | Dùng được | Không có kiểm tự động của giao diện cho hai phần này |
| Bảng theo trạng thái và xuất CSV | Kiểm tay theo `docs/TESTING.md` mục 6, «Bảng theo trạng thái» và «Xuất CSV»; `npx tsc --noEmit` | Mở danh sách của một thực thể có quy trình, bấm Bảng theo trạng thái và Xuất CSV | Dùng được | Không có kiểm tự động của giao diện; bảng chỉ đọc, tối đa 10 thẻ mỗi cột; xuất tối đa 10.000 dòng |
| Khóa chống chạy lặp (`Idempotency-Key`) | Kiểm tay theo `docs/TESTING.md` mục 6, «Khóa chống chạy lặp» | Gửi hai lần cùng khóa | Dùng được | Không có kiểm tự động; chỉ cho tạo hồ sơ và hành động, khóa giữ 24 giờ |
| Chỉ số mục tiêu: mã tự viết bằng 0 ở mức 1 và 2, dựng dưới 10 phút, đổi trường không mất dữ liệu | Đếm bằng script và đo tay, kết quả ở `docs/TARGETS.md` | `python3 deploy/dev/count_own_code.py <thư mục>`; `dxos start <tệp>` | Dùng được | Không chạy trong CI; mục "trong một buổi" mới có bản mô phỏng, chưa có người thật |

## 3. Giấy phép, chuỗi cung ứng, phát hành

| Tính năng | Kiểm | Lệnh | Mức | Giới hạn |
|---|---|---|---|---|
| Header SPDX ở mọi tệp mã, giấy phép của mọi gói trong SBOM, danh mục image và mô hình | Job `Check licenses` của workflow `Compliance` | `python3 .github/scripts/check_licenses.py` | Hoàn thiện | Ngoại lệ có lý do ở `.github/dependency-exceptions.json` |
| Giấy phép của image, bản tải về, gói Maven và npm ở đúng phiên bản đang dùng hoặc được đề xuất (Dependabot) | Jobs `Check upstream licenses`, `Check SBOMs against lockfiles` và `Scan the backend SBOM`; `python3 -m unittest discover -s .github/scripts` | `GITHUB_TOKEN=... python3 .github/scripts/check_upstream_licenses.py` | Hoàn thiện | Giấy phép GitHub không nhận ra phải có `text` hoặc `unverified` trong danh mục; Eclipse Temurin và Alpine chỉ được ghi chú |
| Quét bí mật trong cây mã và lịch sử | Job `Scan for secrets` của workflow `Compliance` (gitleaks) | | Hoàn thiện | |
| Lỗ hổng của dependency và image | workflow `Dependencies` (Trivy, SBOM của Maven), chạy theo lịch | | Hoàn thiện | Image bên thứ ba chỉ báo cáo, không chặn |
| Gói phát hành `.tar.gz`, `.sha256`, SBOM CycloneDX | Job `Install from the release archive` của workflow `Platform` dựng gói của commit và chạy `check_archive.sh` ở mọi pull request; workflow `Release Package` chạy lại kiểm đó rồi mới đăng khi đẩy tag `v*` | `.github/scripts/check_archive.sh <gói> <phiên bản>` | Dùng được | Bản đã phát hành gần nhất là `v2.0.0`; việc gộp vào `Khuong/main` sau đó chưa có trong tag nào, cần một bản phát hành mới trước khi nộp |
| SBOM khớp với tệp khóa | Job `Check SBOMs against lockfiles` của workflow `Compliance` dựng lại SBOM của `frontend` và `website` rồi so với `sbom/` | `python3 .github/scripts/check_sbom_fresh.py` | Hoàn thiện | SBOM của backend so bằng `check_sbom_fresh.py` với kết quả của `cyclonedx-maven-plugin`, chạy tay ngày 06/11/2026: 221 gói, 0 khác biệt |
| Tệp bắt buộc có đủ, shell script đúng cú pháp và chạy được | Job `Check required files and scripts` của workflow `Compliance` | | Hoàn thiện | |
| Workflow đúng cú pháp | Job `Lint workflows` của workflow `Compliance` | | Hoàn thiện | |
| Image theo phiên bản trên GHCR | Workflow `Release Images` | | Dùng được | Image `v2.0.0` đã đăng, `dxos start` kéo về chạy được |
| Compose dừng khi thiếu bí mật, tệp mẫu hợp lệ | Job `Validate the Compose file` của workflow `Platform` | `docker compose config` | Hoàn thiện | |

## 4. Chưa làm

| Nội dung | Mức | Ghi chú |
|---|---|---|
| Hỏi đáp sinh câu trả lời và tác tử AI có người duyệt | Chưa làm | Cần mô hình ngôn ngữ đạt tiêu chuẩn mở hoàn toàn, chưa chọn |
| Ứng dụng Tầng 2 phủ chữ I của H-P-D-I | Chưa làm | |
| Lớp truy vấn riêng trên kho Parquet | Chưa làm | Xem [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md), mục 3 |
| Bản sao số | Chưa làm | Giới hạn chấp nhận, xem [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md), mục 4 |
| Nhiều bản backend sau gateway | Chưa làm | Chạy một bản; hai bản thì danh sách mô hình giữ lệch nhau sau một lần áp dụng |
| Thao tác hoàn tất gọi ra hệ thống ngoài | Chưa làm | Ghi ở giới hạn chấp nhận của Việc tồn đọng: webhook của mô hình đã gửi trạng thái cũ, mới và hành động |
| Người thật thử dựng hệ thống đầu tiên | Chưa làm | Bài thử và bản mô phỏng ở `docs/TARGETS.md`; chưa có người quan sát |

## 5. Kiểm lại

Chạy ở gốc repository, trên stack rỗng:

```bash
deploy/make_env.sh
cd deploy && docker compose up -d --build && cd ..
for c in api recovery case change process booking demo guards users docs conformance; do python3 deploy/dev/check_$c.py; done
python3 deploy/dev/check_events.py
python3 deploy/dev/check_bootstrap.py
```

`check_events.py` và `check_bootstrap.py` khởi động lại backend nhiều lần nên chạy ở stack riêng, như hai nhóm của job `API end-to-end` của workflow `Platform`.
Kiểm tĩnh:

```bash
cd backend && ./mvnw verify
cd frontend && npx tsc --noEmit
python3 .github/scripts/check_licenses.py
python3 deploy/apisix/check_routes.py
```

Một tính năng đổi mức thì sửa dòng của nó trong bảng ở cùng pull request với thay đổi đó.
