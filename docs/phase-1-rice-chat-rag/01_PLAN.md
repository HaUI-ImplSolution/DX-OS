# Plan thực hiện Phase 2

## Thứ tự và phụ thuộc

`WP2-01` và `WP2-02` làm song song sau khi chốt contract; `WP2-03` phụ thuộc taxonomy và pipeline; `WP2-04` phụ thuộc corpus đã duyệt; `WP2-05` phụ thuộc tất cả work package.

## WP2-01: Scope gate và phản hồi không biết

- Mục tiêu: chặn câu hỏi ngoài phạm vi trước khi gọi retriever hoặc LLM, đồng thời nhận diện câu đúng phạm vi nhưng thiếu evidence cho cả Farm và Lab.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Farm hỏi về ô tô | Có thể trả đoạn gần nhất trong RAG | `OUT_OF_SCOPE`, câu trả lời cố định, không truy xuất và không citation |
| Lab hỏi về ô tô | Có thể trả đoạn gần nhất trong RAG Lab | `OUT_OF_SCOPE`, không truy xuất namespace `lab` |
| Farm hỏi “lúa giai đoạn đẻ nhánh bón phân thế nào?” | Mặc định chỉ tìm bệnh | `IN_SCOPE`, tìm toàn bộ `farm` knowledge đã duyệt |
| Lab hỏi về Wi-Fi/quy định HaUI | Tìm trong corpus Lab hiện có | `IN_SCOPE`, chỉ tìm namespace `lab` và document types Lab |
| Hỏi đúng lúa nhưng corpus chưa có | Có thể trả đoạn không liên quan | `IN_SCOPE_NO_EVIDENCE`, nói rõ chưa đủ tài liệu và không bịa |
| Hỏi bệnh lúa | Tìm disease knowledge | Giữ tương thích, có thể lọc thêm `topic=disease` |

**Hướng làm**

- Tái sử dụng `RAGService`, `RAGRetriever`, `CorpusConfig`, `ingest_corpus`, `source_registry` và các test boundary hiện có; không tạo pipeline ingest hoặc abstraction corpus thứ hai.
- Chỉ thêm một scope decision nhỏ tại điểm vào hỏi đáp; không thêm model/classifier dependency nếu rule + embedding threshold hiện có đủ sau calibration.
- Tạo `ScopeDecision` với `namespace`, `scope`, `confidence`, `reason_code` và `retrieval_allowed`.
- MVP dùng hybrid gate: từ khóa/regex tiếng Việt cho chủ đề lúa và nông nghiệp + similarity/evidence threshold đã hiệu chuẩn bằng tập in-scope/out-of-scope; không dùng LLM để quyết định duy nhất.
- Gate phải chạy trước embedding retrieval nếu câu đã rõ ngoài phạm vi; câu mơ hồ được classify theo namespace và có thể yêu cầu người dùng nói rõ cây trồng/giai đoạn hoặc dịch vụ Lab cần hỏi.
- Sau retrieval, áp dụng ngưỡng minimum similarity và evidence diversity; không coi similarity cao đơn lẻ là bằng chứng đúng.
- Giữ `RAGService` grounded: LLM chỉ được diễn đạt nội dung có trong evidence; thuốc, liều, PHI vẫn lấy từ Backend.

**Phạm vi ảnh hưởng**

- Module: `ai/app/rag`, `ai/app/api`, test AI, frontend nếu response hiển thị status; không trộn corpus Farm và Lab.
- File chính: `rag_service.py`, `retriever.py`, `prompt_builder.py`, `api/schemas.py`, router và test mới.
- Endpoint: endpoint hỏi đáp hiện tại; field mới là additive, không đổi field `answer` và `sources`.
- Cấu hình: `RAG_SCOPE_MIN_CONFIDENCE`, `RAG_MIN_SIMILARITY`, `RAG_OUT_OF_SCOPE_MESSAGE` và message theo namespace có default an toàn.
- Tương thích: client cũ bỏ qua field mới; diagnosis flow không được gọi scope gate thay cho `disease_code`.

**Kết quả đầu ra**

- Out-of-scope không gọi DB retriever/LLM.
- In-scope không còn mặc định `disease_knowledge`; Farm và Lab truyền tường minh namespace/document type hợp lệ.
- Có telemetry không chứa nội dung nhạy cảm: scope, score, latency, corpus version và reason code.
- Có một unit/self-check nhỏ làm hỏng build nếu scope gate bỏ qua namespace hoặc gọi retriever khi đã `OUT_OF_SCOPE`.

## WP2-02: Bộ đánh giá scope và retrieval baseline

- Mục tiêu: tạo tập câu hỏi có nhãn để chốt ngưỡng thay vì đoán bằng vài ví dụ.
- Ước lượng: S/M.

**Hướng làm**

- Mở rộng `data/rag/evaluation/questions.jsonl` thành các nhóm Farm: lúa trực tiếp, nông nghiệp ngoài lúa; nhóm Lab: CNTT/quy định/dịch vụ Lab; cùng nhóm ngoài namespace, mơ hồ, adversarial prompt và câu hỏi có lỗi chính tả.
- Mỗi câu có `scope_label`, `topic`, `expected_status`, `gold_source_ids` hoặc `no_answer`.
- Tách train/calibration và holdout; người duyệt không xem ranking khi kiểm tra qrels.
- Báo cáo precision, recall, F1 của scope gate; Recall@k, nDCG@k và tỷ lệ unsupported answer của RAG.

**Kết quả đầu ra**

- Có ít nhất 30 câu out-of-scope, 30 câu in-scope lúa và 15 câu no-evidence trước khi đo chính thức.
- Ngưỡng được ghi kèm corpus version và không tối ưu trên holdout.

## WP2-03: Pipeline crawl, provenance và chuẩn hóa corpus

- Mục tiêu: biến nguồn được allowlist thành artifact có thể kiểm tra, không cho crawler ghi thẳng production vector DB.
- Ước lượng: L.

**Hướng làm**

- Tạo manifest nguồn với `source_id`, URL, tổ chức, authority level, license/terms, locale, namespace, crop/topic, accessed_at, content hash, review_status và recommended_use.
- Farm chỉ allowlist các nguồn uy tín có nội dung tiếng Việt: Cục Trồng trọt và BVTV/PPD; Trung tâm Khuyến nông Quốc gia và khuyến nông tỉnh; viện/trường nghiên cứu Việt Nam; tài liệu nhãn chính thức bằng tiếng Việt chỉ cho phần được phép sử dụng.
- Lab chỉ allowlist `https://sict.haui.edu.vn` và các URL con cùng domain; không crawl domain HaUI khác, mạng xã hội, website bên thứ ba hoặc tài liệu được nhúng từ nguồn ngoài domain.
- Audit toàn bộ `data/rag/source_registry.json`, `data/rag/processed/disease_knowledge.json`, `data/rag/lab/source_registry.json` và `data/rag/lab/processed/lab_knowledge.json`; source không đạt chính sách phải chuyển `QUARANTINED`, không xóa ngay.

**Danh sách nguồn khởi đầu cần thẩm định**

| Nhóm | Nguồn | URL mẫu | Dùng cho |
|---|---|---|---|
| Quy định Việt Nam | Cục Trồng trọt và BVTV | `https://www.ppd.gov.vn/` | Quy định và danh mục hiện hành; không tự suy ra liều từ bài tổng hợp |
| Khuyến nông Việt Nam | Trung tâm Khuyến nông Quốc gia | `https://khuyennongvn.gov.vn/` | Hướng dẫn mùa vụ, IPM và bối cảnh địa phương |
| Nghiên cứu Việt Nam | VAAS Knowledge | `https://vaas.vn/kienthuc/Caylua` | Kiến thức cây lúa và tài liệu tham khảo trong nước |
| Khuyến nông địa phương | Chi cục/Trung tâm BVTV địa phương | URL tiếng Việt thuộc cơ quan nhà nước | Hướng dẫn theo vùng, vụ và điều kiện địa phương |
| Lab | SICT HaUI | `https://sict.haui.edu.vn/` | Tin tức, thông báo, đào tạo, hướng dẫn và tài liệu chính thức của SICT |

Các URL trên chỉ là ứng viên allowlist ban đầu; trước khi crawl phải kiểm tra robots.txt, điều khoản sử dụng, giấy phép, phạm vi tái sử dụng và khả năng truy cập ổn định.
- Validator phải kiểm tra `language=vi`, `authority_level`, `organization`, `namespace` và host trước khi cho phép document vào bước review.
- Với Lab, giữ format registry hiện tại nhưng cập nhật host bắt buộc là `sict.haui.edu.vn`; URL redirect sang host khác phải bị từ chối. Các bài Lab hiện hữu trỏ tới domain HaUI khác chỉ được giữ ở quarantine cho tới khi có bản tương ứng trên SICT.
- Nhóm nội dung ưu tiên: kỹ thuật gieo/cấy và vòng đời; giống; đất/dinh dưỡng; tưới và quản lý nước; cỏ dại; sâu hại/IPM; bệnh; thu hoạch/sau thu hoạch; VietGAP và an toàn.
- Crawler tôn trọng robots/terms, rate limit, retry có giới hạn, user-agent, timeout và giới hạn kích thước; PDF/HTML được lưu snapshot bất biến.
- Extract text có page/section locator; loại navigation, quảng cáo, menu; giữ bảng và ghi cảnh báo khi OCR.
- Chuẩn hóa mỗi document với `document_type`, `topic`, `crop`, `locale`, `source_ids`, `version`, `review_status`; chunk theo heading, tối đa hiện hành 350 ký tự trừ khi benchmark chứng minh cần đổi.
- Cấm đưa từ nguồn crawl vào treatment rule, thuốc, liều hoặc PHI nếu chưa có quy trình phê duyệt riêng.

**Kết quả đầu ra**

- `crawl --dry-run` không ghi DB; manifest và raw/processed artifacts có checksum.
- Validator phát hiện URL thiếu, source_id trùng, license thiếu, document chưa duyệt, chunk rỗng, source không allowlist, Farm không phải tiếng Việt hoặc Lab không thuộc `sict.haui.edu.vn`.

## WP2-04: Tái sử dụng ingest hiện có và rollback theo namespace

- Mục tiêu: nạp corpus mới atomic theo namespace, có thể quay về version trước mà không làm lẫn Farm và Lab.
- Ước lượng: M.

**Hướng làm**

- Giữ và tái sử dụng `CorpusConfig`, `CORPORA` và `ingest_corpus` hiện có; chỉ thêm cấu hình/data file cho corpus Farm đa chủ đề hoặc metadata cần thiết.
- Không đổi cơ chế atomic replace theo namespace; giữ `disease_knowledge` cho diagnosis và `lab_knowledge` cho Lab nếu client hiện tại phụ thuộc các document type này.
- Dữ liệu mới dùng contract RAG chung gồm `title`, `content`, `provenance.source_ids`, `topic`, `language` và `review_status`; `disease_code` chỉ bắt buộc với corpus bệnh.
- Mapping vào schema hiện có: `title` vào `documents.title`, chunk vào `document_chunks.content`, namespace/type/version/source giữ ở `documents`, embedding giữ ở `document_chunks`, `disease_code=NULL` cho tài liệu không phải bệnh.
- Job ingest dùng `RAG_INGEST_DATABASE_URL`, khóa transaction và thay thế atomic đúng namespace theo cơ chế hiện có; chạy dry-run và smoke retrieval trước khi ingest.
- Runtime chỉ đọc corpus đã ingest; không cho FastAPI crawl hoặc ingest.
- Citation phải trả URL, tổ chức, source_id, version, accessed_at và locator nếu có.
- Rollback bằng cách chạy lại ingest với artifact version trước; không xóa raw/processed artifacts. Không thêm active alias hoặc migration nếu chưa có yêu cầu vận hành thực tế.

**Kết quả đầu ra**

- Một run ingest thất bại không làm mất corpus đang phục vụ.
- Truy vấn Farm không bệnh lấy được evidence thuộc các topic mới; truy vấn bệnh vẫn giữ ranking và citation cũ; truy vấn Lab chỉ lấy evidence từ namespace `lab`.

## WP2-05: UI, vận hành và tài liệu

- Mục tiêu: người dùng hiểu vì sao chatbot từ chối hoặc chưa biết, còn đội vận hành biết cách cập nhật corpus.
- Ước lượng: S.

**Hướng làm**

- Hiển thị thông báo tiếng Việt theo ba trạng thái; không hiển thị điểm nội bộ nếu gây hiểu nhầm.
- Cho xem nguồn đã dùng và thời điểm cập nhật; không hiển thị raw content vượt quyền/bản quyền nếu không cần.
- Viết runbook crawl → review → dry-run → ingest → evaluate → rollback.

## Thay đổi hợp đồng API, schema, cấu hình

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Response | Endpoint hỏi đáp Farm/Lab | Thêm `scope_status`, `scope_confidence`, `reason_code`, `corpus_version`, `namespace` | Không |
| Response | `sources[]` | Thêm `locator`, `topic`, `version` nếu có | Không |
| Filter | Retriever | `namespace` và `document_type` được truyền tường minh; Farm dùng `farm`, Lab dùng `lab`; diagnosis vẫn truyền loại cũ | Không nếu truyền tường minh |
| Data | Document metadata | Giữ trong processed JSON/manifest và validate trước ingest; không thêm cột DB ở phase này | Không |
| Config | AI service | Thêm ngưỡng gate và message mặc định | Không |

## Dữ liệu, migration và rollback

- Migration: không đổi schema DB ở phase này; chỉ sửa contract JSON, validator và cấu hình ingest.
- Dữ liệu cũ: giữ nguyên `disease_knowledge`; thêm corpus mới song song, không ghi đè khi chưa đánh giá.
- Corpus cũ không đạt chính sách nguồn: đánh dấu/quarantine và không ingest lại; chỉ loại khỏi corpus phục vụ sau khi có báo cáo ảnh hưởng và người duyệt xác nhận.
- Rollback: ingest lại artifact version trước; tắt feature flag scope gate nếu gate gây false reject nghiêm trọng; không xóa snapshot nguồn.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Scope gate chạy trước retrieval và không gọi LLM để phân loại duy nhất | Giảm chi phí và tránh LLM tự mở rộng phạm vi |
| D2 | “Ngoài phạm vi” khác “đúng namespace nhưng thiếu evidence” | Hai trạng thái cần thông báo và metric khác nhau |
| D3 | Chỉ allowlist, đúng ngôn ngữ/domain và duyệt trước khi ingest | Crawl nhanh không bảo đảm thẩm quyền, chất lượng hoặc giấy phép |
| D4 | Không lấy thuốc/liều/PHI từ corpus tự do | Giữ nguyên safety boundary của DX-Farm |
| D5 | Giữ nguyên migration và dùng DB như projection RAG | Schema hiện tại đã đủ cho title, chunk, namespace, type, source, version và embedding; tránh migration chưa cần thiết |
