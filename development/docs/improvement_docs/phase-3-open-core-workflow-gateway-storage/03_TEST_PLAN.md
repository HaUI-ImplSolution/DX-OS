# Kế hoạch kiểm thử: Phase 3

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống, không coverage đầy đủ happy path.
Phase này đụng hạ tầng (gateway, storage) nên một số case bắt buộc chạy thủ công trên compose thật, không chỉ unit test.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP3-02/04; thủ công trên compose thật cho WP3-03/04 (gateway, storage là hành vi hạ tầng, khó mock đầy đủ).
- Môi trường: clone sạch + `docker compose up -d --build` với APISIX/MinIO đã thêm.
- Dữ liệu và tài khoản test: 1 báo cáo có ảnh, 1 chẩn đoán `PENDING`; farmer1, engineer1, admin1.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T3-01 | WP3-02 | Happy path | ENGINEER approve 1 chẩn đoán `PENDING` | 1 `WorkflowEvent` mới tạo, `WorkflowCase.status = APPROVED` | | |
| T3-02 | WP3-02 | Poka-yoke | Reject rồi approve lại cùng 1 `WorkflowCase` (nếu luồng nghiệp vụ cho phép chẩn đoán lại) | 2 `WorkflowEvent` riêng biệt, event cũ không bị sửa/xoá | | |
| T3-03 | WP3-02 | Đầy đủ | `GET /workflow-cases?caseType=DIAGNOSIS_APPROVAL&status=PENDING` | Số lượng khớp đúng số `Diagnosis.approvalStatus = PENDING` thật trong DB | | |
| T3-04 | WP3-03 | Bảo mật hạ tầng | Gọi `curl IP:8081/...` và `curl IP:8180/...` từ máy ngoài compose network | Kết nối thất bại (cổng không publish) | | |
| T3-05 | WP3-03 | Hồi quy | Đăng nhập web, thực hiện 1 luồng nghiệp vụ bất kỳ (xem danh sách thửa) qua APISIX | Hoạt động như trước khi đổi gateway | | |
| T3-06 | WP3-03 | Hồi quy | Đăng nhập mobile, gửi 1 báo cáo qua APISIX | Hoạt động như trước khi đổi gateway | | |
| T3-07 | WP3-03 | Poka-yoke | Gọi upload ảnh liên tục vượt ngưỡng rate limit đã cấu hình | 429 sau khi vượt ngưỡng | | |
| T3-08 | WP3-04 | Bảo mật dữ liệu | `GET /uploads/{file}` (route cũ) sau khi tắt resource handler | 404 | | |
| T3-09 | WP3-04 | Hồi quy | Web/mobile mở màn xem ảnh báo cáo | Ảnh load được bằng presigned URL mới | | |
| T3-10 | WP3-04 | Hồi quy | Tạo báo cáo mới, để backend gọi AI predict như luồng hiện có | AI predict thành công bằng ảnh lấy qua presigned URL | | |

## 3. Hồi quy (không được hỏng)

- [ ] Khóa PHI (P1) vẫn chặn đúng sau khi đổi storage/gateway.
- [ ] Farmer chỉ thấy thửa và báo cáo của mình (P2) không đổi sau khi đổi gateway.
- [ ] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động qua APISIX.
- [ ] Thông báo Mattermost vẫn gửi, ảnh trong thông báo load được bằng presigned URL mới.
- [ ] Dựng từ clone sạch (gồm APISIX, MinIO mới) bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T3-01 đến T3-10 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test, ảnh chụp sơ đồ 3 tầng/luồng request, log gọi `curl` xác nhận cổng không publish.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
