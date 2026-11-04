# Case Assistant: Trợ lý tìm và hiểu hồ sơ

| Mục | Giá trị |
|---|---|
| Trạng thái | Đang kiểm thử và nghiệm thu |
| Mức độ | Cao |
| Ứng dụng và module chính | `ai/`, adapter trong `dx-engine`, giao diện chung `frontend/`, cấu hình AI tùy chọn |
| Phụ thuộc | `Khuong/main` mới nhất, sau Phase 10 và phần hoàn thiện lõi |

## 1. Tham chiếu

| Nguồn | Nội dung áp dụng |
|---|---|
| [`ARCHITECTURE.md`](../../ARCHITECTURE.md) | Lõi headless; mô hình động; API chung kiểm quyền; AI chạy tiến trình riêng |
| [`docs/dynamic-core/README.md`](../dynamic-core/README.md) | Miền bản ghi có vòng đời; mở rộng qua provider; tiêu chuẩn mở cho mã, model và dữ liệu |
| [`ai/README.md`](../../ai/README.md) | API AI, pipeline hồ sơ, giới hạn và cấu hình Compose tùy chọn |
| [`backend/README.md`](../../backend/README.md), [`frontend/README.md`](../../frontend/README.md) | Phân lớp, hợp đồng API và giao diện dựng từ metadata |
| [`BACKLOG.md`](../dynamic-core/BACKLOG.md) | Giới hạn nhiều backend, thay đổi đơn vị và dữ liệu huấn luyện embedding |

Kế hoạch dùng cấu trúc năm tệp theo mẫu hiện hành.
Thứ tự thực hiện là đặc tả, Swagger, mã nguồn, kiểm chứng.
Mỗi câu tài liệu nằm trên một dòng nguồn; chỉ đánh dấu kết quả có bằng chứng.

### Nguồn mở tham chiếu

Nguồn tham chiếu chính cho trải nghiệm là **Nextcloud Assistant**, trợ lý dùng chung của nền tảng Nextcloud.
Trợ lý có thể được gọi ngay trong ứng dụng khác với tác vụ và nội dung được chọn sẵn, như nút xử lý một đoạn văn trong Nextcloud Text.
Case Assistant áp dụng cách tổ chức đó vào danh sách và chi tiết hồ sơ: màn hình cung cấp ngữ cảnh, người dùng chọn việc cần làm mà không phải nhập ID.

| Dự án và nguồn gốc | Cách tổ chức được tham chiếu | Áp dụng trong Case Assistant | Mức sử dụng |
|---|---|---|---|
| [Nextcloud Assistant](https://github.com/nextcloud/assistant#readme) | Một trợ lý chung, có điểm gọi ngay trong các ứng dụng với tác vụ và đầu vào được chọn sẵn | Ô tìm trong danh sách; nút tóm tắt/tìm tương tự từ hồ sơ đang mở | Tham chiếu thiết kế và trải nghiệm |
| [Nextcloud Context Chat](https://docs.nextcloud.com/server/stable/admin_manual/ai/app_context_chat.html) | App tích hợp với nền tảng và backend Python riêng, dùng provider sinh nội dung có thể chạy nội bộ | Adapter Backend giữ ngữ cảnh và quyền; dịch vụ AI riêng xử lý truy xuất/tổng hợp | Tham chiếu cách phân tách thành phần |
| [Haystack của deepset](https://docs.haystack.deepset.ai/docs/documentjoiner) | Pipeline ghép bộ tìm từ khóa, embedding và `DocumentJoiner` hỗ trợ RRF | Dùng trực tiếp component Haystack cho BM25, cosine/RRF và integration Ollama | Dependency triển khai trực tiếp |

Ngữ cảnh hồ sơ của DX-OS được lấy qua quyền Core và chỉ giữ tạm theo request.
Các dự án tham chiếu không bổ sung dependency Nextcloud hoặc thay đổi định dạng mô hình, workflow và cơ chế quyền của DX-OS.

## 2. Lý do và trạng thái nền

Nền thiết kế duy nhất là lõi dynamic trên `Khuong/main` hiện tại.
Năng lực AI được xây cho nền tảng mô hình động, dùng chung qua metadata, API và quyền của Core.
Các mô hình minh họa chỉ là dữ liệu cấu hình và ca kiểm chứng; không quyết định kiến trúc hoặc chứa logic riêng trong lõi.

Người xử lý cần tìm hồ sơ theo nội dung, hiểu diễn biến và tham khảo những trường hợp đã xử lý.
Lọc và đếm là trách nhiệm của code; AI hỗ trợ hiểu cách diễn đạt và tổng hợp từ thông tin được phép đọc.
Giá trị phải được kiểm chứng bằng tác vụ thực tế và so sánh với tìm từ khóa, không chỉ bằng việc sinh được câu trả lời.

Lõi hiện có `MetaController`, `DataService.list/get/events`, quyền theo dòng và giao diện dùng chung.
`ai/` dùng embedding MiniLM; API kho tài liệu pgvector và API trợ lý hồ sơ được tách riêng.
Trợ lý có pipeline Haystack, client Ollama và overlay Compose tùy chọn.
Cache `qwen2.5:1.5b` hiện có đã được dùng cho phép thử local trên CPU; đây chưa phải model mặc định phát hành.
Kết quả kiểm của nhánh được ghi trong checklist và kế hoạch kiểm thử; người dùng chưa nghiệm thu giao diện mới.

## 3. Domain và phạm vi

**Domain:** hỗ trợ đọc thông tin của bản ghi có vòng đời, dựa trên mô hình đã áp dụng và quyền của người hỏi.

Trong phạm vi:

- Tìm bản ghi bằng điều kiện xác định hoặc nội dung gần nghĩa trong một thực thể đã chọn.
- Tóm tắt một hồ sơ và lịch sử của nó, gắn từng ý với căn cứ.
- Tìm hồ sơ tương tự trong cùng thực thể, hiển thị cách xử lý đã được ghi nhận khi lịch sử có nội dung đó.
- Tái sử dụng phần mềm nguồn mở cho pipeline, xếp hạng và gọi model; viết adapter DX-OS cần thiết.
- Giao diện chung ở Tầng 2, API headless ở Tầng 1 và cấu hình AI tùy chọn.
- Tìm theo nội dung ngay trong danh sách; tóm tắt và tìm tương tự từ hồ sơ đã chọn, không yêu cầu nhập ID.
- Đánh giá trên dữ liệu tổng hợp do nhóm tạo, có quyền tái sử dụng rõ ràng, thuộc ít nhất ba mô hình có sẵn.

Ngoài phạm vi:

- Tạo/sửa/xóa hồ sơ, duyệt, giao việc, thay đổi trạng thái hoặc tự chạy hành động nghiệp vụ bằng AI.
- Sinh YAML, thay định dạng mô hình, workflow, SSO, quyền hiện hành hoặc cơ chế plan/apply/rollback.
- Tìm toàn tổ chức không chọn thực thể, join tùy ý, SQL do LLM sinh hoặc truy cập database nghiệp vụ trực tiếp từ AI.
- OCR, đọc byte tệp đính kèm, cào tài liệu, nhập dữ liệu bên ngoài và nối kho tài liệu vào trợ lý hồ sơ.
- Lập chỉ mục nghiệp vụ bền vững, đồng bộ outbox sang vector, cache câu trả lời dùng chung, lịch sử hội thoại nhiều lượt.
- Reranker mới, dự báo, process mining, MCP, nhiều agent và tự hành động.
- VPS, tag/release, đổi CI hoặc dựng nền tảng đánh giá riêng.

## 4. Kết quả đầu ra mong đợi

- Trợ lý lấy ngữ cảnh danh sách/chi tiết đang mở hoặc loại hồ sơ đã chọn; không viết Java hoặc prompt riêng cho từng ngành.
- Ba thao tác `find`, `summarize`, `similar` có hợp đồng thống nhất, bằng chứng mở được và trạng thái lỗi rõ ràng.
- AI chỉ nhận metadata và nội dung đã qua quyền; kết quả được kiểm lại trước khi trả cho người dùng.
- Bộ lọc, số lượng và thời gian dùng giá trị Backend tính; nội dung sinh không được thay các giá trị đó.
- Kết quả ưu tiên thông tin hồ sơ và trạng thái Backend, tách phần tổng hợp AI và thu gọn nguồn đối chiếu.
- Dữ liệu hồ sơ chỉ được giữ tạm theo yêu cầu; không lưu prompt, câu trả lời hoặc bản sao nghiệp vụ vào database AI.
- Tắt AI, lỗi AI hoặc thiếu LLM không làm hỏng CRUD, đăng nhập, hộp việc và workflow.
- Khởi động lõi giữ nguyên; AI được bật riêng bằng Compose overlay và tài liệu một lệnh tương ứng.
- Có số đo mới trên bộ đề đã rà nhãn, kèm các ca không đạt và giới hạn; không đặt yêu cầu phải vượt mọi baseline.
- Sinh nội dung chỉ đưa vào bản phát hành khi model đạt tiêu chuẩn mở của dự án và qua kiểm chất lượng.
- Người dùng kiểm local trước; chưa commit hoặc push khi chưa có lệnh riêng.

## 5. Quyết định thiết kế đề xuất

| Nội dung | Đề xuất |
|---|---|
| Phần mềm dùng lại | Haystack và integration Ollama; Pydantic hiện có; API, quyền và embedding hiện có |
| Ngữ cảnh | Một thực thể từ danh sách/chi tiết đang mở hoặc loại hồ sơ đã chọn; hồ sơ đích được giao diện truyền tự động |
| Tìm gần nghĩa | Tập ứng viên đầy đủ theo quyền và bộ lọc, tối đa 200 bản ghi; vượt thì yêu cầu thu hẹp |
| Kho tạm | Haystack in-memory theo từng request, giải phóng sau khi dùng; chưa phải kho tìm kiếm production toàn tổ chức |
| Model local | Thử Qwen có sẵn nếu bật rõ ràng; không mặc định tải thêm hoặc chọn nó cho bản phát hành |
| Model phát hành | Chọn sau cổng kiểm giấy phép, dữ liệu huấn luyện, chất lượng tiếng Việt và tài nguyên |
| Nguồn sự thật | API chung và lịch sử Core; trường hợp cũ chỉ là tham khảo |

Phần triển khai được giữ trong diff để review; chỉ nghiệm thu khi các mốc nghiệm thu của checklist được đáp ứng.
