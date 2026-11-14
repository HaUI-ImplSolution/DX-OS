# Kế hoạch triển khai

Phạm vi và quy tắc nền tảng ở [overview](./00_OVERVIEW.md).
Đây là kế hoạch đề xuất để review, không phải danh sách thay đổi đã triển khai.

## 1. Cơ sở thiết kế

Nhánh làm việc là `feature/issue-210-open-source-ai`, đích tích hợp dự kiến `Khuong/main`.
Khi bắt đầu từng phần, kiểm lại nhánh cha, thay đổi local và các thay đổi đã tích hợp để tránh làm trùng.
Không lấy checkout khác làm nguồn trạng thái cho nhánh này.

| Thành phần hiện có | Cách sử dụng và phần cần bổ sung |
|---|---|
| `frontend/src/admin/AdminApp.tsx`, `frontend/src/shell/` | Thêm trang chat nạp chậm, giữ đăng nhập, chủ đề và cơ chế dịch |
| `backend/dx-engine/.../meta/MetaController.java` | Đọc mô hình, trường và quyền; cần bổ sung phiên bản mô hình cho xác nhận |
| `backend/dx-engine/.../data/DataController.java` và `DataService.java` | Dùng truy vấn, thống kê, lịch sử và người nhận hiện có; bổ sung điều kiện phiên bản cho giao việc và hành động |
| `backend/dx-engine/.../event/EventFeedController.java` | API feed hiện chỉ dành ADMIN; không dùng nó làm nguồn của trợ lý cá nhân |
| `deploy/INTEGRATIONS.md` | Dùng cơ chế webhook đã ký và khử trùng sự kiện; không thêm một trục sự kiện mới |
| `deploy/`, `sso/`, `sbom/` | Ghép cấu hình vận hành, danh tính nền nếu cần và hồ sơ giấy phép theo cơ chế hiện có |

## 2. Cổng review trước triển khai

Tech lead review mục tiêu API cá nhân thay cho pipeline OSAID và việc giữ mã tích hợp nguồn mở.
Chốt dịch vụ AI riêng, schema riêng, route giao diện và hợp đồng xác nhận; không thêm module Java thứ năm.
Route dự kiến là `/ai` cho giao diện và `/ai-api/v1` cho dịch vụ; kiểm xung đột route trước khi triển khai.
Chốt phần tác tử theo sự kiện: cấu hình theo mô hình, trường được đề xuất và danh tính người duyệt.
Không tự bỏ phần tác tử hoặc coi nó đã hoàn thành nếu cổng này chưa có quyết định.
Chỉ sau review mới đồng bộ mục tiêu trên issue và tài liệu định hướng trong phạm vi được cho phép.
Quyền sửa local không tự cấp quyền chỉnh issue, gửi bình luận hoặc thực hiện thao tác Git xuất bản.

## 3. Danh tính, cấu hình và dữ liệu

### API cá nhân

Chỉ nhận Google Gemini, OpenAI và Anthropic, với endpoint chính thức cố định theo từng adapter.
Người dùng lưu nhãn cấu hình, provider, model ID và API key; có thể chọn cấu hình cho hội thoại.
Không tự đổi provider hoặc lấy key tài khoản khác khi lỗi quota.
Chọn model ID không bảo đảm có quyền API hoặc hỗ trợ công cụ; nút kiểm kết nối xác minh bằng dữ liệu giả và thông báo có thể tiêu quota.
Thao tác lưu không tự gọi model.
Đổi provider/model không tự gửi lại lịch sử cũ; người dùng chủ động tiếp tục hoặc tạo hội thoại mới.

### Cách ly và lưu trữ

Chủ tài khoản lấy từ issuer và subject trong JWT đã kiểm chữ ký, issuer, audience và thời hạn.
Không tin `ownerId` do trình duyệt hoặc model gửi; ADMIN không có API đọc key hoặc hội thoại của người khác.
Mọi truy vấn cấu hình, hội thoại, tin nhắn và đề xuất ràng buộc chủ ở phía server.
Mỗi hội thoại cố định mô hình nghiệp vụ; mỗi lượt đọc lại quyền Core trước khi đưa kết quả vào ngữ cảnh.
Khi mất quyền mô hình, không trả lại nội dung hội thoại và đề xuất của mô hình đó dù bản sao vẫn còn trong DB.
Đọc lại hồ sơ và tài liệu trước khi tái sử dụng nguồn cũ; nếu quyền nguồn đã thay đổi, chặn tiếp tục hội thoại cũ và yêu cầu tạo hội thoại mới.
Không gửi lịch sử cũ có nguồn chưa xác minh lại tới provider.

Schema AI giữ cấu hình provider, hội thoại, tin nhắn, nguồn trích dẫn, đề xuất và trạng thái xử lý sự kiện.
API key và credential nền được mã hóa bằng AEAD gắn với chủ; khóa gốc nằm ngoài DB, ngoài Git và không gửi tới frontend.
API chỉ trả trạng thái đã cấu hình và nhãn đã che; log không ghi Authorization, API key, prompt hoặc nội dung hồ sơ mặc định.
Xóa hội thoại xóa tin nhắn và nguồn tương ứng; đề xuất đã thực thi chỉ giữ thông tin đối chiếu tối thiểu theo chính sách lưu trữ được review.
Backup bao gồm dữ liệu AI và cách khôi phục khóa; tài liệu nêu rõ bản backup nào chứa bí mật và ai có quyền khôi phục.

## 4. Hợp đồng dịch vụ

Lập schema request/response trước khi viết handler và sinh OpenAPI từ cơ chế schema được chọn.
Giữ envelope `success`, `message`, `data`, `errorCode`, HTTP status và bản dịch lỗi theo quy ước DX-OS.
Các route dưới đây là nhóm hợp đồng dự kiến, không phải danh sách endpoint đã có.

| Nhóm dưới `/ai-api/v1` | Hành vi |
|---|---|
| `/features`, `/health` | Trạng thái dịch vụ không lộ thông tin tài khoản hoặc bí mật |
| `/me/providers` | Tạo, liệt kê, thay và xóa cấu hình của chủ |
| `/me/providers/{id}/probe` | Kiểm kết nối khi người dùng yêu cầu |
| `/me/conversations` | Tạo theo mô hình, liệt kê, đổi tên và xóa hội thoại |
| `/me/conversations/{id}/messages` | Đọc lịch sử và gửi lượt hỏi với mã request chống gửi trùng |
| `/me/proposals/{id}` | Xem, xác nhận và hủy đề xuất của chủ |
| `/me/agents` | Bật/tắt cấu hình sự kiện đã được phép của chính người dùng |
| `/webhooks/records` | Nhận sự kiện có chữ ký; không cho webhook trực tiếp thực thi thao tác nghiệp vụ |

Trả lời tăng dần qua POST với luồng SSE đọc bằng fetch có Authorization; không đặt JWT hoặc API key trong URL.
Thông điệp luồng phân biệt đoạn chữ, nguồn, đề xuất, hoàn tất, lỗi và usage nếu provider trả được.
Mất kết nối đánh dấu lượt bị gián đoạn; tải lại không tự gửi prompt hoặc thực thi công cụ lần nữa.
Nút dừng hủy yêu cầu đang chạy trong khả năng transport, không cam kết nhà cung cấp hoàn tiền hoặc dừng tính token tức thời.

## 5. Công cụ và truy xuất nhẹ

### Dữ liệu nghiệp vụ

Công cụ chỉ gồm metadata, hộp việc, danh sách có bộ lọc, chi tiết, lịch sử, thống kê, người nhận hợp lệ và tạo đề xuất.
Tên trường, toán tử, hành động và model/entity phải thuộc metadata Core; model không đưa SQL, URL hoặc tên bảng tùy ý.
Core nhận danh tính của người dùng và tự kiểm quyền theo dòng; dịch vụ AI không giữ kết nối DB nghiệp vụ.
Thống kê lấy từ endpoint thống kê, không suy tổng từ một trang hồ sơ.
Kết quả có phân trang phải giữ thông tin còn trang và giới hạn; không mô tả danh sách bị cắt là đầy đủ.
Không lập chỉ mục tập trung nội dung hồ sơ hay nội dung tệp.

### Kho tri thức

Người triển khai nạp tài liệu văn bản/Markdown do họ có quyền sử dụng, với mã nguồn, tiêu đề, phiên bản, giấy phép và phạm vi mô hình.
Bản đầu không mở luồng tải URL, OCR, PDF hoặc upload tệp tùy ý từ hội thoại.
Chuẩn hóa Unicode tiếng Việt, chia đoạn theo cấu trúc và xếp hạng BM25 trong phạm vi nguồn được phép đọc.
Giới hạn kích thước kho và số đoạn nạp vào bộ nhớ; vượt giới hạn thì báo rõ, không âm thầm bỏ tài liệu.
Không trộn corpus hoặc cache giữa các mô hình và phạm vi truy cập.
Chỉ lấy các đoạn đứng đầu trong ngân sách ngữ cảnh; không thêm bước gọi LLM riêng để rerank.
Câu trả lời chỉ được dẫn ID đoạn thực sự đã cấp; ID lạ bị từ chối và không trở thành liên kết nguồn.
Không có bằng chứng phù hợp thì báo thiếu bằng chứng hoặc hỏi lại; điểm BM25 không được trình bày thành xác suất đúng.

Tạo bộ tri thức giả lập gồm hướng dẫn thủ tục và tình huống xử lý hư cấu, không dùng dữ liệu cá nhân hoặc tài liệu quy chế thật.
Ghi rõ nội dung không phải quy định của trường hoặc tổ chức thật.
Khi triển khai, giữ fixture và câu hỏi kiểm ngoài Git mặc định; chỉ đóng gói corpus demo khi quyền sử dụng, giấy phép và nhu cầu phát hành đã được chốt.

### Adapter provider

Chuẩn hóa text, lời gọi công cụ, kết quả công cụ, lỗi, hủy luồng và usage vào hợp đồng dùng chung.
Kiểm API chính thức và khả năng model trước khi ghim phiên bản adapter; không suy ba provider có cùng payload.
Model chỉ yêu cầu đọc hoặc tạo đề xuất; không nhận công cụ xác nhận hay credential ghi.
Nội dung hồ sơ, tài liệu và kết quả công cụ là dữ liệu không đáng tin, không được sửa danh sách công cụ hoặc phạm vi quyền.
Giới hạn lượt gọi công cụ và lượng dữ liệu trên mỗi lượt để tránh vòng lặp và chi phí không kiểm soát.

## 6. Đề xuất và xác nhận

Server tạo snapshot từ metadata và dữ liệu Core, không nhận bằng chứng quyền từ câu trả lời của LLM.
Snapshot gồm chủ, mô hình, thực thể, hồ sơ, thao tác, tham số, version hồ sơ, modelVersion, nguồn và thời hạn.
Thẻ xác nhận do server dựng từ snapshot, không dùng văn bản model làm nội dung quyết định.
Đổi thao tác, người nhận, ghi chú hoặc giá trị trường phải tạo đề xuất mới và xác nhận lại.

Luồng trạng thái là `pending` → `executing` → `succeeded`, `failed` hoặc `uncertain`; hủy chuyển sang `cancelled`.
Claim từ `pending` phải nguyên tử, kiểm đúng chủ và chưa hết hạn; xác nhận kép không thực thi lần hai.
Timeout hoặc mất tiến trình sau khi gửi ghi tạo trạng thái `uncertain`, không tự gửi lại lệnh ghi.
Chỉ báo thành công khi có bằng chứng từ Core, kèm liên kết hồ sơ và lịch sử để đối chiếu.

Core cần nhận điều kiện version/modelVersion cho giao việc và hành động, kiểm cùng quyền, trạng thái và `when` trong giao dịch dưới khóa phù hợp.
Giữ hành vi tương thích cho client hiện có không gửi điều kiện; các lệnh do AI tạo bắt buộc có đủ điều kiện.
Chỉ kiểm snapshot tại dịch vụ AI rồi gọi API ghi không điều kiện là không đủ vì hồ sơ có thể thay đổi giữa hai bước.
Với đề xuất sửa một trường, chỉ nhận trường và kiểu đã khai, còn được phép sửa; dùng đường update Core có kiểm version và bổ sung modelVersion nếu chưa có.
Không cho AI sửa trường hệ thống, vai, quyền hoặc cấu trúc mô hình.
Hiển thị tác động membership và trạng thái tác vụ hoàn tất bất đồng bộ; không coi request thành công là mọi tác động ngoài đã hoàn tất.

## 7. Tác tử theo sự kiện

Đề xuất cấu hình trong dịch vụ AI theo mô hình/thực thể/sự kiện, trường đích, hướng dẫn và nguồn tri thức; không thêm DSL vào Core trong bản thiết kế này.
Người dùng có quyền bật cấu hình cho chính họ và dùng API key của họ; không tự chọn một người khác để tiêu quota hoặc nhận đề xuất.
Chỉ sinh đề xuất cho người duyệt có quyền đọc và sửa trường đích; không dùng một tài khoản ADMIN dùng chung.
Webhook đã ký chỉ là tín hiệu hồ sơ đổi, không phải bằng chứng người dùng được đọc hồ sơ.
Worker phải đọc lại Core theo danh tính đã ủy quyền và kiểm phạm vi trước khi gọi model.

Phần chạy nền cần phiên SSO riêng có thể thu hồi, qua authorization code, PKCE và state một lần; không lưu refresh token lấy từ phiên frontend.
Kiểm khả năng và chính sách phiên nền của Keycloak hiện tại trước khi triển khai; nếu chưa thể cấp quyền nền an toàn thì phần tác tử còn mở, không thay bằng quyền ADMIN.
Chưa bật tác tử thì không lưu credential nền hoặc chạy job model.
Lưu tác vụ và khử trùng theo eventId, người duyệt và cấu hình; webhook lặp không sinh thêm đề xuất hoặc gọi model lặp.
Nhận bền vững rồi trả 2xx sớm; không giữ webhook chờ LLM.
Job có giới hạn đồng thời và retry hữu hạn; tác động ghi vẫn chỉ đi qua xác nhận trên web.
Sự kiện do chính đề xuất vừa áp dụng không tự tạo vòng lặp đề xuất; kiểm nguồn thay đổi và khóa khử trùng.
Hỏng hoặc thiếu webhook phải hiện tình trạng chưa đồng bộ; không lấy feed ADMIN để bù cho người dùng thường.
Không thêm yêu cầu nhiều đích webhook vào Core; nếu bản cài đã dùng đích khác, cấu hình nhận sự kiện cần được chốt trước khi bật tác tử.

## 8. Giao diện

Thêm trang chat nạp chậm trong ứng dụng hiện tại, giữ cơ chế SSO và chủ đề Material UI.
Thanh bên thu gọn gồm hội thoại mới và lịch sử của mô hình đang chọn; vùng chat trung tâm và ô nhập nhiều dòng ở dưới.
Chọn provider/model bằng cấu hình đã lưu; cài đặt API ở panel riêng, không chiếm màn hình chat thường xuyên.
Không sao chép logo, tên sản phẩm hoặc tài sản độc quyền của Gemini.
Nguồn mở được ngay từ câu trả lời; thẻ hồ sơ đi tới màn hình nghiệp vụ hiện có.
Thẻ đề xuất có Xác nhận/Hủy và trạng thái đang thực hiện, hết hạn, lỗi hoặc chưa rõ kết quả.
Phím Enter gửi và Shift+Enter xuống dòng, tôn trọng bộ gõ đang composition; gửi bằng nút vẫn dùng được trên mobile.
Giữ focus, nhãn truy cập, tương phản, reduced motion, theme sáng/tối và dịch theo cơ chế Việt/Anh hiện tại.
Không render HTML do model cung cấp; liên kết chỉ theo giao thức cho phép và nguồn do server xác thực.

## 9. Thứ tự triển khai và đầu ra

| Bước | Đầu ra review được | Điều kiện đi tiếp |
|---|---|---|
| 0 | Chốt phạm vi, nguồn mở, tác tử và hợp đồng dữ liệu | Tech lead review các quyết định ở mục 2 |
| 1 | Kiểm giấy phép từng ứng viên, hợp đồng OpenAPI, schema dữ liệu AI và giới hạn tài nguyên | Báo kết quả trước khi thêm manifest; chưa có kết luận thì không thêm dependency |
| 2 | Dịch vụ AI, xác thực, cấu hình mã hóa, lưu hội thoại và ba adapter | Hai chủ tách biệt; chưa có key không gọi provider |
| 3 | Công cụ đọc, corpus giả lập, BM25 và câu trả lời có nguồn | Truy xuất đúng phạm vi, không bịa nguồn hoặc số tổng |
| 4 | Điều kiện phiên bản ở Core, snapshot và xác nhận ghi | Snapshot cũ, confirm kép và timeout được xử lý đúng |
| 5 | Trang chat hoàn chỉnh và các trạng thái sử dụng | Dùng được bằng bàn phím, mobile và theme hiện có |
| 6 | Nhận sự kiện, phiên nền có thể thu hồi và đề xuất giá trị trường | Khử trùng, đúng chủ, không tự ghi hoặc tự lặp |
| 7 | Ghép cài đặt, backup/restore, giới hạn tài nguyên và tài liệu sử dụng | Tắt AI không chặn Core; người triển khai có thể khôi phục dữ liệu |
| 8 | Kiểm chức năng trong môi trường được duyệt, API thật và nghiệm thu | Checklist có bằng chứng và giới hạn còn mở, không tick thay người dùng |

## 10. Kiểm chứng

Lượt hiện tại chỉ kiểm tài liệu, liên kết và diff; không chạy ứng dụng, Docker hoặc gọi provider.
Khi sửa backend, mặc định chạy `./mvnw compile`, hoặc `./mvnw test-compile` nếu sửa test source, cùng kiểm định dạng đúng nhánh và đọc diff.
Frontend chạy `npx tsc --noEmit`; dịch vụ TypeScript mới kiểm typecheck và build theo script sẽ khai báo.
Không tự thêm file test hoặc framework; dùng hạ tầng kiểm hiện có và dữ liệu giả lập ngoài Git khi phần kiểm chức năng được duyệt.
Check CI bắt buộc và kiểm tích hợp rộng hơn được thực hiện đúng giai đoạn, không coi compile là nghiệm thu chức năng.

| Nhóm ca | Bằng chứng cần có |
|---|---|
| Provider | Ba API thật: kết nối, text/stream, gọi công cụ, key sai, model không hỗ trợ, quota và hủy/timeout; không công bố provider chỉ mới kiểm mô phỏng |
| Danh tính | Hai tài khoản, hai mô hình, đổi/xóa quyền; đoán ID, cache và hội thoại cũ không lộ nội dung |
| Nghiệp vụ | Đọc đúng quyền, thống kê đúng API, giao/trả việc và hành động trên ba họ quy trình |
| Tri thức | Bộ câu hỏi tiếng Việt có đáp án và nguồn chuẩn, không có đáp án, sai mã, sai mô hình và nội dung chứa chỉ dẫn độc hại |
| Xác nhận | Hết hạn, sai chủ, đổi snapshot, version/modelVersion cũ, confirm đồng thời, crash và kết quả chưa rõ |
| Sự kiện | Webhook giả/lặp, mất phiên nền, mất quyền, worker khởi động lại và ngăn vòng lặp |
| Vận hành | Không key, provider lỗi, tắt AI, restart và backup/restore; Core tiếp tục nghiệp vụ |
| Giao diện | Desktop/mobile, bàn phím và bộ gõ tiếng Việt, sáng/tối, nguồn, thẻ xác nhận và lỗi luồng |

Bộ đo truy xuất đề xuất có ít nhất 30 câu tiếng Việt, gồm 20 câu có bằng chứng, 5 câu không có đáp án và 5 ca sai phạm vi hoặc prompt injection.
Mục tiêu review là đoạn đúng có trong top 5 ở ít nhất 18/20 câu có đáp án; mọi ca sai phạm vi không được lộ nguồn và mọi trích dẫn phải tồn tại.
Tách số đo tìm được đoạn khỏi số đo câu trả lời được nguồn hỗ trợ; trả lời bịa hoặc thao tác sai quyền là lỗi chặn nghiệm thu dù trung bình cao.
Các ngưỡng này là mục tiêu đề xuất, chưa phải kết quả đã đo.

Đo thêm CPU, RAM, kích thước image, thời gian truy xuất, thời gian tới token đầu và tổng token ở trạng thái không bật AI, AI nhàn rỗi và hai người chat đồng thời.
Đặt giới hạn cứng cho payload, corpus, lịch sử, output token, lượt công cụ, concurrency và timeout trước khi mở gọi API thật.
Giá trị cấu hình đầy đủ đặt ở schema/env nguồn khi triển khai, không tạo bảng cấu hình rời rạc tại đây.
Nếu thiếu key hoặc quota kiểm một provider, ghi rõ chưa nghiệm thu provider đó và giữ mục tương ứng mở.
