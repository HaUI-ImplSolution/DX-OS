# Checklist: Trợ lý hồ sơ đa người dùng

Chỉ tick việc có bằng chứng được ghi trong [test plan](./03_TEST_PLAN.md).
Mã TAS/TRV chỉ ra ca kiểm, DOC-01 chỉ ra hồ sơ thiết kế đã đối chiếu.
Ca mới đạt một phần không đóng mục nghiệm thu tương ứng.
Việc triển khai có mã không thay xác nhận nghiệm thu toàn bộ trợ lý của người phụ trách.

## Chuẩn bị kế hoạch

- [x] Đối chiếu kiến trúc Dynamic Core hiện tại và template của nhánh cha. — DOC-01
- [x] Ghi nhận nhiều tài khoản, 2–3 trợ lý đồng thời, đọc tự động và ghi có xác nhận. — DOC-01
- [x] Tách nhánh kế hoạch từ nhánh cha đã cập nhật, giữ nguyên diff và môi trường PoC. — DOC-01
- [x] Đọc toàn bộ gói delta tech lead ngày 05/11, đối chiếu quy tắc với CONTRIBUTING, mã và policy hiện hành. — DOC-01

## WPAS-01: Hợp đồng và kiểm nền

Kết quả: G0 đạt trước khi triển khai các chức năng.

- [x] WPAS-01-DOC-01 Chốt API, schema proposal/receipt và danh mục công cụ với người dùng. — DOC-01
- [x] WPAS-01-DOC-02 Lập bảng tái sử dụng và thay thế từng thành phần PoC; hợp đồng API/proposal ở assistant/README.md. — DOC-01
- [x] WPAS-01-INFRA-01 Kiểm LICENSE upstream/artifact, OSI và bundled cho runtime/build/test; báo trước khi thêm manifest, rồi khóa phiên bản OpenClaw/Node. — TRV-04
- [ ] WPAS-01-TEST-01 Chốt ánh xạ case vào script/test hiện có hoặc kiểm tay; file mới chỉ trong phạm vi được duyệt; kiểm SDK/runtime/approval qua TAS-01.

## WPAS-02: Tài khoản, state và runtime

Kết quả: danh tính và dữ liệu A/B/C tách biệt.

- [x] WPAS-02-DOC-01 Mô tả API tài khoản, liên kết SSO và runtime trước code. — DOC-01
- [x] WPAS-02-BE-01 [assistant] Tạo route/service/store theo chức năng, JWT issuer/audience và quyền chính chủ. — TAS-02, TRV-01
- [ ] WPAS-02-BE-02 [assistant] Liên kết PKCE/refresh riêng; xử lý revoke, logout và tài khoản bị khóa.
- [ ] WPAS-02-DATA-01 [assistant] Migration SQLite và AEAD; kiểm backup/restore, owner binding và key riêng.
- [ ] WPAS-02-BE-03 [assistant] Liên kết bot/private chat, nonce hết hạn, unique bot ID và unlink.
- [x] WPAS-02-INFRA-01 Provision runtime có volume/token riêng, không đặc quyền, không Docker socket hoặc cổng public. — TRV-01, TRV-13
- [ ] WPAS-02-TEST-01 Kiểm TAS-02–TAS-09 và TAS-40; chặn truy cập chéo cả API, callback và runtime.

## WPAS-03: Provider

Kết quả: công cụ và xác nhận nhất quán giữa các provider được xác minh.

- [ ] WPAS-03-AI-01 Cấu hình adapter native Gemini, OpenAI và Anthropic bằng API OpenClaw.
- [ ] WPAS-03-AI-02 Profile compatible tạm khóa; còn thiếu nghiệm thu transport và model trước khi mở lại.
- [ ] WPAS-03-BE-01 [assistant] Probe, timeout, quota, giới hạn retry và lỗi được che bí mật.
- [ ] WPAS-03-AI-03 Không tự đổi provider; giữ công cụ hoạt động độc lập với nội dung sinh.
- [ ] WPAS-03-TEST-01 Chạy TAS-10–TAS-14; ít nhất hai provider thật, ghi rõ profile còn chưa xác minh.

## WPAS-04: Điều kiện thực thi Core

Kết quả: snapshot cũ bị Core từ chối, client cũ giữ hành vi.

- [x] WPAS-04-DOC-01 Cập nhật spec action/assign/metadata và `@Operation` trước code. — DOC-01, TRV-06
- [x] WPAS-04-BE-01 [dx-engine] Thêm điều kiện version/modelVersion tùy chọn cho action/assign. — TAS-16, TRV-06
- [x] WPAS-04-BE-02 [dx-engine] Kiểm phiên bản dưới khóa mô hình và khóa dòng trong transaction. — TAS-16, TRV-06
- [ ] WPAS-04-BE-03 [dx-engine] Metadata thêm model version và mô tả tác động completion đã rút gọn.
- [ ] WPAS-04-TEST-01 Chạy TAS-15–TAS-19 và TAS-42; giữ nguyên ProcessRules, booking và giao dịch.

## WPAS-05: Công cụ nghiệp vụ và ADMIN

Kết quả: thao tác đúng API, đúng chủ, có xác nhận kiểm chứng được.

- [ ] WPAS-05-BE-01 [assistant] Đọc/lọc/chi tiết/lịch sử/stats và tìm ứng viên qua `/handlers`.
- [ ] WPAS-05-BE-02 [assistant] Giao/đổi/trả việc và action của ba họ mẫu theo metadata.
- [ ] WPAS-05-BE-03 [assistant] Proposal server-side, TTL, xác nhận từ người dùng và atomic execution ledger.
- [ ] WPAS-05-BE-04 [assistant] Tác động membership xác nhận web; phân biệt trạng thái Core với completion bất đồng bộ.
- [ ] WPAS-05-BE-05 [assistant] ADMIN chỉ đọc vận hành/giải thích plan; handoff các lệnh quản trị ghi.
- [ ] WPAS-05-TEST-01 Chạy TAS-20–TAS-27, TAS-41 và TAS-43 trên dữ liệu giả của các mô hình hiện có.

## WPAS-06: Thông báo và chất lượng trả lời

Kết quả: thông báo chủ động, không bịa dữ kiện hoặc tự ghi.

- [ ] WPAS-06-BE-01 [assistant] Poll phân trang theo chủ và ngân sách; baseline, ledger, chống lặp và retry hữu hạn.
- [ ] WPAS-06-BE-02 [assistant] Nhắc hạn/digest theo lịch và múi giờ; kiểm quyền trước gửi.
- [ ] WPAS-06-AI-01 Khung trả lời có nguồn/phạm vi/thời điểm; dữ liệu hồ sơ không được ghi đè chỉ dẫn công cụ.
- [ ] WPAS-06-FE-01 Renderer Telegram/web dùng label, trạng thái và tác động rõ; không lỗi escape hoặc dump trường kỹ thuật.
- [ ] WPAS-06-TEST-01 Chạy TAS-28–TAS-33 và TAS-44; không chiếm webhook hoặc dựng lại scheduler Core.

## WPAS-07: UI, bộ cài và tuân thủ

Kết quả: tùy chọn trợ lý phát hành được, Core mặc định giữ cách chạy.

- [ ] WPAS-07-FE-01 Trang cấu hình cá nhân, hướng dẫn kích hoạt, probe, pairing, bật/tắt và đề xuất/lịch sử.
- [ ] WPAS-07-FE-02 Runtime feature config và trang capacity; không đọc key/hội thoại người khác.
- [x] WPAS-07-INFRA-01 Compose/profile và lệnh assistant theo `dxos`; biến mặc định trong env.example, thiếu biến bắt buộc báo rõ. — TRV-08
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

## Xử lý review PR #199

Các dấu tick trước phần này chỉ là trạng thái từng việc của kế hoạch gốc, không phải chấp thuận merge.
Kết quả triển khai được đánh dấu theo bằng chứng của lượt review; các mục còn mở không tính nghiệm thu.

- [x] WPRV-01-DOC-01 Nhận bản merge mới nhất của nhánh PR, đối chiếu `origin/Khuong/main`; không phát sinh conflict mới. — DOC-01
- [x] WPRV-01-DOC-02 Đọc review, convention, template hiện hành và rule tech lead; ánh xạ nhận xét vào WPRV-01–06. — DOC-01
- [x] WPRV-01-DOC-03 Rà bằng chứng từng dấu tick và ghi quyết định API/SPDX, không xác nhận lại phần đã duyệt. — DOC-01
- [x] WPRV-02-TEST-01 Kiểm bridge/callback và hai supervisor thật, volume/network/capability; phần hội thoại LLM của TAS-06 còn mở. — TRV-01
- [x] WPRV-02-TEST-02 Chặn compatible ở lưu/chọn/probe/start, kể cả profile cũ; test transport để mở lại vẫn chưa đạt. — TRV-02
- [x] WPRV-03-INFRA-01 Kiểm backup/restore và resume khi lỗi; ghi trust boundary và khoảng gián đoạn. — TRV-03
- [x] WPRV-03-INFRA-02 Thu hẹp ngoại lệ Artistic đúng gói; kiểm cả repository và attribution SPDX. — TRV-04
- [x] WPRV-04-BE-01 [dx-engine] Hoàn lại riêng diff cache local; giữ kiểm phiên bản của PR và ghi tối ưu vào BACKLOG. — TRV-05
- [x] WPRV-04-BE-02 [dx-engine] Chốt nghĩa effects theo completion thật, giữ xác nhận web và handler metadata của cha. — TRV-06
- [x] WPRV-04-BE-03 [dx-engine] Định dạng RecordPreconditions và kiểm precondition không đổi. — TRV-06, TRV-09
- [x] WPRV-05-FE-01 Cache feature detection, bỏ request theo focus; reload nhận cấu hình mới. — TRV-07
- [x] WPRV-05-INFRA-01 Một đường dựng gateway, route trợ lý tùy chọn và biến theo convention cha. — TRV-08
- [x] WPRV-05-DOC-01 Giới hạn ba runtime nhất quán giữa cấu hình, mã, UI và tài liệu. — DOC-01, TAS-09
- [x] WPRV-06-DOC-01 Đồng bộ CHANGELOG/API/website dependency/README; giữ log cũ và bỏ số audit cố định. — TRV-10
- [x] WPRV-06-TEST-01 Chạy TRV-01–TRV-10, ghi bản kiểm/ngày/người chạy và kết quả thật ngoài repository. — TRV-01–10
- [x] WPRV-06-DOC-02 Rà checklist gốc, cập nhật BACKLOG và bảng phản hồi đầy đủ các nhận xét. — DOC-01
- [x] WPRV-06-TEST-02 Kiểm formatter/verify, Node test, TypeScript/build, docs/license/workflow liên quan và diff/secret. — TRV-09–10
- [x] WPRV-06-DOC-03 Giữ plan; gửi diff cho người dùng trước khi commit/push/resolve, không đưa report hoặc .gitignore vào. — TRV-10

## Review bổ sung — triển khai theo phạm vi đã chốt

- [x] WPRV-07-DOC-01 Đọc review sau test thật, đối chiếu code và ghi bảng sửa ngay/chờ quyết định/đề xuất BACKLOG. — DOC-01
- [x] WPRV-07-DOC-03 Chốt tách mạng control, giữ Gemini/OpenAI/Anthropic, giới hạn egress/backup bản thử và hoãn cache; Origin/PATCH giữ hành vi hiện có. — DOC-01
- [x] WPRV-07-BE-01 [assistant] Trả 400/413 đúng lỗi framework; giữ 500 cho server error và che bí mật. — TRV-12
- [x] WPRV-07-INFRA-01 Tách control khỏi default, bổ sung đúng mạng API/ingress và kiểm TCP/DNS bằng stack riêng. — TRV-13
- [x] WPRV-07-DOC-04 Ghi đúng schema/Origin/PATCH và backup capability; chưa tự mở tính năng hoặc thay chính sách. — TRV-14–15
- [x] WPRV-07-BE-02 [assistant] Phân biệt chưa liên kết Core (409) với phiên sai/hết hạn (401), kiểm client. — TRV-14
- [x] WPRV-07-TEST-01 Chạy TRV-11–16 và hoàn tất kiểm còn dở theo phạm vi đã chốt; không dùng kết quả trên JAR cũ. — TRV-11–16
- [x] WPRV-07-DOC-05 Ghi các giới hạn egress/backup và việc tối ưu đã được chấp nhận vào BACKLOG, ghi điều kiện trước dùng thật. — DOC-01, TRV-15
