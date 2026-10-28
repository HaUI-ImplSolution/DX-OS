# Runbook: hỏi đáp hybrid và đánh giá độc lập

Hướng dẫn dùng runner đã triển khai; kết quả và giới hạn xác minh được ghi trong checklist và TESTING.

## 1. Môi trường và dữ liệu

Người dùng cần Linux x86_64, Bash và Docker Engine/Compose theo runner hiện có.
Host không cần cài thêm Python, k6, ir_measures hoặc cấu hình Langfuse thủ công.

Corpus được phép dùng, registry, câu hỏi và nhãn đi kèm repository; không yêu cầu người dùng tải dataset ngoài bằng tay.
Bảy tài liệu Farm do đồng đội đưa lên nhánh đã được chủ dự án duyệt ngày 28/10/2026.
Artifact IN_REVIEW bổ sung sau này nằm staging và không được coi là dữ liệu phục vụ.
Model embedding/reranker và image còn thiếu được chuẩn bị lần đầu; các lần sau dùng cache riêng, có đủ cache thì chạy offline.
Không hứa chạy offline lần đầu khi máy chưa có image/model.

State mặc định giữ ở `${XDG_DATA_HOME:-$HOME/.local/share}/dx-os-rag-evaluation-runner`, ngoài repository.
Nó chứa config/tài khoản local, model cache, volume DB evaluation/Langfuse và artifact theo run.
Không lấy credential hoặc kết nối tới database sản phẩm.

## 2. Một lệnh cho từng nhu cầu

```bash
./ai/evaluate.sh quality
```

Chuẩn bị DB riêng và model cache, ingest corpus đã duyệt, chạy các strategy, chấm Recall/nDCG và xuất Langfuse local.
Terminal in điểm theo app/nhóm câu/strategy, link Langfuse, link báo cáo, tài khoản local và thư mục artifact.
Diagnosis chỉ chạy lại query legacy @3 để kiểm hồi quy, không tuning.

```bash
./ai/evaluate.sh performance
```

Chuẩn bị cùng môi trường rồi đo HTTP retrieval legacy và hybrid+rerank tuần tự.
Mỗi strategy có 18 lượt Farm/Lab ở 1/2/5 VU, lặp ba lần sau warm-up, mặc định 30 giây/lượt.
Có p50/p95, request/giây, lỗi, CPU/RAM, cold/warm và thời gian từng tầng; không gồm Qwen, Backend hoặc điện thoại.
Langfuse chỉ bật sau đo, không tranh tài nguyên với workload đang benchmark.

```bash
./ai/evaluate.sh all
```

Chạy quality và performance trong cùng phiên, chia sẻ bước chuẩn bị.
Đây là đợt đo đầy đủ sau build, không phải bước tự động khi khởi động sản phẩm.

```bash
./ai/evaluate.sh view
```

Mở lịch sử Langfuse/báo cáo đã lưu, không đo hay ingest lại chỉ để xem kết quả.

```bash
./ai/evaluate.sh down
```

Khôi phục khi phiên bị ngắt bất thường; chỉ dọn tài nguyên evaluation đúng owner/project.
Chế độ đo/xem tự dừng dịch vụ khi Enter, hết timeout hoặc Ctrl+C; lỗi cũng phải cleanup.
Cache, volume, tài khoản và lịch sử vẫn giữ để lần sau không tải và tạo lại.
SIGKILL/tắt máy không đảm bảo trap chạy; dùng down để hồi phục phiên đó.

## 3. Xem và kiểm chứng kết quả

Trong Langfuse, chọn dataset/snapshot rồi so experiment legacy, BM25, hybrid và hybrid+rerank.
Expected Output là nhãn; Output là chunk/ranking thực tế; scores gồm Recall/nDCG và metadata về strategy/config/latency.
Có ranking từng tầng để biết mất evidence từ candidate, rerank hay gate.
Replay không phải tracing live; duration publish không dùng làm độ trễ retrieval.

| Artifact | Nội dung |
|---|---|
| Manifest/provenance | Corpus, nhãn, model/tokenizer, config, code version và boundary |
| Collect/rankings | Query, trạng thái, ranking raw/trung gian/cuối và component scores |
| Qrels + TREC run | Đầu vào để bên khác tự tính lại điểm |
| Score/comparison | Điểm từng câu/nhóm/app/strategy, chênh lệch và lỗi |
| K6/HTML/resources | Thử tải và mẫu CPU/RAM thực |
| Status/log | Exit code, lỗi, ingest và cleanup |

Không gộp no-evidence không có nhãn dương vào mean Recall/nDCG answerable.
Nếu nhãn chưa rà đủ, báo cáo vẫn ghi draft; chủ dự án duyệt pilot không tự trở thành đánh giá độc lập.
Chỉ ghi câu trả lời Qwen khi có chạy generation thật; điểm truy xuất không chứng nhận văn bản đúng.

## 4. Phục vụ sản phẩm và cập nhật corpus

Cách chạy Farm/Lab vẫn theo README và `deploy/start.sh` hiện có.
Không gọi evaluate.sh từ startup sản phẩm.
Runtime chỉ đọc; crawl/review/ingest là thao tác quản trị tách riêng.

Luồng cập nhật data: crawl staging → kiểm quyền dùng/nội dung → người duyệt xác nhận → dry-run → ingest artifact approved → smoke → đánh giá → giữ bản rollback.
Lệnh ingest --corpus farm/lab đang được deploy gọi phải tự chọn đúng corpus/type đã duyệt; không buộc người clone về chạy thêm lệnh để có kiến thức tổng quan.
Corpus bệnh và luồng diagnosis không thay đổi.

Evaluation có thể chạy khi sản phẩm đang tắt hoặc đang bật.
Hai môi trường không dùng chung DB/volume/network nhưng vẫn dùng chung CPU/RAM/ổ đĩa máy host.
Thiếu tài nguyên thì runner báo và dừng trước, không hạ sản phẩm, tăng swap tùy tiện hoặc xóa cache toàn máy.
