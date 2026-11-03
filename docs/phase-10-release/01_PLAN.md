# Plan thực hiện: Phase 10

Thứ tự làm: Gỡ → Cắt `ai/` → Phiên bản → Giấy phép → Tài liệu → Phát hành → Kiểm chứng.
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WP10-01 → WP10-02 → WP10-03 → WP10-04 → WP10-05 → WP10-06 → WP10-07.

## WP10-01: Gỡ phần chưa mở và của bản cũ

- Mục tiêu: cây mã chỉ còn những gì thuộc lõi mới.
- Hướng làm:
  - Gỡ `data/`, `ai/weights`, `ai/evaluation`, `ai/evaluate.sh`, `ai/requirements-eval.txt`, các script đánh giá, benchmark và nhận diện, ảnh minh họa của Farm.
  - Sửa mọi tham chiếu: `.gitattributes`, `.gitignore`, `dependabot.yml`, workflow, tệp mẫu `.env`, README.

## WP10-02: Cắt `ai/` còn RAG chung

- Mục tiêu: `ai/` là một dịch vụ RAG chạy được, không biết đến Farm hay Lab.
- Hướng làm:
  - Gỡ `app/cv`, `app/graph`, các dịch vụ chẩn đoán và triệu chứng, router bệnh, client gọi backend cũ, test và script của chúng.
  - Giữ retriever, chunker, embedding, chat search, answer contract, auth giữa dịch vụ, mô hình dữ liệu và API tìm kiếm.
  - Nạp tài liệu từ một tệp JSON và một registry nguồn kèm giấy phép do người dùng cấp, không gắn với tên miền nào; `scripts/init_db.py` tạo bảng và tiện ích pgvector.
  - Mô hình duy nhất là embedding MiniLM (Apache-2.0); không còn reranker và không dùng mô hình ngôn ngữ.
  - Test còn lại chạy bằng `pytest`.

## WP10-03: Phiên bản 2.0.0

- Mục tiêu: một số phiên bản duy nhất.
- Hướng làm: `backend/pom.xml` và các module, `frontend/package.json` và lock, tiêu đề OpenAPI nếu có, mục `[2.0.0]` của `CHANGELOG.md` kèm link so sánh, README.

## WP10-04: Giấy phép và SBOM

- Mục tiêu: kho mã chứng minh được giấy phép của từng thành phần.
- Hướng làm:
  - Sinh lại `sbom/backend.cdx.json`, `frontend.cdx.json`, `ai.cdx.json` theo `sbom/README.md`.
  - `COPYRIGHT.txt` và `NOTICE` chỉ nêu thành phần của bản phát hành; thành phần tùy chọn (Superset, Mattermost, Ollama, cloudflared) ghi rõ là tùy chọn.
  - `check_licenses.py` đọc `.github/license-catalog.json` gồm image, mô hình và tệp trọng số, tài liệu, nén, và chặn mục thiếu hoặc có giấy phép ngoài danh sách; `COPYRIGHT.txt` phải nhắc từng mục.

## WP10-05: Tài liệu

- Mục tiêu: người chấm đọc kho mã thấy đúng bản `2.0.0`.
- Hướng làm: `README.md` và `README_EN.md` cùng lượt, `ARCHITECTURE.md`, `CONTRIBUTING.md`, `deploy/OPERATIONS.md`, `deploy/VPS.md`, README của `backend`, `frontend`, `ai`, `docs/TESTING.md`, `docs/dynamic-core/BACKLOG.md` và mục "Cam kết nguồn mở" của `docs/dynamic-core/README.md`.

## WP10-06: Đường phát hành và CI

- Mục tiêu: gắn tag `v2.0.0` là ra bản phát hành đầy đủ.
- Hướng làm: đối chiếu `release.yml`, `release-package.yml`, `install.sh` và các workflow CI với các module và image hiện có; sửa chỗ còn nhắc thành phần đã gỡ.

## WP10-07: Kiểm chứng

- Mục tiêu: chứng minh bản phát hành chạy từ kho sạch.
- Hướng làm: clone sạch, `clean verify`, `tsc`, `pytest` của `ai/`, hai script kiểm tĩnh, dựng stack, chuỗi `check_*.py`, thử giao diện bằng Chrome.
