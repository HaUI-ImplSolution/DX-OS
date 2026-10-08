# Phase 8: Kiểm thử, dữ liệu demo, hồ sơ nộp bài

| Mục | Giá trị |
|---|---|
| Trạng thái | Code, tài liệu và kiểm thử T8-01 đến T8-05 đã đạt; còn hồ sơ nộp bài (báo cáo, slide, video, tag) và Q2 (chuyên gia duyệt treatment rule); Q1 đã chốt |
| Mức độ | Nghiêm trọng (hạn nộp) |
| Nhóm ưu tiên | A |
| Ước lượng | 5-6 ngày, chạy xuyên suốt |
| Người phụ trách | {điền tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Chạy xuyên suốt P1-P7, chốt sau cùng |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 17 | Deploy / CI / hạ tầng | Quan trọng | Một phần: build từ clone sạch có bằng chứng, backup/restore |
| mục 18 | Test, E2E và dữ liệu demo | Quan trọng | Toàn bộ |
| mục 19 | Tài liệu, license, PoF, hồ sơ nộp bài | Quan trọng/Theo dõi | Toàn bộ |
| mục 20 | Kịch bản demo và câu hỏi hội đồng | Quan trọng | Toàn bộ |

## 2. Lý do

- Vấn đề đang gặp: chưa có E2E tự động trên môi trường sạch; dữ liệu seed hiện chưa có treatment rule nào được duyệt nên chưa demo trọn luồng áp dụng thuốc/khóa PHI trên database sạch.
  Repo chưa có tag/release nào (`git tag` rỗng); không có `CONTRIBUTING.md`; `.github/` chỉ có `ci.yml`, không có issue template.
  `ai/requirements.txt` dùng khoảng phiên bản (`>=,<`), không phải lockfile cố định; chưa có SBOM; backend Java chưa có SPDX header (AI Python đã có ở một số file).
  Portal hiện chỉ là `index.html` tĩnh liệt kê link, không hiện trạng thái dịch vụ.
- Vì sao làm ở phase này: đây là điều kiện không bị loại (repo public, build từ source, test tối thiểu) và chiếm 50 điểm PoF theo rubric — phải chốt trước hạn nộp, không thể bỏ qua dù không tạo tính năng mới.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Test cho phần lõi (kế thừa D6 từ P0, đã viết rải ở P1-P6) |
| Frontend (web) | Có | Chuẩn hoá thông báo lỗi tiếng Việt |
| Mobile | Không | |
| AI | Có | Test đã có ở P6, bổ sung nếu thiếu |
| Data / Seed / Migration | Có | Script seed demo riêng, treatment rule mẫu đã duyệt |
| Infra / Deploy / CI | Có | E2E trong CI, backup/restore, portal hiện trạng dịch vụ |
| Docs / PoF | Có | README, CHANGELOG, CONTRIBUTING, issue template, LICENSES.md, SBOM, SPDX, báo cáo/slide/video |

## 4. Phạm vi

- Trong phạm vi: WP8-01 đến WP8-06 ở `01_PLAN.md` (test lõi + E2E, dữ liệu demo đầy đủ luồng, hồ sơ PoF, đồng bộ README/CHANGELOG, portal hiện trạng + lỗi tiếng Việt, kịch bản demo + báo cáo + slide + video).
- Ngoài phạm vi (không làm ở phase này): tính năng nghiệp vụ mới (đã làm ở P1-P7); việc cần chuyên gia duyệt dữ liệu (treatment rule, policy) — chỉ demo trung thực nhánh `review_required` nếu chưa kịp có rule được duyệt thật.
- Giả định: D6 (phase 0, bật test cho case crash/logic) đã áp dụng rải suốt P1-P6, phase này chỉ tổng hợp và lấp khoảng trống còn thiếu, không viết lại từ đầu.

## 5. Ưu điểm và hạn chế

- Ưu điểm: điều kiện không bị loại được đóng đầy đủ; có bằng chứng kiểm chứng được cho mọi tuyên bố trong báo cáo.
- Hạn chế: không tạo giá trị nghiệp vụ mới, dễ bị xem nhẹ nếu dồn hết vào cuối — phải làm xuyên suốt, không dồn vào 5-6 ngày cuối.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (demo trung thực nhánh `review_required` nếu chưa có rule thuốc được duyệt thật kịp hạn).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: clone sạch từ tag đã public, chạy script dựng, có đủ tài khoản/dữ liệu demo, chạy trọn kịch bản 10 phút không lỗi.
- Sản phẩm bàn giao: E2E tự động trong CI, script seed demo riêng, repo public có tag, `CONTRIBUTING.md`, issue template, `LICENSES.md`/SBOM/SPDX cập nhật, portal hiện trạng dịch vụ, báo cáo kỹ thuật 8-10 trang, slide tối đa 12, video 3-5 phút.
- Tiêu chí đo được: `git clone` → script dựng → đủ dịch vụ `healthy` trong một lần chạy, lặp lại được lần hai; CI chạy test thật và xanh.
- Bằng chứng: log CI, video quay thử kịch bản demo, checklist PoF đã tick đủ.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: mọi phase P1-P7 phải có test/tài liệu cập nhật theo đúng tiêu chí của chính phase đó trước khi P8 tổng hợp (không phải P8 viết test thay cho các phase kia).
- Phase sau cần từ phase này: không có, đây là phase chốt cuối cùng trước khi nộp bài.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: toàn bộ 50 điểm PoF (đề mục 5, 10, 11, 12) và điều kiện không bị loại (repo public, build from source).
- Dùng làm bằng chứng cho: toàn bộ hồ sơ nộp bài — báo cáo, slide, video, câu trả lời hội đồng.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Thời điểm chuyển repo từ Private sang Public | Ngay khi rà xong lịch sử commit không còn secret / Chỉ chuyển sát ngày nộp để tránh lộ sớm | 05/10/2026 | Ngay khi rà xong lịch sử commit — tránh rủi ro quên/hết thời gian nếu để tới sát hạn, không có lợi ích thực sự khi trì hoãn sau khi đã sạch secret |
| Q2 | Treatment rule mẫu có kịp được duyệt thật trước hạn không | Có, demo trọn luồng khóa PHI thật / Không, demo trung thực nhánh `review_required` | {ngày} | _(chưa chốt — phụ thuộc chuyên gia duyệt rule, không tự quyết được)_ |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md` (kiến trúc 3 tầng, H-P-D-I, cài đặt, API, tài khoản demo — theo đúng yêu cầu đề).
- [x] `CHANGELOG.md`, `CONTRIBUTING.md` (mới), `LICENSES.md`, SBOM (mới).
- [x] `.github/ISSUE_TEMPLATE/`, `.github/PULL_REQUEST_TEMPLATE.md` (mới).

## 11. Việc chưa làm và chưa chốt

**Chưa làm**

- Tag và release, bật GitHub Issues và tạo issue mẫu, chuyển repo sang public (việc trên GitHub).
- Báo cáo kỹ thuật, slide, video và rehearsal; mới có kịch bản demo, bộ trả lời hội đồng và dàn ý ở `development/docs/submission/`.
- Test tích hợp phạm vi dữ liệu giữa hai nông hộ và idempotency của thửa/vụ.
- Ảnh lá lúa thật cho `seed_demo.py --images` và cho chẩn đoán AI trong demo.
- Chart "Thửa đang cách ly" và "Tỷ lệ AI đúng/sai" trên Superset vẫn trống cho tới khi có rule được duyệt và chẩn đoán AI.

**Chưa chốt**

- Q2: treatment rule mẫu có kịp được chuyên gia duyệt trước hạn không.
- Ngày hạn nộp thật.
