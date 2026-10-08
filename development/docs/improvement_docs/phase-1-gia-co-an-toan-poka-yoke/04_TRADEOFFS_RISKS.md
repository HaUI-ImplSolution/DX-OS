# Trade-off và rủi ro: Phase 1

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Q1 — ngưỡng ngày hợp lệ của `appliedAt` | 14 ngày / 30 ngày / không giới hạn cứng, chỉ cảnh báo | **30 ngày** | Ít chặn nhầm trường hợp hợp lệ, đủ dư cho sai số khai báo trễ thực tế | Ngưỡng rộng hơn 14 ngày nghĩa là dữ liệu PHI sai có thể tồn tại lâu hơn trước khi bị phát hiện | Thấp — chỉ là một hằng số, đổi được bất kỳ lúc nào không cần migration |
| TO2 | Unique constraint "1 vụ ACTIVE/thửa" | Chặn cứng ở DB (partial unique index) / chỉ chặn ở service | DB + service (cả hai lớp) | An toàn trước race condition, đúng nguyên tắc chặn 2 tầng của dự án | Phải dọn dữ liệu cũ vi phạm trước khi migrate, có thể mất vài vụ demo cũ không hợp lệ | Vừa — bỏ constraint dễ, nhưng dữ liệu đã dọn (xóa/đổi trạng thái) không phục hồi được |
| TO3 | Re-encode ảnh để xóa EXIF | Re-encode mọi ảnh upload / chỉ strip EXIF tag không decode lại | Re-encode (decode + encode lại) | Vừa xác minh ảnh thật (chặn file giả dạng ảnh) vừa xóa EXIF trong một bước | Tốn CPU hơn, có thể đổi nhẹ chất lượng ảnh nén lại (JPEG re-encode) | Thấp — đổi lại cách strip nhẹ hơn không cần migration dữ liệu đã lưu |
| TO4 | Ngừng publish cổng AI (8000) ra host | Ngừng hẳn / giữ nhưng chỉ bind `127.0.0.1` | Ngừng hẳn (bỏ hẳn `AI_HOST_PORT`) | Đóng đúng lỗ hổng mục 9; dọn đường cho gateway ở P3 | Mất tiện lợi debug trực tiếp AI qua Postman từ máy host, phải `docker compose exec` hoặc port-forward tạm | Thấp — compose dễ đổi lại, không ảnh hưởng dữ liệu |

Kết luận: TO1-TO4 đã chốt và áp dụng (xem `00_OVERVIEW.md` mục 9, ngày 05/10/2026).

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Dữ liệu seed/demo hiện có vi phạm ràng buộc mới (nhiều vụ ACTIVE/thửa, `appliedAt` ngoài khoảng) khiến migration fail hoặc seed không chạy được trên clone sạch | Cao | Vừa | Chạy WP1-02-DATA-01 (rà và dọn) trước khi thêm constraint DB, không làm song song | Migration báo lỗi vi phạm unique constraint khi chạy trên dữ liệu seed | Lùi lại: xóa seed cũ, viết seed mới tuân thủ ràng buộc ngay từ đầu (ít rủi ro hơn sửa dữ liệu cũ) |
| R2 | `gitleaks` (hoặc tool quét) phát hiện nhiều false positive (chuỗi giống secret nhưng không phải), gây nhiễu hoặc bị bỏ qua cảnh báo thật | Vừa | Thấp | Review kỹ từng phát hiện lần đầu, ghi allowlist có lý do cụ thể, không allowlist hàng loạt | Số lượng cảnh báo cao bất thường khi chạy lần đầu trên lịch sử commit | Review thủ công toàn bộ danh sách trước khi public repo (P8), không tin tưởng tuyệt đối kết quả tool |
| R3 | `@Version` (optimistic lock) làm một luồng cập nhật hợp lệ hiện có (ví dụ retry tự động ở FE/mobile) bất ngờ nhận 409 dù không có xung đột thật | Thấp | Vừa | Test T1-07 xác nhận đúng kịch bản 2 request đồng thời; kiểm tra FE/mobile hiện có không có cơ chế retry tự động nào gọi lại API ghi mà không tải lại dữ liệu mới | FE/mobile báo lỗi 409 bất thường trong luồng người dùng bình thường (không thao tác đồng thời) | Rà lại logic gọi API ở FE/mobile, thêm xử lý 409 (tải lại rồi thử lại) nếu phát hiện |
| R4 | CI thêm job test thật (bỏ `-DskipTests`) làm lộ ra các test cũ/mới fail do môi trường CI khác máy local (thiếu service phụ thuộc, timezone...) | Vừa | Vừa | Test ở WP1-02 không phụ thuộc DB thật nếu dùng unit test thuần (mock `Clock`); nếu cần DB thì dùng Testcontainers hoặc H2 trong profile test | CI đỏ ngay sau khi bỏ `-DskipTests` dù code đúng | Tạm thời giữ `-DskipTests` cho job build chính, thêm job test riêng có thể fail mà không chặn merge, chuyển bắt buộc sau khi ổn định |

## 2b. Kết quả thực tế của các rủi ro

- R1: seed (`data.sql`) chỉ có danh mục bệnh, không có vụ mùa nên không vi phạm ràng buộc; chỉ DB dev cũ cần `reset.sh`.
- R2: gitleaks báo 4 phát hiện trên lịch sử (mật khẩu demo cũ, placeholder trong tài liệu skill); đã xem từng cái và allowlist có lý do trong `.gitleaks.toml`.
- R3: chưa thấy luồng nào của web hoặc mobile tự retry API ghi.
- R4: test chạy PostgreSQL thật bằng Testcontainers; CI xanh ngay lần đầu.

## 3. Nợ kỹ thuật chấp nhận

- Chưa chuyển sang Flyway (hoặc công cụ migration có phiên bản) ở phase này — để P2 xử lý cùng lúc với migration Household, tránh đổi 2 lần.
- Server giới hạn ảnh ở 10MB và 40 megapixel; chưa có giới hạn chiều rộng/cao riêng — theo dõi ở P6 khi làm precheck ảnh.
- Reverse proxy + HTTPS và backup/restore không làm ở phase này — để P3/P8, phase 1 chỉ xử lý phần bảo mật có thể làm độc lập.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: dọn dữ liệu seed cũ (R1) phát hiện có dữ liệu demo quan trọng không thể xóa mà vẫn vi phạm ràng buộc mới — khi đó cân nhắc nới ràng buộc DB thành chỉ cảnh báo (không chặn cứng) tạm thời.
- Rollback: migration thêm cột/constraint có thể rollback bằng migration ngược; code validation có thể tắt bằng feature toggle tạm nếu chặn nhầm nghiêm trọng trong lúc demo (không khuyến khích, chỉ dùng khi cấp bách).

## 5. Tác động ngoài phase

- Lên các phase sau: P2 kế thừa `Clock` bean và pattern `@Version` cho các entity mới (Household, Parcel); P3 không cần tự làm lại auth AI (đã có ở lớp backend-AI), chỉ thêm lớp gateway bên ngoài.
- Lên demo, tài liệu nộp bài, thời hạn: khóa PHI đúng là điểm nhấn chính của demo nghiệp vụ — phase này phải xong và có bằng chứng trước khi tập demo ở P8.
