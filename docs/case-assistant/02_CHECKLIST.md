# Checklist: Case Assistant

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ kèm lý do.
Chỉ đánh dấu xong khi phần việc có bằng chứng; ca kiểm chưa chạy vẫn để trống.

## Chuẩn bị kế hoạch

- [x] Đọc hướng dẫn làm việc được người dùng cung cấp và tài liệu kiến trúc hiện hành.
- [x] Fetch và cập nhật `Khuong/main` bằng fast-forward; làm việc trên nhánh riêng của Case Assistant.
- [x] Đọc metadata, API dữ liệu/lịch sử, quyền, dịch vụ AI và Compose hiện có.
- [x] Kiểm và tái sử dụng image Ollama cùng cache `qwen2.5:1.5b` cho thử nghiệm local.
- [x] Bổ sung ý tưởng vào README local riêng và viết năm tệp kế hoạch.
- [x] Người dùng duyệt kế hoạch và cho phép bắt đầu triển khai.

## CA-WP-01: Hợp đồng, công cụ và model

- [x] CA-WP-01-SPEC-01 Chốt request/response, status, coverage, nguồn và điều kiện hỏi lại.
- [x] CA-WP-01-OSS-01 Chốt phiên bản Haystack/integration tương thích, dependency và giấy phép.
- [x] CA-WP-01-MODEL-01 Kiểm model local, tiếng Việt, RAM và thời gian trên phần cứng đích.
- [x] CA-WP-01-LICENSE-01 Chốt model phát hành đạt tiêu chuẩn hoặc ghi rõ phần sinh chưa đủ điều kiện.

## CA-WP-02: Adapter và API

- [x] CA-WP-02-BE-01 Controller/DTO/Swagger và client AI trong package trung tính.
- [x] CA-WP-02-BE-02 Kiểm bộ lọc đề xuất, dùng API đọc và tổng số chính xác.
- [x] CA-WP-02-GATE-01 Tắt AI: Backend khởi động và chạy luồng chính khi không có model/secret AI.

## CA-WP-03: Ngữ cảnh và quyền

- [x] CA-WP-03-AUTH-01 Không gửi bản ghi ngoài quyền cho retrieval hoặc LLM.
- [x] CA-WP-03-COVER-01 Tập ứng viên đủ bộ lọc; quá giới hạn yêu cầu thu hẹp, không kết luận trên trang đầu.
- [x] CA-WP-03-SNAPSHOT-01 Đọc lại quyền và đối chiếu model, bản ghi, lịch sử trước khi sinh/trả.
- [x] CA-WP-03-SNAPSHOT-02 Snapshot ứng viên/tổng số cùng transaction; phát hiện thay đổi ngoài top-k và sự kiện mới.
- [x] CA-WP-03-ISOLATION-01 Không cache/chia sẻ corpus, prompt hoặc câu trả lời giữa người dùng.

## CA-WP-04: Pipeline

- [x] CA-WP-04-AI-01 Dùng component Haystack cho BM25, cosine và RRF; một model embedding được nạp.
- [x] CA-WP-04-AI-02 Kho tạm riêng từng request, giải phóng khi xong/lỗi/hủy.
- [x] CA-WP-04-AI-03 Tổng hợp có schema, căn cứ và kiểm trường hợp thiếu/không hợp lệ.
- [x] CA-WP-04-LIMIT-01 Giới hạn payload, ứng viên, lịch sử, timeout, đồng thời và số lượt model.
- [x] CA-WP-04-GATE-01 AI không thể ghi DB nghiệp vụ, gọi tool ngoài danh sách hoặc thay quyền.

## CA-WP-05: Giao diện

- [x] CA-WP-05-FE-01 Ba thao tác dùng metadata chung và liên kết hiện có.
- [x] CA-WP-05-FE-02 Hiện nguồn, coverage, tổng số, phần sinh và lỗi rõ ràng.
- [x] CA-WP-05-FE-03 Loading/hủy, màn hình nhỏ và nội dung không an toàn được xử lý.
- [x] CA-WP-05-UX-01 Tìm nội dung ngay trong danh sách, giữ bộ lọc và không cần nhập ID.
- [x] CA-WP-05-UX-02 Hai thao tác từ chi tiết/kết quả tự lấy hồ sơ đích; liên kết sâu được kiểm quyền.
- [x] CA-WP-05-UX-03 Dữ kiện Backend đặt trước phần AI, nguồn thu gọn và mở được từ trích dẫn.
- [x] CA-WP-05-UX-04 Đổi ngữ cảnh/hủy không nhận kết quả cũ; kiểm trình duyệt, bàn phím, 360px/chữ lớn và AI tắt/lỗi.
- [x] CA-WP-05-UX-05 Đổi liên kết sâu và Back/Forward đặt lại đúng loại hồ sơ, bản ghi và thao tác, không tự gọi sinh hoặc nhận kết quả cũ.

## CA-WP-06: Triển khai và tài liệu

- [x] CA-WP-06-DEPLOY-01 Overlay AI tùy chọn, lệnh bật lõi giữ nguyên; không có dependency khởi động bắt buộc.
- [x] CA-WP-06-DEPLOY-02 Biến mặc định ở env mẫu; secret/model kiểm khi bật; không publish cổng AI/Ollama.
- [x] CA-WP-06-CACHE-01 Chuẩn bị model khi thiếu; restart/offline dùng cache và không tải lại.
- [x] CA-WP-06-DOC-01 Hướng dẫn module, vận hành, kiến trúc và README hai ngôn ngữ đúng tính năng đã làm.
- [x] CA-WP-06-LICENSE-01 Lock, SBOM, COPYRIGHT/NOTICE và license catalog khớp dependency/model thật.

## CA-WP-07: Kiểm chứng

- [x] CA-WP-07-UNIT-01 Test Backend và AI liên quan đạt; kiểm ranh giới package đạt.
- [x] CA-WP-07-ACL-01 Quyền own/unit/assigned, nhiều vai, owner_via và token mới đạt.
- [x] CA-WP-07-RACE-01 Sửa/xóa/ghi chú mới/model đổi trong lúc chờ được phát hiện.
- [~] CA-WP-07-QUALITY-01 Nhãn được rà, có kết quả tìm kiếm và tổng hợp cho ít nhất ba mô hình.
- [x] CA-WP-07-ACCEPT-01 Tiêu chí chấp nhận được duyệt trước phép nghiệm thu; không hạ theo kết quả đã thấy.
- [x] CA-WP-07-PERF-01 Có số đo cold/warm, đồng thời, p50/p95, CPU/RAM và giới hạn mẫu.
- [x] CA-WP-07-REGRESSION-01 Chuỗi kiểm lõi chạy với AI tắt/bật, dữ liệu và luồng ghi không đổi.
- [x] CA-WP-07-CLEAN-01 Bản sao sạch khởi động theo hướng dẫn; dọn đúng container, volume và dữ liệu thử.
- [ ] CA-WP-07-USER-01 Người dùng kiểm local và xác nhận ba thao tác.

## Nghiệm thu

- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` có bằng chứng; hạn chế chưa đạt được ghi đúng phạm vi.
- [x] Chỉ cập nhật tài liệu trạng thái sau khi tính năng tương ứng đã làm và kiểm.
- [x] Rà tệp, test, dependency và trọng số thừa; không thêm secret, dữ liệu thật, `report/` hoặc thay `.gitignore`.
- [x] Rà diff với nhánh cha mới nhất, đường khởi động mặc định, giấy phép và rollback.
- [ ] Bằng chứng nghiệm thu được tạo trên đúng nhánh, mô hình động, dependency và model đã chọn cho kế hoạch này.
- [ ] Chỉ commit/push sau khi người dùng yêu cầu rõ ràng.

## Bằng chứng hiện có

- Nhánh đã ghép `origin/Khuong/main`, gồm giao diện mới, gỡ phần thừa và bộ cài một lệnh; giữ cách khởi động lõi.
- Backend `verify` đạt trên bản ghép: 20 test core, 27 test engine và 3 test monitor; trong đó 19 test của trợ lý, gồm 8 test PostgreSQL.
- Embedding và Haystack đã chạy trong image Python 3.11 khi tắt mạng; qrels/run của 24 truy vấn được lưu ngoài repository.
- Qwen local đã chạy 12 ca tổng hợp và bốn ca đề xuất bộ lọc; schema/ID nguồn hợp lệ không được coi là nội dung đã nghiệm thu.
- Còn nhận định suy diễn từ tiêu đề trong pilot; phần sinh giữ thử nghiệm, model mặc định để trống và giới hạn nằm trong BACKLOG.
- Bản ghép đạt 44 test AI; 324 kiểm lõi và 10 kiểm trợ lý, cùng các ca lỗi AI/Ollama và browser 360px/chữ 200%.
- Giao diện theo ngữ cảnh đạt kiểm kiểu/build và browser: bộ lọc/quyền thật, chọn hồ sơ, mở nguồn, đổi ngữ cảnh, hủy, AI tắt và chặn kết quả quá hạn.
- Liên kết sâu đạt 7 kiểm browser; 11 unit Backend liên quan đạt, cấu hình Spring giữ đúng mặc định và giá trị được cấp.
- Qwen thật qua API có nguồn; render phản hồi ghi lại và các lỗi/delay được kiểm riêng bằng giả lập, không dùng để xác nhận độ đúng nội dung.
- Chưa commit/push; `report/` và cấu hình, artifact local không nằm trong phần bàn giao Git.

## Trạng thái bàn giao local

Đã khôi phục stack dự án với dữ liệu hiện có; stack/database thử được dọn riêng.
Đã kiểm khởi động lõi khi không có AI/Ollama, bật trợ lý tùy chọn và tóm tắt có nguồn mà hồ sơ gốc không đổi.
Phần triển khai kỹ thuật sẵn sàng để kiểm local; chưa nghiệm thu nghiệm thu.
Các mốc còn chờ gồm người dùng kiểm ba thao tác, rà nhãn/nội dung, đánh giá trường dài và chọn model/runtime đủ điều kiện phát hành.
Không tự đánh dấu các mốc chất lượng đạt hoặc hạ mục tiêu theo kết quả đã thấy.

Rà cuối: license và route không có lỗi; gitleaks không phát hiện secret trong cây mã bàn giao.
Không còn container, volume hoặc dữ liệu PostgreSQL của stack thử; cache model và dữ liệu dự án được giữ.
Bản local đã kiểm tóm tắt có nguồn, lọc/đếm xác định, nguồn không đổi sau suy luận; phần sinh thiếu căn cứ và trường dài vẫn thuộc mốc chất lượng chưa đóng.
