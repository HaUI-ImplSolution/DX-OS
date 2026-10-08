# Phase {N}: {Tên phase}

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft / Đang làm / Xong |
| Mức độ | Nghiêm trọng / Quan trọng / Theo dõi |
| Nhóm ưu tiên | A / B / C (theo mục 23 của `PROJECT_IMPROVEMENTS.md`) |
| Ước lượng | {n ngày} |
| Người phụ trách | {tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | {Phase nào phải xong trước, hoặc "không"} |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục {X} | {tên mục} | {mức} | Toàn bộ / Một phần ({phần nào}) |

_Gợi ý: ghi thêm đề mục nào của đề thi HaUI và hạng mục rubric nào phase này đáp ứng._

## 2. Lý do

- Vấn đề đang gặp: {1-2 câu}.
- Vì sao làm ở phase này: {rủi ro nếu hoãn, phụ thuộc của phase sau}.

## 3. Tổng quan sửa đổi

{3 đến 7 dòng: phase làm gì, theo thứ tự nào.}

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có / Không | |
| Frontend (web) | Có / Không | |
| Mobile | Có / Không | |
| AI | Có / Không | |
| Data / Seed / Migration | Có / Không | |
| Infra / Deploy / CI | Có / Không | |
| Docs / PoF | Có / Không | |

## 4. Phạm vi

- Trong phạm vi: {danh sách}.
- Ngoài phạm vi (không làm ở phase này): {danh sách, ghi phase nào làm}.
- Giả định: {điều kiện đã coi là đúng}.

## 5. Ưu điểm và hạn chế

- Ưu điểm: {…}.
- Hạn chế: {…}.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (nếu nhỏ thì ghi thẳng ở đây).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: {kịch bản ngắn, ai làm gì, thấy gì}.
- Sản phẩm bàn giao: {code, tài liệu, sơ đồ, dữ liệu mẫu}.
- Tiêu chí đo được: {số hoặc điều kiện đúng/sai, ví dụ "farmer gọi API bằng token mobile vào web bị 403"}.
- Bằng chứng: {link log, ảnh, video, test report}.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: {…}.
- Phase sau cần từ phase này: {…}.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: {tiêu chí, số điểm}.
- Dùng làm bằng chứng cho: báo cáo / slide / video / câu trả lời hội đồng số {n}.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | {…} | A / B | {ngày} | _(chưa chốt)_ |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Swagger (springdoc) nếu đổi API.
- [ ] Sơ đồ kiến trúc, `LICENSES.md`, `.env.example` nếu có đổi.
