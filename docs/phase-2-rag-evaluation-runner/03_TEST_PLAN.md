# Kế hoạch kiểm thử: Phase 2

## 1. Chiến lược

Lần hiện tại chỉ lập kế hoạch, kiểm tra cấu trúc/liên kết tài liệu; chưa chạy `.sh`, build image hoặc bật môi trường mới.
Các case dưới đây chỉ thực hiện khi chủ dự án yêu cầu triển khai/kiểm thử; không tự tick theo kết quả công cụ của đợt trước.
Khi thực hiện, dùng Bash/Docker Engine local trên Linux x86_64; host không cần Python/k6/HF CLI.
Phân biệt clone/state/cache đánh giá mới với cache layer Docker dùng chung; không prune toàn máy để tạo cold case.
Kiểm tĩnh bằng `bash -n`, Compose config không in giá trị bí mật, self-check và diff; không thêm job vào CI sản phẩm.
Integration dùng retriever/model/pgvector thật, k6/SDK thật và trình duyệt; số liệu thấp hoặc lỗi không được loại để làm xanh kết quả.

## 2. Test case bắt buộc

| ID | WP | Loại | Bước kiểm tra | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T2-01 | WP2-01 | Tĩnh | `bash -n`, help, mode sai, đọc diff/allowlist | Không lỗi cú pháp; help/mode sai chưa tạo container, env hoặc image | Chưa chạy | Chưa chạy |
| T2-02 | WP2-02 | Clone/state mới | Chỉ có checkout, Bash/Docker; không có state/model cache/tag đánh giá; chạy quality | Tự bootstrap, không cần file private hay tag deploy-ai/deploy-minio; đủ 42 lượt | Chưa chạy | Chưa chạy |
| T2-03 | WP2-02 | Cache | Chạy lại với state/cache có sẵn, nguồn ngoài mạng không sẵn nhưng đủ image/model | Không tải lại model hoặc build image không đổi; giữ credential, tạo run mới | Chưa chạy | Chưa chạy |
| T2-04 | WP2-01 | Preflight | Thiếu Docker/quyền socket, Compose không hỗ trợ, thiếu RAM/disk, cổng bị chiếm | Báo rõ trước phần phụ thuộc; không dừng stack khác hoặc đổi cấu hình host | Chưa chạy | Chưa chạy |
| T2-05 | WP2-01 | Đường dẫn/domain | Gọi từ thư mục khác/checkout có khoảng trắng; state trong repo/symlink hoặc project sản phẩm | Đường dẫn hợp lệ chạy được; state/project sai domain bị từ chối trước up/down | Chưa chạy | Chưa chạy |
| T2-06 | WP2-02 | Dữ liệu | Kiểm nguồn corpus, snapshot, model/runtime ingest-query và thử ghi bằng role truy xuất | Dữ liệu khớp, ghi bị từ chối; mismatch không được giả thành baseline hợp lệ | Chưa chạy | Chưa chạy |
| T2-07 | WP2-03 | Chất lượng | quality gọi CLI hiện có, chấm lại qrels/run và mở Langfuse | Đủ 12/24/6 bản ghi, ba nhóm score đúng; trạng thái nhãn/phê duyệt giữ nguyên | Chưa chạy | Chưa chạy |
| T2-08 | WP2-03 | Hiệu năng | performance: 1/2/5 VU, 30 giây, ba lượt, hai namespace | Đủ 18 trial; JSON/HTML/mẫu đúng AI-vector; đối chiếu native k6 đúng | Chưa chạy | Chưa chạy |
| T2-09 | WP2-03 | Chạy cả hai | all chạy tuần tự | Một lần chuẩn bị, đủ đầu ra hai phép đo; Langfuse không tranh tài nguyên lúc đo tải | Chưa chạy | Chưa chạy |
| T2-10 | WP2-03 | Xem | Mở UI/HTML, đọc cấu hình, score và thời gian đo | AI/vector đã dừng; link hoạt động, có account local, metadata thật; replay phân biệt rõ | Chưa chạy | Chưa chạy |
| T2-11 | WP2-03 | Enter | Nhấn Enter sau khi xem | Đợi cleanup xong, không còn container/job con của phiên; dữ liệu và khóa được xử lý đúng | Chưa chạy | Chưa chạy |
| T2-12 | WP2-03 | Timeout | Đặt timeout ngắn trong cấu hình thử, không thao tác | Tự dọn đúng hạn xem; không cắt cửa sổ đo; không yêu cầu chạy down | Chưa chạy | Chưa chạy |
| T2-13 | WP2-03 | Hủy | Ctrl+C, TERM, HUP trong prepare, đo và xem | Dọn idempotent, không để job đo tiếp ở nền; giữ artifact đã tạo và exit code phù hợp | Chưa chạy | Chưa chạy |
| T2-14 | WP2-03 | Không TTY | Chạy mode đo với stdin đóng; gọi view không TTY | Đo/publish rồi down ngay; view từ chối trước startup; không chờ 600 giây | Chưa chạy | Chưa chạy |
| T2-15 | WP2-03 | Lỗi đo | Inject lỗi trong job/k6 có trial exit 99 hoặc phản hồi sai | Giữ lỗi và dữ liệu thật, không báo tất cả đạt; cleanup hoàn tất | Chưa chạy | Chưa chạy |
| T2-16 | WP2-03 | Publish | Sai credential hoặc Langfuse không lên | Báo publish chưa xong, giữ file để kiểm tra lại, trả lỗi và dọn; không dựng kết quả thay thế | Chưa chạy | Chưa chạy |
| T2-17 | WP2-01 | Hai phiên | Mở phiên thứ hai cùng state/project khi phiên đầu chạy | Phiên sau báo bận và không down/nhả khóa của phiên trước | Chưa chạy | Chưa chạy |
| T2-18 | WP2-03 | Lịch sử | Sau cleanup, chạy view rồi down lại | Không ingest/retrieval/k6; đủ lịch sử, account giữ nguyên, down lặp không mất volume/cache | Chưa chạy | Chưa chạy |
| T2-19 | WP2-03 | Phiên dở | Mô phỏng khóa stale; Docker không tới được khi cleanup | Nhận diện đúng chủ sở hữu, báo dọn chưa hoàn tất; down dự phòng chỉ chạm tài nguyên đánh giá | Chưa chạy | Chưa chạy |
| T2-20 | WP2-04 | Ranh giới | Chạy cùng stack Farm/Lab hoặc container chứng kiến ngoài project | Không chiếm 5432, không stop/restart/write stack kia; startup sản phẩm không gọi wrapper | Chưa chạy | Chưa chạy |
| T2-21 | WP2-04 | Bảo mật/file thừa | Quét diff, log, build context, mount và quyền file | Không secret/hash literal trong Git, không mount env/token thật; report không track, ignore không đổi | Chưa chạy | Chưa chạy |
| T2-22 | WP2-04 | Tài liệu | Người đọc làm theo riêng hai mục ai/README trên checkout mới | Gọi đúng một lệnh, biết kết quả/giới hạn/tự dọn; không cần các bước thủ công cũ | Chưa chạy | Chưa chạy |

## 3. Hồi quy

- [ ] CLI collect/score/publish và benchmark hiện có vẫn chạy độc lập với đầu vào hợp lệ.
- [ ] Query, namespace, k, thứ tự sau boost, metric, bộ đề và nhãn không đổi.
- [ ] `ai/app/`, API, backend/UI, data nghiệp vụ, `deploy/`, CI và runtime dependency không có diff của phase.
- [ ] Không thêm dịch vụ/job vào lệnh khởi động Farm/Lab; không cần dựng lại toàn stack để dùng đánh giá.
- [ ] Cache, volume, account, run cũ và project thủ công của đợt trước không bị tự chuyển đổi/xóa.

## 4. Tiêu chí đạt

Mọi case bắt buộc đạt và mục 6 của overview có bằng chứng.
Điểm/RPS không có ngưỡng tùy ý; điều kiện đạt là phép đo đúng, đủ, tái lập được và cleanup đúng domain.
Nếu chưa chạy được cold case do thiếu tài nguyên thì để chưa đạt, không lấy warm run thay thế.
SIGKILL/mất điện ngoài khả năng trap được công bố rõ, không ghi thành đảm bảo tự dọn tuyệt đối.

## 5. Bằng chứng và báo lỗi

Mỗi run lưu cấu hình đã khử bí mật, phiên bản, provenance, qrels/run, score, k6 JSON/HTML, mẫu Docker, exit status và dấu mốc cleanup ở state ngoài checkout.
Ghi kiểm tra container/project trước/sau để chứng minh phạm vi dọn và bảo toàn dữ liệu.
Ghi ai chạy, ngày và artifact thực tế vào bảng; lỗi ngoài domain chỉ ghi nhận, không sửa ké.
