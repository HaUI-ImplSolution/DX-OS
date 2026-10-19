# Trade-off và rủi ro: Phase 2

## 1. Trade-off

| # | Quyết định | Phương án | Chọn đề xuất | Được | Mất |
|---|---|---|---|---|---|
| TO1 | Dạng evidence trên UI | Danh sách chunk hiện có / section hóa corpus | Danh sách chunk | Không đổi ingestion/ranking | Chưa có nhóm triệu chứng/phòng ngừa riêng |
| TO2 | Cách lưu evidence | JSONB snapshot / bảng con | JSONB snapshot | Ít thay đổi, giữ đúng evidence tại thời điểm diagnosis | Không thuận tiện để query BI |
| TO3 | Text cũ | Xóa ngay / deprecate | Deprecate | Không mất dữ liệu cũ/manual | Field cũ còn tồn tại tạm thời |
| TO4 | Client | Chỉ web / web và mobile | Chỉ web | Đúng phạm vi nhánh hiện tại | Mobile chưa có evidence có cấu trúc |

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dự phòng |
|---|---|---|---|---|---|
| R1 | Evidence bị hiểu nhầm là chỉ định điều trị | Vừa | Cao | Nhãn “tham khảo”, tách card, không đọc field điều trị từ evidence | Ẩn knowledge card nhưng giữ treatment card |
| R2 | Evidence/source quá lớn hoặc sai schema | Thấp | Vừa | Giới hạn payload và validate trước khi lưu | Trả evidence rỗng, không làm hỏng rule path |
| R3 | Backend/AI lệch contract khi triển khai | Vừa | Cao | Thay đổi additive, Backend chấp nhận summary nullable | Giữ AI response cũ trong cửa sổ chuyển tiếp |
| R4 | Bản ghi cũ không có evidence | Cao | Thấp | Giữ dữ liệu cũ, không backfill giả | Web chỉ hiện treatment card như hiện tại |
| R5 | Source ID không resolve được | Thấp | Thấp | Giữ và hiển thị source ID thay vì bỏ evidence | Ghi log để sửa catalogue riêng |
| R6 | Confidence bị hiểu là accuracy hoặc similarity bị hiểu là xác suất mắc bệnh | Vừa | Cao | Dùng hai nhãn riêng theo format và đặt ở hai khối khác nhau | Ẩn similarity nếu UI không thể giải thích rõ |
| R7 | Parse `TreatmentRule.notes` làm “phương pháp” sai hoặc lộ metadata nội bộ | Vừa | Cao | Chỉ hiển thị field cấu trúc đã xác thực; Phase 2 ẩn dòng nếu chưa có | Mở follow-up chuẩn hóa application method/water volume |

## 3. Nợ kỹ thuật chấp nhận

- Evidence chưa được phân loại theo triệu chứng, lây lan hoặc phòng ngừa.
- Mobile tiếp tục hành vi hiện tại và chưa nhận knowledge card mới.
- `recommendationText` chưa bị xóa vì còn dữ liệu cũ và diagnosis thủ công.
- Chưa chuẩn hóa `TreatmentRule.notes`, water volume hoặc application method.
- Format đã chừa vị trí cho phương pháp áp dụng, nhưng Phase 2 chưa hiển thị cho đến khi có field cấu trúc được xác thực.

## 4. Điều kiện dừng và rollback

- Dừng nếu evidence có thể ghi đè thuốc/liều/PHI, rule validation bị thay đổi hoặc FARMER thấy dữ liệu PENDING trái quyền hiện tại.
- Rollback web bằng cách ẩn knowledge card; Backend/DB có thể giữ column evidence mà không ảnh hưởng response cũ.
- Không drop column hoặc xóa snapshot evidence trong rollback khẩn cấp.

## 5. Tác động ngoài phase

- Section hóa evidence và thay ingestion chỉ được mở thành phase riêng nếu danh sách chunk không đáp ứng UX.
- Mobile chỉ được bổ sung sau khi contract web/Backend ổn định.
- Việc đánh giá ranking/corpus RAG production-grade thuộc backlog AI, không gộp vào phase này.
