# Thành phần được chọn và không chọn

Tài liệu này nói vì sao mỗi thành phần có trong DX-OS, cách dùng nó, và những thành phần đã cân nhắc nhưng không dùng.
Bảng giấy phép và phiên bản đầy đủ nằm ở [`COPYRIGHT.txt`](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/COPYRIGHT.txt); danh mục từng gói kể cả gói gián tiếp nằm ở [`sbom/`](https://github.com/HaUI-ImplSolution/DX-OS/tree/main/sbom).
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
| PostgreSQL, pgvector | PostgreSQL License | Dữ liệu có cấu trúc, hàng đợi outbox, vector cho `ai/` | Một cơ sở dữ liệu cho dữ liệu nghiệp vụ, lịch sử, outbox và quy trình, nên giao dịch bao trùm được việc ghi hồ sơ và đổi trạng thái | Image `pgvector/pgvector` ghim digest; mỗi mô hình một schema |
| SeaweedFS | Apache-2.0 | Dữ liệu phi cấu trúc | API tương thích S3, nên đổi sang kho S3 khác chỉ đổi địa chỉ | Image chính thức ghim digest; truy cập qua AWS SDK |
| Flowable 8 | Apache-2.0 | Động cơ luồng công việc | Động cơ BPMN trưởng thành, chạy nhúng trong tiến trình và dùng chung giao dịch với việc ghi hồ sơ | Nhúng bằng `flowable-spring`; người dùng không viết BPMN, đội cung cấp ba họ mẫu có tham số |
| pgschema 1.13 | Apache-2.0 | So sánh và đổi lược đồ | Khai báo trạng thái đích rồi tính câu lệnh cần chạy, giống Terraform | Binary lấy từ image chính thức ghim digest; lõi đổi tên cột trước và chặn mọi thao tác xóa mà mô hình không yêu cầu |
| ClamAV | GPL-2.0 | Quét mã độc tệp tải lên | Bộ quét nguồn mở sẵn có | Chạy ở container riêng, bật bằng profile `av`; lõi chỉ gọi qua mạng |
| Spring Boot, Java 21 | Apache-2.0, GPL-2.0 with Classpath Exception | Khung dịch vụ lõi | Khung phổ biến, có sẵn bảo mật OAuth2, Flyway và kiểm dữ liệu; ranh giới module kiểm được lúc biên dịch | Eclipse Temurin chạy nguyên bản |
| react-admin, MUI | MIT | Giao diện dựng từ metadata | Có sẵn danh sách, form và trường cho các kiểu trường của mô hình; một mô hình nạp lúc chạy không cần build lại giao diện | Chỉ dùng phần MIT, không dùng gói Enterprise |
| Monaco Editor, monaco-yaml | MIT | Trình soạn mô hình | Gợi ý và báo lỗi YAML theo JSON Schema của tệp mô hình | Chạy trong trình duyệt |
| react-jsonschema-form, Ajv | Apache-2.0, MIT | Form theo tham số của họ mẫu, kiểm schema | Bảng điều khiển dựng form từ schema nên thêm tham số không phải viết giao diện | |
| React Flow | MIT | Sơ đồ quy trình sinh ra | Chỉ để xem quy trình, không để thiết kế | |
| FullCalendar | MIT | Lịch đặt chỗ | Kéo thả để đổi giờ | |
| restic | BSD-2-Clause | Sao lưu có mã hóa | Chạy trong container nên chỉ cần Docker | `deploy/backup.sh` |
| Trivy, gitleaks, CycloneDX | Apache-2.0, MIT | Quét lỗ hổng, bí mật và lập SBOM trong CI | Công cụ nguồn mở, chạy ở mọi pull request | `.github/workflows/` |

Mã `ai/` theo AGPL-3.0 và chạy ở tiến trình riêng, chỉ giao tiếp qua HTTP, nên phần còn lại giữ Apache-2.0.
Embedding `paraphrase-multilingual-MiniLM-L12-v2` theo Apache-2.0; dữ liệu huấn luyện của nó chưa kiểm theo định nghĩa Open Source AI của OSI và được khai báo như vậy ở `.github/license-catalog.json`.

## 3. Đã cân nhắc nhưng không dùng

| Thành phần | Kết luận | Lý do |
|---|---|---|
| NocoBase | Không làm nền | Điều khoản 5.4 và 7.3 trong LICENSE cấm cung cấp hoặc phân phối sản phẩm no-code, low-code hay PaaS xây trên nó, dù nhãn là Apache-2.0 (kiểm ngày 02/11/2026) |
| Directus | Không dùng | Giấy phép có điều kiện, không thuộc OSI (kiểm ngày 02/11/2026) |
| Supabase | Chỉ đọc để học | Đề gợi ý tham khảo; kiến trúc của nó (PostgreSQL, API sinh từ bảng, xác thực, lưu trữ) là mẫu để đọc, nhưng lõi cần đọc mô hình lúc chạy, đổi tên trường giữ dữ liệu và chặn đổi trạng thái ngoài quy trình, nên dịch vụ lõi tự viết phần API chung |
| Frappe | Chỉ lấy ý tưởng | Một mô hình là nguồn sự thật cho bảng, form và quyền; không dùng làm nền vì đề yêu cầu lõi không có giao diện |
| Trình thiết kế BPMN kéo thả | Không làm | Người triển khai nhập tham số, hệ thống sinh quy trình từ họ mẫu |
| Mattermost | Đã gỡ khỏi cây mã | Giấy phép bản Team Edition bị tranh cãi (MIT cho bản build, AGPL cho mã nguồn) và bản 11 giới hạn 250 người dùng; thông báo chat đi qua webhook dạng `chat` nên đổi sang Zulip, Slack hay dịch vụ khác chỉ đổi địa chỉ |
| Superset | Đã gỡ khỏi cây mã | Gỡ cùng các ứng dụng cũ; báo cáo dựng theo mô hình để sau, còn vai `report_ro` chỉ đọc dành cho công cụ báo cáo do người vận hành cấp |
| Qwen2.5 | Đã gỡ | Trọng số Apache-2.0 nhưng dữ liệu huấn luyện không công khai, không đạt định nghĩa Open Source AI; `ai/` không dùng mô hình ngôn ngữ cho đến khi có mô hình đạt |
| Reranker mMARCO | Đã gỡ | Thẻ mô hình ghi Apache-2.0 nhưng MS MARCO, bộ dữ liệu nó dựa trên, chỉ cho nghiên cứu phi thương mại |
| Redis, Langfuse | Đã gỡ | Redis theo RSALv2 hoặc SSPLv1; Langfuse có phần EE riêng; cả hai chỉ phục vụ bộ đánh giá RAG đã bỏ |
| Refine | Dự phòng, không dùng | Chỉ dùng nếu react-admin không dựng được giao diện theo metadata đổi lúc chạy; điều đó đã kiểm và không xảy ra |
| Cloudflare Tunnel và Access | Tùy chọn | `cloudflared` theo Apache-2.0 nhưng hai dịch vụ là đóng; chỉ dùng cho bản triển khai công khai, tắt mặc định và công bố ở `COPYRIGHT.txt` |

Mã của bản trước (hai ứng dụng nghiệp vụ viết tay, Superset, Mattermost) nằm ở tag `v1.1.1`.
Strapi chưa được đánh giá riêng.

## 4. Kiểm bằng máy

| Điều kiện | Cách kiểm | Nơi chạy |
|---|---|---|
| Mọi tệp mã có header SPDX | `.github/scripts/check_licenses.py` | Job `license` của `CI Infra` |
| Mọi gói trong SBOM có giấy phép cho phép, ngoại lệ có lý do | `check_licenses.py`, `.github/license-exceptions.json` | Cùng job |
| Image, mô hình và tệp dữ liệu khai báo giấy phép | `.github/license-catalog.json` | Cùng job |
| Không bí mật trong cây mã và lịch sử | gitleaks | Job `secret-scan` |
| Không dependency có lỗ hổng HIGH hoặc CRITICAL đã có bản vá | Trivy, SBOM của Maven | `CI Dependencies`, chạy cả theo lịch |
| Không có `vendor/`, `node_modules/`, `third_party/` trong Git | `git ls-files \| grep -E '(^\|/)(vendor\|third_party\|node_modules)/'` | Chạy tay |

Muốn thêm một thành phần: đọc giấy phép ở kho gốc, ghi vào `COPYRIGHT.txt`, và nếu là image hoặc mô hình thì khai báo ở `.github/license-catalog.json`; `check_licenses.py` sẽ chặn nếu thiếu.
