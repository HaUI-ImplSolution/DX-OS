# Kế hoạch kiểm thử: Phase 2

Chưa chạy build, kiểm tra diff hoặc kiểm tra giao diện trong giai đoạn lập kế hoạch.
Chỉ ghi kết quả sau khi code được triển khai trên nhánh riêng.

## 1. Chiến lược

- Mức kiểm thử: build TypeScript/Vite, kiểm tra thủ công giao diện, responsive và accessibility cơ bản.
- Môi trường: frontend Farm dùng API hoặc dữ liệu demo hiện có; không sửa database để phục vụ test.
- Trình duyệt: ít nhất một trình duyệt Chromium; kiểm thêm Firefox nếu môi trường cho phép vì có style `::-moz-progress-bar`.
- Kích thước màn hình: desktop, đúng 640 px và nhỏ hơn 640 px.
- Dữ liệu test: chẩn đoán có/không có confidence; treatment rule có liều đơn/khoảng liều; có/không có source và version.
- Không cần chạy backend, mobile, AI hoặc migration test nếu dữ liệu cần thiết đã có sẵn để quan sát UI.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T2-01 | WP2-02 | Happy path | Mở chẩn đoán có confidence và treatment rule đầy đủ | Hiện phần trăm, progress, thuốc, liều, PHI, source và version đúng payload |  |  |
| T2-02 | WP2-02 | Liều lượng | So trường hợp `doseMin = doseMax` và `doseMin != doseMax` | Hiện một giá trị ở trường hợp đầu; hiện khoảng `min–max` ở trường hợp sau; luôn có đơn vị |  |  |
| T2-03 | WP2-02 | Dữ liệu tùy chọn | Lần lượt bỏ confidence, treatment rule, source và version | Ẩn đúng khối thiếu dữ liệu; không có chữ `undefined`, khoảng trống thừa hoặc lỗi render |  |  |
| T2-04 | WP2-02 | Định dạng nguồn | Dùng source gồm nhiều giá trị phân tách bằng dấu phẩy | Các phần nguồn hiển thị ngăn bằng `·`; dữ liệu gốc không bị sửa |  |  |
| T2-05 | WP2-02 | Responsive | Quan sát treatment card ở desktop, 640 px và dưới 640 px | Desktop có ba cột; từ 640 px trở xuống có một cột; không tràn ngang |  |  |
| T2-06 | WP2-02 | Accessibility | Kiểm tra accessible name của progress và liên kết section-heading | Progress có nhãn “Độ tin cậy của chẩn đoán”; section tham chiếu đúng heading duy nhất theo diagnosis ID |  |  |
| T2-07 | WP2-03 | Hồi quy hành vi | Với PENDING/APPROVED/REJECTED và các role hợp lệ, quan sát nút hành động | Điều kiện duyệt, từ chối, chụp lại và ghi nhận phun giống trước thay đổi |  |  |
| T2-08 | WP2-03 | Build/phạm vi | Chạy `npm run build`, `git diff --check` và đọc danh sách file đổi | Build/diff check đạt; không có thay đổi API, schema, backend, mobile, AI hoặc deploy |  |  |

## 3. Hồi quy (không được hỏng)

**Frontend dùng chung**

- [ ] Shell web, đăng nhập và chọn module theo `GET /api/v1/app` vẫn hoạt động.
- [ ] CSS mới không làm thay đổi bảng, card hoặc progress ngoài khối chẩn đoán.
- [ ] Giao diện Lab không bị ảnh hưởng.

**Farm**

- [ ] Chẩn đoán PENDING chỉ cho ENGINEER/ADMIN thao tác quyết định như trước.
- [ ] Chẩn đoán APPROVED có treatment rule vẫn cho ENGINEER/ADMIN mở phần ghi nhận phun.
- [ ] Chẩn đoán APPROVED thiếu treatment rule vẫn hiện cảnh báo và không cho ghi nhận phun.
- [ ] Chẩn đoán REJECTED vẫn hiện người từ chối và lý do.
- [ ] Yêu cầu chụp lại vẫn khóa các thao tác theo hành vi hiện có.
- [ ] Lịch sử chẩn đoán phía dưới không đổi.

## 4. Tiêu chí đạt của phase (exit criteria)

- `npm run build` và `git diff --check` đạt.
- T2-01 đến T2-08 đạt; không còn lỗi giao diện nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 có bằng chứng thực tế.
- Nếu cần khôi phục recommendation hoặc metadata đã ẩn, cập nhật quyết định Q1 và plan trước khi sửa tiếp.

## 5. Bằng chứng và báo lỗi

- Ghi người chạy và kết quả trực tiếp vào bảng sau khi thực hiện.
- Ảnh chụp tạm dùng cho review không chứa thông tin người dùng thật.
- Lỗi ngoài phạm vi được ghi vào issue hoặc `docs/BACKLOG.md`; không mở rộng phase để sửa kèm.
