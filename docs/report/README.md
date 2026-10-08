# Sườn báo cáo kỹ thuật (8-10 trang)

Sườn của báo cáo kỹ thuật nộp theo mục 10 của đề.
Báo cáo đi theo mạch: vấn đề, câu hỏi nghiên cứu, phương pháp, giải pháp, kiểm chứng, giới hạn.
Mỗi mục ghi số trang dự kiến, nội dung cần viết, hình và bảng, và nơi lấy số liệu trong repository.

## Quy ước viết

- Khổ giấy, cỡ chữ và cách tính số trang theo quy định trình bày của đề.
- Mỗi khẳng định có một bằng chứng kiểm tra được: số đo, log CI, hoặc đường dẫn tới tệp trong repository.
- Số liệu ghi kèm ngày đo và nguồn; số do người khác cung cấp ghi rõ là của ai.
- Gọi tên khái niệm trong thân bài; tên tệp, lệnh và đường dẫn để ở chú thích hoặc phụ lục.

## Phân bổ trang

| Mục | Trang |
|---|---|
| Tóm tắt | 0,25 |
| 1. Đặt vấn đề | 0,5 |
| 2. Mục tiêu, câu hỏi nghiên cứu và phạm vi | 0,5 |
| 3. Cơ sở lý thuyết và phương pháp | 0,75 |
| 4. Kiến trúc Open-Core ba tầng | 1,5 |
| 5. Dịch vụ dùng chung và vận hành | 1 |
| 6. H-P-D-I qua hai ứng dụng | 2 |
| 7. Trí tuệ nhân tạo | 1 |
| 8. Kiểm thử và kiểm chứng | 1 |
| 9. Mã nguồn mở và giấy phép | 0,75 |
| 10. Hạn chế và hướng phát triển | 0,5 |
| 11. Kết luận | 0,25 |
| Tổng | 10 |

## Nội dung từng mục

### Trang bìa và tóm tắt

- Tên đề tài, đội, thành viên, giảng viên hướng dẫn, đường dẫn repository và site công khai.
- Tóm tắt khoảng 150 từ: vấn đề, giải pháp một lõi hai ứng dụng, ba kết quả chính kèm số, một giới hạn lớn nhất.

### 1. Đặt vấn đề

- Đơn vị đào tạo vận hành bằng email, bảng tính và sổ tay rời rạc; mỗi phần mềm tự làm lại đăng nhập, phân quyền, lưu trữ và phê duyệt.
- Hệ quả: dữ liệu phân mảnh, quy trình không truy vết được, chi phí lặp lại.
- Đề thi yêu cầu xây Open-Core trước, ứng dụng sau; nhóm coi đây là bài toán kiến trúc.

Nguồn: đề thi ở [`../reference/`](../reference/), [`../../README.md`](../../README.md) phần tổng quan.

### 2. Mục tiêu, câu hỏi nghiên cứu và phạm vi

- Mục tiêu: một lõi dịch vụ headless, dùng lại được cho nhiều nghiệp vụ.
- Ba câu hỏi nghiên cứu: CH1 lõi đứng yên khi ứng dụng thay đổi; CH2 lõi đủ H-P-D-I cho hai miền; CH3 AI giữ trách nhiệm giải trình của con người.
- Phạm vi: DX-Lab cho phòng máy và phòng thí nghiệm của khoa (theo đề); DX-Farm cho quản lý dịch hại lúa (miền kiểm chứng tính tổng quát).
- Người dùng của từng ứng dụng và luồng chính của mỗi bên.

Bảng: vai người dùng của hai ứng dụng và việc mỗi vai làm.

### 3. Cơ sở lý thuyết và phương pháp

- Mô hình Open-Core và nền tảng dịch vụ.
- Khung năng lực H-P-D-I của đề.
- Poka-yoke (Shingo, 1986): làm cho thao tác sai không thể xảy ra.
- Con người trong vòng lặp: AI gợi ý, chuyên gia quyết định.
- RAG (Lewis và cộng sự, 2020) và truy xuất lai: BM25, vector, gộp RRF, xếp hạng lại.
- Phương pháp nghiên cứu khoa học thiết kế (Hevner và cộng sự, 2004): mỗi câu hỏi nghiên cứu trả lời bằng một bằng chứng ở mục 8.

### 4. Kiến trúc Open-Core ba tầng

- Sơ đồ ba tầng đọc từ dưới lên: lõi headless, không gian năng lực H-P-D-I, hai ứng dụng.
- Các module và ranh giới biên dịch: ứng dụng chỉ thấy giao diện lập trình công khai của lõi, CI kiểm luật phân lớp.
- Những gì ứng dụng khai báo: vai, loại quy trình, loại thông báo, kho tri thức, route của cổng API.
- Chọn ứng dụng lúc chạy bằng một biến cấu hình; hạ tầng dùng chung không khởi động lại.
- Luồng một request từ người dùng qua cổng API, máy chủ nghiệp vụ, cơ sở dữ liệu và AI; quyền kiểm hai lần.

Hình: sơ đồ ba tầng ([`../images/so_do_3_tang.png`](../images/so_do_3_tang.png)); sơ đồ luồng request của một ứng dụng.
Nguồn: [`../../ARCHITECTURE.md`](../../ARCHITECTURE.md) mục 1 đến 7.

### 5. Dịch vụ dùng chung và vận hành

| Dịch vụ | Thành phần | Ứng dụng khai báo gì |
|---|---|---|
| Định danh và đăng nhập một lần | Keycloak, xác thực hai bước, chính sách mật khẩu | Vai người dùng |
| Cổng API | APISIX, giới hạn tốc độ theo địa chỉ | Route của ứng dụng |
| Dữ liệu và tệp | PostgreSQL, SeaweedFS, quét mã độc bằng ClamAV (tùy chọn) | Bảng và view của ứng dụng |
| Quy trình và kiểm toán | Bộ máy quy trình tự xây, sổ kiểm toán chỉ ghi thêm | Loại quy trình và người duyệt |
| Thông báo | Hộp thông báo, Mattermost, gửi lại khi lỗi tạm thời | Loại thông báo |
| Cổng AI | Gọi dịch vụ AI qua cầu dao, che thông tin cá nhân | Kho tri thức |
| Giám sát | Màn hình giám sát chạy tách khỏi máy chủ nghiệp vụ | Không cần khai báo |

- Vận hành: giới hạn số lần gọi theo người dùng, sao lưu mã hóa có lệnh kiểm tra khôi phục, kiểm máy chủ trước khi chạy, cập nhật từ image đã phát hành.
- Triển khai công khai qua Cloudflare Tunnel; trang quản trị đặt sau Cloudflare Access.

Nguồn: [`../../ARCHITECTURE.md`](../../ARCHITECTURE.md) mục 3 và 8, [`../../sso/README.md`](../../sso/README.md), [`../../deploy/OPERATIONS.md`](../../deploy/OPERATIONS.md), [`../../backend/dx-monitor/README.md`](../../backend/dx-monitor/README.md), [`../CLOUDFLARE.md`](../CLOUDFLARE.md).

### 6. H-P-D-I qua hai ứng dụng

Mỗi ứng dụng một bảng bốn dòng H, P, D, I, rồi một đoạn về quy tắc chống sai.

DX-Lab:

- H: bốn vai sinh viên, giảng viên, kỹ thuật viên, quản trị.
- P: yêu cầu hỗ trợ có hạn phản hồi và hạn xử lý; mượn thiết bị duyệt theo cấp; đặt phòng chống trùng giờ ở cơ sở dữ liệu.
- D: thống kê hạn xử lý, tải kỹ thuật viên, mức dùng phòng; view cho BI.
- I: gợi ý loại và độ ưu tiên có giải thích; hỏi đáp trích văn bản của HaUI.

DX-Farm:

- H: nông dân trên di động, kỹ sư và quản trị trên web; nông dân chỉ thấy dữ liệu hộ mình.
- P: duyệt thửa, báo cáo bệnh, chẩn đoán, khai phun, thu hoạch.
- D: dashboard Superset 11 chart, thời tiết theo thửa, chỉ đọc bản ghi đã duyệt.
- I: chẩn đoán ảnh, tài liệu tham khảo kèm chẩn đoán, hỏi đáp canh tác.

Quy tắc chống sai: khóa thu hoạch khi chưa hết thời gian cách ly, giới hạn GPS, không tự duyệt yêu cầu của mình, hai người xử lý cùng lúc thì một người nhận `409`.

Hình: ảnh chụp màn hình mỗi ứng dụng (chưa có, xem [`../BACKLOG.md`](../BACKLOG.md) mục 1).
Nguồn: [`../../backend/app-lab/README.md`](../../backend/app-lab/README.md), [`../../backend/app-farm/README.md`](../../backend/app-farm/README.md), [`../TESTING.md`](../TESTING.md) mục 1.

### 7. Trí tuệ nhân tạo

| Chức năng | Mô hình, dữ liệu, giấy phép | Kết quả đo | Ai quyết định |
|---|---|---|---|
| Nhận diện bệnh lá lúa | EfficientNetV2-M, RiceLeafDiseaseBD (CC BY 4.0); lọc ảnh không phải lúa bằng crop classifier (AGPL-3.0) | Accuracy 89,43%, macro-F1 88,13% trên 1.466 ảnh (số liệu của người huấn luyện); dưới 0,70 chuyển chuyên gia | Kỹ sư nông nghiệp |
| Hỏi đáp Farm và Lab | RAG lai, rerank mMARCO MiniLM, embedding MiniLM đa ngữ, Qwen2.5-1.5B (Apache-2.0) | Recall@4 Farm 0,52 (cosine) lên 0,65 (lai và xếp hạng lại) trên 72 câu hỏi | Người đọc, có trích nguồn |
| Gợi ý phân loại yêu cầu Lab | Luật từ khóa có giải thích | Chưa đo trên dữ liệu thật | Người gửi yêu cầu |

- Nguyên tắc: thuốc, liều và thời gian cách ly lấy từ dữ liệu có cấu trúc đã duyệt, không do AI sinh.
- Kiểm câu trả lời: câu không đủ căn cứ thì từ chối; nguyên nhân và cách phòng phải có trong nguồn; máy chủ yếu thì trả nguyên văn tài liệu.
- Giới hạn của phép đo: số đo mô hình bệnh chưa độc lập; bộ đề RAG còn nhãn nháp.

Hình: biểu đồ đánh giá RAG ([`../images/ai/`](../images/ai/)).
Nguồn: [`../../ai/README.md`](../../ai/README.md), [`../AI_EVALUATION.md`](../AI_EVALUATION.md) mục 3 và 7 đến 10.

### 8. Kiểm thử và kiểm chứng

Trả lời ba câu hỏi nghiên cứu bằng bảng câu hỏi, bằng chứng, kết quả:

| Câu hỏi | Bằng chứng | Kết quả |
|---|---|---|
| CH1 | Thêm DX-Lab sau DX-Farm; ứng dụng mẫu rỗng theo hướng dẫn đóng góp | 0 dòng thay đổi trong lõi; ứng dụng rỗng khởi động |
| CH2 | Kiểm thử đầu-cuối trên môi trường dựng sạch trong CI | Farm 10/10 bước, Lab 16/16 bước |
| CH3 | Đánh giá mô hình bệnh và truy xuất RAG | Số liệu ở mục 7 |

- Các lớp kiểm thử: test backend với cơ sở dữ liệu thật, test AI, kiểm luật phân lớp, kiểm route công khai của cổng API, quét bí mật và lỗ hổng.
- Kiểm trên máy chủ công khai: cổng API, log, kiểm toán, màn hình giám sát, cấu hình Cloudflare.
- Dựng từ clone sạch theo README: thời gian dựng và kết quả.

Nguồn: [`../TESTING.md`](../TESTING.md) mục 7, 8 và 10, log workflow `E2E` trên GitHub, [`../../deploy/demo/README.md`](../../deploy/demo/README.md).

### 9. Mã nguồn mở và giấy phép

- Giấy phép: mã tự viết Apache-2.0; `ai/` AGPL-3.0 vì dùng mô hình lọc ảnh AGPL, máy chủ nghiệp vụ gọi AI qua HTTP nên không là tác phẩm phái sinh.
- Bảng phụ thuộc và giấy phép, SBOM CycloneDX cho 4 khối, tệp khóa phiên bản, dòng SPDX trên mọi tệp Java và Python tự viết.
- Bốn bản phát hành `v1.0.0` đến `v1.0.3`, image trên GHCR, CHANGELOG theo phiên bản.
- Cộng đồng: hướng dẫn đóng góp và cách thêm ứng dụng mới, quy tắc ứng xử, mẫu issue và pull request, Dependabot.

Nguồn: [`../../COPYRIGHT.txt`](../../COPYRIGHT.txt), [`../../sbom/README.md`](../../sbom/README.md), [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md), [`../../CHANGELOG.md`](../../CHANGELOG.md).

### 10. Hạn chế và hướng phát triển

- Mô hình bệnh chưa đánh giá trên ảnh ruộng thật; crop classifier nhầm ảnh ngô và mía là lúa.
- Luật phân loại của Lab chưa đo trên dữ liệu thật.
- Mới thử tải rút gọn phần truy xuất; dịch vụ AI là một tiến trình.
- Kho tri thức còn nhỏ; phác đồ thuốc được duyệt mới có ba bệnh.
- Máy chủ công khai không có AVX2 nên tắt Qwen; chẩn đoán ảnh trên CPU mất khoảng 50 giây.
- Lab chưa có ứng dụng di động và dashboard BI riêng.
- Hướng mở rộng từ 100 lên 100.000 người dùng.

Nguồn: [`../BACKLOG.md`](../BACKLOG.md) mục 4 đến 8, [`../COUNCIL_QA.md`](../COUNCIL_QA.md) câu 9.

### 11. Kết luận

- Trả lời lại ba câu hỏi nghiên cứu, mỗi câu một câu kèm số.
- Đóng góp chính: một nền tảng mở để đơn vị khác xây tiếp, không phải một ứng dụng.

### Tài liệu tham khảo

- Đề thi Phần mềm nguồn mở HaUI 2026 ([`../reference/`](../reference/)).
- Hevner, A. R., March, S. T., Park, J., Ram, S. (2004). Design Science in Information Systems Research. MIS Quarterly 28(1).
- Lewis, P. và cộng sự (2020). Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks. NeurIPS 2020.
- Shingo, S. (1986). Zero Quality Control: Source Inspection and the Poka-yoke System. Productivity Press.
- Cormack, G. V., Clarke, C. L. A., Büttcher, S. (2009). Reciprocal Rank Fusion Outperforms Condorcet and Individual Rank Learning Methods. SIGIR 2009.
- Tài liệu của các thành phần nguồn mở dùng trong lõi, theo [`../../COPYRIGHT.txt`](../../COPYRIGHT.txt).

### Phụ lục

- A. Hướng dẫn dựng và chạy thử, tài khoản demo, theo [`../../README.md`](../../README.md).
- B. Danh sách API chính, theo [`../../README.md`](../../README.md) mục API.
