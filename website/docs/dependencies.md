# Thành phần được chọn và không chọn

Tài liệu này nói vì sao mỗi thành phần có trong DX-OS, cách dùng nó, và những thành phần đã cân nhắc nhưng không dùng.
Bảng giấy phép và phiên bản đầy đủ nằm ở [`DEPENDENCIES.txt`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/DEPENDENCIES.txt); danh mục từng gói kể cả gói gián tiếp nằm ở [`sbom/`](https://github.com/HaUI-ImplSolution/DX-OS/tree/main/sbom).
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
| ClamAV | GPL-2.0 | Quét mã độc tệp tải lên | Bộ quét nguồn mở sẵn có | Chạy ở container riêng, bật sẵn bằng profile `av`; lõi chỉ gọi qua mạng. Image của ClamAV kèm `libclamunrar` (giải nén RAR) theo giấy phép UnRAR, không phải mã mở; nạp lúc chạy, không liên kết với `libclamav`, và dự án không sửa hay phân phối lại nó |
| Spring Boot, Java 21 | Apache-2.0, GPL-2.0 with Classpath Exception | Khung dịch vụ lõi | Khung phổ biến, có sẵn bảo mật OAuth2, Flyway và kiểm dữ liệu; ranh giới module kiểm được lúc biên dịch | Eclipse Temurin chạy nguyên bản |
| react-admin, MUI | MIT | Giao diện dựng từ metadata | Có sẵn danh sách, form và trường cho các kiểu trường của mô hình; một mô hình nạp lúc chạy không cần build lại giao diện | Chỉ dùng phần MIT, không dùng gói Enterprise |
| Monaco Editor, monaco-yaml | MIT | Trình soạn mô hình | Gợi ý và báo lỗi YAML theo JSON Schema của tệp mô hình | Chạy trong trình duyệt |
| React Flow, Dagre, Smart Edge | MIT | Sơ đồ quy trình vẽ từ tệp đang soạn | Chỉ để xem quy trình, không để thiết kế | `frontend/src/console/` |
| FullCalendar | MIT | Lịch đặt chỗ | Kéo thả để đổi giờ | |
| restic | BSD-2-Clause | Sao lưu có mã hóa | Chạy trong container nên chỉ cần Docker | `deploy/backup.sh` |
| Trivy, gitleaks, CycloneDX | Apache-2.0, MIT | Quét lỗ hổng, bí mật và lập SBOM trong CI | Công cụ nguồn mở, chạy ở mọi pull request | `.github/workflows/` |
| reuse | Apache-2.0, GPL-3.0-or-later (mã); CC0-1.0, CC-BY-SA-4.0 (tài liệu) | Kiểm giấy phép và bản quyền của từng tệp theo chuẩn REUSE trong CI | Công cụ nguồn mở, chạy bằng `pipx run`, không đóng gói vào sản phẩm | `.github/workflows/ci-compliance.yml` |


ClamAV được giữ vì đề thi yêu cầu kiểm dịch mã độc ngay tại cửa ngõ, và nó bật mặc định.
Dự án kéo image chính thức nguyên bản khi chạy, không đóng gói và không phân phối lại nó.
`libclamunrar` nạp lúc chạy, không liên kết với `libclamav`; thiếu nó thì ClamAV vẫn chạy, chỉ không giải nén RAR.

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
| Mọi gói trong SBOM có giấy phép cho phép, ngoại lệ có lý do | `check_licenses.py`, `.github/dependency-exceptions.json` | Cùng job |
| Image, mô hình và tệp dữ liệu khai báo giấy phép | `.github/component-catalog.json` | Cùng job |
| Giấy phép của từng image và bản tải về ở đúng phiên bản đang dùng đúng như đã khai và thuộc danh sách OSI; không xác định được thì đỏ | `.github/scripts/check_upstream_licenses.py` | Job `Check upstream licenses` của workflow `Compliance`, mỗi pull request và mỗi tuần |
| Giấy phép của gói Maven và npm ở phiên bản mà pull request đề xuất | `check_licenses.py sbom` trên SBOM sinh lại | Job `Check SBOMs against lockfiles` và `Scan the backend SBOM` |
| Không bí mật trong cây mã và lịch sử | gitleaks | Job `Scan for secrets` của workflow `Compliance` |
| Không dependency có lỗ hổng HIGH hoặc CRITICAL đã có bản vá | Trivy, SBOM của Maven | workflow `Dependencies`, chạy cả theo lịch |
| Không có `vendor/`, `node_modules/`, `third_party/` trong Git | `git ls-files \| grep -E '(^\|/)(vendor\|third_party\|node_modules)/'` | Chạy tay |

Hai gói mang giấy phép chưa nằm trong danh sách của OSI được nêu tên kèm lý do ở `.github/dependency-exceptions.json`: `caniuse-lite` (CC-BY-4.0) và `mdn-data` (CC0-1.0) là dữ liệu chứ không phải mã, chỉ đọc lúc dựng trang docs và không có trong trang đã dựng.
Mọi gói khác phải thuộc danh sách OSI trong `check_licenses.py`, hoặc có một lựa chọn thay thế thuộc danh sách đó như `MIT OR CC0-1.0`.

Muốn thêm một thành phần: đọc giấy phép ở kho gốc, ghi vào `DEPENDENCIES.txt`, và nếu là image hoặc mô hình thì khai báo ở `.github/component-catalog.json`; `check_licenses.py` sẽ chặn nếu thiếu.
