## Thay đổi

<!-- Làm gì và vì sao cần. -->

## Issue liên quan

Closes #

## Cách kiểm

<!-- Các bước để người review chạy và thấy thay đổi hoạt động. -->

## Đã kiểm tra

- [ ] `./mvnw spotless:check` và `./mvnw verify` (backend)
- [ ] `npx tsc --noEmit` (web)
- [ ] `npm run build` trong `website/` (trang docs)
- [ ] `python3 .github/scripts/check_licenses.py`

## Danh sách kiểm

- [ ] Đã cập nhật `@Operation` khi đổi API
- [ ] Tài liệu liên quan (`README.md`, `docs/`, `website/docs/`) cập nhật trong cùng pull request
- [ ] `CHANGELOG.md` mục `[Unreleased]` có một dòng cho mỗi thay đổi người dùng thấy
- [ ] Cấu hình mới có trong `deploy/.env.example` kèm chú thích
- [ ] Dependency mới có giấy phép OSI, đã cập nhật `DEPENDENCIES.txt` và `sbom/`
- [ ] Không fork, vá hay đóng gói mã nguồn của dự án khác
- [ ] Mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực
- [ ] Không có secret, `.env`, dữ liệu thật hay trọng số mô hình trong diff
