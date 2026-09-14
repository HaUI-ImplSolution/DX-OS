# RAG handoff — Stage 7

Input: `data/rag/processed/disease_knowledge.json` và `data/rag/source_registry.json`.
Embedding: multilingual MiniLM, 384 chiều.
Model được cache theo tên trong process.

Từ thư mục `ai`:

```bash
python -m scripts.ingest_diseases --dry-run --limit 1
python -m scripts.ingest_diseases
```

Ingest thật tạo embedding và thay thế atomically corpus document_type=disease_knowledge, version=stage7-rich-v1.
Document khác loại được giữ nguyên; lỗi DB rollback corpus cũ.
Advisory lock PostgreSQL tuần tự hóa hai lần replace đồng thời.
Chunk trùng nội dung trong cùng document được loại bỏ; re-ingest tạo lại ID document/chunk.

Output truy vấn:

```python
from app.rag.retriever import retrieve_knowledge
rows = retrieve_knowledge(db, query="Triệu chứng cháy lá muộn",
                         disease_code="Tomato___Late_blight", top_k=3)
# rows: content, disease_code, source_ids, score; thêm ID/citation metadata để audit.
```

`disease_code` là bộ lọc SQL exact; mã không tồn tại trả []; bỏ lọc sẽ tìm toàn corpus.
`score` = 1 - cosine distance; giữ `similarity` để tương thích caller cũ.
`top_k` nằm trong [1,20].
Endpoint: `POST /api/v1/disease/knowledge/retrieve` nhận JSON query/disease_code/top_k.
LangGraph Stage 8 có thể gọi `app.graph.tools.rag_tool.retrieve`.

`RAGService.answer` dùng Ollama tùy chọn.
Cổng kiểm soát đầu ra chỉ nhận đoạn trích nguyên văn từ evidence; nếu LLM thêm thông tin hoặc không phản hồi trong 30 giây, trả evidence.
`answer_mode=grounded_excerpt` thể hiện giới hạn này.
Đây là lựa chọn bảo thủ của MVP; chưa hỗ trợ diễn đạt tự do có đảm bảo factuality.
Thuốc/liều/PHI/policy được cung cấp riêng bởi structured lookup, không lấy từ LLM.
