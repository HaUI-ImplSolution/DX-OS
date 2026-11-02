# Phase 7: Đổi mô hình khi đã có dữ liệu

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong |
| Mức độ | Nghiêm trọng |
| Ứng dụng và module chính | `dx-model`, `dx-engine`, `deploy`, `backend/Dockerfile` |
| Ước lượng | 7 ngày |
| Phụ thuộc | Phase 6 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Phạm vi trong phase này |
|---|---|---|
| [`docs/dynamic-core/README.md`](../dynamic-core/README.md), mục "GitOps cho nghiệp vụ" | Kiểm tra, xem trước, áp dụng, đổi phiên bản, quay lui; ba mức rủi ro | Toàn bộ, quay lui ở mức thay đổi an toàn |
| Cùng tài liệu, mục "Thế nào là đạt" | Mục 5 (đổi trường khi có dữ liệu) và mục 7 (báo dòng vi phạm) | Toàn bộ |
| [`docs/phase-6-case-core/04_TRADEOFFS_RISKS.md`](../phase-6-case-core/04_TRADEOFFS_RISKS.md), mục 3 | Bốn điều kiện giữ pgschema | Kiểm ngay đầu phase |

## 2. Lý do

Đây là đặt cược về tính nguyên gốc của lõi: khai báo thực thể thì nhiều nền tảng đã có, còn đổi mô hình an toàn trên hệ thống đang chạy là điểm khác biệt.

## 3. Phạm vi

- Trong phạm vi:
  - So sánh hai phiên bản mô hình, phân loại an toàn, cần chuyển dữ liệu, mất dữ liệu.
  - Thêm, đổi tên, xóa trường và thực thể; thêm quy tắc; thêm đơn vị, vai, lựa chọn.
  - Báo dòng vi phạm trước khi áp dụng; báo hồ sơ đang xử lý bị ảnh hưởng.
  - Xác nhận riêng cho thay đổi mất dữ liệu; kế hoạch hết hiệu lực khi phiên bản đang chạy đã đổi.
  - Quay lui về phiên bản trước cho thay đổi quay lui được.
- Ngoài phạm vi:
  - Đổi kiểu trường: bị từ chối kèm giải thích.
  - Đổi tên thực thể: bị từ chối kèm giải thích.
  - Màn hình xem kế hoạch và lịch sử: Phase 9.
  - Đổi mẫu quy trình của hồ sơ đang chạy: Phase 8, khi có mẫu thứ hai.
- Ranh giới: mã lõi không chứa tên thực thể của một lĩnh vực; dữ liệu không bao giờ mất nếu không có xác nhận riêng.

## 4. Kết quả đầu ra mong đợi (Definition of Done)

- Thêm và đổi tên một trường khi có dữ liệu: có kế hoạch xem trước, không build lại, dữ liệu còn nguyên, API và giao diện thấy ngay.
- Thêm trường bắt buộc hoặc quy tắc mà dữ liệu đang vi phạm: bị từ chối, kèm các dòng vi phạm.
- Xóa trường: không chạy nếu thiếu xác nhận; đổi tên mà quên khai báo tên cũ cũng được báo là mất dữ liệu.
- Kế hoạch xem trước khớp với những gì áp dụng thật.
- Áp dụng dở chạy lại được và về đúng trạng thái đích.
- Có kết luận cho bốn điều kiện của pgschema.
