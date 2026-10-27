# RAG: tra cứu kiến thức

`app/rag/` biến kiến thức đã duyệt thành các đoạn tra cứu được và trả evidence kèm nguồn cho workflow hoặc LLM.
RAG chỉ giải thích: thuốc, liều, PHI, policy an toàn luôn lấy từ Backend qua REST.
RAG không tự crawl nguồn mới, không seed bệnh, thuốc, rule và không thay phê duyệt của kỹ sư.

Mỗi corpus là một namespace: `farm` (corpus bệnh lúa cũ và corpus `farm_knowledge` đa chủ đề), và `lab` (bài trích từ văn bản của HaUI). Farm knowledge đa chủ đề được validate bằng `python data/scripts/validate_phase2_rag.py` trước ingest.

Hỏi đáp đi qua scope gate trước retrieval. Câu ngoài namespace trả `OUT_OF_SCOPE` không có citation; câu đúng phạm vi nhưng không có đoạn đạt ngưỡng trả `IN_SCOPE_NO_EVIDENCE`. `disease_code` và luồng diagnosis giữ contract cũ.
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
  → top-k (1–20) chunk, score, provenance, vị trí chunk trong tài liệu (`chunk_index`)
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
- Mỗi lượt nhận tối đa 2.000 ký tự evidence, sinh tối đa 192 token, chờ model tối đa 20 giây và không retry; thiếu evidence hoặc hỏi thuốc/liều/PHI thì không gọi model.
- `RAGService.answer` giữ gate nguyên văn cho caller cũ; luồng tổng hợp mới dùng schema riêng.
- Chỉ truy xuất được kiến thức đã ingest; similarity cao không xác nhận một khuyến nghị là đúng.
- Chất lượng truy xuất (Recall@k, nDCG@k) và độ trễ endpoint được đo bằng bộ đề ở `data/rag/evaluation/`; cách chạy ở [`../../README.md`](../../README.md#đánh-giá-truy-xuất-rag).
