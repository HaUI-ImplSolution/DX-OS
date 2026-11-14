# Checklist triển khai và nghiệm thu

Đối chiếu [overview](./00_OVERVIEW.md), [plan](./01_PLAN.md) và [rủi ro](./03_TRADEOFFS_RISKS.md).
Chỉ đánh dấu khi có bằng chứng; các lựa chọn đã thống nhất không đồng nghĩa chức năng đã đạt.
Log, fixture, ảnh và kết quả kiểm chi tiết giữ ngoài Git mặc định; PR khi được phép gửi sẽ dẫn bằng chứng đã loại bí mật.

## 1. Quyết định và review

- [x] Người dùng chọn API cá nhân Google Gemini, OpenAI và Anthropic thay mục tiêu model đạt OSAID.
- [x] Người dùng chọn giao diện chat theo cách bố trí Gemini và truy xuất nhẹ trước.
- [x] Người dùng cho phép tạo tri thức giả lập lúc triển khai.
- [x] Người dùng yêu cầu bộ tài liệu độc lập trong `docs/`, ngang hàng với `dynamic-core/`.
- [ ] Tech lead review phạm vi mới và sự khác biệt với yêu cầu OSAID đang ghi trên issue/tài liệu định hướng.
- [ ] Tech lead chốt cấu hình và danh tính nền của tác tử theo sự kiện, trường được đề xuất và người duyệt.
- [ ] Tech lead review kiến trúc dịch vụ AI riêng, lưu trữ, route và hợp đồng xác nhận.
- [ ] Đồng bộ mục tiêu issue và các tài liệu liên quan khi có ủy quyền đúng phạm vi.

## 2. Nền tảng và giấy phép

- [ ] Kiểm lại nhánh cha, thay đổi local và phần đã tích hợp trước khi sửa.
- [ ] Kiểm LICENSE/NOTICE đúng phiên bản của dependency trực tiếp, bắc cầu, build/test và image; báo trước khi thêm manifest.
- [ ] Không thêm model local, embedding, reranker, kho vector hoặc framework agent vào bản đầu.
- [ ] Có schema dữ liệu AI riêng, migration và tài khoản DB không chạm bảng nghiệp vụ.
- [ ] OpenAPI được sinh từ hợp đồng triển khai; giữ envelope, HTTP status, mã lỗi và bản dịch.
- [ ] Hồ sơ dependency, NOTICE, catalog và SBOM phản ánh đúng phần thực sự thêm.

## 3. API cá nhân và cách ly

- [ ] Tạo/chọn/thay/xóa cấu hình của chủ; lưu cấu hình không tự gọi API.
- [ ] Endpoint cố định cho ba provider; không tự đổi nhà cung cấp khi lỗi.
- [ ] JWT kiểm issuer, audience, chữ ký và hạn; chủ luôn lấy từ danh tính đã kiểm.
- [ ] API key mã hóa, không trả lại qua API, URL, log, lỗi hoặc hội thoại.
- [ ] Hai người dùng đồng thời không truy cập chéo cấu hình, tin nhắn, nguồn hoặc đề xuất.
- [ ] Hội thoại gắn một mô hình; quyền được kiểm lại khi đọc và tiếp tục hội thoại.
- [ ] Mất quyền nguồn cũ không khiến dữ liệu được gửi lại tới provider qua lịch sử.
- [ ] Credential và dữ liệu AI được backup/restore đúng quyền; khóa gốc ngoài DB và Git.

## 4. Hội thoại và công cụ đọc

- [ ] Tạo, liệt kê, đổi tên, xóa hội thoại và đọc lịch sử theo đúng chủ/mô hình.
- [ ] Gửi có mã request chống trùng; gián đoạn không tự gửi lại prompt hoặc công cụ.
- [ ] Chuẩn hóa stream, lời gọi công cụ, kết quả, lỗi và usage của ba provider.
- [ ] Metadata, hộp việc, bộ lọc, chi tiết, lịch sử, thống kê và người nhận dùng API Core đúng quyền.
- [ ] Tên trường/toán tử/hành động lấy từ metadata; không SQL, HTTP tùy ý hoặc shell.
- [ ] Trang kết quả bị cắt không được mô tả là đầy đủ; số tổng dùng endpoint thống kê.
- [ ] Có giới hạn payload, token, vòng công cụ, đồng thời và timeout.

## 5. Tri thức có nguồn

- [ ] Có corpus hư cấu ghi rõ nguồn, phiên bản, giấy phép và phạm vi mô hình.
- [ ] Không đưa quy chế thật, dữ liệu cá nhân hoặc tài liệu chưa rõ quyền sử dụng vào corpus.
- [ ] BM25 chuẩn hóa tiếng Việt; cache và corpus tách đúng phạm vi truy cập.
- [ ] Không lập chỉ mục tập trung hồ sơ hoặc nội dung tệp nghiệp vụ.
- [ ] Câu trả lời chỉ dẫn nguồn đã cấp; nguồn không tồn tại bị từ chối.
- [ ] Thiếu bằng chứng thì từ chối hoặc hỏi lại, không coi điểm tìm kiếm là độ tin cậy.
- [ ] Bộ câu hỏi đạt mục tiêu truy xuất đã được tech lead review; ghi riêng độ đúng câu trả lời và kết quả sai phạm vi.

## 6. Đề xuất và xác nhận

- [ ] Snapshot do server dựng, có chủ, đích, tham số, nguồn, version/modelVersion và hạn.
- [ ] Thẻ hiển thị đúng snapshot; đổi tham số bắt buộc tạo đề xuất mới.
- [ ] LLM chỉ đọc/tạo đề xuất, không được gọi xác nhận hoặc thực thi.
- [ ] Core kiểm phiên bản, quyền, trạng thái và điều kiện trong cùng giao dịch.
- [ ] Client cũ không gửi điều kiện vẫn giữ hành vi; client AI bắt buộc có điều kiện.
- [ ] Đề xuất giao/trả việc, chuyển trạng thái và sửa trường được phép không vượt metadata.
- [ ] Sai chủ, quá hạn, thay phiên bản, đổi vai hoặc người nhận không hợp lệ bị từ chối.
- [ ] Xác nhận kép chỉ claim một lần; crash/timeout sau ghi chuyển `uncertain`, không tự gửi lại.
- [ ] Thành công và tác động bất đồng bộ được mô tả theo bằng chứng API, có lịch sử đối chiếu.

## 7. Tác tử theo sự kiện

- [ ] Bật/tắt cấu hình theo chính chủ; dùng API và quota của người đã bật.
- [ ] Nhận webhook có chữ ký, lưu bền vững và khử trùng trước khi gọi model.
- [ ] Không dùng feed ADMIN hoặc tài khoản ADMIN để đọc dữ liệu thay người dùng.
- [ ] Có phiên nền riêng được cấp/thu hồi đúng cách; chưa bật thì không giữ credential nền.
- [ ] Mất phiên hoặc mất quyền dừng xử lý và hiển thị tình trạng đúng.
- [ ] Chỉ tạo đề xuất trường được cấu hình, không tự ghi và không tạo vòng lặp sự kiện.
- [ ] Tác vụ trùng, worker restart và lỗi provider không gây gọi vô hạn hoặc mất trạng thái âm thầm.
- [ ] Cách nhận sự kiện tương thích với cấu hình webhook của bản cài được review.

## 8. Giao diện và vận hành

- [ ] Trang chat dùng Material UI hiện có, nạp chậm; không thêm bộ giao diện khác.
- [ ] Lịch sử bên trái, vùng chat, ô nhập, chọn API/model và cài đặt riêng dùng được.
- [ ] Nguồn, liên kết hồ sơ và thẻ xác nhận hiển thị trực tiếp trong hội thoại.
- [ ] Không có HTML tùy ý từ model; liên kết và nội dung hiển thị được kiểm.
- [ ] Desktop/mobile, bàn phím, bộ gõ tiếng Việt, sáng/tối, loading/empty/error và reduced motion đạt.
- [ ] Không có key hoặc provider lỗi vẫn dùng được Core và giao diện nghiệp vụ.
- [ ] Có cách bật/tắt, khởi động lại, thu hồi key/phiên nền và backup/restore.
- [ ] Có số đo tài nguyên và độ trễ ở trạng thái tắt, nhàn rỗi và hai người chat đồng thời.

## 9. Kiểm chứng và bàn giao

- [ ] Đọc diff; backend compile/test-compile và định dạng theo đúng nhánh khi có sửa tương ứng.
- [ ] Frontend typecheck và dịch vụ AI typecheck/build đạt.
- [ ] Ca chức năng chạy trên môi trường được duyệt, phân biệt mô phỏng và API thật.
- [ ] Cả Google, OpenAI và Anthropic có bằng chứng API thật cho model công bố hỗ trợ.
- [ ] Ba họ quy trình và hai chủ đồng thời đạt các ca quyền, snapshot và xác nhận.
- [ ] Kiểm giấy phép và CI bắt buộc đạt trên phiên bản chuẩn bị nộp.
- [ ] Tài liệu sử dụng/cấu hình phản ánh đúng triển khai; không xóa backlog khi chưa hoàn tất cả phạm vi.
- [ ] Bàn giao nêu nhánh/base, thay đổi, kết quả, giới hạn, việc còn lại và trạng thái Git/GitHub đã xác minh.
- [ ] Tech lead/người dùng nghiệm thu; không tự đóng issue hoặc xuất bản thay đổi khi chưa có ủy quyền.
