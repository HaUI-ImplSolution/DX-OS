# Đánh giá theo đề thi và đề xuất mở rộng lõi

Đối chiếu lõi dynamic với đề thi, thể lệ, chuẩn của một repository nguồn mở và tài liệu dự thi công khai của đội Signet, rồi nêu các hướng mở rộng lõi.
Việc còn thiếu và chỗ lệch hướng nằm ở [`BACKLOG.md`](./BACKLOG.md); hướng đi của lõi ở [`README.md`](./README.md).
Số liệu kiểm ngày 14/11/2026 trên nhánh `Khuong/main` và API công khai của GitHub.
Mỗi mục ghi một trong bốn mức: Đạt, Đạt có điều kiện, Rủi ro, Chưa kiểm; tài liệu này không ghi số điểm, vì việc chấm là của Ban giám khảo.
Bản đánh giá nêu cả điểm yếu và những chỗ mình chưa kiểm được.

Nguồn: [đề thi](https://vfossa.vn/tin-tuc/de-thi-phan-mem-nguon-mo-olp-2026-798.html), [thể lệ và thang điểm](https://vfossa.vn/tin-tuc/the-le-olp-phan-mem-nguon-mo-nam-2026-760.html), [tài liệu dự thi của đội Signet](https://github.com/HIT-Champion/Signet/tree/develop/docs).

## 1. Yêu cầu bắt buộc của đề

| Yêu cầu | Hiện có | Mức |
|---|---|---|
| Lõi Tầng 1 chạy ngầm, không giao diện, là PaaS | Dịch vụ lõi chỉ phục vụ API; ứng dụng ngoài gọi bằng token máy và đọc OpenAPI sinh theo mô hình | Đạt: không có Swagger UI, `/admin` mặc định đóng, `dxos start --core` chạy riêng Tầng 1, và một nhóm kiểm API của CI chạy ở chế độ này |
| Định danh, SSO | Keycloak; vai, đơn vị và client sinh từ mô hình | Đạt |
| Cổng API | APISIX kiểm token và giới hạn tốc độ, dịch vụ lõi kiểm lại token | Đạt |
| Dữ liệu có cấu trúc | PostgreSQL; lược đồ, ràng buộc và API có quyền theo dòng sinh từ mô hình | Đạt |
| Dữ liệu phi cấu trúc | SeaweedFS, tệp đính kèm theo quyền của hồ sơ, ClamAV | Đạt; quét mã độc bật mặc định, tệp EICAR bị chặn trong CI |
| Động cơ luồng công việc | Flowable nhúng, BPMN sinh từ mô hình, ba họ mẫu | Đạt |
| Ít nhất một ứng dụng Tầng 2 trong H-P-D-I | Ứng dụng web dựng từ metadata và bảng điều khiển, chạm H, P và D | Đạt theo cách đọc của đội ("ít nhất một ứng dụng"); chữ I chưa có, và chưa hỏi Ban tổ chức xem có bắt buộc phủ cả bốn không gian (#211); `README.md` có mục đối chiếu với đề |
| Giấy phép OSI, mã công khai, có bản phát hành trước khi nộp | Apache-2.0; bảy bản `v1.0.0` đến `v2.0.0` | Đạt có điều kiện: bản phát hành mới nhất (`v2.0.0`, 11/11) không chứa 40 commit sau đó, và nhánh mặc định `main` chưa có chúng (mục 4) |

Các giao diện đi kèm được tính như sau.
Giao diện của DX-OS là giao diện web, bảng điều khiển, trang liên kết (portal) và màn hình giám sát, đều ở Tầng 2; lõi chạy riêng không kèm giao diện nào.
Keycloak Admin Console là giao diện của dịch vụ nguồn mở đi kèm chứ không phải của DX-OS, và mặc định không mở ra cổng vào.
Đây là cách đọc của đội, chưa có xác nhận của Ban tổ chức.

## 2. Bốn nguyên lý kỹ thuật

| Nguyên lý | Hiện có | Mức |
|---|---|---|
| (i) Tách theo vòng đời dữ liệu, đẩy lịch sử xuống hồ dữ liệu | Hồ sơ đã đóng tự ghi ra Parquet trong kho đối tượng theo lịch, kèm manifest và hash; `dxos archive query` truy vấn chỉ đọc từng lần chạy bằng DuckDB | Đủ trong phạm vi kho hồ sơ |
| (ii) Hội tụ IT và OT, bản sao số | Số đo của thiết bị giả lập thành hồ sơ sự cố, đi cùng quy trình và quyền với hồ sơ hành chính | Minh họa: không có bản sao số |
| (iii) Dòng chảy, phả hệ, chất lượng dữ liệu | Mô hình là danh mục dữ liệu, tab Danh mục dữ liệu kèm phả hệ từng trường, lịch sử nối chuỗi hash | Đáp ứng: phả hệ đến từng trường, không có bước biến đổi vì lõi giữ nguyên giá trị |
| (iv) Zero-Trust, GitOps, kiểm dịch mã độc, tự phục hồi | Token kiểm ở cổng và ở lõi; mô hình áp dụng theo kế hoạch, phần lệch được sửa khi áp dụng lại; container bị xóa được chạy lại; hạ tầng và mô hình kéo từ git bằng `dxos gitops` | Đáp ứng trong phạm vi một máy: quét mã độc bật mặc định, và nhóm chế độ lõi của CI kiểm tệp EICAR bị chặn; GitOps và tự phục hồi chạy trên Compose, không có Kubernetes |

## 3. Bảy cụm dịch vụ của Tầng 1

Chỉ bốn dịch vụ ở mục 1 là bắt buộc.
Phần còn lại của bảy cụm là tầm nhìn của đề, phủ thêm là điểm cộng ở tính nguyên gốc và mức hoàn thiện.

| Cụm | Có | Không có |
|---|---|---|
| Định danh, bảo mật | SSO, MFA của Keycloak, quét tệp | Két bí mật, chữ ký số và PKI |
| Đa thuê, biên mạng | Cổng API kiểm token; cách ly theo đơn vị và theo mô hình (mỗi mô hình một schema và một client); giao diện tiếng Việt và tiếng Anh | Che dữ liệu cá nhân, đa thuê giữa các tổ chức, dịch chữ của mô hình |
| Dữ liệu có cấu trúc | Giao dịch toàn vẹn, API sinh từ mô hình có quyền theo dòng, lịch sử giá trị từng trường, phiên bản mô hình | Số liệu đóng băng theo kỳ, lớp ngữ nghĩa cho báo cáo |
| Dữ liệu phi cấu trúc | Lưu trữ đối tượng, tệp gắn với hồ sơ phê duyệt | Tìm toàn văn, trộn dữ liệu sinh biểu mẫu |
| Quy trình, tự động hóa | BPMN sinh từ mô hình, hành động hoàn tất | Kịch bản serverless, nạp dữ liệu theo lô |
| Vận hành, kiểm toán | Truy vết theo mã yêu cầu, nhật ký, chuỗi hash, sao lưu và khôi phục, GitOps cho hạ tầng và nghiệp vụ | Trục streaming riêng |
| Truyền thông | Hàng đợi gửi đi có thứ tự và chữ ký, webhook chat, kênh sự kiện cho hộp việc | Thông báo đa kênh tới từng người |

## 4. Điểm kho mã (50 điểm)

| Tiêu chí | Điểm | Đã có | Mức | Còn thiếu |
|---|---|---|---|---|
| Quản lý mã nguồn trên Internet | 5 | GitHub công khai; 998 commit trên `Khuong/main`; 201 pull request đã gộp; 20 kiểm tra của CI đều xanh trên pull request gần nhất | Đạt | Đóng góp tập trung: trong 757 commit của người thật, một thành viên có 534 (khoảng 70%), hai người còn lại 116 và 106 |
| Giấy phép OSI | 10 | Apache-2.0 cho mã, tài liệu, ảnh, video và sơ đồ; OFL-1.1 cho hai tệp phông; `LICENSE` toàn văn; `NOTICE` có mục "Mục đích của giấy phép"; dòng SPDX ở tệp mã; `reuse lint` đạt 564 trên 564 tệp; CI kiểm giấy phép của mọi gói, của `REUSE.toml` và của `LICENSES/`; chỉ hai gói dữ liệu chỉ đọc lúc dựng trang docs nằm ngoài danh sách OSI, nêu tên kèm lý do ở mục 7 của `DEPENDENCIES.txt` | Đạt | Không có |
| Bản phát hành | 5 | Bảy bản theo SemVer, mỗi bản có `.tar.gz`, `.sha256` và SBOM, không có `.zip` do đội đăng | Rủi ro | Nhánh `main` mà ban giám khảo mở chậm 40 commit (160 tệp) so với `Khuong/main`, và `v2.0.0` không chứa phần đó; cần một pull request vào `main` và một bản phát hành mới trước 17:00 ngày 06/12; bản phát hành chỉ có SHA-256, chưa ký |
| Dịch từ mã nguồn | 10 | Cấu hình ở `.env` trước khi dựng, Docker, Maven wrapper và npm, cài ra ngoài thư mục mã nguồn, CI cài từ gói phát hành và chạy trình duyệt trên stack rỗng; công cụ cần có trên máy chạy đã khai giấy phép; ngày 14/11 mình clone `Khuong/main` vào thư mục trống và chạy đúng các lệnh của README (`make_env.sh`, `docker compose up -d --build`, `dxos admin`): 9 dịch vụ `healthy`, 5 địa chỉ trả 200, 122 giây | Đạt | Lần chạy dùng bộ nhớ đệm của Docker nên chưa đo trên máy hoàn toàn mới; mới chạy trên `Khuong/main`, chưa chạy trên `main` |
| Thư viện và gói đính kèm | 10 | `DEPENDENCIES.txt` có tên, phiên bản, giấy phép, vai trò, trang chủ và nguồn; SBOM khớp tệp khóa; image ghim digest; không chép và không vá mã của dự án khác | Đạt | Không có |
| Tài liệu và giao tiếp | 10 | README hai thứ tiếng, CHANGELOG, mẫu issue, CONTRIBUTING, SECURITY, quy tắc ứng xử, trang docs, `ROADMAP.md`, Discussions; trang Contributors hiện đủ ba thành viên (GitHub đếm 689, 144 và 124 lần đóng góp, kể cả commit gộp) | Đạt có điều kiện | Lịch sử issue mỏng so với việc đã làm: 55 issue (36 đã đóng) so với 201 pull request, và khoảng 20 issue đã xong chờ pull request vào `main` đóng; 30 sao, 0 fork, một chủ đề ở Discussions; không issue nào gắn nhãn `good first issue` hay `help wanted`; phần lớn tài liệu chỉ có tiếng Việt |

Các việc ở cột cuối nằm ở `BACKLOG.md`, mục 2 và 3.
Thư gửi bài (thành viên, người hướng dẫn, đường dẫn kho, cam kết bản quyền) không nằm trong bảng chấm và do đội gửi; đề không đòi báo cáo kỹ thuật, slide hay video.

## 5. Điểm sản phẩm (50 điểm)

Phần này do Ban giám khảo chấm ở buổi chung kết; bảng ghi điểm tựa và điểm yếu, không ghi mức.

| Tiêu chí | Điểm tựa | Điểm yếu |
|---|---|---|
| Tính nguyên gốc | Đổi mô hình an toàn trên hệ thống đang chạy: kế hoạch, mức rủi ro, dòng vi phạm, quay lui; quy tắc bất biến nằm trong engine; lịch sử nối chuỗi hash | Đề không hỏi trực tiếp, nên phải giải thích được giá trị trong vài phút; các đội cùng dùng Keycloak, APISIX, PostgreSQL, Flowable nên phần riêng chỉ là vòng đời mô hình; chưa nói chuyện với khách hàng nào (#231) |
| Mức hoàn thiện | Mười mẫu mô hình trên ba họ quy trình; 20 kiểm tra của CI trên stack thật, kể cả trình duyệt; bộ kiểm chung cho mô hình mới | Chưa có phần AI và chữ I của H-P-D-I; pull request kế hoạch (#296) và pull request trợ lý nhiều người dùng (#199) còn mở, chưa gộp |
| Thân thiện | Cài một lệnh, bảng điều khiển, thư viện mẫu, tài khoản và hồ sơ thử, mã hồ sơ đọc được, giao diện điện thoại, tiếng Việt và tiếng Anh | Giao diện sinh từ metadata dễ trông chung chung; chữ do mô hình định nghĩa không được dịch (`BACKLOG.md`, mục 4) |
| Phát triển bền vững | Tài liệu kiến trúc, tệp mô hình, API và vận hành; mục ứng phó sự cố; lệnh `dxos`; bộ kiểm chung cho mô hình; SBOM | Chưa có ứng dụng mẫu của bên ngoài gọi lõi (`BACKLOG.md`, mục 3.2); đóng góp tập trung vào một người |
| Trình diễn, thu hút cộng đồng | Kịch bản 15 phút ở `docs/DEMO.md`, bản chạy công khai, hai video và ảnh động kiến trúc | Chưa có phần trình chiếu; bản công khai chạy sau Cloudflare Tunnel, một dịch vụ đóng, nên cần phương án chạy tại chỗ nếu mạng ở buổi thi có sự cố |

## 6. Nhận xét từ tài liệu của đội Signet

Đội Signet (HIT-Champion) công bố phân tích đề, bảng tự chấm kho mã, hỏi đáp với hội đồng và khảo sát kho mã các đội.
Số liệu của họ ghi theo ngày họ tra.

| Điều tài liệu ghi | Ý nghĩa với đội |
|---|---|
| Phần lớn các đội chọn một ngành cụ thể, và cùng dùng Keycloak, APISIX, PostgreSQL, Flowable | Tính nguyên gốc không đến từ việc chọn thành phần; lõi trung lập với lĩnh vực là điểm khác |
| Kho của đội được mô tả là "DX-Farm và DX-Lab, một lõi nhiều ứng dụng" | Mô tả này là của bản cũ; nhánh `main` nay là lõi dynamic, nên người ngoài đọc đúng bản hiện hành |
| Giám khảo vòng trường của họ nhận xét thêm một ứng dụng vẫn phải viết nhiều mã; họ đáp bằng bảng tạo không cần mã, mẫu in và báo cáo khai báo bằng dữ liệu | Hướng "khai báo là có" đang được đội khác đi theo; phần còn riêng của đội là vòng đời mô hình và quy tắc bất biến của quy trình |
| Họ phủ rộng bảy cụm: két bí mật, tường lửa ứng dụng, che dữ liệu cá nhân, tìm toàn văn, NGSI-LD, kịch bản tự động | Đội đi sâu thay vì rộng; bài trình bày cần nói rõ đây là lựa chọn |
| Kho mã của họ: mọi tệp đạt chuẩn REUSE, 209 issue, bảy bản phát hành, Discussions bật, bản chạy công khai được CI kiểm hằng ngày | Đội nay cũng đạt REUSE cho cả 564 tệp, có bảy bản phát hành và một bản chạy công khai; còn kém ở số issue và mức hoạt động của Discussions, và bản công khai chưa được CI kiểm hằng ngày (có `deploy/online-check.sh` để chạy tay hoặc theo cron) |
| Họ đọc Tầng 2 là cổng dịch vụ dùng chung, còn ứng dụng ngành thuộc Tầng 3 | Cùng kết luận với mục "Đối chiếu với đề thi" của `README.md` |
| Bảng tham chiếu của đề có năm thành phần không đạt OSI: Vault, n8n, Redpanda, MongoDB, immudb | Đội không dùng thành phần nào trong số này; nên nêu thành một quyết định thiết kế khi trình bày |
| Câu hội đồng dễ hỏi: lõi không giao diện mà sao có trang quản trị Keycloak | Đã trả lời ở mục 1: `/admin` mặc định đóng và `dxos start --core` chạy riêng Tầng 1 |

## 7. Đề xuất mở rộng lõi

Mỗi đề xuất đã soát theo điều kiện thêm năng lực của `README.md`: cần ở ít nhất ba lĩnh vực, khai báo được, không làm hỏng mô hình đã có.
Các ý đã chốt không làm ở `BACKLOG.md`, mục 4 không được nêu lại.
Cột Trạng thái ghi quyết định của đội: đề xuất đã chốt làm có dòng việc ở `BACKLOG.md`, đề xuất chờ chốt kèm khuyến nghị của lần rà soát.

### 7.1. Lõi là dịch vụ cho ứng dụng khác

| Đề xuất | Lý do | Làm gì | Lợi ích | Rủi ro | Trạng thái |
|---|---|---|---|---|---|
| **Đường vào cho giao diện tự viết** | Public client PKCE, allowlist CORS theo origin và tệp route riêng của bản cài đã có; chưa có ứng dụng giao diện mẫu độc lập với web của đội | Làm một ứng dụng mẫu ở `examples/` bằng khung khác react-admin, chỉ dùng OpenAPI theo mô hình và Keycloak | Hoàn thiện bằng chứng rằng ứng dụng Tầng 2 độc lập dùng lõi mà không sửa source hoặc tệp triển khai do lõi quản lý | Ứng dụng mẫu là mã phải giữ chạy được qua các bản, và phụ thuộc của nó phải qua kiểm giấy phép | Chốt làm |
| **Chế độ chỉ lõi** | Đề viết lõi không có giao diện; hiện không khởi động được riêng Tầng 1 | `dxos start --core` chỉ chạy cổng API, Keycloak, dịch vụ lõi, PostgreSQL và kho tệp; một job CI chạy bộ kiểm API trên chế độ này và khẳng định không dịch vụ nào trả trang HTML | Chứng minh headless bằng một lệnh; ứng dụng ngoài dùng lõi mà không kéo theo giao diện của đội | Thêm một tổ hợp phải kiểm trong CI; cần phân biệt với dòng "Cắt bớt service bằng compose profile" đã chốt, vì ở đây bỏ Tầng 2 chứ không bỏ thành phần của lõi | Đã làm |
| **Kiểm tra trước khi chuyển trạng thái** | Logic đặc thù ở mức 3 chỉ phản ứng sau qua webhook, không chặn được một hành động; đây là chỗ đầu tiên một tổ chức có luật riêng sẽ vấp | Một hành động khai `check: <tên đích>`; sau khi mọi quy tắc của quy trình đã đạt và trong giao dịch giữ khóa hồ sơ, lõi gửi hồ sơ và hành động có chữ ký tới đích, chờ tối đa 3 giây, nhận cho phép hoặc từ chối kèm mã và lý do; dịch vụ không trả lời thì `on_error` của đích quyết định (`deny` mặc định hoặc `allow`), và `bypass` cho vận hành; từ chối, không trả lời hoặc bỏ qua đều có dòng trong lịch sử | Luật đặc thù chặn được mà không có mã trong tiến trình lõi; quy tắc bất biến vẫn do engine giữ; hồ sơ không kẹt vì từ chối nghĩa là ở nguyên trạng thái | Hành động phụ thuộc độ sẵn sàng và độ trễ của dịch vụ ngoài, và giữ khóa hồ sơ tối đa 3 giây mỗi lần hỏi; hành động hàng loạt nhân số lần gọi | Đã làm |
| **Hợp đồng sự kiện theo chuẩn mở** | API dữ liệu có OpenAPI sinh theo mô hình, còn thông điệp webhook chỉ được tả bằng lời | Sinh tài liệu AsyncAPI theo mô hình cạnh OpenAPI; đích webhook thêm lựa chọn `format: cloudevents` bọc thông điệp theo CloudEvents 1.0 | Bên nhận sinh mã và kiểm thông điệp bằng công cụ sẵn có; đúng cam kết dùng chuẩn mở | Thêm một định dạng phải giữ tương thích; chữ ký và hash phải nói rõ tính trên dạng nào | Chốt không làm; ghi ở `BACKLOG.md`, mục 4 |
| **Số đo theo định dạng Prometheus** | Giám sát hiện là màn hình riêng của đội; người triển khai đã có Prometheus và Grafana không nối được | Dịch vụ lõi mở `/actuator/prometheus`: hồ sơ theo trạng thái và quá hạn của từng thực thể, tuổi và số sự kiện chưa chuyển trong hàng đợi, số lần áp dụng; số đo tính từ cơ sở dữ liệu mỗi 30 giây, nhãn chỉ gồm tên khai báo trong mô hình, endpoint chỉ trong mạng Docker và đòi mật khẩu | Đúng tư tưởng provider: lõi đưa số, công cụ ngoài vẽ; khớp lớp giám sát trong bảng tham chiếu của đề | Số chuỗi tăng theo số mô hình và trạng thái, được chặn ở một mức tối đa; thêm một thư viện đã qua kiểm giấy phép | Đã làm |

### 7.2. Khai báo được nhiều hơn

| Đề xuất | Lý do | Làm gì | Lợi ích | Rủi ro | Trạng thái |
|---|---|---|---|---|---|
| **Lớp tùy biến trên mẫu** | Người triển khai chọn một mẫu rồi sửa thẳng vào bản sao, nên khi mẫu gốc có bản mới họ phải tự gộp tay; đây cũng là ý tách phần tùy biến khỏi mô hình gốc học từ Frappe | Tệp mô hình khai `extends: <mẫu>` và chỉ viết phần khác; quy tắc ghi đè dùng lại quy tắc đang có giữa họ mẫu và quy trình khai báo (ghi đè từng khóa, `null` để bỏ); kế hoạch tính trên tệp đã gộp, và một lệnh in tệp đã gộp | Tệp của tổ chức ngắn và chỉ chứa quyết định của họ; nâng mẫu đi qua đúng đường xem kế hoạch đang có | Khó biết một trường đến từ đâu nếu thiếu lệnh in tệp gộp; mẫu đổi tên trường thì phần tùy biến phải theo; cần ghim phiên bản mẫu để áp dụng lại cho ra cùng kết quả | Chốt làm |
| **Mã hồ sơ đọc được** | Hồ sơ chỉ có id là số tăng dần; ticket, phiếu đặt chỗ và hồ sơ hành chính đều cần một mã để đọc, in và tra | Kiểu trường `code` với khuôn như `HS-{year}-{seq:5}`; lõi điền một lần lúc tạo hồ sơ, không ghi hay sửa được qua API, có ràng buộc duy nhất; bộ đếm khóa theo hàng trong giao dịch tạo hồ sơ, nên không trùng khi ghi đồng thời và hồ sơ lỗi không làm mất số; thêm vào thực thể đã có hồ sơ thì khi áp dụng, hồ sơ cũ nhận mã | Thân thiện hơn ở danh sách, thông báo và bản in; là khóa tự nhiên cho ứng dụng ngoài | Hết số thì hồ sơ mới bị từ chối cho đến khi mở rộng khuôn; mỗi thực thể một trường mã; đổi khuôn chỉ áp cho hồ sơ mới | Đã làm |
| **Truy vấn gộp có phân quyền** | Thống kê cố định theo trạng thái và đơn vị; muốn số khác thì công cụ báo cáo đọc thẳng cơ sở dữ liệu bằng vai `report_ro`, đi vòng qua quyền theo dòng; đề nêu lớp ngữ nghĩa cho báo cáo | API gộp nhận trường nhóm (lựa chọn, đơn vị, trạng thái, ngày theo tháng) và phép tính (đếm, tổng, trung bình trên trường số), áp cùng bộ lọc và quyền theo dòng của danh sách; trang Thống kê của ứng dụng web có mục Báo cáo theo trường | Ứng dụng ngoài và bảng số liệu lấy số mà không cần tài khoản cơ sở dữ liệu; chữ D của H-P-D-I có nội dung do mô hình khai báo | Truy vấn nặng trên bảng lớn, nên giới hạn số nhóm và dùng lại mức cắt 10 giây đang có; số gộp có thể lộ số lượng ngoài quyền nếu đặt lọc quyền sai chỗ | Đã làm |
| **Mẫu in khai báo** | Đề nêu "trộn dữ liệu tự động sinh biểu mẫu"; quy trình phê duyệt ở lĩnh vực nào cũng kết thúc bằng một tờ giấy: giấy xác nhận, phiếu đặt phòng, biên bản sự cố | Thực thể khai `documents:` với tiêu đề, đoạn văn, bảng trường và khối chữ ký có chỗ trống theo tên trường; lõi trộn một hồ sơ vào mẫu và trả PDF tiếng Việt qua API theo quyền đọc của hồ sơ, và mỗi lần in được ghi vào nhật ký kiểm toán | Phủ thêm một năng lực đề nêu tên mà người triển khai chỉ khai báo, không viết mã | Mẫu chỉ có chỗ trống, không điều kiện hay vòng lặp; phông Noto Sans (OFL-1.1) nằm trong kho kèm nguồn và SHA-256, và PDFBox đã qua kiểm giấy phép OSI trước khi thêm | Đã làm |

### 7.3. GitOps trọn vòng và công cụ cho người viết mô hình

| Đề xuất | Lý do | Làm gì | Lợi ích | Rủi ro | Trạng thái |
|---|---|---|---|---|---|
| **Kiểm tệp không cần hệ thống** | Kiểm một tệp mô hình cần cả hệ thống đang chạy; bộ kiểm chung cho mô hình mới nằm ở `deploy/dev` như script nội bộ | `dxos lint <tệp>` chạy bộ đọc của backend không cần hệ thống, từ JAR đi kèm bản phát hành hoặc từ image backend; `dxos conformance <mô hình>` chạy các kiểm rào chắn của lõi trên hệ thống đang chạy | Lỗi tệp bị bắt ở máy người viết và ở pull request; là công cụ hỗ trợ mà tiêu chí phát triển bền vững chấm | Lint không thấy lỗi phụ thuộc dữ liệu đang có nên không thay cho kế hoạch; conformance tạo client và người dùng thử, nên từ chối stack ngoài `localhost` nếu thiếu cờ | Đã làm |

### 7.4. Kho mã và cộng đồng

| Đề xuất | Lý do | Làm gì | Lợi ích | Rủi ro | Trạng thái |
|---|---|---|---|---|---|
| **Chuẩn REUSE** | Thể lệ chấm giấy phép trong từng tệp; kiểm hiện tại bỏ sót loại tệp và không nói gì về tệp không chèn được chú thích | Thêm `REUSE.toml` và thư mục `LICENSES/`, khai giấy phép và bản quyền cho JSON, ảnh và tệp của bên thứ ba; chạy `reuse lint` trong job `Check licenses` | Một chuẩn có công cụ kiểm thay cho script tự viết; mọi tệp đều có giấy phép máy đọc được | REUSE đòi cả dòng bản quyền, tệp hiện chỉ có dòng giấy phép; phải kiểm GitHub vẫn nhận một giấy phép sau khi có `LICENSES/` | Đã làm |
| **Trang docs là tài liệu sản phẩm** | Trang docs là hồ sơ dự thi; người mới không có đường đọc từ cài đặt tới mở rộng | Đưa tệp mô hình, API, cài đặt và vận hành, giám sát, tích hợp, báo cáo, mở rộng, kiến trúc, tương thích và kịch bản trình diễn lên trang; một script chép chúng từ `docs/` và `deploy/` mỗi lần dựng trang | Điểm tài liệu và phát triển bền vững cùng dựa vào đây | Hai nơi chứa một nội dung dễ lệch nhau, nên trang docs dựng từ chính các tệp nguồn và không giữ bản riêng | Đã làm |

## 8. Đã cân nhắc, không đề xuất

| Ý | Lý do |
|---|---|
| Kubernetes và Argo CD | Có trong bảng tham chiếu của đề, nhưng bản cài là Compose trên một máy; thêm cụm chỉ để có GitOps kéo là đổi cả cách cài |
| GraphQL | API chung đã có lọc, sắp xếp, phân trang và OpenAPI theo mô hình; thêm một kiểu API là hai bề mặt cùng phải giữ quyền theo dòng |
| Két bí mật, tường lửa ứng dụng, che dữ liệu cá nhân | Thuộc bảy cụm của đề nhưng không phục vụ quy trình trên bản ghi; là thành phần người triển khai gắn ở biên |
| Kịch bản serverless trong lõi | Trái nguyên tắc không chạy mã tùy ý trong tiến trình lõi; mở rộng đi qua webhook và kiểm tra trước khi chuyển trạng thái |
| Trình kéo thả BPMN | `README.md` đã chốt người dùng không viết BPMN |
| Tham chiếu sang mô hình khác | Hợp ý chống ốc đảo dữ liệu của đề, nhưng mô hình không còn áp dụng và quay lui độc lập, và quyền của hai mô hình thuộc hai client khác nhau; đã chốt không làm, ghi ở `BACKLOG.md`, mục 4 |

## 9. Đối chiếu với chuẩn repository nguồn mở

Đối chiếu với hồ sơ cộng đồng của GitHub, tiêu chí của huy hiệu OpenSSF Best Practices và Scorecard, và tệp có ở bảy dự án khác (Keycloak, APISIX, Supabase, Directus, Frappe, Strapi, Signet).
Huy hiệu OpenSSF Best Practices mức passing đã được cấp; công cụ Scorecard chưa được chạy.
GitHub chấm hồ sơ cộng đồng của repository là 87 trên 100, trong khi Signet được 100.
Phần thiếu không phải mẫu issue (cả hai repository đều bị API ghi `issue_template: false`) mà là cài đặt "Reported content", Signet bật còn DX-OS chưa; đó là cài đặt của chủ repository.

| Hạng mục | DX-OS | Các dự án khác | Mức |
|---|---|---|---|
| README, LICENSE, quy tắc ứng xử, hướng dẫn đóng góp, mẫu pull request | Có | Phần lớn có | Đạt |
| Mẫu issue | Có `.github/ISSUE_TEMPLATE` (hai mẫu và `config.yml`); API hồ sơ cộng đồng ghi `false` cho cả DX-OS lẫn Signet nên không phải điểm trừ riêng | Phần lớn có | Đạt |
| `SECURITY.md`, quy trình báo lỗ hổng | Có, kèm mốc phản hồi và quy tắc thử trên bản trực tuyến; private vulnerability reporting đang bật (API trả `enabled: true`) | Một nửa có | Đạt |
| `CHANGELOG`, `ROADMAP`, `NOTICE` | Có cả ba | Rất ít dự án có đủ | Đạt |
| Giấy phép đọc được bằng máy | `reuse lint` đạt 564 trên 564 tệp; SBOM kèm mỗi bản phát hành | Ít dự án có | Đạt |
| Cập nhật và quét phụ thuộc | Dependabot, Trivy, gitleaks; tab Security ngày 14/11 có 6 cảnh báo mở (1 cao, 4 vừa, 1 thấp), đều ở thư viện npm gián tiếp (`decode-uri-component`, `react-router` trong web; `uuid`, `deepmerge` trong trang docs) và đều mở từ 6 đến 11/11, chưa cái nào quá 60 ngày; quét độc lập bằng Trivy 0.58.1 (cùng image với CI) trên các tệp khóa thấy 0 lỗ hổng cao hoặc nghiêm trọng, và các lỗ hổng mức vừa bị giới hạn bởi phiên bản mà `react-admin` và Docusaurus cho phép | Phần lớn có công cụ cập nhật | Đạt có điều kiện: cần nâng các thư viện này khi bản vá tương thích ra |
| Bảo vệ nhánh và tag | Hai ruleset đang `active`: `main` (bắt buộc pull request, 16 kiểm tra phải xanh, một đối tượng được bỏ qua) và tag `v*` bất biến | Không đo được từ ngoài | Đạt |
| `.editorconfig` | Thêm ngày 14/11 | 6 trên 7 dự án có | Đạt |
| `CODEOWNERS` | Chưa có; cần tên tài khoản GitHub của người phụ trách từng vùng | 4 trên 7 có | Thiếu |
| Phân tích tĩnh (SAST) | SpotBugs với find-sec-bugs chạy ở bước `verify` của backend và làm hỏng build khi có phát hiện chưa được giải thích; ngày 14/11 đã duyệt 67 phát hiện còn lại sau khi lọc cảnh báo lộ tham chiếu của record và DTO: 37 về SQL (tên bảng và cột đều qua `Sql.ident`, giá trị qua tham số `?`), 7 về giá trị có thể null, 5 về đa luồng và 18 khác, không phát hiện chỗ nào khai thác được; mỗi nhóm có lý do ở `backend/spotbugs-exclude.xml` | 2 trên 7 có CodeQL | Đạt: công cụ chạy ở mọi lần build, mọi phát hiện đã duyệt và build chặn phát hiện mới |
| Bản phát hành có chữ ký | Chỉ có SHA-256; chưa ký | Không có trong khảo sát | Thiếu, ưu tiên thấp |
| `GOVERNANCE.md`, `MAINTAINERS` | Chưa có | Chỉ Keycloak có | Chưa cần với nhóm ba người |
| Huy hiệu OpenSSF Best Practices, Scorecard | Huy hiệu mức passing đã cấp ngày 14/11 ([hồ sơ](https://www.bestpractices.dev/projects/16682)): 57 tiêu chí Met, 7 N/A và 3 Unmet ở mức khuyến nghị (`test_most`, `warnings_strict`, `dynamic_analysis`); lời giải thích từng tiêu chí trỏ về tệp của repository trên `main`, nên hồ sơ chỉ khớp sau khi nhánh tích hợp vào `main`; Scorecard đã tải và kiểm checksum nhưng hệ thống chặn việc chạy tệp nhị phân đó, nên chưa có kết quả | Không có dự án nào trong khảo sát chạy Scorecard | Đạt cho huy hiệu; Scorecard chưa kiểm |

Về phân tích tĩnh, CodeQL không hợp nguyên tắc công cụ phải có giấy phép OSI của dự án vì điều khoản của công cụ dòng lệnh không thuộc OSI.
SpotBugs hoặc Error Prone cho Java và ESLint cho web đều là phần mềm nguồn mở.

## 10. Kết luận

Điểm vững: lõi đáp ứng đủ các thành phần bắt buộc của đề và được kiểm tự động trên stack thật; giấy phép của dự án và của mọi thành phần dùng đều thuộc danh sách OSI, trừ hai gói dữ liệu có ngoại lệ ghi rõ; kho mã có nhiều tệp chuẩn hơn phần lớn dự án trong khảo sát.

Điểm yếu, theo mức nặng giảm dần:

1. **Nhánh `main` và bản phát hành đang không phản ánh sản phẩm.**
   Ban giám khảo mở `main` và `v2.0.0`; cả hai thiếu 40 commit gần nhất.
   Đây là việc duy nhất có thể mất điểm nặng ở hai tiêu chí cùng lúc, và đội chủ động xử lý được.
2. **Chưa có phần AI.** Chữ I của H-P-D-I chưa có và chưa hỏi Ban tổ chức; ảnh hưởng đến mức hoàn thiện và tính nguyên gốc ở buổi chung kết, không ảnh hưởng điểm kho mã.
3. **Lịch sử issue mỏng và đóng góp tập trung vào một người.** Không mất điểm trực tiếp theo thể lệ, nhưng làm yếu bằng chứng về dự án có cộng đồng.
4. **Chưa kiểm được:** kết quả OpenSSF Scorecard, và việc dựng trên máy hoàn toàn mới không có bộ nhớ đệm của Docker.
5. **Thiếu so với chuẩn repository:** chưa có `CODEOWNERS`; ở mọi vùng của repository một người viết từ 62 đến 100% commit, nên việc chia người phụ trách là quyết định của nhóm.

Các việc còn lại và thứ tự làm nằm ở `BACKLOG.md`.
