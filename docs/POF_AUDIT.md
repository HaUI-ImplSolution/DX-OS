# Rà soát PoF theo đề thi và phiếu chấm

Bảng đối chiếu repository với mục 5, 10, 11, 12 của [đề thi](./reference/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf) và phần B, C.I của [phiếu đánh giá](./reference/Phieu_du_kien_danh_gia_Hoi_dong_PMNM_HaUI_2026.pdf).
Phần PoF chiếm 50 điểm và được chấm trên repository trước phần trình diễn, nên mọi bằng chứng phải thấy được từ trang chủ GitHub.

Kiểm ngày 30/10/2026 trên `main` (`41a01c3`, tag `v1.1.0`) và `Khuong/main` (`58906c3`).
Số liệu GitHub lấy từ API công khai, không đăng nhập, nên cũng là những gì giám khảo nhìn thấy.
Các mục ghi "nhánh `Khuong/pof-hardening`" đã sửa trong mã nhưng chỉ có hiệu lực trên GitHub sau khi nhánh được gộp.

Ký hiệu: ✅ đạt, 🔧 đã sửa trong nhánh `Khuong/pof-hardening`, chờ gộp và phát hành, ⚠️ còn thiếu hoặc chưa chuẩn, ❌ chưa có.

Hồ sơ nộp (báo cáo kỹ thuật, slide, video, ảnh giao diện) do nhóm bổ sung riêng nên không nằm trong bảng này.

## 1. Tóm tắt

| Tiêu chí PoF | Điểm | Tình trạng | Việc còn lại |
|---|---|---|---|
| 1. Kho mã nguồn và lịch sử phát triển | 5 | ✅ | Gộp PR #160; đóng dần issue #161 đến #168 bằng PR |
| 2. Giấy phép và tính tương thích | 10 | 🔧 | Gộp nhánh |
| 3. Release và quản lý phiên bản | 5 | 🔧 | Gộp nhánh, đặt tag mới, chạy `Release Package` cho 5 release cũ |
| 4. Build và cài đặt từ mã nguồn | 10 | 🔧 | Chạy thử trên máy sạch (#161, #162) |
| 5. Thư viện, phụ thuộc, nguồn gốc | 10 | 🔧 | Quyết định về thư mục `ai/.codex` (mục 7) |
| 6. Tài liệu, quản lý lỗi, giao tiếp | 10 | 🔧 | Gộp nhánh; bật Private vulnerability reporting |

Không có lỗi loại trừ ở mục 12 của đề: repository công khai, có LICENSE, có release, không phát hiện secret.

## 2. Điều kiện kiểm tra trước khi chấm (phần B của phiếu)

| # | Điều kiện | Kết quả | Bằng chứng |
|---|---|---|---|
| 1 | Repository công khai, có web viewer | ✅ | `HaUI-ImplSolution/DX-OS`, `visibility: public`, API trả 200 không đăng nhập |
| 2 | Giấy phép OSI, có LICENSE toàn văn, có thông báo | ✅ | `LICENSE` Apache-2.0 (10,8 KB, toàn văn), GitHub nhận đúng `Apache-2.0`; `ai/LICENSE` AGPL-3.0; `NOTICE`; `COPYRIGHT.txt` |
| 3 | Có release/tag trước hạn nộp, không dùng định dạng độc quyền | 🔧 | 5 tag và 5 GitHub Release; tệp `.tar.gz` đính kèm do workflow mới tạo; hạn nộp thật chưa chốt |
| 4 | Có hướng dẫn build/install, chạy lại được | 🔧 | README có bước dựng bằng Docker, build không Docker và chạy ngoài thư mục mã nguồn |
| 5 | Khai báo thư viện, phiên bản, nguồn, giấy phép | ✅ | `COPYRIGHT.txt` và `sbom/`, mọi gói có giấy phép |
| 6 | README, CHANGELOG, bug tracker, tài liệu đúng trạng thái | 🔧 | Đã có 8 issue, xem mục 8 |
| 7 | Không lộ secret hay dữ liệu cá nhân | ✅ | Không thấy secret; email mỗi người một địa chỉ, xem mục 9 |
| 8 | Lịch sử Git liên tục, commit hợp lý | ✅ | 686 commit từ 05/09 đến 30/10/2026, mọi tuần đều có commit, xem mục 3 |

## 3. Tiêu chí 1: kho mã nguồn và lịch sử (5 điểm)

| Mục | Trạng thái | Bằng chứng đã kiểm | Việc cần làm |
|---|---|---|---|
| Công khai, truy cập mở | ✅ | API không đăng nhập trả 200 cho repo, release, tag, `LICENSE` | |
| Lịch sử trải đều | ✅ | Số commit theo tuần ISO: W36 3, W37 80, W38 81, W39 52, W40 92, W41 97, W42 85, W43 84, W44 112 | |
| Nhiều người đóng góp | ✅ | 3 sinh viên: `KhuongSpring` 457, `tung51652-alt` 117, `nddKhanh` 107 commit; còn dependabot | |
| Nhận diện tác giả | 🔧 | `git log` có 9 tên cho 3 người; `.mailmap` gom về `KhuongSpring`, `tung51652-alt`, `nddKhanh` (hai commit mang email cơ quan chưa gom, xem mục 9) | |
| Repo được dùng thật | ✅ | 158 pull request (135 đã gộp), CI chạy trên mọi PR | |
| Nhánh trên GitHub | ✅ | Chỉ 5 nhánh: `main`, `Khuong/main` và 3 nhánh Dependabot; trên máy còn tham chiếu `origin/*` cũ, dọn bằng `git fetch --prune` | |
| Thông điệp commit | ✅ | Phần lớn theo Conventional Commits; còn `Initial commit`, `update: docs`, `P1-5`, `feat: phrase 1` đến `4` ("phrase" thay vì "phase") trong lịch sử cũ | Không sửa lịch sử đã đẩy; biết để trả lời khi hội đồng hỏi |
| Tag phản ánh sản phẩm | ⚠️ | `main` chứa `v1.1.0` nhưng `Khuong/main` có thêm 1 commit tài liệu chưa vào `main` (PR #160 `DEV TO MAIN` đang mở) | Gộp PR #160 và nhánh `pof-hardening` trước khi chốt tag cuối |
| Issue được dùng | ✅ | 8 issue mở ngày 30/10/2026 (#161 đến #168), lấy từ `BACKLOG.md` | Đóng dần bằng PR có `Closes #n` |

## 4. Tiêu chí 2: giấy phép và tính tương thích (10 điểm)

| Mục | Trạng thái | Bằng chứng | Còn lại |
|---|---|---|---|
| License OSI, toàn văn, NOTICE | ✅ | `LICENSE` Apache-2.0, `ai/LICENSE` AGPL-3.0, `NOTICE` | |
| Giấy phép hai vùng rõ ràng | ✅ | `COPYRIGHT.txt` mục 1 và README: `ai/` AGPL-3.0, phần còn lại Apache-2.0; AI Service là tiến trình riêng gọi qua HTTP | |
| Thành phần bên thứ ba có giấy phép rõ | ✅ | `COPYRIGHT.txt` có bảng cho Maven, npm, Python, mô hình, dữ liệu, hạ tầng | |
| Header giấy phép trong từng tệp mã | 🔧 | Mọi tệp Java, TypeScript, JavaScript, Python, shell, YAML có `SPDX-License-Identifier`: `Apache-2.0`, riêng `ai/` là `AGPL-3.0-only` | |
| Header của `ai/` khớp `ai/LICENSE` | 🔧 | 77 tệp trong `ai/` từng mang `Apache-2.0` trong khi `ai/LICENSE` là AGPL-3.0; đã đổi sang `AGPL-3.0-only` | Xác nhận chọn `-only` hay `-or-later` |
| Kiểm giấy phép tự động | 🔧 | Job `license` của `CI Infra` chạy `.github/scripts/check_licenses.py`: kiểm header và giấy phép của từng gói trong `sbom/`, ngoại lệ ở `.github/license-exceptions.json` (hiện rỗng) | |
| Giấy phép mô hình CV | 🔧 | `COPYRIGHT.txt` và `ai/weights/README.md` giải thích vì sao áp AGPL-3.0 cho `crop_classifier.onnx` dù thẻ mô hình ghi Apache-2.0 | |
| Bảng công nghệ trong README | 🔧 | Dòng AI Service ghi AGPL-3.0 cho mã `ai/` và MIT, Apache-2.0 cho thư viện | |
| Dữ liệu RAG của Lab | ⚠️ | `COPYRIGHT.txt` nêu nguồn, ngày truy cập, giấy phép, SHA-256 của từng văn bản HaUI và giữ nguyên nội dung; chưa có văn bản cho phép phân phối lại từ HaUI | Xin xác nhận bằng văn bản của HaUI nếu có thể, hoặc giữ nguyên cách ghi nguồn |

## 5. Tiêu chí 3: release và quản lý phiên bản (5 điểm)

| Mục | Trạng thái | Bằng chứng | Còn lại |
|---|---|---|---|
| Có tag và release | ✅ | `v1.0.0` (13/10) đến `v1.1.0` (30/10), đều là release chính thức | |
| CHANGELOG theo phiên bản | ✅ | Keep a Changelog, có mục cho cả 5 bản và `[Unreleased]` | |
| Tệp phát hành định dạng mở | 🔧 | Workflow `Release Package` (`.github/workflows/release-package.yml`) chạy khi đẩy tag `v*`: đính kèm `dx-os-X.Y.Z.tar.gz`, `.sha256` và các SBOM `.cdx.json`; cả 5 release hiện có `assets: []` | Chạy workflow từ tab Actions cho từng tag cũ (ô `replace_notes` để ghi đè ghi chú bằng mục CHANGELOG) |
| Ghi chú phát hành | 🔧 | Workflow lấy ghi chú từ mục CHANGELOG của tag; ghi chú hiện có của `v1.0.0`, `v1.0.1`, `v1.1.0` còn mỏng và liên kết của `v1.1.0` ghi tên `RELEASE.md` | Chạy `Release Package` với `replace_notes` cho các tag đó |
| Image theo phiên bản | 🔧 | Workflow `Release` chạy trên tag thì đẩy thêm tag image `vX.Y.Z` lên GHCR | Chạy `Release` trên tag mới |
| Số phiên bản trong mã khớp tag | 🔧 | Backend, web, mobile, SBOM cùng `1.1.1` | Nếu bản kế tiếp không phải `1.1.1` thì đổi ở `backend/**/pom.xml`, `frontend/package.json`, `mobile/package.json`, `mobile/app.json` |
| Tài liệu nêu đúng release | ⚠️ | `docs/SUBMISSION.md` mục 1 vẫn ghi Release `v1.0.0` đến `v1.0.3` (thuộc hồ sơ nộp nên chưa sửa) | Sửa thành `v1.1.0` hoặc bản mới nhất |
| Ngày hạn nộp | ⚠️ | `BACKLOG.md` mục 1: chưa chốt; tag cuối phải tạo trước hạn | Chốt ngày, rồi đặt tag cuối |

## 6. Tiêu chí 4: build và cài đặt từ mã nguồn (10 điểm)

| Mục | Trạng thái | Bằng chứng | Còn lại |
|---|---|---|---|
| Hướng dẫn cài đặt đầy đủ | ✅ | `README.md` mục "Cài đặt và chạy": yêu cầu hệ thống, các lệnh từ `git clone` đến `./deploy/start.sh` | |
| Cấu hình trước khi build, không sửa mã | ✅ | Mọi cấu hình ở `deploy/.env` (mẫu `deploy/.env.example`); `deploy/doctor.sh` kiểm trước khi chạy | |
| Công cụ nguồn mở | ✅ | Docker, Compose, Git, Maven wrapper, npm, pip | |
| Chạy lại được trên stack sạch | ✅ | Workflow `E2E` chạy Farm 10 bước và Lab 16 bước, xanh trên `main` ngày 30/10 | |
| Build không qua Docker | 🔧 | README mục "Build từ mã nguồn không qua Docker": lệnh cho backend, web, mobile, AI (bản tiếng Anh ở `README_EN.md`) | |
| Chạy ngoài thư mục mã nguồn | 🔧 | `deploy/install.sh <prefix> [tag]` chép `deploy/`, `data/`, `sso/`, `bi/`, `chat/` và crop classifier sang thư mục khác, sinh `.env`, dùng image GHCR; job `install` của CI kiểm compose và mọi tệp được mount; đã chạy thử `config` cho Farm và Lab | Chạy `docker compose pull` và `start.sh` từ thư mục đã cài (#162); các package GHCR đã công khai |
| Hai đường nhập `.env` khác nhau | 🔧 | README và `CONTRIBUTING.md` cùng dùng `make_ci_env.py`, đường điền tay ghi thêm | |
| Kiểm clone sạch | ⚠️ | `BACKLOG.md` mục 3: "Khởi động bản Qwen từ clone sạch" chưa kiểm | Chạy đúng chuỗi README trên máy sạch trước ngày nộp |
| Yêu cầu phần cứng với giám khảo | ⚠️ | 8 GB RAM, 40 GB ổ đĩa; Qwen cần CPU có AVX2, máy không có thì đặt `AI_ANSWER_GENERATION=false` (đã ghi ở README) | |
| Dung lượng clone | ⚠️ | `.git` nặng khoảng 259 MB do trọng số mô hình | Ghi dung lượng clone trong README nếu cần |

## 7. Tiêu chí 5: thư viện, phụ thuộc, nguồn gốc (10 điểm)

| Mục | Trạng thái | Bằng chứng | Còn lại |
|---|---|---|---|
| Danh sách thư viện, phiên bản, giấy phép | ✅ | `COPYRIGHT.txt` 7 mục; README gọi đây là bảng dependency | |
| SBOM đủ giấy phép | 🔧 | `sbom/ai.cdx.json` thiếu giấy phép ở 107/108 gói Python; `.github/scripts/enrich_sbom_licenses.py` bổ sung từ PyPI, nay 108/108 | Dấu "BSD License" của PyPI được hiểu là `BSD-3-Clause`, nên xem lại nếu cần chính xác tới từng gói |
| Phạm vi SBOM | 🔧 | `sbom/README.md` ghi rõ SBOM chỉ gồm thư viện của mã nhóm viết; image bên thứ ba ở `COPYRIGHT.txt` mục 6; SBOM đính kèm release | |
| Ghim phiên bản image | 🔧 | Mọi image bên thứ ba trong `deploy/docker-compose.yml` ghim tag và digest; `compose config` hợp lệ cho Farm và Lab | `deploy/backup.sh` và `deploy/reset.sh` còn dùng tag trần cho PostgreSQL và Alpine |
| Không sửa mã thượng nguồn | ✅ | Không có tệp `.patch`; Keycloak dựng từ bản phát hành chính thức; Mattermost nguyên bản | |
| Không phát hành kèm thư viện đóng gói | ✅ | Không có `vendor/`, `node_modules/`, `third_party/` trong cây Git | |
| Nguồn gốc tệp mô hình | 🔧 | `ai/weights/README.md` ghi nguồn, commit và SHA-256 của `crop_classifier.onnx`, đã so với bản gốc trên Hugging Face: trùng từng byte | |
| Tệp `.zip` của Superset | 🔧 | `bi/README.md` ghi đây là định dạng export của Superset | |
| Tài liệu của công cụ AI trong repo | ⚠️ | `ai/.codex/skills/` (5 thư mục, nội dung thuộc dự án thượng nguồn như LangChain, Hugging Face) và `backend/.claude/agents/` được commit mà không ghi nguồn hay giấy phép | Quyết định giữ, bỏ khỏi repo, hoặc ghi nguồn và giấy phép; chưa xóa vì là công cụ làm việc của nhóm |
| Tệp nhị phân trong repo | ⚠️ | `ai/weights/` khoảng 250 MB | Không gỡ khỏi lịch sử; cân nhắc tải lúc build nếu muốn clone nhẹ hơn |
| Cập nhật phụ thuộc | ✅ | Dependabot, CI `Dependencies` chặn khi có lỗ hổng HIGH/CRITICAL đã có bản vá | |

## 8. Tiêu chí 6: tài liệu, quản lý lỗi, giao tiếp (10 điểm)

| Mục | Trạng thái | Bằng chứng | Còn lại |
|---|---|---|---|
| README đủ mục đề yêu cầu | ✅ | `README.md` và `README_EN.md`: kiến trúc 3 tầng, bảng H-P-D-I cho Farm và Lab, tính năng, công nghệ và giấy phép, cài đặt, cấu hình, chạy thử, tài khoản demo, API, kiểm thử, đóng góp | |
| CHANGELOG | ✅ | Có mục cho cả 5 bản; `[Unreleased]` ghi các thay đổi của nhánh này | |
| Tài liệu kiến trúc, API | ✅ | `ARCHITECTURE.md`; springdoc/Swagger UI trong backend; 66 tệp Markdown không có liên kết tương đối hỏng | |
| CONTRIBUTING, CODE_OF_CONDUCT | ✅ | GitHub xác nhận cả hai | |
| CONTRIBUTING khớp quy trình thật | 🔧 | Nhánh `Khuong/main` làm nhánh tích hợp, mục Phát hành mô tả tag trên `main` và các workflow | |
| `SECURITY.md` | 🔧 | Có tệp: cách báo qua GitHub, quy trình xử lý, phạm vi, cơ chế quét trong CI | Bật Private vulnerability reporting ở Settings > Security; kiểm lại cam kết "xác nhận trong 7 ngày" |
| Biểu mẫu issue | 🔧 | `bug_report.yml`, `feature_request.yml`, `config.yml` thay cho hai tệp `.md`; liên kết trong README đã đổi | Kiểm ở tab Issues > New issue sau khi gộp |
| Issue tracker có nội dung | ✅ | 8 issue (#161 đến #168), danh sách dưới | |
| Actions có lượt chạy lỗi | ⚠️ | Workflow `Online check` (chạy theo lịch) có ba lượt `failure` ngày 29 và 30/10; tệp workflow không còn trên `main` | Không cần làm nếu workflow đã bỏ; lượt lỗi cũ vẫn hiện ở tab Actions |
| Bản test | ✅ | 224 phương thức `@Test` Java (39 tệp), 148 hàm test Python; E2E hai ứng dụng trong CI | |

### Issue đã mở

Lấy từ `BACKLOG.md` mục 3 đến 6, mỗi việc một issue gắn nhãn `enhancement`; đóng bằng PR có `Closes #n`:

- #161 Kiểm khởi động từ clone sạch với Qwen bật.
- #162 Kiểm cài đặt ngoài thư mục mã nguồn bằng image GHCR.
- #163 Đánh giá toàn pipeline chẩn đoán bằng ảnh ruộng thật có nhãn kỹ sư.
- #164 Xóa `audit_log` theo thời hạn.
- #165 Cảnh báo đăng nhập sai theo tài khoản.
- #166 Phân trang tra cứu chi tiết ở phía máy chủ (màn hình giám sát).
- #167 Che thông tin cá nhân trong log.
- #168 Bổ sung bài tri thức Lab về máy in, VPN, quy chế phòng máy.

## 9. Lộ dữ liệu nhạy cảm (điều kiện 7 của phần B)

| Kiểm tra | Kết quả |
|---|---|
| Mẫu khóa, token, khóa riêng trên cây hiện tại | Không có (quét mẫu AWS, GitHub, Slack, Google, khóa riêng) |
| Cùng các mẫu trên toàn bộ lịch sử Git | Không có |
| Tệp `.env` thật bị theo dõi | Không; chỉ có bốn tệp `.env.example` |
| Mật khẩu mặc định trong `.env.example` | Để trống, `make_ci_env.py` sinh ngẫu nhiên; `.gitleaks.toml` chỉ cho phép vài mật khẩu demo cũ còn trong lịch sử |
| Gitleaks | CI `Infra` có job `secret-scan`; trên máy này chưa cài gitleaks nên chưa tự chạy lại toàn lịch sử |
| Email trong repo | ✅ Mỗi thành viên một địa chỉ, nhóm đã xác nhận: ở `README.md`, `README_EN.md` mục Liên hệ và `CODE_OF_CONDUCT.md` (một địa chỉ của Khương) |
| Email trong lịch sử commit | ✅ `.mailmap` gom mọi danh tính về một tên và một email mỗi người (kể cả hai commit mang email cơ quan), nên `git log` và `git shortlog` chỉ hiện địa chỉ đã chọn; lịch sử gốc không bị viết lại |

## 10. Mức trừ điểm có thể gặp (mục 12 của đề)

| Lỗi trong đề | Mức xử lý của đề | DX-OS hiện tại |
|---|---|---|
| Repository không công khai | Không chấm PoF | Không vi phạm |
| Không có LICENSE hoặc license không rõ | -5 đến -10 | Không vi phạm |
| Không có release/tag | -5 | Không vi phạm; release chưa có tệp phát hành cho đến khi chạy `Release Package` |
| Không build/install được từ mã nguồn | -5 đến -10 | Rủi ro thấp; còn cần chạy thử trên máy sạch (#161, #162) |
| Không có README/hướng dẫn chạy | -5 | Không vi phạm |
| Không có CHANGELOG hoặc bug tracker | -3 đến -5 | Không vi phạm: có CHANGELOG và 8 issue |
| Commit secret, API key, dữ liệu nhạy cảm | Trừ nghiêm trọng | Không thấy secret; email mỗi người một địa chỉ (mục 9) |
| Dùng thành phần đóng hoặc không rõ giấy phép | Không tính phần liên quan | Không thấy; công cụ AI ở `ai/.codex` chưa ghi nguồn (mục 7) |
| Không chứng minh được Open-Core headless | Trừ mạnh ở tiêu chí kiến trúc | Ngoài phạm vi tài liệu này; `ArchitectureTest` và `ARCHITECTURE.md` là bằng chứng chính |

## 11. Thứ tự làm tiếp

1. Duyệt và gộp nhánh `Khuong/pof-hardening`, rồi gộp PR #160, để `main` có đủ nội dung.
2. Đóng dần các issue #161 đến #168 bằng PR; bật Private vulnerability reporting.
3. Chốt ngày hạn nộp; đặt tag mới trên `main` (workflow `Release Package` tự tạo Release kèm gói), chạy `Release` để có image `vX.Y.Z`.
4. Chạy `Release Package` thủ công cho 5 tag cũ để có tệp phát hành và ghi chú.
5. Chạy chuỗi README trên một máy sạch, cả cách cài ra thư mục khác (`deploy/install.sh`).
6. Quyết định về thư mục `ai/.codex` và `-only` hay `-or-later` của AGPL.

## 12. Phạm vi kiểm tra

Đã kiểm: cây Git và lịch sử cục bộ, API công khai của GitHub (repo, release, tag, issue, PR, Actions, community profile), tệp trong repo, quét mẫu secret trên cây hiện tại và toàn lịch sử, liên kết Markdown tương đối, thẻ mô hình và tệp ONNX trên Hugging Face.
Các thay đổi trong nhánh đã chạy thử cục bộ: `check_licenses.py` (0 vấn đề), `install.sh` và `docker compose config` ở thư mục cài, biên dịch backend, kiểm kiểu web và mobile, cú pháp YAML và shell, các bước tạo gói của workflow phát hành.

Chưa kiểm được:

- Workflow `Release Package` và job `install`, `license` chạy trên GitHub Actions (chỉ chạy thử từng bước ở máy này).
- Cài đặt GitHub cần quyền chủ repo: branch protection, Private vulnerability reporting.
- Chạy lại README từ một clone sạch trên máy mới, và `docker compose pull` từ GHCR.
- Gitleaks hoặc trufflehog toàn lịch sử; quét mới dừng ở các mẫu khóa phổ biến.
