# Thành phần được chọn và không chọn

Tài liệu này nói vì sao mỗi thành phần có trong DX-OS, cách dùng nó, và những thành phần đã cân nhắc nhưng không dùng.
Bảng giấy phép và phiên bản đầy đủ nằm ở [`DEPENDENCIES.txt`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/DEPENDENCIES.txt); danh mục từng gói kể cả gói dev, test, plugin build và gói gián tiếp nằm ở `docs/DEPENDENCY_INVENTORY.md` (do máy sinh và kiểm); SBOM CycloneDX của phần chạy thật nằm ở [`sbom/`](https://github.com/HaUI-ImplSolution/DX-OS/tree/main/sbom).
Hai nơi đó được máy kiểm ở mỗi pull request, tài liệu này chỉ giữ phần lý do.

## 1. Nguyên tắc chọn

- **Dùng cái đã có:** thứ đã có phần mềm nguồn mở trưởng thành thì dùng lại và cấu hình, không viết lại.
  Phần đội tự viết là phần nối các thành phần theo một tệp mô hình: định dạng mô hình, phần điều khiển, API chung, họ mẫu quy trình, bảng điều khiển.
- **Giấy phép OSI, không ngoại lệ:** mã, công cụ build và kiểm thử, mô hình AI và dữ liệu đều phải mở.
  Không dùng BUSL, SSPL, RSAL, MSCL, và không dùng phần Enterprise của một sản phẩm.
- **Không sửa mã thượng nguồn:** image bên thứ ba ghim tag và digest trong `deploy/docker-compose.yml`, tùy biến bằng cấu hình.
  Repository không có tệp `.patch`, không có `vendor/`, `node_modules/`, `third_party/` trong cây Git.
- **Không dịch vụ đóng bắt buộc:** thành phần nào gắn với một dịch vụ đóng thì để tùy chọn và công bố.
- **Thay được:** các thành phần nói chuyện bằng chuẩn mở (OIDC, S3, SQL, webhook, BPMN) nên thay một thành phần là đổi cấu hình hoặc địa chỉ.

## 2. Thành phần của lõi

| Thành phần | Giấy phép | Vai trò | Vì sao chọn | Cách dùng |
|---|---|---|---|---|
| Keycloak 26 | Apache-2.0 | Định danh, SSO | Phần mềm định danh nguồn mở trưởng thành, khai báo bằng realm và có Admin API để tạo vai, client, nhóm từ mô hình | Bản phát hành chính thức, tải về kiểm SHA-1, không sửa; realm nhập từ `sso/realm-dxos.json` |
| Apache APISIX | Apache-2.0 | Cổng API | Kiểm JWT, giới hạn tốc độ và định tuyến bằng tệp cấu hình, không cần mã | Image chính thức ghim digest; route nằm ở `deploy/apisix/` và được `check_routes.py` kiểm |
| PostgreSQL | PostgreSQL License | Dữ liệu có cấu trúc, hàng đợi outbox | Một cơ sở dữ liệu cho dữ liệu nghiệp vụ, lịch sử, outbox và quy trình, nên giao dịch bao trùm được việc ghi hồ sơ và đổi trạng thái | Image chính thức `postgres:16-bookworm` ghim digest; mỗi mô hình một schema |
| SeaweedFS | Apache-2.0 | Dữ liệu phi cấu trúc | API tương thích S3, nên đổi sang kho S3 khác chỉ đổi địa chỉ | Image chính thức ghim digest; truy cập qua AWS SDK |
| Flowable 8 | Apache-2.0 | Động cơ luồng công việc | Động cơ BPMN trưởng thành, chạy nhúng trong tiến trình và dùng chung giao dịch với việc ghi hồ sơ | Nhúng bằng `flowable-spring`; người dùng không viết BPMN, đội cung cấp ba họ mẫu có tham số |
| Apache PDFBox 3 | Apache-2.0 | Ghi PDF của mẫu in | Thư viện PDF nguồn mở của Apache chỉ ghi tệp, không có bộ dịch HTML hay script nên mẫu không chạy được mã | Mẫu in là khai báo có cấu trúc trong tệp mô hình; DX-OS tự dàn trang rồi gọi PDFBox |
| Noto Sans | OFL-1.1 | Phông nhúng trong PDF | Phủ đủ chữ tiếng Việt, và giấy phép của phông cho phép nhúng vào tài liệu | Hai tệp (thường và đậm) nằm trong resource của backend, mỗi PDF nhúng phần chữ nó dùng |
| pgschema 1.13 | Apache-2.0 | So sánh và đổi lược đồ | Khai báo trạng thái đích rồi tính câu lệnh cần chạy, giống Terraform | Binary lấy từ image chính thức ghim digest; lõi đổi tên cột trước và chặn mọi thao tác xóa mà mô hình không yêu cầu |
| ClamAV | GPL-2.0 | Quét mã độc tệp tải lên | Bộ quét nguồn mở sẵn có | Chạy ở container riêng, bật sẵn bằng profile `av`; lõi chỉ gọi qua mạng. Image `clamav` của dự án dựng trên image chính thức và đã bỏ `libclamunrar` (giấy phép UnRAR, không phải OSI); tệp RAR vì thế bị từ chối khi tải lên. |
| Prometheus 3.13 | Apache-2.0 | Đọc số đo `dxos_*` của lõi (tùy chọn) | Công cụ thu số đo phổ biến; lõi chỉ xuất định dạng Prometheus nên thay bằng công cụ khác đọc cùng định dạng được | Image chính thức ghim digest, bật bằng `docker-compose.grafana.yml`; không mở cổng ra ngoài mạng Docker |
| Grafana 12.4 (bản OSS) | AGPL-3.0-only | Vẽ dashboard số đo của lõi (tùy chọn) | Công cụ dashboard phổ biến nhất cho số đo Prometheus, đề nêu trong bảng tham chiếu | Image chính thức ghim digest, chạy nguyên bản ở dịch vụ riêng và chỉ nhận tệp cấu hình, nguồn dữ liệu và dashboard của đội; không dùng bản Enterprise, không tải plugin |
| Eclipse Mosquitto 2.0 | EPL-2.0 hoặc EDL-1.0 (dùng EPL-2.0) | Broker MQTT của cầu nối MQTT (tùy chọn) | Broker MQTT nguồn mở phổ biến nhất, nhỏ và chạy được bằng một tệp cấu hình | Image chính thức ghim digest, bật bằng `docker-compose.mqtt.yml`; mật khẩu lấy từ `.env`, không sửa mã |
| Python 3.13 (image alpine) | Python-2.0 (PSF), giấy phép của các gói Alpine của image | Chạy cầu nối MQTT (tùy chọn) | Image chính thức có sẵn CPython, nên cầu nối là một tệp script không cần dựng image riêng | Image chính thức ghim digest; cầu nối chỉ dùng thư viện chuẩn của Python, nằm ở `deploy/mqtt/` |
| Spring Boot, Java 21 | Apache-2.0, GPL-2.0 with Classpath Exception | Khung dịch vụ lõi | Khung phổ biến, có sẵn bảo mật OAuth2, Flyway và kiểm dữ liệu; ranh giới module kiểm được lúc biên dịch | Eclipse Temurin chạy nguyên bản |
| react-admin, MUI | MIT | Giao diện dựng từ metadata | Có sẵn danh sách, form và trường cho các kiểu trường của mô hình; một mô hình nạp lúc chạy không cần build lại giao diện | Chỉ dùng phần MIT, không dùng gói Enterprise |
| Monaco Editor, monaco-yaml | MIT | Trình soạn mô hình | Gợi ý và báo lỗi YAML theo JSON Schema của tệp mô hình | Chạy trong trình duyệt |
| React Flow, Dagre, Smart Edge | MIT | Sơ đồ quy trình vẽ từ tệp đang soạn | Chỉ để xem quy trình, không để thiết kế | `frontend/src/console/` |
| FullCalendar | MIT | Lịch đặt chỗ | Kéo thả để đổi giờ | |
| Fastify, @fastify/swagger, jose | MIT | Dịch vụ trợ lý tùy chọn | Tách khỏi API lõi, chỉ chạy khi bật | `assistant/`, không đưa vào module Java Core |
| Apertus-8B-Instruct-2509, llama.cpp | Apache-2.0, MIT | Model tham khảo và runtime suy luận self-host của trợ lý | Endpoint do người vận hành cố định, người dùng không nhập | Do người vận hành cấp, không đóng gói trong repository; xem `assistant/README.md` |
| restic | BSD-2-Clause | Sao lưu có mã hóa | Chạy trong container nên chỉ cần Docker | `deploy/backup.sh` |
| Trivy, gitleaks, CycloneDX | Apache-2.0, MIT | Quét lỗ hổng, bí mật và lập SBOM trong CI | Công cụ nguồn mở, chạy ở mọi pull request | `.github/workflows/` |
| reuse | Apache-2.0, GPL-3.0-or-later (mã); CC0-1.0, CC-BY-SA-4.0 (tài liệu) | Kiểm giấy phép và bản quyền của từng tệp theo chuẩn REUSE trong CI | Công cụ nguồn mở, chạy bằng `pipx run`, không đóng gói vào sản phẩm | `.github/workflows/ci-compliance.yml` |


ClamAV được giữ vì đề thi yêu cầu kiểm dịch mã độc ngay tại cửa ngõ, và nó bật mặc định.
Image `clamav` của dự án dựng trên image chính thức ghim digest và chỉ xóa `libclamunrar`, có giấy phép UnRAR không phải OSI.
ClamAV không mở được tệp RAR nên backend từ chối tệp có chữ ký RAR dù tên tệp đổi đuôi.

## 3. Đã cân nhắc nhưng không dùng

| Thành phần | Kết luận | Lý do |
|---|---|---|
| NocoBase | Không làm nền | Điều khoản 5.4 và 7.3 trong LICENSE cấm cung cấp hoặc phân phối sản phẩm no-code, low-code hay PaaS xây trên nó, dù nhãn là Apache-2.0 |
| Directus | Không dùng | Giấy phép MSCL-1.0-GPL của Monospace giới hạn mục đích sử dụng (loại trừ sử dụng cạnh tranh với bản thương mại của họ), không thuộc OSI |
| Strapi | Không làm nền | Một hệ quản trị nội dung không giao diện (headless CMS); mã ngoài thư mục `ee/` theo MIT nhưng mã trong `ee/` theo giấy phép Enterprise riêng, và MIT chỉ áp dụng khi không dùng phần `ee/` và không có tài khoản trên dịch vụ đám mây của họ, nên một kho trộn cả hai phần không đạt điều kiện nguồn mở thuần; được đánh giá ở mức giấy phép và mục đích, chưa chạy thử |
| Supabase | Chỉ đọc để học | Apache-2.0; đề gợi ý tham khảo; kiến trúc của nó (PostgreSQL, API sinh từ bảng, xác thực, lưu trữ) là mẫu để đọc, nhưng lõi cần đọc mô hình lúc chạy, đổi tên trường giữ dữ liệu và chặn đổi trạng thái ngoài quy trình, nên dịch vụ lõi tự viết phần API chung |
| Frappe | Chỉ lấy ý tưởng | Một mô hình là nguồn sự thật cho bảng, form và quyền; không dùng làm nền vì đề yêu cầu lõi không có giao diện |
| Trình thiết kế BPMN kéo thả | Không làm | Người triển khai nhập tham số, hệ thống sinh quy trình từ họ mẫu |
| Mattermost | Đã gỡ khỏi cây mã | Giấy phép bản Team Edition bị tranh cãi (MIT cho bản build, AGPL cho mã nguồn) và bản 11 giới hạn 250 người dùng; thông báo chat đi qua webhook dạng `chat` nên đổi sang Zulip, Slack hay dịch vụ khác chỉ đổi địa chỉ |
| Superset | Đã gỡ khỏi cây mã | Gỡ cùng các ứng dụng cũ; vai `report_ro` chỉ đọc dành cho công cụ báo cáo do người vận hành cấp; Superset (Apache-2.0) chỉ là ví dụ trong `docs/REPORTING.md`, cài riêng và không thuộc stack |
| Qwen2.5 | Đã gỡ | Trọng số Apache-2.0 nhưng dữ liệu huấn luyện không công khai, không đạt định nghĩa Open Source AI |
| Reranker mMARCO | Đã gỡ | Thẻ mô hình ghi Apache-2.0 nhưng MS MARCO, bộ dữ liệu nó dựa trên, chỉ cho nghiên cứu phi thương mại |
| Redis, Langfuse | Đã gỡ | Redis theo RSALv2 hoặc SSPLv1; Langfuse có phần EE riêng; cả hai chỉ phục vụ bộ đánh giá RAG đã bỏ |
| Refine | Dự phòng, không dùng | Chỉ dùng nếu react-admin không dựng được giao diện theo metadata đổi lúc chạy; điều đó đã kiểm và không xảy ra |
| Cloudflare Tunnel và Access | Ngoài repository | Hai dịch vụ là đóng. Máy chủ công khai của đội dùng chúng, cấu hình giữ ngoài repository; hệ thống chạy được không cần chúng |

Mã của bản trước (hai ứng dụng nghiệp vụ viết tay, Superset, Mattermost) nằm ở tag `v1.1.1`.

## 4. Kiểm bằng máy

| Điều kiện | Cách kiểm | Nơi chạy |
|---|---|---|
| Mọi tệp mã có header SPDX; mọi tệp có giấy phép và bản quyền theo chuẩn REUSE | `.github/scripts/check_licenses.py`, `reuse lint` với `REUSE.toml` | Job `Check licenses` của workflow `Compliance` |
| Mọi gói trong SBOM có giấy phép cho phép, ngoại lệ có lý do | `check_licenses.py`, `.github/licenses/dependency-exceptions.json` | Cùng job |
| Mọi nơi dự án khai giấy phép của chính nó (`package.json`, `pom.xml`, `CITATION.cff`, chân trang trang docs) đều là Apache-2.0 | `.github/scripts/check_licenses.py` | Job `Check licenses` của `Compliance` |
| Image, mô hình và tệp dữ liệu khai báo giấy phép | `.github/licenses/component-catalog.json` | Cùng job |
| `docs/DEPENDENCY_INVENTORY.md` liệt kê mọi gói của lockfile npm (kể cả dev), của Maven (mọi scope và plugin), mọi action và công cụ CI, mỗi gói có giấy phép OSI hoặc ngoại lệ có lý do | `.github/scripts/check_license_inventory.py check` | Job `Check licenses` của `Compliance` (npm, action, image, công cụ) và `Scan the backend SBOM` của `Dependencies` (Maven) |
| Mỗi image mà stack chạy có bảng gói và giấy phép trong `docs/IMAGE_PACKAGES.md`; hằng tuần đọc lại image và so với bảng | `.github/scripts/check_image_packages.py` | Job `Check licenses` của `Compliance` (đủ bảng) và job `Check the packages inside the images` (đọc lại image, theo lịch) |
| Image phát hành kèm `LICENSE`, `NOTICE` và văn bản giấy phép của thư viện ở `/licenses`; bản sao nằm cạnh Dockerfile khớp tệp gốc | `.github/scripts/check_third_party_notices.py`, `check_licenses.py` | Job `Scan the backend SBOM` của `Dependencies` và job `Check licenses` của `Compliance` |
| Giấy phép của từng image và bản tải về ở đúng phiên bản đang dùng đúng như đã khai và thuộc danh sách OSI; không xác định được thì đỏ | `.github/scripts/check_upstream_licenses.py` | Job `Check upstream licenses` của workflow `Compliance`, mỗi pull request và mỗi tuần |
| Giấy phép của gói Maven và npm ở phiên bản mà pull request đề xuất | `check_licenses.py sbom` trên SBOM sinh lại | Job `Check SBOMs against lockfiles` và `Scan the backend SBOM` |
| Không bí mật trong cây mã và lịch sử | gitleaks | Job `Scan for secrets` của workflow `Compliance` |
| Không dependency có lỗ hổng HIGH hoặc CRITICAL đã có bản vá | Trivy, SBOM của Maven | workflow `Dependencies`, chạy cả theo lịch |
| Không có `vendor/`, `node_modules/`, `third_party/` trong Git | `git ls-files \| grep -E '(^\|/)(vendor\|third_party\|node_modules)/'` | Chạy tay |

Gói mang giấy phép chưa nằm trong danh sách của OSI được nêu tên kèm lý do ở `.github/licenses/dependency-exceptions.json`, và lý do hiện cuối dòng của gói ở `docs/DEPENDENCY_INVENTORY.md`.
Đó là `caniuse-lite` (CC-BY-4.0) và `mdn-data` (CC0-1.0), dữ liệu chứ không phải mã, chỉ đọc lúc dựng trang docs và không có trong trang đã dựng; `jdom2` và `xz` 1.9, chỉ do plugin Maven dùng lúc dựng backend và không có trong sản phẩm; phông Codicons (CC-BY-4.0) trong bản build web, kèm ghi công ở `third-party-licenses.txt`; mô-đun Go `xi2/xz` (phạm vi công cộng) trong tệp chạy `pgschema` của image `backend`; và vài gói mà lockfile thiếu trường giấy phép nhưng văn bản giấy phép của gói là MIT hoặc BSD.
Thành phần nằm sẵn trong image bên thứ ba mà dự án kéo nguyên bản (như gói hệ điều hành của image Debian) nêu ở phần "Giấy phép ngoài danh sách OSI" của `DEPENDENCIES.txt`.
Mọi gói khác phải thuộc danh sách OSI ở `.github/licenses/osi-approved.json` (đối chiếu danh sách SPDX và opensource.org), hoặc có một lựa chọn thay thế thuộc danh sách đó như `MIT OR CC0-1.0`.

Muốn thêm một thành phần: đọc giấy phép ở kho gốc, chạy `python3 .github/scripts/check_license_inventory.py generate` (thêm `--maven` khi đổi Maven) để cập nhật `docs/DEPENDENCY_INVENTORY.md`, ghi vai trò vào các mục 2 đến 4 nếu là thành phần cấp cao, và nếu là image hoặc mô hình thì khai báo ở `.github/licenses/component-catalog.json`; `check_licenses.py` và `check_license_inventory.py check` sẽ chặn nếu thiếu.
