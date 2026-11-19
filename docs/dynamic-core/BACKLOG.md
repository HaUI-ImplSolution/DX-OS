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
| 1 | Tài liệu và giao tiếp: lịch sử issue (mục 3) | Tài liệu và giao tiếp 10, phát triển bền vững 10 |
| 2 | Bằng chứng lõi là PaaS: ứng dụng mẫu (mục 3) | Nguyên gốc 10, phát triển bền vững 10 |
| 3 | Bài trình bày chung kết (mục 2) | Hoàn thiện 10, trình diễn 10 |
| 4 | Hồ sơ giấy phép của model trợ lý (mục 3) | Hoàn thiện 10, nguyên gốc 10 |

## 2. Phải xử lý trước khi phát hành

| Việc | Ghi chú |
|---|---|
| Rà soát cuối trên bản phát hành | Trước 17:00 ngày 06/12, trên commit gắn tag: clone vào máy sạch rồi `deploy/make_env.sh` và `docker compose up -d --build` thành công; CI xanh (`Platform`, `Compliance`, `Backend`, `Frontend`, `Dependencies`); trang docs mô tả đúng bản gắn tag; số issue, pull request và commit ở `website/docs/pof-audit.md` lấy lại theo ngày gắn tag |
| Kiểm lại khi có bản phát hành mới | Cài bằng `dxos-install.sh` của bản mới rồi làm theo `docs/QUICKSTART.md` (`client add --env`, `client test` và gợi ý sau `dxos start` phải có; liên kết `raw.githubusercontent.com/.../main/examples/order_approval/dxos.yaml` phải hết 404 sau khi gộp vào `main`); chạy `dxos update` từ `v2.0.1` lên bản mới ở locale `en_US.UTF-8` |
| Gửi bài | Thư tới `olpvietnam@vaip.vn` và `thuky@vfossa.vn` trước 17:00 ngày 06/12/2026, tiêu đề `BÀI THI PMNM 2026 - [Tên Đội] - [Tên Trường]`, nội dung gồm thành viên đội, người hướng dẫn, đường dẫn kho mã nguồn và cam kết bản quyền theo mẫu trong đề; trước đó xác nhận với trường rằng đội đã có trong danh sách đăng ký gửi BTC, và ghi người hướng dẫn vào mục Nhóm phát triển của `README.md` |
| Bài trình bày chung kết | 15 phút ngày 10/12: giải pháp và lựa chọn công nghệ, tính năng nền tảng và bài toán minh họa, kết quả và demo, định hướng phát triển; kịch bản demo đã có ở `docs/DEMO.md`, chưa thấy phần trình chiếu trong kho |

## 3. Giới hạn đã biết

Cột Issue ghi số issue đang mở trên GitHub; việc chưa có issue nằm ở mục 3.2.

### 3.1. Đã có issue

| Việc | Issue | Ghi chú |
|---|---|---|
| Khách hàng | #231 | Chưa nói chuyện với khách hàng nào; mục Khách hàng và giá trị của `README.md` là giả thuyết |

### 3.2. Chưa có issue

| Việc | Ghi chú |
|---|---|
| Ứng dụng mẫu gọi lõi từ bên ngoài | Chưa có ứng dụng có giao diện nào ngoài ứng dụng của đội gọi lõi qua API, nên câu "PaaS phục vụ các ứng dụng tầng trên" của đề chưa có bằng chứng chạy được; đã chốt làm một ứng dụng mẫu ở `examples/`, viết bằng khung khác react-admin, chỉ dùng OpenAPI theo mô hình và đăng nhập Keycloak |
| Egress trợ lý | Trợ lý chạy trên network mặc định của stack và chưa có allowlist kết nối cho container; cần giới hạn mạng ra ngoài endpoint inference cố định |
| Thu hồi và thay khóa/token trợ lý | Hoàn thiện quy trình thu hồi và thay mới khóa và token của trợ lý |
| Lịch sử issue và cộng đồng | Ít issue so với pull request (63 issue, 220 pull request đã gộp), nhãn `good first issue` và `help wanted` đã có nhưng chưa issue nào dùng, Discussions chỉ có một chủ đề chào mừng, repository chưa có fork, phần lớn tài liệu chỉ có tiếng Việt |
| Đóng góp tập trung | Một thành viên giữ khoảng hai phần ba commit; `CODEOWNERS` nêu ba thành viên cho mọi đường dẫn, chưa chia người phụ trách theo vùng; chia hay không là quyết định của nhóm |
| Cảnh báo Scorecard và huy hiệu | Tab Security có 8 cảnh báo của Scorecard chưa duyệt (Maintained, CodeReview, Vulnerabilities, Fuzzing, SAST, TokenPermissions hai cảnh báo, BranchProtection); huy hiệu OpenSSF Best Practices mức passing còn ba tiêu chí khuyến nghị chưa đạt (`test_most`, `warnings_strict`, `dynamic_analysis`), và lời giải thích từng tiêu chí trỏ về tệp trên `main` nên chỉ khớp khi nhánh tích hợp vào `main` |
| Bản phát hành chưa ký | Bản phát hành chỉ có SHA-256 và attestation của BuildKit, chưa có chữ ký; ưu tiên thấp |
| Bản chạy công khai | Chưa được CI kiểm hằng ngày (`deploy/online-check.sh` chạy tay hoặc theo cron), và chưa có phương án chạy tại chỗ nếu mạng ở buổi thi có sự cố vì bản công khai chạy sau Cloudflare Tunnel |
| Hồ sơ giấy phép của model trợ lý | Chưa ghi nguồn của tệp GGUF Q4, phạm vi thành phần nhúng trong binary và Web UI của llama.cpp, điều khoản của model card và bằng chứng theo định nghĩa Open Source AI của OSI; cần trước khi phân phối model |
| Cài trên Windows | Chưa có người dùng Windows thử cài và làm theo `docs/QUICKSTART.md` trong WSL2 với Docker Desktop |

## 4. Giới hạn chấp nhận

Đã cân nhắc và quyết định giữ nguyên; chỉ mở lại khi có yêu cầu thật.

| Việc | Ghi chú |
|---|---|
| Phạm vi AI | Chỉ có trợ lý đề xuất giá trị một trường có người duyệt; không có hỏi đáp RAG, tác tử theo sự kiện, embedding và reranker; đề không nêu số thành phần của nhóm I phải làm, và ứng dụng minh họa không cần có AI |
| Chất lượng đề xuất trợ lý | Model có thể đoán khi thiếu dữ kiện, bỏ sót đối chiếu, từ chối nhầm và diễn đạt sai quan hệ số; chưa có kiểm ngữ nghĩa tự chặn các lỗi này nên người dùng đối chiếu nguồn rồi mới xác nhận; nhiệm vụ phụ thuộc giá trị hiện tại của chính trường đối chiếu chưa được hỗ trợ; vòng đời trợ lý (bật, tắt, cập nhật) chưa chạy trong CI |
| Quy mô vận hành trợ lý | Một provider và một slot suy luận, không chạy nhiều replica trên cùng SQLite; chưa có SLA hoặc số đo model cold/warm, RAM/VRAM/swap; cần đo trên host và model đã chọn trước khi cam kết tải |
| Tìm người cho trường `user` | `GET /api/v1/people` cho mọi người đã đăng nhập tìm cả người ngoài phạm vi mô hình, khớp theo tên, tên đăng nhập hoặc email, và chỉ trả id với tên hiển thị; thu hẹp theo vai của mô hình thì phải đối chiếu vai của từng người |
| Đổi mô hình khi còn hồ sơ mở | Hồ sơ chưa lưu phiên bản mô hình và hồ sơ đang chạy giữ bản BPMN cũ (Flowable không chuyển chúng sang bản mới); hạn xử lý, quyền tự quyết, hành động hoàn tất, đặt chỗ, trạng thái, hành động và điều kiện `when` vì thế chỉ đổi được khi thực thể không còn hồ sơ đang xử lý, phải hoàn tất hoặc rút hết hồ sơ trước; đổi vai xử lý thì có hiệu lực ngay vì quyền xét theo mô hình, hồ sơ đã giao cho người không còn giữ vai mới chỉ người điều phối giao lại được, và nhóm ứng viên ghi trong task Flowable đang chạy vẫn là vai cũ (không nơi nào đọc nó) |
| Ngôn ngữ giao diện | Chữ do mô hình định nghĩa (tên thực thể, nhãn trường, nhãn trạng thái và hành động tự khai, nội dung tệp mẫu) không được dịch |
| Cụm Tầng 1 ngoài bốn dịch vụ bắt buộc | Không có chữ ký số và PKI (lõi không giữ khóa; là thành phần người triển khai gắn ở biên, cùng loại với két bí mật), đa thuê giữa các tổ chức (cách ly hiện theo mô hình, mỗi mô hình một schema và một client; theo tổ chức là đổi cả realm Keycloak và quyền theo dòng), số liệu đóng băng theo kỳ (`dxos archive` chỉ ghi hồ sơ đã đóng ra Parquet kèm hash, không chụp dữ liệu đang mở theo kỳ), lớp ngữ nghĩa dựng sẵn (công cụ báo cáo tự dựng trên vai `report_ro`) và trục streaming riêng (bản cài là Compose một máy, kênh sự kiện và hàng đợi gửi đi đã đủ cho hộp việc và webhook); chỉ mở lại nếu Ban tổ chức xác nhận bắt buộc đủ bảy cụm |
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
| Kiểm tương thích khi đổi mô hình | Chưa có nơi đăng ký mã mở rộng nên chưa có hợp đồng để đối chiếu |
| Người thứ hai duyệt thay đổi mô hình | Không bắt buộc; là chính sách tùy bản triển khai |
| Ủy quyền có phạm vi và thời hạn | Không có; chạm nhiều đường kiểm quyền |
| Thời gian viết tay làm mốc | Không đo; phần Lab viết tay không có ghi nhận giờ, nên so sánh công sức dùng số dòng (`examples/effort-benchmark/README.md`) chứ không dùng thời gian |
| Quyền theo từng trường | Không có; phải phủ mọi đường dữ liệu đi ra: danh sách, bộ lọc, số đếm, lịch sử và webhook |
| Tham chiếu sang mô hình khác | Trường `reference` chỉ trỏ tới thực thể cùng mô hình; trỏ sang mô hình khác thì mô hình không còn áp dụng và quay lui độc lập, và quyền của hai mô hình thuộc hai client khác nhau |
| Kiểm tài liệu và OpenAPI chiều ngược | `check_docs.py` kiểm endpoint tài liệu nêu có trong OpenAPI; không kiểm chiều ngược vì `docs/API.md` chỉ liệt kê các đường dẫn chính và viết tắt đường dẫn, nên sẽ báo nhầm |
| CLI và bộ cài riêng cho Windows | Không viết `dxos` bằng PowerShell hay bộ cài `.ps1`: `dxos` là Bash dài và hai bản sẽ lệch nhau; người dùng Windows chạy bản Linux trong WSL2 với Docker Desktop, và `/console` trong trình duyệt đã đưa mô hình (`plan`, `apply`) và đăng ký client mà không cần dòng lệnh |
| Gói phát hành riêng cho Windows | Release giữ `dxos-install.sh` và `dx-os-X.Y.Z.tar.gz`, không thêm tài sản riêng cho Windows hay gói công cụ tích hợp: mẫu mô hình có ở `/console` và `docs/MODEL.md`, hợp đồng API có ở OpenAPI của mô hình đang chạy |
| Mẫu cấu hình và gói ví dụ cho ứng dụng ngoài | Không phát hành tệp `.env` mẫu cho ứng dụng ngoài (dễ nhầm với `.env` của nền tảng, và tên biến là quy ước của từng ứng dụng) và không phát hành gói ví dụ client riêng: `docs/EXTENSIONS.md` nêu giá trị cần và `examples/itot-simulator` là ví dụ chạy được |
| Cảnh báo bảo mật của thư viện bắc cầu | Năm cảnh báo Dependabot còn mở: `react-router` có bản vá ở nhánh 7 mà `react-admin` 5 chạy trên nhánh 6, `decode-uri-component` có bản vá chỉ ở dạng ESM mà `query-string` 7 của `react-admin` nạp bằng `require`, `uuid` nằm trong công cụ dựng của site tài liệu; không ghi đè phiên bản để khỏi làm hỏng giao diện, và Dependabot đề xuất khi `react-admin` hoặc Docusaurus nâng |
| Kiểm thử tự động chưa phủ một số ý | Chưa phủ khe hở khi áp dụng (áp dụng một mô hình đổi bảng trong lúc có yêu cầu ghi hồ sơ), vì kiểm chạy đua dễ lúc đỏ lúc xanh; ý này kiểm tay theo mục 6 của `docs/TESTING.md`; bộ hẹn giờ của `dxos archive schedule`, tab Danh mục dữ liệu, ứng dụng ngoài loại máy (`dxos client`), kiểu và ràng buộc mới, `required_when`, `ui:`, tài liệu OpenAPI theo mô hình, giới hạn luồng thông báo, khóa chống chạy lặp, bảng theo trạng thái, xuất CSV, kế hoạch đổi quyền truy cập, hạn theo lịch làm việc và mục Báo cáo theo trường của trang Thống kê cũng chỉ kiểm tay theo mục đó, chưa có script trong CI |
| Thông lượng ghi lịch sử | Chuỗi hash nối mọi dòng theo thứ tự id trên toàn hệ thống nên các lần ghi lịch sử lần lượt qua một khóa tư vấn của PostgreSQL (`HistoryChain.lock()`), giữ đến hết giao dịch; thông lượng của API chung đứng dưới khoảng 1.000 yêu cầu mỗi giây dù CPU còn dư (`docs/SIZING.md`), ở 50 đến 100 người dùng đồng thời vẫn đạt p95 dưới 500 ms; chưa kiểm chứng riêng rằng khóa này là nguyên nhân; chuỗi theo từng hồ sơ hoặc nối hash bất đồng bộ bỏ được khóa nhưng đổi hợp đồng của chuỗi hash và của `hash` trong thông điệp webhook |
| Đích webhook riêng cho từng mức leo thang | Thông báo leo thang đi qua đích webhook của mô hình, nên một mức chưa chọn được kênh khác; chưa lĩnh vực nào cần, và điều kiện thêm năng lực của `README.md` đòi ít nhất ba lĩnh vực |
| Ngày làm bù và lịch theo đơn vị | Lịch chỉ khai ngày làm trong tuần và ngày nghỉ: không có ngày làm bù (thứ Bảy đi làm) và mỗi quy trình dùng một lịch của mô hình, không theo đơn vị của hồ sơ; chưa có lĩnh vực nào cần, và mỗi thứ thêm một đường tính hạn phải giữ đúng |
| Thông báo theo giờ làm | `due_soon` và `escalate` đếm bằng giờ thường từ `handle_by` (hạn đã đúng giờ làm), nên một thông báo leo thang có thể rơi vào ngày nghỉ; đếm bằng giờ làm phải bỏ cách quét theo tập bằng SQL để tính từng dòng trong Java và đổi cách ghi lịch sử |
| Hợp đồng sự kiện theo chuẩn mở | Thông điệp webhook có `eventId`, chữ ký và `hash`, và được tả trong `docs/INTEGRATIONS.md`; AsyncAPI và `format: cloudevents` thêm một định dạng phải giữ tương thích mà không phủ tiêu chí nào của đề chưa phủ |
| Trang và thành phần riêng trong ứng dụng Tầng 2 | Ứng dụng web chưa có chỗ đăng ký trang hay thành phần form; thêm chúng là sửa mã nguồn và dựng lại image; đề chỉ đòi một ứng dụng Tầng 2, và ứng dụng mẫu ngoài đã cho thấy lõi phục vụ ứng dụng khác |
| Hai họ mẫu mở rộng | Kiểm tra định kỳ (lịch sinh bản ghi, kiểm tra, khắc phục) và Đánh giá và chọn lọc (nhiều người chấm, chọn theo chỉ tiêu) chưa làm; chỉ giữ một họ khi có ít nhất ba miền chạy được bằng tham số; ba họ hiện có đã đạt động cơ luồng công việc của đề |
| Trợ lý cá nhân qua Telegram | Dịch vụ mở rộng tích hợp OpenClaw cần một mô hình ngôn ngữ như phần AI, mà phần đó chưa có mô hình đạt định nghĩa Open Source AI của OSI; chỉ mở lại khi phần AI có mô hình đạt |
| Cô lập tải theo mô hình | Các mô hình dùng chung một pool kết nối, và `UserRateLimitFilter` giới hạn theo người dùng và đường dẫn chứ không theo mô hình; chưa có số đo cho thấy mô hình này làm chậm mô hình kia (`docs/SIZING.md` không đo chéo mô hình) nên chưa thêm giới hạn đồng thời cho báo cáo hay xuất PDF |
| Phục hồi PostgreSQL theo thời điểm | `deploy/backup.sh` dùng `pg_dumpall` cùng restic và giữ bản sao cơ sở dữ liệu khớp với tệp nhờ `DXOS_FILE_DELETE_DELAY`; lưu WAL (pgBackRest) phá bảo đảm đó và đổi image PostgreSQL dùng chung với Keycloak; `docs/TARGETS.md` và `docs/OPERATIONS.md` không có chỉ tiêu RPO nên chưa có lý do; mở lại khi người triển khai nêu RPO nhỏ hơn chu kỳ sao lưu |
| Truy vết phân tán | `X-Request-Id` và số đo Prometheus đủ để tìm bước chậm của một yêu cầu; OpenTelemetry và Jaeger thêm tác nhân Java và một dịch vụ chẩn đoán; mở lại khi có ca chậm mà hai thứ trên không giải thích được |
| Mức của kế hoạch mở rộng quyền | Kế hoạch ghi vai nào được cấp thêm hoặc mở rộng (`GRANT_ACCESS`, `WIDEN_ACCESS`) nhưng vẫn ở mức `SAFE`, nên `dxos gitops` ở chế độ `auto` và `ModelBootstrap` áp dụng nó mà không đòi `--accept`; nâng lên `MIGRATION` làm cả hai bỏ qua mọi kế hoạch có mở rộng quyền để chờ duyệt tay, kể cả mẫu mới ở lần khởi động đầu; người duyệt đã thấy vai và mức cũ mới trong kế hoạch, và chỉ nâng khi có yêu cầu thật |
