# Kế hoạch kiểm thử: Phase 0

Phase này không có code nên không áp dụng unit/integration/E2E.
"Kiểm thử" ở đây là kiểm tra tài liệu quyết định đã đủ và nhất quán để các phase sau dùng được ngay, không phải chạy test tự động.

## 1. Chiến lược

- Mức kiểm thử áp dụng: rà soát tài liệu (document review) + đối chiếu chéo giữa `00_OVERVIEW.md`, `01_PLAN.md` và `PHASE_PLAN.md`.
- Môi trường: không cần dựng app.
- Người rà soát: người phụ trách phase 0, lý tưởng có thêm một người khác đọc chéo trước khi coi là "chốt".

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T0-01 | DP0-01 | Đầy đủ | Đọc `01_PLAN.md` mục "Quyết định thiết kế đã chốt", kiểm D1 có giá trị và lý do | D1 khác `_(chưa chốt)_`, có 1-2 câu lý do | | |
| T0-02 | DP0-02 | Đầy đủ | Kiểm D2, D3, D4 có giá trị | Cả 3 dòng có giá trị, không bỏ trống | | |
| T0-03 | DP0-03 | Đầy đủ | Kiểm D5-D9 có giá trị | Cả 5 dòng có giá trị | | |
| T0-04 | DP0-04 | Đầy đủ | Kiểm D10 và quyết định ngày hạn | D10 có giá trị; mục "Ngày hạn" ghi rõ "không áp dụng mốc 14 ngày", không còn `{điền ngày}` | | |
| T0-05 | DP0-01 | Nhất quán | Nếu D1 = A hoặc C: kiểm `PHASE_PLAN.md` đã bỏ/sửa P9 tương ứng, không còn nhắc P9 như đang "giữ" | `PHASE_PLAN.md` khớp với D1 | | |
| T0-06 | DP0-02 | Nhất quán | Nếu D2/D3/D4 đổi khuyến nghị mặc định (tự làm / Traefik / cắt MinIO): kiểm `PHASE_PLAN.md` mục 3 "Lộ trình rút gọn" đã cập nhật theo | Lộ trình rút gọn khớp với lựa chọn thật | | |
| T0-07 | DP0-04 | Nhất quán | Kiểm `PHASE_PLAN.md` mục 3 đã ghi rõ lộ trình rút gọn là phương án dự phòng, không áp dụng | `PHASE_PLAN.md` mục 3 khớp với quyết định "không ràng buộc 14 ngày" | | |
| T0-08 | Toàn phase | Khả dụng | Người phụ trách P1 (hoặc P2, P3...) đọc `01_PLAN.md` của phase 0, xác nhận đủ thông tin để bắt đầu viết `00_OVERVIEW.md`/`01_PLAN.md` của phase mình mà không cần hỏi lại | Xác nhận "đủ", hoặc liệt kê câu hỏi còn thiếu | | |

## 3. Hồi quy (không được hỏng)

Không áp dụng — phase 0 không đụng code hay dữ liệu đang chạy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T0-01 đến T0-04 đạt (cả 10 quyết định có giá trị).
- T0-05 đến T0-07 đạt nếu điều kiện tương ứng xảy ra (đổi khuyến nghị mặc định).
- T0-08 đạt: ít nhất một người ngoài người viết phase 0 xác nhận tài liệu đủ dùng.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: bản thân `01_PLAN.md` đã điền đủ cột "Kết luận"/"Lý do ngắn" chính là bằng chứng; nếu có họp/trao đổi với giảng viên thì lưu tóm tắt hoặc link biên bản trong `00_OVERVIEW.md` mục 6 "Bằng chứng".
- Lỗi phát hiện (quyết định mâu thuẫn nhau, thiếu căn cứ): ghi lại ngay trong `04_TRADEOFFS_RISKS.md` mục rủi ro, không cần tạo issue GitHub cho phase tài liệu này.
