# Plan thực hiện: Phase 2

Chỉ lập kế hoạch trong đợt hiện tại.
Khi triển khai, làm theo WP2-01 → WP2-02 → WP2-03 → WP2-04; không có bước sửa Swagger vì không đổi API.

## Skill và kiến trúc đã đối chiếu

| Hướng dẫn | Áp dụng vào kế hoạch |
|---|---|
| [`ai/.codex/AGENTS.md`](../../ai/.codex/AGENTS.md) | Tái sử dụng công cụ, stdlib và Compose; một helper chuẩn bị, không thêm framework/UI/queue |
| [`verification-before-completion`](../../ai/.codex/skills/verification-before-completion/SKILL.md) | Tách case dự kiến khỏi kết quả đã chạy; chỉ tích khi có lệnh, exit code và bằng chứng thật |
| [`langchain-dependencies`](../../ai/.codex/skills/langchain-dependencies/SKILL.md) | Dùng lock runtime hiện có; dependency đánh giá ở môi trường riêng, không nâng LangChain/LangGraph hoặc thêm LangSmith |
| [`hf-cli`](../../ai/.codex/skills/hf-cli/SKILL.md) | Reuse cache/snapshot local; không force-download, prune hoặc đòi token cho model công khai |
| [`langchain-rag`](../../ai/.codex/skills/langchain-rag/SKILL.md) | Ingest/query cùng artifact embedding, nguồn dữ liệu và chunker hiện có; không đổi thuật toán theo ví dụ của skill |

Không có skill Bash/Docker chuyên biệt trong các skill cục bộ đã tìm thấy.
Đối chiếu thêm tài liệu chính thức về [project Compose](https://docs.docker.com/compose/how-tos/project-name/), [down](https://docs.docker.com/reference/cli/docker/compose/down/), [host networking](https://docs.docker.com/engine/network/drivers/host/), [Langfuse local](https://langfuse.com/self-hosting/deployment/docker-compose), cùng `help trap`/`help read` của Bash.

## WP2-01: Hợp đồng lệnh và ranh giới

- Tham chiếu: `00_OVERVIEW.md`, mục 4 và 6; ước lượng S.
- Mục tiêu: chốt giao diện opt-in và quy tắc sở hữu tài nguyên trước khi viết bộ chạy.

| Lệnh dự kiến | Hành vi |
|---|---|
| `./ai/evaluate.sh quality` | Chuẩn bị → collect 42 lượt → score → dừng AI/vector → publish/xem → tự down |
| `./ai/evaluate.sh performance` | Chuẩn bị → chạy 18 lượt k6 → dừng AI/vector → publish/xem → tự down |
| `./ai/evaluate.sh all` | Chuẩn bị một lần; chạy chất lượng rồi hiệu năng tuần tự; xem rồi tự down |
| `./ai/evaluate.sh view` | Chỉ mở lịch sử đã có, không ingest/retrieval/k6; xem rồi tự down |
| `./ai/evaluate.sh down` | Dọn riêng project đánh giá khi cần khôi phục phiên dở; giữ dữ liệu/cache |
| `./ai/evaluate.sh --help` hoặc không truyền mode | In cách dùng; không tạo image, file cấu hình hay container |

- Dùng đường dẫn tuyệt đối từ vị trí script, quote tham số; không phụ thuộc thư mục gọi và không `eval`/`source` env như mã shell.
- Ép rõ Compose `-p`, `-f`, `--env-file`; không nhận nhầm `COMPOSE_FILE`, project hoặc env của sản phẩm từ shell.
- Project mặc định `dx-rag-evaluation-runner`; state mặc định `~/.local/share/dx-os-rag-evaluation-runner/` để không nhận nhầm môi trường thủ công cũ.
- Chỉ chấp nhận project thuộc namespace của wrapper và có marker sở hữu khớp state; từ chối tên project sản phẩm hoặc môi trường thủ công cũ trước mọi up/down.
- Kiểm state path thật nằm ngoài checkout, kể cả qua symlink; khóa một phiên trên mỗi project/state.
- Phiên thứ hai bị báo bận trước khi tạo tài nguyên và không được cleanup phiên thứ nhất; `down` của phiên đang hoạt động hướng dẫn dùng Enter/Ctrl+C tại terminal sở hữu.
- Preflight Docker local, phiên bản Compose/tính năng, quyền socket, kiến trúc, RAM/disk/cổng; thiếu điều kiện thì báo lỗi, không tự cài host hoặc dừng stack khác.
- Kết quả: cách gọi và phạm vi cleanup nhất quán; không đổi lệnh khởi động sản phẩm.

## WP2-02: Môi trường và dữ liệu đánh giá tự chuẩn bị

- Tham chiếu: `00_OVERVIEW.md`, mục 3–6; ước lượng M.
- Mục tiêu: clone mới không phải dựa vào credential, image local hoặc model path trên máy người triển khai.

**Hành vi thay đổi:** từ chuẩn bị thủ công sang cấu hình riêng được sinh và tái sử dụng tự động.

- Thêm một Compose đánh giá, một Dockerfile công cụ và một `.env.example` riêng; không sửa `deploy/` hoặc `ai/Dockerfile`.
- Dockerfile công cụ dùng Python/runtime theo lock hiện có, môi trường dependency đánh giá riêng, k6 và Docker CLI; không nạp/ghép trọng số CV.
- Chuẩn bị build context nhỏ ngoài checkout từ các file dependency cần thiết; không gửi `deploy/data`, `report/`, trọng số CV hay env sản phẩm vào build context.
- Không phụ thuộc tag local `deploy-ai`/`deploy-minio`: tag image đánh giá riêng, reuse cache/layer; thiếu mới pull/build tuần tự, không `--no-cache` hoặc build cả DX-OS.
- MinIO có thể dùng lại recipe [`minio/Dockerfile`](../../minio/Dockerfile) ở chế độ chỉ đọc để build tag đánh giá riêng; không sửa recipe hoặc ghi đè tag sản phẩm.
- Langfuse dùng kiến trúc Compose chính thức; khởi đầu từ release server/SDK đã kiểm tra 4.70.1/5.0.4, không tự nâng mỗi lần chạy.
- Mọi giá trị mặc định ở `.env.example`; Compose lấy giá trị tường minh/bắt buộc, không rải `${VAR:-...}` hoặc password mặc định vào YAML.
- Sinh credential ngẫu nhiên, lưu state mode 0700 và env mode 0600; dùng lại khi chạy tiếp, in thông tin đăng nhập nhưng không in API/service key.
- PostgreSQL Langfuse và pgvector đánh giá có DB/volume riêng; Redis, ClickHouse, MinIO ở mạng riêng, không publish DB hoặc dùng cổng 5432.
- Chỉ publish UI/báo cáo và endpoint đo trên loopback; đề xuất cổng 13000/18765/18088, lấy từ env mẫu và dừng rõ ràng nếu trùng.
- Model công khai hiện có được tải khi cache thiếu; lưu artifact local và provenance, các lần sau dùng snapshot local/offline, không tự theo revision mới.
- Helper `prepare.py` chỉ dùng ORM/schema và `ingest_corpus` hiện có cho database đánh giá; tạo role ingest và role truy xuất mặc định chỉ đọc, ghi `ingest.json` đúng cấu hình.
- Nếu corpus/model/runtime hoặc nội dung chunk khác cache, chỉ làm mới DB đánh giá và ghi bằng chứng; không đổi corpus/bộ nhãn hoặc truy cập DB nghiệp vụ.
- Mount chọn lọc app/scripts/corpus ở chế độ đọc; không đọc `ai/.env`, `deploy/.env`, `report/`, token HF hoặc dữ liệu người dùng.
- Kết quả: môi trường mới có đủ nguồn vector/model xác minh được; chạy lại giữ cache, credential, dữ liệu Langfuse và lịch sử.

## WP2-03: Chạy, publish và tự dọn

- Tham chiếu: `00_OVERVIEW.md`, mục 6; ước lượng M.
- Mục tiêu: một lệnh tạo kết quả thật và tự giải phóng tài nguyên sau cửa sổ xem.

**Hành vi thay đổi:** từ gọi nhiều CLI rồi tự down sang một vòng đời có chủ sở hữu.

- Gọi nguyên CLI `evaluate_rag.py collect/score/publish`, `benchmark_rag.py serve/run/publish` và `load_rag.js`; không chép lại metric, retrieval hoặc xử lý Backend.
- Job ingest/collect dùng mạng Docker riêng; client k6/publish dùng host networking của Linux để giữ endpoint loopback theo validator hiện tại.
- Chỉ job lấy số liệu Docker được mount socket; Docker CLI trong image làm việc bằng quyền người gọi, không dùng `privileged` hoặc mount filesystem host tùy ý.
- Lấy ID/tên container qua label đúng project để sampler ghi đúng AI/vector DB; không hardcode tên container của máy đã test.
- Chế độ performance dùng app hiện có với CV lifespan tắt, 1 worker, Torch 2 thread, giới hạn AI 2 CPU/1536 MiB và vector 1 CPU/256 MiB như phép đo đã có.
- Langfuse/report server không chạy trong cửa sổ tạo tải; chấm/lấy mẫu xong mới dừng AI/vector và bật Langfuse để publish.
- Mỗi run tạo thư mục mới với cấu hình, phiên bản, bộ đề/nhãn, provenance, kết quả thô, score và trạng thái thành công/thất bại; không hardcode điểm hoặc số request.
- Giữ nguyên việc phân biệt owner approval với nhãn `reviewed`; wrapper không tự duyệt nhãn, thêm nhãn 0, bỏ câu khó hoặc biến điểm thành accuracy.
- Publish dùng SDK/receipt hiện có; lỗi dịch vụ giữ artifact và báo lỗi, không tự chạy lại truy xuất để thay kết quả.
- In link Langfuse/HTML khi sẵn sàng, tài khoản local, vị trí kết quả, cách thoát và hạn xem; latency replay khác latency đo được ghi rõ.
- TTY chờ Enter tối đa `EVAL_VIEW_TIMEOUT_SECONDS=600` tính từ khi UI/publish sẵn sàng, không tính vào thời gian đo; `view` dùng cùng quy tắc.
- Không TTY: các lệnh đo lưu/publish rồi down ngay, in vị trí file; `view` từ chối trước startup, không giả vờ link vẫn hoạt động.
- Đăng ký cleanup cho `EXIT`, `INT`, `TERM`, `HUP`; dừng/reap job con trước, down đúng Compose project rồi nhả khóa, cleanup lặp lại được.
- Không dùng `down -v`, `--rmi`, prune toàn máy hoặc restart policy tự bật lại; volume/cache/kết quả được giữ.
- Bảo toàn lỗi gốc và phân biệt lỗi cleanup; k6 có trial exit 99/lỗi phản hồi không được wrapper báo thành tất cả đạt dù đã lưu đủ artifact.
- Kết quả: Enter, timeout, kết thúc hoặc signal bình thường không để lại container phiên đánh giá; không chạm project khác.

## WP2-04: Kiểm tra và tài liệu

- Tham chiếu: `03_TEST_PLAN.md`; ước lượng M.
- Thêm một self-check nhỏ cho điều phối/cleanup khi cần; giữ self-check công cụ đã có, không thêm framework test vào CI.
- Kiểm tra clone/state mới, warm cache, từng mode, hủy/lỗi/timeout, hai phiên và chạy cùng stack sản phẩm; ghi rõ phạm vi cache được làm trống.
- Không prune image/volume thật để giả lập clone sạch; cold case chỉ chạy khi đủ tài nguyên, không thay bằng kết quả warm rồi tích đạt.
- Cập nhật `ai/README.md` tại hai mục đánh giá: lệnh `.sh`, điều kiện Docker/Linux, đầu ra, thời gian xem, cache, cleanup và `view`.
- Giữ hướng dẫn CLI thấp tầng ngắn ở mục nâng cao để người khác kiểm tra; không sửa các phần chạy model/CV/Farm/Lab.
- `docs/TESTING.md` chỉ sửa phần đánh giá liên quan, ghi bằng chứng mới; LICENSES cập nhật nếu thực tế thêm thành phần, SBOM theo quy định runtime.
- Kết quả: các case bắt buộc có bằng chứng, diff nằm trong allowlist, tài liệu khớp hành vi thật.

## Danh sách file được phép thay đổi khi triển khai

| File | Thay đổi được phép |
|---|---|
| `ai/evaluate.sh` | Entry point, preflight, lifecycle, cleanup và help |
| `ai/evaluation/docker-compose.yml` | Chỉ dịch vụ/job đánh giá và tài nguyên riêng |
| `ai/evaluation/.env.example` | Default không nhạy cảm; secret để trống, sinh ở state ngoài Git |
| `ai/evaluation/Dockerfile` | Image công cụ riêng, khóa phiên bản, cache build |
| `ai/evaluation/prepare.py` | Bootstrap env/DB/provenance bằng helper hiện có, self-check nhỏ nếu cần |
| `ai/README.md` | Hai mục đánh giá và cách gọi công cụ kiểm tra liên quan |
| `docs/TESTING.md`, `CHANGELOG.md`, `docs/BACKLOG.md` | Chỉ nội dung gắn trực tiếp với bộ chạy |
| `LICENSES.md` | Chỉ thành phần đánh giá thực sự được thêm |
| `docs/phase-2-rag-evaluation-runner/` | Kế hoạch, checklist và kết quả kiểm tra của đợt này |

`ai/scripts/evaluate_rag.py`, `benchmark_rag.py`, `load_rag.js`, `app/`, corpus, bộ đề/nhãn, lock/runtime requirements, Dockerfile sản phẩm và `minio/Dockerfile` chỉ được đọc/tái sử dụng.
Nếu CLI hiện có thật sự không đủ, phải ghi rõ adapter tối thiểu và cập nhật phạm vi trước khi sửa; không sửa thuật toán hoặc thêm framework để tiện cho wrapper.

## Hợp đồng, cấu hình và rollback

| Loại | Phạm vi | Thay đổi |
|---|---|---|
| API/schema/role sản phẩm | Không | Giữ nguyên |
| CLI đánh giá | Có | Năm mode và `--help`; CLI cũ vẫn dùng độc lập |
| Env đánh giá | Có | Prefix `EVAL_`: project/state, cổng, timeout, limits và tham số tải; defaults ở env mẫu |
| Credential | Chỉ local | Sinh riêng, không dùng khóa/tài khoản sản phẩm |
| Dữ liệu | Chỉ state/volume đánh giá | Không dùng `report/`, không sửa `.gitignore`; không mất dữ liệu khi down |

Rollback bằng dừng bộ chạy/down đúng project đánh giá; giữ image/cache/volume và kết quả.
State cũ thủ công không được tự nhận làm state của wrapper, chuyển đổi hoặc xóa.
Quyết định giữ nguyên: một entry point, không tích hợp runtime, không UI riêng, không cải thiện điểm trong đợt đóng gói này.
