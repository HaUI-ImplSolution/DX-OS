# Chính sách bảo mật

## Phiên bản được hỗ trợ

Bản vá bảo mật được phát hành cho phiên bản mới nhất ở trang [Releases](https://github.com/HaUI-ImplSolution/DX-OS/releases) và cho nhánh `main`.

## Báo lỗ hổng bảo mật

Không mở issue công khai cho lỗ hổng bảo mật.
Báo qua tính năng báo cáo riêng của GitHub: tab **Security**, rồi **Report a vulnerability** ở [trang bảo mật của repository](https://github.com/HaUI-ImplSolution/DX-OS/security/advisories/new).

Nội dung nên có:

- Thành phần bị ảnh hưởng (web, mobile, backend, AI, gateway, SSO, BI, chat) và phiên bản hoặc commit.
- Các bước tái hiện hoặc đoạn mã chứng minh.
- Ảnh hưởng theo đánh giá của người báo.

Không đưa dữ liệu thật của người dùng vào báo cáo.

## Quy trình xử lý

- Nhóm phát triển xác nhận đã nhận báo cáo trong vòng 7 ngày.
- Lỗ hổng được xác minh, sửa ở nhánh riêng và công bố cùng bản phát hành chứa bản vá, ghi ở [`CHANGELOG.md`](./CHANGELOG.md).
- Người báo được ghi công trong ghi chú phát hành nếu đồng ý.

## Phạm vi

Trong phạm vi: mã nguồn của repository này và cấu hình triển khai ở `deploy/`.
Ngoài phạm vi: lỗ hổng của phần mềm thượng nguồn (Keycloak, Apache APISIX, Apache Superset, Mattermost, PostgreSQL, SeaweedFS, Ollama) khi không do cấu hình của repository này gây ra; hãy báo cho dự án thượng nguồn.

## Cơ chế đã có trong repository

- Quét secret trong mã và lịch sử bằng gitleaks ở mỗi pull request.
- Quét lỗ hổng của dependency và image bên thứ ba bằng Trivy khi file khai báo đổi và mỗi tuần.
- Dependabot đề xuất cập nhật dependency.
- Chi tiết mô hình phân quyền, giới hạn tốc độ, ghi vết và sao lưu ở [`ARCHITECTURE.md`](./ARCHITECTURE.md) và [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md).
