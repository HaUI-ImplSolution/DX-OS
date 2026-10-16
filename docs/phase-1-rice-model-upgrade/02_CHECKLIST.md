# Checklist: Phase 1

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` không áp dụng.
Chỉ giữ các lớp thực sự liên quan model bệnh.

## WP1-01: Chốt checkpoint và mapping

Kết quả đầu ra: sáu lớp và metadata khớp checkpoint, dùng đúng mã bệnh hiện có.

**Spec và tài liệu**

- [x] WP1-01-DOC-01 Đọc template, kiến trúc CV gốc và lập kế hoạch trên nhánh riêng; đã đồng bộ nền `origin/Khuong/main`.
- [x] WP1-01-DOC-02 Người dùng duyệt phạm vi, đóng gói và danh sách file ở `01_PLAN.md` trước code.
- [-] WP1-01-DOC-03 Swagger/DTO Backend không đổi vì endpoint và payload giữ nguyên.

**AI và data**

- [ ] WP1-01-AI-01 Đối chiếu metadata checkpoint với architecture, sáu nhãn, kích thước và normalization.
- [ ] WP1-01-DATA-01 [farm] Cập nhật class contract sáu lớp; giữ mã bệnh, danh mục và metadata fallback tương thích.

## WP1-02: Thay nạp và suy luận

Kết quả đầu ra: trả payload cũ bằng model mới, không đổi quality/crop gate hoặc nghiệp vụ.

**AI**

- [ ] WP1-02-AI-01 Thay loader YOLO bằng Torchvision, kiểm checksum và metadata, nạp state dict strict.
- [ ] WP1-02-AI-02 Cập nhật `model_profile.py`, đường dẫn mặc định và inference 480×480 trong service hiện có.
- [ ] WP1-02-AI-03 Trả confidence chưa làm tròn, kiểm đầu ra hợp lệ và giữ gate 0,70.
- [ ] WP1-02-AI-04 Bỏ logic detection/IoU và dependency riêng không còn caller; giữ kiểm ảnh và crop classifier.
- [ ] WP1-02-DOC-01 Đồng bộ dependency AI, lock, SBOM và giấy phép đúng phần thay đổi.

## WP1-03: Đóng gói trọng số

Kết quả đầu ra: build/restart dùng model bệnh cục bộ theo cách chạy gốc.

**AI, infra và CI**

- [ ] WP1-03-AI-01 Đưa ba phần checkpoint, metadata thứ tự/kích thước và nguồn giấy phép vào Git; kiểm checkout nguyên byte, không dùng LFS và mỗi phần dưới 100 MiB.
- [ ] WP1-03-AI-02 Thêm duy nhất script ghép cần cho build, không in giá trị checksum.
- [ ] WP1-03-INFRA-01 Chỉnh build/context AI để ghép model vào `/opt/dxos/models`, giữ image runtime và volume khác đúng mục đích.
- [ ] WP1-03-INFRA-02 Chỉ chỉnh phần model bệnh trong Compose và `.env.example`; không thêm fallback mặc định trong Compose.
- [ ] WP1-03-INFRA-03 Điều chỉnh cách tính checksum model trong script env CI; giữ nguyên workflow CI của main, ghép model trong Docker build hiện có.
- [ ] WP1-03-INFRA-04 Giữ nguyên start/switch/reset, SSO, BI, gateway và cấu trúc demo/E2E.
- [ ] WP1-03-AI-03 Xóa trọng số và tham chiếu runtime YOLO bệnh cũ sau khi model mới được xác nhận.

## WP1-04: Kiểm tra tương thích, tài liệu và dọn release

Kết quả đầu ra: diff chỉ có việc thay model, có bằng chứng đúng phiên bản được bàn giao.

**Test và tài liệu**

- [ ] WP1-04-TEST-01 Điều chỉnh test CV gốc cho sáu lớp, không thêm thư mục test hoặc ảnh fixture tích hợp riêng.
- [ ] WP1-04-TEST-02 Điều chỉnh đúng assertion model/class ID trong test và validator gốc, không đổi dữ liệu nguồn.
- [ ] WP1-04-TEST-03 Kiểm tra cú pháp AI và đọc toàn bộ diff; chạy case runtime chỉ theo yêu cầu rõ của người dùng.
- [ ] WP1-04-TEST-04 Kiểm luồng Farm/Lab và model thật theo yêu cầu; giữ quyền xem trước/sau duyệt, không sửa module ngoài phạm vi.
- [ ] WP1-04-TEST-05 Kiểm đóng gói từ checkout sạch, restart offline và cách chạy README khi được yêu cầu; ghi nhận lỗi ngoài model.
- [ ] WP1-04-TEST-06 Đối chiếu diff với Khuong/main, kiểm conflict và CI giữ nguyên; không dựng thêm Docker nếu người dùng không yêu cầu.
- [ ] WP1-04-DOC-01 Sửa tại chỗ thông tin model/giấy phép cũ; giữ bố cục, không thêm hướng dẫn deploy ngoài nhu cầu model.
- [ ] WP1-04-DOC-02 Chỉ ghi kết quả đánh giá người huấn luyện cung cấp, không ghi thành accuracy pipeline đã tự kiểm chứng.

## Đóng phase

- [ ] Cú pháp AI và các kiểm tra model đạt; smoke Farm/Lab có bằng chứng và điều kiện môi trường trong `03_TEST_PLAN.md`.
- [ ] Comment mới ngắn, bằng tiếng Anh, chỉ mô tả hành vi hiện tại; không đưa nhãn phase hoặc lịch sử tích hợp vào code.
- [ ] Diff không có thay đổi Backend, web/mobile, SSO, BI, RAG/LLM hoặc script khởi động.
- [ ] API/DTO và ranh giới Core/App giữ nguyên; không thêm endpoint model-capabilities hoặc provider framework.
- [ ] Checkout có đủ trọng số, không có LFS pointer hoặc file vượt 100 MiB; restart không tải model bệnh.
- [ ] Bước cấu hình và lệnh chạy vẫn đúng README main, chỉ tham chiếu trọng số bệnh thay đổi.
- [ ] Không có giá trị checksum riêng, credential, mật khẩu hoặc dữ liệu người dùng trong diff, tài liệu, manifest hay log được đưa vào Git.
- [ ] Không còn loader/checkpoint/model manifest cũ của nhánh tích hợp; không mang code ngoài phạm vi từ PR cũ.
- [ ] Không còn ảnh test, log, cache, file backup, benchmark, script kiểm tra một lần hoặc tài liệu kế hoạch trùng lặp.
- [ ] CI không gọi đường dẫn đã xóa; bộ kiểm tra gốc còn phù hợp vẫn dùng được.
- [ ] README, LICENSES, NOTICE, SBOM và CHANGELOG khớp trạng thái cuối; không xóa log cũ.
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 có bằng chứng; không lấy kết quả PR cũ.
- [ ] Cập nhật trạng thái và nền mới nhất trong README phase.
- [ ] Chuyển lỗi ngoài phạm vi vào BACKLOG; không sửa startup/seed/BI để làm xanh kiểm tra.
- [ ] Xóa thư mục phase theo template trước release, sau khi đạt tiêu chí đóng phase.
