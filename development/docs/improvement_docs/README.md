# Phases triển khai

Thư mục này chứa các phase để implement các vấn đề trong [`PROJECT_IMPROVEMENTS.md`](./PROJECT_IMPROVEMENTS.md).
Mỗi phase là một folder, sinh từ [`_template/`](../_template/).
Kế hoạch chia phase (P0 đến P10) nằm ở [`PHASE_PLAN.md`](./PHASE_PLAN.md); việc còn lại sau P0 đến P8 tổng hợp ở [`REMAINING_WORK.md`](./REMAINING_WORK.md).
Kế hoạch refactor Open-Core và đa ứng dụng (các phase C0 đến C7) nằm ở [`../refactor_docs/README.md`](../refactor_docs/README.md).

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

1. Copy `development/docs/_template/` thành `phase-{N}-{slug}/`.
2. Điền `00_OVERVIEW.md` trước (chọn mục từ `PROJECT_IMPROVEMENTS.md`, chốt phạm vi và DoD).
3. Viết `01_PLAN.md`, rồi bẻ nhỏ thành `02_CHECKLIST.md` và `03_TEST_PLAN.md`.
4. Chỉ làm `04_TRADEOFFS_RISKS.md` đầy đủ khi có quyết định khó đảo ngược. Hỏi người dùng chốt các quyết định ở mục "Câu hỏi mở" trước khi code.
5. Cập nhật bảng chỉ mục bên dưới.

Gợi ý câu lệnh cho Claude: "Tạo phase {N} từ `development/docs/_template`, gồm mục {X, Y, Z} của `PROJECT_IMPROVEMENTS.md`, mức {Nghiêm trọng/Quan trọng}".

## Chỉ mục phase

| Phase | Tên | Mục tham chiếu | Mức độ | Trạng thái |
|---|---|---|---|---|
| [P0](./phase-0-chot-quyet-dinh-pham-vi/00_OVERVIEW.md) | Chốt quyết định và phạm vi | 22, 23 | Nghiêm trọng | Xong |
| [P1](./phase-1-gia-co-an-toan-poka-yoke/00_OVERVIEW.md) | Gia cố an toàn, poka-yoke và nền dựng sạch | 9, 10, 17 | Nghiêm trọng | Xong |
| [P2](./phase-2-danh-tinh-nong-ho-phan-quyen/00_OVERVIEW.md) | Danh tính nông hộ, phân quyền, migration | 1, 6, 9, 16 | Nghiêm trọng | Xong |
| [P3](./phase-3-open-core-workflow-gateway-storage/00_OVERVIEW.md) | Open-Core: workflow, gateway, storage, sơ đồ | 7, 8, 17 | Nghiêm trọng | Xong |
| [P4](./phase-4-duyet-thua-vu-trang-quan-ly/00_OVERVIEW.md) | Duyệt thửa/vụ, mã chuẩn, GPS, trang quản lý | 2, 3, 12, 13, 15, 16 | Quan trọng | Xong |
| [P5](./phase-5-vong-doi-vu-mua-thong-bao/00_OVERVIEW.md) | Vòng đời vụ mùa và thông báo cho farmer | 4, 10, 13, 15 | Quan trọng | Xong (việc chưa làm ghi ở `00_OVERVIEW.md` mục 11) |
| [P6](./phase-6-ai-kiem-tra-chan-doan-danh-gia/00_OVERVIEW.md) | AI: kiểm tra ảnh, chẩn đoán | 5, 11 | Quan trọng | Xong (việc chưa làm ghi ở `04_TRADEOFFS_RISKS.md` mục 3) |
| [P7](./phase-7-du-lieu-bi-tri-thuc/00_OVERVIEW.md) | Dữ liệu, BI và tri thức | 14, 21 | Quan trọng | Xong (hoãn test thời tiết T7-04, T7-05; việc chưa làm và chưa chốt ghi ở `00_OVERVIEW.md` mục 11) |
| [P8](./phase-8-test-demo-ho-so-nop-bai/00_OVERVIEW.md) | Kiểm thử, dữ liệu demo, hồ sơ nộp bài | 17, 18, 19, 20 | Nghiêm trọng (hạn nộp) | Code, tài liệu và kiểm thử đã đạt; còn hồ sơ nộp bài (việc chưa làm ghi ở `00_OVERVIEW.md` mục 11) |
| [P10](./phase-10-de-sau/00_OVERVIEW.md) | Để sau (backlog) | 24, 15 (phần hoàn thiện) | Theo dõi | Backlog |
