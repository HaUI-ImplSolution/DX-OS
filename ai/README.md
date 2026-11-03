# Dịch vụ AI: tìm kiếm tri thức có nguồn

Dịch vụ tìm các đoạn tài liệu trả lời một câu hỏi, kèm nguồn và giấy phép của nguồn.
Dịch vụ chỉ tìm và xếp hạng bằng chứng: không sinh câu trả lời, không gọi mô hình ngôn ngữ và không giữ dữ liệu nghiệp vụ.
Mã của `ai/` theo giấy phép AGPL-3.0 và chạy trong tiến trình riêng, nên phần còn lại của DX-OS giữ Apache-2.0.

Dịch vụ chưa nằm trong `deploy/docker-compose.yml`; mô hình không khai báo AI thì không chạy dịch vụ này.

## Cách hoạt động

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

- Endpoint tìm kiếm cần header `X-Service-Api-Key` khớp `AI_SERVICE_API_KEY`: thiếu hoặc sai key trả `401`, chưa cấu hình key trả `503`.
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
| `DATABASE_URL` | trống | Kết nối chỉ đọc của dịch vụ |
| `RAG_INGEST_DATABASE_URL` | trống | Kết nối ghi của `init_db` và `ingest` |
| `EMBEDDING_MODEL` | `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2` | Mô hình embedding, Apache-2.0 |
| `SEARCH_CANDIDATE_COUNT` | `20` | Số ứng viên của mỗi bảng xếp hạng, 4–40 |
| `SEARCH_COSINE_MIN_SCORE` | `0.40` | Ngưỡng cosine để một đoạn được coi là bằng chứng |
| `AI_SERVICE_API_KEY` | trống | Khóa dịch vụ |
| `LOG_LEVEL` | `INFO` | Mức log |

Mô hình embedding tải về từ Hugging Face lần đầu và nằm trong `/app/weights` của image.

## Chạy và kiểm thử

```bash
docker build -t dxos-ai ai/
pip install --extra-index-url https://download.pytorch.org/whl/cpu -r ai/requirements-dev.txt
cd ai && python -m pytest tests -q
```

`requirements.lock` ghim phiên bản của mọi gói trong image; làm mới bằng `docker run --rm --entrypoint pip <image> freeze > requirements.lock`.
