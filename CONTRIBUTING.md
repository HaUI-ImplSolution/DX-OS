# Đóng góp cho DX-OS

Cảm ơn bạn đã quan tâm.
Tài liệu này nói cách báo lỗi, dựng môi trường và gửi thay đổi.
Hướng thiết kế ở [`docs/dynamic-core/README.md`](./docs/dynamic-core/README.md), kiến trúc ở [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Báo lỗi và đề xuất

- **Lỗi:** [tạo bug report](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=bug_report.yml), ghi các bước tái hiện và phiên bản.
- **Tính năng:** [tạo feature request](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=feature_request.yml).
- **Lỗ hổng bảo mật:** không đăng công khai, làm theo [`SECURITY.md`](./SECURITY.md).

## Dựng môi trường

Cần Docker với Compose v2, JDK 21, Node 20 và Python 3.

```bash
deploy/make_env.sh                          # sinh deploy/.env
cd deploy && docker compose up -d --build   # dựng cả stack
cd ../backend && ./mvnw verify              # build và test backend
cd ../frontend && npm install && npm run dev
```

## Quy trình

1. Tạo nhánh từ `Khuong/main`.
2. Sửa nhỏ, mỗi thay đổi một mục đích.
3. Chạy các lệnh kiểm ở bảng dưới trước khi gửi.
4. Gửi pull request vào `Khuong/main` và điền danh sách kiểm trong mẫu.

| Phần đổi | Lệnh kiểm |
|---|---|
| Backend | `./mvnw formatter:validate` rồi `./mvnw verify` trong `backend/` |
| Web | `npx tsc --noEmit` trong `frontend/` |
| AI | `pytest` trong `ai/` |
| Toàn stack | `python3 deploy/dev/check_api.py`, `check_recovery.py`, `check_case.py`, `check_change.py`, `check_process.py`, `check_booking.py`, `check_demo.py`, `check_guards.py` và `check_users.py`, theo thứ tự, trên stack mới dựng |
| Giấy phép | `python3 .github/scripts/check_licenses.py` |

## Quy ước

- **Commit:** tiếng Anh, thể mệnh lệnh, ví dụ `feat: add the approval template`.
- **Mã:** Java 21, định dạng theo `backend/eclipse-java-google-style.xml` (chạy `./mvnw formatter:format`); comment ngắn, nêu hành vi hiện tại, tiếng Anh.
- **API:** mô tả `@Operation` bằng tiếng Anh; mọi response bọc trong `ApiResponse`.
- **Tài liệu:** tiếng Việt, mỗi câu một dòng, chỉ ghi trạng thái hiện tại.
- **Mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực**; nghiệp vụ nằm trong tệp mô hình.
- **Header giấy phép:** `SPDX-License-Identifier: Apache-2.0` ở đầu mọi file mã nguồn, riêng trong `ai/` là `AGPL-3.0-only`.

## Dependency và dữ liệu

- Chỉ dùng thành phần theo giấy phép được OSI công nhận; `check_licenses.py` chặn giấy phép ngoài danh sách.
- Đổi dependency thì sinh lại SBOM theo [`sbom/README.md`](./sbom/README.md) và cập nhật [`COPYRIGHT.txt`](./COPYRIGHT.txt).
- Image, mô hình và tệp trọng số, tài liệu, nén phải khai báo ở [`.github/license-catalog.json`](./.github/license-catalog.json) với giấy phép được phép; `check_licenses.py` kiểm danh mục này.
- Không đưa mật khẩu, khóa hay dữ liệu thật vào kho mã; `deploy/.env` nằm ngoài Git.

## Phát hành

Bản phát hành là tag `vX.Y.Z` nhẹ trên `main`, kèm mục tương ứng trong [`CHANGELOG.md`](./CHANGELOG.md).
Workflow `Release` build và đẩy image lên GHCR; workflow `Release Package` đính kèm SBOM.

## Thêm một nghiệp vụ mới

Viết một tệp mô hình theo mẫu ở [`models/`](./models) và áp dụng bằng `POST /api/v1/models/apply`; không cần sửa mã lõi.
Nếu nghiệp vụ cần một năng lực mà tệp mô hình chưa khai báo được, mở một issue mô tả: một năng lực mới chỉ được thêm khi cần ở ít nhất ba lĩnh vực và khai báo được mà không cần cú pháp mới.
