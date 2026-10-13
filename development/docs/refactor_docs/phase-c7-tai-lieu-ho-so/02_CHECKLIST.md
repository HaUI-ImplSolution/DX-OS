# Checklist: Phase C7

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC7-{xx}-{lớp}-{nn}`.

## WPC7-01: Kiến trúc và link

Kết quả đầu ra: không còn link gãy; sơ đồ khớp sản phẩm.

**Spec và tài liệu**

- [x] WPC7-01-DOC-01 `submission/ARCHITECTURE_OPEN_CORE.md`: sơ đồ module, đồ thị phụ thuộc, interface, luồng request cho hai app.
- [x] WPC7-01-DOC-02 `README.md` mục kiến trúc: sơ đồ 3 tầng với hai app.
- [x] WPC7-01-DOC-03 `CONTRIBUTING.md`, `LICENSES.md`, `CHANGELOG.md`, `data/README.md`, `sbom/README.md`: thay `backend/DX-Farm` bằng đường dẫn mới.
- [x] WPC7-01-DOC-04 `legacy_docs/AI_EVENT_SCHEMA_README.md`, `pending_docs/MATTERMOST_WEBHOOK_NOTIFICATION_PLAN.md`: sửa link và đường dẫn tới file Java đã chuyển module.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC7-01-TEST-01 Tìm đường dẫn cũ (TC7-01).

## WPC7-02: README, CONTRIBUTING và hướng dẫn thêm app

Kết quả đầu ra: dựng được từng app; thêm được app mẫu rỗng theo hướng dẫn.

**Spec và tài liệu**

- [x] WPC7-02-DOC-01 `README.md`: mô tả DX-OS Open-Core, hai app, `DXOS_APP`, tài khoản demo từng app, lệnh build.
- [x] WPC7-02-DOC-02 `CONTRIBUTING.md`: lệnh kiểm theo module; hướng dẫn thêm app.
- [x] WPC7-02-DOC-03 `app-lab/README.md`, `app-farm/README.md`: domain, vai, interface đã cài.
- [x] WPC7-02-DOC-04 `deploy/demo/README.md`: seed và E2E theo app, chuyển app.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC7-02-TEST-01 Làm theo README và hướng dẫn thêm app (TC7-02, TC7-03).

## WPC7-03: Tài liệu nộp bài

Kết quả đầu ra: mỗi câu hỏi mục 13 có câu trả lời và tham chiếu.

**Spec và tài liệu**

- [x] WPC7-03-DOC-01 `submission/COUNCIL_QA.md`: trả lời 10 câu hỏi định hướng theo core + hai app.
- [x] WPC7-03-DOC-02 `submission/DEMO_SCRIPT.md`: kịch bản hai app và thao tác chuyển app, trong 10 phút.
- [x] WPC7-03-DOC-03 `submission/REPORT_OUTLINE.md`: dàn ý theo core, H-P-D-I, hai app.
- [x] WPC7-03-DOC-04 `LICENSES.md`: nguồn dữ liệu mẫu và tri thức của Lab.
- [x] WPC7-03-DOC-05 `CHANGELOG.md`: trạng thái cuối của core, hai app, hạ tầng.
- [x] WPC7-03-DOC-06 `improvement_docs/REMAINING_WORK.md`: cập nhật; ghi RD7 (AGPL cho `ai/`) xử lý riêng.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC7-03-TEST-01 Đối chiếu từng câu hỏi mục 13 (TC7-04).

## WPC7-04: Rà soát trước khi nộp

Kết quả đầu ra: quét secret sạch; SBOM khớp module.

**Infra, deploy, CI**

- [x] WPC7-04-INFRA-01 Quét lại secret (gitleaks) trên toàn repo.
- [x] WPC7-04-INFRA-02 Cập nhật SBOM cho các module backend mới.
- [x] WPC7-04-INFRA-03 `.gitleaks.toml`: kiểm mẫu bỏ qua sau đổi tên.

**Spec và tài liệu**

- [x] WPC7-04-DOC-01 Tìm `dxfarm`, `DX-Farm` ngoài chỗ giữ có chủ đích; ghi danh sách ngoại lệ.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC7-04-TEST-01 Quét secret và tìm tên cũ (TC7-05, TC7-06).

## Đóng phase

- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff (gitleaks lịch sử và cây làm việc không có phát hiện).
- [x] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
