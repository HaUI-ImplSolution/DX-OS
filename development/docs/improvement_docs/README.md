# Phases triển khai

Thư mục này chứa các phase để implement các vấn đề trong [`PROJECT_IMPROVEMENTS.md`](./PROJECT_IMPROVEMENTS.md).
Mỗi phase là một folder, sinh từ [`_template/`](./_template/).
Kế hoạch chia phase (P0 đến P10) nằm ở [`PHASE_PLAN.md`](./PHASE_PLAN.md).

## Cấu trúc một phase

| File | Vai trò | Bắt buộc |
|---|---|---|
| `00_OVERVIEW.md` | Vì sao làm, làm gì, phạm vi, kết quả đầu ra (Definition of Done) | Có |
| `01_PLAN.md` | Plan tổng quan theo từng work package: hành vi đổi, phạm vi ảnh hưởng, hợp đồng API/schema | Có |
| `02_CHECKLIST.md` | Việc chi tiết theo lớp (BE, FE, Mobile, AI, Infra, Docs) để tick | Có |
| `03_TEST_PLAN.md` | Kế hoạch kiểm thử và tiêu chí đạt của phase | Có |
| `04_TRADEOFFS_RISKS.md` | Trade-off, rủi ro, phương án dự phòng | Chỉ khi trade-off lớn, nếu nhỏ thì gộp vào `00` hoặc `01` rồi xóa file |

## Quy ước

- Tên folder: `phase-{N}-{slug-ngắn}`, ví dụ `phase-1-open-core-workflow`.
- Mã work package: `WP{N}-{xx}` (ví dụ `WP1-02`).
- Mã dòng checklist: `WP1-02-BE-03`. Lớp gồm `DOC`, `BE`, `FE`, `MB`, `AI`, `DATA`, `INFRA`, `TEST`.
- Mã test case: `T{N}-{xx}`, mỗi case ghi WP nó kiểm.
- Mọi WP phải trỏ về ít nhất một mục của `PROJECT_IMPROVEMENTS.md` (ghi `mục X`).
- Phase chỉ coi là xong khi mọi tiêu chí trong `00_OVERVIEW.md` mục 6 đạt và `02_CHECKLIST.md` mục "Đóng phase" đã tick.
- Chỉ ghi trạng thái cuối trong README/CHANGELOG của repo, không kể diễn biến trước/sau.
- Viết ngắn, tiếng Việt, mỗi câu một dòng. Comment trong code bằng tiếng Anh.
- Không tự commit hay push, phải có yêu cầu rõ của người dùng.

## Cách tạo phase mới

1. Copy `_template/` thành `phase-{N}-{slug}/`.
2. Điền `00_OVERVIEW.md` trước (chọn mục từ `PROJECT_IMPROVEMENTS.md`, chốt phạm vi và DoD).
3. Viết `01_PLAN.md`, rồi bẻ nhỏ thành `02_CHECKLIST.md` và `03_TEST_PLAN.md`.
4. Chỉ làm `04_TRADEOFFS_RISKS.md` đầy đủ khi có quyết định khó đảo ngược. Hỏi người dùng chốt các quyết định ở mục "Câu hỏi mở" trước khi code.
5. Cập nhật bảng chỉ mục bên dưới.

Gợi ý câu lệnh cho Claude: "Tạo phase {N} từ `improvement_docs/_template`, gồm mục {X, Y, Z} của `PROJECT_IMPROVEMENTS.md`, mức {Nghiêm trọng/Quan trọng}".

## Chỉ mục phase

| Phase | Tên | Mục tham chiếu | Mức độ | Trạng thái |
|---|---|---|---|---|
| _(chưa có)_ | | | | |
