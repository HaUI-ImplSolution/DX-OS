# Checklist: Phase 1

`[x]` hoàn thành; `[ ]` chưa làm.
Đã mở dữ liệu đốm nâu theo yêu cầu gán `EXPERT_APPROVED` của chủ dự án ngày 20/10/2026; các kiểm tra dữ liệu và Java đã đạt, chưa kiểm tay giao diện hoặc khởi động toàn stack.

## WP1-01: Hoàn thiện hồ sơ đốm nâu

Kết quả đầu ra: có quyết định duyệt truy vết được hoặc giữ trạng thái chưa đủ điều kiện.

- [x] WP1-01-DOC-01 Đọc `_template`, dữ liệu thuốc, generator, lookup và kiểm thử đang có.
- [x] WP1-01-DOC-02 Lập kế hoạch với ranh giới thay đổi cụ thể.
- [x] WP1-01-DATA-01 [farm] Đối chiếu đúng Help 400SC/đốm nâu/lúa trong TT75 trang PDF 152 và danh sách tự rút TT28; ghi rõ giới hạn xác minh trong WP1-01 của kế hoạch.
- [x] WP1-01-DATA-02 [farm] Đối chiếu bốn thông số ADC; ghi yêu cầu gán trạng thái của chủ dự án ngày 20/10/2026, chưa kèm văn bản chuyên môn riêng.
- [x] WP1-01-DOC-03 Ghi người/vai trò, ngày, kết luận và tham chiếu hồ sơ duyệt được phép công khai; không đưa thông tin cá nhân nhạy cảm vào repo.

## WP1-02: Dữ liệu và seed

Kết quả đầu ra: 2 thuốc và 3 rule active sau khi đủ điều kiện; không đổi hai rule Newtec.

- [x] WP1-02-DATA-01 [farm] Cập nhật eligibility và trạng thái của duy nhất `P-RICE-002`.
- [x] WP1-02-DATA-02 [farm] Cập nhật duy nhất `TR-RICE-003`, quyết định thật, phiên bản và lý do chặn tương ứng.
- [x] WP1-02-DATA-03 [farm] Sinh lại `farm/data.sql` bằng generator hiện có.
- [x] WP1-02-TEST-01 Cập nhật kỳ vọng hai thuốc/ba rule trong `data/scripts/test_generate_seed_sql.py`; giữ các kiểm tra chặn ứng viên chưa đủ điều kiện.
- [x] WP1-02-TEST-02 Kiểm tra không đổi thông số Newtec, lớp bệnh, policy hoặc bản ghi lịch sử.

## WP1-03: Đường dẫn tiếng Việt

Kết quả đầu ra: danh mục nguồn Farm đồng bộ và không tạo phác đồ bệnh khác.

- [x] WP1-03-DOC-01 Đọc bài đạo ôn Tuyên Quang 25/03/2026, khô vằn Quảng Trị 31/03/2025 và nguồn môi giới Tungro; truy cập trực tiếp HTTP 200 ngày 20/10/2026.
- [x] WP1-03-DOC-02 Ghi trực tiếp nguồn gốc, ngày bài, vị trí đọc và giới hạn theo bệnh trong `01_PLAN.md`, WP1-03; ghi rõ khoảng trống nguồn than lá.
- [x] WP1-03-DATA-01 [farm] Đồng bộ ADC và các nguồn được chọn giữa registry và danh mục Backend.
- [x] WP1-03-DATA-02 [farm] Kiểm tra URL, phạm vi, trạng thái thật và giấy phép; không thay corpus hoặc gán nguồn cho đoạn chưa đối chiếu.
- [x] WP1-03-TEST-01 Kiểm tra danh mục không trùng ID, không lệch metadata và không ảnh hưởng namespace Lab; bộ test nguồn và seed đạt 7 test ngày 20/10/2026.

## WP1-04: Kiểm tra và tài liệu

Kết quả đầu ra: có kết quả thật cho các tiêu chí cần thiết, không có sửa đổi ngoài phạm vi.

- [ ] WP1-04-TEST-01 Thực hiện các case được yêu cầu trong `03_TEST_PLAN.md`; ghi người/ngày chạy, chưa chạy thì không tick đạt.
- [x] WP1-04-DOC-01 Sửa tối thiểu các câu về hai rule và đốm nâu đang bị chặn sau khi trạng thái thực sự đổi.
- [x] WP1-04-DOC-02 Bổ sung CHANGELOG, giữ log cũ; không sửa README/cách chạy nếu không thay đổi.

Đã cập nhật CHANGELOG và các câu trạng thái trong `data/README.md`, `docs/COUNCIL_QA.md`, `docs/BACKLOG.md`; giữ toàn bộ lịch sử cũ.
Chạy compile backend bằng Maven offline ngày 20/10/2026 thất bại tại `dx-core`: `release version 21 not supported`; môi trường có Java 21 runtime nhưng không có `javac` trên PATH.
Không sửa pom, dependency hoặc môi trường hệ thống; dùng image Maven/JDK 21 có sẵn để chạy lại.
Ba lớp test Farm (áp dụng, khoảng liều, khóa thu hoạch) đạt 9 test với JDK 21; hai lớp tích hợp seed/chẩn đoán đạt 15 test trên PostgreSQL 16, gồm luồng đốm nâu và nạp seed lặp lại.
Các kiểm tra dùng image Maven/JDK 21 và pgvector/pg16 có sẵn; alias image tạm và container test được dọn, không khởi động toàn stack.
Kiểm tra orchestration AI bằng fixture đạt 7 tình huống; không sửa code AI hoặc đánh giá lại model.
Còn T1-12 (giao diện/điện thoại) và T1-13 (khởi động toàn stack theo README), nên phase chưa đóng.

## Đóng phase

- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 có bằng chứng; chưa có duyệt chuyên môn thì chưa đóng.
- [x] JSON và SQL đồng bộ; các compile/check áp dụng được thực hiện và ghi kết quả thật.
- [x] Đọc diff: chỉ dữ liệu Farm, kiểm thử liên quan và tài liệu; không đổi API, model, UI, Docker/env/CI hoặc DX-Lab.
- [x] Không có secret, token, mật khẩu, dữ liệu người thật hoặc mã kiểm tra nhạy cảm trong phần thay đổi mới.
- [x] Dọn file tạm, ảnh/PDF/trọng số/test/demo sinh thêm; không đưa tài liệu chưa rõ quyền tái phân phối vào repo.
- [x] Lỗi ngoài phạm vi chỉ được ghi nhận; không sửa kèm hoặc ghi đã qua khi chưa kiểm chứng.
- [ ] Cập nhật trạng thái cuối; chuyển việc còn lại phù hợp vào BACKLOG và xóa thư mục phase theo quy ước `_template` khi kết thúc.

Chỉ sửa hai file kiểm thử Java có sẵn của host; không sửa Java nghiệp vụ, web/mobile, AI, infra hoặc Swagger.
Không khởi động/build thêm Docker chỉ để hoàn thiện tài liệu.
