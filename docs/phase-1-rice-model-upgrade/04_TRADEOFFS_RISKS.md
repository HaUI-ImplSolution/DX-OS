# Trade-off và rủi ro: Phase 1

Giữ file này trong giai đoạn kế hoạch vì đổi detector sang classifier và cách phân phối checkpoint ảnh hưởng trực tiếp kết quả CV và triển khai model.

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Model bệnh | A: YOLO cũ; B: EfficientNetV2-M sáu lớp | B theo yêu cầu nâng cấp | Healthy được huấn luyện, dùng checkpoint trên tập dữ liệu dự án | Không có định vị tổn thương bằng boxes; checkpoint lớn hơn | Vừa, revert model và cấu hình |
| TO2 | Đóng gói | A: ghép trên host; B: ghép trong Docker build | B, đã duyệt | Không thêm bước/công cụ ghép trên host, restart offline cho model bệnh | Thêm stage build AI và chỉnh riêng mount/path của model bệnh | Vừa |
| TO3 | Tổ chức CV | A: provider framework/manifest riêng; B: cấu trúc CV gốc | B | Ít file/caller thay đổi, giữ ranh giới hiện có | Chưa hỗ trợ chuyển nhiều provider | Thấp |

Kết luận: TO1 và TO3 theo phạm vi người dùng đã yêu cầu; TO2 đã được người dùng duyệt cùng kế hoạch ngày 16/10/2026.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Lệch class ID hoặc tiền xử lý | Vừa | Kết luận sai dù model nạp được | Đối chiếu metadata/contract và recipe, nạp strict | Logits hoặc mapping khác reference | Dừng tích hợp, sửa đúng loader/model recipe |
| R2 | Điểm classifier bị hiểu như độ chính xác đã hiệu chuẩn | Vừa | Kết luận quá tự tin ngoài thực địa | Giữ 0,70 và bước kỹ sư duyệt; ghi đúng nguồn chỉ số | Ảnh lạ được dự đoán cao | Ghi nhận để đánh giá riêng, không mở rộng phase |
| R3 | Volume che checkpoint hoặc env còn path/checksum cũ | Vừa | AI không khởi động | Model đặt ngoài volume embedding, cập nhật đúng tham chiếu model | Thiếu file/checksum không khớp | Revert image và cấu hình model |
| R4 | CI hoặc công cụ gốc còn gắn ID/path YOLO | Vừa | Kiểm tra lỗi hoặc báo cáo sai | Rà mọi caller liên quan class contract/model path | Test đòi năm lớp hoặc đọc best.pt | Sửa đúng caller trong danh sách phạm vi |
| R5 | Nhận nhầm ảnh không phải lúa | Đã có ở crop classifier | Làm giảm chất lượng pipeline | Giữ bộ lọc và ghi nhận giới hạn hiện tại | Crop gate nhận nhầm ảnh cây khác | Tách công việc crop classifier khỏi phase |

## 3. Nợ kỹ thuật chấp nhận

- Chưa đánh giá độc lập trên ảnh ruộng thật hoặc hiệu chuẩn confidence tại 0,70; không chấm lại trong đợt đổi model này.
- Giữ metadata `fallback` để consumer gốc tương thích; inference classifier không dùng trường này thay cho kết quả lỗi.
- Giữ nguyên các hạn chế vận hành/SSO/BI của main; chỉ sửa lỗi thuộc model được thay.

## 4. Điều kiện dừng và rollback

- Dừng nếu phải đổi API/DTO, Backend, UI, SSO, BI hoặc script khởi động mới tích hợp được model.
- Dừng nếu artifact/recipe/mapping không khớp hoặc không còn quyền phân phối trọng số/dữ liệu theo giấy phép đã khai báo.
- Dừng nếu diff vượt danh sách file của `01_PLAN.md`; đưa vấn đề về kế hoạch để người dùng xem trước.
- Rollback bằng revert commit model, phục hồi image/trọng số và path/checksum riêng tương ứng bản main; giữ dữ liệu và volume ứng dụng.

## 5. Tác động ngoài phase

- Core, Backend, web, mobile và Lab giữ code, hợp đồng và quy trình hiện có.
- RAG, điều trị và phê duyệt tiếp tục dùng dữ liệu có cấu trúc hiện tại.
- Tài liệu release chỉ cập nhật trạng thái model; thư mục phase được dọn theo quy ước template khi kết thúc.
