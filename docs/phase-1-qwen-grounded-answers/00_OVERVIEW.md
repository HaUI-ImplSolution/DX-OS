# Phase 1: Câu trả lời Qwen có cấu trúc

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft; chỉ lập kế hoạch, chưa triển khai |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | AI, app-farm, app-lab, giao diện hỏi đáp và chẩn đoán |
| Nhánh | `feat/qwen-grounded-answers`, tách từ `origin/Khuong/main` ngày 27/10/2026 |
| Ước lượng | 3–5 ngày làm việc sau khi duyệt kế hoạch; chưa gồm đánh giá thực địa |
| Người phụ trách | Nhóm DX-OS; chủ dự án duyệt nội dung và giao diện |
| Bắt đầu, dự kiến xong | Lập kế hoạch 27/10/2026; lịch triển khai sau khi duyệt |
| Phụ thuộc | Qwen2.5:1.5b/Ollama, corpus và các gateway hiện có |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi |
|---|---|---|---|
| Chủ dự án, 27/10/2026 | Qwen tổng hợp câu trả lời trên web Farm/Lab và giải thích chẩn đoán Farm; không nâng cấp chatbot mobile | Quan trọng | Toàn bộ |
| Chủ dự án, 27/10/2026 | Giữ cách trình bày ngắn, rõ từng mục; bảo vệ sản phẩm đã phát hành và Core | Quan trọng | Toàn bộ |
| [`ARCHITECTURE.md`](../../ARCHITECTURE.md), mục 2–5 | App chỉ phụ thuộc core-api; dữ liệu nghiệp vụ và duyệt thuộc Backend | Bắt buộc | Giữ nguyên ranh giới |
| [`CONTRIBUTING.md`](../../CONTRIBUTING.md), [`backend/.claude/WORKFLOW.md`](../../backend/.claude/WORKFLOW.md) | Thay đổi nhỏ, Swagger trước code, migration mới, kiểm tra theo phạm vi | Bắt buộc | Áp dụng khi triển khai |
| [`ai/app/rag/README.md`](../../ai/app/rag/README.md) | Retriever, nguồn và quyền database chỉ đọc | Bắt buộc | Tái sử dụng |

Đối chiếu hướng dẫn Ponytail và các skill RAG, dependency, LangGraph, verification của AI; Spring Boot và JPA của Backend.
Áp dụng vào phạm vi: tái sử dụng helper/dependency, giữ graph stateless và transaction ngắn, chỉ ghi kết quả đã kiểm chứng.
Không dùng các ví dụ trong skill để tự nâng framework hoặc thêm cơ chế ngoài yêu cầu.

## 2. Lý do

Hỏi đáp hiện chỉ trả các đoạn trích; chẩn đoán trả rule và evidence, chưa gọi Qwen để tổng hợp lời giải thích.
Ghép toàn bộ nội dung vào một đoạn văn dễ dài, lặp và khó đọc trên điện thoại.
Cần thêm câu trả lời có cấu trúc, tách khỏi bằng chứng và giữ khả năng dùng client đã phát hành.

## 3. Tổng quan sửa đổi

AI bổ sung một bộ tổng hợp dùng Qwen với schema cố định và chế độ dự phòng rõ ràng.
Ba profile prompt riêng là hỏi đáp Farm, hỏi đáp Lab và giải thích chẩn đoán Farm; có thể dùng chung schema nhưng không dùng chung vai trò trả lời.
Farm/Lab lấy evidence qua KnowledgeGateway, rồi gọi tổng hợp qua AiGateway đã có.
Chẩn đoán Farm tạo và lưu phần giải thích riêng theo từng bản chẩn đoán.
Web dựng bố cục hỏi đáp và chẩn đoán từ các trường đã kiểm tra; mobile chỉ hiển thị phần giải thích trong chi tiết báo cáo Farm.
Giữ tài liệu gốc ở phần tham khảo; màn hỏi đáp mobile giữ hành vi hiện tại.
Client mới yêu cầu phần sinh; request cũ tiếp tục chạy luồng hiện tại.

| Module hoặc lớp | Có thay đổi | Ghi chú |
|---|---|---|
| `dx-core-api` | Không | Giữ interface và DTO, đặc biệt nghĩa trích dẫn của KnowledgePassage |
| `dx-core` | Không | Tái sử dụng KnowledgeGateway và AiGateway |
| `app-farm` | Có | Hỏi đáp, DTO, lưu/đọc và che phần giải thích chẩn đoán theo quyền hiện có |
| `app-lab` | Có | Hỏi đáp và DTO; không đổi ticket, SLA, đặt phòng, mượn thiết bị |
| `dx-host`, `dx-monitor` | Không | Giữ ghép app, cấu hình chung và giám sát |
| Frontend | Có | Trang hỏi đáp dùng chung và hiển thị chẩn đoán Farm |
| Mobile | Có, chỉ chi tiết báo cáo | Hiển thị giải thích chẩn đoán Farm; không sửa chatbot mobile |
| AI | Có | Tổng hợp Qwen, schema, prompt, điểm nối chẩn đoán; không đổi model CV/retriever |
| Data / Seed / Migration | Có, chỉ schema Farm | Thêm một cột nullable; không sửa seed, corpus, nhãn đánh giá hoặc dữ liệu cũ |
| Infra / Deploy / CI | Không | Dùng Ollama, model và cách khởi động sẵn có |
| Docs | Có | Kế hoạch; cập nhật đúng mục liên quan khi hoàn thành |

## 4. Phạm vi

- Trong phạm vi: hỏi đáp một lượt trên web Farm/Lab, giải thích chẩn đoán Farm trên web và chi tiết báo cáo mobile, trích nguồn, định dạng, dự phòng, tương thích và hồi quy liên quan.
- Ngoài phạm vi: chatbot mobile, trí nhớ hội thoại, streaming, agent/tool tự thực hiện nghiệp vụ, đổi model, huấn luyện, hybrid search/reranker, sửa corpus, chấm lại nhãn RAG, Langfuse/runtime tracing mới, thay deploy/CI hoặc tái cấu trúc hệ thống.
- Giả định: cấu hình `OLLAMA_BASE_URL`, `OLLAMA_MODEL` hiện tại sử dụng được; cần xác minh bằng model thật trước khi tích hợp rộng.
- Ranh giới Core/App: app điều phối qua hai interface sẵn có; không thêm interface, dependency hoặc DTO sinh nội dung vào Core.
- `frontend/src/shell/` có thay đổi UI hỏi đáp dùng chung; đây là giao diện, không phải Core Backend headless.
- Kế hoạch này cho phép thêm một migration Farm để giữ đúng nội dung đã duyệt; không tái dùng `recommendationText` hoặc `knowledgeEvidence` để chứa văn bản sinh.

## 5. Ưu điểm và hạn chế

- Ưu điểm: Qwen viết câu trả lời đúng vai trò; giao diện kiểm soát bố cục; evidence gốc vẫn kiểm tra được; Core Backend giữ nguyên.
- Hạn chế: tăng độ trễ và tranh chấp tài nguyên; schema và nguồn hợp lệ chưa chứng minh mọi câu diễn giải đều đúng.
- Hạn chế: hỏi đáp không có lịch sử nhiều lượt; kết quả pilot chưa phải đánh giá độc lập.
- Quyết định về tương thích, lưu dữ liệu và lỗi dịch vụ ở [`04_TRADEOFFS_RISKS.md`](./04_TRADEOFFS_RISKS.md).

## 6. Kết quả đầu ra mong đợi

- Demo được câu trả lời Qwen cho hỏi đáp web Farm/Lab và giải thích chẩn đoán Farm, kèm nguồn đối chiếu.
- Bố cục cố định: trả lời trực tiếp 1–2 câu, tối đa 3 ý xử lý khi phù hợp, tối đa một lưu ý; không hiện JSON hoặc Markdown thô.
- Chatbot Farm vẫn tìm kiến thức bệnh trong corpus và giữ trích dẫn; trả lời đúng câu hỏi, không tự xác nhận bệnh của người dùng hoặc lấy bối cảnh chẩn đoán từ lượt khác.
- Giải thích chẩn đoán bám kết quả CV của đúng report; phân biệt đặc điểm bệnh trong tài liệu với triệu chứng thực sự được ghi nhận, không giả nhận đã quan sát ảnh.
- Thuốc, liều, PHI, rule ID/version và trạng thái duyệt giữ đúng dữ liệu Backend; Qwen không tạo hoặc sửa các trường đó.
- Phần sinh có mode đúng: `generated`, `extractive` hoặc `insufficient_context`; chỉ ghi `generated` khi đã có đầu ra model được chấp nhận.
- Dữ liệu chẩn đoán cũ đọc được; phần giải thích mới lưu cùng bản chẩn đoán và chỉ hiện cho nông dân theo trạng thái được phép.
- Client cũ gọi được endpoint cũ; client mới đọc được response chưa có phần sinh; chatbot mobile không opt-in và giữ cách trả lời hiện tại.
- Diff của `dx-core-api`, `dx-core`, `dx-host`, `dx-monitor`, deploy/CI, CV, retriever, embedding và corpus so với nhánh cha phải trống.
- Cách chạy Farm/Lab theo README giữ nguyên; không cần thêm dịch vụ hoặc tài khoản.
- Bằng chứng tách rõ kiểm tra tĩnh, kiểm thử tự động và chạy model thật theo [`03_TEST_PLAN.md`](./03_TEST_PLAN.md); chưa chạy thì chưa tích đạt.

## 7. Phụ thuộc và thứ tự

WP1-01 chốt hợp đồng và ranh giới trước code.
WP1-02 kiểm chứng Qwen/schema trong dependency hiện có trước khi nối app và lưu dữ liệu.
WP1-03 và WP1-04 dùng cùng bộ tổng hợp, sau đó WP1-05 nối giao diện và WP1-06 nghiệm thu.

## 8. Ảnh hưởng đến thi

Làm rõ vai trò Intelligence: tìm căn cứ, tổng hợp câu trả lời và hiển thị nguồn trong quy trình có kiểm soát.
Chỉ công bố kết quả đã đo; không suy điểm Recall/nDCG thành độ đúng câu trả lời hoặc điểm rubric.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Nội dung | Phương án trong kế hoạch | Trạng thái |
|---|---|---|---|
| Q1 | Core Backend có sửa không? | Không sửa cả code, interface và schema Core | Đã thống nhất phạm vi |
| Q2 | Qwen chọn trích dẫn hay viết câu trả lời? | Viết câu trả lời mới từ evidence, có kiểm tra và mode dự phòng | Đã thống nhất hướng |
| Q3 | Lưu giải thích chẩn đoán thế nào? | Một cột JSONB nullable riêng ở Farm, không backfill | Đề xuất để duyệt cùng kế hoạch |
| Q4 | Bảo vệ client đã phát hành thế nào? | Request cũ không yêu cầu sinh; client mới opt-in bằng query parameter | Đề xuất để duyệt cùng kế hoạch |
| Q5 | Hỏi đáp và giải thích chẩn đoán có dùng cùng cách trả lời không? | Dùng chung hạ tầng, schema; profile, ngữ cảnh và nhãn hiển thị riêng | Đã thống nhất yêu cầu |

Chưa triển khai trước khi chủ dự án duyệt kế hoạch và các đề xuất Q3–Q4.

## 10. Tài liệu cần cập nhật khi xong

- [ ] AI README, README module RAG; rà dòng mô tả writer cũ ở README graph nếu điểm nối thay đổi.
- [ ] README app/UI đúng phần hỏi đáp và giải thích; mobile chỉ cập nhật chi tiết báo cáo và quyền xem.
- [ ] Swagger của các endpoint app và schema AI đúng hợp đồng mới.
- [ ] CHANGELOG chỉ bổ sung kết quả, không xóa log cũ hoặc kể lại quá trình.
- [ ] TESTING ghi phương pháp, người chạy và kết quả thật; BACKLOG giữ việc ngoài phạm vi.
- [ ] LICENSES/SBOM chỉ cập nhật nếu thực tế đổi dependency; kế hoạch không thêm dependency.
- [ ] Không sửa sơ đồ kiến trúc hoặc hướng dẫn triển khai khi ranh giới/cách chạy không đổi.
