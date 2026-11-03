# Phase 11: Khắc phục rủi ro khi đổi mô hình đang chạy

| Mục | Giá trị |
|---|---|
| Trạng thái | Kế hoạch |
| Mức độ | Nghiêm trọng |
| Ứng dụng và module chính | `dx-model`, `dx-core`, `dx-engine`, Keycloak, PostgreSQL, Flowable, SeaweedFS |
| Phụ thuộc | Phase 6, Phase 7, Phase 8, Phase 9, PR #182 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Phạm vi trong phase này |
|---|---|---|
| [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md), mục “Rủi ro kỹ thuật hiện tại” | Kích hoạt phiên bản không nguyên tử; hồ sơ đang chạy không gắn phiên bản; xóa thực thể để lại tài nguyên; cách ly role và unit | Toàn bộ, trừ quyền đọc BI |
| [`docs/dynamic-core/README.md`](../dynamic-core/README.md), mục “Đổi mô hình” | Áp dụng theo kế hoạch, chạy lại được hoặc quay lui; Flowable giữ và migrate process definition | Kiểm tra lại theo source hiện tại |
| PR [#182](https://github.com/HaUI-ImplSolution/DX-OS/pull/182) | Bốn failure mode được ghi nhận trong backlog | Chuyển thành hạng mục triển khai có tiêu chí nghiệm thu |
| Flowable `ProcessMigrationService` | Validate và migrate process instance giữa các definition version | Chỉ xác minh capability; chưa đưa vào apply thường ngày |

## 2. Lý do

PR #182 ghi nhận các trạng thái có thể làm model, schema, workflow và quyền không cùng một phiên bản.
Source hiện tại đã có lịch sử apply và Flowable embedded, nhưng chưa có active-version pointer, model-version pinning hoặc tích hợp `ProcessMigrationService`.
Phase này phải bảo vệ hồ sơ đang xử lý dở và không làm rò quyền giữa các model, với thay đổi nhỏ nhất có thể.

## 3. Phạm vi

- Trong phạm vi:
  - Chặn collision role và unit giữa các model đã áp dụng.
  - Đưa model vào maintenance trong lúc apply và chỉ xác nhận hoàn tất sau khi registry reload thành công.
  - Chặn thay đổi luật process khi còn hồ sơ đang chạy thay vì thêm hệ thống pin/migrate mới.
  - Chặn `DROP_ENTITY` khi còn process hoặc attachment phụ thuộc.
  - Cập nhật audit, Swagger và tài liệu vận hành cần thiết.
- Ngoài phạm vi:
  - Quyền đọc báo cáo `report_ro` và tích hợp BI.
  - Pin version cho mọi hồ sơ và tự động migrate hồ sơ đang chạy.
  - Namespace và migrate toàn bộ role/group Keycloak hiện có.
  - Tự động dọn process, attachment và object storage khi xóa entity.
  - Transaction nguyên tử xuyên PostgreSQL, Keycloak, Flowable và SeaweedFS.
  - Tên role, unit hoặc workflow đặc thù của một miền nghiệp vụ.
- Ranh giới:
  - Model vẫn là nguồn sự thật; core không chứa tên nghiệp vụ.
  - Hồ sơ mới dùng model active; hồ sơ cũ giữ version cho tới khi được migrate explicit.

## 4. Kết quả đầu ra mong đợi (Definition of Done)

- Hai model có cùng tên role hoặc unit không thể dùng chung quyền, dữ liệu hoặc danh sách handler.
- Apply bị lỗi không ghi active version thành công dở dang và có thể resume hoặc reconcile rõ ràng.
- Thay đổi handler, deadline hoặc completion bị chặn khi còn hồ sơ đang chạy, nên không tạo trạng thái hai version rule.
- Flowable migration là năng lực được xác minh riêng, chưa được đưa vào apply thường ngày.
- `DROP_ENTITY` bị chặn trước khi tạo process hoặc attachment mồ côi.
- Mọi failure mode có checklist, test plan và tài liệu vận hành; phần BI không bị thay đổi.
