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

Tech lead review pipeline self-host theo OSAID, hồ sơ Apertus Instruct, artifact lượng tử hóa và runtime.
Chốt cấu hình triển khai sau khảo sát chất lượng và tài nguyên; tự host chưa phải bằng chứng đạt OSAID.
Chốt dịch vụ AI riêng, schema riêng, route giao diện và hợp đồng xác nhận; không thêm module Java thứ năm.
Chốt stack sau khảo sát thư viện có sẵn tại mục 5; ưu tiên tái sử dụng pipeline, không tự viết thuật toán tìm kiếm hoặc engine suy luận.
Route dự kiến là `/ai` cho giao diện và `/ai-api/v1` cho dịch vụ; kiểm xung đột route trước khi triển khai.
Chốt phần tác tử theo sự kiện: cấu hình theo mô hình, trường được đề xuất và danh tính người duyệt.
Không tự bỏ phần tác tử hoặc coi nó đã hoàn thành nếu cổng này chưa có quyết định.
Chỉ sau review mới đồng bộ mục tiêu trên issue và tài liệu định hướng trong phạm vi được cho phép.
Quyền sửa local không tự cấp quyền chỉnh issue, gửi bình luận hoặc thực hiện thao tác Git xuất bản.

## 3. Danh tính, cấu hình và dữ liệu

### Backend self-host

Endpoint `llama.cpp`, model và giới hạn suy luận do người triển khai cấu hình cố định ở hạ tầng; giao diện người dùng chỉ đọc trạng thái và model đang phục vụ.
Lưu cấu hình hạ tầng không tự gọi model; kiểm kết nối thực hiện bằng dữ liệu giả khi người triển khai yêu cầu.
Kết nối nội bộ chỉ tới dịch vụ đã định danh, có xác thực dịch vụ; không chấp nhận redirect hoặc cho model điều khiển đích kết nối.
Không publish cổng suy luận trực tiếp ra Internet và không chuyển JWT, refresh token hoặc bí mật Core tới model local.
Một tiến trình suy luận dùng chung nhưng prompt, lịch sử, cache và kết quả phải giữ đúng chủ/mô hình; backend local không thay kiểm quyền Core.
Backend lỗi hoặc đầy hàng đợi phải báo rõ, giữ trạng thái lượt bị gián đoạn và cho người dùng chủ động thử lại.
Ghim model/runtime và lưu model ID cùng lượt chat để đối chiếu; đổi model không tự phát lại lịch sử hoặc lệnh ghi.
Người triển khai chuẩn bị trọng số theo phiên bản và checksum đã kiểm, lưu ngoài Git trong volume/cache riêng; ghi artifact vào hồ sơ thành phần theo chính sách dự án.
Khởi động thiếu trọng số trả trạng thái chưa sẵn sàng, không tự tải model lớn hoặc giữ giao dịch Core chờ tải.
Sau khi chuẩn bị đủ artifact, kiểm luồng hỏi đáp và tác tử với mạng ra ngoài bị chặn để chứng minh suy luận self-host.

### Cách ly và lưu trữ

Chủ tài khoản lấy từ issuer và subject trong JWT đã kiểm chữ ký, issuer, audience và thời hạn.
Không tin `ownerId` do trình duyệt hoặc model gửi; ADMIN không có API đọc credential nền hoặc hội thoại của người khác.
Mọi truy vấn cấu hình, hội thoại, tin nhắn và đề xuất ràng buộc chủ ở phía server.
Mỗi hội thoại cố định mô hình nghiệp vụ; mỗi lượt đọc lại quyền Core trước khi đưa kết quả vào ngữ cảnh.
Khi mất quyền mô hình, không trả lại nội dung hội thoại và đề xuất của mô hình đó dù bản sao vẫn còn trong DB.
Đọc lại hồ sơ và tài liệu trước khi tái sử dụng nguồn cũ; nếu quyền nguồn đã thay đổi, chặn tiếp tục hội thoại cũ và yêu cầu tạo hội thoại mới.
Không đưa lịch sử cũ có nguồn chưa xác minh lại vào ngữ cảnh suy luận.

Schema AI giữ tùy chọn hội thoại, tin nhắn, nguồn trích dẫn, đề xuất và trạng thái xử lý sự kiện.
Credential nền được mã hóa bằng AEAD gắn với chủ; khóa gốc nằm ngoài DB, ngoài Git và không gửi tới frontend.
Bí mật kết nối runtime nằm trong cấu hình hạ tầng; API chỉ trả trạng thái khả dụng và model ID, log không ghi Authorization, credential, prompt hoặc nội dung hồ sơ mặc định.
Xóa hội thoại xóa tin nhắn và nguồn tương ứng; đề xuất đã thực thi chỉ giữ thông tin đối chiếu tối thiểu theo chính sách lưu trữ được review.
Backup bao gồm dữ liệu AI và cách khôi phục khóa; tài liệu nêu rõ bản backup nào chứa bí mật và ai có quyền khôi phục.

## 4. Hợp đồng dịch vụ

Lập schema request/response trước khi viết handler và sinh OpenAPI từ cơ chế schema được chọn.
Giữ envelope `success`, `message`, `data`, `errorCode`, HTTP status và bản dịch lỗi theo quy ước DX-OS.
Các route dưới đây là nhóm hợp đồng dự kiến, không phải danh sách endpoint đã có.

| Nhóm dưới `/ai-api/v1` | Hành vi |
|---|---|
| `/features`, `/health` | Trạng thái dịch vụ không lộ thông tin tài khoản hoặc bí mật |
| `/runtime` | Người đã đăng nhập đọc trạng thái khả dụng, model và giới hạn sử dụng; không trả endpoint hoặc bí mật hạ tầng |
| `/me/conversations` | Tạo theo mô hình, liệt kê, đổi tên và xóa hội thoại |
| `/me/conversations/{id}/messages` | Đọc lịch sử và gửi lượt hỏi với mã request chống gửi trùng |
| `/me/proposals/{id}` | Xem, xác nhận và hủy đề xuất của chủ |
| `/me/agents` | Bật/tắt cấu hình sự kiện đã được phép của chính người dùng |
| `/webhooks/records` | Nhận sự kiện có chữ ký; không cho webhook trực tiếp thực thi thao tác nghiệp vụ |

Trả lời tăng dần qua POST với luồng SSE đọc bằng fetch có Authorization; không đặt JWT hoặc bí mật dịch vụ trong URL.
Thông điệp luồng phân biệt đoạn chữ, nguồn, đề xuất, hoàn tất, lỗi và số token nếu runtime trả được.
Mất kết nối đánh dấu lượt bị gián đoạn; tải lại không tự gửi prompt hoặc thực thi công cụ lần nữa.
Nút dừng hủy yêu cầu suy luận qua transport; kiểm runtime thực sự ngừng xử lý và giải phóng slot sau khi hủy.

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

### Adapter suy luận

Chuẩn hóa text, lời gọi công cụ, kết quả công cụ, lỗi, hủy luồng và usage vào hợp đồng dùng chung.
Kiểm hợp đồng `llama.cpp`, chat template và khả năng model trước khi ghim adapter; không suy hỗ trợ text đồng nghĩa gọi công cụ đúng.
Model chỉ yêu cầu đọc hoặc tạo đề xuất; không nhận công cụ xác nhận hay credential ghi.
Nội dung hồ sơ, tài liệu và kết quả công cụ là dữ liệu không đáng tin, không được sửa danh sách công cụ hoặc phạm vi quyền.
Giới hạn lượt gọi công cụ và lượng dữ liệu trên mỗi lượt để tránh vòng lặp và chiếm tài nguyên không kiểm soát.

### Tái sử dụng dự án nguồn mở

| Thành phần | Ứng viên và nguồn | Cách sử dụng đề xuất |
|---|---|---|
| Pipeline RAG | [Haystack của deepset](https://github.com/deepset-ai/haystack), Apache-2.0 | Ưu tiên khảo sát chia đoạn, BM25, lọc metadata, prompt và generator có sẵn; chỉ dùng component cần thiết |
| Suy luận local | [llama.cpp](https://github.com/ggml-org/llama.cpp), MIT | Server C/C++ chạy GGUF trên CPU/GPU, gọi qua adapter; không tự viết engine hoặc vá upstream |
| Embedding/rerank khi cần | [FastEmbed của Qdrant](https://github.com/qdrant/fastembed), Apache-2.0 | ONNX Runtime, không bắt buộc PyTorch/GPU; kiểm riêng model và dependency được chọn |
| Rerank chuyên biệt khi cần | [FlashRank](https://github.com/PrithivirajDamodaran/FlashRank), Apache-2.0 | So sánh với FastEmbed nếu truy xuất chưa đạt; có lựa chọn CPU không cần Torch/Transformers |

Đây là danh sách ứng viên, không phải danh sách dependency được duyệt hoặc yêu cầu cài cả bốn.
Các giấy phép nêu trên là giấy phép mã dự án upstream; trước khi thêm manifest vẫn phải kiểm đúng phiên bản, artifact, dependency bắc cầu và thành phần bundled.
Giấy phép thư viện không chứng minh model embedding/reranker đi kèm đạt OSAID.
FlashRank có model nhỏ khoảng 4 MB và lựa chọn đa ngôn ngữ khoảng 150 MB theo tài liệu upstream; kích thước trọng số không phải tổng RAM và không bảo đảm chất lượng tiếng Việt.
So sánh Haystack/Python với dịch vụ TypeScript tối thiểu theo lượng mã tích hợp, dependency, image và tài nguyên khi nhàn rỗi; chọn một stack điều phối trước khi triển khai.
Mã tự viết tập trung vào danh tính DX-OS, công cụ Core, snapshot và xác nhận; thư viện pipeline không thay được các rào chắn này.

### Khảo sát Apertus 8B lượng tử hóa

Model khảo sát là [swiss-ai/Apertus-8B-Instruct-2509](https://huggingface.co/swiss-ai/Apertus-8B-Instruct-2509), có trọng số Apache-2.0 và tài liệu nguồn huấn luyện để đối chiếu.
Không chọn model nhỏ chỉ vì tên có nhãn open hoặc vì chạy vừa RAM.
Đối chiếu checklist OSAID cho đúng bản Instruct, kể cả phần hậu huấn luyện; không coi Apache-2.0 của trọng số là kết luận đủ.

| Bản trọng số | Dung lượng file tham khảo | Vai trò trong khảo sát |
|---|---|---|
| BF16 | 16,12 GB | Bản độ chính xác gốc để hiểu mức giảm dung lượng; chưa yêu cầu tải/chạy |
| Q8_0 | 8,57 GB | Tham khảo mức lượng tử hóa ít hơn; chưa yêu cầu tải/chạy |
| Q5_K_M | 5,81 GB | Đối chứng khi Q4 chưa đạt, nếu tài nguyên cho phép |
| Q4_K_M | 5,06 GB | Ưu tiên chạy thử đầu tiên |

Dung lượng theo [artifact GGUF tham khảo](https://huggingface.co/bartowski/swiss-ai_Apertus-8B-Instruct-2509-GGUF), là bản chuyển đổi bên thứ ba, không phải xác nhận đã kiểm giấy phép/artifact hoặc chất lượng.
RAM cần thêm KV cache, buffer và runtime; không lấy 5,06 GB làm mức RAM đủ cho model hay cả stack DX-OS.
Kiểm nguồn trọng số, phiên bản công cụ chuyển đổi, cấu hình và dữ liệu hiệu chỉnh nếu dùng; ghi hồ sơ giấy phép và provenance trước khi phân phối.
Nếu artifact bên thứ ba không đủ hồ sơ, dùng công cụ upstream nguyên bản để chuyển đổi trọng số chính thức theo quy trình được duyệt, không fork hoặc vendor mã.

Cấu hình khảo sát ban đầu: `llama.cpp`, Q4_K_M, tổng ngữ cảnh 4.096 token gồm prompt/lịch sử/nguồn và phần dành cho output, một lượt suy luận tại một thời điểm.
RAG chỉ dùng BM25 và ít đoạn liên quan; chưa nạp embedding hoặc reranker để đo riêng phần LLM.
Giới hạn output, hàng đợi và thời gian chờ; không cam kết độ trễ khi chưa đo hoặc bật toàn bộ ngữ cảnh tối đa của model.
Lượng tử hóa có thể giảm độ đúng của câu trả lời, JSON và tham số công cụ; tác động cụ thể phải đo, không gán tỷ lệ chất lượng theo số bit.
Q4 không mặc nhiên nhanh hơn mọi bản khác trên mọi phần cứng; ghi rõ CPU/GPU, số luồng và phiên bản runtime khi đo.

Dùng cùng bộ câu hỏi, công cụ, chat template, ngân sách ngữ cảnh và tham số sinh khi so Q4 với Q5; ghi nhận các sai khác còn lại.
Kiểm riêng mức hỗ trợ tool calling/chat template của Apertus trên bản `llama.cpp` được ghim; JSON đúng schema chưa chứng minh chọn công cụ hoặc tham số đúng.
Phân biệt lỗi truy xuất thiếu bằng chứng, giới hạn model gốc, runtime/template và suy giảm do lượng tử hóa; không mặc nhiên quy mọi lỗi Q4 cho lượng tử hóa.
Đo câu trả lời tiếng Việt có nguồn, chọn công cụ/tham số, từ chối khi thiếu nguồn và không tạo thao tác ngoài quyền.
Đo RAM đỉnh, CPU, thời gian tới token đầu, token/giây, tổng độ trễ, timeout và ảnh hưởng tới API Core trên phần cứng dự kiến triển khai.
Đo một lượt trước, sau đó hai tài khoản gửi đồng thời vào hàng đợi để kiểm cách ly và chờ; không tự tăng concurrency để che độ trễ.

Tech lead review ngưỡng tài nguyên/độ trễ theo máy đích trước khi chạy khảo sát; chưa có số đo thì không kết luận local đủ nhẹ.
Nếu Q4 đạt các ca chặn nghiệm thu và ngân sách vận hành, chốt nó làm cấu hình self-host; nếu chưa đạt thì đối chứng Q5 khi cần.
Nếu cả hai chưa đạt, ghi rõ phần chất lượng hoặc tài nguyên còn thiếu để tech lead chốt cách xử lý trong hướng self-host; không nới tiêu chí quyền/nguồn/công cụ hoặc tự tuyên bố toàn pipeline đạt OSAID.
Nội dung khảo sát này chưa cấp quyền tải/chạy model trong lượt sửa tài liệu.

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
Người dùng có quyền bật cấu hình tác tử cho chính họ, dùng backend self-host; không tự chọn người khác để nhận đề xuất.
Tác vụ nền dùng cùng ngân sách hàng đợi và giới hạn suy luận, không làm nghẽn các lượt chat tương tác.
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
Thanh trên hiển thị model self-host đang dùng và trạng thái sẵn sàng/đang chờ; cài đặt hội thoại ở panel riêng.
Giao diện không cho người dùng sửa endpoint hoặc bí mật hạ tầng.
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
| 0a | So sánh Haystack với stack tối thiểu, kiểm hồ sơ model/runtime và khảo sát Q4/Q5 trong môi trường được duyệt | Chốt stack điều phối, cấu hình self-host đạt hoặc phần chất lượng/tài nguyên còn phải xử lý |
| 1 | Kiểm giấy phép từng ứng viên, hợp đồng OpenAPI, schema dữ liệu AI và giới hạn tài nguyên | Báo kết quả trước khi thêm manifest; chưa có kết luận thì không thêm dependency |
| 2 | Dịch vụ AI, xác thực, lưu hội thoại, credential nền mã hóa và adapter llama.cpp | Hai chủ tách biệt; chỉ gọi backend self-host đã cấu hình |
| 3 | Công cụ đọc, corpus giả lập, BM25 và câu trả lời có nguồn | Truy xuất đúng phạm vi, không bịa nguồn hoặc số tổng |
| 4 | Điều kiện phiên bản ở Core, snapshot và xác nhận ghi | Snapshot cũ, confirm kép và timeout được xử lý đúng |
| 5 | Trang chat hoàn chỉnh và các trạng thái sử dụng | Dùng được bằng bàn phím, mobile và theme hiện có |
| 6 | Nhận sự kiện, phiên nền có thể thu hồi và đề xuất giá trị trường | Khử trùng, đúng chủ, không tự ghi hoặc tự lặp |
| 7 | Ghép cài đặt, backup/restore, giới hạn tài nguyên và tài liệu sử dụng | Tắt AI không chặn Core; người triển khai có thể khôi phục dữ liệu |
| 8 | Kiểm chức năng trong môi trường được duyệt, model self-host thật và nghiệm thu | Checklist có bằng chứng và giới hạn còn mở, không tick thay người dùng |

## 10. Kiểm chứng

Lượt hiện tại chỉ kiểm tài liệu, liên kết và diff; không tải/chạy model, ứng dụng hoặc Docker.
Khi sửa backend, mặc định chạy `./mvnw compile`, hoặc `./mvnw test-compile` nếu sửa test source, cùng kiểm định dạng đúng nhánh và đọc diff.
Frontend chạy `npx tsc --noEmit`; dịch vụ AI kiểm cú pháp/typecheck/build phù hợp với stack đã chốt và script sẽ khai báo.
Không tự thêm file test hoặc framework; dùng hạ tầng kiểm hiện có và dữ liệu giả lập ngoài Git khi phần kiểm chức năng được duyệt.
Check CI bắt buộc và kiểm tích hợp rộng hơn được thực hiện đúng giai đoạn, không coi compile là nghiệm thu chức năng.

| Nhóm ca | Bằng chứng cần có |
|---|---|
| Self-host | Q4 chạy thật và Q5 đối chứng khi cần; ghi artifact/runtime/phần cứng, tiếng Việt có nguồn, công cụ/JSON, tài nguyên, độ trễ và kết luận theo mục 5 |
| Danh tính | Hai tài khoản, hai mô hình, đổi/xóa quyền; đoán ID, cache và hội thoại cũ không lộ nội dung |
| Nghiệp vụ | Đọc đúng quyền, thống kê đúng API, giao/trả việc và hành động trên ba họ quy trình |
| Tri thức | Bộ câu hỏi tiếng Việt có đáp án và nguồn chuẩn, không có đáp án, sai mã, sai mô hình và nội dung chứa chỉ dẫn độc hại |
| Xác nhận | Hết hạn, sai chủ, đổi snapshot, version/modelVersion cũ, confirm đồng thời, crash và kết quả chưa rõ |
| Sự kiện | Webhook giả/lặp, mất phiên nền, mất quyền, worker khởi động lại và ngăn vòng lặp |
| Vận hành | Thiếu trọng số, runtime lỗi, đầy hàng đợi, tắt AI, restart và backup/restore; Core tiếp tục nghiệp vụ |
| Giao diện | Desktop/mobile, bàn phím và bộ gõ tiếng Việt, sáng/tối, nguồn, thẻ xác nhận và lỗi luồng |

Bộ đo truy xuất đề xuất có ít nhất 30 câu tiếng Việt, gồm 20 câu có bằng chứng, 5 câu không có đáp án và 5 ca sai phạm vi hoặc prompt injection.
Mục tiêu review là đoạn đúng có trong top 5 ở ít nhất 18/20 câu có đáp án; mọi ca sai phạm vi không được lộ nguồn và mọi trích dẫn phải tồn tại.
Tách số đo tìm được đoạn khỏi số đo câu trả lời được nguồn hỗ trợ; trả lời bịa hoặc thao tác sai quyền là lỗi chặn nghiệm thu dù trung bình cao.
Các ngưỡng này là mục tiêu đề xuất, chưa phải kết quả đã đo.

Đo thêm CPU, RAM, kích thước image, thời gian truy xuất, thời gian tới token đầu và tổng token ở trạng thái không bật AI, AI nhàn rỗi và hai người chat đồng thời.
Đặt giới hạn cứng cho payload, corpus, lịch sử, output token, lượt công cụ, concurrency và timeout trước khi chạy model thật.
Giá trị cấu hình đầy đủ đặt ở schema/env nguồn khi triển khai, không tạo bảng cấu hình rời rạc tại đây.
Nếu chưa có trọng số, runtime hoặc máy đích phù hợp để kiểm, ghi rõ model self-host chưa được nghiệm thu và giữ mục tương ứng mở.
