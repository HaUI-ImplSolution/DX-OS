# Trade-off và rủi ro: Phase 11

## 1. Quyết định thiết kế

| # | Quyết định | Phương án | Chọn | Được | Mất |
|---|---|---|---|---|---|
| TO1 | Giao dịch xuyên hệ thống | Cố gộp PostgreSQL, Keycloak, Flowable và SeaweedFS vào một transaction | Maintenance gate và retry apply hiện có | Ít code mới, chặn traffic nguy hiểm | Cần operator xử lý apply dở |
| TO2 | Hồ sơ khi model đổi | Tự động dùng rule mới; pin version toàn hệ thống; chặn thay đổi nguy hiểm | Chặn process rule change khi còn hồ sơ mở | Không làm vỡ mọi đường đọc model | Người vận hành phải chờ hồ sơ kết thúc |
| TO3 | Cách ly role/unit | Namespace và migrate token; realm riêng; từ chối collision | Từ chối collision trước | Diff nhỏ, không làm mất membership | Không dùng được tên chung giữa model |
| TO4 | Xóa entity | Drop ngay; cleanup tổng quát; từ chối khi còn dependency | Từ chối khi còn process/file | Không tạo orphan với ít code | Chưa tự động dọn dữ liệu cũ |
| TO5 | Flowable version | Tích hợp migration ngay; đổi version trực tiếp; xác minh riêng | Xác minh capability, chưa gọi trong apply | Không mở rộng scope không cần thiết | Migration thật phải làm phase sau |

## 2. Rủi ro

| # | Rủi ro | Giảm thiểu | Dự phòng |
|---|---|---|---|
| R1 | Schema đã đổi trong khi registry cũ còn phục vụ | Maintenance gate và reload trước `done` | Reconcile hoặc backup/forward recovery |
| R2 | Có hồ sơ mở khi process rule đổi | Chặn thay đổi và báo số hồ sơ | Phase migration riêng |
| R3 | Model hiện có đã trùng role/unit | Audit lúc apply và khóa model bị ảnh hưởng | Migration namespace riêng |
| R4 | Drop bị chặn vì dependency | Hiển thị process/file đang giữ entity | Cleanup phase riêng |
| R5 | Flowable capability trong tài liệu khác source | Test capability độc lập, không đưa vào apply | Không nhận DoD migration |
| R6 | Apply retry tạo side effect lặp | Dùng apply_step hiện có và kiểm trạng thái trước khi chạy | Maintenance/reconcile |
| R7 | Thay đổi schema phá BI dù BI ngoài scope | Không sửa BI nhưng ghi rõ compatibility gate | Phase BI riêng |

## 3. Kết luận về Flowable

Flowable Open Source có `ProcessMigrationService` để validate và migrate process instance giữa các definition version.
Instance mới mặc định dùng definition mới, còn instance đang chạy giữ definition đã bắt đầu.
Phase này chỉ xác minh capability đó; không đưa migration workflow vào apply khi model hiện tại chưa cần.
Flowable không rollback PostgreSQL schema, Keycloak role/group, registry hoặc object storage.
Source hiện tại chưa gọi `ProcessMigrationService`, nên không được ghi nhận là năng lực đã hoàn thiện.

## 4. Nợ kỹ thuật chấp nhận

- Chưa có distributed transaction xuyên các hệ thống ngoài PostgreSQL.
- Người vận hành phải chờ hồ sơ mở kết thúc trước khi đổi process rule.
- Namespace role/unit và cleanup orphan là phase riêng nếu yêu cầu dùng chung hoặc dọn dữ liệu cũ xuất hiện.
