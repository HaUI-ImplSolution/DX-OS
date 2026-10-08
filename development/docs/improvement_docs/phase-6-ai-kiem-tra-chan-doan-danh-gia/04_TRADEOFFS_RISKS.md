# Trade-off và rủi ro: Phase 6

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Lớp lọc "không phải lúa/ảnh linh tinh" | Huấn luyện lại model với lớp background / Chỉ dựa vào `confidence = 0.0` của fallback, không huấn luyện lại | Không huấn luyện lại | Không tốn công thu dữ liệu và huấn luyện lại | Ảnh không phải lúa vẫn có thể lọt qua; precheck chỉ kiểm chất lượng, không kiểm nội dung; fallback confidence 0 không chứng minh cây khỏe | Cao: huấn luyện lại là việc riêng, cần dữ liệu và thời gian |
| TO2 | Quan hệ `Diagnosis`-`DiseaseReport` | Giữ 1-1 / Đổi sang 1-N | 1-N, bản có `id` lớn nhất là hiện hành | Chạy lại chẩn đoán trên cùng báo cáo, không bắt farmer gửi lại ảnh | Mọi nơi giả định 1-1 phải rà lại (R1) | Cao: quay lại 1-1 sau khi có nhiều chẩn đoán/báo cáo phải chọn một bản để giữ |
| TO3 | Đường nhập tay khi thiếu treatment rule | Giữ bắt buộc rule (404) / Cho lưu "chờ khuyến nghị" | Cho lưu | Dùng được ngay trên database sạch | Không có khuyến nghị điều trị, không cho ghi nhận phun cho tới khi có rule | Thấp: có rule thì chẩn đoán mới tự có khuyến nghị |
| TO4 | Kiểm chất lượng ảnh khi nộp báo cáo | Từ chối nếu AI không chạy / Nhận ảnh chưa kiểm khi AI không chạy | Từ chối (503), hàng đợi offline thử lại | Không nhận ảnh chưa kiểm | Farmer không nộp được báo cáo khi AI service dừng | Thấp: đổi thành nhận và đánh dấu `quality_passed = NULL` |
| TO5 | Nhãn huấn luyện của kỹ sư | Ghi khi nhập chẩn đoán / Ghi khi duyệt | Ghi khi duyệt | Nhãn chỉ phản ánh điều kỹ sư đã chốt; chẩn đoán bị từ chối hoặc bị thay không làm nhiễu | Phải lưu `symptomText` trên chẩn đoán cho tới lúc duyệt | Thấp |
| TO6 | Độ nét của ảnh | Phương sai Laplacian đơn giản / Chuẩn hóa độ phân giải, trung vị theo vùng, trừ nhiễu | Chuẩn hóa và trung vị theo vùng | Không bị đánh lừa bởi sọc màn hình, nhiễu, chữ và viền sắc | Thuật toán phức tạp hơn, ngưỡng 20 chưa hiệu chuẩn bằng ảnh ngoài ruộng (R3) | Thấp: đổi thuật toán hoặc ngưỡng ở `ai/app/cv/policy.py`, `plant_disease_service.py` |

Kết luận: TO1-TO6 áp dụng ngay.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Đổi 1-1 sang 1-N bỏ sót một nơi vẫn giả định 1-1, lấy nhầm chẩn đoán cũ | Vừa | Cao (sai khuyến nghị, ảnh hưởng khóa PHI) | Rà mọi tham chiếu `Diagnosis`/`diseaseReport`; lấy hiện hành theo `id`; khóa PHI tính trên mọi lần phun; T6-01 và hồi quy PHI | Khuyến nghị hoặc khóa PHI sai sau khi chẩn đoán lại | Thêm cột `is_latest` thay vì chỉ dựa vào `id` |
| R2 | Farmer hiểu "đã qua precheck" là ảnh chắc chắn là lúa | Vừa | Thấp-Vừa | UI ghi rõ precheck chỉ kiểm chất lượng; kỹ sư chốt bệnh | Tỷ lệ ảnh không phải lúa vẫn cao | Ghi là giới hạn đã biết trong báo cáo đánh giá AI |
| R3 | Ngưỡng độ nét 20 và độ sáng 20–245 chặn nhầm ảnh tốt hoặc cho lọt ảnh mờ ngoài ruộng | Vừa | Vừa | Hiệu chuẩn bằng ảnh thật khi có; server là nơi quyết định, ngưỡng nằm ở một chỗ | Farmer báo ảnh rõ bị từ chối | Hạ ngưỡng hoặc chuyển sang cảnh báo thay vì chặn |
| R4 | AI service dừng làm farmer không nộp được báo cáo (TO4) | Thấp-Vừa | Vừa | Hàng đợi offline thử lại lỗi dịch vụ; báo lỗi 503 rõ ràng | Nhiều báo cáo ở trạng thái "Lỗi" hoặc chờ gửi | Chuyển TO4 sang nhận ảnh chưa kiểm |

## 3. Nợ kỹ thuật chấp nhận

- Không huấn luyện lại model để có lớp "không phải lúa" (TO1); ghi rõ là giới hạn khi trả lời hội đồng.
- Mô hình nhận diện sâu hại (ngoài 5 bệnh lá) không làm, theo D7.
- Không có báo cáo đánh giá AI (test set độc lập, chỉ số theo lớp, confusion matrix): bỏ khỏi phase, nên không có số liệu độ chính xác của model và ngưỡng `0.70` không được đo lại.
- Ngưỡng độ nét 20, độ sáng 20–245 chưa hiệu chuẩn bằng ảnh ngoài ruộng (R3); điểm độ nét cũ không so sánh trực tiếp với điểm mới và không tự đổi trạng thái báo cáo đã lưu.
- Precheck không tách vùng lá: nền sắc hoặc chủ thể ngoài trung tâm có thể làm sai điểm nét.
- Nộp báo cáo cần AI service chạy (TO4); chưa có đường nhận ảnh chưa kiểm khi AI dừng.
- Yêu cầu chụp lại (thông báo, hủy workflow chờ, chặn khi đã `APPROVED`) chưa có test tự động; đã kiểm tay trên giao diện web và điện thoại.
- Chưa có seed thuốc và treatment rule được duyệt: chẩn đoán thiếu rule vẫn lưu nhưng không áp dụng thuốc; demo khóa PHI thật cần phác đồ do chuyên gia duyệt (P8, Q2).
- Báo cáo `PROCESSED` nghĩa là đã có chẩn đoán, kể cả chẩn đoán đang chờ duyệt.
- Nhãn kỹ sư chỉ có cho chẩn đoán đã duyệt; chart tỷ lệ AI đúng/sai thuộc P7.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi phát hiện sai chẩn đoán hiện hành hoặc sai khóa PHI: không merge cho tới khi rà xong mọi chỗ tham chiếu và chạy lại hồi quy.
- Rollback: đổi `Diagnosis.diseaseReport` về `@OneToOne` là migration một chiều khó (mất dữ liệu nếu đã có nhiều chẩn đoán/báo cáo); ưu tiên sửa tiếp, không thêm lại unique 1-1 khi còn báo cáo có nhiều chẩn đoán.

## 5. Tác động ngoài phase

- Lên các phase sau: P7 (BI) dùng `training_labels` để làm chart tỷ lệ AI đúng/sai so với kỹ sư chốt.
- Lên demo, tài liệu nộp bài, thời hạn: không có số liệu đánh giá AI để đưa vào hồ sơ; khi trả lời hội đồng về AI phải nêu rõ các giới hạn ở mục 3.
