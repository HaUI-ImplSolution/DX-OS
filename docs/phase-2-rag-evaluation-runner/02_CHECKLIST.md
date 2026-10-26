# Checklist: Phase 2

`[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ có lý do.
Hiện chỉ có kế hoạch; chưa ghi nhận case triển khai nào đạt.

## WP2-01: Hợp đồng và ranh giới

Kết quả đầu ra: các mode, quyền sở hữu project và domain được chốt.

- [ ] WP2-01-DOC-01 Chủ dự án xem kế hoạch, chốt thời gian xem và nền tảng hỗ trợ.
- [~] WP2-01-INFRA-01 Xác định project/state/cổng riêng, khóa phiên và guard đường dẫn/context.
- [~] WP2-01-INFRA-02 Preflight Docker/Compose, tài nguyên, cổng, socket; help không tạo tài nguyên.
- [ ] WP2-01-TEST-01 Kiểm đường dẫn có khoảng trắng, mode sai, state qua symlink và hai phiên.

## WP2-02: Chuẩn bị môi trường

Kết quả đầu ra: clone/state mới tự có môi trường đánh giá và provenance đúng.

- [~] WP2-02-INFRA-01 Thêm Compose/env mẫu, chỉ giá trị tường minh; không publish DB và không dùng tag sản phẩm.
- [~] WP2-02-INFRA-02 Image công cụ và MinIO có đường cold build/pull độc lập, cache dùng lại, build tuần tự/context nhỏ.
- [~] WP2-02-AI-01 Helper sinh credential, tạo schema/roles đánh giá, ingest bằng code hiện có và ghi provenance.
- [ ] WP2-02-AI-02 Model/cache/phiên bản thống nhất giữa ingest và query, không force-download hoặc tự đổi revision.
- [ ] WP2-02-INFRA-03 Tách dependency đánh giá và runtime; mount nguồn cần thiết RO, không đọc env/token sản phẩm.
- [ ] WP2-02-TEST-01 Kiểm state mới, cache ấm, cập nhật corpus/model hợp lệ và role không ghi được.

## WP2-03: Chạy, xem và tự dọn

Kết quả đầu ra: một lệnh tạo kết quả, cho xem có thời hạn và tự giải phóng đúng tài nguyên.

- [ ] WP2-03-AI-01 Gọi nguyên CLI chất lượng, đủ 42 lượt/ba nhóm và metadata bộ nhãn.
- [ ] WP2-03-AI-02 Gọi nguyên CLI thử tải, đủ 18 trial và đúng container lấy mẫu.
- [ ] WP2-03-INFRA-01 Dừng AI/vector trước cửa sổ xem; tách cửa sổ đo khỏi Langfuse/publish.
- [ ] WP2-03-INFRA-02 Headless init Langfuse, publish bằng SDK, mở báo cáo HTML và in link/đăng nhập.
- [~] WP2-03-INFRA-03 Enter/timeout/exit/signal cleanup idempotent; xử lý job con và khóa phiên.
- [~] WP2-03-INFRA-04 Không TTY không chờ treo; view/down dùng đúng dữ liệu và project của wrapper.
- [ ] WP2-03-AI-03 Giữ artifact lỗi, exit code gốc, receipt và lịch sử; không báo k6 lỗi thành thành công.
- [ ] WP2-03-TEST-01 Kiểm đủ mode, timeout, Enter, signal, lỗi startup/publish, hai phiên và phiên dở.

## WP2-04: Kiểm chứng và bàn giao

Kết quả đầu ra: bằng chứng chạy lại và hướng dẫn khớp bản đã thử.

- [ ] WP2-04-TEST-01 Self-check nhỏ cho logic điều phối và self-check công cụ hiện có đạt.
- [ ] WP2-04-TEST-02 Clone/state mới không dựa vào file private hoặc tag image local của máy đã test.
- [ ] WP2-04-TEST-03 Sau down, cache/credential/volume/kết quả còn nguyên; view mở lại đúng lịch sử.
- [ ] WP2-04-TEST-04 Stack sản phẩm/namespace khác không bị stop, ghi DB hoặc đổi cấu hình.
- [ ] WP2-04-DOC-01 Sửa hai mục trong `ai/README.md` thành cách chạy `.sh`, đầu ra và tự dọn đã kiểm chứng.
- [ ] WP2-04-DOC-02 Cập nhật phần TESTING liên quan, CHANGELOG thêm mới, LICENSES nếu cần; BACKLOG giữ việc chưa làm.

## Đóng phase

- [ ] Tất cả case bắt buộc ở `03_TEST_PLAN.md` có người chạy, ngày, exit code và bằng chứng thật.
- [ ] Diff chỉ thuộc allowlist; không có thay đổi `ai/app/`, Backend, UI, corpus/nhãn, deploy hoặc CI sản phẩm.
- [ ] Không có đường khởi động/import/job CI của sản phẩm gọi `evaluate.sh`.
- [ ] Tự dọn được sau mọi đường thoát hỗ trợ; không ảnh hưởng project khác, không xóa volume hoặc cache.
- [ ] Không tự tạo điểm, đổi nhãn, đổi phép đo hoặc báo trace replay thành latency thật.
- [ ] README/TESTING ghi trạng thái đã thử; không dùng kết quả cũ thay bằng chứng chạy wrapper mới.
- [ ] Rà script trùng, file thử, cache, artifact, model, tag image và tài liệu thừa; giữ bằng chứng ngoài Git.
- [ ] Không có secret, dữ liệu thật, checksum literal, `report/` hoặc `report.zip` trong diff; không thêm/sửa `.gitignore`.
- [ ] CHANGELOG giữ log cũ; LICENSES đúng thành phần; SBOM chỉ đổi khi chính sách runtime yêu cầu.
- [ ] Các tiêu chí mục 6 của overview đạt; chuyển việc còn lại sang BACKLOG và xóa thư mục phase sau bàn giao.
