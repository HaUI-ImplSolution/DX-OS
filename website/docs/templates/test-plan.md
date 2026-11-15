# Kế hoạch kiểm thử: Phase {N}

Kiểm tra phase đã đạt tiêu chuẩn và chạy đúng kỳ vọng chưa.
Quy ước dự án: mặc định chỉ compile, đọc diff.
Chạy test, khởi động app hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ, ghi rõ ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit / integration / E2E / thủ công trên thiết bị / đánh giá AI / bảo mật / hiệu năng.
- Môi trường: {clone sạch + compose + seed demo, hoặc môi trường khác}.
- Chạy lại với từng tệp mô hình mà phase đụng tới.
- Dữ liệu và tài khoản test: {tài khoản theo vai của từng mô hình và dữ liệu mẫu cần có}.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T{N}-01 | WP{N}-01 | Happy path | {…} | {…} | | |
| T{N}-02 | WP{N}-01 | Âm tính | {dữ liệu sai, thiếu, trùng} | {mã lỗi và thông báo} | | |
| T{N}-03 | WP{N}-01 | Phân quyền | {từng vai của mô hình / chưa đăng nhập} | {200, 403, 404 đúng vai} | | |
| T{N}-04 | WP{N}-01 | Ranh giới | {áp dụng mô hình khác lên cùng lõi, áp dụng lại cùng tệp} | {mô hình áp dụng được, lõi không đổi, áp dụng lại không làm gì} | | |

_Gợi ý nhóm case: happy path, âm tính, phân quyền theo role và theo client, ranh giới lõi và mô hình, poka-yoke, áp dụng lại cùng tệp, đồng thời (hai người cùng thao tác), dữ liệu cũ sau migration, lỗi dịch vụ ngoài (AI, Mattermost tắt)._

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi mô hình)**

- [ ] Đăng nhập SSO qua Keycloak (web, API) vẫn hoạt động.
- [ ] Người dùng chỉ thấy hồ sơ mà quyền của vai cho phép (`all`, `own`, `read`, `unit`, `assigned`).
- [ ] Trạng thái hồ sơ chỉ đổi qua quy trình, ghi đủ lịch sử; hành động sai trạng thái và cập nhật bản cũ bị từ chối (HTTP 409).
- [ ] Link tải tệp đính kèm có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Thông báo qua Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ.
- [ ] Áp dụng lại cùng tệp mô hình không làm gì; hệ thống bị gián đoạn giữa chừng thì áp dụng lại để làm tiếp.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` trong `deploy/` lên đủ dịch vụ healthy.

**Theo mô hình** (chỉ giữ mô hình mà phase đụng tới)

- [ ] `python3 e2e/api/check_conformance.py <tên mô hình>` đạt.
- [ ] {hồi quy riêng của mô hình}.

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case mức bắt buộc đạt, không còn lỗi nghiêm trọng mở.
- Lỗi còn lại đã có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: {log, ảnh chụp, video, báo cáo test} lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
