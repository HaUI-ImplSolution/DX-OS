# Format đầu ra kỳ vọng trên web

Tài liệu này là đặc tả trình bày trong kế hoạch Phase 2, không phải kết quả đã triển khai.
Mục tiêu là để người dùng trả lời được bốn câu hỏi theo đúng thứ tự: **vấn đề có thể là gì, hệ thống tin cậy đến đâu, cần xử lý thế nào và thông tin dựa trên nguồn nào**.

## 1. Nguyên tắc diễn đạt

- Dùng nhãn **“Bệnh nghi ngờ”** trước khi kỹ sư duyệt và **“Bệnh đã xác nhận”** sau khi duyệt; không trình bày dự đoán AI như kết luận tuyệt đối.
- Dùng **“Độ tin cậy của mô hình”**, không dùng “tỷ lệ chính xác”. Confidence là mức tin cậy của dự đoán hiện tại, không phải accuracy đã đo trên toàn bộ tập kiểm thử.
- Tách ba khối theo ba nguồn dữ liệu: nhận diện từ `cvResult`, phác đồ từ `treatmentRule` đã được Backend xác thực và tài liệu tham khảo từ `knowledgeEvidence`.
- Không dùng similarity của RAG làm độ tin cậy chẩn đoán. Nếu hiển thị, phải ghi **“Độ liên quan của tài liệu”** và đặt trong từng mục tham khảo.
- Không suy ra thuốc, liều, phương pháp áp dụng hoặc thời gian cách ly từ đoạn văn RAG.
- Không hiển thị field rỗng, không dùng chuỗi “N/A” khó hiểu. Với dữ liệu quan trọng bị thiếu, dùng thông báo hành động rõ ràng như “Chưa có phác đồ điều trị được xác thực”.

## 2. Thứ tự thông tin

### 2.1. Kết quả nhận diện

Đây là khối đầu tiên và phải đọc được ngay mà không cần mở rộng:

```text
KẾT QUẢ NHẬN DIỆN
Bệnh nghi ngờ: Bệnh đạo ôn trên lúa
Độ tin cậy của mô hình: 92%
Trạng thái: Chờ kỹ sư xác nhận
```

Quy tắc:

- Tên bệnh là thông tin nổi bật nhất; confidence đứng kế tiếp nhưng không lớn hơn tên bệnh.
- Trạng thái nghiệp vụ phải được Việt hóa, ví dụ “Chờ kỹ sư xác nhận”, “Đã xác nhận” hoặc “Đã từ chối”; không dùng trực tiếp `PENDING`, `APPROVED`, `REJECTED` làm nội dung chính cho người dùng.
- Không tự đặt thêm ngưỡng “cao/vừa/thấp”. Chỉ dùng ngưỡng low-confidence và hành vi yêu cầu chụp lại đã được hệ thống cấu hình, tránh tạo một chuẩn đánh giá mới chỉ ở UI.
- Với lớp healthy, dùng “Không phát hiện dấu hiệu thuộc các nhóm bệnh mô hình có thể nhận diện”, không khẳng định cây hoặc toàn bộ ruộng hoàn toàn khỏe.

### 2.2. Phác đồ điều trị đã xác thực

Khối này chỉ lấy dữ liệu từ `treatmentRule` mà Backend đã xác thực:

```text
PHÁC ĐỒ ĐIỀU TRỊ ĐÃ XÁC THỰC
Thuốc khuyến cáo: Newtec® 300SC
Liều lượng: 0,75–1,00 L/ha
Phương pháp áp dụng: Phun khi tỷ lệ bệnh khoảng 5–10%        (nếu có dữ liệu cấu trúc đã xác thực)
Thời gian cách ly: 10 ngày
Nguồn phác đồ: [tên hoặc mã nguồn]
Phiên bản phác đồ: 2026.10.05-expert-approved-v1
```

Quy tắc:

- “Thuốc khuyến cáo”, “Liều lượng” và “Thời gian cách ly” tiếp tục dùng các field có cấu trúc hiện có.
- “Phương pháp áp dụng” là field tùy chọn. Chỉ hiển thị khi contract có field riêng đã được chuyên gia xác thực.
- Trong Phase 2 hiện tại, không parse `TreatmentRule.notes` vì chuỗi này đang trộn hướng dẫn áp dụng, lượng nước, provenance và thông tin phê duyệt. Không lấy phương pháp từ `knowledgeEvidence`.
- Nếu chưa có field phương pháp đã chuẩn hóa thì ẩn riêng dòng này; không ẩn các dữ kiện phác đồ còn lại và không tự sinh nội dung thay thế.
- Nếu không có `treatmentRule`, thay toàn bộ khối bằng thông báo “Chưa có phác đồ điều trị được xác thực” và không hiển thị nút ghi nhận phun.
- Nguồn và phiên bản phải nằm trong chính khối phác đồ để người dùng hiểu chúng bảo chứng cho thuốc/liều/PHI, không phải cho dự đoán ảnh.

Việc chuẩn hóa `applicationMethod` và lượng nước thành field riêng là follow-up ngoài phạm vi Phase 2; format này chừa vị trí để bổ sung sau mà không phải đổi thứ tự thông tin.

### 2.3. Thông tin tham khảo từ tài liệu

Khối này đặt sau phác đồ và có nhãn phân biệt rõ tính chất tham khảo:

```text
THÔNG TIN THAM KHẢO TỪ TÀI LIỆU
Không thay thế kết luận của kỹ sư hoặc phác đồ điều trị đã xác thực.

[1] Quản lý bệnh đạo ôn
Nội dung: [đoạn tài liệu ngắn]
Độ liên quan của tài liệu: 86%
Nguồn tài liệu: [tên tài liệu] · [các nguồn]
```

Quy tắc:

- Mỗi evidence là một mục độc lập gồm tiêu đề tài liệu, nội dung, độ liên quan và nguồn.
- Không dùng màu hoặc từ ngữ khiến similarity trông như mức nguy hiểm hay xác suất mắc bệnh.
- Hiển thị tối đa số evidence mà contract đã giới hạn và giữ thứ tự Backend trả về.
- Khi evidence rỗng hoặc RAG lỗi, ẩn cả khối; kết quả nhận diện và phác đồ vẫn hoạt động.

## 3. Trạng thái ngoại lệ

| Tình huống | Nội dung ưu tiên | Không được làm |
|---|---|---|
| Low confidence / cần chụp lại | “Chưa đủ cơ sở nhận diện. Vui lòng chụp lại ảnh theo hướng dẫn.” | Không hiện thuốc hoặc evidence như một kết luận chắc chắn |
| Chờ duyệt | Tên bệnh nghi ngờ, confidence và “Chờ kỹ sư xác nhận” | Không gọi là “đã xác nhận” |
| Đã duyệt | Tên bệnh đã xác nhận, phác đồ và nguồn | Không bỏ nguồn/version của phác đồ |
| Bị từ chối | Trạng thái và lý do từ chối | Không tiếp tục trình bày phác đồ như khuyến cáo hợp lệ |
| Không có treatment rule | “Chưa có phác đồ điều trị được xác thực” | Không lấy thuốc/liều từ RAG hoặc text cũ |
| Không có evidence | Không dựng khối tham khảo | Không hiện card rỗng hoặc lỗi kỹ thuật cho người dùng |
| Lớp healthy | Giới hạn rõ phạm vi những bệnh mô hình không phát hiện | Không khẳng định cây/ruộng hoàn toàn khỏe |

## 4. Tiêu chí nghiệm thu format

- Trong lần đọc đầu tiên, tên bệnh hoặc thông báo chưa đủ cơ sở phải là thông tin nổi bật nhất.
- Các nhãn “Độ tin cậy của mô hình” và “Độ liên quan của tài liệu” không bị dùng lẫn nhau.
- Thuốc, liều, phương pháp áp dụng và PHI chỉ xuất hiện trong khối phác đồ, từ dữ liệu cấu trúc đã xác thực.
- Nguồn được ghi theo đối tượng: “Nguồn phác đồ” và “Nguồn tài liệu”, không dùng một nhãn “Nguồn” mơ hồ cho toàn màn hình.
- Trạng thái PENDING/APPROVED/REJECTED được trình bày bằng tiếng Việt và giữ nguyên logic phân quyền hiện có.
- Format không làm thay đổi contract hoặc phạm vi Phase 2 chỉ để lấp một field còn thiếu; field tùy chọn được ẩn an toàn.
- Giao diện vẫn đọc được theo đúng thứ tự heading → facts → nguồn bằng bàn phím và screen reader, đồng thời không tràn ở màn hình hẹp.
