# Kế hoạch kiểm thử: Phase 2

Kiểm tra phase đã đạt tiêu chuẩn và chạy đúng kỳ vọng chưa.
Quy ước dự án: mặc định chỉ kiểm cấu hình và đọc diff.
Chạy stack hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ, ghi rõ ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: thủ công trên stack thật, bảo mật, hồi quy trên thiết bị.
- Môi trường: VPS qua Cloudflare Tunnel; điện thoại thật cho hồi quy mobile.
- Chạy lại với `DXOS_APP=farm` và `DXOS_APP=lab`.
- Dữ liệu và tài khoản test: `farmer1`, `engineer1`, `admin1`; hai máy hoặc hai mạng khác nhau để thử IP khác nhau.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T2-01 | WP2-01 | Âm tính | Từ một IP gửi 40 yêu cầu `POST /realms/dxos/protocol/openid-connect/token` trong một phút | Các yêu cầu vượt 30 nhận 429 kèm header hạn mức | | |
| T2-02 | WP2-01 | Ranh giới | Trong lúc IP thứ nhất bị 429, đăng nhập từ IP thứ hai | Đăng nhập thành công | | |
| T2-03 | WP2-01 | Hồi quy | Mobile gửi hàng đợi 10 ảnh liên tiếp | Tất cả gửi được, không có 429 | | |
| T2-04 | WP2-02 | Hồi quy | Sau khi bật WAF, gửi ảnh báo cáo và chạy chẩn đoán AI từ web | Thành công, Security Events không có block nhầm | | |
| T2-05 | WP2-02 | Bảo mật | Gửi request có chuỗi tấn công mẫu (`' OR 1=1--`) vào một tham số | Cloudflare chặn hoặc thử thách, log Security Events có dòng tương ứng | | |
| T2-06 | WP2-03 | Phân quyền | Mở `https://bi.khuongdev.io.vn` và `https://files.khuongdev.io.vn` khi chưa qua Access | Hiện trang đăng nhập Cloudflare Access, không thấy trang của dịch vụ | | |
| T2-07 | WP2-03 | Hồi quy | Kỹ sư qua Access, đăng nhập Superset bằng SSO | Vào được dashboard | | |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động.
- [ ] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [ ] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Thông báo trong app và Mattermost vẫn gửi.
- [ ] Chat `chat.khuongdev.io.vn` và Swagger vẫn mở như cũ.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với từng giá trị `DXOS_APP`.

**Theo app**

- Farm:
  - [ ] Nông dân gửi báo cáo kèm ảnh trên mobile thành công.
- Lab:
  - [ ] Sinh viên tạo ticket thành công, giới hạn riêng của ticket vẫn hoạt động.

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case T2-01 tới T2-07 đạt.
- Lỗi còn lại đã có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: trích đoạn log 429, ảnh chụp Security Events và trang Access, lưu trong mô tả PR.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
