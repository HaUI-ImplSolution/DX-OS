# Kế hoạch kiểm thử: Phase 3

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống, không coverage đầy đủ happy path.
Phase này đụng hạ tầng (gateway, storage) nên một số case bắt buộc chạy thủ công trên compose thật, không chỉ unit test.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP3-02/04; thủ công trên compose thật cho WP3-03/04 (gateway, storage là hành vi hạ tầng, khó mock đầy đủ).
- Môi trường: clone sạch + `docker compose up -d --build` với APISIX/MinIO đã thêm.
- Dữ liệu và tài khoản test: farmer1, engineer1, admin1 (README gốc).
  Dữ liệu mẫu nạp thẳng vào DB cục bộ, không nằm trong repo vì repo chỉ seed record chuyên gia đã duyệt:
  - Hộ `H0001` gắn với farmer1 (thửa `P-CT-01` vụ đang canh tác, `P-CT-02` vụ đã thu hoạch) và hộ `H0002` không có tài khoản (thửa `P-ST-01`, farmer1 không được thấy).
  - Trên `P-CT-01`: báo cáo 1 (chẩn đoán `PENDING`, đang ở danh sách chờ), báo cáo 2 (chẩn đoán `APPROVED`, có timeline), báo cáo 3 (chưa chẩn đoán, dùng để thử gọi AI).
  - Chẩn đoán mẫu có `model_version = demo-seed`, không kèm luật thuốc (nhánh cần kỹ sư xem xét); ảnh mẫu là ảnh tổng hợp, không phải lá lúa.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T3-01 | WP3-02 | Happy path | ENGINEER approve 1 chẩn đoán `PENDING` | 1 `WorkflowEvent` mới tạo, `WorkflowCase.status = APPROVED` | | Đạt. `WorkflowServiceIntegrationTest` và chạy thật qua gateway: duyệt thêm đúng 1 event, case thành `APPROVED` |
| T3-02 | WP3-02 | Poka-yoke | Reject rồi approve lại cùng 1 `WorkflowCase` (nếu luồng nghiệp vụ cho phép chẩn đoán lại) | 2 `WorkflowEvent` riêng biệt, event cũ không bị sửa/xoá | | Đạt. Test PostgreSQL thật: `REJECTED` rồi gửi lại rồi `APPROVED` giữ đủ 4 event theo thứ tự, event cũ không đổi; trigger chặn `UPDATE`/`DELETE` event. Luồng nghiệp vụ hiện chưa cho chẩn đoán lại nên kiểm ở tầng `WorkflowService` |
| T3-03 | WP3-02 | Đầy đủ | `GET /workflow-cases?caseType=DIAGNOSIS_APPROVAL&status=PENDING` | Số lượng khớp đúng số `Diagnosis.approvalStatus = PENDING` thật trong DB | | Đạt. Danh sách `PENDING` khớp số case `PENDING` trong DB (test) và `WorkflowMigrationTest` kiểm case dựng từ chẩn đoán cũ |
| T3-04 | WP3-03 | Bảo mật hạ tầng | Gọi `curl IP:8081/...` và `curl IP:8180/...` từ máy ngoài compose network | Kết nối thất bại (cổng không publish) | | Đạt. Kết nối tới cổng 8081, 8180, 8000, 9000 từ máy chủ bị từ chối; chỉ 9080 mở |
| T3-05 | WP3-03 | Hồi quy | Đăng nhập web, thực hiện 1 luồng nghiệp vụ bất kỳ (xem danh sách thửa) qua APISIX | Hoạt động như trước khi đổi gateway | | Đạt. Đăng nhập web bằng trình duyệt tại `localhost:9080` (chuyển sang Keycloak qua gateway, quay lại app, tải danh sách thửa); token sai chữ ký bị APISIX trả 401 |
| T3-06 | WP3-03 | Hồi quy | Đăng nhập mobile, gửi 1 báo cáo qua APISIX | Hoạt động như trước khi đổi gateway | | Đạt. Token client `dxfarm-mobile` qua gateway; app trên máy thật đăng nhập và gửi báo cáo qua gateway (xác nhận của người dùng) |
| T3-07 | WP3-03 | Poka-yoke | Gọi upload ảnh liên tục vượt ngưỡng rate limit đã cấu hình | 429 sau khi vượt ngưỡng | | Đạt. 40 lần upload liên tiếp: 30 lần `201`, 10 lần `429` |
| T3-08 | WP3-04 | Bảo mật dữ liệu | `GET /uploads/{file}` (route cũ) sau khi tắt resource handler | 404 | | Đạt. `GET /uploads/{file}` trả `404`; link ảnh không chữ ký hoặc sai chữ ký trả `403` |
| T3-09 | WP3-04 | Hồi quy | Web/mobile mở màn xem ảnh báo cáo | Ảnh load được bằng presigned URL mới | | Đạt. `imageUrl` tải được qua gateway (kể cả bằng `127.0.0.1`); web và app mobile hiển thị ảnh (xác nhận của người dùng) |
| T3-10 | WP3-04 | Hồi quy | Tạo báo cáo mới, để backend gọi AI predict như luồng hiện có | AI predict thành công bằng ảnh lấy qua presigned URL | | Đạt theo xác nhận của người dùng. Phần tôi tự kiểm: AI tải ảnh qua link nội bộ và chạy mô hình; ảnh thử của tôi và một ảnh chụp lại màn hình đều không đủ tin cậy do giới hạn của mô hình |

## 3. Hồi quy (không được hỏng)

- [x] Khóa PHI (P1) vẫn chặn đúng sau khi đổi storage/gateway.
  `DiagnosisServiceApplyTest` đạt.
- [x] Farmer chỉ thấy thửa và báo cáo của mình (P2) không đổi sau khi đổi gateway.
  `PermissionMatrixTest` đạt; farmer qua gateway thấy thửa của hộ mình.
- [x] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
  `ConcurrencyIntegrationTest` và `DiseaseReportServiceTest` đạt.
- [x] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động qua APISIX.
  Web và Superset kiểm bằng trình duyệt, mobile trên máy thật.
- [x] Thông báo Mattermost vẫn gửi, ảnh trong thông báo load được bằng presigned URL mới. Kiểm ở P4 bằng báo cáo thật: thông báo vào kênh `ky-su-nong-nghiep`, link ảnh trả 200.
  Link ảnh công khai đã kiểm bằng `StorageServiceTest`.
- [x] Dựng từ clone sạch (gồm APISIX, MinIO mới) bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.
  Đã chạy `deploy/reset.sh` từ thư mục dữ liệu trống, mọi service healthy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T3-01 đến T3-10 đạt.
- Mọi case hồi quy ở mục 3 đạt, gồm Mattermost kèm ảnh (kiểm ở P4).
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test, ảnh chụp sơ đồ 3 tầng/luồng request, log gọi `curl` xác nhận cổng không publish.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
