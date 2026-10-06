# Trade-off và rủi ro: Phase 6

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Lớp lọc "không phải lúa/ảnh linh tinh" | Huấn luyện lại model với lớp background mới / Chỉ dựa vào `confidence = 0.0` của fallback hiện có, không huấn luyện lại | Không huấn luyện lại trong phạm vi phase này | Không tốn công thu dữ liệu lớp background + huấn luyện lại (việc AI tốn nhiều thời gian, không khả thi ngắn hạn) | Ảnh không phải lúa vẫn có thể lọt qua nếu người xem không chú ý `confidence = 0.0` — rủi ro gốc chưa giải quyết triệt để, chỉ giảm nhẹ bằng precheck (WP6-01, kiểm mờ/nhỏ, không kiểm "có phải lúa không") | Cao — huấn luyện lại model là việc riêng, cần dữ liệu và thời gian, không làm được "thêm vào" giữa chừng |
| TO2 | Quan hệ `Diagnosis`-`DiseaseReport` | Giữ 1-1 (bắt buộc báo cáo mới mới chẩn đoán lại được) / Đổi sang 1-N (chẩn đoán lại trên cùng báo cáo) | 1-N | Đúng yêu cầu "chẩn đoán lại được" của mục 11, không bắt farmer gửi lại ảnh chỉ vì kỹ sư muốn thử lại | Mọi nơi đang giả định 1-1 phải rà lại (rủi ro bỏ sót, xem R1) | Cao — đổi ngược lại 1-1 sau khi đã có dữ liệu nhiều chẩn đoán/báo cáo sẽ phải chọn 1 để giữ, mất dữ liệu |
| TO3 | Đường nhập tay bỏ điều kiện treatment rule active | Giữ bắt buộc rule (và chấp nhận 404 cho tới khi mục 18 có rule) / Bỏ bắt buộc, cho trạng thái "chờ khuyến nghị" | Bỏ bắt buộc | Đường nhập tay dùng được ngay trên database sạch, không phải chờ mục 18 xong mới demo được | Chẩn đoán "chờ khuyến nghị" không có khuyến nghị điều trị cụ thể cho tới khi có rule — farmer/kỹ sư thấy thiếu thông tin tạm thời | Thấp — khi mục 18 có rule, tự động hết tình trạng "chờ khuyến nghị" cho chẩn đoán mới, không cần đổi code |

Kết luận: TO1-TO3 áp dụng ngay.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Đổi `Diagnosis`-`DiseaseReport` từ 1-1 sang 1-N (WP6-05) bỏ sót một nơi vẫn giả định 1-1, gây lấy sai chẩn đoán (ví dụ lấy chẩn đoán cũ thay vì mới nhất) | Vừa | Cao (sai dữ liệu nghiệp vụ, ảnh hưởng khóa PHI nếu lấy sai `treatmentRule`) | Rà toàn bộ `grep` tham chiếu `Diagnosis`/`diseaseReport` trước khi đổi, không chỉ sửa chỗ biết trước; test T6-01 xác nhận lấy đúng chẩn đoán mới nhất | Khóa PHI hoặc khuyến nghị hiển thị sai sau khi có chẩn đoán lại | Thêm cột `isLatest` rõ ràng thay vì chỉ dựa vào `ORDER BY diagnosedAt`, giảm rủi ro lấy nhầm nếu có nhiều bản ghi cùng thời điểm |
| R2 | Precheck (WP6-01) cho qua ảnh không phải lúa (vì chỉ kiểm mờ/nhỏ, không kiểm nội dung), farmer hiểu nhầm "đã qua precheck" là ảnh chắc chắn dùng được | Vừa | Thấp-Vừa | UI ghi rõ precheck chỉ kiểm độ nét/kích thước, không đảm bảo ảnh đúng là lúa | Tỷ lệ ảnh "linh tinh" vẫn cao sau khi có precheck | Giới hạn đã biết được ghi ở `ai/app/cv/README.md` |

## 3. Nợ kỹ thuật chấp nhận

- Không huấn luyện lại model để có lớp "không phải lúa" (TO1); giới hạn được ghi ở `ai/app/cv/README.md`.
- Mô hình nhận diện sâu hại (ngoài 5 bệnh lá) không làm ở phase này, theo D7 (phase 0).
- Ngưỡng confidence giữ `0.70` theo quyết định của người dùng ngày 06/10/2026.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: R1 xảy ra (phát hiện khóa PHI tính sai sau khi đổi quan hệ 1-N) trong lúc test — dừng ngay, không merge WP6-05 cho tới khi rà xong toàn bộ chỗ tham chiếu.
- Rollback: đổi `Diagnosis.diseaseReport` về `@OneToOne` là migration một chiều khó (mất dữ liệu nếu đã có nhiều chẩn đoán/báo cáo) — ưu tiên sửa tiếp thay vì rollback nếu phát hiện lỗi sau khi đã có dữ liệu thật dùng tính năng chẩn đoán lại.

## 5. Tác động ngoài phase

- Lên các phase sau: P7 (BI) dùng nhãn huấn luyện đã lưu (WP6-04) để làm chart tỷ lệ AI đúng/sai so với kỹ sư chốt.

## Ghi nhận triển khai 06/10/2026

- Bộ dataset, công cụ tính chỉ số và báo cáo đánh giá AI đã được bỏ khỏi phạm vi theo yêu cầu của người dùng.
- Brightness 20–245 là ngưỡng kiểm tra ban đầu.
- Precheck lúc nộp cần AI hoạt động: AI lỗi trả 503 và không nhận ảnh chưa kiểm; hàng đợi mobile có thể retry lỗi dịch vụ.
- P5 chưa có trên nhánh cha; farmer xem yêu cầu chụp lại trong màn báo cáo. Workflow duyệt P3 đã được tích hợp.
- Migration Phase 6 dùng V5 sau V4 workflow; giữ nguyên migration của nhánh cha.
- Nhánh cha chưa seed thuốc/rule được duyệt; chẩn đoán vẫn ghi nhận được nhưng không áp dụng thuốc khi thiếu rule.
- Ghi chẩn đoán/duyệt cùng báo cáo được tuần tự hóa để tránh nhãn trùng; kết quả AI stale trả 409.
