# Rà soát PoF theo đề thi và phiếu chấm

Bảng tự chấm 50 điểm PoF: đối chiếu repository với [đề thi](https://vfossa.vn/tin-tuc/de-thi-phan-mem-nguon-mo-olp-2026-798.html), [thể lệ](https://vfossa.vn/tin-tuc/the-le-olp-phan-mem-nguon-mo-nam-2026-760.html) và phiếu đánh giá của Hội đồng.
Phần PoF được chấm trên repository trước buổi trình diễn, không có đội thi giải thích, nên mọi bằng chứng phải thấy được từ trang chủ GitHub.
Mỗi dòng ghi yêu cầu, bằng chứng, lệnh tự kiểm và mức trừ nếu thiếu; chỉ đánh dấu ✅ khi đã chạy lệnh và thấy kết quả sạch.

Kiểm ngày 06/11/2026 trên `Khuong/main` (`0fcef13`, sau khi gộp PR #191).
Số GitHub lấy từ API công khai, không đăng nhập, nên cũng là những gì giám khảo nhìn thấy.
Hồ sơ nộp (báo cáo kỹ thuật, slide, video) không nằm trong bảng này; mức hoàn thiện của từng tính năng ở [Ma trận bằng chứng](./evidence-matrix.md).

Ký hiệu: ✅ đạt, ⚠️ còn thiếu hoặc chưa kiểm được, ❌ chưa có.

## 1. Tóm tắt

| Tiêu chí PoF | Điểm | Tình trạng | Việc còn lại |
|---|---|---|---|
| 1. Kho mã nguồn và lịch sử phát triển | 5 | ✅ | Mở issue cho các việc còn lại để bug tracker có lịch sử |
| 2. Giấy phép và tính tương thích | 10 | ✅ | Không còn việc |
| 3. Release và quản lý phiên bản | 5 | ⚠️ | Tạo tag `v2.0.0` và Release trước hạn nộp |
| 4. Build và cài đặt từ mã nguồn | 10 | ⚠️ | Chạy lại chuỗi cài đặt trên máy sạch với image phát hành thật |
| 5. Thư viện, phụ thuộc, nguồn gốc | 10 | ✅ | Không còn việc |
| 6. Tài liệu, quản lý lỗi, giao tiếp | 10 | ✅ | Kiểm cài đặt GitHub cần quyền chủ repository |

Không có lỗi loại trừ: repository công khai, có `LICENSE`, đã có release (hiện đến `v1.1.1`), không phát hiện bí mật.

## 2. Điều kiện kiểm tra trước khi chấm

| # | Điều kiện | Kết quả | Bằng chứng |
|---|---|---|---|
| 1 | Repository công khai, có web viewer | ✅ | `HaUI-ImplSolution/DX-OS`, `visibility: public`, API trả 200 không đăng nhập |
| 2 | Giấy phép OSI, có `LICENSE` toàn văn, có thông báo | ✅ | `LICENSE` Apache-2.0, GitHub nhận đúng `Apache-2.0`; `NOTICE`; `DEPENDENCIES.txt` |
| 3 | Có release hoặc tag trước hạn nộp, không dùng định dạng độc quyền | ⚠️ | 6 release `v1.0.0` đến `v1.1.1`, mỗi release có `.tar.gz`; bản `2.0.0` chưa gắn tag |
| 4 | Có hướng dẫn build và cài, chạy lại được | ⚠️ | `README.md` mục Chạy thử; chạy lại trên máy sạch với image `v2.0.0` chờ có release |
| 5 | Khai báo thư viện, phiên bản, nguồn, giấy phép | ✅ | `DEPENDENCIES.txt`, `sbom/`, `.github/component-catalog.json` |
| 6 | README, CHANGELOG, bug tracker, tài liệu đúng trạng thái | ✅ | Xem tiêu chí 6 |
| 7 | Không lộ bí mật hay dữ liệu cá nhân | ✅ | Xem mục 9 |
| 8 | Lịch sử Git liên tục, commit hợp lý | ✅ | Xem tiêu chí 1 |

## 3. Tiêu chí 1: kho mã nguồn và lịch sử (5 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Kho công khai trên Internet, có web viewer | GitHub: duyệt mã, lịch sử, blame trên trình duyệt | −3 |
| ✅ | Truy cập mở, không cần đăng nhập | API không đăng nhập trả 200 cho repository, release, tag, `LICENSE` | −3 |
| ✅ | Repository được dùng thật | 808 commit trên nhánh làm việc; 153 pull request đã gộp (GitHub, 06/11/2026); CI chạy trên mọi pull request | −5 |
| ✅ | Lịch sử trải đều | Mười tuần ISO liên tiếp từ W36 đến W45 đều có commit: 3, 80, 82, 53, 93, 97, 85, 84, 136, 95 | |
| ✅ | Nhiều người đóng góp | 3 sinh viên: `KhuongSpring` 565, `nddKhanh` 119, `tung51652-alt` 119 commit, cộng `dependabot` 5 | |
| ✅ | Nhận diện tác giả | `.mailmap` gom các danh tính về một tên mỗi người | |
| ⚠️ | Bug tracker có lịch sử | 8 issue so với 153 pull request; phần lớn việc đã ghi ở `BACKLOG.md` và `CHANGELOG.md` thay vì issue | Mở issue cho mỗi dòng của Việc tồn đọng và đóng bằng pull request có `Closes #n` |

```bash
git log --pretty='%h %ad %an %s' --date=short | tail -20     # lịch sử có trải đều không
git shortlog -sn                                             # đóng góp có nhiều người không
```

## 4. Tiêu chí 2: giấy phép và tính tương thích (10 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Giấy phép được OSI công nhận | `LICENSE` Apache-2.0 | điều kiện loại trừ |
| ✅ | Ghi giấy phép trong từng tệp mã | Dòng `SPDX-License-Identifier` ở đầu mọi tệp Java, TypeScript, JavaScript, Python, shell, YAML: `Apache-2.0`; `check_licenses.py` báo 0 vấn đề ngày 06/11/2026 | −5 |
| ✅ | Không xung đột giấy phép giữa các thành phần | `DEPENDENCIES.txt`; repository chỉ có một giấy phép, Apache-2.0; job `Check licenses` của workflow `Compliance` kiểm giấy phép của từng gói trong `sbom/`, ngoại lệ có lý do ở `.github/dependency-exceptions.json` | −5 |
| ✅ | Có thông báo về mục đích của giấy phép | `NOTICE` | −5 |
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
| ✅ | Có ít nhất một release trước hạn nộp | Trang Releases: `v1.0.0`, `v1.0.1`, `v1.0.2`, `v1.0.3`, `v1.1.0`, `v1.1.1` | −5 |
| ⚠️ | Release là sản phẩm dự thi | Sản phẩm dự thi là bản `2.0.0` (`CHANGELOG.md` mục `[2.0.0]`, `backend/pom.xml`, `frontend/package.json`); tag `v2.0.0` chưa tạo | −5 |
| ✅ | Phát hành theo phiên bản | Thẻ SemVer; `CHANGELOG.md` theo Keep a Changelog có mục cho mọi bản và `[Unreleased]` | −3 |
| ✅ | Định dạng mở cho gói phát hành | Workflow `Release Package` chạy khi đẩy tag `v*`, đính kèm `dx-os-X.Y.Z.tar.gz`, `.sha256` và SBOM `.cdx.json`; không có `.zip` | −3 |
| ✅ | Gói được kiểm trước khi đăng | `check_archive.sh` kiểm gói có `LICENSE`, `NOTICE`, `DEPENDENCIES.txt`, `README.md`, `CHANGELOG.md` và không có tệp giống bí mật; chạy ở job `Install from the release archive` của workflow `Platform` của mọi pull request và trong `Release Package` trước khi đăng | |
| ⚠️ | Image theo phiên bản | Workflow `Release Images` đẩy image `vX.Y.Z` lên GHCR; `dxos-install.sh` kéo image này nên bản `2.0.0` chưa cài được bằng một lệnh cho đến khi chạy | |

GitHub tự đính kèm `Source code (zip)` vào mỗi release và không xóa được; phần mô tả release ghi rõ `dx-os-X.Y.Z.tar.gz` mới là gói phát hành chính thức.

Trình tự phát hành bản `2.0.0`:

1. Đưa nhánh làm việc vào `main`.
2. Sửa ngày của mục `[2.0.0]` trong `CHANGELOG.md` theo ngày đặt tag.
3. Đẩy tag `v2.0.0`; `Release Package` tạo Release kèm gói.
4. Chạy workflow `Release Images` trên tag để có image `v2.0.0`.

## 6. Tiêu chí 4: build và cài đặt từ mã nguồn (10 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Có hướng dẫn dịch từ mã nguồn | `README.md` mục Chạy thử, phần Từ mã nguồn; `README_EN.md` | −5 |
| ✅ | Không cấu hình bằng cách sửa tay tệp mã | Mọi tham số ở `deploy/.env`, mẫu `deploy/.env.example`; `deploy/make_env.sh` sinh mật khẩu ngẫu nhiên; job `Validate the Compose file` của workflow `Platform` kiểm Compose dừng khi thiếu bí mật | −5 |
| ✅ | Cấu hình được trước khi dịch | `deploy/make_env.sh` rồi mới `docker compose up -d --build` | −5 |
| ✅ | Dịch bằng công cụ nguồn mở | Docker, Compose, Git, Maven wrapper, npm | −5 |
| ✅ | Chạy được ngoài thư mục mã nguồn | `dxos-install.sh` cài vào `~/.dxos`, `install.sh` từ chối cài vào chính thư mục mã nguồn; job `Install from the release archive` của workflow `Platform` cài từ gói, gói sai SHA-256 bị từ chối, mọi tệp Compose mount đều có | −5 |
| ✅ | Hệ thống dựng từ trạng thái rỗng dùng được qua trình duyệt | Job `Browser end-to-end`: dựng image của commit, khởi động stack rỗng, Playwright đăng nhập bằng Keycloak, áp dụng một mẫu và thấy mô hình chạy (`e2e/tests/console.spec.ts`) | |
| ⚠️ | Chạy lại chuỗi trên máy sạch | Đã kiểm trên máy dev với image dựng tại chỗ; chưa kiểm với image phát hành thật vì bản `2.0.0` chưa phát hành | |

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
| ✅ | SBOM khớp với tệp khóa | Job `Check SBOMs against lockfiles` dựng lại SBOM của web và trang docs rồi so với `sbom/` ở mọi pull request; SBOM của backend so với kết quả của `cyclonedx-maven-plugin` (221 gói, 0 khác biệt ngày 06/11/2026) | |

```bash
git ls-files | grep -E '(^|/)(vendor|third_party|node_modules)/|\.patch$' && echo "VI PHẠM" || echo "Sạch"
```

## 8. Tiêu chí 6: tài liệu, quản lý lỗi, giao tiếp (10 điểm)

| Trạng thái | Yêu cầu | Bằng chứng | Mức trừ nếu thiếu |
|---|---|---|---|
| ✅ | Có bug tracker | GitHub Issues đã bật; mẫu `bug_report.yml`, `feature_request.yml`; 8 issue | −5 |
| ✅ | Có changelog | `CHANGELOG.md` theo Keep a Changelog, mục `[Unreleased]` và mục cho mọi bản đến `[2.0.0]` | −5 |
| ✅ | Có README và hướng dẫn | `README.md`, `README_EN.md`; `ARCHITECTURE.md`; `docs/TESTING.md`; `deploy/OPERATIONS.md`; API qua Swagger UI và `check_docs.py` kiểm tài liệu khớp OpenAPI; job `Check required files and scripts` báo lỗi nếu thiếu một tài liệu bắt buộc hoặc `CHANGELOG.md` mất mục `[Unreleased]`; job `Build the docs site` dựng trang docs và dừng khi có liên kết hỏng | −5 |
| ✅ | Hướng dẫn đóng góp | `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, mẫu pull request | |
| ✅ | Chính sách bảo mật | `SECURITY.md` | |
| ⚠️ | Bảo vệ nhánh và tag | `.github/rulesets/main.json` (cấm xóa, cấm ghi đè lịch sử, chỉ nhận pull request gộp bằng merge, 16 check phải xanh) và `tags.json` (cấm sửa và xóa tag `v*`) có sẵn để nhập; chỉ có hiệu lực khi chủ repository nhập vào Settings → Rules | Nhập hai ruleset vào GitHub |
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
| Không có release hoặc tag | −5 | Không vi phạm; sản phẩm dự thi `2.0.0` cần được gắn tag |
| Không build hoặc cài được từ mã nguồn | −5 đến −10 | Rủi ro thấp; còn cần chạy lại trên máy sạch với image phát hành |
| Không có README hoặc hướng dẫn chạy | −5 | Không vi phạm |
| Không có CHANGELOG hoặc bug tracker | −3 đến −5 | Không vi phạm |
| Commit bí mật hoặc dữ liệu nhạy cảm | Trừ nghiêm trọng | Không thấy |
| Dùng thành phần đóng hoặc không rõ giấy phép | Không tính phần liên quan | Không thấy; thành phần chưa đạt đã gỡ |

## 11. Rà soát cuối cùng

Làm trên bản phát hành cuối, trước 17:00 ngày 06/12/2026.
Lần kiểm ở các bảng trên không thay cho mục này.

- [ ] Tag `v2.0.0` và Release đã có, kèm `dx-os-2.0.0.tar.gz`, `.sha256` và các SBOM `.cdx.json`.
- [ ] Image `v2.0.0` có trên GHCR; `curl ... dxos-install.sh | bash` rồi `dxos start` chạy được trên máy sạch.
- [ ] Clone repository vào máy sạch, chạy `deploy/make_env.sh` và `docker compose up -d --build` thành công.
- [ ] Mở repository bằng trình duyệt ẩn danh, xác nhận truy cập được không cần đăng nhập.
- [ ] `python3 .github/scripts/check_licenses.py` báo 0 vấn đề trên bản phát hành.
- [ ] CI xanh trên commit được gắn tag: `Platform`, `Compliance`, `Backend`, `Frontend`, `Dependencies`.
- [ ] Hai ruleset ở `.github/rulesets/` đã nhập vào GitHub.
- [ ] Private vulnerability reporting đã bật; `SECURITY.md` có trên trang chủ repository.
- [ ] `CHANGELOG.md` có mục cho mọi bản đã phát hành và ngày của `[2.0.0]` khớp ngày tag.
- [ ] Không có bí mật trong lịch sử: job `Scan for secrets` của workflow `Compliance` xanh.
- [ ] README, `ARCHITECTURE.md`, `docs/TESTING.md` mô tả đúng bản `2.0.0`; `check_docs.py` đạt.
- [ ] Trang docs mô tả đúng bản `2.0.0`.
- [ ] Thư gửi BTC kèm thành viên, người hướng dẫn, đường dẫn repository và cam kết bản quyền theo mẫu của đề.

## 12. Phạm vi kiểm tra

Đã kiểm: cây Git và lịch sử cục bộ, API công khai của GitHub (repository, release, tag, issue, pull request), tệp trong repository, `check_licenses.py`, danh sách tệp bị theo dõi.

Chưa kiểm được:

- Cài đặt GitHub cần quyền chủ repository: Private vulnerability reporting, và việc hai ruleset đã được nhập.
- Cài đặt từ image phát hành thật của bản `2.0.0`.
- Số lượt chạy xanh của từng workflow trên commit gắn tag; xem tab Actions.
