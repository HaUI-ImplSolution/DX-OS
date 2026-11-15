# Checklist: Phase {N}

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP{N}-{xx}-{lớp}-{nn}`.
Lớp gồm DOC, BE, FE, AI, DATA, INFRA, TEST.
Mỗi dòng là một việc không quá nửa ngày và có thể kiểm chứng.
Dòng BE và DATA ghi module trong ngoặc vuông: `[dx-model]`, `[dx-core]`, `[dx-engine]`, `[dx-monitor]`.

## WP{N}-01: {Tên work package}

Kết quả đầu ra: {điều kiện xong của WP, trùng `01_PLAN.md`}.

**Spec và tài liệu**

- [ ] WP{N}-01-DOC-01 Cập nhật spec và Swagger trước khi code.

**Backend**

- [ ] WP{N}-01-BE-01 [dx-model] {thuộc tính tệp mô hình, kiểm tra, thông báo lỗi theo dòng và cột}.
- [ ] WP{N}-01-BE-02 [dx-core] {thư viện dùng chung: bảo mật, audit, lưu trữ tệp}.
- [ ] WP{N}-01-BE-03 [dx-engine] {API chung, quy trình, áp dụng mô hình, phân quyền}.
- [ ] WP{N}-01-BE-04 [dx-monitor] {…}.

**Frontend (web)**

- [ ] WP{N}-01-FE-01 {màn hình, router, form, thông báo lỗi tiếng Việt}.

**AI**

- [ ] WP{N}-01-AI-01 {endpoint, mô hình, ngưỡng, đánh giá}.

**Data, migration**

- [ ] WP{N}-01-DATA-01 [dx-core] {migration dùng chung}.
- [ ] WP{N}-01-DATA-02 [dx-engine] {migration của lõi, tệp mô hình mẫu}.

**Infra, deploy, CI**

- [ ] WP{N}-01-INFRA-01 {compose, env, gateway, healthcheck, CI job}.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WP{N}-01-TEST-01 Viết hoặc chạy các case T{N}-{xx} liên quan.

_Chỉ giữ các lớp và module WP này thật sự đụng tới, xóa phần còn lại._

## WP{N}-02: {…}

_(copy khối WP ở trên)_

## Đóng phase

- [ ] `./mvnw spotless:check` và `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn milestone.
- [ ] `deploy/.env.example` và migration chạy được trên clone sạch; từng tệp mô hình mà phase đụng tới áp dụng được vào hệ thống rỗng.
- [ ] `dx-model` không phụ thuộc module nào, `dx-core` không phụ thuộc `dx-engine`, mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu và ảnh test không còn dùng.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở chỉ mục phase (README của thư mục chứa phase).
