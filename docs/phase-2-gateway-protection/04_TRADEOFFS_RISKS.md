# Trade-off và rủi ro: Phase 2

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Bảo vệ cổng quản trị | A: Cloudflare Access. B: `ip-restriction` ở gateway. C: để mở | A | Đăng nhập theo người, không phụ thuộc IP động, miễn phí đến 50 người | Thêm một bước đăng nhập; phụ thuộc Cloudflare | Thấp |
| TO2 | Nơi giới hạn tốc độ | A: gateway theo IP. B: chỉ Cloudflare | A | Hoạt động cả khi chạy LAN, không phụ thuộc Cloudflare | Người dùng chung IP di động dùng chung bộ đếm | Thấp |
| TO3 | Chặn theo quốc gia | A: không. B: chỉ Việt Nam | A | Hội đồng chấm truy cập từ nước ngoài được | Nhiều lưu lượng quét từ nước ngoài vẫn tới gateway | Thấp |

Kết luận (đề xuất, chờ người dùng chốt): chấp nhận TO1 tới TO3 theo phương án A.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Nhiều nông dân dùng chung một IP di động, chạm 600 lượt mỗi phút | Thấp | Vừa | Ngưỡng rộng; mobile gửi ảnh tuần tự | Nhiều 429 trên `/api/*` từ một IP | Nâng ngưỡng, khóa theo `sub` của token thay vì IP |
| R2 | WAF chặn nhầm tải ảnh hoặc câu hỏi AI | Vừa | Vừa | Kiểm hồi quy T2-04 sau khi bật; xem Security Events | Báo cáo gửi lỗi sau khi bật WAF | Tạo rule bỏ qua cho đường dẫn tải ảnh |
| R3 | Access làm hỏng đăng nhập SSO của Superset hoặc MinIO | Vừa | Vừa | Kiểm T2-07 trước khi coi là xong | Vòng lặp chuyển hướng ở trang BI | Gỡ ứng dụng Access của dịch vụ đó |
| R4 | Quản trị viên bị khóa khỏi Admin Console khi Access cấu hình sai | Thấp | Cao | Giữ phiên Admin Console mở khi cấu hình; có đường vào SSH và `kcadm.sh` | Không vào được `/admin/` | Gỡ ứng dụng Access trong Zero Trust |

## 3. Nợ kỹ thuật chấp nhận

- Chưa chặn tự động IP sau nhiều lần vi phạm; dựa vào Cloudflare.
- Chưa giới hạn kích thước body ở gateway; backend và MinIO đã giới hạn.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: nông dân gặp 429 trong thao tác thường ngày hoặc không gửi được ảnh.
- Rollback: bỏ khối `limit-count` thêm vào, tắt WAF rule hoặc ứng dụng Access gây lỗi.

## 5. Tác động ngoài phase

- Lên các phase sau: phase 3 ghi nhận các lần bị từ chối.
- Lên core và các app khác (Farm, Lab): không đụng mã.
- Lên demo, tài liệu nộp bài, thời hạn: kỹ sư phải qua Access khi demo BI; nên chuẩn bị tài khoản trước.
