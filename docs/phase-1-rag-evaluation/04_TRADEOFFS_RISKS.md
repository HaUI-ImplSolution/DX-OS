# Trade-off và rủi ro: Phase 1

## 1. Trade-off

| # | Quyết định | Phương án | Chọn đề xuất | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Giao diện đánh giá | HTML tự viết / Langfuse Cloud / self-host | Langfuse self-host ngoài repository | Giao diện có sẵn, dữ liệu trên máy local | Cần tài nguyên và tự quản lý dữ liệu đánh giá | Vừa; giữ bản xuất độc lập |
| TO2 | Phép đo | Hai metric truy xuất / đánh giá toàn pipeline | Recall và nDCG | Nhỏ, rõ công thức và tái lập được | Không kết luận được chất lượng CV, liều hoặc câu trả lời cuối | Thấp |
| TO3 | Nhãn đúng | AI tự chấm / người kiểm tra độc lập | Người kiểm tra, AI chỉ soạn nháp | Giảm thiên lệch do tự tạo đề và đáp án | Cần thời gian của người hiểu nghiệp vụ | Vừa |
| TO4 | Tích hợp | Công cụ riêng / tracing runtime | Công cụ riêng | Không đổi hành vi và cách deploy sản phẩm | Chưa quan sát mọi request người dùng | Thấp |
| TO5 | Mốc đo | Đầu ra retriever / sao chép xử lý Backend | Đầu ra retriever | Đo đúng một thành phần, không lặp logic nghiệp vụ | Không kết luận chất lượng câu trả lời sau lọc/gộp | Thấp |

Chủ dự án chọn localhost; quy mô bộ đề, người kiểm tra và tài nguyên máy còn chờ chốt ở mục 9 của `00_OVERVIEW.md`.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Nhãn do AI tạo bị coi là đáp án độc lập | Cao | Cao | Tách draft/reviewed, người kiểm tra chưa xem thứ hạng | Không có xác nhận hoặc nhãn chỉ theo tên bệnh | Chỉ công bố là nội bộ, chưa đóng phase |
| R2 | Đề quá dễ hoặc thiếu chủ đề | Vừa | Cao | Có cách diễn đạt tự nhiên, triệu chứng và chủ đề Lab; công bố phạm vi | Hầu hết câu lặp nguyên tiêu đề | Rà lại trước đóng băng, không sửa nhãn để chạy theo điểm |
| R3 | DB khớp nội dung nhưng vector sinh từ model khác | Vừa | Cao | Khóa nhãn đọc được, so corpus và bằng chứng ingest đúng model/artifact | Thiếu lịch sử ingest, chunk sai hoặc thiếu | Dừng công bố; dùng DB đánh giá riêng khi cần, không sửa schema runtime |
| R4 | Chấm sai thứ tự vì dùng cosine trước boost | Vừa | Cao | Run ghi thứ tự cuối, kiểm tra T1-06 | Thứ hạng dashboard khác retriever | Sửa adapter đánh giá, giữ runtime |
| R5 | Chỉ số bị hiểu thành độ chính xác toàn hệ thống | Cao | Cao | Ghi rõ đo chunk truy xuất, nhóm/k và giới hạn | Dùng nDCG như xác suất chẩn đoán đúng | Sửa cách trình bày, giữ dữ liệu gốc |
| R6 | Lộ dữ liệu trong trace hoặc khi chia sẻ UI | Vừa | Cao | Chỉ bộ đề và nguồn công khai, UI loopback, kiểm payload, không credential/ảnh | Trace có thông tin nghiệp vụ | Dừng publish/chia sẻ và xử lý theo quyền project |
| R7 | SDK kéo dependency làm đổi runtime | Vừa | Cao | Môi trường đánh giá riêng, kiểm phiên bản, API chính thức khi phù hợp | Lock/runtime requirements có diff | Đổi cách publish trong công cụ riêng; không nâng runtime |
| R8 | Localhost thiếu RAM/disk hoặc trùng cổng | Cao | Cao | Kiểm tra tài nguyên, project/cổng/volume riêng; không bật cả DX-OS để demo | Swap cao, container restart hoặc cổng bị dùng | Hoãn bật Langfuse hoặc dùng máy đánh giá khác theo quyết định chủ dự án |
| R9 | Langfuse dừng hoặc credential sai | Vừa | Vừa | Tách collect/score/publish, lưu artifact trước | Không gửi được experiment | Giữ kết quả tái lập và gửi lại cùng lần chạy |
| R10 | Hứa có trace chi tiết nhưng chỉ thu được top-k cuối | Vừa | Cao | Một observation cho lời gọi thật, dữ liệu chunking là metadata | Có span/ứng viên không do collector thu | Giảm dữ liệu hiển thị đúng khả năng hiện có, không sửa runtime để lấy trace |
| R11 | Recall thấp do nhiều chunk liên quan hơn k hoặc overlap | Vừa | Vừa | Ghi rõ đơn vị chunk và số nhãn liên quan; giải thích trần Recall@k | Điểm bị hiểu là độ đủ ý của câu trả lời | Giữ nhãn đúng, không xóa nhãn để tăng điểm; không thêm metric ngoài phạm vi |

## 3. Nợ kỹ thuật chấp nhận

Chưa chấm câu ngoài chủ đề, độ đúng câu trả lời, khả năng suy luận, CV hoặc phác đồ.
Chưa tích hợp monitoring online, cảnh báo chất lượng hoặc LLM judge; self-host chỉ phục vụ thí nghiệm đánh giá.
Đây là baseline trên corpus nhỏ; không suy ra chất lượng trên mọi câu hỏi thực địa.
Pilot gồm 36 câu hỏi đáp và sáu query chẩn đoán hiện có; chưa mở rộng thành benchmark lớn hoặc hệ thống đồng bộ dashboard.

## 4. Điều kiện dừng và rollback

Dừng triển khai phần liên quan nếu cần sửa runtime, API, schema, deploy hoặc CI ngoài danh sách file cho phép.
Dừng công bố điểm chính thức nếu nhãn chưa được kiểm tra, dữ liệu thiếu, snapshot sai hoặc người khác chưa tái lập được.
Lỗi ngoài domain chỉ ghi nhận; không mở rộng công việc để sửa ké.
Rollback bằng dừng công cụ và riêng project Langfuse, giữ volume làm bằng chứng, không đổi database sản phẩm.
Xóa dữ liệu Langfuse cần yêu cầu riêng; không dùng prune toàn máy hoặc xóa volume sản phẩm.

## 5. Tác động ngoài phase

Người dùng Farm/Lab giữ nguyên cách sử dụng và khởi động.
Kết quả làm mốc cho công việc tối ưu RAG sau này; không tự mở rộng phase để cải thiện điểm.
Khi trình bày cho bên thứ ba, ghi rõ người chấm nhãn và phạm vi phép đo; Langfuse không thay vai trò người kiểm định độc lập.
