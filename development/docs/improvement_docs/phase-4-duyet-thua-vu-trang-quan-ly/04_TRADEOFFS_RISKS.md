# Trade-off và rủi ro: Phase 4

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | GPS bắt buộc khi tạo thửa | Bắt buộc cứng / Tuỳ chọn, cảnh báo nếu thiếu | Bắt buộc cứng | Đáp ứng đúng yêu cầu định vị cho Superset; dữ liệu mới luôn có vị trí | Farmer ở vùng sóng yếu có thể không lấy được GPS đúng lúc tạo thửa | Vừa — đổi lại thành tuỳ chọn là một thay đổi validation nhỏ, không mất dữ liệu đã có |
| TO2 | `Parcel.approvalStatus` song song với `WorkflowCase.status` | Giống TO2 của P3 (giữ song song) | Giữ song song | Nhất quán với cách đã làm ở P3 cho `Diagnosis`, không phải quyết định lại pattern | Thêm một nguồn sự thật cần đồng bộ | Thấp — theo đúng pattern đã có |
| TO3 | Mốc tháng loại vụ (Q1) chưa có xác nhận chuyên môn nhưng vẫn cần giá trị để code | Hard-code giá trị đề xuất tạm, đánh dấu rõ "chờ xác nhận" / Hoãn tính năng tự gợi ý, chỉ cho chọn tay | Hard-code tạm, đánh dấu rõ trong code và docs | Mobile vẫn demo được tính năng gợi ý ngay | Nếu giá trị tạm sai, farmer thấy gợi ý loại vụ không đúng thực tế cho tới khi kỹ sư xác nhận lại | Thấp — chỉ là hằng số, sửa không cần migration |

Kết luận: TO1-TO3 áp dụng ngay; TO3 phải có việc theo dõi riêng (xem rủi ro R1 dưới) vì giá trị chưa được xác nhận chuyên môn.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Mốc tháng loại vụ (Q1) dùng giá trị đề xuất tạm, không khớp thực tế canh tác ở vùng demo | Vừa | Vừa (gợi ý sai, farmer phải tự sửa) | Farmer luôn xác nhận lại loại vụ gợi ý trước khi lưu (không tự động chốt); theo dõi riêng để kỹ sư nông nghiệp xác nhận sớm | Farmer sửa lại loại vụ gợi ý thường xuyên trong lúc test/demo | Đổi hằng số mốc tháng ngay khi có xác nhận, không cần migration |
| R2 | GPS bắt buộc (TO1) chặn farmer ở vùng sóng yếu không tạo được thửa đúng lúc ở ruộng | Vừa | Vừa | Cho phép lưu tạm ở local (hàng đợi offline, WP4-02) khi chưa lấy được GPS, farmer thử lại khi có tín hiệu tốt hơn, không bắt buộc gửi ngay | Nhiều request tạo thửa bị kẹt lâu trong hàng đợi offline vì thiếu GPS | Cho phép admin nhập GPS hộ farmer qua trang quản lý nếu farmer báo không lấy được (ngoài phạm vi code tự động, xử lý thủ công) |
| R3 | Khối lượng frontend lớn (web + mobile cùng lúc, WP4-02 + WP4-05) làm trễ tiến độ phase so với ước lượng | Vừa | Vừa | Làm trang quản lý tối thiểu trước (danh sách + duyệt, bỏ qua trang trang trí/thống kê đẹp), nâng cấp sau nếu còn thời gian | Một trong hai WP (WP4-02 hoặc WP4-05) chưa xong khi các WP khác đã xong | Ưu tiên WP4-03 (duyệt) và WP4-05 (trang quản lý tối thiểu) hoàn thành trước, vì đó là phần đề chấm trực tiếp (phần D, phần Process) |
| R4 | Giới hạn 3 yêu cầu chờ duyệt (Q3) chặn nhầm hộ hợp pháp có nhiều thửa thật đang chờ duyệt cùng lúc (ví dụ hộ mới onboard nhiều thửa một lần) | Thấp | Thấp | Thông báo rõ lý do bị chặn (đã đủ 3 yêu cầu chờ), hướng dẫn chờ admin duyệt xong rồi gửi tiếp | Farmer/admin phản hồi bị chặn khi không có ý định spam | Admin có thể duyệt nhanh các yêu cầu cũ để farmer gửi tiếp, hoặc nâng ngưỡng Q3 (chỉ đổi hằng số) |

## 3. Nợ kỹ thuật chấp nhận

- Mốc tháng loại vụ (Q1) là giá trị tạm, cần kỹ sư nông nghiệp xác nhận trước khi dùng cho dữ liệu thật ngoài demo.
- Vụ mùa cũ (trước phase này) không có `code`/`seasonType`, không backfill — chấp nhận "thiếu mã" cho dữ liệu lịch sử.
- Trang quản lý ở phase này là bản tối thiểu (danh sách + duyệt + chi tiết), chưa có thống kê nhanh/dashboard riêng trong app (để Superset đảm nhiệm theo P7).

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: R3 xảy ra rõ ràng (một trong WP4-02/WP4-05 trễ quá nhiều so với các WP khác) — tách phần "nâng cấp thêm" (icon đẹp, UX mượt) ra khỏi Definition of Done, chỉ giữ phần lõi (tạo được, duyệt được, xem được).
- Rollback: các WP trong phase này độc lập về code (trừ phụ thuộc đã ghi ở `01_PLAN.md`), revert riêng từng WP không ảnh hưởng WP khác; migration thêm cột đều nullable/có default, rollback không mất dữ liệu nghiệp vụ.

## 5. Tác động ngoài phase

- Lên các phase sau: P5 (vòng đời vụ mùa) cần thửa/vụ đã `APPROVED` từ phase này; P7 (BI) hiển thị đúng mã thửa/vụ chuẩn lên Superset.
- Lên demo, tài liệu nộp bài, thời hạn: đây là phần demo chính (chuỗi người dùng farmer → engineer/admin → kết quả), cần tập kỹ ở P8 vì có nhiều bước thao tác nối tiếp (tạo → duyệt → tạo vụ → báo cáo).
