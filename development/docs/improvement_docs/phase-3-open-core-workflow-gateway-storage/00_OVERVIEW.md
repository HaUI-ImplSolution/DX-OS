# Phase 3: Open-Core — workflow, gateway, storage, sơ đồ

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ước lượng | 5-7 ngày |
| Người phụ trách | {điền tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Phase 2 |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 7 | Kiến trúc Open-Core, Tầng 1 | Nghiêm trọng | Toàn bộ |
| mục 8 | Workflow / quy trình xử lý, phần Process | Nghiêm trọng | Toàn bộ, áp dụng trên luồng chẩn đoán (Diagnosis) có sẵn |
| mục 17 | Deploy / CI / hạ tầng | Quan trọng | Một phần: reverse proxy/HTTPS gộp vào gateway ở phase này |

Đây là phase quyết định điểm kiến trúc Open-Core (đề mục 11, 10 điểm) và là câu trả lời trực tiếp cho câu hỏi hội đồng số 1, số 4 (mục 20 `PROJECT_IMPROVEMENTS.md`).
Mọi quyết định công nghệ (D2 workflow, D3 gateway, D4 storage) đã chốt ở phase 0 — phase này chỉ còn việc thiết kế và code theo đúng lựa chọn đó.

## 2. Lý do

- Vấn đề đang gặp: backend hiện là một khối gộp nghiệp vụ nông nghiệp, chưa chỉ ra được phần nào là Core dùng chung và phần nào là ứng dụng DX-Farm.
  Trạng thái duyệt (`Diagnosis.approvalStatus`, `CropSeason.status`) là enum + field phẳng trên entity, không có lịch sử, không có người xử lý (`assignedTo`), không có API danh sách chờ — thiếu đúng 3 trong 5 ý đề yêu cầu cho phần Process.
  Client (web, mobile) gọi thẳng cổng backend (8081) và Keycloak (8180) qua IP LAN, AI service (8000) publish ra host nhưng 2/3 endpoint không xác thực (đã xử lý ở P1, nhưng vẫn publish ra host).
  Ảnh thửa ruộng phục vụ qua `/uploads/**` công khai không cần token, lưu trên filesystem local của container backend.
- Vì sao làm ở phase này: đây là phase rủi ro cao nhất của dự án theo chính đánh giá của `PHASE_PLAN.md`, vì nó định hình ranh giới mà P4-P9 đều dựa vào.
  Làm sau sẽ phải sửa lại mọi luồng nghiệp vụ đã viết trên giả định gọi thẳng backend.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | `workflow_case`/`workflow_event`, storage service, tách package Core/App |
| Frontend (web) | Có | Đổi base URL API sang qua gateway, đổi cách hiển thị ảnh (presigned URL) |
| Mobile | Có | Đổi `EXPO_PUBLIC_API_BASE_URL`/`EXPO_PUBLIC_KEYCLOAK_URL` sang gateway |
| AI | Không | Không đổi hành vi, chỉ không còn publish cổng ra host |
| Data / Seed / Migration | Có | Migration bảng workflow, migrate ảnh cũ sang MinIO |
| Infra / Deploy / CI | Có | Thêm service APISIX, MinIO vào compose |
| Docs / PoF | Có | Sơ đồ 3 tầng, sơ đồ luồng request |

## 4. Phạm vi

- Trong phạm vi: WP3-01 đến WP3-04 ở `01_PLAN.md` (ranh giới Core/App + sơ đồ, workflow dùng chung áp cho Diagnosis, gateway APISIX, storage MinIO).
- Ngoài phạm vi (không làm ở phase này): áp workflow dùng chung cho Parcel/CropSeason (P4 làm, chỉ tái dùng bảng đã có ở đây), bật kiểm `iss` ở backend (vẫn tắt theo đánh đổi đã ghi ở `sso/README.md`, dù gateway đã thống nhất hostname — để riêng vì ảnh hưởng rộng hơn phạm vi phase).
- Giả định: D2 (tự làm workflow), D3 (APISIX standalone), D4 (MinIO) đã chốt ở phase 0, không mở lại thảo luận công cụ.

## 5. Ưu điểm và hạn chế

- Ưu điểm: có bằng chứng cụ thể cho kiến trúc 3 tầng; đóng luôn 2 lỗ hổng bảo mật còn treo từ mục 9 (publish cổng AI, `/uploads` công khai).
- Hạn chế: khối lượng sửa lớn nhất trong toàn bộ dự án, đụng tới mọi client (web, mobile).
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (giữ API cũ chạy song song trong lúc chuyển, rủi ro hồi quy khi đổi gateway).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: mở sơ đồ 3 tầng, chỉ đúng Tầng 1 (Core: Identity, Workflow, Gateway, Storage), H-P-D-I, Tầng 3 (DX-Farm).
  Gọi API qua gateway (không gọi thẳng backend), xem timeline duyệt chẩn đoán có lịch sử đầy đủ, xem ảnh qua link có hạn.
- Sản phẩm bàn giao: bảng `workflow_case`/`workflow_event`, APISIX cấu hình file-based, MinIO + storage service, sơ đồ 3 tầng và sơ đồ luồng request (file ảnh/draw.io hoặc tương đương trong `development/docs/`).
- Tiêu chí đo được: gọi `/disease/predict` thẳng cổng AI từ máy ngoài compose network thất bại (cổng không còn publish); link `/uploads/...` cũ không còn truy cập công khai được; danh sách chờ duyệt chẩn đoán trả đúng dữ liệu từ `workflow_case`.
- Bằng chứng: ảnh chụp/log gọi gateway, output test T3-xx, sơ đồ đã vẽ.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: `Household`/ma trận quyền (P2) dùng làm ví dụ cụ thể trong sơ đồ H (Human); `@Version` (P1) áp dụng tiếp cho `workflow_case` nếu cần.
- Phase sau cần từ phase này: P4 tái dùng `workflow_case`/`workflow_event` cho duyệt thửa/vụ; P9 (nếu làm) tái dùng Identity/Workflow/Gateway nguyên trạng, không sửa Core.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: đề mục 11 (kiến trúc Open-Core, 10 điểm) — tiêu chí nặng nhất liên quan tới phase này.
- Dùng làm bằng chứng cho: câu hỏi hội đồng số 1, 4 (mục 20 `PROJECT_IMPROVEMENTS.md`); câu hỏi "tăng từ 100 lên 100.000 người dùng thì sao" (gateway + storage tách rời là một phần câu trả lời).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | `workflow_event` có cần cột `metadata` dạng JSONB để lưu dữ liệu riêng từng loại quy trình không | Có, JSONB tuỳ chọn / Không, chỉ các cột cố định | 05/10/2026 | Có, JSONB tuỳ chọn — P4/P9 thêm loại quy trình mới không cần migration riêng mỗi lần |
| Q2 | Ảnh cũ trong `/uploads` local có migrate sang MinIO ngay hay chạy song song | Migrate ngay (một script, một lần) / Chạy song song (ảnh mới vào MinIO, ảnh cũ đọc từ filesystem cho tới khi dọn) | 05/10/2026 | Migrate ngay bằng một script — dữ liệu ở quy mô demo, chạy song song 2 nguồn ảnh lâu dài tốn công bảo trì hơn lợi ích |
| Q3 | Image MinIO chính thức không còn phát hành (Docker Hub 404, quay.io 401, repo upstream archived) thì lấy MinIO từ đâu | Build từ source tag chính thức / Dùng image bên thứ ba / Đổi sang store S3 khác | 06/10/2026 | Build từ source tag `RELEASE.2025-10-15T17-29-55Z` (`minio/Dockerfile`): giữ D4, tránh image không rõ nguồn; code dùng S3 API nên đổi store sau này không sửa backend |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] Sơ đồ 3 tầng, sơ đồ luồng request: `development/docs/submission/ARCHITECTURE_OPEN_CORE.md`.
- [x] Swagger cho endpoint `workflow`/danh sách chờ mới.
- [x] `deploy/.env.example`, `sso/README.md` nếu đổi cấu hình hostname/Keycloak qua gateway.
