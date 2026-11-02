# Phase 6: Hồ sơ là khái niệm hạng nhất

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong trên máy dev, chờ commit và CI |
| Mức độ | Nghiêm trọng |
| Ứng dụng và module chính | `dx-model`, `dx-core`, `dx-engine`, frontend, `sso` |
| Ước lượng | 7 ngày |
| Phụ thuộc | Phase 5 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Phạm vi trong phase này |
|---|---|---|
| [`docs/dynamic-core/README.md`](../dynamic-core/README.md), mục "Miền phục vụ" | Hồ sơ gồm người nộp, người xử lý, hộp việc, hạn, minh chứng, yêu cầu bổ sung, quyết định, dòng lịch sử | Trừ hạn và minh chứng, thuộc Phase 8 |
| Cùng tài liệu, mục "Thế nào là đạt" | Mục 2 (quyền theo đơn vị, hộp việc) và mục 8 (yêu cầu bổ sung) | Toàn bộ |
| Cùng tài liệu, mục "Chưa xác minh" | Khả năng đổi tên trường của pgschema, pgroll | Thử và ghi kết luận cho Phase 7 |

## 2. Lý do

- Lõi hiện chỉ có thực thể, CRUD và một nút duyệt, nên chưa khác các nền tảng dữ liệu chung.
- Đổi mô hình ở Phase 7 phải biết các khái niệm hồ sơ cần chuyển đổi, nên các khái niệm này làm trước.
- Rủi ro của đổi mô hình được thử song song ngay đầu phase để kết quả kịp chi phối thiết kế.

## 3. Phạm vi

- Trong phạm vi:
  - Mẫu quy trình khai báo trạng thái và hành động; API hành động dùng chung.
  - Yêu cầu bổ sung: cán bộ trả lại kèm ghi chú, người nộp sửa và nộp lại.
  - Đơn vị khai báo trong mô hình, đồng bộ thành nhóm Keycloak; quyền `unit` và `assigned`.
  - Giao việc, nhận việc, hộp việc.
  - Dòng lịch sử của mọi bản ghi, trong cùng giao dịch với thay đổi.
  - Giao diện dựng các phần trên từ metadata.
  - Thử `ProcessMigrationService`, pgschema và pgroll.
- Ngoài phạm vi:
  - So sánh phiên bản và đổi mô hình khi có dữ liệu: Phase 7.
  - Tệp đính kèm, mẫu xử lý có hạn, trục sự kiện, hành động hoàn tất: Phase 8.
- Ranh giới:
  - Mã lõi không chứa tên thực thể, vai, đơn vị của một lĩnh vực.
  - `dx-model` vẫn không phụ thuộc module nào khác; người dùng không đụng tới BPMN.

## 4. Kết quả đầu ra mong đợi (Definition of Done)

- Áp dụng `models/onestop.yaml` vào hệ thống rỗng thì có đơn vị, vai, bảng và màn hình, không sửa mã lõi.
- Sinh viên nộp hồ sơ cho một đơn vị; cán bộ đơn vị khác không thấy, cán bộ đúng đơn vị thấy trong hộp việc.
- Cán bộ nhận việc, trả lại kèm ghi chú; sinh viên sửa, nộp lại; cán bộ duyệt.
- Hồ sơ đã giao cho người khác thì cán bộ còn lại không xử lý được.
- Dòng lịch sử cho biết ai làm gì, khi nào, từ trạng thái nào sang trạng thái nào, kể cả sau khi bản ghi bị xóa.
- Mọi case Phase 5 vẫn đạt; `./mvnw clean verify`, `npx tsc --noEmit`, CI đạt.
- Có kết luận ghi lại cho công cụ đổi mô hình ở Phase 7.
