# Checklist: Phase {N}

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP{N}-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, AI, DATA, INFRA, TEST.
Mỗi dòng là một việc không quá nửa ngày và có thể kiểm chứng.

## WP{N}-01: {Tên work package}

Kết quả đầu ra: {điều kiện xong của WP, trùng `01_PLAN.md`}.

**Spec và tài liệu**

- [ ] WP{N}-01-DOC-01 Cập nhật spec và Swagger trước khi code.

**Backend**

- [ ] WP{N}-01-BE-01 {entity, repository, service, controller, exception, phân quyền}.
- [ ] WP{N}-01-BE-02 {…}.

**Frontend (web)**

- [ ] WP{N}-01-FE-01 {màn hình, router, form, thông báo lỗi tiếng Việt}.

**Mobile**

- [ ] WP{N}-01-MB-01 {màn hình, offline queue, quyền thiết bị}.

**AI**

- [ ] WP{N}-01-AI-01 {endpoint, mô hình, ngưỡng, đánh giá}.

**Data, seed, migration**

- [ ] WP{N}-01-DATA-01 {script, dữ liệu mẫu, xử lý dữ liệu cũ}.

**Infra, deploy, CI**

- [ ] WP{N}-01-INFRA-01 {compose, env, gateway, healthcheck, CI job}.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WP{N}-01-TEST-01 Viết hoặc chạy các case T{N}-{xx} liên quan.

_Chỉ giữ các lớp WP này thật sự đụng tới, xóa lớp còn lại._

## WP{N}-02: {…}

_(copy khối WP ở trên)_

## Đóng phase

- [ ] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn milestone.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu và ảnh test không còn dùng.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
