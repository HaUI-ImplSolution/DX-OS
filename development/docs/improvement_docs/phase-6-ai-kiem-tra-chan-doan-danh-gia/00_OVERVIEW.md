# Phase 6: AI — kiểm tra ảnh, chẩn đoán

| Mục | Giá trị |
|---|---|
| Trạng thái | Hoàn thành triển khai và kiểm thử tự động; còn demo điện thoại thật |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ước lượng | 5-6 ngày |
| Người phụ trách | tung |
| Bắt đầu, dự kiến xong | 06/10/2026, chờ demo điện thoại |
| Phụ thuộc | Phase 1 (song song được với P4) |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 5 | Luồng nộp báo cáo sâu bệnh | Quan trọng | Toàn bộ |
| mục 11 | Luồng chẩn đoán và AI | Quan trọng | Chẩn đoán lại, transaction, lưu nhãn kỹ sư |

## 2. Lý do

- Vấn đề đang gặp: model YOLO hiện có kiểm tra độ nét (`MIN_SHARPNESS = 10.0`, `ai/app/cv/policy.py`) và kích thước ảnh tối thiểu ngay trong `PlantDiseaseService.predict(...)`, nhưng chỉ chạy khi báo cáo đã nộp xong và backend gọi chẩn đoán — không có bước "thử trước khi gửi" cho farmer.
  Model không có lớp "không phải lúa/ảnh linh tinh": khi không có detection nào đạt ngưỡng, `predict(...)` trả fallback `Rice___healthy` với `confidence = 0.0`, nghĩa là ảnh con mèo hay ảnh mờ quá ngưỡng che giấu đều có thể ra kết quả giống "lúa khỏe" nếu không nhìn kỹ `confidence`.
  Đường nhập tay (`DiagnosisController.create`, `DiagnosisCreateRequest`) hiện bắt kỹ sư tự biết và gõ đúng `diseaseCode`, không có hỗ trợ nào; đồng thời luôn đòi treatment rule active cho bệnh đó, seed sạch chưa có rule nào nên đường này lỗi 404 trên database mới.
  `DiagnosisService.createFromAi(...)` gọi AI (timeout đọc 120 giây) bên trong `@Transactional`, giữ một kết nối DB suốt thời gian đó.
- Vì sao làm ở phase này: các lỗ hổng trên ảnh hưởng trực tiếp tới độ tin cậy của phần Intelligence khi demo.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Endpoint precheck, tách trạng thái báo cáo, gọi AI ngoài transaction |
| Frontend (web) | Có | Màn kỹ sư nhập triệu chứng, xem gợi ý RAG |
| Mobile | Có | Check nhẹ trước khi gửi, màn "cần chụp lại" riêng |
| AI | Có | Endpoint precheck, xếp hạng theo triệu chứng |
| Data / Seed / Migration | Có | Cột trạng thái mới, bảng nhãn huấn luyện |
| Infra / Deploy / CI | Không | |
| Docs / PoF | Có | API, hướng dẫn sử dụng và bàn giao |

## 4. Phạm vi

- Trong phạm vi: WP6-01/03/04/05 ở `01_PLAN.md` (precheck, trạng thái cần chụp lại, nhập triệu chứng qua RAG, gọi AI ngoài transaction + chẩn đoán lại, lưu nhãn kỹ sư).
- Theo yêu cầu của người dùng ngày 06/10/2026, bỏ phần bộ dataset, công cụ tính chỉ số và báo cáo đánh giá AI khỏi phase này.
- Ngoài phạm vi (không làm ở phase này): huấn luyện lớp "không phải lúa" mới cho model (nợ kỹ thuật, cần bộ dữ liệu riêng — xem `04_TRADEOFFS_RISKS.md`); mô hình nhận diện sâu hại (ngoài 5 bệnh lá hiện có, theo D7 phase 0).
- Giả định: D7 (phase 0, giữ 5 bệnh lá + thêm đường nhập triệu chứng) đã chốt.

## 5. Ưu điểm và hạn chế

- Ưu điểm: giảm số báo cáo "ảnh không dùng được" phải chờ kỹ sư phát hiện; đường nhập tay hoạt động được trên database sạch (sửa lỗi 404).
- Hạn chế: không giải quyết được gốc rễ "model không phân biệt ảnh không phải lúa" — chỉ giảm nhẹ bằng ngưỡng confidence, không phải giải pháp triệt để.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (chấp nhận không huấn luyện lại model trong phạm vi phase này).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: farmer chụp ảnh mờ, nhận cảnh báo chụp lại trước khi gửi; kỹ sư nhập triệu chứng bằng văn bản, nhận danh sách bệnh được RAG xếp hạng từ danh mục hiện có, chọn 1 mã bệnh hợp lệ; chẩn đoán lại được trên cùng báo cáo nếu kỹ sư không đồng ý kết quả trước.
- Sản phẩm bàn giao: endpoint precheck, cột trạng thái `NEEDS_RETAKE` tách khỏi `REJECTED`, luồng nhập triệu chứng qua RAG, bảng lưu nhãn kỹ sư và tài liệu bàn giao.
- Tiêu chí đo được: đường nhập tay không còn lỗi 404 trên database sạch chưa có treatment rule; gọi AI không còn giữ transaction DB suốt thời gian chờ.
- Bằng chứng: log test T6-xx và demo luồng nghiệp vụ.

## 7. Phụ thuộc và thứ tự

- Nền nhánh cha hiện có P1/P2/P3. Phase 6 dùng MinIO để lưu ảnh và workflow P3 để ghi hồ sơ/event duyệt cho từng chẩn đoán.
- Phase sau cần từ phase này: P7 (BI) có thể dùng nhãn kỹ sư đã lưu; P8 dùng luồng nghiệp vụ và tài liệu bàn giao cho demo.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Dùng làm bằng chứng cho vai trò của AI, giới hạn kết quả và bước duyệt của kỹ sư trong luồng nghiệp vụ.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Ngưỡng confidence cho phép tiếp tục chẩn đoán AI | Giữ `0.70` / Điều chỉnh | 06/10/2026 | Người dùng đã chốt giữ `0.70` |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] `ai/app/cv/README.md` (đổi mô tả hành vi fallback, ngưỡng, precheck).
- [x] Swagger cho endpoint precheck, nhập triệu chứng mới.

## Ghi nhận triển khai

Xem [bàn giao](./05_IMPLEMENTATION.md).
Q1 đã chốt giữ ngưỡng 0.70 theo xác nhận của người dùng ngày 06/10/2026.
Nhãn kỹ sư được lưu trong DB Backend; AI chỉ xếp hạng từ corpus, không sở hữu dữ liệu báo cáo/chẩn đoán.
