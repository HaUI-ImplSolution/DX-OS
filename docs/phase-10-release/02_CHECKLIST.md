# Checklist: Phase 10

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP10-01: Gỡ phần chưa mở và của bản cũ

- [x] WP10-01-DATA-01 Gỡ `data/`.
- [x] WP10-01-AI-01 Gỡ `ai/weights`, `ai/evaluation`, `ai/evaluate.sh`, `ai/requirements-eval.txt`.
- [x] WP10-01-DOC-01 Gỡ ảnh minh họa của Farm và của đánh giá RAG.
- [x] WP10-01-INFRA-01 Sửa `.gitattributes`, `.gitignore`, `dependabot.yml`, workflow và tệp mẫu `.env`.

## WP10-02: Cắt `ai/` còn RAG chung

- [x] WP10-02-AI-01 Gỡ mã CV, luồng chẩn đoán, dịch vụ bệnh và triệu chứng, router bệnh, client gọi backend cũ.
- [x] WP10-02-AI-02 Nạp tài liệu từ tệp JSON và registry nguồn kèm giấy phép.
- [x] WP10-02-AI-03 Chỉ còn mô hình embedding đạt giấy phép mở; bỏ reranker và mô hình ngôn ngữ.
- [x] WP10-02-AI-04 Gỡ và viết lại test, script, `Dockerfile` và README của `ai/`.
- [x] WP10-02-TEST-01 `pytest` của `ai/` đạt.

## WP10-03: Phiên bản 2.0.0

- [x] WP10-03-BE-01 `pom.xml` của backend và các module.
- [x] WP10-03-FE-01 `package.json` và lock của web.
- [x] WP10-03-DOC-01 Mục `[2.0.0]` của `CHANGELOG.md` kèm link so sánh.

## WP10-04: Giấy phép và SBOM

- [x] WP10-04-INFRA-01 Sinh lại ba SBOM.
- [x] WP10-04-DOC-01 Cập nhật `COPYRIGHT.txt` và `NOTICE`.
- [x] WP10-04-INFRA-02 Danh mục `.github/license-catalog.json` cho image, mô hình, tệp trọng số, tài liệu, nén; mở rộng `check_licenses.py`.

## WP10-05: Tài liệu

- [x] WP10-05-DOC-01 `README.md` và `README_EN.md`.
- [x] WP10-05-DOC-02 `ARCHITECTURE.md`, `CONTRIBUTING.md`, `deploy/OPERATIONS.md`, `deploy/VPS.md`.
- [x] WP10-05-DOC-03 README của `backend`, `frontend`, `ai`, `bi`, `chat`.
- [x] WP10-05-DOC-04 `docs/TESTING.md`, `BACKLOG.md`, mục "Cam kết nguồn mở" của `docs/dynamic-core/README.md`.

## WP10-06: Đường phát hành và CI

- [x] WP10-06-INFRA-01 `release.yml`, `release-package.yml`, `install.sh`.
- [x] WP10-06-INFRA-02 Các workflow CI khớp cây mã mới.

## WP10-07: Kiểm chứng

- [x] WP10-07-TEST-01 Clone sạch, `clean verify`, `tsc`, `pytest`, hai script kiểm tĩnh.
- [x] WP10-07-TEST-02 Dựng stack và chuỗi `check_*.py`.
- [x] WP10-07-TEST-03 Thử giao diện bằng Chrome.

## Đóng phase

- [ ] CI trên GitHub: chưa chạy vì chưa push.
- [x] Đã cập nhật `ARCHITECTURE.md`, `CHANGELOG.md`, `README.md`, `README_EN.md`, `docs/TESTING.md`, `docs/dynamic-core/README.md`, `docs/dynamic-core/BACKLOG.md`, `COPYRIGHT.txt`, `NOTICE`, `sbom/` và `.github/license-catalog.json`.
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 4 có bằng chứng.
- [ ] Người dùng tạo tag `v2.0.0` sau khi merge: việc của người dùng.
