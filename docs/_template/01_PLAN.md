# Plan thực hiện: Phase {N}

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

{WP1-01 → WP1-02 → …, ghi rõ WP nào làm song song được.}

## WP{N}-01: {Tên work package}

- Mục tham chiếu: {mục X của `docs/BACKLOG.md`, mục Y của đề thi, hoặc quyết định Dz}.
- Mục tiêu: {1-2 câu}.
- Ước lượng: S / M / L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| {vd: một vai tạo yêu cầu} | {…} | {…} |

**Hướng làm**

- {API, schema, UI, luồng AI, cấu hình…}
- Core/App: {phần dùng chung vào `dx-core`, interface vào `dx-core-api`, phần riêng vào app; chỉ tách interface khi hai app thật sự khác nhau}.

**Phạm vi ảnh hưởng**

- Module: {`dx-core-api` / `dx-core` / `app-farm` / `app-lab` / `dx-host`}.
- File chính: {…}.
- Endpoint, bảng DB, biến môi trường: {…}.
- Role và client bị ảnh hưởng: {vai của app nào, web / mobile}.
- Tương thích ngược và dữ liệu cũ: {có cần migration không; app còn lại có phải sửa không}.

**Kết quả đầu ra**

- {điều kiện đúng/sai để coi WP này xong}.

**Phụ thuộc, lưu ý**: {WP khác, quyết định ở `00` mục 9, rủi ro ở `04`}.

## WP{N}-02: {…}

_(copy khối WP ở trên)_

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST /api/v1/...` | {…} | Có / Không |
| Interface | `{Tên}` ở `dx-core-api` | {thêm hoặc đổi} | Có / Không (app nào phải sửa) |
| Bảng DB | `{bảng}` | {cột / ràng buộc mới} | Có / Không |
| Biến môi trường | `{TÊN}` | {mới, mặc định} | Có / Không |

## Dữ liệu, migration và rollback

- Migration: {script nào, thuộc `db/migration/core` hay `db/migration/{app}`, chạy khi nào}.
- Dữ liệu cũ: {xử lý ra sao}.
- Rollback: {quay lại bằng cách nào, mất gì}.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | {…} | {…} |
