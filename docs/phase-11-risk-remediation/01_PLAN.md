# Plan thực hiện: Phase 11

Thứ tự làm: Spec → Swagger → Code.
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WP11-00 → WP11-01 → WP11-02 → WP11-03 → WP11-04 → WP11-05.
WP11-00 phải chốt hợp đồng dữ liệu trước khi thêm migration.
WP11-03 ưu tiên chặn thay đổi không an toàn; không thêm migration workflow nếu chưa có use case cần thiết.

## WP11-00: Chốt hợp đồng version và trạng thái apply

- Mục tiêu: chốt các guard nhỏ nhất để không tạo trạng thái nửa cũ nửa mới.
- Hướng làm:
  - Dùng trạng thái `apply_run`/`apply_step` hiện có làm maintenance/reconcile signal trước khi thêm state machine mới.
  - Quy định registry chỉ reload sau khi toàn bộ step đã thành công.
  - Quy định thay đổi process rule khi còn hồ sơ mở phải bị từ chối hoặc yêu cầu phase migration riêng.
  - Quy định `LOSS` của `DROP_ENTITY` bị từ chối khi còn dependency runtime hoặc file.

## WP11-01: Chặn collision role và unit

- Mục tiêu: chặn rò quyền mà không phải migrate token và membership ngay trong phase này.
- Hướng làm:
  - Khi apply model, so role/unit với toàn bộ model đã áp dụng.
  - Từ chối tên đã thuộc model khác; không tạo thêm namespace hoặc dual-read token trong phase này.
  - Chạy audit phát hiện collision của dữ liệu hiện có và đưa model bị ảnh hưởng vào trạng thái cần xử lý.
  - Để phase riêng quyết định namespace và migrate membership nếu sau này cần dùng trùng tên.

## WP11-02: Maintenance gate và phục hồi apply

- Mục tiêu: không để request và job tiếp tục dùng registry cũ khi schema đang đổi.
- Hướng làm:
  - Đặt model vào maintenance trong lúc apply; API ghi và các job xử lý model đó phải dừng rõ ràng.
  - Giữ `apply_run`/`apply_step` hiện có làm nguồn theo dõi, chưa thêm active pointer mới.
  - Đổi thứ tự để registry reload thành công trước khi ghi apply `done`.
  - Khi backend khởi động với apply chưa hoàn tất, giữ model ở maintenance và yêu cầu resume/reconcile.
  - Chỉ thêm endpoint resume nếu semantics hiện tại không đủ; không tạo state machine phân tán mới.

## WP11-03: Chặn thay đổi process rule khi còn hồ sơ mở

- Mục tiêu: tránh hai version rule chi phối mà không phải thay đổi mọi đường đọc model.
- Hướng làm:
  - Khi `openRecords` khác rỗng, nâng thay đổi handler, deadline, completion, booking hoặc process rule lên migration/rejected.
  - Không cho apply thường ngày đổi các rule đó khi còn hồ sơ đang chờ.
  - Giữ Flowable definition và process variables hiện tại cho tới khi hồ sơ kết thúc.
  - Chỉ tích hợp `ProcessMigrationService` trong phase riêng khi có yêu cầu migrate thực tế; trước mắt chỉ xác minh capability bằng test nhỏ.
  - Không đổi version process trực tiếp trong database.

## WP11-04: Chặn DROP_ENTITY có dependency

- Mục tiêu: không tạo orphan mà chưa phải xây cleanup engine tổng quát.
- Hướng làm:
  - Trước `DROP_ENTITY`, kiểm tra process instance và attachment rows theo model/entity.
  - Từ chối drop nếu còn một trong hai loại dependency.
  - Giữ nguyên history/audit/outbox cho tới khi có chính sách cleanup riêng.
  - Để việc hủy process và dọn object storage thành phase riêng, tránh mở rộng phạm vi apply hiện tại.

## WP11-05: Kiểm chứng và tài liệu

- Mục tiêu: chứng minh các failure mode đã được xử lý mà không thay đổi phạm vi BI.
- Hướng làm:
  - Chạy các kịch bản trong [`03_TEST_PLAN.md`](./03_TEST_PLAN.md).
  - Ghi trade-off và giới hạn trong [`04_TRADEOFFS_RISKS.md`](./04_TRADEOFFS_RISKS.md).
  - Cập nhật Swagger, ARCHITECTURE và BACKLOG sau khi code đã được review.
  - Không đánh dấu DoD chỉ vì unit test xanh; phải có bằng chứng maintenance gate, collision guard và dependency guard.

## Thay đổi hợp đồng dự kiến

| Thành phần | Thay đổi | Phá tương thích |
|---|---|---|
| Apply API | Có maintenance/reconcile rõ ràng; chỉ thêm resume khi cần | Có thể có |
| Process rule | Thay đổi khi còn hồ sơ mở bị từ chối hoặc nâng mức rủi ro | Có hành vi chặn mới |
| Keycloak | Tên role/unit trùng model bị từ chối | Có hành vi chặn mới |
| Drop entity | Có dependency runtime/file thì bị từ chối | Có hành vi chặn mới |
