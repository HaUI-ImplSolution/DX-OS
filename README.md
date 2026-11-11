# DX-OS

**Tiếng Việt** · [English](./README_EN.md)

> **Lõi dynamic nguồn mở cho hệ thống hồ sơ có quy trình.**
> Khai báo nghiệp vụ trong một tệp mô hình; DX-OS dựng cơ sở dữ liệu, tài khoản và vai, API có phân quyền, quy trình duyệt và giao diện.
> Đổi nghiệp vụ là đổi tệp, không đổi mã.

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![Platform](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/ci-platform.yml/badge.svg)](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/ci-platform.yml)
[![Compliance](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/ci-compliance.yml/badge.svg)](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/ci-compliance.yml)
[![Docs](https://github.com/HaUI-ImplSolution/DX-OS/actions/workflows/docs.yml/badge.svg)](https://haui-implsolution.github.io/DX-OS/)

Bài dự thi **OLP Phần mềm nguồn mở 2026**, khối thi DX-OS (VFOSSA), của đội **HaUI-ImplSolution**, Trường Công nghệ Thông tin và Truyền thông, Đại học Công nghiệp Hà Nội.

**Đọc nhanh:** [Giới thiệu](#giới-thiệu) · [Tính năng](#tính-năng) · [Cách hoạt động](#cách-hoạt-động) · [Chạy thử](#chạy-thử) · [Tài liệu](#tài-liệu) · [Cấu trúc](#cấu-trúc-repository) · [Đóng góp](#đóng-góp) · [Giấy phép](#giấy-phép)

## Giới thiệu

Trường học, khoa, doanh nghiệp nhỏ nào cũng có những quy trình giống nhau: một người gửi yêu cầu, một người khác xem xét, duyệt hoặc trả lại, và mọi bước đều phải để lại dấu vết.
Mỗi lần có nghiệp vụ mới, người ta lại dựng từ đầu phần đăng nhập, phân quyền, lưu tệp và luồng duyệt.

DX-OS làm phần dùng chung đó một lần, thành một lõi chạy ngầm không gắn với nghiệp vụ nào.
Người triển khai chỉ mô tả nghiệp vụ của mình trong một tệp mô hình, như hồ sơ cần những thông tin gì, ai được xem và sửa, ai duyệt, hạn xử lý là bao lâu.
Lõi đọc tệp đó rồi dựng hệ thống chạy được.

Toàn bộ hệ thống lắp từ phần mềm nguồn mở có giấy phép được OSI công nhận, tự cài trên máy của mình và dữ liệu nằm hoàn toàn ở đó.
Phiên bản hiện tại là `2.0.0`, thay đổi theo từng phiên bản ở [`CHANGELOG.md`](./CHANGELOG.md).

## Tính năng

- **Từ một tệp đến hệ thống chạy được.** Hồ sơ, bảng dữ liệu, tài khoản, vai, màn hình danh sách và biểu mẫu đều được dựng từ tệp mô hình.
- **Phân quyền đến từng dòng.** Mỗi vai chỉ thấy và sửa phần việc của mình, của đơn vị mình hoặc được giao cho mình.
- **Quy tắc dữ liệu ngay trong tệp.** Trường đúng hoặc sai, ngày, chọn nhiều giá trị, giới hạn giá trị, mẫu chuỗi và điều kiện bắt buộc đều khai báo trong tệp; cơ sở dữ liệu cũng từ chối giá trị sai.
- **Quy trình tùy biến.** Chọn một trong ba mẫu có sẵn (phê duyệt, công việc cần xử lý, đặt chỗ theo khung giờ có kiểm sức chứa) rồi thêm bớt bước, giao từng bước cho vai riêng, hoặc tự khai báo quy trình từ đầu; một bước có thể chỉ mở khi hồ sơ đạt điều kiện, ví dụ số tiền vượt mức thì lên cấp duyệt cao hơn; từ chối và trả lại đều cần lý do, và không ai tự duyệt hồ sơ của mình.
- **Đổi nghiệp vụ khi đã có dữ liệu.** Hệ thống cho xem trước tác động, cảnh báo rủi ro, giữ nguyên dữ liệu khi đổi tên và cho quay về phiên bản trước.
- **Dấu vết không sửa được.** Lịch sử hồ sơ nối thành một chuỗi có dấu kiểm; nếu ai sửa, xóa hoặc chèn một dòng thì một lệnh chỉ ra đúng dòng đó.
- **Kết nối ra ngoài.** Mọi thay đổi của hồ sơ được báo tới địa chỉ web hoặc kênh chat (Mattermost, Slack) với chữ ký để bên nhận kiểm tra, và được gửi lại khi bên nhận lỗi.
- **Mở cho ứng dụng bên ngoài.** Mỗi mô hình có tài liệu API riêng, tự đổi theo mô hình; một ứng dụng ngoài được đăng ký với đúng một vai của một mô hình và gọi bằng mã bí mật của nó.
- **Bảng điều khiển cho người triển khai.** Thư viện mẫu, trình soạn mô hình (viết tệp hoặc điền form, có hướng dẫn nói rõ viết được và mở rộng được gì) kèm sơ đồ quy trình vẽ theo tệp đang soạn, xem kế hoạch, áp dụng, lịch sử, quản lý người dùng và thống kê hồ sơ.
- **Giao diện theo khai báo.** Tệp mô hình chọn được biểu tượng, nhóm menu, cột và sắp xếp mặc định, cách chia nhóm biểu mẫu và lời gợi ý; cán bộ chọn nhiều hồ sơ rồi xử lý cùng lúc, mỗi hồ sơ vẫn qua đủ luật.
- **Xem theo trạng thái và xuất danh sách.** Danh sách của hồ sơ có quy trình xem được như một bảng theo từng trạng thái, và xuất ra tệp CSV mà bảng tính đọc được, đúng phần người xem được quyền thấy.
- **Danh mục dữ liệu.** Một trang cho biết dữ liệu của mỗi mô hình nằm ở đâu, ai được xem, các loại hồ sơ liên quan nhau thế nào, dữ liệu đi vào từ đâu và đi ra đâu.
- **Lưu trữ hồ sơ cũ.** Hồ sơ đã đóng lâu được ghi sang kho lưu trữ theo lịch mỗi ngày, dữ liệu đang dùng vẫn giữ nguyên và bản lưu kiểm lại được.
- **Hộp việc tự cập nhật.** Khi hồ sơ đổi, hộp việc, danh sách và trang chi tiết đang mở tự làm mới, không cần tải lại trang.
- **Dùng thử ngay.** Một lệnh tạo tài khoản thử cho từng vai cùng hồ sơ mẫu ở nhiều trạng thái, để người xem đăng nhập là thấy hệ thống có việc đang chạy.
- **Cài và vận hành đơn giản.** Một lệnh cài, một lệnh chạy; có sao lưu kiểm được, tự khởi động lại dịch vụ và quét mã độc cho tệp đính kèm khi bật.
- **Đăng nhập một lần.** Qua Keycloak, và có thể bắt quản trị viên nhập thêm mã một lần.

Bảng đối chiếu từng tính năng với bài kiểm của nó ở [ma trận bằng chứng](https://haui-implsolution.github.io/DX-OS/docs/evidence-matrix).

## Cách hoạt động

1. **Khai báo.** Viết hoặc chọn một tệp mô hình: thực thể, trường, vai, quyền và quy trình của nghiệp vụ.
2. **Áp dụng.** Xem kế hoạch rồi áp dụng; lõi dựng cơ sở dữ liệu, vai trong Keycloak, API và quy trình, và ghi lại từng bước.
3. **Dùng.** Người dùng đăng nhập và làm việc trên giao diện web dựng từ chính tệp đó; mọi thay đổi để lại lịch sử và được báo ra ngoài.

Hệ thống có ba tầng:

| Tầng | Vai trò |
|---|---|
| Lõi | Dịch vụ không giao diện: đọc mô hình, áp dụng, API chung, quy trình, lịch sử, sự kiện |
| Ứng dụng | Giao diện web và bảng điều khiển dựng từ dữ liệu của lõi, cùng trang liên kết (portal) và màn hình giám sát |
| Mô hình | Các tệp mô tả nghiệp vụ; thêm nghiệp vụ mới chỉ là thêm một tệp |

Sơ đồ và luồng một yêu cầu đi qua hệ thống ở [`ARCHITECTURE.md`](./ARCHITECTURE.md); cách viết tệp mô hình ở [`docs/MODEL.md`](./docs/MODEL.md).

Tám mẫu có sẵn, chọn được ngay ở bảng điều khiển: một cửa sinh viên, hỗ trợ kỹ thuật, sự cố thiết bị, đặt phòng, cấp quyền dịch vụ CNTT, mượn thiết bị, ghi chú và sổ tài sản.

## Chạy thử

### Cài đặt

Cần máy Linux có Docker với Compose v2; không cần mã nguồn.

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

Bộ cài tải bản phát hành mới nhất, kiểm SHA-256 rồi cài vào `~/.dxos`; lệnh `dxos` nằm ở `~/.local/bin`.
`dxos start` tải image ở lần đầu, chờ mọi dịch vụ sẵn sàng, tạo tài khoản `admin` với mật khẩu tạm in ra màn hình, rồi in các địa chỉ.
Đăng nhập `http://localhost:9080/console` bằng `admin`, đổi mật khẩu, chọn một mẫu ở tab Thư viện mẫu, xem kế hoạch rồi áp dụng.
Đã có tệp mô hình thì `dxos start <tệp>` đưa tệp vào luôn; có nhiều tệp thì liệt kê hết sau `dxos start`.
Các lệnh khác và cách vận hành ở [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md).

### Từ mã nguồn

Cần thêm JDK 21 và Node 22 nếu muốn build và kiểm thử ngoài Docker.

```bash
deploy/make_env.sh                       # sinh deploy/.env với mật khẩu ngẫu nhiên
cd deploy && docker compose up -d --build
./dxos admin                             # tạo tài khoản admin với mật khẩu tạm
```

Người dùng vào qua gateway `http://localhost:9080`:

| Địa chỉ | Nội dung |
|---|---|
| `/` | Giao diện web |
| `/console` | Bảng điều khiển cho người triển khai |
| `/portal/` | Trang liệt kê các địa chỉ và trạng thái dịch vụ |
| `/monitor/` | Giám sát và truy vết (ADMIN) |
| `/swagger-ui.html` | Tài liệu API |
| `/admin` | Quản trị Keycloak (tài khoản trong `deploy/.env`) |

Hướng dẫn kiểm thử đầy đủ ở [`docs/TESTING.md`](./docs/TESTING.md).

## Tài liệu

Trang tài liệu: <https://haui-implsolution.github.io/DX-OS/>.

| Cần gì | Xem |
|---|---|
| Viết tệp mô hình | [`docs/MODEL.md`](./docs/MODEL.md) |
| Gọi API | [`docs/API.md`](./docs/API.md) và Swagger UI của backend đang chạy |
| Kiến trúc | [`ARCHITECTURE.md`](./ARCHITECTURE.md) |
| Cài đặt, lệnh `dxos`, sao lưu | [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md) |
| Giám sát, log, nhật ký truy cập | [`deploy/MONITORING.md`](./deploy/MONITORING.md) |
| Webhook, lịch sử chống sửa, lưu trữ dài hạn | [`deploy/INTEGRATIONS.md`](./deploy/INTEGRATIONS.md) |
| Kiểm thử | [`docs/TESTING.md`](./docs/TESTING.md), [`e2e/README.md`](./e2e/README.md) |
| Kịch bản trình diễn 15 phút | [`docs/DEMO.md`](./docs/DEMO.md) |
| Hướng thiết kế và việc còn lại | [`docs/dynamic-core/README.md`](./docs/dynamic-core/README.md), [`BACKLOG.md`](./docs/dynamic-core/BACKLOG.md) |
| Thay đổi theo phiên bản | [`CHANGELOG.md`](./CHANGELOG.md) |
| Thành phần bên thứ ba và giấy phép | [`DEPENDENCIES.txt`](./DEPENDENCIES.txt), [`NOTICE`](./NOTICE), [`sbom/`](./sbom/README.md) |

## Cấu trúc repository

| Thư mục | Nội dung |
|---|---|
| `backend/dx-model` | Định dạng tệp mô hình: đọc và kiểm tra |
| `backend/dx-core` | Thư viện dùng chung: bảo mật, audit, lưu trữ, trạng thái, client Keycloak |
| `backend/dx-engine` | Dịch vụ lõi: áp dụng mô hình, API chung, Flowable |
| `backend/dx-monitor` | Màn hình giám sát và truy vết |
| `frontend` | Giao diện react-admin dựng từ metadata, có tiếng Việt và tiếng Anh |
| `deploy` | Compose, gateway APISIX, bộ cài và lệnh `dxos`, script vận hành, script kiểm tra ở `deploy/dev` |
| `sso`, `portal`, `seaweedfs` | Keycloak realm, trang portal, lưu trữ tệp |
| `models` | Tệp mô hình sai mẫu cho kiểm tự động; mô hình mẫu dùng được ở `backend/dx-engine/src/main/resources/templates/` |
| `examples` | Chương trình mẫu: bên nhận sự kiện giữ hash lịch sử ([`history-witness`](./examples/history-witness/README.md)), giả lập hệ thống IT/OT ([`itot-simulator`](./examples/itot-simulator/README.md)) |
| `e2e` | Kiểm giao diện web trên trình duyệt (Playwright) |
| `website` | Trang tài liệu (Docusaurus), đăng lên GitHub Pages |
| `docs` | Hướng dẫn kiểm thử, tệp mô hình, API; hướng thiết kế ở `dynamic-core/` |
| `sbom` | Danh mục thành phần CycloneDX của backend, web, và trang tài liệu |
| `.github` | Workflow CI, mẫu issue và pull request, quy tắc nhánh, script kiểm tra |

## Đóng góp

- **Báo lỗi:** [tạo bug report](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=bug_report.yml).
- **Đề xuất tính năng:** [tạo feature request](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=feature_request.yml).
- **Gửi mã:** đọc [`CONTRIBUTING.md`](./CONTRIBUTING.md) và tuân theo [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md).
- **Lỗ hổng bảo mật:** không đăng công khai, làm theo [`SECURITY.md`](./SECURITY.md).

## Nhóm phát triển

| Tên | Liên hệ |
|---|---|
| Phạm Minh Khương | pkhuong535@gmail.com |
| Nguyễn Đình Duy Khánh | dinhkhanh19042006@gmail.com |
| Trần Quang Tùng | tung51652@gmail.com |

## Giấy phép

Apache License 2.0, xem [`LICENSE`](./LICENSE).
Giấy phép của từng thành phần bên thứ ba ở [`DEPENDENCIES.txt`](./DEPENDENCIES.txt).

Tham khảo: [chủ đề cuộc thi](https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html) · [giáo trình DX-OS](https://opendigitransform.gitbook.io/dx-os)
