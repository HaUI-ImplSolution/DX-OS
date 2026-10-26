# Phase 2: Bộ chạy đánh giá RAG độc lập

| Mục | Giá trị |
|---|---|
| Trạng thái | Đang triển khai; đã thêm skeleton runner, chưa kiểm thử integration |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | Công cụ đánh giá trong `ai/`, môi trường Docker riêng |
| Ước lượng | 2–3 ngày triển khai và kiểm tra; chưa tính thời gian tải/build lần đầu |
| Người phụ trách | Người triển khai; chủ dự án duyệt kế hoạch |
| Bắt đầu, dự kiến xong | Bắt đầu triển khai 26/10/2026; chưa chốt ngày hoàn tất |
| Phụ thuộc | Các công cụ collect/score/publish và thử tải đã có trên `eval/rag-baseline` |

## 1. Tham chiếu

| Nguồn | Nội dung | Phạm vi |
|---|---|---|
| Yêu cầu chủ dự án | Một file `.sh` chọn đo chất lượng, hiệu năng hoặc cả hai | Toàn bộ bộ chạy |
| Yêu cầu chủ dự án | Tự dọn sau khi xem, hết thời gian hoặc hủy; giữ cache và kết quả | Vòng đời môi trường đánh giá |
| Yêu cầu chủ dự án | Độc lập với sử dụng sản phẩm và cập nhật `ai/README.md` khi làm xong | Không đổi khởi động Farm/Lab |
| [`docs/_template`](../_template/README.md) | Kế hoạch, checklist, kiểm thử và bàn giao | Năm tài liệu của đợt này |

## 2. Lý do

Công cụ đánh giá đã chạy thật nhưng người mới clone còn phải chuẩn bị database, model, dependency và Langfuse bằng nhiều lệnh.
Cần đóng gói việc chuẩn bị và dọn môi trường để người dùng chủ động kiểm tra bằng một lệnh.

## 3. Tổng quan sửa đổi

Thêm `ai/evaluate.sh` và cấu hình môi trường đánh giá riêng.
Tự chuẩn bị credential, image, cache embedding, database và bằng chứng ingest.
Gọi lại các công cụ hiện có, lưu kết quả rồi dừng AI/database đánh giá.
Bật Langfuse để publish và xem; nhấn Enter hoặc hết thời gian thì tự down.
Cập nhật hai mục đánh giá trong `ai/README.md` sau khi kiểm chứng được luồng chạy.

| Module | Có thay đổi | Ghi chú |
|---|---|---|
| `dx-core-api`, `dx-core`, `app-farm`, `app-lab`, `dx-host`, `dx-monitor` | Không | Giữ API, dữ liệu và nghiệp vụ |
| Frontend, Mobile, Portal, SSO | Không | Dùng giao diện Langfuse/k6 có sẵn |
| AI | Có, giới hạn | Bộ điều phối và chuẩn bị môi trường đánh giá; không sửa `ai/app/` |
| Data / Seed / Migration sản phẩm | Không | Đọc corpus/bộ đề có sẵn; chỉ khởi tạo DB đánh giá |
| Infra / Deploy / CI sản phẩm | Không | Compose đánh giá đặt trong `ai/evaluation/` |
| Docs | Có | Hướng dẫn AI và phần kiểm thử liên quan |

## 4. Phạm vi và domain

**Domain duy nhất: vận hành bộ công cụ đánh giá truy xuất RAG ở local.**

- Trong phạm vi: CLI `.sh`, Docker đánh giá, khởi tạo DB riêng, cache model, gọi công cụ hiện có, publish, xem kết quả và tự dọn.
- Chất lượng: giữ 12 câu Farm QA, 24 câu Lab QA, 6 query chẩn đoán; giữ namespace, k, bộ nhãn và công thức hiện có.
- Hiệu năng: HTTP retrieval, 1/2/5 VU, 30 giây, ba lượt lặp; giữ cách đo và đầu ra k6/Docker hiện có.
- Ngoài phạm vi: cải thiện điểm, sửa nhãn, chunker, embedding, ranking, ngưỡng Backend, LLM, CV, thuốc/liều/PHI hoặc chẩn đoán toàn pipeline.
- Không thêm lệnh gọi bộ đánh giá vào `deploy/`, Dockerfile/Compose sản phẩm, lifecycle AI hoặc CI.
- Môi trường mục tiêu: Linux x86_64, Bash, Docker Engine local và Compose hỗ trợ healthcheck/`--wait`; không yêu cầu Python, Node, JDK, k6 hoặc HF CLI trên host.
- Docker Desktop, daemon từ xa, public Langfuse và Cloud nằm ngoài cam kết tương thích của đợt này.
- File được sửa và các skill áp dụng ghi ở `01_PLAN.md`; phát sinh ngoài danh sách phải đối chiếu lại domain trước khi làm.

## 5. Ưu điểm và hạn chế

Một lệnh tạo được kết quả thật và bằng chứng kiểm tra lại, không cần sản phẩm đang chạy.
Lần đầu cần mạng, dung lượng và RAM để tải/build các thành phần còn thiếu; lần sau giữ cache.
Tự dọn có hiệu lực khi shell xử lý được exit/signal; không cam kết bắt `SIGKILL` hoặc xử lý khi máy mất điện.
Các giới hạn còn lại và phương án dọn phiên dở ở `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi

- Clone sạch, dùng một lệnh `quality`, `performance` hoặc `all`, không phải tự viết env, ingest hay tạo tài khoản Langfuse.
- Terminal in chế độ, tiến độ, kết quả thật, link local, tài khoản đăng nhập và vị trí bằng chứng; không in service/API key.
- `quality` tạo đủ 42 bản ghi và ba nhóm điểm; `performance` có đủ 18 lượt cùng mẫu tài nguyên, JSON và HTML k6.
- Langfuse hiển thị đúng kết quả đã lưu; trace publish được ghi rõ là replay, không dùng latency replay thay latency đo.
- Sau đo, AI/vector DB đã dừng trước giai đoạn xem; Enter, hết 600 giây, lỗi hoặc tín hiệu được xử lý đều dọn đúng tài nguyên do phiên sở hữu.
- Không còn container đánh giá sau cleanup thành công; volume, credential, cache và lịch sử kết quả còn nguyên, `view` mở lại được.
- Không có TTY thì các chế độ đo lưu/publish rồi tự dọn ngay; `view` báo cần terminal tương tác trước khi mở dịch vụ.
- Chạy lại dùng cache, không ghi đè lần đo cũ hoặc tự nâng dependency/model; điểm thấp vẫn giữ nguyên, lỗi không bị báo thành thành công.
- Không sửa file/runtime sản phẩm; project/container khác không bị stop, prune hoặc đổi dữ liệu.
- `ai/README.md` phản ánh đúng hành vi đã kiểm thử; các case bắt buộc có bằng chứng rồi mới đóng phase.

## 7. Phụ thuộc và thứ tự

WP2-01 hợp đồng và ranh giới → WP2-02 môi trường/bằng chứng → WP2-03 chạy và tự dọn → WP2-04 kiểm tra và tài liệu.
Không phụ thuộc việc rà nhãn độc lập trong BACKLOG; trạng thái/phê duyệt bộ nhãn hiện tại được giữ nguyên trong mọi lần xuất.

## 8. Ảnh hưởng đến thi

Hỗ trợ người xem tự chạy và kiểm tra lại chỉ số bằng công cụ công khai.
Không quy đổi việc có dashboard thành chứng nhận độc lập hoặc độ chính xác toàn sản phẩm.

## 9. Câu hỏi mở và quyết định

| # | Nội dung | Kết luận |
|---|---|---|
| Q1 | Các chế độ gọi | `quality`, `performance`, `all`, `view`, `down`, `--help` theo trao đổi |
| Q2 | Tự dọn | Chủ dự án yêu cầu tự dọn; đề xuất Enter hoặc 600 giây từ lúc sẵn sàng xem, cấu hình được trong env mẫu |
| Q3 | Hệ điều hành hỗ trợ ban đầu | Đề xuất Linux x86_64/Docker Engine local, phù hợp môi trường đã kiểm tra |
| Q4 | Bắt đầu triển khai | Đã bắt đầu; code wrapper/Compose/bootstrap đã có, cần Docker Engine Linux để kiểm thử thật |

## 10. Tài liệu cập nhật khi xong

- [ ] `ai/README.md`: sửa hai mục đánh giá thành hướng dẫn `.sh`, đầu ra, cache và tự dọn; giữ các mục chạy AI/CV/nghiệp vụ khác.
- [ ] `docs/TESTING.md`: cập nhật cách gọi và bằng chứng mới trong hai mục đánh giá; giữ kết quả lịch sử đúng phiên bản.
- [ ] `CHANGELOG.md`: chỉ thêm chức năng đã hoàn tất, không ghi tên phase hoặc xóa log cũ.
- [ ] `LICENSES.md`: chỉ cập nhật nếu thêm thành phần vào image công cụ; runtime SBOM giữ nguyên theo `sbom/README.md`.
- [ ] `docs/BACKLOG.md`: chỉ cập nhật việc liên quan; giữ các mục kiểm chứng nhãn/tái lập chưa thực hiện.
- [ ] Sau bàn giao, gộp việc còn lại vào BACKLOG và xóa thư mục kế hoạch theo template.

## 11. Cập nhật tiến độ

Đã thêm `ai/evaluate.sh`, `ai/evaluation/docker-compose.yml`, Dockerfile riêng, helper bootstrap database/provenance và k6 vào image đánh giá.
Kiểm tra tĩnh đã đạt (`bash -n`, `py_compile`, `git diff --check`, help). Chưa đánh dấu các case integration vì Docker daemon Linux chưa sẵn sàng trên máy thực hiện.
