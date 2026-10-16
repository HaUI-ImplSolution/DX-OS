# Kế hoạch kiểm thử: Phase 1

Chưa chạy kiểm tra triển khai model trong commit kế hoạch này.
Mặc định kiểm cú pháp và diff; chỉ chạy test/runtime khi người dùng yêu cầu.

## 1. Chiến lược

- Mức kiểm tra: cú pháp, unit CV gốc, tương thích contract và smoke luồng chẩn đoán khi được yêu cầu.
- Môi trường runtime: checkout riêng của lần tích hợp mới, cấu hình theo README main; không thay dữ liệu đang dùng của người dùng.
- Test CV sửa trong bộ `ai/tests` hiện có; không thêm benchmark, ảnh fixture riêng hoặc thư mục test trong deploy.
- Dữ liệu: ảnh người dùng cho phép dùng ngoài Git; mock trong test gốc; tài khoản demo hiện có.
- Không huấn luyện hoặc chấm lại accuracy; chỉ số 89,43% là báo cáo đã cung cấp.
- Các ô kết quả và người chạy để trống cho đến khi kiểm tra thật trên code mới.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T1-01 | WP1-01 | Contract | So class_names/class_to_idx checkpoint với contract và disease.code | Sáu lớp đúng thứ tự, không tạo mã bệnh mới |  |  |
| T1-02 | WP1-02 | Happy path | Nạp checkpoint thật và đối chiếu recipe trên một ảnh được phép dùng | RGB/EXIF, 480×480 bicubic, normalization và top-1 đúng recipe |  |  |
| T1-03 | WP1-02 | Ngưỡng | Mock confidence 0,6999; 0,70; 0,91 trong test gốc | Chỉ hai giá trị sau được đi tiếp, không đổi ngưỡng |  |  |
| T1-04 | WP1-02 | Healthy | Classifier trả lớp Healthy qua diagnose | Mã Healthy đúng, không tự duyệt hoặc tự tạo rule thuốc |  |  |
| T1-05 | WP1-02 | Âm tính | Ảnh hỏng/mờ/tối hoặc crop classifier không nhận lúa | Giữ quy tắc từ chối, không chạy suy luận bệnh/Backend/RAG sau khi bị chặn |  |  |
| T1-06 | WP1-02 | Artifact/output lỗi | Thiếu file, checksum sai, class order sai, logits lỗi hoặc không hữu hạn | Bị từ chối theo lỗi phù hợp; không trả Healthy thay lỗi |  |  |
| T1-07 | WP1-02 | API | Đối chiếu predict/diagnose/health với schema cũ | Endpoint và cấu trúc cũ; metadata model mới; service key vẫn bắt buộc như main |  |  |
| T1-08 | WP1-03 | Đóng gói | Build image từ checkout mới, kiểm file trong image và restart AI khi không có mạng | Ghép đủ checkpoint, không tải model bệnh, volume embedding không che file |  |  |
| T1-09 | WP1-03 | Cấu hình/CI | Dùng `.env.example`, script env CI và job AI hiện có | Path/checksum khớp artifact mới; không hard-code/in checksum vào log |  |  |
| T1-10 | WP1-04 | Farm | Upload → kỹ sư chẩn đoán → duyệt → nông dân xem | Giữ phân quyền, ẩn kết luận chờ duyệt; rule/liều/PHI do Backend quyết định |  |  |
| T1-11 | WP1-04 | Ranh giới | Chuyển Farm/Lab theo lệnh gốc khi được yêu cầu smoke | App hoạt động như main, không đổi core hoặc thêm cách chạy |  |  |
| T1-12 | WP1-04 | Kiểm tra phạm vi | Đọc diff theo danh sách file của `01_PLAN.md`, dò file thừa và secret | Chỉ có thay model và phụ thuộc trực tiếp; không còn công cụ tích hợp tạm |  |  |

## 3. Hồi quy (không được hỏng)

**Dùng chung**

- [ ] Payload diagnose và mã bệnh canonical vẫn được Backend đọc được.
- [ ] Xác thực service key, chất lượng ảnh và crop classifier giữ hành vi hiện tại.
- [ ] Các dịch vụ RAG, Ollama, SSO, BI và chat không bị sửa cấu hình để phục vụ đổi model.
- [ ] Cách chạy và chuyển app giống main; không thêm script chuẩn bị env hoặc host dependency mới.

**Farm**

- [ ] Confidence dưới 0,70 bị chặn trước Backend facts/RAG/LLM.
- [ ] Chẩn đoán chưa duyệt vẫn bị ẩn với nông dân.
- [ ] Healthy không tự sinh thuốc; PHI và rule điều trị tiếp tục do Backend quản lý.
- [ ] Chẩn đoán lịch sử và dữ liệu hiện có không bị sửa hoặc backfill.

## 4. Tiêu chí đạt của phase (exit criteria)

- Các case được yêu cầu đã có người chạy và kết quả; không tự tick các case chưa được chạy.
- Các tiêu chí bàn giao trong `00_OVERVIEW.md` có bằng chứng mới và không có lỗi chặn tích hợp.
- Bất kỳ thay đổi phạm vi nào cần xem lại kế hoạch trước khi sửa tiếp.
- Không sử dụng kết quả test từ nhánh/PR cũ để chứng minh lần tích hợp mới đạt.

## 5. Bằng chứng và báo lỗi

- Ghi tóm tắt lệnh, người chạy, ngày và kết quả vào bảng trên hoặc nội dung PR khi thực hiện.
- Log/ảnh tạm nằm ngoài repository và được dọn sau kiểm tra; không thêm báo cáo test phát sinh vào release.
- Che credential/checksum riêng trước khi chia sẻ log; số liệu accuracy phải ghi đúng nguồn người huấn luyện.
- Lỗi mới ghi vào issue hoặc BACKLOG đúng phạm vi, không sửa module ngoài phase để làm xanh kiểm tra.
