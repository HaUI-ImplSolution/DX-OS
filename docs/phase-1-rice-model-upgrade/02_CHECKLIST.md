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

- [x] WP1-01-AI-01 Đối chiếu metadata checkpoint với architecture, sáu nhãn, kích thước và normalization.
- [x] WP1-01-DATA-01 [farm] Cập nhật class contract sáu lớp; giữ mã bệnh, danh mục và metadata fallback tương thích.

## WP1-02: Thay nạp và suy luận

Kết quả đầu ra: trả payload cũ bằng model mới, không đổi quality/crop gate hoặc nghiệp vụ.

**AI**

- [x] WP1-02-AI-01 Thay loader YOLO bằng Torchvision, kiểm checksum và metadata, nạp state dict strict.
- [x] WP1-02-AI-02 Cập nhật `model_profile.py`, đường dẫn mặc định và inference 480×480 trong service hiện có.
- [x] WP1-02-AI-03 Trả confidence chưa làm tròn, kiểm đầu ra hợp lệ và giữ gate 0,70.
- [x] WP1-02-AI-04 Bỏ logic detection/IoU và dependency riêng không còn caller; giữ kiểm ảnh và crop classifier.
- [x] WP1-02-DOC-01 Đồng bộ dependency AI, lock, SBOM và giấy phép đúng phần thay đổi.

## WP1-03: Đóng gói trọng số

Kết quả đầu ra: build/restart dùng model bệnh cục bộ theo cách chạy gốc.

**AI, infra và CI**

- [x] WP1-03-AI-01 Đưa ba phần checkpoint, metadata và nguồn/giấy phép vào Git; đã kiểm checkout nguyên byte, không dùng LFS, mỗi phần dưới 100 MiB.
- [x] WP1-03-AI-02 Thêm duy nhất script ghép cần cho build, không in giá trị checksum.
- [x] WP1-03-INFRA-01 Chỉnh build/context AI để ghép model vào `/opt/dxos/models`, giữ image runtime và volume khác đúng mục đích.
- [x] WP1-03-INFRA-02 Chỉ chỉnh phần model bệnh trong Compose và `.env.example`; không thêm fallback mặc định trong Compose.
- [x] WP1-03-INFRA-03 Điều chỉnh cách tính checksum model trong script env CI; giữ nguyên workflow CI của main, ghép model trong Docker build hiện có.
- [x] WP1-03-INFRA-04 Giữ nguyên start/switch/reset, SSO, BI, gateway và cấu trúc demo/E2E.
- [x] WP1-03-AI-03 Xóa trọng số và tham chiếu runtime YOLO bệnh cũ sau khi model mới được xác nhận.

## WP1-04: Kiểm tra tương thích, tài liệu và dọn release

Kết quả đầu ra: diff chỉ có việc thay model, có bằng chứng đúng phiên bản được bàn giao.

**Test và tài liệu**

- [x] WP1-04-TEST-01 Điều chỉnh test CV gốc cho sáu lớp, không thêm thư mục test hoặc ảnh fixture tích hợp riêng.
- [x] WP1-04-TEST-02 Điều chỉnh đúng assertion model/class ID trong test và validator gốc, không đổi dữ liệu nguồn.
- [x] WP1-04-TEST-03 Kiểm tra cú pháp AI và đọc toàn bộ diff; chạy case runtime chỉ theo yêu cầu rõ của người dùng.
- [x] WP1-04-TEST-04 Chạy Farm E2E 10/10, Lab E2E 16/16 có nguồn tri thức, ảnh thật qua model/Backend và kiểm quyền xem trước/sau duyệt; ghi rõ MinIO thử dùng RAM.
- [x] WP1-04-TEST-05 Đã dọn Docker và thử đúng lệnh README với env/dữ liệu/cache mới, MinIO trên ổ đĩa thường; Farm 10/10, Lab 16/16, offline đạt. Cold start lỗi seed trước AI ready; ghi BACKLOG, không sửa startup/seed ngoài phạm vi.
- [x] WP1-04-TEST-06 Đồng bộ Khuong/main, kiểm không có conflict; AI 100/100, dữ liệu 5/5, Farm 10/10 và model thật đạt. Không chạy lại Lab/Docker theo yêu cầu; mã Lab/core/CI đã đối chiếu không đổi.
- [x] WP1-04-DOC-01 Sửa tại chỗ thông tin model/giấy phép cũ; giữ bố cục, không thêm hướng dẫn deploy ngoài nhu cầu model.
- [x] WP1-04-DOC-02 Chỉ ghi kết quả đánh giá người huấn luyện cung cấp, không ghi thành accuracy pipeline đã tự kiểm chứng.

## Đóng phase

- [x] Cú pháp AI và các kiểm tra model đạt; smoke Farm/Lab có bằng chứng và điều kiện môi trường trong `03_TEST_PLAN.md`.
- [x] Comment mới ngắn, bằng tiếng Anh, chỉ mô tả hành vi hiện tại; không đưa nhãn phase hoặc lịch sử tích hợp vào code.
- [x] Diff không có thay đổi Backend, web/mobile, SSO, BI, RAG/LLM hoặc script khởi động.
- [x] API/DTO và ranh giới Core/App giữ nguyên; không thêm endpoint model-capabilities hoặc provider framework.
- [x] Checkout bộ trọng số đủ nguyên byte, không có LFS pointer hoặc file vượt 100 MiB; restart/predict offline đã đạt.
- [x] Bước cấu hình và lệnh chạy vẫn đúng README main, chỉ tham chiếu trọng số bệnh thay đổi.
- [x] Không có giá trị checksum riêng, credential, mật khẩu hoặc dữ liệu người dùng trong diff, tài liệu, manifest hay log được đưa vào Git.
- [x] Không còn loader/checkpoint/model manifest cũ của nhánh tích hợp; không mang code ngoài phạm vi từ PR cũ.
- [x] Không còn ảnh test, log, cache, file backup, benchmark, script kiểm tra một lần hoặc tài liệu kế hoạch trùng lặp.
- [x] CI không gọi đường dẫn đã xóa; bộ kiểm tra gốc còn phù hợp vẫn dùng được.
- [x] README, LICENSES, NOTICE, SBOM và CHANGELOG khớp trạng thái cuối; không xóa log cũ.
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 có bằng chứng; không ghi Pass dựa vào PR cũ. (Cold start còn lỗi startup/seed ngoài phạm vi.)
- [x] Cập nhật trạng thái và nền mới nhất trong README phase.
- [x] Chuyển lỗi ngoài phạm vi vào BACKLOG; không sửa startup/seed/BI để làm xanh kiểm tra.
- [ ] Xóa thư mục phase theo template trước release, sau khi đạt tiêu chí đóng phase.

Phần model được chia commit theo kế hoạch; các mục đóng phase còn phụ thuộc lỗi startup/seed ngoài phạm vi.
Lỗi ngoài phần model chỉ ghi tại `03_TEST_PLAN.md` và BACKLOG, không sửa code hoặc cấu hình module khác.

Lỗi startup/seed ngoài phạm vi vẫn nằm ở BACKLOG; không tick các điều kiện chưa đạt.
