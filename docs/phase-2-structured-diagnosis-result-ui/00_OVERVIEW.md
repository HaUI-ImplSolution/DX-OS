# Phase 2: Cấu trúc hóa giao diện kết quả chẩn đoán

| Mục | Giá trị |
|---|---|
| Trạng thái | Đã lập kế hoạch, chờ triển khai |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | Frontend web của `app-farm` |
| Ước lượng | 0,5 ngày |
| Người phụ trách | Nhóm Frontend |
| Bắt đầu, dự kiến xong | Sau khi kế hoạch được duyệt; trong 0,5 ngày làm việc |
| Phụ thuộc | API chẩn đoán và treatment rule hiện có |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Yêu cầu UI ngày 19/10/2026 | Cấu trúc hóa cách hiển thị kết quả chẩn đoán và điều trị | Quan trọng | Toàn bộ |
| `DiagnosisPanel` trên `origin/Khuong/main` | Kết quả đang dùng dòng metadata, blockquote và bảng treatment rule | Quan trọng | Chỉ khối kết quả chẩn đoán |
| [Frontend README](../../frontend/README.md) | Web dùng chung, Farm cung cấp màn hình nghiệp vụ chẩn đoán | Theo dõi | Chỉ giao diện Farm |

## 2. Lý do

- Kết quả hiện tại trình bày confidence, model, thời điểm chẩn đoán và phác đồ trong các dòng văn bản/bảng đơn giản nên khó quét nhanh.
- Các dữ liệu hành động quan trọng gồm thuốc, liều lượng và thời gian cách ly cần được phân nhóm rõ hơn cho kỹ sư và quản trị viên.
- Giao diện cần giữ khả năng đọc trên màn hình hẹp mà không thay đổi nghiệp vụ phía sau.

## 3. Tổng quan sửa đổi

Sẽ đổi confidence từ một dòng metadata thành phần trăm kèm thanh tiến trình có nhãn hỗ trợ truy cập.
Sẽ thay bảng treatment rule bằng card ngữ nghĩa gồm thuốc, liều lượng và thời gian cách ly.
Sẽ tách nguồn và phiên bản treatment rule thành khu vực riêng, đồng thời định dạng danh sách nguồn dễ đọc hơn.
Khối kết quả chính sẽ ưu tiên dữ liệu có cấu trúc và không còn hiển thị recommendation dạng văn bản tự do, model version hoặc thời điểm chẩn đoán.
Lưới ba cột sẽ chuyển về một cột trên màn hình rộng tối đa 640 px.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Không | Giữ hợp đồng hiện tại |
| `dx-core` (cài đặt dùng chung) | Không | Không ảnh hưởng |
| `app-farm` | Không | Không đổi backend nghiệp vụ |
| `app-lab` | Không | Không ảnh hưởng |
| `dx-host` (chạy, chọn app) | Không | Không ảnh hưởng |
| Frontend (web) | Có | `DiagnosisPanel` và CSS dùng cho kết quả chẩn đoán Farm |
| Mobile | Không | Không thay đổi giao diện mobile |
| AI | Không | Không đổi mô hình hoặc confidence |
| Data / Seed / Migration | Không | Không có migration |
| Infra / Deploy / CI | Không | Không đổi cấu hình |
| Docs | Có | Bổ sung kế hoạch phase này |

## 4. Phạm vi

- Trong phạm vi: bố cục confidence, treatment rule, nguồn, phiên bản và responsive của kết quả chẩn đoán trên web Farm.
- Trong phạm vi: dùng phần tử HTML ngữ nghĩa `progress`, `section` và `dl` cùng nhãn truy cập phù hợp.
- Ngoài phạm vi: tạo hoặc duyệt chẩn đoán, yêu cầu chụp lại, áp dụng điều trị, lịch sử chẩn đoán và mọi endpoint liên quan.
- Ngoài phạm vi: thay đổi dữ liệu trả về, công thức confidence, nội dung treatment rule, phân quyền và giao diện mobile/Lab.
- Giả định: `confidence` tiếp tục nằm trong khoảng 0–1; `treatmentRule` dùng cùng cấu trúc hiện có.
- Ranh giới Core/App: không thêm hoặc đổi interface; thay đổi nằm hoàn toàn trong component và stylesheet frontend Farm.

## 5. Ưu điểm và hạn chế

- Ưu điểm: thông tin cần hành động được phân cấp rõ, dễ quét và phù hợp màn hình hẹp.
- Ưu điểm: cấu trúc `progress`, `section`, `dl`, `dt`, `dd` diễn đạt ngữ nghĩa tốt hơn bảng trình bày cũ.
- Hạn chế: model version, thời điểm chẩn đoán và recommendation dạng văn bản tự do không còn xuất hiện trong khối kết quả chính.
- Hạn chế: màu thanh tiến trình chỉ biểu diễn confidence, không tự diễn giải đây là mức an toàn hoặc quyết định phê duyệt.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: mở một báo cáo có chẩn đoán và thấy confidence dạng phần trăm/thanh tiến trình cùng card phác đồ điều trị.
- Card hiển thị đúng mã thuốc, một giá trị hoặc khoảng liều, đơn vị và số ngày cách ly từ payload hiện có.
- Nguồn phân tách bằng dấu phẩy được trình bày thành các mục ngăn bằng `·`; phiên bản chỉ hiện khi có dữ liệu.
- Khi không có confidence hoặc treatment rule, giao diện không dựng khối tương ứng và các thao tác còn lại vẫn hoạt động.
- Ở chiều rộng tối đa 640 px, ba trường treatment chuyển thành một cột và không tràn ngang.
- `npm run build` trong `frontend` đạt; `git diff --check` đạt.
- Không có thay đổi API, schema, dữ liệu, backend, mobile, AI, deploy hoặc quyền người dùng.
- Bằng chứng: kết quả build, đọc diff và ảnh chụp desktop/mobile khi kiểm tra thủ công.

## 7. Phụ thuộc và thứ tự

- WP2-01 chốt cấu trúc thông tin trước WP2-02.
- WP2-02 hoàn tất trước kiểm tra responsive, accessibility và hồi quy ở WP2-03.
- Không có phase sau phụ thuộc bắt buộc vào thay đổi này.

## 8. Ảnh hưởng đến thi

- Cải thiện khả năng trình diễn luồng AI chẩn đoán → kỹ sư duyệt → áp dụng phác đồ của DX-Farm.
- Làm rõ các dữ liệu VietGAP quan trọng gồm thuốc, liều và thời gian cách ly mà không thay đổi luật poka-yoke phía backend.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Có giữ recommendation dạng văn bản, model version và thời điểm chẩn đoán trong khối kết quả chính không? | Giữ / bỏ để ưu tiên dữ liệu có cấu trúc | Trước khi triển khai | Chọn bỏ khỏi khối kết quả chính trong phase này |

## 10. Tài liệu cần cập nhật khi xong

- [ ] Không thay đổi README hoặc CHANGELOG ở bước lập kế hoạch; chỉ cập nhật trạng thái cuối khi phase hoàn thành.
- [ ] Chỉ cập nhật `CHANGELOG.md` nếu maintainer yêu cầu ghi thay đổi UI nhỏ này vào release note.
- [ ] Không cập nhật Swagger, kiến trúc, `.env.example` hoặc migration vì hợp đồng và vận hành không đổi.
