# Dịch vụ AI: tra cứu và trợ lý hồ sơ

Dịch vụ tìm các đoạn tài liệu trả lời một câu hỏi, kèm nguồn và giấy phép của nguồn.
API tri thức trả bằng chứng từ tài liệu.
Trợ lý hồ sơ dùng Haystack để tìm nội dung gần nghĩa và có thể tổng hợp bằng LLM nội bộ khi bật riêng.
Dữ liệu hồ sơ chỉ được giữ tạm theo request, không lưu vào database AI.
Mã của `ai/` theo giấy phép AGPL-3.0 và chạy trong tiến trình riêng, nên phần còn lại của DX-OS giữ Apache-2.0.

Lõi chạy bằng Compose hiện có; `deploy/docker-compose.ai.yml` bật AI tùy chọn.

## Trợ lý hồ sơ

Người dùng tìm theo nội dung ngay trong danh sách với các bộ lọc hiện hành.
Chi tiết và kết quả tìm có nút **Tóm tắt diễn biến**, **Tìm trường hợp tương tự**, tự lấy hồ sơ đích mà không yêu cầu nhập ID.
Mục **Trợ lý hồ sơ** là lối tìm kiếm bổ sung theo một loại hồ sơ được phép đọc.
Giao diện đặt trạng thái Backend trước phần tổng hợp AI, thu gọn nguồn và cho mở từng căn cứ từ trích dẫn.

```text
người dùng → Backend kiểm quyền và bộ lọc → bản ghi được phép đọc
  → Haystack BM25 + cosine → RRF → tối đa 4 hồ sơ
  → Backend đọc lịch sử và kiểm lại nguồn
  → Ollama tổng hợp khi bật → Backend kiểm nguồn → giao diện
```

- Backend gọi API nội bộ AI bằng khóa dịch vụ, không chuyển token người dùng hoặc quyền truy cập database nghiệp vụ.
- Tập ứng viên phải đủ theo bộ lọc và quyền, tối đa 200 bản ghi; vượt giới hạn thì cần thu hẹp, không chỉ tìm trang đầu.
- Tìm theo nội dung trường của bản ghi; lịch sử được đọc cho hồ sơ đã chọn, không tìm toàn bộ ghi chú của mọi hồ sơ.
- Với model đang kiểm, cosine dùng tối đa 128 token mỗi chuỗi; BM25 đọc cả chuỗi, chưa chia đoạn hồ sơ dài.
- Lịch sử tối đa 100 sự kiện mỗi hồ sơ trong khoảng đã chọn; ngữ cảnh tổng tối đa 128 KiB và trường văn bản tối đa 4.000 ký tự.
- Phần sinh có ngân sách ngữ cảnh riêng để tránh model cắt mất nguồn; vượt thì yêu cầu thu hẹp.
- Nguồn sửa/xóa, có sự kiện mới hoặc model đổi trong lúc xử lý thì kết quả bị từ chối với `STALE_CONTEXT`.
- Chỉ số, trạng thái và quyền do Backend xác định; phần tổng hợp dẫn nguồn không thay quyết định nghiệp vụ.
- Truy vấn chỉ lọc/đếm không sinh tổng hợp từ top-k; `NOT_NEEDED` giữ tổng số Backend tính thay vì nhờ LLM đếm các nguồn đã chọn.
- Chưa hỗ trợ tìm xuyên thực thể, OCR, đọc tệp đính kèm, hội thoại nhiều lượt hoặc hành động ghi.

### Bật bằng Docker

Tạo `deploy/.env` theo README gốc; `deploy/make_env.sh` sinh cả khóa dịch vụ AI.
Các giá trị mặc định nằm trong `.env.example`.
Chạy từ `deploy/`:

Các lệnh dưới dùng bản clone có thư mục `ai/`.
`dxos start` giữ cấu hình lõi mặc định; AI được bật riêng bằng Compose overlay.

```bash
ASSISTANT_ENABLED=true docker compose -f docker-compose.yml -f docker-compose.ai.yml --profile ai up -d --build
```

Bước chuẩn bị tải embedding khi cache chưa có; dịch vụ suy luận dùng cache ở chế độ offline.
Những lần khởi động sau bỏ `--build`; không xóa volume model nếu muốn dùng lại cache.
AI không publish cổng host và không trở thành dependency bắt buộc của Backend.

Muốn sinh nội dung, chọn model và đặt `ASSISTANT_MODEL` trong `.env`, sau đó chạy:

```bash
ASSISTANT_ENABLED=true ASSISTANT_GENERATION_ENABLED=true docker compose -f docker-compose.yml -f docker-compose.ai.yml --profile ai --profile ai-llm up -d --build
```

Ollama chuẩn bị model khi thiếu; request người dùng không tải model.
LLM dùng volume riêng, một request model tại một thời điểm và tối đa hai lượt gọi cho một yêu cầu.
Khi đã dùng xong phần AI, chỉ dừng các service AI; không dùng `down -v` với project đang chứa dữ liệu cần giữ.

```bash
docker compose -f docker-compose.yml -f docker-compose.ai.yml --profile ai --profile ai-llm stop ai ai-model-prepare ollama ollama-model-prepare
```

Đặt `ASSISTANT_ENABLED=false` và khởi động lại `backend` để tắt chức năng trên giao diện.
Tắt LLM vẫn giữ tra cứu với bộ lọc và bằng chứng; câu hỏi cần diễn giải điều kiện phải được làm rõ hoặc chuyển sang tìm nội dung.

### Model và giới hạn

Không chọn sẵn LLM mặc định cho bản phát hành.
Model phải qua kiểm giấy phép, thông tin huấn luyện, tiếng Việt và tài nguyên theo tiêu chuẩn dự án.
Runtime Ollama và trọng số model có điều kiện sử dụng riêng.
Thử model local không đồng nghĩa model đó đã đủ điều kiện phát hành.

Giới hạn CPU/RAM của AI và Ollama nằm trong env mẫu; model lớn có thể cần tài nguyên cao hơn.
Backend dành tối đa 60 giây cho một tác vụ, gồm thời gian gọi AI và kiểm lại nguồn; AI bận trả không sẵn sàng thay vì xếp hàng không giới hạn.
Giới hạn ngữ cảnh của LLM cấu hình bằng `ASSISTANT_CONTEXT_TOKENS`, mặc định 8192; đây không phải chỉ số chất lượng.

### Kiểm thử và đánh giá

```bash
(cd ai && python -m pytest tests -q)
# Chạy trên stack thử có AI, sau các check lõi trong docs/TESTING.md:
python3 deploy/dev/check_demo.py --assistant
```

Các lệnh chạy từ gốc repository, trừ lệnh đã ghi `cd ai`.
Bộ pilot tổng hợp nằm trong `tests/fixtures/assistant_pilot.json`, gồm 36 câu trên ba mô hình; nhãn đang là `draft`.
Phép đo truy xuất dùng 24 câu tìm/tìm tương tự; 12 câu tóm tắt cần kiểm nội dung riêng.
Đã cài dependency dev, có cache embedding thì chạy từ `ai/`:

```bash
ASSISTANT_EVALUATE=true python -m pytest tests/test_assistant.py -k retrieval_pilot -s
```

Kết quả Recall@4/nDCG@4 mặc định ở thư mục tạm của máy; `ASSISTANT_EVAL_OUTPUT` chọn tệp ngoài repository.
JSON chứa qrels, thứ hạng hybrid/BM25 và kết quả từng câu để tính lại bằng `ir_measures`.
Recall@4 đo tỷ lệ bản ghi liên quan tìm được trong bốn kết quả đầu; nDCG@4 đo mức ưu tiên kết quả hữu ích ở đầu danh sách.
Đây là đánh giá truy xuất trên dữ liệu tổng hợp, không tự chứng minh nội dung LLM đúng hoặc nhu cầu thực tế của tổ chức.

Kết quả ngày 03/11/2026, 24 câu trên ba tập ứng viên, mỗi tập tám bản ghi; nhãn đang là `draft`:

| Phương pháp | Recall@4 | nDCG@4 |
|---|---|---|
| BM25 | 0,9444 | 0,9107 |
| BM25 + cosine/RRF, ngưỡng cosine 0,40 | 1,0000 | 0,9900 |

Pilot Qwen local có 12 ca tổng hợp với schema và ID nguồn hợp lệ; vẫn có nhận định biến tiêu đề yêu cầu thành việc đã hoàn tất.
Phần sinh chưa đạt nghiệm thu nội dung; cần rà nhãn và từng nhận định trên dữ liệu đại diện trước khi công bố chất lượng.

### Thời gian và tài nguyên local

Pilot 12 lượt tổng hợp ngắn trên CPU, Qwen `qwen2.5:1.5b`, hai luồng; AI giới hạn 1 CPU/1536 MiB, Ollama 2 CPU/3 GiB.
Lượt đầu sau khi gỡ model khỏi RAM: 12.86 giây; p50 toàn bộ lượt: 5.62 giây, p95: 10.46 giây.
Đỉnh RAM quan sát bằng `docker stats`: AI 606 MiB, Ollama 1326 MiB.
Hai yêu cầu Backend đồng thời: một lượt sinh, một lượt giữ bằng chứng và báo phần sinh không sẵn sàng; không xếp hàng không giới hạn.
Đây là số đo smoke trên dữ liệu tổng hợp ngắn và máy dùng chung, không phải benchmark tải dài hoặc SLA.

## Cách hoạt động của kho tài liệu

```text
câu hỏi → embedding 384 chiều (multilingual MiniLM)
  → cosine trên pgvector và BM25 trên cùng một ảnh chụp dữ liệu chỉ đọc
  → gộp theo thứ hạng (RRF), giữ đoạn có cosine đạt ngưỡng
  → trả đoạn kèm tài liệu, mã nguồn và điểm
```

- Câu hỏi nêu một mã như `ABC123` mà không tài liệu nào chứa thì không có bằng chứng (`UNKNOWN_SUBJECT`).
- Không đoạn nào đạt ngưỡng thì trả `NO_EVIDENCE` với `LOW_RELEVANCE`.
- Chỉ mục BM25 nằm trong bộ nhớ, giới hạn 20.000 đoạn, cache theo tài liệu hiện có.

## API

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/api/v1/health` | Dịch vụ sống và mô hình embedding (công khai) |
| `POST` | `/api/v1/knowledge/search` | `query`, `namespace` (mặc định `default`), `top_k` 1–20, `document_type` và `code` tùy chọn |
| `POST` | `/api/v1/assistant/plan` | Diễn giải câu hỏi theo metadata Backend cấp, trả điều kiện hoặc yêu cầu làm rõ |
| `POST` | `/api/v1/assistant/rank` | Xếp hạng tập bản ghi đã được Backend kiểm quyền |
| `POST` | `/api/v1/assistant/summarize` | Tổng hợp có cấu trúc từ tập bằng chứng, khi bật LLM |

- Mọi endpoint POST cần header `X-Service-Api-Key` khớp `AI_SERVICE_API_KEY`: thiếu hoặc sai key trả `401`, chưa cấu hình key trả `503`.
- Header `X-Request-Id` được trả lại ở phản hồi và ghi vào log, mỗi request một dòng `request_id=... status=... ms`.
- Cổng của dịch vụ không nên publish ra host, chỉ truy cập trong mạng của stack.

## Dựng kho tri thức

Cần PostgreSQL có `pgvector` và hai người dùng: một chỉ đọc cho dịch vụ, một được tạo bảng cho việc nạp.

```bash
export RAG_INGEST_DATABASE_URL=postgresql+psycopg://<ingest-user>:<password>@<host>:5432/<db>
python -m scripts.init_db
python -m scripts.ingest --documents documents.json --sources sources.json \
  --namespace handbook --document-type guide --version v1 [--dry-run]
```

`documents.json` là một mảng tài liệu, `sources.json` là một mảng nguồn:

```json
[{"code": "dang-ky", "title": "Đăng ký học phần", "content": "…", "source_ids": ["so-tay"]}]
[{"source_id": "so-tay", "title": "Sổ tay", "url": "https://example.org/so-tay", "license": "CC-BY-4.0"}]
```

- Mỗi nguồn phải ghi giấy phép; mỗi tài liệu phải trỏ tới nguồn có trong registry.
- Nạp thay toàn bộ một cặp `namespace` và `document_type` trong một giao dịch, các cặp khác giữ nguyên.
- `--dry-run` kiểm tệp và in bản xem trước, không ghi database.
- Đổi mô hình embedding thì phải nạp lại.

## Cấu hình

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DATABASE_URL` | trống | Kết nối chỉ đọc của kho tài liệu; không cần khi chỉ dùng trợ lý hồ sơ |
| `RAG_INGEST_DATABASE_URL` | trống | Kết nối ghi của `init_db` và `ingest` |
| `EMBEDDING_MODEL` | `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` | Mô hình embedding, Apache-2.0 |
| `SEARCH_CANDIDATE_COUNT` | `20` | Số ứng viên của mỗi bảng xếp hạng, 4–40 |
| `SEARCH_COSINE_MIN_SCORE` | `0.40` | Ngưỡng cosine để một đoạn được coi là bằng chứng |
| `AI_SERVICE_API_KEY` | trống | Khóa dịch vụ |
| `ASSISTANT_GENERATION_ENABLED` | `false` | Bật diễn giải và tổng hợp bằng LLM |
| `ASSISTANT_MODEL` | trống | Model ngôn ngữ do người vận hành chọn |
| `ASSISTANT_MODEL_URL` | `http://ollama:11434` | API Ollama nội bộ |
| `ASSISTANT_CONTEXT_TOKENS` | `8192` | Ngân sách ngữ cảnh model |
| `ASSISTANT_MODEL_THREADS` | `2` | Số luồng CPU cho model ngôn ngữ |
| `ASSISTANT_COSINE_MIN_SCORE` | `0.40` | Ngưỡng gần nghĩa cho hồ sơ, không phải xác suất đúng |
| `LOG_LEVEL` | `INFO` | Mức log |

Mô hình embedding được chuẩn bị một lần và nằm trong volume `/app/weights`.

## Chạy và kiểm thử

```bash
docker build -t dxos-ai ai/
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r ai/requirements-dev.txt
cd ai && python -m pytest tests -q
```

`requirements.lock` ghim phiên bản của mọi gói trong image; làm mới bằng `docker run --rm --entrypoint pip <image> freeze > requirements.lock`.
