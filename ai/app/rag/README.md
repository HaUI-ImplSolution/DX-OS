# RAG — Tra cứu kiến thức bệnh cây

## Vai trò của thư mục này

`app/rag/` là phần Retrieval-Augmented Generation của AI Service. Nó biến kiến thức bệnh cây đã được duyệt thành các đoạn có thể tra cứu, tìm các đoạn liên quan theo câu hỏi và cung cấp evidence cho workflow hoặc LLM.

RAG chỉ là lớp kiến thức giải thích. Nó không là nguồn quyết định về thuốc, liều lượng, PHI hay thao tác áp dụng; các thông tin đó luôn lấy từ dữ liệu nghiệp vụ có cấu trúc trong PostgreSQL.

## Phạm vi

RAG chịu trách nhiệm:

- Đọc corpus kiến thức bệnh từ `data/rag/processed/disease_knowledge.json`.
- Kiểm tra cấu trúc dữ liệu, `disease_code`, nội dung và nguồn trích dẫn trước khi ingest.
- Cắt nội dung thành các chunk nhỏ, tạo embedding và lưu vào PostgreSQL.
- Tra cứu các chunk gần với câu hỏi bằng cosine similarity.
- Lọc kết quả theo `disease_code` khi đã có kết quả nhận diện từ CV.
- Trả context kèm nguồn trích dẫn có thể kiểm tra.
- Tạo câu trả lời có căn cứ khi Ollama khả dụng; nếu không, vẫn trả evidence đã truy xuất.

RAG không chịu trách nhiệm:

- Không tự crawl web hoặc tự thêm nguồn tài liệu mới.
- Không seed bảng bệnh, thuốc, treatment rule hoặc policy.
- Không sinh hay xác nhận liều lượng, hoạt chất, PHI và quyết định điều trị.
- Không thay thế truy vấn SQL có cấu trúc hoặc phê duyệt của kỹ sư.

## Cách hoạt động

### Ingest kiến thức

```text
disease_knowledge.json + source_registry.json
  → kiểm tra trường bắt buộc và source_id hợp lệ
  → cắt nội dung thành chunk tối đa 350 ký tự
  → tạo embedding 384 chiều bằng multilingual MiniLM
  → khóa advisory transaction để tránh hai lần ingest đồng thời
  → thay thế atomically corpus disease_knowledge cũ
  → lưu documents, document_chunks và embedding vào PostgreSQL
```

Ingest chạy qua:

```bash
python -m scripts.ingest_diseases
```

Dùng `--dry-run` để kiểm tra dữ liệu và preview chunk mà không ghi database.

### Tra cứu

```text
Câu hỏi + disease_code tùy chọn
  → tạo embedding cho câu hỏi
  → PostgreSQL so sánh cosine với embedding của các chunk
  → lọc theo document_type và disease_code nếu có
  → trả top-k đoạn gần nhất, score và provenance
  → workflow/RAG service dùng evidence để giải thích
```

Khi CV đã trả `disease_code`, workflow truyền mã đó vào RAG. Việc giới hạn corpus theo mã bệnh giúp context phù hợp với bệnh nhận diện và giảm nguy cơ lấy tài liệu của bệnh khác.

## Thành phần

| Thành phần | Vai trò |
|---|---|
| `embedding_service.py` | Nạp và cache mô hình embedding multilingual MiniLM; tạo vector đã normalize. |
| `ingestion_service.py` | Validate corpus, chunk nội dung, tạo embedding và thay thế corpus trong transaction. |
| `chunker.py` | Chia văn bản theo dòng, tối đa 350 ký tự/chunk, overlap một dòng. |
| `retriever.py` | Tạo embedding câu hỏi, cosine search và trả các chunk liên quan. |
| `rag_service.py` | Ghép evidence, gọi Ollama khi khả dụng và tạo citations. |
| `prompt_builder.py` | Tạo prompt có guardrail: chỉ trả lời từ context, không tự bịa thuốc/liều lượng/PHI. |
| `source_registry.py` | Nạp và kiểm tra registry nguồn, ánh xạ `source_id` thành metadata trích dẫn. |
| `scripts/ingest_diseases.py` | Entry point CLI cho preview hoặc ingest corpus. |

## Dữ liệu và lưu trữ

Corpus nguồn không tự xuất hiện trong database. Sau khi PostgreSQL có schema hiện tại, cần chạy seed dữ liệu nghiệp vụ và ingest RAG để các endpoint/workflow có knowledge truy vấn.

Mỗi document lưu metadata như tiêu đề, `disease_code`, loại document, version và `source_id`. Mỗi chunk lưu nội dung, vị trí chunk và embedding `vector(384)`.

Implementation hiện tại dùng PostgreSQL kèm `pgvector` để thực hiện cosine search. Vì embedding model tạo 384 chiều, thay model embedding sẽ cần tạo lại toàn bộ embedding của corpus.

## Evidence và guardrail

Kết quả truy xuất luôn chứa `source_ids`, document/chunk ID, similarity và nội dung evidence. `RAGService` chỉ chấp nhận câu trả lời từ LLM khi câu đó xuất hiện nguyên văn trong evidence đã lấy; nếu Ollama lỗi, timeout hoặc trả nội dung không được chứng minh, service trả lại excerpt evidence thay vì bịa câu trả lời.

Do đó RAG chỉ dùng để giải thích kiến thức. Thuốc, liều lượng, PHI và policy an toàn phải tiếp tục lấy từ structured knowledge/service tương ứng.

## Giới hạn

- Chỉ truy xuất được kiến thức đã ingest thành công vào PostgreSQL.
- Chất lượng retrieval phụ thuộc vào corpus, metadata nguồn và embedding model.
- Similarity cao không tự xác nhận một khuyến nghị là đúng.
- `top_k` phải từ 1 đến 20; giá trị cao hơn không đồng nghĩa context tốt hơn.
- RAG không thay thế dữ liệu nghiệp vụ có cấu trúc hay phê duyệt của kỹ sư.
