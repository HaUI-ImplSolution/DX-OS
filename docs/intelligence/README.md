<!--
SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
SPDX-License-Identifier: Apache-2.0
-->

# Kế hoạch phần I — Dự báo nguy cơ trễ trong Hộp việc

**Trạng thái đề xuất để review.**
Các hạng mục bên dưới là thiết kế và tiêu chí nghiệm thu, chưa phải kết quả triển khai.
**Phương án dự thi đã chọn ở mức thiết kế là mô hình thống kê tự xây.**
Các checkpoint pretrained đã rà hiện chưa đủ bằng chứng để đưa vào bản dự thi theo chính sách ở mục 4.

## 1. Giá trị cần chứng minh

Hộp việc hiện ưu tiên hồ sơ đã quá hạn, sau đó ưu tiên hồ sơ cập nhật gần nhất.
Thêm một chế độ xem giúp cán bộ phát hiện **hồ sơ còn hạn nhưng có nguy cơ trễ**, khi vẫn còn thời gian can thiệp.
Người dùng xem căn cứ và chọn việc cần xử lý qua các thao tác sẵn có của DX-OS.
Ví dụ hiển thị minh họa: **“Nên xem trước — 14/20 lượt tương tự còn chờ khi tới hạn.”**
Chỉ dùng số đo thật của bộ dữ liệu đang chạy khi trình diễn.

## 2. Đối chiếu với đề quốc gia OLP Phần mềm nguồn mở 2026

Dự báo nguy cơ trễ đóng góp trực tiếp cho **chữ I tại Tầng 2** theo mục 1 và mục 3 của đề.
Mục 1 mô tả I qua phân tích dữ liệu, dự báo và hỗ trợ ra quyết định.
Đầu ra dự kiến là một gợi ý ưu tiên dựa trên lịch sử mà cán bộ có thể kiểm tra trước khi xử lý.
Mục 2 mô tả thêm tìm kiếm ngữ nghĩa, OCR, quản trị tác tử và RAG; dự báo chỉ phủ phần năng lực nêu trên.
Mục 3 nêu các dịch vụ bắt buộc của Tầng 1 và yêu cầu ứng dụng minh họa ở Tầng 2.
Mục 4 nêu bốn nguyên lý kỹ thuật và cho đội chọn công nghệ, với bảng bảy lớp để tham khảo.
Việc dùng mô hình thống kê chạy CPU là lựa chọn của đội để thực hiện năng lực dự báo. [Đề quốc gia VFOSSA](https://vfossa.vn/tin-tuc/de-thi-phan-mem-nguon-mo-olp-2026-798.html)

| Mục trong đề | DX-OS dùng gì và phần này đóng góp gì | Bằng chứng cần có |
| --- | --- | --- |
| **Mục 1 và 3 · I và ứng dụng minh họa Tầng 2** | Hộp việc đọc lịch sử (D) và hạn do quy trình (P) tính để gợi ý việc cho cán bộ đã đăng nhập (H). | Luồng cảnh báo trước hạn, xem căn cứ và xử lý hồ sơ, kèm kết quả đối chứng. |
| **Mục 3 · Tầng 1 headless và bốn dịch vụ bắt buộc** | Tầng 2 dùng Keycloak (SSO), APISIX (cổng API), PostgreSQL và SeaweedFS (dữ liệu), Flowable (quy trình) qua nền tảng headless. | Demo truy cập theo quyền và xử lý qua Core, kèm bằng chứng riêng của các dịch vụ Tầng 1 trong bản phát hành. |
| **Mục 2 · Tách nghiệp vụ khỏi nền tảng** | Cùng Hộp việc và thuật toán đọc mô hình, trạng thái và hạn theo khai báo. | Một bản build chạy trên các mẫu đã chọn và một mô hình mới, chỉ đổi YAML và sinh lại bảng thống kê. |
| **Mục 4(i) · Tách giao dịch khỏi phân tích lịch sử** | Tính bảng ngoài giao dịch tác nghiệp và giữ các lượt còn chờ trong tập quan sát. | Kết quả tái tạo, số request, tác động lên Core và bằng chứng kho Parquet ở cấp bản cài. |
| **Mục 4(ii) · Hội tụ IT và OT** | Dự báo áp dụng cho hồ sơ sự cố nhà máy từ luồng IT/OT giả lập hiện có. | Dữ liệu thiết bị được đưa vào hồ sơ, với phạm vi IT/OT giới hạn ở luồng giả lập và quy trình xử lý hồ sơ. |
| **Mục 4(iii) · Quản trị nguồn gốc và chất lượng dữ liệu** | Công bố nguồn, kỳ dữ liệu, phiên bản và cách sinh bảng, kèm lý do khi dữ liệu chưa đủ để chấm. | Từ một gợi ý, tra được nhóm mẫu và tái tạo bảng bằng mã và dữ liệu công khai. |
| **Mục 4(iv) · Zero-Trust, GitOps, kiểm dịch và tự phục hồi** | Xếp hạng qua API chỉ đọc theo quyền, với GitOps, kiểm dịch và tự phục hồi do nền tảng cung cấp. | Kết quả kiểm vai, phạm vi hồ sơ và bảng lỗi, kèm kiểm GitOps, phục hồi và quét tệp trên cấu hình dự thi. |

### Tiêu chí chấm liên quan

| Tiêu chí của thể lệ | Đầu ra phần dự báo cần có |
| --- | --- |
| **PoF 1–3 · Kho công khai, giấy phép, bản phát hành** | Mã và cấu hình công khai, hồ sơ giấy phép theo thành phần, bản phát hành theo phiên bản trước hạn nộp và bộ mẫu có thể tái tạo. |
| **PoF 4–6 · Dựng từ nguồn, phụ thuộc, tài liệu** | Lệnh dựng và tính lại bảng bằng công cụ mở, bản cài chạy ngoài thư mục mã nguồn, danh mục phụ thuộc, hướng dẫn, CHANGELOG và issue theo dõi. |
| **Sản phẩm 7–11 · Nguyên gốc, hoàn thiện, thân thiện, bền vững, trình diễn** | Kịch bản cho thấy cách đội gắn dự báo vào mô hình động, kèm đối chứng, số đo chi phí và hướng dẫn áp dụng thêm mô hình. |

Phần dự báo cung cấp bằng chứng cho các tiêu chí trên trong phạm vi thay đổi.
Việc nộp bài và cam kết bản quyền theo mục 5, hệ thống chạy thử và phần trình diễn theo mục 6 áp dụng cho toàn bộ sản phẩm dự thi. [Thể lệ OLP Phần mềm nguồn mở 2026](https://vfossa.vn/tin-tuc/the-le-olp-phan-mem-nguon-mo-nam-2026-760.html)

## 3. Thiết kế và phạm vi thay đổi

### 3.1. Mô hình thống kê và tính động

Mô hình do đội tự viết ước lượng tỷ lệ trễ của các lượt trước đang chờ xử lý trong điều kiện tương tự.
Một nhóm được xác định từ mô hình nghiệp vụ, thực thể, phiên bản cấu hình, trạng thái đang chờ và khoảng thời gian còn lại đến hạn.
Mã tính dùng trạng thái và hạn Core cung cấp; tên miền, vai, nhãn, lịch làm việc và thời hạn vẫn lấy từ mô hình nghiệp vụ.
Phiên bản và dấu vân tay của cấu hình trong bộ demo xác định dữ liệu nào tương thích với bảng tham số.

- **Đầu vào để tính bảng:** ảnh chụp tại thời điểm quan sát gồm mã mô hình, thực thể, phiên bản, trạng thái, thời điểm quan sát, hạn do Core tính và mã lượt giả lập để đối chiếu.
  Kết quả của một lượt là **còn chờ xử lý khi tới hạn** hoặc **đã rời lượt chờ trước hạn**.
  Kết quả này đo việc chờ qua hạn của lượt hiện tại, không đánh giá hồ sơ cuối cùng được duyệt hay từ chối.
- **Cách tính:** gom các lượt có cùng nhóm và tính tỷ lệ bằng số lượt còn chờ lúc tới hạn chia cho số lượt đủ quan sát.
  Chỉ đếm một lần mỗi lượt trong mỗi nhóm và giữ cả các lượt còn chờ khi đã tới hạn.
  Điểm của một hồ sơ hiện tại không dùng bất kỳ thông tin nào phát sinh sau thời điểm chấm.
- **Đầu ra:** số mẫu, số lượt trễ, tỷ lệ quan sát được, kỳ dữ liệu và phiên bản bảng.
  Tỷ lệ dùng để xếp hạng và giải thích, chưa phải xác suất đã kiểm định cho một cá nhân.
- **Điều kiện từ chối chấm:** thiếu hạn, thiếu cấu hình tương thích, thiếu mẫu, dữ liệu quá cũ, bảng lỗi hoặc hồ sơ đã quá hạn.
  Hộp việc vẫn mở được; các hồ sơ đã quá hạn giữ ưu tiên hiện có.
  Các hồ sơ chưa đủ dữ liệu tiếp tục có thứ tự mặc định và được giải thích vì sao chưa chấm.

Ba miền để kiểm trên cùng một bản build gồm Một cửa sinh viên (họ duyệt, 72 giờ), hỗ trợ CNTT (họ xử lý công việc, 8 giờ) và sự cố nhà máy (họ xử lý công việc, 4 giờ).
Kiểm thêm một mô hình mới dùng YAML với tên trường, vai và thực thể khác, có khai hạn và đủ lịch sử mẫu.
Điều kiện đạt là chỉ áp dụng mô hình và sinh lại bảng dữ liệu, không sửa thuật toán hay thêm cú pháp YAML.
Mẫu đặt phòng hiện có khung giờ đặt nhưng không có hạn xử lý là đối chứng âm.
Đổi phiên bản mô hình, đổi hạn hoặc đổi lịch phải làm bảng cũ mất hiệu lực với nhóm tương ứng.

### 3.2. Các phần sẽ thêm

| Hạng mục | Đầu ra dự kiến |
| --- | --- |
| Mã tạo dữ liệu và tính bảng | Script JavaScript chạy một lần bằng Node.js trong môi trường phát triển có sẵn, dùng thư viện chuẩn. Sinh dữ liệu giả từ seed cố định, tạo bảng tóm tắt và báo cáo đối chứng. Xuất tệp JSON có phiên bản định dạng, nguồn, khoảng thời gian, tham số và checksum. |
| Mã chấm điểm | Hàm TypeScript trong khu vực Hộp việc, tra bảng theo hồ sơ và cấu hình rồi trả lý do có điểm hoặc chưa đủ dữ liệu. Giới hạn kích thước bảng và số hồ sơ theo trang, nạp bảng khi người dùng mở chế độ dự báo. |
| Dữ liệu trên giao diện | Bổ sung có điều kiện trường chỉ đọc `handleBy` cho các dòng Hộp việc nếu chưa có nguồn đọc theo lô phù hợp. Dùng metadata hiện có để biết `modelVersion`. Nếu đổi API, cập nhật hợp đồng và OpenAPI trước khi sửa mã; dùng cùng truy vấn lọc theo quyền, giữ phân trang và tránh gọi chi tiết từng hồ sơ. |
| Hộp việc | Thêm chế độ xem **Nguy cơ trễ**, giải thích bằng số liệu và nhãn tiếng Việt. Phạm vi xếp hạng giới hạn ở trang đang xem và được ghi rõ. Chỉ các hồ sơ người gọi đã được xem mới có thể xuất hiện. Mở hồ sơ và xử lý bằng các thao tác hiện hành. |
| Bộ kiểm và tài liệu | Bổ sung ca bắt hồi quy vào bộ kiểm hiện có cho API và giao diện. Viết cách tái tạo bộ mẫu, kiểm phiên bản và bật chế độ dự báo. Cập nhật tài liệu sản phẩm và changelog khi chức năng dùng được. |

Ứng dụng tầng trên chấm điểm trên CPU bằng bảng đã tính trước.
Lõi cung cấp dữ liệu theo quyền và không chạy model trong giao dịch nghiệp vụ.
Thiết kế dùng cùng web, Node.js phục vụ bước chuẩn bị, PostgreSQL và stack hiện có.
Mục tiêu của phiên bản đầu là **không thêm gói ML, image, container, trọng số tải ngoài hoặc API nhà cung cấp**.
Dự báo chỉ gợi ý thứ tự xem; phân công, chuyển trạng thái và ghi lịch sử vẫn đi qua Core.

Phiên bản dự thi bật chế độ dự báo trên bản cài demo riêng dùng dữ liệu tổng hợp từ mẫu giả công khai.
Chưa bật bảng demo trên bản cài chứa dữ liệu thật.
Mở rộng sang dữ liệu vận hành cần xác minh quyền xử lý, dữ liệu lịch sử có phiên bản rõ ràng và cách phân phối bảng tổng hợp theo quyền.
Nghiệm thu vận hành là một mốc riêng, cần bằng chứng từ dữ liệu và tải phù hợp.

### 3.3. Ước tính dung lượng và tài nguyên

**Ước tính tăng 35–75 KiB trên bản cài demo** cho mã web và bảng thống kê, tính trước nén.
Đây là ước tính thiết kế, chưa có bản build hay số đo khi chạy.

| Phần tăng thêm | Cơ sở ước tính |
| --- | --- |
| Bảng dự báo JSON | Khoảng **23–47 KiB** với bốn mô hình, tối đa tám cặp thực thể và trạng thái chờ trên mỗi mô hình, sáu khoảng thời gian và 120–250 byte mỗi nhóm. |
| Mã web sau khi thu gọn | Dự trù **10–25 KiB** cho chấm điểm, giải thích và chế độ xem, dùng lại thành phần giao diện Hộp việc. |
| Cài đặt máy chủ | **0 dịch vụ, 0 image và 0 gói ML mới**; mã và bảng nằm cùng bản web hiện có. |
| Mạng | Tải bảng một lần khi mở chế độ dự báo. Nếu bổ sung `handleBy` vào API, dự trù thêm khoảng **2 KiB JSON** cho trang 50 hồ sơ, trong cùng request. |
| CPU và RAM | Mỗi trang tra tối đa 50 mục rồi xếp hạng trên trình duyệt. Bảng và cấu trúc tra cứu dùng RAM của tab web; chi phí phân tích JSON, sắp xếp và heap cần đo trên bản triển khai. |

Đặt giới hạn **64 KiB cho bảng demo** và mục tiêu **100 KiB cho tổng dung lượng tăng thêm** của web và bảng trước nén.
Bảng vượt giới hạn hoặc lệch phiên bản sẽ không được dùng để chấm.
Bộ dữ liệu kiểm và log thử nghiệm để ngoài bản cài; dung lượng của chúng phụ thuộc số ca và được báo riêng.
Khi nghiệm thu, so bản build cùng cấu hình có và không có dự báo, ghi byte trước và sau nén, số request, p95 thời gian chấm và RAM tăng thêm trên cùng máy.

## 4. Chính sách giấy phép, tính mở và dữ liệu

| Phần | Giấy phép hoặc điều kiện đưa vào |
| --- | --- |
| Mã, cấu hình, tài liệu và script do đội viết | Apache-2.0 theo repo. Áp dụng SPDX/REUSE, giữ bản toàn văn giấy phép và thông báo bản quyền. Bên cấp phép phải sở hữu quyền hoặc có quyền cấp phép cho phần đóng góp. |
| Dữ liệu mẫu và bảng tham số sinh từ mẫu | Dự kiến CC0-1.0 sau khi xác nhận quyền. Công bố thông tin nguồn, cách tạo, seed, định dạng, phiên bản và giấy phép. Chỉ dùng đầu vào tự tạo có quyền công bố. Giữ tệp dữ liệu, bảng sinh và log kiểm ngoài Git; kế hoạch phát hành phải có đường dẫn công khai tới bộ mẫu có phiên bản để người ngoài tự tái tạo. |
| TypeScript và giao diện đang dùng | TypeScript 7.0.2 (Apache-2.0), React 18.3.1 (MIT), react-admin 5.15.5 (MIT). Đã đọc LICENSE trong gói npm của đúng ba phiên bản này; cần kiểm lại theo khóa của tag dự thi khi phát hành. |
| Công cụ và dịch vụ dùng lại | Mã Node.js theo MIT với giấy phép riêng của phần đóng kèm, PostgreSQL theo PostgreSQL License. Kiểm đúng bản Node.js, image PostgreSQL và các thành phần đóng kèm theo digest; giấy phép của dự án gốc chỉ là bằng chứng cho phần mã tương ứng. |
| Mô hình và dữ liệu từ bên thứ ba | Phương án dự thi dùng bảng tham số do đội tạo. Thành phần ngoài chỉ được đưa vào sau khi xác minh quyền sử dụng, sửa đổi và phân phối ở đúng phiên bản cho mục đích dự thi, cùng nghĩa vụ ghi công và phân phối. |

1. **Điều kiện đối với mã.** Dùng giấy phép được OSI công nhận và tương thích với cách đóng gói.
   Rà cả dependency trực tiếp, bắc cầu, công cụ build/test, base image, plugin, font và thành phần tải lúc chạy.
   Ghim phiên bản hoặc digest, lưu đường dẫn nguồn và bằng chứng LICENSE/NOTICE của artifact đúng bản.
2. **Điều kiện đối với dữ liệu.** Xác minh quyền sử dụng, sửa đổi và công bố độc lập với giấy phép mã.
   Công bố dữ liệu mẫu có thể chia sẻ, cách tái tạo và bảng tham số dưới điều khoản mở.
   Không đưa hồ sơ thật, dữ liệu cá nhân, bí mật, tài liệu công khai chưa rõ quyền, hoặc dữ liệu tổng hợp có thể suy ngược cá nhân vào bộ phát hành.
3. **Điều kiện đối với chính sách.** Các quy tắc chọn mẫu, khoảng thời gian, ngưỡng tối thiểu, xếp hạng và từ chối chấm đều nằm trong mã hoặc cấu hình mở.
   Người nhận có thể đọc, sửa, chạy lại và phân phối chúng.
   Phân quyền hồ sơ là chính sách vận hành của từng bản cài.
4. **Điều kiện đối với khả năng tái tạo.** Công bố mã tiền xử lý và tính điểm, dữ liệu hoặc cách sinh dữ liệu, tham số, phiên bản và lệnh kiểm.
   Dùng [OSAID 1.0](https://opensource.org/ai/open-source-ai-definition) làm chuẩn bổ sung của đội để đối chiếu quyền sử dụng, nghiên cứu, sửa đổi, chia sẻ và các thành phần cần để tái tạo.
   Hồ sơ dự thi không dựa vào lời gắn nhãn hoặc chứng nhận model.
5. **Xử lý thành phần chưa rõ.** Không đưa vào cấu hình dự thi khi thiếu bằng chứng về quyền hoặc có điều khoản hạn chế trái với yêu cầu mở.
   Ưu tiên thay thế hoặc gỡ thành phần đó khỏi đúng đường chạy dự thi.
   Ghi nguồn, phiên bản, vai trò, SPDX và nghĩa vụ vào hồ sơ hiện có như `DEPENDENCIES.txt`, `NOTICE`, catalog và SBOM khi có thay đổi.
   Không mở danh sách cho phép toàn repo để vượt kiểm.

### Kết quả rà mô hình pretrained

Mỗi dòng dưới đây áp dụng cho bản được nêu.
Thể lệ quốc gia yêu cầu sản phẩm phát hành theo giấy phép được OSI công nhận và mã nguồn truy cập được.
Đội dùng thêm OSAID 1.0 để rà phần mô hình: mã suy luận, mã huấn luyện, thông tin dữ liệu và tham số phải có điều khoản mở phù hợp.
Mức tái tạo cần đủ để tạo một hệ thống tương đương về thực chất, không đòi checkpoint sinh lại trùng từng byte.
Danh sách cho phép trong repo là điều kiện riêng cho việc tích hợp.
[Thể lệ quốc gia](https://vfossa.vn/tin-tuc/the-le-olp-phan-mem-nguon-mo-nam-2026-760.html) · [OSAID 1.0](https://opensource.org/ai/open-source-ai-definition) · [Danh sách cho phép của DX-OS](../../.github/scripts/check_licenses.py)

| Bản rà | Bằng chứng chính | Quyết định |
| --- | --- | --- |
| **TabDPT 1.3** | Trọng số Apache-2.0, mã suy luận Apache-2.0, mã huấn luyện giới hạn mục đích nghiên cứu và không cho phép thương mại. [Trọng số](https://huggingface.co/Layer6/TabDPT/blob/a5ca6e01c0fa09ec68c73e958e5199d1932abb3a/LICENSE) · [Suy luận](https://github.com/layer6ai-labs/TabDPT-inference/blob/3cef828a0bbfe8818bcdd6d855940e1949f2b4cd/LICENSE) · [Huấn luyện](https://github.com/layer6ai-labs/TabDPT-training/blob/af0340c5cdebe2ceb6b94c09d6f9564ee80b89df/LICENSE) | Loại theo chuẩn mở của đội. |
| **TabICLv2**, checkpoint phân loại ngày 12/02/2026, mã v2.2.0 | Checkpoint công bố BSD-3-Clause, có giấy phép mã, công thức và trình sinh dữ liệu giả; upstream ghi mã huấn luyện công khai là bản chuyển từ codebase nội bộ. [Checkpoint](https://huggingface.co/jingang/TabICL/tree/4dcd344ece2c00be9e831fdd35bed57b5ad83e19) · [Giấy phép mã và thông báo bên thứ ba](https://github.com/soda-inria/tabicl/blob/v2.2.0/LICENSE) · [Công thức huấn luyện](https://github.com/soda-inria/tabicl/blob/v2.2.0/README.md#pre-training) | Ứng viên ưu tiên để khảo sát; cần xác minh công thức công khai đủ tái tạo mô hình tương đương và rà bộ phụ thuộc CPU khóa phiên bản. |
| **TabPFN 1.0.0 và 3.5** | V1 có khai Apache-2.0 trong tài liệu nhưng tệp `LICENSE.txt` ở tag trống và trình nạp trỏ đường tải theo nhánh; 3.5 có giấy phép trọng số phi thương mại. [Tài liệu v1](https://github.com/PriorLabs/TabPFN/blob/v1.0.0/README.md) · [Tệp giấy phép v1](https://github.com/PriorLabs/TabPFN/blob/v1.0.0/LICENSE.txt) · [Giấy phép 3.5](https://huggingface.co/Prior-Labs/tabpfn_3_5/blob/main/LICENSE) | V1 chưa chốt đủ hồ sơ checkpoint, công thức và bộ phụ thuộc; loại 3.5 theo chuẩn mở của đội. |
| **OpenTFM Joint-2M v0.2** | Repo mã MIT, có checkpoint 9,1 MB và SHA-256; manifest 42 họ dữ liệu thật ghi nguồn và checksum cache nhưng chưa đủ hồ sơ giấy phép từng bộ đầu vào. [Giấy phép mã](https://github.com/T-Lab/OpenTFM/blob/v0.2/LICENSE) · [Checkpoint](https://github.com/T-Lab/OpenTFM/releases/tag/v0.2) · [Manifest D42](https://github.com/T-Lab/OpenTFM/blob/v0.2/experiments/joint_v1/joint_d42_corpus_manifest_v1.json) | Chưa đưa vào khi chưa xác minh quyền của checkpoint phát hành và các nguồn dữ liệu. |
| **TabOmics v2.5** | Có checkpoint, trình sinh dữ liệu giả và cấu hình từng giai đoạn theo GPLv3; GPLv3 được OSI công nhận. [Checkpoint](https://huggingface.co/lbotecur/tabomics-v2.5) · [Mã và công thức](https://github.com/lbotecur/tabomics#training) · [GPLv3 tại OSI](https://opensource.org/license/gpl-3.0) | GPLv3 chưa nằm trong danh sách cho phép của repo đối với dependency hoặc artifact thuộc phạm vi này. |
| **NVIDIA Kumo Tabular** | Trọng số theo OpenMDW-1.1; có mã và công thức pretrain, còn OSI công bố OpenMDW đang chờ phê duyệt. [Giấy phép trọng số](https://huggingface.co/nvidia/Kumo-Tabular/blob/main/LICENSE) · [Báo cáo mô hình](https://arxiv.org/abs/2611.23001) · [Trạng thái tại OSI](https://opensource.org/blog/open-source-ai-salons-shaping-the-osaid-together) | Chưa chọn theo yêu cầu dùng giấy phép được OSI công nhận. |
| **Mitra-v2 và Nori** | Mitra-v2 công bố trọng số, suy luận và fine-tune Apache-2.0 nhưng hồ sơ đã rà chưa có mã pretrain đầy đủ; Nori chính thức tập trung vào hồi quy, còn bài toán đã chọn là phân loại trễ. [Mitra-v2](https://arxiv.org/abs/2609.04540) · [Nori](https://github.com/Synthefy/synthefy-nori) | Giữ ngoài phương án phân loại dự thi. |

Chỉ xem xét thay mô hình hiện tại khi hồ sơ ghi được **checkpoint và checksum**, **giấy phép của đúng bản mã và trọng số**, **nguồn dữ liệu và quyền sử dụng**, **công thức đủ tái tạo**, **điều khoản bổ sung**, và **SBOM của bộ chạy CPU khóa phiên bản**.
Bản đưa đi thi phải được kiểm với các thành phần thực sự sử dụng, gồm cả thư viện bắc cầu và binary đi kèm.
Sau khi đủ bằng chứng mở, mô hình pretrained vẫn phải vượt đối chứng về giá trị và tài nguyên theo mục 5.
Tính mở của phương án tự xây sẽ được kiểm từ các tài sản công khai thực tế của đội trước khi phát hành.

### Điểm chặn của bản phát hành

- Kho mã có kiểm giấy phép và SBOM, nhưng cần kiểm thêm đúng artifact phát hành và các thành phần bên trong image.
  Hồ sơ hiện hành ghi image ClamAV bật mặc định kèm `libclamunrar` có giấy phép hạn chế.
  Trước khi tuyên bố toàn bản dự thi mở, phải xác minh giải pháp quét không có UnRAR, quyền trong bộ chữ ký CVD được dùng và kết quả kiểm quét qua cửa ngõ.
  Đây là việc của cấu hình phát hành, không nằm trong thay đổi thuật toán.
- Trợ lý sinh văn bản cũ có cấu hình tham khảo Apertus với AUP riêng.
  Bản chạy dùng để chứng minh dự báo không cần bật trợ lý đó.
  Nếu bản dự thi cũng trình diễn trợ lý cũ, toàn bộ model, trọng số lượng tử, dữ liệu, runtime và điều khoản bổ sung của bản cụ thể phải qua rà riêng.

## 5. Trình tự triển khai và điều kiện nghiệm thu

1. **Chốt hợp đồng và dữ liệu.**
   Xác định một lượt chờ, hạn đã tính và phiên bản mô hình tại thời điểm quan sát.
   Dùng ảnh chụp demo có phiên bản xác định được; loại dữ liệu lịch sử thiếu căn cứ ghép phiên bản.
   Chốt cách đọc hạn theo quyền và danh sách đầu vào được phép.
   Khi đổi API, làm rõ hợp đồng, cập nhật OpenAPI rồi triển khai mã, giữ cơ chế phản hồi và phân quyền hiện có.
2. **Tính bảng và thiết lập phép đối chứng.**
   Cùng seed và phiên bản phải sinh cùng bảng và checksum.
   Kiểm lượt mở lại, hạn thay đổi, dữ liệu trống, mẫu ít và lượt vẫn còn chờ khi tới hạn.
   Chốt trước khoảng thời gian, ngưỡng mẫu và quy tắc xếp hạng.
   Chia theo thời gian và theo hồ sơ để ca dùng tính bảng không lọt vào phần đánh giá.
3. **Nối Hộp việc và kiểm đa miền.**
   Chạy cùng bản build trên ba mẫu hiện có và mô hình mới.
   Kiểm quyền, phân trang, bảng sai phiên bản, mẫu không có hạn và việc xử lý vẫn ghi lịch sử qua Core.
   Tận dụng bộ kiểm hiện có, kiểm compile hoặc test-compile theo phần thay đổi và kiểm giao diện ở mốc nghiệm thu đã được duyệt.
4. **Đo bằng cùng tập ứng viên và cùng số việc được chọn.**
   So dự báo với thứ tự Hộp việc hiện tại và với hạn gần nhất.
   Báo số ca thực sự trễ xuất hiện trong nhóm được ưu tiên, số báo nhầm, số bỏ sót và tỷ lệ đủ dữ liệu để chấm theo từng miền.
   Đo dung lượng tăng thêm, thời gian nạp và chấm, số request, tài nguyên CPU/RAM và tác động Core trên cùng máy.
   Kết quả trên dữ liệu giả chứng minh cách hoạt động và khả năng tái tạo.
   Kết luận hiệu quả thực tế cần đánh giá độc lập bằng dữ liệu được phép sử dụng.
5. **Nghiệm thu bản trình diễn và hồ sơ PoF.**
   Dựng từ source trên bản cài riêng, tái tạo bộ mẫu công khai và kiểm đường demo cục bộ sau khi cài.
   Chốt tag và cấu hình thực chạy, đối chiếu danh sách thành phần với LICENSE/NOTICE, catalog và SBOM theo mục 4 của kế hoạch.
   Chạy kiểm giấy phép, trích dẫn tài liệu và kiểm dựng từ nguồn theo hướng dẫn đóng góp trước khi công bố.
   Demo đi từ việc đăng nhập, xem và đối chiếu gợi ý, đến xử lý hồ sơ qua Core.
   Sau đó áp dụng một mô hình mới và trình bày cách người ngoài có thể chạy lại từ source.

### Câu hỏi BGK và điều kiện đạt

- [ ] **Có giá trị hơn sắp theo hạn không?** Trên tập đối chứng đã chốt, dự báo phát hiện được nhiều lượt trễ trước hạn hơn hai cách xếp cơ sở với cùng số việc ưu tiên.
- [ ] **Tính động nằm ở đâu?** Mô hình mới chạy được với cùng bản build và tham số tính từ dữ liệu của mô hình.
- [ ] **Có kiểm tra được từng gợi ý không?** Giám khảo xem được kỳ dữ liệu, số mẫu, lý do xếp hạng và lệnh tái tạo.
- [ ] **Có nhẹ và tách khỏi lõi không?** Bản thử có số đo dung lượng, tài nguyên và request; Core vẫn vận hành khi thiếu bảng dự báo.
- [ ] **Có đủ bằng chứng mở trên đúng bản đem thi không?** Bản phát hành có hồ sơ quyền của mã, dữ liệu và tham số; xác minh cấu hình quét tệp và các thành phần đóng kèm.
- [ ] **Đâu là kết quả đã kiểm?** Phân biệt số đo trên dữ liệu giả, bằng chứng tích hợp và hiệu quả vận hành; nêu rõ phạm vi I đã trình diễn.

Tài liệu dự án để đối chiếu gồm [mô hình](../MODEL.md), [API](../API.md), [đánh giá Dynamic Core theo đề](../dynamic-core/DANH_GIA.md), [quy tắc đóng góp](../../CONTRIBUTING.md) và [danh mục giấy phép](../../DEPENDENCIES.txt).

Nguồn rà giấy phép gồm [Apache-2.0](https://opensource.org/license/apache-2.0), [CC0-1.0](https://creativecommons.org/publicdomain/zero/1.0/legalcode), [giấy phép PostgreSQL](https://opensource.org/license/postgresql) và [tài liệu giấy phép ClamAV 1.5.4](https://github.com/Cisco-Talos/clamav/blob/clamav-1.5.4/README.md#licensing).
