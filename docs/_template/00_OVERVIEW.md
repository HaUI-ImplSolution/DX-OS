# Phase {N}: {Tên phase}

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft / Đang làm / Xong |
| Mức độ | Nghiêm trọng / Quan trọng / Theo dõi |
| Nhóm ưu tiên | A / B / C (theo kế hoạch phase của đợt làm) |
| Ứng dụng và module chính | core / app-farm / app-lab / host / dùng chung |
| Ước lượng | {n ngày} |
| Người phụ trách | {tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | {Phase nào phải xong trước, hoặc "không"} |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục {X} của `docs/BACKLOG.md`, mục {Y} của đề thi, hoặc quyết định {Dz} | {tên mục} | {mức} | Toàn bộ / Một phần ({phần nào}) |

_Gợi ý: ghi thêm đề mục nào của đề thi và hạng mục rubric nào phase này đáp ứng._

## 2. Lý do

- Vấn đề đang gặp: {1-2 câu}.
- Vì sao làm ở phase này: {rủi ro nếu hoãn, phụ thuộc của phase sau}.

## 3. Tổng quan sửa đổi

{3 đến 7 dòng: phase làm gì, theo thứ tự nào.}

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Có / Không | |
| `dx-core` (cài đặt dùng chung) | Có / Không | |
| `app-farm` | Có / Không | |
| `app-lab` | Có / Không | |
| `dx-host` (chạy, chọn app) | Có / Không | |
| Frontend (web) | Có / Không | |
| Mobile | Có / Không | |
| AI | Có / Không | |
| Data / Seed / Migration | Có / Không | |
| Infra / Deploy / CI | Có / Không | |
| Docs | Có / Không | |

## 4. Phạm vi

- Trong phạm vi: {danh sách}.
- Ngoài phạm vi (không làm ở phase này): {danh sách, ghi phase nào làm}.
- Giả định: {điều kiện đã coi là đúng}.
- Ranh giới Core/App: {interface nào thêm hoặc đổi ở `dx-core-api`, app nào cài; phần dùng chung đặt ở `dx-core`, phần riêng ở app; core không phụ thuộc app}.

## 5. Ưu điểm và hạn chế

- Ưu điểm: {…}.
- Hạn chế: {…}.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (nếu nhỏ thì ghi thẳng ở đây).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: {kịch bản ngắn, ai làm gì, thấy gì}.
- Sản phẩm bàn giao: {code, tài liệu, sơ đồ, dữ liệu mẫu}.
- Tiêu chí đo được: {số hoặc điều kiện đúng/sai, ví dụ "tài khoản vai X gọi API Y bằng client Z bị 403"}.
- Với mỗi app đang hỗ trợ: khởi động được sau khi đổi `DXOS_APP`, và core không phải sửa để nhận app đó.
- Bằng chứng: {link log, ảnh, video, test report}.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: {…}.
- Phase sau cần từ phase này: {…}.

## 8. Ảnh hưởng đến thi

- Điểm rubric liên quan: {tiêu chí, số điểm}.
- Dùng làm bằng chứng cho: báo cáo / slide / video / câu hỏi định hướng số {n} ở mục 13 của đề.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | {…} | A / B | {ngày} | _(chưa chốt)_ |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger (springdoc) nếu đổi API.
- [ ] `ARCHITECTURE.md` (mục 3, phần ứng dụng khai báo) và hướng dẫn thêm ứng dụng ở `CONTRIBUTING.md` nếu đổi interface.
- [ ] Sơ đồ ở `ARCHITECTURE.md`, `COPYRIGHT.txt`, `.env.example` (kể cả `DXOS_APP`) nếu có đổi.
- [ ] `docs/BACKLOG.md`: xóa việc đã xong, thêm việc phát sinh.
