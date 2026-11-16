# Báo cáo vấn đề DX-OS v2.0.0 — Đối chiếu với quy trình tích hợp kỳ vọng trên Windows

**Ngày đối chiếu:** 16/11/2026  
**Phạm vi:** Chỉ đánh giá **bản phát hành DX-OS `v2.0.0`**: tài sản GitHub Release và tài liệu/script tại đúng tag `v2.0.0`. **Không sử dụng nhánh `main`, thay đổi chưa phát hành hoặc mã nguồn của ứng dụng khách hàng để kết luận vấn đề.**  
**Bối cảnh:** Khách hàng có sẵn ứng dụng độc lập, dùng Windows 10/11, PowerShell/Windows Terminal và Docker Desktop; ban đầu chưa có file hoặc công cụ DX-OS.

> **Mức độ xác nhận:** Đây là rà soát nội dung **được phân phối và hướng dẫn** trong release, chưa phải kết quả chạy thử trên Windows. Chưa trực tiếp giải nén toàn bộ asset `.tar.gz`; do đó “chưa công bố/chưa có artifact riêng” không đồng nghĩa “đã chứng minh mã chạy lỗi hoặc hoàn toàn không có bên trong archive”. Các vấn đề dưới đây là **khoảng trống cần được giải quyết nếu DX-OS muốn đáp ứng quy trình Windows kỳ vọng**, không quy kết mọi khoảng trống là lỗi chức năng của Core.

## A. Các vấn đề của DX-OS v2.0.0

### Cài đặt, vận hành và phân phối trên Windows

1. **DXOS-01 — Chưa công bố bộ cài Windows chính thức.** Release cung cấp `dxos-install.sh` và `.tar.gz`; README v2.0.0 hướng dẫn cài đặt trên Linux, không có installer PowerShell được phát hành riêng. **Ảnh hưởng:** Bước 1.
2. **DXOS-02 — Công cụ vận hành chưa có bảo đảm tương thích Windows.** CLI/bộ cài đang là Bash; `doctor.sh` đọc `/proc/meminfo` và dùng tiện ích Unix. Chưa có CLI Windows được công bố cho `start`, `plan`, `apply`, `client add`. **Ảnh hưởng:** Bước 1, 3, 4.
3. **DXOS-03 — Thiếu hướng dẫn vận hành Windows/Docker Desktop theo release.** Chưa có quy trình chính thức dùng PowerShell để cài, xác định đường dẫn, cấu hình gateway, mạng container–host và khởi động nền tảng trên Windows. **Ảnh hưởng:** Bước 1, 5.
4. **DXOS-04 — Chưa có gói công cụ tích hợp Windows có thể lấy trực tiếp từ release.** Danh sách asset v2.0.0 chưa cung cấp một bộ tài liệu/mẫu tích hợp được phân phối đồng bộ với installer cho khách hàng Windows. **Ảnh hưởng:** Bước 1, 2, 5.

### Cấu hình và tài liệu tích hợp REST API

5. **DXOS-05 — Chưa công bố mẫu cấu hình kết nối REST dành cho ứng dụng ngoài như một phần gói tích hợp.** `deploy/.env.example` mô tả cấu hình **nền tảng DX-OS**, không phải một mẫu đầu vào của ứng dụng tiêu thụ API có chỉ dẫn nguồn giá trị URL, token endpoint, model/entity và client credential. **Ảnh hưởng:** Bước 5.
6. **DXOS-06 — Chưa có hướng dẫn onboarding REST API xuyên suốt dành cho khách hàng Windows.** Release đã có mô tả `client_credentials`, API, metadata và workflow nhưng chúng nằm ở nhiều mục; chưa có luồng hướng dẫn liên tục từ cấp client đến đọc hợp đồng model, tạo hồ sơ, gọi action và xử lý phản hồi trong ứng dụng ngoài bằng công cụ Windows. **Ảnh hưởng:** Bước 4, 6.
7. **DXOS-07 — Chưa phát hành kèm một ví dụ client REST độc lập, sử dụng lại được ngay từ gói Windows.** Release notes đề cập `examples/itot-simulator`, nhưng danh sách tài sản phát hành không có một gói ví dụ tích hợp HTTP dành riêng cho khách hàng Windows. **Ảnh hưởng:** Bước 6.

**Không đưa vào danh sách vấn đề:** thiếu SDK Java/Spring Boot (REST API có thể được gọi trực tiếp); không có model riêng theo nghiệp vụ từng khách hàng; thiếu script, file `.env`, adapter hoặc công cụ di chuyển dữ liệu trong ứng dụng khách hàng; thiếu lệnh `dxos lint` (v2.0.0 đã hỗ trợ `plan` và `apply`). Các điểm này **không phải điều kiện buộc DX-OS phải sửa** để đạt quy trình kỳ vọng dưới đây.

## B. Đối chiếu vấn đề ↔ quy trình kỳ vọng

| Bước kỳ vọng | Vấn đề DX-OS phải được giải quyết | Điều kiện đủ ở phía DX-OS |
|---|---|---|
| 1. Nhận bộ kit và cài DX-OS trên Windows | DXOS-01, DXOS-02, DXOS-03, DXOS-04 | Release có đường tải và quy trình PowerShell chính thức; cài/khởi động được mà không yêu cầu Ubuntu hoặc clone mã nguồn DX-OS. |
| 2. Tạo model YAML | DXOS-04 | Khách hàng nhận được mẫu model và đặc tả đúng phiên bản từ nguồn DX-OS, không giả định ứng dụng đã có file mẫu. |
| 3. Xem trước và áp dụng model | DXOS-02 | Có giao diện lệnh hoặc giao diện quản trị hoạt động trên Windows cho `plan`/`apply`. |
| 4. Đăng ký service client | DXOS-02, DXOS-06 | Tạo client, nhận ID/secret được bằng Windows Terminal, theo hợp đồng/tài liệu chính thức. |
| 5. Cấu hình ứng dụng ngoài | DXOS-03, DXOS-04, DXOS-05 | Có mẫu kết nối REST và hướng dẫn lấy từng giá trị; không nhầm với `.env` nội bộ DX-OS. |
| 6. Gọi REST API theo hợp đồng | DXOS-06, DXOS-07 | Tài liệu hợp đồng và ví dụ đủ để viết HTTP client: token, OpenAPI/metadata, CRUD/action, lỗi và idempotency. |
| 7. Build và chạy ứng dụng khách hàng | Không có vấn đề riêng của DX-OS | Khách hàng dùng quy trình build/deploy của họ sau khi đã viết phần tích hợp nghiệp vụ. |

**Đối chiếu tính nhất quán:** Mỗi khả năng được giả định bổ sung ở quy trình dưới đây đều có vấn đề tương ứng trong mục A. Không còn giả định bắt buộc có SDK hoặc lệnh `lint`.

---

## C. Quy trình **KỲ VỌNG SAU KHI KHẮC PHỤC**

> **QUY TRÌNH NÀY CHỈ LÀ KẾT QUẢ KỲ VỌNG SAU KHI SỬA CÁC VẤN ĐỀ Ở MỤC A. KHÔNG PHẢI KHẢ NĂNG ĐÃ ĐƯỢC XÁC NHẬN TRONG DX-OS v2.0.0.** Tên file và lệnh minh họa sau đây **không được coi là tên artifact đã tồn tại**. Không bao gồm bước kiểm thử.

**Đầu vào:** Máy Windows 10/11 có Docker Desktop; khách hàng có mã nguồn ứng dụng riêng. Mọi thao tác cài đặt/quản trị dùng PowerShell hoặc giao diện Windows; không yêu cầu Ubuntu, WSL terminal hay clone repository DX-OS.

### Bước 1 — Lấy bộ công cụ và cài DX-OS

- **Nguồn kỳ vọng:** Trang [DX-OS Releases](https://github.com/HaUI-ImplSolution/DX-OS/releases) công bố gói Windows, link tải, phiên bản và hướng dẫn tương ứng.
- Từ Windows Terminal, tải **một lần** gói tích hợp Windows theo lệnh tải được tài liệu phát hành công bố; giải nén để nhận installer, tài liệu và file mẫu. Không yêu cầu tự đi tìm file ở repo khách hàng.
- Chạy installer PowerShell trong gói (**ví dụ minh họa**: `./install-dxos.ps1`). Bộ cài cấu hình hạ tầng, tự sinh bí mật nền tảng, dùng image phát hành và cung cấp URL gateway cùng công cụ quản trị Windows.
- **Đầu ra:** DX-OS đang hoạt động và khách hàng nhận được địa chỉ gateway, vị trí bộ kit và CLI/giao diện quản trị.

### Bước 2 — Khai báo model theo nghiệp vụ

- **Nguồn kỳ vọng:** Mẫu YAML, schema và tài liệu model **do DX-OS phân phối cùng bộ kit hoặc liên kết phiên bản rõ ràng tới tài liệu phát hành**.
- Khách hàng sao chép mẫu thành `dxos.yaml`; tự điền `name`, `roles`, `entities`, `fields`, `access`, `process`, `states`, `actions` theo nghiệp vụ của mình.
- **Đầu ra:** Tệp model do khách hàng tạo, tuân theo hợp đồng cấu hình DX-OS.

### Bước 3 — Xem trước và áp dụng model

- **Nguồn kỳ vọng:** CLI Windows hoặc giao diện Console chính thức của DX-OS.
- Tại Windows Terminal, dùng CLI được cài ở bước 1 để chạy **cú pháp minh họa**:

  ```powershell
  dxos plan .\dxos.yaml
  dxos apply .\dxos.yaml --accept SAFE
  ```

- Chỉ dùng `--accept` với mức rủi ro phù hợp kết quả `plan`.
- **Đầu ra:** Model, thực thể, vai và workflow được đưa vào DX-OS, không sửa Core.

### Bước 4 — Đăng ký service client

- **Nguồn kỳ vọng:** CLI Windows và tài liệu ứng dụng ngoài của DX-OS.
- Tại Windows Terminal, đăng ký từng danh tính dịch vụ theo vai được khai trong model; **cú pháp minh họa**:

  ```powershell
  dxos client add customer-requester --model customer_model --role REQUESTER
  dxos client add customer-approver --model customer_model --role APPROVER
  ```

- Lưu `clientId` và `secret` của từng client vào nơi quản lý bí mật phù hợp, không lưu trong mã nguồn.
- **Đầu ra:** Có danh tính máy được cấp quyền gọi API theo model.

### Bước 5 — Điền cấu hình kết nối REST cho ứng dụng

- **Nguồn kỳ vọng:** Template kết nối REST API dành cho ứng dụng ngoài và hướng dẫn biến/tham số, **do DX-OS công bố trong bộ kit** (ví dụ tên minh họa `external-rest.env.example`).
- Đọc mẫu và bổ sung vào cấu hình runtime của ứng dụng khách hàng; không thay thế cấu hình database/JWT nội bộ của ứng dụng.
- Giá trị lấy từ nguồn xác định: **gateway URL** ở bước 1; **model/entity** ở bước 2; **client ID/secret** ở bước 4; **token endpoint, yêu cầu header và timeout gợi ý** từ tài liệu DX-OS. Tên biến `.env` trong ứng dụng là quy ước của chính ứng dụng, trừ trường hợp mẫu DX-OS định nghĩa một quy ước cụ thể.
- **Đầu ra:** Ứng dụng có đủ thông tin kết nối, không phải suy đoán nguồn secret hoặc tự tạo thông tin định danh.

### Bước 6 — Tích hợp trực tiếp qua REST API theo hợp đồng

- **Nguồn kỳ vọng:** Tài liệu REST, hướng dẫn `client_credentials`, OpenAPI được DX-OS sinh từ model đang chạy và ví dụ HTTP client trong bộ công cụ tích hợp. **Không cần SDK chuyên biệt.**
- Lập trình viên sử dụng HTTP client của ngôn ngữ đang dùng và thực hiện các lời gọi theo hợp đồng:
  1. `POST /realms/dxos/protocol/openid-connect/token` — lấy access token bằng `grant_type=client_credentials`.
  2. `GET /api/v1/meta/models/{model}/openapi` — đọc schema API đúng phiên bản model và metadata cần thiết.
  3. `POST /api/v1/data/{model}/{entity}` — tạo hồ sơ theo schema; lưu ID do DX-OS trả về để liên kết với dữ liệu ứng dụng.
  4. `GET /api/v1/data/{model}/{entity}/{id}` và `GET .../{id}/events` — đọc hồ sơ, trạng thái và lịch sử.
  5. `POST /api/v1/data/{model}/{entity}/{id}/actions/{action}` — thực hiện thao tác workflow theo action và payload được mô tả trong OpenAPI.
  6. Gửi Bearer token; tuân thủ `Idempotency-Key` khi tạo hồ sơ hoặc thực hiện action; xử lý token hết hạn, timeout và phản hồi `401`, `403`, `409`, `429`, `5xx`.
- **Ranh giới:** DX-OS cung cấp API và hợp đồng; khách hàng tự quyết định lúc nào gọi API, dữ liệu nào cần gửi và kết quả ảnh hưởng gì đến nghiệp vụ ứng dụng.
- **Đầu ra:** Ứng dụng có thể gọi chức năng DX-OS trực tiếp bằng HTTP, không phụ thuộc mã nguồn Core hoặc SDK riêng.

### Bước 7 — Build và chạy ứng dụng khách hàng

- **Nguồn:** Công cụ và hướng dẫn triển khai **của ứng dụng khách hàng**, không thuộc bộ cài DX-OS.
- Khách hàng build và chạy ứng dụng với cấu hình kết nối đã điền, bằng cơ chế triển khai của họ.
- **Đầu ra:** Ứng dụng chạy độc lập và dùng REST API DX-OS cho các capability đã tích hợp.

---

## D. Căn cứ đánh giá — đúng bản phát hành v2.0.0

1. [DX-OS v2.0.0 Release: ghi chú, bộ cài Bash, gói runtime, checksum và các SBOM](https://github.com/HaUI-ImplSolution/DX-OS/releases/tag/v2.0.0).
2. [README tại tag v2.0.0: phần cài đặt yêu cầu Linux + Docker Compose v2](https://github.com/HaUI-ImplSolution/DX-OS/blob/v2.0.0/README.md).
3. [Bộ cài `deploy/get.sh` tại tag v2.0.0](https://github.com/HaUI-ImplSolution/DX-OS/blob/v2.0.0/deploy/get.sh).
4. [CLI `deploy/dxos` và `deploy/doctor.sh` tại tag v2.0.0](https://github.com/HaUI-ImplSolution/DX-OS/tree/v2.0.0/deploy).
5. [Hướng dẫn `deploy/OPERATIONS.md` tại tag v2.0.0: model và ứng dụng ngoài](https://github.com/HaUI-ImplSolution/DX-OS/blob/v2.0.0/deploy/OPERATIONS.md).
6. [Tài liệu `docs/MODEL.md` tại tag v2.0.0](https://github.com/HaUI-ImplSolution/DX-OS/blob/v2.0.0/docs/MODEL.md).
7. [Tài liệu `docs/API.md` tại tag v2.0.0](https://github.com/HaUI-ImplSolution/DX-OS/blob/v2.0.0/docs/API.md).
8. [Cấu hình nền tảng `deploy/.env.example` tại tag v2.0.0](https://github.com/HaUI-ImplSolution/DX-OS/blob/v2.0.0/deploy/.env.example).

**Ngoài phạm vi:** Không đánh giá hoặc liệt kê lỗi từ bất kỳ repository ứng dụng khách hàng nào. Không sử dụng `main` để xác nhận thành phần đã được phát hành.
