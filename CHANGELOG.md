# Changelog

Nhật ký thay đổi của DX-OS.
Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/).

Mỗi phiên bản có một mục tóm tắt các điểm cốt lõi; mục Chưa phát hành gom những gì sau phiên bản mới nhất.

## [Unreleased]

### Lõi chạy ngầm

- API danh sách, `stats`, `report` và `quality` nhận `q`: mỗi từ phải có, không phân biệt hoa thường và dấu, trong ít nhất một trường chữ, mã hoặc lựa chọn đơn của hồ sơ; điều kiện nằm cùng câu SQL với quyền theo dòng nên không trả hồ sơ ngoài quyền, và truy vấn có `q` bị cắt sau 10 giây.
- Cầu nối MQTT tùy chọn (`deploy/docker-compose.mqtt.yml`, bật bằng `COMPOSE_FILE`): một broker Mosquitto có mật khẩu và một cầu nối nhận bản tin theo chủ đề khai báo rồi tạo hồ sơ qua API dữ liệu chung bằng client máy; bản tin giao hai lần chỉ tạo một hồ sơ, bản tin bị từ chối hoặc sai khuôn được ghi log và bỏ qua, và lõi ngừng thì bản tin được giữ ở broker đến khi xử lý. `examples/itot-simulator` có `--transport mqtt`.
- Prometheus và Grafana tùy chọn (`deploy/docker-compose.grafana.yml`, bật bằng `COMPOSE_FILE`): Prometheus đọc số đo `dxos_*` của lõi bằng `METRICS_PASSWORD`, Grafana có sẵn nguồn dữ liệu và dashboard "DX-OS" về hồ sơ, hạn xử lý, hàng đợi sự kiện, lần áp dụng mô hình và dịch vụ lõi; Grafana mở ở `127.0.0.1`, đòi mật khẩu quản trị và không tải plugin.
- Người dùng đóng hoặc tải lại trang không còn bị ghi thành lỗi server: luồng SSE của Hộp việc ngắt kết nối thì không có ERROR trong nhật ký và không có bản ghi audit `SERVER_ERROR`.
- `dxos plan` in kế hoạch bằng chữ: mức rủi ro, mỗi thay đổi một dòng, hồ sơ đang xử lý, tác động lên hành động, dòng vi phạm và lệnh `dxos apply --accept` cần dùng; `--json` in JSON đầy đủ như trước.
- `dxos apply` in tên mô hình, phiên bản và từng bước đã chạy bằng chữ, in thông báo máy chủ khi áp dụng lỗi và in phản hồi JSON đầy đủ với `--json`.
- `stats` của thực thể có quy trình trả `timeInStatus`: mỗi trạng thái có số lượt vào, thời gian trung bình và lâu nhất, sắp theo trung bình giảm dần.
  Mỗi lần vào lại tính một lượt, lượt đang mở tính đến `computedAt`, trạng thái cuối không tính; cùng quyền theo dòng, bộ lọc và mức cắt 10 giây như các số khác.
- Tệp mô hình có khóa viết hai lần trong cùng một khối bị `dxos lint`, `dxos plan` và áp dụng từ chối, kèm dòng của lần viết thứ hai; mô hình đã lưu có khóa trùng vẫn chạy và hiện cảnh báo.
- `dxos import <mô hình> <thực thể> <tệp.csv> --client TÊN` tạo mỗi dòng của tệp CSV thành một hồ sơ qua API dữ liệu chung, nên mỗi dòng đi qua quyền, ràng buộc, quy tắc và quy trình của thực thể; dòng bị từ chối được báo kèm số dòng và lý do, gửi lại cùng tệp không tạo trùng trong 24 giờ, và `--dry-run` kiểm tệp mà không tạo gì.

### Giấy phép

- `docs/DEPENDENCY_INVENTORY.md` liệt kê từng gói dự án dùng ở bất kỳ bước nào (npm kể cả gói dev, Maven kể cả test và plugin build, GitHub Actions, image, công cụ, phông, model), mỗi gói có phiên bản và giấy phép của đúng phiên bản đó; `.github/scripts/check_license_inventory.py` sinh mục này và `check` báo đỏ ở mỗi pull request khi thiếu gói hoặc có giấy phép ngoài danh sách OSI (`.github/licenses/osi-approved.json`) mà chưa có lý do.
- Mỗi image do dự án phát hành (`backend`, `monitor`, `frontend`, `portal`, `sso`, `assistant`, `clamav`) kèm `LICENSE`, `NOTICE` và văn bản giấy phép của thư viện nó chứa ở `/licenses`; image `backend` có thêm giấy phép của `pgschema` và các mô-đun Go của nó.
- Image `sso` kèm danh sách và văn bản giấy phép của 438 thư viện trong bản Keycloak (`THIRD-PARTY-LICENSES-keycloak.txt`), image `clamav` kèm giấy phép GPL-2.0 của ClamAV và của các phần bên thứ ba dựng sẵn trong nó, và mỗi image kèm `SOURCES.txt` nêu nơi lấy nguồn của gói hệ điều hành và thành phần GPL của image nền; các thư viện đó đều có trong `docs/DEPENDENCY_INVENTORY.md`.
- Quét mã độc chạy bằng image `clamav` của dự án, dựng trên image ClamAV chính thức và bỏ `libclamunrar` (giấy phép UnRAR, không phải OSI); backend từ chối tệp tải lên có chữ ký RAR dù tên tệp đổi đuôi, vì ClamAV không mở được RAR.
- Image `frontend`, `portal` dùng `nginx:1.30-alpine-slim`, image `assistant` và ứng dụng ví dụ `appliance-store` dùng `node:24.16.0-alpine`, ví dụ `order_approval` dùng `python:3.12-alpine`; số gói ngoài OSI trong các image này giảm rõ (xem `docs/IMAGE_PACKAGES.md`).
- `docs/IMAGE_PACKAGES.md` liệt kê gói bên trong các image bên thứ ba mà stack chạy, kèm giấy phép từng gói và cột Ngoài OSI; `check_image_packages.py` kiểm đủ bảng ở mỗi pull request và đọc lại image hằng tuần.
- `LICENSE` và `NOTICE` đi kèm GitHub Release cùng `dxos-install.sh` và `dxos-lint.jar`; SBOM của web và trang docs khai Apache-2.0 cho gói gốc; README của mỗi ví dụ nêu giấy phép; `check_licenses.py` kiểm mọi nơi dự án khai giấy phép (`package.json`, `pom.xml`, `CITATION.cff`, chân trang trang docs) đều là Apache-2.0.
- `dxos-lint.jar` kèm `LICENSE`, `NOTICE` và giấy phép của SnakeYAML ở `META-INF`; `pom.xml` và `package.json` khai Apache-2.0; phông Codicons (CC-BY-4.0) trong bản build web có ghi công ở `third-party-licenses.txt`.
- Mọi câu về giấy phép ở README, NOTICE, `DEPENDENCIES.txt` và trang docs nêu cùng danh sách ngoại lệ ngoài OSI.

### Giao diện

- Bản build web không còn kèm các dịch vụ ngôn ngữ TypeScript, HTML, CSS và JSON của Monaco mà trình soạn YAML không dùng, nên nhỏ đi hơn một nửa (từ 18 MB còn 8,4 MB).
- Mở một mẫu hoặc một mô hình ở bảng điều khiển khi bản đang soạn có thay đổi chưa áp dụng thì hỏi trước khi thay; chọn Hủy giữ nguyên bản đang soạn.
- Web có test đơn vị bằng Vitest (`npm test` trong `frontend/`), chạy ở workflow `Frontend`.
- Trình dựng mô hình có thêm hai trang cho mỗi thực thể: Giao diện và Mẫu in.
  Giao diện sửa biểu tượng, nhóm và thứ tự trong menu, cột và cách sắp xếp của danh sách, nhóm trường trên form và gợi ý của từng trường.
  Mẫu in sửa tên mẫu, nhãn, tên tệp, tiêu đề, các trạng thái được in và các khối nội dung (tiêu đề, văn bản, bảng trường, dòng ký, đổi được thứ tự).
  Lỗi của Core thuộc `ui` hoặc `documents` hiện ở đúng trang đó, và mỗi trang có hướng dẫn về những gì khối nhận.
  Lỗi của Core về hai khối này hiện bằng tiếng Việt khi giao diện ở tiếng Việt.
- Đổi tên một trường hay một vai trò trong trình dựng mô hình đổi theo mọi chỗ đang dùng tên đó, đúng các chỗ hộp xác nhận khi xóa liệt kê, nên Xem kế hoạch không còn báo tham chiếu tới tên cũ.
  Với trường: `required_when` của trường khác, `require` và `when` của hành động, cột, cách sắp xếp, nhóm trường và gợi ý trong `ui`, và `file`, `title`, `text`, `fields` và `signatures` của mẫu in; với vai trò: `role` của hành động và `escalate.to`.
- Thông báo lỗi của trợ lý nghiệp vụ hiện bằng tiếng Anh khi giao diện ở tiếng Anh.
- Danh sách hồ sơ có ô "Tìm theo chữ" ở đầu bộ lọc; nó thay ô lọc riêng của trường mã, vì mã cũng được tìm.
- Trang Thống kê có thẻ "Thời gian trung bình ở từng trạng thái", đánh dấu trạng thái giữ hồ sơ lâu nhất; bấm một dòng mở danh sách hồ sơ ở trạng thái đó.
- Hộp việc trống có nút tạo hồ sơ cho từng loại hồ sơ người dùng được tạo.
- Duyệt, từ chối, yêu cầu bổ sung, chuyển tiếp, rút lại, nhận việc, giao việc và thao tác hàng loạt gửi kèm phiên bản của hồ sơ và của mô hình mà màn hình đã đọc, nên không ai quyết định trên nội dung đã đổi mà họ chưa thấy.
  Khi hồ sơ đã đổi, màn hình báo "Hồ sơ vừa được thay đổi, hãy xem lại." và đọc lại hồ sơ; khi mô hình đã đổi, màn hình báo hãy tải lại trang.
- Kế hoạch đổi mô hình hiện "Hồ sơ hiện có được cấp mã" bằng tiếng Việt khi thêm một trường kiểu `code`.
- Hủy hộp thoại nhập lý do, của một hồ sơ hay của thao tác hàng loạt, thì ghi chú vừa gõ bị xóa, nên không lọt sang thao tác khác; gửi lỗi thì ghi chú được giữ để gửi lại.
- Thẻ danh sách trên điện thoại hiện số và ngày theo ngôn ngữ đang chọn như trang chi tiết, ví dụ `1.500.000` và `20/11/2026`; trang chi tiết cũng định dạng số theo ngôn ngữ đang chọn.
- Chế độ Điền form của trình soạn mô hình là một trình dựng mô hình: cây cấu trúc của mô hình, trường dữ liệu dạng bảng có ô tìm, ma trận quyền vai trò × thực thể, quy trình chia Tổng quan, Trạng thái, Hành động, Thời hạn và Tự động hóa, và bấm trạng thái hay mũi tên trên sơ đồ thì mở chỗ sửa nó.
- Trình dựng mô hình hỏi lại trước khi xóa trường, vai trò hay thực thể đang được dùng; lỗi Core chặn áp dụng và cảnh báo của form được tách màu, đánh dấu trên cây và liệt kê kèm liên kết tới chỗ sửa; cạnh tên tệp có nhãn khi còn thay đổi chưa áp dụng.
- Ở chế độ form, "Viết được gì trong tệp" nằm dưới khung chính và chỉ nêu phần đang sửa; biểu tượng xóa màu đỏ.
- Nhãn của form theo nghiệp vụ, ở cả tiếng Việt và tiếng Anh: Trường dữ liệu, Vai trò, Quyền truy cập, Mẫu quy trình, Kế thừa quyền từ hồ sơ liên quan, Kết nối gửi sự kiện, Nhóm người dùng (Keycloak).
- Bảng kế hoạch có nhãn tiếng Việt và tiếng Anh cho định dạng mã, mẫu chuỗi, điều kiện bắt buộc nhập, cách hiển thị và tài liệu in, và cho các quy tắc bắt buộc có điều kiện, số phần tử, giá trị nhỏ nhất, giá trị lớn nhất và mẫu chuỗi ở dòng vi phạm.
- Form sửa và form tạo chỉ gửi các trường mô hình khai báo cho thực thể, nên `webUrl` và các khóa khác API thêm vào hồ sơ không còn bị gửi lại và làm lưu hồ sơ bị từ chối.
  Khi máy chủ trả 400 mà không lỗi nào thuộc trường trên form, form hiện nguyên thông báo của máy chủ.
- Lịch sử xử lý của hồ sơ tách đang tải, chưa có hoạt động và tải lỗi kèm nút "Thử lại"; dòng thêm tệp và gỡ tệp hiện tên tệp.

## [2.1.0] - 2026-11-26

Thêm và sửa sau bản `2.0.2`: dự án gom mô hình và ứng dụng ngoài kèm quản trị theo dự án, `dxos connect` và `webUrl` để nối ứng dụng ngoài, ứng dụng mẫu `appliance-store`, kế hoạch đổi mô hình báo tác động lên hồ sơ đang chờ, ghi có điều kiện theo phiên bản, và trợ lý nghiệp vụ tùy chọn.
Nâng cấp từ `2.0.2` giữ nguyên dữ liệu; hai migration mới thêm bảng dự án và thành viên dự án.

### Lõi chạy ngầm

- Kế hoạch đổi mô hình nêu hành động nào đổi (`CHANGE_ACTIONS` ghi nhãn, vai, `needs_note`, `check` và `require` thêm hoặc bỏ), và thêm `impacts`: với mỗi hành động mới đòi thêm trường bằng `require`, số hồ sơ đang ở trạng thái nguồn còn thiếu trường đó.
  Mức vẫn `SAFE`; hành động từ chối các hồ sơ đó đến khi trường được bổ sung. `dxos plan` in `impacts` cùng phần còn lại của kế hoạch, và bảng điều khiển hiện một dải cảnh báo cho mỗi hành động.
- `dxos start` và `dxos doctor` phân biệt cổng do chính stack giữ với cổng do chương trình ngoài giữ.
  Stack chạy dở (PostgreSQL chạy, APISIX chưa có) thì `dxos start` dựng nốt các dịch vụ còn thiếu; cổng do chương trình ngoài giữ vẫn báo `FAIL`, nêu rõ `dxos stop` không giải phóng được và nên đổi khóa nào trong `deploy/.env`.
- Dự án gom mô hình và ứng dụng ngoài của một hệ thống tích hợp để lọc và xem phạm vi ảnh hưởng; dự án không cấp quyền, không cô lập dữ liệu và không đổi vai `dxos-m-<mô hình>`.
  Một mô hình và một ứng dụng ngoài mỗi bên thuộc tối đa một dự án, ứng dụng có dự án riêng của nó nên ứng dụng của dự án này gọi được mô hình của dự án khác, và bên chưa khai nằm ở nhóm chưa phân loại.
  Dự án nằm ngoài tệp mô hình, nên đổi dự án không tạo phiên bản mới.
- `GET`, `POST /api/v1/projects`, `PUT`, `DELETE /api/v1/projects/{name}` quản lý dự án (ADMIN); xóa dự án còn mô hình hoặc ứng dụng trả 409.
  `PUT /api/v1/models/{model}/project` và `PUT /api/v1/clients/{name}/project` đưa một mô hình hoặc ứng dụng vào dự án, `null` đưa ra khỏi dự án; `POST /api/v1/clients` nhận `project` tùy chọn.
  `GET /api/v1/models`, `GET /api/v1/clients` và danh mục dữ liệu trả `project`, `null` khi chưa phân loại.
  Nhật ký quản trị ghi `PROJECT_CREATED`, `PROJECT_UPDATED`, `PROJECT_DELETED`, `MODEL_PROJECT_CHANGED` và `CLIENT_PROJECT_CHANGED`.
- Bảng điều khiển có tab Dự án, bộ lọc dự án ở tab Mô hình đang chạy và Ứng dụng ngoài, ô chọn dự án ngay trên từng mô hình và ứng dụng, nhãn "Dùng chéo dự án", và dự án ở Danh mục dữ liệu.
- `dxos project list|add|remove`, `dxos model project <mô hình> <dự án|->`, `dxos client project <tên> <dự án|->`, và `--project` cho `dxos client add` và `dxos connect`.
  Client trình duyệt (`dxos client browser`) chưa có dự án; `docs/OPERATIONS.md` nêu khi nào nhiều dự án dùng chung một bản cài và khi nào tách bản cài hoặc realm Keycloak.
- Dự án có thành viên và quản trị viên: `GET /api/v1/projects/{name}/members`, `PUT`, `DELETE /api/v1/projects/{name}/members/{userId}` (thân `{"level": "MEMBER"|"ADMIN"}`) và `GET /api/v1/projects/mine`.
  Thành viên là người nằm trong phạm vi của dự án, không tự cấp quyền; xóa dự án xóa luôn danh sách thành viên.
- Quản trị viên của một dự án không cần vai `ADMIN`: `GET /api/v1/users` chỉ thấy thành viên các dự án mình, và gán, gỡ vai của mô hình thuộc dự án cho thành viên của dự án đó.
  Tạo, khóa, đặt lại mật khẩu, đơn vị và mô hình chưa phân loại chỉ `ADMIN` làm được; vượt phạm vi trả 403, backend kiểm ở mỗi lần gọi.
  Đây là quản trị theo dự án, không phải cô lập giữa các tổ chức.
- Nhật ký quản trị ghi thêm `project` ở `USER_ROLE_GRANTED`, `USER_ROLE_REVOKED`, và ghi `PROJECT_MEMBER_ADDED`, `PROJECT_MEMBER_CHANGED`, `PROJECT_MEMBER_REMOVED`.
- Bảng điều khiển: tab Dự án có cột và hộp thành viên, đổi dự án của một mô hình hỏi xác nhận, và quản trị viên của dự án vào được Bảng điều khiển với hai tab Người dùng và Thành viên dự án.
- `dxos project member list|add|remove`; `add` nhận `--admin`.
- Ghi có điều kiện: `PATCH` một hồ sơ, hành động và giao việc nhận thêm `version` và `modelVersion`; hồ sơ hoặc mô hình đã đổi thì trả 409, `data.reason` là `MODEL_VERSION_CHANGED` khi mô hình đổi; `PATCH` kèm `version` cũ trả 409 kể cả khi giá trị không đổi.
- `GET /api/v1/meta/models` trả `modelVersion` của từng mô hình và thực thể, `process.effects` (việc hoàn tất xếp vào hàng đợi, `asynchronous: true`) và `aiTasks`.
- Thực thể khai báo được khối `ai.tasks` trong tệp mô hình: trường đầu vào, trường đề xuất, hướng dẫn và mã nguồn tham khảo; lõi chỉ kiểm khai báo, không gọi model.
- `dxos connect <mô hình> --role R [--role R ...]` đăng ký một client cho mỗi vai và ghi `DXOS_BASE_URL` cùng các biến của chúng vào `.env` của ứng dụng, đăng nhập thử rồi in hai dòng cần thêm vào `docker-compose.yml`; chạy lại thì giữ nguyên secret đã có, và client mất secret cần `--rotate`.
- Ứng dụng chạy cùng máy với DX-OS không cần đổi `PUBLIC_HOST`: container gọi DX-OS qua `host.docker.internal` với `extra_hosts`; `docs/QUICKSTART.md` đường 2 và `examples/order_approval` theo cách này.
- `examples/appliance-store`: ứng dụng bán hàng của một cửa hàng điện máy, không liên quan đến dự án, thêm quy trình đổi trả (mẫu `retail_return` dùng nguyên) và bảo hành (`extends: support_desk`).
  Làm được hết trên Console, có lệnh `dxos` tương đương; ứng dụng đọc trạng thái từ DX-OS khi mở trang và nhận webhook nếu bật.
  README có ba phép thử cho thấy luật theo vai, lịch sử chống sửa và kế hoạch đổi quy trình nằm ở DX-OS.
- Backend có `extra_hosts: host.docker.internal:host-gateway`, nên webhook tới ứng dụng cùng máy dùng địa chỉ `http://host.docker.internal:<cổng>/...` trong `deploy/webhooks/targets.yml`.
- Hồ sơ trong API dữ liệu và thông điệp webhook, cùng feed `GET /api/v1/events`, mang `webUrl`, trang của hồ sơ trên giao diện web; ứng dụng ngoài đưa người duyệt sang đó bằng liên kết này (xem `docs/EXTENSIONS.md`). Chế độ `dxos start --core` không có `webUrl`.
- Phiên bản đã lưu vi phạm một luật kiểm được siết thêm thì mô hình vẫn chạy khi bỏ phần vi phạm được an toàn: hiện là `auto_close` gọi hành động cần ghi chú, `auto_close` đó bị bỏ và có cảnh báo, áp dụng tệp đã sửa là xóa cảnh báo.
  Không bỏ được thì mô hình bị loại khỏi hoạt động, yêu cầu dữ liệu của nó trả 409 kèm lý do và số đo `dxos_models_left_out` đếm nó.
  `GET /api/v1/models` ghi `leftOut`, `problems` và `warnings`, bảng điều khiển hiện cả hai trường hợp trên thẻ của mô hình, và tệp mới vi phạm luật vẫn bị từ chối.

### Tệp mô hình

- Mẫu Một cửa in "Kết quả xử lý hồ sơ" sau khi duyệt, nên cùng một mẫu không gọi hồ sơ Hoãn thi hoặc Phúc khảo là giấy xác nhận.

### Trợ lý nghiệp vụ (tùy chọn)

- Trợ lý đề xuất một giá trị trường từ hồ sơ và nguồn tham khảo; người dùng sửa, hủy hoặc xác nhận, Core kiểm quyền và phiên bản trước khi ghi.
- Trợ lý dùng inference self-host với endpoint tương thích OpenAI hoặc llama.cpp do người vận hành cấu hình; nguồn tham khảo hiển thị nhãn trường và tên đơn vị theo metadata của mô hình.
- Trợ lý nằm trong `deploy/docker-compose.assistant.yml`, chỉ nạp khi `COMPOSE_FILE` nêu tệp đó; bật hay tắt không tạo lại service nào của lõi.
- Cấu hình ở `deploy/assistant.env`, kiểm tra ở workflow `Assistant`, và `assistant/README.md` có danh sách các chỗ cần xóa để gỡ trợ lý.
- Image trợ lý chỉ chứa Node.js và mã của trợ lý, không có npm, corepack và yarn.
- Trợ lý chạy trên network nội bộ riêng; đường ra duy nhất là dịch vụ `assistant-egress`, chỉ tới host và cổng của `ASSISTANT_INFERENCE_URL`.
- Người dùng hủy liên kết ở trang Trợ lý nghiệp vụ (`DELETE /assistant-api/v1/me/core/link`): refresh token bị thu hồi ở Keycloak, token đã lưu bị xóa và các đề xuất đang chờ bị hủy.
- `docker compose run --rm assistant-init dist/rotate.js /bootstrap` thay master key và mã hóa lại state; cách thay khóa gọi inference ở `assistant/README.md`.

### Phát hành

- Image phát hành có nhãn `org.opencontainers.image.licenses=Apache-2.0`.

## [2.0.2] - 2026-11-19

Sửa và bổ sung sau bản `2.0.1`: cài mới tạo được tài khoản `admin`, Hộp việc đưa hồ sơ mới lên đầu, kế hoạch mở rộng quyền chờ duyệt, điều kiện nối nhiều phép, hướng dẫn bắt đầu nhanh và chữ ký cho bản phát hành.
Nâng cấp từ `2.0.1` giữ nguyên dữ liệu.

### Lõi chạy ngầm

- `dxos start` tạo tài khoản `admin` và in mật khẩu tạm trên bản cài mới, vì tài khoản dịch vụ giữ vai ADMIN không được tính là đã có admin.
- `dxos update` và `install.sh --upgrade` so sánh danh sách file runtime theo thứ tự `C`, nên cập nhật giữ đủ file ở mọi locale của máy.
- `GET /api/v1/inbox` xếp hồ sơ quá hạn trước, rồi hồ sơ thay đổi gần nhất trước, và mỗi mục có thêm `updatedAt`; hồ sơ vừa tạo, nộp lại hoặc mở lại nằm ở đầu Hộp việc.
- Danh sách của thực thể có quy trình mở theo thay đổi gần nhất trước và hiện cột Cập nhật lúc, trừ khi mô hình khai `ui.list`.
- Kế hoạch đổi mô hình xếp việc cấp thêm hoặc mở rộng quyền của một vai ở mức `MIGRATION` kèm câu hỏi, nên `dxos apply` đòi `--accept MIGRATION` và `dxos gitops` ở chế độ `auto` để nó chờ duyệt.
- Điều kiện `when` của hành động và `required_when` của trường ghép được nhiều phép so sánh bằng `and`, `or`, `not` và ngoặc; điều kiện một phép viết như cũ.
- Tệp của bản phát hành và image `dxos-*` được ký bằng Sigstore khi phát hành, kiểm bằng `gh attestation verify` (xem `docs/OPERATIONS.md`).
- Chuỗi hash của lịch sử giữ cách xác minh phiên bản 1 cho dữ liệu đã phát hành; sự kiện mới dùng phiên bản 2 và không đưa tên, ghi chú hoặc chi tiết tự do vào chuỗi.

### Giao diện

- Nút Xuất CSV chờ rồi hỏi lại khi cổng API từ chối vì hết hạn mức (429), thay vì dừng hoặc bỏ tên người, và báo khi có tên người không tra được nên được ghi bằng mã.

### Ứng dụng ngoài

- `dxos client add` và `dxos client rotate` nhận `--env <TIỀN_TỐ>` và chỉ in hai dòng `<TIỀN_TỐ>_CLIENT_ID` và `<TIỀN_TỐ>_CLIENT_SECRET`, nối thẳng vào tệp `.env` của ứng dụng được.
- `dxos client test <tên>` đăng nhập bằng client đó, gọi API của mô hình của nó và nêu bước nào hỏng; mã bí mật lấy từ tệp, biến môi trường hoặc hỏi lúc chạy, không nằm trên dòng lệnh.
- `dxos start` in bước tiếp theo và liên kết tới hướng dẫn bắt đầu nhanh.
- `docs/QUICKSTART.md` đi từ máy sạch đến một quy trình chạy được, có đường không cần code và đường có code; `examples/order_approval` là ví dụ chạy được của đường có code.

### Phát hành

- Workflow `Release Images` và `Release Package` đặt quyền ghi ở job cần ghi, còn cấp workflow chỉ đọc.
- CI tự chạy lại khi một job lỗi vì mạng hoặc hạn mức của dịch vụ tải về (Maven Central, GitHub API): bước dựng image chạy lại trong job, workflow `Rerun` chạy lại job lỗi, và kiểm giấy phép của phiên bản giữ câu trả lời của GitHub trong cache.
- `backend/Dockerfile` tải Maven một lần cho cả hai image và thử `repo1.maven.org` khi host mặc định từ chối.
- Workflow `Platform` dựng năm image một lần và giữ trong cache theo hash của mã nguồn, các job kiểm đầu-cuối nạp image từ đó thay vì tự dựng; thời gian chờ giữa các lần chạy lại có thêm phần ngẫu nhiên.

## [2.0.1] - 2026-11-17

Bổ sung và sửa sau bản `2.0.0`, nâng cấp từ `2.0.0` giữ nguyên dữ liệu, migration mới tự chạy khi backend khởi động.

### Giấy phép

- `DEPENDENCIES.txt` khai thêm công cụ cần có trên máy chạy (Docker Engine, Docker Compose v2, curl, jq, Git, OpenSSH, Python) và draw.io, kèm trang chủ và mã nguồn của các thành phần ngoài SBOM.
- Mã, tài liệu, ảnh chụp màn hình, video và sơ đồ cùng theo Apache-2.0; `CODE_OF_CONDUCT.md` viết bằng lời của dự án vì Contributor Covenant theo CC BY 4.0, giấy phép không thuộc danh sách OSI; `check_licenses.py` chặn giấy phép ngoài danh sách OSI trong `REUSE.toml`.
- Mọi tệp mã và cấu hình (CSS, HTML, `Dockerfile`, `pom.xml`, `nginx.conf`, `.env.example`, `.gitleaks.toml`) có dòng `SPDX-License-Identifier`; `REUSE.toml` và `LICENSES/` khai giấy phép và bản quyền cho tệp không chèn được chú thích, và job `Check licenses` chạy `reuse lint`.
- Mã Java của backend định dạng bằng google-java-format (Apache-2.0) qua Spotless, chạy `./mvnw spotless:apply`; mã của kho theo Apache-2.0.
- `check_upstream_licenses.py` đọc giấy phép của dự án gốc ở đúng phiên bản đang dùng của từng image và bản tải về (tag của image, hoặc `ARG` của Dockerfile), và làm job đỏ khi giấy phép khác bản đã khai, ngoài danh sách OSI, không xác định được hoặc không đọc được; mục không đọc được theo cách đó khai `refs`, `text` hoặc `unverified` trong `component-catalog.json`.
- `check_licenses.py sbom <tệp>` kiểm giấy phép của SBOM sinh lại từ pull request ở job SBOM của Compliance và của Dependencies, nên bản mới của một thư viện được kiểm trước khi `sbom/` được sinh lại; `python3 -m unittest discover -s .github/scripts/tests` chạy các ca kiểm của hai script.

### Lõi chạy ngầm

- SpotBugs với find-sec-bugs chạy ở bước `verify` của backend và làm hỏng build khi có phát hiện mà `backend/spotbugs-exclude.xml` chưa giải thích; workflow `Backend` in số phát hiện của từng module.
- Số đo vận hành theo định dạng Prometheus ở `GET /actuator/prometheus` của dịch vụ lõi: hồ sơ theo trạng thái và quá hạn của từng thực thể có quy trình, tuổi và số sự kiện chưa chuyển trong hàng đợi, số lần áp dụng mô hình thành công hoặc thất bại. Endpoint chỉ đọc được trong mạng Docker, đòi mật khẩu `METRICS_PASSWORD` (người dùng `metrics`), và nhãn chỉ gồm tên mô hình, thực thể, trạng thái và kết quả nên số chuỗi không tăng theo dữ liệu. Cách đọc, cấu hình scrape, truy vấn Grafana và quy tắc cảnh báo ở `docs/MONITORING.md`.
- `dxos lint <tệp>...` kiểm tệp mô hình bằng bộ đọc của backend mà không cần hệ thống đang chạy, in `tệp:dòng:cột: nội dung` và thoát 0, 1 hoặc 2. Nó chạy `dxos-lint.jar` trong image backend, hoặc trên Java 21 qua `DXOS_LINT_JAR`; Release kèm `dxos-lint.jar`.
- `dxos conformance <mô hình>` chạy các kiểm rào chắn của lõi trên hệ thống đang chạy và từ chối stack có `PUBLIC_HOST` khác `localhost` khi thiếu `--allow-test-users`.
- `examples/model-repo` có workflow `model-lint.yml` kiểm tệp mô hình trên runner của GitHub bằng `dxos-lint.jar`.
- Quét mã độc tệp tải lên bật sẵn trong `.env` sinh từ `.env.example` (`av` trong `COMPOSE_PROFILES`, `STORAGE_SCAN_HOST=clamav`); `dxos av off` tắt và `dxos doctor` báo `WARN` khi tắt. `install.sh --upgrade` giữ quét tắt ở bản chưa từng bật.
- Nhóm chế độ lõi của job `API end-to-end` chạy với ClamAV: `check_demo.py` kiểm tệp sạch tải lên và tải về, tệp EICAR bị từ chối 422 và không lưu, tệp bị từ chối 503 khi ClamAV dừng, rồi tải lại được khi ClamAV chạy lại. `DXOS_REQUIRE_SCAN=1` làm script hỏng khi stack không quét.
- `dxos start --core` chỉ chạy Tầng 1 (cổng vào, Keycloak, dịch vụ lõi, PostgreSQL, kho tệp); `dxos start --full` chạy đủ, và các lệnh `stop`, `status`, `update`, `reconcile` giữ chế độ đã lưu ở `DXOS_MODE`.
- Dịch vụ lõi không kèm Swagger UI; đặc tả OpenAPI ở `/v3/api-docs` và `/api/v1/meta/models/{model}/openapi`.
- Keycloak Admin Console mặc định không mở ra gateway; đặt `KEYCLOAK_ADMIN_CONSOLE=true` trong `deploy/.env` để mở.
- Job `Gateway surface` của workflow `Platform` kiểm bằng `e2e/api/check_surface.py` rằng cả hai chế độ không trả Swagger UI hay Admin Console, và nhóm kiểm API chạy thêm trên chế độ lõi.
- Portal bỏ thẻ Keycloak Admin và Swagger, thêm thẻ Bảng điều khiển.
- `dxos gitops` kéo hạ tầng và tệp mô hình từ git. Nguồn hạ tầng so `deploy/`, cấu hình cổng vào và tag image của bản cài với một kho DX-OS, rồi nâng cấp bằng luồng của `dxos update`; `auto` cho hạ tầng chỉ nhận commit có chữ ký hợp lệ. Nguồn mô hình so từng tệp với mô hình đang chạy. Cả hai báo lệch, chờ `dxos gitops approve` hoặc tự áp dụng; `dxos gitops install` chạy mỗi 5 phút bằng systemd user timer và `dxos doctor` kiểm các biến `GITOPS_*`.
- `dxos plan --check` thoát với mã 0 khi mô hình đang chạy giống tệp, 2 khi kế hoạch có thay đổi và 1 khi bị từ chối.
- `examples/model-repo` là kho tệp mô hình mẫu, kèm workflow dán kế hoạch vào pull request.
- Kế hoạch (`POST /api/v1/models/plan`, `dxos plan`) của một mô hình chưa chạy không để lại schema `m_<mô hình>` trong PostgreSQL; schema chỉ có khi áp dụng.
- `e2e/api/load_test.py` đo thông lượng, p50, p95, p99 và lỗi của API chung ở nhiều mức người dùng đồng thời, và đo đăng nhập riêng; `e2e/api/load_override.yml` ghim mọi dịch vụ vào N nhân và đặt giới hạn bộ nhớ để một máy đóng vai máy nhỏ hơn. Kết quả theo số nhân và cấu hình nhỏ nhất đạt ngưỡng nằm ở `docs/SIZING.md`.
- Flowable dùng id UUID sinh trong bộ nhớ, nên lấy id không cần thêm kết nối cơ sở dữ liệu và backend không còn treo khi có từ 50 người dùng đồng thời.
- Hành động của quy trình có thêm khóa `check: <tên đích>`: dịch vụ riêng được hỏi trước khi trạng thái đổi và có thể từ chối (403, `CHECK_DENIED`). Đích có `on_error` (`deny` mặc định hoặc `allow`) cho lúc dịch vụ không trả lời (503, `CHECK_UNAVAILABLE`, hồ sơ giữ nguyên) và `bypass` cho vận hành. Lịch sử ghi `check_denied`, `check_unavailable`, `check_skipped`.
- Image phát hành lên GHCR kèm SBOM (SPDX) và thông tin dựng (SLSA); mỗi tag là một chỉ mục gồm image và manifest attestation, đọc theo digest bằng `docker buildx imagetools inspect` (cách kiểm ở `docs/OPERATIONS.md`).

### Ứng dụng ngoài

- Route riêng của bản cài nằm trong `apisix/local.yaml`, được kiểm trước khi ghép với cấu hình lõi và giữ nguyên qua cập nhật.
- Ứng dụng ngoài đổi sang vai khác của cùng mô hình bằng `dxos client role`, nút Đổi vai ở tab Ứng dụng ngoài hoặc `PUT /api/v1/clients/{name}`, giữ nguyên tên và mã bí mật; token đã cấp giữ vai cũ đến khi hết hạn.
- `dxos client browser add` đăng ký public OIDC client dùng Authorization Code với PKCE S256 cho giao diện ở tên miền khác, đồng thời thêm origin chính xác vào allowlist CORS; backend và `dxos doctor` từ chối wildcard, HTTP ngoài loopback, path hoặc URI không hợp lệ.
- `CORS_ALLOWED_ORIGINS` mặc định chỉ nhận `http://localhost:5173` và `http://127.0.0.1:5173`; giao diện thử chạy ở cổng khác trên máy phát triển phải thêm origin của nó vào biến này.

### Tệp mô hình

- Kế hoạch của một lần đổi `access` ghi mỗi vai đổi mức thành một dòng `vai: cũ → mới`, phân loại là cấp thêm, thu hồi, mở rộng, thu hẹp hoặc đổi ở API kế hoạch, `dxos plan` và tab Kế hoạch; mức rủi ro vẫn là `SAFE`.
- Khóa `extends: <mẫu>` cho một tệp bắt đầu từ mẫu của thư viện và chỉ ghi phần khác: lõi ghép lúc xem kế hoạch và áp dụng, lưu tệp đã ghép kèm tên và hash của mẫu; `POST /api/v1/models/merge` và `dxos model merge` in tệp đầy đủ, còn `dxos lint` kiểm phần của tệp ngắn và để `dxos plan` kiểm phần ghép.
- Kiểu trường `code` sinh mã hồ sơ đọc được theo khuôn `format`, ví dụ `HS-{year}-{seq:5}`: chữ cố định, năm tạo và số thứ tự có độ dài cố định, đếm riêng theo mô hình, thực thể và trường, và đếm lại mỗi năm khi khuôn có `{year}`. Mã gán một lần lúc tạo hồ sơ, không ghi hay sửa được, tra bằng `filter.<trường>`; thêm trường vào thực thể đã có hồ sơ là thay đổi `MIGRATION` và điền mã cho hồ sơ cũ.
- Thực thể khai báo mẫu in bằng khóa `documents`: tiêu đề, đoạn văn, bảng trường và khối chữ ký với chỗ trống `{{trường}}`, kèm tên tệp và trạng thái cho in. `GET /api/v1/data/{model}/{entity}/{id}/documents` liệt kê mẫu và `.../documents/{tên}` trả PDF tiếng Việt cho người đọc được hồ sơ; mỗi lần in vào `audit_log`, và màn hình hồ sơ có nút In.
- Format hiện hành là `1`; lõi tự nâng format `0` trong bộ nhớ, API `/models/upgrade` và `dxos model upgrade` cho xem diff trước khi tùy chọn ghi nguyên tử, còn format tương lai hoặc thiếu bước migration bị từ chối.
- Lần áp dụng đầu của tệp đã nâng format tạo thêm một phiên bản không đổi bảng hay quyền nào, và `dxos plan --check` trả 2 cho đến lúc đó.
- Mẫu trống và đặc tả cho trình soạn YAML của bảng điều khiển (`model-v1.schema.json`) dùng format `1`; tệp format `0` dán vào vẫn lập được kế hoạch, chỉ bị trình soạn gạch chân dòng `format`.
- Thêm mẫu đặt lịch khám và phiếu đổi trả hàng trong thư viện mẫu của bảng điều khiển.
- Trường nhiều lựa chọn nhận `default` là một danh sách các lựa chọn.
- `escalate` trong quy trình khai các mức leo thang khi hồ sơ quá hạn, mỗi mức có thời gian sau hạn, vai nhận và số lần nhắc lại; mỗi thông báo là một dòng lịch sử `escalated` gửi qua webhook của mô hình, không đổi trạng thái hồ sơ, và sau lúc dịch vụ tắt chỉ gửi mức mới nhất đã đến hạn.
- Trường `date` làm nguồn của điều kiện `when` và `required_when`, so sánh bằng `==`, `!=`, `<`, `<=`, `>` và `>=`.
- `calendars` khai các lịch làm việc (ngày làm, khoảng giờ, ngày nghỉ) và `calendar` trong quy trình đếm `deadline` theo lịch đó: `m` và `h` chỉ đếm qua giờ làm, `d` là ngày làm, hồ sơ vào ngoài giờ tính từ lần mở cửa kế tiếp; không khai `calendar` thì hạn đếm liên tục như trước. Đổi lịch là thay đổi `SAFE` (`CHANGE_CALENDAR`) và bị từ chối khi quy trình còn hồ sơ mở, như đổi `deadline`; kế hoạch, bảng điều khiển (ô chọn lịch ở form quy trình, nhãn tiếng Việt và tiếng Anh) và trình soạn YAML đều hiểu khóa mới.

### Dữ liệu

- `GET /api/v1/data/{model}/{entity}/quality` đo chất lượng các dòng người gọi được xem (ADMIN đo mọi dòng của thực thể và chỉ nhận số đếm): độ điền của từng trường, số bản ghi thiếu trường bắt buộc có điều kiện, số giá trị vi phạm lựa chọn, `min`, `max` hay `pattern`, và độ mới theo `staleDays`; mỗi thực thể trong tab Danh mục dữ liệu có mục Chất lượng dữ liệu kèm thời điểm đo và số bản ghi được xét, và ô chọn số ngày cho độ mới.
- `GET /api/v1/models/{model}/data-lineage` nêu cho từng trường của mô hình đang chạy nơi giá trị đi vào, các quy tắc đọc nó và nơi nó đi ra (ADMIN); tab Danh mục dữ liệu hiện phả hệ này ở mục Nguồn gốc của từng trường.
- `GET /api/v1/data/{model}/{entity}/report` nhóm các dòng người gọi được xem theo một trường lựa chọn, đơn vị, đúng/sai, trạng thái hoặc tháng của một ngày và tính đếm, tổng, trung bình, nhỏ nhất, lớn nhất trên trường số, với cùng bộ lọc và quyền theo dòng như `stats`; trường hoặc phép tính mô hình không cho phép là 400 và số nhóm bị giới hạn. Trang Thống kê có mục Báo cáo theo trường.

### Giao diện

- Lịch của thực thể đặt chỗ tải đủ các lượt của khoảng đang xem từng trang 200 lượt (tối đa 5000) thay vì chỉ trang đầu, và báo số lượt đang hiện trên tổng số khi khoảng vượt mức đó.
- Ô chọn bản ghi tham chiếu tìm theo trường tên của thực thể được chọn (`filter.<trường>.like`), nên chọn được bản ghi ngoài 25 mục tải sẵn; không có kết quả khớp thì ô nói rõ.
- `npm run lint` trong `frontend/` phân tích tĩnh mã web bằng Biome (cấu hình ở `frontend/biome.json`) và chạy ở workflow `Frontend`; phát hiện nghiêm trọng làm job đỏ, cảnh báo về phụ thuộc của hook và khóa theo chỉ số mảng chỉ hiện trong kết quả.
- Lưu biểu mẫu bỏ trường tệp khỏi dữ liệu tạo/sửa hồ sơ, giữ tệp đính kèm hiện có khi sửa thông tin khác.
- Trình sửa mô hình chọn `default` của trường nhiều lựa chọn từ danh sách `options`, và trình soạn YAML không còn cảnh báo khi `default` là danh sách.
- Các mức leo thang hiện ở ghi chú dưới sơ đồ quy trình, ở dòng lịch sử của hồ sơ kèm vai được báo và lần nhắc, và ở kế hoạch khi tệp đổi các mức này; trình soạn YAML gợi ý các khóa `escalate`, `after`, `to` và `repeat`.
- Ở màn hình điện thoại, hộp việc và danh sách hồ sơ hiện mỗi hồ sơ là một thẻ, và thanh đầu giữ chỗ cho tên trang; bài `e2e/tests/phone.spec.ts` kiểm bảng điều khiển và hộp việc không rộng hơn màn hình 390 px.
- Trường `code` không có ô nhập ở biểu mẫu; trang hồ sơ nêu mã trên đầu kèm nút sao chép, danh sách có cột mã đầu tiên và ô tìm theo một phần của mã.
- Ở màn hình lịch, ô lượt đặt và các nút chuyển chế độ xem bo góc vừa phải thay vì thành hình quả trứng.
- Bộ lọc dạng khoảng ngày (`từ`, `đến`) và tìm theo một phần của mã gửi đúng `filter.<trường>.<phép>` tới API.

### Tài liệu

- Đối chiếu toàn bộ giáo trình DX-OS với thuật ngữ, kiến trúc và hợp đồng hiện tại của lõi, gồm phân tích riêng DX-Ticket và các khoảng trống đã có issue theo dõi.
- `CONTRIBUTING.md` ghi rõ tính năng mới đi kèm kiểm tự động (test backend hoặc mục kiểm trong `e2e/api/check_*.py`) và issue, pull request viết được bằng tiếng Việt hoặc tiếng Anh; README có huy hiệu OpenSSF Best Practices mức passing.
- `.github/CODEOWNERS` khai ba thành viên là người sở hữu của mọi đường dẫn, ruleset của `main` đòi một người trong số đó duyệt, và workflow `Scorecard` chạy OpenSSF Scorecard mỗi tuần và khi đẩy lên `main`, đưa kết quả vào tab Security mà không công bố ra ngoài.
- `docs/INTEGRATIONS.md` ghi cấu hình và kết quả kiểm webhook `chat` với Mattermost Team Edition 11.7.15: thông báo, hiển thị tiếng Việt, gửi lại khi lỗi và khả năng trùng tin.
- Bài so sánh luồng ticket cơ bản ở `examples/effort-benchmark/`: mã API/xử lý của Lab với khai báo Core, cách đếm tái lập và kết quả kiểm API/UI.
- `docs/TARGETS.md` ghi cách đo và kết quả của các chỉ số mục tiêu: mức 1, 2 và 3 của một hệ thống, quy tắc đếm mã tự viết (`tools/count_own_code.py`), thời gian dựng và đổi, bài thử có hướng dẫn kèm một bản mô phỏng người mới, và chi phí hạ tầng của cấu hình tham chiếu.
- Ba trang hướng dẫn sử dụng theo vai ở trang docs: người nộp hồ sơ, người xử lý hồ sơ và người triển khai.
- Trang docs có tài liệu sản phẩm: viết tệp mô hình, API, cài đặt và vận hành, giám sát, tích hợp, báo cáo, điểm mở rộng, kiến trúc (kèm sơ đồ), tương thích giữa các bản và kịch bản trình diễn; chúng được chép từ `docs/` và `deploy/` mỗi lần dựng trang, nên mỗi tài liệu chỉ có một nơi để sửa, và workflow Docs chạy lại khi các tệp đó đổi.
- Ảnh màn hình và hai video ngắn của sản phẩm (từ yêu cầu đến hồ sơ được duyệt và in; đổi mô hình và áp dụng): mục Hình ảnh trong `README.md` và `README_EN.md`, và trang Hình ảnh và video ở trang docs với đủ bộ ảnh tiếng Việt và tiếng Anh, giao diện điện thoại và giao diện tối.
- `ROADMAP.md` nêu hướng phát triển theo nhóm, và `docs/COMPATIBILITY.md` nêu cách giữ tương thích giữa các bản cho API, tệp mô hình, webhook và cơ sở dữ liệu, kèm cách báo trước khi bỏ một phần.
- `README.md` và `README_EN.md` có mục đối chiếu với đề thi theo bốn không gian H, P, D, I, cột Tầng trong bảng cấu trúc repository và đoạn nói DX-OS khác Supabase, Strapi, Directus ở chỗ nào.
- `website/docs/dependencies.md` ghi giấy phép của Supabase, Directus và Strapi và lý do không dùng làm nền.
- Hướng dẫn kết nối công cụ báo cáo bằng `report_ro` ở `docs/REPORTING.md`: giữ PostgreSQL trong mạng riêng, kết nối Apache Superset, xoay mật khẩu và truy vấn dữ liệu xuyên hai mô hình.
- Mục Dữ liệu cá nhân ra nước ngoài ở `docs/OPERATIONS.md` nêu khi nào cấu hình làm phát sinh nghĩa vụ đánh giá tác động theo Luật 91/2025/QH15 và Nghị định 356/2025/NĐ-CP, đối chiếu với bản đăng trên Công báo.
- Hướng dẫn phát triển dịch vụ, bên nhận webhook, giao diện và trang mở rộng ở `docs/EXTENSIONS.md`: quyền, gateway, chạy local, kiểm thử, đóng gói, nâng cấp và ranh giới hỗ trợ hiện tại.
- Windows 10/11 cài và chạy DX-OS qua WSL2 và Docker Desktop: bảng Yêu cầu hệ thống ở `README.md` và `README_EN.md` ghi đường này, và `docs/OPERATIONS.md` có mục Windows (WSL2).
- Hướng dẫn tích hợp REST cho ứng dụng ngoài ở `docs/EXTENSIONS.md`: bảng giá trị lấy ở đâu, ví dụ PowerShell cho bước lấy token, đọc OpenAPI và gọi dữ liệu, và bảng xử lý `401`, `403`, `409`, `429`, `503` và timeout.
- Điều kiện tải chữ ký virus của ClamAV và hạn hỗ trợ của image đang ghim ở `DEPENDENCIES.txt` và `docs/OPERATIONS.md`.

## [2.0.0] - 2026-11-11

Lõi dynamic thay cho hai ứng dụng nghiệp vụ viết tay của bản `1.1.1`.
Không có đường nâng cấp dữ liệu từ `1.1.x`; bản cũ nằm ở tag `v1.1.1`.

### Lõi và tệp mô hình

- Một tệp mô hình khai báo thực thể, trường, vai, quyền, đơn vị và quy trình; `dx-model` đọc và kiểm tra, báo lỗi theo dòng và cột.
- API chung `/api/v1/data/{model}/{entity}` với lọc, sắp xếp, phân trang, quy tắc bắt buộc, duy nhất và lựa chọn; metadata ở `/api/v1/meta/models`.
- Sửa hồ sơ nhận `version`: hồ sơ đã đổi thì trả 409 `ERR_CONCURRENT_MODIFICATION` và không ghi đè.
- Lỗi 403 `ERR_ACTION_NOT_ALLOWED` và 409 `ERR_INVALID_STATE` có `data.reason`, mã ổn định cho client; giao diện dịch lỗi theo mã này.
- Hành động của người xử lý nhận điều kiện `when`, một phép so sánh một trường với một giá trị như `amount > 5000000`; hồ sơ chưa đạt thì trả 409 `ERR_INVALID_STATE` với `data.reason` là `CONDITION_NOT_MET`, giá trị trống không đạt, nên một trạng thái rẽ nhánh theo ngưỡng được bằng hai hành động `when` loại trừ nhau; nút của hành động mờ trên giao diện và sơ đồ quy trình ghi điều kiện trên cạnh.
- Thống kê `GET /api/v1/data/{model}/{entity}/stats` cho thực thể có quy trình: tổng số, theo trạng thái, quá hạn, theo đơn vị, thời gian xử lý trung bình và tuổi hồ sơ đang mở, cùng bộ lọc và quyền như danh sách.
- Thực thể khai hạn xử lý có thêm `closedOnTime` và `closedLate` trong thống kê, đếm từ các dòng lịch sử `overdue`; trang thống kê hiện tỷ lệ hồ sơ đã đóng đúng hạn.
- Dòng `overdue` được ghi khi hồ sơ rời một hạn đã quá.
- Tên hành động của quy trình không được trùng tên một dòng lịch sử của hệ thống như `created`, `overdue` hay `due_soon`; tệp đặt tên đó bị từ chối ở bước kiểm.
- Dòng lịch sử `updated` ghi giá trị cũ và mới của từng trường đã đổi.
- Tám mẫu mô hình ở `backend/dx-engine/src/main/resources/templates/`: một cửa sinh viên, hỗ trợ kỹ thuật, sự cố thiết bị, đặt phòng, cấp quyền, mượn thiết bị, ghi chú, sổ tài sản.
- Vai `report_ro` chỉ đọc cho công cụ báo cáo của người vận hành.
- Kiểu trường `boolean` và `date`, trường `choice` hoặc `unit` có `multiple: true` để chọn nhiều giá trị (lưu thành mảng, lọc bằng `contains`).
- Trường có `min` và `max` (số hoặc ngày), `pattern` (khớp cả chuỗi) và `required_when`, một phép so sánh trường khác như `kind == Hoãn thi`; API kiểm các quy tắc này và cơ sở dữ liệu giữ `min`, `max`, `pattern` bằng ràng buộc `CHECK`.
- Thực thể có khối `ui:` chọn biểu tượng, nhóm và thứ tự menu, cột và sắp xếp mặc định của danh sách, nhóm trường của biểu mẫu và lời gợi ý; tệp cũ không cần sửa.
- Mẫu `asset_register` (sổ tài sản) dùng đủ các kiểu và khối `ui:` mới.

### Áp dụng và đổi mô hình

- Áp dụng vào hệ thống rỗng dựng schema `m_<mô hình>` (pgschema), bảng, ràng buộc, vai và nhóm Keycloak, phiên bản và lịch sử từng bước; áp dụng lại cùng tệp thì không làm gì, bảng hoặc Keycloak lệch thì kế hoạch ghi `repair` và sửa phần lệch.
- Đổi mô hình khi đã có dữ liệu: xem trước theo ba mức rủi ro, đổi tên bằng `renamed_from`, `default` cho trường bắt buộc mới, báo dòng vi phạm, xác nhận riêng khi mất dữ liệu, kế hoạch hết hiệu lực khi phiên bản đã đổi, quay về phiên bản trước.
- Mô hình bị giữ trong lúc áp dụng; áp dụng dừng giữa chừng thì tiếp tục (`resume`) hoặc hủy bỏ (`abandon`), kể cả sau khi backend khởi động lại.
- Mỗi mô hình có một lần áp dụng tại một thời điểm, các mô hình khác nhau áp dụng song song; yêu cầu dữ liệu, hành động quy trình và thao tác tệp giữ khóa đọc nên không hồ sơ nào lọt giữa kiểm và đổi bảng.
- Chốt chặn: không đổi hạn, quyền tự quyết, hành động hoàn tất, đặt chỗ hay trạng thái và hành động của quy trình (kể cả điều kiện `when`) khi còn hồ sơ đang xử lý; không xóa thực thể còn hồ sơ đang chạy quy trình; vai `ADMIN` của nền tảng không khai báo được.
- Trạng thái còn hồ sơ nằm ở đó không bỏ được, và trạng thái cuối còn hồ sơ đã đóng không đổi thành trạng thái thường; kế hoạch báo số dòng vi phạm.
- Đổi nhãn, vai của hành động, trường bắt buộc, các trạng thái rút lại hay danh sách vai xử lý có hiệu lực ngay với hồ sơ đang mở.
- Khai báo lại một vai còn người giữ thì kế hoạch lên mức `MIGRATION` và báo số người có lại quyền.
- Tệp mô hình lúc khởi động: `dxos start <tệp> [<tệp>...]` đưa một hay nhiều tệp vào, backend áp dụng lần lượt từng tệp nếu là tệp mới và có kế hoạch an toàn; tệp lỗi không chặn tệp sau, và mỗi tệp có một dòng kết quả.
- `dxos plan` và `dxos apply [--accept MỨC]` đổi mô hình từ dòng lệnh, đăng nhập bằng client `dxos-cli`.
- Siết `min`, `max`, `pattern`, bớt lựa chọn hoặc thêm `required_when` khi còn dòng vi phạm thì kế hoạch bị từ chối kèm số dòng; nới giới hạn hoặc đổi `ui:` áp dụng ngay, và đổi `ui:` không bị chặn khi còn hồ sơ mở.
- Đổi trường giữa một giá trị và nhiều giá trị bị từ chối; đổi tên trường kéo theo đổi tên các ràng buộc `CHECK` của nó.
- `GET /api/v1/meta/models/{model}/openapi` trả tài liệu OpenAPI của một mô hình đang chạy, sinh từ mô hình nên đổi theo mỗi lần áp dụng và quay về.

### Quyền và định danh

- Quyền theo vai và theo dòng (`all`, `read`, `own`, `unit`, `assigned`); thực thể con đi theo chuỗi `owner_via` nhiều cấp, chuỗi quay vòng bị từ chối.
- Đơn vị khai trong mô hình và đồng bộ thành nhóm Keycloak.
- Mỗi mô hình có một client `dxos-m-<mô hình>` chứa vai của nó, nên hai mô hình dùng cùng tên vai không vào được dữ liệu của nhau.
- Tài khoản dịch vụ `dxos-backend-admin` có quyền `manage-clients`, `view-clients`, `query-clients` và scope `kc-admin-api`, vì Admin API của Keycloak 26.8 chỉ nhận token có audience của nó.
  Realm đã import từ trước chạy `deploy/keycloak-admin-scope.sh` (`update.sh` gọi nó) và cấp các quyền client ở Keycloak Admin Console.
- Trường kiểu `user` giữ id tài khoản Keycloak: backend từ chối id không có thật khi tạo hồ sơ hoặc khi giá trị đổi, giao diện cho chọn người theo tên qua `GET /api/v1/people` và hiện tên ở danh sách, trang chi tiết và lịch sử.
- Tab Người dùng và API `/api/v1/users` (ADMIN): tìm kiếm, vai theo từng ứng dụng, gán và gỡ vai và đơn vị, tạo người dùng với mật khẩu tạm, khóa và mở, đặt lại mật khẩu.
- Thay đổi tài khoản và việc áp dụng, quay về, tiếp tục, hủy bỏ mô hình ghi `audit_log` (`USER_*`, `MODEL_*`); `GET /api/v1/audit-logs` lọc theo đối tượng.
- Keycloak từ chối tài khoản dịch vụ thì API trả 502 `ERR_ACCOUNT_SERVICE_DENIED`, tách khỏi `ERR_ACCOUNT_SERVICE_UNAVAILABLE` khi Keycloak không trả lời.
- Ứng dụng ngoài: `GET`, `POST /api/v1/clients`, `POST /api/v1/clients/{name}/secret` và `DELETE /api/v1/clients/{name}` (ADMIN), cùng `dxos client add|list|rotate|remove` và tab Ứng dụng ngoài ở bảng điều khiển; mỗi ứng dụng là một client `dxos-app-{tên}` giữ đúng một vai của một mô hình, mã bí mật chỉ hiện một lần và không vào log hay sự kiện.

### Quy trình và hồ sơ

- Ba họ mẫu trên Flowable nhúng, cùng giao dịch với việc ghi hồ sơ: `approval`, `work_item` và `reservation`; trạng thái chỉ đổi qua quy trình.
- Khối `process:` dùng `family`, `handler`, `states`, `actions`, `initial`, `allow_self_decision`, `deadline`, `auto_close` và `after`; khối `booking:` khai báo tài nguyên, khung giờ và sức chứa.
- Quy tắc chung của mọi quy trình: từ chối và trả lại cần lý do, người nộp sửa rồi nộp lại hoặc rút lại, người tạo không tự quyết định, hồ sơ đã giao thì chỉ người được giao xử lý.
- Hồ sơ lưu người quyết định và thời điểm quyết định.
- Giao việc, hộp việc `/api/v1/inbox` có phân trang, và dòng lịch sử của từng bản ghi ghi trong cùng giao dịch; người nhận phải nằm trong danh sách người xử lý, đọc hết các trang của Keycloak nên không giới hạn số người, và ô Giao cho tìm theo tên.
- Hạn xử lý theo bản ghi: hồ sơ quá hạn lên đầu hộp việc và có dòng quá hạn; hồ sơ có hạn dài hơn `DXOS_DUE_SOON_BEFORE` nhận thêm dòng `due_soon`.
- Hành động hoàn tất `add_to_group` và `remove_from_group` chạy qua outbox và được thử lại; trường trống thì bỏ qua và ghi dòng `completion_skipped`.
- Đặt chỗ so sức chứa với số lượt chạy cùng lúc ở mọi thời điểm của khung giờ, có khóa dòng tài nguyên.
- `auto_close` tùy chọn: hồ sơ chờ người nộp quá thời gian đó thì hệ thống làm thay họ một hành động và ghi dòng `auto_closed`; họ `work_item` viết `auto_close: 7d`, quy trình khác viết `auto_close: {after: 7d, action: <hành động>}`.
- Quy trình tự định nghĩa: `states` và `actions` thêm, ghi đè hoặc bỏ (`null`) từng phần của khung, hoặc khai báo toàn bộ khi không có `family`; `initial` chọn trạng thái đầu, `withdraw` là hành động dành riêng của người nộp.
- `handler` nhận một vai hoặc danh sách vai; mỗi hành động có thể giới hạn cho vai riêng bằng `role`, và `require` bắt buộc trường có giá trị trước khi hành động được thực hiện.
- Parser kiểm cả quy trình: hành động trỏ tới trạng thái có thật, mỗi trạng thái không phải cuối có lối ra và tới được từ trạng thái đầu, có trạng thái cuối; lỗi báo dòng và cột, kèm lựa chọn hợp lệ.
- Quy trình khác khung gốc chạy trên BPMN riêng sinh từ các trạng thái và hành động, được deploy khi áp dụng mô hình; quy trình giống khung dùng BPMN của khung.
- Hộp việc và danh sách người nhận việc tính theo vai của từng hành động.
- Metadata của thực thể có `handlerActions`, các hành động mà vai của người gọi được thực hiện.
- Người nộp chỉ rút lại được hồ sơ ở các trạng thái khai trong `withdraw`.
- Tạo hồ sơ và hành động nhận header `Idempotency-Key`: gửi lại cùng khóa và cùng nội dung thì trả lại câu trả lời lần đầu mà không làm lại, cùng khóa với yêu cầu khác là 409, yêu cầu lỗi không giữ khóa, và khóa giữ 24 giờ.

### Sự kiện, webhook và toàn vẹn

- Hàng đợi sự kiện bằng bảng outbox: thông điệp ghi trong giao dịch của thay đổi, giao sau đó, thử lại với thời gian chờ tăng dần và dừng sau `DXOS_OUTBOX_MAX_ATTEMPTS` lần.
  ADMIN xem và thử lại ở `GET /api/v1/outbox`, `POST /api/v1/outbox/{id}/retry` và tab Hàng đợi sự kiện.
- Webhook theo mô hình: mỗi dòng lịch sử gửi dạng JSON có chữ ký tới đích mà tệp mô hình chọn (`webhook: <tên đích>`), định dạng `json` hoặc `chat` cho Mattermost và Slack; các dòng của một hồ sơ tới đúng thứ tự.
- Thông điệp `admin.event` báo việc áp dụng mô hình và thay đổi tài khoản; mỗi thay đổi được ghi thành một dòng có `eventId`, và `GET /api/v1/events/admin` trả lại các dòng đó cho bên nhận bị lỡ, dù có đích webhook hay không.
- `GET /api/v1/events` cho bên nhận lỡ tin kéo lại theo `eventId`.
- Lịch sử hồ sơ nối thành chuỗi hash, mỗi giờ ghi một điểm neo ra kho đối tượng; `dxos history verify` và `GET /api/v1/history/verify` chỉ ra dòng bị sửa, xóa hoặc chèn.
- `examples/history-witness` cho bên nhận giữ `eventId` và `hash` rồi so với hệ thống.
- `dxos archive` xuất hồ sơ đã xong ra Parquet kèm `manifest.json`; `dxos archive check` kiểm lại.
- `dxos archive --store` ghi vào bucket riêng của SeaweedFS (`S3_ARCHIVE_BUCKET`); `archive list` liệt kê các lần chạy, `archive check --store` đọc lại từ kho và so với manifest.
- `dxos archive query` truy vấn chỉ đọc một tệp Parquet của một lần chạy bằng DuckDB (`--file`), kiểm tệp với manifest trước khi chạy, hỗ trợ tham số ràng buộc, đặt giới hạn tài nguyên và chặn truy cập đường dẫn ngoài.
- `dxos archive auto` ghi hồ sơ đã đóng quá `ARCHIVE_AFTER_DAYS` ngày, mỗi hồ sơ một lần; `dxos archive schedule install` chạy lệnh đó mỗi ngày bằng systemd user timer, `uninstall` gỡ; dữ liệu trong cơ sở dữ liệu giữ nguyên.
- `GET /api/v1/models/{model}/data-catalog` (ADMIN) và tab Danh mục dữ liệu ở bảng điều khiển: nơi dữ liệu của một mô hình đi vào (vai, ứng dụng ngoài) và đi ra (webhook, hành động hoàn tất, dòng sự kiện, lưu trữ), liên kết giữa các thực thể, số bản ghi, quyền theo vai và danh sách trường.
- `GET /api/v1/inbox/stream` báo cho giao diện khi một dòng lịch sử hồ sơ ghi xong, để hộp việc, danh sách và trang chi tiết đang mở tự đọc lại; khung không kèm nội dung hồ sơ, nhịp ping 20 giây, server đóng kênh sau 10 phút, tối đa 8 kênh cho mỗi người và 2000 kênh cùng lúc; route gateway giữ kiểm token và cho đọc 15 phút.
- `examples/itot-simulator` giả lập thiết bị tạo hồ sơ sự cố qua API; gửi lại cùng một quan sát thì lõi từ chối.

### Tệp đính kèm

- Trường kiểu `file` lưu tệp ở SeaweedFS, quyền theo quyền của bản ghi; loại tệp nhận được đặt bằng `engine.files.allowed-extensions`, mỗi hồ sơ tối đa 20 tệp, link tải luôn ở dạng đính kèm.
- ClamAV quét tệp khi bật bằng `dxos av on`; tệp nhiễm bị từ chối với 422 `ERR_FILE_INFECTED`, ClamAV không trả lời thì 503 `ERR_SCAN_UNAVAILABLE` và tệp không được lưu.
- Tệp gỡ khỏi hồ sơ rời kho sau `DXOS_FILE_DELETE_DELAY` (mặc định 24 giờ) để bản sao lưu luôn đủ tệp; tệp của giao dịch bị hủy xóa ngay.

### Giao diện

- Giao diện react-admin dựng từ metadata, có tiếng Việt và tiếng Anh chọn bằng nút VI và EN, có chế độ sáng và tối: hộp việc, chi tiết hồ sơ dạng thẻ với dòng thời gian, lịch đặt chỗ kéo thả được, trang thống kê.
  Danh sách mỗi thực thể cho chọn và sắp xếp cột bằng nút Cột, nhớ bộ lọc và sắp xếp đã chọn, và lưu bộ lọc thành truy vấn đặt tên ở nút Thêm bộ lọc; tất cả lưu trong trình duyệt.
  Lựa chọn ngôn ngữ dùng chung với portal; đổi ngôn ngữ khi trình soạn của bảng điều khiển còn bản chưa áp dụng thì hỏi xác nhận trước khi tải lại trang.
  Backend trả thông báo tiếng Anh kèm `errorCode`; bản tiếng Việt dịch theo `shell/backendMessages.ts` và `data.reason`, bản tiếng Anh hiện thông báo của backend.
- Bảng điều khiển `/console` cho ADMIN: thư viện mẫu, trình soạn YAML (Monaco) theo JSON Schema, xem kế hoạch, áp dụng, lịch sử áp dụng kèm tên người, quay về, sơ đồ quy trình, mô hình dừng giữa chừng (Tiếp tục, Hủy bỏ) và nút "Sửa phần lệch".
  API đọc cho bảng điều khiển: `/api/v1/models/schema`, `families`, `templates` và `{model}/versions`.
- Sơ đồ quy trình vẽ từ chính tệp đang soạn: vai xử lý của từng hành động, hạn xử lý, hành động của `auto_close`, hành động `after` và đặt chỗ, cho cả quy trình tự khai báo.
  Tệp được kiểm và vẽ lại sau 5 giây không gõ; tệp sai thì báo lỗi theo dòng và cột và giữ sơ đồ đúng gần nhất.
- Điền form gồm các tab Chung, Vai, Đơn vị và Thực thể (Chung, Trường, Quyền, Quy trình), nhãn tiếng Việt; vai, trường và thực thể đã khai báo chọn từ danh sách, đổi tên một vai thì quyền và vai xử lý đổi theo.
  Tab Quy trình sửa nhãn, thêm hoặc bỏ trạng thái và hành động của khung, giao một hành động cho vai riêng, bắt buộc điền trường, đặt hành động tự đóng, hoặc chọn không dùng khung để tự khai báo.
- Mỗi phần của form có mục "Phạm vi có thể làm": hệ thống chấp nhận gì, mở rộng được gì, tự tạo được gì.
  Cạnh trình soạn YAML có bảng "Viết được gì trong tệp" với tên trạng thái và hành động của từng khung, ví dụ mở rộng quy trình và ý nghĩa từng khóa; rê chuột lên một khóa hiện giải thích.
  `POST /api/v1/models/diagram` (ADMIN) chỉ đọc tệp, không so với mô hình đang chạy.
- Chi tiết hồ sơ chỉ hiện nút hành động mà vai của người dùng được làm; hồ sơ có quy trình không có nút xóa.
- Bảng kế hoạch nêu rõ trạng thái hay hành động nào được thêm hoặc bỏ, và lý do một thay đổi bị từ chối bằng tiếng Việt.
- Menu người dùng có "Tài khoản của tôi", mở Account Console của Keycloak.
- Giao diện vừa màn hình rộng 360 px.
- Danh sách của thực thể có quy trình cho chọn nhiều hồ sơ và thực hiện cùng một hành động, tối đa 50 hồ sơ mỗi lần; mỗi hồ sơ vẫn qua đủ luật của quy trình và hồ sơ bị từ chối báo lý do.
- Danh sách của thực thể có quy trình xem được như bảng theo trạng thái: mỗi cột một trạng thái với tối đa 10 thẻ và tổng số, bấm thẻ mở chi tiết, "Xem cả N hồ sơ" mở danh sách đã lọc theo trạng thái; lựa chọn danh sách hoặc bảng nhớ trong trình duyệt.
- Nút Xuất CSV ghi các dòng của danh sách theo bộ lọc và sắp xếp hiện tại, đúng phần người xem được quyền thấy, tối đa 10.000 dòng và báo khi bị cắt; tệp UTF-8 có BOM, giá trị đúng/sai và nhiều lựa chọn ở dạng đọc được, trường người dùng ghi tên, ô bắt đầu bằng `=`, `+`, `-`, `@`, tab hoặc xuống dòng được thêm dấu nháy để không chạy thành công thức.
- Ô nhập, cột, chi tiết và bộ lọc cho kiểu `boolean`, `date` và chọn nhiều giá trị; biểu mẫu báo giới hạn, mẫu chuỗi và điều kiện bắt buộc trước khi gửi.
- Menu, biểu tượng, cột và sắp xếp mặc định, nhóm trường và lời gợi ý theo khối `ui:`; cột người dùng đã chọn đè lên cột mặc định.
- Form soạn mô hình có ô cho nhiều giá trị, nhỏ nhất, lớn nhất, mẫu chuỗi và điều kiện bắt buộc.

### Cài đặt và vận hành

- Cài bằng một lệnh: `dxos-install.sh` phát hành kèm mỗi bản tải gói, kiểm SHA-256 và cài vào `~/.dxos`; `install.sh --upgrade` nâng cấp bản đã cài, giữ `.env` và dữ liệu.
- Lệnh `dxos`: `start`, `stop`, `status`, `logs`, `admin`, `update`, `doctor`, `backup`, `av`, `plan`, `apply`, `history`, `reconcile`, `archive`, `demo`, `client`, `uninstall`; `dxos start` tạo tài khoản `admin` đầu tiên với mật khẩu tạm.
- `dxos demo` tạo bốn tài khoản thử theo vai của mô hình Một cửa sinh viên và nộp khoảng 40 hồ sơ mẫu qua API chung; mật khẩu sinh ngẫu nhiên và in một lần; chạy lại không nộp trùng; `dxos demo remove` gỡ các tài khoản thử.
- `deploy/make_env.sh` sinh `.env` với mật khẩu ngẫu nhiên; cài và chạy stack không cần Python.
- Tự phục hồi: mọi service chạy lâu có `restart: unless-stopped`, `dxos reconcile` đưa stack về đúng khai báo và chạy mỗi phút bằng systemd user timer.
- `deploy/backup.sh` sao lưu mã hóa Postgres và kho tệp; `verify` nạp bản dump vào Postgres tạm và kiểm mọi tệp đính kèm có trong kho.
- Màn hình giám sát `/monitor/` gửi cảnh báo qua `ALERT_WEBHOOK_URL_ADMIN` hoặc `ALERT_WEBHOOK_URL`.
- Stack gồm `postgres` (image chính thức `postgres:16-bookworm`), `keycloak`, `seaweedfs`, `backend`, `apisix`, `frontend`, `portal`, `monitor` và `clamav` qua profile `av`.
- Biến tùy chọn `KEYCLOAK_HOSTNAME` trong `deploy/.env.example` (đặt bằng `GATEWAY_PUBLIC_URL` khi đứng sau proxy HTTPS) bật `KC_HOSTNAME` và `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` cho Keycloak; không đặt thì Keycloak chạy như mặc định.

### Kiểm thử, CI và tài liệu

- Mười bốn script `deploy/dev/check_*.py` kiểm API, phục hồi, hồ sơ, đổi mô hình, quy trình, đặt chỗ, kịch bản trình diễn, chốt chặn, người dùng, tài liệu khớp OpenAPI, rào chắn của mọi mô hình, webhook, tệp mô hình lúc khởi động và các lệnh `dxos plan`, `apply`, `archive` cùng bộ mô phỏng IT/OT.
- Test WireMock cho client Keycloak, bộ gửi webhook và bộ dò dịch vụ; test chậm gắn `@Tag("slow")`.
- CI gồm tám workflow: `Backend`, `Frontend`, `Platform`, `Compliance`, `Dependencies`, `Docs`, `Release Images`, `Release Package`; action ghim theo SHA, mọi job có giới hạn thời gian.
  Mỗi pull request chạy hai nhóm kiểm API trên stack rỗng, một bài Playwright cho bảng điều khiển (`e2e/`), kiểm gói phát hành, SBOM khớp tệp khóa, giấy phép, tệp bắt buộc, cú pháp workflow và bí mật.
- `Release Package` chỉ phát hành tag `vX.Y.Z` nằm trên `main`, kiểm gói trước khi đăng và không ghi đè tệp đã có.
- `.github/rulesets/` lưu quy tắc của nhánh `main` và của tag `v*` để nhập vào GitHub.
- Trang tài liệu ở `website/` (Docusaurus) đăng lên GitHub Pages khi đẩy vào `main`.
- Tài liệu vận hành chia ba tệp ở `deploy/`: `OPERATIONS.md`, `MONITORING.md` và `INTEGRATIONS.md`; `docs/TESTING.md` là hướng dẫn kiểm.
- `docs/DEMO.md` ghi kịch bản trình diễn 15 phút: chuẩn bị, tài khoản thử, từng bước kèm kết quả mong đợi và cách xử lý sự cố.

### Gỡ bỏ và giấy phép

- Gỡ `bi/` (Superset), `chat/` (Mattermost), `ai/`, `data/`, trọng số và mã nhận diện bệnh lúa, luồng chẩn đoán, bộ đánh giá RAG, kho văn bản của HaUI, reranker mMARCO và Qwen; bản cũ ở tag `v1.1.1`.
  Portal chỉ còn ứng dụng chính, Keycloak Admin, màn hình giám sát và Swagger; biến `PORTAL_BI_URL` và `PORTAL_CHAT_URL` không còn dùng.
- Mã của dự án chỉ có một giấy phép, Apache-2.0.
- Mỗi bản build của ứng dụng web và của trang tài liệu kèm `third-party-licenses.txt` ở gốc, chép thông báo bản quyền của từng thư viện npm đóng gói trong đó (`frontend/scripts/third-party-licenses.mjs`).
- `.github/licenses/component-catalog.json` khai báo image và tệp tải về; `check_licenses.py` chỉ cho giấy phép OSI và chặn mục thiếu.
  `DEPENDENCIES.txt`, `NOTICE` và các tệp SBOM ghi đúng thành phần của bản phát hành; số phiên bản của backend, web và SBOM là `2.0.0`.

## [1.1.1] - 2026-10-30

- Workflow `Release Package` tạo GitHub Release cho mỗi tag `vX.Y.Z`, kèm gói `dx-os-X.Y.Z.tar.gz`, tệp `.sha256` và SBOM; ghi chú lấy từ mục tương ứng của CHANGELOG.
  Image GHCR của workflow `Release` có thêm tag theo phiên bản khi chạy trên tag.
- `deploy/install.sh` cài các file chạy sang một thư mục khác và dùng image dựng sẵn, nên stack chạy được khi không còn thư mục mã nguồn; job `install` của CI kiểm cách cài này.
- Mọi file mã nguồn có header `SPDX-License-Identifier` (`AGPL-3.0-only` trong `ai/`, `Apache-2.0` ở nơi khác); job `license` của CI kiểm header và giấy phép của từng dependency trong `sbom/`.
- `sbom/ai.cdx.json` ghi giấy phép của từng gói Python; `.github/scripts/enrich_sbom_licenses.py` bổ sung từ PyPI sau khi sinh SBOM.
- Image của Apache APISIX, PostgreSQL (pgvector), Superset, Mattermost, Ollama và Alpine ghim digest trong `deploy/docker-compose.yml`.
- Số phiên bản của backend, web và mobile khớp với phiên bản phát hành (`1.1.1`).
- Thêm `SECURITY.md`, biểu mẫu issue dạng form và `.mailmap`; `CONTRIBUTING.md` mô tả đúng luồng nhánh và quy trình phát hành.
- README có mục chạy ngoài thư mục mã nguồn và build từ mã nguồn không qua Docker; `ai/weights/README.md` ghi nguồn và SHA-256 của crop classifier.
- Tài liệu nội bộ nằm ở trang docs https://haui-implsolution.github.io/DX-OS-docs/; repository giữ `docs/TESTING.md` và `docs/images/`.
- Thư mục `.claude/` và `.codex/` không nằm trong repository.
- `COPYRIGHT.txt` ghi phiên bản của Keycloak, APISIX, Superset, Mattermost, nginx, cloudflared và thêm Alpine; image `cloudflared` ghim phiên bản và digest.

## [1.1.0] - 2026-10-30

- Bảng thành phần và giấy phép nằm ở `COPYRIGHT.txt`, trình bày bằng bảng văn bản thuần.
- Web và mobile hiển thị thông báo tiếng Việt cho lỗi `ERR_TOO_MANY_REQUESTS` và cho phản hồi `429` của gateway.
- `deploy/doctor.sh` kiểm Docker, RAM, ổ đĩa, `deploy/.env` và các cổng trước khi chạy hoặc cập nhật.
- Backend chạy trên Spring Boot 4.1.2 (Tomcat 11.0.27, Jackson 2.21.7 và 3.1.7); SBOM của backend không còn lỗi HIGH hoặc CRITICAL đã có bản vá.
- CI kiểm mọi route gateway không có token nằm trong danh sách công khai, quét SBOM đã phân giải của backend (chặn khi có lỗi đã có bản vá) và có luật phân lớp bằng ArchUnit.
- `deploy/online-check.sh` kiểm site công khai từ máy chủ (chạy bằng cron) và báo sang Mattermost khi site lỗi hoặc trở lại.
- Lưu ảnh và tệp đính kèm chạy trên SeaweedFS, kèm trang quản trị `seaweedfs-admin`.
  Bộ chạy đánh giá RAG dùng cùng image cho kho S3 của Langfuse.
  Biến cấu hình là `S3_*`, `APISIX_STORAGE_ADMIN_HOST_PORT` và `PORTAL_STORAGE_URL`.
  gRPC của filer chỉ nhận lời gọi ký bằng `S3_SECRET_KEY`, nên khóa này cần tối thiểu 16 ký tự; log của `seaweedfs` chỉ gồm cảnh báo và lỗi.
  `seaweedfs/migrate-uploads.sh` chuyển ảnh có sẵn vào bucket, chạy được trên CPU không có x86-64-v2.
- Link ảnh ký sau Cloudflare dùng `https`; portal đọc địa chỉ dịch vụ ở `/portal/config`, đường dẫn Cloudflare không cache, và chú thích thẻ hiện địa chỉ thật.
- `backup.sh` xóa snapshot cũ theo chính sách giữ cho mọi snapshot của dự án, kể cả khi danh sách thư mục sao lưu thay đổi.
- AI nạp sẵn model embedding lúc khởi động; hỏi đáp Farm về một bệnh đã nêu tên chỉ giữ nguyên nhân và cách phòng có trong nguồn, và dòng "Tên khoa học" được giữ trong evidence.
- Header `Idempotency-Key` của cả sáu endpoint tạo mới được đọc bằng `IdempotencyKey.parse` trong `dx-core-api`.
- Dependabot mở pull request cập nhật Maven, npm, GitHub Actions và image gốc của Dockerfile; Trivy quét thêm các image của bên thứ ba trong compose mỗi tuần và báo bằng cảnh báo.
- Backend giới hạn số lần gọi theo người dùng (`sub` của token) ở các lệnh tốn tài nguyên, trả `429` kèm `Retry-After`; `RATE_LIMIT_ENABLED=false` tắt khi thử tải.
- Route chẩn đoán AI của gateway chờ tới 95 giây, đủ cho chẩn đoán 50 đến 70 giây trên CPU và dưới giới hạn 100 giây của Cloudflare.

## [1.0.3] - 2026-10-29

- Sao lưu và khôi phục có mã hóa bằng `deploy/backup.sh`, có lệnh kiểm tra khôi phục và job CI chạy thử.
- Độ bền: tin chat lỗi tạm thời được gửi lại, cầu dao khi gọi AI service, mã request đi suốt từ gateway tới AI service.
- Bảo mật: xác thực hai bước và chính sách mật khẩu ở Keycloak, quét mã độc tệp tải lên bằng ClamAV, che thông tin cá nhân trong tin chat và câu hỏi gửi sang AI, container chạy không phải root, console MinIO mặc định chỉ mở trên máy chủ.
- Giám sát: cảnh báo vào kênh Ban quản trị khi một dịch vụ ngừng hoặc phục hồi, khi lỗi `5xx` dồn dập, khi một địa chỉ bị giới hạn tốc độ hoặc đăng nhập sai nhiều lần.
- Câu trả lời Qwen: Lab giữ đúng điều kiện và đối tượng của quy định; Farm trả lời được câu hỏi mô tả bệnh dài và câu "mô tả bệnh"; gỡ đường trả lời RAG legacy.
- Máy chủ có CPU yếu tắt được câu trả lời Qwen và giảm số đoạn đưa vào rerank bằng `deploy/.env`.
- Hỏi đáp Farm tìm bệnh từ triệu chứng trả tài liệu thay cho câu trả lời của Qwen; AI kiểm SHA-256 của crop classifier; mô tả triệu chứng gửi sang AI được che thông tin cá nhân.
- Vận hành và CI: sinh `deploy/.env` với mật khẩu ngẫu nhiên, quét lỗ hổng phụ thuộc bằng Trivy.
- Chatbot Farm/Lab dùng chung hybrid retrieval BM25/cosine, RRF và multilingual rerank; giữ truy xuất chẩn đoán/ticket legacy, mở rộng corpus canh tác và tổng hợp QA đa nguồn.
- Chatbot kiểm độ liên quan cosine trước rerank; truy vấn thiếu căn cứ không sinh câu trả lời.
  Cache BM25 theo metadata tài liệu, lấy top ứng viên cosine tại PostgreSQL và giữ snapshot nhất quán khi ingest đồng thời.
- Corpus Farm đã duyệt không lưu lại giá trị checksum khi promotion; fingerprint của mỗi lượt đánh giá nằm trong state ngoài Git.
- AI vẫn khởi động CV khi chưa có cache reranker; chatbot báo chưa sẵn sàng.
  Ingest corpus tổng quát rỗng thu hồi đúng loại tài liệu; evaluator đối chiếu evidence đã lọc theo policy khai báo.
- Qwen QA dùng khuôn JSON gọn, câu tổng quan Lab chọn dịch vụ đúng đối tượng; giữ giới hạn hàng đợi khi chạy đồng thời và công bố số đo mới nhất.
- Bộ chạy đánh giá so sánh bốn chiến lược trên 72 query, hiển thị ranking/điểm trên Langfuse local và đo tải search chatbot; dữ liệu đánh giá mới giữ trạng thái nháp.

- Chatbot Lab giữ thứ tự tài liệu truy xuất, từ chối yêu cầu thực hiện phê duyệt và không tự nhận thay đổi nghiệp vụ; vẫn hỗ trợ hỏi về quy định.
- Qwen giữ câu trả lời hợp lệ khi diễn đạt khác nguồn hoặc thiếu dấu chấm; xử lý mục phụ lỗi định dạng riêng, tiếp tục chặn mâu thuẫn bệnh và thông số điều trị.
- Qwen tổng hợp hỏi đáp web Farm/Lab và giải thích chẩn đoán Farm bằng khuôn cố định, có nguồn và kiểm nhất quán bệnh; ẩn phần sinh không đạt, chatbot thông báo theo lý do; lưu giải thích riêng, chỉ hiện cho nông dân sau duyệt.
- Màn hình giám sát và truy vết `/monitor/` cho quản trị viên, chạy riêng nên vẫn hoạt động khi backend dừng, giữ dữ liệu 30 ngày.
- Truy vết máy chủ công khai: log gateway kèm `X-Request-Id`, sự kiện đăng nhập của Keycloak và `audit_log` chỉ thêm.
- Giới hạn tốc độ theo IP thật ở gateway.
- Farm: báo cáo đang có lượt chẩn đoán AI chạy dở thì lượt thứ hai trả `409`.
- Bộ câu hỏi và nhãn đánh giá truy xuất RAG cho Farm/Lab, gồm 36 câu hỏi đáp và 6 lượt tham khảo chẩn đoán.
- Công cụ thu kết quả chỉ đọc, chấm Recall/nDCG bằng `ir_measures` và hiển thị câu hỏi, đoạn tài liệu, điểm trên Langfuse local.
- Thử tải endpoint truy xuất bằng k6, đo độ trễ, request/giây, tỷ lệ lỗi và tài nguyên Docker; xuất dữ liệu để kiểm tra lại.
- Bộ chạy đánh giá RAG độc lập bằng một lệnh, tự chuẩn bị Langfuse local, dùng lại cache và tự dọn sau khi xem.
- Mọi file Java và Python tự viết có dòng `SPDX-License-Identifier` với `Apache-2.0`.
- Tài liệu: README có mục API, CHANGELOG theo phiên bản, mật khẩu Keycloak băm Argon2id.

## [1.0.2] - 2026-10-23

- Model bệnh lá lúa DX-Farm EfficientNetV2-M (Healthy và 5 bệnh) thay detector cũ.
- Workflow `Release` phát hành image lên GHCR; `deploy/update.sh` cập nhật stack từ image đã phát hành.
- Overlay Cloudflare Tunnel cho máy chủ công khai, một biến `PUBLIC_HOST` quyết định các địa chỉ công khai, cấu hình Cloudflare được tổng hợp ở [trang docs](https://haui-implsolution.github.io/DX-OS-docs/); chạy được trên CPU ảo không có x86-64-v2.
- Farm: chẩn đoán kèm tài liệu tham khảo, nhắc khai phun chờ xác nhận, nhắc thu hoạch có giới hạn ngày, bật rule đốm nâu, thời tiết lưu theo giờ và chart thời tiết trên Superset.
- Lab: tri thức từ văn bản chính thức của HaUI kèm link tới trang gốc, cảnh báo thiết bị hỏng lặp lại, màn hình ticket và quy tắc mượn thiết bị, đặt phòng làm lại.
- Lab: kỹ thuật viên không đặt phòng hay mượn thiết bị, không ai duyệt lịch đặt phòng của chính mình; trùng mã phòng, mã tài sản hoặc mã loại sự cố trả `422` kèm câu tiếng Việt.
- Portal lấy địa chỉ BI, MinIO và chat từ cấu hình; web và mobile hiển thị tài liệu tham khảo của chẩn đoán; hộp thông báo trên mobile tải theo trang và có profile `preview` của EAS cho máy thật.

## [1.0.1] - 2026-10-15

- Farm: thời tiết theo thửa, danh sách báo cáo chưa chẩn đoán, thông báo chat kèm liên kết mở thẳng báo cáo, chart sản lượng thu hoạch.
- Mobile: thẻ thời tiết, hỏi đáp, giới hạn GPS lấy từ backend, icon và màn hình khởi động riêng của DX-Farm.
- BI: bản đồ Superset dùng nền vector tile OpenFreeMap có nhãn tiếng Việt; chart của dashboard Farm có `query_context` nên `/api/v1/chart/{id}/data` chạy được cho cả 9 chart.
- Backend ghi người tạo chẩn đoán, khóa việc chạy định kỳ giữa nhiều bản chạy; kỹ sư xem được dashboard BI.
- Webhook chat tạo lúc khởi động và backend đọc từ file; nhận ảnh nền đơn sắc.
- Tài liệu: sơ đồ kiến trúc dạng ảnh, README tiếng Anh, quy tắc ứng xử.

## [1.0.0] - 2026-10-13

Bản phát hành đầu tiên của DX-OS.

- Core dùng chung (`dx-core-api`, `dx-core`, `dx-host`): định danh và workspace, workflow, hộp duyệt, thông báo, lưu trữ, cổng AI và tri thức, bảo mật, trạng thái hệ thống.
- Hai ứng dụng chọn bằng `DXOS_APP`: DX-Farm (`app-farm`) và DX-Lab (`app-lab`).
- AI Service: nhận diện bệnh lá lúa, RAG theo namespace `farm` và `lab`, LangGraph.
- Web React dùng chung; mobile Expo cho nông dân.
- Hạ tầng Docker Compose: Keycloak, APISIX, PostgreSQL, MinIO, Superset, Mattermost, portal; E2E Farm và Lab trong CI.
- Farm: thửa và vụ mùa có trạng thái duyệt; nông dân gửi vị trí GPS có sai số tối đa 30 m; kỹ sư duyệt chẩn đoán từ ảnh và có thể yêu cầu chụp lại; ghi nhận phun thuốc tính khóa thời gian cách ly (PHI); đổi ngày thu hoạch dự kiến kèm lý do.
- Lab: ticket, mượn thiết bị và đặt phòng chạy trên workflow và hộp duyệt dùng chung của core; thống kê qua `GET /api/v1/lab-stats/summary` và 5 view `vw_lab_*` cho Superset.
- Quyền theo vai bằng `@PreAuthorize`; nông dân chỉ thấy thửa của hộ mình; quản trị viên tạo hộ kèm tài khoản Keycloak.
- Mobile đăng nhập Keycloak bằng PKCE, token lưu trong `expo-secure-store`; ảnh nén trước khi gửi; khi mất mạng, báo cáo, thửa và vụ mùa vào hàng đợi offline, mỗi mục một `Idempotency-Key`.
- Đăng nhập một lần giữa web, BI và portal, đăng xuất ở một nơi kết thúc phiên Keycloak; tài khoản chỉ có vai FARMER không vào Superset, Superset đọc dữ liệu bằng role chỉ-đọc `superset_ro`.
- Backend, Keycloak và frontend không publish cổng, mọi truy cập đi qua gateway; `deploy/demo/seed_demo.py` tạo tài khoản và dữ liệu demo.
- Tài liệu và nguồn mở: `LICENSE`, `LICENSES.md`, SBOM CycloneDX, `CONTRIBUTING.md`, mẫu issue và pull request.

[Unreleased]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v2.1.0...main
[2.1.0]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v2.0.2...v2.1.0
[2.0.2]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v2.0.1...v2.0.2
[2.0.1]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.1.1...v2.0.0
[1.1.1]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.3...v1.1.0
[1.0.3]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/HaUI-ImplSolution/DX-OS/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/HaUI-ImplSolution/DX-OS/releases/tag/v1.0.0
