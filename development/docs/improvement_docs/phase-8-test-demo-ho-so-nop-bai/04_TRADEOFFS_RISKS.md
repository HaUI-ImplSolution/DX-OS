# Trade-off và rủi ro: Phase 8

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Treatment rule chưa duyệt kịp (Q2) | Chờ chuyên gia duyệt, trì hoãn demo trọn luồng khóa PHI thật / Demo trung thực nhánh `review_required`, ghi rõ giới hạn | Demo trung thực nhánh `review_required` nếu Q2 = không | Không dựng số liệu giả, giữ đúng nguyên tắc "không tự bịa" của dự án | Mất điểm nhấn "khóa PHI hoạt động trọn luồng" trong demo nếu hội đồng mong đợi thấy đủ | Thấp — khi có rule được duyệt thật (bất kỳ lúc nào sau đó), chuyển demo sang luồng đầy đủ không cần đổi code |
| TO2 | Thời điểm chuyển repo public | Ngay khi rà xong lịch sử commit / Sát ngày nộp | Ngay khi rà xong (đã chốt Q1) | Giảm rủi ro quên/hết thời gian | Secret cũ (nếu còn sót) có thời gian lộ công khai dài hơn trước khi phát hiện | Thấp — repo có thể chuyển lại Private nếu phát hiện vấn đề sau khi public, GitHub hỗ trợ việc này |

Kết luận: TO1 phụ thuộc kết quả Q2 thật (không tự quyết được); TO2 áp dụng ngay theo Q1 đã chốt.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Dồn hết việc P8 vào những ngày cuối vì các phase trước trễ tiến độ | Cao (rủi ro đã tự nhận định trong `PHASE_PLAN.md`) | Cao (không đủ thời gian rehearsal, hồ sơ sơ sài) | Cập nhật tài liệu/test theo từng phase (D6 đã áp dụng từ P1), không để P8 viết test thay cho các phase trước; chừa tối thiểu 2 ngày cuối cho rehearsal | Tới gần hạn nộp mà `02_CHECKLIST.md` các mục WP8-03/04/06 còn trống nhiều | Cắt giảm phần "đẹp" (slide trau chuốt, video dựng kỹ) để giữ đúng phần "đúng" (nội dung chính xác, demo chạy được) |
| R2 | Treatment rule mẫu không được duyệt kịp hạn (liên quan TO1), ảnh hưởng cả WP8-01 (E2E) và WP8-02 (dữ liệu demo) | Vừa | Cao | Theo dõi tiến độ duyệt rule tách biệt, báo sớm nếu có nguy cơ trễ để kịp chuyển hướng sang demo nhánh `review_required` | Gần hết thời gian phase mà rule vẫn `IN_REVIEW` | E2E và kịch bản demo đều thiết kế chạy được với cả 2 nhánh (rule có hoặc chưa có), không viết cứng giả định "chắc chắn có rule" |
| R3 | Chuyển repo public (TO2) lộ ra vấn đề license/dependency chưa rà kỹ (ví dụ thành phần AGPL-3.0 chưa ghi rõ nghĩa vụ công bố mã nguồn) | Thấp | Cao (ảnh hưởng điểm PoF license, mục 19) | Rà `LICENSES.md` kỹ trước khi public (WP8-03), đặc biệt 2 thành phần AGPL-3.0 (Ultralytics, PyMuPDF) đã được `PROJECT_IMPROVEMENTS.md` mục 19 lưu ý | Phát hiện thiếu ghi chú nghĩa vụ AGPL sau khi đã public | Bổ sung ngay phần ghi chú thiếu trong `LICENSES.md`, không cần gỡ public (bổ sung tài liệu không phải thay đổi mã nguồn) |

## 3. Nợ kỹ thuật chấp nhận

- Nếu Q2 = không (chưa có rule duyệt thật), chấp nhận giới hạn "chưa demo trọn luồng khóa PHI với dữ liệu thật" — ghi rõ trong báo cáo, không che giấu.
- Portal (WP8-05) chỉ hiện trạng thái up/down cơ bản, không có giám sát/alerting đầy đủ (Prometheus/Grafana vẫn là "không bắt buộc" theo mục 17).

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: R1 xảy ra rõ ràng (còn hơn 1/3 checklist P8 trống khi chỉ còn vài ngày tới hạn) — họp lại để cắt giảm phạm vi (ưu tiên Nhóm A, bỏ phần trang trí), không cố làm hết mọi WP.
- Rollback: tag/release có thể xoá và tạo lại; repo public có thể chuyển lại Private nếu phát hiện vấn đề nghiêm trọng trước hạn chấm.

## 5. Tác động ngoài phase

- Lên các phase sau: không có phase sau, đây là phase chốt cuối.
- Lên demo, tài liệu nộp bài, thời hạn: toàn bộ kết quả phase này chính là hồ sơ nộp bài — không có "tác động ngoài phase" theo nghĩa kỹ thuật, nhưng là điều kiện quyết định có được chấm hay không.
