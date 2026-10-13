# Mẫu tài liệu cho một đợt làm

Sao chép thư mục này thành `docs/phase-{N}-{slug}/` khi bắt đầu một đợt làm cần kế hoạch riêng; xong đợt thì gộp việc còn lại vào [`../BACKLOG.md`](../BACKLOG.md) và xóa thư mục phase.

| File | Vai trò | Bắt buộc |
|---|---|---|
| `00_OVERVIEW.md` | Vì sao làm, làm gì, phạm vi, kết quả đầu ra (Definition of Done) | Có |
| `01_PLAN.md` | Kế hoạch theo work package: hành vi đổi, phạm vi ảnh hưởng, hợp đồng API và schema | Có |
| `02_CHECKLIST.md` | Việc chi tiết theo lớp (BE, FE, Mobile, AI, Infra, Docs) để tick | Có |
| `03_TEST_PLAN.md` | Kế hoạch kiểm thử và tiêu chí đạt | Có |
| `04_TRADEOFFS_RISKS.md` | Trade-off, rủi ro, phương án dự phòng | Chỉ khi có quyết định lớn, khó đảo ngược |

## Quy ước

- Mã work package `WP{N}-{xx}`, dòng checklist `WP{N}-{xx}-{lớp}-{nn}` (lớp: `DOC`, `BE`, `FE`, `MB`, `AI`, `DATA`, `INFRA`, `TEST`), test case `T{N}-{xx}`.
- Phase xong khi mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đạt và mục "Đóng phase" của `02_CHECKLIST.md` đã tick.
- Chỉ ghi trạng thái cuối trong `README.md` và `CHANGELOG.md`; không ghi nhãn phase vào code, `@Operation` hay comment.
- Viết ngắn, mỗi câu một dòng; hỏi người dùng chốt các quyết định ở mục "Câu hỏi mở" trước khi code.
