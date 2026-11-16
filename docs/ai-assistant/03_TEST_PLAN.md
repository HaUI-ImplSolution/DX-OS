# Kế hoạch kiểm trợ lý nghiệp vụ động

## 1. Phạm vi và cách ghi bằng chứng

Ma trận CP là tiêu chí cho bản mới, không phải kết quả đã chạy hoặc danh sách test file mới.
Mỗi kết quả phải gắn với revision mã/model/cấu hình, môi trường, dữ liệu giả, thời điểm và bằng chứng ngoài Git; phân biệt mock, inference thật, Core thật và nghiệm thu người dùng.

Tận dụng test Node trong `assistant/test/`, bộ kiểm API/E2E và test backend hiện có theo phạm vi thực sự đổi.
Không thêm framework/test file, snapshot response hoặc test chỉ lặp lại validation; ưu tiên guard quyền, race, timeout và tính động.
Kiểm compile/diff khi sửa mã; test/model/Docker chỉ chạy ở mốc kiểm được cho phép và cần bằng chứng, không dựng stack trong đợt lập kế hoạch.
CONTRIBUTING và các check CI bắt buộc vẫn áp dụng; chưa chạy thì ghi rõ chưa chạy.

## 2. Ma trận nghiệm thu

| Mã | Tình huống và kết quả cần đạt | Loại kiểm |
|---|---|---|
| CP-01 | LICENSE/NOTICE/provenance đúng phiên bản/artifact và bundled; model/dữ liệu đạt cam kết trước đóng gói, thiếu bằng chứng không được ghi đạt OSAID | Giấy phép upstream, artifact và dữ liệu |
| CP-02 | Parser/schema/Console giữ nhiệm vụ sau load/edit/apply; tham chiếu sai bị chặn, YAML cũ không đổi hành vi | Parser, schema, Console và Core thật |
| CP-03 | A/B có phạm vi khác nhau; task/metadata/record/API không cho đọc hoặc tạo đề xuất chéo quyền, ADMIN nền tảng không thay vai nghiệp vụ | API và SSO của hai chủ |
| CP-04 | Đổi trường/lựa chọn/hướng dẫn/nguồn hoặc bỏ task sau khi tạo proposal; xác nhận cũ bị từ chối, không ghi sai cột | Node và Core/giao diện thật |
| CP-05 | API/OpenAPI/schema/envelope/status/errorCode/VI-EN khớp hành vi, client cũ không có AI vẫn dùng được | Schema, OpenAPI và kiểm kiểu |
| CP-06 | Hai cập nhật cùng version, apply mô hình cạnh tranh confirm và cập nhật không đổi giá trị; guard phiên bản được Core kiểm, không chỉ kiểm trước ở provider | Core thật với cập nhật đồng thời |
| CP-07 | JSON lỗi, kiểu sai, choice ngoài danh mục, nguồn không tồn tại hoặc output quá giới hạn: không có proposal xác nhận được, không gọi tool/vòng sửa tự trị | Node và inference thật |
| CP-08 | Bộ ca VI gồm đủ/thiếu/mâu thuẫn căn cứ; đề xuất đúng dữ kiện, nguồn có thật và hỗ trợ kết luận, thiếu căn cứ không tự bịa quy định | Model thật và đánh giá nội dung |
| CP-09 | Injection trong hồ sơ/nguồn, nguồn khác mô hình, URL/header/owner giả từ browser: không đổi nhiệm vụ/quyền, không lộ token hoặc đọc ngoài phạm vi | HTTP, phân quyền và injection trên model thật |
| CP-10 | Hủy/hết TTL/sai chủ/sửa giá trị: không ghi; sửa phải tạo snapshot xác nhận mới, model không có quyền confirm | Node và giao diện thật |
| CP-11 | Confirm trùng, restart lúc executing, thu hồi quyền, hồ sơ terminal và timeout sau ghi: một claim, Core chặn sai quyền, uncertain không replay | Node và Core thật |
| CP-12 | UI hồ sơ có nhãn đúng metadata, trước/sau/nguồn, lỗi và receipt; bản nháp chưa lưu không bị AI ghi đè, hai tab không làm mất sửa của người dùng | Giao diện trên Core/model thật |
| CP-13 | Model thiếu/tắt/bận, input quá dài, output bị cắt, timeout/hủy, session Core hết hạn: lỗi rõ, slot được giải phóng, không fallback cloud hoặc cản thao tác Core | Transport và model thật |
| CP-14 | Snapshot state dưới bảo trì, restore thành công và lỗi migration: không sinh lại key/AAD, lỗi nâng cấp không tự tạo lại key | Snapshot, nâng cấp và restore |
| CP-15 | State 0/1/nhiều chủ, proposal pending/executing và migration lỗi: giữ lịch sử/chủ, không gửi lại ghi | Migration và dữ liệu phiên bản cũ |
| CP-16 | Route ngoài hợp đồng: JWT/Origin/body vẫn kiểm đúng, không lưu credential, gọi model hoặc ghi Core | HTTP và rà consumer |
| CP-17 | Dockerfile/Compose/env/APISIX/CLI/backup/CI chỉ có consumer của chức năng được hỗ trợ; Core workflow/outbox/nhắc hạn và dữ liệu cũ được giữ | Compose, CLI và rà consumer |
| CP-18 | Các ca owner/claim/encryption/timeout vẫn được kiểm khi tích hợp provider; không xóa guard để tránh lỗi | Bộ kiểm guard hiện có |
| CP-19 | Lockfile/SBOM/catalog/notices/REUSE/docs nhất quán; không bỏ giấy phép dùng chung hoặc hướng dẫn không khớp chức năng | CI/compliance và rà tài liệu |
| CP-20 | Cài mới/nâng cấp/rollback và cấu hình model off: Core khởi động độc lập, không tải trọng số lúc boot/request, restore không gửi lại ghi | Cài mới, nâng cấp và rollback |
| CP-21 | Hai domain khác tên thực thể/trường/choice dùng cùng mã; thay YAML làm UI/task đổi, kiểm hồi quy approval/work_item/reservation và mô hình không khai AI | Core/model/giao diện thật trên nhiều mẫu |
| CP-22 | Tài nguyên cold/warm/idle/busy/cancel/model off theo cùng workload; công bố đủ số mẫu và tách RAM/VRAM/swap/Core, không suy SLA từ một ca | Đo host và workload cố định |
| CP-23 | Compile/typecheck và compliance/secret/doc refs/diff theo phần đổi; rà reference còn lại theo consumer thật | Build, compliance và diff |
| CP-24 | Người dùng demo trên Core và model thật, xem được đề xuất/nguồn/confirm/receipt; review chất lượng, tính động và giới hạn chưa thực hiện | Người dùng và tech lead nghiệm thu |

## 3. Điều kiện đánh giá chất lượng và độ nhẹ

Tạo bộ dữ liệu giả có giấy phép mở cho hai domain, gồm nhiệm vụ text và choice; có ca thiếu thông tin, nguồn mâu thuẫn, injection và câu tiếng Việt không rõ.
Chốt đáp án chấp nhận, lỗi chặn nghiệm thu và ngưỡng chất lượng với tech lead trước khi đo; không hạ ngưỡng sau khi nhìn kết quả.
Các ca phân quyền, ghi trước xác nhận, bịa nguồn hoặc báo đã lưu sai phải không có lỗi trong bộ nghiệm thu.
Ghi riêng số đề xuất đúng, sai, từ chối đúng/sai và số ca cần người sửa; không dùng JSON hợp lệ làm thước đo đúng nghiệp vụ.

Đo trên một host với cùng revision Core, model, tài liệu và dữ liệu; ghi rõ backend CPU/GPU, context, input/output và concurrency.
Tách thời gian nạp, chuẩn bị dữ liệu, prefill/decode, lưu proposal, xác nhận và ghi Core.
Đo Core/OS riêng, provider idle, model loaded-idle, một lượt, hàng chờ/busy, timeout/hủy và dừng inference.
Không so phần trăm cải thiện khi khác workload, revision mã hoặc model.
Không đưa bằng chứng mock vào ô nghiệm thu Core/model thật và không thay kết quả người dùng bằng CI xanh.

Giữ bộ đối chứng trên mười mẫu hiện có và các ca bổ sung tách biệt, với đáp án/tiêu chí chốt trước khi chạy.
Tạo hồ sơ mới khi chạy lại để không dùng dữ liệu đã được đề xuất trước đó làm đầu vào.
Đo riêng giữ đúng các ca rõ nghĩa, từ chối đúng khi thiếu dữ kiện, quan hệ số, cảnh báo danh mục và độ trễ.
Giữ một lượt suy luận; lỗi nội dung còn lại được theo dõi trong [BACKLOG](../dynamic-core/BACKLOG.md).
Bằng chứng và fixture để ngoài Git; lỗi nội dung bị người kiểm hủy không được tính là ứng dụng tự chặn lỗi.

## 4. Giới hạn của phạm vi này

Dùng inference và nguồn giả lập của bản cài để kiểm; không gửi dữ liệu ra dịch vụ bên ngoài.
Hỏi đáp mở, embedding/reranker và tác tử tự tạo proposal theo sự kiện chưa được nghiệm thu bằng ma trận CP.
Giữ các mục đó trong #210/định hướng hiện hành, không đánh dấu hoàn tất thay chúng.
