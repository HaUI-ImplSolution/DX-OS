# Bộ đề đánh giá truy xuất RAG

Bộ đề gồm 27 câu hỏi đáp Farm, 39 câu hỏi đáp Lab và 6 query tham khảo chẩn đoán legacy.
Giữ 42 lượt pilot ban đầu, bổ sung 30 câu tổng quan/diễn đạt gián tiếp với topic/family và calibration/holdout.
Các nhãn bổ sung vẫn là draft, chưa được duyệt độc lập; trạng thái này tách khỏi việc chủ dự án đã duyệt nguồn tri thức.
Chủ dự án đã duyệt bộ câu hỏi và nhãn pilot ngày 26/10/2026; xác nhận ở `owner_approval` trong metadata.

| File | Nội dung |
|---|---|
| `questions.jsonl` | Câu hỏi, nhóm, namespace và mã tài liệu |
| `qrels.draft.tsv` | Nhãn nháp chuẩn TREC: mã câu, iteration, khóa chunk, độ liên quan |
| `metadata.json` | Phiên bản bộ đề, corpus, trạng thái nhãn và việc kiểm tra câu hỏi |

Nhãn 0 là không hỗ trợ, 1 hỗ trợ một phần và 2 trả lời trực tiếp.
Khóa chunk là namespace/mã tài liệu/chunk_index; dùng đúng chunker và quy tắc bỏ trùng của ingest.
Bộ pilot hiện chỉ liệt kê nhãn dương, phần không liệt kê được thư viện coi là không liên quan; phê duyệt pilot được ghi riêng với trạng thái rà đủ nhãn `reviewed`.
Người kiểm tra cần rà toàn bộ chunk trong phạm vi từng câu trước khi xem ranking, bổ sung nhãn tường minh 0/1/2 và ghi reviewer, reviewed_at, review_scope trong metadata trước khi chuyển sang reviewed.
Khi so sánh BM25/cosine/RRF/rerank, mọi strategy dùng cùng corpus/chunk và bộ nhãn; điểm truy xuất không đánh giá độ đúng văn bản Qwen.
Công cụ từ chối trạng thái reviewed thiếu xác nhận hoặc còn chunk chưa được gắn nhãn.
Việc rà nhãn tường minh toàn bộ chunk và tái lập bởi bên thứ ba được theo dõi ở [`docs/BACKLOG.md`](../../../docs/BACKLOG.md).

Qrels/run và snapshot của từng lần chạy được xuất ngoài repository, không đặt trong report hoặc thêm vào Git.
Giữ riêng bộ đề hiện tại và artifact lần chạy; đổi nhãn/phiên bản thì collect lại sau khi đóng băng để tránh gắn nhãn đã duyệt cho kết quả draft cũ.
