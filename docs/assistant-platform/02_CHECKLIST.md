# Checklist: Trợ lý hồ sơ đa người dùng

Chỉ đánh dấu khi có kết quả kiểm chứng của bản tích hợp, không dùng kết quả PoC thay thế.
Đã triển khai bản tích hợp trong worktree riêng; checkbox triển khai và checkbox nghiệm thu được kiểm riêng.
Đánh dấu adapter đã cấu hình không có nghĩa là provider đã được xác minh bằng key thật.
Core riêng đã khởi động, đăng nhập web, liên kết PKCE và kiểm Gemini/Telegram thật.
Kết quả vận chuyển tin nhắn không thay thế đánh giá nội dung hoặc xác nhận người nhận đã đọc.

## Chuẩn bị kế hoạch

- [x] Đối chiếu kiến trúc Dynamic Core hiện tại và template của nhánh cha.
- [x] Ghi nhận nhiều tài khoản, 2–3 trợ lý đồng thời, đọc tự động và ghi có xác nhận.
- [x] Tách nhánh kế hoạch từ nhánh cha đã cập nhật, giữ nguyên diff và môi trường PoC.
- [x] Đọc toàn bộ gói delta tech lead ngày 05/11, đối chiếu quy tắc với CONTRIBUTING, mã và policy hiện hành.

## WPAS-01: Hợp đồng và kiểm nền

Kết quả: G0 đạt trước khi triển khai các chức năng.

- [x] WPAS-01-DOC-01 Chốt API, schema proposal/receipt và danh mục công cụ với người dùng.
- [x] WPAS-01-DOC-02 Lập bảng tái sử dụng và thay thế từng thành phần PoC; hợp đồng API/proposal ở assistant/README.md.
- [x] WPAS-01-INFRA-01 Kiểm LICENSE upstream/artifact, OSI và bundled cho runtime/build/test; báo trước khi thêm manifest, rồi khóa phiên bản OpenClaw/Node.
- [ ] WPAS-01-TEST-01 Chốt ánh xạ case vào script/test hiện có hoặc kiểm tay; file mới chỉ trong phạm vi được duyệt; kiểm SDK/runtime/approval qua TAS-01.

## WPAS-02: Tài khoản, state và runtime

Kết quả: danh tính và dữ liệu A/B/C tách biệt.

- [x] WPAS-02-DOC-01 Mô tả API tài khoản, liên kết SSO và runtime trước code.
- [x] WPAS-02-BE-01 [assistant] Tạo route/service/store theo chức năng, JWT issuer/audience và quyền chính chủ.
- [ ] WPAS-02-BE-02 [assistant] Liên kết PKCE/refresh riêng; xử lý revoke, logout và tài khoản bị khóa.
- [ ] WPAS-02-DATA-01 [assistant] Migration SQLite và AEAD; kiểm backup/restore, owner binding và key riêng.
- [ ] WPAS-02-BE-03 [assistant] Liên kết bot/private chat, nonce hết hạn, unique bot ID và unlink.
- [x] WPAS-02-INFRA-01 Provision runtime có volume/token riêng, không đặc quyền, không Docker socket hoặc cổng public.
- [ ] WPAS-02-TEST-01 Kiểm TAS-02–TAS-09 và TAS-40; chặn truy cập chéo cả API, callback và runtime.

## WPAS-03: Provider

Kết quả: công cụ và xác nhận nhất quán giữa các provider được xác minh.

- [x] WPAS-03-AI-01 Cấu hình adapter native Gemini, OpenAI và Anthropic bằng API OpenClaw.
- [x] WPAS-03-AI-02 Profile compatible có origin allowlist, capability và model đã kiểm.
- [ ] WPAS-03-BE-01 [assistant] Probe, timeout, quota, giới hạn retry và lỗi được che bí mật.
- [x] WPAS-03-AI-03 Không tự đổi provider; giữ công cụ hoạt động độc lập với nội dung sinh.
- [ ] WPAS-03-TEST-01 Chạy TAS-10–TAS-14; ít nhất hai provider thật, ghi rõ profile còn chưa xác minh.

## WPAS-04: Điều kiện thực thi Core

Kết quả: snapshot cũ bị Core từ chối, client cũ giữ hành vi.

- [x] WPAS-04-DOC-01 Cập nhật spec action/assign/metadata và `@Operation` trước code.
- [x] WPAS-04-BE-01 [dx-engine] Thêm điều kiện version/modelVersion tùy chọn cho action/assign.
- [x] WPAS-04-BE-02 [dx-engine] Kiểm phiên bản dưới khóa mô hình và khóa dòng trong transaction.
- [x] WPAS-04-BE-03 [dx-engine] Metadata thêm model version và mô tả tác động completion đã rút gọn.
- [ ] WPAS-04-TEST-01 Chạy TAS-15–TAS-19 và TAS-42; giữ nguyên ProcessRules, booking và giao dịch.

## WPAS-05: Công cụ nghiệp vụ và ADMIN

Kết quả: thao tác đúng API, đúng chủ, có xác nhận kiểm chứng được.

- [x] WPAS-05-BE-01 [assistant] Đọc/lọc/chi tiết/lịch sử/stats và tìm ứng viên qua `/handlers`.
- [x] WPAS-05-BE-02 [assistant] Giao/đổi/trả việc và action của ba họ mẫu theo metadata.
- [x] WPAS-05-BE-03 [assistant] Proposal server-side, TTL, xác nhận từ người dùng và atomic execution ledger.
- [x] WPAS-05-BE-04 [assistant] Tác động membership xác nhận web; phân biệt trạng thái Core với completion bất đồng bộ.
- [x] WPAS-05-BE-05 [assistant] ADMIN chỉ đọc vận hành/giải thích plan; handoff các lệnh quản trị ghi.
- [ ] WPAS-05-TEST-01 Chạy TAS-20–TAS-27, TAS-41 và TAS-43 trên dữ liệu giả của các mô hình hiện có.

## WPAS-06: Thông báo và chất lượng trả lời

Kết quả: thông báo chủ động, không bịa dữ kiện hoặc tự ghi.

- [x] WPAS-06-BE-01 [assistant] Poll phân trang theo chủ và ngân sách; baseline, ledger, chống lặp và retry hữu hạn.
- [x] WPAS-06-BE-02 [assistant] Nhắc hạn/digest theo lịch và múi giờ; kiểm quyền trước gửi.
- [x] WPAS-06-AI-01 Khung trả lời có nguồn/phạm vi/thời điểm; dữ liệu hồ sơ không được ghi đè chỉ dẫn công cụ.
- [ ] WPAS-06-FE-01 Renderer Telegram/web dùng label, trạng thái và tác động rõ; không lỗi escape hoặc dump trường kỹ thuật.
- [ ] WPAS-06-TEST-01 Chạy TAS-28–TAS-33 và TAS-44; không chiếm webhook hoặc dựng lại scheduler Core.

## WPAS-07: UI, bộ cài và tuân thủ

Kết quả: tùy chọn trợ lý phát hành được, Core mặc định giữ cách chạy.

- [x] WPAS-07-FE-01 Trang cấu hình cá nhân, hướng dẫn kích hoạt, probe, pairing, bật/tắt và đề xuất/lịch sử.
- [ ] WPAS-07-FE-02 Runtime feature config và trang capacity; không đọc key/hội thoại người khác.
- [x] WPAS-07-INFRA-01 Compose/profile và lệnh assistant theo `dxos`; biến mặc định trong env.example, thiếu biến bắt buộc báo rõ.
- [ ] WPAS-07-INFRA-02 Installer/update/backup và SSO/APISIX nhận phần mở rộng tùy chọn.
- [ ] WPAS-07-INFRA-03 Image/cache, healthcheck, giới hạn tài nguyên; AI lỗi không chặn Core.
- [ ] WPAS-07-INFRA-04 Tích hợp CI hiện có, license/SPDX/notices, catalog và lock; SBOM runtime đúng policy, dependency build/test vẫn kiểm riêng.
- [ ] WPAS-07-DOC-01 Cập nhật README/API/kiến trúc/vận hành theo trạng thái cuối; không sửa định hướng Dynamic Core.
- [ ] WPAS-07-TEST-01 Chạy TAS-34–TAS-39 và TAS-45; kiểm Core off/on từ clone sạch.

## WPAS-08: Nghiệm thu và bàn giao

Kết quả: đủ bằng chứng trên bản tích hợp trước đề nghị đóng.

- [ ] WPAS-08-TEST-01 Chạy toàn bộ ma trận bắt buộc; lỗi còn lại có mức độ, chủ xử lý và quyết định chấp nhận.
- [ ] WPAS-08-TEST-02 Đo 2 rồi 3 người: cold/warm, thời gian, CPU/RAM/swap/disk, Core request và quota provider.
- [ ] WPAS-08-TEST-03 Chạy build/formatter/architecture/API test, TypeScript và các check tuân thủ liên quan theo CI cha.
- [ ] WPAS-08-TEST-04 Người dùng nghiệm thu Telegram/web: giao việc giữa hai người, hoàn tất và xem lịch sử.
- [ ] WPAS-08-DOC-01 Ghi số đo thật, provider đã kiểm và giới hạn; không tuyên bố exactly-once hoặc Open Source AI.

## Đóng đợt triển khai

- [ ] Đủ tiêu chí mục 6 của overview và exit criteria trong test plan.
- [ ] Không có API nghiệp vụ mới ngoài phạm vi WPAS-04 hoặc phụ thuộc ngược giữa bốn module.
- [ ] Lỗi backend mới có mã và mẫu dịch frontend; không đổi envelope hoặc hạ quyền kiểm của Core.
- [ ] Không có test file ngoài phạm vi được duyệt, snapshot cả response, helper/dependency không dùng hoặc build image lặp không cần thiết.
- [ ] Không có entity/role/nhóm quyền riêng ngành trong mã dùng chung.
- [ ] Core khởi động theo hướng dẫn khi không có AI/key; provider hoặc Telegram mất kết nối không ảnh hưởng nghiệp vụ.
- [ ] Không có secret, token, khóa mã hóa, mapping thật, hash từ môi trường hoặc dữ liệu cá nhân trong diff/log/artifact công bố.
- [ ] Không loại bỏ lockfile/digest chuẩn hoặc làm yếu check bảo mật của cha để đạt checklist.
- [ ] Dọn file thử, build output, fixture thừa; không sửa `.gitignore`, không stage `report/` hoặc tài liệu cá nhân.
- [ ] Đọc diff toàn bộ và báo mọi tác động ngoài domain; giữ kế hoạch và chờ người dùng cho phép commit/push.

## Bàn giao tiến độ ngày 07/11/2026

- Nhánh nền vẫn khớp `origin/Khuong/main` tại lần fetch cuối; chưa commit hoặc push bản tích hợp.
- Backend `./mvnw verify`, frontend build/TypeScript, kiểm kiến trúc, giấy phép, workflow, SBOM và bộ cài đạt ở các lượt kiểm đã ghi trong test plan.
- Backup/restic restore giữ được state, master key và phiên SSO; bật/tắt trợ lý không chặn Core.
- Ba supervisor tách volume, mạng và capability; chưa dùng kết quả này thay phép thử ba trợ lý hoạt động thật.

- Bộ test trợ lý hiện có 25 test đạt; TypeScript và `git diff --check` đạt.
- Gemini `gemini-3.1-flash-lite` gọi được công cụ và trả lời qua Telegram; Groq mới kiểm được capability, chưa nghiệm thu đủ luồng.
- Kiểm hộp việc, thống kê, quyền giữa mô hình, dữ liệu chứa chỉ dẫn giả và yêu cầu vượt công cụ bằng model thật.
- Giao việc giữa hai tài khoản: người nhận thấy hồ sơ trong inbox; thông báo có acknowledgement Telegram; nhận xử lý sau xác nhận tạo đúng một sự kiện Core.
- Bảy ca API kiểm hủy, hết hạn thật, xác nhận đồng thời, snapshot cũ, người nhận không hợp lệ, xác nhận chéo tài khoản và tự duyệt đều đạt.
- Phê duyệt giả có tác động nhóm quyền được xác nhận trên web; Core ghi một lần và từ chối lặp.
- Chuyển cấu hình thử nghiệm sang tài khoản Admin theo yêu cầu chủ dự án: dừng runtime, sao lưu state, ngắt bot cũ rồi liên kết bot đã xác minh vào runtime Admin riêng; không sao chép hội thoại.
- Admin đọc mô hình/phiên bản, lượt triển khai, hàng đợi và audit qua công cụ; xem trước YAML không áp dụng mô hình.
- Admin không có vai nghiệp vụ bị Core từ chối đọc dữ liệu; trợ lý không có công cụ apply, cấp vai hoặc đổi mật khẩu.
- Sửa mô tả công cụ sau ca chọn nhầm thống kê nghiệp vụ cho hàng đợi hệ thống; chạy lại dùng đúng công cụ và khớp API.
- Một lượt đề xuất dùng sai target/action bị chặn trước ghi; bổ sung chỉ dẫn đọc hồ sơ/metadata trước và phản hồi lỗi có mã đã lọc. Lượt nhận việc chạy lại đạt.
- Browser Admin: thông tin bí mật không trả lại biểu mẫu, nút ngắt bot hiện đúng, màn hình 390px không tràn và không lỗi JavaScript.

Giới hạn: chưa chứng minh người dùng đã đọc tin Telegram, chưa chạy hai–ba trợ lý hoạt động đồng thời hoặc đủ luồng với provider thứ hai.
Bộ gọi Gateway kiểm thử dùng thêm một tiến trình SDK; runtime Admin được nâng giới hạn cục bộ lên 2 GiB khi chạy bộ này để tránh ép bộ nhớ.
Đây không phải số đo tải sản phẩm hoặc thay đổi cấu hình mặc định trong mã.

### Kiểm tính động bằng YAML mới

- Áp dụng YAML bảo tàng qua API quản trị hiện có, không thêm mã ngành vào Core, bridge hoặc prompt.
- Bốn thực thể dùng ba họ `work_item`, `approval`, `reservation`; trợ lý đọc đúng metadata mới và thống kê theo trường vừa khai báo.
- Model thật thực hiện nhận/xử lý xong phiếu bảo tồn, duyệt trưng bày và xác nhận lịch mượn sau xác nhận native; mỗi thao tác có đúng một sự kiện Core.
- Tài khoản người gửi thử nghiệm xác nhận đóng phiếu qua Core; không nhầm quyền người xử lý với quyền người tạo.
- Giao cho chuyên viên của domain mới qua `/handlers`; phía người nhận có hồ sơ trong inbox và nhận xử lý được. Tài khoản này không cấu hình Telegram, không tuyên bố đã gửi hoặc đọc tin.
- Core chặn lịch mượn trùng ngay khi tạo; quyền bảo tàng không mở quyền dữ liệu support và ngược lại.
- Từ chối xác nhận native đóng ledger; xác nhận lại trả 409, hồ sơ chưa chuyển trạng thái.
- Sửa nullable assignee bị validator đổi thành chuỗi rỗng; test tái hiện đỏ/xanh và thao tác trả việc về nhóm qua API thật đạt.
- Tách receipt chuyển trạng thái khỏi receipt giao việc để câu trả lời không gắn nhầm trạng thái người nhận.
- Các lượt bị Gemini 429/503 hoặc timeout chờ Gateway không tính đạt; các ca xác nhận thành công đã được chạy lại và đối chiếu dữ liệu, không gửi lại lệnh ghi chỉ dựa trên timeout.

YAML, dữ liệu giả và artifact của đợt này nằm ngoài repository.
Năng lực dynamic được kiểm trong ba họ quy trình Core hiện hỗ trợ, không suy ra trợ lý có thể tạo họ quy trình hoặc quyền mới.

Hai ca cuối qua model thật chưa được tính đạt: hỏi chéo museum/support trả `UNAVAILABLE`, đọc hồ sơ chứa chỉ dẫn giả ở museum bị timeout chờ model.
Quyền chéo đã kiểm riêng bằng Core API (403), không dùng bằng chứng API thay kết quả hội thoại.
Giữ hai ca này để chạy lại khi Gemini ổn định; không đóng checklist chất lượng trả lời hoặc toàn bộ phase.

Kết thúc bộ kiểm đã dừng worker thử, khởi động lại runtime Admin từ cấu hình lưu và trả giới hạn RAM cục bộ về 1.536 MiB.
Core metadata vẫn trả thành công; không chạy lại toàn stack, không commit/push và giữ kế hoạch.
