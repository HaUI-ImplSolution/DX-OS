# Bàn giao ngày 28/10/2026

Nhánh: `feat/qwen-grounded-answers`. Đã hoàn thiện phần triển khai và kiểm thử kỹ thuật; chờ review cuối của chủ dự án.
Giữ thư mục kế hoạch theo yêu cầu, không xóa khi push.

## Đã triển khai

- Chatbot web dùng BM25 + cosine → RRF → multilingual MiniLM rerank; Qwen tổng hợp từ evidence Backend đã gộp. Giữ hai lượt search/answer qua AiGateway.
- Scope/profile theo namespace, hỗ trợ câu tổng quan và nhiều nguồn; giữ chốt thuốc/liều/PHI, xác nhận bệnh và thẩm quyền. Lab tổng quan chọn dịch vụ đúng đối tượng, câu thủ tục vẫn có context chi tiết.
- Giữ Knowledge adapter và DTO/helper dùng chung trong core-api của đồng đội. Không đưa thuật toán AI vào Java Core; diagnosis/ticket dùng retriever legacy.
- Khôi phục owner approval 7 tài liệu Farm, giữ nguồn chính thức Lab và ingest bằng lệnh hiện có. Corpus rỗng vẫn thu hồi đúng loại tài liệu; transaction lỗi rollback.
- Model dùng cache, không tải trong request. Thiếu reranker không chặn startup CV; chatbot báo chưa sẵn sàng. Queue CPU tối đa 12 giây, rerank 6 giây; QA chờ model 30 giây, diagnosis giữ ngân sách riêng.
- Runner một lệnh giữ quality/performance/all/view/down, DB/state riêng, artifact và lịch sử Langfuse ngoài repository, tự cleanup. Không sửa deploy/CI hoặc luồng startup chính.

## Bằng chứng mới nhất

| Hạng mục | Kết quả |
|---|---|
| AI | 168 test + 50 subtest; diagnose contract, access boundary và các self-check đạt |
| Backend | 24 test gateway/Knowledge/codec; package sáu module đạt |
| Data | 7 Farm/12 Lab, hai test parity/approval; offline ingest, corpus rỗng và rollback trên DB riêng đạt |
| Quality | 4 × 72 query, 25 tài liệu/244 chunk; scope 90/90; CLI khớp 600 giá trị ≤1e-8 |
| Performance | 12 lượt/888 request, 10 giây/mức, một lần lặp; không lỗi HTTP/hợp đồng/OOM, AI 2 CPU/1536 MiB |
| Langfuse/isolation | Publish quality/scope/performance, đã xem experiment/HTML; tự down, ID/start-time container và số dòng DB sản phẩm giữ nguyên |
| Farm/Lab thật | E2E 10/10 và 16/16; Qwen tổng quan/triệu chứng, từ chối đúng lý do; web Lab không lỗi JavaScript |
| Chẩn đoán ảnh | Báo cáo mới, AI, ẩn trước duyệt, lịch sử, duyệt và đọc lại đạt; giữ explanation và treatment rule |

Điểm chi tiết và p95/RPS mới nhất ở [`TESTING.md`](../TESTING.md).
Lần tải trên là smoke; cấu hình mặc định vẫn 36 lượt, 30 giây/mức và ba lần lặp.
Không coi generated là độ đúng nội dung hoặc ir_measures là accuracy chẩn đoán.

## Việc còn lại

- Chủ dự án review diff và kiểm lại UI trên bản cuối; chưa tự tick thay người dùng.
- Rà nhãn 0/1/2 độc lập, chia calibration/holdout và đo riêng ngưỡng từng app. Nhãn mở rộng vẫn draft; owner duyệt nguồn không đồng nghĩa duyệt nhãn.
- Bổ sung nội dung module của tài liệu Farm còn dạng mục lục, rà điều khoản sử dụng nguồn; không suy diễn hướng dẫn từ tên mục.
- Theo dõi JSON Qwen chưa hoàn chỉnh và diễn đạt điều kiện Lab trong BACKLOG; giữ draft lỗi bị ẩn và nguồn tham khảo còn nguyên.
- Đo tải riêng nhóm từ chối, chạy lại toàn bộ all với cấu hình cuối và xác minh full clone sạch trên máy khác. Local dùng cache; CI E2E checkout sạch đạt, không thay thế full stack từ clone sạch.

Các giới hạn trên nằm trong [`BACKLOG.md`](../BACKLOG.md), không mở rộng code ngoài domain để xử lý trong lần này.

## Chạy lại

Từ repository: `./ai/evaluate.sh quality`, `performance` hoặc `all`; `view` xem lịch sử, `down` dọn phiên bị ngắt.
Runner không cần bật sản phẩm, tự dừng môi trường đánh giá khi thoát, giữ cache/volume/artifact.

Bằng chứng máy hiện tại ở `~/.local/share/dx-os-chat-rag/`, state ở thư mục con `evaluation-runner`.
Xem đúng lịch sử này:

```bash
EVAL_STATE_DIR="$HOME/.local/share/dx-os-chat-rag/evaluation-runner" EVAL_PROJECT=dx-rag-evaluation-runner-chat ./ai/evaluate.sh view
```

Credential ở state ngoài Git, không sao chép vào PR.
Môi trường đánh giá đã dừng; sản phẩm đang chọn Farm, Backend/AI/storage UP.
