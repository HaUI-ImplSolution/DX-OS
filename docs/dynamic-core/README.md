# Lõi dynamic của DX-OS

Định hướng và phạm vi đã chốt ngày 2026-11-02, bổ sung ngày 2026-11-03.
Vòng hẹp của lõi đã chạy và được kiểm chứng, kết quả ở mục [Đã xác minh](#đã-xác-minh); các điểm còn lại nằm ở mục [Chưa xác minh](#chưa-xác-minh).

## Tóm tắt

- **Mục đích:** giữ phần nền ổn định và dùng chung, để nghiệp vụ bên trên đổi được nhanh, an toàn và không bị khóa vào nhà cung cấp nào.
- **Cách dùng:** như Keycloak hay Superset, kéo về, đưa một tệp mô hình là có service hoàn chỉnh, sửa tệp là đổi được service.
- **Miền phục vụ:** quy trình chung cho bản ghi có vòng đời và có người tham gia (hồ sơ, ticket, đặt chỗ, mượn trả...), đổi được mô hình và quy trình an toàn khi hệ thống đang chạy; xem [Miền phục vụ](#miền-phục-vụ).
- **Người dùng chính:** người triển khai hệ thống cho một tổ chức, làm việc qua [bảng điều khiển](#bảng-điều-khiển).
- **Cách xây:** lõi headless, ghép từ các dịch vụ nguồn mở; đội tự viết một dịch vụ Java gồm phần điều khiển và API chung đọc mô hình lúc chạy.
- **Ba tính năng đào sâu:** GitOps cho nghiệp vụ, quy trình không lách được, tác tử AI có người duyệt; phần AI làm sau khi lõi hoàn toàn mở.
- **Mở rộng:** người dùng có thể viết thêm API và giao diện, luôn nằm ngoài lõi.
- **Nguồn mở:** mọi thành phần của bản phát hành đều mở, kể cả công cụ, mô hình AI và dữ liệu.
- **Hạn gửi bài:** 17:00 ngày 2026-12-06.

## Đề thi

Nguồn: [đề thi](https://vfossa.vn/tin-tuc/de-thi-phan-mem-nguon-mo-olp-2026-798.html), [thể lệ](https://vfossa.vn/tin-tuc/the-le-olp-phan-mem-nguon-mo-nam-2026-760.html), [bài giới thiệu chủ đề](https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html) và [giáo trình DX-OS](https://opendigitransform.gitbook.io/dx-os).
Với giáo trình, mới đọc được phần tổng quan, mục lục chương và trang Hộp cát.

### Yêu cầu bắt buộc

- Bài thi gồm một nền tảng lõi ở Tầng 1 và ít nhất một ứng dụng minh họa có giao diện ở Tầng 2.
- Lõi hoàn toàn không có giao diện, chỉ hoạt động như một PaaS phục vụ các ứng dụng tầng trên.
- Lõi phải có định danh và SSO, cổng API, quản trị dữ liệu có cấu trúc và phi cấu trúc, động cơ luồng công việc.
- Sản phẩm theo giấy phép được OSI công nhận, công khai mã nguồn, có bản phát hành trước khi nộp.
- Đội được toàn quyền chọn công nghệ; bảng 7 lớp trong đề chỉ là ví dụ.
- Đề gợi ý tham khảo Supabase, Strapi, Directus.

### Mốc thời gian và thang điểm


| Ngày                       | Việc                                      |
| ----------------------------- | -------------------------------------------- |
| **2026-12-06, 17:00**       | **Hạn gửi bài**                         |
| 2026-12-07 đến 2026-12-09 | Chấm kho mã nguồn                       |
| 2026-12-10                  | Trình diễn 15 phút và chấm chung kết |
| 2026-12-11                  | Công bố và trao giải                   |


| Phần                                                                                | Điểm |
| -------------------------------------------------------------------------------------- | -------- |
| Kho mã, giấy phép, bản phát hành, dịch từ mã nguồn, thư viện, tài liệu | 50     |
| Tính nguyên gốc của giải pháp                                                  | 10     |
| Mức độ hoàn thiện                                                               | 10     |
| Mức độ thân thiện                                                               | 10     |
| Phát triển bền vững                                                              | 10     |
| Phong cách trình diễn, thu hút cộng đồng                                      | 10     |

### Gửi bài và trình diễn

- **Gửi bài:** thư điện tử tới `olpvietnam@vaip.vn` và `thuky@vfossa.vn`, tiêu đề `BÀI THI PMNM 2026 - [Tên Đội] - [Tên Trường]`.
- **Nội dung thư:** thành viên đội, người hướng dẫn, đường dẫn kho mã nguồn, cam kết bản quyền theo mẫu trong đề.
- **Chung kết:** trình bày giải pháp và lựa chọn công nghệ, tính năng nền tảng và bài toán minh họa, kết quả và demo, định hướng phát triển.
- **Môi trường demo:** đề khuyến khích chạy thử trên Internet để người dùng truy cập và phản hồi.

### Người ra đề muốn gì

Đề mở đầu bằng hai thái cực cần tránh: nhiều phần mềm rời rạc tạo ra "ốc đảo dữ liệu", và một hệ thống nguyên khối biến tổ chức thành "con tin" của nhà cung cấp.
Giữa hai thái cực đó là kiến trúc lắp ghép: nhiều thành phần độc lập nhưng có chung nền.

Bài giới thiệu định nghĩa chuyển đổi số là "quá trình chuyển giao quyền kiểm soát từ thao tác thủ công của con người sang các thuật toán tự động".
H-P-D-I là chuỗi tiến hóa đi theo thứ tự: môi trường số chung, rào chắn và luồng tự động, một nguồn sự thật, rồi tác tử AI với con người duyệt ngoại lệ.
AI vô nghĩa nếu chưa có rào chắn và dữ liệu sạch.

Bốn mục đích của lõi, rút từ đề và cột giá trị của bảng 7 lớp:

1. **Trừu tượng hóa hạ tầng:** "tập trung trừu tượng hóa độ phức tạp của hạ tầng kỹ thuật".
2. **Tách tốc độ thay đổi:** "linh hoạt thay đổi nghiệp vụ kinh doanh ở tầng trên mà luôn bảo đảm giữ vững tính ổn định của hệ thống nền tảng".
   Đây là câu quan trọng nhất của đề.
3. **Hợp nhất dữ liệu và sự kiện:** chống ốc đảo dữ liệu là nỗi lo trung tâm của đề.
4. **Giữ chủ quyền số:** nguồn mở là ưu tiên số một, AI chạy nội bộ, không bị một nhà cung cấp nắm giữ.

Các hàm ý sau là suy luận từ cách đề viết, người ra đề không nói thẳng:

- **Bài kiểm tra thật là tính trung lập:** Tầng 3 trải từ bán lẻ, bệnh án đến nhà máy, nên câu hỏi ngầm là chúng có dùng chung được lõi không.
- **Đề kiểm tra tư duy kiến trúc:** điểm nguyên gốc nằm ở cách "lắp ghép các phần mềm mã nguồn mở một cách logic", không nằm ở số lượng công cụ.
- **Danh sách dài ở Tầng 1 là tầm nhìn:** phần bắt buộc chỉ có bốn dịch vụ.
- **Low-code hợp mạch đề:** đề gom ba chủ đề OLP trước là low-code, dữ liệu liên kết và LLM.
- **Đối tượng là SME, cơ quan nhà nước và tư vấn viên:** giáo trình đề cao tỷ suất hoàn vốn, nên bộ cài càng nhẹ càng có lợi.
- **Dự án xuyên suốt của giáo trình là DX-Ticket:** ứng dụng yêu cầu và phê duyệt rất gần ngôn ngữ của người chấm.

### Lõi đáp ứng đề thế nào


| Đề nêu                                                               | Lõi đáp ứng                                                                            |
| ------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| Tự động tạo API từ cơ sở dữ liệu, phân quyền tới cấp dòng | API chung đọc mô hình lúc chạy, phân quyền theo vai và theo dòng                 |
| Điều chỉnh quy trình không cần sửa mã nguồn, BPMN              | Mẫu quy trình trên Flowable                                                             |
| GitOps, tự phục hồi                                                  | GitOps cho nghiệp vụ, tệp sai bị chặn, áp dụng dở được hoàn tất hoặc hủy bỏ |
| Kiến trúc hướng sự kiện                                           | Trục sự kiện bằng bảng outbox                                                         |
| Tác tử AI, Human-in-the-loop, RAG                                     | Tác tử có người duyệt và hỏi đáp kèm nguồn                                     |
| Chống nguyên khối, chủ quyền số                                   | Thành phần thay được, dữ liệu trong PostgreSQL thuần                               |
| Chống ốc đảo dữ liệu                                              | Một định danh, một cơ sở dữ liệu, mô hình là danh mục dữ liệu                |

### Bốn nguyên lý kỹ thuật của đề


| Nguyên lý                                                                                               | Lõi đáp ứng                                                                                |
| ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Phân tách theo vòng đời và tốc độ của dữ liệu, đẩy dữ liệu lịch sử xuống hồ dữ liệu | Ngoài phạm vi bản dự thi; Superset đọc dữ liệu để báo cáo                          |
| Hội tụ dữ liệu hành chính và dữ liệu vận hành, bản sao số                                    | Ngoài phạm vi bản dự thi                                                                   |
| Quản trị dữ liệu, phả hệ và nguồn gốc                                                            | Mô hình là danh mục dữ liệu; thông tin dữ liệu từ đâu ra làm nếu còn thời gian |
| Zero-Trust gắn với GitOps, kiểm dịch mã độc, tự phục hồi                                        | APISIX và API chung đều kiểm token, ClamAV quét tệp tải lên, GitOps cho nghiệp vụ    |

Hai nguyên lý đầu không được đáp ứng trong bản dự thi, nên phần trình bày cần nói rõ và đưa vào định hướng phát triển.

## Tư tưởng thiết kế

Mẫu tham chiếu là [`sso/realm-dxos.json`](../../sso/realm-dxos.json): đưa một tệp khai báo vào Keycloak là có toàn bộ hệ thống đăng nhập.
Mattermost cũng vậy: kéo về là dùng được, khi mở rộng chỉ đổi cấu hình.

- **Không sinh mã:** API chung và các engine đọc mô hình lúc chạy, nên không có mã sinh ra để bảo trì và API không biết trước thực thể nào.
- **Miền hẹp:** Keycloak làm được vì miền của nó hẹp và cố định, nên lõi cũng chọn một miền và nói rõ chỗ dừng.
- **Chất lượng nằm ở quy trình:** lõi làm trọn một quy trình, như Keycloak với danh tính và Superset với dữ liệu.
- **Đứng trên vai người khổng lồ:** thứ đã có dịch vụ nguồn mở thì dùng lại, và đóng góp ngược khi chạm giới hạn thật của công cụ.


| Sản phẩm     | Miền                 | Quy trình chỉn chu                                                                          |
| ---------------- | ----------------------- | ----------------------------------------------------------------------------------------------- |
| Keycloak       | Danh tính            | Khai báo realm, đăng nhập, phân quyền, xuất và nhập                                  |
| Superset       | Dữ liệu, biểu đồ | Kết nối, dataset, biểu đồ, dashboard                                                     |
| **Lõi DX-OS** | **Quy trình chung cho bản ghi có vòng đời** | **Khai báo mô hình, xem trước, áp dụng, vận hành, đổi phiên bản hoặc quay lui** |

Các ý tưởng học từ [Frappe](https://github.com/frappe/frappe) (MIT), cần đọc mã của họ trước khi áp dụng:

- Một mô hình là nguồn sự thật cho bảng, form và quyền.
- Bộ kiểu trường theo nghiệp vụ, như tham chiếu và tệp đính kèm.
- Tách phần tùy biến khỏi mô hình gốc.
- Sự kiện vòng đời hồ sơ làm điểm gắn mở rộng.

[Supabase](https://github.com/supabase/supabase) (Apache-2.0) là kiến trúc nên đọc đầu tiên, vì ghép các mảnh tương tự: PostgreSQL, PostgREST, xác thực, lưu trữ, cùng [postgres-meta](https://github.com/supabase/postgres-meta) để quản lý schema qua API.

## Miền phục vụ

Lõi phục vụ một năng lực là quy trình có người tham gia trên bản ghi có vòng đời, với điểm khác biệt là đổi được mô hình và quy trình an toàn khi hệ thống đang chạy.
Hồ sơ, ticket, lượt đặt chỗ và phiếu mượn là các cách dùng của cùng một quy trình chung, được làm thật sâu và chỉn chu.
Miền là một năng lực dùng được ở nhiều ngành, không phải một ngành cụ thể.

| Phần | Vai trò | Nội dung |
|---|---|---|
| **Quy trình chung** | Miền của lõi | Bản ghi đi qua các bước, có người tham gia, điều kiện chuyển, hạn và hành động hoàn tất |
| **Vòng đời mô hình** | Điểm khác biệt của lõi | Xem trước, áp dụng, đổi phiên bản và quay lui trên hệ thống đang chạy |
| **Mô hình nghiệp vụ** | Chạy trên lõi | Một cửa sinh viên, ticket hỗ trợ, đặt phòng, cấp phát dịch vụ IT |

Bản ghi có vòng đời là khái niệm hạng nhất của lõi, gồm:

- Người tạo và người xử lý.
- Hộp việc cần xử lý.
- Hạn xử lý.
- Minh chứng.
- Yêu cầu bổ sung.
- Quyết định.
- Dòng lịch sử.

Thực thể không có quy trình, như danh mục thiết bị hay danh sách phòng, là dữ liệu phụ trợ phục vụ bản ghi.

Cấp phát dịch vụ cần hành động hoàn tất sau khi duyệt.
Hành động này chỉ gọi các thao tác đã đăng ký và chạy sau khi giao dịch hoàn tất, qua trục sự kiện.

Lõi chỉ nhận quy trình có người tham gia.
Lõi không phục vụ bán hàng, kho hàng, ứng dụng chỉ có CRUD, đường ống dữ liệu tự động và dữ liệu thời gian thực.

### Quy trình chung

Quy trình chung là phần được đào sâu nhất của lõi.
Người dùng không viết BPMN: họ chọn họ mẫu và nhập tham số trên bảng điều khiển, hệ thống tạo quy trình từ đó.

**Họ mẫu** được đặt tên theo hình dạng chung của quy trình, không theo một ngành.
Mỗi họ mẫu là một quy trình viết tay, nhận tham số qua biến và dùng chung các quy trình con.
Một họ mẫu chỉ được giữ khi ít nhất ba miền khác nhau chạy được trên nó bằng tham số thuần, không sửa mã lõi.

| Họ mẫu | Hình dạng chung | Miền chạy bằng tham số |
|---|---|---|
| **Chuỗi duyệt** | Nộp, một hoặc nhiều cấp duyệt tuần tự hoặc song song, quyết định, có thể trả lại | Yêu cầu sinh viên, nghỉ phép, mua sắm, xin quyền truy cập |
| **Công việc cần xử lý** | Tiếp nhận, phân loại, giao người, xử lý, xác nhận, đóng; có ưu tiên và hạn | Ticket hỗ trợ, báo hỏng, sự cố, yêu cầu dịch vụ |
| **Chiếm dụng tài nguyên theo thời gian** | Yêu cầu, kiểm tra xung đột khung giờ, xác nhận, sử dụng, hoàn tất hoặc vắng mặt | Phòng khách sạn, phòng thực hành, thiết bị, lịch hẹn, ca trực, mượn trả |

Họ chiếm dụng tài nguyên theo thời gian sở hữu việc kiểm tra xung đột khung giờ, với sức chứa của tài nguyên là tham số.
Khối `booking:` trong `process:` nêu trường tham chiếu tới tài nguyên, trường bắt đầu, trường kết thúc và sức chứa.
Khung giờ tính nửa mở: hai lượt liền nhau không xung đột, và lượt đã ở trạng thái cuối không còn chiếm tài nguyên.
Mượn trả thiết bị chạy trên họ này, với tham số chọn khung giờ đặt trước hoặc khoảng từ lúc phát đến hạn trả.

Hai họ sau là hướng mở rộng, chỉ làm khi ba họ trên đã hoàn chỉnh và đủ ba miền chứng minh:

| Họ mẫu | Hình dạng chung | Miền chạy bằng tham số |
|---|---|---|
| **Kiểm tra định kỳ** | Lịch sinh bản ghi, kiểm tra, ghi kết quả, hành động khắc phục | Bảo trì thiết bị, kiểm định an toàn, gia hạn giấy phép |
| **Đánh giá và chọn lọc** | Nhiều người chấm, tổng hợp điểm, chọn theo chỉ tiêu | Học bổng, tuyển dụng, chấm dự án |

**Khối dùng chung** của mọi họ mẫu:

- Bước người xử lý, gồm phân công, hạn, các hành động cho phép và lý do.
- Vòng trả lại để bổ sung.
- Hành động hoàn tất.
- Rút lại.

**Quy tắc bất biến** nằm trong engine bằng Java, không nằm trong tệp BPMN, nên mọi quy trình đều chịu cùng một bộ quy tắc:

1. Cột trạng thái chỉ quy trình được ghi.
2. Trạng thái cuối bị đóng băng, ghi ai quyết định và lúc nào.
3. Mỗi lần chuyển trạng thái để lại một dòng lịch sử gồm người làm, lúc nào, hành động và trạng thái trước sau; từ chối và trả lại bắt buộc có lý do.
4. Người làm hành động phải có vai và thấy được bản ghi.
5. Người tạo không tự quyết định bản ghi của mình, trừ khi mô hình bật `allow_self_decision` một cách có chủ ý.
6. Người nộp rút lại được khi chưa ở trạng thái cuối, và bản ghi được giữ lại.
7. Hành động hoàn tất chạy sau khi giao dịch xong, thử lại được và có khóa chống chạy lặp.

Khi áp dụng mô hình, hệ thống còn kiểm tra không có trạng thái cụt và mọi vai được nhắc tới có thật trong Keycloak.

**Ba tầng quy tắc:**

| Tầng | Ví dụ | Ai quyết định |
|---|---|---|
| Bất biến | Bảy quy tắc trên | Lõi, mô hình không tắt được |
| Tham số của quy trình | Vai duyệt, số cấp duyệt, độ dài hạn, vòng trả lại, hai cấp duyệt không do cùng một người | Người triển khai, trên bảng điều khiển |
| Ràng buộc nghiệp vụ | Giới hạn số lần đặt, điều kiện riêng của tổ chức | Người triển khai, trong mô hình |

Hạn và leo thang không bắt buộc: mô hình có khai báo hạn thì quá hạn được ghi lại và nhắc.
Chi tiết tham số của từng họ mẫu và các khối dùng chung được thiết kế ở các phase sau.

**Quy trình nằm trong kế hoạch áp dụng:**

- Một lần áp dụng sinh cả phần mềm lẫn quy trình từ cùng một tệp mô hình: lược đồ, vai Keycloak, quy trình và phiên bản mô hình nằm chung một kế hoạch.
- Kế hoạch cho xem khác biệt của quy trình, kiểm tra không có trạng thái cụt và vai có thật, và báo số bản ghi đang chạy bị ảnh hưởng.
- Luật của quy trình (người xử lý, hạn xử lý, quyền tự quyết, hành động hoàn tất, đặt chỗ) chỉ đổi được khi thực thể không còn hồ sơ đang xử lý, nên mỗi hồ sơ chỉ chịu một bộ luật từ lúc nộp đến lúc đóng.
- Đổi họ mẫu của một thực thể bị từ chối; muốn đổi hình dạng quy trình thì thêm thực thể mới.
- Chuyển hồ sơ đang chạy sang quy trình mới bằng `ProcessMigrationService` của Flowable là hướng phát triển.
- Đổi lược đồ và vai Keycloak nằm ngoài giao dịch của Flowable, nên "một lần" nghĩa là một kế hoạch có thứ tự, chạy lại được hoặc quay lui, không phải một giao dịch duy nhất.
- Việc ghép này được làm cùng lúc với việc triển khai các họ mẫu.

**Họ mẫu tùy biến:**

- Người dùng nâng cao mở rộng từ một mẫu có sẵn hoặc từ BPMN mặc định đã mang các quy tắc, hoặc nạp tệp BPMN riêng.
- Bảng điều khiển có giao diện dạng danh sách bước và tham số để mở rộng từ mẫu mà không viết mã; không có trình kéo thả.
- Quy tắc bất biến nằm trong engine nên họ mẫu tùy biến cũng phải chịu.
- Tệp tùy biến chỉ đổi trạng thái qua các khối do engine cung cấp, không chạy mã tùy ý trong tiến trình của lõi, và bị kiểm khi áp dụng.

## Trải nghiệm người dùng

Người dùng chỉ cần Docker, không cần mã nguồn, JDK, Node hay Python.


| Tệp                 | Vai trò                                    |
| ---------------------- | --------------------------------------------- |
| `docker-compose.yml` | Kéo và chạy các image đã phát hành  |
| `.env`               | Mật khẩu, tên miền, kết nối hạ tầng |
| `model.yaml`         | Service này là gì                        |

```bash
curl -fsSL https://github.com/HaUI-ImplSolution/DX-OS/releases/latest/download/dxos-install.sh | bash
dxos start model.yaml   # khởi động, tạo tài khoản admin đầu tiên, áp dụng model.yaml nếu là tệp mới, in các địa chỉ
dxos plan model.yaml   # xem trước sau khi sửa model.yaml
dxos apply model.yaml  # áp dụng
```

Bộ cài và các lệnh vận hành của `dxos` mô tả ở [`deploy/OPERATIONS.md`](../../deploy/OPERATIONS.md#cài-đặt-và-lệnh-dxos); `dxos plan` và `dxos apply` đưa tệp mô hình vào hệ thống đang chạy với cùng kế hoạch và mức xác nhận như bảng điều khiển.


| Trường hợp                                          | Hành vi                                                   |
| -------------------------------------------------------- | ------------------------------------------------------------ |
| Tệp sai                                               | Báo lỗi đúng dòng, service vẫn chạy phiên bản cũ |
| Thay đổi an toàn                                    | Áp dụng được ngay, không dựng lại                  |
| Thay đổi cần chuyển dữ liệu hoặc mất dữ liệu | Service chạy phiên bản cũ, chờ xác nhận kế hoạch  |
| Khởi động lại với tệp không đổi               | Không làm gì                                            |
| Áp dụng lại tệp không đổi mà bảng hoặc Keycloak đã lệch | Kế hoạch ghi `repair`, áp dụng sửa phần lệch và giữ phiên bản |

Tệp mô hình luôn là nguồn sự thật, và dựng lại được cấu trúc hệ thống trên máy khác; dữ liệu hồ sơ đi theo đường sao lưu riêng.

Mô hình không khai báo AI thì không chạy dịch vụ AI, không khai báo đích webhook thì không gửi gì ra ngoài và không cần dịch vụ chat.
Ai đã có Keycloak hay PostgreSQL riêng thì trỏ tới bằng `.env`.
Phân phối bằng một tệp compose gồm nhiều image, vì mỗi dịch vụ có vòng đời và cách nâng cấp riêng.

### Bảng điều khiển

Người triển khai làm việc qua bảng điều khiển; CLI `dxos` là máy khách mỏng dùng cho CI.
Bảng điều khiển thuộc tầng 2, nằm ở tuyến `/console` của ứng dụng web, chỉ dành cho vai ADMIN và chỉ gọi API của lõi.

- **Thư viện mẫu:** chọn họ mẫu và mô hình mẫu, kèm mô tả mỗi mẫu cần gì.
- **Soạn mô hình có hướng dẫn:** form dựng từ JSON Schema của định dạng mô hình, trình soạn YAML có gợi ý và báo lỗi theo schema, và bản xem trước giao diện sẽ được sinh ra.
- **Màn hình kế hoạch:** khác biệt, mức rủi ro, dòng vi phạm và xác nhận thay đổi mất dữ liệu.
- **Lịch sử áp dụng:** từng phiên bản và từng bước của mỗi lần áp dụng, kèm quay về phiên bản trước.
- **Sơ đồ quy trình:** trạng thái và hành động của họ mẫu mà thực thể dùng, chỉ để xem.
- **Người dùng:** tìm người dùng, xem vai theo từng ứng dụng, gán và gỡ vai và đơn vị, tạo người dùng với mật khẩu tạm, khóa và mở, đặt lại mật khẩu; mỗi thay đổi được ghi vào nhật ký với người thực hiện và hiện lại ở hộp thoại của người đó: ai đổi gì, lúc nào.
  Tài khoản không khai báo trong tệp mô hình: tệp là cấu trúc và nằm trong git, còn người dùng là dữ liệu có email và mật khẩu, nên tài khoản nằm ở Keycloak.
  Tab chỉ mở API mỏng trên Keycloak: chính sách mật khẩu, MFA, phiên và đăng nhập bằng nhà cung cấp ngoài để Keycloak lo.
  Người dùng cuối tự đổi mật khẩu, bật OTP và xem phiên ở Account Console của Keycloak, mở từ mục "Tài khoản của tôi" trong menu người dùng.

Bảng điều khiển đọc và ghi đúng tệp mô hình, và tệp luôn là nguồn sự thật.
Giao diện cho người dùng cuối do bộ dựng sinh từ metadata, đủ dùng, nhất quán và được chăm chút về hình thức.

## Kiến trúc

### Ranh giới các tầng

- **Tầng 1, lõi:** chỉ có API, không có trang nào do đội viết.
- **Tầng 2, ứng dụng minh họa:** bộ dựng giao diện đọc metadata, gồm danh sách, form, chi tiết, hộp việc cần xử lý, chế độ xem lịch cho thực thể có trường khoảng thời gian, màn hình xem kế hoạch và lịch sử áp dụng.
  Bộ dựng xây trên react-admin, đội chỉ viết lớp mỏng nối metadata với khung.
  Portal và màn hình giám sát cũng thuộc tầng này.
- **Tầng 3, nghiệp vụ:** chỉ là tệp mô hình, không có mã.

### Hai phần của lõi

**Dịch vụ lõi** do đội tự viết bằng Java 21, dùng lại phần cần thiết của `dx-core`, gồm ba phần chạy trong một tiến trình:

- **Phần điều khiển:** lưu các phiên bản mô hình, kiểm tra hợp lệ, tính khác biệt rồi áp dụng tuần tự và ghi lịch sử.
  Cách làm giống Terraform: khai báo trạng thái đích, xem kế hoạch, rồi mới áp dụng.
- **API chung:** một API dùng cho mọi thực thể, đọc mô hình đang chạy để biết có những thực thể, trường, quyền và quy tắc nào.
  Request đến đâu, API đối chiếu với mô hình rồi mới dựng truy vấn SQL; thêm hay đổi thực thể chỉ cần đổi mô hình, không đổi mã.
- **Flowable nhúng:** chạy quy trình và là nơi duy nhất đổi trạng thái bản ghi, cùng giao dịch với việc ghi bản ghi.

**Phần chạy** dùng nguồn mở, mỗi công cụ là một provider nhận phần cấu hình của mình.
Thêm năng lực mới nghĩa là thêm provider, không phải sửa lõi.


| Năng lực                        | Thành phần                              | Đội tự viết                                                 |
| ----------------------------------- | ------------------------------------------- | ----------------------------------------------------------------- |
| Định danh, SSO                  | Keycloak                                  | Sinh client và vai từ mô hình, quản trị người dùng           |
| Cổng API                         | APISIX                                    | Sinh route khi cần                                             |
| Dữ liệu có cấu trúc          | PostgreSQL, pgschema                     | Sinh schema và ràng buộc; mỗi mô hình một schema         |
| API dữ liệu, quyền từng dòng | API chung trong dịch vụ lõi            | Đọc mô hình, kiểm token Keycloak, dựng truy vấn an toàn |
| Dữ liệu phi cấu trúc          | SeaweedFS, ClamAV                         | Dùng lại `dx-core/storage`, gắn với trường tệp           |
| Luồng công việc                | Flowable chạy nhúng                     | Các họ mẫu quy trình, API đổi trạng thái                          |
| Vòng đời mô hình             | Git, phần điều khiển                  | **Phần tự viết chính**                                      |
| Trục sự kiện                   | Bảng outbox trong PostgreSQL             | Webhook theo mô hình, API kéo sự kiện theo `eventId`, thông điệp của việc áp dụng mô hình và của tài khoản |
| Thông báo                       | Mattermost hoặc Slack do người triển khai cấp | Đích webhook dạng `chat`: việc mới, sắp đến hạn, quá hạn |
| Báo cáo                         | Superset                                  | Sinh dataset từ mô hình                                      |
| AI | Làm sau | Khai báo nguồn tri thức và tác tử |
| Vận hành                        | `dx-monitor`, `deploy/backup.sh`          | Giữ nguyên                                                    |

### Nguyên tắc mã nguồn

- **Lõi không biết về nghiệp vụ:** mã lõi không có tên thực thể, vai hay luồng của một lĩnh vực.
- **Truy vấn dựng từ mô hình đã kiểm:** tên bảng và cột chỉ lấy từ mô hình đang chạy, giá trị luôn đi qua tham số.
- **Tách hai loại cấu hình:** `.env` cho hạ tầng, `model.yaml` cho nghiệp vụ.
- **Một đường duy nhất:** dựng lần đầu là áp dụng kế hoạch từ trạng thái rỗng.
- **Phát hành là image có phiên bản:** nâng cấp bằng đổi tag.
- **Kiểm thử từ góc người dùng:** E2E chỉ dùng image đã build và một tệp mô hình.
- **Định dạng mô hình có tài liệu tham chiếu:** kèm JSON Schema có phiên bản.

## Tệp mô hình

Trần dưới đây giữ cho tệp mô hình không biến thành ngôn ngữ lập trình.


| Mức              | Nội dung                                               | Cách làm                                                      |
| ------------------- | --------------------------------------------------------- | ----------------------------------------------------------------- |
| **1. Khai báo**  | Thực thể, kiểu trường, quyền, luồng, đích webhook | Chỉ tệp mô hình                                             |
| **2. Cấu hình** | Quy tắc, biểu thức, hạn xử lý, tác tử AI        | Chỉ tệp mô hình, không viết mã                           |
| **3. Mở rộng**  | Logic đặc thù, giao diện riêng                     | Mã nằm ngoài lõi, xem [Điểm mở rộng](#điểm-mở-rộng) |


| Phần          | Giới hạn                                                                                 |
| ---------------- | -------------------------------------------------------------------------------------------- |
| Kiểu trường | Văn bản, số, ngày giờ, lựa chọn, tham chiếu, tệp, người dùng                   |
| Quyền         | Theo vai và theo dòng: tất cả, của tôi, của đơn vị hoặc được giao cho tôi   |
| Quy tắc       | Bắt buộc, duy nhất, chống trùng khoảng thời gian, chặn khi chưa đủ điều kiện |
| Biểu thức    | Một dòng, không có vòng lặp, không có tác dụng phụ                              |
| Quy trình     | Chọn từ mẫu có sẵn và điền tham số                                                |
| Tác tử AI    | Khi có sự kiện, đề xuất giá trị một trường, người xác nhận                  |

Một năng lực mới chỉ được thêm khi cần ở ít nhất 3 lĩnh vực, khai báo được mà không cần cú pháp mới, và không làm hỏng các mô hình đã có.
Không đạt thì đi qua điểm mở rộng.

Ví dụ để minh họa, chưa phải cú pháp cuối cùng:

```yaml
entity: StudentRequest
fields: {kind: choice(confirmation, regrade, postpone), reason: text!, proof: file, student: user}
access: {OFFICE: all, STUDENT: own}
process: {deadline_task: {by: OFFICE, deadline: 72h, notify: chat}}
ai:
  ask: {sources: [regulations]}
  agent: {on: created, suggest: kind, confirm_by: OFFICE}
```

## Ba tính năng đào sâu

### 1. GitOps cho nghiệp vụ

Nghiệp vụ được khai báo trong git, lõi đưa toàn bộ hệ thống về đúng trạng thái đó, lỗi thì quay lui.
Đây là đặt cược về tính nguyên gốc: đề không yêu cầu trực tiếp, và theo những gì đã đọc, các dự án tham khảo không tập trung vào việc đổi mô hình an toàn trên hệ thống đang chạy.


| Bước            | Lõi làm gì                                                                            | Người dùng nhận được                                     |
| ------------------- | ------------------------------------------------------------------------------------------ | ----------------------------------------------------------------- |
| Khai báo         | Nhận tệp mô hình qua `dxos apply`                                                    | Một tệp duy nhất, lưu được trong git                     |
| Kiểm tra         | Soát cú pháp, tham chiếu, vai, quy tắc; chưa đụng vào hệ thống                | Lỗi chỉ đúng dòng và đúng lý do                        |
| Xem trước       | So với phiên bản đang chạy, lập kế hoạch cho từng provider, phân loại rủi ro | Biết sẽ đổi gì, ảnh hưởng bao nhiêu dòng              |
| Áp dụng         | Thực hiện theo thứ tự, ghi lịch sử                                                 | Hệ thống đổi theo mô hình, không build lại              |
| Vận hành        | API và giao diện đọc metadata của phiên bản đang chạy                           | Người dùng cuối thấy ngay thay đổi                       |
| Đổi phiên bản | Lặp lại từ đầu, giữ lịch sử                                                      | Biết ai đổi gì, khi nào                                    |
| Quay lui          | Quay về phiên bản trước nếu an toàn                                               | Khôi phục được, hoặc được báo rõ vì sao không thể |

Git là nơi lưu lịch sử của tệp mô hình; lõi nhận tệp qua `dxos apply` và không tự kéo từ git.
Lõi tự kéo từ git để liên tục đối chiếu là hướng phát triển.

Thay đổi mô hình sai được xử lý như sau:

- **Tệp sai:** bị chặn ở bước kiểm tra, hệ thống vẫn chạy phiên bản đang chạy.
- **Áp dụng dở:** mỗi bước chạy lại được an toàn, và lõi giữ mô hình lại cho đến khi quản trị viên quyết định.
  Người dùng chưa vào được mô hình đó, còn bảng điều khiển hiện nhãn "Đang dừng dở" và lịch sử ghi bước đã dừng.
  **Tiếp tục** chạy lại từ nội dung, đổi tên và mức đã xác nhận mà lần áp dụng đó đã lưu, nên không cần tệp.
  **Hủy bỏ** đưa các bảng về phiên bản đang chạy, hoặc xóa những gì lần áp dụng đầu đã tạo nếu mô hình chưa có phiên bản.
  Lõi không tự thử lại và không tự quay lui.
- **Hệ thống lệch khỏi tệp:** cột, ràng buộc, client, vai hoặc nhóm đơn vị bị xóa tay thì áp dụng lại cùng tệp báo `repair` và sửa lại, không tăng phiên bản.
  Bảng hoặc cột thừa bị từ chối, đổi kiểu cột hay bỏ ràng buộc thừa cần xác nhận mức `MIGRATION`, và lõi không tự sửa lúc khởi động.

Ví dụ một lần xem trước:

```text
Phiên bản 7 -> 8

An toàn
  + PostgreSQL  thêm cột priority
  + Giao diện   thêm priority vào form và danh sách

Cần chuyển dữ liệu
  ~ PostgreSQL  đổi tên reason thành content, giữ nguyên 1.240 dòng
  ! Quy tắc     proof bắt buộc: 3 dòng hiện có đang thiếu, phải xử lý trước khi áp dụng

Không thể áp dụng: còn 3 dòng vi phạm.
```


| Mức                       | Ví dụ                                                       | Cách xử lý                               | Quay lui            |
| ---------------------------- | --------------------------------------------------------------- | --------------------------------------------- | --------------------- |
| **An toàn**               | Thêm trường tùy chọn, thực thể, vai, kênh thông báo | Áp dụng sau khi xác nhận kế hoạch     | Được             |
| **Cần chuyển dữ liệu** | Đổi tên, thêm trường bắt buộc, thêm quy tắc         | Dữ liệu vi phạm thì dừng và liệt kê | Kế hoạch nêu rõ |
| **Mất dữ liệu**         | Xóa trường, xóa thực thể                                | Sao lưu trước và xác nhận riêng      | Chỉ từ sao lưu   |

Những chỗ khó:

- **Đổi tên:** so sánh hai tệp không phân biệt được đổi tên với xóa rồi thêm, nên tệp mô hình phải khai báo tên cũ.
- **Không có giao dịch chung:** các provider có thể lỗi giữa chừng, nên mỗi bước phải chạy lại được an toàn và lịch sử cho biết dừng ở đâu.
- **Bên gọi API cũ:** đổi tên trường làm hỏng bên gọi cũ, nên bước xem trước phải nói rõ.
- **Hai người sửa cùng lúc:** kế hoạch gắn với phiên bản gốc, phiên bản đang chạy đã đổi thì kế hoạch hết hiệu lực.
- **Định dạng mô hình đổi theo thời gian:** tệp ghi phiên bản định dạng, lõi tự nâng được tệp cũ.

**Bản dự thi** làm thêm, đổi tên, xóa trường; thêm quy tắc và báo dữ liệu vi phạm; quay lui cho thay đổi an toàn ở mức nên có.
Đổi kiểu trường bị từ chối kèm giải thích.
Đổi mẫu quy trình khi còn hồ sơ đang chờ dùng `ProcessMigrationService` của Flowable, làm khi có mẫu thứ hai.

### 2. Quy trình không lách được

Flowable thay phần workflow viết tay, người dùng không đụng tới BPMN.
Đội viết sẵn và kiểm thử các họ mẫu quy trình, người dùng chỉ chọn họ mẫu và điền tham số như ai duyệt, hạn bao lâu, báo cho ai; xem [Quy trình chung](#quy-trình-chung).
Hạn xử lý lưu trên bản ghi và quá hạn được tính khi đọc, một tác vụ định kỳ ghi dòng quá hạn vào lịch sử; Flowable lo phiên bản quy trình.

Trạng thái hồ sơ không thể bị đi vòng:

- Flowable là nơi duy nhất được đổi trạng thái hồ sơ.
- API chung từ chối mọi yêu cầu ghi cột trạng thái.
- Dịch vụ lõi là kết nối ghi duy nhất vào dữ liệu nghiệp vụ; các dịch vụ khác như Superset chỉ có quyền đọc.
- Flowable chạy nhúng trong dịch vụ lõi dùng chung cơ sở dữ liệu, để đổi trạng thái và ghi hồ sơ nằm trong một giao dịch.

Quy tắc khai báo được API chung kiểm để báo lỗi rõ và biên dịch thành ràng buộc trong cơ sở dữ liệu.
Mọi thay đổi hồ sơ và bước quy trình phát ra sự kiện vào trục sự kiện, để thông báo, báo cáo và AI cùng nghe.
Tệp mô hình chọn đích webhook bằng `webhook: <tên đích>`; địa chỉ và bí mật của đích nằm ngoài tệp mô hình, trong một tệp cấu hình của bản cài.
Các dòng của một hồ sơ tới đích theo đúng thứ tự, và bên nhận lỡ tin kéo lại theo `eventId`.

**Bản dự thi** có ba họ mẫu là chuỗi duyệt, công việc cần xử lý và chiếm dụng tài nguyên theo thời gian, cùng bộ quy tắc bất biến; có hộp việc, giao việc và quy tắc chặn; trục sự kiện có webhook theo mô hình, thông báo chat và nhắc hạn.
Chi tiết tham số của từng họ mẫu được thiết kế ở các phase sau.

### 3. Tác tử AI có người duyệt

Phần này làm sau, khi lõi đã hoàn toàn mở.
Khai báo trên một mô hình bất kỳ để có hỏi đáp kèm nguồn và tác tử nghe sự kiện.
Có hồ sơ mới thì AI đề xuất và dẫn nguồn, người xác nhận.
Đây là chuỗi từ P tới I đúng với Human-in-the-loop của đề, và cũng chứng minh cơ chế provider hoạt động.

**Bản dự thi** chưa có phần AI: tìm kiếm tri thức có nguồn, hỏi đáp sinh câu trả lời và tác tử đề xuất giá trị một trường làm sau, khi có mô hình mở hoàn toàn, đều ở mức nên có.
Tác tử tự hành động không cần xác nhận để sau.

## Điểm mở rộng

Người dùng có thể viết thêm API và giao diện khi cần, không bắt buộc.
Mã mở rộng luôn nằm ngoài lõi, chỉ gọi lõi qua API và sự kiện, nên nâng cấp lõi không làm hỏng nó và lỗi của nó không kéo sập lõi.


| Nhu cầu                              | Cách làm                                                                                                |
| --------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| API riêng                            | Dịch vụ viết bằng ngôn ngữ tùy ý, khai báo trong mô hình; APISIX định tuyến và kiểm token |
| Phản ứng theo nghiệp vụ           | Webhook nhận sự kiện từ trục sự kiện                                                               |
| Giao diện riêng hoàn toàn         | Ứng dụng tự viết, dùng API và đăng nhập Keycloak của lõi                                       |
| Trang riêng trong ứng dụng Tầng 2 | Khai báo mục menu trỏ tới trang riêng                                                                |
| Họ mẫu quy trình riêng                | Tệp BPMN tự viết hoặc mở rộng từ mẫu qua bảng điều khiển; vẫn chịu quy tắc bất biến và bị kiểm khi áp dụng |
| Thành phần riêng trong form        | Đăng ký thành phần React, để sau bản dự thi                                                      |

Đổi tên trường có thể làm hỏng mã mở rộng đang dùng trường đó.
Bản dự thi chỉ nhắc rằng có mã mở rộng đang đăng ký; cảnh báo theo từng trường để sau.

**Bản dự thi** có route API, webhook, mục menu và tài liệu, kèm ví dụ dịch vụ sinh giấy xác nhận.

## Ứng dụng nghiệp vụ

Ứng dụng nghiệp vụ tập trung hai đến ba tính năng, mỗi tính năng thể hiện một khả năng của lõi:

- **Xử lý có hạn:** hồ sơ được giao cho một vai và phải xử lý trong hạn.
- **Phê duyệt một cấp:** hồ sơ chờ một vai duyệt hoặc từ chối.
- **Tính năng thêm lúc trình diễn:** thêm một trường, một thực thể hoặc một bước chỉ bằng cách sửa tệp mô hình, không viết mã; đây là cách chứng minh tính dynamic của lõi.

Kịch bản demo chính là **Một cửa sinh viên**: gửi yêu cầu xin giấy xác nhận, phúc khảo, hoãn thi kèm minh chứng.
Đây là mô hình yêu cầu và phê duyệt chạy trên lõi.
Phần AI đề xuất và hỏi đáp quy chế bổ sung sau, từ một kho văn bản đạt [cam kết nguồn mở](#cam-kết-nguồn-mở).

**Mô hình thứ hai** là cấp phát dịch vụ IT, rất ngắn, chứng minh tính trung lập: nhân viên xin vào một nhóm quyền, được duyệt thì Keycloak cấp thật.
Nếu mô hình này tốn hơn một tệp, đó là tín hiệu lõi còn gắn với ứng dụng đầu.

Ba họ mẫu quy trình được kiểm bằng ba mô hình: Một cửa sinh viên (chuỗi duyệt), ticket hỗ trợ (công việc cần xử lý) và đặt phòng (chiếm dụng tài nguyên theo thời gian).

## Phạm vi bản dự thi


| Mức                     | Nội dung                                                                                                                                                                                                                                                                                                              |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **Phải có để nộp**  | Định dạng mô hình, sinh bảng, API chung với quyền từng dòng, quyền theo đơn vị, vai Keycloak, xem trước và áp dụng, ba họ mẫu quy trình, bảng điều khiển của người triển khai, hộp việc, giao việc, yêu cầu bổ sung, chặn lách API, tệp đính kèm, ứng dụng Tầng 2, ứng dụng nghiệp vụ, bản phát hành và tài liệu |
| **Nên có**             | Trục sự kiện, thông báo chat, mô hình thứ hai và hành động hoàn tất, quay lui, điểm mở rộng, họ mẫu tùy biến                                                                                                                                                                                                          |
| **Làm sau lõi**        | Tác tử AI và hỏi đáp, sau khi lõi hoàn toàn mở                                                                                                                                                                                                                                                               |
| **Nếu còn thời gian** | Lịch sử biến động dữ liệu, AI soạn mô hình, sinh dataset Superset, đo và công bố tài nguyên, thông tin dữ liệu từ đâu ra                                                                                                                                                    |

Ngoài phạm vi:

- Vault, WAF, Redpanda, Iceberg, Trino, ImmuDB, NGSI-LD, GIS.
- Trình thiết kế BPMN kéo thả.
- Trang giao diện tự do, trò chuyện, cộng tác thời gian thực.
- Kế toán, kho hàng phức tạp, ERP.
- Mobile và chẩn đoán ảnh.
- Nhiều môi trường, đổi mô hình hoàn toàn không gián đoạn.
- Logic tùy ý trong tệp mô hình, plugin chạy trong tiến trình của lõi.

### Thế nào là đạt

Trên máy sạch, không có mã nguồn:

1. Chạy một lệnh với tệp mô hình, có hệ thống đăng nhập được với API và giao diện.
2. Sinh viên chỉ thấy hồ sơ của mình; cán bộ chỉ thấy hồ sơ của đơn vị mình hoặc được giao, và xử lý từ hộp việc.
3. Đính kèm được tệp; tệp nhiễm mã độc bị chặn khi bật quét.
4. Gọi thẳng API để đổi trạng thái thì bị chặn.
5. Thêm và đổi tên một trường khi đã có dữ liệu: có kế hoạch xem trước, không build lại, dữ liệu còn nguyên.
6. Đưa vào một mô hình thuộc họ mẫu khác (công việc cần xử lý hoặc chiếm dụng theo thời gian), chạy được mà không sửa mã lõi.
7. Mô hình mới mà dữ liệu đang vi phạm quy tắc thì bị từ chối, kèm các dòng vi phạm.
8. Cán bộ yêu cầu bổ sung, sinh viên sửa và nộp lại, hồ sơ đi tiếp theo quy trình.

Tám mục này cũng là khung kịch bản trình diễn 15 phút, cộng thêm phần vấn đề, kiến trúc và định hướng phát triển.

### Thứ tự công việc

1. Dọn nền: gỡ Farm, Lab, mobile, `DXOS_APP` và rút gọn `dx-core`, để lõi được xây trên nền sạch.
2. Vòng hẹp: một tệp mô hình qua API chung, PostgreSQL, Keycloak, Flowable.
3. Đổi mô hình khi có dữ liệu và chặn lời gọi vượt quy trình, vì đây là hai rủi ro lớn nhất.
4. Xem trước và áp dụng, mẫu quy trình, hộp việc, giao việc, yêu cầu bổ sung, quyền theo đơn vị, tệp đính kèm, ứng dụng Tầng 2.
5. Ứng dụng nghiệp vụ qua E2E.
6. Các phần ở mức "nên có".
7. Bản phát hành, giấy phép, SBOM, tài liệu, triển khai công khai, rồi gửi bài; vài ngày cuối chỉ dành cho bước này.

Các giả định ở mục [Chưa xác minh](#chưa-xác-minh) được kiểm ngay trong bước 2 và 3, mỗi giả định có tiêu chí đạt rõ ràng.
Việc react-admin dựng form và danh sách từ metadata được kiểm song song với bước 2.
Bản công khai trên VPS giữ `v1.1.1` cho đến khi lõi mới đạt và được triển khai lại.

### Những gì giữ lại

- **Hạ tầng:** Keycloak, APISIX, SeaweedFS, PostgreSQL, ClamAV; thông báo chat đi qua webhook dạng `chat`, báo cáo dựng lại theo mô hình khi đến phần đó, cấu hình Superset và Mattermost của bản cũ ở tag `v1.1.1`.
- **Dịch vụ:** `dx-monitor`.
- **Dự án:** bộ triển khai, CI, kiểm tra giấy phép, SBOM, quy trình phát hành.
- **`dx-core` rút gọn thành thư viện:** giữ bảo mật, audit, lưu trữ và quét tệp, trạng thái hệ thống, client Keycloak; phần gắn với ứng dụng cũ như workflow viết tay, workspace, thông báo cũ được gỡ và dựng lại theo thiết kế mới khi cần.
- **Module backend sau khi dọn:** `dx-model`, `dx-core`, `dx-engine` (dịch vụ chạy được) và `dx-monitor`; `dx-core-api` và `dx-host` bỏ vì không còn ứng dụng nào cắm vào lõi.

### Những gì sẽ gỡ

Đã gỡ khỏi nhánh làm việc; mã cũ nằm ở tag `v1.1.1`.


| Phần                                             | Quy mô                                  |
| --------------------------------------------------- | ------------------------------------------ |
| `backend/app-farm`, `backend/app-lab`             | 173 và 94 tệp                          |
| `frontend/src/apps/farm`, `frontend/src/apps/lab` | 17 và 16 tệp                           |
| `mobile/`                                         | 69 tệp                                  |
| Mã nhận diện bệnh lúa, luồng chẩn đoán, trọng số, bộ đánh giá RAG và các script của chúng | Khoảng 230 MB |
| `data/`: dữ liệu Farm, kho văn bản của HaUI, bộ câu hỏi đánh giá | 41 tệp, gỡ toàn bộ |
| `AppModule`, `DXOS_APP`, `dx-core/workflow`       | Thay bằng tệp mô hình và Flowable   |

Các nơi phải sửa theo: `pom.xml` của backend, `backend/Dockerfile`, `AiVectorDatabaseSeeder`, `deploy/demo`, năm workflow CI, SBOM, các README, `ARCHITECTURE.md`, `docs/TESTING.md`.

## Rủi ro

- **Khối lượng:** phạm vi rất chặt với đội 3 người; phần lớn nhất là bộ dựng giao diện và phần điều khiển.
- **Hai nguồn trạng thái:** trạng thái trong Flowable và trong bảng dữ liệu có thể lệch nhau.
- **Bề mặt của API chung:** truy vấn dựng lúc chạy từ mô hình nên chèn SQL, quyền theo dòng sai và hiệu năng đều nằm trong mã tự viết.
- **Điểm hoàn thiện và thân thiện:** giao diện dựng từ metadata dễ trông chung chung, và bản mới ít tính năng đã kiểm thử hơn `v1.1.1`.
- **Đặt cược vòng đời mô hình:** đề không yêu cầu trực tiếp, nên phải giải thích được giá trị trong vài phút.
- **Giới hạn của khai báo:** "không code" chỉ đúng ở mức 1 và 2.

## Khách hàng và giá trị

Chưa nói chuyện với khách hàng nào, nên phần này là giả thuyết.
Đến chung kết, giám khảo là khách hàng số một; phần này phục vụ tầm nhìn sau cuộc thi.

> Dựng hệ thống hồ sơ có quy trình cho một tổ chức trong vài giờ, chạy trên máy chủ của mình, và đổi được khi nghiệp vụ đổi mà không sợ hỏng dữ liệu.

- **Người được phục vụ:** người triển khai như công ty phần mềm nhỏ, phòng CNTT, tư vấn viên; người quản lý nghiệp vụ; người dùng cuối.
- **Chưa phục vụ:** tổ chức không có ai vận hành máy chủ, lập trình viên muốn toàn quyền kiểm soát mã, doanh nghiệp cần ERP đầy đủ.
- **Đối thủ thật:** bảng tính và thuê làm riêng; với người muốn trọn gói tự cài thì là Frappe.
- **Lý do chọn:** thay đổi an toàn trên toàn hệ thống, dữ liệu ở máy chủ của mình, không tính phí theo đầu người.
- **Pháp lý:** [Luật Bảo vệ dữ liệu cá nhân](https://digitalpolicyalert.org/event/36569-implementing-regulations-of-law-on-personal-data-protection-including-cross-border-data-transfer-regulation-entered-into-force) có hiệu lực từ 2026-01-01 và [theo nguồn thứ cấp](https://www.uniconsent.com/vietnam-pdpl) yêu cầu đánh giá khi chuyển dữ liệu ra nước ngoài; cần đọc văn bản gốc.
- **Đo công sức:** so số dòng và thời gian dựng ứng dụng mới với phần Lab viết tay, gồm 5.792 dòng Java, 4.524 dòng giao diện và 8 migration.

Mục tiêu đề xuất, chưa đo được:


| Chỉ số                                                       | Mục tiêu                        |
| ---------------------------------------------------------------- | ----------------------------------- |
| Từ tệp mô hình đến hệ thống chạy, máy đã có image | Dưới 10 phút                   |
| Người mới tự dựng xong hệ thống đầu tiên             | Trong một buổi                  |
| Đổi một trường và áp dụng                              | Vài phút, không mất dữ liệu |
| Mã tự viết cho một hệ thống thường gặp                | Bằng 0 ở mức 1 và 2           |

Giá tham khảo cho 100 người dùng trong một năm, từ các trang tổng hợp giá nên cần đối chiếu với nhà cung cấp:


| Lựa chọn                                                                                                              | Cách tính                                           | Khoảng một năm               |
| ------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------- | --------------------------------- |
| [AppSheet Core](https://www.capterra.com/p/149367/AppSheet/pricing/)                                                    | 10 USD một người một tháng                       | 12.000 USD                      |
| [Power Apps Premium](https://www.licensingschool.co.uk/licensing-blog/2026/09/17/new-pricing-for-power-apps-premium/)   | 20 USD, tăng thêm 2 USD từ 2027-01-01              | 24.000 USD, 26.400 USD từ 2027 |
| [Retool Business](https://www.jetadmin.io/blog/retool-pricing-explained-2026-guide-to-plans-user-types-and-total-cost/) | 3 người dựng ở 50 USD, 97 người dùng ở 15 USD | 19.260 USD                      |
| Lõi tự cài                                                                                                           | Tiền máy chủ cộng công người vận hành        | Chưa có số                   |

## Cam kết nguồn mở

Mọi thành phần của bản phát hành phải mở: mã, công cụ, mô hình AI và dữ liệu.
Thể lệ chỉ bắt buộc giấy phép OSI cho sản phẩm; cam kết của đội chặt hơn thể lệ.

### Tiêu chuẩn


| Loại                          | Đạt khi                                                                                                                                                                 | Không chấp nhận                                                      |
| -------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Mã và dịch vụ              | Giấy phép được OSI công nhận, chỉ dùng phần có giấy phép đó                                                                                                | BUSL, SSPL, RSAL, MSCL; tính năng trong thư mục hay bản Enterprise |
| Công cụ build và kiểm thử | Như trên                                                                                                                                                                | Công cụ nguồn đóng                                                 |
| Mô hình AI                   | Trọng số theo giấy phép OSI, dữ liệu huấn luyện và cách huấn luyện được công khai theo [định nghĩa Open Source AI của OSI](https://opensource.org/ai) | Dữ liệu huấn luyện có điều khoản phi thương mại              |
| Dữ liệu                      | CC0, CC BY, ODbL, hoặc không thuộc diện bảo hộ quyền tác giả                                                                                                     | "Công khai trên mạng" nhưng giữ toàn quyền                       |
| Dịch vụ ngoài               | Không bắt buộc khi chạy                                                                                                                                               | Sản phẩm phụ thuộc dịch vụ đóng                                 |

Nơi lưu mã và máy chủ demo như GitHub, VPS, Cloudflare không thuộc sản phẩm; được dùng nhưng phải tùy chọn và công bố rõ.

### Kết quả rà soát

Rà soát ngày 2026-11-02 từ `DEPENDENCIES.txt`, GitHub API và Hugging Face; cột kết luận cập nhật ngày 2026-11-03 sau khi xử lý ở Phase 10.


| Thành phần                                                      | Giấy phép                                                                                                                                                                                                                                                    | Kết luận                                                        |
| ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| Keycloak, APISIX, Superset, SeaweedFS, Flowable, pgschema         | Apache-2.0                                                                                                                                                                                                                                                     | Đạt                                                             |
| react-admin | MIT | Đạt; chỉ dùng phần MIT, không dùng gói Enterprise |
| PostgreSQL                                                        | PostgreSQL License                                                                                                                                                                                                                                             | Đạt                                                             |
| ClamAV                                                            | GPL-2.0                                                                                                                                                                                                                                                        | Đạt; điều khoản của kho chữ ký mã độc cần kiểm thêm |
| Mattermost Team Edition | [Tách MIT cho bản build và AGPL cho mã nguồn, đang bị tranh cãi](https://docs.mattermost.com/product-overview/faq-license.html); [bản 11 giới hạn 250 người dùng](https://forum.mattermost.com/t/mattermost-v11-changes-in-free-offerings/25126) | **Chưa đạt rõ ràng**; đã gỡ khỏi cây mã |
| Qwen2.5 1.5B | Trọng số Apache-2.0, dữ liệu huấn luyện không công khai theo hiểu biết hiện có | Đã gỡ khỏi bản phát hành |
| Reranker mMARCO | Thẻ mô hình Apache-2.0, nhưng [MS MARCO chỉ cho nghiên cứu phi thương mại](https://microsoft.github.io/msmarco/Notice) và mMARCO dựa trên bộ này | Đã gỡ; chỉ thêm lại khi có mô hình đạt tiêu chuẩn, sau khi đo chất lượng truy xuất |
| Embedding paraphrase-multilingual-MiniLM | Apache-2.0 | Đã gỡ cùng `ai/`: dữ liệu huấn luyện chưa kiểm theo định nghĩa của OSI |
| Kho văn bản HaUI ở `data/rag/lab` | © Đại học Công nghiệp Hà Nội, theo `DEPENDENCIES.txt` | Đã gỡ khỏi cây mã |
| Redis trong môi trường đánh giá RAG | RSALv2 hoặc SSPLv1 | Đã gỡ cùng bộ đánh giá RAG |
| Langfuse trong môi trường đánh giá RAG | MIT phần lõi, có phần EE riêng | Đã gỡ cùng bộ đánh giá RAG |
| cloudflared | Apache-2.0, nhưng Cloudflare Tunnel và Access là dịch vụ đóng | Ngoài repository: chỉ máy chủ công khai của đội dùng, tệp cấu hình giữ ngoài repository; hệ thống chạy được không cần |

Chẩn đoán ảnh, dữ liệu Farm và dashboard Superset đọc Open-Meteo và OpenFreeMap đã rời bản phát hành.

### Việc cần làm

Bản phát hành lõi hoàn toàn mở làm trước; các việc về AI dưới đây xử lý khi đến phần AI.
Xử lý theo tiến độ của Tầng 1 và Tầng 2:


| Việc                | Khi nào                                                                       | Hướng xử lý                                                                                                                                                                                               |
| ---------------------- | -------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| LLM                  | Khi bắt đầu phần AI, để kịp đo trước khi trình diễn                | Mặc định dùng mô hình đạt tiêu chuẩn; [Apertus 8B](https://huggingface.co/swiss-ai/Apertus-8B-Instruct-2509) (Apache-2.0) và OLMo 2 là ứng viên, cần thử tiếng Việt và tốc độ trên CPU |
| Không có LLM đạt | Sau khi thử LLM | Phần AI chưa làm cho đến khi có mô hình đạt; Qwen chỉ là tùy chọn người dùng tự gắn, ghi rõ là mở trọng số |
| Reranker             | Khi bắt đầu phần AI                                                        | Thay hoặc bỏ, đo lại chất lượng truy xuất                                                                                                                                                             |
| Chat                 | Dịch vụ chat do người triển khai cấp; thông báo đi qua webhook dạng `chat` nên đổi dịch vụ chỉ đổi địa chỉ | [Zulip](https://github.com/zulip/zulip) (Apache-2.0) là ứng viên thay Mattermost, cần kiểm các phụ thuộc của nó                                                                                     |

Mô hình AI mở hoàn toàn có thể trả lời tiếng Việt kém hơn và chậm hơn trên CPU; đây là cái giá của cam kết, cần đo trước khi trình diễn.

Đã xử lý xong ở Phase 10, trước khi phát hành:

| Việc | Kết quả |
|---|---|
| Kho quy chế HaUI | Gỡ khỏi cây mã cùng `data/`; bản cũ ở tag `v1.1.1` |
| Dữ liệu tự tạo | Không còn dữ liệu tự tạo trong cây mã |
| Redis và Langfuse trong đánh giá RAG | Gỡ cùng bộ đánh giá RAG |
| cloudflared và dịch vụ Cloudflare | Overlay đã gỡ khỏi repository; máy chủ công khai giữ tệp riêng ngoài repository |
| Kiểm tự động | `.github/scripts/check_licenses.py` kiểm image, mô hình và tệp trọng số, tài liệu, nén theo `.github/component-catalog.json` |
| `DEPENDENCIES.txt` và SBOM | Cập nhật theo thành phần của bản `2.0.0` |

## Công cụ

Số sao và giấy phép lấy từ GitHub API ngày 2026-11-02, trừ khi có ghi khác; các dòng của bảng điều khiển lấy ngày 2026-11-03.


| Vai trò                  | Dự án                                                                     | Giấy phép                    | Sao                | Ghi chú                                                                              |
| --------------------------- | ----------------------------------------------------------------------------- | -------------------------------- | -------------------- | --------------------------------------------------------------------------------------- |
| Luồng công việc        | [Flowable](https://github.com/flowable/flowable-engine)                     | Apache-2.0                     | 9.632              | Có BPMN, CMMN, DMN; bản 8.0.0 ngày 2026-02-27                                      |
| Giao diện                | [react-admin](https://github.com/marmelab/react-admin)                      | MIT                            | 26.979             | Có sẵn field và input cho các kiểu trường của mô hình; chỉ dùng phần MIT |
| Giao diện, dự phòng    | [Refine](https://github.com/refinedev/refine)                               | MIT                            | 35.977             | Dùng nếu react-admin không dựng được theo metadata đổi lúc chạy            |
| Soạn mô hình | [Monaco Editor](https://github.com/microsoft/monaco-editor) | MIT | 46.944 | Trình soạn mã, có chế độ so sánh dùng cho màn hình kế hoạch |
| Soạn mô hình | [monaco-yaml](https://github.com/remcohaszing/monaco-yaml) | MIT | 333 | Gợi ý và báo lỗi YAML theo JSON Schema |
| Sơ đồ quy trình | [React Flow](https://github.com/xyflow/xyflow) | MIT | 38.874 | Vẽ quy trình sinh ra, chỉ để xem |
| Đổi lược đồ             | [pgschema](https://pkg.go.dev/github.com/pgplex/pgschema@v1.13.4)            | Apache-2.0                     | 1.077              | So sánh và đổi lược đồ kiểu Terraform; lõi lo đổi tên và chặn mất dữ liệu |

## Cần quyết khi thiết kế chi tiết

- **Đặc tả định dạng mô hình:** cú pháp đầy đủ, cách khai báo tên cũ, nhiều thực thể trong một tệp, quan hệ giữa các thực thể.
- **Tham số của từng họ mẫu:** danh sách tham số, giá trị mặc định và các khối dùng chung; làm ở các phase sau.
- **Mượn trả:** xác nhận bằng một mô hình mượn thiết bị thật rằng họ chiếm dụng tài nguyên theo thời gian đủ dùng; nếu cần nhiều ngoại lệ thì tách thành họ riêng.
- **Họ mẫu tùy biến:** các khối engine cung cấp cho tệp BPMN, cách kiểm tệp khi áp dụng, và phạm vi của giao diện mở rộng từ mẫu.
- **Quy trình trong kế hoạch áp dụng:** cách lưu phiên bản mô hình trên bản ghi và khi nào cần định nghĩa quy trình mới.
- **Quyền với phần điều khiển:** ai được xem kế hoạch, ai được áp dụng, ai được xác nhận thay đổi mất dữ liệu.
- **Hợp đồng metadata:** Tầng 2 đọc metadata từ API nào và ở dạng gì.
- **Hợp đồng API chung:** đường dẫn, bộ lọc, phân trang, quan hệ giữa các thực thể và định dạng lỗi.
- **Luồng tệp đính kèm:** tải lên, quét và gắn tệp vào hồ sơ qua API nào.
- **Đơn vị và giao việc:** đơn vị lấy từ nhóm Keycloak hay khai báo trong mô hình, hồ sơ được giao tự động hay do người điều phối.
- **Yêu cầu bổ sung:** trường nào mở lại để sửa và hạn xử lý tính thế nào khi hồ sơ bị trả lại.

## Đã xác minh

Cách kiểm ở [`docs/TESTING.md`](../TESTING.md); kết quả mỗi lần chạy ở tab Actions:

- Flowable 8.0.0 chạy nhúng trên Spring Boot 4.1 bằng `flowable-spring` cấu hình tay, dùng chung giao dịch với việc ghi hồ sơ.
- Chỉ cho dịch vụ lõi ghi, cùng Flowable nhúng, đủ để trạng thái không lệch: lỗi cố ý sau bước quy trình làm hồ sơ và quy trình cùng quay về.
- API chung dựng truy vấn lúc chạy: 10.000 dòng có bộ lọc và sắp xếp trả về ở phân vị 95 là 11 ms; giá trị, tên trường và toán tử có SQL không có tác dụng.
- Quyền theo dòng cho vai `own` qua nhiều bước tham chiếu về thực thể đầu chuỗi `owner_via`; chuỗi quay vòng bị từ chối khi kiểm tệp mô hình.
- react-admin dựng form và danh sách từ metadata cho kiểu văn bản, số, ngày giờ, lựa chọn và tham chiếu; mô hình thứ hai nạp lúc chạy không cần build lại giao diện.
- pgschema đưa lược đồ về trạng thái đích khi đã có dữ liệu, với điều kiện lõi đổi tên cột, ràng buộc và chỉ mục trước; tự nó biến đổi tên thành xóa rồi thêm, nên lõi chặn mọi thao tác xóa mà mô hình không yêu cầu.
- Kế hoạch xem trước khớp với áp dụng thật khi có đổi tên, và áp dụng lại sau lỗi giữa chừng về đúng trạng thái đích; kết quả thử ở [`docs/phase-7-model-change/04_TRADEOFFS_RISKS.md`](../phase-7-model-change/04_TRADEOFFS_RISKS.md).
- Hành động hoàn tất (`add_to_group`, `remove_from_group`) đi qua bảng outbox và được thử lại tới khi Keycloak nhận, người triển khai xem lỗi và thử lại ở bảng điều khiển; tệp đính kèm tải lên và tải xuống qua gateway, tải về dạng đính kèm, và đối tượng trong kho bị xóa khi tệp hoặc hồ sơ bị gỡ; các script kiểm ở [`docs/TESTING.md`](../TESTING.md), mục 4.
- Webhook theo mô hình gửi đúng đích kèm chữ ký; các dòng của một hồ sơ tới theo thứ tự, dòng sau chờ dòng cũ đã dừng; đích dạng `chat` chỉ gửi một câu không có nội dung hồ sơ; dòng nhắc hạn đến đúng một lần; các script kiểm ở [`docs/TESTING.md`](../TESTING.md), mục 4.
- `ProcessMigrationService` của Flowable chuyển được hồ sơ đang chạy sang mẫu mới và báo trước khi mẫu mới bỏ bước hiện tại; kết quả thử ở [`docs/phase-6-case-core/04_TRADEOFFS_RISKS.md`](../phase-6-case-core/04_TRADEOFFS_RISKS.md).
- Bản `2.0.0` dựng từ một bản sao sạch của cây mã: stack lên đủ, chuỗi `check_*.py` đạt, `clean verify`, `tsc`, `check_licenses.py` và `check_routes.py` đạt; các script kiểm ở [`docs/TESTING.md`](../TESTING.md), mục 4.

## Chưa xác minh

- Chất lượng tiếng Việt và tốc độ trên CPU của Apertus 8B, OLMo 2.
- Các phụ thuộc và khả năng nối OIDC, webhook của Zulip.
- Đích dạng `chat` với Mattermost và Slack thật.
- Phông chữ, biểu tượng và tài nguyên tĩnh của giao diện.
- Chi tiết các ý tưởng lấy từ Frappe.
- Mức tài nguyên của toàn bộ stack.

## Câu hỏi cho BTC

Có thể hỏi trên [nhóm Telegram của BTC](https://t.me/+YUkj6aM3bnYyOWQ1):

1. Màn hình quản trị đi kèm dịch vụ nguồn mở, như Keycloak Admin, có bị coi là lõi có giao diện không?
2. Ứng dụng minh họa ở Tầng 2 có cần phủ cả bốn không gian H, P, D, I không?
