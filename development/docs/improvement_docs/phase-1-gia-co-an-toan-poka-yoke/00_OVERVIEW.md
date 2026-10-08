# Phase 1: Gia cố an toàn, poka-yoke và nền dựng sạch

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong (CI GitHub xanh, 6 job) |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ước lượng | 3-4 ngày |
| Người phụ trách | {điền tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Phase 0 (đã xong) |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 9 | Phân quyền và bảo mật | Nghiêm trọng | Một phần: secret mặc định, auth AI, upload thật, Keycloak dev-mode. Phân quyền theo hành động (role x thao tác) để ở P2 |
| mục 10 | Lỗ hổng nghiệp vụ / poka-yoke | Nghiêm trọng | Toàn bộ |
| mục 17 | Deploy / CI / hạ tầng | Quan trọng (một phần Nghiêm trọng) | Một phần: mở rộng CI, script reset, log theo profile. Reverse proxy/HTTPS và backup/restore để ở P3/P8 |

Đề thi: mục 5 (secret và mật khẩu mặc định, PoF loại ngay nếu không xử lý trước demo), mục 12 câu hỏi hội đồng về build từ clone sạch.
Đây là phase đầu tiên có code, không phụ thuộc kết quả D1-D4 của phase 0 (chỉ cần D6 đã chốt — bật test cho case crash/logic).

## 2. Lý do

- Vấn đề đang gặp: secret/mật khẩu mặc định đang nằm trong file commit (`sso/realm-dxfarm.json`, `deploy/.env.example`); khóa PHI (thời gian cách ly sau phun thuốc) — cơ chế được dự án lấy làm điểm nhấn — có nhiều lỗ hổng khiến khóa vô hiệu hoặc kéo dài vô lý; 2 trong 3 endpoint AI không xác thực dù cổng AI publish ra host.
- Vì sao làm ở phase này: đây là lỗi có nguy cơ bị loại/trừ điểm nặng nhất theo rubric PoF (mục 5), và nhỏ/độc lập nên làm trước, không chặn ai mà không bị P2/P3 chặn lại.
  Khóa PHI sai sẽ làm mọi demo sau này mất tin cậy.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Validation, Clock/ZoneId thống nhất, `@Version`, exception mới |
| Frontend (web) | Không | |
| Mobile | Không | |
| AI | Có | Bắt buộc `X-Service-Api-Key` cho mọi endpoint |
| Data / Seed / Migration | Có | Dọn dữ liệu cũ vi phạm ràng buộc mới trước khi bật constraint |
| Infra / Deploy / CI | Có | `.env.example`, compose, CI mở rộng, script reset |
| Docs / PoF | Có | README/CHANGELOG, `LICENSES.md` nếu đổi dependency |

## 4. Phạm vi

- Trong phạm vi: WP1-01 đến WP1-04 ở `01_PLAN.md` (secret, khóa PHI, AI auth + upload thật, CI/script reset/log).
- Ngoài phạm vi (không làm ở phase này): phân quyền theo hành động/role (P2), Household/migration dữ liệu nông hộ (P2), gateway/MinIO/workflow dùng chung (P3), reverse proxy + HTTPS, backup/restore (P3/P8).
- Giả định: D6 (phase 0) đã chốt — chỉ viết test cho case ảnh hưởng logic/an toàn hệ thống, không coverage đầy đủ.

## 5. Ưu điểm và hạn chế

- Ưu điểm: đóng lỗ hổng có thể làm sai lệch toàn bộ demo khóa PHI; xử lý xong rủi ro loại thẳng vì secret lộ trong repo trước khi public.
- Hạn chế: siết validation làm một số luồng cũ (demo, dữ liệu mẫu) không chạy được nữa cho tới khi seed cập nhật.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (ngưỡng ngày hợp lệ của `appliedAt`, xử lý dữ liệu cũ vi phạm ràng buộc vụ mùa).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: khai `appliedAt` lùi quá N ngày hoặc ở tương lai bị từ chối (422); gọi `AI /disease/predict` không có `X-Service-Api-Key` bị 401; dựng lại từ clone sạch bằng một script, mọi service `healthy`.
- Sản phẩm bàn giao: code đã merge, seed demo cập nhật (không còn dữ liệu vi phạm ràng buộc mới), CI chạy test thật (không còn `-DskipTests`), script reset, `gitleaks` (hoặc tương đương) chạy sạch trên lịch sử commit.
- Tiêu chí đo được: 6 tình huống ở bảng mục 3 `PHASE_PLAN.md` phần P1 đều đổi đúng "Sau phase"; `03_TEST_PLAN.md` mục hồi quy không có case fail.
- Bằng chứng: log CI xanh, kết quả quét secret, output test case T1-xx.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: D6 (phase 0) — phạm vi test.
- Phase sau cần từ phase này: P2 kế thừa Clock/ZoneId và pattern validation; P3 kế thừa yêu cầu "mọi endpoint AI có auth" khi thiết kế gateway; P8 kế thừa CI đã mở rộng và script reset để làm E2E.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: đề mục 5 (secret và mật khẩu mặc định — loại ngay nếu không khắc phục trước demo); đề mục 12 câu hỏi hội đồng "build từ clone sạch được không".
- Dùng làm bằng chứng cho: câu trả lời hội đồng về bảo mật (mục 20 `PROJECT_IMPROVEMENTS.md`); phần "khóa PHI" trong demo chính (điểm nhấn nghiệp vụ của dự án).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | `appliedAt` được phép lùi tối đa bao nhiêu ngày so với hôm nay | 14 ngày / 30 ngày / {khác} | 05/10/2026 | 30 ngày — đủ dư cho sai số khai báo trễ thực tế, vẫn chặn input vô lý (hàng tháng/hàng năm); kỹ sư có thể điều chỉnh lại sau khi có dữ liệu thật |
| Q2 | Dữ liệu demo cũ vi phạm ràng buộc "1 vụ ACTIVE/thửa" xử lý thế nào | Xóa và seed lại / Viết script chuyển các vụ thừa sang trạng thái khác | 05/10/2026 | Xóa và seed lại — đây là dữ liệu demo không phải dữ liệu thật, viết script chuyển trạng thái phức tạp hơn mà không có lợi ích thêm |
| Q3 | Công cụ quét secret | gitleaks (CLI, chạy tay + CI) / trufflehog / khác | 05/10/2026 | gitleaks — MIT license (OSI), chạy được cả local (`gitleaks detect`) và CI, không cần tài khoản/dịch vụ ngoài |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] Swagger (springdoc) cho các response lỗi mới (422 khóa PHI, 401 AI).
- [x] `deploy/.env.example` (bỏ mật khẩu mặc định), `ai/app/cv/README.md` nếu đổi hành vi auth.
