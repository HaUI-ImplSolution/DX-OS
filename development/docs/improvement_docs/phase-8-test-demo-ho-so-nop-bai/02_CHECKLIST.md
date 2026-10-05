# Checklist: Phase 8

Mã dòng: `WP8-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, DATA, INFRA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP8-02: Dữ liệu demo chạy hết luồng

Kết quả đầu ra: database sạch có đủ dữ liệu demo; rõ ràng về việc có treatment rule thật hay demo nhánh `review_required`.

**Data, seed, migration**

- [ ] WP8-02-DATA-01 Viết script seed demo riêng (hộ, thửa có GPS thật ở ĐBSCL, vụ nhiều trạng thái, báo cáo kèm ảnh thật).
- [ ] WP8-02-DATA-02 Theo dõi tiến độ duyệt treatment rule mẫu (Q2), cập nhật kết luận khi có.

**Test**

- [ ] WP8-02-TEST-01 Chạy script seed demo trên database sạch, xác nhận đủ dữ liệu cho kịch bản demo (T8-01).

## WP8-01: Test cho phần lõi và E2E trên môi trường sạch

Kết quả đầu ra: CI có job E2E chạy xanh trên clone sạch; danh sách case D6 của các phase đã rà đủ.

**Backend**

- [ ] WP8-01-BE-01 Rà lại case D6 đã viết ở P1-P6, liệt kê khoảng trống còn thiếu (nếu có), bổ sung.

**Infra, deploy, CI**

- [ ] WP8-01-INFRA-01 Viết script/test E2E (dựng compose sạch → tạo hộ/thửa/vụ → báo cáo → chẩn đoán → duyệt → phun → thu hoạch bị chặn).
- [ ] WP8-01-INFRA-02 Thêm job E2E vào `.github/workflows/ci.yml`.

**Test**

- [ ] WP8-01-TEST-01 Job E2E chạy xanh trên clone sạch (T8-02).

## WP8-03: Hồ sơ PoF — repo, license, release, issue tracker

Kết quả đầu ra: repo có tag, `CONTRIBUTING.md`, issue template, `LICENSES.md`/SBOM khớp thực tế.

**Tài liệu**

- [ ] WP8-03-DOC-01 Tag `v0.1.0` sớm, tag lại bản chốt khi P8 hoàn tất.
- [ ] WP8-03-DOC-02 Viết `CONTRIBUTING.md`.
- [ ] WP8-03-DOC-03 Thêm `.github/ISSUE_TEMPLATE/`, `.github/PULL_REQUEST_TEMPLATE.md`, bật GitHub Issues, tạo vài issue mẫu.
- [ ] WP8-03-DOC-04 Rà `LICENSES.md` khớp dependency thực tế (MinIO, APISIX, thư viện mobile mới).

**Infra, deploy, CI**

- [ ] WP8-03-INFRA-01 Pin phiên bản chính xác `ai/requirements.txt`, sinh SBOM.

**Backend**

- [ ] WP8-03-BE-01 Thêm SPDX header cho mã tự viết ở backend Java.

**Test**

- [ ] WP8-03-TEST-01 Rà lịch sử commit bằng công cụ quét secret (đã có từ P1) trước khi chuyển public (T8-03).

## WP8-04: Đồng bộ README, CHANGELOG với thực tế

Kết quả đầu ra: README đủ mục đề yêu cầu, không còn mô tả tiến độ cũ sai lệch.

**Tài liệu**

- [ ] WP8-04-DOC-01 Viết lại mục kiến trúc README theo 3 tầng + H-P-D-I, trỏ sơ đồ từ P3.
- [ ] WP8-04-DOC-02 Bỏ mục mô tả tiến độ M1-M6 đã cũ; chỉ giữ trạng thái cuối.
- [ ] WP8-04-DOC-03 Rà `CHANGELOG.md`, cập nhật mục "Chưa làm" đã lỗi thời.

## WP8-05: Portal hiện trạng dịch vụ, chuẩn hoá lỗi tiếng Việt

Kết quả đầu ra: portal hiện đúng trạng thái dịch vụ; lỗi phổ biến hiển thị tiếng Việt.

**Backend**

- [ ] WP8-05-BE-01 `ApiResponse` lỗi: thêm trường `errorCode`.

**Frontend (web)**

- [ ] WP8-05-FE-01 `portal/index.html`: gọi healthcheck từng dịch vụ, hiện chỉ báo trạng thái.
- [ ] WP8-05-FE-02 Bảng ánh xạ `errorCode → tiếng Việt` cho các lỗi phổ biến (khóa PHI, idempotency, phân quyền).

**Test**

- [ ] WP8-05-TEST-01 Dừng 1 service, kiểm portal hiện đúng trạng thái down (T8-04).
- [ ] WP8-05-TEST-02 Gọi API gây lỗi khóa PHI, FE hiện thông báo tiếng Việt (T8-05).

## WP8-06: Kịch bản demo, báo cáo kỹ thuật, slide, video

Kết quả đầu ra: bộ hồ sơ nộp bài đầy đủ, đã rehearsal.

**Tài liệu**

- [ ] WP8-06-DOC-01 Viết kịch bản demo 10 phút.
- [ ] WP8-06-DOC-02 Chuẩn bị bộ trả lời câu hỏi hội đồng (mục 20 `PROJECT_IMPROVEMENTS.md`).
- [ ] WP8-06-DOC-03 Viết báo cáo kỹ thuật 8-10 trang.
- [ ] WP8-06-DOC-04 Làm slide tối đa 12.
- [ ] WP8-06-DOC-05 Quay video demo 3-5 phút.
- [ ] WP8-06-DOC-06 Rehearsal ít nhất 1 lần, chừa tối thiểu 2 ngày cuối.

## Đóng phase

- [ ] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [ ] CI xanh (gồm job E2E mới).
- [ ] `.env.example`, seed, migration chạy được trên clone sạch, lặp lại được 2 lần liên tiếp.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff hoặc lịch sử commit.
- [ ] README, CHANGELOG, LICENSES.md, CONTRIBUTING.md đầy đủ và khớp thực tế.
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
