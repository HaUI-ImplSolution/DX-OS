# Kế hoạch kiểm thử: Phase 1

Kiểm tra phase đã đạt tiêu chuẩn và chạy đúng kỳ vọng chưa.
Quy ước dự án: mặc định chỉ kiểm cấu hình và đọc diff.
Chạy stack hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ, ghi rõ ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: kiểm cấu hình, thủ công trên stack thật, bảo mật.
- Môi trường: VPS đã bật overlay Cloudflare cho các case qua tunnel; máy dev cho các case LAN.
- Chạy lại với `DXOS_APP=farm` và `DXOS_APP=lab`.
- Dữ liệu và tài khoản test: `farmer1`, `admin1` (Farm); tài khoản demo của Lab.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T1-01 | WP1-01 | Happy path | Mở `https://khuongdev.io.vn` từ máy có IP công khai đã biết, đọc `docker compose logs apisix --tail 5` | `remote_addr` bằng IP công khai của máy | Claude, local, 23/10/2026 | Đạt (giả lập: header `CF-Connecting-IP` gửi từ nguồn tin cậy; tunnel thật chưa chạy) |
| T1-02 | WP1-01 | Bảo mật | Từ máy trong LAN, gọi cổng 9080 kèm `CF-Connecting-IP: 1.2.3.4` | `remote_addr` trong log là IP LAN, không phải `1.2.3.4` | Claude, local, 23/10/2026 | Đạt (qua `192.168.1.221`, `remote_addr` là IP LAN) |
| T1-03 | WP1-02 | Happy path | Gọi `GET /api/v1/system/status` | Dòng log là JSON hợp lệ, có `request_id`, `upstream_addr`, `status`, `request_time` | Claude, local, 23/10/2026 | Đạt |
| T1-04 | WP1-02 | Âm tính | Gọi `/realms/dxos/protocol/openid-connect/auth?code=abc&state=xyz` | Log có `uri` không kèm `code` hay `state`; không có header `Authorization` trong log | Claude, local, 23/10/2026 | Đạt (không lộ `code`, `state`, `Authorization`) |
| T1-05 | WP1-03 | Happy path | Đăng nhập sai ba lần rồi đúng bằng `farmer1` từ máy ngoài | Admin Console → Events có ba `LOGIN_ERROR` và một `LOGIN`, `ipAddress` là IP thật | Claude, local, 23/10/2026 | Đạt (IP `203.0.113.9` giả lập qua gateway) |
| T1-06 | WP1-03 | Âm tính | Đăng nhập sai 5 lần, thử đăng nhập đúng ngay sau đó | Bị từ chối trong thời gian chờ, đăng nhập lại được sau đó | Claude, local, 23/10/2026 | Đạt (cần chờ 1,5 giây giữa các lần sai; đăng nhập lại được sau 70 giây) |
| T1-07 | WP1-04 | Happy path | Chạy các lệnh trong `OPERATIONS.md` trên log thật | Mỗi lệnh in ra kết quả đúng như mô tả | Claude, local, 23/10/2026 | Đạt sau khi sửa lệnh `jq` và loại dòng health check |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [~] Đăng nhập SSO qua Keycloak vẫn hoạt động sau khi bật `KC_PROXY_HEADERS`: web (Chrome và E2E) và Superset (Chrome) đạt; mobile chưa kiểm.
- [x] Ảnh báo cáo tải được qua `/dxos-uploads/*`, link hết hạn bị từ chối (thử với thời hạn 5 giây: link mới 200, chữ ký sai và link quá hạn 403).
- [x] Giới hạn `limit-count` hiện có vẫn trả 429 khi vượt, tính theo IP (IP A nhận 429 sau 120 lượt, IP B vẫn 200).
- [x] Dựng từ clone sạch lên đủ dịch vụ healthy với từng giá trị `DXOS_APP` (clone sạch trong thư mục tạm, không overlay, không `--build`: Farm và Lab đều lên đủ dịch vụ healthy, migration và import realm từ đầu); chưa thử `--build` toàn bộ image.
- [x] Chạy LAN không có overlay: cấu hình `real_ip_*` không làm hỏng gateway (stack sạch ở chế độ LAN, Chrome và E2E đạt; sự kiện Keycloak ghi IP container gateway).

## 4. Tiêu chí đạt của phase (exit criteria)

- Tất cả case T1-01 tới T1-07 đạt.
- Lỗi còn lại đã có issue và được chấp nhận ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: trích đoạn log và ảnh chụp Admin Console → Events, lưu trong mô tả PR.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
