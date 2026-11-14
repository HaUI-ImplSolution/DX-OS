# Thuật ngữ

Các từ dùng trong tài liệu và giao diện của DX-OS.
Mỗi từ được giải thích trong một hoặc hai dòng, kèm tài liệu để đọc kỹ hơn.

## 1. Sản phẩm và kiến trúc

| Thuật ngữ | Nghĩa | Xem thêm |
|---|---|---|
| DX-OS | Lõi dynamic nguồn mở cho hệ thống hồ sơ có quy trình: khai báo nghiệp vụ trong một tệp mô hình, lõi dựng phần còn lại | [`README.md`](../README.md) |
| Lõi, Tầng 1 | Phần chạy ngầm, không có giao diện, chỉ có API: định danh, cổng API, dữ liệu, quy trình, lịch sử, sự kiện | [`ARCHITECTURE.md`](../ARCHITECTURE.md#1-ba-tầng) |
| Ứng dụng, Tầng 2 | Giao diện web, bảng điều khiển, portal, màn hình giám sát và lệnh `dxos`, dựng từ dữ liệu của lõi và gọi lõi qua cổng API | [`ARCHITECTURE.md`](../ARCHITECTURE.md#1-ba-tầng) |
| Mô hình, Tầng 3 | Các tệp mô tả nghiệp vụ; thêm một nghiệp vụ mới là thêm một tệp, không viết mã | [`MODEL.md`](./MODEL.md) |
| H-P-D-I | Bốn không gian của đề thi: H môi trường số chung, P rào chắn và luồng tự động, D một nguồn sự thật, I trí tuệ nhân tạo | [`README.md`](../README.md#đối-chiếu-với-đề-thi) |
| Headless | Không có giao diện: lõi chỉ phục vụ API cho ứng dụng tầng trên | [`ARCHITECTURE.md`](../ARCHITECTURE.md) |
| Cổng API, APISIX | Cổng duy nhất mở ra ngoài: kiểm token ở biên, giới hạn tốc độ, định tuyến đến web, API, Keycloak và giám sát | [`ARCHITECTURE.md`](../ARCHITECTURE.md#3-định-danh-và-token) |
| `dx-engine` | Dịch vụ lõi chạy được: phần điều khiển, API chung và Flowable nhúng | [`ARCHITECTURE.md`](../ARCHITECTURE.md#2-module-backend) |
| `dx-model` | Module đọc và kiểm tra tệp mô hình, báo lỗi theo dòng và cột; không phụ thuộc Spring | [`ARCHITECTURE.md`](../ARCHITECTURE.md#2-module-backend) |
| `dx-core` | Thư viện dùng chung của backend: bảo mật, audit, lưu trữ, quét tệp, client Keycloak | [`ARCHITECTURE.md`](../ARCHITECTURE.md#2-module-backend) |
| `dx-monitor` | Dịch vụ giám sát chạy riêng, không phụ thuộc các module còn lại | [`docs/MONITORING.md`](MONITORING.md) |
| Bảng điều khiển | Trang `/console` của người triển khai (ADMIN): thư viện mẫu, soạn mô hình, xem kế hoạch, áp dụng, người dùng, ứng dụng ngoài, danh mục dữ liệu | [`TESTING.md`](./TESTING.md) |
| Portal | Trang `/portal/` liệt kê các địa chỉ và trạng thái dịch vụ | [`README.md`](../README.md) |
| Chế độ chỉ lõi | `dxos start --core` chỉ chạy Tầng 1, không có ứng dụng web, portal và giám sát | [`OPERATIONS.md`](OPERATIONS.md#chế-độ-chỉ-lõi) |
| Điểm mở rộng | Chỗ để người dùng viết thêm dịch vụ, bên nhận webhook, giao diện riêng hay route riêng ở ngoài lõi | [`EXTENSIONS.md`](./EXTENSIONS.md) |

## 2. Tệp mô hình

| Thuật ngữ | Nghĩa | Xem thêm |
|---|---|---|
| Tệp mô hình | Một tệp YAML khai báo thực thể, trường, vai, quyền và quy trình của một nghiệp vụ | [`MODEL.md`](./MODEL.md) |
| Định dạng (`format`) | Số phiên bản của cú pháp tệp mô hình; lõi tự nâng tệp cũ trong bộ nhớ, và `dxos model upgrade` ghi ra tệp mới | [`MODEL.md`](./MODEL.md#nâng-định-dạng) |
| Thực thể | Một loại bản ghi của nghiệp vụ, như hồ sơ hay thiết bị; mỗi thực thể thành một bảng dữ liệu | [`MODEL.md`](./MODEL.md#trường-và-đơn-vị) |
| Trường | Một cột của thực thể, có kiểu (`text`, `number`, `date`, `choice`, `reference`, `file`...) và các ràng buộc | [`MODEL.md`](./MODEL.md#trường-và-đơn-vị) |
| Vai | Nhóm quyền của một người dùng trong một mô hình; vai của mô hình nằm ở client `dxos-m-<mô hình>` trong Keycloak | [`ARCHITECTURE.md`](../ARCHITECTURE.md#3-định-danh-và-token) |
| `ADMIN` | Vai của nền tảng, không thuộc mô hình nào: dùng bảng điều khiển và quản trị người dùng | [`OPERATIONS.md`](OPERATIONS.md) |
| Đơn vị | Cơ cấu tổ chức, là một nhóm Keycloak dùng chung cho mọi mô hình; chỉ quyết định phạm vi dòng, không quyết định quyền làm gì | [`MODEL.md`](./MODEL.md#trường-và-đơn-vị) |
| Quyền theo dòng | Mức một vai được chạm tới bản ghi: `all` mọi dòng, `read` mọi dòng chỉ đọc, `own` dòng mình tạo, `unit` dòng của đơn vị mình, `assigned` dòng giao cho mình | [`MODEL.md`](./MODEL.md#quyền-theo-vai) |
| Mẫu có sẵn | Mô hình mẫu chọn được ngay ở bảng điều khiển, nằm ở `backend/dx-engine/src/main/resources/templates/` | [`MODEL.md`](./MODEL.md) |
| Mã hồ sơ | Trường `type: code` cho mỗi hồ sơ một mã ngắn không trùng, như `HS-2026-00007`, để đọc, in và tra | [`MODEL.md`](./MODEL.md#mã-hồ-sơ-đọc-được) |
| `required_when` | Khóa làm một trường chỉ bắt buộc khi một trường khác có giá trị nhất định | [`MODEL.md`](./MODEL.md#bắt-buộc-có-điều-kiện) |
| Khối `ui` | Phần của tệp mô hình chọn biểu tượng, nhóm menu, cột, nhóm biểu mẫu và lời gợi ý | [`MODEL.md`](./MODEL.md#giao-diện-ui) |
| Tài liệu in | Khóa `documents` khai báo mẫu giấy xác nhận, phiếu hay biên bản; mỗi hồ sơ thành một tệp PDF | [`MODEL.md`](./MODEL.md#tài-liệu-in) |

## 3. Quy trình

| Thuật ngữ | Nghĩa | Xem thêm |
|---|---|---|
| Họ mẫu | Khung quy trình viết sẵn: `approval` (chuỗi duyệt), `work_item` (công việc cần xử lý), `reservation` (chiếm dụng tài nguyên theo khung giờ) | [`MODEL.md`](./MODEL.md#quy-trình) |
| Trạng thái | Chỗ một hồ sơ đang đứng trong quy trình; chỉ quy trình đổi được cột trạng thái | [`MODEL.md`](./MODEL.md#tự-khai-báo-hoặc-mở-rộng-quy-trình) |
| Hành động | Một bước chuyển trạng thái do một vai thực hiện, có thể cần lý do, trường bắt buộc hoặc điều kiện `when` | [`MODEL.md`](./MODEL.md#tự-khai-báo-hoặc-mở-rộng-quy-trình) |
| Trạng thái cuối | Trạng thái đóng hồ sơ: sau đó hồ sơ bị đóng băng và ghi ai quyết định, lúc nào | [`MODEL.md`](./MODEL.md#tự-khai-báo-hoặc-mở-rộng-quy-trình) |
| Hộp việc | Danh sách việc đang chờ của một người; tự làm mới khi hồ sơ đổi | [`TESTING.md`](./TESTING.md#giao-diện-web) |
| Hạn xử lý | Thời gian một hồ sơ được xử lý (`deadline`); quá hạn thì ghi vào lịch sử và nhắc | [`MODEL.md`](./MODEL.md#quy-trình) |
| Leo thang | Các mức báo tiếp cho vai khác khi hồ sơ quá hạn lâu; chỉ thông báo, không đổi trạng thái hay người giữ hồ sơ | [`MODEL.md`](./MODEL.md#quy-trình) |
| Hành động hoàn tất | Thao tác chạy sau khi hồ sơ đến một trạng thái cuối, như thêm người vào nhóm Keycloak; chạy sau giao dịch và thử lại nếu lỗi | [`MODEL.md`](./MODEL.md#hành-động-hoàn-tất) |
| `check` | Khóa của một hành động cho một dịch vụ riêng quyết định trước khi trạng thái đổi; bị từ chối thì trả 403 | [`MODEL.md`](./MODEL.md#tự-khai-báo-hoặc-mở-rộng-quy-trình) |
| Rút lại | Người nộp lấy lại hồ sơ ở các trạng thái khai trong `withdraw` | [`MODEL.md`](./MODEL.md#quy-trình) |
| Quy tắc bất biến | Các quy tắc nằm trong lõi mà mô hình không tắt được: chỉ quy trình đổi trạng thái, người tạo không tự duyệt, mỗi bước để lại lịch sử | [`dynamic-core/README.md`](./dynamic-core/README.md#quy-trình-chung) |
| Flowable | Động cơ quy trình nhúng trong `dx-engine`; BPMN do lõi sinh từ tệp mô hình | [`ARCHITECTURE.md`](../ARCHITECTURE.md#6-quy-trình-và-trạng-thái) |

## 4. Đổi mô hình khi hệ thống đang chạy

| Thuật ngữ | Nghĩa | Xem thêm |
|---|---|---|
| Kế hoạch (`plan`) | Kết quả xem trước một lần đổi: những gì sẽ đổi, mức rủi ro và dòng đang vi phạm; chưa đổi gì | [`ARCHITECTURE.md`](../ARCHITECTURE.md#4-áp-dụng-một-mô-hình) |
| Áp dụng (`apply`) | Đưa hệ thống về đúng tệp mô hình, từng bước một và ghi lại từng bước | [`ARCHITECTURE.md`](../ARCHITECTURE.md#4-áp-dụng-một-mô-hình) |
| `SAFE`, `MIGRATION`, `LOSS` | Ba mức rủi ro của kế hoạch: áp dụng được ngay; cần chuyển dữ liệu (dòng vi phạm thì dừng); mất dữ liệu nên cần xác nhận riêng | [`ARCHITECTURE.md`](../ARCHITECTURE.md#4-áp-dụng-một-mô-hình) |
| Áp dụng dở | Một bước lỗi làm lần áp dụng dừng; mô hình bị giữ cho tới khi quản trị viên chọn tiếp tục (`resume`) hoặc hủy bỏ (`abandon`) | [`ARCHITECTURE.md`](../ARCHITECTURE.md#giữ-mô-hình-và-áp-dụng-dở) |
| Sửa chữa (`repair`) | Áp dụng lại tệp không đổi để dựng lại thứ bị mất ở bảng hay Keycloak, không tăng phiên bản | [`ARCHITECTURE.md`](../ARCHITECTURE.md#4-áp-dụng-một-mô-hình) |
| Quay lui (`rollback`) | Áp lại nội dung phiên bản trước, ghi thành một phiên bản mới | [`ARCHITECTURE.md`](../ARCHITECTURE.md#4-áp-dụng-một-mô-hình) |
| `renamed_from` | Khai báo tên cũ của một trường để đổi tên mà giữ nguyên dữ liệu | [`MODEL.md`](./MODEL.md#đổi-mô-hình-khi-đã-có-dữ-liệu) |
| `pgschema` | Công cụ nguồn mở so bảng với tệp mô hình và đổi bảng cho khớp; lõi lo đổi tên và chặn mất dữ liệu | [`ARCHITECTURE.md`](../ARCHITECTURE.md#4-áp-dụng-một-mô-hình) |
| GitOps | `dxos gitops` kéo hạ tầng và tệp mô hình từ git theo chu kỳ, rồi báo lệch, chờ duyệt hoặc tự áp dụng | [`OPERATIONS.md`](OPERATIONS.md) |
| `dxos lint` | Kiểm tệp mô hình bằng đúng bộ đọc của backend, không cần hệ thống đang chạy | [`MODEL.md`](./MODEL.md#kiểm-tệp-trước-khi-áp-dụng) |

## 5. Dữ liệu và sự kiện

| Thuật ngữ | Nghĩa | Xem thêm |
|---|---|---|
| Lịch sử chống sửa | Các dòng lịch sử của hồ sơ nối thành một chuỗi hash; sửa, xóa hay chèn một dòng thì `dxos history verify` chỉ ra dòng đầu tiên bị đổi | [`INTEGRATIONS.md`](INTEGRATIONS.md) |
| Điểm neo | Mỗi giờ lõi ghi mốc của chuỗi hash vào kho đối tượng, ngoài PostgreSQL, để so lại khi kiểm | [`ARCHITECTURE.md`](../ARCHITECTURE.md#10-toàn-vẹn-và-phục-hồi) |
| Outbox | Bảng trong PostgreSQL giữ các sự kiện chờ gửi, để gửi lại khi bên nhận lỗi | [`INTEGRATIONS.md`](INTEGRATIONS.md) |
| Webhook | Thông báo mỗi thay đổi của hồ sơ tới một địa chỉ web hoặc kênh chat, có chữ ký để bên nhận kiểm tra | [`EXTENSIONS.md`](./EXTENSIONS.md#bên-nhận-webhook) |
| `Idempotency-Key` | Header cho yêu cầu tạo hồ sơ hay hành động: gửi lại cùng khóa và cùng nội dung chỉ làm một lần | [`EXTENSIONS.md`](./EXTENSIONS.md#3-gọi-dữ-liệu-và-quy-trình) |
| Hồ dữ liệu | Kho Parquet trong SeaweedFS chứa hồ sơ đã đóng và lịch sử của chúng, kèm manifest và hash; đọc bằng DuckDB | [`ARCHITECTURE.md`](../ARCHITECTURE.md#10-toàn-vẹn-và-phục-hồi) |
| Danh mục dữ liệu | Trang cho biết dữ liệu của mỗi mô hình nằm ở đâu, ai được xem, đi vào và đi ra đâu, và chất lượng ra sao | [`ARCHITECTURE.md`](../ARCHITECTURE.md#11-dữ-liệu) |
| Phả hệ | Với mỗi trường: nơi giá trị đi vào, các quy tắc đọc nó và nơi nó đi ra | [`ARCHITECTURE.md`](../ARCHITECTURE.md#11-dữ-liệu) |
| `report_ro` | Vai PostgreSQL chỉ đọc cho công cụ báo cáo; thấy mọi dòng, không áp quyền theo dòng | [`REPORTING.md`](REPORTING.md) |
| OpenAPI theo mô hình | Tài liệu API sinh ngay lúc gọi từ mô hình đang chạy, nên luôn khớp tệp mô hình | [`API.md`](./API.md) |

## 6. Đăng nhập, vận hành và giấy phép

| Thuật ngữ | Nghĩa | Xem thêm |
|---|---|---|
| Keycloak, realm `dxos` | Dịch vụ đăng nhập một lần; giữ tài khoản, mật khẩu và vai | [`ARCHITECTURE.md`](../ARCHITECTURE.md#3-định-danh-và-token) |
| Client | Một ứng dụng đăng ký với Keycloak: `dxos-web` cho web, `dxos-cli` cho lệnh, `dxos-m-<mô hình>` giữ vai của mô hình, `dxos-app-<tên>` cho ứng dụng ngoài | [`ARCHITECTURE.md`](../ARCHITECTURE.md#3-định-danh-và-token) |
| Token máy | Token một ứng dụng ngoài lấy bằng mã bí mật của nó; tài khoản dịch vụ giữ đúng một vai của một mô hình | [`EXTENSIONS.md`](./EXTENSIONS.md#dịch-vụ-riêng-gọi-api) |
| OIDC, PKCE | Cách giao diện ở tên miền khác đăng nhập người dùng, không giữ mã bí mật trong trình duyệt | [`EXTENSIONS.md`](./EXTENSIONS.md#giao-diện-ngoài) |
| Tự phục hồi (`reconcile`) | `dxos reconcile` đưa stack về đúng khai báo, tạo lại container bị xóa hay lệch; chạy mỗi phút bằng systemd timer | [`OPERATIONS.md`](OPERATIONS.md) |
| ClamAV | Dịch vụ quét mã độc tệp tải lên; tệp chưa quét được thì bị từ chối | [`OPERATIONS.md`](OPERATIONS.md#quét-mã-độc-tệp-tải-lên) |
| SPDX, REUSE | Dòng `SPDX-License-Identifier` ở đầu mỗi tệp mã và `REUSE.toml` cho tệp không chèn được; `reuse lint` kiểm cả cây | [`CONTRIBUTING.md`](../CONTRIBUTING.md) |
| SBOM | Danh mục thành phần (CycloneDX) của backend, web và trang docs, ở `sbom/` | [`sbom/README.md`](../sbom/README.md) |
| OSI | Tổ chức công nhận giấy phép nguồn mở; mọi thành phần của DX-OS dùng giấy phép được OSI công nhận | [`DEPENDENCIES.txt`](../DEPENDENCIES.txt) |
