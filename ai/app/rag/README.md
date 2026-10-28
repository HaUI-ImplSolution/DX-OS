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

BM25 index chỉ đọc, cache theo tập chunk/title/content/version và tách namespace/filter.
Câu tổng quan có thể lấy nhiều tài liệu; rerank chạy ngoài transaction DB, có giới hạn concurrency và thời gian.
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
| `rag_service.py` | Ghép evidence, gọi Ollama khi khả dụng, tạo citations |
| `answer_contract.py` | Schema câu trả lời sinh và giới hạn evidence do Backend cung cấp |
| `prompt_builder.py` | Prompt có guardrail: chỉ trả lời từ context, không bịa thuốc, liều, PHI |
| `source_registry.py` | Kiểm registry, ánh xạ `source_id` sang metadata trích dẫn |

## Guardrail và giới hạn

- Kết quả luôn có `source_ids`, document/chunk ID, similarity và nội dung evidence.
- `synthesize_answer` dùng Qwen với ba profile: hỏi đáp Farm, hỏi đáp Lab và giải thích kết quả CV; hỏi đáp không xác nhận bệnh cá nhân, không sửa evidence hoặc structured facts.
- Câu trả lời có summary ngắn, tối đa hai ý hướng dẫn khi phù hợp và nguồn thuộc evidence; không render Markdown/HTML, không sinh thuốc/liều/PHI.
- Mode `generated` là đầu ra model đã qua kiểm tra; `extractive`/`insufficient_context` không trả văn bản dự phòng, reason cho biết lý do.
- Lab chỉ sinh từ nguồn thuộc danh mục namespace hiện tại; vector local cũ cần được đồng bộ bằng quy trình ingest sẵn có.
- Chẩn đoán khóa mã/tên bệnh CV và chỉ cấp một ý có căn cứ cho Qwen; chặn các mâu thuẫn cụ thể về bệnh/điều kiện, giữ evidence gốc nguyên vẹn.
- Không yêu cầu câu sinh khớp từng từ nguồn hoặc có dấu chấm cuối; bỏ mục phụ lỗi định dạng, giữ phần trả lời chính hợp lệ.
- QA đóng gói tối đa 2.000 ký tự theo dòng nguyên vẹn; câu tổng quan dùng nhiều nguồn khi có hỗ trợ.
- Diagnosis giữ một ý có căn cứ và ngân sách hiện tại; generation sinh tối đa 192 token, chờ model tối đa 20 giây và không retry; thiếu evidence hoặc hỏi thuốc/liều/PHI thì không gọi model.
- `RAGService.answer` giữ gate nguyên văn cho caller cũ; luồng tổng hợp mới dùng schema riêng.
- Chỉ truy xuất được kiến thức đã ingest; similarity cao không xác nhận một khuyến nghị là đúng.
- Chất lượng truy xuất (Recall@k, nDCG@k) và độ trễ endpoint được đo bằng bộ đề ở `data/rag/evaluation/`; cách chạy ở [`../../README.md`](../../README.md#đánh-giá-truy-xuất-rag).
