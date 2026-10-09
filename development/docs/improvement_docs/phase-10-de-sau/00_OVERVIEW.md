# Phase 10: Để sau

| Mục | Giá trị |
|---|---|
| Trạng thái | Backlog (không có việc chủ động) |
| Mức độ | Theo dõi |
| Nhóm ưu tiên | C |
| Ước lượng | Không tính |
| Người phụ trách | Không áp dụng |
| Bắt đầu, dự kiến xong | Không áp dụng |
| Phụ thuộc | Không |

Phase này không có `01_PLAN.md`/`02_CHECKLIST.md`/`03_TEST_PLAN.md` — đây là danh sách backlog, không phải kế hoạch thi công.
Tạo WP/checklist/test case cho việc chưa quyết định làm là việc thừa, không có giá trị tham chiếu.
Khi một mục dưới đây được quyết định đưa vào làm, tạo phase mới riêng từ `development/docs/_template/`, không mở lại phase này.

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 24 | Để sau (chưa đưa vào phase gần) | Theo dõi | Toàn bộ |
| mục 15 | Mobile, phần hoàn thiện chưa xếp vào phase nào | Theo dõi | Một phần |

## 2. Danh sách backlog

| # | Việc | Vì sao để sau | Điều kiện để đưa vào làm |
|---|---|---|---|
| 1 | Carbon/AWD Estimator, IoT mô phỏng mực nước, tự xác nhận phun từ IoT | Giai đoạn 3 theo README gốc, cần dữ liệu sản lượng/diện tích thật từ P5 trước khi tính được gì có ý nghĩa | Có đủ dữ liệu thu hoạch thật qua ít nhất 1 vụ hoàn chỉnh |
| 2 | Frontend low-code (ToolJet/Appsmith) | Xung đột hướng với trang quản lý tự viết ở P4 — làm cả hai là hai hướng song song không cần thiết | Chỉ xét lại nếu quyết định bỏ hẳn trang quản lý tự viết |
| 3 | GraphRAG / multi-agent | Không cần cho quy mô hiện tại, RAG đơn giản (P6/P7) đã đáp ứng yêu cầu "trả lời có trích nguồn" | Khi corpus tri thức lớn hơn đáng kể, RAG đơn giản không còn đủ |
| 4 | Polygon ruộng + PostGIS | GPS điểm đơn (P4) đã đủ cho yêu cầu định vị hiện tại; polygon là nâng cấp độ chính xác, không phải yêu cầu bắt buộc | Khi cần tính diện tích chính xác từ bản đồ thay vì nhập tay |
| 5 | Mattermost tương tác hai chiều (duyệt/từ chối ngay trong chat) | Đã chốt không làm (quyết định cũ, giữ nguyên) | Không có điều kiện mở lại trong phạm vi dự án này |
| 6 | Push thật (FCM, UnifiedPush/ntfy) | D5 (phase 0) đã chọn hộp thông báo trong app (pull) cho MVP, đủ dùng cho quy mô demo | Khi cần thông báo tức thời thật, ngoài phạm vi "mở app mới thấy" |
| 7 | Icon và splash mobile vẫn là ảnh mặc định của Expo | Không ảnh hưởng chức năng, chỉ ảnh hưởng hình thức | Bất kỳ lúc nào có người làm UI rảnh, không cần điều kiện kỹ thuật |
| 8 | Domain + HTTPS cho APK phát hành, địa chỉ backend/Keycloak không còn hard-code trong APK | Ngoài phạm vi "demo cho hội đồng", chỉ cần khi phát hành thật cho nông dân | Khi có domain thật và quyết định phát hành ngoài phạm vi cuộc thi |
| 9 | Scale thật (100 → 100.000 người dùng) | Câu trả lời cho câu hỏi hội đồng (mục 20) chỉ cần nói được hướng scale, không cần triển khai thật trong 14 ngày | Ngoài phạm vi cuộc thi, chỉ cần khi vận hành thật |

## 3. Câu trả lời chuẩn bị sẵn nếu hội đồng hỏi

- "Vì sao chưa làm carbon/IoT?": dữ liệu sản lượng thật mới có từ P5, chưa đủ một vụ hoàn chỉnh để tính toán có ý nghĩa; đã chuẩn bị trường dữ liệu (P5 WP5-01) để làm được ngay khi có đủ dữ liệu.
- "Vì sao không polygon ruộng?": GPS điểm đơn đã đủ cho yêu cầu định vị và hiển thị bản đồ của đề; polygon là nâng cấp độ chính xác diện tích, không phải điều kiện bắt buộc.
- "Scale 100 → 100.000?": backend stateless (Spring Boot, không giữ session), Postgres có thể cụm/đọc-replica, Keycloak cụm được, AI đã tách qua gateway (P3) nên scale riêng được, object storage (MinIO, P3) không giới hạn như filesystem local, đã phân trang (P4) và có index qua Flyway migration; điểm nghẽn còn lại đã biết: SQLite của Superset metadata (không scale, nhưng chỉ ảnh hưởng BI không ảnh hưởng nghiệp vụ chính).

## 4. Tài liệu cần cập nhật khi xong

Không áp dụng — phase này không "xong", chỉ cập nhật khi một mục được chuyển sang phase thi công thật.
