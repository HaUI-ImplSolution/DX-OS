# Kế hoạch kiểm thử: Phase {N}

Kiểm tra phase đã đạt tiêu chuẩn và chạy đúng kỳ vọng chưa.
Quy ước dự án: mặc định chỉ compile, đọc diff.
Chạy test, khởi động app hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ, ghi rõ ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit / integration / E2E / thủ công trên thiết bị / đánh giá AI / bảo mật / hiệu năng.
- Môi trường: {clone sạch + compose + seed demo, hoặc môi trường khác}.
- Chạy lại với từng giá trị `DXOS_APP` mà phase đụng tới.
- Dữ liệu và tài khoản test: {tài khoản theo vai của từng app và dữ liệu mẫu cần có}.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T{N}-01 | WP{N}-01 | Happy path | {…} | {…} | | |
| T{N}-02 | WP{N}-01 | Âm tính | {dữ liệu sai, thiếu, trùng} | {mã lỗi và thông báo} | | |
| T{N}-03 | WP{N}-01 | Phân quyền | {từng vai của app / chưa đăng nhập} | {200, 403, 404 đúng vai} | | |
| T{N}-04 | WP{N}-01 | Ranh giới | {đổi `DXOS_APP`, khởi động lại} | {app khởi động được, core không đổi} | | |

_Gợi ý nhóm case: happy path, âm tính, phân quyền theo role và theo client, ranh giới core/app, poka-yoke, idempotency và offline, đồng thời (hai người cùng thao tác), dữ liệu cũ sau migration, lỗi dịch vụ ngoài (AI, Mattermost tắt)._

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động.
- [ ] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [ ] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409).
- [ ] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Thông báo trong app và Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ.
- [ ] Gửi lại yêu cầu với `Idempotency-Key` cũ không tạo bản ghi trùng.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với từng giá trị `DXOS_APP`.

**Theo app** (chỉ giữ app mà phase đụng tới)

- Farm:
  - [ ] Khóa thu hoạch theo PHI vẫn chặn đúng (HTTP 422).
  - [ ] Farmer chỉ thấy thửa và báo cáo của mình, chẩn đoán chưa duyệt bị ẩn.
- Lab:
  - [ ] {hồi quy riêng của app Lab}.

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case mức bắt buộc đạt, không còn lỗi nghiêm trọng mở.
- Lỗi còn lại đã có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: {log, ảnh chụp, video, báo cáo test} lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
