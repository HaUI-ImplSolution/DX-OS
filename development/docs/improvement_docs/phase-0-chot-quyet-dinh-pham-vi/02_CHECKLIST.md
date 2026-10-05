# Checklist: Phase 0

Phase này không code, chỉ còn lớp DOC. Mã dòng: `WP0-{xx}-DOC-{nn}`.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## DP0-01: Phạm vi đề tài (D1)

Kết quả đầu ra: D1 có giá trị A/B/C ở `01_PLAN.md`, P9 giữ hay bỏ đã rõ trong `PHASE_PLAN.md`.

- [x] WP0-01-DOC-01 Gửi câu hỏi D1 cho giảng viên hướng dẫn / BTC (đề tài nông nghiệp có hợp lệ ở vòng HaUI không).
- [-] WP0-01-DOC-02 Không cần — đã có phản hồi chính thức (A), không phải dùng mặc định B.
- [x] WP0-01-DOC-03 Cập nhật dòng D1 ở `01_PLAN.md` mục "Quyết định thiết kế đã chốt".
- [x] WP0-01-DOC-04 Chọn A: đã viết lại `PHASE_PLAN.md` mục P9 (đánh dấu loại bỏ) và bảng tổng quan.

## DP0-02: Kiến trúc Open-Core (D2, D3, D4)

Kết quả đầu ra: D2-D4 có giá trị chốt, dùng được ngay làm "Hướng làm" của P3 khi tạo phase đó.

- [x] WP0-02-DOC-01 Xác nhận D2 — tự làm workflow.
- [x] WP0-02-DOC-02 Xác nhận D3 — APISIX standalone, lý do: plugin openid-connect tích hợp Keycloak nhanh hơn.
- [x] WP0-02-DOC-03 Xác nhận D4 — MinIO, không cắt khỏi lộ trình rút gọn 14 ngày.
- [x] WP0-02-DOC-04 Cập nhật dòng D2-D4 ở `01_PLAN.md`.

## DP0-03: Vận hành nghiệp vụ nông nghiệp (D5-D9)

Kết quả đầu ra: D5-D9 có giá trị chốt.

- [x] WP0-03-DOC-01 Xác nhận D5 — hộp thông báo: GET danh sách + PATCH đánh dấu đã đọc, push để sau.
- [x] WP0-03-DOC-02 Xác nhận D6 — bật test, chỉ case crash/logic cho nghiệp vụ lõi dùng chung (danh sách case ở `01_PLAN.md`).
- [x] WP0-03-DOC-03 Xác nhận D7 — giữ 5 bệnh lá, thêm đường nhập triệu chứng.
- [x] WP0-03-DOC-04 Xác nhận D8 — biên soạn danh mục giống tối thiểu, ghi nguồn.
- [x] WP0-03-DOC-05 Xác nhận D9 — phân quyền theo thao tác, hoãn "phạm vi khu vực".
- [x] WP0-03-DOC-06 Cập nhật dòng D5-D9 ở `01_PLAN.md`.

## DP0-04: Dữ liệu mở và lịch trình (D10, ngày hạn)

Kết quả đầu ra: D10 có giá trị chốt, ngày hạn nộp và số người làm song song đã điền.

- [x] WP0-04-DOC-01 Xác nhận D10 — Open-Meteo + snapshot dự phòng.
- [x] WP0-04-DOC-02 Chốt: không bị ràng buộc mốc 14 ngày, không cần điền ngày công bố/ngày hạn cụ thể.
- [x] WP0-04-DOC-03 Đối chiếu: dùng ước lượng đầy đủ ~40 người-ngày (P0-P8 + P10), không áp dụng lộ trình rút gọn.
- [x] WP0-04-DOC-04 Cập nhật dòng D10 ở `01_PLAN.md` và bảng tổng quan `PHASE_PLAN.md`.

## Đóng phase

- [x] Không có code để compile/đọc diff — bỏ qua bước `mvnw compile` / `tsc --noEmit`.
- [x] Cả 10 dòng D1-D10 ở `01_PLAN.md` mục "Quyết định thiết kế đã chốt" đã có giá trị khác `_(chưa chốt)_`.
- [x] `PHASE_PLAN.md` đã cập nhật theo kết quả (P9 đã loại bỏ, lộ trình và mục 4 đổi sang giá trị thật).
- [x] README, CHANGELOG — không cần cập nhật ngày hạn vì đã chốt là không có hạn chót ép buộc.
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] Cập nhật trạng thái phase ở `improvement_docs/README.md` mục "Chỉ mục phase".
