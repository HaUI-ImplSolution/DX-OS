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
| T2-01 | WP2-01 | Âm tính | Từ một IP gửi 130 yêu cầu `POST /realms/dxos/protocol/openid-connect/token` trong một phút | Các yêu cầu vượt 120 nhận 429 kèm header hạn mức | Claude, local, 23/10/2026 | Đạt (130 lượt vào token endpoint: 120 lượt qua, lượt 121 trở đi nhận 429; chạy seed hai lần rồi E2E Lab liên tiếp như CI không còn bị 429) |
| T2-02 | WP2-01 | Ranh giới | Trong lúc IP thứ nhất bị 429, đăng nhập từ IP thứ hai | Đăng nhập thành công | Claude, local, 23/10/2026 | Đạt (IP khác nhận 400 từ Keycloak, không bị 429) |
| T2-03 | WP2-01 | Hồi quy | Mobile gửi hàng đợi 10 ảnh liên tiếp | Tất cả gửi được, không có 429 | Claude, local, 23/10/2026 | Đạt một phần (610 lượt API với token hợp lệ: 600 qua, từ lượt 601 mới 429; hàng đợi 10 ảnh trên điện thoại chưa thử) |
| T2-08 | WP2-01 | Ranh giới | Gửi 650 request `/api/*` với token sai từ một IP, rồi một request với token hợp lệ từ cùng IP | 650 lần 401 không bị 429; request hợp lệ sau đó không bị chặn | Claude, local, 23/10/2026 | Đạt (650 lần 401, request hợp lệ sau đó nhận 403 từ backend vì `farmer1` không phải staff) |
| T2-09 | WP2-01 | Hồi quy | Từ IP đang bị 429 ở token endpoint, gọi `/realms/dxos/.well-known/openid-configuration` 40 lần | Không bị giới hạn | Claude, local, 23/10/2026 | Đạt (40 lần 200) |
| T2-04 | WP2-02 | Hồi quy | Sau khi bật WAF, gửi ảnh báo cáo và chạy chẩn đoán AI từ web | Thành công, Security Events không có block nhầm | | |
| T2-05 | WP2-02 | Bảo mật | Gửi request có chuỗi tấn công mẫu (`' OR 1=1--`) vào một tham số | Cloudflare chặn hoặc thử thách, log Security Events có dòng tương ứng | | |
| T2-06 | WP2-03 | Phân quyền | Mở `https://bi.khuongdev.io.vn` và `https://files.khuongdev.io.vn` khi chưa qua Access | Hiện trang đăng nhập Cloudflare Access, không thấy trang của dịch vụ | | |
| T2-07 | WP2-03 | Hồi quy | Kỹ sư qua Access, đăng nhập Superset bằng SSO | Vào được dashboard | | |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [~] Đăng nhập SSO qua Keycloak vẫn hoạt động: web (Chrome và E2E) và Superset (Chrome, SSO bằng `admin1`) đạt; mobile chưa kiểm.
- [x] Người dùng chỉ thấy dữ liệu thuộc workspace của mình (E2E Lab: sinh viên khác nhận 404; Chrome: `student1` chỉ thấy yêu cầu và thiết bị của mình).
- [x] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối (thời hạn thử 5 giây: link mới 200, chữ ký sai và quá hạn 403).
- [x] Thông báo trong app và Mattermost vẫn gửi (có bài đăng ở các kênh "Kỹ sư nông nghiệp" và "Ban quản trị"; Chrome: sinh viên thấy huy hiệu thông báo).
- [x] Chat và Swagger vẫn mở như cũ (Chrome: Swagger, portal báo 7 dịch vụ hoạt động, trang đăng nhập Mattermost mở được).
- [x] Dựng từ clone sạch lên đủ dịch vụ healthy với từng giá trị `DXOS_APP` (clone sạch trong thư mục tạm, không overlay, không `--build`: Farm và Lab đều lên đủ dịch vụ healthy, migration và import realm từ đầu); chưa thử `--build` toàn bộ image.

**Theo app**

- Farm:
  - [~] Nông dân gửi báo cáo kèm ảnh thành công: qua API (E2E Farm 10/10) đạt; trên điện thoại chưa thử.
- Lab:
  - [x] Sinh viên tạo ticket thành công, giới hạn riêng của ticket vẫn hoạt động (66 yêu cầu từ ba người dùng: 60 tạo được, 6 nhận 429 của gateway).

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case T2-01 tới T2-09 đạt.
- Lỗi còn lại đã có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: trích đoạn log 429, ảnh chụp Security Events và trang Access, lưu trong mô tả PR.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
