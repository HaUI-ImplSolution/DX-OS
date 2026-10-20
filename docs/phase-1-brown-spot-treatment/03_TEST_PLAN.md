# Kế hoạch kiểm thử: Phase 1

Kết quả tự động cập nhật ngày 20/10/2026; các case kiểm tay chưa chạy được ghi rõ bên dưới.
Mặc định chỉ đọc diff/compile theo `_template`; test, gọi API và chạy app chỉ thực hiện khi người dùng yêu cầu rõ.
Không dựng nguyên stack Docker mới; ưu tiên môi trường đang có hoặc CI hiện tại.

## 1. Chiến lược

Kiểm tra dữ liệu trước, sau đó lookup và luồng áp dụng hiện có khi được yêu cầu chạy thử.
Dùng bệnh đốm nâu được chỉ định trong fixture/chẩn đoán thủ công để kiểm tra rule độc lập với độ chính xác CV.
Kiểm tra AI chọn đúng rule bằng context/fixture sẵn có; không huấn luyện, hạ ngưỡng hoặc tải thêm model/ảnh.
Dùng vai engineer để duyệt, farmer để khai phun và người có quyền xác nhận theo phân quyền hiện hành.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T1-01 | WP1-01 | Hồ sơ | Đối chiếu sản phẩm/đối tượng, ADC và quyết định duyệt | Đủ căn cứ; thiếu thì giữ eligibility false | Codex, 20/10/2026 | Đã đối chiếu TT75 trang PDF 152, phụ lục TT28 và ADC; chủ dự án yêu cầu gán EXPERT_APPROVED, quyết định ghi trong seed_authorization |
| T1-02 | WP1-02 | Seed | Generator `--check` và bộ `test_generate_seed_sql` đã cập nhật | JSON/SQL khớp; đúng 2 thuốc, 3 rule, 6 lớp bệnh | Codex, 20/10/2026 | Đạt: 7 test dữ liệu, generator --check và SeedDataIntegrationTest nạp seed hai lần; 3 rule không trùng |
| T1-03 | WP1-02 | Lookup | Lấy context `Rice___Brown_spot` và danh sách rule qua API hiện có | Help 400SC, 0,6 L/ha, PHI 14; lượng nước/thời điểm đúng trong notes của rule | Codex, 20/10/2026 | Đạt qua InternalAiContextService và DiagnosisResponse trong test tích hợp; chưa gọi HTTP trực tiếp |
| T1-04 | WP1-02 | Chẩn đoán | Tạo chẩn đoán đốm nâu mới qua luồng hiện có | Rule từ Backend, không có liều do AI sinh; vẫn chờ kỹ sư duyệt | Codex, 20/10/2026 | Đạt qua luồng createFromAi với phản hồi AI giả lập; chẩn đoán PENDING và rule lấy từ DB |
| T1-05 | WP1-02 | Áp dụng | Duyệt chẩn đoán, khai phun đúng liều, xác nhận | Lưu đúng thuốc, liều và snapshot PHI 14 ngày | Codex, 20/10/2026 | Đạt: selfReport rồi confirmApplication, đúng 0,6 L/ha, PHI 14 và ngày khóa |
| T1-06 | WP1-02 | Âm tính | Thử liều ngoài 0,6 L/ha hoặc áp dụng khi chưa được duyệt | Bị chặn theo luật hiện có; không bỏ kiểm tra để làm test qua | Codex, 20/10/2026 | Đạt: chặn trước duyệt, chặn 0,61 L/ha; unit test kiểm tra khoảng liều |
| T1-07 | WP1-02 | PHI | Thử thu hoạch khi còn cách ly và sau hạn khóa | Còn khóa trả 422; hết hạn xử lý theo luật cũ, không đổi cách tính ngày | Codex, 20/10/2026 | Đạt: test tích hợp chặn thu hoạch khi khóa còn hiệu lực; unit CropSeasonServiceTest kiểm tra hai phía hạn khóa |
| T1-08 | WP1-02 | Hồi quy | Đối chiếu hai rule Newtec và lookup Tungro/than lá/lá khỏe | Newtec giữ nguyên; ba lớp còn lại không có rule mới | Codex, 20/10/2026 | Đạt: đối chiếu JSON Newtec nguyên vẹn, policy/CV/corpus không đổi; các nhánh AI được kiểm tra riêng bằng fixture |
| T1-09 | WP1-02 | Dữ liệu cũ | Nạp seed lặp lại; đọc diagnosis và bản khai phun cũ | Không trùng rule, không tự gán lại diagnosis cũ, không đổi snapshot PHI | Codex, 20/10/2026 | Đạt: seed lặp không trùng, PHI snapshot bản khai phun giữ nguyên; test lịch sử chẩn đoán hiện có đạt |
| T1-10 | WP1-03 | Nguồn | Kiểm tra bài tiếng Việt đã chọn bằng trình duyệt và truy cập trực tiếp | Nội dung đúng bệnh; không chỉ HTTP 200 của trang chống bot | Codex, 20/10/2026 | Đạt cho bài Tuyên Quang, Quảng Trị và rầy xanh đuôi đen ở WP1-03; cần kiểm tra lại lúc triển khai |
| T1-11 | WP1-03 | Metadata | Bộ `data.scripts.test_knowledge_sources` và đọc diff danh mục Farm | ADC và nguồn mới đồng bộ; corpus và dữ liệu Lab không đổi | Codex, 20/10/2026 | Đạt: metadata nguồn và seed đồng bộ; corpus và dữ liệu Lab không đổi |
| T1-12 | WP1-04 | Giao diện | Mở chẩn đoán đốm nâu mới trên web/điện thoại hiện có | Phác đồ dùng dữ liệu mới; không đòi thêm cột nước/thời điểm vào UI | Chưa chạy | Chờ |
| T1-13 | WP1-04 | Khởi động | Khi được yêu cầu, dùng lệnh README trên dữ liệu sạch và dữ liệu đang có | Seed mới nạp theo luồng cũ; không thêm lệnh/env/bước tải riêng | Chưa chạy | Chờ |

Lệnh đã dùng để kiểm tra dữ liệu:

```bash
python3 data/scripts/generate_seed_sql.py --check
python3 -m unittest data.scripts.test_generate_seed_sql data.scripts.test_knowledge_sources
```

Ngày 20/10/2026: generator `--check` và 7 test dữ liệu đạt với ba rule.
Ba lớp test `DiagnosisServiceApplyTest`, `TreatmentRuleTest`, `CropSeasonServiceTest` đạt 9 test.
Hai lớp `SeedDataIntegrationTest`, `ReportDiagnosisIntegrationTest` đạt 15 test trên PostgreSQL 16; test đốm nâu dùng phản hồi CV/AI giả lập, không đánh giá lại model.
Maven chạy offline trong image Maven/JDK 21 có sẵn, giới hạn CPU/RAM; PostgreSQL dùng image pgvector/pg16 có sẵn qua alias tạm, không sửa test image mặc định của CI.
Reactor sáu module compile thành công; lỗi compiler của môi trường host được tránh bằng công cụ test có sẵn, không sửa mã/cấu hình sản phẩm.
Đã kiểm thêm 7 tình huống qua orchestration AI thật với CV/Backend/RAG giả lập: sáu lớp đi đúng nhánh, đốm nâu trả đúng rule/0,6 L/ha/PHI 14, vẫn yêu cầu kỹ sư duyệt và ngưỡng 0,7 chặn kết quả 0,69.
Không đánh giá lại trọng số hoặc ảnh bệnh trong các tình huống này.
Không dựng toàn stack; T1-12 và T1-13 chưa thực hiện nên phase chưa đóng.

## 3. Hồi quy

- Hai rule Newtec, phân quyền, bước duyệt, khai phun và khóa PHI giữ hành vi hiện có.
- Không mở rule cho Tungro, than lá hoặc lá khỏe.
- Namespace, dữ liệu và code Lab không đổi; không bật Lab chỉ để kiểm tra thay đổi dữ liệu Farm.
- API/DTO, xử lý đăng nhập, model và ngưỡng nhận diện không đổi.
- Không thay pipeline CI hoặc thêm thư mục test trong deploy.

## 4. Tiêu chí đạt

Hồ sơ duyệt phải có thật trước kích hoạt seed.
Các case dữ liệu, lookup, khai phun, PHI, hồi quy và dữ liệu cũ phải có kết quả kiểm chứng trước khi đóng phase.
Case chưa chạy vẫn ghi Chờ; lỗi ngoài phạm vi ghi nhận riêng, không sửa hoặc coi là đạt.

## 5. Bằng chứng và báo lỗi

Ghi ngày/người chạy và kết quả ngắn trong bảng; chỉ giữ bằng chứng cần thiết.
Không commit token đăng nhập, presigned URL, dữ liệu người thật, dump DB hoặc log chứa thông tin nhạy cảm.
Không tạo bộ script test mới trong `deploy/demo`.
