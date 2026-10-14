# RAG: tra cứu kiến thức

`app/rag/` biến kiến thức đã duyệt thành các đoạn tra cứu được và trả evidence kèm nguồn cho workflow hoặc LLM.
RAG chỉ giải thích: thuốc, liều, PHI, policy an toàn luôn lấy từ Backend qua REST.
RAG không tự crawl nguồn mới, không seed bệnh, thuốc, rule và không thay phê duyệt của kỹ sư.

Mỗi corpus là một namespace: `farm` (6 tài liệu bệnh lúa, `data/rag/processed/disease_knowledge.json`) và `lab` (10 bài quy chế, `data/rag/lab/processed/lab_knowledge.json`).
Mỗi corpus đi kèm registry nguồn ở `data/rag/`.

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
  → top-k (1–20) chunk, score, provenance
```

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
| `retriever.py` | Embedding câu hỏi, cosine search |
| `rag_service.py` | Ghép evidence, gọi Ollama khi khả dụng, tạo citations |
| `prompt_builder.py` | Prompt có guardrail: chỉ trả lời từ context, không bịa thuốc, liều, PHI |
| `source_registry.py` | Kiểm registry, ánh xạ `source_id` sang metadata trích dẫn |

## Guardrail và giới hạn

- Kết quả luôn có `source_ids`, document/chunk ID, similarity và nội dung evidence.
- `RAGService` chỉ nhận câu trả lời của LLM khi xuất hiện nguyên văn trong evidence; Ollama lỗi, timeout hoặc nội dung không chứng minh được thì trả excerpt evidence.
- Chỉ truy xuất được kiến thức đã ingest; similarity cao không xác nhận một khuyến nghị là đúng.
