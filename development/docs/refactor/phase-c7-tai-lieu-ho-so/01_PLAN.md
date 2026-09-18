# Plan thực hiện: Phase C7

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WPC7-01 → WPC7-02 → WPC7-03; WPC7-04 làm song song được.

## WPC7-01: Kiến trúc và link

- Mục tham chiếu: đề thi HaUI mục 3, 4.1, 13.
- Mục tiêu: tài liệu kiến trúc mô tả đúng host + plugin và ranh giới biên dịch.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| `ARCHITECTURE_OPEN_CORE.md` mục 2 | Ranh giới là quy ước package, một module | Sơ đồ module, đồ thị phụ thuộc, danh sách interface, luồng request cho Farm và Lab |
| Sơ đồ 3 tầng | Core, H-P-D-I, DX-Farm | Core, H-P-D-I, hai app |
| Link tới tài liệu đã chuyển | Trỏ đường dẫn cũ | Trỏ đường dẫn mới |

**Hướng làm**

- Dùng sơ đồ Mermaid; tham chiếu `SPI_CATALOG.md` thay vì chép lại.
- Core/App: mô tả core trung tính, app là phần cắm vào.

**Phạm vi ảnh hưởng**

- File chính: `submission/ARCHITECTURE_OPEN_CORE.md`, `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `submission/COUNCIL_QA.md`, `submission/DEMO_SCRIPT.md`, `improvement_docs/README.md`, `improvement_docs/PHASE_PLAN.md`, phase-3 và phase-4 của `improvement_docs`.
- Endpoint, bảng DB, biến môi trường: không.

**Kết quả đầu ra**

- Không còn link gãy; sơ đồ khớp sản phẩm.

**Phụ thuộc, lưu ý**: cần C6 để chụp sơ đồ luồng thật.

## WPC7-02: README, CONTRIBUTING và hướng dẫn thêm app

- Mục tham chiếu: đề thi HaUI mục 5, 13 (đóng góp module mới).
- Mục tiêu: người ngoài dựng và thêm app được theo tài liệu.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| README | Mô tả DX-Farm | Mô tả DX-OS Open-Core, hai app, `DXOS_APP`, tài khoản demo từng app |
| Thêm tính năng | Mục "Thêm một quy trình hoặc ứng dụng mới" cho Farm | Hướng dẫn thêm app: module, `AppModule`, `RoleProvider`, migration, interface cần cài, module web |
| Lệnh build và chạy | Một module | Toàn bộ module, chọn app |

**Hướng làm**

- Hướng dẫn thêm app lấy `app-lab` làm ví dụ mẫu.
- Core/App: README mô tả core trước, app sau.

**Phạm vi ảnh hưởng**

- File chính: `README.md`, `CONTRIBUTING.md`, `app-lab/README.md`, `app-farm/README.md`, `deploy/demo/README.md`.

**Kết quả đầu ra**

- Làm theo README dựng được từng app; làm theo hướng dẫn thêm được app mẫu rỗng.

**Phụ thuộc, lưu ý**: Q2.

## WPC7-03: Tài liệu nộp bài

- Mục tham chiếu: đề thi HaUI mục 10, 13.
- Mục tiêu: hồ sơ khớp sản phẩm hai app.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| `COUNCIL_QA.md` | Trả lời theo Farm | Trả lời theo core + hai app (ranh giới, interface, chuyển app, mở rộng) |
| `DEMO_SCRIPT.md` | Kịch bản Farm | Kịch bản hai app trong 10 phút: Lab theo ba vai (sinh viên, kỹ thuật viên, quản trị) và Farm, kèm thao tác chuyển app |
| `REPORT_OUTLINE.md` | Dàn ý theo Farm | Dàn ý theo core, H-P-D-I, hai app |
| `LICENSES.md` | Dependency Farm | Thêm nguồn dữ liệu và tri thức của Lab |
| `REMAINING_WORK.md` | Việc sau P0 đến P8 | Cập nhật sau refactor |

**Hướng làm**

- Trả lời câu hỏi mục 13 dẫn tới sơ đồ, mã và tài liệu cụ thể.
- Core/App: nhấn mạnh Core không đổi khi thay app.

**Phạm vi ảnh hưởng**

- File chính: `submission/*`, `LICENSES.md`, `CHANGELOG.md`, `improvement_docs/REMAINING_WORK.md`.

**Kết quả đầu ra**

- Mỗi câu hỏi mục 13 có câu trả lời và tham chiếu.

**Phụ thuộc, lưu ý**: cần C6.

## WPC7-04: Rà soát trước khi nộp

- Mục tham chiếu: đề thi HaUI mục 5, 12.
- Mục tiêu: không còn lỗi PoF do refactor gây ra.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Secret | Quét trước refactor | Quét lại sau refactor |
| SBOM | Cho `backend`, `frontend`, `mobile`, `ai` | Cập nhật cho các module mới |
| Tên cũ | Còn rải rác | Rà `dxfarm`, `DX-Farm` ngoài chỗ cố ý giữ |

**Hướng làm**

- Quét secret, cập nhật SBOM, tìm tên cũ.
- Việc tag release, bật issue, public repository theo `REMAINING_WORK.md`, không làm ở phase này.

**Phạm vi ảnh hưởng**

- File chính: `sbom/`, `.gitleaks.toml`, tài liệu có tên cũ.

**Kết quả đầu ra**

- Quét secret sạch; SBOM khớp module hiện có.

**Phụ thuộc, lưu ý**: cần C6.

## Thay đổi hợp đồng (API, schema, cấu hình)

Không có.

## Dữ liệu, migration và rollback

- Migration: không.
- Dữ liệu cũ: không.
- Rollback: quay lại commit tài liệu trước.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Dùng `app-lab` làm ví dụ mẫu cho hướng dẫn thêm app | Có sẵn, chạy được, đã kiểm |
| D2 | Giấy phép AGPL cho `ai/` để sau (RD7) | Người dùng chốt hoãn |
