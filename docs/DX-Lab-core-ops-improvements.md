# DX-Lab: đề xuất cải tiến Core (Tầng 1, 2) và Vận hành

Ngày lập: 28/10/2026, cập nhật 29/10/2026 · Phạm vi: `DX---Farm` (repo chung của DX-Farm và DX-Lab), **chỉ phần Core, nền tảng và vận hành**.
Không đề cập nghiệp vụ của Farm hay Lab.
Nguồn so sánh: dự án **Signet** (`/home/khuongpc/KhuongPC/KhuongSpring/Signet`, Apache-2.0), cùng đề thi OLP PMNM 2026.

> **Trạng thái:** các việc đã làm (sao lưu và khôi phục, gửi lại tin chat, mã request sang AI, TOTP và chính sách mật khẩu, cứng hóa container, quét mã độc, cầu dao gọi AI, cảnh báo đăng nhập sai theo IP, quét lỗ hổng bằng Trivy, sinh `.env`, healthcheck kèm `depends_on: service_healthy`, giới hạn tốc độ theo người dùng ở backend, Dependabot, quét image bằng Trivy, đọc `Idempotency-Key` ở Core, kho ảnh SeaweedFS) đã được bỏ khỏi tài liệu này; phần còn lại là việc chưa làm hoặc làm dở.

## 0. Cách đọc tài liệu này

**Độ ưu tiên**

| Mức | Ý nghĩa |
|---|---|
| **P0** | Lỗ hổng lớn về an toàn hoặc mất dữ liệu, hoặc rẻ mà hiệu quả cao. Nên làm trước |
| **P1** | Giá trị cao, đáng làm trong đợt này |
| **P2** | Tốt, làm khi còn thời gian |
| **P3** | Có thì hay, không gấp |

**Công sức:** S = dưới 1 ngày · M = 2–5 ngày · L = hơn 1 tuần.

**Độ chắc chắn.** Cột "Hiện trạng DX" ghi điều mình **đã kiểm trong repo** (đường dẫn kèm theo).
Chỗ nào chỉ là suy luận thì ghi "chưa kiểm".
Phần "Tham khảo Signet" chỉ đường dẫn bên Signet để đọc cách họ làm, các số liệu của Signet lấy từ tài liệu của họ và mình chưa chạy thử.

**Về stack.** Signet dùng Python + Hasura + Flowable, DX dùng Java Spring.
Cấu hình hạ tầng (APISIX, Prometheus, Keycloak, restic, Vector…) dùng lại gần như nguyên xi.
Mã ứng dụng thì chỉ lấy ý tưởng và viết lại.

## 1. Tóm tắt

DX đã có nền tốt: ranh giới module do trình biên dịch chặn, `AdvisoryJobLock` cho tác vụ định kỳ, `dx-monitor` chạy tách riêng, log truy cập JSON kèm `X-Request-Id`, audit log chỉ thêm, gitleaks, CI e2e cho cả hai ứng dụng.
Các lỗ hổng lớn nhất của đợt đầu (sao lưu, thông báo bị lỡ, xác thực hai bước, container chạy root) đã được xử lý.

Phần còn thiếu nằm ở **quan sát và phát hành**, đúng chỗ Signet làm sâu:

1. **Chưa có metric, cảnh báo ngoài, giám sát từ bên ngoài, log tập trung, tracing** (nhóm O).
2. **Phát hành chưa đủ chuẩn PoF**: workflow `release.yml` chỉ đẩy image GHCR bằng tay, không tạo GitHub Release kèm SBOM (R5).
3. **Chưa kiểm giấy phép tự động** (C2).
4. **Nâng cấp chưa có đường lùi**, chưa có compose profiles cho máy yếu (R1, R2).

## 2. Lộ trình gợi ý theo đợt

| Đợt | Nội dung | Ước lượng |
|---|---|---|
| **1. Việc rẻ, hiệu quả ngay** | D5 | vài ngày |
| **2. Nền vận hành** | O1, O2, O3, C2, R2, R5 | 2–3 tuần |
| **3. Độ bền và quan sát sâu** | D3, D4, R1, O5, O6, O8, K2, K3 | 2–3 tuần |
| **4. Tùy chọn** | PostgreSQL RLS, PITR, scaffold ứng dụng mới | khi dư sức |

## 3. Bảo mật và danh tính

Mục này không làm thêm.
Các việc để lại: hạn phiên và `sslRequired` (S2), WAF Coraza + OWASP CRS (S3), giới hạn RAM/CPU và hệ tệp chỉ đọc cho container (S7), nâng Superset, Mattermost và APISIX (S8), xoay khóa giữa các dịch vụ (S9), két bí mật OpenBao (S11).

## 4. Toàn vẹn và độ bền dữ liệu

| ID | Cải tiến | Hiện trạng DX (đã kiểm) | Tham khảo Signet | Mức | Công sức |
|---|---|---|---|---|---|
| **D3** | **Sổ kiểm toán chuỗi băm + neo mốc ra ngoài CSDL.** Mỗi dòng có `prev_hash`, `hash = sha256(prev_hash ‖ nội dung)`; trigger tính hash, script độc lập kiểm chuỗi, định kỳ ký và đẩy hash đầu chuỗi ra ngoài (tệp công khai, git, bucket). Kết hợp **phân vùng theo tháng + lưu trữ** để giải bài toán "xóa audit theo thời hạn" của backlog mà vẫn giữ tính toàn vẹn | `V4__audit_log.sql`: có trigger chặn UPDATE/DELETE nhưng superuser tắt trigger là sửa lén được, không phát hiện được. Backlog §5: chưa có cách xóa theo thời hạn | `scripts/audit_chain.py`, `scripts/anchor_sig.py`, `db/tests/test_audit.py` (có ca sửa lén khi tắt trigger, viết lại cả chuỗi, cắt đuôi) | P1 | M |
| **D4** | **Đường bất đồng bộ cho tác vụ chậm**: trả 202 rồi báo kết quả qua thông báo hoặc cho poll, thay vì giữ kết nối tới khi gateway 504; tùy chọn hàng đợi ở AI service | Cầu dao gọi AI đã có. Chẩn đoán vẫn ~50 s/ảnh trên CPU, lần đầu sau khởi động vượt 60 s thì nhận 504, AI service không có hàng đợi (backlog §4) | Tác tử chạy nền, "chạy song song, không chắn việc" trong README Signet | P1 | M |
| **D5** | **Superset dùng PostgreSQL** cho metadata thay SQLite | Backlog §4 xác nhận Superset dùng SQLite | — | P1 | S |
| **D6** | **Che PII ở các đường ra còn lại**: nội dung log (khi có log tập trung), prompt LLM khi có Qwen | Đã che tin chat, câu hỏi tri thức và văn bản triệu chứng gửi sang AI (`PiiMask`) | `services/svc-files/src/svc_files/pii.py`, `make do-che-pii` | P2 | S |
| **D9** | **PITR cho PostgreSQL** (WAL archiving bằng pgBackRest/WAL-G) khi cần RPO thấp hơn bản dump | Chưa có | `scripts/backup.py` chỉ dùng `pg_dump -Fc`, chưa PITR | P3 | L |
| **D10** | **PostgreSQL Row-Level Security** làm lớp phòng thủ thứ hai cho cách ly workspace (`SET LOCAL app.workspace_id` mỗi giao dịch) | Cách ly workspace nằm ở tầng service, chưa kiểm có RLS | Signet khóa quyền ở Hasura và ràng buộc trong DB (`docs/mo-hinh-du-lieu.md`) | P3 | M–L |

## 5. Quan sát và giám sát

DX có `dx-monitor` (sự kiện gateway, dò dịch vụ, cảnh báo Mattermost).
Phần dưới bổ sung metric, cảnh báo ngoài, log và trace, không thay thế `dx-monitor`.

| ID | Cải tiến | Hiện trạng DX (đã kiểm) | Tham khảo Signet | Mức | Công sức |
|---|---|---|---|---|---|
| **O1** | **Metric chuẩn**: Micrometer + `/actuator/prometheus` (chỉ mạng nội bộ), plugin `prometheus` của APISIX, Prometheus + Grafana. Có sẵn các chỉ số: tỷ lệ 5xx, p95, hàng đợi, tác vụ định kỳ lần chạy cuối | `monitor` chỉ dò `/actuator/health`. Backlog §5: chưa có CPU/RAM/p95 | `platform/monitoring/prometheus.yml`, `platform/monitoring/grafana/` | **P1** | M |
| **O2** | **Alertmanager → Mattermost**, luật cảnh báo kiểm bằng `promtool test rules` trong CI. Luật nên có: dịch vụ chết, 5xx cao, độ trễ cao, đĩa gần đầy, bản sao lưu quá cũ, chứng chỉ sắp hết hạn, hàng đợi thông báo tồn đọng, tác vụ định kỳ không chạy. Thêm "dead-man switch" để biết khi chính hệ giám sát chết | Backlog §5: không cảnh báo khi `monitor` ngừng. Cảnh báo `monitor` không nhớ điều kiện sau khi khởi động lại | `platform/monitoring/canh-bao.yml`, `alertmanager.yml`, `kiem-tra/canh-bao.test.yml` | **P1** | M |
| **O3** | **Giám sát từ bên ngoài** (Uptime Kuma hoặc Gatus, hoặc Cloudflare Health Checks) cho đường công khai và trang đăng nhập | Backlog §5: "cần kiểm tra từ ngoài", hiện kiểm bằng `docker compose ps` | Workflow `ban-online.yml` kiểm bản online hằng ngày | **P1** | S |
| **O5** | **Log tập trung**: Vector hoặc Grafana Alloy → Loki (hoặc Parseable), che PII trước khi lưu, tra theo `request_id`, giữ theo thời hạn | Docker xoay vòng log (gateway 10×100 MB, dịch vụ khác 5×20 MB), mất khi container bị tạo lại (`deploy/OPERATIONS.md`) | `platform/logs/vector.yaml`, `vector.test.yaml`, mục "Log tập trung" trong `docs/van-hanh.md` | P2 | M |
| **O6** | **Tracing phân tán** OpenTelemetry: APISIX (plugin `opentelemetry`, lấy mẫu theo tỷ lệ) → Backend → AI service → Jaeger/Tempo. Chỉ ghi span khi gateway đã chọn mẫu | Chưa có | `platform/trace/jaeger.yaml`, mục "Truy vết phân tán" | P2 | M |
| **O7** | **Cảnh báo đăng nhập sai theo tài khoản**: thêm `user_id` vào view `vw_auth_events` hoặc bật `KC_METRICS_ENABLED` rồi đưa vào Prometheus | Đã có cảnh báo theo địa chỉ IP. Sự kiện `LOGIN_ERROR` của Keycloak không ghi tên tài khoản, chỉ `user_id` | Luật cảnh báo APISIX 401/403 trong `canh-bao.yml` | P2 | S–M |
| **O8** | **Lịch sử chạy tác vụ định kỳ**: bảng `job_run` (tên, bắt đầu, kết thúc, kết quả) + metric "lần thành công cuối", cảnh báo khi trễ | `AdvisoryJobLock` đã tránh chạy trùng giữa nhiều bản sao, nhưng không có dấu vết chạy | Trang trạng thái `/trang-thai` của Signet | P2 | S |
| **O9** | Hai việc vặt trong backlog: **lưu bộ nhớ chống lặp cảnh báo** của `monitor` vào DB; **gộp lớp gửi Mattermost** của `monitor` và `core` thành một module nhỏ độc lập, không kéo `dx-core` vào `dx-monitor` | Backlog §5 | — | P3 | S |

## 6. Phát hành, triển khai, vòng đời

| ID | Cải tiến | Hiện trạng DX (đã kiểm) | Tham khảo Signet | Mức | Công sức |
|---|---|---|---|---|---|
| **R1** | **Nâng cấp có đường lùi**: sao lưu trước khi nâng → dừng → nâng → chạy → kiểm; lệnh `lui` khôi phục bản sao lưu vừa lấy. Thêm job CI nâng từ **thẻ phát hành trước** lên bản mới (bắt lỗi migration Flyway và đổi cấu hình) | `deploy/update.sh` chỉ kéo image và tạo lại container. Không có sao lưu trước hay lệnh lùi | `scripts/nang_cap.sh` (`nang/lui/kiem`), `.github/workflows/nang-cap.yml` | P1 | M |
| **R2** | **Compose profiles** (`core`, `ai`, `bi`, `chat`, `monitoring`, `logs`) để chạy bản gọn. Lab không cần mô hình CV; máy 8 GB không phải dựng cả Superset, Mattermost, Ollama | `docker-compose.yml`: chỉ ClamAV có profile (`av`), mọi dịch vụ còn lại luôn chạy; README yêu cầu 8–16 GB RAM, 40 GB đĩa | Signet có 31 profile, đo ~3,2 GB RAM cho cấu hình mặc định | P1 | S–M |
| **R4** | **Lệnh `doctor`/preflight**: kiểm Docker và Compose phiên bản, RAM, đĩa, cổng đã bị chiếm, biến `.env` thiếu hoặc còn là giá trị mẫu, DNS/tunnel | `start.sh` chỉ dựa vào Compose báo thiếu biến | `make check`, `scripts/gen-env.sh`, `docs/trien-khai.md` mục 6 | P2 | S |
| **R5** | **Phát hành đúng chuẩn**: khi có thẻ `vX.Y.Z` tự tạo **GitHub Release** kèm gói mã nguồn `.tar.gz`, `.sha256`, **SBOM sinh lúc phát hành** (CycloneDX/Syft), ghi chú trích từ `CHANGELOG.md`; ký image bằng cosign và đính kèm provenance | `release.yml` chỉ chạy tay (`workflow_dispatch`), đẩy image lên GHCR, không tạo Release. Thẻ `v1.0.0–v1.0.3` có, GitHub Release tạo tay và không kèm SBOM sinh lúc phát hành. `sbom/*.cdx.json` là tệp commit tĩnh, dễ lỗi thời | `.github/workflows/release.yml`, `scripts/release.sh`, `release-notes.sh`, `make sbom` | **P1** | M |
| **R6** | **GitOps tự phục hồi** (reconciler theo timer systemd: container bị xóa thì dựng lại theo khai báo, chỉ áp commit có chữ ký). Chỉ đáng làm nếu có máy chủ chạy dài hạn | Dựa vào `restart:` của Docker (9 dịch vụ) | `scripts/reconcile.py`, `platform/systemd/`, `make demo-tu-phuc-hoi` | P3 | M |
| **R7** | **Kiểm tra cấu hình Gateway trong CI sâu hơn**: ngoài "file hợp lệ và có route", thêm test mỗi route công khai đều có kiểm token (hoặc nằm trong danh sách ngoại lệ có chủ đích) | `ci-infra.yml` kiểm file hợp lệ và có route | Test route trong smoke của Signet | P2 | S |

## 7. CI/CD và chất lượng

| ID | Cải tiến | Hiện trạng DX (đã kiểm) | Tham khảo Signet | Mức | Công sức |
|---|---|---|---|---|---|
| **C1** | **Phủ CI cho mọi nhánh đích** (đề nghị bỏ). Workflow chỉ chạy khi PR/push vào `main` và `Khuong/main`, `ci.yml` chỉ khi đổi `backend/**`. PR vào nhánh khác không có CI; bỏ bộ lọc nhánh thì e2e nặng chạy trên mọi PR. Cách làm hiện tại (PR thẳng vào `Khuong/main`) không cần | `.github/workflows/ci.yml` | `pr.yml`, `ci.yml` có job `thay-doi` tính phạm vi thay đổi | P3 | S |
| **C2** | **Kiểm giấy phép tự động** cho Maven, pip, npm, action CI (kể cả phụ thuộc gián tiếp), có tệp ngoại lệ ghi lý do; kiểm header SPDX/REUSE. Đặc biệt hữu ích vì repo trộn Apache-2.0 và `ai/` AGPL-3.0 | Có `LICENSES.md` viết tay, không thấy kiểm tự động | `scripts/license_audit.py`, `.github/license-exceptions.json`, `REUSE.toml`, `make license-audit` | P1 | M |
| **C4** | **Kiểm tiêu đề PR và tên nhánh** (Conventional Commits), ruleset bảo vệ nhánh để thành tệp trong repo | Có `CONTRIBUTING.md`, chưa thấy kiểm tự động | `scripts/check_pr.py`, `.github/rulesets/develop.json` | P2 | S |
| **C5** | **ArchUnit** bổ sung cho ranh giới module: core không phụ thuộc app, ứng dụng không dùng entity của core, controller không gọi repository | Ranh giới đã được Maven chặn khi biên dịch, nhưng chưa chặn ở mức gói/lớp | — | P2 | S |
| **C6** | **Thử tải** (k6) cho gateway và các route có giới hạn để chốt số liệu (backlog đang ghi "số tạm") | Chưa thử tải | — | P2 | M |
| **C7** | **Kiểm nâng cấp migration**: dựng DB ở bản thẻ trước, nạp dữ liệu mẫu, chạy Flyway bản mới, kiểm. (Gộp với R1) | Test dùng Testcontainers trên DB sạch | Job nâng cấp `nang-cap.yml` | P2 | M |
| **C8** | **Diễn tập sự cố trong CI hoặc script demo**: kill một container, kiểm hệ thống tự hồi phục và cảnh báo được gửi; ngắt Postgres, kiểm outbox không mất thông báo | Chưa có | `make demo-tu-phuc-hoi`, `make demo-canh-bao` | P3 | S–M |
| **C9** | **Tài liệu mức hoàn thiện có test kiểm lại** (số hàm test, tệp test, bước CI ghi trong `TESTING.md` khớp với repo) | `TESTING.md` có nhiều ô "Kiểm tay", không có kiểm tự động bảng này | `docs/muc-hoan-thien.md` + `scripts/tests/test_muc_hoan_thien.py` | P3 | S |

## 8. Tính năng mới cho Core (Tầng 1) và H-P-D-I (Tầng 2)

| ID | Tính năng | Vì sao | Hiện trạng DX | Mức | Công sức |
|---|---|---|---|---|---|
| **K1** | **Kênh thời gian thực**: SSE trong Spring (`SseEmitter`) hoặc Centrifugo, để hộp thông báo, hàng đợi duyệt cập nhật ngay không cần tải lại | Giảm polling, trải nghiệm tốt hơn, nền cho push | Hộp thông báo chỉ kéo về khi mở app (backlog §5) | P2 | M |
| **K2** | **Kênh thông báo đa kênh**: `email` (SMTP), `webhook`, `push` (ntfy/UnifiedPush/FCM) ngoài `chat`; người dùng tự bật/tắt từng kênh; trạng thái giao qua `chat_outbox` | Chỉ Mattermost. Một bản email (JavaMail, hàng đợi chung, bảng lựa chọn, `PUT /api/v1/notifications/preferences`) đã làm thử và bỏ khỏi đợt trước. Khi làm lại phải tắt `management.health.mail`, vì `SPRING_MAIL_HOST` rỗng vẫn tạo bộ gửi và làm `/actuator/health` báo lỗi | — | **P1** | M |
| **K3** | **Công cụ SLA và leo thang dùng chung ở Core**: hạn theo loại hồ sơ, cảnh báo ở 80%, đánh dấu quá hạn, leo thang theo bậc, tự đóng. Đưa từ `LabScheduler` lên `core/workflow` để mọi ứng dụng dùng lại | Logic SLA đang nằm trong `app-lab`. Backlog §7 đã ghi "leo thang và tự đóng" | `LabScheduler`; trong Signet là việc nhắc hạn của `svc-workflow` | P2 | M |
| **K4** | **Dịch vụ tìm kiếm của Core**: PostgreSQL `tsvector` + `unaccent` + `pg_trgm` cho tiếng Việt có dấu/không dấu, expose qua `dx-core-api`. Chưa cần Meilisearch | Mọi ứng dụng đều cần tìm kiếm. Backlog §7 đã cần `pg_trgm` | Chỉ thấy 1 file nhắc tới | P2 | S–M |
| **K5** | **Đường xử lý tệp có hạn mức**: xóa metadata, băm SHA-256 chống trùng, hạn mức theo người dùng/workspace, dọn tệp mồ côi, link có hạn (đã có 15 phút) | Core đã có `StorageService` và quét ClamAV khi bật, nhưng thiếu hạn mức và dọn rác | `FileStoreService`, `StorageBucketInitializer` | P2 | M |
| **K6** | **Cờ tính năng và công tắc tắt ứng dụng/mô-đun lúc chạy** (không cần dựng lại) | Giúp vận hành tắt AI hoặc BI khi quá tải mà các phần khác vẫn chạy | `DXOS_APP` chọn ứng dụng lúc khởi động | P3 | S |
| **K7** | **CLI vận hành** `dxos` (tạo workspace, đổi vai, xoay khóa, xuất audit, kiểm chuỗi băm, sao lưu/khôi phục) gom các script rời | Giảm thao tác tay trong Keycloak Admin | Nhiều script rời trong `deploy/` | P3 | M |
| **K8** | **Scaffold ứng dụng mới** (`dxos app new <tên>`): sinh module Maven, Flyway schema riêng, bean cấu hình mẫu (`AppModule`, `SecurityPolicies`, `WorkflowConfig`, `NotificationConfig`). Hiện thêm ứng dụng phải làm tay theo `CONTRIBUTING.md`; có bộ scaffold thì chứng minh được luận điểm "thêm ứng dụng không sửa Core" bằng một lệnh | Đã có ranh giới biên dịch | `moc app install` của Signet (cài lúc chạy, khác cách làm lúc biên dịch của DX) | P3 | M |
| **K9** | **Mô hình lỗi API thống nhất** (RFC 9457 `application/problem+json`) kèm `request_id`, để web, mobile và log nói cùng một mã | Chưa kiểm cách DX trả lỗi | `RestAccessDeniedHandler`, `exception/` | P3 | S |
| **K10** | **Wiki nội bộ đăng nhập bằng Keycloak** (DokuWiki, mỗi workspace một không gian) làm nơi chứa quy trình vận hành và sổ tay, tách khỏi kho RAG | Lab đang có RAG cho quy chế nhưng không có nơi soạn thảo tri thức cho nhân sự | Mục "Wiki nội bộ" trong `docs/van-hanh.md`, `make demo-wiki` | P3 | M |

## 9. Dịch vụ mới có thể thêm vào stack

Mọi dịch vụ nên để sau một **profile** tắt mặc định (R2) để không tăng RAM cho máy yếu.

| Dịch vụ | Giấy phép | Dùng để | Thay hoặc bổ sung | Liên quan | Mức |
|---|---|---|---|---|---|
| **Prometheus + Alertmanager + Grafana** | Apache-2.0 · Apache-2.0 · AGPL-3.0 | Metric, cảnh báo, bảng điều khiển | Bổ sung `dx-monitor` | O1, O2, O7 | P1 |
| **Uptime Kuma** hoặc **Gatus** | MIT · Apache-2.0 | Giám sát từ ngoài, trang trạng thái công khai | Bổ sung `monitor` | O3 | P1 |
| **Mailpit** (thử) + **SMTP relay** (thật) | MIT | Kênh email, thử thông báo | Mới (đã thử, bỏ khỏi đợt trước) | K2 | P1 |
| **Loki + Grafana Alloy** hoặc **Vector + Parseable** | AGPL-3.0 + Apache-2.0 · MPL-2.0 + AGPL-3.0 | Log tập trung | Thay `docker logs` | O5 | P2 |
| **Jaeger** hoặc **Tempo** + OpenTelemetry | Apache-2.0 | Tracing phân tán | Mới | O6 | P2 |
| **ntfy** (hoặc UnifiedPush) | Apache-2.0 / GPL-2.0 | Push cho mobile mà không cần FCM | Giải backlog "Push thật" | K1, K2 | P2 |
| **Centrifugo** | Apache-2.0 | Thời gian thực qua WebSocket | Tùy chọn thay SSE | K1 | P3 |
| **PgBouncer**, **postgres_exporter**, **pgBackRest** | ISC · Apache-2.0 · MIT | Gom kết nối, metric DB, PITR | Bổ sung PostgreSQL | D9, O1 | P3 |
| **DokuWiki** | GPL-2.0 | Wiki nội bộ | Mới | K10 | P3 |

**Ngân sách RAM.** Signet tự đo khoảng 3,2 GB cho 18 dịch vụ chạy liên tục (ClamAV ~0,8 GB, Keycloak ~0,6 GB).
DX đã nặng hơn vì Superset, Mattermost, Ollama, AI service.
Thêm Prometheus + Grafana + Loki cộng khoảng 1–1,5 GB (ClamAV, khi bật, thêm khoảng 1 GB), nên R2 (profile) nên làm **trước** khi thêm dịch vụ.

## 10. Không nên chép từ Signet

| Thứ | Lý do |
|---|---|
| Hasura, Flowable, Scorpio | Stack Python/GraphQL của họ. DX đã có quy trình duyệt và API trong Spring |
| Nhiều dịch vụ tùy chọn cùng lúc | Chi phí RAM và vận hành. Thêm từng dịch vụ khi có nhu cầu, đừng bật hết |
| Reconciler GitOps (R6) | Chỉ hợp khi chạy trên máy chủ dài hạn có quyền áp cấu hình tự động. Docker `restart:` đủ cho bản thi |
| Ký số PAdES và các tính năng gắn nghiệp vụ văn thư | Ngoài phạm vi Core của DX |
| Sao chép nguyên mã | Signet là Apache-2.0 nên được dùng lại, nhưng phải giữ header, `NOTICE` và ghi vào `LICENSES.md`. Với một đội cùng cuộc thi, nên viết lại theo ý tưởng và ghi nguồn tham khảo |

## 11. Đối chiếu với `docs/BACKLOG.md`

| Mục backlog của DX | Giải bởi |
|---|---|
| §5 Xóa audit theo thời hạn | D3 (phân vùng + lưu trữ) |
| §5 Cảnh báo đăng nhập sai theo tài khoản | O7 |
| §5 Metric CPU/RAM, phân vị độ trễ | O1 |
| §5 Cảnh báo khi chính `monitor` ngừng | O2 (dead-man switch), O3 |
| §5 Gộp lớp Mattermost của monitor và core; `monitor` không nhớ điều kiện đã báo sau restart | O9 |
| §5 Push thật; email cho thông báo | K1, K2, ntfy |
| §4 Giới hạn tốc độ là số tạm, chưa thử tải; Superset dùng SQLite | C6, D5 |
| §4 Chẩn đoán AI chậm, 504 lần đầu; AI service không có hàng đợi | D4 |
| §7 Leo thang và tự đóng ticket | K3 |

## 12. Việc nên làm tiếp

1. **O1 + O2 + O3**: metric, cảnh báo và giám sát từ ngoài.
   Có thêm bằng chứng vận hành cho phần chấm PoF về tính bền vững.
2. **R5**: tự tạo GitHub Release kèm SBOM khi có thẻ phiên bản; `release.yml` hiện chỉ đẩy image.
