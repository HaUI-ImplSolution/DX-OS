# Đánh đổi và risk-off cho trợ lý nghiệp vụ động

Đọc cùng [overview](./00_OVERVIEW.md), [plan](./01_PLAN.md), [checklist](./02_CHECKLIST.md) và [test plan](./03_TEST_PLAN.md).
Các điều kiện chất lượng, restore toàn stack và nghiệm thu còn mở; tiêu chí kiểm ở [test plan](./03_TEST_PLAN.md).

## 1. Đánh đổi của phạm vi mới

| Lựa chọn | Lợi ích | Giới hạn phải công bố |
|---|---|---|
| Một năng lực đề xuất trường, cấu hình theo mô hình | Nhiều domain dùng chung, nhiệm vụ ngắn và demo rõ | Chỉ text/choice đơn trị; không hứa mọi nghiệp vụ hoặc hành động tự trị |
| Hệ thống cấp ngữ cảnh, một lượt inference | Không cần LLM khám phá API hoặc điều phối công cụ | Người dùng phải mở đúng hồ sơ/chọn nhiệm vụ; không có hội thoại tổng quát |
| UI ngay trên hồ sơ | Tận dụng giao diện, đăng nhập và form hiện có | Chưa có kênh hội thoại hoặc thông báo riêng của trợ lý |
| Một provider và inference chung | Một dịch vụ dùng chung, state tách theo chủ | Model vẫn chiếm RAM/VRAM và có tranh chấp slot; cần đo thật |
| Nguồn ngắn được cấu hình | Có căn cứ và provenance mà chưa cần hạ tầng truy xuất lớn | Không thay RAG/hỏi đáp hoặc embedding/reranker của toàn bộ #210 |
| State có schema và migration | Giữ lịch sử và có đường khôi phục | Vẫn cần bảo vệ backup/key/transcript, migration không được purge dữ liệu |

## 2. Rủi ro và điều kiện risk-off

| Rủi ro | Biện pháp | Bằng chứng cần có |
|---|---|---|
| Thu hẹp thành một ứng dụng cố định | Trường/task/nguồn lấy từ YAML, không switch theo ngành | CP-02/21: hai domain và đổi YAML không sửa mã |
| Schema mới bị Console bỏ mất hoặc model upgrade sai | Đồng bộ parser/schema/form/editor/plan/metadata | CP-02/04: vòng edit/apply và model cũ |
| Khai báo AI trở thành quyền mới | Task chỉ giới hạn dữ liệu sử dụng; Core giữ access hiện có | CP-03/09: sai chủ/role/scope bị từ chối |
| Model đoán sai hoặc bịa căn cứ | Một đầu ra hữu hạn, kiểm cấu trúc/choice/nguồn; người xem căn cứ | CP-07/08: bộ ca VI có đáp án và ca thiếu thông tin |
| Nguồn hợp lệ nhưng không hỗ trợ kết luận | Đánh giá nội dung đề xuất với đoạn dẫn, không chỉ kiểm source ID | CP-08/24: review ngữ nghĩa và nghiệm thu thực |
| Injection hoặc lộ dữ liệu | Coi hồ sơ/nguồn là dữ liệu; context tối thiểu, không token/tool tùy ý | CP-09: injection và cách ly hai chủ/mô hình |
| Proposal cũ ghi lên schema/nguồn mới | Gắn revision và kiểm lại; Core guard version/modelVersion trong giao dịch | CP-04/06/11: race với edit/apply/thu hồi quyền |
| Sửa giá trị sau xác nhận hoặc ghi lặp | Snapshot bất biến, xem lại sau sửa, atomic claim và receipt | CP-10/11: hai confirm và restart/timeout |
| Migration làm mất state hoặc thực thi lại lệnh ghi | Snapshot, migration có phiên bản và không replay | CP-14/15/20: nâng cấp và restore nhiều chủ |
| Route ngoài hợp đồng có tác dụng phụ | Từ chối yêu cầu ngoài hợp đồng và kiểm xác thực/Origin | CP-16/17: không tạo credential, gọi model hoặc ghi Core |
| Dọn rộng làm hỏng Core/CI/license | Rà consumer, chuyển test guard, giữ notices dùng chung và required checks | CP-17/18/19/23: build/compliance và hồi quy |
| Model nhỏ vẫn chậm hoặc model lớn hết bộ nhớ | Kiểm workload ngắn, context/output hữu hạn và một slot; đo toàn host | CP-13/22: cold/warm/idle/busy/cancel, không hứa giảm RAM từ số tham số |
| Kết nối inference thành SSRF hoặc lộ credential | Endpoint deployment cố định, chặn redirect, network riêng, không publish | CP-09/20: đích lạ không nhận request, model không nhận token Core |
| Artifact/model không đạt cam kết nguồn mở | Audit đúng bản và provenance trước manifest/phân phối | CP-01/19: hồ sơ code/model/dữ liệu; chưa đủ thì giữ gate mở |
| Tuyên bố hoàn tất đề/issue khi mới có một phần | Tách phạm vi trợ lý khỏi hỏi đáp/tác tử sự kiện và yêu cầu nền tảng | CP-24: tech lead review, issue vẫn mở |

Một lượt suy luận vẫn có thể đoán khi thiếu thông tin hoặc từ chối nhầm yêu cầu rõ nghĩa.
Đo riêng độ trễ và lỗi nội dung; không thêm vòng suy luận để che hạn chế chất lượng.
Không cho phép từ chối mọi hồ sơ để làm đẹp tỷ lệ an toàn; phải đo riêng các ca đủ thông tin.

## 3. Điều kiện dừng và rollback

Prototype local dùng cấu hình inference do người vận hành cung cấp; chưa phát hành model/artifact mặc định khi audit và chất lượng chưa đủ bằng chứng.
Chưa chuyển state thật nếu chưa kiểm snapshot/restore và khả năng dừng runtime cũ.
Không chấp nhận đọc chéo quyền, ghi trước xác nhận, nguồn giả hoặc lời báo đã lưu khi Core chưa xác nhận.
Nếu ngân sách không đạt, trình lại model/context/nơi chạy; không thay bằng cloud fallback hoặc mở quyền cho model.
Nếu migration lỗi, giữ dịch vụ trợ lý dừng, phục hồi bản/snapshot tương thích theo quyết định vận hành.
Rollback không tự gửi lại ghi, hoặc đảo ngược dữ liệu nghiệp vụ đã commit.

## 4. Những việc chưa được chốt hoặc chưa làm

- Model/runtime/artifact mặc định, context, chất lượng VI, thời gian chờ và ngân sách cần P0 cùng đo thật.
- Dữ liệu demo, nguồn khảo sát và bằng chứng nằm ngoài Git theo quyết định người dùng; giữ kiểm tra giấy phép của repository.
- Nguồn demo tiếng Việt đã hỗ trợ đối chiếu trường có tập lựa chọn; model vẫn có thể đoán loại hồ sơ khi lý do mơ hồ, nên gate chất lượng và review nguồn còn mở.
  Cần bộ đánh giá nội dung trước phát hành.
- Hỏi đáp sinh câu trả lời, embedding/reranker và tác tử tự sinh proposal theo sự kiện vẫn là phạm vi chưa thực hiện của #210/định hướng Dynamic Core.
- Xóa secret/thu hồi key, transcript, backup hoặc cache là thao tác riêng; không gộp vào dọn mã và không prune toàn máy.
- Nghiệm thu và cập nhật issue/PR cần ủy quyền; không tự đóng hoặc nối hai issue.
