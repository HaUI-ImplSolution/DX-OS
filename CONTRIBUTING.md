# Đóng góp cho DX-OS

Cảm ơn bạn đã quan tâm.
Tài liệu này nói cách báo lỗi, dựng môi trường và gửi thay đổi.
Hướng thiết kế ở [`docs/dynamic-core/README.md`](./docs/dynamic-core/README.md), kiến trúc ở [`ARCHITECTURE.md`](./ARCHITECTURE.md).
Mọi người tham gia theo [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md).

## Báo lỗi và đề xuất

- **Lỗi:** [tạo bug report](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=bug_report.yml), ghi các bước tái hiện và phiên bản.
- **Tính năng:** [tạo feature request](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=feature_request.yml).
- **Lỗ hổng bảo mật:** không đăng công khai, làm theo [`SECURITY.md`](./SECURITY.md).

## Dựng môi trường

Cần Docker với Compose v2, JDK 21, Node 22 và Python 3.

```bash
deploy/make_env.sh                          # sinh deploy/.env
cd deploy && docker compose up -d --build   # dựng cả stack
cd ../backend && ./mvnw verify              # build và test backend
cd ../frontend && npm install && npm run dev
```

## Quy trình

1. Tạo nhánh từ `Khuong/main`, nhánh tích hợp; `main` chỉ nhận bản đã phát hành.
   Đặt tên nhánh `<loại>/<số issue>-<mô tả ngắn>`, ví dụ `feat/272-prometheus-metrics`, với loại là `feat`, `fix`, `docs`, `refactor`, `test` hoặc `chore`.
   Thành viên nhóm tạo nhánh thẳng trên repository; người ngoài nhóm fork repository, tạo nhánh trên bản fork và gửi pull request từ đó.
2. Sửa nhỏ, mỗi thay đổi một mục đích.
   Tính năng mới đi kèm kiểm tự động: một test backend ở `backend/*/src/test`, hoặc một mục kiểm trong script `e2e/api/check_*.py` khi tính năng chỉ chạy trên stack thật.
   Sửa lỗi thì thêm kiểm bắt đúng lỗi đó khi làm được.
3. Chạy các lệnh kiểm ở bảng dưới trước khi gửi.
4. Gửi pull request vào `Khuong/main` và điền danh sách kiểm trong mẫu, kèm `Closes #<số issue>` khi pull request đủ đóng issue.
   Pull request vào `main` phải có các check xanh theo ruleset ở [`.github/rulesets/README.md`](.github/rulesets/README.md).

| Phần đổi | Lệnh kiểm |
|---|---|
| Backend | `./mvnw spotless:check` rồi `./mvnw verify` trong `backend/` |
| Frontend | `npx tsc --noEmit` trong `frontend/` |
| Toàn stack | Các script `e2e/api/check_*.py` theo thứ tự ở [`docs/TESTING.md`](./docs/TESTING.md), mục 4, trên stack mới dựng |
| Giấy phép | `python3 .github/scripts/check_licenses.py` |
| Header giấy phép | `pipx run --spec reuse==6.3.0 reuse lint` |
| Giấy phép của phiên bản đang dùng | `GITHUB_TOKEN=... python3 .github/scripts/check_upstream_licenses.py` và `python3 -m unittest discover -s .github/scripts` |
| Trích dẫn giữa các tài liệu | `python3 .github/scripts/check_doc_refs.py` |
| Trang docs | `npm run build` trong `website/` |
| Giao diện trên trình duyệt | Xem [`e2e/README.md`](./e2e/README.md) |
| Workflow | `docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.12` |

## Quy ước

- **Commit:** tiếng Anh, thể mệnh lệnh, kèm số issue ở cuối tiêu đề, ví dụ `feat: add the approval template (#123)`.
- **Mã:** Java 21, định dạng theo google-java-format (chạy `./mvnw spotless:apply`); comment ngắn, nêu hành vi hiện tại, tiếng Anh.
- **API:** mô tả `@Operation` bằng tiếng Anh; mọi response bọc trong `ApiResponse`.
- **Tài liệu:** tiếng Việt, mỗi câu một dòng, chỉ ghi trạng thái hiện tại.
- **Issue và pull request:** viết bằng tiếng Việt hoặc tiếng Anh; nhóm đọc và trả lời cả hai.
- **Thông báo của backend:** viết bằng tiếng Anh kèm `errorCode`; mỗi thông báo mới cần thêm một mẫu dịch ở `frontend/src/shell/backendMessages.ts`, nếu không giao diện hiện nguyên văn tiếng Anh.
- **Mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực**; nghiệp vụ nằm trong tệp mô hình.
- **Header giấy phép:** hai dòng `SPDX-FileCopyrightText: 2026 HaUI-ImplSolution` và `SPDX-License-Identifier: Apache-2.0` ở đầu mọi file mã nguồn; file không chèn được chú thích (hoặc đã phát hành, như migration SQL) khai trong `REUSE.toml`; `reuse lint` kiểm cả hai.

## Tình huống thường gặp

| Tình huống | Cách xử lý |
|---|---|
| Nhánh bị tụt so với `Khuong/main` | `git fetch origin`, `git rebase origin/Khuong/main`, sửa xung đột, rồi `git push --force-with-lease` |
| Lỡ mở pull request vào `main` | Bấm **Edit** cạnh tiêu đề và đổi nhánh đích sang `Khuong/main`; `main` chỉ nhận bản phát hành |
| Xung đột ở tệp khóa (`package-lock.json`...) | Lấy bản của `Khuong/main`, chạy lại trình cài gói để sinh lại tệp khóa, rồi commit |
| Sai commit message, chưa push | `git commit --amend` |
| Sai commit message, đã push nhánh của mình | `git commit --amend` rồi `git push --force-with-lease`; không bao giờ làm trên `Khuong/main` hay `main` |
| Lỡ commit bí mật | Đổi bí mật đó ngay vì coi như đã lộ, báo nhóm, rồi mới gỡ khỏi mã; gitleaks sẽ báo ở pull request |
| Pull request đã gộp gây lỗi | Mở pull request `git revert <sha>` vào `Khuong/main`; không sửa lịch sử của `Khuong/main` và `main` |

## Dependency và dữ liệu

- Chỉ dùng thành phần theo giấy phép được OSI công nhận; `check_licenses.py` chặn giấy phép ngoài danh sách, trừ vài gói có lý do ở `.github/dependency-exceptions.json`.
- Không fork, vá hay đóng gói mã nguồn của dự án khác vào repository; dùng bản gốc và cấu hình nó.
- Đổi dependency thì sinh lại SBOM theo [`sbom/README.md`](./sbom/README.md) và cập nhật [`DEPENDENCIES.txt`](./DEPENDENCIES.txt).
- Dependabot chỉ mở pull request cho bản vá và bản phụ; bản chính, dòng hỗ trợ dài hạn khác và image gốc khác nâng bằng tay, sau khi kiểm giấy phép OSI, thời hạn hỗ trợ và tương thích (xem [`.github/dependabot.yml`](.github/dependabot.yml)).
- Image (kể cả image chép vào bằng `COPY --from`), bản phát hành tải bằng `ADD`, mô hình và tệp trọng số, tài liệu, nén phải khai báo ở [`.github/component-catalog.json`](.github/component-catalog.json) với giấy phép được phép; `check_licenses.py` kiểm danh mục này.
  `check_upstream_licenses.py` đọc giấy phép của dự án gốc ở đúng phiên bản đang dùng của từng image và bản tải về, ở mỗi pull request và mỗi tuần: giấy phép khác bản đã khai, ngoài danh sách OSI, không xác định được hoặc không đọc được đều làm job đỏ, nên một pull request của Dependabot đổi tag image bị kiểm ở chính phiên bản nó đề xuất.
  Mục nào của danh mục không đọc được theo cách đó thì ghi cách xác minh ở mục ấy (`refs`, `text`) hoặc lý do ở `unverified`.
  `check_licenses.py sbom <tệp>` kiểm giấy phép của SBOM sinh lại từ pull request, nên bản mới của một thư viện Maven hay npm bị kiểm trước khi `sbom/` được sinh lại.
- Không đưa mật khẩu, khóa hay dữ liệu thật vào kho mã; `deploy/.env` nằm ngoài Git.

## Giấy phép của phần đóng góp

Phần đóng góp, gồm mã, tài liệu, ảnh, video và sơ đồ, được cấp phép theo Apache License 2.0 như cả dự án (mục 5 của giấy phép), trừ khi bạn nêu rõ khác ngay trong pull request.
Mã, tài liệu hay ảnh lấy từ nơi khác phải có giấy phép được OSI công nhận và tương thích với Apache-2.0, được khai ở [`DEPENDENCIES.txt`](./DEPENDENCIES.txt) hoặc `REUSE.toml`, và nêu nguồn trong mô tả pull request.

## Phát hành

Bản phát hành là tag `vX.Y.Z` nhẹ trên `main`, kèm mục tương ứng trong [`CHANGELOG.md`](./CHANGELOG.md).
Đẩy tag thì workflow `Release Package` tạo GitHub Release kèm gói `.tar.gz`, `.sha256` và SBOM; workflow chỉ nhận tag nằm trên `main`.
Sau đó chạy tay workflow `Release Images` trên tag để build và đẩy image lên GHCR, vì bộ cài `dxos-install.sh` kéo image theo tag đó.

## Thêm một nghiệp vụ mới

Viết một tệp mô hình theo các mẫu ở [`backend/dx-engine/src/main/resources/templates/`](./backend/dx-engine/src/main/resources/templates); muốn nó hiện ở thư viện mẫu thì đặt tệp vào đó và khai trong `index.json`.
Áp dụng bằng `POST /api/v1/models/apply`, `dxos apply <tệp>` hoặc `dxos start <tệp>...`; không cần sửa mã lõi.
Sau khi áp dụng, `python3 e2e/api/check_conformance.py <tên mô hình>` kiểm mô hình giữ các rào chắn của lõi.
Kiểu trường, ràng buộc, `required_when` và khối `ui:` mô tả ở [`docs/MODEL.md`](./docs/MODEL.md); tài liệu OpenAPI của mô hình ở `GET /api/v1/meta/models/{model}/openapi` dùng để sinh client, và một dịch vụ ngoài đăng ký bằng `dxos client add`.
Nếu nghiệp vụ cần một năng lực mà tệp mô hình chưa khai báo được, mở một issue mô tả: một năng lực mới chỉ được thêm khi cần ở ít nhất ba lĩnh vực và khai báo được mà không cần cú pháp mới.
