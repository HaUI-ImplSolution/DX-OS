# Đối chiếu giáo trình DX-OS với lõi

Tài liệu này đối chiếu kiến trúc logic của giáo trình DX-OS với lõi đang chạy, không dùng tên sản phẩm trong giáo trình làm danh sách dependency bắt buộc.
Giáo trình được đọc tại commit [`a955d4ab`](https://github.com/tanhtanhvn/DX-OS/tree/a955d4ab3ca58b350e3f5eea9790e8c6f3575dc7), gồm toàn bộ 11 chương, phần mở đầu, Hộp cát DX-Lab và phần chia sẻ tri thức.
Lõi được đối chiếu tại commit [`abc07089`](https://github.com/HaUI-ImplSolution/DX-OS/tree/abc07089135b08e1c33adc6bf740852b275d74bb) của `Khuong/main`.

## 1. Cách đọc hai kiến trúc

Giáo trình mô tả một phương pháp chuyển đổi số cho tổ chức, đi từ kiến trúc doanh nghiệp, kỷ luật vận hành và H-P-D-I đến triển khai, bảo mật và hoàn vốn.
DX-Lab là hộp cát minh họa phương pháp đó bằng Google Workspace, AppSheet, Apps Script, n8n, Looker Studio, NotebookLM và Gemini.
DX-Ticket là luồng yêu cầu khách hàng xuyên suốt hộp cát, không phải định nghĩa bắt buộc của một lõi DX-OS.

Repository này xây một lõi tự cài, trung lập nghiệp vụ.
Tệp mô hình khai báo thực thể, vai, quyền và quy trình; lõi sinh cơ sở dữ liệu, API, giao diện và rào chắn dùng chung.
Vì vậy, đối chiếu đúng là so năng lực và hợp đồng, không đòi cùng công cụ hay cùng tên trường.

Các mức dưới đây có nghĩa:

- **Phù hợp:** lõi đã có hợp đồng tương ứng.
- **Khác cách làm:** cùng mục tiêu nhưng dùng kiến trúc khác.
- **Điểm mở rộng:** thuộc ứng dụng hoặc dịch vụ ngoài lõi.
- **Còn thiếu:** phù hợp hướng sản phẩm nhưng chưa có.
- **Ngoài phạm vi lõi:** là phương pháp quản trị hoặc việc của tổ chức triển khai.

## 2. Ánh xạ thuật ngữ và kiến trúc

| Giáo trình | Ý nghĩa | DX-OS hiện tại | Kết luận |
|---|---|---|---|
| Kiến trúc doanh nghiệp và luồng giá trị | Căn chỉnh nghiệp vụ, dữ liệu, ứng dụng và công nghệ theo một luồng có giá trị | Tệp mô hình giữ nghiệp vụ; PostgreSQL giữ dữ liệu; API và giao diện đọc cùng metadata; hạ tầng nằm ở `deploy/` | Phù hợp ở phạm vi một hệ thống hồ sơ; lõi không thay công cụ lập kiến trúc doanh nghiệp |
| H-P-D-I | Chuyển quyền điều khiển từ con người sang quy trình, dữ liệu và trí tuệ, nhưng vẫn giữ con người trong vòng quyết định | H có SSO và hộp việc; P có quy trình và rào chắn; D có dữ liệu, lịch sử, báo cáo và danh mục; I chưa có | H, P và D phù hợp; I còn thiếu và đã được theo dõi ở #210, #211 |
| DTI và radar HPDI | Đánh giá mức trưởng thành số của cả tổ chức | Không có bộ khảo sát hoặc thuật toán DTI; `docs/TARGETS.md` và `docs/SIZING.md` chỉ đo sản phẩm | Ngoài phạm vi lõi; không được dùng số đo kỹ thuật thay cho DTI tổ chức |
| 5 RÕ, RACI, SMART | Làm rõ vai, trách nhiệm, quy trình, tiêu chuẩn và công cụ trước khi số hóa | Vai, quyền theo dòng, người xử lý, trạng thái, hành động, hạn và ràng buộc nằm trong tệp mô hình | Phù hợp về khả năng thực thi; KPI, OKR và việc thiết kế tổ chức vẫn do người triển khai quyết định |
| Poka-Yoke | Chặn thao tác sai tại giao diện và nơi lưu dữ liệu | API kiểm trường và quy trình; PostgreSQL giữ các ràng buộc có thể biên dịch; trạng thái chỉ đổi qua Flowable | Phù hợp và mạnh hơn kiểm tra chỉ ở máy khách |
| Kiến trúc lắp ghép | Tách công cụ và nối qua API để thay từng phần khi chạm ngưỡng | Lõi headless, OpenAPI theo mô hình, OIDC/PKCE, client máy, webhook và các điểm mở rộng | Phù hợp; thay thành phần lõi vẫn phải theo hợp đồng tương thích của dự án |
| Một nguồn sự thật | Không nhập lại và không có nhiều bản dữ liệu cạnh tranh | Dữ liệu nghiệp vụ, lịch sử quy trình và outbox nằm trong PostgreSQL; backend là kết nối ghi duy nhất | Phù hợp trong phạm vi lõi |
| SSO và RBAC | Một định danh, vai và thu hồi quyền tập trung | Keycloak giữ tài khoản; vai theo từng mô hình; đơn vị dùng chung; gateway và backend cùng kiểm token | Phù hợp; quyền đang có hiệu lực có thể chậm tối đa tuổi token |
| Dữ liệu phẳng và quan hệ | Trường có kiểu, khóa và quan hệ một-nhiều thay cho bảng trình bày | Mỗi thực thể thành bảng; trường `reference` tạo quan hệ trong cùng mô hình; API kiểm kiểu và quy tắc | Phù hợp, không phụ thuộc bảng tính |
| Kiến trúc hướng sự kiện | Một thay đổi kích hoạt thông báo và tự động hóa mà không ghi lặp | Transactional outbox, webhook có chữ ký, gửi lại, thứ tự theo hồ sơ, API đọc bù sự kiện và `Idempotency-Key` | Phù hợp; PostgreSQL outbox thay cho message broker hoặc Apps Script trigger |
| Danh mục, phả hệ và chất lượng dữ liệu | Biết dữ liệu ở đâu, từ đâu đến, đi đâu và còn đáng tin không | API và tab Danh mục dữ liệu mô tả mô hình, quan hệ, nguồn/đích từng trường, độ đầy đủ, vi phạm và độ mới | Phù hợp trong phạm vi dữ liệu do mô hình quản lý |
| Dashboard và DSS | Điều hành bằng số liệu thay cho báo cáo thủ công | API `stats`, `report`, CSV và giao diện thống kê áp quyền theo dòng; `report_ro` cho BI tin cậy | Phù hợp về nguồn dữ liệu; dashboard chuyên ngành là cấu hình của ứng dụng hoặc công cụ BI |
| Snapshot và vùng lưu trữ lịch sử | Đóng băng dữ liệu theo kỳ để truy vết và phân tích | `dxos archive` xuất hồ sơ đã kết thúc cùng lịch sử ra Parquet, manifest và SHA-256; DuckDB truy vấn bản lưu | Khác cách làm nhưng cùng mục tiêu, với bằng chứng toàn vẹn rõ hơn CSV định kỳ |
| Data Lake, Lakehouse, ELT, MDM và Data Fabric | Mở rộng phân tích khi dữ liệu nhiều nguồn và khối lượng lớn | Có kho Parquet của dữ liệu lõi và truy vấn DuckDB; chưa có ELT đa nguồn, MDM, lakehouse hay data fabric | Ngoài phạm vi lõi hiện tại; chỉ trở thành backlog khi có yêu cầu tích hợp cụ thể |
| Không gian tri thức và P.A.R.A | Quản lý tài liệu, SOP và tri thức hiện của tổ chức | Repository có tài liệu sản phẩm; tệp nghiệp vụ có thể gắn vào hồ sơ; không có DMS, wiki hay P.A.R.A tích hợp | Điểm mở rộng, không nên đưa cấu trúc thư mục của một tổ chức vào lõi |
| AI có nguồn và tác tử có người duyệt | RAG trên tri thức nội bộ, đề xuất có dẫn nguồn rồi con người xác nhận | Chưa có thành phần AI trong bản hiện tại; hướng thiết kế yêu cầu mô hình và dữ liệu đạt cam kết nguồn mở | Còn thiếu; không dùng NotebookLM hoặc Gemini làm dependency bắt buộc vì đó là dịch vụ đóng |
| Zero Trust và an toàn dữ liệu | Xác minh mỗi lần truy cập, tối thiểu quyền, MFA, bảo vệ tệp và dữ liệu cá nhân | Token được kiểm ở gateway và backend; quyền theo dòng; TOTP cho ADMIN; ClamAV; link tệp có hạn; rate limit và audit | Phù hợp ở lớp nền tảng; đồng ý xử lý, phân loại PII và masking phụ thuộc mô hình và bản triển khai |
| GitOps, sao lưu và phục hồi | Cấu hình có phiên bản, tự phát hiện lệch và phục hồi có kiểm soát | `dxos gitops`, plan/apply/rollback, `dxos reconcile`, sao lưu mã hóa và lệnh kiểm khôi phục | Phù hợp và cụ thể hơn mức hướng dẫn của giáo trình |
| Quản trị thay đổi, ROI và 5S Số | Chọn luồng trọng yếu, đào tạo, duy trì kỷ luật và chỉ mở rộng khi có số đo | Tài liệu có bài thử, số đo công sức, sizing và chi phí tham khảo; lõi không quản trị hành vi tổ chức | Ngoài phạm vi lõi; đây là trách nhiệm của đội triển khai và chủ quy trình |

Các hợp đồng trên được mô tả trong [`ARCHITECTURE.md`](../../ARCHITECTURE.md), [`MODEL.md`](../MODEL.md), [`INTEGRATIONS.md`](../INTEGRATIONS.md), [`REPORTING.md`](../REPORTING.md) và [`OPERATIONS.md`](../OPERATIONS.md).

## 3. Đối chiếu DX-Ticket

Giáo trình dùng DX-Ticket làm MVP quản lý yêu cầu khách hàng.
Nguồn chính là [mục 6.2](https://github.com/tanhtanhvn/DX-OS/blob/a955d4ab3ca58b350e3f5eea9790e8c6f3575dc7/phan-ii-xay-dung-he-dieu-hanh/chuong-6-p-process-chuan-hoa-khong-gian-nghiep-vu-so-and-tu-dong-hoa-quy-trinh/6.2.-dac-ta-tang-luu-tru-backend-thiet-lap-mo-hinh-du-lieu-quan-he.md), [mục 6.5](https://github.com/tanhtanhvn/DX-OS/blob/a955d4ab3ca58b350e3f5eea9790e8c6f3575dc7/phan-ii-xay-dung-he-dieu-hanh/chuong-6-p-process-chuan-hoa-khong-gian-nghiep-vu-so-and-tu-dong-hoa-quy-trinh/6.5.-truc-tu-dong-hoa-da-kenh-dong-bo-quy-trinh-voi-apps-script-va-n8n.md) và [bài nâng cấp 6.8](https://github.com/tanhtanhvn/DX-OS/blob/a955d4ab3ca58b350e3f5eea9790e8c6f3575dc7/phan-ii-xay-dung-he-dieu-hanh/chuong-6-p-process-chuan-hoa-khong-gian-nghiep-vu-so-and-tu-dong-hoa-quy-trinh/6.8.-thuc-hanh-dx-lab-nang-cap-kien-truc-du-lieu-va-thiet-lap-rao-chan-ky-thuat-nang-cao.md).

| Năng lực DX-Ticket | Cách của giáo trình | Cách trên lõi | Kết luận |
|---|---|---|---|
| Khách hàng và yêu cầu | `CUSTOMERS` 1:N `TICKETS`, tự tìm hoặc tạo khách hàng bằng số điện thoại | Hai thực thể và trường `reference` biểu diễn được quan hệ; tự khớp hoặc upsert cần dịch vụ ngoài | Mô hình dữ liệu phù hợp; khớp khách hàng là điểm mở rộng |
| Tiếp nhận nội bộ | Nhân viên tạo yêu cầu trong AppSheet | Người có vai tạo hồ sơ qua giao diện chung hoặc API | Phù hợp |
| Tiếp nhận công khai | Google Form không yêu cầu tài khoản đẩy dữ liệu qua Apps Script | API nghiệp vụ yêu cầu token; biểu mẫu công khai phải qua dịch vụ ngoài có kiểm tra, chống lạm dụng và một vai hẹp | Điểm mở rộng; không mở thẳng API ghi ẩn danh |
| Trường ticket | Loại yêu cầu, nội dung, ảnh, ưu tiên, người phụ trách, hướng xử lý và các mốc thời gian | `text`, `choice`, `file`, `user`, trường hệ thống và mã hồ sơ khai báo được trong YAML | Phù hợp |
| Trạng thái | Chờ xử lý → Đang xử lý → Đóng | Họ `work_item` có giao, nhận, bắt đầu, giải quyết, đánh giá và đóng; cũng có thể tự khai báo trạng thái và hành động | Khác tên nhưng bao phủ luồng và tách rõ người xử lý với người xác nhận |
| Phân quyền cấp dòng | Ai cũng thấy việc chờ nhận; sau khi nhận chỉ người phụ trách thấy | `own`, `all`, `unit`, `assigned` và kiểm người được giao khi thực hiện hành động | Phù hợp; cách hiển thị việc chưa giao tùy quyền của vai xử lý |
| Rào chắn hoàn tất | Không được nhận việc khi thiếu người phụ trách; không được đóng khi thiếu hướng xử lý | Giao việc ghi người xử lý; `require` bắt buộc kết quả và nguyên nhân trước `resolve`, điểm đánh giá trước `confirm` | Phù hợp và thực thi ở API/quy trình, không chỉ ở giao diện |
| SLA và leo thang | Tính thời gian từ nhận đến đóng, lọc yêu cầu trễ | `deadline`, `handle_by`, `due_soon`, `overdue`, nhiều mức `escalate`, thống kê đúng hạn và trễ | Phù hợp và rộng hơn ví dụ DX-Ticket |
| Email và Telegram | Apps Script gửi email; n8n chuyển cảnh báo khiếu nại sang Telegram | Mọi dòng lịch sử đi qua webhook có chữ ký và gửi lại; đích chat hỗ trợ Mattermost/Slack | Cùng trục sự kiện; email hoặc Telegram là bên nhận ngoài lõi |
| CSAT | Liên kết công khai nhận `ticket_id` rồi ghi điểm 1–5 | Người nộp đã xác thực thực hiện `confirm` với trường `rating` 1–5 | Phù hợp về nghiệp vụ và an toàn hơn liên kết chỉ mang mã phiếu; khảo sát công khai cần token một lần do dịch vụ ngoài cấp |
| Lịch sử và bằng chứng | Cột cờ chống gửi lặp, snapshot và báo cáo kỳ | Idempotency, outbox, lịch sử nối hash, điểm neo, archive Parquet và mẫu in PDF | Phù hợp và có bảo đảm mạnh hơn |
| Phân tích và AI | Looker theo SLA/CSAT; Gemini đọc CSV và gợi ý hướng xử lý | `stats`, `report`, CSV và `report_ro` đã có; AI chưa có | D phù hợp; I còn thiếu |

Mẫu [`examples/effort-benchmark/support.yaml`](../../examples/effort-benchmark/support.yaml) đã kiểm một lát cắt tương đương: tạo và sửa, quyền xem, giao và bắt đầu, nhập kết quả, đánh giá và đóng, lịch sử và tệp đính kèm.
Kết quả ở [`examples/effort-benchmark/README.md`](../../examples/effort-benchmark/README.md) là 28 dòng YAML thay cho 347 dòng Java trong phạm vi đó, cùng 33 kiểm API và 9 bước giao diện đã đạt tại mốc được ghi trong bài đo.
Kết quả này chứng minh khả năng biểu diễn luồng cơ bản, không chứng minh lõi là bản sao đầy đủ của DX-Ticket trong giáo trình.

## 4. Kết luận và việc còn lại

Giáo trình và lõi thống nhất ở kiến trúc lắp ghép, SSO/RBAC, quy trình có rào chắn, dữ liệu làm nguồn sự thật, sự kiện, báo cáo, bảo vệ dữ liệu và mở rộng theo số đo.
Lõi hiện đi xa hơn hộp cát ở thay đổi mô hình an toàn, quyền và ràng buộc phía máy chủ, sự kiện tin cậy, dấu vết chống sửa, lưu trữ Parquet và GitOps.

Ba nhóm không nên đưa vào lõi chỉ để giống giáo trình:

- Công cụ đóng cụ thể như Google Workspace, AppSheet, Looker Studio, NotebookLM và Gemini.
- Phương pháp quản trị tổ chức như DTI, RACI, KPI/OKR, P.A.R.A, quản trị thay đổi, ROI và 5S Số.
- Logic riêng của DX-Ticket như tự upsert khách hàng theo số điện thoại, biểu mẫu công khai, email và Telegram; các phần này dùng tệp mô hình hoặc điểm mở rộng.

Khoảng trống sản phẩm duy nhất mà đối chiếu này xác nhận là không gian I.
Nó đã có issue #210 cho trợ lý AI mở và #211 để xác minh yêu cầu phủ H-P-D-I, nên không tạo backlog trùng.
Những năng lực dữ liệu lớn như ELT, MDM, lakehouse và data fabric chỉ nên được đề xuất khi xuất hiện nhu cầu đa nguồn cụ thể.
