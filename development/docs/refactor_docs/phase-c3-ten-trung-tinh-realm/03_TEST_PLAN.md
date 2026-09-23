# Kế hoạch kiểm thử: Phase C3

Quy ước dự án: mặc định chỉ compile và đọc diff.
Chạy test, dựng stack hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ; ghi ai chạy và khi nào ở cột "Người chạy".
Không thêm file test mới.

## 1. Chiến lược

- Mức kiểm thử áp dụng: tìm kiếm từ khóa, dựng sạch và thử đăng nhập thủ công trên web, mobile, Superset.
- Môi trường: clone sạch, `deploy/reset.sh`, `DXOS_APP=farm`.
- Dữ liệu và tài khoản test: `farmer1`, `engineer1`, `admin1` do seed tạo.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC3-01 | WPC3-04 | Tài liệu | Tìm `dxfarm` ngoài tài liệu lịch sử, scheme và bundle mobile | Không có | | |
| TC3-02 | WPC3-01 | Happy path | Đăng nhập web bằng `admin1` | Vào được, token có audience `dxos-api` | | |
| TC3-03 | WPC3-01 | Âm tính | Gọi API bằng token audience cũ | 401 | | |
| TC3-04 | WPC3-02 | Dữ liệu | Đọc `sso/realm-dxos.json` | Không có user, không có vai của app | | |
| TC3-05 | WPC3-03 | Happy path | Tải ảnh báo cáo rồi mở link | Ảnh hiển thị qua `/dxos-uploads/*` | | |
| TC3-06 | WPC3-01 | Phân quyền | FARMER dùng client web | 403; dùng `farm-mobile` thì được | | |
| TC3-07 | WPC3-01 | Happy path | Đăng nhập Superset bằng SSO | Vào được, `admin1` thấy dashboard | | |
| TC3-08 | WPC3-04 | Happy path | Mobile (build mới) đăng nhập `farmer1` | Vào được, gửi được báo cáo | | |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động.
- [ ] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [ ] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với `DXOS_APP=farm`.

**Theo app**

- Farm:
  - [ ] FARMER dùng token web bị 403, dùng token mobile được.
  - [ ] Seed demo tạo đủ tài khoản và dữ liệu mẫu.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC3-01 đến TC3-08 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả tìm kiếm, ảnh chụp đăng nhập ba client, log seed lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
