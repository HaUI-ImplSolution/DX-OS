# Checklist nâng cấp chất lượng AI Service

## 1. Mục tiêu và phạm vi

Checklist này chuyển định hướng nâng cấp LLM, embedding và RAG thành các phase có thể triển khai, kiểm chứng và chia thành PR riêng.

Mục tiêu của lần nâng cấp là làm evidence chính xác hơn, recommendation rõ ràng hơn và hệ thống dễ tái tạo hơn, trong khi giữ nguyên ranh giới an toàn của MVP.

Phạm vi mặc định chỉ gồm:

- Mã nguồn trong `ai/`.
- Dữ liệu ingest, benchmark và công cụ quản trị thuộc AI.
- Tài liệu AI liên quan trong `development/docs/`.
- AI Vector DB ở góc độ schema, ingestion và truy xuất.

Không được sửa `backend/`, contract Backend hoặc dữ liệu nghiệp vụ của Backend nếu chưa có một quyết định phối hợp riêng được phê duyệt.

Đây là checklist kế hoạch. Tất cả mục bên dưới mặc định chưa thực hiện.

Quy ước:

- `[ ]`: chưa thực hiện.
- `[x]`: đã hoàn thành và có bằng chứng kiểm chứng.
- `[!]`: gate bắt buộc; không đạt thì không sang phase tiếp theo.
- `[BE]`: cần Backend hoặc hai bên thống nhất; chỉ ghi nhận, không thực hiện trong phạm vi AI.

## 2. Đầu ra tối thượng và các bất biến không được phép sai

### 2.1. Đầu ra mục tiêu

Legacy docs sử dụng cụm “Deterministic RAG” cho cả chuỗi tra cứu dữ liệu có cấu trúc, tìm tài liệu và diễn đạt bằng LLM. Kiến trúc hiện tại tách các trách nhiệm này để RAG không trở thành nơi quyết định phác đồ.

**Đầu ra chắc chắn của riêng RAG** là một gói evidence giải thích có nguồn, gồm nội dung liên quan, `source_id`, metadata tài liệu và điểm liên quan. Evidence có thể mô tả triệu chứng, nguyên nhân, điều kiện phát sinh, biện pháp phòng ngừa, hướng dẫn thực hiện và nguyên tắc kỹ thuật.

**Đầu ra cuối của toàn bộ AI Service** là một recommendation có căn cứ, dễ hiểu và kiểm toán được để Backend tiếp nhận ở trạng thái chờ kỹ sư phê duyệt. Recommendation phải kết hợp:

- Kết quả CV: bệnh canonical, confidence và model/version.
- Structured facts từ Backend: rule ID/version, thuốc, liều, đơn vị, PHI, field context và lịch sử xử lý.
- Evidence từ RAG: kiến thức giải thích cùng nguồn tài liệu.
- Cảnh báo khi confidence thấp, evidence yếu hoặc cần chuyên gia đánh giá.

AI/RAG không tự khóa thu hoạch. Backend chỉ tính `harvest_locked_until` sau khi việc xử lý thực tế được xác nhận `APPLIED`, không phải khi đề xuất mới được `APPROVED`.

### 2.2. Gate an toàn áp dụng cho mọi phase

- [!] `disease_code` canonical là khóa nối giữa CV, RAG và Backend; không fuzzy-match tên bệnh để nối dữ liệu.
- [!] Thuốc, hoạt chất, liều, đơn vị, PHI và rule version phải giữ nguyên từ Backend.
- [!] RAG không được tự chọn thuốc, sinh liều lượng, đặt PHI hoặc tính ngày thu hoạch.
- [!] Thiếu structured facts hợp lệ thì request phải thất bại hoặc chuyển review; không tạo recommendation chỉ dựa trên RAG.
- [!] Evidence chỉ đến từ corpus đã duyệt và phải giữ provenance; similarity cao không tự chứng minh nội dung đúng.
- [!] Confidence thấp, evidence yếu hoặc không có evidence phải được thể hiện rõ, không bị che bởi văn bản trôi chảy của LLM.
- [!] Recommendation có thuốc luôn yêu cầu kỹ sư nông nghiệp phê duyệt.
- [!] AI tiếp tục stateless và advisory; Backend sở hữu `PENDING`, `APPROVED`, `REJECTED`, `APPLIED` và toàn bộ dữ liệu nghiệp vụ.
- [!] Backend phải kiểm tra lại rule version trước khi lưu kết quả.
- [!] Khi có nhiều treatment rule, LLM không được tự ý chọn nếu chưa có tiêu chí nghiệp vụ được thống nhất.
- [!] Mọi thay đổi phải giữ tương thích contract `POST /api/v1/diagnose`, trừ khi hai bên chốt một phiên bản contract mới.
- [!] Không sửa Backend như một phần ngầm định của checklist này.

## 3. Trạng thái baseline cần đóng băng

Baseline hiện tại:

- LLM local: `qwen2.5:1.5b` qua Ollama.
- Embedding local: `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, vector 384 chiều.
- Retrieval: cosine similarity, metadata filter theo `disease_code`, lấy top-k.
- LLM chỉ được chọn excerpt nguyên văn trong evidence; không hợp lệ thì trả evidence trực tiếp.
- AI trả CV result, RAG evidence, structured context và recommendation theo contract hiện tại.
- Backend là nguồn sự thật cho structured facts và vòng đời phê duyệt.

Baseline này phải tiếp tục chạy được cho tới khi phương án mới vượt qua đầy đủ gate chất lượng, an toàn và khả năng rollback.

## 4. Phase 0 — Khóa phạm vi, compliance và tiêu chí quyết định

Mục tiêu: không bắt đầu đổi model khi chưa biết tiêu chí thắng, tài nguyên và ràng buộc license.

- [ ] **AI-U0.1** — Ghi lại phiên bản model, model revision, dependency, cấu hình inference, vector dimension và cấu hình retrieval hiện tại.
- [ ] **AI-U0.2** — Chốt ngân sách CPU, RAM, GPU/VRAM, dung lượng lưu trữ và latency mục tiêu của môi trường demo.
- [ ] **AI-U0.3** — Rà toàn bộ license của AI runtime, LLM, embedding, reranker, tokenizer, checkpoint và dataset.
- [ ] **AI-U0.4** — Bổ sung phần AI còn thiếu trong `LICENSES.md`; phân biệt rõ software license, model-weight license và mức đáp ứng Open Source AI Definition.
- [ ] **AI-U0.5** — Pin model ID, revision/commit, checksum và nguồn tải; không dùng tag trôi nổi làm release.
- [ ] **AI-U0.6** — Xác nhận không có API/model đóng nào là dependency bắt buộc của luồng chạy và demo.
- [ ] **AI-U0.7** — Chốt tiêu chí pass/fail chung: chất lượng, an toàn, latency, tài nguyên, khả năng tái tạo và tương thích contract.
- [!] **Gate 0** — Chỉ sang Phase 1 khi mọi thành phần dự kiến sử dụng có license rõ ràng và tiêu chí đánh giá đã được chốt.

## 5. Phase 1 — Bộ benchmark và số liệu MVP

Mục tiêu: có bằng chứng định lượng để biết lần nâng cấp có thực sự tốt hơn.

- [ ] **AI-U1.1** — Tạo tập truy vấn tiếng Việt được chuyên gia duyệt cho từng nhóm bệnh, triệu chứng và ngữ cảnh ruộng.
- [ ] **AI-U1.2** — Có trường hợp bệnh gần giống nhau, truy vấn mơ hồ, không có evidence, confidence thấp và structured facts không khả dụng.
- [ ] **AI-U1.3** — Gắn expected document/source cho từng truy vấn retrieval.
- [ ] **AI-U1.4** — Gắn expected behavior cho recommendation: trả lời, từ chối hoặc chuyển chuyên gia.
- [ ] **AI-U1.5** — Bảo đảm dữ liệu benchmark có nguồn gốc và quyền sử dụng rõ ràng; không chứa bí mật hoặc dữ liệu cá nhân.
- [ ] **AI-U1.6** — Viết công cụ đánh giá có thể chạy lại, không phụ thuộc dịch vụ đóng.
- [ ] **AI-U1.7** — Đo Recall@k, MRR/NDCG, citation accuracy, groundedness, latency, RAM và VRAM.
- [ ] **AI-U1.8** — Ghi baseline của MiniLM + cosine top-k + Qwen2.5 1.5B hiện tại.
- [!] **Gate 1** — Benchmark chạy lặp lại được và chứng minh tỷ lệ sai thuốc/liều/PHI bằng 0 trước khi thay đổi retrieval hoặc model.

## 6. Phase 2 — Chuẩn hóa corpus, chunking và metadata

Mục tiêu: cải thiện chất lượng dữ liệu trước khi tăng kích thước model.

- [ ] **AI-U2.1** — Kiểm kê toàn bộ corpus, loại tài liệu, phiên bản, tổ chức phát hành và quyền sử dụng.
- [ ] **AI-U2.2** — Chỉ ingest tài liệu đã duyệt; loại bản trùng, bản lỗi thời hoặc không xác định nguồn.
- [ ] **AI-U2.3** — Chuẩn hóa source registry và bắt buộc mỗi document/chunk truy ngược được tới tài liệu gốc.
- [ ] **AI-U2.4** — Chia chunk theo ý nghĩa nghiệp vụ: triệu chứng, nguyên nhân, điều kiện phát sinh, phòng ngừa, hướng dẫn và nguyên tắc kỹ thuật.
- [ ] **AI-U2.5** — Bổ sung metadata cần thiết như cây trồng, bệnh, loại tài liệu, phiên bản, ngôn ngữ và phạm vi áp dụng.
- [ ] **AI-U2.6** — Không đưa thuốc, liều hoặc PHI từ corpus thành nguồn sự thật thay cho Backend.
- [ ] **AI-U2.7** — Validate schema, source ID, chunk rỗng, duplicate và quan hệ `disease_code` trước ingest.
- [ ] **AI-U2.8** — Re-ingest bằng job quản trị riêng; AI runtime vẫn chỉ có quyền đọc.
- [!] **Gate 2** — Mọi evidence trả về đều có nguồn hợp lệ và corpus mới không làm thay đổi ranh giới structured facts.

## 7. Phase 3 — Cải thiện query và retrieval

Mục tiêu: tăng độ liên quan của evidence mà chưa cần đổi embedding.

- [ ] **AI-U3.1** — Thay câu hỏi chung chung bằng truy vấn tiếng Việt từ disease code/name, triệu chứng và field context có sẵn.
- [ ] **AI-U3.2** — Giữ filter bằng `disease_code` canonical và kiểm tra hành vi khi code không có tài liệu tương ứng.
- [ ] **AI-U3.3** — Thử hybrid retrieval giữa pgvector semantic search và PostgreSQL full-text search.
- [ ] **AI-U3.4** — Dùng phương pháp hợp nhất thứ hạng có thể giải thích, ví dụ Reciprocal Rank Fusion.
- [ ] **AI-U3.5** — Lấy nhiều ứng viên ban đầu nhưng chỉ trả số evidence cần thiết, không đẩy context dư thừa sang LLM.
- [ ] **AI-U3.6** — Xác lập threshold và nhánh abstain khi evidence yếu hoặc không liên quan.
- [ ] **AI-U3.7** — Giữ `source_id`, document/chunk ID, similarity và provenance xuyên suốt output.
- [ ] **AI-U3.8** — So sánh với baseline Phase 1 bằng cùng benchmark.
- [BE] **Phối hợp nếu cần** — Nếu hybrid search cần thay schema/index do Backend hiện đang khởi tạo AI Vector DB, yêu cầu phía sở hữu initializer thực hiện ở một task riêng; không sửa `backend/` trong PR AI.
- [!] **Gate 3** — Retrieval mới phải tốt hơn baseline trên benchmark, không làm tăng false evidence và vẫn fallback an toàn khi RAG không khả dụng.

## 8. Phase 4 — Thêm reranker

Mục tiêu: xếp lại các ứng viên retrieval trước khi gửi evidence cho LLM.

- [ ] **AI-U4.1** — Benchmark reranker đa ngôn ngữ có license phù hợp, ưu tiên `BAAI/bge-reranker-v2-m3` Apache-2.0.
- [ ] **AI-U4.2** — Tách cấu hình số candidate được retrieve và số evidence được giữ sau rerank.
- [ ] **AI-U4.3** — Không dùng reranker để sửa nội dung hoặc sinh facts; reranker chỉ thay đổi thứ tự.
- [ ] **AI-U4.4** — Đo mức tăng retrieval quality so với latency, RAM và VRAM bổ sung.
- [ ] **AI-U4.5** — Có thể tắt reranker bằng cấu hình và quay về retrieval Phase 3.
- [!] **Gate 4** — Chỉ giữ reranker nếu mức tăng chất lượng có ý nghĩa và vẫn nằm trong ngân sách tài nguyên đã chốt.

## 9. Phase 5 — Đánh giá và chuyển embedding

Mục tiêu: chỉ đổi embedding khi đã chứng minh lợi ích trên dữ liệu DX-Farm.

Ứng viên ban đầu:

- `BAAI/bge-m3` — MIT.
- `intfloat/multilingual-e5-base` hoặc `multilingual-e5-large` — MIT.
- MiniLM hiện tại tiếp tục là baseline và phương án rollback.

Tasks:

- [ ] **AI-U5.1** — Benchmark các embedding trên cùng corpus, query và metric của Phase 1.
- [ ] **AI-U5.2** — Đo chất lượng tiếng Việt, model load time, ingest time, latency và tài nguyên.
- [ ] **AI-U5.3** — Chọn model bằng kết quả benchmark, không chọn chỉ dựa trên kích thước hoặc bảng xếp hạng công khai.
- [ ] **AI-U5.4** — Pin revision, checksum, license và hướng dẫn tải model.
- [ ] **AI-U5.5** — Thiết kế migration/re-ingest toàn bộ corpus; không trộn vector từ hai model trong cùng index.
- [ ] **AI-U5.6** — Có phương án rollback về model/vector cũ nếu ingest hoặc runtime mới thất bại.
- [BE] **Phối hợp bắt buộc nếu đổi vector dimension** — AI DB schema và initializer phải đổi đồng bộ. Phần sửa initializer hiện nằm phía Backend/hạ tầng phải được tách thành task/PR riêng do bên sở hữu phê duyệt.
- [!] **Gate 5** — Không chuyển production/demo sang embedding mới trước khi schema, toàn bộ corpus và runtime cùng dùng đúng một model revision và dimension.

## 10. Phase 6 — Nâng LLM và grounded recommendation

Mục tiêu: tạo lời giải thích rõ hơn nhưng không mở rộng quyền quyết định của LLM.

Ứng viên cần benchmark theo tài nguyên:

- Qwen3 4B quantized cho tài nguyên thấp.
- Qwen3 8B quantized cho GPU tầm trung.
- Qwen3 14B quantized chỉ khi tài nguyên và latency cho phép.

Tasks:

- [ ] **AI-U6.1** — Benchmark model mới với Qwen2.5 1.5B trên cùng input và safety cases.
- [ ] **AI-U6.2** — Giữ temperature thấp, giới hạn token và timeout rõ ràng.
- [ ] **AI-U6.3** — Tạo grounded synthesis chỉ từ structured facts và evidence đã được chọn.
- [ ] **AI-U6.4** — Validate output bằng schema cố định; reject field thừa và nội dung không có căn cứ.
- [ ] **AI-U6.5** — So khớp thuốc, liều, đơn vị, PHI, selected rule và version với structured facts trước khi trả.
- [ ] **AI-U6.6** — Mọi phát biểu từ tài liệu phải gắn citation/source tương ứng.
- [ ] **AI-U6.7** — Giữ deterministic fallback khi Ollama/LLM lỗi, timeout hoặc vi phạm validator.
- [ ] **AI-U6.8** — Không để LLM chọn giữa nhiều treatment rule nếu chưa có tiêu chí nghiệp vụ được chốt.
- [ ] **AI-U6.9** — Bộc lộ low confidence/no evidence/review required trong logic nội bộ và log.
- [BE] **Phối hợp nếu đổi response DTO** — Nếu muốn Backend nhận thêm `low_confidence`, `generation_mode`, citation chi tiết hoặc lý do abstain, hai bên phải version contract trước. Nếu chưa chốt, AI phải giữ response hiện tại.
- [BE] **Phối hợp về recommendation** — Backend hiện cần xác nhận cách sử dụng đầy đủ `recommendation.summary`; checklist AI không tự sửa logic lưu/hiển thị của Backend.
- [!] **Gate 6** — Tỷ lệ LLM sinh sai hoặc sửa thuốc/liều/PHI phải bằng 0; mọi output không hợp lệ phải bị validator chặn và fallback.

## 11. Phase 7 — Hiệu năng, quan sát và khả năng tái tạo

Mục tiêu: bản nâng cấp chạy ổn định trong môi trường demo và có thể dựng lại từ source.

- [ ] **AI-U7.1** — Cache model và embedding truy vấn lặp lại có giới hạn, không làm sai version.
- [ ] **AI-U7.2** — Batch embedding trong ingestion.
- [ ] **AI-U7.3** — Đánh giá HNSW/index phù hợp khi corpus đủ lớn; ghi rõ tham số và cách rebuild.
- [ ] **AI-U7.4** — Log riêng thời gian CV, Backend lookup, retrieval, rerank, LLM và validator theo `request_id`.
- [ ] **AI-U7.5** — Không log API key, dữ liệu nhạy cảm hoặc toàn bộ payload ảnh.
- [ ] **AI-U7.6** — Có health check và lỗi phân biệt cho model, Vector DB, Backend facts và LLM.
- [ ] **AI-U7.7** — Kiểm tra Docker build, Compose config và chạy với model cache.
- [ ] **AI-U7.8** — Tài liệu hóa lần tải model đầu tiên, chế độ offline sau khi cache và yêu cầu tài nguyên.
- [ ] **AI-U7.9** — Chỉ đánh giá vLLM thay Ollama khi tải thực tế yêu cầu; runtime mới phải được rà license và benchmark riêng.
- [!] **Gate 7** — Build/run có thể tái tạo, lỗi có fallback rõ ràng và latency/tài nguyên không vượt ngân sách Phase 0.

## 12. Phase 8 — Fine-tuning tùy chọn

Phase này không bắt buộc. Chỉ thực hiện nếu các phase trước chưa đạt chất lượng mục tiêu.

- [ ] **AI-U8.1** — Chứng minh bằng benchmark rằng prompt/retrieval/reranker/model có sẵn chưa đủ.
- [ ] **AI-U8.2** — Rà quyền sử dụng, provenance, dữ liệu cá nhân và khả năng công bố thông tin dữ liệu huấn luyện.
- [ ] **AI-U8.3** — Fine-tune embedding bằng query-positive-hard-negative đã được chuyên gia duyệt.
- [ ] **AI-U8.4** — Fine-tune LLM bằng LoRA trên recommendation grounded và trường hợp phải từ chối.
- [ ] **AI-U8.5** — Công bố code, cấu hình, hyperparameter, base model revision và adapter checksum.
- [ ] **AI-U8.6** — Chạy lại toàn bộ benchmark, safety gate và license review.
- [!] **Gate 8** — Không dùng adapter fine-tune nếu không chứng minh được cải thiện, không tái tạo được hoặc dữ liệu/license không đủ rõ.

## 13. Phase 9 — Nghiệm thu, tài liệu và release OLP

Mục tiêu: khóa bản nâng cấp thành một sản phẩm có bằng chứng kỹ thuật và đáp ứng PoF.

- [ ] **AI-U9.1** — Chạy regression cho CV, Backend client contract, RAG access boundary và diagnose contract.
- [ ] **AI-U9.2** — Chạy benchmark cuối và so sánh trực tiếp với baseline Phase 1.
- [ ] **AI-U9.3** — Kiểm tra các failure case: RAG down, Ollama down, Backend facts down, evidence yếu, low confidence và rule không hợp lệ.
- [ ] **AI-U9.4** — Xác nhận AI runtime chỉ đọc AI Vector DB và không có credential Backend DB hoặc ingest role.
- [ ] **AI-U9.5** — Rà SPDX header, `LICENSE`, `LICENSES.md`, dependency/model/dataset license và license compatibility.
- [ ] **AI-U9.6** — Cập nhật README, hướng dẫn build/run từ source, cấu hình, ingest, benchmark và giới hạn đã biết.
- [ ] **AI-U9.7** — Cập nhật changelog ngắn gọn theo ngày sau khi kết quả thật sự hoàn thành.
- [ ] **AI-U9.8** — Ghi issue/bug tracker cho phần chưa hoàn thành hoặc phụ thuộc Backend.
- [ ] **AI-U9.9** — Chuẩn bị release có version ở định dạng mở được thể lệ chấp nhận; không bundle model/dependency của bên thứ ba vào source release.
- [ ] **AI-U9.10** — Chuẩn bị demo chứng minh provenance, fallback, validator và yêu cầu kỹ sư phê duyệt.
- [!] **Gate 9** — Chỉ đánh dấu nâng cấp hoàn thành khi chất lượng tốt hơn baseline, mọi safety invariant còn nguyên, tài liệu khớp code và không còn dependency license chưa rõ.

## 14. Các điểm cần Backend hoặc hai bên thống nhất

Các mục dưới đây không cấp quyền sửa Backend. Mỗi mục cần quyết định riêng trước khi tạo task ngoài phạm vi AI:

- [BE] Contract cho `management_policy` và nhánh `NO_CHEMICAL_CONTROL`.
- [BE] Tiêu chí chọn một rule khi Backend trả nhiều treatment rule hợp lệ.
- [BE] Cách Backend sử dụng và lưu `recommendation.summary` do AI trả về.
- [BE] Có mở rộng response DTO cho low confidence, abstain reason, generation mode và citation chi tiết hay không.
- [BE] Quyền sở hữu và cách thay đổi initializer/schema AI Vector DB khi đổi vector dimension hoặc index.
- [BE] Versioning contract và chiến lược tương thích ngược nếu schema request/response thay đổi.

Nếu chưa thống nhất các mục này, AI phải giữ contract và hành vi MVP hiện tại, đồng thời ghi rõ giới hạn thay vì tự giả định.

## 15. Thứ tự PR đề xuất

Mỗi PR chỉ thuộc AI hoặc tài liệu AI:

1. Benchmark và baseline.
2. Corpus, source registry, chunking và metadata.
3. Query + hybrid retrieval.
4. Reranker.
5. Embedding migration.
6. LLM + validator.
7. Performance, observability và reproducibility.
8. Compliance, tài liệu và release readiness.
9. Fine-tuning chỉ tách riêng nếu Phase 8 được kích hoạt.

Không gộp thay đổi Backend vào các PR này.

## 16. Nguồn bắt buộc tham chiếu

- [`DX - Farm.docx`](./legacy_docs/DX%20-%20Farm.docx) — mục tiêu nghiệp vụ và spine flow ban đầu.
- [`DX-Farm_MVP_DeXuat_Duyet_Lead.docx`](./legacy_docs/DX-Farm_MVP_DeXuat_Duyet_Lead.docx) — ranh giới Structured Knowledge, RAG, LLM và Human-in-the-loop.
- [`ai_backend_decoupling_plan.md`](./ai_backend_decoupling_plan.md) — nguồn sự thật cho kiến trúc Backend–AI và contract hiện tại.
- [`ai_backend_decoupling_checklist.md`](./ai_backend_decoupling_checklist.md) — trạng thái triển khai decoupling.
- [Thể lệ OLP Phần mềm nguồn mở 2026](https://www.vfossa.vn/tin-tuc/the-le-olp-phan-mem-nguon-mo-nam-2026-760.html).
- [Open Source AI Definition 1.0](https://opensource.org/ai/open-source-ai-definition).
- Model card và license chính thức của từng model được đưa vào benchmark.

Theo thể lệ OLP PMNM 2026, dự án phải công khai mã nguồn, dùng license OSI-approved, build được từ source, làm rõ dependency, có release, README, changelog và bug tracker. Checklist này coi các yêu cầu đó là gate phát hành, không phải việc bổ sung sau cùng.
