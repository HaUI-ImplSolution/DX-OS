# Chính sách bảo mật

## Phiên bản được hỗ trợ

| Phiên bản | Nhận bản vá bảo mật |
|---|---|
| Bản phát hành mới nhất ở trang [Releases](https://github.com/HaUI-ImplSolution/DX-OS/releases) | Có |
| Nhánh `main` | Có |
| Các bản cũ hơn | Không; hãy nâng lên bản mới nhất |

Thay đổi của từng bản ở [`CHANGELOG.md`](./CHANGELOG.md).

## Báo lỗ hổng bảo mật

Không mở issue, pull request hay thảo luận công khai về lỗ hổng.
Báo qua tính năng báo cáo riêng của GitHub: tab **Security**, rồi **Report a vulnerability** ở [trang bảo mật của repository](https://github.com/HaUI-ImplSolution/DX-OS/security/advisories/new).
Chỉ người báo và nhóm phát triển đọc được báo cáo, và mọi trao đổi sau đó diễn ra ngay trong báo cáo.

Không thấy nút **Report a vulnerability** nghĩa là tính năng chưa được bật.
Khi đó mở một issue chỉ ghi "Cần kênh riêng để báo lỗ hổng bảo mật", không mô tả lỗ hổng và không đính kèm gì; nhóm sẽ bật tính năng hoặc mời bạn vào một bản nháp security advisory riêng.
Dự án không nhận báo lỗ hổng qua email cá nhân hay mạng xã hội của thành viên.

Nội dung nên có:

- Thành phần bị ảnh hưởng (web, backend, tệp mô hình, gateway, SSO, AI, deploy, trang docs) và phiên bản hoặc commit.
- Các bước tái hiện ngắn nhất, hoặc đoạn mã chứng minh, kèm cấu hình khác mặc định nếu có.
- Ảnh hưởng theo đánh giá của người báo: ai làm được gì mà lẽ ra không được.
- Bản vá hoặc cách giảm thiểu đề xuất, nếu có.
- Người báo có muốn được ghi tên khi công bố hay không.

Không đưa dữ liệu thật của người dùng, tệp `.env`, mật khẩu hay khóa thật vào báo cáo.

## Thời gian phản hồi

DX-OS do một nhóm sinh viên duy trì; các mốc dưới đây là mục tiêu của nhóm, không phải cam kết hợp đồng.

| Bước | Mục tiêu |
|---|---|
| Xác nhận đã nhận báo cáo | Trong 7 ngày |
| Đánh giá ban đầu: có phải lỗ hổng không, mức độ, kế hoạch sửa | Trong 14 ngày |
| Phát hành bản vá | Mức nghiêm trọng và cao: trong 30 ngày; mức thấp hơn: bản phát hành kế tiếp |

Quá các mốc trên mà chưa có phản hồi, hãy nhắc lại ngay trong báo cáo.

## Phạm vi

Trong phạm vi:

- Mã nguồn của repository này và cấu hình triển khai ở `deploy/`.
- Gói phát hành, bộ cài và các workflow trong `.github/workflows/`.
- Bản chạy thử trực tuyến do nhóm vận hành, theo các giới hạn dưới đây.

Khi thử trên bản chạy thử trực tuyến:

- Chỉ dùng tài khoản thử hoặc tài khoản của chính bạn, và dừng ngay khi đã chứng minh được lỗ hổng.
- Không quét tự động cường độ cao, không tấn công từ chối dịch vụ.
- Không đọc, sửa, xóa dữ liệu của người khác và không giữ quyền truy cập lâu hơn mức cần để chứng minh.
- Dữ liệu trên bản chạy thử là dữ liệu mẫu giả lập.

Ngoài phạm vi:

- Lỗ hổng của phần mềm thượng nguồn (Keycloak, Apache APISIX, PostgreSQL, SeaweedFS, Flowable, ClamAV) khi không do cấu hình của repository này gây ra; hãy báo cho dự án thượng nguồn.
  Phiên bản đang ghim có lỗ hổng đã công bố thì vẫn báo ở đây để nhóm nâng bản.
- Tài khoản thử và mật khẩu của bản chạy thử được công bố trong `README.md`; đây là thiết kế, không phải lỗ hổng.
  Tài khoản này vào được bảng điều khiển hay màn hình giám sát thì vẫn trong phạm vi.
- Bản cài do đơn vị khác vận hành: báo cho đơn vị đó; lỗ hổng nằm trong mã thì báo ở đây.
- Lừa đảo, tấn công vật lý vào máy của thành viên, và kết quả quét tự động chưa chỉ ra được tác động.

## Công bố

- Đừng công bố (issue, bài viết, mạng xã hội, hội thảo) trước khi bản vá được phát hành, hoặc trước ngày hai bên đã thống nhất.
- Lỗ hổng được xác minh, sửa ở nhánh riêng và công bố cùng bản phát hành chứa bản vá, ghi ở [`CHANGELOG.md`](./CHANGELOG.md).
- Người báo được ghi công trong ghi chú phát hành nếu đồng ý.
- Dự án chưa có chương trình thưởng tiền cho báo cáo lỗ hổng.

## Cơ chế đã có trong repository

- Quét secret trong mã và lịch sử bằng gitleaks ở mỗi pull request.
- Quét lỗ hổng của dependency và image bên thứ ba bằng Trivy khi file khai báo đổi và mỗi tuần.
- Dependabot đề xuất cập nhật dependency.
- Mỗi bản phát hành kèm gói `.tar.gz`, `.sha256` và SBOM; bộ cài kiểm SHA-256 trước khi cài.
- Giấy phép của mọi gói, kể cả gói gián tiếp, được kiểm ở mỗi pull request ([`CONTRIBUTING.md`](./CONTRIBUTING.md)).
- Chi tiết mô hình phân quyền, giới hạn tốc độ, ghi vết và sao lưu ở [`ARCHITECTURE.md`](./ARCHITECTURE.md), [`docs/MONITORING.md`](./docs/MONITORING.md) (giới hạn tốc độ và ghi vết) và [`docs/OPERATIONS.md`](./docs/OPERATIONS.md) (sao lưu và ứng phó sự cố).
