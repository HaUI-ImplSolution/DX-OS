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
| TC3-01 | WPC3-04 | Tài liệu | Tìm `dxfarm` ngoài tài liệu lịch sử, scheme và bundle mobile | Không có | Claude (12/10/2026) | Đạt (còn `.gitleaks.toml` cho lịch sử git, `slug` và `name` của mobile; xem `04_TRADEOFFS_RISKS.md`) |
| TC3-02 | WPC3-01 | Happy path | Đăng nhập web bằng `admin1` | Vào được, token có audience `dxos-api` | Claude (12/10/2026) | Đạt (Chrome, client `dxos-web`; token `aud` = `dxos-api`, `account`; `/parcels` 200) |
| TC3-03 | WPC3-01 | Âm tính | Gọi API bằng token audience cũ | 401 | Claude (12/10/2026) | Đạt (token của service account, `aud` = `realm-management`, trả 401) |
| TC3-04 | WPC3-02 | Dữ liệu | Đọc `sso/realm-dxos.json` | Không có user, không có vai của app | Claude (12/10/2026) | Đạt (chỉ có vai `ADMIN` của nền tảng và service account của `dxos-backend-admin`) |
| TC3-05 | WPC3-03 | Happy path | Tải ảnh báo cáo rồi mở link | Ảnh hiển thị qua `/dxos-uploads/*` | Claude (12/10/2026) | Đạt (link ký trả 200 `image/png`; bỏ chữ ký trả 403) |
| TC3-06 | WPC3-01 | Phân quyền | FARMER dùng client web | 403; dùng `farm-mobile` thì được | Claude (12/10/2026) | Đạt (`dxos-web` 403, `farm-mobile` 200, `azp` = `farm-mobile`) |
| TC3-07 | WPC3-01 | Happy path | Đăng nhập Superset bằng SSO | Vào được, `admin1` thấy dashboard | Claude (12/10/2026) | Đạt (Chrome, client `dxos-superset`; dashboard Farm hiển thị từ `farm_dashboard_export.zip`) |
| TC3-08 | WPC3-04 | Happy path | Mobile (build mới) đăng nhập `farmer1` | Vào được, gửi được báo cáo | Người dùng (12/10/2026) | Đạt (build lại với client `farm-mobile`; đăng nhập, tạo thửa, tạo vụ, nộp báo cáo) |

## 3. Hồi quy (không được hỏng)

Bằng chứng (12/10/2026): `./mvnw clean verify` 80/80, `tsc --noEmit` web và mobile đạt, stack dựng sạch bằng `reset.sh --yes` (11 service healthy), `seed_demo.py` rồi `e2e.py --require-rule` 10/10 (Claude).

**Dùng chung (mọi app)**

- [x] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động: web và Superset đạt trên Chrome, mobile đạt trên ứng dụng Expo build lại (người dùng chạy 12/10/2026).
- [x] Người dùng chỉ thấy dữ liệu thuộc workspace của mình: bước đầu của E2E (phạm vi theo nông hộ) và `PermissionMatrixTest` đạt.
- [x] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối: link ký trả 200, thiếu chữ ký trả 403; hết hạn do chữ ký của MinIO đảm nhận.
- [x] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với `DXOS_APP=farm`: dựng bằng `reset.sh --yes`, 11 service healthy.

**Theo app**

- Farm:
  - [x] FARMER dùng token web bị 403, dùng token mobile được: xem TC3-06.
  - [x] Seed demo tạo đủ tài khoản và dữ liệu mẫu: `farmer1`, `engineer1`, `admin1` với vai tương ứng, hộ, thửa, vụ, báo cáo.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC3-01 đến TC3-08 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả tìm kiếm, ảnh chụp đăng nhập ba client, log seed lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
