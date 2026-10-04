# Sườn slide trình bày (12 slide, 10 phút)

Sườn của bộ slide nộp kèm hồ sơ và dùng khi trình bày trước hội đồng.
Mạch lập luận đi theo một báo cáo khoa học: vấn đề, câu hỏi nghiên cứu, phương pháp, giải pháp, kiểm chứng, giới hạn.
Báo cáo kỹ thuật dùng cùng mạch này, xem [`../report/README.md`](../report/README.md).

## Nguyên tắc

- Mỗi slide một thông điệp; chữ trên slide đọc lướt được trong 5 giây, lời nói không đọc lại chữ trên slide.
- Gọi tên khái niệm thay vì tên tệp hay lệnh.
- Mỗi khẳng định đi kèm một bằng chứng đo được và nơi lấy bằng chứng.
- Giới hạn nói thẳng, không né.
- Demo trực tiếp nằm ở Slide 10; 5 phút hỏi đáp dùng [`../COUNCIL_QA.md`](../COUNCIL_QA.md).

## Phân bổ thời gian

| Slide | Nội dung | Mốc | Thời lượng |
|---|---|---|---|
| 1 | Mở đầu | 0:00–0:30 | 30 giây |
| 2 | Bối cảnh và vấn đề | 0:30–1:15 | 45 giây |
| 3 | Mục tiêu và câu hỏi nghiên cứu | 1:15–1:50 | 35 giây |
| 4 | Cơ sở lý thuyết và phương pháp | 1:50–2:30 | 40 giây |
| 5 | Kiến trúc tổng thể | 2:30–3:30 | 60 giây |
| 6 | DX-Lab: ứng dụng trả lời đề | 3:30–4:15 | 45 giây |
| 7 | DX-Farm: kiểm chứng tính tổng quát | 4:15–4:50 | 35 giây |
| 8 | Trí tuệ nhân tạo và tri thức | 4:50–5:30 | 40 giây |
| 9 | Tính mở | 5:30–6:00 | 30 giây |
| 10 | Minh chứng vận hành (có demo) và đánh giá | 6:00–9:00 | 3 phút |
| 11 | Hạn chế và hướng phát triển | 9:00–9:30 | 30 giây |
| 12 | Kết luận | 9:30–10:00 | 30 giây |

## Nội dung từng slide

### Slide 1 — Mở đầu

- DX-OS: Trạm điều hành số trên nền tảng lõi nguồn mở.
- Một lõi dùng chung, hai ứng dụng: DX-Lab và DX-Farm.
- Tên đội, thành viên, giảng viên hướng dẫn; Phần mềm nguồn mở HaUI 2026.

Thông điệp: trọng tâm là nền tảng dùng chung, không phải một ứng dụng.

### Slide 2 — Bối cảnh và vấn đề

- Đơn vị đào tạo vận hành bằng email, bảng tính, sổ tay rời rạc.
- Mỗi phần mềm tự làm lại đăng nhập, phân quyền, lưu trữ, phê duyệt.
- Hệ quả: dữ liệu phân mảnh, quy trình không truy vết được, chi phí lặp lại.
- Định hướng của đề: Open-Core trước, ứng dụng sau.

Thông điệp: đây là bài toán kiến trúc, không chỉ là bài toán giao diện.

### Slide 3 — Mục tiêu và câu hỏi nghiên cứu

- Mục tiêu: một lõi dịch vụ headless, dùng lại được cho nhiều nghiệp vụ.
- CH1. Lõi có đứng yên khi ứng dụng thay đổi?
- CH2. Lõi có đủ năng lực số hóa H-P-D-I cho hai miền khác nhau?
- CH3. AI tham gia đến đâu mà vẫn giữ trách nhiệm giải trình của con người?
- Phạm vi: phòng thí nghiệm của khoa (theo đề) và quản lý dịch hại lúa (miền kiểm chứng).

### Slide 4 — Cơ sở lý thuyết và phương pháp

| Cơ sở | Áp dụng trong đề tài |
|---|---|
| Mô hình Open-Core và nền tảng dịch vụ | Lõi dùng chung, ứng dụng chỉ khai báo nhu cầu |
| Khung năng lực H-P-D-I | Con người, Quy trình, Dữ liệu, Trí tuệ |
| Poka-yoke, chống sai từ gốc (Shingo, 1986) | Quy tắc an toàn chặn ở máy chủ, không chỉ ẩn nút |
| Con người trong vòng lặp | AI gợi ý, chuyên gia quyết định |
| Truy hồi tri thức tăng cường (RAG, Lewis và cộng sự, 2020) | Truy xuất lai theo từ khóa và theo nghĩa, xếp hạng lại; trả lời có trích nguồn và giấy phép |

Phương pháp: nghiên cứu khoa học thiết kế (Hevner và cộng sự, 2004), xây dựng sản phẩm rồi đánh giá bằng bằng chứng.

### Slide 5 — Kiến trúc tổng thể

- Sơ đồ ba tầng, đọc từ dưới lên: lõi headless, không gian năng lực H-P-D-I, hai ứng dụng.
- Lõi gồm Keycloak (định danh), APISIX (cổng API), PostgreSQL và SeaweedFS (dữ liệu); nhóm tự xây bộ máy quy trình, thông báo và cổng AI.
- Hai quyết định thiết kế: ranh giới lõi và ứng dụng kiểm tra khi biên dịch; mọi truy cập qua một cổng, máy chủ nghiệp vụ kiểm quyền lần thứ hai.
- Lớp vận hành dùng chung: giám sát chạy tách khỏi máy chủ nghiệp vụ, sổ kiểm toán chỉ ghi thêm, giới hạn tốc độ, sao lưu mã hóa.

Nguồn: [`../../ARCHITECTURE.md`](../../ARCHITECTURE.md) mục 1 đến 4.

### Slide 6 — DX-Lab: ứng dụng trả lời đề

| Năng lực | Thể hiện trong DX-Lab |
|---|---|
| H — Con người | Bốn vai: sinh viên, giảng viên, kỹ thuật viên, quản trị; mỗi người chỉ thấy phần của mình |
| P — Quy trình | Yêu cầu hỗ trợ có hạn xử lý; mượn thiết bị duyệt theo cấp; đặt phòng không trùng giờ |
| D — Dữ liệu | Thống kê hạn xử lý, tải kỹ thuật viên, mức dùng phòng |
| I — Trí tuệ | Gợi ý loại và độ ưu tiên có giải thích; hỏi đáp quy chế trích văn bản của HaUI |

Thông điệp: quy tắc nghiệp vụ ràng buộc ở máy chủ và cơ sở dữ liệu, không ai tự duyệt yêu cầu của mình.

### Slide 7 — DX-Farm: kiểm chứng tính tổng quát

- Cùng lõi, khác miền: quản lý dịch hại lúa cho nông hộ và kỹ sư.
- Nông dân dùng di động, vẫn ghi nhận được khi mất mạng.
- Chẩn đoán bệnh từ ảnh, kỹ sư duyệt rồi mới khuyến nghị thuốc.
- Chặn thu hoạch khi chưa hết thời gian cách ly thuốc.
- Thời tiết theo thửa ruộng, báo cáo tổng hợp.

Thông điệp: DX-Farm và DX-Lab dùng chung đúng một lõi, chỉ khác phần khai báo.

### Slide 8 — Trí tuệ nhân tạo và tri thức

| Chức năng | Mô hình, dữ liệu (giấy phép) | Ai quyết định |
|---|---|---|
| Gợi ý phân loại yêu cầu (Lab) | Luật từ khóa, trả kèm lý do | Người gửi yêu cầu |
| Hỏi đáp quy chế và tài liệu (Lab, Farm) | RAG lai BM25 và vector, xếp hạng lại bằng mMARCO MiniLM; embedding MiniLM đa ngữ; Qwen2.5-1.5B (đều Apache-2.0) | Người đọc, có trích nguồn |
| Nhận diện bệnh lá lúa (Farm) | EfficientNetV2-M, bộ ảnh RiceLeafDiseaseBD (CC BY 4.0) | Kỹ sư nông nghiệp |

- AI không tự sinh con số về thuốc, liều lượng, thời gian cách ly.
- Câu hỏi không đủ căn cứ thì từ chối thay vì đoán; máy chủ yếu thì trả nguyên văn tài liệu.

Nguồn: [`../../ai/README.md`](../../ai/README.md), [`../AI_EVALUATION.md`](../AI_EVALUATION.md).

### Slide 9 — Tính mở

- Mã tự viết: Apache-2.0; riêng dịch vụ AI: AGPL-3.0 (do dùng mô hình lọc ảnh AGPL).
- Bảng phụ thuộc và giấy phép; SBOM CycloneDX cho 4 khối.
- 670 commit từ 06/09/2026; bốn bản phát hành `v1.0.0` đến `v1.0.3`.
- README song ngữ, CHANGELOG, hướng dẫn đóng góp, quy tắc ứng xử, mẫu báo lỗi.
- Dựng lại toàn bộ từ mã nguồn bằng một lệnh; CI quét bí mật, lỗ hổng phụ thuộc và image; Dependabot cập nhật phụ thuộc.

Cập nhật số commit trước khi xuất slide.
Nguồn: [`../../LICENSES.md`](../../LICENSES.md), [`../../sbom/README.md`](../../sbom/README.md), [`../../CONTRIBUTING.md`](../../CONTRIBUTING.md).

### Slide 10 — Minh chứng vận hành và đánh giá

| Câu hỏi | Bằng chứng | Kết quả |
|---|---|---|
| CH1. Lõi đứng yên | Thêm DX-Lab sau DX-Farm; chạy một ứng dụng mẫu rỗng | 0 dòng thay đổi trong lõi; ứng dụng rỗng vẫn khởi động |
| CH2. Đủ H-P-D-I cho hai miền | Kiểm thử đầu-cuối trên môi trường dựng sạch, chạy tự động mỗi lần thay đổi | Farm 10 bước, Lab 16 bước đều đạt |
| CH3. AI có kiểm soát | Mô hình bệnh trên 1.466 ảnh kiểm tra (số liệu của người huấn luyện); truy xuất RAG trên 72 câu hỏi có nhãn | Accuracy 89,43%, macro-F1 88,13%; độ tin cậy dưới 0,70 chuyển cho chuyên gia; Recall@4 hỏi đáp Farm từ 0,52 lên 0,65 khi dùng truy xuất lai và xếp hạng lại |

Demo trực tiếp trên DX-Lab, 2 phút 30 giây, theo chuỗi người dùng → Open-Core → quy trình → dữ liệu → trí tuệ → kết quả:

| Thời gian | Thao tác | Tầng, năng lực |
|---|---|---|
| 0:00–0:20 | Mở cổng thông tin, chỉ trạng thái các dịch vụ lõi | Tầng 1 |
| 0:20–0:50 | Sinh viên đăng nhập một lần, gửi yêu cầu "máy chiếu không lên hình", xem gợi ý | H, I |
| 0:50–1:30 | Kỹ thuật viên nhận việc, ghi chú nội bộ, hoàn tất; mở dòng thời gian | P |
| 1:30–2:00 | Giảng viên đặt phòng, rồi thử đặt chồng giờ | P, poka-yoke |
| 2:00–2:30 | Mở trang thống kê, hỏi một câu về quy chế mượn thiết bị | D, I |

Dự phòng: AI chưa sẵn sàng thì gợi ý phân loại vẫn chạy bằng luật và hỏi đáp trả nguyên văn tài liệu; máy demo hỏng thì chiếu video quay sẵn.
Kịch bản demo đầy đủ và các tab cần mở sẵn ở [`../SUBMISSION.md`](../SUBMISSION.md) mục 2.

### Slide 11 — Hạn chế và hướng phát triển

| Hạn chế | Hướng phát triển |
|---|---|
| Mô hình bệnh chưa được đánh giá độc lập trên ảnh ruộng thật | Thu ít nhất 100 ảnh ruộng có nhãn kỹ sư để đo toàn quy trình |
| Luật phân loại của Lab chưa đo độ chính xác trên dữ liệu thật | Đo trên yêu cầu thật của khoa; tìm yêu cầu tương tự |
| Mới thử tải rút gọn phần truy xuất; dịch vụ AI là một tiến trình | Đo tải đầy đủ; hàng đợi và nhiều tiến trình AI; chạy nhiều bản máy chủ sau cổng |
| Kho tri thức còn nhỏ (Farm 7 tài liệu tổng quát, Lab 12 bài từ 9 văn bản của HaUI) | Mở rộng tài liệu có giấy phép rõ ràng |
| Lab chưa có ứng dụng di động và bảng điều khiển BI riêng | Bổ sung trên cùng lõi, không sửa lõi |
| Máy chủ công khai không có AVX2: tắt Qwen, chẩn đoán ảnh trên CPU mất khoảng 50 giây | Máy có AVX2 hoặc GPU; mô hình nhỏ hơn |

Nguồn: [`../BACKLOG.md`](../BACKLOG.md) mục 4 và 5.

### Slide 12 — Kết luận

- Một lõi headless từ thành phần nguồn mở, phục vụ hai miền nghiệp vụ khác nhau.
- Đủ bốn năng lực H-P-D-I, quy tắc an toàn ràng buộc từ gốc.
- AI có căn cứ, giải thích được, con người quyết định.
- Công khai, tái lập được, sẵn sàng cho cộng đồng đóng góp.
- Xin cảm ơn Hội đồng.

## Đối chiếu tiêu chí chấm

| Nhóm | Tiêu chí | Điểm | Slide | Bằng chứng |
|---|---|---|---|---|
| PoF | Kho mã công khai, lịch sử commit | 5 | 9 | Lịch sử commit trên nhánh `main` |
| PoF | Giấy phép, tương thích, nguồn gốc | 10 | 9 | Bảng phụ thuộc và giấy phép; cách xử lý thành phần AGPL |
| PoF | Bản phát hành có phiên bản | 5 | 9 | `v1.0.0` đến `v1.0.3`, image phát hành trên GHCR |
| PoF | Dựng từ mã nguồn, tái lập | 10 | 9, 10 | Một lệnh dựng toàn bộ; kiểm thử đầu-cuối trên môi trường sạch |
| PoF | Quản lý phụ thuộc, phiên bản | 10 | 9 | SBOM CycloneDX cho 4 khối, tệp khóa phiên bản; Dependabot và Trivy |
| PoF | README, CHANGELOG, theo dõi lỗi, tài liệu | 10 | 9 | README song ngữ, CHANGELOG, mẫu báo lỗi, hướng dẫn đóng góp |
| Sản phẩm | Đúng 3 tầng, lõi headless, liên kết H-P-D-I | 10 | 5, 6, 7 | Sơ đồ ba tầng; ranh giới kiểm tra khi biên dịch |
| Sản phẩm | Hoàn thiện, ổn định khi demo | 10 | 10 | Demo trực tiếp; phương án dự phòng |
| Sản phẩm | Thân thiện, giá trị sử dụng | 10 | 6, 10 | Ba quy trình của khoa; giao diện tiếng Việt |
| Sản phẩm | Mở rộng, bền vững, chất lượng kỹ thuật | 10 | 7, 10, 11 | Thêm ứng dụng không sửa lõi; điểm nghẽn đã xác định |
| Sản phẩm | Trình diễn, giải thích, tiềm năng cộng đồng | 10 | 4, 12 | Mạch lập luận theo câu hỏi nghiên cứu; hướng dẫn thêm ứng dụng |

## Trước khi xuất slide

- Điền tên thành viên và giảng viên hướng dẫn ở Slide 1.
- Cập nhật số commit và bản phát hành mới nhất ở Slide 9.
- Tạo vài issue mẫu trên GitHub để có bằng chứng cho tiêu chí theo dõi lỗi.
- Chụp ảnh giao diện hai ứng dụng và màn hình giám sát cho Slide 6, 7 và 10; che IP và email thật.
