# Phase {N}: {Tên phase}

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft / Đang làm / Xong |
| Mức độ | Nghiêm trọng / Quan trọng / Theo dõi |
| Nhóm ưu tiên | A / B / C (theo kế hoạch phase của đợt làm) |
| Module chính | `dx-model` / `dx-core` / `dx-engine` / `dx-monitor` / frontend / deploy / dùng chung |
| Người phụ trách | {tên} |
| Phụ thuộc | {Phase nào phải xong trước, hoặc "không"} |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục {X} của `docs/dynamic-core/BACKLOG.md`, mục {Y} của đề thi, hoặc quyết định {Dz} | {tên mục} | {mức} | Toàn bộ / Một phần ({phần nào}) |

_Gợi ý: ghi thêm đề mục nào của đề thi và hạng mục rubric nào phase này đáp ứng._

## 2. Lý do

- Vấn đề đang gặp: {1-2 câu}.
- Vì sao làm ở phase này: {rủi ro nếu hoãn, phụ thuộc của phase sau}.

## 3. Tổng quan sửa đổi

{3 đến 7 dòng: phase làm gì, theo thứ tự nào.}

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-model` (định dạng tệp mô hình) | Có / Không | |
| `dx-core` (thư viện dùng chung) | Có / Không | |
| `dx-engine` (API chung, quy trình, áp dụng mô hình) | Có / Không | |
| `dx-monitor` (giám sát) | Có / Không | |
| Frontend (web) | Có / Không | |
| AI (`ai/`) | Có / Không | |
| Data / Migration | Có / Không | |
| Infra / Deploy / CI | Có / Không | |
| Docs | Có / Không | |

## 4. Phạm vi

- Trong phạm vi: {danh sách}.
- Ngoài phạm vi (không làm ở phase này): {danh sách, ghi phase nào làm}.
- Giả định: {điều kiện đã coi là đúng}.
- Ranh giới lõi và mô hình: {thuộc tính nào của tệp mô hình thêm hoặc đổi ở `dx-model`, phần nào của API chung hay quy trình đổi ở `dx-engine`; `dx-core` không phụ thuộc `dx-engine`; mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực}.

## 5. Ưu điểm và hạn chế

- Ưu điểm: {…}.
- Hạn chế: {…}.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (nếu nhỏ thì ghi thẳng ở đây).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: {kịch bản ngắn, ai làm gì, thấy gì}.
- Sản phẩm bàn giao: {code, tài liệu, sơ đồ, dữ liệu mẫu}.
- Tiêu chí đo được: {số hoặc điều kiện đúng/sai, ví dụ "tài khoản vai X gọi API Y bằng client Z bị 403"}.
- Với mỗi tệp mô hình mà phase đụng tới: áp dụng được vào hệ thống rỗng, áp dụng lại cùng tệp thì không làm gì, và `python3 deploy/dev/check_conformance.py <tên mô hình>` đạt mà không phải sửa mã lõi.
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
- [ ] `ARCHITECTURE.md` (mục 2 về module, mục 3 về áp dụng mô hình) và mục "Thêm một nghiệp vụ mới" ở `CONTRIBUTING.md` nếu đổi tệp mô hình hay API chung.
- [ ] `COPYRIGHT.txt` và `sbom/` nếu đổi dependency, `deploy/.env.example` nếu đổi cấu hình, sơ đồ ở `ARCHITECTURE.md` nếu đổi kiến trúc.
- [ ] `docs/dynamic-core/BACKLOG.md`: xóa việc đã xong, thêm việc phát sinh.
