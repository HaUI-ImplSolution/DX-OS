# Plan thực hiện: Phase 8

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.
Phase này chạy xuyên suốt P1-P7 (mỗi phase tự viết test/tài liệu của mình theo D6), các WP dưới đây là bước tổng hợp và lấp khoảng trống cuối cùng.

## Thứ tự và phụ thuộc

WP8-02 (dữ liệu demo) chặn WP8-01 (E2E cần dữ liệu demo mới chạy được trọn luồng).
WP8-03 (hồ sơ PoF), WP8-05 (portal + lỗi tiếng Việt) độc lập, làm song song được.
WP8-04 (README/CHANGELOG đồng bộ) nên làm gần cuối vì cần phản ánh đúng kết quả của mọi WP khác.
WP8-06 (kịch bản demo + báo cáo/slide/video) làm cuối cùng, cần mọi WP khác đã ổn định.
Thứ tự: WP8-02 → WP8-01 → (WP8-03 ∥ WP8-05) → WP8-04 → WP8-06.

## WP8-01: Test cho phần lõi và E2E trên môi trường sạch

- Mục tham chiếu: mục 18 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: có bộ test tự động cho nghiệp vụ lõi và một kịch bản E2E chạy được từ clone/tag sạch, có trong CI.
- Ước lượng: M (phần lớn test đã viết rải ở P1-P6 theo D6, đây là bước rà khoảng trống còn thiếu).

**Hiện trạng đã rà soát**

- Trước P1, chỉ có 2 file test (`TreatmentRuleTest`, `CatalogActiveFilterTest`).
  Từ P1 tới P6, mỗi phase đã thêm test theo khung D6 (khóa PHI, idempotency, phạm vi dữ liệu, chuyển trạng thái workflow, ràng buộc vụ mùa...) — cần rà lại xem còn thiếu case nào không, không viết lại từ đầu.
- Chưa có kịch bản E2E tự động nào (dựng compose sạch → tạo hộ/thửa/vụ → báo cáo → chẩn đoán → duyệt → phun → thu hoạch bị chặn) chạy trong CI.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| CI chạy khi đổi code | Chỉ build backend khi đổi `backend/**` (đã mở rộng một phần ở P1 WP1-04) | Có thêm job E2E: dựng compose sạch, chạy kịch bản đủ bước, dọn sau khi xong |
| Kiểm chứng "chạy được từ clone sạch" | Chưa có bằng chứng ghi lại | Log CI của job E2E là bằng chứng trực tiếp |

**Hướng làm**

- Rà lại danh sách case D6 đã viết ở P1 (`03_TEST_PLAN.md` các phase) đối chiếu với mục 18's yêu cầu "state machine duyệt, khóa PHI, phạm vi dữ liệu FARMER, idempotency, workflow" — bổ sung case còn thiếu, không viết lại case đã có.
- Viết script/test E2E (ví dụ dùng Testcontainers hoặc docker compose thật trong CI runner) chạy chuỗi: tạo hộ (P2) → tạo thửa/vụ (P4) → duyệt (P3/P4) → báo cáo (gốc) → chẩn đoán (P6) → duyệt chẩn đoán → ghi nhận phun (P1/P5) → thử thu hoạch sớm bị chặn (P1).
- Thêm job E2E vào `.github/workflows/ci.yml` (mở rộng từ P1 WP1-04), chạy riêng với job build thông thường vì tốn thời gian hơn.

**Phạm vi ảnh hưởng**

- Module và file chính: `backend/DX-Farm/src/test/`, `.github/workflows/ci.yml`, script E2E mới (có thể ở `development/` hoặc `deploy/`).
- Endpoint, bảng DB, biến môi trường: không có thay đổi hành vi, chỉ thêm test.
- Role và client bị ảnh hưởng: không ảnh hưởng runtime.
- Tương thích ngược và dữ liệu cũ: không áp dụng.

**Kết quả đầu ra**

- CI có job E2E chạy xanh trên clone sạch.
- Danh sách case D6 của mọi phase đã rà, không còn khoảng trống rõ ràng (khóa PHI, idempotency, phạm vi dữ liệu, workflow).

**Phụ thuộc, lưu ý**: cần WP8-02 (dữ liệu demo) có treatment rule hợp lệ để kịch bản E2E chạy được hết bước (khóa PHI), trừ khi chấp nhận demo nhánh `review_required` theo Q2.

## WP8-02: Dữ liệu demo chạy hết luồng

- Mục tham chiếu: mục 18 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: database dựng từ clone sạch có đủ dữ liệu để chạy trọn luồng, kể cả áp dụng thuốc và khóa PHI.
- Ước lượng: S-M (phụ thuộc Q2, có thể kéo dài nếu chờ chuyên gia).

**Hiện trạng đã rà soát**

- Seed hiện có bệnh nhưng chưa có treatment rule/policy nào được duyệt (`PROJECT_IMPROVEMENTS.md` mục 18) — trên database sạch, đường nhập tay từng lỗi 404 vì đòi rule active (đã xử lý một phần ở P6 WP6-04, không còn chặn cứng).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Dựng database sạch | Thiếu rule được duyệt, không demo trọn luồng khóa PHI | Có ít nhất 1 treatment rule đã duyệt thật (nếu Q2 = có) hoặc demo trung thực nhánh `review_required` (nếu Q2 = không) |
| Dữ liệu mẫu | Thiếu thửa ở ĐBSCL có tọa độ thật, thiếu báo cáo mẫu kèm ảnh | Script seed demo riêng (tách khỏi seed nghiệp vụ) tạo vài hộ, vài thửa ở ĐBSCL có GPS thật, vài vụ ở nhiều trạng thái, vài báo cáo kèm ảnh thật |

**Hướng làm**

- Script `seed-demo` riêng (không gộp vào seed nghiệp vụ mặc định), chạy một lệnh là có sẵn: vài hộ (P2), vài thửa có tọa độ thật ở ĐBSCL (P4), vài vụ ở các trạng thái khác nhau (ACTIVE/HARVESTED), vài báo cáo kèm ảnh thật đã qua precheck (P6).
- Theo dõi tiến độ duyệt treatment rule (Q2) riêng với tiến độ code — nếu không kịp, viết rõ trong README/báo cáo là demo nhánh `review_required`, không giả vờ có rule thật.

**Phạm vi ảnh hưởng**

- Module và file chính: script seed demo mới (ví dụ `backend/DX-Farm/src/main/resources/db/seed-demo/` hoặc tương đương), không đổi code nghiệp vụ.
- Endpoint, bảng DB, biến môi trường: không có.
- Role và client bị ảnh hưởng: không ảnh hưởng runtime, chỉ ảnh hưởng dữ liệu khi chạy script.
- Tương thích ngược và dữ liệu cũ: không áp dụng, script chạy trên database mới.

**Kết quả đầu ra**

- Chạy script seed demo trên database sạch, có đủ dữ liệu để demo (hoặc chạy trọn luồng khóa PHI thật, hoặc trung thực nhánh `review_required` theo Q2).

**Phụ thuộc, lưu ý**: chặn WP8-01 (E2E cần dữ liệu); Q2 ảnh hưởng trực tiếp phạm vi kịch bản demo (WP8-06).

## WP8-03: Hồ sơ PoF — repo, license, release, issue tracker

- Mục tham chiếu: mục 19 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: đủ điều kiện chấm PoF theo rubric (repo public, LICENSE, release/tag, quản lý dependency, CONTRIBUTING, issue tracker).
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `git tag` hiện rỗng — chưa có release nào.
- Không có `CONTRIBUTING.md`; `.github/` chỉ có `ci.yml`, không có issue/PR template.
- `ai/requirements.txt` dùng khoảng phiên bản (`>=,<`), không phải lockfile cố định; chưa có SBOM.
- Backend Java chưa có SPDX header (một số file AI Python đã có, ví dụ `ai/app/api/router.py` dòng 1: `# SPDX-License-Identifier: Apache-2.0`).
- `LICENSES.md` đã có, cần rà lại khớp với dependency thực tế sau các phase mới (MinIO, APISIX từ P3, các thư viện mobile mới từ P4).

**Hành vi thay đổi**

Không áp dụng — đây là việc đóng gói hồ sơ, không đổi hành vi hệ thống.

**Hướng làm**

- Tag sớm một bản (ví dụ `v0.1.0`) ngay khi các phase nền (P0-P3) ổn định, tag lại bản chốt (ví dụ `v1.0.0`) khi hoàn tất P8.
- Viết `CONTRIBUTING.md` (quy trình đóng góp, hướng dẫn thêm module/quy trình mới — trả lời trực tiếp câu hỏi hội đồng số 10).
- Thêm `.github/ISSUE_TEMPLATE/bug_report.md`, `feature_request.md`, `.github/PULL_REQUEST_TEMPLATE.md`; bật GitHub Issues, tạo vài issue mẫu từ chính các mục còn mở trong `PROJECT_IMPROVEMENTS.md`.
- Pin phiên bản chính xác trong `ai/requirements.txt` (hoặc dùng `pip freeze`/`uv.lock` tương đương), sinh SBOM (ví dụ `pip-licenses`/`cyclonedx-py` cho Python, `license-checker` cho npm).
- Thêm SPDX header cho mã tự viết ở backend Java (nhất quán với AI Python đã có).
- Rà lại `LICENSES.md` khớp với dependency thực tế sau P3/P4 (MinIO Apache-2.0, APISIX Apache-2.0, các thư viện mobile mới).

**Phạm vi ảnh hưởng**

- Module và file chính: `CONTRIBUTING.md`, `.github/` (template mới), `ai/requirements.txt`, `LICENSES.md`, SBOM mới, SPDX header rải trong `backend/DX-Farm/src/main/java`.
- Endpoint, bảng DB, biến môi trường: không có.
- Role và client bị ảnh hưởng: không ảnh hưởng runtime.
- Tương thích ngược và dữ liệu cũ: không áp dụng.

**Kết quả đầu ra**

- Repo có ít nhất 1 tag/release.
- `CONTRIBUTING.md`, issue template tồn tại; `LICENSES.md` khớp dependency thật; SBOM tồn tại.

**Phụ thuộc, lưu ý**: nên làm sau khi các phase code lớn (P1-P7) đã ổn định để `LICENSES.md`/SBOM không phải rà lại nhiều lần.

## WP8-04: Đồng bộ README, CHANGELOG với thực tế

- Mục tham chiếu: mục 19 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: README mô tả đúng kiến trúc 3 tầng/H-P-D-I theo yêu cầu đề, không còn mô tả tiến độ M1-M6 đã cũ; phân biệt rõ "đã có trong code"/"đã kiểm thử"/"đã có dữ liệu demo".
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- README gốc hiện thiên về tiến độ M1-M6, một số mục "Chưa làm" đã cũ (ví dụ từng ghi chưa có SSO — nay đã có); README theo yêu cầu đề cần mô tả kiến trúc 3 tầng, H-P-D-I, tính năng, cài đặt, cấu hình, chạy thử, API, tài khoản demo.

**Hành vi thay đổi**

Không áp dụng — việc viết tài liệu, không đổi hành vi hệ thống.

**Hướng làm**

- Viết lại mục kiến trúc trong README theo khung 3 tầng (trỏ sơ đồ từ P3 WP3-01), H-P-D-I (trỏ các phase tương ứng: H→P2, P→P3/P4, D→P7, I→P6/P7).
- Bỏ các mục mô tả tiến độ M1-M6 đã lỗi thời; chỉ còn mục trạng thái cuối, phân tách rõ 3 mức: đã có trong code / đã kiểm thử / đã có dữ liệu demo (theo đúng quy ước mục 19 và `WORKFLOW.md` mục 10).
- `CHANGELOG.md`: rà lại các mục "Chưa làm" cũ, xoá hoặc cập nhật cho đúng trạng thái hiện tại.

**Phạm vi ảnh hưởng**

- Module và file chính: `README.md`, `CHANGELOG.md` (gốc repo).
- Endpoint, bảng DB, biến môi trường: không có.
- Role và client bị ảnh hưởng: không ảnh hưởng runtime.
- Tương thích ngược và dữ liệu cũ: không áp dụng.

**Kết quả đầu ra**

- README có đủ mục đề yêu cầu, không còn thông tin sai lệch với code thật.

**Phụ thuộc, lưu ý**: làm gần cuối, sau khi P1-P7 đã có kết quả thật để mô tả đúng.

## WP8-05: Portal hiện trạng dịch vụ, chuẩn hoá lỗi tiếng Việt

- Mục tham chiếu: mục 16, 20 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: portal thể hiện trạng thái vận hành từng dịch vụ; thông báo lỗi hiển thị cho người dùng bằng tiếng Việt, không lộ tiếng Anh thẳng từ backend.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `portal/` hiện chỉ có `index.html` tĩnh liệt kê link tới các dịch vụ, không hiện trạng thái up/down.
- Thông báo lỗi hiện lấy thẳng từ backend (tiếng Anh) hiện ra cho người dùng ở FE/mobile (theo `PROJECT_IMPROVEMENTS.md` mục 16).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Xem portal | Danh sách link tĩnh | Mỗi dịch vụ có chỉ báo up/down, lấy từ endpoint healthcheck đã có (backend, AI, Superset...) |
| Lỗi backend hiển thị cho người dùng | Thông điệp tiếng Anh thẳng từ exception | Mã lỗi chuẩn hoá (ví dụ `ERR_HARVEST_LOCKED`) + bản dịch tiếng Việt ở FE/mobile |

**Hướng làm**

- `portal/index.html` (hoặc thêm chút JS): gọi healthcheck của từng service (backend `/actuator/health`, AI `/disease/health`, Superset endpoint có sẵn) định kỳ, hiện chỉ báo màu.
- Backend: `GlobalExceptionHandler` đã có — thêm trường `errorCode` (String, ổn định, không đổi theo bản dịch) vào `ApiResponse` lỗi; FE/mobile có bảng ánh xạ `errorCode → tiếng Việt`.

**Phạm vi ảnh hưởng**

- Module và file chính: `portal/index.html`, `backend/.../exception/GlobalExceptionHandler.java`, FE/mobile (bảng ánh xạ lỗi).
- Endpoint, bảng DB, biến môi trường: response lỗi thêm trường `errorCode`.
- Role và client bị ảnh hưởng: mọi client hiển thị lỗi cho người dùng cuối.
- Tương thích ngược và dữ liệu cũ: thêm trường vào response lỗi không phá client cũ (chỉ thêm, không đổi field cũ).

**Kết quả đầu ra**

- Portal hiện đúng trạng thái up/down từng dịch vụ.
- Lỗi phổ biến (khóa PHI, idempotency trùng, phân quyền) hiển thị tiếng Việt cho người dùng.

**Phụ thuộc, lưu ý**: không phụ thuộc WP khác trong phase này, có thể làm song song với WP8-03.

## WP8-06: Kịch bản demo, báo cáo kỹ thuật, slide, video

- Mục tham chiếu: mục 19, 20 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: có bộ hồ sơ hoàn chỉnh để nộp bài và trình bày trước hội đồng.
- Ước lượng: M (chủ yếu công sức viết/dựng, không phải code).

**Hiện trạng đã rà soát**

- Chưa có kịch bản demo 10 phút, báo cáo kỹ thuật, slide, video nào theo `PROJECT_IMPROVEMENTS.md` mục 19.

**Hành vi thay đổi**

Không áp dụng.

**Hướng làm**

- Viết kịch bản demo 10 phút theo chuỗi: người dùng (farmer mobile) → Open-Core (chỉ rõ qua gateway, P3) → Process (duyệt qua workflow, P3/P4) → Data (dashboard Superset, P7) → Intelligence (chẩn đoán + hỏi đáp RAG, P6/P7) → kết quả.
- Chuẩn bị bộ trả lời cho các câu hỏi hội đồng đã liệt kê ở mục 20 `PROJECT_IMPROVEMENTS.md`, mỗi câu dẫn đúng phase/tài liệu làm bằng chứng (đã có sẵn địa chỉ cụ thể từ các phase 0-7).
- Viết báo cáo kỹ thuật 8-10 trang, slide tối đa 12, quay video demo 3-5 phút — rehearsal ít nhất 1 lần trước khi nộp, chừa tối thiểu 2 ngày cuối cho việc này (theo khuyến nghị của `PHASE_PLAN.md` mục 5).

**Phạm vi ảnh hưởng**

- Module và file chính: tài liệu mới (báo cáo, slide, video), không đổi code.
- Endpoint, bảng DB, biến môi trường: không có.
- Role và client bị ảnh hưởng: không ảnh hưởng runtime.
- Tương thích ngược và dữ liệu cũ: không áp dụng.

**Kết quả đầu ra**

- Bộ hồ sơ đầy đủ: kịch bản demo đã tập, báo cáo, slide, video, bộ trả lời câu hỏi hội đồng.

**Phụ thuộc, lưu ý**: làm cuối cùng, sau khi mọi WP khác trong P8 và các phase trước đã ổn định.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Response DTO | `ApiResponse` lỗi | Thêm trường `errorCode` | Không (thêm trường) |
| Tài liệu | `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `LICENSES.md` | Viết lại/thêm mới | Không áp dụng (không phải API) |

## Dữ liệu, migration và rollback

- Migration: không có thay đổi schema bắt buộc ngoài những gì các phase trước đã làm; nếu WP8-05 cần lưu lịch sử healthcheck thì thêm bảng nhỏ (tuỳ chọn, không bắt buộc).
- Dữ liệu cũ: không ảnh hưởng.
- Rollback: tài liệu/hồ sơ không cần rollback theo nghĩa kỹ thuật; tag/release có thể xoá nếu cần làm lại.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D6 (kế thừa phase 0) | Test case ảnh hưởng logic/an toàn hệ thống, rải suốt P1-P6 | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | Chuyển repo public ngay khi rà xong lịch sử commit | Tránh rủi ro quên/hết thời gian nếu để tới sát hạn |
| Q2 | Có, một phần: rule đạo ôn và khô vằn được duyệt và seed; đốm nâu đi nhánh `review_required` | Demo trọn luồng khóa PHI với dữ liệu thật cho đạo ôn |
