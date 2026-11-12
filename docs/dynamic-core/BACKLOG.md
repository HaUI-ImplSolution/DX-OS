# Việc tồn đọng của lõi dynamic

Nguồn duy nhất cho việc chưa làm, chưa kiểm thử và giới hạn đã biết của lõi dynamic.
Hướng đi nằm ở [`README.md`](./README.md).
Đối chiếu với đề thi và đề xuất mở rộng nằm ở [`DANH_GIA.md`](./DANH_GIA.md).
Làm xong một mục thì xóa dòng đó, không ghi lại diễn biến.

## 1. Thứ tự đến hạn nộp

Hạn gửi bài là 17:00 ngày 06/12/2026.
Kho mã được chấm từ 07 đến 09/12 trên những gì thấy được ở trang repository, không có đội giải thích; sản phẩm được chấm ở buổi trình diễn 10/12.
Thứ tự dưới đây xếp theo số điểm đang để hở; chi tiết từng việc ở các mục sau.

| Thứ tự | Việc | Điểm liên quan |
|---|---|---|
| 1 | Rà soát cuối của mục 2 trên bản `2.0.0` đã phát hành | Điều kiện để kho mã được chấm trên bản dự thi; bản phát hành 5, dịch từ mã nguồn 10 |
| 2 | Tài liệu và giao tiếp: lịch sử issue, tài liệu sản phẩm trên trang docs (mục 2 và 3) | Tài liệu và giao tiếp 10, phát triển bền vững 10 |
| 3 | Bằng chứng lõi là PaaS: ứng dụng mẫu (mục 3) | Nguyên gốc 10, phát triển bền vững 10 |
| 4 | Bài trình bày, video (mục 2) | Hoàn thiện 10, trình diễn 10 |
| 5 | Phần AI và chữ I của ứng dụng Tầng 2 (mục 3) | Hoàn thiện 10, nguyên gốc 10 |
| 6 | Phủ thêm hai nguyên lý: truy vấn trên hồ dữ liệu, kiểm EICAR trong CI (mục 3 và 5) | Hoàn thiện 10 |

## 2. Phải xử lý trước khi phát hành

| Việc | Ghi chú |
|---|---|
| Ảnh và video của sản phẩm | `README.md`, `README_EN.md` và trang docs không có ảnh hay video nào của sản phẩm, và kho không có tệp ảnh nào ngoài logo của trang docs; thêm ảnh bảng điều khiển, màn hình kế hoạch, hộp việc và lịch, cùng một video hoặc ảnh động ngắn |
| Rà soát cuối trên bản phát hành | Trước 17:00 ngày 06/12: clone vào máy sạch rồi `deploy/make_env.sh` và `docker compose up -d --build` thành công; mở repository bằng trình duyệt ẩn danh thấy được không cần đăng nhập; trang Contributors hiện đủ ba thành viên (API `contributors` hiện chỉ trả hai người); `check_licenses.py` báo 0 vấn đề, `reuse lint` xanh và `gh api repos/HaUI-ImplSolution/DX-OS/license` vẫn trả Apache-2.0 khi có `LICENSES/`; CI xanh trên commit gắn tag (`Platform`, `Compliance`, `Backend`, `Frontend`, `Dependencies`); trang docs mô tả đúng bản `2.0.0`; số issue, pull request và commit ở `website/docs/pof-audit.md` lấy lại theo ngày gắn tag |
| Gửi bài | Thư tới `olpvietnam@vaip.vn` và `thuky@vfossa.vn` trước 17:00 ngày 06/12/2026, tiêu đề `BÀI THI PMNM 2026 - [Tên Đội] - [Tên Trường]`, nội dung gồm thành viên đội, người hướng dẫn, đường dẫn kho mã nguồn và cam kết bản quyền theo mẫu trong đề; trước đó xác nhận với trường rằng đội đã có trong danh sách đăng ký gửi BTC, và ghi người hướng dẫn vào mục Nhóm phát triển của `README.md` |
| Bài trình bày chung kết | 15 phút ngày 10/12: giải pháp và lựa chọn công nghệ, tính năng nền tảng và bài toán minh họa, kết quả và demo, định hướng phát triển; kịch bản demo đã có ở `docs/DEMO.md`, chưa thấy phần trình chiếu trong kho |

## 3. Giới hạn đã biết

| Việc | Ghi chú |
|---|---|
| Phần AI | Chưa có mã: hỏi đáp sinh câu trả lời và tác tử đề xuất có người duyệt cần mô hình ngôn ngữ và embedding đạt định nghĩa Open Source AI của OSI (trọng số và dữ liệu huấn luyện đều mở), chưa chọn; khi làm sẽ là dịch vụ mới nối vào lõi; ứng viên LLM là Apertus 8B (Apache-2.0) và OLMo 2, cần thử tiếng Việt và tốc độ trên CPU; reranker và embedding phải thay bằng mô hình đạt chuẩn rồi đo lại chất lượng truy xuất; mô hình mở hoàn toàn có thể trả lời tiếng Việt kém và chậm hơn trên CPU nên cần đo trước khi trình diễn; không có LLM đạt thì Qwen chỉ là tùy chọn người dùng tự gắn, ghi rõ là mở trọng số; hỏi đáp quy chế cần một kho văn bản đạt cam kết nguồn mở (CC0, CC BY, ODbL hoặc không thuộc diện bảo hộ), vì kho văn bản của trường đã gỡ do thuộc bản quyền của trường |
| Ứng dụng Tầng 2 | Chưa phủ I của H-P-D-I; hỏi BTC trên nhóm Telegram của họ xem ứng dụng minh họa có cần phủ cả bốn không gian H, P, D, I không |
| Hồ dữ liệu | Chưa có lớp truy vấn riêng trên các tệp Parquet trong kho; công cụ báo cáo đọc dữ liệu đang chạy qua vai `report_ro`; hướng làm: `dxos archive query` chạy DuckDB đọc thẳng các tệp trong kho |
| Phả hệ dữ liệu | Danh mục dữ liệu dừng ở mức thực thể; chưa có phả hệ đến từng trường |
| Tài liệu sản phẩm trên trang docs | Trang docs chỉ có hồ sơ dự thi; cách viết tệp mô hình, API, vận hành và mở rộng đều là liên kết sang tệp trên GitHub |
| Giao diện trên điện thoại | Chưa kiểm ứng dụng web trên màn hình điện thoại, trong khi không gian H của đề có ứng dụng di động tác nghiệp |
| Nâng format với thay đổi cấu trúc | Migration hiện tại chỉ đổi marker `format: 0` thành `format: 1`; khi format tiếp theo thay đổi tên trường, kiểu dữ liệu hoặc vị trí thuộc tính, cần thêm migration chuyển đổi thật và kiểm thử bảo toàn ngữ nghĩa |
| Kiểm tra gián đoạn khi nâng tệp | Bộ test mới chỉ kiểm tra pipeline dừng khi một migration ném ngoại lệ; chưa có failure injection lúc ghi tệp tạm, gián đoạn tiến trình hoặc kiểm phục hồi từ bản sao lưu |
| Bảo toàn metadata khi thay tệp nâng format | CLI thay tệp bằng rename nguyên tử cùng thư mục và chỉ sao chép mode; chưa xác định hoặc kiểm thử chính sách cho owner/group, symlink và độ bền sau sự cố hệ thống |
| Trang và thành phần riêng trong ứng dụng Tầng 2 | Ứng dụng web chưa có chỗ đăng ký trang hay thành phần form; thêm chúng là sửa mã nguồn và dựng lại image |
| Ý từ Frappe | Tách phần tùy biến khỏi mô hình gốc: chưa đọc mã của Frappe để đối chiếu |
| Giáo trình DX-OS | Mới đọc tổng quan, mục lục chương và trang Hộp cát; đọc hết rồi đối chiếu thuật ngữ và kiến trúc với lõi, nhất là dự án DX-Ticket |
| Kiểm tra đồng bộ do dịch vụ riêng quyết định | Webhook chỉ báo sau khi hồ sơ đã chuyển; chưa có cách để dịch vụ riêng chặn một hành động trước khi chuyển trạng thái, nên logic đặc thù ở mức 3 chỉ phản ứng sau, không chặn được; nếu làm thì phải giữ quy tắc bất biến và hồ sơ không kẹt khi dịch vụ riêng không trả lời |
| Đích webhook riêng cho từng mức leo thang | Thông báo leo thang đi qua đích webhook của mô hình, nên một mức chưa chọn được kênh khác, ví dụ kênh riêng cho vai cấp cao; nếu làm thì thêm khóa `webhook` vào mức, kiểm tên đích khi áp dụng mô hình và đưa tên đích vào tin |
| Ứng dụng mẫu gọi lõi từ bên ngoài | Chưa có ứng dụng có giao diện nào ngoài ứng dụng của đội gọi lõi qua API, nên câu "PaaS phục vụ các ứng dụng tầng trên" của đề chưa có bằng chứng chạy được; đã chốt làm một ứng dụng mẫu ở `examples/`, viết bằng khung khác react-admin, chỉ dùng OpenAPI theo mô hình và đăng nhập Keycloak |
| Lịch sử issue | PoF chấm bug tracker có lịch sử; số issue và pull request lấy ở `website/docs/pof-audit.md`; mở issue cho việc phải sửa hoặc làm lại và đóng bằng pull request có `Closes #n`, còn việc phải thêm (giấy phép, lộ trình, ảnh) chỉ ghi ở file này; issue chỉ mang nhãn `enhancement` hoặc `documentation`, chưa gắn milestone |
| Hai họ mẫu mở rộng | Kiểm tra định kỳ (lịch sinh bản ghi, kiểm tra, khắc phục) và Đánh giá và chọn lọc (nhiều người chấm, chọn theo chỉ tiêu) chưa làm; chỉ giữ một họ khi có ít nhất ba miền chạy được bằng tham số |
| Nâng AWS SDK lên 2.57.8 | Pull request #239 của Dependabot chưa merge được vì `sbom/backend.cdx.json` còn ghi 2.57.6 và job `Scan the backend SBOM` đỏ; cần đổi `awssdk.version` và sinh lại SBOM theo `sbom/README.md` trong cùng một commit rồi đóng pull request |
| Đo công sức | So số dòng và thời gian dựng ứng dụng mới trên lõi với phần Lab viết tay (5.792 dòng Java, 4.524 dòng giao diện, 8 migration) chưa làm |
| Chỉ số mục tiêu | "Người mới tự dựng xong trong một buổi" cần một người chưa biết hệ thống thử; "mã tự viết bằng 0 ở mức 1 và 2" cần đếm trên một hệ thống thật; chưa có giá của lõi tự cài |
| Tải | Chưa đo với nhiều người dùng đồng thời hay ít nhân hơn 12 |
| Khách hàng | Chưa nói chuyện với khách hàng nào; mục Khách hàng và giá trị của `README.md` là giả thuyết |

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
| Hành động hàng loạt | Web gọi lại hành động của một hồ sơ cho từng hồ sơ đã chọn, tối đa 50 hồ sơ mỗi lần để nằm trong giới hạn tốc độ của cổng API; mỗi hồ sơ tự đi qua quyền và luật quy trình của nó, hồ sơ bị từ chối được báo kèm lý do còn các hồ sơ khác vẫn chạy; không có hoàn tác vì hành động đơn lẻ cũng không có, chuyển trạng thái để lại dòng lịch sử trong chuỗi hash, và hồ sơ xử lý nhầm thì xử lý tiếp theo đường quy trình (mở lại hoặc trả lại nếu quy trình có); không có đường lô riêng ở backend |
| Nhập dữ liệu hàng loạt | Không có nhập CSV và gói mô hình kèm dữ liệu danh mục; dữ liệu vào qua API chung để đi qua quyền, ràng buộc và quy trình |
| Tìm kiếm | Không tìm hồ sơ và nội dung tệp; chỉ mục và đoạn trích có thể lộ dữ liệu ngoài quyền, và tìm tiếng Việt cần đo riêng |
| Đăng ký thông báo | Không đăng ký theo loại sự kiện và người nhận; thông báo chat qua webhook cùng các dòng `due_soon`, `overdue` và `escalated` là mức cung cấp |
| Hành động hoàn tất gọi ra ngoài | Chỉ có `add_to_group` và `remove_from_group`; không có thao tác gọi tới một đích của webhook, vì webhook của mô hình đã gửi mọi dòng lịch sử kèm trạng thái cũ, trạng thái mới và hành động, nên bên nhận lọc theo trạng thái được |
| Webhook sự kiện | Dạng `chat` chưa thử với Mattermost và Slack thật; Zulip (Apache-2.0) cũng chưa kiểm phụ thuộc, nối OIDC và webhook |
| Kiểm tương thích khi đổi mô hình | Chưa có nơi đăng ký mã mở rộng nên chưa có hợp đồng để đối chiếu |
| Người thứ hai duyệt thay đổi mô hình | Không bắt buộc; là chính sách tùy bản triển khai |
| Ủy quyền có phạm vi và thời hạn | Không có; chạm nhiều đường kiểm quyền |
| Quyền theo từng trường | Không có; phải phủ mọi đường dữ liệu đi ra: danh sách, bộ lọc, số đếm, lịch sử và webhook |
| Tham chiếu sang mô hình khác | Trường `reference` chỉ trỏ tới thực thể cùng mô hình; trỏ sang mô hình khác thì mô hình không còn áp dụng và quay lui độc lập, và quyền của hai mô hình thuộc hai client khác nhau |
| Kiểm tài liệu và OpenAPI chiều ngược | `check_docs.py` kiểm endpoint tài liệu nêu có trong OpenAPI; không kiểm chiều ngược vì `docs/API.md` chỉ liệt kê các đường dẫn chính và viết tắt đường dẫn, nên sẽ báo nhầm |
| Kiểm thử tự động chưa phủ một số ý | Chưa phủ khe hở khi áp dụng (áp dụng một mô hình đổi bảng trong lúc có yêu cầu ghi hồ sơ), vì kiểm chạy đua dễ lúc đỏ lúc xanh; ý này kiểm tay theo mục 6 của `docs/TESTING.md`; bộ hẹn giờ của `dxos archive schedule`, tab Danh mục dữ liệu, ứng dụng ngoài loại máy (`dxos client`), kiểu và ràng buộc mới, `required_when`, `ui:`, tài liệu OpenAPI theo mô hình, giới hạn luồng thông báo, khóa chống chạy lặp, bảng theo trạng thái và xuất CSV cũng chỉ kiểm tay theo mục đó, chưa có script trong CI |

## 5. Lệch hướng so với đề thi

Chỗ bài làm hoặc cách trình bày chưa khớp câu chữ của đề.

| Chỗ lệch | Ghi chú |
|---|---|
| Kiểm dịch mã độc | Nguyên lý (iv) của đề viết "kiểm dịch mã độc nghiêm ngặt"; quét tắt mặc định, và kiểm tệp EICAR không nằm trong CI vì job `API end-to-end` không bật quét |
