# Bàn giao triển khai ngày 28/10/2026

Nhánh: `feat/qwen-grounded-answers`. Chủ dự án đã cho phép commit/push bản bàn giao trên nhánh này. Chưa đóng phase. Checklist là trạng thái có bằng chứng, không phải mọi việc đã hoàn tất.

## Đã triển khai

- Search chatbot riêng: BM25 + cosine → RRF → MiniLM đa ngôn ngữ rerank; giới hạn CPU, hàng đợi, timeout, cache và trạng thái lỗi. Model được chuẩn bị khi ingest, nạp từ cache trước khi phục vụ; không tải trong request.
- Core chỉ đổi adapter `KnowledgeGatewayService` và test. Giữ hai lượt search → gộp evidence Backend → Qwen; `retrieve` cho diagnosis/ticket vẫn dùng đường cũ.
- Scope/policy theo namespace trong `data/rag/chat_profiles.json`; câu thiếu từ khóa được thử truy xuất. Chatbot tổng quan dùng nhiều nguồn, giữ guard thuốc/liều/PHI và thẩm quyền.
- Ghi owner duyệt 7 tài liệu Farm, mở ingest generic bằng lệnh Farm hiện có; khôi phục allowlist nguồn chính thức Lab. Không đổi nội dung corpus bệnh/chunker/embedding.
- Runner một lệnh giữ các mode cũ, so bốn strategy, thử tải hai strategy, replay Langfuse và tự cleanup. Thêm dependency BM25, cập nhật lock/LICENSES/SBOM.
- Bỏ script lexical proxy và hai báo cáo sinh sẵn không còn caller; kết quả đo nằm ngoài repository.

## Đã kiểm

| Hạng mục | Bằng chứng |
|---|---|
| AI | 162 test, 50 subtest; diagnose contract và RAG access boundary |
| Backend | 24 test gateway/Knowledge/codec; package đủ 6 module |
| Web/mobile | Typecheck đạt, frontend build đạt; không sửa mobile |
| Data | Validator 7 Farm/12 Lab, source parity; ingest Lab offline 12 tài liệu/78 chunk; rollback và role chỉ đọc trên DB riêng |
| Quality | 4 × 72 query, cùng 25 tài liệu/244 chunk; gate 90/90 ca pilot, gồm 15 ca thiếu evidence |
| Đối chiếu điểm | CLI ir_measures khớp 600 giá trị trong sai số 1e-8 |
| Performance | 36 lượt/8.974 request, không lỗi HTTP/hợp đồng; 2 CPU/1536 MiB, không gồm Qwen/Backend |
| Langfuse | Publish 12 experiment quality, scope và cả 36 lượt tải; đăng nhập/xem experiment và HTML bằng trình duyệt; Enter tự down |
| Trước push | Gitleaks v8.24.3 không phát hiện secret ở các file thay đổi; diff không thêm hash cố định; đã chứa origin/Khuong/main hiện tại |
| Lab thật | API và web có Qwen trả lời câu tổng quan; câu Wi-Fi sinh viên được trả lời, câu trồng lúa không có evidence; không lỗi JavaScript |

Chi tiết điểm ở TESTING. Nhãn mới vẫn `draft`; không gọi đây là đánh giá độc lập hoặc accuracy chẩn đoán. Chủ dự án đã chấp nhận không bắt buộc điểm mới vượt baseline.

## Việc cần tiếp tục, theo thứ tự

1. **Kiểm lại đối tượng trong câu tổng quan Lab.** Trước sửa cuối, hỏi “Nhà trường có những dịch vụ hỗ trợ nào cho sinh viên?” có lúc Qwen liệt kê ngắt USB của Faronics như dịch vụ sinh viên. Đã bổ sung policy loại nguồn quản trị giảng viên khỏi câu tổng quan dành cho sinh viên và unit regression; lượt quality/scope chạy lại đạt. Chưa kiểm lại generation/web sau thay đổi cuối này. Giữ nguồn Faronics cho câu kỹ thuật/giảng viên; không đổi ticket.
2. **Smoke Farm trên sản phẩm thật sau build.** Chăm sóc lúa/phát thải thấp, bệnh cụ thể, thiếu tài liệu, ngoài miền, hỏi thuốc/liều/PHI; kiểm summary/steps/note và nguồn bằng mắt. Sau đó kiểm lại diagnosis cùng ảnh/facts và gợi ý ticket Lab; hiện có unit/contract và 6 query diagnosis cùng ranking, chưa thay thế smoke nghiệp vụ.
3. **Kiểm startup bàn giao.** Chạy theo README lần lượt Farm/Lab bằng cache, không full rebuild/prune. Local đã package và chạy adapter mới, ingest Lab offline; chưa coi đây là bằng chứng clone sạch đầy đủ trên máy khác. `deploy/`, CI và Core API không đổi.
4. **Rà nội dung/nhãn.** Sáu bài Farm có nội dung thực; một bài khí hậu chỉ có mục lục 858 ký tự. Cần lấy nội dung module trước khi coi là hướng dẫn chi tiết; không tự suy diễn từ tên mục. Ngưỡng rerank hiện là ngưỡng pilot chung, chưa calibration độc lập theo từng app; workload tải hiện chủ yếu là câu có nhãn dương, chưa stress riêng nhóm từ chối. Rà nhãn 0/1/2 và coverage trước khi đổi dataset sang `reviewed`; approval nguồn không tự duyệt nhãn mới.
5. **Chốt review.** Hoàn thiện các ô còn trống của test plan/checklist, kiểm diff/domain và quét secret trước khi commit. Không đưa report/, state, tài khoản, artifact/checksum cục bộ hoặc thay đổi .gitignore vào commit. Chưa xóa kế hoạch.

## Tiếp tục đánh giá

Từ thư mục gốc: `./ai/evaluate.sh quality`, `performance` hoặc `all`; `view` mở lịch sử, `down` phục hồi sau ngắt. Runner độc lập với sản phẩm, tự dừng dịch vụ khi thoát, giữ cache/volume/artifact. Không gọi runner từ luồng startup chính.

Máy hiện tại lưu bằng chứng ở `~/.local/share/dx-os-chat-rag/`; state runner ở thư mục con `evaluation-runner`, không nằm trong Git. Muốn xem đúng lịch sử local: `EVAL_STATE_DIR="$HOME/.local/share/dx-os-chat-rag/evaluation-runner" EVAL_PROJECT=dx-rag-evaluation-runner-chat ./ai/evaluate.sh view`. Không sao chép credential trong state vào PR.

AI sản phẩm đã được dừng để không tranh tài nguyên với lượt đánh giá cuối. Không tự mở thêm toàn bộ stack. Lỗi CI thời điểm `LabBookingIntegrationTest` có sẵn ngoài domain được giữ trong BACKLOG, chưa sửa.
