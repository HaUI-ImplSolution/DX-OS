# Checklist: Trợ lý hồ sơ đa người dùng

Chỉ tick việc có bằng chứng được ghi trong [test plan](./03_TEST_PLAN.md).
Mã TAS/TRV chỉ ra ca kiểm, DOC-01 chỉ ra hồ sơ thiết kế đã đối chiếu.
Ca mới đạt một phần không đóng mục nghiệm thu tương ứng.
Việc triển khai có mã không thay xác nhận nghiệm thu toàn bộ trợ lý của người phụ trách.
WPI-01–04 ở cuối file ghi kết quả điều chỉnh khởi động chung; các dấu tích của bản cũ không thay bằng chứng TDI.

## Chuẩn bị kế hoạch

- [x] Đối chiếu kiến trúc Dynamic Core hiện tại và template của nhánh cha. — DOC-01
- [x] Ghi nhận nhiều tài khoản, nghiệm thu 2 trợ lý đồng thời và hoãn thử tải 3; đọc tự động và ghi có xác nhận. — DOC-01
- [x] Tách nhánh kế hoạch từ nhánh cha đã cập nhật, giữ riêng dữ liệu và cấu hình cục bộ. — DOC-01
- [x] Đọc toàn bộ gói delta tech lead ngày 05/11, đối chiếu quy tắc với CONTRIBUTING, mã và policy hiện hành. — DOC-01

## WPAS-01: Hợp đồng và kiểm nền

Kết quả: G0 đạt trước khi triển khai các chức năng.

- [x] WPAS-01-DOC-01 Chốt API, schema proposal/receipt và danh mục công cụ với người dùng. — DOC-01
- [x] WPAS-01-DOC-02 Lập bảng thành phần tích hợp; hợp đồng API/proposal ở assistant/README.md. — DOC-01
- [x] WPAS-01-INFRA-01 Kiểm LICENSE upstream/artifact, OSI và bundled cho runtime/build/test; báo trước khi thêm manifest, rồi khóa phiên bản OpenClaw/Node. — TRV-04
- [x] WPAS-01-TEST-01 Chốt ánh xạ case vào năm file test hiện có và kiểm native SDK/runtime/approval. — TAS-01, TAS-13–14

## WPAS-02: Tài khoản, state và runtime

Kết quả: hai tài khoản nghiệm thu tách biệt; capacity ba slot kiểm riêng.

- [x] WPAS-02-DOC-01 Mô tả API tài khoản, liên kết SSO và runtime trước code. — DOC-01
- [x] WPAS-02-BE-01 [assistant] Tạo route/service/store theo chức năng, JWT issuer/audience và quyền chính chủ. — TAS-02, TRV-01
- [x] WPAS-02-BE-02 [assistant] Liên kết PKCE/refresh riêng; xử lý revoke, logout và tài khoản bị khóa. — TAS-03
- [x] WPAS-02-DATA-01 [assistant] Migration SQLite và AEAD; kiểm backup/restore, owner binding và key riêng. — TAS-07, TRV-03
- [x] WPAS-02-BE-03 [assistant] Liên kết bot/private chat, nonce hết hạn, unique bot ID, unlink và phạm vi pairing mới mỗi lần cấu hình. — TAS-05, TAS-08
- [x] WPAS-02-INFRA-01 Provision runtime có volume/token riêng, không đặc quyền, không Docker socket hoặc cổng public. — TRV-01, TRV-13
- [x] WPAS-02-TEST-01 Kiểm quyền chính chủ, callback, volume và capability runtime; Core kiểm lại quyền khi đọc/ghi. — TAS-02–09, TAS-40

## WPAS-03: Provider

Kết quả: công cụ và xác nhận nhất quán giữa các provider được xác minh.

- [x] WPAS-03-AI-01 Cấu hình adapter native Gemini, OpenAI và Anthropic bằng API OpenClaw. — TAS-10 (cấu hình native; nghiệm thu model thật tại TAS-11 còn mở)
- [x] WPAS-03-AI-02 Khóa compatible ở lưu/chọn/probe/start, kể cả profile cũ; nghiệm thu transport để mở lại theo BACKLOG. — TRV-02, TAS-12
- [x] WPAS-03-BE-01 [assistant] Kiểm probe, timeout, giới hạn retry và che bí mật khi lỗi; không tạo flood quota trên provider thật. — TAS-10, TAS-13–14
- [x] WPAS-03-AI-03 Không tự đổi provider; giữ công cụ hoạt động độc lập với nội dung sinh. — TAS-10, TAS-25
- [x] WPAS-03-TEST-01 Kiểm cấu hình ba adapter và model Gemini thật; provider thật thứ hai hoãn theo chủ dự án vì chưa có key. — TAS-10–14, BACKLOG

## WPAS-04: Điều kiện thực thi Core

Kết quả: snapshot cũ bị Core từ chối, client cũ giữ hành vi.

- [x] WPAS-04-DOC-01 Cập nhật spec action/assign/metadata và `@Operation` trước code. — DOC-01, TRV-06
- [x] WPAS-04-BE-01 [dx-engine] Thêm điều kiện version/modelVersion tùy chọn cho action/assign. — TAS-16, TRV-06
- [x] WPAS-04-BE-02 [dx-engine] Kiểm phiên bản dưới khóa mô hình và khóa dòng trong transaction. — TAS-16, TRV-06
- [x] WPAS-04-BE-03 [dx-engine] Metadata thêm model version và mô tả tác động completion đã rút gọn. — TAS-19, TRV-06
- [x] WPAS-04-TEST-01 Chạy TAS-15–TAS-19 và TAS-42; giữ nguyên ProcessRules, booking và giao dịch. — TAS-15–19, TAS-42

## WPAS-05: Công cụ nghiệp vụ và ADMIN

Kết quả: thao tác đúng API, đúng chủ, có xác nhận kiểm chứng được.

- [x] WPAS-05-BE-01 [assistant] Đọc/lọc/chi tiết/lịch sử/stats và tìm ứng viên qua `/handlers`. — TAS-20, TRV-06
- [x] WPAS-05-BE-02 [assistant] Kiểm giao/đổi/trả việc, action theo metadata của ba họ quy trình và ứng viên bị thu hồi quyền. — TAS-21–23, TAS-41
- [x] WPAS-05-BE-03 [assistant] Proposal server-side, TTL, xác nhận từ người dùng và atomic execution ledger. — TAS-16, TAS-24–25
- [x] WPAS-05-BE-04 [assistant] Membership chỉ xác nhận web; thông báo rõ completion bất đồng bộ và không đồng nhất approve với cấp quyền ngoài hệ thống. — TAS-22, TAS-27
- [x] WPAS-05-BE-05 [assistant] ADMIN đọc vận hành, giải thích plan và chuyển lệnh quản trị ghi về Console. — TAS-26–27
- [x] WPAS-05-TEST-01 Kiểm công cụ trên ba họ quy trình và hoàn tất luồng hai tài khoản bằng model thật, đối chiếu trạng thái/event Core. — TAS-20–27, TAS-41, TAS-43

## WPAS-06: Thông báo và chất lượng trả lời

Kết quả: thông báo chủ động, không bịa dữ kiện hoặc tự ghi.

- [x] WPAS-06-BE-01 [assistant] Poll phân trang theo chủ và ngân sách; baseline, ledger, chống lặp và retry hữu hạn. — TAS-28–30, TAS-44 (có receipt Telegram thật; không suy ra người dùng đã đọc)
- [x] WPAS-06-BE-02 [assistant] Nhắc hạn/digest theo lịch và múi giờ; kiểm quyền trước gửi. — TAS-29–30 (test dịch vụ; nghiệm thu transport thật còn mở)
- [x] WPAS-06-AI-01 Kiểm phạm vi, dữ kiện và prompt injection trong hồ sơ; hạn chế diễn đạt còn lại theo BACKLOG. — TAS-31–32
- [x] WPAS-06-FE-01 Kiểm thẻ Telegram và hộp xác nhận web bằng label/trạng thái/tác động; giới hạn văn phong nội dung sinh theo BACKLOG. — TAS-33
- [x] WPAS-06-TEST-01 Kiểm scheduler, retry, thu hồi quyền trước gửi và receipt Telegram thật; không chiếm webhook hoặc dựng lại scheduler Core. — TAS-28–33, TAS-44

## WPAS-07: UI, bộ cài và tuân thủ

Kết quả: tùy chọn trợ lý phát hành được, Core mặc định giữ cách chạy.

- [x] WPAS-07-FE-01 Kiểm cấu hình cá nhân, hướng dẫn, probe, pairing, bật/tắt, đề xuất và lịch sử qua browser thật. — TAS-34
- [x] WPAS-07-FE-02 Runtime feature config và trang capacity; không đọc key/hội thoại người khác. — TAS-09, TRV-07
- [x] WPAS-07-INFRA-01 Compose/profile và lệnh assistant theo `dxos`; biến mặc định trong env.example, thiếu biến bắt buộc báo rõ. — TRV-08
- [x] WPAS-07-INFRA-02 Installer/update/backup và SSO/APISIX nhận phần mở rộng tùy chọn. — TAS-37–38, TRV-03, TRV-08 (chưa chạy phát hành Release)
- [x] WPAS-07-INFRA-03 Image/cache, healthcheck, giới hạn tài nguyên; AI lỗi không chặn Core. — TAS-36, TRV-13 (healthcheck supervisor; chưa đo ba model đồng thời)
- [x] WPAS-07-INFRA-04 Kiểm tích hợp workflow hiện có, license/SPDX/notices, catalog, lock và SBOM; CI của diff chưa commit chưa được chạy. — TRV-04, TRV-09–10
- [x] WPAS-07-DOC-01 Đồng bộ README/API/kiến trúc/vận hành với trạng thái hiện tại; giữ định hướng Dynamic Core và chờ người dùng review cách viết. — DOC-01
- [ ] WPAS-07-TEST-01 Chạy TAS-34–TAS-39 và TAS-45; kiểm Core off/on từ clone sạch.

## WPAS-08: Nghiệm thu và bàn giao

Kết quả: đủ bằng chứng trên bản tích hợp trước đề nghị đóng.

- [ ] WPAS-08-TEST-01 Chạy toàn bộ ma trận bắt buộc; lỗi còn lại có mức độ, chủ xử lý và quyết định chấp nhận.
- [x] WPAS-08-TEST-02 Đo hai người: cold/warm, CPU/RAM/swap, request Core và token SDK; không suy ra quota/hóa đơn provider; thử tải ba hoãn theo BACKLOG. — TAS-39, mục 9 test plan
- [x] WPAS-08-TEST-03 Kiểm 52 test Node, frontend/docs build, license và secret; Java không đổi từ verify và 11 bộ kiểm Core đã đạt, CI của diff hiện tại chưa chạy. — TRV-09–10, mục 7 test plan
- [ ] WPAS-08-TEST-04 Người dùng nghiệm thu Telegram/web: giao việc giữa hai người, hoàn tất và xem lịch sử.
- [x] WPAS-08-DOC-01 Ghi số đo thật, provider đã kiểm và giới hạn; không tuyên bố exactly-once hoặc Open Source AI. — TAS-39, DOC-01

## Đóng đợt triển khai

- [ ] Đủ tiêu chí mục 6 của overview và exit criteria trong test plan.
- [x] Không có API nghiệp vụ mới ngoài phạm vi WPAS-04 hoặc phụ thuộc ngược giữa bốn module.
- [x] Lỗi backend mới có mã và mẫu dịch frontend; không đổi envelope hoặc hạ quyền kiểm của Core.
- [x] Không có test file ngoài phạm vi được duyệt, snapshot cả response, helper/dependency không dùng hoặc build image lặp không cần thiết.
- [x] Không có entity/role/nhóm quyền riêng ngành trong mã dùng chung.
- [ ] Core khởi động theo hướng dẫn khi không có AI/key; provider hoặc Telegram mất kết nối không ảnh hưởng nghiệp vụ.
- [x] Không có secret, token, khóa mã hóa, mapping thật, hash từ môi trường hoặc dữ liệu cá nhân trong diff/log/artifact công bố.
- [x] Không loại bỏ lockfile/digest chuẩn hoặc làm yếu check bảo mật của cha để đạt checklist.
- [x] Dọn file thử, build output, fixture thừa; không sửa `.gitignore`, không stage `report/` hoặc tài liệu cá nhân.
- [x] Đọc diff toàn bộ và báo mọi tác động ngoài domain; giữ kế hoạch và chờ người dùng cho phép commit/push.

## Xử lý review PR #199

Các dấu tick trước phần này chỉ là trạng thái từng việc của kế hoạch gốc, không phải chấp thuận merge.
Kết quả triển khai được đánh dấu theo bằng chứng của lượt review; các mục còn mở không tính nghiệm thu.

- [x] WPRV-01-DOC-01 Nhận bản merge mới nhất của nhánh PR, đối chiếu `origin/Khuong/main`; không phát sinh conflict mới. — DOC-01
- [x] WPRV-01-DOC-02 Đọc review, convention, template hiện hành và rule tech lead; ánh xạ nhận xét vào WPRV-01–06. — DOC-01
- [x] WPRV-01-DOC-03 Rà bằng chứng từng dấu tick và ghi quyết định API/SPDX, không xác nhận lại phần đã duyệt. — DOC-01
- [x] WPRV-02-TEST-01 Kiểm bridge/callback và hai supervisor thật, volume/network/capability; hội thoại hai tài khoản tại TAS-06. — TRV-01, TAS-06
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
- [x] WPRV-07-DOC-03 Chốt tách mạng control, giữ Gemini/OpenAI/Anthropic, các việc còn thiếu về egress/backup và hoãn cache; Origin/PATCH giữ hành vi hiện có. — DOC-01
- [x] WPRV-07-BE-01 [assistant] Trả 400/413 đúng lỗi framework; giữ 500 cho server error và che bí mật. — TRV-12
- [x] WPRV-07-INFRA-01 Tách control khỏi default, bổ sung đúng mạng API/ingress và kiểm TCP/DNS bằng stack riêng. — TRV-13
- [x] WPRV-07-DOC-04 Ghi đúng schema/Origin/PATCH và backup capability; chưa tự mở tính năng hoặc thay chính sách. — TRV-14–15
- [x] WPRV-07-BE-02 [assistant] Phân biệt chưa liên kết Core (409) với phiên sai/hết hạn (401), kiểm client. — TRV-14
- [x] WPRV-07-TEST-01 Chạy TRV-11–16 và hoàn tất kiểm còn dở theo phạm vi đã chốt; không dùng kết quả trên JAR cũ. — TRV-11–16
- [x] WPRV-07-DOC-05 Ghi công việc còn thiếu về egress/backup và tối ưu vào BACKLOG, kèm tiêu chí hoàn thành từng mục. — DOC-01, TRV-15

## WPI: Khởi động chung và docs

Đã triển khai và kiểm trên stack mới cùng bản sao dữ liệu cũ.
Nghiệm thu còn mở ở TDI-03, TDI-05 và TDI-07; người dùng đã review và cho phép push.

- [x] WPI-01-DOC-01 Rà bốn review NOTICE/TESTING/API/OPERATIONS; chốt vị trí bổ sung theo bố cục cha. — TDI-01
- [x] WPI-01-DOC-02 Giữ attribution khi thu gọn NOTICE, không mất thông tin trong gói phát hành. — TDI-01, TDI-16
- [x] WPI-02-INFRA-01 Khai báo control, bootstrap và supervisor cách ly trong Compose chính, không cần overlay/profile trợ lý. — TDI-02–04
- [x] WPI-02-INFRA-02 Tự chuẩn bị env/defaults theo env.example cho cả source và bản cài; không yêu cầu API key hoặc gõ lệnh phụ. — TDI-14, TDI-16
- [x] WPI-02-BE-01 [assistant] Bootstrap idempotent, giữ khóa/capability/mapping và chỉ mount config riêng từng runtime. — TDI-09–10, TDI-12
- [x] WPI-02-INFRA-03 Đồng bộ client SSO tự động cho realm mới/cũ; không cấp operator credential cho control/runtime. — TDI-11–12
- [x] WPI-02-INFRA-04 Route/mạng trong stack chung, Core không phụ thuộc trạng thái API hoặc gateway cá nhân. — TDI-07, TDI-12, TDI-15
- [x] WPI-03-FE-01 Trang cấu hình sẵn có, trạng thái thiếu key/bot rõ; gateway chỉ chạy khi chủ kích hoạt. — TDI-02, TDI-04–06
- [x] WPI-03-BE-01 [assistant] Giữ tách hai chủ, cổng xác nhận và trạng thái runtimeEnabled qua restart/nâng cấp. — TDI-06, TDI-08; 54 test Node
- [x] WPI-03-DATA-01 [assistant] Kiểm nhận state cũ, giải mã profile, giữ pairing và hội thoại; không tạo khóa thay thế. — TDI-09, TDI-13
- [x] WPI-03-INFRA-01 Installer/update/stop/status/reconcile dùng stack chung; legacy không còn là bước bắt buộc. — TDI-03, TDI-08, TDI-16
- [x] WPI-03-INFRA-02 Backup/restore và cleanup khi lỗi giữ đúng khóa, state, quyền file và gateway cần phục hồi. — TDI-13
- [ ] WPI-04-TEST-01 TDI-01–18 có bằng chứng trên bản mới, không lấy kết quả trước đổi startup thay thế.
- [x] WPI-04-TEST-02 Formatter/compile, TypeScript, test được duyệt và check CI liên quan đạt; không sửa lỗi ngoài domain. — TDI-15–16, TDI-18
- [x] WPI-04-DOC-01 Hướng dẫn sau triển khai khớp lệnh source/bản cài đã kiểm; không thêm kết quả từng lượt vào TESTING. — TDI-01–03
- [x] WPI-04-INFRA-01 Không còn tham chiếu overlay/generator/lệnh prepare cũ; chỉ xóa file hết dùng thuộc nhánh trợ lý. — TDI-16, TDI-18
- [x] WPI-04-TEST-03 Diff không có secret/report/.gitignore, không thay phần cha ngoài phạm vi; người dùng review trước commit/push.
