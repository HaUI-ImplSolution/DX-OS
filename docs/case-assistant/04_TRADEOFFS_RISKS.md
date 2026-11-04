# Trade-off và rủi ro: Case Assistant

## 1. Trade-off

| # | Quyết định | Được | Mất |
|---|---|---|---|
| TO1 | Dùng Haystack và integration có sẵn | Không tự xây pipeline, BM25/RRF hoặc client Ollama mới | Thêm dependency, cần kiểm phiên bản, giấy phép và RAM |
| TO2 | Ngữ cảnh tạm theo người dùng, tối đa 200 ứng viên | Không tạo bản sao nghiệp vụ và cơ chế đồng bộ quyền mới | Embedding có thể lặp lại, phải thu hẹp trước khi tìm tập lớn |
| TO3 | Chọn thực thể trước khi hỏi | Ngữ cảnh nhỏ, rõ và phù hợp thử LLM vừa phải | Chưa hỏi tự do xuyên toàn tổ chức |
| TO4 | Bản đầu chỉ đọc và dẫn nguồn | Luồng ghi và workflow giữ quyền quyết định ở Core | Chưa tự thực hiện yêu cầu của người dùng |
| TO5 | AI và LLM bật tùy chọn | Lõi chạy độc lập; thiếu model không chặn triển khai | Người dùng phải cấu hình thêm khi muốn dùng AI |
| TO6 | Tách Qwen local và model phát hành | Tận dụng tài nguyên local mà giữ tiêu chuẩn mở | Phần sinh có thể chưa đủ điều kiện phát hành nếu chưa chọn được model |
| TO7 | Giữ API tri thức pgvector hiện có | Không đổi hợp đồng và ingest tài liệu đang chạy | Chưa tổng hợp kết hợp hồ sơ với quy định/tệp đính kèm |
| TO8 | Đặt trợ lý trong danh sách/chi tiết, giữ trang tìm kiếm bổ sung | Tự lấy ngữ cảnh và ID, giảm thao tác; dữ kiện và nguồn rõ hơn | Không tự khắc phục lỗi ngữ nghĩa của LLM |

## 2. Rủi ro

| # | Rủi ro | Giảm thiểu | Dự phòng |
|---|---|---|---|
| R1 | Lộ dữ liệu giữa người dùng hoặc đơn vị | Thu nội dung qua quyền Core trước khi tìm; store riêng từng request; đối chiếu quyền trước khi trả | Tắt trợ lý; giữ tra cứu chuẩn |
| R2 | LLM diễn giải sai dù trích nguồn thật | Tách dữ kiện Backend với phần tổng hợp; schema và kiểm nguồn; bộ câu hỏi có phủ định và timeline; người rà | Hiện bằng chứng, bỏ phần sinh không hợp lệ; ghi giới hạn nội dung |
| R3 | Nguồn đổi trong lúc model chờ | Snapshot đọc ngắn, đối chiếu toàn bộ ứng viên, tổng số và sự kiện mới; không giữ transaction dài | Trả stale và yêu cầu chạy lại |
| R4 | Tìm trên tập con nhưng báo như toàn kho | Chặn quá số ứng viên/payload; trả coverage và điều kiện thực thi | Yêu cầu người dùng thu hẹp |
| R5 | Model nhỏ không hiểu tiếng Việt hoặc quá chậm | Pilot model trên hardware thật; tối đa hai lượt model, giới hạn đồng thời và timeout | Tìm/bằng chứng không sinh; đổi model sau khi rà tiêu chuẩn |
| R6 | Model hoặc dependency không đạt tiêu chuẩn mở | Rà đúng phiên bản, trọng số, dữ liệu và quy trình huấn luyện; catalog và SBOM | Không đưa thành phần đó vào bản phát hành |
| R7 | RAM rò rỉ do store tạm hoặc tranh CPU với Core | Giới hạn ứng viên/byte, giải phóng sau lỗi/hủy, đo đồng thời; dùng một embedder | Hạ giới hạn hoặc tắt semantic/generation; không tự nâng cấu hình máy |
| R8 | Thư viện không tương thích Python/image hiện tại | Resolve và thử khối nhỏ trước; không đại nâng dependency không liên quan | Dừng WP để chọn phiên bản/công cụ phù hợp |
| R9 | Định hướng mở rộng làm thay kiến trúc ghi | Package adapter chỉ đọc; không SQL/tool ghi, BPMN hoặc quyền mới | Dừng và xin duyệt phạm vi riêng |
| R10 | Dùng lại image cũ rồi tưởng đã kiểm mã mới | Ghi nguồn image, build chọn lọc đúng thành phần; dữ liệu thử riêng | Không tích kết quả khi chưa xác định bản đã chạy |
| R11 | In-memory bị dùng như kho production toàn tổ chức | Nêu rõ giới hạn upstream và mục đích ngữ cảnh tạm; kiểm giới hạn cứng và tải | Lập kế hoạch chỉ mục bền vững riêng nếu nhu cầu vượt khả năng |

## 3. Điều kiện dừng để review lại

- Có ca gửi hoặc trả nội dung ngoài quyền, ghi dữ liệu nghiệp vụ từ AI hoặc thay hành vi workflow.
- Cần thay định dạng mô hình, quyền, schema nghiệp vụ, cơ chế apply/rollback hoặc đồng bộ nhiều backend để triển khai.
- Muốn bỏ giới hạn tập ứng viên mà chưa thiết kế chỉ mục, đồng bộ dữ liệu và thu hồi quyền.
- Không có model đạt tiêu chuẩn mở nhưng muốn đóng phần sinh như đã đủ điều kiện phát hành.
- Dependency buộc nâng lớn stack, hoặc phần cứng không giữ được luồng chính khi bật AI.

## 4. Rollback và việc để sau

Tắt feature và dừng đúng các service của overlay AI; không hạ hoặc xóa dữ liệu lõi.
Không có migration nghiệp vụ hoặc bản sao hồ sơ AI cần phục hồi.
Endpoint và UI trợ lý có thể bị tắt mà request CRUD/process hiện hành giữ nguyên.
Cache model chỉ xóa khi người vận hành yêu cầu; không xóa volume local dùng chung.

Các hướng để sau gồm chỉ mục bền vững, tìm nhiều thực thể, nối tài liệu/OCR, MCP, dự báo và tác tử ghi có người duyệt.
Đây là phần ngoài phạm vi hiện tại, không phải các nhiệm vụ đã được duyệt để triển khai.
