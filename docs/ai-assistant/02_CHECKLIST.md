# Checklist trợ lý nghiệp vụ động

Các mục CP theo dõi trợ lý nghiệp vụ; chỉ tick phần đã kiểm đủ theo [test plan](./03_TEST_PLAN.md).
Model mặc định đã chọn; nghiệm thu cuối và hồ sơ phân phối còn mở.
Mục có hậu tố tách phần đã kiểm và phần còn thiếu; chỉ hoàn tất tiêu chí CP tương ứng khi mọi phần đều đạt.
Dấu tick kỹ thuật không thay nghiệm thu bản phát hành.
Các ca UI và vòng đời dùng Core, SSO và provider thật với inference giả lập; kết quả local không chứng nhận chất lượng model hoặc hai bản phát hành.
Mục chuyển thành giới hạn không được tính là tiêu chí đã đạt.

## Quyết định và review

- [x] Người dùng chốt trợ lý nghiệp vụ nhiều domain, self-host và có xác nhận trước ghi.
- [x] Đã xác minh worktree/nhánh và fetch nhánh cha `Khuong/main` trước khi lập kế hoạch.
- [ ] Tech lead review schema nhiệm vụ, trải nghiệm trên hồ sơ, ranh giới dịch vụ và phần #210 chưa thực hiện.
- [x] Chọn Apertus-8B-Instruct-2509 làm model mặc định, bản local GGUF Q4 theo [cấu hình inference](../../assistant/README.md#inference-và-nguồn-tri-thức).
- [ ] Hoàn thiện hồ sơ nguồn mở, chất lượng tiếng Việt và ngân sách của model đã chọn theo nhiệm vụ hiện có.

## P0–P1: Hợp đồng và mô hình

- [x] CP-01a Kiểm giấy phép và hồ sơ dependency mã nguồn, catalog, NOTICE, SBOM và REUSE.
- [ ] CP-01b Hoàn thiện provenance/GGUF Q4, điều khoản model card, phạm vi binary/Web UI/notice của runtime và bằng chứng OSAID trước phân phối; giấy phép upstream và thư viện đã xác minh có trong DEPENDENCIES/NOTICE.
- [x] CP-02a Triển khai khai báo nhiệm vụ trong YAML, parser/schema, Console, diff/plan và model upgrade; kiểm parser, Core và build Console.
- [x] CP-02b Thêm/sửa nhiệm vụ bằng form Console, xem kế hoạch, apply và tải lại giữ đúng cấu hình trên Core thật.
- [x] CP-03 Metadata/nhiệm vụ theo quyền người gọi; mô hình không có AI vẫn hoạt động.
- [x] CP-04 Đổi/bỏ trường, lựa chọn, nhiệm vụ hoặc nguồn làm proposal cũ mất hiệu lực.
- [x] CP-05 Chốt spec rồi cập nhật OpenAPI/schema và code cùng thay đổi; giữ envelope/status/errorCode/i18n.
- [x] CP-06 Sửa trường có `version` và `modelVersion` được Core kiểm dưới khóa/giao dịch; client cũ giữ tương thích.

## P2–P3: Provider và giao diện

- [x] CP-07 Một request suy luận cho một nhiệm vụ; schema đầu ra, lựa chọn và nguồn được server kiểm, lỗi không tự chữa thành lệnh ghi.
- [x] CP-08a Catalog nguồn có ID/license/revision, nạp bất biến và kiểm mã nguồn do model trả về.
- [ ] CP-08b Nguồn phải hỗ trợ kết luận và model từ chối đúng khi thiếu căn cứ; còn lỗi ngữ nghĩa.
- [x] CP-09a Kiểm JWT/owner/quyền đọc, giới hạn dữ liệu gửi model và endpoint cố định; model không nhận token Core.
- [ ] CP-09b Kiểm đầy đủ injection ngữ nghĩa trong hồ sơ/nguồn trên model thật; chưa có bộ nghiệm thu đủ.
- [x] CP-10 Proposal có chủ/snapshot/TTL/revision; confirm ngoài LLM, sửa nội dung phải xác nhận snapshot mới.
- [x] CP-11 Hai confirm chỉ một claim; mất quyền/đổi trạng thái/phiên bản cũ bị Core chặn; timeout sau ghi là uncertain.
- [x] CP-12a UI dùng metadata, hiển thị giá trị/nguồn/lưu ý, sửa/hủy/xác nhận, no-op và lịch sử sau lưu đã được kiểm.
- [x] CP-12b Hai tab giữ bản nháp chưa lưu; lưu trên phiên bản cũ và xác nhận đề xuất sau thay đổi cạnh tranh bị Core chặn.
- [x] CP-13a Kiểm busy, timeout/hủy, ngân sách input/output và giải phóng slot bằng transport; không có fallback cloud.
- [x] CP-13b UI báo lỗi khi dừng inference mà Core vẫn đọc/ghi được; phiên Core bị thu hồi yêu cầu liên kết lại và phục hồi qua SSO/PKCE thật.

## P4–P5: Triển khai và state

- [x] CP-14a Snapshot state bằng container, loại master key và kiểm tính toàn vẹn SQLite trên bản restore.
- [x] CP-14b Restore state hai chủ bằng khóa giữ độc lập: liên kết/đề xuất giữ đúng chủ, chặn truy cập chéo và replay; migration schema v1 được kiểm bằng fixture tái dựng từ snapshot này.
- [x] CP-15 Giữ owner/SSO/lịch sử/key khi migration; trạng thái chưa rõ không replay.
- [x] CP-16 Route ngoài hợp đồng không được tạo credential, gọi inference hoặc ghi Core.
- [x] CP-17a Trợ lý sau profile; CLI chờ bootstrap SSO thật, từ chối client unmanaged/mật khẩu sai, không chạy control khi lỗi và giữ Core hoạt động.
- [ ] CP-17b Kiểm trọn bộ khởi động, update và backup trợ lý trên bản tích hợp trong CI.
- [x] CP-18 Kiểm owner, claim, encryption và timeout bằng bộ kiểm hiện có; không thêm test file/framework.
- [x] CP-19 Đồng bộ license/catalog/NOTICE/DEPENDENCIES/SBOM/REUSE và docs VI/EN; không xóa mục license còn dùng chung.
- [x] CP-20a Cài archive từ mã hiện tại qua bộ cài và khởi động Core thật khi trợ lý tắt; không cần image trợ lý hoặc tải trọng số.
- [x] CP-20b CI của bản sửa tích hợp đạt cài archive, các bài khởi động Core và backup/restore Postgres/kho tệp; phần tích hợp trợ lý còn ở CP-17b.
- [ ] CP-20c Đã kiểm local nâng cấp hai chủ, rollback provider/toàn bản cài, quay lại checkpoint và chặn ghi sai sau restore theo [kế hoạch rollback](./01_PLAN.md#nâng-cấp-và-rollback); chờ người dùng/tech lead review kết quả và giới hạn source/artifact local.

## P6: Tính động, tài nguyên và nghiệm thu

- [x] CP-21 Hai domain thật dùng cùng provider/UI, thay YAML không sửa mã; hồi quy ba họ workflow hiện có.
- CP-22 Giới hạn vận hành: một provider và một slot, chưa có SLA hoặc số đo model cold/warm/RAM/VRAM; xem mục 4 của [BACKLOG](../dynamic-core/BACKLOG.md).
- [x] CP-23 Kiểm build/compliance/doc refs/secret/diff đúng phần thay đổi; không để reference vận hành treo hoặc dependency vô dụng.
- [ ] CP-24 Người dùng chạy kịch bản demo, tech lead review giới hạn và giấy phép; chưa đánh dấu hoàn tất toàn bộ #210.

## Cải thiện chất lượng đa mẫu

- [x] QI-01 Giữ đối chứng 30 ca và chốt thêm ca chưa dùng để chỉnh prompt.
- [ ] QI-02 Quyết định đủ căn cứ trước giá trị; đo từ chối đúng và không làm mất các ca rõ nghĩa.
- [x] QI-03 Quan hệ số và đối chiếu danh mục lấy từ dữ liệu/metadata, không có tên ngành trong mã dùng chung.
- [ ] QI-04 Nội dung ngắn hoàn chỉnh; lỗi kiểu, nguồn và cấu trúc không thành đề xuất ghi được.
- [x] QI-05 Giữ một lượt suy luận, một slot, thời hạn và hủy; không lặp suy luận vô hạn hoặc tự ghi.
- [x] QI-06 Đã đo đối chứng và ca bổ sung trên Core/model/giao diện thật, giữ đáp án và công bố giới hạn; nghiệm thu bản tích hợp vẫn ở CP-24.

QI-02 và QI-04 còn mở ở phần chất lượng ngữ nghĩa và văn phong; các lỗi model còn lại được theo dõi tại [BACKLOG](../dynamic-core/BACKLOG.md).
Bản hiện tại giữ một lượt suy luận; chưa nghiệm thu khả năng từ chối ổn định trên các cách diễn đạt mới.
