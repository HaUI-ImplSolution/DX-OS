# Phase 6: AI — kiểm tra ảnh, chẩn đoán

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong (việc chưa làm ghi ở `04_TRADEOFFS_RISKS.md` mục 3) |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ước lượng | 5-6 ngày |
| Người phụ trách | tung |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Phase 1 (song song được với P4) |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 5 | Luồng nộp báo cáo sâu bệnh | Quan trọng | Toàn bộ |
| mục 11 | Luồng chẩn đoán và AI | Quan trọng | Gọi AI ngoài transaction, chẩn đoán lại, nhập triệu chứng, nhãn kỹ sư; phần đánh giá AI (dataset, công cụ, báo cáo chỉ số) không thuộc phase này |

## 2. Lý do

- Vấn đề đang gặp: farmer chỉ biết ảnh mờ sau khi kỹ sư phát hiện; "cần chụp lại" bị trộn với "kỹ sư từ chối khuyến nghị" ở cùng một trạng thái.
  Báo cáo chỉ có tối đa một chẩn đoán nên kỹ sư không chạy lại được; `createFromAi` gọi AI (timeout đọc 120 giây) bên trong transaction và giữ một kết nối DB suốt thời gian đó.
  Đường nhập tay bắt kỹ sư tự gõ `diseaseCode` và luôn đòi treatment rule active, nên lỗi 404 trên database sạch.
  Model YOLO chỉ có 5 lớp bệnh lá, không có lớp "không phải lúa": khi không có detection nào đạt ngưỡng, kết quả rơi về `Rice___healthy` với `confidence = 0.0`; việc lọc ảnh ngoài lúa do crop classifier riêng đảm nhận (mục 5).
- Vì sao làm ở phase này: các lỗ hổng trên ảnh hưởng trực tiếp độ tin cậy của phần Intelligence khi demo.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Precheck và kiểm lại khi nộp, trạng thái cần chụp lại, nhiều chẩn đoán trên một báo cáo, gọi AI ngoài transaction, gợi ý theo triệu chứng, nhãn kỹ sư |
| Frontend (web) | Có | Kỹ sư nhập triệu chứng, xem gợi ý, yêu cầu ảnh mới, xem lịch sử chẩn đoán |
| Mobile | Có | Kiểm ảnh (chất lượng và nhận diện lúa) trước khi gửi, màn "cần chụp lại", chụp lại liên kết báo cáo cũ, giữ `retakeOf` trong hàng đợi offline |
| AI | Có | `POST /api/v1/disease/precheck`, `POST /api/v1/disease/symptoms/suggest`; kiểm chất lượng và phân loại lúa (`assess_image`) dùng chung với `predict` |
| Data / Seed / Migration | Có | `V8`: bỏ unique 1-1 của `diagnoses`, cột chất lượng và retake trên `disease_reports`, bảng `training_labels` |
| Infra / Deploy / CI | Có | Compose mount `best.pt` và `crop_classifier.onnx` read-only từ `ai/weights/`; E2E không tải model |
| Docs / PoF | Có | `ai/app/cv/README.md`, CHANGELOG |

## 4. Phạm vi

- Trong phạm vi: WP6-01, WP6-03, WP6-04, WP6-05 ở `01_PLAN.md` (precheck, trạng thái cần chụp lại, nhập triệu chứng qua RAG, gọi AI ngoài transaction và chẩn đoán lại).
- Ngoài phạm vi (không làm ở phase này):
  - Huấn luyện lớp "không phải lúa" cho detector bệnh: không làm, lọc bằng crop classifier ONNX có sẵn, xem `04_TRADEOFFS_RISKS.md` TO1, TO7.
  - Mô hình nhận diện sâu hại (ngoài 5 bệnh lá, theo D7).
  - Dataset, công cụ và báo cáo chỉ số đánh giá AI (test set độc lập, chỉ số theo lớp, confusion matrix): bỏ khỏi phase, giữ ngưỡng confidence `0.70`.
- Giả định: D7 (phase 0, giữ 5 bệnh lá và thêm đường nhập triệu chứng) đã chốt; hộp thông báo của P5 đã có để báo yêu cầu chụp lại.

## 5. Ưu điểm và hạn chế

- Ưu điểm: farmer biết ảnh có dùng được trước khi gửi; kỹ sư chạy lại chẩn đoán và yêu cầu ảnh mới mà không mất lịch sử; đường nhập tay dùng được trên database sạch; kết nối DB không bị giữ trong lúc chờ AI.
- Hạn chế: crop classifier vẫn nhận nhầm ngô, cỏ, lúa mì thành lúa, chưa nghiệm thu khả năng chặn mọi ảnh ngoài miền; ngưỡng độ nét chưa hiệu chuẩn bằng ảnh ngoài ruộng, không có số liệu đánh giá độ chính xác của model; nộp báo cáo phụ thuộc AI service đang chạy.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (không huấn luyện lại model, quan hệ chẩn đoán 1-N, cho lưu chẩn đoán khi thiếu treatment rule).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: farmer chụp ảnh mờ hoặc ảnh không phải lúa, nhận cảnh báo chụp lại trước khi gửi; kỹ sư nhập triệu chứng, nhận danh sách bệnh xếp hạng từ danh mục, chọn một mã hợp lệ; kỹ sư yêu cầu ảnh mới, farmer nhận thông báo, chụp lại và xem được chuỗi ảnh; chẩn đoán lại được trên cùng báo cáo.
- Sản phẩm bàn giao: endpoint precheck, trạng thái `NEEDS_RETAKE` tách khỏi `REJECTED`, luồng nhập triệu chứng qua RAG, bảng `training_labels`.
- Tiêu chí đo được: đường nhập tay không còn 404 trên database sạch; gọi AI không giữ transaction DB; ảnh mờ hoặc không được nhận là lúa nộp thẳng bị 422 và không lưu.
- Bằng chứng: kết quả T6-xx ở `03_TEST_PLAN.md`.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: `workflow_case` (P3) cho duyệt từng chẩn đoán; `NotificationService` (P5) cho thông báo chụp lại; chỉ cần P1 về mặt kỹ thuật cho phần AI.
- Phase sau cần từ phase này: P7 (BI) dùng nhãn kỹ sư để làm chart tỷ lệ AI đúng/sai.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: đề mục 4.2 phần I (phần AI); phase này không thêm số liệu đánh giá, chỉ giới hạn đã biết ở `04_TRADEOFFS_RISKS.md`.
- Dùng làm bằng chứng cho: câu hỏi hội đồng "AI dùng ở đâu, giới hạn?" (mục 20 `PROJECT_IMPROVEMENTS.md`).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Ngưỡng confidence coi là "không chắc, cần chuyển người xem" | Giữ `0.70` / Đổi theo số liệu đo | 06/10/2026 | Giữ `0.70` trong code; không đo lại vì phần đánh giá AI bỏ khỏi phase |

## 10. Tài liệu cần cập nhật khi xong

- [x] `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] `ai/README.md`, `ai/app/cv/README.md` (precheck, phân loại lúa, ngưỡng, gợi ý triệu chứng).
- [x] `development/docs/pending_docs/CV_RICE_IMAGE_VALIDATION_SUMMARY.md`: tóm tắt triển khai và kiểm chứng crop classifier.
- [x] `mobile/README.md` (kiểm ảnh, chụp lại).
- [x] Swagger cho precheck, yêu cầu chụp lại, gợi ý triệu chứng, lịch sử chẩn đoán.
