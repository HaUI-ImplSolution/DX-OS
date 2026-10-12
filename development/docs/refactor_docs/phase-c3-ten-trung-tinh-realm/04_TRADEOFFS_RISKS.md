# Trade-off và rủi ro: Phase C3

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Tên nền tảng | A: `dxos`. B: giữ `dxfarm` | A | Core, SSO, hạ tầng trung tính | Phá phiên đăng nhập, link ảnh, `.env` cũ | Vừa |
| TO2 | Tài khoản mẫu | A: seed của từng app. B: giữ trong realm import | A | Realm không chứa dữ liệu app; Lab tự có tài khoản | Cần chạy seed mới có tài khoản; thêm bước dựng | Thấp |
| TO3 | Nhận diện mobile Farm | A: giữ scheme và bundle `dxfarm`, đổi client id. B: đổi hết | A | Không phải ký lại, phát hành lại app | Còn từ `dxfarm` ở thương hiệu mobile | Thấp |

Kết luận: TO1 đã chốt ngày 09/10/2026 (RD4, tên `dxos`).

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Bỏ sót một nơi dùng tên cũ, đăng nhập lỗi một client | Vừa | Vừa | Tìm toàn repo theo `dxfarm`; thử đăng nhập cả ba client | 401 hoặc lỗi redirect ở một client | Sửa theo tìm kiếm; giữ danh sách ngoại lệ có chủ đích |
| R2 | Tạo vai lúc khởi động chạy trước khi Keycloak sẵn sàng | Vừa | Vừa | Thử lại có giới hạn; không làm backend dừng nếu Keycloak chưa lên | Log báo lỗi tạo vai khi khởi động | Chạy lại khi khởi động sau |
| R3 | Seed không tạo được tài khoản vì thiếu vai | Thấp | Vừa | Seed đợi core tạo vai xong | Lỗi gán vai | Seed tự tạo vai nếu thiếu |
| R4 | Mobile đang cài trên máy không đăng nhập được sau đổi client | Cao | Thấp | Build và cài lại; ghi chú ở `mobile/README.md` | Lỗi `invalid_client` | Cài lại bản mới |

## 3. Nợ kỹ thuật chấp nhận

- Scheme `dxfarm://` và bundle `vn.dxfarm.mobile` giữ nguyên.
- Tên repository `DX---Farm` chưa đổi.
- `slug` trong `mobile/app.json` và `name` trong `mobile/package.json` là `dxfarm-mobile`: định danh Expo của app Farm, đổi thì mất liên kết dự án EAS.
- `.gitleaks.toml` còn chuỗi `dxfarm_superset_demo_secret` trong danh sách bỏ qua vì chuỗi này còn trong lịch sử git.
- `sbom/mobile.cdx.json` và `mobile/package-lock.json` còn tên gói `dxfarm-mobile`, sinh lại cùng bản build mobile.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: không thể đăng nhập ở bất kỳ client nào sau khi dựng sạch.
- Rollback: quay lại nhánh trước phase và dựng lại.

## 5. Tác động ngoài phase

- Lên các phase sau: C4 dùng realm và vai theo `RoleProvider`; C6 seed theo app.
- Lên demo, tài liệu nộp bài, thời hạn: ảnh chụp, kịch bản demo và README dùng tên mới.

## 6. Tồn đọng khi đóng phase

| # | Nội dung | Xử lý ở |
|---|---|---|
| B1 | `deploy/.env` cũ còn tên `dxfarm` ở `MINIO_ACCESS_KEY` và `SUPERSET_OIDC_CLIENT_SECRET` (giá trị bí mật, không đổi tự động) | Người dùng |
| B2 | Webhook Mattermost trong `deploy/.env` trả HTTP 400 sau khi dựng lại, nghiệp vụ không bị ảnh hưởng | Tạo lại webhook theo `chat/README.md` |
| B3 | Người phụ trách và ngày bắt đầu/xong để trống ở `00_OVERVIEW.md` | Người dùng điền |
| B4 | Branch `Khuong/refactor-c3-neutral-names` chưa merge vào `Khuong/main` | Người dùng quyết định |
