# Trade-off và rủi ro: Phase 2

## 1. Trade-off

| # | Quyết định | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|
| TO1 | Chatbot search riêng, giữ retrieve legacy | Endpoint nội bộ có envelope | Scope/ranking mới không đổi diagnosis và ticket | Thêm một contract cần kiểm | Thấp |
| TO2 | BM25 in-process + pgvector cosine/RRF | Dùng DB/cache hiện có, thêm thư viện BM25 nhỏ | Không cần search cluster hoặc schema mới | Index RAM và scan/version-check có giới hạn quy mô | Vừa |
| TO3 | CrossEncoder chỉ rerank tập ứng viên | Đo model multilingual trước khi chốt | Xếp hạng theo query–passage | Tăng CPU/RAM/latency; không cứu được candidate bị bỏ sót | Thấp |
| TO4 | Gate mềm với câu chưa rõ | Cho retrieval thử, kiểm evidence sau | Giảm false reject câu tổng quan | Tăng request retrieval, cần calibration tốt | Thấp |
| TO5 | Corpus có phê duyệt, Lab allowlist host chính thức | Owner đã duyệt 7 tài liệu hiện có; không tự duyệt nguồn tương lai | Giữ coverage và provenance hợp kiến trúc | Quyền dùng và nhãn vẫn cần kiểm riêng | Vừa |
| TO6 | Qwen QA đa tài liệu, context có giới hạn | Chỉ sửa farm_qa/lab_qa | Trả lời tổng quan có nhiều khía cạnh | Có thể vẫn thiếu ý hoặc trộn điều kiện nếu chọn context kém | Thấp |
| TO7 | Langfuse replay sau đo | Dùng runner/phiên bản hiện có | Trực quan và ít tranh tài nguyên lúc benchmark | Không có tracing live của toàn sản phẩm | Thấp |

Các lựa chọn này là thiết kế kế hoạch, chưa là kết quả chứng minh model hoặc ngưỡng tối ưu.
Model/ngân sách và tiêu chí cần khóa ở mốc trong overview trước khi triển khai/đo holdout.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Corpus chỉ có mục lục/tin giới thiệu | Cao | Cao | Review nội dung và ma trận câu–nguồn; lấy phần gốc được phép dùng | Có title đúng nhưng không có đoạn trả lời | Để thiếu evidence, bổ sung nguồn trước ingest |
| R2 | Policy SICT-only làm rỗng Lab hoặc loader chặn startup | Đã thấy ở artifact | Cao | Rà host chính thức theo nguồn, đồng bộ registry/loader/quarantine | Validator lab_active=0, load_corpus lỗi | Giữ corpus Lab hợp lệ trước đó; chặn ingest rỗng |
| R3 | Rule từ khóa vẫn từ chối câu tổng quan | Cao | Cao | Một gate, câu mơ hồ được tìm; holdout không từ khóa theo app | Scope recall thấp dù có evidence | Điều chỉnh bằng calibration, không thêm whitelist từng câu test |
| R4 | BM25 tokenizer làm mất nghĩa tiếng Việt | Vừa | Vừa | Chuẩn hóa nhất quán, test đ/Đ, số/đơn vị, ablation | BM25 yếu với không dấu/thuật ngữ | Sửa tokenizer có đo; không thêm NLP framework tùy tiện |
| R5 | Trộn BM25/RRF/rerank/cosine cùng một threshold | Vừa | Cao | Component fields riêng, AI gate theo strategy; score cũ vẫn cosine | Kết quả BM25 hữu ích bị min-score Core loại | Rollback chatbot adapter/strategy, giữ legacy |
| R6 | Reranker quá nặng/chậm hoặc không hợp tiếng Việt | Vừa | Cao | Spike trên lock hiện có, giới hạn candidate/length/batch/concurrency | Vượt RAM, p95 gần HTTP timeout benchmark 20 giây | Giảm tải hoặc trình model khác để chốt; không tự bỏ rerank rồi báo xong |
| R7 | Index BM25 cũ sau ingest/rollback hoặc trộn snapshot | Vừa | Cao | Index theo namespace/filter/snapshot, swap atomic và test đồng thời | Chunk ID/content không khớp provenance | Invalidate và dựng lại, không trả evidence không nhất quán |
| R8 | Qwen đa nguồn gán sai điều kiện hoặc đối tượng | Vừa | Cao | Context nguyên ý, source từng ý; ca thư viện/Wi-Fi bắt buộc | Câu nghe hợp lý nhưng điều kiện khác nguồn | Hạn chế context đúng chủ đề/notice, không nới guard diagnosis |
| R9 | Tối ưu trên holdout hoặc qrels thiên lệch ranking | Vừa | Cao | Split theo họ câu, người duyệt không xem ranking, khóa ngưỡng | Điểm pilot cao nhưng query mới thất bại | Giữ draft, rà nhãn và đo lại trên holdout mới |
| R10 | Mở rộng CORPORA làm evaluation nhầm namespace/nạp draft | Đã thấy trong code | Cao | Manifest approved, dùng corpus.namespace, snapshot signature | farm_knowledge xuất hiện như namespace | Fail trước ghi, giữ artifact cũ và sửa bootstrap |
| R11 | Nguồn chính thống nhưng không có quyền tái phân phối | Vừa | Cao | Kiểm terms/license riêng từng nguồn, giới hạn excerpt/raw | Không xác minh quyền dùng | Không ingest/public raw; tìm nguồn hoặc xin phép |
| R12 | Đo retrieval rồi kết luận Qwen/diagnosis đúng | Vừa | Cao | Boundary/label rõ, manual review tách riêng | Generated rate bị gọi accuracy | Sửa báo cáo, không công bố khẳng định vượt bằng chứng |
| R13 | Runner dọn nhầm hoặc chiếm quá nhiều máy | Thấp | Cao | Giữ project labels/lock/trap, resource guard, benchmark tuần tự | Container sản phẩm bị stop hoặc swap tăng | Dừng evaluation đúng project, giữ sản phẩm và artifact |
| R14 | Bật hybrid vô tình cho diagnosis/ticket | Thấp | Cao | Endpoint riêng, giữ defaults và regression snapshot | Ranking bệnh/ticket thay đổi | Rollback chatbot code/config, không re-ingest disease corpus |
| R15 | Raw HTML/checksum/state thừa bị push | Vừa | Vừa | Rà staged diff, license, artifact và mã không cần công khai | File raw lớn hoặc credential lẫn corpus | Giữ riêng ngoài repo, không đổi .gitignore để che thiếu review |

## 3. Nợ kỹ thuật chấp nhận

- BM25 trong RAM phù hợp corpus nhỏ hiện tại; khi corpus vượt ngân sách RAM hoặc thời gian dựng index thì cần kế hoạch search/index riêng.
- Không nâng embedding/chunking, thêm hội thoại nhiều lượt, query-rewrite bằng LLM, GraphRAG hoặc chấm bằng LLM judge trong đợt này.
- Qwen schema/guard không chứng minh mọi câu đều đúng; còn cần kiểm nội dung theo nguồn và đúng điều kiện.
- Thiếu chuyên gia/nhãn độc lập thì giữ pilot/draft, không dùng điểm nội bộ làm chứng nhận của bên thứ ba.
- Không kiểm hoặc sửa lỗi booking CI phụ thuộc thời gian trong domain chatbot này; giữ ghi nhận BACKLOG hiện có.

## 4. Điều kiện dừng và rollback

Dừng promotion/ingest khi chưa duyệt, corpus Lab rỗng, nội dung không có quyền dùng hoặc source mapping thiếu.
Dừng phát hành khi chéo namespace, sinh treatment facts, sai thẩm quyền, ranking diagnosis đổi hoặc cleanup đụng sản phẩm.
Dừng đo chính thức khi dataset/config chưa khóa, snapshot mismatch hoặc resource budget không đáp ứng.

Rollback adapter/strategy chatbot về bản trước và ingest lại artifact approved theo đúng namespace + document_type nếu cần.
Giữ nguyên database nghiệp vụ và corpus bệnh; invalidation BM25 theo snapshot sau rollback.
Rerank lỗi chỉ suy giảm khi có ngưỡng fallback đã đo; nếu không thì notice/thiếu evidence.
Evaluation down chỉ dừng tài nguyên đánh giá, giữ lịch sử/cache/volume; không prune hoặc down deploy.

## 5. Tác động ngoài phase

Core chỉ đổi adapter Knowledge trung tính, không thay kiến trúc Open-Core, chọn app hoặc nghiệp vụ.
Farm/Lab web có câu trả lời tổng quan hơn và nguồn rõ; mobile/diagnosis/ticket/deploy/CI giữ nguyên phạm vi đã nêu.
AI runtime có thêm chi phí rerank, cần kiểm cả khi cùng máy chạy sản phẩm và công cụ đánh giá.
Việc nâng corpus không tự biến tri thức RAG thành treatment rule hoặc quy định mới của đơn vị.
