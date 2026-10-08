# Trade-off và rủi ro: Phase 3

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Tách Core/App | Tách module Maven riêng / giữ 1 module, nhóm package rõ ràng | Giữ 1 module, nhóm package | Đủ để chứng minh ranh giới bằng code thật, không tốn công refactor module lớn | Không có ràng buộc compile-time cấm App import nội bộ Core sai cách (chỉ là convention, không bị build chặn) | Thấp — tách module sau này vẫn làm được, không mất code |
| TO2 | `WorkflowCase`/`WorkflowEvent` thay thế hay song song với field cũ trên `Diagnosis` | Thay thế hoàn toàn (xoá `approvalStatus` khỏi `Diagnosis`) / Giữ song song | Giữ song song | Không đổi logic nghiệp vụ hiện có (khóa PHI, điều kiện thu hoạch đang đọc `approvalStatus` trực tiếp), giảm rủi ro hồi quy | 2 nguồn sự thật tạm thời (`Diagnosis.approvalStatus` và `WorkflowCase.status`) phải đồng bộ mỗi lần đổi, dễ lệch nếu sau này có người sửa 1 chỗ mà quên chỗ kia | Vừa — dọn về 1 nguồn sau này cần rà hết chỗ đọc `approvalStatus` trước khi xoá |
| TO3 | APISIX đặt trước hay sau Spring Security | Đặt trước (xác thực ở gateway, backend tin tưởng) / Đặt trước nhưng backend vẫn tự kiểm lại (2 lớp độc lập) | 2 lớp độc lập | Đúng nguyên tắc "chặn 2 tầng" của dự án, backend vẫn an toàn nếu gateway bị cấu hình sai | Xác thực JWT 2 lần mỗi request (gateway + backend), tốn thêm một chút CPU/latency | Thấp — bỏ lớp gateway không ảnh hưởng backend vẫn tự bảo vệ được |
| TO4 | Ảnh cũ migrate ngay (Q2, đã chốt) | Xem mục 00_OVERVIEW Q2 | Migrate ngay | Không phải duy trì 2 code path đọc ảnh (filesystem cũ + MinIO mới) lâu dài | Nếu script migrate lỗi giữa chừng, một phần ảnh có thể tạm không truy cập được | Vừa — chạy lại script trên phần chưa migrate, không mất dữ liệu nếu chưa xoá filesystem cũ |

| TO5 | Nguồn image MinIO (Q3) | Build từ source / image bên thứ ba / store S3 khác | Build từ source tag chính thức | Biết chính xác mã nguồn đã chạy, vẫn là MinIO theo D4 | Lần dựng đầu thêm khoảng 2 phút, bản không còn được vá lỗi vì upstream đã archived | Thấp — chỉ đổi `image:` hoặc Dockerfile, backend dùng S3 API nên không đổi |
| TO6 | Host của link ảnh có hạn | Một host cố định trong cấu hình / ký theo host client dùng để gọi API | Ký theo header `Host` của request (host không hợp lệ thì dùng `GATEWAY_PUBLIC_URL`) | Web (`localhost`) và điện thoại (IP LAN) cùng nhận link tải được, không cần cấu hình theo từng máy | Link chỉ đúng cho host đã gọi API; chữ ký gắn với host nên gateway phải chuyển tiếp nguyên `Host` | Thấp |
| TO7 | Web phục vụ ở đâu | Cổng riêng của frontend, gọi API khác origin / qua gateway cùng origin | Qua gateway (`/*`) | Không cần CORS, không cần cấu hình URL API cho web, một địa chỉ duy nhất | Frontend không còn cổng riêng, redirect URI Keycloak đổi sang `localhost:9080` | Thấp |

| TO8 | Superset và portal qua gateway | Đường dẫn con `/superset` / listener thứ hai của gateway / giữ cổng riêng | Superset ở listener thứ hai (`9081`) của APISIX, portal ở `/portal/` | Một gateway, không service nào publish cổng riêng (trừ Mattermost) | Superset 3.1 không có tiền tố đường dẫn (giao diện React báo 404), nên người dùng thấy hai cổng; nâng lên Superset 4.x mới cho chung cổng | Thấp |

Kết luận: TO1-TO8 áp dụng ngay, không cần chờ chốt thêm.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Đổi gateway (WP3-03) làm gãy luồng đăng nhập/gọi API của web hoặc mobile, phát hiện muộn vì phần lớn test là thủ công trên compose thật | Cao | Cao (chặn toàn bộ demo) | Test T3-05/T3-06 chạy ngay sau khi đổi route, trước khi xoá publish cổng cũ. Giữ cổng backend/Keycloak publish tạm (song song với APISIX) trong vài ngày đầu, chỉ tắt hẳn sau khi xác nhận ổn định | Web/mobile báo lỗi kết nối hoặc 401/403 bất thường ngay sau khi đổi `.env`/`nginx.conf` | Mở lại publish cổng cũ tạm thời (đổi `.env`, không cần sửa code), điều tra route APISIX song song |
| R2 | Plugin `openid-connect` của APISIX cấu hình sai (sai `discovery`/`client_id`) chặn hết request hợp lệ, hoặc ngược lại cho qua request không hợp lệ | Vừa | Cao | Test kỹ với cả token hợp lệ và không hợp lệ trước khi coi WP3-03 xong; so sánh kết quả với lớp xác thực của backend (phải nhất quán) | APISIX trả 401 cho token mà backend vẫn chấp nhận được (hoặc ngược lại) | Tắt tạm plugin `openid-connect` ở APISIX, giữ lớp xác thực của backend làm lớp chính trong lúc sửa |
| R3 | Script migrate ảnh (WP3-04) chạy nhầm trên môi trường có dữ liệu không phải seed, xoá filesystem cũ trước khi xác nhận migrate đủ | Thấp (giai đoạn demo) | Cao nếu xảy ra (mất ảnh) | Không tự động xoá filesystem cũ trong script; xoá là bước tay riêng, chỉ làm sau khi đối chiếu số lượng object | Số object trong MinIO không khớp số file gốc sau khi chạy script | Giữ filesystem cũ (không xoá) cho tới khi có ít nhất một buổi demo chạy ổn định bằng MinIO |
| R4 | Rate limit (WP3-03) đặt ngưỡng quá thấp, chặn nhầm luồng demo bình thường (nhiều engineer cùng duyệt trong thời gian ngắn) | Vừa | Thấp | Đặt ngưỡng rộng ban đầu (ví dụ 30 request/phút/IP), chỉ siết lại sau khi có số liệu tải thật | Gặp 429 trong lúc demo hoặc test bình thường, không phải tấn công | Nâng ngưỡng hoặc tắt tạm plugin `limit-count` cho route đang bị chặn nhầm |
| R5 | Thêm 2 service mới (APISIX, MinIO) vào compose làm thời gian dựng lần đầu và RAM yêu cầu tăng thêm, ảnh hưởng máy giám khảo yếu | Vừa | Vừa | Đo lại thời gian dựng và RAM sau khi thêm 2 service, cập nhật README mục yêu cầu máy (đã ghi nhận là việc cần làm ở mục 17 `PROJECT_IMPROVEMENTS.md`) | `docker compose up` lần đầu chậm hơn đáng kể so với trước phase 3 | Ghi rõ trong README thời gian dựng mới, không có phương án giảm tải khác trong phạm vi phase này |

## 3. Nợ kỹ thuật chấp nhận

- `Diagnosis.approvalStatus` và `WorkflowCase.status` là 2 nguồn sự thật song song (TO2) — dọn về 1 nguồn để P4/P9 không phải quyết định lại từ đầu.
- Không tách module Maven riêng cho Core (TO1) — chỉ là convention package, không có ràng buộc compile-time.
- `iss` vẫn không được kiểm ở backend dù APISIX đã thống nhất hostname — để riêng vì ảnh hưởng rộng hơn phạm vi phase, xem `sso/README.md`.
- Giới hạn tốc độ tính theo `remote_addr`; sau mạng Docker mọi client trên cùng máy dùng chung một bộ đếm.
- Phản hồi `429` và `401` do gateway sinh ra không có dạng `ApiResponse` của backend; web và mobile hiển thị mã HTTP thay cho thông điệp tiếng Việt.
- `MINIO_ACCESS_KEY` và `MINIO_SECRET_KEY` là tài khoản gốc của MinIO, backend dùng chung; chưa tạo tài khoản dịch vụ riêng với quyền hẹp.
- Gateway chạy HTTP; HTTPS cho bản phát hành thuộc P10.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi: R1 xảy ra trong buổi test đầu tiên của WP3-03 (web/mobile không gọi được qua gateway) — quay lại publish cổng cũ ngay, không cố sửa tiếp trong lúc demo đang chờ.
- Rollback: APISIX/MinIO là service độc lập trong compose, tắt (`docker compose stop apisix minio`) và đổi `.env` trả về cấu hình publish cổng cũ không ảnh hưởng dữ liệu đã có; `WorkflowCase`/`WorkflowEvent` là bảng thêm mới, bỏ qua không đọc không ảnh hưởng logic nghiệp vụ hiện có (do giữ song song theo TO2).

## 5. Tác động ngoài phase

- Lên các phase sau: P4 tái dùng nguyên `workflow_case`/`workflow_event` cho duyệt thửa/vụ — nếu schema ở phase này thiếu cột gì, P4 phải thêm migration vá thêm, không viết lại từ đầu.
  P9 (nếu làm) tái dùng Identity/Workflow/Gateway nguyên trạng — đây là phase chứng minh trực tiếp cho lời hứa "không sửa Core khi đổi ứng dụng".
- Lên demo, tài liệu nộp bài, thời hạn: sơ đồ 3 tầng và sơ đồ luồng request (WP3-01) là tài liệu dùng trực tiếp trong báo cáo kỹ thuật và slide (P8), nên phải vẽ đúng với code thật, không vẽ trước rồi code lệch.
