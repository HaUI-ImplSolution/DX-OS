# Checklist: Phase 2

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP2-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, AI, DATA, INFRA, TEST.
Mỗi dòng là một việc không quá nửa ngày và có thể kiểm chứng.

## WP2-01: Giới hạn tốc độ theo IP ở gateway

Kết quả đầu ra: vượt ngưỡng nhận 429; IP khác không bị ảnh hưởng.

**Infra, deploy, CI**

- [x] WP2-01-INFRA-01 `common.yaml`: tách route Keycloak để giới hạn đường dẫn đăng nhập, giữ `/resources/*` không giới hạn.
- [x] WP2-01-INFRA-02 `common.yaml`: `limit-count` cho đăng nhập (theo Q1).
- [x] WP2-01-INFRA-03 `common.yaml`: `limit-count` cho route `api`.
- [x] WP2-01-INFRA-04 Sinh `apisix.yaml` cho `DXOS_APP=farm` và `DXOS_APP=lab`, kiểm YAML hợp lệ.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WP2-01-TEST-01 Case T2-01, T2-02, T2-08, T2-09 (đạt trên stack local); T2-03 còn thử hàng đợi ảnh trên điện thoại.

## WP2-02: Cấu hình Cloudflare và danh sách kiểm

Kết quả đầu ra: mọi mục trong danh sách kiểm được bật hoặc ghi lý do bỏ qua.

**Spec và tài liệu**

- [x] WP2-02-DOC-01 `deploy/OPERATIONS.md`: mục Cloudflare với danh sách kiểm.

**Infra, deploy, CI**

- [ ] WP2-02-INFRA-01 Bật các mục trong danh sách (người vận hành, ngoài repo).

**Test**

- [ ] WP2-02-TEST-01 Case T2-04, T2-05.

## WP2-03: Thu hẹp bề mặt quản trị

Kết quả đầu ra: ba dịch vụ quản trị yêu cầu Cloudflare Access; mobile và web nông dân không đổi.

**Spec và tài liệu**

- [x] WP2-03-DOC-01 `deploy/OPERATIONS.md`: các bước tạo ứng dụng Access và chính sách.

**Infra, deploy, CI**

- [ ] WP2-03-INFRA-01 Tạo ứng dụng Access cho `bi.`, `files.`, `/admin*` (người vận hành, ngoài repo).

**Test**

- [ ] WP2-03-TEST-01 Case T2-06, T2-07.

## Đóng phase

- [x] YAML hợp lệ (`apisix.yaml` sinh ra cho cả hai app) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] Swagger: không áp dụng (không đổi API).
- [ ] `.env.example`: không thêm biến; stack khởi động với từng giá trị `DXOS_APP`.
- [x] `dx-core-api` và `dx-core` không bị đụng.
- [x] Không có secret, token tunnel hay email thật trong diff.
- [x] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase: xóa thư mục phase khi xong, việc còn lại sang `docs/BACKLOG.md`.
