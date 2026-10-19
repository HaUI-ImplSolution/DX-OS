# Checklist: Phase 2

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` không áp dụng.
Checklist này được lập trước khi triển khai; chỉ các mục chuẩn bị kế hoạch được đánh dấu hoàn tất.

## WP2-01: Chốt cấu trúc kết quả chẩn đoán

Kết quả đầu ra: nội dung ưu tiên và hành vi với dữ liệu tùy chọn được mô tả rõ.

**Spec và tài liệu**

- [x] WP2-01-DOC-01 Khảo sát `DiagnosisPanel` và stylesheet trên `origin/Khuong/main` để xác định phạm vi UI.
- [x] WP2-01-DOC-02 Ghi hành vi trước/sau, phạm vi và hợp đồng cần giữ nguyên trong plan.
- [-] WP2-01-DOC-03 Không cập nhật Swagger vì endpoint và DTO không đổi.

**Frontend (web)**

- [ ] WP2-01-FE-01 Xác nhận confidence và treatment rule là thông tin chính trong kết quả có cấu trúc.
- [ ] WP2-01-FE-02 Xác nhận điều kiện ẩn/hiện khi thiếu confidence, treatment rule, source hoặc version.

## WP2-02: Triển khai card điều trị và responsive

Kết quả đầu ra: kết quả chẩn đoán có phân cấp rõ trên desktop và màn hình hẹp.

**Frontend (web)**

- [ ] WP2-02-FE-01 Đổi confidence thành phần trăm và thanh `progress` có nhãn truy cập.
- [ ] WP2-02-FE-02 Đổi treatment rule từ bảng sang `section` và description list.
- [ ] WP2-02-FE-03 Tách thuốc, liều lượng và thời gian cách ly thành ba fact card.
- [ ] WP2-02-FE-04 Tách source/version và định dạng danh sách source bằng dấu `·`.
- [ ] WP2-02-FE-05 Bổ sung style riêng cho card, progress và layout một cột ở tối đa 640 px.
- [ ] WP2-02-FE-06 Giữ nguyên điều kiện duyệt, từ chối, yêu cầu ảnh mới và ghi nhận phun.

## WP2-03: Kiểm tra và chốt phạm vi

Kết quả đầu ra: build đạt, giao diện đúng ở các trạng thái bắt buộc và không có thay đổi ngoài phạm vi.

**Test**

- [ ] WP2-03-TEST-01 Chạy `npm run build` trong `frontend`.
- [ ] WP2-03-TEST-02 Chạy `git diff --check` với diff triển khai của nhánh.
- [ ] WP2-03-TEST-03 Kiểm tra thủ công các case T2-01 đến T2-08.
- [ ] WP2-03-TEST-04 Chụp bằng chứng desktop và màn hình hẹp nếu quy trình review yêu cầu.

**Tài liệu**

- [x] WP2-03-DOC-01 Tạo thư mục phase từ cấu trúc `docs/_template` trước khi triển khai.
- [ ] WP2-03-DOC-02 Cập nhật trạng thái và kết quả test thực tế trước khi đóng phase.
- [ ] WP2-03-DOC-03 Chuyển việc phát sinh vào `docs/BACKLOG.md`, nếu có.

## Đóng phase

- [ ] `npm run build` đạt cho frontend và đã đọc lại toàn bộ diff.
- [ ] `git diff --check` đạt, không có lỗi whitespace.
- [ ] Không đổi Swagger, API, type, schema, migration hoặc biến môi trường.
- [ ] Không đổi phân quyền và điều kiện nghiệp vụ của các thao tác chẩn đoán.
- [ ] Confidence và treatment rule hiển thị đúng khi có dữ liệu và ẩn đúng khi thiếu dữ liệu.
- [ ] Layout không tràn ngang ở desktop, 640 px và màn hình nhỏ hơn.
- [ ] Nhãn `progress`, heading và description list dùng được với công nghệ hỗ trợ cơ bản.
- [ ] Không có secret, mật khẩu, dữ liệu thật, ảnh hoặc log test trong diff.
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái cuối trong README phase; phần mô tả PR nằm ngoài phạm vi tài liệu này.
