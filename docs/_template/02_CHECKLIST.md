# Checklist: Phase {N}

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP{N}-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, AI, DATA, INFRA, TEST.
Mỗi dòng là một việc không quá nửa ngày và có thể kiểm chứng.
Dòng BE và DATA ghi module trong ngoặc vuông: `[core-api]`, `[core]`, `[farm]`, `[lab]`, `[host]`.

## WP{N}-01: {Tên work package}

Kết quả đầu ra: {điều kiện xong của WP, trùng `01_PLAN.md`}.

**Spec và tài liệu**

- [ ] WP{N}-01-DOC-01 Cập nhật spec và Swagger trước khi code.

**Backend**

- [ ] WP{N}-01-BE-01 [core-api] {interface, DTO công khai}.
- [ ] WP{N}-01-BE-02 [core] {cài đặt dùng chung: service, controller, phân quyền}.
- [ ] WP{N}-01-BE-03 [farm] {entity, repository, service, controller của app}.
- [ ] WP{N}-01-BE-04 [lab] {…}.

**Frontend (web)**

- [ ] WP{N}-01-FE-01 {màn hình, router, form, thông báo lỗi tiếng Việt}.

**Mobile**

- [ ] WP{N}-01-MB-01 {màn hình, offline queue, quyền thiết bị}.

**AI**

- [ ] WP{N}-01-AI-01 {endpoint, mô hình, ngưỡng, đánh giá}.

**Data, seed, migration**

- [ ] WP{N}-01-DATA-01 [core] {migration và dữ liệu dùng chung}.
- [ ] WP{N}-01-DATA-02 [farm] {migration, seed, dữ liệu mẫu của app}.

**Infra, deploy, CI**

- [ ] WP{N}-01-INFRA-01 {compose, env, gateway, healthcheck, CI job}.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WP{N}-01-TEST-01 Viết hoặc chạy các case T{N}-{xx} liên quan.

_Chỉ giữ các lớp và module WP này thật sự đụng tới, xóa phần còn lại._

## WP{N}-02: {…}

_(copy khối WP ở trên)_

## Đóng phase

- [ ] `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn milestone.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch với từng giá trị `DXOS_APP`.
- [ ] `dx-core-api` và `dx-core` không import gói của app nào; app chỉ import `dx-core-api`.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu và ảnh test không còn dùng.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở chỉ mục phase (README của thư mục chứa phase).
