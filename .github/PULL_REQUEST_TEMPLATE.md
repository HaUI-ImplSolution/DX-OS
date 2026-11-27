## Thay đổi

<!-- Làm gì và vì sao cần. -->

## Issue liên quan

Closes #

## Cách kiểm

<!-- Các bước để người review chạy và thấy thay đổi hoạt động. -->

## Danh sách kiểm

### Backend

- [ ] `./mvnw spotless:check` và `./mvnw verify`
- [ ] `@Operation` cập nhật khi đổi API
- [ ] Mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực

### Frontend

- [ ] `npx tsc --noEmit`, `npm run lint` và `npm test`

### Trợ lý và bộ phân loại

- [ ] `npm test` trong `assistant/`
- [ ] Đổi dữ liệu hoặc trọng số: chạy lại `tools/classifier/evaluate.py`
- [ ] Đổi trọng số: sinh lại bộ đối chiếu bằng `make_parity_fixture.py`
- [ ] `MODEL_CARD.md` và `DATA_CARD.md` khớp số đo và nguồn dữ liệu
- [ ] Dữ liệu không chứa hồ sơ thật

### Giấy phép và dependency

- [ ] `python3 .github/scripts/check_licenses.py`
- [ ] `reuse lint`
- [ ] Dependency mới: giấy phép OSI, có trong `DEPENDENCIES.txt` và `sbom/`
- [ ] Trọng số, dữ liệu hoặc image mới: khai trong `component-catalog.json` và `REUSE.toml`
- [ ] Image mới: có `/licenses` gồm `LICENSE`, `NOTICE`, `SOURCES.txt`
- [ ] Không fork, vá hay đóng gói mã nguồn của dự án khác

### Triển khai

- [ ] Cấu hình mới có trong `deploy/.env.example` kèm chú thích
- [ ] Không có secret hay `.env` trong diff

### Tài liệu

- [ ] `npm run build` trong `website/`
- [ ] `README.md`, `docs/`, `website/docs/` cập nhật trong cùng pull request
- [ ] `CHANGELOG.md` mục `[Unreleased]` có một dòng cho mỗi thay đổi người dùng thấy
