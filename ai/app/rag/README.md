# RAG: tra cứu kiến thức

`app/rag/` biến kiến thức đã duyệt thành các đoạn tra cứu được và trả evidence kèm nguồn cho workflow hoặc LLM.
RAG chỉ giải thích: thuốc, liều, PHI, policy an toàn luôn lấy từ Backend qua REST.
RAG không tự crawl nguồn mới, không seed bệnh, thuốc, rule và không thay phê duyệt của kỹ sư.

Kho tri thức tách namespace: `farm` chứa `disease_knowledge` và `farm_knowledge`; `lab` chứa `lab_knowledge`.
Nguồn và policy chatbot khai báo trong `data/rag/chat_profiles.json`; registry nguồn ở `data/rag/` và danh mục trích dẫn của Backend phải khớp.

Chatbot gọi `/api/v1/knowledge/search`: từ chối chủ đề rõ ngoài miền, cho câu chưa rõ thử tìm trong namespace, rồi kiểm evidence.
Kết quả có `OUT_OF_SCOPE`, `IN_SCOPE_NO_EVIDENCE` hoặc `ANSWERABLE`; model rerank lỗi không được giả thành kết quả đã rerank.
Diagnosis/ticket giữ `/api/v1/disease/knowledge/retrieve` và filter cũ.

## Ingest

```text
corpus + source_registry
  → kiểm trường bắt buộc, source_id, disease_code
  → chunk tối đa 350 ký tự, overlap một dòng
  → embedding 384 chiều (multilingual MiniLM) của "tiêu đề tài liệu + đoạn"
  → khóa advisory transaction, thay thế atomically corpus của namespace
  → lưu documents, document_chunks
```

```bash
export RAG_INGEST_DATABASE_URL=postgresql+psycopg://<ingest-user>:<password>@<host>:5432/<ai-vector-db>
python -m scripts.ingest_diseases [--corpus lab] [--dry-run]
```

- Runtime FastAPI không ingest và chỉ dùng `DATABASE_URL` (chỉ đọc); credential ingest chỉ cấp cho job quản trị.
- `--dry-run` kiểm dữ liệu và preview chunk, không ghi database.
- Document lưu tiêu đề, `disease_code`, loại, version, `source_id`; chunk lưu nội dung, vị trí và `vector(384)`.
  Đổi embedding model thì phải ingest lại.

## Tra cứu

```text
câu hỏi (+ namespace, disease_code, document_type) → embedding → cosine search pgvector
  → top-k (1–20) chunk, score, provenance, vị trí chunk trong tài liệu (`chunk_index`)
```

Chatbot dùng nhánh riêng:

```text
query + namespace/filter → BM25 và cosine cùng snapshot
  → RRF → tối đa 20 ứng viên → CrossEncoder multilingual MiniLM
  → ngưỡng evidence → top-k → Backend gộp → Qwen theo profile QA
```

BM25 index chỉ đọc, cache theo metadata và UUID tài liệu, tách namespace/filter; ingest thay tài liệu bằng UUID mới nên cache tự đổi kể cả version không đổi.
Chỉ tải nội dung chunk khi cache chưa có; request sau lấy top ứng viên cosine bằng SQL LIMIT và hợp với ứng viên BM25 toàn index trong cùng snapshot read-only/repeatable-read.
Thay nội dung qua ingest, không cập nhật chunk trực tiếp trong DB vì cache theo vòng đời tài liệu.
Index giới hạn 20.000 chunk, cache tối đa bốn snapshot; BM25 vẫn chạy trên RAM, chưa phải thiết kế cho kho lớn.
Câu tổng quan có thể lấy nhiều tài liệu; rerank chạy ngoài transaction DB, có giới hạn concurrency và thời gian.
Trước rerank, cosine tốt nhất của câu hỏi phải đạt `chat_cosine_min_score` (mặc định 0,40); sau đó từng đoạn vẫn phải đạt ngưỡng rerank.
Câu thiếu liên quan trả `IN_SCOPE_NO_EVIDENCE`/`LOW_RELEVANCE`, không gọi Qwen.
Một đoạn BM25 có cosine thấp vẫn có thể được chọn nếu câu hỏi vượt kiểm tra liên quan chung và đoạn đó đạt rerank.
`score`/`similarity` vẫn là cosine; BM25, RRF và rerank có field riêng, không phải xác suất đúng.
Reranker mặc định là `cross-encoder/mmarco-mMiniLMv2-L12-H384-v1`, cache model được chuẩn bị qua ingest.

Có `disease_code` từ CV thì workflow truyền vào để giới hạn context đúng bệnh.
Câu hỏi gọi tên chủ đề của một tài liệu thì đoạn của tài liệu đó được xếp lên trước.
Tên chủ đề là tiêu đề bỏ "Bệnh" và "trên lúa", hoặc tên trong ngoặc.
Điểm trả về là độ tương đồng cosine.

## Thành phần

| Thành phần | Vai trò |
|---|---|
| `embedding_service.py` | Nạp và cache MiniLM, tạo vector đã normalize |
| `ingestion_service.py` | Validate corpus, chunk, embedding, thay thế corpus trong transaction |
| `chunker.py` | Chia theo dòng, tối đa 350 ký tự/chunk |
| `retriever.py` | Embedding câu hỏi, cosine legacy cho diagnosis/ticket |
| `chat_search.py` | BM25/cosine, RRF, scope và chọn evidence chatbot |
| `reranker.py` | CrossEncoder local, cache và ngân sách rerank |
| `rag_service.py` | Gọi Qwen sinh câu trả lời có cấu trúc và kiểm tra trước khi trả |
| `answer_contract.py` | Schema câu trả lời sinh và giới hạn evidence do Backend cung cấp |
| `prompt_builder.py` | Prompt có guardrail: chỉ trả lời từ context, không bịa thuốc, liều, PHI |
| `safety.py` | Lọc dòng chứa thuốc, liều hoặc PHI trước khi evidence Farm rời AI |
| `source_registry.py` | Kiểm registry, ánh xạ `source_id` sang metadata trích dẫn |

## Kiểm soát câu trả lời và giới hạn

Qwen dùng ba profile riêng: hỏi đáp Farm, hỏi đáp Lab và giải thích chẩn đoán Farm.
Nguồn của Lab được giới hạn theo namespace; giải thích chẩn đoán được giới hạn theo mã bệnh CV.

| Luồng | Ngữ cảnh và đầu ra | Giới hạn gọi model |
|---|---|---|
| Chatbot Farm/Lab | Tối đa 2.000 ký tự tài liệu, giữ nguyên dòng; câu trả lời gồm tóm tắt, tối đa hai ý hướng dẫn và nguồn. Câu tổng quan có thể dùng nhiều tài liệu | Tối đa 320 token, 30 giây |
| Chẩn đoán Farm | Khóa mã/tên bệnh từ CV; giải thích một ý có căn cứ, tách khỏi phác đồ. Thiếu tài liệu hoặc hỏi thuốc/liều/PHI thì không gọi model | Tối đa 192 token, 20 giây, không thử lại |

- Chỉ chấp nhận nội dung đúng schema, nguồn thuộc evidence và không có mâu thuẫn đã được kiểm tra về bệnh hoặc điều kiện áp dụng.
- Ở Lab, thời hạn (ví dụ 30 ngày) phải đi cùng điều kiện của câu nguồn nêu nó; ý chỉ có trong dòng "Với sinh viên:" hay "Với cán bộ, giảng viên:" phải nêu đúng đối tượng đó.
- Câu trả lời chỉ lặp lại câu hỏi hoặc tên tài liệu không được tính là `generated`.
- Qwen không sinh thuốc, liều hoặc PHI, không sửa evidence và dữ liệu Backend; hỏi đáp không tự xác nhận bệnh cho người dùng.
- Mode `generated` chứa nội dung đã qua kiểm tra.
  Mode `extractive`/`insufficient_context` không trả văn bản dự phòng; hệ thống trả mã lý do và giữ đoạn trích tham khảo.
- Mục phụ sai định dạng được loại bỏ; phần trả lời chính hợp lệ vẫn được giữ.
  Giao diện không render Markdown/HTML từ model.
- Chatbot chờ CPU tối đa 12 giây, rerank tối đa 6 giây.
  Chỉ tìm được kiến thức đã ingest; điểm tương đồng không xác nhận nội dung trả lời là đúng.

Cách đo chất lượng truy xuất và độ trễ: [`ai/README.md`](../../README.md#đánh-giá-truy-xuất-rag).
