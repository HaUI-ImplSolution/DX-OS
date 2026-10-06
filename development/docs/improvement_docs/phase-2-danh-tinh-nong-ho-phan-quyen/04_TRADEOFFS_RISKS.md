# Trade-off và rủi ro: Phase 2

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Q1 — ghép dữ liệu `farmerName` cũ | Fuzzy-match tự động / tạo hộ riêng cho mỗi tên lạ | Tạo hộ riêng | Không ghép sai người; đơn giản, dự đoán được | Có thể tạo dư hộ nếu 1 người thật bị đánh máy tên khác nhau ở nhiều thửa cũ; admin phải gộp tay | Vừa — gộp 2 hộ thành 1 sau này cần chuyển `parcel.household_id`, không khó nhưng phải làm tay |
| TO2 | Đưa Flyway vào giữa dự án | Flyway từ đầu phase này / tiếp tục `ddl-auto`, hoãn Flyway sang sau | Flyway từ phase này | Có migration thật, cần cho PoF (đề mục 10 "quản lý thư viện/migration" gián tiếp); an toàn hơn khi P4 thêm nhiều bảng | Tốn công viết baseline đúng với schema thật đang chạy, rủi ro baseline sai lệch với DB thật nếu không cẩn thận | Cao — nếu baseline sai, mọi migration sau sai theo, phải làm lại từ đầu |
| TO3 | Giữ cột `farmerName` trên `Parcel` song song với `householdId` | Xóa luôn `farmerName` / giữ tạm | Giữ tạm, dọn ở phase sau | An toàn hơn, không phá code/test cũ còn đọc `farmerName`, có đường lùi nếu migrate sai | Tạo 2 nguồn sự thật tạm thời (`farmerName` và `household.fullName` có thể lệch nếu không đồng bộ) | Thấp — xóa cột là việc độc lập, làm bất cứ lúc nào sau khi xác nhận không còn chỗ đọc |

Kết luận: TO1-TO3 đã chốt cùng Q1 ở `00_OVERVIEW.md`, áp dụng ngay.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Bật kiểm `aud` (WP2-03) khóa nhầm mobile nếu mobile và web đang dùng chung client Keycloak | Vừa | Cao (mobile farmer không đăng nhập được) | WP2-03-DOC-01 xác nhận cấu hình client trước khi code; test T2-08 chạy trên mobile thật trước khi coi WP2-03 xong | Mobile báo lỗi 401 ngay sau khi đăng nhập, trước đây không có | Tắt validator `aud` tạm thời (feature flag hoặc revert riêng commit đó), không rollback toàn bộ phase |
| R2 | Flyway baseline (TO2) không khớp 100% schema thật đang chạy (ví dụ cột/index tạo tay ngoài JPA) | Vừa | Cao (migration sau áp lên baseline sai, lỗi khó debug) | Sinh baseline bằng cách đối chiếu `pg_dump --schema-only` của DB đang chạy thật, không chỉ dựa vào entity JPA | Migration thất bại trên DB đã có dữ liệu (không phải DB mới) | Dựng DB mới từ baseline, so `pg_dump --schema-only` với DB cũ, vá thủ công chỗ lệch trước khi áp dụng cho ai khác |
| R3 | Script ghép `farmerName` → `Household` (WP2-01-DATA-03) chạy nhầm trên dữ liệu đã có người dùng thật (không phải seed), tạo hộ trùng hoặc sai | Thấp (ở giai đoạn demo, dữ liệu chủ yếu là seed) | Cao nếu xảy ra | Chạy thử trên bản sao DB trước, không chạy trực tiếp trên dữ liệu "thật" nếu có | Số lượng `Household` tạo ra nhiều bất thường so với số `farmerName` phân biệt dự kiến | Có log đầy đủ ánh xạ `farmerName` → `household.id` để truy ngược và gộp/sửa tay |
| R4 | Thêm `@PreAuthorize` (WP2-02) chặn luôn một luồng nào đó đang được test/demo dựa vào việc "engineer tạo thửa được" (dù sai theo đề) | Thấp | Thấp | Rà lại mọi script/test/seed hiện có có giả định engineer tạo thửa trước khi merge | Seed demo hoặc E2E cũ lỗi 403 bất ngờ sau khi merge WP2-02 | Sửa seed/test dùng đúng role ADMIN hoặc FARMER để tạo thửa, không mở lại quyền cho engineer |

## 2b. Kết quả thực tế của các rủi ro

- R1: kiểm bằng luồng client web và mobile với token thật: farmer qua web nhận 403, qua mobile vào bình thường. Chưa thử trên điện thoại thật.
- R2: baseline sinh từ chính schema Hibernate; Hibernate `validate` trong test và trong ứng dụng chạy thật xác nhận entity khớp schema.
- R3: ghép tên chạy trên dữ liệu thử có tên viết hoa khác nhau, kết quả đúng; hộ chưa có tài khoản được admin gắn tay qua `PATCH .../account`.
- R4: kịch bản E2E của phase 1 vẫn đúng nếu dùng token farmer để gửi báo cáo; seed và test dùng token admin/engineer để tạo thửa phải đổi.

## 3. Nợ kỹ thuật chấp nhận

- Cột `farmerName` trên `Parcel` chưa xóa ngay (TO3) — dọn ở phase sau khi xác nhận không còn chỗ đọc.
- Hộ "mồ côi" (không có `keycloakUserId` khớp) sau khi ghép dữ liệu cũ (TO1) — admin xử lý tay, không tự động gộp.
- `reportedByUserId`/`decidedByUserId` của dữ liệu cũ là `NULL` — không backfill, chỉ áp dụng cho bản ghi mới.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: test T2-08 (mobile sau khi bật `aud`) fail trên thiết bị thật — phải xử lý R1 trước khi tiếp tục các WP khác, không merge WP2-03 cùng lúc với các WP song song khác.
- Rollback: migration Flyway hỗ trợ xuống phiên bản trước nếu baseline sai (R2); các WP còn lại (WP2-02, WP2-04, WP2-05) độc lập về code, revert riêng từng WP không ảnh hưởng nhau.

## 5. Tác động ngoài phase

- Lên các phase sau: P4 (duyệt thửa/vụ) bắt buộc dùng `Household` để sinh mã thửa (`<MÃ HTX>-<MÃ HỘ>-T<STT>`) — nếu `Household.code` sinh sai quy tắc ở phase này, P4 phải sửa lại cả 2 chỗ.
- Lên demo, tài liệu nộp bài, thời hạn: ma trận quyền ở `01_PLAN.md` dùng trực tiếp làm tài liệu trả lời câu hỏi hội đồng về RBAC (mục 20 `PROJECT_IMPROVEMENTS.md`), nên phải giữ đồng bộ nếu có đổi sau này.
