# Trade-off và rủi ro: Phase 2

## 1. Trade-off

| # | Quyết định | Chọn đề xuất | Được | Mất / chi phí đảo ngược |
|---|---|---|---|---|
| TO1 | Một lệnh nhưng giữ domain nhỏ | Shell điều phối Compose/CLI, helper chuẩn bị bằng code có sẵn | Người dùng không cài Python/k6; không thêm framework | Vài file hỗ trợ trong ai/evaluation; đổi wrapper không đổi sản phẩm |
| TO2 | Dọn và xem kết quả | Dừng AI/vector sau đo, giữ UI tối đa 600 giây/Enter rồi down | Giảm RAM và không quên UI chạy lâu | Link ngừng sau down; mở lại bằng view, HTML còn trên đĩa |
| TO3 | Bộ chạy trong container | Linux Engine, client host network để giữ localhost validator | Reuse CLI hiện có, không sửa API | Chưa cam kết Docker Desktop/đa nền tảng; mở rộng cần kiểm riêng |
| TO4 | Cold start và cache | Build/pull thiếu, giữ snapshot/image/volume đã dùng | Các lần sau nhẹ hơn, không đổi model ngầm | Lần đầu vẫn cần mạng/dung lượng/RAM; không phải sản phẩm không cần Docker |

## 2. Rủi ro

| # | Rủi ro | Xác suất / ảnh hưởng | Giảm thiểu | Dự phòng |
|---|---|---|---|---|
| R1 | Cleanup chạm nhầm sản phẩm/phiên khác | Vừa / Cao | Project/context/state tường minh, label/khóa chủ sở hữu; không nhận env sản phẩm | Dừng và báo lỗi trước thao tác không xác minh được |
| R2 | Chỉ chạy được nhờ file/image cache trên máy tác giả | Cao / Cao | Cold case không có private state/tag local; mọi recipe và dependency cần thiết có trong checkout | Chưa đủ bằng chứng thì không đóng phase |
| R3 | OOM/đầy ổ/trùng cổng | Cao / Cao | Preflight, limits, build tuần tự, dừng AI trước UI, DB không publish | Báo thiếu điều kiện, không prune hoặc tắt project khác |
| R4 | Docker socket có quyền điều khiển daemon | Vừa / Cao | Chỉ client đo cần socket; guard project/lệnh, nguồn RO, không privileged | Không đủ quyền thì báo lỗi, không đổi quyền host tự động |
| R5 | Trap không chạy khi SIGKILL/mất điện | Vừa / Vừa | Restart no, giữ dấu phiên và dữ liệu; không hứa cleanup tuyệt đối | Nhận diện phiên dở khi chạy lại, down đúng project |
| R6 | Dọn làm mất lịch sử/cache hoặc đổi credential | Vừa / Cao | Named volume/state bền vững; không down -v/rmi/prune, không ghi đè run | Dừng trước cấu hình state không tương thích; không tự migrate môi trường cũ |
| R7 | Model/dependency đổi làm phép đo khác | Vừa / Cao | Snapshot local, lock runtime, eval env riêng và provenance | Từ chối mismatch; chỉ rebuild/ingest DB đánh giá |
| R8 | Publish/cleanup lỗi nhưng terminal báo thành công | Vừa / Cao | Giữ exit status, kiểm trial lỗi và trạng thái cleanup; không dùng latency replay | Giữ artifact, báo phần chưa hoàn tất để chạy lại đúng bước |

## 3. Nợ kỹ thuật chấp nhận

Không nâng chất lượng RAG, rà nhãn đầy đủ, chạy tái lập độc lập thay người khác hoặc đo end-to-end sản phẩm.
Không làm Langfuse HA, Cloud, UI riêng, watchdog chống mọi kiểu kill, cron hoặc tự khởi động sau reboot.
Không sửa quyền Docker, cấu hình proxy/daemon hoặc cài Docker lên host của người dùng.

## 4. Điều kiện dừng và rollback

Dừng phần liên quan nếu cần sửa runtime/model/corpus/deploy/CI ngoài allowlist hoặc phải xóa dữ liệu project khác để chạy được.
Không đổi công thức/nhãn để làm đẹp điểm hoặc tự đánh dấu một case chưa chạy là đạt.
Rollback bằng hủy bộ chạy và down project đánh giá, giữ volume/cache/kết quả; lỗi daemon được báo rõ để xử lý khi kết nối lại.

## 5. Tác động ngoài phase

Farm/Lab và màn monitor của main giữ nguyên cách chạy.
AI README chỉ đổi hướng dẫn đánh giá sau khi có bằng chứng chạy wrapper mới.
Các việc độc lập về nhãn/bên thứ ba vẫn ở BACKLOG; không được xóa để đóng đợt đóng gói này.
