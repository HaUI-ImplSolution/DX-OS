# Rà soát PoF theo đề thi và phiếu chấm

Bảng tự chấm 50 điểm PoF: đối chiếu repository với [đề thi](https://vfossa.vn/tin-tuc/de-thi-phan-mem-nguon-mo-olp-2026-798.html), [thể lệ](https://vfossa.vn/tin-tuc/the-le-olp-phan-mem-nguon-mo-nam-2026-760.html) và phiếu đánh giá của Hội đồng.
Phần PoF được chấm trên repository trước buổi trình diễn, không có đội thi giải thích, nên mọi bằng chứng phải thấy được từ trang chủ GitHub.
Mỗi dòng ghi yêu cầu, bằng chứng, lệnh tự kiểm và mức trừ nếu thiếu; chỉ đánh dấu ✅ khi đã chạy lệnh và thấy kết quả sạch.

Số liệu tính trên nhánh `Khuong/main`.
Số GitHub lấy từ API công khai, không đăng nhập, nên cũng là những gì giám khảo nhìn thấy.
Thư gửi bài (thành viên, người hướng dẫn, đường dẫn kho, cam kết bản quyền) không nằm trong bảng này; đề không đòi báo cáo kỹ thuật, slide hay video.
Mức hoàn thiện của từng tính năng ở [Ma trận bằng chứng](./evidence-matrix.md).

Ký hiệu: ✅ đạt, ⚠️ còn thiếu hoặc chưa kiểm được, ❌ chưa có.
Một tiêu chí chỉ được ✅ khi mọi dòng của nó và mọi điều kiện ở mục 2 liên quan đến nó đều ✅.

## 1. Tóm tắt

| Tiêu chí PoF | Điểm | Tình trạng | Điều còn mở |
| --- | --- | --- | --- |
| 1. Kho mã nguồn và lịch sử phát triển | 5 | ⚠️ | Lịch sử issue mỏng so với số pull request |
| 2. Giấy phép và tính tương thích | 10 | ✅ | |
| 3. Release và quản lý phiên bản | 5 | ⚠️ | `main` chậm 40 commit so với `Khuong/main`; bản `v2.0.0` chưa chứa phần mới |
| 4. Build và cài đặt từ mã nguồn | 10 | ⚠️ | Bài dựng từ bản clone chưa chạy lại trên máy sạch |
| 5. Thư viện, phụ thuộc, nguồn gốc | 10 | ✅ | |
| 6. Tài liệu, quản lý lỗi, giao tiếp | 10 | ⚠️ | Chưa kiểm được private vulnerability reporting |

Không có lỗi loại trừ: repository công khai, có `LICENSE`, đã có release (hiện đến `v2.0.0`), không phát hiện bí mật.

## 2. Điều kiện kiểm tra trước khi chấm

| # | Điều kiện | Kết quả | Bằng chứng |
|---|---|---|---|
| 1 | Repository công khai, có web viewer | ✅ | `HaUI-ImplSolution/DX-OS`, `visibility: public`, API trả 200 không đăng nhập |
| 2 | Giấy phép OSI, có `LICENSE` toàn văn, có thông báo | ✅ | `LICENSE` Apache-2.0, GitHub nhận đúng `Apache-2.0`; `NOTICE`; `DEPENDENCIES.txt` |
| 3 | Có release hoặc tag trước hạn nộp, không dùng định dạng độc quyền | ✅ | 7 release `v1.0.0` đến `v2.0.0`, mỗi release có `.tar.gz` |
| 4 | Có hướng dẫn build và cài, chạy lại được | ⚠️ | `README.md` mục Chạy thử; `dxos-install.sh` của `v2.0.0` đã chạy được trên máy sạch, còn bài dựng từ bản clone (`docker compose up -d --build`) chưa chạy lại trên máy sạch |
| 5 | Khai báo thư viện, phiên bản, nguồn, giấy phép | ✅ | `DEPENDENCIES.txt`, `sbom/`, `.github/component-catalog.json` |
| 6 | README, CHANGELOG, bug tracker, tài liệu đúng trạng thái | ✅ | Xem tiêu chí 6 |
| 7 | Không lộ bí mật hay dữ liệu cá nhân | ✅ | Xem mục 9 |
| 8 | Lịch sử Git liên tục, commit hợp lý | ✅ | Xem tiêu chí 1 |

## 3. Tiêu chí 1: kho mã nguồn và lịch sử (5 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Kho công khai trên Internet, có web viewer | GitHub: duyệt mã, lịch sử, blame trên trình duyệt | −3 |
| ✅ | Truy cập mở, không cần đăng nhập | API không đăng nhập trả 200 cho repository, release, tag, `LICENSE` | −3 |
| ✅ | Repository được dùng thật | 998 commit trên nhánh làm việc; 201 pull request đã gộp (GitHub); CI chạy trên mọi pull request | −5 |
| ✅ | Lịch sử trải đều | Mười một tuần ISO liên tiếp từ W36 đến W46 đều có commit: 3, 80, 82, 53, 93, 97, 85, 84, 136, 119, 166 | |
| ✅ | Nhiều người đóng góp | 3 sinh viên: `KhuongSpring` 718, `nddKhanh` 144, `tung51652-alt` 127 commit, cộng `dependabot` 7 | |
| ✅ | Nhận diện tác giả | `.mailmap` gom các danh tính về một tên mỗi người | |
| ⚠️ | Bug tracker có lịch sử | 55 issue (36 đã đóng) so với 201 pull request đã gộp; khoảng 20 issue đã xong nhưng còn mở vì pull request tính năng gộp vào `Khuong/main` chứ chưa vào `main`, GitHub chỉ tự đóng issue khi `Closes #n` nằm trên nhánh mặc định; một pull request DEV TO MAIN sắp tới sẽ đóng các issue đó | |

```bash
git log --pretty='%h %ad %an %s' --date=short | tail -20     # lịch sử có trải đều không
git shortlog -sn                                             # đóng góp có nhiều người không
```

## 4. Tiêu chí 2: giấy phép và tính tương thích (10 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Giấy phép được OSI công nhận | `LICENSE` Apache-2.0 | điều kiện loại trừ |
| ✅ | Ghi giấy phép trong từng tệp mã | Dòng `SPDX-License-Identifier` ở đầu mọi tệp Java, TypeScript, JavaScript, Python, shell, YAML: `Apache-2.0`; `check_licenses.py` báo 0 vấn đề | −5 |
| ✅ | Không xung đột giấy phép giữa các thành phần | `DEPENDENCIES.txt`; mã, tài liệu, ảnh, video và sơ đồ theo Apache-2.0, hai tệp phông theo OFL-1.1, đều được OSI công nhận và tương thích nhau; job `Check licenses` của workflow `Compliance` kiểm giấy phép của từng gói trong `sbom/`, ngoại lệ có lý do ở `.github/dependency-exceptions.json` | −5 |
| ✅ | Có thông báo về mục đích của giấy phép | `NOTICE`, mục "Mục đích của giấy phép": vì sao chọn Apache-2.0, bốn quyền tự do, quyền sáng chế, và cách khai giấy phép của từng tệp | −5 |
| ✅ | Có bản sao toàn văn giấy phép | `LICENSE` | −5 |
| ✅ | Mọi giấy phép được OSI công nhận | `check_licenses.py` chỉ cho phép giấy phép OSI; hai gói ngoại lệ (dữ liệu CC-BY-4.0 và CC0-1.0 chỉ dùng lúc dựng trang docs) nêu tên kèm lý do ở `.github/dependency-exceptions.json` | |
| ✅ | GitHub nhận một giấy phép | `LICENSE` là Apache-2.0 và là tệp duy nhất mà tab License liệt kê; các tệp thành phần tên `DEPENDENCIES.txt`, `component-catalog.json`, `dependency-exceptions.json` không chứa chữ license hay copyright để GitHub không coi là giấy phép thứ hai | |

```bash
python3 .github/scripts/check_licenses.py      # header, giấy phép của SBOM, danh mục image và mô hình
head -3 LICENSE                                # toàn văn, không phải liên kết
```

## 5. Tiêu chí 3: release và quản lý phiên bản (5 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Có ít nhất một release trước hạn nộp | Trang Releases: `v1.0.0`, `v1.0.1`, `v1.0.2`, `v1.0.3`, `v1.1.0`, `v1.1.1`, `v2.0.0` | −5 |
| ⚠️ | Release là sản phẩm dự thi | Tag `v2.0.0` đã tạo (11/11); các việc gộp vào `Khuong/main` sau đó (#270 đến #295: Prometheus, mã hồ sơ, mẫu in, phả hệ dữ liệu, lint mô hình, điện thoại, quét mã độc e2e...) chưa nằm trong bản tag nào; nhánh mặc định `main` chậm 40 commit so với `Khuong/main` (kiểm 14/11), nên cần một pull request vào `main` rồi một bản phát hành mới trước khi nộp | −5 |
| ✅ | Phát hành theo phiên bản | Thẻ SemVer; `CHANGELOG.md` theo Keep a Changelog có mục cho mọi bản và `[Unreleased]` | −3 |
| ✅ | Định dạng mở cho gói phát hành | Workflow `Release Package` chạy khi đẩy tag `v*`, đính kèm `dx-os-X.Y.Z.tar.gz`, `.sha256` và SBOM `.cdx.json`; không có `.zip` | −3 |
| ✅ | Gói được kiểm trước khi đăng | `check_archive.sh` kiểm gói có `LICENSE`, `NOTICE`, `DEPENDENCIES.txt`, `README.md`, `CHANGELOG.md` và không có tệp giống bí mật; chạy ở job `Install from the release archive` của workflow `Platform` của mọi pull request và trong `Release Package` trước khi đăng | |
| ✅ | Image theo phiên bản | Workflow `Release Images` đẩy image `vX.Y.Z` lên GHCR; `dxos-install.sh` kéo image này, bản `2.0.0` cài được bằng một lệnh | |

GitHub tự đính kèm `Source code (zip)` vào mỗi release và không xóa được; phần mô tả release ghi rõ `dx-os-X.Y.Z.tar.gz` mới là gói phát hành chính thức.

## 6. Tiêu chí 4: build và cài đặt từ mã nguồn (10 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Có hướng dẫn dịch từ mã nguồn | `README.md` mục Chạy thử, phần Từ mã nguồn; `README_EN.md` | −5 |
| ✅ | Không cấu hình bằng cách sửa tay tệp mã | Mọi tham số ở `deploy/.env`, mẫu `deploy/.env.example`; `deploy/make_env.sh` sinh mật khẩu ngẫu nhiên; job `Validate the Compose file` của workflow `Platform` kiểm Compose dừng khi thiếu bí mật | −5 |
| ✅ | Cấu hình được trước khi dịch | `deploy/make_env.sh` rồi mới `docker compose up -d --build` | −5 |
| ✅ | Dịch bằng công cụ nguồn mở | Docker, Compose, Git, Maven wrapper, npm | −5 |
| ✅ | Chạy được ngoài thư mục mã nguồn | `dxos-install.sh` cài vào `~/.dxos`, `install.sh` từ chối cài vào chính thư mục mã nguồn; job `Install from the release archive` của workflow `Platform` cài từ gói, gói sai SHA-256 bị từ chối, mọi tệp Compose mount đều có | −5 |
| ✅ | Hệ thống dựng từ trạng thái rỗng dùng được qua trình duyệt | Job `Browser end-to-end`: dựng image của commit, khởi động stack rỗng, Playwright đăng nhập bằng Keycloak, áp dụng một mẫu và thấy mô hình chạy (`e2e/tests/console.spec.ts`) | |
| ✅ | Chạy lại chuỗi trên máy sạch | `dxos-install.sh` của release `v2.0.0` cài vào `~/.dxos` trên máy chủ không còn stack cũ, `dxos start` kéo image từ GHCR, áp dụng `onestop.yaml` và `dxos demo` chạy được | |

Bài kiểm tra bắt buộc làm trước ngày chấm, giả lập thao tác của giám khảo:

```bash
cd /tmp && rm -rf pof-test && mkdir pof-test && cd pof-test
git clone https://github.com/HaUI-ImplSolution/DX-OS.git && cd DX-OS
deploy/make_env.sh
cd deploy && docker compose up -d --build
# Hệ thống phải lên đủ dịch vụ mà không sửa tệp mã nào.

# Cài từ bản phát hành, ngoài thư mục mã nguồn
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start
```

## 7. Tiêu chí 5: thư viện, phụ thuộc, nguồn gốc (10 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Tài liệu làm rõ thư viện và gói đã dùng | `DEPENDENCIES.txt` (tên, phiên bản, giấy phép, vai trò); `sbom/*.cdx.json` kể cả gói gián tiếp, đính kèm mỗi release; lý do chọn ở [Thành phần được chọn và không chọn](./dependencies.md) | |
| ✅ | Ưu tiên thư viện sẵn có | Dùng image chính thức, ghim tag và digest trong `deploy/docker-compose.yml`; Keycloak dựng từ bản phát hành chính thức kiểm SHA-1; binary `pgschema` lấy từ image chính thức ghim digest | −5 |
| ✅ | Không phát hành kèm gói của dự án phụ thuộc | `git ls-files` không có `vendor/`, `node_modules/`, `third_party/` | −5 |
| ✅ | Không chỉnh sửa mã nguồn gói đính kèm | Không có tệp `.patch`; tùy biến bằng cấu hình (route APISIX, realm Keycloak, biến môi trường) | −5 |
| ✅ | Phụ thuộc được cập nhật và quét | Dependabot; workflow `Dependencies` chặn khi có lỗ hổng HIGH hoặc CRITICAL đã có bản vá | |
| ✅ | SBOM khớp với tệp khóa | Job `Check SBOMs against lockfiles` dựng lại SBOM của web và trang docs rồi so với `sbom/` ở mọi pull request; SBOM của backend so với kết quả của `cyclonedx-maven-plugin` (221 gói, 0 khác biệt) | |

```bash
git ls-files | grep -E '(^|/)(vendor|third_party|node_modules)/|\.patch$' && echo "VI PHẠM" || echo "Sạch"
```

## 8. Tiêu chí 6: tài liệu, quản lý lỗi, giao tiếp (10 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Có bug tracker | GitHub Issues đã bật; mẫu `bug_report.yml`, `feature_request.yml`; 55 issue | −5 |
| ✅ | Có changelog | `CHANGELOG.md` theo Keep a Changelog, mục `[Unreleased]` và mục cho mọi bản đến `[2.0.0]` | −5 |
| ✅ | Có README và hướng dẫn | `README.md`, `README_EN.md`; `ARCHITECTURE.md`; `docs/TESTING.md`; `deploy/OPERATIONS.md`; API qua đặc tả OpenAPI và `check_docs.py` kiểm tài liệu khớp OpenAPI; job `Check required files and scripts` báo lỗi nếu thiếu một tài liệu bắt buộc hoặc `CHANGELOG.md` mất mục `[Unreleased]`; job `Build the docs site` dựng trang docs, trong đó có các tài liệu của `docs/` và `deploy/` chép lại mỗi lần dựng, và dừng khi có liên kết hỏng | −5 |
| ✅ | Hướng dẫn đóng góp | `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, mẫu pull request | |
| ✅ | Chính sách bảo mật | `SECURITY.md` | |
| ✅ | Bảo vệ nhánh và tag | Hai ruleset đang `active` (API GitHub, 14/11): `main` (cấm xóa, cấm ghi đè lịch sử, bắt buộc pull request, 16 check phải xanh; một đối tượng được bỏ qua) và `tags-v-immutable` (cấm sửa và xóa tag `v*`); khớp với `.github/rulesets/main.json` và `tags.json` | |
| ⚠️ | Private vulnerability reporting | Cần quyền chủ repository nên chưa kiểm được từ bên ngoài | Kiểm ở Settings của repository |

## 9. Lộ bí mật và dữ liệu cá nhân

| Kiểm tra | Kết quả |
|---|---|
| Quét bí mật trong cây mã và toàn lịch sử | Job `Scan for secrets` của workflow `Compliance` chạy gitleaks ở mọi pull request; cấu hình ở `.gitleaks.toml` |
| Tệp `.env` thật bị theo dõi | Không; chỉ có `deploy/.env.example` |
| Mật khẩu mặc định | Không; `deploy/make_env.sh` sinh ngẫu nhiên, Compose dừng khi thiếu bí mật |
| Email trong repository | Mỗi thành viên một địa chỉ ở mục Liên hệ của README; `.mailmap` gom các danh tính trong lịch sử, lịch sử không bị viết lại |

## 10. Mức trừ điểm có thể gặp (mục 12 của đề)

| Lỗi trong đề | Mức xử lý của đề | DX-OS hiện tại |
|---|---|---|
| Repository không công khai | Không chấm PoF | Không vi phạm |
| Không có `LICENSE` hoặc giấy phép không rõ | −5 đến −10 | Không vi phạm |
| Không có release hoặc tag | −5 | Không vi phạm; `v2.0.0` đã tag, bản nộp cuối cần một tag mới nhất |
| Không build hoặc cài được từ mã nguồn | −5 đến −10 | Rủi ro thấp; còn cần chạy lại trên máy sạch với image phát hành |
| Không có README hoặc hướng dẫn chạy | −5 | Không vi phạm |
| Không có CHANGELOG hoặc bug tracker | −3 đến −5 | Không vi phạm |
| Commit bí mật hoặc dữ liệu nhạy cảm | Trừ nghiêm trọng | Không thấy |
| Dùng thành phần đóng hoặc không rõ giấy phép | Không tính phần liên quan | Không thấy; thành phần chưa đạt đã gỡ |

## 11. Rà soát cuối cùng

Danh sách việc làm trên bản phát hành cuối nằm ở [Việc tồn đọng](https://github.com/HaUI-ImplSolution/DX-OS/blob/main/docs/dynamic-core/BACKLOG.md), mục 2.
Lần kiểm ở các bảng trên không thay cho lần rà soát đó.

## 12. Phạm vi kiểm tra

Đã kiểm: cây Git và lịch sử cục bộ, API công khai của GitHub (repository, release, tag, issue, pull request), tệp trong repository, `check_licenses.py`, danh sách tệp bị theo dõi.

Chưa kiểm được:

- Cài đặt GitHub cần quyền chủ repository: Private vulnerability reporting, và việc hai ruleset đã được nhập.
- Cài đặt từ image phát hành thật của bản `2.0.0`.
- Số lượt chạy xanh của từng workflow trên commit gắn tag; xem tab Actions.
