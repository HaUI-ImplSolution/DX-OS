# Phase 2 Mở rộng hỏi đáp lúa và kiểm soát ngoài phạm vi

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | app-farm, app-lab, AI RAG, data/rag |
| Ước lượng | 7–10 ngày làm việc, chưa tính thời gian chuyên gia duyệt dữ liệu |
| Người phụ trách | Chưa chốt; cần 1 kỹ sư AI/data và 1 người duyệt nông nghiệp |
| Bắt đầu, dự kiến xong | Chưa chốt |
| Phụ thuộc | Kết quả baseline của Phase 1; quyền truy cập nguồn và người duyệt |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Yêu cầu chủ dự án | Câu hỏi về ô tô hoặc chủ đề không liên quan phải nhận câu trả lời không biết | Quan trọng | Tầng scope gate trước RAG của Farm và Lab |
| Yêu cầu chủ dự án | Hỏi về lúa nói chung phải trả lời được, không chỉ bệnh | Quan trọng | Corpus và pipeline ingest Farm mới |
| Yêu cầu mở rộng | Câu hỏi ngoài chủ đề DX-Lab phải bị từ chối, câu hỏi đúng chủ đề nhưng thiếu tài liệu phải nói rõ chưa biết | Quan trọng | Scope gate theo namespace `lab` |
| `docs/BACKLOG.md`, mục 4 | Farm RAG hiện chỉ có 6 tài liệu bệnh lúa | Quan trọng | Mở rộng nhóm tài liệu |
| `ai/app/rag/README.md` | Runtime chỉ đọc, ingest tách riêng, câu trả lời phải có evidence | Bắt buộc | Giữ nguyên ranh giới an toàn |
| `docs/_template` | Quy ước phase, checklist và test | Tham chiếu | Toàn bộ |

## 2. Lý do

- Retriever hiện mặc định lọc `document_type="disease_knowledge"`, nên câu hỏi canh tác lúa ngoài bệnh không có corpus phù hợp.
- Chưa có scope gate trước truy xuất, nên câu hỏi như “xe ô tô nên thay dầu gì” vẫn có thể nhận các đoạn gần nhất trong kho bệnh.
- Nguồn mới chỉ được đưa vào RAG sau khi kiểm tra nguồn, ngôn ngữ/domain, bản quyền, phạm vi áp dụng và duyệt nội dung; corpus hiện hữu cũng phải được audit lại.

## 3. Tổng quan sửa đổi

1. Tạo bộ phân loại phạm vi theo ứng dụng/namespace và trả lời từ chối ổn định với câu ngoài lĩnh vực.
2. Tách trạng thái `OUT_OF_SCOPE`, `IN_SCOPE_NO_EVIDENCE` và `ANSWERABLE` để không nhầm “không biết” với “chưa có tài liệu”.
3. Mở rộng corpus Farm theo chủ đề: canh tác, giống, đất/dinh dưỡng, nước, cỏ dại, sâu hại/IPM, bệnh, thu hoạch và sau thu hoạch.
4. Áp dụng cùng scope contract cho Lab nhưng giữ namespace `lab`, nguồn HaUI và taxonomy hỗ trợ CNTT riêng.
5. Xây job crawl/ingest allowlist có rate limit, lưu raw snapshot, checksum, thời điểm truy cập và provenance.
6. Chuẩn hóa, kiểm duyệt, đánh giá retrieval rồi ingest atomic vào namespace tương ứng.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api`, `dx-core`, `dx-host` | Không | Không đổi nghiệp vụ dùng chung |
| `app-farm` / `app-lab` / AI API | Có | Scope gate và response fields theo namespace |
| Frontend web/mobile | Có thể có | Hiển thị thông báo ngoài phạm vi và nguồn theo trạng thái |
| AI | Có | Classifier/gate, retriever filter, tests |
| Data / Seed / Migration | Có, không đổi schema DB | Corpus, registry, raw/processed và version; dữ liệu mới chiếu vào bảng RAG hiện có |
| Infra / Deploy / CI | Có | Job crawl/ingest riêng, credential write chỉ ở job |
| Docs | Có | Tài liệu nguồn, đánh giá và vận hành |

## 4. Phạm vi

- Trong phạm vi: hỏi đáp tiếng Việt về lúa và nông nghiệp liên quan trực tiếp đến sản xuất lúa; hỏi đáp DX-Lab trong các chủ đề tài liệu Lab đã được duyệt; scope gate; citation; đánh giá retrieval và từ chối.
- Ràng buộc nguồn Farm: mọi dữ liệu về lúa và nông nghiệp phải lấy từ nguồn uy tín có nội dung tiếng Việt; nguồn tiếng Anh hoặc nguồn không xác minh được uy tín không được ingest.
- Ràng buộc nguồn Lab: chỉ được crawl và ingest các URL thuộc `https://sict.haui.edu.vn` hoặc các đường dẫn con của domain này.
- Ngoài phạm vi Farm: chatbot tư vấn ô tô hoặc nông nghiệp mọi cây trồng; tự động đưa liều thuốc/PHI; tự động biến nội dung crawl thành treatment rule.
- Ngoài phạm vi Lab: hỗ trợ kỹ thuật chưa có tài liệu, yêu cầu truy cập hệ thống, thao tác thay đổi dữ liệu, chẩn đoán sự cố ngoài catalog và mọi câu hỏi không thuộc CNTT/quy định/dịch vụ Lab được công bố.
- Ngoài phạm vi chung: LLM judge và GraphRAG.
- Giả định: Backend vẫn là nguồn sự thật cho thuốc, liều, PHI và policy; RAG chỉ cung cấp giải thích có dẫn nguồn.
- Ranh giới Core/App: thay đổi thuộc AI/app-farm và AI/app-lab; không đưa dependency của Farm hoặc Lab vào core.

## 5. Ưu điểm và hạn chế

- Ưu điểm: giảm hallucination ngoài miền, câu hỏi lúa có coverage rộng hơn, mọi đoạn đều truy ngược được về nguồn, không làm lẫn dữ liệu Farm và Lab.
- Hạn chế: scope gate có thể từ chối câu hỏi mơ hồ; dữ liệu địa phương cần chuyên gia duyệt; crawl không đồng nghĩa với được phép tái phân phối nội dung.
- Trade-off lớn: ưu tiên precision của từ chối và an toàn nguồn hơn recall tuyệt đối; xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi Definition of Done

- Demo Farm: hỏi “xe ô tô thay dầu gì?” nhận thông báo chatbot chỉ hỗ trợ kiến thức nông nghiệp/lúa; hỏi “lúa giai đoạn đẻ nhánh cần quản lý nước thế nào?” nhận câu trả lời có nguồn.
- Demo Lab: hỏi “cách đặt lại mật khẩu Wi-Fi HaUI?” chỉ trả lời nếu có tài liệu Lab; hỏi “cách sửa động cơ ô tô?” bị từ chối, không lấy nhầm tài liệu HaUI.
- Demo: hỏi về bệnh lúa vẫn giữ luồng hiện tại; hỏi ngoài bệnh nhưng đúng lúa không bị lọc vào `disease_knowledge`.
- API trả thêm trạng thái scope và reason ổn định, không phá client cũ nếu client bỏ qua field mới.
- Corpus Farm có tối thiểu 8 nhóm chủ đề, mỗi nhóm có tài liệu đã duyệt và source registry hợp lệ.
- Corpus hiện hữu được audit; nguồn Farm không phải tiếng Việt và nguồn Lab ngoài `sict.haui.edu.vn` được quarantine hoặc loại khỏi dữ liệu phục vụ.
- 100% câu hỏi out-of-scope trong bộ test bắt buộc bị từ chối; không có citation sai namespace cho câu bị từ chối.
- Câu hỏi in-scope có evidence đạt ngưỡng đã chốt từ baseline Phase 1; câu thiếu bằng chứng ở Farm hoặc Lab trả `IN_SCOPE_NO_EVIDENCE`.
- Ingest là job quản trị, có dry-run, validate, version artifact và rollback bằng cách ingest lại corpus trước đó.
- Giữ nguyên migration hiện tại; mở rộng contract ingest để dữ liệu không phải bệnh lúa không cần `disease_code` hoặc field đặc thù của bệnh.
- Có báo cáo precision/recall của scope gate và Recall@k/nDCG@k của corpus mới.
- Có bằng chứng: test report, manifest corpus, registry nguồn, log dry-run/ingest và ảnh demo.

## 7. Phụ thuộc và thứ tự

- Cần từ Phase 1: baseline retrieval và bộ câu hỏi được kiểm tra nhãn.
- Thứ tự: chốt taxonomy/contract → tạo bộ test scope → triển khai gate → chọn/crawl nguồn → chuẩn hóa/duyệt → ingest → đánh giá → demo.
- Phase sau cần từ phase này: corpus version theo namespace, scope contract và danh sách backlog nguồn còn thiếu.

## 8. Ảnh hưởng đến thi

- Cải thiện phần AI/RAG, provenance, guardrail và khả năng giải thích trong demo Farm và Lab.
- Dùng làm bằng chứng cho báo cáo kỹ thuật, slide và video hỏi đáp.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Scope Farm hỗ trợ “nông nghiệp nói chung” hay chỉ lúa? | A: lúa trước, B: mọi cây trồng | Trước WP2 | A: lúa trước; ngoài lúa chỉ mở sau khi có corpus |
| Q2 | Nguồn nào được phép nạp nội dung? | A: allowlist nguồn chính thống, B: crawl mở | Trước WP2 | A |
| Q3 | Câu in-scope nhưng chưa có evidence trả gì? | A: nói chưa có dữ liệu và gợi ý hỏi lại, B: LLM trả theo kiến thức nền | Trước WP1 | A |
| Q4 | Có cho phép tự động crawl định kỳ production không? | A: job staging + duyệt rồi ingest, B: tự động ingest | Trước WP2 | A |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `ai/app/rag/README.md`, `ai/README.md` và tài liệu vận hành crawl/ingest.
- [ ] `data/README.md`, `data/rag/source_registry.json` hoặc registry mới theo schema đã chốt.
- [ ] `docs/BACKLOG.md`: xóa mục kho RAG chỉ có bệnh lúa nếu DoD đạt; thêm nguồn Farm/Lab chưa duyệt.
- [ ] API/OpenAPI nếu response chat thêm field công khai.
- [ ] `docs/AI_EVALUATION.md` hoặc báo cáo đánh giá mới nếu chốt công bố kết quả.
