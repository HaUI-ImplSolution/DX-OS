# Đánh giá theo đề thi và đề xuất mở rộng lõi

Đối chiếu lõi dynamic với đề thi, thể lệ và tài liệu dự thi công khai của đội Signet, rồi nêu các hướng mở rộng lõi.
Việc còn thiếu và chỗ lệch hướng nằm ở [`BACKLOG.md`](./BACKLOG.md); hướng đi của lõi ở [`README.md`](./README.md).
Số liệu kiểm trên nhánh `Khuong/main` và API công khai của GitHub.

Nguồn: [đề thi](https://vfossa.vn/tin-tuc/de-thi-phan-mem-nguon-mo-olp-2026-798.html), [thể lệ và thang điểm](https://vfossa.vn/tin-tuc/the-le-olp-phan-mem-nguon-mo-nam-2026-760.html), [tài liệu dự thi của đội Signet](https://github.com/HIT-Champion/Signet/tree/develop/docs).

## 1. Yêu cầu bắt buộc của đề

| Yêu cầu | Hiện có | Mức |
|---|---|---|
| Lõi Tầng 1 chạy ngầm, không giao diện, là PaaS | Dịch vụ lõi chỉ phục vụ API; ứng dụng ngoài gọi bằng token máy và đọc OpenAPI sinh theo mô hình | Đạt: không có Swagger UI, `/admin` mặc định đóng, và `dxos start --core` chạy riêng Tầng 1 |
| Định danh, SSO | Keycloak; vai, đơn vị và client sinh từ mô hình | Đạt |
| Cổng API | APISIX kiểm token và giới hạn tốc độ, dịch vụ lõi kiểm lại token | Đạt |
| Dữ liệu có cấu trúc | PostgreSQL; lược đồ, ràng buộc và API có quyền theo dòng sinh từ mô hình | Đạt |
| Dữ liệu phi cấu trúc | SeaweedFS, tệp đính kèm theo quyền của hồ sơ, ClamAV | Đạt; quét mã độc bật mặc định, tệp EICAR bị chặn trong CI |
| Động cơ luồng công việc | Flowable nhúng, BPMN sinh từ mô hình, ba họ mẫu | Đạt |
| Ít nhất một ứng dụng Tầng 2 trong H-P-D-I | Ứng dụng web dựng từ metadata và bảng điều khiển, chạm H, P và D | Đạt; chưa có I; `README.md` có mục đối chiếu với đề |
| Giấy phép OSI, mã công khai, có bản phát hành trước khi nộp | Apache-2.0; bảy bản `v1.0.0` đến `v2.0.0` | Đạt |

## 2. Bốn nguyên lý kỹ thuật

| Nguyên lý | Hiện có | Mức |
|---|---|---|
| (i) Tách theo vòng đời dữ liệu, đẩy lịch sử xuống hồ dữ liệu | Hồ sơ đã đóng tự ghi ra Parquet trong kho đối tượng theo lịch, kèm manifest và hash; `dxos archive query` truy vấn chỉ đọc từng lần chạy bằng DuckDB | Đủ trong phạm vi kho hồ sơ |
| (ii) Hội tụ IT và OT, bản sao số | Số đo của thiết bị giả lập thành hồ sơ sự cố, đi cùng quy trình và quyền với hồ sơ hành chính | Minh họa: không có bản sao số |
| (iii) Dòng chảy, phả hệ, chất lượng dữ liệu | Mô hình là danh mục dữ liệu, tab Danh mục dữ liệu kèm phả hệ từng trường, lịch sử nối chuỗi hash | Đáp ứng: phả hệ đến từng trường, không có bước biến đổi vì lõi giữ nguyên giá trị |
| (iv) Zero-Trust, GitOps, kiểm dịch mã độc, tự phục hồi | Token kiểm ở cổng và ở lõi; mô hình áp dụng theo kế hoạch, phần lệch được sửa khi áp dụng lại; container bị xóa được chạy lại; hạ tầng và mô hình kéo từ git bằng `dxos gitops` | Một phần: quét tắt mặc định |

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

| Tiêu chí | Điểm | Đã có | Còn thiếu để chắc điểm |
|---|---|---|---|
| Quản lý mã nguồn trên Internet | 5 | GitHub công khai, hơn 950 commit của ba thành viên, CI trên mọi pull request | Đạt |
| Giấy phép OSI | 10 | Apache-2.0, `LICENSE` toàn văn, `NOTICE`, dòng SPDX ở tệp Java, TypeScript, JavaScript, Python, shell và YAML, `REUSE.toml` và `LICENSES/` cho tệp không chèn được chú thích, `reuse lint` và kiểm giấy phép của mọi gói trong CI, `README.md` có mục đối chiếu với đề | Đạt |
| Bản phát hành | 5 | Bảy bản theo SemVer, mỗi bản có `.tar.gz`, `.sha256` và SBOM | Đạt |
| Dịch từ mã nguồn | 10 | Cấu hình ở `.env` trước khi dựng, dựng bằng Docker, Maven và npm, cài ra ngoài thư mục mã nguồn, CI cài từ gói phát hành | Chạy lại trên máy sạch với image phát hành thật |
| Thư viện và gói đính kèm | 10 | `DEPENDENCIES.txt`, SBOM khớp tệp khóa, image ghim digest, không chép và không vá mã của dự án khác | Đạt |
| Tài liệu và giao tiếp | 10 | README hai thứ tiếng, CHANGELOG, mẫu issue, CONTRIBUTING, SECURITY, trang docs, `ROADMAP.md`, Discussions | 44 issue (34 đã đóng) so với 186 pull request đã gộp; chưa có issue nào gắn nhãn `good first issue` |

Các việc ở cột cuối nằm ở `BACKLOG.md`, mục 2 và 3.

## 5. Điểm sản phẩm (50 điểm)

| Tiêu chí | Điểm tựa | Điểm yếu |
|---|---|---|
| Tính nguyên gốc | Đổi mô hình an toàn trên hệ thống đang chạy: kế hoạch, mức rủi ro, dòng vi phạm, quay lui; quy tắc bất biến nằm trong engine; lịch sử nối chuỗi hash | Đề không hỏi trực tiếp, nên phải giải thích được giá trị trong vài phút |
| Mức hoàn thiện | Mười mẫu mô hình trên ba họ quy trình; kiểm tự động trên stack thật trong CI | Chưa có phần AI |
| Thân thiện | Cài một lệnh, bảng điều khiển, thư viện mẫu, tài khoản và hồ sơ thử | Giao diện sinh từ metadata dễ trông chung chung; hồ sơ chỉ có id là số |
| Phát triển bền vững | Tài liệu kiến trúc, tệp mô hình, API và vận hành; lệnh `dxos`; bộ kiểm chung cho mô hình; SBOM | Chưa có ứng dụng mẫu của bên ngoài |
| Trình diễn, thu hút cộng đồng | Kịch bản 15 phút, README tiếng Anh | Chưa có video và phần trình chiếu |

## 6. Nhận xét từ tài liệu của đội Signet

Đội Signet (HIT-Champion) công bố phân tích đề, bảng tự chấm kho mã, hỏi đáp với hội đồng và khảo sát kho mã các đội.
Số liệu của họ ghi theo ngày họ tra.

| Điều tài liệu ghi | Ý nghĩa với đội |
|---|---|
| Phần lớn các đội chọn một ngành cụ thể, và cùng dùng Keycloak, APISIX, PostgreSQL, Flowable | Tính nguyên gốc không đến từ việc chọn thành phần; lõi trung lập với lĩnh vực là điểm khác |
| Kho của đội được mô tả là "DX-Farm và DX-Lab, một lõi nhiều ứng dụng" | Mô tả này là của bản cũ; nhánh `main` nay là lõi dynamic, nên người ngoài đọc đúng bản hiện hành |
| Giám khảo vòng trường của họ nhận xét thêm một ứng dụng vẫn phải viết nhiều mã; họ đáp bằng bảng tạo không cần mã, mẫu in và báo cáo khai báo bằng dữ liệu | Hướng "khai báo là có" đang được đội khác đi theo; phần còn riêng của đội là vòng đời mô hình và quy tắc bất biến của quy trình |
| Họ phủ rộng bảy cụm: két bí mật, tường lửa ứng dụng, che dữ liệu cá nhân, tìm toàn văn, NGSI-LD, kịch bản tự động | Đội đi sâu thay vì rộng; bài trình bày cần nói rõ đây là lựa chọn |
| Kho mã của họ: mọi tệp đạt chuẩn REUSE, 209 issue, bảy bản phát hành, Discussions bật, bản chạy công khai được CI kiểm hằng ngày | Đội kém ở số issue, kênh trao đổi, phạm vi dòng giấy phép và bản công khai |
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
| **Số đo theo định dạng Prometheus** | Giám sát hiện là màn hình riêng của đội; người triển khai đã có Prometheus và Grafana không nối được | Dịch vụ lõi mở endpoint số đo: hồ sơ theo trạng thái và quá hạn của từng mô hình, độ trễ hàng đợi sự kiện, số lần áp dụng; chỉ nhận từ mạng nội bộ hoặc token quản trị | Đúng tư tưởng provider: lõi đưa số, công cụ ngoài vẽ; khớp lớp giám sát trong bảng tham chiếu của đề | Nhãn theo mô hình và thực thể làm số chuỗi tăng theo số mô hình; thêm một thư viện phải qua kiểm giấy phép | Chốt làm |

### 7.2. Khai báo được nhiều hơn

| Đề xuất | Lý do | Làm gì | Lợi ích | Rủi ro | Trạng thái |
|---|---|---|---|---|---|
| **Lớp tùy biến trên mẫu** | Người triển khai chọn một mẫu rồi sửa thẳng vào bản sao, nên khi mẫu gốc có bản mới họ phải tự gộp tay; đây cũng là ý tách phần tùy biến khỏi mô hình gốc học từ Frappe | Tệp mô hình khai `extends: <mẫu>` và chỉ viết phần khác; quy tắc ghi đè dùng lại quy tắc đang có giữa họ mẫu và quy trình khai báo (ghi đè từng khóa, `null` để bỏ); kế hoạch tính trên tệp đã gộp, và một lệnh in tệp đã gộp | Tệp của tổ chức ngắn và chỉ chứa quyết định của họ; nâng mẫu đi qua đúng đường xem kế hoạch đang có | Khó biết một trường đến từ đâu nếu thiếu lệnh in tệp gộp; mẫu đổi tên trường thì phần tùy biến phải theo; cần ghim phiên bản mẫu để áp dụng lại cho ra cùng kết quả | Chốt làm |
| **Mã hồ sơ đọc được** | Hồ sơ chỉ có id là số tăng dần; ticket, phiếu đặt chỗ và hồ sơ hành chính đều cần một mã để đọc, in và tra | Một kiểu trường sinh mã theo khuôn khai báo, gồm tiền tố, năm và số thứ tự; lõi điền lúc tạo, không sửa được, có ràng buộc duy nhất | Thân thiện hơn ở danh sách, thông báo và bản in; là khóa tự nhiên cho ứng dụng ngoài | Số thứ tự có khoảng trống khi một lần tạo bị hủy; đổi khuôn khi đã có dữ liệu cần luật riêng trong kế hoạch | Chốt làm |
| **Truy vấn gộp có phân quyền** | Thống kê cố định theo trạng thái và đơn vị; muốn số khác thì công cụ báo cáo đọc thẳng cơ sở dữ liệu bằng vai `report_ro`, đi vòng qua quyền theo dòng; đề nêu lớp ngữ nghĩa cho báo cáo | API gộp nhận trường nhóm (lựa chọn, đơn vị, trạng thái, ngày theo tháng) và phép tính (đếm, tổng, trung bình trên trường số), áp cùng bộ lọc và quyền theo dòng của danh sách; thực thể khai `reports:` để ứng dụng web vẽ sẵn | Ứng dụng ngoài và bảng số liệu lấy số mà không cần tài khoản cơ sở dữ liệu; chữ D của H-P-D-I có nội dung do mô hình khai báo | Truy vấn nặng trên bảng lớn, nên giới hạn số nhóm và dùng lại mức cắt 10 giây đang có; số gộp có thể lộ số lượng ngoài quyền nếu đặt lọc quyền sai chỗ | Chốt làm |

### 7.3. Kho mã và cộng đồng

| Đề xuất | Lý do | Làm gì | Lợi ích | Rủi ro | Trạng thái |
|---|---|---|---|---|---|
| **Chuẩn REUSE** | Thể lệ chấm giấy phép trong từng tệp; kiểm hiện tại bỏ sót loại tệp và không nói gì về tệp không chèn được chú thích | Thêm `REUSE.toml` và thư mục `LICENSES/`, khai giấy phép và bản quyền cho JSON, ảnh và tệp của bên thứ ba; chạy `reuse lint` trong job `Check licenses` | Một chuẩn có công cụ kiểm thay cho script tự viết; mọi tệp đều có giấy phép máy đọc được | REUSE đòi cả dòng bản quyền, tệp hiện chỉ có dòng giấy phép; phải kiểm GitHub vẫn nhận một giấy phép sau khi có `LICENSES/` | Đã làm |
| **Trang docs là tài liệu sản phẩm** | Trang docs là hồ sơ dự thi; người mới không có đường đọc từ cài đặt tới mở rộng | Đưa bắt đầu nhanh, tệp mô hình, API, vận hành và hướng dẫn mở rộng lên trang; gắn nhãn `good first issue` cho vài việc nhỏ | Điểm tài liệu, phát triển bền vững và thu hút cộng đồng cùng dựa vào đây | Hai nơi chứa một nội dung dễ lệch nhau, nên trang docs phải dựng từ chính các tệp trong `docs/` | Chốt làm |

## 8. Đã cân nhắc, không đề xuất

| Ý | Lý do |
|---|---|
| Kubernetes và Argo CD | Có trong bảng tham chiếu của đề, nhưng bản cài là Compose trên một máy; thêm cụm chỉ để có GitOps kéo là đổi cả cách cài |
| GraphQL | API chung đã có lọc, sắp xếp, phân trang và OpenAPI theo mô hình; thêm một kiểu API là hai bề mặt cùng phải giữ quyền theo dòng |
| Két bí mật, tường lửa ứng dụng, che dữ liệu cá nhân | Thuộc bảy cụm của đề nhưng không phục vụ quy trình trên bản ghi; là thành phần người triển khai gắn ở biên |
| Kịch bản serverless trong lõi | Trái nguyên tắc không chạy mã tùy ý trong tiến trình lõi; mở rộng đi qua webhook và kiểm tra trước khi chuyển trạng thái |
| Trình kéo thả BPMN | `README.md` đã chốt người dùng không viết BPMN |
| Tham chiếu sang mô hình khác | Hợp ý chống ốc đảo dữ liệu của đề, nhưng mô hình không còn áp dụng và quay lui độc lập, và quyền của hai mô hình thuộc hai client khác nhau; đã chốt không làm, ghi ở `BACKLOG.md`, mục 4 |
