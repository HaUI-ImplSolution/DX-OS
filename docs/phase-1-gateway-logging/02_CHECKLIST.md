# Checklist: Phase 1

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP1-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, AI, DATA, INFRA, TEST.
Mỗi dòng là một việc không quá nửa ngày và có thể kiểm chứng.

## WP1-01: IP thật của client tại gateway

Kết quả đầu ra: `remote_addr` trong log là IP người dùng; header giả từ ngoài dải tin cậy bị bỏ qua.

**Infra, deploy, CI**

- [x] WP1-01-INFRA-01 `config.yaml`: thêm `real_ip_header` và `real_ip_from`.
- [x] WP1-01-INFRA-02 Kiểm `real_ip_from` bao trùm subnet của mạng compose (`docker network inspect`), ghi dải vào `OPERATIONS.md`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [~] WP1-01-TEST-01 Case T1-01, T1-02 (T1-01 còn phải chạy qua tunnel thật trên VPS).

## WP1-02: Access log JSON, `X-Request-Id` và xoay vòng log

Kết quả đầu ra: log gateway là JSON có `request_id`; mọi service có giới hạn dung lượng log.

**Infra, deploy, CI**

- [x] WP1-02-INFRA-01 `config.yaml`: `access_log_format` JSON với `access_log_format_escape: json`.
- [x] WP1-02-INFRA-02 `common.yaml`: `global_rules` với plugin `request-id`.
- [x] WP1-02-INFRA-03 `docker-compose.yml`: khối `x-logging` và `logging` cho từng service (`apisix` dùng giá trị lớn hơn).
- [x] WP1-02-INFRA-04 `docker compose config` hợp lệ với `DXOS_APP=farm` và `DXOS_APP=lab`, có và không có overlay Cloudflare.

**Test**

- [x] WP1-02-TEST-01 Case T1-03, T1-04.

## WP1-03: Sự kiện và khóa tạm của Keycloak

Kết quả đầu ra: có sự kiện đăng nhập kèm IP thật; tài khoản bị khóa tạm sau 5 lần sai.

**Data, seed, migration**

- [x] WP1-03-DATA-01 `realm-dxos.json`: events, admin events, brute force.

**Infra, deploy, CI**

- [x] WP1-03-INFRA-01 `docker-compose.cloudflared.yml`: `KC_PROXY_HEADERS: xforwarded` cho Keycloak.
- [x] WP1-03-INFRA-02 `common.yaml`: route Keycloak ghi đè `X-Forwarded-For` bằng `$remote_addr`.

**Spec và tài liệu**

- [x] WP1-03-DOC-01 `sso/README.md`: các bước áp cấu hình cho Keycloak đang chạy.

**Test**

- [x] WP1-03-TEST-01 Case T1-05, T1-06.

## WP1-04: Tài liệu vận hành và cách xem log

Kết quả đầu ra: các ví dụ lệnh trong `OPERATIONS.md` chạy được.

**Spec và tài liệu**

- [x] WP1-04-DOC-01 Viết `deploy/OPERATIONS.md`.
- [x] WP1-04-DOC-02 `CHANGELOG.md` ghi trạng thái cuối.

**Test**

- [x] WP1-04-TEST-01 Case T1-07.

## Đóng phase

- [x] Cấu hình YAML và JSON hợp lệ (`docker compose config`, parse `realm-dxos.json`) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] Swagger: không áp dụng (không đổi API).
- [x] `.env.example`: không thêm biến; stack khởi động với từng giá trị `DXOS_APP` (clone sạch Farm và Lab).
- [x] `dx-core-api` và `dx-core` không bị đụng.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] Dọn dữ liệu test không còn dùng (stack sạch đã xóa; trong dữ liệu local còn sự kiện Keycloak thử).
- [x] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase: xóa thư mục phase khi xong, việc còn lại sang `docs/BACKLOG.md`.
