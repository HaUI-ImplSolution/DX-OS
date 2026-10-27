# Runbook Phase 2 RAG (offline-first)

## Kiểm tra không cần Docker/PostgreSQL

```powershell
python data/scripts/validate_phase2_rag.py
$env:PYTHONPATH = "ai"
python -m unittest ai.tests.test_scope_gate -v
```

Validator kiểm tra corpus Farm đa chủ đề, registry, trạng thái duyệt, ngôn ngữ,
source reference và host Lab. Crawler chỉ ghi `data/rag/staging/`; không được
ghi vector DB.

`python -m scripts.evaluate_scope` tính metric scope gate; `python -m
scripts.evaluate_retrieval_offline` tạo lexical proxy report. Proxy không thay
thế được report pgvector, nhưng kiểm tra được qrels, chunk identity và công
thức Recall/nDCG trước khi có DB.

## Luồng promotion

1. Chạy crawler ở chế độ staging/dry-run.
2. Kiểm tra raw snapshot, checksum, locator, license/terms và review queue.
3. Người duyệt nông nghiệp xác nhận nội dung; người có trách nhiệm Lab xác nhận
   nguồn thuộc `sict.haui.edu.vn`.
4. Chỉ sau đó chạy `promote_farm_corpus.py --approve-all` và ingest bằng
   credential `RAG_INGEST_DATABASE_URL`.
5. Giữ corpus version cũ để rollback bằng ingest lại artifact trước đó.

Không dùng bài crawl để tạo treatment rule, thuốc, liều hoặc PHI. Các dữ liệu
này vẫn phải đến từ Backend facts đã duyệt.

## Trạng thái hiện tại

Farm staging/corpus đa chủ đề đã có artifact và pass offline validation. Corpus
Lab hiện hữu đang cần quarantine vì các URL hiện tại không thuộc host SICT
được policy Phase 2 cho phép; không tự động promote hoặc xóa các artifact đó.
