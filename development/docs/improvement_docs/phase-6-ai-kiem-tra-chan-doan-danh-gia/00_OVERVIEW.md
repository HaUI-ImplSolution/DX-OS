# Phase 6: AI — kiểm tra ảnh, chẩn đoán, đánh giá

| Mục | Giá trị |
|---|---|
| Trạng thái | Chờ Q2 (cần thu bộ ảnh thật ngoài ruộng), Q1 phụ thuộc kết quả WP6-06 |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A (báo cáo đánh giá), B (phần còn lại) |
| Ước lượng | 5-6 ngày |
| Người phụ trách | {điền tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Phase 1 (song song được với P4) |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 5 | Luồng nộp báo cáo sâu bệnh | Quan trọng | Toàn bộ |
| mục 11 | Luồng chẩn đoán và AI | Quan trọng (đánh giá AI là Nhóm A) | Toàn bộ |

## 2. Lý do

- Vấn đề đang gặp: model YOLO hiện có kiểm tra độ nét (`MIN_SHARPNESS = 10.0`, `ai/app/cv/policy.py`) và kích thước ảnh tối thiểu ngay trong `PlantDiseaseService.predict(...)`, nhưng chỉ chạy khi báo cáo đã nộp xong và backend gọi chẩn đoán — không có bước "thử trước khi gửi" cho farmer.
  Model không có lớp "không phải lúa/ảnh linh tinh": khi không có detection nào đạt ngưỡng, `predict(...)` trả fallback `Rice___healthy` với `confidence = 0.0`, nghĩa là ảnh con mèo hay ảnh mờ quá ngưỡng che giấu đều có thể ra kết quả giống "lúa khỏe" nếu không nhìn kỹ `confidence`.
  Đường nhập tay (`DiagnosisController.create`, `DiagnosisCreateRequest`) hiện bắt kỹ sư tự biết và gõ đúng `diseaseCode`, không có hỗ trợ nào; đồng thời luôn đòi treatment rule active cho bệnh đó, seed sạch chưa có rule nào nên đường này lỗi 404 trên database mới.
  `DiagnosisService.createFromAi(...)` gọi AI (timeout đọc 120 giây) bên trong `@Transactional`, giữ một kết nối DB suốt thời gian đó.
- Vì sao làm ở phase này: đánh giá AI có bằng chứng là điều kiện không bị loại (đề mục 4.2 phần I); các lỗ hổng còn lại ảnh hưởng trực tiếp tới độ tin cậy của phần Intelligence khi demo.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Endpoint precheck, tách trạng thái báo cáo, gọi AI ngoài transaction |
| Frontend (web) | Có | Màn kỹ sư nhập triệu chứng, xem gợi ý RAG |
| Mobile | Có | Check nhẹ trước khi gửi, màn "cần chụp lại" riêng |
| AI | Có | Endpoint precheck, xếp hạng theo triệu chứng, lưu nhãn huấn luyện |
| Data / Seed / Migration | Có | Cột trạng thái mới, bảng nhãn huấn luyện |
| Infra / Deploy / CI | Không | |
| Docs / PoF | Có | Báo cáo đánh giá AI |

## 4. Phạm vi

- Trong phạm vi: WP6-01 đến WP6-06 ở `01_PLAN.md` (precheck, trạng thái cần chụp lại, nhập triệu chứng qua RAG, gọi AI ngoài transaction + chẩn đoán lại, lưu nhãn huấn luyện, báo cáo đánh giá AI).
- Ngoài phạm vi (không làm ở phase này): huấn luyện lớp "không phải lúa" mới cho model (nợ kỹ thuật, cần bộ dữ liệu riêng — xem `04_TRADEOFFS_RISKS.md`); mô hình nhận diện sâu hại (ngoài 5 bệnh lá hiện có, theo D7 phase 0).
- Giả định: D7 (phase 0, giữ 5 bệnh lá + thêm đường nhập triệu chứng) đã chốt.

## 5. Ưu điểm và hạn chế

- Ưu điểm: giảm số báo cáo "ảnh không dùng được" phải chờ kỹ sư phát hiện; đường nhập tay hoạt động được trên database sạch (sửa lỗi 404); có báo cáo đánh giá AI đúng yêu cầu đề.
- Hạn chế: không giải quyết được gốc rễ "model không phân biệt ảnh không phải lúa" — chỉ giảm nhẹ bằng ngưỡng confidence, không phải giải pháp triệt để.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (chấp nhận không huấn luyện lại model trong phạm vi phase này).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: farmer chụp ảnh mờ, nhận cảnh báo chụp lại trước khi gửi; kỹ sư nhập triệu chứng bằng văn bản, nhận danh sách bệnh được RAG xếp hạng từ danh mục hiện có, chọn 1 mã bệnh hợp lệ; chẩn đoán lại được trên cùng báo cáo nếu kỹ sư không đồng ý kết quả trước.
- Sản phẩm bàn giao: endpoint precheck, cột trạng thái `NEEDS_RETAKE` tách khỏi `REJECTED`, luồng nhập triệu chứng qua RAG, bảng lưu nhãn huấn luyện, báo cáo đánh giá AI (file riêng trong `development/docs/` hoặc báo cáo kỹ thuật P8).
- Tiêu chí đo được: đường nhập tay không còn lỗi 404 trên database sạch chưa có treatment rule; gọi AI không còn giữ transaction DB suốt thời gian chờ.
- Bằng chứng: log test T6-xx, file báo cáo đánh giá AI.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: `workflow_case`/`workflow_event` (P3) cho danh sách chờ "cần chụp lại"/"chờ chẩn đoán lại" nếu muốn hiển thị tập trung; chỉ cần P1 về mặt kỹ thuật (không phụ thuộc workflow để chạy được).
- Phase sau cần từ phase này: P7 (BI) dùng nhãn huấn luyện đã lưu để làm chart tỷ lệ AI đúng/sai; P8 dùng báo cáo đánh giá AI làm bằng chứng nộp bài.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: đề mục 4.2 phần I (đánh giá AI, Nhóm A — điều kiện không bị loại).
- Dùng làm bằng chứng cho: câu hỏi hội đồng "AI dùng ở đâu, đánh giá, giới hạn?" (mục 20 `PROJECT_IMPROVEMENTS.md`).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Ngưỡng confidence coi là "không chắc, cần chuyển người xem" giữ `0.70` hiện tại hay đổi sau khi có test set độc lập (WP6-06) | Giữ `0.70` tạm, điều chỉnh sau khi có số liệu / Đổi ngay theo ước lượng chuyên gia | {ngày} | _(chưa chốt — chờ kết quả WP6-06)_ |
| Q2 | Bộ ảnh test độc lập cho đánh giá AI lấy từ đâu | Thu thập mới bằng điện thoại ngoài ruộng (mờ, ngược sáng, không phải lúa) / Dùng tạm ảnh có sẵn trong dataset huấn luyện (ít khách quan hơn) | {ngày} | _(chưa chốt — cần người đi thu ảnh thật)_ |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] `ai/app/cv/README.md` (đổi mô tả hành vi fallback, ngưỡng, precheck).
- [ ] Swagger cho endpoint precheck, nhập triệu chứng mới.
