# Plan thực hiện: Phase 1

Thứ tự: chốt hồ sơ → dữ liệu nguồn → generator → kiểm tra → tài liệu trạng thái cuối.
Không có thay đổi Swagger hoặc schema cần thiết cho phạm vi này.
Việc chi tiết ở [02_CHECKLIST.md](02_CHECKLIST.md).

## WP1-01: Hoàn thiện hồ sơ đốm nâu

- Tham chiếu: `docs/BACKLOG.md`, mục 6; ước lượng S, chưa tính chờ thẩm định.
- Hiện tại: ứng viên có dữ liệu nhưng `IN_REVIEW`, chưa được seed.
- Sau thay đổi: quyết định duyệt có người, vai trò, ngày và tham chiếu; chưa đủ thì giữ nguyên trạng thái.

Đối chiếu [ADC — Help 400SC](https://www.adcgroup.net/shop/thuoc-bvtv/thuoc-tru-benh/help-400sc/), phần Hướng dẫn sử dụng cho đốm nâu/lúa.

| Liều | Lượng nước | Thời điểm | PHI |
|---|---|---|---|
| 0,6 L/ha | 400 L/ha | Khi bệnh chớm xuất hiện | 14 ngày |

Thông số trên phục vụ thẩm định hồ sơ, chưa là quyết định kích hoạt thuốc.
Kiểm tra đăng ký đúng sản phẩm, đúng đối tượng và các sửa đổi/thu hồi hiện hành qua nguồn cơ quan quản lý.
Không suy ra đăng ký còn hiệu lực chỉ từ trang thương mại hoặc việc không xuất hiện trong một danh sách rút thuốc.
Ghi quyết định thật và tham chiếu căn cứ trong `seed_authorization` hiện có; không tạo hệ thống duyệt mới.
Kết quả: đủ căn cứ để WP1-02 thực hiện, hoặc ghi rõ phần còn thiếu và giữ rule bị chặn.

Ngày 20/10/2026 đã đọc trực tiếp PDF Phụ lục I Thông tư 75/2025 từ URL trong registry: trang PDF 152 có Help 400SC với đối tượng đốm nâu/lúa và đơn vị ADC.
Đã đọc phụ lục Thông tư 28/2026: Help 400SC không xuất hiện trong danh sách năm sản phẩm tự nguyện rút của văn bản này.
Trang ADC truy cập trực tiếp HTTP 200 và có đủ bốn thông số nêu trên.
Các đối chiếu này chưa xác nhận toàn bộ tình trạng đăng ký hiện hành.
Chủ dự án đã yêu cầu gán `EXPERT_APPROVED` và mở rule ngày 20/10/2026; eligibility đã đổi theo xác nhận này, ghi rõ vai trò và chưa kèm văn bản chuyên môn riêng trong dữ liệu.

## WP1-02: Mở dữ liệu và seed bằng cơ chế hiện có

- Tham chiếu: WP1-01; ước lượng S.
- Hiện tại: 1 thuốc, 2 rule active; đốm nâu chưa có phác đồ khả dụng.
- Sau thay đổi: 2 thuốc, 3 rule active; đốm nâu dùng Help 400SC qua lookup sẵn có.

Chỉ cập nhật record `P-RICE-002` trong `data/structured/pesticides_candidates_master.json` và `TR-RICE-003` trong `data/structured/treatment_rules_candidates_master.json`.
Sau quyết định hợp lệ, cập nhật eligibility, trạng thái, lý do chặn, thông tin ủy quyền và phiên bản có ngày thực tế.
Giữ các thông số đã đối chiếu; không chỉnh hai rule Newtec hoặc record bệnh khác.
Không phát minh schema duyệt hoặc yêu cầu sửa generator; có thể ghi thông tin người/ngày/tham chiếu trong object `seed_authorization` đang có.
Chạy generator để sinh `backend/app-farm/src/main/resources/farm/data.sql`; không sửa SQL bằng tay.
Cập nhật `data/scripts/test_generate_seed_sql.py` và `SeedDataIntegrationTest.java` vì kỳ vọng trước đây chỉ có hai rule; giữ kiểm tra thu hồi/chặn ứng viên.
Bổ sung case đốm nâu vào `ReportDiagnosisIntegrationTest.java` hiện có để kiểm tra seed thật, context, duyệt, khai phun và khóa PHI trên PostgreSQL.
Kết quả: SQL nhất quán với JSON, không trùng rule khi chạy lại, không mất lịch sử.

Luồng sử dụng giữ nguyên: lookup đốm nâu → chẩn đoán chờ kỹ sư duyệt → khai phun/xác nhận → khóa thu hoạch theo PHI.
Chẩn đoán cũ không tự được gán phác đồ mới; kiểm tra bằng chẩn đoán mới, giữ nguyên lịch sử đã ghi.
Không sửa Java nghiệp vụ, AI, web, mobile hoặc yêu cầu ảnh CV phải phân loại đúng để kiểm tra dữ liệu rule.

## WP1-03: Bổ sung đường dẫn tiếng Việt theo bệnh

- Tham chiếu: các tài liệu gốc trong bảng dưới; ước lượng S.
- Hiện tại: Farm thiếu một số nguồn tiếng Việt trong danh mục phục vụ tra cứu.
- Sau thay đổi: metadata nguồn có URL truy cập được, phạm vi theo bệnh và trạng thái thật.

| Bệnh | Nguồn được chọn | Vị trí và giới hạn |
|---|---|---|
| Đốm nâu | [ADC — Help 400SC](https://www.adcgroup.net/shop/thuoc-bvtv/thuoc-tru-benh/help-400sc/) | Hướng dẫn riêng cho đốm nâu/lúa; dùng cho hồ sơ thuốc |
| Đạo ôn | [Tuyên Quang — đạo ôn vụ Xuân 2026](https://khuyennongvn.gov.vn/thien-tai-dich-hai/tuyen-quang-tang-cuong-phong-tru-benh-dao-on-bao-ve-lua-vu-xuan-2026-31951.html) | Kiểm tra ruộng, quản lý đạo ôn; hướng dẫn địa phương, không thay rule Newtec |
| Khô vằn | [Quảng Trị — chăm sóc lúa trước, trong, sau trổ](https://khuyennongvn.gov.vn/thien-tai-dich-hai/quang-tri-tang-cuong-cham-soc-lua-truoc-trong-sau-tro-31028.html) | Đoạn bắt đầu “Đối với bệnh khô vằn, lem lép hạt”; không chuyển hướng dẫn cổ bông thành xử lý ảnh lá |
| Tungro | [Chi cục BVTV TP HCM — rầy xanh đuôi đen](https://chicucttbvtvhcm.gov.vn/cay-lua/ray-xanh-duoi-den-416.html) | Đoạn Triệu chứng xác nhận môi giới; bài năm 2019, không nhập thuốc/ngưỡng cũ thành rule Tungro |
| Than lá | Chưa chọn được nguồn tiếng Việt trực tiếp đủ phù hợp | Không lấy tài liệu hoa cúc/than hạt hoặc bản đăng lại chưa xác minh |
| Lá khỏe | Không bổ sung nguồn riêng trong phase này | Không tạo thuốc hoặc kết luận toàn ruộng không bệnh |

Bài Tuyên Quang cập nhật 25/03/2026, tác giả Hà Thu, Chi cục Trồng trọt và BVTV tỉnh Tuyên Quang.
Bài Quảng Trị cập nhật 31/03/2025, tác giả Phan Việt Toàn, Trung tâm Khuyến nông Quảng Trị.
Bài rầy xanh đuôi đen cập nhật 05/11/2019 trên website Chi cục Trồng trọt và BVTV TP HCM.
Ba bài đã đọc được qua công cụ web và truy cập HTTPS trực tiếp HTTP 200 có nội dung thật ngày 20/10/2026; kiểm tra lại khi triển khai.

Bổ sung hoặc đồng bộ record trong `data/rag/source_registry.json` và `backend/app-farm/src/main/resources/knowledge-sources.json`.
Giữ ID ADC hiện có; chỉ thêm ID mới cho nguồn thực sự mới, không đổi định danh nguồn cũ.
URL, tiêu đề, đơn vị, giấy phép và trạng thái phải khớp giữa hai danh mục.
Giấy phép chưa rõ ghi `null`; không ghi tài liệu công khai là dữ liệu có giấy phép mở.
Không tự chuyển source sang `EXPERT_APPROVED` vì đã kiểm tra URL.
Không thêm `source_ids` vào corpus khi chưa đối chiếu chính xác đoạn được nguồn hỗ trợ.
Không đổi `disease_knowledge.json`, ingestion hoặc retrieval trong phase này.
Danh mục có link không đồng nghĩa chẩn đoán sẽ tự trích nguồn đó; đầu ra RAG hiện có được giữ nguyên.
Kết quả: link và metadata được kiểm chứng, phạm vi rõ, không phát sinh phác đồ bệnh khác.

## WP1-04: Kiểm tra và cập nhật trạng thái cuối

- Tham chiếu: [03_TEST_PLAN.md](03_TEST_PLAN.md); ước lượng M.
- Kiểm tra seed, lookup và khai phun đốm nâu, hồi quy hai rule Newtec và các bệnh chưa có rule.
- Chỉ cập nhật kiểm thử hiện có cần thay kỳ vọng; không thêm thư mục test/demo hoặc script khởi động.
- Mặc định giai đoạn triển khai chỉ đọc diff/compile theo template; test runtime chỉ chạy khi được yêu cầu, ghi người chạy và kết quả thật.
- Không build hoặc chạy toàn bộ Docker để làm kế hoạch; khi kiểm thử dùng môi trường sẵn có, chỉ cập nhật dịch vụ Farm cần thiết.
- Chỉ sửa câu trạng thái cũ trong `data/README.md`, `docs/COUNCIL_QA.md`, `docs/BACKLOG.md`; CHANGELOG chỉ bổ sung, không xóa lịch sử.
- Lỗi ngoài phạm vi ghi lại, không sửa kèm.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `/internal/v1/diagnosis-context`, `/api/v1/treatment-rules` và luồng chẩn đoán/khai phun | Chỉ thêm dữ liệu cho đốm nâu | Không |
| Interface, DTO | Core, Backend, AI, web, mobile | Không đổi | Không |
| Bảng DB | `pesticides`, `treatment_rules` | Seed thêm record qua generator | Không |
| Biến môi trường / CI | Hiện có | Không đổi | Không |

## Dữ liệu, migration và rollback

Không thêm migration; dùng cơ chế nạp `farm/data.sql` của nhánh nền.
Đường chạy theo README giữ nguyên; xác minh cả dữ liệu mới và dữ liệu cũ khi được yêu cầu chạy thử.
Rollback eligibility của Help/rule đốm nâu, sinh lại SQL và nạp lại theo cơ chế hiện có.
Rule/thuốc đã được tham chiếu phải ngừng active theo generator; không xóa diagnosis hoặc bản khai phun, không rút ngắn PHI đã lưu.

## Quyết định thiết kế đã chốt trong kế hoạch

| # | Quyết định | Lý do |
|---|---|---|
| D1 | Chỉ mở một rule đốm nâu sau xác nhận | Đúng phạm vi và có bốn thông số từ ADC |
| D2 | Nguồn tiếng Việt chỉ thêm link/metadata | Không kéo theo sửa RAG hoặc UI |
| D3 | Hai rule Newtec và policy bệnh khác giữ nguyên | Không mở rộng xử lý chuyên môn ngoài yêu cầu |
| D4 | Không chèn nhãn phase vào code, SQL hoặc API | Theo quy ước `_template` |
