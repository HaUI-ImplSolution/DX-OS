# Plan thực hiện: Phase 2

Việc chi tiết nằm ở [02_CHECKLIST.md](./02_CHECKLIST.md).
Thứ tự thực hiện: chốt cấu trúc thông tin → triển khai component/CSS → kiểm tra.
Không có thay đổi Swagger, API hoặc schema.

## Thứ tự và phụ thuộc

WP2-01 → WP2-02 → WP2-03.
WP2-03 chỉ bắt đầu sau khi component và responsive CSS hoàn tất.

## WP2-01: Chốt cấu trúc kết quả chẩn đoán

- Mục tham chiếu: `00_OVERVIEW.md`, mục 2–6.
- Mục tiêu: xác định thông tin chính cần ưu tiên và hành vi khi dữ liệu tùy chọn bị thiếu.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Có confidence | Một dòng metadata gồm confidence, model và thời điểm | Phần trăm nổi bật kèm thanh tiến trình |
| Có recommendation dạng văn bản | Hiển thị trong blockquote | Không hiển thị trong khối kết quả chính |
| Có treatment rule | Bảng bốn dòng | Card có ba nhóm thông tin chính và khu vực nguồn riêng |
| Có nhiều nguồn ngăn bằng dấu phẩy | Hiển thị nguyên chuỗi | Hiển thị các nguồn ngăn bằng `·` |
| Thiếu source hoặc version | Dòng nguồn vẫn phụ thuộc toàn bộ rule | Chỉ hiện phần có giá trị; ẩn cả khu vực nếu thiếu cả hai |

**Hướng làm**

- Dùng dữ liệu sẵn có trong `diagnosis`; không biến đổi contract hoặc gọi thêm API.
- Ưu tiên thuốc, liều và thời gian cách ly vì đây là dữ liệu có cấu trúc phục vụ thao tác tiếp theo.
- Giữ nguyên điều kiện hiển thị theo `approvalStatus`, `canDecide`, `needsRetake` và `treatmentRule`.

**Phạm vi ảnh hưởng**

- Module: frontend web của Farm.
- File chính: `frontend/src/apps/farm/components/DiagnosisPanel.tsx`.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Role và client bị ảnh hưởng: người dùng web Farm nhìn thấy kết quả chẩn đoán; quyền thao tác không đổi.
- Tương thích ngược và dữ liệu cũ: payload và dữ liệu cũ tiếp tục dùng được, không cần migration.

**Kết quả đầu ra**

- Cấu trúc hiển thị mới dùng đúng các trường hiện có và không thay đổi điều kiện nghiệp vụ.

**Phụ thuộc, lưu ý**: quyết định Q1 ở `00_OVERVIEW.md` cần được xác nhận khi review.

## WP2-02: Triển khai card điều trị và responsive

- Mục tham chiếu: WP2-01.
- Mục tiêu: hiện thực cấu trúc mới bằng markup ngữ nghĩa và CSS phù hợp desktop/mobile.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Desktop | Treatment rule dạng bảng dọc | Ba fact card cùng hàng trong card điều trị |
| Màn hình tối đa 640 px | Không có layout riêng cho bảng | Ba fact card xếp thành một cột |
| Công nghệ hỗ trợ đọc nội dung | Confidence là văn bản thường | `progress` có `aria-label`; section nối với heading bằng `aria-labelledby` |

**Hướng làm**

- Thay markup bảng bằng `section`, `dl`, `dt` và `dd` trong `DiagnosisPanel`.
- Tạo class riêng cho confidence, treatment card, facts và source; không dùng inline style.
- Style pseudo-element của `progress` cho trình duyệt WebKit và Firefox.
- Giữ màu từ biến theme hiện có; giới hạn CSS mới trong các class của khối chẩn đoán.

**Phạm vi ảnh hưởng**

- Module: frontend web.
- File chính: `frontend/src/apps/farm/components/DiagnosisPanel.tsx`, `frontend/src/styles.css`.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Role và client bị ảnh hưởng: mọi role được phép xem nội dung chẩn đoán trên web Farm.
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng.

**Kết quả đầu ra**

- Card đúng bố cục ở desktop và chuyển một cột ở màn hình hẹp; nhãn và heading có liên kết truy cập rõ ràng.

**Phụ thuộc, lưu ý**: không thay đổi các selector chung ngoài phạm vi chẩn đoán.

## WP2-03: Kiểm tra và chốt phạm vi

- Mục tham chiếu: `03_TEST_PLAN.md`.
- Mục tiêu: chứng minh thay đổi build được, không làm mất thao tác nghiệp vụ và không kéo theo phạm vi ngoài UI.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Kiểm tra phase | Chưa có bằng chứng riêng | Có build, diff check và kiểm tra thủ công theo ma trận dữ liệu |

**Hướng làm**

- Chạy `npm run build` tại `frontend` và `git diff --check`.
- Kiểm tra thủ công với confidence/treatment rule có và không có dữ liệu tùy chọn.
- Kiểm tra desktop, chiều rộng 640 px và nhỏ hơn 640 px.
- Xác nhận nút duyệt, từ chối, yêu cầu ảnh mới và ghi nhận phun vẫn theo điều kiện cũ.
- Đọc diff để bảo đảm không đổi API, type, backend hoặc stylesheet không liên quan.

**Phạm vi ảnh hưởng**

- Module: frontend web và tài liệu phase.
- File chính: các file ở WP2-02 cùng thư mục plan này.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Role và client bị ảnh hưởng: không đổi quyền; chỉ xác nhận hồi quy cho web Farm.
- Tương thích ngược và dữ liệu cũ: không cần xử lý.

**Kết quả đầu ra**

- Các case bắt buộc trong `03_TEST_PLAN.md` đạt và có bằng chứng; diff chỉ còn thay đổi thuộc phạm vi phase.

**Phụ thuộc, lưu ý**: không ghi một case là đạt nếu chưa thực sự chạy.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | API chẩn đoán hiện có | Không đổi | Không |
| Interface/DTO | `Diagnosis` và `TreatmentRule` phía frontend | Không đổi | Không |
| Bảng DB | Chẩn đoán và treatment rule | Không đổi | Không |
| Biến môi trường | Frontend và deploy | Không đổi | Không |
| UI | Khối kết quả trong `DiagnosisPanel` | Đổi cấu trúc trình bày | Không đổi contract; có thay đổi thông tin hiển thị |

## Dữ liệu, migration và rollback

- Migration: không có.
- Dữ liệu cũ: tiếp tục hiển thị từ payload hiện có, không backfill hoặc chuyển đổi.
- Rollback: revert markup và CSS của hai file frontend; không mất dữ liệu hoặc cần rollback backend.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Ưu tiên treatment rule có cấu trúc trong khối kết quả | Thuốc, liều và PHI dễ quét và phục vụ thao tác tiếp theo |
| D2 | Dùng HTML ngữ nghĩa thay cho bảng trình bày | Diễn đạt quan hệ nội dung rõ hơn và dễ responsive |
| D3 | Responsive bằng breakpoint 640 px | Giữ ba cột trên desktop và tránh tràn trên màn hình hẹp |
| D4 | Không đổi contract hoặc nghiệp vụ | Phase chỉ cải thiện giao diện kết quả |
