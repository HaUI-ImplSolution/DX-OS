# Việc tồn đọng của lõi dynamic

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của lõi dynamic.
Hướng đi nằm ở [`README.md`](./README.md).
Làm xong một mục thì xóa dòng đó, không ghi lại diễn biến.

## 1. Thứ tự đến hạn nộp

Hạn gửi bài là 17:00 ngày 06/12/2026.
Mốc dưới đây là dự kiến.

| Mốc | Nội dung | Dự kiến xong |
|---|---|---|
| Phát hành 2.0.0 | Tag `v2.0.0` và GitHub Release, triển khai công khai, trang docs, gửi bài | 06/12 |

## 2. Phải xử lý trước khi phát hành

| Việc | Ghi chú |
|---|---|
| Tag `v2.0.0` và GitHub Release | Tạo từ `main`; ngày của mục `[2.0.0]` trong `CHANGELOG.md` sửa theo ngày tag; đẩy tag thì workflow `Release Package` tự tạo Release từ mục đó (không tạo Release bằng tay trước, vì workflow không ghi đè nội dung đã có); rồi chạy tay workflow `Release Images` trên tag để có image `v2.0.0` mà `dxos-install.sh` kéo về |
| Kiểm cài bằng image thật | `dxos-install.sh` và `dxos start <tệp>` mới kiểm bằng image local qua `install.sh`; sau khi workflow Release có image `v2.0.0`, chạy lại trên máy sạch |
| Triển khai công khai | VPS giữ `v1.1.1`; bản mới không có đường nâng cấp dữ liệu nên phải dựng lại; đặt `STAFF_OTP_FLOW=CONDITIONAL` trước lần khởi động đầu để bắt ADMIN nhập mã một lần, đặt `KEYCLOAK_HOSTNAME=${GATEWAY_PUBLIC_URL}` trong `.env` thay cho `KC_HOSTNAME` ở file override (đặt `KC_HOSTNAME` một mình thì gateway từ chối token), và bật quét mã độc (`dxos av on`) |
| Ruleset của `main` | Nhập `.github/rulesets/main.json` và `tags.json` ở Settings → Rules sau khi các check đã chạy một lần trên pull request, vì tên check phải đã xuất hiện |
| Gửi bài | Thư tới `olpvietnam@vaip.vn` và `thuky@vfossa.vn` trước 17:00 ngày 06/12/2026, tiêu đề `BÀI THI PMNM 2026 - [Tên Đội] - [Tên Trường]`, nội dung gồm thành viên đội, người hướng dẫn, đường dẫn kho mã nguồn và cam kết bản quyền theo mẫu trong đề |
| Bài trình bày chung kết | 15 phút ngày 10/12: giải pháp và lựa chọn công nghệ, tính năng nền tảng và bài toán minh họa, kết quả và demo, định hướng phát triển; kịch bản demo đã có ở `docs/DEMO.md`, chưa thấy phần trình chiếu trong kho; thêm video demo ngắn hoặc ảnh động ở `README.md` và trang docs để thu hút cộng đồng |

## 3. Giới hạn đã biết

| Việc | Ghi chú |
|---|---|
| Phần AI | Chưa có mã: hỏi đáp sinh câu trả lời và tác tử đề xuất có người duyệt cần mô hình ngôn ngữ và embedding đạt định nghĩa Open Source AI của OSI (trọng số và dữ liệu huấn luyện đều mở), chưa chọn; khi làm sẽ là dịch vụ mới nối vào lõi; ứng viên LLM là Apertus 8B (Apache-2.0) và OLMo 2, cần thử tiếng Việt và tốc độ trên CPU; reranker và embedding phải thay bằng mô hình đạt chuẩn rồi đo lại chất lượng truy xuất; mô hình mở hoàn toàn có thể trả lời tiếng Việt kém và chậm hơn trên CPU nên cần đo trước khi trình diễn; không có LLM đạt thì Qwen chỉ là tùy chọn người dùng tự gắn, ghi rõ là mở trọng số; hỏi đáp quy chế cần một kho văn bản đạt cam kết nguồn mở (CC0, CC BY, ODbL hoặc không thuộc diện bảo hộ), vì kho văn bản của trường đã gỡ do thuộc bản quyền của trường |
| Ứng dụng Tầng 2 | Chưa phủ I của H-P-D-I; hỏi BTC trên nhóm Telegram của họ xem ứng dụng minh họa có cần phủ cả bốn không gian H, P, D, I không, và màn hình quản trị đi kèm dịch vụ nguồn mở như Keycloak Admin có được coi là lõi có giao diện không |
| Giới hạn của kiểu và thao tác mới | Đổi vai của một ứng dụng ngoài bằng cách gỡ rồi đăng ký lại; hành động hàng loạt không có hoàn tác và tối đa 50 hồ sơ mỗi lần; trường nhiều lựa chọn không có giá trị mặc định; trường ngày chưa làm nguồn của điều kiện `when` và `required_when` |
| Hồ dữ liệu | Chưa có lớp truy vấn riêng trên các tệp Parquet trong kho; công cụ báo cáo đọc dữ liệu đang chạy qua vai `report_ro`; hướng làm: `dxos archive query` chạy DuckDB đọc thẳng các tệp trong kho |
| Phả hệ dữ liệu | Danh mục dữ liệu dừng ở mức thực thể; chưa có phả hệ đến từng trường |
| Thành phần React riêng trong form | Bộ dựng giao diện chưa có chỗ đăng ký thành phần |
| Ý từ Frappe | Tách phần tùy biến khỏi mô hình gốc: chưa đọc mã của Frappe để đối chiếu |
| Giáo trình DX-OS | Mới đọc tổng quan, mục lục chương và trang Hộp cát; đọc hết rồi đối chiếu thuật ngữ và kiến trúc với lõi, nhất là dự án DX-Ticket |
| Mẫu cho lĩnh vực khác | Tầng 3 trong đề trải từ bán lẻ, bệnh án đến nhà máy, còn mẫu hiện có chạm nhà máy (`plant_incident`), giáo dục, hành chính và IT; thêm một mẫu y tế và một mẫu bán lẻ (ví dụ phiếu đổi trả), mỗi mẫu là một tệp mô hình chạy qua `check_conformance.py` như các mẫu khác |
| GitOps kéo | Lõi nhận tệp qua `dxos apply` và chưa tự kéo từ git để liên tục đối chiếu với trạng thái đang chạy |
| Hai họ mẫu mở rộng | Kiểm tra định kỳ (lịch sinh bản ghi, kiểm tra, khắc phục) và Đánh giá và chọn lọc (nhiều người chấm, chọn theo chỉ tiêu) chưa làm; chỉ giữ một họ khi có ít nhất ba miền chạy được bằng tham số |
| Nâng tệp mô hình cũ | Tệp ghi `format` để lõi tự nâng tệp cũ khi định dạng đổi, nhưng mới có `format: 0` nên chưa có bước nâng nào |
| Leo thang khi quá hạn | Chưa có; quá hạn chỉ được ghi vào lịch sử và nhắc qua `due_soon`, `overdue` |
| Hướng dẫn công cụ báo cáo | Vai `report_ro` chỉ đọc các bảng nghiệp vụ, nhưng chưa có tài liệu nào nói người triển khai nối công cụ báo cáo vào đó (địa chỉ, tài khoản, mật khẩu, phạm vi bảng), kèm một truy vấn ví dụ nối bảng của hai mô hình để thấy dữ liệu không thành ốc đảo |
| Đo công sức | So số dòng và thời gian dựng ứng dụng mới trên lõi với phần Lab viết tay (5.792 dòng Java, 4.524 dòng giao diện, 8 migration) chưa làm |
| Chỉ số mục tiêu | "Người mới tự dựng xong trong một buổi" cần một người chưa biết hệ thống thử; "mã tự viết bằng 0 ở mức 1 và 2" cần đếm trên một hệ thống thật; chưa có giá của lõi tự cài |
| Tải | Chưa đo với nhiều người dùng đồng thời hay ít nhân hơn 12 |
| Khách hàng | Chưa nói chuyện với khách hàng nào; mục Khách hàng và giá trị của `README.md` là giả thuyết |
| Pháp lý | Chưa đọc văn bản gốc của Luật Bảo vệ dữ liệu cá nhân về chuyển dữ liệu ra nước ngoài; `README.md` dẫn nguồn thứ cấp |
| Điều khoản kho chữ ký của ClamAV | Chưa kiểm |

## 4. Giới hạn chấp nhận

Đã cân nhắc và quyết định giữ nguyên; chỉ mở lại khi có yêu cầu thật.

| Việc | Ghi chú |
|---|---|
| Tìm người cho trường `user` | `GET /api/v1/people` cho mọi người đã đăng nhập tìm cả người ngoài phạm vi mô hình, khớp theo tên, tên đăng nhập hoặc email, và chỉ trả id với tên hiển thị; thu hẹp theo vai của mô hình thì phải đối chiếu vai của từng người |
| Đổi mô hình khi còn hồ sơ mở | Hồ sơ chưa lưu phiên bản mô hình và hồ sơ đang chạy giữ bản BPMN cũ (Flowable không chuyển chúng sang bản mới); hạn xử lý, quyền tự quyết, hành động hoàn tất, đặt chỗ, trạng thái, hành động và điều kiện `when` vì thế chỉ đổi được khi thực thể không còn hồ sơ đang xử lý, phải hoàn tất hoặc rút hết hồ sơ trước; đổi vai xử lý thì có hiệu lực ngay vì quyền xét theo mô hình, hồ sơ đã giao cho người không còn giữ vai mới chỉ người điều phối giao lại được, và nhóm ứng viên ghi trong task Flowable đang chạy vẫn là vai cũ (không nơi nào đọc nó) |
| Ngôn ngữ giao diện | Chữ do mô hình định nghĩa (tên thực thể, nhãn trường, nhãn trạng thái và hành động tự khai, nội dung tệp mẫu) không được dịch |
| Bản sao số | Không có; số đo của thiết bị vào lõi thành hồ sơ sự cố qua API chung, lõi không giữ trạng thái thời gian thực của thiết bị |
| Cắt bớt service bằng compose profile | Mô hình không dùng thành phần nào vẫn phải chạy thành phần đó |
| Nhiều bản sao của backend | Chạy một bản; hai bản sau gateway thì danh sách mô hình và trạng thái giữ lệch nhau giữa các bản sau một lần áp dụng |
| Điều kiện `when` | Một phép so sánh trên một trường, không có và/hoặc hay biểu thức; chỉ cho hành động của người xử lý, không cho hành động của người nộp, `withdraw` hay `auto_close`; người nộp sửa được trường đó khi hồ sơ còn mở nên có thể đổi đường đi trước khi người duyệt bấm, dòng lịch sử `updated` giữ giá trị cũ và mới |
| Đổi họ mẫu của một thực thể | Đổi khóa `family` sang họ khác bị từ chối, vì trạng thái của hồ sơ cũ không ánh xạ sang họ mới; muốn đổi hình dạng quy trình thì sửa `states` và `actions` của quy trình hiện có, hoặc thêm thực thể mới |
| Đổi vai hoặc đơn vị của người đang đăng nhập | Vai và đơn vị đọc từ token đã ký nên thay đổi có hiệu lực sau tối đa khoảng 5 phút (token sống 300 giây), và người bị gỡ giữ quyền đến lúc đó |
| Chuỗi hash của lịch sử | Phát hiện chứ không ngăn sửa; điểm neo nằm trong kho đối tượng của cùng máy chủ nên người sửa được cả cơ sở dữ liệu lẫn kho thì chỉ bị lộ khi so với `hash` mà bên nhận webhook đã giữ |
| Giá trị trong lịch sử | Dòng lịch sử `updated` giữ giá trị cũ và mới của từng trường; hồ sơ không có quy trình bị xóa thì các dòng này vẫn còn, vì chuỗi hash không cho sửa dòng đã ghi, và chỉ người đọc được hồ sơ xem được chúng khi hồ sơ còn |
| Nhập dữ liệu hàng loạt | Không có nhập CSV và gói mô hình kèm dữ liệu danh mục; dữ liệu vào qua API chung để đi qua quyền, ràng buộc và quy trình |
| Tìm kiếm | Không tìm hồ sơ và nội dung tệp; chỉ mục và đoạn trích có thể lộ dữ liệu ngoài quyền, và tìm tiếng Việt cần đo riêng |
| Đăng ký thông báo | Không đăng ký theo loại sự kiện và người nhận; thông báo chat qua webhook cùng các dòng `due_soon` và `overdue` là mức cung cấp |
| Hành động hoàn tất gọi ra ngoài | Chỉ có `add_to_group` và `remove_from_group`; không có thao tác gọi tới một đích của webhook, vì webhook của mô hình đã gửi mọi dòng lịch sử kèm trạng thái cũ, trạng thái mới và hành động, nên bên nhận lọc theo trạng thái được |
| Webhook sự kiện | Dạng `chat` chưa thử với Mattermost và Slack thật; Zulip (Apache-2.0) cũng chưa kiểm phụ thuộc, nối OIDC và webhook |
| Kiểm tương thích khi đổi mô hình | Chưa có nơi đăng ký mã mở rộng nên chưa có hợp đồng để đối chiếu |
| Người thứ hai duyệt thay đổi mô hình | Không bắt buộc; là chính sách tùy bản triển khai |
| Ủy quyền có phạm vi và thời hạn | Không có; chạm nhiều đường kiểm quyền |
| Quyền theo từng trường | Không có; phải phủ mọi đường dữ liệu đi ra: danh sách, bộ lọc, số đếm, lịch sử và webhook |
| Kiểm tài liệu và OpenAPI chiều ngược | `check_docs.py` kiểm endpoint tài liệu nêu có trong OpenAPI; không kiểm chiều ngược vì `docs/API.md` chỉ liệt kê các đường dẫn chính và viết tắt đường dẫn, nên sẽ báo nhầm |
| Kiểm thử tự động chưa phủ một số ý | Chưa phủ khe hở khi áp dụng (áp dụng một mô hình đổi bảng trong lúc có yêu cầu ghi hồ sơ), vì kiểm chạy đua dễ lúc đỏ lúc xanh; ý này kiểm tay theo mục 6 của `docs/TESTING.md`; bộ hẹn giờ của `dxos archive schedule`, tab Danh mục dữ liệu, ứng dụng ngoài, kiểu và ràng buộc mới, `required_when`, `ui:`, tài liệu OpenAPI theo mô hình, giới hạn luồng thông báo, khóa chống chạy lặp, bảng theo trạng thái và xuất CSV cũng chỉ kiểm tay theo mục đó, chưa có script trong CI |
